package dev.schematicstock;

import com.tom.storagemod.menu.CraftingTerminalMenu;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.util.IDataReceiver;
import com.mojang.blaze3d.platform.InputConstants;
import dev.schematicstock.refill.RefillSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.storage.ValueInput;

final class StockScreen extends Screen implements IDataReceiver {
    private static final int ROW_HEIGHT = 20;
    private static final int LIST_TOP = 62;
    private static final long SCROLL_DURATION_NANOS = 120_000_000;
    private final String source;
    private List<StockRow> rows;
    private long missingTypes;
    private AbstractStorageTerminalScreen<?> terminal;
    private final boolean returnToTerminal;
    private CraftingRecipes recipes;
    private ItemStack craftIcon = ItemStack.EMPTY;
    private boolean stockLoading;
    private boolean materialsLoading;
    private Component craftStatus;
    private StockList stockList;
    private int left;
    private int right;
    private int requiredX;
    private int placedX;
    private int availableX;
    private int columnWidth;
    private int nameWidth;
    private int craftX;

    StockScreen(String source, List<StockRow> rows, AbstractStorageTerminalScreen<?> terminal, boolean returnToTerminal) {
        super(label("title"));
        this.source = source;
        updateRows(rows);
        this.terminal = terminal;
        this.returnToTerminal = returnToTerminal;
    }

    boolean attachTerminal(AbstractStorageTerminalScreen<?> terminal) {
        if (this.terminal != null) return false;
        this.terminal = terminal;
        return true;
    }

    private void enableCrafting() {
        if (recipes == null && terminal != null && terminal.getMenu() instanceof CraftingTerminalMenu
            && minecraft.player != null && minecraft.level != null) {
            recipes = new CraftingRecipes(minecraft.player.getRecipeBook(), SlotDisplayContext.fromLevel(minecraft.level));
            craftIcon = new ItemStack(Items.CRAFTING_TABLE);
        }
    }

    void updateRows(List<StockRow> rows) {
        int previousSize = this.rows == null ? 0 : this.rows.size();
        this.rows = List.copyOf(rows);
        missingTypes = this.rows.stream().filter(row -> row.shortfall() > 0).count();
        if (stockList != null && previousSize != rows.size()) rebuildWidgets();
    }

    void setStockLoading(boolean stockLoading) {
        this.stockLoading = stockLoading;
    }

    void setMaterialsLoading(boolean materialsLoading) {
        this.materialsLoading = materialsLoading;
    }

    @Override
    public void receive(ValueInput data) {
        // Tom's dispatches stock packets to the screen that is currently visible.
        var active = activeTerminal();
        if (active != null) active.receive(data);
    }

    @Override
    protected void init() {
        enableCrafting();
        double previousScroll = stockList == null ? 0 : stockList.scrollTarget;
        left = Math.max(6, (width - 620) / 2);
        stockList = addRenderableWidget(new StockList(left, width - 2 * left, Math.max(1, height - LIST_TOP - 29)));
        stockList.setScrollAmount(previousScroll);
        stockList.scrollTarget = stockList.scrollAmount();
        right = stockList.getRight() - (stockList.maxScrollAmount() > 0 ? AbstractScrollArea.SCROLLBAR_WIDTH + 3 : 0);
        craftX = recipes == null ? right : right - 34;
        columnWidth = (craftX - left) * 7 / 40;
        availableX = craftX - 2 - columnWidth;
        placedX = availableX - columnWidth;
        requiredX = placedX - columnWidth;
        nameWidth = Math.max(0, requiredX - columnWidth - left - 22);
        addRenderableWidget(Button.builder(label("close"), button -> onClose())
            .bounds(width - 65, 5, 60, 20).build());
        Button refill = addRenderableWidget(Button.builder(refillLabel(), button -> {
            if (RefillSetting.toggle()) {
                button.setMessage(refillLabel());
                button.setTooltip(Tooltip.create(label("refill.hint")));
            } else {
                button.setTooltip(Tooltip.create(label("refill.save_failed")));
            }
        }).bounds(left, height - 24, Math.min(220, width - 2 * left), 20).build());
        refill.setTooltip(Tooltip.create(label("refill.hint")));
        setInitialFocus(stockList);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 10, UiColors.TITLE_TEXT);
        graphics.drawCenteredString(font, fit(source, right - left), width / 2, 22, UiColors.SECONDARY_TEXT);
        boolean unknown = rows.isEmpty() || rows.getLast().shortfall() < 0;
        Component status = craftStatus != null ? craftStatus
            : rows.isEmpty() ? label(materialsLoading ? "materials_loading" : "empty_list")
            : unknown ? label(stockLoading ? "stock_loading" : "stock_unknown")
            : missingTypes == 0 ? label("enough") : label("missing_types", missingTypes, rows.size());
        graphics.drawCenteredString(font, fit(status.getString(), width - 16), width / 2, 34,
            craftStatus == null && !unknown && missingTypes == 0 ? UiColors.POSITIVE_TEXT : UiColors.STATUS_WARNING_TEXT);
        graphics.drawString(font, label("item"), left + 2, 50, UiColors.SECONDARY_TEXT);
        alignRight(graphics, label("required").getString(), requiredX, 50, UiColors.SECONDARY_TEXT);
        alignRight(graphics, label("placed").getString(), placedX, 50, UiColors.SECONDARY_TEXT);
        alignRight(graphics, label("available").getString(), availableX, 50, UiColors.SECONDARY_TEXT);
        alignRight(graphics, label("shortfall").getString(), craftX - 2, 50, UiColors.SECONDARY_TEXT);
        if (recipes != null) graphics.drawCenteredString(font, label("craft"), craftX + 17, 50, UiColors.SECONDARY_TEXT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        return stockList.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        var active = activeTerminal();
        if (active != null) {
            if (returnToTerminal) ClientScreens.open(minecraft, active);
            else active.onClose();
        } else {
            super.onClose();
        }
    }

