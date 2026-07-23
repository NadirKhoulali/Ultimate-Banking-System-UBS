package net.austizz.ultimatebankingsystem.phone;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.item.SmartphoneData;
import net.austizz.ultimatebankingsystem.market.CommodityMarketService;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.network.SmartphoneLiveRefreshPayload;
import net.austizz.ultimatebankingsystem.network.SmartphoneNotificationPayload;
import net.austizz.ultimatebankingsystem.network.SmartphoneSnapshotPayload;
import net.austizz.ultimatebankingsystem.payrequest.PayRequestManager;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SmartphoneService {
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final String PAY_REQUEST_MESSAGE_PREFIX = "[[UBS_PAY_REQUEST:";
    private static final String PAY_REQUEST_MESSAGE_SUFFIX = "]]";
    private static final String GIFT_MESSAGE_PREFIX = "[[UBS_GIFT:";
    private static final String GIFT_MESSAGE_SUFFIX = "]]";
    private static final long GIFT_TIMEOUT_MILLIS = 5L * 60L * 1000L;
    private static final long MESSENGER_TYPING_TIMEOUT_MILLIS = 85L * 1000L;
    private static final ConcurrentHashMap<String, TypingState> MESSENGER_TYPING = new ConcurrentHashMap<>();

    private SmartphoneService() {
    }

    private record TypingState(UUID senderId, String senderName, UUID recipientId, long expiresAtMillis) {
    }

    public static void openPhone(ServerPlayer player, boolean animate) {
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            ServerNotification.send(player, "Phone", "No smartphone found in your inventory.",
                    DeliveryAlertPayload.AlertTone.ERROR, 3800);
            return;
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            ServerNotification.send(player, "Phone",
                    "This phone belongs to " + SmartphoneData.getOwnerName(phone) + ".",
                    DeliveryAlertPayload.AlertTone.ERROR, 4600);
            return;
        }
        PacketDistributor.sendToPlayer(player, buildSnapshot(player, phone, animate));
        deliverQueuedPhoneNotifications(player, true);
    }

    public static void onPlayerLogin(ServerPlayer player) {
        deliverQueuedPhoneNotifications(player, false);
    }

    public static SmartphoneSnapshotPayload handleAction(ServerPlayer player, String action, String p1, String p2, String p3) {
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            return SmartphoneSnapshotPayload.closed("No smartphone found in inventory.");
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            return SmartphoneSnapshotPayload.closed("This phone belongs to " + SmartphoneData.getOwnerName(phone) + ".");
        }

        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String status = switch (normalized) {
            case "SET_THEME" -> handleSetTheme(phone, p1, p2);
            case "SET_PHONE_PASSCODE" -> handleSetPhonePasscode(phone, p1);
            case "VERIFY_PHONE_PASSCODE" -> handleVerifyPhonePasscode(phone, p1);
            case "CHANGE_PHONE_PASSCODE" -> handleChangePhonePasscode(phone, p1, p2, p3);
            case "SET_BANK_THEME" -> handleSetBankTheme(phone, p1);
            case "MARKET_REFRESH" -> "Spot market refreshed.";
            case "SET_PROFILE_NAME" -> handleSetProfileName(phone, p1);
            case "SET_TAP_ACCOUNT" -> handleSetTapAccount(player, phone, p1);
            case "SET_PRIMARY" -> handleSetPrimary(player, p1);
            case "OPEN_DEFAULT_ACCOUNT" -> handleOpenDefaultAccount(player, p1);
            case "REMEMBER_PAYMENT_TARGET" -> handleRememberPaymentTarget(player, p1, p2);
            case "TRANSFER" -> handleTransfer(player, p1, p2, p3);
            case "REQUEST_MONEY" -> handleRequestMoney(player, p1, p2, p3);
            case "BANK_HIRE" -> handleBankStaffAction(player, "HIRE", p1, p2, p3);
            case "BANK_FIRE" -> handleBankStaffAction(player, "FIRE", p1, p2, p3);
            case "DISBAND_BANK" -> handleDisbandBank(player, p1, p2);
            case "SAVE_NOTE" -> handleSaveNote(phone, p1, p2);
            case "DELETE_NOTE" -> handleDeleteNote(phone, p1);
            case "SAVE_PAINTING" -> handleSavePainting(phone, p1, p2);
            case "SEND_MESSAGE" -> handleSendMessage(player, p1, p2);
            case "MESSAGE_TYPING" -> handleMessengerTyping(player, p1, p2);
            case "MESSAGE_PAY_REQUEST_CREATE" -> handleMessengerPayRequestCreate(player, p1, p2, p3);
            case "MESSAGE_PAY_REQUEST_ACCEPT" -> handleMessengerPayRequestAction(player, p1, p2, true);
            case "MESSAGE_PAY_REQUEST_DECLINE" -> handleMessengerPayRequestAction(player, p1, "", false);
            case "MESSAGE_GIFT_CREATE" -> handleMessengerGiftCreate(player, p1, p2, p3);
            case "MESSAGE_GIFT_ACCEPT" -> handleMessengerGiftAction(player, p1, p2, true);
            case "MESSAGE_GIFT_DECLINE" -> handleMessengerGiftAction(player, p1, "", false);
            case "READ_CONVERSATION" -> handleReadConversation(player, p1);
            case "FAVORITE_CONTACT" -> handleFavorite(player, p1, true);
            case "UNFAVORITE_CONTACT" -> handleFavorite(player, p1, false);
            case "MUTE_CONTACT" -> handleMute(player, p1, true);
            case "UNMUTE_CONTACT" -> handleMute(player, p1, false);
            case "BLOCK_CONTACT" -> handleBlock(player, p1, true);
            case "UNBLOCK_CONTACT" -> handleBlock(player, p1, false);
            case "REPORT_CONTACT" -> handleReport(player, p1, p2);
            default -> "Unknown phone action.";
        };
        SmartphoneSnapshotPayload snapshot = buildSnapshot(player, phone, false);
        return new SmartphoneSnapshotPayload(snapshot.open(), snapshot.animate(), status, snapshot.lines());
    }

    public static ItemStack findUsablePhone(ServerPlayer player) {
        return SmartphoneData.findInventoryPhone(player);
    }

    public static UUID resolveTapAccountId(ServerPlayer player) {
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            return null;
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            return null;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return null;
        }
        AccountHolder account = resolveTapAccount(player, centralBank, phone);
        return account == null ? null : account.getAccountUUID();
    }

    public static boolean canUsePhone(ItemStack phone, ServerPlayer player) {
        if (phone == null || phone.isEmpty() || player == null) {
            return false;
        }
        String mode = Config.PHONE_ACCESS_MODE.get();
        if ("OPEN_ACCESS".equalsIgnoreCase(mode)) {
            return true;
        }
        return SmartphoneData.isOwner(phone, player);
    }

    private static SmartphoneSnapshotPayload buildSnapshot(ServerPlayer player, ItemStack phone, boolean animate) {
        List<String> lines = new ArrayList<>();
        lines.add(token("owner", player.getUUID(), player.getGameProfile().getName(), SmartphoneData.getOwnerName(phone)));
        lines.add(token("theme", SmartphoneData.getAccent(phone), SmartphoneData.getWallpaper(phone), SmartphoneData.getLayout(phone)));
        lines.add(token("security", SmartphoneData.hasPasscode(phone)));
        lines.add(token("phone_access", Config.PHONE_ACCESS_MODE.get()));
        lines.add(token("server_time", System.currentTimeMillis(), ZoneId.systemDefault().getId()));
        lines.add(token("app", "banking", "Banking", "UBS accounts and payments"));
        lines.add(token("app", "tap", "Tap to Pay", "Default phone payment card"));
        lines.add(token("app", "market", "Spot Market", "Global bullion desk"));
        lines.add(token("app", "calculator", "Calculator", "Fast arithmetic"));
        lines.add(token("app", "paint", "Paint", "Pixel sketch pad"));
        lines.add(token("app", "contacts", "Contacts", "Server player directory"));
        lines.add(token("app", "messenger", "Messenger", "Private phone messages"));
        lines.add(token("app", "notes", "Notes", "Private phone notes"));
        lines.add(token("app", "settings", "Settings", "Phone customization"));
        for (CommodityMarketService.PhoneMarketQuote quote : CommodityMarketService.phoneQuotes(player.getServer())) {
            lines.add(token("market_quote",
                    quote.id(),
                    quote.displayName(),
                    quote.unitName(),
                    quote.spotLabel(),
                    quote.bidLabel(),
                    quote.askLabel(),
                    quote.changeLabel(),
                    quote.highLabel(),
                    quote.lowLabel(),
                    quote.source(),
                    quote.updatedAtMillis(),
                    quote.seeded(),
                    quote.formula(),
                    quote.confidenceLabel()));
        }

        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank != null) {
            List<AccountHolder> accounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
            accounts.sort(Comparator
                    .comparing(AccountHolder::isPrimaryAccount).reversed()
                    .thenComparing(account -> bankName(centralBank, account))
                    .thenComparing(account -> String.valueOf(account.getAccountType())));
            lines.add(token("bank_state", accounts.size(), centralBank.getBankName(), centralBank.getBankId()));
            AccountHolder tap = resolveTapAccount(player, centralBank, phone);
            for (AccountHolder account : accounts) {
                lines.add(token("app",
                        "account:" + account.getAccountUUID(),
                        bankName(centralBank, account) + " " + accountType(account),
                        MoneyText.abbreviateWithDollar(account.getBalance())));
                lines.add(token("account",
                        account.getAccountUUID(),
                        bankName(centralBank, account),
                        accountType(account),
                        MoneyText.abbreviateWithDollar(account.getBalance()),
                        account.getBalance().toPlainString(),
                        account.isPrimaryAccount(),
                        account.isFrozen(),
                        account.getCreditScore(),
                        account.getAccountAccessType(),
                        account.getBusinessLabel(),
                        account.getRole(player.getUUID()),
                        tap != null && tap.getAccountUUID().equals(account.getAccountUUID()),
                        SmartphoneData.getOrCreateVirtualCardNumber(phone, account.getAccountUUID()),
                        account.hasPin()));
            }
            for (AccountHolder account : accounts) {
                account.getTransactions().values().stream()
                        .filter(UserTransaction.class::isInstance)
                        .map(UserTransaction.class::cast)
                        .sorted(Comparator.<UserTransaction, LocalDateTime>comparing(tx ->
                                tx.getTimestamp() == null ? LocalDateTime.MIN : tx.getTimestamp()).reversed())
                        .limit(120)
                        .forEach(tx -> lines.add(token("tx",
                                account.getAccountUUID(),
                                tx.getTransactionUUID(),
                                signedAmount(account, tx),
                                tx.getTransactionDescription(),
                                tx.getTimestamp() == null ? "" : tx.getTimestamp().format(DateTimeFormatter.ofPattern("MM/dd HH:mm")),
                                txMillis(tx),
                                tx.getSenderUUID() != null && tx.getSenderUUID().equals(account.getAccountUUID()) ? "OUT" : "IN",
                                counterpartyLabel(player.getServer(), centralBank, account, tx),
                                tx.getSenderUUID(),
                                tx.getReceiverUUID())));
            }

            Bank ownedBank = resolveOwnedBank(centralBank, player.getUUID());
            if (ownedBank != null) {
                CompoundTag metadata = centralBank.getOrCreateBankMetadata(ownedBank.getBankId());
                lines.add(token("owned_bank",
                        ownedBank.getBankId(),
                        ownedBank.getBankName(),
                        metadata.getString("status").isBlank() ? "ACTIVE" : metadata.getString("status"),
                        countEncodedEntries(metadata.getString("employees"))));
                decodeEmployeeMap(metadata.getString("employees")).forEach((employeeId, spec) ->
                        lines.add(token("bank_staff",
                                employeeId,
                                resolvePlayerName(player.getServer(), employeeId),
                                spec.role(),
                                MoneyText.abbreviateWithDollar(spec.salary()))));
            }
        }

        SmartphoneSavedData savedData = SmartphoneSavedData.get(player.getServer());
        pruneExpiredGifts(player.getServer(), centralBank, savedData);
        SmartphoneSavedData.PlayerPrefs prefs = savedData.prefs(player.getUUID());
        if (centralBank != null) {
            appendPaymentRecipients(player, centralBank, prefs, lines);
        }
        player.getServer().getPlayerList().getPlayers().stream()
                .filter(serverPlayer -> !serverPlayer.getUUID().equals(player.getUUID()))
                .sorted(Comparator.comparing(serverPlayer -> serverPlayer.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(serverPlayer -> lines.add(token("contact",
                        serverPlayer.getUUID(),
                        serverPlayer.getGameProfile().getName(),
                        true,
                        prefs.favorites.contains(serverPlayer.getUUID()),
                        prefs.muted.contains(serverPlayer.getUUID()),
                        prefs.blocked.contains(serverPlayer.getUUID()),
                        prefs.unread.getOrDefault(serverPlayer.getUUID(), 0))));
        Set<UUID> knownContacts = new LinkedHashSet<>(prefs.knownContacts);
        knownContacts.remove(player.getUUID());
        for (UUID known : knownContacts) {
            if (player.getServer().getPlayerList().getPlayer(known) == null) {
                lines.add(token("contact", known, resolveKnownContactName(player, savedData, known), false,
                        prefs.favorites.contains(known), prefs.muted.contains(known),
                        prefs.blocked.contains(known), prefs.unread.getOrDefault(known, 0)));
            }
        }
        for (UUID other : knownContacts) {
            List<SmartphoneSavedData.MessageEntry> messages = new ArrayList<>(savedData.conversation(player.getUUID(), other));
            messages.sort(Comparator
                    .comparingLong(SmartphoneSavedData.MessageEntry::createdAt)
                    .thenComparing(message -> message.id().toString()));
            int from = Math.max(0, messages.size() - 120);
            for (int i = from; i < messages.size(); i++) {
                SmartphoneSavedData.MessageEntry message = messages.get(i);
                PayRequestMarker marker = parsePayRequestMarker(message.body());
                GiftMarker giftMarker = parseGiftMarker(message.body());
                if (marker == null && giftMarker == null) {
                    lines.add(token("message",
                            other,
                            message.id(),
                            message.senderId(),
                            message.senderName(),
                            message.body(),
                            SHORT_TIME.format(Instant.ofEpochMilli(message.createdAt())),
                            message.createdAt()));
                } else if (marker != null) {
                    PayRequestManager.PayRequest request = PayRequestManager.getRequest(marker.requestId());
                    String amount = request == null ? marker.amount() : request.getAmount().toPlainString();
                    String status = request == null ? "EXPIRED" : request.getStatus().name();
                    UUID requester = request == null ? message.senderId() : request.getRequesterUUID();
                    UUID payer = request == null ? message.recipientId() : request.getPayerUUID();
                    lines.add(token("message_pay_request",
                            other,
                            message.id(),
                            message.senderId(),
                            message.senderName(),
                            "Pay request",
                            SHORT_TIME.format(Instant.ofEpochMilli(message.createdAt())),
                            message.createdAt(),
                            marker.requestId(),
                            amount,
                            status,
                            requester,
                            payer));
                } else {
                    SmartphoneSavedData.GiftEntry gift = savedData.gift(giftMarker.giftId());
                    String amount = gift == null ? giftMarker.amount() : gift.amountDecimal().toPlainString();
                    String status = gift == null ? "EXPIRED" : gift.status();
                    UUID sender = gift == null ? message.senderId() : gift.senderId();
                    UUID recipient = gift == null ? message.recipientId() : gift.recipientId();
                    lines.add(token("message_gift",
                            other,
                            message.id(),
                            message.senderId(),
                            message.senderName(),
                            "Gift",
                            SHORT_TIME.format(Instant.ofEpochMilli(message.createdAt())),
                            message.createdAt(),
                            giftMarker.giftId(),
                            amount,
                            status,
                            sender,
                            recipient));
                }
            }
        }
        appendTypingIndicators(player, savedData, lines);

        List<String> notes = SmartphoneData.getNotes(phone);
        for (int i = 0; i < notes.size(); i++) {
            lines.add(token("note", i, notes.get(i)));
        }
        List<String> paintings = SmartphoneData.getPaintings(phone);
        for (int i = 0; i < paintings.size(); i++) {
            lines.add(token("painting", i, paintings.get(i)));
        }
        return new SmartphoneSnapshotPayload(true, animate, "", lines);
    }

    private static AccountHolder resolveTapAccount(ServerPlayer player, CentralBank centralBank, ItemStack phone) {
        UUID selected = SmartphoneData.getTapAccount(phone);
        if (selected != null) {
            AccountHolder account = centralBank.SearchForAccountByAccountId(selected);
            if (account != null && player.getUUID().equals(account.getPlayerUUID())) {
                return account;
            }
        }
        return centralBank.SearchForAccount(player.getUUID()).values().stream()
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst()
                .or(() -> centralBank.SearchForAccount(player.getUUID()).values().stream().findFirst())
                .orElse(null);
    }

    private static String handleSetTheme(ItemStack phone, String accent, String wallpaper) {
        SmartphoneData.setTheme(phone, accent, wallpaper);
        return "Phone theme updated.";
    }

    private static String handleSetPhonePasscode(ItemStack phone, String passcode) {
        if (SmartphoneData.hasPasscode(phone)) {
            return "Phone passcode already set.";
        }
        if (!SmartphoneData.setPasscode(phone, passcode)) {
            return "Use a 4 digit phone passcode.";
        }
        return "Phone passcode set.";
    }

    private static String handleVerifyPhonePasscode(ItemStack phone, String passcode) {
        if (!SmartphoneData.hasPasscode(phone)) {
            return "Set a phone passcode first.";
        }
        return SmartphoneData.verifyPasscode(phone, passcode)
                ? "Phone unlocked."
                : "Incorrect phone passcode.";
    }

    private static String handleChangePhonePasscode(ItemStack phone, String currentPasscode, String newPasscode, String confirmPasscode) {
        if (!SmartphoneData.hasPasscode(phone)) {
            return "Set a phone passcode first.";
        }
        String next = newPasscode == null ? "" : newPasscode.trim();
        String confirm = confirmPasscode == null ? "" : confirmPasscode.trim();
        if (!next.matches("\\d{4}") || !confirm.matches("\\d{4}")) {
            return "Use a 4 digit phone passcode.";
        }
        if (!next.equals(confirm)) {
            return "Phone passcodes do not match.";
        }
        if (!SmartphoneData.changePasscode(phone, currentPasscode, next)) {
            return "Current phone passcode is incorrect.";
        }
        return "Phone passcode changed.";
    }

    private static String handleSetBankTheme(ItemStack phone, String theme) {
        String normalized = "light".equalsIgnoreCase(theme == null ? "" : theme.trim()) ? "light" : "dark";
        SmartphoneData.setLayout(phone, normalized);
        return "Banking theme set to " + normalized + ".";
    }

    private static String handleSetProfileName(ItemStack phone, String name) {
        if (name == null || name.isBlank()) {
            return "Profile name cannot be empty.";
        }
        SmartphoneData.setOwnerName(phone, name);
        return "Banking profile updated.";
    }

    private static String handleSetTapAccount(ServerPlayer player, ItemStack phone, String accountIdRaw) {
        UUID accountId = parseUuid(accountIdRaw);
        if (accountId == null) {
            return "Select a valid account first.";
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        AccountHolder account = centralBank == null ? null : centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !player.getUUID().equals(account.getPlayerUUID())) {
            return "That account does not belong to this phone user.";
        }
        SmartphoneData.setTapAccount(phone, accountId);
        return "Tap to Pay now uses " + accountType(account) + ".";
    }

    private static String handleSetPrimary(ServerPlayer player, String accountIdRaw) {
        UUID accountId = parseUuid(accountIdRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || accountId == null) {
            return "Account data is unavailable.";
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null || !player.getUUID().equals(account.getPlayerUUID())) {
            return "That account does not belong to you.";
        }
        if (!centralBank.setPrimaryAccountForPlayer(player.getUUID(), accountId, true)) {
            return "Could not set primary account.";
        }
        return "Primary account updated.";
    }

    private static String handleOpenDefaultAccount(ServerPlayer player, String pinRaw) {
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return "Central Bank is unavailable.";
        }
        String pin = pinRaw == null ? "" : pinRaw.trim();
        if (!pin.matches("\\d{4}")) {
            return "Choose a 4-digit PIN to create your account.";
        }

        List<AccountHolder> playerAccounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
        int existingAccountCount = playerAccounts.size();
        AccountHolder existing = centralBank.SearchForAccount(player.getUUID()).values().stream()
                .filter(account -> account != null && centralBank.getBankId().equals(account.getBankId()))
                .filter(account -> account.getAccountType() == AccountTypes.CheckingAccount)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            if (existing.hasPin()) {
                if (existingAccountCount == 1 && !existing.isPrimaryAccount()) {
                    existing.setPrimaryAccount(true);
                }
                BankManager.markDirty();
                return "Central Bank checking account already exists. Sign in with its PIN.";
            }
            existing.setPin(pin);
            if (existingAccountCount == 1 && !existing.isPrimaryAccount()) {
                existing.setPrimaryAccount(true);
            }
            BankManager.markDirty();
            return "Central Bank checking account ready.";
        }

        AccountHolder account = new AccountHolder(
                player.getUUID(),
                BigDecimal.ZERO,
                AccountTypes.CheckingAccount,
                pin,
                centralBank.getBankId(),
                null
        );
        if (!centralBank.AddAccount(account)) {
            return "You already have a Central Bank checking account.";
        }
        if (existingAccountCount == 0) {
            account.setPrimaryAccount(true);
        }
        BankManager.markDirty();
        return "Created Central Bank checking account.";
    }

    private static String handleRememberPaymentTarget(ServerPlayer player, String targetRaw, String flowRaw) {
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return "Bank data is unavailable.";
        }
        PaymentDestination resolved = resolvePaymentDestination(player, centralBank, targetRaw);
        if (resolved.account() == null) {
            return resolved.message();
        }
        boolean requestFlow = "request".equalsIgnoreCase(flowRaw);
        if (requestFlow && (resolved.accountIdDirect() || resolved.playerId() == null)) {
            return "Requests need a player with a primary account.";
        }
        rememberPaymentTarget(player, resolved.account(), resolved.accountIdDirect());
        return "Recipient added.";
    }

    private static String handleTransfer(ServerPlayer player, String fromRaw, String toRaw, String amountRaw) {
        UUID from = parseUuid(fromRaw);
        BigDecimal amount = parseAmount(amountRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || from == null || amount == null) {
            return "Transfer needs source, destination, and amount.";
        }
        AccountHolder source = centralBank.SearchForAccountByAccountId(from);
        PaymentDestination resolved = resolvePaymentDestination(player, centralBank, toRaw);
        AccountHolder destination = resolved.account();
        if (source == null || destination == null) {
            return resolved.message();
        }
        if (!player.getUUID().equals(source.getPlayerUUID()) && !source.canWithdraw(player.getUUID())) {
            return "You do not have withdrawal access on the source account.";
        }
        if (from.equals(destination.getAccountUUID())) {
            return "You cannot transfer to the same account.";
        }
        boolean success = new UserTransaction(from, destination.getAccountUUID(), amount, LocalDateTime.now(), "PHONE_TRANSFER").makeTransaction(player.getServer());
        if (!success) {
            return "Transfer failed.";
        }
        rememberPaymentTarget(player, destination, resolved.accountIdDirect());
        return "Transferred " + MoneyText.abbreviateWithDollar(amount) + ".";
    }

    private static String handleRequestMoney(ServerPlayer player, String accountRaw, String targetRaw, String amountRaw) {
        UUID destinationAccountId = parseUuid(accountRaw);
        BigDecimal amount = parseAmount(amountRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || destinationAccountId == null || amount == null) {
            return "Request needs destination account, player, and amount.";
        }
        AccountHolder destination = centralBank.SearchForAccountByAccountId(destinationAccountId);
        if (destination == null || !player.getUUID().equals(destination.getPlayerUUID())) {
            return "Choose one of your accounts as the request destination.";
        }
        PaymentDestination payerTarget = resolvePaymentDestination(player, centralBank, targetRaw);
        if (payerTarget.account() == null) {
            return payerTarget.message() == null || payerTarget.message().isBlank()
                    ? "Requests need a player with a primary account."
                    : payerTarget.message();
        }
        if (payerTarget.playerId() == null) {
            return "Requests need an account with a player owner.";
        }
        UUID payerId = payerTarget.playerId();
        if (payerId.equals(player.getUUID())) {
            return "You cannot request money from yourself.";
        }
        if (!payerTarget.accountIdDirect() && findPrimaryAccount(centralBank, payerId) == null) {
            return resolvePlayerName(player.getServer(), payerId) + " has no primary account.";
        }

        PayRequestManager.createRequest(player.getUUID(), payerId, destination.getAccountUUID(), amount);
        rememberPaymentTarget(player, payerTarget.account(), payerTarget.accountIdDirect());
        notifyPhone(player.getServer(), payerId, "Pay Request",
                player.getGameProfile().getName() + " requested " + MoneyText.abbreviateWithDollar(amount) + ".",
                DeliveryAlertPayload.AlertTone.INFO, 5200);
        return "Pay request sent to " + resolvePlayerName(player.getServer(), payerId) + ".";
    }

    private static String handleMessengerPayRequestCreate(ServerPlayer player,
                                                          String accountRaw,
                                                          String payerRaw,
                                                          String amountRaw) {
        UUID destinationAccountId = parseUuid(accountRaw);
        UUID payerId = parseUuid(payerRaw);
        BigDecimal amount = parseAmount(amountRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || destinationAccountId == null || payerId == null || amount == null) {
            return "Pay request needs an account, contact, and amount.";
        }
        AccountHolder destination = centralBank.SearchForAccountByAccountId(destinationAccountId);
        if (destination == null || !player.getUUID().equals(destination.getPlayerUUID())) {
            return "Choose one of your accounts as the request destination.";
        }
        if (payerId.equals(player.getUUID())) {
            return "You cannot request money from yourself.";
        }
        ServerPlayer payer = player.getServer().getPlayerList().getPlayer(payerId);
        String payerName = payer == null ? resolvePlayerName(player.getServer(), payerId) : payer.getGameProfile().getName();
        AccountHolder payerPrimary = findPrimaryAccount(centralBank, payerId);
        if (payerPrimary == null) {
            return payerName + " has no primary account.";
        }

        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        if (data.prefs(player.getUUID()).blocked.contains(payerId)
                || data.prefs(payerId).blocked.contains(player.getUUID())) {
            return "Pay request not sent.";
        }

        PayRequestManager.PayRequest request = PayRequestManager.createRequest(
                player.getUUID(),
                payerId,
                destination.getAccountUUID(),
                amount);
        boolean sent = data.sendMessage(player.getUUID(), player.getGameProfile().getName(), payerId,
                payerName, payRequestMarker(request.getRequestId(), amount));
        if (!sent) {
            PayRequestManager.markDeclined(request.getRequestId());
            return "Pay request not sent.";
        }
        rememberPaymentTarget(player, payerPrimary, false);
        if (payer != null) {
            pushLiveRefresh(payer, "");
        }
        notifyPhone(player.getServer(), payerId, "Pay Request",
                player.getGameProfile().getName() + " requested " + MoneyText.abbreviateWithDollar(amount) + ".",
                DeliveryAlertPayload.AlertTone.INFO, 5200);
        return "Pay request sent to " + payerName + ".";
    }

    private static String handleMessengerPayRequestAction(ServerPlayer player,
                                                          String requestRaw,
                                                          String accountRaw,
                                                          boolean accept) {
        UUID requestId = parseUuid(requestRaw);
        if (requestId == null) {
            return "Choose a valid pay request.";
        }
        PayRequestManager.PayRequest request = PayRequestManager.getRequest(requestId);
        if (request == null) {
            return "Pay request expired or missing.";
        }
        if (!request.getPayerUUID().equals(player.getUUID())) {
            return "This request is not for you.";
        }
        if (request.getStatus() != PayRequestManager.Status.PENDING || PayRequestManager.isExpired(request)) {
            PayRequestManager.pruneExpired();
            pushLiveRefresh(player.getServer().getPlayerList().getPlayer(request.getRequesterUUID()), "");
            return "Pay request is no longer pending.";
        }

        ServerPlayer requester = player.getServer().getPlayerList().getPlayer(request.getRequesterUUID());
        if (!accept) {
            PayRequestManager.markDeclined(request.getRequestId());
            if (requester != null) {
                pushLiveRefresh(requester, "");
            }
            notifyPhone(player.getServer(), request.getRequesterUUID(), "Pay Request",
                    player.getGameProfile().getName() + " declined your request for "
                            + MoneyText.abbreviateWithDollar(request.getAmount()) + ".",
                    DeliveryAlertPayload.AlertTone.WARNING, 5200);
            return "Request declined.";
        }

        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null) {
            return "Bank data is unavailable.";
        }
        AccountHolder sender = null;
        UUID senderId = parseUuid(accountRaw);
        if (senderId != null) {
            AccountHolder candidate = centralBank.SearchForAccountByAccountId(senderId);
            if (candidate != null && candidate.getPlayerUUID().equals(player.getUUID())) {
                sender = candidate;
            }
        }
        if (sender == null) {
            sender = findPrimaryAccount(centralBank, player.getUUID());
        }
        if (sender == null) {
            return "No account available to pay this request.";
        }
        AccountHolder receiver = centralBank.SearchForAccountByAccountId(request.getReceiverAccountUUID());
        if (receiver == null) {
            if (requester != null) {
                pushLiveRefresh(requester, "");
            }
            notifyPhone(player.getServer(), request.getRequesterUUID(), "Pay Request",
                    "Your pay request could not be completed because its destination account is unavailable.",
                    DeliveryAlertPayload.AlertTone.ERROR, 5200);
            return "Requester destination account is unavailable.";
        }
        if (sender.getAccountUUID().equals(receiver.getAccountUUID())) {
            return "Cannot pay the same account.";
        }

        boolean success = new UserTransaction(
                sender.getAccountUUID(),
                receiver.getAccountUUID(),
                request.getAmount(),
                LocalDateTime.now(),
                "Pay Request"
        ).makeTransaction(player.getServer());
        if (!success) {
            if (requester != null) {
                pushLiveRefresh(requester, "");
            }
            notifyPhone(player.getServer(), request.getRequesterUUID(), "Pay Request",
                    player.getGameProfile().getName() + " tried to pay your request, but payment failed.",
                    DeliveryAlertPayload.AlertTone.WARNING, 5200);
            return "Payment failed. Check balance/account status.";
        }

        PayRequestManager.markAccepted(request.getRequestId());
        if (requester != null) {
            pushLiveRefresh(requester, "");
        }
        notifyPhone(player.getServer(), request.getRequesterUUID(), "Pay Request",
                player.getGameProfile().getName() + " paid " + MoneyText.abbreviateWithDollar(request.getAmount()) + ".",
                DeliveryAlertPayload.AlertTone.SUCCESS, 5200);
        return MoneyText.abbreviateCurrencyTokens(
                "Paid $" + request.getAmount().toPlainString() + " using " + accountType(sender) + ".");
    }

    private static String handleMessengerGiftCreate(ServerPlayer player,
                                                    String accountRaw,
                                                    String recipientRaw,
                                                    String amountRaw) {
        UUID sourceAccountId = parseUuid(accountRaw);
        UUID recipientId = parseUuid(recipientRaw);
        BigDecimal amount = parseAmount(amountRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        pruneExpiredGifts(player.getServer(), centralBank, data);
        if (centralBank == null || sourceAccountId == null || recipientId == null || amount == null) {
            return "Gift needs an account, contact, and amount.";
        }
        AccountHolder source = centralBank.SearchForAccountByAccountId(sourceAccountId);
        if (source == null || !source.canWithdraw(player.getUUID())) {
            return "Choose one of your withdrawable accounts.";
        }
        if (recipientId.equals(player.getUUID())) {
            return "You cannot gift money to yourself.";
        }
        ServerPlayer recipient = player.getServer().getPlayerList().getPlayer(recipientId);
        String recipientName = recipient == null ? resolvePlayerName(player.getServer(), recipientId) : recipient.getGameProfile().getName();
        AccountHolder recipientPrimary = findPrimaryAccount(centralBank, recipientId);
        if (recipientPrimary == null) {
            return recipientName + " has no primary account.";
        }
        if (data.prefs(player.getUUID()).blocked.contains(recipientId)
                || data.prefs(recipientId).blocked.contains(player.getUUID())) {
            return "Gift not sent.";
        }
        if (!source.RemoveBalance(amount)) {
            return "Gift failed. Check balance/account status.";
        }

        SmartphoneSavedData.GiftEntry gift = data.createGift(player.getUUID(), recipientId,
                source.getAccountUUID(), amount);
        if (gift == null) {
            source.forceAddBalance(amount);
            return "Gift not sent.";
        }
        boolean sent = data.sendMessage(player.getUUID(), player.getGameProfile().getName(), recipientId,
                recipientName, giftMarker(gift.id(), amount));
        if (!sent) {
            data.markGiftStatus(gift.id(), "DECLINED");
            source.forceAddBalance(amount);
            return "Gift not sent.";
        }
        rememberPaymentTarget(player, recipientPrimary, false);
        if (recipient != null) {
            pushLiveRefresh(recipient, "");
        }
        notifyPhone(player.getServer(), recipientId, "Gift",
                player.getGameProfile().getName() + " gifted " + MoneyText.abbreviateWithDollar(amount) + ".",
                DeliveryAlertPayload.AlertTone.SUCCESS, 5200);
        return "Gift sent to " + recipientName + ".";
    }

    private static String handleMessengerGiftAction(ServerPlayer player,
                                                    String giftRaw,
                                                    String accountRaw,
                                                    boolean accept) {
        UUID giftId = parseUuid(giftRaw);
        if (giftId == null) {
            return "Choose a valid gift.";
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        pruneExpiredGifts(player.getServer(), centralBank, data);
        SmartphoneSavedData.GiftEntry gift = data.gift(giftId);
        if (gift == null) {
            return "Gift expired or missing.";
        }
        if (!gift.recipientId().equals(player.getUUID())) {
            return "This gift is not for you.";
        }
        if (!"PENDING".equalsIgnoreCase(gift.status())) {
            return "Gift is no longer pending.";
        }
        ServerPlayer sender = player.getServer().getPlayerList().getPlayer(gift.senderId());
        if (!accept) {
            if (data.markGiftStatus(gift.id(), "DECLINED")) {
                refundGift(player.getServer(), centralBank, gift);
                if (sender != null) {
                    pushLiveRefresh(sender, "");
                }
                notifyPhone(player.getServer(), gift.senderId(), "Gift",
                        player.getGameProfile().getName() + " declined your gift of "
                                + MoneyText.abbreviateWithDollar(gift.amountDecimal()) + ".",
                        DeliveryAlertPayload.AlertTone.WARNING, 5200);
            }
            return "Gift declined.";
        }
        if (centralBank == null) {
            return "Bank data is unavailable.";
        }
        AccountHolder destination = null;
        UUID destinationId = parseUuid(accountRaw);
        if (destinationId != null) {
            AccountHolder candidate = centralBank.SearchForAccountByAccountId(destinationId);
            if (candidate != null && candidate.getPlayerUUID().equals(player.getUUID()) && candidate.canDeposit(player.getUUID())) {
                destination = candidate;
            }
        }
        if (destination == null) {
            destination = findPrimaryAccount(centralBank, player.getUUID());
        }
        if (destination == null) {
            return "No account available to receive this gift.";
        }
        BigDecimal amount = gift.amountDecimal();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            data.markGiftStatus(gift.id(), "EXPIRED");
            return "Gift amount is invalid.";
        }
        if (destination.isFrozen()) {
            return "Destination account cannot receive this gift.";
        }
        if (!data.markGiftStatus(gift.id(), "ACCEPTED")) {
            return "Gift is no longer pending.";
        }
        destination.forceAddBalance(amount);
        AccountHolder source = centralBank.SearchForAccountByAccountId(gift.sourceAccountId());
        UserTransaction tx = new UserTransaction(
                gift.sourceAccountId(),
                destination.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "Messenger Gift"
        );
        if (source != null) {
            source.addTransaction(tx);
        }
        destination.addTransaction(tx);
        BankManager.markDirty();
        if (sender != null) {
            pushLiveRefresh(sender, "");
        }
        notifyPhone(player.getServer(), gift.senderId(), "Gift",
                player.getGameProfile().getName() + " accepted "
                        + MoneyText.abbreviateWithDollar(amount) + ".",
                DeliveryAlertPayload.AlertTone.SUCCESS, 5200);
        return "Gift accepted.";
    }

    private static String handleBankStaffAction(ServerPlayer player, String action, String bankRaw, String arg1, String arg2) {
        UUID bankId = parseUuid(bankRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || bankId == null) {
            return "Bank data is unavailable.";
        }
        String normalized = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        String role = arg2;
        String salary = "0";
        if ("HIRE".equals(normalized) && arg2 != null) {
            int split = arg2.indexOf('|');
            if (split >= 0) {
                role = arg2.substring(0, split);
                salary = arg2.substring(split + 1);
            }
        }
        BankOwnerPcService.ActionResult result = BankOwnerPcService.executeAction(
                player.getServer(),
                centralBank,
                player,
                bankId,
                normalized,
                arg1,
                role,
                "HIRE".equals(normalized) ? salary : "",
                ""
        );
        return result.message();
    }

    private static String handleDisbandBank(ServerPlayer player, String bankRaw, String confirmationRaw) {
        UUID bankId = parseUuid(bankRaw);
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        if (centralBank == null || bankId == null) {
            return "Bank data is unavailable.";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null || bank.getBankId().equals(centralBank.getBankId())) {
            return "That bank cannot be dissolved.";
        }
        if (!player.getUUID().equals(bank.getBankOwnerId())) {
            return "Only the bank owner can dissolve this bank.";
        }
        String confirmation = confirmationRaw == null ? "" : confirmationRaw.trim();
        if (!"DISSOLVE".equalsIgnoreCase(confirmation)) {
            return "Type DISSOLVE to confirm bank removal.";
        }

        BigDecimal movedAmount = BigDecimal.ZERO;
        int movedAccounts = 0;
        for (AccountHolder account : new ArrayList<>(bank.getBankAccounts().values())) {
            if (account == null) {
                continue;
            }
            BigDecimal balance = account.getBalance();
            if (balance.compareTo(BigDecimal.ZERO) > 0) {
                AccountHolder destination = findOrCreateCentralCheckingAccount(centralBank, account.getPlayerUUID());
                if (destination != null && account.forceRemoveBalance(balance) && destination.forceAddBalance(balance)) {
                    movedAmount = movedAmount.add(balance);
                    movedAccounts++;
                }
            }
            bank.RemoveAccount(account);
        }

        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bank.getBankId());
        metadata.putString("status", "DISBANDED");
        metadata.putString("revokeReason", "Owner dissolved bank from phone app");
        metadata.putLong("revokedAtMillis", System.currentTimeMillis());
        centralBank.putBankMetadata(bank.getBankId(), metadata);
        centralBank.removeBank(bank);
        return "Dissolved " + bank.getBankName() + ". Moved " + MoneyText.abbreviateWithDollar(movedAmount)
                + " across " + movedAccounts + " account(s).";
    }

    private static void appendPaymentRecipients(ServerPlayer player,
                                                CentralBank centralBank,
                                                SmartphoneSavedData.PlayerPrefs prefs,
                                                List<String> lines) {
        Set<UUID> emittedPlayers = new HashSet<>();
        for (UUID recentPlayer : prefs.paymentRecentPlayers) {
            ServerPlayer online = player.getServer().getPlayerList().getPlayer(recentPlayer);
            if (online == null || online.getUUID().equals(player.getUUID())) {
                continue;
            }
            AccountHolder primary = findPrimaryAccount(centralBank, online.getUUID());
            if (primary != null) {
                emittedPlayers.add(online.getUUID());
                lines.add(token("payment_contact", online.getUUID(), online.getGameProfile().getName(),
                        true, primary.getAccountUUID(), true, "player"));
            }
        }

        for (UUID accountId : prefs.paymentRecentAccounts) {
            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
            if (account != null && !account.getPlayerUUID().equals(player.getUUID())) {
                lines.add(token("payment_contact", accountId, "Acct " + shortId(accountId),
                        false, accountId, true, "account"));
            }
        }

        player.getServer().getPlayerList().getPlayers().stream()
                .filter(online -> !online.getUUID().equals(player.getUUID()))
                .sorted(Comparator.comparing(online -> online.getGameProfile().getName(), String.CASE_INSENSITIVE_ORDER))
                .forEach(online -> {
                    if (emittedPlayers.contains(online.getUUID())) {
                        return;
                    }
                    AccountHolder primary = findPrimaryAccount(centralBank, online.getUUID());
                    if (primary != null) {
                        lines.add(token("payment_contact", online.getUUID(), online.getGameProfile().getName(),
                                true, primary.getAccountUUID(), false, "player"));
                    }
                });
    }

    private static PaymentDestination resolvePaymentDestination(ServerPlayer actor, CentralBank centralBank, String raw) {
        String target = raw == null ? "" : raw.trim();
        if (target.isBlank()) {
            return PaymentDestination.error("Choose a player or account ID.");
        }

        UUID id = parseUuid(target);
        if (id != null) {
            AccountHolder directAccount = centralBank.SearchForAccountByAccountId(id);
            if (directAccount != null) {
                return new PaymentDestination(directAccount, directAccount.getPlayerUUID(), true, "");
            }
            ServerPlayer byId = actor.getServer().getPlayerList().getPlayer(id);
            if (byId != null) {
                return resolvePlayerPrimary(centralBank, byId);
            }
            AccountHolder offlinePrimary = findPrimaryAccount(centralBank, id);
            if (offlinePrimary != null) {
                return new PaymentDestination(offlinePrimary, id, false, "");
            }
        }

        ServerPlayer byName = actor.getServer().getPlayerList().getPlayerByName(target);
        if (byName != null) {
            return resolvePlayerPrimary(centralBank, byName);
        }
        UUID offlineId = resolvePlayerIdByName(actor.getServer(), target);
        if (offlineId != null) {
            AccountHolder offlinePrimary = findPrimaryAccount(centralBank, offlineId);
            if (offlinePrimary != null) {
                return new PaymentDestination(offlinePrimary, offlineId, false, "");
            }
            return PaymentDestination.error(target + " has no primary account.");
        }
        return PaymentDestination.error("Player or account ID was not found.");
    }

    private static PaymentDestination resolvePlayerPrimary(CentralBank centralBank, ServerPlayer player) {
        AccountHolder primary = findPrimaryAccount(centralBank, player.getUUID());
        if (primary == null) {
            return new PaymentDestination(null, player.getUUID(), false,
                    player.getGameProfile().getName() + " has no primary account.");
        }
        return new PaymentDestination(primary, player.getUUID(), false, "");
    }

    private static void rememberPaymentTarget(ServerPlayer player, AccountHolder destination, boolean accountIdDirect) {
        if (destination == null) {
            return;
        }
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        if (accountIdDirect) {
            data.rememberPaymentAccount(player.getUUID(), destination.getAccountUUID());
        } else {
            data.rememberPaymentPlayer(player.getUUID(), destination.getPlayerUUID());
        }
    }

    private static AccountHolder findPrimaryAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        List<AccountHolder> accounts = new ArrayList<>(centralBank.SearchForAccount(playerId).values());
        if (accounts.isEmpty()) {
            return null;
        }
        return accounts.stream()
                .filter(AccountHolder::isPrimaryAccount)
                .findFirst()
                .orElse(accounts.size() == 1 ? accounts.get(0) : null);
    }

    private static AccountHolder findOrCreateCentralCheckingAccount(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        AccountHolder existing = centralBank.SearchForAccount(playerId).values().stream()
                .filter(account -> account != null && centralBank.getBankId().equals(account.getBankId()))
                .filter(account -> account.getAccountType() == AccountTypes.CheckingAccount)
                .findFirst()
                .orElse(null);
        if (existing != null) {
            return existing;
        }
        AccountHolder account = new AccountHolder(playerId, BigDecimal.ZERO, AccountTypes.CheckingAccount, "",
                centralBank.getBankId(), null);
        if (!centralBank.AddAccount(account)) {
            return null;
        }
        if (findPrimaryAccount(centralBank, playerId) == null) {
            account.setPrimaryAccount(true);
        }
        return account;
    }

    private static Bank resolveOwnedBank(CentralBank centralBank, UUID ownerId) {
        if (centralBank == null || ownerId == null || centralBank.getBanks() == null) {
            return null;
        }
        return centralBank.getBanks().values().stream()
                .filter(bank -> bank != null && !bank.getBankId().equals(centralBank.getBankId()))
                .filter(bank -> ownerId.equals(bank.getBankOwnerId()))
                .findFirst()
                .orElse(null);
    }

    private static String signedAmount(AccountHolder account, UserTransaction tx) {
        if (tx == null || tx.getAmount() == null) {
            return "$0";
        }
        String amount = MoneyText.abbreviateWithDollar(tx.getAmount());
        if (account != null && account.getAccountUUID().equals(tx.getSenderUUID())) {
            return "-" + amount;
        }
        return amount.startsWith("+") ? amount : "+" + amount;
    }

    private static long txMillis(UserTransaction tx) {
        if (tx == null || tx.getTimestamp() == null) {
            return 0L;
        }
        return tx.getTimestamp().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static String counterpartyLabel(net.minecraft.server.MinecraftServer server,
                                            CentralBank centralBank,
                                            AccountHolder account,
                                            UserTransaction tx) {
        if (tx == null || account == null) {
            return "";
        }
        UUID otherAccountId = account.getAccountUUID().equals(tx.getSenderUUID())
                ? tx.getReceiverUUID()
                : tx.getSenderUUID();
        AccountHolder other = centralBank.SearchForAccountByAccountId(otherAccountId);
        if (other == null) {
            return shortId(otherAccountId);
        }
        ServerPlayer online = server.getPlayerList().getPlayer(other.getPlayerUUID());
        if (online != null) {
            return online.getGameProfile().getName();
        }
        Bank bank = centralBank.getBank(other.getBankId());
        return (bank == null ? "Account" : bank.getBankName()) + " " + shortId(otherAccountId);
    }

    private static int countEncodedEntries(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return 0;
        }
        int count = 0;
        for (String row : encoded.split(";")) {
            if (!row.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static Map<UUID, EmployeeSpec> decodeEmployeeMap(String encoded) {
        Map<UUID, EmployeeSpec> result = new HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return result;
        }
        for (String entry : encoded.split(";")) {
            String raw = entry.trim();
            if (raw.isBlank() || !raw.contains("=") || !raw.contains(":")) {
                continue;
            }
            String[] uuidAndRest = raw.split("=", 2);
            String[] roleAndSalary = uuidAndRest[1].split(":", 2);
            if (roleAndSalary.length < 2) {
                continue;
            }
            try {
                UUID id = UUID.fromString(uuidAndRest[0].trim());
                String role = roleAndSalary[0].trim().toUpperCase(Locale.ROOT);
                BigDecimal salary = new BigDecimal(roleAndSalary[1].trim());
                if (salary.compareTo(BigDecimal.ZERO) >= 0) {
                    result.put(id, new EmployeeSpec(role, salary));
                }
            } catch (RuntimeException ignored) {
            }
        }
        return result;
    }

    private static String resolvePlayerName(net.minecraft.server.MinecraftServer server, UUID id) {
        if (id == null) {
            return "Unknown";
        }
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                return online.getGameProfile().getName();
            }
            if (server.getProfileCache() != null) {
                var cached = server.getProfileCache().get(id);
                if (cached.isPresent()
                        && cached.get().getName() != null
                        && !cached.get().getName().isBlank()) {
                    return cached.get().getName();
                }
            }
        }
        return shortId(id);
    }

    private static UUID resolvePlayerIdByName(net.minecraft.server.MinecraftServer server, String name) {
        String target = name == null ? "" : name.trim();
        if (server == null || target.isBlank()) {
            return null;
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(target);
        if (online != null) {
            return online.getUUID();
        }
        if (server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(target);
            if (cached.isPresent()) {
                return cached.get().getId();
            }
        }
        return null;
    }

    private static String resolveKnownContactName(ServerPlayer owner, SmartphoneSavedData data, UUID contactId) {
        if (owner == null || contactId == null) {
            return "Unknown";
        }
        String profileName = resolvePlayerName(owner.getServer(), contactId);
        if (!profileName.equals(shortId(contactId)) && !profileName.isBlank()) {
            return profileName;
        }
        if (data != null) {
            List<SmartphoneSavedData.MessageEntry> messages = new ArrayList<>(data.conversation(owner.getUUID(), contactId));
            messages.sort(Comparator.comparingLong(SmartphoneSavedData.MessageEntry::createdAt).reversed());
            for (SmartphoneSavedData.MessageEntry message : messages) {
                if (contactId.equals(message.senderId()) && message.senderName() != null && !message.senderName().isBlank()) {
                    return message.senderName();
                }
                if (contactId.equals(message.recipientId()) && message.recipientName() != null && !message.recipientName().isBlank()) {
                    return message.recipientName();
                }
            }
        }
        return shortId(contactId);
    }

    private static String shortId(UUID id) {
        String value = id == null ? "" : id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private record PaymentDestination(AccountHolder account, UUID playerId, boolean accountIdDirect, String message) {
        static PaymentDestination error(String message) {
            return new PaymentDestination(null, null, false, message);
        }
    }

    private record EmployeeSpec(String role, BigDecimal salary) {
    }

    private record PayRequestMarker(UUID requestId, String amount) {
    }

    private record GiftMarker(UUID giftId, String amount) {
    }

    private static String handleSaveNote(ItemStack phone, String indexRaw, String body) {
        SmartphoneData.saveNote(phone, parseInt(indexRaw, 0), body);
        return "Note saved.";
    }

    private static String handleDeleteNote(ItemStack phone, String indexRaw) {
        SmartphoneData.deleteNote(phone, parseInt(indexRaw, 0));
        return "Note deleted.";
    }

    private static String handleSavePainting(ItemStack phone, String indexRaw, String body) {
        SmartphoneData.savePainting(phone, parseInt(indexRaw, 0), body);
        return "Painting saved.";
    }

    private static String payRequestMarker(UUID requestId, BigDecimal amount) {
        return PAY_REQUEST_MESSAGE_PREFIX
                + (requestId == null ? "" : requestId)
                + ":"
                + (amount == null ? "0" : amount.toPlainString())
                + PAY_REQUEST_MESSAGE_SUFFIX;
    }

    private static PayRequestMarker parsePayRequestMarker(String body) {
        String raw = body == null ? "" : body.trim();
        if (!raw.startsWith(PAY_REQUEST_MESSAGE_PREFIX) || !raw.endsWith(PAY_REQUEST_MESSAGE_SUFFIX)) {
            return null;
        }
        String payload = raw.substring(PAY_REQUEST_MESSAGE_PREFIX.length(),
                raw.length() - PAY_REQUEST_MESSAGE_SUFFIX.length());
        int split = payload.indexOf(':');
        String idRaw = split < 0 ? payload : payload.substring(0, split);
        UUID requestId = parseUuid(idRaw);
        if (requestId == null) {
            return null;
        }
        String amount = split < 0 || split + 1 >= payload.length() ? "$0" : payload.substring(split + 1);
        return new PayRequestMarker(requestId, amount);
    }

    private static String giftMarker(UUID giftId, BigDecimal amount) {
        return GIFT_MESSAGE_PREFIX
                + (giftId == null ? "" : giftId)
                + ":"
                + (amount == null ? "0" : amount.toPlainString())
                + GIFT_MESSAGE_SUFFIX;
    }

    private static GiftMarker parseGiftMarker(String body) {
        String raw = body == null ? "" : body.trim();
        if (!raw.startsWith(GIFT_MESSAGE_PREFIX) || !raw.endsWith(GIFT_MESSAGE_SUFFIX)) {
            return null;
        }
        String payload = raw.substring(GIFT_MESSAGE_PREFIX.length(),
                raw.length() - GIFT_MESSAGE_SUFFIX.length());
        int split = payload.indexOf(':');
        String idRaw = split < 0 ? payload : payload.substring(0, split);
        UUID giftId = parseUuid(idRaw);
        if (giftId == null) {
            return null;
        }
        String amount = split < 0 || split + 1 >= payload.length() ? "$0" : payload.substring(split + 1);
        return new GiftMarker(giftId, amount);
    }

    private static void pruneExpiredGifts(net.minecraft.server.MinecraftServer server,
                                          CentralBank centralBank,
                                          SmartphoneSavedData data) {
        if (server == null || centralBank == null || data == null) {
            return;
        }
        for (SmartphoneSavedData.GiftEntry gift : data.expirePendingGifts(System.currentTimeMillis(), GIFT_TIMEOUT_MILLIS)) {
            BigDecimal amount = gift.amountDecimal();
            refundGift(server, centralBank, gift);
            ServerPlayer sender = server.getPlayerList().getPlayer(gift.senderId());
            if (sender != null) {
                pushLiveRefresh(sender, "");
            }
            notifyPhone(server, gift.senderId(), "Gift",
                    "Your gift of " + MoneyText.abbreviateWithDollar(amount) + " expired and was refunded.",
                    DeliveryAlertPayload.AlertTone.WARNING, 5200);
            ServerPlayer recipient = server.getPlayerList().getPlayer(gift.recipientId());
            if (recipient != null) {
                pushLiveRefresh(recipient, "");
            } else {
                notifyPhone(server, gift.recipientId(), "Gift",
                        "A pending gift expired before it was accepted.",
                        DeliveryAlertPayload.AlertTone.WARNING, 5200);
            }
        }
    }

    private static boolean refundGift(net.minecraft.server.MinecraftServer server,
                                      CentralBank centralBank,
                                      SmartphoneSavedData.GiftEntry gift) {
        if (server == null || centralBank == null || gift == null) {
            return false;
        }
        BigDecimal amount = gift.amountDecimal();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        AccountHolder source = centralBank.SearchForAccountByAccountId(gift.sourceAccountId());
        if (source == null) {
            return false;
        }
        return source.forceAddBalance(amount);
    }

    private static String handleSendMessage(ServerPlayer player, String recipientRaw, String body) {
        UUID recipient = parseUuid(recipientRaw);
        if (recipient == null || body == null || body.isBlank()) {
            return "Choose a contact and write a message.";
        }
        clearMessengerTyping(player, recipient);
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(recipient);
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        boolean sent = data.sendMessage(player.getUUID(), player.getGameProfile().getName(), recipient,
                target == null ? resolveKnownContactName(player, data, recipient) : target.getGameProfile().getName(), body);
        if (!sent) {
            return "Message not sent.";
        }
        if (target != null) {
            pushLiveRefresh(target, "");
        }
        if (!data.prefs(recipient).muted.contains(player.getUUID())) {
            notifyPhone(player.getServer(), recipient, "Messenger",
                    "New message from " + player.getGameProfile().getName(),
                    DeliveryAlertPayload.AlertTone.INFO, 4400);
        }
        return "Message sent.";
    }

    private static String handleMessengerTyping(ServerPlayer player, String recipientRaw, String activeRaw) {
        UUID recipient = parseUuid(recipientRaw);
        if (recipient == null || recipient.equals(player.getUUID())) {
            return "";
        }
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        boolean active = Boolean.parseBoolean(activeRaw == null ? "" : activeRaw.trim());
        if (!active) {
            MESSENGER_TYPING.remove(typingKey(player.getUUID(), recipient));
        } else if (!data.prefs(player.getUUID()).blocked.contains(recipient)
                && !data.prefs(recipient).blocked.contains(player.getUUID())) {
            MESSENGER_TYPING.put(typingKey(player.getUUID(), recipient),
                    new TypingState(player.getUUID(),
                            player.getGameProfile().getName(),
                            recipient,
                            System.currentTimeMillis() + MESSENGER_TYPING_TIMEOUT_MILLIS));
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(recipient);
        if (target != null) {
            pushLiveRefresh(target, "");
        }
        return "";
    }

    private static void clearMessengerTyping(ServerPlayer player, UUID recipient) {
        if (player == null || recipient == null) {
            return;
        }
        MESSENGER_TYPING.remove(typingKey(player.getUUID(), recipient));
    }

    private static void appendTypingIndicators(ServerPlayer player,
                                               SmartphoneSavedData data,
                                               List<String> lines) {
        if (player == null || data == null || lines == null) {
            return;
        }
        UUID recipient = player.getUUID();
        long now = System.currentTimeMillis();
        pruneExpiredTyping(now);
        SmartphoneSavedData.PlayerPrefs recipientPrefs = data.prefs(recipient);
        for (TypingState state : MESSENGER_TYPING.values()) {
            if (state == null
                    || !recipient.equals(state.recipientId())
                    || state.expiresAtMillis() <= now
                    || recipientPrefs.blocked.contains(state.senderId())
                    || data.prefs(state.senderId()).blocked.contains(recipient)) {
                continue;
            }
            lines.add(token("typing",
                    state.senderId(),
                    state.senderName(),
                    Math.max(0L, state.expiresAtMillis() - now)));
        }
    }

    private static void pruneExpiredTyping(long now) {
        MESSENGER_TYPING.entrySet().removeIf(entry -> entry.getValue() == null
                || entry.getValue().expiresAtMillis() <= now);
    }

    private static String typingKey(UUID sender, UUID recipient) {
        return String.valueOf(sender) + "->" + recipient;
    }

    private static void pushLiveRefresh(ServerPlayer player, String statusMessage) {
        if (player == null) {
            return;
        }
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            return;
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            return;
        }
        SmartphoneSnapshotPayload snapshot = buildSnapshot(player, phone, false);
        PacketDistributor.sendToPlayer(player, SmartphoneLiveRefreshPayload.fromSnapshot(snapshot, statusMessage));
    }

    private static void deliverQueuedPhoneNotifications(ServerPlayer player, boolean consume) {
        if (player == null) {
            return;
        }
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            return;
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            return;
        }
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        List<SmartphoneSavedData.QueuedNotification> notifications = consume
                ? data.consumeNotifications(player.getUUID())
                : data.queuedNotifications(player.getUUID());
        for (SmartphoneSavedData.QueuedNotification notification : notifications) {
            if (notification == null || notification.message() == null || notification.message().isBlank()) {
                continue;
            }
            PacketDistributor.sendToPlayer(player, new SmartphoneNotificationPayload(
                    notification.title(),
                    notification.message(),
                    notification.toneCode(),
                    notification.durationMs()
            ));
        }
    }

    private static void notifyPhone(net.minecraft.server.MinecraftServer server,
                                    UUID recipientId,
                                    String title,
                                    String message,
                                    DeliveryAlertPayload.AlertTone tone,
                                    int durationMs) {
        if (server == null || recipientId == null || message == null || message.isBlank()) {
            return;
        }
        ServerPlayer recipient = server.getPlayerList().getPlayer(recipientId);
        if (recipient != null && sendPhoneNotification(recipient, title, message, tone, durationMs)) {
            return;
        }
        SmartphoneSavedData.get(server).queueNotification(
                recipientId,
                title == null || title.isBlank() ? "UBS Phone" : title,
                message,
                tone == null ? DeliveryAlertPayload.AlertTone.INFO.id() : tone.id(),
                durationMs
        );
    }

    private static boolean sendPhoneNotification(ServerPlayer player,
                                                 String title,
                                                 String message,
                                                 DeliveryAlertPayload.AlertTone tone,
                                                 int durationMs) {
        if (player == null || message == null || message.isBlank()) {
            return false;
        }
        ItemStack phone = findUsablePhone(player);
        if (phone.isEmpty()) {
            return false;
        }
        SmartphoneData.ensureOwner(phone, player);
        if (!canUsePhone(phone, player)) {
            return false;
        }
        DeliveryAlertPayload.AlertTone safeTone = tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone;
        PacketDistributor.sendToPlayer(player, new SmartphoneNotificationPayload(
                title == null || title.isBlank() ? "UBS Phone" : title,
                message,
                safeTone.id(),
                durationMs
        ));
        return true;
    }

    private static String handleReadConversation(ServerPlayer player, String otherRaw) {
        UUID other = parseUuid(otherRaw);
        if (other != null) {
            SmartphoneSavedData.get(player.getServer()).markRead(player.getUUID(), other);
        }
        return "";
    }

    private static String handleFavorite(ServerPlayer player, String otherRaw, boolean favorite) {
        UUID other = parseUuid(otherRaw);
        if (other == null) {
            return "Choose a contact first.";
        }
        SmartphoneSavedData.get(player.getServer()).favorite(player.getUUID(), other, favorite);
        return favorite ? "Contact favorited." : "Contact removed from favorites.";
    }

    private static String handleMute(ServerPlayer player, String otherRaw, boolean mute) {
        UUID other = parseUuid(otherRaw);
        if (other == null) {
            return "Choose a contact first.";
        }
        SmartphoneSavedData.get(player.getServer()).mute(player.getUUID(), other, mute);
        return mute ? "Contact muted." : "Contact unmuted.";
    }

    private static String handleBlock(ServerPlayer player, String otherRaw, boolean block) {
        UUID other = parseUuid(otherRaw);
        if (other == null) {
            return "Choose a contact first.";
        }
        SmartphoneSavedData.get(player.getServer()).block(player.getUUID(), other, block);
        return block ? "Contact blocked." : "Contact unblocked.";
    }

    private static String handleReport(ServerPlayer player, String otherRaw, String reason) {
        UUID other = parseUuid(otherRaw);
        if (other == null) {
            return "Choose a contact first.";
        }
        ServerPlayer target = player.getServer().getPlayerList().getPlayer(other);
        SmartphoneSavedData data = SmartphoneSavedData.get(player.getServer());
        SmartphoneSavedData.ReportEntry report = data.report(
                player.getUUID(),
                player.getGameProfile().getName(),
                other,
                target == null ? resolveKnownContactName(player, data, other) : target.getGameProfile().getName(),
                reason == null || reason.isBlank() ? "Phone report" : reason
        );
        return "Report filed: " + report.id().toString().substring(0, 8) + ".";
    }

    private static String bankName(CentralBank centralBank, AccountHolder account) {
        Bank bank = centralBank == null || account == null ? null : centralBank.getBank(account.getBankId());
        return bank == null ? "Unknown Bank" : bank.getBankName();
    }

    private static String accountType(AccountHolder account) {
        if (account == null || account.getAccountType() == null) {
            return "Account";
        }
        String raw = account.getAccountType().name().replace("Account", " Account");
        return raw.replaceAll("([a-z])([A-Z])", "$1 $2");
    }

    private static String token(Object... values) {
        List<String> encoded = new ArrayList<>();
        for (Object value : values) {
            encoded.add(encode(String.valueOf(value == null ? "" : value)));
        }
        return String.join("|", encoded);
    }

    private static String encode(String value) {
        return value == null ? "" : value
                .replace("%", "%25")
                .replace("|", "%7C")
                .replace("\n", "%0A")
                .replace("\r", "");
    }

    private static UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal parseAmount(String raw) {
        try {
            BigDecimal amount = new BigDecimal(raw == null ? "" : raw.trim().replace("$", ""));
            return amount.compareTo(BigDecimal.ZERO) > 0 ? amount : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
