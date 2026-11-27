package dev.schematicstock;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ClientScreens {
    private ClientScreens() {}

    public static Screen current(Minecraft client) {
        return client.screen;
    }

    public static void open(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    public static void registerKeyMapping(KeyMapping key) {
        KeyBindingHelper.registerKeyBinding(key);
    }

    public static void showOverlay(Minecraft client, Component message) {
        client.player.displayClientMessage(message, true);
    }
}
