package dev.schematicstock.mixin;

import com.tom.storagemod.screen.AbstractStorageTerminalScreen;
import com.tom.storagemod.screen.TSContainerScreen;
import dev.schematicstock.CompareButton;
import dev.schematicstock.SchematicStock;
import dev.schematicstock.UiColors;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractStorageTerminalScreen.class)
abstract class TerminalScreenMixin extends TSContainerScreen<AbstractContainerMenu> {
    @Shadow protected int rowCount;
    @Unique private CompareButton tomsmatic$button;
    @Unique private Component tomsmatic$status;
    @Unique private long tomsmatic$statusUntil;
    @Unique private boolean tomsmatic$consumeRelease;

    private TerminalScreenMixin(AbstractContainerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void tomsmatic$addCompareButton(CallbackInfo callback) {
        tomsmatic$button = addRenderableWidget(new CompareButton(button -> {
            tomsmatic$status = SchematicStock.compare(minecraft, (AbstractStorageTerminalScreen<?>) (Object) this);
            tomsmatic$statusUntil = System.nanoTime() + 7_000_000_000L;
        }));
        tomsmatic$positionButton();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void tomsmatic$positionBeforeRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                               float delta, CallbackInfo callback) {
        tomsmatic$positionButton();
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void tomsmatic$renderStatus(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       float delta, CallbackInfo callback) {
        if (tomsmatic$status == null || System.nanoTime() - tomsmatic$statusUntil >= 0) return;
        int panelWidth = Math.min(width - 12, 330);
        var lines = font.split(tomsmatic$status, panelWidth - 12);
        int x = (width - panelWidth) / 2;
        int panelHeight = lines.size() * 11 + 10;
        int y = height - panelHeight - 5;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, UiColors.NOTICE_BACKGROUND);
        for (var line : lines) {
            graphics.centeredText(font, line, width / 2, y + 5, UiColors.NOTICE_TEXT);
            y += 11;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void tomsmatic$handleCompareClick(MouseButtonEvent event, boolean doubleClick,
                                             CallbackInfoReturnable<Boolean> callback) {
        tomsmatic$positionButton();
        if (tomsmatic$button != null && tomsmatic$button.mouseClicked(event, doubleClick)) {
            tomsmatic$consumeRelease = true;
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void tomsmatic$handleCompareRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> callback) {
        if (tomsmatic$consumeRelease && tomsmatic$button.isPrimaryClick(event)) {
            tomsmatic$consumeRelease = false;
            callback.setReturnValue(true);
        }
    }

    @Unique
    private void tomsmatic$positionButton() {
        if (tomsmatic$button == null) return;
        // Tom's beacon is at (leftPos + 176, topPos + 24 + 18 * rowCount).
        tomsmatic$button.setX(leftPos + 175);
        tomsmatic$button.setY(topPos + 42 + 18 * rowCount);
    }
}
