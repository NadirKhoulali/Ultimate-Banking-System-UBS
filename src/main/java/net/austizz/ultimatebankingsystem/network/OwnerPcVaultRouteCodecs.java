package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteFace;
import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class OwnerPcVaultRouteCodecs {
    private static final UUID ZERO_UUID = new UUID(0L, 0L);
    static final int MAX_ID_CHARS = 128;
    static final int MAX_DIRECTION_CHARS = 16;
    static final int MAX_MESSAGE_CHARS = 512;
    static final int MAX_STEPS = 256;

    private OwnerPcVaultRouteCodecs() {
    }

    static UUID requireUuid(UUID value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    static UUID requireSessionId(UUID value) {
        UUID sessionId = requireUuid(value, "editSessionId");
        if (ZERO_UUID.equals(sessionId)) {
            throw new IllegalArgumentException("editSessionId must not be zero");
        }
        return sessionId;
    }

    static String requireText(String value, int maxChars, String field) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > maxChars) {
            throw new IllegalArgumentException(field + " must contain 1-" + maxChars + " characters");
        }
        return clean;
    }

    static String optionalText(String value, int maxChars, String field) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() > maxChars) {
            throw new IllegalArgumentException(field + " exceeds " + maxChars + " characters");
        }
        return clean;
    }

    static SafeTellerRouteDirection requireDirection(SafeTellerRouteDirection value) {
        if (value == null) {
            throw new IllegalArgumentException("direction is required");
        }
        return value;
    }

    static void writeDirection(RegistryFriendlyByteBuf buf, SafeTellerRouteDirection direction) {
        buf.writeUtf(requireDirection(direction).name(), MAX_DIRECTION_CHARS);
    }

    static SafeTellerRouteDirection readDirection(RegistryFriendlyByteBuf buf) {
        String value = buf.readUtf(MAX_DIRECTION_CHARS).toUpperCase(Locale.ROOT);
        try {
            return SafeTellerRouteDirection.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown route direction: " + value, exception);
        }
    }

    static void writeFace(RegistryFriendlyByteBuf buf, SafeTellerRouteFace face) {
        if (face == null) {
            throw new IllegalArgumentException("redstone face is required");
        }
        buf.writeUtf(face.name(), MAX_DIRECTION_CHARS);
    }

    static SafeTellerRouteFace readFace(RegistryFriendlyByteBuf buf) {
        String value = buf.readUtf(MAX_DIRECTION_CHARS).toUpperCase(Locale.ROOT);
        try {
            return SafeTellerRouteFace.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown route face: " + value, exception);
        }
    }

    static List<OwnerPcVaultRouteStepPayload> copySteps(List<OwnerPcVaultRouteStepPayload> steps,
                                                         boolean allowEmpty) {
        List<OwnerPcVaultRouteStepPayload> copy = steps == null ? List.of() : List.copyOf(steps);
        if ((!allowEmpty && copy.isEmpty()) || copy.size() > MAX_STEPS) {
            throw new IllegalArgumentException("route must contain 1-" + MAX_STEPS + " steps");
        }
        return copy;
    }

    static void writeSteps(RegistryFriendlyByteBuf buf, List<OwnerPcVaultRouteStepPayload> steps) {
        if (steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("route step count exceeds " + MAX_STEPS);
        }
        buf.writeVarInt(steps.size());
        for (OwnerPcVaultRouteStepPayload step : steps) {
            OwnerPcVaultRouteStepPayload.STREAM_CODEC.encode(buf, step);
        }
    }

    static List<OwnerPcVaultRouteStepPayload> readSteps(RegistryFriendlyByteBuf buf,
                                                         boolean allowEmpty) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_STEPS || (!allowEmpty && count == 0)) {
            throw new IllegalArgumentException("invalid route step count: " + count);
        }
        List<OwnerPcVaultRouteStepPayload> steps = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            steps.add(OwnerPcVaultRouteStepPayload.STREAM_CODEC.decode(buf));
        }
        return List.copyOf(steps);
    }
}
