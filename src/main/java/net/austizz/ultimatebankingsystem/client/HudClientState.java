package net.austizz.ultimatebankingsystem.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class HudClientState {
    private static String balanceText = "";
    private static boolean enabled = true;

    private HudClientState() {}

    public static String getBalanceText() {
        return balanceText == null ? "" : balanceText;
    }

    public static void setBalanceText(String balanceText) {
        HudClientState.balanceText = balanceText == null ? "" : balanceText;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        HudClientState.enabled = enabled;
    }
}
