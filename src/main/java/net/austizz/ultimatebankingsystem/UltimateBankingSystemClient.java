package net.austizz.ultimatebankingsystem;

import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.austizz.ultimatebankingsystem.item.HandheldPaymentTerminalItem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = UltimateBankingSystem.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public class UltimateBankingSystemClient {
    public UltimateBankingSystemClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        UltimateBankingSystem.LOGGER.info("HELLO FROM CLIENT SETUP");
        UltimateBankingSystem.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        renderBalanceHud(mc, graphics);
    }

    @SubscribeEvent
    static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(VanillaGuiLayers.CHAT)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        renderHandheldTerminalOverlay(mc, event.getGuiGraphics());
    }

    private static void renderBalanceHud(Minecraft mc, GuiGraphics graphics) {
        if (!HudClientState.isEnabled()) {
            return;
        }

        String balance = HudClientState.getBalanceText();
        if (balance.isBlank()) {
            return;
        }

        String text = UbsClientTranslations.resolve("Balance: ") + Config.CURRENCY_SYMBOL.get() + MoneyText.abbreviate(balance);
        int color = Config.HUD_TEXT_COLOR.get();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        int textWidth = mc.font.width(text);
        int textHeight = mc.font.lineHeight;

        int x = 6;
        int y = 6;
        String corner = Config.HUD_CORNER.get().trim().toUpperCase();
        switch (corner) {
            case "TOP_RIGHT" -> {
                x = width - textWidth - 6;
                y = 6;
            }
            case "BOTTOM_LEFT" -> {
                x = 6;
                y = height - textHeight - 6;
            }
            case "BOTTOM_RIGHT" -> {
                x = width - textWidth - 6;
                y = height - textHeight - 6;
            }
            default -> {
                x = 6;
                y = 6;
            }
        }

        graphics.drawString(mc.font, text, x, y, color, true);
    }

    private static void renderHandheldTerminalOverlay(Minecraft mc, GuiGraphics graphics) {
        if (!(mc.hitResult instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof Player targetPlayer)) {
            return;
        }

        ItemStack terminalStack = HandheldPaymentTerminalItem.findHeldTerminal(targetPlayer);
        if (terminalStack.isEmpty()) {
            return;
        }

        String title = HandheldPaymentTerminalItem.getShopName(terminalStack);
        String amount = UbsClientTranslations.format("tooltip.ultimatebankingsystem.money",
                MoneyText.abbreviate(String.valueOf(HandheldPaymentTerminalItem.getPriceDollars(terminalStack))));
        String target = targetPlayer.getName().getString();

        String line1 = title + " | " + amount;
        String line2 = UbsClientTranslations.resolve("Merchant: ") + target;
        String line3 = UbsClientTranslations.resolve("Right-click to pay");

        int padding = 6;
        int lineHeight = mc.font.lineHeight;
        int width = Math.max(mc.font.width(line1), Math.max(mc.font.width(line2), mc.font.width(line3))) + padding * 2;
        int height = lineHeight * 3 + padding * 2 + 2;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - height - 46;

        graphics.fill(x, y, x + width, y + height, 0xD0262A2F);
        graphics.drawString(mc.font, line1, x + padding, y + padding, 0xFFFFFFFF, false);
        graphics.drawString(mc.font, line2, x + padding, y + padding + lineHeight + 1, 0xFFE6ECF3, false);
        graphics.drawString(mc.font, line3, x + padding, y + padding + (lineHeight + 1) * 2, 0xFFDCE8F7, false);
    }

    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() != ModItems.BANK_NOTE.get()
                && stack.getItem() != ModItems.CHEQUE.get()
                && stack.getItem() != ModItems.CREDIT_CARD.get()
                && stack.getItem() != ModItems.HANDHELD_PAYMENT_TERMINAL.get()) {
            return;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag = customData == null ? new CompoundTag() : customData.copyTag();
        if (tag == null) {
            tag = new CompoundTag();
        }
        boolean needsData = stack.getItem() == ModItems.CHEQUE.get()
                || stack.getItem() == ModItems.BANK_NOTE.get()
                || stack.getItem() == ModItems.CREDIT_CARD.get();
        if (needsData && tag.isEmpty()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.empty());
        if (stack.getItem() == ModItems.CHEQUE.get()) {
            addChequeTooltip(tooltip, tag);
        } else if (stack.getItem() == ModItems.BANK_NOTE.get()) {
            addBankNoteTooltip(tooltip, tag);
        } else if (stack.getItem() == ModItems.CREDIT_CARD.get()) {
            addCreditCardTooltip(tooltip, tag);
        } else {
            addHandheldTerminalTooltip(tooltip, stack);
        }
    }

    private static void addChequeTooltip(List<Component> tooltip, CompoundTag tag) {
        String id = tag.contains("ubs_cheque_id") ? tag.getString("ubs_cheque_id") : "Unknown";
        String amount = tag.contains("ubs_cheque_amount") ? tag.getString("ubs_cheque_amount") : "Unknown";
        String recipient = tag.contains("ubs_cheque_recipient_name")
                ? tag.getString("ubs_cheque_recipient_name")
                : (tag.contains("ubs_cheque_recipient") ? tag.getUUID("ubs_cheque_recipient").toString() : "Unknown");
        String writer = tag.contains("ubs_cheque_writer_name")
                ? tag.getString("ubs_cheque_writer_name")
                : (tag.contains("ubs_cheque_writer") ? tag.getUUID("ubs_cheque_writer").toString() : "Unknown");
        String sourceBank = tag.contains("ubs_cheque_source_bank") ? tag.getString("ubs_cheque_source_bank") : "Unknown";
        String sourceAccount = tag.contains("ubs_cheque_source_account")
                ? tag.getString("ubs_cheque_source_account")
                : "Unknown";

        tooltip.add(UbsTranslations.literal("Cheque Details").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(id).withStyle(ChatFormatting.AQUA)));
        tooltip.add(UbsTranslations.literal("Pay To: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(recipient).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(UbsTranslations.literal("From: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(writer).withStyle(ChatFormatting.GOLD)));
        tooltip.add(UbsTranslations.literal("Source Bank: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(sourceBank).withStyle(ChatFormatting.BLUE)));
        tooltip.add(UbsTranslations.literal("Source Account: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(sourceAccount).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(UbsTranslations.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(MoneyText.abbreviateWithDollar(amount)).withStyle(ChatFormatting.GREEN)));
    }

    private static void addBankNoteTooltip(List<Component> tooltip, CompoundTag tag) {
        String serial = tag.contains("ubs_note_serial") ? tag.getString("ubs_note_serial") : "Unknown";
        String amount = tag.contains("ubs_note_amount") ? tag.getString("ubs_note_amount") : "Unknown";
        String issuer = tag.contains("ubs_note_issuer_name")
                ? tag.getString("ubs_note_issuer_name")
                : (tag.contains("ubs_note_issuer_uuid") ? tag.getUUID("ubs_note_issuer_uuid").toString() : "Unknown");
        String sourceBank = tag.contains("ubs_note_source_bank") ? tag.getString("ubs_note_source_bank") : "Unknown";
        String sourceAccount = tag.contains("ubs_note_source_account")
                ? tag.getString("ubs_note_source_account")
                : (tag.contains("ubs_note_account") ? tag.getUUID("ubs_note_account").toString() : "Unknown");

        tooltip.add(UbsTranslations.literal("Bank Note Details").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(serial).withStyle(ChatFormatting.AQUA)));
        tooltip.add(UbsTranslations.literal("Issued By: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(issuer).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(UbsTranslations.literal("Source Bank: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(sourceBank).withStyle(ChatFormatting.BLUE)));
        tooltip.add(UbsTranslations.literal("Source Account: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(sourceAccount).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(UbsTranslations.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(MoneyText.abbreviateWithDollar(amount)).withStyle(ChatFormatting.GREEN)));
    }

    private static void addCreditCardTooltip(List<Component> tooltip, CompoundTag tag) {
        String cardNumber = tag.contains(CreditCardService.TAG_CARD_NUMBER)
                ? tag.getString(CreditCardService.TAG_CARD_NUMBER)
                : "";
        String cvc = tag.contains(CreditCardService.TAG_CVC)
                ? tag.getString(CreditCardService.TAG_CVC)
                : "---";
        String accountId = tag.hasUUID(CreditCardService.TAG_ACCOUNT_ID)
                ? tag.getUUID(CreditCardService.TAG_ACCOUNT_ID).toString()
                : "Unknown";
        String bankName = tag.contains(CreditCardService.TAG_BANK_NAME)
                ? tag.getString(CreditCardService.TAG_BANK_NAME)
                : "Unknown Bank";
        long expiry = tag.contains(CreditCardService.TAG_EXPIRY_AT)
                ? tag.getLong(CreditCardService.TAG_EXPIRY_AT)
                : 0L;
        boolean blocked = tag.contains(CreditCardService.TAG_BLOCKED) && tag.getBoolean(CreditCardService.TAG_BLOCKED);
        boolean expired = expiry > 0L && System.currentTimeMillis() > expiry;

        tooltip.add(UbsTranslations.literal("Credit Card Details").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("Card Number: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(CreditCardService.maskCardNumber(cardNumber)).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(UbsTranslations.literal("CVC: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(cvc).withStyle(ChatFormatting.GOLD)));
        tooltip.add(UbsTranslations.literal("Bank: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(bankName).withStyle(ChatFormatting.BLUE)));
        tooltip.add(UbsTranslations.literal("Status: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(blocked ? "BLOCKED" : "ACTIVE")
                        .withStyle(blocked ? ChatFormatting.RED : ChatFormatting.GREEN)));
        tooltip.add(UbsTranslations.literal("Linked Account: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(accountId).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(UbsTranslations.literal("Expiry: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(CreditCardService.formatExpiryMonthYear(expiry))
                        .withStyle(expired ? ChatFormatting.RED : ChatFormatting.GREEN)));
        if (expired) {
            tooltip.add(UbsTranslations.literal("This card is expired.").withStyle(ChatFormatting.RED));
        }
        if (blocked) {
            tooltip.add(UbsTranslations.literal("This card is blocked.").withStyle(ChatFormatting.RED));
        }
    }

    private static void addHandheldTerminalTooltip(List<Component> tooltip, ItemStack stack) {
        String shopName = HandheldPaymentTerminalItem.getShopName(stack);
        String amount = MoneyText.abbreviate(String.valueOf(HandheldPaymentTerminalItem.getPriceDollars(stack)));
        int result = HandheldPaymentTerminalItem.getResultState(stack);
        Component state = switch (result) {
            case HandheldPaymentTerminalItem.RESULT_SUCCESS -> UbsTranslations.tr("tooltip.ultimatebankingsystem.terminal_state.success");
            case HandheldPaymentTerminalItem.RESULT_DENIED -> UbsTranslations.tr("tooltip.ultimatebankingsystem.terminal_state.denied");
            default -> UbsTranslations.tr("tooltip.ultimatebankingsystem.terminal_state.idle");
        };
        ChatFormatting stateColor = switch (result) {
            case HandheldPaymentTerminalItem.RESULT_SUCCESS -> ChatFormatting.GREEN;
            case HandheldPaymentTerminalItem.RESULT_DENIED -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(UbsTranslations.literal("Handheld Terminal").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("Name: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(shopName).withStyle(ChatFormatting.WHITE)));
        tooltip.add(UbsTranslations.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.tr("tooltip.ultimatebankingsystem.money", amount).withStyle(ChatFormatting.GOLD)));
        tooltip.add(UbsTranslations.literal("State: ").withStyle(ChatFormatting.GRAY)
                .append(state.copy().withStyle(stateColor)));
        tooltip.add(UbsTranslations.literal("Use: Hold it while others right-click you to pay").withStyle(ChatFormatting.DARK_GRAY));
    }
}
