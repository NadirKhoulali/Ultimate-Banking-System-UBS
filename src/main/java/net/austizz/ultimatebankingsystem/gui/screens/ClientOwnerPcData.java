package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcFileEntry;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ClientOwnerPcData {
    private static final List<OwnerPcBankAppSummary> APPS = new ArrayList<>();
    private static final List<String> ACTION_OUTPUT_LINES = new ArrayList<>();
    private static final List<OwnerPcFileEntry> DESKTOP_FILES = new ArrayList<>();
    private static final Set<String> HIDDEN_APP_IDS = new LinkedHashSet<>();

    private static UUID selectedBankId;
    private static int ownedCount;
    private static int maxBanks;
    private static int desktopMaxStorageBytes;
    private static int desktopUsedStorageBytes;
    private static String desktopComputerLabel = "Unknown PC";
    private static boolean desktopPinSet;
    private static boolean desktopDataLoaded;
    private static boolean desktopSessionUnlocked;
    private static String desktopSessionComputerLabel = "";
    private static int preferredPcUiScaleMode;

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

    public static void setDesktopData(OwnerPcDesktopDataPayload payload) {
        DESKTOP_FILES.clear();
        HIDDEN_APP_IDS.clear();
        desktopComputerLabel = "Unknown PC";
        desktopMaxStorageBytes = 0;
        desktopUsedStorageBytes = 0;
        desktopPinSet = false;
        desktopDataLoaded = false;

        if (payload == null) {
            return;
        }
        desktopDataLoaded = true;

        desktopComputerLabel = payload.computerLabel() == null || payload.computerLabel().isBlank()
                ? "Unknown PC"
                : payload.computerLabel();
        if (desktopSessionUnlocked && !desktopComputerLabel.equals(desktopSessionComputerLabel)) {
            desktopSessionUnlocked = false;
            desktopSessionComputerLabel = "";
        }
        desktopMaxStorageBytes = Math.max(0, payload.maxStorageBytes());
        desktopUsedStorageBytes = Math.max(0, payload.usedStorageBytes());
        desktopPinSet = payload.pinSet();
        if (payload.files() != null) {
            DESKTOP_FILES.addAll(payload.files());
        }
        if (payload.hiddenAppIds() != null) {
            for (String id : payload.hiddenAppIds()) {
                if (id != null && !id.isBlank()) {
                    HIDDEN_APP_IDS.add(id.trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
    }

    public static List<OwnerPcFileEntry> getDesktopFiles() {
        return List.copyOf(DESKTOP_FILES);
    }

    public static Set<String> getHiddenAppIds() {
        return Set.copyOf(HIDDEN_APP_IDS);
    }

    public static boolean isAppHidden(String appId) {
        return appId != null && HIDDEN_APP_IDS.contains(appId.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public static int getDesktopMaxStorageBytes() {
        return desktopMaxStorageBytes;
    }

    public static int getDesktopUsedStorageBytes() {
        return desktopUsedStorageBytes;
    }

    public static String getDesktopComputerLabel() {
        return desktopComputerLabel;
    }

    public static boolean isDesktopPinSet() {
        return desktopPinSet;
    }

    public static boolean hasDesktopDataLoaded() {
        return desktopDataLoaded;
    }

    public static boolean isDesktopSessionUnlocked() {
        return desktopSessionUnlocked
                && desktopComputerLabel != null
                && desktopComputerLabel.equals(desktopSessionComputerLabel);
    }

    public static void markDesktopSessionUnlocked() {
        desktopSessionUnlocked = true;
        desktopSessionComputerLabel = desktopComputerLabel == null ? "" : desktopComputerLabel;
    }

    public static void clearDesktopSession() {
        desktopSessionUnlocked = false;
        desktopSessionComputerLabel = "";
    }

    public static int getPreferredPcUiScaleMode() {
        return preferredPcUiScaleMode;
    }

    public static void setPreferredPcUiScaleMode(int mode) {
        preferredPcUiScaleMode = Math.max(0, Math.min(3, mode));
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
        DESKTOP_FILES.clear();
        HIDDEN_APP_IDS.clear();
        selectedBankId = null;
        ownedCount = 0;
        maxBanks = 1;
        desktopMaxStorageBytes = 0;
        desktopUsedStorageBytes = 0;
        desktopComputerLabel = "Unknown PC";
        desktopPinSet = false;
        desktopDataLoaded = false;
        desktopSessionUnlocked = false;
        desktopSessionComputerLabel = "";
        currentBankData = null;
        toastMessage = "";
        toastUntilMillis = 0L;
        toastSuccess = true;
    }

    public static void clearForUiClose() {
        boolean keepSessionUnlocked = desktopSessionUnlocked;
        String keepSessionComputer = desktopSessionComputerLabel;
        int keepScale = preferredPcUiScaleMode;
        clear();
        desktopSessionUnlocked = keepSessionUnlocked;
        desktopSessionComputerLabel = keepSessionComputer == null ? "" : keepSessionComputer;
        preferredPcUiScaleMode = keepScale;
    }
}
