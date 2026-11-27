package dev.schematicstock.refill;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

public final class RefillPackets {
    private RefillPackets() {}

    public static void register() {
        RefillPayloadRegistration.register();
    }

    public record Request(int id, ItemStack item) implements CustomPacketPayload {
        public static final Type<Request> TYPE = RefillPayloadRegistration.type("refill_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Request::id, ItemStack.STREAM_CODEC, Request::item, Request::new);

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record Reply(int id, int result) implements CustomPacketPayload {
        public static final Type<Reply> TYPE = RefillPayloadRegistration.type("refill_result_v1");
        public static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Reply::id, ByteBufCodecs.VAR_INT, Reply::result, Reply::new);

        @Override
        public Type<Reply> type() {
            return TYPE;
        }
    }

    public enum Result {
        OK, NO_TERMINAL, UNAVAILABLE, NO_STOCK, INVENTORY_FULL, BUSY;

        public static Result decode(int value) {
            return value >= 0 && value < values().length ? values()[value] : UNAVAILABLE;
        }
    }
}
