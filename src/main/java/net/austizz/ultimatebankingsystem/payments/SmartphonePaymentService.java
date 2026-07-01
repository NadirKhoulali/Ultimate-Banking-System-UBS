package net.austizz.ultimatebankingsystem.payments;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.phone.SmartphoneService;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class SmartphonePaymentService {
    private SmartphonePaymentService() {
    }

    public static boolean shouldHandleAsPhonePayment(ServerPlayer player) {
        return SmartphoneService.resolveTapAccountId(player) != null;
    }

    public static AccountHolder resolveTapAccount(ServerPlayer player, CentralBank centralBank) {
        UUID accountId = SmartphoneService.resolveTapAccountId(player);
        return centralBank == null || accountId == null ? null : centralBank.SearchForAccountByAccountId(accountId);
    }

    public static String unavailableMessage(ServerPlayer player) {
        return SmartphoneService.findUsablePhone(player).isEmpty()
                ? "No smartphone in inventory."
                : "Phone has no usable Tap to Pay account.";
    }
}
