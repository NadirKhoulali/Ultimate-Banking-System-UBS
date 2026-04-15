package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.austizz.ultimatebankingsystem.gui.screens.ATMScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.BankOwnerPcScreen;
import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.austizz.ultimatebankingsystem.gui.screens.BankTellerScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData;
import net.austizz.ultimatebankingsystem.gui.screens.HandheldTerminalScreen;
import net.austizz.ultimatebankingsystem.gui.screens.OwnerPcScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.ShopTerminalScreen;
import net.austizz.ultimatebankingsystem.gui.screens.layers.AccountSettingsLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.BalanceInquiryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.CreatePayRequestLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.DepositLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.PayRequestsLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.PinEntryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransactionHistoryLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.TransferLayer;
import net.austizz.ultimatebankingsystem.gui.screens.layers.WithdrawLayer;
import net.minecraft.client.Minecraft;

final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    static void handleHudState(HudStatePayload payload) {
        HudClientState.setBalanceText(payload.balance());
        HudClientState.setEnabled(payload.enabled());
    }

    static void handleAccountList(AccountListPayload payload) {
        ClientATMData.setAccounts(payload.accounts());
        ClientATMData.setSelectedAccount(null);
        ClientATMData.setAuthenticatedAccountId(null);
        for (var acc : payload.accounts()) {
            if (acc.isPrimary()) {
                ClientATMData.setSelectedAccount(acc);
                break;
            }
        }
        if (ClientATMData.getSelectedAccount() == null && !payload.accounts().isEmpty()) {
            ClientATMData.setSelectedAccount(payload.accounts().get(0));
        }
        ATMScreenHelper.openATMScreen();
    }

    static void handleBankTellerOpen(BankTellerOpenPayload payload) {
        if (Minecraft.getInstance().screen instanceof BankTellerScreen tellerScreen
                && tellerScreen.hasTeller(payload.tellerId())) {
            tellerScreen.refresh(payload);
            return;
        }
        Minecraft.getInstance().setScreen(new BankTellerScreen(payload));
    }

    static void handleBankTellerActionResponse(BankTellerActionResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankTellerScreen tellerScreen) {
            tellerScreen.handleActionResponse(payload);
        }
    }

    static void handleShopTerminalOpen(ShopTerminalOpenPayload payload) {
        Minecraft.getInstance().setScreen(new ShopTerminalScreen(payload));
    }

    static void handleShopTerminalSaveResponse(ShopTerminalSaveResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof ShopTerminalScreen terminalScreen) {
            terminalScreen.handleSaveResponse(payload);
        }
    }

    static void handleHandheldTerminalOpen(HandheldTerminalOpenPayload payload) {
        Minecraft.getInstance().setScreen(new HandheldTerminalScreen(payload));
    }

    static void handleHandheldTerminalSaveResponse(HandheldTerminalSaveResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof HandheldTerminalScreen terminalScreen) {
            terminalScreen.handleSaveResponse(payload);
        }
    }

    static void handleOwnerPcBootstrap(OwnerPcBootstrapPayload payload) {
        ClientOwnerPcData.setApps(payload.apps(), payload.ownedCount(), payload.maxBanks());
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.refreshFromNetwork();
        } else {
            OwnerPcScreenHelper.openOwnerPcScreen();
        }
    }

    static void handleOwnerPcDesktopData(OwnerPcDesktopDataPayload payload) {
        ClientOwnerPcData.setDesktopData(payload);
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.refreshFromNetwork();
        }
    }

    static void handleOwnerPcDesktopActionResponse(OwnerPcDesktopActionResponsePayload payload) {
        ClientOwnerPcData.setToast(payload.success(), payload.message());
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.handleDesktopActionResponse(payload);
            ownerScreen.refreshFromNetwork();
        }
    }

    static void handleOwnerPcBankData(OwnerPcBankDataPayload payload) {
        ClientOwnerPcData.setCurrentBankData(payload);
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.refreshFromNetwork();
        }
    }

    static void handleOwnerPcActionResponse(OwnerPcActionResponsePayload payload) {
        ClientOwnerPcData.setActionOutput(payload.message());
        String raw = payload.message() == null ? "" : payload.message();
        String toastMessage = raw;
        int firstNewline = raw.indexOf('\n');
        if (firstNewline >= 0) {
            toastMessage = payload.success()
                    ? "Action complete. See output panel for details."
                    : raw.substring(0, firstNewline).trim();
        }
        ClientOwnerPcData.setToast(payload.success(), toastMessage);
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.refreshFromNetwork();
        }
    }

    static void handleOwnerPcCreateBankResponse(OwnerPcCreateBankResponsePayload payload) {
        ClientOwnerPcData.setToast(payload.success(), payload.message());
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ownerScreen.refreshFromNetwork();
        }
    }

    static void handlePinAuthResponse(PinAuthResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof PinEntryLayer layer) {
            layer.updateAuthResult(payload);
        }
    }

    static void handleBalanceResponse(BalanceResponsePayload payload) {
        if (!(Minecraft.getInstance().screen instanceof BankScreen bs)) {
            return;
        }

        if (bs.getTopLayer() instanceof BalanceInquiryLayer balanceLayer) {
            balanceLayer.updateData(payload);
        } else if (bs.getTopLayer() instanceof AccountSettingsLayer settingsLayer) {
            settingsLayer.updateAccountInfo(payload);
        }
    }

    static void handleWithdrawResponse(WithdrawResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof WithdrawLayer layer) {
            layer.updateResult(payload);
        }
    }

    static void handleDepositResponse(DepositResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof DepositLayer layer) {
            layer.updateResult(payload);
        }
    }

    static void handleTransferResponse(TransferResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof TransferLayer layer) {
            layer.updateResult(payload);
        }
    }

    static void handleTxHistoryResponse(TxHistoryResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof TransactionHistoryLayer layer) {
            layer.updateEntries(payload.entries());
        }
    }

    static void handleSetPrimaryResponse(SetPrimaryResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
            layer.updatePrimaryResult(payload);
        }
    }

    static void handleSetTemporaryWithdrawalLimitResponse(SetTemporaryWithdrawalLimitResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
            layer.updateWithdrawalLimitResult(payload);
        }
    }

    static void handleChangePinResponse(ChangePinResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
            layer.updatePinResult(payload);
        } else if (Minecraft.getInstance().screen instanceof BankScreen bs2
                && bs2.getTopLayer() instanceof PinEntryLayer pinLayer) {
            pinLayer.updatePinSetupResult(payload);
        }
    }

    static void handlePayRequestCreateResponse(PayRequestCreateResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof CreatePayRequestLayer layer) {
            layer.updateResult(payload);
        }
    }

    static void handlePayRequestInboxResponse(PayRequestInboxResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof PayRequestsLayer layer) {
            layer.updateInbox(payload.requests(), payload.primaryAccountLabel());
        }
    }

    static void handlePayRequestActionResponse(PayRequestActionResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof PayRequestsLayer layer) {
            layer.updateActionResult(payload);
        }
    }
}
