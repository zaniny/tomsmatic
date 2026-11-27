package dev.schematicstock;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
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
        KeyMappingHelper.registerKeyMapping(key);
    }

    public static void showOverlay(Minecraft client, Component message) {
        client.player.sendOverlayMessage(message);
    }
}
