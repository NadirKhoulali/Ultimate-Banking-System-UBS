package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.NumismaticsMigrationFilePicker;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationPhase;
import net.austizz.ultimatebankingsystem.migration.numismatics.NumismaticsMigrationSnapshot;
import net.austizz.ultimatebankingsystem.network.NumismaticsMigrationActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NumismaticsMigrationScreen extends Screen {
    private static final int BG = 0xFF071422;
    private static final int PANEL = 0xFF0E2338;
    private static final int CARD = 0xFF132C45;
    private static final int BORDER = 0xFF244A6D;
    private static final int CYAN = 0xFF42C8FF;
    private static final int GOLD = 0xFFFFC857;
    private static final int GREEN = 0xFF5FE79D;
    private static final int RED = 0xFFFF6B7E;
    private static final int TEXT = 0xFFF4F8FF;
    private static final int MUTED = 0xFFA9BED4;
    private static final String[] STEP_TITLES = {"Source", "Policy", "Scan", "Convert", "Finish"};

    private NumismaticsMigrationSnapshot snapshot;
    private int page;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int bodyTop;
    private int bodyBottom;
    private int refreshTicks;
    private DesktopEditBox rateInput;
    private String localScope;
    private boolean localCards;
    private boolean localUnsafe;

    public NumismaticsMigrationScreen(NumismaticsMigrationSnapshot snapshot) {
        super(Component.literal("Numismatics Migration"));
        this.snapshot = snapshot;
        this.page = suggestedPage(snapshot);
        this.localScope = snapshot.scope();
        this.localCards = snapshot.convertCards();
        this.localUnsafe = snapshot.allowUnsafeRemoval();
    }

    public void refresh(NumismaticsMigrationSnapshot next) {
        String previousPhase = snapshot.phase();
        snapshot = next;
        if (!previousPhase.equals(next.phase())) page = Math.max(page, suggestedPage(next));
        localScope = next.scope();
        localCards = next.convertCards();
        localUnsafe = next.allowUnsafeRemoval();
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        panelWidth = Math.min(900, Math.max(300, width - 20));
        panelHeight = Math.min(560, Math.max(220, height - 20));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        int headerHeight = 48;
        int stepsHeight = 32;
        int footerHeight = 38;
        bodyTop = panelTop + headerHeight + stepsHeight;
        bodyBottom = panelTop + panelHeight - footerHeight;

        buildStepButtons(panelTop + headerHeight + 5);
        switch (page) {
            case 0 -> buildSourcePage();
            case 1 -> buildPolicyPage();
            case 2 -> buildScanPage();
            case 3 -> buildConvertPage();
            default -> buildFinishPage();
        }
        buildFooter();
    }

    private void buildStepButtons(int y) {
        int gap = 4;
        int available = panelWidth - 28;
        int buttonWidth = (available - gap * 4) / 5;
        for (int index = 0; index < 5; index++) {
            int target = index;
            String label = panelWidth < 520 ? Integer.toString(index + 1) : (index + 1) + "  " + STEP_TITLES[index];
            DesktopButton button = addButton(panelLeft + 14 + index * (buttonWidth + gap), y,
                    buttonWidth, 22, label, index == page ? CYAN : BORDER, pressed -> {
                        page = target;
                        rebuild();
                    }, "Open step " + (index + 1) + ": " + STEP_TITLES[index] + ".");
            button.active = canOpenPage(index);
        }
    }

    private void buildSourcePage() {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int y = bodyTop + 30;
        int gap = 12;
        int column = Math.max(120, (width - gap) / 2);
        if (panelWidth < 520) {
            addButton(x, y, width, 34, "Use This World's Data", CYAN,
                    ignored -> send("WORLD_SOURCE", "", 0, false, false),
                    "Read Numismatics accounts from this world. A fresh live snapshot is created if Numismatics has not written its bank file yet.");
            addButton(x, y + 44, width, 34, "Import Numismatics File", GOLD,
                    ignored -> NumismaticsMigrationFilePicker.choose(snapshot.sessionToken()),
                    "Choose a numismatics_bank.dat file from another world using the operating-system file picker.");
        } else {
            addButton(x, y, column, 52, "Use This World's Data", CYAN,
                    ignored -> send("WORLD_SOURCE", "", 0, false, false),
                    "Read Numismatics accounts from this world. A fresh live snapshot is created if Numismatics has not written its bank file yet.");
            addButton(x + column + gap, y, column, 52, "Import Numismatics File", GOLD,
                    ignored -> NumismaticsMigrationFilePicker.choose(snapshot.sessionToken()),
                    "Choose a numismatics_bank.dat file from another world using the operating-system file picker.");
        }
        if (!snapshot.sourceHash().isBlank()) {
            addButton(x, Math.min(bodyBottom - 30, y + 78), Math.min(170, width), 24,
                    "Continue to Policy", GREEN, ignored -> { page = 1; rebuild(); });
        }
    }

    private void buildPolicyPage() {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int y = bodyTop + 42;
        int col = panelWidth < 600 ? width : (width - 12) / 2;
        rateInput = new DesktopEditBox(font, x, y, Math.min(col, 280), 24,
                Component.literal("Whole cents per Spur"));
        rateInput.setValue(Integer.toString(snapshot.centsPerSpur()));
        rateInput.setMaxLength(9);
        rateInput.setTooltip(Tooltip.create(Component.literal(
                "Whole UBS cents credited for one Numismatics Spur. 100 means $1.00 per Spur.")));
        addRenderableWidget(rateInput);
        int scopeY = y + 50;
        int rightX = panelWidth < 600 ? x : x + col + 12;
        int cardsY = panelWidth < 600 ? scopeY + 50 : y;
        int unsafeY = cardsY + 50;
        addButton(x, scopeY, Math.min(col, 310), 26,
                "Scope: " + pretty(localScope), CYAN, ignored -> {
                    localScope = "FULL_ECONOMY".equals(localScope) ? "ACCOUNTS_ONLY" : "FULL_ECONOMY";
                    rebuildKeepingRate();
                }, "Switch between converting only account balances and converting the complete economy, including physical coins and cards.");
        addButton(rightX, cardsY, Math.min(col, 310), 26,
                "Bank Cards: " + (localCards ? "Convert" : "Leave"), localCards ? GREEN : GOLD,
                ignored -> { localCards = !localCards; rebuildKeepingRate(); },
                "Convert linked and blank Numismatics bank cards into UBS cards. Full economy conversion requires this when cards exist.");
        addButton(rightX, unsafeY, Math.min(col, 340), 26,
                "Account-Only Uninstall Risk: " + (localUnsafe ? "Accepted" : "Not Accepted"),
                localUnsafe ? RED : BORDER, ignored -> { localUnsafe = !localUnsafe; rebuildKeepingRate(); },
                "Required only for Accounts Only. Accepting acknowledges that physical Numismatics items will remain and removing the mod can delete them.");
        int saveY = panelWidth < 600 ? unsafeY + 90 : bodyTop + 190;
        addButton(x, Math.min(bodyBottom - 30, saveY),
                Math.min(240, width), 24, "Save Policy & Continue", GREEN, ignored -> {
                    int rate = parseRate();
                    if (rate < 1) return;
                    page = 2;
                    send("SET_OPTIONS", localScope, rate, localCards, localUnsafe);
                }, "Validate these settings, store the migration policy, and continue to the preflight scan.");
    }

    private void buildScanPage() {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int y = bodyTop + 24;
        boolean running = "PREFLIGHT_RUNNING".equals(snapshot.phase());
        boolean scanned = "READY".equals(snapshot.phase()) || snapshot.authoritativeScan()
                || snapshot.physicalCoinItems() > 0 || snapshot.candidatePlayerFiles() > 0
                || snapshot.candidateChunks() > 0;
        DesktopButton scan = addButton(x, y, Math.min(220, width), 28,
                running ? "Scanning..." : scanned ? "Re-run Fresh Preflight" : "Run Fresh Preflight", CYAN,
                ignored -> send("PREFLIGHT", "", 0, false, false),
                "Saves current online inventories, then rescans all player files, chunks, entities, containers, cards, and Numismatics SavedData from scratch.");
        scan.active = !running && !snapshot.sourceHash().isBlank();
        if ("READY".equals(snapshot.phase()) && snapshot.blockers().isEmpty()) {
            addButton(x + Math.min(232, width / 2), y, Math.min(190, Math.max(100, width - 232)), 28,
                    "Review Conversion", GREEN, ignored -> { page = 3; rebuild(); });
        }
    }

    private void buildConvertPage() {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int y = bodyTop + 24;
        boolean canStart = "READY".equals(snapshot.phase()) && snapshot.blockers().isEmpty();
        boolean running = running(snapshot.phase());
        DesktopButton execute = addButton(x, y, Math.min(240, width), 30,
                running ? "Conversion In Progress" : "Lock Server & Convert", running ? GOLD : RED,
                ignored -> send("EXECUTE", "", 0, false, false));
        execute.active = canStart && !running;
        if (snapshot.maintenanceLocked() && !running && !"COMPLETE".equals(snapshot.phase())) {
            addButton(x, y + 40, Math.min(190, width), 26, "Resume Migration", CYAN,
                    ignored -> send("RESUME", "", 0, false, false));
            addButton(x + Math.min(202, width / 2), y + 40, Math.min(180, Math.max(100, width - 202)), 26,
                    "Restore Backup", RED, ignored -> send("ROLLBACK", "", 0, false, false));
        }
    }

    private void buildFinishPage() {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int y = bodyTop + 24;
        if (snapshot.recoveryItems() > 0) {
            addButton(x, y, Math.min(220, width), 28,
                    "Claim Recovery Items (" + snapshot.recoveryItems() + ")", GOLD,
                    ignored -> send("CLAIM_RECOVERY", "", 0, false, false));
            y += 38;
        }
        DesktopButton stop = addButton(x, y, Math.min(220, width), 30,
                "Save & Stop Server", RED, ignored -> send("STOP_SERVER", "", 0, false, false));
        stop.active = "COMPLETE".equals(snapshot.phase());
        if (!snapshot.backupDirectory().isBlank() && !"COMPLETE".equals(snapshot.phase())) {
            addButton(x + Math.min(232, width / 2), y, Math.min(180, Math.max(100, width - 232)), 30,
                    "Restore Backup", RED, ignored -> send("ROLLBACK", "", 0, false, false));
        }
    }

    private void buildFooter() {
        int y = panelTop + panelHeight - 31;
        addButton(panelLeft + 14, y, 80, 22, "Close", BORDER, ignored -> onClose());
        addButton(panelLeft + panelWidth - 104, y, 90, 22, "Refresh", CYAN,
                ignored -> send("REFRESH", "", 0, false, false));
    }

    private void rebuildKeepingRate() {
        String value = rateInput == null ? Integer.toString(snapshot.centsPerSpur()) : rateInput.getValue();
        rebuild();
        if (rateInput != null) rateInput.setValue(value);
    }

    private DesktopButton addButton(int x, int y, int width, int height, String label, int accent,
                                    java.util.function.Consumer<DesktopButton> action) {
        return addButton(x, y, width, height, label, accent, action, "");
    }

    private DesktopButton addButton(int x, int y, int width, int height, String label, int accent,
                                    java.util.function.Consumer<DesktopButton> action, String tooltip) {
        DesktopButton button = new DesktopButton(x, y, Math.max(40, width), height,
                Component.literal(label), accent, action);
        if (tooltip != null && !tooltip.isBlank()) {
            button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        }
        return addRenderableWidget(button);
    }

    private int parseRate() {
        try {
            int value = Integer.parseInt(rateInput.getValue().trim());
            if (value < 1 || value > 100_000_000) throw new NumberFormatException();
            return value;
        } catch (RuntimeException malformed) {
            if (minecraft != null && minecraft.player != null) minecraft.player.displayClientMessage(
                    Component.literal("Enter a whole-cent rate between 1 and 100,000,000."), false);
            return -1;
        }
    }

    private void send(String action, String text, int number, boolean flag, boolean secondFlag) {
        PacketDistributor.sendToServer(new NumismaticsMigrationActionPayload(
                snapshot.sessionToken(), action, text, number, flag, secondFlag));
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicks >= 20 && (running(snapshot.phase()) || snapshot.maintenanceLocked())) {
            refreshTicks = 0;
            send("REFRESH", "", 0, false, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xEE020913);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, BORDER);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1,
                panelTop + panelHeight - 1, PANEL);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 46, BG);
        graphics.fill(panelLeft + 1, bodyTop, panelLeft + panelWidth - 1, bodyBottom, CARD);
        graphics.fill(panelLeft + 1, bodyTop, panelLeft + 5, bodyBottom,
                snapshot.blockers().isEmpty() ? CYAN : RED);

        graphics.drawString(font, "Create: Numismatics Migration", panelLeft + 16, panelTop + 12, TEXT, false);
        graphics.drawString(font, phaseLabel(), panelLeft + 16, panelTop + 28,
                snapshot.maintenanceLocked() ? GOLD : MUTED, false);
        String id = snapshot.migrationId().isBlank() ? "Not configured" : snapshot.migrationId().substring(0, 8);
        graphics.drawString(font, "Run " + id, panelLeft + panelWidth - 16 - font.width("Run " + id),
                panelTop + 18, MUTED, false);

        graphics.enableScissor(panelLeft + 8, bodyTop + 2, panelLeft + panelWidth - 8, bodyBottom - 2);
        renderPageText(graphics);
        graphics.disableScissor();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderFeedback(graphics);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The migration setup owns its backdrop and must not invoke vanilla's menu blur.
    }

    private void renderFeedback(GuiGraphics graphics) {
        if (snapshot.feedback().isBlank()) return;
        int left = panelLeft + 14;
        int right = panelLeft + panelWidth - 14;
        int bottom = bodyBottom - 8;
        int top = bottom - 34;
        int accent = snapshot.feedbackError() ? RED : GREEN;
        graphics.fill(left, top, right, bottom, BG);
        graphics.fill(left, top, left + 3, bottom, accent);
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(
                Component.literal(snapshot.feedback()), right - left - 18);
        int y = top + 7;
        for (int index = 0; index < Math.min(2, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), left + 10, y, accent, false);
            y += 10;
        }
    }

    private void renderPageText(GuiGraphics graphics) {
        int x = panelLeft + 24;
        int y = bodyTop + 8;
        int maxWidth = panelWidth - 48;
        String heading = switch (page) {
            case 0 -> "Choose Conversion Source";
            case 1 -> "Define Exchange & Conversion Policy";
            case 2 -> "Preflight Inventory and Safety Scan";
            case 3 -> "Review and Execute";
            default -> "Finalize Migration";
        };
        graphics.drawString(font, heading, x, y, TEXT, false);

        if (page == 1) renderPolicyLabels(graphics);
        int detailsY = page == 1
                ? bodyTop + (panelWidth < 600 ? 238 : 136)
                : Math.max(bodyTop + 90, y + 22);
        List<String> lines = new ArrayList<>();
        if (page == 0) {
            lines.add(snapshot.sourceHash().isBlank()
                    ? "Use this world, or securely upload numismatics_bank.dat from another world."
                    : "Validated: " + snapshot.sourceAccountCount() + " accounts | "
                    + snapshot.sourceAccountSpurs() + " Spurs | SHA-256 " + shortHash(snapshot.sourceHash()));
            lines.add("Dedicated servers can use /ubs admin migrate numismatics file <server path>.");
        } else if (page == 1) {
            lines.add("The default rate is $1.00 per Spur. Rates use whole cents and are fixed for this migration.");
            lines.add("Full Economy converts accounts, coins, nested inventories, and bound bank cards.");
        } else if (page == 2) {
            lines.add("Accounts: " + snapshot.sourceAccountCount() + " ($" + snapshot.sourceAccountValue() + ")");
            lines.add("Physical: " + snapshot.physicalCoinItems() + " coin items ($" + snapshot.physicalValue()
                    + ") | Cards: " + (snapshot.boundCards() + snapshot.blankCards()));
            lines.add("Candidate data: " + snapshot.candidatePlayerFiles() + " player files, "
                    + snapshot.candidateChunks() + " chunks");
            appendIssues(lines);
        } else if (page == 3) {
            lines.add(snapshot.status());
            lines.add("A verified backup is created before UBS credits any account or changes an inventory.");
            lines.add("Non-operators are disconnected and world mutation remains locked until completion or rollback.");
            appendIssues(lines);
        } else {
            lines.add(snapshot.status());
            if (!snapshot.failure().isBlank()) lines.add("Failure: " + snapshot.failure());
            lines.add("Progress: " + snapshot.progressCurrent() + " / " + snapshot.progressTotal());
            if (!snapshot.backupDirectory().isBlank()) lines.add("Backup: " + snapshot.backupDirectory());
            if ("COMPLETE".equals(snapshot.phase())) {
                lines.add("Review migration-report.json, stop the server, remove Numismatics, then restart.");
                lines.add("ID cards are reported but intentionally not converted.");
            }
        }
        drawWrappedLines(graphics, lines, x, detailsY, maxWidth);
        if (snapshot.progressTotal() > 0 && page >= 3) {
            int barY = bodyBottom - 18;
            int barWidth = maxWidth;
            graphics.fill(x, barY, x + barWidth, barY + 5, BG);
            int filled = (int) Math.round(barWidth * Math.min(1.0D,
                    (double) snapshot.progressCurrent() / Math.max(1, snapshot.progressTotal())));
            graphics.fill(x, barY, x + filled, barY + 5, GREEN);
        }
    }

    private void renderPolicyLabels(GuiGraphics graphics) {
        int x = panelLeft + 24;
        int width = panelWidth - 48;
        int col = panelWidth < 600 ? width : (width - 12) / 2;
        int rightX = panelWidth < 600 ? x : x + col + 12;
        int fieldY = bodyTop + 42;
        int scopeY = fieldY + 50;
        int cardsY = panelWidth < 600 ? scopeY + 50 : fieldY;
        int unsafeY = cardsY + 50;
        graphics.drawString(font, "Exchange rate (whole cents per Spur)", x, fieldY - 13, MUTED, false);
        graphics.drawString(font, "Conversion scope", x, scopeY - 13, MUTED, false);
        graphics.drawString(font, "Numismatics bank cards", rightX, cardsY - 13, MUTED, false);
        graphics.drawString(font, "Account-only uninstall acknowledgement", rightX, unsafeY - 13, MUTED, false);
    }

    private void appendIssues(List<String> lines) {
        for (String blocker : snapshot.blockers()) lines.add("BLOCKED: " + blocker);
        for (String warning : snapshot.warnings()) lines.add("Warning: " + warning);
    }

    private void drawWrappedLines(GuiGraphics graphics, List<String> values, int x, int y, int maxWidth) {
        int cursor = y;
        int maxY = bodyBottom - (snapshot.feedback().isBlank() ? 24 : 54);
        for (String value : values) {
            for (var line : font.split(Component.literal(value), maxWidth)) {
                if (cursor + 9 > maxY) return;
                int color = value.startsWith("BLOCKED") || value.startsWith("Failure") ? RED
                        : value.startsWith("Warning") ? GOLD : MUTED;
                graphics.drawString(font, line, x, cursor, color, false);
                cursor += 10;
            }
            cursor += 2;
        }
    }

    private boolean canOpenPage(int target) {
        if (target == 0) return true;
        if (target == 1) return !snapshot.sourceHash().isBlank();
        if (target == 2) return !snapshot.sourceHash().isBlank();
        if (target == 3) return snapshot.authoritativeScan() || "READY".equals(snapshot.phase())
                || running(snapshot.phase()) || snapshot.maintenanceLocked();
        return "COMPLETE".equals(snapshot.phase()) || snapshot.maintenanceLocked()
                || "FAILED".equals(snapshot.phase());
    }

    private String phaseLabel() {
        return pretty(snapshot.phase()) + (snapshot.maintenanceLocked() ? " | MAINTENANCE LOCK" : "");
    }

    private static int suggestedPage(NumismaticsMigrationSnapshot snapshot) {
        if (snapshot.sourceHash().isBlank()) return 0;
        if ("SOURCE_READY".equals(snapshot.phase())) return 1;
        if ("READY".equals(snapshot.phase()) || "PREFLIGHT_RUNNING".equals(snapshot.phase())) return 2;
        if (running(snapshot.phase())) return 3;
        if ("COMPLETE".equals(snapshot.phase()) || "FAILED".equals(snapshot.phase())) return 4;
        return 1;
    }

    private static boolean running(String phase) {
        try {
            return NumismaticsMigrationPhase.valueOf(phase).running();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String pretty(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String shortHash(String value) {
        return value == null || value.length() <= 12 ? value : value.substring(0, 12) + "...";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
