package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.SmartphoneActionPayload;
import net.austizz.ultimatebankingsystem.network.SmartphoneOpenRequestPayload;
import net.austizz.ultimatebankingsystem.network.SmartphoneSnapshotPayload;
import net.austizz.ultimatebankingsystem.network.ChangePinResponsePayload;
import net.austizz.ultimatebankingsystem.network.ChangePinPayload;
import net.austizz.ultimatebankingsystem.network.PayRequestActionPayload;
import net.austizz.ultimatebankingsystem.network.PayRequestActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.PayRequestEntry;
import net.austizz.ultimatebankingsystem.network.PayRequestInboxRequestPayload;
import net.austizz.ultimatebankingsystem.network.PayRequestInboxResponsePayload;
import net.austizz.ultimatebankingsystem.network.PinAuthRequestPayload;
import net.austizz.ultimatebankingsystem.network.PinAuthResponsePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SmartphoneClientState {
    public enum Mode {
        CLOSED,
        OPENING,
        INTERACTIVE,
        PASSIVE,
        CLOSING
    }

    public enum App {
        HOME,
        BANKING,
        STATISTICS,
        HISTORY,
        SEARCH,
        SEND_MONEY,
        REQUEST_MONEY,
        BANK_PAY_REQUESTS,
        BANK_SETTINGS,
        BANK_STAFF,
        BANK_DISSOLVE,
        BANK_WELCOME,
        BANK_PROFILE,
        BANK_EDIT_PROFILE,
        BANK_SIGN_IN,
        BANK_LANGUAGE,
        BANK_CHANGE_PASSWORD,
        BANK_TERMS,
        ALL_CARDS,
        ADD_CARD,
        ACCOUNT,
        TRANSACTION_DETAIL,
        TAP,
        CALCULATOR,
        PAINT,
        CONTACTS,
        MESSENGER,
        NOTES,
        SETTINGS,
        JOURNEYMAP,
        AUCTION,
        REAL_ESTATE
    }

    public enum InputTarget {
        NONE,
        CALCULATOR,
        TRANSFER_TO,
        TRANSFER_AMOUNT,
        HISTORY_SEARCH,
        REQUEST_TARGET,
        REQUEST_AMOUNT,
        CURRENT_PIN,
        NEW_PIN,
        CONFIRM_PIN,
        BANK_PIN,
        SIGNUP_PIN,
        STAFF_PLAYER,
        STAFF_ROLE,
        STAFF_SALARY,
        DISBAND_CONFIRM,
        PROFILE_NAME,
        MESSAGE,
        NOTE
    }

    public record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + w && py < y + h;
        }
    }

    public record Hitbox(Rect rect, String action, String p1, String p2, String p3) {
    }

    public record PhoneApp(String id, String label, String description) {
    }

    public record PhoneAccount(UUID id,
                               String bankName,
                               String accountType,
                               String shortBalance,
                               String rawBalance,
                               boolean primary,
                               boolean frozen,
                               int creditScore,
                               String accessType,
                               String businessLabel,
                               String role,
                               boolean tapSelected,
                               String virtualCard,
                               boolean pinSet) {
        String appTitle() {
            return bankName + " " + accountType;
        }
    }

    public record PhoneContact(UUID id,
                               String name,
                               boolean online,
                               boolean favorite,
                               boolean muted,
                               boolean blocked,
                               int unread) {
    }

    public record PaymentRecipient(UUID id,
                                   String name,
                                   boolean online,
                                   UUID accountId,
                                   boolean recent,
                                   String type) {
        String targetValue() {
            if ("account".equalsIgnoreCase(type) && accountId != null) {
                return accountId.toString();
            }
            return name == null || name.isBlank() ? id.toString() : name;
        }
    }

    public record OwnedBank(UUID id, String name, String status, int employeeCount) {
    }

    public record BankStaffMember(UUID id, String name, String role, String salary) {
    }

    public record PhoneMessage(UUID otherId,
                               UUID messageId,
                               UUID senderId,
                               String senderName,
                               String body,
                               String time,
                               long createdAt) {
    }

    public record PhoneTransaction(UUID accountId,
                                   UUID txId,
                                   String amount,
                                   String description,
                                   String time,
                                   long timestampMillis,
                                   String direction,
                                   String counterparty,
                                   UUID senderAccountId,
                                   UUID receiverAccountId) {
    }

    private static final List<Hitbox> HITBOXES = new ArrayList<>();
    private static Mode mode = Mode.CLOSED;
    private static App activeApp = App.HOME;
    private static float animation = 0.0F;
    private static boolean cursorReleased;
    private static String statusMessage = "";
    private static String ownerName = "Phone";
    private static String accent = "cyan";
    private static String wallpaper = "aurora";
    private static UUID selectedAccount;
    private static UUID selectedContact;
    private static int noteIndex;
    private static int paintIndex;
    private static String calculatorInput = "";
    private static String calculatorResult = "0";
    private static String transferTo = "";
    private static String transferAmount = "";
    private static String historySearch = "";
    private static String requestTarget = "";
    private static String requestAmount = "";
    private static String currentPin = "";
    private static String newPin = "";
    private static String confirmPin = "";
    private static String bankPin = "";
    private static String signupPin = "";
    private static String staffPlayer = "";
    private static String staffRole = "TELLER";
    private static String staffSalary = "0";
    private static String disbandConfirm = "";
    private static String bankLanguage = "English";
    private static String profileNameDraft = "";
    private static String messageDraft = "";
    private static int messageDraftScrollOffset;
    private static int messageDraftScrollMax;
    private static boolean messageDraftStickToBottom = true;
    private static int messageThreadScrollOffset;
    private static int messageThreadScrollMax;
    private static boolean messageThreadStickToBottom = true;
    private static long lastMessageSubmitMillis;
    private static String lastMessageSubmitToken = "";
    private static String noteDraft = "";
    private static InputTarget inputTarget = InputTarget.NONE;
    private static final long BANKING_LOADING_DURATION_MS = 1150L;
    private static long bankingLoadingStartedMillis;
    private static long bankingLoadingUntilMillis;
    private static boolean bankingLoadingBankPick;
    private static UUID ownerId;
    private static int bankAccountCount;
    private static String defaultBankName = "Central Bank";
    private static OwnedBank ownedBank;
    private static UUID selectedTransaction;
    private static int historyScrollOffset;
    private static int historyScrollMax;
    private static int paymentContactOffset;
    private static String statsMonthKey = "";
    private static UUID pendingBankingAuthAccount;
    private static App pendingBankingAuthTarget = App.BANKING;
    private static boolean paymentTargetModalOpen;
    private static boolean paymentTargetModalRequest;
    private static boolean snapshotLoaded;
    private static long lastSnapshotRequestMillis;
    private static final long SNAPSHOT_RETRY_INTERVAL_MS = 1200L;

    private static final List<PhoneApp> apps = new ArrayList<>();
    private static final List<PhoneAccount> accounts = new ArrayList<>();
    private static final List<PhoneContact> contacts = new ArrayList<>();
    private static final List<PaymentRecipient> paymentRecipients = new ArrayList<>();
    private static final List<BankStaffMember> bankStaffMembers = new ArrayList<>();
    private static final List<PhoneMessage> messages = new ArrayList<>();
    private static final List<PhoneTransaction> transactions = new ArrayList<>();
    private static final List<PayRequestEntry> payRequests = new ArrayList<>();
    private static final Set<UUID> unlockedBankAccounts = new HashSet<>();
    private static final List<String> notes = new ArrayList<>();
    private static final List<String> paintings = new ArrayList<>();
    private static String payRequestsPrimaryLabel = "None";

    private SmartphoneClientState() {
    }

    public static void requestOpen() {
        if (mode == Mode.CLOSED || mode == Mode.CLOSING) {
            ensureDefaultApps();
            snapshotLoaded = false;
            mode = Mode.OPENING;
            animation = Math.max(0.01F, animation);
            requestSnapshot(true);
            releaseCursor();
            return;
        }
        if (mode == Mode.PASSIVE) {
            mode = Mode.INTERACTIVE;
            releaseCursor();
            return;
        }
        close();
    }

    public static void applySnapshot(SmartphoneSnapshotPayload payload) {
        if (payload == null) {
            return;
        }
        statusMessage = payload.statusMessage();
        if (!payload.open()) {
            snapshotLoaded = false;
            close();
            return;
        }
        snapshotLoaded = true;
        parseLines(payload.lines());
        if (payload.animate()) {
            mode = Mode.OPENING;
            animation = Math.max(0.01F, animation);
        } else if (mode == Mode.CLOSED || mode == Mode.CLOSING) {
            mode = Mode.INTERACTIVE;
            animation = 1.0F;
        }
        applyActionSideEffects(statusMessage);
        releaseCursor();
    }

    private static void applyActionSideEffects(String message) {
        String status = message == null ? "" : message.trim();
        if (status.startsWith("Hired ") || status.startsWith("Fired ")) {
            staffPlayer = "";
            staffSalary = "0";
            inputTarget = InputTarget.NONE;
        } else if (status.startsWith("Dissolved ")) {
            disbandConfirm = "";
            activeApp = App.BANKING;
            inputTarget = InputTarget.NONE;
        } else if (status.startsWith("Created Central Bank")
                || status.startsWith("Central Bank checking account ready")) {
            signupPin = "";
            unlockSelectedAccount();
            activeApp = App.BANKING;
            inputTarget = InputTarget.NONE;
            showBankingLoading();
        } else if (status.startsWith("Transferred ")) {
            transferAmount = "";
            inputTarget = InputTarget.NONE;
        } else if (status.startsWith("Pay request sent")) {
            requestAmount = "";
            inputTarget = InputTarget.NONE;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc == null) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) {
            if (mode != Mode.CLOSED) {
                mode = Mode.CLOSED;
                animation = 0.0F;
                grabCursor();
            }
            return;
        }
        if (mode == Mode.OPENING) {
            animation = Math.min(1.0F, animation + 0.18F);
            if (animation >= 1.0F) {
                mode = Mode.INTERACTIVE;
            }
        } else if (mode == Mode.CLOSING) {
            animation = Math.max(0.0F, animation - 0.20F);
            if (animation <= 0.0F) {
                mode = Mode.CLOSED;
                grabCursor();
            }
        }
        if (mode == Mode.INTERACTIVE && !cursorReleased) {
            releaseCursor();
        }
        if ((mode == Mode.PASSIVE || mode == Mode.CLOSED) && cursorReleased) {
            grabCursor();
        }
        if ((mode == Mode.OPENING || mode == Mode.INTERACTIVE || mode == Mode.PASSIVE) && !snapshotLoaded) {
            long now = System.currentTimeMillis();
            if (now - lastSnapshotRequestMillis >= SNAPSHOT_RETRY_INTERVAL_MS) {
                requestSnapshot(false);
            }
        }
    }

    public static boolean isVisible() {
        return mode != Mode.CLOSED && animation > 0.0F;
    }

    public static boolean isInteractive() {
        return mode == Mode.INTERACTIVE || mode == Mode.OPENING;
    }

    public static Mode mode() {
        return mode;
    }

    public static App activeApp() {
        return activeApp;
    }

    public static float animation() {
        return Mth.clamp(animation, 0.0F, 1.0F);
    }

    public static String statusMessage() {
        return statusMessage;
    }

    public static String ownerName() {
        return ownerName;
    }

    public static boolean isOutgoingMessage(PhoneMessage message) {
        if (message == null) {
            return false;
        }
        if (ownerId != null && ownerId.equals(message.senderId())) {
            return true;
        }
        return message.senderName() != null && message.senderName().equalsIgnoreCase(ownerName);
    }

    public static int accentColor() {
        return switch (accent.toLowerCase(Locale.ROOT)) {
            case "green" -> 0xFF23D18B;
            case "gold" -> 0xFFFFCA5C;
            case "rose" -> 0xFFFF6A9C;
            case "violet" -> 0xFF9B7CFF;
            default -> 0xFF52D7FF;
        };
    }

    public static int wallpaperTop() {
        return switch (wallpaper.toLowerCase(Locale.ROOT)) {
            case "dusk" -> 0xFF442052;
            case "forest" -> 0xFF07381F;
            case "mono" -> 0xFF1D2430;
            default -> 0xFF122A5A;
        };
    }

    public static int wallpaperBottom() {
        return switch (wallpaper.toLowerCase(Locale.ROOT)) {
            case "dusk" -> 0xFFB85C38;
            case "forest" -> 0xFF0D7355;
            case "mono" -> 0xFF3B4657;
            default -> 0xFF0B8A8F;
        };
    }

    public static List<PhoneApp> apps() {
        return apps;
    }

    public static List<PhoneAccount> accounts() {
        return accounts;
    }

    public static List<PhoneContact> contacts() {
        return contacts;
    }

    public static List<PaymentRecipient> paymentRecipients() {
        if (paymentContactOffset <= 0) {
            return paymentRecipients;
        }
        return paymentRecipients.stream().skip(paymentContactOffset).toList();
    }

    public static int paymentRecipientCount() {
        return paymentRecipients.size();
    }

    public static int paymentContactOffset() {
        return paymentContactOffset;
    }

    public static OwnedBank ownedBank() {
        return ownedBank;
    }

    public static List<BankStaffMember> bankStaffMembers() {
        return bankStaffMembers;
    }

    public static List<PhoneMessage> messagesForSelectedContact() {
        if (selectedContact == null) {
            return List.of();
        }
        return messages.stream()
                .filter(message -> selectedContact.equals(message.otherId()))
                .sorted(Comparator
                        .comparingLong(PhoneMessage::createdAt)
                        .thenComparing(message -> message.messageId().toString()))
                .toList();
    }

    public static List<PhoneTransaction> transactionsForSelectedAccount() {
        if (selectedAccount == null) {
            return List.of();
        }
        return transactions.stream()
                .filter(tx -> selectedAccount.equals(tx.accountId()))
                .toList();
    }

    public static PhoneTransaction selectedTransaction() {
        UUID id = selectedTransaction;
        if (id == null) {
            return null;
        }
        return transactions.stream().filter(tx -> tx.txId().equals(id)).findFirst().orElse(null);
    }

    public static int historyScrollOffset() {
        return historyScrollOffset;
    }

    public static void setHistoryScrollWindow(int totalRows, int visibleRows) {
        historyScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        historyScrollOffset = Mth.clamp(historyScrollOffset, 0, historyScrollMax);
    }

    public static String statsMonthKey() {
        return statsMonthKey;
    }

    public static List<PayRequestEntry> payRequests() {
        return payRequests;
    }

    public static String payRequestsPrimaryLabel() {
        return payRequestsPrimaryLabel == null || payRequestsPrimaryLabel.isBlank() ? "None" : payRequestsPrimaryLabel;
    }

    public static PhoneAccount selectedAccount() {
        if (selectedAccount == null && !accounts.isEmpty()) {
            selectedAccount = accounts.get(0).id();
        }
        UUID id = selectedAccount;
        return accounts.stream().filter(account -> account.id().equals(id)).findFirst().orElse(null);
    }

    public static PhoneContact selectedContact() {
        if (selectedContact == null && !contacts.isEmpty()) {
            selectedContact = contacts.get(0).id();
        }
        UUID id = selectedContact;
        return contacts.stream().filter(contact -> contact.id().equals(id)).findFirst().orElse(null);
    }

    public static List<String> notes() {
        return notes;
    }

    public static int noteIndex() {
        return noteIndex;
    }

    public static String noteDraft() {
        return noteDraft;
    }

    public static List<String> paintings() {
        return paintings;
    }

    public static int paintIndex() {
        return paintIndex;
    }

    public static String paintingDraft() {
        if (paintings.isEmpty() || paintIndex >= paintings.size()) {
            return "";
        }
        return paintings.get(paintIndex);
    }

    public static String calculatorInput() {
        return calculatorInput;
    }

    public static String calculatorResult() {
        return calculatorResult;
    }

    public static String transferTo() {
        return transferTo;
    }

    public static String transferAmount() {
        return transferAmount;
    }

    public static String historySearch() {
        return historySearch;
    }

    public static String requestTarget() {
        return requestTarget;
    }

    public static boolean paymentTargetModalOpen() {
        return paymentTargetModalOpen;
    }

    public static boolean paymentTargetModalRequest() {
        return paymentTargetModalRequest;
    }

    public static String paymentTargetModalValue() {
        return paymentTargetModalRequest ? requestTarget : transferTo;
    }

    public static String requestAmount() {
        return requestAmount;
    }

    public static String currentPin() {
        return currentPin;
    }

    public static String newPin() {
        return newPin;
    }

    public static String confirmPin() {
        return confirmPin;
    }

    public static String bankPin() {
        return bankPin;
    }

    public static String signupPin() {
        return signupPin;
    }

    public static String staffPlayer() {
        return staffPlayer;
    }

    public static String staffRole() {
        return staffRole;
    }

    public static String staffSalary() {
        return staffSalary;
    }

    public static String disbandConfirm() {
        return disbandConfirm;
    }

    public static boolean hasBankAccounts() {
        return bankAccountCount > 0 || !accounts.isEmpty();
    }

    public static String defaultBankName() {
        return defaultBankName == null || defaultBankName.isBlank() ? "Central Bank" : defaultBankName;
    }

    public static String bankLanguage() {
        return bankLanguage;
    }

    public static String profileNameDraft() {
        return profileNameDraft == null || profileNameDraft.isBlank() ? ownerName : profileNameDraft;
    }

    public static String messageDraft() {
        return messageDraft;
    }

    public static int messageDraftScrollOffset() {
        return messageDraftScrollOffset;
    }

    public static void syncMessageDraftScroll(int lineCount, int visibleLines) {
        int max = Math.max(0, lineCount - Math.max(1, visibleLines));
        messageDraftScrollMax = max;
        if (messageDraftStickToBottom) {
            messageDraftScrollOffset = max;
            return;
        }
        messageDraftScrollOffset = Mth.clamp(messageDraftScrollOffset, 0, max);
        if (messageDraftScrollOffset >= max) {
            messageDraftStickToBottom = true;
        }
    }

    public static int messageThreadScrollOffset() {
        return messageThreadScrollOffset;
    }

    public static void syncMessageThreadScroll(int contentHeight, int viewportHeight) {
        int max = Math.max(0, contentHeight - Math.max(1, viewportHeight));
        messageThreadScrollMax = max;
        if (messageThreadStickToBottom) {
            messageThreadScrollOffset = 0;
            return;
        }
        messageThreadScrollOffset = Mth.clamp(messageThreadScrollOffset, 0, max);
        if (messageThreadScrollOffset <= 0) {
            messageThreadStickToBottom = true;
        }
    }

    public static InputTarget inputTarget() {
        return inputTarget;
    }

    public static boolean isTextInputFocused() {
        return isInteractive() && inputTarget != InputTarget.NONE;
    }

    public static boolean bankingLoadingActive() {
        return System.currentTimeMillis() < bankingLoadingUntilMillis;
    }

    public static boolean bankingBankPickLoadingActive() {
        return bankingLoadingBankPick && bankingLoadingActive();
    }

    public static float bankingLoadingProgress() {
        if (bankingLoadingStartedMillis <= 0L || bankingLoadingUntilMillis <= bankingLoadingStartedMillis) {
            return 1.0F;
        }
        long now = System.currentTimeMillis();
        float progress = (now - bankingLoadingStartedMillis) / (float) (bankingLoadingUntilMillis - bankingLoadingStartedMillis);
        return Mth.clamp(progress, 0.0F, 1.0F);
    }

    private static void showBankingLoading() {
        showBankingLoading(false);
    }

    private static void showBankingLoading(boolean bankPick) {
        bankingLoadingStartedMillis = System.currentTimeMillis();
        bankingLoadingUntilMillis = bankingLoadingStartedMillis + BANKING_LOADING_DURATION_MS;
        bankingLoadingBankPick = bankPick;
    }

    public static boolean bankSignInRequired() {
        PhoneAccount account = selectedAccount();
        return account != null && account.pinSet() && !isAccountUnlocked(account.id());
    }

    public static boolean bankPinSetupRequired() {
        PhoneAccount account = selectedAccount();
        return account != null && !account.pinSet();
    }

    public static PhoneAccount signInAccount() {
        PhoneAccount selected = selectedAccount();
        if (selected != null && selected.pinSet()) {
            return selected;
        }
        return selected;
    }

    private static void openBankAccountApp(UUID accountId, App targetApp, boolean showLoadingWhenReady) {
        openBankAccountApp(accountId, targetApp, showLoadingWhenReady, false);
    }

    private static void openBankAccountApp(UUID accountId, App targetApp, boolean showLoadingWhenReady,
                                           boolean bankPickLoadingWhenReady) {
        if (bankPickLoadingWhenReady) {
            showBankingLoading(true);
        }
        if (!hasBankAccounts()) {
            selectedAccount = null;
            activeApp = App.BANK_WELCOME;
            inputTarget = InputTarget.SIGNUP_PIN;
            return;
        }
        if (accountId == null) {
            PhoneAccount primary = primaryAccount();
            accountId = primary == null ? null : primary.id();
        }
        selectedAccount = accountId;
        PhoneAccount account = selectedAccount();
        if (account == null) {
            activeApp = App.BANK_WELCOME;
            inputTarget = InputTarget.SIGNUP_PIN;
            return;
        }
        pendingBankingAuthAccount = account.id();
        pendingBankingAuthTarget = targetApp == null ? App.BANKING : targetApp;
        switch (SmartphoneBankAccessGate.decide(true, true, account.pinSet(), isAccountUnlocked(account.id()))) {
            case SET_PIN -> {
                activeApp = App.BANK_CHANGE_PASSWORD;
                currentPin = "";
                newPin = "";
                confirmPin = "";
                inputTarget = InputTarget.NEW_PIN;
            }
            case SIGN_IN -> {
                bankPin = "";
                activeApp = App.BANK_SIGN_IN;
                inputTarget = InputTarget.BANK_PIN;
            }
            case READY -> completeBankAccountAccess(targetApp, showLoadingWhenReady, bankPickLoadingWhenReady);
            case WELCOME -> {
                activeApp = App.BANK_WELCOME;
                inputTarget = InputTarget.SIGNUP_PIN;
            }
        }
    }

    private static void completeBankAccountAccess(App targetApp, boolean showLoading) {
        completeBankAccountAccess(targetApp, showLoading, false);
    }

    private static void completeBankAccountAccess(App targetApp, boolean showLoading, boolean bankPickLoading) {
        activeApp = targetApp == null ? App.BANKING : targetApp;
        inputTarget = InputTarget.NONE;
        pendingBankingAuthAccount = null;
        pendingBankingAuthTarget = App.BANKING;
        if (activeApp == App.BANK_EDIT_PROFILE) {
            profileNameDraft = ownerName;
            inputTarget = InputTarget.PROFILE_NAME;
        }
        if (activeApp == App.BANK_PAY_REQUESTS) {
            requestPayRequests();
        }
        if (showLoading) {
            showBankingLoading(bankPickLoading);
        }
    }

    private static boolean requiresBankAccountAccess(App app) {
        return app == App.ACCOUNT
                || app == App.BANKING
                || app == App.STATISTICS
                || app == App.HISTORY
                || app == App.SEARCH
                || app == App.SEND_MONEY
                || app == App.REQUEST_MONEY
                || app == App.BANK_PAY_REQUESTS
                || app == App.BANK_SETTINGS
                || app == App.BANK_STAFF
                || app == App.BANK_DISSOLVE
                || app == App.BANK_PROFILE
                || app == App.BANK_EDIT_PROFILE
                || app == App.BANK_LANGUAGE
                || app == App.BANK_TERMS
                || app == App.ALL_CARDS
                || app == App.ADD_CARD
                || app == App.TRANSACTION_DETAIL
                || app == App.TAP;
    }

    private static PhoneAccount primaryAccount() {
        return accounts.stream()
                .filter(PhoneAccount::primary)
                .findFirst()
                .orElse(accounts.isEmpty() ? null : accounts.get(0));
    }

    private static boolean isAccountUnlocked(UUID accountId) {
        return accountId != null && unlockedBankAccounts.contains(accountId);
    }

    private static void unlockSelectedAccount() {
        PhoneAccount account = selectedAccount();
        if (account != null) {
            unlockAccount(account.id());
        }
    }

    private static void unlockAccount(UUID accountId) {
        if (accountId != null) {
            unlockedBankAccounts.add(accountId);
        }
    }

    private static void clearPendingBankingAuth() {
        pendingBankingAuthAccount = null;
        pendingBankingAuthTarget = App.BANKING;
    }

    public static void setHitboxes(List<Hitbox> hitboxes) {
        HITBOXES.clear();
        if (hitboxes != null) {
            HITBOXES.addAll(hitboxes);
        }
    }

    public static boolean handleMouseButton(double mouseX, double mouseY, int button, int action) {
        if (!isInteractive() || action != GLFW.GLFW_PRESS || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        for (int i = HITBOXES.size() - 1; i >= 0; i--) {
            Hitbox hitbox = HITBOXES.get(i);
            if (hitbox.rect().contains(mouseX, mouseY)) {
                handleUiAction(hitbox.action(), hitbox.p1(), hitbox.p2(), hitbox.p3());
                return true;
            }
        }
        Rect shell = SmartphoneOverlay.lastPhoneRect();
        if (shell == null || !shell.contains(mouseX, mouseY)) {
            mode = Mode.PASSIVE;
            inputTarget = InputTarget.NONE;
            grabCursor();
            return true;
        }
        return true;
    }

    public static boolean handleScroll(double deltaY) {
        if (!isInteractive()) {
            return false;
        }
        if (activeApp == App.HISTORY || activeApp == App.SEARCH || activeApp == App.STATISTICS) {
            int max = Math.max(0, historyScrollMax);
            historyScrollOffset = Mth.clamp(historyScrollOffset + (deltaY < 0 ? 1 : -1), 0, max);
            return true;
        }
        if (activeApp == App.SEND_MONEY || activeApp == App.REQUEST_MONEY) {
            int max = Math.max(0, paymentRecipients.size() - 4);
            paymentContactOffset = Mth.clamp(paymentContactOffset + (deltaY < 0 ? 1 : -1), 0, max);
            return true;
        }
        if (activeApp == App.MESSENGER && inputTarget == InputTarget.MESSAGE && messageDraftScrollMax > 0) {
            messageDraftScrollOffset = Mth.clamp(messageDraftScrollOffset + (deltaY < 0 ? 1 : -1), 0, messageDraftScrollMax);
            messageDraftStickToBottom = messageDraftScrollOffset >= messageDraftScrollMax;
            return true;
        }
        if (activeApp == App.MESSENGER && messageThreadScrollMax > 0) {
            int step = Math.max(14, (int) Math.round(Math.abs(deltaY) * 30.0D));
            messageThreadScrollOffset = Mth.clamp(messageThreadScrollOffset + (deltaY > 0 ? step : -step), 0, messageThreadScrollMax);
            messageThreadStickToBottom = messageThreadScrollOffset <= 0;
            return true;
        }
        return isInteractive();
    }

    public static boolean handleKey(int key, int action) {
        if (!isInteractive() || (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_REPEAT)) {
            return false;
        }
        boolean press = action == GLFW.GLFW_PRESS;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!press) {
                return true;
            }
            if (paymentTargetModalOpen) {
                paymentTargetModalOpen = false;
                inputTarget = InputTarget.NONE;
                return true;
            }
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            mutateInput(true, '\0');
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (press) {
                submitFocusedInput();
            }
            return true;
        }
        char typed = keyToChar(key);
        if (typed != 0) {
            mutateInput(false, typed);
            return true;
        }
        return true;
    }

    public static void close() {
        if (mode == Mode.CLOSED) {
            return;
        }
        mode = Mode.CLOSING;
        inputTarget = InputTarget.NONE;
        paymentTargetModalOpen = false;
        snapshotLoaded = false;
        unlockedBankAccounts.clear();
        pendingBankingAuthAccount = null;
        pendingBankingAuthTarget = App.BANKING;
        bankingLoadingBankPick = false;
        bankPin = "";
        grabCursor();
    }

    private static void handleUiAction(String action, String p1, String p2, String p3) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "HOME" -> {
                activeApp = App.HOME;
                inputTarget = InputTarget.NONE;
                clearPendingBankingAuth();
            }
            case "APP" -> {
                if (p1 != null && p1.startsWith("account:")) {
                    openBankAccountApp(parseUuid(p1.substring("account:".length())), App.ACCOUNT, true);
                    return;
                }
                App requestedApp = parseApp(p1);
                if (requestedApp == App.BANKING) {
                    PhoneAccount primary = primaryAccount();
                    openBankAccountApp(primary == null ? null : primary.id(), App.BANKING, true, true);
                    return;
                }
                if (requiresBankAccountAccess(requestedApp)) {
                    PhoneAccount account = selectedAccount();
                    if (account == null) {
                        account = primaryAccount();
                    }
                    openBankAccountApp(account == null ? null : account.id(), requestedApp, false);
                    return;
                }
                clearPendingBankingAuth();
                if (requestedApp == App.MESSENGER) {
                    activeApp = App.CONTACTS;
                    inputTarget = InputTarget.NONE;
                    return;
                }
                activeApp = requestedApp;
                inputTarget = InputTarget.NONE;
                if (activeApp == App.BANK_EDIT_PROFILE) {
                    profileNameDraft = ownerName;
                    inputTarget = InputTarget.PROFILE_NAME;
                } else if (activeApp == App.BANK_PAY_REQUESTS) {
                    requestPayRequests();
                }
            }
            case "ACCOUNT" -> {
                openBankAccountApp(parseUuid(p1), App.ACCOUNT, true);
            }
            case "CONTACT" -> {
                selectedContact = parseUuid(p1);
                resetMessageThreadScroll();
                activeApp = App.MESSENGER;
                inputTarget = InputTarget.MESSAGE;
                PacketDistributor.sendToServer(new SmartphoneActionPayload("READ_CONVERSATION", p1, "", ""));
            }
            case "INPUT" -> inputTarget = parseInputTarget(p1);
            case "SEARCH_CLEAR" -> {
                historySearch = "";
                inputTarget = InputTarget.HISTORY_SEARCH;
            }
            case "CALC_CLEAR" -> {
                calculatorInput = "";
                calculatorResult = "0";
                inputTarget = InputTarget.CALCULATOR;
            }
            case "CALC_EVAL" -> evaluateCalculator();
            case "SET_PRIMARY" -> sendAction("SET_PRIMARY", selectedAccountString(), "", "");
            case "SET_TAP_ACCOUNT" -> sendAction("SET_TAP_ACCOUNT", selectedAccountString(), "", "");
            case "SET_TAP_ACCOUNT_FOR" -> {
                selectedAccount = parseUuid(p1);
                sendAction("SET_TAP_ACCOUNT", selectedAccountString(), "", "");
                activeApp = App.ALL_CARDS;
                inputTarget = InputTarget.NONE;
            }
            case "TRANSFER_CONTACT" -> {
                transferTo = p1 == null ? "" : p1;
                activeApp = App.SEND_MONEY;
                inputTarget = InputTarget.TRANSFER_AMOUNT;
            }
            case "REQUEST_CONTACT" -> {
                requestTarget = p1 == null ? "" : p1;
                activeApp = App.REQUEST_MONEY;
                inputTarget = InputTarget.REQUEST_AMOUNT;
            }
            case "OPEN_PAYMENT_TARGET_MODAL" -> {
                paymentTargetModalOpen = true;
                paymentTargetModalRequest = "request".equalsIgnoreCase(p1);
                inputTarget = paymentTargetModalRequest ? InputTarget.REQUEST_TARGET : InputTarget.TRANSFER_TO;
            }
            case "CONFIRM_PAYMENT_TARGET_MODAL" -> {
                String value = paymentTargetModalValue();
                if (value == null || value.isBlank()) {
                    statusMessage = "Enter a player name or account ID.";
                    inputTarget = paymentTargetModalRequest ? InputTarget.REQUEST_TARGET : InputTarget.TRANSFER_TO;
                    return;
                }
                sendAction("REMEMBER_PAYMENT_TARGET", value,
                        paymentTargetModalRequest ? "request" : "send", "");
                paymentTargetModalOpen = false;
                inputTarget = paymentTargetModalRequest ? InputTarget.REQUEST_AMOUNT : InputTarget.TRANSFER_AMOUNT;
            }
            case "CLOSE_PAYMENT_TARGET_MODAL" -> {
                paymentTargetModalOpen = false;
                inputTarget = InputTarget.NONE;
            }
            case "PAY_REQUESTS_REFRESH" -> requestPayRequests();
            case "PAY_REQUEST_ACCEPT" -> sendPayRequestAction(p1, "accept_primary");
            case "PAY_REQUEST_DECLINE" -> sendPayRequestAction(p1, "decline");
            case "PAYMENT_CONTACTS_PREV" -> paymentContactOffset = Math.max(0, paymentContactOffset - 1);
            case "PAYMENT_CONTACTS_NEXT" -> {
                int max = Math.max(0, paymentRecipients.size() - 4);
                paymentContactOffset = Mth.clamp(paymentContactOffset + 1, 0, max);
            }
            case "TX_DETAIL" -> {
                selectedTransaction = parseUuid(p1);
                activeApp = App.TRANSACTION_DETAIL;
                inputTarget = InputTarget.NONE;
            }
            case "STATS_MONTH" -> {
                statsMonthKey = p1 == null ? "" : p1;
                historyScrollOffset = 0;
            }
            case "COPY_SELECTED_ACCOUNT_ID" -> copySelectedAccountId();
            case "OPEN_DEFAULT_ACCOUNT" -> {
                if (signupPin == null || !signupPin.trim().matches("\\d{4}")) {
                    statusMessage = "Enter a 4-digit PIN first.";
                    inputTarget = InputTarget.SIGNUP_PIN;
                    return;
                }
                sendAction("OPEN_DEFAULT_ACCOUNT", signupPin, "", "");
                inputTarget = InputTarget.NONE;
                activeApp = App.BANKING;
                showBankingLoading();
            }
            case "BANK_HIRE" -> {
                if (ownedBank == null) {
                    statusMessage = "You do not own a bank.";
                    return;
                }
                String role = staffRole.isBlank() ? "TELLER" : staffRole;
                String salary = staffSalary.isBlank() ? "0" : staffSalary;
                sendAction("BANK_HIRE", ownedBank.id().toString(), staffPlayer, role + "|" + salary);
                inputTarget = InputTarget.NONE;
            }
            case "BANK_FIRE" -> {
                if (ownedBank == null) {
                    statusMessage = "You do not own a bank.";
                    return;
                }
                sendAction("BANK_FIRE", ownedBank.id().toString(), staffPlayer, "");
                inputTarget = InputTarget.NONE;
            }
            case "BANK_STAFF_SELECT" -> {
                staffPlayer = p1 == null ? "" : p1;
                inputTarget = InputTarget.STAFF_PLAYER;
            }
            case "BANK_FIRE_MEMBER" -> {
                if (ownedBank == null) {
                    statusMessage = "You do not own a bank.";
                    return;
                }
                staffPlayer = p1 == null ? "" : p1;
                sendAction("BANK_FIRE", ownedBank.id().toString(), staffPlayer, "");
                inputTarget = InputTarget.NONE;
            }
            case "DISBAND_BANK" -> {
                if (ownedBank == null) {
                    statusMessage = "You do not own a bank.";
                    return;
                }
                sendAction("DISBAND_BANK", ownedBank.id().toString(), disbandConfirm, "");
                inputTarget = InputTarget.NONE;
            }
            case "BANK_LANGUAGE" -> {
                bankLanguage = p1 == null || p1.isBlank() ? "English" : p1;
                statusMessage = "Banking language set to " + bankLanguage + ".";
                activeApp = App.BANK_LANGUAGE;
                inputTarget = InputTarget.NONE;
            }
            case "BANK_SIGN_IN" -> {
                PhoneAccount account = signInAccount();
                if (account == null || !account.pinSet()) {
                    pendingBankingAuthAccount = account == null ? selectedAccount : account.id();
                    activeApp = App.BANK_CHANGE_PASSWORD;
                    currentPin = "";
                    newPin = "";
                    confirmPin = "";
                    inputTarget = InputTarget.NEW_PIN;
                    return;
                }
                String pin = bankPin == null ? "" : bankPin.trim();
                if (!pin.matches("\\d{4}")) {
                    statusMessage = "Enter your 4-digit account PIN.";
                    inputTarget = InputTarget.BANK_PIN;
                    return;
                }
                pendingBankingAuthAccount = account.id();
                PacketDistributor.sendToServer(new PinAuthRequestPayload(account.id(), pin));
                statusMessage = "Checking PIN...";
            }
            case "SAVE_BANK_PROFILE" -> {
                String name = profileNameDraft == null || profileNameDraft.isBlank() ? ownerName : profileNameDraft;
                sendAction("SET_PROFILE_NAME", name, "", "");
                ownerName = name;
                activeApp = App.BANK_PROFILE;
                inputTarget = InputTarget.NONE;
            }
            case "CHANGE_BANK_PIN" -> {
                PhoneAccount account = selectedAccount();
                if (account == null) {
                    statusMessage = "Select an account before changing PIN.";
                    return;
                }
                String newValue = newPin == null ? "" : newPin.trim();
                String confirmValue = confirmPin == null ? "" : confirmPin.trim();
                if (!newValue.matches("\\d{4}") || !confirmValue.matches("\\d{4}")) {
                    statusMessage = "PIN must be exactly 4 digits.";
                    inputTarget = InputTarget.NEW_PIN;
                    return;
                }
                if (!newValue.equals(confirmValue)) {
                    statusMessage = "New PIN confirmation does not match.";
                    inputTarget = InputTarget.CONFIRM_PIN;
                    return;
                }
                PacketDistributor.sendToServer(new ChangePinPayload(account.id(), currentPin, newValue));
                statusMessage = "PIN change submitted.";
                currentPin = "";
                newPin = "";
                confirmPin = "";
                inputTarget = InputTarget.NONE;
            }
            case "TRANSFER" -> sendAction("TRANSFER", selectedAccountString(), transferTo, transferAmount);
            case "REQUEST_MONEY" -> {
                PhoneAccount account = selectedAccount();
                if (account != null) {
                    sendAction("REQUEST_MONEY", account.id().toString(), requestTarget, requestAmount);
                }
            }
            case "SEND_MESSAGE" -> {
                String body = messageDraft == null ? "" : messageDraft;
                if (selectedContact == null || body.isBlank()) {
                    statusMessage = "Choose a contact and write a message.";
                    inputTarget = InputTarget.MESSAGE;
                    return;
                }
                String submitToken = selectedContact + "\n" + body.trim();
                long now = System.currentTimeMillis();
                if (submitToken.equals(lastMessageSubmitToken) && now - lastMessageSubmitMillis < 1200L) {
                    resetMessageDraft();
                    inputTarget = InputTarget.MESSAGE;
                    return;
                }
                lastMessageSubmitToken = submitToken;
                lastMessageSubmitMillis = now;
                sendAction("SEND_MESSAGE", selectedContactString(), body, "");
                resetMessageDraft();
                resetMessageThreadScroll();
                inputTarget = InputTarget.MESSAGE;
            }
            case "SAVE_NOTE" -> sendAction("SAVE_NOTE", String.valueOf(noteIndex), noteDraft, "");
            case "DELETE_NOTE" -> sendAction("DELETE_NOTE", String.valueOf(noteIndex), "", "");
            case "NOTE_SELECT" -> {
                noteIndex = Math.max(0, parseInt(p1, 0));
                noteDraft = noteIndex < notes.size() ? notes.get(noteIndex) : "";
                activeApp = App.NOTES;
                inputTarget = InputTarget.NOTE;
            }
            case "PAINT_SELECT" -> {
                paintIndex = Math.max(0, parseInt(p1, 0));
                activeApp = App.PAINT;
                inputTarget = InputTarget.NONE;
            }
            case "PAINT_SAVE" -> sendAction("SAVE_PAINTING", String.valueOf(paintIndex), buildNextPainting(), "");
            case "THEME" -> sendAction("SET_THEME", p1, p2, "");
            case "FAVORITE" -> sendAction("FAVORITE_CONTACT", selectedContactString(), "", "");
            case "UNFAVORITE" -> sendAction("UNFAVORITE_CONTACT", selectedContactString(), "", "");
            case "MUTE" -> sendAction("MUTE_CONTACT", selectedContactString(), "", "");
            case "UNMUTE" -> sendAction("UNMUTE_CONTACT", selectedContactString(), "", "");
            case "BLOCK" -> sendAction("BLOCK_CONTACT", selectedContactString(), "", "");
            case "UNBLOCK" -> sendAction("UNBLOCK_CONTACT", selectedContactString(), "", "");
            case "REPORT" -> sendAction("REPORT_CONTACT", selectedContactString(), messageDraft.isBlank() ? "Phone report" : messageDraft, "");
            case "PASSIVE" -> {
                mode = Mode.PASSIVE;
                grabCursor();
            }
            case "CLOSE" -> close();
            default -> {
            }
        }
    }

    private static void sendAction(String action, String p1, String p2, String p3) {
        PacketDistributor.sendToServer(new SmartphoneActionPayload(action, p1, p2, p3));
    }

    private static void resetMessageDraft() {
        messageDraft = "";
        messageDraftScrollOffset = 0;
        messageDraftScrollMax = 0;
        messageDraftStickToBottom = true;
    }

    private static void resetMessageThreadScroll() {
        messageThreadScrollOffset = 0;
        messageThreadScrollMax = 0;
        messageThreadStickToBottom = true;
    }

    private static void requestSnapshot(boolean animate) {
        lastSnapshotRequestMillis = System.currentTimeMillis();
        PacketDistributor.sendToServer(new SmartphoneOpenRequestPayload(animate));
    }

    private static void copySelectedAccountId() {
        PhoneAccount account = selectedAccount();
        if (account == null || account.id() == null) {
            statusMessage = "No account selected.";
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(account.id().toString());
            statusMessage = "Copied account ID.";
        } else {
            statusMessage = "Clipboard unavailable.";
        }
    }

    private static void requestPayRequests() {
        PhoneAccount account = selectedAccount();
        if (account == null) {
            statusMessage = "Select an account before viewing pay requests.";
            return;
        }
        PacketDistributor.sendToServer(new PayRequestInboxRequestPayload(account.id()));
        statusMessage = "Refreshing pay requests...";
    }

    private static void sendPayRequestAction(String requestIdRaw, String action) {
        PhoneAccount account = selectedAccount();
        UUID requestId = parseUuid(requestIdRaw);
        if (account == null || requestId == null) {
            statusMessage = "Choose a valid pay request first.";
            return;
        }
        PacketDistributor.sendToServer(new PayRequestActionPayload(account.id(), requestId, action, ""));
        statusMessage = "Updating pay request...";
    }

    public static void handlePinAuthResponse(PinAuthResponsePayload payload) {
        if (payload == null || activeApp != App.BANK_SIGN_IN) {
            return;
        }
        if (payload.success()) {
            UUID accountId = pendingBankingAuthAccount;
            if (accountId == null) {
                PhoneAccount account = selectedAccount();
                accountId = account == null ? null : account.id();
            }
            unlockAccount(accountId);
            bankPin = "";
            inputTarget = InputTarget.NONE;
            statusMessage = "";
            activeApp = pendingBankingAuthTarget == null ? App.BANKING : pendingBankingAuthTarget;
            pendingBankingAuthAccount = null;
            pendingBankingAuthTarget = App.BANKING;
            showBankingLoading();
            return;
        }
        bankPin = "";
        inputTarget = InputTarget.BANK_PIN;
        statusMessage = payload.message() == null || payload.message().isBlank()
                ? "Incorrect PIN."
                : payload.message();
        if (payload.pinSetupRequired()) {
            activeApp = App.BANK_CHANGE_PASSWORD;
            inputTarget = InputTarget.NEW_PIN;
        }
    }

    public static void handlePayRequestInboxResponse(PayRequestInboxResponsePayload payload) {
        payRequests.clear();
        if (payload != null && payload.requests() != null) {
            payRequests.addAll(payload.requests());
            payRequestsPrimaryLabel = payload.primaryAccountLabel();
            statusMessage = payRequests.isEmpty() ? "No pending pay requests." : "";
        }
    }

    public static void handleChangePinResponse(ChangePinResponsePayload payload) {
        if (payload == null) {
            return;
        }
        if (mode == Mode.CLOSED) {
            return;
        }
        if (payload.success()) {
            unlockSelectedAccount();
            currentPin = "";
            newPin = "";
            confirmPin = "";
            bankPin = "";
            inputTarget = InputTarget.NONE;
            activeApp = pendingBankingAuthTarget == null ? App.BANKING : pendingBankingAuthTarget;
            pendingBankingAuthAccount = null;
            pendingBankingAuthTarget = App.BANKING;
            statusMessage = "PIN updated successfully.";
            showBankingLoading();
            PacketDistributor.sendToServer(new SmartphoneOpenRequestPayload(false));
            return;
        }
        statusMessage = payload.errorMessage() == null || payload.errorMessage().isBlank()
                ? "PIN update failed."
                : payload.errorMessage();
        inputTarget = InputTarget.NEW_PIN;
    }

    public static void handlePayRequestActionResponse(PayRequestActionResponsePayload payload) {
        if (payload == null) {
            return;
        }
        statusMessage = payload.message();
        if (payload.success()) {
            requestPayRequests();
        }
    }

    private static void submitFocusedInput() {
        if (inputTarget == InputTarget.CALCULATOR) {
            evaluateCalculator();
        } else if (paymentTargetModalOpen
                && (inputTarget == InputTarget.TRANSFER_TO || inputTarget == InputTarget.REQUEST_TARGET)) {
            handleUiAction("CONFIRM_PAYMENT_TARGET_MODAL", "", "", "");
        } else if (inputTarget == InputTarget.MESSAGE) {
            handleUiAction("SEND_MESSAGE", "", "", "");
        } else if (inputTarget == InputTarget.NOTE) {
            handleUiAction("SAVE_NOTE", "", "", "");
        } else if (inputTarget == InputTarget.TRANSFER_AMOUNT) {
            handleUiAction("TRANSFER", "", "", "");
        } else if (inputTarget == InputTarget.REQUEST_AMOUNT) {
            handleUiAction("REQUEST_MONEY", "", "", "");
        } else if (inputTarget == InputTarget.CONFIRM_PIN) {
            handleUiAction("CHANGE_BANK_PIN", "", "", "");
        } else if (inputTarget == InputTarget.BANK_PIN) {
            handleUiAction("BANK_SIGN_IN", "", "", "");
        } else if (inputTarget == InputTarget.SIGNUP_PIN) {
            handleUiAction("OPEN_DEFAULT_ACCOUNT", "", "", "");
        } else if (inputTarget == InputTarget.PROFILE_NAME) {
            handleUiAction("SAVE_BANK_PROFILE", "", "", "");
        }
    }

    private static void mutateInput(boolean backspace, char typed) {
        switch (inputTarget) {
            case CALCULATOR -> calculatorInput = mutate(calculatorInput, backspace, typed, 48, "0123456789+-*/(). ");
            case TRANSFER_TO -> transferTo = mutate(transferTo, backspace, typed, 64, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-");
            case TRANSFER_AMOUNT -> transferAmount = mutate(transferAmount, backspace, typed, 16, "0123456789.");
            case HISTORY_SEARCH -> historySearch = mutate(historySearch, backspace, typed, 40, null);
            case REQUEST_TARGET -> requestTarget = mutate(requestTarget, backspace, typed, 64, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-");
            case REQUEST_AMOUNT -> requestAmount = mutate(requestAmount, backspace, typed, 16, "0123456789.");
            case CURRENT_PIN -> currentPin = mutate(currentPin, backspace, typed, 4, "0123456789");
            case NEW_PIN -> newPin = mutate(newPin, backspace, typed, 4, "0123456789");
            case CONFIRM_PIN -> confirmPin = mutate(confirmPin, backspace, typed, 4, "0123456789");
            case BANK_PIN -> bankPin = mutate(bankPin, backspace, typed, 4, "0123456789");
            case SIGNUP_PIN -> signupPin = mutate(signupPin, backspace, typed, 4, "0123456789");
            case STAFF_PLAYER -> staffPlayer = mutate(staffPlayer, backspace, typed, 36, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-");
            case STAFF_ROLE -> staffRole = mutate(staffRole, backspace, typed, 16, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
            case STAFF_SALARY -> staffSalary = mutate(staffSalary, backspace, typed, 16, "0123456789.");
            case DISBAND_CONFIRM -> disbandConfirm = mutate(disbandConfirm, backspace, typed, 12, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
            case PROFILE_NAME -> profileNameDraft = mutate(profileNameDraft, backspace, typed, 32, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_ -");
            case MESSAGE -> {
                String updated = mutate(messageDraft, backspace, typed, 180, null);
                if (!updated.equals(messageDraft)) {
                    messageDraft = updated;
                    messageDraftStickToBottom = true;
                }
            }
            case NOTE -> noteDraft = mutate(noteDraft, backspace, typed, 4000, null);
            default -> {
            }
        }
    }

    private static String mutate(String value, boolean backspace, char typed, int max, String allowed) {
        String current = value == null ? "" : value;
        if (backspace) {
            return current.isEmpty() ? current : current.substring(0, current.length() - 1);
        }
        if (typed == 0 || current.length() >= max) {
            return current;
        }
        if (allowed != null && allowed.indexOf(typed) < 0) {
            return current;
        }
        return current + typed;
    }

    private static char keyToChar(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            boolean shift = GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            char base = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(base) : base;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            return (char) ('0' + (key - GLFW.GLFW_KEY_0));
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + (key - GLFW.GLFW_KEY_KP_0));
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> ' ';
            case GLFW.GLFW_KEY_PERIOD, GLFW.GLFW_KEY_KP_DECIMAL -> '.';
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> '-';
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> '+';
            case GLFW.GLFW_KEY_SLASH, GLFW.GLFW_KEY_KP_DIVIDE -> '/';
            case GLFW.GLFW_KEY_KP_MULTIPLY -> '*';
            case GLFW.GLFW_KEY_LEFT_BRACKET -> '(';
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> ')';
            default -> 0;
        };
    }

    private static void evaluateCalculator() {
        try {
            calculatorResult = String.valueOf(Math.round(evaluateExpression(calculatorInput) * 100.0D) / 100.0D);
        } catch (RuntimeException ex) {
            calculatorResult = "Error";
        }
    }

    private static double evaluateExpression(String input) {
        String expr = input == null ? "" : input.replace(" ", "");
        if (expr.isBlank()) {
            return 0.0D;
        }
        final int[] pos = {0};
        return parseExpression(expr, pos);
    }

    private static double parseExpression(String s, int[] pos) {
        double value = parseTerm(s, pos);
        while (pos[0] < s.length()) {
            char op = s.charAt(pos[0]);
            if (op != '+' && op != '-') {
                break;
            }
            pos[0]++;
            double right = parseTerm(s, pos);
            value = op == '+' ? value + right : value - right;
        }
        return value;
    }

    private static double parseTerm(String s, int[] pos) {
        double value = parseFactor(s, pos);
        while (pos[0] < s.length()) {
            char op = s.charAt(pos[0]);
            if (op != '*' && op != '/') {
                break;
            }
            pos[0]++;
            double right = parseFactor(s, pos);
            value = op == '*' ? value * right : value / right;
        }
        return value;
    }

    private static double parseFactor(String s, int[] pos) {
        if (pos[0] < s.length() && s.charAt(pos[0]) == '(') {
            pos[0]++;
            double value = parseExpression(s, pos);
            if (pos[0] < s.length() && s.charAt(pos[0]) == ')') {
                pos[0]++;
            }
            return value;
        }
        int start = pos[0];
        if (pos[0] < s.length() && (s.charAt(pos[0]) == '-' || s.charAt(pos[0]) == '+')) {
            pos[0]++;
        }
        while (pos[0] < s.length() && (Character.isDigit(s.charAt(pos[0])) || s.charAt(pos[0]) == '.')) {
            pos[0]++;
        }
        if (start == pos[0]) {
            throw new IllegalArgumentException("Expected number");
        }
        return Double.parseDouble(s.substring(start, pos[0]));
    }

    private static String buildNextPainting() {
        String current = paintingDraft();
        int next = current.length() % 9;
        return current + Integer.toHexString(next);
    }

    private static String selectedAccountString() {
        PhoneAccount account = selectedAccount();
        return account == null ? "" : account.id().toString();
    }

    private static String selectedContactString() {
        PhoneContact contact = selectedContact();
        return contact == null ? "" : contact.id().toString();
    }

    private static void parseLines(List<String> lines) {
        apps.clear();
        accounts.clear();
        contacts.clear();
        paymentRecipients.clear();
        bankStaffMembers.clear();
        messages.clear();
        transactions.clear();
        notes.clear();
        paintings.clear();
        ownedBank = null;
        bankAccountCount = 0;
        defaultBankName = "Central Bank";
        if (lines == null) {
            ensureDefaultApps();
            return;
        }
        for (String line : lines) {
            List<String> parts = split(line);
            if (parts.isEmpty()) {
                continue;
            }
            switch (parts.get(0)) {
                case "owner" -> {
                    ownerId = parseUuid(get(parts, 1, ""));
                    ownerName = get(parts, 3, get(parts, 2, "Phone"));
                    if (activeApp != App.BANK_EDIT_PROFILE) {
                        profileNameDraft = ownerName;
                    }
                }
                case "theme" -> {
                    accent = get(parts, 1, "cyan");
                    wallpaper = get(parts, 2, "aurora");
                }
                case "bank_state" -> {
                    bankAccountCount = parseInt(get(parts, 1, "0"), 0);
                    defaultBankName = get(parts, 2, "Central Bank");
                }
                case "app" -> apps.add(new PhoneApp(get(parts, 1, ""), get(parts, 2, "App"), get(parts, 3, "")));
                case "owned_bank" -> {
                    UUID id = parseUuid(get(parts, 1, ""));
                    if (id != null) {
                        ownedBank = new OwnedBank(id, get(parts, 2, "Bank"),
                                get(parts, 3, "ACTIVE"), parseInt(get(parts, 4, "0"), 0));
                    }
                }
                case "bank_staff" -> {
                    UUID id = parseUuid(get(parts, 1, ""));
                    if (id != null) {
                        bankStaffMembers.add(new BankStaffMember(id, get(parts, 2, shortUuid(id)),
                                get(parts, 3, "STAFF"), get(parts, 4, "$0")));
                    }
                }
                case "account" -> {
                    UUID id = parseUuid(get(parts, 1, ""));
                    if (id != null) {
                        accounts.add(new PhoneAccount(id, get(parts, 2, "Bank"), get(parts, 3, "Account"),
                                get(parts, 4, "$0"), get(parts, 5, "0"), parseBool(get(parts, 6, "false")),
                                parseBool(get(parts, 7, "false")), parseInt(get(parts, 8, "0"), 0),
                                get(parts, 9, "PERSONAL"), get(parts, 10, ""), get(parts, 11, ""),
                                parseBool(get(parts, 12, "false")), get(parts, 13, ""),
                                parseBool(get(parts, 14, "false"))));
                    }
                }
                case "tx" -> {
                    UUID accountId = parseUuid(get(parts, 1, ""));
                    UUID txId = parseUuid(get(parts, 2, ""));
                    if (accountId != null && txId != null) {
                        transactions.add(new PhoneTransaction(accountId, txId, get(parts, 3, "$0"), get(parts, 4, ""),
                                get(parts, 5, ""), parseLong(get(parts, 6, "0"), 0L), get(parts, 7, ""),
                                get(parts, 8, ""), parseUuid(get(parts, 9, "")), parseUuid(get(parts, 10, ""))));
                    }
                }
                case "payment_contact" -> {
                    UUID id = parseUuid(get(parts, 1, ""));
                    UUID accountId = parseUuid(get(parts, 4, ""));
                    if (id != null && accountId != null) {
                        paymentRecipients.add(new PaymentRecipient(id, get(parts, 2, "Player"),
                                parseBool(get(parts, 3, "false")), accountId,
                                parseBool(get(parts, 5, "false")), get(parts, 6, "player")));
                    }
                }
                case "contact" -> {
                    UUID id = parseUuid(get(parts, 1, ""));
                    if (id != null) {
                        contacts.add(new PhoneContact(id, get(parts, 2, "Player"), parseBool(get(parts, 3, "false")),
                                parseBool(get(parts, 4, "false")), parseBool(get(parts, 5, "false")),
                                parseBool(get(parts, 6, "false")), parseInt(get(parts, 7, "0"), 0)));
                    }
                }
                case "message" -> {
                    UUID other = parseUuid(get(parts, 1, ""));
                    UUID messageId = parseUuid(get(parts, 2, ""));
                    UUID sender = parseUuid(get(parts, 3, ""));
                    if (other != null && sender != null) {
                        messages.add(new PhoneMessage(other, messageId == null ? fallbackMessageId(parts) : messageId,
                                sender, get(parts, 4, "Player"), get(parts, 5, ""), get(parts, 6, ""),
                                parseLong(get(parts, 7, "0"), 0L)));
                    } else {
                        sender = parseUuid(get(parts, 2, ""));
                        if (other != null && sender != null) {
                            long fallbackCreatedAt = -messages.size();
                            messages.add(new PhoneMessage(other, fallbackMessageId(parts), sender,
                                    get(parts, 3, "Player"), get(parts, 4, ""), get(parts, 5, ""),
                                    fallbackCreatedAt));
                        }
                    }
                }
                case "note" -> {
                    int index = parseInt(get(parts, 1, "0"), 0);
                    while (notes.size() <= index) {
                        notes.add("");
                    }
                    notes.set(index, get(parts, 2, ""));
                }
                case "painting" -> {
                    int index = parseInt(get(parts, 1, "0"), 0);
                    while (paintings.size() <= index) {
                        paintings.add("");
                    }
                    paintings.set(index, get(parts, 2, ""));
                }
                default -> {
                }
            }
        }
        if (selectedAccount == null && !accounts.isEmpty()) {
            selectedAccount = accounts.get(0).id();
        }
        ensureDefaultApps();
        if (selectedAccount != null && accounts.stream().noneMatch(account -> account.id().equals(selectedAccount))) {
            selectedAccount = accounts.isEmpty() ? null : accounts.get(0).id();
        }
        unlockedBankAccounts.removeIf(accountId -> accounts.stream().noneMatch(account -> account.id().equals(accountId)));
        if (pendingBankingAuthAccount != null
                && accounts.stream().noneMatch(account -> account.id().equals(pendingBankingAuthAccount))) {
            clearPendingBankingAuth();
        }
        if (selectedContact == null && !contacts.isEmpty()) {
            selectedContact = contacts.get(0).id();
        }
        if (selectedTransaction != null && transactions.stream().noneMatch(tx -> selectedTransaction.equals(tx.txId()))) {
            selectedTransaction = null;
            if (activeApp == App.TRANSACTION_DETAIL) {
                activeApp = App.HISTORY;
            }
        }
        paymentContactOffset = Mth.clamp(paymentContactOffset, 0, Math.max(0, paymentRecipients.size() - 4));
        if (noteDraft.isBlank() && noteIndex < notes.size()) {
            noteDraft = notes.get(noteIndex);
        }
    }

    private static void ensureDefaultApps() {
        if (!apps.isEmpty()) {
            return;
        }
        apps.add(new PhoneApp("banking", "Banking", "UBS accounts and payments"));
        apps.add(new PhoneApp("tap", "Tap to Pay", "Default phone payment card"));
        apps.add(new PhoneApp("calculator", "Calculator", "Fast arithmetic"));
        apps.add(new PhoneApp("paint", "Paint", "Pixel sketch pad"));
        apps.add(new PhoneApp("contacts", "Contacts", "Server player directory"));
        apps.add(new PhoneApp("messenger", "Messenger", "Private phone messages"));
        apps.add(new PhoneApp("notes", "Notes", "Private phone notes"));
        apps.add(new PhoneApp("settings", "Settings", "Phone customization"));
    }

    private static List<String> split(String line) {
        String[] raw = (line == null ? "" : line).split("\\|", -1);
        List<String> out = new ArrayList<>();
        for (String part : raw) {
            out.add(URLDecoder.decode(part, StandardCharsets.UTF_8));
        }
        return out;
    }

    public static String encodeActionValue(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String get(List<String> values, int index, String fallback) {
        return index >= 0 && index < values.size() ? values.get(index) : fallback;
    }

    private static App parseApp(String value) {
        String id = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (id) {
            case "banking" -> App.BANKING;
            case "statistics", "stats" -> App.STATISTICS;
            case "history", "transactions", "transaction-history" -> App.HISTORY;
            case "search" -> App.SEARCH;
            case "send-money", "send" -> App.SEND_MONEY;
            case "request-money", "request" -> App.REQUEST_MONEY;
            case "bank-pay-requests", "pay-requests", "requests-inbox" -> App.BANK_PAY_REQUESTS;
            case "bank-settings", "banking-settings" -> App.BANK_SETTINGS;
            case "bank-staff", "staff" -> App.BANK_STAFF;
            case "bank-dissolve", "dissolve-bank", "disband-bank" -> App.BANK_DISSOLVE;
            case "bank-welcome", "welcome" -> App.BANK_WELCOME;
            case "bank-profile", "profile" -> App.BANK_PROFILE;
            case "bank-edit-profile", "edit-profile" -> App.BANK_EDIT_PROFILE;
            case "bank-sign-in", "sign-in", "login" -> App.BANK_SIGN_IN;
            case "bank-language", "language" -> App.BANK_LANGUAGE;
            case "bank-change-password", "change-password", "change-pin" -> App.BANK_CHANGE_PASSWORD;
            case "bank-terms", "terms" -> App.BANK_TERMS;
            case "all-cards" -> App.ALL_CARDS;
            case "my-cards", "cards" -> App.ACCOUNT;
            case "add-card", "add-new-card" -> App.ADD_CARD;
            case "transaction-detail", "tx-detail" -> App.TRANSACTION_DETAIL;
            case "tap" -> App.TAP;
            case "calculator" -> App.CALCULATOR;
            case "paint" -> App.PAINT;
            case "contacts" -> App.CONTACTS;
            case "messenger" -> App.MESSENGER;
            case "notes" -> App.NOTES;
            case "settings" -> App.SETTINGS;
            case "journeymap" -> App.JOURNEYMAP;
            case "auction" -> App.AUCTION;
            case "realestate" -> App.REAL_ESTATE;
            default -> App.HOME;
        };
    }

    private static InputTarget parseInputTarget(String value) {
        String id = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        try {
            return InputTarget.valueOf(id);
        } catch (IllegalArgumentException ex) {
            return InputTarget.NONE;
        }
    }

    private static List<PhoneTransaction> filterHistory(List<PhoneTransaction> txs, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isBlank()) {
            return txs == null ? List.of() : txs;
        }
        List<PhoneTransaction> filtered = new ArrayList<>();
        for (PhoneTransaction tx : txs == null ? List.<PhoneTransaction>of() : txs) {
            String haystack = ((tx.description() == null ? "" : tx.description()) + " "
                    + (tx.amount() == null ? "" : tx.amount()) + " "
                    + (tx.time() == null ? "" : tx.time()) + " "
                    + (tx.counterparty() == null ? "" : tx.counterparty())).toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                filtered.add(tx);
            }
        }
        return filtered;
    }

    private static UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean parseBool(String raw) {
        return Boolean.parseBoolean(raw);
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static UUID fallbackMessageId(List<String> parts) {
        return UUID.nameUUIDFromBytes(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
    }

    private static String shortUuid(UUID id) {
        if (id == null) {
            return "-";
        }
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private static void releaseCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || cursorReleased) {
            return;
        }
        mc.mouseHandler.releaseMouse();
        cursorReleased = true;
    }

    private static void grabCursor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !cursorReleased || mc.screen != null) {
            return;
        }
        mc.mouseHandler.grabMouse();
        cursorReleased = false;
    }
}
