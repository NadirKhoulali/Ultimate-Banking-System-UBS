package net.austizz.ultimatebankingsystem.npc;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class BankTellerInteractionManager {

    private static final int PAGE_SIZE = 5;
    private static final double CANCEL_DISTANCE_SQ = 8.0D * 8.0D;
    private static final long SESSION_TIMEOUT_TICKS = 20L * 120L;
    private static final String[] GOODBYE_MESSAGES = {
            "§7Bank Teller: §bThank you for banking with us.",
            "§7Bank Teller: §aTake care and have a good day.",
            "§7Bank Teller: §eYour business is appreciated.",
            "§7Bank Teller: §dCome back any time."
    };

    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private BankTellerInteractionManager() {}

    private enum Stage {
        CHOICE,
        ACCOUNT_PICK
    }

    private record ChequeData(
            String chequeId,
            BigDecimal amount,
            UUID recipientId,
            UUID writerId,
            String recipientName,
            String writerName
    ) {}

    private static final class Session {
        private final UUID playerId;
        private final UUID tellerId;
        private final String tellerDimension;
        private final ChequeData cheque;
        private final long startedTick;
        private Stage stage = Stage.CHOICE;
        private int page = 0;

        private Session(UUID playerId, UUID tellerId, String tellerDimension, ChequeData cheque, long startedTick) {
            this.playerId = playerId;
            this.tellerId = tellerId;
            this.tellerDimension = tellerDimension;
            this.cheque = cheque;
            this.startedTick = startedTick;
        }
    }

    public static void beginChequeSession(ServerPlayer player, BankTellerEntity teller, InteractionHand hand) {
        if (player == null || teller == null) {
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            player.sendSystemMessage(Component.literal("§cBank data is unavailable right now."));
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        ChequeData cheque = readChequeData(stack);
        if (cheque == null) {
            player.sendSystemMessage(Component.literal("§cInvalid cheque."));
            return;
        }
        if (!player.getUUID().equals(cheque.recipientId)) {
            player.sendSystemMessage(Component.literal("§cThis cheque is not payable to you."));
            return;
        }
        if (centralBank.isChequeRedeemed(cheque.chequeId)) {
            player.sendSystemMessage(Component.literal("§cThis cheque has already been redeemed."));
            return;
        }

        Session session = new Session(
                player.getUUID(),
                teller.getUUID(),
                teller.level().dimension().location().toString(),
                cheque,
                teller.level().getGameTime()
        );
        SESSIONS.put(player.getUUID(), session);
        sendChoicePrompt(player, session);
    }

    public static int handleChoose(CommandSourceStack source, String rawChoice) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can do this."));
            return 0;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal("§7No active teller interaction."));
            return 0;
        }

        String choice = rawChoice == null ? "" : rawChoice.trim().toLowerCase(Locale.ROOT);
        if ("bank".equals(choice) || "return".equals(choice)) {
            session.stage = Stage.ACCOUNT_PICK;
            session.page = 0;
            showAccountPage(player, session);
            return 1;
        }
        if ("cash".equals(choice) || "handin".equals(choice) || "hand_in".equals(choice)) {
            completeAsCash(player, session);
            return 1;
        }

        player.sendSystemMessage(Component.literal("§cUnknown option. Use bank, cash, or cancel."));
        return 0;
    }

    public static int handlePage(CommandSourceStack source, String direction) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can do this."));
            return 0;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.stage != Stage.ACCOUNT_PICK) {
            player.sendSystemMessage(Component.literal("§7No active account picker."));
            return 0;
        }

        List<AccountHolder> accounts = getPlayerAccounts(player);
        int pageCount = Math.max(1, (accounts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        String dir = direction == null ? "" : direction.trim().toLowerCase(Locale.ROOT);
        if ("next".equals(dir)) {
            session.page = Math.min(pageCount - 1, session.page + 1);
        } else if ("prev".equals(dir) || "previous".equals(dir)) {
            session.page = Math.max(0, session.page - 1);
        }
        showAccountPage(player, session);
        return 1;
    }

    public static int handleAccountPick(CommandSourceStack source, UUID accountId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can do this."));
            return 0;
        }
        if (accountId == null) {
            player.sendSystemMessage(Component.literal("§cInvalid account."));
            return 0;
        }

        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.stage != Stage.ACCOUNT_PICK) {
            player.sendSystemMessage(Component.literal("§7No active account picker."));
            return 0;
        }

        AccountHolder selected = null;
        for (AccountHolder account : getPlayerAccounts(player)) {
            if (accountId.equals(account.getAccountUUID())) {
                selected = account;
                break;
            }
        }
        if (selected == null) {
            player.sendSystemMessage(Component.literal("§cThat account is not available."));
            return 0;
        }

        completeToBank(player, session, selected);
        return 1;
    }

    public static int handleCancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can do this."));
            return 0;
        }
        return cancel(player, "You cancelled the teller interaction.");
    }

    public static void tick(MinecraftServer server) {
        if (server == null || SESSIONS.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : ids) {
            Session session = SESSIONS.get(playerId);
            if (session == null) {
                continue;
            }

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null) {
                SESSIONS.remove(playerId);
                continue;
            }

            ServerLevel level = player.serverLevel();
            if (!session.tellerDimension.equals(level.dimension().location().toString())) {
                cancel(player, "You moved too far away from the teller.");
                continue;
            }

            Entity tellerEntity = level.getEntity(session.tellerId);
            if (!(tellerEntity instanceof BankTellerEntity teller)) {
                cancel(player, "This teller is no longer available.");
                continue;
            }

            if (player.distanceToSqr(teller) > CANCEL_DISTANCE_SQ) {
                cancel(player, "You walked away from the teller.");
                continue;
            }

            if (Math.abs(level.getGameTime() - session.startedTick) > SESSION_TIMEOUT_TICKS) {
                cancel(player, "Teller request timed out.");
            }
        }
    }

    public static void cancelForTeller(UUID tellerId, String reason) {
        if (tellerId == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : ids) {
            Session session = SESSIONS.get(playerId);
            if (session == null || !tellerId.equals(session.tellerId)) {
                continue;
            }
            SESSIONS.remove(playerId);
            if (server == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("§7" + reason));
                sendGoodbye(player);
            }
        }
    }

    private static void showAccountPage(ServerPlayer player, Session session) {
        List<AccountHolder> accounts = getPlayerAccounts(player);
        if (accounts.isEmpty()) {
            cancel(player, "You don't have any bank accounts to deposit into.");
            return;
        }

        int pageCount = Math.max(1, (accounts.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        session.page = Math.max(0, Math.min(session.page, pageCount - 1));
        int from = session.page * PAGE_SIZE;
        int to = Math.min(accounts.size(), from + PAGE_SIZE);

        player.sendSystemMessage(Component.literal("§b[Bank Teller] §fChoose an account for §a$"
                + MoneyText.abbreviate(session.cheque.amount) + "§f:"));
        player.sendSystemMessage(Component.literal("§7Page §f" + (session.page + 1) + "§7/§f" + pageCount));

        for (int i = from; i < to; i++) {
            AccountHolder account = accounts.get(i);
            Bank bank = BankManager.getCentralBank(player.server) != null
                    ? BankManager.getCentralBank(player.server).getBank(account.getBankId())
                    : null;
            String bankName = bank != null ? bank.getBankName() : "Unknown Bank";
            String type = account.getAccountType() != null ? account.getAccountType().label : "Account";
            String suffix = account.isPrimaryAccount() ? " §2[Primary]" : "";

            MutableComponent line = actionButton(
                    "Deposit to " + bankName + " - " + type + suffix,
                    "/bankteller account " + account.getAccountUUID(),
                    ChatFormatting.GREEN,
                    "Deposit cheque into this account"
            );
            line.append(Component.literal(" §8(ID: " + shortId(account.getAccountUUID()) + ")"));
            player.sendSystemMessage(line);
        }

        MutableComponent nav = Component.literal("");
        if (session.page > 0) {
            nav.append(actionButton("Prev", "/bankteller page prev", ChatFormatting.AQUA, "Previous 5 accounts"));
            nav.append(Component.literal("  "));
        }
        if (session.page < pageCount - 1) {
            nav.append(actionButton("Next", "/bankteller page next", ChatFormatting.AQUA, "Next 5 accounts"));
            nav.append(Component.literal("  "));
        }
        nav.append(actionButton("Cancel", "/bankteller cancel", ChatFormatting.RED, "Cancel teller interaction"));
        player.sendSystemMessage(nav);
    }

    private static void sendChoicePrompt(ServerPlayer player, Session session) {
        player.sendSystemMessage(Component.literal("§b[Bank Teller] §fHow would you like to handle this cheque?"));
        player.sendSystemMessage(Component.literal("§7Cheque ID: §f" + session.cheque.chequeId));
        player.sendSystemMessage(Component.literal("§7Amount: §a$" + MoneyText.abbreviate(session.cheque.amount)));

        MutableComponent choices = Component.literal("")
                .append(actionButton("Return To Bank", "/bankteller choose bank", ChatFormatting.GREEN, "Deposit to one of your bank accounts"))
                .append(Component.literal("  "))
                .append(actionButton("Hand In Cash", "/bankteller choose cash", ChatFormatting.GOLD, "Receive physical USD cash"))
                .append(Component.literal("  "))
                .append(actionButton("Cancel", "/bankteller cancel", ChatFormatting.RED, "Cancel teller interaction"));
        player.sendSystemMessage(choices);
        player.sendSystemMessage(Component.literal("§8(You can also cancel by walking away from the teller.)"));
    }

    private static void completeToBank(ServerPlayer player, Session session, AccountHolder destination) {
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            cancel(player, "Bank data is unavailable.");
            return;
        }
        if (centralBank.isChequeRedeemed(session.cheque.chequeId)) {
            cancel(player, "This cheque was already redeemed.");
            return;
        }

        if (!consumeChequeStack(player, session.cheque.chequeId)) {
            cancel(player, "Hold the same cheque in your inventory to complete this action.");
            return;
        }

        if (!destination.AddBalance(session.cheque.amount)) {
            player.sendSystemMessage(Component.literal("§cCould not deposit to the selected account."));
            return;
        }
        centralBank.markChequeRedeemed(session.cheque.chequeId);
        destination.addTransaction(new UserTransaction(
                session.cheque.writerId != null
                        ? session.cheque.writerId
                        : UUID.nameUUIDFromBytes("ultimatebankingsystem:unknown-cheque-writer".getBytes()),
                destination.getAccountUUID(),
                session.cheque.amount,
                LocalDateTime.now(),
                "CHEQUE_TELLER_DEPOSIT:" + session.cheque.chequeId
        ));

        SESSIONS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("§aCheque deposited successfully into account §f"
                + shortId(destination.getAccountUUID()) + "§a."));
        sendGoodbye(player);
    }

    private static void completeAsCash(ServerPlayer player, Session session) {
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            cancel(player, "Bank data is unavailable.");
            return;
        }
        if (centralBank.isChequeRedeemed(session.cheque.chequeId)) {
            cancel(player, "This cheque was already redeemed.");
            return;
        }

        int valueCents;
        try {
            valueCents = session.cheque.amount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .intValueExact();
        } catch (ArithmeticException ex) {
            player.sendSystemMessage(Component.literal("§cCheque value is too large for cash payout."));
            return;
        }
        if (valueCents <= 0) {
            player.sendSystemMessage(Component.literal("§cCheque amount is invalid."));
            return;
        }

        int[] plan = DollarBills.buildCashWithdrawPlan(valueCents);
        if (plan == null) {
            player.sendSystemMessage(Component.literal("§cUnable to prepare cash payout for this amount."));
            return;
        }
        if (!consumeChequeStack(player, session.cheque.chequeId)) {
            cancel(player, "Hold the same cheque in your inventory to complete this action.");
            return;
        }

        centralBank.markChequeRedeemed(session.cheque.chequeId);
        BankManager.markDirty();
        DollarBills.giveCash(player, plan);
        SESSIONS.remove(player.getUUID());
        player.sendSystemMessage(Component.literal("§aCheque cashed out as cash: §f" + DollarBills.formatCashPlan(plan)));
        sendGoodbye(player);
    }

    private static int cancel(ServerPlayer player, String reason) {
        if (SESSIONS.remove(player.getUUID()) == null) {
            player.sendSystemMessage(Component.literal("§7No active teller interaction."));
            return 0;
        }
        player.sendSystemMessage(Component.literal("§e" + reason));
        sendGoodbye(player);
        return 1;
    }

    private static void sendGoodbye(ServerPlayer player) {
        String msg = GOODBYE_MESSAGES[ThreadLocalRandom.current().nextInt(GOODBYE_MESSAGES.length)];
        player.sendSystemMessage(Component.literal(msg));
    }

    private static List<AccountHolder> getPlayerAccounts(ServerPlayer player) {
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            return List.of();
        }
        List<AccountHolder> accounts = new ArrayList<>(centralBank.SearchForAccount(player.getUUID()).values());
        accounts.sort(Comparator
                .comparing(AccountHolder::isPrimaryAccount).reversed()
                .thenComparing(AccountHolder::getDateOfCreation));
        return accounts;
    }

    private static ChequeData readChequeData(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.CHEQUE.get())) {
            return null;
        }
        CompoundTag tag = readCustomTag(stack);
        if (tag == null
                || !tag.contains("ubs_cheque_id")
                || !tag.contains("ubs_cheque_amount")
                || !tag.contains("ubs_cheque_recipient")) {
            return null;
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(tag.getString("ubs_cheque_amount"));
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        UUID recipientId = tag.getUUID("ubs_cheque_recipient");
        UUID writerId = tag.hasUUID("ubs_cheque_writer") ? tag.getUUID("ubs_cheque_writer") : null;
        String recipientName = tag.contains("ubs_cheque_recipient_name") ? tag.getString("ubs_cheque_recipient_name") : "";
        String writerName = tag.contains("ubs_cheque_writer_name") ? tag.getString("ubs_cheque_writer_name") : "";
        return new ChequeData(tag.getString("ubs_cheque_id"), amount, recipientId, writerId, recipientName, writerName);
    }

    private static boolean consumeChequeStack(ServerPlayer player, String chequeId) {
        if (chequeId == null || chequeId.isBlank()) {
            return false;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (!isMatchingChequeStack(stack, chequeId)) {
                continue;
            }
            stack.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (!isMatchingChequeStack(stack, chequeId)) {
                continue;
            }
            stack.shrink(1);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static boolean isMatchingChequeStack(ItemStack stack, String chequeId) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItems.CHEQUE.get())) {
            return false;
        }
        CompoundTag tag = readCustomTag(stack);
        if (tag == null || !tag.contains("ubs_cheque_id")) {
            return false;
        }
        return chequeId.equals(tag.getString("ubs_cheque_id"));
    }

    private static CompoundTag readCustomTag(ItemStack stack) {
        CompoundTag customData = ItemStackDataCompat.getCustomData(stack);
        return customData == null ? null : customData.copy();
    }

    private static MutableComponent actionButton(String label, String command, ChatFormatting color, String hover) {
        return Component.literal("[" + label + "]")
                .withStyle(Style.EMPTY
                        .withColor(color)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }
}
