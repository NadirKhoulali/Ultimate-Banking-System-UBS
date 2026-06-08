package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopSlider;
import net.austizz.ultimatebankingsystem.network.OpenBankOwnerPcPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankAppSummary;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcBankDataRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcCreateBankPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcFileEntry;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.austizz.ultimatebankingsystem.compat.neoforge.network.PacketDistributor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private record ShopEmployeeCardData(int index,
                                        String employeeId,
                                        String cashierEntityId,
                                        String name,
                                        String dimension,
                                        String position,
                                        String terminalLabel) {}

    private record ShopEmployeeActionHitbox(int x,
                                            int y,
                                            int width,
                                            int height,
                                            String action,
                                            ShopEmployeeCardData employee) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopOwnerAccountCardData(int index,
                                            String accountId,
                                            String type,
                                            String bank,
                                            long balanceCents,
                                            boolean primary) {}

    private record ShopPermissionRoleHeaderData(int index,
                                                String roleKey,
                                                String roleLabel,
                                                String description,
                                                int count) {}

    private record ShopPermissionMemberCardData(int index,
                                                String roleKey,
                                                String playerId,
                                                String playerName,
                                                String assignedRole,
                                                boolean online,
                                                boolean owner,
                                                boolean guest,
                                                long grantedAtMillis,
                                                String location) {}

    private record ShopOwnerAccountCardHitbox(int x, int y, int width, int height, ShopOwnerAccountCardData account) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopPermissionMemberCardHitbox(int x,
                                                  int y,
                                                  int width,
                                                  int height,
                                                  ShopPermissionMemberCardData member) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopVaultAdjustHitbox(int x, int y, int width, int height, int index, boolean increase) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopLevelRoadmapNode(int level,
                                        long requiredRevenueDollars,
                                        long claimCapacityBlocks,
                                        long stockroomCapacityBlocks,
                                        int displayCap,
                                        int cashierCap,
                                        int deliveryPalletCap,
                                        String state) {
    }

    private record ShopLevelRoadmapSnapshot(String shopName,
                                            String shopType,
                                            int currentLevel,
                                            long currentRevenueDollars,
                                            long currentLevelFloorDollars,
                                            long nextLevelTargetDollars,
                                            double progressRatio,
                                            int maxLevel,
                                            List<ShopLevelRoadmapNode> nodes) {
    }

    private record ShopRoadmapNodeHitbox(int centerX, int centerY, int radius, ShopLevelRoadmapNode node) {
        boolean contains(double mouseX, double mouseY) {
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            return (dx * dx) + (dy * dy) <= (double) radius * radius;
        }
    }

    private record KpiCardHitbox(int x, int y, int width, int height, String label, String value, String description) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopOperationsHelpHitbox(int x, int y, int width, int height, String title, String description) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record SectionTextLabel(int x, int y, String text, int color) {
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

    private enum ScrollbarTarget {
        NAV,
        SECTION,
        OUTPUT,
        LENDING_MARKET,
        NOTEPAD,
        PAINT_CONTROLS,
        SHOP_MANAGER,
        SYSTEM_MONITOR,
        ORDER_BOARD,
        WEBSHOP
    }

    private record ScrollbarHitbox(ScrollbarTarget target,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   int position,
                                   int maxPosition,
                                   int thumbHeight,
                                   boolean rebuildOnDrag) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record MarketParseResult(boolean isMarketPayload, List<MarketOfferData> offers) {}

    private record ShopDashboardSnapshot(
            String shopName,
            String shopType,
            String ragStatus,
            long revenueDollars,
            long targetDollars,
            int level,
            long nextLevelTargetDollars,
            long usedClaimBlocks,
            long claimCapacityBlocks,
            int claimRegions,
            int stockroomRegions,
            int shelves,
            int configuredSlots,
            int lowStockSlots,
            int outOfStockSlots,
            long stockUnits,
            boolean stockUnitsInfinite,
            int cashiers,
            int linkedCashiers,
            long cashTxCount,
            long terminalTxCount,
            int cashCustomers,
            int terminalCustomers,
            long cashTotalCents,
            long terminalTotalCents,
            long vaultTotalCents,
            double aov,
            double conversionRate,
            double grossMarginPct,
            double operatingExpenses,
            double netProfit,
            double inventoryTurnover,
            double stockToSalesRatio,
            double salesPerLaborHour,
            long footTraffic,
            double waitSeconds,
            double serviceSeconds,
            double csat,
            double nps,
            long sixMonthCashflowForecast,
            long allShopsRevenueDollars,
            int allShopsCount,
            String bestShopName,
            List<Long> trendDailyRevenue,
            Map<String, Long> categorySalesValue
    ) {}

    private record ShopFinanceSnapshot(
            String settlementAccountId,
            String checkoutAccountId,
            String checkoutTerminal,
            long cashTxCount,
            long cashTotalCents,
            int cashCustomers,
            long terminalTxCount,
            long terminalTotalCents,
            int terminalCustomers,
            long vaultTotalCents
    ) {}

    private record ShopVaultSnapshot(
            long totalCents,
            List<Long> denominationsCents,
            List<Integer> counts
    ) {}

    private record ShopInventoryItemCardData(int shelfIndex,
                                             int slotIndex,
                                             String itemId,
                                             String itemName,
                                             long priceCents,
                                             int stock,
                                             boolean restockable,
                                             String targetKey,
                                             int minTarget,
                                             int maxTarget,
                                             int stockroomAvailable,
                                             long lastSoldMillis,
                                             double velocityPerDay) {}

    private record ShopInventoryShelfCardData(int index,
                                              String dimension,
                                              String position,
                                              boolean creative,
                                              int configuredSlots,
                                              int totalStock,
                                              int lowStock,
                                              int outOfStock,
                                              String shelfTarget,
                                              List<ShopInventoryItemCardData> items) {}

    private record ShopStockroomItemCardData(int index,
                                             String itemId,
                                             String itemName,
                                             int count,
                                             String inventoryType,
                                             String dimension,
                                             String position,
                                             int slot,
                                             int totalSlots,
                                             String locateTarget) {}

    private record ShopInventoryActionHitbox(int x,
                                             int y,
                                             int width,
                                             int height,
                                             String action,
                                             ShopInventoryItemCardData item,
                                             boolean enabled) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopInventoryShelfSelectHitbox(int x,
                                                  int y,
                                                  int width,
                                                  int height,
                                                  ShopInventoryShelfCardData shelf) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopStockroomLocateHitbox(int x,
                                             int y,
                                             int width,
                                             int height,
                                             ShopStockroomItemCardData item) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopOrderCardData(int index,
                                     String orderId,
                                     String itemId,
                                     String itemName,
                                     int quantity,
                                     long rewardCents,
                                     String status,
                                     String acceptedBy,
                                     long remainingSeconds,
                                     int timeoutMinutes,
                                     long createdAtMillis,
                                     String boundPalletRef) {}

    private record ShopOrderPalletCardData(int index,
                                           String palletRef,
                                           String dimension,
                                           int x,
                                           int y,
                                           int z,
                                           boolean assigned,
                                           boolean full) {}

    private record ShopOrderPickCardData(int index,
                                         String itemId,
                                         String itemName,
                                         int maxStack,
                                         int availableCount) {}

    private record ShopOrderCardHitbox(int x, int y, int width, int height, ShopOrderCardData order) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopOrderPalletCardHitbox(int x, int y, int width, int height, ShopOrderPalletCardData pallet) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record ShopOrderPickCardHitbox(int x, int y, int width, int height, ShopOrderPickCardData pick) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record OrderBoardSummary(int activeMine,
                                     int activeCap,
                                     long totalOpen,
                                     long totalVisible,
                                     long completedTotal,
                                     long canceledTotal,
                                     long streak,
                                     long bestStreak,
                                     long totalPayoutCents,
                                     int successRatePct,
                                     long nextRankAt,
                                     long nextStreakAt,
                                     String rankLabel) {}

    private record OrderBoardCardData(int index,
                                      String orderId,
                                      String shopId,
                                      String shopName,
                                      String itemId,
                                      String itemName,
                                      int quantity,
                                      long rewardCents,
                                      String status,
                                      String acceptedByName,
                                      long remainingSeconds,
                                      int timeoutMinutes,
                                      long createdAtMillis,
                                      String boundPalletRef) {}

    private record OrderBoardCardHitbox(int x, int y, int width, int height, OrderBoardCardData order) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopSummary(long catalogCount,
                                  long cartLines,
                                  long cartUnits,
                                  long subtotalCents,
                                  long surchargeCents,
                                  long totalCents,
                                  long queuedOrders,
                                  long maxActiveOrders,
                                  String selectedAccountId,
                                  String deliveryMode,
                                  String selectedShopId,
                                  String selectedPalletId,
                                  boolean expedite,
                                  String coordDim,
                                  int coordX,
                                  int coordY,
                                  int coordZ) {}

    private record WebshopCatalogCardData(int index,
                                          String itemId,
                                          String itemName,
                                          String category,
                                          long unitPriceCents,
                                          String description) {}

    private record WebshopCartCardData(int index,
                                       String itemId,
                                       String itemName,
                                       int quantity,
                                       long unitPriceCents,
                                       long lineTotalCents) {}

    private record WebshopAccountCardData(int index,
                                          String accountId,
                                          String accountType,
                                          String bankName,
                                          long balanceCents,
                                          boolean primary) {}

    private record WebshopShopCardData(int index,
                                       String shopId,
                                       String shopName,
                                       String shopType,
                                       int assignedPallets) {}

    private record WebshopPalletCardData(int index,
                                         String palletId,
                                         String shopId,
                                         String shopName,
                                         String dimension,
                                         int x,
                                         int y,
                                         int z,
                                         boolean full) {}

    private record WebshopOrderCardData(int index,
                                        String orderId,
                                        String status,
                                        long totalCents,
                                        long createdAtMillis,
                                        long etaAtMillis,
                                        String deliveryMode,
                                        String target,
                                        int boxCount,
                                        int attempts) {}

    private record WebshopCatalogCardHitbox(int x, int y, int width, int height, WebshopCatalogCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopCartCardHitbox(int x, int y, int width, int height, WebshopCartCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopAccountCardHitbox(int x, int y, int width, int height, WebshopAccountCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopShopCardHitbox(int x, int y, int width, int height, WebshopShopCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY <= (y + height) && mouseY >= y;
        }
    }

    private record WebshopPalletCardHitbox(int x, int y, int width, int height, WebshopPalletCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopOrderCardHitbox(int x, int y, int width, int height, WebshopOrderCardData card) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private record WebshopControlHelpHitbox(int x, int y, int width, int height, String title, String description) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= (x + width) && mouseY >= y && mouseY <= (y + height);
        }
    }

    private enum ShopInventoryFilterMode {
        ALL,
        LOW_STOCK,
        OUT_OF_STOCK
    }

    private enum ShopInventorySortMode {
        SHELF_SLOT,
        STOCK_ASC,
        STOCK_DESC,
        VELOCITY_DESC,
        NAME_ASC
    }

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
        private boolean shopLevelRoadmapOpen;
        private int shopLevelRoadmapScrollX;
        private ShopLevelRoadmapNode shopLevelRoadmapSelectedNode;

        private BankWindowState(UUID bankId) {
            this.bankId = bankId;
        }
    }

    private enum WindowMode {
        DESKTOP,
        BANK_APP,
        CREATE_BANK,
        CREATE_SHOP,
        UTILITY_APP
    }

    private enum Section {
        OVERVIEW,
        BRANDING,
        LIMITS,
        GOVERNANCE,
        STAFFING,
        LENDING,
        HOURS,
        COMPLIANCE,
        PERMISSIONS
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
        SHOP_MANAGER,
        SYSTEM_MONITOR,
        ORDER_BOARD,
        WEBSHOP
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
    private static final float HOVER_TOOLTIP_Z = 950.0F;
    private static final String SHOP_INVENTORY_SEARCH_KEY = "shop.inventory.search";
    private static final String SHOP_STOCKROOM_SEARCH_KEY = "shop.stockroom.search";
    private static final String SHOP_ORDER_PICK_SEARCH_KEY = "shop.order.pick.search";
    private static final String SHOP_PERMISSIONS_PICK_SEARCH_KEY = "shop.permissions.pick.search";
    private static final String SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY = "shop.permissions.selected.id";
    private static final String SHOP_PERMISSIONS_SELECTED_PLAYER_NAME_KEY = "shop.permissions.selected.name";
    private static final String SHOP_PERMISSIONS_ROLE_KEY = "shop.permissions.role";
    private static final String SHOP_HOURS_OPEN_KEY = "shop.hours.open";
    private static final String SHOP_HOURS_CLOSE_KEY = "shop.hours.close";
    private static final String SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY = "shop.hours.deliverer_stockroom_access";
    private static final String SHOP_LIGHTING_ENABLED_KEY = "shop.lighting.enabled";
    private static final String SHOP_LIGHTING_MAIN_MODE_KEY = "shop.lighting.main_mode";
    private static final String SHOP_LIGHTING_STOCKROOM_MODE_KEY = "shop.lighting.stock_mode";
    private static final String SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY = "shop.lighting.exclude_stockroom";
    private static final String SHOP_LIGHTING_LEVEL_KEY = "shop.lighting.level";
    private static final int[] SHOP_CASH_DENOMINATIONS = DollarBills.CASH_DENOMINATIONS_CENTS_DESC;
    private static final int NOTEPAD_MAX_CHARS = 16000;
    private static final List<String> OWNERSHIP_MODELS = List.of(
            "SOLE",
            "ROLE_BASED",
            "PERCENTAGE_SHARES",
            "FIXED_COFOUNDERS"
    );
    private static final List<String> SHOP_TYPES = List.of(
            "INDEPENDENT_RETAILER",
            "FRANCHISE",
            "CORPORATE_RETAIL_CHAIN"
    );
    private static final int MAX_SHOPS_PER_PLAYER = 3;
    private static final List<UtilityApp> DESKTOP_UTILITY_APPS = List.of(
            UtilityApp.CALCULATOR,
            UtilityApp.NOTEPAD,
            UtilityApp.FILE_EXPLORER,
            UtilityApp.PAINT,
            UtilityApp.SYSTEM_MONITOR,
            UtilityApp.ORDER_BOARD,
            UtilityApp.WEBSHOP
    );
    private static final List<Section> BANK_NAV_SECTIONS = List.of(
            Section.OVERVIEW,
            Section.BRANDING,
            Section.LIMITS,
            Section.GOVERNANCE,
            Section.STAFFING,
            Section.LENDING,
            Section.COMPLIANCE
    );
    private static final List<Section> SHOP_NAV_SECTIONS = List.of(
            Section.OVERVIEW,
            Section.BRANDING,
            Section.LIMITS,
            Section.GOVERNANCE,
            Section.STAFFING,
            Section.LENDING,
            Section.HOURS,
            Section.COMPLIANCE,
            Section.PERMISSIONS
    );

    private WindowMode activeWindow = WindowMode.DESKTOP;
    private Section activeSection = Section.OVERVIEW;

    private boolean bankWindowOpen;
    private boolean createWindowOpen;
    private boolean createShopWindowOpen;
    private UUID activeBankId;
    private final Map<UUID, BankWindowState> bankWindows = new HashMap<>();
    private final List<UUID> bankWindowOrder = new ArrayList<>();
    private final List<UtilityApp> utilityWindowOrder = new ArrayList<>();
    private UtilityApp activeUtilityApp;

    private String selectedOwnershipModel = OWNERSHIP_MODELS.get(0);
    private String selectedShopType = SHOP_TYPES.get(0);

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
    private boolean shopSettlementPickerOpen;
    private String shopSelectedSettlementAccountId = "";
    private boolean shopVaultPlanEditOpen;
    private final int[] shopVaultRequestedCounts = new int[SHOP_CASH_DENOMINATIONS.length];
    private final Map<String, DesktopEditBox> activeFormInputs = new HashMap<>();
    private final List<AccountCardHitbox> visibleAccountCards = new ArrayList<>();
    private AccountCardData selectedAccountCard;
    private boolean accountProfileOpen;
    private boolean lendingMarketOpen;
    private MarketSort marketSort = MarketSort.AMOUNT;
    private boolean marketSortDescending = true;
    private final List<MarketOfferData> marketOfferCache = new ArrayList<>();
    private final List<MarketActionHitbox> visibleMarketActions = new ArrayList<>();
    private final List<ShopEmployeeActionHitbox> visibleShopEmployeeActions = new ArrayList<>();
    private final List<ShopOwnerAccountCardHitbox> visibleShopOwnerAccountCards = new ArrayList<>();
    private final List<ShopPermissionMemberCardHitbox> visibleShopPermissionMemberCards = new ArrayList<>();
    private final List<ShopVaultAdjustHitbox> visibleShopVaultAdjustActions = new ArrayList<>();
    private final List<ShopInventoryActionHitbox> visibleShopInventoryActions = new ArrayList<>();
    private final List<ShopInventoryShelfSelectHitbox> visibleShopInventoryShelfCards = new ArrayList<>();
    private final List<ShopStockroomLocateHitbox> visibleShopStockroomLocateActions = new ArrayList<>();
    private final List<ShopOrderCardHitbox> visibleShopOrderCards = new ArrayList<>();
    private final List<ShopOrderPalletCardHitbox> visibleShopOrderPalletCards = new ArrayList<>();
    private final List<ShopOrderPickCardHitbox> visibleShopOrderPickCards = new ArrayList<>();
    private final List<ShopOperationsHelpHitbox> visibleShopOperationsHelp = new ArrayList<>();
    private final List<SectionTextLabel> visibleSectionTextLabels = new ArrayList<>();
    private String shopOrderSelectedId = "";
    private String shopOrderSelectedPalletRef = "";
    private String shopOrderSelectedItemId = "";
    private String shopOrderSelectedItemName = "";
    private boolean shopOrderUseSpecificPallet;
    private boolean shopOrderPickerOpen;
    private boolean shopOrderPalletPickerOpen;
    private ShopInventoryFilterMode shopInventoryFilterMode = ShopInventoryFilterMode.ALL;
    private ShopInventorySortMode shopInventorySortMode = ShopInventorySortMode.SHELF_SLOT;
    private String shopInventorySelectedShelfTarget = "";
    private boolean shopStockroomViewOpen;
    private boolean shopLevelRoadmapOpen;
    private int shopLevelRoadmapScrollX;
    private int shopLevelRoadmapMaxScrollX;
    private RectHitbox shopLevelRoadmapScrollbarTrackHitbox;
    private RectHitbox shopLevelRoadmapScrollbarThumbHitbox;
    private boolean shopLevelRoadmapScrollbarDragging;
    private int shopLevelRoadmapScrollbarDragOffsetX;
    private ShopLevelRoadmapNode shopLevelRoadmapSelectedNode;
    private RectHitbox shopLevelRoadmapModalCloseHitbox;
    private final List<KpiCardHitbox> visibleKpiCards = new ArrayList<>();
    private final List<ShopRoadmapNodeHitbox> visibleShopLevelRoadmapNodes = new ArrayList<>();
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
    private int shopManagerScroll;
    private int shopManagerMaxScroll;
    private int shopManagerViewportX;
    private int shopManagerViewportY;
    private int shopManagerViewportW;
    private int shopManagerViewportH;
    private int orderBoardScroll;
    private int orderBoardMaxScroll;
    private int orderBoardViewportX;
    private int orderBoardViewportY;
    private int orderBoardViewportW;
    private int orderBoardViewportH;
    private String orderBoardSelectedOrderId = "";
    private final List<OrderBoardCardHitbox> visibleOrderBoardCards = new ArrayList<>();
    private final List<KpiCardHitbox> visibleOrderBoardKpiCards = new ArrayList<>();
    private int webshopScroll;
    private int webshopMaxScroll;
    private int webshopViewportX;
    private int webshopViewportY;
    private int webshopViewportW;
    private int webshopViewportH;
    private String webshopSelectedCatalogItemId = "";
    private String webshopSelectedCatalogItemName = "";
    private String webshopSelectedCartItemId = "";
    private String webshopSelectedOrderId = "";
    private String webshopSelectedAccountId = "";
    private String webshopSelectedShopId = "";
    private String webshopSelectedPalletId = "";
    private final List<WebshopCatalogCardHitbox> visibleWebshopCatalogCards = new ArrayList<>();
    private final List<WebshopCartCardHitbox> visibleWebshopCartCards = new ArrayList<>();
    private final List<WebshopAccountCardHitbox> visibleWebshopAccountCards = new ArrayList<>();
    private final List<WebshopShopCardHitbox> visibleWebshopShopCards = new ArrayList<>();
    private final List<WebshopPalletCardHitbox> visibleWebshopPalletCards = new ArrayList<>();
    private final List<WebshopOrderCardHitbox> visibleWebshopOrderCards = new ArrayList<>();
    private final List<KpiCardHitbox> visibleWebshopKpiCards = new ArrayList<>();
    private final List<WebshopControlHelpHitbox> visibleWebshopControlHelp = new ArrayList<>();
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
    private long lastShopPermissionsRefreshAtMillis;
    private final Map<ScrollbarTarget, ScrollbarHitbox> visibleScrollbars = new EnumMap<>(ScrollbarTarget.class);
    private ScrollbarTarget activeScrollbarDrag;
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
    public void tick() {
        super.tick();
        applyForcedGuiScale();
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) {
            return;
        }
        if (activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.PERMISSIONS) {
            long now = System.currentTimeMillis();
            if (now - lastShopPermissionsRefreshAtMillis >= 1000L) {
                lastShopPermissionsRefreshAtMillis = now;
                sendShopDesktopAction("SHOP_PERMISSIONS_REPORT", "");
            }
        }
        int scaledW = mc.getWindow().getGuiScaledWidth();
        int scaledH = mc.getWindow().getGuiScaledHeight();
        if (scaledW > 0 && scaledH > 0 && (this.width != scaledW || this.height != scaledH)) {
            this.resize(mc, scaledW, scaledH);
        }
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
        this.visibleKpiCards.clear();
        this.visibleSystemAppCards.clear();
        this.visibleShopOrderCards.clear();
        this.visibleShopOrderPalletCards.clear();
        this.visibleShopOrderPickCards.clear();
        this.visibleShopOperationsHelp.clear();
        this.visibleSectionTextLabels.clear();
        this.visibleOrderBoardCards.clear();
        this.visibleOrderBoardKpiCards.clear();
        this.visibleWebshopControlHelp.clear();
        this.visibleShopLevelRoadmapNodes.clear();
        this.marketConfirmAcceptHitbox = null;
        this.marketConfirmCancelHitbox = null;
        this.accountProfileCopyIdHitbox = null;
        this.shopLevelRoadmapScrollbarTrackHitbox = null;
        this.shopLevelRoadmapScrollbarThumbHitbox = null;
        this.shopLevelRoadmapModalCloseHitbox = null;
        this.shopLevelRoadmapScrollbarDragging = false;
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
        } else if (activeWindow == WindowMode.CREATE_SHOP) {
            initCreateShopWidgets();
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
        createShopWindowOpen = false;
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
        selectedOwnershipModel = OWNERSHIP_MODELS.get(0);
        selectedExplorerFileName = "";
        explorerFilesScroll = 0;
        systemMonitorScroll = 0;
        systemHideAppsScroll = 0;
        paintControlsScroll = 0;
        shopManagerScroll = 0;
        shopManagerMaxScroll = 0;
        shopManagerViewportX = 0;
        shopManagerViewportY = 0;
        shopManagerViewportW = 0;
        shopManagerViewportH = 0;
        shopLevelRoadmapOpen = false;
        shopLevelRoadmapScrollX = 0;
        shopLevelRoadmapMaxScrollX = 0;
        shopLevelRoadmapScrollbarDragging = false;
        shopLevelRoadmapScrollbarDragOffsetX = 0;
        shopLevelRoadmapSelectedNode = null;
        shopLevelRoadmapScrollbarTrackHitbox = null;
        shopLevelRoadmapScrollbarThumbHitbox = null;
        shopLevelRoadmapModalCloseHitbox = null;
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
            if (ClientOwnerPcData.isAppHidden(appIdFor(app))) {
                continue;
            }
            int col = idx % columns;
            int row = idx / columns;
            int x = contentLeft + (col * (buttonWidth + 12));
            int y = contentTop + (row * (buttonHeight + 10));

            String label;
            if (app.isShopApp()) {
                label = "SHOP | " + app.bankName();
            } else {
                String icon = app.owner() ? "BANK" : "ROLE";
                label = icon + " | " + app.bankName();
                if (!app.owner() && app.roleLabel() != null && !app.roleLabel().isBlank()) {
                    label = label + " [" + app.roleLabel() + "]";
                }
            }

            addPcButton(
                    x,
                    y,
                    buttonWidth,
                    buttonHeight,
                    fitToWidth(label, buttonWidth - 10),
                    btn -> openOrActivateAppWindow(app)
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
        int ownedShops = countOwnedShopApps();
        boolean canCreateBank = ClientOwnerPcData.getOwnedCount() < ClientOwnerPcData.getMaxBanks();
        boolean canCreateShop = ownedShops < MAX_SHOPS_PER_PLAYER;
        int createGap = 8;
        int twoButtonW = Math.max(132, (Math.min(620, contentWidth) - createGap) / 2);
        if (contentWidth >= 360) {
            DesktopButton createBankButton = addPcButton(
                    contentLeft,
                    createY,
                    twoButtonW,
                    28,
                    canCreateBank
                            ? "Create Bank (" + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks() + ")"
                            : "Bank Limit Reached",
                    btn -> {
                        createWindowOpen = true;
                        createShopWindowOpen = false;
                        activeWindow = WindowMode.CREATE_BANK;
                        rebuildWidgets();
                    }
            );
            createBankButton.active = canCreateBank;

            DesktopButton createShopButton = addPcButton(
                    contentLeft + twoButtonW + createGap,
                    createY,
                    twoButtonW,
                    28,
                    canCreateShop
                            ? "Create Shop (" + ownedShops + "/" + MAX_SHOPS_PER_PLAYER + ")"
                            : "Shop Limit Reached",
                    btn -> {
                        createShopWindowOpen = true;
                        createWindowOpen = false;
                        activeWindow = WindowMode.CREATE_SHOP;
                        rebuildWidgets();
                    }
            );
            createShopButton.active = canCreateShop;
        } else {
            DesktopButton createBankButton = addPcButton(
                    contentLeft,
                    createY,
                    Math.min(300, contentWidth),
                    28,
                    canCreateBank
                            ? "Create Bank (" + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks() + ")"
                            : "Bank Limit Reached",
                    btn -> {
                        createWindowOpen = true;
                        createShopWindowOpen = false;
                        activeWindow = WindowMode.CREATE_BANK;
                        rebuildWidgets();
                    }
            );
            createBankButton.active = canCreateBank;

            DesktopButton createShopButton = addPcButton(
                    contentLeft,
                    createY + 32,
                    Math.min(300, contentWidth),
                    28,
                    canCreateShop
                            ? "Create Shop (" + ownedShops + "/" + MAX_SHOPS_PER_PLAYER + ")"
                            : "Shop Limit Reached",
                    btn -> {
                        createShopWindowOpen = true;
                        createWindowOpen = false;
                        activeWindow = WindowMode.CREATE_SHOP;
                        rebuildWidgets();
                    }
            );
            createShopButton.active = canCreateShop;
        }
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

        boolean shopMode = isActiveShopApp();
        List<Section> navSections = shopMode ? SHOP_NAV_SECTIONS : BANK_NAV_SECTIONS;
        if (!navSections.contains(activeSection)) {
            activeSection = Section.OVERVIEW;
        }
        int sectionCount = navSections.size();
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
        for (Section section : navSections) {
            String label = shopMode
                    ? shopSectionLabel(section)
                    : section.name().substring(0, 1) + section.name().substring(1).toLowerCase(Locale.ROOT);
            int buttonY = sectionY + (i * (sectionH + sectionGap));
            DesktopButton button = addPcButton(
                    sectionX,
                    buttonY,
                    sectionW,
                    sectionH,
                    label,
                    btn -> {
                        activeSection = section;
                        if (isActiveShopApp() && activeSection == Section.PERMISSIONS) {
                            lastShopPermissionsRefreshAtMillis = 0L;
                        }
                        overviewDetailOpen = false;
                        overviewDetailAction = "SHOW_INFO";
                        selectedAccountCard = null;
                        accountProfileOpen = false;
                        lendingMarketOpen = false;
                        pendingMarketAccept = null;
                        marketOfferCache.clear();
                        refreshMarketAfterNextResponse = false;
                        shopSettlementPickerOpen = false;
                        shopSelectedSettlementAccountId = "";
                        shopVaultPlanEditOpen = false;
                        shopStockroomViewOpen = false;
                        shopOrderSelectedId = "";
                        shopOrderSelectedPalletRef = "";
                        shopOrderSelectedItemId = "";
                        shopOrderSelectedItemName = "";
                        shopOrderUseSpecificPallet = false;
                        shopOrderPickerOpen = false;
                        shopOrderPalletPickerOpen = false;
                        shopLevelRoadmapOpen = false;
                        shopLevelRoadmapScrollX = 0;
                        shopLevelRoadmapMaxScrollX = 0;
                        shopLevelRoadmapScrollbarDragging = false;
                        shopLevelRoadmapSelectedNode = null;
                        shopLevelRoadmapScrollbarTrackHitbox = null;
                        shopLevelRoadmapScrollbarThumbHitbox = null;
                        shopLevelRoadmapModalCloseHitbox = null;
                        java.util.Arrays.fill(shopVaultRequestedCounts, 0);
                        ClientOwnerPcData.clearActionOutput();
                        outputScroll = 0;
                        sectionScroll = 0;
                        if (isActiveShopApp()) {
                            sendShopDesktopAction(defaultShopActionForSection(section), "");
                        }
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
                btn -> {
                    if (isActiveShopApp()) {
                        if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
                            sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "");
                        } else {
                            sendShopDesktopAction(defaultShopActionForSection(activeSection), "");
                        }
                    } else {
                        requestBankData(activeBankId);
                    }
                }
        ).setLabelOffset(4, 1);

        int areaX = left + 170;
        int areaY = top + 60;
        int areaWidth = Math.max(180, right - areaX - 10);
        sectionViewportX = areaX + 10;
        sectionViewportY = areaY + 2;
        sectionViewportW = Math.max(120, areaWidth - 20);
        int availableSectionHeight = Math.max(80, bottom - sectionViewportY - 12);
        int minOutputHeight = Math.max(90, this.height / 5);
        if (isActiveShopApp() && activeSection == Section.LIMITS) {
            // Keep more room for inventory output in shop view.
            minOutputHeight = Math.max(minOutputHeight, Math.max(150, this.height / 3));
            if (shopStockroomViewOpen) {
                minOutputHeight = Math.max(minOutputHeight, Math.max(220, this.height / 2));
            }
        } else if (isActiveShopApp() && activeSection == Section.GOVERNANCE && shopOrderPickerOpen) {
            // Picker mode is output-heavy; reserve more height for cards.
            minOutputHeight = Math.max(minOutputHeight, Math.max(220, this.height / 2));
        }
        int maxSectionHeight = Math.max(60, availableSectionHeight - minOutputHeight - 8);
        int targetSectionHeight = Math.min(220, Math.max(110, maxSectionHeight));
        if (isActiveShopApp() && activeSection == Section.LIMITS) {
            // Give inventory-heavy views a taller output panel while preserving control spacing.
            targetSectionHeight = Math.max(84, targetSectionHeight - 56);
            if (shopStockroomViewOpen) {
                targetSectionHeight = Math.max(60, Math.min(targetSectionHeight, 68));
            }
        } else if (isActiveShopApp() && activeSection == Section.GOVERNANCE && shopOrderPickerOpen) {
            // Keep operations controls compact in picker mode so output can stay large.
            targetSectionHeight = Math.max(60, Math.min(targetSectionHeight, 84));
        }
        sectionViewportH = Math.max(60, Math.min(targetSectionHeight, maxSectionHeight));
        sectionScroll = Math.max(0, Math.min(sectionScroll, sectionMaxScroll));

        initSectionWidgets(areaX, areaY, areaWidth);

        if (isActiveShopApp() && (activeSection == Section.OVERVIEW
                || (activeSection == Section.LENDING && shopSettlementPickerOpen))) {
            int contentHeight = Math.max(36, sectionControlsBottomY - sectionViewportY + 10);
            int desiredSectionHeight = Math.max(56, Math.min(maxSectionHeight, contentHeight));
            if (desiredSectionHeight != sectionViewportH) {
                sectionViewportH = desiredSectionHeight;
                sectionScroll = Math.max(0, Math.min(sectionScroll, sectionMaxScroll));
                initSectionWidgets(areaX, areaY, areaWidth);
            }
        }
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

    private void initCreateShopWidgets() {
        int width = Math.min(700, this.width - (PAD * 2) - 80);
        int left = (this.width - width) / 2;
        int frameTop = PAD + TOPBAR_HEIGHT + 20;
        int frameBottom = this.height - PAD - TASKBAR_HEIGHT - 20;

        int top = frameTop + 34;
        DesktopEditBox name = addFormInput("create.shop.name", left, top, width, "Shop name");

        addPcButton(
                left + width - 92,
                frameTop + 4,
                84,
                20,
                "Close App",
                btn -> closeCreateShopWindow()
        ).setLabelOffset(4, 1);

        int optionY = top + 94;
        int optionW = width;
        int optionH = 24;
        int optionGap = 6;

        for (int i = 0; i < SHOP_TYPES.size(); i++) {
            String shopType = SHOP_TYPES.get(i);
            int y = optionY + (i * (optionH + optionGap));
            boolean selected = shopType.equalsIgnoreCase(selectedShopType);
            DesktopButton option = addPcButton(
                    left,
                    y,
                    optionW,
                    optionH,
                    (selected ? "Selected: " : "") + prettifyShopType(shopType),
                    btn -> {
                        selectedShopType = shopType;
                        rebuildWidgets();
                    }
            );
            option.active = !selected;
        }

        int actionY = Math.min(optionY + ((optionH + optionGap) * SHOP_TYPES.size()) + 12, frameBottom - 34);
        if (width >= 680) {
            addPcButton(
                    left,
                    actionY,
                    220,
                    26,
                    "Create Shop",
                    btn -> sendDesktopAction("SHOP_CREATE", textOrBlank(name), selectedShopType)
            );

            addPcButton(
                    left + 230,
                    actionY,
                    140,
                    26,
                    "Back",
                    btn -> {
                        activeWindow = WindowMode.DESKTOP;
                        createShopWindowOpen = false;
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
                    "Create Shop",
                    btn -> sendDesktopAction("SHOP_CREATE", textOrBlank(name), selectedShopType)
            );
            addPcButton(
                    left + splitW + 8,
                    actionY,
                    splitW,
                    26,
                    "Back",
                    btn -> {
                        activeWindow = WindowMode.DESKTOP;
                        createShopWindowOpen = false;
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
                    "Create Shop",
                    btn -> sendDesktopAction("SHOP_CREATE", textOrBlank(name), selectedShopType)
            );
            addPcButton(
                    left,
                    actionY + 32,
                    width,
                    26,
                    "Back",
                    btn -> {
                        activeWindow = WindowMode.DESKTOP;
                        createShopWindowOpen = false;
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

        createShopWindowOpen = true;
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
                case SHOP_MANAGER -> initShopManagerWidgets();
                case SYSTEM_MONITOR -> initSystemMonitorWidgets();
                case ORDER_BOARD -> initOrderBoardWidgets();
                case WEBSHOP -> initWebshopWidgets();
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

    private void initShopManagerWidgets() {
        int panelX = utilityContentX + 8;
        int panelY = utilityContentY + 8;
        int panelW = Math.max(140, utilityContentW - 16);
        int gap = 6;

        addFormInput("shop.name", panelX, panelY, panelW, "Shop name");
        addFormInput("shop.cashier", panelX, panelY + 26, panelW, "Cashier UUID or index");
        int y = panelY + 54;

        int cols = panelW >= 560 ? 3 : panelW >= 380 ? 2 : 1;
        int buttonW = Math.max(100, (panelW - (gap * (cols - 1))) / cols);
        int buttonH = 22;
        String[] labels = {
                "Create Shop",
                "Shop Overview",
                "Claim Tool (Plot)",
                "Claim Tool (Stockroom)",
                "Claim Tool (Pallets)",
                "Set Checkout Terminal",
                "Scan Shelves",
                "Restock Shelves",
                "Scan Cashiers",
                "Link Cashier Terminal"
        };
        String[] actions = {
                "SHOP_CREATE",
                "SHOP_OVERVIEW",
                "SHOP_CLAIM_TOOL_PLOT",
                "SHOP_CLAIM_TOOL_STOCKROOM",
                "SHOP_CLAIM_TOOL_PALLETS",
                "SHOP_SET_CHECKOUT_TERMINAL",
                "SHOP_SCAN",
                "SHOP_RESTOCK",
                "SHOP_SCAN_CASHIERS",
                "SHOP_LINK_CASHIER_TERMINAL"
        };

        for (int i = 0; i < labels.length; i++) {
            int row = i / cols;
            int col = i % cols;
            int bx = panelX + (col * (buttonW + gap));
            int by = y + (row * (buttonH + gap));
            String action = actions[i];
            addPcButton(
                    bx,
                    by,
                    buttonW,
                    buttonH,
                    labels[i],
                    btn -> {
                        sendDesktopAction(
                                action,
                                "SHOP_CREATE".equals(action)
                                        ? formValues.getOrDefault("shop.name", "").trim()
                                        : ("SHOP_LINK_CASHIER_TERMINAL".equals(action)
                                        ? formValues.getOrDefault("shop.cashier", "").trim()
                                        : ""),
                                ""
                        );
                        if ("SHOP_CLAIM_TOOL_PLOT".equals(action)
                                || "SHOP_CLAIM_TOOL_STOCKROOM".equals(action)
                                || "SHOP_CLAIM_TOOL_PALLETS".equals(action)) {
                            closeEntirePcUi();
                        }
                    }
            ).setLabelOffset(4, 1);
        }

        int rows = (labels.length + cols - 1) / cols;
        int listY = y + (rows * (buttonH + gap)) + 8;
        shopManagerViewportX = panelX;
        shopManagerViewportY = listY;
        shopManagerViewportW = panelW;
        shopManagerViewportH = Math.max(64, (utilityContentY + utilityContentH) - listY - 4);

        List<String> rawLines = ClientOwnerPcData.getActionOutputLines();
        ShopDashboardSnapshot snapshot = parseShopDashboardSnapshot(rawLines);
        if (snapshot != null) {
            // Shop Overview output includes KPI/chart payload; scroll by pixel height in dashboard mode.
            int dashboardContentHeight = getShopDashboardContentHeight(panelW, shopManagerViewportH, Section.OVERVIEW);
            shopManagerMaxScroll = Math.max(0, dashboardContentHeight - shopManagerViewportH);
        } else {
            List<String> lines = getWrappedShopManagerLines(panelW);
            int visible = Math.max(1, (shopManagerViewportH - 8) / LINE_HEIGHT);
            shopManagerMaxScroll = Math.max(0, lines.size() - visible);
        }
        shopManagerScroll = Math.max(0, Math.min(shopManagerScroll, shopManagerMaxScroll));
    }

    private void initOrderBoardWidgets() {
        int panelX = utilityContentX + 8;
        int panelY = utilityContentY + 8;
        int panelW = Math.max(140, utilityContentW - 16);
        int gap = 6;

        int buttonW = panelW < 420 ? panelW : Math.max(100, (panelW - (gap * 2)) / 3);
        int buttonH = 22;
        if (panelW < 420) {
            addPcButton(panelX, panelY, buttonW, buttonH, "Refresh Orders",
                    btn -> sendDesktopAction("ORDER_BOARD_REPORT", "", "")).setLabelOffset(4, 1);
            panelY += buttonH + gap;
            DesktopButton accept = addPcButton(panelX, panelY, buttonW, buttonH, "Accept Selected",
                    btn -> {
                        if (orderBoardSelectedOrderId == null || orderBoardSelectedOrderId.isBlank()) {
                            ClientOwnerPcData.setToast(false, "Select an order first.");
                            return;
                        }
                        sendDesktopAction("ORDER_BOARD_ACCEPT", orderBoardSelectedOrderId, "");
                    }).setLabelOffset(4, 1);
            panelY += buttonH + gap;
            DesktopButton cancel = addPcButton(panelX, panelY, buttonW, buttonH, "Cancel Mine",
                    btn -> {
                        if (orderBoardSelectedOrderId == null || orderBoardSelectedOrderId.isBlank()) {
                            ClientOwnerPcData.setToast(false, "Select an order first.");
                            return;
                        }
                        sendDesktopAction("ORDER_BOARD_CANCEL", orderBoardSelectedOrderId, "");
                    }).setLabelOffset(4, 1);
            accept.active = orderBoardSelectedOrderId != null && !orderBoardSelectedOrderId.isBlank();
            cancel.active = orderBoardSelectedOrderId != null && !orderBoardSelectedOrderId.isBlank();
            panelY += buttonH + gap;
        } else {
            addPcButton(panelX, panelY, buttonW, buttonH, "Refresh Orders",
                    btn -> sendDesktopAction("ORDER_BOARD_REPORT", "", "")).setLabelOffset(4, 1);
            DesktopButton accept = addPcButton(panelX + buttonW + gap, panelY, buttonW, buttonH, "Accept Selected",
                    btn -> {
                        if (orderBoardSelectedOrderId == null || orderBoardSelectedOrderId.isBlank()) {
                            ClientOwnerPcData.setToast(false, "Select an order first.");
                            return;
                        }
                        sendDesktopAction("ORDER_BOARD_ACCEPT", orderBoardSelectedOrderId, "");
                    }).setLabelOffset(4, 1);
            DesktopButton cancel = addPcButton(panelX + ((buttonW + gap) * 2), panelY, buttonW, buttonH, "Cancel Mine",
                    btn -> {
                        if (orderBoardSelectedOrderId == null || orderBoardSelectedOrderId.isBlank()) {
                            ClientOwnerPcData.setToast(false, "Select an order first.");
                            return;
                        }
                        sendDesktopAction("ORDER_BOARD_CANCEL", orderBoardSelectedOrderId, "");
                    }).setLabelOffset(4, 1);
            accept.active = orderBoardSelectedOrderId != null && !orderBoardSelectedOrderId.isBlank();
            cancel.active = orderBoardSelectedOrderId != null && !orderBoardSelectedOrderId.isBlank();
            panelY += buttonH + gap;
        }

        String selected = orderBoardSelectedOrderId == null || orderBoardSelectedOrderId.isBlank()
                ? "Selected: none"
                : "Selected: " + fitToWidth(orderBoardSelectedOrderId, Math.max(24, panelW - 22));
        DesktopButton selectedButton = addPcButton(panelX, panelY, panelW, 22, selected, btn -> {
        }).setLabelOffset(4, 1);
        selectedButton.active = false;
        panelY += 28;

        orderBoardViewportX = panelX;
        orderBoardViewportY = panelY;
        orderBoardViewportW = panelW;
        orderBoardViewportH = Math.max(64, (utilityContentY + utilityContentH) - panelY - 4);

        int contentHeight = getOrderBoardCardsContentHeight(orderBoardViewportW, orderBoardViewportH, parseOrderBoardCards(ClientOwnerPcData.getActionOutputLines()));
        orderBoardMaxScroll = Math.max(0, contentHeight - orderBoardViewportH);
        orderBoardScroll = Math.max(0, Math.min(orderBoardScroll, orderBoardMaxScroll));
    }

    private void initWebshopWidgets() {
        // Webshop input strip + action bar; heavy data rendering stays in drawWebshopApp.
        visibleWebshopControlHelp.clear();
        int panelX = utilityContentX + 8;
        int panelY = utilityContentY + 8;
        int panelW = Math.max(160, utilityContentW - 16);
        int gap = 6;
        int buttonH = 22;
        int cols = panelW >= 620 ? 3 : (panelW >= 420 ? 2 : 1);
        int buttonW = Math.max(108, (panelW - (gap * (cols - 1))) / cols);

        List<String> output = ClientOwnerPcData.getActionOutputLines();
        WebshopSummary summary = parseWebshopSummary(output);
        if (summary != null) {
            if (webshopSelectedAccountId.isBlank() && summary.selectedAccountId() != null) {
                webshopSelectedAccountId = summary.selectedAccountId().trim();
            }
            if (webshopSelectedShopId.isBlank() && summary.selectedShopId() != null) {
                webshopSelectedShopId = summary.selectedShopId().trim();
            }
            if (webshopSelectedPalletId.isBlank() && summary.selectedPalletId() != null) {
                webshopSelectedPalletId = summary.selectedPalletId().trim();
            }
        }

        if (formValues.getOrDefault("webshop.qty", "").isBlank()) {
            formValues.put("webshop.qty", "1");
        }
        DesktopEditBox qtyInput = addFormInput("webshop.qty", panelX, panelY, Math.max(90, Math.min(120, panelW / 4)), "Qty");
        registerWebshopControlHelp(
                qtyInput,
                "Quantity",
                "Units used by Add Selected and Set Cart Qty. Values below 1 default to 1."
        );

        int rightButtonsStart = panelX + Math.max(98, Math.min(130, panelW / 4)) + gap;
        int rightButtonsW = Math.max(80, panelW - (rightButtonsStart - panelX));
        int rightCols = rightButtonsW >= 520 ? 3 : (rightButtonsW >= 340 ? 2 : 1);
        int rightButtonW = Math.max(100, (rightButtonsW - (gap * (rightCols - 1))) / rightCols);

        int buttonY = panelY;
        DesktopButton refresh = addPcButton(
                rightButtonsStart,
                buttonY,
                rightButtonW,
                buttonH,
                "Refresh",
                btn -> sendDesktopAction("SHOP_WEBSHOP_REPORT", "", "")
        ).setLabelOffset(4, 1);
        refresh.active = true;
        registerWebshopControlHelp(
                refresh,
                "Refresh",
                "Reload catalog, cart, accounts, shops, pallets, and order status from the server."
        );
        int col = 1;
        int row = 0;

        DesktopButton checkout = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Checkout",
                btn -> sendDesktopAction("SHOP_WEBSHOP_CHECKOUT", "", "")
        ).setLabelOffset(4, 1);
        registerWebshopControlHelp(
                checkout,
                "Checkout",
                "Reserve funds from the selected account and create a webshop delivery order."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton clearCart = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Clear Cart",
                btn -> sendDesktopAction("SHOP_WEBSHOP_CLEAR_CART", "", "")
        ).setLabelOffset(4, 1);
        registerWebshopControlHelp(
                clearCart,
                "Clear Cart",
                "Remove all cart lines for this session."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton addSelected = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Add Selected",
                btn -> {
                    if (webshopSelectedCatalogItemId == null || webshopSelectedCatalogItemId.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select a catalog card first.");
                        return;
                    }
                    sendDesktopAction("SHOP_WEBSHOP_ADD",
                            webshopSelectedCatalogItemId + "|" + formValues.getOrDefault("webshop.qty", "1").trim(),
                            "");
                }
        ).setLabelOffset(4, 1);
        addSelected.active = webshopSelectedCatalogItemId != null && !webshopSelectedCatalogItemId.isBlank();
        registerWebshopControlHelp(
                addSelected,
                "Add Selected",
                "Add the selected catalog card item to cart using the quantity input."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton setQty = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Set Cart Qty",
                btn -> {
                    if (webshopSelectedCartItemId == null || webshopSelectedCartItemId.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select a cart card first.");
                        return;
                    }
                    sendDesktopAction("SHOP_WEBSHOP_SET_QTY",
                            webshopSelectedCartItemId + "|" + formValues.getOrDefault("webshop.qty", "1").trim(),
                            "");
                }
        ).setLabelOffset(4, 1);
        setQty.active = webshopSelectedCartItemId != null && !webshopSelectedCartItemId.isBlank();
        registerWebshopControlHelp(
                setQty,
                "Set Cart Qty",
                "Update quantity of the selected cart item using the quantity input."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton removeSelected = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Remove Item",
                btn -> {
                    if (webshopSelectedCartItemId == null || webshopSelectedCartItemId.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select a cart card first.");
                        return;
                    }
                    sendDesktopAction("SHOP_WEBSHOP_REMOVE", webshopSelectedCartItemId, "");
                }
        ).setLabelOffset(4, 1);
        removeSelected.active = webshopSelectedCartItemId != null && !webshopSelectedCartItemId.isBlank();
        registerWebshopControlHelp(
                removeSelected,
                "Remove Item",
                "Remove the selected cart item line entirely."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        String modeLabel = "Mode: Random Pallet";
        if (summary != null && summary.deliveryMode() != null && !summary.deliveryMode().isBlank()) {
            modeLabel = "Mode: " + prettyWebshopMode(summary.deliveryMode());
        }
        DesktopButton modeButton = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                fitToWidth(modeLabel, rightButtonW - 10),
                btn -> {
                    String current = summary == null ? "PALLET_RANDOM" : summary.deliveryMode();
                    String next = nextWebshopMode(current);
                    sendDesktopAction("SHOP_WEBSHOP_MODE", next, "");
                }
        ).setLabelOffset(4, 1);
        registerWebshopControlHelp(
                modeButton,
                "Delivery Mode",
                "Toggle between random assigned pallet and specific selected pallet delivery."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton expedite = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                summary != null && summary.expedite() ? "Expedite: ON" : "Expedite: OFF",
                btn -> sendDesktopAction("SHOP_WEBSHOP_EXPEDITE",
                        summary != null && summary.expedite() ? "0" : "1",
                        "")
        ).setLabelOffset(4, 1);
        registerWebshopControlHelp(
                expedite,
                "Expedite",
                "Apply delivery surcharge for faster webshop order ETA."
        );

        if (col >= rightCols) {
            col = 0;
            row++;
        }
        DesktopButton cancelSelectedOrder = addPcButton(
                rightButtonsStart + ((rightButtonW + gap) * col++),
                buttonY + (row * (buttonH + gap)),
                rightButtonW,
                buttonH,
                "Cancel Selected Order",
                btn -> {
                    if (webshopSelectedOrderId == null || webshopSelectedOrderId.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                        return;
                    }
                    sendDesktopAction("SHOP_WEBSHOP_CANCEL_ORDER", webshopSelectedOrderId, "");
                }
        ).setLabelOffset(4, 1);
        cancelSelectedOrder.active = webshopSelectedOrderId != null && !webshopSelectedOrderId.isBlank();
        registerWebshopControlHelp(
                cancelSelectedOrder,
                "Cancel Selected Order",
                "Cancel the selected queued order and refund based on cancellation rules."
        );

        int rowsUsed = Math.max(1, row + 1);
        int labelsY = panelY + (rowsUsed * (buttonH + gap)) + 4;
        int labelCols = cols;
        int labelW = Math.max(100, (panelW - (gap * (labelCols - 1))) / labelCols);
        String[] selectionLabels = {
                "Catalog: " + (webshopSelectedCatalogItemName == null || webshopSelectedCatalogItemName.isBlank()
                        ? "none"
                        : fitToWidth(webshopSelectedCatalogItemName, Math.max(20, labelW - 16))),
                "Cart: " + (webshopSelectedCartItemId == null || webshopSelectedCartItemId.isBlank()
                        ? "none"
                        : shortUuid(webshopSelectedCartItemId)),
                "Account: " + (webshopSelectedAccountId == null || webshopSelectedAccountId.isBlank()
                        ? "none"
                        : shortUuid(webshopSelectedAccountId)),
                "Shop: " + (webshopSelectedShopId == null || webshopSelectedShopId.isBlank()
                        ? "none"
                        : shortUuid(webshopSelectedShopId)),
                "Pallet: " + (webshopSelectedPalletId == null || webshopSelectedPalletId.isBlank()
                        ? "none"
                        : shortUuid(webshopSelectedPalletId)),
                "Order: " + (webshopSelectedOrderId == null || webshopSelectedOrderId.isBlank()
                        ? "none"
                        : shortUuid(webshopSelectedOrderId))
        };
        for (int i = 0; i < selectionLabels.length; i++) {
            int lx = panelX + ((i % labelCols) * (labelW + gap));
            int ly = labelsY + ((i / labelCols) * (buttonH + gap));
            DesktopButton info = addPcButton(lx, ly, labelW, buttonH, selectionLabels[i], btn -> {
            }).setLabelOffset(4, 1);
            info.active = false;
            String title = switch (i) {
                case 0 -> "Selected Catalog";
                case 1 -> "Selected Cart Item";
                case 2 -> "Selected Account";
                case 3 -> "Selected Shop";
                case 4 -> "Selected Pallet";
                default -> "Selected Order";
            };
            String description = switch (i) {
                case 0 -> "Current catalog item target for Add Selected.";
                case 1 -> "Current cart item target for Set Cart Qty and Remove Item.";
                case 2 -> "Account used for checkout payment reservation.";
                case 3 -> "Shop targeted for delivery pallet routing.";
                case 4 -> "Specific delivery pallet target when mode is set to Specific Pallet.";
                default -> "Queued order target for cancellation.";
            };
            registerWebshopControlHelp(info, title, description);
        }
        int labelRows = (selectionLabels.length + labelCols - 1) / labelCols;
        int panelTop = labelsY + (labelRows * (buttonH + gap)) + 4;

        webshopViewportX = panelX;
        webshopViewportY = panelTop;
        webshopViewportW = panelW;
        webshopViewportH = Math.max(80, (utilityContentY + utilityContentH) - panelTop - 4);

        int contentHeight = getWebshopContentHeight(webshopViewportW, webshopViewportH, output);
        webshopMaxScroll = Math.max(0, contentHeight - webshopViewportH);
        webshopScroll = Math.max(0, Math.min(webshopScroll, webshopMaxScroll));
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
        int totalWindowTabs = bankWindowOrder.size()
                + (createWindowOpen ? 1 : 0)
                + (createShopWindowOpen ? 1 : 0)
                + utilityWindowOrder.size();
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
                            activateBankWindow(bankId, isBankApp(bankId));
                            activeWindow = WindowMode.BANK_APP;
                            rebuildWidgets();
                        }
                );
                tab.active = !isActiveBankTab;
            }
            tabX += tabWidth + gap;
        }

        if (createShopWindowOpen) {
            int clippedX = Math.max(tabX, viewportX);
            int clippedRight = Math.min(tabX + tabWidth, viewportX + viewportW);
            int clippedW = clippedRight - clippedX;
            if (clippedW > 6) {
                DesktopButton createShopTab = addPcButton(
                        clippedX,
                        barY,
                        clippedW,
                        20,
                        fitToWidth("Create Shop", Math.max(10, clippedW - 10)),
                        btn -> {
                            if (activeWindow == WindowMode.BANK_APP) {
                                saveActiveBankWindowState();
                            }
                            activeWindow = WindowMode.CREATE_SHOP;
                            rebuildWidgets();
                        }
                );
                createShopTab.active = activeWindow != WindowMode.CREATE_SHOP;
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

        if (isActiveShopApp()) {
            initShopSectionWidgets(innerX, y, innerWidth);
            int contentHeight = Math.max(0, sectionControlsBottomY - sectionViewportY + 4);
            int viewportHeight = Math.max(40, sectionViewportH);
            sectionMaxScroll = Math.max(0, contentHeight - viewportHeight);
            return;
        }

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

    private void initShopSectionWidgets(int innerX, int y, int innerWidth) {
        int gap = 8;
        int curY = y + 8;
        switch (activeSection) {
            case OVERVIEW -> {
                if (shopLevelRoadmapOpen) {
                    if (innerWidth < 360) {
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Back To Dashboard", btn -> {
                            shopLevelRoadmapOpen = false;
                            shopLevelRoadmapSelectedNode = null;
                            shopLevelRoadmapScrollX = 0;
                            shopLevelRoadmapMaxScrollX = 0;
                            shopLevelRoadmapScrollbarDragging = false;
                            sendShopDesktopAction("SHOP_OVERVIEW", "");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Level Roadmap",
                                btn -> sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "")).setLabelOffset(6, 1);
                    } else {
                        int half = (innerWidth - gap) / 2;
                        addSectionPcButton(innerX, curY, half, 24, "Back To Dashboard", btn -> {
                            shopLevelRoadmapOpen = false;
                            shopLevelRoadmapSelectedNode = null;
                            shopLevelRoadmapScrollX = 0;
                            shopLevelRoadmapMaxScrollX = 0;
                            shopLevelRoadmapScrollbarDragging = false;
                            sendShopDesktopAction("SHOP_OVERVIEW", "");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        addSectionPcButton(innerX + half + gap, curY, half, 24, "Refresh Level Roadmap",
                                btn -> sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "")).setLabelOffset(6, 1);
                    }
                } else if (innerWidth < 360) {
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Dashboard (All KPIs)",
                            btn -> sendShopDesktopAction("SHOP_OVERVIEW", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Open Level Roadmap", btn -> {
                        shopLevelRoadmapOpen = true;
                        shopLevelRoadmapSelectedNode = null;
                        shopLevelRoadmapScrollX = 0;
                        shopLevelRoadmapMaxScrollX = 0;
                        shopLevelRoadmapScrollbarDragging = false;
                        outputScroll = 0;
                        sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "");
                        rebuildWidgets();
                    }).setLabelOffset(6, 1);
                } else {
                    int half = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, half, 24, "Refresh Dashboard (All KPIs)",
                            btn -> sendShopDesktopAction("SHOP_OVERVIEW", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Open Level Roadmap", btn -> {
                        shopLevelRoadmapOpen = true;
                        shopLevelRoadmapSelectedNode = null;
                        shopLevelRoadmapScrollX = 0;
                        shopLevelRoadmapMaxScrollX = 0;
                        shopLevelRoadmapScrollbarDragging = false;
                        outputScroll = 0;
                        sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "");
                        rebuildWidgets();
                    }).setLabelOffset(6, 1);
                }
            }
            case BRANDING -> {
                addSectionPcButton(innerX, curY, innerWidth, 24, "Set Checkout Terminal Near PC",
                        btn -> sendShopDesktopAction("SHOP_SET_CHECKOUT_TERMINAL", "")).setLabelOffset(6, 1);
            }
            case LIMITS -> {
                if (shopStockroomViewOpen) {
                    if (innerWidth < 360) {
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Back To Shelf Inventory", btn -> {
                            shopStockroomViewOpen = false;
                            sendShopDesktopAction("SHOP_SCAN", "");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Stockroom",
                                btn -> sendShopDesktopAction("SHOP_STOCKROOM_REPORT", "")).setLabelOffset(6, 1);
                    } else {
                        int half = (innerWidth - gap) / 2;
                        addSectionPcButton(innerX, curY, half, 24, "Back To Shelf Inventory", btn -> {
                            shopStockroomViewOpen = false;
                            sendShopDesktopAction("SHOP_SCAN", "");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        addSectionPcButton(innerX + half + gap, curY, half, 24, "Refresh Stockroom",
                                btn -> sendShopDesktopAction("SHOP_STOCKROOM_REPORT", "")).setLabelOffset(6, 1);
                    }
                    curY += 32;
                    addSectionFormInput(
                            SHOP_STOCKROOM_SEARCH_KEY,
                            innerX,
                            curY,
                            innerWidth,
                            "Search stockroom: item / storage / position"
                    );
                } else if (shopOrderPalletPickerOpen) {
                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Delivery Pallet Picker");
                    if (innerWidth < 380) {
                        DesktopButton refreshPallets = addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Delivery Pallets",
                                btn -> sendShopDesktopAction("SHOP_ORDER_REPORT", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshPallets, "Refresh Delivery Pallets",
                                "Reload currently labeled delivery pallets that can receive order drop-offs.");
                        curY += 32;
                        DesktopButton selectPallet = addSectionPcButton(innerX, curY, innerWidth, 24, "Select Pallet",
                                btn -> {
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet card first.");
                                        return;
                                    }
                                    shopOrderUseSpecificPallet = true;
                                    shopOrderPalletPickerOpen = false;
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        selectPallet.active = shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(selectPallet, "Select Pallet",
                                "Confirm selected delivery pallet and return to the order form.");
                        curY += 32;
                        DesktopButton backToOrderForm = addSectionPcButton(innerX, curY, innerWidth, 24, "Back To Order Form",
                                btn -> {
                                    shopOrderPalletPickerOpen = false;
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(backToOrderForm, "Back To Order Form",
                                "Return to order management without changing the selected pallet.");
                        curY += 32;
                    } else {
                        int third = (innerWidth - (gap * 2)) / 3;
                        DesktopButton refreshPallets = addSectionPcButton(innerX, curY, third, 24, "Refresh Delivery Pallets",
                                btn -> sendShopDesktopAction("SHOP_ORDER_REPORT", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshPallets, "Refresh Delivery Pallets",
                                "Reload currently labeled delivery pallets that can receive order drop-offs.");
                        DesktopButton selectPallet = addSectionPcButton(innerX + third + gap, curY, third, 24, "Select Pallet",
                                btn -> {
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet card first.");
                                        return;
                                    }
                                    shopOrderUseSpecificPallet = true;
                                    shopOrderPalletPickerOpen = false;
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        selectPallet.active = shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(selectPallet, "Select Pallet",
                                "Confirm selected delivery pallet and return to the order form.");
                        DesktopButton backToOrderForm = addSectionPcButton(innerX + ((third + gap) * 2), curY, third, 24, "Back To Order Form",
                                btn -> {
                                    shopOrderPalletPickerOpen = false;
                                    outputScroll = 0;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(backToOrderForm, "Back To Order Form",
                                "Return to order management without changing the selected pallet.");
                        curY += 32;
                    }
                    String selectedPallet = shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()
                            ? "Selected delivery pallet: none"
                            : "Selected delivery pallet: " + fitToWidth(shopOrderSelectedPalletRef, Math.max(24, innerWidth - 42));
                    DesktopButton selectedPalletButton = addSectionPcButton(innerX, curY, innerWidth, 24, selectedPallet, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedPalletButton.active = false;
                    registerShopOperationsHelp(selectedPalletButton, "Selected Delivery Pallet",
                            "Click a pallet card in output to select it, then use Select Pallet.");
                    curY += 32;
                } else {
                    if (innerWidth < 360) {
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Scan Shelves",
                                btn -> sendShopDesktopAction("SHOP_SCAN", "")).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Show Stockroom", btn -> {
                            shopStockroomViewOpen = true;
                            sendShopDesktopAction("SHOP_STOCKROOM_REPORT", "");
                            outputScroll = 0;
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24,
                                "Filter: " + formatShopInventoryFilterMode(shopInventoryFilterMode),
                                btn -> cycleShopInventoryFilterMode()).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, innerWidth, 24,
                                "Sort: " + formatShopInventorySortMode(shopInventorySortMode),
                                btn -> cycleShopInventorySortMode()).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionFormInput(
                                SHOP_INVENTORY_SEARCH_KEY,
                                innerX,
                                curY,
                                innerWidth,
                                "Search shelves: #, item, slot"
                        );
                        curY += 28;
                        addSectionPcButton(innerX, curY, innerWidth, 24, "Restock All Low Stock",
                                btn -> sendShopDesktopAction("SHOP_RESTOCK_LOW", "")).setLabelOffset(6, 1);
                        curY += 32;
                        DesktopButton selectedShelfButton = addSectionPcButton(
                                innerX,
                                curY,
                                innerWidth,
                                24,
                                "Restock Selected Shelf",
                                btn -> sendShopDesktopAction("SHOP_RESTOCK_SHELF", shopInventorySelectedShelfTarget)
                        ).setLabelOffset(6, 1);
                        selectedShelfButton.active = shopInventorySelectedShelfTarget != null
                                && !shopInventorySelectedShelfTarget.isBlank();
                    } else {
                        int half = (innerWidth - gap) / 2;
                        addSectionPcButton(innerX, curY, half, 24, "Scan Shelves",
                                btn -> sendShopDesktopAction("SHOP_SCAN", "")).setLabelOffset(6, 1);
                        addSectionPcButton(innerX + half + gap, curY, half, 24, "Show Stockroom", btn -> {
                            shopStockroomViewOpen = true;
                            sendShopDesktopAction("SHOP_STOCKROOM_REPORT", "");
                            outputScroll = 0;
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionPcButton(innerX, curY, half, 24,
                                "Filter: " + formatShopInventoryFilterMode(shopInventoryFilterMode),
                                btn -> cycleShopInventoryFilterMode()).setLabelOffset(6, 1);
                        addSectionPcButton(innerX + half + gap, curY, half, 24,
                                "Sort: " + formatShopInventorySortMode(shopInventorySortMode),
                                btn -> cycleShopInventorySortMode()).setLabelOffset(6, 1);
                        curY += 32;
                        addSectionFormInput(
                                SHOP_INVENTORY_SEARCH_KEY,
                                innerX,
                                curY,
                                innerWidth,
                                "Search shelves: #, item, slot"
                        );
                        curY += 28;
                        addSectionPcButton(innerX, curY, half, 24, "Restock All Low Stock",
                                btn -> sendShopDesktopAction("SHOP_RESTOCK_LOW", "")).setLabelOffset(6, 1);
                        DesktopButton selectedShelfButton = addSectionPcButton(
                                innerX + half + gap,
                                curY,
                                half,
                                24,
                                "Restock Selected Shelf",
                                btn -> sendShopDesktopAction("SHOP_RESTOCK_SHELF", shopInventorySelectedShelfTarget)
                        ).setLabelOffset(6, 1);
                        selectedShelfButton.active = shopInventorySelectedShelfTarget != null
                                && !shopInventorySelectedShelfTarget.isBlank();
                    }
                }
            }
            case GOVERNANCE -> {
                formValues.putIfAbsent("shop.order.qty", "64");
                formValues.putIfAbsent("shop.order.reward", "250");
                formValues.putIfAbsent("shop.order.timeout", "30");
                formValues.putIfAbsent(SHOP_ORDER_PICK_SEARCH_KEY, "");

                if (shopOrderPickerOpen) {
                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Shelf Item Picker");
                    if (innerWidth < 380) {
                        DesktopButton refreshShelfItems = addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Shelf Items",
                                btn -> sendShopDesktopAction("SHOP_ORDER_ITEM_PICKER", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshShelfItems, "Refresh Shelf Items",
                                "Reload the list of items currently displayed on shelf slots.");
                        curY += 32;
                        DesktopButton selectItem = addSectionPcButton(innerX, curY, innerWidth, 24, "Select Item",
                                btn -> {
                                    if (shopOrderSelectedItemId == null || shopOrderSelectedItemId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an item card first.");
                                        return;
                                    }
                                    shopOrderPickerOpen = false;
                                    outputScroll = 0;
                                    sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        selectItem.active = shopOrderSelectedItemId != null && !shopOrderSelectedItemId.isBlank();
                        registerShopOperationsHelp(selectItem, "Select Item",
                                "Confirm currently selected shelf item and return to the order form.");
                        curY += 32;
                        DesktopButton backToOrderForm = addSectionPcButton(innerX, curY, innerWidth, 24, "Back To Order Form",
                                btn -> {
                                    shopOrderPickerOpen = false;
                                    outputScroll = 0;
                                    sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(backToOrderForm, "Back To Order Form",
                                "Return to order management after selecting the display item.");
                        curY += 32;
                    } else {
                        int third = (innerWidth - (gap * 2)) / 3;
                        DesktopButton refreshShelfItems = addSectionPcButton(innerX, curY, third, 24, "Refresh Shelf Items",
                                btn -> sendShopDesktopAction("SHOP_ORDER_ITEM_PICKER", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshShelfItems, "Refresh Shelf Items",
                                "Reload the list of items currently displayed on shelf slots.");
                        DesktopButton selectItem = addSectionPcButton(innerX + third + gap, curY, third, 24, "Select Item",
                                btn -> {
                                    if (shopOrderSelectedItemId == null || shopOrderSelectedItemId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an item card first.");
                                        return;
                                    }
                                    shopOrderPickerOpen = false;
                                    outputScroll = 0;
                                    sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        selectItem.active = shopOrderSelectedItemId != null && !shopOrderSelectedItemId.isBlank();
                        registerShopOperationsHelp(selectItem, "Select Item",
                                "Confirm currently selected shelf item and return to the order form.");
                        DesktopButton backToOrderForm = addSectionPcButton(innerX + ((third + gap) * 2), curY, third, 24, "Back To Order Form",
                                btn -> {
                                    shopOrderPickerOpen = false;
                                    outputScroll = 0;
                                    sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(backToOrderForm, "Back To Order Form",
                                "Return to order management after selecting the display item.");
                        curY += 32;
                    }
                    String selectedItem = shopOrderSelectedItemName == null || shopOrderSelectedItemName.isBlank()
                            ? "Selected item: none"
                            : "Selected item: " + fitToWidth(shopOrderSelectedItemName, Math.max(24, innerWidth - 34));
                    DesktopButton selectedItemButton = addSectionPcButton(innerX, curY, innerWidth, 24, selectedItem, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedItemButton.active = false;
                    registerShopOperationsHelp(selectedItemButton, "Selected Item",
                            "After selecting a shelf card from output, this value is used for the order form.");
                    curY += 32;
                    curY = addSectionFieldHeader(innerX, curY, innerWidth, "Search Display Items");
                    DesktopEditBox searchInput = addSectionFormInput(
                            SHOP_ORDER_PICK_SEARCH_KEY,
                            innerX,
                            curY,
                            innerWidth,
                            "Search by item name or modid:item_name"
                    );
                    registerShopOperationsHelp(searchInput, "Search Display Items",
                            "Filter shelf item cards by display name or item id (modid:item_name).");
                    curY += 28;
                } else {
                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Order Board Controls");
                    if (innerWidth < 380) {
                        DesktopButton refreshOrders = addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Orders",
                                btn -> sendShopDesktopAction("SHOP_ORDER_REPORT", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshOrders, "Refresh Orders",
                                "Reload active, completed, and assigned delivery order data.");
                        curY += 32;
                        DesktopButton pickFromShelves = addSectionPcButton(innerX, curY, innerWidth, 24, "Pick Item From Shelves",
                                btn -> openShopOrderItemPickerFromShelves()).setLabelOffset(6, 1);
                        registerShopOperationsHelp(pickFromShelves, "Pick Item From Shelves",
                                "Open shelf-display item picker and choose which product to order.");
                        curY += 32;
                        DesktopButton cancelSelected = addSectionPcButton(innerX, curY, innerWidth, 24, "Cancel Selected Order",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_CANCEL", shopOrderSelectedId);
                                }).setLabelOffset(6, 1);
                        cancelSelected.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank();
                        registerShopOperationsHelp(cancelSelected, "Cancel Selected Order",
                                "Cancel the currently selected order card if it is not already completed.");
                        curY += 32;
                        DesktopButton clearSelection = addSectionPcButton(innerX, curY, innerWidth, 24, "Clear Selection",
                                btn -> {
                                    shopOrderSelectedId = "";
                                    shopOrderSelectedItemId = "";
                                    shopOrderSelectedItemName = "";
                                    shopOrderSelectedPalletRef = "";
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(clearSelection, "Clear Selection",
                                "Clear currently selected order, pallet, and item values.");
                        curY += 32;
                    } else {
                        int half = (innerWidth - gap) / 2;
                        DesktopButton refreshOrders = addSectionPcButton(innerX, curY, half, 24, "Refresh Orders",
                                btn -> sendShopDesktopAction("SHOP_ORDER_REPORT", "")).setLabelOffset(6, 1);
                        registerShopOperationsHelp(refreshOrders, "Refresh Orders",
                                "Reload active, completed, and assigned delivery order data.");
                        DesktopButton pickFromShelves = addSectionPcButton(innerX + half + gap, curY, half, 24, "Pick Item From Shelves",
                                btn -> openShopOrderItemPickerFromShelves()).setLabelOffset(6, 1);
                        registerShopOperationsHelp(pickFromShelves, "Pick Item From Shelves",
                                "Open shelf-display item picker and choose which product to order.");
                        curY += 32;
                        DesktopButton cancelSelected = addSectionPcButton(innerX, curY, half, 24, "Cancel Selected Order",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_CANCEL", shopOrderSelectedId);
                                }).setLabelOffset(6, 1);
                        cancelSelected.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank();
                        registerShopOperationsHelp(cancelSelected, "Cancel Selected Order",
                                "Cancel the currently selected order card if it is not already completed.");
                        DesktopButton clearSelection = addSectionPcButton(innerX + half + gap, curY, half, 24, "Clear Selection",
                                btn -> {
                                    shopOrderSelectedId = "";
                                    shopOrderSelectedItemId = "";
                                    shopOrderSelectedItemName = "";
                                    shopOrderSelectedPalletRef = "";
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(clearSelection, "Clear Selection",
                                "Clear currently selected order, pallet, and item values.");
                        curY += 32;
                    }

                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Create Delivery Order");
                    String selectedItem = shopOrderSelectedItemName == null || shopOrderSelectedItemName.isBlank()
                            ? "Selected item: none"
                            : "Selected item: " + fitToWidth(shopOrderSelectedItemName, Math.max(24, innerWidth - 34));
                    DesktopButton selectedItemButton = addSectionPcButton(innerX, curY, innerWidth, 24, selectedItem, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedItemButton.active = false;
                    registerShopOperationsHelp(selectedItemButton, "Selected Item",
                            "Selected from shelf display picker and used as the requested item.");
                    curY += 32;

                    curY = addSectionFieldHeader(innerX, curY, innerWidth, "Delivery Target Mode");
                    if (innerWidth < 380) {
                        DesktopButton randomMode = addSectionPcButton(
                                innerX,
                                curY,
                                innerWidth,
                                24,
                                shopOrderUseSpecificPallet ? "Use Random Delivery Pallet" : "Selected: Random Delivery Pallet",
                                btn -> {
                                    shopOrderUseSpecificPallet = false;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        randomMode.active = shopOrderUseSpecificPallet;
                        registerShopOperationsHelp(randomMode, "Random Delivery Pallet",
                                "Order can be delivered to any labeled delivery pallet that has free capacity.");
                        curY += 32;

                        DesktopButton specificMode = addSectionPcButton(
                                innerX,
                                curY,
                                innerWidth,
                                24,
                                shopOrderUseSpecificPallet ? "Selected: Specific Delivery Pallet" : "Use Specific Delivery Pallet",
                                btn -> {
                                    shopOrderUseSpecificPallet = true;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        specificMode.active = !shopOrderUseSpecificPallet;
                        registerShopOperationsHelp(specificMode, "Specific Delivery Pallet",
                                "Order can only be delivered to the currently selected labeled pallet card.");
                        curY += 32;
                    } else {
                        int half = (innerWidth - gap) / 2;
                        DesktopButton randomMode = addSectionPcButton(
                                innerX,
                                curY,
                                half,
                                24,
                                shopOrderUseSpecificPallet ? "Use Random Delivery Pallet" : "Selected: Random Delivery Pallet",
                                btn -> {
                                    shopOrderUseSpecificPallet = false;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        randomMode.active = shopOrderUseSpecificPallet;
                        registerShopOperationsHelp(randomMode, "Random Delivery Pallet",
                                "Order can be delivered to any labeled delivery pallet that has free capacity.");

                        DesktopButton specificMode = addSectionPcButton(
                                innerX + half + gap,
                                curY,
                                half,
                                24,
                                shopOrderUseSpecificPallet ? "Selected: Specific Delivery Pallet" : "Use Specific Delivery Pallet",
                                btn -> {
                                    shopOrderUseSpecificPallet = true;
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        specificMode.active = !shopOrderUseSpecificPallet;
                        registerShopOperationsHelp(specificMode, "Specific Delivery Pallet",
                                "Order can only be delivered to the currently selected labeled pallet card.");
                        curY += 32;
                    }

                    String modeInfo = shopOrderUseSpecificPallet
                            ? (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()
                            ? "Specific target: none selected"
                            : fitToWidth("Specific target: " + shopOrderSelectedPalletRef, Math.max(24, innerWidth - 24)))
                            : "Random target: any labeled delivery pallet";
                    DesktopButton modeInfoButton = addSectionPcButton(innerX, curY, innerWidth, 24, modeInfo, btn -> {
                    }).setLabelOffset(6, 1);
                    modeInfoButton.active = false;
                    registerShopOperationsHelp(modeInfoButton, "Active Delivery Mode",
                            "Choose Random to allow any labeled pallet, or Specific to lock this order to one selected pallet.");
                    curY += 32;

                    if (innerWidth < 540) {
                        curY = addSectionFieldHeader(innerX, curY, innerWidth, "Order Quantity");
                        DesktopEditBox quantityInput = addSectionFormInput("shop.order.qty", innerX, curY, innerWidth, "Order quantity");
                        registerShopOperationsHelp(quantityInput, "Order Quantity",
                                "Total requested units for the delivery order.");
                        curY += 28;
                        curY = addSectionFieldHeader(innerX, curY, innerWidth, "Courier Reward ($)");
                        DesktopEditBox rewardInput = addSectionFormInput("shop.order.reward", innerX, curY, innerWidth, "Courier reward ($)");
                        registerShopOperationsHelp(rewardInput, "Courier Reward",
                                "Amount paid to courier when delivery is completed.");
                        curY += 28;
                        curY = addSectionFieldHeader(innerX, curY, innerWidth, "Timeout Minutes");
                        DesktopEditBox timeoutInput = addSectionFormInput("shop.order.timeout", innerX, curY, innerWidth, "Timeout minutes (5-240)");
                        registerShopOperationsHelp(timeoutInput, "Timeout Minutes",
                                "Minutes before accepted order expires and returns to open pool.");
                        curY += 28;
                    } else {
                        int colW = (innerWidth - (gap * 2)) / 3;
                        addSectionFieldHeader(innerX, curY, colW, "Order Quantity");
                        addSectionFieldHeader(innerX + colW + gap, curY, colW, "Courier Reward ($)");
                        addSectionFieldHeader(innerX + ((colW + gap) * 2), curY, colW, "Timeout Minutes");
                        curY += 16;
                        DesktopEditBox quantityInput = addSectionFormInput("shop.order.qty", innerX, curY, colW, "Order quantity");
                        DesktopEditBox rewardInput = addSectionFormInput("shop.order.reward", innerX + colW + gap, curY, colW, "Courier reward ($)");
                        DesktopEditBox timeoutInput = addSectionFormInput("shop.order.timeout", innerX + ((colW + gap) * 2), curY, colW, "Timeout minutes");
                        registerShopOperationsHelp(quantityInput, "Order Quantity",
                                "Total requested units for the delivery order.");
                        registerShopOperationsHelp(rewardInput, "Courier Reward",
                                "Amount paid to courier when delivery is completed.");
                        registerShopOperationsHelp(timeoutInput, "Timeout Minutes",
                                "Minutes before accepted order expires and returns to open pool.");
                        curY += 28;
                    }
                    DesktopButton createOrder = addSectionPcButton(innerX, curY, innerWidth, 24, "Create Delivery Order",
                            btn -> submitShopOrderCreate()).setLabelOffset(6, 1);
                    registerShopOperationsHelp(createOrder, "Create Delivery Order",
                            "Create a new order using selected item, quantity, reward, and timeout.");
                    curY += 32;

                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Specific Delivery Pallet");
                    String selectedPallet = shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()
                            ? "Selected delivery pallet: none"
                            : fitToWidth("Selected delivery pallet: " + shopOrderSelectedPalletRef, Math.max(24, innerWidth - 20));
                    DesktopButton selectedPalletButton = addSectionPcButton(innerX, curY, innerWidth, 24, selectedPallet, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedPalletButton.active = false;
                    registerShopOperationsHelp(selectedPalletButton, "Selected Pallet",
                            "Selected from delivery pallet picker cards. Used for specific-order targeting.");
                    curY += 32;

                    if (innerWidth < 380) {
                        DesktopButton pickerToggle = addSectionPcButton(innerX, curY, innerWidth, 24,
                                shopOrderPalletPickerOpen ? "Back From Pallet Picker" : "Select Specific Pallet",
                                btn -> {
                                    shopOrderPalletPickerOpen = !shopOrderPalletPickerOpen;
                                    if (shopOrderPalletPickerOpen) {
                                        shopOrderPickerOpen = false;
                                        outputScroll = 0;
                                        sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    }
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(pickerToggle, "Select Specific Pallet",
                                "Open a dedicated pallet-card selector and choose one labeled delivery pallet.");
                        curY += 32;
                        DesktopButton useSelected = addSectionPcButton(innerX, curY, innerWidth, 24, "Use Selected Specific Pallet",
                                btn -> {
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet first.");
                                        return;
                                    }
                                    shopOrderUseSpecificPallet = true;
                                    shopOrderPalletPickerOpen = false;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        useSelected.active = shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(useSelected, "Use Selected Specific Pallet",
                                "Lock new order creation to this selected delivery pallet.");
                        curY += 32;
                    } else {
                        int half = (innerWidth - gap) / 2;
                        DesktopButton pickerToggle = addSectionPcButton(innerX, curY, half, 24,
                                shopOrderPalletPickerOpen ? "Back From Pallet Picker" : "Select Specific Pallet",
                                btn -> {
                                    shopOrderPalletPickerOpen = !shopOrderPalletPickerOpen;
                                    if (shopOrderPalletPickerOpen) {
                                        shopOrderPickerOpen = false;
                                        outputScroll = 0;
                                        sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                                    }
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        registerShopOperationsHelp(pickerToggle, "Select Specific Pallet",
                                "Open a dedicated pallet-card selector and choose one labeled delivery pallet.");
                        DesktopButton useSelected = addSectionPcButton(innerX + half + gap, curY, half, 24, "Use Selected Pallet",
                                btn -> {
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet first.");
                                        return;
                                    }
                                    shopOrderUseSpecificPallet = true;
                                    shopOrderPalletPickerOpen = false;
                                    rebuildWidgets();
                                }).setLabelOffset(6, 1);
                        useSelected.active = shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(useSelected, "Use Selected Pallet",
                                "Lock new order creation to this selected delivery pallet.");
                        curY += 32;
                    }

                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Per-Order Pallet Binding");
                    String selectedOrder = shopOrderSelectedId == null || shopOrderSelectedId.isBlank()
                            ? "Selected order: none"
                            : "Selected order: " + shortUuid(shopOrderSelectedId);
                    DesktopButton selectedOrderButton = addSectionPcButton(innerX, curY, innerWidth, 24, selectedOrder, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedOrderButton.active = false;
                    registerShopOperationsHelp(selectedOrderButton, "Selected Order",
                            "Choose an order card in output. Bind/Clear actions apply to that selected order.");
                    curY += 32;

                    if (innerWidth < 380) {
                        DesktopButton bind = addSectionPcButton(innerX, curY, innerWidth, 24, "Bind Pallet To Selected Order",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_BIND_PALLET",
                                            shopOrderSelectedId.trim() + "|" + shopOrderSelectedPalletRef.trim());
                                }).setLabelOffset(6, 1);
                        bind.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank()
                                && shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(bind, "Bind Pallet To Selected Order",
                                "Bind currently selected order to currently selected assigned pallet.");
                        curY += 32;

                        DesktopButton clearBinding = addSectionPcButton(innerX, curY, innerWidth, 24, "Clear Selected Order Pallet",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_CLEAR_PALLET", shopOrderSelectedId.trim());
                                }).setLabelOffset(6, 1);
                        clearBinding.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank();
                        registerShopOperationsHelp(clearBinding, "Clear Selected Order Pallet",
                                "Remove any bound pallet from the selected order.");
                        curY += 32;
                    } else {
                        int half = (innerWidth - gap) / 2;
                        DesktopButton bind = addSectionPcButton(innerX, curY, half, 24, "Bind Pallet To Selected Order",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    if (shopOrderSelectedPalletRef == null || shopOrderSelectedPalletRef.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select a pallet card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_BIND_PALLET",
                                            shopOrderSelectedId.trim() + "|" + shopOrderSelectedPalletRef.trim());
                                }).setLabelOffset(6, 1);
                        bind.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank()
                                && shopOrderSelectedPalletRef != null && !shopOrderSelectedPalletRef.isBlank();
                        registerShopOperationsHelp(bind, "Bind Pallet To Selected Order",
                                "Bind currently selected order to currently selected assigned pallet.");

                        DesktopButton clearBinding = addSectionPcButton(innerX + half + gap, curY, half, 24, "Clear Selected Order Pallet",
                                btn -> {
                                    if (shopOrderSelectedId == null || shopOrderSelectedId.isBlank()) {
                                        ClientOwnerPcData.setToast(false, "Select an order card first.");
                                        return;
                                    }
                                    sendShopDesktopAction("SHOP_ORDER_CLEAR_PALLET", shopOrderSelectedId.trim());
                                }).setLabelOffset(6, 1);
                        clearBinding.active = shopOrderSelectedId != null && !shopOrderSelectedId.isBlank();
                        registerShopOperationsHelp(clearBinding, "Clear Selected Order Pallet",
                                "Remove any bound pallet from the selected order.");
                        curY += 32;
                    }

                    curY = addSectionGroupHeader(innerX, curY, innerWidth, "Claim Regions");
                    if (innerWidth < 380) {
                        DesktopButton claimPlot = addSectionPcButton(innerX, curY, innerWidth, 24, "Claim Tool: Plot",
                                btn -> startShopClaimToolAndClose(false)).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimPlot, "Claim Tool: Plot",
                                "Exit PC and start the plot claim tool workflow.");
                        curY += 32;
                        DesktopButton claimStockroom = addSectionPcButton(innerX, curY, innerWidth, 24, "Claim Tool: Stockroom",
                                btn -> startShopClaimToolAndClose(true)).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimStockroom, "Claim Tool: Stockroom",
                                "Exit PC and start stockroom claim workflow inside shop plot.");
                        curY += 32;
                        DesktopButton claimPallets = addSectionPcButton(innerX, curY, innerWidth, 24, "Claim Tool: Delivery Pallets",
                                btn -> startShopPalletClaimToolAndClose()).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimPallets, "Claim Tool: Delivery Pallets",
                                "Exit PC and label/unlabel delivery pallets for orders.");
                    } else {
                        int half = (innerWidth - gap) / 2;
                        DesktopButton claimPlot = addSectionPcButton(innerX, curY, half, 24, "Claim Tool: Plot",
                                btn -> startShopClaimToolAndClose(false)).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimPlot, "Claim Tool: Plot",
                                "Exit PC and start the plot claim tool workflow.");
                        DesktopButton claimStockroom = addSectionPcButton(innerX + half + gap, curY, half, 24, "Claim Tool: Stockroom",
                                btn -> startShopClaimToolAndClose(true)).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimStockroom, "Claim Tool: Stockroom",
                                "Exit PC and start stockroom claim workflow inside shop plot.");
                        curY += 32;
                        DesktopButton claimPallets = addSectionPcButton(innerX, curY, innerWidth, 24, "Claim Tool: Delivery Pallets",
                                btn -> startShopPalletClaimToolAndClose()).setLabelOffset(6, 1);
                        registerShopOperationsHelp(claimPallets, "Claim Tool: Delivery Pallets",
                                "Exit PC and label/unlabel delivery pallets for orders.");
                    }
                }
            }
            case STAFFING -> {
                if (innerWidth < 320) {
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Hire Cashier NPC",
                            btn -> sendShopDesktopAction("SHOP_HIRE_CASHIER", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Show All Employees",
                            btn -> sendShopDesktopAction("SHOP_LIST_EMPLOYEES", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Link Cashier to Payment Terminal",
                            btn -> startShopCashierLinkAndClose()).setLabelOffset(6, 1);
                } else if (innerWidth < 560) {
                    int w = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, w, 24, "Hire Cashier NPC",
                            btn -> sendShopDesktopAction("SHOP_HIRE_CASHIER", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + w + gap, curY, w, 24, "Show All Employees",
                            btn -> sendShopDesktopAction("SHOP_LIST_EMPLOYEES", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Link Cashier to Payment Terminal",
                            btn -> startShopCashierLinkAndClose()).setLabelOffset(6, 1);
                } else {
                    int w = (innerWidth - (gap * 2)) / 3;
                    addSectionPcButton(innerX, curY, w, 24, "Hire Cashier NPC",
                            btn -> sendShopDesktopAction("SHOP_HIRE_CASHIER", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + w + gap, curY, w, 24, "Show All Employees",
                            btn -> sendShopDesktopAction("SHOP_LIST_EMPLOYEES", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + (w + gap) * 2, curY, w, 24, "Refresh Cashiers",
                            btn -> sendShopDesktopAction("SHOP_SCAN_CASHIERS", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Link Cashier to Payment Terminal",
                            btn -> startShopCashierLinkAndClose()).setLabelOffset(6, 1);
                }
            }
            case LENDING -> {
                if (shopSettlementPickerOpen) {
                    String selected = shopSelectedSettlementAccountId == null || shopSelectedSettlementAccountId.isBlank()
                            ? "No account selected"
                            : ("Selected: " + fitToWidth(shopSelectedSettlementAccountId, Math.max(24, innerWidth - 28)));
                    DesktopButton selectedButton = addSectionPcButton(innerX, curY, innerWidth, 24, selected, btn -> {
                    }).setLabelOffset(6, 1);
                    selectedButton.active = false;
                    curY += 32;
                    int half = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, half, 24, "Apply Settlement",
                            btn -> applyShopSettlementSelection()).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Back",
                            btn -> closeShopSettlementPicker()).setLabelOffset(6, 1);
                    break;
                }

                if (shopVaultPlanEditOpen) {
                    int half = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, half, 24, "Withdraw Selected Bills",
                            btn -> applyShopVaultPlanWithdrawal()).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Reset",
                            btn -> {
                                java.util.Arrays.fill(shopVaultRequestedCounts, 0);
                                rebuildWidgets();
                            }).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Back",
                            btn -> {
                                shopVaultPlanEditOpen = false;
                                sendShopDesktopAction("SHOP_FINANCE_REPORT", "");
                                rebuildWidgets();
                            }).setLabelOffset(6, 1);
                    break;
                }

                formValues.putIfAbsent("shop.finance.withdraw_amount", "100");
                addSectionFormInput("shop.finance.withdraw_amount", innerX, curY, innerWidth, "Withdraw amount from vault ($)");
                curY += 32;
                if (innerWidth < 360) {
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Select Settlement Account",
                            btn -> openShopSettlementPicker()).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Withdraw Amount",
                            btn -> sendShopDesktopAction("SHOP_VAULT_WITHDRAW_AMOUNT",
                                    formValues.getOrDefault("shop.finance.withdraw_amount", "").trim())).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Specific Bills (Interactive)",
                            btn -> openShopVaultPlanEditor()).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Finance",
                            btn -> sendShopDesktopAction("SHOP_FINANCE_REPORT", "")).setLabelOffset(6, 1);
                } else {
                    int half = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, half, 24, "Select Settlement Account",
                            btn -> openShopSettlementPicker()).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Withdraw Amount",
                            btn -> sendShopDesktopAction("SHOP_VAULT_WITHDRAW_AMOUNT",
                                    formValues.getOrDefault("shop.finance.withdraw_amount", "").trim())).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, half, 24, "Specific Bills (Interactive)",
                            btn -> openShopVaultPlanEditor()).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Refresh Finance",
                            btn -> sendShopDesktopAction("SHOP_FINANCE_REPORT", "")).setLabelOffset(6, 1);
                }
            }
            case HOURS -> {
                formValues.putIfAbsent(SHOP_HOURS_OPEN_KEY, "9:00 AM");
                formValues.putIfAbsent(SHOP_HOURS_CLOSE_KEY, "9:00 PM");
                formValues.putIfAbsent(SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY, "false");
                formValues.putIfAbsent(SHOP_LIGHTING_ENABLED_KEY, "true");
                formValues.putIfAbsent(SHOP_LIGHTING_MAIN_MODE_KEY, "OPEN_HOURS");
                formValues.putIfAbsent(SHOP_LIGHTING_STOCKROOM_MODE_KEY, "OPEN_HOURS");
                formValues.putIfAbsent(SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY, "false");
                formValues.putIfAbsent(SHOP_LIGHTING_LEVEL_KEY, "15");

                curY = addSectionGroupHeader(innerX, curY, innerWidth, "Store Schedule");
                DesktopEditBox openInput = addSectionFormInput(SHOP_HOURS_OPEN_KEY, innerX, curY, innerWidth, "Open time (e.g. 9:00 AM)");
                registerShopOperationsHelp(openInput, "Open Time",
                        "Set daily opening time in AM/PM or 24h format. Example: 9:00 AM or 21:30.");
                curY += 32;
                DesktopEditBox closeInput = addSectionFormInput(SHOP_HOURS_CLOSE_KEY, innerX, curY, innerWidth, "Close time (e.g. 9:00 PM)");
                registerShopOperationsHelp(closeInput, "Close Time",
                        "Set daily closing time in AM/PM or 24h format. Closing enforces no-customer access.");
                curY += 32;

                int half = (innerWidth - gap) / 2;
                DesktopButton applyHours = addSectionPcButton(innerX, curY, half, 24, "Apply Hours",
                        btn -> sendShopDesktopAction(
                                "SHOP_HOURS_SET",
                                formValues.getOrDefault(SHOP_HOURS_OPEN_KEY, "").trim()
                                        + "|"
                                        + formValues.getOrDefault(SHOP_HOURS_CLOSE_KEY, "").trim()
                        )).setLabelOffset(6, 1);
                registerShopOperationsHelp(applyHours, "Apply Hours",
                        "Saves opening and closing times. Closed shops block basket checkout and cashier flow.");
                DesktopButton refreshHours = addSectionPcButton(innerX + half + gap, curY, half, 24, "Refresh Hours & Lighting",
                        btn -> sendShopDesktopAction("SHOP_HOURS_LIGHTING_REPORT", "")).setLabelOffset(6, 1);
                registerShopOperationsHelp(refreshHours, "Refresh Hours & Lighting",
                        "Reloads current schedule, open status, and managed lighting state from server.");
                curY += 32;

                curY = addSectionGroupHeader(innerX, curY, innerWidth, "Closed-Hours Delivery Access");
                boolean delivererStockroomAccess = "true".equalsIgnoreCase(
                        formValues.getOrDefault(SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY, "false")
                );
                DesktopButton allowDeliverers = addSectionPcButton(innerX, curY, half, 24,
                        delivererStockroomAccess ? "Allowed" : "Allow",
                        btn -> {
                            formValues.put(SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY, "true");
                            sendShopDesktopAction("SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS", "true");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                allowDeliverers.active = !delivererStockroomAccess;
                registerShopOperationsHelp(allowDeliverers, "Allow Closed-Hours Deliverers",
                        "When enabled, accepted couriers may stay on claimed land for 1 minute, and inside stockroom for 5 minutes.");
                DesktopButton denyDeliverers = addSectionPcButton(innerX + half + gap, curY, half, 24,
                        delivererStockroomAccess ? "Deny" : "Denied",
                        btn -> {
                            formValues.put(SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY, "false");
                            sendShopDesktopAction("SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS", "false");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                denyDeliverers.active = delivererStockroomAccess;
                registerShopOperationsHelp(denyDeliverers, "Deny Closed-Hours Deliverers",
                        "When disabled, non-staff players are removed from claimed shop land while closed.");
                curY += 32;

                curY = addSectionGroupHeader(innerX, curY, innerWidth, "Automatic Lighting");
                boolean lightingEnabled = "true".equalsIgnoreCase(formValues.getOrDefault(SHOP_LIGHTING_ENABLED_KEY, "true"));
                DesktopButton lightingOn = addSectionPcButton(innerX, curY, half, 24,
                        lightingEnabled ? "Enabled" : "Enable",
                        btn -> {
                            formValues.put(SHOP_LIGHTING_ENABLED_KEY, "true");
                            sendShopDesktopAction("SHOP_LIGHTING_ENABLED", "true");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                lightingOn.active = !lightingEnabled;
                registerShopOperationsHelp(lightingOn, "Enable Automatic Lighting",
                        "Uses managed light blocks to keep the shop illuminated based on selected lighting modes.");
                DesktopButton lightingOff = addSectionPcButton(innerX + half + gap, curY, half, 24,
                        lightingEnabled ? "Disable" : "Disabled",
                        btn -> {
                            formValues.put(SHOP_LIGHTING_ENABLED_KEY, "false");
                            sendShopDesktopAction("SHOP_LIGHTING_ENABLED", "false");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                lightingOff.active = lightingEnabled;
                registerShopOperationsHelp(lightingOff, "Disable Automatic Lighting",
                        "Removes UBS-managed light blocks and leaves lighting fully manual.");
                curY += 32;

                curY = addSectionFieldHeader(innerX, curY, innerWidth, "Light Intensity");
                int lightingLevel = parseBoundedInt(formValues.get(SHOP_LIGHTING_LEVEL_KEY), 15, 1, 15);
                DesktopSlider levelSlider = addSectionSlider(
                        innerX,
                        curY,
                        innerWidth,
                        24,
                        1,
                        15,
                        lightingLevel,
                        value -> formValues.put(SHOP_LIGHTING_LEVEL_KEY, String.valueOf(value))
                ).setLabel("Level").setAccentColor(0xFF8DEFA8);
                registerShopOperationsHelp(levelSlider, "Light Level",
                        "Sets invisible light-block intensity from 1 to 15 for automated store lighting.");
                curY += 32;

                DesktopButton applyLightLevel = addSectionPcButton(innerX, curY, innerWidth, 24, "Apply Light Level",
                        btn -> sendShopDesktopAction(
                                "SHOP_LIGHTING_LEVEL",
                                String.valueOf(parseBoundedInt(formValues.get(SHOP_LIGHTING_LEVEL_KEY), 15, 1, 15))
                        )).setLabelOffset(6, 1);
                registerShopOperationsHelp(applyLightLevel, "Apply Light Level",
                        "Applies the current brightness to managed light blocks for this shop.");
                curY += 32;

                curY = addSectionFieldHeader(innerX, curY, innerWidth, "Main Plot Lighting Mode");
                String mainMode = formValues.getOrDefault(SHOP_LIGHTING_MAIN_MODE_KEY, "OPEN_HOURS").trim().toUpperCase(Locale.ROOT);
                String[] lightingModes = {"ON", "OFF", "OPEN_HOURS", "INVERTED"};
                int modeCols = innerWidth < 520 ? 2 : 4;
                int modeW = (innerWidth - (gap * (modeCols - 1))) / modeCols;
                int modeRowY = curY;
                int modeX = innerX;
                int modeIndex = 0;
                for (String mode : lightingModes) {
                    String label = formatLightingModeLabel(mode, mainMode.equals(mode));
                    DesktopButton modeButton = addSectionPcButton(modeX, modeRowY, modeW, 24, label, btn -> {
                        formValues.put(SHOP_LIGHTING_MAIN_MODE_KEY, mode);
                        sendShopDesktopAction("SHOP_LIGHTING_MAIN_MODE", mode);
                        rebuildWidgets();
                    }).setLabelOffset(6, 1);
                    modeButton.active = !mainMode.equals(mode);
                    registerShopOperationsHelp(modeButton, "Main Lighting Mode",
                            lightingModeDescription(mode));
                    modeIndex++;
                    if (modeIndex % modeCols == 0) {
                        modeX = innerX;
                        modeRowY += 32;
                    } else {
                        modeX += modeW + gap;
                    }
                }
                curY = modeRowY + (modeIndex % modeCols == 0 ? 0 : 32);

                curY = addSectionFieldHeader(innerX, curY, innerWidth, "Stockroom Lighting");
                boolean excludeStockroom = "true".equalsIgnoreCase(formValues.getOrDefault(SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY, "false"));
                DesktopButton includeStockroom = addSectionPcButton(innerX, curY, half, 24,
                        excludeStockroom ? "Include In Main Plot" : "Included In Main Plot",
                        btn -> {
                            formValues.put(SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY, "false");
                            sendShopDesktopAction("SHOP_LIGHTING_EXCLUDE_STOCKROOM", "false");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                includeStockroom.active = excludeStockroom;
                registerShopOperationsHelp(includeStockroom, "Include Stockroom",
                        "Stockroom follows the same lighting mode as the main shop plot.");
                DesktopButton excludeStockroomButton = addSectionPcButton(innerX + half + gap, curY, half, 24,
                        excludeStockroom ? "Excluded / Independent" : "Exclude + Independent Mode",
                        btn -> {
                            formValues.put(SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY, "true");
                            sendShopDesktopAction("SHOP_LIGHTING_EXCLUDE_STOCKROOM", "true");
                            rebuildWidgets();
                        }).setLabelOffset(6, 1);
                excludeStockroomButton.active = !excludeStockroom;
                registerShopOperationsHelp(excludeStockroomButton, "Exclude Stockroom",
                        "Stockroom uses its own lighting mode and can be inverted or always on/off separately.");
                curY += 32;

                String stockMode = formValues.getOrDefault(SHOP_LIGHTING_STOCKROOM_MODE_KEY, "OPEN_HOURS").trim().toUpperCase(Locale.ROOT);
                modeRowY = curY;
                modeX = innerX;
                modeIndex = 0;
                for (String mode : lightingModes) {
                    String label = formatLightingModeLabel(mode, stockMode.equals(mode));
                    DesktopButton modeButton = addSectionPcButton(modeX, modeRowY, modeW, 24, label, btn -> {
                        formValues.put(SHOP_LIGHTING_STOCKROOM_MODE_KEY, mode);
                        sendShopDesktopAction("SHOP_LIGHTING_STOCKROOM_MODE", mode);
                        rebuildWidgets();
                    }).setLabelOffset(6, 1);
                    modeButton.active = !stockMode.equals(mode);
                    registerShopOperationsHelp(modeButton, "Stockroom Lighting Mode",
                            lightingModeDescription(mode));
                    modeIndex++;
                    if (modeIndex % modeCols == 0) {
                        modeX = innerX;
                        modeRowY += 32;
                    } else {
                        modeX += modeW + gap;
                    }
                }
            }
            case PERMISSIONS -> {
                formValues.putIfAbsent(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, "");
                formValues.putIfAbsent(SHOP_PERMISSIONS_SELECTED_PLAYER_NAME_KEY, "");
                formValues.putIfAbsent(SHOP_PERMISSIONS_PICK_SEARCH_KEY, "");
                String selectedRole = formValues
                        .getOrDefault(SHOP_PERMISSIONS_ROLE_KEY, ShopService.SHOP_ROLE_MANAGER)
                        .trim()
                        .toUpperCase(Locale.ROOT);
                if (!ShopService.SHOP_PERMISSION_ROLES.contains(selectedRole)) {
                    selectedRole = ShopService.SHOP_ROLE_MANAGER;
                    formValues.put(SHOP_PERMISSIONS_ROLE_KEY, selectedRole);
                }
                String selectedPlayerId = formValues.getOrDefault(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, "").trim();
                String selectedPlayerName = formValues.getOrDefault(SHOP_PERMISSIONS_SELECTED_PLAYER_NAME_KEY, "").trim();
                String selectedPlayerLabel = selectedPlayerId.isBlank()
                        ? "No player selected"
                        : (selectedPlayerName.isBlank() ? selectedPlayerId : (selectedPlayerName + " • " + shortUuid(selectedPlayerId)));
                curY = addSectionGroupHeader(innerX, curY, innerWidth, "Role List Filter");
                DesktopEditBox searchInput = addSectionFormInput(
                        SHOP_PERMISSIONS_PICK_SEARCH_KEY,
                        innerX,
                        curY,
                        innerWidth,
                        "Search list (name / UUID / role)"
                );
                searchInput.setTextColor(0xFFFFFFFF);
                curY += 32;

                curY = addSectionGroupHeader(innerX, curY, innerWidth, "Plot Access Roles");
                if (innerWidth < 420) {
                    for (String role : ShopService.SHOP_PERMISSION_ROLES) {
                        String label = selectedRole.equals(role) ? "Selected: " + role : role;
                        DesktopButton roleButton = addSectionPcButton(
                                innerX,
                                curY,
                                innerWidth,
                                24,
                                label,
                                btn -> {
                                    formValues.put(SHOP_PERMISSIONS_ROLE_KEY, role);
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        roleButton.active = !selectedRole.equals(role);
                        curY += 32;
                    }
                } else {
                    int roleGap = 8;
                    int roleWidth = (innerWidth - (roleGap * 2)) / 3;
                    int roleX = innerX;
                    for (String role : ShopService.SHOP_PERMISSION_ROLES) {
                        String label = selectedRole.equals(role) ? "Selected: " + role : role;
                        DesktopButton roleButton = addSectionPcButton(
                                roleX,
                                curY,
                                roleWidth,
                                24,
                                label,
                                btn -> {
                                    formValues.put(SHOP_PERMISSIONS_ROLE_KEY, role);
                                    rebuildWidgets();
                                }
                        ).setLabelOffset(6, 1);
                        roleButton.active = !selectedRole.equals(role);
                        roleX += roleWidth + roleGap;
                    }
                    curY += 32;
                }

                curY = addSectionFieldHeader(innerX, curY, innerWidth, "Selected Player");
                DesktopButton selectedPlayerInfo = addSectionPcButton(
                        innerX,
                        curY,
                        innerWidth,
                        24,
                        "Selected: " + fitToWidth(selectedPlayerLabel, Math.max(32, innerWidth - 66)),
                        btn -> {
                        }
                ).setLabelOffset(6, 1);
                selectedPlayerInfo.active = false;
                curY += 32;

                Runnable runSetPermission = () -> {
                    String playerToken = formValues.getOrDefault(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, "").trim();
                    if (playerToken.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select a player card from the output list first.");
                        return;
                    }
                    String role = formValues
                            .getOrDefault(SHOP_PERMISSIONS_ROLE_KEY, ShopService.SHOP_ROLE_MANAGER)
                            .trim()
                            .toUpperCase(Locale.ROOT);
                    if (!ShopService.SHOP_PERMISSION_ROLES.contains(role)) {
                        role = ShopService.SHOP_ROLE_MANAGER;
                    }
                    sendShopDesktopAction("SHOP_PERMISSIONS_SET", playerToken + "|" + role);
                };

                Runnable runRemovePermission = () -> {
                    String playerToken = formValues.getOrDefault(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, "").trim();
                    if (playerToken.isBlank()) {
                        ClientOwnerPcData.setToast(false, "Select a player card from the output list first.");
                        return;
                    }
                    sendShopDesktopAction("SHOP_PERMISSIONS_REMOVE", playerToken);
                };

                if (innerWidth < 380) {
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Permissions",
                            btn -> sendShopDesktopAction("SHOP_PERMISSIONS_REPORT", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Grant / Update Role",
                            btn -> runSetPermission.run()).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Remove Permission",
                            btn -> runRemovePermission.run()).setLabelOffset(6, 1);
                } else {
                    int actionGap = 8;
                    int actionWidth = (innerWidth - (actionGap * 2)) / 3;
                    addSectionPcButton(innerX, curY, actionWidth, 24, "Refresh Permissions",
                            btn -> sendShopDesktopAction("SHOP_PERMISSIONS_REPORT", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + actionWidth + actionGap, curY, actionWidth, 24, "Grant / Update Role",
                            btn -> runSetPermission.run()).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + ((actionWidth + actionGap) * 2), curY, actionWidth, 24, "Remove Permission",
                            btn -> runRemovePermission.run()).setLabelOffset(6, 1);
                    curY += 32;
                }

                DesktopButton selectedRoleInfo = addSectionPcButton(
                        innerX,
                        curY,
                        innerWidth,
                        24,
                        "Selected role: " + selectedRole + " • Click a player card in output to target",
                        btn -> {
                        }
                ).setLabelOffset(6, 1);
                selectedRoleInfo.active = false;
            }
            case COMPLIANCE -> {
                OwnerPcBankAppSummary currentShopApp = getActiveShopAppSummary();
                String currentShopName = currentShopApp == null || currentShopApp.bankName() == null
                        ? ""
                        : currentShopApp.bankName().trim();
                String currentShopId = (currentShopApp == null || currentShopApp.bankId() == null)
                        ? ""
                        : currentShopApp.bankId().toString();

                String existingRename = formValues.getOrDefault("shop.settings.name", "").trim();
                if (existingRename.isBlank() && !currentShopName.isBlank()) {
                    formValues.put("shop.settings.name", currentShopName);
                }
                String selectedType = formValues.getOrDefault("shop.settings.type", SHOP_TYPES.get(0)).toUpperCase(Locale.ROOT);
                if (!SHOP_TYPES.contains(selectedType)) {
                    selectedType = SHOP_TYPES.get(0);
                    formValues.put("shop.settings.type", selectedType);
                }

                addSectionFormInput("shop.settings.name", innerX, curY, innerWidth, "Rename shop");
                curY += 32;

                int typeBottom = addShopTypeSelectors(innerX, curY, innerWidth);
                curY = typeBottom + 6;

                if (innerWidth < 380) {
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Save Shop Name",
                            btn -> sendShopDesktopAction("SHOP_RENAME", formValues.getOrDefault("shop.settings.name", "").trim()))
                            .setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Save Shop Type",
                            btn -> sendShopDesktopAction("SHOP_SET_TYPE", formValues.getOrDefault("shop.settings.type", SHOP_TYPES.get(0))))
                            .setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Clear Checkout Terminal",
                            btn -> sendShopDesktopAction("SHOP_CLEAR_CHECKOUT_TERMINAL", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Clear Cashier Links",
                            btn -> sendShopDesktopAction("SHOP_CLEAR_CASHIER_LINKS", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Shop Apps",
                            btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())).setLabelOffset(6, 1);
                    curY += 32;
                    DesktopButton copyShopId = addSectionPcButton(innerX, curY, innerWidth, 24, "Copy Shop ID",
                            btn -> {
                                if (currentShopId.isBlank()) {
                                    ClientOwnerPcData.setToast(false, "No shop id is available for this app.");
                                    return;
                                }
                                Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
                                if (mc != null && mc.keyboardHandler != null) {
                                    mc.keyboardHandler.setClipboard(currentShopId);
                                }
                                ClientOwnerPcData.setToast(true, "Copied shop ID " + currentShopId + ".");
                            }).setLabelOffset(6, 1);
                    copyShopId.active = !currentShopId.isBlank();
                    curY += 32;
                } else {
                    int half = (innerWidth - gap) / 2;
                    addSectionPcButton(innerX, curY, half, 24, "Save Shop Name",
                            btn -> sendShopDesktopAction("SHOP_RENAME", formValues.getOrDefault("shop.settings.name", "").trim()))
                            .setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Save Shop Type",
                            btn -> sendShopDesktopAction("SHOP_SET_TYPE", formValues.getOrDefault("shop.settings.type", SHOP_TYPES.get(0))))
                            .setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, half, 24, "Clear Checkout Terminal",
                            btn -> sendShopDesktopAction("SHOP_CLEAR_CHECKOUT_TERMINAL", "")).setLabelOffset(6, 1);
                    addSectionPcButton(innerX + half + gap, curY, half, 24, "Clear Cashier Links",
                            btn -> sendShopDesktopAction("SHOP_CLEAR_CASHIER_LINKS", "")).setLabelOffset(6, 1);
                    curY += 32;
                    addSectionPcButton(innerX, curY, innerWidth, 24, "Refresh Shop Apps",
                            btn -> PacketDistributor.sendToServer(new OpenBankOwnerPcPayload())).setLabelOffset(6, 1);
                    curY += 32;
                    DesktopButton copyShopId = addSectionPcButton(innerX, curY, innerWidth, 24, "Copy Shop ID",
                            btn -> {
                                if (currentShopId.isBlank()) {
                                    ClientOwnerPcData.setToast(false, "No shop id is available for this app.");
                                    return;
                                }
                                Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
                                if (mc != null && mc.keyboardHandler != null) {
                                    mc.keyboardHandler.setClipboard(currentShopId);
                                }
                                ClientOwnerPcData.setToast(true, "Copied shop ID " + currentShopId + ".");
                            }).setLabelOffset(6, 1);
                    copyShopId.active = !currentShopId.isBlank();
                    curY += 32;
                }

                addSectionFormInput("shop.settings.delete_confirm", innerX, curY, innerWidth,
                        "Type current shop name to delete");
                curY += 32;
                String deleteConfirm = formValues.getOrDefault("shop.settings.delete_confirm", "").trim();
                boolean canDelete = !currentShopName.isBlank() && deleteConfirm.equalsIgnoreCase(currentShopName);
                DesktopButton deleteButton = addSectionPcButton(
                        innerX,
                        curY,
                        innerWidth,
                        24,
                        canDelete ? "Delete Shop" : "Delete Shop (confirm name first)",
                        btn -> sendShopDesktopAction("SHOP_DELETE", formValues.getOrDefault("shop.settings.delete_confirm", "").trim())
                );
                deleteButton.active = canDelete;
                deleteButton.setLabelOffset(6, 1);
            }
        }
    }

    private int addShopTypeSelectors(int x, int y, int width) {
        String selected = formValues.getOrDefault("shop.settings.type", SHOP_TYPES.get(0)).toUpperCase(Locale.ROOT);
        if (!SHOP_TYPES.contains(selected)) {
            selected = SHOP_TYPES.get(0);
            formValues.put("shop.settings.type", selected);
        } else {
            formValues.putIfAbsent("shop.settings.type", selected);
        }

        int bottom;
        if (width < 420) {
            int currentY = y;
            for (String type : SHOP_TYPES) {
                String label = prettifyShopType(type);
                DesktopButton button = addSectionPcButton(
                        x,
                        currentY,
                        width,
                        24,
                        (selected.equals(type) ? "Selected: " : "") + label,
                        btn -> {
                            formValues.put("shop.settings.type", type);
                            rebuildWidgets();
                        }
                );
                button.active = !selected.equals(type);
                currentY += 30;
            }
            bottom = currentY;
        } else {
            int gap = 8;
            int buttonW = (width - (gap * 2)) / 3;
            int currentX = x;
            for (String type : SHOP_TYPES) {
                String label = prettifyShopType(type);
                DesktopButton button = addSectionPcButton(
                        currentX,
                        y,
                        buttonW,
                        24,
                        (selected.equals(type) ? "Selected: " : "") + label,
                        btn -> {
                            formValues.put("shop.settings.type", type);
                            rebuildWidgets();
                        }
                );
                button.active = !selected.equals(type);
                currentX += buttonW + gap;
            }
            bottom = y + 24;
        }
        return bottom;
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

    private int addSectionGroupHeader(int x, int y, int width, String title) {
        addSectionTextLabel(x + 4, y + 4, title, 0xFFE2F2FF);
        return y + 22;
    }

    private int addSectionFieldHeader(int x, int y, int width, String title) {
        addSectionTextLabel(x + 2, y + 2, title, 0xFFB9D8F3);
        return y + 16;
    }

    private void addSectionTextLabel(int x, int y, String text, int color) {
        if (text == null || text.isBlank()) {
            return;
        }
        int renderY = y - sectionScroll;
        markSectionControl(y, LINE_HEIGHT);
        boolean visible = renderY >= sectionViewportY && (renderY + LINE_HEIGHT) <= (sectionViewportY + sectionViewportH);
        if (!visible) {
            return;
        }
        visibleSectionTextLabels.add(new SectionTextLabel(x, renderY, text, color));
    }

    private void registerShopOperationsHelp(DesktopButton widget, String title, String description) {
        if (widget == null || title == null || title.isBlank() || description == null || description.isBlank()) {
            return;
        }
        if (!widget.visible) {
            return;
        }
        visibleShopOperationsHelp.add(new ShopOperationsHelpHitbox(
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                title,
                description
        ));
    }

    private void registerShopOperationsHelp(DesktopEditBox widget, String title, String description) {
        if (widget == null || title == null || title.isBlank() || description == null || description.isBlank()) {
            return;
        }
        if (!widget.visible) {
            return;
        }
        visibleShopOperationsHelp.add(new ShopOperationsHelpHitbox(
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                title,
                description
        ));
    }

    private void registerShopOperationsHelp(DesktopSlider widget, String title, String description) {
        if (widget == null || title == null || title.isBlank() || description == null || description.isBlank()) {
            return;
        }
        if (!widget.visible) {
            return;
        }
        visibleShopOperationsHelp.add(new ShopOperationsHelpHitbox(
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                title,
                description
        ));
    }

    private void registerWebshopControlHelp(DesktopButton widget, String title, String description) {
        if (widget == null || title == null || title.isBlank() || description == null || description.isBlank()) {
            return;
        }
        if (!widget.visible) {
            return;
        }
        visibleWebshopControlHelp.add(new WebshopControlHelpHitbox(
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                title,
                description
        ));
    }

    private void registerWebshopControlHelp(DesktopEditBox widget, String title, String description) {
        if (widget == null || title == null || title.isBlank() || description == null || description.isBlank()) {
            return;
        }
        if (!widget.visible) {
            return;
        }
        visibleWebshopControlHelp.add(new WebshopControlHelpHitbox(
                widget.getX(),
                widget.getY(),
                widget.getWidth(),
                widget.getHeight(),
                title,
                description
        ));
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

    private DesktopSlider addSectionSlider(int x,
                                           int y,
                                           int width,
                                           int height,
                                           int min,
                                           int max,
                                           int initial,
                                           java.util.function.Consumer<Integer> onValueChanged) {
        int renderY = y - sectionScroll;
        DesktopSlider slider = addRenderableWidget(new DesktopSlider(
                x,
                renderY,
                width,
                height,
                min,
                max,
                initial,
                onValueChanged
        ));
        markSectionControl(y, height);
        boolean visible = renderY >= sectionViewportY && (renderY + height) <= (sectionViewportY + sectionViewportH);
        slider.visible = visible;
        slider.active = visible;
        if (!visible) {
            slider.setFocused(false);
        }
        return slider;
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

    private void openOrActivateAppWindow(OwnerPcBankAppSummary app) {
        if (app == null || app.bankId() == null) {
            return;
        }
        openOrActivateBankWindow(app.bankId(), app.isBankApp());
    }

    private void openOrActivateBankWindow(UUID bankId) {
        openOrActivateBankWindow(bankId, isBankApp(bankId));
    }

    private void openOrActivateBankWindow(UUID bankId, boolean requestData) {
        if (bankId == null) {
            return;
        }
        if (!bankWindows.containsKey(bankId)) {
            bankWindows.put(bankId, new BankWindowState(bankId));
            bankWindowOrder.remove(bankId);
            bankWindowOrder.add(bankId);
        }
        activateBankWindow(bankId, requestData);
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
        } else if (isActiveShopApp()) {
            if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
                sendShopDesktopAction("SHOP_LEVEL_ROADMAP", "");
            } else {
                sendShopDesktopAction(defaultShopActionForSection(activeSection), "");
            }
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
        state.shopLevelRoadmapOpen = shopLevelRoadmapOpen;
        state.shopLevelRoadmapScrollX = Math.max(0, shopLevelRoadmapScrollX);
        state.shopLevelRoadmapSelectedNode = shopLevelRoadmapSelectedNode;
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
        if (isActiveShopApp()) {
            shopLevelRoadmapOpen = state.shopLevelRoadmapOpen;
            shopLevelRoadmapScrollX = Math.max(0, state.shopLevelRoadmapScrollX);
            shopLevelRoadmapSelectedNode = state.shopLevelRoadmapSelectedNode;
        } else {
            shopLevelRoadmapOpen = false;
            shopLevelRoadmapScrollX = 0;
            shopLevelRoadmapSelectedNode = null;
        }
        shopLevelRoadmapMaxScrollX = 0;
        shopLevelRoadmapScrollbarDragging = false;
        shopLevelRoadmapScrollbarTrackHitbox = null;
        shopLevelRoadmapScrollbarThumbHitbox = null;
        shopLevelRoadmapModalCloseHitbox = null;
    }

    private String resolveBankWindowTitle(UUID bankId) {
        if (bankId == null) {
            return "Bank";
        }
        OwnerPcBankAppSummary app = findAppById(bankId);
        if (app != null) {
            return app.isShopApp() ? "Shop: " + app.bankName() : app.bankName();
        }
        OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
        if (data != null && bankId.equals(data.bankId()) && data.bankName() != null && !data.bankName().isBlank()) {
            return data.bankName();
        }
        String raw = bankId.toString();
        return (isBankApp(bankId) ? "Bank " : "Shop ") + raw.substring(0, Math.min(8, raw.length()));
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
            case SHOP_MANAGER -> "APP | Shop Manager";
            case SYSTEM_MONITOR -> "APP | System Monitor";
            case ORDER_BOARD -> "APP | Order Board";
            case WEBSHOP -> "APP | Retail Webshop";
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
            case SHOP_MANAGER -> "Shop Manager";
            case SYSTEM_MONITOR -> "System";
            case ORDER_BOARD -> "Order Board";
            case WEBSHOP -> "Retail Webshop";
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
        if (app == UtilityApp.ORDER_BOARD) {
            // App open does an auto-refresh; keep the banner clean and render results in the output panel.
            ClientOwnerPcData.suppressNextOrderBoardReportToast();
            sendDesktopAction("ORDER_BOARD_REPORT", "", "");
        } else if (app == UtilityApp.WEBSHOP) {
            sendDesktopAction("SHOP_WEBSHOP_REPORT", "", "");
        }
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
        } else if (createShopWindowOpen) {
            activeWindow = WindowMode.CREATE_SHOP;
        } else if (!bankWindowOrder.isEmpty()) {
            UUID target = bankWindowOrder.get(Math.max(0, bankWindowOrder.size() - 1));
            activateBankWindow(target, isBankApp(target));
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
            case SHOP_MANAGER -> {
                shopManagerScroll = 0;
                shopManagerMaxScroll = 0;
                shopManagerViewportX = 0;
                shopManagerViewportY = 0;
                shopManagerViewportW = 0;
                shopManagerViewportH = 0;
                formValues.remove("shop.name");
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
            case ORDER_BOARD -> {
                orderBoardScroll = 0;
                orderBoardMaxScroll = 0;
                orderBoardViewportX = 0;
                orderBoardViewportY = 0;
                orderBoardViewportW = 0;
                orderBoardViewportH = 0;
                orderBoardSelectedOrderId = "";
                visibleOrderBoardCards.clear();
            }
            case WEBSHOP -> {
                webshopScroll = 0;
                webshopMaxScroll = 0;
                webshopViewportX = 0;
                webshopViewportY = 0;
                webshopViewportW = 0;
                webshopViewportH = 0;
                webshopSelectedCatalogItemId = "";
                webshopSelectedCatalogItemName = "";
                webshopSelectedCartItemId = "";
                webshopSelectedOrderId = "";
                webshopSelectedAccountId = "";
                webshopSelectedShopId = "";
                webshopSelectedPalletId = "";
                visibleWebshopCatalogCards.clear();
                visibleWebshopCartCards.clear();
                visibleWebshopAccountCards.clear();
                visibleWebshopShopCards.clear();
                visibleWebshopPalletCards.clear();
                visibleWebshopOrderCards.clear();
                visibleWebshopKpiCards.clear();
                visibleWebshopControlHelp.clear();
                formValues.remove("webshop.qty");
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

    private void sendShopDesktopAction(String action, String arg1) {
        String shopId = activeBankId == null ? "" : activeBankId.toString();
        sendDesktopAction(action, arg1 == null ? "" : arg1, shopId);
    }

    private void cycleShopInventoryFilterMode() {
        ShopInventoryFilterMode[] values = ShopInventoryFilterMode.values();
        int next = (shopInventoryFilterMode.ordinal() + 1) % values.length;
        shopInventoryFilterMode = values[next];
        outputScroll = 0;
        rebuildWidgets();
    }

    private void cycleShopInventorySortMode() {
        ShopInventorySortMode[] values = ShopInventorySortMode.values();
        int next = (shopInventorySortMode.ordinal() + 1) % values.length;
        shopInventorySortMode = values[next];
        outputScroll = 0;
        rebuildWidgets();
    }

    private String formatShopInventoryFilterMode(ShopInventoryFilterMode mode) {
        if (mode == null) {
            return "All";
        }
        return switch (mode) {
            case ALL -> "All";
            case LOW_STOCK -> "Low Stock";
            case OUT_OF_STOCK -> "Out of Stock";
        };
    }

    private String formatShopInventorySortMode(ShopInventorySortMode mode) {
        if (mode == null) {
            return "Shelf";
        }
        return switch (mode) {
            case SHELF_SLOT -> "Shelf";
            case STOCK_ASC -> "Stock Asc";
            case STOCK_DESC -> "Stock Desc";
            case VELOCITY_DESC -> "Velocity";
            case NAME_ASC -> "Name";
        };
    }

    private void closeEntirePcUi() {
        ClientOwnerPcData.suppressNextOwnerPcAutoOpen();
        discardCachedScreenOnClose = true;
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(null);
        } else {
            onClose();
        }
    }

    private void startShopClaimToolAndClose(boolean stockroomMode) {
        sendShopDesktopAction(stockroomMode ? "SHOP_CLAIM_TOOL_STOCKROOM" : "SHOP_CLAIM_TOOL_PLOT", "");
        closeEntirePcUi();
    }

    private void startShopPalletClaimToolAndClose() {
        sendShopDesktopAction("SHOP_CLAIM_TOOL_PALLETS", "");
        closeEntirePcUi();
    }

    private void startShopCashierLinkAndClose() {
        sendShopDesktopAction("SHOP_LINK_CASHIER_TERMINAL", "");
        closeEntirePcUi();
    }

    private void openShopSettlementPicker() {
        shopSettlementPickerOpen = true;
        shopVaultPlanEditOpen = false;
        shopSelectedSettlementAccountId = "";
        outputScroll = 0;
        sendShopDesktopAction("SHOP_LIST_OWNER_ACCOUNTS", "");
        rebuildWidgets();
    }

    private void closeShopSettlementPicker() {
        shopSettlementPickerOpen = false;
        shopSelectedSettlementAccountId = "";
        outputScroll = 0;
        sendShopDesktopAction("SHOP_FINANCE_REPORT", "");
        rebuildWidgets();
    }

    private void applyShopSettlementSelection() {
        String selected = shopSelectedSettlementAccountId == null ? "" : shopSelectedSettlementAccountId.trim();
        if (selected.isBlank()) {
            ClientOwnerPcData.setToast(false, "Select an account card first.");
            return;
        }
        sendShopDesktopAction("SHOP_SET_SETTLEMENT_ACCOUNT", selected);
    }

    private void openShopVaultPlanEditor() {
        shopSettlementPickerOpen = false;
        shopVaultPlanEditOpen = true;
        outputScroll = 0;
        java.util.Arrays.fill(shopVaultRequestedCounts, 0);
        sendShopDesktopAction("SHOP_SHOW_CASH_VAULT", "");
        rebuildWidgets();
    }

    private void applyShopVaultPlanWithdrawal() {
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < shopVaultRequestedCounts.length; i++) {
            if (i > 0) {
                encoded.append(',');
            }
            encoded.append(Math.max(0, shopVaultRequestedCounts[i]));
        }
        sendShopDesktopAction("SHOP_VAULT_WITHDRAW_PLAN", encoded.toString());
    }

    private void openShopOrderItemPickerFromShelves() {
        // Picker runs in a dedicated operations sub-view so players can focus on
        // selecting one of the currently displayed shelf items.
        shopOrderPalletPickerOpen = false;
        shopOrderPickerOpen = true;
        outputScroll = 0;
        sendShopDesktopAction("SHOP_ORDER_ITEM_PICKER", "");
        rebuildWidgets();
    }

    private void submitShopOrderCreate() {
        // Order payload is validated again on server; this keeps UX responsive
        // by catching obvious missing/empty values before network round-trip.
        String itemId = shopOrderSelectedItemId == null ? "" : shopOrderSelectedItemId.trim();
        String itemName = shopOrderSelectedItemName == null ? "" : shopOrderSelectedItemName.trim();
        if (itemId.isBlank()) {
            ClientOwnerPcData.setToast(false, "Select an item first (Pick Item From Shelves).");
            return;
        }

        int quantity = parseIntMetricToken(formValues.getOrDefault("shop.order.qty", "0"));
        if (quantity <= 0) {
            ClientOwnerPcData.setToast(false, "Order quantity must be above 0.");
            return;
        }
        String rewardRaw = formValues.getOrDefault("shop.order.reward", "").trim();
        if (rewardRaw.isBlank()) {
            ClientOwnerPcData.setToast(false, "Courier reward is required.");
            return;
        }
        int timeout = parseIntMetricToken(formValues.getOrDefault("shop.order.timeout", "30"));
        if (timeout <= 0) {
            timeout = 30;
        }

        String safeName = itemName.replace('|', ' ').trim();
        if (safeName.isBlank()) {
            safeName = itemId;
        }
        String selectedPallet = "";
        String palletMode = shopOrderUseSpecificPallet ? "SPECIFIC" : "RANDOM";
        if (shopOrderUseSpecificPallet) {
            selectedPallet = shopOrderSelectedPalletRef == null ? "" : shopOrderSelectedPalletRef.trim();
            if (selectedPallet.isBlank()) {
                ClientOwnerPcData.setToast(false, "Select a specific delivery pallet first.");
                return;
            }
        }
        String payload = itemId
                + "|" + safeName
                + "|" + quantity
                + "|" + rewardRaw
                + "|" + timeout
                + "|" + selectedPallet
                + "|" + palletMode;
        sendShopDesktopAction("SHOP_ORDER_CREATE", payload);
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
            String id = appIdFor(app);
            String label;
            if (app.isShopApp()) {
                label = "Shop: " + (app.bankName() == null ? "Unknown" : app.bankName());
            } else {
                label = "Bank: " + (app.bankName() == null ? "Unknown" : app.bankName());
                if (!app.owner() && app.roleLabel() != null && !app.roleLabel().isBlank()) {
                    label = label + " [" + app.roleLabel() + "]";
                }
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

    private String shopAppId(UUID shopId) {
        if (shopId == null) {
            return "shop:unknown";
        }
        return "shop:" + shopId.toString().toLowerCase(Locale.ROOT);
    }

    private OwnerPcBankAppSummary findAppById(UUID appId) {
        if (appId == null) {
            return null;
        }
        for (OwnerPcBankAppSummary app : ClientOwnerPcData.getApps()) {
            if (app != null && app.bankId() != null && app.bankId().equals(appId)) {
                return app;
            }
        }
        return null;
    }

    private boolean isBankApp(UUID appId) {
        OwnerPcBankAppSummary app = findAppById(appId);
        return app == null || app.isBankApp();
    }

    private boolean isActiveShopApp() {
        if (activeBankId == null) {
            return false;
        }
        OwnerPcBankAppSummary app = findAppById(activeBankId);
        return app != null && app.isShopApp();
    }

    private OwnerPcBankAppSummary getActiveShopAppSummary() {
        return isActiveShopApp() ? findAppById(activeBankId) : null;
    }

    private String appIdFor(OwnerPcBankAppSummary app) {
        if (app == null || app.bankId() == null) {
            return "app:unknown";
        }
        return app.isShopApp() ? shopAppId(app.bankId()) : bankAppId(app.bankId());
    }

    private int countOwnedShopApps() {
        int count = 0;
        for (OwnerPcBankAppSummary app : ClientOwnerPcData.getApps()) {
            if (app != null && app.isShopApp() && app.owner()) {
                count++;
            }
        }
        return count;
    }

    private void copySystemInfoToClipboard() {
        Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
        int guiScale = 0;
        if (mc != null && mc.options != null && mc.options.guiScale() != null && mc.options.guiScale().get() != null) {
            guiScale = mc.options.guiScale().get();
        }
        String info = "UBS Commerce Desktop System Info\n"
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
            activateBankWindow(nextBankId, isBankApp(nextBankId));
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
            shopLevelRoadmapOpen = false;
            shopLevelRoadmapSelectedNode = null;
            shopLevelRoadmapScrollX = 0;
            shopLevelRoadmapMaxScrollX = 0;
            shopLevelRoadmapScrollbarDragging = false;
            shopLevelRoadmapScrollbarTrackHitbox = null;
            shopLevelRoadmapScrollbarThumbHitbox = null;
            shopLevelRoadmapModalCloseHitbox = null;
            formValues.clear();
            ClientOwnerPcData.clearActionOutput();
            if (activeWindow == WindowMode.BANK_APP) {
                if (createWindowOpen) {
                    activeWindow = WindowMode.CREATE_BANK;
                } else if (createShopWindowOpen) {
                    activeWindow = WindowMode.CREATE_SHOP;
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
                activateBankWindow(target, isBankApp(target));
                activeWindow = WindowMode.BANK_APP;
            } else if (createShopWindowOpen) {
                activeWindow = WindowMode.CREATE_SHOP;
            } else if (!utilityWindowOrder.isEmpty()) {
                activeUtilityApp = utilityWindowOrder.get(Math.max(0, utilityWindowOrder.size() - 1));
                activeWindow = WindowMode.UTILITY_APP;
            } else {
                activeWindow = WindowMode.DESKTOP;
            }
        }
        rebuildWidgets();
    }

    private void closeCreateShopWindow() {
        createShopWindowOpen = false;
        if (activeWindow == WindowMode.CREATE_SHOP) {
            if (!bankWindowOrder.isEmpty()) {
                UUID target = activeBankId != null ? activeBankId : bankWindowOrder.get(Math.max(0, bankWindowOrder.size() - 1));
                activateBankWindow(target, isBankApp(target));
                activeWindow = WindowMode.BANK_APP;
            } else if (createWindowOpen) {
                activeWindow = WindowMode.CREATE_BANK;
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
        if (action.startsWith("SHOP_")) {
            if (payload.success()
                    && ("SHOP_CLAIM_TOOL_PLOT".equals(action)
                    || "SHOP_CLAIM_TOOL_STOCKROOM".equals(action)
                    || "SHOP_CLAIM_TOOL_PALLETS".equals(action)
                    || "SHOP_LINK_CASHIER_TERMINAL".equals(action))) {
                closeEntirePcUi();
                return;
            }
            if (payload.success()) {
                switch (action) {
                    case "SHOP_CREATE", "SHOP_RENAME", "SHOP_SET_TYPE", "SHOP_DELETE" ->
                            PacketDistributor.sendToServer(new OpenBankOwnerPcPayload());
                    default -> {
                    }
                }
                if ("SHOP_DELETE".equals(action)) {
                    closeBankAppWindow();
                    return;
                }
                if (activeWindow == WindowMode.UTILITY_APP
                        && activeUtilityApp == UtilityApp.WEBSHOP
                        && action.startsWith("SHOP_WEBSHOP_")
                        && !"SHOP_WEBSHOP_REPORT".equals(action)) {
                    sendDesktopAction("SHOP_WEBSHOP_REPORT", "", "");
                }
            }
            if (activeWindow == WindowMode.BANK_APP && isActiveShopApp()) {
                if ("SHOP_CREATE".equals(action) && payload.success()) {
                    createShopWindowOpen = false;
                }
                if (payload.success()) {
                    if ("SHOP_LEVEL_ROADMAP".equals(action)) {
                        shopLevelRoadmapOpen = true;
                        shopLevelRoadmapSelectedNode = null;
                        shopLevelRoadmapScrollX = 0;
                        shopLevelRoadmapMaxScrollX = 0;
                        shopLevelRoadmapScrollbarDragging = false;
                        outputScroll = 0;
                    }
                    if ("SHOP_HIRE_CASHIER".equals(action)
                            || "SHOP_FIRE_EMPLOYEE".equals(action)
                            || "SHOP_SCAN_CASHIERS".equals(action)) {
                        sendShopDesktopAction("SHOP_LIST_EMPLOYEES", "");
                    } else if ("SHOP_ORDER_ITEM_PICKER".equals(action)) {
                        shopOrderPalletPickerOpen = false;
                        shopOrderPickerOpen = payload.success();
                        outputScroll = 0;
                    } else if ("SHOP_PERMISSIONS_SET".equals(action)
                            || "SHOP_PERMISSIONS_REMOVE".equals(action)) {
                        sendShopDesktopAction("SHOP_PERMISSIONS_REPORT", "");
                    } else if ("SHOP_ORDER_CREATE".equals(action)
                            || "SHOP_ORDER_CANCEL".equals(action)
                            || "SHOP_ORDER_ASSIGN_PALLET".equals(action)
                            || "SHOP_ORDER_UNASSIGN_PALLET".equals(action)
                            || "SHOP_ORDER_BIND_PALLET".equals(action)
                            || "SHOP_ORDER_CLEAR_PALLET".equals(action)) {
                        shopOrderPalletPickerOpen = false;
                        shopOrderPickerOpen = false;
                        sendShopDesktopAction("SHOP_ORDER_REPORT", "");
                    } else if ("SHOP_SET_SETTLEMENT_ACCOUNT".equals(action)
                            || "SHOP_SET_CHECKOUT_TERMINAL".equals(action)) {
                        shopSettlementPickerOpen = false;
                        shopSelectedSettlementAccountId = "";
                        sendShopDesktopAction("SHOP_FINANCE_REPORT", "");
                    } else if ("SHOP_VAULT_WITHDRAW_AMOUNT".equals(action)
                            || "SHOP_VAULT_WITHDRAW_PLAN".equals(action)) {
                        if (shopVaultPlanEditOpen) {
                            sendShopDesktopAction("SHOP_SHOW_CASH_VAULT", "");
                        } else {
                            sendShopDesktopAction("SHOP_FINANCE_REPORT", "");
                        }
                    } else if ("SHOP_STOCKROOM_REPORT".equals(action)) {
                        if (activeSection == Section.LIMITS) {
                            shopStockroomViewOpen = true;
                            outputScroll = 0;
                        }
                    } else if ("SHOP_HOURS_LIGHTING_REPORT".equals(action)) {
                        applyShopHoursLightingTokens(payload.message());
                    } else if ("SHOP_HOURS_SET".equals(action)
                            || "SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS".equals(action)
                            || "SHOP_LIGHTING_ENABLED".equals(action)
                            || "SHOP_LIGHTING_LEVEL".equals(action)
                            || "SHOP_LIGHTING_MAIN_MODE".equals(action)
                            || "SHOP_LIGHTING_STOCKROOM_MODE".equals(action)
                            || "SHOP_LIGHTING_EXCLUDE_STOCKROOM".equals(action)) {
                        sendShopDesktopAction("SHOP_HOURS_LIGHTING_REPORT", "");
                    } else if ("SHOP_RESTOCK_SLOT".equals(action)
                            || "SHOP_RESTOCK".equals(action)
                            || "SHOP_RESTOCK_LOW".equals(action)
                            || "SHOP_RESTOCK_SHELF".equals(action)
                            || "SHOP_REMOVE_SHELF_SLOT".equals(action)
                            || "SHOP_SET_SLOT_TARGETS".equals(action)) {
                        sendShopDesktopAction("SHOP_SCAN", "");
                    }
                }
                rebuildWidgets();
            } else if (activeWindow == WindowMode.CREATE_SHOP) {
                if ("SHOP_CREATE".equals(action) && payload.success()) {
                    createShopWindowOpen = false;
                    activeWindow = WindowMode.DESKTOP;
                }
                rebuildWidgets();
            }
            return;
        }
        if (action.startsWith("ORDER_BOARD_")) {
            if (activeWindow == WindowMode.UTILITY_APP && activeUtilityApp == UtilityApp.ORDER_BOARD) {
                if (payload.success() && ("ORDER_BOARD_ACCEPT".equals(action) || "ORDER_BOARD_CANCEL".equals(action))) {
                    // Accept/Cancel already returns feedback; silent-refresh the board content afterwards.
                    ClientOwnerPcData.suppressNextOrderBoardReportToast();
                    sendDesktopAction("ORDER_BOARD_REPORT", "", "");
                }
                rebuildWidgets();
            }
            return;
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollDelta) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (desktopAuthenticated
                && taskbarMaxScroll > 0
                && localMouseX >= taskbarViewportX && localMouseX <= (taskbarViewportX + taskbarViewportW)
                && localMouseY >= taskbarViewportY && localMouseY <= (taskbarViewportY + taskbarViewportH)) {
            int previous = taskbarScroll;
            int step = 56;
            if (scrollDelta < 0) {
                taskbarScroll = Math.min(taskbarMaxScroll, taskbarScroll + step);
            } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    notepadScroll = Math.min(maxScroll, previous + 2);
                } else if (scrollDelta > 0) {
                    notepadScroll = Math.max(0, previous - 2);
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.FILE_EXPLORER
                    && localMouseX >= explorerFileListX && localMouseX <= (explorerFileListX + explorerFileListW)
                    && localMouseY >= explorerFileListY && localMouseY <= (explorerFileListY + explorerFileListH)
                    && explorerFilesMaxScroll > 0) {
                int previous = explorerFilesScroll;
                if (scrollDelta < 0) {
                    explorerFilesScroll = Math.min(explorerFilesMaxScroll, explorerFilesScroll + 1);
                } else if (scrollDelta > 0) {
                    explorerFilesScroll = Math.max(0, explorerFilesScroll - 1);
                }
                if (previous != explorerFilesScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.SHOP_MANAGER
                    && localMouseX >= shopManagerViewportX && localMouseX <= (shopManagerViewportX + shopManagerViewportW)
                    && localMouseY >= shopManagerViewportY && localMouseY <= (shopManagerViewportY + shopManagerViewportH)
                    && shopManagerMaxScroll > 0) {
                int previous = shopManagerScroll;
                ShopDashboardSnapshot snapshot = parseShopDashboardSnapshot(ClientOwnerPcData.getActionOutputLines());
                int step = snapshot != null ? 14 : 2;
                if (scrollDelta < 0) {
                    shopManagerScroll = Math.min(shopManagerMaxScroll, shopManagerScroll + step);
                } else if (scrollDelta > 0) {
                    shopManagerScroll = Math.max(0, shopManagerScroll - step);
                }
                if (previous != shopManagerScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.ORDER_BOARD
                    && localMouseX >= orderBoardViewportX && localMouseX <= (orderBoardViewportX + orderBoardViewportW)
                    && localMouseY >= orderBoardViewportY && localMouseY <= (orderBoardViewportY + orderBoardViewportH)
                    && orderBoardMaxScroll > 0) {
                int previous = orderBoardScroll;
                int step = 14;
                if (scrollDelta < 0) {
                    orderBoardScroll = Math.min(orderBoardMaxScroll, orderBoardScroll + step);
                } else if (scrollDelta > 0) {
                    orderBoardScroll = Math.max(0, orderBoardScroll - step);
                }
                if (previous != orderBoardScroll) {
                    rebuildWidgets();
                }
                return true;
            }
            if (activeUtilityApp == UtilityApp.WEBSHOP
                    && localMouseX >= webshopViewportX && localMouseX <= (webshopViewportX + webshopViewportW)
                    && localMouseY >= webshopViewportY && localMouseY <= (webshopViewportY + webshopViewportH)
                    && webshopMaxScroll > 0) {
                int previous = webshopScroll;
                int step = 14;
                if (scrollDelta < 0) {
                    webshopScroll = Math.min(webshopMaxScroll, webshopScroll + step);
                } else if (scrollDelta > 0) {
                    webshopScroll = Math.max(0, webshopScroll - step);
                }
                if (previous != webshopScroll) {
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
                if (scrollDelta < 0) {
                    systemHideAppsScroll = Math.min(systemHideAppsMaxScroll, systemHideAppsScroll + 1);
                } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    systemMonitorScroll = Math.min(systemMonitorMaxScroll, systemMonitorScroll + step);
                } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    paintControlsScroll = Math.min(paintControlsMaxScroll, paintControlsScroll + step);
                } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    paintBrushSize = Math.max(1, paintBrushSize - 1);
                } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    navScroll = Math.min(navMaxScroll, navScroll + step);
                } else if (scrollDelta > 0) {
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
                if (scrollDelta < 0) {
                    sectionScroll = Math.min(sectionMaxScroll, sectionScroll + step);
                } else if (scrollDelta > 0) {
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
                if (isActiveShopApp()) {
                    if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
                        if (shopLevelRoadmapMaxScrollX > 0) {
                            int roadmapStep = 84;
                            if (scrollDelta < 0) {
                                shopLevelRoadmapScrollX = Math.min(shopLevelRoadmapMaxScrollX, shopLevelRoadmapScrollX + roadmapStep);
                            } else if (scrollDelta > 0) {
                                shopLevelRoadmapScrollX = Math.max(0, shopLevelRoadmapScrollX - roadmapStep);
                            }
                        }
                        return true;
                    }
                    List<String> rawShopLines = ClientOwnerPcData.getActionOutputLines();
                    ShopDashboardSnapshot snapshot = activeSection == Section.OVERVIEW
                            ? parseShopDashboardSnapshot(rawShopLines)
                            : null;
                    List<ShopEmployeeCardData> employeeCards = activeSection == Section.STAFFING
                            ? parseShopEmployeeCards(rawShopLines)
                            : List.of();
                    List<ShopOwnerAccountCardData> ownerAccounts = activeSection == Section.LENDING && shopSettlementPickerOpen
                            ? parseShopOwnerAccountCards(rawShopLines)
                            : List.of();
                    List<ShopPermissionRoleHeaderData> permissionHeaders = activeSection == Section.PERMISSIONS
                            ? parseShopPermissionRoleHeaders(rawShopLines)
                            : List.of();
                    List<ShopPermissionMemberCardData> permissionMembers = activeSection == Section.PERMISSIONS
                            ? parseShopPermissionMemberCards(rawShopLines)
                            : List.of();
                    List<ShopInventoryShelfCardData> inventoryCards = activeSection == Section.LIMITS
                            ? parseShopInventoryCards(rawShopLines)
                            : List.of();
                    List<ShopStockroomItemCardData> stockroomCards = activeSection == Section.LIMITS && shopStockroomViewOpen
                            ? parseShopStockroomCards(rawShopLines)
                            : List.of();
                    List<ShopOrderCardData> orderCards = activeSection == Section.GOVERNANCE
                            ? parseShopOrderCards(rawShopLines)
                            : List.of();
                    List<ShopOrderPalletCardData> orderPalletCards = activeSection == Section.GOVERNANCE
                            ? parseShopOrderPalletCards(rawShopLines)
                            : List.of();
                    List<ShopOrderPickCardData> orderPickCards = activeSection == Section.GOVERNANCE && shopOrderPickerOpen
                            ? parseShopOrderPickCards(rawShopLines)
                            : List.of();
                    if (activeSection == Section.GOVERNANCE && shopOrderPickerOpen) {
                        orderPickCards = filterShopOrderPickCards(orderPickCards);
                    }
                    OrderBoardSummary orderSummary = activeSection == Section.GOVERNANCE
                            ? parseOrderBoardSummary(rawShopLines)
                            : null;
                    ShopFinanceSnapshot financeSnapshot = activeSection == Section.LENDING
                            ? parseShopFinanceSnapshot(rawShopLines)
                            : null;
                    ShopVaultSnapshot vaultSnapshot = activeSection == Section.LENDING
                            ? parseShopVaultSnapshot(rawShopLines)
                            : null;
                    if (snapshot != null) {
                        int contentHeight = getShopDashboardContentHeight(bodyWidth, bodyHeight);
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (shopSettlementPickerOpen && activeSection == Section.LENDING) {
                        int contentHeight = getShopOwnerAccountCardsContentHeight(bodyWidth, bodyHeight, ownerAccounts.size());
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (activeSection == Section.PERMISSIONS) {
                        int contentHeight = getShopPermissionRoleCardsContentHeight(
                                bodyWidth,
                                bodyHeight,
                                permissionHeaders,
                                filterShopPermissionMemberCards(permissionMembers)
                        );
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (shopVaultPlanEditOpen && activeSection == Section.LENDING) {
                        int contentHeight = getShopVaultPlanEditorContentHeight(bodyWidth, bodyHeight);
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (activeSection == Section.LIMITS) {
                        int contentHeight = shopStockroomViewOpen
                                ? getShopStockroomCardsContentHeight(bodyWidth, bodyHeight, stockroomCards.size())
                                : getShopInventoryCardsContentHeight(bodyWidth, bodyHeight, inventoryCards);
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (activeSection == Section.GOVERNANCE) {
                        int contentHeight = getShopOrderManagerContentHeight(
                                bodyWidth,
                                bodyHeight,
                                orderCards.size(),
                                orderPalletCards.size(),
                                orderPickCards.size(),
                                orderSummary
                        );
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (!employeeCards.isEmpty()) {
                        int contentHeight = getShopEmployeeCardsContentHeight(bodyWidth, bodyHeight, employeeCards.size());
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (financeSnapshot != null) {
                        int contentHeight = getShopFinanceContentHeight(bodyWidth, bodyHeight);
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else if (vaultSnapshot != null) {
                        int contentHeight = getShopVaultContentHeight(bodyWidth, bodyHeight, vaultSnapshot.counts().size());
                        maxScroll = Math.max(0, contentHeight - bodyHeight);
                        step = OUTPUT_PIXEL_SCROLL_STEP;
                    } else {
                        List<String> lines = getWrappedOutputLines();
                        if (lines.isEmpty()) {
                            lines = wrapLines(getShopSectionHintLines(), Math.max(80, bodyWidth - 12));
                        }
                        int visible = Math.max(1, (bodyHeight - 8) / LINE_HEIGHT);
                        maxScroll = Math.max(0, lines.size() - visible);
                    }
                } else if (activeSection == Section.LENDING && lendingMarketOpen) {
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
                    if (scrollDelta < 0) {
                        outputScroll = Math.min(maxScroll, outputScroll + step);
                    } else if (scrollDelta > 0) {
                        outputScroll = Math.max(0, outputScroll - step);
                    }
                    return true;
                }
            }
        }
        return super.mouseScrolled(localMouseX, localMouseY, scrollDelta);
    }

    private int computeOutputMaxScroll() {
        if (activeWindow != WindowMode.BANK_APP) {
            return 0;
        }
        int bodyWidth = Math.max(1, outputPanelW - (OUTPUT_PANEL_INSET * 2));
        int bodyHeight = Math.max(1, outputPanelH - (OUTPUT_PANEL_INSET * 2));
        OwnerPcBankDataPayload data = ClientOwnerPcData.getCurrentBankData();
        InputHelp help = getFocusedInputHelp();
        if (isActiveShopApp()) {
            if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
                return 0;
            }
            List<String> rawShopLines = ClientOwnerPcData.getActionOutputLines();
            ShopDashboardSnapshot snapshot = activeSection == Section.OVERVIEW
                    ? parseShopDashboardSnapshot(rawShopLines)
                    : null;
            List<ShopEmployeeCardData> employeeCards = activeSection == Section.STAFFING
                    ? parseShopEmployeeCards(rawShopLines)
                    : List.of();
            List<ShopOwnerAccountCardData> ownerAccounts = activeSection == Section.LENDING && shopSettlementPickerOpen
                    ? parseShopOwnerAccountCards(rawShopLines)
                    : List.of();
            List<ShopPermissionRoleHeaderData> permissionHeaders = activeSection == Section.PERMISSIONS
                    ? parseShopPermissionRoleHeaders(rawShopLines)
                    : List.of();
            List<ShopPermissionMemberCardData> permissionMembers = activeSection == Section.PERMISSIONS
                    ? parseShopPermissionMemberCards(rawShopLines)
                    : List.of();
            List<ShopInventoryShelfCardData> inventoryCards = activeSection == Section.LIMITS
                    ? parseShopInventoryCards(rawShopLines)
                    : List.of();
            List<ShopStockroomItemCardData> stockroomCards = activeSection == Section.LIMITS && shopStockroomViewOpen
                    ? parseShopStockroomCards(rawShopLines)
                    : List.of();
            List<ShopOrderCardData> orderCards = activeSection == Section.GOVERNANCE
                    ? parseShopOrderCards(rawShopLines)
                    : List.of();
            List<ShopOrderPalletCardData> orderPalletCards = activeSection == Section.GOVERNANCE
                    ? parseShopOrderPalletCards(rawShopLines)
                    : List.of();
            List<ShopOrderPickCardData> orderPickCards = activeSection == Section.GOVERNANCE && shopOrderPickerOpen
                    ? parseShopOrderPickCards(rawShopLines)
                    : List.of();
            if (activeSection == Section.GOVERNANCE && shopOrderPickerOpen) {
                orderPickCards = filterShopOrderPickCards(orderPickCards);
            }
            OrderBoardSummary orderSummary = activeSection == Section.GOVERNANCE
                    ? parseOrderBoardSummary(rawShopLines)
                    : null;
            ShopFinanceSnapshot financeSnapshot = activeSection == Section.LENDING
                    ? parseShopFinanceSnapshot(rawShopLines)
                    : null;
            ShopVaultSnapshot vaultSnapshot = activeSection == Section.LENDING
                    ? parseShopVaultSnapshot(rawShopLines)
                    : null;
            int contentHeight;
            if (snapshot != null) {
                contentHeight = getShopDashboardContentHeight(bodyWidth, bodyHeight);
            } else if (shopSettlementPickerOpen && activeSection == Section.LENDING) {
                contentHeight = getShopOwnerAccountCardsContentHeight(bodyWidth, bodyHeight, ownerAccounts.size());
            } else if (activeSection == Section.PERMISSIONS) {
                contentHeight = getShopPermissionRoleCardsContentHeight(
                        bodyWidth,
                        bodyHeight,
                        permissionHeaders,
                        filterShopPermissionMemberCards(permissionMembers)
                );
            } else if (shopVaultPlanEditOpen && activeSection == Section.LENDING) {
                contentHeight = getShopVaultPlanEditorContentHeight(bodyWidth, bodyHeight);
            } else if (activeSection == Section.LIMITS) {
                contentHeight = shopStockroomViewOpen
                        ? getShopStockroomCardsContentHeight(bodyWidth, bodyHeight, stockroomCards.size())
                        : getShopInventoryCardsContentHeight(bodyWidth, bodyHeight, inventoryCards);
            } else if (activeSection == Section.GOVERNANCE) {
                contentHeight = getShopOrderManagerContentHeight(
                        bodyWidth,
                        bodyHeight,
                        orderCards.size(),
                        orderPalletCards.size(),
                        orderPickCards.size(),
                        orderSummary
                );
            } else if (!employeeCards.isEmpty()) {
                contentHeight = getShopEmployeeCardsContentHeight(bodyWidth, bodyHeight, employeeCards.size());
            } else if (financeSnapshot != null) {
                contentHeight = getShopFinanceContentHeight(bodyWidth, bodyHeight);
            } else if (vaultSnapshot != null) {
                contentHeight = getShopVaultContentHeight(bodyWidth, bodyHeight, vaultSnapshot.counts().size());
            } else {
                List<String> lines = getWrappedOutputLines();
                if (lines.isEmpty()) {
                    lines = wrapLines(getShopSectionHintLines(), Math.max(80, bodyWidth - 12));
                }
                int visible = Math.max(1, (bodyHeight - 8) / LINE_HEIGHT);
                return Math.max(0, lines.size() - visible);
            }
            return Math.max(0, contentHeight - bodyHeight);
        }
        if (activeSection == Section.LENDING && lendingMarketOpen) {
            int listHeight = Math.max(32, bodyHeight - 30);
            int cardH = 76;
            int gap = 10;
            int cols = bodyWidth >= 620 ? 2 : 1;
            int rows = (getSortedMarketOffers().size() + cols - 1) / cols;
            int visibleRows = Math.max(1, (listHeight + gap) / (cardH + gap));
            return Math.max(0, rows - visibleRows);
        }
        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && isOverviewMetricsAction(overviewDetailAction)
                && data != null) {
            int contentHeight = getOverviewDashboardContentHeight(bodyWidth, bodyHeight);
            return Math.max(0, contentHeight - bodyHeight);
        }
        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && "SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction)
                && accountProfileOpen
                && selectedAccountCard != null) {
            int contentHeight = getAccountProfileContentHeight(bodyWidth, bodyHeight);
            return Math.max(0, contentHeight - bodyHeight);
        }
        if (activeSection == Section.OVERVIEW
                && overviewDetailOpen
                && !isOverviewMetricsAction(overviewDetailAction)
                && !("SHOW_ACCOUNTS".equalsIgnoreCase(overviewDetailAction) && accountProfileOpen)
                && data != null) {
            int cols = bodyWidth >= 520 ? 2 : 1;
            int cardH = 46;
            int gap = 8;
            int rows = (extractOverviewCardEntries(overviewDetailAction).size() + cols - 1) / cols;
            int visibleRows = Math.max(1, (bodyHeight + gap) / (cardH + gap));
            return Math.max(0, rows - visibleRows);
        }
        if (help != null && (activeSection == Section.LIMITS
                || (activeSection == Section.LENDING && !lendingMarketOpen))) {
            int contentHeight = getInputHelpContentHeight(help, bodyWidth, bodyHeight);
            return Math.max(0, contentHeight - bodyHeight);
        }
        List<String> wrapped = getWrappedOutputLines();
        int visible = Math.max(1, (outputPanelH - 10) / LINE_HEIGHT);
        return Math.max(0, wrapped.size() - visible);
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
        if (button == 0 && tryStartScrollbarDrag(localMouseX, localMouseY)) {
            return true;
        }
        if (activeWindow == WindowMode.UTILITY_APP) {
            if (activeUtilityApp == UtilityApp.ORDER_BOARD
                    && button == 0
                    && !visibleOrderBoardCards.isEmpty()) {
                for (OrderBoardCardHitbox cardHitbox : visibleOrderBoardCards) {
                    if (!cardHitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    OrderBoardCardData card = cardHitbox.order();
                    if (card == null || card.orderId() == null || card.orderId().isBlank()) {
                        return true;
                    }
                    orderBoardSelectedOrderId = card.orderId().trim();
                    ClientOwnerPcData.setToast(true, "Selected order " + orderBoardSelectedOrderId + ".");
                    rebuildWidgets();
                    return true;
                }
            }
            if (activeUtilityApp == UtilityApp.WEBSHOP && button == 0) {
                if (!isInsideWebshopViewport(localMouseX, localMouseY)) {
                    return super.mouseClicked(localMouseX, localMouseY, button);
                }
                for (WebshopCatalogCardHitbox hitbox : visibleWebshopCatalogCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopCatalogCardData card = hitbox.card();
                    if (card == null || card.itemId() == null || card.itemId().isBlank()) {
                        return true;
                    }
                    webshopSelectedCatalogItemId = card.itemId().trim();
                    webshopSelectedCatalogItemName = card.itemName() == null ? webshopSelectedCatalogItemId : card.itemName().trim();
                    ClientOwnerPcData.setToast(true, "Selected catalog item " + webshopSelectedCatalogItemName + ".");
                    rebuildWidgets();
                    return true;
                }
                for (WebshopCartCardHitbox hitbox : visibleWebshopCartCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopCartCardData card = hitbox.card();
                    if (card == null || card.itemId() == null || card.itemId().isBlank()) {
                        return true;
                    }
                    webshopSelectedCartItemId = card.itemId().trim();
                    ClientOwnerPcData.setToast(true, "Selected cart item " + (card.itemName() == null ? webshopSelectedCartItemId : card.itemName()) + ".");
                    rebuildWidgets();
                    return true;
                }
                for (WebshopAccountCardHitbox hitbox : visibleWebshopAccountCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopAccountCardData card = hitbox.card();
                    if (card == null || card.accountId() == null || card.accountId().isBlank()) {
                        return true;
                    }
                    webshopSelectedAccountId = card.accountId().trim();
                    sendDesktopAction("SHOP_WEBSHOP_SELECT_ACCOUNT", webshopSelectedAccountId, "");
                    return true;
                }
                for (WebshopShopCardHitbox hitbox : visibleWebshopShopCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopShopCardData card = hitbox.card();
                    if (card == null || card.shopId() == null || card.shopId().isBlank()) {
                        return true;
                    }
                    webshopSelectedShopId = card.shopId().trim();
                    sendDesktopAction("SHOP_WEBSHOP_SELECT_SHOP", webshopSelectedShopId, "");
                    return true;
                }
                for (WebshopPalletCardHitbox hitbox : visibleWebshopPalletCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopPalletCardData card = hitbox.card();
                    if (card == null || card.palletId() == null || card.palletId().isBlank()) {
                        return true;
                    }
                    if (card.full()) {
                        ClientOwnerPcData.setToast(false, "Selected delivery pallet is currently full.");
                        return true;
                    }
                    webshopSelectedPalletId = card.palletId().trim();
                    sendDesktopAction("SHOP_WEBSHOP_SELECT_PALLET", webshopSelectedPalletId, "");
                    return true;
                }
                for (WebshopOrderCardHitbox hitbox : visibleWebshopOrderCards) {
                    if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                            || !hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    WebshopOrderCardData card = hitbox.card();
                    if (card == null || card.orderId() == null || card.orderId().isBlank()) {
                        return true;
                    }
                    webshopSelectedOrderId = card.orderId().trim();
                    ClientOwnerPcData.setToast(true, "Selected order " + shortUuid(webshopSelectedOrderId) + ".");
                    rebuildWidgets();
                    return true;
                }
            }
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
                && isActiveShopApp()
                && activeSection == Section.OVERVIEW
                && shopLevelRoadmapOpen) {
            if (shopLevelRoadmapSelectedNode != null) {
                if (shopLevelRoadmapModalCloseHitbox != null
                        && shopLevelRoadmapModalCloseHitbox.contains(localMouseX, localMouseY)) {
                    shopLevelRoadmapSelectedNode = null;
                    shopLevelRoadmapModalCloseHitbox = null;
                    return true;
                }
                if (localMouseX >= outputPanelX && localMouseX <= (outputPanelX + outputPanelW)
                        && localMouseY >= outputPanelY && localMouseY <= (outputPanelY + outputPanelH)) {
                    return true;
                }
            }

            if (shopLevelRoadmapScrollbarThumbHitbox != null
                    && shopLevelRoadmapScrollbarThumbHitbox.contains(localMouseX, localMouseY)) {
                shopLevelRoadmapScrollbarDragging = true;
                shopLevelRoadmapScrollbarDragOffsetX = (int) Math.round(localMouseX) - shopLevelRoadmapScrollbarThumbHitbox.x();
                return true;
            }
            if (shopLevelRoadmapScrollbarTrackHitbox != null
                    && shopLevelRoadmapScrollbarTrackHitbox.contains(localMouseX, localMouseY)) {
                int thumbW = shopLevelRoadmapScrollbarThumbHitbox == null
                        ? Math.max(20, shopLevelRoadmapScrollbarTrackHitbox.width() / 6)
                        : shopLevelRoadmapScrollbarThumbHitbox.width();
                updateShopLevelRoadmapScrollFromThumbLeft((int) Math.round(localMouseX) - (thumbW / 2));
                shopLevelRoadmapScrollbarDragging = true;
                shopLevelRoadmapScrollbarDragOffsetX = thumbW / 2;
                return true;
            }
            if (localMouseX >= outputPanelX && localMouseX <= (outputPanelX + outputPanelW)
                    && localMouseY >= outputPanelY && localMouseY <= (outputPanelY + outputPanelH)) {
                for (ShopRoadmapNodeHitbox hitbox : visibleShopLevelRoadmapNodes) {
                    if (!hitbox.contains(localMouseX, localMouseY)) {
                        continue;
                    }
                    shopLevelRoadmapSelectedNode = hitbox.node();
                    return true;
                }
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.LENDING
                && shopSettlementPickerOpen
                && !visibleShopOwnerAccountCards.isEmpty()) {
            for (ShopOwnerAccountCardHitbox cardHitbox : visibleShopOwnerAccountCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopOwnerAccountCardData account = cardHitbox.account();
                if (account == null || account.accountId() == null || account.accountId().isBlank()) {
                    return true;
                }
                shopSelectedSettlementAccountId = account.accountId().trim();
                ClientOwnerPcData.setToast(true, "Selected settlement account " + shopSelectedSettlementAccountId + ".");
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.PERMISSIONS
                && !visibleShopPermissionMemberCards.isEmpty()) {
            for (ShopPermissionMemberCardHitbox cardHitbox : visibleShopPermissionMemberCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopPermissionMemberCardData player = cardHitbox.member();
                if (player == null || player.playerId() == null || player.playerId().isBlank()) {
                    return true;
                }
                formValues.put(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, player.playerId().trim());
                formValues.put(SHOP_PERMISSIONS_SELECTED_PLAYER_NAME_KEY, player.playerName() == null ? "" : player.playerName().trim());
                if (player.assignedRole() != null
                        && !player.assignedRole().isBlank()
                        && ShopService.SHOP_PERMISSION_ROLES.contains(player.assignedRole())) {
                    formValues.put(SHOP_PERMISSIONS_ROLE_KEY, player.assignedRole());
                }
                String selectedLabel = player.playerName() == null || player.playerName().isBlank()
                        ? shortUuid(player.playerId())
                        : player.playerName();
                ClientOwnerPcData.setToast(true, "Selected player " + selectedLabel + ".");
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.LENDING
                && shopVaultPlanEditOpen
                && !visibleShopVaultAdjustActions.isEmpty()) {
            ShopVaultSnapshot vaultSnapshot = parseShopVaultSnapshot(ClientOwnerPcData.getActionOutputLines());
            int[] available = new int[SHOP_CASH_DENOMINATIONS.length];
            if (vaultSnapshot != null) {
                for (int i = 0; i < available.length && i < vaultSnapshot.counts().size(); i++) {
                    available[i] = Math.max(0, vaultSnapshot.counts().get(i) == null ? 0 : vaultSnapshot.counts().get(i));
                }
            }
            for (ShopVaultAdjustHitbox hitbox : visibleShopVaultAdjustActions) {
                if (!hitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                int idx = hitbox.index();
                if (idx < 0 || idx >= shopVaultRequestedCounts.length) {
                    return true;
                }
                int current = Math.max(0, shopVaultRequestedCounts[idx]);
                if (hitbox.increase()) {
                    int maxAllowed = Math.max(0, available[idx]);
                    if (current >= maxAllowed) {
                        ClientOwnerPcData.setToast(false, "No more of that denomination is available in the cash vault.");
                        return true;
                    }
                    shopVaultRequestedCounts[idx] = current + 1;
                } else {
                    if (current <= 0) {
                        return true;
                    }
                    shopVaultRequestedCounts[idx] = current - 1;
                }
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.LIMITS
                && shopStockroomViewOpen
                && !visibleShopStockroomLocateActions.isEmpty()) {
            for (ShopStockroomLocateHitbox hitbox : visibleShopStockroomLocateActions) {
                if (!hitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopStockroomItemCardData item = hitbox.item();
                if (item == null || item.locateTarget() == null || item.locateTarget().isBlank()) {
                    return true;
                }
                sendShopDesktopAction("SHOP_STOCKROOM_LOCATE", item.locateTarget());
                String itemLabel = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
                ClientOwnerPcData.setToast(true, "Locating " + itemLabel + "...");
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.GOVERNANCE) {
            for (ShopOrderCardHitbox cardHitbox : visibleShopOrderCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopOrderCardData order = cardHitbox.order();
                if (order == null || order.orderId() == null || order.orderId().isBlank()) {
                    return true;
                }
                shopOrderSelectedId = order.orderId().trim();
                ClientOwnerPcData.setToast(true, "Selected order " + shortUuid(shopOrderSelectedId) + ".");
                rebuildWidgets();
                return true;
            }
            for (ShopOrderPalletCardHitbox cardHitbox : visibleShopOrderPalletCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopOrderPalletCardData pallet = cardHitbox.pallet();
                if (pallet == null || pallet.palletRef() == null || pallet.palletRef().isBlank()) {
                    return true;
                }
                shopOrderSelectedPalletRef = pallet.palletRef().trim();
                ClientOwnerPcData.setToast(true,
                        "Selected pallet " + shortUuid(shopOrderSelectedPalletRef)
                                + " at " + pallet.x() + "," + pallet.y() + "," + pallet.z()
                                + (pallet.full() ? " (FULL)" : "") + ".");
                rebuildWidgets();
                return true;
            }
            for (ShopOrderPickCardHitbox cardHitbox : visibleShopOrderPickCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopOrderPickCardData pick = cardHitbox.pick();
                if (pick == null || pick.itemId() == null || pick.itemId().isBlank()) {
                    return true;
                }
                shopOrderSelectedItemId = pick.itemId().trim();
                shopOrderSelectedItemName = pick.itemName() == null || pick.itemName().isBlank()
                        ? shopOrderSelectedItemId
                        : pick.itemName().trim();
                ClientOwnerPcData.setToast(true, "Selected order item " + shopOrderSelectedItemName + ".");
                // Keep picker open; selection is confirmed via explicit Select Item button.
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.LIMITS
                && !shopStockroomViewOpen
                && !visibleShopInventoryActions.isEmpty()) {
            for (ShopInventoryActionHitbox actionHitbox : visibleShopInventoryActions) {
                if (!actionHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                if (!actionHitbox.enabled()) {
                    return true;
                }
                ShopInventoryItemCardData item = actionHitbox.item();
                if (item == null || item.targetKey() == null || item.targetKey().isBlank()) {
                    return true;
                }
                String action = actionHitbox.action() == null ? "" : actionHitbox.action().trim().toUpperCase(Locale.ROOT);
                String itemLabel = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
                switch (action) {
                    case "RESTOCK" -> {
                        sendShopDesktopAction("SHOP_RESTOCK_SLOT", item.targetKey());
                        ClientOwnerPcData.setToast(true, "Restocking " + itemLabel + "...");
                    }
                    case "REMOVE" -> {
                        sendShopDesktopAction("SHOP_REMOVE_SHELF_SLOT", item.targetKey());
                        ClientOwnerPcData.setToast(true, "Removing " + itemLabel + " from shelf...");
                    }
                    case "MIN_DEC", "MIN_INC", "MAX_DEC", "MAX_INC" -> {
                        int min = Math.max(0, item.minTarget());
                        int max = Math.max(min, item.maxTarget());
                        switch (action) {
                            case "MIN_DEC" -> min = Math.max(0, min - 1);
                            case "MIN_INC" -> min = Math.min(max, min + 1);
                            case "MAX_DEC" -> max = Math.max(min, max - 1);
                            case "MAX_INC" -> max = Math.min(64, max + 1);
                            default -> {
                            }
                        }
                        if (max < min) {
                            max = min;
                        }
                        sendShopDesktopAction("SHOP_SET_SLOT_TARGETS", item.targetKey() + "|" + min + "|" + max);
                        ClientOwnerPcData.setToast(true, "Updated stock targets for " + itemLabel + " to " + min + "-" + max + ".");
                    }
                    default -> {
                    }
                }
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.LIMITS
                && !shopStockroomViewOpen
                && !visibleShopInventoryShelfCards.isEmpty()) {
            for (ShopInventoryShelfSelectHitbox cardHitbox : visibleShopInventoryShelfCards) {
                if (!cardHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopInventoryShelfCardData shelf = cardHitbox.shelf();
                if (shelf == null || shelf.shelfTarget() == null || shelf.shelfTarget().isBlank()) {
                    return true;
                }
                shopInventorySelectedShelfTarget = shelf.shelfTarget().trim();
                ClientOwnerPcData.setToast(true, "Selected shelf #" + shelf.index() + " for bulk restock.");
                rebuildWidgets();
                return true;
            }
        }

        if (button == 0
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.STAFFING
                && !visibleShopEmployeeActions.isEmpty()) {
            for (ShopEmployeeActionHitbox actionHitbox : visibleShopEmployeeActions) {
                if (!actionHitbox.contains(localMouseX, localMouseY)) {
                    continue;
                }
                ShopEmployeeCardData employee = actionHitbox.employee();
                if (employee == null) {
                    return true;
                }
                if ("COPY".equals(actionHitbox.action())) {
                    Minecraft mc = this.minecraft != null ? this.minecraft : Minecraft.getInstance();
                    if (mc != null && mc.keyboardHandler != null) {
                        mc.keyboardHandler.setClipboard(employee.employeeId());
                    }
                    ClientOwnerPcData.setToast(true, "Copied employee ID " + employee.employeeId() + ".");
                    return true;
                }
                if ("FIRE".equals(actionHitbox.action())) {
                    sendShopDesktopAction("SHOP_FIRE_EMPLOYEE", employee.employeeId());
                    ClientOwnerPcData.setToast(true, "Firing employee " + employee.employeeId() + "...");
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
        if (button == 0) {
            activeScrollbarDrag = null;
            shopLevelRoadmapScrollbarDragging = false;
        }
        if (activeWindow == WindowMode.UTILITY_APP && activeUtilityApp == UtilityApp.PAINT) {
            paintDrawing = false;
        }
        return super.mouseReleased(toLocalX(mouseX), toLocalY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double localMouseX = toLocalX(mouseX);
        double localMouseY = toLocalY(mouseY);
        if (button == 0 && activeScrollbarDrag != null && updateScrollbarDrag(localMouseY)) {
            return true;
        }
        if (button == 0
                && shopLevelRoadmapScrollbarDragging
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.OVERVIEW
                && shopLevelRoadmapOpen) {
            int targetThumbLeft = (int) Math.round(localMouseX) - shopLevelRoadmapScrollbarDragOffsetX;
            updateShopLevelRoadmapScrollFromThumbLeft(targetThumbLeft);
            return true;
        }
        if ((button == 0 || button == 1)
                && activeWindow == WindowMode.BANK_APP
                && isActiveShopApp()
                && activeSection == Section.OVERVIEW
                && shopLevelRoadmapOpen
                && shopLevelRoadmapSelectedNode == null
                && shopLevelRoadmapMaxScrollX > 0
                && localMouseX >= outputPanelX && localMouseX <= (outputPanelX + outputPanelW)
                && localMouseY >= outputPanelY && localMouseY <= (outputPanelY + outputPanelH)) {
            int previous = shopLevelRoadmapScrollX;
            int deltaX = (int) Math.round(toLocalDeltaX(dragX));
            if (deltaX != 0) {
                shopLevelRoadmapScrollX = Math.max(0, Math.min(shopLevelRoadmapMaxScrollX, shopLevelRoadmapScrollX - deltaX));
            }
            return previous != shopLevelRoadmapScrollX;
        }
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
        activeScrollbarDrag = null;
        shopLevelRoadmapScrollbarDragging = false;
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
        createShopWindowOpen = false;
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
            mc.resizeDisplay();
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
                mc.resizeDisplay();
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
        visibleScrollbars.clear();
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

        graphics.drawString(this.font, "UBS Commerce Desktop", left + 10, top + 9, 0xFF1E324E, false);
        int desktopAppCount = ClientOwnerPcData.getApps().size() + DESKTOP_UTILITY_APPS.size();
        int ownedShopCount = countOwnedShopApps();
        graphics.drawString(this.font,
                "Apps: " + desktopAppCount + "   Banks: "
                        + ClientOwnerPcData.getOwnedCount() + "/" + ClientOwnerPcData.getMaxBanks()
                        + "   Shops: " + ownedShopCount + "/" + MAX_SHOPS_PER_PLAYER,
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
        } else if (activeWindow == WindowMode.CREATE_SHOP) {
            drawCreateShopWindowFrame(graphics);
        } else if (activeWindow == WindowMode.UTILITY_APP) {
            drawUtilityWindowFrame(graphics);
        } else {
            drawDesktopHints(graphics);
        }

        super.render(graphics, localMouseX, localMouseY, partialTicks);
        drawSectionTextLabels(graphics);
        drawViewportMasks(graphics);
        drawTaskbarClockAndPower(graphics, localMouseX, localMouseY);
        drawKpiHoverTooltip(graphics, localMouseX, localMouseY);
        drawShopInventoryActionHoverTooltip(graphics, localMouseX, localMouseY);
        drawShopOperationsHoverTooltip(graphics, localMouseX, localMouseY);
        drawOrderBoardKpiHoverTooltip(graphics, localMouseX, localMouseY);
        drawOrderBoardHoverTooltip(graphics, localMouseX, localMouseY);
        drawWebshopKpiHoverTooltip(graphics, localMouseX, localMouseY);
        drawWebshopCardHoverTooltip(graphics, localMouseX, localMouseY);
        drawWebshopControlHoverTooltip(graphics, localMouseX, localMouseY);
        if (useVirtualScale) {
            graphics.pose().popPose();
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        // No-op to hard-disable vanilla menu blur/background behavior.
    }

    private void drawSectionTextLabels(GuiGraphics graphics) {
        if (visibleSectionTextLabels.isEmpty()) {
            return;
        }
        if (!useVirtualScale) {
            enableScaledScissor(
                    graphics,
                    sectionViewportX,
                    sectionViewportY,
                    sectionViewportX + sectionViewportW,
                    sectionViewportY + sectionViewportH
            );
        }
        // Draw section headers as plain text labels (not widgets/buttons).
        for (SectionTextLabel label : visibleSectionTextLabels) {
            if (label == null || label.text() == null || label.text().isBlank()) {
                continue;
            }
            graphics.drawString(this.font, label.text(), label.x(), label.y(), label.color(), false);
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
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
            computerLabel = "UBS Business Manager PC";
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
                    "No bank/shop apps available. Create one or obtain access.",
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
                    ScrollbarTarget.NAV,
                    true,
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
                    ScrollbarTarget.SECTION,
                    true,
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
                                       ScrollbarTarget target,
                                       boolean rebuildOnDrag,
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
        registerScrollbar(target, x, y, width, height, position, maxPosition, thumbH, rebuildOnDrag);
    }

    private void registerScrollbar(ScrollbarTarget target,
                                   int x,
                                   int y,
                                   int width,
                                   int height,
                                   int position,
                                   int maxPosition,
                                   int thumbHeight,
                                   boolean rebuildOnDrag) {
        if (target == null || maxPosition <= 0 || width <= 0 || height <= 0) {
            return;
        }
        int safeThumb = Math.max(1, Math.min(height, thumbHeight));
        int safePosition = Math.max(0, Math.min(position, maxPosition));
        visibleScrollbars.put(target, new ScrollbarHitbox(
                target,
                x,
                y,
                width,
                height,
                safePosition,
                maxPosition,
                safeThumb,
                rebuildOnDrag
        ));
    }

    private boolean tryStartScrollbarDrag(double mouseX, double mouseY) {
        if (visibleScrollbars.isEmpty()) {
            return false;
        }
        for (ScrollbarHitbox hitbox : visibleScrollbars.values()) {
            if (hitbox == null || !hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            int targetPosition = getScrollbarPositionFromMouse(hitbox, mouseY);
            if (setScrollbarPosition(hitbox.target(), targetPosition) && hitbox.rebuildOnDrag()) {
                rebuildWidgets();
            }
            activeScrollbarDrag = hitbox.target();
            return true;
        }
        return false;
    }

    private boolean updateScrollbarDrag(double mouseY) {
        if (activeScrollbarDrag == null) {
            return false;
        }
        ScrollbarHitbox hitbox = visibleScrollbars.get(activeScrollbarDrag);
        if (hitbox == null || hitbox.maxPosition() <= 0) {
            return false;
        }
        int targetPosition = getScrollbarPositionFromMouse(hitbox, mouseY);
        if (setScrollbarPosition(hitbox.target(), targetPosition) && hitbox.rebuildOnDrag()) {
            rebuildWidgets();
        }
        return true;
    }

    private int getScrollbarPositionFromMouse(ScrollbarHitbox hitbox, double mouseY) {
        int travel = Math.max(1, hitbox.height() - hitbox.thumbHeight());
        double relative = mouseY - hitbox.y() - (hitbox.thumbHeight() / 2.0D);
        int raw = (int) Math.round((relative / (double) travel) * hitbox.maxPosition());
        return Math.max(0, Math.min(hitbox.maxPosition(), raw));
    }

    private boolean setScrollbarPosition(ScrollbarTarget target, int value) {
        int next = Math.max(0, value);
        return switch (target) {
            case NAV -> {
                int clamped = Math.min(navMaxScroll, next);
                if (clamped == navScroll) {
                    yield false;
                }
                navScroll = clamped;
                yield true;
            }
            case SECTION -> {
                int clamped = Math.min(sectionMaxScroll, next);
                if (clamped == sectionScroll) {
                    yield false;
                }
                sectionScroll = clamped;
                yield true;
            }
            case OUTPUT, LENDING_MARKET -> {
                int clamped = Math.min(computeOutputMaxScroll(), next);
                if (clamped == outputScroll) {
                    yield false;
                }
                outputScroll = clamped;
                yield true;
            }
            case NOTEPAD -> {
                List<String> lines = buildNotepadLayout(Math.max(1, notepadAreaW - 14)).lines();
                int visible = Math.max(1, (notepadAreaH - 8) / LINE_HEIGHT);
                int maxScroll = Math.max(0, lines.size() - visible);
                int clamped = Math.min(maxScroll, next);
                if (clamped == notepadScroll) {
                    yield false;
                }
                notepadScroll = clamped;
                yield true;
            }
            case PAINT_CONTROLS -> {
                int clamped = Math.min(paintControlsMaxScroll, next);
                if (clamped == paintControlsScroll) {
                    yield false;
                }
                paintControlsScroll = clamped;
                yield true;
            }
            case SHOP_MANAGER -> {
                int clamped = Math.min(shopManagerMaxScroll, next);
                if (clamped == shopManagerScroll) {
                    yield false;
                }
                shopManagerScroll = clamped;
                yield true;
            }
            case SYSTEM_MONITOR -> {
                int clamped = Math.min(systemMonitorMaxScroll, next);
                if (clamped == systemMonitorScroll) {
                    yield false;
                }
                systemMonitorScroll = clamped;
                yield true;
            }
            case ORDER_BOARD -> {
                int clamped = Math.min(orderBoardMaxScroll, next);
                if (clamped == orderBoardScroll) {
                    yield false;
                }
                orderBoardScroll = clamped;
                yield true;
            }
            case WEBSHOP -> {
                int clamped = Math.min(webshopMaxScroll, next);
                if (clamped == webshopScroll) {
                    yield false;
                }
                webshopScroll = clamped;
                yield true;
            }
        };
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
        visibleShopEmployeeActions.clear();
        visibleShopOwnerAccountCards.clear();
        visibleShopPermissionMemberCards.clear();
        visibleShopVaultAdjustActions.clear();
        visibleShopInventoryActions.clear();
        visibleShopInventoryShelfCards.clear();
        visibleShopStockroomLocateActions.clear();
        visibleShopOrderCards.clear();
        visibleShopOrderPalletCards.clear();
        visibleShopOrderPickCards.clear();
        visibleShopLevelRoadmapNodes.clear();
        visibleKpiCards.clear();
        marketConfirmAcceptHitbox = null;
        marketConfirmCancelHitbox = null;
        accountProfileCopyIdHitbox = null;
        shopLevelRoadmapScrollbarTrackHitbox = null;
        shopLevelRoadmapScrollbarThumbHitbox = null;
        shopLevelRoadmapModalCloseHitbox = null;
        int bodyX = x + OUTPUT_PANEL_INSET;
        int bodyY = y + OUTPUT_PANEL_INSET;
        int bodyW = Math.max(1, width - (OUTPUT_PANEL_INSET * 2));
        int bodyH = Math.max(1, height - (OUTPUT_PANEL_INSET * 2));

        if (isActiveShopApp()) {
            drawShopOutputPanel(graphics, bodyX, bodyY, bodyW, bodyH);
            return;
        }

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
                ScrollbarTarget.OUTPUT,
                false,
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
            case HOURS -> "Hours & Lighting";
            case COMPLIANCE -> "Compliance";
            case PERMISSIONS -> "Permissions";
        };
        if (isActiveShopApp()) {
            if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
                return "Shop / Level Roadmap";
            }
            return "Shop / " + shopSectionLabel(activeSection);
        }
        if (activeSection == Section.OVERVIEW && overviewDetailOpen) {
            return "Overview / " + overviewActionLabel(overviewDetailAction);
        }
        if (activeSection == Section.LENDING && lendingMarketOpen) {
            return "Lending / Market";
        }
        return section;
    }

    private String shopSectionLabel(Section section) {
        if (section == null) {
            return "Dashboard";
        }
        return switch (section) {
            case OVERVIEW -> "Dashboard";
            case BRANDING -> "Sales";
            case LIMITS -> "Inventory";
            case GOVERNANCE -> "Operations";
            case STAFFING -> "Team & POS";
            case LENDING -> "Finance";
            case HOURS -> "Hours & Lighting";
            case COMPLIANCE -> "Settings";
            case PERMISSIONS -> "Permissions";
        };
    }

    private String defaultShopActionForSection(Section section) {
        if (section == null) {
            return "SHOP_OVERVIEW";
        }
        return switch (section) {
            case OVERVIEW -> "SHOP_OVERVIEW";
            case BRANDING -> "SHOP_OVERVIEW";
            case LIMITS -> "SHOP_SCAN";
            case GOVERNANCE -> "SHOP_ORDER_REPORT";
            case STAFFING -> "SHOP_LIST_EMPLOYEES";
            case LENDING -> "SHOP_FINANCE_REPORT";
            case HOURS -> "SHOP_HOURS_LIGHTING_REPORT";
            case COMPLIANCE -> "SHOP_OVERVIEW";
            case PERMISSIONS -> "SHOP_PERMISSIONS_REPORT";
        };
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
        boolean compactForShopInventory = isActiveShopApp() && activeSection == Section.LIMITS;
        boolean stockroomMode = compactForShopInventory && shopStockroomViewOpen;
        int minimumTop = top + (stockroomMode ? 74 : (compactForShopInventory ? 80 : 96));
        int sectionGap = 10; // Keep the standard spacing between input and output containers.
        int desiredTop = Math.max(minimumTop, sectionViewportY + sectionViewportH + sectionGap);
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
                registerScrollbar(
                        ScrollbarTarget.LENDING_MARKET,
                        barX1,
                        listY + 1,
                        Math.max(1, barX2 - barX1),
                        Math.max(1, listH - 2),
                        outputScroll,
                        maxRowScroll,
                        thumbH,
                        false
                );
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
        drawMetricCard(graphics, x, y, width, height, label, value, accent, null);
    }

    private void drawMetricCard(GuiGraphics graphics,
                                int x,
                                int y,
                                int width,
                                int height,
                                String label,
                                String value,
                                int accent,
                                String tooltipDescription) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF2E4D6D);
        graphics.fill(x, y, x + width, y + height, 0x8A1A304A);
        graphics.fill(x, y, x + width, y + 2, accent);
        graphics.drawString(this.font, label, x + 6, y + 7, 0xFFC6DEF7, false);
        graphics.drawString(this.font, fitToWidth(value, Math.max(40, width - 12)), x + 6, y + 22, 0xFFFFFFFF, false);
        if (tooltipDescription != null && !tooltipDescription.isBlank()) {
            visibleKpiCards.add(new KpiCardHitbox(x, y, width, height, label, value, tooltipDescription));
        }
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
        drawBarCard(graphics, x, y, width, height, title, value, subtitle, accent, null);
    }

    private void drawBarCard(GuiGraphics graphics,
                             int x,
                             int y,
                             int width,
                             int height,
                             String title,
                             float value,
                             String subtitle,
                             int accent,
                             String tooltipDescription) {
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
        if (tooltipDescription != null && !tooltipDescription.isBlank()) {
            String valueLine = Math.round(clamped * 100.0F) + "% • " + subtitle;
            visibleKpiCards.add(new KpiCardHitbox(x, y, width, height, title, fitToWidth(valueLine, 120), tooltipDescription));
        }
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

    /**
     * Synchronizes Hours/Lighting form controls from tokenized report output.
     * This keeps button/field states aligned with server truth after refresh.
     */
    private void applyShopHoursLightingTokens(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return;
        }
        String[] lines = rawMessage.split("\\R");
        for (String raw : lines) {
            if (raw == null || raw.isBlank() || !raw.startsWith("@")) {
                continue;
            }
            int eq = raw.indexOf('=');
            if (eq <= 1 || eq >= raw.length() - 1) {
                continue;
            }
            String key = raw.substring(1, eq).trim().toLowerCase(Locale.ROOT);
            String value = raw.substring(eq + 1).trim();
            switch (key) {
                case "shop_hours.open_label" -> formValues.put(SHOP_HOURS_OPEN_KEY, value);
                case "shop_hours.close_label" -> formValues.put(SHOP_HOURS_CLOSE_KEY, value);
                case "shop_hours.closed_deliverer_stockroom_access" ->
                        formValues.put(SHOP_HOURS_DELIVERER_STOCKROOM_ACCESS_KEY, "1".equals(value) ? "true" : "false");
                case "shop_lighting.enabled" -> formValues.put(SHOP_LIGHTING_ENABLED_KEY, "1".equals(value) ? "true" : "false");
                case "shop_lighting.main_mode" -> formValues.put(SHOP_LIGHTING_MAIN_MODE_KEY, value.toUpperCase(Locale.ROOT));
                case "shop_lighting.stockroom_mode" -> formValues.put(SHOP_LIGHTING_STOCKROOM_MODE_KEY, value.toUpperCase(Locale.ROOT));
                case "shop_lighting.exclude_stockroom" ->
                        formValues.put(SHOP_LIGHTING_EXCLUDE_STOCKROOM_KEY, "1".equals(value) ? "true" : "false");
                case "shop_lighting.level" ->
                        formValues.put(SHOP_LIGHTING_LEVEL_KEY, String.valueOf(parseBoundedInt(value, 15, 1, 15)));
                default -> {
                }
            }
        }
    }

    private int parseBoundedInt(String raw, int fallback, int min, int max) {
        int safeMin = Math.min(min, max);
        int safeMax = Math.max(min, max);
        int safeFallback = Math.max(safeMin, Math.min(safeMax, fallback));
        if (raw == null || raw.isBlank()) {
            return safeFallback;
        }
        try {
            int parsed = Integer.parseInt(raw.trim());
            return Math.max(safeMin, Math.min(safeMax, parsed));
        } catch (NumberFormatException ignored) {
            return safeFallback;
        }
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
            if (line != null && line.trim().startsWith("@")) {
                continue;
            }
            formatted.add(abbreviateMoneyInLine(line));
        }
        return wrapLines(formatted, Math.max(1, outputPanelW - 14));
    }

    /**
     * Shop app output host. In overview mode it can switch between KPI dashboard cards
     * and the dedicated horizontal level-roadmap renderer.
     */
    private void drawShopOutputPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0x40213A56);
        List<String> rawLines = ClientOwnerPcData.getActionOutputLines();
        if (activeSection == Section.OVERVIEW && shopLevelRoadmapOpen) {
            ShopLevelRoadmapSnapshot roadmap = parseShopLevelRoadmapSnapshot(rawLines);
            drawShopLevelRoadmapPanel(graphics, roadmap, x, y, width, height);
            return;
        }
        ShopDashboardSnapshot snapshot = activeSection == Section.OVERVIEW
                ? parseShopDashboardSnapshot(rawLines)
                : null;
        List<ShopEmployeeCardData> employeeCards = activeSection == Section.STAFFING
                ? parseShopEmployeeCards(rawLines)
                : List.of();
        List<ShopOwnerAccountCardData> ownerAccounts = activeSection == Section.LENDING && shopSettlementPickerOpen
                ? parseShopOwnerAccountCards(rawLines)
                : List.of();
        List<ShopPermissionRoleHeaderData> permissionHeaders = activeSection == Section.PERMISSIONS
                ? parseShopPermissionRoleHeaders(rawLines)
                : List.of();
        List<ShopPermissionMemberCardData> permissionMembers = activeSection == Section.PERMISSIONS
                ? parseShopPermissionMemberCards(rawLines)
                : List.of();
        List<ShopInventoryShelfCardData> inventoryCards = activeSection == Section.LIMITS
                ? parseShopInventoryCards(rawLines)
                : List.of();
        List<ShopStockroomItemCardData> stockroomCards = activeSection == Section.LIMITS && shopStockroomViewOpen
                ? parseShopStockroomCards(rawLines)
                : List.of();
        List<ShopOrderCardData> orderCards = activeSection == Section.GOVERNANCE
                ? parseShopOrderCards(rawLines)
                : List.of();
        List<ShopOrderPalletCardData> orderPalletCards = activeSection == Section.GOVERNANCE
                ? parseShopOrderPalletCards(rawLines)
                : List.of();
        List<ShopOrderPickCardData> orderPickCards = activeSection == Section.GOVERNANCE && shopOrderPickerOpen
                ? parseShopOrderPickCards(rawLines)
                : List.of();
        OrderBoardSummary orderSummary = activeSection == Section.GOVERNANCE
                ? parseOrderBoardSummary(rawLines)
                : null;
        ShopFinanceSnapshot financeSnapshot = activeSection == Section.LENDING
                ? parseShopFinanceSnapshot(rawLines)
                : null;
        ShopVaultSnapshot vaultSnapshot = activeSection == Section.LENDING
                ? parseShopVaultSnapshot(rawLines)
                : null;
        boolean clip = !useVirtualScale;
        if (snapshot != null) {
            int contentHeight = getShopDashboardContentHeight(width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            if (clip) {
                enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
            }
            drawShopDashboardSnapshot(graphics, snapshot, x + 2, y + 2 - outputScroll, Math.max(1, width - 4), contentHeight);
            if (clip) {
                graphics.disableScissor();
            }
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (shopSettlementPickerOpen && activeSection == Section.LENDING) {
            int contentHeight = drawShopOwnerAccountCards(graphics, ownerAccounts, x, y, width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (activeSection == Section.PERMISSIONS) {
            int contentHeight = drawShopPermissionRoleCards(graphics, permissionHeaders, permissionMembers, x, y, width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (shopVaultPlanEditOpen && activeSection == Section.LENDING) {
            int contentHeight = drawShopVaultPlanEditor(graphics, vaultSnapshot, x, y, width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (activeSection == Section.LIMITS) {
            int contentHeight = shopStockroomViewOpen
                    ? drawShopStockroomCards(graphics, stockroomCards, x, y, width, height)
                    : drawShopInventoryCards(graphics, inventoryCards, x, y, width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (activeSection == Section.GOVERNANCE) {
            int contentHeight = drawShopOrderManagerCards(
                    graphics,
                    orderCards,
                    orderPalletCards,
                    orderPickCards,
                    orderSummary,
                    x,
                    y,
                    width,
                    height
            );
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (!employeeCards.isEmpty()) {
            int contentHeight = drawShopEmployeeCards(graphics, employeeCards, x, y, width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (financeSnapshot != null) {
            int contentHeight = getShopFinanceContentHeight(width, height);
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            if (clip) {
                enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
            }
            drawShopFinanceSnapshot(graphics, financeSnapshot, x + 2, y + 2 - outputScroll, Math.max(1, width - 4));
            if (clip) {
                graphics.disableScissor();
            }
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }
        if (vaultSnapshot != null) {
            int contentHeight = getShopVaultContentHeight(width, height, vaultSnapshot.counts().size());
            int maxScroll = Math.max(0, contentHeight - height);
            outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));
            if (clip) {
                enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
            }
            drawShopVaultSnapshot(graphics, vaultSnapshot, x + 2, y + 2 - outputScroll, Math.max(1, width - 4));
            if (clip) {
                graphics.disableScissor();
            }
            drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
            return;
        }

        List<String> lines = getWrappedOutputLines();
        if (lines.isEmpty()) {
            lines = wrapLines(getShopSectionHintLines(), Math.max(80, width - 12));
        }

        int visible = Math.max(1, (height - 8) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visible);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (clip) {
            enableScaledScissor(graphics, x + 2, y + 2, x + width - 2, y + height - 2);
        }
        int lineY = y + 4;
        for (int i = 0; i < visible; i++) {
            int idx = outputScroll + i;
            if (idx >= lines.size()) {
                break;
            }
            graphics.drawString(this.font, lines.get(idx), x + 6, lineY, 0xFFE7F3FF, false);
            lineY += LINE_HEIGHT;
        }
        if (clip) {
            graphics.disableScissor();
        }
        drawOutputScrollbar(graphics, outputPanelX, outputPanelY, outputPanelW, outputPanelH, outputScroll, maxScroll);
    }

    /**
     * Draws the horizontal level roadmap:
     * - level nodes with reached/current/locked states
     * - progress segments between nodes
     * - horizontal drag/scroll bar
     * - click-to-open level detail modal
     */
    private void drawShopLevelRoadmapPanel(GuiGraphics graphics,
                                           ShopLevelRoadmapSnapshot snapshot,
                                           int x,
                                           int y,
                                           int width,
                                           int height) {
        visibleShopLevelRoadmapNodes.clear();
        shopLevelRoadmapScrollbarTrackHitbox = null;
        shopLevelRoadmapScrollbarThumbHitbox = null;
        shopLevelRoadmapModalCloseHitbox = null;

        int headerY = y + 6;
        graphics.fill(x, y, x + width, y + 22, 0x6C21405F);
        if (snapshot == null || snapshot.nodes().isEmpty()) {
            shopLevelRoadmapMaxScrollX = 0;
            shopLevelRoadmapScrollX = 0;
            List<String> lines = getWrappedOutputLines();
            if (lines.isEmpty()) {
                lines = wrapLines(
                        List.of(
                                "Level roadmap is not loaded yet.",
                                "Use Refresh Level Roadmap to fetch progression milestones."
                        ),
                        Math.max(80, width - 14)
                );
            }
            int textY = y + 28;
            for (String line : lines) {
                if (textY > y + height - 12) {
                    break;
                }
                graphics.drawString(this.font, line, x + 6, textY, 0xFFE1EEFF, false);
                textY += LINE_HEIGHT;
            }
            return;
        }

        int currentLevel = Math.max(1, snapshot.currentLevel());
        String revenueText = "$" + compactCurrency(String.valueOf(Math.max(0L, snapshot.currentRevenueDollars())));
        String nextTargetText = "$" + compactCurrency(String.valueOf(Math.max(1L, snapshot.nextLevelTargetDollars())));
        String header = fitToWidth(
                snapshot.shopName() + " • " + prettifyShopType(snapshot.shopType()) + " • Level " + currentLevel,
                Math.max(80, width - 12)
        );
        String subHeader = fitToWidth(
                "Revenue " + revenueText + " / Next " + nextTargetText + " • Progress "
                        + Math.round(Math.max(0.0D, Math.min(1.0D, snapshot.progressRatio())) * 100.0D) + "%",
                Math.max(80, width - 12)
        );
        graphics.drawString(this.font, header, x + 6, headerY, 0xFFE9F4FF, false);
        graphics.drawString(this.font, subHeader, x + 6, headerY + 10, 0xFFC5DCF3, false);

        int scrollbarH = 8;
        int viewportX = x + 4;
        int viewportY = y + 24;
        int viewportW = Math.max(40, width - 8);
        int viewportH = Math.max(80, height - 24 - scrollbarH - 6);
        int baselineY = viewportY + (viewportH / 2);
        int cardW = 114;
        int cardH = 34;
        int nodeSpacing = 94;
        int sidePadding = 58;

        int contentWidth = Math.max(viewportW,
                (sidePadding * 2) + Math.max(0, (snapshot.nodes().size() - 1) * nodeSpacing) + 2);
        shopLevelRoadmapMaxScrollX = Math.max(0, contentWidth - viewportW);
        shopLevelRoadmapScrollX = Math.max(0, Math.min(shopLevelRoadmapScrollX, shopLevelRoadmapMaxScrollX));

        graphics.fill(viewportX, viewportY, viewportX + viewportW, viewportY + viewportH, 0x3B18314A);
        graphics.fill(viewportX, baselineY - 1, viewportX + viewportW, baselineY + 1, 0x664F6F91);

        if (!useVirtualScale) {
            enableScaledScissor(graphics, viewportX + 1, viewportY + 1, viewportX + viewportW - 1, viewportY + viewportH - 1);
        }

        int startX = viewportX + sidePadding - shopLevelRoadmapScrollX;
        double currentProgress = Math.max(0.0D, Math.min(1.0D, snapshot.progressRatio()));
        for (int i = 0; i < snapshot.nodes().size() - 1; i++) {
            ShopLevelRoadmapNode node = snapshot.nodes().get(i);
            int nodeX = startX + (i * nodeSpacing);
            int nextX = startX + ((i + 1) * nodeSpacing);
            int segmentStart = nodeX + 12;
            int segmentEnd = nextX - 12;
            if (segmentEnd <= segmentStart) {
                continue;
            }
            if (segmentEnd < viewportX || segmentStart > (viewportX + viewportW)) {
                continue;
            }
            graphics.fill(segmentStart, baselineY - 1, segmentEnd, baselineY + 1, 0x6650698B);
            if (currentLevel > node.level()) {
                graphics.fill(segmentStart, baselineY - 1, segmentEnd, baselineY + 1, 0xCC59C988);
            } else if (currentLevel == node.level()) {
                int fillEnd = segmentStart + (int) Math.round((segmentEnd - segmentStart) * currentProgress);
                if (fillEnd > segmentStart) {
                    graphics.fill(segmentStart, baselineY - 1, fillEnd, baselineY + 1, 0xCC59C988);
                }
            }
        }

        for (int i = 0; i < snapshot.nodes().size(); i++) {
            ShopLevelRoadmapNode node = snapshot.nodes().get(i);
            int nodeX = startX + (i * nodeSpacing);
            if (nodeX < (viewportX - cardW) || nodeX > (viewportX + viewportW + cardW)) {
                continue;
            }
            boolean reached = node.level() <= currentLevel;
            boolean current = node.level() == currentLevel;
            int radius = current ? 14 : 11;
            int ringColor = reached ? 0xFF8DE0A8 : 0xFF8FB4D7;
            int fillColor = reached ? (current ? 0xFF55C980 : 0xFF4AAA70) : 0xFF325576;
            int textColor = reached ? 0xFF103523 : 0xFFE6F2FF;

            drawFilledCircle(graphics, nodeX, baselineY, radius, ringColor);
            drawFilledCircle(graphics, nodeX, baselineY, Math.max(1, radius - 2), fillColor);
            graphics.drawCenteredString(this.font, String.valueOf(node.level()), nodeX, baselineY - 4, textColor);
            visibleShopLevelRoadmapNodes.add(new ShopRoadmapNodeHitbox(nodeX, baselineY, radius, node));

            boolean topCard = (node.level() % 2) == 1;
            int cardX = nodeX - (cardW / 2);
            int cardY = topCard ? baselineY - 66 : baselineY + 24;
            int accent = reached ? 0xFF68D493 : 0xFF6EAEE0;
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 0xFF2F4F73);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xA81A3149);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, accent);
            int connectorStartY = topCard ? (cardY + cardH) : cardY;
            int connectorEndY = topCard ? (baselineY - radius) : (baselineY + radius);
            graphics.fill(nodeX, Math.min(connectorStartY, connectorEndY), nodeX + 1, Math.max(connectorStartY, connectorEndY), 0x88A9C8E7);

            String title = "Level " + node.level();
            String required = node.requiredRevenueDollars() <= 0L
                    ? "Unlock: Start"
                    : "Unlock: $" + compactCurrency(String.valueOf(node.requiredRevenueDollars()));
            String stateLine = current ? "Current" : (reached ? "Reached" : "Locked");
            graphics.drawString(this.font, fitToWidth(title, cardW - 8), cardX + 4, cardY + 6, 0xFFEAF5FF, false);
            graphics.drawString(this.font, fitToWidth(required, cardW - 8), cardX + 4, cardY + 16, 0xFFCDE2F9, false);
            graphics.drawString(this.font, fitToWidth(stateLine, cardW - 8), cardX + 4, cardY + 25, reached ? 0xFF91E1AA : 0xFFB9D1E9, false);

            String tooltipTitle = "Level " + node.level() + " • " + stateLine;
            String tooltipValue = node.requiredRevenueDollars() <= 0L
                    ? "Unlock Revenue: $0"
                    : ("Unlock Revenue: $" + compactCurrency(String.valueOf(node.requiredRevenueDollars())));
            visibleKpiCards.add(new KpiCardHitbox(
                    cardX,
                    cardY,
                    cardW,
                    cardH,
                    tooltipTitle,
                    tooltipValue,
                    roadmapNodeDescription(node, snapshot)
            ));
        }

        if (!useVirtualScale) {
            graphics.disableScissor();
        }

        int barY = y + height - scrollbarH - 2;
        graphics.fill(viewportX, barY, viewportX + viewportW, barY + scrollbarH, 0x5D2D4763);
        if (shopLevelRoadmapMaxScrollX > 0) {
            int thumbW = Math.max(28, (int) Math.round((viewportW / (double) contentWidth) * viewportW));
            thumbW = Math.min(viewportW, thumbW);
            int travel = Math.max(1, viewportW - thumbW);
            int thumbX = viewportX + (int) Math.round((shopLevelRoadmapScrollX / (double) shopLevelRoadmapMaxScrollX) * travel);
            graphics.fill(thumbX, barY, thumbX + thumbW, barY + scrollbarH, 0xCC9EC4E8);
            shopLevelRoadmapScrollbarTrackHitbox = new RectHitbox(viewportX, barY, viewportW, scrollbarH);
            shopLevelRoadmapScrollbarThumbHitbox = new RectHitbox(thumbX, barY, thumbW, scrollbarH);
        } else {
            shopLevelRoadmapScrollX = 0;
            shopLevelRoadmapScrollbarTrackHitbox = null;
            shopLevelRoadmapScrollbarThumbHitbox = null;
        }

        if (shopLevelRoadmapSelectedNode != null) {
            drawShopLevelRoadmapModal(graphics, snapshot, x, y, width, height, shopLevelRoadmapSelectedNode);
        }
    }

    /**
     * Node detail modal shown on top of the roadmap for exact unlock information.
     */
    private void drawShopLevelRoadmapModal(GuiGraphics graphics,
                                           ShopLevelRoadmapSnapshot snapshot,
                                           int panelX,
                                           int panelY,
                                           int panelW,
                                           int panelH,
                                           ShopLevelRoadmapNode node) {
        if (node == null) {
            shopLevelRoadmapModalCloseHitbox = null;
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xA8101F30);

        int modalW = Math.min(360, Math.max(250, panelW - 36));
        int modalH = 182;
        int modalX = panelX + (panelW - modalW) / 2;
        int modalY = panelY + (panelH - modalH) / 2;

        graphics.fill(modalX - 1, modalY - 1, modalX + modalW + 1, modalY + modalH + 1, 0xFF355474);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + modalH, 0xEE19324A);
        graphics.fill(modalX, modalY, modalX + modalW, modalY + 22, 0xCC2A5E94);
        graphics.fill(modalX, modalY + 22, modalX + modalW, modalY + 23, 0x88A8CDEE);

        String state = node.state() == null ? "LOCKED" : node.state().toUpperCase(Locale.ROOT);
        String stateLabel = switch (state) {
            case "COMPLETED" -> "Reached";
            case "CURRENT" -> "Current";
            default -> "Locked";
        };
        int stateColor = switch (state) {
            case "COMPLETED" -> 0xFF89E0A4;
            case "CURRENT" -> 0xFF9DE6B2;
            default -> 0xFFC7D8EA;
        };

        graphics.drawString(this.font, "Level " + node.level() + " Unlock Details", modalX + 8, modalY + 7, 0xFFFFFFFF, false);
        graphics.drawString(this.font, stateLabel, modalX + modalW - 8 - this.font.width(stateLabel), modalY + 7, stateColor, false);

        int lineY = modalY + 30;
        graphics.drawString(this.font,
                "Required Revenue: $" + compactCurrency(String.valueOf(Math.max(0L, node.requiredRevenueDollars()))),
                modalX + 8,
                lineY,
                0xFFE5F2FF,
                false);
        lineY += 12;
        graphics.drawString(this.font,
                "Claim Capacity: " + compactCurrency(String.valueOf(Math.max(0L, node.claimCapacityBlocks()))) + " blocks",
                modalX + 8,
                lineY,
                0xFFCFE3FA,
                false);
        lineY += 12;
        graphics.drawString(this.font,
                "Stockroom Capacity: " + compactCurrency(String.valueOf(Math.max(0L, node.stockroomCapacityBlocks()))) + " blocks",
                modalX + 8,
                lineY,
                0xFFCFE3FA,
                false);
        lineY += 12;
        graphics.drawString(this.font,
                "Display Capacity: " + Math.max(0, node.displayCap()),
                modalX + 8,
                lineY,
                0xFFCFE3FA,
                false);
        lineY += 12;
        graphics.drawString(this.font,
                "Cashier Spawn-Egg Capacity: " + Math.max(0, node.cashierCap()),
                modalX + 8,
                lineY,
                0xFFCFE3FA,
                false);
        lineY += 12;
        graphics.drawString(this.font,
                "Delivery Pallet Capacity: " + Math.max(0, node.deliveryPalletCap()),
                modalX + 8,
                lineY,
                0xFFCFE3FA,
                false);
        lineY += 12;

        if ("CURRENT".equals(state)) {
            long floor = Math.max(0L, snapshot.currentLevelFloorDollars());
            long next = Math.max(floor + 1L, snapshot.nextLevelTargetDollars());
            long revenue = Math.max(0L, snapshot.currentRevenueDollars());
            int pct = (int) Math.round(Math.max(0.0D, Math.min(1.0D, snapshot.progressRatio())) * 100.0D);
            graphics.drawString(this.font,
                    "Current Progress: $" + compactCurrency(String.valueOf(revenue))
                            + " / $" + compactCurrency(String.valueOf(next))
                            + " (" + pct + "%)",
                    modalX + 8,
                    lineY,
                    0xFF9DE6B2,
                    false);
            lineY += 12;
        }

        graphics.drawString(this.font,
                fitToWidth("Tip: click any roadmap node to inspect unlock milestones.", modalW - 16),
                modalX + 8,
                lineY + 4,
                0xFFBCD7F3,
                false);

        int closeW = 108;
        int closeH = 18;
        int closeX = modalX + (modalW - closeW) / 2;
        int closeY = modalY + modalH - closeH - 8;
        drawInlineActionButton(graphics, closeX, closeY, closeW, closeH, "Back", 0xFF6BAED6);
        shopLevelRoadmapModalCloseHitbox = new RectHitbox(closeX, closeY, closeW, closeH);
        graphics.pose().popPose();
    }

    private String roadmapNodeDescription(ShopLevelRoadmapNode node, ShopLevelRoadmapSnapshot snapshot) {
        if (node == null) {
            return "Level roadmap milestone.";
        }
        String state = node.state() == null ? "LOCKED" : node.state().toUpperCase(Locale.ROOT);
        String stateLabel = switch (state) {
            case "COMPLETED" -> "Reached";
            case "CURRENT" -> "Current milestone";
            default -> "Locked milestone";
        };
        String required = "$" + compactCurrency(String.valueOf(Math.max(0L, node.requiredRevenueDollars())));
        String unlocks = "Unlocks: claim "
                + compactCurrency(String.valueOf(Math.max(0L, node.claimCapacityBlocks())))
                + " blocks, stockroom "
                + compactCurrency(String.valueOf(Math.max(0L, node.stockroomCapacityBlocks())))
                + " blocks, displays " + Math.max(0, node.displayCap())
                + ", cashiers " + Math.max(0, node.cashierCap())
                + ", delivery pallets " + Math.max(0, node.deliveryPalletCap()) + ".";
        if ("CURRENT".equals(state)) {
            int pct = (int) Math.round(Math.max(0.0D, Math.min(1.0D, snapshot.progressRatio())) * 100.0D);
            return stateLabel + ". Required revenue " + required + ". " + unlocks + " Progress to next level: " + pct + "%.";
        }
        return stateLabel + ". Required revenue " + required + ". " + unlocks;
    }

    private void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        int safeRadius = Math.max(1, radius);
        for (int dy = -safeRadius; dy <= safeRadius; dy++) {
            int width = (int) Math.floor(Math.sqrt(Math.max(0, (safeRadius * safeRadius) - (dy * dy))));
            graphics.fill(centerX - width, centerY + dy, centerX + width + 1, centerY + dy + 1, color);
        }
    }

    private void updateShopLevelRoadmapScrollFromThumbLeft(int thumbLeft) {
        if (shopLevelRoadmapScrollbarTrackHitbox == null || shopLevelRoadmapScrollbarThumbHitbox == null) {
            return;
        }
        if (shopLevelRoadmapMaxScrollX <= 0) {
            shopLevelRoadmapScrollX = 0;
            return;
        }
        int travel = Math.max(1, shopLevelRoadmapScrollbarTrackHitbox.width() - shopLevelRoadmapScrollbarThumbHitbox.width());
        int minThumbLeft = shopLevelRoadmapScrollbarTrackHitbox.x();
        int maxThumbLeft = minThumbLeft + travel;
        int clampedThumbLeft = Math.max(minThumbLeft, Math.min(maxThumbLeft, thumbLeft));
        double ratio = (clampedThumbLeft - minThumbLeft) / (double) travel;
        shopLevelRoadmapScrollX = (int) Math.round(ratio * shopLevelRoadmapMaxScrollX);
        shopLevelRoadmapScrollX = Math.max(0, Math.min(shopLevelRoadmapMaxScrollX, shopLevelRoadmapScrollX));
    }

    private List<String> getShopSectionHintLines() {
        return switch (activeSection) {
            case OVERVIEW -> List.of(
                    shopLevelRoadmapOpen ? "Shop level roadmap is ready." : "Shop dashboard is ready.",
                    shopLevelRoadmapOpen
                            ? "Use Refresh Level Roadmap to reload milestones. Click a level node to inspect unlock details."
                            : "Use Refresh Dashboard (All KPIs) for the full overview with cards, trend, and category charts."
            );
            case BRANDING -> List.of(
                    "Checkout routing.",
                    "Set Checkout Terminal Near PC to route cashier card payments."
            );
            case LIMITS -> List.of(
                    "Inventory controls.",
                    "Scan Shelves builds interactive shelf cards with restock, remove, min/max targets, stockroom preview, velocity and last sold.",
                    "Use Show Stockroom for a full stockroom card list with locate guidance to each inventory."
            );
            case GOVERNANCE -> List.of(
                    "Ordering and operations.",
                    "Use this panel to create delivery orders, assign delivery pallets, and manage courier-ready logistics.",
                    "Claim tools close the PC and start selection mode: stick Pos1/Pos2, paper apply, sponge clear."
            );
            case STAFFING -> List.of(
                    "Cashier operations.",
                    "Hire Cashier NPC creates a unique employee tied to this shop. Show All Employees opens cards with copy-id and fire actions."
            );
            case LENDING -> List.of(
                    "Finance and settlement controls.",
                    "Set Settlement Account decides where terminal/card payments are deposited. Show Cash Vault lists exact bill/coin counts paid by customers."
            );
            case HOURS -> List.of(
                    "Store schedule and automatic lighting.",
                    "Set opening/closing times in AM/PM format, then choose main and stockroom lighting modes.",
                    "Closed-hours delivery access gives accepted couriers 1 minute on claimed land and 5 minutes in stockroom.",
                    "Opening-hours mode follows shop hours, inverted mode flips that behavior for after-hours visibility."
            );
            case COMPLIANCE -> List.of(
                    "Shop settings and maintenance.",
                    "Rename the shop, change shop type, clear terminal links, or delete the shop with name confirmation."
            );
            case PERMISSIONS -> List.of(
                    "Plot permissions.",
                    "Output shows grouped role headers (OWNER/MANAGER/BUILDER/STAFF/GUESTS) with player cards.",
                    "Click a player card to target it, then assign/update/remove roles from this panel."
            );
        };
    }

    private int getShopDashboardContentHeight(int width, int viewportHeight) {
        return getShopDashboardContentHeight(width, viewportHeight, activeSection);
    }

    private int getShopDashboardContentHeight(int width, int viewportHeight, Section section) {
        int cardGap = 6;
        int cardH = 42;
        int cardCols = width >= 560 ? 4 : width >= 360 ? 2 : 1;
        int cardRows = (getShopDashboardCardCount(section) + cardCols - 1) / cardCols;
        int cardsBlock = (cardRows * cardH) + (Math.max(0, cardRows - 1) * cardGap);
        int chartsBlock = width < 420 ? (62 + 8 + 62 + 8 + 70) : (64 + 10 + 70);
        int headerBlock = 20;
        int listBlock = 86;
        return Math.max(viewportHeight, 8 + headerBlock + cardsBlock + 8 + chartsBlock + 8 + listBlock + 8);
    }

    private int getShopDashboardCardCount() {
        return getShopDashboardCardCount(activeSection);
    }

    private int getShopDashboardCardCount(Section section) {
        Section resolved = section == null ? Section.OVERVIEW : section;
        return switch (resolved) {
            case OVERVIEW -> 24;
            default -> 8;
        };
    }

    private void drawShopDashboardSnapshot(GuiGraphics graphics,
                                           ShopDashboardSnapshot s,
                                           int x,
                                           int y,
                                           int width,
                                           int height) {
        drawShopDashboardSnapshot(graphics, s, x, y, width, height, activeSection);
    }

    private void drawShopDashboardSnapshot(GuiGraphics graphics,
                                           ShopDashboardSnapshot s,
                                           int x,
                                           int y,
                                           int width,
                                           int height,
                                           Section section) {
        if (s == null) {
            return;
        }
        Section dashboardSection = section == null ? Section.OVERVIEW : section;

        graphics.drawString(this.font,
                fitToWidth(s.shopName() + "  |  " + prettifyShopType(s.shopType()) + "  |  Level " + s.level(), Math.max(40, width - 12)),
                x + 4,
                y + 2,
                0xFFE8F4FF,
                false);
        graphics.drawString(this.font,
                "Status: " + s.ragStatus(),
                x + Math.max(160, width - 124),
                y + 2,
                "GREEN".equalsIgnoreCase(s.ragStatus()) ? 0xFF84E4A3
                        : "AMBER".equalsIgnoreCase(s.ragStatus()) ? 0xFFF2C272
                        : 0xFFE58B8B,
                false);

        String[] cardLabels = new String[0];
        String[] cardValues = new String[0];
        int[] accents = new int[0];
        switch (dashboardSection) {
            case BRANDING -> {
                cardLabels = new String[]{"Revenue", "AOV", "Conversion", "Foot Traffic", "Sales / Labor Hr", "Top Shop", "All Shops Revenue", "RAG"};
                cardValues = new String[]{
                        "$" + compactCurrency(String.valueOf(s.revenueDollars())),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.aov())),
                        String.format(Locale.ROOT, "%.1f%%", s.conversionRate()),
                        String.valueOf(s.footTraffic()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.salesPerLaborHour())),
                        s.bestShopName(),
                        "$" + compactCurrency(String.valueOf(s.allShopsRevenueDollars())),
                        s.ragStatus()
                };
                accents = new int[]{0xFF6EB9FF, 0xFF8DC7FF, 0xFF7CD6B0, 0xFFBFA9FF, 0xFF6FD39A, 0xFF6FB8FF, 0xFFC4B57A, 0xFF8BD68A};
            }
            case LIMITS -> {
                cardLabels = new String[]{"Shelves", "Configured Slots", "Stock Units", "Low Stock", "Out of Stock", "Turnover", "Stock/Sales", "Claim Fill"};
                cardValues = new String[]{
                        String.valueOf(s.shelves()),
                        String.valueOf(s.configuredSlots()),
                        formatStockUnitsValue(s),
                        String.valueOf(s.lowStockSlots()),
                        String.valueOf(s.outOfStockSlots()),
                        String.format(Locale.ROOT, "%.2f", s.inventoryTurnover()),
                        String.format(Locale.ROOT, "%.2f", s.stockToSalesRatio()),
                        s.claimCapacityBlocks() <= 0L ? "-" : Math.round((s.usedClaimBlocks() / (double) s.claimCapacityBlocks()) * 100.0D) + "%"
                };
                accents = new int[]{0xFF6EB9FF, 0xFF8DC7FF, 0xFF7CD6B0, 0xFFF2C272, 0xFFE58B8B, 0xFF8CC7FF, 0xFF8AE2CE, 0xFFC7B37A};
            }
            case GOVERNANCE -> {
                cardLabels = new String[]{"Foot Traffic", "Wait Time", "Service Time", "CSAT", "NPS", "Conversion", "Claim Regions", "Stockroom Regions"};
                cardValues = new String[]{
                        String.valueOf(s.footTraffic()),
                        String.format(Locale.ROOT, "%.1fs", s.waitSeconds()),
                        String.format(Locale.ROOT, "%.1fs", s.serviceSeconds()),
                        String.format(Locale.ROOT, "%.1f%%", s.csat()),
                        String.format(Locale.ROOT, "%.0f", s.nps()),
                        String.format(Locale.ROOT, "%.1f%%", s.conversionRate()),
                        String.valueOf(s.claimRegions()),
                        String.valueOf(s.stockroomRegions())
                };
                accents = new int[]{0xFF6EB9FF, 0xFFF2C272, 0xFF8DC7FF, 0xFF7CD6B0, 0xFF8AE2CE, 0xFF9AC9FF, 0xFFC0B2FF, 0xFFC7B37A};
            }
            case STAFFING -> {
                cardLabels = new String[]{"Cashiers", "Linked Terminals", "Sales/Employee", "Sales/Labor Hr", "AOV", "Conversion", "Foot Traffic", "RAG"};
                cardValues = new String[]{
                        String.valueOf(s.cashiers()),
                        String.valueOf(s.linkedCashiers()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.cashiers() <= 0 ? 0.0D : (s.revenueDollars() / (double) Math.max(1, s.cashiers())))),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.salesPerLaborHour())),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.aov())),
                        String.format(Locale.ROOT, "%.1f%%", s.conversionRate()),
                        String.valueOf(s.footTraffic()),
                        s.ragStatus()
                };
                accents = new int[]{0xFF6EB9FF, 0xFF8DC7FF, 0xFF7CD6B0, 0xFF6FD39A, 0xFFC4B57A, 0xFF8AE2CE, 0xFF9AC9FF, 0xFF8BD68A};
            }
            case LENDING -> {
                cardLabels = new String[]{"Gross Margin", "Operating Expenses", "Net Profit", "6M Cash Flow", "Revenue", "Target", "All Shops", "Best Shop"};
                cardValues = new String[]{
                        String.format(Locale.ROOT, "%.1f%%", s.grossMarginPct()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.operatingExpenses())),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.netProfit())),
                        "$" + compactCurrency(String.valueOf(s.sixMonthCashflowForecast())),
                        "$" + compactCurrency(String.valueOf(s.revenueDollars())),
                        "$" + compactCurrency(String.valueOf(s.targetDollars())),
                        String.valueOf(s.allShopsCount()),
                        s.bestShopName()
                };
                accents = new int[]{0xFF7CD6B0, 0xFFE58B8B, 0xFF6FD39A, 0xFF6EB9FF, 0xFF8DC7FF, 0xFFC4B57A, 0xFFBDA7FF, 0xFF8CC7FF};
            }
            case COMPLIANCE -> {
                cardLabels = new String[]{"RAG", "Claim Fill", "Low Stock Alerts", "Out of Stock Alerts", "Cashiers Linked", "Stock/Sales", "All Shops Revenue", "Best Shop"};
                cardValues = new String[]{
                        s.ragStatus(),
                        s.claimCapacityBlocks() <= 0L ? "-" : Math.round((s.usedClaimBlocks() / (double) s.claimCapacityBlocks()) * 100.0D) + "%",
                        String.valueOf(s.lowStockSlots()),
                        String.valueOf(s.outOfStockSlots()),
                        s.cashiers() <= 0 ? "0%" : Math.round((s.linkedCashiers() / (double) Math.max(1, s.cashiers())) * 100.0D) + "%",
                        String.format(Locale.ROOT, "%.2f", s.stockToSalesRatio()),
                        "$" + compactCurrency(String.valueOf(s.allShopsRevenueDollars())),
                        s.bestShopName()
                };
                accents = new int[]{0xFF8BD68A, 0xFFC7B37A, 0xFFF2C272, 0xFFE58B8B, 0xFF6EB9FF, 0xFF8AE2CE, 0xFFC4B57A, 0xFF8CC7FF};
            }
            case PERMISSIONS -> {
                cardLabels = new String[]{
                        "Shop Type",
                        "Claim Regions",
                        "Claim Fill",
                        "Stockroom Regions",
                        "Shelves",
                        "Cashiers",
                        "Linked Terminals",
                        "RAG"
                };
                cardValues = new String[]{
                        prettifyShopType(s.shopType()),
                        String.valueOf(s.claimRegions()),
                        s.claimCapacityBlocks() <= 0L ? "-" : Math.round((s.usedClaimBlocks() / (double) s.claimCapacityBlocks()) * 100.0D) + "%",
                        String.valueOf(s.stockroomRegions()),
                        String.valueOf(s.shelves()),
                        String.valueOf(s.cashiers()),
                        String.valueOf(s.linkedCashiers()),
                        s.ragStatus()
                };
                accents = new int[]{
                        0xFF6EB9FF,
                        0xFFC7B37A,
                        0xFF8AE2CE,
                        0xFF9AC9FF,
                        0xFF8DC7FF,
                        0xFF7CD6B0,
                        0xFF8CC7FF,
                        0xFF8BD68A
                };
            }
            case OVERVIEW -> {
                cardLabels = new String[]{
                        "Revenue",
                        "Target",
                        "Next Level Target",
                        "Net Profit",
                        "Gross Margin",
                        "Operating Expenses",
                        "6M Cash Flow",
                        "AOV",
                        "Conversion",
                        "Foot Traffic",
                        "Sales / Labor Hr",
                        "Cashiers Linked",
                        "Cash Tx",
                        "Terminal Tx",
                        "Cash Customers",
                        "Terminal Customers",
                        "Cash Collected",
                        "Card Collected",
                        "Vault Tender",
                        "Claim Fill",
                        "Shelves",
                        "Stock Units",
                        "Low Stock",
                        "Out of Stock"
                };
                cardValues = new String[]{
                        "$" + compactCurrency(String.valueOf(s.revenueDollars())),
                        "$" + compactCurrency(String.valueOf(s.targetDollars())),
                        "$" + compactCurrency(String.valueOf(s.nextLevelTargetDollars())),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.netProfit())),
                        String.format(Locale.ROOT, "%.1f%%", s.grossMarginPct()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.operatingExpenses())),
                        "$" + compactCurrency(String.valueOf(s.sixMonthCashflowForecast())),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.aov())),
                        String.format(Locale.ROOT, "%.1f%%", s.conversionRate()),
                        String.valueOf(s.footTraffic()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.salesPerLaborHour())),
                        s.linkedCashiers() + "/" + s.cashiers(),
                        String.valueOf(s.cashTxCount()),
                        String.valueOf(s.terminalTxCount()),
                        String.valueOf(s.cashCustomers()),
                        String.valueOf(s.terminalCustomers()),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.cashTotalCents() / 100.0D)),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.terminalTotalCents() / 100.0D)),
                        "$" + compactCurrency(String.format(Locale.ROOT, "%.2f", s.vaultTotalCents() / 100.0D)),
                        s.claimCapacityBlocks() <= 0L ? "-" : Math.round((s.usedClaimBlocks() / (double) s.claimCapacityBlocks()) * 100.0D) + "%",
                        String.valueOf(s.shelves()),
                        formatStockUnitsValue(s),
                        String.valueOf(s.lowStockSlots()),
                        String.valueOf(s.outOfStockSlots())
                };
                accents = new int[]{
                        0xFF6EB9FF,
                        0xFFC4B57A,
                        0xFF8CC7FF,
                        0xFF6FD39A,
                        0xFF7CD6B0,
                        0xFFE58B8B,
                        0xFF6EB9FF,
                        0xFF8DC7FF,
                        0xFF8AE2CE,
                        0xFF9AC9FF,
                        0xFF6FD39A,
                        0xFF9AC9FF,
                        0xFF7CD6B0,
                        0xFF9AC9FF,
                        0xFF8AE2CE,
                        0xFFC4B57A,
                        0xFF6FD39A,
                        0xFF8DC7FF,
                        0xFFF0C278,
                        0xFFC7B37A,
                        0xFF8DC7FF,
                        0xFF8AE2CE,
                        0xFFF2C272,
                        0xFFE58B8B
                };
            }
        }

        int cardGap = 6;
        int cardCols = width >= 560 ? 4 : width >= 360 ? 2 : 1;
        int cardW = Math.max(120, (width - (cardGap * (cardCols - 1))) / cardCols);
        int cardH = 42;
        int cardsTop = y + 18;
        for (int i = 0; i < cardLabels.length; i++) {
            int col = i % cardCols;
            int row = i / cardCols;
            int cardX = x + (col * (cardW + cardGap));
            int cardY = cardsTop + (row * (cardH + cardGap));
            drawMetricCard(
                    graphics,
                    cardX,
                    cardY,
                    cardW,
                    cardH,
                    cardLabels[i],
                    cardValues[i],
                    accents[i],
                    shopKpiDescription(cardLabels[i])
            );
        }

        int cardRows = (cardLabels.length + cardCols - 1) / cardCols;
        int cursorY = cardsTop + (cardRows * (cardH + cardGap)) + 4;
        int chartGap = 8;
        int chartW = width < 420 ? width : (width - chartGap) / 2;

        float revenueProgress = s.targetDollars() <= 0L ? 0.0F : (float) (s.revenueDollars() / (double) s.targetDollars());
        float stockHealth = s.configuredSlots() <= 0 ? 1.0F
                : (float) ((Math.max(0, s.configuredSlots() - s.lowStockSlots() - s.outOfStockSlots())) / (double) s.configuredSlots());
        if (width < 420) {
            drawBarCard(graphics, x, cursorY, chartW, 58, "Revenue Progress", revenueProgress,
                    "$" + compactCurrency(String.valueOf(s.revenueDollars())) + " / $" + compactCurrency(String.valueOf(s.targetDollars())),
                    0xFF6FD39A,
                    "Revenue progress bar showing current sales versus the level target.");
            cursorY += 66;
            drawBarCard(graphics, x, cursorY, chartW, 58, "Stock Health", stockHealth,
                    "Low " + s.lowStockSlots() + " | OOS " + s.outOfStockSlots(),
                    0xFF6EB9FF,
                    "Stock health bar based on configured slots that are neither low-stock nor out-of-stock.");
            cursorY += 66;
        } else {
            drawBarCard(graphics, x, cursorY, chartW, 58, "Revenue Progress", revenueProgress,
                    "$" + compactCurrency(String.valueOf(s.revenueDollars())) + " / $" + compactCurrency(String.valueOf(s.targetDollars())),
                    0xFF6FD39A,
                    "Revenue progress bar showing current sales versus the level target.");
            drawBarCard(graphics, x + chartW + chartGap, cursorY, chartW, 58, "Stock Health", stockHealth,
                    "Low " + s.lowStockSlots() + " | OOS " + s.outOfStockSlots(),
                    0xFF6EB9FF,
                    "Stock health bar based on configured slots that are neither low-stock nor out-of-stock.");
            cursorY += 66;
        }

        int trendH = 70;
        int trendY = cursorY;
        drawShopTrendChart(graphics, x, trendY, width, trendH, s.trendDailyRevenue());
        List<Long> trendValues = s.trendDailyRevenue();
        long latestTrend = 0L;
        if (trendValues != null && !trendValues.isEmpty()) {
            Long last = trendValues.get(trendValues.size() - 1);
            latestTrend = Math.max(0L, last == null ? 0L : last);
        }
        visibleKpiCards.add(new KpiCardHitbox(
                x,
                trendY,
                width,
                trendH,
                "Sales Trend (Last 7 Days)",
                "$" + compactCurrency(String.valueOf(latestTrend)) + " latest day",
                "Line chart of daily shop revenue. Use this to spot growth, drops, and volatility over the last week."
        ));
        cursorY += trendH + 8;

        int categoryH = 84;
        int categoryY = cursorY;
        drawShopCategoryChart(graphics, x, categoryY, width, categoryH, s.categorySalesValue());
        Map<String, Long> categoryMap = s.categorySalesValue();
        String topCategory = "N/A";
        long topValue = 0L;
        if (categoryMap != null && !categoryMap.isEmpty()) {
            for (Map.Entry<String, Long> entry : categoryMap.entrySet()) {
                long value = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
                if (value >= topValue) {
                    topValue = value;
                    topCategory = entry.getKey() == null || entry.getKey().isBlank() ? "Unknown" : entry.getKey();
                }
            }
        }
        visibleKpiCards.add(new KpiCardHitbox(
                x,
                categoryY,
                width,
                categoryH,
                "Sales by Category (Value)",
                fitToWidth(topCategory + " $" + compactCurrency(String.valueOf(topValue)), 120),
                "Bar chart comparing category sales value. Taller bars indicate stronger category performance."
        ));
    }

    private void drawShopTrendChart(GuiGraphics graphics,
                                    int x,
                                    int y,
                                    int width,
                                    int height,
                                    List<Long> values) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF355474);
        graphics.fill(x, y, x + width, y + height, 0x75192D45);
        graphics.drawString(this.font, "Sales Trend (Last 7 Days)", x + 6, y + 6, 0xFFE5F3FF, false);

        int plotX1 = x + 8;
        int plotY1 = y + 20;
        int plotX2 = x + width - 8;
        int plotY2 = y + height - 8;
        graphics.fill(plotX1, plotY1, plotX2, plotY2, 0x44152A40);
        graphics.fill(plotX1, plotY2 - 1, plotX2, plotY2, 0x6698BEDF);
        graphics.fill(plotX1, plotY1, plotX1 + 1, plotY2, 0x6698BEDF);

        List<Long> trend = values == null ? List.of() : values;
        if (trend.size() < 2) {
            graphics.drawString(this.font, "Not enough history yet.", plotX1 + 4, plotY1 + 4, 0xFFC9DEF3, false);
            return;
        }

        long max = 1L;
        for (Long value : trend) {
            if (value != null) {
                max = Math.max(max, Math.max(0L, value));
            }
        }
        int count = trend.size();
        int prevX = plotX1;
        int prevY = plotY2;
        for (int i = 0; i < count; i++) {
            long value = trend.get(i) == null ? 0L : Math.max(0L, trend.get(i));
            float ratio = max <= 0L ? 0.0F : (float) (value / (double) max);
            int px = plotX1 + Math.round((plotX2 - plotX1 - 1) * (i / (float) Math.max(1, count - 1)));
            int py = plotY2 - 1 - Math.round((plotY2 - plotY1 - 2) * ratio);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, 0xFF78CAFF);
            if (i > 0) {
                drawLine(graphics, prevX, prevY, px, py, 0xCC78CAFF);
            }
            prevX = px;
            prevY = py;
        }
    }

    private void drawShopCategoryChart(GuiGraphics graphics,
                                       int x,
                                       int y,
                                       int width,
                                       int height,
                                       Map<String, Long> categories) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF355474);
        graphics.fill(x, y, x + width, y + height, 0x75192D45);
        graphics.drawString(this.font, "Sales by Category (Value)", x + 6, y + 6, 0xFFE5F3FF, false);

        Map<String, Long> values = categories == null ? Map.of() : categories;
        if (values.isEmpty()) {
            graphics.drawString(this.font, "No category data yet.", x + 8, y + 22, 0xFFC9DEF3, false);
            return;
        }

        List<Map.Entry<String, Long>> entries = new ArrayList<>(values.entrySet());
        entries.sort((a, b) -> Long.compare(
                b.getValue() == null ? 0L : b.getValue(),
                a.getValue() == null ? 0L : a.getValue()
        ));
        int maxBars = Math.min(4, entries.size());
        long max = 1L;
        for (int i = 0; i < maxBars; i++) {
            max = Math.max(max, Math.max(0L, entries.get(i).getValue() == null ? 0L : entries.get(i).getValue()));
        }

        int baseY = y + height - 10;
        int barTop = y + 22;
        int areaW = Math.max(80, width - 14);
        int barGap = 8;
        int barW = Math.max(16, (areaW - (barGap * (maxBars - 1))) / Math.max(1, maxBars));
        int startX = x + 7;
        int[] colors = {0xFF7CC5FF, 0xFF79D8A8, 0xFFF0C278, 0xFFB6A0FF};

        for (int i = 0; i < maxBars; i++) {
            Map.Entry<String, Long> entry = entries.get(i);
            long value = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            float ratio = (float) (value / (double) max);
            int h = Math.max(2, Math.round((baseY - barTop) * ratio));
            int barX = startX + (i * (barW + barGap));
            int barY = baseY - h;
            int color = colors[i % colors.length];
            graphics.fill(barX, barY, barX + barW, baseY, color);
            graphics.fill(barX, barY, barX + barW, barY + 1, 0x66FFFFFF);
            graphics.drawCenteredString(this.font,
                    fitToWidth(entry.getKey(), Math.max(12, barW - 2)),
                    barX + (barW / 2),
                    baseY + 1,
                    0xFFCFE4FF);
        }
    }

    private List<ShopInventoryShelfCardData> parseShopInventoryCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        Map<Integer, ShopInventoryShelfCardData> shelfCards = new LinkedHashMap<>();
        Map<Integer, List<ShopInventoryItemCardData>> itemMap = new HashMap<>();

        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.startsWith("@shelf_card=")) {
                String payload = line.substring("@shelf_card=".length()).trim();
                if (payload.isBlank()) {
                    continue;
                }
                String[] parts = payload.split("\\|", -1);
                if (parts.length < 10) {
                    continue;
                }
                int index = parseIntMetricToken(parts[0]);
                if (index <= 0) {
                    continue;
                }
                String dimension = parts[1].trim();
                String position = "(" + parts[2].trim() + "," + parts[3].trim() + "," + parts[4].trim() + ")";
                boolean creative = "CREATIVE".equalsIgnoreCase(parts[5].trim());
                int configured = Math.max(0, parseIntMetricToken(parts[6]));
                int totalStock = parseIntMetricToken(parts[7]);
                int lowStock = Math.max(0, parseIntMetricToken(parts[8]));
                int outOfStock = Math.max(0, parseIntMetricToken(parts[9]));
                String shelfTarget = parts.length >= 11 ? parts[10].trim() : "";
                shelfCards.put(index, new ShopInventoryShelfCardData(
                        index,
                        dimension,
                        position,
                        creative,
                        configured,
                        totalStock,
                        lowStock,
                        outOfStock,
                        shelfTarget,
                        List.of()
                ));
            } else if (line.startsWith("@shelf_item=")) {
                String payload = line.substring("@shelf_item=".length()).trim();
                if (payload.isBlank()) {
                    continue;
                }
                String[] parts = payload.split("\\|", -1);
                if (parts.length < 7) {
                    continue;
                }
                int shelfIndex = parseIntMetricToken(parts[0]);
                int slotIndex = Math.max(1, parseIntMetricToken(parts[1]));
                if (shelfIndex <= 0) {
                    continue;
                }
                String itemId = parts[2].trim();
                long priceCents = Math.max(0L, parseLongMetricToken(parts[3]));
                int stock = parseIntMetricToken(parts[4]);
                boolean restockable = "1".equals(parts[5].trim()) || "true".equalsIgnoreCase(parts[5].trim());
                String targetKey = parts[6].trim();
                String itemName = parts.length >= 8 ? parts[7].trim() : itemId;
                int minTarget = parts.length >= 9 ? Math.max(0, parseIntMetricToken(parts[8])) : 8;
                int maxTarget = parts.length >= 10 ? Math.max(0, parseIntMetricToken(parts[9])) : 64;
                int stockroomAvailable = parts.length >= 11 ? parseIntMetricToken(parts[10]) : -1;
                long lastSoldMillis = parts.length >= 12 ? Math.max(0L, parseLongMetricToken(parts[11])) : 0L;
                double velocityPerDay = 0.0D;
                if (parts.length >= 13) {
                    try {
                        velocityPerDay = Double.parseDouble(parts[12].trim());
                    } catch (NumberFormatException ignored) {
                        velocityPerDay = 0.0D;
                    }
                }
                if (itemName.isBlank()) {
                    itemName = itemId;
                }
                if (maxTarget < minTarget) {
                    maxTarget = minTarget;
                }
                itemMap.computeIfAbsent(shelfIndex, ignored -> new ArrayList<>())
                        .add(new ShopInventoryItemCardData(
                                shelfIndex,
                                slotIndex,
                                itemId,
                                itemName,
                                priceCents,
                                stock,
                                restockable,
                                targetKey,
                                minTarget,
                                maxTarget,
                                stockroomAvailable,
                                lastSoldMillis,
                                Math.max(0.0D, velocityPerDay)
                        ));
            }
        }

        List<ShopInventoryShelfCardData> out = new ArrayList<>();
        for (Map.Entry<Integer, ShopInventoryShelfCardData> entry : shelfCards.entrySet()) {
            int index = entry.getKey();
            ShopInventoryShelfCardData base = entry.getValue();
            List<ShopInventoryItemCardData> items = new ArrayList<>(itemMap.getOrDefault(index, List.of()));
            items.sort(java.util.Comparator.comparingInt(ShopInventoryItemCardData::slotIndex));
            out.add(new ShopInventoryShelfCardData(
                    base.index(),
                    base.dimension(),
                    base.position(),
                    base.creative(),
                    base.configuredSlots(),
                    base.totalStock(),
                    base.lowStock(),
                    base.outOfStock(),
                    base.shelfTarget(),
                    items
            ));
        }
        for (Map.Entry<Integer, List<ShopInventoryItemCardData>> entry : itemMap.entrySet()) {
            int index = entry.getKey();
            if (shelfCards.containsKey(index)) {
                continue;
            }
            List<ShopInventoryItemCardData> items = new ArrayList<>(entry.getValue());
            items.sort(java.util.Comparator.comparingInt(ShopInventoryItemCardData::slotIndex));
            out.add(new ShopInventoryShelfCardData(
                    index,
                    "-",
                    "(?, ?, ?)",
                    false,
                    items.size(),
                    0,
                    0,
                    0,
                    "",
                    items
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopInventoryShelfCardData::index));
        return applyShopInventoryFilterAndSort(out);
    }

    private List<ShopInventoryShelfCardData> applyShopInventoryFilterAndSort(List<ShopInventoryShelfCardData> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return List.of();
        }
        String query = formValues.getOrDefault(SHOP_INVENTORY_SEARCH_KEY, "").trim().toLowerCase(Locale.ROOT);
        String[] terms = query.isBlank() ? new String[0] : query.split("\\s+");
        List<ShopInventoryShelfCardData> filteredShelves = new ArrayList<>();
        for (ShopInventoryShelfCardData shelf : shelves) {
            List<ShopInventoryItemCardData> items = shelf.items() == null ? List.of() : shelf.items();
            List<ShopInventoryItemCardData> filteredItems = new ArrayList<>();
            for (ShopInventoryItemCardData item : items) {
                if (item == null) {
                    continue;
                }
                if (!matchesShopInventorySearch(shelf, item, terms)) {
                    continue;
                }
                if (item.stock() < 0 && shopInventoryFilterMode != ShopInventoryFilterMode.ALL) {
                    continue;
                }
                if (shopInventoryFilterMode == ShopInventoryFilterMode.LOW_STOCK && item.stock() > item.minTarget()) {
                    continue;
                }
                if (shopInventoryFilterMode == ShopInventoryFilterMode.OUT_OF_STOCK && item.stock() > 0) {
                    continue;
                }
                filteredItems.add(item);
            }
            if (filteredItems.isEmpty()) {
                continue;
            }
            filteredItems.sort((a, b) -> switch (shopInventorySortMode) {
                case STOCK_ASC -> Integer.compare(a.stock(), b.stock());
                case STOCK_DESC -> Integer.compare(b.stock(), a.stock());
                case VELOCITY_DESC -> Double.compare(b.velocityPerDay(), a.velocityPerDay());
                case NAME_ASC -> {
                    String aName = a.itemName() == null ? "" : a.itemName();
                    String bName = b.itemName() == null ? "" : b.itemName();
                    yield aName.compareToIgnoreCase(bName);
                }
                case SHELF_SLOT -> Integer.compare(a.slotIndex(), b.slotIndex());
            });
            filteredShelves.add(new ShopInventoryShelfCardData(
                    shelf.index(),
                    shelf.dimension(),
                    shelf.position(),
                    shelf.creative(),
                    shelf.configuredSlots(),
                    shelf.totalStock(),
                    shelf.lowStock(),
                    shelf.outOfStock(),
                    shelf.shelfTarget(),
                    filteredItems
            ));
        }
        filteredShelves.sort((a, b) -> {
            if (shopInventorySortMode == ShopInventorySortMode.STOCK_ASC
                    || shopInventorySortMode == ShopInventorySortMode.STOCK_DESC
                    || shopInventorySortMode == ShopInventorySortMode.VELOCITY_DESC
                    || shopInventorySortMode == ShopInventorySortMode.NAME_ASC) {
                ShopInventoryItemCardData aItem = a.items().isEmpty() ? null : a.items().get(0);
                ShopInventoryItemCardData bItem = b.items().isEmpty() ? null : b.items().get(0);
                if (shopInventorySortMode == ShopInventorySortMode.STOCK_ASC) {
                    return Integer.compare(aItem == null ? Integer.MAX_VALUE : aItem.stock(),
                            bItem == null ? Integer.MAX_VALUE : bItem.stock());
                }
                if (shopInventorySortMode == ShopInventorySortMode.STOCK_DESC) {
                    return Integer.compare(bItem == null ? Integer.MIN_VALUE : bItem.stock(),
                            aItem == null ? Integer.MIN_VALUE : aItem.stock());
                }
                if (shopInventorySortMode == ShopInventorySortMode.VELOCITY_DESC) {
                    return Double.compare(
                            bItem == null ? Double.NEGATIVE_INFINITY : bItem.velocityPerDay(),
                            aItem == null ? Double.NEGATIVE_INFINITY : aItem.velocityPerDay());
                }
                String aName = aItem == null || aItem.itemName() == null ? "" : aItem.itemName();
                String bName = bItem == null || bItem.itemName() == null ? "" : bItem.itemName();
                int byName = aName.compareToIgnoreCase(bName);
                if (byName != 0) {
                    return byName;
                }
            }
            return Integer.compare(a.index(), b.index());
        });
        return filteredShelves;
    }

    private boolean matchesShopInventorySearch(ShopInventoryShelfCardData shelf,
                                               ShopInventoryItemCardData item,
                                               String[] terms) {
        if (terms == null || terms.length == 0) {
            return true;
        }
        String searchable = (String.valueOf(shelf.index()) + " "
                + (item.itemName() == null ? "" : item.itemName()) + " "
                + (item.itemId() == null ? "" : item.itemId()) + " "
                + "slot " + item.slotIndex() + " "
                + (shelf.position() == null ? "" : shelf.position()) + " "
                + (shelf.dimension() == null ? "" : shelf.dimension()))
                .toLowerCase(Locale.ROOT);
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            if (!searchable.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private int getShopInventoryCardsContentHeight(int width,
                                                   int viewportHeight,
                                                   List<ShopInventoryShelfCardData> shelves) {
        if (shelves == null || shelves.isEmpty()) {
            return viewportHeight;
        }
        int cols = width >= 760 ? 2 : 1;
        int gap = 8;
        int rows = Math.max(1, (shelves.size() + cols - 1) / cols);
        int[] rowHeights = new int[rows];
        for (int i = 0; i < shelves.size(); i++) {
            int row = i / cols;
            rowHeights[row] = Math.max(rowHeights[row], getShopInventoryShelfCardHeight(shelves.get(i)));
        }
        int total = 8;
        for (int row = 0; row < rows; row++) {
            total += Math.max(72, rowHeights[row]);
            if (row < rows - 1) {
                total += gap;
            }
        }
        total += 8;
        return Math.max(viewportHeight, total);
    }

    private int getShopStockroomCardsContentHeight(int width, int viewportHeight, int itemCount) {
        int rows = Math.max(0, itemCount);
        int rowHeight = 54;
        int rowGap = 6;
        int total = 8 + (rows * rowHeight) + (Math.max(0, rows - 1) * rowGap) + 8;
        return Math.max(viewportHeight, total);
    }

    private int getShopInventoryShelfCardHeight(ShopInventoryShelfCardData shelf) {
        if (shelf == null) {
            return 72;
        }
        int itemRows = Math.max(1, shelf.items() == null ? 0 : shelf.items().size());
        int itemRowH = 42;
        int itemGap = 5;
        int itemsBlock = (itemRows * itemRowH) + (Math.max(0, itemRows - 1) * itemGap);
        return 56 + itemsBlock + 10;
    }

    private int drawShopInventoryCards(GuiGraphics graphics,
                                       List<ShopInventoryShelfCardData> shelves,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        if (shelves == null || shelves.isEmpty()) {
            graphics.drawString(this.font, "No shelf slots match current view. Scan Shelves or change filter/sort.", x + 6, y + 6, 0xFFE6F3FF, false);
            return height;
        }
        if (shopInventorySelectedShelfTarget != null && !shopInventorySelectedShelfTarget.isBlank()) {
            boolean exists = false;
            for (ShopInventoryShelfCardData shelf : shelves) {
                if (shelf != null
                        && shelf.shelfTarget() != null
                        && !shelf.shelfTarget().isBlank()
                        && shelf.shelfTarget().equalsIgnoreCase(shopInventorySelectedShelfTarget)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                shopInventorySelectedShelfTarget = "";
            }
        }
        int gap = 8;
        int cols = width >= 760 ? 2 : 1;
        int cardW = Math.max(240, (width - (gap * (cols - 1))) / cols);
        int rows = Math.max(1, (shelves.size() + cols - 1) / cols);
        int[] rowHeights = new int[rows];
        for (int i = 0; i < shelves.size(); i++) {
            int row = i / cols;
            rowHeights[row] = Math.max(rowHeights[row], getShopInventoryShelfCardHeight(shelves.get(i)));
        }

        int contentHeight = getShopInventoryCardsContentHeight(width, height, shelves);
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        int topY = y + 4 - outputScroll;

        int[] rowYStart = new int[rows];
        int cursorY = topY;
        for (int row = 0; row < rows; row++) {
            rowYStart[row] = cursorY;
            cursorY += Math.max(72, rowHeights[row]) + gap;
        }

        for (int i = 0; i < shelves.size(); i++) {
            ShopInventoryShelfCardData shelf = shelves.get(i);
            int row = i / cols;
            int col = i % cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = rowYStart[row];
            int cardH = getShopInventoryShelfCardHeight(shelf);
            if (cardY > (y + height) || (cardY + cardH) < y) {
                continue;
            }

            boolean selectedShelf = shelf.shelfTarget() != null
                    && !shelf.shelfTarget().isBlank()
                    && shelf.shelfTarget().equalsIgnoreCase(shopInventorySelectedShelfTarget);
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, selectedShelf ? 0xFF7ED7A7 : 0xFF315274);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x7A1B334E);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, shelf.creative() ? 0xFFB58DFF : 0xFF68B7FF);
            if (shelf.shelfTarget() != null && !shelf.shelfTarget().isBlank()) {
                visibleShopInventoryShelfCards.add(new ShopInventoryShelfSelectHitbox(cardX + 1, cardY + 1, cardW - 2, 40, shelf));
            }

            String shelfTitle = "Shelf #" + shelf.index() + " • " + (shelf.creative() ? "Creative Shelf" : "Shop Shelf");
            graphics.drawString(this.font, fitToWidth(shelfTitle, cardW - 12), cardX + 6, cardY + 6, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(shelf.dimension() + " " + shelf.position(), cardW - 12), cardX + 6, cardY + 18, 0xFFCFE4F8, false);

            String stockLabel = shelf.creative() ? "INF" : String.valueOf(Math.max(0, shelf.totalStock()));
            String stats = "Configured: " + shelf.configuredSlots()
                    + "  Stock: " + stockLabel
                    + "  Low: " + shelf.lowStock()
                    + "  Out: " + shelf.outOfStock();
            graphics.drawString(this.font, fitToWidth(stats, cardW - 12), cardX + 6, cardY + 30, 0xFFBFD7EE, false);

            int itemY = cardY + 44;
            List<ShopInventoryItemCardData> items = shelf.items() == null ? List.of() : shelf.items();
            if (items.isEmpty()) {
                graphics.drawString(this.font, "No configured items in this shelf.", cardX + 8, itemY + 6, 0xFFB2CAE3, false);
                continue;
            }

            for (ShopInventoryItemCardData item : items) {
                int rowH = 42;
                graphics.fill(cardX + 5, itemY, cardX + cardW - 5, itemY + rowH, 0x6C15314D);
                graphics.fill(cardX + 5, itemY, cardX + cardW - 5, itemY + 1, 0x5578B7EA);

                ItemStack iconStack = resolveShopInventoryIcon(item.itemId());
                if (!iconStack.isEmpty()) {
                    graphics.renderItem(iconStack, cardX + 9, itemY + 6);
                } else {
                    graphics.fill(cardX + 9, itemY + 6, cardX + 25, itemY + 22, 0x663A5578);
                }

                int actionButtonW = 50;
                int actionGap = 4;
                int removeX = cardX + cardW - actionButtonW - 10;
                int restockX = removeX - actionButtonW - actionGap;
                int topButtonY = itemY + 4;
                int adjustY = itemY + 22;
                int microW = 14;
                int minMinusX = restockX;
                int minPlusX = minMinusX + microW + 2;
                int maxMinusX = minPlusX + microW + 6;
                int maxPlusX = maxMinusX + microW + 2;
                int textW = Math.max(40, restockX - (cardX + 30) - 6);

                String itemTitle = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
                graphics.drawString(this.font, fitToWidth(itemTitle, textW), cardX + 30, itemY + 4, 0xFFEAF5FF, false);

                String priceLabel = item.priceCents() <= 0L
                        ? "Free"
                        : MoneyText.abbreviateWithDollar(BigDecimal.valueOf(item.priceCents(), 2));
                String stockText = item.stock() < 0 ? "INF" : String.valueOf(Math.max(0, item.stock()));
                graphics.drawString(this.font,
                        fitToWidth("Slot " + item.slotIndex() + " • " + priceLabel + " • Stock " + stockText, textW),
                        cardX + 30,
                        itemY + 14,
                        0xFFC6DEF6,
                        false);
                String preview = item.stockroomAvailable() < 0 ? "-" : String.valueOf(Math.max(0, item.stockroomAvailable()));
                String soldLabel = item.lastSoldMillis() <= 0L
                        ? "never"
                        : formatRelativeTime(item.lastSoldMillis());
                String targets = "Target " + item.minTarget() + "-" + item.maxTarget()
                        + " • Stockroom " + preview
                        + " • Vel " + String.format(Locale.ROOT, "%.2f", Math.max(0.0D, item.velocityPerDay())) + "/d"
                        + " • Last " + soldLabel;
                graphics.drawString(this.font,
                        fitToWidth(targets, textW),
                        cardX + 30,
                        itemY + 24,
                        0xFFB8D2EA,
                        false);

                if (item.restockable() && item.targetKey() != null && !item.targetKey().isBlank()) {
                    drawInlineActionButton(graphics, restockX, topButtonY, actionButtonW, 14, "Restock", 0xFF67BC86);
                    visibleShopInventoryActions.add(new ShopInventoryActionHitbox(restockX, topButtonY, actionButtonW, 14, "RESTOCK", item, true));
                } else {
                    String disabledLabel = item.stock() < 0 ? "INF" : (item.stock() >= item.maxTarget() ? "Full" : "N/A");
                    drawInlineActionButton(graphics, restockX, topButtonY, actionButtonW, 14, disabledLabel, 0xFF5C7896);
                    visibleShopInventoryActions.add(new ShopInventoryActionHitbox(restockX, topButtonY, actionButtonW, 14, "RESTOCK", item, false));
                }
                boolean canRemove = item.targetKey() != null && !item.targetKey().isBlank() && !shelf.creative();
                drawInlineActionButton(graphics, removeX, topButtonY, actionButtonW, 14, "Remove", canRemove ? 0xFFE09090 : 0xFF5C7896);
                visibleShopInventoryActions.add(new ShopInventoryActionHitbox(removeX, topButtonY, actionButtonW, 14, "REMOVE", item, canRemove));

                boolean canAdjustTargets = !shelf.creative() && item.targetKey() != null && !item.targetKey().isBlank();
                drawInlineActionButton(graphics, minMinusX, adjustY, microW, 12, "-", canAdjustTargets ? 0xFF6BAED6 : 0xFF5C7896);
                drawInlineActionButton(graphics, minPlusX, adjustY, microW, 12, "+", canAdjustTargets ? 0xFF6BAED6 : 0xFF5C7896);
                drawInlineActionButton(graphics, maxMinusX, adjustY, microW, 12, "-", canAdjustTargets ? 0xFF8C9DE0 : 0xFF5C7896);
                drawInlineActionButton(graphics, maxPlusX, adjustY, microW, 12, "+", canAdjustTargets ? 0xFF8C9DE0 : 0xFF5C7896);
                visibleShopInventoryActions.add(new ShopInventoryActionHitbox(minMinusX, adjustY, microW, 12, "MIN_DEC", item, canAdjustTargets));
                visibleShopInventoryActions.add(new ShopInventoryActionHitbox(minPlusX, adjustY, microW, 12, "MIN_INC", item, canAdjustTargets));
                visibleShopInventoryActions.add(new ShopInventoryActionHitbox(maxMinusX, adjustY, microW, 12, "MAX_DEC", item, canAdjustTargets));
                visibleShopInventoryActions.add(new ShopInventoryActionHitbox(maxPlusX, adjustY, microW, 12, "MAX_INC", item, canAdjustTargets));

                itemY += rowH + 5;
            }
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private List<ShopStockroomItemCardData> parseShopStockroomCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopStockroomItemCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@stockroom_item=")) {
                continue;
            }
            String payload = line.substring("@stockroom_item=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 12) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            int xPos = parseIntMetricToken(parts[6]);
            int yPos = parseIntMetricToken(parts[7]);
            int zPos = parseIntMetricToken(parts[8]);
            out.add(new ShopStockroomItemCardData(
                    index,
                    parts[1].trim(),
                    parts[2].trim(),
                    Math.max(0, parseIntMetricToken(parts[3])),
                    parts[4].trim(),
                    parts[5].trim(),
                    "(" + xPos + ", " + yPos + ", " + zPos + ")",
                    Math.max(1, parseIntMetricToken(parts[9])),
                    Math.max(1, parseIntMetricToken(parts[10])),
                    parts[11].trim()
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopStockroomItemCardData::index));
        return applyShopStockroomSearch(out);
    }

    private List<ShopStockroomItemCardData> applyShopStockroomSearch(List<ShopStockroomItemCardData> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        String query = formValues.getOrDefault(SHOP_STOCKROOM_SEARCH_KEY, "").trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return items;
        }
        String[] terms = query.split("\\s+");
        List<ShopStockroomItemCardData> filtered = new ArrayList<>();
        for (ShopStockroomItemCardData item : items) {
            if (item == null) {
                continue;
            }
            String searchable = (String.valueOf(item.index()) + " "
                    + (item.itemName() == null ? "" : item.itemName()) + " "
                    + (item.itemId() == null ? "" : item.itemId()) + " "
                    + (item.inventoryType() == null ? "" : item.inventoryType()) + " "
                    + (item.dimension() == null ? "" : item.dimension()) + " "
                    + (item.position() == null ? "" : item.position()) + " "
                    + "slot " + item.slot() + " "
                    + item.totalSlots())
                    .toLowerCase(Locale.ROOT);
            boolean matches = true;
            for (String term : terms) {
                if (term == null || term.isBlank()) {
                    continue;
                }
                if (!searchable.contains(term)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    private int drawShopStockroomCards(GuiGraphics graphics,
                                       List<ShopStockroomItemCardData> items,
                                       int x,
                                       int y,
                                       int width,
                                       int height) {
        if (items == null || items.isEmpty()) {
            graphics.drawString(this.font, "No stockroom inventory entries found. Claim stockroom and refresh.", x + 6, y + 6, 0xFFE6F3FF, false);
            return height;
        }
        int rowHeight = 54;
        int rowGap = 6;
        int contentHeight = Math.max(height, 8 + (items.size() * rowHeight) + (Math.max(0, items.size() - 1) * rowGap) + 8);
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        int cardY = y + 4 - outputScroll;
        int cardX = x + 5;
        int cardW = Math.max(220, width - 10);
        for (ShopStockroomItemCardData item : items) {
            if (cardY > (y + height) || (cardY + rowHeight) < y) {
                cardY += rowHeight + rowGap;
                continue;
            }
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + rowHeight + 1, 0xFF315274);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + rowHeight, 0x7A1B334E);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, 0xFF6EB9FF);

            ItemStack iconStack = resolveShopInventoryIcon(item.itemId());
            if (!iconStack.isEmpty()) {
                graphics.renderItem(iconStack, cardX + 8, cardY + 8);
            } else {
                graphics.fill(cardX + 8, cardY + 8, cardX + 24, cardY + 24, 0x663A5578);
            }

            int buttonW = 54;
            int buttonH = 16;
            int locateX = cardX + cardW - buttonW - 10;
            int locateY = cardY + 18;
            drawInlineActionButton(graphics, locateX, locateY, buttonW, buttonH, "Locate", 0xFF67BC86);
            visibleShopStockroomLocateActions.add(new ShopStockroomLocateHitbox(locateX, locateY, buttonW, buttonH, item));

            int textW = Math.max(40, locateX - (cardX + 30) - 8);
            String itemName = item.itemName() == null || item.itemName().isBlank() ? item.itemId() : item.itemName();
            graphics.drawString(this.font, fitToWidth(itemName, textW), cardX + 30, cardY + 6, 0xFFEAF5FF, false);
            graphics.drawString(this.font, fitToWidth("Count " + item.count() + " • Slot " + item.slot() + "/" + item.totalSlots(), textW),
                    cardX + 30, cardY + 17, 0xFFC6DEF6, false);
            graphics.drawString(this.font, fitToWidth(item.inventoryType(), textW), cardX + 30, cardY + 28, 0xFFB8D2EA, false);
            graphics.drawString(this.font, fitToWidth(item.dimension() + " " + item.position(), textW), cardX + 30, cardY + 39, 0xFFB2CAE3, false);
            cardY += rowHeight + rowGap;
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    // Governance panel order manager uses a custom card layout, so it computes
    // its own scrollable content height instead of plain wrapped text rows.
    private int getShopOrderManagerContentHeight(int width,
                                                 int viewportHeight,
                                                 int orderCount,
                                                 int palletCount,
                                                 int pickCount,
                                                 OrderBoardSummary summary) {
        int total = 8;

        if (shopOrderPickerOpen) {
            int cols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
            int rows = Math.max(1, (pickCount + cols - 1) / cols);
            int cardH = 46;
            int gap = 6;
            total += 16;
            if (pickCount <= 0) {
                total += 22;
            } else {
                total += (rows * cardH) + (Math.max(0, rows - 1) * gap);
            }
        } else if (shopOrderPalletPickerOpen) {
            int cols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
            int rows = Math.max(1, (palletCount + cols - 1) / cols);
            int cardH = 42;
            int gap = 6;
            total += 16;
            if (palletCount <= 0) {
                total += 22;
            } else {
                total += (rows * cardH) + (Math.max(0, rows - 1) * gap);
            }
        } else {
            int summaryCards = 4;
            int summaryCols = width >= 620 ? 4 : width >= 420 ? 2 : 1;
            int summaryRows = (summaryCards + summaryCols - 1) / summaryCols;
            int summaryCardH = 42;
            int summaryGap = 6;
            total += (summaryRows * summaryCardH) + (Math.max(0, summaryRows - 1) * summaryGap);
            total += 10;

            int orderCardH = 58;
            int orderGap = 6;
            total += 16;
            if (orderCount <= 0) {
                total += 22;
            } else {
                total += (orderCount * orderCardH) + (Math.max(0, orderCount - 1) * orderGap);
            }
            total += 10;

            int palletCols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
            int palletRows = Math.max(1, (palletCount + palletCols - 1) / palletCols);
            int palletCardH = 40;
            int palletGap = 6;
            total += 16;
            if (palletCount <= 0) {
                total += 22;
            } else {
                total += (palletRows * palletCardH) + (Math.max(0, palletRows - 1) * palletGap);
            }
        }

        total += 8;
        return Math.max(viewportHeight, total);
    }

    private int drawShopOrderManagerCards(GuiGraphics graphics,
                                          List<ShopOrderCardData> orders,
                                          List<ShopOrderPalletCardData> pallets,
                                          List<ShopOrderPickCardData> picks,
                                          OrderBoardSummary summary,
                                          int x,
                                          int y,
                                          int width,
                                          int height) {
        List<ShopOrderCardData> safeOrders = orders == null ? List.of() : orders;
        List<ShopOrderPalletCardData> safePallets = pallets == null ? List.of() : pallets;
        List<ShopOrderPickCardData> safePicks = picks == null ? List.of() : picks;
        if (shopOrderPickerOpen) {
            safePicks = filterShopOrderPickCards(safePicks);
        }

        boolean orderStillExists = false;
        for (ShopOrderCardData order : safeOrders) {
            if (order != null && order.orderId() != null && order.orderId().equalsIgnoreCase(shopOrderSelectedId)) {
                orderStillExists = true;
                break;
            }
        }
        if (!orderStillExists) {
            shopOrderSelectedId = "";
        }

        boolean palletStillExists = false;
        for (ShopOrderPalletCardData pallet : safePallets) {
            if (pallet != null && pallet.palletRef() != null && pallet.palletRef().equalsIgnoreCase(shopOrderSelectedPalletRef)) {
                palletStillExists = true;
                break;
            }
        }
        if (!palletStillExists) {
            shopOrderSelectedPalletRef = "";
        }

        if (shopOrderPickerOpen) {
            boolean pickStillExists = false;
            for (ShopOrderPickCardData pick : safePicks) {
                if (pick != null && pick.itemId() != null && pick.itemId().equalsIgnoreCase(shopOrderSelectedItemId)) {
                    pickStillExists = true;
                    break;
                }
            }
            if (!pickStillExists) {
                shopOrderSelectedItemId = "";
                shopOrderSelectedItemName = "";
            }
        }

        int contentHeight = getShopOrderManagerContentHeight(
                width,
                height,
                safeOrders.size(),
                safePallets.size(),
                safePicks.size(),
                summary
        );
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }

        int cursorY = y + 4 - outputScroll;
        if (shopOrderPickerOpen) {
            graphics.drawString(this.font, "Shelf Item Picker (click a card, then press Select Item)", x + 6, cursorY + 4, 0xFFE7F4FF, false);
            cursorY += 16;

            String pickQuery = formValues.getOrDefault(SHOP_ORDER_PICK_SEARCH_KEY, "").trim();
            if (!pickQuery.isBlank()) {
                graphics.drawString(this.font, fitToWidth("Filter: " + pickQuery, Math.max(20, width - 12)), x + 6, cursorY + 2, 0xFFBEDAF2, false);
                cursorY += 12;
            }

            int cols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
            int gap = 6;
            int cardW = Math.max(150, (width - (gap * (cols - 1))) / cols);
            int cardH = 46;
            if (safePicks.isEmpty()) {
                String emptyText = pickQuery.isBlank()
                        ? "No display items were found on your shop shelves."
                        : "No display items match your search filter.";
                graphics.drawString(this.font, emptyText, x + 8, cursorY + 6, 0xFFC7DDF2, false);
                cursorY += 22;
            } else {
                for (int i = 0; i < safePicks.size(); i++) {
                    ShopOrderPickCardData pick = safePicks.get(i);
                    int col = i % cols;
                    int row = i / cols;
                    int cardX = x + (col * (cardW + gap));
                    int cardY = cursorY + (row * (cardH + gap));
                    if (cardY > (y + height) || (cardY + cardH) < y) {
                        continue;
                    }
                    boolean selected = pick.itemId() != null && pick.itemId().equalsIgnoreCase(shopOrderSelectedItemId);
                    graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, selected ? 0xFF7ED7A7 : 0xFF315274);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, selected ? 0x8C1F3E57 : 0x7A1B334E);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, selected ? 0xFF7ED7A7 : 0xFF69B8FF);

                    ItemStack iconStack = resolveShopInventoryIcon(pick.itemId());
                    int iconX = cardX + 6;
                    int iconY = cardY + Math.max(3, (cardH - 16) / 2);
                    if (!iconStack.isEmpty()) {
                        graphics.renderItem(iconStack, iconX, iconY);
                    }

                    int textX = iconX + 20;
                    int textW = Math.max(42, cardW - 26);
                    String pickName = pick.itemName() == null || pick.itemName().isBlank() ? pick.itemId() : pick.itemName();
                    graphics.drawString(this.font, fitToWidth(pickName, textW - 8), textX, cardY + 6, 0xFFFFFFFF, false);
                    graphics.drawString(this.font,
                            fitToWidth("In stock " + abbreviateStockCount(pick.availableCount()) + " • Max stack " + Math.max(1, pick.maxStack()), textW - 8),
                            textX,
                            cardY + 18,
                            0xFFCFE4F8,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth(pick.itemId(), textW - 8),
                            textX,
                            cardY + 30,
                            0xFFB7D0E8,
                            false);
                    visibleShopOrderPickCards.add(new ShopOrderPickCardHitbox(cardX, cardY, cardW, cardH, pick));
                }
            }
        } else if (shopOrderPalletPickerOpen) {
            graphics.drawString(this.font, "Delivery Pallet Picker (click a card, then press Select Pallet)", x + 6, cursorY + 4, 0xFFE7F4FF, false);
            cursorY += 16;

            int cols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
            int gap = 6;
            int cardW = Math.max(160, (width - (gap * (cols - 1))) / cols);
            int cardH = 42;
            if (safePallets.isEmpty()) {
                graphics.drawString(this.font, "No labeled delivery pallets found in claimed shop plot.", x + 8, cursorY + 6, 0xFFC7DDF2, false);
                cursorY += 22;
            } else {
                for (int i = 0; i < safePallets.size(); i++) {
                    ShopOrderPalletCardData pallet = safePallets.get(i);
                    int col = i % cols;
                    int row = i / cols;
                    int cardX = x + (col * (cardW + gap));
                    int cardY = cursorY + (row * (cardH + gap));
                    if (cardY > (y + height) || (cardY + cardH) < y) {
                        continue;
                    }
                    boolean selected = pallet.palletRef() != null && pallet.palletRef().equalsIgnoreCase(shopOrderSelectedPalletRef);
                    int accent = pallet.full() ? 0xFFE58B8B : 0xFF7CD6B0;
                    graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, selected ? 0xFF7ED7A7 : 0xFF315274);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, selected ? 0x8C1F3E57 : 0x7A1B334E);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, selected ? 0xFF7ED7A7 : accent);

                    graphics.drawString(this.font,
                            fitToWidth("Delivery " + (pallet.full() ? "• FULL" : "• READY"), cardW - 12),
                            cardX + 6,
                            cardY + 6,
                            0xFFFFFFFF,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth("ID " + shortUuid(pallet.palletRef()), cardW - 12),
                            cardX + 6,
                            cardY + 18,
                            0xFFCFE4F8,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth("Pos " + pallet.x() + ", " + pallet.y() + ", " + pallet.z(), cardW - 12),
                            cardX + 6,
                            cardY + 30,
                            0xFFB7D0E8,
                            false);
                    visibleShopOrderPalletCards.add(new ShopOrderPalletCardHitbox(cardX, cardY, cardW, cardH, pallet));
                }
            }
        } else {
            int summaryActiveMine = summary == null ? 0 : Math.max(0, summary.activeMine());
            int summaryActiveCap = summary == null ? 0 : Math.max(0, summary.activeCap());
            long summaryOpen = summary == null ? safeOrders.stream()
                    .filter(order -> order != null && "OPEN".equalsIgnoreCase(order.status()))
                    .count() : Math.max(0L, summary.totalOpen());
            long summaryVisible = summary == null ? safeOrders.size() : Math.max(0L, summary.totalVisible());

            int summaryGap = 6;
            int summaryCols = width >= 620 ? 4 : width >= 420 ? 2 : 1;
            int summaryCardW = Math.max(110, (width - (summaryGap * (summaryCols - 1))) / summaryCols);
            int summaryCardH = 42;
            String[] labels = {
                    "Open Orders",
                    "Visible Orders",
                    "My Active Orders",
                    "Courier Cap"
            };
            String[] values = {
                    String.valueOf(summaryOpen),
                    String.valueOf(summaryVisible),
                    String.valueOf(summaryActiveMine),
                    String.valueOf(summaryActiveCap)
            };
            int[] accents = {
                    0xFF6EB9FF,
                    0xFF8DC7FF,
                    0xFF7CD6B0,
                    0xFFC4B57A
            };
            for (int i = 0; i < labels.length; i++) {
                int col = i % summaryCols;
                int row = i / summaryCols;
                int cardX = x + (col * (summaryCardW + summaryGap));
                int cardY = cursorY + (row * (summaryCardH + summaryGap));
                drawMetricCard(graphics, cardX, cardY, summaryCardW, summaryCardH, labels[i], values[i], accents[i]);
            }
            int summaryRows = (labels.length + summaryCols - 1) / summaryCols;
            cursorY += (summaryRows * (summaryCardH + summaryGap)) + 4;

            graphics.drawString(this.font, "Delivery Orders", x + 6, cursorY + 4, 0xFFE7F4FF, false);
            cursorY += 16;

            int orderCardH = 58;
            int orderGap = 6;
            if (safeOrders.isEmpty()) {
                graphics.drawString(this.font, "No orders yet. Use Pick Item From Shelves + Create Delivery Order.", x + 8, cursorY + 6, 0xFFC7DDF2, false);
                cursorY += 22;
            } else {
                for (ShopOrderCardData order : safeOrders) {
                    int cardX = x + 4;
                    int cardY = cursorY;
                    int cardW = Math.max(180, width - 8);
                    if (cardY > (y + height) || (cardY + orderCardH) < y) {
                        cursorY += orderCardH + orderGap;
                        continue;
                    }
                    boolean selected = order.orderId() != null && order.orderId().equalsIgnoreCase(shopOrderSelectedId);
                    int accent = "ACCEPTED".equalsIgnoreCase(order.status()) ? 0xFF78C8A8
                            : "COMPLETED".equalsIgnoreCase(order.status()) ? 0xFF6EB9FF
                            : "CANCELED".equalsIgnoreCase(order.status()) ? 0xFFE58B8B
                            : "EXPIRED".equalsIgnoreCase(order.status()) ? 0xFFC4B57A
                            : 0xFF69B8FF;
                    graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + orderCardH + 1, selected ? 0xFF7ED7A7 : 0xFF315274);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + orderCardH, selected ? 0x8C1F3E57 : 0x7A1B334E);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, accent);

                    String itemName = order.itemName() == null || order.itemName().isBlank() ? order.itemId() : order.itemName();
                    graphics.drawString(this.font,
                            fitToWidth(itemName + " x" + Math.max(1, order.quantity()), cardW - 12),
                            cardX + 6,
                            cardY + 6,
                            0xFFFFFFFF,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth("Reward " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, order.rewardCents()), 2))
                                    + " • Status " + order.status(), cardW - 12),
                            cardX + 6,
                            cardY + 18,
                            0xFFCFE4F8,
                            false);
                    String acceptedBy = order.acceptedBy() == null || order.acceptedBy().isBlank() ? "-" : order.acceptedBy();
                    String timeInfo = "OPEN".equalsIgnoreCase(order.status())
                            ? ("Timeout " + Math.max(5, order.timeoutMinutes()) + "m")
                            : ("Time left " + Math.max(0L, order.remainingSeconds()) + "s");
                    graphics.drawString(this.font,
                            fitToWidth("Courier " + acceptedBy + " • " + timeInfo, cardW - 12),
                            cardX + 6,
                            cardY + 30,
                            0xFFBDD5EC,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth("Drop " + formatPalletRefLabel(order.boundPalletRef())
                                    + " • Created " + formatRelativeTime(order.createdAtMillis()), cardW - 12),
                            cardX + 6,
                            cardY + 42,
                            0xFFAAC4DE,
                            false);

                    visibleShopOrderCards.add(new ShopOrderCardHitbox(cardX, cardY, cardW, orderCardH, order));
                    cursorY += orderCardH + orderGap;
                }
            }

            cursorY += 4;
            graphics.drawString(this.font, "Assigned Delivery Pallets", x + 6, cursorY + 4, 0xFFE7F4FF, false);
            cursorY += 16;

            if (safePallets.isEmpty()) {
                graphics.drawString(this.font, "No pallets detected in claimed plot.", x + 8, cursorY + 6, 0xFFC7DDF2, false);
            } else {
                int cols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
                int gap = 6;
                int cardW = Math.max(160, (width - (gap * (cols - 1))) / cols);
                int cardH = 40;
                for (int i = 0; i < safePallets.size(); i++) {
                    ShopOrderPalletCardData pallet = safePallets.get(i);
                    int col = i % cols;
                    int row = i / cols;
                    int cardX = x + (col * (cardW + gap));
                    int cardY = cursorY + (row * (cardH + gap));
                    if (cardY > (y + height) || (cardY + cardH) < y) {
                        continue;
                    }
                    boolean selected = pallet.palletRef() != null && pallet.palletRef().equalsIgnoreCase(shopOrderSelectedPalletRef);
                    int accent = pallet.full() ? 0xFFE58B8B : (pallet.assigned() ? 0xFF7CD6B0 : 0xFF69B8FF);
                    graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, selected ? 0xFF7ED7A7 : 0xFF315274);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, selected ? 0x8C1F3E57 : 0x7A1B334E);
                    graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, accent);

                    graphics.drawString(this.font,
                            fitToWidth((pallet.assigned() ? "Labeled" : "Available")
                                    + " • " + (pallet.full() ? "FULL" : "SPACE")
                                    + " • #" + pallet.index(), cardW - 12),
                            cardX + 6,
                            cardY + 6,
                            0xFFFFFFFF,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth(pallet.dimension(), cardW - 12),
                            cardX + 6,
                            cardY + 18,
                            0xFFCFE4F8,
                            false);
                    graphics.drawString(this.font,
                            fitToWidth("Pos " + pallet.x() + ", " + pallet.y() + ", " + pallet.z(), cardW - 12),
                            cardX + 6,
                            cardY + 30,
                            0xFFB7D0E8,
                            false);

                    visibleShopOrderPalletCards.add(new ShopOrderPalletCardHitbox(cardX, cardY, cardW, cardH, pallet));
                }
            }
        }

        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private List<ShopOrderCardData> parseShopOrderCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopOrderCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@shop_order=")) {
                continue;
            }
            String payload = line.substring("@shop_order=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 11) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String orderId = parts[1].trim();
            if (orderId.isBlank()) {
                continue;
            }
            out.add(new ShopOrderCardData(
                    index,
                    orderId,
                    parts[2].trim(),
                    parts[3].trim(),
                    Math.max(1, parseIntMetricToken(parts[4])),
                    Math.max(0L, parseLongMetricToken(parts[5])),
                    parts[6].trim(),
                    parts[7].trim(),
                    Math.max(0L, parseLongMetricToken(parts[8])),
                    Math.max(5, parseIntMetricToken(parts[9])),
                    Math.max(0L, parseLongMetricToken(parts[10])),
                    parts.length >= 12 ? parts[11].trim() : ""
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopOrderCardData::index));
        return out;
    }

    private List<ShopOrderPalletCardData> parseShopOrderPalletCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopOrderPalletCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@shop_order_pallet=")) {
                continue;
            }
            String payload = line.substring("@shop_order_pallet=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 7) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String ref = parts[1].trim();
            if (ref.isBlank()) {
                continue;
            }
            boolean assigned = "1".equals(parts[6].trim()) || "true".equalsIgnoreCase(parts[6].trim());
            boolean full = parts.length >= 8
                    && ("1".equals(parts[7].trim()) || "true".equalsIgnoreCase(parts[7].trim()));
            out.add(new ShopOrderPalletCardData(
                    index,
                    ref,
                    parts[2].trim(),
                    parseIntMetricToken(parts[3]),
                    parseIntMetricToken(parts[4]),
                    parseIntMetricToken(parts[5]),
                    assigned,
                    full
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopOrderPalletCardData::index));
        return out;
    }

    private List<ShopOrderPickCardData> parseShopOrderPickCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopOrderPickCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@shop_order_pick=")) {
                continue;
            }
            String payload = line.substring("@shop_order_pick=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String itemId = parts[1].trim();
            if (itemId.isBlank()) {
                continue;
            }
            String itemName = parts[2].trim();
            if (itemName.isBlank()) {
                itemName = itemId;
            }
            out.add(new ShopOrderPickCardData(
                    index,
                    itemId,
                    itemName,
                    Math.max(1, parseIntMetricToken(parts[3])),
                    Math.max(0, parseIntMetricToken(parts[4]))
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopOrderPickCardData::index));
        return out;
    }

    private List<ShopOrderPickCardData> filterShopOrderPickCards(List<ShopOrderPickCardData> picks) {
        if (picks == null || picks.isEmpty()) {
            return List.of();
        }
        String query = formValues.getOrDefault(SHOP_ORDER_PICK_SEARCH_KEY, "").trim().toLowerCase(Locale.ROOT);
        if (query.isBlank()) {
            return picks;
        }
        List<ShopOrderPickCardData> out = new ArrayList<>();
        for (ShopOrderPickCardData pick : picks) {
            if (pick == null) {
                continue;
            }
            String itemId = pick.itemId() == null ? "" : pick.itemId().toLowerCase(Locale.ROOT);
            String itemName = pick.itemName() == null ? "" : pick.itemName().toLowerCase(Locale.ROOT);
            if (itemId.contains(query) || itemName.contains(query)) {
                out.add(pick);
            }
        }
        return out;
    }

    private OrderBoardSummary parseOrderBoardSummary(List<String> lines) {
        Map<String, String> values = parseAtTokenMap(lines);
        if (!values.containsKey("order.board.total_visible")
                && !values.containsKey("order.manager.open")
                && !values.containsKey("order.manager.assigned_pallets")) {
            return null;
        }
        int activeMine = parseIntMetricToken(values.get("order.board.active_mine"));
        int activeCap = parseIntMetricToken(values.get("order.board.active_cap"));
        long totalOpen = values.containsKey("order.board.total_open")
                ? parseLongMetricToken(values.get("order.board.total_open"))
                : parseLongMetricToken(values.get("order.manager.open"));
        long totalVisible = values.containsKey("order.board.total_visible")
                ? parseLongMetricToken(values.get("order.board.total_visible"))
                : (parseLongMetricToken(values.get("order.manager.open")) + parseLongMetricToken(values.get("order.manager.accepted")));
        long completedTotal = parseLongMetricToken(values.get("order.board.completed_total"));
        long canceledTotal = parseLongMetricToken(values.get("order.board.canceled_total"));
        long streak = parseLongMetricToken(values.get("order.board.streak"));
        long bestStreak = parseLongMetricToken(values.get("order.board.best_streak"));
        long totalPayoutCents = parseLongMetricToken(values.get("order.board.total_payout_cents"));
        int successRatePct = parseIntMetricToken(values.get("order.board.success_rate_pct"));
        long nextRankAt = parseLongMetricToken(values.get("order.board.next_rank_at"));
        long nextStreakAt = parseLongMetricToken(values.get("order.board.next_streak_at"));
        String rankLabel = values.getOrDefault("order.board.rank", "-");
        return new OrderBoardSummary(
                Math.max(0, activeMine),
                Math.max(0, activeCap),
                Math.max(0L, totalOpen),
                Math.max(0L, totalVisible),
                Math.max(0L, completedTotal),
                Math.max(0L, canceledTotal),
                Math.max(0L, streak),
                Math.max(0L, bestStreak),
                Math.max(0L, totalPayoutCents),
                Math.max(0, Math.min(100, successRatePct)),
                Math.max(0L, nextRankAt),
                Math.max(0L, nextStreakAt),
                rankLabel == null || rankLabel.isBlank() ? "-" : rankLabel
        );
    }

    private List<OrderBoardCardData> parseOrderBoardCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<OrderBoardCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@order_board_order=")) {
                continue;
            }
            String payload = line.substring("@order_board_order=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 13) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String orderId = parts[1].trim();
            if (orderId.isBlank()) {
                continue;
            }
            out.add(new OrderBoardCardData(
                    index,
                    orderId,
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    Math.max(1, parseIntMetricToken(parts[6])),
                    Math.max(0L, parseLongMetricToken(parts[7])),
                    parts[8].trim(),
                    parts[9].trim(),
                    Math.max(0L, parseLongMetricToken(parts[10])),
                    Math.max(5, parseIntMetricToken(parts[11])),
                    Math.max(0L, parseLongMetricToken(parts[12])),
                    parts.length >= 14 ? parts[13].trim() : ""
            ));
        }
        out.sort(java.util.Comparator.comparingInt(OrderBoardCardData::index));
        return out;
    }

    private int getOrderBoardCardsContentHeight(int width, int viewportHeight, List<OrderBoardCardData> cards) {
        int count = cards == null ? 0 : cards.size();
        int summaryCards = 13;
        int summaryCols = width >= 760 ? 5 : width >= 560 ? 4 : width >= 420 ? 2 : 1;
        int summaryRows = (summaryCards + summaryCols - 1) / summaryCols;
        int summaryH = (summaryRows * 42) + (Math.max(0, summaryRows - 1) * 6);
        int cardH = 66;
        int cardGap = 6;
        int listH = count <= 0 ? 28 : (count * cardH) + (Math.max(0, count - 1) * cardGap);
        int total = 8 + summaryH + 10 + listH + 8;
        return Math.max(viewportHeight, total);
    }

    // Standalone courier-facing order board utility app.
    private void drawOrderBoardApp(GuiGraphics graphics, int x, int y, int width, int height) {
        visibleOrderBoardCards.clear();
        visibleOrderBoardKpiCards.clear();
        List<String> raw = ClientOwnerPcData.getActionOutputLines();
        OrderBoardSummary summary = parseOrderBoardSummary(raw);
        List<OrderBoardCardData> cards = parseOrderBoardCards(raw);

        boolean selectionExists = false;
        for (OrderBoardCardData card : cards) {
            if (card != null && card.orderId() != null && card.orderId().equalsIgnoreCase(orderBoardSelectedOrderId)) {
                selectionExists = true;
                break;
            }
        }
        if (!selectionExists) {
            orderBoardSelectedOrderId = "";
        }

        int viewX = orderBoardViewportX > 0 ? orderBoardViewportX : (x + 4);
        int viewY = orderBoardViewportY > 0 ? orderBoardViewportY : (y + 4);
        int viewW = orderBoardViewportW > 0 ? orderBoardViewportW : Math.max(120, width - 8);
        int viewH = orderBoardViewportH > 0 ? orderBoardViewportH : Math.max(80, height - 8);

        graphics.fill(viewX - 1, viewY - 1, viewX + viewW + 1, viewY + viewH + 1, 0xFF2D4B6D);
        graphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xC0182F47);
        graphics.fill(viewX, viewY, viewX + viewW, viewY + 2, 0xFF72BDF5);

        int contentHeight = getOrderBoardCardsContentHeight(viewW, viewH, cards);
        int maxScroll = Math.max(0, contentHeight - viewH);
        orderBoardMaxScroll = maxScroll;
        orderBoardScroll = Math.max(0, Math.min(orderBoardScroll, orderBoardMaxScroll));

        int activeMine = summary == null ? 0 : Math.max(0, summary.activeMine());
        int activeCap = summary == null ? 0 : Math.max(0, summary.activeCap());
        long totalOpen = summary == null ? cards.stream().filter(c -> c != null && "OPEN".equalsIgnoreCase(c.status())).count() : Math.max(0L, summary.totalOpen());
        long totalVisible = summary == null ? cards.size() : Math.max(0L, summary.totalVisible());
        long completedTotal = summary == null ? 0L : Math.max(0L, summary.completedTotal());
        long canceledTotal = summary == null ? 0L : Math.max(0L, summary.canceledTotal());
        long streak = summary == null ? 0L : Math.max(0L, summary.streak());
        long bestStreak = summary == null ? 0L : Math.max(0L, summary.bestStreak());
        long payoutCents = summary == null ? 0L : Math.max(0L, summary.totalPayoutCents());
        int successRate = summary == null ? 100 : Math.max(0, Math.min(100, summary.successRatePct()));
        long nextRankAt = summary == null ? 0L : Math.max(0L, summary.nextRankAt());
        long nextStreakAt = summary == null ? 0L : Math.max(0L, summary.nextStreakAt());
        String rank = summary == null || summary.rankLabel() == null || summary.rankLabel().isBlank()
                ? "-"
                : summary.rankLabel();

        if (!useVirtualScale) {
            enableScaledScissor(graphics, viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
        }

        int cursorY = viewY + 4 - orderBoardScroll;
        int summaryGap = 6;
        int summaryCols = viewW >= 760 ? 5 : viewW >= 560 ? 4 : viewW >= 420 ? 2 : 1;
        int summaryCardW = Math.max(110, (viewW - (summaryGap * (summaryCols - 1))) / summaryCols);
        int summaryCardH = 42;
        String[] labels = {"Open", "Visible", "My Active", "Active Cap",
                "Completed", "Canceled", "Success", "Streak",
                "Best Streak", "Paid", "Rank", "Next Rank", "Next Streak"};
        String[] values = {
                String.valueOf(totalOpen),
                String.valueOf(totalVisible),
                String.valueOf(activeMine),
                String.valueOf(activeCap),
                String.valueOf(completedTotal),
                String.valueOf(canceledTotal),
                successRate + "%",
                String.valueOf(streak),
                String.valueOf(bestStreak),
                MoneyText.abbreviateWithDollar(BigDecimal.valueOf(payoutCents, 2)),
                rank,
                nextRankAt <= 0L ? "-" : String.valueOf(nextRankAt),
                nextStreakAt <= 0L ? "-" : String.valueOf(nextStreakAt)
        };
        String[] descriptions = {
                "Total currently open courier orders available to be accepted.",
                "Total orders currently visible on the board (open + accepted).",
                "How many orders you currently have in progress.",
                "Maximum simultaneous active orders you can hold at your current rank.",
                "Total delivery orders you have completed successfully.",
                "Total orders canceled or failed before completion.",
                "Completion success rate based on your completed vs canceled history.",
                "Current consecutive successful delivery streak.",
                "Your highest recorded consecutive successful delivery streak.",
                "Total payout received from completed deliveries on this account.",
                "Current courier rank name used for progression and reward scaling.",
                "Completed delivery count needed to reach the next courier rank.",
                "Streak milestone needed to hit the next streak bonus tier."
        };
        int[] accents = {0xFF6EB9FF, 0xFF8DC7FF, 0xFF7CD6B0, 0xFFC4B57A,
                0xFF6AD7AC, 0xFFE59A8B, 0xFF79D3B3, 0xFF8AC9FF,
                0xFF9FB9FF, 0xFFC9B56F, 0xFF8ED1FF, 0xFFC3A8FF, 0xFF8AE2CE};
        for (int i = 0; i < labels.length; i++) {
            int col = i % summaryCols;
            int row = i / summaryCols;
            int cardX = viewX + (col * (summaryCardW + summaryGap));
            int cardY = cursorY + (row * (summaryCardH + summaryGap));
            drawMetricCard(graphics, cardX, cardY, summaryCardW, summaryCardH, labels[i], values[i], accents[i]);
            visibleOrderBoardKpiCards.add(new KpiCardHitbox(cardX, cardY, summaryCardW, summaryCardH, labels[i], values[i], descriptions[i]));
        }
        int summaryRows = (labels.length + summaryCols - 1) / summaryCols;
        cursorY += (summaryRows * (summaryCardH + summaryGap)) + 8;

        if (cards.isEmpty()) {
            graphics.drawString(this.font, "No open delivery orders right now.", viewX + 8, cursorY + 6, 0xFFD1E8FF, false);
        } else {
            int cardH = 66;
            int cardGap = 6;
            int cardX = viewX + 4;
            int cardW = Math.max(180, viewW - 8);
            for (OrderBoardCardData card : cards) {
                int cardY = cursorY;
                if (cardY > (viewY + viewH) || (cardY + cardH) < viewY) {
                    cursorY += cardH + cardGap;
                    continue;
                }
                boolean selected = card.orderId() != null && card.orderId().equalsIgnoreCase(orderBoardSelectedOrderId);
                int accent = "ACCEPTED".equalsIgnoreCase(card.status()) ? 0xFF78C8A8 : 0xFF69B8FF;
                graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, selected ? 0xFF7ED7A7 : 0xFF315274);
                graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, selected ? 0x8C1F3E57 : 0x7A1B334E);
                graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, accent);

                String shopName = card.shopName() == null || card.shopName().isBlank() ? "-" : card.shopName();
                String itemName = card.itemName() == null || card.itemName().isBlank() ? card.itemId() : card.itemName();
                graphics.drawString(this.font,
                        fitToWidth(shopName + " • " + itemName + " x" + Math.max(1, card.quantity()), cardW - 12),
                        cardX + 6,
                        cardY + 6,
                        0xFFFFFFFF,
                        false);
                graphics.drawString(this.font,
                        fitToWidth("Reward " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, card.rewardCents()), 2))
                                + " • Status " + card.status(), cardW - 12),
                        cardX + 6,
                        cardY + 18,
                        0xFFCFE4F8,
                        false);
                String acceptedBy = card.acceptedByName() == null || card.acceptedByName().isBlank() ? "-" : card.acceptedByName();
                String timer = "OPEN".equalsIgnoreCase(card.status())
                        ? ("Timeout " + Math.max(5, card.timeoutMinutes()) + "m")
                        : ("Time left " + Math.max(0L, card.remainingSeconds()) + "s");
                graphics.drawString(this.font,
                        fitToWidth("Courier " + acceptedBy + " • " + timer, cardW - 12),
                        cardX + 6,
                        cardY + 30,
                        0xFFBCD5EC,
                        false);
                graphics.drawString(this.font,
                        fitToWidth("Drop " + formatPalletRefLabel(card.boundPalletRef())
                                + " • Created " + formatRelativeTime(card.createdAtMillis()), cardW - 12),
                        cardX + 6,
                        cardY + 42,
                        0xFFAAC4DE,
                        false);
                graphics.drawString(this.font,
                        fitToWidth("Shop ID " + shortUuid(card.shopId()), cardW - 12),
                        cardX + 6,
                        cardY + 54,
                        0xFF9AB7D1,
                        false);

                visibleOrderBoardCards.add(new OrderBoardCardHitbox(cardX, cardY, cardW, cardH, card));
                cursorY += cardH + cardGap;
            }
        }

        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        if (orderBoardMaxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    ScrollbarTarget.ORDER_BOARD,
                    true,
                    viewX + viewW - 4,
                    viewY + 1,
                    3,
                    Math.max(10, viewH - 2),
                    orderBoardScroll,
                    orderBoardMaxScroll
            );
        }
    }

    private String prettyWebshopMode(String rawMode) {
        String mode = rawMode == null ? "" : rawMode.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "PALLET_RANDOM" -> "Random Pallet";
            case "PALLET_SPECIFIC" -> "Specific Pallet";
            default -> "Random Pallet";
        };
    }

    private String nextWebshopMode(String rawMode) {
        String mode = rawMode == null ? "" : rawMode.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case "PALLET_RANDOM" -> "PALLET_SPECIFIC";
            case "PALLET_SPECIFIC" -> "PALLET_RANDOM";
            default -> "PALLET_RANDOM";
        };
    }

    private WebshopSummary parseWebshopSummary(List<String> lines) {
        // Token map keys are produced by ShopService.webshopReport.
        Map<String, String> values = parseAtTokenMap(lines);
        if (!values.containsKey("webshop.summary.catalog_count")
                && !values.containsKey("webshop.summary.cart_lines")
                && !values.containsKey("webshop.summary.total_cents")) {
            return null;
        }
        String dim = "minecraft:overworld";
        int x = 0;
        int y = 0;
        int z = 0;
        boolean expedite = "1".equals(values.get("webshop.selected.expedite"))
                || "true".equalsIgnoreCase(values.getOrDefault("webshop.selected.expedite", ""));
        return new WebshopSummary(
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.catalog_count"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.cart_lines"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.cart_units"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.subtotal_cents"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.surcharge_cents"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.total_cents"))),
                Math.max(0L, parseLongMetricToken(values.get("webshop.summary.queued_orders"))),
                Math.max(1L, parseLongMetricToken(values.get("webshop.summary.max_active_orders"))),
                values.getOrDefault("webshop.selected.account_id", ""),
                values.getOrDefault("webshop.selected.mode", "PALLET_RANDOM"),
                values.getOrDefault("webshop.selected.shop_id", ""),
                values.getOrDefault("webshop.selected.pallet_id", ""),
                expedite,
                dim,
                x,
                y,
                z
        );
    }

    private List<WebshopCatalogCardData> parseWebshopCatalogCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopCatalogCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_catalog=")) {
                continue;
            }
            String payload = line.substring("@webshop_catalog=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String itemId = parts[1].trim();
            if (itemId.isBlank()) {
                continue;
            }
            String itemName = parts[2].trim().isBlank() ? itemId : parts[2].trim();
            out.add(new WebshopCatalogCardData(
                    index,
                    itemId,
                    itemName,
                    parts[3].trim(),
                    Math.max(0L, parseLongMetricToken(parts[4])),
                    parts[5].trim()
            ));
        }
        out.sort(Comparator.comparingInt(WebshopCatalogCardData::index));
        return out;
    }

    private List<WebshopCartCardData> parseWebshopCartCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopCartCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_cart=")) {
                continue;
            }
            String payload = line.substring("@webshop_cart=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String itemId = parts[1].trim();
            if (itemId.isBlank()) {
                continue;
            }
            out.add(new WebshopCartCardData(
                    index,
                    itemId,
                    parts[2].trim().isBlank() ? itemId : parts[2].trim(),
                    Math.max(1, parseIntMetricToken(parts[3])),
                    Math.max(0L, parseLongMetricToken(parts[4])),
                    Math.max(0L, parseLongMetricToken(parts[5]))
            ));
        }
        out.sort(Comparator.comparingInt(WebshopCartCardData::index));
        return out;
    }

    private List<WebshopAccountCardData> parseWebshopAccountCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopAccountCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_account=")) {
                continue;
            }
            String payload = line.substring("@webshop_account=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String accountId = parts[1].trim();
            if (accountId.isBlank()) {
                continue;
            }
            out.add(new WebshopAccountCardData(
                    index,
                    accountId,
                    parts[2].trim(),
                    parts[3].trim(),
                    Math.max(0L, parseLongMetricToken(parts[4])),
                    "1".equals(parts[5].trim()) || "true".equalsIgnoreCase(parts[5].trim())
            ));
        }
        out.sort(Comparator.comparingInt(WebshopAccountCardData::index));
        return out;
    }

    private List<WebshopShopCardData> parseWebshopShopCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopShopCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_shop=")) {
                continue;
            }
            String payload = line.substring("@webshop_shop=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String shopId = parts[1].trim();
            if (shopId.isBlank()) {
                continue;
            }
            out.add(new WebshopShopCardData(
                    index,
                    shopId,
                    parts[2].trim(),
                    parts[3].trim(),
                    Math.max(0, parseIntMetricToken(parts[4]))
            ));
        }
        out.sort(Comparator.comparingInt(WebshopShopCardData::index));
        return out;
    }

    private List<WebshopPalletCardData> parseWebshopPalletCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopPalletCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_pallet=")) {
                continue;
            }
            String payload = line.substring("@webshop_pallet=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 9) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String palletId = parts[1].trim();
            if (palletId.isBlank()) {
                continue;
            }
            out.add(new WebshopPalletCardData(
                    index,
                    palletId,
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parseIntMetricToken(parts[5]),
                    parseIntMetricToken(parts[6]),
                    parseIntMetricToken(parts[7]),
                    "1".equals(parts[8].trim()) || "true".equalsIgnoreCase(parts[8].trim())
            ));
        }
        out.sort(Comparator.comparingInt(WebshopPalletCardData::index));
        return out;
    }

    private List<WebshopOrderCardData> parseWebshopOrderCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<WebshopOrderCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@webshop_order=")) {
                continue;
            }
            String payload = line.substring("@webshop_order=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 10) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String orderId = parts[1].trim();
            if (orderId.isBlank()) {
                continue;
            }
            out.add(new WebshopOrderCardData(
                    index,
                    orderId,
                    parts[2].trim(),
                    Math.max(0L, parseLongMetricToken(parts[3])),
                    Math.max(0L, parseLongMetricToken(parts[4])),
                    Math.max(0L, parseLongMetricToken(parts[5])),
                    parts[6].trim(),
                    parts[7].trim(),
                    Math.max(0, parseIntMetricToken(parts[8])),
                    Math.max(0, parseIntMetricToken(parts[9]))
            ));
        }
        out.sort(Comparator.comparingInt(WebshopOrderCardData::index));
        return out;
    }

    private int getWebshopContentHeight(int width, int viewportHeight, List<String> lines) {
        // Content height is computed from section card counts for smooth pixel scrolling.
        WebshopSummary summary = parseWebshopSummary(lines);
        List<WebshopCatalogCardData> catalog = parseWebshopCatalogCards(lines);
        List<WebshopCartCardData> cart = parseWebshopCartCards(lines);
        List<WebshopAccountCardData> accounts = parseWebshopAccountCards(lines);
        List<WebshopShopCardData> shops = parseWebshopShopCards(lines);
        List<WebshopPalletCardData> pallets = parseWebshopPalletCards(lines);
        List<WebshopOrderCardData> orders = parseWebshopOrderCards(lines);

        int y = 6;
        int metricCards = summary == null ? 0 : 9;
        if (metricCards > 0) {
            int metricCols = width >= 760 ? 5 : width >= 560 ? 3 : width >= 380 ? 2 : 1;
            int metricRows = (metricCards + metricCols - 1) / metricCols;
            y += (metricRows * 42) + (Math.max(0, metricRows - 1) * 6) + 10;
        }
        int sectionGap = 8;
        int catalogCols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
        int catalogRows = Math.max(1, (catalog.size() + catalogCols - 1) / catalogCols);
        y += 14 + (catalogRows * 58) + (Math.max(0, catalogRows - 1) * 6) + sectionGap;
        int cartRows = Math.max(1, cart.size());
        y += 14 + (cartRows * 52) + (Math.max(0, cartRows - 1) * 6) + sectionGap;
        int accountCols = width >= 760 ? 3 : width >= 520 ? 2 : 1;
        int accountRows = Math.max(1, (accounts.size() + accountCols - 1) / accountCols);
        y += 14 + (accountRows * 48) + (Math.max(0, accountRows - 1) * 6) + sectionGap;
        int shopRows = Math.max(1, shops.size());
        y += 14 + (shopRows * 44) + (Math.max(0, shopRows - 1) * 6) + sectionGap;
        int palletRows = Math.max(1, pallets.size());
        y += 14 + (palletRows * 44) + (Math.max(0, palletRows - 1) * 6) + sectionGap;
        int orderRows = Math.max(1, orders.size());
        y += 14 + (orderRows * 56) + (Math.max(0, orderRows - 1) * 6) + sectionGap;
        return Math.max(viewportHeight, y + 6);
    }

    private void drawWebshopApp(GuiGraphics graphics, int x, int y, int width, int height) {
        // Draw webshop as a multi-section dashboard with selectable cards for each workflow step.
        visibleWebshopCatalogCards.clear();
        visibleWebshopCartCards.clear();
        visibleWebshopAccountCards.clear();
        visibleWebshopShopCards.clear();
        visibleWebshopPalletCards.clear();
        visibleWebshopOrderCards.clear();
        visibleWebshopKpiCards.clear();

        List<String> raw = ClientOwnerPcData.getActionOutputLines();
        WebshopSummary summary = parseWebshopSummary(raw);
        List<WebshopCatalogCardData> catalog = parseWebshopCatalogCards(raw);
        List<WebshopCartCardData> cart = parseWebshopCartCards(raw);
        List<WebshopAccountCardData> accounts = parseWebshopAccountCards(raw);
        List<WebshopShopCardData> shops = parseWebshopShopCards(raw);
        List<WebshopPalletCardData> pallets = parseWebshopPalletCards(raw);
        List<WebshopOrderCardData> orders = parseWebshopOrderCards(raw);

        int viewX = webshopViewportX > 0 ? webshopViewportX : (x + 4);
        int viewY = webshopViewportY > 0 ? webshopViewportY : (y + 4);
        int viewW = webshopViewportW > 0 ? webshopViewportW : Math.max(140, width - 8);
        int viewH = webshopViewportH > 0 ? webshopViewportH : Math.max(80, height - 8);

        graphics.fill(viewX - 1, viewY - 1, viewX + viewW + 1, viewY + viewH + 1, 0xFF2D4B6D);
        graphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, 0xC0182F47);
        graphics.fill(viewX, viewY, viewX + viewW, viewY + 2, 0xFF72BDF5);

        int contentHeight = getWebshopContentHeight(viewW, viewH, raw);
        webshopMaxScroll = Math.max(0, contentHeight - viewH);
        webshopScroll = Math.max(0, Math.min(webshopScroll, webshopMaxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, viewX + 1, viewY + 1, viewX + viewW - 1, viewY + viewH - 1);
        }

        int cursorY = viewY + 6 - webshopScroll;
        int gap = 6;

        if (summary != null) {
            int metricCols = viewW >= 760 ? 5 : viewW >= 560 ? 3 : viewW >= 380 ? 2 : 1;
            int metricW = Math.max(108, (viewW - (gap * (metricCols - 1))) / metricCols);
            int metricH = 42;
            String[] labels = {
                    "Catalog", "Cart Lines", "Cart Units",
                    "Subtotal", "Surcharge", "Total",
                    "Queued", "Max Active", "Mode"
            };
            String[] values = {
                    String.valueOf(summary.catalogCount()),
                    String.valueOf(summary.cartLines()),
                    String.valueOf(summary.cartUnits()),
                    MoneyText.abbreviateWithDollar(BigDecimal.valueOf(summary.subtotalCents(), 2)),
                    MoneyText.abbreviateWithDollar(BigDecimal.valueOf(summary.surchargeCents(), 2)),
                    MoneyText.abbreviateWithDollar(BigDecimal.valueOf(summary.totalCents(), 2)),
                    String.valueOf(summary.queuedOrders()),
                    String.valueOf(summary.maxActiveOrders()),
                    prettyWebshopMode(summary.deliveryMode())
            };
            String[] descriptions = {
                    "Total catalog products available in the retail webshop.",
                    "How many distinct products are currently in your shopping cart.",
                    "Total item units currently in your cart across all lines.",
                    "Cart subtotal before delivery surcharges.",
                    "Expedite surcharge currently applied to this cart.",
                    "Final checkout total that will be reserved from your account.",
                    "Queued webshop orders currently waiting for delivery.",
                    "Maximum queued orders you can have at once.",
                    "Current delivery routing mode for new checkout orders."
            };
            int[] accents = {0xFF6EB9FF, 0xFF9AD1FF, 0xFF79D3B3, 0xFF8BE3CF, 0xFFC8B376, 0xFF6AD7AC, 0xFF9FC3FF, 0xFFC3A8FF, 0xFF89D7C4};
            for (int i = 0; i < labels.length; i++) {
                int col = i % metricCols;
                int row = i / metricCols;
                int cardX = viewX + (col * (metricW + gap));
                int cardY = cursorY + (row * (metricH + gap));
                drawMetricCard(graphics, cardX, cardY, metricW, metricH, labels[i], values[i], accents[i]);
                visibleWebshopKpiCards.add(new KpiCardHitbox(cardX, cardY, metricW, metricH, labels[i], values[i], descriptions[i]));
            }
            int rows = (labels.length + metricCols - 1) / metricCols;
            cursorY += (rows * (metricH + gap)) + 8;
        }

        cursorY = drawWebshopSectionHeader(graphics, viewX + 4, cursorY, "Catalog");
        int catalogCols = viewW >= 760 ? 3 : viewW >= 520 ? 2 : 1;
        int catalogCardW = Math.max(140, (viewW - 8 - (gap * (catalogCols - 1))) / catalogCols);
        int catalogCardH = 58;
        if (catalog.isEmpty()) {
            graphics.drawString(this.font, "No catalog items available.", viewX + 8, cursorY + 4, 0xFFD1E8FF, false);
            cursorY += 28;
        } else {
            for (int i = 0; i < catalog.size(); i++) {
                WebshopCatalogCardData card = catalog.get(i);
                int col = i % catalogCols;
                int row = i / catalogCols;
                int cardX = viewX + 4 + (col * (catalogCardW + gap));
                int cardY = cursorY + (row * (catalogCardH + gap));
                boolean selected = card.itemId() != null && card.itemId().equalsIgnoreCase(webshopSelectedCatalogItemId);
                drawWebshopCardFrame(graphics, cardX, cardY, catalogCardW, catalogCardH, selected, 0xFF6EB9FF);
                ItemStack icon = resolveShopInventoryIcon(card.itemId());
                graphics.renderItem(icon, cardX + 6, cardY + 6);
                graphics.drawString(this.font, fitToWidth(card.itemName(), catalogCardW - 32), cardX + 26, cardY + 6, 0xFFFFFFFF, false);
                graphics.drawString(this.font, fitToWidth(card.category(), catalogCardW - 32), cardX + 26, cardY + 18, 0xFFBED8F3, false);
                graphics.drawString(this.font, fitToWidth("Unit " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.unitPriceCents(), 2)), catalogCardW - 32), cardX + 26, cardY + 30, 0xFFAED1EE, false);
                graphics.drawString(this.font, fitToWidth(card.description(), catalogCardW - 32), cardX + 26, cardY + 42, 0xFF98B8D4, false);
                visibleWebshopCatalogCards.add(new WebshopCatalogCardHitbox(cardX, cardY, catalogCardW, catalogCardH, card));
            }
            int rows = (catalog.size() + catalogCols - 1) / catalogCols;
            cursorY += (rows * (catalogCardH + gap));
        }
        cursorY += 4;

        cursorY = drawWebshopSectionHeader(graphics, viewX + 4, cursorY, "Cart");
        int cartCardW = Math.max(180, viewW - 8);
        int cartCardH = 52;
        if (cart.isEmpty()) {
            graphics.drawString(this.font, "Cart is empty. Select a catalog item and press Add Selected.", viewX + 8, cursorY + 4, 0xFFD1E8FF, false);
            cursorY += 28;
        } else {
            for (WebshopCartCardData card : cart) {
                int cardX = viewX + 4;
                int cardY = cursorY;
                boolean selected = card.itemId() != null && card.itemId().equalsIgnoreCase(webshopSelectedCartItemId);
                drawWebshopCardFrame(graphics, cardX, cardY, cartCardW, cartCardH, selected, 0xFF8AC9FF);
                ItemStack icon = resolveShopInventoryIcon(card.itemId());
                graphics.renderItem(icon, cardX + 6, cardY + 6);
                graphics.drawString(this.font, fitToWidth(card.itemName() + " x" + card.quantity(), cartCardW - 34), cardX + 26, cardY + 6, 0xFFFFFFFF, false);
                graphics.drawString(this.font, fitToWidth("Unit " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.unitPriceCents(), 2))
                        + " • Line " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.lineTotalCents(), 2)), cartCardW - 34), cardX + 26, cardY + 18, 0xFFC6DDF3, false);
                graphics.drawString(this.font, fitToWidth("Item ID " + card.itemId(), cartCardW - 34), cardX + 26, cardY + 30, 0xFFAED1EE, false);
                graphics.drawString(this.font, "Click to select for Set Qty / Remove.", cardX + 26, cardY + 42, 0xFF98B8D4, false);
                visibleWebshopCartCards.add(new WebshopCartCardHitbox(cardX, cardY, cartCardW, cartCardH, card));
                cursorY += cartCardH + gap;
            }
        }
        cursorY += 2;

        cursorY = drawWebshopSectionHeader(graphics, viewX + 4, cursorY, "Accounts");
        int accountCols = viewW >= 760 ? 3 : viewW >= 520 ? 2 : 1;
        int accountCardW = Math.max(130, (viewW - 8 - (gap * (accountCols - 1))) / accountCols);
        int accountCardH = 48;
        if (accounts.isEmpty()) {
            graphics.drawString(this.font, "No eligible accounts found.", viewX + 8, cursorY + 4, 0xFFD1E8FF, false);
            cursorY += 28;
        } else {
            for (int i = 0; i < accounts.size(); i++) {
                WebshopAccountCardData card = accounts.get(i);
                int col = i % accountCols;
                int row = i / accountCols;
                int cardX = viewX + 4 + (col * (accountCardW + gap));
                int cardY = cursorY + (row * (accountCardH + gap));
                boolean selected = card.accountId() != null && card.accountId().equalsIgnoreCase(summary == null ? webshopSelectedAccountId : summary.selectedAccountId());
                drawWebshopCardFrame(graphics, cardX, cardY, accountCardW, accountCardH, selected, 0xFF7DD7B1);
                graphics.drawString(this.font, fitToWidth(card.accountType() + " • " + card.bankName(), accountCardW - 12), cardX + 6, cardY + 6, 0xFFFFFFFF, false);
                graphics.drawString(this.font, fitToWidth("Bal " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.balanceCents(), 2))
                        + (card.primary() ? " • Primary" : ""), accountCardW - 12), cardX + 6, cardY + 18, 0xFFC6DDF3, false);
                graphics.drawString(this.font, fitToWidth("ID " + shortUuid(card.accountId()), accountCardW - 12), cardX + 6, cardY + 30, 0xFFAED1EE, false);
                visibleWebshopAccountCards.add(new WebshopAccountCardHitbox(cardX, cardY, accountCardW, accountCardH, card));
            }
            int rows = (accounts.size() + accountCols - 1) / accountCols;
            cursorY += (rows * (accountCardH + gap));
        }
        cursorY += 2;

        cursorY = drawWebshopSectionHeader(graphics, viewX + 4, cursorY, "Delivery");
        int deliveryCardW = Math.max(180, viewW - 8);
        int deliveryCardH = 44;
        if (summary != null) {
            graphics.drawString(this.font,
                    fitToWidth("Current mode: " + prettyWebshopMode(summary.deliveryMode()), deliveryCardW - 8),
                    viewX + 6,
                    cursorY + 2,
                    0xFFE2F2FF,
                    false);
            cursorY += 14;
        }
        for (WebshopShopCardData card : shops) {
            int cardX = viewX + 4;
            int cardY = cursorY;
            boolean selected = card.shopId() != null && card.shopId().equalsIgnoreCase(summary == null ? webshopSelectedShopId : summary.selectedShopId());
            drawWebshopCardFrame(graphics, cardX, cardY, deliveryCardW, deliveryCardH, selected, 0xFF86C5FF);
            graphics.drawString(this.font, fitToWidth(card.shopName() + " • " + card.shopType(), deliveryCardW - 12), cardX + 6, cardY + 6, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth("Assigned delivery pallets: " + card.assignedPallets(), deliveryCardW - 12), cardX + 6, cardY + 18, 0xFFC6DDF3, false);
            graphics.drawString(this.font, fitToWidth("Shop ID " + shortUuid(card.shopId()), deliveryCardW - 12), cardX + 6, cardY + 30, 0xFFAED1EE, false);
            visibleWebshopShopCards.add(new WebshopShopCardHitbox(cardX, cardY, deliveryCardW, deliveryCardH, card));
            cursorY += deliveryCardH + gap;
        }
        for (WebshopPalletCardData card : pallets) {
            int cardX = viewX + 4;
            int cardY = cursorY;
            boolean selected = card.palletId() != null && card.palletId().equalsIgnoreCase(summary == null ? webshopSelectedPalletId : summary.selectedPalletId());
            drawWebshopCardFrame(graphics, cardX, cardY, deliveryCardW, deliveryCardH, selected, card.full() ? 0xFFE58F8F : 0xFF70D3B5);
            graphics.drawString(this.font, fitToWidth("Pallet " + shortUuid(card.palletId()) + " • " + card.shopName(), deliveryCardW - 12), cardX + 6, cardY + 6, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(card.dimension() + " (" + card.x() + "," + card.y() + "," + card.z() + ")", deliveryCardW - 12), cardX + 6, cardY + 18, 0xFFC6DDF3, false);
            graphics.drawString(this.font, card.full() ? "Status: full" : "Status: available", cardX + 6, cardY + 30, card.full() ? 0xFFFFB7B7 : 0xFFB8F2E0, false);
            visibleWebshopPalletCards.add(new WebshopPalletCardHitbox(cardX, cardY, deliveryCardW, deliveryCardH, card));
            cursorY += deliveryCardH + gap;
        }
        if (shops.isEmpty() && pallets.isEmpty()) {
            graphics.drawString(this.font, "Select a shop and assign delivery pallets for webshop checkout.", viewX + 8, cursorY + 2, 0xFFD1E8FF, false);
            cursorY += 20;
        }

        cursorY = drawWebshopSectionHeader(graphics, viewX + 4, cursorY + 2, "Orders");
        int orderCardW = Math.max(180, viewW - 8);
        int orderCardH = 56;
        if (orders.isEmpty()) {
            graphics.drawString(this.font, "No webshop orders yet.", viewX + 8, cursorY + 4, 0xFFD1E8FF, false);
            cursorY += 24;
        } else {
            for (WebshopOrderCardData card : orders) {
                int cardX = viewX + 4;
                int cardY = cursorY;
                boolean selected = card.orderId() != null && card.orderId().equalsIgnoreCase(webshopSelectedOrderId);
                int accent = "DELIVERED".equalsIgnoreCase(card.status()) ? 0xFF73D4AD
                        : ("FAILED".equalsIgnoreCase(card.status()) ? 0xFFE28F8F : 0xFF74BAFF);
                drawWebshopCardFrame(graphics, cardX, cardY, orderCardW, orderCardH, selected, accent);
                graphics.drawString(this.font, fitToWidth("Order " + shortUuid(card.orderId()) + " • " + card.status(), orderCardW - 12), cardX + 6, cardY + 6, 0xFFFFFFFF, false);
                graphics.drawString(this.font, fitToWidth("Total " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.totalCents(), 2))
                        + " • Boxes " + card.boxCount(), orderCardW - 12), cardX + 6, cardY + 18, 0xFFC6DDF3, false);
                graphics.drawString(this.font, fitToWidth("Mode " + prettyWebshopMode(card.deliveryMode()) + " • " + card.target(), orderCardW - 12), cardX + 6, cardY + 30, 0xFFAED1EE, false);
                graphics.drawString(this.font, fitToWidth("Created " + formatRelativeTime(card.createdAtMillis())
                        + " • ETA " + formatRelativeTime(card.etaAtMillis()), orderCardW - 12), cardX + 6, cardY + 42, 0xFF99B9D4, false);
                visibleWebshopOrderCards.add(new WebshopOrderCardHitbox(cardX, cardY, orderCardW, orderCardH, card));
                cursorY += orderCardH + gap;
            }
        }

        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        if (webshopMaxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    ScrollbarTarget.WEBSHOP,
                    true,
                    viewX + viewW - 4,
                    viewY + 1,
                    3,
                    Math.max(10, viewH - 2),
                    webshopScroll,
                    webshopMaxScroll
            );
        }
    }

    private int drawWebshopSectionHeader(GuiGraphics graphics, int x, int y, String label) {
        graphics.fill(x, y, x + 140, y + 12, 0xA2284E72);
        graphics.drawString(this.font, fitToWidth(label == null ? "-" : label, 132), x + 4, y + 2, 0xFFE6F4FF, false);
        return y + 14;
    }

    private void drawWebshopCardFrame(GuiGraphics graphics, int x, int y, int w, int h, boolean selected, int accent) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, selected ? 0xFF76D2A3 : 0xFF315274);
        graphics.fill(x, y, x + w, y + h, selected ? 0x901F3E57 : 0x7A1B334E);
        graphics.fill(x, y, x + w, y + 2, accent);
    }

    private boolean isInsideWebshopViewport(double mouseX, double mouseY) {
        return mouseX >= webshopViewportX
                && mouseX <= (webshopViewportX + webshopViewportW)
                && mouseY >= webshopViewportY
                && mouseY <= (webshopViewportY + webshopViewportH);
    }

    private boolean isWebshopHitboxInViewport(int x, int y, int width, int height) {
        int left = x;
        int right = x + width;
        int top = y;
        int bottom = y + height;
        int viewportRight = webshopViewportX + webshopViewportW;
        int viewportBottom = webshopViewportY + webshopViewportH;
        return right >= webshopViewportX
                && left <= viewportRight
                && bottom >= webshopViewportY
                && top <= viewportBottom;
    }

    private void drawWebshopCardHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // Contextual card tooltips explain next interaction step for the hovered card type.
        if (activeWindow != WindowMode.UTILITY_APP || activeUtilityApp != UtilityApp.WEBSHOP) {
            return;
        }
        if (!isInsideWebshopViewport(mouseX, mouseY)) {
            return;
        }
        List<String> lines = new ArrayList<>();
        String title = null;

        for (WebshopCatalogCardHitbox hitbox : visibleWebshopCatalogCards) {
            if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                    || !hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            WebshopCatalogCardData card = hitbox.card();
            title = card.itemName();
            lines.add("Catalog item: " + card.itemId());
            lines.add("Category: " + card.category());
            lines.add("Unit price: " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.unitPriceCents(), 2)));
            lines.add("Click to select, then use Add Selected.");
            break;
        }
        if (title == null) {
            for (WebshopCartCardHitbox hitbox : visibleWebshopCartCards) {
                if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                        || !hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                WebshopCartCardData card = hitbox.card();
                title = "Cart • " + card.itemName();
                lines.add("Quantity: " + card.quantity());
                lines.add("Line total: " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.lineTotalCents(), 2)));
                lines.add("Click to select for Set Cart Qty or Remove Item.");
                break;
            }
        }
        if (title == null) {
            for (WebshopAccountCardHitbox hitbox : visibleWebshopAccountCards) {
                if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                        || !hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                WebshopAccountCardData card = hitbox.card();
                title = "Account • " + card.accountType();
                lines.add("Bank: " + card.bankName());
                lines.add("Balance: " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.balanceCents(), 2)));
                lines.add("Click to select as checkout account.");
                break;
            }
        }
        if (title == null) {
            for (WebshopShopCardHitbox hitbox : visibleWebshopShopCards) {
                if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                        || !hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                WebshopShopCardData card = hitbox.card();
                title = "Shop • " + card.shopName();
                lines.add("Type: " + card.shopType());
                lines.add("Assigned delivery pallets: " + card.assignedPallets());
                lines.add("Click to target this shop for pallet delivery.");
                break;
            }
        }
        if (title == null) {
            for (WebshopPalletCardHitbox hitbox : visibleWebshopPalletCards) {
                if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                        || !hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                WebshopPalletCardData card = hitbox.card();
                title = "Pallet • " + shortUuid(card.palletId());
                lines.add(card.dimension() + " (" + card.x() + "," + card.y() + "," + card.z() + ")");
                lines.add(card.full() ? "This pallet is currently full." : "This pallet has free delivery space.");
                lines.add("Click to set as the specific delivery pallet.");
                break;
            }
        }
        if (title == null) {
            for (WebshopOrderCardHitbox hitbox : visibleWebshopOrderCards) {
                if (!isWebshopHitboxInViewport(hitbox.x(), hitbox.y(), hitbox.width(), hitbox.height())
                        || !hitbox.contains(mouseX, mouseY)) {
                    continue;
                }
                WebshopOrderCardData card = hitbox.card();
                title = "Order • " + shortUuid(card.orderId());
                lines.add("Status: " + card.status());
                lines.add("Attempts: " + card.attempts() + " • Boxes: " + card.boxCount());
                lines.add("Click to select for cancel.");
                break;
            }
        }
        if (title == null) {
            return;
        }

        int tooltipW = Math.min(360, Math.max(220, this.width / 3));
        List<String> wrapped = wrapLines(lines, Math.max(90, tooltipW - 14));
        int tooltipH = 10 + 9 + Math.max(11, wrapped.size() * LINE_HEIGHT) + 8;
        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;
        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, fitToWidth(title, tooltipW - 14), x + 7, y + 6, 0xFFFFFFFF, false);
        int lineY = y + 23;
        for (String line : wrapped) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawWebshopKpiHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.UTILITY_APP
                || activeUtilityApp != UtilityApp.WEBSHOP
                || visibleWebshopKpiCards.isEmpty()) {
            return;
        }
        if (!isInsideWebshopViewport(mouseX, mouseY)) {
            return;
        }
        KpiCardHitbox hovered = null;
        for (KpiCardHitbox card : visibleWebshopKpiCards) {
            if (!isWebshopHitboxInViewport(card.x(), card.y(), card.width(), card.height())
                    || !card.contains(mouseX, mouseY)) {
                continue;
            }
            hovered = card;
            break;
        }
        if (hovered == null) {
            return;
        }
        int tooltipW = Math.min(340, Math.max(210, this.width / 3));
        String title = fitToWidth(hovered.label(), tooltipW - 14);
        String valueLine = fitToWidth("Value: " + hovered.value(), tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(hovered.description()), Math.max(90, tooltipW - 14));
        int tooltipH = 10 + 9 + 11 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;
        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;
        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(this.font, valueLine, x + 7, y + 23, 0xFFCFE8FF, false);
        int lineY = y + 35;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawWebshopControlHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.UTILITY_APP
                || activeUtilityApp != UtilityApp.WEBSHOP
                || visibleWebshopControlHelp.isEmpty()) {
            return;
        }
        WebshopControlHelpHitbox hovered = null;
        for (WebshopControlHelpHitbox hitbox : visibleWebshopControlHelp) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            hovered = hitbox;
            break;
        }
        if (hovered == null) {
            return;
        }

        int tooltipW = Math.min(340, Math.max(210, this.width / 3));
        String title = fitToWidth(hovered.title(), tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(hovered.description()), Math.max(90, tooltipW - 14));
        int tooltipH = 10 + 9 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;
        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;
        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        // Keep webshop control helper tooltip above all widgets and cards.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        int lineY = y + 23;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private ItemStack resolveShopInventoryIcon(String rawItemId) {
        if (rawItemId == null || rawItemId.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation key = ResourceLocation.parse(rawItemId.trim());
            if (!BuiltInRegistries.ITEM.containsKey(key)) {
                return new ItemStack(Items.BARRIER);
            }
            return new ItemStack(BuiltInRegistries.ITEM.get(key));
        } catch (Exception ignored) {
            return new ItemStack(Items.BARRIER);
        }
    }

    private String abbreviateStockCount(int count) {
        long safeCount = Math.max(0L, count);
        return MoneyText.abbreviate(BigDecimal.valueOf(safeCount));
    }

    private String formatPalletRefLabel(String rawRef) {
        if (rawRef == null || rawRef.isBlank() || "-".equals(rawRef.trim())) {
            return "Any labeled pallet";
        }
        String[] parts = rawRef.trim().split(";", -1);
        if (parts.length == 4) {
            return parts[1].trim() + "," + parts[2].trim() + "," + parts[3].trim();
        }
        String id = rawRef.trim();
        return "ID " + shortUuid(id);
    }

    private String formatRelativeTime(long millis) {
        if (millis <= 0L) {
            return "never";
        }
        long delta = System.currentTimeMillis() - millis;
        if (delta < 0L) {
            delta = 0L;
        }
        long seconds = delta / 1000L;
        if (seconds < 60L) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60L;
        if (minutes < 60L) {
            return minutes + "m ago";
        }
        long hours = minutes / 60L;
        if (hours < 48L) {
            return hours + "h ago";
        }
        long days = hours / 24L;
        return days + "d ago";
    }

    private String shortUuid(String rawUuid) {
        String raw = rawUuid == null ? "" : rawUuid.trim();
        if (raw.isBlank()) {
            return "-";
        }
        return raw.length() <= 8 ? raw : raw.substring(0, 8);
    }

    private List<ShopEmployeeCardData> parseShopEmployeeCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopEmployeeCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@employee=")) {
                continue;
            }
            String payload = line.substring("@employee=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 7) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            out.add(new ShopEmployeeCardData(
                    index,
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim(),
                    parts[4].trim(),
                    parts[5].trim(),
                    parts[6].trim()
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopEmployeeCardData::index));
        return out;
    }

    private List<ShopOwnerAccountCardData> parseShopOwnerAccountCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopOwnerAccountCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@owner_account=")) {
                continue;
            }
            String payload = line.substring("@owner_account=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            String accountId = parts[1].trim();
            if (index <= 0 || accountId.isBlank()) {
                continue;
            }
            long balanceCents = parseLongMetricToken(parts[4]);
            boolean primary = "1".equals(parts[5].trim()) || "true".equalsIgnoreCase(parts[5].trim());
            out.add(new ShopOwnerAccountCardData(
                    index,
                    accountId,
                    parts[2].trim(),
                    parts[3].trim(),
                    Math.max(0L, balanceCents),
                    primary
            ));
        }
        out.sort(java.util.Comparator.comparingInt(ShopOwnerAccountCardData::index));
        return out;
    }

    private List<ShopPermissionRoleHeaderData> parseShopPermissionRoleHeaders(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopPermissionRoleHeaderData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@shop_permission_header=")) {
                continue;
            }
            String payload = line.substring("@shop_permission_header=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 5) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String roleKey = parts[1].trim().toUpperCase(Locale.ROOT);
            if (roleKey.isBlank()) {
                continue;
            }
            String roleLabel = parts[2].trim();
            String description = parts[3].trim();
            int count = Math.max(0, parseIntMetricToken(parts[4]));
            out.add(new ShopPermissionRoleHeaderData(index, roleKey, roleLabel, description, count));
        }
        out.sort(Comparator.comparingInt(ShopPermissionRoleHeaderData::index));
        return out;
    }

    private List<ShopPermissionMemberCardData> parseShopPermissionMemberCards(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ShopPermissionMemberCardData> out = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@shop_permission_member=")) {
                continue;
            }
            String payload = line.substring("@shop_permission_member=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 10) {
                continue;
            }
            int index = parseIntMetricToken(parts[0]);
            if (index <= 0) {
                index = out.size() + 1;
            }
            String roleKey = parts[1].trim().toUpperCase(Locale.ROOT);
            String playerId = parts[2].trim();
            if (roleKey.isBlank() || playerId.isBlank()) {
                continue;
            }
            String playerName = parts[3].trim();
            String assignedRole = parts[4].trim().toUpperCase(Locale.ROOT);
            boolean online = "1".equals(parts[5].trim()) || "true".equalsIgnoreCase(parts[5].trim());
            boolean owner = "1".equals(parts[6].trim()) || "true".equalsIgnoreCase(parts[6].trim());
            boolean guest = "1".equals(parts[7].trim()) || "true".equalsIgnoreCase(parts[7].trim());
            long grantedAtMillis = Math.max(0L, parseLongMetricToken(parts[8]));
            String location = parts[9].trim();
            out.add(new ShopPermissionMemberCardData(
                    index,
                    roleKey,
                    playerId,
                    playerName,
                    assignedRole,
                    online,
                    owner,
                    guest,
                    grantedAtMillis,
                    location
            ));
        }
        out.sort(Comparator.comparingInt(ShopPermissionMemberCardData::index));
        return out;
    }

    private List<ShopPermissionMemberCardData> filterShopPermissionMemberCards(List<ShopPermissionMemberCardData> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        String search = formValues.getOrDefault(SHOP_PERMISSIONS_PICK_SEARCH_KEY, "").trim().toLowerCase(Locale.ROOT);
        if (search.isBlank()) {
            return members;
        }
        List<ShopPermissionMemberCardData> out = new ArrayList<>();
        for (ShopPermissionMemberCardData member : members) {
            if (member == null) {
                continue;
            }
            String name = member.playerName() == null ? "" : member.playerName().toLowerCase(Locale.ROOT);
            String id = member.playerId() == null ? "" : member.playerId().toLowerCase(Locale.ROOT);
            String role = member.assignedRole() == null ? "" : member.assignedRole().toLowerCase(Locale.ROOT);
            String group = member.roleKey() == null ? "" : member.roleKey().toLowerCase(Locale.ROOT);
            if (name.contains(search) || id.contains(search) || role.contains(search) || group.contains(search)) {
                out.add(member);
            }
        }
        return out;
    }

    private Map<String, String> parseAtTokenMap(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return Map.of();
        }
        Map<String, String> values = new HashMap<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 1) {
                continue;
            }
            String key = line.substring(1, eq).trim().toLowerCase(Locale.ROOT);
            String value = eq + 1 < line.length() ? line.substring(eq + 1).trim() : "";
            if (!key.isBlank()) {
                values.put(key, value);
            }
        }
        return values;
    }

    private ShopFinanceSnapshot parseShopFinanceSnapshot(List<String> lines) {
        Map<String, String> values = parseAtTokenMap(lines);
        if (!values.containsKey("finance.settlement_account_id")
                && !values.containsKey("finance.checkout_account_id")
                && !values.containsKey("finance.checkout_terminal")) {
            return null;
        }
        return new ShopFinanceSnapshot(
                values.getOrDefault("finance.settlement_account_id", ""),
                values.getOrDefault("finance.checkout_account_id", ""),
                values.getOrDefault("finance.checkout_terminal", "-"),
                parseLongMetricToken(values.get("finance.cash_tx_count")),
                parseLongMetricToken(values.get("finance.cash_total_cents")),
                parseIntMetricToken(values.get("finance.cash_customers")),
                parseLongMetricToken(values.get("finance.terminal_tx_count")),
                parseLongMetricToken(values.get("finance.terminal_total_cents")),
                parseIntMetricToken(values.get("finance.terminal_customers")),
                parseLongMetricToken(values.get("finance.vault_total_cents"))
        );
    }

    private ShopVaultSnapshot parseShopVaultSnapshot(List<String> lines) {
        Map<String, String> values = parseAtTokenMap(lines);
        if (!values.containsKey("vault.total_cents") && !values.containsKey("vault.counts")) {
            return null;
        }
        long totalCents = parseLongMetricToken(values.get("vault.total_cents"));
        String rawCounts = values.getOrDefault("vault.counts", "");
        List<Long> denominations = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        if (!rawCounts.isBlank()) {
            String[] entries = rawCounts.split(",");
            for (String entry : entries) {
                String token = entry == null ? "" : entry.trim();
                if (token.isBlank()) {
                    continue;
                }
                int sep = token.indexOf(':');
                if (sep <= 0 || sep >= token.length() - 1) {
                    continue;
                }
                long denom = parseLongMetricToken(token.substring(0, sep));
                int count = parseIntMetricToken(token.substring(sep + 1));
                if (denom <= 0L) {
                    continue;
                }
                denominations.add(denom);
                counts.add(Math.max(0, count));
            }
        }
        return new ShopVaultSnapshot(totalCents, denominations, counts);
    }

    private int drawShopEmployeeCards(GuiGraphics graphics,
                                      List<ShopEmployeeCardData> employees,
                                      int x,
                                      int y,
                                      int width,
                                      int height) {
        if (employees == null || employees.isEmpty()) {
            graphics.drawString(this.font, "No employees found.", x + 6, y + 6, 0xFFE6F3FF, false);
            return height;
        }

        int gap = 8;
        int cols = width >= 620 ? 2 : 1;
        int cardW = Math.max(210, (width - (gap * (cols - 1))) / cols);
        int cardH = 92;
        int contentHeight = getShopEmployeeCardsContentHeight(width, height, employees.size());
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        int topY = y + 4 - outputScroll;
        for (int i = 0; i < employees.size(); i++) {
            ShopEmployeeCardData employee = employees.get(i);
            int row = i / cols;
            int col = i % cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = topY + (row * (cardH + gap));
            if (cardY > (y + height) || (cardY + cardH) < y) {
                continue;
            }

            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 0xFF315274);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x7A1B334E);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, 0xFF68B7FF);

            graphics.drawString(this.font, fitToWidth(employee.name(), cardW - 16), cardX + 8, cardY + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth("Employee ID: " + employee.employeeId(), cardW - 16), cardX + 8, cardY + 20, 0xFFCCE3FA, false);
            graphics.drawString(this.font, fitToWidth("Cashier Entity: " + employee.cashierEntityId(), cardW - 16), cardX + 8, cardY + 32, 0xFFBFD7EE, false);
            graphics.drawString(this.font, fitToWidth(employee.dimension() + " " + employee.position(), cardW - 16), cardX + 8, cardY + 44, 0xFFB2CAE3, false);
            graphics.drawString(this.font, fitToWidth("Terminal: " + employee.terminalLabel(), cardW - 16), cardX + 8, cardY + 56, 0xFFAAC4DE, false);
            graphics.drawString(this.font, fitToWidth("Index: #" + employee.index(), cardW - 16), cardX + 8, cardY + 68, 0xFF9FB9D4, false);

            int btnY = cardY + cardH - 18;
            int btnW = Math.max(74, (cardW - 24) / 2);
            int copyX = cardX + 8;
            int fireX = cardX + cardW - btnW - 8;
            drawInlineActionButton(graphics, copyX, btnY, btnW, 14, "Copy ID", 0xFF5E9ED0);
            drawInlineActionButton(graphics, fireX, btnY, btnW, 14, "Fire", 0xFFE28A8A);

            visibleShopEmployeeActions.add(new ShopEmployeeActionHitbox(copyX, btnY, btnW, 14, "COPY", employee));
            visibleShopEmployeeActions.add(new ShopEmployeeActionHitbox(fireX, btnY, btnW, 14, "FIRE", employee));
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private int getShopFinanceContentHeight(int width, int viewportHeight) {
        int cards = 10;
        int gap = 6;
        int cardH = 42;
        int cols = width >= 560 ? 3 : width >= 380 ? 2 : 1;
        int rows = (cards + cols - 1) / cols;
        int cardsBlock = (rows * cardH) + (Math.max(0, rows - 1) * gap);
        int notesBlock = 44;
        int estimated = 8 + 18 + cardsBlock + 8 + notesBlock + 8;
        return Math.max(viewportHeight, estimated);
    }

    private int getShopVaultContentHeight(int width, int viewportHeight, int rowCount) {
        int rows = Math.max(1, rowCount);
        int listBlock = (rows * LINE_HEIGHT) + 30;
        int estimated = 8 + 46 + 8 + listBlock + 8;
        return Math.max(viewportHeight, estimated);
    }

    private void drawShopFinanceSnapshot(GuiGraphics graphics,
                                         ShopFinanceSnapshot finance,
                                         int x,
                                         int y,
                                         int width) {
        graphics.drawString(this.font, "Finance Overview", x + 4, y + 2, 0xFFE8F4FF, false);

        String settlementId = finance.settlementAccountId() == null ? "" : finance.settlementAccountId().trim();
        String checkoutAccountId = finance.checkoutAccountId() == null ? "" : finance.checkoutAccountId().trim();
        String checkoutTerminal = finance.checkoutTerminal() == null ? "-" : finance.checkoutTerminal().trim();
        String settlementShort = settlementId.isBlank()
                ? "-"
                : (settlementId.length() <= 12 ? settlementId : settlementId.substring(0, 8) + "...");
        String checkoutAccountShort = checkoutAccountId.isBlank()
                ? "-"
                : (checkoutAccountId.length() <= 12 ? checkoutAccountId : checkoutAccountId.substring(0, 8) + "...");

        String[] labels = new String[]{
                "Settlement Account",
                "Checkout Account",
                "Checkout Terminal",
                "Cash Tx",
                "Cash Customers",
                "Cash Total",
                "Terminal Tx",
                "Terminal Customers",
                "Terminal Total",
                "Vault Tender"
        };
        String[] values = new String[]{
                settlementShort,
                checkoutAccountShort,
                fitToWidth(checkoutTerminal, 120),
                String.valueOf(finance.cashTxCount()),
                String.valueOf(finance.cashCustomers()),
                MoneyText.abbreviateWithDollar(BigDecimal.valueOf(finance.cashTotalCents(), 2)),
                String.valueOf(finance.terminalTxCount()),
                String.valueOf(finance.terminalCustomers()),
                MoneyText.abbreviateWithDollar(BigDecimal.valueOf(finance.terminalTotalCents(), 2)),
                MoneyText.abbreviateWithDollar(BigDecimal.valueOf(finance.vaultTotalCents(), 2))
        };
        int[] accents = new int[]{
                0xFF6EB9FF,
                0xFF8DC7FF,
                0xFFBDA7FF,
                0xFF7CD6B0,
                0xFF8AE2CE,
                0xFF6FD39A,
                0xFF9AC9FF,
                0xFFC4B57A,
                0xFF6FD39A,
                0xFFF0C278
        };

        int gap = 6;
        int cardH = 42;
        int cols = width >= 560 ? 3 : width >= 380 ? 2 : 1;
        int cardW = Math.max(120, (width - (gap * (cols - 1))) / cols);
        int topY = y + 18;
        for (int i = 0; i < labels.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = topY + (row * (cardH + gap));
            drawMetricCard(graphics, cardX, cardY, cardW, cardH, labels[i], values[i], accents[i]);
        }

        int rows = (labels.length + cols - 1) / cols;
        int notesY = topY + (rows * (cardH + gap)) + 6;
        int notesH = 40;
        graphics.fill(x - 1, notesY - 1, x + width + 1, notesY + notesH + 1, 0xFF355474);
        graphics.fill(x, notesY, x + width, notesY + notesH, 0x75192D45);
        graphics.drawString(this.font,
                fitToWidth("Card payments are deposited into the settlement account shown above.", Math.max(40, width - 12)),
                x + 6,
                notesY + 8,
                0xFFE6F3FF,
                false);
        graphics.drawString(this.font,
                fitToWidth("Use \"Set Settlement Account\" to route terminal funds to a specific owner account.", Math.max(40, width - 12)),
                x + 6,
                notesY + 20,
                0xFFC9DFF5,
                false);
    }

    private void drawShopVaultSnapshot(GuiGraphics graphics,
                                       ShopVaultSnapshot vault,
                                       int x,
                                       int y,
                                       int width) {
        graphics.drawString(this.font, "Cash Vault (Exact Legal Tender)", x + 4, y + 2, 0xFFE8F4FF, false);

        int tenderTypes = Math.max(0, vault.counts().size());
        long totalItems = 0L;
        for (Integer count : vault.counts()) {
            totalItems += Math.max(0, count == null ? 0 : count);
        }
        int cardW = width >= 420 ? (width - 12) / 3 : width;
        int rowY = y + 18;
        drawMetricCard(graphics, x, rowY, cardW, 42, "Total Tender",
                MoneyText.abbreviateWithDollar(BigDecimal.valueOf(vault.totalCents(), 2)),
                0xFFF0C278);
        if (width >= 420) {
            drawMetricCard(graphics, x + cardW + 6, rowY, cardW, 42, "Tender Types",
                    String.valueOf(tenderTypes), 0xFF8DC7FF);
            drawMetricCard(graphics, x + ((cardW + 6) * 2), rowY, cardW, 42, "Stored Bills/Coins",
                    String.valueOf(totalItems), 0xFF7CD6B0);
        } else {
            drawMetricCard(graphics, x, rowY + 48, width, 42, "Tender Types",
                    String.valueOf(tenderTypes), 0xFF8DC7FF);
            drawMetricCard(graphics, x, rowY + 96, width, 42, "Stored Bills/Coins",
                    String.valueOf(totalItems), 0xFF7CD6B0);
        }

        int listY = width >= 420 ? (rowY + 50) : (rowY + 144);
        graphics.fill(x - 1, listY - 1, x + width + 1, listY + 20 + (Math.max(1, vault.counts().size()) * LINE_HEIGHT), 0xFF355474);
        graphics.fill(x, listY, x + width, listY + 20 + (Math.max(1, vault.counts().size()) * LINE_HEIGHT), 0x75192D45);
        graphics.drawString(this.font, "Denomination | Count | Subtotal", x + 6, listY + 6, 0xFFE6F3FF, false);
        int lineY = listY + 18;
        for (int i = 0; i < vault.counts().size() && i < vault.denominationsCents().size(); i++) {
            long denomCents = Math.max(0L, vault.denominationsCents().get(i) == null ? 0L : vault.denominationsCents().get(i));
            int count = Math.max(0, vault.counts().get(i) == null ? 0 : vault.counts().get(i));
            BigDecimal denomDollars = BigDecimal.valueOf(denomCents, 2).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal subtotalDollars = BigDecimal.valueOf(denomCents * (long) count, 2).setScale(2, RoundingMode.HALF_EVEN);
            String row = "$" + denomDollars.toPlainString() + " x " + count + " = $" + subtotalDollars.toPlainString();
            graphics.drawString(this.font, fitToWidth(row, Math.max(40, width - 12)), x + 6, lineY, 0xFFCFE4F8, false);
            lineY += LINE_HEIGHT;
        }
        if (vault.counts().isEmpty()) {
            graphics.drawString(this.font, "No legal tender is stored in the vault yet.", x + 6, lineY, 0xFFCFE4F8, false);
        }
    }

    private int getShopOwnerAccountCardsContentHeight(int width, int viewportHeight, int count) {
        int cols = width >= 620 ? 2 : 1;
        int cardH = 78;
        int gap = 8;
        int rows = Math.max(1, (Math.max(0, count) + cols - 1) / cols);
        int block = 8 + (rows * cardH) + (Math.max(0, rows - 1) * gap) + 8;
        return Math.max(viewportHeight, block);
    }

    private int drawShopOwnerAccountCards(GuiGraphics graphics,
                                          List<ShopOwnerAccountCardData> cards,
                                          int x,
                                          int y,
                                          int width,
                                          int height) {
        if (cards == null || cards.isEmpty()) {
            graphics.drawString(this.font, "No owner accounts available.", x + 6, y + 6, 0xFFE6F3FF, false);
            return height;
        }

        int gap = 8;
        int cols = width >= 620 ? 2 : 1;
        int cardW = Math.max(220, (width - (gap * (cols - 1))) / cols);
        int cardH = 78;
        int contentHeight = getShopOwnerAccountCardsContentHeight(width, height, cards.size());
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        int topY = y + 4 - outputScroll;
        for (int i = 0; i < cards.size(); i++) {
            ShopOwnerAccountCardData card = cards.get(i);
            int row = i / cols;
            int col = i % cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = topY + (row * (cardH + gap));
            if (cardY > (y + height) || (cardY + cardH) < y) {
                continue;
            }

            boolean selected = shopSelectedSettlementAccountId != null
                    && !shopSelectedSettlementAccountId.isBlank()
                    && shopSelectedSettlementAccountId.equalsIgnoreCase(card.accountId());
            int border = selected ? 0xFF74D3A2 : 0xFF315274;
            int fill = selected ? 0x8A1F3E57 : 0x7A1B334E;
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, border);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, fill);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, selected ? 0xFF74D3A2 : 0xFF68B7FF);

            String accountLabel = card.primary() ? "PRIMARY • " + card.type() : card.type();
            graphics.drawString(this.font, fitToWidth(accountLabel, cardW - 16), cardX + 8, cardY + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, fitToWidth(card.bank(), cardW - 16), cardX + 8, cardY + 20, 0xFFCCE3FA, false);
            graphics.drawString(this.font, fitToWidth("ID: " + card.accountId(), cardW - 16), cardX + 8, cardY + 32, 0xFFBFD7EE, false);
            graphics.drawString(this.font,
                    fitToWidth("Balance: " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(card.balanceCents(), 2)), cardW - 16),
                    cardX + 8,
                    cardY + 45,
                    0xFFB2CAE3,
                    false);
            graphics.drawString(this.font, selected ? "Selected" : "Click to select", cardX + 8, cardY + 58,
                    selected ? 0xFF83E0B1 : 0xFFAAC4DE, false);

            visibleShopOwnerAccountCards.add(new ShopOwnerAccountCardHitbox(cardX, cardY, cardW, cardH, card));
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private int getShopPermissionRoleCardsContentHeight(int width,
                                                        int viewportHeight,
                                                        List<ShopPermissionRoleHeaderData> headers,
                                                        List<ShopPermissionMemberCardData> members) {
        List<ShopPermissionRoleHeaderData> safeHeaders = headers == null ? List.of() : headers;
        List<ShopPermissionMemberCardData> safeMembers = members == null ? List.of() : members;
        if (safeHeaders.isEmpty()) {
            return viewportHeight;
        }
        int cols = width >= 620 ? 2 : 1;
        int cardH = 72;
        int cardGap = 8;
        int headerH = 24;
        int headerGap = 6;
        int blockPadding = 8;
        int total = blockPadding;
        for (ShopPermissionRoleHeaderData header : safeHeaders) {
            total += headerH + headerGap;
            int memberCount = 0;
            for (ShopPermissionMemberCardData member : safeMembers) {
                if (member == null || header == null) {
                    continue;
                }
                if (header.roleKey().equalsIgnoreCase(member.roleKey())) {
                    memberCount++;
                }
            }
            if (memberCount <= 0) {
                total += 16 + cardGap;
            } else {
                int rows = Math.max(1, (memberCount + cols - 1) / cols);
                total += (rows * cardH) + (Math.max(0, rows - 1) * cardGap) + cardGap;
            }
        }
        total += blockPadding;
        return Math.max(viewportHeight, total);
    }

    private int drawShopPermissionRoleCards(GuiGraphics graphics,
                                            List<ShopPermissionRoleHeaderData> headers,
                                            List<ShopPermissionMemberCardData> members,
                                            int x,
                                            int y,
                                            int width,
                                            int height) {
        List<ShopPermissionRoleHeaderData> safeHeaders = headers == null ? List.of() : headers;
        List<ShopPermissionMemberCardData> filteredMembers = filterShopPermissionMemberCards(members == null ? List.of() : members);
        if (safeHeaders.isEmpty()) {
            String line = "No role groups available. Press Refresh Permissions.";
            graphics.drawString(this.font, line, x + 6, y + 6, 0xFFE6F3FF, false);
            return height;
        }

        int cardGap = 8;
        int cols = width >= 620 ? 2 : 1;
        int cardW = Math.max(220, (width - (cardGap * (cols - 1))) / cols);
        int cardH = 72;
        int headerH = 24;
        int headerGap = 6;
        int contentHeight = getShopPermissionRoleCardsContentHeight(width, height, safeHeaders, filteredMembers);
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        String selectedId = formValues.getOrDefault(SHOP_PERMISSIONS_SELECTED_PLAYER_ID_KEY, "").trim();
        int cursorY = y + 6 - outputScroll;
        for (ShopPermissionRoleHeaderData header : safeHeaders) {
            if (header == null) {
                continue;
            }
            String headerTitle = (header.roleLabel() == null || header.roleLabel().isBlank()
                    ? header.roleKey()
                    : header.roleLabel()) + " (" + header.count() + ")";
            graphics.fill(x + 2, cursorY, x + width - 2, cursorY + headerH, 0x8A223B59);
            graphics.fill(x + 2, cursorY, x + width - 2, cursorY + 1, 0xFF6FB8F2);
            graphics.drawString(this.font,
                    fitToWidth(headerTitle, Math.max(40, width - 12)),
                    x + 8,
                    cursorY + 6,
                    0xFFFFFFFF,
                    false);
            cursorY += headerH;
            graphics.drawString(this.font,
                    fitToWidth(header.description() == null ? "" : header.description(), Math.max(40, width - 16)),
                    x + 8,
                    cursorY + 1,
                    0xFFB7D2EA,
                    false);
            cursorY += headerGap + 10;

            List<ShopPermissionMemberCardData> groupMembers = new ArrayList<>();
            for (ShopPermissionMemberCardData member : filteredMembers) {
                if (member == null) {
                    continue;
                }
                if (header.roleKey().equalsIgnoreCase(member.roleKey())) {
                    groupMembers.add(member);
                }
            }
            if (groupMembers.isEmpty()) {
                graphics.drawString(this.font, "No players in this group.", x + 8, cursorY + 2, 0xFF9FBAD3, false);
                cursorY += 18 + cardGap;
                continue;
            }

            for (int i = 0; i < groupMembers.size(); i++) {
                ShopPermissionMemberCardData card = groupMembers.get(i);
                int row = i / cols;
                int col = i % cols;
                int cardX = x + (col * (cardW + cardGap));
                int cardY = cursorY + (row * (cardH + cardGap));
                if (cardY > (y + height) || (cardY + cardH) < y) {
                    continue;
                }
                boolean selected = !selectedId.isBlank() && selectedId.equalsIgnoreCase(card.playerId());
                int border = selected ? 0xFF74D3A2 : 0xFF315274;
                int fill = selected ? 0x8A1F3E57 : 0x7A1B334E;
                int accent = selected ? 0xFF74D3A2 : 0xFF68B7FF;
                graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, border);
                graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, fill);
                graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, accent);

                String name = card.playerName() == null || card.playerName().isBlank() ? "-" : card.playerName();
                String status = card.online() ? "Online" : "Offline";
                String role = card.guest() ? "Guest (No Role)" : (card.assignedRole() == null || card.assignedRole().isBlank() ? "-" : card.assignedRole());
                String flags = card.owner() ? "Owner" : (card.guest() ? "Guest" : role);
                graphics.drawString(this.font, fitToWidth(name, cardW - 16), cardX + 8, cardY + 7, 0xFFFFFFFF, false);
                graphics.drawString(this.font, fitToWidth("UUID: " + card.playerId(), cardW - 16), cardX + 8, cardY + 20, 0xFFCCE3FA, false);
                graphics.drawString(this.font, fitToWidth(flags + " • " + status, cardW - 16), cardX + 8, cardY + 33, 0xFFCFE4F8, false);
                String location = card.location() == null || card.location().isBlank() ? "-" : card.location();
                graphics.drawString(this.font, fitToWidth("Loc: " + location, cardW - 16), cardX + 8, cardY + 46, 0xFFB5CFE8, false);
                if (!card.guest() && !card.owner() && card.grantedAtMillis() > 0L) {
                    graphics.drawString(this.font,
                            fitToWidth("Granted " + formatRelativeTime(card.grantedAtMillis()), cardW - 16),
                            cardX + 8,
                            cardY + 59,
                            selected ? 0xFF8FE3BA : 0xFF9FC0DA,
                            false);
                } else {
                    graphics.drawString(this.font,
                            selected ? "Selected • Click role + update/remove" : "Click to select",
                            cardX + 8,
                            cardY + 59,
                            selected ? 0xFF8FE3BA : 0xFFAAC4DE,
                            false);
                }

                visibleShopPermissionMemberCards.add(new ShopPermissionMemberCardHitbox(cardX, cardY, cardW, cardH, card));
            }
            int rows = Math.max(1, (groupMembers.size() + cols - 1) / cols);
            cursorY += (rows * cardH) + (Math.max(0, rows - 1) * cardGap) + cardGap;
        }
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private int getShopVaultPlanEditorContentHeight(int width, int viewportHeight) {
        int cols = width >= 620 ? 2 : 1;
        int cardH = 54;
        int gap = 8;
        int rows = Math.max(1, (SHOP_CASH_DENOMINATIONS.length + cols - 1) / cols);
        int block = 8 + (rows * cardH) + (Math.max(0, rows - 1) * gap) + 24;
        return Math.max(viewportHeight, block);
    }

    private int drawShopVaultPlanEditor(GuiGraphics graphics,
                                        ShopVaultSnapshot vault,
                                        int x,
                                        int y,
                                        int width,
                                        int height) {
        int[] available = new int[SHOP_CASH_DENOMINATIONS.length];
        if (vault != null) {
            for (int i = 0; i < available.length && i < vault.counts().size(); i++) {
                available[i] = Math.max(0, vault.counts().get(i) == null ? 0 : vault.counts().get(i));
            }
        }
        int gap = 8;
        int cols = width >= 620 ? 2 : 1;
        int cardW = Math.max(220, (width - (gap * (cols - 1))) / cols);
        int cardH = 54;
        int contentHeight = getShopVaultPlanEditorContentHeight(width, height);
        int maxScroll = Math.max(0, contentHeight - height);
        outputScroll = Math.max(0, Math.min(outputScroll, maxScroll));

        if (!useVirtualScale) {
            enableScaledScissor(graphics, x + 1, y + 1, x + width - 1, y + height - 1);
        }
        int topY = y + 4 - outputScroll;
        for (int i = 0; i < SHOP_CASH_DENOMINATIONS.length; i++) {
            int row = i / cols;
            int col = i % cols;
            int cardX = x + (col * (cardW + gap));
            int cardY = topY + (row * (cardH + gap));
            if (cardY > (y + height) || (cardY + cardH) < y) {
                continue;
            }
            graphics.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, 0xFF315274);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x7A1B334E);
            graphics.fill(cardX, cardY, cardX + cardW, cardY + 2, 0xFF68B7FF);

            int denomCents = SHOP_CASH_DENOMINATIONS[i];
            int req = Math.max(0, shopVaultRequestedCounts[i]);
            int avail = Math.max(0, available[i]);
            String left = "$" + DollarBills.formatCents(denomCents);
            String right = "Avail: " + avail;
            graphics.drawString(this.font, fitToWidth(left + " • " + right, cardW - 90), cardX + 8, cardY + 7, 0xFFFFFFFF, false);
            graphics.drawString(this.font, "Selected: " + req, cardX + 8, cardY + 22, 0xFFCFE4F8, false);

            int minusX = cardX + cardW - 68;
            int plusX = cardX + cardW - 34;
            int btnY = cardY + 15;
            drawInlineActionButton(graphics, minusX, btnY, 26, 16, "-", 0xFF6A8CB0);
            drawInlineActionButton(graphics, plusX, btnY, 26, 16, "+", 0xFF6A8CB0);
            visibleShopVaultAdjustActions.add(new ShopVaultAdjustHitbox(minusX, btnY, 26, 16, i, false));
            visibleShopVaultAdjustActions.add(new ShopVaultAdjustHitbox(plusX, btnY, 26, 16, i, true));
        }
        int helperY = topY + (((SHOP_CASH_DENOMINATIONS.length + cols - 1) / cols) * (cardH + gap)) + 2;
        graphics.drawString(this.font,
                fitToWidth("Tip: click + / - to build the exact bill/coin mix, then use Withdraw Selected Bills.", Math.max(80, width - 10)),
                x + 4,
                helperY,
                0xFFBFD7EE,
                false);
        if (!useVirtualScale) {
            graphics.disableScissor();
        }
        return contentHeight;
    }

    private int getShopEmployeeCardsContentHeight(int width, int viewportHeight, int employeeCount) {
        int cols = width >= 620 ? 2 : 1;
        int cardH = 92;
        int gap = 8;
        int rows = Math.max(1, (Math.max(0, employeeCount) + cols - 1) / cols);
        int block = 8 + (rows * cardH) + (Math.max(0, rows - 1) * gap) + 8;
        return Math.max(viewportHeight, block);
    }

    private String shopKpiDescription(String label) {
        if (label == null || label.isBlank()) {
            return "Real-time KPI used by the shop dashboard.";
        }
        return switch (label.trim().toLowerCase(Locale.ROOT)) {
            case "revenue" -> "Total sales value made by this shop in the selected reporting window.";
            case "target" -> "Current sales target for this level. Reaching it unlocks progress to the next milestone.";
            case "next level target" -> "Sales goal required to reach the next shop level and unlock additional capacity.";
            case "all shops revenue" -> "Combined sales value from all shops in the world for benchmark comparison.";
            case "net profit" -> "Revenue minus operating costs; this is the effective earnings after expenses.";
            case "gross margin" -> "Profitability ratio before operating costs. Higher margin means healthier pricing.";
            case "operating expenses" -> "Estimated running costs for the shop such as staffing and maintenance overhead.";
            case "6m cash flow" -> "Projected six-month cash position based on current performance trends.";
            case "aov" -> "Average Order Value: average amount each completed checkout spends.";
            case "conversion" -> "Percent of visitors that become buyers. Higher conversion means better sales efficiency.";
            case "foot traffic" -> "Number of customer visits tracked for this shop in the active time window.";
            case "sales / labor hr" -> "Revenue generated per labor hour. Useful for staffing efficiency decisions.";
            case "cashiers linked" -> "How many cashier NPCs currently have an assigned payment terminal.";
            case "cash tx" -> "Number of completed customer transactions paid with physical cash.";
            case "terminal tx" -> "Number of completed customer transactions paid through a payment terminal/card.";
            case "cash customers" -> "Unique player count that paid in cash in the current reporting window.";
            case "terminal customers" -> "Unique player count that paid through terminals in the current reporting window.";
            case "cash collected" -> "Total cash value paid by customers, based on completed cash transactions.";
            case "card collected" -> "Total value paid through terminals/cards, routed to the configured settlement account.";
            case "vault tender" -> "Total legal tender currently stored in the shop cash vault from customer cash payments.";
            case "claim fill" -> "How much of the claim capacity is already used by the shop and stockroom regions.";
            case "shelves" -> "Number of configured shelf/display blocks currently registered to this shop.";
            case "configured slots" -> "Total product slots configured across active shelves and display units.";
            case "stock units" -> "Total item units currently available in shelf stock for customer purchase.";
            case "low stock" -> "Configured slots currently below low-stock threshold and needing restock soon.";
            case "out of stock" -> "Configured slots with zero sellable units left for customers.";
            case "turnover" -> "Inventory turnover ratio showing how quickly stocked items are sold and replenished.";
            case "stock/sales" -> "Stock-to-sales ratio for inventory health; high values can indicate overstock.";
            case "claim regions" -> "Amount of plot claim regions currently assigned to this shop.";
            case "stockroom regions" -> "Amount of stockroom claim regions used for storage scanning and restock.";
            case "wait time" -> "Average customer wait time before checkout service starts.";
            case "service time" -> "Average checkout service duration once a transaction begins.";
            case "csat" -> "Customer Satisfaction score estimate from in-game transaction quality signals.";
            case "nps" -> "Net Promoter Score style signal representing customer loyalty tendency.";
            case "cashiers" -> "Total cashier NPC employees registered under this shop.";
            case "linked terminals" -> "Number of payment terminals actively linked to cashier employees.";
            case "sales/employee" -> "Average revenue generated per cashier employee for the period.";
            case "rag" -> "Overall health status: Green is healthy, Amber needs attention, Red requires action.";
            case "all shops" -> "Total number of created shops currently tracked in the world.";
            case "best shop", "top shop" -> "Current highest performing shop by revenue in the active period.";
            default -> "Real-time KPI used by the shop dashboard.";
        };
    }

    private void drawKpiHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.BANK_APP || !isActiveShopApp() || activeSection != Section.OVERVIEW || visibleKpiCards.isEmpty()) {
            return;
        }
        if (shopLevelRoadmapOpen && shopLevelRoadmapSelectedNode != null) {
            return;
        }
        if (mouseX < outputPanelX || mouseX > (outputPanelX + outputPanelW)
                || mouseY < outputPanelY || mouseY > (outputPanelY + outputPanelH)) {
            return;
        }

        KpiCardHitbox hovered = null;
        for (KpiCardHitbox card : visibleKpiCards) {
            if (!card.contains(mouseX, mouseY)) {
                continue;
            }
            int cardRight = card.x() + card.width();
            int cardBottom = card.y() + card.height();
            if (cardRight < outputPanelX
                    || card.x() > (outputPanelX + outputPanelW)
                    || cardBottom < outputPanelY
                    || card.y() > (outputPanelY + outputPanelH)) {
                continue;
            }
            hovered = card;
            break;
        }
        if (hovered == null) {
            return;
        }

        int tooltipW = Math.min(320, Math.max(190, this.width / 3));
        String title = fitToWidth(hovered.label(), tooltipW - 14);
        String valueLine = fitToWidth("Value: " + hovered.value(), tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(hovered.description()), Math.max(80, tooltipW - 14));
        int tooltipH = 10 + 9 + 11 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;

        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);

        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(this.font, valueLine, x + 7, y + 23, 0xFFCFE8FF, false);

        int lineY = y + 35;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawShopInventoryActionHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.BANK_APP
                || !isActiveShopApp()
                || activeSection != Section.LIMITS
                || visibleShopInventoryActions.isEmpty()) {
            return;
        }
        if (mouseX < outputPanelX || mouseX > (outputPanelX + outputPanelW)
                || mouseY < outputPanelY || mouseY > (outputPanelY + outputPanelH)) {
            return;
        }

        ShopInventoryActionHitbox hovered = null;
        for (ShopInventoryActionHitbox hitbox : visibleShopInventoryActions) {
            if (hitbox.contains(mouseX, mouseY)) {
                hovered = hitbox;
                break;
            }
        }
        if (hovered == null) {
            return;
        }

        String actionLabel = shopInventoryActionLabel(hovered.action());
        String itemName = hovered.item() == null || hovered.item().itemName() == null || hovered.item().itemName().isBlank()
                ? (hovered.item() == null ? "Item" : hovered.item().itemId())
                : hovered.item().itemName();
        String description = shopInventoryActionDescription(hovered);

        int tooltipW = Math.min(320, Math.max(200, this.width / 3));
        String title = fitToWidth(actionLabel, tooltipW - 14);
        String itemLine = fitToWidth("Item: " + itemName, tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(description), Math.max(80, tooltipW - 14));
        int tooltipH = 10 + 9 + 11 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;

        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);

        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(this.font, itemLine, x + 7, y + 23, 0xFFCFE8FF, false);
        int lineY = y + 35;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawShopOperationsHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.BANK_APP
                || !isActiveShopApp()
                || (activeSection != Section.GOVERNANCE && activeSection != Section.HOURS)
                || visibleShopOperationsHelp.isEmpty()) {
            return;
        }
        if (mouseX < sectionViewportX || mouseX > (sectionViewportX + sectionViewportW)
                || mouseY < sectionViewportY || mouseY > (sectionViewportY + sectionViewportH)) {
            return;
        }

        ShopOperationsHelpHitbox hovered = null;
        for (ShopOperationsHelpHitbox hitbox : visibleShopOperationsHelp) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            hovered = hitbox;
            break;
        }
        if (hovered == null) {
            return;
        }

        int tooltipW = Math.min(320, Math.max(200, this.width / 3));
        String title = fitToWidth(hovered.title(), tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(hovered.description()), Math.max(80, tooltipW - 14));
        int tooltipH = 10 + 9 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;

        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        // Force tooltip above all section widgets so labels never overlap it.
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);

        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        int lineY = y + 23;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawOrderBoardHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.UTILITY_APP
                || activeUtilityApp != UtilityApp.ORDER_BOARD
                || visibleOrderBoardCards.isEmpty()) {
            return;
        }
        if (mouseX < outputPanelX || mouseX > (outputPanelX + outputPanelW)
                || mouseY < outputPanelY || mouseY > (outputPanelY + outputPanelH)) {
            return;
        }

        OrderBoardCardData hovered = null;
        for (OrderBoardCardHitbox hitbox : visibleOrderBoardCards) {
            if (!hitbox.contains(mouseX, mouseY)) {
                continue;
            }
            hovered = hitbox.order();
            break;
        }
        if (hovered == null) {
            return;
        }

        String itemName = hovered.itemName() == null || hovered.itemName().isBlank() ? hovered.itemId() : hovered.itemName();
        String status = hovered.status() == null || hovered.status().isBlank() ? "-" : hovered.status().toUpperCase(Locale.ROOT);
        String reward = MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, hovered.rewardCents()), 2));
        String timer = "ACCEPTED".equalsIgnoreCase(status)
                ? ("Time left: " + Math.max(0L, hovered.remainingSeconds()) + "s")
                : ("Timeout: " + Math.max(5, hovered.timeoutMinutes()) + "m");

        List<String> detailLines = new ArrayList<>();
        detailLines.add("Item: " + itemName + " x" + Math.max(1, hovered.quantity()));
        detailLines.add("Reward: " + reward + " | " + timer);
        detailLines.add("Drop: " + formatPalletRefLabel(hovered.boundPalletRef()));
        detailLines.add("Status: " + status + " | Courier: " + (hovered.acceptedByName() == null || hovered.acceptedByName().isBlank() ? "-" : hovered.acceptedByName()));
        detailLines.add("Click to select this order.");

        int tooltipW = Math.min(360, Math.max(220, this.width / 3));
        String title = fitToWidth("Order " + shortUuid(hovered.orderId()) + " • " + fitToWidth(status, 18), tooltipW - 14);
        List<String> wrapped = wrapLines(detailLines, Math.max(90, tooltipW - 14));
        int tooltipH = 10 + 9 + Math.max(11, wrapped.size() * LINE_HEIGHT) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;
        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        graphics.pose().pushPose();
        // Keep order-board hover hints above all app widgets and panel text.
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        int lineY = y + 23;
        for (String line : wrapped) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private void drawOrderBoardKpiHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (activeWindow != WindowMode.UTILITY_APP
                || activeUtilityApp != UtilityApp.ORDER_BOARD
                || visibleOrderBoardKpiCards.isEmpty()) {
            return;
        }
        if (mouseX < outputPanelX || mouseX > (outputPanelX + outputPanelW)
                || mouseY < outputPanelY || mouseY > (outputPanelY + outputPanelH)) {
            return;
        }

        KpiCardHitbox hovered = null;
        for (KpiCardHitbox card : visibleOrderBoardKpiCards) {
            if (!card.contains(mouseX, mouseY)) {
                continue;
            }
            hovered = card;
            break;
        }
        if (hovered == null) {
            return;
        }

        int tooltipW = Math.min(340, Math.max(210, this.width / 3));
        String title = fitToWidth(hovered.label(), tooltipW - 14);
        String valueLine = fitToWidth("Value: " + hovered.value(), tooltipW - 14);
        List<String> descriptionLines = wrapLines(List.of(hovered.description()), Math.max(90, tooltipW - 14));
        int tooltipH = 10 + 9 + 11 + Math.max(11, descriptionLines.size() * LINE_HEIGHT) + 8;

        int x = mouseX + 12;
        int y = mouseY + 12;
        int rightLimit = this.width - PAD - 6;
        int topLimit = PAD + TOPBAR_HEIGHT + 6;
        int bottomLimit = this.height - TASKBAR_HEIGHT - PAD - 6;
        if (x + tooltipW > rightLimit) {
            x = mouseX - tooltipW - 12;
        }
        if (x < PAD + 4) {
            x = PAD + 4;
        }
        if (y + tooltipH > bottomLimit) {
            y = bottomLimit - tooltipH;
        }
        if (y < topLimit) {
            y = topLimit;
        }

        graphics.pose().pushPose();
        // Keep KPI hover hints above all order-board widgets and cards.
        graphics.pose().translate(0.0F, 0.0F, HOVER_TOOLTIP_Z);
        graphics.fill(x - 1, y - 1, x + tooltipW + 1, y + tooltipH + 1, 0xE4274567);
        graphics.fill(x, y, x + tooltipW, y + tooltipH, 0xF0162D45);
        graphics.fill(x, y, x + tooltipW, y + 18, 0xD12B5E94);
        graphics.fill(x, y + 18, x + tooltipW, y + 19, 0x88A8CDEE);
        graphics.drawString(this.font, title, x + 7, y + 6, 0xFFFFFFFF, false);
        graphics.drawString(this.font, valueLine, x + 7, y + 23, 0xFFCFE8FF, false);
        int lineY = y + 35;
        for (String line : descriptionLines) {
            graphics.drawString(this.font, line, x + 7, lineY, 0xFFE5F2FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private String shopInventoryActionLabel(String action) {
        if (action == null || action.isBlank()) {
            return "Shelf Action";
        }
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "RESTOCK" -> "Restock Slot";
            case "REMOVE" -> "Remove From Shelf";
            case "MIN_DEC" -> "Lower Min Target";
            case "MIN_INC" -> "Raise Min Target";
            case "MAX_DEC" -> "Lower Max Target";
            case "MAX_INC" -> "Raise Max Target";
            default -> "Shelf Action";
        };
    }

    private String shopInventoryActionDescription(ShopInventoryActionHitbox hitbox) {
        if (hitbox == null) {
            return "Shelf inventory action.";
        }
        String action = hitbox.action() == null ? "" : hitbox.action().trim().toUpperCase(Locale.ROOT);
        boolean enabled = hitbox.enabled();
        ShopInventoryItemCardData item = hitbox.item();
        return switch (action) {
            case "RESTOCK" -> enabled
                    ? "Pulls matching items from stockroom inventories into this shelf slot until its max target is reached."
                    : "Restock is unavailable for this slot (already full, infinite stock, or not configured).";
            case "REMOVE" -> enabled
                    ? "Removes this display slot and returns all stock to stockroom storage."
                    : "Remove is unavailable for creative shelves or unconfigured slots.";
            case "MIN_DEC" -> enabled
                    ? "Decreases the low-stock threshold. Slots at or below min are flagged by Low Stock filter."
                    : "Target editing is unavailable for this slot.";
            case "MIN_INC" -> enabled
                    ? "Increases the low-stock threshold so this item is restocked earlier."
                    : "Target editing is unavailable for this slot.";
            case "MAX_DEC" -> enabled
                    ? "Decreases the max stock target. Restock will stop at this ceiling."
                    : "Target editing is unavailable for this slot.";
            case "MAX_INC" -> enabled
                    ? "Increases the max stock target (up to 64)."
                    : "Target editing is unavailable for this slot.";
            default -> {
                if (item != null && item.targetKey() != null && !item.targetKey().isBlank()) {
                    yield "Shelf inventory action for this configured slot.";
                }
                yield "Shelf inventory action.";
            }
        };
    }

    private void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;
        int x = x1;
        int y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) {
                break;
            }
            int e2 = err * 2;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * Parses tokenized roadmap lines produced by ShopService.levelRoadmapReport.
     */
    private ShopLevelRoadmapSnapshot parseShopLevelRoadmapSnapshot(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        Map<String, String> values = parseAtTokenMap(lines);
        boolean roadmapEnabled = "1".equals(values.get("roadmap.enabled"))
                || "true".equalsIgnoreCase(values.getOrDefault("roadmap.enabled", ""));
        List<ShopLevelRoadmapNode> nodes = new ArrayList<>();
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (!line.startsWith("@roadmap.node=")) {
                continue;
            }
            String payload = line.substring("@roadmap.node=".length()).trim();
            if (payload.isBlank()) {
                continue;
            }
            String[] parts = payload.split("\\|", -1);
            if (parts.length < 6) {
                continue;
            }
            int level = parseIntMetricToken(parts[0]);
            if (level <= 0) {
                continue;
            }
            long stockroomCap = parts.length >= 7 ? Math.max(0L, parseLongMetricToken(parts[3])) : 0L;
            int displayCap = parts.length >= 8 ? Math.max(0, parseIntMetricToken(parts[4])) : 0;
            int cashierCap = Math.max(0, parseIntMetricToken(parts[parts.length >= 8 ? 5 : 3]));
            int palletCap = Math.max(0, parseIntMetricToken(parts[parts.length >= 8 ? 6 : 4]));
            String state = parts[parts.length >= 8 ? 7 : 5].trim().isBlank()
                    ? "LOCKED"
                    : parts[parts.length >= 8 ? 7 : 5].trim().toUpperCase(Locale.ROOT);
            nodes.add(new ShopLevelRoadmapNode(
                    level,
                    Math.max(0L, parseLongMetricToken(parts[1])),
                    Math.max(0L, parseLongMetricToken(parts[2])),
                    stockroomCap,
                    displayCap,
                    cashierCap,
                    palletCap,
                    state
            ));
        }
        if (!roadmapEnabled && nodes.isEmpty()) {
            return null;
        }
        nodes.sort(java.util.Comparator.comparingInt(ShopLevelRoadmapNode::level));
        if (nodes.isEmpty()) {
            return null;
        }
        int maxLevel = Math.max(1, parseIntMetricToken(values.get("roadmap.max_level")));
        int currentLevel = Math.max(1, parseIntMetricToken(values.get("roadmap.current_level")));
        long revenue = Math.max(0L, parseLongMetricToken(values.get("roadmap.current_revenue_dollars")));
        long floor = Math.max(0L, parseLongMetricToken(values.get("roadmap.current_level_floor_dollars")));
        long nextTarget = Math.max(1L, parseLongMetricToken(values.get("roadmap.next_level_target_dollars")));
        double progress = Math.max(0.0D, Math.min(1.0D, parseDoubleMetricToken(values.get("roadmap.progress_ratio"))));
        return new ShopLevelRoadmapSnapshot(
                values.getOrDefault("roadmap.shop_name", "Shop"),
                values.getOrDefault("roadmap.shop_type", "INDEPENDENT_RETAILER"),
                currentLevel,
                revenue,
                floor,
                nextTarget,
                progress,
                maxLevel,
                nodes
        );
    }

    private ShopDashboardSnapshot parseShopDashboardSnapshot(List<String> lines) {
        Map<String, String> values = parseAtTokenMap(lines);
        if (!values.containsKey("shop.name")) {
            return null;
        }

        List<Long> trend = new ArrayList<>();
        String trendRaw = values.getOrDefault("trend.daily", "");
        if (!trendRaw.isBlank()) {
            String[] split = trendRaw.split(",");
            for (String token : split) {
                trend.add(parseLongMetricToken(token));
            }
        }

        Map<String, Long> categories = new LinkedHashMap<>();
        String catRaw = values.getOrDefault("category.sales", "");
        if (!catRaw.isBlank()) {
            String[] split = catRaw.split(",");
            for (String token : split) {
                String part = token == null ? "" : token.trim();
                int sep = part.indexOf(':');
                if (sep <= 0 || sep >= part.length() - 1) {
                    continue;
                }
                String key = part.substring(0, sep).trim();
                long value = parseLongMetricToken(part.substring(sep + 1));
                if (!key.isBlank()) {
                    categories.put(key, value);
                }
            }
        }

        return new ShopDashboardSnapshot(
                values.getOrDefault("shop.name", "Shop"),
                values.getOrDefault("shop.type", "INDEPENDENT_RETAILER"),
                values.getOrDefault("status.rag", "GREEN"),
                parseLongMetricToken(values.get("kpi.revenue_dollars")),
                parseLongMetricToken(values.get("kpi.target_dollars")),
                parseIntMetricToken(values.get("kpi.level")),
                parseLongMetricToken(values.get("kpi.next_level_target_dollars")),
                parseLongMetricToken(values.get("kpi.claim_used_blocks")),
                parseLongMetricToken(values.get("kpi.claim_capacity_blocks")),
                parseIntMetricToken(values.get("kpi.claim_regions")),
                parseIntMetricToken(values.get("kpi.stockroom_regions")),
                parseIntMetricToken(values.get("kpi.shelves")),
                parseIntMetricToken(values.get("kpi.configured_slots")),
                parseIntMetricToken(values.get("kpi.low_stock_slots")),
                parseIntMetricToken(values.get("kpi.out_of_stock_slots")),
                parseLongMetricToken(values.get("kpi.stock_units")),
                parseBooleanMetricToken(values.get("kpi.stock_units_infinite")),
                parseIntMetricToken(values.get("kpi.cashiers")),
                parseIntMetricToken(values.get("kpi.linked_cashiers")),
                parseLongMetricToken(values.get("kpi.cash_tx_count")),
                parseLongMetricToken(values.get("kpi.terminal_tx_count")),
                parseIntMetricToken(values.get("kpi.cash_customers")),
                parseIntMetricToken(values.get("kpi.terminal_customers")),
                parseLongMetricToken(values.get("kpi.cash_total_cents")),
                parseLongMetricToken(values.get("kpi.terminal_total_cents")),
                parseLongMetricToken(values.get("kpi.vault_total_cents")),
                parseDoubleMetricToken(values.get("kpi.aov")),
                parseDoubleMetricToken(values.get("kpi.conversion_rate")),
                parseDoubleMetricToken(values.get("kpi.gross_margin_pct")),
                parseDoubleMetricToken(values.get("kpi.operating_expenses")),
                parseDoubleMetricToken(values.get("kpi.net_profit")),
                parseDoubleMetricToken(values.get("kpi.inventory_turnover")),
                parseDoubleMetricToken(values.get("kpi.stock_to_sales_ratio")),
                parseDoubleMetricToken(values.get("kpi.sales_per_labor_hour")),
                parseLongMetricToken(values.get("kpi.foot_traffic")),
                parseDoubleMetricToken(values.get("kpi.wait_seconds")),
                parseDoubleMetricToken(values.get("kpi.service_seconds")),
                parseDoubleMetricToken(values.get("kpi.csat")),
                parseDoubleMetricToken(values.get("kpi.nps")),
                parseLongMetricToken(values.get("kpi.cashflow_6m")),
                parseLongMetricToken(values.get("kpi.all_shops_revenue_dollars")),
                parseIntMetricToken(values.get("kpi.all_shops_count")),
                values.getOrDefault("kpi.best_shop_name", "-"),
                trend,
                categories
        );
    }

    private int parseIntMetricToken(String raw) {
        long value = parseLongMetricToken(raw);
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private long parseLongMetricToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private double parseDoubleMetricToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0D;
        }
        try {
            return Double.parseDouble(raw.trim());
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private boolean parseBooleanMetricToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return "1".equals(normalized)
                || "true".equals(normalized)
                || "yes".equals(normalized)
                || "on".equals(normalized);
    }

    private String formatStockUnitsValue(ShopDashboardSnapshot snapshot) {
        if (snapshot == null) {
            return "0";
        }
        long finiteUnits = Math.max(0L, snapshot.stockUnits());
        if (snapshot.stockUnitsInfinite()) {
            return finiteUnits > 0L ? ("∞ + " + finiteUnits) : "∞";
        }
        return String.valueOf(finiteUnits);
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

    private void drawCreateShopWindowFrame(GuiGraphics graphics) {
        int width = Math.min(740, this.width - (PAD * 2) - 40);
        int left = (this.width - width) / 2;
        int top = PAD + TOPBAR_HEIGHT + 20;
        int right = left + width;
        int bottom = this.height - PAD - TASKBAR_HEIGHT - 20;

        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF2A3D59);
        graphics.fill(left, top, right, bottom, 0xFFE8EEF6);
        graphics.fill(left, top, right, top + 28, 0xFF6C93C8);

        graphics.drawString(this.font, "Create New Shop", left + 8, top + 10, 0xFFFFFFFF, false);
        graphics.drawString(this.font,
                "Owned: " + countOwnedShopApps() + " / " + MAX_SHOPS_PER_PLAYER,
                left + 230,
                top + 10,
                0xFFE8F2FF,
                false);

        graphics.drawString(this.font, "Shop Type", left + 8, top + 56, 0xFF1D2F4A, false);
        graphics.drawString(this.font,
                "Selected: " + prettifyShopType(selectedShopType),
                left + 8,
                top + 68,
                0xFF2A496E,
                false);
        graphics.drawString(this.font, "Description", left + 8, top + 84, 0xFF1D2F4A, false);
        List<String> descLines = wrapLines(
                List.of(shopTypeDescription(selectedShopType)),
                Math.max(120, width - 16)
        );
        int descY = top + 96;
        for (int i = 0; i < Math.min(3, descLines.size()); i++) {
            graphics.drawString(this.font, descLines.get(i), left + 8, descY + (i * LINE_HEIGHT), 0xFF2A496E, false);
        }
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
        } else if (activeUtilityApp == UtilityApp.SHOP_MANAGER) {
            drawShopManagerApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.SYSTEM_MONITOR) {
            drawSystemMonitorApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.ORDER_BOARD) {
            drawOrderBoardApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
        } else if (activeUtilityApp == UtilityApp.WEBSHOP) {
            drawWebshopApp(graphics, contentX + 4, contentY + 4, contentW - 8, contentH - 8);
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
            registerScrollbar(
                    ScrollbarTarget.NOTEPAD,
                    barX1,
                    notepadAreaY + 1,
                    Math.max(1, barX2 - barX1),
                    Math.max(1, notepadAreaH - 2),
                    notepadScroll,
                    maxScroll,
                    thumbH,
                    false
            );
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
                        ScrollbarTarget.PAINT_CONTROLS,
                        true,
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

    private void drawShopManagerApp(GuiGraphics graphics, int x, int y, int width, int height) {
        int infoX = x + 4;
        int infoY = y + 4;
        int infoW = Math.max(120, width - 8);
        graphics.drawString(this.font,
                fitToWidth("Create and manage your player shop. Use claim tools for plot and stockroom regions.", infoW - 8),
                infoX,
                infoY,
                0xFFD6EBFF,
                false);

        int listX = shopManagerViewportX > 0 ? shopManagerViewportX : (x + 4);
        int listY = shopManagerViewportY > 0 ? shopManagerViewportY : (y + 64);
        int listW = shopManagerViewportW > 0 ? shopManagerViewportW : Math.max(120, width - 8);
        int listH = shopManagerViewportH > 0 ? shopManagerViewportH : Math.max(80, (y + height) - listY - 4);

        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0xFF2D4B6D);
        graphics.fill(listX, listY, listX + listW, listY + listH, 0xD0182F47);
        graphics.fill(listX, listY, listX + listW, listY + 2, 0xFF72BDF5);

        List<String> rawLines = ClientOwnerPcData.getActionOutputLines();
        ShopDashboardSnapshot snapshot = parseShopDashboardSnapshot(rawLines);
        if (snapshot != null) {
            int contentX = listX + 2;
            int contentY = listY + 2 - shopManagerScroll;
            int contentW = Math.max(120, listW - 4);
            int contentH = getShopDashboardContentHeight(contentW, listH, Section.OVERVIEW);
            int maxScroll = Math.max(0, contentH - listH);
            shopManagerMaxScroll = maxScroll;
            shopManagerScroll = Math.max(0, Math.min(shopManagerScroll, maxScroll));

            enableScaledScissor(graphics, listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);
            drawShopDashboardSnapshot(graphics, snapshot, contentX, contentY, contentW, contentH, Section.OVERVIEW);
            graphics.disableScissor();

            if (maxScroll > 0) {
                drawVerticalScrollbar(
                        graphics,
                        ScrollbarTarget.SHOP_MANAGER,
                        true,
                        listX + listW - 4,
                        listY + 1,
                        3,
                        Math.max(10, listH - 2),
                        shopManagerScroll,
                        maxScroll
                );
            }
            return;
        }

        List<String> lines = getWrappedShopManagerLines(listW);
        int visible = Math.max(1, (listH - 8) / LINE_HEIGHT);
        int maxScroll = Math.max(0, lines.size() - visible);
        shopManagerMaxScroll = maxScroll;
        shopManagerScroll = Math.max(0, Math.min(shopManagerScroll, maxScroll));

        enableScaledScissor(graphics, listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);
        int lineY = listY + 4;
        for (int i = 0; i < visible; i++) {
            int idx = shopManagerScroll + i;
            if (idx >= lines.size()) {
                break;
            }
            graphics.drawString(this.font, lines.get(idx), listX + 6, lineY, 0xFFE7F3FF, false);
            lineY += LINE_HEIGHT;
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            drawVerticalScrollbar(
                    graphics,
                    ScrollbarTarget.SHOP_MANAGER,
                    true,
                    listX + listW - 4,
                    listY + 1,
                    3,
                    Math.max(10, listH - 2),
                    shopManagerScroll,
                    maxScroll
            );
        }
    }

    private List<String> getWrappedShopManagerLines(int width) {
        List<String> base = ClientOwnerPcData.getActionOutputLines();
        if (base.isEmpty()) {
            base = List.of(
                    "Shop Manager ready.",
                    "- Create Shop: creates your first shop profile.",
                    "- Claim Tool (Plot/Stockroom): closes UI and gives temporary selector hotbar.",
                    "- Claim Tool (Delivery Pallets): closes UI and labels delivery pallets with add/remove/save/cancel flow.",
                    "- Extra claims must connect to your existing shop claim.",
                    "- Plot claims cannot overlap your existing claimed regions.",
                    "- Stockroom claims must be fully inside your shop plot.",
                    "- Set Checkout Terminal: binds nearest payment terminal for cashier checkout.",
                    "- Scan Cashiers: lists cashier employees found inside your claims.",
                    "- Link Cashier Terminal: select cashier by index/UUID, then click a terminal.",
                    "- Scan Shelves: renders shelf cards with item previews, price, stock, and slot details.",
                    "- Per-item Restock button: refills one shelf slot from stockroom inventories on demand."
            );
        }
        return wrapLines(base, Math.max(100, width - 14));
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
                    ScrollbarTarget.SYSTEM_MONITOR,
                    true,
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

    private String prettifyShopType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Independent Retailer";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "FRANCHISE" -> "Franchise";
            case "CORPORATE_RETAIL_CHAIN" -> "Corporate Retail Chain";
            default -> "Independent Retailer";
        };
    }

    private String shopTypeDescription(String raw) {
        if (raw == null || raw.isBlank()) {
            return "A small business owned by an individual or family, allowing for complete control and flexibility in a local market.";
        }
        return switch (raw.toUpperCase(Locale.ROOT)) {
            case "FRANCHISE" ->
                    "A business owner (franchisee) buys the right to use a well-known brand, operating system, and support from a larger company (franchisor).";
            case "CORPORATE_RETAIL_CHAIN" ->
                    "Multiple outlets owned by one entity, benefiting from centralized buying power and economies of scale.";
            default ->
                    "A small business owned by an individual or family, allowing for complete control and flexibility in a local market.";
        };
    }

    private String formatLightingModeLabel(String mode, boolean selected) {
        String base = switch ((mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT))) {
            case "ON" -> "Always On";
            case "OFF" -> "Always Off";
            case "INVERTED" -> "Inverted";
            default -> "Opening Hours";
        };
        return selected ? "Selected: " + base : base;
    }

    private String lightingModeDescription(String mode) {
        return switch ((mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT))) {
            case "ON" -> "Lights stay on at all times, regardless of open or closed status.";
            case "OFF" -> "Lights stay off at all times, regardless of open or closed status.";
            case "INVERTED" -> "Lights are off while the store is open and turn on while the store is closed.";
            default -> "Lights follow shop hours: on while open and off while closed.";
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
