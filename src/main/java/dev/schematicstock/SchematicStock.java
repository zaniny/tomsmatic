package dev.schematicstock;

import com.tom.storagemod.inventory.TerminalItemStack;
import com.tom.storagemod.menu.StorageTerminalMenu;
import com.tom.storagemod.network.NetworkHandler;
import com.tom.storagemod.network.OpenTerminalPacket;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.mojang.blaze3d.platform.InputConstants;
import dev.schematicstock.refill.RefillClient;
import dev.schematicstock.refill.RefillServer;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.ToLongFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

public final class SchematicStock implements ClientModInitializer {
    static final KeyMapping OPEN_MATERIALS_KEY = new KeyMapping("key.schematic_stock.materials",
        InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    private static final StockSelection SELECTION = new StockSelection();
    private static PendingOpen pending;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        RefillClient.initialize();
        KeyMappingHelper.registerKeyMapping(OPEN_MATERIALS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pollTerminal(client);
            while (OPEN_MATERIALS_KEY.consumeClick()) {
                if (client.player != null && client.gui.screen() == null) {
                    Component error = openStandalone(client);
                    if (error != null) client.player.sendOverlayMessage(error);
                }
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof GuiMaterialList materials) {
                SELECTION.select(client, materials.getMaterialList());
            }
        });
    }

    public static Component compare(Minecraft client, AbstractStorageTerminalScreen<?> terminal) {
        MaterialListBase source = SELECTION.forLevel(client);
        if (source == null) {
            return message("select_list");
        }
        if (source.getMaterialsAll().isEmpty()) {
            return message("empty_list");
        }
        var menu = (StorageTerminalMenu) terminal.getMenu();
        if (!menu.getCarried().isEmpty()) {
            return message("carried");
        }
        if (!stockReady(menu)) {
            return message(menu.slotCount <= 0 ? "waiting_network" : "waiting");
        }

        List<StockRow> rows = compareMaterials(source, menu.sync::getAmount);
        if (rows.isEmpty()) {
            return message("empty_list");
        }
        client.gui.setScreen(new StockScreen(source.getName(), rows, terminal, true));
        return null;
    }

    static Component openStandalone(Minecraft client) {
        if (client.level == null || client.player == null || !client.player.isAlive()
            || client.player.containerMenu != client.player.inventoryMenu) return message("materials_unavailable");
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return message("select_list");
        MaterialListBase source = SELECTION.forLevel(client);
        if (source == null) {
            var placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
            if (placement == null) return message("select_list");
            source = placement.getMaterialList();
        }
        if (source.getMaterialsAll().isEmpty()) {
            var report = new StockScreen(source.getName(), List.of(), null, false);
            client.gui.setScreen(report);
            pending = new PendingOpen(client.level, source, report, true);
            return null;
        }
        showStandalone(client, source);
        return null;
    }

    private static void showStandalone(Minecraft client, MaterialListBase source) {
        pending = null;
        var report = new StockScreen(source.getName(), compareMaterials(source, item -> -1), null, false);
        client.gui.setScreen(report);
        if (ClientPlayNetworking.canSend(OpenTerminalPacket.ID) && !RefillServer.findWireless(client.player).isEmpty()) {
            pending = new PendingOpen(client.level, source, report, false);
            NetworkHandler.openTerminal();
        }
    }

    public static void keepMaterialsVisible(Minecraft client, Screen previousScreen) {
        var request = pending;
        if (request == null || request.waitingForMaterials || previousScreen != request.report
            || client.level != request.level || client.player == null
            || !(client.gui.screen() instanceof AbstractStorageTerminalScreen<?> terminal)
            || terminal.getMenu() != client.player.containerMenu) return;

        if (request.report.attachTerminal(terminal)) client.gui.setScreen(request.report);
    }

    private static void pollTerminal(Minecraft client) {
        var request = pending;
        if (request == null) return;
        if (client.level != request.level || client.player == null
            || client.gui.screen() != request.report) {
            pending = null;
            return;
        }
        if (request.waitingForMaterials) {
            if (++request.age >= 200) pending = null;
            else if (!request.source.getMaterialsAll().isEmpty()) showStandalone(client, request.source);
            return;
        }
        boolean timedOut = ++request.age >= 60;
        var terminal = request.report.activeTerminal();
        if (terminal != null && terminal.getMenu() instanceof StorageTerminalMenu menu && menu.getCarried().isEmpty()) {
            if (!timedOut && !stockReady(menu)) return;
            pending = null;
            boolean ready = stockReady(menu);
            var rows = compareMaterials(request.source, item -> ready ? menu.sync.getAmount(item) : -1);
            client.gui.setScreen(new StockScreen(request.source.getName(), rows, terminal, false));
        } else if (timedOut) {
            pending = null;
        }
    }

    private static boolean stockReady(StorageTerminalMenu menu) {
        return menu.itemsLoaded || (menu.slotCount > 0 && menu.slotCount < Short.MAX_VALUE
            && menu.freeCount == menu.slotCount);
    }

    static List<StockRow> compareMaterials(MaterialListBase source,
                                           ToLongFunction<TerminalItemStack> stock) {
        var required = new LinkedHashMap<TerminalItemStack, Long>();
        for (var material : source.getMaterialsAll()) {
            long count = (long) material.getCountTotal() * source.getMultiplier();
            if (count > 0 && !material.getStack().isEmpty()) {
                required.merge(new TerminalItemStack(material.getStack().copyWithCount(1)), count, Long::sum);
            }
        }
        return required.entrySet().stream()
            .map(entry -> {
                var key = entry.getKey();
                return new StockRow(key.getStack(), entry.getValue(), stock.applyAsLong(key));
            })
            .sorted(Comparator.comparingLong(StockRow::shortfall).reversed().thenComparing(StockRow::name))
            .toList();
    }

    private static Component message(String key) {
        return Component.translatable("schematic_stock." + key);
    }

    private static final class PendingOpen {
        private final ClientLevel level;
        private final MaterialListBase source;
        private final StockScreen report;
        private final boolean waitingForMaterials;
        private int age;

        private PendingOpen(ClientLevel level, MaterialListBase source, StockScreen report, boolean waitingForMaterials) {
            this.level = level;
            this.source = source;
            this.report = report;
            this.waitingForMaterials = waitingForMaterials;
        }
    }
}
