package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CreditCardService {

    public static final String META_CARD_ISSUE_FEE = "cardIssueFee";
    public static final String META_CARD_REPLACEMENT_FEE = "cardReplacementFee";

    private static final BigDecimal DEFAULT_ISSUE_FEE = new BigDecimal("25");
    private static final BigDecimal DEFAULT_REPLACEMENT_FEE = new BigDecimal("50");
    private static final long DEFAULT_VALIDITY_MILLIS = 90L * 24L * 60L * 60L * 1000L;

    public static final String TAG_CARD_ID = "ubs_cc_id";
    public static final String TAG_ACCOUNT_ID = "ubs_cc_account";
    public static final String TAG_BANK_ID = "ubs_cc_bank";
    public static final String TAG_BANK_NAME = "ubs_cc_bank_name";
    public static final String TAG_OWNER_ID = "ubs_cc_owner";
    public static final String TAG_CARD_NUMBER = "ubs_cc_number";
    public static final String TAG_CVC = "ubs_cc_cvc";
    public static final String TAG_ISSUED_AT = "ubs_cc_issued";
    public static final String TAG_EXPIRY_AT = "ubs_cc_expiry";
    public static final String TAG_BLOCKED = "ubs_cc_blocked";

    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("MM/yy").withZone(ZoneId.systemDefault());

    public record CardValidationResult(
            boolean valid,
            String message,
            UUID cardId,
            UUID accountId,
            UUID bankId,
            String cardNumber,
            String cvc,
            long expiryEpochMillis,
            boolean blocked,
            boolean expired
    ) {
        public static CardValidationResult invalid(String message) {
            return new CardValidationResult(false, message, null, null, null, "", "", 0L, false, false);
        }
    }

    public record HeldCardLookup(boolean hasCard, CardValidationResult validation) {
        public static HeldCardLookup none() {
            return new HeldCardLookup(false, CardValidationResult.invalid("No held credit card."));
        }
    }

    public record CardIssueResult(
            boolean success,
            String message,
            ItemStack cardStack,
            UUID cardId,
            String cardNumber,
            String cvc,
            long expiryEpochMillis
    ) {
        public static CardIssueResult fail(String message) {
            return new CardIssueResult(false, message, ItemStack.EMPTY, null, "", "", 0L);
        }
    }

    private CreditCardService() {}

    public static BigDecimal getIssueFee(CentralBank centralBank, UUID bankId) {
        return readNonNegativeWholeFee(centralBank, bankId, META_CARD_ISSUE_FEE, DEFAULT_ISSUE_FEE);
    }

    public static BigDecimal getReplacementFee(CentralBank centralBank, UUID bankId) {
        return readNonNegativeWholeFee(centralBank, bankId, META_CARD_REPLACEMENT_FEE, DEFAULT_REPLACEMENT_FEE);
    }

    public static boolean setFees(CentralBank centralBank, UUID bankId, BigDecimal issueFee, BigDecimal replacementFee) {
        if (centralBank == null || bankId == null || issueFee == null || replacementFee == null) {
            return false;
        }
        if (!isNonNegativeWhole(issueFee) || !isNonNegativeWhole(replacementFee)) {
            return false;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        metadata.putString(META_CARD_ISSUE_FEE, issueFee.toPlainString());
        metadata.putString(META_CARD_REPLACEMENT_FEE, replacementFee.toPlainString());
        centralBank.putBankMetadata(bankId, metadata);
        return true;
    }

    public static HeldCardLookup findHeldCard(CentralBank centralBank, ServerPlayer player) {
        if (player == null || centralBank == null) {
            return HeldCardLookup.none();
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        boolean sawCard = false;
        CardValidationResult firstInvalid = null;

        if (isCreditCard(main)) {
            sawCard = true;
            CardValidationResult result = validateCardStack(centralBank, main, player.getUUID());
            if (result.valid()) {
                return new HeldCardLookup(true, result);
            }
            firstInvalid = result;
        }

        if (isCreditCard(off)) {
            sawCard = true;
            CardValidationResult result = validateCardStack(centralBank, off, player.getUUID());
            if (result.valid()) {
                return new HeldCardLookup(true, result);
            }
            if (firstInvalid == null) {
                firstInvalid = result;
            }
        }

        if (!sawCard) {
            return HeldCardLookup.none();
        }
        return new HeldCardLookup(true, firstInvalid == null
                ? CardValidationResult.invalid("Credit card is invalid.")
                : firstInvalid);
    }

    public static CardValidationResult validateCardStack(CentralBank centralBank,
                                                         ItemStack stack,
                                                         UUID expectedOwnerPlayerId) {
        if (!isCreditCard(stack)) {
            return CardValidationResult.invalid("No credit card provided.");
        }

        CompoundTag tag = readCustomTag(stack);
        if (tag == null || !tag.hasUUID(TAG_CARD_ID)) {
            return CardValidationResult.invalid("This credit card has no valid card ID.");
        }

        UUID cardId = tag.getUUID(TAG_CARD_ID);
        CompoundTag record = centralBank.getIssuedCreditCards().get(cardId);
        if (record == null || record.isEmpty()) {
            return CardValidationResult.invalid("This credit card is not recognized by the bank.");
        }

        UUID accountId = record.hasUUID("accountId") ? record.getUUID("accountId")
                : (record.hasUUID(TAG_ACCOUNT_ID) ? record.getUUID(TAG_ACCOUNT_ID) : null);
        UUID bankId = record.hasUUID("bankId") ? record.getUUID("bankId")
                : (record.hasUUID(TAG_BANK_ID) ? record.getUUID(TAG_BANK_ID) : null);

        if (accountId == null) {
            return CardValidationResult.invalid("This credit card is missing account data.");
        }

        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            return CardValidationResult.invalid("Linked account no longer exists.");
        }

        if (expectedOwnerPlayerId != null && !expectedOwnerPlayerId.equals(account.getPlayerUUID())) {
            return CardValidationResult.invalid("This credit card belongs to another player.");
        }

        boolean blocked = record.getBoolean("blocked");
        long expiry = record.contains("expiryEpochMillis") ? record.getLong("expiryEpochMillis")
                : (record.contains(TAG_EXPIRY_AT) ? record.getLong(TAG_EXPIRY_AT) : 0L);
        boolean expired = expiry > 0L && System.currentTimeMillis() > expiry;

        String cardNumber = readNonBlank(record, "cardNumber", readNonBlank(tag, TAG_CARD_NUMBER, ""));
        String cvc = readNonBlank(record, "cvc", readNonBlank(tag, TAG_CVC, ""));
        String bankName = readNonBlank(record, "bankName", "");
        boolean recordUpdated = false;
        if (bankName.isBlank()) {
            bankName = resolveBankName(centralBank, bankId);
            if (!bankName.isBlank()) {
                record.putString("bankName", bankName);
                recordUpdated = true;
            }
        }

        boolean stackUpdated = false;
        String stackBankName = readNonBlank(tag, TAG_BANK_NAME, "");
        if (!bankName.isBlank() && !bankName.equals(stackBankName)) {
            tag.putString(TAG_BANK_NAME, bankName);
            stackUpdated = true;
        }
        if (stackUpdated) {
            ItemStackDataCompat.setCustomData(stack, tag);
        }
        if (recordUpdated) {
            BankManager.markDirty();
        }
        if (tag.getBoolean(TAG_BLOCKED) != blocked) {
            tag.putBoolean(TAG_BLOCKED, blocked);
            ItemStackDataCompat.setCustomData(stack, tag);
        }
        ItemStackDataCompat.setCustomName(stack, buildCardDisplayName(bankName, cardNumber, blocked));

        if (blocked) {
            return new CardValidationResult(false,
                    "Credit card is blocked.",
                    cardId,
                    accountId,
                    bankId,
                    cardNumber,
                    cvc,
                    expiry,
                    true,
                    false);
        }

        if (expired) {
            return new CardValidationResult(false,
                    "Credit card expired on " + formatExpiryMonthYear(expiry) + ".",
                    cardId,
                    accountId,
                    bankId,
                    cardNumber,
                    cvc,
                    expiry,
                    false,
                    true);
        }

        return new CardValidationResult(true,
                "OK",
                cardId,
                accountId,
                bankId,
                cardNumber,
                cvc,
                expiry,
                false,
                false);
    }

    public static CardIssueResult issueCard(CentralBank centralBank,
                                            AccountHolder account,
                                            String holderName,
                                            boolean replacement) {
        if (centralBank == null || account == null) {
            return CardIssueResult.fail("Bank data is unavailable.");
        }

        long now = System.currentTimeMillis();
        UUID cardId = UUID.randomUUID();
        String cardNumber = generateCardNumber(cardId, account.getAccountUUID());
        String cvc = String.format(Locale.ROOT, "%03d", ThreadLocalRandom.current().nextInt(0, 1000));
        long expiry = now + DEFAULT_VALIDITY_MILLIS;

        if (replacement) {
            blockCardsForAccount(centralBank, account.getAccountUUID(), cardId, "Replaced by account holder.");
        }

        CompoundTag record = new CompoundTag();
        record.putUUID("id", cardId);
        record.putUUID("accountId", account.getAccountUUID());
        record.putUUID("bankId", account.getBankId());
        record.putUUID("ownerPlayerId", account.getPlayerUUID());
        Bank sourceBank = centralBank.getBank(account.getBankId());
        String bankName = sourceBank != null && sourceBank.getBankName() != null && !sourceBank.getBankName().isBlank()
                ? sourceBank.getBankName().trim()
                : "Unknown Bank";
        record.putString("bankName", bankName);
        record.putString("holderName", holderName == null ? "" : holderName.trim());
        record.putString("cardNumber", cardNumber);
        record.putString("cvc", cvc);
        record.putLong("issuedEpochMillis", now);
        record.putLong("expiryEpochMillis", expiry);
        record.putBoolean("blocked", false);
        record.putString("status", "ACTIVE");
        if (replacement) {
            record.putBoolean("replacement", true);
        }

        centralBank.getIssuedCreditCards().put(cardId, record);
        BankManager.markDirty();

        ItemStack stack = new ItemStack(ModItems.CREDIT_CARD.get());
        writeCreditCardTag(stack, record);

        String masked = maskCardNumber(cardNumber);
        ItemStackDataCompat.setCustomName(stack, buildCardDisplayName(bankName, masked, false));

        return new CardIssueResult(
                true,
                "Credit card issued.",
                stack,
                cardId,
                cardNumber,
                cvc,
                expiry
        );
    }

    public static void blockCardsForAccount(CentralBank centralBank,
                                            UUID accountId,
                                            UUID replacementCardId,
                                            String reason) {
        if (centralBank == null || accountId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        Set<UUID> blockedCardIds = new HashSet<>();
        for (var entry : centralBank.getIssuedCreditCards().entrySet()) {
            UUID cardId = entry.getKey();
            CompoundTag record = entry.getValue();
            if (cardId == null || record == null) {
                continue;
            }

            if (!record.hasUUID("accountId") || !accountId.equals(record.getUUID("accountId"))) {
                continue;
            }

            if (replacementCardId != null && replacementCardId.equals(cardId)) {
                continue;
            }

            if (record.getBoolean("blocked")) {
                continue;
            }

            record.putBoolean("blocked", true);
            record.putString("status", "BLOCKED");
            record.putLong("blockedEpochMillis", now);
            if (reason != null && !reason.isBlank()) {
                record.putString("blockedReason", reason);
            }
            if (replacementCardId != null) {
                record.putUUID("replacedBy", replacementCardId);
            }
            blockedCardIds.add(cardId);
        }

        BankManager.markDirty();
        if (!blockedCardIds.isEmpty()) {
            markBlockedCardsInOnlineInventories(centralBank, blockedCardIds);
        }
    }

    public static String formatExpiryMonthYear(long epochMillis) {
        if (epochMillis <= 0L) {
            return "--/--";
        }
        return EXPIRY_FORMAT.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String maskCardNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return "**** **** **** ****";
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "****";
        }
        String last4 = digits.substring(digits.length() - 4);
        return "**** **** **** " + last4;
    }

    public static boolean hasActiveCardForAccount(CentralBank centralBank, UUID accountId) {
        if (centralBank == null || accountId == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (var entry : centralBank.getIssuedCreditCards().entrySet()) {
            CompoundTag record = entry.getValue();
            if (record == null || record.isEmpty()) {
                continue;
            }

            UUID recordAccountId = null;
            if (record.hasUUID("accountId")) {
                recordAccountId = record.getUUID("accountId");
            } else if (record.hasUUID(TAG_ACCOUNT_ID)) {
                recordAccountId = record.getUUID(TAG_ACCOUNT_ID);
            }
            if (!accountId.equals(recordAccountId)) {
                continue;
            }

            boolean blocked = record.getBoolean("blocked")
                    || "BLOCKED".equalsIgnoreCase(record.getString("status"));
            if (blocked) {
                continue;
            }

            long expiry = record.contains("expiryEpochMillis") ? record.getLong("expiryEpochMillis")
                    : (record.contains(TAG_EXPIRY_AT) ? record.getLong(TAG_EXPIRY_AT) : 0L);
            boolean expired = expiry > 0L && now > expiry;
            if (expired) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static void writeCreditCardTag(ItemStack stack, CompoundTag record) {
        CompoundTag tag = readCustomTag(stack);
        if (tag == null) {
            tag = new CompoundTag();
        }

        if (record.hasUUID("id")) {
            tag.putUUID(TAG_CARD_ID, record.getUUID("id"));
        }
        if (record.hasUUID("accountId")) {
            tag.putUUID(TAG_ACCOUNT_ID, record.getUUID("accountId"));
        }
        if (record.hasUUID("bankId")) {
            tag.putUUID(TAG_BANK_ID, record.getUUID("bankId"));
        }
        tag.putString(TAG_BANK_NAME, readNonBlank(record, "bankName", ""));
        if (record.hasUUID("ownerPlayerId")) {
            tag.putUUID(TAG_OWNER_ID, record.getUUID("ownerPlayerId"));
        }

        tag.putString(TAG_CARD_NUMBER, readNonBlank(record, "cardNumber", ""));
        tag.putString(TAG_CVC, readNonBlank(record, "cvc", ""));
        tag.putLong(TAG_ISSUED_AT, record.contains("issuedEpochMillis") ? record.getLong("issuedEpochMillis") : 0L);
        tag.putLong(TAG_EXPIRY_AT, record.contains("expiryEpochMillis") ? record.getLong("expiryEpochMillis") : 0L);
        tag.putBoolean(TAG_BLOCKED, record.getBoolean("blocked"));

        ItemStackDataCompat.setCustomData(stack, tag);
    }

    private static boolean isCreditCard(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(ModItems.CREDIT_CARD.get());
    }

    private static String generateCardNumber(UUID cardId, UUID accountId) {
        long seed = (cardId.getMostSignificantBits() ^ cardId.getLeastSignificantBits())
                ^ (accountId.getMostSignificantBits() ^ accountId.getLeastSignificantBits());
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int g1 = Math.abs((int) (seed % 10_000L));
        int g2 = Math.abs((int) ((seed >>> 16) % 10_000L));
        int g3 = Math.abs((int) ((seed >>> 32) % 10_000L));
        int g4 = random.nextInt(0, 10_000);

        return String.format(Locale.ROOT, "%04d %04d %04d %04d", g1, g2, g3, g4);
    }

    private static boolean isNonNegativeWhole(BigDecimal value) {
        if (value == null) {
            return false;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        return value.stripTrailingZeros().scale() <= 0;
    }

    private static BigDecimal readNonNegativeWholeFee(CentralBank centralBank,
                                                      UUID bankId,
                                                      String key,
                                                      BigDecimal fallback) {
        if (centralBank == null || bankId == null || key == null || key.isBlank()) {
            return fallback;
        }
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        if (!metadata.contains(key)) {
            metadata.putString(key, fallback.toPlainString());
            centralBank.putBankMetadata(bankId, metadata);
            return fallback;
        }

        String raw = metadata.getString(key);
        try {
            BigDecimal parsed = new BigDecimal(raw == null ? "" : raw.trim());
            if (!isNonNegativeWhole(parsed)) {
                return fallback;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String readNonBlank(CompoundTag tag, String key, String fallback) {
        if (tag == null || key == null || key.isBlank() || !tag.contains(key)) {
            return fallback;
        }
        String value = tag.getString(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String resolveBankName(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) {
            return "Unknown Bank";
        }
        Bank bank = centralBank.getBank(bankId);
        if (bank == null || bank.getBankName() == null || bank.getBankName().isBlank()) {
            return "Unknown Bank";
        }
        return bank.getBankName().trim();
    }

    private static Component buildCardDisplayName(String bankName, String cardNumberOrMasked, boolean blocked) {
        String safeBankName = (bankName == null || bankName.isBlank()) ? "Unknown Bank" : bankName.trim();
        String masked = cardNumberOrMasked == null ? "" : cardNumberOrMasked.trim();
        if (!masked.contains("*")) {
            masked = maskCardNumber(masked);
        }
        if (blocked) {
            return Component.literal("Credit Card • BLOCKED • " + safeBankName + " • " + masked)
                    .withStyle(ChatFormatting.RED);
        }
        return Component.literal("Credit Card • " + safeBankName + " • " + masked)
                .withStyle(ChatFormatting.AQUA);
    }

    private static void markBlockedCardsInOnlineInventories(CentralBank centralBank, Set<UUID> blockedCardIds) {
        if (centralBank == null || blockedCardIds == null || blockedCardIds.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!isCreditCard(stack)) {
                    continue;
                }
                CompoundTag tag = readCustomTag(stack);
                if (tag == null || !tag.hasUUID(TAG_CARD_ID)) {
                    continue;
                }
                UUID cardId = tag.getUUID(TAG_CARD_ID);
                if (!blockedCardIds.contains(cardId)) {
                    continue;
                }
                CompoundTag record = centralBank.getIssuedCreditCards().get(cardId);
                String bankName = record == null ? readNonBlank(tag, TAG_BANK_NAME, "Unknown Bank")
                        : readNonBlank(record, "bankName", readNonBlank(tag, TAG_BANK_NAME, "Unknown Bank"));
                String cardNumber = readNonBlank(tag, TAG_CARD_NUMBER, "");
                tag.putBoolean(TAG_BLOCKED, true);
                if (!bankName.isBlank()) {
                    tag.putString(TAG_BANK_NAME, bankName);
                }
                ItemStackDataCompat.setCustomData(stack, tag);
                ItemStackDataCompat.setCustomName(stack, buildCardDisplayName(bankName, cardNumber, true));
            }
            player.containerMenu.broadcastChanges();
        }
    }

    private static CompoundTag readCustomTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag data = ItemStackDataCompat.getCustomData(stack);
        return data == null ? null : data.copy();
    }
}
