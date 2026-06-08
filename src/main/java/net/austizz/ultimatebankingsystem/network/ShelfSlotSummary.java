package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;
import net.austizz.ultimatebankingsystem.compat.network.codec.ByteBufCodecs;
import net.austizz.ultimatebankingsystem.compat.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

public record ShelfSlotSummary(
        int slotIndex,
        int absoluteSlotIndex,
        String itemName,
        String itemId,
        ItemStack displayStack,
        long priceDollars,
        boolean configured,
        int stockCount,
        float offsetX,
        float offsetY,
        float offsetZ,
        float rotationX,
        float rotationY,
        float rotationZ,
        float scaleX,
        float scaleY,
        float scaleZ
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, Long> LONG_CODEC =
            StreamCodec.of((buf, value) -> buf.writeLong(value), RegistryFriendlyByteBuf::readLong);
    private static final StreamCodec<RegistryFriendlyByteBuf, Float> FLOAT_CODEC =
            StreamCodec.of((buf, value) -> buf.writeFloat(value), RegistryFriendlyByteBuf::readFloat);
    private static final StreamCodec<RegistryFriendlyByteBuf, ItemStack> ITEM_STACK_CODEC =
            StreamCodec.of(
                    (buf, stack) -> buf.writeItem(stack == null ? ItemStack.EMPTY : stack),
                    RegistryFriendlyByteBuf::readItem
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfSlotSummary> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.VAR_INT.encode(buf, payload.slotIndex());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.absoluteSlotIndex());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemName());
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemId());
                        ITEM_STACK_CODEC.encode(buf, payload.displayStack());
                        LONG_CODEC.encode(buf, payload.priceDollars());
                        ByteBufCodecs.BOOL.encode(buf, payload.configured());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.stockCount());
                        FLOAT_CODEC.encode(buf, payload.offsetX());
                        FLOAT_CODEC.encode(buf, payload.offsetY());
                        FLOAT_CODEC.encode(buf, payload.offsetZ());
                        FLOAT_CODEC.encode(buf, payload.rotationX());
                        FLOAT_CODEC.encode(buf, payload.rotationY());
                        FLOAT_CODEC.encode(buf, payload.rotationZ());
                        FLOAT_CODEC.encode(buf, payload.scaleX());
                        FLOAT_CODEC.encode(buf, payload.scaleY());
                        FLOAT_CODEC.encode(buf, payload.scaleZ());
                    },
                    buf -> new ShelfSlotSummary(
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ITEM_STACK_CODEC.decode(buf),
                            LONG_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf),
                            FLOAT_CODEC.decode(buf)
                    )
            );
}
