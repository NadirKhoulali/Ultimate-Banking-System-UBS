package net.austizz.ultimatebankingsystem.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BankTellerSafeBoxState(UUID requestedTellerId,
                                     UUID boundBankId,
                                     boolean bankHasReadyVault,
                                     List<String> missingReasons,
                                     List<AccountAssignment> assignments) {
    public static final String REQUEST_RENTAL_SAFE_BOX_ACTION = "REQUEST_SAFE_BOX";
    public static final String REQUEST_OPEN_SAFE_BOX_ACTION = "REQUEST_OPEN_SAFE_BOX";

    private static final int MAX_REASON_COUNT = 16;
    private static final int MAX_ASSIGNMENT_COUNT = 256;
    private static final int MAX_REASON_LENGTH = 160;
    private static final int MAX_SHORT_TEXT_LENGTH = 96;

    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> NULLABLE_UUID_CODEC =
            StreamCodec.of(
                    (buf, uuid) -> {
                        ByteBufCodecs.BOOL.encode(buf, uuid != null);
                        if (uuid != null) {
                            buf.writeUUID(uuid);
                        }
                    },
                    buf -> ByteBufCodecs.BOOL.decode(buf) ? buf.readUUID() : null
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, String> SHORT_TEXT_CODEC =
            StreamCodec.of(
                    (buf, value) -> buf.writeUtf(limit(value, MAX_SHORT_TEXT_LENGTH), MAX_SHORT_TEXT_LENGTH),
                    buf -> buf.readUtf(MAX_SHORT_TEXT_LENGTH)
            );

    private static final StreamCodec<RegistryFriendlyByteBuf, String> REASON_CODEC =
            StreamCodec.of(
                    (buf, value) -> buf.writeUtf(limit(value, MAX_REASON_LENGTH), MAX_REASON_LENGTH),
                    buf -> buf.readUtf(MAX_REASON_LENGTH)
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, BankTellerSafeBoxState> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        NULLABLE_UUID_CODEC.encode(buf, payload.requestedTellerId());
                        NULLABLE_UUID_CODEC.encode(buf, payload.boundBankId());
                        ByteBufCodecs.BOOL.encode(buf, payload.bankHasReadyVault());
                        REASON_CODEC.apply(ByteBufCodecs.list(MAX_REASON_COUNT)).encode(buf, payload.missingReasons());
                        AccountAssignment.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASSIGNMENT_COUNT))
                                .encode(buf, payload.assignments());
                    },
                    buf -> new BankTellerSafeBoxState(
                            NULLABLE_UUID_CODEC.decode(buf),
                            NULLABLE_UUID_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            REASON_CODEC.apply(ByteBufCodecs.list(MAX_REASON_COUNT)).decode(buf),
                            AccountAssignment.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ASSIGNMENT_COUNT)).decode(buf)
                    )
            );

    public BankTellerSafeBoxState {
        missingReasons = copyStrings(missingReasons, MAX_REASON_COUNT, MAX_REASON_LENGTH);
        assignments = copyValues(assignments, MAX_ASSIGNMENT_COUNT);
    }

    public static BankTellerSafeBoxState unavailable(UUID tellerId, UUID bankId, String reason) {
        return new BankTellerSafeBoxState(tellerId, bankId, false, List.of(reason), List.of());
    }

    public OpenRequestResult validateOpenRequest(UUID tellerId, UUID accountId) {
        if (requestedTellerId == null || tellerId == null || !requestedTellerId.equals(tellerId)) {
            return OpenRequestResult.fail(OpenRequestFailure.INVALID_TELLER,
                    "Bank teller validation failed.", null);
        }
        AccountAssignment assignment = assignmentFor(accountId);
        if (assignment == null) {
            return OpenRequestResult.fail(OpenRequestFailure.NO_ASSIGNMENT,
                    "Selected account does not have an assigned safety deposit box.", null);
        }
        if (!bankHasReadyVault) {
            return OpenRequestResult.fail(OpenRequestFailure.BANK_NOT_READY,
                    firstReasonOr("No ready safe-deposit vault is available."), assignment);
        }
        if (!assignment.ready()) {
            return OpenRequestResult.fail(OpenRequestFailure.ASSIGNMENT_UNAVAILABLE,
                    assignment.firstReasonOr("Selected safety deposit box is unavailable."), assignment);
        }
        return OpenRequestResult.ok(assignment);
    }

    public AccountAssignment assignmentFor(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        for (AccountAssignment assignment : assignments) {
            if (accountId.equals(assignment.accountId())) {
                return assignment;
            }
        }
        return null;
    }

    private String firstReasonOr(String fallback) {
        return missingReasons.isEmpty() ? fallback : missingReasons.getFirst();
    }

    private static List<String> copyStrings(List<String> values, int maxCount, int maxLength) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(Math.min(values.size(), maxCount));
        for (String value : values) {
            if (out.size() >= maxCount) {
                break;
            }
            out.add(limit(value, maxLength));
        }
        return List.copyOf(out);
    }

    private static <T> List<T> copyValues(List<T> values, int maxCount) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values.stream().filter(java.util.Objects::nonNull).limit(maxCount).toList());
    }

    private static String limit(String value, int maxLength) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    public record AccountAssignment(UUID accountId,
                                    String assignmentLabel,
                                    String dimension,
                                    int x,
                                    int y,
                                    int z,
                                    int doorIndex,
                                    String vaultId,
                                    boolean ready,
                                    boolean locked,
                                    List<String> missingReasons) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AccountAssignment> STREAM_CODEC =
                StreamCodec.of(
                        (buf, payload) -> {
                            buf.writeUUID(payload.accountId());
                            SHORT_TEXT_CODEC.encode(buf, payload.assignmentLabel());
                            SHORT_TEXT_CODEC.encode(buf, payload.dimension());
                            ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                            ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                            ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                            ByteBufCodecs.VAR_INT.encode(buf, payload.doorIndex());
                            SHORT_TEXT_CODEC.encode(buf, payload.vaultId());
                            ByteBufCodecs.BOOL.encode(buf, payload.ready());
                            ByteBufCodecs.BOOL.encode(buf, payload.locked());
                            REASON_CODEC.apply(ByteBufCodecs.list(MAX_REASON_COUNT))
                                    .encode(buf, payload.missingReasons());
                        },
                        buf -> new AccountAssignment(
                                buf.readUUID(),
                                SHORT_TEXT_CODEC.decode(buf),
                                SHORT_TEXT_CODEC.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf),
                                ByteBufCodecs.VAR_INT.decode(buf),
                                SHORT_TEXT_CODEC.decode(buf),
                                ByteBufCodecs.BOOL.decode(buf),
                                ByteBufCodecs.BOOL.decode(buf),
                                REASON_CODEC.apply(ByteBufCodecs.list(MAX_REASON_COUNT)).decode(buf)
                        )
                );

        public AccountAssignment {
            accountId = accountId == null ? new UUID(0L, 0L) : accountId;
            assignmentLabel = limit(assignmentLabel, MAX_SHORT_TEXT_LENGTH);
            dimension = limit(dimension, MAX_SHORT_TEXT_LENGTH);
            vaultId = limit(vaultId, MAX_SHORT_TEXT_LENGTH);
            missingReasons = copyStrings(missingReasons, MAX_REASON_COUNT, MAX_REASON_LENGTH);
        }

        private String firstReasonOr(String fallback) {
            return missingReasons.isEmpty() ? fallback : missingReasons.getFirst();
        }
    }

    public enum OpenRequestFailure {
        NONE,
        INVALID_TELLER,
        NO_ASSIGNMENT,
        BANK_NOT_READY,
        ASSIGNMENT_UNAVAILABLE,
        QUEUE_BUSY
    }

    public record OpenRequestResult(boolean success,
                                    OpenRequestFailure failure,
                                    String message,
                                    AccountAssignment assignment,
                                    boolean queueRequested) {
        public static OpenRequestResult fail(OpenRequestFailure failure,
                                             String message,
                                             AccountAssignment assignment) {
            return new OpenRequestResult(false, failure, message == null ? "" : message, assignment, false);
        }

        public static OpenRequestResult ok(AccountAssignment assignment) {
            return new OpenRequestResult(true, OpenRequestFailure.NONE, "", assignment, false);
        }

        public static OpenRequestResult busy(String message, AccountAssignment assignment) {
            return new OpenRequestResult(false, OpenRequestFailure.QUEUE_BUSY,
                    message == null ? "" : message, assignment, false);
        }
    }
}
