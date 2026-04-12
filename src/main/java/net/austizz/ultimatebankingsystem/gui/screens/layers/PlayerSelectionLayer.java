package net.austizz.ultimatebankingsystem.gui.screens.layers;

import net.austizz.ultimatebankingsystem.gui.widgets.AtmEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.NineSliceTexturedButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class PlayerSelectionLayer extends AbstractScreenLayer {
    private static final ResourceLocation ATM_BUTTONS = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/atm_buttons.png");
    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 27;
    private static final int ROW_SPACING = 2;
    private static final int PLAYER_HEAD_SIZE = 16;
    private static final int PLAYER_HEAD_RIGHT_PADDING = 6;

    private record PlayerEntry(String name, PlayerInfo info) {}

    private final List<PlayerEntry> allPlayers = new ArrayList<>();
    private final List<PlayerEntry> filteredPlayers = new ArrayList<>();
    private final List<NineSliceTexturedButton> rowButtons = new ArrayList<>();
    private final Consumer<String> onPlayerSelected;
    private final String initialSelectedName;

    private EditBox searchField;
    private int scrollIndex;
    private String selectedName;

    public PlayerSelectionLayer(Minecraft minecraft,
                                String initialSelectedName,
                                Consumer<String> onPlayerSelected) {
        super(minecraft);
        this.initialSelectedName = initialSelectedName == null ? "" : initialSelectedName;
        this.onPlayerSelected = onPlayerSelected;
        this.selectedName = this.initialSelectedName;
    }

    @Override
    protected void onInit() {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int panelHeight = bankScreen.getPanelHeight();
        int listLeft = getListLeft();
        int listWidth = getListRight() - getListLeft();

        searchField = new AtmEditBox(font, listLeft + 4, panelTop + 58, listWidth - 8, 20, Component.literal(""));
        searchField.setMaxLength(32);
        searchField.setHint(Component.literal("Search player...").withStyle(ChatFormatting.WHITE));
        searchField.setResponder(value -> applyFilter(value));
        styleEditBox(searchField);
        addWidget(searchField);

        gatherPlayers();
        applyFilter("");

        rowButtons.clear();
        int rowY = getListTop() + 2;
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            final int slot = i;
            NineSliceTexturedButton rowButton = addWidget(new NineSliceTexturedButton(
                    listLeft + 4, rowY,
                    listWidth - 8, ROW_HEIGHT,
                    ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                    4, 4, 4, 4,
                    Component.literal(""),
                    btn -> selectPlayerAtSlot(slot)
            ));
            rowButtons.add(rowButton);
            rowY += ROW_HEIGHT + ROW_SPACING;
        }

        addWidget(new NineSliceTexturedButton(
                panelLeft + 8,
                panelTop + panelHeight - 36,
                56, 22,
                ATM_BUTTONS, 0, 0, 120, 20, 120, 40,
                4, 4, 4, 4,
                Component.literal("Back").withStyle(ChatFormatting.WHITE),
                btn -> bankScreen.popLayer()
        ));

        refreshRows();
    }

    private void gatherPlayers() {
        allPlayers.clear();
        if (minecraft.getConnection() == null) {
            return;
        }

        String selfName = minecraft.player == null ? "" : minecraft.player.getGameProfile().getName();
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getProfile() == null) {
                continue;
            }
            String name = info.getProfile().getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!selfName.isBlank() && name.equalsIgnoreCase(selfName)) {
                continue;
            }
            allPlayers.add(new PlayerEntry(name, info));
        }
        allPlayers.sort(Comparator.comparing(entry -> entry.name().toLowerCase()));
    }

    private void applyFilter(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase();
        filteredPlayers.clear();
        for (PlayerEntry entry : allPlayers) {
            if (query.isEmpty() || entry.name().toLowerCase().contains(query)) {
                filteredPlayers.add(entry);
            }
        }
        scrollIndex = clamp(scrollIndex, 0, getMaxScrollIndex());
        refreshRows();
    }

    private void selectPlayerAtSlot(int slot) {
        int index = scrollIndex + slot;
        if (index < 0 || index >= filteredPlayers.size()) {
            return;
        }

        selectedName = filteredPlayers.get(index).name();
        if (onPlayerSelected != null) {
            onPlayerSelected.accept(selectedName);
        }
        bankScreen.popLayer();
    }

    private void refreshRows() {
        for (int i = 0; i < rowButtons.size(); i++) {
            NineSliceTexturedButton button = rowButtons.get(i);
            int index = scrollIndex + i;
            if (index >= 0 && index < filteredPlayers.size()) {
                button.setMessage(Component.literal(""));
                button.active = true;
                button.visible = true;
            } else {
                button.setMessage(Component.literal(""));
                button.active = false;
                button.visible = false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (filteredPlayers.size() <= VISIBLE_ROWS) {
            return false;
        }
        if (!isInsideList(mouseX, mouseY) || scrollY == 0.0D) {
            return false;
        }

        int delta = scrollY > 0 ? -1 : 1;
        int next = clamp(scrollIndex + delta, 0, getMaxScrollIndex());
        if (next != scrollIndex) {
            scrollIndex = next;
            refreshRows();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int panelLeft = bankScreen.getPanelLeft();
        int panelTop = bankScreen.getPanelTop();
        int panelWidth = bankScreen.getPanelWidth();
        int listLeft = getListLeft();
        int listTop = getListTop();
        int listRight = getListRight();
        int listBottom = getListBottom();

        graphics.drawCenteredString(
                font,
                Component.literal("Select Player").withStyle(ChatFormatting.AQUA),
                panelLeft + panelWidth / 2,
                panelTop + 31,
                0xFFFFFFFF
        );

        drawCenteredFittedString(graphics,
                "Search and choose player (mouse wheel to scroll)",
                panelLeft + panelWidth / 2,
                panelTop + 40,
                panelWidth - 20,
                COLOR_MUTED);

        graphics.drawString(font, "Search", listLeft + 6, panelTop + 50, COLOR_LABEL);
        drawSectionBox(graphics, listLeft, listTop, listRight, listBottom);

        if (filteredPlayers.isEmpty()) {
            graphics.drawCenteredString(
                    font,
                    Component.literal("No players found.").withStyle(ChatFormatting.GRAY),
                    panelLeft + panelWidth / 2,
                    listTop + (listBottom - listTop) / 2 - 4,
                    COLOR_MUTED
            );
        } else {
            int from = scrollIndex + 1;
            int to = Math.min(scrollIndex + VISIBLE_ROWS, filteredPlayers.size());
            graphics.drawCenteredString(
                    font,
                    Component.literal("Showing " + from + "-" + to + " of " + filteredPlayers.size())
                            .withStyle(ChatFormatting.DARK_AQUA),
                    panelLeft + panelWidth / 2,
                    listBottom + 7,
                    0xFF55FFFF
            );
            renderPlayerBlocks(graphics);
        }
    }

    private void renderPlayerBlocks(GuiGraphics graphics) {
        long now = System.currentTimeMillis();

        for (int i = 0; i < rowButtons.size(); i++) {
            NineSliceTexturedButton button = rowButtons.get(i);
            if (!button.visible) {
                continue;
            }

            int playerIndex = scrollIndex + i;
            if (playerIndex < 0 || playerIndex >= filteredPlayers.size()) {
                continue;
            }

            PlayerEntry player = filteredPlayers.get(playerIndex);
            boolean isSelected = selectedName != null && selectedName.equalsIgnoreCase(player.name());
            boolean isHovered = button.isHoveredOrFocused();

            int x1 = button.getX() + 1;
            int y1 = button.getY() + 1;
            int x2 = button.getX() + button.getWidth() - 1;
            int y2 = button.getY() + button.getHeight() - 1;

            drawAnimatedBlock(graphics, x1, y1, x2, y2, i, now, isSelected);
            int borderColor = isSelected ? 0xFFFFE066 : (isHovered ? 0xFF7BCBFF : 0xFF2A4768);
            graphics.fill(x1, y1, x2, y1 + 1, borderColor);
            graphics.fill(x1, y2 - 1, x2, y2, borderColor);
            graphics.fill(x1, y1, x1 + 1, y2, borderColor);
            graphics.fill(x2 - 1, y1, x2, y2, borderColor);

            int headX = x2 - PLAYER_HEAD_SIZE - PLAYER_HEAD_RIGHT_PADDING;
            int headY = y1 + Math.max(0, (y2 - y1 - PLAYER_HEAD_SIZE) / 2);
            int textX = x1 + 6;
            int textWidth = Math.max(16, headX - textX - 8);
            int textY = y1 + Math.max(0, (y2 - y1 - font.lineHeight) / 2);

            graphics.drawString(font, fitToWidth(player.name(), textWidth), textX, textY,
                    isSelected ? 0xFFFFFF99 : 0xFFFFFFFF);

            PlayerFaceRenderer.draw(graphics, player.info().getSkin(), headX, headY, PLAYER_HEAD_SIZE);
        }
    }

    private void drawAnimatedBlock(GuiGraphics graphics,
                                   int x1, int y1, int x2, int y2,
                                   int rowOffset, long now, boolean selected) {
        int height = Math.max(1, y2 - y1);
        float cycle = ((now + (rowOffset * 420L)) % 3200L) / 3200.0F;
        float pulse = cycle <= 0.5F ? (cycle * 2.0F) : ((1.0F - cycle) * 2.0F);
        int baseDark = selected ? 0xE41A4F86 : 0xCC173F6A;
        int baseLight = selected ? 0xF06FCFFF : 0xE25EB8EE;
        int topColor = lerpColor(baseDark, baseLight, pulse);
        int bottomColor = lerpColor(baseLight, baseDark, pulse);

        for (int y = 0; y < height; y++) {
            float ratio = height <= 1 ? 0.0F : (float) y / (float) (height - 1);
            int lineColor = lerpColor(topColor, bottomColor, ratio);
            graphics.fill(x1, y1 + y, x2, y1 + y + 1, lineColor);
        }
    }

    private int getListLeft() {
        return bankScreen.getPanelLeft() + 8;
    }

    private int getListTop() {
        return bankScreen.getPanelTop() + 84;
    }

    private int getListRight() {
        return bankScreen.getPanelLeft() + bankScreen.getPanelWidth() - 8;
    }

    private int getListBottom() {
        return bankScreen.getPanelTop() + bankScreen.getPanelHeight() - 52;
    }

    private int getMaxScrollIndex() {
        return Math.max(0, filteredPlayers.size() - VISIBLE_ROWS);
    }

    private boolean isInsideList(double mouseX, double mouseY) {
        return mouseX >= getListLeft() && mouseX <= getListRight()
                && mouseY >= getListTop() && mouseY <= getListBottom();
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
