package net.austizz.ultimatebankingsystem.npc;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.BankTellerActionResponsePayload;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
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
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BankTellerPaymentInteractionManager {

    private static final double CANCEL_DISTANCE_SQ = 8.0D * 8.0D;
    private static final long SESSION_TIMEOUT_TICKS = 20L * 180L;
    private static final UUID BANK_TELLER_TERMINAL_ID = UUID.nameUUIDFromBytes(
            "ultimatebankingsystem:bank-teller".getBytes(StandardCharsets.UTF_8)
    );

    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final class Session {
        private final UUID playerId;
        private final UUID tellerId;
        private final String tellerDimension;
        private final long startedTick;
        private final String action;
        private final String accountId;
        private final String amount;
        private final String recipient;
        private final boolean confirmed;
        private final long requiredCents;
        private final String paymentReason;
        private final int[] insertedCash = new int[DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length];
        private long cashPaidCents = 0L;
        private long cardPaidCents = 0L;
        private UUID cardChargedAccountId;

        private Session(UUID playerId,
                        UUID tellerId,
                        String tellerDimension,
                        long startedTick,
                        String action,
                        String accountId,
                        String amount,
                        String recipient,
                        boolean confirmed,
                        long requiredCents,
                        String paymentReason) {
            this.playerId = playerId;
            this.tellerId = tellerId;
            this.tellerDimension = tellerDimension;
            this.startedTick = startedTick;
            this.action = action;
            this.accountId = accountId;
            this.amount = amount;
            this.recipient = recipient;
            this.confirmed = confirmed;
            this.requiredCents = requiredCents;
            this.paymentReason = paymentReason;
        }
    }

    private BankTellerPaymentInteractionManager() {}

    public static BankTellerService.ActionResult beginSession(ServerPlayer player,
                                                              BankTellerEntity teller,
                                                              String action,
                                                              String accountId,
                                                              String amount,
                                                              String recipient,
                                                              boolean confirmed,
                                                              BigDecimal requiredAmount,
                                                              String paymentReason) {
        if (player == null || teller == null || requiredAmount == null) {
            return BankTellerService.ActionResult.fail("Payment session could not be started.");
        }
        if (requiredAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return BankTellerService.ActionResult.fail("No payment is required.");
        }

        long requiredCents;
        try {
            requiredCents = requiredAmount.setScale(2, java.math.RoundingMode.UNNECESSARY)
                    .movePointRight(2)
                    .longValueExact();
        } catch (ArithmeticException ex) {
            return BankTellerService.ActionResult.fail("Cash/card teller payment supports up to 2 decimal places.");
        }
        if (requiredCents <= 0L) {
            return BankTellerService.ActionResult.fail("Payment amount is invalid.");
        }

        Session existing = SESSIONS.remove(player.getUUID());
        if (existing != null) {
            refundSession(existing, player, "Previous teller payment request was replaced.");
        }

        Session session = new Session(
                player.getUUID(),
                teller.getUUID(),
                teller.level().dimension().location().toString(),
                teller.level().getGameTime(),
                action == null ? "" : action.trim(),
                accountId == null ? "" : accountId.trim(),
                amount == null ? "" : amount.trim(),
                recipient == null ? "" : recipient.trim(),
                confirmed,
                requiredCents,
                paymentReason == null ? "Teller payment" : paymentReason
        );
        SESSIONS.put(player.getUUID(), session);
        sendPaymentPrompt(player, session);
        return BankTellerService.ActionResult.beginExternalPayment("Payment started. Right-click the teller with cash or a credit card.");
    }

    public static boolean handleInteract(ServerPlayer player, BankTellerEntity teller, InteractionHand hand) {
        if (player == null || teller == null) {
            return false;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (!session.tellerId.equals(teller.getUUID())) {
            player.sendSystemMessage(Component.literal("§eYou already have an active teller payment at another teller."));
            return true;
        }

        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            cancelAndCleanup(player, "Bank data is unavailable.", false, false);
            return true;
        }

        ItemStack held = player.getItemInHand(hand);
        int cashIndex = held == null || held.isEmpty() ? -1 : DollarBills.cashIndexForItem(held.getItem());
        if (cashIndex >= 0) {
            processCashTender(player, teller, session, held, cashIndex);
            return true;
        }

        if (held != null && !held.isEmpty() && held.is(ModItems.CREDIT_CARD.get())) {
            processCardPayment(player, teller, session, centralBank);
            return true;
        }

        player.sendSystemMessage(Component.literal("§7Use §aCash (bills/coins)§7 or a §bCredit Card§7 on the teller to continue payment."));
        player.sendSystemMessage(Component.literal("§8Remaining: §6$" + MoneyText.abbreviate(BigDecimal.valueOf(remainingCents(session), 2))));
        return true;
    }

    public static int handleCancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendSystemMessage(Component.literal("§cOnly players can do this."));
            return 0;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return 0;
        }
        cancelAndCleanup(player, "Payment cancelled. Any inserted cash has been returned.", true, true);
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (server == null || SESSIONS.isEmpty()) {
            return;
        }

        List<UUID> playerIds = new ArrayList<>(SESSIONS.keySet());
        for (UUID playerId : playerIds) {
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
                cancelAndCleanup(player, "You moved away from the teller. Payment cancelled.", false, true);
                continue;
            }
            Entity entity = level.getEntity(session.tellerId);
            if (!(entity instanceof BankTellerEntity teller)) {
                cancelAndCleanup(player, "This teller is no longer available. Payment cancelled.", false, true);
                continue;
            }
            if (player.distanceToSqr(teller) > CANCEL_DISTANCE_SQ) {
                cancelAndCleanup(player, "You walked too far from the teller. Payment cancelled.", false, true);
                continue;
            }
            if (Math.abs(level.getGameTime() - session.startedTick) > SESSION_TIMEOUT_TICKS) {
                cancelAndCleanup(player, "Payment request timed out.", true, true);
            }
        }
    }

    public static void cancelForTeller(UUID tellerId, String reason) {
        if (tellerId == null) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<UUID> ids = new ArrayList<>(SESSIONS.keySet());
        for (UUID id : ids) {
            Session session = SESSIONS.get(id);
            if (session == null || !tellerId.equals(session.tellerId)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                cancelAndCleanup(player, reason == null ? "Payment session cancelled." : reason, false, true);
            } else {
                SESSIONS.remove(id);
            }
        }
    }

    private static void processCashTender(ServerPlayer player,
                                          BankTellerEntity teller,
                                          Session session,
                                          ItemStack stack,
                                          int cashIndex) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, teller, session);
            return;
        }

        int denominationCents = DollarBills.cashDenominationCentsForIndex(cashIndex);
        stack.shrink(1);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();

        session.insertedCash[cashIndex] += 1;
        session.cashPaidCents += denominationCents;

        long newRemaining = remainingCents(session);
        if (newRemaining <= 0L) {
            completeSession(player, teller, session);
            return;
        }

        player.sendSystemMessage(Component.literal("§aAccepted §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(denominationCents, 2))
                + "§a cash. Remaining: §6$"
                + MoneyText.abbreviate(BigDecimal.valueOf(newRemaining, 2))));
        sendCancelHint(player);
    }

    private static void processCardPayment(ServerPlayer player,
                                           BankTellerEntity teller,
                                           Session session,
                                           CentralBank centralBank) {
        long remaining = remainingCents(session);
        if (remaining <= 0L) {
            completeSession(player, teller, session);
            return;
        }

        var cardLookup = CreditCardService.findHeldCard(centralBank, player);
        if (!cardLookup.hasCard()) {
            player.sendSystemMessage(Component.literal("§cNo credit card detected. Hold a card and right-click the teller."));
            return;
        }
        if (!cardLookup.validation().valid()) {
            player.sendSystemMessage(Component.literal("§cCard payment failed: " + cardLookup.validation().message()));
            return;
        }

        AccountHolder payer = centralBank.SearchForAccountByAccountId(cardLookup.validation().accountId());
        if (payer == null || !player.getUUID().equals(payer.getPlayerUUID())) {
            player.sendSystemMessage(Component.literal("§cCard payment failed: linked account is unavailable."));
            return;
        }

        BigDecimal charge = BigDecimal.valueOf(remaining, 2);
        if (!payer.RemoveBalance(charge)) {
            player.sendSystemMessage(Component.literal("§cCard payment failed: insufficient funds for §6$"
                    + MoneyText.abbreviate(charge) + "§c."));
            return;
        }

        payer.addTransaction(new UserTransaction(
                payer.getAccountUUID(),
                BANK_TELLER_TERMINAL_ID,
                charge,
                LocalDateTime.now(),
                "TELLER_FEE_CARD:" + session.action
        ));

        session.cardPaidCents += remaining;
        session.cardChargedAccountId = payer.getAccountUUID();
        completeSession(player, teller, session);
    }

    private static void completeSession(ServerPlayer player, BankTellerEntity teller, Session session) {
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            cancelAndCleanup(player, "Bank data is unavailable.", false, true);
            return;
        }

        long totalPaid = session.cashPaidCents + session.cardPaidCents;
        long required = Math.max(0L, session.requiredCents);
        long changeCents = Math.max(0L, totalPaid - required);
        int[] changePlan = null;
        if (changeCents > 0L) {
            int changeIntCents;
            try {
                changeIntCents = Math.toIntExact(changeCents);
            } catch (ArithmeticException ex) {
                refundSession(session, player, "Payment was refunded: change amount is too large.");
                SESSIONS.remove(player.getUUID());
                reopenWithFeedback(player, teller, false, "Payment cancelled: unable to process change.");
                return;
            }
            changePlan = DollarBills.buildCashWithdrawPlan(changeIntCents);
            if (changePlan == null) {
                refundSession(session, player, "Payment was refunded: unable to prepare change.");
                SESSIONS.remove(player.getUUID());
                reopenWithFeedback(player, teller, false, "Payment cancelled: unable to prepare change cash.");
                return;
            }
        }
        BigDecimal paidAmount = BigDecimal.valueOf(totalPaid, 2);
        String method;
        if (session.cashPaidCents > 0 && session.cardPaidCents > 0) {
            method = "cash + card";
        } else if (session.cardPaidCents > 0) {
            method = "card";
        } else {
            method = "cash";
        }

        BankTellerService.ActionResult result = BankTellerService.completeManualPaymentAction(
                player.server,
                centralBank,
                player,
                teller.getUUID(),
                session.action,
                session.accountId,
                session.amount,
                session.recipient,
                session.confirmed,
                new BankTellerService.ExternalFeePayment(paidAmount, method, session.cardChargedAccountId)
        );

        if (!result.success()) {
            refundSession(session, player, "Payment was refunded: action could not be completed.");
            SESSIONS.remove(player.getUUID());
            reopenWithFeedback(player, teller, false, result.message());
            return;
        }

        if (changePlan != null) {
            DollarBills.giveCash(player, changePlan);
        }
        SESSIONS.remove(player.getUUID());
        String changeLine = (changePlan == null)
                ? ""
                : " Change returned: " + DollarBills.formatCashPlan(changePlan) + ".";
        reopenWithFeedback(player, teller, true, "Payment complete via " + method + "." + changeLine + " " + result.message());
    }

    private static void cancelAndCleanup(ServerPlayer player,
                                         String reason,
                                         boolean reopenUi,
                                         boolean refundPayment) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) {
            return;
        }

        if (refundPayment) {
            refundSession(session, player, "Payment refunded.");
        }

        if (reopenUi) {
            BankTellerEntity teller = findTeller(player.server, session.tellerId);
            if (teller != null && teller.isAlive()
                    && player.level() == teller.level()
                    && player.distanceToSqr(teller) <= CANCEL_DISTANCE_SQ) {
                reopenWithFeedback(player, teller, false, reason);
                return;
            }
        }

        player.sendSystemMessage(Component.literal("§e" + reason));
    }

    private static void reopenWithFeedback(ServerPlayer player,
                                           BankTellerEntity teller,
                                           boolean success,
                                           String message) {
        if (player == null || teller == null || player.server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.server);
        if (centralBank == null) {
            player.sendSystemMessage(Component.literal("§e" + message));
            return;
        }
        PacketDistributor.sendToPlayer(player, BankTellerService.buildOpenPayload(player.server, centralBank, player, teller));
        PacketDistributor.sendToPlayer(player, new BankTellerActionResponsePayload(success, message == null ? "" : message, false));
    }

    private static void refundSession(Session session, ServerPlayer player, String infoMessage) {
        if (session == null || player == null || player.server == null) {
            return;
        }

        if (session.cashPaidCents > 0L) {
            DollarBills.giveCash(player, session.insertedCash);
        }

        if (session.cardPaidCents > 0L && session.cardChargedAccountId != null) {
            CentralBank centralBank = BankManager.getCentralBank(player.server);
            if (centralBank != null) {
                AccountHolder cardAccount = centralBank.SearchForAccountByAccountId(session.cardChargedAccountId);
                if (cardAccount != null) {
                    BigDecimal refund = BigDecimal.valueOf(session.cardPaidCents, 2);
                    cardAccount.AddBalance(refund);
                    cardAccount.addTransaction(new UserTransaction(
                            BANK_TELLER_TERMINAL_ID,
                            cardAccount.getAccountUUID(),
                            refund,
                            LocalDateTime.now(),
                            "TELLER_FEE_CARD_REFUND:" + session.action
                    ));
                }
            }
        }

        if (infoMessage != null && !infoMessage.isBlank()) {
            player.sendSystemMessage(Component.literal("§7" + infoMessage));
        }
    }

    private static void sendPaymentPrompt(ServerPlayer player, Session session) {
        if (player == null || session == null) {
            return;
        }
        player.sendSystemMessage(Component.literal("§b[Bank Teller] §f" + session.paymentReason));
        player.sendSystemMessage(Component.literal("§7Amount due: §6$" + MoneyText.abbreviate(BigDecimal.valueOf(session.requiredCents, 2))));
        player.sendSystemMessage(Component.literal("§7Right-click this teller with §aCash (bills/coins)§7 (one item per click) or with a §bCredit Card§7."));
        sendCancelHint(player);
    }

    private static void sendCancelHint(ServerPlayer player) {
        MutableComponent cancel = Component.literal("")
                .append(Component.literal("[Cancel Payment]").withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bankteller cancel"))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Cancel teller payment and refund")))));
        player.sendSystemMessage(cancel);
        player.sendSystemMessage(Component.literal("§8(You can also cancel by walking away from the teller.)"));
    }

    private static long remainingCents(Session session) {
        long paid = session.cashPaidCents + session.cardPaidCents;
        long remaining = session.requiredCents - paid;
        return Math.max(0L, remaining);
    }

    private static BankTellerEntity findTeller(MinecraftServer server, UUID tellerId) {
        if (server == null || tellerId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tellerId);
            if (entity instanceof BankTellerEntity teller) {
                return teller;
            }
        }
        return null;
    }
}
