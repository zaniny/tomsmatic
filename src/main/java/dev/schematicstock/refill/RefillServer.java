package dev.schematicstock.refill;

import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import com.tom.storagemod.inventory.StoredItemStack;
import com.tom.storagemod.item.AdvWirelessTerminalItem;
import com.tom.storagemod.util.PlayerInvUtil;
import dev.schematicstock.refill.RefillPackets.Reply;
import dev.schematicstock.refill.RefillPackets.Request;
import dev.schematicstock.refill.RefillPackets.Result;
import java.util.WeakHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RefillServer implements ModInitializer {
    private static final ThreadLocal<OpenedTerminal> OPENING = new ThreadLocal<>();
    private final WeakHashMap<ServerPlayer, Integer> lastRequest = new WeakHashMap<>();

    @Override
    public void onInitialize() {
        RefillPackets.register();
        // No login handshake or unsolicited Tomsmatic packets. Clients opt in with their reply channel.
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE, this::handleRequest);
    }

    private void handleRequest(Request request, ServerPlayNetworking.Context context) {
        var player = context.player();
        if (!ServerPlayNetworking.canSend(player, Reply.TYPE)) return;

        Result result = refill(player, request.item());
        context.responseSender().sendPacket(new Reply(request.id(), result.ordinal()));
    }

    private Result refill(ServerPlayer player, ItemStack item) {
        int tick = player.tickCount;
        Integer previous = lastRequest.get(player);
        if (previous != null && tick - previous < 5) return Result.BUSY;
        lastRequest.put(player, tick);
        if (!player.isAlive() || player.isSpectator() || player.containerMenu != player.inventoryMenu || item.isEmpty()) {
            return Result.UNAVAILABLE;
        }

        var inventory = player.getInventory();
        if (inventory.contains(item)) return Result.OK;
        if (inventory.getFreeSlot() < 0) return Result.INVENTORY_FULL;
        ItemStack wireless = findWireless(player);
        if (wireless.isEmpty()) return Result.NO_TERMINAL;

        var opening = new OpenedTerminal(player);
        OPENING.set(opening);
        try {
            ((AdvWirelessTerminalItem) wireless.getItem()).open(player, wireless);
        } finally {
            OPENING.remove();
        }
        var storage = opening.storage;
        if (storage == null) return Result.UNAVAILABLE;

        var pulled = storage.pullStack(new StoredItemStack(item.copyWithCount(1)), item.getMaxStackSize());
        if (pulled == null || pulled.getQuantity() <= 0) return Result.NO_STOCK;
        ItemStack delivery = pulled.getActualStack();
        int before = delivery.getCount();
        inventory.add(delivery);
        boolean delivered = delivery.getCount() < before;
        if (!delivery.isEmpty()) storage.pushOrDrop(delivery);
        inventory.setChanged();
        player.inventoryMenu.broadcastChanges();
        return delivered ? Result.OK : Result.INVENTORY_FULL;
    }

    public static ItemStack findWireless(net.minecraft.world.entity.player.Player player) {
        return PlayerInvUtil.findItem(player,
            stack -> stack.getItem() instanceof AdvWirelessTerminalItem terminal && terminal.canOpen(stack),
            ItemStack.EMPTY, stack -> stack);
    }

    public static boolean captureTerminal(net.minecraft.world.entity.player.Player player, StorageTerminalBlockEntity storage) {
        var opening = OPENING.get();
        if (opening == null || opening.player != player) return false;
        opening.storage = storage;
        return true;
    }

    private static final class OpenedTerminal {
        private final ServerPlayer player;
        private StorageTerminalBlockEntity storage;

        private OpenedTerminal(ServerPlayer player) {
            this.player = player;
        }
    }
}
