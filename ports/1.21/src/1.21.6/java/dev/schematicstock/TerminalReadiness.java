package dev.schematicstock;

import com.tom.storagemod.menu.StorageTerminalMenu;

public final class TerminalReadiness {
    private TerminalReadiness() {}

    public static boolean hasStockData(StorageTerminalMenu menu) {
        // Tom's Storage 2.4.1 has no itemsLoaded flag.
        return !menu.itemList.isEmpty() || (menu.slotCount > 0 && menu.slotCount < Short.MAX_VALUE
            && menu.freeCount == menu.slotCount);
    }
}
