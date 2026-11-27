package dev.schematicstock;

import com.tom.storagemod.inventory.StoredItemStack;
import net.minecraft.world.item.ItemStack;

final class TerminalStock {
    private TerminalStock() {}

    static StoredItemStack key(ItemStack item) {
        return new StoredItemStack(item);
    }
}
