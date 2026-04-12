package net.austizz.ultimatebankingsystem.api;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.AvailableSince("1.0.0")
public final class UltimateBankingApiProvider {
    private static final UltimateBankingApi INSTANCE = new UltimateBankingApiImpl();

    private UltimateBankingApiProvider() {}

    public static UltimateBankingApi get() {
        return INSTANCE;
    }
}
