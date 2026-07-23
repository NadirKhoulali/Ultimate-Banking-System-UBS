package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteValidator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public sealed interface OwnerPcVaultRouteStepPayload
        permits OwnerPcVaultRouteStepPayload.Walk,
                OwnerPcVaultRouteStepPayload.Wait,
                OwnerPcVaultRouteStepPayload.Redstone,
                OwnerPcVaultRouteStepPayload.Rfid {
    int WALK_TYPE = 0;
    int WAIT_TYPE = 1;
    int REDSTONE_TYPE = 2;
    int RFID_TYPE = 3;

    StreamCodec<RegistryFriendlyByteBuf, OwnerPcVaultRouteStepPayload> STREAM_CODEC =
            StreamCodec.of(OwnerPcVaultRouteStepPayload::encode, OwnerPcVaultRouteStepPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, OwnerPcVaultRouteStepPayload step) {
        if (step instanceof Walk walk) {
            buf.writeByte(WALK_TYPE);
            OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, walk.target());
        } else if (step instanceof Wait wait) {
            buf.writeByte(WAIT_TYPE);
            buf.writeVarInt(wait.durationTicks());
        } else if (step instanceof Redstone redstone) {
            buf.writeByte(REDSTONE_TYPE);
            OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, redstone.target());
            OwnerPcVaultRouteCodecs.writeFace(buf, redstone.face());
            buf.writeVarInt(redstone.strength());
            buf.writeVarInt(redstone.durationTicks());
        } else if (step instanceof Rfid rfid) {
            buf.writeByte(RFID_TYPE);
            OwnerPcVaultRoutePosition.STREAM_CODEC.encode(buf, rfid.scanner());
        } else {
            throw new IllegalArgumentException("Unknown route step implementation");
        }
    }

    private static OwnerPcVaultRouteStepPayload decode(RegistryFriendlyByteBuf buf) {
        return switch (buf.readUnsignedByte()) {
            case WALK_TYPE -> new Walk(OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf));
            case WAIT_TYPE -> new Wait(buf.readVarInt());
            case REDSTONE_TYPE -> new Redstone(
                    OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf),
                    OwnerPcVaultRouteCodecs.readFace(buf),
                    buf.readVarInt(),
                    buf.readVarInt());
            case RFID_TYPE -> new Rfid(OwnerPcVaultRoutePosition.STREAM_CODEC.decode(buf));
            default -> throw new IllegalArgumentException("Unknown route step type");
        };
    }

    record Walk(OwnerPcVaultRoutePosition target) implements OwnerPcVaultRouteStepPayload {
        public Walk {
            if (target == null) {
                throw new IllegalArgumentException("walk target is required");
            }
        }
    }

    record Wait(int durationTicks) implements OwnerPcVaultRouteStepPayload {
        public Wait {
            if (durationTicks < 1 || durationTicks > SafeTellerRouteValidator.MAX_WAIT_TICKS) {
                throw new IllegalArgumentException("wait duration is out of range");
            }
        }
    }

    record Redstone(OwnerPcVaultRoutePosition target,
                    SafeTellerRouteFace face,
                    int strength,
                    int durationTicks) implements OwnerPcVaultRouteStepPayload {
        public Redstone {
            if (target == null || face == null) {
                throw new IllegalArgumentException("redstone target and face are required");
            }
            if (strength < 1 || strength > 15) {
                throw new IllegalArgumentException("redstone strength is out of range");
            }
            if (durationTicks < 1
                    || durationTicks > SafeTellerRouteValidator.MAX_REDSTONE_DURATION_TICKS) {
                throw new IllegalArgumentException("redstone duration is out of range");
            }
        }
    }

    record Rfid(OwnerPcVaultRoutePosition scanner) implements OwnerPcVaultRouteStepPayload {
        public Rfid {
            if (scanner == null) {
                throw new IllegalArgumentException("RFID scanner position is required");
            }
        }
    }
}
