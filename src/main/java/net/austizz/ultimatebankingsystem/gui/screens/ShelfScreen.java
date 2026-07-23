package net.austizz.ultimatebankingsystem.gui.screens;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.client.UbsClientTranslations;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.network.ShelfActionPayload;
import net.austizz.ultimatebankingsystem.network.ShelfActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.ShelfOpenPayload;
import net.austizz.ultimatebankingsystem.network.ShelfSlotSummary;
import net.austizz.ultimatebankingsystem.network.ShelfUnitSummary;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayType;
import net.austizz.ultimatebankingsystem.shelf.ShelfPrice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.Registries;

import java.util.ArrayList;
import java.util.List;

public class ShelfScreen extends Screen {
    private static final int MAX_VISIBLE_SLOTS = 4;

    private ShelfOpenPayload payload;
    private final List<ShelfUnitSummary> shelves = new ArrayList<>();
    private int selectedShelfIndex;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentLeft;
    private int contentRight;
    private int contentTop;
    private int contentBottom;
    private int slotsAreaLeft;
    private int slotsAreaRight;
    private int slotsAreaTop;
    private int slotsAreaBottom;
    private int closeRowY;
    private int feedbackY;
    private int slotBlockHeight;
    private int slotGap;
    private int maxSlotScroll;
    private int slotScroll;
    private boolean narrowHeader;
    private boolean selectedCanManage;
    private boolean selectedCreativeShelf;
    private boolean selectedShopMode;
    private int ownerLineX;
    private int ownerLineY;
    private int accessLineX;
    private int accessLineY;
    private boolean showOwnerLines;
    private final SlotLayout[] slotLayouts = new SlotLayout[MAX_VISIBLE_SLOTS];

    private DesktopButton prevShelfButton;
    private DesktopButton nextShelfButton;
    private DesktopButton removeShelfButton;
    private DesktopButton spinToggleButton;
    private DesktopButton modularLayoutButton;
    private DesktopButton modeToggleButton;
    private DesktopButton closeButton;

    private final DesktopEditBox[] priceInputs = new DesktopEditBox[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] setFromHandButtons = new DesktopButton[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] savePriceButtons = new DesktopButton[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] setStockButtons = new DesktopButton[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] takeStockButtons = new DesktopButton[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] clearSlotButtons = new DesktopButton[MAX_VISIBLE_SLOTS];
    private final DesktopButton[] editPositionButtons = new DesktopButton[MAX_VISIBLE_SLOTS];

    private boolean inventoryPickerOpen;
    private int pickerTargetShelfSlot = -1;
    private int pickerLeft;
    private int pickerTop;
    private int pickerWidth;
    private int pickerHeight;
    private int pickerGridLeft;
    private int pickerGridTop;
    private int pickerCancelX;
    private int pickerCancelY;
    private int pickerCancelW;
    private int pickerCancelH;
    private final List<InventoryChoice> inventoryChoices = new ArrayList<>();
    private PickerMode pickerMode = PickerMode.SET_ITEM;

    private boolean feedbackSuccess = true;
    private String feedbackMessage = "";
    private long feedbackUntilMillis;

    public ShelfScreen(ShelfOpenPayload payload) {
        super(UbsTranslations.literal("Shelf Manager"));
        applyPayload(payload);
    }

    public void refresh(ShelfOpenPayload newPayload) {
        applyPayload(newPayload);
        if (this.minecraft != null) {
            this.init(this.minecraft, this.width, this.height);
        }
    }

    public void handleActionResponse(ShelfActionResponsePayload response) {
        if (response == null) {
            return;
        }
        feedbackSuccess = response.success();
        feedbackMessage = response.message() == null ? "" : response.message();
        feedbackUntilMillis = System.currentTimeMillis() + 4500L;
    }

