package net.austizz.ultimatebankingsystem;

import net.austizz.ultimatebankingsystem.client.HudClientState;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
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
        if (!HudClientState.isEnabled()) {
            return;
        }

        String balance = HudClientState.getBalanceText();
        if (balance.isBlank()) {
            return;
        }

        String text = "Balance: " + Config.CURRENCY_SYMBOL.get() + MoneyText.abbreviate(balance);
        GuiGraphics graphics = event.getGuiGraphics();
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

    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (stack.getItem() != ModItems.BANK_NOTE.get() && stack.getItem() != ModItems.CHEQUE.get()) {
            return;
        }

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        if (tag == null || tag.isEmpty()) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.empty());
        if (stack.getItem() == ModItems.CHEQUE.get()) {
            addChequeTooltip(tooltip, tag);
        } else {
            addBankNoteTooltip(tooltip, tag);
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

        tooltip.add(Component.literal("Cheque Details").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        tooltip.add(Component.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(id).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("Pay To: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(recipient).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("From: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(writer).withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("Source Bank: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sourceBank).withStyle(ChatFormatting.BLUE)));
        tooltip.add(Component.literal("Source Account: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sourceAccount).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(Component.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyText.abbreviateWithDollar(amount)).withStyle(ChatFormatting.GREEN)));
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

        tooltip.add(Component.literal("Bank Note Details").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(Component.literal("ID: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(serial).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("Issued By: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(issuer).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("Source Bank: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sourceBank).withStyle(ChatFormatting.BLUE)));
        tooltip.add(Component.literal("Source Account: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(sourceAccount).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(Component.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyText.abbreviateWithDollar(amount)).withStyle(ChatFormatting.GREEN)));
    }
}
