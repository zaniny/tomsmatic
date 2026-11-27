package dev.schematicstock.refill;

import com.tom.storagemod.menu.StorageTerminalMenu;
import com.tom.storagemod.network.NetworkHandler;
import com.tom.storagemod.network.OpenTerminalPacket;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.util.TerminalSyncManager.SlotAction;
import dev.schematicstock.ClientScreens;
import dev.schematicstock.refill.RefillPackets.Reply;
import dev.schematicstock.refill.RefillPackets.Request;
import dev.schematicstock.refill.RefillPackets.Result;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.litematica.util.WorldUtils;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class RefillClient {
    private static final int TIMEOUT_TICKS = 60;
    private static Pending pending;
    private static Pending retry;
    private static int nextId;
    private static ClientLevel cooldownLevel;
    private static long retryAfter;

    private RefillClient() {}

    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(Reply.TYPE, (reply, context) -> {
            if (pending != null && pending.seamless && pending.id == reply.id()) {
                pending.result = Result.decode(reply.result());
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(RefillClient::tick);
    }

    public static boolean rejectRetryTarget(BlockPos pos, ItemStack item) {
        return retry != null && (!retry.pos.equals(pos) || !ItemStack.isSameItemSameComponents(retry.item, item));
    }

    public static boolean handleMissing(Minecraft client, BlockPos pos, ItemStack item) {
        var player = client.player;
        if (!RefillSetting.enabled() || retry != null || player == null || client.level == null
            || item.isEmpty() || player.isCreative() || player.isSpectator() || ClientScreens.current(client) != null
            || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        var inventory = player.getInventory();
        if (EntityUtils.getUsedHandForItem(player, item) != null || inventory.contains(item)) return false;
        if (pending != null || (cooldownLevel == client.level && client.level.getGameTime() < retryAfter)) return true;
        if (inventory.getFreeSlot() < 0) {
            notify(client, Result.INVENTORY_FULL);
            return true;
        }
        if (RefillServer.findWireless(player).isEmpty()) {
            notify(client, Result.NO_TERMINAL);
            return true;
        }
        boolean seamless = ClientPlayNetworking.canSend(Request.TYPE);
        if (!seamless && !ClientPlayNetworking.canSend(OpenTerminalPacket.ID)) {
            notify(client, Result.UNAVAILABLE);
            return true;
        }
        pending = new Pending(++nextId, client.level, player, pos.immutable(), item.copyWithCount(1), seamless);
        if (seamless) {
            ClientPlayNetworking.send(new Request(pending.id, pending.item));
        } else {
            NetworkHandler.openTerminal();
        }
        return true;
    }

    private static void tick(Minecraft client) {
        if (cooldownLevel != client.level) cooldownLevel = null;
        var request = pending;
        if (request == null) return;
        if (client.level != request.level || client.player != request.player) {
            pending = null;
            return;
        }
        if (!RefillSetting.enabled() || !Configs.Generic.EASY_PLACE_MODE.getBooleanValue() || !request.player.isAlive()) {
            finish(client, request, null);
            return;
        }
        if (request.result != null && request.result != Result.OK) {
            finish(client, request, request.result);
            return;
        }
        if ((request.seamless ? request.result == Result.OK : request.screen != null)
            && request.player.getInventory().contains(request.item)) {
            finish(client, request, Result.OK);
            return;
        }
        if (!request.seamless) tickTerminal(client, request);
        if (pending == request && ++request.age >= TIMEOUT_TICKS) {
            if (request.stockSeen && !request.withdrawn) {
                finish(client, request, Result.NO_STOCK);
            } else {
                finish(client, request, null);
                notify(client, "timeout");
            }
        }
    }

    private static void tickTerminal(Minecraft client, Pending request) {
        var screen = ClientScreens.current(client);
        if (request.screen == null && screen instanceof AbstractStorageTerminalScreen<?> terminal) {
            request.screen = terminal;
        }
        if (screen != request.screen) {
            pending = null;
            return;
        }
        if (request.screen == null || request.withdrawn) return;

        var menu = (StorageTerminalMenu) request.screen.getMenu();
        if (menu != request.player.containerMenu || !menu.getCarried().isEmpty()) {
            pending = null;
            return;
        }
        boolean empty = menu.slotCount > 0 && menu.slotCount < Short.MAX_VALUE && menu.slotCount == menu.freeCount;
        if (!menu.itemsLoaded && !empty) return;
        request.stockSeen = true;
        // Tom's can send the initial list in several packets.
        var stack = menu.itemList.stream()
            .filter(entry -> entry.getQuantity() > 0 && ItemStack.isSameItemSameComponents(request.item, entry.getStack()))
            .findFirst().orElse(null);
        if (stack == null) return;
        if (request.player.getInventory().getFreeSlot() < 0) {
            finish(client, request, Result.INVENTORY_FULL);
            return;
        }
        request.withdrawn = true;
        request.age = 0;
        menu.sync.sendInteract(stack, SlotAction.SHIFT_PULL, false);
    }

    private static void finish(Minecraft client, Pending request, Result result) {
        pending = null;
        if (request.screen != null && ClientScreens.current(client) == request.screen) request.screen.onClose();
        if (result == null) return;
        if (result != Result.OK) {
            notify(client, result);
            return;
        }
        if (ClientScreens.current(client) == null && request.player.containerMenu == request.player.inventoryMenu) {
            retry = request;
            try {
                if (Configs.Generic.EASY_PLACE_POST_REWRITE.getBooleanValue()) {
                    EasyPlaceUtils.handleEasyPlaceWithMessage();
                } else {
                    WorldUtils.handleEasyPlace(client);
                }
            } finally {
                retry = null;
            }
        }
    }

    private static void notify(Minecraft client, Result result) {
        notify(client, result.name().toLowerCase(Locale.ROOT));
    }

    private static void notify(Minecraft client, String reason) {
        cooldownLevel = client.level;
        retryAfter = client.level.getGameTime() + 20;
        ClientScreens.showOverlay(client, Component.translatable("schematic_stock.refill." + reason));
    }

    private static final class Pending {
        private final int id;
        private final ClientLevel level;
        private final LocalPlayer player;
        private final BlockPos pos;
        private final ItemStack item;
        private final boolean seamless;
        private AbstractStorageTerminalScreen<?> screen;
        private boolean stockSeen;
        private boolean withdrawn;
        private Result result;
        private int age;

        private Pending(int id, ClientLevel level, LocalPlayer player, BlockPos pos, ItemStack item, boolean seamless) {
            this.id = id;
            this.level = level;
            this.player = player;
            this.pos = pos;
            this.item = item;
            this.seamless = seamless;
        }
    }
}