    @Override
    protected void init() {
        clearWidgets();

        int frameMargin = (this.width <= 420 || this.height <= 280) ? 4 : 10;
        panelWidth = Math.max(300, this.width - frameMargin * 2);
        panelHeight = Math.max(220, this.height - frameMargin * 2);
        panelWidth = Math.min(panelWidth, Math.max(220, this.width - 2));
        panelHeight = Math.min(panelHeight, Math.max(180, this.height - 2));
        panelLeft = (this.width - panelWidth) / 2;
        panelTop = (this.height - panelHeight) / 2;
        boolean compactVertical = panelHeight < 290;
        boolean ultraCompact = panelHeight < 230;

        contentLeft = panelLeft + 12;
        contentRight = panelLeft + panelWidth - 12;
        contentTop = panelTop + (compactVertical ? 40 : 48);
        closeRowY = panelTop + panelHeight - (compactVertical ? 30 : 34);
        feedbackY = closeRowY + 6;
        contentBottom = closeRowY - 8;
        int contentWidth = contentRight - contentLeft;

        narrowHeader = contentWidth < 620;
        int controlsY = contentTop + 6;
        int pairGap = 8;
        int navWidth = narrowHeader
                ? Math.max(92, (contentWidth - pairGap) / 2)
                : Math.max(98, Math.min(132, contentWidth / 6));
        int removeWidth = narrowHeader
                ? contentWidth
                : Math.max(150, Math.min(230, contentWidth / 3));
        int spinWidth = narrowHeader
                ? contentWidth
                : Math.max(130, Math.min(180, contentWidth / 4));

        prevShelfButton = addRenderableWidget(new DesktopButton(contentLeft, controlsY, navWidth, 24,
                UbsTranslations.literal("Prev Shelf"), 0xFF7BC2FF, btn -> stepShelf(-1)));
        nextShelfButton = addRenderableWidget(new DesktopButton(contentLeft + navWidth + pairGap, controlsY, navWidth, 24,
                UbsTranslations.literal("Next Shelf"), 0xFF7BC2FF, btn -> stepShelf(1)));
        int removeX = narrowHeader ? contentLeft : contentRight - removeWidth;
        int removeY = narrowHeader ? controlsY + 28 : controlsY;
        removeShelfButton = addRenderableWidget(new DesktopButton(removeX, removeY, removeWidth, 24,
                UbsTranslations.literal("Remove Selected Shelf"), 0xFFE28787, btn -> submitAction("remove_shelf", -1)));

        ShelfUnitSummary selected = getSelectedShelf();
        selectedCanManage = selected != null && selected.canManage();
        selectedCreativeShelf = selected != null && selected.creativeShelf();
        selectedShopMode = selected == null || selected.shopMode();
        removeShelfButton.active = selectedCanManage;

        int spinX = narrowHeader ? contentLeft : (removeX - spinWidth - 8);
        int spinY = narrowHeader ? (removeY + 28) : controlsY;
        String spinLabel = selected != null && selected.spinEnabled() ? "Spin: ON" : "Spin: OFF";
        spinToggleButton = addRenderableWidget(new DesktopButton(
                spinX,
                spinY,
                spinWidth,
                24,
                UbsTranslations.literal(spinLabel),
                0xFF9CC0FF,
                btn -> submitAction("toggle_spin", -1)
        ));
        boolean showSpin = selected != null && selected.spinCapable();
        spinToggleButton.visible = showSpin;
        spinToggleButton.active = showSpin && selectedCanManage;

        boolean showModularLayout = isModularShelf(selected);
        int layoutY = narrowHeader
                ? ((showSpin ? spinY : removeY) + 28)
                : (controlsY + 28);
        int layoutWidth = narrowHeader
                ? contentWidth
                : Math.max(140, navWidth * 2 + pairGap);
        modularLayoutButton = addRenderableWidget(new DesktopButton(
                contentLeft,
                layoutY,
                layoutWidth,
                24,
                UbsTranslations.literal(isSelectedModularExpanded() ? "Layout: 4 Items (2x2)" : "Layout: 2 Items (2x1)"),
                0xFF98C7FF,
                btn -> submitAction("toggle_modular_layout", -1)
        ));
        modularLayoutButton.visible = showModularLayout;
        modularLayoutButton.active = showModularLayout && selectedCanManage;
        int modularHeaderOffset = showModularLayout ? 28 : 0;

        showOwnerLines = !ultraCompact;
        if (showOwnerLines) {
            ownerLineX = narrowHeader ? contentLeft : nextShelfButton.getX() + nextShelfButton.getWidth() + 12;
            ownerLineY = narrowHeader ? controlsY + 56 + modularHeaderOffset : controlsY + (compactVertical ? 20 : 4) + modularHeaderOffset;
            accessLineX = ownerLineX;
            accessLineY = ownerLineY + 13;
        } else {
            ownerLineX = contentLeft;
            ownerLineY = -1000;
            accessLineX = contentLeft;
            accessLineY = -1000;
        }

        slotsAreaLeft = contentLeft + 2;
        slotsAreaRight = contentRight - 2;
        if (showOwnerLines) {
            slotsAreaTop = narrowHeader ? controlsY + 86 + modularHeaderOffset : controlsY + (compactVertical ? 52 : 36) + modularHeaderOffset;
        } else {
            slotsAreaTop = narrowHeader ? controlsY + 54 + modularHeaderOffset : controlsY + 30 + modularHeaderOffset;
        }
        slotsAreaBottom = contentBottom - 3;
        if (slotsAreaBottom <= slotsAreaTop + 8) {
            slotsAreaBottom = slotsAreaTop + 8;
        }

        slotBlockHeight = narrowHeader ? 148 : 138;
        slotGap = 10;

        for (int slot = 0; slot < MAX_VISIBLE_SLOTS; slot++) {
            final int slotIndex = slot;
            ShelfSlotSummary slotData = findSlot(selected, slotIndex);

            DesktopEditBox priceInput = new DesktopEditBox(font,
                    0,
                    0,
                    100,
                    22,
                    UbsTranslations.literal("Price"));
            priceInput.setMaxLength(16);
            priceInput.setValue(ShelfPrice.displayInputFromCents(slotData == null ? 0L : Math.max(0L, slotData.priceDollars())));
            addRenderableWidget(priceInput);
            priceInputs[slot] = priceInput;

            DesktopButton setFromHand = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    24,
                    UbsTranslations.literal("Set From Inventory"),
                    0xFF8DD9FF,
                    btn -> openInventoryPicker(slotIndex)
            ));
            setFromHand.active = selectedCanManage;
            setFromHandButtons[slot] = setFromHand;

