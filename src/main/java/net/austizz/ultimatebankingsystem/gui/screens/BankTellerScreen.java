package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.BankTellerAccountSummary;
import net.austizz.ultimatebankingsystem.network.BankTellerActionPayload;
import net.austizz.ultimatebankingsystem.network.BankTellerActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.BankTellerOpenPayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankTellerScreen extends Screen {

    private enum Tab {
        INSTRUMENTS("Cheque - Note - Cash"),
        CHEQUE_REDEEM("Redeem Cashables"),
        CARD("Credit Card"),
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

    private BankTellerOpenPayload payload;
    private final List<BankTellerAccountSummary> accounts = new ArrayList<>();

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
    private int tabInfoY;

    private String amountDraft = "";
    private String recipientDraft = "";

    private boolean replaceConfirmOpen = false;

    private boolean feedbackSuccess = true;
    private String feedbackMessage = "";
    private long feedbackUntilMillis = 0L;

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
        super(Component.literal("Bank Teller"));
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
        this.confirmReplaceButton = null;
        this.cancelReplaceButton = null;

        panelWidth = Math.min(760, Math.max(520, this.width - 34));
        panelHeight = Math.min(430, Math.max(340, this.height - 34));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;

        int contentLeft = panelLeft + 14;
        int contentTop = panelTop + 56;
        int contentWidth = panelWidth - 28;
        int rowY = contentTop;
        int gap = 8;

        int closeW = 110;
        addRenderableWidget(new DesktopButton(panelLeft + panelWidth - closeW - 12, panelTop + 14, closeW, 22,
                Component.literal("Close"), 0xFF85B8E8, btn -> onClose()));

        int tabW = (contentWidth - (gap * 3)) / 4;
        addTabButton(contentLeft, rowY, tabW, Tab.INSTRUMENTS);
        addTabButton(contentLeft + tabW + gap, rowY, tabW, Tab.CHEQUE_REDEEM);
        addTabButton(contentLeft + (tabW + gap) * 2, rowY, tabW, Tab.CARD);
        addTabButton(contentLeft + (tabW + gap) * 3, rowY, tabW, Tab.OPEN_ACCOUNT);
        rowY += 32;

        int paymentW = (contentWidth - gap) / 2;
        payByCashCardButton = addRenderableWidget(new DesktopButton(
                contentLeft,
                rowY,
                paymentW,
                22,
                Component.literal(PaymentMode.CASH_OR_CARD.label),
                paymentMode == PaymentMode.CASH_OR_CARD ? 0xFF8DDDB1 : 0xFF6BAEE8,
                btn -> {
                    paymentMode = PaymentMode.CASH_OR_CARD;
                    rebuildWidgets();
                }
        ));
        payByAccountButton = addRenderableWidget(new DesktopButton(
                contentLeft + paymentW + gap,
                rowY,
                paymentW,
                22,
                Component.literal(PaymentMode.ACCOUNT.label),
                paymentMode == PaymentMode.ACCOUNT ? 0xFFA4E2FF : 0xFF6BAEE8,
                btn -> {
                    paymentMode = PaymentMode.ACCOUNT;
                    rebuildWidgets();
                }
        ));
        rowY += 30;

        int selectorW = Math.max(88, Math.min(112, contentWidth / 6));
        int selectorMidW = contentWidth - (selectorW * 2) - (gap * 2);
        accountPrevButton = addRenderableWidget(new DesktopButton(contentLeft, rowY, selectorW, 22,
                Component.literal("Prev"), 0xFF7CB8EE, btn -> stepAccount(-1)));
        accountSelectButton = addRenderableWidget(new DesktopButton(contentLeft + selectorW + gap, rowY, selectorMidW, 22,
                accountCaption(), 0xFF8FD8FF, btn -> stepAccount(1)));
        accountNextButton = addRenderableWidget(new DesktopButton(contentLeft + selectorW + gap + selectorMidW + gap, rowY, selectorW, 22,
                Component.literal("Next"), 0xFF7CB8EE, btn -> stepAccount(1)));
        boolean accountSwitchEnabled = !accounts.isEmpty() && paymentMode == PaymentMode.ACCOUNT;
        accountPrevButton.active = accountSwitchEnabled;
        accountNextButton.active = accountSwitchEnabled;
        accountSelectButton.active = accountSwitchEnabled;
        rowY += 30;
        rowY += 14;
        tabInfoY = rowY + 62;

        if (activeTab == Tab.INSTRUMENTS) {
            amountInput = new DesktopEditBox(font, contentLeft, rowY, contentWidth, 22, Component.literal("Amount"));
            amountInput.setHint(Component.literal("Amount (dollars.cents)"));
            amountInput.setValue(amountDraft);
            amountInput.setMaxLength(16);
            amountInput.setResponder(value -> amountDraft = value == null ? "" : value);
            addRenderableWidget(amountInput);
            rowY += 30;

            recipientInput = new DesktopEditBox(font, contentLeft, rowY, contentWidth, 22, Component.literal("Recipient"));
            recipientInput.setHint(Component.literal("Cheque recipient player name"));
            recipientInput.setValue(recipientDraft);
            recipientInput.setMaxLength(32);
            recipientInput.setResponder(value -> recipientDraft = value == null ? "" : value);
            addRenderableWidget(recipientInput);
            rowY += 34;

            int actionW = (contentWidth - (gap * 2)) / 3;
            addRenderableWidget(new DesktopButton(contentLeft, rowY, actionW, 24,
                    Component.literal("Issue Cheque"), 0xFF80DFA4, btn ->
                    sendAction("ISSUE_CHEQUE", getSelectedAccountId(), amountDraft, recipientDraft, false)));
            addRenderableWidget(new DesktopButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    Component.literal("Issue Bank Note"), 0xFFF2C27A, btn ->
                    sendAction("ISSUE_NOTE", getSelectedAccountId(), amountDraft, "", false)));
            addRenderableWidget(new DesktopButton(contentLeft + (actionW + gap) * 2, rowY, actionW, 24,
                    Component.literal("Withdraw Cash"), 0xFF9BC8FF, btn ->
                    sendAction("WITHDRAW_CASH", getSelectedAccountId(), amountDraft, "", false)));
            tabInfoY = rowY + 34;
        } else if (activeTab == Tab.CHEQUE_REDEEM) {
            int actionW = (contentWidth - gap) / 2;
            addRenderableWidget(new DesktopButton(contentLeft, rowY, actionW, 24,
                    Component.literal("Deposit Cheque"), 0xFF81DEAD, btn ->
                    sendAction("CHEQUE_TO_ACCOUNT", getSelectedAccountId(), "", "", false)));
            addRenderableWidget(new DesktopButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    Component.literal("Cash Out Cheque"), 0xFFE8B977, btn ->
                    sendAction("CHEQUE_TO_CASH", getSelectedAccountId(), "", "", false)));
            rowY += 32;
            addRenderableWidget(new DesktopButton(contentLeft, rowY, actionW, 24,
                    Component.literal("Deposit Bank Note"), 0xFF86DBCB, btn ->
                    sendAction("NOTE_TO_ACCOUNT", getSelectedAccountId(), "", "", false)));
            addRenderableWidget(new DesktopButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    Component.literal("Cash Out Bank Note"), 0xFFE9C68E, btn ->
                    sendAction("NOTE_TO_CASH", getSelectedAccountId(), "", "", false)));
            tabInfoY = rowY + 34;
        } else if (activeTab == Tab.CARD) {
            int actionW = (contentWidth - gap) / 2;
            DesktopButton issue = addRenderableWidget(new DesktopButton(contentLeft, rowY, actionW, 24,
                    Component.literal("Issue Credit Card"), 0xFF82C9FF, btn ->
                    sendAction("ISSUE_CARD", getSelectedAccountId(), "", "", false)));
            DesktopButton replace = addRenderableWidget(new DesktopButton(contentLeft + actionW + gap, rowY, actionW, 24,
                    Component.literal("Replace Card"), 0xFFE5A17A, btn -> {
                        replaceConfirmOpen = true;
                        rebuildWidgets();
                    }));

            BankTellerAccountSummary selected = getSelectedAccount();
            boolean eligible = selected != null && selected.cardEligible();
            boolean hasActiveCard = selected != null && selected.hasActiveCard();
            issue.active = eligible && !hasActiveCard;
            replace.active = eligible && hasActiveCard;
            tabInfoY = rowY + 34;
        } else if (activeTab == Tab.OPEN_ACCOUNT) {
            int selectorW2 = Math.max(84, Math.min(106, contentWidth / 6));
            int selectorMidW2 = contentWidth - (selectorW2 * 2) - (gap * 2);
            addRenderableWidget(new DesktopButton(contentLeft, rowY, selectorW2, 22,
                    Component.literal("Prev"), 0xFF7CB8EE, btn -> stepOpenType(-1)));
            addRenderableWidget(new DesktopButton(contentLeft + selectorW2 + gap, rowY, selectorMidW2, 22,
                    Component.literal("Type: " + currentOpenTypeLabel()), 0xFF8FD8FF, btn -> stepOpenType(1)));
            addRenderableWidget(new DesktopButton(contentLeft + selectorW2 + gap + selectorMidW2 + gap, rowY, selectorW2, 22,
                    Component.literal("Next"), 0xFF7CB8EE, btn -> stepOpenType(1)));
            rowY += 30;

            boolean certificateSelected = isOpenTypeCertificate();
            DesktopButton tierPrev = addRenderableWidget(new DesktopButton(contentLeft, rowY, selectorW2, 22,
                    Component.literal("Prev"), 0xFF7CB8EE, btn -> stepOpenTier(-1)));
            DesktopButton tierSelect = addRenderableWidget(new DesktopButton(contentLeft + selectorW2 + gap, rowY, selectorMidW2, 22,
                    Component.literal("Tier: " + currentOpenTierLabel()), 0xFF8FD8FF, btn -> stepOpenTier(1)));
            DesktopButton tierNext = addRenderableWidget(new DesktopButton(contentLeft + selectorW2 + gap + selectorMidW2 + gap, rowY, selectorW2, 22,
                    Component.literal("Next"), 0xFF7CB8EE, btn -> stepOpenTier(1)));
            tierPrev.active = certificateSelected;
            tierSelect.active = certificateSelected;
            tierNext.active = certificateSelected;
            rowY += 34;

            DesktopButton openButton = addRenderableWidget(new DesktopButton(contentLeft, rowY, contentWidth, 24,
                    Component.literal("Open Account At Teller Bank"), 0xFF8AE0B3, btn ->
                    sendAction("OPEN_ACCOUNT", getSelectedAccountId(), currentOpenTypeToken(), currentOpenTierToken(), false)));
            openButton.active = getSelectedAccountId() != null;
            tabInfoY = rowY + 34;
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
            confirmReplaceButton = addRenderableWidget(new DesktopButton(
                    modalX + 10,
                    modalY + modalH - 34,
                    btnW,
                    22,
                    Component.literal("Accept Replacement"),
                    0xFFD48686,
                    btn -> {
                        replaceConfirmOpen = false;
                        sendAction("REPLACE_CARD", getSelectedAccountId(), "", "", true);
                        rebuildWidgets();
                    }
            ));
            cancelReplaceButton = addRenderableWidget(new DesktopButton(
                    modalX + 18 + btnW,
                    modalY + modalH - 34,
                    btnW,
                    22,
                    Component.literal("Decline"),
                    0xFF8DB8E2,
                    btn -> {
                        replaceConfirmOpen = false;
                        rebuildWidgets();
                    }
            ));
            confirmReplaceButton.active = true;
            cancelReplaceButton.active = true;
        }
    }

    private void addTabButton(int x, int y, int width, Tab tab) {
        DesktopButton button = addRenderableWidget(new DesktopButton(
                x,
                y,
                width,
                24,
                Component.literal(tab.label),
                activeTab == tab ? 0xFFA4E2FF : 0xFF6BAEE8,
                btn -> {
                    activeTab = tab;
                    replaceConfirmOpen = false;
                    rebuildWidgets();
                }
        ));
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
        if (activeTab == Tab.CARD || activeTab == Tab.OPEN_ACCOUNT) {
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
        BankTellerAccountSummary selected = getSelectedAccount();
        if (selected == null) {
            return Component.literal("No account selected");
        }
        String primaryPrefix = selected.primary() ? "[Primary] " : "";
        String amount = "$" + MoneyText.abbreviate(parseDecimalSafe(selected.balance()));
        return Component.literal(primaryPrefix + selected.accountType() + " | " + selected.bankName() + " | " + amount);
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
        graphics.fill(0, 0, this.width, this.height, 0xFF0A1A2E);

        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, right + 2, bottom + 2, 0xFF2D4D75);
        graphics.fill(panelLeft, panelTop, right, bottom, 0xFF173252);
        graphics.fill(panelLeft + 1, panelTop + 1, right - 1, panelTop + 30, 0xFF7CA6D8);
        graphics.fill(panelLeft + 12, panelTop + 46, right - 12, bottom - 48, 0xFF10263F);

        String title = payload == null ? "Bank Teller" : payload.tellerName();
        graphics.drawString(font, title, panelLeft + 12, panelTop + 10, 0xFFF3F9FF, false);

        String subTitle = payload == null
                ? "No teller data available"
                : "Bank: " + safe(payload.boundBankName()) + (payload.bankMotto().isBlank() ? "" : " | Motto: " + payload.bankMotto());
        graphics.drawString(font, fitToWidth(subTitle, panelWidth - 140), panelLeft + 12, panelTop + 33, 0xFFC9E4FF, false);

        if (activeTab == Tab.CHEQUE_REDEEM) {
            graphics.drawString(font,
                    "Hold a cheque or bank note in your hand/inventory, then choose account deposit or cash-out.",
                    panelLeft + 18,
                    tabInfoY,
                    0xFFD1E7FF,
                    false);
        } else if (activeTab == Tab.CARD) {
            BankTellerAccountSummary selected = getSelectedAccount();
            boolean eligible = selected != null && selected.cardEligible();
            boolean hasActiveCard = selected != null && selected.hasActiveCard();
            boolean tellerBound = payload != null && payload.parseBoundBankId() != null;
            String issueFee = selected == null ? safe(payload == null ? "0" : payload.cardIssueFee()) : safe(selected.cardIssueFee());
            String replacementFee = selected == null ? safe(payload == null ? "0" : payload.cardReplacementFee()) : safe(selected.cardReplacementFee());
            String feeLine = "Issue Fee: $" + issueFee + " | Replacement Fee: $" + replacementFee;
            graphics.drawString(font, feeLine, panelLeft + 18, tabInfoY, 0xFFD1E7FF, false);
            String status = !tellerBound
                    ? "This teller is unbound. Only Central Bank accounts are eligible for cards."
                    : eligible && hasActiveCard
                    ? "Active card found for this account. Use Replace Card for a new one."
                    : eligible
                    ? "No active card found. You can issue a new card for this account."
                    : "Select an account from this teller's bank, or a Central Bank account.";
            graphics.drawString(font, fitToWidth(status, panelWidth - 36), panelLeft + 18, tabInfoY + 14,
                    eligible ? 0xFF8DF0B2 : 0xFFFFB7A3, false);
            graphics.drawString(font,
                    paymentMode == PaymentMode.ACCOUNT
                            ? "Payment mode: Account (fees debit from selected account)"
                            : "Payment mode: Cash/Card (UI closes, then pay by right-clicking teller)",
                    panelLeft + 18,
                    tabInfoY + 28,
                    0xFFB9D8FF,
                    false);
        } else if (activeTab == Tab.OPEN_ACCOUNT) {
            boolean bound = payload != null && payload.parseBoundBankId() != null;
            String targetBank = bound
                    ? safe(payload == null ? "" : payload.boundBankName())
                    : "Central Bank";
            String lineOne = "Target Bank: " + targetBank + (bound ? "" : " (Unbound teller fallback)");
            graphics.drawString(font, fitToWidth(lineOne, panelWidth - 36), panelLeft + 18, tabInfoY, 0xFFD1E7FF, false);
            String lineTwo = "Opening fee applies. First account at this bank costs extra to generate bank profit.";
            graphics.drawString(font, fitToWidth(lineTwo, panelWidth - 36), panelLeft + 18, tabInfoY + 14, 0xFFB9D8FF, false);
            String lineThree = paymentMode == PaymentMode.ACCOUNT
                    ? "Payment mode: Account (fee comes from selected account)"
                    : "Payment mode: Cash/Card (UI closes, then right-click teller with cash/card)";
            graphics.drawString(font, fitToWidth(lineThree, panelWidth - 36), panelLeft + 18, tabInfoY + 28, 0xFFB9D8FF, false);
        } else {
            graphics.drawString(font,
                    "Cheque uses recipient + amount. Bank note/cash use amount only.",
                    panelLeft + 18,
                    tabInfoY,
                    0xFFD1E7FF,
                    false);
        }

        if (!feedbackMessage.isBlank()) {
            int color = feedbackSuccess ? 0xFF91F2B7 : 0xFFFFA8A8;
            graphics.drawString(font, fitToWidth(feedbackMessage, panelWidth - 32),
                    panelLeft + 16, panelTop + panelHeight - 34, color, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (replaceConfirmOpen) {
            renderReplaceModal(graphics, mouseX, mouseY, partialTick);
        }
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

        graphics.drawString(font, "Replace Credit Card", modalX + 10, modalY + 9, 0xFFFFFFFF, false);
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
    public void renderBackground(GuiGraphics graphics) {
        // No-op: hard disable vanilla blur/background for this screen.
    }

    private static String fitToWidth(String text, int width) {
        if (text == null || text.isBlank()) {
            return "";
        }
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
