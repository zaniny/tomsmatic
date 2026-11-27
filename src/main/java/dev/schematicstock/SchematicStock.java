package dev.schematicstock;

import com.tom.storagemod.inventory.StoredItemStack;
import com.tom.storagemod.item.WirelessTerminal;
import com.tom.storagemod.menu.StorageTerminalMenu;
import com.tom.storagemod.network.NetworkHandler;
import com.tom.storagemod.network.OpenTerminalPacket;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.util.PlayerInvUtil;
import com.mojang.blaze3d.platform.InputConstants;
import dev.schematicstock.refill.RefillClient;
import fi.dy.masa.litematica.gui.GuiMaterialList;
import fi.dy.masa.litematica.materials.MaterialListBase;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.ToLongFunction;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

public final class SchematicStock implements ClientModInitializer {
    private static final int UNTRACKED_MATERIAL_WAIT_TICKS = 200;
    private static final int STOCK_WAIT_TICKS = 200;
    static final KeyMapping OPEN_MATERIALS_KEY = new KeyMapping("key.schematic_stock.materials",
        InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    private static final StockSelection SELECTION = new StockSelection();
    private static PendingOpen pending;

    @Override
    public void onInitializeClient() {
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return;
        RefillClient.initialize();
        ClientScreens.registerKeyMapping(OPEN_MATERIALS_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pollTerminal(client);
            while (OPEN_MATERIALS_KEY.consumeClick()) {
                if (client.player != null && ClientScreens.current(client) == null) {
                    Component error = openStandalone(client);
                    if (error != null) ClientScreens.showOverlay(client, error);
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
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return message("select_list");
        MaterialListBase source = SELECTION.forComparison(client);
        if (source == null) {
            return message("select_list");
        }
        var menu = (StorageTerminalMenu) terminal.getMenu();
        if (!menu.getCarried().isEmpty()) {
            return message("carried");
        }
        if (!stockReady(menu)) {
            return message(menu.slotCount <= 0 ? "waiting_network" : "waiting");
        }
        if (source instanceof PlacementMaterialLoad load) {
            load.refreshMaterials();
            var report = new StockScreen(source.getName(), List.of(), terminal, true);
            openReport(client, source, report, load.materialsLoading());
            return null;
        }

        List<StockRow> rows = compareMaterials(source, menu.sync::getAmount);
        if (rows.isEmpty()) {
            return message("empty_list");
        }
        ClientScreens.open(client, new StockScreen(source.getName(), rows, terminal, true));
        return null;
    }

    static Component openStandalone(Minecraft client) {
        if (client.level == null || client.player == null || !client.player.isAlive()
            || client.player.containerMenu != client.player.inventoryMenu) return message("materials_unavailable");
        if (!FabricLoader.getInstance().isModLoaded("litematica")) return message("select_list");
        MaterialListBase source = SELECTION.forComparison(client);
        if (source == null) return message("select_list");
        boolean waiting = source.getMaterialsAll().isEmpty();
        if (source instanceof PlacementMaterialLoad load) {
            load.refreshMaterials();
            waiting = load.materialsLoading();
        }
        var report = new StockScreen(source.getName(), List.of(), null, false);
        openReport(client, source, report, waiting);
        return null;
    }

    private static void openReport(Minecraft client, MaterialListBase source, StockScreen report, boolean waiting) {
        report.setMaterialsLoading(waiting);
        ClientScreens.open(client, report);
        if (waiting) {
            pending = new PendingOpen(client.level, source, report, true);
        } else {
            loadStock(client, source, report);
        }
    }

    private static void loadStock(Minecraft client, MaterialListBase source, StockScreen report) {
        pending = null;
        report.setMaterialsLoading(false);
        var active = report.activeTerminal();
        if (active != null && active.getMenu() instanceof StorageTerminalMenu menu) {
            boolean ready = stockReady(menu);
            var request = new PendingOpen(client.level, source, report, false);
            report.updateRows(compareMaterials(source, item -> ready ? menu.sync.getAmount(item) : -1));
            if (ready) request.lastItems = menu.itemList;
            report.setStockLoading(!ready);
            pending = request;
            return;
        }
        var rows = compareMaterials(source, item -> -1);
        report.updateRows(rows);
        if (rows.isEmpty()) return;
        boolean hasWirelessTerminal = PlayerInvUtil.findItem(client.player,
            stack -> stack.getItem() instanceof WirelessTerminal terminal && terminal.canOpen(stack),
            false, stack -> true);
        if (hasWirelessTerminal && ClientPlayNetworking.canSend(OpenTerminalPacket.ID)) {
            pending = new PendingOpen(client.level, source, report, false);
            report.setStockLoading(true);
            NetworkHandler.openTerminal();
        }
    }

    public static void keepMaterialsVisible(Minecraft client, Screen previousScreen) {
        var request = pending;
        if (request == null || request.waitingForMaterials || previousScreen != request.report
            || client.level != request.level || client.player == null
            || !(ClientScreens.current(client) instanceof AbstractStorageTerminalScreen<?> terminal)
            || terminal.getMenu() != client.player.containerMenu) return;

        if (request.report.attachTerminal(terminal)) ClientScreens.open(client, request.report);
    }

    private static void pollTerminal(Minecraft client) {
        var request = pending;
        if (request == null) return;
        if (client.level != request.level || client.player == null
            || ClientScreens.current(client) != request.report) {
            pending = null;
            return;
        }
        if (request.waitingForMaterials) {
            boolean waiting = request.source instanceof PlacementMaterialLoad load ? load.materialsLoading()
                : request.source.getMaterialsAll().isEmpty() && ++request.age < UNTRACKED_MATERIAL_WAIT_TICKS;
            if (!waiting) loadStock(client, request.source, request.report);
            return;
        }
        if (request.age < STOCK_WAIT_TICKS) request.age++;
        boolean timedOut = request.age >= STOCK_WAIT_TICKS;
        var terminal = request.report.activeTerminal();
        if (terminal != null && terminal.getMenu() instanceof StorageTerminalMenu menu && menu.getCarried().isEmpty()) {
            // Tom's replaces itemList on every stock packet, including chunks of the initial list.
            if (stockReady(menu) && request.lastItems != menu.itemList) {
                request.lastItems = menu.itemList;
                request.report.updateRows(compareMaterials(request.source, menu.sync::getAmount));
                request.report.setStockLoading(false);
            } else if (timedOut) {
                request.report.setStockLoading(false);
            }
        } else if (timedOut) {
            request.report.setStockLoading(false);
            pending = null;
        }
    }

    private static boolean stockReady(StorageTerminalMenu menu) {
        return menu.itemsLoaded || (menu.slotCount > 0 && menu.slotCount < Short.MAX_VALUE
            && menu.freeCount == menu.slotCount);
    }

    static List<StockRow> compareMaterials(MaterialListBase source,
                                           ToLongFunction<StoredItemStack> stock) {
        boolean tracksPlacement = source instanceof PlacementMaterialLoad;
        int multiplier = source.getMultiplier();
        var required = new LinkedHashMap<StoredItemStack, MaterialCounts>();
        for (var material : source.getMaterialsAll()) {
            long total = material.getCountTotal();
            long count = total * multiplier;
            if (count > 0 && !material.getStack().isEmpty()) {
                long placed = tracksPlacement ? Math.clamp(total - material.getCountMissing(), 0, total) : 0;
                required.merge(TerminalStock.key(material.getStack().copyWithCount(1)),
                    new MaterialCounts(count, placed), MaterialCounts::add);
            }
        }
        return required.entrySet().stream()
            .map(entry -> {
                var key = entry.getKey();
                var counts = entry.getValue();
                return new StockRow(key.getStack(), counts.required(), tracksPlacement ? counts.placed() : -1,
                    stock.applyAsLong(key));
            })
            .sorted(Comparator.comparingLong(StockRow::shortfall).reversed().thenComparing(StockRow::name))
            .toList();
    }

    private record MaterialCounts(long required, long placed) {
        MaterialCounts add(MaterialCounts other) {
            return new MaterialCounts(required + other.required, placed + other.placed);
        }
    }

    private static Component message(String key) {
        return Component.translatable("schematic_stock." + key);
    }

    private static final class PendingOpen {
        private final ClientLevel level;
        private final MaterialListBase source;
        private final StockScreen report;
        private final boolean waitingForMaterials;
        private List<?> lastItems;
        private int age;

        private PendingOpen(ClientLevel level, MaterialListBase source, StockScreen report, boolean waitingForMaterials) {
            this.level = level;
            this.source = source;
            this.report = report;
            this.waitingForMaterials = waitingForMaterials;
        }
    }
}
