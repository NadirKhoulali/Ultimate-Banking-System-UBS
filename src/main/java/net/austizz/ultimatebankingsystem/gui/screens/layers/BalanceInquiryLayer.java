package net.austizz.ultimatebankingsystem.gui.screens.layers;

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
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class BalanceInquiryLayer extends AbstractScreenLayer {

    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
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
            Component.literal("Back").withStyle(ChatFormatting.WHITE),
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
        int contentLeft = panelLeft + 14;
        int contentWidth = panelWidth - 28;
        int sectionTop = panelTop + 58;
        int sectionBottom = sectionTop + 110;

        drawCenteredFittedString(graphics, "Balance Inquiry",
                panelLeft + panelWidth / 2, panelTop + 31, contentWidth, COLOR_TITLE);
        drawSectionBox(graphics, contentLeft, sectionTop, contentLeft + contentWidth, sectionBottom);

        if (!loaded) {
            drawCenteredFittedString(graphics, "Loading...",
                    panelLeft + panelWidth / 2, sectionTop + 46, contentWidth, COLOR_MUTED);
            return;
        }

        int labelX = contentLeft + 8;
        int labelWidth = 98;
        int valueX = labelX + labelWidth;
        int valueMaxWidth = Math.max(20, contentLeft + contentWidth - 8 - valueX);
        int y = sectionTop + 10;
        int lineSpacing = 20;
        int labelColor = COLOR_LABEL;
        int valueColor = COLOR_VALUE;

        graphics.drawString(font, "Account Type:", labelX, y, labelColor);
        drawFittedString(graphics, accountType, valueX, y, valueMaxWidth, valueColor);
        y += lineSpacing;

        graphics.drawString(font, "Bank Name:", labelX, y, labelColor);
        drawFittedString(graphics, bankName, valueX, y, valueMaxWidth, valueColor);
        y += lineSpacing;

        graphics.drawString(font, "Account ID:", labelX, y, labelColor);
        drawFittedString(graphics, accountId, valueX, y, valueMaxWidth, valueColor);
        y += lineSpacing;

        graphics.drawString(font, "Balance:", labelX, y, labelColor);
        drawFittedString(graphics, MoneyText.abbreviateWithDollar(balance), valueX, y, valueMaxWidth, COLOR_SUCCESS);
        y += lineSpacing;

        graphics.drawString(font, "Created:", labelX, y, labelColor);
        drawFittedString(graphics, createdDate, valueX, y, valueMaxWidth, valueColor);
    }
}
