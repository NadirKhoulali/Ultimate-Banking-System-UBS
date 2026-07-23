package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record OwnerPcSetupObjectivePayload(boolean ready,
                                           int premiseCount,
                                           int vaultCount,
                                           int readyVaultCount,
                                           List<String> missingSteps) {
    private static final int MAX_STEP_COUNT = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, OwnerPcSetupObjectivePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.BOOL.encode(buf, payload.ready());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.premiseCount());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.vaultCount());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.readyVaultCount());
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_STEP_COUNT))
                                .encode(buf, payload.missingSteps());
                    },
                    buf -> new OwnerPcSetupObjectivePayload(
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(MAX_STEP_COUNT)).decode(buf)
                    )
            );

    public OwnerPcSetupObjectivePayload {
        premiseCount = Math.max(0, premiseCount);
        vaultCount = Math.max(0, vaultCount);
        readyVaultCount = Math.max(0, Math.min(readyVaultCount, vaultCount));
        missingSteps = missingSteps == null
                ? List.of()
                : List.copyOf(missingSteps.stream().limit(MAX_STEP_COUNT).toList());
    }

    public static OwnerPcSetupObjectivePayload unavailable() {
        return new OwnerPcSetupObjectivePayload(false, 0, 0, 0,
                List.of("Claim a bank premise and safe area."));
    }
}
