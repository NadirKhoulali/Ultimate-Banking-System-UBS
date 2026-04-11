package net.austizz.ultimatebankingsystem.payrequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PayRequestManager {

    public enum Status {
        PENDING,
        ACCEPTED,
        DECLINED,
        EXPIRED
    }

    public static final class PayRequest {
        private final UUID requestId;
        private final UUID requesterUUID;
        private final UUID payerUUID;
        private final BigDecimal amount;
        private final long createdAtMillis;
        private volatile Status status;

        private PayRequest(UUID requestId, UUID requesterUUID, UUID payerUUID, BigDecimal amount, long createdAtMillis) {
            this.requestId = requestId;
            this.requesterUUID = requesterUUID;
            this.payerUUID = payerUUID;
            this.amount = amount;
            this.createdAtMillis = createdAtMillis;
            this.status = Status.PENDING;
        }

        public UUID getRequestId() {
            return requestId;
        }

        public UUID getRequesterUUID() {
            return requesterUUID;
        }

        public UUID getPayerUUID() {
            return payerUUID;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public long getCreatedAtMillis() {
            return createdAtMillis;
        }

        public Status getStatus() {
            return status;
        }
    }

    private static final long REQUEST_TIMEOUT_MILLIS = 5L * 60L * 1000L;
    private static final ConcurrentHashMap<UUID, PayRequest> REQUESTS = new ConcurrentHashMap<>();

    private PayRequestManager() {
    }

    public static PayRequest createRequest(UUID requesterUUID, UUID payerUUID, BigDecimal amount) {
        pruneExpired();
        UUID requestId = UUID.randomUUID();
        PayRequest request = new PayRequest(requestId, requesterUUID, payerUUID, amount, System.currentTimeMillis());
        REQUESTS.put(requestId, request);
        return request;
    }

    public static PayRequest getRequest(UUID requestId) {
        pruneExpired();
        return REQUESTS.get(requestId);
    }

    public static List<PayRequest> getPendingForPayer(UUID payerUUID) {
        pruneExpired();
        List<PayRequest> pending = new ArrayList<>();
        for (PayRequest request : REQUESTS.values()) {
            if (request.getPayerUUID().equals(payerUUID) && request.getStatus() == Status.PENDING) {
                pending.add(request);
            }
        }
        pending.sort(Comparator.comparingLong(PayRequest::getCreatedAtMillis).reversed());
        return pending;
    }

    public static boolean markAccepted(UUID requestId) {
        return setStatus(requestId, Status.ACCEPTED);
    }

    public static boolean markDeclined(UUID requestId) {
        return setStatus(requestId, Status.DECLINED);
    }

    private static boolean setStatus(UUID requestId, Status newStatus) {
        PayRequest request = REQUESTS.get(requestId);
        if (request == null) {
            return false;
        }
        synchronized (request) {
            if (request.status != Status.PENDING) {
                return false;
            }
            request.status = newStatus;
            return true;
        }
    }

    public static boolean isExpired(PayRequest request) {
        if (request == null) {
            return true;
        }
        long age = System.currentTimeMillis() - request.getCreatedAtMillis();
        return age > REQUEST_TIMEOUT_MILLIS;
    }

    public static void pruneExpired() {
        long now = System.currentTimeMillis();
        for (PayRequest request : REQUESTS.values()) {
            if (request.getStatus() != Status.PENDING) {
                continue;
            }
            if (now - request.getCreatedAtMillis() <= REQUEST_TIMEOUT_MILLIS) {
                continue;
            }
            synchronized (request) {
                if (request.status == Status.PENDING) {
                    request.status = Status.EXPIRED;
                }
            }
        }
    }
}
