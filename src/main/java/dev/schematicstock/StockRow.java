package dev.schematicstock;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

record StockRow(ItemStack item, long required, long placed, long available) {
    String name() {
        return item.getHoverName().getString();
    }

    long shortfall() {
        long remaining = required - Math.max(0, placed);
        if (remaining == 0) return 0;
        return available < 0 ? -1 : Math.max(0, remaining - available);
    }

    Component totalStackTooltip() {
        return Component.translatable("schematic_stock.tooltip.total", formatStackQuantity(required));
    }

    Component shortfallStackTooltip() {
        return Component.translatable("schematic_stock.tooltip.shortfall", formatStackQuantity(shortfall()));
    }

    private Component formatStackQuantity(long quantity) {
        if (quantity < 0) return Component.literal("—");
        int stackSize = item.getMaxStackSize();
        if (stackSize <= 1 || quantity < stackSize) {
            return Component.translatable("schematic_stock.tooltip.items", quantity);
        }
        long stacks = quantity / stackSize;
        long remainder = quantity % stackSize;
        if (remainder == 0) {
            return Component.translatable("schematic_stock.tooltip.stacks", stacks, stackSize);
        }
        return Component.translatable("schematic_stock.tooltip.stacks_and_items", stacks, stackSize, remainder);
    }
}
