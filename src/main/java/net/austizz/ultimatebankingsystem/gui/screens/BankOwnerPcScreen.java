package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.OpenBankOwnerPcPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcCreateBankPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcFileEntry;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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

    private record ExplorerAppEntry(String appId, String label, boolean hidden, boolean lockHide) {}

    private record AppVisibilityCard(int x, int y, int width, int height, ExplorerAppEntry app) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record NotepadLayout(List<String> lines, List<Integer> starts) {}

    private record RectHitbox(int x, int y, int width, int height) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record MarketParseResult(boolean isMarketPayload, List<MarketOfferData> offers) {}

    private static final class BankWindowState {
        private final UUID bankId;
        private Section activeSection = Section.OVERVIEW;
        private int outputScroll;
        private int sectionScroll;
        private int navScroll;
        private boolean overviewDetailOpen;
        private String overviewDetailAction = "SHOW_INFO";
        private AccountCardData selectedAccountCard;
        private boolean accountProfileOpen;
        private boolean lendingMarketOpen;
        private MarketSort marketSort = MarketSort.AMOUNT;
        private boolean marketSortDescending = true;
        private final List<MarketOfferData> marketOfferCache = new ArrayList<>();
        private MarketOfferData pendingMarketAccept;
        private boolean refreshMarketAfterNextResponse;
        private final Map<String, String> formValues = new HashMap<>();

        private BankWindowState(UUID bankId) {
            this.bankId = bankId;
        }
    }

    private enum WindowMode {
        DESKTOP,
        BANK_APP,
        CREATE_BANK,
        UTILITY_APP
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

    private enum UtilityApp {
        CALCULATOR,
        NOTEPAD,
        FILE_EXPLORER,
        PAINT,
        SYSTEM_MONITOR
    }

    private enum AuthStage {
        LOADING,
        LOGIN,
        SETUP,
        RECOVER
    }

    private static final int PAD = 8;
    private static final int TOPBAR_HEIGHT = 26;
    private static final int TASKBAR_HEIGHT = 26;
    private static final int LINE_HEIGHT = 11;
    private static final int OUTPUT_PANEL_INSET = 6;
    private static final int OUTPUT_PIXEL_SCROLL_STEP = 14;
    private static final int NOTEPAD_MAX_CHARS = 16000;
    private static final List<String> OWNERSHIP_MODELS = List.of(
            "SOLE",
            "ROLE_BASED",
            "PERCENTAGE_SHARES",
            "FIXED_COFOUNDERS"
    );
    private static final List<UtilityApp> DESKTOP_UTILITY_APPS = List.of(
            UtilityApp.CALCULATOR,
            UtilityApp.NOTEPAD,
            UtilityApp.FILE_EXPLORER,
            UtilityApp.PAINT,
            UtilityApp.SYSTEM_MONITOR
    );

    private WindowMode activeWindow = WindowMode.DESKTOP;
    private Section activeSection = Section.OVERVIEW;

    private boolean bankWindowOpen;
    private boolean createWindowOpen;
    private UUID activeBankId;
    private final Map<UUID, BankWindowState> bankWindows = new HashMap<>();
    private final List<UUID> bankWindowOrder = new ArrayList<>();
    private final List<UtilityApp> utilityWindowOrder = new ArrayList<>();
    private UtilityApp activeUtilityApp;

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
    private RectHitbox accountProfileCopyIdHitbox;
    private boolean refreshMarketAfterNextResponse;
    private boolean useVirtualScale;
    private float virtualScaleX = 1.0F;
    private float virtualScaleY = 1.0F;

    private int utilityFrameLeft;
    private int utilityFrameTop;
    private int utilityFrameRight;
    private int utilityFrameBottom;
    private int utilityContentX;
    private int utilityContentY;
    private int utilityContentW;
    private int utilityContentH;
    private int notepadAreaX;
    private int notepadAreaY;
    private int notepadAreaW;
    private int notepadAreaH;
    private int paintCanvasX;
    private int paintCanvasY;
    private int paintCanvasW = 48;
    private int paintCanvasH = 32;
    private int paintCellSize = 8;
    private boolean paintDrawing;
    private int paintDrawColor = 0xFF111111;

    private String calculatorExpression = "";
    private String calculatorDisplay = "0";
    private String calculatorStatus = "Ready";

    private final StringBuilder notepadText = new StringBuilder();
    private boolean notepadFocused;
    private int notepadScroll;
    private int notepadCursorIndex;
    private boolean suppressNextNotepadSpaceChar;
    private boolean notepadSaveModalOpen;
    private boolean paintSaveModalOpen;
    private boolean systemHideAppsMenuOpen;
    private int systemMonitorScroll;
    private int systemMonitorMaxScroll;
    private int systemMonitorViewportX;
    private int systemMonitorViewportY;
    private int systemMonitorViewportW;
    private int systemMonitorViewportH;
    private int systemHideAppsScroll;
    private int systemHideAppsMaxScroll;
    private int systemHideAppsX;
    private int systemHideAppsY;
    private int systemHideAppsW;
    private int systemHideAppsH;
    private final List<AppVisibilityCard> visibleSystemAppCards = new ArrayList<>();
    private boolean unsavedClosePromptOpen;
    private UtilityApp unsavedCloseTarget;
    private UtilityApp pendingCloseAfterSaveTarget;
    private String notepadSavedSnapshot = "";
    private int paintSavedSnapshotHash;
    private int explorerFilesScroll;
    private int explorerFilesMaxScroll;
    private int explorerFileListX;
    private int explorerFileListY;
    private int explorerFileListW;
    private int explorerFileListH;
    private String selectedExplorerFileName = "";
    private int paintControlsScroll;
    private int paintControlsMaxScroll;
    private int paintControlsX;
    private int paintControlsY;
    private int paintControlsW;
    private int paintControlsH;
    private int taskbarScroll;
    private int taskbarMaxScroll;
    private int taskbarViewportX;
    private int taskbarViewportY;
    private int taskbarViewportW;
    private int taskbarViewportH;
    private RectHitbox taskbarClockHitbox;
    private RectHitbox taskbarMenuHitbox;
    private RectHitbox taskbarLogoutHitbox;
    private RectHitbox taskbarTurnOffHitbox;
    private boolean desktopAuthenticated;
    private boolean authInitialized;
    private AuthStage authStage = AuthStage.LOADING;
    private boolean taskbarMenuOpen;
    private boolean discardCachedScreenOnClose;
    private String boundDesktopComputerId = "";
    private Integer previousGuiScale;
    private boolean forcedGuiScaleActive;

    private final int[] paintPixels = new int[48 * 32];
    private int paintSelectedColor = 0xFF111111;
    private int paintBrushSize = 1;
    private final int[] paintPalette = {
            0xFF111111, 0xFF2A5F9E, 0xFF3E8E41, 0xFFC26A2D,
            0xFFB23333, 0xFF7B57B8, 0xFFD4B03D, 0xFFFFFFFF
    };

    public BankOwnerPcScreen(Component title) {
        super(title);
        Arrays.fill(this.paintPixels, 0xFFFFFFFF);
        this.paintSavedSnapshotHash = Arrays.hashCode(this.paintPixels);
    }

    public void relayoutForCurrentWindow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        applyForcedGuiScale();
        this.resize(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    @Override
    protected void init() {
        applyForcedGuiScale();
        configureVirtualScale();
        initializeAuthStateIfNeeded();
        rebuildWidgets();
    }

    @Override
    public void removed() {
        restoreForcedGuiScale();
        super.removed();
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();
        this.activeFormInputs.clear();
        this.visibleAccountCards.clear();
        this.visibleMarketActions.clear();
        this.visibleSystemAppCards.clear();
        this.marketConfirmAcceptHitbox = null;
        this.marketConfirmCancelHitbox = null;
        this.accountProfileCopyIdHitbox = null;
        this.taskbarClockHitbox = null;
        this.taskbarMenuHitbox = null;
        this.taskbarLogoutHitbox = null;
        this.taskbarTurnOffHitbox = null;

        if (!desktopAuthenticated) {
            initAuthWidgets();
            initTaskbarWidgets();
            return;
        }

        if (activeWindow == WindowMode.DESKTOP) {
            initDesktopWidgets();
        } else if (activeWindow == WindowMode.BANK_APP) {
            initBankWindowWidgets();
        } else if (activeWindow == WindowMode.CREATE_BANK) {
            initCreateBankWidgets();
        } else if (activeWindow == WindowMode.UTILITY_APP) {
            initUtilityWindowWidgets();
        }
        initTaskbarWidgets();
    }

    private void initializeAuthStateIfNeeded() {
        syncBoundDesktopContextIfNeeded();
        if (authInitialized) {
            return;
        }
        authInitialized = true;
        desktopAuthenticated = ClientOwnerPcData.isDesktopSessionUnlocked();
        if (!ClientOwnerPcData.hasDesktopDataLoaded()) {
            authStage = AuthStage.LOADING;
            return;
        }
        if (desktopAuthenticated) {
            authStage = ClientOwnerPcData.isDesktopPinSet() ? AuthStage.LOGIN : AuthStage.SETUP;
            return;
        }
        authStage = ClientOwnerPcData.isDesktopPinSet() ? AuthStage.LOGIN : AuthStage.SETUP;
    }

    private void syncAuthStateFromDesktopData() {
        syncBoundDesktopContextIfNeeded();
        if (ClientOwnerPcData.isDesktopSessionUnlocked()) {
            desktopAuthenticated = true;
            return;
        }
        if (desktopAuthenticated) {
            return;
        }
        if (!ClientOwnerPcData.hasDesktopDataLoaded()) {
            authStage = AuthStage.LOADING;
            return;
        }
        boolean pinSet = ClientOwnerPcData.isDesktopPinSet();
        if (!pinSet) {
            authStage = AuthStage.SETUP;
        } else if (authStage == AuthStage.LOADING || authStage == AuthStage.SETUP) {
            authStage = AuthStage.LOGIN;
        }
    }

    private void syncBoundDesktopContextIfNeeded() {
        String incomingId = ClientOwnerPcData.getDesktopComputerId();
        if (incomingId == null) {
            incomingId = "";
        }
        if (incomingId.equals(boundDesktopComputerId)) {
            return;
        }
        resetForDesktopContextSwitch();
        boundDesktopComputerId = incomingId;
    }

    private void resetForDesktopContextSwitch() {
        bankWindows.clear();
        bankWindowOrder.clear();
        utilityWindowOrder.clear();
        bankWindowOpen = false;
        createWindowOpen = false;
        activeBankId = null;
        activeUtilityApp = null;
        activeWindow = WindowMode.DESKTOP;
        activeSection = Section.OVERVIEW;
        outputScroll = 0;
        sectionScroll = 0;
        navScroll = 0;
        sectionMaxScroll = 0;
        navMaxScroll = 0;
        taskbarScroll = 0;
        taskbarMaxScroll = 0;
        overviewDetailOpen = false;
        overviewDetailAction = "SHOW_INFO";
        selectedAccountCard = null;
        accountProfileOpen = false;
        lendingMarketOpen = false;
        pendingMarketAccept = null;
        refreshMarketAfterNextResponse = false;
        marketOfferCache.clear();
        formValues.clear();
        activeFormInputs.clear();
        selectedOwnershipModel = OWNERSHIP_MODELS.getFirst();
        selectedExplorerFileName = "";
        explorerFilesScroll = 0;
        systemMonitorScroll = 0;
        systemHideAppsScroll = 0;
        paintControlsScroll = 0;
        notepadFocused = false;
        notepadScroll = 0;
        notepadCursorIndex = 0;
        notepadSaveModalOpen = false;
        paintSaveModalOpen = false;
        unsavedClosePromptOpen = false;
        unsavedCloseTarget = null;
        pendingCloseAfterSaveTarget = null;
        taskbarMenuOpen = false;
        systemHideAppsMenuOpen = false;
        calculatorExpression = "";
        calculatorDisplay = "0";
        calculatorStatus = "Ready";
        notepadText.setLength(0);
        notepadSavedSnapshot = "";
        Arrays.fill(this.paintPixels, 0xFFFFFFFF);
        this.paintSavedSnapshotHash = Arrays.hashCode(this.paintPixels);
        ClientOwnerPcData.clearActionOutput();
    }

    private void initAuthWidgets() {
        syncAuthStateFromDesktopData();
        int contentTop = PAD + TOPBAR_HEIGHT + 6;
        int contentBottom = this.height - PAD - TASKBAR_HEIGHT - 6;
        int contentHeight = Math.max(160, contentBottom - contentTop);
        int panelW = Math.min(460, Math.max(300, this.width - 84));

        int neededH = switch (authStage) {
            case LOADING -> 148;
            case LOGIN -> 214;
            case SETUP, RECOVER -> 286;
        };
        int panelH = Math.min(Math.max(neededH, 140), Math.max(140, contentHeight - 8));
        boolean compact = panelH < neededH;
        int panelX = (this.width - panelW) / 2;
        int panelY = contentTop + Math.max(0, (contentHeight - panelH) / 2);

        int fieldW = Math.min(320, panelW - 36);
        int fieldX = panelX + (panelW - fieldW) / 2;
        int controlsNeeded = switch (authStage) {
            case LOADING -> 52;
            case LOGIN -> 108;
            case SETUP, RECOVER -> 172;
        };
        int iconY = panelY + (compact ? 10 : 18);
        int avatarSize = compact ? 40 : 52;
        int labelY = iconY + avatarSize + 8;
        int titleY = labelY + 16;
        int subtitleY = titleY + 14;
        int safeTop = subtitleY + 16;
        int y = Math.max(safeTop, panelY + panelH - controlsNeeded - 4);
        int inputStep = compact ? 24 : 28;
        int recoveryStep = compact ? 26 : 32;
        int buttonStep = compact ? 26 : 30;

        if (authStage == AuthStage.LOADING) {
            addPcButton(
                    fieldX,
                    panelY + panelH - 46,
                    fieldW,
                    24,
                    "Refresh Security",
                    btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())
            ).setLabelOffset(6, 1);
            return;
        }

        DesktopEditBox passwordInput = addFormInput(
                "auth.password",
                fieldX,
                y,
                fieldW,
                authStage == AuthStage.LOGIN ? "Password" : "New password"
        );
        passwordInput.setMaxLength(64);
        passwordInput.setTextColor(0xFFFFFFFF);
        y += inputStep;

        if (authStage == AuthStage.SETUP || authStage == AuthStage.RECOVER) {
            DesktopEditBox repeatInput = addFormInput(
                    "auth.password_repeat",
                    fieldX,
                    y,
                    fieldW,
                    "Repeat password"
            );
            repeatInput.setMaxLength(64);
            repeatInput.setTextColor(0xFFFFFFFF);
            y += inputStep;

            DesktopEditBox recoveryInput = addFormInput(
                    "auth.recovery",
                    fieldX,
                    y,
                    fieldW,
                    authStage == AuthStage.SETUP ? "Recovery phrase" : "Recovery phrase (required)"
            );
            recoveryInput.setMaxLength(64);
            recoveryInput.setTextColor(0xFFFFFFFF);
            y += recoveryStep;
        } else {
            y += compact ? 2 : 4;
        }

        if (authStage == AuthStage.LOGIN) {
            int splitW = (fieldW - 8) / 2;
            addPcButton(fieldX, y, splitW, 24, "Unlock", btn -> submitAuth()).setLabelOffset(6, 1);
            addPcButton(fieldX + splitW + 8, y, splitW, 24, "Forgot Password", btn -> {
                authStage = AuthStage.RECOVER;
                formValues.put("auth.password", "");
                formValues.put("auth.password_repeat", "");
                formValues.put("auth.recovery", "");
                rebuildWidgets();
            }).setLabelOffset(6, 1);
            y += buttonStep;
            if (!compact) {
                addPcButton(fieldX, y, fieldW, 22, "Refresh Security", btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload()))
                        .setLabelOffset(6, 1);
            }
        } else if (authStage == AuthStage.SETUP) {
            addPcButton(fieldX, y, fieldW, 24, "Set Password", btn -> submitAuth()).setLabelOffset(6, 1);
        } else {
            int splitW = (fieldW - 8) / 2;
            addPcButton(fieldX, y, splitW, 24, "Reset Password", btn -> submitAuth()).setLabelOffset(6, 1);
            addPcButton(fieldX + splitW + 8, y, splitW, 24, "Back to Login", btn -> {
                authStage = ClientOwnerPcData.isDesktopPinSet() ? AuthStage.LOGIN : AuthStage.SETUP;
                formValues.put("auth.password", "");
                formValues.put("auth.password_repeat", "");
                rebuildWidgets();
            }).setLabelOffset(6, 1);
        }
    }

    private void submitAuth() {
        if (authStage == AuthStage.LOADING) {
            return;
        }
        String password = formValues.getOrDefault("auth.password", "").trim();
        if (authStage == AuthStage.LOGIN) {
            if (password.length() < 4) {
                ClientOwnerPcData.setToast(false, "Password must be at least 4 characters.");
                return;
            }
            sendDesktopAction("AUTH_VERIFY_PIN", password, "");
            return;
        }

        String repeat = formValues.getOrDefault("auth.password_repeat", "").trim();
        String recoveryPhrase = formValues.getOrDefault("auth.recovery", "").trim();
        if (password.length() < 4) {
            ClientOwnerPcData.setToast(false, "Password must be at least 4 characters.");
            return;
        }
        if (repeat.length() < 4) {
            ClientOwnerPcData.setToast(false, "Repeat password is too short.");
            return;
        }
        if (!password.equals(repeat)) {
            ClientOwnerPcData.setToast(false, "Password values do not match.");
            return;
        }
        if (recoveryPhrase.length() < 4) {
            ClientOwnerPcData.setToast(false, "Recovery phrase must be at least 4 characters.");
            return;
        }

        if (authStage == AuthStage.SETUP) {
            sendDesktopAction("AUTH_SET_PIN", password, recoveryPhrase);
        } else {
            sendDesktopAction("AUTH_RECOVER_RESET", recoveryPhrase, password);
        }
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
            if (ClientOwnerPcData.isAppHidden(bankAppId(app.bankId()))) {
                continue;
            }
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
                    btn -> openOrActivateBankWindow(app.bankId())
            );
            idx++;
        }

        for (UtilityApp utilityApp : DESKTOP_UTILITY_APPS) {
            if (utilityApp != UtilityApp.SYSTEM_MONITOR && ClientOwnerPcData.isAppHidden(utilityAppId(utilityApp))) {
                continue;
            }
            int col = idx % columns;
            int row = idx / columns;
            int x = contentLeft + (col * (buttonWidth + 12));
            int y = contentTop + (row * (buttonHeight + 10));
            addPcButton(
                    x,
                    y,
                    buttonWidth,
                    buttonHeight,
                    fitToWidth(utilityDesktopLabel(utilityApp), buttonWidth - 10),
                    btn -> openUtilityAppWindow(utilityApp)
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
        int toolbarButtonWidth = 82;
        int toolbarGap = 8;
        int refreshX = right - 8 - toolbarButtonWidth;
        int minimizeX = refreshX - toolbarGap - toolbarButtonWidth;
        int closeX = minimizeX - toolbarGap - toolbarButtonWidth;

        addPcButton(
                closeX,
                toolbarY,
                toolbarButtonWidth,
                20,
                "Close App",
                btn -> closeBankAppWindow()
        ).setLabelOffset(4, 1);

        addPcButton(
                minimizeX,
                toolbarY,
                toolbarButtonWidth,
                20,
                "Minimize",
                btn -> {
                    saveActiveBankWindowState();
                    activeWindow = WindowMode.DESKTOP;
                    rebuildWidgets();
                }
        ).setLabelOffset(4, 1);

        addPcButton(
                refreshX,
                toolbarY,
                toolbarButtonWidth,
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
        int frameTop = PAD + TOPBAR_HEIGHT + 20;
        int frameBottom = this.height - PAD - TASKBAR_HEIGHT - 20;

        // Keep clear spacing below ownership labels and keep controls inside the frame.
        int top = frameTop + 34;

        DesktopEditBox name = addFormInput("create.name", left, top, width, "Bank name");

        addPcButton(
                left + width - 92,
                frameTop + 4,
                84,
                20,
                "Close App",
                btn -> closeCreateBankWindow()
        ).setLabelOffset(4, 1);

        int optionY = top + 58;
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

        int actionY = Math.min(top + 122, frameBottom - 34);

        if (width >= 680) {
            addPcButton(
                    left,
                    actionY,
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
                    actionY,
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
                    actionY,
                    170,
                    26,
                    "Refresh Apps",
                    btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())
            );
        } else if (width >= 440) {
            int splitW = (width - 8) / 2;
            addPcButton(
                    left,
                    actionY,
                    splitW,
                    26,
                    "Create Bank",
                    btn -> PacketDistributor.sendToServer(new OwnerPcCreateBankPayload(
                            textOrBlank(name),
                            selectedOwnershipModel
                    ))
            );
            addPcButton(
                    left + splitW + 8,
                    actionY,
                    splitW,
                    26,
                    "Back",
                    btn -> {
                        activeWindow = WindowMode.DESKTOP;
                        rebuildWidgets();
                    }
            );
            addPcButton(
                    left,
                    actionY + 32,
                    width,
                    26,
                    "Refresh Apps",
                    btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())
            );
        } else {
            addPcButton(
                    left,
                    actionY,
                    width,
                    26,
                    "Create Bank",
                    btn -> PacketDistributor.sendToServer(new OwnerPcCreateBankPayload(
                            textOrBlank(name),
                            selectedOwnershipModel
                    ))
            );
            addPcButton(
                    left,
                    actionY + 32,
                    width,
                    26,
                    "Back",
                    btn -> {
                        activeWindow = WindowMode.DESKTOP;
                        rebuildWidgets();
                    }
            );
            addPcButton(
                    left,
                    actionY + 64,
                    width,
                    26,
                    "Refresh Apps",
                    btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())
            );
        }

        createWindowOpen = true;
    }

    private void initUtilityWindowWidgets() {
        if (activeUtilityApp == null) {
            activeUtilityApp = UtilityApp.CALCULATOR;
        }

        int left = PAD + 12;
        int top = PAD + TOPBAR_HEIGHT + 10;
        int right = this.width - PAD - 12;
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 8;

        utilityFrameLeft = left;
        utilityFrameTop = top;
        utilityFrameRight = right;
        utilityFrameBottom = bottom;
        utilityContentX = left + 12;
        utilityContentY = top + 38;
        utilityContentW = Math.max(180, right - left - 24);
        utilityContentH = Math.max(120, bottom - utilityContentY - 10);

        int toolbarY = top + 4;
        int toolbarButtonWidth = 90;
        int toolbarGap = 8;
        int minimizeX = right - 8 - toolbarButtonWidth;
        int closeX = minimizeX - toolbarGap - toolbarButtonWidth;

        DesktopButton closeButton = addPcButton(
                closeX,
                toolbarY,
                toolbarButtonWidth,
                20,
                "Close App",
                btn -> closeActiveUtilityApp()
        ).setLabelOffset(4, 1);

        DesktopButton minimizeButton = addPcButton(
                minimizeX,
                toolbarY,
                toolbarButtonWidth,
                20,
                "Minimize",
                btn -> {
                    activeWindow = WindowMode.DESKTOP;
                    rebuildWidgets();
                }
        ).setLabelOffset(4, 1);

        boolean modalBlocking = unsavedClosePromptOpen || notepadSaveModalOpen || paintSaveModalOpen;
        closeButton.active = !modalBlocking;
        minimizeButton.active = !modalBlocking;

        if (!unsavedClosePromptOpen) {
            switch (activeUtilityApp) {
                case CALCULATOR -> initCalculatorWidgets();
                case NOTEPAD -> initNotepadWidgets();
                case FILE_EXPLORER -> initFileExplorerWidgets();
                case PAINT -> initPaintWidgets();
                case SYSTEM_MONITOR -> initSystemMonitorWidgets();
            }
        }

        if (unsavedClosePromptOpen) {
            initUnsavedClosePromptWidgets();
        }
    }

    private void initCalculatorWidgets() {
        int gap = 6;
        int gridW = Math.min(360, utilityContentW - 16);
        int gridX = utilityContentX + Math.max(0, (utilityContentW - gridW) / 2);
        int gridY = utilityContentY + 46;
        int buttonW = (gridW - (gap * 3)) / 4;
        int buttonH = 22;
        String[][] rows = {
                {"C", "(", ")", "/"},
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"0", ".", "BK", "="}
        };

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length; c++) {
                String token = rows[r][c];
                addPcButton(
                        gridX + (c * (buttonW + gap)),
                        gridY + (r * (buttonH + gap)),
                        buttonW,
                        buttonH,
                        token.equals("BK") ? "Back" : token,
                        btn -> onCalculatorButton(token)
                ).setLabelOffset(4, 1);
            }
        }
    }

    private void initNotepadWidgets() {
        int controlsY = utilityContentY + 4;
        int gap = 6;
        int availableW = Math.max(120, utilityContentW - 8);
        int columns = availableW >= 540 ? 5 : availableW >= 400 ? 3 : 2;
        int btnW = Math.max(70, (availableW - (gap * (columns - 1))) / columns);
        int btnH = 22;

        String[] labels = {"Copy All", "Paste", "Timestamp", "Save", "Clear"};
        if (!notepadSaveModalOpen) {
            for (int i = 0; i < labels.length; i++) {
                int row = i / columns;
                int col = i % columns;
                int x = utilityContentX + 4 + (col * (btnW + gap));
                int y = controlsY + (row * (btnH + gap));
                final int actionIdx = i;
                addPcButton(x, y, btnW, btnH, labels[i], btn -> {
                    switch (actionIdx) {
                        case 0 -> copyNotepadToClipboard();
                        case 1 -> pasteIntoNotepad();
                        case 2 -> appendNotepadTimestamp();
                        case 3 -> onNotepadSavePressed();
                        default -> clearNotepad();
                    }
                }).setLabelOffset(4, 1);
            }
        }

        notepadAreaX = utilityContentX + 4;
        int controlRows = (labels.length + columns - 1) / columns;
        notepadAreaY = controlsY + (controlRows * (btnH + gap));
        notepadAreaW = Math.max(120, utilityContentW - 8);
        notepadAreaH = Math.max(64, (utilityContentY + utilityContentH) - notepadAreaY - 4);

        if (notepadSaveModalOpen) {
            int modalW = Math.min(340, Math.max(180, utilityContentW - 40));
            int modalH = 108;
            int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
            int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);
            int fieldW = modalW - 20;
            int fieldX = modalX + 10;
            int fieldY = modalY + 44;
            DesktopEditBox saveInput = addFormInput("notepad.saveas", fieldX, fieldY, fieldW, "File name");
            saveInput.setFocused(true);
            this.setFocused(saveInput);

            int btnY = modalY + modalH - 30;
            int actionW = (modalW - 30) / 2;
            addPcButton(modalX + 10, btnY, actionW, 20, "Save File", btn -> confirmNotepadSaveAs()).setLabelOffset(4, 1);
            addPcButton(modalX + 20 + actionW, btnY, actionW, 20, "Cancel", btn -> {
                notepadSaveModalOpen = false;
                if (pendingCloseAfterSaveTarget == UtilityApp.NOTEPAD) {
                    pendingCloseAfterSaveTarget = null;
                }
                rebuildWidgets();
            }).setLabelOffset(4, 1);
        }
    }

    private void initFileExplorerWidgets() {
        int panelX = utilityContentX + 4;
        int panelY = utilityContentY + 4;
        int panelW = Math.max(140, utilityContentW - 8);
        int gap = 6;

        DesktopEditBox fileNameInput = addFormInput(
                "explorer.filename",
                panelX,
                panelY,
                panelW,
                "File name"
        );

        int row1Y = panelY + 26;
        int btnW = Math.max(24, (panelW - (gap * 2)) / 3);
        addPcButton(panelX, row1Y, btnW, 22, "Save File", btn -> saveExplorerFile(fileNameInput)).setLabelOffset(4, 1);
        addPcButton(panelX + btnW + gap, row1Y, btnW, 22, "Delete File", btn -> deleteExplorerFile()).setLabelOffset(4, 1);
        addPcButton(panelX + ((btnW + gap) * 2), row1Y, btnW, 22, "Refresh", btn -> sendDesktopAction("REFRESH", "", "")).setLabelOffset(4, 1);

        explorerFileListX = panelX;
        explorerFileListY = row1Y + 44;
        explorerFileListW = panelW;
        explorerFileListH = Math.max(64, (utilityContentY + utilityContentH) - explorerFileListY - 4);

        List<OwnerPcFileEntry> files = ClientOwnerPcData.getDesktopFiles();
        int rowH = 22;
        int rowGap = 4;
        int visibleRows = Math.max(1, explorerFileListH / (rowH + rowGap));
        explorerFilesMaxScroll = Math.max(0, files.size() - visibleRows);
        explorerFilesScroll = Math.max(0, Math.min(explorerFilesScroll, explorerFilesMaxScroll));

        int rowY = explorerFileListY + 2;
        for (int i = 0; i < visibleRows; i++) {
            int index = explorerFilesScroll + i;
            if (index >= files.size()) {
                break;
            }
            OwnerPcFileEntry file = files.get(index);
            String fileName = file.name() == null ? "" : file.name();
            int approxBytes = utf8Bytes(fileName) + utf8Bytes(file.content());
            int cardX = explorerFileListX + 4;
            int cardW = Math.max(120, explorerFileListW - 8);
            int cardInnerGap = 4;
            int openBtnW = Math.min(108, Math.max(86, cardW / 4));
            int openBtnH = rowH - 2;
            int openBtnX = cardX + cardW - cardInnerGap - openBtnW;
            int openBtnY = rowY + 1;
            int selectW = Math.max(80, openBtnX - cardX - cardInnerGap);
            boolean selected = selectedExplorerFileName != null && selectedExplorerFileName.equalsIgnoreCase(fileName);

            DesktopButton row = addPcButton(
                    cardX,
                    rowY,
                    selectW,
                    rowH,
                    fitToWidth(explorerCardLabel(file, approxBytes, selected), Math.max(80, selectW - 10)),
                    btn -> {
                        selectedExplorerFileName = fileName;
                        formValues.put("explorer.filename", fileName);
                        rebuildWidgets();
                    }
            );
            row.active = true;
            row.setLabelOffset(4, 1);

            DesktopButton open = addPcButton(
                    openBtnX,
                    openBtnY,
                    openBtnW,
                    openBtnH,
                    fitToWidth(explorerOpenLabel(file), Math.max(56, openBtnW - 10)),
                    btn -> {
                        selectedExplorerFileName = fileName;
                        formValues.put("explorer.filename", fileName);
                        openExplorerFile(file);
                    }
            );
            open.active = true;
            open.setLabelOffset(4, 1);
            rowY += rowH + rowGap;
        }
    }

    private void initPaintWidgets() {
        int sideW = Math.min(166, Math.max(132, utilityContentW / 4));
        int sideX = utilityContentX + utilityContentW - sideW;
        int y = utilityContentY + 4;
        int labelW = sideW - 8;
        int gap = 4;
        int rowStep = 26;
        int paletteRows = (paintPalette.length + 1) / 2;
        int controlsContentHeight = (rowStep * 5) + 30 + (paletteRows * rowStep);

        paintControlsX = sideX + 4;
        paintControlsY = utilityContentY + 4;
        paintControlsW = labelW;
        paintControlsH = Math.max(40, utilityContentH - 8);
        paintControlsMaxScroll = Math.max(0, controlsContentHeight - paintControlsH);
        paintControlsScroll = Math.max(0, Math.min(paintControlsScroll, paintControlsMaxScroll));

        if (!paintSaveModalOpen) {
            addPaintControlButton(sideX + 4, 0, labelW, 22, "Save Canvas", btn -> onPaintSavePressed()).setLabelOffset(4, 1);
            addPaintControlButton(sideX + 4, rowStep, labelW, 22, "Brush -", btn -> paintBrushSize = Math.max(1, paintBrushSize - 1)).setLabelOffset(4, 1);
            addPaintControlButton(sideX + 4, rowStep * 2, labelW, 22, "Brush +", btn -> paintBrushSize = Math.min(8, paintBrushSize + 1)).setLabelOffset(4, 1);
            addPaintControlButton(sideX + 4, rowStep * 3, labelW, 22, "Eraser", btn -> paintSelectedColor = 0xFFFFFFFF).setLabelOffset(4, 1);
            addPaintControlButton(sideX + 4, rowStep * 4, labelW, 22, "Clear Canvas", btn -> Arrays.fill(paintPixels, 0xFFFFFFFF)).setLabelOffset(4, 1);

            int paletteStartY = (rowStep * 5) + 30;
            for (int i = 0; i < paintPalette.length; i++) {
                int col = i % 2;
                int row = i / 2;
                int swW = (labelW - gap) / 2;
                int swX = sideX + 4 + (col * (swW + gap));
                int swContentY = paletteStartY + (row * rowStep);
                final int color = paintPalette[i];
                addPaintControlButton(swX, swContentY, swW, 22, paintColorLabel(color), color, btn -> paintSelectedColor = color).setLabelOffset(4, 1);
            }
        }

        if (paintSaveModalOpen) {
            int modalW = Math.min(340, Math.max(180, utilityContentW - 40));
            int modalH = 108;
            int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
            int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);
            DesktopEditBox saveInput = addFormInput("paint.saveas", modalX + 10, modalY + 44, modalW - 20, "File name");
            saveInput.setFocused(true);
            this.setFocused(saveInput);
            int btnY = modalY + modalH - 30;
            int actionW = (modalW - 30) / 2;
            addPcButton(modalX + 10, btnY, actionW, 20, "Save Canvas", btn -> confirmPaintSaveAs()).setLabelOffset(4, 1);
            addPcButton(modalX + 20 + actionW, btnY, actionW, 20, "Cancel", btn -> {
                paintSaveModalOpen = false;
                if (pendingCloseAfterSaveTarget == UtilityApp.PAINT) {
                    pendingCloseAfterSaveTarget = null;
                }
                rebuildWidgets();
            }).setLabelOffset(4, 1);
        }
    }

    private void initSystemMonitorWidgets() {
        int topY = utilityContentY + 6;
        if (!systemHideAppsMenuOpen) {
            addPcButton(
                    utilityContentX + 8,
                    topY,
                    160,
                    22,
                    "Copy System Info",
                    btn -> copySystemInfoToClipboard()
            ).setLabelOffset(4, 1);
            addPcButton(
                    utilityContentX + 176,
                    topY,
                    132,
                    22,
                    "Hide Apps",
                    btn -> {
                        systemHideAppsMenuOpen = true;
                        systemHideAppsScroll = 0;
                        rebuildWidgets();
                    }
            ).setLabelOffset(4, 1);
            int viewportX = utilityContentX + 4;
            int viewportY = topY + 28;
            int viewportW = Math.max(120, utilityContentW - 8);
            int viewportH = Math.max(1, utilityContentH - (viewportY - utilityContentY) - 4);
            systemMonitorViewportX = viewportX;
            systemMonitorViewportY = viewportY;
            systemMonitorViewportW = viewportW;
            systemMonitorViewportH = viewportH;

            int metricsCols = viewportW >= 560 ? 2 : 1;
            int metricsRows = (9 + metricsCols - 1) / metricsCols;
            int metricsBlockHeight = (metricsRows * 46) + (Math.max(0, metricsRows - 1) * 8);
            int contentHeight = metricsBlockHeight + 4;
            systemMonitorMaxScroll = Math.max(0, contentHeight - viewportH);
            systemMonitorScroll = Math.max(0, Math.min(systemMonitorScroll, systemMonitorMaxScroll));
            return;
        }

        systemMonitorScroll = 0;
        systemMonitorMaxScroll = 0;
        systemMonitorViewportX = 0;
        systemMonitorViewportY = 0;
        systemMonitorViewportW = 0;
        systemMonitorViewportH = 0;

        addPcButton(
                utilityContentX + 8,
                topY,
                96,
                22,
                "Back",
                btn -> {
                    systemHideAppsMenuOpen = false;
                    systemHideAppsScroll = 0;
                    rebuildWidgets();
                }
        ).setLabelOffset(4, 1);

        int panelX = utilityContentX + 8;
        int panelY = topY + 28;
        int panelW = Math.max(120, utilityContentW - 16);
        int panelH = Math.max(80, utilityContentH - 36);
        systemHideAppsX = panelX;
        systemHideAppsY = panelY;
        systemHideAppsW = panelW;
        systemHideAppsH = panelH;

        List<ExplorerAppEntry> apps = buildExplorerAppEntries();
        int cols = panelW >= 520 ? 2 : 1;
        int cardW = cols == 1 ? panelW - 8 : (panelW - 8 - 8) / 2;
        int cardH = 40;
        int gap = 8;
        int rows = (apps.size() + cols - 1) / cols;
        int visibleRows = Math.max(1, (panelH - 8 + gap) / (cardH + gap));
        systemHideAppsMaxScroll = Math.max(0, rows - visibleRows);
        systemHideAppsScroll = Math.max(0, Math.min(systemHideAppsScroll, systemHideAppsMaxScroll));

        visibleSystemAppCards.clear();
        for (int i = 0; i < apps.size(); i++) {
            ExplorerAppEntry app = apps.get(i);
            int row = i / cols;
            int col = i % cols;
            int renderRow = row - systemHideAppsScroll;
            if (renderRow < 0 || renderRow >= visibleRows) {
                continue;
            }
            int x = panelX + 4 + (col * (cardW + gap));
            int y = panelY + 4 + (renderRow * (cardH + gap));
            int accent = app.hidden() ? 0xFFD95C5C : 0xFF6FD39A;
            DesktopButton button = addPcButton(
                    x,
                    y,
                    cardW,
                    cardH,
                    fitToWidth(app.label(), cardW - 10),
                    accent,
                    btn -> {
                        if (app.lockHide() && !app.hidden()) {
                            ClientOwnerPcData.setToast(false, "This app cannot be hidden.");
                            return;
                        }
                        sendDesktopAction("APP_VISIBILITY", app.appId(), app.hidden() ? "false" : "true");
                    }
            ).setLabelOffset(4, -3);
            button.active = !(app.lockHide() && !app.hidden());
            visibleSystemAppCards.add(new AppVisibilityCard(x, y, cardW, cardH, app));
        }
    }

    private void initUnsavedClosePromptWidgets() {
        int modalW = 330;
        int modalH = 98;
        int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
        int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);
        int buttonW = (modalW - 32) / 3;
        int buttonY = modalY + modalH - 28;

        addPcButton(modalX + 8, buttonY, buttonW, 20, "Save", btn -> {
            UtilityApp target = unsavedCloseTarget;
            unsavedClosePromptOpen = false;
            unsavedCloseTarget = null;
            pendingCloseAfterSaveTarget = target;
            if (target == UtilityApp.NOTEPAD) {
                onNotepadSavePressed();
            } else if (target == UtilityApp.PAINT) {
                onPaintSavePressed();
            } else {
                pendingCloseAfterSaveTarget = null;
            }
        }).setLabelOffset(4, 1);
        addPcButton(modalX + 16 + buttonW, buttonY, buttonW, 20, "Forget", btn -> {
            UtilityApp target = unsavedCloseTarget;
            unsavedClosePromptOpen = false;
            unsavedCloseTarget = null;
            pendingCloseAfterSaveTarget = null;
            if (target != null && target == activeUtilityApp) {
                closeActiveUtilityAppImmediately();
            }
        }).setLabelOffset(4, 1);
        addPcButton(modalX + 24 + (buttonW * 2), buttonY, buttonW, 20, "Cancel", btn -> {
            unsavedClosePromptOpen = false;
            unsavedCloseTarget = null;
            pendingCloseAfterSaveTarget = null;
            rebuildWidgets();
        }).setLabelOffset(4, 1);
    }

    private void initTaskbarWidgets() {
        int barY = this.height - PAD - TASKBAR_HEIGHT + 3;
        int x = PAD + 8;
        int clockWidth = 106;
        int clockX = this.width - PAD - 8 - clockWidth;
        taskbarClockHitbox = new RectHitbox(clockX, barY - 1, clockWidth, 22);
        taskbarMenuHitbox = null;

        if (desktopAuthenticated) {
            addPcButton(
                    x,
                    barY,
                    64,
                    20,
                    "Start",
                    btn -> {
                        if (activeWindow == WindowMode.BANK_APP) {
                            saveActiveBankWindowState();
                        }
                        activeWindow = WindowMode.DESKTOP;
                        rebuildWidgets();
                    }
            );
            x += 72;
        }

        int rightBound = clockX - 8;
        int totalWindowTabs = bankWindowOrder.size() + (createWindowOpen ? 1 : 0) + utilityWindowOrder.size();
        taskbarViewportX = x;
        taskbarViewportY = barY;
        taskbarViewportH = 20;
        taskbarViewportW = 0;
        taskbarMaxScroll = 0;
        if (rightBound <= x || totalWindowTabs <= 0) {
            taskbarScroll = 0;
            initTaskbarPowerPanelButtons(barY);
            return;
        }

        int availableWidth = Math.max(96, rightBound - x);
        int gap = 6;
        int tabWidth = Math.max(122, Math.min(200, availableWidth / Math.max(1, Math.min(totalWindowTabs, 4))));
        int contentWidth = (totalWindowTabs * tabWidth) + (gap * Math.max(0, totalWindowTabs - 1));
        int viewportX = x;
        int viewportW = availableWidth;
        taskbarMaxScroll = Math.max(0, contentWidth - viewportW);
        taskbarScroll = Math.max(0, Math.min(taskbarScroll, taskbarMaxScroll));
        taskbarViewportX = viewportX;
        taskbarViewportY = barY;
        taskbarViewportW = viewportW;

        int tabX = viewportX - taskbarScroll;
        for (UUID bankId : bankWindowOrder) {
            String label = resolveBankWindowTitle(bankId);
            boolean isActiveBankTab = activeWindow == WindowMode.BANK_APP
                    && activeBankId != null
                    && activeBankId.equals(bankId);
            int clippedX = Math.max(tabX, viewportX);
            int clippedRight = Math.min(tabX + tabWidth, viewportX + viewportW);
            int clippedW = clippedRight - clippedX;
            if (clippedW > 6) {
                DesktopButton tab = addPcButton(
                        clippedX,
                        barY,
                        clippedW,
                        20,
                        fitToWidth(label, Math.max(10, clippedW - 10)),
                        btn -> {
                            activateBankWindow(bankId, true);
                            activeWindow = WindowMode.BANK_APP;
                            rebuildWidgets();
                        }
                );
                tab.active = !isActiveBankTab;
            }
            tabX += tabWidth + gap;
        }

        if (createWindowOpen) {
            int clippedX = Math.max(tabX, viewportX);
            int clippedRight = Math.min(tabX + tabWidth, viewportX + viewportW);
            int clippedW = clippedRight - clippedX;
            if (clippedW > 6) {
                DesktopButton createTab = addPcButton(
                        clippedX,
                        barY,
                        clippedW,
                        20,
                        fitToWidth("Create Bank", Math.max(10, clippedW - 10)),
                        btn -> {
                            if (activeWindow == WindowMode.BANK_APP) {
                                saveActiveBankWindowState();
                            }
                            activeWindow = WindowMode.CREATE_BANK;
                            rebuildWidgets();
                        }
                );
                createTab.active = activeWindow != WindowMode.CREATE_BANK;
            }
            tabX += tabWidth + gap;
        }

        for (UtilityApp utilityApp : utilityWindowOrder) {
            boolean activeUtilityTab = activeWindow == WindowMode.UTILITY_APP && activeUtilityApp == utilityApp;
            int clippedX = Math.max(tabX, viewportX);
            int clippedRight = Math.min(tabX + tabWidth, viewportX + viewportW);
            int clippedW = clippedRight - clippedX;
            if (clippedW > 6) {
                DesktopButton utilityTab = addPcButton(
                        clippedX,
                        barY,
                        clippedW,
                        20,
                        fitToWidth(utilityWindowTitle(utilityApp), Math.max(10, clippedW - 10)),
                        btn -> {
                            if (activeWindow == WindowMode.BANK_APP) {
                                saveActiveBankWindowState();
                            }
                            activeUtilityApp = utilityApp;
                            notepadFocused = false;
                            suppressNextNotepadSpaceChar = false;
                            paintDrawing = false;
                            activeWindow = WindowMode.UTILITY_APP;
                            rebuildWidgets();
                        }
                );
                utilityTab.active = !activeUtilityTab;
            }
            tabX += tabWidth + gap;
        }

        initTaskbarPowerPanelButtons(barY);
    }

    private void initTaskbarPowerPanelButtons(int barY) {
        taskbarMenuHitbox = null;
        taskbarLogoutHitbox = null;
        taskbarTurnOffHitbox = null;
        if (!taskbarMenuOpen || taskbarClockHitbox == null) {
            return;
        }
        int panelW = 172;
        int panelH = 74;
        int panelX = taskbarClockHitbox.x() + taskbarClockHitbox.width() - panelW;
        int panelY = barY - panelH - 8;
        taskbarMenuHitbox = new RectHitbox(panelX, panelY, panelW, panelH);
        int buttonX = panelX + 8;
        int buttonW = panelW - 16;
        taskbarLogoutHitbox = new RectHitbox(buttonX, panelY + 8, buttonW, 24);
        taskbarTurnOffHitbox = new RectHitbox(buttonX, panelY + 38, buttonW, 24);
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
                    boolean accountsListView = "SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && !accountProfileView;
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

                    if (accountsListView) {
                        int searchY = Math.max(y + 40, sectionViewportY + sectionViewportH - 22);
                        DesktopEditBox search = addFormInput(
                                "overview.accounts.search",
                                innerX,
                                searchY,
                                innerWidth,
                                "Search player / type / account id..."
                        );
                        search.setResponder(value -> {
                            formValues.put("overview.accounts.search", value == null ? "" : value);
                            outputScroll = 0;
                        });
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
                int currentY = selectorBottom + 4;
                addSectionFormInput("limits.amount", innerX, currentY, innerWidth, "Amount");
                currentY += 32;

                if (innerWidth < 260) {
                    addSectionActionButton(innerX, currentY, innerWidth, "Apply Limit", "SET_LIMIT", "@limits.type", "@limits.amount", "", "", ownerView);
                    currentY += 32;
                    addSectionActionButton(innerX, currentY, innerWidth, "Show Limits", "SHOW_LIMITS", "", "", "", "", true);
                } else {
                    int btnW = (innerWidth - gap) / 2;
                    addSectionActionButton(innerX, currentY, btnW, "Apply Limit", "SET_LIMIT", "@limits.type", "@limits.amount", "", "", ownerView);
                    addSectionActionButton(innerX + btnW + gap, currentY, btnW, "Show Limits", "SHOW_LIMITS", "", "", "", "", true);
                }

                currentY += 36;
                if (data != null) {
                    formValues.putIfAbsent("limits.cardIssueFee", data.cardIssueFee());
                    formValues.putIfAbsent("limits.cardReplacementFee", data.cardReplacementFee());
                }

                if (innerWidth < 390) {
                    addSectionFormInput("limits.cardIssueFee", innerX, currentY, innerWidth, "Card issue fee");
                    currentY += 28;
                    addSectionFormInput("limits.cardReplacementFee", innerX, currentY, innerWidth, "Card replacement fee");
                    currentY += 32;
                    addSectionActionButton(innerX, currentY, innerWidth, "Set Card Fees", "SET_CARD_FEES",
                            "@limits.cardIssueFee", "@limits.cardReplacementFee", "", "", ownerView);
                } else {
                    int halfW = (innerWidth - gap) / 2;
                    addSectionFormInput("limits.cardIssueFee", innerX, currentY, halfW, "Card issue fee");
                    addSectionFormInput("limits.cardReplacementFee", innerX + halfW + gap, currentY, halfW, "Card replacement fee");
                    currentY += 32;
                    addSectionActionButton(innerX, currentY, innerWidth, "Set Card Fees", "SET_CARD_FEES",
                            "@limits.cardIssueFee", "@limits.cardReplacementFee", "", "", ownerView);
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

                    int tellerBtnW = (innerWidth - gap) / 2;
                    addSectionActionButton(innerX, y + 74, tellerBtnW, "Issue Teller Egg", "TELLER_ISSUE", "", "", "", "", ownerView);
                    addSectionActionButton(innerX + tellerBtnW + gap, y + 74, tellerBtnW, "Teller Count", "TELLER_COUNT", "", "", "", "", ownerView);
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
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Issue Teller Egg", "TELLER_ISSUE", "", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Teller Count", "TELLER_COUNT", "", "", "", "", ownerView);
                    } else {
                        int btnW = (innerWidth - gap) / 2;
                        addSectionActionButton(innerX, curY, btnW, "Hire", "HIRE", "@staff.player", "@staff.role", "@staff.salary", "", ownerView);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Fire", "FIRE", "@staff.player", "", "", "", ownerView);
                        curY += 32;
                        addSectionActionButton(innerX, curY, innerWidth, "Employee List", "SHOW_EMPLOYEES", "", "", "", "", true);
                        curY += 32;
                        addSectionActionButton(innerX, curY, btnW, "Issue Teller Egg", "TELLER_ISSUE", "", "", "", "", ownerView);
                        addSectionActionButton(innerX + btnW + gap, curY, btnW, "Teller Count", "TELLER_COUNT", "", "", "", "", ownerView);
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
        if (!List.of("single", "dailyplayer", "dailybank", "teller").contains(selected)) {
            selected = "single";
            formValues.put("limits.type", selected);
        } else {
            formValues.putIfAbsent("limits.type", selected);
        }

        String[] types = {"single", "dailyplayer", "dailybank", "teller"};
        String[] labels = {"Single", "Daily Player", "Daily Bank", "Teller Cash"};

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
            int buttonW = (width - (gap * 3)) / 4;
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
        return addPcButton(x, y, width, height, label, 0xFF69B8FF, onPress);
    }

    private DesktopButton addPcButton(int x,
                                      int y,
                                      int width,
                                      int height,
                                      String label,
                                      int accentColor,
                                      java.util.function.Consumer<DesktopButton> onPress) {
        return addRenderableWidget(new DesktopButton(
                x,
                y,
                width,
                height,
                Component.literal(label),
                accentColor,
                onPress
        ));
    }

    private DesktopButton addPaintControlButton(int x,
                                                int contentY,
                                                int width,
                                                int height,
                                                String label,
                                                java.util.function.Consumer<DesktopButton> onPress) {
        return addPaintControlButton(x, contentY, width, height, label, 0xFF69B8FF, onPress);
    }

    private DesktopButton addPaintControlButton(int x,
                                                int contentY,
                                                int width,
                                                int height,
                                                String label,
                                                int accentColor,
                                                java.util.function.Consumer<DesktopButton> onPress) {
        int renderY = paintControlsY + contentY - paintControlsScroll;
        DesktopButton button = addPcButton(x, renderY, width, height, label, accentColor, onPress);
        boolean visible = renderY >= paintControlsY && (renderY + height) <= (paintControlsY + paintControlsH);
        button.visible = visible;
        button.active = visible;
        return button;
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
        if (!"SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction)) {
            formValues.put("overview.accounts.search", "");
        }
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

    private void openOrActivateBankWindow(UUID bankId) {
        if (bankId == null) {
            return;
        }
        if (!bankWindows.containsKey(bankId)) {
            bankWindows.put(bankId, new BankWindowState(bankId));
            bankWindowOrder.remove(bankId);
            bankWindowOrder.add(bankId);
        }
        activateBankWindow(bankId, true);
        activeWindow = WindowMode.BANK_APP;
        bankWindowOpen = !bankWindowOrder.isEmpty();
        rebuildWidgets();
    }

    private void activateBankWindow(UUID bankId, boolean requestData) {
        if (bankId == null) {
            return;
        }
        if (!bankWindows.containsKey(bankId)) {
            bankWindows.put(bankId, new BankWindowState(bankId));
        }
        if (!bankWindowOrder.contains(bankId)) {
            bankWindowOrder.add(bankId);
        }

        if (activeBankId != null
                && !activeBankId.equals(bankId)
                && (activeWindow == WindowMode.BANK_APP || bankWindows.containsKey(activeBankId))) {
            saveActiveBankWindowState();
            ClientOwnerPcData.clearActionOutput();
        }

        loadBankWindowState(bankId);
        ClientOwnerPcData.setSelectedBankId(bankId);
        if (requestData) {
            requestBankData(bankId);
        }
        bankWindowOpen = !bankWindowOrder.isEmpty();
    }

    private void saveActiveBankWindowState() {
        if (activeBankId == null) {
            return;
        }
        BankWindowState state = bankWindows.computeIfAbsent(activeBankId, BankWindowState::new);
        state.activeSection = activeSection;
        state.outputScroll = outputScroll;
        state.sectionScroll = sectionScroll;
        state.navScroll = navScroll;
        state.overviewDetailOpen = overviewDetailOpen;
        state.overviewDetailAction = overviewDetailAction == null || overviewDetailAction.isBlank() ? "SHOW_INFO" : overviewDetailAction;
        state.selectedAccountCard = selectedAccountCard;
        state.accountProfileOpen = accountProfileOpen;
        state.lendingMarketOpen = lendingMarketOpen;
        state.marketSort = marketSort;
        state.marketSortDescending = marketSortDescending;
        state.marketOfferCache.clear();
        state.marketOfferCache.addAll(marketOfferCache);
        state.pendingMarketAccept = pendingMarketAccept;
        state.refreshMarketAfterNextResponse = refreshMarketAfterNextResponse;
        state.formValues.clear();
        state.formValues.putAll(formValues);
    }

    private void loadBankWindowState(UUID bankId) {
        BankWindowState state = bankWindows.computeIfAbsent(bankId, BankWindowState::new);
        activeBankId = state.bankId;
        activeSection = state.activeSection == null ? Section.OVERVIEW : state.activeSection;
        outputScroll = Math.max(0, state.outputScroll);
        sectionScroll = Math.max(0, state.sectionScroll);
        navScroll = Math.max(0, state.navScroll);
        overviewDetailOpen = state.overviewDetailOpen;
        overviewDetailAction = state.overviewDetailAction == null || state.overviewDetailAction.isBlank()
                ? "SHOW_INFO"
                : state.overviewDetailAction;
        selectedAccountCard = state.selectedAccountCard;
        accountProfileOpen = state.accountProfileOpen;
        lendingMarketOpen = state.lendingMarketOpen;
        marketSort = state.marketSort == null ? MarketSort.AMOUNT : state.marketSort;
        marketSortDescending = state.marketSortDescending;
        marketOfferCache.clear();
        marketOfferCache.addAll(state.marketOfferCache);
        pendingMarketAccept = state.pendingMarketAccept;
        refreshMarketAfterNextResponse = state.refreshMarketAfterNextResponse;
        formValues.clear();
        formValues.putAll(state.formValues);
    }

    private String resolveBankWindowTitle(UUID bankId) {
        if (bankId == null) {
            return "Bank";
        }
        for (OwnerPcBankAppSummary app : ClientOwnerPcData.getApps()) {
            if (bankId.equals(app.bankId())) {
                return app.bankName();
            }
        }
        OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
        if (data != null && bankId.equals(data.bankId()) && data.bankName() != null && !data.bankName().isBlank()) {
            return data.bankName();
        }
        String raw = bankId.toString();
        return "Bank " + raw.substring(0, Math.min(8, raw.length()));
    }

    private String utilityDesktopLabel(UtilityApp app) {
        if (app == null) {
            return "APP | Utility";
        }
        return switch (app) {
            case CALCULATOR -> "APP | Calculator";
            case NOTEPAD -> "APP | Notepad";
            case FILE_EXPLORER -> "APP | File Explorer";
            case PAINT -> "APP | Paint";
            case SYSTEM_MONITOR -> "APP | System Monitor";
        };
    }

    private String utilityWindowTitle(UtilityApp app) {
        if (app == null) {
            return "Utility";
        }
        return switch (app) {
            case CALCULATOR -> "Calculator";
            case NOTEPAD -> "Notepad";
            case FILE_EXPLORER -> "File Explorer";
            case PAINT -> "Paint";
            case SYSTEM_MONITOR -> "System";
        };
    }

    private void openUtilityAppWindow(UtilityApp app) {
        if (app == null) {
            return;
        }
        if (activeWindow == WindowMode.BANK_APP) {
            saveActiveBankWindowState();
        }
        if (!utilityWindowOrder.contains(app)) {
            utilityWindowOrder.add(app);
        }
        activeUtilityApp = app;
        notepadFocused = false;
        suppressNextNotepadSpaceChar = false;
        if (app != UtilityApp.NOTEPAD) {
            notepadSaveModalOpen = false;
        }
        if (app != UtilityApp.PAINT) {
            paintSaveModalOpen = false;
        }
        unsavedClosePromptOpen = false;
        unsavedCloseTarget = null;
        pendingCloseAfterSaveTarget = null;
        paintDrawing = false;
        activeWindow = WindowMode.UTILITY_APP;
        rebuildWidgets();
    }

    private void closeActiveUtilityApp() {
        if (activeUtilityApp == null) {
            return;
        }
        if ((activeUtilityApp == UtilityApp.NOTEPAD || activeUtilityApp == UtilityApp.PAINT)
                && hasUnsavedState(activeUtilityApp)) {
            unsavedClosePromptOpen = true;
            unsavedCloseTarget = activeUtilityApp;
            pendingCloseAfterSaveTarget = null;
            notepadSaveModalOpen = false;
            paintSaveModalOpen = false;
            rebuildWidgets();
            return;
        }
        closeActiveUtilityAppImmediately();
    }

    private void closeActiveUtilityAppImmediately() {
        if (activeUtilityApp == null) {
            return;
        }
        UtilityApp closing = activeUtilityApp;
        resetUtilityState(closing);
        utilityWindowOrder.remove(closing);
        activeUtilityApp = null;
        notepadFocused = false;
        suppressNextNotepadSpaceChar = false;
        notepadSaveModalOpen = false;
        paintSaveModalOpen = false;
        unsavedClosePromptOpen = false;
        unsavedCloseTarget = null;
        pendingCloseAfterSaveTarget = null;
        paintDrawing = false;

        if (!utilityWindowOrder.isEmpty()) {
            activeUtilityApp = utilityWindowOrder.get(Math.max(0, utilityWindowOrder.size() - 1));
            activeWindow = WindowMode.UTILITY_APP;
        } else if (createWindowOpen) {
            activeWindow = WindowMode.CREATE_BANK;
        } else if (!bankWindowOrder.isEmpty()) {
            activateBankWindow(bankWindowOrder.get(Math.max(0, bankWindowOrder.size() - 1)), true);
            activeWindow = WindowMode.BANK_APP;
        } else {
            activeWindow = WindowMode.DESKTOP;
        }
        rebuildWidgets();
    }

    private boolean hasUnsavedState(UtilityApp app) {
        if (app == UtilityApp.NOTEPAD) {
            return !notepadText.toString().equals(notepadSavedSnapshot);
        }
        if (app == UtilityApp.PAINT) {
            return Arrays.hashCode(paintPixels) != paintSavedSnapshotHash;
        }
        return false;
    }

    private void resetUtilityState(UtilityApp app) {
        if (app == null) {
            return;
        }
        switch (app) {
            case CALCULATOR -> {
                calculatorExpression = "";
                calculatorDisplay = "0";
                calculatorStatus = "Ready";
            }
            case NOTEPAD -> {
                notepadText.setLength(0);
                notepadCursorIndex = 0;
                notepadScroll = 0;
                notepadFocused = false;
                notepadSaveModalOpen = false;
                notepadSavedSnapshot = "";
                formValues.remove("notepad.linkedFile");
                formValues.remove("notepad.saveas");
            }
            case FILE_EXPLORER -> {
                selectedExplorerFileName = "";
                explorerFilesScroll = 0;
                formValues.remove("explorer.filename");
            }
            case PAINT -> {
                Arrays.fill(paintPixels, 0xFFFFFFFF);
                paintSelectedColor = 0xFF111111;
                paintBrushSize = 1;
                paintSaveModalOpen = false;
                paintDrawing = false;
                paintSavedSnapshotHash = Arrays.hashCode(paintPixels);
                paintControlsScroll = 0;
                paintControlsMaxScroll = 0;
                paintControlsX = 0;
                paintControlsY = 0;
                paintControlsW = 0;
                paintControlsH = 0;
                formValues.remove("paint.linkedFile");
                formValues.remove("paint.saveas");
            }
            case SYSTEM_MONITOR -> {
                systemHideAppsMenuOpen = false;
                systemHideAppsScroll = 0;
                systemHideAppsMaxScroll = 0;
                systemMonitorScroll = 0;
                systemMonitorMaxScroll = 0;
                systemMonitorViewportX = 0;
                systemMonitorViewportY = 0;
                systemMonitorViewportW = 0;
                systemMonitorViewportH = 0;
            }
        }
    }

    private String paintColorLabel(int color) {
        return switch (color) {
            case 0xFF111111 -> "Black";
            case 0xFF2A5F9E -> "Blue";
            case 0xFF3E8E41 -> "Green";
            case 0xFFC26A2D -> "Orange";
            case 0xFFB23333 -> "Red";
            case 0xFF7B57B8 -> "Purple";
            case 0xFFD4B03D -> "Yellow";
            default -> "White";
        };
    }

    private void insertNotepadText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int free = NOTEPAD_MAX_CHARS - notepadText.length();
        if (free <= 0) {
            ClientOwnerPcData.setToast(false, "Notepad is full.");
            return;
        }
        String toInsert = text;
        if (toInsert.length() > free) {
            toInsert = toInsert.substring(0, free);
            ClientOwnerPcData.setToast(false, "Notepad reached max size.");
        }
        int cursor = Math.max(0, Math.min(notepadCursorIndex, notepadText.length()));
        notepadText.insert(cursor, toInsert);
        notepadCursorIndex = Math.min(notepadText.length(), cursor + toInsert.length());
        ensureNotepadCursorVisible();
    }

    private void appendNotepadText(String text) {
        notepadCursorIndex = notepadText.length();
        insertNotepadText(text);
    }

    private void deleteNotepadBackward() {
        int cursor = Math.max(0, Math.min(notepadCursorIndex, notepadText.length()));
        if (cursor <= 0 || notepadText.isEmpty()) {
            return;
        }
        notepadText.deleteCharAt(cursor - 1);
        notepadCursorIndex = cursor - 1;
        ensureNotepadCursorVisible();
    }

    private void deleteNotepadForward() {
        int cursor = Math.max(0, Math.min(notepadCursorIndex, notepadText.length()));
        if (cursor >= notepadText.length() || notepadText.isEmpty()) {
            return;
        }
        notepadText.deleteCharAt(cursor);
        ensureNotepadCursorVisible();
    }

    private void moveNotepadCursor(int delta) {
        notepadCursorIndex = Math.max(0, Math.min(notepadText.length(), notepadCursorIndex + delta));
        ensureNotepadCursorVisible();
    }

    private void setNotepadCursor(int index) {
        notepadCursorIndex = Math.max(0, Math.min(notepadText.length(), index));
        ensureNotepadCursorVisible();
    }

    private void setNotepadTextFromFile(String content, String fileName) {
        notepadText.setLength(0);
        if (content != null && !content.isEmpty()) {
            String clipped = content.length() > NOTEPAD_MAX_CHARS ? content.substring(0, NOTEPAD_MAX_CHARS) : content;
            notepadText.append(clipped);
        }
        notepadCursorIndex = notepadText.length();
        notepadSavedSnapshot = notepadText.toString();
        if (fileName == null || fileName.isBlank()) {
            formValues.remove("notepad.linkedFile");
        } else {
            formValues.put("notepad.linkedFile", fileName);
        }
        notepadScroll = 0;
    }

    private void ensureNotepadCursorVisible() {
        if (notepadAreaW <= 0 || notepadAreaH <= 0) {
            return;
        }
        NotepadLayout layout = buildNotepadLayout(Math.max(1, notepadAreaW - 14));
        int cursor = Math.max(0, Math.min(notepadCursorIndex, notepadText.length()));
        int lineIndex = 0;
        for (int i = 0; i < layout.lines().size(); i++) {
            int start = layout.starts().get(i);
            int end = start + layout.lines().get(i).length();
            if ((cursor >= start && cursor <= end) || (i == layout.lines().size() - 1 && cursor >= start)) {
                lineIndex = i;
                break;
            }
        }
        int visible = Math.max(1, (notepadAreaH - 8) / LINE_HEIGHT);
        if (lineIndex < notepadScroll) {
            notepadScroll = lineIndex;
        } else if (lineIndex >= notepadScroll + visible) {
            notepadScroll = Math.max(0, lineIndex - visible + 1);
        }
    }

    private void clearNotepad() {
        notepadText.setLength(0);
        notepadCursorIndex = 0;
        notepadScroll = 0;
        ClientOwnerPcData.setToast(true, "Notepad cleared.");
    }

    private void copyNotepadToClipboard() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(notepadText.toString());
            ClientOwnerPcData.setToast(true, "Copied notepad text.");
        }
    }

    private void pasteIntoNotepad() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            insertNotepadText(mc.keyboardHandler.getClipboard());
            ClientOwnerPcData.setToast(true, "Pasted clipboard into notepad.");
        }
    }

    private void appendNotepadTimestamp() {
        appendNotepadText((notepadText.isEmpty() ? "" : "\n") + "[" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "] ");
    }

    private void onNotepadSavePressed() {
        String linkedName = formValues.getOrDefault("notepad.linkedFile", "").trim();
        if (!linkedName.isBlank() && desktopFileExists(linkedName)) {
            selectedExplorerFileName = linkedName;
            sendDesktopAction("FILE_SAVE_TEXT", linkedName, notepadText.toString());
            notepadSavedSnapshot = notepadText.toString();
            if (pendingCloseAfterSaveTarget == UtilityApp.NOTEPAD && activeUtilityApp == UtilityApp.NOTEPAD) {
                pendingCloseAfterSaveTarget = null;
                closeActiveUtilityAppImmediately();
            }
            return;
        }

        String selectedName = selectedExplorerFileName == null ? "" : selectedExplorerFileName.trim();
        if (!selectedName.isBlank() && desktopFileExists(selectedName)) {
            formValues.put("notepad.linkedFile", selectedName);
            sendDesktopAction("FILE_SAVE_TEXT", selectedName, notepadText.toString());
            notepadSavedSnapshot = notepadText.toString();
            if (pendingCloseAfterSaveTarget == UtilityApp.NOTEPAD && activeUtilityApp == UtilityApp.NOTEPAD) {
                pendingCloseAfterSaveTarget = null;
                closeActiveUtilityAppImmediately();
            }
            return;
        }

        openNotepadSaveModal();
    }

    private void confirmNotepadSaveAs() {
        String name = formValues.getOrDefault("notepad.saveas", "").trim();
        if (name.isBlank()) {
            ClientOwnerPcData.setToast(false, "Enter a file name.");
            return;
        }
        selectedExplorerFileName = name;
        formValues.put("notepad.linkedFile", name);
        notepadSaveModalOpen = false;
        notepadSavedSnapshot = notepadText.toString();
        sendDesktopAction("FILE_SAVE_TEXT", name, notepadText.toString());
        if (pendingCloseAfterSaveTarget == UtilityApp.NOTEPAD && activeUtilityApp == UtilityApp.NOTEPAD) {
            pendingCloseAfterSaveTarget = null;
            closeActiveUtilityAppImmediately();
            return;
        }
        pendingCloseAfterSaveTarget = null;
        rebuildWidgets();
    }

    private void openNotepadSaveModal() {
        String linkedName = formValues.getOrDefault("notepad.linkedFile", "").trim();
        String selectedName = selectedExplorerFileName == null ? "" : selectedExplorerFileName.trim();
        notepadSaveModalOpen = true;
        notepadFocused = false;
        suppressNextNotepadSpaceChar = false;
        String suggested = !selectedName.isBlank() ? selectedName : linkedName;
        if (!suggested.isBlank()) {
            formValues.put("notepad.saveas", suggested);
        }
        rebuildWidgets();
    }

    private boolean desktopFileExists(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (OwnerPcFileEntry file : ClientOwnerPcData.getDesktopFiles()) {
            if (file != null && file.name() != null && file.name().equalsIgnoreCase(name.trim())) {
                return true;
            }
        }
        return false;
    }

    private void saveExplorerFile(DesktopEditBox fileNameInput) {
        OwnerPcFileEntry selected = getSelectedExplorerFile();
        if (selected == null || selected.name() == null || selected.name().isBlank()) {
            ClientOwnerPcData.setToast(false, "Select a file first.");
            return;
        }

        String currentName = selected.name().trim();
        String requestedName = textOrBlank(fileNameInput);
        if (requestedName.isBlank()) {
            requestedName = currentName;
        }

        formValues.put("explorer.filename", requestedName);
        if (currentName.equalsIgnoreCase(requestedName)) {
            selectedExplorerFileName = currentName;
            ClientOwnerPcData.setToast(true, "File name unchanged.");
            return;
        }

        selectedExplorerFileName = requestedName;
        sendDesktopAction("FILE_RENAME", currentName, requestedName);
    }

    private String explorerOpenLabel(OwnerPcFileEntry file) {
        if (file == null) {
            return "Open File";
        }
        boolean canvas = file.kind() != null && file.kind().equalsIgnoreCase("CANVAS");
        return canvas ? "Open Canvas" : "Open Note";
    }

    private String explorerCardLabel(OwnerPcFileEntry file, int approxBytes, boolean selected) {
        if (file == null) {
            return selected ? "Selected | Missing file" : "Missing file";
        }
        boolean canvas = file.kind() != null && file.kind().equalsIgnoreCase("CANVAS");
        String kind = canvas ? "Canvas" : "Note";
        String name = file.name() == null ? "" : file.name();
        String prefix = selected ? "Selected | " : "";
        return prefix + kind + " | " + name + "  (" + approxBytes + " B)";
    }

    private void openExplorerFile(OwnerPcFileEntry file) {
        if (file == null) {
            ClientOwnerPcData.setToast(false, "File is unavailable.");
            return;
        }
        boolean canvas = file.kind() != null && file.kind().equalsIgnoreCase("CANVAS");
        if (canvas) {
            if (!loadPaintCanvasFromString(file.content())) {
                ClientOwnerPcData.setToast(false, "Could not load canvas file.");
                return;
            }
            formValues.put("paint.linkedFile", file.name());
            paintSavedSnapshotHash = Arrays.hashCode(paintPixels);
            paintSaveModalOpen = false;
            openUtilityAppWindow(UtilityApp.PAINT);
            ClientOwnerPcData.setToast(true, "Opened canvas " + file.name() + ".");
            return;
        }

        notepadSaveModalOpen = false;
        setNotepadTextFromFile(file.content(), file.name());
        notepadFocused = true;
        openUtilityAppWindow(UtilityApp.NOTEPAD);
        ClientOwnerPcData.setToast(true, "Opened note " + file.name() + ".");
    }

    private void onPaintSavePressed() {
        String linked = formValues.getOrDefault("paint.linkedFile", "").trim();
        if (!linked.isBlank() && desktopFileExists(linked)) {
            sendDesktopAction("FILE_SAVE_CANVAS", linked, serializePaintCanvas());
            paintSavedSnapshotHash = Arrays.hashCode(paintPixels);
            if (pendingCloseAfterSaveTarget == UtilityApp.PAINT && activeUtilityApp == UtilityApp.PAINT) {
                pendingCloseAfterSaveTarget = null;
                closeActiveUtilityAppImmediately();
            }
            return;
        }
        openPaintSaveModal();
    }

    private void confirmPaintSaveAs() {
        String name = formValues.getOrDefault("paint.saveas", "").trim();
        if (name.isBlank()) {
            ClientOwnerPcData.setToast(false, "Enter a file name.");
            return;
        }
        formValues.put("paint.linkedFile", name);
        paintSaveModalOpen = false;
        sendDesktopAction("FILE_SAVE_CANVAS", name, serializePaintCanvas());
        paintSavedSnapshotHash = Arrays.hashCode(paintPixels);
        if (pendingCloseAfterSaveTarget == UtilityApp.PAINT && activeUtilityApp == UtilityApp.PAINT) {
            pendingCloseAfterSaveTarget = null;
            closeActiveUtilityAppImmediately();
            return;
        }
        pendingCloseAfterSaveTarget = null;
        rebuildWidgets();
    }

    private void openPaintSaveModal() {
        String linked = formValues.getOrDefault("paint.linkedFile", "").trim();
        paintSaveModalOpen = true;
        unsavedClosePromptOpen = false;
        if (!linked.isBlank()) {
            formValues.put("paint.saveas", linked);
        }
        rebuildWidgets();
    }

    private String serializePaintCanvas() {
        StringBuilder out = new StringBuilder(paintPixels.length * 9 + 16);
        out.append("CANVAS:").append(paintCanvasW).append("x").append(paintCanvasH).append("|");
        for (int i = 0; i < paintPixels.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(Integer.toHexString(paintPixels[i]));
        }
        return out.toString();
    }

    private boolean loadPaintCanvasFromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String payload = raw;
        int sep = raw.indexOf('|');
        if (sep >= 0) {
            payload = raw.substring(sep + 1);
        }
        String[] parts = payload.split(",");
        if (parts.length != paintPixels.length) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            try {
                paintPixels[i] = (int) Long.parseLong(parts[i], 16);
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private void deleteExplorerFile() {
        String name = selectedExplorerFileName == null ? "" : selectedExplorerFileName.trim();
        if (name.isBlank()) {
            name = formValues.getOrDefault("explorer.filename", "").trim();
        }
        if (name.isBlank()) {
            ClientOwnerPcData.setToast(false, "Select a file to delete.");
            return;
        }
        sendDesktopAction("FILE_DELETE", name, "");
        if (selectedExplorerFileName != null && selectedExplorerFileName.equalsIgnoreCase(name)) {
            selectedExplorerFileName = "";
        }
    }

    private void sendDesktopAction(String action, String arg1, String arg2) {
        PacketDistributor.sendToServer(new OwnerPcDesktopActionPayload(
                action == null ? "" : action,
                arg1 == null ? "" : arg1,
                arg2 == null ? "" : arg2
        ));
    }

    private OwnerPcFileEntry getSelectedExplorerFile() {
        List<OwnerPcFileEntry> files = ClientOwnerPcData.getDesktopFiles();
        if (files.isEmpty()) {
            return null;
        }
        String selectedName = selectedExplorerFileName == null ? "" : selectedExplorerFileName.trim();
        if (selectedName.isBlank()) {
            selectedName = formValues.getOrDefault("explorer.filename", "").trim();
        }
        if (selectedName.isBlank()) {
            return null;
        }
        for (OwnerPcFileEntry entry : files) {
            if (entry != null && entry.name() != null && entry.name().equalsIgnoreCase(selectedName)) {
                return entry;
            }
        }
        return null;
    }

    private List<ExplorerAppEntry> buildExplorerAppEntries() {
        List<ExplorerAppEntry> entries = new ArrayList<>();
        for (UtilityApp app : DESKTOP_UTILITY_APPS) {
            String id = utilityAppId(app);
            entries.add(new ExplorerAppEntry(
                    id,
                    "Utility: " + utilityWindowTitle(app),
                    ClientOwnerPcData.isAppHidden(id),
                    app == UtilityApp.SYSTEM_MONITOR
            ));
        }
        for (OwnerPcBankAppSummary app : ClientOwnerPcData.getApps()) {
            if (app == null || app.bankId() == null) {
                continue;
            }
            String id = bankAppId(app.bankId());
            String label = "Bank: " + (app.bankName() == null ? "Unknown" : app.bankName());
            if (!app.owner() && app.roleLabel() != null && !app.roleLabel().isBlank()) {
                label = label + " [" + app.roleLabel() + "]";
            }
            entries.add(new ExplorerAppEntry(
                    id,
                    label,
                    ClientOwnerPcData.isAppHidden(id),
                    false
            ));
        }
        return entries;
    }

    private String utilityAppId(UtilityApp app) {
        if (app == null) {
            return "utility:unknown";
        }
        return "utility:" + app.name().toLowerCase(Locale.ROOT);
    }

    private String bankAppId(UUID bankId) {
        if (bankId == null) {
            return "bank:unknown";
        }
        return "bank:" + bankId.toString().toLowerCase(Locale.ROOT);
    }

    private void copySystemInfoToClipboard() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int guiScale = 0;
        if (mc != null && mc.options != null && mc.options.guiScale() != null && mc.options.guiScale().get() != null) {
            guiScale = mc.options.guiScale().get();
        }
        String info = "UBS Desktop System Info\n"
                + "Resolution: " + this.width + "x" + this.height + "\n"
                + "GUI Scale: " + guiScale + "\n"
                + "PC UI Scale: Native\n"
                + "Virtual Scale Active: " + useVirtualScale + "\n"
                + "Open Bank Windows: " + bankWindowOrder.size() + "\n"
                + "Open Utility Windows: " + utilityWindowOrder.size() + "\n"
                + "Timestamp: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(info);
            ClientOwnerPcData.setToast(true, "System info copied.");
        }
    }

    private void closeBankAppWindow() {
        if (activeBankId == null) {
            return;
        }

        UUID closingBankId = activeBankId;
        bankWindows.remove(closingBankId);
        bankWindowOrder.remove(closingBankId);
        activeBankId = null;
        bankWindowOpen = !bankWindowOrder.isEmpty();

        if (!bankWindowOrder.isEmpty()) {
            UUID nextBankId = bankWindowOrder.get(Math.max(0, bankWindowOrder.size() - 1));
            activateBankWindow(nextBankId, true);
            activeWindow = WindowMode.BANK_APP;
        } else {
            activeBankId = null;
            activeSection = Section.OVERVIEW;
            outputScroll = 0;
            sectionScroll = 0;
            navScroll = 0;
            overviewDetailOpen = false;
            overviewDetailAction = "SHOW_INFO";
            selectedAccountCard = null;
            accountProfileOpen = false;
            lendingMarketOpen = false;
            pendingMarketAccept = null;
            marketOfferCache.clear();
            refreshMarketAfterNextResponse = false;
            formValues.clear();
            ClientOwnerPcData.clearActionOutput();
            if (activeWindow == WindowMode.BANK_APP) {
                if (createWindowOpen) {
                    activeWindow = WindowMode.CREATE_BANK;
                } else if (!utilityWindowOrder.isEmpty()) {
                    activeUtilityApp = utilityWindowOrder.get(Math.max(0, utilityWindowOrder.size() - 1));
                    activeWindow = WindowMode.UTILITY_APP;
                } else {
                    activeWindow = WindowMode.DESKTOP;
                }
            }
        }
        rebuildWidgets();
    }

    private void closeCreateBankWindow() {
        createWindowOpen = false;
        if (activeWindow == WindowMode.CREATE_BANK) {
            if (!bankWindowOrder.isEmpty()) {
                UUID target = activeBankId != null ? activeBankId : bankWindowOrder.get(Math.max(0, bankWindowOrder.size() - 1));
                activateBankWindow(target, true);
                activeWindow = WindowMode.BANK_APP;
            } else if (!utilityWindowOrder.isEmpty()) {
                activeUtilityApp = utilityWindowOrder.get(Math.max(0, utilityWindowOrder.size() - 1));
                activeWindow = WindowMode.UTILITY_APP;
            } else {
                activeWindow = WindowMode.DESKTOP;
            }
        }
        rebuildWidgets();
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

    public void handleDesktopActionResponse(OwnerPcDesktopActionResponsePayload payload) {
        if (payload == null) {
            return;
        }
        String action = payload.action() == null ? "" : payload.action().trim().toUpperCase(Locale.ROOT);
        boolean authAction = action.startsWith("AUTH_");
        boolean powerAction = "POWER_OFF".equals(action);
        if (!authAction && !powerAction) {
            return;
        }

        if (payload.success()) {
            switch (action) {
                case "AUTH_SET_PIN", "AUTH_VERIFY_PIN", "AUTH_RECOVER_RESET" -> {
                    desktopAuthenticated = true;
                    ClientOwnerPcData.markDesktopSessionUnlocked();
                    authStage = AuthStage.LOGIN;
                    formValues.remove("auth.password");
                    formValues.remove("auth.password_repeat");
                    formValues.remove("auth.recovery");
                }
                case "AUTH_LOGOUT", "POWER_OFF" -> {
                    desktopAuthenticated = false;
                    ClientOwnerPcData.clearDesktopSession();
                    authStage = ClientOwnerPcData.isDesktopPinSet() ? AuthStage.LOGIN : AuthStage.SETUP;
                    formValues.put("auth.password", "");
                    formValues.put("auth.password_repeat", "");
                    formValues.put("auth.recovery", "");
                    activeWindow = WindowMode.DESKTOP;
                }
                default -> {
                }
            }
            return;
        }

        if ("AUTH_SET_PIN".equals(action) && payload.message() != null
                && payload.message().toLowerCase(Locale.ROOT).contains("already exists")) {
            authStage = AuthStage.LOGIN;
        }
        if ("AUTH_VERIFY_PIN".equals(action)) {
            formValues.put("auth.password", "");
        }
        if ("AUTH_RECOVER_RESET".equals(action)) {
            formValues.put("auth.password", "");
            formValues.put("auth.password_repeat", "");
        }
    }

    public void refreshFromNetwork() {
        syncAuthStateFromDesktopData();
        if (refreshMarketAfterNextResponse
                && activeWindow == WindowMode.BANK_APP
                && activeSection == Section.LENDING
                && lendingMarketOpen
                && activeBankId != null) {
            refreshMarketAfterNextResponse = false;
            sendOwnerPcAction("SHOW_MARKET", "", "", "", "");
        }
        if (selectedExplorerFileName != null && !selectedExplorerFileName.isBlank()) {
            boolean exists = false;
            for (OwnerPcFileEntry entry : ClientOwnerPcData.getDesktopFiles()) {
                if (entry != null && entry.name() != null && entry.name().equalsIgnoreCase(selectedExplorerFileName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                selectedExplorerFileName = "";
                formValues.remove("explorer.filename");
            }
        }
        rebuildWidgets();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (taskbarMenuOpen && keyCode == 256) {
            taskbarMenuOpen = false;
            rebuildWidgets();
            return true;
        }
        if (!desktopAuthenticated) {
            if (keyCode == 256) {
                this.onClose();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                submitAuth();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (activeWindow == WindowMode.UTILITY_APP && unsavedClosePromptOpen) {
            if (keyCode == 256) {
                unsavedClosePromptOpen = false;
                unsavedCloseTarget = null;
                rebuildWidgets();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        if (activeWindow == WindowMode.UTILITY_APP
                && activeUtilityApp == UtilityApp.NOTEPAD
                && notepadSaveModalOpen) {
            if (keyCode == 256) {
                notepadSaveModalOpen = false;
                if (pendingCloseAfterSaveTarget == UtilityApp.NOTEPAD) {
                    pendingCloseAfterSaveTarget = null;
                }
                rebuildWidgets();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmNotepadSaveAs();
                return true;
            }
        }

        if (activeWindow == WindowMode.UTILITY_APP
                && activeUtilityApp == UtilityApp.PAINT
                && paintSaveModalOpen) {
            if (keyCode == 256) {
                paintSaveModalOpen = false;
                if (pendingCloseAfterSaveTarget == UtilityApp.PAINT) {
                    pendingCloseAfterSaveTarget = null;
                }
                rebuildWidgets();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                confirmPaintSaveAs();
                return true;
            }
        }

        if (keyCode == 256) {
            if (activeWindow == WindowMode.DESKTOP) {
                this.onClose();
            } else {
                if (activeWindow == WindowMode.BANK_APP) {
                    saveActiveBankWindowState();
                }
                activeWindow = WindowMode.DESKTOP;
                rebuildWidgets();
            }
            return true;
        }

        if (activeWindow == WindowMode.UTILITY_APP) {
            if (activeUtilityApp == UtilityApp.NOTEPAD && notepadFocused && !notepadSaveModalOpen) {
                boolean controlDown = hasControlDown();
                if (controlDown && keyCode == 67) {
                    copyNotepadToClipboard();
                    return true;
                }
                if (controlDown && keyCode == 86) {
                    pasteIntoNotepad();
                    return true;
                }
                if (keyCode == 32) {
                    // Prevent focused desktop buttons from consuming SPACE while typing in notepad.
                    insertNotepadText(" ");
                    suppressNextNotepadSpaceChar = true;
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) {
                    insertNotepadText("\n");
                    return true;
                }
                if (keyCode == 259) {
                    deleteNotepadBackward();
                    return true;
                }
                if (keyCode == 261) {
                    deleteNotepadForward();
                    return true;
                }
                if (keyCode == 263) {
                    moveNotepadCursor(-1);
                    return true;
                }
                if (keyCode == 262) {
                    moveNotepadCursor(1);
                    return true;
                }
                if (keyCode == 268) {
                    setNotepadCursor(0);
                    return true;
                }
                if (keyCode == 269) {
                    setNotepadCursor(notepadText.length());
                    return true;
                }
                if (keyCode == 266) {
                    notepadScroll = Math.max(0, notepadScroll - 4);
                    return true;
                }
                if (keyCode == 267) {
                    notepadScroll = Math.max(0, notepadScroll + 4);
                    return true;
                }
            } else if (activeUtilityApp == UtilityApp.CALCULATOR) {
                if (keyCode == 259) {
                    onCalculatorButton("BK");
                    return true;
                }
                if (keyCode == 261) {
                    onCalculatorButton("C");
                    return true;
                }
                if (keyCode == 257 || keyCode == 335) {
                    onCalculatorButton("=");
                    return true;
                }
            } else if (activeUtilityApp == UtilityApp.PAINT) {
                if (keyCode == 67) {
                    Arrays.fill(paintPixels, 0xFFFFFFFF);
                    return true;
                }
                if (keyCode == 91) {
                    paintBrushSize = Math.max(1, paintBrushSize - 1);
                    return true;
                }
                if (keyCode == 93) {
                    paintBrushSize = Math.min(8, paintBrushSize + 1);
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!desktopAuthenticated) {
            return super.charTyped(codePoint, modifiers);
        }
        if (activeWindow == WindowMode.UTILITY_APP) {
            if (activeUtilityApp == UtilityApp.NOTEPAD && notepadFocused && !notepadSaveModalOpen) {
                if (codePoint == ' ' && suppressNextNotepadSpaceChar) {
                    suppressNextNotepadSpaceChar = false;
                    return true;
                }
                suppressNextNotepadSpaceChar = false;
                if (!Character.isISOControl(codePoint) && codePoint != 127) {
                    insertNotepadText(String.valueOf(codePoint));
                    return true;
                }
            } else if (activeUtilityApp == UtilityApp.CALCULATOR) {
                if (codePoint == '=') {
                    onCalculatorButton("=");
                    return true;
                }
                if ("0123456789.+-*/()".indexOf(codePoint) >= 0) {
                    onCalculatorButton(String.valueOf(codePoint));
                    return true;
                }
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (desktopAuthenticated
                && taskbarMaxScroll > 0
                && localMouseX >= taskbarViewportX && localMouseX <= (taskbarViewportX + taskbarViewportW)
                && localMouseY >= taskbarViewportY && localMouseY <= (taskbarViewportY + taskbarViewportH)) {
            int previous = taskbarScroll;
            int step = 56;
            if (scrollY < 0) {
                taskbarScroll = Math.min(taskbarMaxScroll, taskbarScroll + step);
            } else if (scrollY > 0) {
                taskbarScroll = Math.max(0, taskbarScroll - step);
            }
            if (previous != taskbarScroll) {
                rebuildWidgets();
            }
            return true;
        }
        if (activeWindow == WindowMode.UTILITY_APP) {
            if (activeUtilityApp == UtilityApp.NOTEPAD
                    && localMouseX >= notepadAreaX && localMouseX <= (notepadAreaX + notepadAreaW)
                    && localMouseY >= notepadAreaY && localMouseY <= (notepadAreaY + notepadAreaH)) {
                List<String> lines = buildNotepadLayout(Math.max(1, notepadAreaW - 14)).lines();
                int visible = Math.max(1, (notepadAreaH - 8) / LINE_HEIGHT);
                int maxScroll = Math.max(0, lines.size() - visible);
                int previous = Math.max(0, Math.min(notepadScroll, maxScroll));
                if (scrollY < 0) {
                    notepadScroll = Math.min(maxScroll, previous + 2);
                } else if (scrollY > 0) {
                    notepadScroll = Math.max(0, previous - 2);
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.FILE_EXPLORER
                    && localMouseX >= explorerFileListX && localMouseX <= (explorerFileListX + explorerFileListW)
                    && localMouseY >= explorerFileListY && localMouseY <= (explorerFileListY + explorerFileListH)
                    && explorerFilesMaxScroll > 0) {
                int previous = explorerFilesScroll;
                if (scrollY < 0) {
                    explorerFilesScroll = Math.min(explorerFilesMaxScroll, explorerFilesScroll + 1);
                } else if (scrollY > 0) {
                    explorerFilesScroll = Math.max(0, explorerFilesScroll - 1);
                }
                if (previous != explorerFilesScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.SYSTEM_MONITOR
                    && systemHideAppsMenuOpen
                    && localMouseX >= systemHideAppsX && localMouseX <= (systemHideAppsX + systemHideAppsW)
                    && localMouseY >= systemHideAppsY && localMouseY <= (systemHideAppsY + systemHideAppsH)
                    && systemHideAppsMaxScroll > 0) {
                int previous = systemHideAppsScroll;
                if (scrollY < 0) {
                    systemHideAppsScroll = Math.min(systemHideAppsMaxScroll, systemHideAppsScroll + 1);
                } else if (scrollY > 0) {
                    systemHideAppsScroll = Math.max(0, systemHideAppsScroll - 1);
                }
                if (previous != systemHideAppsScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.SYSTEM_MONITOR
                    && !systemHideAppsMenuOpen
                    && localMouseX >= systemMonitorViewportX && localMouseX <= (systemMonitorViewportX + systemMonitorViewportW)
                    && localMouseY >= systemMonitorViewportY && localMouseY <= (systemMonitorViewportY + systemMonitorViewportH)
                    && systemMonitorMaxScroll > 0) {
                int previous = systemMonitorScroll;
                int step = 12;
                if (scrollY < 0) {
                    systemMonitorScroll = Math.min(systemMonitorMaxScroll, systemMonitorScroll + step);
                } else if (scrollY > 0) {
                    systemMonitorScroll = Math.max(0, systemMonitorScroll - step);
                }
                if (previous != systemMonitorScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.PAINT
                    && !paintSaveModalOpen
                    && !unsavedClosePromptOpen
                    && localMouseX >= paintControlsX && localMouseX <= (paintControlsX + paintControlsW)
                    && localMouseY >= paintControlsY && localMouseY <= (paintControlsY + paintControlsH)
                    && paintControlsMaxScroll > 0) {
                int previous = paintControlsScroll;
                int step = 12;
                if (scrollY < 0) {
                    paintControlsScroll = Math.min(paintControlsMaxScroll, paintControlsScroll + step);
                } else if (scrollY > 0) {
                    paintControlsScroll = Math.max(0, paintControlsScroll - step);
                }
                if (previous != paintControlsScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.PAINT
                    && !paintSaveModalOpen
                    && !unsavedClosePromptOpen
                    && isInsidePaintCanvas(localMouseX, localMouseY)) {
                if (scrollY < 0) {
                    paintBrushSize = Math.max(1, paintBrushSize - 1);
                } else if (scrollY > 0) {
                    paintBrushSize = Math.min(8, paintBrushSize + 1);
                }
                return true;
            }
        }

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
                int step = 1;
                int bodyWidth = Math.max(1, outputPanelW - (OUTPUT_PANEL_INSET * 2));
                int bodyHeight = Math.max(1, outputPanelH - (OUTPUT_PANEL_INSET * 2));
                OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
                InputHelp help = getFocusedInputHelp();
                if (activeSection == Section.LENDING && lendingMarketOpen) {
                    if (pendingMarketAccept != null) {
                        return true;
                    }
                    int listHeight = Math.max(32, bodyHeight - 30);
                    int cardH = 76;
                    int gap = 10;
                    int cols = bodyWidth >= 620 ? 2 : 1;
                    int rows = (getSortedMarketOffers().size() + cols - 1) / cols;
                    int visibleRows = Math.max(1, (listHeight + gap) / (cardH + gap));
                    maxScroll = Math.max(0, rows - visibleRows);
                } else if (activeSection == Section.OVERVIEW
                        && overviewDetailOpen
                        && isOverviewMetricsAction(overviewDetailAction)
                        && data != null) {
                    int contentHeight = getOverviewDashboardContentHeight(bodyWidth, bodyHeight);
                    maxScroll = Math.max(0, contentHeight - bodyHeight);
                    step = OUTPUT_PIXEL_SCROLL_STEP;
                } else if (activeSection == Section.OVERVIEW
                        && overviewDetailOpen
                        && "SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction)
                        && accountProfileOpen
                        && selectedAccountCard != null) {
                    int contentHeight = getAccountProfileContentHeight(bodyWidth, bodyHeight);
                    maxScroll = Math.max(0, contentHeight - bodyHeight);
                    step = OUTPUT_PIXEL_SCROLL_STEP;
                } else if (activeSection == Section.OVERVIEW
                        && overviewDetailOpen
                        && !isOverviewMetricsAction(overviewDetailAction)
                        && !("SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && accountProfileOpen)
                        && data != null) {
                    int cols = bodyWidth >= 520 ? 2 : 1;
                    int cardH = 46;
                    int gap = 8;
                    int rows = (extractOverviewCardEntries(overviewDetailAction).size() + cols - 1) / cols;
                    int visibleRows = Math.max(1, (bodyHeight + gap) / (cardH + gap));
                    maxScroll = Math.max(0, rows - visibleRows);
                } else if (help != null
                        && (activeSection == Section.LIMITS
                        || (activeSection == Section.LENDING && !lendingMarketOpen))) {
                    int contentHeight = getInputHelpContentHeight(help, bodyWidth, bodyHeight);
                    maxScroll = Math.max(0, contentHeight - bodyHeight);
                    step = OUTPUT_PIXEL_SCROLL_STEP;
                } else {
                    List<String> wrapped = getWrappedOutputLines();
                    int visible = Math.max(1, (outputPanelH - 10) / LINE_HEIGHT);
                    maxScroll = Math.max(0, wrapped.size() - visible);
                }
                if (maxScroll > 0) {
                    if (scrollY < 0) {
                        outputScroll = Math.min(maxScroll, outputScroll + step);
                    } else if (scrollY > 0) {
                        outputScroll = Math.max(0, outputScroll - step);
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
        if (button == 0 && taskbarClockHitbox != null && taskbarClockHitbox.contains(localMouseX, localMouseY)) {
            taskbarMenuOpen = !taskbarMenuOpen;
            rebuildWidgets();
            return true;
        }
        if (button == 0 && taskbarMenuOpen) {
            if (taskbarLogoutHitbox != null && taskbarLogoutHitbox.contains(localMouseX, localMouseY)) {
                sendDesktopAction("AUTH_LOGOUT", "", "");
                ClientOwnerPcData.clearDesktopSession();
                desktopAuthenticated = false;
                authInitialized = true;
                authStage = ClientOwnerPcData.isDesktopPinSet() ? AuthStage.LOGIN : AuthStage.SETUP;
                formValues.put("auth.password", "");
                formValues.put("auth.password_repeat", "");
                activeWindow = WindowMode.DESKTOP;
                taskbarMenuOpen = false;
                rebuildWidgets();
                return true;
            }
            if (taskbarTurnOffHitbox != null && taskbarTurnOffHitbox.contains(localMouseX, localMouseY)) {
                taskbarMenuOpen = false;
                sendDesktopAction("POWER_OFF", "", "");
                ClientOwnerPcData.clearDesktopSession();
                discardCachedScreenOnClose = true;
                this.onClose();
                return true;
            }
            if (taskbarMenuHitbox != null && taskbarMenuHitbox.contains(localMouseX, localMouseY)) {
                return true;
            }
            if (taskbarMenuHitbox != null) {
                taskbarMenuOpen = false;
                rebuildWidgets();
                return true;
            }
        }
        if (activeWindow == WindowMode.UTILITY_APP) {
            if (activeUtilityApp == UtilityApp.NOTEPAD) {
                if (notepadSaveModalOpen || unsavedClosePromptOpen) {
                    notepadFocused = false;
                } else {
                    notepadFocused = localMouseX >= notepadAreaX && localMouseX <= (notepadAreaX + notepadAreaW)
                            && localMouseY >= notepadAreaY && localMouseY <= (notepadAreaY + notepadAreaH);
                    if (!notepadFocused) {
                        suppressNextNotepadSpaceChar = false;
                    }
                    if (notepadFocused) {
                        int row = (int) ((localMouseY - (notepadAreaY + 4)) / LINE_HEIGHT);
                        NotepadLayout layout = buildNotepadLayout(Math.max(1, notepadAreaW - 14));
                        int lineIndex = Math.max(0, Math.min(layout.lines().size() - 1, notepadScroll + Math.max(0, row)));
                        String line = layout.lines().get(lineIndex);
                        int start = layout.starts().get(lineIndex);
                        int xOffset = (int) Math.max(0, localMouseX - (notepadAreaX + 6));
                        int col = 0;
                        for (int i = 0; i < line.length(); i++) {
                            int nextWidth = this.font.width(line.substring(0, i + 1));
                            if (xOffset < nextWidth) {
                                int leftWidth = this.font.width(line.substring(0, i));
                                col = (xOffset - leftWidth) > (nextWidth - xOffset) ? i + 1 : i;
                                break;
                            }
                            col = i + 1;
                        }
                        setNotepadCursor(start + col);
                        return true;
                    }
                }
            }
            if (activeUtilityApp == UtilityApp.PAINT && (button == 0 || button == 1)) {
                if (paintSaveModalOpen || unsavedClosePromptOpen) {
                    return super.mouseClicked(localMouseX, localMouseY, button);
                }
                if (isInsidePaintCanvas(localMouseX, localMouseY)) {
                    paintDrawing = true;
                    paintDrawColor = (button == 1) ? 0xFFFFFFFF : paintSelectedColor;
                    paintAt(localMouseX, localMouseY, paintDrawColor);
                    return true;
                }
            }
        }

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
                && accountProfileOpen
                && selectedAccountCard != null
                && accountProfileCopyIdHitbox != null
                && accountProfileCopyIdHitbox.contains(localMouseX, localMouseY)) {
            Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(selectedAccountCard.id());
            }
            ClientOwnerPcData.setToast(true, "Copied full account id to clipboard.");
            return true;
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
        if (activeWindow == WindowMode.UTILITY_APP && activeUtilityApp == UtilityApp.PAINT) {
            paintDrawing = false;
        }
        return super.mouseReleased(toLocalX(mouseX), toLocalY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (desktopAuthenticated
                && taskbarMaxScroll > 0
                && (button == 0 || button == 1)
                && localMouseX >= taskbarViewportX && localMouseX <= (taskbarViewportX + taskbarViewportW)
                && localMouseY >= taskbarViewportY && localMouseY <= (taskbarViewportY + taskbarViewportH)) {
            int previous = taskbarScroll;
            int deltaX = (int) Math.round(toLocalDeltaX(dragX));
            if (deltaX != 0) {
                taskbarScroll = Math.max(0, Math.min(taskbarMaxScroll, taskbarScroll - deltaX));
                if (taskbarScroll != previous) {
                    rebuildWidgets();
                }
            }
            return true;
        }
        if (activeWindow == WindowMode.UTILITY_APP
                && activeUtilityApp == UtilityApp.PAINT
                && paintDrawing
                && !paintSaveModalOpen
                && !unsavedClosePromptOpen
                && (button == 0 || button == 1)) {
            paintAt(localMouseX, localMouseY, paintDrawColor);
            return true;
        }
        return super.mouseDragged(
                localMouseX,
                localMouseY,
                button,
                toLocalDeltaX(dragX),
                toLocalDeltaY(dragY)
        );
    }

    @Override
    public void onClose() {
        restoreForcedGuiScale();
        if (!discardCachedScreenOnClose) {
            taskbarMenuOpen = false;
            paintDrawing = false;
            notepadFocused = false;
            suppressNextNotepadSpaceChar = false;
            ClientOwnerPcData.clearForUiClose();
            OwnerPcScreenHelper.invalidateCachedScreen(this);
            super.onClose();
            return;
        }

        bankWindows.clear();
        bankWindowOrder.clear();
        utilityWindowOrder.clear();
        bankWindowOpen = false;
        createWindowOpen = false;
        activeBankId = null;
        activeUtilityApp = null;
        notepadFocused = false;
        suppressNextNotepadSpaceChar = false;
        notepadSaveModalOpen = false;
        paintSaveModalOpen = false;
        unsavedClosePromptOpen = false;
        unsavedCloseTarget = null;
        pendingCloseAfterSaveTarget = null;
        systemHideAppsMenuOpen = false;
        systemMonitorScroll = 0;
        systemMonitorMaxScroll = 0;
        systemMonitorViewportX = 0;
        systemMonitorViewportY = 0;
        systemMonitorViewportW = 0;
        systemMonitorViewportH = 0;
        systemHideAppsScroll = 0;
        systemHideAppsMaxScroll = 0;
        notepadCursorIndex = 0;
        notepadSavedSnapshot = "";
        paintSavedSnapshotHash = Arrays.hashCode(paintPixels);
        selectedExplorerFileName = "";
        explorerFilesScroll = 0;
        explorerFilesMaxScroll = 0;
        paintControlsScroll = 0;
        paintControlsMaxScroll = 0;
        paintControlsX = 0;
        paintControlsY = 0;
        paintControlsW = 0;
        paintControlsH = 0;
        taskbarScroll = 0;
        taskbarMaxScroll = 0;
        taskbarViewportX = 0;
        taskbarViewportY = 0;
        taskbarViewportW = 0;
        taskbarViewportH = 0;
        taskbarClockHitbox = null;
        taskbarMenuHitbox = null;
        taskbarLogoutHitbox = null;
        taskbarTurnOffHitbox = null;
        accountProfileCopyIdHitbox = null;
        taskbarMenuOpen = false;
        desktopAuthenticated = false;
        authInitialized = false;
        authStage = AuthStage.LOADING;
        paintDrawing = false;
        ClientOwnerPcData.clearForUiClose();
        OwnerPcScreenHelper.invalidateCachedScreen(this);
        discardCachedScreenOnClose = false;
        super.onClose();
    }

    private void applyForcedGuiScale() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null || mc.options == null || mc.options.guiScale() == null) {
            return;
        }
        Integer current = mc.options.guiScale().get();
        if (current == null) {
            return;
        }
        if (!forcedGuiScaleActive) {
            previousGuiScale = current;
            forcedGuiScaleActive = true;
        }
        if (current != 2) {
            mc.options.guiScale().set(2);
        }
    }

    private void restoreForcedGuiScale() {
        if (!forcedGuiScaleActive) {
            return;
        }
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.guiScale() != null && previousGuiScale != null) {
            Integer current = mc.options.guiScale().get();
            if (current == null || !current.equals(previousGuiScale)) {
                mc.options.guiScale().set(previousGuiScale);
            }
        }
        forcedGuiScaleActive = false;
        previousGuiScale = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        applyForcedGuiScale();
        if (mc != null && mc.getWindow() != null) {
            int scaledW = mc.getWindow().getGuiScaledWidth();
            int scaledH = mc.getWindow().getGuiScaledHeight();
            if (scaledW > 0 && scaledH > 0 && (this.width != scaledW || this.height != scaledH)) {
                this.resize(mc, scaledW, scaledH);
            }
        }

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
        int desktopAppCount = ClientOwnerPcData.getApps().size() + DESKTOP_UTILITY_APPS.size();
        graphics.drawString(this.font,
                "Apps: " + desktopAppCount + "   Owned: "
                        + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks(),
                left + 130,
                top + 9,
                0xFF2C4770,
                false);

        if (!desktopAuthenticated) {
            drawAuthLockScreen(graphics, left, top, right, bottom);
        } else if (activeWindow == WindowMode.BANK_APP) {
            drawBankWindowFrame(graphics);
        } else if (activeWindow == WindowMode.CREATE_BANK) {
            drawCreateWindowFrame(graphics);
        } else if (activeWindow == WindowMode.UTILITY_APP) {
            drawUtilityWindowFrame(graphics);
        } else {
            drawDesktopHints(graphics);
        }

        super.render(graphics, localMouseX, localMouseY, partialTicks);
        drawViewportMasks(graphics);
        drawToast(graphics);
        drawTaskbarClockAndPower(graphics, localMouseX, localMouseY);
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

    private void drawTaskbarClockAndPower(GuiGraphics graphics, int mouseX, int mouseY) {
        if (taskbarClockHitbox == null) {
            return;
        }

        int clockX = taskbarClockHitbox.x();
        int clockY = taskbarClockHitbox.y();
        int clockW = taskbarClockHitbox.width();
        int clockH = taskbarClockHitbox.height();

        if (taskbarMenuOpen && taskbarMenuHitbox != null) {
            int panelX = taskbarMenuHitbox.x();
            int panelY = taskbarMenuHitbox.y();
            int panelW = taskbarMenuHitbox.width();
            int panelH = taskbarMenuHitbox.height();
            graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xDD2A3F5E);
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xE0122539);
            graphics.fill(panelX, panelY, panelX + panelW, panelY + 18, 0xCC2A5B91);
            drawTaskbarMenuButton(graphics, taskbarLogoutHitbox, "Log Out PC", mouseX, mouseY);
            drawTaskbarMenuButton(graphics, taskbarTurnOffHitbox, "Turn Off", mouseX, mouseY);
        }

        int border = taskbarMenuOpen ? 0xFF9FCBF0 : 0xFF3C587A;
        graphics.fill(clockX, clockY, clockX + clockW, clockY + clockH, border);
        graphics.fill(clockX + 1, clockY + 1, clockX + clockW - 1, clockY + clockH - 1, 0xCC1A2F48);

        LocalDateTime now = LocalDateTime.now();
        String time = now.format(DateTimeFormatter.ofPattern("HH:mm"));
        String date = now.format(DateTimeFormatter.ofPattern("d-M-yyyy"));
        graphics.drawCenteredString(this.font, time, clockX + (clockW / 2), clockY + 3, 0xFFEAF5FF);
        graphics.drawCenteredString(this.font, date, clockX + (clockW / 2), clockY + 12, 0xFFCFE4FF);
    }

    private void drawTaskbarMenuButton(GuiGraphics graphics, RectHitbox hitbox, String label, int mouseX, int mouseY) {
        if (hitbox == null) {
            return;
        }
        int x = hitbox.x();
        int y = hitbox.y();
        int w = hitbox.width();
        int h = hitbox.height();
        boolean hovered = hitbox.contains(mouseX, mouseY);

        int border = hovered ? 0xFFCDE9FF : 0xFF355A83;
        int top = hovered ? 0xEE2B5A8D : 0xE6264E7A;
        int bottom = hovered ? 0xEE1D4062 : 0xE61A3856;
        graphics.fill(x, y, x + w, y + h, border);

        int innerX1 = x + 1;
        int innerY1 = y + 1;
        int innerX2 = x + w - 1;
        int innerY2 = y + h - 1;
        int innerH = Math.max(1, innerY2 - innerY1);
        for (int i = 0; i < innerH; i++) {
            float t = innerH <= 1 ? 0.0F : (float) i / (float) (innerH - 1);
            graphics.fill(innerX1, innerY1 + i, innerX2, innerY1 + i + 1, lerpColor(top, bottom, t));
        }

        graphics.fill(innerX1 + 1, innerY1 + 1, innerX1 + 4, innerY2 - 1, 0xFF69B8FF);
        int iconX = innerX1 + 8;
        int iconY = innerY1 + Math.max(1, (innerY2 - innerY1 - 8) / 2);
        graphics.fill(iconX, iconY, iconX + 8, iconY + 2, 0xFFEAF5FF);
        graphics.fill(iconX, iconY + 3, iconX + 6, iconY + 5, 0xFFEAF5FF);
        graphics.fill(iconX, iconY + 6, iconX + 4, iconY + 8, 0xFFEAF5FF);

        graphics.drawString(this.font, fitToWidth(label, w - 28), innerX1 + 19, y + Math.max(1, (h - 8) / 2), 0xFFFFFFFF, false);
        if (hovered) {
            graphics.fill(innerX1 + 1, innerY1 + 1, innerX2 - 1, innerY1 + 2, 0x66FFFFFF);
        }
    }

    private void drawAuthLockScreen(GuiGraphics graphics, int left, int top, int right, int bottom) {
        int contentTop = top + TOPBAR_HEIGHT + 6;
        int contentBottom = bottom - TASKBAR_HEIGHT - 6;
        int contentHeight = Math.max(160, contentBottom - contentTop);
        int panelW = Math.min(460, Math.max(300, this.width - 84));
        int neededH = switch (authStage) {
            case LOADING -> 148;
            case LOGIN -> 214;
            case SETUP, RECOVER -> 286;
        };
        int panelH = Math.min(Math.max(neededH, 140), Math.max(140, contentHeight - 8));
        boolean compact = panelH < neededH;
        int panelX = (this.width - panelW) / 2;
        int panelY = contentTop + Math.max(0, (contentHeight - panelH) / 2);
        int centerX = panelX + (panelW / 2);
        int iconY = panelY + (compact ? 10 : 18);
        int avatarSize = compact ? 40 : 52;

        for (int y = contentTop; y < contentBottom; y++) {
            float ratio = (float) (y - contentTop) / (float) Math.max(1, (contentBottom - contentTop - 1));
            int row = lerpColor(0xAA1C4AA0, 0xAA10336F, ratio);
            graphics.fill(left + 4, y, right - 4, y + 1, row);
        }

        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xB21E3A5D);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0x7F102843);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 26, 0xAA2A5F9E);

        graphics.fill(centerX - (avatarSize / 2), iconY, centerX + (avatarSize / 2), iconY + avatarSize, 0xFFE8EEF5);
        int headHalf = Math.max(8, avatarSize / 5);
        int headTop = iconY + Math.max(6, avatarSize / 6);
        graphics.fill(centerX - headHalf, headTop, centerX + headHalf, headTop + (headHalf * 2), 0xFF8C8C8C);
        int shoulderHalf = Math.max(12, avatarSize / 3);
        int shouldersTop = iconY + avatarSize - Math.max(14, avatarSize / 3);
        graphics.fill(centerX - shoulderHalf, shouldersTop, centerX + shoulderHalf, shouldersTop + Math.max(10, avatarSize / 4), 0xFF9D9D9D);

        String computerLabel = ClientOwnerPcData.getDesktopComputerLabel();
        if (computerLabel == null || computerLabel.isBlank()) {
            computerLabel = "UBS Bank Owner PC";
        }
        int labelY = iconY + avatarSize + 8;
        graphics.drawCenteredString(this.font, fitToWidth(computerLabel, panelW - 18), centerX, labelY, 0xFFE8F3FF);

        String title;
        String subtitle;
        if (authStage == AuthStage.LOADING) {
            title = "Loading security profile...";
            subtitle = "Requesting desktop state from server";
        } else if (authStage == AuthStage.SETUP) {
            title = "Set your PC password";
            subtitle = "First use requires a password and recovery phrase";
        } else if (authStage == AuthStage.RECOVER) {
            title = "Forgot password";
            subtitle = "Enter your recovery phrase and create a new password";
        } else {
            title = "Enter your password";
            subtitle = "Sign in to access this computer";
        }

        int titleY = labelY + 16;
        int subtitleY = titleY + 14;

        graphics.drawCenteredString(this.font, title, centerX, titleY, 0xFFFFFFFF);
        graphics.drawCenteredString(this.font, fitToWidth(subtitle, panelW - 20), centerX, subtitleY, 0xFFD0E7FF);
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
        accountProfileCopyIdHitbox = null;
        int bodyX = x + OUTPUT_PANEL_INSET;
        int bodyY = y + OUTPUT_PANEL_INSET;
        int bodyW = Math.max(1, width - (OUTPUT_PANEL_INSET * 2));
        int bodyH = Math.max(1, height - (OUTPUT_PANEL_INSET * 2));

        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && isOverviewMetricsAction(overviewDetailAction)
                && data != null) {
            visibleAccountCards.clear();
            int contentHeight = getOverviewDashboardContentHeight(bodyW, bodyH);
            int maxScroll = Math.max(0, contentHeight - bodyH);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            enableScaledScissor(graphics, bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            drawOverviewDashboard(graphics, data, overviewDetailAction, bodyX, bodyY - outputScroll, bodyW, contentHeight);
            graphics.disableScissor();
            drawOutputScrollbar(graphics, x, y, width, height, outputScroll, maxScroll);
            return;
        }

        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && data != null) {
            enableScaledScissor(graphics, bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            if ("SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && accountProfileOpen && selectedAccountCard != null) {
                visibleAccountCards.clear();
                int contentHeight = getAccountProfileContentHeight(bodyW, bodyH);
                int maxScroll = Math.max(0, contentHeight - bodyH);
                outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
                drawAccountProfilePanel(graphics, selectedAccountCard, bodyX, bodyY - outputScroll, bodyW, contentHeight);
                graphics.disableScissor();
                drawOutputScrollbar(graphics, x, y, width, height, outputScroll, maxScroll);
                return;
            }
            drawOverviewListCards(graphics, overviewDetailAction, bodyX, bodyY, bodyW, bodyH);
            graphics.disableScissor();
            return;
        }

        visibleAccountCards.clear();

        if (activeSection == Section.LENDING && lendingMarketOpen) {
            drawLendingMarketPanel(graphics, bodyX, bodyY, bodyW, bodyH);
            return;
        }

        InputHelp help = getFocusedInputHelp();
        if (help != null && activeSection == Section.LIMITS) {
            int contentHeight = getInputHelpContentHeight(help, bodyW, bodyH);
            int maxScroll = Math.max(0, contentHeight - bodyH);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            enableScaledScissor(graphics, bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            drawInputHelpPanel(graphics, help, bodyX, bodyY - outputScroll, bodyW, contentHeight);
            graphics.disableScissor();
            drawOutputScrollbar(graphics, x, y, width, height, outputScroll, maxScroll);
            return;
        }
        if (help != null && activeSection == Section.LENDING && !lendingMarketOpen) {
            int contentHeight = getInputHelpContentHeight(help, bodyW, bodyH);
            int maxScroll = Math.max(0, contentHeight - bodyH);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            enableScaledScissor(graphics, bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
            drawInputHelpPanel(graphics, help, bodyX, bodyY - outputScroll, bodyW, contentHeight);
            graphics.disableScissor();
            drawOutputScrollbar(graphics, x, y, width, height, outputScroll, maxScroll);
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

        int available = Math.max(1, bodyH / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - available);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        enableScaledScissor(graphics, bodyX, bodyY, bodyX + bodyW, bodyY + bodyH);
        int lineY = bodyY;
        for (int i = 0; i < available; i++) {
            int idx = outputScroll + i;
            if (idx >= lines.size()) {
                break;
            }
            graphics.drawString(this.font, lines.get(idx), bodyX, lineY, 0xFFE7F3FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.disableScissor();

        drawOutputScrollbar(graphics, x, y, width, height, outputScroll, maxScroll);
    }

    private void drawOutputScrollbar(GuiGraphics graphics,
                                     int x,
                                     int y,
                                     int width,
                                     int height,
                                     int position,
                                     int maxPosition) {
        if (maxPosition <= 0) {
            return;
        }
        drawVerticalScrollbar(
                graphics,
                x + width - 5,
                y + 3,
                3,
                Math.max(10, height - 6),
                position,
                maxPosition
        );
    }

    private int getOverviewDashboardContentHeight(int width, int viewportHeight) {
        int cardGap = 6;
        int cardH = 42;
        int cardCols = width >= 560 ? 4 : width >= 360 ? 2 : 1;
        int cardRows = (4 + cardCols - 1) / cardCols;
        int cardsBlock = (cardRows * cardH) + (Math.max(0, cardRows - 1) * cardGap);
        boolean compactCharts = width < 420;
        int chartsBlock = compactCharts ? (62 + 8 + 62 + 10) : (64 + 10);
        int listBlock = 56;
        int estimated = 4 + cardsBlock + 4 + chartsBlock + listBlock + 8;
        return Math.max(viewportHeight, estimated);
    }

    private int getAccountProfileContentHeight(int width, int viewportHeight) {
        int cardGap = 8;
        int cardCols = width >= 420 ? 2 : 1;
        int cardH = 42;
        int cardsBlock = cardCols > 1 ? cardH : ((cardH * 2) + cardGap);
        int estimated = 38 + cardsBlock + 10 + 40 + 8 + 70 + 8;
        return Math.max(viewportHeight, estimated);
    }

    private int getInputHelpContentHeight(InputHelp help, int width, int viewportHeight) {
        int summaryWidth = Math.max(80, width - 16);
        List<String> summaryLines = wrapLines(List.of(help.summary()), summaryWidth);
        int estimated = 36 + 14 + (summaryLines.size() * LINE_HEIGHT) + 22 + 8;
        return Math.max(viewportHeight, estimated);
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
        if (width < 40 || height < 40) {
            return;
        }
        graphics.fill(x, y, x + width, y + height, 0x40213A56);

        String normalizedAction = action == null ? "SHOW_INFO" : action.toUpperCase(Locale.ROOT);
        int cardGap = height < 200 ? 4 : 6;
        int cardH = height < 190 ? 34 : height < 250 ? 38 : 42;
        int cardCols = width >= 560 ? 4 : width >= 360 ? 2 : 1;
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
        int cardsTop = y + 4;
        for (int i = 0; i < cardLabels.length; i++) {
            int col = i % cardCols;
            int row = i / cardCols;
            int cardX = x + (col * (cardW + cardGap));
            int cardY = cardsTop + (row * (cardH + cardGap));
            drawMetricCard(graphics, cardX, cardY, cardW, cardH, cardLabels[i], cardValues[i], accents[i]);
        }

        int cardRows = (cardLabels.length + cardCols - 1) / cardCols;
        int cursorY = cardsTop + (cardRows * (cardH + cardGap)) + 4;
        int contentBottom = y + height - 6;
        int remaining = contentBottom - cursorY;
        boolean compactCharts = width < 420;
        int chartGap = 8;
        int chartW = compactCharts ? width : (width - chartGap) / 2;

        float reserveCoverage = minReserve.signum() <= 0
                ? 1.0F
                : reserve.divide(minReserve, 4, RoundingMode.HALF_EVEN).floatValue();
        float dailyUsedPct = dailyCap.signum() <= 0
                ? 0.0F
                : dailyUsed.divide(dailyCap, 4, RoundingMode.HALF_EVEN).floatValue();
        float liquidityHeadroom = Math.max(0.0F, Math.min(1.0F, dailyRemaining.signum() <= 0
                ? 0.0F
                : dailyRemaining.divide(dailyCap.max(BigDecimal.ONE), 4, RoundingMode.HALF_EVEN).floatValue()));
        String firstBarTitle = "SHOW_RESERVE".equals(normalizedAction) ? "Reserve Cushion" : "Reserve Coverage";
        String firstBarSubtitle = "SHOW_RESERVE".equals(normalizedAction)
                ? "$" + compactCurrency(data.reserve()) + " vs min $" + compactCurrency(data.minReserve())
                : "Min $" + compactCurrency(data.minReserve());
        String secondBarTitle = "SHOW_DASHBOARD".equals(normalizedAction) ? "Liquidity Headroom" : "Daily Utilization";
        String secondBarSubtitle = "SHOW_DASHBOARD".equals(normalizedAction)
                ? "$" + compactCurrency(data.dailyRemaining()) + " available"
                : "$" + compactCurrency(data.dailyUsed()) + " / $" + compactCurrency(data.dailyCap());
        float secondBarValue = "SHOW_DASHBOARD".equals(normalizedAction) ? liquidityHeadroom : dailyUsedPct;

        if (remaining >= 44) {
            if (compactCharts) {
                boolean drawTwo = remaining >= 98;
                int chartH = drawTwo
                        ? Math.min(62, Math.max(42, (remaining - chartGap - 14) / 2))
                        : Math.min(58, Math.max(42, remaining - 14));
                drawBarCard(graphics, x, cursorY, chartW, chartH, firstBarTitle, reserveCoverage, firstBarSubtitle,
                        reserve.compareTo(minReserve) >= 0 ? 0xFF64D47B : 0xFFE36D6D);
                cursorY += chartH + 8;
                if (drawTwo) {
                    drawBarCard(graphics, x, cursorY, chartW, chartH, secondBarTitle, secondBarValue, secondBarSubtitle, 0xFF6FB8FF);
                    cursorY += chartH + 10;
                } else {
                    cursorY += 2;
                }
            } else {
                int chartH = Math.min(64, Math.max(42, Math.min(58, remaining - 34)));
                drawBarCard(graphics, x, cursorY, chartW, chartH, firstBarTitle, reserveCoverage, firstBarSubtitle,
                        reserve.compareTo(minReserve) >= 0 ? 0xFF64D47B : 0xFFE36D6D);
                drawBarCard(graphics, x + chartW + chartGap, cursorY, chartW, chartH, secondBarTitle, secondBarValue,
                        secondBarSubtitle, 0xFF6FB8FF);
                cursorY += chartH + 10;
            }
        }

        int listTop = cursorY;
        int listBottom = contentBottom;
        if (listBottom - listTop >= 48) {
            graphics.fill(x, listTop, x + width, listBottom, 0x55273E59);
            graphics.fill(x, listTop, x + width, listTop + 1, 0x88A9CBED);

            int maxRows = Math.max(1, (listBottom - listTop - 10) / 12);
            int rowY = listTop + 6;
            if ("SHOW_RESERVE".equals(normalizedAction)) {
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Reserve Audit", x + 8, rowY, 0xFFE6F3FF, false);
                    graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                }
                rowY += 12;
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Declared Reserve: $" + compactCurrency(data.reserve()), x + 8, rowY, 0xFFD3E9FF, false);
                    graphics.drawString(this.font, "Minimum Reserve: $" + compactCurrency(data.minReserve()), x + (width / 2), rowY, 0xFFD3E9FF, false);
                }
                rowY += 12;
                if (maxRows > 0) {
                    graphics.drawString(this.font, "Daily Used: $" + compactCurrency(data.dailyUsed()), x + 8, rowY, 0xFFC6DEFA, false);
                    graphics.drawString(this.font, "Daily Cap: $" + compactCurrency(data.dailyCap()), x + (width / 2), rowY, 0xFFC6DEFA, false);
                }
            } else if ("SHOW_DASHBOARD".equals(normalizedAction)) {
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Operations Snapshot", x + 8, rowY, 0xFFE6F3FF, false);
                    graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                }
                rowY += 12;
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Owner: " + fitToWidth(data.ownerName(), Math.max(40, width / 2 - 20)), x + 8, rowY, 0xFFD3E9FF, false);
                    graphics.drawString(this.font, "Accounts: " + data.accountsCount(), x + (width / 2), rowY, 0xFFD3E9FF, false);
                }
                rowY += 12;
                if (maxRows > 0) {
                    graphics.drawString(this.font, "Fed Funds: " + data.federalFundsRate() + "%", x + 8, rowY, 0xFFC6DEFA, false);
                    graphics.drawString(this.font, "Remaining Today: $" + compactCurrency(data.dailyRemaining()), x + (width / 2), rowY, 0xFFC6DEFA, false);
                }
            } else {
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Bank Profile", x + 8, rowY, 0xFFE6F3FF, false);
                    graphics.drawString(this.font, "Status: " + data.status(), x + (width / 2), rowY, 0xFFE6F3FF, false);
                }
                rowY += 12;
                if (maxRows-- > 0) {
                    graphics.drawString(this.font, "Owner: " + fitToWidth(data.ownerName(), Math.max(40, width / 2 - 20)), x + 8, rowY, 0xFFD3E9FF, false);
                    graphics.drawString(this.font, "Model: " + data.ownershipModel(), x + (width / 2), rowY, 0xFFD3E9FF, false);
                }
                rowY += 12;
                if (maxRows > 0) {
                    graphics.drawString(this.font, "Color: " + data.color(), x + 8, rowY, 0xFFC6DEFA, false);
                    graphics.drawString(this.font, "Motto: " + fitToWidth(data.motto().isBlank() ? "-" : data.motto(), Math.max(40, width / 2 - 20)), x + (width / 2), rowY, 0xFFC6DEFA, false);
                }
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
        List<String> cards = extractOverviewCardEntries(action);
        if (cards.isEmpty()) {
            String query = formValues.getOrDefault("overview.accounts.search", "").trim();
            if ("SHOW_ACCOUNTS".equalsIgnoreCase(action) && !query.isBlank()) {
                graphics.drawString(this.font, "No accounts match \"" + fitToWidth(query, 40) + "\".", x + 6, y + 8, 0xFFE6F3FF, false);
                graphics.drawString(this.font, "Try player name, type, or account id.", x + 6, y + 20, 0xFFBFD7EE, false);
            } else {
                graphics.drawString(this.font, "No entries available.", x + 6, y + 8, 0xFFE6F3FF, false);
            }
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

    private List<String> extractOverviewCardEntries(String action) {
        List<String> base = ClientOwnerPcData.getActionOutputLines();
        if (base.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        String query = formValues.getOrDefault("overview.accounts.search", "").trim().toLowerCase(Locale.ROOT);
        boolean filterAccounts = "SHOW_ACCOUNTS".equalsIgnoreCase(action) && !query.isBlank();
        for (int i = 0; i < base.size(); i++) {
            String line = base.get(i) == null ? "" : base.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (i == 0 && line.startsWith("Bank:")) {
                continue;
            }
            if (line.startsWith("- ")) {
                String entry = line.substring(2).trim();
                if (filterAccounts) {
                    AccountCardData account = parseAccountCard(entry);
                    if (account == null || !accountMatchesQuery(account, query)) {
                        continue;
                    }
                }
                out.add(entry);
            } else if (!line.contains("(") || !line.endsWith(")")) {
                if (filterAccounts) {
                    AccountCardData account = parseAccountCard(line);
                    if (account == null || !accountMatchesQuery(account, query)) {
                        continue;
                    }
                }
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
            String balance = parts.length > 2 ? "$" + compactCurrency(parts[2].trim()) : "$0.00";
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
            String maturity = parts.length > 3 ? abbreviateMoneyInLine(parts[3].trim()) : "";

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
            String remaining = parts.length > 2 ? abbreviateMoneyInLine(parts[2].trim()) : "";
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
            graphics.drawString(this.font, fitToWidth(abbreviateMoneyInLine(text), width - 12), x + 6, y + 22, 0xFFFFFFFF, false);
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

    private boolean accountMatchesQuery(AccountCardData account, String query) {
        if (account == null || query == null || query.isBlank()) {
            return true;
        }
        String q = query.toLowerCase(Locale.ROOT);
        return account.player().toLowerCase(Locale.ROOT).contains(q)
                || account.type().toLowerCase(Locale.ROOT).contains(q)
                || account.id().toLowerCase(Locale.ROOT).contains(q)
                || account.balance().toLowerCase(Locale.ROOT).contains(q);
    }

    private void drawAccountProfilePanel(GuiGraphics graphics,
                                         AccountCardData account,
                                         int x,
                                         int y,
                                         int width,
                                         int height) {
        accountProfileCopyIdHitbox = null;
        if (width < 60 || height < 90) {
            return;
        }
        String fullBalance = "$" + compactCurrency(account.balance());
        int headerH = Math.min(46, Math.max(40, height / 5));
        graphics.fill(x, y, x + width, y + height, 0x5A1D3550);
        graphics.fill(x, y, x + width, y + headerH, 0xB2234B73);
        graphics.fill(x, y + headerH, x + width, y + headerH + 1, 0x889CC8EE);
        graphics.drawString(this.font, "Account Profile", x + 8, y + 10, 0xFFFFFFFF, false);
        int profileTextX = x + 118;
        int profileTextW = Math.max(52, width - 126);
        graphics.drawString(this.font, fitToWidth(account.player(), profileTextW), profileTextX, y + 9, 0xFFE2F1FF, false);
        graphics.drawString(this.font, fitToWidth(account.type(), profileTextW), profileTextX, y + 22, 0xFFCFE6FF, false);

        int contentX = x + 10;
        int contentW = Math.max(120, width - 20);
        int balanceCardY = y + headerH + 8;
        int balanceCardH = 30;
        int idCardH = Math.max(32, Math.min(38, height / 4));
        int idCardY = y + height - idCardH - 8;

        int detailsTop = balanceCardY + balanceCardH + 8;
        int detailsBottom = idCardY - 8;
        if (detailsBottom < detailsTop + 20) {
            detailsBottom = detailsTop + 20;
        }

        graphics.fill(contentX - 1, balanceCardY - 1, contentX + contentW + 1, balanceCardY + balanceCardH + 1, 0xFF2E4D6D);
        graphics.fill(contentX, balanceCardY, contentX + contentW, balanceCardY + balanceCardH, 0x8A1A304A);
        graphics.fill(contentX, balanceCardY, contentX + contentW, balanceCardY + 2, 0xFF67C789);
        graphics.drawString(this.font, "Balance", contentX + 8, balanceCardY + 7, 0xFFC6DEF7, false);
        graphics.drawString(this.font, fitToWidth(fullBalance, contentW - 90), contentX + 74, balanceCardY + 7, 0xFFFFFFFF, false);

        graphics.fill(contentX - 1, idCardY - 1, contentX + contentW + 1, idCardY + idCardH + 1, 0xFF2E4D6D);
        graphics.fill(contentX, idCardY, contentX + contentW, idCardY + idCardH, 0x8A1A304A);
        graphics.fill(contentX, idCardY, contentX + contentW, idCardY + 2, 0xFF70B9F2);
        graphics.drawString(this.font, "Account ID", contentX + 6, idCardY + 7, 0xFFC6DEF7, false);

        int copyW = Math.min(88, Math.max(72, contentW / 4));
        int copyH = 16;
        int copyX = contentX + contentW - copyW - 8;
        int copyY = idCardY + Math.max(8, (idCardH - copyH) / 2);
        drawInlineActionButton(graphics, copyX, copyY, copyW, copyH, "Copy ID", 0xFF5E9ED0);
        accountProfileCopyIdHitbox = new RectHitbox(copyX, copyY, copyW, copyH);

        int idTextY = idCardY + Math.max(8, (idCardH / 2) - 1);
        int idTextW = Math.max(20, contentW - copyW - 28);
        graphics.drawString(this.font, fitToWidth(account.id(), idTextW), contentX + 6, idTextY, 0xFFFFFFFF, false);

        if (detailsBottom - detailsTop >= 36) {
            graphics.fill(contentX, detailsTop, contentX + contentW, detailsBottom, 0x6A16304A);
            graphics.fill(contentX, detailsTop, contentX + contentW, detailsTop + 1, 0x88A8CDEE);

            int avatarSize = Math.min(34, Math.max(24, detailsBottom - detailsTop - 18));
            int avatarX = contentX + 8;
            int avatarY = detailsTop + 7;
            graphics.fill(avatarX - 1, avatarY - 1, avatarX + avatarSize + 1, avatarY + avatarSize + 1, 0xFF2E567D);
            graphics.fill(avatarX, avatarY, avatarX + avatarSize, avatarY + avatarSize, 0xFF67A5DC);
            String initials = account.player().isBlank() ? "?" : account.player().substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawCenteredString(this.font, initials, avatarX + (avatarSize / 2), avatarY + (avatarSize / 2) - 4, 0xFFFFFFFF);

            int rowX = avatarX + avatarSize + 12;
            int rowW = Math.max(40, contentX + contentW - rowX - 8);
            int rowY = detailsTop + 8;
            int rowStep = 12;
            graphics.drawString(this.font, "Player", rowX, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.player(), rowW - 62), rowX + 56, rowY, 0xFFFFFFFF, false);
            rowY += rowStep;
            graphics.drawString(this.font, "Type", rowX, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, fitToWidth(account.type(), rowW - 62), rowX + 56, rowY, 0xFFFFFFFF, false);
            rowY += rowStep;
            graphics.drawString(this.font, "Status", rowX, rowY, 0xFFBFDFFF, false);
            graphics.drawString(this.font, "Active", rowX + 56, rowY, 0xFF8BE3A8, false);
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
        int listW = Math.max(1, width - 8);
        int listH = Math.max(1, height - 34);
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

            enableScaledScissor(graphics, listX, listY, listX + listW, listY + listH);
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
                    "Use: single, dailyplayer, dailybank, or teller. Each type updates a different rule.",
                    "Example: teller"
            );
            case "limits.amount" -> new InputHelp(
                    "Limit Amount",
                    "Positive whole number used by the selected limit type.",
                    "Example: 25000"
            );
            case "limits.cardIssueFee" -> new InputHelp(
                    "Card Issue Fee",
                    "Fee charged when a customer requests a new credit card from this bank.",
                    "Example: 25"
            );
            case "limits.cardReplacementFee" -> new InputHelp(
                    "Card Replacement Fee",
                    "Fee charged when replacing a lost/stolen card. Old cards are blocked.",
                    "Example: 50"
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
        return MoneyText.abbreviate(value);
    }

    private String abbreviateMoneyInLine(String line) {
        return MoneyText.abbreviateCurrencyTokens(line);
    }

    private int utf8Bytes(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private List<String> getWrappedOutputLines() {
        List<String> base = ClientOwnerPcData.getActionOutputLines();
        if (base.isEmpty() || outputPanelW <= 0) {
            return List.of();
        }
        List<String> formatted = new ArrayList<>(base.size());
        for (String line : base) {
            formatted.add(abbreviateMoneyInLine(line));
        }
        return wrapLines(formatted, Math.max(1, outputPanelW - 14));
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

        graphics.drawString(this.font, "Ownership Type", left + 8, top + 56, 0xFF1D2F4A, false);
        graphics.drawString(this.font,
                "Selected: " + prettifyOwnership(selectedOwnershipModel),
                left + 8,
                top + 68,
                0xFF2A496E,
                false);
    }

    private void drawUtilityWindowFrame(GuiGraphics graphics) {
        int left = utilityFrameLeft > 0 ? utilityFrameLeft : PAD + 12;
        int top = utilityFrameTop > 0 ? utilityFrameTop : PAD + TOPBAR_HEIGHT + 10;
        int right = utilityFrameRight > 0 ? utilityFrameRight : this.width - PAD - 12;
        int bottom = utilityFrameBottom > 0 ? utilityFrameBottom : this.height - PAD - TASKBAR_HEIGHT - 8;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF2A3D59);
        graphics.fill(left, top, right, bottom, 0xFFE8EEF6);
        graphics.fill(left, top, right, top + 28, 0xFF6C93C8);

        String title = "Utilities / " + utilityWindowTitle(activeUtilityApp);
        graphics.drawString(this.font, fitToWidth(title, right - left - 20), left + 8, top + 10, 0xFFFFFFFF, false);

        int contentX = utilityContentX > 0 ? utilityContentX : left + 12;
        int contentY = utilityContentY > 0 ? utilityContentY : top + 38;
        int contentW = utilityContentW > 0 ? utilityContentW : Math.max(180, right - left - 24);
        int contentH = utilityContentH > 0 ? utilityContentH : Math.max(120, bottom - contentY - 10);

        graphics.fill(contentX - 1, contentY - 1, contentX + contentW + 1, contentY + contentH + 1, 0xFF2C4768);
        graphics.fill(contentX, contentY, contentX + contentW, contentY + contentH, 0xCC19314A);

        if (activeUtilityApp == UtilityApp.CALCULATOR) {
            drawCalculatorApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.NOTEPAD) {
            drawNotepadApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.FILE_EXPLORER) {
            drawFileExplorerApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.PAINT) {
            drawPaintApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.SYSTEM_MONITOR) {
            drawSystemMonitorApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        }

        if (unsavedClosePromptOpen) {
            int modalW = 330;
            int modalH = 98;
            int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
            int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);
            graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF2D4B6D);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF01A3049);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + 20, 0xE6285A8B);
            graphics.drawString(this.font, "Unsaved changes", modalX + 8, modalY + 6, 0xFFFFFFFF, false);
            String label = unsavedCloseTarget == UtilityApp.PAINT ? "Paint" : "Notepad";
            graphics.drawString(this.font, "Save " + label + " before closing?", modalX + 8, modalY + 30, 0xFFD5E9FF, false);
        }
    }

    private void drawCalculatorApp(GuiGraphics graphics, int x, int y, int width, int height) {
        int displayH = Math.min(48, Math.max(40, height / 4));
        graphics.fill(x, y, x + width, y + displayH, 0x7A162E48);
        graphics.fill(x, y, x + width, y + 1, 0x889FCEEF);
        graphics.drawString(this.font, fitToWidth("Expression: " + (calculatorExpression.isBlank() ? "-" : calculatorExpression), width - 12), x + 6, y + 8, 0xFFCDE6FF, false);
        graphics.drawString(this.font, fitToWidth("Result: " + calculatorDisplay, width - 12), x + 6, y + 20, 0xFFFFFFFF, false);
        graphics.drawString(this.font, fitToWidth("Status: " + calculatorStatus, width - 12), x + 6, y + 32, 0xFF9FD3FF, false);
    }

    private void drawNotepadApp(GuiGraphics graphics, int x, int y, int width, int height) {
        notepadAreaX = x + 4;
        notepadAreaY = Math.max(y + 30, notepadAreaY);
        notepadAreaW = Math.max(120, width - 8);
        notepadAreaH = Math.max(64, Math.min(height - 34, (y + height) - notepadAreaY - 2));

        graphics.fill(notepadAreaX - 1, notepadAreaY - 1, notepadAreaX + notepadAreaW + 1, notepadAreaY + notepadAreaH + 1, 0xFF2D4B6D);
        graphics.fill(notepadAreaX, notepadAreaY, notepadAreaX + notepadAreaW, notepadAreaY + notepadAreaH, 0xEE10253B);

        NotepadLayout layout = buildNotepadLayout(Math.max(1, notepadAreaW - 14));
        List<String> lines = layout.lines();
        int visible = Math.max(1, (notepadAreaH - 8) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visible);
        if (notepadScroll == Integer.MAX_VALUE) {
            notepadScroll = maxScroll;
        } else {
            notepadScroll = Math.max(0, Math.min(notepadScroll, maxScroll));
        }

        int lineY = notepadAreaY + 4;
        for (int i = 0; i < visible; i++) {
            int idx = notepadScroll + i;
            if (idx >= lines.size()) {
                break;
            }
            graphics.drawString(this.font, lines.get(idx), notepadAreaX + 6, lineY, 0xFFE7F3FF, false);
            lineY += LINE_HEIGHT;
        }

        if (notepadFocused && ((System.currentTimeMillis() / 400L) % 2L) == 0L) {
            int cursor = Math.max(0, Math.min(notepadCursorIndex, notepadText.length()));
            int caretLineIndex = 0;
            int caretColumn = 0;
            for (int i = 0; i < lines.size(); i++) {
                int start = layout.starts().get(i);
                int endExclusive = start + lines.get(i).length();
                boolean inLine = (cursor >= start && cursor <= endExclusive)
                        || (i == lines.size() - 1 && cursor >= start);
                if (inLine) {
                    caretLineIndex = i - notepadScroll;
                    caretColumn = Math.max(0, Math.min(lines.get(i).length(), cursor - start));
                    break;
                }
            }
            if (caretLineIndex < visible) {
                String caretLine = lines.isEmpty() ? "" : lines.get(Math.max(0, Math.min(lines.size() - 1, caretLineIndex + notepadScroll)));
                String left = caretLine.substring(0, Math.max(0, Math.min(caretLine.length(), caretColumn)));
                int cx = notepadAreaX + 6 + Math.min(this.font.width(left), notepadAreaW - 16);
                int cy = notepadAreaY + 4 + (caretLineIndex * LINE_HEIGHT);
                graphics.fill(cx, cy, cx + 1, cy + 9, 0xFFFFFFFF);
            }
        }

        if (maxScroll > 0) {
            int barX1 = notepadAreaX + notepadAreaW - 4;
            int barX2 = notepadAreaX + notepadAreaW - 1;
            graphics.fill(barX1, notepadAreaY + 1, barX2, notepadAreaY + notepadAreaH - 1, 0x553C5878);
            int thumbH = Math.max(10, (int) ((notepadAreaH - 2) * (visible / (float) lines.size())));
            int thumbTravel = Math.max(1, (notepadAreaH - 2) - thumbH);
            int thumbY = notepadAreaY + 1 + (int) (thumbTravel * (notepadScroll / (float) maxScroll));
            graphics.fill(barX1, thumbY, barX2, thumbY + thumbH, 0xCC9FD1FF);
        }

        if (notepadSaveModalOpen) {
            int modalW = Math.min(340, Math.max(180, utilityContentW - 40));
            int modalH = 108;
            int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
            int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);

            graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF2D4B6D);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF01A3049);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + 20, 0xE6285A8B);
            graphics.drawString(this.font, "Save Notepad", modalX + 8, modalY + 6, 0xFFFFFFFF, false);
            graphics.drawString(this.font, "Enter a file name:", modalX + 10, modalY + 26, 0xFFD5E9FF, false);
        }
    }

    private void drawFileExplorerApp(GuiGraphics graphics, int x, int y, int width, int height) {
        int innerX = x + 4;
        int innerW = Math.max(120, width - 8);
        int infoY = Math.max(y + 4, explorerFileListY - 20);

        int used = ClientOwnerPcData.getDesktopUsedStorageBytes();
        int max = Math.max(1, ClientOwnerPcData.getDesktopMaxStorageBytes());
        String storageText = "PC " + fitToWidth(ClientOwnerPcData.getDesktopComputerLabel(), Math.max(80, innerW / 2))
                + "  |  Storage " + used + " / " + max + " bytes";
        graphics.drawString(this.font, fitToWidth(storageText, innerW - 8), innerX, infoY, 0xFFD6EBFF, false);

        graphics.drawString(
                this.font,
                "Click a file card to open it.",
                innerX,
                infoY + 11,
                0xFF9EF0B6,
                false
        );

        graphics.fill(explorerFileListX - 1,
                explorerFileListY - 1,
                explorerFileListX + explorerFileListW + 1,
                explorerFileListY + explorerFileListH + 1,
                0xFF2D4B6D);
        graphics.fill(explorerFileListX,
                explorerFileListY,
                explorerFileListX + explorerFileListW,
                explorerFileListY + explorerFileListH,
                0xC0182E46);

        List<OwnerPcFileEntry> files = ClientOwnerPcData.getDesktopFiles();
        if (files.isEmpty()) {
            graphics.drawString(this.font, "No files saved on this PC yet.", explorerFileListX + 8, explorerFileListY + 8, 0xFF9FC2E6, false);
        }
    }

    private void drawPaintApp(GuiGraphics graphics, int x, int y, int width, int height) {
        int sideW = Math.min(166, Math.max(132, width / 4));
        int canvasAreaX = x + 6;
        int canvasAreaY = y + 30;
        int canvasAreaW = Math.max(120, width - sideW - 14);
        int canvasAreaH = Math.max(80, height - 36);

        paintCellSize = Math.max(2, Math.min(canvasAreaW / paintCanvasW, canvasAreaH / paintCanvasH));
        int pixelW = paintCanvasW * paintCellSize;
        int pixelH = paintCanvasH * paintCellSize;
        paintCanvasX = canvasAreaX + Math.max(0, (canvasAreaW - pixelW) / 2);
        paintCanvasY = canvasAreaY + Math.max(0, (canvasAreaH - pixelH) / 2);

        graphics.drawString(this.font,
                "Brush: " + paintBrushSize + "   Color: " + paintColorLabel(paintSelectedColor),
                x + 6,
                y + 10,
                0xFFE6F3FF,
                false);

        if (paintControlsW > 0 && paintControlsH > 0) {
            graphics.fill(paintControlsX - 1, paintControlsY - 1, paintControlsX + paintControlsW + 1, paintControlsY + paintControlsH + 1, 0xFF2D4B6D);
            graphics.fill(paintControlsX, paintControlsY, paintControlsX + paintControlsW, paintControlsY + paintControlsH, 0xA0182F47);
            if (paintControlsMaxScroll > 0) {
                drawVerticalScrollbar(
                        graphics,
                        paintControlsX + paintControlsW - 4,
                        paintControlsY + 1,
                        3,
                        Math.max(10, paintControlsH - 2),
                        paintControlsScroll,
                        paintControlsMaxScroll
                );
            }
        }

        graphics.fill(paintCanvasX - 2, paintCanvasY - 2, paintCanvasX + pixelW + 2, paintCanvasY + pixelH + 2, 0xFF2B4B6C);
        graphics.fill(paintCanvasX - 1, paintCanvasY - 1, paintCanvasX + pixelW + 1, paintCanvasY + pixelH + 1, 0xFF0F2135);

        for (int py = 0; py < paintCanvasH; py++) {
            int rowOffset = py * paintCanvasW;
            int drawY = paintCanvasY + (py * paintCellSize);
            for (int px = 0; px < paintCanvasW; px++) {
                int drawX = paintCanvasX + (px * paintCellSize);
                int color = paintPixels[rowOffset + px];
                graphics.fill(drawX, drawY, drawX + paintCellSize, drawY + paintCellSize, color);
            }
        }

        if (paintCellSize >= 8) {
            for (int px = 0; px <= paintCanvasW; px++) {
                int gx = paintCanvasX + (px * paintCellSize);
                graphics.fill(gx, paintCanvasY, gx + 1, paintCanvasY + pixelH, 0x22000000);
            }
            for (int py = 0; py <= paintCanvasH; py++) {
                int gy = paintCanvasY + (py * paintCellSize);
                graphics.fill(paintCanvasX, gy, paintCanvasX + pixelW, gy + 1, 0x22000000);
            }
        }

        if (paintSaveModalOpen) {
            int modalW = Math.min(340, Math.max(180, utilityContentW - 40));
            int modalH = 108;
            int modalX = utilityContentX + Math.max(0, (utilityContentW - modalW) / 2);
            int modalY = utilityContentY + Math.max(0, (utilityContentH - modalH) / 2);
            graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF2D4B6D);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xF01A3049);
            graphics.fill(modalX, modalY, modalX + modalW, modalY + 20, 0xE6285A8B);
            graphics.drawString(this.font, "Save Canvas", modalX + 8, modalY + 6, 0xFFFFFFFF, false);
            graphics.drawString(this.font, "Enter a file name:", modalX + 10, modalY + 26, 0xFFD5E9FF, false);
        }
    }

    private void drawSystemMonitorApp(GuiGraphics graphics, int x, int y, int width, int height) {
        if (systemHideAppsMenuOpen) {
            int panelX = systemHideAppsX > 0 ? systemHideAppsX : (utilityContentX + 8);
            int panelY = systemHideAppsY > 0 ? systemHideAppsY : (utilityContentY + 34);
            int panelW = systemHideAppsW > 0 ? systemHideAppsW : Math.max(120, utilityContentW - 16);
            int panelH = systemHideAppsH > 0 ? systemHideAppsH : Math.max(80, utilityContentH - 36);
            graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF2D4B6D);
            graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xC8192F47);
            graphics.drawString(this.font, "App Visibility", panelX + 8, panelY + 8, 0xFFE4F2FF, false);
            graphics.drawString(this.font, "Click a card to toggle hidden/visible.", panelX + 8, panelY + 19, 0xFFBFD6EE, false);

            for (AppVisibilityCard card : visibleSystemAppCards) {
                boolean hidden = card.app().hidden();
                int accent = hidden ? 0xFFD95C5C : 0xFF6FD39A;
                graphics.fill(card.x(), card.y(), card.x() + card.width(), card.y() + 2, accent);
                String status = hidden ? "Hidden" : "Visible";
                graphics.drawString(this.font, status, card.x() + 8, card.y() + card.height() - 11, hidden ? 0xFFFFC7C7 : 0xFFC7FFE0, false);
            }
            return;
        }

        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int guiScale = 0;
        if (mc != null && mc.options != null && mc.options.guiScale() != null && mc.options.guiScale().get() != null) {
            guiScale = mc.options.guiScale().get();
        }
        long gameTime = mc != null && mc.level != null ? mc.level.getDayTime() : -1L;
        long day = gameTime >= 0 ? (gameTime / 24000L) : -1L;
        long dayTime = gameTime >= 0 ? (gameTime % 24000L) : -1L;

        int gap = 8;
        int cols = width >= 560 ? 2 : 1;
        int cardW = Math.max(120, (width - (gap * (cols - 1))) / cols);
        int cardH = 46;
        int viewportX = systemMonitorViewportX > 0 ? systemMonitorViewportX : (x + 2);
        int viewportY = systemMonitorViewportY > 0 ? systemMonitorViewportY : (y + 34);
        int viewportW = systemMonitorViewportW > 0 ? systemMonitorViewportW : Math.max(1, width - 4);
        int viewportH = systemMonitorViewportH > 0 ? systemMonitorViewportH : Math.max(1, height - 38);
        int cardStartY = viewportY + 34 - systemMonitorScroll;
        List<String[]> entries = List.of(
                new String[]{"Resolution", this.width + "x" + this.height},
                new String[]{"GUI Scale", String.valueOf(guiScale)},
                new String[]{"PC UI Scale", "Native"},
                new String[]{"Virtual Scale", String.valueOf(useVirtualScale)},
                new String[]{"Bank Windows", String.valueOf(bankWindowOrder.size())},
                new String[]{"Utility Windows", String.valueOf(utilityWindowOrder.size())},
                new String[]{"In-Game Day", day < 0 ? "-" : String.valueOf(day)},
                new String[]{"Day Time", dayTime < 0 ? "-" : String.valueOf(dayTime)},
                new String[]{"Local Time", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}
        );

        enableScaledScissor(graphics, viewportX, viewportY, viewportX + viewportW, viewportY + viewportH);
        for (int i = 0; i < entries.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = cardStartY + (row * (cardH + gap));
            if (cardY + cardH < viewportY || cardY > viewportY + viewportH) {
                continue;
            }
            drawMetricCard(graphics, cardX, cardY, cardW, cardH, entries.get(i)[0], entries.get(i)[1], 0xFF67B5F2);
        }
        graphics.disableScissor();

        if (systemMonitorMaxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    viewportX + viewportW - 4,
                    viewportY + 1,
                    3,
                    Math.max(10, viewportH - 2),
                    systemMonitorScroll,
                    systemMonitorMaxScroll
            );
        }
    }

    private NotepadLayout buildNotepadLayout(int maxWidth) {
        if (maxWidth <= 0) {
            return new NotepadLayout(List.of(""), List.of(0));
        }

        List<String> lines = new ArrayList<>();
        List<Integer> starts = new ArrayList<>();
        String text = notepadText.toString();
        int length = text.length();
        int lineStart = 0;
        int lineWidth = 0;
        int lastBreakIndex = -1;
        int i = 0;
        while (i < length) {
            char c = text.charAt(i);
            if (c == '\n') {
                lines.add(text.substring(lineStart, i));
                starts.add(lineStart);
                i++;
                lineStart = i;
                lineWidth = 0;
                lastBreakIndex = -1;
                continue;
            }

            int charWidth = this.font.width(String.valueOf(c));
            int nextWidth = lineWidth + charWidth;
            if (nextWidth > maxWidth && i > lineStart) {
                int wrapIndex = i;
                int newLineStart = i;
                if (lastBreakIndex >= lineStart) {
                    wrapIndex = lastBreakIndex + 1;
                    newLineStart = wrapIndex;
                    while (newLineStart < length && text.charAt(newLineStart) == ' ') {
                        newLineStart++;
                    }
                }

                lines.add(text.substring(lineStart, wrapIndex));
                starts.add(lineStart);
                lineStart = newLineStart;
                lineWidth = this.font.width(text.substring(lineStart, i));
                lastBreakIndex = -1;
                continue;
            }

            lineWidth = nextWidth;
            if (c == ' ' || c == '\t') {
                lastBreakIndex = i;
            }
            i++;
        }

        lines.add(text.substring(lineStart));
        starts.add(lineStart);
        if (lines.isEmpty()) {
            lines.add("");
            starts.add(0);
        }
        return new NotepadLayout(lines, starts);
    }

    private boolean isInsidePaintCanvas(double mouseX, double mouseY) {
        if (paintCellSize <= 0) {
            return false;
        }
        int pixelW = paintCanvasW * paintCellSize;
        int pixelH = paintCanvasH * paintCellSize;
        return mouseX >= paintCanvasX
                && mouseX < (paintCanvasX + pixelW)
                && mouseY >= paintCanvasY
                && mouseY < (paintCanvasY + pixelH);
    }

    private void paintAt(double mouseX, double mouseY, int color) {
        if (!isInsidePaintCanvas(mouseX, mouseY)) {
            return;
        }
        int px = (int) ((mouseX - paintCanvasX) / paintCellSize);
        int py = (int) ((mouseY - paintCanvasY) / paintCellSize);
        int radius = Math.max(0, paintBrushSize - 1);
        for (int dy = -radius; dy <= radius; dy++) {
            int yy = py + dy;
            if (yy < 0 || yy >= paintCanvasH) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                int xx = px + dx;
                if (xx < 0 || xx >= paintCanvasW) {
                    continue;
                }
                paintPixels[(yy * paintCanvasW) + xx] = color;
            }
        }
    }

    private void onCalculatorButton(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        switch (token) {
            case "C" -> {
                calculatorExpression = "";
                calculatorDisplay = "0";
                calculatorStatus = "Cleared";
            }
            case "BK" -> {
                if (!calculatorExpression.isEmpty()) {
                    calculatorExpression = calculatorExpression.substring(0, calculatorExpression.length() - 1);
                    calculatorDisplay = calculatorExpression.isEmpty() ? "0" : calculatorExpression;
                    calculatorStatus = "Ready";
                }
            }
            case "=" -> evaluateCalculatorExpression();
            default -> {
                if (calculatorExpression.length() >= 128) {
                    calculatorStatus = "Expression too long";
                    return;
                }
                if ("ERR".equals(calculatorDisplay)
                        || ("OK".equals(calculatorStatus) && "0123456789.(".contains(token) && calculatorExpression.equals(calculatorDisplay))) {
                    calculatorExpression = "";
                }
                calculatorExpression = calculatorExpression + token;
                calculatorDisplay = calculatorExpression;
                calculatorStatus = "Ready";
            }
        }
    }

    private void evaluateCalculatorExpression() {
        String expr = calculatorExpression == null ? "" : calculatorExpression.trim();
        if (expr.isEmpty()) {
            return;
        }
        try {
            double value = new CalculatorParser(expr).parse();
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Non-finite result");
            }
            String result = BigDecimal.valueOf(value)
                    .setScale(10, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString();
            if (result.length() > 24) {
                result = String.format(Locale.ROOT, "%.8g", value);
            }
            calculatorDisplay = result;
            calculatorExpression = result;
            calculatorStatus = "OK";
        } catch (RuntimeException ex) {
            calculatorDisplay = "ERR";
            calculatorStatus = "Error";
        }
    }

    private static final class CalculatorParser {
        private final String input;
        private int index;

        private CalculatorParser(String input) {
            this.input = input == null ? "" : input;
        }

        private double parse() {
            double value = parseExpression();
            skipWhitespace();
            if (index < input.length()) {
                throw new IllegalArgumentException("Unexpected token");
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1.0E-12) {
                        throw new IllegalArgumentException("Division by zero");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing ')'");
                }
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipWhitespace();
            int start = index;
            boolean dotSeen = false;
            while (index < input.length()) {
                char c = input.charAt(index);
                if (c >= '0' && c <= '9') {
                    index++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected number");
            }
            return Double.parseDouble(input.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
                index++;
            }
        }

        private boolean match(char expected) {
            if (index < input.length() && input.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }
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

    private void enableScaledScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);

        if (useVirtualScale) {
            minX = (int) Math.floor(minX * virtualScaleX);
            minY = (int) Math.floor(minY * virtualScaleY);
            maxX = (int) Math.ceil(maxX * virtualScaleX);
            maxY = (int) Math.ceil(maxY * virtualScaleY);
        }

        Minecraft mc = Minecraft.getInstance();
        int screenW = this.width;
        int screenH = this.height;
        if (mc != null && mc.getWindow() != null) {
            screenW = mc.getWindow().getGuiScaledWidth();
            screenH = mc.getWindow().getGuiScaledHeight();
        }

        minX = Math.max(0, Math.min(screenW, minX));
        minY = Math.max(0, Math.min(screenH, minY));
        maxX = Math.max(0, Math.min(screenW, maxX));
        maxY = Math.max(0, Math.min(screenH, maxY));

        if (maxX <= minX || maxY <= minY) {
            return;
        }

        graphics.enableScissor(minX, minY, maxX, maxY);
    }

    private void configureVirtualScale() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null) {
            this.width = mc.getWindow().getGuiScaledWidth();
            this.height = mc.getWindow().getGuiScaledHeight();
        }

        // Keep the PC UI bound to the actual GUI viewport dimensions.
        // Virtual scaling caused partial-width rendering on some client setups.
        useVirtualScale = false;
        virtualScaleX = 1.0F;
        virtualScaleY = 1.0F;
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
