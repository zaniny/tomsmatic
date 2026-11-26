package dev.schematicstock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

record StockRow(ItemStack item, long required, long available) {
    String name() {
        return item.getHoverName().getString();
    }

    long shortfall() {
        return available < 0 ? -1 : Math.max(0, required - available);
    }

    Component requiredStackTooltip() {
        int stackSize = item.getMaxStackSize();
        if (stackSize <= 1 || required < stackSize) {
            return Component.translatable("schematic_stock.tooltip.required_items", required);
        }
        long stacks = required / stackSize;
        long remainder = required % stackSize;
        if (remainder == 0) {
            return Component.translatable("schematic_stock.tooltip.required_stacks", stacks, stackSize);
        }
        return Component.translatable("schematic_stock.tooltip.required_stacks_and_items", stacks, stackSize, remainder);
    }
}
