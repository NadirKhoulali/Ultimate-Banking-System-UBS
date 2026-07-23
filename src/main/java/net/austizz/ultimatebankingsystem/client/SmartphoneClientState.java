package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.SmartphoneActionPayload;
import net.austizz.ultimatebankingsystem.network.SmartphoneLiveRefreshPayload;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SmartphoneClientState {
    private static final int MAX_PHONE_NOTES = 12;

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
        SPOT_MARKET,
        CALCULATOR,
        PAINT,
        CONTACTS,
        MESSENGER,
        NOTES,
        SETTINGS
    }

    public enum InputTarget {
        NONE,
        CALCULATOR,
        TRANSFER_TO,
        TRANSFER_AMOUNT,
        HISTORY_SEARCH,
        CONTACT_SEARCH,
        NOTE_SEARCH,
        SETTINGS_SEARCH,
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
        MESSENGER_REQUEST_AMOUNT,
        NOTE,
        PHONE_PASSCODE,
        PHONE_PASSCODE_CURRENT,
        PHONE_PASSCODE_NEW,
        PHONE_PASSCODE_CONFIRM
    }

    public enum MessageKind {
        TEXT,
        PAY_REQUEST,
        GIFT
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
                               long createdAt,
                               MessageKind kind,
                               UUID payRequestId,
                               String payRequestAmount,
                               String payRequestStatus,
                               UUID payRequestRequesterId,
                               UUID payRequestPayerId) {
        public PhoneMessage(UUID otherId,
                            UUID messageId,
                            UUID senderId,
                            String senderName,
                            String body,
                            String time,
                            long createdAt) {
            this(otherId, messageId, senderId, senderName, body, time, createdAt,
                    MessageKind.TEXT, null, "", "", null, null);
        }

        public boolean payRequest() {
            return kind == MessageKind.PAY_REQUEST && payRequestId != null;
        }

        public boolean gift() {
            return kind == MessageKind.GIFT && payRequestId != null;
        }

        public boolean moneyAction() {
            return payRequest() || gift();
        }
    }

    public record TypingIndicator(UUID contactId, String contactName, long expiresAtMillis) {
    }

    public record PhoneNote(int index, String body) {
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

    public record MarketQuote(String id,
                              String displayName,
                              String unitName,
                              String spotLabel,
                              String bidLabel,
                              String askLabel,
                              String changeLabel,
                              String highLabel,
                              String lowLabel,
                              String source,
                              long updatedAtMillis,
                              boolean seeded,
                              String formula,
                              String confidenceLabel) {
    }

    private static final List<Hitbox> HITBOXES = new ArrayList<>();
    private static Mode mode = Mode.CLOSED;
    private static App activeApp = App.HOME;
    private static float animation = 0.0F;
    private static long animationLastFrameMillis;
    private static final float OPEN_ANIMATION_DURATION_MS = 520.0F;
    private static final float CLOSE_ANIMATION_DURATION_MS = 280.0F;
    private static boolean cursorReleased;
    private static String statusMessage = "";
    private static String statusNotificationSource = "";
    private static String statusNotificationTitle = "UBS Phone";
    private static String statusNotificationMessage = "";
    private static long statusNotificationStartedMillis;
    private static long manualStatusNotificationUntilMillis;
    private static String ownerName = "Phone";
    private static String accent = "cyan";
    private static String wallpaper = "aurora";
    private static String phoneAccessMode = "OWNER_LOCKED";
    private static long serverClockEpochMillis = System.currentTimeMillis();
    private static long serverClockReceivedAtMillis = System.currentTimeMillis();
    private static String serverClockZone = "";
    private static boolean phonePasscodeSet;
    private static boolean phoneUnlocked;
    private static boolean lockPanelOpen;
    private static float lockPanelAnimation;
    private static String phonePasscodeDraft = "";
    private static String phonePasscodeFirst = "";
    private static String phonePasscodeError = "";
    private static boolean settingsPasscodeOpen;
    private static String settingsPasscodeCurrent = "";
    private static String settingsPasscodeNew = "";
    private static String settingsPasscodeConfirm = "";
    private static UUID selectedAccount;
    private static UUID selectedContact;
    private static int noteIndex;
    private static int paintIndex;
    private static char paintColor = '1';
    private static String calculatorInput = "";
    private static String calculatorResult = "0";
    private static boolean calculatorResultCurrent;
    private static double calculatorAccumulator;
    private static String calculatorPendingOperator = "";
    private static boolean calculatorAwaitingOperand;
    private static int homeGridScrollOffset;
    private static int homeGridScrollMax;
    private static String transferTo = "";
    private static String transferAmount = "";
    private static String historySearch = "";
    private static String contactSearch = "";
    private static String noteSearch = "";
    private static int contactListScrollOffset;
    private static int contactListScrollMax;
    private static int contactDetailScrollOffset;
    private static int contactDetailScrollMax;
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
    private static String bankTheme = "dark";
    private static String profileNameDraft = "";
    private static String messageDraft = "";
    private static int messageDraftScrollOffset;
    private static int messageDraftScrollMax;
    private static boolean messageDraftStickToBottom = true;
    private static int noteDraftScrollOffset;
    private static int noteDraftScrollMax;
    private static int noteListScrollOffset;
    private static int noteListScrollMax;
    private static boolean noteDraftStickToBottom = true;
    private static boolean noteEditorOpen;
    private static int messageThreadScrollOffset;
    private static int messageThreadScrollMax;
    private static boolean messageThreadStickToBottom = true;
    private static long lastMessageSubmitMillis;
    private static String lastMessageSubmitToken = "";
    private static UUID localTypingContact;
    private static boolean localTypingActive;
    private static long lastTypingSentMillis;
    private static final long MESSAGE_TYPING_REFRESH_INTERVAL_MS = 12_000L;
    private static boolean messengerTrayOpen;
    private static boolean messengerPayRequestPanel;
    private static boolean messengerGiftPanel;
    private static boolean messengerAccountPickerOpen;
    private static UUID messengerRequestAccount;
    private static String messengerRequestAmount = "";
    private static int messengerAccountPickerOffset;
    private static int messengerAccountPickerMax;
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
    private static int marketScrollOffset;
    private static int marketScrollMax;
    private static int paymentContactOffset;
    private static String statsMonthKey = "";
    private static UUID pendingBankingAuthAccount;
    private static App pendingBankingAuthTarget = App.BANKING;
    private static boolean paymentTargetModalOpen;
    private static boolean paymentTargetModalRequest;
    private static boolean contactsMessengerMode;
    private static boolean contactDetailOpen;
    private static boolean settingsAppearanceOpen;
    private static boolean settingsInstalledAppsOpen;
    private static String settingsSearch = "";
    private static int settingsMainScrollOffset;
    private static int settingsMainScrollMax;
    private static int settingsAppearanceScrollOffset;
    private static int settingsAppearanceScrollMax;
    private static int settingsInstalledAppsScrollOffset;
    private static int settingsInstalledAppsScrollMax;
    private static String settingsSelectedAppId = "";
    private static boolean snapshotLoaded;
    private static long lastSnapshotRequestMillis;
    private static final long SNAPSHOT_RETRY_INTERVAL_MS = 1200L;

    private static final List<PhoneApp> apps = new ArrayList<>();
    private static final List<PhoneAccount> accounts = new ArrayList<>();
    private static final List<PhoneContact> contacts = new ArrayList<>();
    private static final List<PaymentRecipient> paymentRecipients = new ArrayList<>();
    private static final List<BankStaffMember> bankStaffMembers = new ArrayList<>();
    private static final List<PhoneMessage> messages = new ArrayList<>();
    private static final List<PhoneMessage> pendingMessages = new ArrayList<>();
    private static final Map<UUID, TypingIndicator> typingIndicators = new HashMap<>();
    private static final List<PhoneTransaction> transactions = new ArrayList<>();
    private static final List<MarketQuote> marketQuotes = new ArrayList<>();
    private static final List<PayRequestEntry> payRequests = new ArrayList<>();
    private static final Set<UUID> unlockedBankAccounts = new HashSet<>();
    private static final List<String> notes = new ArrayList<>();
    private static final List<String> paintings = new ArrayList<>();
    private static String payRequestsPrimaryLabel = "None";
    private static int phoneDataRevision;
    private static int cachedContactsRevision = -1;
    private static String cachedContactsQuery = null;
    private static List<PhoneContact> cachedVisibleContacts = List.of();
    private static int cachedMessagesRevision = -1;
    private static UUID cachedMessagesContact;
    private static List<PhoneMessage> cachedMessagesForContact = List.of();
    private static int cachedTransactionsRevision = -1;
    private static UUID cachedTransactionsAccount;
    private static List<PhoneTransaction> cachedTransactionsForAccount = List.of();
    private static int cachedPaymentRecipientsRevision = -1;
    private static int cachedPaymentRecipientsOffset = -1;
    private static List<PaymentRecipient> cachedPaymentRecipients = List.of();
    private static int cachedSelectedTransactionRevision = -1;
    private static UUID cachedSelectedTransactionId;
    private static PhoneTransaction cachedSelectedTransaction;

    private SmartphoneClientState() {
    }

    public static void requestOpen() {
        if (mode == Mode.CLOSED || mode == Mode.CLOSING) {
            ensureDefaultApps();
            activeApp = App.HOME;
            resetUtilitySubpages();
            resetPhoneLockSession();
            snapshotLoaded = false;
            mode = Mode.OPENING;
            animation = Math.max(0.01F, animation);
            resetAnimationClock();
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
            resetAnimationClock();
        } else if (mode == Mode.CLOSED || mode == Mode.CLOSING) {
            mode = Mode.INTERACTIVE;
            animation = 1.0F;
            resetAnimationClock();
        }
        applyActionSideEffects(statusMessage);
        releaseCursor();
    }

    public static void applyLiveRefresh(SmartphoneLiveRefreshPayload payload) {
        if (payload == null || mode == Mode.CLOSED || mode == Mode.CLOSING) {
            return;
        }
        String status = payload.statusMessage() == null ? "" : payload.statusMessage();
        if (!status.isBlank()) {
            statusMessage = status;
        }
        parseLines(payload.lines());
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
            messengerRequestAmount = "";
            closeMessengerTray();
            inputTarget = activeApp == App.MESSENGER ? InputTarget.MESSAGE : InputTarget.NONE;
        } else if (status.startsWith("Gift sent")) {
            messengerRequestAmount = "";
            closeMessengerTray();
            inputTarget = activeApp == App.MESSENGER ? InputTarget.MESSAGE : InputTarget.NONE;
        } else if (status.startsWith("Message not sent")) {
            clearRecentPendingMessage();
            inputTarget = InputTarget.MESSAGE;
        } else if (status.startsWith("Phone passcode set") || status.startsWith("Phone unlocked")) {
            phonePasscodeSet = true;
            phoneUnlocked = true;
            lockPanelOpen = false;
            phonePasscodeDraft = "";
            phonePasscodeFirst = "";
            phonePasscodeError = "";
            inputTarget = InputTarget.NONE;
        } else if (status.startsWith("Incorrect phone passcode")) {
            phonePasscodeDraft = "";
            phonePasscodeError = "Incorrect passcode";
            lockPanelOpen = true;
            inputTarget = InputTarget.PHONE_PASSCODE;
        } else if (status.startsWith("Use a 4 digit phone passcode")
                || status.startsWith("Set a phone passcode first")
                || status.startsWith("Phone passcode already set")) {
            if (settingsPasscodeOpen) {
                settingsPasscodeNew = "";
                settingsPasscodeConfirm = "";
                inputTarget = InputTarget.PHONE_PASSCODE_NEW;
            } else {
                phonePasscodeDraft = "";
                phonePasscodeError = status;
                lockPanelOpen = true;
                inputTarget = InputTarget.PHONE_PASSCODE;
            }
        } else if (status.startsWith("Phone passcode changed")) {
            clearSettingsPasscodeDrafts();
            settingsPasscodeOpen = false;
            inputTarget = InputTarget.NONE;
        } else if (status.startsWith("Current phone passcode is incorrect")
                || status.startsWith("Phone passcodes do not match")) {
            settingsPasscodeCurrent = "";
            inputTarget = InputTarget.PHONE_PASSCODE_CURRENT;
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
                resetAnimationClock();
                grabCursor();
            }
            return;
        }
        updatePhoneAnimation();
        float lockTarget = lockPanelOpen && phoneLocked() ? 1.0F : 0.0F;
        if (lockPanelAnimation < lockTarget) {
            lockPanelAnimation = Math.min(lockTarget, lockPanelAnimation + 0.24F);
        } else if (lockPanelAnimation > lockTarget) {
            lockPanelAnimation = Math.max(lockTarget, lockPanelAnimation - 0.22F);
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
        updatePhoneAnimation();
        return Mth.clamp(animation, 0.0F, 1.0F);
    }

    private static void updatePhoneAnimation() {
        long now = System.currentTimeMillis();
        if (animationLastFrameMillis <= 0L) {
            animationLastFrameMillis = now;
        }
        long elapsedMillis = Math.min(80L, Math.max(0L, now - animationLastFrameMillis));
        animationLastFrameMillis = now;
        if (mode == Mode.OPENING) {
            animation = Math.min(1.0F, animation + elapsedMillis / OPEN_ANIMATION_DURATION_MS);
            if (animation >= 1.0F) {
                mode = Mode.INTERACTIVE;
            }
        } else if (mode == Mode.CLOSING) {
            animation = Math.max(0.0F, animation - elapsedMillis / CLOSE_ANIMATION_DURATION_MS);
            if (animation <= 0.0F) {
                mode = Mode.CLOSED;
            }
        }
    }

    private static void resetAnimationClock() {
        animationLastFrameMillis = System.currentTimeMillis();
    }

    public static String statusMessage() {
        return statusMessage;
    }

    public static String statusNotificationMessage() {
        updateStatusNotification();
        return statusNotificationMessage;
    }

    public static String statusNotificationTitle() {
        updateStatusNotification();
        return statusNotificationTitle == null || statusNotificationTitle.isBlank() ? "UBS Phone" : statusNotificationTitle;
    }

    public static void showPhoneNotification(String title, String message) {
        String safeMessage = message == null ? "" : message.trim();
        if (safeMessage.isBlank()) {
            return;
        }
        statusMessage = safeMessage;
        statusNotificationTitle = title == null || title.isBlank() ? "UBS Phone" : title.trim();
        statusNotificationMessage = safeMessage;
        statusNotificationSource = safeMessage;
        statusNotificationStartedMillis = System.currentTimeMillis();
        manualStatusNotificationUntilMillis = statusNotificationStartedMillis + 4200L;
    }

    public static float statusNotificationProgress() {
        updateStatusNotification();
        if (statusNotificationMessage == null || statusNotificationMessage.isBlank()) {
            return 0.0F;
        }
        long elapsed = Math.max(0L, System.currentTimeMillis() - statusNotificationStartedMillis);
        if (elapsed >= 3700L) {
            return 0.0F;
        }
        if (elapsed < 320L) {
            return easeOut(elapsed / 320.0F);
        }
        if (elapsed < 2850L) {
            return 1.0F;
        }
        return 1.0F - easeIn((elapsed - 2850L) / 850.0F);
    }

    private static void updateStatusNotification() {
        String normalized = statusMessage == null ? "" : statusMessage.trim();
        if (normalized.isBlank()) {
            statusNotificationSource = "";
            return;
        }
        if (System.currentTimeMillis() < manualStatusNotificationUntilMillis
                && normalized.equals(statusNotificationMessage)) {
            return;
        }
        if (!normalized.equals(statusNotificationSource)) {
            statusNotificationSource = normalized;
            statusNotificationTitle = "UBS Phone";
            statusNotificationMessage = normalized;
            statusNotificationStartedMillis = System.currentTimeMillis();
            manualStatusNotificationUntilMillis = 0L;
        }
    }

    private static float easeOut(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    private static float easeIn(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * t;
    }

    public static String ownerName() {
        return ownerName;
    }

    public static String phoneAccessLabel() {
        return phoneOpenAccess() ? "Open access" : "Owner locked";
    }

    public static String phoneAccessDescription() {
        return phoneOpenAccess()
                ? "Anyone holding this phone can use it."
                : "Only the bound owner can use this phone.";
    }

    private static boolean phoneOpenAccess() {
        return "OPEN_ACCESS".equalsIgnoreCase(phoneAccessMode);
    }

    public static long localClockMillis() {
        return System.currentTimeMillis();
    }

    public static long serverClockMillis() {
        long elapsed = Math.max(0L, System.currentTimeMillis() - serverClockReceivedAtMillis);
        return serverClockEpochMillis + elapsed;
    }

    public static String serverClockZone() {
        return serverClockZone == null ? "" : serverClockZone;
    }

    public static boolean phoneLocked() {
        return !phoneUnlocked;
    }

    public static boolean phonePasscodeSet() {
        return phonePasscodeSet;
    }

    public static boolean lockPanelOpen() {
        return lockPanelOpen;
    }

    public static float lockPanelAnimation() {
        return Mth.clamp(lockPanelAnimation, 0.0F, 1.0F);
    }

    public static String phonePasscodeDraft() {
        return phonePasscodeDraft == null ? "" : phonePasscodeDraft;
    }

    public static String phonePasscodeError() {
        return phonePasscodeError == null ? "" : phonePasscodeError;
    }

    public static boolean phonePasscodeConfirmStep() {
        return !phonePasscodeSet && phonePasscodeFirst != null && !phonePasscodeFirst.isBlank();
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

    public static String accentName() {
        return accent == null || accent.isBlank() ? "cyan" : accent;
    }

    public static String wallpaperName() {
        return wallpaper == null || wallpaper.isBlank() ? "aurora" : wallpaper;
    }

    public static int wallpaperTop() {
        return switch (wallpaper.toLowerCase(Locale.ROOT)) {
            case "dusk" -> 0xFF442052;
            case "forest" -> 0xFF07381F;
            case "mono" -> 0xFF111318;
            default -> 0xFF122A5A;
        };
    }

    public static int wallpaperBottom() {
        return switch (wallpaper.toLowerCase(Locale.ROOT)) {
            case "dusk" -> 0xFFB85C38;
            case "forest" -> 0xFF0D7355;
            case "mono" -> 0xFF252B38;
            default -> 0xFF0B8A8F;
        };
    }

    public static List<PhoneApp> apps() {
        return apps;
    }

    public static List<PhoneAccount> accounts() {
        return accounts;
    }

    public static List<MarketQuote> marketQuotes() {
        return marketQuotes;
    }

    public static int marketScrollOffset() {
        return marketScrollOffset;
    }

    public static void syncMarketScroll(int totalRows, int visibleRows) {
        marketScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        marketScrollOffset = Mth.clamp(marketScrollOffset, 0, marketScrollMax);
    }

    public static List<PhoneContact> contacts() {
        return contacts;
    }

    public static List<PhoneContact> visibleContacts() {
        String query = contactSearch == null ? "" : contactSearch.trim().toLowerCase(Locale.ROOT);
        if (cachedContactsRevision == phoneDataRevision && query.equals(cachedContactsQuery)) {
            return cachedVisibleContacts;
        }
        cachedContactsQuery = query;
        cachedContactsRevision = phoneDataRevision;
        cachedVisibleContacts = contacts.stream()
                .filter(contact -> query.isBlank() || contactMatchesQuery(contact, query))
                .sorted(Comparator
                        .comparing((PhoneContact contact) -> safeContactSortName(contact.name()))
                        .thenComparing(contact -> contact.id() == null ? "" : contact.id().toString()))
                .toList();
        return cachedVisibleContacts;
    }

    private static boolean contactMatchesQuery(PhoneContact contact, String query) {
        if (contact == null) {
            return false;
        }
        String name = contact.name() == null ? "" : contact.name().toLowerCase(Locale.ROOT);
        String id = contact.id() == null ? "" : contact.id().toString().toLowerCase(Locale.ROOT);
        String compactId = id.replace("-", "");
        String compactQuery = query.replace("-", "");
        String status = contact.online() ? "online" : "offline";
        String flags = (contact.favorite() ? " favorite" : "")
                + (contact.muted() ? " muted" : "")
                + (contact.blocked() ? " blocked" : "")
                + (contact.unread() > 0 ? " unread" : "");
        return name.contains(query)
                || id.contains(query)
                || compactId.contains(compactQuery)
                || status.contains(query)
                || flags.contains(query);
    }

    private static String safeContactSortName(String name) {
        String safe = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return safe.isBlank() ? "~" : safe;
    }

    public static String contactSearch() {
        return contactSearch == null ? "" : contactSearch;
    }

    public static int contactListScrollOffset() {
        return contactListScrollOffset;
    }

    public static void syncContactListScroll(int totalRows, int visibleRows) {
        contactListScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        contactListScrollOffset = Mth.clamp(contactListScrollOffset, 0, contactListScrollMax);
    }

    public static void jumpToContactSection(String sectionRaw) {
        String section = sectionRaw == null || sectionRaw.isBlank()
                ? "#"
                : sectionRaw.trim().substring(0, 1).toUpperCase(Locale.ROOT);
        List<PhoneContact> visible = visibleContacts();
        for (int i = 0; i < visible.size(); i++) {
            if (section.equals(contactSectionKey(visible.get(i)))) {
                contactListScrollOffset = Mth.clamp(i, 0, contactListScrollMax);
                return;
            }
        }
    }

    private static String contactSectionKey(PhoneContact contact) {
        String name = contact == null || contact.name() == null ? "" : contact.name().trim();
        if (name.isBlank()) {
            return "#";
        }
        char first = Character.toUpperCase(name.charAt(0));
        return Character.isLetterOrDigit(first) ? String.valueOf(first) : "#";
    }

    public static int homeGridScrollOffset() {
        return homeGridScrollOffset;
    }

    public static void syncHomeGridScroll(int totalRows, int visibleRows) {
        homeGridScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        homeGridScrollOffset = Mth.clamp(homeGridScrollOffset, 0, homeGridScrollMax);
    }

    public static void setHomeGridPage(int page) {
        homeGridScrollOffset = Mth.clamp(page, 0, homeGridScrollMax);
    }

    public static boolean contactsMessengerMode() {
        return contactsMessengerMode;
    }

    public static boolean contactDetailOpen() {
        return contactDetailOpen;
    }

    public static int contactDetailScrollOffset() {
        return contactDetailScrollOffset;
    }

    public static void syncContactDetailScroll(int totalRows, int visibleRows) {
        contactDetailScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        contactDetailScrollOffset = Mth.clamp(contactDetailScrollOffset, 0, contactDetailScrollMax);
    }

    public static List<PaymentRecipient> paymentRecipients() {
        if (paymentContactOffset <= 0) {
            return paymentRecipients;
        }
        if (cachedPaymentRecipientsRevision == phoneDataRevision
                && cachedPaymentRecipientsOffset == paymentContactOffset) {
            return cachedPaymentRecipients;
        }
        cachedPaymentRecipientsRevision = phoneDataRevision;
        cachedPaymentRecipientsOffset = paymentContactOffset;
        cachedPaymentRecipients = paymentRecipients.stream().skip(paymentContactOffset).toList();
        return cachedPaymentRecipients;
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
        if (cachedMessagesRevision == phoneDataRevision && selectedContact.equals(cachedMessagesContact)) {
            return cachedMessagesForContact;
        }
        cachedMessagesRevision = phoneDataRevision;
        cachedMessagesContact = selectedContact;
        cachedMessagesForContact = messages.stream()
                .filter(message -> selectedContact.equals(message.otherId()))
                .sorted(Comparator
                        .comparingLong(PhoneMessage::createdAt)
                        .thenComparing(message -> message.messageId().toString()))
                .toList();
        return cachedMessagesForContact;
    }

    public static boolean selectedContactTyping() {
        return selectedTypingIndicator() != null;
    }

    public static TypingIndicator selectedTypingIndicator() {
        if (selectedContact == null) {
            return null;
        }
        TypingIndicator indicator = typingIndicators.get(selectedContact);
        long now = System.currentTimeMillis();
        if (indicator == null || indicator.expiresAtMillis() <= now) {
            if (indicator != null) {
                typingIndicators.remove(selectedContact);
            }
            return null;
        }
        return indicator;
    }

    public static List<PhoneTransaction> transactionsForSelectedAccount() {
        if (selectedAccount == null) {
            return List.of();
        }
        if (cachedTransactionsRevision == phoneDataRevision && selectedAccount.equals(cachedTransactionsAccount)) {
            return cachedTransactionsForAccount;
        }
        cachedTransactionsRevision = phoneDataRevision;
        cachedTransactionsAccount = selectedAccount;
        cachedTransactionsForAccount = transactions.stream()
                .filter(tx -> selectedAccount.equals(tx.accountId()))
                .toList();
        return cachedTransactionsForAccount;
    }

    public static PhoneTransaction selectedTransaction() {
        UUID id = selectedTransaction;
        if (id == null) {
            return null;
        }
        if (cachedSelectedTransactionRevision == phoneDataRevision && id.equals(cachedSelectedTransactionId)) {
            return cachedSelectedTransaction;
        }
        cachedSelectedTransactionRevision = phoneDataRevision;
        cachedSelectedTransactionId = id;
        cachedSelectedTransaction = transactions.stream().filter(tx -> tx.txId().equals(id)).findFirst().orElse(null);
        return cachedSelectedTransaction;
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
        return accountById(id);
    }

    private static PhoneAccount accountById(UUID id) {
        if (id == null) {
            return null;
        }
        return accounts.stream().filter(account -> account.id().equals(id)).findFirst().orElse(null);
    }

    public static PhoneContact selectedContact() {
        return selectedContact == null
                ? null
                : contacts.stream().filter(contact -> contact.id().equals(selectedContact)).findFirst().orElse(null);
    }

    public static List<String> notes() {
        return notes;
    }

    public static int maxPhoneNotes() {
        return MAX_PHONE_NOTES;
    }

    public static List<PhoneNote> visibleNotes() {
        String query = noteSearch == null ? "" : noteSearch.trim().toLowerCase(Locale.ROOT);
        List<PhoneNote> out = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            String note = notes.get(i);
            if (note == null || note.isBlank()) {
                continue;
            }
            if (query.isBlank() || note.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(new PhoneNote(i, note));
            }
        }
        return out;
    }

    public static String noteSearch() {
        return noteSearch == null ? "" : noteSearch;
    }

    public static int noteIndex() {
        return noteIndex;
    }

    public static String noteDraft() {
        return noteDraft;
    }

    public static boolean noteEditorOpen() {
        return noteEditorOpen;
    }

    public static boolean selectedNoteHasContent() {
        return !noteSlotBlank(noteIndex);
    }

    public static boolean settingsAppearanceOpen() {
        return settingsAppearanceOpen;
    }

    public static boolean settingsInstalledAppsOpen() {
        return settingsInstalledAppsOpen;
    }

    public static boolean settingsPasscodeOpen() {
        return settingsPasscodeOpen;
    }

    public static String settingsPasscodeCurrent() {
        return settingsPasscodeCurrent == null ? "" : settingsPasscodeCurrent;
    }

    public static String settingsPasscodeNew() {
        return settingsPasscodeNew == null ? "" : settingsPasscodeNew;
    }

    public static String settingsPasscodeConfirm() {
        return settingsPasscodeConfirm == null ? "" : settingsPasscodeConfirm;
    }

    public static String settingsSearch() {
        return settingsSearch == null ? "" : settingsSearch;
    }

    public static int settingsMainScrollOffset() {
        return settingsMainScrollOffset;
    }

    public static void syncSettingsMainScroll(int totalRows, int visibleRows) {
        settingsMainScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        settingsMainScrollOffset = Mth.clamp(settingsMainScrollOffset, 0, settingsMainScrollMax);
    }

    public static int settingsAppearanceScrollOffset() {
        return settingsAppearanceScrollOffset;
    }

    public static void syncSettingsAppearanceScroll(int totalRows, int visibleRows) {
        settingsAppearanceScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        settingsAppearanceScrollOffset = Mth.clamp(settingsAppearanceScrollOffset, 0, settingsAppearanceScrollMax);
    }

    public static int settingsInstalledAppsScrollOffset() {
        return settingsInstalledAppsScrollOffset;
    }

    public static PhoneApp settingsSelectedApp() {
        if (settingsSelectedAppId == null || settingsSelectedAppId.isBlank()) {
            return null;
        }
        return apps.stream()
                .filter(app -> app.id() != null && app.id().equals(settingsSelectedAppId))
                .findFirst()
                .orElse(null);
    }

    public static void syncSettingsInstalledAppsScroll(int totalRows, int visibleRows) {
        settingsInstalledAppsScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        settingsInstalledAppsScrollOffset = Mth.clamp(settingsInstalledAppsScrollOffset, 0, settingsInstalledAppsScrollMax);
    }

    public static int noteDraftScrollOffset() {
        return noteDraftScrollOffset;
    }

    public static void syncNoteDraftScroll(int lineCount, int visibleLines) {
        int max = Math.max(0, lineCount - Math.max(1, visibleLines));
        noteDraftScrollMax = max;
        if (noteDraftStickToBottom) {
            noteDraftScrollOffset = max;
            return;
        }
        noteDraftScrollOffset = Mth.clamp(noteDraftScrollOffset, 0, max);
        if (noteDraftScrollOffset >= max) {
            noteDraftStickToBottom = true;
        }
    }

    public static int noteListScrollOffset() {
        return noteListScrollOffset;
    }

    public static void syncNoteListScroll(int totalRows, int visibleRows) {
        noteListScrollMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        noteListScrollOffset = Mth.clamp(noteListScrollOffset, 0, noteListScrollMax);
    }

    public static List<String> paintings() {
        return paintings;
    }

    public static int paintIndex() {
        return paintIndex;
    }

    public static char paintColor() {
        return paintColor;
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

    public static boolean calculatorResultCurrent() {
        return calculatorResultCurrent;
    }

    public static String calculatorPendingOperator() {
        return calculatorPendingOperator == null ? "" : calculatorPendingOperator;
    }

    public static boolean calculatorAwaitingOperand() {
        return calculatorAwaitingOperand;
    }

    public static String calculatorExpressionPreview() {
        String operator = calculatorPendingOperator();
        if (operator.isBlank()) {
            return "";
        }
        String left = formatCalculatorValue(calculatorAccumulator);
        String symbol = switch (operator) {
            case "/" -> "\u00F7";
            case "*" -> "\u00D7";
            default -> operator;
        };
        if (calculatorAwaitingOperand) {
            return left + " " + symbol;
        }
        return left + " " + symbol + " " + currentCalculatorDisplay()
                .replace("/", "\u00F7")
                .replace("*", "\u00D7");
    }

    public static boolean calculatorAtResetState() {
        return calculatorInput.isBlank()
                && "0".equals(calculatorResult)
                && !calculatorResultCurrent
                && calculatorPendingOperator.isBlank()
                && !calculatorAwaitingOperand;
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

    public static boolean bankLightMode() {
        return "light".equalsIgnoreCase(bankTheme);
    }

    public static String bankTheme() {
        return bankLightMode() ? "light" : "dark";
    }

    public static String profileNameDraft() {
        return profileNameDraft == null || profileNameDraft.isBlank() ? ownerName : profileNameDraft;
    }

    public static String messageDraft() {
        return messageDraft;
    }

    public static boolean messengerTrayOpen() {
        return messengerTrayOpen;
    }

    public static boolean messengerPayRequestPanel() {
        return messengerPayRequestPanel;
    }

    public static boolean messengerGiftPanel() {
        return messengerGiftPanel;
    }

    public static boolean messengerAccountPickerOpen() {
        return messengerAccountPickerOpen;
    }

    public static String messengerRequestAmount() {
        return messengerRequestAmount;
    }

    public static PhoneAccount messengerRequestAccount() {
        PhoneAccount account = accountById(messengerRequestAccount);
        if (account != null) {
            return account;
        }
        PhoneAccount primary = primaryAccount();
        if (primary != null) {
            messengerRequestAccount = primary.id();
            return primary;
        }
        return null;
    }

    public static String messengerRequestAccountLabel() {
        PhoneAccount account = messengerRequestAccount();
        if (account == null) {
            return "Choose account";
        }
        return account.bankName() + " " + account.accountType();
    }

    public static int messengerAccountPickerOffset() {
        return messengerAccountPickerOffset;
    }

    public static void syncMessengerAccountPickerScroll(int totalRows, int visibleRows) {
        messengerAccountPickerMax = Math.max(0, totalRows - Math.max(1, visibleRows));
        messengerAccountPickerOffset = Mth.clamp(messengerAccountPickerOffset, 0, messengerAccountPickerMax);
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
        if (phoneLocked()) {
            openLockPanel();
            return true;
        }
        if (activeApp == App.HOME && homeGridScrollMax > 0) {
            homeGridScrollOffset = Mth.clamp(homeGridScrollOffset + (deltaY < 0 ? 1 : -1), 0, homeGridScrollMax);
            return true;
        }
        if (((activeApp == App.CONTACTS && (!contactDetailOpen || selectedContact() == null))
                || (activeApp == App.MESSENGER && selectedContact() == null))
                && contactListScrollMax > 0) {
            contactListScrollOffset = Mth.clamp(contactListScrollOffset + (deltaY < 0 ? 1 : -1), 0, contactListScrollMax);
            return true;
        }
        if (activeApp == App.CONTACTS && contactDetailOpen && contactDetailScrollMax > 0) {
            contactDetailScrollOffset = Mth.clamp(contactDetailScrollOffset + (deltaY < 0 ? 1 : -1),
                    0, contactDetailScrollMax);
            return true;
        }
        if (activeApp == App.SPOT_MARKET && marketScrollMax > 0) {
            marketScrollOffset = Mth.clamp(marketScrollOffset + (deltaY < 0 ? 1 : -1), 0, marketScrollMax);
            return true;
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
        if (activeApp == App.MESSENGER && messengerAccountPickerOpen && messengerAccountPickerMax > 0) {
            messengerAccountPickerOffset = Mth.clamp(messengerAccountPickerOffset + (deltaY < 0 ? 1 : -1),
                    0, messengerAccountPickerMax);
            return true;
        }
        if (activeApp == App.MESSENGER && inputTarget == InputTarget.MESSAGE && messageDraftScrollMax > 0) {
            messageDraftScrollOffset = Mth.clamp(messageDraftScrollOffset + (deltaY < 0 ? 1 : -1), 0, messageDraftScrollMax);
            messageDraftStickToBottom = messageDraftScrollOffset >= messageDraftScrollMax;
            return true;
        }
        if (activeApp == App.NOTES && inputTarget == InputTarget.NOTE && noteDraftScrollMax > 0) {
            noteDraftScrollOffset = Mth.clamp(noteDraftScrollOffset + (deltaY < 0 ? 1 : -1), 0, noteDraftScrollMax);
            noteDraftStickToBottom = noteDraftScrollOffset >= noteDraftScrollMax;
            return true;
        }
        if (activeApp == App.NOTES && !noteEditorOpen && noteListScrollMax > 0) {
            noteListScrollOffset = Mth.clamp(noteListScrollOffset + (deltaY < 0 ? 1 : -1), 0, noteListScrollMax);
            return true;
        }
        if (activeApp == App.SETTINGS && settingsInstalledAppsOpen && settingsSelectedAppId.isBlank()
                && settingsInstalledAppsScrollMax > 0) {
            settingsInstalledAppsScrollOffset = Mth.clamp(settingsInstalledAppsScrollOffset + (deltaY < 0 ? 1 : -1),
                    0, settingsInstalledAppsScrollMax);
            return true;
        }
        if (activeApp == App.SETTINGS && settingsAppearanceOpen && settingsAppearanceScrollMax > 0) {
            settingsAppearanceScrollOffset = Mth.clamp(settingsAppearanceScrollOffset + (deltaY < 0 ? 1 : -1),
                    0, settingsAppearanceScrollMax);
            return true;
        }
        if (activeApp == App.SETTINGS && !settingsAppearanceOpen && !settingsInstalledAppsOpen && !settingsPasscodeOpen
                && settingsMainScrollMax > 0) {
            settingsMainScrollOffset = Mth.clamp(settingsMainScrollOffset + (deltaY < 0 ? 1 : -1),
                    0, settingsMainScrollMax);
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
        if (phoneLocked()) {
            if (key == GLFW.GLFW_KEY_ESCAPE && press) {
                if (lockPanelOpen) {
                    closeLockPanel();
                } else {
                    close();
                }
                return true;
            }
            if (!lockPanelOpen) {
                openLockPanel();
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                mutateInput(true, '\0');
                return true;
            }
            if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && press) {
                submitPhonePasscode();
                return true;
            }
            char typed = keyToChar(key);
            if (typed != 0) {
                mutateInput(false, typed);
                return true;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!press) {
                return true;
            }
            if (paymentTargetModalOpen) {
                paymentTargetModalOpen = false;
                inputTarget = InputTarget.NONE;
                return true;
            }
            if (inputTarget == InputTarget.CONTACT_SEARCH) {
                handleUiAction("CONTACT_SEARCH_CANCEL", "", "", "");
                return true;
            }
            if (inputTarget == InputTarget.NOTE_SEARCH) {
                handleUiAction("NOTE_SEARCH_CANCEL", "", "", "");
                return true;
            }
            if (inputTarget == InputTarget.SETTINGS_SEARCH) {
                handleUiAction("SETTINGS_SEARCH_CANCEL", "", "", "");
                return true;
            }
            if (activeApp == App.MESSENGER && messengerAccountPickerOpen) {
                messengerAccountPickerOpen = false;
                inputTarget = InputTarget.MESSENGER_REQUEST_AMOUNT;
                return true;
            }
            if (activeApp == App.MESSENGER && messengerTrayOpen) {
                closeMessengerTray();
                inputTarget = InputTarget.MESSAGE;
                return true;
            }
            navigateBack();
            return true;
        }
        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            mutateInput(true, '\0');
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (press) {
                if (inputTarget == InputTarget.NOTE) {
                    mutateInput(false, '\n');
                } else {
                    submitFocusedInput();
                }
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

    private static void navigateBack() {
        inputTarget = InputTarget.NONE;
        paymentTargetModalOpen = false;
        clearPendingBankingAuth();
        if (activeApp == App.HOME) {
            close();
            return;
        }
        if (activeApp == App.MESSENGER) {
            if (selectedContact != null) {
                if (messengerTrayOpen || messengerPayRequestPanel || messengerGiftPanel || messengerAccountPickerOpen) {
                    closeMessengerTray();
                    inputTarget = InputTarget.MESSAGE;
                    return;
                }
                selectedContact = null;
                resetMessageDraft();
                resetMessageThreadScroll();
                closeMessengerTray();
                contactsMessengerMode = true;
                return;
            }
            contactsMessengerMode = false;
            activeApp = App.HOME;
            return;
        }
        if (activeApp == App.CONTACTS) {
            if (contactDetailOpen) {
                contactDetailOpen = false;
                contactDetailScrollOffset = 0;
                return;
            }
            activeApp = App.HOME;
            return;
        }
        if (activeApp == App.NOTES) {
            if (noteEditorOpen) {
                closeNoteEditorSavingDraft(false);
                return;
            }
            activeApp = App.HOME;
            return;
        }
        if (activeApp == App.SETTINGS) {
            if (settingsAppearanceOpen || settingsInstalledAppsOpen || settingsPasscodeOpen || !settingsSelectedAppId.isBlank()) {
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = false;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = "";
                settingsAppearanceScrollOffset = 0;
                settingsInstalledAppsScrollOffset = 0;
                clearSettingsPasscodeDrafts();
                return;
            }
            activeApp = App.HOME;
            return;
        }
        if (activeApp == App.BANKING || activeApp == App.BANK_WELCOME) {
            activeApp = App.HOME;
            return;
        }
        if (requiresBankAccountAccess(activeApp)
                || activeApp == App.SEARCH
                || activeApp == App.BANK_STAFF
                || activeApp == App.BANK_DISSOLVE
                || activeApp == App.BANK_PROFILE
                || activeApp == App.BANK_EDIT_PROFILE
                || activeApp == App.BANK_SIGN_IN
                || activeApp == App.BANK_LANGUAGE
                || activeApp == App.BANK_CHANGE_PASSWORD
                || activeApp == App.BANK_TERMS) {
            activeApp = App.BANKING;
            return;
        }
        activeApp = App.HOME;
    }

    public static void close() {
        if (mode == Mode.CLOSED) {
            return;
        }
        sendMessengerTypingInactive();
        mode = Mode.CLOSING;
        inputTarget = InputTarget.NONE;
        paymentTargetModalOpen = false;
        snapshotLoaded = false;
        unlockedBankAccounts.clear();
        pendingBankingAuthAccount = null;
        pendingBankingAuthTarget = App.BANKING;
        bankingLoadingBankPick = false;
        bankPin = "";
        closeMessengerTray();
        resetUtilitySubpages();
        resetPhoneLockSession();
        grabCursor();
    }

    private static void resetUtilitySubpages() {
        contactDetailOpen = false;
        contactsMessengerMode = false;
        contactListScrollOffset = 0;
        contactDetailScrollOffset = 0;
        contactSearch = "";
        noteEditorOpen = false;
        noteListScrollOffset = 0;
        noteSearch = "";
        resetNoteDraftScroll();
        settingsAppearanceOpen = false;
        settingsInstalledAppsOpen = false;
        settingsPasscodeOpen = false;
        settingsSearch = "";
        settingsMainScrollOffset = 0;
        settingsAppearanceScrollOffset = 0;
        settingsInstalledAppsScrollOffset = 0;
        settingsSelectedAppId = "";
        clearSettingsPasscodeDrafts();
        homeGridScrollOffset = 0;
    }

    private static void clearSettingsPasscodeDrafts() {
        settingsPasscodeCurrent = "";
        settingsPasscodeNew = "";
        settingsPasscodeConfirm = "";
    }

    private static void resetPhoneLockSession() {
        phoneUnlocked = false;
        lockPanelOpen = false;
        lockPanelAnimation = 0.0F;
        phonePasscodeDraft = "";
        phonePasscodeFirst = "";
        phonePasscodeError = "";
    }

    private static void openLockPanel() {
        if (!phoneLocked()) {
            return;
        }
        lockPanelOpen = true;
        inputTarget = InputTarget.PHONE_PASSCODE;
        if (phonePasscodeError == null) {
            phonePasscodeError = "";
        }
    }

    private static void closeLockPanel() {
        lockPanelOpen = false;
        inputTarget = InputTarget.NONE;
        phonePasscodeDraft = "";
        phonePasscodeFirst = "";
        phonePasscodeError = "";
    }

    private static void appendPhonePasscodeDigit(String raw) {
        if (raw == null || raw.length() != 1 || !Character.isDigit(raw.charAt(0))) {
            return;
        }
        openLockPanel();
        if (phonePasscodeDraft.length() >= 4) {
            return;
        }
        phonePasscodeDraft += raw;
        phonePasscodeError = "";
        if (phonePasscodeDraft.length() >= 4) {
            submitPhonePasscode();
        }
    }

    private static void deletePhonePasscodeDigit() {
        if (!phonePasscodeDraft.isEmpty()) {
            phonePasscodeDraft = phonePasscodeDraft.substring(0, phonePasscodeDraft.length() - 1);
        }
        phonePasscodeError = "";
    }

    private static void submitPhonePasscode() {
        openLockPanel();
        String pin = phonePasscodeDraft == null ? "" : phonePasscodeDraft.trim();
        if (!pin.matches("\\d{4}")) {
            phonePasscodeError = "Enter 4 digits";
            return;
        }
        if (!phonePasscodeSet) {
            if (phonePasscodeFirst == null || phonePasscodeFirst.isBlank()) {
                phonePasscodeFirst = pin;
                phonePasscodeDraft = "";
                phonePasscodeError = "Re-enter passcode";
                return;
            }
            if (!phonePasscodeFirst.equals(pin)) {
                phonePasscodeFirst = "";
                phonePasscodeDraft = "";
                phonePasscodeError = "Passcodes did not match";
                return;
            }
            sendAction("SET_PHONE_PASSCODE", pin, "", "");
            statusMessage = "Saving phone passcode...";
            return;
        }
        sendAction("VERIFY_PHONE_PASSCODE", pin, "", "");
        statusMessage = "Unlocking phone...";
    }

    private static void savePhonePasscodeChange() {
        String current = settingsPasscodeCurrent == null ? "" : settingsPasscodeCurrent.trim();
        String next = settingsPasscodeNew == null ? "" : settingsPasscodeNew.trim();
        String confirm = settingsPasscodeConfirm == null ? "" : settingsPasscodeConfirm.trim();
        if (!current.matches("\\d{4}")) {
            statusMessage = "Enter your current phone passcode.";
            inputTarget = InputTarget.PHONE_PASSCODE_CURRENT;
            return;
        }
        if (!next.matches("\\d{4}") || !confirm.matches("\\d{4}")) {
            statusMessage = "Use a 4 digit phone passcode.";
            inputTarget = InputTarget.PHONE_PASSCODE_NEW;
            return;
        }
        if (!next.equals(confirm)) {
            settingsPasscodeNew = "";
            settingsPasscodeConfirm = "";
            statusMessage = "Phone passcodes do not match.";
            inputTarget = InputTarget.PHONE_PASSCODE_NEW;
            return;
        }
        sendAction("CHANGE_PHONE_PASSCODE", current, next, confirm);
        statusMessage = "Changing phone passcode...";
    }

    private static void handleUiAction(String action, String p1, String p2, String p3) {
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        switch (normalized) {
            case "LOCK_SWIPE" -> openLockPanel();
            case "LOCK_CANCEL" -> closeLockPanel();
            case "PHONE_PASSCODE_DIGIT" -> appendPhonePasscodeDigit(p1);
            case "PHONE_PASSCODE_DELETE" -> deletePhonePasscodeDigit();
            case "PHONE_PASSCODE_SUBMIT" -> submitPhonePasscode();
            case "HOME" -> {
                activeApp = App.HOME;
                inputTarget = InputTarget.NONE;
                closeMessengerTray();
                contactsMessengerMode = false;
                contactDetailOpen = false;
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = false;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = "";
                clearSettingsPasscodeDrafts();
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
                    sendMessengerTypingInactive();
                    contactsMessengerMode = true;
                    contactDetailOpen = false;
                    contactListScrollOffset = 0;
                    contactSearch = "";
                    selectedContact = null;
                    activeApp = App.MESSENGER;
                    inputTarget = InputTarget.NONE;
                    return;
                }
                if (activeApp == App.MESSENGER && requestedApp != App.MESSENGER) {
                    sendMessengerTypingInactive();
                }
                closeMessengerTray();
                activeApp = requestedApp;
                inputTarget = InputTarget.NONE;
                if (activeApp == App.CONTACTS) {
                    contactsMessengerMode = false;
                    contactDetailOpen = false;
                    contactListScrollOffset = 0;
                    contactSearch = "";
                } else if (activeApp == App.CALCULATOR) {
                    inputTarget = InputTarget.CALCULATOR;
                } else if (activeApp == App.NOTES) {
                    noteEditorOpen = false;
                    noteListScrollOffset = 0;
                    noteSearch = "";
                } else if (activeApp == App.SETTINGS) {
                    settingsAppearanceOpen = false;
                    settingsInstalledAppsOpen = false;
                    settingsPasscodeOpen = false;
                    settingsSearch = "";
                    settingsMainScrollOffset = 0;
                    settingsAppearanceScrollOffset = 0;
                    settingsInstalledAppsScrollOffset = 0;
                    settingsSelectedAppId = "";
                    clearSettingsPasscodeDrafts();
                }
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
                if (contactsMessengerMode) {
                    openMessengerContact(selectedContact);
                    return;
                }
                contactDetailOpen = true;
                contactDetailScrollOffset = 0;
                activeApp = App.CONTACTS;
                inputTarget = InputTarget.NONE;
            }
            case "HOME_PAGE" -> {
                setHomeGridPage(parseInt(p1, 0));
                activeApp = App.HOME;
                inputTarget = InputTarget.NONE;
            }
            case "CONTACTS_BACK" -> {
                contactDetailOpen = false;
                contactDetailScrollOffset = 0;
                activeApp = App.CONTACTS;
                inputTarget = InputTarget.NONE;
            }
            case "CONTACT_MESSAGE" -> {
                UUID contactId = parseUuid(p1);
                if (contactId == null) {
                    contactId = selectedContact;
                }
                openMessengerContact(contactId);
            }
            case "INPUT" -> {
                inputTarget = parseInputTarget(p1);
                if (inputTarget == InputTarget.MESSENGER_REQUEST_AMOUNT) {
                    messengerAccountPickerOpen = false;
                }
            }
            case "SEARCH_CLEAR" -> {
                historySearch = "";
                inputTarget = InputTarget.HISTORY_SEARCH;
            }
            case "CONTACT_SEARCH_CLEAR" -> {
                contactSearch = "";
                contactListScrollOffset = 0;
                inputTarget = InputTarget.CONTACT_SEARCH;
            }
            case "CONTACT_SEARCH_CANCEL" -> {
                contactSearch = "";
                contactListScrollOffset = 0;
                inputTarget = InputTarget.NONE;
            }
            case "CONTACT_JUMP" -> {
                jumpToContactSection(p1);
                inputTarget = InputTarget.NONE;
            }
            case "NOTE_SEARCH_CLEAR" -> {
                noteSearch = "";
                noteListScrollOffset = 0;
                inputTarget = InputTarget.NOTE_SEARCH;
            }
            case "NOTE_SEARCH_CANCEL" -> {
                noteSearch = "";
                noteListScrollOffset = 0;
                inputTarget = InputTarget.NONE;
            }
            case "SETTINGS_SEARCH_CLEAR" -> {
                settingsSearch = "";
                settingsMainScrollOffset = 0;
                inputTarget = InputTarget.SETTINGS_SEARCH;
            }
            case "SETTINGS_SEARCH_CANCEL" -> {
                settingsSearch = "";
                settingsMainScrollOffset = 0;
                inputTarget = InputTarget.NONE;
            }
            case "CALC_CLEAR" -> {
                clearCalculator();
                inputTarget = InputTarget.CALCULATOR;
            }
            case "CALC_KEY" -> appendCalculatorKey(p1);
            case "CALC_TOGGLE_SIGN" -> toggleCalculatorSign();
            case "CALC_PERCENT" -> applyCalculatorPercent();
            case "CALC_BACKSPACE" -> {
                backspaceCalculator();
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
            case "MARKET_REFRESH" -> sendAction("MARKET_REFRESH", "", "", "");
            case "PAY_REQUEST_ACCEPT" -> sendPayRequestAction(p1, "accept_primary");
            case "PAY_REQUEST_DECLINE" -> sendPayRequestAction(p1, "decline");
            case "MESSAGE_ATTACH_TOGGLE" -> {
                if (messengerTrayOpen) {
                    closeMessengerTray();
                    inputTarget = InputTarget.MESSAGE;
                } else {
                    messengerTrayOpen = true;
                    messengerPayRequestPanel = false;
                    messengerGiftPanel = false;
                    messengerAccountPickerOpen = false;
                    inputTarget = InputTarget.NONE;
                }
            }
            case "MESSAGE_PAY_REQUEST_TILE" -> {
                messengerTrayOpen = true;
                messengerPayRequestPanel = true;
                messengerGiftPanel = false;
                messengerAccountPickerOpen = false;
                messengerRequestAccount();
                inputTarget = InputTarget.MESSENGER_REQUEST_AMOUNT;
            }
            case "MESSAGE_GIFT_TILE" -> {
                messengerTrayOpen = true;
                messengerPayRequestPanel = false;
                messengerGiftPanel = true;
                messengerAccountPickerOpen = false;
                messengerRequestAccount();
                inputTarget = InputTarget.MESSENGER_REQUEST_AMOUNT;
            }
            case "MESSAGE_REQUEST_ACCOUNT_PICKER" -> {
                messengerTrayOpen = true;
                if (!messengerPayRequestPanel && !messengerGiftPanel) {
                    messengerPayRequestPanel = true;
                }
                messengerAccountPickerOpen = true;
                inputTarget = InputTarget.NONE;
            }
            case "MESSAGE_REQUEST_ACCOUNT_SELECT" -> {
                UUID accountId = parseUuid(p1);
                if (accountById(accountId) != null) {
                    messengerRequestAccount = accountId;
                }
                messengerAccountPickerOpen = false;
                inputTarget = InputTarget.MESSENGER_REQUEST_AMOUNT;
            }
            case "MESSAGE_REQUEST_SEND" -> {
                PhoneAccount account = messengerRequestAccount();
                PhoneContact contact = selectedContact();
                String amount = messengerRequestAmount == null ? "" : messengerRequestAmount.trim();
                if (account == null) {
                    statusMessage = messengerGiftPanel ? "Choose an account for the gift." : "Choose an account for the request.";
                    inputTarget = InputTarget.NONE;
                    return;
                }
                if (contact == null) {
                    statusMessage = "Choose a contact first.";
                    inputTarget = InputTarget.MESSAGE;
                    return;
                }
                if (amount.isBlank()) {
                    statusMessage = messengerGiftPanel ? "Enter a gift amount." : "Enter a request amount.";
                    inputTarget = InputTarget.MESSENGER_REQUEST_AMOUNT;
                    return;
                }
                if (messengerGiftPanel) {
                    sendAction("MESSAGE_GIFT_CREATE", account.id().toString(), contact.id().toString(), amount);
                    statusMessage = "Sending gift...";
                } else {
                    sendAction("MESSAGE_PAY_REQUEST_CREATE", account.id().toString(), contact.id().toString(), amount);
                    statusMessage = "Sending pay request...";
                }
                inputTarget = InputTarget.NONE;
            }
            case "MESSAGE_PAY_REQUEST_ACCEPT" -> {
                PhoneAccount account = primaryAccount();
                if (account == null) {
                    account = selectedAccount();
                }
                if (account == null) {
                    statusMessage = "No account available to pay this request.";
                    return;
                }
                sendAction("MESSAGE_PAY_REQUEST_ACCEPT", p1, account.id().toString(), "");
                statusMessage = "Paying request...";
            }
            case "MESSAGE_PAY_REQUEST_DECLINE" -> {
                sendAction("MESSAGE_PAY_REQUEST_DECLINE", p1, "", "");
                statusMessage = "Declining request...";
            }
            case "MESSAGE_GIFT_ACCEPT" -> {
                PhoneAccount account = primaryAccount();
                if (account == null) {
                    account = selectedAccount();
                }
                if (account == null) {
                    statusMessage = "No account available to receive this gift.";
                    return;
                }
                sendAction("MESSAGE_GIFT_ACCEPT", p1, account.id().toString(), "");
                statusMessage = "Accepting gift...";
            }
            case "MESSAGE_GIFT_DECLINE" -> {
                sendAction("MESSAGE_GIFT_DECLINE", p1, "", "");
                statusMessage = "Declining gift...";
            }
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
            case "BANK_THEME" -> {
                bankTheme = normalizeBankTheme(p1);
                sendAction("SET_BANK_THEME", bankTheme, "", "");
                statusMessage = "Banking theme set to " + bankTheme + ".";
                activeApp = App.BANK_SETTINGS;
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
                addPendingMessage(selectedContact, body, now);
                sendAction("SEND_MESSAGE", selectedContactString(), body, "");
                resetMessageDraft();
                resetMessageThreadScroll();
                inputTarget = InputTarget.MESSAGE;
            }
            case "SAVE_NOTE" -> {
                closeNoteEditorSavingDraft(true);
            }
            case "DELETE_NOTE" -> {
                int deletedIndex = noteIndex;
                deleteLocalNote(deletedIndex);
                sendAction("DELETE_NOTE", String.valueOf(deletedIndex), "", "");
                noteDraft = "";
                noteEditorOpen = false;
                resetNoteDraftScroll();
                noteListScrollOffset = 0;
                inputTarget = InputTarget.NONE;
                statusMessage = "Note deleted.";
            }
            case "NOTE_NEW" -> {
                int availableIndex = nextAvailableNoteIndex();
                if (availableIndex < 0) {
                    statusMessage = "Notes are full.";
                    inputTarget = InputTarget.NONE;
                    return;
                }
                noteIndex = availableIndex;
                noteDraft = "";
                resetNoteDraftScroll();
                noteEditorOpen = true;
                activeApp = App.NOTES;
                inputTarget = InputTarget.NOTE;
            }
            case "NOTE_SELECT" -> {
                noteIndex = Mth.clamp(parseInt(p1, 0), 0, MAX_PHONE_NOTES - 1);
                noteDraft = noteIndex < notes.size() ? notes.get(noteIndex) : "";
                resetNoteDraftScroll();
                noteEditorOpen = true;
                activeApp = App.NOTES;
                inputTarget = InputTarget.NOTE;
            }
            case "NOTE_LIST" -> {
                closeNoteEditorSavingDraft(false);
            }
            case "PAINT_SELECT" -> {
                paintIndex = Mth.clamp(parseInt(p1, 0), 0, 5);
                activeApp = App.PAINT;
                inputTarget = InputTarget.NONE;
            }
            case "PAINT_COLOR" -> {
                paintColor = parsePaintColor(p1);
                activeApp = App.PAINT;
                inputTarget = InputTarget.NONE;
            }
            case "PAINT_CELL" -> {
                String nextPainting = paintCell(Mth.clamp(parseInt(p1, 0), 0, 80), paintColor);
                sendAction("SAVE_PAINTING", String.valueOf(paintIndex), nextPainting, "");
                statusMessage = "Pixel updated.";
                activeApp = App.PAINT;
                inputTarget = InputTarget.NONE;
            }
            case "PAINT_CLEAR" -> {
                saveLocalPainting(paintIndex, "");
                sendAction("SAVE_PAINTING", String.valueOf(paintIndex), "", "");
                statusMessage = "Canvas cleared.";
                activeApp = App.PAINT;
                inputTarget = InputTarget.NONE;
            }
            case "SETTINGS_APPEARANCE" -> {
                settingsAppearanceOpen = true;
                settingsInstalledAppsOpen = false;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = "";
                settingsSearch = "";
                settingsMainScrollOffset = 0;
                settingsAppearanceScrollOffset = 0;
                clearSettingsPasscodeDrafts();
                inputTarget = InputTarget.NONE;
            }
            case "SETTINGS_APPS" -> {
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = true;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = "";
                settingsSearch = "";
                settingsMainScrollOffset = 0;
                settingsAppearanceScrollOffset = 0;
                settingsInstalledAppsScrollOffset = 0;
                clearSettingsPasscodeDrafts();
                inputTarget = InputTarget.NONE;
            }
            case "SETTINGS_APP_DETAIL" -> {
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = true;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = p1 == null ? "" : p1;
                clearSettingsPasscodeDrafts();
                inputTarget = InputTarget.NONE;
            }
            case "SETTINGS_PASSCODE" -> {
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = false;
                settingsPasscodeOpen = true;
                settingsSelectedAppId = "";
                settingsSearch = "";
                clearSettingsPasscodeDrafts();
                inputTarget = InputTarget.PHONE_PASSCODE_CURRENT;
            }
            case "SAVE_PHONE_PASSCODE" -> savePhonePasscodeChange();
            case "SETTINGS_APP_OPEN" -> {
                settingsSelectedAppId = "";
                handleUiAction("APP", p1, "", "");
            }
            case "SETTINGS_MAIN" -> {
                settingsAppearanceOpen = false;
                settingsInstalledAppsOpen = false;
                settingsPasscodeOpen = false;
                settingsSelectedAppId = "";
                settingsAppearanceScrollOffset = 0;
                settingsInstalledAppsScrollOffset = 0;
                clearSettingsPasscodeDrafts();
                inputTarget = InputTarget.NONE;
            }
            case "THEME" -> {
                if (p1 != null && !p1.isBlank()) {
                    accent = p1;
                }
                if (p2 != null && !p2.isBlank()) {
                    wallpaper = p2;
                }
                sendAction("SET_THEME", p1, p2, "");
            }
            case "FAVORITE" -> sendAction("FAVORITE_CONTACT", contactActionTarget(p1), "", "");
            case "UNFAVORITE" -> sendAction("UNFAVORITE_CONTACT", contactActionTarget(p1), "", "");
            case "MUTE" -> sendAction("MUTE_CONTACT", contactActionTarget(p1), "", "");
            case "UNMUTE" -> sendAction("UNMUTE_CONTACT", contactActionTarget(p1), "", "");
            case "BLOCK" -> sendAction("BLOCK_CONTACT", contactActionTarget(p1), "", "");
            case "UNBLOCK" -> sendAction("UNBLOCK_CONTACT", contactActionTarget(p1), "", "");
            case "REPORT" -> sendAction("REPORT_CONTACT", contactActionTarget(p1), contactReportReason(p1), "");
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

    private static void syncMessengerTypingFromDraft() {
        if (selectedContact == null) {
            sendMessengerTypingInactive();
            return;
        }
        boolean hasDraft = messageDraft != null && !messageDraft.trim().isEmpty();
        if (!hasDraft) {
            sendMessengerTypingInactive();
            return;
        }
        long now = System.currentTimeMillis();
        if (!localTypingActive
                || !selectedContact.equals(localTypingContact)
                || now - lastTypingSentMillis >= MESSAGE_TYPING_REFRESH_INTERVAL_MS) {
            sendMessengerTyping(selectedContact, true);
        }
    }

    private static void sendMessengerTypingInactive() {
        if (!localTypingActive || localTypingContact == null) {
            return;
        }
        sendMessengerTyping(localTypingContact, false);
    }

    private static void sendMessengerTyping(UUID contactId, boolean active) {
        if (contactId == null) {
            return;
        }
        localTypingContact = active ? contactId : null;
        localTypingActive = active;
        lastTypingSentMillis = System.currentTimeMillis();
        sendAction("MESSAGE_TYPING", contactId.toString(), Boolean.toString(active), "");
    }

    private static void openMessengerContact(UUID contactId) {
        if (contactId == null) {
            statusMessage = "Choose a contact first.";
            return;
        }
        if (selectedContact != null && !selectedContact.equals(contactId)) {
            sendMessengerTypingInactive();
        }
        selectedContact = contactId;
        resetMessageThreadScroll();
        closeMessengerTray();
        contactsMessengerMode = true;
        contactDetailOpen = false;
        activeApp = App.MESSENGER;
        inputTarget = InputTarget.MESSAGE;
        PacketDistributor.sendToServer(new SmartphoneActionPayload("READ_CONVERSATION", contactId.toString(), "", ""));
    }

    private static void resetMessageDraft() {
        sendMessengerTypingInactive();
        messageDraft = "";
        messageDraftScrollOffset = 0;
        messageDraftScrollMax = 0;
        messageDraftStickToBottom = true;
    }

    private static void resetNoteDraftScroll() {
        noteDraftScrollOffset = 0;
        noteDraftScrollMax = 0;
        noteDraftStickToBottom = true;
    }

    private static void saveLocalNote(int index, String body) {
        int safeIndex = Mth.clamp(index, 0, MAX_PHONE_NOTES - 1);
        while (notes.size() <= safeIndex) {
            notes.add("");
        }
        notes.set(safeIndex, body == null ? "" : body);
    }

    private static void deleteLocalNote(int index) {
        if (index >= 0 && index < MAX_PHONE_NOTES) {
            while (notes.size() <= index) {
                notes.add("");
            }
            notes.set(index, "");
        }
        noteIndex = Mth.clamp(index, 0, MAX_PHONE_NOTES - 1);
    }

    private static void closeNoteEditorSavingDraft(boolean showSavedStatus) {
        boolean blankDraft = noteDraft == null || noteDraft.isBlank();
        if (blankDraft) {
            if (!noteSlotBlank(noteIndex)) {
                int deletedIndex = noteIndex;
                deleteLocalNote(deletedIndex);
                sendAction("DELETE_NOTE", String.valueOf(deletedIndex), "", "");
                statusMessage = "Note deleted.";
            } else {
                statusMessage = "";
            }
        } else {
            if (!noteDraft.equals(currentNoteBody(noteIndex))) {
                saveLocalNote(noteIndex, noteDraft);
                sendAction("SAVE_NOTE", String.valueOf(noteIndex), noteDraft, "");
                if (showSavedStatus) {
                    statusMessage = "Note saved.";
                }
            } else if (showSavedStatus) {
                statusMessage = "";
            }
        }
        noteEditorOpen = false;
        resetNoteDraftScroll();
        noteListScrollOffset = 0;
        inputTarget = InputTarget.NONE;
    }

    private static void saveLocalPainting(int index, String pixels) {
        int safeIndex = Mth.clamp(index, 0, 5);
        while (paintings.size() <= safeIndex) {
            paintings.add("");
        }
        paintings.set(safeIndex, normalizePainting(pixels));
    }

    private static String paintCell(int cellIndex, char color) {
        char[] cells = new char[81];
        String current = paintingDraft();
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < current.length() && isPaintPixel(current.charAt(i)) ? current.charAt(i) : '.';
        }
        cells[Mth.clamp(cellIndex, 0, 80)] = color;
        String next = trimPaintCells(cells);
        saveLocalPainting(paintIndex, next);
        return next;
    }

    private static char parsePaintColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return paintColor;
        }
        char color = Character.toLowerCase(raw.trim().charAt(0));
        return isPaintPixel(color) ? color : paintColor;
    }

    private static String normalizePainting(String pixels) {
        String raw = pixels == null ? "" : pixels.trim();
        char[] cells = new char[81];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = i < raw.length() && isPaintPixel(raw.charAt(i)) ? raw.charAt(i) : '.';
        }
        return trimPaintCells(cells);
    }

    private static String trimPaintCells(char[] cells) {
        int end = Math.min(81, cells == null ? 0 : cells.length);
        while (end > 0 && cells[end - 1] == '.') {
            end--;
        }
        return end <= 0 ? "" : new String(cells, 0, end);
    }

    private static boolean isPaintPixel(char value) {
        char color = Character.toLowerCase(value);
        return (color >= '0' && color <= '9') || (color >= 'a' && color <= 'f') || color == '.';
    }

    private static void closeMessengerTray() {
        messengerTrayOpen = false;
        messengerPayRequestPanel = false;
        messengerGiftPanel = false;
        messengerAccountPickerOpen = false;
        messengerAccountPickerOffset = 0;
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
        } else if (inputTarget == InputTarget.CONTACT_SEARCH) {
            List<PhoneContact> visible = visibleContacts();
            if (!visible.isEmpty()) {
                handleUiAction("CONTACT", visible.get(0).id().toString(), "", "");
            }
        } else if (inputTarget == InputTarget.NOTE_SEARCH) {
            List<PhoneNote> visible = visibleNotes();
            if (!visible.isEmpty()) {
                handleUiAction("NOTE_SELECT", String.valueOf(visible.get(0).index()), "", "");
            }
        } else if (inputTarget == InputTarget.SETTINGS_SEARCH) {
            openFirstSettingsSearchResult();
        } else if (paymentTargetModalOpen
                && (inputTarget == InputTarget.TRANSFER_TO || inputTarget == InputTarget.REQUEST_TARGET)) {
            handleUiAction("CONFIRM_PAYMENT_TARGET_MODAL", "", "", "");
        } else if (inputTarget == InputTarget.MESSAGE) {
            handleUiAction("SEND_MESSAGE", "", "", "");
        } else if (inputTarget == InputTarget.TRANSFER_AMOUNT) {
            handleUiAction("TRANSFER", "", "", "");
        } else if (inputTarget == InputTarget.REQUEST_AMOUNT) {
            handleUiAction("REQUEST_MONEY", "", "", "");
        } else if (inputTarget == InputTarget.MESSENGER_REQUEST_AMOUNT) {
            handleUiAction("MESSAGE_REQUEST_SEND", "", "", "");
        } else if (inputTarget == InputTarget.CONFIRM_PIN) {
            handleUiAction("CHANGE_BANK_PIN", "", "", "");
        } else if (inputTarget == InputTarget.BANK_PIN) {
            handleUiAction("BANK_SIGN_IN", "", "", "");
        } else if (inputTarget == InputTarget.SIGNUP_PIN) {
            handleUiAction("OPEN_DEFAULT_ACCOUNT", "", "", "");
        } else if (inputTarget == InputTarget.PROFILE_NAME) {
            handleUiAction("SAVE_BANK_PROFILE", "", "", "");
        } else if (inputTarget == InputTarget.PHONE_PASSCODE) {
            submitPhonePasscode();
        } else if (inputTarget == InputTarget.PHONE_PASSCODE_CURRENT) {
            inputTarget = InputTarget.PHONE_PASSCODE_NEW;
        } else if (inputTarget == InputTarget.PHONE_PASSCODE_NEW) {
            inputTarget = InputTarget.PHONE_PASSCODE_CONFIRM;
        } else if (inputTarget == InputTarget.PHONE_PASSCODE_CONFIRM) {
            savePhonePasscodeChange();
        }
    }

    private static void mutateInput(boolean backspace, char typed) {
        switch (inputTarget) {
            case CALCULATOR -> {
                if (backspace) {
                    backspaceCalculator();
                } else if (typed == '=') {
                    evaluateCalculator();
                } else if (typed == '%') {
                    applyCalculatorPercent();
                } else if (typed != 0 && isCalculatorKeyboardChar(typed)) {
                    appendCalculatorKey(String.valueOf(typed));
                }
            }
            case TRANSFER_TO -> transferTo = mutate(transferTo, backspace, typed, 64, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-");
            case TRANSFER_AMOUNT -> transferAmount = mutate(transferAmount, backspace, typed, 16, "0123456789.");
            case HISTORY_SEARCH -> historySearch = mutate(historySearch, backspace, typed, 40, null);
            case CONTACT_SEARCH -> {
                String updated = mutate(contactSearch, backspace, typed, 40, null);
                if (!updated.equals(contactSearch)) {
                    contactSearch = updated;
                    contactListScrollOffset = 0;
                }
            }
            case NOTE_SEARCH -> {
                String updated = mutate(noteSearch, backspace, typed, 80, null);
                if (!updated.equals(noteSearch)) {
                    noteSearch = updated;
                    noteListScrollOffset = 0;
                }
            }
            case SETTINGS_SEARCH -> {
                String updated = mutate(settingsSearch, backspace, typed, 40, null);
                if (!updated.equals(settingsSearch)) {
                    settingsSearch = updated;
                    settingsMainScrollOffset = 0;
                }
            }
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
            case MESSENGER_REQUEST_AMOUNT -> messengerRequestAmount = mutate(messengerRequestAmount, backspace, typed, 16, "0123456789.");
            case PHONE_PASSCODE -> {
                if (backspace) {
                    deletePhonePasscodeDigit();
                } else if (typed != 0 && Character.isDigit(typed)) {
                    appendPhonePasscodeDigit(String.valueOf(typed));
                }
            }
            case PHONE_PASSCODE_CURRENT -> settingsPasscodeCurrent = mutate(settingsPasscodeCurrent, backspace, typed, 4, "0123456789");
            case PHONE_PASSCODE_NEW -> settingsPasscodeNew = mutate(settingsPasscodeNew, backspace, typed, 4, "0123456789");
            case PHONE_PASSCODE_CONFIRM -> settingsPasscodeConfirm = mutate(settingsPasscodeConfirm, backspace, typed, 4, "0123456789");
            case MESSAGE -> {
                String updated = mutate(messageDraft, backspace, typed, 180, null);
                if (!updated.equals(messageDraft)) {
                    messageDraft = updated;
                    messageDraftStickToBottom = true;
                    syncMessengerTypingFromDraft();
                }
            }
            case NOTE -> {
                String updated = mutate(noteDraft, backspace, typed, 4000, null);
                if (!updated.equals(noteDraft)) {
                    noteDraft = updated;
                    noteDraftStickToBottom = true;
                }
            }
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
        boolean shift = shiftDown();
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
            char base = (char) ('a' + (key - GLFW.GLFW_KEY_A));
            return shift ? Character.toUpperCase(base) : base;
        }
        if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) {
            char digit = (char) ('0' + (key - GLFW.GLFW_KEY_0));
            return shift ? shiftedDigit(digit) : digit;
        }
        if (key >= GLFW.GLFW_KEY_KP_0 && key <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + (key - GLFW.GLFW_KEY_KP_0));
        }
        return switch (key) {
            case GLFW.GLFW_KEY_SPACE -> ' ';
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> shift ? '~' : '`';
            case GLFW.GLFW_KEY_PERIOD -> shift ? '>' : '.';
            case GLFW.GLFW_KEY_COMMA -> shift ? '<' : ',';
            case GLFW.GLFW_KEY_MINUS -> shift ? '_' : '-';
            case GLFW.GLFW_KEY_EQUAL -> shift ? '+' : '=';
            case GLFW.GLFW_KEY_SLASH -> shift ? '?' : '/';
            case GLFW.GLFW_KEY_BACKSLASH -> shift ? '|' : '\\';
            case GLFW.GLFW_KEY_SEMICOLON -> shift ? ':' : ';';
            case GLFW.GLFW_KEY_APOSTROPHE -> shift ? '"' : '\'';
            case GLFW.GLFW_KEY_LEFT_BRACKET -> shift ? '{' : '[';
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> shift ? '}' : ']';
            case GLFW.GLFW_KEY_KP_DECIMAL -> '.';
            case GLFW.GLFW_KEY_KP_SUBTRACT -> '-';
            case GLFW.GLFW_KEY_KP_ADD -> '+';
            case GLFW.GLFW_KEY_KP_DIVIDE -> '/';
            case GLFW.GLFW_KEY_KP_MULTIPLY -> '*';
            default -> 0;
        };
    }

    private static boolean shiftDown() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return false;
        }
        long window = mc.getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static char shiftedDigit(char digit) {
        return switch (digit) {
            case '1' -> '!';
            case '2' -> '@';
            case '3' -> '#';
            case '4' -> '$';
            case '5' -> '%';
            case '6' -> '^';
            case '7' -> '&';
            case '8' -> '*';
            case '9' -> '(';
            case '0' -> ')';
            default -> digit;
        };
    }

    private static boolean isCalculatorKeyboardChar(char value) {
        return "0123456789.+-*/xX\u00D7\u2715\u00F7".indexOf(value) >= 0;
    }

    private static void evaluateCalculator() {
        try {
            double current = parseCalculatorOperand(currentCalculatorDisplay());
            if (!calculatorPendingOperator.isBlank()) {
                double result = applyCalculatorOperation(calculatorAccumulator, current, calculatorPendingOperator);
                calculatorAccumulator = result;
                calculatorResult = formatCalculatorValue(result);
                calculatorInput = calculatorResult;
                calculatorPendingOperator = "";
                calculatorAwaitingOperand = true;
            } else {
                calculatorResult = formatCalculatorValue(current);
                calculatorInput = calculatorResult;
            }
            calculatorResultCurrent = true;
        } catch (RuntimeException ex) {
            calculatorError();
        }
        inputTarget = InputTarget.CALCULATOR;
    }

    private static void appendCalculatorKey(String raw) {
        String key = normalizeCalculatorKey(raw);
        if (key.isEmpty()) {
            inputTarget = InputTarget.CALCULATOR;
            return;
        }
        if (key.length() != 1 || !"0123456789.+-*/".contains(key)) {
            inputTarget = InputTarget.CALCULATOR;
            return;
        }

        char value = key.charAt(0);
        if (isCalculatorOperator(value)) {
            commitCalculatorOperator(String.valueOf(value));
        } else {
            appendCalculatorDigit(value);
        }
        inputTarget = InputTarget.CALCULATOR;
    }

    private static String normalizeCalculatorKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        return switch (key) {
            case "x", "X", "\u00D7", "\u2715" -> "*";
            case "\u00F7" -> "/";
            default -> key;
        };
    }

    private static void appendCalculatorDigit(char value) {
        if (calculatorResultCurrent && calculatorPendingOperator.isBlank()) {
            calculatorInput = "";
            calculatorResult = "0";
            calculatorResultCurrent = false;
            calculatorAwaitingOperand = false;
        }
        if (calculatorAwaitingOperand) {
            calculatorInput = "";
            calculatorResultCurrent = false;
            calculatorAwaitingOperand = false;
        }
        String current = calculatorInput == null ? "" : calculatorInput;
        if (current.equals("Error")) {
            current = "";
        }
        if (value == '.') {
            if (current.contains(".")) {
                return;
            }
            calculatorInput = current.isBlank() ? "0." : current + ".";
            calculatorResultCurrent = false;
            return;
        }
        if (current.equals("0")) {
            calculatorInput = String.valueOf(value);
        } else if (current.equals("-0")) {
            calculatorInput = "-" + value;
        } else if (current.length() < 12) {
            calculatorInput = current + value;
        }
        calculatorResultCurrent = false;
    }

    private static void commitCalculatorOperator(String operator) {
        try {
            double current = parseCalculatorOperand(currentCalculatorDisplay());
            if (!calculatorPendingOperator.isBlank() && !calculatorAwaitingOperand) {
                calculatorAccumulator = applyCalculatorOperation(calculatorAccumulator, current, calculatorPendingOperator);
                calculatorResult = formatCalculatorValue(calculatorAccumulator);
                calculatorInput = calculatorResult;
                calculatorResultCurrent = true;
            } else {
                calculatorAccumulator = current;
                calculatorResult = formatCalculatorValue(current);
            }
            calculatorPendingOperator = operator;
            calculatorAwaitingOperand = true;
            calculatorResultCurrent = true;
        } catch (RuntimeException ex) {
            calculatorError();
        }
    }

    private static void openFirstSettingsSearchResult() {
        String query = settingsSearch == null ? "" : settingsSearch.trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            inputTarget = InputTarget.NONE;
            return;
        }
        if (settingsRowMatches("Appearance", settingsThemeLabel(), query)) {
            handleUiAction("SETTINGS_APPEARANCE", "", "", "");
            return;
        }
        if (settingsRowMatches("Installed apps", apps.size() + " apps", query)) {
            handleUiAction("SETTINGS_APPS", "", "", "");
            return;
        }
        if (settingsRowMatches("Change Passcode", phonePasscodeSet ? "4 digit passcode" : "Not set", query)) {
            handleUiAction("SETTINGS_PASSCODE", "", "", "");
            return;
        }
        statusMessage = "No matching settings.";
        inputTarget = InputTarget.SETTINGS_SEARCH;
    }

    private static boolean settingsRowMatches(String title, String subtitle, String query) {
        String safeTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String safeSubtitle = subtitle == null ? "" : subtitle.toLowerCase(Locale.ROOT);
        return safeTitle.contains(query) || safeSubtitle.contains(query);
    }

    private static String settingsThemeLabel() {
        return switch (wallpaper.toLowerCase(Locale.ROOT)) {
            case "forest" -> "Forest";
            case "dusk" -> "Dusk";
            case "mono" -> "Midnight";
            default -> "Aurora";
        };
    }

    private static boolean isCalculatorOperator(char value) {
        return value == '+' || value == '-' || value == '*' || value == '/';
    }

    private static void toggleCalculatorSign() {
        String current = currentCalculatorDisplay();
        if (current.equals("0") || current.equals("Error")) {
            inputTarget = InputTarget.CALCULATOR;
            return;
        }
        calculatorInput = current.startsWith("-") ? current.substring(1) : "-" + current;
        calculatorResult = calculatorInput;
        calculatorResultCurrent = false;
        calculatorAwaitingOperand = false;
        inputTarget = InputTarget.CALCULATOR;
    }

    private static void applyCalculatorPercent() {
        try {
            double value = parseCalculatorOperand(currentCalculatorDisplay());
            double percent = value / 100.0D;
            if (!calculatorPendingOperator.isBlank()
                    && ("+".equals(calculatorPendingOperator) || "-".equals(calculatorPendingOperator))) {
                percent = calculatorAccumulator * percent;
            }
            calculatorInput = formatCalculatorValue(percent);
            calculatorResult = calculatorInput;
            calculatorResultCurrent = false;
            calculatorAwaitingOperand = false;
        } catch (NumberFormatException ignored) {
            calculatorError();
        }
        inputTarget = InputTarget.CALCULATOR;
    }

    private static void backspaceCalculator() {
        if (calculatorResultCurrent || calculatorAwaitingOperand || calculatorInput.isBlank()) {
            calculatorInput = "";
            calculatorResult = "0";
            calculatorResultCurrent = false;
            calculatorAwaitingOperand = false;
            return;
        }
        calculatorInput = calculatorInput.length() <= 1
                || (calculatorInput.length() == 2 && calculatorInput.startsWith("-"))
                ? ""
                : calculatorInput.substring(0, calculatorInput.length() - 1);
        calculatorResultCurrent = false;
    }

    private static void clearCalculator() {
        if (calculatorAtResetState()) {
            resetCalculator();
            return;
        }
        if (!calculatorPendingOperator.isBlank()
                && calculatorAwaitingOperand
                && (calculatorInput == null || calculatorInput.isBlank())) {
            resetCalculator();
            return;
        }
        if (calculatorPendingOperator.isBlank() || calculatorResultCurrent && !calculatorAwaitingOperand) {
            resetCalculator();
            return;
        }
        calculatorInput = "";
        calculatorResult = "0";
        calculatorResultCurrent = false;
        calculatorAwaitingOperand = true;
    }

    private static void resetCalculator() {
        calculatorInput = "";
        calculatorResult = "0";
        calculatorResultCurrent = false;
        calculatorAccumulator = 0.0D;
        calculatorPendingOperator = "";
        calculatorAwaitingOperand = false;
    }

    private static String currentCalculatorDisplay() {
        String value = calculatorResultCurrent ? calculatorResult : calculatorInput;
        return value == null || value.isBlank() ? "0" : value;
    }

    private static double parseCalculatorOperand(String value) {
        String safe = value == null || value.isBlank() ? "0" : value;
        if ("Error".equals(safe)) {
            throw new NumberFormatException("calculator error");
        }
        return Double.parseDouble(safe);
    }

    private static double applyCalculatorOperation(double left, double right, String operator) {
        return switch (operator) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> {
                if (Math.abs(right) < 0.0000001D) {
                    throw new ArithmeticException("divide by zero");
                }
                yield left / right;
            }
            default -> right;
        };
    }

    private static void calculatorError() {
        calculatorInput = "";
        calculatorResult = "Error";
        calculatorResultCurrent = true;
        calculatorPendingOperator = "";
        calculatorAwaitingOperand = true;
    }

    private static String formatCalculatorValue(double value) {
        double rounded = Math.round(value * 1000000.0D) / 1000000.0D;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.0000001D) {
            return String.valueOf((long) Math.rint(rounded));
        }
        String text = String.valueOf(rounded);
        while (text.contains(".") && text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }

    private static double evaluateExpression(String input) {
        String expr = input == null ? "" : input.replace(" ", "");
        if (expr.isBlank()) {
            return 0.0D;
        }
        final int[] pos = {0};
        double value = parseExpression(expr, pos);
        if (pos[0] != expr.length()) {
            throw new IllegalArgumentException("Unexpected input");
        }
        return value;
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
            if (pos[0] >= s.length() || s.charAt(pos[0]) != ')') {
                throw new IllegalArgumentException("Missing closing parenthesis");
            }
            pos[0]++;
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

    private static int nextAvailableNoteIndex() {
        for (int i = 0; i < MAX_PHONE_NOTES; i++) {
            if (i >= notes.size() || notes.get(i) == null || notes.get(i).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private static String currentNoteBody(int index) {
        return index >= 0 && index < notes.size() && notes.get(index) != null ? notes.get(index) : "";
    }

    private static boolean noteSlotBlank(int index) {
        return index < 0 || index >= notes.size() || notes.get(index) == null || notes.get(index).isBlank();
    }

    private static String selectedAccountString() {
        PhoneAccount account = selectedAccount();
        return account == null ? "" : account.id().toString();
    }

    private static String selectedContactString() {
        PhoneContact contact = selectedContact();
        return contact == null ? "" : contact.id().toString();
    }

    private static String contactActionTarget(String raw) {
        UUID id = parseUuid(raw);
        return id == null ? selectedContactString() : id.toString();
    }

    private static String contactReportReason(String raw) {
        return parseUuid(raw) == null && messageDraft != null && !messageDraft.isBlank()
                ? messageDraft
                : "Phone report";
    }

    private static String normalizeBankTheme(String raw) {
        return "light".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "light" : "dark";
    }

    private static String normalizePhoneAccessMode(String raw) {
        return "OPEN_ACCESS".equalsIgnoreCase(raw == null ? "" : raw.trim()) ? "OPEN_ACCESS" : "OWNER_LOCKED";
    }

    private static void parseLines(List<String> lines) {
        apps.clear();
        accounts.clear();
        contacts.clear();
        paymentRecipients.clear();
        bankStaffMembers.clear();
        messages.clear();
        typingIndicators.clear();
        transactions.clear();
        marketQuotes.clear();
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
                    bankTheme = normalizeBankTheme(get(parts, 3, "dark"));
                }
                case "security" -> phonePasscodeSet = parseBool(get(parts, 1, "false"));
                case "phone_access" -> phoneAccessMode = normalizePhoneAccessMode(get(parts, 1, "OWNER_LOCKED"));
                case "server_time" -> {
                    serverClockEpochMillis = parseLong(get(parts, 1, "0"), System.currentTimeMillis());
                    serverClockReceivedAtMillis = System.currentTimeMillis();
                    serverClockZone = get(parts, 2, "");
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
                case "market_quote" -> marketQuotes.add(new MarketQuote(
                        get(parts, 1, ""),
                        get(parts, 2, "Commodity"),
                        get(parts, 3, "unit"),
                        get(parts, 4, "Unpriced"),
                        get(parts, 5, "-"),
                        get(parts, 6, "-"),
                        get(parts, 7, "Seed"),
                        get(parts, 8, "-"),
                        get(parts, 9, "-"),
                        get(parts, 10, "Central Bank spot desk"),
                        parseLong(get(parts, 11, "0"), 0L),
                        parseBool(get(parts, 12, "false")),
                        get(parts, 13, ""),
                        get(parts, 14, "")
                ));
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
                case "message_pay_request" -> {
                    UUID other = parseUuid(get(parts, 1, ""));
                    UUID messageId = parseUuid(get(parts, 2, ""));
                    UUID sender = parseUuid(get(parts, 3, ""));
                    UUID requestId = parseUuid(get(parts, 8, ""));
                    if (other != null && sender != null && requestId != null) {
                        messages.add(new PhoneMessage(other, messageId == null ? fallbackMessageId(parts) : messageId,
                                sender, get(parts, 4, "Player"), get(parts, 5, "Pay request"),
                                get(parts, 6, ""), parseLong(get(parts, 7, "0"), 0L),
                                MessageKind.PAY_REQUEST,
                                requestId,
                                get(parts, 9, "$0"),
                                get(parts, 10, "PENDING"),
                                parseUuid(get(parts, 11, "")),
                                parseUuid(get(parts, 12, ""))));
                    }
                }
                case "message_gift" -> {
                    UUID other = parseUuid(get(parts, 1, ""));
                    UUID messageId = parseUuid(get(parts, 2, ""));
                    UUID sender = parseUuid(get(parts, 3, ""));
                    UUID giftId = parseUuid(get(parts, 8, ""));
                    if (other != null && sender != null && giftId != null) {
                        messages.add(new PhoneMessage(other, messageId == null ? fallbackMessageId(parts) : messageId,
                                sender, get(parts, 4, "Player"), get(parts, 5, "Gift"),
                                get(parts, 6, ""), parseLong(get(parts, 7, "0"), 0L),
                                MessageKind.GIFT,
                                giftId,
                                get(parts, 9, "$0"),
                                get(parts, 10, "PENDING"),
                                parseUuid(get(parts, 11, "")),
                                parseUuid(get(parts, 12, ""))));
                    }
                }
                case "typing" -> {
                    UUID contact = parseUuid(get(parts, 1, ""));
                    long remainingMillis = parseLong(get(parts, 3, "0"), 0L);
                    if (contact != null && remainingMillis > 0L) {
                        typingIndicators.put(contact, new TypingIndicator(contact, get(parts, 2, "Player"),
                                System.currentTimeMillis() + remainingMillis));
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
        reconcilePendingMessages();
        if (selectedAccount == null && !accounts.isEmpty()) {
            selectedAccount = accounts.get(0).id();
        }
        ensureDefaultApps();
        if (selectedAccount != null && accounts.stream().noneMatch(account -> account.id().equals(selectedAccount))) {
            selectedAccount = accounts.isEmpty() ? null : accounts.get(0).id();
        }
        if (messengerRequestAccount != null
                && accounts.stream().noneMatch(account -> account.id().equals(messengerRequestAccount))) {
            PhoneAccount primary = primaryAccount();
            messengerRequestAccount = primary == null ? null : primary.id();
        }
        unlockedBankAccounts.removeIf(accountId -> accounts.stream().noneMatch(account -> account.id().equals(accountId)));
        if (pendingBankingAuthAccount != null
                && accounts.stream().noneMatch(account -> account.id().equals(pendingBankingAuthAccount))) {
            clearPendingBankingAuth();
        }
        if (selectedContact != null && contacts.stream().noneMatch(contact -> selectedContact.equals(contact.id()))) {
            selectedContact = null;
            contactDetailOpen = false;
            if (activeApp == App.MESSENGER) {
                contactsMessengerMode = true;
                activeApp = App.CONTACTS;
                inputTarget = InputTarget.NONE;
            }
        }
        if (selectedTransaction != null && transactions.stream().noneMatch(tx -> selectedTransaction.equals(tx.txId()))) {
            selectedTransaction = null;
            if (activeApp == App.TRANSACTION_DETAIL) {
                activeApp = App.HISTORY;
            }
        }
        paymentContactOffset = Mth.clamp(paymentContactOffset, 0, Math.max(0, paymentRecipients.size() - 4));
        if (!noteEditorOpen && noteDraft.isBlank() && noteIndex < notes.size()) {
            noteDraft = notes.get(noteIndex);
        }
        invalidatePhoneDataCaches();
    }

    private static void addPendingMessage(UUID other, String body, long createdAt) {
        if (other == null || body == null || body.isBlank()) {
            return;
        }
        String safeBody = body.trim();
        UUID sender = ownerId == null ? new UUID(0L, 0L) : ownerId;
        String senderName = ownerName == null || ownerName.isBlank() ? "You" : ownerName;
        UUID pendingId = UUID.nameUUIDFromBytes(("pending|" + sender + "|" + other + "|" + createdAt + "|" + safeBody)
                .getBytes(StandardCharsets.UTF_8));
        PhoneMessage message = new PhoneMessage(other, pendingId, sender, senderName, safeBody, "Sending", createdAt);
        pendingMessages.add(message);
        messages.add(message);
        while (pendingMessages.size() > 20) {
            pendingMessages.remove(0);
        }
        invalidatePhoneDataCaches();
    }

    private static void reconcilePendingMessages() {
        if (pendingMessages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        pendingMessages.removeIf(pending -> now - pending.createdAt() > 15_000L
                || messages.stream().anyMatch(message -> confirmsPendingMessage(pending, message)));
        for (PhoneMessage pending : pendingMessages) {
            boolean alreadyVisible = messages.stream().anyMatch(message -> message.messageId().equals(pending.messageId()));
            if (!alreadyVisible) {
                messages.add(pending);
            }
        }
    }

    private static boolean confirmsPendingMessage(PhoneMessage pending, PhoneMessage message) {
        if (pending == null || message == null || pending.messageId().equals(message.messageId())) {
            return false;
        }
        if (!pending.otherId().equals(message.otherId())) {
            return false;
        }
        if (!pending.body().equals(message.body())) {
            return false;
        }
        boolean sameSender = pending.senderId().equals(message.senderId())
                || (pending.senderName() != null && pending.senderName().equalsIgnoreCase(message.senderName()));
        if (!sameSender) {
            return false;
        }
        long delta = message.createdAt() - pending.createdAt();
        return delta >= -2_000L && delta <= 15_000L;
    }

    private static void clearRecentPendingMessage() {
        if (pendingMessages.isEmpty()) {
            return;
        }
        long cutoff = Math.max(0L, lastMessageSubmitMillis - 1_000L);
        Set<UUID> removedIds = new HashSet<>();
        pendingMessages.removeIf(message -> {
            boolean remove = message.createdAt() >= cutoff
                    && (selectedContact == null || selectedContact.equals(message.otherId()));
            if (remove) {
                removedIds.add(message.messageId());
            }
            return remove;
        });
        if (!removedIds.isEmpty()) {
            messages.removeIf(message -> removedIds.contains(message.messageId()));
            invalidatePhoneDataCaches();
        }
    }

    private static void invalidatePhoneDataCaches() {
        phoneDataRevision++;
        cachedContactsRevision = -1;
        cachedPaymentRecipientsRevision = -1;
        cachedMessagesRevision = -1;
        cachedTransactionsRevision = -1;
        cachedSelectedTransactionRevision = -1;
    }

    private static void ensureDefaultApps() {
        if (!apps.isEmpty()) {
            return;
        }
        apps.add(new PhoneApp("banking", "Banking", "UBS accounts and payments"));
        apps.add(new PhoneApp("tap", "Tap to Pay", "Default phone payment card"));
        apps.add(new PhoneApp("market", "Spot Market", "Global bullion desk"));
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
            case "market", "spot", "spot-market", "bullion" -> App.SPOT_MARKET;
            case "calculator" -> App.CALCULATOR;
            case "paint" -> App.PAINT;
            case "contacts" -> App.CONTACTS;
            case "messenger" -> App.MESSENGER;
            case "notes" -> App.NOTES;
            case "settings" -> App.SETTINGS;
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
