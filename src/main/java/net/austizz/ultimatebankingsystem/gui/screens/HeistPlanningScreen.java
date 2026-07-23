package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.client.HeistClientState;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.HeistPlanningActionPayload;
import net.austizz.ultimatebankingsystem.network.HeistPlanningPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public final class HeistPlanningScreen extends Screen {
    private HeistPlanningPayload snapshot;
    private List<HeistClientState.CrewEntry> crew = List.of();
    private List<HeistClientState.TargetEntry> targets = List.of();
    private DesktopEditBox inviteInput;
    private int targetScroll;
    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int splitX;
    private int listTop;
    private int visibleTargets;

    public HeistPlanningScreen(HeistPlanningPayload payload) {
        super(Component.literal("Bank Heist Planning"));
        this.snapshot = payload;
        decode();
    }

    public void update(HeistPlanningPayload payload) {
        snapshot = payload;
        decode();
        if (minecraft != null) init(minecraft, width, height);
    }

    private void decode() {
        crew = HeistClientState.planningCrew(snapshot);
        targets = HeistClientState.targets(snapshot);
    }

    @Override
    protected void init() {
        clearWidgets();
        panelWidth = Math.min(980, Math.max(300, width - 20));
        panelHeight = Math.min(620, Math.max(220, height - 20));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        boolean compact = panelWidth < 620;
        splitX = compact ? panelLeft + Math.max(128, panelWidth * 38 / 100) : panelLeft + panelWidth * 34 / 100;
        listTop = panelTop + 74;
        int footerTop = panelTop + panelHeight - 48;
        visibleTargets = Math.max(1, (footerTop - listTop - 8) / 36);
        targetScroll = Math.max(0, Math.min(targetScroll, Math.max(0, targets.size() - visibleTargets)));

        int leftX = panelLeft + 14;
        int leftW = Math.max(108, splitX - leftX - 10);
        if (snapshot.leader() && "PLANNING".equals(snapshot.phase())) {
            int inputW = Math.max(54, leftW - 72);
            inviteInput = new DesktopEditBox(font, leftX, panelTop + 43, inputW, 20, Component.literal("Online player"));
            inviteInput.setMaxLength(32);
            addRenderableWidget(inviteInput);
            addRenderableWidget(new DesktopButton(leftX + inputW + 5, panelTop + 43, 67, 20,
                    Component.literal("Invite"), 0xFF56C8FF, button -> send("invite", inviteInput.getValue(), "")));
        }

        int targetX = splitX + 10;
        int targetW = panelLeft + panelWidth - 14 - targetX;
        for (int index = 0; index < visibleTargets; index++) {
            int targetIndex = targetScroll + index;
            if (targetIndex >= targets.size()) break;
            HeistClientState.TargetEntry target = targets.get(targetIndex);
            String selected = target.bankName().equals(snapshot.bankName()) && target.premiseId().equals(snapshot.premiseId())
                    ? "SELECTED  " : "";
            String label = selected + target.bankName() + " / " + target.premiseId()
                    + "  [" + target.lootSources() + " sources]";
            DesktopButton button = new DesktopButton(targetX, listTop + index * 36, targetW, 30,
                    Component.literal(label), target.eligible() ? 0xFF48D6A5 : 0xFFFF6E76,
                    ignored -> send("select", target.bankId().toString(), target.premiseId()));
            button.active = snapshot.leader() && target.eligible() && "PLANNING".equals(snapshot.phase());
            addRenderableWidget(button);
        }

        int buttonGap = 6;
        int actionW = Math.max(78, Math.min(142, (panelWidth - 28 - buttonGap * 3) / 4));
        int actionX = panelLeft + 14;
        HeistClientState.CrewEntry localMember = crew.stream().filter(entry -> minecraft != null && minecraft.player != null
                && entry.id().equals(minecraft.player.getUUID())).findFirst().orElse(null);
        boolean localReady = localMember != null && localMember.ready();
        boolean running = "CASING".equals(snapshot.phase()) || "ACTIVE".equals(snapshot.phase())
                || "ESCAPING".equals(snapshot.phase());
        if (localMember != null && !localMember.accepted()) {
            addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                    Component.literal("Accept Invite"), 0xFF48D6A5,
                    ignored -> send("accept", "", "")));
            actionX += actionW + buttonGap;
            addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                    Component.literal("Decline"), 0xFFFF6E76,
                    ignored -> send("decline", "", "")));
            actionX += actionW + buttonGap;
        } else if ("PLANNING".equals(snapshot.phase())) {
            addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                    Component.literal(localReady ? "Not Ready" : "Ready"), localReady ? 0xFFFFBE55 : 0xFF48D6A5,
                    ignored -> send(localReady ? "unready" : "ready", "", "")));
            actionX += actionW + buttonGap;
            if (snapshot.leader()) {
                addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                        Component.literal("Start Heist"), 0xFFFFB84D, ignored -> send("start", "", "")));
                actionX += actionW + buttonGap;
            }
        } else if ("COUNTDOWN".equals(snapshot.phase()) && snapshot.leader()) {
            addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                    Component.literal("Cancel Countdown"), 0xFFFF6E76, ignored -> send("cancel", "", "")));
            actionX += actionW + buttonGap;
        } else if (running) {
            addRenderableWidget(new DesktopButton(actionX, footerTop + 10, actionW, 26,
                    Component.literal("Abandon Heist"), 0xFFFF6E76, ignored -> send("abandon", "", "")));
            actionX += actionW + buttonGap;
        }
        if (!running) {
            addRenderableWidget(new DesktopButton(panelLeft + panelWidth - 14 - actionW, footerTop + 10, actionW, 26,
                    Component.literal("Leave Crew"), 0xFF8FA6BD, ignored -> send("leave", "", "")));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xE8080D14);
        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + panelWidth + 2, panelTop + panelHeight + 2, 0xFF32475C);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xFF111A24);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 34, 0xFF1B2938);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + 5, panelTop + 34, 0xFFFFB84D);
        graphics.fill(splitX, panelTop + 35, splitX + 1, panelTop + panelHeight - 49, 0xFF30475D);
        graphics.fill(panelLeft, panelTop + panelHeight - 49, panelLeft + panelWidth, panelTop + panelHeight - 48, 0xFF30475D);

        graphics.drawString(font, "BANK HEIST PLANNING", panelLeft + 14, panelTop + 12, 0xFFF5F7FA, false);
        String phase = snapshot.phase().replace('_', ' ');
        graphics.drawString(font, phase, panelLeft + panelWidth - 14 - font.width(phase), panelTop + 12,
                "COUNTDOWN".equals(snapshot.phase()) ? 0xFFFFB84D : 0xFF8FA6BD, false);
        graphics.drawString(font, "CREW", panelLeft + 14, listTop - 19, 0xFF8FA6BD, false);
        graphics.drawString(font, "TARGET PREMISES", splitX + 10, listTop - 19, 0xFF8FA6BD, false);

        int crewY = listTop;
        for (HeistClientState.CrewEntry member : crew) {
            int color = !member.online() ? 0xFF697582 : member.ready() ? 0xFF48D6A5 : 0xFFFFBE55;
            String state = !member.accepted() ? "INVITED" : member.ready() ? "READY" : "NOT READY";
            graphics.fill(panelLeft + 14, crewY, splitX - 10, crewY + 29, 0xFF182634);
            graphics.fill(panelLeft + 14, crewY, panelLeft + 17, crewY + 29, color);
            graphics.drawString(font, member.name(), panelLeft + 23, crewY + 5, 0xFFF5F7FA, false);
            graphics.drawString(font, state, panelLeft + 23, crewY + 16, color, false);
            crewY += 35;
        }
        if (crew.isEmpty()) graphics.drawString(font, "No crew members", panelLeft + 14, crewY, 0xFF8FA6BD, false);

        if (targets.isEmpty()) {
            graphics.drawString(font, "No claimed bank premises are available.", splitX + 10, listTop + 8, 0xFFFF7D84, false);
        } else {
            int hoverIndex = (mouseY - listTop) / 36 + targetScroll;
            if (mouseX >= splitX + 10 && hoverIndex >= targetScroll && hoverIndex < targets.size()) {
                HeistClientState.TargetEntry hovered = targets.get(hoverIndex);
                if (!hovered.eligible() && !hovered.blocker().isBlank()) {
                    graphics.renderTooltip(font, Component.literal(hovered.blocker()), mouseX, mouseY);
                }
            }
        }
        if (!snapshot.status().isBlank()) {
            graphics.drawString(font, snapshot.status(), panelLeft + 14, panelTop + panelHeight - 62,
                    snapshot.status().toLowerCase().contains("invalid") || snapshot.status().toLowerCase().contains("cannot")
                            ? 0xFFFF7D84 : 0xFF9ED6FF, false);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
        if (mouseX >= splitX && targets.size() > visibleTargets) {
            targetScroll = Math.max(0, Math.min(targets.size() - visibleTargets,
                    targetScroll + (scrollDelta < 0 ? 1 : -1)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private void send(String action, String arg1, String arg2) {
        PacketDistributor.sendToServer(new HeistPlanningActionPayload(action, arg1, arg2));
    }
}
