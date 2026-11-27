package dev.schematicstock.refill;

import dev.schematicstock.refill.RefillPackets.Reply;
import dev.schematicstock.refill.RefillPackets.Request;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

final class RefillPayloadRegistration {
    private RefillPayloadRegistration() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("schematic_stock", path));
    }

    static void register() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.TYPE, Reply.CODEC);
    }
}
