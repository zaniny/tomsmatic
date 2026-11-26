package dev.schematicstock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;

final class CraftingRecipes {
    private final Map<Item, List<Choice>> byItem = new HashMap<>();

    CraftingRecipes(ClientRecipeBook book, ContextMap context) {
        for (var collection : book.getCollections()) {
            for (var entry : collection.getRecipes()) {
                if (!(entry.display() instanceof ShapedCraftingRecipeDisplay
                    || entry.display() instanceof ShapelessCraftingRecipeDisplay)
                    || entry.craftingRequirements().isEmpty()) continue;
                for (var output : entry.resultItems(context)) {
                    if (!output.isEmpty()) {
                        byItem.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>()).add(new Choice(output, entry));
                    }
                }
            }
        }
    }

    boolean hasRecipe(ItemStack item) {
        return choices(item).stream().anyMatch(choice -> ItemStack.isSameItemSameComponents(item, choice.output()));
    }

    RecipeDisplayEntry findCraftable(ItemStack item, StackedItemContents contents) {
        return choices(item).stream()
            .filter(choice -> ItemStack.isSameItemSameComponents(item, choice.output()))
            .map(Choice::entry)
            .filter(entry -> entry.canCraft(contents))
            .min(java.util.Comparator.comparingInt(entry -> entry.id().index()))
            .orElse(null);
    }

    private List<Choice> choices(ItemStack item) {
        return byItem.getOrDefault(item.getItem(), List.of());
    }

    private record Choice(ItemStack output, RecipeDisplayEntry entry) {}
}
