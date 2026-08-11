package net.austizz.ultimatebankingsystem;

import net.austizz.ultimatebankingsystem.client.ActionAlertClientState;
import net.austizz.ultimatebankingsystem.client.BalanceHudRenderer;
import net.austizz.ultimatebankingsystem.client.ClaimModeClientState;
import net.austizz.ultimatebankingsystem.client.ClaimModeOutlineRenderer;
import net.austizz.ultimatebankingsystem.client.DeliveryInfoBoardClientState;
import net.austizz.ultimatebankingsystem.client.DallasMaskAnimationClientState;
import net.austizz.ultimatebankingsystem.client.DallasMaskKeyMappings;
import net.austizz.ultimatebankingsystem.client.HeistClientState;
import net.austizz.ultimatebankingsystem.client.HeistExfillBorderClientState;
import net.austizz.ultimatebankingsystem.client.HeistExfillBorderRenderer;
import net.austizz.ultimatebankingsystem.client.HeistWorldHologramRenderer;
import net.austizz.ultimatebankingsystem.client.NotificationClientState;
import net.austizz.ultimatebankingsystem.client.NotificationRenderer;
import net.austizz.ultimatebankingsystem.client.PhoneNotificationClientState;
import net.austizz.ultimatebankingsystem.client.PickpocketClientState;
import net.austizz.ultimatebankingsystem.client.PickpocketKeyMappings;
import net.austizz.ultimatebankingsystem.client.SmartphoneClientState;
import net.austizz.ultimatebankingsystem.client.SmartphoneKeyMappings;
import net.austizz.ultimatebankingsystem.client.SmartphoneOverlay;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.client.DeliveryPalletLabelsClientState;
import net.austizz.ultimatebankingsystem.client.ShopSetupObjectiveClientState;
import net.austizz.ultimatebankingsystem.client.StockroomLocateClientState;
import net.austizz.ultimatebankingsystem.client.SafeBoxEscortMarkerClientState;
import net.austizz.ultimatebankingsystem.client.renderer.DallasMaskFirstPersonRenderer;
import net.austizz.ultimatebankingsystem.client.renderer.Ove9000SawFirstPersonRenderer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ModularWallDisplayBlock;
import net.austizz.ultimatebankingsystem.block.custom.PalletBlock;
import net.austizz.ultimatebankingsystem.block.custom.SafetyDepositBoxRowBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.PalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShoppingBagDataKeys;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.gui.screens.ShelfItemPositionScreen;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.item.HandheldPaymentTerminalItem;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.PickpocketCancelPayload;
import net.austizz.ultimatebankingsystem.network.PickpocketStartPayload;
import net.austizz.ultimatebankingsystem.network.DallasMaskTogglePayload;
import net.austizz.ultimatebankingsystem.network.HeistActionHoldPayload;
import net.austizz.ultimatebankingsystem.network.ShopSetupObjectivePayload;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService;
import net.austizz.ultimatebankingsystem.shelf.ShelfPrice;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public class UltimateBankingSystemClient {
    private static final int STOCKROOM_LINE_SEGMENTS = 28;
    private static final double STOCKROOM_LINE_START_OFFSET = 0.42D;
    private static final double STOCKROOM_LINE_MAX_ARC = 0.34D;
    private static final double SAFE_BOX_LINE_START_OFFSET = 0.42D;
    private static final float SAFE_BOX_LABEL_SCALE = 0.018F;
    private static final int PALLET_LOCATE_SLOT_BASE = 10_000;
    private static final int PALLET_LOCATE_SLOT_COUNT = 27;
    private static final double PALLET_BOX_BASE_Y = 0.5625D;
    private static final double PALLET_BOX_STEP_Y = 0.75D;
    private static final double PALLET_BOX_HEIGHT = 0.75D;
    private static final double BAG_TIMER_SCAN_RADIUS = 30.0D;
    private static final double BAG_TIMER_VERTICAL_SCAN = 12.0D;
    private static final double BAG_TIMER_CLUSTER_RANGE_SQ = 0.85D * 0.85D;
    private static final float BAG_TIMER_LABEL_SCALE = 0.025F;
    private static final float DELIVERY_PALLET_LABEL_SCALE = 0.025F;
    private static final double DELIVERY_PALLET_LABEL_MIN_Y_OFFSET = 3.35D;
    private static final double DELIVERY_PALLET_LABEL_TOP_PADDING = 0.50D;
    private static final double DELIVERY_PALLET_LABEL_RENDER_RANGE_SQ = 160.0D * 160.0D;
    private static final int DELIVERY_ALERT_FADE_MS = 220;
    private static final int MAX_UBS_MENU_GUI_SCALE = 2;
    // Keep timer holograms around eye-level so they are readable in crowded checkout areas.
    private static final float BAG_TIMER_LABEL_Y_OFFSET = 1.50F;
    private static final double PICKPOCKET_TARGET_RANGE_BLOCKS = 1.0D;
    private static boolean pickpocketHoldSent;
    private static UUID pickpocketRequestedTargetId;
    private static boolean heistActionHoldSent;
    private static boolean forcedMenuGuiScaleActive;
    private static Integer forcedMenuPreviousGuiScale;
    private static int smartphoneInputScreenOpenBlockTicks;

    private record PickpocketTarget(Player player, double distanceToEyeSq) {
    }

    @SubscribeEvent
    static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || ClaimModeClientState.active()) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        // HUD pass only: when a screen is open we draw alerts in the screen post pass so they stay above UI.
        if (mc.screen == null) {
            renderActionAlert(mc, graphics);
            renderPhoneNotification(mc, graphics);
            NotificationRenderer.render(mc, graphics);
            renderShopSetupObjective(mc, graphics);
            renderDeliveryInfoBoard(mc, graphics);
        }
        renderBalanceHud(mc, graphics);
        renderHandheldTerminalOverlay(mc, graphics);
        renderHeistHud(mc, graphics);
        renderPickpocketOverlay(mc, graphics);
        renderShelfOverlay(mc, graphics);
        SmartphoneOverlay.render(mc, graphics);
    }

    @SubscribeEvent
    static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen == null) {
            return;
        }
        // Screen pass: keep shared alerts visible above desktop/ATM widgets.
        renderActionAlert(mc, event.getGuiGraphics());
        renderPhoneNotification(mc, event.getGuiGraphics());
        NotificationRenderer.render(mc, event.getGuiGraphics());
        renderDeliveryInfoBoard(mc, event.getGuiGraphics());
    }

    @SubscribeEvent
    static void onKeyInput(InputEvent.Key event) {
        if (ClaimModeClientState.active()) {
            ClaimModeClientState.handleKey(event.getKey(), event.getAction(), event.getModifiers());
            return;
        }
        if (SmartphoneClientState.isInteractive()) {
            boolean wasTextInputFocused = SmartphoneClientState.isTextInputFocused();
            if (event.getAction() == GLFW.GLFW_PRESS || event.getAction() == GLFW.GLFW_REPEAT) {
                SmartphoneClientState.handleKey(event.getKey(), event.getAction());
            }
            if (wasTextInputFocused || SmartphoneClientState.isTextInputFocused()) {
                smartphoneInputScreenOpenBlockTicks = 2;
                suppressGameplayKeybinds(Minecraft.getInstance());
            }
            return;
        }
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        if (event.getKey() == GLFW.GLFW_KEY_RIGHT_BRACKET
                && ShopSetupObjectiveClientState.isActive()
                && ShopSetupObjectiveClientState.getProjectCount() > 1) {
            ShopSetupObjectiveClientState.cycleProject();
            return;
        }
        boolean minimizeKey = event.getKey() == GLFW.GLFW_KEY_X;
        boolean hardCloseKey = minimizeKey && (event.getModifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
        if (!minimizeKey && !hardCloseKey) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options.hideGui) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        if (ShopSetupObjectiveClientState.isActive()) {
            if (hardCloseKey) {
                ShopSetupObjectiveClientState.dismiss();
                return;
            }
            ShopSetupObjectiveClientState.toggleCollapsed();
            return;
        }
        if (DeliveryInfoBoardClientState.isActive()) {
            DeliveryInfoBoardClientState.toggleCollapsed();
            return;
        }
    }

    @SubscribeEvent
    static void onScreenOpening(ScreenEvent.Opening event) {
        if (ClaimModeClientState.active()
                || smartphoneInputScreenOpenBlockTicks > 0
                || SmartphoneClientState.isTextInputFocused()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        if (ClaimModeClientState.active() || SmartphoneClientState.isTextInputFocused()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (ClaimModeClientState.active()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getWindow() != null) {
                double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                        / mc.getWindow().getScreenWidth();
                double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                        / mc.getWindow().getScreenHeight();
                ClaimModeClientState.handleMouse(mouseX, mouseY,
                        event.getButton(), event.getAction());
            }
            event.setCanceled(true);
            return;
        }
        if (SmartphoneClientState.isTextInputFocused() && event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event.setCanceled(true);
            return;
        }
        if (!SmartphoneClientState.isInteractive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        if (SmartphoneClientState.handleMouseButton(mouseX, mouseY, event.getButton(), event.getAction())) {
            event.setCanceled(true);
            return;
        }
        if (SmartphoneClientState.isTextInputFocused()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ClaimModeClientState.active()) {
            event.setCanceled(true);
            return;
        }
        if (SmartphoneClientState.handleScroll(event.getScrollDeltaY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        ClaimModeClientState.tick(mc);
        if (ClaimModeClientState.active()) {
            suppressClaimModeKeybinds(mc, ClaimModeClientState.cursorMode());
            sendHeistActionCancelIfNeeded();
            sendPickpocketCancelIfNeeded();
            return;
        }
        SafeBoxEscortMarkerClientState.onLevelAvailabilityChanged(mc.level != null);
        if (smartphoneInputScreenOpenBlockTicks > 0) {
            smartphoneInputScreenOpenBlockTicks--;
        }
        tickUbsMenuGuiScaleGuard(mc);
        SmartphoneClientState.tick(mc);
        if (SmartphoneClientState.isTextInputFocused()) {
            suppressGameplayKeybinds(mc);
            sendPickpocketCancelIfNeeded();
            return;
        }
        while (SmartphoneKeyMappings.OPEN_PHONE.consumeClick()) {
            if (mc.screen == null && !mc.options.hideGui) {
                SmartphoneClientState.requestOpen();
            }
        }

        PickpocketClientState.tickClient();

        if (mc.player == null || mc.level == null) {
            sendHeistActionCancelIfNeeded();
            sendPickpocketCancelIfNeeded();
            PickpocketClientState.clear();
            return;
        }

        while (DallasMaskKeyMappings.TOGGLE_MASK.consumeClick()) {
            if (mc.screen == null && !mc.options.hideGui) {
                PacketDistributor.sendToServer(new DallasMaskTogglePayload());
            }
        }

        if (mc.screen != null || mc.options.hideGui) {
            sendHeistActionCancelIfNeeded();
            sendPickpocketCancelIfNeeded();
            return;
        }

        boolean heistAction = HeistClientState.actionable() && PickpocketKeyMappings.isHeistActionDown();
        if (heistAction) {
            mc.options.keySwapOffhand.setDown(false);
            while (mc.options.keySwapOffhand.consumeClick()) {
                // F belongs to the contextual heist action while this prompt is visible.
            }
            PacketDistributor.sendToServer(new HeistActionHoldPayload(true));
            heistActionHoldSent = true;
        } else {
            sendHeistActionCancelIfNeeded();
        }

        PickpocketTarget target = resolvePickpocketTarget(mc, PICKPOCKET_TARGET_RANGE_BLOCKS);
        boolean holdingChord = PickpocketKeyMappings.isPickpocketChordDown();
        boolean canStart = holdingChord
                && target != null
                && PickpocketClientState.getCooldownRemainingTicks() <= 0;

        if (!canStart) {
            sendPickpocketCancelIfNeeded();
            return;
        }

        UUID targetId = target.player().getUUID();
        if (!pickpocketHoldSent) {
            PacketDistributor.sendToServer(new PickpocketStartPayload(targetId));
            pickpocketHoldSent = true;
            pickpocketRequestedTargetId = targetId;
            return;
        }

        if (!targetId.equals(pickpocketRequestedTargetId)) {
            // Retargeting while holding forces a fresh start request to keep server state deterministic.
            sendPickpocketCancelIfNeeded();
        }
    }

    @SubscribeEvent
    static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClaimModeClientState.clear();
        ClaimModeOutlineRenderer.clearCache();
        SafeBoxEscortMarkerClientState.onClientDisconnect();
        DallasMaskAnimationClientState.clear();
        HeistExfillBorderClientState.clear();
        HeistExfillBorderRenderer.clearCache();
        NotificationClientState.clear();
    }

    private static void suppressGameplayKeybinds(Minecraft mc) {
        if (mc == null || mc.options == null || mc.options.keyMappings == null) {
            return;
        }
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null) {
                continue;
            }
            mapping.setDown(false);
            while (mapping.consumeClick()) {
                // Drain queued keybind clicks produced by typing inside the phone input field.
            }
        }
        if (mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    private static void suppressClaimModeKeybinds(Minecraft mc, boolean freezeMovement) {
        if (mc == null || mc.options == null || mc.options.keyMappings == null) {
            return;
        }
        for (KeyMapping mapping : mc.options.keyMappings) {
            if (mapping == null || !freezeMovement && isClaimMovementKey(mc, mapping)) {
                continue;
            }
            mapping.setDown(false);
            while (mapping.consumeClick()) {
                // Claim mode owns all non-movement input; drain queued gameplay actions.
            }
        }
        if (freezeMovement && mc.player != null) {
            mc.player.setSprinting(false);
        }
    }

    private static boolean isClaimMovementKey(Minecraft mc, KeyMapping mapping) {
        return mapping == mc.options.keyUp
                || mapping == mc.options.keyDown
                || mapping == mc.options.keyLeft
                || mapping == mc.options.keyRight
                || mapping == mc.options.keyJump
                || mapping == mc.options.keyShift
                || mapping == mc.options.keySprint;
    }

    private static void tickUbsMenuGuiScaleGuard(Minecraft mc) {
        if (mc == null || mc.options == null || mc.options.guiScale() == null) {
            return;
        }
        Integer currentScale = mc.options.guiScale().get();
        if (currentScale == null) {
            return;
        }

        Screen currentScreen = mc.screen;
        boolean shouldForce = shouldForceGuiScaleForScreen(currentScreen);
        if (shouldForce) {
            if (!forcedMenuGuiScaleActive) {
                forcedMenuPreviousGuiScale = currentScale;
                forcedMenuGuiScaleActive = true;
            }
            if (currentScale > MAX_UBS_MENU_GUI_SCALE) {
                mc.options.guiScale().set(MAX_UBS_MENU_GUI_SCALE);
                mc.resizeDisplay();
            }
            return;
        }

        if (!forcedMenuGuiScaleActive) {
            return;
        }
        Integer previous = forcedMenuPreviousGuiScale;
        if (previous != null && currentScale.intValue() != previous.intValue()) {
            mc.options.guiScale().set(previous);
            mc.resizeDisplay();
        }
        forcedMenuGuiScaleActive = false;
        forcedMenuPreviousGuiScale = null;
    }

    private static boolean shouldForceGuiScaleForScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.BankOwnerPcScreen) {
            // The owner PC is responsive and follows the player's configured GUI scale.
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.WalletScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.SafetyDepositBoxScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.SecureSafeScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.SecureSafeAccessScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.RfidScannerScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.NumismaticsMigrationScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.CardboardBoxScreen) {
            return false;
        }
        if (screen instanceof net.austizz.ultimatebankingsystem.gui.screens.HeistDuffelScreen) {
            return false;
        }
        String className = screen.getClass().getName();
        return className.startsWith("net.austizz.ultimatebankingsystem.gui.screens.");
    }

    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        if (ClaimModeClientState.active()) {
            ClaimModeOutlineRenderer.render(event, mc);
            return;
        }

        renderStockroomLocateLine(event, mc);
        renderSafeBoxEscortMarker(event, mc);
        renderShoppingBagTimers(event, mc);
        HeistExfillBorderRenderer.render(event, mc);
        HeistWorldHologramRenderer.render(event, mc);
    }

    private static void renderStockroomLocateLine(RenderLevelStageEvent event, Minecraft mc) {
        if (!StockroomLocateClientState.isActive()) {
            return;
        }

        String clientDim = normalizeDimensionId(mc.level.dimension().location().toString());
        String targetDim = normalizeDimensionId(StockroomLocateClientState.getDimensionId());
        if (!clientDim.equals(targetDim)) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // Use player eye + aim vector so the guide line stays centered even with view bobbing enabled.
        Vec3 eye = mc.player.getEyePosition(partialTick);
        Vec3 cameraForward = mc.player.getViewVector(partialTick);
        if (cameraForward.lengthSqr() < 0.000001D) {
            var lookVector = event.getCamera().getLookVector();
            cameraForward = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        }
        if (cameraForward.lengthSqr() < 0.000001D) {
            cameraForward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        cameraForward = cameraForward.normalize();
        // Start a little in front of the eye position to avoid near-plane clipping and flicker.
        Vec3 from = eye.add(cameraForward.scale(STOCKROOM_LINE_START_OFFSET));
        LocateRenderTarget locateTarget = resolveLocateRenderTarget(mc);
        Vec3 to = locateTarget.anchor();
        if (from.distanceToSqr(to) < 0.0001D) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        drawStockroomGuideLine(pose, lines, from, to);

        AABB highlight = locateTarget.box();
        LevelRenderer.renderLineBox(
                pose,
                lines,
                highlight.minX,
                highlight.minY,
                highlight.minZ,
                highlight.maxX,
                highlight.maxY,
                highlight.maxZ,
                1.0F,
                0.92F,
                0.45F,
                1.0F
        );
        if (!locateTarget.palletBox()) {
            LevelRenderer.renderLineBox(
                    pose,
                    lines,
                    StockroomLocateClientState.getX() + 0.36D,
                    StockroomLocateClientState.getY() + 0.10D,
                    StockroomLocateClientState.getZ() + 0.36D,
                    StockroomLocateClientState.getX() + 0.64D,
                    StockroomLocateClientState.getY() + 1.35D,
                    StockroomLocateClientState.getZ() + 0.64D,
                    0.42F,
                    0.88F,
                    1.0F,
                    0.85F
            );
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void renderSafeBoxEscortMarker(RenderLevelStageEvent event, Minecraft mc) {
        SafeBoxEscortMarkerClientState.Snapshot marker = SafeBoxEscortMarkerClientState.snapshot();
        String dimensionId = mc.level.dimension().location().toString();
        if (!marker.shouldRenderIn(dimensionId)) {
            return;
        }

        BlockPos rowPos = new BlockPos(marker.rowX(), marker.rowY(), marker.rowZ());
        boolean chunkLoaded = mc.level.hasChunkAt(rowPos);
        boolean validRow = false;
        SafeBoxEscortMarkerClientState.Facing facing = null;
        if (chunkLoaded) {
            BlockState rowState = mc.level.getBlockState(rowPos);
            if (rowState.getBlock() instanceof SafetyDepositBoxRowBlock
                    && rowState.hasProperty(SafetyDepositBoxRowBlock.FACING)) {
                validRow = true;
                facing = safeBoxMarkerFacing(rowState.getValue(SafetyDepositBoxRowBlock.FACING));
            }
        }

        SafeBoxEscortMarkerClientState.RenderTarget renderTarget = marker.resolveRenderTarget(
                new SafeBoxEscortMarkerClientState.RenderContext(
                        dimensionId, chunkLoaded, validRow, facing)
        ).orElse(null);
        if (renderTarget == null) {
            return;
        }
        SafeBoxEscortMarkerClientState.DoorGeometry geometry = renderTarget.geometry();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cameraForward = mc.player.getViewVector(partialTick);
        if (cameraForward.lengthSqr() < 0.000001D) {
            var lookVector = event.getCamera().getLookVector();
            cameraForward = new Vec3(lookVector.x(), lookVector.y(), lookVector.z());
        }
        if (cameraForward.lengthSqr() < 0.000001D) {
            cameraForward = new Vec3(0.0D, 0.0D, 1.0D);
        }
        Vec3 from = mc.player.getEyePosition(partialTick)
                .add(cameraForward.normalize().scale(SAFE_BOX_LINE_START_OFFSET));
        Vec3 to = new Vec3(geometry.anchorX(), geometry.anchorY(), geometry.anchorZ());
        if (from.distanceToSqr(to) < 0.0001D) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        drawSafeBoxDirectionalLine(pose, lines, from, to);
        LevelRenderer.renderLineBox(
                pose,
                lines,
                geometry.minX(),
                geometry.minY(),
                geometry.minZ(),
                geometry.maxX(),
                geometry.maxY(),
                geometry.maxZ(),
                0.72F,
                0.38F,
                1.0F,
                1.0F
        );

        pose.popPose();
        buffers.endBatch(RenderType.lines());

        drawSafeBoxMarkerLabel(pose, buffers, mc, camera, geometry, renderTarget.boxLabel());
        buffers.endBatch();
    }

    private static SafeBoxEscortMarkerClientState.Facing safeBoxMarkerFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> SafeBoxEscortMarkerClientState.Facing.NORTH;
            case SOUTH -> SafeBoxEscortMarkerClientState.Facing.SOUTH;
            case WEST -> SafeBoxEscortMarkerClientState.Facing.WEST;
            case EAST -> SafeBoxEscortMarkerClientState.Facing.EAST;
            default -> null;
        };
    }

    private static void drawSafeBoxMarkerLabel(PoseStack pose,
                                               MultiBufferSource.BufferSource buffers,
                                               Minecraft mc,
                                               Vec3 camera,
                                               SafeBoxEscortMarkerClientState.DoorGeometry geometry,
                                               String boxLabel) {
        if (mc.font == null || mc.getEntityRenderDispatcher() == null) {
            return;
        }
        String label = boxLabel == null || boxLabel.isBlank()
                ? "Assigned safe box"
                : "Safe box " + boxLabel;
        int bgAlpha = (int) (mc.options.getBackgroundOpacity(0.30F) * 255.0F) << 24;

        pose.pushPose();
        pose.translate(
                geometry.anchorX() - camera.x,
                geometry.anchorY() + 0.08D - camera.y,
                geometry.anchorZ() - camera.z
        );
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-SAFE_BOX_LABEL_SCALE, -SAFE_BOX_LABEL_SCALE, SAFE_BOX_LABEL_SCALE);
        Matrix4f matrix = pose.last().pose();
        float x = -mc.font.width(label) / 2.0F;
        mc.font.drawInBatch(
                label, x, 0.0F, 0xFFE7C7FF, false, matrix, buffers,
                Font.DisplayMode.NORMAL, bgAlpha, 0xF000F0);
        pose.popPose();
    }

    private static void renderShoppingBagTimers(RenderLevelStageEvent event, Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.font == null || mc.getEntityRenderDispatcher() == null) {
            return;
        }

        AABB searchBounds = mc.player.getBoundingBox().inflate(
                BAG_TIMER_SCAN_RADIUS,
                BAG_TIMER_VERTICAL_SCAN,
                BAG_TIMER_SCAN_RADIUS
        );

        List<ItemEntity> nearbyBags = mc.level.getEntitiesOfClass(
                ItemEntity.class,
                searchBounds,
                UltimateBankingSystemClient::isShoppingBagDrop
        );
        if (nearbyBags.isEmpty()) {
            return;
        }

        long nowTick = mc.level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        List<BagTimerCandidate> candidates = new ArrayList<>();
        for (ItemEntity bagEntity : nearbyBags) {
            if (bagEntity == null || !bagEntity.isAlive()) {
                continue;
            }
            long remainingTicks = computeBagRemainingTicks(bagEntity, nowTick);
            if (remainingTicks <= 0L) {
                continue;
            }
            String storeName = resolveBagStoreName(bagEntity.getItem());
            String ownerName = resolveBagOwnerName(mc, bagEntity.getItem());
            Vec3 entityPos = bagEntity.getPosition(partialTick);
            Vec3 anchor = entityPos.add(0.0D, bagEntity.getBbHeight() + BAG_TIMER_LABEL_Y_OFFSET, 0.0D);
            candidates.add(new BagTimerCandidate(bagEntity, anchor, remainingTicks, storeName, ownerName));
        }
        if (candidates.isEmpty()) {
            return;
        }

        List<BagTimerCandidate> winners = selectBagTimerWinners(candidates);
        if (winners.isEmpty()) {
            return;
        }

        PoseStack pose = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int bgAlpha = (int) (mc.options.getBackgroundOpacity(0.30F) * 255.0F) << 24;
        int packedLight = 0xF000F0;
        for (BagTimerCandidate winner : winners) {
            drawBagTimerLabel(pose, buffers, mc, cameraPos, winner, bgAlpha, packedLight);
        }
        buffers.endBatch();
    }

    private static void renderDeliveryPalletLabels(RenderLevelStageEvent event, Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.font == null || mc.getEntityRenderDispatcher() == null) {
            return;
        }
        String dimensionId = mc.level.dimension().location().toString();
        List<DeliveryPalletLabelsClientState.Label> labels = DeliveryPalletLabelsClientState.getLabels(dimensionId);
        if (labels.isEmpty()) {
            return;
        }

        Vec3 cameraPos = event.getCamera().getPosition();
        Vec3 playerPos = mc.player.position();
        PoseStack pose = event.getPoseStack();
        int bgAlpha = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        int packedLight = 0xF000F0;
        MultiBufferSource.BufferSource buffers = null;

        for (DeliveryPalletLabelsClientState.Label label : labels) {
            if (label == null) {
                continue;
            }
            BlockPos labelPos = new BlockPos(label.x(), label.y(), label.z());
            BlockState state = mc.level.getBlockState(labelPos);
            if (!state.is(ModBlocks.PALLET.get())) {
                continue;
            }
            BlockPos masterPos = PalletBlock.getMasterPos(state, labelPos);
            if (!mc.level.getBlockState(masterPos).is(ModBlocks.PALLET.get())) {
                continue;
            }
            Vec3 anchor = new Vec3(
                    masterPos.getX() + 0.5D,
                    masterPos.getY() + computeDeliveryPalletLabelYOffset(mc, masterPos),
                    masterPos.getZ() + 0.5D
            );
            if (anchor.distanceToSqr(playerPos) > DELIVERY_PALLET_LABEL_RENDER_RANGE_SQ) {
                continue;
            }
            if (isDeliveryPalletLabelOccluded(mc, cameraPos, anchor)) {
                continue;
            }
            if (buffers == null) {
                buffers = mc.renderBuffers().bufferSource();
            }
            drawDeliveryPalletLabel(pose, buffers, mc, cameraPos, masterPos, label.shopName(), bgAlpha, packedLight);
        }

        if (buffers != null) {
            buffers.endBatch();
        }
    }

    private static double computeDeliveryPalletLabelYOffset(Minecraft mc, BlockPos masterPos) {
        if (mc == null || mc.level == null || masterPos == null) {
            return DELIVERY_PALLET_LABEL_MIN_Y_OFFSET;
        }
        if (!(mc.level.getBlockEntity(masterPos) instanceof PalletBlockEntity pallet)) {
            return DELIVERY_PALLET_LABEL_MIN_Y_OFFSET;
        }
        int highestLayer = -1;
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack stack = pallet.getBox(column, layer);
                if (stack != null && !stack.isEmpty()) {
                    highestLayer = Math.max(highestLayer, layer);
                }
            }
        }
        if (highestLayer < 0) {
            return DELIVERY_PALLET_LABEL_MIN_Y_OFFSET;
        }
        double aboveBoxes = PALLET_BOX_BASE_Y
                + (PALLET_BOX_STEP_Y * (highestLayer + 1))
                + DELIVERY_PALLET_LABEL_TOP_PADDING;
        return Math.max(DELIVERY_PALLET_LABEL_MIN_Y_OFFSET, aboveBoxes);
    }

    private static boolean isDeliveryPalletLabelOccluded(Minecraft mc, Vec3 cameraPos, Vec3 anchor) {
        if (mc == null || mc.level == null || cameraPos == null || anchor == null) {
            return false;
        }
        double labelDistanceSq = cameraPos.distanceToSqr(anchor);
        if (labelDistanceSq < 0.25D) {
            return false;
        }

        BlockHitResult hit = mc.level.clip(new ClipContext(
                cameraPos,
                anchor,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        double hitDistanceSq = hit.getLocation().distanceToSqr(cameraPos);
        return hitDistanceSq > 0.04D && hitDistanceSq < labelDistanceSq - 0.04D;
    }

    private static void drawDeliveryPalletLabel(PoseStack pose,
                                                MultiBufferSource.BufferSource buffers,
                                                Minecraft mc,
                                                Vec3 cameraPos,
                                                BlockPos masterPos,
                                                String shopName,
                                                int bgAlpha,
                                                int packedLight) {
        Font font = mc.font;
        String safeShop = shopName == null || shopName.isBlank() ? "Shop" : shopName.trim();
        String header = "Delivery Pallet";
        String shop = safeShop;
        String coords = "(" + masterPos.getX() + ", " + masterPos.getY() + ", " + masterPos.getZ() + ")";

        pose.pushPose();
        pose.translate(
                masterPos.getX() + 0.5D - cameraPos.x,
                masterPos.getY() + computeDeliveryPalletLabelYOffset(mc, masterPos) - cameraPos.y,
                masterPos.getZ() + 0.5D - cameraPos.z
        );
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-DELIVERY_PALLET_LABEL_SCALE, -DELIVERY_PALLET_LABEL_SCALE, DELIVERY_PALLET_LABEL_SCALE);
        Matrix4f matrix = pose.last().pose();

        drawWorldLabelLine(font, header, 0.0F, 0xFF74D78A, matrix, buffers, bgAlpha, packedLight);
        drawWorldLabelLine(font, shop, 10.0F, 0xFFEAF6FF, matrix, buffers, bgAlpha, packedLight);
        drawWorldLabelLine(font, coords, 20.0F, 0xFFCFE0F2, matrix, buffers, bgAlpha, packedLight);
        pose.popPose();
    }

    private static void drawWorldLabelLine(Font font, String line, float y, int color, Matrix4f matrix, MultiBufferSource.BufferSource buffers, int bgAlpha, int packedLight) {
        float x = -font.width(line) / 2.0F;
        font.drawInBatch(line, x, y, color, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    private static boolean isShoppingBagDrop(ItemEntity itemEntity) {
        if (itemEntity == null || !itemEntity.isAlive()) {
            return false;
        }
        ItemStack stack = itemEntity.getItem();
        return stack != null && !stack.isEmpty() && stack.is(ModBlocks.SHOPPING_BAG.get().asItem());
    }

    private static long computeBagRemainingTicks(ItemEntity itemEntity, long nowTick) {
        if (itemEntity == null) {
            return 0L;
        }
        ItemStack stack = itemEntity.getItem();
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        if (root != null && root.contains(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY)) {
            long expiresAt = Math.max(0L, root.getLong(ShoppingBagDataKeys.BAG_DROP_EXPIRES_TICK_KEY));
            return Math.max(0L, expiresAt - nowTick);
        }
        // Fallback for manually dropped shopping bags: vanilla item despawn baseline.
        return Math.max(0L, 6000L - itemEntity.tickCount);
    }

    private static String resolveBagStoreName(ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        String raw = (root != null && root.contains(ShoppingBagDataKeys.BAG_STORE_NAME_KEY))
                ? root.getString(ShoppingBagDataKeys.BAG_STORE_NAME_KEY)
                : "";
        if (raw == null || raw.isBlank()) {
            return "Shop";
        }
        return raw.trim();
    }

    private static String resolveBagOwnerName(Minecraft mc, ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        if (root != null && root.contains(ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY)) {
            String raw = root.getString(ShoppingBagDataKeys.BAG_DROP_OWNER_NAME_KEY);
            if (raw != null && !raw.isBlank()) {
                return raw.trim();
            }
        }

        if (root != null && root.hasUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY) && mc.level != null) {
            UUID ownerId = root.getUUID(ShoppingBagDataKeys.BAG_DROP_OWNER_KEY);
            for (Player player : mc.level.players()) {
                if (player != null && ownerId.equals(player.getUUID())) {
                    String resolved = player.getName().getString();
                    if (resolved != null && !resolved.isBlank()) {
                        return resolved.trim();
                    }
                }
            }
        }
        return "Unknown";
    }

    private static List<BagTimerCandidate> selectBagTimerWinners(List<BagTimerCandidate> candidates) {
        List<BagTimerCandidate> winners = new ArrayList<>();
        boolean[] assigned = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            if (assigned[i]) {
                continue;
            }
            List<Integer> cluster = new ArrayList<>();
            cluster.add(i);
            assigned[i] = true;

            // Group tightly packed bags so only one timer renders for readability.
            for (int cursor = 0; cursor < cluster.size(); cursor++) {
                int sourceIndex = cluster.get(cursor);
                BagTimerCandidate source = candidates.get(sourceIndex);
                for (int j = 0; j < candidates.size(); j++) {
                    if (assigned[j]) {
                        continue;
                    }
                    BagTimerCandidate candidate = candidates.get(j);
                    if (source.entity().distanceToSqr(candidate.entity()) <= BAG_TIMER_CLUSTER_RANGE_SQ) {
                        assigned[j] = true;
                        cluster.add(j);
                    }
                }
            }

            int winnerIndex = cluster.get(0);
            for (int index : cluster) {
                if (candidates.get(index).remainingTicks() < candidates.get(winnerIndex).remainingTicks()) {
                    winnerIndex = index;
                }
            }
            winners.add(candidates.get(winnerIndex));
        }
        return winners;
    }

    private static void drawBagTimerLabel(PoseStack pose,
                                          MultiBufferSource.BufferSource buffers,
                                          Minecraft mc,
                                          Vec3 cameraPos,
                                          BagTimerCandidate candidate,
                                          int bgAlpha,
                                          int packedLight) {
        Font font = mc.font;
        String header = "[" + candidate.storeName() + "] Bag";
        String owner = "Owner: " + candidate.ownerName();
        String timer = "despawns in " + formatBagCountdown(candidate.remainingTicks());

        pose.pushPose();
        pose.translate(
                candidate.anchor().x - cameraPos.x,
                candidate.anchor().y - cameraPos.y,
                candidate.anchor().z - cameraPos.z
        );
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-BAG_TIMER_LABEL_SCALE, -BAG_TIMER_LABEL_SCALE, BAG_TIMER_LABEL_SCALE);
        Matrix4f matrix = pose.last().pose();

        float headerX = -font.width(header) / 2.0F;
        float ownerX = -font.width(owner) / 2.0F;
        float timerX = -font.width(timer) / 2.0F;
        font.drawInBatch(header, headerX, 0.0F, 0xFF79E7FF, false, matrix, buffers, Font.DisplayMode.NORMAL, bgAlpha, packedLight);
        font.drawInBatch(owner, ownerX, 10.0F, 0xFFCDEBFF, false, matrix, buffers, Font.DisplayMode.NORMAL, bgAlpha, packedLight);
        font.drawInBatch(timer, timerX, 20.0F, 0xFFFFD86A, false, matrix, buffers, Font.DisplayMode.NORMAL, bgAlpha, packedLight);
        pose.popPose();
    }

    private static String formatBagCountdown(long ticksRemaining) {
        long totalTenths = Math.max(0L, (ticksRemaining + 1L) / 2L);
        long minutes = totalTenths / 600L;
        long seconds = (totalTenths / 10L) % 60L;
        long tenths = totalTenths % 10L;
        return String.format(Locale.ROOT, "%d:%02d.%d", minutes, seconds, tenths);
    }

    private record BagTimerCandidate(ItemEntity entity, Vec3 anchor, long remainingTicks, String storeName, String ownerName) {
    }

    @SubscribeEvent
    static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (ClaimModeClientState.active()) {
            event.setCanceled(true);
            return;
        }
        BlockState state = mc.level.getBlockState(event.getTarget().getBlockPos());
        if (!state.is(ModBlocks.GLASS_COUNTER_DISPLAY.get())
                && !state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get())
                && !state.is(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get())
                && !state.is(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get())
                && !state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                && !state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get())) {
            return;
        }

        event.setCanceled(true);
        Vec3 cam = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        BlockPos hitPos = event.getTarget().getBlockPos();
        VoxelShape shape = state.getShape(mc.level, hitPos);

        if ((state.is(ModBlocks.MODULAR_WALL_DISPLAY.get())
                || state.is(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get()))
                && state.hasProperty(ModularWallDisplayBlock.PART)
                && state.hasProperty(ModularWallDisplayBlock.FACING)) {
            BlockPos masterPos = ModularWallDisplayBlock.getMasterPos(state, hitPos);
            Direction extensionDirection = mc.level.getBlockState(masterPos).hasProperty(ModularWallDisplayBlock.FACING)
                    ? mc.level.getBlockState(masterPos).getValue(ModularWallDisplayBlock.FACING).getCounterClockWise()
                    : state.getValue(ModularWallDisplayBlock.FACING).getCounterClockWise();
            BlockPos extensionPos = masterPos.relative(extensionDirection);
            BlockState masterState = mc.level.getBlockState(masterPos);
            BlockState extensionState = mc.level.getBlockState(extensionPos);

            drawOutlineShape(pose, lines, cam, masterPos, masterState.getShape(mc.level, masterPos));
            drawOutlineShape(pose, lines, cam, extensionPos, extensionState.getShape(mc.level, extensionPos));
            return;
        }

        if (shape.isEmpty()) {
            LevelRenderer.renderLineBox(pose, lines,
                    hitPos.getX() - cam.x,
                    hitPos.getY() - cam.y,
                    hitPos.getZ() - cam.z,
                    hitPos.getX() + 1.0D - cam.x,
                    hitPos.getY() + 1.0D - cam.y,
                    hitPos.getZ() + 1.0D - cam.z,
                    0.0F, 0.0F, 0.0F, 1.0F);
            return;
        }

        drawOutlineShape(pose, lines, cam, hitPos, shape);
    }

    private static void drawOutlineShape(PoseStack pose,
                                         VertexConsumer lines,
                                         Vec3 camera,
                                         BlockPos origin,
                                         VoxelShape shape) {
        if (shape == null || shape.isEmpty() || origin == null || camera == null) {
            return;
        }
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> LevelRenderer.renderLineBox(
                pose,
                lines,
                origin.getX() + minX - camera.x,
                origin.getY() + minY - camera.y,
                origin.getZ() + minZ - camera.z,
                origin.getX() + maxX - camera.x,
                origin.getY() + maxY - camera.y,
                origin.getZ() + maxZ - camera.z,
                0.0F, 0.0F, 0.0F, 1.0F
        ));
    }

    private static void drawStockroomGuideLine(PoseStack pose,
                                               VertexConsumer lines,
                                               Vec3 from,
                                               Vec3 to) {
        PoseStack.Pose last = pose.last();
        Vec3 span = to.subtract(from);
        double distance = span.length();
        if (distance < 0.0001D) {
            return;
        }
        Vec3 normal = span.scale(1.0D / distance);
        double arcHeight = Math.min(STOCKROOM_LINE_MAX_ARC, Math.max(0.08D, distance * 0.04D));

        for (int i = 0; i < STOCKROOM_LINE_SEGMENTS; i++) {
            double t0 = (double) i / (double) STOCKROOM_LINE_SEGMENTS;
            double t1 = (double) (i + 1) / (double) STOCKROOM_LINE_SEGMENTS;
            Vec3 p0 = curvedPoint(from, to, t0, arcHeight);
            Vec3 p1 = curvedPoint(from, to, t1, arcHeight);

            int a0 = (int) (180 + (70 * t0));
            int a1 = (int) (180 + (70 * t1));
            lineVertex(last, lines, p0, 108, 236, 255, a0, normal);
            lineVertex(last, lines, p1, 108, 236, 255, a1, normal);
        }
    }

    private static void drawSafeBoxDirectionalLine(PoseStack pose,
                                                   VertexConsumer lines,
                                                   Vec3 from,
                                                   Vec3 to) {
        Vec3 span = to.subtract(from);
        double distance = span.length();
        if (distance < 0.0001D) {
            return;
        }

        PoseStack.Pose last = pose.last();
        Vec3 normal = span.scale(1.0D / distance);
        lineVertex(last, lines, from, 184, 102, 255, 220, normal);
        lineVertex(last, lines, to, 184, 102, 255, 255, normal);

        Vec3 arrowBase = to.subtract(normal.scale(Math.min(0.34D, distance * 0.24D)));
        Vec3 referenceAxis = Math.abs(normal.y) < 0.90D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 arrowSide = normal.cross(referenceAxis).normalize()
                .scale(Math.min(0.15D, distance * 0.10D));
        lineVertex(last, lines, arrowBase.add(arrowSide), 184, 102, 255, 255, normal);
        lineVertex(last, lines, to, 184, 102, 255, 255, normal);
        lineVertex(last, lines, arrowBase.subtract(arrowSide), 184, 102, 255, 255, normal);
        lineVertex(last, lines, to, 184, 102, 255, 255, normal);
    }

    private static Vec3 curvedPoint(Vec3 from, Vec3 to, double t, double arcHeight) {
        Vec3 base = from.lerp(to, t);
        double arc = Math.sin(Math.PI * t) * arcHeight;
        return base.add(0.0D, arc, 0.0D);
    }

    private static void lineVertex(PoseStack.Pose pose,
                                   VertexConsumer lines,
                                   Vec3 point,
                                   int r,
                                   int g,
                                   int b,
                                   int a,
                                   Vec3 normal) {
        lines.addVertex(pose, (float) point.x, (float) point.y, (float) point.z)
                .setColor(r, g, b, a)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static String normalizeDimensionId(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("minecraft:")
                ? normalized.substring("minecraft:".length())
                : normalized;
    }

    private static LocateRenderTarget resolveLocateRenderTarget(Minecraft mc) {
        int baseX = StockroomLocateClientState.getX();
        int baseY = StockroomLocateClientState.getY();
        int baseZ = StockroomLocateClientState.getZ();
        int slot = StockroomLocateClientState.getSlot();

        if (mc.level != null && slot > PALLET_LOCATE_SLOT_BASE && slot <= PALLET_LOCATE_SLOT_BASE + PALLET_LOCATE_SLOT_COUNT) {
            BlockPos master = new BlockPos(baseX, baseY, baseZ);
            BlockState state = mc.level.getBlockState(master);
            if (state.is(ModBlocks.PALLET.get())) {
                int encoded = slot - PALLET_LOCATE_SLOT_BASE - 1;
                int column = encoded % 9;
                int layer = encoded / 9;
                int colX = column % 3;
                int colZ = column / 3;
                int worldX = master.getX() + (colX - 1);
                int worldZ = master.getZ() + (colZ - 1);
                double boxBottomY = master.getY() + PALLET_BOX_BASE_Y + (PALLET_BOX_STEP_Y * layer);
                double centerX = worldX + 0.5D;
                double centerY = boxBottomY + (PALLET_BOX_HEIGHT * 0.5D);
                double centerZ = worldZ + 0.5D;
                AABB box = new AABB(
                        worldX + 0.2D,
                        boxBottomY + 0.02D,
                        worldZ + 0.2D,
                        worldX + 0.8D,
                        boxBottomY + PALLET_BOX_HEIGHT - 0.02D,
                        worldZ + 0.8D
                );
                return new LocateRenderTarget(new Vec3(centerX, centerY, centerZ), box, true);
            }
        }

        AABB fallback = new AABB(
                baseX + 0.2D,
                baseY + 0.1D,
                baseZ + 0.2D,
                baseX + 0.8D,
                baseY + 0.9D,
                baseZ + 0.8D
        );
        return new LocateRenderTarget(new Vec3(baseX + 0.5D, baseY + 0.7D, baseZ + 0.5D), fallback, false);
    }

    private record LocateRenderTarget(Vec3 anchor, AABB box, boolean palletBox) {
    }

    private static void renderShopSetupObjective(Minecraft mc, GuiGraphics graphics) {
        if (mc.screen != null || !ShopSetupObjectiveClientState.isActive()) {
            return;
        }
        Font font = mc.font;
        if (font == null) {
            return;
        }

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        int lineHeight = font.lineHeight;
        int panelWidth = Math.max(220, Math.min(300, guiWidth / 3));
        int panelX = guiWidth - panelWidth - 8;

        if (ShopSetupObjectiveClientState.isCollapsed()) {
            String title = tr(ShopSetupObjectiveClientState.getProjectType() + " Requirements");
            String stepText = tr("Step") + " " + ShopSetupObjectiveClientState.getStep()
                    + " / " + ShopSetupObjectiveClientState.getTotalSteps();
            String objectiveTitle = tr(ShopSetupObjectiveClientState.getObjectiveTitle());
            String expandHint = ShopSetupObjectiveClientState.getProjectCount() > 1
                    ? tr("Press X to expand | ] to switch")
                    : tr("Press X to expand");
            int compactWidth = Math.max(176, Math.min(236, panelWidth));
            int compactHeight = 42;
            int compactX = guiWidth - compactWidth - 8;
            int compactY = guiHeight - compactHeight - 8;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0D, 0.0D, 1250.0D);
            graphics.fill(compactX, compactY, compactX + compactWidth, compactY + compactHeight, 0xD0132438);
            graphics.fill(compactX, compactY, compactX + compactWidth, compactY + 2, 0xFF59B7FF);
            int textX = compactX + 6;
            int textWidth = compactWidth - 12;
            graphics.drawString(font, fitToWidth(font, title, textWidth), textX, compactY + 5, 0xFFE8F7FF, false);
            graphics.drawString(font, fitToWidth(font, stepText + " - " + objectiveTitle, textWidth), textX, compactY + 16, 0xFFFFE39A, false);
            graphics.drawString(font, fitToWidth(font, expandHint, textWidth), textX, compactY + 29, 0xFF8CA8C5, false);
            graphics.pose().popPose();
            return;
        }

        String projectName = ShopSetupObjectiveClientState.getShopName();
        String projectType = ShopSetupObjectiveClientState.getProjectType();
        String title = tr(projectType + " Requirements");
        String subtitle = projectName.isBlank()
                ? tr("Setup in progress")
                : tr(projectType + ": ") + projectName;
        String stepText = tr("Step") + " " + ShopSetupObjectiveClientState.getStep()
                + " / " + ShopSetupObjectiveClientState.getTotalSteps();
        String objectiveTitle = tr(ShopSetupObjectiveClientState.getObjectiveTitle());
        String objectiveDetail = tr(ShopSetupObjectiveClientState.getObjectiveDetail());
        List<ShopSetupObjectivePayload.RequirementProgress> requirements = ShopSetupObjectiveClientState.getRequirements();
        String closeHint = ShopSetupObjectiveClientState.getProjectCount() > 1
                ? tr("Press X to minimize | ] switch | Shift+X close")
                : tr("Press X to minimize | Shift+X to close");

        List<FormattedCharSequence> detailLines = font.split(Component.literal(objectiveDetail), panelWidth - 14);
        if (detailLines.size() > 6) {
            detailLines = new ArrayList<>(detailLines.subList(0, 6));
        }
        int bodyHeight = (detailLines.isEmpty() ? 0 : (detailLines.size() * lineHeight) + 2);
        int requirementHeight = requirements.isEmpty() ? 0 : (requirements.size() * lineHeight) + 3;
        int panelHeight = 58 + bodyHeight + requirementHeight + lineHeight + 4;
        int panelY = Math.max(40, (guiHeight / 2) - (panelHeight / 2));

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1250.0D);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0132438);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF59B7FF);

        int textX = panelX + 6;
        int cursorY = panelY + 6;
        graphics.drawString(font, fitToWidth(font, title, panelWidth - 12), textX, cursorY, 0xFFE8F7FF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, subtitle, panelWidth - 12), textX, cursorY, 0xFFCBE7FF, false);
        cursorY += lineHeight + 1;
        graphics.drawString(font, fitToWidth(font, stepText, panelWidth - 12), textX, cursorY, 0xFF9DEB9E, false);
        cursorY += lineHeight + 2;
        graphics.drawString(font, fitToWidth(font, objectiveTitle, panelWidth - 12), textX, cursorY, 0xFFFFE39A, false);
        cursorY += lineHeight;
        for (FormattedCharSequence seq : detailLines) {
            graphics.drawString(font, seq, textX, cursorY, 0xFFE9F1FC, false);
            cursorY += lineHeight;
        }
        if (!requirements.isEmpty()) {
            cursorY += 2;
            for (ShopSetupObjectivePayload.RequirementProgress requirement : requirements) {
                if (requirement == null) {
                    continue;
                }
                String itemName = requirement.itemName() == null || requirement.itemName().isBlank()
                        ? tr("items")
                        : tr(requirement.itemName());
                String line = "- " + requirement.current() + " / " + requirement.needed() + " " + itemName;
                int color = requirement.complete() ? 0xFF9DEB9E : 0xFFFFE39A;
                graphics.drawString(font, fitToWidth(font, line, panelWidth - 12), textX, cursorY, color, false);
                cursorY += lineHeight;
            }
        }
        cursorY += 2;
        graphics.drawString(font, fitToWidth(font, closeHint, panelWidth - 12), textX, cursorY, 0xFF8CA8C5, false);
        graphics.pose().popPose();
    }

    private static void renderDeliveryInfoBoard(Minecraft mc, GuiGraphics graphics) {
        if (!DeliveryInfoBoardClientState.isActive()) {
            return;
        }
        Font font = mc.font;
        if (font == null) {
            return;
        }

        int guiWidth = graphics.guiWidth();
        int panelWidth = Math.max(238, Math.min(314, guiWidth / 3));
        int panelX = guiWidth - panelWidth - 8;
        int panelY = 26;
        int textWidth = panelWidth - 12;
        int lineHeight = font.lineHeight;

        String title = tr("Delivery Board");
        String subtitle = tr("Active Orders: ")
                + DeliveryInfoBoardClientState.getActiveOrders()
                + " / " + DeliveryInfoBoardClientState.getActiveCap();
        if (DeliveryInfoBoardClientState.isCollapsed()) {
            int collapsedW = Math.max(150, Math.min(228, guiWidth / 4));
            int collapsedX = guiWidth - collapsedW - 8;
            int collapsedY = panelY;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0D, 0.0D, 1240.0D);
            graphics.fill(collapsedX, collapsedY, collapsedX + collapsedW, collapsedY + 26, 0xD0112740);
            graphics.fill(collapsedX, collapsedY, collapsedX + collapsedW, collapsedY + 2, 0xFF58B8FF);
            graphics.drawString(font, fitToWidth(font, title, collapsedW - 12), collapsedX + 6, collapsedY + 5, 0xFFEAF7FF, false);
            graphics.drawString(font, fitToWidth(font, subtitle, collapsedW - 12), collapsedX + 6, collapsedY + 15, 0xFFCBE7FF, false);
            graphics.pose().popPose();
            return;
        }
        String shopLine = tr("Shop: ") + safeDeliveryField(DeliveryInfoBoardClientState.getShopName(), tr("Unknown"));
        String itemLine = tr("Item: ") + safeDeliveryField(DeliveryInfoBoardClientState.getItemName(), tr("Unknown"))
                + " x" + Math.max(1, DeliveryInfoBoardClientState.getQuantity());
        String rewardLine = tr("Reward: ") + MoneyText.abbreviateWithDollar(
                BigDecimal.valueOf(Math.max(0L, DeliveryInfoBoardClientState.getRewardCents()), 2)
        );
        String timeLine = tr("Time Left: ") + formatCountdown(DeliveryInfoBoardClientState.getRemainingSeconds());
        String distanceLine = tr("Distance: ") + safeDeliveryField(DeliveryInfoBoardClientState.getDistanceLabel(), tr("Unknown"));
        String rankLine = tr("Rank: ") + safeDeliveryField(DeliveryInfoBoardClientState.getRankLabel(), "-")
                + " | " + tr("Streak: ") + Math.max(0L, DeliveryInfoBoardClientState.getStreak());
        String successLine = tr("Success: ") + DeliveryInfoBoardClientState.getSuccessRatePct()
                + "% | " + tr("Completed: ") + Math.max(0L, DeliveryInfoBoardClientState.getCompletedOrders());
        String payoutLine = tr("Lifetime Payout: ") + MoneyText.abbreviateWithDollar(
                BigDecimal.valueOf(Math.max(0L, DeliveryInfoBoardClientState.getTotalPayoutCents()), 2)
        );

        List<FormattedCharSequence> dropTargetLines = font.split(
                Component.literal(tr("Drop Target: ") + safeDeliveryField(DeliveryInfoBoardClientState.getDropTarget(), tr("Any delivery pallet"))),
                textWidth
        );
        if (dropTargetLines.size() > 3) {
            dropTargetLines = new ArrayList<>(dropTargetLines.subList(0, 3));
        }

        int panelHeight = 70
                + (lineHeight * 7)
                + (dropTargetLines.size() * lineHeight)
                + 12;

        long timeoutSeconds = Math.max(1L, (long) DeliveryInfoBoardClientState.getTimeoutMinutes() * 60L);
        float remainingRatio = Math.max(0.0F, Math.min(1.0F,
                DeliveryInfoBoardClientState.getRemainingSeconds() / (float) timeoutSeconds));
        int timerLeft;
        int timerRight;
        if (remainingRatio <= 0.20F) {
            timerLeft = 0xFFEA6C6C;
            timerRight = 0xFFF0984F;
        } else if (remainingRatio <= 0.50F) {
            timerLeft = 0xFFE7B55D;
            timerRight = 0xFFF0D071;
        } else {
            timerLeft = 0xFF60D995;
            timerRight = 0xFF58C9FF;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1240.0D);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xD0112740);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFF58B8FF);

        int textX = panelX + 6;
        int cursorY = panelY + 6;
        graphics.drawString(font, fitToWidth(font, title, textWidth), textX, cursorY, 0xFFEAF7FF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, subtitle, textWidth), textX, cursorY, 0xFFCBE7FF, false);
        cursorY += lineHeight + 2;
        graphics.drawString(font, fitToWidth(font, shopLine, textWidth), textX, cursorY, 0xFFAEE2FF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, itemLine, textWidth), textX, cursorY, 0xFFFFFFFF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, rewardLine, textWidth), textX, cursorY, 0xFFF7DB8A, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, timeLine, textWidth), textX, cursorY, 0xFFD9F0FF, false);
        cursorY += lineHeight;
        for (FormattedCharSequence line : dropTargetLines) {
            graphics.drawString(font, line, textX, cursorY, 0xFFE7F2FF, false);
            cursorY += lineHeight;
        }
        graphics.drawString(font, fitToWidth(font, distanceLine, textWidth), textX, cursorY, 0xFFBFE2FF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, rankLine, textWidth), textX, cursorY, 0xFFA6E39D, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, successLine, textWidth), textX, cursorY, 0xFFD0E8FF, false);
        cursorY += lineHeight;
        graphics.drawString(font, fitToWidth(font, payoutLine, textWidth), textX, cursorY, 0xFF9FEACF, false);
        cursorY += lineHeight + 4;

        int barLeft = textX;
        int barRight = panelX + panelWidth - 6;
        int barTop = cursorY;
        int barBottom = barTop + 4;
        graphics.fill(barLeft, barTop, barRight, barBottom, 0x80111C2A);
        int barWidth = Math.max(0, barRight - barLeft);
        int fillWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * remainingRatio)));
        if (fillWidth > 0) {
            graphics.fillGradient(barLeft, barTop, barLeft + fillWidth, barBottom, timerLeft, timerRight);
        }
        graphics.pose().popPose();
    }

    private static String fitToWidth(Font font, String text, int maxWidth) {
        if (font == null) {
            return text == null ? "" : text;
        }
        String raw = text == null ? "" : text;
        if (raw.isBlank()) {
            return "";
        }
        if (font.width(raw) <= maxWidth) {
            return raw;
        }
        String ellipsis = "...";
        int target = Math.max(8, maxWidth - font.width(ellipsis));
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (font.width(out.toString() + ch) > target) {
                break;
            }
            out.append(ch);
        }
        return out + ellipsis;
    }

    private static String tr(String text) {
        return UbsClientTranslations.resolve(text == null ? "" : text);
    }

    private static void renderBalanceHud(Minecraft mc, GuiGraphics graphics) {
        BalanceHudRenderer.render(mc, graphics);
    }

    private static void renderActionAlert(Minecraft mc, GuiGraphics graphics) {
        if (!ActionAlertClientState.isActive()) {
            return;
        }

        long now = System.currentTimeMillis();
        long shownAt = ActionAlertClientState.getShownAtMillis();
        long expiresAt = ActionAlertClientState.getExpiresAtMillis();
        if (expiresAt <= shownAt) {
            return;
        }

        float fadeIn = Math.min(1.0F, Math.max(0.0F, (now - shownAt) / (float) DELIVERY_ALERT_FADE_MS));
        float fadeOut = Math.min(1.0F, Math.max(0.0F, (expiresAt - now) / (float) DELIVERY_ALERT_FADE_MS));
        float alphaFactor = Math.min(fadeIn, fadeOut);
        if (alphaFactor <= 0.01F) {
            return;
        }

        String title = ActionAlertClientState.getTitle();
        if (title.isBlank()) {
            title = switch (ActionAlertClientState.getTone()) {
                case SUCCESS -> tr("Success");
                case ERROR -> tr("Action Failed");
                case WARNING -> tr("Warning");
                case INFO -> tr("Info");
            };
        } else {
            title = tr(title);
        }
        String message = tr(ActionAlertClientState.getMessage());
        if (message.isBlank()) {
            return;
        }
        long totalDurationMs = Math.max(1L, expiresAt - shownAt);
        long remainingDurationMs = Math.max(0L, expiresAt - now);
        float remainingProgress = Math.max(0.0F, Math.min(1.0F, remainingDurationMs / (float) totalDurationMs));
        String remainingText = String.format(Locale.ROOT, "%.1fs", remainingDurationMs / 1000.0F);

        int padding = 8;
        int lineGap = 2;
        int progressGap = 4;
        int progressHeight = 4;
        int lineHeight = mc.font.lineHeight;
        int maxWidth = Math.max(240, Math.min(graphics.guiWidth() - 24, (int) (graphics.guiWidth() * 0.78F)));
        int preferredWidth = Math.max(mc.font.width(title), mc.font.width(message)) + (padding * 2);
        int width = Math.max(240, Math.min(maxWidth, preferredWidth));

        List<FormattedCharSequence> wrapped = new ArrayList<>(mc.font.split(Component.literal(message), width - (padding * 2)));
        if (wrapped.isEmpty()) {
            return;
        }
        // Keep alerts concise and non-intrusive while still showing enough context.
        if (wrapped.size() > 4) {
            wrapped = new ArrayList<>(wrapped.subList(0, 4));
        }
        int textBlockHeight = wrapped.size() * lineHeight;
        int height = lineHeight + textBlockHeight + (padding * 2) + lineGap + progressGap + progressHeight;
        int x = (graphics.guiWidth() - width) / 2;
        int y = 8;

        ActionAlertClientState.Tone tone = ActionAlertClientState.getTone();
        int borderColor;
        int topBarLeft;
        int topBarRight;
        int panelLeft;
        int panelRight;
        switch (tone) {
            case SUCCESS -> {
                borderColor = withScaledAlpha(0xFF6EEBA7, alphaFactor);
                topBarLeft = withScaledAlpha(0xFF39C985, alphaFactor);
                topBarRight = withScaledAlpha(0xFF59D4FF, alphaFactor);
                panelLeft = withScaledAlpha(0xDC14332D, alphaFactor);
                panelRight = withScaledAlpha(0xDC102B40, alphaFactor);
            }
            case ERROR -> {
                borderColor = withScaledAlpha(0xFFFF8E8E, alphaFactor);
                topBarLeft = withScaledAlpha(0xFFE35E5E, alphaFactor);
                topBarRight = withScaledAlpha(0xFFFF9A6E, alphaFactor);
                panelLeft = withScaledAlpha(0xDC311A1A, alphaFactor);
                panelRight = withScaledAlpha(0xDC3A1E12, alphaFactor);
            }
            case WARNING -> {
                borderColor = withScaledAlpha(0xFFF3C66E, alphaFactor);
                topBarLeft = withScaledAlpha(0xFFE1A44A, alphaFactor);
                topBarRight = withScaledAlpha(0xFFFFD66D, alphaFactor);
                panelLeft = withScaledAlpha(0xDC322718, alphaFactor);
                panelRight = withScaledAlpha(0xDC3C2D12, alphaFactor);
            }
            case INFO -> {
                borderColor = withScaledAlpha(0xFF8FD2FF, alphaFactor);
                topBarLeft = withScaledAlpha(0xFF53A9FF, alphaFactor);
                topBarRight = withScaledAlpha(0xFF7CD9FF, alphaFactor);
                panelLeft = withScaledAlpha(0xDC152E46, alphaFactor);
                panelRight = withScaledAlpha(0xDC102640, alphaFactor);
            }
            default -> {
                borderColor = withScaledAlpha(0xFF8FD2FF, alphaFactor);
                topBarLeft = withScaledAlpha(0xFF53A9FF, alphaFactor);
                topBarRight = withScaledAlpha(0xFF7CD9FF, alphaFactor);
                panelLeft = withScaledAlpha(0xDC152E46, alphaFactor);
                panelRight = withScaledAlpha(0xDC102640, alphaFactor);
            }
        }
        int titleColor = withScaledAlpha(0xFFE8F8FF, alphaFactor);
        int textColor = withScaledAlpha(0xFFFFFFFF, alphaFactor);
        int timerColor = withScaledAlpha(0xFFD6ECFF, alphaFactor);
        int progressTrackColor = withScaledAlpha(0xA3141F2A, alphaFactor);

        // Draw as a shared floating alert card for all client feedback states.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1300.0D);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, borderColor);
        graphics.fillGradient(x, y, x + width, y + height, panelLeft, panelRight);
        graphics.fillGradient(x, y, x + width, y + 3, topBarLeft, topBarRight);
        graphics.drawString(mc.font, title, x + padding, y + padding - 1, titleColor, false);
        graphics.drawString(mc.font, remainingText,
                x + width - padding - mc.font.width(remainingText),
                y + padding - 1,
                timerColor,
                false);
        int textY = y + padding + lineHeight + lineGap - 1;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(mc.font, line, x + padding, textY, textColor, false);
            textY += lineHeight;
        }
        int barLeft = x + padding;
        int barRight = x + width - padding;
        int barTop = y + padding + lineHeight + lineGap + textBlockHeight + progressGap;
        int barBottom = barTop + progressHeight;
        graphics.fill(barLeft, barTop, barRight, barBottom, progressTrackColor);
        int barWidth = Math.max(0, barRight - barLeft);
        int fillWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * remainingProgress)));
        if (fillWidth > 0) {
            graphics.fillGradient(barLeft, barTop, barLeft + fillWidth, barBottom, topBarLeft, topBarRight);
        }
        graphics.pose().popPose();
    }

    private static void renderPhoneNotification(Minecraft mc, GuiGraphics graphics) {
        if (SmartphoneClientState.isInteractive() || !PhoneNotificationClientState.isActive()) {
            return;
        }
        String message = PhoneNotificationClientState.message();
        if (message.isBlank()) {
            return;
        }

        float progress = PhoneNotificationClientState.progress();
        float alpha = PhoneNotificationClientState.alpha();
        if (progress <= 0.01F || alpha <= 0.01F) {
            return;
        }

        int maxWidth = Math.max(238, Math.min(360, graphics.guiWidth() - 28));
        int preferredWidth = Math.max(238, Math.min(maxWidth,
                Math.max(mc.font.width(PhoneNotificationClientState.title()), mc.font.width(message)) + 76));
        int width = preferredWidth;
        List<FormattedCharSequence> wrapped = new ArrayList<>(
                mc.font.split(Component.literal(message), width - 76));
        if (wrapped.isEmpty()) {
            return;
        }
        if (wrapped.size() > 2) {
            wrapped = new ArrayList<>(wrapped.subList(0, 2));
        }

        int height = 48 + (wrapped.size() - 1) * mc.font.lineHeight;
        int x = (graphics.guiWidth() - width) / 2;
        int targetY = 18;
        int y = Math.round(targetY - (height + 28) * (1.0F - progress));
        int radius = 14;
        int bg = withScaledAlpha(0xF8F7F8FA, alpha);
        int border = withScaledAlpha(0x55FFFFFF, alpha);
        int shadow = withScaledAlpha(0x52000000, alpha * 0.55F);
        int text = withScaledAlpha(0xFF17191F, alpha);
        int muted = withScaledAlpha(0xFF69707A, alpha);
        int accent = withScaledAlpha(phoneNotificationAccent(PhoneNotificationClientState.tone()), alpha);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1400.0D);
        fillRoundedRect(graphics, x, y + 3, width, height, radius, shadow);
        fillRoundedRect(graphics, x, y, width, height, radius, bg);
        drawRoundedRectBorder(graphics, x, y, width, height, radius, border);

        int icon = 30;
        int iconX = x + 10;
        int iconY = y + (height - icon) / 2;
        fillRoundedRect(graphics, iconX, iconY, icon, icon, 8, accent);
        graphics.drawString(mc.font, "$", iconX + (icon - mc.font.width("$")) / 2, iconY + 10,
                withScaledAlpha(0xFFFFFFFF, alpha), false);

        String title = PhoneNotificationClientState.title();
        graphics.drawString(mc.font, title.isBlank() ? "UBS Phone" : title,
                x + 50, y + 9, text, false);
        int lineY = y + 24;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(mc.font, line, x + 50, lineY, muted, false);
            lineY += mc.font.lineHeight;
        }
        graphics.drawString(mc.font, "now", x + width - 10 - mc.font.width("now"), y + 9,
                muted, false);
        graphics.pose().popPose();
    }

    private static int phoneNotificationAccent(ActionAlertClientState.Tone tone) {
        return switch (tone == null ? ActionAlertClientState.Tone.INFO : tone) {
            case SUCCESS -> 0xFF20C985;
            case ERROR -> 0xFFFF3B30;
            case WARNING -> 0xFFFFB800;
            case INFO -> 0xFF0078FF;
        };
    }

    private static void drawRoundedRectBorder(GuiGraphics graphics, int x, int y, int w, int h, int r, int color) {
        if (((color >>> 24) & 0xFF) == 0 || w <= 2 || h <= 2) {
            return;
        }
        graphics.fill(x + r, y, x + w - r, y + 1, color);
        graphics.fill(x + r, y + h - 1, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + 1, y + h - r, color);
        graphics.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    private static void fillRoundedRect(GuiGraphics graphics, int x, int y, int w, int h, int r, int color) {
        if (((color >>> 24) & 0xFF) == 0 || w <= 0 || h <= 0) {
            return;
        }
        int radius = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (radius <= 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        graphics.fill(x + radius, y, x + w - radius, y + h, color);
        graphics.fill(x, y + radius, x + w, y + h - radius, color);
        graphics.fill(x + 2, y + radius / 2, x + radius, y + h - radius / 2, color);
        graphics.fill(x + w - radius, y + radius / 2, x + w - 2, y + h - radius / 2, color);
        graphics.fill(x + radius / 2, y + 2, x + w - radius / 2, y + radius, color);
        graphics.fill(x + radius / 2, y + h - radius, x + w - radius / 2, y + h - 2, color);
    }

    private static int withScaledAlpha(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaledAlpha = (int) Math.max(0.0F, Math.min(255.0F, baseAlpha * factor));
        return (scaledAlpha << 24) | (argb & 0x00FFFFFF);
    }

    private static String safeDeliveryField(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return text.trim();
    }

    private static String formatCountdown(long totalSecondsRaw) {
        long totalSeconds = Math.max(0L, totalSecondsRaw);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds);
    }

    private static void renderPickpocketOverlay(Minecraft mc, GuiGraphics graphics) {
        if (mc.level == null || mc.player == null || mc.screen != null) {
            return;
        }

        PickpocketTarget target = resolvePickpocketTarget(mc, PICKPOCKET_TARGET_RANGE_BLOCKS);
        boolean hasHoverTarget = target != null;
        boolean hasActiveAttempt = PickpocketClientState.isActive();
        if (!hasHoverTarget && !hasActiveAttempt) {
            return;
        }

        int centerX = graphics.guiWidth() / 2;
        int panelY = (graphics.guiHeight() / 2) + 18;
        int lineHeight = mc.font.lineHeight;
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 520.0D);

        if (hasHoverTarget) {
            String targetName = target.player().getName().getString();
            String line1 = "Target: " + targetName;
            int cooldownTicks = PickpocketClientState.getCooldownRemainingTicks();
            String line2 = cooldownTicks > 0
                    ? "Cooldown: " + formatSecondsFromTicks(cooldownTicks) + "s"
                    : "Hold Shift + " + PickpocketKeyMappings.getBoundKeyName() + " to pickpocket";
            int width = Math.max(mc.font.width(line1), mc.font.width(line2)) + 12;
            int x = centerX - (width / 2);
            int y = panelY;
            int height = (lineHeight * 2) + 9;

            graphics.fill(x, y, x + width, y + height, 0xCC1A2432);
            graphics.fill(x, y, x + width, y + 2, 0xFF60C6FF);
            graphics.drawString(mc.font, line1, x + 6, y + 4, 0xFFEAF7FF, false);
            graphics.drawString(mc.font, line2, x + 6, y + 4 + lineHeight,
                    cooldownTicks > 0 ? 0xFFF3C66E : 0xFFD6EBFF, false);
            panelY += height + 6;
        }

        if (hasActiveAttempt) {
            int durationTicks = Math.max(1, PickpocketClientState.getDurationTicks());
            int elapsedTicks = Math.max(0, PickpocketClientState.getElapsedTicks());
            float progress = Math.max(0.0F, Math.min(1.0F, elapsedTicks / (float) durationTicks));
            String targetName = PickpocketClientState.getTargetName().isBlank()
                    ? "target"
                    : PickpocketClientState.getTargetName();
            String label = "Stealing from " + targetName + "...";
            int barWidth = 164;
            int barHeight = 6;
            int x = centerX - (barWidth / 2);
            int y = panelY;

            graphics.drawString(mc.font, label, centerX - (mc.font.width(label) / 2), y, 0xFFF3F8FF, false);
            int barTop = y + lineHeight + 2;
            int barBottom = barTop + barHeight;
            graphics.fill(x, barTop, x + barWidth, barBottom, 0xAA11202F);
            int fillWidth = Math.max(0, Math.min(barWidth, Math.round(barWidth * progress)));
            if (fillWidth > 0) {
                graphics.fillGradient(x, barTop, x + fillWidth, barBottom, 0xFF3FB36E, 0xFF59D3B8);
            }
            String percent = String.format(Locale.ROOT, "%d%%", Math.round(progress * 100.0F));
            graphics.drawString(mc.font, percent, centerX - (mc.font.width(percent) / 2), barBottom + 2, 0xFFCBE7FF, false);
        }

        graphics.pose().popPose();
    }

    private static void renderHeistHud(Minecraft mc, GuiGraphics graphics) {
        var state = HeistClientState.hud();
        if (!state.active() || mc.screen != null) return;
        List<HeistClientState.HudCrewEntry> crew = HeistClientState.hudCrew();
        int width = Math.min(214, Math.max(176, graphics.guiWidth() / 5));
        int height = 74 + crew.size() * 23;
        int x = graphics.guiWidth() - width - 12;
        int y = Math.max(18, (graphics.guiHeight() - height) / 2);

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 610.0D);
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xDD344A60);
        graphics.fill(x, y, x + width, y + height, 0xE6111A24);
        graphics.fill(x, y, x + width, y + 3, state.alarmed() ? 0xFFFF4F5E : 0xFFFFB84D);
        graphics.drawString(mc.font, state.bankName(), x + 9, y + 10, 0xFFF5F7FA, false);
        String timer = formatCountdown(state.remainingTicks() / 20L);
        graphics.drawString(mc.font, timer, x + width - 9 - mc.font.width(timer), y + 10,
                state.remainingTicks() < 1200 ? 0xFFFF6E76 : 0xFFF5F7FA, false);
        String alarm = state.alarmed() ? "ALARM ACTIVE" : state.phase().replace('_', ' ');
        graphics.drawString(mc.font, alarm, x + 9, y + 23,
                state.alarmed() ? 0xFFFF6E76 : 0xFF8FA6BD, false);
        String loot = "Loot  $" + MoneyText.abbreviate(BigDecimal.valueOf(state.lootCents(), 2));
        graphics.drawString(mc.font, loot, x + 9, y + 37, 0xFFFFD166, false);
        String bag = "Duffel  " + state.bagSlots() + "/" + state.bagCapacity() + " slots";
        graphics.drawString(mc.font, bag, x + 9, y + 49, 0xFF9ED6FF, false);

        int crewY = y + 65;
        for (HeistClientState.HudCrewEntry member : crew) {
            int textColor = member.active() && member.online() ? 0xFFF5F7FA : 0xFF697582;
            graphics.drawString(mc.font, member.name(), x + 9, crewY, textColor, false);
            int barX = x + 9;
            int barY = crewY + 11;
            int barWidth = width - 18;
            float ratio = member.maxHealth() <= 0 ? 0F : Math.max(0F, Math.min(1F,
                    member.health() / (float) member.maxHealth()));
            graphics.fill(barX, barY, barX + barWidth, barY + 4, 0xFF293746);
            graphics.fill(barX, barY, barX + Math.round(barWidth * ratio), barY + 4,
                    ratio > .35F ? 0xFF48D6A5 : 0xFFFF5F68);
            crewY += 23;
        }

        if (state.actionable()) {
            int centerX = graphics.guiWidth() / 2;
            String prompt = state.prompt().replace("{key}", PickpocketKeyMappings.getBoundKeyName());
            int promptWidth = Math.max(150, mc.font.width(prompt) + 22);
            int promptX = centerX - promptWidth / 2;
            int promptY = graphics.guiHeight() / 2 + 28;
            graphics.fill(promptX, promptY, promptX + promptWidth, promptY + 28, 0xDC111A24);
            graphics.fill(promptX, promptY, promptX + promptWidth, promptY + 2, 0xFFFFB84D);
            graphics.drawString(mc.font, prompt, centerX - mc.font.width(prompt) / 2,
                    promptY + 6, 0xFFF5F7FA, false);
            int required = Math.max(1, state.actionRequired());
            int fill = Math.round((promptWidth - 12) * Math.min(1F, state.actionElapsed() / (float) required));
            graphics.fill(promptX + 6, promptY + 20, promptX + promptWidth - 6, promptY + 24, 0xFF293746);
            graphics.fill(promptX + 6, promptY + 20, promptX + 6 + fill, promptY + 24, 0xFFFFB84D);
        }
        graphics.pose().popPose();
    }

    private static PickpocketTarget resolvePickpocketTarget(Minecraft mc, double rangeBlocks) {
        if (mc == null || mc.level == null || mc.player == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition(1.0F);
        Vec3 look = mc.player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(rangeBlocks));
        AABB searchBounds = mc.player.getBoundingBox()
                .expandTowards(look.scale(rangeBlocks))
                .inflate(1.0D);

        Player bestTarget = null;
        double bestDistanceSq = (rangeBlocks * rangeBlocks) + 0.0001D;
        for (Player candidate : mc.level.players()) {
            if (candidate == null || candidate == mc.player || !candidate.isAlive()) {
                continue;
            }
            if (!searchBounds.intersects(candidate.getBoundingBox().inflate(0.5D))) {
                continue;
            }
            AABB targetBounds = candidate.getBoundingBox().inflate(0.15D);
            var hit = targetBounds.clip(eye, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distanceSq = eye.distanceToSqr(hit.get());
            if (distanceSq > (rangeBlocks * rangeBlocks) || distanceSq >= bestDistanceSq) {
                continue;
            }
            bestTarget = candidate;
            bestDistanceSq = distanceSq;
        }

        return bestTarget == null ? null : new PickpocketTarget(bestTarget, bestDistanceSq);
    }

    private static void sendPickpocketCancelIfNeeded() {
        if (!pickpocketHoldSent) {
            return;
        }
        PacketDistributor.sendToServer(new PickpocketCancelPayload());
        pickpocketHoldSent = false;
        pickpocketRequestedTargetId = null;
    }

    private static void sendHeistActionCancelIfNeeded() {
        if (!heistActionHoldSent) return;
        PacketDistributor.sendToServer(new HeistActionHoldPayload(false));
        heistActionHoldSent = false;
    }

    private static String formatSecondsFromTicks(int ticks) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0, ticks) / 20.0F);
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
        String amount = "$" + MoneyText.abbreviate(String.valueOf(HandheldPaymentTerminalItem.getPriceDollars(terminalStack)));
        String target = targetPlayer.getName().getString();

        String line1 = title + " | " + amount;
        String line2 = "Merchant: " + target;
        String line3 = "Right-click to pay";

        int padding = 6;
        int lineHeight = mc.font.lineHeight;
        int width = Math.max(mc.font.width(line1), Math.max(mc.font.width(line2), mc.font.width(line3))) + padding * 2;
        int height = lineHeight * 3 + padding * 2 + 2;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - height - 46;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 500.0D);
        graphics.fill(x, y, x + width, y + height, 0xD0262A2F);
        graphics.drawString(mc.font, line1, x + padding, y + padding, 0xFFFFFFFF, false);
        graphics.drawString(mc.font, line2, x + padding, y + padding + lineHeight + 1, 0xFFE6ECF3, false);
        graphics.drawString(mc.font, line3, x + padding, y + padding + (lineHeight + 1) * 2, 0xFFDCE8F7, false);
        graphics.pose().popPose();
    }

    private static void renderShelfOverlay(Minecraft mc, GuiGraphics graphics) {
        // Positioner owns the world focus; suppress shelf hover card while editing transforms.
        if (mc.screen instanceof ShelfItemPositionScreen) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult blockHit)) {
            return;
        }
        if (mc.level == null) {
            return;
        }

        var state = mc.level.getBlockState(blockHit.getBlockPos());
        if (!ShelfService.isShelf(state)) {
            return;
        }

        var lowerPos = ShelfService.toLowerShelfPos(mc.level, blockHit.getBlockPos());
        ShelfDisplayBlockEntity shelf = ShelfService.getDisplayEntity(mc.level, lowerPos);
        if (shelf == null) {
            return;
        }
        // Regular displays are item-frame style and should not show shop hover guidance.
        if (!shelf.isShopMode()) {
            return;
        }

        int slot = ShelfService.resolveSlotByHit(
                mc.level,
                lowerPos,
                blockHit.getLocation().x,
                blockHit.getLocation().y,
                blockHit.getLocation().z
        );
        ItemStack display = shelf.getDisplayItem(slot);
        long priceCents = shelf.getSlotPrice(slot);
        if (display.isEmpty() || priceCents < 0L) {
            return;
        }
        String stockLine = shelf.isCreativeShelf()
                ? "Stock: Infinite"
                : "Stock: " + Math.max(0, shelf.getSlotStock(slot));

        String line1 = display.getHoverName().getString();
        String line2 = priceCents == 0L
                ? "Price: Free"
                : "Price: $" + ShelfPrice.abbreviateFromCents(priceCents);
        String line3 = stockLine;
        String line4 = "Right-click: +1 | Shift+Right-click: +stack";
        String line5 = "Left-click: -1 | Shift+Left-click: -stack";

        int padding = 6;
        int lineHeight = mc.font.lineHeight;
        int width = Math.max(Math.max(mc.font.width(line1), mc.font.width(line2)),
                Math.max(Math.max(mc.font.width(line3), mc.font.width(line4)), mc.font.width(line5))) + padding * 2;
        int height = lineHeight * 5 + padding * 2 + 2;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - height - 70;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 500.0D);
        graphics.fill(x, y, x + width, y + height, 0xD02B323A);
        graphics.drawString(mc.font, line1, x + padding, y + padding, 0xFFFFFFFF, false);
        graphics.drawString(mc.font, line2, x + padding, y + padding + lineHeight + 1, 0xFFF1E57A, false);
        graphics.drawString(mc.font, line3, x + padding, y + padding + (lineHeight + 1) * 2, 0xFFAED8FF, false);
        graphics.drawString(mc.font, line4, x + padding, y + padding + (lineHeight + 1) * 3, 0xFFD5DFEC, false);
        graphics.drawString(mc.font, line5, x + padding, y + padding + (lineHeight + 1) * 4, 0xFFD5DFEC, false);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        // Hide held-hand model during shelf positioning so camera preview is unobstructed.
        if (mc.screen instanceof ShelfItemPositionScreen) {
            event.setCanceled(true);
            return;
        }
        if (Ove9000SawFirstPersonRenderer.render(event)
                || DallasMaskFirstPersonRenderer.render(event)) {
            event.setCanceled(true);
        }
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
                && stack.getItem() != ModItems.HANDHELD_PAYMENT_TERMINAL.get()
                && stack.getItem() != ModBlocks.SHOPPING_BAG.get().asItem()
                && !ShelfCartService.isBasketStack(stack)) {
            return;
        }

        CompoundTag tag = ItemStackDataCompat.getCustomData(stack);
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
        } else if (stack.getItem() == ModItems.HANDHELD_PAYMENT_TERMINAL.get()) {
            try {
                addHandheldTerminalTooltip(tooltip, stack);
            } catch (Throwable throwable) {
                // Never crash UI search-tree/inventory init because of tooltip hydration.
                UltimateBankingSystem.LOGGER.error("Failed to build handheld terminal tooltip.", throwable);
                tooltip.add(UbsTranslations.literal("Handheld Terminal").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
                tooltip.add(UbsTranslations.literal("Tooltip data unavailable").withStyle(ChatFormatting.RED));
            }
        } else if (stack.getItem() == ModBlocks.SHOPPING_BAG.get().asItem()) {
            addShoppingBagTooltip(tooltip, stack);
        } else {
            addBasketTooltip(tooltip, stack);
        }
    }

    private static void addBasketTooltip(List<Component> tooltip, ItemStack stack) {
        tooltip.add(Component.empty());
        tooltip.addAll(ShelfCartService.buildTooltip(stack));
    }

    private static void addShoppingBagTooltip(List<Component> tooltip, ItemStack stack) {
        CompoundTag root = ItemStackDataCompat.getCustomData(stack);
        String storeName = (root != null && root.contains(ShoppingBagDataKeys.BAG_STORE_NAME_KEY))
                ? root.getString(ShoppingBagDataKeys.BAG_STORE_NAME_KEY)
                : "Unknown Shop";
        tooltip.add(UbsTranslations.literal("Shopping Bag").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("Store: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(storeName).withStyle(ChatFormatting.AQUA)));

        if (root == null || !root.contains(ShoppingBagDataKeys.BAG_DATA_KEY, Tag.TAG_COMPOUND)) {
            tooltip.add(UbsTranslations.literal("Contents: Empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        CompoundTag bagData = root.getCompound(ShoppingBagDataKeys.BAG_DATA_KEY);
        ListTag list = bagData.getList("Items", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            tooltip.add(UbsTranslations.literal("Contents: Empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        Map<String, Integer> byName = new LinkedHashMap<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag stackTag)) {
                continue;
            }
            ItemStack entry = ItemStackDataCompat.parseStack(stackTag);
            if (entry.isEmpty()) {
                continue;
            }
            String name = entry.getHoverName().getString();
            byName.merge(name, Math.max(1, entry.getCount()), Integer::sum);
        }
        if (byName.isEmpty()) {
            tooltip.add(UbsTranslations.literal("Contents: Empty").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        tooltip.add(UbsTranslations.literal("Contents:").withStyle(ChatFormatting.GRAY));
        int shown = 0;
        for (Map.Entry<String, Integer> entry : byName.entrySet()) {
            if (shown >= 6) {
                break;
            }
            tooltip.add(Component.literal("• ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(UbsTranslations.literal("x").append(Component.literal(entry.getValue() + " ")).withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(entry.getKey()).withStyle(ChatFormatting.WHITE)));
            shown++;
        }
        int remaining = byName.size() - shown;
        if (remaining > 0) {
            tooltip.add(Component.literal("... +")
                    .append(Component.literal(String.valueOf(remaining)))
                    .append(UbsTranslations.literal(" more"))
                    .withStyle(ChatFormatting.DARK_GRAY));
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

        tooltip.add(Component.literal("Credit Card Details").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(Component.literal("Card Number: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(CreditCardService.maskCardNumber(cardNumber)).withStyle(ChatFormatting.YELLOW)));
        tooltip.add(Component.literal("CVC: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(cvc).withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("Bank: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(bankName).withStyle(ChatFormatting.BLUE)));
        tooltip.add(Component.literal("Status: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(blocked ? "BLOCKED" : "ACTIVE")
                        .withStyle(blocked ? ChatFormatting.RED : ChatFormatting.GREEN)));
        tooltip.add(Component.literal("Linked Account: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(accountId).withStyle(ChatFormatting.DARK_AQUA)));
        tooltip.add(Component.literal("Expiry: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(CreditCardService.formatExpiryMonthYear(expiry))
                        .withStyle(expired ? ChatFormatting.RED : ChatFormatting.GREEN)));
        if (expired) {
            tooltip.add(Component.literal("This card is expired.").withStyle(ChatFormatting.RED));
        }
        if (blocked) {
            tooltip.add(Component.literal("This card is blocked.").withStyle(ChatFormatting.RED));
        }
    }

    private static void addHandheldTerminalTooltip(List<Component> tooltip, ItemStack stack) {
        String shopName = HandheldPaymentTerminalItem.getShopName(stack);
        String amount = "$" + MoneyText.abbreviate(String.valueOf(HandheldPaymentTerminalItem.getPriceDollars(stack)));
        int result = HandheldPaymentTerminalItem.getResultState(stack);
        String state = switch (result) {
            case HandheldPaymentTerminalItem.RESULT_SUCCESS -> "SUCCESS";
            case HandheldPaymentTerminalItem.RESULT_DENIED -> "DENIED";
            default -> "IDLE";
        };
        ChatFormatting stateColor = switch (result) {
            case HandheldPaymentTerminalItem.RESULT_SUCCESS -> ChatFormatting.GREEN;
            case HandheldPaymentTerminalItem.RESULT_DENIED -> ChatFormatting.RED;
            default -> ChatFormatting.GRAY;
        };
        tooltip.add(UbsTranslations.literal("Handheld Terminal").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        tooltip.add(UbsTranslations.literal("Name: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(shopName).withStyle(ChatFormatting.WHITE)));
        tooltip.add(UbsTranslations.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(amount).withStyle(ChatFormatting.GOLD)));
        tooltip.add(UbsTranslations.literal("State: ").withStyle(ChatFormatting.GRAY)
                .append(UbsTranslations.literal(state).withStyle(stateColor)));
        tooltip.add(UbsTranslations.literal("Use: Hold it while others right-click you to pay").withStyle(ChatFormatting.DARK_GRAY));
    }
}
