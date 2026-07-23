package net.austizz.ultimatebankingsystem.bank.owner;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OwnerPcActionPolicy {
    public enum Channel {
        DIRECT_OWNER_PC,
        TRUSTED_REMOTE
    }

    public enum Access {
        READ_ONLY,
        MUTATION
    }

    public enum Action {
        SHOW_INFO(Access.READ_ONLY),
        SHOW_RESERVE(Access.READ_ONLY),
        SHOW_DASHBOARD(Access.READ_ONLY),
        SHOW_ACCOUNTS(Access.READ_ONLY),
        SHOW_CDS(Access.READ_ONLY),
        SHOW_LIMITS(Access.READ_ONLY),
        SHOW_ROLES(Access.READ_ONLY),
        SHOW_SHARES(Access.READ_ONLY),
        SHOW_COFOUNDERS(Access.READ_ONLY),
        SHOW_EMPLOYEES(Access.READ_ONLY),
        SHOW_LOAN_PRODUCTS(Access.READ_ONLY),
        SHOW_LOANS(Access.READ_ONLY),
        SHOW_MARKET(Access.READ_ONLY),
        BANK_LEVEL_ROADMAP(Access.READ_ONLY),
        SET_MOTTO(Access.MUTATION),
        SET_COLOR(Access.MUTATION),
        SET_LIMIT(Access.MUTATION),
        SET_CARD_FEES(Access.MUTATION),
        ROLE_ASSIGN(Access.MUTATION),
        ROLE_REVOKE(Access.MUTATION),
        SHARES_SET(Access.MUTATION),
        COFOUNDER_ADD(Access.MUTATION),
        HIRE(Access.MUTATION),
        FIRE(Access.MUTATION),
        TELLER_ISSUE(Access.MUTATION),
        TELLER_COUNT(Access.READ_ONLY),
        BORROW(Access.MUTATION),
        LEND_OFFER(Access.MUTATION),
        LEND_ACCEPT(Access.MUTATION),
        APPEAL(Access.MUTATION),
        CREATE_LOAN_PRODUCT(Access.MUTATION),
        ACCOUNT_DETAIL(Access.READ_ONLY),
        ACCOUNT_FREEZE(Access.MUTATION),
        ACCOUNT_UNFREEZE(Access.MUTATION),
        ACCOUNT_TEMP_LIMIT(Access.MUTATION),
        SAFE_AREA_CLAIM_TOOL(Access.MUTATION),
        SAFE_BOX_ASSIGN(Access.MUTATION),
        SAFE_BOX_LOCATE(Access.MUTATION),
        SAFE_BOX_POLICY(Access.MUTATION),
        SAFE_BOX_SEIZE(Access.MUTATION),
        SAFE_ACCESS_GRANT(Access.MUTATION),
        SAFE_ACCESS_REVOKE(Access.MUTATION),
        SAFE_ALARM_CONFIG(Access.MUTATION),
        SAFE_ALARM_TEST(Access.MUTATION),
        SAFE_ALARM_STOP_TEST(Access.MUTATION),
        SAFE_ALARM_RESET(Access.MUTATION),
        VIEWING_ROOM_CLAIM_TOOL(Access.MUTATION),
        VIEWING_ROOM_ANCHOR(Access.MUTATION),
        VIEWING_ROOM_RENAME(Access.MUTATION),
        VIEWING_ROOM_SUSPEND(Access.MUTATION),
        VIEWING_ROOM_DELETE(Access.MUTATION);

        private final Access access;

        Action(Access access) {
            this.access = access;
        }

        public Access access() {
            return access;
        }
    }

    public record MutationContext(boolean remembered,
                                  boolean levelLoaded,
                                  boolean ownerPcBlock,
                                  boolean machineMatches,
                                  boolean sameDimension,
                                  boolean withinRange,
                                  boolean poweredOn,
                                  boolean sessionUnlocked) {
    }

    public record Decision(Action action, boolean allowed, String message) {
    }

    private static final Map<String, Action> ACTIONS = Stream.of(Action.values())
            .collect(Collectors.toUnmodifiableMap(Enum::name, Function.identity()));

    private OwnerPcActionPolicy() {
    }

    public static Action classify(String rawAction) {
        String normalized = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
        return ACTIONS.get(normalized);
    }

    public static Set<String> supportedRawActions() {
        return ACTIONS.keySet();
    }

    public static Decision authorize(String rawAction,
                                     Channel channel,
                                     MutationContext context) {
        Action action = classify(rawAction);
        if (action == null) {
            String normalized = rawAction == null ? "" : rawAction.trim().toUpperCase(Locale.ROOT);
            return new Decision(null, false, "Unknown action: " + normalized);
        }
        if (channel == Channel.TRUSTED_REMOTE || action.access() == Access.READ_ONLY) {
            return new Decision(action, true, "");
        }
        if (context == null || !context.remembered()) {
            return denied(action, "Open a bank owner PC block first.");
        }
        if (!context.levelLoaded()) {
            return denied(action, "The bank owner PC is not loaded.");
        }
        if (!context.ownerPcBlock()) {
            return denied(action, "The remembered bank owner PC is no longer present.");
        }
        if (!context.machineMatches()) {
            return denied(action, "The bank owner PC machine changed. Re-open it.");
        }
        if (!context.sameDimension()) {
            return denied(action, "Return to the bank owner PC dimension.");
        }
        if (!context.withinRange()) {
            return denied(action, "Move closer to the bank owner PC.");
        }
        if (!context.poweredOn()) {
            return denied(action, "This bank owner PC is powered off.");
        }
        if (!context.sessionUnlocked()) {
            return denied(action, "This bank owner PC is locked. Enter your password first.");
        }
        return new Decision(action, true, "");
    }

    private static Decision denied(Action action, String message) {
        return new Decision(action, false, message);
    }
}
