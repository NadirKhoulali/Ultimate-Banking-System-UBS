package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.claim.ClaimAction;
import net.austizz.ultimatebankingsystem.claim.ClaimSelectionType;
import net.austizz.ultimatebankingsystem.claim.ClaimToolKind;
import net.austizz.ultimatebankingsystem.network.ClaimModeActionPayload;
import net.austizz.ultimatebankingsystem.network.ClaimModeSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClaimModeClientState {
    public enum Modal {
        NONE,
        EXIT,
        REMOVE_CONFIRM
    }

    private static ClaimModeSnapshotPayload snapshot = ClaimModeSnapshotPayload.inactive();
    private static boolean active;
    private static boolean cursorMode;
    private static Modal modal = Modal.NONE;
    private static int remainingTicks;
    private static long revision;

    private ClaimModeClientState() {
    }

    public static void apply(ClaimModeSnapshotPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload == null || !payload.active()) {
            clear();
            return;
        }
        snapshot = payload;
        remainingTicks = payload.remainingTicks();
        active = true;
        revision++;
        if (minecraft.screen != null) {
            minecraft.setScreen(null);
        }
        if (!cursorMode && minecraft.mouseHandler != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static void tick(Minecraft minecraft) {
        if (!active) {
            return;
        }
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            clear();
            return;
        }
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    public static boolean handleKey(int key, int action, int modifiers) {
        if (!active) {
            return false;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            if (action == GLFW.GLFW_PRESS) {
                setCursorMode(true);
            } else if (action == GLFW.GLFW_RELEASE) {
                setCursorMode(false);
            }
            return true;
        }
        if (action != GLFW.GLFW_PRESS) {
            return cursorMode;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            modal = modal == Modal.EXIT ? Modal.NONE : Modal.EXIT;
            revision++;
            return true;
        }
        if (modal != Modal.NONE) {
            return true;
        }
        if (key == GLFW.GLFW_KEY_1 && kind().supportsMode()) {
            send(ClaimAction.SET_ADD_MODE);
            return true;
        }
        if (key == GLFW.GLFW_KEY_2 && kind().supportsMode()) {
            send(ClaimAction.SET_REMOVE_MODE);
            return true;
        }
        if (key == GLFW.GLFW_KEY_C) {
            send(ClaimAction.CLEAR);
            return true;
        }
        if (key == GLFW.GLFW_KEY_O) {
            send(ClaimAction.TOGGLE_OUTLINES);
            return true;
        }
        if (key == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && kind().staged()) {
            send(ClaimAction.SAVE_AND_EXIT);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            primaryAction();
            return true;
        }
        return cursorMode;
    }

    public static boolean handleMouse(double mouseX, double mouseY, int button, int action) {
        if (!active) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (cursorMode) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS
                    && minecraft.getWindow() != null) {
                String uiAction = ClaimModeHudRenderer.actionAt(
                        minecraft.getWindow().getGuiScaledWidth(),
                        minecraft.getWindow().getGuiScaledHeight(), mouseX, mouseY,
                        snapshot, modal);
                handleUiAction(uiAction);
            }
            return true;
        }
        if (action != GLFW.GLFW_PRESS) {
            return true;
        }
        ClaimSelectionType selectionType = kind().selectionType();
        if (selectionType == ClaimSelectionType.POSITION_AND_FACING) {
            return true;
        }
        BlockPos target = observedTarget(minecraft);
        if (target == null) {
            return true;
        }
        if (selectionType == ClaimSelectionType.BLOCK_TARGET) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                send(ClaimAction.STAGE_TARGET, target);
            }
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            send(ClaimAction.SET_POS1, target);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            send(ClaimAction.SET_POS2, target);
        }
        return true;
    }

    public static void clear() {
        boolean shouldGrab = active || cursorMode;
        active = false;
        cursorMode = false;
        modal = Modal.NONE;
        snapshot = ClaimModeSnapshotPayload.inactive();
        remainingTicks = 0;
        revision++;
        Minecraft minecraft = Minecraft.getInstance();
        if (shouldGrab && minecraft.screen == null && minecraft.mouseHandler != null) {
            minecraft.mouseHandler.grabMouse();
        }
    }

    public static boolean active() {
        return active && snapshot.active();
    }

    public static boolean cursorMode() {
        return cursorMode;
    }

    public static Modal modal() {
        return modal;
    }

    public static ClaimModeSnapshotPayload snapshot() {
        return snapshot;
    }

    public static ClaimToolKind kind() {
        return ClaimToolKind.byName(snapshot.kind());
    }

    public static int remainingTicks() {
        return remainingTicks;
    }

    public static long revision() {
        return revision;
    }

    private static void handleUiAction(String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        switch (action) {
            case "resume" -> {
                modal = Modal.NONE;
                revision++;
            }
            case "exit" -> {
                modal = Modal.EXIT;
                revision++;
            }
            case "discard" -> send(ClaimAction.DISCARD_AND_EXIT);
            case "finish" -> send(ClaimAction.FINISH_AND_EXIT);
            case "save" -> send(ClaimAction.SAVE_AND_EXIT);
            case "add" -> send(ClaimAction.SET_ADD_MODE);
            case "remove" -> send(ClaimAction.SET_REMOVE_MODE);
            case "apply" -> primaryAction();
            case "confirm_remove" -> {
                modal = Modal.NONE;
                send(ClaimAction.APPLY);
            }
            case "clear" -> send(ClaimAction.CLEAR);
            case "outlines" -> send(ClaimAction.TOGGLE_OUTLINES);
            case "capture" -> send(ClaimAction.CAPTURE_POSITION);
            default -> {
            }
        }
    }

    private static void primaryAction() {
        ClaimToolKind kind = kind();
        if (kind.isViewingRoomAnchor()
                || kind.requiresExitCapture() && !snapshot.hasAnchor()) {
            send(ClaimAction.CAPTURE_POSITION);
            return;
        }
        if (kind.staged()) {
            send(ClaimAction.SAVE_AND_EXIT);
            return;
        }
        if (kind.supportsMode() && !snapshot.addMode()) {
            modal = Modal.REMOVE_CONFIRM;
            revision++;
            return;
        }
        send(ClaimAction.APPLY);
    }

    private static BlockPos observedTarget(Minecraft minecraft) {
        if (minecraft == null || !(minecraft.hitResult instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hit.getBlockPos().immutable();
    }

    private static void send(ClaimAction action) {
        PacketDistributor.sendToServer(ClaimModeActionPayload.withoutTarget(
                snapshot.sessionId(), action.name()));
    }

    private static void send(ClaimAction action, BlockPos target) {
        PacketDistributor.sendToServer(new ClaimModeActionPayload(
                snapshot.sessionId(), action.name(), true,
                target.getX(), target.getY(), target.getZ()));
    }

    private static void setCursorMode(boolean enabled) {
        if (cursorMode == enabled) {
            return;
        }
        cursorMode = enabled;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.mouseHandler == null) {
            return;
        }
        if (enabled) {
            minecraft.mouseHandler.releaseMouse();
        } else if (minecraft.screen == null) {
            minecraft.mouseHandler.grabMouse();
        }
        revision++;
    }
}
