package net.austizz.ultimatebankingsystem.compat.network.codec;

import net.austizz.ultimatebankingsystem.compat.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Minimal codec helpers used by UBS payload records.
 */
public final class ByteBufCodecs {
    private ByteBufCodecs() {
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Boolean> BOOL =
            StreamCodec.of(RegistryFriendlyByteBuf::writeBoolean, RegistryFriendlyByteBuf::readBoolean);

    public static final StreamCodec<RegistryFriendlyByteBuf, Integer> VAR_INT =
            StreamCodec.of(RegistryFriendlyByteBuf::writeVarInt, RegistryFriendlyByteBuf::readVarInt);

    public static final StreamCodec<RegistryFriendlyByteBuf, Long> VAR_LONG =
            StreamCodec.of(RegistryFriendlyByteBuf::writeVarLong, RegistryFriendlyByteBuf::readVarLong);

    public static final StreamCodec<RegistryFriendlyByteBuf, String> STRING_UTF8 =
            StreamCodec.of(
                    (buf, value) -> buf.writeUtf(value == null ? "" : value, 32767),
                    buf -> buf.readUtf(32767)
            );

    public static <T> Function<StreamCodec<RegistryFriendlyByteBuf, T>, StreamCodec<RegistryFriendlyByteBuf, List<T>>> list(int maxSize) {
        return elementCodec -> StreamCodec.of(
                (buf, values) -> {
                    List<T> safe = values == null ? List.of() : values;
                    if (safe.size() > maxSize) {
                        throw new IllegalArgumentException("List size " + safe.size() + " exceeds max " + maxSize);
                    }
                    buf.writeVarInt(safe.size());
                    for (T value : safe) {
                        elementCodec.encode(buf, value);
                    }
                },
                buf -> {
                    int size = buf.readVarInt();
                    if (size < 0 || size > maxSize) {
                        throw new IllegalArgumentException("List size " + size + " exceeds max " + maxSize);
                    }
                    List<T> out = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        out.add(elementCodec.decode(buf));
                    }
                    return out;
                }
        );
    }
}
