package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.AccountSummary;
import net.austizz.ultimatebankingsystem.network.PayRequestCreatePayload;
import net.austizz.ultimatebankingsystem.network.PayRequestCreateResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

public class CreatePayRequestLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");

    private NineSliceTexturedButton playerSelectButton;
    private String selectedPlayerName = "";

    private EditBox amountField;
    private NineSliceTexturedButton destinationButton;
    private UUID destinationAccountId;

    private String resultMessage = "";
    private boolean resultSuccess = false;

    public CreatePayRequestLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int sectionTop = panelTop + 62;

        if (destinationAccountId == null) {
            destinationAccountId = resolveDefaultDestinationAccountId();
        }

        playerSelectButton = addWidget(new NineSliceTexturedButton(
                contentLeft, sectionTop + 20, contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(""),
                btn -> bankScreen.pushLayer(new PlayerSelectionLayer(
                        minecraft,
                        selectedPlayerName,
                        selectedName -> {
                            selectedPlayerName = selectedName == null ? "" : selectedName;
                            updatePlayerButtonText(false);
                        }
                ))
        ));
        updatePlayerButtonText(false);

        amountField = new AtmEditBox(font, contentLeft, sectionTop + 54, contentWidth, 20, Component.literal(""));
        amountField.setMaxLength(20);
        amountField.setHint(Component.literal("Amount (e.g. 25.00)").withStyle(ChatFormatting.WHITE));
        styleEditBox(amountField);
        addWidget(amountField);

        destinationButton = addWidget(new NineSliceTexturedButton(
                contentLeft, sectionTop + 88, contentWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal(""),
                btn -> bankScreen.pushLayer(new AccountSelectionLayer(minecraft, selected -> {
                    if (selected == null) {
                        return;
                    }
                    destinationAccountId = selected.accountId();
                    updateDestinationButtonText();
                }, false))
        ));
        updateDestinationButtonText();

        int sendWidth = Math.min(170, contentWidth);
        int sendX = panelLeft + (panelWidth - sendWidth) / 2;
        addWidget(new NineSliceTexturedButton(
                sendX, sectionTop + 120, sendWidth, 20,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Send Pay Request").withStyle(ChatFormatting.WHITE),
                btn -> sendCreateRequest()
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

    public void updateResult(PayRequestCreateResponsePayload payload) {
        resultSuccess = payload.success();
        resultMessage = MoneyText.abbreviateCurrencyTokens(payload.message());
    }

    private void sendCreateRequest() {
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected == null) {
            resultSuccess = false;
            resultMessage = "No account selected.";
            return;
        }

        String playerName = selectedPlayerName == null ? "" : selectedPlayerName.trim();
        if (playerName.isEmpty()) {
            resultSuccess = false;
            resultMessage = "Choose a player.";
            return;
        }

        String amount = amountField.getValue().trim();
        if (amount.isEmpty()) {
            resultSuccess = false;
            resultMessage = "Enter an amount.";
            return;
        }

        if (destinationAccountId == null) {
            resultSuccess = false;
            resultMessage = "Choose a destination account.";
            return;
        }

        resultSuccess = true;
        resultMessage = "Sending pay request...";
        PacketDistributor.sendToServer(new PayRequestCreatePayload(
                selected.accountId(),
                playerName,
                amount,
                destinationAccountId.toString()
        ));
    }

    private void updatePlayerButtonText(boolean hovered) {
        if (playerSelectButton == null) {
            return;
        }
        String label;
        if (hovered) {
            label = "Choose Player";
        } else if (selectedPlayerName == null || selectedPlayerName.isBlank()) {
            label = "Choose Player";
        } else {
            label = "Player: " + selectedPlayerName;
        }
        int maxWidth = Math.max(16, playerSelectButton.getWidth() - 12);
        playerSelectButton.setMessage(Component.literal(fitToWidth(label, maxWidth)).withStyle(ChatFormatting.WHITE));
    }

    private void updateDestinationButtonText() {
        if (destinationButton == null) {
            return;
        }
        String label = "Destination: " + resolveDestinationLabel(destinationAccountId);
        int maxWidth = Math.max(16, destinationButton.getWidth() - 12);
        destinationButton.setMessage(Component.literal(fitToWidth(label, maxWidth)).withStyle(ChatFormatting.WHITE));
    }

    private static UUID resolveDefaultDestinationAccountId() {
        List<AccountSummary> accounts = ClientATMData.getAccounts();
        for (AccountSummary account : accounts) {
            if (account.isPrimary()) {
                return account.accountId();
            }
        }
        AccountSummary selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            return selected.accountId();
        }
        return accounts.isEmpty() ? null : accounts.getFirst().accountId();
    }

    private static String resolveDestinationLabel(UUID accountId) {
        if (accountId == null) {
            return "None";
        }
        for (AccountSummary account : ClientATMData.getAccounts()) {
            if (account.accountId().equals(accountId)) {
                return account.accountType() + " @ " + account.bankName() + " (" + shortId(account.accountId()) + ")";
            }
        }
        return "Unknown (" + shortId(accountId) + ")";
    }

    private static String shortId(UUID uuid) {
        String raw = uuid.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int sectionTop = panelTop + 62;

        updatePlayerButtonText(playerSelectButton != null && playerSelectButton.isHoveredOrFocused());

        drawCenteredFittedString(graphics, "Create Pay Request",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);

        if (!resultMessage.isEmpty()) {
            int color = resultSuccess ? COLOR_SUCCESS : COLOR_ERROR;
            drawCenteredFittedString(graphics, resultMessage,
                    panelLeft + panelWidth / 2, panelTop + 44, contentWidth, color);
        }

        int playerLabelY = sectionTop + Math.max(0, (playerSelectButton.getY() - sectionTop - font.lineHeight) / 2);
        int playerBottom = playerSelectButton.getY() + playerSelectButton.getHeight();
        int amountLabelY = playerBottom + Math.max(0, (amountField.getY() - playerBottom - font.lineHeight) / 2);
        int amountBottom = amountField.getY() + amountField.getHeight();
        int destinationLabelY = amountBottom + Math.max(0, (destinationButton.getY() - amountBottom - font.lineHeight) / 2);

        graphics.drawString(font, "Target Player", contentLeft + 6, playerLabelY, COLOR_LABEL);
        graphics.drawString(font, "Amount", contentLeft + 6, amountLabelY, COLOR_LABEL);
        graphics.drawString(font, "Receive Into Account", contentLeft + 6, destinationLabelY, COLOR_LABEL);
    }
}
