package dev.schematicstock.refill;

import dev.schematicstock.refill.RefillPackets.Reply;
import dev.schematicstock.refill.RefillPackets.Request;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

final class RefillPayloadRegistration {
    private RefillPayloadRegistration() {}

    static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("schematic_stock", path));
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Reply.TYPE, Reply.CODEC);
    }
}