            DesktopButton savePrice = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    22,
                    UbsTranslations.literal("Save Price"),
                    0xFF87E1AA,
                    btn -> submitAction("save_price", slotIndex)
            ));
            savePrice.active = selectedCanManage && selectedShopMode;
            savePriceButtons[slot] = savePrice;

            DesktopButton setStock = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    22,
                    UbsTranslations.literal("Restock"),
                    0xFF8DBEFF,
                    btn -> submitAction("restock_from_stockroom", slotIndex)
            ));
            setStock.active = selectedCanManage && selectedShopMode && !selectedCreativeShelf;
            setStockButtons[slot] = setStock;

            DesktopButton takeStock = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    22,
                    UbsTranslations.literal("Take Stock"),
                    0xFFD6AE7A,
                    btn -> submitAction("take_stock_back", slotIndex)
            ));
            takeStock.active = selectedCanManage && selectedShopMode && !selectedCreativeShelf;
            takeStockButtons[slot] = takeStock;

            DesktopButton clear = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    24,
                    UbsTranslations.literal("Clear"),
                    0xFFE49A9A,
                    btn -> submitAction("clear_slot", slotIndex)
            ));
            clear.active = selectedCanManage;
            clearSlotButtons[slot] = clear;

            DesktopButton editPosition = addRenderableWidget(new DesktopButton(
                    0,
                    0,
                    100,
                    24,
                    UbsTranslations.literal("Edit Position"),
                    0xFFB8A5F2,
                    btn -> openTransformEditor(slotIndex)
            ));
            editPosition.active = selectedCanManage;
            editPositionButtons[slot] = editPosition;
        }

        closeButton = addRenderableWidget(new DesktopButton(contentRight - 110, closeRowY, 110, 22,
                UbsTranslations.literal("Close"), 0xFF8ABAF1, btn -> onClose()));
        int modeWidth = 168;
        int modeX = Math.max(contentLeft, closeButton.getX() - modeWidth - 8);
        modeToggleButton = addRenderableWidget(new DesktopButton(
                modeX,
                closeRowY,
                modeWidth,
                22,
                UbsTranslations.literal(selectedShopMode ? "Mode: Shop" : "Mode: Regular"),
                selectedShopMode ? 0xFF8FC0FF : 0xFF98B0C8,
                btn -> submitAction("toggle_shop_mode", -1)
        ));
        modeToggleButton.visible = selected != null;
        modeToggleButton.active = selectedCanManage;

        buildSlotLayouts();
        layoutInventoryPicker();
        if (inventoryPickerOpen) {
            rebuildInventoryChoices();
        }
        updateNavButtons();
    }

    private void buildSlotLayouts() {
        int visibleSlots = getVisibleSlotCount();
        int totalHeight = 0;
        for (int slot = 0; slot < MAX_VISIBLE_SLOTS; slot++) {
            if (slot >= visibleSlots) {
                slotLayouts[slot] = null;
                hideSlotWidgets(slot);
                continue;
            }
            int baseY = slotsAreaTop + slot * (slotBlockHeight + slotGap);
            SlotLayout layout = new SlotLayout();
            layout.baseY = baseY;
            layout.height = slotBlockHeight;
            slotLayouts[slot] = layout;
            totalHeight = (baseY - slotsAreaTop) + slotBlockHeight;
        }
        maxSlotScroll = Math.max(0, totalHeight - Math.max(1, slotsAreaBottom - slotsAreaTop));
        if (slotScroll > maxSlotScroll) {
            slotScroll = maxSlotScroll;
        }
        applySlotScrollToWidgets();
    }

    private void applySlotScrollToWidgets() {
        int visibleSlots = getVisibleSlotCount();
        int blockX = slotsAreaLeft + 2;
        int blockW = Math.max(120, (slotsAreaRight - slotsAreaLeft) - 4);
        int iconX = blockX + 8;
        int formX = iconX + 30;
        int formRight = blockX + blockW - 8;
        int stockSlotSize = 20;

        for (int slot = 0; slot < MAX_VISIBLE_SLOTS; slot++) {
            SlotLayout layout = slotLayouts[slot];
            if (layout == null || slot >= visibleSlots) {
                hideSlotWidgets(slot);
                continue;
            }
            int y = layout.baseY - slotScroll;
            layout.y = y;
            layout.blockX = blockX;
            layout.blockW = blockW;
            layout.iconX = iconX;
            layout.iconY = y + 10;
            layout.stockSlotX = formX + 44;
            layout.stockSlotY = y + 64;

            int savePriceW = Math.max(84, Math.min(112, blockW / 4));
            int saveStockW = Math.max(96, Math.min(136, blockW / 3));

            int priceLabelW = 38;
            int priceInputW = Math.max(32, formRight - formX - priceLabelW - 6 - savePriceW);
            int priceInputX = formX + priceLabelW + 6;
            int savePriceX = priceInputX + priceInputW + 6;
            int inputY = y + 40;

            int stockLabelW = 44;
            int setStockX = formX + stockLabelW + 6 + stockSlotSize + 6;
            int setStockW = Math.max(24, Math.min(saveStockW, formRight - setStockX));
            int stockY = y + 63;

            int actionGap = 6;
            int actionW = Math.max(34, (formRight - formX - actionGap * 3) / 4);
            int takeStockX = formX + actionW + actionGap;
            int clearX = takeStockX + actionW + actionGap;
            int editX = clearX + actionW + actionGap;
            int actionY = y + 92;

            setWidgetBounds(priceInputs[slot], priceInputX, inputY, priceInputW);
            setWidgetBounds(savePriceButtons[slot], savePriceX, inputY, savePriceW);
            setWidgetBounds(setStockButtons[slot], setStockX, stockY, setStockW);
            setWidgetBounds(setFromHandButtons[slot], formX, actionY, actionW);
            setWidgetBounds(takeStockButtons[slot], takeStockX, actionY, actionW);
            setWidgetBounds(clearSlotButtons[slot], clearX, actionY, actionW);
            setWidgetBounds(editPositionButtons[slot], editX, actionY, actionW);

            clampWidgetToArea(priceInputs[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(savePriceButtons[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(setStockButtons[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(setFromHandButtons[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(takeStockButtons[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(clearSlotButtons[slot], blockX + 4, formRight - 2);
            clampWidgetToArea(editPositionButtons[slot], blockX + 4, formRight - 2);

            boolean priceVisible = isRectInsideSlotsArea(priceInputs[slot].getX(), priceInputs[slot].getY(), priceInputs[slot].getWidth(), 22);
            boolean savePriceVisible = isRectInsideSlotsArea(savePriceButtons[slot].getX(), savePriceButtons[slot].getY(), savePriceButtons[slot].getWidth(), 22);
            boolean setStockVisible = isRectInsideSlotsArea(setStockButtons[slot].getX(), setStockButtons[slot].getY(), setStockButtons[slot].getWidth(), 22);
            boolean setFromVisible = isRectInsideSlotsArea(setFromHandButtons[slot].getX(), setFromHandButtons[slot].getY(), setFromHandButtons[slot].getWidth(), 24);
            boolean takeStockVisible = isRectInsideSlotsArea(takeStockButtons[slot].getX(), takeStockButtons[slot].getY(), takeStockButtons[slot].getWidth(), 22);
            boolean clearVisible = isRectInsideSlotsArea(clearSlotButtons[slot].getX(), clearSlotButtons[slot].getY(), clearSlotButtons[slot].getWidth(), 24);
            boolean editVisible = isRectInsideSlotsArea(editPositionButtons[slot].getX(), editPositionButtons[slot].getY(), editPositionButtons[slot].getWidth(), 24);
            boolean shopControls = selectedShopMode;

            priceInputs[slot].visible = priceVisible && shopControls;
            savePriceButtons[slot].visible = savePriceVisible && shopControls;
            setStockButtons[slot].visible = setStockVisible && shopControls;
            setFromHandButtons[slot].visible = setFromVisible;
            takeStockButtons[slot].visible = takeStockVisible && shopControls;
            clearSlotButtons[slot].visible = clearVisible;
            editPositionButtons[slot].visible = editVisible;

            priceInputs[slot].active = priceVisible && shopControls && selectedCanManage;
            savePriceButtons[slot].active = savePriceVisible && shopControls && selectedCanManage;
            setStockButtons[slot].active = setStockVisible && shopControls && selectedCanManage && !selectedCreativeShelf;
            setFromHandButtons[slot].active = setFromVisible && selectedCanManage;
            takeStockButtons[slot].active = takeStockVisible && shopControls && selectedCanManage && !selectedCreativeShelf;
            clearSlotButtons[slot].active = clearVisible && selectedCanManage;
            editPositionButtons[slot].active = editVisible && selectedCanManage;
        }
    }

    private int getVisibleSlotCount() {
        ShelfUnitSummary selected = getSelectedShelf();
        if (selected == null || selected.slots() == null || selected.slots().isEmpty()) {
            return 1;
        }
        return Math.max(1, Math.min(MAX_VISIBLE_SLOTS, selected.slots().size()));
    }

    private void hideSlotWidgets(int slot) {
        if (slot < 0 || slot >= MAX_VISIBLE_SLOTS) {
            return;
        }
        if (priceInputs[slot] != null) {
            priceInputs[slot].visible = false;
            priceInputs[slot].active = false;
        }
        if (savePriceButtons[slot] != null) {
            savePriceButtons[slot].visible = false;
            savePriceButtons[slot].active = false;
        }
        if (setStockButtons[slot] != null) {
            setStockButtons[slot].visible = false;
            setStockButtons[slot].active = false;
        }
        if (setFromHandButtons[slot] != null) {
            setFromHandButtons[slot].visible = false;
            setFromHandButtons[slot].active = false;
        }
        if (takeStockButtons[slot] != null) {
            takeStockButtons[slot].visible = false;
            takeStockButtons[slot].active = false;
        }
        if (clearSlotButtons[slot] != null) {
            clearSlotButtons[slot].visible = false;
            clearSlotButtons[slot].active = false;
        }
        if (editPositionButtons[slot] != null) {
            editPositionButtons[slot].visible = false;
            editPositionButtons[slot].active = false;
        }
    }

    private static void setWidgetBounds(net.minecraft.client.gui.components.AbstractWidget widget,
                                        int x,
                                        int y,
                                        int width) {
        if (widget == null) {
            return;
        }
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(Math.max(8, width));
    }

    private static void clampWidgetToArea(net.minecraft.client.gui.components.AbstractWidget widget, int minX, int maxX) {
        if (widget == null) {
            return;
        }
        int x = Math.max(minX, widget.getX());
        int right = Math.min(maxX, x + widget.getWidth());
        int width = Math.max(8, right - x);
        widget.setX(x);
        widget.setWidth(width);
    }

    @Override
    public void tick() {
        super.tick();
        if (System.currentTimeMillis() > feedbackUntilMillis) {
            feedbackMessage = "";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inventoryPickerOpen && keyCode == 256) {
            inventoryPickerOpen = false;
            return true;
        }
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
        if (!inventoryPickerOpen
                && inside(mouseX, mouseY, slotsAreaLeft, slotsAreaTop, Math.max(1, slotsAreaRight - slotsAreaLeft), Math.max(1, slotsAreaBottom - slotsAreaTop))
                && maxSlotScroll > 0) {
            int delta = (int) Math.round(scrollDelta * 24.0D);
            int next = clamp(slotScroll - delta, 0, maxSlotScroll);
            if (next != slotScroll) {
                slotScroll = next;
                applySlotScrollToWidgets();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xFF0A1B30);

        int frameRight = panelLeft + panelWidth;
        int frameBottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, frameRight + 2, frameBottom + 2, 0xFF2D4C74);
        graphics.fill(panelLeft, panelTop, frameRight, frameBottom, 0xFF1D3558);
        graphics.fill(panelLeft + 1, panelTop + 1, frameRight - 1, panelTop + 30, 0xFF7DA6D7);
        graphics.fill(contentLeft, contentTop, contentRight, contentBottom, 0xFF10263F);

        graphics.drawString(font, tr("Shelf Manager"), panelLeft + 12, panelTop + 11, 0xFFF6FCFF, false);

        ShelfUnitSummary selected = getSelectedShelf();
        String shelfType = selected == null
                ? "Shelf"
                : isModularShelf(selected)
                ? (selected.creativeShelf() ? "Creative Modular Display" : "Modular Display")
                : selected.spinCapable()
                ? "Display Table"
                : (selected.slots() != null && selected.slots().size() == 1)
                ? "Large Display Table"
                : (selected.creativeShelf() ? "Creative Shelf" : "Shop Shelf");
        String selectedTitleRaw = selected == null
                ? "No connected shelves"
                : "Selected " + shelfType + " "
                + (selectedShelfIndex + 1) + "/" + shelves.size() + " | " + selected.posKey();
        graphics.drawString(font, fitText(selectedTitleRaw, contentRight - contentLeft - 2), panelLeft + 14, panelTop + 36, 0xFFD4EBFF, false);
        if (selected != null) {
            String modeLabel = selected.shopMode() ? "Mode: Shop" : "Mode: Regular Display";
            graphics.drawString(font, tr(modeLabel), panelLeft + 14, panelTop + 48, selected.shopMode() ? 0xFFAED8FF : 0xFFC9D8E6, false);
        }

        ItemStack hoverStack = ItemStack.EMPTY;
        if (selected != null) {
            int visibleSlots = getVisibleSlotCount();
            if (showOwnerLines) {
                String owner = selected.ownerName() == null || selected.ownerName().isBlank()
                        ? "Unknown"
                        : selected.ownerName();
                graphics.drawString(font, fitText("Owner: " + owner, Math.max(80, contentRight - ownerLineX - 6)), ownerLineX, ownerLineY, 0xFFC8E7FF, false);
                graphics.drawString(font,
                        selected.canManage() ? tr("Access: Manage") : tr("Access: View only"),
                        accessLineX,
                        accessLineY,
                        selected.canManage() ? 0xFF8CFFBA : 0xFFFFB9B9,
                        false);
            }

            graphics.enableScissor(slotsAreaLeft, slotsAreaTop, slotsAreaRight, slotsAreaBottom);
            for (int slot = 0; slot < MAX_VISIBLE_SLOTS; slot++) {
                SlotLayout layout = slotLayouts[slot];
                if (layout == null) {
                    continue;
                }
                int y = layout.y;
                if (y > slotsAreaBottom || y + layout.height < slotsAreaTop) {
                    continue;
                }
                ShelfSlotSummary slotData = findSlot(selected, slot);
                ItemStack slotStack = stackFromSlot(slotData);
                boolean hasItem = slotData != null && !slotStack.isEmpty();
                boolean configured = slotData != null && slotData.configured() && hasItem;
                int blockLeft = layout.blockX;
                int blockRight = layout.blockX + layout.blockW;
                int blockBottom = y + layout.height;
                graphics.fill(blockLeft, y, blockRight, blockBottom, 0x9F1F3F61);
                graphics.fill(blockLeft + 1, y + 1, blockRight - 1, y + 2, 0x4489C8FF);

                int slotBoxX = layout.iconX;
                int slotBoxY = layout.iconY;
                graphics.fill(slotBoxX, slotBoxY, slotBoxX + 20, slotBoxY + 20, hasItem ? 0xFF335A80 : 0xFF33404D);
                graphics.fill(slotBoxX + 1, slotBoxY + 1, slotBoxX + 19, slotBoxY + 19, hasItem ? 0xFF204669 : 0xFF212B36);
                if (hasItem) {
                    graphics.renderItem(slotStack, slotBoxX + 2, slotBoxY + 2);
                    graphics.renderItemDecorations(font, slotStack, slotBoxX + 2, slotBoxY + 2);
                } else {
                    graphics.drawString(font, "-", slotBoxX + 7, slotBoxY + 6, 0xFF9CB0C4, false);
                }

                if (selectedShopMode
                        && mouseX >= slotBoxX && mouseX < slotBoxX + 20
                        && mouseY >= slotBoxY && mouseY < slotBoxY + 20
                        && hasItem) {
                    hoverStack = slotStack;
                }

                String slotLabel = visibleSlots == 1 ? "Display Item" : ("Shelf " + (slot + 1));
                String shelfLabel = slotLabel + (configured ? "" : " (Not configured)");
                graphics.drawString(font, tr(shelfLabel), slotBoxX + 28, y + 8, 0xFFE7F5FF, false);
                String itemLine = hasItem
                        ? tr("Item ") + fitText(slotData == null ? slotStack.getHoverName().getString() : slotData.itemName(), Math.max(60, blockRight - (slotBoxX + 34)))
                        : tr("Item -");
                graphics.drawString(font, itemLine, slotBoxX + 28, y + 19, hasItem ? 0xFFD2E9FF : 0xFF9CB0C4, false);

                if (selectedShopMode) {
                    String price = configured
                            ? (Math.max(0L, slotData.priceDollars()) == 0L
                            ? "Free"
                            : "$" + ShelfPrice.abbreviateFromCents(Math.max(0L, slotData.priceDollars())))
                            : "$0";
                    String stock = configured
                            ? (selectedCreativeShelf ? "Infinite" : String.valueOf(Math.max(0, slotData.stockCount())))
                            : "0";
                    graphics.drawString(font, tr("Price ") + tr(price), slotBoxX + 28, y + 30, 0xFFF7E38A, false);
                    graphics.drawString(font, tr("Stock ") + tr(stock), slotBoxX + 110, y + 30, 0xFFAED8FF, false);
                    graphics.drawString(font, tr("Price"), priceInputs[slot].getX() - 44, priceInputs[slot].getY() + 6, 0xFFC3DAEF, false);
                    graphics.drawString(font, tr("Stock"), layout.stockSlotX - 43, layout.stockSlotY + 6, 0xFFC3DAEF, false);

                    int stockBorder = selectedCreativeShelf ? 0xFF5A6372 : 0xFF4D86BB;
                    int stockFill = selectedCreativeShelf ? 0xFF2A3442 : 0xFF17324F;
                    graphics.fill(layout.stockSlotX, layout.stockSlotY, layout.stockSlotX + 20, layout.stockSlotY + 20, stockBorder);
                    graphics.fill(layout.stockSlotX + 1, layout.stockSlotY + 1, layout.stockSlotX + 19, layout.stockSlotY + 19, stockFill);
                    String stockLabel = selectedCreativeShelf ? "INF" : String.valueOf(Math.max(0, slotData == null ? 0 : slotData.stockCount()));
                    graphics.drawCenteredString(font, fitText(stockLabel, 18), layout.stockSlotX + 10, layout.stockSlotY + 6, selectedCreativeShelf ? 0xFF99AFC6 : 0xFFD4ECFF);
                } else {
                    graphics.drawString(font, tr("Regular display (not purchasable)"), slotBoxX + 28, y + 30, 0xFFBBD1E7, false);
                }
            }
            graphics.disableScissor();
        }

        if (!feedbackMessage.isBlank()) {
            int color = feedbackSuccess ? 0xFF8FFFB4 : 0xFFFFA2A2;
            int feedbackRight = (modeToggleButton != null && modeToggleButton.visible)
                    ? modeToggleButton.getX()
                    : closeButton.getX();
            graphics.drawString(font, fitText(feedbackMessage, Math.max(80, feedbackRight - (panelLeft + 14) - 8)), panelLeft + 14, feedbackY, color, false);
        }
        if (maxSlotScroll > 0) {
            String hint = tr("Scroll for more");
            int hintWidth = font.width(hint);
            int hintX = slotsAreaLeft + Math.max(4, ((slotsAreaRight - slotsAreaLeft) - hintWidth) / 2);
            int hintY = Math.min(closeRowY - 12, slotsAreaBottom + 4);
            graphics.drawString(font, hint, hintX, hintY, 0xFF8DB6DA, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (selectedShopMode && !hoverStack.isEmpty()) {
            graphics.renderTooltip(font, hoverStack, mouseX, mouseY);
        }
        if (inventoryPickerOpen) {
            renderInventoryPicker(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally blank to disable vanilla blur.
    }

    private void applyPayload(ShelfOpenPayload newPayload) {
        payload = newPayload;
        shelves.clear();
        if (newPayload != null && newPayload.shelves() != null) {
            shelves.addAll(newPayload.shelves());
        }
        if (shelves.isEmpty()) {
            selectedShelfIndex = -1;
            return;
        }
        int fromPayload = newPayload == null ? 0 : newPayload.selectedShelfIndex();
        selectedShelfIndex = Math.max(0, Math.min(shelves.size() - 1, fromPayload));
    }

    private void updateNavButtons() {
        boolean hasShelves = shelves.size() > 1;
        if (prevShelfButton != null) {
            prevShelfButton.active = hasShelves;
        }
        if (nextShelfButton != null) {
            nextShelfButton.active = hasShelves;
        }
    }

    private void stepShelf(int direction) {
        if (shelves.isEmpty()) {
            selectedShelfIndex = -1;
            return;
        }
        int next = selectedShelfIndex + direction;
        if (next < 0) {
            next = shelves.size() - 1;
        }
        if (next >= shelves.size()) {
            next = 0;
        }
        selectedShelfIndex = next;
        slotScroll = 0;
        if (minecraft != null) {
            this.init(minecraft, width, height);
        }
    }

    private void submitAction(String action, int slot) {
        submitAction(action, slot, -1);
    }

    private void submitAction(String action, int slot, int inventorySlot) {
        if (payload == null) {
            return;
        }
        ShelfUnitSummary selected = getSelectedShelf();
        if (selected == null) {
            return;
        }
        String textInput = "";
        if (slot >= 0 && slot < priceInputs.length && priceInputs[slot] != null
                && ("save_price".equals(action) || "set_slot_inventory".equals(action) || "set_slot".equals(action))) {
            textInput = selected.shopMode() ? priceInputs[slot].getValue() : "0";
        }

        PacketDistributor.sendToServer(new ShelfActionPayload(
                payload.dimensionId(),
                payload.rootX(),
                payload.rootY(),
                payload.rootZ(),
                selected.posKey(),
                action,
                slot,
                textInput,
                inventorySlot
        ));
    }

    private void openInventoryPicker(int slotIndex) {
        openInventoryPicker(slotIndex, PickerMode.SET_ITEM);
    }

    private void openTransformEditor(int slotIndex) {
        ShelfUnitSummary selected = getSelectedShelf();
        if (selected == null) {
            return;
        }
        ShelfSlotSummary slot = findSlot(selected, slotIndex);
        ItemStack slotStack = stackFromSlot(slot);
        if (slot == null || !slot.configured() || slotStack.isEmpty()) {
            feedbackSuccess = false;
            feedbackMessage = "Set an item for this slot first.";
            feedbackUntilMillis = System.currentTimeMillis() + 2500L;
            return;
        }
        Minecraft.getInstance().setScreen(new ShelfItemPositionScreen(payload, selected, slot));
    }

    private void openInventoryPicker(int slotIndex, PickerMode mode) {
        if (slotIndex < 0 || slotIndex >= getVisibleSlotCount()) {
            return;
        }
        pickerTargetShelfSlot = slotIndex;
        pickerMode = mode == null ? PickerMode.SET_ITEM : mode;
        inventoryPickerOpen = true;
        layoutInventoryPicker();
        rebuildInventoryChoices();
    }

    private void layoutInventoryPicker() {
        pickerWidth = Math.min(340, Math.max(230, panelWidth - 40));
        pickerHeight = Math.min(panelHeight - 20, 190);
        pickerHeight = Math.max(150, pickerHeight);
        pickerLeft = panelLeft + (panelWidth - pickerWidth) / 2;
        pickerTop = panelTop + (panelHeight - pickerHeight) / 2;
        pickerGridLeft = pickerLeft + 14;
        pickerGridTop = pickerTop + 40;
        pickerCancelW = 92;
        pickerCancelH = 20;
        pickerCancelX = pickerLeft + pickerWidth - pickerCancelW - 14;
        pickerCancelY = pickerTop + pickerHeight - pickerCancelH - 10;
    }

    private void rebuildInventoryChoices() {
        inventoryChoices.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        int cell = Math.max(16, Math.min(20, (pickerWidth - 28 - (8 * 2)) / 9));
        int gap = 2;

        // Main inventory (3 rows): slots 9..35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int invSlot = 9 + row * 9 + col;
                int x = pickerGridLeft + col * (cell + gap);
                int y = pickerGridTop + row * (cell + gap);
                ItemStack stack = mc.player.getInventory().getItem(invSlot);
                inventoryChoices.add(new InventoryChoice(invSlot, stack.copy(), x, y, cell));
            }
        }

        // Hotbar: slots 0..8
        int hotbarY = pickerGridTop + 3 * (cell + gap) + 8;
        for (int col = 0; col < 9; col++) {
            int invSlot = col;
            int x = pickerGridLeft + col * (cell + gap);
            ItemStack stack = mc.player.getInventory().getItem(invSlot);
            inventoryChoices.add(new InventoryChoice(invSlot, stack.copy(), x, hotbarY, cell));
        }
    }

    private void renderInventoryPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 600.0D);
        graphics.fill(0, 0, width, height, 0x92000000);

        int right = pickerLeft + pickerWidth;
        int bottom = pickerTop + pickerHeight;
        graphics.fill(pickerLeft - 2, pickerTop - 2, right + 2, bottom + 2, 0xFF2D4C74);
        graphics.fill(pickerLeft, pickerTop, right, bottom, 0xFF1D3558);
        graphics.fill(pickerLeft + 1, pickerTop + 1, right - 1, pickerTop + 24, 0xFF7DA6D7);
        graphics.fill(pickerLeft + 8, pickerTop + 30, right - 8, bottom - 8, 0xFF10263F);

        String title = pickerMode == PickerMode.SET_STOCK
                ? "Select Stock Stack (max 64)"
                : "Select Inventory Item";
        graphics.drawString(font, fitText(title, pickerWidth - 20), pickerLeft + 10, pickerTop + 8, 0xFFF6FCFF, false);
        graphics.drawString(font, tr("Shelf Slot ") + (pickerTargetShelfSlot + 1), pickerLeft + 10, pickerTop + 28, 0xFFD0E7FF, false);

        InventoryChoice hovered = null;
        for (InventoryChoice choice : inventoryChoices) {
            int x1 = choice.x;
            int y1 = choice.y;
            int x2 = x1 + choice.size;
            int y2 = y1 + choice.size;
            boolean isHovered = mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
            if (isHovered) {
                hovered = choice;
            }

            int border = isHovered ? 0xFFCBE8FF : 0xFF385E84;
            int fill = choice.stack.isEmpty() ? 0xDD182E46 : 0xDD234A73;
            graphics.fill(x1, y1, x2, y2, border);
            graphics.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);

            if (!choice.stack.isEmpty()) {
                int itemOffset = Math.max(1, (choice.size - 16) / 2);
                graphics.renderItem(choice.stack, x1 + itemOffset, y1 + itemOffset);
                graphics.renderItemDecorations(font, choice.stack, x1 + itemOffset, y1 + itemOffset);
            }
        }

        boolean cancelHover = mouseX >= pickerCancelX && mouseX < pickerCancelX + pickerCancelW
                && mouseY >= pickerCancelY && mouseY < pickerCancelY + pickerCancelH;
        graphics.fill(pickerCancelX, pickerCancelY, pickerCancelX + pickerCancelW, pickerCancelY + pickerCancelH,
                cancelHover ? 0xFF8C3750 : 0xFF6B2C40);
        graphics.drawString(font, tr("Cancel"), pickerCancelX + 26, pickerCancelY + 6, 0xFFFFFFFF, false);

        if (hovered != null && !hovered.stack.isEmpty()) {
            graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inventoryPickerOpen) {
            if (inside(mouseX, mouseY, pickerCancelX, pickerCancelY, pickerCancelW, pickerCancelH)) {
                inventoryPickerOpen = false;
                return true;
            }

            for (InventoryChoice choice : inventoryChoices) {
                if (!inside(mouseX, mouseY, choice.x, choice.y, choice.size, choice.size)) {
                    continue;
                }
                if (choice.stack.isEmpty()) {
                    feedbackSuccess = false;
                    feedbackMessage = "Selected slot is empty.";
                    feedbackUntilMillis = System.currentTimeMillis() + 2500L;
                    return true;
                }

                if (pickerMode == PickerMode.SET_STOCK) {
                    submitAction("save_stock_inventory", pickerTargetShelfSlot, choice.inventorySlot);
                } else {
                    submitAction("set_slot_inventory", pickerTargetShelfSlot, choice.inventorySlot);
                }
                inventoryPickerOpen = false;
                return true;
            }
            return true;
        }

        if (!inventoryPickerOpen && selectedCanManage && selectedShopMode && !selectedCreativeShelf) {
            for (int slot = 0; slot < MAX_VISIBLE_SLOTS; slot++) {
                SlotLayout layout = slotLayouts[slot];
                if (layout == null) {
                    continue;
                }
                if (!isRectInsideSlotsArea(layout.stockSlotX, layout.stockSlotY, 20, 20)) {
                    continue;
                }
                if (inside(mouseX, mouseY, layout.stockSlotX, layout.stockSlotY, 20, 20)
                        && inside(mouseX, mouseY, slotsAreaLeft, slotsAreaTop, Math.max(1, slotsAreaRight - slotsAreaLeft), Math.max(1, slotsAreaBottom - slotsAreaTop))) {
                    openInventoryPicker(slot, PickerMode.SET_STOCK);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private boolean isRectInsideSlotsArea(int x, int y, int w, int h) {
        return x >= slotsAreaLeft + 1
                && y >= slotsAreaTop + 1
                && x + w <= slotsAreaRight - 1
                && y + h <= slotsAreaBottom - 1;
    }

    private ShelfUnitSummary getSelectedShelf() {
        if (shelves.isEmpty() || selectedShelfIndex < 0 || selectedShelfIndex >= shelves.size()) {
            return null;
        }
        return shelves.get(selectedShelfIndex);
    }

    private static ShelfSlotSummary findSlot(ShelfUnitSummary shelf, int slotIndex) {
        if (shelf == null || shelf.slots() == null) {
            return null;
        }
        for (ShelfSlotSummary slot : shelf.slots()) {
            if (slot.slotIndex() == slotIndex) {
                return slot;
            }
        }
        return null;
    }

    private static ItemStack stackFromSlot(ShelfSlotSummary slot) {
        if (slot == null || !slot.configured()) {
            return ItemStack.EMPTY;
        }
        // Use exact stack data from server (including NBT) so modded items resolve with correct icon + name.
        ItemStack fullStack = slot.displayStack();
        if (fullStack != null && !fullStack.isEmpty()) {
            return fullStack.copy();
        }
        // Fallback for legacy payloads that may only include item id.
        if (slot.itemId() == null || slot.itemId().isBlank()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(slot.itemId());
        if (id == null) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static boolean isModularShelf(ShelfUnitSummary shelf) {
        if (shelf == null || shelf.displayType() == null) {
            return false;
        }
        return ShelfDisplayType.MODULAR_WALL.id().equalsIgnoreCase(shelf.displayType());
    }

    private boolean isSelectedModularExpanded() {
        ShelfUnitSummary selected = getSelectedShelf();
        if (!isModularShelf(selected)) {
            return false;
        }
        String base = baseShelfKey(selected.posKey());
        int matches = 0;
        for (ShelfUnitSummary unit : shelves) {
            if (!isModularShelf(unit)) {
                continue;
            }
            if (base.equalsIgnoreCase(baseShelfKey(unit.posKey()))) {
                matches++;
                if (matches > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String baseShelfKey(String key) {
        if (key == null) {
            return "";
        }
        int idx = key.indexOf('|');
        return idx < 0 ? key : key.substring(0, idx);
    }

    private String fitText(String text, int maxWidth) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return "";
        }
        text = tr(text);
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int ellipsisWidth = font.width(ellipsis);
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }

    private static String tr(String text) {
        return UbsClientTranslations.resolve(text == null ? "" : text);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum PickerMode {
        SET_ITEM,
        SET_STOCK
    }

    private static final class SlotLayout {
        private int baseY;
        private int y;
        private int height;
        private int blockX;
        private int blockW;
        private int iconX;
        private int iconY;
        private int stockSlotX;
        private int stockSlotY;

        private SlotLayout() {
        }
    }

    private static final class InventoryChoice {
        private final int inventorySlot;
        private final ItemStack stack;
        private final int x;
        private final int y;
        private final int size;

        private InventoryChoice(int inventorySlot, ItemStack stack, int x, int y, int size) {
            this.inventorySlot = inventorySlot;
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.size = size;
        }
    }
}
