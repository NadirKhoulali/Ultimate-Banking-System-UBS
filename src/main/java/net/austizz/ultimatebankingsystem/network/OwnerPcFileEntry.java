package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record OwnerPcFileEntry(
        String kind,
        String name,
        String content,
        long updatedAtMillis
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcFileEntry> STREAM_CODEC =
            StreamCodec.of(
                    (buf, entry) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.kind());
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.name());
                        ByteBufCodecs.STRING_UTF8.encode(buf, entry.content());
                        ByteBufCodecs.VAR_LONG.encode(buf, entry.updatedAtMillis());
                    },
                    buf -> new OwnerPcFileEntry(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_LONG.decode(buf)
                    )
            );
}
