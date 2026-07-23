package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.claim.ClaimSelectionType;
import net.austizz.ultimatebankingsystem.claim.ClaimToolKind;
import net.austizz.ultimatebankingsystem.network.ClaimModeSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Full claim-mode HUD rendered in place of Minecraft's normal HUD. */
public final class ClaimModeHudRenderer {
    private static final int NAVY = 0xE80A1726;
    private static final int PANEL = 0xE5122A42;
    private static final int PANEL_ALT = 0xE51A3754;
    private static final int CYAN = 0xFF45C7F4;
    private static final int GOLD = 0xFFF4B942;
    private static final int GREEN = 0xFF46D69A;
    private static final int RED = 0xFFFF647C;
    private static final int TEXT = 0xFFF2F7FC;
    private static final int MUTED = 0xFFAABBD0;

    private ClaimModeHudRenderer() {
    }

    public static boolean render(GuiGraphics graphics) {
        if (graphics == null || !ClaimModeClientState.active()) {
            return false;
        }
        try {
            Minecraft minecraft = Minecraft.getInstance();
            ClaimModeSnapshotPayload snapshot = ClaimModeClientState.snapshot();
            int width = graphics.guiWidth();
            int height = graphics.guiHeight();
            Font font = minecraft.font;
            boolean compact = width < 700 || height < 400;
            ClaimModeClientState.Modal modal = ClaimModeClientState.modal();
            List<Button> controls = buttons(width, height, snapshot, modal);

            drawTopBar(graphics, font, snapshot, width);
            drawInspector(graphics, font, snapshot, width, height, compact);
            if (modal == ClaimModeClientState.Modal.NONE) {
                for (Button button : controls) {
                    drawButton(graphics, font, button);
                }
                drawStatus(graphics, font, snapshot, width, height, compact, controls);
                if (!ClaimModeClientState.cursorMode()) {
                    drawCrosshair(graphics, width / 2, height / 2);
                }
            } else {
                drawModal(graphics, font, snapshot, width, height, modal);
                for (Button button : controls) {
                    drawButton(graphics, font, button);
                }
            }
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String actionAt(int width,
                                  int height,
                                  double mouseX,
                                  double mouseY,
                                  ClaimModeSnapshotPayload snapshot,
                                  ClaimModeClientState.Modal modal) {
        for (Button button : buttons(width, height, snapshot, modal)) {
            if (button.enabled && button.contains(mouseX, mouseY)) {
                return button.action;
            }
        }
        return "";
    }

    private static void drawTopBar(GuiGraphics graphics, Font font,
                                   ClaimModeSnapshotPayload snapshot, int width) {
        graphics.fill(8, 8, width - 8, 46, NAVY);
        graphics.fill(8, 8, 12, 46, CYAN);
        graphics.fill(12, 44, width - 8, 46, 0xFF244A6B);
        String timer = formatTime(ClaimModeClientState.remainingTicks());
        int timerWidth = font.width(timer);
        String mode = snapshot.addMode() ? "ADD" : "REMOVE";
        int modeWidth = font.width(mode);
        int availableTextWidth = Math.max(24, width - 54 - Math.max(timerWidth, modeWidth));
        String title = font.plainSubstrByWidth(snapshot.title(), availableTextWidth);
        graphics.drawString(font, title, 22, 17, TEXT, false);
        String context = snapshot.contextName().isBlank() ? "UBS Claim Workspace" : snapshot.contextName();
        context = font.plainSubstrByWidth(context, availableTextWidth);
        graphics.drawString(font, context, 22, 31, MUTED, false);
        graphics.drawString(font, timer, width - 20 - timerWidth, 17, GOLD, false);
        graphics.drawString(font, mode, width - 20 - modeWidth, 31,
                snapshot.addMode() ? GREEN : RED, false);
    }

    private static void drawInspector(GuiGraphics graphics, Font font,
                                      ClaimModeSnapshotPayload snapshot,
                                      int width, int height, boolean compact) {
        int panelWidth = compact ? Math.min(width - 16, 330) : 238;
        int x = 8;
        int y = 54;
        boolean tight = compact && height < 220;
        int panelHeight = compact ? tight ? 54 : 82 : 156;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, PANEL);
        graphics.fill(x, y, x + panelWidth, y + 2, CYAN);
        graphics.drawString(font, "Selection", x + 12, y + 10, TEXT, false);

        ClaimToolKind kind = ClaimToolKind.byName(snapshot.kind());
        int lineY = y + 27;
        if (kind.selectionType() == ClaimSelectionType.CUBOID) {
            graphics.drawString(font, positionLine("Pos 1", snapshot.hasPos1(),
                    snapshot.pos1X(), snapshot.pos1Y(), snapshot.pos1Z()), x + 12, lineY, MUTED, false);
            graphics.drawString(font, positionLine("Pos 2", snapshot.hasPos2(),
                    snapshot.pos2X(), snapshot.pos2Y(), snapshot.pos2Z()), x + 12, lineY + 14, MUTED, false);
            if (!tight && snapshot.hasPos1() && snapshot.hasPos2()) {
                int sizeX = Math.abs(snapshot.pos2X() - snapshot.pos1X()) + 1;
                int sizeY = Math.abs(snapshot.pos2Y() - snapshot.pos1Y()) + 1;
                int sizeZ = Math.abs(snapshot.pos2Z() - snapshot.pos1Z()) + 1;
                long volume = (long) sizeX * sizeY * sizeZ;
                graphics.drawString(font, sizeX + " x " + sizeY + " x " + sizeZ
                        + "  |  " + volume + " blocks", x + 12, lineY + 30, GOLD, false);
            } else if (!tight) {
                graphics.drawString(font, "Left click Pos 1  |  Right click Pos 2",
                        x + 12, lineY + 30, GOLD, false);
            }
        } else if (kind.selectionType() == ClaimSelectionType.BLOCK_TARGET) {
            graphics.drawString(font, "Aim at a delivery pallet and click.", x + 12, lineY, MUTED, false);
            graphics.drawString(font, "Pending  +" + snapshot.pendingAdd() + "  /  -" + snapshot.pendingRemove(),
                    x + 12, lineY + 16, GOLD, false);
        } else {
            graphics.drawString(font, snapshot.hasAnchor()
                            ? "Position and facing captured."
                            : "Stand at the target and capture your facing.",
                    x + 12, lineY, snapshot.hasAnchor() ? GREEN : MUTED, false);
        }
        if (!compact) {
            graphics.drawString(font, "Nearby claims: " + snapshot.outlines().size(),
                    x + 12, y + 114, MUTED, false);
            graphics.drawString(font, snapshot.outlinesVisible() ? "Outlines visible" : "Outlines hidden",
                    x + 12, y + 130, snapshot.outlinesVisible() ? CYAN : MUTED, false);
        }
    }

    private static void drawStatus(GuiGraphics graphics, Font font,
                                   ClaimModeSnapshotPayload snapshot,
                                   int width, int height, boolean compact,
                                   List<Button> controls) {
        String instruction = ClaimModeClientState.cursorMode()
                ? "Release Tab to return to world selection"
                : "Hold Tab for controls";
        int controlsTop = controls.stream().mapToInt(button -> button.y).min().orElse(height - 8);
        int instructionY = controlsTop - 20;
        int inspectorBottom = 54 + (compact ? height < 220 ? 54 : 82 : 156);

        if (!compact) {
            if (!snapshot.statusMessage().isBlank()) {
                drawStatusBanner(graphics, font, snapshot, 254, 54, Math.max(80, width - 262));
            }
            drawCenteredHint(graphics, font, instruction, instructionY);
            return;
        }

        if (instructionY <= inspectorBottom + 2) {
            return;
        }
        boolean hasStatus = !snapshot.statusMessage().isBlank();
        boolean roomForTwoRows = instructionY - (inspectorBottom + 6) >= 22;
        if (hasStatus) {
            int statusY = roomForTwoRows ? inspectorBottom + 6 : instructionY;
            drawStatusBanner(graphics, font, snapshot, 8, statusY, width - 16);
        }
        if (!hasStatus || roomForTwoRows) {
            drawCenteredHint(graphics, font, instruction, instructionY);
        }
    }

    private static void drawStatusBanner(GuiGraphics graphics,
                                         Font font,
                                         ClaimModeSnapshotPayload snapshot,
                                         int x,
                                         int y,
                                         int maxWidth) {
        int safeWidth = Math.max(40, maxWidth);
        String message = font.plainSubstrByWidth(snapshot.statusMessage(), safeWidth - 18);
        int messageWidth = Math.min(safeWidth, font.width(message) + 18);
        graphics.fill(x, y, x + messageWidth, y + 18, NAVY);
        graphics.fill(x, y, x + 3, y + 18, snapshot.statusSuccess() ? GREEN : RED);
        graphics.drawString(font, message, x + 9, y + 5,
                snapshot.statusSuccess() ? TEXT : 0xFFFFC2CC, false);
    }

    private static void drawCenteredHint(GuiGraphics graphics,
                                         Font font,
                                         String instruction,
                                         int y) {
        int width = graphics.guiWidth();
        int instructionWidth = font.width(instruction);
        graphics.fill(width / 2 - instructionWidth / 2 - 9, y,
                width / 2 + instructionWidth / 2 + 9, y + 16, NAVY);
        graphics.drawString(font, instruction, width / 2 - instructionWidth / 2,
                y + 4, GOLD, false);
    }

    private static void drawModal(GuiGraphics graphics, Font font,
                                  ClaimModeSnapshotPayload snapshot,
                                  int width, int height,
                                  ClaimModeClientState.Modal modal) {
        graphics.fill(0, 0, width, height, 0x88000000);
        int modalWidth = Math.min(390, width - 32);
        int modalHeight = modal == ClaimModeClientState.Modal.EXIT ? 124 : 110;
        int x = (width - modalWidth) / 2;
        int y = (height - modalHeight) / 2;
        graphics.fill(x, y, x + modalWidth, y + modalHeight, NAVY);
        graphics.fill(x, y, x + modalWidth, y + 3,
                modal == ClaimModeClientState.Modal.REMOVE_CONFIRM ? RED : GOLD);
        String title = modal == ClaimModeClientState.Modal.REMOVE_CONFIRM
                ? "Confirm Region Removal" : "Exit Claim Mode";
        graphics.drawString(font, title, x + 16, y + 16, TEXT, false);
        String detail = modal == ClaimModeClientState.Modal.REMOVE_CONFIRM
                ? "This removes the selected overlap from the current claim."
                : snapshot.pendingAdd() + snapshot.pendingRemove() > 0
                ? "Unsaved delivery-pallet changes will be discarded."
                : appliedAndSettled(snapshot)
                ? "Your claim is already saved. Save & Exit safely closes this workspace."
                : "No claim changes have been applied yet.";
        graphics.drawString(font, font.plainSubstrByWidth(detail, modalWidth - 32),
                x + 16, y + 38, MUTED, false);
    }

    private static List<Button> buttons(int width, int height,
                                        ClaimModeSnapshotPayload snapshot,
                                        ClaimModeClientState.Modal modal) {
        List<Button> buttons = new ArrayList<>();
        if (modal == ClaimModeClientState.Modal.REMOVE_CONFIRM) {
            int modalWidth = Math.min(390, width - 32);
            int x = (width - modalWidth) / 2;
            int y = (height - 110) / 2;
            buttons.add(new Button("resume", "Back", x + 16, y + 72, 92, 24, true, false));
            buttons.add(new Button("confirm_remove", "Remove", x + modalWidth - 108, y + 72,
                    92, 24, true, true));
            return buttons;
        }
        if (modal == ClaimModeClientState.Modal.EXIT) {
            int modalWidth = Math.min(390, width - 32);
            int x = (width - modalWidth) / 2;
            int y = (height - 124) / 2;
            int buttonY = y + 82;
            buttons.add(new Button("resume", "Resume", x + 14, buttonY, 92, 26, true, false));
            boolean staged = ClaimToolKind.byName(snapshot.kind()).staged();
            if (staged) {
                buttons.add(new Button("save", "Save & Exit", x + modalWidth / 2 - 52,
                        buttonY, 104, 26, true, false));
            }
            if (!staged && appliedAndSettled(snapshot)) {
                buttons.add(new Button("finish", "Save & Exit", x + modalWidth - 122,
                        buttonY, 108, 26, true, false));
            } else {
                buttons.add(new Button("discard", "Discard & Exit", x + modalWidth - 122,
                        buttonY, 108, 26, true, true));
            }
            return buttons;
        }

        ClaimToolKind kind = ClaimToolKind.byName(snapshot.kind());
        int buttonHeight = 24;
        int gap = 5;
        List<ButtonSpec> specs = new ArrayList<>();
        if (kind.supportsMode()) {
            specs.add(new ButtonSpec("add", "1  Add", snapshot.addMode(), false));
            specs.add(new ButtonSpec("remove", "2  Remove", !snapshot.addMode(), true));
        }
        if (kind.isViewingRoomAnchor()
                || kind.requiresExitCapture() && !snapshot.hasAnchor()) {
            specs.add(new ButtonSpec("capture", "Enter  Capture", false, false));
        } else {
            specs.add(new ButtonSpec("apply", kind.staged() ? "Ctrl+S  Save" : "Enter  Apply",
                    false, !snapshot.addMode() && kind.supportsMode()));
        }
        if (!kind.staged() && !kind.isViewingRoomAnchor()) {
            specs.add(new ButtonSpec("clear", "C  Clear", false, false));
        }
        specs.add(new ButtonSpec("outlines", "O  Outlines", snapshot.outlinesVisible(), false));
        specs.add(new ButtonSpec("exit", "Esc  Exit", false, true));

        int buttonWidth = width < 700 ? 88 : 108;
        int columns = Math.max(1, Math.min(specs.size(), (width - 16 + gap) / (buttonWidth + gap)));
        int rows = (specs.size() + columns - 1) / columns;
        int totalWidth = columns * buttonWidth + (columns - 1) * gap;
        int startX = (width - totalWidth) / 2;
        int startY = height - 10 - rows * buttonHeight - (rows - 1) * gap;
        for (int index = 0; index < specs.size(); index++) {
            int column = index % columns;
            int row = index / columns;
            ButtonSpec spec = specs.get(index);
            buttons.add(new Button(spec.action, spec.label,
                    startX + column * (buttonWidth + gap),
                    startY + row * (buttonHeight + gap),
                    buttonWidth, buttonHeight, true, spec.destructive, spec.selected));
        }
        return buttons;
    }

    private static boolean appliedAndSettled(ClaimModeSnapshotPayload snapshot) {
        return snapshot.appliedSuccessfully()
                && !snapshot.hasPos1()
                && !snapshot.hasPos2()
                && snapshot.pendingAdd() == 0
                && snapshot.pendingRemove() == 0;
    }

    private static void drawButton(GuiGraphics graphics, Font font, Button button) {
        int color = button.destructive ? 0xE3421F31 : button.selected ? 0xE3265A72 : PANEL_ALT;
        int border = button.destructive ? RED : button.selected ? GREEN : CYAN;
        graphics.fill(button.x, button.y, button.x + button.width, button.y + button.height, color);
        graphics.fill(button.x, button.y, button.x + 3, button.y + button.height, border);
        int textX = button.x + (button.width - font.width(button.label)) / 2;
        graphics.drawString(font, button.label, textX, button.y + (button.height - 8) / 2,
                button.enabled ? TEXT : MUTED, false);
    }

    private static void drawCrosshair(GuiGraphics graphics, int centerX, int centerY) {
        graphics.fill(centerX - 7, centerY, centerX - 2, centerY + 1, CYAN);
        graphics.fill(centerX + 3, centerY, centerX + 8, centerY + 1, CYAN);
        graphics.fill(centerX, centerY - 7, centerX + 1, centerY - 2, CYAN);
        graphics.fill(centerX, centerY + 3, centerX + 1, centerY + 8, CYAN);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, GOLD);
    }

    private static String positionLine(String label, boolean present, int x, int y, int z) {
        return present ? label + "  " + x + ", " + y + ", " + z : label + "  Not set";
    }

    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private record ButtonSpec(String action, String label, boolean selected, boolean destructive) {
    }

    private static final class Button {
        private final String action;
        private final String label;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final boolean enabled;
        private final boolean destructive;
        private final boolean selected;

        private Button(String action, String label, int x, int y, int width, int height,
                       boolean enabled, boolean destructive) {
            this(action, label, x, y, width, height, enabled, destructive, false);
        }

        private Button(String action, String label, int x, int y, int width, int height,
                       boolean enabled, boolean destructive, boolean selected) {
            this.action = action;
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.enabled = enabled;
            this.destructive = destructive;
            this.selected = selected;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
