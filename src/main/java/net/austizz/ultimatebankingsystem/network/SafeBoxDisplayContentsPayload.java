package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SafeBoxDisplayContentsPayload(
        UUID proxyId,
        boolean active,
        List<ItemStack> slots
) implements CustomPacketPayload {
    public static final Type<SafeBoxDisplayContentsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    UltimateBankingSystem.MODID, "safe_box_display_contents"));

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> {
                buf.writeLong(uuid.getMostSignificantBits());
                buf.writeLong(uuid.getLeastSignificantBits());
            },
            buf -> new UUID(buf.readLong(), buf.readLong())
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> SLOTS_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(54));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafeBoxDisplayContentsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        UUID_CODEC.encode(buf, payload.proxyId());
                        ByteBufCodecs.BOOL.encode(buf, payload.active());
                        SLOTS_CODEC.encode(buf, payload.slots());
                    },
                    buf -> new SafeBoxDisplayContentsPayload(
                            UUID_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            SLOTS_CODEC.decode(buf)
                    )
            );

    public SafeBoxDisplayContentsPayload {
        List<ItemStack> safe = new ArrayList<>();
        if (slots != null) {
            for (int i = 0; i < slots.size() && i < 54; i++) {
                ItemStack stack = slots.get(i);
                safe.add(stack == null || stack.isEmpty()
                        ? ItemStack.EMPTY : stack.copyWithCount(1));
            }
        }
        slots = List.copyOf(safe);
    }

    public static SafeBoxDisplayContentsPayload clear(UUID proxyId) {
        return new SafeBoxDisplayContentsPayload(proxyId, false, List.of());
    }

    @Override
    public Type<SafeBoxDisplayContentsPayload> type() {
        return TYPE;
    }
}
