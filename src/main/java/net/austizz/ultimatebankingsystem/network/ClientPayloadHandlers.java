package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.client.ActionAlertClientState;
import net.austizz.ultimatebankingsystem.client.DeliveryAlertClientState;
import net.austizz.ultimatebankingsystem.client.DeliveryInfoBoardClientState;
import net.austizz.ultimatebankingsystem.client.DeliveryPalletLabelsClientState;
import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.austizz.ultimatebankingsystem.client.PickpocketClientState;
import net.austizz.ultimatebankingsystem.client.ShopSetupObjectiveClientState;
import net.austizz.ultimatebankingsystem.client.ShelfTransformPreviewClientState;
import net.austizz.ultimatebankingsystem.client.StockroomLocateClientState;
import net.austizz.ultimatebankingsystem.gui.screens.ATMScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.BankOwnerPcScreen;
import net.austizz.ultimatebankingsystem.gui.screens.BankScreen;
import net.austizz.ultimatebankingsystem.gui.screens.BankTellerScreen;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.gui.screens.ClientOwnerPcData;
import net.austizz.ultimatebankingsystem.gui.screens.HandheldTerminalScreen;
import net.austizz.ultimatebankingsystem.gui.screens.OwnerPcScreenHelper;
import net.austizz.ultimatebankingsystem.gui.screens.ShelfScreen;
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

    static void handlePickpocketState(PickpocketStatePayload payload) {
        PickpocketClientState.apply(payload);
    }

    static void handleStockroomLocateRender(StockroomLocateRenderPayload payload) {
        if (payload == null || !payload.active()) {
            StockroomLocateClientState.clear();
            return;
        }
        StockroomLocateClientState.set(
                true,
                payload.dimensionId(),
                payload.x(),
                payload.y(),
                payload.z(),
                payload.slot()
        );
    }

    static void handleDeliveryAlert(DeliveryAlertPayload payload) {
        if (payload == null) {
            DeliveryAlertClientState.clear();
            return;
        }
        ActionAlertClientState.Tone tone = switch (payload.tone()) {
            case SUCCESS -> ActionAlertClientState.Tone.SUCCESS;
            case ERROR -> ActionAlertClientState.Tone.ERROR;
            case INFO -> ActionAlertClientState.Tone.INFO;
            case WARNING -> ActionAlertClientState.Tone.WARNING;
        };
        ActionAlertClientState.show(payload.title(), payload.message(), tone, payload.durationMs());
    }

    static void handleShopSetupObjective(ShopSetupObjectivePayload payload) {
        if (payload == null || !payload.active()) {
            ShopSetupObjectiveClientState.clear();
            return;
        }
        ShopSetupObjectiveClientState.set(
                true,
                payload.shopName(),
                payload.step(),
                payload.totalSteps(),
                payload.objectiveTitle(),
                payload.objectiveDetail(),
                payload.requirements()
        );
    }

    static void handleDeliveryInfoBoard(DeliveryInfoBoardPayload payload) {
        if (payload == null || !payload.active()) {
            DeliveryInfoBoardClientState.clear();
            return;
        }
        DeliveryInfoBoardClientState.set(
                true,
                payload.shopName(),
                payload.itemName(),
                payload.quantity(),
                payload.rewardCents(),
                payload.remainingSeconds(),
                payload.timeoutMinutes(),
                payload.activeOrders(),
                payload.activeCap(),
                payload.dropTarget(),
                payload.distanceLabel(),
                payload.rankLabel(),
                payload.streak(),
                payload.successRatePct(),
                payload.completedOrders(),
                payload.totalPayoutCents()
        );
    }

    static void handleDeliveryPalletLabels(DeliveryPalletLabelsPayload payload) {
        if (payload == null) {
            DeliveryPalletLabelsClientState.clear();
            return;
        }
        DeliveryPalletLabelsClientState.set(payload.dimensionId(), payload.labels());
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
        pushActionAlert(payload.success(), payload.message(), "Bank Teller");
    }

    static void handleShopTerminalOpen(ShopTerminalOpenPayload payload) {
        Minecraft.getInstance().setScreen(new ShopTerminalScreen(payload));
    }

    static void handleShopTerminalSaveResponse(ShopTerminalSaveResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof ShopTerminalScreen terminalScreen) {
            terminalScreen.handleSaveResponse(payload);
        }
        pushActionAlert(payload.success(), payload.message(), "Payment Terminal");
    }

    static void handleHandheldTerminalOpen(HandheldTerminalOpenPayload payload) {
        Minecraft.getInstance().setScreen(new HandheldTerminalScreen(payload));
    }

    static void handleHandheldTerminalSaveResponse(HandheldTerminalSaveResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof HandheldTerminalScreen terminalScreen) {
            terminalScreen.handleSaveResponse(payload);
        }
        pushActionAlert(payload.success(), payload.message(), "Handheld Terminal");
    }

    static void handleShelfOpen(ShelfOpenPayload payload) {
        if (payload != null) {
            // Fresh server shelf state arrived; drop any stale local transform previews.
            ShelfTransformPreviewClientState.clearForDimension(payload.dimensionId());
        }
        if (payload == null || payload.shelves() == null || payload.shelves().isEmpty()) {
            if (Minecraft.getInstance().screen instanceof ShelfScreen) {
                Minecraft.getInstance().setScreen(null);
            }
            return;
        }
        if (Minecraft.getInstance().screen instanceof ShelfScreen shelfScreen) {
            shelfScreen.refresh(payload);
            return;
        }
        Minecraft.getInstance().setScreen(new ShelfScreen(payload));
    }

    static void handleShelfActionResponse(ShelfActionResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof ShelfScreen shelfScreen) {
            shelfScreen.handleActionResponse(payload);
        }
        pushActionAlert(payload.success(), payload.message(), "Shelf Manager");
    }

    static void handleOwnerPcBootstrap(OwnerPcBootstrapPayload payload) {
        ClientOwnerPcData.setApps(payload.apps(), payload.ownedCount(), payload.maxBanks());
        if (Minecraft.getInstance().screen instanceof BankOwnerPcScreen ownerScreen) {
            ClientOwnerPcData.consumeSuppressNextOwnerPcAutoOpen();
            ownerScreen.refreshFromNetwork();
        } else {
            if (ClientOwnerPcData.consumeSuppressNextOwnerPcAutoOpen()) {
                return;
            }
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
        String action = payload.action() == null ? "" : payload.action().trim().toUpperCase(java.util.Locale.ROOT);
        if (action.startsWith("ORDER_BOARD_")) {
            ClientOwnerPcData.setActionOutput(payload.message());
            if ("ORDER_BOARD_REPORT".equals(action)) {
                // Opening the Order Board performs an automatic refresh; suppress that first toast.
                if (ClientOwnerPcData.consumeSuppressNextOrderBoardReportToast()) {
                    ClientOwnerPcData.setToast(payload.success(), "");
                } else {
                    String raw = payload.message() == null ? "" : payload.message();
                    String toastMessage = "Order board refreshed.";
                    if (!payload.success()) {
                        int firstNewline = raw.indexOf('\n');
                        toastMessage = firstNewline >= 0 ? raw.substring(0, firstNewline).trim() : raw;
                    }
                    ClientOwnerPcData.setToast(payload.success(), toastMessage, payload.success() ? 1000 : 2000);
                }
            } else {
                ClientOwnerPcData.setToast(payload.success(), payload.message());
            }
        } else if (action.startsWith("SHOP_")) {
            ClientOwnerPcData.setActionOutput(payload.message());
            String raw = payload.message() == null ? "" : payload.message();
            int firstNewline = raw.indexOf('\n');
            if (payload.success()) {
                if (isShopPanelRefreshAction(action)) {
                    ClientOwnerPcData.setToast(true, shopRefreshToast(action), 1000);
                } else if (firstNewline >= 0) {
                    // Menu switches often return multi-line panel payloads; suppress noisy success toasts.
                    ClientOwnerPcData.setToast(true, "");
                } else {
                    ClientOwnerPcData.setToast(true, raw);
                }
            } else {
                String toastMessage = firstNewline >= 0 ? raw.substring(0, firstNewline).trim() : raw;
                ClientOwnerPcData.setToast(false, toastMessage);
            }
        } else {
            ClientOwnerPcData.setToast(payload.success(), payload.message());
        }
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
        String message = payload.success()
                ? "Withdrawal completed. New balance: " + payload.newBalance()
                : payload.errorMessage();
        pushActionAlert(payload.success(), message, "ATM Withdrawal");
    }

    static void handleDepositResponse(DepositResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof DepositLayer layer) {
            layer.updateResult(payload);
        }
        String message = payload.success()
                ? "Deposit completed. New balance: " + payload.newBalance()
                : payload.errorMessage();
        pushActionAlert(payload.success(), message, "ATM Deposit");
    }

    static void handleTransferResponse(TransferResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof TransferLayer layer) {
            layer.updateResult(payload);
        }
        String message = payload.success()
                ? "Transfer completed. New balance: " + payload.newBalance()
                : payload.errorMessage();
        pushActionAlert(payload.success(), message, "Transfer");
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
        String message = payload.success()
                ? (payload.newPrimaryState() ? "Account set as primary." : "Account is no longer primary.")
                : "Could not update primary account.";
        pushActionAlert(payload.success(), message, "Account");
    }

    static void handleSetTemporaryWithdrawalLimitResponse(SetTemporaryWithdrawalLimitResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
            layer.updateWithdrawalLimitResult(payload);
        }
        String message = payload.success()
                ? "Withdrawal limit updated. Effective: " + payload.effectiveLimit()
                : payload.errorMessage();
        pushActionAlert(payload.success(), message, "Withdrawal Limit");
    }

    static void handleChangePinResponse(ChangePinResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof AccountSettingsLayer layer) {
            layer.updatePinResult(payload);
        } else if (Minecraft.getInstance().screen instanceof BankScreen bs2
                && bs2.getTopLayer() instanceof PinEntryLayer pinLayer) {
            pinLayer.updatePinSetupResult(payload);
        }
        String message = payload.success() ? "PIN updated successfully." : payload.errorMessage();
        pushActionAlert(payload.success(), message, "PIN");
    }

    static void handlePayRequestCreateResponse(PayRequestCreateResponsePayload payload) {
        if (Minecraft.getInstance().screen instanceof BankScreen bs
                && bs.getTopLayer() instanceof CreatePayRequestLayer layer) {
            layer.updateResult(payload);
        }
        pushActionAlert(payload.success(), payload.message(), "Pay Request");
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
        pushActionAlert(payload.success(), payload.message(), "Pay Request");
    }

    private static void pushActionAlert(boolean success, String message, String title) {
        if (message == null || message.isBlank()) {
            return;
        }
        String trimmed = message.trim();
        ActionAlertClientState.Tone tone = inferAlertTone(success, trimmed);
        int durationMs = tone == ActionAlertClientState.Tone.ERROR ? 5600 : 4600;
        ActionAlertClientState.show(
                title == null || title.isBlank() ? defaultAlertTitle(tone) : title.trim(),
                trimmed,
                tone,
                durationMs
        );
    }

    private static ActionAlertClientState.Tone inferAlertTone(boolean success, String message) {
        if (!success) {
            return ActionAlertClientState.Tone.ERROR;
        }
        String normalized = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("warning")
                || normalized.contains("limit")
                || normalized.contains("already")
                || normalized.contains("queued")
                || normalized.contains("wait")) {
            return ActionAlertClientState.Tone.WARNING;
        }
        if (normalized.contains("selected")
                || normalized.contains("copied")
                || normalized.contains("refresh")
                || normalized.contains("locating")) {
            return ActionAlertClientState.Tone.INFO;
        }
        return ActionAlertClientState.Tone.SUCCESS;
    }

    private static String defaultAlertTitle(ActionAlertClientState.Tone tone) {
        return switch (tone) {
            case ERROR -> "Action Failed";
            case WARNING -> "Warning";
            case INFO -> "Info";
            case SUCCESS -> "Success";
        };
    }

    private static boolean isShopPanelRefreshAction(String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return switch (action) {
            case "SHOP_OVERVIEW",
                    "SHOP_CHECKOUT_DIAGNOSTIC",
                    "SHOP_ORDER_REPORT",
                    "SHOP_SCAN",
                    "SHOP_STOCKROOM_REPORT",
                    "SHOP_FINANCE_REPORT",
                    "SHOP_WEBSHOP_REPORT" -> true;
            default -> false;
        };
    }

    private static String shopRefreshToast(String action) {
        if (action == null || action.isBlank()) {
            return "Shop panel refreshed.";
        }
        return switch (action) {
            case "SHOP_OVERVIEW" -> "Dashboard refreshed.";
            case "SHOP_CHECKOUT_DIAGNOSTIC" -> "Checkout diagnostic refreshed.";
            case "SHOP_ORDER_REPORT" -> "Orders refreshed.";
            case "SHOP_SCAN" -> "Inventory scan refreshed.";
            case "SHOP_STOCKROOM_REPORT" -> "Stockroom refreshed.";
            case "SHOP_FINANCE_REPORT" -> "Finance refreshed.";
            case "SHOP_WEBSHOP_REPORT" -> "Webshop refreshed.";
            default -> "Shop panel refreshed.";
        };
    }
}
