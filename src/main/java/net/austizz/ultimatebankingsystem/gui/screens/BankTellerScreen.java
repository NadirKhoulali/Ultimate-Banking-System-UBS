package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.BankTellerAccountSummary;
import net.austizz.ultimatebankingsystem.network.BankTellerActionPayload;
import net.austizz.ultimatebankingsystem.network.BankTellerActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.BankTellerOpenPayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class BankTellerScreen extends Screen {

    private enum Tab {
        INSTRUMENTS("Cash & Instruments"),
        CHEQUE_REDEEM("Redeem Cashables"),
        CARD("Credit Card"),
        SAFE_BOX("Safe Box"),
        OPEN_ACCOUNT("Open Account");

        private final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private enum PaymentMode {
        ACCOUNT("Use Account", "ACCOUNT"),
        CASH_OR_CARD("Pay Cash/Card", "CASH_OR_CARD");

        private final String label;
        private final String token;

        PaymentMode(String label, String token) {
            this.label = label;
            this.token = token;
        }
    }

    private record HoverHint(String title, String description) {
    }

    private record HoverBinding(AbstractWidget widget, HoverHint hint) {
    }

    private BankTellerOpenPayload payload;
    private final List<BankTellerAccountSummary> accounts = new ArrayList<>();
    private final List<HoverBinding> hoverBindings = new ArrayList<>();

    private Tab activeTab = Tab.INSTRUMENTS;
    private PaymentMode paymentMode = PaymentMode.ACCOUNT;
    private int selectedAccountIndex = -1;

    private DesktopEditBox amountInput;
    private DesktopEditBox recipientInput;

    private DesktopButton accountPrevButton;
    private DesktopButton accountNextButton;
    private DesktopButton accountSelectButton;
    private DesktopButton payByAccountButton;
    private DesktopButton payByCashCardButton;

    private DesktopButton confirmReplaceButton;
    private DesktopButton cancelReplaceButton;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int headerTop;
    private int headerHeight;
    private int tabContentBottomY;
    private int tabInfoY;
    private int tabInfoLeft;
    private int tabInfoWidth;
    private int tabInfoBottom;
    private boolean tabInfoVisible;
    private int contentLeft;
    private int contentWidth;

    private String amountDraft = "";
    private String recipientDraft = "";

    private boolean replaceConfirmOpen = false;

    private boolean feedbackSuccess = true;
    private String feedbackMessage = "";
    private long feedbackUntilMillis = 0L;
    private final long openedAtMillis = System.currentTimeMillis();

    private int openTypeIndex = 0;
    private int openTierIndex = 0;

    private static final String[] OPEN_TYPE_TOKENS = {"checking", "saving", "moneymarket", "certificate"};
    private static final String[] OPEN_TYPE_LABELS = {
            "Checking Account",
            "Saving Account",
            "Money Market Account",
            "Certificate Account"
    };
    private static final String[] OPEN_TIER_TOKENS = {"short", "medium", "long"};
    private static final String[] OPEN_TIER_LABELS = {"Short", "Medium", "Long"};

    public BankTellerScreen(BankTellerOpenPayload payload) {
        super(Component.literal(tr("Bank Teller")));
        this.payload = payload;
        resetAccountsFromPayload();
    }

    public boolean hasTeller(UUID tellerId) {
        return tellerId != null && payload != null && tellerId.equals(payload.tellerId());
    }

    public void refresh(BankTellerOpenPayload updated) {
        UUID selectedBefore = getSelectedAccountId();
        this.payload = updated;
        resetAccountsFromPayload();
        if (selectedBefore != null) {
            for (int i = 0; i < accounts.size(); i++) {
                if (selectedBefore.equals(accounts.get(i).accountId())) {
                    selectedAccountIndex = i;
                    break;
                }
            }
        }
        if (!accounts.isEmpty() && (selectedAccountIndex < 0 || selectedAccountIndex >= accounts.size())) {
            selectedAccountIndex = 0;
        }
        rebuildWidgets();
    }

    public void handleActionResponse(BankTellerActionResponsePayload response) {
        this.feedbackSuccess = response.success();
        this.feedbackMessage = response.message() == null ? "" : response.message();
        this.feedbackUntilMillis = System.currentTimeMillis() + 5500L;
        if (response.closeScreen()) {
            this.onClose();
        }
    }

    @Override
    protected void init() {
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        hoverBindings.clear();
        this.confirmReplaceButton = null;
        this.cancelReplaceButton = null;

        panelWidth = Math.min(860, Math.max(560, this.width - 32));
        panelHeight = Math.min(520, Math.max(380, this.height - 30));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        contentLeft = panelLeft + 14;
        contentWidth = panelWidth - 28;
        headerTop = panelTop + 42;
        headerHeight = panelHeight >= 430 ? 96 : 84;

        int rowY = headerTop + headerHeight + 6;
        int gap = 8;
        tabContentBottomY = rowY;

        int closeW = 110;
        addHintedButton(
                panelLeft + panelWidth - closeW - 12,
                panelTop + 12,
                closeW,
                22,
                "Close",
                0xFF85B8E8,
                btn -> onClose(),
                "Close Teller UI",
                "Exit this teller terminal."
        );

        int tabW = (contentWidth - (gap * 4)) / 5;
        addTabButton(contentLeft, rowY, tabW, Tab.INSTRUMENTS,
                "Cash and paper instruments",
                "Issue cheque/note, withdraw cash, deposit exact cash, or deposit all carried bills and coins.");
        addTabButton(contentLeft + tabW + gap, rowY, tabW, Tab.CHEQUE_REDEEM,
                "Redeem cheque or note",
                "Convert held cheque/bank-note to account balance or physical cash.");
        addTabButton(contentLeft + (tabW + gap) * 2, rowY, tabW, Tab.CARD,
                "Credit cards",
                "Issue a first card or replace existing cards for the selected eligible account.");
        addTabButton(contentLeft + (tabW + gap) * 3, rowY, tabW, Tab.SAFE_BOX,
                "Safety deposit boxes",
                "Request a physical safety deposit box for the selected account at this teller bank.");
        addTabButton(contentLeft + (tabW + gap) * 4, rowY, tabW, Tab.OPEN_ACCOUNT,
                "Open account",
                "Create a new account at this teller bank. Central Bank teller account opening can be free.");
        rowY += 32;

        int paymentW = (contentWidth - gap) / 2;
        payByCashCardButton = addHintedButton(
                contentLeft,
                rowY,
                paymentW,
                22,
                PaymentMode.CASH_OR_CARD.label,
                paymentMode == PaymentMode.CASH_OR_CARD ? 0xFF8DDDB1 : 0xFF6BAEE8,
                btn -> {
                    paymentMode = PaymentMode.CASH_OR_CARD;
                    rebuildWidgets();
                },
                "Pay by Cash/Card",
                "For teller fees: UI closes and you pay manually by right-clicking the teller with cash/card."
        );
        payByAccountButton = addHintedButton(
                contentLeft + paymentW + gap,
                rowY,
                paymentW,
                22,
                PaymentMode.ACCOUNT.label,
                paymentMode == PaymentMode.ACCOUNT ? 0xFFA4E2FF : 0xFF6BAEE8,
                btn -> {
                    paymentMode = PaymentMode.ACCOUNT;
                    rebuildWidgets();
                },
                "Pay by Account",
                "Teller fees are debited from your currently selected bank account."
        );
        rowY += 30;

        int selectorW = Math.max(88, Math.min(112, contentWidth / 6));
        int selectorMidW = contentWidth - (selectorW * 2) - (gap * 2);
        accountPrevButton = addHintedButton(contentLeft, rowY, selectorW, 22,
                "Prev", 0xFF7CB8EE, btn -> stepAccount(-1),
                "Previous Account", "Select your previous account in this teller session.");
        accountSelectButton = addHintedButton(contentLeft + selectorW + gap, rowY, selectorMidW, 22,
                accountCaption().getString(), 0xFF8FD8FF, btn -> stepAccount(1),
                "Current Account", "Click to cycle through your available accounts.");
        accountNextButton = addHintedButton(contentLeft + selectorW + gap + selectorMidW + gap, rowY, selectorW, 22,
                "Next", 0xFF7CB8EE, btn -> stepAccount(1),
                "Next Account", "Select your next account in this teller session.");
        boolean accountSwitchEnabled = !accounts.isEmpty() && paymentMode == PaymentMode.ACCOUNT;
        accountPrevButton.active = accountSwitchEnabled;
        accountNextButton.active = accountSwitchEnabled;
        accountSelectButton.active = accountSwitchEnabled;
        rowY += 30;
        rowY += 14;
        tabInfoY = rowY + 64;
        tabContentBottomY = rowY;

        if (activeTab == Tab.INSTRUMENTS) {
            amountInput = new DesktopEditBox(font, contentLeft, rowY, contentWidth, 22, Component.literal("Amount"));
            amountInput.setHint(Component.literal("Amount (supports coins, example: 0.25 or 12.75)"));
            amountInput.setValue(amountDraft);
            amountInput.setMaxLength(16);
            amountInput.setResponder(value -> amountDraft = value == null ? "" : value);
            addHintedWidget(
                    amountInput,
                    "Amount Input",
                    "Enter dollar.cents with up to 2 decimals. Coins are supported for withdraw and deposit."
            );
            rowY += 30;

            recipientInput = new DesktopEditBox(font, contentLeft, rowY, contentWidth, 22, Component.literal("Recipient"));
            recipientInput.setHint(Component.literal("Cheque recipient player name"));
            recipientInput.setValue(recipientDraft);
            recipientInput.setMaxLength(32);
            recipientInput.setResponder(value -> recipientDraft = value == null ? "" : value);
            addHintedWidget(
                    recipientInput,
                    "Cheque Recipient",
                    "Used only for cheque issuing. Enter a valid player name."
            );
            rowY += 30;

            int quickW = (contentWidth - (gap * 5)) / 6;
            addHintedButton(contentLeft, rowY, quickW, 20, "$0.25", 0xFF6E9FD2, btn -> setAmountDraft("0.25"),
                    "Quick Amount", "Set amount to a quarter-dollar.");
            addHintedButton(contentLeft + quickW + gap, rowY, quickW, 20, "$1", 0xFF6E9FD2, btn -> setAmountDraft("1.00"),
                    "Quick Amount", "Set amount to one dollar.");
            addHintedButton(contentLeft + (quickW + gap) * 2, rowY, quickW, 20, "$5", 0xFF6E9FD2, btn -> setAmountDraft("5.00"),
                    "Quick Amount", "Set amount to five dollars.");
            addHintedButton(contentLeft + (quickW + gap) * 3, rowY, quickW, 20, "$20", 0xFF6E9FD2, btn -> setAmountDraft("20.00"),
                    "Quick Amount", "Set amount to twenty dollars.");
            addHintedButton(contentLeft + (quickW + gap) * 4, rowY, quickW, 20, "Pocket", 0xFF7CC8B0, btn -> setAmountDraftFromPocketCash(),
                    "Use Pocket Cash", "Prefill amount from your currently carried bills and coins.");
            addHintedButton(contentLeft + (quickW + gap) * 5, rowY, quickW, 20, "Clear", 0xFF8CA2C0, btn -> setAmountDraft(""),
                    "Clear Amount", "Clear the amount field.");
            rowY += 24;

            int actionW = (contentWidth - (gap * 2)) / 3;
            addHintedButton(contentLeft, rowY, actionW, 24,
                    "Issue Cheque", 0xFF80DFA4, btn ->
                            sendAction("ISSUE_CHEQUE", getSelectedAccountId(), amountDraft, recipientDraft, false),
                    "Issue Cheque",
                    "Creates a cheque for recipient + amount from the selected account.");
            addHintedButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    "Issue Bank Note", 0xFFF2C27A, btn ->
                            sendAction("ISSUE_NOTE", getSelectedAccountId(), amountDraft, "", false),
                    "Issue Bank Note",
                    "Creates a redeemable bank note with the entered amount.");
            addHintedButton(contentLeft + (actionW + gap) * 2, rowY, actionW, 24,
                    "Withdraw Bills+Coins", 0xFF9BC8FF, btn ->
                            sendAction("WITHDRAW_CASH", getSelectedAccountId(), amountDraft, "", false),
                    "Withdraw Cash",
                    "Withdraw exact amount as physical bills and coins.");
            rowY += 30;
            addHintedButton(contentLeft, rowY, actionW, 24,
                    "Deposit Exact Cash", 0xFF8EDDB2, btn ->
                            sendAction("DEPOSIT_CASH", getSelectedAccountId(), amountDraft, "", false),
                    "Deposit Exact Cash",
                    "Deposits the entered amount only if your inventory has an exact bills/coins combination.");
            addHintedButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    "Deposit All Cash", 0xFF85E4CC, btn ->
                            sendAction("DEPOSIT_ALL_CASH", getSelectedAccountId(), "", "", false),
                    "Deposit All Cash",
                    "Deposits all carried bills and coins in one action.");
            addHintedButton(contentLeft + (actionW + gap) * 2, rowY, actionW, 24,
                    "Refresh Teller Data", 0xFF93B8EC, btn -> sendRefreshHint(),
                    "Refresh Summary",
                    "Shows a local summary of selected account and carried cash.");
            tabInfoY = rowY + 34;
            tabContentBottomY = rowY + 24;
        } else if (activeTab == Tab.CHEQUE_REDEEM) {
            int actionW = (contentWidth - gap) / 2;
            addHintedButton(contentLeft, rowY, actionW, 24,
                    "Deposit Cheque", 0xFF81DEAD, btn ->
                            sendAction("CHEQUE_TO_ACCOUNT", getSelectedAccountId(), "", "", false),
                    "Deposit Held Cheque",
                    "Redeem held cheque and deposit amount into selected account.");
            addHintedButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    "Cash Out Cheque", 0xFFE8B977, btn ->
                            sendAction("CHEQUE_TO_CASH", getSelectedAccountId(), "", "", false),
                    "Cash Out Held Cheque",
                    "Redeem held cheque into physical bills and coins.");
            rowY += 32;
            addHintedButton(contentLeft, rowY, actionW, 24,
                    "Deposit Bank Note", 0xFF86DBCB, btn ->
                            sendAction("NOTE_TO_ACCOUNT", getSelectedAccountId(), "", "", false),
                    "Deposit Held Bank Note",
                    "Redeem held bank note and deposit into selected account.");
            addHintedButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    "Cash Out Bank Note", 0xFFE9C68E, btn ->
                            sendAction("NOTE_TO_CASH", getSelectedAccountId(), "", "", false),
                    "Cash Out Held Bank Note",
                    "Redeem held bank note into bills and coins.");
            tabInfoY = rowY + 34;
            tabContentBottomY = rowY + 24;
        } else if (activeTab == Tab.CARD) {
            int actionW = (contentWidth - gap) / 2;
            DesktopButton issue = addHintedButton(contentLeft, rowY, actionW, 24,
                    "Issue Credit Card", 0xFF82C9FF, btn ->
                            sendAction("ISSUE_CARD", getSelectedAccountId(), "", "", false),
                    "Issue Card",
                    "Issue a new active card for this account if none is active.");
            DesktopButton replace = addHintedButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    "Replace Card", 0xFFE5A17A, btn -> {
                        replaceConfirmOpen = true;
                        rebuildWidgets();
                    },
                    "Replace Card",
                    "Blocks old cards and issues a fresh one. Confirmation required.");

            BankTellerAccountSummary selected = getSelectedAccount();
            boolean eligible = selected != null && selected.cardEligible();
            boolean hasActiveCard = selected != null && selected.hasActiveCard();
            issue.active = eligible && !hasActiveCard;
            replace.active = eligible && hasActiveCard;
            tabInfoY = rowY + 34;
            tabContentBottomY = rowY + 24;
        } else if (activeTab == Tab.SAFE_BOX) {
            DesktopButton requestBox = addHintedButton(contentLeft, rowY, contentWidth, 24,
                    "Request Safety Deposit Box", 0xFFA4D9B2, btn ->
                            sendAction("REQUEST_SAFE_BOX", getSelectedAccountId(), "", "", false),
                    "Request Safety Deposit Box",
                    "Assigns the first free physical locker door in this bank's claimed safe area to the selected account.");
            BankTellerAccountSummary selected = getSelectedAccount();
            UUID boundBankId = payload == null ? null : payload.parseBoundBankId();
            requestBox.active = selected != null && boundBankId != null && boundBankId.equals(selected.bankId());
            tabInfoY = rowY + 34;
            tabContentBottomY = rowY + 24;
        } else if (activeTab == Tab.OPEN_ACCOUNT) {
            int selectorW2 = Math.max(84, Math.min(106, contentWidth / 6));
            int selectorMidW2 = contentWidth - (selectorW2 * 2) - (gap * 2);
            addHintedButton(contentLeft, rowY, selectorW2, 22,
                    "Prev", 0xFF7CB8EE, btn -> stepOpenType(-1),
                    "Previous Account Type", "Cycle backward through account type options.");
            addHintedButton(contentLeft + selectorW2 + gap, rowY, selectorMidW2, 22,
                    "Type: " + currentOpenTypeLabel(), 0xFF8FD8FF, btn -> stepOpenType(1),
                    "Account Type", "Choose checking, saving, money market, or certificate.");
            addHintedButton(contentLeft + selectorW2 + gap + selectorMidW2 + gap, rowY, selectorW2, 22,
                    "Next", 0xFF7CB8EE, btn -> stepOpenType(1),
                    "Next Account Type", "Cycle forward through account type options.");
            rowY += 30;

            boolean certificateSelected = isOpenTypeCertificate();
            DesktopButton tierPrev = addHintedButton(contentLeft, rowY, selectorW2, 22,
                    "Prev", 0xFF7CB8EE, btn -> stepOpenTier(-1),
                    "Previous Certificate Tier", "Switch to previous certificate maturity tier.");
            DesktopButton tierSelect = addHintedButton(contentLeft + selectorW2 + gap, rowY, selectorMidW2, 22,
                    "Tier: " + currentOpenTierLabel(), 0xFF8FD8FF, btn -> stepOpenTier(1),
                    "Certificate Tier", "Applicable only for certificate accounts.");
            DesktopButton tierNext = addHintedButton(contentLeft + selectorW2 + gap + selectorMidW2 + gap, rowY, selectorW2, 22,
                    "Next", 0xFF7CB8EE, btn -> stepOpenTier(1),
                    "Next Certificate Tier", "Switch to next certificate maturity tier.");
            tierPrev.active = certificateSelected;
            tierSelect.active = certificateSelected;
            tierNext.active = certificateSelected;
            rowY += 34;

            DesktopButton openButton = addHintedButton(contentLeft, rowY, contentWidth, 24,
                    "Open Account At Teller Bank", 0xFF8AE0B3, btn ->
                            sendAction("OPEN_ACCOUNT", getSelectedAccountId(), currentOpenTypeToken(), currentOpenTierToken(), false),
                    "Open Account",
                    "Creates an account at this teller bank. Central Bank teller openings are free.");
            boolean openAccountFree = payload != null && payload.openAccountFree();
            openButton.active = openAccountFree || getSelectedAccountId() != null;
            tabInfoY = rowY + 34;
            tabContentBottomY = rowY + 24;
        }

        if (replaceConfirmOpen) {
            this.children().stream()
                    .filter(child -> child instanceof AbstractWidget)
                    .map(child -> (AbstractWidget) child)
                    .forEach(widget -> widget.active = false);

            int modalW = Math.min(440, panelWidth - 40);
            int modalH = 124;
            int modalX = panelLeft + (panelWidth - modalW) / 2;
            int modalY = panelTop + (panelHeight - modalH) / 2;
            int btnW = (modalW - 28) / 2;
            confirmReplaceButton = addHintedButton(
                    modalX + 10,
                    modalY + modalH - 34,
                    btnW,
                    22,
                    "Accept Replacement",
                    0xFFD48686,
                    btn -> {
                        replaceConfirmOpen = false;
                        sendAction("REPLACE_CARD", getSelectedAccountId(), "", "", true);
                        rebuildWidgets();
                    },
                    "Confirm Replacement",
                    "Proceed with card replacement and block previous cards."
            );
            cancelReplaceButton = addHintedButton(
                    modalX + 18 + btnW,
                    modalY + modalH - 34,
                    btnW,
                    22,
                    "Decline",
                    0xFF8DB8E2,
                    btn -> {
                        replaceConfirmOpen = false;
                        rebuildWidgets();
                    },
                    "Cancel Replacement",
                    "Abort card replacement and keep existing active card."
            );
            confirmReplaceButton.active = true;
            cancelReplaceButton.active = true;
        }
    }

    private void addTabButton(int x, int y, int width, Tab tab, String hintTitle, String hintDescription) {
        DesktopButton button = addHintedButton(
                x,
                y,
                width,
                24,
                tab.label,
                activeTab == tab ? 0xFFA4E2FF : 0xFF6BAEE8,
                btn -> {
                    activeTab = tab;
                    replaceConfirmOpen = false;
                    rebuildWidgets();
                },
                hintTitle,
                hintDescription
        );
        button.active = !replaceConfirmOpen && activeTab != tab;
    }

    private void sendAction(String action, UUID accountId, String amount, String recipient, boolean confirmed) {
        if (payload == null) {
            return;
        }
        String account = accountId == null ? "" : accountId.toString();
        PacketDistributor.sendToServer(new BankTellerActionPayload(
                payload.tellerId(),
                action == null ? "" : action,
                account,
                amount == null ? "" : amount.trim(),
                recipient == null ? "" : recipient.trim(),
                confirmed,
                paymentMode.token
        ));
    }

    private void setAmountDraft(String value) {
        amountDraft = value == null ? "" : value;
        if (amountInput != null) {
            amountInput.setValue(amountDraft);
            amountInput.setCursorPosition(amountDraft.length());
        }
    }

    private void setAmountDraftFromPocketCash() {
        int cents = getClientCarriedCashCents();
        if (cents <= 0) {
            showLocalFeedback(false, "No carried bills or coins found in your inventory.");
            return;
        }
        setAmountDraft(BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString());
        showLocalFeedback(true, "Amount prefilled from pocket cash: $" + DollarBills.formatCents(cents));
    }

    private void sendRefreshHint() {
        BankTellerAccountSummary selected = getSelectedAccount();
        String accountText = selected == null
                ? "None selected"
                : selected.accountType() + " | " + selected.bankName() + " | $" + MoneyText.abbreviate(parseDecimalSafe(selected.balance()));
        String summary = "Selected: " + accountText + " | Pocket cash: $" + DollarBills.formatCents(getClientCarriedCashCents());
        showLocalFeedback(true, summary);
    }

    private void showLocalFeedback(boolean success, String message) {
        feedbackSuccess = success;
        feedbackMessage = message == null ? "" : message;
        feedbackUntilMillis = System.currentTimeMillis() + 4500L;
    }

    private void stepAccount(int direction) {
        if (accounts.isEmpty()) {
            selectedAccountIndex = -1;
            if (accountSelectButton != null) {
                accountSelectButton.setMessage(accountCaption());
            }
            return;
        }
        if (selectedAccountIndex < 0 || selectedAccountIndex >= accounts.size()) {
            selectedAccountIndex = 0;
        } else {
            int next = selectedAccountIndex + direction;
            if (next < 0) {
                next = accounts.size() - 1;
            } else if (next >= accounts.size()) {
                next = 0;
            }
            selectedAccountIndex = next;
        }
        if (accountSelectButton != null) {
            accountSelectButton.setMessage(accountCaption());
        }
        if (activeTab == Tab.CARD || activeTab == Tab.SAFE_BOX || activeTab == Tab.OPEN_ACCOUNT) {
            rebuildWidgets();
        }
    }

    private void resetAccountsFromPayload() {
        accounts.clear();
        if (payload != null && payload.accounts() != null) {
            accounts.addAll(payload.accounts());
        }
        selectedAccountIndex = -1;
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).primary()) {
                selectedAccountIndex = i;
                break;
            }
        }
        if (selectedAccountIndex < 0 && !accounts.isEmpty()) {
            selectedAccountIndex = 0;
        }
    }

    private UUID getSelectedAccountId() {
        BankTellerAccountSummary selected = getSelectedAccount();
        return selected == null ? null : selected.accountId();
    }

    private BankTellerAccountSummary getSelectedAccount() {
        if (selectedAccountIndex < 0 || selectedAccountIndex >= accounts.size()) {
            return null;
        }
        return accounts.get(selectedAccountIndex);
    }

    private Component accountCaption() {
        return Component.literal(accountCaptionText());
    }

    private String accountCaptionText() {
        BankTellerAccountSummary selected = getSelectedAccount();
        if (selected == null) {
            return "No account selected";
        }
        String primaryPrefix = selected.primary() ? "[Primary] " : "";
        String amount = "$" + MoneyText.abbreviate(parseDecimalSafe(selected.balance()));
        return primaryPrefix + selected.accountType() + " | " + selected.bankName() + " | " + amount;
    }

    private void stepOpenType(int direction) {
        if (OPEN_TYPE_TOKENS.length == 0) {
            return;
        }
        int next = openTypeIndex + direction;
        while (next < 0) {
            next += OPEN_TYPE_TOKENS.length;
        }
        openTypeIndex = next % OPEN_TYPE_TOKENS.length;
        rebuildWidgets();
    }

    private void stepOpenTier(int direction) {
        if (OPEN_TIER_TOKENS.length == 0) {
            return;
        }
        int next = openTierIndex + direction;
        while (next < 0) {
            next += OPEN_TIER_TOKENS.length;
        }
        openTierIndex = next % OPEN_TIER_TOKENS.length;
        rebuildWidgets();
    }

    private boolean isOpenTypeCertificate() {
        String token = currentOpenTypeToken();
        return "certificate".equalsIgnoreCase(token);
    }

    private String currentOpenTypeToken() {
        if (OPEN_TYPE_TOKENS.length == 0) {
            return "checking";
        }
        int idx = Math.max(0, Math.min(openTypeIndex, OPEN_TYPE_TOKENS.length - 1));
        return OPEN_TYPE_TOKENS[idx];
    }

    private String currentOpenTypeLabel() {
        if (OPEN_TYPE_LABELS.length == 0) {
            return "Checking Account";
        }
        int idx = Math.max(0, Math.min(openTypeIndex, OPEN_TYPE_LABELS.length - 1));
        return OPEN_TYPE_LABELS[idx];
    }

    private String currentOpenTierToken() {
        if (!isOpenTypeCertificate()) {
            return "";
        }
        if (OPEN_TIER_TOKENS.length == 0) {
            return "short";
        }
        int idx = Math.max(0, Math.min(openTierIndex, OPEN_TIER_TOKENS.length - 1));
        return OPEN_TIER_TOKENS[idx];
    }

    private String currentOpenTierLabel() {
        if (!isOpenTypeCertificate()) {
            return "Not required";
        }
        if (OPEN_TIER_LABELS.length == 0) {
            return "Short";
        }
        int idx = Math.max(0, Math.min(openTierIndex, OPEN_TIER_LABELS.length - 1));
        return OPEN_TIER_LABELS[idx];
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() > feedbackUntilMillis) {
            feedbackMessage = "";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (replaceConfirmOpen) {
                replaceConfirmOpen = false;
                rebuildWidgets();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderAnimatedBackground(graphics, partialTick);
        renderPanelShell(graphics);
        renderHeaderProfile(graphics, partialTick);

        String title = payload == null ? "Bank Teller" : translateTellerTitle(payload.tellerName());
        graphics.drawString(font, fitToWidth(title, panelWidth - 160), panelLeft + 14, panelTop + 10, 0xFFF3F9FF, false);

        String subTitle = payload == null
                ? "No teller data available"
                : "Bank: " + safe(payload.boundBankName()) + (payload.bankMotto().isBlank() ? "" : " | Motto: " + payload.bankMotto());
        graphics.drawString(font, fitToWidth(subTitle, panelWidth - 160), panelLeft + 14, panelTop + 28, 0xFFC9E4FF, false);

        renderTabInfoCard(graphics);
        if (!tabInfoVisible) {
            // Tiny resolutions skip instructional copy to avoid any overlap with action controls.
            super.render(graphics, mouseX, mouseY, partialTick);
            if (replaceConfirmOpen) {
                renderReplaceModal(graphics, mouseX, mouseY, partialTick);
                return;
            }
            renderHoverHintCard(graphics, mouseX, mouseY);
            return;
        }

        if (activeTab == Tab.CHEQUE_REDEEM) {
            drawWrappedLines(
                    graphics,
                    List.of(
                            "Hold a cheque or bank note in hand/inventory, then choose account deposit or cash-out.",
                            "Cash-out returns physical bills and coins."
                    ),
                    tabInfoLeft,
                    tabInfoY,
                    tabInfoWidth,
                    0xFFD1E7FF,
                    tabInfoBottom - 4
            );
        } else if (activeTab == Tab.CARD) {
            BankTellerAccountSummary selected = getSelectedAccount();
            boolean eligible = selected != null && selected.cardEligible();
            boolean hasActiveCard = selected != null && selected.hasActiveCard();
            boolean tellerBound = payload != null && payload.parseBoundBankId() != null;
            String issueFee = selected == null ? safe(payload == null ? "0" : payload.cardIssueFee()) : safe(selected.cardIssueFee());
            String replacementFee = selected == null ? safe(payload == null ? "0" : payload.cardReplacementFee()) : safe(selected.cardReplacementFee());
            String feeLine = "Issue Fee: $" + issueFee + " | Replacement Fee: $" + replacementFee
                    + " | Mode: " + paymentMode.label;
            drawTabInfoLine(graphics, feeLine, tabInfoY, 0xFFD1E7FF);
            String status = !tellerBound
                    ? "This teller is unbound. Only Central Bank accounts are eligible for cards."
                    : eligible && hasActiveCard
                    ? "Active card found for this account. Use Replace Card for a new one."
                    : eligible
                    ? "No active card found. You can issue a new card for this account."
                    : "Select an account from this teller's bank, or a Central Bank account.";
            drawTabInfoLine(graphics, status, tabInfoY + 14, eligible ? 0xFF8DF0B2 : 0xFFFFB7A3);
            drawTabInfoLine(graphics, paymentMode == PaymentMode.ACCOUNT
                            ? "Account mode: teller fees debit from selected account."
                            : "Cash/Card mode: UI closes, then pay by right-clicking teller.",
                    tabInfoY + 28, 0xFFB9D8FF);
        } else if (activeTab == Tab.SAFE_BOX) {
            BankTellerAccountSummary selected = getSelectedAccount();
            UUID boundBankId = payload == null ? null : payload.parseBoundBankId();
            boolean eligible = selected != null && boundBankId != null && boundBankId.equals(selected.bankId());
            drawTabInfoLine(graphics,
                    boundBankId == null
                            ? "This teller is not bound to a bank safe area."
                            : "Target Bank: " + safe(payload == null ? "" : payload.boundBankName()),
                    tabInfoY,
                    0xFFD1E7FF);
            drawTabInfoLine(graphics,
                    eligible
                            ? "Request assigns the first free physical locker door in the claimed safe area."
                            : "Select an account from this teller's bank to request a safety deposit box.",
                    tabInfoY + 14,
                    eligible ? 0xFF8DF0B2 : 0xFFFFB7A3);
            drawTabInfoLine(graphics,
                    "After assignment, click the matching locker door to animate it open and access storage.",
                    tabInfoY + 28,
                    0xFFB9D8FF);
        } else if (activeTab == Tab.OPEN_ACCOUNT) {
            boolean bound = payload != null && payload.parseBoundBankId() != null;
            String targetBank = bound
                    ? safe(payload == null ? "" : payload.boundBankName())
                    : "Central Bank";
            String lineOne = "Target Bank: " + targetBank + (bound ? "" : " (Unbound teller fallback)");
            drawTabInfoLine(graphics, lineOne, tabInfoY, 0xFFD1E7FF);
            boolean openAccountFree = payload != null && payload.openAccountFree();
            String lineTwo = openAccountFree
                    ? "Central Bank teller account opening is free. No funding account is required."
                    : "Opening fee applies. First account at this bank costs extra to generate bank profit.";
            drawTabInfoLine(graphics, lineTwo, tabInfoY + 14, 0xFFB9D8FF);
            String lineThree = openAccountFree
                    ? "Payment mode does not apply while opening free Central Bank accounts."
                    : paymentMode == PaymentMode.ACCOUNT
                    ? "Payment mode: Account (fee comes from selected account)"
                    : "Payment mode: Cash/Card (UI closes, then right-click teller with cash/card)";
            drawTabInfoLine(graphics, lineThree, tabInfoY + 28, 0xFFB9D8FF);
        } else {
            drawWrappedLines(
                    graphics,
                    List.of(
                            "Cheque: uses recipient + amount. Bank note/cash: amount only.",
                            "Withdraw and deposit support coins. Use decimals (example: 0.25).",
                            "Deposit Exact Cash requires an exact bills/coins combination in inventory."
                    ),
                    tabInfoLeft,
                    tabInfoY,
                    tabInfoWidth,
                    0xFFD1E7FF,
                    tabInfoBottom - 4
            );
        }

        if (!feedbackMessage.isBlank()) {
            int color = feedbackSuccess ? 0xFF91F2B7 : 0xFFFFA8A8;
            graphics.drawString(font, fitToWidth(feedbackMessage, panelWidth - 40),
                    panelLeft + 20, panelTop + panelHeight - 32, color, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (replaceConfirmOpen) {
            renderReplaceModal(graphics, mouseX, mouseY, partialTick);
            return;
        }
        renderHoverHintCard(graphics, mouseX, mouseY);
    }

    private void renderReplaceModal(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x98000000);

        int modalW = Math.min(440, panelWidth - 40);
        int modalH = 124;
        int modalX = panelLeft + (panelWidth - modalW) / 2;
        int modalY = panelTop + (panelHeight - modalH) / 2;

        graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF3A5C81);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xFF162E49);
        graphics.fill(modalX + 1, modalY + 1, modalX + modalW - 1, modalY + 24, 0xFF7BA5D7);

        graphics.drawString(font, fitToWidth("Replace Credit Card", modalW - 20), modalX + 10, modalY + 9, 0xFFFFFFFF, false);
        graphics.drawString(font,
                fitToWidth("This will block all old cards linked to the selected account. Continue?", modalW - 20),
                modalX + 10,
                modalY + 40,
                0xFFFFC3B2,
                false);

        if (confirmReplaceButton != null) {
            confirmReplaceButton.render(graphics, mouseX, mouseY, partialTick);
        }
        if (cancelReplaceButton != null) {
            cancelReplaceButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op: hard disable vanilla blur/background for this screen.
    }

    private void renderAnimatedBackground(GuiGraphics graphics, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF071623);
        long now = System.currentTimeMillis();

        // Animated moving strips to avoid static/bland screen background.
        int stripHeight = Math.max(22, this.height / 9);
        for (int i = 0; i < 7; i++) {
            float phase = (float) (((now * 0.00018D) + (i * 0.17D)) % 1.0D);
            int y = (int) (phase * (this.height + stripHeight)) - stripHeight;
            int color = lerpColor(0x111D87D4, 0x1A2EC8A6, i / 6.0F);
            graphics.fill(0, y, this.width, y + stripHeight, color);
        }

        int pulse = (int) ((Math.sin((now - openedAtMillis) * 0.0025D) + 1.0D) * 42.0D);
        int edgeGlow = (0x2A + pulse) << 24 | 0x2A4F74;
        graphics.fill(0, 0, this.width, 4, edgeGlow);
        graphics.fill(0, this.height - 4, this.width, this.height, edgeGlow);
    }

    private void renderPanelShell(GuiGraphics graphics) {
        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, right + 2, bottom + 2, 0xFF2A4A68);
        drawVerticalGradient(graphics, panelLeft, panelTop, right, bottom, 0xFF11293F, 0xFF0A1D30);
        drawVerticalGradient(graphics, panelLeft + 1, panelTop + 1, right - 1, panelTop + 36, 0xFF86BEEA, 0xFF4A85B7);
        graphics.fill(panelLeft + 1, panelTop + 35, right - 1, panelTop + 36, 0x88D8EDFF);

        int contentTop = headerTop + headerHeight + 4;
        graphics.fill(panelLeft + 10, contentTop, right - 10, bottom - 44, 0xD50D2235);
        graphics.fill(panelLeft + 11, contentTop + 1, right - 11, bottom - 45, 0xCC10283F);
    }

    private void renderHeaderProfile(GuiGraphics graphics, float partialTick) {
        int boxX = panelLeft + 14;
        int boxY = headerTop;
        int boxW = panelWidth - 28;
        int boxH = headerHeight;

        drawVerticalGradient(graphics, boxX, boxY, boxX + boxW, boxY + boxH, 0xDB163753, 0xDB0D243A);
        graphics.fill(boxX, boxY, boxX + boxW, boxY + 1, 0x77E2F3FF);
        graphics.fill(boxX, boxY + boxH - 1, boxX + boxW, boxY + boxH, 0x55447799);

        int profileW = Math.min(300, Math.max(240, boxW / 3));
        int profileX = boxX + 8;
        int profileY = boxY + 8;
        int profileH = boxH - 16;
        drawVerticalGradient(graphics, profileX, profileY, profileX + profileW, profileY + profileH, 0xCC1B4669, 0xCC133B58);
        graphics.fill(profileX, profileY, profileX + profileW, profileY + 1, 0x80D5EEFF);

        int headSize = 28;
        int headX = profileX + 8;
        int headY = profileY + (profileH - headSize) / 2;
        ResourceLocation skin = resolveLocalSkin();
        if (skin != null) {
            PlayerFaceRenderer.draw(graphics, skin, headX, headY, headSize);
        } else {
            graphics.fill(headX, headY, headX + headSize, headY + headSize, 0xAA223D57);
            graphics.fill(headX + 6, headY + 8, headX + 22, headY + 10, 0xFFCDE6FF);
            graphics.fill(headX + 8, headY + 16, headX + 20, headY + 18, 0xFFCDE6FF);
        }

        String playerName = "Player";
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            playerName = mc.player.getName().getString();
        }

        int textX = headX + headSize + 8;
        int textW = profileW - (textX - profileX) - 8;
        graphics.drawString(font, fitToWidth(playerName, textW), textX, profileY + 8, 0xFFFFFFFF, false);

        graphics.drawString(font, fitToWidth("Current Account:", textW), textX, profileY + 20, 0xFFCFE7FF, false);
        String accountLine = getSelectedAccount() == null ? "None" : accountCaptionText();
        graphics.drawString(font, fitToWidth(accountLine, textW), textX, profileY + 32, 0xFFEAF5FF, false);
        graphics.drawString(font, fitToWidth("Bank: " + safe(payload == null ? "" : payload.boundBankName()), textW),
                textX, profileY + 46, 0xFFAED3F0, false);

        int statsX = profileX + profileW + 8;
        int statsW = boxW - (statsX - boxX) - 8;
        int cardGap = 8;
        int cardW = (statsW - cardGap) / 2;
        int cardH = (profileH - cardGap) / 2;

        drawHeaderCard(graphics, statsX, profileY, cardW, cardH, "Total Balance",
                MoneyText.abbreviateWithDollar(totalBalance()), 0xFF9FE9C4);
        drawHeaderCard(graphics, statsX + cardW + cardGap, profileY, cardW, cardH, "Total Accounts",
                Integer.toString(accounts.size()), 0xFFB9DCFF);
        drawHeaderCard(graphics, statsX, profileY + cardH + cardGap, cardW, cardH, "Selected Balance",
                MoneyText.abbreviateWithDollar(selectedBalance()), 0xFFE8D39A);
        drawHeaderCard(graphics, statsX + cardW + cardGap, profileY + cardH + cardGap, cardW, cardH, "Pocket Cash",
                "$" + DollarBills.formatCents(getClientCarriedCashCents()), 0xFFC1F0DE);
    }

    private void renderTabInfoCard(GuiGraphics graphics) {
        tabInfoVisible = false;
        int cardX = panelLeft + 16;
        int cardW = panelWidth - 32;
        int cardY = Math.max(panelTop + 186, Math.max(tabInfoY - 8, tabContentBottomY + 8));
        int maxBottom = panelTop + panelHeight - 44;
        int available = maxBottom - cardY;
        if (available < 24) {
            return;
        }
        int cardH = Math.min(60, available);
        drawVerticalGradient(graphics, cardX, cardY, cardX + cardW, cardY + cardH, 0xC918334C, 0xC9122A3F);
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 1, 0x66B4DAF8);
        tabInfoY = cardY + 8;
        tabInfoLeft = cardX + 6;
        tabInfoWidth = cardW - 12;
        tabInfoBottom = cardY + cardH;
        tabInfoVisible = true;
    }

    private void drawHeaderCard(GuiGraphics graphics, int x, int y, int w, int h,
                                String label, String value, int accentColor) {
        drawVerticalGradient(graphics, x, y, x + w, y + h, 0xC61A405F, 0xC5143048);
        graphics.fill(x, y, x + w, y + 1, 0x66D8ECFF);
        int accentY = y + Math.max(2, h - 2);
        graphics.fill(x + 2, accentY, x + w - 2, accentY + 1, accentColor);
        int labelY = y + 4;
        // Anchor value text above the accent strip so it never collides on small cards.
        int valueY = y + Math.max(13, h - font.lineHeight - 4);
        graphics.drawString(font, fitToWidth(label, w - 8), x + 4, labelY, 0xFFE2F2FF, false);
        graphics.drawString(font, fitToWidth(value, w - 8), x + 4, valueY, accentColor, false);
    }

    private void drawWrappedLines(GuiGraphics graphics,
                                  List<String> lines,
                                  int x,
                                  int y,
                                  int maxWidth,
                                  int color,
                                  int maxBottomY) {
        int lineY = y;
        for (String line : lines) {
            for (String wrapped : wrapText(line, maxWidth)) {
                if (lineY + font.lineHeight > maxBottomY) {
                    return;
                }
                graphics.drawString(font, wrapped, x, lineY, color, false);
                lineY += 11;
            }
        }
    }

    private void drawTabInfoLine(GuiGraphics graphics, String text, int y, int color) {
        if (!tabInfoVisible || y + font.lineHeight > tabInfoBottom - 4) {
            return;
        }
        graphics.drawString(font, fitToWidth(text, tabInfoWidth), tabInfoLeft, y, color, false);
    }

    private void renderHoverHintCard(GuiGraphics graphics, int mouseX, int mouseY) {
        HoverHint hint = findHoveredHint(mouseX, mouseY);
        if (hint == null) {
            return;
        }

        int cardW = Math.min(340, Math.max(220, this.width / 3));
        List<String> wrapped = wrapText(hint.description(), cardW - 14);
        int cardH = 10 + 9 + Math.max(11, wrapped.size() * 11) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = panelLeft + panelWidth - 8;
        int bottomLimit = panelTop + panelHeight - 8;
        if (x + cardW > rightLimit) {
            x = mouseX - cardW - 12;
        }
        if (x < panelLeft + 6) {
            x = panelLeft + 6;
        }
        if (y + cardH > bottomLimit) {
            y = bottomLimit - cardH;
        }
        if (y < panelTop + 6) {
            y = panelTop + 6;
        }

        graphics.fill(x - 1, y - 1, x + cardW + 1, y + cardH + 1, 0xE4274567);
        graphics.fill(x, y, x + cardW, y + cardH, 0xF0162D45);
        graphics.fill(x, y, x + cardW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + cardW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, fitToWidth(hint.title(), cardW - 14), x + 7, y + 6, 0xFFFFFFFF, false);

        int lineY = y + 24;
        for (String wrappedLine : wrapped) {
            graphics.drawString(this.font, wrappedLine, x + 7, lineY, 0xFFD3E9FF, false);
            lineY += 11;
        }
    }

    private HoverHint findHoveredHint(int mouseX, int mouseY) {
        for (int i = hoverBindings.size() - 1; i >= 0; i--) {
            HoverBinding binding = hoverBindings.get(i);
            AbstractWidget widget = binding.widget();
            if (widget != null && widget.visible && widget.active && widget.isMouseOver(mouseX, mouseY)) {
                return binding.hint();
            }
        }
        return null;
    }

    private DesktopButton addHintedButton(int x,
                                          int y,
                                          int width,
                                          int height,
                                          String label,
                                          int accentColor,
                                          Consumer<DesktopButton> onPress,
                                          String hintTitle,
                                          String hintDescription) {
        DesktopButton button = new DesktopButton(
                x, y, width, height, Component.literal(label), accentColor, onPress
        );
        DesktopButton added = addRenderableWidget(button);
        registerHoverHint(added, hintTitle, hintDescription);
        return added;
    }

    private <T extends AbstractWidget> T addHintedWidget(T widget, String hintTitle, String hintDescription) {
        T added = addRenderableWidget(widget);
        registerHoverHint(added, hintTitle, hintDescription);
        return added;
    }

    private void registerHoverHint(AbstractWidget widget, String title, String description) {
        if (widget == null || title == null || title.isBlank()) {
            return;
        }
        hoverBindings.add(new HoverBinding(widget, new HoverHint(title, safe(description))));
    }

    private ResourceLocation resolveLocalSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return null;
        }
        var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? null : info.getSkin().texture();
    }

    private BigDecimal totalBalance() {
        BigDecimal total = BigDecimal.ZERO;
        for (BankTellerAccountSummary account : accounts) {
            if (account == null) {
                continue;
            }
            total = total.add(parseDecimalSafe(account.balance()));
        }
        return total.max(BigDecimal.ZERO);
    }

    private BigDecimal selectedBalance() {
        BankTellerAccountSummary selected = getSelectedAccount();
        if (selected == null) {
            return BigDecimal.ZERO;
        }
        return parseDecimalSafe(selected.balance()).max(BigDecimal.ZERO);
    }

    private int getClientCarriedCashCents() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        int cents = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int value = DollarBills.cashCentsForItem(stack.getItem());
            if (value > 0) {
                cents += value * stack.getCount();
            }
        }
        for (ItemStack stack : mc.player.getInventory().offhand) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int value = DollarBills.cashCentsForItem(stack.getItem());
            if (value > 0) {
                cents += value * stack.getCount();
            }
        }
        return Math.max(0, cents);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return lines;
        }

        String[] words = tr(text).trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }

            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }

            if (font.width(word) <= maxWidth) {
                current.append(word);
            } else {
                lines.add(fitToWidth(word, maxWidth));
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static void drawVerticalGradient(GuiGraphics graphics, int x1, int y1, int x2, int y2, int topColor, int bottomColor) {
        int h = Math.max(1, y2 - y1);
        for (int y = 0; y < h; y++) {
            float ratio = h <= 1 ? 0.0F : (float) y / (float) (h - 1);
            int line = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(x1, y1 + y, x2, y1 + y + 1, line);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * clamped);
        int r = (int) (r1 + (r2 - r1) * clamped);
        int g = (int) (g1 + (g2 - g1) * clamped);
        int b = (int) (b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String fitToWidth(String text, int width) {
        if (text == null || text.isBlank()) {
            return "";
        }
        text = tr(text);
        var font = net.minecraft.client.Minecraft.getInstance().font;
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > width) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String tr(String text) {
        return UbsClientTranslations.resolve(text == null ? "" : text);
    }

    private static String translateTellerTitle(String title) {
        String value = safe(title);
        if (value.isBlank()) {
            return tr("Bank Teller");
        }
        return value
                .replace("Bank Teller", tr("Bank Teller"))
                .replace("Cashier", tr("Cashier"));
    }

    private static BigDecimal parseDecimalSafe(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }
}
