package dev.schematicstock;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListBase;
import java.lang.ref.WeakReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

final class StockSelection {
    private WeakReference<ClientLevel> level = new WeakReference<>(null);
    private MaterialListBase list;

    void select(Minecraft client, MaterialListBase list) {
        level = new WeakReference<>(client.level);
        this.list = list;
    }

    MaterialListBase forLevel(Minecraft client) {
        if (level.get() != client.level || client.level == null) {
            level.clear();
            list = null;
        }
        return list;
    }

    MaterialListBase forComparison(Minecraft client) {
        var opened = forLevel(client);
        if (opened != null) return opened;
        var placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        return placement == null ? null : placement.getMaterialList();
    }
}
