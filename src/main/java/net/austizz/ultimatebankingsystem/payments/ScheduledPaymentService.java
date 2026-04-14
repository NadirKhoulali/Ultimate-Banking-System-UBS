package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public final class ScheduledPaymentService {
    private ScheduledPaymentService() {}

    public static void process(MinecraftServer server, long gameTime) {
        if (server == null) {
            return;
        }
        var centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        List<ScheduledPayment> duePayments = centralBank.getScheduledPayments().values().stream()
                .filter(ScheduledPayment::isActive)
                .filter(p -> gameTime >= p.getNextExecutionGameTime())
                .sorted(Comparator.comparingLong(ScheduledPayment::getNextExecutionGameTime))
                .toList();

        for (ScheduledPayment payment : duePayments) {
            if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
                payment.setNextExecutionGameTime(gameTime + Math.max(20L, payment.getFrequencyTicks()));
                continue;
            }

            UserTransaction tx = new UserTransaction(
                    payment.getSourceAccountId(),
                    payment.getTargetAccountId(),
                    payment.getAmount(),
                    LocalDateTime.now(),
                    "SCHEDULED_PAYMENT"
            );
            boolean success = tx.makeTransaction(server);
            if (!success) {
                for (ServerPlayer online : server.getPlayerList().getPlayers()) {
                    if (online.hasPermissions(3)) {
                        online.sendSystemMessage(Component.literal(
                                "§c[UBS] Scheduled payment failed: " + payment.getPaymentId()
                                        + " (" + MoneyText.abbreviateWithDollar(payment.getAmount()) + ")"
                        ));
                    }
                }
            }

            long next = payment.getNextExecutionGameTime();
            long interval = Math.max(20L, payment.getFrequencyTicks());
            while (next <= gameTime) {
                next += interval;
            }
            payment.setNextExecutionGameTime(next);
            BankManager.markDirty();
        }
    }
}
