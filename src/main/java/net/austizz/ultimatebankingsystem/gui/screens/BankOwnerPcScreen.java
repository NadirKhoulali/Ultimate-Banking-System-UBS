package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.OpenBankOwnerPcPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcCreateBankPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BankOwnerPcScreen extends Screen {

    private record AccountCardData(String player, String type, String balance, String id) {}

    private record AccountCardHitbox(int x, int y, int width, int height, AccountCardData data) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record InputHelp(String title, String summary, String example) {}

    private record MarketOfferData(String id,
                                   String lender,
                                   String amountText,
                                   String aprText,
                                   String termText,
                                   BigDecimal amountValue,
                                   BigDecimal aprValue,
                                   long termTicks) {}

    private record MarketActionHitbox(int x, int y, int width, int height, String action, MarketOfferData offer) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record RectHitbox(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record MarketParseResult(boolean isMarketPayload, List<MarketOfferData> offers) {}

    private enum WindowMode {
        DESKTOP,
        BANK_APP,
        CREATE_BANK
    }

    private enum Section {
        OVERVIEW,
        BRANDING,
        LIMITS,
        GOVERNANCE,
        STAFFING,
        LENDING,
        COMPLIANCE
    }

    private enum MarketSort {
        AMOUNT,
        APR,
        TERM,
        LENDER,
        ID
    }

    private static final int PAD = 8;
    private static final int TOPBAR_HEIGHT = 26;
    private static final int TASKBAR_HEIGHT = 26;
    private static final int LINE_HEIGHT = 11;
    private static final List<String> OWNERSHIP_MODELS = List.of(
            "SOLE",
            "ROLE_BASED",
            "PERCENTAGE_SHARES",
            "FIXED_COFOUNDERS"
    );

    private WindowMode activeWindow = WindowMode.DESKTOP;
    private Section activeSection = Section.OVERVIEW;

    private boolean bankWindowOpen;
    private boolean createWindowOpen;
    private UUID activeBankId;

    private String selectedOwnershipModel = OWNERSHIP_MODELS.getFirst();

    private final Map<String, String> formValues = new HashMap<>();

    private int outputPanelX;
    private int outputPanelY;
    private int outputPanelW;
    private int outputPanelH;
    private int outputScroll;
    private int sectionControlsBottomY;
    private boolean overviewDetailOpen;
    private String overviewDetailAction = "SHOW_INFO";
    private int navScroll;
    private int navMaxScroll;
    private int navViewportX;
    private int navViewportY;
    private int navViewportW;
    private int navViewportH;
    private int sectionScroll;
    private int sectionMaxScroll;
    private int sectionViewportX;
    private int sectionViewportY;
    private int sectionViewportW;
    private int sectionViewportH;
    private final Map<String, DesktopEditBox> activeFormInputs = new HashMap<>();
    private final List<AccountCardHitbox> visibleAccountCards = new ArrayList<>();
    private AccountCardData selectedAccountCard;
    private boolean accountProfileOpen;
    private boolean lendingMarketOpen;
    private MarketSort marketSort = MarketSort.AMOUNT;
    private boolean marketSortDescending = true;
    private final List<MarketOfferData> marketOfferCache = new ArrayList<>();
    private final List<MarketActionHitbox> visibleMarketActions = new ArrayList<>();
    private MarketOfferData pendingMarketAccept;
    private RectHitbox marketConfirmAcceptHitbox;
    private RectHitbox marketConfirmCancelHitbox;
    private boolean refreshMarketAfterNextResponse;
    private boolean useVirtualScale;
    private float virtualScaleX = 1.0F;
    private float virtualScaleY = 1.0F;

    public BankOwnerPcScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        configureVirtualScale();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.activeFormInputs.clear();
        this.visibleAccountCards.clear();
        this.visibleMarketActions.clear();
        this.marketConfirmAcceptHitbox = null;
        this.marketConfirmCancelHitbox = null;

        int closeW = 28;
        addPcButton(
                this.width - PAD - closeW - 4,
                PAD + 2,
                closeW,
                20,
                "Close",
                btn -> this.onClose()
        );

        if (activeWindow == WindowMode.DESKTOP) {
            initDesktopWidgets();
        } else if (activeWindow == WindowMode.BANK_APP) {
            initBankWindowWidgets();
        } else if (activeWindow == WindowMode.CREATE_BANK) {
            initCreateBankWidgets();
        }

        initTaskbarWidgets();
    }

    private void initDesktopWidgets() {
        int contentLeft = PAD + 18;
        int contentTop = PAD + TOPBAR_HEIGHT + 22;
        int contentWidth = this.width - (PAD * 2) - 36;

        List<OwnerPcBankAppSummary> apps = ClientOwnerPcData.getApps();
        int columns = Math.max(1, Math.min(4, contentWidth / 200));
        int buttonWidth = Math.max(156, Math.min(188, (contentWidth - ((columns - 1) * 12)) / columns));
        int buttonHeight = 42;

        int idx = 0;
        for (OwnerPcBankAppSummary app : apps) {
            int col = idx % columns;
            int row = idx / columns;
            int x = contentLeft + (col * (buttonWidth + 12));
            int y = contentTop + (row * (buttonHeight + 10));

            String icon = app.owner() ? "BANK" : "ROLE";
            String label = icon + " | " + app.bankName();
            if (!app.owner() && app.roleLabel() != null && !app.roleLabel().isBlank()) {
                label = label + " [" + app.roleLabel() + "]";
            }

            addPcButton(
                    x,
                    y,
                    buttonWidth,
                    buttonHeight,
                    fitToWidth(label, buttonWidth - 10),
                    btn -> {
                        activeBankId = app.bankId();
                        ClientOwnerPcData.setSelectedBankId(activeBankId);
                        ClientOwnerPcData.clearActionOutput();
                        outputScroll = 0;
                        sectionScroll = 0;
                        navScroll = 0;
                        bankWindowOpen = true;
                        activeWindow = WindowMode.BANK_APP;
                        activeSection = Section.OVERVIEW;
                        overviewDetailOpen = false;
                        overviewDetailAction = "SHOW_INFO";
                        selectedAccountCard = null;
                        accountProfileOpen = false;
                        lendingMarketOpen = false;
                        pendingMarketAccept = null;
                        marketOfferCache.clear();
                        refreshMarketAfterNextResponse = false;
                        requestBankData(activeBankId);
                        rebuildWidgets();
                    }
            );
            idx++;
        }

        int createY = this.height - PAD - TASKBAR_HEIGHT - 44;
        boolean canCreate = ClientOwnerPcData.getOwnedCount() < ClientOwnerPcData.getMaxBanks();
        DesktopButton createButton = addPcButton(
                contentLeft,
                createY,
                300,
                28,
                canCreate
                        ? "Create Bank (" + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks() + ")"
                        : "Bank Limit Reached",
                btn -> {
                    createWindowOpen = true;
                    activeWindow = WindowMode.CREATE_BANK;
                    rebuildWidgets();
                }
        );
        createButton.active = canCreate;
    }

    private void initBankWindowWidgets() {
        int left = PAD + 12;
        int top = PAD + TOPBAR_HEIGHT + 10;
        int right = this.width - PAD - 12;
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 8;

        int sidebarTop = top + 50;
        int sidebarBottom = this.height - PAD - TASKBAR_HEIGHT - 14;
        int sectionX = left + 14;
        int sectionW = 132;
        navViewportX = sectionX;
        navViewportY = sidebarTop + 6;
        navViewportW = sectionW;
        navViewportH = Math.max(40, sidebarBottom - navViewportY - 2);

        int sectionCount = Section.values().length;
        int sectionH = 24;
        int sectionGap = 6;
        int totalNavHeight = (sectionCount * sectionH) + ((sectionCount - 1) * sectionGap);
        int availableNavHeight = Math.max(40, navViewportH);
        if (totalNavHeight > availableNavHeight) {
            sectionH = 21;
            sectionGap = 4;
            totalNavHeight = (sectionCount * sectionH) + ((sectionCount - 1) * sectionGap);
        }
        if (totalNavHeight > availableNavHeight) {
            sectionH = 19;
            sectionGap = 3;
            totalNavHeight = (sectionCount * sectionH) + ((sectionCount - 1) * sectionGap);
        }
        navMaxScroll = Math.max(0, totalNavHeight - availableNavHeight);
        navScroll = Math.max(0, Math.min(navScroll, navMaxScroll));
        int sectionY = navViewportY - navScroll;

        int i = 0;
        for (Section section : Section.values()) {
            String label = section.name().substring(0, 1) + section.name().substring(1).toLowerCase(Locale.ROOT);
            int buttonY = sectionY + (i * (sectionH + sectionGap));
            DesktopButton button = addPcButton(
                    sectionX,
                    buttonY,
                    sectionW,
                    sectionH,
                    label,
                    btn -> {
                        activeSection = section;
                        overviewDetailOpen = false;
                        overviewDetailAction = "SHOW_INFO";
                        selectedAccountCard = null;
                        accountProfileOpen = false;
                        lendingMarketOpen = false;
                        pendingMarketAccept = null;
                        marketOfferCache.clear();
                        refreshMarketAfterNextResponse = false;
                        ClientOwnerPcData.clearActionOutput();
                        outputScroll = 0;
                        sectionScroll = 0;
                        rebuildWidgets();
                    }
            );
            button.setLabelOffset(14, 3).setIconOffset(4, 3);
            boolean visible = buttonY >= navViewportY && (buttonY + sectionH) <= (navViewportY + navViewportH);
            button.visible = visible;
            button.active = visible && activeSection != section;
            i++;
        }

        int toolbarY = top + 4;
        addPcButton(
                right - 178,
                toolbarY,
                82,
                20,
                "Minimize",
                btn -> {
                    activeWindow = WindowMode.DESKTOP;
                    rebuildWidgets();
                }
        ).setLabelOffset(4, 1);

        addPcButton(
                right - 90,
                toolbarY,
                82,
                20,
                "Refresh",
                btn -> requestBankData(activeBankId)
        ).setLabelOffset(4, 1);

        int areaX = left + 170;
        int areaY = top + 60;
        int areaWidth = Math.max(180, right - areaX - 10);
        sectionViewportX = areaX + 10;
        sectionViewportY = areaY + 2;
        sectionViewportW = Math.max(120, areaWidth - 20);
        int availableSectionHeight = Math.max(80, bottom - sectionViewportY - 12);
        int minOutputHeight = Math.max(90, this.height / 5);
        int maxSectionHeight = Math.max(60, availableSectionHeight - minOutputHeight - 8);
        int targetSectionHeight = Math.min(220, Math.max(110, maxSectionHeight));
        sectionViewportH = Math.max(60, Math.min(targetSectionHeight, maxSectionHeight));
        sectionScroll = Math.max(0, Math.min(sectionScroll, sectionMaxScroll));

        initSectionWidgets(areaX, areaY, areaWidth);
    }

    private void initCreateBankWidgets() {
        int width = Math.min(700, this.width - (PAD * 2) - 80);
        int left = (this.width - width) / 2;
        int top = PAD + TOPBAR_HEIGHT + 44;

        DesktopEditBox name = addFormInput("create.name", left, top, width, "Bank name");

        int optionY = top + 42;
        int optionW = (width - 8) / 2;
        int optionH = 24;

        for (int i = 0; i < OWNERSHIP_MODELS.size(); i++) {
            String model = OWNERSHIP_MODELS.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = left + (col * (optionW + 8));
            int y = optionY + (row * (optionH + 6));

            boolean selected = model.equalsIgnoreCase(selectedOwnershipModel);
            DesktopButton option = addPcButton(
                    x,
                    y,
                    optionW,
                    optionH,
                    (selected ? "Selected: " : "") + prettifyOwnership(model),
                    btn -> {
                        selectedOwnershipModel = model;
                        rebuildWidgets();
                    }
            );
            option.active = !selected;
        }

        addPcButton(
                left,
                top + 106,
                220,
                26,
                "Create Bank",
                btn -> PacketDistributor.sendToServer(new OwnerPcCreateBankPayload(
                        textOrBlank(name),
                        selectedOwnershipModel
                ))
        );

        addPcButton(
                left + 230,
                top + 106,
                140,
                26,
                "Back",
                btn -> {
                    activeWindow = WindowMode.DESKTOP;
                    rebuildWidgets();
                }
        );

        addPcButton(
                left + 378,
                top + 106,
                170,
                26,
                "Refresh Apps",
                btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())
        );

        createWindowOpen = true;
    }

    private void initTaskbarWidgets() {
        int barY = this.height - PAD - TASKBAR_HEIGHT + 3;
        int x = PAD + 8;

        addPcButton(
                x,
                barY,
                64,
                20,
                "Start",
                btn -> {
                    activeWindow = WindowMode.DESKTOP;
                    rebuildWidgets();
                }
        );
        x += 72;

        if (bankWindowOpen) {
            addPcButton(
                    x,
                    barY,
                    190,
                    20,
                    fitToWidth("Bank Manager", 170),
                    btn -> {
                        activeWindow = WindowMode.BANK_APP;
                        rebuildWidgets();
                    }
            );
            x += 196;
        }

        if (createWindowOpen) {
            addPcButton(
                    x,
                    barY,
                    190,
                    20,
                    "Create Bank",
                    btn -> {
                        activeWindow = WindowMode.CREATE_BANK;
                        rebuildWidgets();
                    }
            );
        }
    }

    private void initSectionWidgets(int x, int y, int width) {
        sectionControlsBottomY = y;
        OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
        int innerX = x + 12;
        int innerWidth = Math.max(120, width - 24);
        int gap = 8;

        if (data == null || activeBankId == null || !activeBankId.equals(data.bankId())) {
            addSectionPcButton(
                    innerX,
                    y + 8,
                    Math.min(220, innerWidth),
                    24,
                    "Load Bank Data",
                    btn -> requestBankData(activeBankId)
            ).setLabelOffset(4, 1);
            int contentHeight = Math.max(0, sectionControlsBottomY - sectionViewportY + 4);
            int viewportHeight = Math.max(40, sectionViewportH);
            sectionMaxScroll = Math.max(0, contentHeight - viewportHeight);
            return;
        }

        boolean ownerView = data.ownerView();

        switch (activeSection) {
            case OVERVIEW -> {
                if (overviewDetailOpen) {
                    boolean accountProfileView = accountProfileOpen
                            && "SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction)
                            && selectedAccountCard != null;
                    if (accountProfileView) {
                        if (innerWidth < 320) {
                            addSectionPcButton(
                                    innerX,
                                    y + 8,
                                    innerWidth,
                                    24,
                                    "Back to Accounts",
                                    btn -> {
                                        accountProfileOpen = false;
                                        selectedAccountCard = null;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX,
                                    y + 40,
                                    innerWidth,
                                    24,
                                    "Back to Overview",
                                    btn -> {
                                        overviewDetailOpen = false;
                                        overviewDetailAction = "SHOW_INFO";
                                        accountProfileOpen = false;
                                        selectedAccountCard = null;
                                        ClientOwnerPcData.clearActionOutput();
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                        } else {
                            int leftW = (innerWidth - gap) / 2;
                            int rightW = innerWidth - leftW - gap;
                            addSectionPcButton(
                                    innerX,
                                    y + 8,
                                    leftW,
                                    24,
                                    "Back to Accounts",
                                    btn -> {
                                        accountProfileOpen = false;
                                        selectedAccountCard = null;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + leftW + gap,
                                    y + 8,
                                    rightW,
                                    24,
                                    "Back to Overview",
                                    btn -> {
                                        overviewDetailOpen = false;
                                        overviewDetailAction = "SHOW_INFO";
                                        accountProfileOpen = false;
                                        selectedAccountCard = null;
                                        ClientOwnerPcData.clearActionOutput();
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                        }
                    } else if (innerWidth < 320) {
                        addSectionPcButton(
                                innerX,
                                y + 8,
                                innerWidth,
                                24,
                                "Back to Overview",
                                btn -> {
                                    overviewDetailOpen = false;
                                    overviewDetailAction = "SHOW_INFO";
                                    ClientOwnerPcData.clearActionOutput();
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);

                        addSectionPcButton(
                                innerX,
                                y + 40,
                                innerWidth,
                                24,
                                "Refresh " + overviewActionLabel(overviewDetailAction),
                                btn -> {
                                    ClientOwnerPcData.clearActionOutput();
                                    outputScroll = 0;
                                    sendOwnerPcAction(overviewDetailAction, "", "", "", "");
                                }
                        ).setLabelOffset(6, 1);
                    } else {
                        int backW = Math.min(190, Math.max(140, innerWidth / 3));
                        int refreshW = Math.max(140, innerWidth - backW - gap);
                        addSectionPcButton(
                                innerX,
                                y + 8,
                                backW,
                                24,
                                "Back to Overview",
                                btn -> {
                                    overviewDetailOpen = false;
                                    overviewDetailAction = "SHOW_INFO";
                                    ClientOwnerPcData.clearActionOutput();
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);

                        addSectionPcButton(
                                innerX + backW + gap,
                                y + 8,
                                refreshW,
                                24,
                                "Refresh " + overviewActionLabel(overviewDetailAction),
                                btn -> {
                                    ClientOwnerPcData.clearActionOutput();
                                    outputScroll = 0;
                                    sendOwnerPcAction(overviewDetailAction, "", "", "", "");
                                }
                        ).setLabelOffset(6, 1);
                    }
                } else {
                    String[] labels = {"Info", "Dashboard", "Reserve", "Accounts", "Certificates", "Loan Summary"};
                    String[] actions = {"SHOW_INFO", "SHOW_DASHBOARD", "SHOW_RESERVE", "SHOW_ACCOUNTS", "SHOW_CDS", "SHOW_LOANS"};
                    int columns = innerWidth >= 560 ? 3 : innerWidth >= 370 ? 2 : 1;
                    int buttonW = Math.max(120, (innerWidth - (gap * (columns - 1))) / columns);
                    int rowY = y + 8;
                    for (int idx = 0; idx < labels.length; idx++) {
                        int col = idx % columns;
                        int row = idx / columns;
                        addOverviewActionButton(
                                innerX + (col * (buttonW + gap)),
                                rowY + (row * 34),
                                buttonW,
                                labels[idx],
                                actions[idx]
                        );
                    }
                }
            }
            case BRANDING -> {
                addSectionFormInput("branding.motto", innerX, y + 8, innerWidth, "Motto");
                addSectionFormInput("branding.color", innerX, y + 38, innerWidth, "Color (#55AAFF or blue)");
                if (innerWidth < 260) {
                    addSectionActionButton(innerX, y + 72, innerWidth, "Set Motto", "SET_MOTTO", "@branding.motto", "", "", "", ownerView);
                    addSectionActionButton(innerX, y + 104, innerWidth, "Set Color", "SET_COLOR", "@branding.color", "", "", "", ownerView);
                } else {
                    int btnW = (innerWidth - gap) / 2;
                    addSectionActionButton(innerX, y + 72, btnW, "Set Motto", "SET_MOTTO", "@branding.motto", "", "", "", ownerView);
                    addSectionActionButton(innerX + btnW + gap, y + 72, btnW, "Set Color", "SET_COLOR", "@branding.color", "", "", "", ownerView);
                }
            }
            case LIMITS -> {
                int selectorBottom = addLimitTypeSelectors(innerX, y + 8, innerWidth);
                if (innerWidth < 260) {
                    addSectionFormInput("limits.amount", innerX, selectorBottom + 4, innerWidth, "Amount");
                    addSectionActionButton(innerX, selectorBottom + 36, innerWidth, "Apply Limit", "SET_LIMIT", "@limits.type", "@limits.amount", "", "", ownerView);
                    addSectionActionButton(innerX, selectorBottom + 68, innerWidth, "Show Limits", "SHOW_LIMITS", "", "", "", "", true);
                } else {
                    addSectionFormInput("limits.amount", innerX, selectorBottom + 4, innerWidth, "Amount");
                    int btnW = (innerWidth - gap) / 2;
                    addSectionActionButton(innerX, selectorBottom + 36, btnW, "Apply Limit", "SET_LIMIT", "@limits.type", "@limits.amount", "", "", ownerView);
                    addSectionActionButton(innerX + btnW + gap, selectorBottom + 36, btnW, "Show Limits", "SHOW_LIMITS", "", "", "", "", true);
                }
            }
            case GOVERNANCE -> {
                boolean compact = innerWidth < 620;
                if (!compact) {
                    int halfW = Math.max(120, (innerWidth - gap) / 2);
                    addSectionFormInput("gov.player", innerX, y + 8, halfW, "Player (name or UUID)");
                    addSectionFormInput("gov.role", innerX + halfW + gap, y + 8, halfW, "Role");
                    addSectionFormInput("gov.share", innerX, y + 38, halfW, "Share %");

                    int row1W = Math.max(110, (innerWidth - (gap * 3)) / 4);
                    int row1Y = y + 72;
                    addSectionActionButton(innerX, row1Y, row1W, "Assign Role", "ROLE_ASSIGN", "@gov.player", "@gov.role", "", "", ownerView);
                    addSectionActionButton(innerX + row1W + gap, row1Y, row1W, "Revoke Role", "ROLE_REVOKE", "@gov.player", "", "", "", ownerView);
                    addSectionActionButton(innerX + (row1W + gap) * 2, row1Y, row1W, "Role List", "SHOW_ROLES", "", "", "", "", true);
                    addSectionActionButton(innerX + (row1W + gap) * 3, row1Y, row1W, "Add Cofounder", "COFOUNDER_ADD", "@gov.player", "", "", "", ownerView);

                    int row2W = Math.max(120, (innerWidth - (gap * 2)) / 3);
                    int row2Y = row1Y + 32;
                    addSectionActionButton(innerX, row2Y, row2W, "Set Shares", "SHARES_SET", "@gov.player", "@gov.share", "", "", ownerView);
                    addSectionActionButton(innerX + row2W + gap, row2Y, row2W, "Share List", "SHOW_SHARES", "", "", "", "", true);
                    addSectionActionButton(innerX + (row2W + gap) * 2, row2Y, row2W, "Cofounders", "SHOW_COFOUNDERS", "", "", "", "", true);
                } else {
                    int curY = y + 8;
                    addSectionFormInput("gov.player", innerX, curY, innerWidth, "Player (name or UUID)");
                    curY += 28;
                    addSectionFormInput("gov.role", innerX, curY, innerWidth, "Role");
                    curY += 28;
                    addSectionFormInput("gov.share", innerX, curY, innerWidth, "Share %");
                    curY += 34;

                    if (innerWidth < 260) {
                        addSectionActionButton(innerX, curY, innerWidth, "Assign Role", "ROLE_ASSIGN", "@gov.player", "@gov.role", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Revoke Role", "ROLE_REVOKE", "@gov.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Role List", "SHOW_ROLES", "", "", "", "", true);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Add Cofounder", "COFOUNDER_ADD", "@gov.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Set Shares", "SHARES_SET", "@gov.player", "@gov.share", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Share List", "SHOW_SHARES", "", "", "", "", true);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Cofounders", "SHOW_COFOUNDERS", "", "", "", "", true);
                    } else {
                        int btnW = (innerWidth - gap) / 2;
                        addSectionActionButton(innerX, curY, btnW, "Assign Role", "ROLE_ASSIGN", "@gov.player", "@gov.role", "", "", ownerView);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Revoke Role", "ROLE_REVOKE", "@gov.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, btnW, "Role List", "SHOW_ROLES", "", "", "", "", true);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Add Cofounder", "COFOUNDER_ADD", "@gov.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, btnW, "Set Shares", "SHARES_SET", "@gov.player", "@gov.share", "", "", ownerView);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Share List", "SHOW_SHARES", "", "", "", "", true);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Cofounders", "SHOW_COFOUNDERS", "", "", "", "", true);
                    }
                }
            }
            case STAFFING -> {
                if (innerWidth >= 560) {
                    int fieldW = Math.max(100, (innerWidth - (gap * 2)) / 3);
                    addSectionFormInput("staff.player", innerX, y + 8, fieldW, "Player (name or UUID)");
                    addSectionFormInput("staff.role", innerX + fieldW + gap, y + 8, fieldW, "Role");
                    addSectionFormInput("staff.salary", innerX + (fieldW + gap) * 2, y + 8, fieldW, "Salary");

                    int btnW = Math.max(120, (innerWidth - (gap * 2)) / 3);
                    addSectionActionButton(innerX, y + 42, btnW, "Hire", "HIRE", "@staff.player", "@staff.role", "@staff.salary", "", ownerView);
                    addSectionActionButton(innerX + btnW + gap, y + 42, btnW, "Fire", "FIRE", "@staff.player", "", "", "", ownerView);
                    addSectionActionButton(innerX + (btnW + gap) * 2, y + 42, btnW, "Employee List", "SHOW_EMPLOYEES", "", "", "", "", true);
                } else {
                    int curY = y + 8;
                    addSectionFormInput("staff.player", innerX, curY, innerWidth, "Player (name or UUID)");
                    curY += 28;
                    addSectionFormInput("staff.role", innerX, curY, innerWidth, "Role");
                    curY += 28;
                    addSectionFormInput("staff.salary", innerX, curY, innerWidth, "Salary");
                    curY += 34;
                    if (innerWidth < 260) {
                        addSectionActionButton(innerX, curY, innerWidth, "Hire", "HIRE", "@staff.player", "@staff.role", "@staff.salary", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Fire", "FIRE", "@staff.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Employee List", "SHOW_EMPLOYEES", "", "", "", "", true);
                    } else {
                        int btnW = (innerWidth - gap) / 2;
                        addSectionActionButton(innerX, curY, btnW, "Hire", "HIRE", "@staff.player", "@staff.role", "@staff.salary", "", ownerView);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Fire", "FIRE", "@staff.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Employee List", "SHOW_EMPLOYEES", "", "", "", "", true);
                    }
                }
            }
            case LENDING -> {
                if (lendingMarketOpen) {
                    int curY = y + 8;
                    if (innerWidth < 420) {
                        addSectionPcButton(
                                innerX,
                                curY,
                                innerWidth,
                                24,
                                "Back to Lending",
                                btn -> closeLendingMarket()
                        ).setLabelOffset(6, 1);
                        curY += 32;

                        if (innerWidth < 280) {
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    innerWidth,
                                    24,
                                    "Refresh Market",
                                    btn -> {
                                        pendingMarketAccept = null;
                                        outputScroll = 0;
                                        sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
                                    }
                            ).setLabelOffset(6, 1);
                            curY += 32;
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    innerWidth,
                                    24,
                                    "Sort: " + marketSortLabel(marketSort),
                                    btn -> cycleMarketSort()
                            ).setLabelOffset(6, 1);
                            curY += 32;
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    innerWidth,
                                    24,
                                    marketSortDescending ? "Order: High-Low" : "Order: Low-High",
                                    btn -> {
                                        marketSortDescending = !marketSortDescending;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                        } else {
                            int sortW = (innerWidth - gap) / 2;
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    sortW,
                                    24,
                                    "Sort: " + marketSortLabel(marketSort),
                                    btn -> cycleMarketSort()
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + sortW + gap,
                                    curY,
                                    sortW,
                                    24,
                                    marketSortDescending ? "Order: High-Low" : "Order: Low-High",
                                    btn -> {
                                        marketSortDescending = !marketSortDescending;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                            curY += 32;

                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    innerWidth,
                                    24,
                                    "Refresh Market",
                                    btn -> {
                                        pendingMarketAccept = null;
                                        outputScroll = 0;
                                        sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
                                    }
                            ).setLabelOffset(6, 1);
                        }
                    } else {
                        if (innerWidth < 560) {
                            int halfW = (innerWidth - gap) / 2;
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    halfW,
                                    24,
                                    "Back to Lending",
                                    btn -> closeLendingMarket()
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + halfW + gap,
                                    curY,
                                    halfW,
                                    24,
                                    "Refresh Market",
                                    btn -> {
                                        pendingMarketAccept = null;
                                        outputScroll = 0;
                                        sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
                                    }
                            ).setLabelOffset(6, 1);
                            curY += 32;

                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    halfW,
                                    24,
                                    "Sort: " + marketSortLabel(marketSort),
                                    btn -> cycleMarketSort()
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + halfW + gap,
                                    curY,
                                    halfW,
                                    24,
                                    marketSortDescending ? "Order: High-Low" : "Order: Low-High",
                                    btn -> {
                                        marketSortDescending = !marketSortDescending;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                        } else {
                            int colW = (innerWidth - (gap * 3)) / 4;
                            addSectionPcButton(
                                    innerX,
                                    curY,
                                    colW,
                                    24,
                                    "Back to Lending",
                                    btn -> closeLendingMarket()
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + colW + gap,
                                    curY,
                                    colW,
                                    24,
                                    "Sort: " + marketSortLabel(marketSort),
                                    btn -> cycleMarketSort()
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + (colW + gap) * 2,
                                    curY,
                                    colW,
                                    24,
                                    marketSortDescending ? "Order: High-Low" : "Order: Low-High",
                                    btn -> {
                                        marketSortDescending = !marketSortDescending;
                                        outputScroll = 0;
                                        rebuildWidgets();
                                    }
                            ).setLabelOffset(6, 1);
                            addSectionPcButton(
                                    innerX + (colW + gap) * 3,
                                    curY,
                                    colW,
                                    24,
                                    "Refresh Market",
                                    btn -> {
                                        pendingMarketAccept = null;
                                        outputScroll = 0;
                                        sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
                                    }
                            ).setLabelOffset(6, 1);
                        }
                    }
                } else {
                    int curY = y + 8;
                    addSectionFormInput("lend.borrow", innerX, curY, innerWidth, "Borrow amount");
                    curY += 28;
                    addSectionActionButton(innerX, curY, Math.min(220, innerWidth), "Borrow", "BORROW", "@lend.borrow", "", "", "", ownerView);
                    curY += 34;

                    if (innerWidth >= 520) {
                        int offerW = (innerWidth - (gap * 2)) / 3;
                        addSectionFormInput("lend.offer.amount", innerX, curY, offerW, "Offer amount");
                        addSectionFormInput("lend.offer.rate", innerX + offerW + gap, curY, offerW, "APR");
                        addSectionFormInput("lend.offer.term", innerX + (offerW + gap) * 2, curY, offerW, "Term ticks");
                        curY += 28;
                    } else {
                        addSectionFormInput("lend.offer.amount", innerX, curY, innerWidth, "Offer amount");
                        curY += 28;
                        addSectionFormInput("lend.offer.rate", innerX, curY, innerWidth, "APR");
                        curY += 28;
                        addSectionFormInput("lend.offer.term", innerX, curY, innerWidth, "Term ticks");
                        curY += 28;
                    }
                    addSectionActionButton(innerX, curY, Math.min(240, innerWidth), "Post Offer", "LEND_OFFER",
                            "@lend.offer.amount", "@lend.offer.rate", "@lend.offer.term", "", ownerView);
                    curY += 34;

                    addSectionFormInput("lend.accept.id", innerX, curY, innerWidth, "Offer UUID to accept");
                    curY += 28;
                    if (innerWidth < 260) {
                        addSectionActionButton(innerX, curY, innerWidth, "Accept Offer", "LEND_ACCEPT", "@lend.accept.id", "", "", "", ownerView);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Market", btn -> openLendingMarket())
                                .setLabelOffset(6, 1);
                    } else {
                        int acceptW = (innerWidth - gap) / 2;
                        addSectionActionButton(innerX, curY, acceptW, "Accept Offer", "LEND_ACCEPT", "@lend.accept.id", "", "", "", ownerView);
                        addSectionPcButton(innerX + acceptW + gap, curY, acceptW, 24, "Market", btn -> openLendingMarket())
                                .setLabelOffset(6, 1);
                    }
                    curY += 34;

                    if (innerWidth >= 520) {
                        int prodW = Math.max(110, (innerWidth - (gap * 3)) / 4);
                        addSectionFormInput("lend.product.name", innerX, curY, prodW, "Product name");
                        addSectionFormInput("lend.product.max", innerX + prodW + gap, curY, prodW, "Max amount");
                        addSectionFormInput("lend.product.rate", innerX + (prodW + gap) * 2, curY, prodW, "APR");
                        addSectionFormInput("lend.product.duration", innerX + (prodW + gap) * 3, curY, prodW, "Duration ticks");
                        curY += 28;
                    } else {
                        addSectionFormInput("lend.product.name", innerX, curY, innerWidth, "Product name");
                        curY += 28;
                        addSectionFormInput("lend.product.max", innerX, curY, innerWidth, "Max amount");
                        curY += 28;
                        addSectionFormInput("lend.product.rate", innerX, curY, innerWidth, "APR");
                        curY += 28;
                        addSectionFormInput("lend.product.duration", innerX, curY, innerWidth, "Duration ticks");
                        curY += 28;
                    }
                    addSectionActionButton(innerX, curY, Math.min(200, innerWidth), "Create Product", "CREATE_LOAN_PRODUCT",
                            "@lend.product.name", "@lend.product.max", "@lend.product.rate", "@lend.product.duration", ownerView);
                    curY += 34;

                    if (innerWidth < 260) {
                        addSectionActionButton(innerX, curY, innerWidth, "Loan Products", "SHOW_LOAN_PRODUCTS", "", "", "", "", true);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Loan Summary", "SHOW_LOANS", "", "", "", "", true);
                    } else {
                        int listW = (innerWidth - gap) / 2;
                        addSectionActionButton(innerX, curY, listW, "Loan Products", "SHOW_LOAN_PRODUCTS", "", "", "", "", true);
                        addSectionActionButton(innerX + listW + gap, curY, listW, "Loan Summary", "SHOW_LOANS", "", "", "", "", true);
                    }
                }
            }
            case COMPLIANCE -> {
                addSectionFormInput("compliance.appeal", innerX, y + 8, innerWidth, "Appeal message");
                if (innerWidth < 390) {
                    addSectionActionButton(innerX, y + 42, innerWidth, "Submit Appeal", "APPEAL", "@compliance.appeal", "", "", "", ownerView);
                    addSectionActionButton(innerX, y + 74, innerWidth, "Dashboard", "SHOW_DASHBOARD", "", "", "", "", true);
                    addSectionActionButton(innerX, y + 106, innerWidth, "Reserve", "SHOW_RESERVE", "", "", "", "", true);
                } else {
                    int btnW = (innerWidth - (gap * 2)) / 3;
                    addSectionActionButton(innerX, y + 42, btnW, "Submit Appeal", "APPEAL", "@compliance.appeal", "", "", "", ownerView);
                    addSectionActionButton(innerX + btnW + gap, y + 42, btnW, "Dashboard", "SHOW_DASHBOARD", "", "", "", "", true);
                    addSectionActionButton(innerX + (btnW + gap) * 2, y + 42, btnW, "Reserve", "SHOW_RESERVE", "", "", "", "", true);
                }
            }
        }

        int contentHeight = Math.max(0, sectionControlsBottomY - sectionViewportY + 4);
        int viewportHeight = Math.max(40, sectionViewportH);
        int newMaxScroll = Math.max(0, contentHeight - viewportHeight);
        sectionMaxScroll = newMaxScroll;
        if (sectionScroll > sectionMaxScroll) {
            sectionScroll = sectionMaxScroll;
            rebuildWidgets();
        }
    }

    private int addLimitTypeSelectors(int x, int y, int width) {
        String selected = formValues.getOrDefault("limits.type", "single").toLowerCase(Locale.ROOT);
        if (!List.of("single", "dailyplayer", "dailybank").contains(selected)) {
            selected = "single";
            formValues.put("limits.type", selected);
        } else {
            formValues.putIfAbsent("limits.type", selected);
        }

        String[] types = {"single", "dailyplayer", "dailybank"};
        String[] labels = {"Single", "Daily Player", "Daily Bank"};

        int rowBottom;
        if (width < 390) {
            int currentY = y;
            for (int i = 0; i < types.length; i++) {
                String type = types[i];
                DesktopButton button = addSectionPcButton(
                        x,
                        currentY,
                        width,
                        24,
                        (selected.equals(type) ? "Selected: " : "") + labels[i],
                        btn -> {
                            formValues.put("limits.type", type);
                            rebuildWidgets();
                        }
                ).setLabelOffset(6, 1);
                button.active = button.visible && !selected.equals(type);
                currentY += 28;
            }
            rowBottom = currentY;
        } else {
            int gap = 8;
            int buttonW = (width - (gap * 2)) / 3;
            for (int i = 0; i < types.length; i++) {
                String type = types[i];
                DesktopButton button = addSectionPcButton(
                        x + (i * (buttonW + gap)),
                        y,
                        buttonW,
                        24,
                        (selected.equals(type) ? "Selected: " : "") + labels[i],
                        btn -> {
                            formValues.put("limits.type", type);
                            rebuildWidgets();
                        }
                ).setLabelOffset(6, 1);
                button.active = button.visible && !selected.equals(type);
            }
            rowBottom = y + 28;
        }
        return rowBottom;
    }

    private DesktopEditBox addFormInput(String key, int x, int y, int width, String placeholder) {
        DesktopEditBox input = new DesktopEditBox(this.font, x, y, width, 20, Component.literal(placeholder));
        input.setValue(formValues.getOrDefault(key, ""));
        input.setResponder(value -> formValues.put(key, value == null ? "" : value));
        input.setHint(Component.literal(placeholder));
        input.setTextColor(0xFFFFFFFF);
        input.setTextColorUneditable(0xFFCFD8E3);
        DesktopEditBox widget = addRenderableWidget(input);
        activeFormInputs.put(key, widget);
        return widget;
    }

    private DesktopEditBox addSectionFormInput(String key, int x, int y, int width, String placeholder) {
        int renderY = y - sectionScroll;
        DesktopEditBox input = addFormInput(key, x, renderY, width, placeholder);
        markSectionControl(y, 20);
        boolean visible = renderY >= sectionViewportY && (renderY + 20) <= (sectionViewportY + sectionViewportH);
        input.visible = visible;
        input.active = visible;
        return input;
    }

    private DesktopButton addSectionPcButton(int x,
                                             int y,
                                             int width,
                                             int height,
                                             String label,
                                             java.util.function.Consumer<DesktopButton> onPress) {
        int renderY = y - sectionScroll;
        DesktopButton button = addPcButton(x, renderY, width, height, label, onPress);
        markSectionControl(y, height);
        boolean visible = renderY >= sectionViewportY && (renderY + height) <= (sectionViewportY + sectionViewportH);
        button.visible = visible;
        button.active = visible;
        return button;
    }

    private DesktopButton addPcButton(int x,
                                      int y,
                                      int width,
                                      int height,
                                      String label,
                                      java.util.function.Consumer<DesktopButton> onPress) {
        return addRenderableWidget(new DesktopButton(
                x,
                y,
                width,
                height,
                Component.literal(label),
                onPress
        ));
    }

    private DesktopButton addSectionActionButton(int x,
                                                 int y,
                                                 int width,
                                                 String label,
                                                 String action,
                                                 String arg1,
                                                 String arg2,
                                                 String arg3,
                                                 String arg4,
                                                 boolean active) {
        int renderY = y - sectionScroll;
        DesktopButton button = addActionButton(x, renderY, width, label, action, arg1, arg2, arg3, arg4, active);
        markSectionControl(y, 24);
        boolean visible = renderY >= sectionViewportY && (renderY + 24) <= (sectionViewportY + sectionViewportH);
        button.visible = visible;
        button.active = visible && active;
        return button;
    }

    private void markSectionControl(int y, int height) {
        sectionControlsBottomY = Math.max(sectionControlsBottomY, y + Math.max(1, height));
    }

    private DesktopButton addOverviewActionButton(int x, int y, int width, String label, String action) {
        DesktopButton button = addSectionPcButton(
                x,
                y,
                width,
                24,
                fitToWidth(label, width - 12),
                btn -> openOverviewDetail(action)
        );
        button.setLabelOffset(4, 1).setIconOffset(0, 1);
        return button;
    }

    private DesktopButton addActionButton(int x,
                                          int y,
                                          int width,
                                          String label,
                                          String action,
                                          String arg1,
                                          String arg2,
                                          String arg3,
                                          String arg4,
                                          boolean active) {
        DesktopButton button = addPcButton(
                x,
                y,
                width,
                24,
                fitToWidth(label, width - 10),
                btn -> {
                    sendOwnerPcAction(
                            action,
                            resolveArg(arg1),
                            resolveArg(arg2),
                            resolveArg(arg3),
                            resolveArg(arg4)
                    );
                }
        );
        button.setLabelOffset(3, 1).setIconOffset(0, 1);
        button.active = active;
        return button;
    }

    private void openOverviewDetail(String action) {
        overviewDetailOpen = true;
        overviewDetailAction = action == null || action.isBlank() ? "SHOW_INFO" : action;
        selectedAccountCard = null;
        accountProfileOpen = false;
        lendingMarketOpen = false;
        pendingMarketAccept = null;
        marketOfferCache.clear();
        refreshMarketAfterNextResponse = false;
        ClientOwnerPcData.clearActionOutput();
        outputScroll = 0;
        sendOwnerPcAction(overviewDetailAction, "", "", "", "");
        rebuildWidgets();
    }

    private void openLendingMarket() {
        lendingMarketOpen = true;
        pendingMarketAccept = null;
        marketOfferCache.clear();
        refreshMarketAfterNextResponse = false;
        outputScroll = 0;
        ClientOwnerPcData.clearActionOutput();
        sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
        rebuildWidgets();
    }

    private void closeLendingMarket() {
        lendingMarketOpen = false;
        pendingMarketAccept = null;
        marketOfferCache.clear();
        refreshMarketAfterNextResponse = false;
        outputScroll = 0;
        rebuildWidgets();
    }

    private void cycleMarketSort() {
        MarketSort[] values = MarketSort.values();
        int next = (marketSort.ordinal() + 1) % values.length;
        marketSort = values[next];
        outputScroll = 0;
        rebuildWidgets();
    }

    private String marketSortLabel(MarketSort sort) {
        return switch (sort) {
            case AMOUNT -> "Amount";
            case APR -> "APR";
            case TERM -> "Term";
            case LENDER -> "Lender";
            case ID -> "Offer ID";
        };
    }

    private void sendOwnerPcAction(String action, String arg1, String arg2, String arg3, String arg4) {
        if (activeBankId == null) {
            return;
        }
        PacketDistributor.sendToServer(new OwnerPcActionPayload(
                activeBankId,
                action,
                arg1 == null ? "" : arg1,
                arg2 == null ? "" : arg2,
                arg3 == null ? "" : arg3,
                arg4 == null ? "" : arg4
        ));
    }

    private String resolveArg(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.charAt(0) == '@') {
            return formValues.getOrDefault(value.substring(1), "").trim();
        }
        return value.trim();
    }

    private String textOrBlank(DesktopEditBox box) {
        return box == null ? "" : box.getValue().trim();
    }

    private void requestBankData(UUID bankId) {
        if (bankId == null) {
            return;
        }
        PacketDistributor.sendToServer(new OwnerPcBankDataRequestPayload(bankId));
    }

    public void refreshFromNetwork() {
        if (refreshMarketAfterNextResponse
                && activeWindow == WindowMode.BANK_APP
                && activeSection == Section.LENDING
                && lendingMarketOpen
                && activeBankId != null) {
            refreshMarketAfterNextResponse = false;
            sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (activeWindow == WindowMode.DESKTOP) {
                this.onClose();
            } else {
                activeWindow = WindowMode.DESKTOP;
                rebuildWidgets();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (activeWindow == WindowMode.BANK_APP) {
            if (localMouseX >= navViewportX && localMouseX <= (navViewportX + navViewportW)
                    && localMouseY >= navViewportY && localMouseY <= (navViewportY + navViewportH)
                    && navMaxScroll > 0) {
                int previous = navScroll;
                int step = 16;
                if (scrollY < 0) {
                    navScroll = Math.min(navMaxScroll, navScroll + step);
                } else if (scrollY > 0) {
                    navScroll = Math.max(0, navScroll - step);
                }
                if (navScroll != previous) {
                    rebuildWidgets();
                }
                return true;
            }

            if (localMouseX >= sectionViewportX && localMouseX <= (sectionViewportX + sectionViewportW)
                    && localMouseY >= sectionViewportY && localMouseY <= (sectionViewportY + sectionViewportH)
                    && sectionMaxScroll > 0) {
                int previous = sectionScroll;
                int step = 16;
                if (scrollY < 0) {
                    sectionScroll = Math.min(sectionMaxScroll, sectionScroll + step);
                } else if (scrollY > 0) {
                    sectionScroll = Math.max(0, sectionScroll - step);
                }
                if (sectionScroll != previous) {
                    rebuildWidgets();
                }
                return true;
            }

            if (localMouseX >= outputPanelX && localMouseX <= (outputPanelX + outputPanelW)
                    && localMouseY >= outputPanelY && localMouseY <= (outputPanelY + outputPanelH)) {
                int maxScroll;
                if (activeSection == Section.LENDING && lendingMarketOpen) {
                    if (pendingMarketAccept != null) {
                        return true;
                    }
                    int bodyWidth = Math.max(120, outputPanelW - 12);
                    int bodyHeight = Math.max(40, outputPanelH - 12);
                    int listHeight = Math.max(32, bodyHeight - 30);
                    int cardH = 76;
                    int gap = 10;
                    int cols = bodyWidth >= 620 ? 2 : 1;
                    int rows = (getSortedMarketOffers().size() + cols - 1) / cols;
                    int visibleRows = Math.max(1, (listHeight + gap) / (cardH + gap));
                    maxScroll = Math.max(0, rows - visibleRows);
                } else if (activeSection == Section.OVERVIEW
                        && overviewDetailOpen
                        && !isOverviewMetricsAction(overviewDetailAction)
                        && !("SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && accountProfileOpen)
                        && ClientOwnerPcData.getCurrentBankData() != null) {
                    int bodyWidth = Math.max(120, outputPanelW - 12);
                    int bodyHeight = Math.max(40, outputPanelH - 12);
                    int cols = bodyWidth >= 520 ? 2 : 1;
                    int cardH = 46;
                    int gap = 8;
                    int rows = (extractOverviewCardEntries().size() + cols - 1) / cols;
                    int visibleRows = Math.max(1, (bodyHeight + gap) / (cardH + gap));
                    maxScroll = Math.max(0, rows - visibleRows);
                } else {
                    List<String> wrapped = getWrappedOutputLines();
                    int visible = Math.max(1, (outputPanelH - 10) / LINE_HEIGHT);
                    maxScroll = Math.max(0, wrapped.size() - visible);
                }
                if (maxScroll > 0) {
                    if (scrollY < 0) {
                        outputScroll = Math.min(maxScroll, outputScroll + 1);
                    } else if (scrollY > 0) {
                        outputScroll = Math.max(0, outputScroll - 1);
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(localMouseX, localMouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && activeSection == Section.LENDING
                && lendingMarketOpen) {
            if (pendingMarketAccept != null) {
                if (marketConfirmAcceptHitbox != null && marketConfirmAcceptHitbox.contains(localMouseX, localMouseY)) {
                    String offerId = pendingMarketAccept.id();
                    pendingMarketAccept = null;
                    refreshMarketAfterNextResponse = true;
                    sendOwnerPcAction("LEND_ACCEPT", offerId, "", "", "");
                    outputScroll = 0;
                    ClientOwnerPcData.setToast(true, "Submitting accept for offer " + offerId + "...");
                    rebuildWidgets();
                    return true;
                }
                if (marketConfirmCancelHitbox != null && marketConfirmCancelHitbox.contains(localMouseX, localMouseY)) {
                    pendingMarketAccept = null;
                    rebuildWidgets();
                    return true;
                }
                return true;
            }

            for (MarketActionHitbox actionHitbox : visibleMarketActions) {
                if (!actionHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                if ("COPY".equals(actionHitbox.action())) {
                    Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
                    if (mc != null && mc.keyboardHandler != null) {
                        mc.keyboardHandler.setClipboard(actionHitbox.offer().id());
                    }
                    ClientOwnerPcData.setToast(true, "Copied offer id " + actionHitbox.offer().id() + ".");
                    return true;
                }
                if ("ACCEPT".equals(actionHitbox.action())) {
                    pendingMarketAccept = actionHitbox.offer();
                    rebuildWidgets();
                    return true;
                }
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && "SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction)
                && !accountProfileOpen) {
            for (AccountCardHitbox card : visibleAccountCards) {
                if (card.contains(localMouseX, localMouseY)) {
                    selectedAccountCard = card.data();
                    accountProfileOpen = true;
                    outputScroll = 0;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(localMouseX, localMouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(toLocalX(mouseX), toLocalY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(
                toLocalX(mouseX),
                toLocalY(mouseY),
                button,
                toLocalDeltaX(dragX),
                toLocalDeltaY(dragY)
        );
    }

    @Override
    public void onClose() {
        ClientOwnerPcData.clear();
        super.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int localMouseX = (int) toLocalX(mouseX);
        int localMouseY = (int) toLocalY(mouseY);
        if (useVirtualScale) {
            graphics.pose().pushPose();
            graphics.pose().scale(virtualScaleX, virtualScaleY, 1.0F);
        }
        for (int y = 0; y < this.height; y++) {
            float ratio = (float) y / (float) Math.max(1, this.height - 1);
            int row = lerpColor(0xFF6EA3DE, 0xFF2E5B97, ratio);
            graphics.fill(0, y, this.width, y + 1, row);
        }

        for (int y = 0; y < this.height; y += 24) {
            graphics.fill(0, y, this.width, y + 1, 0x20FFFFFF);
        }
        for (int x = 0; x < this.width; x += 24) {
            graphics.fill(x, 0, x + 1, this.height, 0x18FFFFFF);
        }

        int left = PAD;
        int top = PAD;
        int right = this.width - PAD;
        int bottom = this.height - PAD;

        graphics.fill(left - 2, top - 2, right + 2, bottom + 2, 0xAA0A1D33);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF1D334D);
        graphics.fill(left, top, right, bottom, 0x66223145);

        int headerBottom = top + TOPBAR_HEIGHT;
        for (int y = top; y < headerBottom; y++) {
            float ratio = (float) (y - top) / (float) Math.max(1, TOPBAR_HEIGHT - 1);
            int row = lerpColor(0xFFEAF3FF, 0xFF9AB8DE, ratio);
            graphics.fill(left, y, right, y + 1, row);
        }

        int taskbarTop = bottom - TASKBAR_HEIGHT;
        for (int y = taskbarTop; y < bottom; y++) {
            float ratio = (float) (y - taskbarTop) / (float) Math.max(1, TASKBAR_HEIGHT - 1);
            int row = lerpColor(0xFFF5F8FD, 0xFFD4DDEB, ratio);
            graphics.fill(left, y, right, y + 1, row);
        }

        graphics.drawString(this.font, "UBS Desktop", left + 10, top + 9, 0xFF1E324E, false);
        graphics.drawString(this.font,
                "Apps: " + ClientOwnerPcData.getApps().size() + "   Owned: "
                        + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks(),
                left + 130,
                top + 9,
                0xFF2C4770,
                false);

        if (activeWindow == WindowMode.BANK_APP) {
            drawBankWindowFrame(graphics);
        } else if (activeWindow == WindowMode.CREATE_BANK) {
            drawCreateWindowFrame(graphics);
        } else {
            drawDesktopHints(graphics);
        }

        super.render(graphics, localMouseX, localMouseY, partialTicks);
        drawViewportMasks(graphics);
        drawToast(graphics);
        if (useVirtualScale) {
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No-op to hard-disable vanilla menu blur/background behavior.
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        // No-op to hard-disable transparent background blur paths.
    }

    @Override
    public void renderBlurredBackground(float partialTick) {
        // No-op to hard-disable blur paths.
    }

    @Override
    public void renderMenuBackground(GuiGraphics graphics) {
        // No-op to hard-disable menu background paths.
    }

    @Override
    public void renderMenuBackground(GuiGraphics graphics, int x, int y, int width, int height) {
        // No-op to hard-disable menu background paths.
    }

    private void drawDesktopHints(GuiGraphics graphics) {
        int x = PAD + 20;
        int y = PAD + TOPBAR_HEIGHT + 6;
        graphics.drawString(this.font, "Desktop Apps", x, y, 0xFFFFFFFF, false);

        if (ClientOwnerPcData.getApps().isEmpty()) {
            graphics.drawString(this.font,
                    "No bank apps available. Create a bank or obtain a bank role.",
                    x,
                    y + 16,
                    0xFFE8F3FF,
                    false);
        }
    }

    private void drawViewportMasks(GuiGraphics graphics) {
        if (activeWindow != WindowMode.BANK_APP) {
            return;
        }

        int navMask = 0xFFD2DBE8;
        graphics.fill(navViewportX - 1, navViewportY - 5, navViewportX + navViewportW + 1, navViewportY, navMask);
        graphics.fill(navViewportX - 1, navViewportY + navViewportH, navViewportX + navViewportW + 1, navViewportY + navViewportH + 5, navMask);

        int sectionMask = 0xCC18314A;
        graphics.fill(sectionViewportX - 3, sectionViewportY - 5, sectionViewportX + sectionViewportW + 3, sectionViewportY, sectionMask);
        graphics.fill(sectionViewportX - 3, sectionViewportY + sectionViewportH, sectionViewportX + sectionViewportW + 3, sectionViewportY + sectionViewportH + 5, sectionMask);
    }

    private void drawBankWindowFrame(GuiGraphics graphics) {
        int left = PAD + 12;
        int top = PAD + TOPBAR_HEIGHT + 10;
        int right = this.width - PAD - 12;
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 8;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF2A3D59);
        graphics.fill(left, top, right, bottom, 0xFFE8EEF6);
        graphics.fill(left, top, right, top + 28, 0xFF6C93C8);

        int sidebarRight = left + 156;
        graphics.fill(left + 2, top + 30, sidebarRight, bottom - 2, 0xFFD2DBE8);
        graphics.fill(sidebarRight, top + 30, sidebarRight + 1, bottom - 2, 0xFF607EA3);
        graphics.fill(navViewportX - 2, navViewportY - 2, navViewportX + navViewportW + 2, navViewportY + navViewportH + 2, 0xFF2D4B6D);
        graphics.fill(navViewportX - 1, navViewportY - 1, navViewportX + navViewportW + 1, navViewportY + navViewportH + 1, 0xCC1D3551);

        graphics.fill(sectionViewportX - 4, sectionViewportY - 3, sectionViewportX + sectionViewportW + 4, sectionViewportY + sectionViewportH + 3, 0xFF2B4768);
        graphics.fill(sectionViewportX - 3, sectionViewportY - 2, sectionViewportX + sectionViewportW + 3, sectionViewportY + sectionViewportH + 2, 0xCC18314A);
        graphics.fill(sectionViewportX - 3, sectionViewportY - 2, sectionViewportX + sectionViewportW + 3, sectionViewportY - 1, 0x889FCBF0);

        OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
        graphics.drawString(this.font, fitToWidth(currentToolTitle(), right - left - 220), left + 8, top + 10, 0xFFFFFFFF, false);

        int outputX = left + 170;
        int outputY = getOutputPanelTop(top);
        drawOutputPanel(graphics, data, outputX, outputY, right - outputX - 10, bottom - outputY - 10);

        if (navMaxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    navViewportX + navViewportW - 4,
                    navViewportY + 1,
                    3,
                    Math.max(10, navViewportH - 2),
                    navScroll,
                    navMaxScroll
            );
        }
        if (sectionMaxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    sectionViewportX + sectionViewportW - 4,
                    sectionViewportY + 1,
                    3,
                    Math.max(10, sectionViewportH - 2),
                    sectionScroll,
                    sectionMaxScroll
            );
        }
    }

    private void drawVerticalScrollbar(GuiGraphics graphics,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       int position,
                                       int maxPosition) {
        if (height <= 0 || width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0x5535475F);
        if (maxPosition <= 0) {
            graphics.fill(x, y, x + width, y + height, 0xAA9FC4E8);
            return;
        }
        int thumbH = Math.max(10, height / 5);
        int travel = Math.max(1, height - thumbH);
        int thumbY = y + (int) (travel * (position / (float) maxPosition));
        graphics.fill(x, thumbY, x + width, thumbY + thumbH, 0xCC9FC4E8);
    }

    private void drawOutputPanel(GuiGraphics graphics,
                                 OwnerPcBankDataPayload data,
                                 int x,
                                 int y,
                                 int width,
                                 int height) {
        if (width < 20 || height < 20) {
            return;
        }
        this.outputPanelX = x;
        this.outputPanelY = y;
        this.outputPanelW = width;
        this.outputPanelH = height;

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2A3F5B);
        graphics.fill(x, y, x + width, y + height, 0xF0132538);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, 0x66BFDFFF);
        visibleMarketActions.clear();
        marketConfirmAcceptHitbox = null;
        marketConfirmCancelHitbox = null;

        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && isOverviewMetricsAction(overviewDetailAction)
                && data != null) {
            visibleAccountCards.clear();
            drawOverviewDashboard(graphics, data, overviewDetailAction, x + 6, y + 6, width - 12, height - 12);
            return;
        }

        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && data != null) {
            if ("SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && accountProfileOpen && selectedAccountCard != null) {
                visibleAccountCards.clear();
                drawAccountProfilePanel(graphics, selectedAccountCard, x + 6, y + 6, width - 12, height - 12);
                return;
            }
            drawOverviewListCards(graphics, overviewDetailAction, x + 6, y + 6, width - 12, height - 12);
            return;
        }

        visibleAccountCards.clear();

        if (activeSection == Section.LENDING && lendingMarketOpen) {
            drawLendingMarketPanel(graphics, x + 6, y + 6, width - 12, height - 12);
            return;
        }

        InputHelp help = getFocusedInputHelp();
        if (help != null && activeSection == Section.LIMITS) {
            drawInputHelpPanel(graphics, help, x + 6, y + 6, width - 12, height - 12);
            return;
        }
        if (help != null && activeSection == Section.LENDING && !lendingMarketOpen) {
            drawInputHelpPanel(graphics, help, x + 6, y + 6, width - 12, height - 12);
            return;
        }

        List<String> lines = getWrappedOutputLines();
        if (lines.isEmpty()) {
            if (activeSection == Section.OVERVIEW && !overviewDetailOpen) {
                lines = wrapLines(
                        List.of(
                                "Select an overview tool above to open details.",
                                "The selected view will load here in full-screen panel mode."
                        ),
                        Math.max(1, width - 14)
                );
            } else if (activeSection == Section.OVERVIEW) {
                lines = wrapLines(
                        List.of(
                                "Loading " + overviewActionLabel(overviewDetailAction) + "...",
                                "Press Refresh if this view does not update."
                        ),
                        Math.max(1, width - 14)
                );
            } else {
                lines = wrapLines(
                        List.of(
                                "Action output appears here.",
                                "Use the controls above to run a command for this section."
                        ),
                        Math.max(1, width - 14)
                );
            }
        }

        int available = Math.max(1, (height - 10) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - available);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        int lineY = y + 4;
        for (int i = 0; i < available; i++) {
            int idx = outputScroll + i;
            if (idx >= lines.size()) {
                break;
            }
            graphics.drawString(this.font, lines.get(idx), x + 6, lineY, 0xFFE7F3FF, false);
            lineY += LINE_HEIGHT;
        }

        if (maxScroll > 0) {
            int barX1 = x + width - 5;
            int barX2 = x + width - 2;
            graphics.fill(barX1, y + 3, barX2, y + height - 3, 0x553C5878);

            int thumbH = Math.max(12, (int) ((height - 6) * (available / (float) lines.size())));
            int thumbTravel = Math.max(1, (height - 6) - thumbH);
            int thumbY = y + 3 + (int) (thumbTravel * (outputScroll / (float) maxScroll));
            graphics.fill(barX1, thumbY, barX2, thumbY + thumbH, 0xCC9FD1FF);
        }
    }

    private String currentToolTitle() {
        String section = switch (activeSection) {
            case OVERVIEW -> "Overview";
            case BRANDING -> "Branding";
            case LIMITS -> "Limits";
            case GOVERNANCE -> "Governance";
            case STAFFING -> "Staffing";
            case LENDING -> "Lending";
            case COMPLIANCE -> "Compliance";
        };
        if (activeSection == Section.OVERVIEW && overviewDetailOpen) {
            return "Overview / " + overviewActionLabel(overviewDetailAction);
        }
        if (activeSection == Section.LENDING && lendingMarketOpen) {
            return "Lending / Market";
        }
        return section;
    }

    private String overviewActionLabel(String action) {
        if (action == null || action.isBlank()) {
            return "Info";
        }
        return switch (action.toUpperCase(Locale.ROOT)) {
            case "SHOW_INFO" -> "Info";
            case "SHOW_DASHBOARD" -> "Dashboard";
            case "SHOW_RESERVE" -> "Reserve";
            case "SHOW_ACCOUNTS" -> "Accounts";
            case "SHOW_CDS" -> "Certificates";
            case "SHOW_LOANS" -> "Loan Summary";
            default -> "Info";
        };
    }

    private int getOutputPanelTop(int top) {
        int minimumTop = top + 96;
        int desiredTop = Math.max(minimumTop, sectionViewportY + sectionViewportH + 10);
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 8;
        int maxTop = bottom - 90; // Keep enough room for the output panel at all GUI scales.
        return Math.max(minimumTop, Math.min(desiredTop, maxTop));
    }

    private boolean isOverviewMetricsAction(String action) {
        if (action == null) {
            return false;
        }
        String normalized = action.toUpperCase(Locale.ROOT);
        return "SHOW_INFO".equals(normalized)
                || "SHOW_DASHBOARD".equals(normalized)
                || "SHOW_RESERVE".equals(normalized);
    }

    private void drawOverviewDashboard(GuiGraphics graphics,
                                       OwnerPcBankDataPayload data,
                                       String action,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        graphics.fill(x, y, x + width, y + height, 0x40213A56);

        String normalizedAction = action == null ? "SHOW_INFO" : action.toUpperCase(Locale.ROOT);
        int cardGap = 6;
        int cardH = 42;
        int cardCols = width >= 520 ? 4 : 2;
        int cardW = Math.max(80, (width - (cardGap * (cardCols - 1))) / cardCols);

        BigDecimal reserve = parseDecimal(data.reserve());
        BigDecimal deposits = parseDecimal(data.deposits());
        BigDecimal minReserve = parseDecimal(data.minReserve());
        BigDecimal dailyCap = parseDecimal(data.dailyCap());
        BigDecimal dailyUsed = parseDecimal(data.dailyUsed());
        BigDecimal dailyRemaining = parseDecimal(data.dailyRemaining());

        String[] cardLabels;
        String[] cardValues;
        int[] accents;
        if ("SHOW_RESERVE".equals(normalizedAction)) {
            BigDecimal buffer = reserve.subtract(minReserve);
            BigDecimal ratioPct = deposits.signum() <= 0
                    ? BigDecimal.valueOf(100)
                    : reserve.multiply(BigDecimal.valueOf(100)).divide(deposits, 1, RoundingMode.HALF_UP);
            cardLabels = new String[]{"Reserve", "Min Reserve", "Buffer", "Utilization"};
            cardValues = new String[]{
                    "$" + compactCurrency(data.reserve()),
                    "$" + compactCurrency(data.minReserve()),
                    "$" + compactCurrency(buffer.toPlainString()),
                    ratioPct + "%"
            };
            accents = new int[]{0xFF5AB8FF, 0xFF88C7FF, 0xFF64D47B, 0xFFE9A56E};
        } else if ("SHOW_DASHBOARD".equals(normalizedAction)) {
            String risk;
            if ("SUSPENDED".equalsIgnoreCase(data.status()) || "REVOKED".equalsIgnoreCase(data.status())
                    || reserve.compareTo(minReserve) < 0) {
                risk = "RED";
            } else if (dailyCap.signum() > 0
                    && dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).compareTo(BigDecimal.valueOf(0.90)) >= 0) {
                risk = "YELLOW";
            } else {
                risk = "GREEN";
            }
            cardLabels = new String[]{"Status", "Risk", "Accounts", "Fed Funds"};
            cardValues = new String[]{
                    data.status(),
                    risk,
                    data.accountsCount(),
                    data.federalFundsRate() + "%"
            };
            accents = new int[]{0xFF72C0FF, 0xFF90DB81, 0xFF7BD1C6, 0xFFD6BD7A};
        } else {
            cardLabels = new String[]{"Owner", "Model", "Color", "Motto"};
            cardValues = new String[]{
                    fitToWidth(data.ownerName(), 32),
                    data.ownershipModel(),
                    data.color(),
                    data.motto().isBlank() ? "-" : fitToWidth(data.motto(), 26)
            };
            accents = new int[]{0xFF66BCFF, 0xFF7BC8F6, 0xFF89DDB2, 0xFFC7C778};
        }
        for (int i = 0; i < cardLabels.length; i++) {
            int col = i % cardCols;
            int row = i / cardCols;
            int cardX = x + (col * (cardW + cardGap));
            int cardY = y + (row * (cardH + cardGap));
            drawMetricCard(graphics, cardX, cardY, cardW, cardH, cardLabels[i], cardValues[i], accents[i]);
        }

        int cardRows = (cardLabels.length + cardCols - 1) / cardCols;
        int chartTop = y + (cardRows * (cardH + cardGap)) + 4;
        int chartH = Math.min(74, Math.max(56, height / 3));
        boolean compactCharts = width < 420;
        int chartW = compactCharts ? width : (width - 8) / 2;
        drawBarCard(graphics,
                x,
                chartTop,
                chartW,
                chartH,
                "SHOW_RESERVE".equals(normalizedAction) ? "Reserve Cushion" : "Reserve Coverage",
                minReserve.signum() <= 0
                        ? 1.0F
                        : reserve.divide(minReserve, 4, RoundingMode.HALF_EVEN).floatValue(),
                "SHOW_RESERVE".equals(normalizedAction)
                        ? "$" + compactCurrency(data.reserve()) + " vs min $" + compactCurrency(data.minReserve())
                        : "Min $" + compactCurrency(data.minReserve()),
                reserve.compareTo(minReserve) >= 0 ? 0xFF64D47B : 0xFFE36D6D);
        float dailyUsedPct = dailyCap.signum() <= 0
                ? 0.0F
                : dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).floatValue();
        drawBarCard(graphics,
                compactCharts ? x : x + chartW + 8,
                compactCharts ? chartTop + chartH + 8 : chartTop,
                chartW,
                chartH,
                "SHOW_DASHBOARD".equals(normalizedAction) ? "Liquidity Headroom" : "Daily Utilization",
                "SHOW_DASHBOARD".equals(normalizedAction) ? Math.max(0.0F, Math.min(1.0F, dailyRemaining.signum() <= 0 ? 0.0F :
                        dailyRemaining.divide(dailyCap.max(BigDecimal.ONE), 4, RoundingMode.HALF_EVEN).floatValue())) : dailyUsedPct,
                "SHOW_DASHBOARD".equals(normalizedAction)
                        ? "$" + compactCurrency(data.dailyRemaining()) + " available"
                        : "$" + compactCurrency(data.dailyUsed()) + " / $" + compactCurrency(data.dailyCap()),
                0xFF6FB8FF);

        int listTop = compactCharts ? chartTop + (chartH * 2) + 18 : chartTop + chartH + 10;
        int listBottom = y + height - 6;
        if (listBottom - listTop >= 48) {
            graphics.fill(x, listTop, x + width, listBottom, 0x55273E59);
            graphics.fill(x, listTop, x + width, listTop + 1, 0x88A9CBED);

            int rowY = listTop + 6;
            if ("SHOW_RESERVE".equals(normalizedAction)) {
                graphics.drawString(this.font, "Reserve Audit", x + 8, rowY, 0xFFE6F3FF, false);
                graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Declared Reserve: $" + compactCurrency(data.reserve()), x + 8, rowY, 0xFFD3E9FF, false);
                graphics.drawString(this.font, "Minimum Reserve: $" + compactCurrency(data.minReserve()), x + (width / 2), rowY, 0xFFD3E9FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Daily Used: $" + compactCurrency(data.dailyUsed()), x + 8, rowY, 0xFFC6DEFA, false);
                graphics.drawString(this.font, "Daily Cap: $" + compactCurrency(data.dailyCap()), x + (width / 2), rowY, 0xFFC6DEFA, false);
            } else if ("SHOW_DASHBOARD".equals(normalizedAction)) {
                graphics.drawString(this.font, "Operations Snapshot", x + 8, rowY, 0xFFE6F3FF, false);
                graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Owner: " + fitToWidth(data.ownerName(), Math.max(40, width / 2 - 20)), x + 8, rowY, 0xFFD3E9FF, false);
                graphics.drawString(this.font, "Accounts: " + data.accountsCount(), x + (width / 2), rowY, 0xFFD3E9FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Fed Funds: " + data.federalFundsRate() + "%", x + 8, rowY, 0xFFC6DEFA, false);
                graphics.drawString(this.font, "Remaining Today: $" + compactCurrency(data.dailyRemaining()), x + (width / 2), rowY, 0xFFC6DEFA, false);
            } else {
                graphics.drawString(this.font, "Bank Profile", x + 8, rowY, 0xFFE6F3FF, false);
                graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Owner: " + fitToWidth(data.ownerName(), Math.max(40, width / 2 - 20)), x + 8, rowY, 0xFFD3E9FF, false);
                graphics.drawString(this.font, "Model: " + data.ownershipModel(), x + (width / 2), rowY, 0xFFD3E9FF, false);
                rowY += 12;
                graphics.drawString(this.font, "Color: " + data.color(), x + 8, rowY, 0xFFC6DEFA, false);
                graphics.drawString(this.font, "Motto: " + fitToWidth(data.motto().isBlank() ? "-" : data.motto(), Math.max(40, width / 2 - 20)), x + (width / 2), rowY, 0xFFC6DEFA, false);
            }
        }
    }

    private void drawOverviewListCards(GuiGraphics graphics,
                                       String action,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        visibleAccountCards.clear();
        List<String> cards = extractOverviewCardEntries();
        if (cards.isEmpty()) {
            graphics.drawString(this.font, "No entries available.", x + 6, y + 8, 0xFFE6F3FF, false);
            return;
        }

        int gap = 8;
        int cols = width >= 520 ? 2 : 1;
        int cardH = 46;
        int cardW = Math.max(120, (width - (gap * (cols - 1))) / cols);
        int rows = (cards.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (height + gap) / (cardH + gap));
        int maxRowScroll = Math.max(0, rows - visibleRows);
        outputScroll = Math.max(0, Math.min(outputScroll, maxRowScroll));

        int startRow = outputScroll;
        int endRow = Math.min(rows, startRow + visibleRows);
        for (int row = startRow; row < endRow; row++) {
            int rowY = y + ((row - startRow) * (cardH + gap));
            for (int col = 0; col < cols; col++) {
                int idx = (row * cols) + col;
                if (idx >= cards.size()) {
                    break;
                }
                int cardX = x + (col * (cardW + gap));
                String raw = cards.get(idx);
                drawOverviewEntryCard(graphics, action, raw, cardX, rowY, cardW, cardH);
                if ("SHOW_ACCOUNTS".equalsIgnoreCase(action)) {
                    AccountCardData account = parseAccountCard(raw);
                    if (account != null) {
                        visibleAccountCards.add(new AccountCardHitbox(cardX, rowY, cardW, cardH, account));
                    }
                }
            }
        }
    }

    private List<String> extractOverviewCardEntries() {
        List<String> base = ClientOwnerPcData.getActionOutputLines();
        if (base.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (int i = 0; i < base.size(); i++) {
            String line = base.get(i) == null ? "" : base.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && line.startsWith("Bank:")) {
                continue;
            }
            if (line.startsWith("- ")) {
                out.add(line.substring(2).trim());
            } else if (!line.contains("(") || !line.endsWith(")")) {
                out.add(line);
            }
        }
        return out;
    }

    private void drawOverviewEntryCard(GuiGraphics graphics,
                                       String action,
                                       String text,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2F4F73);
        graphics.fill(x, y, x + width, y + height, 0x7A1B334E);
        graphics.fill(x, y, x + width, y + 2, 0xFF69B8FF);

        String normalizedAction = action == null ? "" : action.toUpperCase(Locale.ROOT);
        if ("SHOW_ACCOUNTS".equals(normalizedAction) && text.contains("|")) {
            String[] parts = text.split("\\|");
            String player = parts.length > 0 ? parts[0].trim() : "Account";
            String type = parts.length > 1 ? parts[1].trim() : "";
            String balance = parts.length > 2 ? parts[2].trim() : "";
            String id = parts.length > 3 ? parts[3].trim() : "";

            int avatarX = x + 6;
            int avatarY = y + 8;
            graphics.fill(avatarX, avatarY, avatarX + 16, avatarY + 16, 0xFF5D8EBE);
            String initials = player.isBlank() ? "?" : player.substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(this.font, initials, avatarX + 8, avatarY + 4, 0xFFFFFFFF);

            int textX = avatarX + 22;
            graphics.drawString(this.font, fitToWidth(player, width - 30), textX, y + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(type + "  " + balance, width - 30), textX, y + 20, 0xFFD4E8FF, false);
            graphics.drawString(this.font, fitToWidth(id, width - 88), textX, y + 32, 0xFFB7CBE3, false);
            graphics.drawString(this.font, "Open", x + width - 28, y + 32, 0xFF9EC9F0, false);
        } else if ("SHOW_CDS".equals(normalizedAction) && text.contains("|")) {
            String[] parts = text.split("\\|");
            String id = parts.length > 0 ? parts[0].trim() : "CD";
            String holder = parts.length > 1 ? parts[1].trim() : "";
            String tier = parts.length > 2 ? parts[2].trim() : "";
            String maturity = parts.length > 3 ? parts[3].trim() : "";

            int chipX = x + 6;
            int chipY = y + 8;
            graphics.fill(chipX, chipY, chipX + 26, chipY + 14, 0xFF4E9FE0);
            graphics.drawCenteredString(this.font, "CD", chipX + 13, chipY + 3, 0xFFFFFFFF);

            int textX = chipX + 32;
            graphics.drawString(this.font, fitToWidth(id, width - 40), textX, y + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(holder + "  " + tier, width - 40), textX, y + 20, 0xFFD4E8FF, false);
            graphics.drawString(this.font, fitToWidth(maturity, width - 40), textX, y + 32, 0xFFB7CBE3, false);
        } else if ("SHOW_LOANS".equals(normalizedAction) && text.contains("|")) {
            String[] parts = text.split("\\|");
            String id = parts.length > 0 ? parts[0].trim() : "Loan";
            String type = parts.length > 1 ? parts[1].trim() : "";
            String remaining = parts.length > 2 ? parts[2].trim() : "";
            String state = parts.length > 3 ? parts[3].trim() : "";

            int stateColor = state.toUpperCase(Locale.ROOT).contains("OPEN")
                    ? 0xFF4EBD78
                    : state.toUpperCase(Locale.ROOT).contains("OVERDUE")
                    ? 0xFFE0A54E
                    : 0xFF8EA8C2;
            int stateW = Math.min(68, Math.max(40, this.font.width(state) + 8));
            graphics.fill(x + width - stateW - 6, y + 6, x + width - 6, y + 18, stateColor);
            graphics.drawCenteredString(this.font, fitToWidth(state, stateW - 6), x + width - stateW / 2 - 6, y + 9, 0xFF0E2238);

            graphics.drawString(this.font, fitToWidth(id + "  " + type, width - stateW - 18), x + 6, y + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(remaining, width - 12), x + 6, y + 22, 0xFFD4E8FF, false);
        } else {
            graphics.drawString(this.font, fitToWidth(overviewActionLabel(action), width - 12), x + 6, y + 7, 0xFFD8EDFF, false);
            graphics.drawString(this.font, fitToWidth(text, width - 12), x + 6, y + 22, 0xFFFFFFFF, false);
        }
    }

    private AccountCardData parseAccountCard(String raw) {
        if (raw == null || raw.isBlank() || !raw.contains("|")) {
            return null;
        }
        String[] parts = raw.split("\\|");
        String player = parts.length > 0 ? parts[0].trim() : "Account";
        String type = parts.length > 1 ? parts[1].trim() : "-";
        String balance = parts.length > 2 ? parts[2].trim() : "$0.00";
        String id = parts.length > 3 ? parts[3].trim() : "-";
        return new AccountCardData(player, type, balance, id);
    }

    private void drawAccountProfilePanel(GuiGraphics graphics,
                                         AccountCardData account,
                                         int x,
                                         int y,
                                         int width,
                                         int height) {
        graphics.fill(x, y, x + width, y + height, 0x5A1D3550);
        graphics.fill(x, y, x + width, y + 30, 0xB2234B73);
        graphics.fill(x, y + 30, x + width, y + 31, 0x889CC8EE);
        graphics.drawString(this.font, "Account Profile", x + 8, y + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font, fitToWidth(account.player(), width - 150), x + 118, y + 10, 0xFFE2F1FF, false);

        int avatarX = x + 14;
        int avatarY = y + 42;
        int avatarSize = Math.min(56, Math.max(34, height / 4));
        graphics.fill(avatarX - 1, avatarY - 1, avatarX + avatarSize + 1, avatarY + avatarSize + 1, 0xFF2E567D);
        graphics.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF67A5DC);
        String initials = account.player().isBlank() ? "?" : account.player().substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawCenteredString(this.font, initials, avatarX + (avatarSize / 2), avatarY + (avatarSize / 2) - 4, 0xFFFFFFFF);
        graphics.drawString(this.font, fitToWidth(account.type(), width - 120), x + 118, y + 28, 0xFFCFE6FF, false);

        int cardY = avatarY + avatarSize + 10;
        int cardGap = 8;
        int cardCols = width >= 420 ? 2 : 1;
        int cardW = Math.max(140, (width - (cardGap * (cardCols - 1))) / cardCols);
        int cardH = 42;

        drawMetricCard(graphics, x + 10, cardY, cardW, cardH, "Balance", account.balance(), 0xFF67C789);
        if (cardCols > 1) {
            drawMetricCard(graphics, x + 10 + cardW + cardGap, cardY, cardW, cardH, "Account ID", account.id(), 0xFF70B9F2);
        } else {
            drawMetricCard(graphics, x + 10, cardY + cardH + cardGap, cardW, cardH, "Account ID", account.id(), 0xFF70B9F2);
        }

        int detailsTop = cardY + (cardCols > 1 ? (cardH + 12) : ((cardH * 2) + cardGap + 12));
        int detailsBottom = y + height - 8;
        if (detailsBottom - detailsTop >= 54) {
            graphics.fill(x + 10, detailsTop, x + width - 10, detailsBottom, 0x6A16304A);
            graphics.fill(x + 10, detailsTop, x + width - 10, detailsTop + 1, 0x88A8CDEE);

            int rowY = detailsTop + 8;
            graphics.drawString(this.font, "Player", x + 18, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.player(), width - 150), x + 106, rowY, 0xFFFFFFFF, false);
            rowY += 12;
            graphics.drawString(this.font, "Type", x + 18, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.type(), width - 150), x + 106, rowY, 0xFFFFFFFF, false);
            rowY += 12;
            graphics.drawString(this.font, "Balance", x + 18, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.balance(), width - 150), x + 106, rowY, 0xFFFFFFFF, false);
            rowY += 12;
            graphics.drawString(this.font, "Account ID", x + 18, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.id(), width - 150), x + 106, rowY, 0xFFFFFFFF, false);
            rowY += 12;
            graphics.drawString(this.font, "Status", x + 18, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, "Active", x + 106, rowY, 0xFF8BE3A8, false);
        }
    }

    private void drawLendingMarketPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x5A1C334D);
        graphics.fill(x, y, x + width, y + 24, 0xB2234B73);
        graphics.fill(x, y + 24, x + width, y + 25, 0x88A8CDEE);

        List<MarketOfferData> offers = getSortedMarketOffers();
        String header = "Interbank Market";
        String stats = "Offers: " + offers.size()
                + "   Sort: " + marketSortLabel(marketSort)
                + " (" + (marketSortDescending ? "desc" : "asc") + ")";
        graphics.drawString(this.font, fitToWidth(header, width - 16), x + 8, y + 7, 0xFFFFFFFF, false);
        graphics.drawString(this.font, fitToWidth(stats, width - 16), x + 110, y + 7, 0xFFCFE5FF, false);

        int listX = x + 4;
        int listY = y + 30;
        int listW = Math.max(80, width - 8);
        int listH = Math.max(24, height - 34);
        if (listH <= 24) {
            return;
        }

        boolean waitingForMarket = ClientOwnerPcData.getActionOutputLines().isEmpty() && marketOfferCache.isEmpty();
        if (offers.isEmpty()) {
            graphics.fill(listX, listY, listX + listW, listY + listH, 0x55203A57);
            if (waitingForMarket) {
                graphics.drawString(this.font, "Loading market offers...", listX + 8, listY + 8, 0xFFD3E8FF, false);
                graphics.drawString(this.font, "Please wait or press Refresh Market.", listX + 8, listY + 20, 0xFFAACAE9, false);
            } else {
                graphics.drawString(this.font, "No open market offers right now.", listX + 8, listY + 8, 0xFFD3E8FF, false);
                graphics.drawString(this.font, "Use Refresh Market to check again.", listX + 8, listY + 20, 0xFFAACAE9, false);
            }
        } else {
            int cols = listW >= 640 ? 2 : 1;
            int gap = 10;
            int cardH = 76;
            int computedCardW = (listW - (gap * (cols - 1))) / cols;
            int cardW = Math.max(170, computedCardW);
            if (cardW > listW) {
                cardW = listW;
                cols = 1;
            }

            int rows = (offers.size() + cols - 1) / cols;
            int visibleRows = Math.max(1, (listH + gap) / (cardH + gap));
            int maxRowScroll = Math.max(0, rows - visibleRows);
            outputScroll = Math.max(0, Math.min(outputScroll, maxRowScroll));

            graphics.enableScissor(listX, listY, listX + listW, listY + listH);
            int startRow = outputScroll;
            int endRow = Math.min(rows, startRow + visibleRows);
            for (int row = startRow; row < endRow; row++) {
                int rowY = listY + ((row - startRow) * (cardH + gap));
                for (int col = 0; col < cols; col++) {
                    int idx = (row * cols) + col;
                    if (idx >= offers.size()) {
                        break;
                    }
                    int cardX = listX + (col * (cardW + gap));
                    drawLendingMarketCard(graphics, offers.get(idx), cardX, rowY, cardW, cardH);
                }
            }
            graphics.disableScissor();

            if (maxRowScroll > 0) {
                int barX1 = listX + listW - 4;
                int barX2 = listX + listW - 1;
                graphics.fill(barX1, listY + 1, barX2, listY + listH - 1, 0x553C5878);
                int thumbH = Math.max(10, (int) ((listH - 2) * (visibleRows / (float) rows)));
                int thumbTravel = Math.max(1, (listH - 2) - thumbH);
                int thumbY = listY + 1 + (int) (thumbTravel * (outputScroll / (float) maxRowScroll));
                graphics.fill(barX1, thumbY, barX2, thumbY + thumbH, 0xCC9FD1FF);
            }
        }

        if (pendingMarketAccept != null) {
            drawMarketConfirmOverlay(graphics, pendingMarketAccept, x + 6, y + 6, width - 12, height - 12);
        }
    }

    private void drawLendingMarketCard(GuiGraphics graphics,
                                       MarketOfferData offer,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2F4F73);
        graphics.fill(x, y, x + width, y + height, 0x7A1B334E);
        graphics.fill(x, y, x + width, y + 2, 0xFF6AB8FF);

        int idBadgeW = Math.min(110, Math.max(68, this.font.width(offer.id()) + 10));
        graphics.fill(x + width - idBadgeW - 8, y + 6, x + width - 8, y + 18, 0x884E84B2);
        graphics.drawCenteredString(this.font, fitToWidth(offer.id(), idBadgeW - 8), x + width - (idBadgeW / 2) - 8, y + 9, 0xFFDDF0FF);

        graphics.drawString(this.font, fitToWidth(offer.lender(), width - idBadgeW - 24), x + 8, y + 8, 0xFFFFFFFF, false);
        graphics.drawString(this.font, fitToWidth("Amount: " + offer.amountText(), width - 16), x + 8, y + 22, 0xFFD4E8FF, false);
        graphics.drawString(this.font, fitToWidth("APR: " + offer.aprText() + "   Term: " + offer.termText(), width - 16), x + 8, y + 34, 0xFFC3DCF7, false);

        int btnY = y + height - 21;
        int btnW = Math.max(64, (width - 24) / 2);
        int acceptX = x + 8;
        int copyX = x + width - 8 - btnW;

        drawInlineActionButton(graphics, acceptX, btnY, btnW, 16, "Accept Offer", 0xFF67BC86);
        drawInlineActionButton(graphics, copyX, btnY, btnW, 16, "Copy ID", 0xFF5E9ED0);

        visibleMarketActions.add(new MarketActionHitbox(acceptX, btnY, btnW, 16, "ACCEPT", offer));
        visibleMarketActions.add(new MarketActionHitbox(copyX, btnY, btnW, 16, "COPY", offer));
    }

    private void drawInlineActionButton(GuiGraphics graphics,
                                        int x,
                                        int y,
                                        int width,
                                        int height,
                                        String label,
                                        int accent) {
        graphics.fill(x, y, x + width, y + height, 0xFF2E5277);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xB01D3D5E);
        graphics.fill(x + 1, y + 1, x + 3, y + height - 1, accent);
        graphics.drawCenteredString(this.font, fitToWidth(label, width - 8), x + (width / 2), y + 4, 0xFFEAF5FF);
    }

    private void drawMarketConfirmOverlay(GuiGraphics graphics,
                                          MarketOfferData offer,
                                          int x,
                                          int y,
                                          int width,
                                          int height) {
        graphics.fill(x, y, x + width, y + height, 0xAA071625);
        int modalW = Math.min(380, Math.max(220, width - 26));
        int modalH = 110;
        int modalX = x + (width - modalW) / 2;
        int modalY = y + (height - modalH) / 2;

        graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF335476);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xE019324A);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 24, 0xC0254D76);
        graphics.fill(modalX, modalY + 24, modalX + modalW, modalY + 25, 0x88A8CDEE);

        graphics.drawString(this.font, "Confirm Offer Acceptance", modalX + 8, modalY + 7, 0xFFFFFFFF, false);
        graphics.drawString(this.font, fitToWidth("Offer " + offer.id() + " from " + offer.lender(), modalW - 16), modalX + 8, modalY + 34, 0xFFD6E9FF, false);
        graphics.drawString(this.font, fitToWidth("Amount " + offer.amountText() + " at " + offer.aprText(), modalW - 16), modalX + 8, modalY + 46, 0xFFD6E9FF, false);
        graphics.drawString(this.font, fitToWidth("Term: " + offer.termText(), modalW - 16), modalX + 8, modalY + 58, 0xFFC4DBF7, false);

        int btnY = modalY + modalH - 24;
        int btnW = (modalW - 24) / 2;
        int acceptX = modalX + 8;
        int cancelX = modalX + modalW - 8 - btnW;
        drawInlineActionButton(graphics, acceptX, btnY, btnW, 16, "Confirm Accept", 0xFF67BC86);
        drawInlineActionButton(graphics, cancelX, btnY, btnW, 16, "Cancel", 0xFF5E9ED0);
        marketConfirmAcceptHitbox = new RectHitbox(acceptX, btnY, btnW, 16);
        marketConfirmCancelHitbox = new RectHitbox(cancelX, btnY, btnW, 16);
    }

    private List<MarketOfferData> getSortedMarketOffers() {
        MarketParseResult parsed = parseMarketOffersFromOutput();
        if (parsed.isMarketPayload()) {
            marketOfferCache.clear();
            marketOfferCache.addAll(parsed.offers());
        }

        List<MarketOfferData> sorted = new ArrayList<>(marketOfferCache);
        java.util.Comparator<MarketOfferData> comparator = switch (marketSort) {
            case AMOUNT -> java.util.Comparator.comparing(MarketOfferData::amountValue);
            case APR -> java.util.Comparator.comparing(MarketOfferData::aprValue);
            case TERM -> java.util.Comparator.comparingLong(MarketOfferData::termTicks);
            case LENDER -> java.util.Comparator.comparing(MarketOfferData::lender, String.CASE_INSENSITIVE_ORDER);
            case ID -> java.util.Comparator.comparing(MarketOfferData::id, String.CASE_INSENSITIVE_ORDER);
        };
        if (marketSortDescending) {
            comparator = comparator.reversed();
        }
        sorted.sort(comparator.thenComparing(MarketOfferData::id, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private MarketParseResult parseMarketOffersFromOutput() {
        List<String> lines = ClientOwnerPcData.getActionOutputLines();
        if (lines.isEmpty()) {
            return new MarketParseResult(false, List.of());
        }

        boolean hasMarketHeader = false;
        List<MarketOfferData> offers = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("Bank:")) {
                continue;
            }
            if (line.startsWith("Open Market Offers")) {
                hasMarketHeader = true;
                continue;
            }
            if (line.startsWith("- ")) {
                line = line.substring(2).trim();
            }
            if (!line.contains("|") || !line.toUpperCase(Locale.ROOT).contains("APR")) {
                continue;
            }
            String[] parts = line.split("\\|");
            if (parts.length < 5) {
                continue;
            }

            String id = parts[0].trim();
            String lender = parts[1].trim();
            String amount = parts[2].trim();
            String apr = parts[3].trim();
            String term = parts[4].trim();
            offers.add(new MarketOfferData(
                    id,
                    lender,
                    amount,
                    apr,
                    term,
                    parseFlexibleDecimal(amount),
                    parseFlexibleDecimal(apr),
                    parseFlexibleLong(term)
            ));
        }

        return new MarketParseResult(hasMarketHeader, offers);
    }

    private BigDecimal parseFlexibleDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') {
                filtered.append(c);
            }
        }
        if (filtered.isEmpty() || filtered.toString().equals("-") || filtered.toString().equals(".")) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(filtered.toString());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private long parseFlexibleLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c >= '0' && c <= '9') {
                filtered.append(c);
            }
        }
        if (filtered.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(filtered.toString());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private InputHelp getFocusedInputHelp() {
        for (Map.Entry<String, DesktopEditBox> entry : activeFormInputs.entrySet()) {
            DesktopEditBox input = entry.getValue();
            if (input != null && input.visible && input.active && input.isFocused()) {
                return helpForInput(entry.getKey());
            }
        }
        return null;
    }

    private InputHelp helpForInput(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return switch (key) {
            case "lend.borrow" -> new InputHelp(
                    "Borrow Amount",
                    "How much your bank borrows from central bank in one request.",
                    "Example: 50000"
            );
            case "lend.offer.amount" -> new InputHelp(
                    "Offer Amount",
                    "Total money offered on the interbank market for another bank to accept.",
                    "Example: 250000"
            );
            case "lend.offer.rate" -> new InputHelp(
                    "APR (%)",
                    "Annual percentage rate charged on the offer.",
                    "Example: 6.5"
            );
            case "lend.offer.term" -> new InputHelp(
                    "Term (Ticks)",
                    "Loan duration in Minecraft ticks (20 ticks = 1 second).",
                    "Example: 24000 (20 minutes)"
            );
            case "lend.accept.id" -> new InputHelp(
                    "Offer UUID",
                    "Paste the offer id from Market to accept a specific interbank offer.",
                    "Example: 6f2a9c41-..."
            );
            case "lend.product.name" -> new InputHelp(
                    "Product Name",
                    "Display name of the loan product shown to staff/owners.",
                    "Example: Small Business Loan"
            );
            case "lend.product.max" -> new InputHelp(
                    "Max Amount",
                    "Highest principal allowed for this product.",
                    "Example: 1000000"
            );
            case "lend.product.rate" -> new InputHelp(
                    "Product APR (%)",
                    "Interest rate for this loan product.",
                    "Example: 8.25"
            );
            case "lend.product.duration" -> new InputHelp(
                    "Duration (Ticks)",
                    "Repayment window in ticks for this product.",
                    "Example: 1728000 (1 in-game day)"
            );
            case "limits.type" -> new InputHelp(
                    "Limit Type",
                    "Use only: single, dailyplayer, or dailybank. Each type updates a different rule.",
                    "Example: dailyplayer"
            );
            case "limits.amount" -> new InputHelp(
                    "Limit Amount",
                    "Positive whole number used by the selected limit type.",
                    "Example: 25000"
            );
            default -> null;
        };
    }

    private void drawInputHelpPanel(GuiGraphics graphics,
                                    InputHelp help,
                                    int x,
                                    int y,
                                    int width,
                                    int height) {
        graphics.fill(x, y, x + width, y + height, 0x4E18324C);
        graphics.fill(x, y, x + width, y + 26, 0xB0214A73);
        graphics.fill(x, y + 26, x + width, y + 27, 0x88A8CDEE);
        graphics.drawString(this.font, "Input Assistant", x + 8, y + 9, 0xFFFFFFFF, false);

        int titleY = y + 36;
        graphics.drawString(this.font, fitToWidth(help.title(), width - 16), x + 8, titleY, 0xFFE6F3FF, false);

        List<String> summaryLines = wrapLines(List.of(help.summary()), Math.max(80, width - 16));
        int lineY = titleY + 14;
        for (String line : summaryLines) {
            if (lineY > y + height - 28) {
                break;
            }
            graphics.drawString(this.font, line, x + 8, lineY, 0xFFCFE5FF, false);
            lineY += LINE_HEIGHT;
        }

        if (lineY <= y + height - 20) {
            graphics.drawString(this.font, "Example: " + fitToWidth(help.example(), width - 66), x + 8, lineY + 4, 0xFF98E2AF, false);
        }
    }

    private void drawMetricCard(GuiGraphics graphics,
                                int x,
                                int y,
                                int width,
                                int height,
                                String label,
                                String value,
                                int accent) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2E4D6D);
        graphics.fill(x, y, x + width, y + height, 0x8A1A304A);
        graphics.fill(x, y, x + width, y + 2, accent);
        graphics.drawString(this.font, label, x + 6, y + 7, 0xFFC6DEF7, false);
        graphics.drawString(this.font, fitToWidth(value, Math.max(40, width - 12)), x + 6, y + 22, 0xFFFFFFFF, false);
    }

    private void drawBarCard(GuiGraphics graphics,
                             int x,
                             int y,
                             int width,
                             int height,
                             String title,
                             float value,
                             String subtitle,
                             int accent) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF355474);
        graphics.fill(x, y, x + width, y + height, 0x75192D45);
        graphics.drawString(this.font, fitToWidth(title, width - 10), x + 6, y + 6, 0xFFE5F3FF, false);

        int barX = x + 6;
        int barY = y + 21;
        int barW = width - 12;
        int barH = 14;
        graphics.fill(barX, barY, barX + barW, barY + barH, 0x663A4D63);
        graphics.fill(barX, barY, barX + Math.max(1, (int) (barW * clamped)), barY + barH, accent);
        graphics.fill(barX, barY, barX + barW, barY + 1, 0x66FFFFFF);

        graphics.drawString(this.font, Math.round(clamped * 100.0F) + "%", barX, barY + 18, 0xFFCFE5FF, false);
        graphics.drawString(this.font, fitToWidth(subtitle, Math.max(20, width - 62)), barX + 40, barY + 18, 0xFFBCD7F3, false);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private String compactCurrency(String value) {
        BigDecimal amount = parseDecimal(value).abs();
        String suffix = "";
        BigDecimal divisor = BigDecimal.ONE;
        if (amount.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) >= 0) {
            suffix = "T";
            divisor = BigDecimal.valueOf(1_000_000_000_000L);
        } else if (amount.compareTo(BigDecimal.valueOf(1_000_000_000L)) >= 0) {
            suffix = "B";
            divisor = BigDecimal.valueOf(1_000_000_000L);
        } else if (amount.compareTo(BigDecimal.valueOf(1_000_000L)) >= 0) {
            suffix = "M";
            divisor = BigDecimal.valueOf(1_000_000L);
        } else if (amount.compareTo(BigDecimal.valueOf(1_000L)) >= 0) {
            suffix = "K";
            divisor = BigDecimal.valueOf(1_000L);
        }

        BigDecimal shortened = parseDecimal(value).divide(divisor, 2, RoundingMode.HALF_UP);
        String out = shortened.stripTrailingZeros().toPlainString();
        return out + suffix;
    }

    private List<String> getWrappedOutputLines() {
        List<String> base = ClientOwnerPcData.getActionOutputLines();
        if (base.isEmpty() || outputPanelW <= 0) {
            return List.of();
        }
        return wrapLines(base, Math.max(1, outputPanelW - 14));
    }

    private List<String> wrapLines(List<String> lines, int maxWidth) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<String> wrapped = new ArrayList<>();
        for (String raw : lines) {
            String line = raw == null ? "" : raw;
            if (line.isEmpty()) {
                wrapped.add("");
                continue;
            }
            if (this.font.width(line) <= maxWidth) {
                wrapped.add(line);
                continue;
            }

            String[] words = line.split(" ");
            String current = "";
            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (this.font.width(candidate) <= maxWidth) {
                    current = candidate;
                } else {
                    if (!current.isEmpty()) {
                        wrapped.add(current);
                    }
                    current = word;
                    while (this.font.width(current) > maxWidth && current.length() > 1) {
                        int cut = current.length() - 1;
                        while (cut > 1 && this.font.width(current.substring(0, cut) + "-") > maxWidth) {
                            cut--;
                        }
                        wrapped.add(current.substring(0, cut) + "-");
                        current = current.substring(cut);
                    }
                }
            }
            if (!current.isEmpty()) {
                wrapped.add(current);
            }
        }
        return wrapped;
    }

    private void drawCreateWindowFrame(GuiGraphics graphics) {
        int width = Math.min(740, this.width - (PAD * 2) - 40);
        int left = (this.width - width) / 2;
        int top = PAD + TOPBAR_HEIGHT + 20;
        int right = left + width;
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 20;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF2A3D59);
        graphics.fill(left, top, right, bottom, 0xFFE8EEF6);
        graphics.fill(left, top, right, top + 28, 0xFF6C93C8);

        graphics.drawString(this.font, "Create New Player Bank", left + 8, top + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font,
                "Owned: " + ClientOwnerPcData.getOwnedCount() + " / " + ClientOwnerPcData.getMaxBanks(),
                left + 230,
                top + 10,
                0xFFE8F2FF,
                false);

        graphics.drawString(this.font, "Ownership Type", left + 2, top + 70, 0xFF1D2F4A, false);
        graphics.drawString(this.font,
                "Selected: " + prettifyOwnership(selectedOwnershipModel),
                left + 2,
                top + 82,
                0xFF2A496E,
                false);
    }

    private void drawToast(GuiGraphics graphics) {
        String message = ClientOwnerPcData.getToastMessage();
        if (message.isBlank()) {
            return;
        }
        int color = ClientOwnerPcData.isToastSuccess() ? 0xD7489458 : 0xD79B3A43;

        int boxWidth = Math.min(this.width - 40, this.font.width(message) + 18);
        int x = (this.width - boxWidth) / 2;
        int y = PAD + TOPBAR_HEIGHT + 4;
        graphics.fill(x - 1, y - 1, x + boxWidth + 1, y + 19, 0xAA102030);
        graphics.fill(x, y, x + boxWidth, y + 18, color);
        graphics.drawCenteredString(this.font, fitToWidth(message, boxWidth - 10), x + boxWidth / 2, y + 5, 0xFFFFFFFF);
    }

    private String prettifyOwnership(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Sole";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "ROLE_BASED" -> "Role Based";
            case "PERCENTAGE_SHARES" -> "Percentage Shares";
            case "FIXED_COFOUNDERS" -> "Fixed Cofounders";
            default -> "Sole";
        };
    }

    private void configureVirtualScale() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int currentScale = 2;
        if (mc != null && mc.options != null && mc.options.guiScale() != null) {
            Integer opt = mc.options.guiScale().get();
            if (opt != null && opt > 0) {
                currentScale = opt;
            }
        }

        if (currentScale == 2) {
            useVirtualScale = false;
            virtualScaleX = 1.0F;
            virtualScaleY = 1.0F;
            return;
        }

        float desiredFactor = currentScale / 2.0F;
        int actualW = Math.max(1, this.width);
        int actualH = Math.max(1, this.height);
        int virtualW = Math.max(320, Math.round(actualW * desiredFactor));
        int virtualH = Math.max(240, Math.round(actualH * desiredFactor));

        useVirtualScale = true;
        virtualScaleX = actualW / (float) virtualW;
        virtualScaleY = actualH / (float) virtualH;
        this.width = virtualW;
        this.height = virtualH;
    }

    private double toLocalX(double x) {
        if (!useVirtualScale || virtualScaleX == 0.0F) {
            return x;
        }
        return x / virtualScaleX;
    }

    private double toLocalY(double y) {
        if (!useVirtualScale || virtualScaleY == 0.0F) {
            return y;
        }
        return y / virtualScaleY;
    }

    private double toLocalDeltaX(double x) {
        if (!useVirtualScale || virtualScaleX == 0.0F) {
            return x;
        }
        return x / virtualScaleX;
    }

    private double toLocalDeltaY(double y) {
        if (!useVirtualScale || virtualScaleY == 0.0F) {
            return y;
        }
        return y / virtualScaleY;
    }

    private static int lerpColor(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int a1 = (from >>> 24) & 0xFF;
        int r1 = (from >>> 16) & 0xFF;
        int g1 = (from >>> 8) & 0xFF;
        int b1 = from & 0xFF;
        int a2 = (to >>> 24) & 0xFF;
        int r2 = (to >>> 16) & 0xFF;
        int g2 = (to >>> 8) & 0xFF;
        int b2 = to & 0xFF;

        int a = (int) (a1 + (a2 - a1) * clamped);
        int r = (int) (r1 + (r2 - r1) * clamped);
        int g = (int) (g1 + (g2 - g1) * clamped);
        int b = (int) (b1 + (b2 - b1) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String fitToWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        String ellipsis = "...";
        int ellipsisWidth = this.font.width(ellipsis);
        int end = text.length();
        while (end > 0 && this.font.width(text.substring(0, end)) + ellipsisWidth > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