    AbstractStorageTerminalScreen<?> activeTerminal() {
        return terminal != null && minecraft.player != null && minecraft.player.containerMenu == terminal.getMenu()
            ? terminal : null;
    }

    private void craft(StockRow row) {
        if (activeTerminal() == null || minecraft.gameMode == null) {
            craftStatus = label("craft_terminal_unavailable");
            return;
        }
        var menu = (CraftingTerminalMenu) terminal.getMenu();
        if (!menu.getCarried().isEmpty()) {
            craftStatus = label("carried");
            return;
        }
        var contents = new StackedItemContents();
        minecraft.player.getInventory().getNonEquipmentItems().forEach(contents::accountSimpleStack);
        menu.fillCraftSlotsStackedContents(contents);
        var recipe = recipes.findCraftable(row.item(), contents);
        if (recipe == null) {
            craftStatus = label("craft_missing");
            return;
        }
        ClientScreens.open(minecraft, terminal);
        minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe.id(), false);
    }

    private static String quantity(long value) {
        return value < 0 ? "—" : Long.toString(value);
    }

    private void showRowTooltip(GuiGraphics graphics, StockRow row, int mouseX, int mouseY) {
        Component total = row.totalStackTooltip();
        Component shortfall = row.shortfallStackTooltip();
        if (mouseX < left + 22 + nameWidth) {
            var item = row.item();
            var lines = new ArrayList<>(getTooltipFromItem(minecraft, item));
            lines.add(total);
            lines.add(shortfall);
            graphics.setTooltipForNextFrame(font, lines, item.getTooltipImage(), mouseX, mouseY,
                item.get(DataComponents.TOOLTIP_STYLE));
        } else if (mouseX >= craftX && recipes != null && recipes.hasRecipe(row.item())) {
            graphics.setComponentTooltipForNextFrame(font, List.of(total, shortfall, label("craft_fill")), mouseX, mouseY);
        } else {
            graphics.setComponentTooltipForNextFrame(font, List.of(total, shortfall), mouseX, mouseY);
        }
    }

    private String fit(String text, int pixels) {
        return font.width(text) <= pixels ? text : font.plainSubstrByWidth(text, Math.max(0, pixels - font.width("…"))) + "…";
    }

    private void alignRight(GuiGraphics graphics, String text, int x, int y, int color) {
        var clipped = fit(text, Math.max(0, columnWidth - 4));
        graphics.drawString(font, clipped, x - font.width(clipped), y, color);
    }

    private static Component label(String key, Object... args) {
        return Component.translatable("schematic_stock." + key, args);
    }

    private static Component refillLabel() {
        return label(RefillSetting.enabled() ? "refill.enabled" : "refill.disabled");
    }

    private final class StockList extends AbstractScrollArea {
        private double scrollStart;
        private double scrollTarget;
        private long scrollStartedAt;

        private StockList(int x, int width, int height) {
            super(x, LIST_TOP, width, height, label("title"));
        }

        @Override
        protected int contentHeight() {
            return rows.size() * ROW_HEIGHT;
        }

        @Override
        protected double scrollRate() {
            return ROW_HEIGHT;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (vertical == 0 || maxScrollAmount() == 0) return false;
            scrollTo(scrollTarget - vertical * ROW_HEIGHT);
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            double target = switch (keyCode) {
                case InputConstants.KEY_UP -> scrollTarget - ROW_HEIGHT;
                case InputConstants.KEY_DOWN -> scrollTarget + ROW_HEIGHT;
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> maxScrollAmount();
                default -> scrollTarget;
            };
            return scrollTo(target) || super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (recipes != null && mouseX >= craftX && mouseX < right
                && mouseY >= getY() && mouseY < getBottom() && isValidClickButton(button)) {
                int row = ((int) (mouseY - getY()) + (int) Math.round(scrollAmount())) / ROW_HEIGHT;
                if (row < rows.size() && recipes.hasRecipe(rows.get(row).item())) {
                    craft(rows.get(row));
                    return true;
                }
            }
            if (!updateScrolling(mouseX, mouseY, button)) return false;
            scrollTarget = scrollAmount();
            return true;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return false;
            scrollTarget = scrollAmount();
            return true;
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            updateAnimation(System.nanoTime());
            int offset = (int) Math.round(scrollAmount());
            int start = offset / ROW_HEIGHT;
            int end = Math.min(rows.size(), Math.ceilDiv(offset + getHeight(), ROW_HEIGHT));
            boolean pointerInList = mouseY >= getY() && mouseY < getBottom();
            graphics.enableScissor(left, getY(), right, getBottom());
            for (int i = start; i < end; i++) {
                StockRow row = rows.get(i);
                int y = getY() + i * ROW_HEIGHT - offset;
                graphics.fill(left, y, right, y + ROW_HEIGHT - 1, i % 2 == 0 ? UiColors.EVEN_ROW_BACKGROUND : UiColors.ODD_ROW_BACKGROUND);
                graphics.renderItem(row.item(), left + 2, y + 1);
                graphics.drawString(font, fit(row.name(), nameWidth), left + 22, y + 6, UiColors.ROW_TEXT);
                alignRight(graphics, Long.toString(row.required()), requiredX, y + 6, UiColors.ROW_TEXT);
                alignRight(graphics, quantity(row.placed()), placedX, y + 6, UiColors.ROW_TEXT);
                alignRight(graphics, quantity(row.available()), availableX, y + 6, UiColors.ROW_TEXT);
                long shortfall = row.shortfall();
                alignRight(graphics, quantity(shortfall), craftX - 2, y + 6,
                    shortfall > 0 ? UiColors.SHORTFALL_TEXT : UiColors.POSITIVE_TEXT);
                if (recipes != null && recipes.hasRecipe(row.item())) {
                    if (pointerInList && mouseX >= craftX && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                        graphics.fill(craftX, y, right, y + ROW_HEIGHT - 1, UiColors.CRAFT_HOVER_BACKGROUND);
                    }
                    graphics.renderItem(craftIcon, craftX + 9, y + 1);
                }
            }
            graphics.disableScissor();
            renderScrollbar(graphics);
            if (pointerInList && mouseX >= left && mouseX < right) {
                int hovered = (mouseY - getY() + offset) / ROW_HEIGHT;
                if (hovered < rows.size()) {
                    showRowTooltip(graphics, rows.get(hovered), mouseX, mouseY);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, getMessage());
        }

        private boolean scrollTo(double requested) {
            double target = Math.clamp(requested, 0, maxScrollAmount());
            if (target == scrollTarget) return false;
            long now = System.nanoTime();
            updateAnimation(now);
            scrollStart = scrollAmount();
            scrollTarget = target;
            scrollStartedAt = now;
            return true;
        }

        private void updateAnimation(long now) {
            if (scrollAmount() == scrollTarget) return;
            double progress = Math.clamp((now - scrollStartedAt) / (double) SCROLL_DURATION_NANOS, 0, 1);
            double remainder = 1 - progress;
            setScrollAmount(scrollTarget + (scrollStart - scrollTarget) * remainder * remainder);
        }
    }
}
