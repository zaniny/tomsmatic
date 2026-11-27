package dev.schematicstock.refill;

import dev.schematicstock.refill.RefillPackets.Reply;
import dev.schematicstock.refill.RefillPackets.Request;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

final class RefillPayloadRegistration {
    private RefillPayloadRegistration() {}

    static void register() {
        PayloadTypeRegistry.playC2S().register(Request.TYPE, Request.CODEC);
        PayloadTypeRegistry.playS2C().register(Reply.TYPE, Reply.CODEC);
    }
}
