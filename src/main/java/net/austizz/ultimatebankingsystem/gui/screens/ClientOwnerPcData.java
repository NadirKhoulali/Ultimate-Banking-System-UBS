package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ClientOwnerPcData {
    private static final List<OwnerPcBankAppSummary> APPS = new ArrayList<>();
    private static final List<String> ACTION_OUTPUT_LINES = new ArrayList<>();

    private static UUID selectedBankId;
    private static int ownedCount;
    private static int maxBanks;

    private static OwnerPcBankDataPayload currentBankData;

    private static String toastMessage = "";
    private static boolean toastSuccess = true;
    private static long toastUntilMillis = 0L;

    private ClientOwnerPcData() {}

    public static void setApps(List<OwnerPcBankAppSummary> apps, int owned, int max) {
        APPS.clear();
        if (apps != null) {
            APPS.addAll(apps);
        }
        ownedCount = Math.max(0, owned);
        maxBanks = Math.max(1, max);

        if (!APPS.isEmpty()) {
            if (selectedBankId == null || APPS.stream().noneMatch(app -> app.bankId().equals(selectedBankId))) {
                selectedBankId = APPS.getFirst().bankId();
            }
        } else {
            selectedBankId = null;
        }
        currentBankData = null;
        ACTION_OUTPUT_LINES.clear();
    }

    public static List<OwnerPcBankAppSummary> getApps() {
        return List.copyOf(APPS);
    }

    public static int getOwnedCount() {
        return ownedCount;
    }

    public static int getMaxBanks() {
        return maxBanks;
    }

    public static UUID getSelectedBankId() {
        return selectedBankId;
    }

    public static void setSelectedBankId(UUID bankId) {
        selectedBankId = bankId;
    }

    public static OwnerPcBankAppSummary getSelectedApp() {
        if (selectedBankId == null) {
            return APPS.isEmpty() ? null : APPS.getFirst();
        }
        for (OwnerPcBankAppSummary app : APPS) {
            if (app.bankId().equals(selectedBankId)) {
                return app;
            }
        }
        return APPS.isEmpty() ? null : APPS.getFirst();
    }

    public static void setCurrentBankData(OwnerPcBankDataPayload data) {
        currentBankData = data;
        if (data != null) {
            selectedBankId = data.bankId();
        }
    }

    public static OwnerPcBankDataPayload getCurrentBankData() {
        return currentBankData;
    }

    public static void setActionOutput(String message) {
        ACTION_OUTPUT_LINES.clear();
        if (message == null || message.isBlank()) {
            return;
        }
        String[] lines = message.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String line : lines) {
            ACTION_OUTPUT_LINES.add(line == null ? "" : line);
        }
    }

    public static void clearActionOutput() {
        ACTION_OUTPUT_LINES.clear();
    }

    public static List<String> getActionOutputLines() {
        return List.copyOf(ACTION_OUTPUT_LINES);
    }

    public static void setToast(boolean success, String message) {
        toastSuccess = success;
        toastMessage = message == null ? "" : message;
        toastUntilMillis = System.currentTimeMillis() + 4500L;
    }

    public static String getToastMessage() {
        if (System.currentTimeMillis() > toastUntilMillis) {
            return "";
        }
        return toastMessage;
    }

    public static boolean isToastSuccess() {
        return toastSuccess;
    }

    public static void clear() {
        APPS.clear();
        ACTION_OUTPUT_LINES.clear();
        selectedBankId = null;
        ownedCount = 0;
        maxBanks = 1;
        currentBankData = null;
        toastMessage = "";
        toastUntilMillis = 0L;
        toastSuccess = true;
    }
}
