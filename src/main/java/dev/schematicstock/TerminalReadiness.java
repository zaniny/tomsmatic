package dev.schematicstock;

import com.tom.storagemod.menu.StorageTerminalMenu;

public final class TerminalReadiness {
    private TerminalReadiness() {}

    public static boolean hasStockData(StorageTerminalMenu menu) {
        return menu.itemsLoaded || (menu.slotCount > 0 && menu.slotCount < Short.MAX_VALUE
            && menu.freeCount == menu.slotCount);
    }
}
