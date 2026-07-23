package net.austizz.ultimatebankingsystem.bank.safebox.escort;

final class EscortSessionInputs {
    private EscortSessionInputs() {
    }

    static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    static <T> T requireValue(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
