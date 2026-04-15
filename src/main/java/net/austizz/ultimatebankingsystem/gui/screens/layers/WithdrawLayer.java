package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.WithdrawRequestPayload;
import net.austizz.ultimatebankingsystem.network.WithdrawResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WithdrawLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private static final String[] PRESET_AMOUNTS = {"20", "50", "100", "200", "500"};

    private EditBox amountField;
    private String resultMessage = null;
    private boolean resultSuccess = false;
    private BigDecimal defaultWithdrawalLimit = BigDecimal.ZERO;
    private BigDecimal effectiveWithdrawalLimit = BigDecimal.ZERO;
    private BigDecimal dailyWithdrawalLimit = BigDecimal.ZERO;
    private BigDecimal dailyWithdrawnToday = BigDecimal.ZERO;
    private BigDecimal dailyRemaining = BigDecimal.ZERO;
    private long dailyResetEpochMillis = -1L;
    private final List<NineSliceTexturedButton> presetButtons = new ArrayList<>();

    public WithdrawLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        presetButtons.clear();

        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();

        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;

        int quickTop = panelTop + 58;
        int customTop = panelTop + 122;
        int btnSpacing = 4;
        int btnWidth = (contentWidth - ((PRESET_AMOUNTS.length - 1) * btnSpacing)) / PRESET_AMOUNTS.length;
        btnWidth = Math.max(40, btnWidth);
        int totalBtnWidth = PRESET_AMOUNTS.length * btnWidth + (PRESET_AMOUNTS.length - 1) * btnSpacing;
        int startX = panelLeft + (panelWidth - totalBtnWidth) / 2;
        int presetY = quickTop + 24;

        var selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            defaultWithdrawalLimit = parseMoneyOrZero(selected.defaultWithdrawalLimit());
            effectiveWithdrawalLimit = parseMoneyOrZero(selected.effectiveWithdrawalLimit());
            dailyWithdrawalLimit = parseMoneyOrZero(selected.dailyWithdrawalLimit());
            dailyWithdrawnToday = parseMoneyOrZero(selected.dailyWithdrawnToday());
            dailyRemaining = parseMoneyOrZero(selected.dailyWithdrawalRemaining());
            dailyResetEpochMillis = selected.dailyResetEpochMillis();
        }

        for (int i = 0; i < PRESET_AMOUNTS.length; i++) {
            String amt = PRESET_AMOUNTS[i];
            int x = startX + i * (btnWidth + btnSpacing);
            NineSliceTexturedButton button = addWidget(new NineSliceTexturedButton(
                x, presetY,
                btnWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(MoneyText.abbreviateWithDollar(amt)).withStyle(ChatFormatting.WHITE),
                btn -> sendWithdraw(amt)
            ));
            button.active = parseMoneyOrZero(amt).compareTo(effectiveWithdrawalLimit) <= 0;
            presetButtons.add(button);
        }

        int fieldY = customTop + 20;
        int confirmWidth = 84;
        int fieldWidth = contentWidth - (confirmWidth + 8);
        amountField = new AtmEditBox(font, contentLeft, fieldY, fieldWidth, 20, Component.literal(""));
        amountField.setHint(Component.literal("Custom amount...").withStyle(ChatFormatting.WHITE));
        amountField.setMaxLength(15);
        styleEditBox(amountField);
        addWidget(amountField);

        addWidget(new NineSliceTexturedButton(
            contentLeft + fieldWidth + 8, fieldY,
            confirmWidth, 20,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            Component.literal("Confirm").withStyle(ChatFormatting.WHITE),
            btn -> {
                String value = amountField.getValue().trim();
                if (!value.isEmpty()) {
                    sendWithdraw(value);
                }
            }
        ));

        addWidget(new NineSliceTexturedButton(
            panelLeft + 14,
            panelTop + panelHeight - 36,
            56, 22,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            Component.literal("Back").withStyle(ChatFormatting.WHITE),
            btn -> bankScreen.popLayer()
        ));
    }

    private void sendWithdraw(String amount) {
        var selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            BigDecimal requestAmount;
            try {
                requestAmount = new BigDecimal(amount.trim());
            } catch (NumberFormatException ex) {
                resultMessage = "Invalid amount format.";
                resultSuccess = false;
                return;
            }

            if (requestAmount.compareTo(effectiveWithdrawalLimit) > 0) {
                resultMessage = "Amount exceeds active limit of $" + MoneyText.abbreviate(effectiveWithdrawalLimit) + ".";
                resultSuccess = false;
                return;
            }

            resultMessage = null;
            PacketDistributor.sendToServer(new WithdrawRequestPayload(selected.accountId(), amount));
        }
    }

    @Override
    public void tick() {
        var selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            return;
        }

        BigDecimal nextDefault = parseMoneyOrZero(selected.defaultWithdrawalLimit());
        BigDecimal nextEffective = parseMoneyOrZero(selected.effectiveWithdrawalLimit());
        BigDecimal nextDailyLimit = parseMoneyOrZero(selected.dailyWithdrawalLimit());
        BigDecimal nextDailyWithdrawn = parseMoneyOrZero(selected.dailyWithdrawnToday());
        BigDecimal nextDailyRemaining = parseMoneyOrZero(selected.dailyWithdrawalRemaining());
        long nextDailyReset = selected.dailyResetEpochMillis();

        boolean changed = nextDefault.compareTo(defaultWithdrawalLimit) != 0
                || nextEffective.compareTo(effectiveWithdrawalLimit) != 0
                || nextDailyLimit.compareTo(dailyWithdrawalLimit) != 0
                || nextDailyWithdrawn.compareTo(dailyWithdrawnToday) != 0
                || nextDailyRemaining.compareTo(dailyRemaining) != 0
                || nextDailyReset != dailyResetEpochMillis;

        if (!changed) {
            return;
        }

        defaultWithdrawalLimit = nextDefault;
        effectiveWithdrawalLimit = nextEffective;
        dailyWithdrawalLimit = nextDailyLimit;
        dailyWithdrawnToday = nextDailyWithdrawn;
        dailyRemaining = nextDailyRemaining;
        dailyResetEpochMillis = nextDailyReset;

        for (int i = 0; i < presetButtons.size() && i < PRESET_AMOUNTS.length; i++) {
            presetButtons.get(i).active = parseMoneyOrZero(PRESET_AMOUNTS[i]).compareTo(effectiveWithdrawalLimit) <= 0;
        }
    }

    public void updateResult(WithdrawResponsePayload payload) {
        if (payload.success()) {
            resultMessage = "Withdrawal successful! New balance: " + MoneyText.abbreviateWithDollar(payload.newBalance());
            resultSuccess = true;
            // Update the cached account balance
            var selected = ClientATMData.getSelectedAccount();
            if (selected != null) {
                ClientATMData.setSelectedAccount(new net.austizz.ultimatebankingsystem.network.AccountSummary(
                    selected.accountId(),
                    selected.accountType(),
                    selected.bankName(),
                    payload.newBalance(),
                    selected.isPrimary(),
                    selected.pinSet(),
                    selected.defaultWithdrawalLimit(),
                    selected.effectiveWithdrawalLimit(),
                    selected.temporaryWithdrawalLimit(),
                    selected.temporaryLimitExpiresAtGameTime(),
                    payload.dailyLimit(),
                    payload.dailyWithdrawn(),
                    payload.dailyRemaining(),
                    payload.dailyResetEpochMillis()
                ));
            }
            dailyWithdrawalLimit = parseMoneyOrZero(payload.dailyLimit());
            dailyWithdrawnToday = parseMoneyOrZero(payload.dailyWithdrawn());
            dailyRemaining = parseMoneyOrZero(payload.dailyRemaining());
            dailyResetEpochMillis = payload.dailyResetEpochMillis();
        } else {
            resultMessage = MoneyText.abbreviateCurrencyTokens(payload.errorMessage());
            resultSuccess = false;
            if (payload.dailyLimit() != null && !payload.dailyLimit().isBlank()) {
                dailyWithdrawalLimit = parseMoneyOrZero(payload.dailyLimit());
            }
            if (payload.dailyWithdrawn() != null && !payload.dailyWithdrawn().isBlank()) {
                dailyWithdrawnToday = parseMoneyOrZero(payload.dailyWithdrawn());
            }
            if (payload.dailyRemaining() != null && !payload.dailyRemaining().isBlank()) {
                dailyRemaining = parseMoneyOrZero(payload.dailyRemaining());
            }
            if (payload.dailyResetEpochMillis() > 0L) {
                dailyResetEpochMillis = payload.dailyResetEpochMillis();
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int customTop = panelTop + 122;
        int fieldY = customTop + 20;

        drawCenteredFittedString(graphics, "Withdraw Cash",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        graphics.drawString(font, "Custom Amount", contentLeft + 6, customTop + 6, COLOR_LABEL);
        drawFittedString(graphics,
                "Per-withdrawal limit: $" + MoneyText.abbreviate(defaultWithdrawalLimit)
                        + "   Active withdrawal limit: $" + MoneyText.abbreviate(effectiveWithdrawalLimit),
                contentLeft + 6, panelTop + 46, contentWidth - 10, COLOR_LABEL);
        drawFittedString(graphics,
                "Daily withdrawal limit: $" + MoneyText.abbreviate(dailyWithdrawalLimit)
                        + "   Used today: $" + MoneyText.abbreviate(dailyWithdrawnToday)
                        + "   Remaining: $" + MoneyText.abbreviate(dailyRemaining),
                contentLeft + 6, panelTop + 58, contentWidth - 10, COLOR_LABEL);
        drawFittedString(graphics,
                "Daily reset: " + formatResetEpoch(dailyResetEpochMillis),
                contentLeft + 6, panelTop + 70, contentWidth - 10, COLOR_MUTED);

        if (resultMessage != null) {
            int resultY = fieldY + 28;
            int maxResultY = panelTop + panelHeight - 50;
            if (resultY > maxResultY) {
                resultY = maxResultY;
            }
            int color = resultSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, resultMessage, panelLeft + panelWidth / 2, resultY, contentWidth, color);
        }
    }

    private static BigDecimal parseMoneyOrZero(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private static String formatResetEpoch(long epochMillis) {
        if (epochMillis <= 0L) {
            return "Unknown";
        }
        try {
            return java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
                    .withZone(java.time.ZoneId.systemDefault())
                    .format(java.time.Instant.ofEpochMilli(epochMillis));
        } catch (Exception ignored) {
            return "Unknown";
        }
    }
}
