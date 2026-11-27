package dev.schematicstock;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class CompareButton extends Button {
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("schematic_stock", "icon/checklist");

    public CompareButton(OnPress onPress) {
        super(0, 0, 18, 18, Component.translatable("schematic_stock.check"), onPress, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(getMessage()));
    }

    public boolean isPrimaryClick(MouseButtonEvent event) {
        return isValidClickButton(event.buttonInfo());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, getX() + 3, getY() + 3, 12, 12);
    }
}
