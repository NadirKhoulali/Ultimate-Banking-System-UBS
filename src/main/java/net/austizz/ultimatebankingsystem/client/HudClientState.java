package net.austizz.ultimatebankingsystem.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.math.BigDecimal;
import java.math.RoundingMode;

@OnlyIn(Dist.CLIENT)
public final class HudClientState {
    public static final long VALUE_TRANSITION_MS = 460L;
    public static final long CHANGE_EMPHASIS_MS = 1_350L;
    public static final long APPEAR_TRANSITION_MS = 260L;

    public enum ChangeDirection {
        NONE,
        INCREASE,
        DECREASE
    }

    private static String balanceText = "";
    private static String bankName = "";
    private static String accountType = "";
    private static String position = "";
    private static boolean enabled = true;
    private static boolean primaryAccount;
    private static BigDecimal currentBalance;
    private static BigDecimal previousBalance;
    private static ChangeDirection changeDirection = ChangeDirection.NONE;
    private static long changeStartedAtMillis = -1L;
    private static long visibleSinceMillis = -1L;

    private HudClientState() {}

    public static synchronized String getBalanceText() {
        return balanceText == null ? "" : balanceText;
    }

    public static synchronized void setBalanceText(String balanceText) {
        apply(balanceText, enabled, bankName, accountType, primaryAccount, position, System.currentTimeMillis());
    }

    public static synchronized boolean isEnabled() {
        return enabled;
    }

    public static synchronized void setEnabled(boolean enabled) {
        apply(balanceText, enabled, bankName, accountType, primaryAccount, position, System.currentTimeMillis());
    }

    public static synchronized String getBankName() {
        return bankName;
    }

    public static synchronized String getAccountType() {
        return accountType;
    }

    public static synchronized boolean isPrimaryAccount() {
        return primaryAccount;
    }

    public static synchronized String getPosition() {
        return position;
    }

    public static synchronized ChangeDirection getChangeDirection() {
        return changeDirection;
    }

    public static synchronized void apply(String balanceText,
                                          boolean enabled,
                                          String bankName,
                                          String accountType,
                                          boolean primaryAccount,
                                          String position) {
        apply(balanceText, enabled, bankName, accountType, primaryAccount, position,
                System.currentTimeMillis());
    }

    static synchronized void apply(String balanceText,
                                   boolean enabled,
                                   String bankName,
                                   String accountType,
                                   boolean primaryAccount,
                                   String position,
                                   long now) {
        String nextBalanceText = normalize(balanceText);
        BigDecimal nextBalance = parseBalance(nextBalanceText);
        BigDecimal displayedBalance = animatedBalance(now);
        boolean wasVisible = HudClientState.enabled && !HudClientState.balanceText.isBlank();
        boolean changed = currentBalance != null
                && nextBalance != null
                && currentBalance.compareTo(nextBalance) != 0;

        if (changed) {
            previousBalance = displayedBalance == null ? currentBalance : displayedBalance;
            changeDirection = nextBalance.compareTo(currentBalance) > 0
                    ? ChangeDirection.INCREASE
                    : ChangeDirection.DECREASE;
            changeStartedAtMillis = now;
        } else if (currentBalance == null && nextBalance != null) {
            previousBalance = nextBalance;
            changeDirection = ChangeDirection.NONE;
            changeStartedAtMillis = -1L;
        }

        currentBalance = nextBalance;
        HudClientState.balanceText = nextBalanceText;
        HudClientState.enabled = enabled;
        HudClientState.bankName = normalize(bankName);
        HudClientState.accountType = normalize(accountType);
        HudClientState.primaryAccount = primaryAccount;
        HudClientState.position = normalize(position);

        boolean visible = enabled && !nextBalanceText.isBlank();
        if (visible && !wasVisible) {
            visibleSinceMillis = now;
        } else if (!visible) {
            visibleSinceMillis = -1L;
        }
    }

    public static synchronized String getAnimatedBalanceText(long now) {
        BigDecimal animated = animatedBalance(now);
        if (animated == null) {
            return getBalanceText();
        }
        return animated.toPlainString();
    }

    private static BigDecimal animatedBalance(long now) {
        if (currentBalance == null) {
            return null;
        }
        if (previousBalance == null || changeStartedAtMillis < 0L) {
            return currentBalance;
        }
        float progress = clamp01((now - changeStartedAtMillis) / (float) VALUE_TRANSITION_MS);
        if (progress >= 1.0F) {
            return currentBalance;
        }
        double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
        BigDecimal animated = previousBalance.add(
                currentBalance.subtract(previousBalance).multiply(BigDecimal.valueOf(eased))
        );
        int scale = Math.max(0, Math.min(2, Math.max(previousBalance.scale(), currentBalance.scale())));
        return animated.setScale(scale, RoundingMode.HALF_UP);
    }

    public static synchronized float getChangeStrength(long now) {
        if (changeDirection == ChangeDirection.NONE || changeStartedAtMillis < 0L) {
            return 0.0F;
        }
        float elapsed = clamp01((now - changeStartedAtMillis) / (float) CHANGE_EMPHASIS_MS);
        float remaining = 1.0F - elapsed;
        return remaining * remaining * (3.0F - 2.0F * remaining);
    }

    public static synchronized float getAppearanceProgress(long now) {
        if (visibleSinceMillis < 0L) {
            return 1.0F;
        }
        float progress = clamp01((now - visibleSinceMillis) / (float) APPEAR_TRANSITION_MS);
        return progress * progress * (3.0F - 2.0F * progress);
    }

    static synchronized void reset() {
        balanceText = "";
        bankName = "";
        accountType = "";
        position = "";
        enabled = true;
        primaryAccount = false;
        currentBalance = null;
        previousBalance = null;
        changeDirection = ChangeDirection.NONE;
        changeStartedAtMillis = -1L;
        visibleSinceMillis = -1L;
    }

    private static BigDecimal parseBalance(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
