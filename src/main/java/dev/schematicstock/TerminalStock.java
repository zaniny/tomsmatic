package dev.schematicstock;

import com.tom.storagemod.inventory.StoredItemStack;
import com.tom.storagemod.inventory.TerminalItemStack;
import net.minecraft.world.item.ItemStack;

final class TerminalStock {
    private TerminalStock() {}

    static StoredItemStack key(ItemStack item) {
        return new TerminalItemStack(item);
    }
}
