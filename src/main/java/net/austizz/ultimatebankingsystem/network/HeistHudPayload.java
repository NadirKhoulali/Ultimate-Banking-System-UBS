package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record HeistHudPayload(boolean active, String bankName, String phase, boolean alarmed,
                              int remainingTicks, long lootCents, int bagSlots, int bagCapacity,
                              String crewData, boolean actionable, String prompt,
                              int actionElapsed, int actionRequired,
                              String exfillDimension, int exfillReferenceY,
                              String exfillBoundary, String exfillState,
                              boolean exfillLootArmed, int exfillRemainingTicks,
                              int exfillCrewInside, int exfillCrewRequired,
                              String drillData) implements CustomPacketPayload {
    public static final Type<HeistHudPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "heist_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HeistHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> {
                ByteBufCodecs.BOOL.encode(buf, value.active());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.bankName());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.phase());
                ByteBufCodecs.BOOL.encode(buf, value.alarmed());
                ByteBufCodecs.VAR_INT.encode(buf, value.remainingTicks());
                ByteBufCodecs.VAR_LONG.encode(buf, value.lootCents());
                ByteBufCodecs.VAR_INT.encode(buf, value.bagSlots());
                ByteBufCodecs.VAR_INT.encode(buf, value.bagCapacity());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.crewData());
                ByteBufCodecs.BOOL.encode(buf, value.actionable());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.prompt());
                ByteBufCodecs.VAR_INT.encode(buf, value.actionElapsed());
                ByteBufCodecs.VAR_INT.encode(buf, value.actionRequired());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.exfillDimension());
                ByteBufCodecs.VAR_INT.encode(buf, value.exfillReferenceY());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.exfillBoundary());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.exfillState());
                ByteBufCodecs.BOOL.encode(buf, value.exfillLootArmed());
                ByteBufCodecs.VAR_INT.encode(buf, value.exfillRemainingTicks());
                ByteBufCodecs.VAR_INT.encode(buf, value.exfillCrewInside());
                ByteBufCodecs.VAR_INT.encode(buf, value.exfillCrewRequired());
                ByteBufCodecs.STRING_UTF8.encode(buf, value.drillData());
            },
            buf -> new HeistHudPayload(
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf)));

    public static HeistHudPayload inactive() {
        return new HeistHudPayload(false, "", "", false,
                0, 0L, 0, 0, "", false, "", 0, 0,
                "", 0, "", "HIDDEN", false, 0, 0, 0, "");
    }

    @Override
    public Type<HeistHudPayload> type() { return TYPE; }
}
