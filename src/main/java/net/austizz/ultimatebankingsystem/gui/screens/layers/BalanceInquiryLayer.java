package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.gui.screens.ClientATMData;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.austizz.ultimatebankingsystem.network.BalanceRequestPayload;
import net.austizz.ultimatebankingsystem.network.BalanceResponsePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class BalanceInquiryLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = new ResourceLocation(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm");

    private String accountType = null;
    private String bankName = null;
    private String accountId = null;
    private String balance = null;
    private String createdDate = null;
    private boolean loaded = false;

    public BalanceInquiryLayer(Minecraft minecraft) {
        super(minecraft);
    }

    @Override
    protected void onInit() {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelHeight = bankScreen.getPanelHeight();

        addWidget(new NineSliceTexturedButton(
            panelLeft + 14,
            panelTop + panelHeight - 36,
            56, 22,
            ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
            4, 4, 4, 4,
            UbsTranslations.literal("Back").withStyle(ChatFormatting.WHITE),
            btn -> bankScreen.popLayer()
        ));

        var selected = ClientATMData.getSelectedAccount();
        if (selected != null) {
            PacketDistributor.sendToServer(new BalanceRequestPayload(selected.accountId()));
        }
    }

    public void updateData(BalanceResponsePayload payload) {
        this.accountType = payload.accountType();
        this.bankName = payload.bankName();
        this.accountId = payload.accountId();
        this.balance = payload.balance();
        this.createdDate = formatCreatedDate(payload.createdDate());
        this.loaded = true;
    }

    private static String formatCreatedDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "Unknown";
        }
        try {
            return DATE_FORMATTER.format(LocalDateTime.parse(rawDate));
        } catch (DateTimeParseException ignored) {
            return rawDate.replace('T', ' ');
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
        int sectionTop = panelTop + 58;
        int sectionBottom = Math.min(panelTop + panelHeight - 48, sectionTop + 132);

        drawCenteredFittedString(graphics, "Balance Inquiry",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);
        drawSectionBox(graphics, contentLeft, sectionTop, contentLeft + contentWidth, sectionBottom);

        if (!loaded) {
            drawCenteredFittedString(graphics, "Loading...",
                    panelLeft + panelWidth / 2, sectionTop + 46, contentWidth, COLOR_MUTED);
            return;
        }

        int labelX = contentLeft + 8;
        int rightX = contentLeft + contentWidth - 8;
        int labelWidth = Math.min(
                contentWidth - 120,
                Math.max(
                        Math.max(font.width(UbsClientTranslations.resolve("Account Type:")), font.width(UbsClientTranslations.resolve("Bank Name:"))),
                        Math.max(font.width(UbsClientTranslations.resolve("Balance:")), font.width(UbsClientTranslations.resolve("Created:")))
                ) + 10
        );
        int valueX = labelX + labelWidth;
        int valueMaxWidth = Math.max(20, rightX - valueX);
        int y = sectionTop + 10;
        int lineSpacing = 20;
        int labelColor = COLOR_LABEL;
        int valueColor = COLOR_VALUE;

        drawFittedString(graphics, "Account Type:", labelX, y, labelWidth - 4, labelColor);
        drawFittedString(graphics, accountType, valueX, y, valueMaxWidth, valueColor);
        y += lineSpacing;

        drawFittedString(graphics, "Bank Name:", labelX, y, labelWidth - 4, labelColor);
        drawFittedString(graphics, bankName, valueX, y, valueMaxWidth, valueColor);
        y += lineSpacing;

        drawFittedString(graphics, "Account ID:", labelX, y, contentWidth - 16, labelColor);
        y += 12;
        drawFittedString(graphics, accountId, labelX, y, contentWidth - 16, valueColor);
        y += 18;

        drawFittedString(graphics, "Balance:", labelX, y, labelWidth - 4, labelColor);
        drawFittedString(graphics, MoneyText.abbreviateWithDollar(balance), valueX, y, valueMaxWidth, COLOR_SUCCESS);
        y += lineSpacing;

        drawFittedString(graphics, "Created:", labelX, y, labelWidth - 4, labelColor);
        drawFittedString(graphics, createdDate, valueX, y, valueMaxWidth, valueColor);
    }
}
