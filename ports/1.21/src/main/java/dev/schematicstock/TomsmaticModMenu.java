package dev.schematicstock;

import com.mojang.blaze3d.platform.InputConstants;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.schematicstock.refill.RefillSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public final class TomsmaticModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return Settings::new;
    }

    private static final class Settings extends Screen {
        private final Screen parent;
        private Component status;
        private Button hotkey;
        private Button clearHotkey;
        private boolean capturingHotkey;

        private Settings(Screen parent) {
            super(text("settings"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int x = width / 2 - 112;
            int y = height / 2 - 48;
            addRenderableWidget(Button.builder(text("open_materials"), button -> {
                status = SchematicStock.openStandalone(minecraft);
            }).bounds(x, y, 224, 20).build());
            hotkey = addRenderableWidget(Button.builder(hotkeyLabel(), button -> {
                capturingHotkey = true;
                refreshHotkeyButtons();
            }).bounds(x, y + 24, 150, 20).build());
            clearHotkey = addRenderableWidget(Button.builder(text("hotkey.clear"), button -> {
                saveHotkey(InputConstants.UNKNOWN);
            }).bounds(x + 154, y + 24, 70, 20).build());
            refreshHotkeyButtons();
            addRenderableWidget(Button.builder(refillLabel(), button -> {
                status = RefillSetting.toggle() ? null : text("refill.save_failed");
                button.setMessage(refillLabel());
            }).bounds(x, y + 48, 224, 20).build());
            addRenderableWidget(Button.builder(text("close"), button -> onClose())
                .bounds(x, y + 72, 224, 20).build());
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!capturingHotkey) return super.keyPressed(event);
            if (event.isEscape()) {
                capturingHotkey = false;
                refreshHotkeyButtons();
            } else {
                saveHotkey(InputConstants.getKey(event));
            }
            return true;
        }

        private void saveHotkey(InputConstants.Key key) {
            SchematicStock.OPEN_MATERIALS_KEY.setKey(key);
            KeyMapping.resetMapping();
            minecraft.options.save();
            capturingHotkey = false;
            refreshHotkeyButtons();
        }

        private void refreshHotkeyButtons() {
            boolean enabled = FabricLoader.getInstance().isModLoaded("litematica");
            hotkey.setMessage(hotkeyLabel());
            hotkey.active = enabled;
            clearHotkey.active = enabled && !SchematicStock.OPEN_MATERIALS_KEY.isUnbound();
        }

        private Component hotkeyLabel() {
            if (capturingHotkey) return text("hotkey.press");
            var mapping = SchematicStock.OPEN_MATERIALS_KEY;
            return Component.translatable("schematic_stock.hotkey.current",
                mapping.isUnbound() ? text("hotkey.unbound") : mapping.getTranslatedKeyMessage());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            super.render(graphics, mouseX, mouseY, delta);
            graphics.drawCenteredString(font, title, width / 2, Math.max(5, height / 2 - 66), UiColors.TITLE_TEXT);
            if (status != null) {
                int y = height / 2 + 50;
                for (var line : font.split(status, width - 16)) {
                    graphics.drawCenteredString(font, line, width / 2, y, UiColors.NOTICE_TEXT);
                    y += 11;
                }
            }
        }

        @Override
        public void onClose() {
            ClientScreens.open(minecraft, parent);
        }

        private static Component refillLabel() {
            return text(RefillSetting.enabled() ? "refill.enabled" : "refill.disabled");
        }

        private static Component text(String key) {
            return Component.translatable("schematic_stock." + key);
        }
    }
}
