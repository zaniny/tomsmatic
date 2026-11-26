package dev.schematicstock;

import com.tom.storagemod.menu.CraftingTerminalMenu;
import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.util.IDataReceiver;
import com.mojang.blaze3d.platform.InputConstants;
import dev.schematicstock.refill.RefillSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    private int availableX;
    private int nameWidth;
    private int craftX;

    StockScreen(String source, List<StockRow> rows, AbstractStorageTerminalScreen<?> terminal, boolean returnToTerminal) {
        super(label("title"));
        this.source = source;
        updateRows(rows);
        this.terminal = terminal;
        this.returnToTerminal = returnToTerminal;
        enableCrafting(terminal);
    }

    boolean attachTerminal(AbstractStorageTerminalScreen<?> terminal) {
        if (this.terminal != null) return false;
        this.terminal = terminal;
        enableCrafting(terminal);
        return true;
    }

    private void enableCrafting(AbstractStorageTerminalScreen<?> terminal) {
        if (terminal != null && terminal.getMenu() instanceof CraftingTerminalMenu) {
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
        double previousScroll = stockList == null ? 0 : stockList.scrollTarget;
        left = Math.max(6, (width - 620) / 2);
        stockList = addRenderableWidget(new StockList(left, width - 2 * left, Math.max(1, height - LIST_TOP - 29)));
        stockList.setScrollAmount(previousScroll);
        stockList.scrollTarget = stockList.scrollAmount();
        right = stockList.getRight() - (stockList.maxScrollAmount() > 0 ? stockList.scrollbarWidth() + 3 : 0);
        craftX = recipes == null ? right : right - 34;
        int span = craftX - left;
        requiredX = left + span * 55 / 100;
        availableX = left + span * 73 / 100;
        nameWidth = requiredX - left - 26 - span * 17 / 100;
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 10, 0xffffffff);
        graphics.centeredText(font, fit(source, right - left), width / 2, 22, 0xffb4c4d8);
        boolean unknown = rows.isEmpty() || rows.getFirst().available() < 0;
        Component status = craftStatus != null ? craftStatus
            : rows.isEmpty() ? label(materialsLoading ? "materials_loading" : "empty_list")
            : unknown ? label(stockLoading ? "stock_loading" : "stock_unknown")
            : missingTypes == 0 ? label("enough") : label("missing_types", missingTypes, rows.size());
        graphics.centeredText(font, fit(status.getString(), width - 16), width / 2, 34,
            craftStatus == null && !unknown && missingTypes == 0 ? 0xff80d99d : 0xffffbc85);
        graphics.text(font, label("item"), left + 2, 50, 0xffb4c4d8);
        alignRight(graphics, label("required").getString(), requiredX, 50, 0xffb4c4d8);
        alignRight(graphics, label("available").getString(), availableX, 50, 0xffb4c4d8);
        alignRight(graphics, label("shortfall").getString(), craftX - 2, 50, 0xffb4c4d8);
        if (recipes != null) graphics.centeredText(font, label("craft"), craftX + 17, 50, 0xffb4c4d8);
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
            if (returnToTerminal) minecraft.gui.setScreen(active);
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
        minecraft.gui.setScreen(terminal);
        minecraft.gameMode.handlePlaceRecipe(menu.containerId, recipe.id(), false);
    }

    private static String quantity(long value) {
        return value < 0 ? "—" : Long.toString(value);
    }

    private void showRowTooltip(GuiGraphicsExtractor graphics, StockRow row, int mouseX, int mouseY) {
        Component required = row.requiredStackTooltip();
        if (mouseX < left + 22 + nameWidth) {
            var item = row.item();
            var lines = new ArrayList<>(getTooltipFromItem(minecraft, item));
            lines.add(required);
            graphics.setTooltipForNextFrame(font, lines, item.getTooltipImage(), mouseX, mouseY,
                item.get(DataComponents.TOOLTIP_STYLE), true);
        } else if (mouseX >= craftX && recipes != null && recipes.hasRecipe(row.item())) {
            graphics.setComponentTooltipForNextFrame(font, List.of(required, label("craft_fill")), mouseX, mouseY);
        } else {
            graphics.setTooltipForNextFrame(font, required, mouseX, mouseY);
        }
    }

    private String fit(String text, int pixels) {
        return font.width(text) <= pixels ? text : font.plainSubstrByWidth(text, Math.max(0, pixels - font.width("…"))) + "…";
    }

    private void alignRight(GuiGraphicsExtractor graphics, String text, int x, int y, int color) {
        graphics.text(font, text, x - font.width(text), y, color);
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
            super(x, LIST_TOP, width, height, label("title"), defaultSettings(ROW_HEIGHT));
        }

        @Override
        protected int contentHeight() {
            return rows.size() * ROW_HEIGHT;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
            if (vertical == 0 || maxScrollAmount() == 0) return false;
            scrollTo(scrollTarget - vertical * ROW_HEIGHT);
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            double target = switch (event.key()) {
                case InputConstants.KEY_UP -> scrollTarget - ROW_HEIGHT;
                case InputConstants.KEY_DOWN -> scrollTarget + ROW_HEIGHT;
                case InputConstants.KEY_HOME -> 0;
                case InputConstants.KEY_END -> maxScrollAmount();
                default -> scrollTarget;
            };
            return scrollTo(target) || super.keyPressed(event);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (recipes != null && event.x() >= craftX && event.x() < right
                && event.y() >= getY() && event.y() < getBottom() && isValidClickButton(event.buttonInfo())) {
                int row = ((int) (event.y() - getY()) + (int) Math.round(scrollAmount())) / ROW_HEIGHT;
                if (row < rows.size() && recipes.hasRecipe(rows.get(row).item())) {
                    craft(rows.get(row));
                    return true;
                }
            }
            if (!updateScrolling(event)) return false;
            scrollTarget = scrollAmount();
            return true;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (!super.mouseDragged(event, deltaX, deltaY)) return false;
            scrollTarget = scrollAmount();
            return true;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            updateAnimation(System.nanoTime());
            int offset = (int) Math.round(scrollAmount());
            int start = offset / ROW_HEIGHT;
            int end = Math.min(rows.size(), Math.ceilDiv(offset + getHeight(), ROW_HEIGHT));
            boolean pointerInList = mouseY >= getY() && mouseY < getBottom();
            graphics.enableScissor(left, getY(), right, getBottom());
            for (int i = start; i < end; i++) {
                StockRow row = rows.get(i);
                int y = getY() + i * ROW_HEIGHT - offset;
                graphics.fill(left, y, right, y + ROW_HEIGHT - 1, i % 2 == 0 ? 0xb827303c : 0xb81d252f);
                graphics.item(row.item(), left + 2, y + 1);
                graphics.text(font, fit(row.name(), nameWidth), left + 22, y + 6, 0xfff2f5fa);
                alignRight(graphics, Long.toString(row.required()), requiredX, y + 6, 0xfff2f5fa);
                alignRight(graphics, quantity(row.available()), availableX, y + 6, 0xfff2f5fa);
                long shortfall = row.shortfall();
                alignRight(graphics, quantity(shortfall), craftX - 2, y + 6,
                    shortfall > 0 ? 0xffffb184 : 0xff80d99d);
                if (recipes != null && recipes.hasRecipe(row.item())) {
                    if (pointerInList && mouseX >= craftX && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT) {
                        graphics.fill(craftX, y, right, y + ROW_HEIGHT - 1, 0x55ffffff);
                    }
                    graphics.item(craftIcon, craftX + 9, y + 1);
                }
            }
            graphics.disableScissor();
            extractScrollbar(graphics, mouseX, mouseY);
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
