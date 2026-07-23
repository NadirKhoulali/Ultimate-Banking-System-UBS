package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record RfidScannerOpenPayload(
        String dimensionId,
        int x,
        int y,
        int z,
        String readerId,
        boolean configured,
        boolean authenticated,
        boolean enabled,
        int requiredAccessLevel,
        String linkType,
        String linkName,
        String forceMode,
        int idleSignal,
        int successSignal,
        int failSignal,
        int successDurationTicks,
        int failDurationTicks,
        int failThreshold,
        int failAttempts,
        String status,
        String message,
        boolean messageSuccess,
        List<CardSummary> cards,
        List<TargetSummary> successTargets,
        List<TargetSummary> failTargets
) implements CustomPacketPayload {
    public static final Type<RfidScannerOpenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "rfid_scanner_open"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RfidScannerOpenPayload> STREAM_CODEC =
            StreamCodec.of(RfidScannerOpenPayload::encode, RfidScannerOpenPayload::decode);

    public RfidScannerOpenPayload {
        dimensionId = safe(dimensionId);
        readerId = safe(readerId);
        linkType = safe(linkType);
        linkName = safe(linkName);
        forceMode = safe(forceMode);
        status = safe(status);
        message = safe(message);
        cards = List.copyOf(cards == null ? List.of() : cards);
        successTargets = List.copyOf(successTargets == null ? List.of() : successTargets);
        failTargets = List.copyOf(failTargets == null ? List.of() : failTargets);
    }

    @Override
    public Type<RfidScannerOpenPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, RfidScannerOpenPayload payload) {
        buf.writeUtf(payload.dimensionId());
        buf.writeVarInt(payload.x());
        buf.writeVarInt(payload.y());
        buf.writeVarInt(payload.z());
        buf.writeUtf(payload.readerId());
        buf.writeBoolean(payload.configured());
        buf.writeBoolean(payload.authenticated());
        buf.writeBoolean(payload.enabled());
        buf.writeVarInt(payload.requiredAccessLevel());
        buf.writeUtf(payload.linkType());
        buf.writeUtf(payload.linkName());
        buf.writeUtf(payload.forceMode());
        buf.writeVarInt(payload.idleSignal());
        buf.writeVarInt(payload.successSignal());
        buf.writeVarInt(payload.failSignal());
        buf.writeVarInt(payload.successDurationTicks());
        buf.writeVarInt(payload.failDurationTicks());
        buf.writeVarInt(payload.failThreshold());
        buf.writeVarInt(payload.failAttempts());
        buf.writeUtf(payload.status());
        buf.writeUtf(payload.message());
        buf.writeBoolean(payload.messageSuccess());
        writeCards(buf, payload.cards());
        writeTargets(buf, payload.successTargets());
        writeTargets(buf, payload.failTargets());
    }

    private static RfidScannerOpenPayload decode(RegistryFriendlyByteBuf buf) {
        return new RfidScannerOpenPayload(
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readBoolean(),
                readCards(buf),
                readTargets(buf),
                readTargets(buf)
        );
    }

    private static void writeCards(RegistryFriendlyByteBuf buf, List<CardSummary> cards) {
        buf.writeVarInt(cards.size());
        for (CardSummary card : cards) {
            buf.writeUtf(card.cardId());
            buf.writeUtf(card.label());
            buf.writeVarInt(card.level());
            buf.writeUtf(card.holderName());
        }
    }

    private static List<CardSummary> readCards(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(512, buf.readVarInt()));
        List<CardSummary> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(new CardSummary(buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf()));
        }
        return cards;
    }

    private static void writeTargets(RegistryFriendlyByteBuf buf, List<TargetSummary> targets) {
        buf.writeVarInt(targets.size());
        for (TargetSummary target : targets) {
            buf.writeUtf(target.dimensionId());
            buf.writeVarInt(target.x());
            buf.writeVarInt(target.y());
            buf.writeVarInt(target.z());
            buf.writeUtf(target.relaySide());
            buf.writeUtf(target.label());
        }
    }

    private static List<TargetSummary> readTargets(RegistryFriendlyByteBuf buf) {
        int count = Math.max(0, Math.min(256, buf.readVarInt()));
        List<TargetSummary> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(new TargetSummary(
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf()
            ));
        }
        return targets;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record CardSummary(String cardId, String label, int level, String holderName) {
        public CardSummary {
            cardId = safe(cardId);
            label = safe(label);
            holderName = safe(holderName);
        }
    }

    public record TargetSummary(String dimensionId, int x, int y, int z, String relaySide, String label) {
        public TargetSummary {
            dimensionId = safe(dimensionId);
            relaySide = safe(relaySide);
            label = safe(label);
        }
    }
}
