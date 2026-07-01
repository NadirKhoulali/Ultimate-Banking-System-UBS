package net.austizz.ultimatebankingsystem.client;

final class SmartphoneBankAccessGate {
    enum Result {
        WELCOME,
        SET_PIN,
        SIGN_IN,
        READY
    }

    private SmartphoneBankAccessGate() {
    }

    static Result decide(boolean hasAccounts, boolean accountExists, boolean pinSet, boolean accountUnlocked) {
        if (!hasAccounts || !accountExists) {
            return Result.WELCOME;
        }
        if (!pinSet) {
            return Result.SET_PIN;
        }
        if (!accountUnlocked) {
            return Result.SIGN_IN;
        }
        return Result.READY;
    }
}
