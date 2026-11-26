package dev.schematicstock;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class CompareButton extends Button {
    private static final Identifier ICON = Identifier.withDefaultNamespace("icon/search");

    public CompareButton(OnPress onPress) {
        super(0, 0, 18, 18, Component.translatable("schematic_stock.check"), onPress, DEFAULT_NARRATION);
        setTooltip(Tooltip.create(getMessage()));
    }

    public boolean isPrimaryClick(MouseButtonEvent event) {
        return isValidClickButton(event.buttonInfo());
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, getX() + 3, getY() + 3, 12, 12);
    }
}
