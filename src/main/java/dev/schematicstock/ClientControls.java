package dev.schematicstock;

import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.litematica.config.Configs;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.util.WorldUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class ClientControls {
    private ClientControls() {}

    static KeyMapping materialKey() {
        return new KeyMapping("key.schematic_stock.materials", InputConstants.UNKNOWN.getValue(), KeyMapping.Category.MISC);
    }

    public static void retryEasyPlace(Minecraft client) {
        if (Configs.Generic.EASY_PLACE_POST_REWRITE.getBooleanValue()) {
            EasyPlaceUtils.handleEasyPlaceWithMessage();
        } else {
            WorldUtils.handleEasyPlace(client);
        }
    }
}
