package net.austizz.ultimatebankingsystem.shop;

import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.account.transaction.UserTransaction;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.PalletBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.CardboardBoxBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.CardboardBoxDataKeys;
import net.austizz.ultimatebankingsystem.block.entity.custom.PalletBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShelfDisplayBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopSellingTableBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.ShopTerminalBlockEntity;
import net.austizz.ultimatebankingsystem.block.entity.custom.TallWallShelfBlockEntity;
import net.austizz.ultimatebankingsystem.claim.ClaimOutline;
import net.austizz.ultimatebankingsystem.claim.ClaimModeService;
import net.austizz.ultimatebankingsystem.claim.ClaimToolKind;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.item.CashierSpawnEggItem;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.network.DeliveryAlertPayload;
import net.austizz.ultimatebankingsystem.network.DeliveryInfoBoardPayload;
import net.austizz.ultimatebankingsystem.network.DeliveryPalletLabelSummary;
import net.austizz.ultimatebankingsystem.network.DeliveryPalletLabelsPayload;
import net.austizz.ultimatebankingsystem.network.ShopSetupObjectivePayload;
import net.austizz.ultimatebankingsystem.network.ServerNotification;
import net.austizz.ultimatebankingsystem.network.StockroomLocateRenderPayload;
import net.austizz.ultimatebankingsystem.npc.ShopCashierInteractionManager;
import net.austizz.ultimatebankingsystem.shelf.ShelfCartService;
import net.austizz.ultimatebankingsystem.shelf.ShelfPrice;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public final class ShopService {
    public record ShopActionResult(boolean success, String message) {}
    public record ShopSummary(UUID shopId,
                              String name,
                              String type,
                              int level,
                              long revenueDollars,
                              long nextTargetDollars,
                              long usedClaimBlocks,
                              long claimCapacityBlocks,
                              int claimRegions,
                              int stockroomRegions,
                              boolean ownerView,
                              String role) {}

    public record LeaderboardSeedResult(int rowsCreated,
                                        int rowsUpdated,
                                        int rowsRemoved,
                                        int rowsSkipped) {
        public int totalChanged() {
            return rowsCreated + rowsUpdated + rowsRemoved;
        }
    }

    public record CashierSummary(UUID cashierId,
                                 UUID employeeId,
                                 UUID shopId,
                                 String label,
                                 String dimensionId,
                                 BlockPos pos,
                                 boolean linkedTerminal,
                                 String linkedTerminalLabel) {}

    private record ClaimToolSession(UUID playerId,
                                    UUID ownerId,
                                    UUID shopId,
                                    boolean stockroomMode,
                                    boolean addMode,
                                    boolean overlayEnabled,
                                    long startedTick,
                                    long lastUpdatedTick,
                                    String firstDimensionId,
                                    BlockPos firstCorner,
                                    BlockPos secondCorner,
                                    List<ItemStack> hotbarSnapshot,
                                    int selectedHotbarSlot) {}

    private record PalletClaimToolSession(UUID playerId,
                                          UUID ownerId,
                                          UUID shopId,
                                          boolean addMode,
                                          long startedTick,
                                          long lastUpdatedTick,
                                          Set<String> baseAssignedRefs,
                                          Set<String> pendingAddRefs,
                                          Set<String> pendingRemoveRefs,
                                          List<ItemStack> hotbarSnapshot,
                                          int selectedHotbarSlot) {}

    private record CashierTerminalSelection(UUID playerId,
                                            UUID ownerId,
                                            UUID shopId,
                                            UUID cashierId,
                                            long startedTick) {}

    private record StockroomLocateSession(UUID playerId,
                                          UUID ownerId,
                                          UUID shopId,
                                          String dimensionId,
                                          BlockPos pos,
                                          int slot,
                                          String inventoryLabel,
                                          long startedTick) {}

    // Webshop cart/session state is intentionally in-memory:
    // it keeps UX snappy while persistent order data lives in central metadata.
    private static final class WebshopSessionState {
        private final UUID playerId;
        private final LinkedHashMap<String, Integer> cart = new LinkedHashMap<>();
        private UUID selectedAccountId;
        private UUID selectedShopId;
        // Coordinates mode was removed from the retail app; new sessions default to pallet-based delivery.
        private String deliveryMode = WEBSHOP_MODE_PALLET_RANDOM;
        private String selectedPalletId = "";
        private String deliveryDimensionId = "minecraft:overworld";
        private BlockPos deliveryPos = BlockPos.ZERO;
        private boolean expedite;
        private long updatedAtMillis;

        private WebshopSessionState(UUID playerId) {
            this.playerId = playerId;
            this.updatedAtMillis = System.currentTimeMillis();
        }
    }

    private record WebshopCatalogItem(String itemId,
                                      String itemName,
                                      String category,
                                      long unitPriceCents,
                                      String description) {}

    private record WebshopCartLine(String itemId,
                                   String itemName,
                                   int quantity,
                                   long unitPriceCents,
                                   long lineTotalCents) {}

    private record WebshopOrderEntry(UUID orderId,
                                     UUID buyerId,
                                     UUID accountId,
                                     long totalCents,
                                     String status,
                                     long createdAtMillis,
                                     long etaAtMillis,
                                     String deliveryMode,
                                     UUID shopId,
                                     String palletId,
                                     String dimensionId,
                                     BlockPos pos,
                                     int boxCount,
                                     int attempts,
                                     String lastError) {}

    private record WebshopDeliveryAttempt(boolean success,
                                          boolean retryable,
                                          String message) {}

    private record ShopPermissionEntry(UUID playerId,
                                       String role,
                                       long grantedAtMillis) {}

    private record PermissionDisplayEntry(String groupRole,
                                          UUID playerId,
                                          String playerName,
                                          String assignedRole,
                                          boolean online,
                                          boolean owner,
                                          boolean guest,
                                          long grantedAtMillis,
                                          String location) {}

    private record ManagedLightRef(String dimensionId,
                                   BlockPos pos,
                                   boolean stockroom) {}

    private record ShopSetupObjectiveState(boolean complete,
                                           int step,
                                           int totalSteps,
                                           String title,
                                           String detail,
                                           List<ShopSetupObjectivePayload.RequirementProgress> requirements) {
        private ShopSetupObjectiveState(boolean complete,
                                        int step,
                                        int totalSteps,
                                        String title,
                                        String detail) {
            this(complete, step, totalSteps, title, detail, List.of());
        }

        private ShopSetupObjectiveState {
            requirements = requirements == null ? List.of() : List.copyOf(requirements);
        }
    }

    private record SetupCashierStatus(int cashiers,
                                      int linkedCashiers) {}

    private record SetupSuppliesStatus(int shoppingBags,
                                       int displayItems) {}

    private record SetupDisplayStatus(int placedDisplays,
                                      int configuredShopDisplays) {}

    private record FranchiseOfferRef(CompoundTag franchisorShop,
                                     CompoundTag offer) {}

    /**
     * Tracks where a player entered a closed shop so we can return them to that
     * entry point when access is denied or grace windows expire.
     */
    private record ClosedShopEntryPoint(String dimensionId,
                                        double x,
                                        double y,
                                        double z) {}

    private static final String TAG_ROOT = "ubs_shop_root";
    private static final String TAG_SHOPS = "shops";
    private static final String TAG_ID = "id";
    private static final String TAG_OWNER = "owner";
    private static final String TAG_OWNER_NAME = "owner_name";
    private static final String TAG_NAME = "name";
    private static final String TAG_TYPE = "type";
    private static final String TAG_LEVEL = "level";
    private static final String TAG_REVENUE_DOLLARS = "revenue_dollars";
    private static final String TAG_NEXT_TARGET_DOLLARS = "next_target_dollars";
    private static final String TAG_CREATED_MILLIS = "created_millis";
    private static final String TAG_CLAIMS = "claims";
    private static final String TAG_STOCKROOM = "stockroom";
    private static final String TAG_STOCKROOM_CLAIMS = "stockroom_claims";
    private static final String TAG_CHECKOUT_TERMINAL = "checkout_terminal";
    private static final String TAG_CASHIER_TERMINALS = "cashier_terminals";
    private static final String TAG_SHOP_PERMISSIONS = "shop_permissions";
    private static final String TAG_PERMISSION_PLAYER = "player";
    private static final String TAG_PERMISSION_ROLE = "role";
    private static final String TAG_PERMISSION_GRANTED_AT = "granted_at";
    private static final int PALLET_LOCATE_SLOT_BASE = 10_000;
    private static final int PALLET_LOCATE_SLOT_COUNT = PalletBlockEntity.COLUMNS * PalletBlockEntity.LAYERS;
    private static final String TAG_CASHIER_ID = "cashier_id";
    private static final String TAG_SETTLEMENT_ACCOUNT_ID = "settlement_account_id";
    private static final String TAG_CASH_VAULT_COUNTS = "cash_vault_counts";
    private static final String TAG_METRIC_CASH_TX_COUNT = "metric_cash_tx_count";
    private static final String TAG_METRIC_CASH_TOTAL_CENTS = "metric_cash_total_cents";
    private static final String TAG_METRIC_CASH_UNIQUE_PLAYERS = "metric_cash_unique_players";
    private static final String TAG_METRIC_TERMINAL_TX_COUNT = "metric_terminal_tx_count";
    private static final String TAG_METRIC_TERMINAL_TOTAL_CENTS = "metric_terminal_total_cents";
    private static final String TAG_METRIC_TERMINAL_UNIQUE_PLAYERS = "metric_terminal_unique_players";
    private static final String TAG_DAILY_SALES_HISTORY = "daily_sales_history";
    private static final String TAG_SHELF_SLOT_META = "shelf_slot_meta";
    private static final String TAG_SLOT_KEY = "slot_key";
    private static final String TAG_SLOT_MIN_STOCK = "slot_min_stock";
    private static final String TAG_SLOT_MAX_STOCK = "slot_max_stock";
    private static final String TAG_SLOT_LAST_SOLD_MILLIS = "slot_last_sold_millis";
    private static final String TAG_SLOT_DAILY_SALES = "slot_daily_sales";
    private static final String TAG_ORDERS = "orders";
    private static final String TAG_ORDER_PALLETS = "order_pallets";
    private static final String TAG_ORDER_COURIERS = "order_couriers";
    private static final String TAG_ORDER_ID = "order_id";
    private static final String TAG_ORDER_ITEM_ID = "order_item_id";
    private static final String TAG_ORDER_ITEM_NAME = "order_item_name";
    private static final String TAG_ORDER_QTY = "order_qty";
    private static final String TAG_ORDER_REWARD_CENTS = "order_reward_cents";
    private static final String TAG_ORDER_TIMEOUT_MINUTES = "order_timeout_minutes";
    private static final String TAG_ORDER_STATUS = "order_status";
    private static final String TAG_ORDER_CREATED_AT = "order_created_at";
    private static final String TAG_ORDER_CREATED_BY = "order_created_by";
    private static final String TAG_ORDER_ACCEPTED_BY = "order_accepted_by";
    private static final String TAG_ORDER_ACCEPTED_BY_NAME = "order_accepted_by_name";
    private static final String TAG_ORDER_ACCEPTED_AT = "order_accepted_at";
    private static final String TAG_ORDER_EXPIRES_AT = "order_expires_at";
    private static final String TAG_ORDER_COMPLETED_AT = "order_completed_at";
    private static final String TAG_ORDER_COMPLETED_BY = "order_completed_by";
    private static final String TAG_ORDER_PAYOUT_CENTS = "order_payout_cents";
    private static final String TAG_ORDER_BONUS_CENTS = "order_bonus_cents";
    private static final String TAG_ORDER_ROUTE_DISTANCE_BLOCKS = "order_route_distance_blocks";
    private static final String TAG_ORDER_ROUTE_COMPLETED_MILLIS = "order_route_completed_millis";
    private static final String TAG_ORDER_PALLET_ID = "order_pallet_id";
    private static final String TAG_ORDER_PALLET_REF = "order_pallet_ref";
    private static final String TAG_ORDER_RESERVED_CENTS = "order_reserved_cents";
    private static final String TAG_ORDER_RESERVED_FROM_ACCOUNT = "order_reserved_from_account";
    private static final String TAG_PALLET_ID = "pallet_id";
    private static final String TAG_COURIER_ID = "courier_id";
    private static final String TAG_COURIER_NAME = "courier_name";
    private static final String TAG_COURIER_COMPLETED = "completed";
    private static final String TAG_COURIER_CANCELED = "canceled";
    private static final String TAG_COURIER_STREAK = "streak";
    private static final String TAG_COURIER_BEST_STREAK = "best_streak";
    private static final String TAG_COURIER_TOTAL_PAYOUT_CENTS = "total_payout_cents";
    private static final String TAG_COURIER_LAST_ACTIVITY_AT = "last_activity_at";
    private static final String TAG_COURIER_BEST_ROUTE_MILLIS = "best_route_millis";
    private static final String TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS = "best_route_distance_blocks";
    private static final String TAG_COURIER_BEST_ROUTE_SCORE = "best_route_score";
    private static final String TAG_COURIER_BEST_ROUTE_AT = "best_route_at";
    private static final String TAG_COURIER_SEED_DATA = "ubs_leaderboard_seed_data";
    private static final String TAG_COURIER_SEED_KEY = "ubs_leaderboard_seed_key";
    private static final String TAG_DAY = "day";
    private static final String TAG_AMOUNT = "amount";
    private static final String TAG_DIM = "dim";
    private static final String TAG_X = "x";
    private static final String TAG_Y = "y";
    private static final String TAG_Z = "z";
    private static final String TAG_MIN_X = "min_x";
    private static final String TAG_MIN_Y = "min_y";
    private static final String TAG_MIN_Z = "min_z";
    private static final String TAG_MAX_X = "max_x";
    private static final String TAG_MAX_Y = "max_y";
    private static final String TAG_MAX_Z = "max_z";
    private static final String TAG_SCHEDULE_OPEN_TICK = "schedule_open_tick";
    private static final String TAG_SCHEDULE_CLOSE_TICK = "schedule_close_tick";
    private static final String TAG_SCHEDULE_DAYS = "schedule_days";
    private static final String TAG_SCHEDULE_DAY_OPEN_MINUTE = "open_minute";
    private static final String TAG_SCHEDULE_DAY_CLOSE_MINUTE = "close_minute";
    private static final String TAG_SCHEDULE_LAST_OPEN = "schedule_last_open";
    private static final String TAG_SCHEDULE_LAST_NOTIFY_TICK = "schedule_last_notify_tick";
    private static final String TAG_SCHEDULE_LAST_EJECT_TICK = "schedule_last_eject_tick";
    private static final String TAG_LIGHTING_ENABLED = "lighting_enabled";
    private static final String TAG_LIGHTING_MAIN_MODE = "lighting_main_mode";
    private static final String TAG_LIGHTING_STOCKROOM_MODE = "lighting_stockroom_mode";
    private static final String TAG_LIGHTING_EXCLUDE_STOCKROOM = "lighting_exclude_stockroom";
    private static final String TAG_LIGHTING_LEVEL = "lighting_level";
    private static final String TAG_LIGHTING_MANAGED_BLOCKS = "lighting_managed_blocks";
    private static final String TAG_LIGHTING_STOCKROOM_FLAG = "stockroom";
    private static final String TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS = "closed_deliverer_stockroom_access";
    private static final String TAG_SETUP_COMPLETE = "setup_complete";
    private static final String TAG_TYPE_FREE_RECLASS_AVAILABLE = "type_free_reclass_available";
    private static final String TAG_TYPE_LAST_CONVERSION_MILLIS = "type_last_conversion_millis";
    private static final String TAG_TYPE_PAYABLE_CENTS = "type_payable_cents";
    private static final String TAG_TYPE_FEES_PAID_CENTS = "type_fees_paid_cents";
    private static final String TAG_TYPE_FEES_ACCRUED_CENTS = "type_fees_accrued_cents";
    private static final String TAG_FRANCHISE_BRAND = "franchise_brand";
    private static final String TAG_FRANCHISE_OFFERS = "franchise_offers";
    private static final String TAG_FRANCHISE_CONTRACTS = "franchise_contracts";
    private static final String TAG_FRANCHISE_CONTRACT = "franchise_contract";
    private static final String TAG_CORPORATE_HQ = "corporate_hq";
    private static final String TAG_CORPORATE_BRANCHES = "corporate_branches";
    private static final String TAG_REF_SHOP_ID = "shop_id";
    private static final String TAG_REF_OWNER_ID = "owner_id";
    private static final String TAG_REF_NAME = "name";
    private static final String TAG_REF_ACTIVE = "active";
    private static final String TAG_REF_CREATED_MILLIS = "created_millis";
    private static final String TAG_REF_NOTE = "note";
    private static final String TAG_OFFER_ID = "offer_id";
    private static final String TAG_OFFER_BRAND_NAME = "brand_name";
    private static final String TAG_OFFER_UPFRONT_CENTS = "upfront_cents";
    private static final String TAG_OFFER_ROYALTY_PERCENT = "royalty_percent";
    private static final String TAG_OFFER_MARKETING_PERCENT = "marketing_percent";
    private static final String TAG_OFFER_DIRECT_PLAYER = "direct_player";
    private static final String TAG_OFFER_RULES = "rules";
    private static final String TAG_OFFER_CONTRACT_EXPIRES_AT_MILLIS = "contract_expires_at_millis";
    private static final String TAG_FRANCHISE_REQUIRED_ITEMS = "required_items";
    private static final String TAG_REQ_ITEM_ID = "item_id";
    private static final String TAG_REQ_QUANTITY = "quantity";
    private static final String TAG_REQ_EXACT = "exact";
    private static final String TAG_REQ_NOTE = "note";
    private static final String TAG_REQ_STACK = "stack";
    private static final String TAG_CONTRACT_ID = "contract_id";
    private static final String TAG_CONTRACT_FRANCHISOR_SHOP_ID = "franchisor_shop_id";
    private static final String TAG_CONTRACT_FRANCHISEE_SHOP_ID = "franchisee_shop_id";
    private static final String TAG_CONTRACT_FRANCHISOR_OWNER_ID = "franchisor_owner_id";
    private static final String TAG_CONTRACT_FRANCHISEE_OWNER_ID = "franchisee_owner_id";
    private static final String TAG_CONTRACT_BRAND_NAME = "brand_name";
    private static final String TAG_CONTRACT_ROYALTY_PERCENT = "royalty_percent";
    private static final String TAG_CONTRACT_MARKETING_PERCENT = "marketing_percent";
    private static final String TAG_CONTRACT_RULES = "rules";
    private static final String TAG_CONTRACT_NPC = "npc";
    private static final String TAG_CONTRACT_LAST_NPC_ROYALTY_MILLIS = "last_npc_royalty_millis";
    private static final String TAG_CONTRACT_EXPIRES_AT_MILLIS = "expires_at_millis";
    private static final String TAG_CONTRACT_EXPIRED_AT_MILLIS = "expired_at_millis";

    private static final String TAG_WEBSHOP_ORDERS = "webshop_orders";
    private static final String TAG_WEBSHOP_ORDER_ITEMS = "webshop_order_items";
    private static final String TAG_WEBSHOP_ORDER_ID = "webshop_order_id";
    private static final String TAG_WEBSHOP_ORDER_BUYER = "webshop_order_buyer";
    private static final String TAG_WEBSHOP_ORDER_ACCOUNT = "webshop_order_account";
    private static final String TAG_WEBSHOP_ORDER_TOTAL_CENTS = "webshop_order_total_cents";
    private static final String TAG_WEBSHOP_ORDER_SUBTOTAL_CENTS = "webshop_order_subtotal_cents";
    private static final String TAG_WEBSHOP_ORDER_SURCHARGE_CENTS = "webshop_order_surcharge_cents";
    private static final String TAG_WEBSHOP_ORDER_STATUS = "webshop_order_status";
    private static final String TAG_WEBSHOP_ORDER_CREATED_AT = "webshop_order_created_at";
    private static final String TAG_WEBSHOP_ORDER_ETA_AT = "webshop_order_eta_at";
    private static final String TAG_WEBSHOP_ORDER_DELIVERED_AT = "webshop_order_delivered_at";
    private static final String TAG_WEBSHOP_ORDER_CANCELED_AT = "webshop_order_canceled_at";
    private static final String TAG_WEBSHOP_ORDER_FAILED_AT = "webshop_order_failed_at";
    private static final String TAG_WEBSHOP_ORDER_MODE = "webshop_order_mode";
    private static final String TAG_WEBSHOP_ORDER_SHOP_ID = "webshop_order_shop_id";
    private static final String TAG_WEBSHOP_ORDER_PALLET_ID = "webshop_order_pallet_id";
    private static final String TAG_WEBSHOP_ORDER_DIM = "webshop_order_dim";
    private static final String TAG_WEBSHOP_ORDER_X = "webshop_order_x";
    private static final String TAG_WEBSHOP_ORDER_Y = "webshop_order_y";
    private static final String TAG_WEBSHOP_ORDER_Z = "webshop_order_z";
    private static final String TAG_WEBSHOP_ORDER_ATTEMPTS = "webshop_order_attempts";
    private static final String TAG_WEBSHOP_ORDER_LAST_ERROR = "webshop_order_last_error";
    private static final int WEBSHOP_DELIVERY_ALERT_DURATION_MS = 4200;
    private static final String TAG_WEBSHOP_ITEM_ID = "item_id";
    private static final String TAG_WEBSHOP_ITEM_NAME = "item_name";
    private static final String TAG_WEBSHOP_ITEM_QTY = "qty";
    private static final String TAG_WEBSHOP_ITEM_UNIT_CENTS = "unit_cents";

    private static final int MAX_SHOPS_PER_OWNER = 3;
    private static final int BASE_CLAIM_RADIUS = 8;
    private static final int CLAIM_RADIUS_PER_LEVEL = 4;
    private static final int MAX_CLAIM_RADIUS = 48;
    private static final int CLAIM_MIN_Y_OFFSET = -4;
    private static final int CLAIM_MAX_Y_OFFSET = 6;
    private static final int TARGET_STOCK_PER_SLOT = 64;
    private static final int DEFAULT_MIN_STOCK_TARGET = 8;
    private static final int MAX_SLOT_TARGET = 64;
    private static final int SALES_VELOCITY_WINDOW_DAYS = 7;
    private static final int SALES_META_RETENTION_DAYS = 21;
    private static final int CLAIM_TOOL_TIMEOUT_TICKS = 20 * 60 * 5;
    private static final int STOCKROOM_LOCATE_TIMEOUT_TICKS = 20 * 60 * 3;
    private static final int BASE_CLAIM_BLOCKS = 24 * 24 * 12;
    private static final int CLAIM_BLOCKS_PER_LEVEL = 20 * 20 * 6;
    private static final int MAX_CLAIM_BLOCKS = 1_500_000;
    private static final int CASHIER_TERMINAL_SELECTION_TIMEOUT_TICKS = 20 * 120;
    private static final int DELIVERY_PALLET_HOVER_UPDATE_INTERVAL_TICKS = 10;
    private static final int DELIVERY_PALLET_LABEL_SYNC_INTERVAL_TICKS = 20;
    private static final double DELIVERY_PALLET_LABEL_SYNC_RANGE_SQ = 96.0D * 96.0D;
    private static final int DELIVERY_PALLET_LABEL_MAX_SYNCED_PER_PLAYER = 256;
    private static final String DELIVERY_PALLET_HOLOGRAM_TAG = "ubs_delivery_pallet_hologram";
    private static final String DELIVERY_PALLET_HOLOGRAM_REF_TAG_PREFIX = "ubs_delivery_pallet_hologram_ref:";
    private static final double DELIVERY_PALLET_HOLOGRAM_MIN_Y_OFFSET = 3.35D;
    private static final double DELIVERY_PALLET_HOLOGRAM_TOP_PADDING = 0.50D;
    private static final double DELIVERY_PALLET_HOLOGRAM_SEARCH_RADIUS = 2.0D;
    private static final double DELIVERY_PALLET_HOLOGRAM_PRUNE_RANGE = 160.0D;
    private static final int DELIVERY_PALLET_HOLOGRAM_BACKGROUND = 0x00000000;
    private static final int DELIVERY_INFO_SYNC_INTERVAL_TICKS = 20;
    private static final int SETUP_REQUIRED_SHOPPING_BAGS = 1;
    private static final int SETUP_REQUIRED_DISPLAY_ITEMS = 5;
    private static final int SHOP_STATUS_ENFORCE_INTERVAL_TICKS = 20;
    // Real server clock lead window before automatic close warnings.
    private static final int SHOP_CLOSE_WARNING_LEAD_SECONDS = 60 * 60;
    private static final int SHOP_LIGHTING_REFRESH_INTERVAL_TICKS = 20 * 10;
    private static final int SHOP_LIGHTING_MIN_LEVEL = 1;
    private static final int SHOP_LIGHTING_MAX_LEVEL = 15;
    private static final int SHOP_LIGHTING_DEFAULT_LEVEL = 15;
    private static final long CLOSED_SHOP_DELIVERER_LAND_GRACE_MS = 60_000L;
    private static final long CLOSED_SHOP_DELIVERER_STOCKROOM_GRACE_MS = 5L * 60_000L;
    // Stored schedule values are minute-of-day on the server clock (0..1439).
    private static final int SHOP_DEFAULT_OPEN_MINUTE = 9 * 60;  // 09:00 AM
    private static final int SHOP_DEFAULT_CLOSE_MINUTE = 21 * 60; // 09:00 PM
    private static final List<String> SHOP_SCHEDULE_DAY_KEYS = List.of(
            "MON",
            "TUE",
            "WED",
            "THU",
            "FRI",
            "SAT",
            "SUN"
    );
    private static final String LIGHT_MODE_ON = "ON";
    private static final String LIGHT_MODE_OFF = "OFF";
    private static final String LIGHT_MODE_OPEN_HOURS = "OPEN_HOURS";
    private static final String LIGHT_MODE_INVERTED = "INVERTED";
    private static final int ORDER_TIMEOUT_MINUTES_DEFAULT = 30;
    private static final int ORDER_TIMEOUT_MINUTES_MIN = 5;
    private static final int ORDER_TIMEOUT_MINUTES_MAX = 240;
    private static final int ORDER_RESERVE_BONUS_BUFFER_PCT = 20;
    private static final String ORDER_STATUS_OPEN = "OPEN";
    private static final String ORDER_STATUS_ACCEPTED = "ACCEPTED";
    private static final String ORDER_STATUS_COMPLETED = "COMPLETED";
    private static final String ORDER_STATUS_CANCELED = "CANCELED";
    private static final String ORDER_STATUS_EXPIRED = "EXPIRED";
    private static final String WEBSHOP_STATUS_QUEUED = "QUEUED";
    private static final String WEBSHOP_STATUS_DELIVERED = "DELIVERED";
    private static final String WEBSHOP_STATUS_CANCELED = "CANCELED";
    private static final String WEBSHOP_STATUS_FAILED = "FAILED";
    private static final String WEBSHOP_MODE_COORDS = "COORDS";
    private static final String WEBSHOP_MODE_PALLET_RANDOM = "PALLET_RANDOM";
    private static final String WEBSHOP_MODE_PALLET_SPECIFIC = "PALLET_SPECIFIC";
    private static final String CLAIM_TOOL_ITEM_TAG = "ubs_claim_tool_item";
    private static final String CLAIM_TOOL_ADD = "add";
    private static final String CLAIM_TOOL_REMOVE = "remove";
    private static final String CLAIM_TOOL_APPLY = "apply";
    private static final String CLAIM_TOOL_CLEAR = "clear";
    private static final String CLAIM_TOOL_OVERLAY = "overlay";
    private static final String CLAIM_TOOL_LOCK = "lock";
    private static final String CLAIM_TOOL_FINISH = "finish";
    private static final String PALLET_TOOL_ADD = "pallet_add";
    private static final String PALLET_TOOL_REMOVE = "pallet_remove";
    private static final String PALLET_TOOL_SAVE = "pallet_save";
    private static final String PALLET_TOOL_CANCEL = "pallet_cancel";
    private static final String PALLET_TOOL_LOCK = "pallet_lock";

    private static final ConcurrentHashMap<UUID, ClaimToolSession> CLAIM_TOOL_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PalletClaimToolSession> PALLET_CLAIM_TOOL_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, CashierTerminalSelection> CASHIER_TERMINAL_SELECTIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, StockroomLocateSession> STOCKROOM_LOCATE_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> DELIVERY_PALLET_HOVER_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> DELIVERY_PALLET_LABEL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> DELIVERY_PALLET_LABEL_PAYLOAD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, String> DELIVERY_INFO_BOARD_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, WebshopSessionState> WEBSHOP_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ClosedShopEntryPoint> CLOSED_SHOP_ENTRY_POINTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> CLOSED_SHOP_DELIVERER_LAND_WARNINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS = new ConcurrentHashMap<>();
    private static final Set<UUID> SHOP_SETUP_OBJECTIVE_TRACKED = ConcurrentHashMap.newKeySet();

    public static final String SHOP_TYPE_INDEPENDENT = "INDEPENDENT_RETAILER";
    public static final String SHOP_TYPE_FRANCHISE = "FRANCHISE";
    public static final String SHOP_TYPE_CORPORATE_CHAIN = "CORPORATE_RETAIL_CHAIN";
    private static final int FRANCHISE_NPC_ROYALTY_INTERVAL_HOURS = 24;
    private static final long FRANCHISE_NPC_ROYALTY_BASE_CENTS = 750L * 100L;
    public static final String SHOP_ROLE_OWNER = "OWNER";
    public static final String SHOP_ROLE_MANAGER = "MANAGER";
    public static final String SHOP_ROLE_BUILDER = "BUILDER";
    public static final String SHOP_ROLE_STAFF = "STAFF";
    public static final List<String> SHOP_TYPES = List.of(
            SHOP_TYPE_INDEPENDENT,
            SHOP_TYPE_FRANCHISE,
            SHOP_TYPE_CORPORATE_CHAIN
    );
    public static final List<String> SHOP_PERMISSION_ROLES = List.of(
            SHOP_ROLE_MANAGER,
            SHOP_ROLE_BUILDER,
            SHOP_ROLE_STAFF
    );

    private ShopService() {
    }

    public static ShopActionResult createShop(CentralBank centralBank, ServerPlayer owner, String rawName) {
        return createShop(centralBank, owner, rawName, SHOP_TYPE_INDEPENDENT);
    }

    public static ShopActionResult createShop(CentralBank centralBank, ServerPlayer owner, String rawName, String rawType) {
        if (centralBank == null || owner == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        String name = normalizeName(rawName);
        String type = normalizeShopType(rawType);
        if (name.isBlank()) {
            return new ShopActionResult(false, "Enter a shop name first.");
        }
        if (name.length() > 48) {
            return new ShopActionResult(false, "Shop name is too long (max 48 characters).");
        }

        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);

        int owned = 0;
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shopTag)) {
                continue;
            }
            if (shopTag.contains(TAG_OWNER) && owner.getUUID().equals(shopTag.getUUID(TAG_OWNER))) {
                owned++;
                String existingName = shopTag.getString(TAG_NAME);
                if (existingName.equalsIgnoreCase(name)) {
                    return new ShopActionResult(false, "You already have a shop named " + name + ".");
                }
            }
        }
        if (owned >= MAX_SHOPS_PER_OWNER) {
            return new ShopActionResult(false, "You already own the max number of shops (" + MAX_SHOPS_PER_OWNER + ").");
        }

        CompoundTag shop = new CompoundTag();
        UUID shopId = UUID.randomUUID();
        shop.putUUID(TAG_ID, shopId);
        shop.putUUID(TAG_OWNER, owner.getUUID());
        shop.putString(TAG_OWNER_NAME, owner.getName().getString());
        shop.putString(TAG_NAME, name);
        shop.putString(TAG_TYPE, type);
        shop.putInt(TAG_LEVEL, 1);
        shop.putLong(TAG_REVENUE_DOLLARS, 0L);
        shop.putLong(TAG_NEXT_TARGET_DOLLARS, targetForLevel(1));
        shop.putLong(TAG_CREATED_MILLIS, System.currentTimeMillis());
        shop.putBoolean(TAG_TYPE_FREE_RECLASS_AVAILABLE, false);
        shop.putLong(TAG_TYPE_LAST_CONVERSION_MILLIS, 0L);
        shop.putLong(TAG_TYPE_PAYABLE_CENTS, 0L);
        shop.putLong(TAG_TYPE_FEES_PAID_CENTS, 0L);
        shop.putLong(TAG_TYPE_FEES_ACCRUED_CENTS, 0L);
        shop.put(TAG_CLAIMS, new ListTag());
        shop.put(TAG_SHOP_PERMISSIONS, new ListTag());
        // Initialize shop-hours and lighting controls so owners can immediately manage opening windows.
        shop.putInt(TAG_SCHEDULE_OPEN_TICK, clampMinuteOfDay(SHOP_DEFAULT_OPEN_MINUTE));
        shop.putInt(TAG_SCHEDULE_CLOSE_TICK, clampMinuteOfDay(SHOP_DEFAULT_CLOSE_MINUTE));
        initializeWeeklySchedule(shop, SHOP_DEFAULT_OPEN_MINUTE, SHOP_DEFAULT_CLOSE_MINUTE);
        shop.putBoolean(TAG_SCHEDULE_LAST_OPEN, true);
        shop.putLong(TAG_SCHEDULE_LAST_NOTIFY_TICK, -1L);
        shop.putLong(TAG_SCHEDULE_LAST_EJECT_TICK, -1L);
        shop.putBoolean(TAG_LIGHTING_ENABLED, false);
        shop.putString(TAG_LIGHTING_MAIN_MODE, LIGHT_MODE_OPEN_HOURS);
        shop.putString(TAG_LIGHTING_STOCKROOM_MODE, LIGHT_MODE_OFF);
        shop.putBoolean(TAG_LIGHTING_EXCLUDE_STOCKROOM, true);
        shop.putInt(TAG_LIGHTING_LEVEL, SHOP_LIGHTING_DEFAULT_LEVEL);
        shop.put(TAG_LIGHTING_MANAGED_BLOCKS, new ListTag());
        shop.putBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS, false);
        // New shops start locked until setup objectives are completed.
        shop.putBoolean(TAG_SETUP_COMPLETE, false);
        shops.add(shop);

        root.put(TAG_SHOPS, shops);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        ShopSetupObjectiveState setupState = evaluateShopSetupObjective(shop, owner.server);
        PacketDistributor.sendToPlayer(owner, new ShopSetupObjectivePayload(
                !setupState.complete(),
                name,
                setupState.step(),
                setupState.totalSteps(),
                setupState.title(),
                setupState.detail(),
                setupState.requirements()
        ));
        SHOP_SETUP_OBJECTIVE_TRACKED.add(owner.getUUID());
        return new ShopActionResult(true, "Created shop: " + name + " (" + prettyShopType(type) + ").");
    }

    public static ShopActionResult renameShop(CentralBank centralBank,
                                              UUID ownerId,
                                              UUID shopId,
                                              String rawNewName) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        String newName = normalizeName(rawNewName);
        if (newName.isBlank()) {
            return new ShopActionResult(false, "Enter a new shop name first.");
        }
        if (newName.length() > 48) {
            return new ShopActionResult(false, "Shop name is too long (max 48 characters).");
        }

        String currentName = shop.getString(TAG_NAME);
        if (currentName.equalsIgnoreCase(newName)) {
            return new ShopActionResult(true, "Shop name is already " + currentName + ".");
        }

        UUID resolvedShopId = shop.getUUID(TAG_ID);
        for (CompoundTag ownerShop : getOwnerShops(centralBank, ownerId)) {
            if (!ownerShop.contains(TAG_ID) || resolvedShopId.equals(ownerShop.getUUID(TAG_ID))) {
                continue;
            }
            if (newName.equalsIgnoreCase(ownerShop.getString(TAG_NAME))) {
                return new ShopActionResult(false, "You already have a shop named " + newName + ".");
            }
        }

        shop.putString(TAG_NAME, newName);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Shop renamed to " + newName + ".");
    }

    public static ShopActionResult setShopType(CentralBank centralBank,
                                               UUID ownerId,
                                               UUID shopId,
                                               String rawType) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        String newType = normalizeShopType(rawType);
        String oldType = normalizeShopType(shop.getString(TAG_TYPE));
        if (oldType.equals(newType)) {
            return new ShopActionResult(true, "Shop type is already " + prettyShopType(newType) + ".");
        }
        long now = System.currentTimeMillis();
        long payableCents = Math.max(0L, shop.getLong(TAG_TYPE_PAYABLE_CENTS));
        if (payableCents > 0L) {
            return new ShopActionResult(false, "Pay outstanding shop-type fees first: " + formatCents(payableCents) + ".");
        }
        boolean freeReclass = isFreeTypeReclassAvailable(shop);
        if (!freeReclass) {
            long cooldownMillis = Math.max(0L, Config.SHOP_TYPE_CONVERSION_COOLDOWN_HOURS.get()) * 60L * 60L * 1000L;
            long last = Math.max(0L, shop.getLong(TAG_TYPE_LAST_CONVERSION_MILLIS));
            if (cooldownMillis > 0L && last > 0L && now - last < cooldownMillis) {
                long remainingMinutes = Math.max(1L, (cooldownMillis - (now - last) + 59_999L) / 60_000L);
                return new ShopActionResult(false, "Shop type conversion is on cooldown for " + remainingMinutes + " more minute(s).");
            }
            long feeCents = Math.max(0L, Config.SHOP_TYPE_CONVERSION_FEE_DOLLARS.get()) * 100L;
            if (feeCents > 0L) {
                ShopActionResult paid = debitShopSettlement(
                        centralBank,
                        ownerId,
                        shop.getUUID(TAG_ID),
                        feeCents,
                        "SHOP_TYPE_CONVERSION:" + prettyShopType(oldType) + "->" + prettyShopType(newType)
                );
                if (!paid.success()) {
                    return new ShopActionResult(false, "Type conversion fee " + formatCents(feeCents) + " could not be paid: " + paid.message());
                }
            }
        }
        clearTypeSpecificState(shop, oldType, newType);
        shop.putString(TAG_TYPE, newType);
        shop.putBoolean(TAG_TYPE_FREE_RECLASS_AVAILABLE, false);
        shop.putLong(TAG_TYPE_LAST_CONVERSION_MILLIS, now);
        if (SHOP_TYPE_CORPORATE_CHAIN.equals(newType)) {
            ensureCorporateHqState(shop);
        }
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Shop type set to " + prettyShopType(newType)
                + (freeReclass ? " using the one free migration reclass." : "."));
    }

    public static ShopActionResult shopTypeSystemReport(CentralBank centralBank, UUID ownerId, UUID shopId) {
        return shopTypeSystemReport(ServerLifecycleHooks.getCurrentServer(), centralBank, ownerId, shopId);
    }

    public static ShopActionResult shopTypeSystemReport(MinecraftServer server, CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        expireFranchiseAgreements(centralBank);
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        normalizeTypeStateForExistingShop(shop);
        String type = normalizeShopType(shop.getString(TAG_TYPE));
        int level = Math.max(1, shop.getInt(TAG_LEVEL));
        if (SHOP_TYPE_FRANCHISE.equals(type)) {
            processNpcFranchiseRoyalties(centralBank, shop);
        }
        long payableCents = Math.max(0L, shop.getLong(TAG_TYPE_PAYABLE_CENTS));
        long paidCents = Math.max(0L, shop.getLong(TAG_TYPE_FEES_PAID_CENTS));
        long accruedCents = Math.max(0L, shop.getLong(TAG_TYPE_FEES_ACCRUED_CENTS));
        List<String> lines = new ArrayList<>();
        lines.add("Shop Type System");
        lines.add("@type.shop_type=" + type);
        lines.add("@type.shop_label=" + sanitizeTokenText(prettyShopType(type)));
        lines.add("@type.level=" + level);
        lines.add("@type.payable_cents=" + payableCents);
        lines.add("@type.fees_paid_cents=" + paidCents);
        lines.add("@type.fees_accrued_cents=" + accruedCents);
        lines.add("@type.free_reclass=" + (isFreeTypeReclassAvailable(shop) ? "1" : "0"));
        lines.add("@type.last_conversion_millis=" + Math.max(0L, shop.getLong(TAG_TYPE_LAST_CONVERSION_MILLIS)));

        if (SHOP_TYPE_FRANCHISE.equals(type)) {
            int capacity = franchiseLicenseCapacityForLevel(level);
            int active = countActiveFranchiseContracts(shop);
            boolean canSell = level >= Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get());
            CompoundTag accepted = shop.getCompound(TAG_FRANCHISE_CONTRACT);
            lines.add("@franchise.can_sell=" + (canSell ? "1" : "0"));
            lines.add("@franchise.unlock_level=" + Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get()));
            lines.add("@franchise.license_capacity=" + capacity);
            lines.add("@franchise.active_licenses=" + active);
            lines.add("@franchise.accepted_brand=" + sanitizeTokenText(accepted.getString(TAG_CONTRACT_BRAND_NAME)));
            lines.add("@franchise.accepted_royalty_percent=" + accepted.getDouble(TAG_CONTRACT_ROYALTY_PERCENT));
            lines.add("@franchise.accepted_marketing_percent=" + accepted.getDouble(TAG_CONTRACT_MARKETING_PERCENT));
            lines.add("@franchise.accepted_expires_at_millis=" + contractExpiryMillis(accepted));
            FranchiseRequirementStatus acceptedStatus = evaluateFranchiseRequirements(server, shop, accepted);
            lines.add("@franchise.accepted_compliance=" + (acceptedStatus.compliant() ? "1" : "0")
                    + "|" + acceptedStatus.missingCount()
                    + "|" + Config.SHOP_FRANCHISE_NONCOMPLIANCE_PENALTY_PERCENT.get()
                    + "|" + sanitizeTokenText(acceptedStatus.missingSummary()));
            if (accepted.contains(TAG_CONTRACT_ID)) {
                appendFranchiseRequirementReportLines(
                        lines,
                        "franchise.accepted_req",
                        accepted.getUUID(TAG_CONTRACT_ID),
                        acceptedStatus
                );
            }
            appendFranchiseOfferReportLines(server, centralBank, ownerId, shop, lines);
            appendFranchiseContractReportLines(server, centralBank, shop, lines);
            lines.add("- Franchise: buy brand rights now; selling your own brand unlocks at level "
                    + Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get()) + ".");
            lines.add("- License capacity: " + active + " / " + capacity + " active franchisee(s).");
        } else if (SHOP_TYPE_CORPORATE_CHAIN.equals(type)) {
            ensureCorporateHqState(shop);
            int capacity = corporateBranchCapacityForLevel(level);
            int active = countCorporateBranches(shop);
            lines.add("@corporate.branch_capacity=" + capacity);
            lines.add("@corporate.active_branches=" + active);
            lines.add("@corporate.overhead_percent=" + Config.SHOP_CORPORATE_OVERHEAD_PERCENT.get());
            appendCorporateBranchReportLines(centralBank, shop, lines);
            lines.add("- Corporate: branch capacity " + active + " / " + capacity
                    + " with " + formatPercent(Config.SHOP_CORPORATE_OVERHEAD_PERCENT.get()) + " overhead on gross sales.");
        } else {
            lines.add("@independent.net_margin_bonus=1");
            lines.add("- Independent: no franchise royalties, no corporate overhead, and no network income.");
            lines.add("- Best for high-margin single-store play with full pricing and stock autonomy.");
        }
        lines.add("- Outstanding type fees: " + formatCents(payableCents)
                + " | Paid lifetime: " + formatCents(paidCents)
                + " | Accrued lifetime: " + formatCents(accruedCents));
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult payShopTypeFees(CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        long payableCents = Math.max(0L, shop.getLong(TAG_TYPE_PAYABLE_CENTS));
        if (payableCents <= 0L) {
            return new ShopActionResult(true, "No shop-type fees are due.");
        }
        ShopActionResult paid = debitShopSettlement(centralBank, ownerId, shop.getUUID(TAG_ID), payableCents, "SHOP_TYPE_FEES_PAYABLE");
        if (!paid.success()) {
            return paid;
        }
        shop.putLong(TAG_TYPE_PAYABLE_CENTS, 0L);
        shop.putLong(TAG_TYPE_FEES_PAID_CENTS, safeAdd(Math.max(0L, shop.getLong(TAG_TYPE_FEES_PAID_CENTS)), payableCents));
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Paid shop-type fees: " + formatCents(payableCents) + ".");
    }

    public static ShopActionResult publishFranchiseOffer(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId,
                                                         String rawTerms) {
        return publishFranchiseOffer(centralBank, ownerId, shopId, rawTerms, "");
    }

    public static ShopActionResult publishFranchiseOffer(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId,
                                                         String rawTerms,
                                                         String rawRequiredItems) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!SHOP_TYPE_FRANCHISE.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
            return new ShopActionResult(false, "Only Franchise shops can publish franchise offers.");
        }
        int level = Math.max(1, shop.getInt(TAG_LEVEL));
        int unlock = Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get());
        if (level < unlock) {
            return new ShopActionResult(false, "Selling your own franchise brand unlocks at shop level " + unlock + ".");
        }
        if (Math.max(0L, shop.getLong(TAG_TYPE_PAYABLE_CENTS)) > 0L) {
            return new ShopActionResult(false, "Pay outstanding shop-type fees before publishing franchise offers.");
        }
        String[] parts = (rawTerms == null ? "" : rawTerms).split("\\|", -1);
        String brand = normalizeName(parts.length > 0 ? parts[0] : "");
        if (brand.isBlank()) {
            brand = shop.getString(TAG_NAME);
        }
        if (brand.length() > 48) {
            return new ShopActionResult(false, "Brand name is too long (max 48 characters).");
        }
        long upfrontCents = dollarsToCents(parseLongOrDefault(parts.length > 1 ? parts[1] : "", Config.SHOP_FRANCHISE_DEFAULT_UPFRONT_FEE_DOLLARS.get()));
        double royalty = clampPercent(parseDoubleOrDefault(parts.length > 2 ? parts[2] : "", Config.SHOP_FRANCHISE_DEFAULT_ROYALTY_PERCENT.get()));
        double marketing = clampPercent(parseDoubleOrDefault(parts.length > 3 ? parts[3] : "", Config.SHOP_FRANCHISE_DEFAULT_MARKETING_PERCENT.get()));
        String rules = sanitizeTokenText(parts.length > 4 ? parts[4] : "Brand name, catalog template, price bands, hours template");
        UUID directPlayer = parseOptionalUuid(parts.length > 5 ? parts[5] : "");
        long contractExpiresAtMillis = parseContractExpiryMillis(parts.length > 6 ? parts[6] : "");
        if (contractExpiresAtMillis > 0L && contractExpiresAtMillis <= System.currentTimeMillis()) {
            return new ShopActionResult(false, "Contract end date must be in the future.");
        }
        ListTag requiredItems = parseFranchiseRequiredItemsPayload(rawRequiredItems);

        CompoundTag offer = new CompoundTag();
        UUID offerId = UUID.randomUUID();
        offer.putUUID(TAG_OFFER_ID, offerId);
        offer.putString(TAG_OFFER_BRAND_NAME, brand);
        offer.putLong(TAG_OFFER_UPFRONT_CENTS, Math.max(0L, upfrontCents));
        offer.putDouble(TAG_OFFER_ROYALTY_PERCENT, royalty);
        offer.putDouble(TAG_OFFER_MARKETING_PERCENT, marketing);
        offer.putString(TAG_OFFER_RULES, rules);
        if (contractExpiresAtMillis > 0L) {
            offer.putLong(TAG_OFFER_CONTRACT_EXPIRES_AT_MILLIS, contractExpiresAtMillis);
        }
        if (!requiredItems.isEmpty()) {
            offer.put(TAG_FRANCHISE_REQUIRED_ITEMS, requiredItems);
        }
        offer.putBoolean(TAG_REF_ACTIVE, true);
        offer.putLong(TAG_REF_CREATED_MILLIS, System.currentTimeMillis());
        if (directPlayer != null) {
            offer.putUUID(TAG_OFFER_DIRECT_PLAYER, directPlayer);
        }
        ListTag offers = shop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND);
        offers.add(offer);
        shop.put(TAG_FRANCHISE_OFFERS, offers);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Published franchise offer " + offerId + " for " + brand
                + " | upfront " + formatCents(upfrontCents)
                + " | royalty " + formatPercent(royalty)
                + " | marketing " + formatPercent(marketing)
                + (contractExpiresAtMillis > 0L ? " | ends " + formatContractExpiry(contractExpiresAtMillis) : "")
                + (requiredItems.isEmpty() ? "" : " | required shelf items " + requiredItems.size())
                + ".");
    }

    public static ShopActionResult cancelFranchiseOffer(CentralBank centralBank, UUID ownerId, UUID shopId, String rawOfferId) {
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID offerId = parseOptionalUuid(rawOfferId);
        if (offerId == null) {
            return new ShopActionResult(false, "Enter a valid franchise offer UUID.");
        }
        for (Tag tag : shop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag offer) || !offer.contains(TAG_OFFER_ID)) {
                continue;
            }
            if (offerId.equals(offer.getUUID(TAG_OFFER_ID))) {
                offer.putBoolean(TAG_REF_ACTIVE, false);
                saveShopTag(centralBank, shop);
                return new ShopActionResult(true, "Franchise offer canceled: " + offerId + ".");
            }
        }
        return new ShopActionResult(false, "Franchise offer not found for this shop.");
    }

    public static ShopActionResult acceptFranchiseOffer(CentralBank centralBank,
                                                        UUID buyerOwnerId,
                                                        UUID buyerShopId,
                                                        String rawOfferId) {
        return acceptFranchiseOffer(ServerLifecycleHooks.getCurrentServer(), centralBank, buyerOwnerId, buyerShopId, rawOfferId);
    }

    public static ShopActionResult acceptFranchiseOffer(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID buyerOwnerId,
                                                        UUID buyerShopId,
                                                        String rawOfferId) {
        if (centralBank == null || buyerOwnerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        expireFranchiseAgreements(centralBank);
        CompoundTag buyerShop = resolveShopTag(centralBank, buyerOwnerId, buyerShopId);
        if (buyerShop == null || !buyerShop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!SHOP_TYPE_FRANCHISE.equals(normalizeShopType(buyerShop.getString(TAG_TYPE)))) {
            return new ShopActionResult(false, "Only Franchise shops can buy franchise rights.");
        }
        if (buyerShop.contains(TAG_FRANCHISE_CONTRACT, Tag.TAG_COMPOUND)
                && buyerShop.getCompound(TAG_FRANCHISE_CONTRACT).getBoolean(TAG_REF_ACTIVE)) {
            return new ShopActionResult(false, "This shop already has an active franchise contract.");
        }
        if (Math.max(0L, buyerShop.getLong(TAG_TYPE_PAYABLE_CENTS)) > 0L) {
            return new ShopActionResult(false, "Pay outstanding shop-type fees before accepting a franchise offer.");
        }
        UUID offerId = parseOptionalUuid(rawOfferId);
        if (offerId == null) {
            return new ShopActionResult(false, "Enter a valid franchise offer UUID.");
        }
        FranchiseOfferRef ref = findActiveFranchiseOffer(centralBank, offerId);
        if (ref == null) {
            return new ShopActionResult(false, "Franchise offer not found or inactive.");
        }
        if (isFranchiseOfferExpired(ref.offer(), System.currentTimeMillis())) {
            ref.offer().putBoolean(TAG_REF_ACTIVE, false);
            saveShopTag(centralBank, ref.franchisorShop());
            return new ShopActionResult(false, "Franchise offer has expired. Ask the franchisor to publish a new offer.");
        }
        UUID buyerShopResolved = buyerShop.getUUID(TAG_ID);
        UUID franchisorShopId = ref.franchisorShop().getUUID(TAG_ID);
        if (buyerShopResolved.equals(franchisorShopId)) {
            return new ShopActionResult(false, "A shop cannot buy its own franchise offer.");
        }
        if (ref.offer().contains(TAG_OFFER_DIRECT_PLAYER)) {
            UUID direct = ref.offer().getUUID(TAG_OFFER_DIRECT_PLAYER);
            if (!buyerOwnerId.equals(direct)) {
                return new ShopActionResult(false, "This franchise offer is direct-only for another player.");
            }
        }
        int capacity = franchiseLicenseCapacityForLevel(Math.max(1, ref.franchisorShop().getInt(TAG_LEVEL)));
        int active = countActiveFranchiseContracts(ref.franchisorShop());
        if (active >= capacity) {
            return new ShopActionResult(false, "Franchise brand has no remaining license capacity.");
        }
        FranchiseRequirementStatus requirementStatus = evaluateFranchiseRequirements(server, buyerShop, ref.offer());
        if (!requirementStatus.compliant()) {
            return new ShopActionResult(false, "Missing brand-required shelf items: "
                    + requirementStatus.missingSummary()
                    + ". Place matching items on shop-mode shelves inside this shop plot, set a valid price, and stock non-creative shelves.");
        }

        UUID franchisorOwner = ref.franchisorShop().getUUID(TAG_OWNER);
        long upfront = Math.max(0L, ref.offer().getLong(TAG_OFFER_UPFRONT_CENTS));
        if (upfront > 0L) {
            UUID buyerSettlement = resolveSettlementAccountId(centralBank, buyerOwnerId, buyerShopResolved, null);
            UUID franchisorSettlement = resolveSettlementAccountId(centralBank, franchisorOwner, franchisorShopId, null);
            if (buyerSettlement == null || franchisorSettlement == null) {
                return new ShopActionResult(false, "Both shops need settlement accounts before accepting a franchise offer.");
            }
            ShopActionResult transfer = transferAccountCents(
                    centralBank,
                    buyerSettlement,
                    franchisorSettlement,
                    upfront,
                    "SHOP_FRANCHISE_UPFRONT:" + sanitizeTokenText(ref.offer().getString(TAG_OFFER_BRAND_NAME))
            );
            if (!transfer.success()) {
                return new ShopActionResult(false, "Upfront franchise fee failed: " + transfer.message());
            }
        }

        UUID contractId = UUID.randomUUID();
        CompoundTag franchiseeContract = buildFranchiseContractTag(contractId, ref.franchisorShop(), buyerShop, ref.offer(), false);
        buyerShop.put(TAG_FRANCHISE_CONTRACT, franchiseeContract);
        ListTag franchisorContracts = ref.franchisorShop().getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND);
        franchisorContracts.add(buildFranchiseContractTag(contractId, ref.franchisorShop(), buyerShop, ref.offer(), false));
        ref.franchisorShop().put(TAG_FRANCHISE_CONTRACTS, franchisorContracts);
        saveShopTag(centralBank, ref.franchisorShop());
        saveShopTag(centralBank, buyerShop);
        return new ShopActionResult(true, "Franchise contract accepted for "
                + ref.offer().getString(TAG_OFFER_BRAND_NAME) + " (" + contractId + ").");
    }

    public static ShopActionResult addNpcFranchisee(CentralBank centralBank, UUID ownerId, UUID shopId) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSingleplayer()) {
            return new ShopActionResult(false, "NPC franchisees are only available in solo worlds.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!SHOP_TYPE_FRANCHISE.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
            return new ShopActionResult(false, "Only Franchise shops can add NPC franchisees.");
        }
        int capacity = franchiseLicenseCapacityForLevel(Math.max(1, shop.getInt(TAG_LEVEL)));
        int active = countActiveFranchiseContracts(shop);
        if (active >= capacity) {
            return new ShopActionResult(false, "No remaining franchise license capacity.");
        }
        CompoundTag offer = new CompoundTag();
        offer.putString(TAG_OFFER_BRAND_NAME, shop.getString(TAG_NAME));
        offer.putDouble(TAG_OFFER_ROYALTY_PERCENT, Config.SHOP_FRANCHISE_DEFAULT_ROYALTY_PERCENT.get());
        offer.putDouble(TAG_OFFER_MARKETING_PERCENT, Config.SHOP_FRANCHISE_DEFAULT_MARKETING_PERCENT.get());
        offer.putString(TAG_OFFER_RULES, "NPC virtual licensee");
        CompoundTag npcShop = new CompoundTag();
        npcShop.putUUID(TAG_ID, UUID.randomUUID());
        npcShop.putUUID(TAG_OWNER, UUID.nameUUIDFromBytes(("ubs:npc-franchise:" + shop.getUUID(TAG_ID) + ":" + System.nanoTime()).getBytes()));
        npcShop.putString(TAG_NAME, "NPC Franchisee " + (active + 1));
        CompoundTag contract = buildFranchiseContractTag(UUID.randomUUID(), shop, npcShop, offer, true);
        contract.putLong(TAG_CONTRACT_LAST_NPC_ROYALTY_MILLIS, System.currentTimeMillis());
        ListTag contracts = shop.getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND);
        contracts.add(contract);
        shop.put(TAG_FRANCHISE_CONTRACTS, contracts);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Added solo-world NPC franchisee. It will generate virtual royalties once per day.");
    }

    public static ShopActionResult addCorporateBranch(CentralBank centralBank,
                                                      UUID ownerId,
                                                      UUID hqShopId,
                                                      String rawBranchShopId) {
        CompoundTag hq = resolveShopTag(centralBank, ownerId, hqShopId);
        if (hq == null || !hq.contains(TAG_ID)) {
            return new ShopActionResult(false, "Corporate HQ shop not found.");
        }
        if (!SHOP_TYPE_CORPORATE_CHAIN.equals(normalizeShopType(hq.getString(TAG_TYPE)))) {
            return new ShopActionResult(false, "Only Corporate Retail Chain shops can add branches.");
        }
        if (Math.max(0L, hq.getLong(TAG_TYPE_PAYABLE_CENTS)) > 0L) {
            return new ShopActionResult(false, "Pay outstanding shop-type fees before adding branches.");
        }
        UUID branchId = parseOptionalUuid(rawBranchShopId);
        if (branchId == null) {
            return new ShopActionResult(false, "Enter a valid branch shop UUID.");
        }
        CompoundTag branch = resolveShopTag(centralBank, ownerId, branchId);
        if (branch == null || !branch.contains(TAG_ID)) {
            return new ShopActionResult(false, "Branch shop must be owned by you.");
        }
        if (!SHOP_TYPE_CORPORATE_CHAIN.equals(normalizeShopType(branch.getString(TAG_TYPE)))) {
            return new ShopActionResult(false, "Branch shop must also be Corporate Retail Chain type.");
        }
        ensureCorporateHqState(hq);
        int capacity = corporateBranchCapacityForLevel(Math.max(1, hq.getInt(TAG_LEVEL)));
        int active = countCorporateBranches(hq);
        if (!hq.getUUID(TAG_ID).equals(branchId) && active >= capacity) {
            return new ShopActionResult(false, "Corporate branch capacity reached: " + active + " / " + capacity + ".");
        }
        if (containsShopRef(hq.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND), branchId)) {
            return new ShopActionResult(true, "Branch is already linked to this corporate chain.");
        }
        CompoundTag ref = new CompoundTag();
        ref.putUUID(TAG_REF_SHOP_ID, branchId);
        ref.putUUID(TAG_REF_OWNER_ID, ownerId);
        ref.putString(TAG_REF_NAME, branch.getString(TAG_NAME));
        ref.putBoolean(TAG_REF_ACTIVE, true);
        ref.putLong(TAG_REF_CREATED_MILLIS, System.currentTimeMillis());
        ListTag branches = hq.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND);
        branches.add(ref);
        hq.put(TAG_CORPORATE_BRANCHES, branches);
        branch.putUUID(TAG_CORPORATE_HQ, hq.getUUID(TAG_ID));
        saveShopTag(centralBank, hq);
        saveShopTag(centralBank, branch);
        return new ShopActionResult(true, "Added corporate branch: " + branch.getString(TAG_NAME) + ".");
    }

    public static ShopActionResult removeCorporateBranch(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID hqShopId,
                                                         String rawBranchShopId) {
        CompoundTag hq = resolveShopTag(centralBank, ownerId, hqShopId);
        if (hq == null || !hq.contains(TAG_ID)) {
            return new ShopActionResult(false, "Corporate HQ shop not found.");
        }
        UUID branchId = parseOptionalUuid(rawBranchShopId);
        if (branchId == null) {
            return new ShopActionResult(false, "Enter a valid branch shop UUID.");
        }
        boolean changed = false;
        ListTag branches = hq.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND);
        for (Tag tag : branches) {
            if (!(tag instanceof CompoundTag ref) || !ref.contains(TAG_REF_SHOP_ID)) {
                continue;
            }
            if (branchId.equals(ref.getUUID(TAG_REF_SHOP_ID))) {
                ref.putBoolean(TAG_REF_ACTIVE, false);
                changed = true;
            }
        }
        CompoundTag branch = resolveShopTag(centralBank, ownerId, branchId);
        if (branch != null) {
            branch.remove(TAG_CORPORATE_HQ);
            saveShopTag(centralBank, branch);
        }
        if (!changed) {
            return new ShopActionResult(false, "Corporate branch not found in this chain.");
        }
        saveShopTag(centralBank, hq);
        return new ShopActionResult(true, "Corporate branch unlinked: " + branchId + ".");
    }

    public static ShopActionResult clearCheckoutTerminal(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!shop.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            return new ShopActionResult(true, "Checkout terminal is already cleared.");
        }
        shop.remove(TAG_CHECKOUT_TERMINAL);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Checkout terminal removed.");
    }

    public static ShopActionResult clearCashierTerminalLinks(CentralBank centralBank,
                                                             UUID ownerId,
                                                             UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!shop.contains(TAG_CASHIER_TERMINALS, Tag.TAG_LIST)) {
            return new ShopActionResult(true, "No cashier terminal links found.");
        }
        shop.remove(TAG_CASHIER_TERMINALS);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "All cashier terminal links were cleared.");
    }

    public static ShopActionResult deleteShop(MinecraftServer server,
                                              CentralBank centralBank,
                                              UUID ownerId,
                                              UUID shopId,
                                              String rawConfirmName) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        String shopName = shop.getString(TAG_NAME);
        String confirmName = normalizeName(rawConfirmName);
        if (confirmName.isBlank()) {
            return new ShopActionResult(false, "Type the shop name to confirm deletion.");
        }
        if (!shopName.equalsIgnoreCase(confirmName)) {
            return new ShopActionResult(false, "Confirmation does not match shop name (" + shopName + ").");
        }

        UUID resolvedShopId = shop.getUUID(TAG_ID);
        if (!deleteShopTag(centralBank, ownerId, resolvedShopId)) {
            return new ShopActionResult(false, "Shop could not be deleted. Try again.");
        }

        clearSessionsForDeletedShop(server, ownerId, resolvedShopId);
        return new ShopActionResult(true, "Deleted shop " + shopName + ".");
    }

    /**
     * Lists shop apps visible to an actor (owner and delegated permission roles).
     * Owner shops are returned first, then delegated shops.
     */
    public static List<ShopSummary> listOwnerShopSummaries(CentralBank centralBank, UUID ownerId) {
        List<ShopSummary> summaries = new ArrayList<>();
        List<CompoundTag> shops = getAllShops(centralBank);
        shops.sort(Comparator
                .comparingLong((CompoundTag shop) -> shop.getLong(TAG_CREATED_MILLIS))
                .thenComparing(shop -> shop.getString(TAG_NAME), String.CASE_INSENSITIVE_ORDER));
        for (CompoundTag shop : shops) {
            UUID shopId = shop.contains(TAG_ID) ? shop.getUUID(TAG_ID) : null;
            String role = normalizeShopPermissionRole(resolveShopRole(shop, ownerId));
            if (role.isBlank()) {
                continue;
            }
            boolean ownerView = SHOP_ROLE_OWNER.equals(role);
            int level = Math.max(1, shop.getInt(TAG_LEVEL));
            ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
            ListTag stockrooms = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
            summaries.add(new ShopSummary(
                    shopId,
                    shop.getString(TAG_NAME),
                    normalizeShopType(shop.getString(TAG_TYPE)),
                    level,
                    Math.max(0L, shop.getLong(TAG_REVENUE_DOLLARS)),
                    Math.max(1L, shop.getLong(TAG_NEXT_TARGET_DOLLARS)),
                    computeVolume(claims),
                    claimCapacityForLevel(level),
                    claims.size(),
                    stockrooms.size(),
                    ownerView,
                    role
            ));
        }
        summaries.sort(Comparator
                .comparing(ShopSummary::ownerView).reversed()
                .thenComparing(ShopSummary::name, String.CASE_INSENSITIVE_ORDER));
        return summaries;
    }

    /**
     * Admin-oriented view over every registered shop, regardless of actor role.
     * This is used by the embedded web dashboard to populate global shop tables.
     */
    public static List<ShopSummary> listAllShopSummaries(CentralBank centralBank) {
        List<ShopSummary> summaries = new ArrayList<>();
        if (centralBank == null) {
            return summaries;
        }

        List<CompoundTag> shops = getAllShops(centralBank);
        shops.sort(Comparator
                .comparingLong((CompoundTag shop) -> shop.getLong(TAG_CREATED_MILLIS))
                .thenComparing(shop -> shop.getString(TAG_NAME), String.CASE_INSENSITIVE_ORDER));

        for (CompoundTag shop : shops) {
            UUID shopId = shop.contains(TAG_ID) ? shop.getUUID(TAG_ID) : null;
            if (shopId == null) {
                continue;
            }

            int level = Math.max(1, shop.getInt(TAG_LEVEL));
            ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
            ListTag stockrooms = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
            summaries.add(new ShopSummary(
                    shopId,
                    shop.getString(TAG_NAME),
                    normalizeShopType(shop.getString(TAG_TYPE)),
                    level,
                    Math.max(0L, shop.getLong(TAG_REVENUE_DOLLARS)),
                    Math.max(1L, shop.getLong(TAG_NEXT_TARGET_DOLLARS)),
                    computeVolume(claims),
                    claimCapacityForLevel(level),
                    claims.size(),
                    stockrooms.size(),
                    true,
                    SHOP_ROLE_OWNER
            ));
        }

        summaries.sort(Comparator.comparing(ShopSummary::name, String.CASE_INSENSITIVE_ORDER));
        return summaries;
    }

    /**
     * Returns every explicit participant role for a shop (owner + delegated entries).
     */
    public static Map<UUID, String> listShopParticipantRoles(CentralBank centralBank, UUID shopId) {
        Map<UUID, String> roles = new LinkedHashMap<>();
        if (centralBank == null || shopId == null) {
            return roles;
        }

        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null) {
            return roles;
        }

        if (shop.contains(TAG_OWNER)) {
            try {
                roles.put(shop.getUUID(TAG_OWNER), SHOP_ROLE_OWNER);
            } catch (Exception ignored) {
            }
        }

        for (ShopPermissionEntry entry : decodeShopPermissions(shop)) {
            if (entry == null || entry.playerId() == null) {
                continue;
            }
            String normalizedRole = normalizeShopPermissionRole(entry.role());
            if (normalizedRole.isBlank()) {
                continue;
            }
            roles.put(entry.playerId(), normalizedRole);
        }
        return roles;
    }

    /**
     * Resolves a default/primary shop for an actor. This supports delegated roles
     * that do not own shops but still need a selected context in the desktop app.
     */
    public static UUID resolveDefaultShopIdForActor(CentralBank centralBank, UUID actorId) {
        if (centralBank == null || actorId == null) {
            return null;
        }
        for (ShopSummary summary : listOwnerShopSummaries(centralBank, actorId)) {
            if (summary != null && summary.shopId() != null) {
                return summary.shopId();
            }
        }
        return null;
    }

    public static int maxShopsPerOwner() {
        return MAX_SHOPS_PER_OWNER;
    }

    public static String prettyShopType(String type) {
        return switch (normalizeShopType(type)) {
            case SHOP_TYPE_FRANCHISE -> "Franchise";
            case SHOP_TYPE_CORPORATE_CHAIN -> "Corporate Retail Chain";
            default -> "Independent Retailer";
        };
    }

    public static boolean hasShop(CentralBank centralBank, UUID ownerId, UUID shopId) {
        return resolveShopTag(centralBank, ownerId, shopId) != null;
    }

    public static String resolveShopRole(CentralBank centralBank, UUID playerId, UUID shopId) {
        if (centralBank == null || playerId == null || shopId == null) {
            return "";
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        return resolveShopRole(shop, playerId);
    }

    public static boolean canManageShop(CentralBank centralBank, UUID playerId, UUID shopId) {
        String role = resolveShopRole(centralBank, playerId, shopId);
        return SHOP_ROLE_OWNER.equals(role) || SHOP_ROLE_MANAGER.equals(role);
    }

    public static boolean canBuildInShop(CentralBank centralBank, UUID playerId, UUID shopId) {
        String role = resolveShopRole(centralBank, playerId, shopId);
        return SHOP_ROLE_OWNER.equals(role) || SHOP_ROLE_MANAGER.equals(role) || SHOP_ROLE_BUILDER.equals(role);
    }

    /**
     * Resolves a shop at position for a non-owner actor. This is used by delegated
     * shop permissions so builders/managers can work inside claimed plot land.
     */
    public static UUID resolveShopAtPosForActor(CentralBank centralBank,
                                                UUID actorId,
                                                String dimensionId,
                                                BlockPos pos,
                                                boolean requireBuildPermission) {
        if (centralBank == null || actorId == null || pos == null) {
            return null;
        }
        String dim = normalizedDim(dimensionId);
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            if (!isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), dim, pos)) {
                continue;
            }
            boolean allowed = requireBuildPermission
                    ? canBuildInShop(shop, actorId)
                    : canManageShop(shop, actorId);
            if (!allowed) {
                continue;
            }
            return shop.getUUID(TAG_ID);
        }
        return null;
    }

    public static boolean isShopOpenForShopping(CentralBank centralBank, UUID shopId, long gameTime) {
        if (centralBank == null || shopId == null) {
            return true;
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null) {
            return true;
        }
        if (!isShopSetupComplete(shop)) {
            return false;
        }
        int minuteOfDay = resolveCurrentMinuteOfDay();
        return isShopOpenAtMinute(shop, minuteOfDay);
    }

    public static boolean isShopSetupComplete(CentralBank centralBank, UUID shopId) {
        if (centralBank == null || shopId == null) {
            return false;
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        return isShopSetupComplete(shop);
    }

    public static boolean canRemainInClosedShop(CentralBank centralBank,
                                                UUID shopId,
                                                UUID playerId,
                                                MinecraftServer server) {
        if (centralBank == null || shopId == null || playerId == null) {
            return false;
        }
        if (isOpActor(server, playerId)) {
            return true;
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        return hasShopRoleAtLeast(shop, playerId, SHOP_ROLE_STAFF);
    }

    private record ClosedCourierDeliveryContext(UUID shopId, CompoundTag shop) {}

    /**
     * True when the actor is using the special closed-hours courier access window
     * (accepted delivery order + shop closed + no staff/owner/op privileges).
     * In this mode the actor must be restricted to delivery-pallet actions only.
     */
    public static boolean isCourierDeliveryOnlyMode(CentralBank centralBank,
                                                    MinecraftServer server,
                                                    UUID playerId,
                                                    String dimensionId,
                                                    BlockPos pos) {
        return findClosedCourierDeliveryContext(
                centralBank,
                server,
                playerId,
                dimensionId,
                pos,
                System.currentTimeMillis()
        ) != null;
    }

    /**
     * Returns true only for the one allowed interaction in closed-hours courier mode:
     * placing a cardboard box on an assigned delivery pallet that belongs to the same shop.
     */
    public static boolean canUseCourierDeliveryPalletInteraction(CentralBank centralBank,
                                                                 MinecraftServer server,
                                                                 ServerPlayer player,
                                                                 ServerLevel level,
                                                                 BlockPos clickedPos,
                                                                 ItemStack heldStack) {
        if (centralBank == null || server == null || player == null || level == null || clickedPos == null) {
            return false;
        }
        ClosedCourierDeliveryContext context = findClosedCourierDeliveryContext(
                centralBank,
                server,
                player.getUUID(),
                level.dimension().location().toString(),
                player.blockPosition(),
                System.currentTimeMillis()
        );
        if (context == null) {
            return false;
        }

        BlockState clickedState = level.getBlockState(clickedPos);
        if (!clickedState.is(ModBlocks.PALLET.get())) {
            return false;
        }
        if (heldStack == null || heldStack.isEmpty() || !heldStack.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
            return false;
        }

        BlockPos masterPos = PalletBlock.getMasterPos(clickedState, clickedPos);
        FoundAssignedPallet found = findAssignedPalletShop(
                centralBank,
                level.dimension().location().toString(),
                masterPos
        );
        if (found == null || found.shopTag() == null || !found.shopTag().contains(TAG_ID)) {
            return false;
        }
        return context.shopId().equals(found.shopTag().getUUID(TAG_ID));
    }

    private static ClosedCourierDeliveryContext findClosedCourierDeliveryContext(CentralBank centralBank,
                                                                                 MinecraftServer server,
                                                                                 UUID playerId,
                                                                                 String dimensionId,
                                                                                 BlockPos pos,
                                                                                 long nowMillis) {
        if (centralBank == null || server == null || playerId == null || pos == null) {
            return null;
        }
        if (isOpActor(server, playerId)) {
            return null;
        }

        int minuteOfDay = resolveCurrentMinuteOfDay(server);
        String dim = normalizedDim(dimensionId);
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
            ListTag stockroomClaims = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
            if (!isInsideClaims(claims, dim, pos) && !isInsideClaims(stockroomClaims, dim, pos)) {
                continue;
            }

            UUID shopId = shop.getUUID(TAG_ID);
            if (canRemainInClosedShop(centralBank, shopId, playerId, server)) {
                continue;
            }
            if (!isShopSetupComplete(shop)) {
                continue;
            }
            if (isShopOpenAtMinute(shop, minuteOfDay)) {
                continue;
            }
            if (!shop.getBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS)) {
                continue;
            }
            if (!hasActiveAcceptedDeliveryOrder(shop, playerId, nowMillis)) {
                continue;
            }
            return new ClosedCourierDeliveryContext(shopId, shop);
        }
        return null;
    }

    /**
     * Evaluates setup requirements in sequence so owners receive one guided objective at a time.
     */
    private static ShopSetupObjectiveState evaluateShopSetupObjective(CompoundTag shop) {
        return evaluateShopSetupObjective(shop, null);
    }

    /**
     * Server-aware variant used by status ticks so setup objectives can reflect
     * live world placement (for example pallets physically placed in claimed land).
     */
    private static ShopSetupObjectiveState evaluateShopSetupObjective(CompoundTag shop, MinecraftServer server) {
        final int totalSteps = 9;
        if (shop == null) {
            return new ShopSetupObjectiveState(
                    false,
                    1,
                    totalSteps,
                    "Claim your first shop plot",
                    "Where: Bank Owner PC > Retail Shop > Operations > Claim Tool: Plot Claim. Use the selector on your store floor, then save.",
                    List.of(setupProgress("plot regions", 0, 1))
            );
        }
        int plotClaims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND).size();
        int stockroomClaims = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND).size();
        int deliveryPallets = resolveSetupDeliveryPalletCount(shop, server);
        int basketHolders = resolveSetupBasketHolderCount(shop, server);
        boolean hasSettlementAccount = hasValidSettlementAccount(shop);
        SetupCashierStatus cashierStatus = resolveSetupCashierStatus(shop, server);
        SetupSuppliesStatus suppliesStatus = resolveSetupSuppliesStatus(shop, server);
        SetupDisplayStatus displayStatus = resolveSetupDisplayStatus(shop, server);

        if (plotClaims <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    1,
                    totalSteps,
                    "Claim your first shop plot",
                    "Where: Bank Owner PC > Retail Shop > Operations > Claim Tool: Plot Claim. Select the customer/shop floor, then save.",
                    List.of(setupProgress("plot regions", plotClaims, 1))
            );
        }
        if (stockroomClaims <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    2,
                    totalSteps,
                    "Assign a stockroom region",
                    "Where: Bank Owner PC > Retail Shop > Operations > Claim Tool: Stockroom Claim. Select storage space inside the shop plot, then save.",
                    List.of(setupProgress("stockroom regions", stockroomClaims, 1))
            );
        }
        if (deliveryPallets <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    3,
                    totalSteps,
                    "Label a delivery pallet",
                    "Where: Bank Owner PC > Retail Shop > Operations > Claim Tool: Delivery Pallets. Hold Add Delivery Pallet, click a pallet inside the shop claim or stockroom claim, then Paper=Save.",
                    List.of(setupProgress("delivery pallets", deliveryPallets, 1))
            );
        }
        if (basketHolders <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    4,
                    totalSteps,
                    "Place a shopping basket holder",
                    "Where: UBS shop blocks/creative tab or recipe/JEI > Shopping Basket Holder. Place at least one inside the shop claim for customers.",
                    List.of(setupProgress("basket holders", basketHolders, 1))
            );
        }
        if (!hasSettlementAccount) {
            return new ShopSetupObjectiveState(
                    false,
                    5,
                    totalSteps,
                    "Set a settlement account",
                    "Where: Bank Owner PC > Retail Shop > Finance > Settlement Account. Select the account that receives terminal/card revenue.",
                    List.of(setupProgress("settlement account", hasSettlementAccount ? 1 : 0, 1))
            );
        }
        if (cashierStatus.linkedCashiers() <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    6,
                    totalSteps,
                    "Set up a linked cashier",
                    "Where: Bank Owner PC > Retail Shop > Employees/Cashiers. Spawn/place a cashier inside the shop plot, place a Payment Terminal inside the shop plot, "
                            + "then use Cashier-Terminal Link and click the cashier plus terminal.",
                    List.of(
                            setupProgress("cashiers in shop", cashierStatus.cashiers(), 1),
                            setupProgress("linked cashier terminals", cashierStatus.linkedCashiers(), 1)
                    )
            );
        }
        if (suppliesStatus.shoppingBags() < SETUP_REQUIRED_SHOPPING_BAGS
                || suppliesStatus.displayItems() < SETUP_REQUIRED_DISPLAY_ITEMS) {
            return new ShopSetupObjectiveState(
                    false,
                    7,
                    totalSteps,
                    "Order shop supplies",
                    "Where: Bank Owner PC > Retail Shop > Webshop. Order Shopping Bags and at least 5 display blocks, then deliver/store them in the stockroom.",
                    List.of(
                            setupProgress("shopping bags in stockroom", suppliesStatus.shoppingBags(), SETUP_REQUIRED_SHOPPING_BAGS),
                            setupProgress("display blocks in stockroom or placed in shop", suppliesStatus.displayItems(), SETUP_REQUIRED_DISPLAY_ITEMS)
                    )
            );
        }
        if (displayStatus.placedDisplays() <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    8,
                    totalSteps,
                    "Place a shop display",
                    "Where: Place one retail display block inside the shop plot. Shelves, selling tables, wall displays, glass counters, and invisible displays all count.",
                    List.of(setupProgress("placed shop displays", displayStatus.placedDisplays(), 1))
            );
        }
        if (displayStatus.configuredShopDisplays() <= 0) {
            return new ShopSetupObjectiveState(
                    false,
                    9,
                    totalSteps,
                    "Place items inside a shop display",
                    "Where: Open the display's Shelf Manager, switch Mode to Shop, set a sellable item, set a price, and load stock. Regular mode and empty/out-of-stock shelves do not count.",
                    List.of(setupProgress("stocked shop-mode displays", displayStatus.configuredShopDisplays(), 1))
            );
        }
        return new ShopSetupObjectiveState(
                true,
                totalSteps,
                totalSteps,
                "Setup complete",
                "All requirements completed. Your store can now open for customers."
        );
    }

    private static boolean isShopSetupComplete(CompoundTag shop) {
        if (shop == null) {
            return false;
        }
        // The persisted flag is authoritative: the status tick refreshes it whenever the
        // shop chunks are observable. Re-deriving completeness here would force-load the
        // claim chunks on every sales/API call and mis-read shops whose chunks are unloaded.
        if (shop.contains(TAG_SETUP_COMPLETE)) {
            return shop.getBoolean(TAG_SETUP_COMPLETE);
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return evaluateShopSetupObjective(shop, server).complete();
        }
        return false;
    }

    private static ShopSetupObjectivePayload.RequirementProgress setupProgress(String itemName, int current, int needed) {
        return new ShopSetupObjectivePayload.RequirementProgress(itemName, current, needed);
    }

    private static int resolveSetupDeliveryPalletCount(CompoundTag shop, MinecraftServer server) {
        if (shop == null) {
            return 0;
        }
        Set<String> assigned = collectAssignedPalletRefSet(shop);
        if (assigned.isEmpty()) {
            return 0;
        }
        if (server == null) {
            // Without live world context, fall back to assigned records only.
            return assigned.size();
        }

        // Strict setup rule: only *assigned* delivery pallets count, and only
        // when their IDs resolve to a live pallet inside claimed shop land.
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
        if (liveLookup.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String palletId : assigned) {
            if (palletId == null || palletId.isBlank()) {
                continue;
            }
            if (liveLookup.containsKey(palletId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Setup requires at least one basket holder physically placed in claimed
     * shop land so customer shopping sessions can start.
     */
    private static int resolveSetupBasketHolderCount(CompoundTag shop, MinecraftServer server) {
        if (shop == null || server == null) {
            return 0;
        }
        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        if (claims.isEmpty()) {
            return 0;
        }
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(claim.getString(TAG_DIM)));
            if (level == null) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (level.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.SHOPPING_BASKET_HOLDER.get())) {
                            return 1;
                        }
                    }
                }
            }
        }
        return 0;
    }

    private static ListTag combineSetupPalletClaims(ListTag claims, ListTag stockrooms) {
        ListTag out = new ListTag();
        if (claims != null) {
            for (Tag tag : claims) {
                if (tag instanceof CompoundTag region) {
                    out.add(region.copy());
                }
            }
        }
        if (stockrooms != null) {
            for (Tag tag : stockrooms) {
                if (tag instanceof CompoundTag region) {
                    out.add(region.copy());
                }
            }
        }
        return out;
    }

    private static ListTag deliveryPalletSearchClaims(CompoundTag shop) {
        if (shop == null) {
            return new ListTag();
        }
        return combineSetupPalletClaims(
                shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND)
        );
    }

    private static boolean isInsideDeliveryPalletClaims(CompoundTag shop, String dimensionId, BlockPos pos) {
        if (shop == null || pos == null) {
            return false;
        }
        return isInsideClaims(deliveryPalletSearchClaims(shop), dimensionId, pos);
    }

    private static boolean hasValidSettlementAccount(CompoundTag shop) {
        if (shop == null || !shop.contains(TAG_SETTLEMENT_ACCOUNT_ID)) {
            return false;
        }
        try {
            UUID id = shop.getUUID(TAG_SETTLEMENT_ACCOUNT_ID);
            return id != null && !new UUID(0L, 0L).equals(id);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static SetupCashierStatus resolveSetupCashierStatus(CompoundTag shop, MinecraftServer server) {
        if (shop == null || server == null || !shop.contains(TAG_ID)) {
            return new SetupCashierStatus(0, 0);
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        UUID ownerId = resolveShopOwnerIdFromTag(shop);
        UUID shopId = shop.getUUID(TAG_ID);
        if (centralBank == null || ownerId == null || shopId == null) {
            return new SetupCashierStatus(0, 0);
        }
        List<CashierSummary> cashiers = collectCashiers(server, centralBank, ownerId, shopId);
        Set<UUID> liveCashierIds = new HashSet<>();
        for (CashierSummary cashier : cashiers) {
            if (cashier != null && cashier.cashierId() != null) {
                liveCashierIds.add(cashier.cashierId());
            }
        }
        return resolveSetupCashierStatusFromEvidence(
                liveCashierIds,
                collectPersistedLinkedCashierIds(shop)
        );
    }

    private static Set<UUID> collectPersistedLinkedCashierIds(CompoundTag shop) {
        Set<UUID> linkedCashierIds = new HashSet<>();
        if (shop == null) {
            return linkedCashierIds;
        }
        for (Tag tag : shop.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag entry) || !entry.contains(TAG_CASHIER_ID)) {
                continue;
            }
            try {
                linkedCashierIds.add(entry.getUUID(TAG_CASHIER_ID));
            } catch (RuntimeException ignored) {
                // Ignore malformed legacy entries instead of invalidating shop setup.
            }
        }
        return linkedCashierIds;
    }

    private static SetupCashierStatus resolveSetupCashierStatusFromEvidence(Set<UUID> liveCashierIds,
                                                                             Set<UUID> persistedLinkedCashierIds) {
        Set<UUID> live = liveCashierIds == null ? Set.of() : liveCashierIds;
        Set<UUID> linked = persistedLinkedCashierIds == null ? Set.of() : persistedLinkedCashierIds;
        Set<UUID> knownCashierIds = new HashSet<>();
        for (UUID cashierId : live) {
            if (cashierId != null) {
                knownCashierIds.add(cashierId);
            }
        }
        Set<UUID> linkedCashierIds = new HashSet<>();
        for (UUID cashierId : linked) {
            if (cashierId != null) {
                linkedCashierIds.add(cashierId);
                knownCashierIds.add(cashierId);
            }
        }
        return new SetupCashierStatus(knownCashierIds.size(), linkedCashierIds.size());
    }

    private static SetupSuppliesStatus resolveSetupSuppliesStatus(CompoundTag shop, MinecraftServer server) {
        if (shop == null || server == null || !shop.contains(TAG_ID)) {
            return new SetupSuppliesStatus(0, 0);
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        UUID ownerId = resolveShopOwnerIdFromTag(shop);
        UUID shopId = shop.getUUID(TAG_ID);
        if (centralBank == null || ownerId == null || shopId == null) {
            return new SetupSuppliesStatus(0, 0);
        }
        int shoppingBags = countShoppingBagsInStockroom(server, centralBank, ownerId, shopId);
        int displayItems = countSetupDisplayItemsInStockroom(server, centralBank, ownerId, shopId);
        int placedDisplays = collectShelvesForShop(server, shop).size();
        if (Integer.MAX_VALUE - displayItems < placedDisplays) {
            displayItems = Integer.MAX_VALUE;
        } else {
            displayItems += Math.max(0, placedDisplays);
        }
        return new SetupSuppliesStatus(shoppingBags, displayItems);
    }

    private static int countSetupDisplayItemsInStockroom(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return 0;
        }
        ListTag stockroomClaims = resolveStockroomClaimsForShop(centralBank, ownerId, shopId);
        if (stockroomClaims.isEmpty()) {
            return 0;
        }
        List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
        if (inventories.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItemStack template : setupDisplayItemTemplates()) {
            if (template == null || template.isEmpty()) {
                continue;
            }
            int count = countMatchingInInventories(inventories, template);
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += Math.max(0, count);
        }
        return Math.max(0, total);
    }

    private static List<ItemStack> setupDisplayItemTemplates() {
        return List.of(
                new ItemStack(ModBlocks.SHOP_SHELF.get().asItem()),
                new ItemStack(ModBlocks.TALL_WALL_SHELF.get().asItem()),
                new ItemStack(ModBlocks.MODULAR_WALL_DISPLAY.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_MODULAR_WALL_DISPLAY.get().asItem()),
                new ItemStack(ModBlocks.GLASS_COUNTER_DISPLAY.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY.get().asItem()),
                new ItemStack(ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_GLASS_COUNTER_DISPLAY_OPEN.get().asItem()),
                new ItemStack(ModBlocks.SHOP_SELLING_TABLE.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_SHOP_SELLING_TABLE.get().asItem()),
                new ItemStack(ModBlocks.SHOP_SELLING_TABLE_LARGE.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_SHOP_SELLING_TABLE_LARGE.get().asItem()),
                new ItemStack(ModBlocks.INVISIBLE_DISPLAY_SMALL.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_SMALL.get().asItem()),
                new ItemStack(ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_MEDIUM.get().asItem()),
                new ItemStack(ModBlocks.INVISIBLE_DISPLAY_LARGE.get().asItem()),
                new ItemStack(ModBlocks.CREATIVE_INVISIBLE_DISPLAY_LARGE.get().asItem())
        );
    }

    private static SetupDisplayStatus resolveSetupDisplayStatus(CompoundTag shop, MinecraftServer server) {
        if (shop == null || server == null) {
            return new SetupDisplayStatus(0, 0);
        }
        List<ShelfRef> shelves = collectShelvesForShop(server, shop);
        int configured = 0;
        for (ShelfRef ref : shelves) {
            if (ref != null && isConfiguredSetupShopDisplay(ref.shelf())) {
                configured++;
            }
        }
        return new SetupDisplayStatus(shelves.size(), configured);
    }

    private static boolean isConfiguredSetupShopDisplay(ShelfDisplayBlockEntity shelf) {
        if (shelf == null || !shelf.isShopMode()) {
            return false;
        }
        int slotCount = Math.max(0, shelf.getSlotCount());
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = shelf.getDisplayItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (shelf.getSlotPrice(slot) < 0L) {
                continue;
            }
            if (!shelf.isCreativeShelf() && shelf.getSlotStock(slot) <= 0) {
                continue;
            }
            return true;
        }
        return false;
    }

    public static ShopActionResult shopHoursLightingReport(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           UUID ownerId,
                                                           UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can manage opening hours and lighting.");
        }

        long serverEpochMillis = System.currentTimeMillis();
        ZoneId serverZone = resolveServerZone();
        ZonedDateTime serverNow = Instant.ofEpochMilli(serverEpochMillis).atZone(serverZone);
        String serverZoneDescription = ShopHoursTimeZone.describeZone(serverZone, serverEpochMillis);
        String currentDayKey = scheduleDayKey(serverNow.getDayOfWeek().getValue() - 1);
        ensureWeeklySchedule(shop);
        int openMinute = getShopOpenMinuteForDay(shop, currentDayKey);
        int closeMinute = getShopCloseMinuteForDay(shop, currentDayKey);
        int minuteOfDay = (serverNow.getHour() * 60) + serverNow.getMinute();
        boolean openNow = isShopOpenAtMinute(shop, minuteOfDay, currentDayKey);
        long untilChangeSeconds = secondsUntilNextShopStateChange(openMinute, closeMinute, minuteOfDay, openNow);
        boolean lightingEnabled = shop.getBoolean(TAG_LIGHTING_ENABLED);
        boolean excludeStockroom = shop.getBoolean(TAG_LIGHTING_EXCLUDE_STOCKROOM);
        boolean closedDelivererStockroomAccess = shop.getBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS);
        String mainMode = normalizeShopLightingMode(shop.getString(TAG_LIGHTING_MAIN_MODE));
        String stockroomMode = normalizeShopLightingMode(shop.getString(TAG_LIGHTING_STOCKROOM_MODE));
        int lightLevel = resolveShopLightingLevel(shop);
        int managedLights = shop.getList(TAG_LIGHTING_MANAGED_BLOCKS, Tag.TAG_COMPOUND).size();

        List<String> lines = new ArrayList<>();
        lines.add("Shop Hours & Lighting");
        // Keep legacy token keys for UI compatibility; values now represent server minute-of-day.
        lines.add("@shop_hours.open_tick=" + openMinute);
        lines.add("@shop_hours.close_tick=" + closeMinute);
        lines.add("@shop_hours.open_label=" + sanitizeTokenText(formatMinuteAmPm(openMinute)));
        lines.add("@shop_hours.close_label=" + sanitizeTokenText(formatMinuteAmPm(closeMinute)));
        lines.add("@shop_hours.current_day=" + currentDayKey);
        lines.add("@shop_hours.server_zone_id=" + sanitizeTokenText(serverZone.getId()));
        lines.add("@shop_hours.server_zone_label=" + sanitizeTokenText(serverZoneDescription));
        lines.add("@shop_hours.server_epoch_millis=" + serverEpochMillis);
        for (String dayKey : SHOP_SCHEDULE_DAY_KEYS) {
            int dayOpen = getShopOpenMinuteForDay(shop, dayKey);
            int dayClose = getShopCloseMinuteForDay(shop, dayKey);
            lines.add("@shop_hours.day=" + dayKey
                    + "|"
                    + friendlyScheduleDay(dayKey)
                    + "|"
                    + dayOpen
                    + "|"
                    + dayClose
                    + "|"
                    + sanitizeTokenText(formatMinuteAmPm(dayOpen))
                    + "|"
                    + sanitizeTokenText(formatMinuteAmPm(dayClose))
                    + "|"
                    + (dayKey.equals(currentDayKey) ? "1" : "0"));
        }
        lines.add("@shop_hours.is_open=" + (openNow ? "1" : "0"));
        lines.add("@shop_hours.closed_deliverer_stockroom_access=" + (closedDelivererStockroomAccess ? "1" : "0"));
        // Keep legacy key name and provide explicit seconds key for newer UIs.
        lines.add("@shop_hours.until_change_ticks=" + Math.max(-1L, untilChangeSeconds));
        lines.add("@shop_hours.until_change_seconds=" + Math.max(-1L, untilChangeSeconds));
        lines.add("@shop_lighting.enabled=" + (lightingEnabled ? "1" : "0"));
        lines.add("@shop_lighting.main_mode=" + mainMode);
        lines.add("@shop_lighting.stockroom_mode=" + stockroomMode);
        lines.add("@shop_lighting.exclude_stockroom=" + (excludeStockroom ? "1" : "0"));
        lines.add("@shop_lighting.level=" + lightLevel);
        lines.add("@shop_lighting.managed_blocks=" + managedLights);
        lines.add("Schedule timezone: " + serverZoneDescription);
        lines.add("Server time: " + friendlyScheduleDay(currentDayKey) + " "
                + formatMinuteAmPm(minuteOfDay));
        lines.add("Today (" + friendlyScheduleDay(currentDayKey) + "): "
                + formatMinuteAmPm(openMinute) + "  |  Close: " + formatMinuteAmPm(closeMinute));
        lines.add("Current status: " + (openNow ? "OPEN" : "CLOSED"));
        lines.add("Closed-hours delivery access (stockroom only): " + (closedDelivererStockroomAccess ? "Allowed" : "Denied"));
        if (untilChangeSeconds >= 0L) {
            lines.add("Next change in: " + formatDurationSeconds(untilChangeSeconds));
        } else {
            lines.add("Next change in: n/a (24h mode)");
        }
        lines.add("Automatic lighting: " + (lightingEnabled ? "Enabled" : "Disabled"));
        lines.add("Main plot mode: " + prettyLightingMode(mainMode));
        lines.add("Stockroom mode: " + prettyLightingMode(stockroomMode)
                + (excludeStockroom ? "" : " (ignored while include-stockroom is on)"));
        lines.add("Exclude stockroom from main lighting: " + (excludeStockroom ? "Yes" : "No"));
        lines.add("Light level: " + lightLevel + " (range " + SHOP_LIGHTING_MIN_LEVEL + "-" + SHOP_LIGHTING_MAX_LEVEL + ")");
        lines.add("Managed light blocks: " + managedLights);
        lines.add("Tip: Use times like '9:00 AM', '21:30', or '09:00'.");
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult setShopHours(MinecraftServer server,
                                                CentralBank centralBank,
                                                UUID ownerId,
                                                UUID shopId,
                                                String hoursPayload) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change opening hours.");
        }

        String[] parts = (hoursPayload == null ? "" : hoursPayload).split("\\|", -1);
        String dayKey = "";
        String openRaw;
        String closeRaw;
        if (parts.length >= 3) {
            String requestedDay = parts[0].trim();
            if ("ALL".equalsIgnoreCase(requestedDay)) {
                dayKey = "ALL";
            } else {
                dayKey = normalizeScheduleDay(requestedDay);
                if (dayKey.isBlank()) {
                    return new ShopActionResult(false, "Invalid day. Use Mon, Tue, Wed, Thu, Fri, Sat, or Sun.");
                }
            }
            openRaw = parts[1].trim();
            closeRaw = parts[2].trim();
        } else {
            openRaw = parts.length > 0 ? parts[0].trim() : "";
            closeRaw = parts.length > 1 ? parts[1].trim() : "";
        }
        int openMinute = parseClockToMinuteOfDay(openRaw);
        int closeMinute = parseClockToMinuteOfDay(closeRaw);
        if (openMinute < 0 || closeMinute < 0) {
            return new ShopActionResult(false,
                    "Invalid time format. Use values like '9:00 AM', '21:30', or '09:00'.");
        }

        ensureWeeklySchedule(shop);
        if ("ALL".equals(dayKey) || dayKey.isBlank()) {
            for (String key : SHOP_SCHEDULE_DAY_KEYS) {
                setShopDayHours(shop, key, openMinute, closeMinute);
            }
        } else {
            setShopDayHours(shop, dayKey, openMinute, closeMinute);
        }
        syncLegacyScheduleTagsToDay(shop, resolveCurrentScheduleDayKey(server));
        int minuteOfDay = resolveCurrentMinuteOfDay(server);
        shop.putBoolean(TAG_SCHEDULE_LAST_OPEN, isShopOpenAtMinute(shop, minuteOfDay, resolveCurrentScheduleDayKey(server)));
        shop.putLong(TAG_SCHEDULE_LAST_NOTIFY_TICK, -1L);
        shop.putLong(TAG_SCHEDULE_LAST_EJECT_TICK, -1L);
        saveShopTag(centralBank, shop);

        String targetLabel = "ALL".equals(dayKey) || dayKey.isBlank()
                ? "all days"
                : friendlyScheduleDay(dayKey);
        return new ShopActionResult(
                true,
                "Shop hours updated for " + targetLabel + ": "
                        + formatMinuteAmPm(openMinute) + " - " + formatMinuteAmPm(closeMinute)
                        + " (server time: " + resolveServerZone().getId() + ")."
        );
    }

    public static ShopActionResult setShopClosedDelivererStockroomAccess(MinecraftServer server,
                                                                          CentralBank centralBank,
                                                                          UUID ownerId,
                                                                          UUID shopId,
                                                                          String rawEnabled) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change closed-hours delivery access.");
        }

        boolean enabled = parseBooleanFlag(rawEnabled, shop.getBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS));
        shop.putBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS, enabled);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(
                true,
                enabled
                        ? "Closed-hours delivery access enabled: accepted couriers may enter stockroom only."
                        : "Closed-hours delivery access disabled: all non-staff are denied while closed."
        );
    }

    public static ShopActionResult setShopLightingEnabled(MinecraftServer server,
                                                          CentralBank centralBank,
                                                          UUID ownerId,
                                                          UUID shopId,
                                                          String rawEnabled) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change lighting automation.");
        }
        boolean enabled = parseBooleanFlag(rawEnabled, shop.getBoolean(TAG_LIGHTING_ENABLED));
        shop.putBoolean(TAG_LIGHTING_ENABLED, enabled);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Automatic lighting " + (enabled ? "enabled." : "disabled."));
    }

    public static ShopActionResult setShopMainLightingMode(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           UUID ownerId,
                                                           UUID shopId,
                                                           String rawMode) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change lighting automation.");
        }
        String mode = normalizeShopLightingMode(rawMode);
        shop.putString(TAG_LIGHTING_MAIN_MODE, mode);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Main plot lighting mode set to " + prettyLightingMode(mode) + ".");
    }

    public static ShopActionResult setShopStockroomLightingMode(MinecraftServer server,
                                                                CentralBank centralBank,
                                                                UUID ownerId,
                                                                UUID shopId,
                                                                String rawMode) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change lighting automation.");
        }
        String mode = normalizeShopLightingMode(rawMode);
        shop.putString(TAG_LIGHTING_STOCKROOM_MODE, mode);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Stockroom lighting mode set to " + prettyLightingMode(mode) + ".");
    }

    public static ShopActionResult setShopExcludeStockroomLighting(MinecraftServer server,
                                                                   CentralBank centralBank,
                                                                   UUID ownerId,
                                                                   UUID shopId,
                                                                   String rawEnabled) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change lighting automation.");
        }
        boolean exclude = parseBooleanFlag(rawEnabled, shop.getBoolean(TAG_LIGHTING_EXCLUDE_STOCKROOM));
        shop.putBoolean(TAG_LIGHTING_EXCLUDE_STOCKROOM, exclude);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(
                true,
                exclude
                        ? "Stockroom is now excluded from main lighting and uses stockroom mode."
                        : "Stockroom now follows the main lighting mode."
        );
    }

    public static ShopActionResult setShopLightingLevel(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        String rawLevel) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change lighting level.");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(rawLevel == null ? "" : rawLevel.trim());
        } catch (NumberFormatException ignored) {
            return new ShopActionResult(false, "Invalid light level. Enter a value from 1 to 15.");
        }
        int level = Mth.clamp(parsed, SHOP_LIGHTING_MIN_LEVEL, SHOP_LIGHTING_MAX_LEVEL);
        shop.putInt(TAG_LIGHTING_LEVEL, level);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Automatic lighting level set to " + level + ".");
    }

    private static void ensureWeeklySchedule(CompoundTag shop) {
        if (shop == null) {
            return;
        }
        ListTag days = shop.getList(TAG_SCHEDULE_DAYS, Tag.TAG_COMPOUND);
        boolean changed = false;
        int fallbackOpen = getShopOpenMinute(shop);
        int fallbackClose = getShopCloseMinute(shop);
        for (String dayKey : SHOP_SCHEDULE_DAY_KEYS) {
            if (getShopScheduleDayTag(shop, dayKey, false) == null) {
                CompoundTag day = new CompoundTag();
                day.putString(TAG_DAY, dayKey);
                day.putInt(TAG_SCHEDULE_DAY_OPEN_MINUTE, fallbackOpen);
                day.putInt(TAG_SCHEDULE_DAY_CLOSE_MINUTE, fallbackClose);
                days.add(day);
                changed = true;
            }
        }
        if (changed || !shop.contains(TAG_SCHEDULE_DAYS, Tag.TAG_LIST)) {
            shop.put(TAG_SCHEDULE_DAYS, days);
        }
    }

    private static CompoundTag getShopScheduleDayTag(CompoundTag shop, String rawDay, boolean create) {
        if (shop == null) {
            return null;
        }
        String dayKey = normalizeScheduleDay(rawDay);
        if (dayKey.isBlank()) {
            return null;
        }
        ListTag days = shop.getList(TAG_SCHEDULE_DAYS, Tag.TAG_COMPOUND);
        for (Tag tag : days) {
            if (!(tag instanceof CompoundTag dayTag)) {
                continue;
            }
            String existing = normalizeScheduleDay(dayTag.getString(TAG_DAY));
            if (dayKey.equals(existing)) {
                dayTag.putString(TAG_DAY, dayKey);
                return dayTag;
            }
        }
        if (!create) {
            return null;
        }
        CompoundTag created = new CompoundTag();
        created.putString(TAG_DAY, dayKey);
        created.putInt(TAG_SCHEDULE_DAY_OPEN_MINUTE, getShopOpenMinute(shop));
        created.putInt(TAG_SCHEDULE_DAY_CLOSE_MINUTE, getShopCloseMinute(shop));
        days.add(created);
        shop.put(TAG_SCHEDULE_DAYS, days);
        return created;
    }

    private static void initializeWeeklySchedule(CompoundTag shop, int openMinute, int closeMinute) {
        if (shop == null) {
            return;
        }
        ListTag days = new ListTag();
        int safeOpen = clampMinuteOfDay(openMinute);
        int safeClose = clampMinuteOfDay(closeMinute);
        for (String dayKey : SHOP_SCHEDULE_DAY_KEYS) {
            CompoundTag day = new CompoundTag();
            day.putString(TAG_DAY, dayKey);
            day.putInt(TAG_SCHEDULE_DAY_OPEN_MINUTE, safeOpen);
            day.putInt(TAG_SCHEDULE_DAY_CLOSE_MINUTE, safeClose);
            days.add(day);
        }
        shop.put(TAG_SCHEDULE_DAYS, days);
    }

    private static void setShopDayHours(CompoundTag shop, String rawDay, int openMinute, int closeMinute) {
        CompoundTag day = getShopScheduleDayTag(shop, rawDay, true);
        if (day == null) {
            return;
        }
        day.putInt(TAG_SCHEDULE_DAY_OPEN_MINUTE, clampMinuteOfDay(openMinute));
        day.putInt(TAG_SCHEDULE_DAY_CLOSE_MINUTE, clampMinuteOfDay(closeMinute));
    }

    private static int getShopOpenMinuteForDay(CompoundTag shop, String rawDay) {
        if (shop == null) {
            return clampMinuteOfDay(SHOP_DEFAULT_OPEN_MINUTE);
        }
        CompoundTag day = getShopScheduleDayTag(shop, rawDay, false);
        if (day == null || !day.contains(TAG_SCHEDULE_DAY_OPEN_MINUTE)) {
            return getShopOpenMinute(shop);
        }
        return decodeScheduleMinute(day.getInt(TAG_SCHEDULE_DAY_OPEN_MINUTE), SHOP_DEFAULT_OPEN_MINUTE);
    }

    private static int getShopCloseMinuteForDay(CompoundTag shop, String rawDay) {
        if (shop == null) {
            return clampMinuteOfDay(SHOP_DEFAULT_CLOSE_MINUTE);
        }
        CompoundTag day = getShopScheduleDayTag(shop, rawDay, false);
        if (day == null || !day.contains(TAG_SCHEDULE_DAY_CLOSE_MINUTE)) {
            return getShopCloseMinute(shop);
        }
        return decodeScheduleMinute(day.getInt(TAG_SCHEDULE_DAY_CLOSE_MINUTE), SHOP_DEFAULT_CLOSE_MINUTE);
    }

    private static void syncLegacyScheduleTagsToDay(CompoundTag shop, String rawDay) {
        if (shop == null) {
            return;
        }
        String dayKey = normalizeScheduleDay(rawDay);
        if (dayKey.isBlank()) {
            dayKey = SHOP_SCHEDULE_DAY_KEYS.get(0);
        }
        shop.putInt(TAG_SCHEDULE_OPEN_TICK, getShopOpenMinuteForDay(shop, dayKey));
        shop.putInt(TAG_SCHEDULE_CLOSE_TICK, getShopCloseMinuteForDay(shop, dayKey));
    }

    private static String resolveCurrentScheduleDayKey(MinecraftServer server) {
        return scheduleDayKey(ZonedDateTime.now(resolveServerZone()).getDayOfWeek().getValue() - 1);
    }

    private static String scheduleDayKey(int index) {
        return SHOP_SCHEDULE_DAY_KEYS.get(Math.max(0, Math.min(6, index)));
    }

    private static String normalizeScheduleDay(String rawDay) {
        if (rawDay == null || rawDay.isBlank()) {
            return "";
        }
        String value = rawDay.trim().toUpperCase(Locale.ROOT).replace(".", "");
        return switch (value) {
            case "MON", "MONDAY" -> "MON";
            case "TUE", "TUES", "TUESDAY" -> "TUE";
            case "WED", "WEDNESDAY" -> "WED";
            case "THU", "THUR", "THURS", "THURSDAY" -> "THU";
            case "FRI", "FRIDAY" -> "FRI";
            case "SAT", "SATURDAY" -> "SAT";
            case "SUN", "SUNDAY" -> "SUN";
            default -> "";
        };
    }

    private static String friendlyScheduleDay(String rawDay) {
        return switch (normalizeScheduleDay(rawDay)) {
            case "MON" -> "Mon";
            case "TUE" -> "Tue";
            case "WED" -> "Wed";
            case "THU" -> "Thu";
            case "FRI" -> "Fri";
            case "SAT" -> "Sat";
            case "SUN" -> "Sun";
            default -> "Day";
        };
    }

    private static int clampMinuteOfDay(int minute) {
        return (int) Math.floorMod(minute, 1440);
    }

    /**
     * Legacy schedule values were saved as day ticks (0..23999). New values are minute-of-day (0..1439).
     * This keeps old saves compatible and migrates behavior transparently.
     */
    private static int decodeScheduleMinute(int rawValue, int fallbackMinute) {
        if (rawValue < 0) {
            return clampMinuteOfDay(fallbackMinute);
        }
        if (rawValue <= 1439) {
            return clampMinuteOfDay(rawValue);
        }
        return legacyDayTickToMinute(rawValue);
    }

    private static int getShopOpenMinute(CompoundTag shop) {
        if (shop == null) {
            return clampMinuteOfDay(SHOP_DEFAULT_OPEN_MINUTE);
        }
        int raw = shop.contains(TAG_SCHEDULE_OPEN_TICK) ? shop.getInt(TAG_SCHEDULE_OPEN_TICK) : SHOP_DEFAULT_OPEN_MINUTE;
        return decodeScheduleMinute(raw, SHOP_DEFAULT_OPEN_MINUTE);
    }

    private static int getShopCloseMinute(CompoundTag shop) {
        if (shop == null) {
            return clampMinuteOfDay(SHOP_DEFAULT_CLOSE_MINUTE);
        }
        int raw = shop.contains(TAG_SCHEDULE_CLOSE_TICK) ? shop.getInt(TAG_SCHEDULE_CLOSE_TICK) : SHOP_DEFAULT_CLOSE_MINUTE;
        return decodeScheduleMinute(raw, SHOP_DEFAULT_CLOSE_MINUTE);
    }

    private static long resolveReferenceGameTime(MinecraftServer server) {
        if (server == null) {
            return 0L;
        }
        ServerLevel overworld = server.overworld();
        if (overworld != null) {
            return Math.max(0L, overworld.getGameTime());
        }
        var iterator = server.getAllLevels().iterator();
        if (iterator.hasNext()) {
            return Math.max(0L, iterator.next().getGameTime());
        }
        return 0L;
    }

    private static int resolveCurrentMinuteOfDay() {
        ZonedDateTime now = ZonedDateTime.now(resolveServerZone());
        return now.getHour() * 60 + now.getMinute();
    }

    private static int resolveCurrentMinuteOfDay(MinecraftServer server) {
        // Server clock should follow host system clock to match admin/server timezone settings.
        ZonedDateTime now = ZonedDateTime.now(resolveServerZone());
        return now.getHour() * 60 + now.getMinute();
    }

    private static long resolveCurrentEpochDay(MinecraftServer server) {
        return LocalDate.now(resolveServerZone()).toEpochDay();
    }

    private static ZoneId resolveServerZone() {
        return ZoneId.systemDefault();
    }

    private static int legacyDayTickToMinute(int tick) {
        int safeTick = (int) Math.floorMod(tick, 24000);
        return (int) Math.floorMod(Math.round(((safeTick + 6000.0D) % 24000.0D) * (1440.0D / 24000.0D)), 1440L);
    }

    private static boolean isShopOpenAtMinute(CompoundTag shop, int minuteOfDay) {
        return isShopOpenAtMinute(shop, minuteOfDay, resolveCurrentScheduleDayKey(null));
    }

    private static boolean isShopOpenAtMinute(CompoundTag shop, int minuteOfDay, String rawDay) {
        ensureWeeklySchedule(shop);
        int openMinute = getShopOpenMinuteForDay(shop, rawDay);
        int closeMinute = getShopCloseMinuteForDay(shop, rawDay);
        int minute = clampMinuteOfDay(minuteOfDay);
        if (openMinute == closeMinute) {
            // Equal values represent 24h operation.
            return true;
        }
        if (openMinute < closeMinute) {
            return minute >= openMinute && minute < closeMinute;
        }
        // Overnight window (e.g. 21:00 -> 05:00).
        return minute >= openMinute || minute < closeMinute;
    }

    private static long secondsUntilNextShopStateChange(int openMinute, int closeMinute, int minuteOfDay, boolean openNow) {
        int safeOpen = clampMinuteOfDay(openMinute);
        int safeClose = clampMinuteOfDay(closeMinute);
        int safeNow = clampMinuteOfDay(minuteOfDay);
        if (safeOpen == safeClose) {
            return -1L;
        }
        int target = openNow ? safeClose : safeOpen;
        int deltaMinutes = target - safeNow;
        if (deltaMinutes <= 0) {
            deltaMinutes += 1440;
        }
        return deltaMinutes * 60L;
    }

    private static long resolveUpcomingCloseEpochDay(int openMinute, int closeMinute, int minuteOfDay, long todayEpochDay) {
        int safeOpen = clampMinuteOfDay(openMinute);
        int safeClose = clampMinuteOfDay(closeMinute);
        int safeNow = clampMinuteOfDay(minuteOfDay);
        if (safeOpen == safeClose) {
            return todayEpochDay;
        }
        if (safeOpen < safeClose) {
            return todayEpochDay;
        }
        // Overnight window. During late-evening open phase, closing is tomorrow.
        return safeNow >= safeOpen ? (todayEpochDay + 1L) : todayEpochDay;
    }

    private static String normalizeShopLightingMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return LIGHT_MODE_OPEN_HOURS;
        }
        String mode = rawMode.trim().toUpperCase(Locale.ROOT);
        return switch (mode) {
            case LIGHT_MODE_ON -> LIGHT_MODE_ON;
            case LIGHT_MODE_OFF -> LIGHT_MODE_OFF;
            case LIGHT_MODE_INVERTED -> LIGHT_MODE_INVERTED;
            case LIGHT_MODE_OPEN_HOURS -> LIGHT_MODE_OPEN_HOURS;
            default -> LIGHT_MODE_OPEN_HOURS;
        };
    }

    private static String prettyLightingMode(String mode) {
        return switch (normalizeShopLightingMode(mode)) {
            case LIGHT_MODE_ON -> "Always On";
            case LIGHT_MODE_OFF -> "Always Off";
            case LIGHT_MODE_INVERTED -> "Inverted (Off while open, On while closed)";
            default -> "Opening Hours Regulated";
        };
    }

    private static boolean shouldLightsBeOn(boolean shopOpen, String mode) {
        return switch (normalizeShopLightingMode(mode)) {
            case LIGHT_MODE_ON -> true;
            case LIGHT_MODE_OFF -> false;
            case LIGHT_MODE_INVERTED -> !shopOpen;
            default -> shopOpen;
        };
    }

    private static String formatMinuteAmPm(int minuteOfDayRaw) {
        int minuteOfDay = clampMinuteOfDay(minuteOfDayRaw);
        int hour24 = minuteOfDay / 60;
        int minute = minuteOfDay % 60;
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String suffix = hour24 < 12 ? "AM" : "PM";
        return String.format(Locale.ROOT, "%d:%02d %s", hour12, minute, suffix);
    }

    private static int parseClockToMinuteOfDay(String raw) {
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('.', ':');
        String ampm = "";
        if (normalized.endsWith("AM")) {
            ampm = "AM";
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        } else if (normalized.endsWith("PM")) {
            ampm = "PM";
            normalized = normalized.substring(0, normalized.length() - 2).trim();
        }

        int hour;
        int minute = 0;
        try {
            if (normalized.contains(":")) {
                String[] hm = normalized.split(":", -1);
                if (hm.length != 2) {
                    return -1;
                }
                hour = Integer.parseInt(hm[0].trim());
                minute = Integer.parseInt(hm[1].trim());
            } else {
                hour = Integer.parseInt(normalized.trim());
            }
        } catch (NumberFormatException ignored) {
            return -1;
        }

        if (minute < 0 || minute > 59) {
            return -1;
        }
        if (!ampm.isBlank()) {
            if (hour < 1 || hour > 12) {
                return -1;
            }
            if ("AM".equals(ampm)) {
                hour = (hour == 12) ? 0 : hour;
            } else {
                hour = (hour == 12) ? 12 : hour + 12;
            }
        } else if (hour < 0 || hour > 23) {
            return -1;
        }

        int minuteOfDay = (hour * 60) + minute;
        return clampMinuteOfDay(minuteOfDay);
    }

    private static String formatDurationSeconds(long secondsRaw) {
        long seconds = Math.max(0L, secondsRaw);
        long totalMinutes = Math.max(1L, Math.round(seconds / 60.0D));
        long hours = totalMinutes / 60L;
        long minutes = totalMinutes % 60L;
        if (hours <= 0L) {
            return minutes + "m";
        }
        if (minutes <= 0L) {
            return hours + "h";
        }
        return hours + "h " + minutes + "m";
    }

    private static boolean parseBooleanFlag(String raw, boolean fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1", "true", "yes", "on", "enable", "enabled" -> true;
            case "0", "false", "no", "off", "disable", "disabled" -> false;
            default -> fallback;
        };
    }

    private static void tickShopStatusAndLighting(MinecraftServer server) {
        if (server == null) {
            return;
        }
        long gameTime = resolveReferenceGameTime(server);
        boolean statusTick = (gameTime % SHOP_STATUS_ENFORCE_INTERVAL_TICKS) == 0L;
        boolean lightingTick = (gameTime % SHOP_LIGHTING_REFRESH_INTERVAL_TICKS) == 0L;
        if (!statusTick && !lightingTick) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        int minuteOfDay = resolveCurrentMinuteOfDay(server);
        String currentDayKey = resolveCurrentScheduleDayKey(server);
        long epochDay = resolveCurrentEpochDay(server);
        long nowMillis = System.currentTimeMillis();
        Map<UUID, ShopSetupObjectivePayload> setupObjectiveByOwner = new HashMap<>();
        Map<UUID, Integer> setupObjectiveStepByOwner = new HashMap<>();
        Set<UUID> ownersSeen = new HashSet<>();

        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            // Decide observability BEFORE any evaluation or pruning: the setup scans read
            // block state and would synchronously load every claim chunk, so a shop whose
            // chunks are unloaded must keep its persisted state untouched instead of being
            // re-derived from a world that is only partially observable.
            boolean shopChunksUnloaded = anyShopChunksUnloaded(server, shop);
            boolean changed = false;
            if (statusTick && !shopChunksUnloaded) {
                boolean prunedLegacy = pruneLegacyCoordinateOrderPallets(shop) > 0;
                boolean prunedMissing = pruneAssignedPalletsMissingInWorld(server, shop) > 0;
                boolean prunedBindings = pruneOrderPalletBindingsOutsideAssigned(shop) > 0;
                changed |= prunedLegacy || prunedMissing || prunedBindings;
            }
            UUID shopId = shop.getUUID(TAG_ID);
            UUID ownerId = resolveShopOwnerIdFromTag(shop);
            boolean setupPreviouslyComplete = shop.getBoolean(TAG_SETUP_COMPLETE);
            ShopSetupObjectiveState setupState = shopChunksUnloaded
                    ? null
                    : evaluateShopSetupObjective(shop, server);
            boolean setupComplete = setupState == null ? setupPreviouslyComplete : setupState.complete();

            boolean setupChanged = setupPreviouslyComplete != setupComplete;
            if (setupState != null && (setupChanged || !shop.contains(TAG_SETUP_COMPLETE))) {
                // Persist the observed value even when it equals the default, so legacy
                // shop tags gain the flag and isShopSetupComplete stops re-evaluating.
                shop.putBoolean(TAG_SETUP_COMPLETE, setupComplete);
                changed = true;
            }
            if (setupChanged) {
                if (ownerId != null) {
                    ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                    if (owner != null) {
                        if (setupComplete) {
                            pushShopAlert(
                                    owner,
                                    "Shop Setup",
                                    "All setup requirements complete. You can now open your store.",
                                    DeliveryAlertPayload.AlertTone.SUCCESS,
                                    5000
                            );
                        } else {
                            pushShopAlert(
                                    owner,
                                    "Shop Setup",
                                    "Store setup is incomplete again. Complete objectives to reopen.",
                                    DeliveryAlertPayload.AlertTone.WARNING,
                                    5000
                            );
                        }
                    }
                }
            }

            if (statusTick && ownerId != null) {
                ownersSeen.add(ownerId);
                if (!setupComplete && setupState != null) {
                    int candidateStep = Math.max(1, setupState.step());
                    int currentStep = setupObjectiveStepByOwner.getOrDefault(ownerId, -1);
                    if (candidateStep >= currentStep) {
                        setupObjectiveByOwner.put(ownerId, new ShopSetupObjectivePayload(
                                true,
                                shop.getString(TAG_NAME),
                                setupState.step(),
                                setupState.totalSteps(),
                                setupState.title(),
                                setupState.detail(),
                                setupState.requirements()
                        ));
                        setupObjectiveStepByOwner.put(ownerId, candidateStep);
                    }
                }
            }

            ensureWeeklySchedule(shop);
            boolean scheduleOpen = isShopOpenAtMinute(shop, minuteOfDay, currentDayKey);
            boolean isOpen = scheduleOpen && setupComplete;
            boolean wasOpen = shop.getBoolean(TAG_SCHEDULE_LAST_OPEN);
            int openMinute = getShopOpenMinuteForDay(shop, currentDayKey);
            int closeMinute = getShopCloseMinuteForDay(shop, currentDayKey);

            if (statusTick) {
                if (shopId != null) {
                    for (ServerPlayer player : collectPlayersInShopClaims(server, shop)) {
                        if (player == null) {
                            continue;
                        }
                        rememberClosedShopEntryPoint(closedShopAccessKey(shopId, player.getUUID()), shop, player);
                    }
                    cleanupClosedShopAccessTracking(server, shop, shopId);
                }
                if (isOpen) {
                    long untilClose = secondsUntilNextShopStateChange(openMinute, closeMinute, minuteOfDay, true);
                    long notifyCycle = shop.contains(TAG_SCHEDULE_LAST_NOTIFY_TICK) ? shop.getLong(TAG_SCHEDULE_LAST_NOTIFY_TICK) : -1L;
                    long closeCycle = resolveUpcomingCloseEpochDay(openMinute, closeMinute, minuteOfDay, epochDay);
                    if (untilClose > 0L && untilClose <= SHOP_CLOSE_WARNING_LEAD_SECONDS && notifyCycle != closeCycle) {
                        String closeDuration = formatDurationSeconds(untilClose);
                        String message = "Store closes in " + closeDuration + ".";
                        for (ServerPlayer player : collectPlayersInShopClaims(server, shop)) {
                            if (player == null) {
                                continue;
                            }
                            player.sendSystemMessage(UbsTranslations.literal("Store closes in ")
                                    .withStyle(ChatFormatting.YELLOW)
                                    .append(Component.literal(closeDuration).withStyle(ChatFormatting.YELLOW))
                                    .append(Component.literal(".").withStyle(ChatFormatting.YELLOW)));
                            pushShopAlert(player, "Store Hours", message, DeliveryAlertPayload.AlertTone.WARNING, 4200);
                        }
                        shop.putLong(TAG_SCHEDULE_LAST_NOTIFY_TICK, closeCycle);
                        changed = true;
                    }
                    if (shopId != null) {
                        // Store is open: keep entry points but reset closed-hours warning timers.
                        clearClosedShopDeliveryWarningsForShop(shopId);
                    }
                }

                if (!isOpen) {
                    long lastEjectTick = shop.contains(TAG_SCHEDULE_LAST_EJECT_TICK) ? shop.getLong(TAG_SCHEDULE_LAST_EJECT_TICK) : -1L;
                    if (lastEjectTick < 0L || (nowMillis - lastEjectTick) >= 1000L) {
                        ListTag stockroomClaims = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
                        boolean allowClosedDelivererStockroom = setupComplete
                                && shop.getBoolean(TAG_CLOSED_DELIVERER_STOCKROOM_ACCESS);
                        for (ServerPlayer player : collectPlayersInShopClaims(server, shop)) {
                            if (player == null) {
                                continue;
                            }
                            UUID playerId = player.getUUID();
                            String accessKey = closedShopAccessKey(shopId, playerId);
                            rememberClosedShopEntryPoint(accessKey, shop, player);

                            if (shopId != null && canRemainInClosedShop(centralBank, shopId, playerId, server)) {
                                clearClosedShopAccessTracking(shopId, playerId);
                                continue;
                            }

                            boolean hasActiveDelivery = allowClosedDelivererStockroom
                                    && hasActiveAcceptedDeliveryOrder(shop, playerId, nowMillis);
                            boolean inStockroom = isInsideClaims(
                                    stockroomClaims,
                                    player.serverLevel().dimension().location().toString(),
                                    player.blockPosition()
                            );
                            if (hasActiveDelivery && inStockroom) {
                                long startedAt = CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.computeIfAbsent(accessKey, k -> {
                                    String warn = "Store is closed. Stockroom delivery access is active for 5 minutes.";
                                    player.sendSystemMessage(UbsTranslations.literal(warn).withStyle(ChatFormatting.YELLOW));
                                    pushShopAlert(player, "Delivery Access", warn, DeliveryAlertPayload.AlertTone.WARNING, 4600);
                                    return nowMillis;
                                });
                                if ((nowMillis - startedAt) < CLOSED_SHOP_DELIVERER_STOCKROOM_GRACE_MS) {
                                    continue;
                                }
                                if (teleportPlayerOutsideShop(player, shop, shopId)) {
                                    String msg = "Stockroom delivery access expired after 5 minutes. You were moved back to your entry point.";
                                    player.sendSystemMessage(UbsTranslations.literal(msg).withStyle(ChatFormatting.RED));
                                    pushShopAlert(player, "Delivery Access", msg, DeliveryAlertPayload.AlertTone.ERROR, 4200);
                                }
                                clearClosedShopAccessTracking(shopId, playerId);
                                continue;
                            }
                            if (hasActiveDelivery) {
                                long startedAt = CLOSED_SHOP_DELIVERER_LAND_WARNINGS.computeIfAbsent(accessKey, k -> {
                                    String warn = "Store is closed. You may stay on claimed land for 1 minute unless you move into stockroom delivery area.";
                                    player.sendSystemMessage(UbsTranslations.literal(warn).withStyle(ChatFormatting.YELLOW));
                                    pushShopAlert(player, "Delivery Access", warn, DeliveryAlertPayload.AlertTone.WARNING, 4600);
                                    return nowMillis;
                                });
                                if ((nowMillis - startedAt) < CLOSED_SHOP_DELIVERER_LAND_GRACE_MS) {
                                    continue;
                                }
                                if (teleportPlayerOutsideShop(player, shop, shopId)) {
                                    String msg = "Closed-hours land access expired after 1 minute. You were moved back to your entry point.";
                                    player.sendSystemMessage(UbsTranslations.literal(msg).withStyle(ChatFormatting.RED));
                                    pushShopAlert(player, "Delivery Access", msg, DeliveryAlertPayload.AlertTone.ERROR, 4200);
                                }
                                clearClosedShopAccessTracking(shopId, playerId);
                                continue;
                            }

                            String msg;
                            String title;
                            if (!setupComplete) {
                                msg = "Store setup is incomplete. Entry is restricted.";
                                title = "Store Locked";
                            } else if (allowClosedDelivererStockroom) {
                                msg = "Store is closed. Only accepted order couriers may access stockroom. Hours use "
                                        + resolveServerZone().getId() + ".";
                                title = "Store Closed";
                            } else {
                                msg = "Store is currently closed. Hours use " + resolveServerZone().getId() + ".";
                                title = "Store Closed";
                            }
                            if (teleportPlayerOutsideShop(player, shop, shopId)) {
                                player.sendSystemMessage(UbsTranslations.literal(msg).withStyle(ChatFormatting.RED));
                                pushShopAlert(player, title, msg, DeliveryAlertPayload.AlertTone.ERROR, 4200);
                            }
                            clearClosedShopAccessTracking(shopId, playerId);
                        }
                        cleanupClosedShopAccessTracking(server, shop, shopId);
                        shop.putLong(TAG_SCHEDULE_LAST_EJECT_TICK, nowMillis);
                        changed = true;
                    }
                }

                if (wasOpen != isOpen) {
                    shop.putBoolean(TAG_SCHEDULE_LAST_OPEN, isOpen);
                    if (!isOpen) {
                        shop.putLong(TAG_SCHEDULE_LAST_NOTIFY_TICK, -1L);
                    }
                    changed = true;
                }
            }

            if (lightingTick && !shopChunksUnloaded) {
                changed |= refreshShopLighting(server, shop, isOpen);
            }

            if (changed) {
                saveShopTag(centralBank, shop);
            }
        }

        if (statusTick) {
            Set<UUID> nextTracked = new HashSet<>();
            for (UUID ownerId : ownersSeen) {
                ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
                if (owner == null) {
                    continue;
                }
                ShopSetupObjectivePayload payload = setupObjectiveByOwner.get(ownerId);
                PacketDistributor.sendToPlayer(owner, payload == null ? ShopSetupObjectivePayload.inactive() : payload);
                nextTracked.add(ownerId);
            }
            for (UUID previous : new HashSet<>(SHOP_SETUP_OBJECTIVE_TRACKED)) {
                if (nextTracked.contains(previous)) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(previous);
                if (player != null) {
                    PacketDistributor.sendToPlayer(player, ShopSetupObjectivePayload.inactive());
                }
            }
            SHOP_SETUP_OBJECTIVE_TRACKED.clear();
            SHOP_SETUP_OBJECTIVE_TRACKED.addAll(nextTracked);
        }
    }

    private static boolean refreshShopLighting(MinecraftServer server, CompoundTag shop, boolean isOpen) {
        if (server == null || shop == null) {
            return false;
        }
        if (!shop.getBoolean(TAG_LIGHTING_ENABLED)) {
            return clearManagedLighting(server, shop);
        }
        String mainMode = normalizeShopLightingMode(shop.getString(TAG_LIGHTING_MAIN_MODE));
        String stockMode = normalizeShopLightingMode(shop.getString(TAG_LIGHTING_STOCKROOM_MODE));
        boolean excludeStockroom = shop.getBoolean(TAG_LIGHTING_EXCLUDE_STOCKROOM);
        int lightLevel = resolveShopLightingLevel(shop);
        boolean mainOn = shouldLightsBeOn(isOpen, mainMode);
        boolean stockroomOn = shouldLightsBeOn(isOpen, stockMode);
        boolean changed = false;
        if (!mainOn && (!excludeStockroom || !stockroomOn)) {
            // Fast path: everything should be dark, remove all previously managed light blocks.
            return clearManagedLighting(server, shop);
        }

        // Managed lighting only places/removes UBS-managed light blocks so manual lighting is untouched.
        changed |= applyManagedLightingForClaims(server, shop, shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), mainOn, false, lightLevel);
        if (excludeStockroom) {
            changed |= applyManagedLightingForClaims(server, shop, shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND), stockroomOn, true, lightLevel);
        }
        return changed;
    }

    private static boolean applyManagedLightingForClaims(MinecraftServer server,
                                                         CompoundTag shop,
                                                         ListTag claims,
                                                         boolean shouldBeOn,
                                                         boolean stockroomFlag,
                                                         int lightLevel) {
        if (server == null || shop == null || claims == null || claims.isEmpty()) {
            return false;
        }
        int clampedLevel = Mth.clamp(lightLevel, SHOP_LIGHTING_MIN_LEVEL, SHOP_LIGHTING_MAX_LEVEL);
        Set<String> managed = decodeManagedLightSet(shop.getList(TAG_LIGHTING_MANAGED_BLOCKS, Tag.TAG_COMPOUND));
        Set<String> otherScope = new HashSet<>();
        Set<String> scopeManaged = new HashSet<>();
        for (String key : managed) {
            ManagedLightRef ref = parseManagedLightRef(key);
            if (ref == null) {
                continue;
            }
            if (ref.stockroom() == stockroomFlag) {
                scopeManaged.add(key);
            } else {
                otherScope.add(key);
            }
        }
        boolean changed = false;
        if (!shouldBeOn) {
            // Turn this scope off by deleting only previously managed entries from this scope.
            for (String key : scopeManaged) {
                if (removeManagedLight(server, key)) {
                    changed = true;
                }
            }
            if (rewriteManagedLights(shop, otherScope)) {
                changed = true;
            }
            return changed;
        }

        // Full-coverage mode: attempt to light every empty block inside the claimed volume.
        Set<String> desired = new HashSet<>();
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(claim.getString(TAG_DIM)));
            if (level == null) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (state.isAir()) {
                            // Place invisible lights only in empty space so shop blocks/entities are untouched.
                            BlockState lightState = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, clampedLevel);
                            level.setBlock(pos, lightState, 3);
                            state = lightState;
                            changed = true;
                        }
                        if (state.is(Blocks.LIGHT)) {
                            if (state.hasProperty(LightBlock.LEVEL) && state.getValue(LightBlock.LEVEL) != clampedLevel) {
                                level.setBlock(pos, state.setValue(LightBlock.LEVEL, clampedLevel), 3);
                                changed = true;
                            }
                            desired.add(encodeManagedLightRef(level, pos, stockroomFlag));
                        }
                    }
                }
            }
        }
        // Remove stale managed lights in this scope that are no longer desired.
        for (String oldKey : scopeManaged) {
            if (!desired.contains(oldKey) && removeManagedLight(server, oldKey)) {
                changed = true;
            }
        }

        Set<String> keep = new HashSet<>(otherScope);
        keep.addAll(desired);
        if (rewriteManagedLights(shop, keep)) {
            changed = true;
        }
        return changed;
    }

    private static int resolveShopLightingLevel(CompoundTag shop) {
        if (shop == null) {
            return SHOP_LIGHTING_DEFAULT_LEVEL;
        }
        return Mth.clamp(shop.getInt(TAG_LIGHTING_LEVEL), SHOP_LIGHTING_MIN_LEVEL, SHOP_LIGHTING_MAX_LEVEL);
    }

    private static boolean clearManagedLighting(MinecraftServer server, CompoundTag shop) {
        if (server == null || shop == null) {
            return false;
        }
        ListTag list = shop.getList(TAG_LIGHTING_MANAGED_BLOCKS, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            String dim = normalizedDim(entry.getString(TAG_DIM));
            ServerLevel level = server.getLevel(serverLevelKey(dim));
            if (level == null) {
                continue;
            }
            BlockPos pos = new BlockPos(entry.getInt(TAG_X), entry.getInt(TAG_Y), entry.getInt(TAG_Z));
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.LIGHT)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                changed = true;
            }
        }
        shop.put(TAG_LIGHTING_MANAGED_BLOCKS, new ListTag());
        return true || changed;
    }

    private static boolean removeManagedLight(MinecraftServer server, String refRaw) {
        ManagedLightRef ref = parseManagedLightRef(refRaw);
        if (server == null || ref == null || ref.pos() == null) {
            return false;
        }
        ServerLevel level = server.getLevel(serverLevelKey(ref.dimensionId()));
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(ref.pos());
        if (!state.is(Blocks.LIGHT)) {
            return false;
        }
        level.setBlock(ref.pos(), Blocks.AIR.defaultBlockState(), 3);
        return true;
    }

    private static String encodeManagedLightRef(ServerLevel level, BlockPos pos, boolean stockroom) {
        if (level == null || pos == null) {
            return "";
        }
        return normalizedDim(level.dimension().location().toString())
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ()
                + ";" + (stockroom ? "1" : "0");
    }

    private static ManagedLightRef parseManagedLightRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.split(";", -1);
        if (parts.length < 4) {
            return null;
        }
        try {
            String dim = normalizedDim(parts[0]);
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            boolean stock = parts.length >= 5 && "1".equals(parts[4].trim());
            return new ManagedLightRef(dim, new BlockPos(x, y, z), stock);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Set<String> decodeManagedLightSet(ListTag list) {
        Set<String> out = new HashSet<>();
        if (list == null || list.isEmpty()) {
            return out;
        }
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            String dim = normalizedDim(entry.getString(TAG_DIM));
            int x = entry.getInt(TAG_X);
            int y = entry.getInt(TAG_Y);
            int z = entry.getInt(TAG_Z);
            boolean stock = entry.getBoolean(TAG_LIGHTING_STOCKROOM_FLAG);
            out.add(dim + ";" + x + ";" + y + ";" + z + ";" + (stock ? "1" : "0"));
        }
        return out;
    }

    private static boolean rewriteManagedLights(CompoundTag shop, Set<String> refs) {
        if (shop == null) {
            return false;
        }
        ListTag next = new ListTag();
        if (refs != null) {
            List<String> sorted = new ArrayList<>(refs);
            sorted.sort(String.CASE_INSENSITIVE_ORDER);
            for (String raw : sorted) {
                ManagedLightRef ref = parseManagedLightRef(raw);
                if (ref == null || ref.pos() == null) {
                    continue;
                }
                CompoundTag entry = new CompoundTag();
                entry.putString(TAG_DIM, normalizedDim(ref.dimensionId()));
                entry.putInt(TAG_X, ref.pos().getX());
                entry.putInt(TAG_Y, ref.pos().getY());
                entry.putInt(TAG_Z, ref.pos().getZ());
                entry.putBoolean(TAG_LIGHTING_STOCKROOM_FLAG, ref.stockroom());
                next.add(entry);
            }
        }
        String before = shop.getList(TAG_LIGHTING_MANAGED_BLOCKS, Tag.TAG_COMPOUND).toString();
        String after = next.toString();
        shop.put(TAG_LIGHTING_MANAGED_BLOCKS, next);
        return !Objects.equals(before, after);
    }

    private static int alignToStep(int value, int step) {
        if (step <= 1) {
            return value;
        }
        int mod = Math.floorMod(value, step);
        return mod == 0 ? value : (value + (step - mod));
    }

    private static List<ServerPlayer> collectPlayersInShopClaims(MinecraftServer server, CompoundTag shop) {
        List<ServerPlayer> out = new ArrayList<>();
        if (server == null || shop == null || server.getPlayerList() == null) {
            return out;
        }
        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag stockrooms = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        if (claims.isEmpty() && stockrooms.isEmpty()) {
            return out;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null || player.level() == null) {
                continue;
            }
            String dim = player.serverLevel().dimension().location().toString();
            BlockPos pos = player.blockPosition();
            if (isInsideClaims(claims, dim, pos) || isInsideClaims(stockrooms, dim, pos)) {
                out.add(player);
            }
        }
        return out;
    }

    private static boolean teleportPlayerOutsideShop(ServerPlayer player, CompoundTag shop, UUID shopId) {
        if (player == null || shop == null) {
            return false;
        }
        ListTag claims = combineSetupPalletClaims(
                shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND)
        );
        if (shopId != null) {
            String accessKey = closedShopAccessKey(shopId, player.getUUID());
            ClosedShopEntryPoint entry = CLOSED_SHOP_ENTRY_POINTS.get(accessKey);
            if (entry != null) {
                BlockPos entryPos = BlockPos.containing(entry.x(), entry.y(), entry.z());
                ServerLevel entryLevel = player.server == null
                        ? null
                        : player.server.getLevel(serverLevelKey(entry.dimensionId()));
                if (entryLevel != null && !isInsideClaims(claims, entry.dimensionId(), entryPos)) {
                    player.teleportTo(entryLevel, entry.x(), entry.y(), entry.z(), player.getYRot(), player.getXRot());
                    return true;
                }
            }
        }
        ServerLevel level = player.serverLevel();
        String dim = normalizedDim(level.dimension().location().toString());
        BlockPos playerPos = player.blockPosition();
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);
            if (playerPos.getX() < minX || playerPos.getX() > maxX
                    || playerPos.getY() < minY || playerPos.getY() > maxY
                    || playerPos.getZ() < minZ || playerPos.getZ() > maxZ) {
                continue;
            }

            int distWest = Math.abs(playerPos.getX() - minX);
            int distEast = Math.abs(maxX - playerPos.getX());
            int distNorth = Math.abs(playerPos.getZ() - minZ);
            int distSouth = Math.abs(maxZ - playerPos.getZ());
            int min = Math.min(Math.min(distWest, distEast), Math.min(distNorth, distSouth));
            int targetX = playerPos.getX();
            int targetZ = playerPos.getZ();
            if (min == distWest) {
                targetX = minX - 1;
            } else if (min == distEast) {
                targetX = maxX + 1;
            } else if (min == distNorth) {
                targetZ = minZ - 1;
            } else {
                targetZ = maxZ + 1;
            }
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, playerPos.getY(), targetZ));
            double tx = targetX + 0.5D;
            double ty = Math.max(top.getY() + 1.0D, minY + 1.0D);
            double tz = targetZ + 0.5D;
            player.teleportTo(level, tx, ty, tz, player.getYRot(), player.getXRot());
            return true;
        }
        return false;
    }

    private static String closedShopAccessKey(UUID shopId, UUID playerId) {
        if (shopId == null || playerId == null) {
            return "";
        }
        return shopId + "|" + playerId;
    }

    private static void clearClosedShopAccessTracking(UUID shopId, UUID playerId) {
        String key = closedShopAccessKey(shopId, playerId);
        if (key.isBlank()) {
            return;
        }
        CLOSED_SHOP_ENTRY_POINTS.remove(key);
        CLOSED_SHOP_DELIVERER_LAND_WARNINGS.remove(key);
        CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.remove(key);
    }

    private static void clearClosedShopAccessTrackingForShop(UUID shopId) {
        if (shopId == null) {
            return;
        }
        String prefix = shopId + "|";
        for (String key : new HashSet<>(CLOSED_SHOP_ENTRY_POINTS.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                CLOSED_SHOP_ENTRY_POINTS.remove(key);
            }
        }
        for (String key : new HashSet<>(CLOSED_SHOP_DELIVERER_LAND_WARNINGS.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                CLOSED_SHOP_DELIVERER_LAND_WARNINGS.remove(key);
            }
        }
        for (String key : new HashSet<>(CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.remove(key);
            }
        }
    }

    private static void clearClosedShopDeliveryWarningsForShop(UUID shopId) {
        if (shopId == null) {
            return;
        }
        String prefix = shopId + "|";
        for (String key : new HashSet<>(CLOSED_SHOP_DELIVERER_LAND_WARNINGS.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                CLOSED_SHOP_DELIVERER_LAND_WARNINGS.remove(key);
            }
        }
        for (String key : new HashSet<>(CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.keySet())) {
            if (key != null && key.startsWith(prefix)) {
                CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.remove(key);
            }
        }
    }

    private static void rememberClosedShopEntryPoint(String accessKey, CompoundTag shop, ServerPlayer player) {
        if (accessKey == null || accessKey.isBlank() || shop == null || player == null) {
            return;
        }
        if (CLOSED_SHOP_ENTRY_POINTS.containsKey(accessKey)) {
            return;
        }
        String dim = normalizedDim(player.serverLevel().dimension().location().toString());
        ListTag regions = combineSetupPalletClaims(
                shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND)
        );

        double entryX = player.getX();
        double entryY = player.getY();
        double entryZ = player.getZ();
        BlockPos previous = BlockPos.containing(player.xOld, player.yOld, player.zOld);
        if (!isInsideClaims(regions, dim, previous)) {
            entryX = player.xOld;
            entryY = player.yOld;
            entryZ = player.zOld;
        }
        CLOSED_SHOP_ENTRY_POINTS.put(accessKey, new ClosedShopEntryPoint(dim, entryX, entryY, entryZ));
    }

    /**
     * Removes stale closed-shop tracking entries when players leave claimed land
     * or disconnect, keeping access warnings and entry points in sync.
     */
    private static void cleanupClosedShopAccessTracking(MinecraftServer server, CompoundTag shop, UUID shopId) {
        if (server == null || shop == null || shopId == null || server.getPlayerList() == null) {
            return;
        }
        String prefix = shopId + "|";
        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag stockrooms = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);

        for (String key : new HashSet<>(CLOSED_SHOP_ENTRY_POINTS.keySet())) {
            if (key == null || !key.startsWith(prefix)) {
                continue;
            }
            String playerRaw = key.substring(prefix.length());
            UUID playerId = parseOptionalUuid(playerRaw);
            if (playerId == null) {
                CLOSED_SHOP_ENTRY_POINTS.remove(key);
                CLOSED_SHOP_DELIVERER_LAND_WARNINGS.remove(key);
                CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.remove(key);
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            boolean inside = player != null && (
                    isInsideClaims(claims, player.serverLevel().dimension().location().toString(), player.blockPosition())
                            || isInsideClaims(stockrooms, player.serverLevel().dimension().location().toString(), player.blockPosition())
            );
            if (!inside) {
                CLOSED_SHOP_ENTRY_POINTS.remove(key);
                CLOSED_SHOP_DELIVERER_LAND_WARNINGS.remove(key);
                CLOSED_SHOP_DELIVERER_STOCKROOM_WARNINGS.remove(key);
            }
        }
    }

    private static boolean hasActiveAcceptedDeliveryOrder(CompoundTag shop, UUID courierId, long nowMillis) {
        if (shop == null || courierId == null) {
            return false;
        }
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        if (orders.isEmpty()) {
            return false;
        }
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            if (!ORDER_STATUS_ACCEPTED.equals(normalizeOrderStatus(order.getString(TAG_ORDER_STATUS)))) {
                continue;
            }
            if (!order.contains(TAG_ORDER_ACCEPTED_BY)) {
                continue;
            }
            UUID acceptedBy = order.getUUID(TAG_ORDER_ACCEPTED_BY);
            if (!courierId.equals(acceptedBy)) {
                continue;
            }
            long expiresAt = order.getLong(TAG_ORDER_EXPIRES_AT);
            if (expiresAt <= 0L || expiresAt >= nowMillis) {
                return true;
            }
        }
        return false;
    }

    public static ShopActionResult permissionsReport(MinecraftServer server,
                                                     CentralBank centralBank,
                                                     UUID ownerId,
                                                     UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }

        UUID actualOwnerId = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : ownerId;
        String actorRole = normalizeShopPermissionRole(resolveShopRole(shop, ownerId));
        if (actorRole.isBlank()) {
            actorRole = "-";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Shop Plot Permissions");
        lines.add("Shop: " + sanitizeTokenText(shop.getString(TAG_NAME)) + " (" + shortUuid(shop.getUUID(TAG_ID)) + ")");
        lines.add("Your role: " + actorRole);
        lines.add("@shop_permissions.roles=" + String.join(",", SHOP_PERMISSION_ROLES));
        lines.add("@shop_permissions.owner=" + actualOwnerId + "|" + sanitizeTokenText(resolvePlayerLabel(server, actualOwnerId))
                + "|" + SHOP_ROLE_OWNER);
        // Role capability legend is printed here so operators can see exactly what each
        // delegated role can do before assigning or removing permissions.
        lines.add("Role Capabilities:");
        lines.add("@shop_role_capability=" + SHOP_ROLE_OWNER + "|Full control: settings, finance, permissions, claims, employees, inventory, orders.");
        lines.add("OWNER: Full control (settings, finance, permissions, claims, employees, inventory, orders).");
        lines.add("@shop_role_capability=" + SHOP_ROLE_MANAGER + "|Manage operations, employees, POS links, inventory, claims, and order flow.");
        lines.add("MANAGER: Manage shop operations, employees, POS links, inventory, claims, and order flow.");
        lines.add("@shop_role_capability=" + SHOP_ROLE_BUILDER + "|Build/stock mode: claim tools, shelf inventory, stockroom operations.");
        lines.add("BUILDER: Build-mode operations only (claim tools, shelf inventory/stockroom operations).");
        lines.add("@shop_role_capability=" + SHOP_ROLE_STAFF + "|Staff operations: dashboards, inventory/reports, stockroom locate, and non-admin shop workflows.");
        lines.add("STAFF: Shop-floor operations (dashboards, inventory/reports, stockroom locate, and non-admin workflows).");

        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        List<ShopPermissionEntry> entries = decodeShopPermissions(shop);
        lines.add("@shop_permissions.count=" + entries.size());

        LinkedHashMap<String, String> headerDescriptions = new LinkedHashMap<>();
        headerDescriptions.put(SHOP_ROLE_OWNER, "Owner account with full control.");
        headerDescriptions.put(SHOP_ROLE_MANAGER, "Managers can run operations and staff/POS flows.");
        headerDescriptions.put(SHOP_ROLE_BUILDER, "Builders can manage shelves/stock and claim-tool workflows.");
        headerDescriptions.put(SHOP_ROLE_STAFF, "Staff can run day-to-day store operations.");
        headerDescriptions.put("GUESTS", "Players currently inside claimed shop land with no assigned role.");

        LinkedHashMap<String, List<PermissionDisplayEntry>> grouped = new LinkedHashMap<>();
        for (String key : headerDescriptions.keySet()) {
            grouped.put(key, new ArrayList<>());
        }

        ServerPlayer ownerOnline = server == null ? null : server.getPlayerList().getPlayer(actualOwnerId);
        grouped.get(SHOP_ROLE_OWNER).add(new PermissionDisplayEntry(
                SHOP_ROLE_OWNER,
                actualOwnerId,
                resolvePlayerLabel(server, actualOwnerId),
                SHOP_ROLE_OWNER,
                ownerOnline != null,
                true,
                false,
                0L,
                ownerOnline == null ? "-" : (ownerOnline.level().dimension().location() + " " + ownerOnline.blockPosition())
        ));

        entries.sort(Comparator
                .comparing(ShopPermissionEntry::role, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> resolvePlayerLabel(server, entry.playerId()), String.CASE_INSENSITIVE_ORDER));
        for (ShopPermissionEntry entry : entries) {
            if (entry == null || entry.playerId() == null) {
                continue;
            }
            String role = normalizeShopPermissionRole(entry.role());
            if (role.isBlank() || SHOP_ROLE_OWNER.equals(role)) {
                continue;
            }
            ServerPlayer online = server == null ? null : server.getPlayerList().getPlayer(entry.playerId());
            grouped.computeIfAbsent(role, ignored -> new ArrayList<>()).add(new PermissionDisplayEntry(
                    role,
                    entry.playerId(),
                    resolvePlayerLabel(server, entry.playerId()),
                    role,
                    online != null,
                    false,
                    false,
                    Math.max(0L, entry.grantedAtMillis()),
                    online == null ? "-" : (online.level().dimension().location() + " " + online.blockPosition())
            ));
        }

        Set<UUID> assignedIds = new HashSet<>();
        assignedIds.add(actualOwnerId);
        for (ShopPermissionEntry entry : entries) {
            if (entry != null && entry.playerId() != null) {
                assignedIds.add(entry.playerId());
            }
        }
        if (server != null) {
            List<ServerPlayer> onlinePlayers = new ArrayList<>(server.getPlayerList().getPlayers());
            onlinePlayers.sort(Comparator.comparing(player -> player.getName().getString(), String.CASE_INSENSITIVE_ORDER));
            for (ServerPlayer online : onlinePlayers) {
                if (online == null || assignedIds.contains(online.getUUID())) {
                    continue;
                }
                String dim = online.level().dimension().location().toString();
                if (!isInsideClaims(claims, dim, online.blockPosition())) {
                    continue;
                }
                grouped.get("GUESTS").add(new PermissionDisplayEntry(
                        "GUESTS",
                        online.getUUID(),
                        resolvePlayerLabel(server, online.getUUID()),
                        "",
                        true,
                        false,
                        true,
                        0L,
                        dim + " " + online.blockPosition()
                ));
            }
        }

        int headerIndex = 1;
        int memberIndex = 1;
        for (Map.Entry<String, String> header : headerDescriptions.entrySet()) {
            String roleKey = header.getKey();
            String roleLabel = "GUESTS".equals(roleKey) ? "GUESTS" : roleKey;
            List<PermissionDisplayEntry> roleEntries = grouped.getOrDefault(roleKey, List.of());
            lines.add("@shop_permission_header="
                    + headerIndex
                    + "|" + roleKey
                    + "|" + sanitizeTokenText(roleLabel)
                    + "|" + sanitizeTokenText(header.getValue())
                    + "|" + roleEntries.size());
            lines.add(roleLabel + " (" + roleEntries.size() + ")");
            for (PermissionDisplayEntry display : roleEntries) {
                lines.add("@shop_permission_member="
                        + memberIndex
                        + "|" + roleKey
                        + "|" + display.playerId()
                        + "|" + sanitizeTokenText(display.playerName())
                        + "|" + sanitizeTokenText(display.assignedRole() == null || display.assignedRole().isBlank() ? "-" : display.assignedRole())
                        + "|" + (display.online() ? "1" : "0")
                        + "|" + (display.owner() ? "1" : "0")
                        + "|" + (display.guest() ? "1" : "0")
                        + "|" + Math.max(0L, display.grantedAtMillis())
                        + "|" + sanitizeTokenText(display.location() == null ? "-" : display.location()));
                lines.add(" - " + display.playerName() + " | " + display.playerId()
                        + " | " + (display.online() ? "ONLINE" : "OFFLINE")
                        + " | " + (display.assignedRole() == null || display.assignedRole().isBlank() ? "GUEST" : display.assignedRole()));
                memberIndex++;
            }
            if (roleEntries.isEmpty()) {
                lines.add(" - none");
            }
            headerIndex++;
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult setPermissionRole(MinecraftServer server,
                                                     CentralBank centralBank,
                                                     UUID ownerId,
                                                     UUID shopId,
                                                     String playerAndRole) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change delegated permissions.");
        }
        UUID actualOwnerId = resolveShopOwnerIdFromTag(shop);
        if (actualOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }

        String[] parts = (playerAndRole == null ? "" : playerAndRole.trim()).split("\\|", -1);
        String playerToken = parts.length > 0 ? parts[0].trim() : "";
        String roleToken = parts.length > 1 ? parts[1].trim() : "";
        if (playerToken.isBlank()) {
            return new ShopActionResult(false, "Enter a player name or UUID.");
        }
        String role = normalizeShopPermissionRole(roleToken);
        if (role.isBlank() || SHOP_ROLE_OWNER.equals(role)) {
            return new ShopActionResult(false, "Invalid role. Use MANAGER, BUILDER, or STAFF.");
        }

        UUID targetId = resolvePlayerSelection(server, playerToken);
        if (targetId == null) {
            return new ShopActionResult(false, "Player must be online or use a valid UUID.");
        }
        // Never create delegated rows for the real owner account.
        if (actualOwnerId.equals(targetId)) {
            return new ShopActionResult(false, "Owner already has full permissions.");
        }

        upsertShopPermission(shop, targetId, role);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true,
                "Permission updated: " + resolvePlayerLabel(server, targetId) + " -> " + role + ".");
    }

    public static ShopActionResult removePermissionRole(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        String playerSelection) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_OWNER)) {
            return new ShopActionResult(false, "Only the shop owner can change delegated permissions.");
        }
        UUID actualOwnerId = resolveShopOwnerIdFromTag(shop);
        if (actualOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }
        String token = playerSelection == null ? "" : playerSelection.trim();
        if (token.isBlank()) {
            return new ShopActionResult(false, "Enter a player name or UUID.");
        }

        UUID targetId = resolvePlayerSelection(server, token);
        if (targetId == null) {
            return new ShopActionResult(false, "Player must be online or use a valid UUID.");
        }
        // Never allow delegated table edits to remove owner authority.
        if (actualOwnerId.equals(targetId)) {
            return new ShopActionResult(false, "Owner permissions cannot be removed.");
        }
        if (!removeShopPermission(shop, targetId)) {
            return new ShopActionResult(false, "No delegated permission found for that player.");
        }

        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Removed delegated plot permissions for " + resolvePlayerLabel(server, targetId) + ".");
    }

    /**
     * Admin-only helper that forces a specific shop to a given level (clamped 1..100).
     * Revenue is intentionally left unchanged; only level milestone state is rewritten.
     */
    public static ShopActionResult adminSetShopLevel(CentralBank centralBank, UUID shopId, int requestedLevel) {
        if (centralBank == null || shopId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "Shop not found: " + shopId + ".");
        }

        int before = Mth.clamp(shop.getInt(TAG_LEVEL), 1, 100);
        int target = Mth.clamp(requestedLevel, 1, 100);
        if (before == target) {
            return new ShopActionResult(true, "Shop " + shop.getString(TAG_NAME) + " is already level " + target + ".");
        }

        shop.putInt(TAG_LEVEL, target);
        long nextTarget = target >= 100 ? Long.MAX_VALUE : requiredRevenueForLevel(target + 1);
        shop.putLong(TAG_NEXT_TARGET_DOLLARS, Math.max(1L, nextTarget));
        saveShopTag(centralBank, shop);
        return new ShopActionResult(
                true,
                "Shop " + shop.getString(TAG_NAME) + " (" + shortUuid(shopId) + ") level set: " + before + " -> " + target + "."
        );
    }

    /**
     * Admin-only helper that adjusts a shop level by a signed delta.
     */
    public static ShopActionResult adminAdjustShopLevel(CentralBank centralBank, UUID shopId, int deltaLevels) {
        if (centralBank == null || shopId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        if (deltaLevels == 0) {
            return new ShopActionResult(true, "No level change requested.");
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "Shop not found: " + shopId + ".");
        }

        int current = Mth.clamp(shop.getInt(TAG_LEVEL), 1, 100);
        long rawTarget = (long) current + deltaLevels;
        int target = (int) Math.max(1L, Math.min(100L, rawTarget));
        if (target == current) {
            return new ShopActionResult(false, "Shop " + shop.getString(TAG_NAME) + " is already at level boundary " + current + ".");
        }
        return adminSetShopLevel(centralBank, shopId, target);
    }

    public static long claimCapacityForLevel(int level) {
        int safeLevel = effectiveLevelForScaling(level, Config.SHOP_LEVEL_SCALE_CLAIM_CAPACITY.get());
        long base = Math.max(1, BASE_CLAIM_BLOCKS);
        long perLevel = Math.max(0, CLAIM_BLOCKS_PER_LEVEL);
        long cap = Math.max(base, base + (long) (safeLevel - 1) * perLevel);
        return Math.min(Math.max(1L, cap), Math.max(1L, MAX_CLAIM_BLOCKS));
    }

    public static long stockroomCapacityForLevel(int level) {
        int safeLevel = effectiveLevelForScaling(level, Config.SHOP_LEVEL_SCALE_STOCKROOM_CAPACITY.get());
        long base = Math.max(1L, Config.SHOP_STOCKROOM_BASE_CAPACITY_BLOCKS.get());
        long perLevel = Math.max(0L, Config.SHOP_STOCKROOM_CAPACITY_PER_LEVEL_BLOCKS.get());
        long max = Math.max(1L, Config.SHOP_STOCKROOM_MAX_CAPACITY_BLOCKS.get());
        long cap = Math.max(base, safeAdd(base, (long) (safeLevel - 1) * perLevel));
        return Math.min(Math.max(1L, cap), max);
    }

    public static int maxDisplayBlocksForLevel(int level) {
        int safeLevel = effectiveLevelForScaling(level, Config.SHOP_LEVEL_SCALE_DISPLAY_LIMIT.get());
        int base = Math.max(1, Config.SHOP_DISPLAY_BASE_LIMIT.get());
        int perLevel = Math.max(0, Config.SHOP_DISPLAY_LIMIT_PER_LEVEL.get());
        int max = Math.max(1, Config.SHOP_DISPLAY_MAX_LIMIT.get());
        long cap = (long) base + (long) (safeLevel - 1) * perLevel;
        return (int) Math.max(1L, Math.min((long) max, Math.max((long) base, cap)));
    }

    public static int maxCashierSpawnEggsForLevel(int level) {
        int safeLevel = effectiveLevelForScaling(level, Config.SHOP_LEVEL_SCALE_CASHIER_LIMIT.get());
        int cap = 2 + ((safeLevel - 1) * 2);
        int configuredMax = Math.max(1, Config.SHOP_MAX_CASHIER_SPAWN_EGGS_PER_SHOP.get());
        return Math.min(configuredMax, Math.max(1, cap));
    }

    private static int effectiveLevelForScaling(int level, boolean scaleEnabled) {
        // One place to enforce global leveling disable + per-feature scaling toggles.
        if (!Config.SHOP_LEVELING_ENABLED.get() || !scaleEnabled) {
            return 1;
        }
        return Math.max(1, level);
    }

    public static int claimToolTimeoutTicks() {
        return CLAIM_TOOL_TIMEOUT_TICKS;
    }

    public static ShopActionResult claimAroundPosition(CentralBank centralBank,
                                                       ServerPlayer player,
                                                       String dimensionId,
                                                       int x,
                                                       int y,
                                                       int z) {
        return claimAroundPosition(centralBank, player, null, dimensionId, x, y, z);
    }

    public static ShopActionResult claimAroundPosition(CentralBank centralBank,
                                                       ServerPlayer player,
                                                       UUID shopId,
                                                       String dimensionId,
                                                       int x,
                                                       int y,
                                                       int z) {
        if (centralBank == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag targetShop = resolveShopTag(centralBank, player.getUUID(), shopId);
        if (targetShop == null) {
            return new ShopActionResult(false, "Create a shop first.");
        }
        int level = effectiveLevelForScaling(targetShop.getInt(TAG_LEVEL), Config.SHOP_LEVEL_SCALE_CLAIM_CAPACITY.get());
        int radius = Math.min(MAX_CLAIM_RADIUS, BASE_CLAIM_RADIUS + ((level - 1) * CLAIM_RADIUS_PER_LEVEL));
        BlockPos first = new BlockPos(x - radius, y + CLAIM_MIN_Y_OFFSET, z - radius);
        BlockPos second = new BlockPos(x + radius, y + CLAIM_MAX_Y_OFFSET, z + radius);
        return addClaimRegion(
                centralBank,
                player.getUUID(),
                targetShop.contains(TAG_ID) ? targetShop.getUUID(TAG_ID) : null,
                dimensionId,
                first,
                second
        );
    }

    public static ShopActionResult addClaimRegion(CentralBank centralBank,
                                                  UUID ownerId,
                                                  UUID shopId,
                                                  String dimensionId,
                                                  BlockPos first,
                                                  BlockPos second) {
        return mutateClaimRegion(centralBank, ownerId, shopId, dimensionId, first, second, true, false);
    }

    public static ShopActionResult removeClaimRegion(CentralBank centralBank,
                                                     UUID ownerId,
                                                     UUID shopId,
                                                     String dimensionId,
                                                     BlockPos first,
                                                     BlockPos second) {
        return mutateClaimRegion(centralBank, ownerId, shopId, dimensionId, first, second, false, false);
    }

    public static ShopActionResult addStockroomRegion(CentralBank centralBank,
                                                      UUID ownerId,
                                                      UUID shopId,
                                                      String dimensionId,
                                                      BlockPos first,
                                                      BlockPos second) {
        return mutateClaimRegion(centralBank, ownerId, shopId, dimensionId, first, second, true, true);
    }

    public static ShopActionResult removeStockroomRegion(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId,
                                                         String dimensionId,
                                                         BlockPos first,
                                                         BlockPos second) {
        return mutateClaimRegion(centralBank, ownerId, shopId, dimensionId, first, second, false, true);
    }

    private static ShopActionResult mutateClaimRegion(CentralBank centralBank,
                                                      UUID ownerId,
                                                      UUID shopId,
                                                      String dimensionId,
                                                      BlockPos first,
                                                      BlockPos second,
                                                      boolean add,
                                                      boolean stockroom) {
        if (centralBank == null || ownerId == null || first == null || second == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shopTag = resolveShopTag(centralBank, ownerId, shopId);
        if (shopTag == null) {
            return new ShopActionResult(false, "Create a shop first.");
        }
        String dim = normalizedDim(dimensionId);
        int minX = Math.min(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ());
        int maxX = Math.max(first.getX(), second.getX());
        int maxY = Math.max(first.getY(), second.getY());
        int maxZ = Math.max(first.getZ(), second.getZ());

        ListTag target = shopTag.getList(stockroom ? TAG_STOCKROOM_CLAIMS : TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag claims = shopTag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        int level = Math.max(1, shopTag.getInt(TAG_LEVEL));

        if (add) {
            if (!stockroom) {
                for (Tag tag : claims) {
                    if (!(tag instanceof CompoundTag existing)) {
                        continue;
                    }
                    if (!normalizedDim(existing.getString(TAG_DIM)).equals(dim)) {
                        continue;
                    }
                    if (claimsOverlap(
                            minX, minY, minZ, maxX, maxY, maxZ,
                            existing.getInt(TAG_MIN_X), existing.getInt(TAG_MIN_Y), existing.getInt(TAG_MIN_Z),
                            existing.getInt(TAG_MAX_X), existing.getInt(TAG_MAX_Y), existing.getInt(TAG_MAX_Z)
                    )) {
                        return new ShopActionResult(false,
                                "New plot region overlaps existing claim. Add adjacent regions instead.");
                    }
                }

                long currentVolume = computeVolume(claims);
                long addedVolume = computeRegionVolume(minX, minY, minZ, maxX, maxY, maxZ);
                long cap = claimCapacityForLevel(level);
                if (safeAdd(currentVolume, addedVolume) > cap) {
                    return new ShopActionResult(false,
                            "Claim exceeds plot capacity. Used "
                                    + currentVolume + " / " + cap + " blocks.");
                }

                if (!claims.isEmpty()) {
                    boolean connected = false;
                    for (Tag tag : claims) {
                        if (!(tag instanceof CompoundTag existing)) {
                            continue;
                        }
                        if (!normalizedDim(existing.getString(TAG_DIM)).equals(dim)) {
                            continue;
                        }
                        if (claimsTouchOrOverlap(
                                minX, minY, minZ, maxX, maxY, maxZ,
                                existing.getInt(TAG_MIN_X), existing.getInt(TAG_MIN_Y), existing.getInt(TAG_MIN_Z),
                                existing.getInt(TAG_MAX_X), existing.getInt(TAG_MAX_Y), existing.getInt(TAG_MAX_Z)
                        )) {
                            connected = true;
                            break;
                        }
                    }
                    if (!connected) {
                        return new ShopActionResult(false, "New claims must connect to existing plot land.");
                    }
                }

                ShopOverlapResult overlap = overlapsForeignShopClaim(
                        centralBank,
                        shopTag.contains(TAG_ID) ? shopTag.getUUID(TAG_ID) : null,
                        dim,
                        minX, minY, minZ, maxX, maxY, maxZ
                );
                if (overlap.overlap()) {
                    return new ShopActionResult(false, "Selection overlaps another shop claim.");
                }
            } else {
                if (!isRegionInsideClaims(claims, dim, minX, minY, minZ, maxX, maxY, maxZ)) {
                    // Claim corners are often selected at player eye height. If X/Z is valid but
                    // Y is slightly outside the plot envelope, clamp Y to plot bounds first.
                    int[] yBounds = claimVerticalBounds(claims, dim);
                    if (yBounds != null) {
                        int clampedMinY = Math.max(minY, yBounds[0]);
                        int clampedMaxY = Math.min(maxY, yBounds[1]);
                        if (clampedMinY <= clampedMaxY
                                && isRegionInsideClaims(claims, dim, minX, clampedMinY, minZ, maxX, clampedMaxY, maxZ)) {
                            minY = clampedMinY;
                            maxY = clampedMaxY;
                        } else {
                            return new ShopActionResult(false, "Stockroom region must be fully inside your claimed plot.");
                        }
                    } else {
                        return new ShopActionResult(false, "Stockroom region must be fully inside your claimed plot.");
                    }
                }

                // Stockroom claim space is now level-based and can be configured independently.
                long currentStockroomVolume = computeVolume(target);
                long addedStockroomVolume = computeRegionVolume(minX, minY, minZ, maxX, maxY, maxZ);
                long stockroomCap = stockroomCapacityForLevel(level);
                if (safeAdd(currentStockroomVolume, addedStockroomVolume) > stockroomCap) {
                    return new ShopActionResult(false,
                            "Stockroom claim exceeds capacity. Used "
                                    + currentStockroomVolume + " / " + stockroomCap + " blocks.");
                }
            }
            target.add(buildRegionTag(dim, minX, minY, minZ, maxX, maxY, maxZ));
            shopTag.put(stockroom ? TAG_STOCKROOM_CLAIMS : TAG_CLAIMS, target);
            saveShopTag(centralBank, shopTag);
            return new ShopActionResult(true,
                    (stockroom ? "Stockroom" : "Plot")
                            + " region added: "
                            + dim + " (" + minX + "," + minY + "," + minZ + ") -> ("
                            + maxX + "," + maxY + "," + maxZ + ").");
        }

        List<CompoundTag> next = new ArrayList<>();
        boolean changed = false;
        for (Tag tag : target) {
            if (!(tag instanceof CompoundTag existing)) {
                continue;
            }
            if (!normalizedDim(existing.getString(TAG_DIM)).equals(dim)) {
                next.add(existing.copy());
                continue;
            }
            if (!claimsOverlap(
                    minX, minY, minZ, maxX, maxY, maxZ,
                    existing.getInt(TAG_MIN_X), existing.getInt(TAG_MIN_Y), existing.getInt(TAG_MIN_Z),
                    existing.getInt(TAG_MAX_X), existing.getInt(TAG_MAX_Y), existing.getInt(TAG_MAX_Z)
            )) {
                next.add(existing.copy());
                continue;
            }
            changed = true;
            next.addAll(subtractRegion(
                    existing,
                    minX, minY, minZ, maxX, maxY, maxZ
            ));
        }

        if (!changed) {
            return new ShopActionResult(false, "No matching claimed region intersects that selection.");
        }

        MinecraftServer server = null;
        List<BankTellerEntity> plotRemovalCashierCandidates = List.of();
        if (!stockroom) {
            server = ServerLifecycleHooks.getCurrentServer();
            plotRemovalCashierCandidates = collectCashierEntitiesInClaims(
                    server,
                    target,
                    ownerId,
                    shopTag.contains(TAG_ID) ? shopTag.getUUID(TAG_ID) : null
            );
        }

        ListTag replaced = new ListTag();
        for (CompoundTag region : next) {
            replaced.add(region);
        }
        shopTag.put(stockroom ? TAG_STOCKROOM_CLAIMS : TAG_CLAIMS, replaced);

        int removedCashiers = 0;
        int removedTerminalLinks = 0;
        if (!stockroom) {
            clampStockroomClaimsToPlot(shopTag);
            removedCashiers = pruneCashiersOutsideClaims(shopTag, plotRemovalCashierCandidates);
            removedTerminalLinks = pruneTerminalLinksOutsideClaims(shopTag);
        }
        saveShopTag(centralBank, shopTag);
        StringBuilder message = new StringBuilder((stockroom ? "Stockroom" : "Plot") + " region removed.");
        if (removedCashiers > 0) {
            message.append(" Removed ").append(removedCashiers).append(" cashier")
                    .append(removedCashiers == 1 ? "" : "s")
                    .append(" outside the remaining plot.");
        }
        if (removedTerminalLinks > 0) {
            message.append(" Cleared ").append(removedTerminalLinks).append(" terminal link")
                    .append(removedTerminalLinks == 1 ? "" : "s")
                    .append(" outside the remaining plot.");
        }
        return new ShopActionResult(true, message.toString());
    }

    public static ShopActionResult startClaimToolSession(CentralBank centralBank,
                                                         ServerPlayer player,
                                                         boolean stockroomMode) {
        return startClaimToolSession(centralBank, player, null, stockroomMode);
    }

    public static ShopActionResult startClaimToolSession(CentralBank centralBank,
                                                         ServerPlayer player,
                                                         UUID shopId,
                                                         boolean stockroomMode) {
        if (centralBank == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        if (ClaimModeService.hasSession(player.getUUID())
                || SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return new ShopActionResult(false,
                    "Another claim mode is already active. Close it first.");
        }
        if (CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())
                || PALLET_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())) {
            return new ShopActionResult(false, "Claim mode is already active. Exit that workspace first.");
        }
        CompoundTag shop = resolveShopTag(centralBank, player.getUUID(), shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID resolvedShopId = shop.getUUID(TAG_ID);
        long gameTime = player.serverLevel().getGameTime();
        ClaimToolSession session = new ClaimToolSession(
                player.getUUID(),
                player.getUUID(),
                resolvedShopId,
                stockroomMode,
                true,
                false,
                gameTime,
                gameTime,
                "",
                null,
                null,
                List.of(),
                player.getInventory().selected
        );
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), session);
        ClaimToolKind kind = stockroomMode ? ClaimToolKind.SHOP_STOCKROOM : ClaimToolKind.SHOP_PLOT;
        if (!ClaimModeService.begin(player, kind)) {
            CLAIM_TOOL_SESSIONS.remove(player.getUUID());
            return new ShopActionResult(false, "Another claim mode is already active.");
        }
        return new ShopActionResult(
                true,
                (stockroomMode ? "Stockroom" : "Plot")
                        + " claim mode enabled. Left-click sets Pos1, right-click sets Pos2, and Tab opens controls."
        );
    }

    public static ShopActionResult startPalletClaimToolSession(CentralBank centralBank,
                                                               ServerPlayer player,
                                                               UUID shopId) {
        if (centralBank == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        if (ClaimModeService.hasSession(player.getUUID())
                || SafetyDepositBoxService.hasSafeAreaClaimToolSession(player.getUUID())) {
            return new ShopActionResult(false,
                    "Another claim mode is already active. Close it first.");
        }
        if (CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())
                || PALLET_CLAIM_TOOL_SESSIONS.containsKey(player.getUUID())) {
            return new ShopActionResult(false, "A claim tool session is already active. Save/cancel that first.");
        }
        CompoundTag shop = resolveShopTag(centralBank, player.getUUID(), shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        long gameTime = player.serverLevel().getGameTime();
        Set<String> assignedRefs = new LinkedHashSet<>(collectAssignedPalletRefSet(shop));

        // Pallet labeling uses staged changes: players can add/remove refs and commit with Save.
        PalletClaimToolSession session = new PalletClaimToolSession(
                player.getUUID(),
                player.getUUID(),
                shop.getUUID(TAG_ID),
                true,
                gameTime,
                gameTime,
                assignedRefs,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                List.of(),
                player.getInventory().selected
        );
        PALLET_CLAIM_TOOL_SESSIONS.put(player.getUUID(), session);
        if (!ClaimModeService.begin(player, ClaimToolKind.DELIVERY_PALLET)) {
            PALLET_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
            return new ShopActionResult(false, "Another claim mode is already active.");
        }
        return new ShopActionResult(true,
                "Delivery pallet claim mode enabled. Aim at a pallet and click to stage it.");
    }

    public static boolean hasClaimToolSession(UUID playerId) {
        return playerId != null && CLAIM_TOOL_SESSIONS.containsKey(playerId);
    }

    public static boolean hasPalletClaimToolSession(UUID playerId) {
        return playerId != null && PALLET_CLAIM_TOOL_SESSIONS.containsKey(playerId);
    }

    public static boolean hasAnyClaimToolSession(UUID playerId) {
        return hasClaimToolSession(playerId) || hasPalletClaimToolSession(playerId);
    }

    public record ClaimToolView(UUID ownerId,
                                UUID shopId,
                                boolean stockroomMode,
                                boolean addMode,
                                boolean outlinesVisible,
                                long startedTick,
                                long lastUpdatedTick,
                                String dimensionId,
                                BlockPos firstCorner,
                                BlockPos secondCorner,
                                String shopName,
                                String ownerName) {
    }

    public record PalletClaimToolView(UUID ownerId,
                                      UUID shopId,
                                      boolean addMode,
                                      long startedTick,
                                      long lastUpdatedTick,
                                      int pendingAdd,
                                      int pendingRemove,
                                      String shopName,
                                      String ownerName) {
    }

    public static ClaimToolView claimToolView(CentralBank centralBank, UUID playerId) {
        ClaimToolSession session = playerId == null ? null : CLAIM_TOOL_SESSIONS.get(playerId);
        if (session == null) {
            return null;
        }
        CompoundTag shop = resolveShopTag(centralBank, session.ownerId(), session.shopId());
        return new ClaimToolView(
                session.ownerId(), session.shopId(), session.stockroomMode(), session.addMode(),
                session.overlayEnabled(), session.startedTick(), session.lastUpdatedTick(),
                normalizedDim(session.firstDimensionId()),
                session.firstCorner() == null ? null : session.firstCorner().immutable(),
                session.secondCorner() == null ? null : session.secondCorner().immutable(),
                shop == null ? "Shop" : sanitizeTokenText(shop.getString(TAG_NAME)),
                shop == null ? "" : sanitizeTokenText(shop.getString(TAG_OWNER_NAME))
        );
    }

    public static PalletClaimToolView palletClaimToolView(CentralBank centralBank, UUID playerId) {
        PalletClaimToolSession session = playerId == null ? null : PALLET_CLAIM_TOOL_SESSIONS.get(playerId);
        if (session == null) {
            return null;
        }
        CompoundTag shop = resolveShopTag(centralBank, session.ownerId(), session.shopId());
        return new PalletClaimToolView(
                session.ownerId(), session.shopId(), session.addMode(),
                session.startedTick(), session.lastUpdatedTick(),
                session.pendingAddRefs().size(), session.pendingRemoveRefs().size(),
                shop == null ? "Shop" : sanitizeTokenText(shop.getString(TAG_NAME)),
                shop == null ? "" : sanitizeTokenText(shop.getString(TAG_OWNER_NAME))
        );
    }

    public static List<ClaimOutline> collectClaimOutlines(CentralBank centralBank,
                                                           ServerPlayer viewer,
                                                           int range,
                                                           int limit) {
        return collectClaimOutlines(centralBank, viewer, range, limit, null);
    }

    public static List<ClaimOutline> collectClaimOutlines(CentralBank centralBank,
                                                           ServerPlayer viewer,
                                                           int range,
                                                           int limit,
                                                           UUID visibleOwnerId) {
        if (centralBank == null || viewer == null || limit <= 0) {
            return List.of();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        ListTag shops = getOrCreateRoot(centralMeta).getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        String dimension = normalizedDim(viewer.serverLevel().dimension().location().toString());
        BlockPos origin = viewer.blockPosition();
        List<ClaimOutline> outlines = new ArrayList<>();
        for (Tag raw : shops) {
            if (!(raw instanceof CompoundTag shop)) {
                continue;
            }
            UUID ownerId = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
            if (visibleOwnerId != null && !visibleOwnerId.equals(ownerId)) {
                continue;
            }
            String owner = sanitizeTokenText(shop.getString(TAG_OWNER_NAME));
            if (owner.isBlank()) {
                owner = sanitizeTokenText(shop.getString(TAG_NAME));
            }
            appendClaimOutlines(outlines, shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                    dimension, origin, range, limit, "SHOP_PLOT", ownerId, owner);
            appendClaimOutlines(outlines, shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND),
                    dimension, origin, range, limit, "SHOP_STOCKROOM", ownerId, owner);
            if (outlines.size() < limit && viewer.getServer() != null) {
                Map<String, PalletRef> livePallets = buildLivePalletLookup(
                        viewer.getServer(), deliveryPalletSearchClaims(shop));
                for (String palletKey : collectAssignedPalletRefSet(shop)) {
                    PalletRef pallet = resolveAssignedPalletLiveRef(
                            viewer.getServer(), shop, palletKey, livePallets);
                    if (pallet == null || pallet.pos() == null
                            || !dimension.equals(normalizedDim(pallet.dimensionId()))) {
                        continue;
                    }
                    BlockPos pos = pallet.pos();
                    ClaimOutline outline = new ClaimOutline(
                            dimension, "DELIVERY_PALLET", ownerId == null ? "" : ownerId.toString(), owner,
                            pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                            pos.getX() + 1, pos.getY(), pos.getZ() + 1);
                    if (outline.near(origin.getX(), origin.getY(), origin.getZ(), range)) {
                        outlines.add(outline);
                        if (outlines.size() >= limit) {
                            break;
                        }
                    }
                }
            }
            if (outlines.size() >= limit) {
                break;
            }
        }
        return List.copyOf(outlines);
    }

    private static void appendClaimOutlines(List<ClaimOutline> target,
                                            ListTag claims,
                                            String dimension,
                                            BlockPos origin,
                                            int range,
                                            int limit,
                                            String type,
                                            UUID ownerId,
                                            String owner) {
        if (target.size() >= limit) {
            return;
        }
        for (Tag raw : claims) {
            if (!(raw instanceof CompoundTag claim)
                    || !dimension.equals(normalizedDim(claim.getString(TAG_DIM)))) {
                continue;
            }
            ClaimOutline outline = new ClaimOutline(
                    dimension, type, ownerId == null ? "" : ownerId.toString(), owner,
                    claim.getInt(TAG_MIN_X), claim.getInt(TAG_MIN_Y), claim.getInt(TAG_MIN_Z),
                    claim.getInt(TAG_MAX_X), claim.getInt(TAG_MAX_Y), claim.getInt(TAG_MAX_Z));
            if (outline.near(origin.getX(), origin.getY(), origin.getZ(), range)) {
                target.add(outline);
                if (target.size() >= limit) {
                    return;
                }
            }
        }
    }

    public static List<ClaimOutline> collectPendingPalletClaimOutlines(MinecraftServer server,
                                                                        CentralBank centralBank,
                                                                        UUID playerId,
                                                                        int limit) {
        PalletClaimToolSession session = playerId == null ? null : PALLET_CLAIM_TOOL_SESSIONS.get(playerId);
        if (server == null || centralBank == null || session == null || limit <= 0) {
            return List.of();
        }
        CompoundTag shop = resolveShopTag(centralBank, session.ownerId(), session.shopId());
        if (shop == null) {
            return List.of();
        }
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(
                server, deliveryPalletSearchClaims(shop));
        String ownerName = sanitizeTokenText(shop.getString(TAG_OWNER_NAME));
        String ownerId = session.ownerId() == null ? "" : session.ownerId().toString();
        List<ClaimOutline> outlines = new ArrayList<>();
        appendPendingPalletOutlines(outlines, server, shop, liveLookup,
                session.pendingAddRefs(), "PENDING_PALLET_ADD", ownerId, ownerName, limit);
        appendPendingPalletOutlines(outlines, server, shop, liveLookup,
                session.pendingRemoveRefs(), "PENDING_PALLET_REMOVE", ownerId, ownerName, limit);
        return List.copyOf(outlines);
    }

    private static void appendPendingPalletOutlines(List<ClaimOutline> target,
                                                     MinecraftServer server,
                                                     CompoundTag shop,
                                                     Map<String, PalletRef> liveLookup,
                                                     Set<String> palletIds,
                                                     String type,
                                                     String ownerId,
                                                     String ownerName,
                                                     int limit) {
        if (palletIds == null || palletIds.isEmpty() || target.size() >= limit) {
            return;
        }
        for (String palletId : palletIds) {
            PalletRef pallet = resolveAssignedPalletLiveRef(server, shop, palletId, liveLookup);
            if (pallet == null || pallet.pos() == null) {
                continue;
            }
            BlockPos pos = pallet.pos();
            target.add(new ClaimOutline(
                    normalizedDim(pallet.dimensionId()), type, ownerId, ownerName,
                    pos.getX() - 1, pos.getY(), pos.getZ() - 1,
                    pos.getX() + 1, pos.getY(), pos.getZ() + 1));
            if (target.size() >= limit) {
                return;
            }
        }
    }

    public static boolean hasStockroomLocateSession(UUID playerId) {
        return playerId != null && STOCKROOM_LOCATE_SESSIONS.containsKey(playerId);
    }

    public static ShopActionResult cancelStockroomLocate(ServerPlayer player, String reason) {
        if (player == null) {
            return new ShopActionResult(false, "Locate session is unavailable.");
        }
        StockroomLocateSession removed = STOCKROOM_LOCATE_SESSIONS.remove(player.getUUID());
        if (removed == null) {
            return new ShopActionResult(false, "No stockroom locate session is active.");
        }
        clearStockroomLocateRender(player);
        String message = (reason == null || reason.isBlank()) ? "Stockroom locate canceled." : reason.trim();
        player.sendSystemMessage(UbsTranslations.literal(message).withStyle(ChatFormatting.YELLOW));
        pushShopAlert(player, "Stockroom Locate", message, DeliveryAlertPayload.AlertTone.WARNING, 5000);
        player.displayClientMessage(UbsTranslations.literal("§8Stockroom locate inactive"), true);
        return new ShopActionResult(true, message);
    }

    public static boolean isClaimToolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String marker = ItemStackDataCompat.getCustomString(CLAIM_TOOL_ITEM_TAG, stack);
        if ("wand".equals(marker)) {
            return true;
        }
        return CLAIM_TOOL_ADD.equals(marker)
                || CLAIM_TOOL_REMOVE.equals(marker)
                || CLAIM_TOOL_APPLY.equals(marker)
                || CLAIM_TOOL_CLEAR.equals(marker)
                || CLAIM_TOOL_OVERLAY.equals(marker)
                || CLAIM_TOOL_LOCK.equals(marker)
                || CLAIM_TOOL_FINISH.equals(marker);
    }

    public static boolean isPalletClaimToolStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String marker = ItemStackDataCompat.getCustomString(CLAIM_TOOL_ITEM_TAG, stack);
        return PALLET_TOOL_ADD.equals(marker)
                || PALLET_TOOL_REMOVE.equals(marker)
                || PALLET_TOOL_SAVE.equals(marker)
                || PALLET_TOOL_CANCEL.equals(marker)
                || PALLET_TOOL_LOCK.equals(marker);
    }

    public static String claimToolMarker(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return ItemStackDataCompat.getCustomString(CLAIM_TOOL_ITEM_TAG, stack);
    }

    public static String palletClaimToolMarker(ItemStack stack) {
        return claimToolMarker(stack);
    }

    public static ShopActionResult setClaimToolMode(ServerPlayer player, boolean addMode) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        long now = player.serverLevel().getGameTime();
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                addMode,
                session.overlayEnabled(),
                session.startedTick(),
                now,
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true,
                "Mode set to " + (addMode ? "Add Region" : "Remove Region")
                        + ". Left-click sets Pos1, right-click sets Pos2, then press Enter to apply.");
    }

    public static ShopActionResult toggleClaimOverlay(ServerPlayer player) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        boolean overlayEnabled = !session.overlayEnabled();
        long now = player.serverLevel().getGameTime();
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                session.addMode(),
                overlayEnabled,
                session.startedTick(),
                now,
                session.firstDimensionId(),
                session.firstCorner(),
                session.secondCorner(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true, "Claim overlay " + (overlayEnabled ? "enabled" : "disabled") + ".");
    }

    public static ShopActionResult setClaimToolFirstCorner(ServerPlayer player,
                                                           BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        String dim = normalizedDim(player.serverLevel().dimension().location().toString());
        long now = player.serverLevel().getGameTime();
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                session.addMode(),
                session.overlayEnabled(),
                session.startedTick(),
                now,
                dim,
                clickedPos.immutable(),
                session.secondCorner() != null
                        && dim.equals(normalizedDim(session.firstDimensionId()))
                        ? session.secondCorner().immutable()
                        : null,
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true,
                "Pos1 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                        + ". Right-click a block to set Pos2.");
    }

    public static ShopActionResult setClaimToolSecondCorner(ServerPlayer player,
                                                            BlockPos clickedPos) {
        if (player == null || clickedPos == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        String dim = normalizedDim(player.serverLevel().dimension().location().toString());
        long now = player.serverLevel().getGameTime();
        BlockPos first = session.firstCorner();
        String selectedDim = normalizedDim(session.firstDimensionId());
        if (selectedDim.isBlank() || !selectedDim.equals(dim)) {
            first = null;
        }

        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                session.addMode(),
                session.overlayEnabled(),
                session.startedTick(),
                now,
                dim,
                first == null ? null : first.immutable(),
                clickedPos.immutable(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        if (first == null) {
            return new ShopActionResult(true,
                    "Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                            + ". Left-click a block to set Pos1.");
        }
        return new ShopActionResult(true,
                "Pos2 set at " + clickedPos.getX() + ", " + clickedPos.getY() + ", " + clickedPos.getZ()
                        + ". Press Enter to apply " + (session.addMode() ? "Add" : "Remove") + " region.");
    }

    public static ShopActionResult clearClaimToolSelection(ServerPlayer player) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        long now = player.serverLevel().getGameTime();
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                session.addMode(),
                session.overlayEnabled(),
                session.startedTick(),
                now,
                "",
                null,
                null,
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true, "Selection cleared.");
    }

    public static ShopActionResult applyClaimToolSelection(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           ServerPlayer player) {
        if (server == null || centralBank == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        String dim = normalizedDim(player.serverLevel().dimension().location().toString());
        if (session.firstCorner() == null || session.secondCorner() == null
                || session.firstDimensionId() == null || session.firstDimensionId().isBlank()) {
            return new ShopActionResult(false, "Select Pos1 and Pos2 first, then apply.");
        }
        String selectedDim = normalizedDim(session.firstDimensionId());
        if (!selectedDim.equals(dim)) {
            return new ShopActionResult(false, "Return to " + selectedDim + " to apply this selection.");
        }

        BlockPos first = session.firstCorner();
        BlockPos second = session.secondCorner();
        ShopActionResult result;
        if (session.stockroomMode()) {
            result = session.addMode()
                    ? addStockroomRegion(centralBank, session.ownerId(), session.shopId(), selectedDim, first, second)
                    : removeStockroomRegion(centralBank, session.ownerId(), session.shopId(), selectedDim, first, second);
        } else {
            result = session.addMode()
                    ? addClaimRegion(centralBank, session.ownerId(), session.shopId(), selectedDim, first, second)
                    : removeClaimRegion(centralBank, session.ownerId(), session.shopId(), selectedDim, first, second);
        }

        long now = player.serverLevel().getGameTime();
        CLAIM_TOOL_SESSIONS.put(player.getUUID(), new ClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.stockroomMode(),
                session.addMode(),
                session.overlayEnabled(),
                session.startedTick(),
                now,
                result.success() ? "" : selectedDim,
                result.success() ? null : first.immutable(),
                result.success() ? null : second.immutable(),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        if (result.success()) {
            return new ShopActionResult(true, result.message() + " Selection cleared for next region.");
        }
        return result;
    }

    public static ShopActionResult setPalletClaimToolMode(ServerPlayer player, boolean addMode) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        PalletClaimToolSession session = PALLET_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No pallet claim tool session is active.");
        }
        long now = player.serverLevel().getGameTime();
        PALLET_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new PalletClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                addMode,
                session.startedTick(),
                now,
                new LinkedHashSet<>(session.baseAssignedRefs()),
                new LinkedHashSet<>(session.pendingAddRefs()),
                new LinkedHashSet<>(session.pendingRemoveRefs()),
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true, "Pallet mode set to " + (addMode ? "Add" : "Remove") + ".");
    }

    public static ShopActionResult stagePalletClaimSelection(CentralBank centralBank,
                                                             ServerPlayer player,
                                                             BlockPos clickedPos) {
        if (centralBank == null || player == null || clickedPos == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        PalletClaimToolSession session = PALLET_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No pallet claim tool session is active.");
        }

        BlockState clickedState = player.serverLevel().getBlockState(clickedPos);
        if (!(clickedState.getBlock() instanceof PalletBlock)) {
            return new ShopActionResult(false, "Target a pallet block to label/unlabel it.");
        }
        BlockPos masterPos = PalletBlock.getMasterPos(clickedState, clickedPos);
        BlockEntity clickedBe = player.serverLevel().getBlockEntity(masterPos);
        if (!(clickedBe instanceof PalletBlockEntity palletEntity)) {
            return new ShopActionResult(false, "Pallet entity is unavailable at that position.");
        }
        String dim = normalizedDim(player.serverLevel().dimension().location().toString());

        CompoundTag shop = resolveShopTag(centralBank, session.ownerId(), session.shopId());
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "Shop no longer exists.");
        }
        if (!isInsideDeliveryPalletClaims(shop, dim, masterPos)) {
            return new ShopActionResult(false, "Pallet must be inside your claimed shop plot or stockroom claim.");
        }

        // Delivery pallet assignment is identity-based so breaking/re-placing the pallet
        // keeps assignment state via item NBT and block entity ID.
        String palletId = palletEntity.ensureDeliveryPalletId();
        Set<String> base = new LinkedHashSet<>(session.baseAssignedRefs());
        Set<String> pendingAdd = new LinkedHashSet<>(session.pendingAddRefs());
        Set<String> pendingRemove = new LinkedHashSet<>(session.pendingRemoveRefs());
        String actionMessage;

        if (session.addMode()) {
            if (pendingRemove.remove(palletId)) {
                actionMessage = "Undo remove for pallet " + shortPalletId(palletId)
                        + " at " + formatPalletRef(encodeOrderPalletRef(dim, masterPos)) + ".";
            } else if (base.contains(palletId) || pendingAdd.contains(palletId)) {
                return new ShopActionResult(false, "Pallet is already labeled as delivery.");
            } else {
                pendingAdd.add(palletId);
                actionMessage = "Queued add for pallet " + shortPalletId(palletId)
                        + " at " + formatPalletRef(encodeOrderPalletRef(dim, masterPos)) + ".";
            }
        } else {
            if (pendingAdd.remove(palletId)) {
                actionMessage = "Undo add for pallet " + shortPalletId(palletId)
                        + " at " + formatPalletRef(encodeOrderPalletRef(dim, masterPos)) + ".";
            } else if (!base.contains(palletId) || pendingRemove.contains(palletId)) {
                return new ShopActionResult(false, "Pallet is not currently labeled for delivery.");
            } else {
                pendingRemove.add(palletId);
                actionMessage = "Queued remove for pallet " + shortPalletId(palletId)
                        + " at " + formatPalletRef(encodeOrderPalletRef(dim, masterPos)) + ".";
            }
        }

        long now = player.serverLevel().getGameTime();
        PALLET_CLAIM_TOOL_SESSIONS.put(player.getUUID(), new PalletClaimToolSession(
                session.playerId(),
                session.ownerId(),
                session.shopId(),
                session.addMode(),
                session.startedTick(),
                now,
                base,
                pendingAdd,
                pendingRemove,
                session.hotbarSnapshot(),
                session.selectedHotbarSlot()
        ));
        return new ShopActionResult(true,
                actionMessage + " Pending: +" + pendingAdd.size() + " / -" + pendingRemove.size() + ".");
    }

    public static ShopActionResult savePalletClaimToolSession(MinecraftServer server,
                                                              CentralBank centralBank,
                                                              ServerPlayer player) {
        if (server == null || centralBank == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        PalletClaimToolSession session = PALLET_CLAIM_TOOL_SESSIONS.get(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No pallet claim tool session is active.");
        }

        Set<String> pendingRemove = new LinkedHashSet<>(session.pendingRemoveRefs());
        Set<String> pendingAdd = new LinkedHashSet<>(session.pendingAddRefs());
        int removed = 0;
        int added = 0;
        List<String> failures = new ArrayList<>();

        // Apply removals first so the level cap can be reused for new assignments in this save.
        for (String ref : pendingRemove) {
            ShopActionResult result = unassignOrderPallet(server, centralBank, session.ownerId(), session.shopId(), ref);
            if (result.success()) {
                removed++;
            } else if (failures.size() < 3) {
                failures.add(result.message());
            }
        }
        for (String ref : pendingAdd) {
            ShopActionResult result = assignOrderPallet(server, centralBank, session.ownerId(), session.shopId(), ref);
            if (result.success()) {
                added++;
            } else if (failures.size() < 3) {
                failures.add(result.message());
            }
        }

        finishPalletClaimToolSession(player, "Delivery pallet claim tool saved and closed.");
        if (added == 0 && removed == 0 && failures.isEmpty()) {
            return new ShopActionResult(true, "No changes were pending. Tool closed.");
        }

        StringBuilder message = new StringBuilder("Saved delivery pallet labels: +")
                .append(added)
                .append(" / -")
                .append(removed);
        if (!failures.isEmpty()) {
            message.append(" | Issues: ").append(String.join(" | ", failures));
        }
        return new ShopActionResult(failures.isEmpty(), message.toString());
    }

    public static ShopActionResult finishPalletClaimToolSession(ServerPlayer player, String reason) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        PalletClaimToolSession session = PALLET_CLAIM_TOOL_SESSIONS.remove(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No pallet claim tool session is active.");
        }
        restoreHotbar(player, session.hotbarSnapshot(), session.selectedHotbarSlot());
        ClaimModeService.domainClosed(player);
        return new ShopActionResult(true, reason == null || reason.isBlank() ? "Pallet claim tool closed." : reason);
    }

    public static void clearPalletClaimToolSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PALLET_CLAIM_TOOL_SESSIONS.remove(playerId);
        ClaimModeService.clear(playerId);
    }

    public static void closeAllClaimToolSessions(ServerPlayer player, String reason) {
        if (player == null) {
            return;
        }
        if (hasClaimToolSession(player.getUUID())) {
            finishClaimToolSession(player, reason == null || reason.isBlank() ? "Claim tool closed." : reason);
        }
        if (hasPalletClaimToolSession(player.getUUID())) {
            finishPalletClaimToolSession(player, reason == null || reason.isBlank() ? "Pallet claim tool closed." : reason);
        }
        if (hasStockroomLocateSession(player.getUUID())) {
            cancelStockroomLocate(player, "Stockroom locate closed.");
        }
    }

    public static ShopActionResult finishClaimToolSession(ServerPlayer player, String reason) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        ClaimToolSession session = CLAIM_TOOL_SESSIONS.remove(player.getUUID());
        if (session == null) {
            return new ShopActionResult(false, "No claim tool session is active.");
        }
        restoreHotbar(player, session.hotbarSnapshot(), session.selectedHotbarSlot());
        ClaimModeService.domainClosed(player);
        return new ShopActionResult(true, reason == null || reason.isBlank() ? "Claim tool closed." : reason);
    }

    public static void clearClaimToolSession(UUID playerId) {
        if (playerId == null) {
            return;
        }
        CLAIM_TOOL_SESSIONS.remove(playerId);
        ClaimModeService.clear(playerId);
    }

    public static void clearClaimSessionsOnServerStopping() {
        CLAIM_TOOL_SESSIONS.clear();
        PALLET_CLAIM_TOOL_SESSIONS.clear();
        STOCKROOM_LOCATE_SESSIONS.clear();
    }

    public static void tickSessions(MinecraftServer server) {
        if (server == null) {
            return;
        }
        if (!CLAIM_TOOL_SESSIONS.isEmpty()) {
            for (UUID playerId : new ArrayList<>(CLAIM_TOOL_SESSIONS.keySet())) {
                ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(playerId);
                if (session == null) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    CLAIM_TOOL_SESSIONS.remove(playerId);
                    ClaimModeService.clear(playerId);
                    continue;
                }
                long now = player.serverLevel().getGameTime();
                if ((now - session.lastUpdatedTick()) > CLAIM_TOOL_TIMEOUT_TICKS) {
                    finishClaimToolSession(player, "Claim mode timed out.");
                }
            }
        }
        if (!PALLET_CLAIM_TOOL_SESSIONS.isEmpty()) {
            for (UUID playerId : new ArrayList<>(PALLET_CLAIM_TOOL_SESSIONS.keySet())) {
                PalletClaimToolSession session = PALLET_CLAIM_TOOL_SESSIONS.get(playerId);
                if (session == null) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    PALLET_CLAIM_TOOL_SESSIONS.remove(playerId);
                    ClaimModeService.clear(playerId);
                    continue;
                }
                long now = player.serverLevel().getGameTime();
                if ((now - session.lastUpdatedTick()) > CLAIM_TOOL_TIMEOUT_TICKS) {
                    finishPalletClaimToolSession(player, "Delivery pallet claim mode timed out.");
                }
            }
        }
        if (!CASHIER_TERMINAL_SELECTIONS.isEmpty()) {
            for (UUID playerId : new ArrayList<>(CASHIER_TERMINAL_SELECTIONS.keySet())) {
                CashierTerminalSelection selection = CASHIER_TERMINAL_SELECTIONS.get(playerId);
                if (selection == null) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    CASHIER_TERMINAL_SELECTIONS.remove(playerId);
                    continue;
                }
                long now = player.serverLevel().getGameTime();
                if ((now - selection.startedTick()) > CLAIM_TOOL_TIMEOUT_TICKS) {
                    CASHIER_TERMINAL_SELECTIONS.remove(playerId);
                    player.sendSystemMessage(UbsTranslations.literal("§eCashier terminal selection timed out."));
                    pushShopAlert(player, "Cashier Link", "Cashier terminal selection timed out.", DeliveryAlertPayload.AlertTone.WARNING, 5200);
                }
            }
        }
        if (!STOCKROOM_LOCATE_SESSIONS.isEmpty()) {
            for (UUID playerId : new ArrayList<>(STOCKROOM_LOCATE_SESSIONS.keySet())) {
                StockroomLocateSession session = STOCKROOM_LOCATE_SESSIONS.get(playerId);
                if (session == null) {
                    continue;
                }
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    STOCKROOM_LOCATE_SESSIONS.remove(playerId);
                    continue;
                }
                long now = player.serverLevel().getGameTime();
                if ((now - session.startedTick()) > STOCKROOM_LOCATE_TIMEOUT_TICKS) {
                    STOCKROOM_LOCATE_SESSIONS.remove(playerId);
                    clearStockroomLocateRender(player);
                    player.sendSystemMessage(UbsTranslations.literal("§eStockroom locate timed out."));
                    pushShopAlert(player, "Stockroom Locate", "Stockroom locate timed out.", DeliveryAlertPayload.AlertTone.WARNING, 5200);
                    player.displayClientMessage(UbsTranslations.literal("§8Stockroom locate inactive"), true);
                    continue;
                }
                if ((now % 20L) == 0L) {
                    sendStockroomLocateActionBar(player, session);
                }
            }
        }
        tickDeliveryPalletLabelSync(server);
        tickDeliveryPalletHoverHints(server);
        tickShopStatusAndLighting(server);
        tickWebshopOrders(server);
        tickDeliveryInfoBoard(server);
    }

    /**
     * Checks if any chunk covered by the shop's plot or stockroom claims is currently unloaded.
     */
    private static boolean anyShopChunksUnloaded(MinecraftServer server, CompoundTag shop) {
        if (server == null || shop == null) {
            return false;
        }
        return anyRegionsUnloaded(server, shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND))
                || anyRegionsUnloaded(server, shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND));
    }

    private static boolean anyRegionsUnloaded(MinecraftServer server, ListTag regions) {
        if (regions == null || regions.isEmpty()) {
            return false;
        }
        for (Tag tag : regions) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            String dim = region.getString(TAG_DIM);
            ServerLevel level = server.getLevel(serverLevelKey(dim));
            if (level == null) {
                // The claim's dimension is not present on this server, so the region is
                // unobservable; treat it as unloaded to keep persisted state untouched.
                return true;
            }
            int minX = regionMinX(region) >> 4;
            int maxX = regionMaxX(region) >> 4;
            int minZ = regionMinZ(region) >> 4;
            int maxZ = regionMaxZ(region) >> 4;
            for (int cx = minX; cx <= maxX; cx++) {
                for (int cz = minZ; cz <= maxZ; cz++) {
                    if (!level.hasChunk(cx, cz)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Pushes a live courier delivery board to the right-side client HUD.
     * Data only updates when values change to keep network traffic light.
     */
    private static void tickDeliveryInfoBoard(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        long gameTime = resolveReferenceGameTime(server);
        if ((gameTime % DELIVERY_INFO_SYNC_INTERVAL_TICKS) != 0L) {
            return;
        }

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        if (onlinePlayers.isEmpty()) {
            DELIVERY_INFO_BOARD_CACHE.clear();
            return;
        }

        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            for (ServerPlayer player : onlinePlayers) {
                if (player != null) {
                    pushDeliveryInfoBoard(player, DeliveryInfoBoardPayload.inactive());
                }
            }
            clearDeliveryInfoBoardCacheForOfflinePlayers(new HashSet<>());
            return;
        }

        Map<UUID, List<DeliveryBoardOrder>> activeByCourier = collectActiveDeliveryOrdersByCourier(server, centralBank);
        Set<UUID> onlineIds = new HashSet<>();
        for (ServerPlayer player : onlinePlayers) {
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUUID();
            onlineIds.add(playerId);
            List<DeliveryBoardOrder> orders = activeByCourier.get(playerId);
            if (orders == null || orders.isEmpty()) {
                pushDeliveryInfoBoard(player, DeliveryInfoBoardPayload.inactive());
                continue;
            }

            DeliveryBoardOrder focus = orders.get(0);
            long now = System.currentTimeMillis();
            long remainingSeconds = focus.expiresAtMillis() <= 0L
                    ? 0L
                    : Math.max(0L, (focus.expiresAtMillis() - now + 999L) / 1000L);

            CourierProgress progress = readCourierProgress(centralBank, playerId);
            String distanceLabel = buildDeliveryDistanceLabel(focus.shopClaims(), player);
            DeliveryInfoBoardPayload payload = new DeliveryInfoBoardPayload(
                    true,
                    focus.shopName(),
                    focus.itemName(),
                    focus.quantity(),
                    focus.rewardCents(),
                    remainingSeconds,
                    focus.timeoutMinutes(),
                    orders.size(),
                    maxActiveCourierOrders(),
                    focus.dropTarget(),
                    distanceLabel,
                    courierRankLabel(progress.completed()),
                    progress.streak(),
                    progress.successRatePct(),
                    progress.completed(),
                    progress.totalPayoutCents()
            );
            pushDeliveryInfoBoard(player, payload);
        }

        clearDeliveryInfoBoardCacheForOfflinePlayers(onlineIds);
    }

    private static Map<UUID, List<DeliveryBoardOrder>> collectActiveDeliveryOrdersByCourier(MinecraftServer server,
                                                                                             CentralBank centralBank) {
        Map<UUID, List<DeliveryBoardOrder>> byCourier = new HashMap<>();
        if (server == null || centralBank == null) {
            return byCourier;
        }

        long now = System.currentTimeMillis();
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            boolean changed = expireOrdersInPlace(centralBank, shop, now);
            if (changed) {
                saveShopTag(centralBank, shop);
            }

            String shopName = sanitizeTokenText(shop.getString(TAG_NAME));
            ListTag claimCopy = copyClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND));
            for (OrderView order : collectOrderViews(shop)) {
                if (!ORDER_STATUS_ACCEPTED.equals(order.status()) || order.acceptedBy() == null) {
                    continue;
                }
                if (order.expiresAt() > 0L && order.expiresAt() < now) {
                    continue;
                }
                String dropTarget = order.boundPalletRef().isBlank()
                        ? "Any delivery pallet"
                        : formatPalletRef(order.boundPalletRef());
                byCourier.computeIfAbsent(order.acceptedBy(), key -> new ArrayList<>())
                        .add(new DeliveryBoardOrder(
                                shopName,
                                order.itemName(),
                                order.quantity(),
                                order.rewardCents(),
                                order.expiresAt(),
                                order.timeoutMinutes(),
                                dropTarget,
                                claimCopy,
                                order.createdAtMillis()
                        ));
            }
        }

        for (List<DeliveryBoardOrder> orders : byCourier.values()) {
            orders.sort(Comparator
                    .comparingLong((DeliveryBoardOrder order) -> order.expiresAtMillis() <= 0L ? Long.MAX_VALUE : order.expiresAtMillis())
                    .thenComparingLong(DeliveryBoardOrder::createdAtMillis));
        }
        return byCourier;
    }

    private static ListTag copyClaims(ListTag claims) {
        ListTag copy = new ListTag();
        if (claims == null || claims.isEmpty()) {
            return copy;
        }
        for (Tag tag : claims) {
            if (tag instanceof CompoundTag region) {
                copy.add(region.copy());
            }
        }
        return copy;
    }

    private static String buildDeliveryDistanceLabel(ListTag claims, ServerPlayer player) {
        if (claims == null || claims.isEmpty() || player == null) {
            return "Unknown";
        }
        int blocks = blocksAwayFromShopClaims(
                claims,
                player.serverLevel().dimension().location().toString(),
                player.blockPosition()
        );
        if (blocks < 0) {
            return "Different dimension";
        }
        if (blocks == 0) {
            return "Inside shop";
        }
        return blocks + " block" + (blocks == 1 ? "" : "s") + " away";
    }

    private static void pushDeliveryInfoBoard(ServerPlayer player, DeliveryInfoBoardPayload payload) {
        if (player == null) {
            return;
        }
        DeliveryInfoBoardPayload safePayload = payload == null ? DeliveryInfoBoardPayload.inactive() : payload;
        String fingerprint = deliveryInfoBoardFingerprint(safePayload);
        UUID playerId = player.getUUID();
        if (Objects.equals(DELIVERY_INFO_BOARD_CACHE.get(playerId), fingerprint)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, safePayload);
        DELIVERY_INFO_BOARD_CACHE.put(playerId, fingerprint);
    }

    private static String deliveryInfoBoardFingerprint(DeliveryInfoBoardPayload payload) {
        if (payload == null || !payload.active()) {
            return "inactive";
        }
        return "active|"
                + payload.shopName() + "|"
                + payload.itemName() + "|"
                + payload.quantity() + "|"
                + payload.rewardCents() + "|"
                + payload.remainingSeconds() + "|"
                + payload.timeoutMinutes() + "|"
                + payload.activeOrders() + "|"
                + payload.activeCap() + "|"
                + payload.dropTarget() + "|"
                + payload.distanceLabel() + "|"
                + payload.rankLabel() + "|"
                + payload.streak() + "|"
                + payload.successRatePct() + "|"
                + payload.completedOrders() + "|"
                + payload.totalPayoutCents();
    }

    private static void clearDeliveryInfoBoardCacheForOfflinePlayers(Set<UUID> onlineIds) {
        Set<UUID> activeOnline = onlineIds == null ? Set.of() : onlineIds;
        for (UUID cachedPlayerId : new HashSet<>(DELIVERY_INFO_BOARD_CACHE.keySet())) {
            if (!activeOnline.contains(cachedPlayerId)) {
                DELIVERY_INFO_BOARD_CACHE.remove(cachedPlayerId);
            }
        }
    }

    private static List<ItemStack> snapshotHotbar(ServerPlayer player) {
        List<ItemStack> out = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            out.add(player.getInventory().getItem(i).copy());
        }
        return out;
    }

    private static void restoreHotbar(ServerPlayer player, List<ItemStack> snapshot, int selectedSlot) {
        if (player == null || snapshot == null || snapshot.size() < 9) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            player.getInventory().setItem(i, snapshot.get(i).copy());
        }
        player.getInventory().selected = Math.max(0, Math.min(8, selectedSlot));
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void purgeSelectionToolItemsOutsideHotbar(ServerPlayer player) {
        if (player == null) {
            return;
        }
        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int slot = 9; slot < size; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            // Keep all temporary selector items confined to hotbar to prevent loss/duplication exploits.
            if (isClaimToolStack(stack) || isPalletClaimToolStack(stack)) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                changed = true;
            }
        }
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static void installClaimToolHotbar(ServerPlayer player, boolean stockroomMode) {
        ItemStack wand = new ItemStack(Items.STICK);
        wand.enchant(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
        ItemStackDataCompat.setCustomName(wand, UbsTranslations.literal(stockroomMode ? "Stockroom Claim Wand" : "Shop Claim Wand")
                .withStyle(ChatFormatting.GOLD));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, wand, "wand");

        ItemStack add = new ItemStack(Items.LIME_CONCRETE);
        ItemStackDataCompat.setCustomName(add, UbsTranslations.literal("Add Region").withStyle(ChatFormatting.GREEN));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, add, CLAIM_TOOL_ADD);

        ItemStack remove = new ItemStack(Items.RED_CONCRETE);
        ItemStackDataCompat.setCustomName(remove, UbsTranslations.literal("Remove Region").withStyle(ChatFormatting.RED));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, remove, CLAIM_TOOL_REMOVE);

        ItemStack apply = new ItemStack(Items.PAPER);
        ItemStackDataCompat.setCustomName(apply, UbsTranslations.literal("Apply Selection").withStyle(ChatFormatting.YELLOW));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, apply, CLAIM_TOOL_APPLY);

        ItemStack clear = new ItemStack(Items.SPONGE);
        ItemStackDataCompat.setCustomName(clear, UbsTranslations.literal("Clear Selection").withStyle(ChatFormatting.GOLD));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, clear, CLAIM_TOOL_CLEAR);

        ItemStack overlay = new ItemStack(Items.ENDER_EYE);
        ItemStackDataCompat.setCustomName(overlay, UbsTranslations.literal("Toggle Claim Overlay").withStyle(ChatFormatting.AQUA));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, overlay, CLAIM_TOOL_OVERLAY);

        ItemStack locked = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        ItemStackDataCompat.setCustomName(locked, UbsTranslations.literal("Selection Mode Slot (Locked)").withStyle(ChatFormatting.DARK_GRAY));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, locked, CLAIM_TOOL_LOCK);

        ItemStack finish = new ItemStack(Items.BARRIER);
        ItemStackDataCompat.setCustomName(finish, UbsTranslations.literal("Finish Claim Tool").withStyle(ChatFormatting.YELLOW));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, finish, CLAIM_TOOL_FINISH);

        // Claim mode takes over all 9 hotbar slots; non-functional slots are locked placeholders.
        player.getInventory().setItem(0, wand.copy());
        player.getInventory().setItem(1, add.copy());
        player.getInventory().setItem(2, remove.copy());
        player.getInventory().setItem(3, apply.copy());
        player.getInventory().setItem(4, clear.copy());
        player.getInventory().setItem(5, overlay.copy());
        player.getInventory().setItem(6, locked.copy());
        player.getInventory().setItem(7, locked.copy());
        player.getInventory().setItem(8, finish.copy());
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void installPalletClaimToolHotbar(ServerPlayer player) {
        ItemStack add = new ItemStack(Items.LIME_CONCRETE);
        ItemStackDataCompat.setCustomName(add, UbsTranslations.literal("Add Delivery Pallet").withStyle(ChatFormatting.GREEN));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, add, PALLET_TOOL_ADD);

        ItemStack remove = new ItemStack(Items.RED_CONCRETE);
        ItemStackDataCompat.setCustomName(remove, UbsTranslations.literal("Remove Delivery Pallet").withStyle(ChatFormatting.RED));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, remove, PALLET_TOOL_REMOVE);

        ItemStack save = new ItemStack(Items.PAPER);
        ItemStackDataCompat.setCustomName(save, UbsTranslations.literal("Save & Exit").withStyle(ChatFormatting.YELLOW));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, save, PALLET_TOOL_SAVE);

        ItemStack cancel = new ItemStack(Items.BARRIER);
        ItemStackDataCompat.setCustomName(cancel, UbsTranslations.literal("Cancel & Exit").withStyle(ChatFormatting.GOLD));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, cancel, PALLET_TOOL_CANCEL);

        ItemStack locked = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        ItemStackDataCompat.setCustomName(locked, UbsTranslations.literal("Pallet Tool Slot (Locked)").withStyle(ChatFormatting.DARK_GRAY));
        ItemStackDataCompat.putCustomString(CLAIM_TOOL_ITEM_TAG, locked, PALLET_TOOL_LOCK);

        player.getInventory().setItem(0, add.copy());
        player.getInventory().setItem(1, remove.copy());
        player.getInventory().setItem(2, save.copy());
        player.getInventory().setItem(3, cancel.copy());
        for (int i = 4; i < 9; i++) {
            player.getInventory().setItem(i, locked.copy());
        }
        player.getInventory().selected = 0;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    private static void sendPalletClaimToolTutorial(ServerPlayer player) {
        if (player == null) {
            return;
        }
        player.sendSystemMessage(UbsTranslations.literal("§aDelivery pallet tool active."));
        player.sendSystemMessage(UbsTranslations.literal("§e1) Hold §aAdd Delivery Pallet§e or §cRemove Delivery Pallet§e."));
        player.sendSystemMessage(UbsTranslations.literal("§e2) Right/left click a pallet to queue changes."));
        player.sendSystemMessage(UbsTranslations.literal("§e3) Use §6Save & Exit§e to commit or §cCancel & Exit§e to discard."));
        pushShopAlert(
                player,
                "Delivery Pallets",
                "Delivery pallet tool active. Right/left click a pallet to queue changes, then use Save & Exit or Cancel & Exit.",
                DeliveryAlertPayload.AlertTone.INFO,
                5600
        );
    }

    public static ShopActionResult setStockroomNearPosition(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            UUID ownerId,
                                                            String dimensionId,
                                                            int x,
                                                            int y,
                                                            int z) {
        return setStockroomNearPosition(server, centralBank, ownerId, null, dimensionId, x, y, z);
    }

    public static ShopActionResult setStockroomNearPosition(MinecraftServer server,
                                                            CentralBank centralBank,
                                                            UUID ownerId,
                                                            UUID shopId,
                                                            String dimensionId,
                                                            int x,
                                                            int y,
                                                            int z) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "Create a shop first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(primary, ownerId, SHOP_ROLE_BUILDER)) {
            return new ShopActionResult(false, "You do not have build permission for this shop.");
        }
        ServerLevel level = server.getLevel(serverLevelKey(dimensionId));
        if (level == null) {
            return new ShopActionResult(false, "Stockroom dimension is not loaded.");
        }
        BlockPos origin = new BlockPos(x, y, z);
        BlockPos stockroomPos = findNearestContainer(level, origin, 12, 6);
        if (stockroomPos == null) {
            return new ShopActionResult(false, "No container found near this PC. Place a chest/barrel nearby.");
        }
        ShopActionResult result = addStockroomRegion(
                centralBank,
                ownerId,
                primary.contains(TAG_ID) ? primary.getUUID(TAG_ID) : null,
                level.dimension().location().toString(),
                stockroomPos,
                stockroomPos
        );
        if (!result.success()) {
            return result;
        }
        return new ShopActionResult(true, "Stockroom point claimed at " + level.dimension().location() + " " + stockroomPos + ".");
    }

    public static ShopActionResult setCheckoutTerminalNearPosition(MinecraftServer server,
                                                                   CentralBank centralBank,
                                                                   UUID ownerId,
                                                                   String dimensionId,
                                                                   int x,
                                                                   int y,
                                                                   int z) {
        return setCheckoutTerminalNearPosition(server, centralBank, ownerId, null, dimensionId, x, y, z);
    }

    public static ShopActionResult setCheckoutTerminalNearPosition(MinecraftServer server,
                                                                   CentralBank centralBank,
                                                                   UUID ownerId,
                                                                   UUID shopId,
                                                                   String dimensionId,
                                                                   int x,
                                                                   int y,
                                                                   int z) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "Create a shop first.");
        }
        if (!isOpActor(server, ownerId) && !hasShopRoleAtLeast(primary, ownerId, SHOP_ROLE_MANAGER)) {
            return new ShopActionResult(false, "You do not have POS management permission for this shop.");
        }
        UUID shopOwnerId = resolveShopOwnerIdFromTag(primary);
        ServerLevel level = server.getLevel(serverLevelKey(dimensionId));
        if (level == null) {
            return new ShopActionResult(false, "Checkout terminal dimension is not loaded.");
        }

        BlockPos origin = new BlockPos(x, y, z);
        BlockPos terminalPos = findNearestTerminal(level, origin, 16, 6);
        if (terminalPos == null) {
            return new ShopActionResult(false, "No payment terminal found near this PC.");
        }
        if (!(level.getBlockEntity(terminalPos) instanceof ShopTerminalBlockEntity terminal)) {
            return new ShopActionResult(false, "Selected checkout terminal is unavailable.");
        }
        if (!isInsideClaims(primary.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                level.dimension().location().toString(),
                terminalPos)) {
            return new ShopActionResult(false, "Checkout terminal must be inside one of your claimed shop plots.");
        }

        CompoundTag checkoutTerminal = new CompoundTag();
        checkoutTerminal.putString(TAG_DIM, normalizedDim(level.dimension().location().toString()));
        checkoutTerminal.putInt(TAG_X, terminalPos.getX());
        checkoutTerminal.putInt(TAG_Y, terminalPos.getY());
        checkoutTerminal.putInt(TAG_Z, terminalPos.getZ());
        primary.put(TAG_CHECKOUT_TERMINAL, checkoutTerminal);
        saveShopTag(centralBank, primary);

        if (terminal.getOwnerUuid() == null && shopOwnerId != null) {
            terminal.setOwner(shopOwnerId, primary.getString(TAG_OWNER_NAME));
        }
        return new ShopActionResult(true, "Checkout terminal set to " + terminalPosLabel(checkoutTerminal) + ".");
    }

    public static CheckoutTerminalTarget resolveCheckoutTerminal(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID ownerId) {
        return resolveCheckoutTerminal(server, centralBank, ownerId, null);
    }

    public static CheckoutTerminalTarget resolveCheckoutTerminal(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID ownerId,
                                                                 UUID cashierId) {
        return resolveCheckoutTerminal(server, centralBank, ownerId, null, cashierId);
    }

    public static CheckoutTerminalTarget resolveCheckoutTerminal(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID ownerId,
                                                                 UUID shopId,
                                                                 UUID cashierId) {
        if (server == null || centralBank == null || ownerId == null) {
            return null;
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            primary = getPrimaryShopTag(centralBank, ownerId);
        }
        if (primary == null) {
            return null;
        }
        CompoundTag checkout = resolveCheckoutTerminalTagForCashier(primary, cashierId);
        if (checkout == null || checkout.isEmpty()) {
            return null;
        }
        CheckoutTerminalTarget target = resolveCheckoutTerminalTargetFromTag(primary, checkout, server);
        if (target != null) {
            return target;
        }
        if (cashierId != null && primary.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            CompoundTag fallback = primary.getCompound(TAG_CHECKOUT_TERMINAL);
            if (!sameTerminalRef(checkout, fallback)) {
                return resolveCheckoutTerminalTargetFromTag(primary, fallback, server);
            }
        }
        return null;
    }

    private static CheckoutTerminalTarget resolveCheckoutTerminalTargetFromTag(CompoundTag primary,
                                                                               CompoundTag checkout,
                                                                               MinecraftServer server) {
        if (primary == null || checkout == null || server == null) {
            return null;
        }
        ServerLevel level = server.getLevel(serverLevelKey(checkout.getString(TAG_DIM)));
        if (level == null) {
            return null;
        }
        BlockPos pos = new BlockPos(checkout.getInt(TAG_X), checkout.getInt(TAG_Y), checkout.getInt(TAG_Z));
        if (!(level.getBlockEntity(pos) instanceof ShopTerminalBlockEntity terminal)) {
            return null;
        }
        if (!isInsideClaims(primary.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), level.dimension().location().toString(), pos)) {
            return null;
        }
        return new CheckoutTerminalTarget(
                primary.getString(TAG_NAME),
                level.dimension().location().toString(),
                pos,
                terminal
        );
    }

    private static boolean sameTerminalRef(CompoundTag a, CompoundTag b) {
        if (a == null || b == null) {
            return false;
        }
        return normalizedDim(a.getString(TAG_DIM)).equals(normalizedDim(b.getString(TAG_DIM)))
                && a.getInt(TAG_X) == b.getInt(TAG_X)
                && a.getInt(TAG_Y) == b.getInt(TAG_Y)
                && a.getInt(TAG_Z) == b.getInt(TAG_Z);
    }

    public static List<CashierSummary> collectCashiers(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID ownerId) {
        return collectCashiers(server, centralBank, ownerId, null);
    }

    public static List<CashierSummary> collectCashiers(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID ownerId,
                                                       UUID shopId) {
        List<CashierSummary> out = new ArrayList<>();
        if (server == null || centralBank == null || ownerId == null) {
            return out;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null) {
            return out;
        }
        UUID shopOwnerId = resolveShopOwnerIdFromTag(shop);
        if (shopOwnerId == null) {
            return out;
        }
        UUID resolvedShopId = shop.contains(TAG_ID) ? shop.getUUID(TAG_ID) : null;
        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        for (BankTellerEntity teller : collectCashierEntitiesInClaims(server, claims, shopOwnerId, resolvedShopId)) {
            if (teller == null) {
                continue;
            }
            BlockPos pos = teller.blockPosition();
            String dimensionId = teller.level().dimension().location().toString();
            UUID tellerShopId = teller.getShopId();
            if (resolvedShopId != null && tellerShopId == null) {
                teller.setShopId(resolvedShopId);
            }
            UUID employeeId = teller.getEmployeeId();
            if (employeeId == null) {
                employeeId = teller.getUUID();
                teller.setEmployeeId(employeeId);
            }
            CompoundTag linked = resolveLinkedTerminalTag(shop, teller.getUUID());
            String linkedLabel = linked == null ? "-" : terminalPosLabel(linked);
            String label = teller.getName().getString();
            if (label == null || label.isBlank()) {
                label = "Cashier";
            }
            out.add(new CashierSummary(
                    teller.getUUID(),
                    employeeId,
                    resolvedShopId,
                    label,
                    dimensionId,
                    pos.immutable(),
                    linked != null,
                    linkedLabel
            ));
        }
        out.sort(Comparator.comparing((CashierSummary it) -> it.dimensionId().toLowerCase(Locale.ROOT))
                .thenComparingInt(it -> it.pos().getY())
                .thenComparingInt(it -> it.pos().getZ())
                .thenComparingInt(it -> it.pos().getX()));
        return out;
    }

    public static ShopActionResult cashierReport(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 UUID ownerId) {
        return cashierReport(server, centralBank, ownerId, null);
    }

    public static ShopActionResult cashierReport(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 UUID ownerId,
                                                 UUID shopId) {
        List<CashierSummary> cashiers = collectCashiers(server, centralBank, ownerId, shopId);
        if (cashiers.isEmpty()) {
            return new ShopActionResult(false, "No cashier employees found inside your shop claims.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Cashiers (" + cashiers.size() + ")");
        for (int i = 0; i < cashiers.size(); i++) {
            CashierSummary c = cashiers.get(i);
            lines.add((i + 1) + ") " + c.label()
                    + " | employee " + c.employeeId()
                    + " | " + c.cashierId()
                    + " | " + normalizedDim(c.dimensionId())
                    + " (" + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ() + ")"
                    + " | Terminal: " + (c.linkedTerminal() ? c.linkedTerminalLabel() : "default"));
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult beginCashierTerminalSelection(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID ownerId,
                                                                 ServerPlayer player,
                                                                 String cashierSelection) {
        return beginCashierTerminalSelection(server, centralBank, ownerId, null, player, cashierSelection);
    }

    public static ShopActionResult beginCashierTerminalSelection(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID ownerId,
                                                                 UUID shopId,
                                                                 ServerPlayer player,
                                                                 String cashierSelection) {
        if (server == null || centralBank == null || ownerId == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        String raw = cashierSelection == null ? "" : cashierSelection.trim();
        if ("CANCEL".equalsIgnoreCase(raw)) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            player.sendSystemMessage(UbsTranslations.literal("§eCashier-terminal link mode cancelled."));
            pushShopAlert(player, "Cashier Link", "Cashier-terminal link mode cancelled.", DeliveryAlertPayload.AlertTone.WARNING, 5000);
            return new ShopActionResult(true, "Cashier-terminal link mode cancelled.");
        }
        if (raw.isBlank() && CASHIER_TERMINAL_SELECTIONS.containsKey(player.getUUID())) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            player.sendSystemMessage(UbsTranslations.literal("§eCashier-terminal link mode cancelled."));
            pushShopAlert(player, "Cashier Link", "Cashier-terminal link mode cancelled.", DeliveryAlertPayload.AlertTone.WARNING, 5000);
            return new ShopActionResult(true, "Cashier-terminal link mode cancelled.");
        }

        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!player.hasPermissions(3) && !hasShopRoleAtLeast(shop, ownerId, SHOP_ROLE_MANAGER)) {
            return new ShopActionResult(false, "You do not have POS management permission for this shop.");
        }
        List<CashierSummary> cashiers = collectCashiers(server, centralBank, ownerId, shopId);
        if (cashiers.isEmpty()) {
            return new ShopActionResult(false, "No cashier employees found inside your shop claims.");
        }
        CashierSummary selected = raw.isBlank() ? null : selectCashier(cashiers, raw);
        if (!raw.isBlank() && selected == null) {
            return new ShopActionResult(false, "Cashier not found. Use an employee ID, cashier ID, or index.");
        }
        UUID resolvedShopId = shop.getUUID(TAG_ID);
        CASHIER_TERMINAL_SELECTIONS.put(player.getUUID(), new CashierTerminalSelection(
                player.getUUID(),
                ownerId,
                resolvedShopId,
                selected == null ? null : selected.cashierId(),
                player.serverLevel().getGameTime()
        ));

        player.sendSystemMessage(UbsTranslations.literal("Link mode started for shop ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(shop.getString(TAG_NAME)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(".").withStyle(ChatFormatting.AQUA)));
        if (selected == null) {
            player.sendSystemMessage(UbsTranslations.literal("§7Step 1/2: Right-click a cashier NPC to select it."));
        } else {
            player.sendSystemMessage(UbsTranslations.literal("Cashier selected: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(selected.label()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + selected.employeeId() + ")").withStyle(ChatFormatting.GRAY)));
        }
        player.sendSystemMessage(UbsTranslations.literal("§7Step 2/2: Right-click your payment terminal to complete the link."));
        player.sendSystemMessage(UbsTranslations.literal("§8Cancel: shift-right-click the cashier/terminal or run the link action again."));
        pushShopAlert(
                player,
                "Cashier Link",
                selected == null
                        ? "Cashier-terminal link mode started. Right-click a cashier, then right-click a payment terminal."
                        : "Cashier selected. Right-click a payment terminal to complete linking.",
                DeliveryAlertPayload.AlertTone.INFO,
                5600
        );

        return new ShopActionResult(true, selected == null
                ? "Cashier-terminal link mode started. Right-click a cashier, then right-click a payment terminal."
                : "Cashier selected. Right-click a payment terminal to finish linking.");
    }

    public static boolean hasCashierTerminalSelection(UUID playerId) {
        return playerId != null && CASHIER_TERMINAL_SELECTIONS.containsKey(playerId);
    }

    public static ShopActionResult cancelCashierTerminalSelection(ServerPlayer player, String reason) {
        if (player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CashierTerminalSelection removed = CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
        if (removed == null) {
            return new ShopActionResult(false, "No cashier-terminal link mode is active.");
        }
        String message = (reason == null || reason.isBlank())
                ? "Cashier-terminal link mode cancelled."
                : reason.trim();
        return new ShopActionResult(true, message);
    }

    public static ShopActionResult applyCashierTerminalSelection(ServerPlayer player,
                                                                 CentralBank centralBank,
                                                                 BankTellerEntity cashier) {
        if (player == null || centralBank == null || cashier == null || !cashier.isCashier()) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CashierTerminalSelection selection = activeCashierTerminalSelection(player);
        if (selection == null) {
            return new ShopActionResult(false, "No cashier terminal selection is active.");
        }
        CompoundTag shop = resolveShopTag(centralBank, selection.ownerId(), selection.shopId());
        if (shop == null || !shop.contains(TAG_ID)) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            return new ShopActionResult(false, "Shop is no longer available.");
        }
        if (!player.hasPermissions(3) && !hasShopRoleAtLeast(shop, selection.ownerId(), SHOP_ROLE_MANAGER)) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            return new ShopActionResult(false, "You do not have POS management permission for this shop.");
        }
        UUID shopOwnerId = resolveShopOwnerIdFromTag(shop);
        if (shopOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }
        if (!shopOwnerId.equals(cashier.getOwnerUUID()) && !player.hasPermissions(3)) {
            return new ShopActionResult(false, "That cashier does not belong to your selected shop.");
        }
        BlockPos pos = cashier.blockPosition();
        String cashierDim = cashier.level().dimension().location().toString();
        if (!isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), cashierDim, pos)) {
            return new ShopActionResult(false, "Selected cashier must stand inside your claimed shop plot.");
        }
        if (cashier.getShopId() == null) {
            cashier.setShopId(selection.shopId());
        }
        CashierTerminalSelection updated = new CashierTerminalSelection(
                selection.playerId(),
                selection.ownerId(),
                selection.shopId(),
                cashier.getUUID(),
                player.serverLevel().getGameTime()
        );
        CASHIER_TERMINAL_SELECTIONS.put(player.getUUID(), updated);
        return new ShopActionResult(true, "Cashier selected (" + cashier.getUUID() + "). Now right-click a payment terminal.");
    }

    public static ShopActionResult applyCashierTerminalSelection(ServerPlayer player,
                                                                 CentralBank centralBank,
                                                                 ServerLevel level,
                                                                 BlockPos terminalPos,
                                                                 ShopTerminalBlockEntity terminal) {
        if (player == null || centralBank == null || level == null || terminalPos == null || terminal == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CashierTerminalSelection selection = CASHIER_TERMINAL_SELECTIONS.get(player.getUUID());
        if (selection == null) {
            return new ShopActionResult(false, "No cashier terminal selection is active.");
        }
        selection = activeCashierTerminalSelection(player);
        if (selection == null) {
            return new ShopActionResult(false, "Cashier-terminal link mode timed out.");
        }
        if (selection.cashierId() == null) {
            return new ShopActionResult(false, "Select a cashier first by right-clicking it.");
        }

        CompoundTag shop = resolveShopTag(centralBank, selection.ownerId(), selection.shopId());
        if (shop == null || !shop.contains(TAG_ID)) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            return new ShopActionResult(false, "Shop is no longer available.");
        }
        if (!player.hasPermissions(3) && !hasShopRoleAtLeast(shop, selection.ownerId(), SHOP_ROLE_MANAGER)) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            return new ShopActionResult(false, "You do not have POS management permission for this shop.");
        }
        UUID shopOwnerId = resolveShopOwnerIdFromTag(shop);
        if (shopOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }
        UUID terminalOwner = terminal.getOwnerUuid();
        if (terminalOwner != null && !terminalOwner.equals(shopOwnerId) && !player.hasPermissions(3)) {
            return new ShopActionResult(false, "This terminal belongs to a different owner.");
        }

        if (!isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), level.dimension().location().toString(), terminalPos)) {
            return new ShopActionResult(false, "Selected terminal must be inside your claimed shop plot.");
        }

        BankTellerEntity selectedCashier = findCashierById(level.getServer(), selection.cashierId());
        if (selectedCashier == null || !selectedCashier.isCashier()) {
            CashierTerminalSelection updated = new CashierTerminalSelection(
                    selection.playerId(),
                    selection.ownerId(),
                    selection.shopId(),
                    null,
                    level.getGameTime()
            );
            CASHIER_TERMINAL_SELECTIONS.put(player.getUUID(), updated);
            return new ShopActionResult(false, "Selected cashier is unavailable. Right-click a cashier again.");
        }
        if (!shopOwnerId.equals(selectedCashier.getOwnerUUID()) && !player.hasPermissions(3)) {
            return new ShopActionResult(false, "Selected cashier does not belong to your shop.");
        }

        CompoundTag previousLinkedTerminal = resolveLinkedTerminalTag(shop, selection.cashierId());
        CompoundTag newlyLinkedTerminal = new CompoundTag();
        newlyLinkedTerminal.putString(TAG_DIM, level.dimension().location().toString());
        newlyLinkedTerminal.putInt(TAG_X, terminalPos.getX());
        newlyLinkedTerminal.putInt(TAG_Y, terminalPos.getY());
        newlyLinkedTerminal.putInt(TAG_Z, terminalPos.getZ());

        if (terminal.getOwnerUuid() == null) {
            terminal.setOwner(shopOwnerId, shop.getString(TAG_OWNER_NAME));
        }
        UUID settlementAccount = resolveSettlementAccountId(
                centralBank,
                shopOwnerId,
                selection.shopId(),
                terminal.getMerchantAccountId()
        );
        terminal.updateConfig(
                shop.getString(TAG_NAME),
                terminal.getPriceDollars(),
                settlementAccount,
                terminal.isPulseOnSuccess(),
                terminal.isPulseOnFailure(),
                terminal.isPulseOnIdle(),
                terminal.getSuccessPulseTicks(),
                terminal.getFailurePulseTicks(),
                terminal.getIdlePulseStrength()
        );

        setLinkedTerminalTag(
                shop,
                selection.cashierId(),
                level.dimension().location().toString(),
                terminalPos
        );

        // If the global checkout terminal was implicitly pointing at the previous cashier-specific terminal,
        // move it forward to the newly linked terminal to avoid stale "linked terminal" locks.
        if (previousLinkedTerminal != null
                && !sameTerminalRef(previousLinkedTerminal, newlyLinkedTerminal)
                && shop.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)
                && sameTerminalRef(shop.getCompound(TAG_CHECKOUT_TERMINAL), previousLinkedTerminal)) {
            shop.put(TAG_CHECKOUT_TERMINAL, newlyLinkedTerminal.copy());
        }

        saveShopTag(centralBank, shop);
        CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
        CompoundTag linked = new CompoundTag();
        linked.putString(TAG_DIM, level.dimension().location().toString());
        linked.putInt(TAG_X, terminalPos.getX());
        linked.putInt(TAG_Y, terminalPos.getY());
        linked.putInt(TAG_Z, terminalPos.getZ());
        String message = "Cashier-terminal link complete at " + terminalPosLabel(linked) + ".";
        if (previousLinkedTerminal != null && !sameTerminalRef(previousLinkedTerminal, linked)) {
            message += " Previous terminal link was removed.";
        }
        return new ShopActionResult(true, message);
    }

    public static ShopActionResult hireCashierNpc(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  ServerPlayer owner,
                                                  UUID shopId) {
        if (server == null || centralBank == null || owner == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, owner.getUUID(), shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (!owner.hasPermissions(3) && !hasShopRoleAtLeast(shop, owner.getUUID(), SHOP_ROLE_MANAGER)) {
            return new ShopActionResult(false, "You do not have employee management permission for this shop.");
        }
        UUID shopOwnerId = resolveShopOwnerIdFromTag(shop);
        if (shopOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }
        UUID resolvedShopId = shop.getUUID(TAG_ID);
        int level = Math.max(1, shop.getInt(TAG_LEVEL));
        int maxAllowed = maxCashierSpawnEggsForLevel(level);
        int activeCashiers = collectCashiers(server, centralBank, shopOwnerId, resolvedShopId).size();
        int pendingEggs = countOwnedCashierEggsForShop(owner, resolvedShopId);
        int currentUsage = Math.max(0, activeCashiers) + Math.max(0, pendingEggs);
        if (currentUsage >= maxAllowed) {
            return new ShopActionResult(
                    false,
                    "Cashier limit reached for level " + level
                            + " (" + currentUsage + "/" + maxAllowed + "). "
                            + "Active cashiers: " + activeCashiers + ", unplaced cashier eggs: " + pendingEggs
                            + ". Level up your shop to unlock more."
            );
        }

        UUID employeeId = UUID.randomUUID();

        ItemStack egg = new ItemStack(ModItems.CASHIER_SPAWN_EGG.get());
        CompoundTag custom = new CompoundTag();
        // Persist actual shop owner so cashier ownership stays consistent
        // even when a delegated manager issues the spawn egg.
        custom.putUUID(CashierSpawnEggItem.TAG_OWNER, shopOwnerId);
        custom.putUUID(CashierSpawnEggItem.TAG_SHOP_ID, resolvedShopId);
        custom.putUUID(CashierSpawnEggItem.TAG_EMPLOYEE_ID, employeeId);
        custom.putString(CashierSpawnEggItem.TAG_SHOP_NAME, shop.getString(TAG_NAME));
        ItemStackDataCompat.setCustomData(egg, custom);
        ItemStackDataCompat.setCustomName(
                egg,
                Component.empty()
                        .append(Component.literal("[" + shop.getString(TAG_NAME) + "] ").withStyle(ChatFormatting.AQUA))
                        .append(UbsTranslations.literal("Cashier Spawn Egg").withStyle(ChatFormatting.AQUA))
        );

        if (!owner.getInventory().add(egg)) {
            owner.drop(egg, false);
        }
        owner.getInventory().setChanged();
        owner.containerMenu.broadcastChanges();
        return new ShopActionResult(true,
                "Hired cashier employee " + employeeId
                        + ". Spawn egg added to inventory (or dropped if full).");
    }

    private static int countOwnedCashierEggsForShop(ServerPlayer owner, UUID shopId) {
        if (owner == null || shopId == null) {
            return 0;
        }
        int total = 0;
        int size = owner.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = owner.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.is(ModItems.CASHIER_SPAWN_EGG.get())) {
                continue;
            }
            CompoundTag custom = ItemStackDataCompat.getCustomData(stack);
            if (custom == null || !custom.hasUUID(CashierSpawnEggItem.TAG_SHOP_ID)) {
                continue;
            }
            if (!shopId.equals(custom.getUUID(CashierSpawnEggItem.TAG_SHOP_ID))) {
                continue;
            }
            total += Math.max(1, stack.getCount());
        }
        return Math.max(0, total);
    }

    public static ShopActionResult listEmployeesReport(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID ownerId,
                                                       UUID shopId) {
        List<CashierSummary> cashiers = collectCashiers(server, centralBank, ownerId, shopId);
        if (cashiers.isEmpty()) {
            return new ShopActionResult(true, "Employees\nNo active cashier employees found.");
        }
        List<String> lines = new ArrayList<>();
        lines.add("Employees (" + cashiers.size() + ")");
        lines.add("@employees.count=" + cashiers.size());
        for (int i = 0; i < cashiers.size(); i++) {
            CashierSummary c = cashiers.get(i);
            String terminal = c.linkedTerminal() ? c.linkedTerminalLabel() : "default";
            lines.add("@employee=" + (i + 1)
                    + "|" + c.employeeId()
                    + "|" + c.cashierId()
                    + "|" + sanitizeTokenText(c.label())
                    + "|" + normalizedDim(c.dimensionId())
                    + "|" + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ()
                    + "|" + sanitizeTokenText(terminal));
            lines.add((i + 1) + ") " + c.label()
                    + " | employee " + c.employeeId()
                    + " | entity " + c.cashierId()
                    + " | " + normalizedDim(c.dimensionId())
                    + " (" + c.pos().getX() + "," + c.pos().getY() + "," + c.pos().getZ() + ")"
                    + " | terminal " + terminal);
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult fireEmployee(MinecraftServer server,
                                                CentralBank centralBank,
                                                UUID ownerId,
                                                UUID shopId,
                                                String employeeSelection) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        List<CashierSummary> cashiers = collectCashiers(server, centralBank, ownerId, shopId);
        CashierSummary selected = selectCashier(cashiers, employeeSelection);
        if (selected == null) {
            ShopActionResult ghostResult = removeGhostCashierLink(server, centralBank, shop, employeeSelection);
            if (ghostResult != null) {
                return ghostResult;
            }
            return new ShopActionResult(false, cashiers.isEmpty()
                    ? "No cashier employees found in loaded chunks."
                    : "Employee not found. Use index, employee ID, or cashier entity ID.");
        }

        boolean removed = false;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(selected.cashierId());
            if (entity instanceof BankTellerEntity teller && teller.isCashier()) {
                teller.discard();
                removed = true;
            }
        }
        if (!removed) {
            // Never delete the persisted terminal link while the cashier entity is not
            // observably present: an unloaded cashier would otherwise lose its setup link.
            return new ShopActionResult(false,
                    "That cashier is not in a loaded area right now. Load the shop chunks and try again.");
        }
        removeLinkedTerminalTag(shop, selected.cashierId());
        saveShopTag(centralBank, shop);
        return new ShopActionResult(
                true,
                "Fired employee " + selected.label() + " (" + selected.employeeId() + ")."
        );
    }

    private static ShopActionResult removeGhostCashierLink(MinecraftServer server,
                                                           CentralBank centralBank,
                                                           CompoundTag shop,
                                                           String selection) {
        UUID cashierId;
        try {
            cashierId = UUID.fromString(selection == null ? "" : selection.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        CompoundTag linked = resolveLinkedTerminalTag(shop, cashierId);
        if (linked == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(cashierId) != null) {
                return new ShopActionResult(false,
                        "That cashier still exists in a loaded area outside the shop. Remove it there instead.");
            }
        }
        ServerLevel linkLevel = server.getLevel(serverLevelKey(linked.getString(TAG_DIM)));
        BlockPos linkPos = new BlockPos(linked.getInt(TAG_X), linked.getInt(TAG_Y), linked.getInt(TAG_Z));
        if (linkLevel == null || !linkLevel.areEntitiesLoaded(
                net.minecraft.world.level.ChunkPos.asLong(linkPos))) {
            // The linked area is not observable, so the cashier may simply be unloaded;
            // never delete the persisted link on unobservable state.
            return new ShopActionResult(false,
                    "That cashier is not in a loaded area right now. Load the shop chunks and try again.");
        }
        removeLinkedTerminalTag(shop, cashierId);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true,
                "Removed the terminal link for missing cashier " + cashierId + ".");
    }

    public static void unlinkCashierTerminal(MinecraftServer server,
                                             UUID ownerId,
                                             UUID shopId,
                                             UUID cashierId) {
        if (server == null || ownerId == null || shopId == null || cashierId == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null) {
            return;
        }
        removeLinkedTerminalTag(shop, cashierId);
        saveShopTag(centralBank, shop);
    }

    public static ShopActionResult listOwnerAccountsForSettlement(CentralBank centralBank,
                                                                  UUID ownerId,
                                                                  UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        Map<UUID, AccountHolder> ownerAccounts = centralBank.SearchForAccount(ownerId);
        if (ownerAccounts == null || ownerAccounts.isEmpty()) {
            return new ShopActionResult(false, "No owner account found. Create an account first.");
        }
        List<AccountHolder> sorted = new ArrayList<>();
        for (AccountHolder account : ownerAccounts.values()) {
            if (account != null && ownerId.equals(account.getPlayerUUID())) {
                sorted.add(account);
            }
        }
        if (sorted.isEmpty()) {
            return new ShopActionResult(false, "No owner account found. Create an account first.");
        }
        sorted.sort(Comparator
                .comparing(AccountHolder::isPrimaryAccount).reversed()
                .thenComparing(a -> a.getDateOfCreation() == null
                        ? ""
                        : a.getDateOfCreation().toString(), String.CASE_INSENSITIVE_ORDER));

        List<String> lines = new ArrayList<>();
        lines.add("Select Settlement Account");
        lines.add("@owner_accounts.count=" + sorted.size());
        for (int i = 0; i < sorted.size(); i++) {
            AccountHolder account = sorted.get(i);
            AccountTypes type = account.getAccountType();
            String typeLabel = type == null ? "Account" : type.label;
            String bankName = "-";
            if (account.getBankId() != null) {
                var bank = centralBank.getBank(account.getBankId());
                if (bank != null && bank.getBankName() != null && !bank.getBankName().isBlank()) {
                    bankName = bank.getBankName().trim();
                }
            }
            long balanceCents = account.getBalance() == null
                    ? 0L
                    : account.getBalance().movePointRight(2).longValue();
            lines.add("@owner_account=" + (i + 1)
                    + "|" + account.getAccountUUID()
                    + "|" + sanitizeTokenText(typeLabel)
                    + "|" + sanitizeTokenText(bankName)
                    + "|" + balanceCents
                    + "|" + (account.isPrimaryAccount() ? "1" : "0"));
            lines.add((i + 1) + ") "
                    + (account.isPrimaryAccount() ? "[PRIMARY] " : "")
                    + typeLabel + " | " + bankName
                    + " | " + account.getAccountUUID()
                    + " | $" + ShelfPrice.abbreviateFromCents(balanceCents));
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult withdrawCashVaultAmount(CentralBank centralBank,
                                                           UUID ownerId,
                                                           UUID shopId,
                                                           ServerPlayer ownerPlayer,
                                                           String amountInput) {
        if (centralBank == null || ownerId == null || ownerPlayer == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        long cents = parseMoneyToCents(amountInput);
        if (cents <= 0L) {
            return new ShopActionResult(false, "Enter a withdrawal amount greater than $0.00.");
        }
        if (cents > Integer.MAX_VALUE) {
            return new ShopActionResult(false, "Amount is too large.");
        }
        int[] vault = readCashVaultCounts(shop);
        int[] plan = DollarBills.findCashDepositPlan((int) cents, vault);
        if (plan == null) {
            return new ShopActionResult(false, "Vault cannot make that exact amount with current tender.");
        }
        applyVaultWithdrawalPlan(shop, plan);
        saveShopTag(centralBank, shop);
        DollarBills.giveCash(ownerPlayer, plan);
        return new ShopActionResult(true, "Withdrew $" + DollarBills.formatCents((int) cents)
                + " from cash vault. Dispensed: " + DollarBills.formatCashPlan(plan));
    }

    public static ShopActionResult withdrawCashVaultPlan(CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId,
                                                         ServerPlayer ownerPlayer,
                                                         String planInput) {
        if (centralBank == null || ownerId == null || ownerPlayer == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        int[] requested = parseVaultWithdrawPlan(planInput);
        if (requested == null) {
            return new ShopActionResult(false, "Invalid bill/coin plan format.");
        }
        long total = computeVaultTotalCents(requested);
        if (total <= 0L) {
            return new ShopActionResult(false, "Select at least one bill/coin to withdraw.");
        }
        int[] current = readCashVaultCounts(shop);
        for (int i = 0; i < requested.length && i < current.length; i++) {
            if (requested[i] > current[i]) {
                return new ShopActionResult(false, "Requested tender exceeds vault stock for $" + DollarBills.formatCents(
                        DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i]) + ".");
            }
        }
        applyVaultWithdrawalPlan(shop, requested);
        saveShopTag(centralBank, shop);
        DollarBills.giveCash(ownerPlayer, requested);
        return new ShopActionResult(true,
                "Withdrew selected legal tender from vault ($"
                        + ShelfPrice.abbreviateFromCents(total)
                        + "). Dispensed: " + DollarBills.formatCashPlan(requested));
    }

    public static ShopActionResult setSettlementAccount(CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        String accountSelection) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        String raw = accountSelection == null ? "" : accountSelection.trim();
        if (raw.isBlank()) {
            return new ShopActionResult(false, "Enter an account id or PRIMARY.");
        }
        UUID settlementAccount = null;
        if ("PRIMARY".equalsIgnoreCase(raw)) {
            var ownerAccounts = centralBank.SearchForAccount(ownerId);
            for (AccountHolder account : ownerAccounts.values()) {
                if (account != null && account.isPrimaryAccount()) {
                    settlementAccount = account.getAccountUUID();
                    break;
                }
            }
            if (settlementAccount == null && !ownerAccounts.isEmpty()) {
                settlementAccount = ownerAccounts.values().iterator().next().getAccountUUID();
            }
            if (settlementAccount == null) {
                return new ShopActionResult(false, "No owner account found. Create an account first.");
            }
        } else {
            Map<UUID, AccountHolder> ownerAccounts = centralBank.SearchForAccount(ownerId);
            if (ownerAccounts != null && !ownerAccounts.isEmpty()) {
                try {
                    int index = Integer.parseInt(raw);
                    if (index >= 1 && index <= ownerAccounts.size()) {
                        List<AccountHolder> sorted = new ArrayList<>();
                        for (AccountHolder account : ownerAccounts.values()) {
                            if (account != null && ownerId.equals(account.getPlayerUUID())) {
                                sorted.add(account);
                            }
                        }
                        sorted.sort(Comparator
                                .comparing(AccountHolder::isPrimaryAccount).reversed()
                                .thenComparing(a -> a.getDateOfCreation() == null
                                        ? ""
                                        : a.getDateOfCreation().toString(), String.CASE_INSENSITIVE_ORDER));
                        if (index <= sorted.size()) {
                            settlementAccount = sorted.get(index - 1).getAccountUUID();
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (settlementAccount == null) {
                try {
                    settlementAccount = UUID.fromString(raw);
                } catch (IllegalArgumentException ex) {
                    return new ShopActionResult(false, "Account id must be a valid UUID, account index, or PRIMARY.");
                }
            }
            AccountHolder account = centralBank.SearchForAccountByAccountId(settlementAccount);
            if (account == null || !ownerId.equals(account.getPlayerUUID())) {
                return new ShopActionResult(false, "Settlement account must belong to the shop owner.");
            }
        }

        shop.putUUID(TAG_SETTLEMENT_ACCOUNT_ID, settlementAccount);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Settlement account set to " + settlementAccount + ".");
    }

    public static UUID resolveSettlementAccountId(CentralBank centralBank,
                                                  UUID ownerId,
                                                  UUID shopId,
                                                  UUID fallbackAccountId) {
        if (centralBank == null || ownerId == null) {
            return fallbackAccountId;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_SETTLEMENT_ACCOUNT_ID)) {
            return fallbackAccountId;
        }
        UUID configured = shop.getUUID(TAG_SETTLEMENT_ACCOUNT_ID);
        if (configured == null) {
            return fallbackAccountId;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(configured);
        if (account == null || !ownerId.equals(account.getPlayerUUID())) {
            return fallbackAccountId;
        }
        return configured;
    }

    public static ShopActionResult financeReport(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 UUID ownerId,
                                                 UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID resolvedShopId = shop.getUUID(TAG_ID);
        UUID settlementId = resolveSettlementAccountId(centralBank, ownerId, resolvedShopId, null);
        AccountHolder settlement = settlementId == null ? null : centralBank.SearchForAccountByAccountId(settlementId);
        long cashTxCount = Math.max(0L, shop.getLong(TAG_METRIC_CASH_TX_COUNT));
        long cashTotalCents = Math.max(0L, shop.getLong(TAG_METRIC_CASH_TOTAL_CENTS));
        long cardTxCount = Math.max(0L, shop.getLong(TAG_METRIC_TERMINAL_TX_COUNT));
        long cardTotalCents = Math.max(0L, shop.getLong(TAG_METRIC_TERMINAL_TOTAL_CENTS));
        int cashCustomers = decodeUuidStringList(shop.getList(TAG_METRIC_CASH_UNIQUE_PLAYERS, Tag.TAG_STRING)).size();
        int cardCustomers = decodeUuidStringList(shop.getList(TAG_METRIC_TERMINAL_UNIQUE_PLAYERS, Tag.TAG_STRING)).size();

        int[] vault = readCashVaultCounts(shop);
        long vaultTotalCents = computeVaultTotalCents(vault);
        String checkoutLabel = "-";
        UUID checkoutMerchantId = null;
        if (server != null && shop.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            CheckoutTerminalTarget target = resolveCheckoutTerminal(server, centralBank, ownerId, null);
            if (target != null) {
                checkoutLabel = normalizedDim(target.dimensionId()) + " ("
                        + target.pos().getX() + "," + target.pos().getY() + "," + target.pos().getZ() + ")";
                checkoutMerchantId = target.terminal().getMerchantAccountId();
            } else {
                checkoutLabel = terminalPosLabel(shop.getCompound(TAG_CHECKOUT_TERMINAL));
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Finance");
        lines.add("@finance.settlement_account_id=" + (settlementId == null ? "" : settlementId));
        lines.add("@finance.checkout_account_id=" + (checkoutMerchantId == null ? "" : checkoutMerchantId));
        lines.add("@finance.checkout_terminal=" + sanitizeTokenText(checkoutLabel));
        lines.add("@finance.cash_tx_count=" + cashTxCount);
        lines.add("@finance.cash_total_cents=" + cashTotalCents);
        lines.add("@finance.cash_customers=" + cashCustomers);
        lines.add("@finance.terminal_tx_count=" + cardTxCount);
        lines.add("@finance.terminal_total_cents=" + cardTotalCents);
        lines.add("@finance.terminal_customers=" + cardCustomers);
        lines.add("@finance.vault_total_cents=" + vaultTotalCents);

        lines.add("Settlement account: " + (settlementId == null ? "-" : settlementId.toString()));
        if (settlement != null) {
            lines.add("Settlement balance: $" + ShelfPrice.abbreviateFromCents(settlement.getBalance().movePointRight(2).longValue()));
        }
        lines.add("Checkout terminal: " + checkoutLabel);
        lines.add("Checkout account: " + (checkoutMerchantId == null ? "-" : checkoutMerchantId));
        lines.add("Cash tx: " + cashTxCount + " | Customers: " + cashCustomers
                + " | Total: $" + ShelfPrice.abbreviateFromCents(cashTotalCents));
        lines.add("Card/terminal tx: " + cardTxCount + " | Customers: " + cardCustomers
                + " | Total: $" + ShelfPrice.abbreviateFromCents(cardTotalCents));
        lines.add("Cash vault total: $" + ShelfPrice.abbreviateFromCents(vaultTotalCents));
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult cashVaultReport(CentralBank centralBank,
                                                   UUID ownerId,
                                                   UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        int[] vault = readCashVaultCounts(shop);
        long totalCents = computeVaultTotalCents(vault);
        List<String> lines = new ArrayList<>();
        lines.add("Cash Vault");
        lines.add("@vault.total_cents=" + totalCents);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            if (i > 0) {
                encoded.append(',');
            }
            encoded.append(DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i]).append(':').append(Math.max(0, vault[i]));
        }
        lines.add("@vault.counts=" + encoded);
        for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
            int count = Math.max(0, vault[i]);
            lines.add("$" + DollarBills.formatCents(DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i]) + " x " + count);
        }
        lines.add("Total legal tender: $" + ShelfPrice.abbreviateFromCents(totalCents));
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static void recordCashVaultDeposit(CentralBank centralBank,
                                              UUID ownerId,
                                              UUID shopId,
                                              int[] depositedCounts) {
        if (centralBank == null || ownerId == null || depositedCounts == null || depositedCounts.length == 0) {
            return;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return;
        }
        int[] current = readCashVaultCounts(shop);
        boolean changed = false;
        for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length && i < depositedCounts.length; i++) {
            int add = Math.max(0, depositedCounts[i]);
            if (add <= 0) {
                continue;
            }
            long next = (long) current[i] + add;
            current[i] = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
            changed = true;
        }
        if (!changed) {
            return;
        }
        shop.putIntArray(TAG_CASH_VAULT_COUNTS, current);
        saveShopTag(centralBank, shop);
    }

    public static void recordPaymentMethod(CentralBank centralBank,
                                           UUID ownerId,
                                           UUID shopId,
                                           UUID payerId,
                                           boolean cash,
                                           long amountCents) {
        if (centralBank == null || ownerId == null || amountCents <= 0L) {
            return;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return;
        }
        String txTag = cash ? TAG_METRIC_CASH_TX_COUNT : TAG_METRIC_TERMINAL_TX_COUNT;
        String totalTag = cash ? TAG_METRIC_CASH_TOTAL_CENTS : TAG_METRIC_TERMINAL_TOTAL_CENTS;
        String uniqueTag = cash ? TAG_METRIC_CASH_UNIQUE_PLAYERS : TAG_METRIC_TERMINAL_UNIQUE_PLAYERS;

        long txCount = Math.max(0L, shop.getLong(txTag));
        txCount = safeAdd(txCount, 1L);
        long total = Math.max(0L, shop.getLong(totalTag));
        total = safeAdd(total, Math.max(0L, amountCents));
        shop.putLong(txTag, txCount);
        shop.putLong(totalTag, total);

        if (payerId != null) {
            ListTag unique = shop.getList(uniqueTag, Tag.TAG_STRING);
            if (!containsUuidString(unique, payerId)) {
                unique.add(net.minecraft.nbt.StringTag.valueOf(payerId.toString()));
                shop.put(uniqueTag, unique);
            }
        }
        saveShopTag(centralBank, shop);
    }

    public static int maxAssignedOrderPalletsForLevel(int level) {
        int safeLevel = effectiveLevelForScaling(level, Config.SHOP_LEVEL_SCALE_DELIVERY_PALLET_LIMIT.get());
        int byLevel = 1 + safeLevel;
        int configuredMax = Math.max(1, Config.SHOP_MAX_ASSIGNED_ORDER_PALLETS_PER_SHOP.get());
        return Math.min(configuredMax, byLevel);
    }

    public static int maxActiveCourierOrders() {
        return Math.max(1, Config.SHOP_MAX_ACTIVE_COURIER_ORDERS.get());
    }

    private static int maxActiveWebshopOrders() {
        return Math.max(1, Config.SHOP_WEBSHOP_MAX_ACTIVE_ORDERS.get());
    }

    private static int webshopDefaultEtaSeconds() {
        return Math.max(5, Config.SHOP_WEBSHOP_DEFAULT_ETA_SECONDS.get());
    }

    private static int webshopExpediteEtaSeconds() {
        return Math.max(3, Config.SHOP_WEBSHOP_EXPEDITE_ETA_SECONDS.get());
    }

    private static int webshopRetryDelaySeconds() {
        return Math.max(1, Config.SHOP_WEBSHOP_RETRY_DELAY_SECONDS.get());
    }

    private static int webshopMaxRetryAttempts() {
        return Math.max(1, Config.SHOP_WEBSHOP_MAX_RETRY_ATTEMPTS.get());
    }

    private static long webshopCancelFeeCents(long totalCents) {
        if (totalCents <= 0L) {
            return 0L;
        }
        BigDecimal feePercent = BigDecimal.valueOf(Math.max(0.0D, Config.SHOP_WEBSHOP_CANCEL_FEE_PERCENT.get()));
        BigDecimal fee = BigDecimal.valueOf(totalCents)
                .multiply(feePercent)
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
        return fee.max(BigDecimal.ZERO).longValue();
    }

    private static long webshopExpediteSurchargeCents(long subtotalCents, boolean expedite) {
        if (!expedite || subtotalCents <= 0L) {
            return 0L;
        }
        BigDecimal pct = BigDecimal.valueOf(Math.max(0.0D, Config.SHOP_WEBSHOP_EXPEDITE_SURCHARGE_PERCENT.get()));
        BigDecimal surcharge = BigDecimal.valueOf(subtotalCents)
                .multiply(pct)
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN);
        return surcharge.max(BigDecimal.ZERO).longValue();
    }

    public static ShopActionResult orderManagerReport(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      UUID ownerId,
                                                      UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }

        boolean prunedLegacy = pruneLegacyCoordinateOrderPallets(shop) > 0;
        long now = System.currentTimeMillis();
        boolean expired = expireOrdersInPlace(centralBank, shop, now);
        boolean pruned = pruneAssignedPalletsMissingInWorld(server, shop) > 0;
        boolean prunedBindings = pruneOrderPalletBindingsOutsideAssigned(shop) > 0;
        if (prunedLegacy || expired || pruned || prunedBindings) {
            saveShopTag(centralBank, shop);
        }

        List<OrderView> orders = collectOrderViews(shop);
        Set<String> assignedSet = collectAssignedPalletRefSet(shop);
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
        List<AssignedPalletView> availablePallets = new ArrayList<>();
        for (String assignedKey : assignedSet) {
            if (assignedKey == null || assignedKey.isBlank()) {
                continue;
            }
            PalletRef live = liveLookup.get(assignedKey);
            if (live == null) {
                continue;
            }
            availablePallets.add(new AssignedPalletView(assignedKey, live));
        }
        availablePallets.sort(Comparator
                .comparing((AssignedPalletView p) -> p.ref().dimensionId(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(p -> p.ref().pos().getX())
                .thenComparingInt(p -> p.ref().pos().getY())
                .thenComparingInt(p -> p.ref().pos().getZ()));

        int open = 0;
        int accepted = 0;
        int completed = 0;
        int canceled = 0;
        int expiredCount = 0;
        for (OrderView order : orders) {
            switch (order.status()) {
                case ORDER_STATUS_OPEN -> open++;
                case ORDER_STATUS_ACCEPTED -> accepted++;
                case ORDER_STATUS_COMPLETED -> completed++;
                case ORDER_STATUS_CANCELED -> canceled++;
                case ORDER_STATUS_EXPIRED -> expiredCount++;
                default -> {
                }
            }
        }

        int level = Math.max(1, shop.getInt(TAG_LEVEL));
        int palletMax = maxAssignedOrderPalletsForLevel(level);

        List<String> lines = new ArrayList<>();
        lines.add("Ordering");
        lines.add("@order.manager.shop_name=" + sanitizeTokenText(shop.getString(TAG_NAME)));
        lines.add("@order.manager.open=" + open);
        lines.add("@order.manager.accepted=" + accepted);
        lines.add("@order.manager.completed=" + completed);
        lines.add("@order.manager.canceled=" + canceled);
        lines.add("@order.manager.expired=" + expiredCount);
        lines.add("@order.manager.assigned_pallets=" + assignedSet.size());
        lines.add("@order.manager.assigned_pallets_max=" + palletMax);
        lines.add("@order.manager.available_pallets=" + availablePallets.size());

        int idx = 1;
        for (OrderView order : orders) {
            String acceptedName = sanitizeTokenText(order.acceptedByName());
            long remaining = order.status().equals(ORDER_STATUS_ACCEPTED)
                    ? Math.max(0L, (order.expiresAt() - now + 999L) / 1000L)
                    : 0L;
            lines.add("@shop_order=" + idx
                    + "|" + order.orderId()
                    + "|" + sanitizeTokenText(order.itemId())
                    + "|" + sanitizeTokenText(order.itemName())
                    + "|" + Math.max(0, order.quantity())
                    + "|" + Math.max(0L, order.rewardCents())
                    + "|" + sanitizeTokenText(order.status())
                    + "|" + acceptedName
                    + "|" + remaining
                    + "|" + Math.max(ORDER_TIMEOUT_MINUTES_MIN, order.timeoutMinutes())
                    + "|" + order.createdAtMillis()
                    + "|" + sanitizeTokenText(order.boundPalletRef()));
            idx++;
        }

        int palletIndex = 1;
        for (AssignedPalletView pallet : availablePallets) {
            String palletId = pallet.palletId();
            PalletRef liveRef = pallet.ref();
            boolean full = !palletHasFreeSlot(server, liveRef);
            lines.add("@shop_order_pallet=" + palletIndex
                    + "|" + sanitizeTokenText(palletId)
                    + "|" + sanitizeTokenText(liveRef.dimensionId())
                    + "|" + liveRef.pos().getX()
                    + "|" + liveRef.pos().getY()
                    + "|" + liveRef.pos().getZ()
                    + "|1"
                    + "|" + (full ? 1 : 0));
            palletIndex++;
        }

        if (orders.isEmpty()) {
            lines.add("(No orders yet. Create your first delivery order.)");
        }
        if (availablePallets.isEmpty()) {
            lines.add("(No assigned delivery pallets with valid in-plot placement found.)");
        }
        lines.add("Assigned pallets must be accessible so couriers can complete deliveries.");
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult orderItemPickerReport(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }

        List<ShelfRef> shelves = collectShelvesForShop(server, shop);
        Map<String, ItemPickEntry> dedupe = new LinkedHashMap<>();
        for (ShelfRef shelfRef : shelves) {
            if (shelfRef == null || shelfRef.shelf() == null) {
                continue;
            }
            ShelfDisplayBlockEntity shelf = shelfRef.shelf();
            int slotCount = Math.max(1, shelf.getSlotCount());
            for (int slot = 0; slot < slotCount; slot++) {
                ItemStack stack = shelf.getDisplayItem(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                String reason = validateOrderItemAllowed(stack);
                if (reason != null) {
                    continue;
                }
                String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                if (itemId.isBlank() || "minecraft:air".equalsIgnoreCase(itemId)) {
                    continue;
                }

                int stockForSlot = shelf.isCreativeShelf()
                        ? Math.max(1, stack.getMaxStackSize())
                        : Math.max(0, shelf.getSlotStock(slot));
                ItemPickEntry existing = dedupe.get(itemId);
                if (existing == null) {
                    dedupe.put(itemId, new ItemPickEntry(
                            itemId,
                            sanitizeTokenText(stack.getHoverName().getString()),
                            Math.max(1, stack.getMaxStackSize()),
                            stockForSlot
                    ));
                } else {
                    dedupe.put(itemId, new ItemPickEntry(
                            existing.itemId(),
                            existing.itemName(),
                            existing.maxStack(),
                            Math.min(Integer.MAX_VALUE, existing.count() + stockForSlot)
                    ));
                }
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Order Item Picker");
        lines.add("@order.pick.count=" + dedupe.size());
        int idx = 1;
        for (ItemPickEntry entry : dedupe.values()) {
            lines.add("@shop_order_pick=" + idx
                    + "|" + sanitizeTokenText(entry.itemId())
                    + "|" + sanitizeTokenText(entry.itemName())
                    + "|" + Math.max(1, entry.maxStack())
                    + "|" + Math.max(0, entry.count()));
            idx++;
        }
        if (dedupe.isEmpty()) {
            lines.add("(No eligible shelf display items found. Configure shelf slots first.)");
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult createOrder(CentralBank centralBank,
                                               UUID ownerId,
                                               UUID shopId,
                                               String encoded) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID resolvedShopId = shop.getUUID(TAG_ID);
        UUID shopOwnerId = resolveShopOwnerIdFromTag(shop);
        if (shopOwnerId == null) {
            return new ShopActionResult(false, "Shop owner is missing. Re-open the app and try again.");
        }

        String[] parts = (encoded == null ? "" : encoded).split("\\|", -1);
        if (parts.length < 5) {
            return new ShopActionResult(false, "Invalid order payload.");
        }
        String itemId = (parts[0] == null ? "" : parts[0].trim()).toLowerCase(Locale.ROOT);
        String fallbackName = sanitizeTokenText(parts[1]);
        int quantity = parseSafeInt(parts[2], 0);
        long rewardCents = parseCurrencyToCents(parts[3]);
        int timeoutMinutes = Mth.clamp(parseSafeInt(parts[4], ORDER_TIMEOUT_MINUTES_DEFAULT),
                ORDER_TIMEOUT_MINUTES_MIN,
                ORDER_TIMEOUT_MINUTES_MAX);
        String boundPalletRaw = parts.length >= 6 ? parts[5] : "";
        String palletMode = parts.length >= 7 ? parts[6] : "";

        if (itemId.isBlank()) {
            return new ShopActionResult(false, "Select an item first.");
        }
        net.minecraft.resources.ResourceLocation key;
        try {
            key = net.minecraft.resources.ResourceLocation.parse(itemId);
        } catch (Exception ex) {
            return new ShopActionResult(false, "Invalid item id.");
        }
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            return new ShopActionResult(false, "That item does not exist.");
        }
        ItemStack template = new ItemStack(BuiltInRegistries.ITEM.get(key));
        String blockedReason = validateOrderItemAllowed(template);
        if (blockedReason != null) {
            return new ShopActionResult(false, blockedReason);
        }

        int maxStack = Math.max(1, template.getMaxStackSize());
        int maxQuantity = CardboardBoxBlockEntity.SLOT_COUNT * maxStack;
        if (quantity <= 0) {
            return new ShopActionResult(false, "Order quantity must be above 0.");
        }
        if (quantity > maxQuantity) {
            return new ShopActionResult(false, "Max quantity for " + template.getHoverName().getString()
                    + " is " + maxQuantity + " per box.");
        }
        if (rewardCents <= 0L) {
            return new ShopActionResult(false, "Reward must be above $0.00.");
        }

        Set<String> assignedSet = collectAssignedPalletRefSet(shop);
        if (assignedSet.isEmpty()) {
            return new ShopActionResult(false, "Assign at least one delivery pallet before creating orders.");
        }

        boolean specificDropTarget = normalizeOrderPalletMode(palletMode, boundPalletRaw);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Map<String, PalletRef> liveLookup = server == null
                ? Map.of()
                : buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));

        // Optional per-order pallet binding. In specific mode it is mandatory and
        // must point at an assigned pallet with available capacity.
        String boundPalletId = "";
        if (specificDropTarget) {
            if (boundPalletRaw == null || boundPalletRaw.isBlank()) {
                return new ShopActionResult(false, "Select a specific delivery pallet or switch to random pallet mode.");
            }
            String normalizedSelected = normalizeAssignedPalletKey(boundPalletRaw);
            if (normalizedSelected.isBlank()) {
                return new ShopActionResult(false, "Selected order pallet is invalid.");
            }
            PalletRef selectedLiveRef = liveLookup.get(normalizedSelected);
            if (selectedLiveRef != null && server != null) {
                String remappedId = readPalletId(server, selectedLiveRef);
                if (!remappedId.isBlank()) {
                    normalizedSelected = remappedId;
                    selectedLiveRef = liveLookup.get(normalizedSelected);
                }
            }
            if (!assignedSet.contains(normalizedSelected)) {
                return new ShopActionResult(false, "Selected pallet is not labeled as a delivery pallet for this shop.");
            }
            boundPalletId = normalizedSelected;
            if (server != null) {
                PalletRef liveRef = selectedLiveRef == null ? liveLookup.get(boundPalletId) : selectedLiveRef;
                if (liveRef == null) {
                    return new ShopActionResult(false, "Selected pallet no longer exists in your claimed plot.");
                }
                if (!palletHasFreeSlot(server, liveRef)) {
                    return new ShopActionResult(false, "Selected pallet is full. Choose another pallet or use random mode.");
                }
            }
        } else {
            if (server != null && countAssignedPalletsWithFreeSlots(server, assignedSet, liveLookup) <= 0) {
                return new ShopActionResult(false, "All labeled delivery pallets are full.");
            }
        }

        UUID settlementId = resolveSettlementAccountId(centralBank, shopOwnerId, resolvedShopId, null);
        if (settlementId == null) {
            return new ShopActionResult(false, "Shop settlement account is not configured.");
        }
        AccountHolder settlement = centralBank.SearchForAccountByAccountId(settlementId);
        if (settlement == null) {
            return new ShopActionResult(false, "Settlement account is unavailable.");
        }
        long reservedCents = computeOrderReservedCents(rewardCents);
        BigDecimal reservedAmount = BigDecimal.valueOf(reservedCents, 2);
        if (settlement.getBalance().compareTo(reservedAmount) < 0) {
            return new ShopActionResult(false,
                    "Insufficient settlement funds. Required reserve: "
                            + MoneyText.abbreviateWithDollar(reservedAmount) + ".");
        }
        if (!settlement.RemoveBalance(reservedAmount)) {
            return new ShopActionResult(false, "Failed to reserve payout funds from settlement account.");
        }
        // Track reserve movement in statements so owners can audit held funds.
        UserTransaction reserveTx = new UserTransaction(
                settlement.getAccountUUID(),
                settlement.getAccountUUID(),
                reservedAmount,
                LocalDateTime.now(),
                "SHOP_ORDER_RESERVE:" + sanitizeTokenText(shop.getString(TAG_NAME))
        );
        settlement.addTransaction(reserveTx);

        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        CompoundTag order = new CompoundTag();
        UUID orderId = UUID.randomUUID();
        order.putUUID(TAG_ORDER_ID, orderId);
        order.putString(TAG_ORDER_ITEM_ID, itemId);
        order.putString(TAG_ORDER_ITEM_NAME, fallbackName.equals("-")
                ? sanitizeTokenText(template.getHoverName().getString())
                : fallbackName);
        order.putInt(TAG_ORDER_QTY, quantity);
        order.putLong(TAG_ORDER_REWARD_CENTS, rewardCents);
        order.putInt(TAG_ORDER_TIMEOUT_MINUTES, timeoutMinutes);
        order.putString(TAG_ORDER_STATUS, ORDER_STATUS_OPEN);
        order.putLong(TAG_ORDER_CREATED_AT, System.currentTimeMillis());
        // Keep auditing actor for operational traceability; this may be owner or delegated manager.
        order.putUUID(TAG_ORDER_CREATED_BY, ownerId);
        order.putLong(TAG_ORDER_RESERVED_CENTS, reservedCents);
        order.putUUID(TAG_ORDER_RESERVED_FROM_ACCOUNT, settlement.getAccountUUID());
        if (!boundPalletId.isBlank()) {
            order.putString(TAG_ORDER_PALLET_ID, boundPalletId);
            order.remove(TAG_ORDER_PALLET_REF);
        }
        orders.add(order);
        shop.put(TAG_ORDERS, orders);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true,
                "Order created for " + quantity + "x " + template.getHoverName().getString()
                        + " | Reward $" + ShelfPrice.abbreviateFromCents(rewardCents)
                        + " | Reserved " + ShelfPrice.abbreviateFromCents(reservedCents)
                        + (boundPalletId.isBlank()
                        ? " | Drop target: random labeled pallet."
                        : " | Bound pallet " + shortPalletId(boundPalletId) + "."));
    }

    public static ShopActionResult cancelOrderByOwner(CentralBank centralBank,
                                                      UUID ownerId,
                                                      UUID shopId,
                                                      String orderIdRaw) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID orderId = parseOptionalUuid(orderIdRaw);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order id.");
        }
        CompoundTag order = findOrderById(shop, orderId);
        if (order == null) {
            return new ShopActionResult(false, "Order not found.");
        }
        String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
        if (ORDER_STATUS_COMPLETED.equals(status)) {
            return new ShopActionResult(false, "Completed orders cannot be canceled.");
        }
        if (ORDER_STATUS_CANCELED.equals(status)) {
            ShopActionResult releaseCanceled = releaseOrderReservation(centralBank, shop, order, "CANCELED");
            if (releaseCanceled.success()) {
                saveShopTag(centralBank, shop);
            }
            return releaseCanceled.success()
                    ? new ShopActionResult(true, "Order is already canceled.")
                    : releaseCanceled;
        }
        ShopActionResult release = releaseOrderReservation(centralBank, shop, order, "CANCELED");
        if (!release.success()) {
            return release;
        }
        order.putString(TAG_ORDER_STATUS, ORDER_STATUS_CANCELED);
        clearOrderAcceptance(order);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Order canceled.");
    }

    public static ShopActionResult listAssignablePallets(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         UUID ownerId,
                                                         UUID shopId) {
        return orderManagerReport(server, centralBank, ownerId, shopId);
    }

    public static ShopActionResult assignOrderPallet(MinecraftServer server,
                                                     CentralBank centralBank,
                                                     UUID ownerId,
                                                     UUID shopId,
                                                     String palletRefRaw) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (pruneLegacyCoordinateOrderPallets(shop) > 0) {
            saveShopTag(centralBank, shop);
        }
        String requestedKey = normalizeAssignedPalletKey(palletRefRaw);
        if (requestedKey.isBlank()) {
            return new ShopActionResult(false, "Invalid pallet selection.");
        }
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
        PalletRef liveRef = liveLookup.get(requestedKey);
        if (liveRef == null) {
            return new ShopActionResult(false, "Selected pallet is not inside your claimed shop plot or stockroom claim.");
        }
        String encoded = encodeOrderPalletRef(liveRef.dimensionId(), liveRef.pos());
        String palletId = ensurePalletId(server, liveRef);
        if (palletId.isBlank()) {
            return new ShopActionResult(false, "Failed to assign pallet id. Re-place pallet and try again.");
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                continue;
            }
            String existing = assignedPalletKey(palletTag);
            if (!existing.isBlank() && existing.equalsIgnoreCase(palletId)) {
                return new ShopActionResult(true, "Pallet is already assigned.");
            }
        }

        int max = maxAssignedOrderPalletsForLevel(Math.max(1, shop.getInt(TAG_LEVEL)));
        if (pallets.size() >= max) {
            return new ShopActionResult(false, "Assigned pallet limit reached (" + max + ").");
        }
        CompoundTag palletTag = new CompoundTag();
        palletTag.putString(TAG_PALLET_ID, palletId);
        palletTag.putString(TAG_DIM, normalizedDim(liveRef.dimensionId()));
        palletTag.putInt(TAG_X, liveRef.pos().getX());
        palletTag.putInt(TAG_Y, liveRef.pos().getY());
        palletTag.putInt(TAG_Z, liveRef.pos().getZ());
        pallets.add(palletTag);
        shop.put(TAG_ORDER_PALLETS, pallets);
        saveShopTag(centralBank, shop);
        DELIVERY_PALLET_LABEL_CACHE.put(encoded, sanitizeTokenText(shop.getString(TAG_NAME)));
        applyDeliveryPalletLabelToWorld(server, liveRef, true, sanitizeTokenText(shop.getString(TAG_NAME)));
        syncDeliveryPalletLabelsNow(server);
        return new ShopActionResult(true,
                "Assigned delivery pallet " + shortPalletId(palletId) + " at " + liveRef.dimensionId() + " " + liveRef.pos() + ".");
    }

    public static ShopActionResult unassignOrderPallet(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID ownerId,
                                                       UUID shopId,
                                                       String palletRefRaw) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (pruneLegacyCoordinateOrderPallets(shop) > 0) {
            saveShopTag(centralBank, shop);
        }
        String targetKey = normalizeAssignedPalletKey(palletRefRaw);
        if (targetKey.isBlank()) {
            return new ShopActionResult(false, "Invalid pallet selection.");
        }
        ListTag claims = deliveryPalletSearchClaims(shop);
        Map<String, PalletRef> liveLookup = server == null ? Map.of() : buildLivePalletLookup(server, claims);
        String target = targetKey;
        PalletRef mappedRef = liveLookup.get(targetKey);
        if (mappedRef != null && server != null) {
            String mappedId = readPalletId(server, mappedRef);
            if (!mappedId.isBlank()) {
                target = mappedId;
            }
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        ListTag next = new ListTag();
        boolean removed = false;
        PalletRef removedLiveRef = null;
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                continue;
            }
            String encoded = assignedPalletKey(palletTag);
            if (!removed && !encoded.isBlank() && encoded.equalsIgnoreCase(target)) {
                removed = true;
                if (server != null) {
                    removedLiveRef = liveLookup.get(encoded);
                }
                continue;
            }
            next.add(palletTag.copy());
        }
        if (!removed) {
            return new ShopActionResult(false, "Pallet is not assigned.");
        }
        int clearedBindings = 0;
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            String bound = normalizeOrderPalletBindingKey(order);
            if (!bound.isBlank() && bound.equalsIgnoreCase(target)) {
                order.remove(TAG_ORDER_PALLET_ID);
                order.remove(TAG_ORDER_PALLET_REF);
                clearedBindings++;
            }
        }
        shop.put(TAG_ORDER_PALLETS, next);
        saveShopTag(centralBank, shop);
        if (removedLiveRef != null) {
            DELIVERY_PALLET_LABEL_CACHE.remove(encodeOrderPalletRef(removedLiveRef.dimensionId(), removedLiveRef.pos()));
            applyDeliveryPalletLabelToWorld(server, removedLiveRef, false, "");
        }
        syncDeliveryPalletLabelsNow(server);
        return new ShopActionResult(true, "Delivery pallet unassigned."
                + (clearedBindings > 0 ? " Cleared " + clearedBindings + " bound order reference(s)." : ""));
    }

    public static ShopActionResult bindOrderPallet(CentralBank centralBank,
                                                   UUID ownerId,
                                                   UUID shopId,
                                                   String payload) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }

        String[] parts = (payload == null ? "" : payload).split("\\|", -1);
        if (parts.length < 2) {
            return new ShopActionResult(false, "Select an order and pallet first.");
        }
        UUID orderId = parseOptionalUuid(parts[0]);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order selection.");
        }
        String palletKey = normalizeAssignedPalletKey(parts[1]);
        if (palletKey.isBlank()) {
            return new ShopActionResult(false, "Invalid pallet selection.");
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
            PalletRef mappedRef = liveLookup.get(palletKey);
            if (mappedRef != null) {
                String mappedId = readPalletId(server, mappedRef);
                if (!mappedId.isBlank()) {
                    palletKey = mappedId;
                }
            }
        }
        Set<String> assigned = collectAssignedPalletRefSet(shop);
        if (!assigned.contains(palletKey)) {
            return new ShopActionResult(false, "Assign this pallet to the shop first.");
        }

        CompoundTag order = findOrderById(shop, orderId);
        if (order == null) {
            return new ShopActionResult(false, "Order not found.");
        }
        String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
        if (ORDER_STATUS_COMPLETED.equals(status)
                || ORDER_STATUS_CANCELED.equals(status)
                || ORDER_STATUS_EXPIRED.equals(status)) {
            return new ShopActionResult(false, "Only active orders can be bound to pallets.");
        }

        String current = normalizeOrderPalletBindingKey(order);
        if (palletKey.equalsIgnoreCase(current)) {
            return new ShopActionResult(true, "Order is already bound to that pallet.");
        }
        order.putString(TAG_ORDER_PALLET_ID, palletKey);
        order.remove(TAG_ORDER_PALLET_REF);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true,
                "Order " + shortUuid(orderId) + " bound to pallet " + shortPalletId(palletKey) + ".");
    }

    public static ShopActionResult clearOrderPalletBinding(CentralBank centralBank,
                                                           UUID ownerId,
                                                           UUID shopId,
                                                           String orderIdRaw) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        UUID orderId = parseOptionalUuid(orderIdRaw);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order selection.");
        }
        CompoundTag order = findOrderById(shop, orderId);
        if (order == null) {
            return new ShopActionResult(false, "Order not found.");
        }
        String current = normalizeOrderPalletBindingKey(order);
        if (current.isBlank()) {
            return new ShopActionResult(true, "Order has no bound pallet.");
        }
        order.remove(TAG_ORDER_PALLET_ID);
        order.remove(TAG_ORDER_PALLET_REF);
        saveShopTag(centralBank, shop);
        return new ShopActionResult(true, "Cleared pallet binding for order " + shortUuid(orderId) + ".");
    }

    public static ShopActionResult orderBoardReport(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID courierId) {
        if (server == null || centralBank == null || courierId == null) {
            return new ShopActionResult(false, "Order board is unavailable.");
        }
        List<CompoundTag> allShops = getAllShops(centralBank);
        long now = System.currentTimeMillis();
        boolean mutated = false;
        List<OrderBoardEntry> entries = new ArrayList<>();
        int activeMine = 0;

        for (CompoundTag shop : allShops) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            boolean changed = expireOrdersInPlace(centralBank, shop, now);
            if (changed) {
                saveShopTag(centralBank, shop);
                mutated = true;
            }
            UUID shopIdValue = shop.getUUID(TAG_ID);
            UUID ownerId = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
            String shopName = sanitizeTokenText(shop.getString(TAG_NAME));
            for (OrderView order : collectOrderViews(shop)) {
                if (ORDER_STATUS_ACCEPTED.equals(order.status()) && courierId.equals(order.acceptedBy())) {
                    activeMine++;
                }
                boolean include = ORDER_STATUS_OPEN.equals(order.status())
                        || (ORDER_STATUS_ACCEPTED.equals(order.status()) && courierId.equals(order.acceptedBy()))
                        || isFinishedOrderStatus(order.status());
                if (!include) {
                    continue;
                }
                long remaining = ORDER_STATUS_ACCEPTED.equals(order.status())
                        ? Math.max(0L, (order.expiresAt() - now + 999L) / 1000L)
                        : 0L;
                entries.add(new OrderBoardEntry(
                        order.orderId(),
                        shopIdValue,
                        ownerId,
                        shopName,
                        order.itemId(),
                        order.itemName(),
                        order.quantity(),
                        order.rewardCents(),
                        order.status(),
                        order.acceptedBy(),
                        order.acceptedByName(),
                        remaining,
                        order.timeoutMinutes(),
                        order.createdAtMillis(),
                        order.boundPalletRef(),
                        order.completedAtMillis(),
                        order.routeMillis(),
                        order.routeDistanceBlocks(),
                        order.payoutCents()
                ));
            }
        }

        entries.sort(Comparator
                .comparingInt((OrderBoardEntry e) -> orderBoardStatusSort(e.status()))
                .thenComparing(Comparator.comparingLong(OrderBoardEntry::sortMillis).reversed()));

        int cap = maxActiveCourierOrders();
        CourierProgress progress = readCourierProgress(centralBank, courierId);
        List<String> lines = new ArrayList<>();
        lines.add("Order Board");
        lines.add("@order.board.courier_id=" + courierId);
        lines.add("@order.board.active_mine=" + activeMine);
        lines.add("@order.board.active_cap=" + cap);
        lines.add("@order.board.total_open=" + entries.stream().filter(e -> ORDER_STATUS_OPEN.equals(e.status())).count());
        lines.add("@order.board.total_visible=" + entries.size());
        lines.add("@order.board.completed_total=" + progress.completed());
        lines.add("@order.board.canceled_total=" + progress.canceled());
        lines.add("@order.board.streak=" + progress.streak());
        lines.add("@order.board.best_streak=" + progress.bestStreak());
        lines.add("@order.board.total_payout_cents=" + progress.totalPayoutCents());
        lines.add("@order.board.success_rate_pct=" + progress.successRatePct());
        lines.add("@order.board.rank=" + sanitizeTokenText(courierRankLabel(progress.completed())));
        lines.add("@order.board.next_rank_at=" + nextCourierRankMilestone(progress.completed()));
        lines.add("@order.board.next_streak_at=" + nextCourierStreakMilestone(progress.streak()));
        lines.add("@order.board.history_total=" + entries.stream().filter(e -> isFinishedOrderStatus(e.status())).count());
        int idx = 1;
        for (OrderBoardEntry entry : entries) {
            lines.add("@order_board_order=" + idx
                    + "|" + entry.orderId()
                    + "|" + entry.shopId()
                    + "|" + sanitizeTokenText(entry.shopName())
                    + "|" + sanitizeTokenText(entry.itemId())
                    + "|" + sanitizeTokenText(entry.itemName())
                    + "|" + Math.max(1, entry.quantity())
                    + "|" + Math.max(0L, entry.rewardCents())
                    + "|" + sanitizeTokenText(entry.status())
                    + "|" + sanitizeTokenText(entry.acceptedByName())
                    + "|" + Math.max(0L, entry.remainingSeconds())
                    + "|" + Math.max(ORDER_TIMEOUT_MINUTES_MIN, entry.timeoutMinutes())
                    + "|" + entry.createdAtMillis()
                    + "|" + sanitizeTokenText(entry.boundPalletRef())
                    + "|" + Math.max(0L, entry.completedAtMillis())
                    + "|" + Math.max(0L, entry.routeMillis())
                    + "|" + entry.routeDistanceBlocks()
                    + "|" + Math.max(0L, entry.payoutCents()));
            idx++;
        }
        appendCourierRankingLines(lines, centralBank, server, courierId);
        if (entries.isEmpty()) {
            lines.add("(No open delivery orders available right now.)");
        }
        lines.add("Courier Rank: " + courierRankLabel(progress.completed())
                + " | Success: " + progress.successRatePct() + "%"
                + " | Streak: " + progress.streak() + " (best " + progress.bestStreak() + ")");
        lines.add("Tip: accepted orders are reserved for a single courier until completed, canceled, or expired.");
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static LeaderboardSeedResult seedOrderBoardLeaderboardDemo(MinecraftServer server) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new LeaderboardSeedResult(0, 0, 0, 0);
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        long now = System.currentTimeMillis();
        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (int i = 1; i <= 100; i++) {
            UUID courierId = demoLeaderboardCourierId(i);
            CompoundTag entry = getCourierStatsEntry(root, courierId, false);
            if (entry == null) {
                entry = getCourierStatsEntry(root, courierId, true);
                created++;
            } else if (!entry.getBoolean(TAG_COURIER_SEED_DATA)) {
                skipped++;
                continue;
            } else {
                updated++;
            }
            writeDemoLeaderboardCourier(entry, i, now);
        }

        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        BankManager.markDirty();
        return new LeaderboardSeedResult(created, updated, 0, skipped);
    }

    public static LeaderboardSeedResult removeOrderBoardLeaderboardDemo(MinecraftServer server) {
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new LeaderboardSeedResult(0, 0, 0, 0);
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag couriers = root.getList(TAG_ORDER_COURIERS, Tag.TAG_COMPOUND);
        int removed = 0;
        for (int i = couriers.size() - 1; i >= 0; i--) {
            Tag tag = couriers.get(i);
            if (tag instanceof CompoundTag entry
                    && entry.getBoolean(TAG_COURIER_SEED_DATA)
                    && entry.getString(TAG_COURIER_SEED_KEY).startsWith("demo-leaderboard:")) {
                couriers.remove(i);
                removed++;
            }
        }
        root.put(TAG_ORDER_COURIERS, couriers);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        if (removed > 0) {
            BankManager.markDirty();
        }
        return new LeaderboardSeedResult(0, 0, removed, 0);
    }

    private static UUID demoLeaderboardCourierId(int index) {
        return UUID.nameUUIDFromBytes(("ultimatebankingsystem:order-board-demo-leaderboard:" + index)
                .getBytes(StandardCharsets.UTF_8));
    }

    private static void writeDemoLeaderboardCourier(CompoundTag entry, int index, long now) {
        if (entry == null) {
            return;
        }
        int safeIndex = Math.max(1, Math.min(100, index));
        long completed = 180L - safeIndex;
        long canceled = Math.max(0L, safeIndex % 9L);
        long streak = Math.max(1L, 20L - (safeIndex % 20L));
        long bestStreak = Math.max(streak, 28L - (safeIndex % 24L));
        long totalPayout = Math.max(0L, completed * (8_400L + safeIndex * 37L));
        int distance = 36 + (safeIndex * 13 % 88);
        long routeScore = 410L + (safeIndex * 31L % 690L);
        long routeMillis = routeScore * Math.max(1, distance);

        entry.putUUID(TAG_COURIER_ID, demoLeaderboardCourierId(safeIndex));
        entry.putString(TAG_COURIER_NAME, demoLeaderboardName(safeIndex));
        entry.putLong(TAG_COURIER_COMPLETED, completed);
        entry.putLong(TAG_COURIER_CANCELED, canceled);
        entry.putLong(TAG_COURIER_STREAK, streak);
        entry.putLong(TAG_COURIER_BEST_STREAK, bestStreak);
        entry.putLong(TAG_COURIER_TOTAL_PAYOUT_CENTS, totalPayout);
        entry.putLong(TAG_COURIER_LAST_ACTIVITY_AT, Math.max(0L, now - safeIndex * 9L * 60L * 1000L));
        entry.putLong(TAG_COURIER_BEST_ROUTE_MILLIS, routeMillis);
        entry.putInt(TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS, distance);
        entry.putLong(TAG_COURIER_BEST_ROUTE_SCORE, routeScore);
        entry.putLong(TAG_COURIER_BEST_ROUTE_AT, Math.max(0L, now - safeIndex * 7L * 60L * 1000L));
        entry.putBoolean(TAG_COURIER_SEED_DATA, true);
        entry.putString(TAG_COURIER_SEED_KEY, "demo-leaderboard:" + safeIndex);
    }

    private static String demoLeaderboardName(int index) {
        String[] first = {
                "Mira", "Jonas", "Talia", "Ravi", "Nora", "Devon", "Iris", "Kian", "Selene", "Omar"
        };
        String[] last = {
                "Stone", "Reed", "Vale", "Cross", "Lane", "Hart", "Wells", "March", "Quill", "North"
        };
        int safeIndex = Math.max(1, index);
        String name = first[(safeIndex - 1) % first.length] + " " + last[((safeIndex - 1) / first.length) % last.length];
        return name + " " + String.format(Locale.ROOT, "%03d", safeIndex);
    }

    private static void appendCourierRankingLines(List<String> lines, CentralBank centralBank, MinecraftServer server, UUID currentCourierId) {
        if (lines == null || centralBank == null) {
            return;
        }
        List<CourierRankEntry> entries = readCourierRankEntries(centralBank, server);
        if (entries.isEmpty()) {
            return;
        }

        List<CourierRankEntry> completed = new ArrayList<>(entries);
        completed.sort(Comparator
                .comparingLong(CourierRankEntry::completed).reversed()
                .thenComparing(Comparator.comparingLong(CourierRankEntry::successRatePct).reversed())
                .thenComparing(CourierRankEntry::courierName));
        appendCompletedRankLines(lines, completed, currentCourierId);

        List<CourierRankEntry> fastest = new ArrayList<>();
        for (CourierRankEntry entry : entries) {
            if (entry.bestRouteScore() > 0L && entry.bestRouteMillis() > 0L && entry.bestRouteDistanceBlocks() > 0) {
                fastest.add(entry);
            }
        }
        fastest.sort(Comparator
                .comparingLong(CourierRankEntry::bestRouteScore)
                .thenComparing(Comparator.comparingLong(CourierRankEntry::bestRouteMillis))
                .thenComparing(CourierRankEntry::courierName));
        appendFastestRankLines(lines, fastest, currentCourierId);
    }

    private static void appendCompletedRankLines(List<String> lines, List<CourierRankEntry> entries, UUID currentCourierId) {
        int rank = 1;
        for (CourierRankEntry entry : entries) {
            if (entry.completed() <= 0L && entry.canceled() <= 0L) {
                continue;
            }
            boolean currentCourier = currentCourierId != null && currentCourierId.equals(entry.courierId());
            if (rank > 100 && !currentCourier) {
                rank++;
                continue;
            }
            appendCourierRankLine(
                    lines,
                    "completed",
                    rank,
                    entry,
                    entry.completed() + " completed",
                    entry.successRatePct() + "% success | " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(entry.totalPayoutCents(), 2)) + " paid",
                    courierRankLabel(entry.completed()),
                    entry.completed()
            );
            rank++;
        }
    }

    private static void appendFastestRankLines(List<String> lines, List<CourierRankEntry> entries, UUID currentCourierId) {
        int rank = 1;
        for (CourierRankEntry entry : entries) {
            boolean currentCourier = currentCourierId != null && currentCourierId.equals(entry.courierId());
            if (rank > 100 && !currentCourier) {
                rank++;
                continue;
            }
            appendCourierRankLine(
                    lines,
                    "fastest",
                    rank,
                    entry,
                    formatMillisShort(entry.bestRouteMillis()) + " over " + entry.bestRouteDistanceBlocks() + " blocks",
                    entry.bestRouteScore() + " ms/block",
                    "Fast",
                    entry.bestRouteScore()
            );
            rank++;
        }
    }

    private static void appendPayoutRankLines(List<String> lines, List<CourierRankEntry> entries) {
        int rank = 1;
        for (CourierRankEntry entry : entries) {
            if (rank > 100 || entry.totalPayoutCents() <= 0L) {
                break;
            }
            appendCourierRankLine(
                    lines,
                    "payout",
                    rank,
                    entry,
                    MoneyText.abbreviateWithDollar(BigDecimal.valueOf(entry.totalPayoutCents(), 2)) + " lifetime",
                    entry.completed() + " completed | streak " + entry.streak(),
                    "Paid",
                    entry.totalPayoutCents()
            );
            rank++;
        }
    }

    private static void appendStreakRankLines(List<String> lines, List<CourierRankEntry> entries) {
        int rank = 1;
        for (CourierRankEntry entry : entries) {
            if (rank > 100 || entry.bestStreak() <= 0L) {
                break;
            }
            appendCourierRankLine(
                    lines,
                    "streak",
                    rank,
                    entry,
                    entry.bestStreak() + " best streak",
                    "current " + entry.streak() + " | " + entry.successRatePct() + "% success",
                    "Streak",
                    entry.bestStreak()
            );
            rank++;
        }
    }

    private static void appendCourierRankLine(List<String> lines,
                                              String board,
                                              int rank,
                                              CourierRankEntry entry,
                                              String primary,
                                              String secondary,
                                              String badge,
                                              long score) {
        lines.add("@order_board_rank=" + sanitizeTokenText(board)
                + "|" + rank
                + "|" + entry.courierId()
                + "|" + sanitizeTokenText(entry.courierName())
                + "|" + sanitizeTokenText(primary)
                + "|" + sanitizeTokenText(secondary)
                + "|" + Math.max(0L, score)
                + "|" + sanitizeTokenText(badge));
    }

    private static List<CourierRankEntry> readCourierRankEntries(CentralBank centralBank, MinecraftServer server) {
        List<CourierRankEntry> out = new ArrayList<>();
        if (centralBank == null) {
            return out;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag couriers = root.getList(TAG_ORDER_COURIERS, Tag.TAG_COMPOUND);
        boolean mutated = false;
        for (Tag tag : couriers) {
            if (!(tag instanceof CompoundTag entry) || !entry.contains(TAG_COURIER_ID)) {
                continue;
            }
            UUID courierId;
            try {
                courierId = entry.getUUID(TAG_COURIER_ID);
            } catch (Exception ignored) {
                continue;
            }
            long completed = Math.max(0L, entry.getLong(TAG_COURIER_COMPLETED));
            long canceled = Math.max(0L, entry.getLong(TAG_COURIER_CANCELED));
            long streak = Math.max(0L, entry.getLong(TAG_COURIER_STREAK));
            long bestStreak = Math.max(0L, entry.getLong(TAG_COURIER_BEST_STREAK));
            long totalPayout = Math.max(0L, entry.getLong(TAG_COURIER_TOTAL_PAYOUT_CENTS));
            long bestRouteMillis = Math.max(0L, entry.getLong(TAG_COURIER_BEST_ROUTE_MILLIS));
            int bestRouteDistance = entry.contains(TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS)
                    ? entry.getInt(TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS)
                    : -1;
            long bestRouteScore = Math.max(0L, entry.getLong(TAG_COURIER_BEST_ROUTE_SCORE));
            String name = sanitizeTokenText(entry.getString(TAG_COURIER_NAME));
            String resolvedName = resolveCourierProfileName(server, courierId);
            if (!resolvedName.isBlank() && isCourierNameFallback(name, courierId)) {
                name = resolvedName;
                entry.putString(TAG_COURIER_NAME, name);
                mutated = true;
            }
            if (isCourierNameFallback(name, courierId)) {
                name = shortUuid(courierId);
            }
            out.add(new CourierRankEntry(
                    courierId,
                    name,
                    completed,
                    canceled,
                    streak,
                    bestStreak,
                    totalPayout,
                    bestRouteMillis,
                    bestRouteDistance,
                    bestRouteScore,
                    computeCourierSuccessRate(completed, canceled)
            ));
        }
        if (mutated) {
            centralMeta.put(TAG_ROOT, root);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        }
        return out;
    }

    private static boolean isFinishedOrderStatus(String status) {
        return ORDER_STATUS_COMPLETED.equals(status)
                || ORDER_STATUS_CANCELED.equals(status)
                || ORDER_STATUS_EXPIRED.equals(status);
    }

    private static int orderBoardStatusSort(String status) {
        if (ORDER_STATUS_ACCEPTED.equals(status)) {
            return 0;
        }
        if (ORDER_STATUS_OPEN.equals(status)) {
            return 1;
        }
        return 2;
    }

    private static String formatMillisShort(long millis) {
        long safe = Math.max(0L, millis);
        if (safe < 60_000L) {
            return String.format(Locale.ROOT, "%.1fs", safe / 1000.0D);
        }
        long seconds = safe / 1000L;
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    public static ShopActionResult orderBoardAccept(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID courierId,
                                                    String orderIdRaw) {
        if (server == null || centralBank == null || courierId == null) {
            return new ShopActionResult(false, "Order board is unavailable.");
        }
        UUID orderId = parseOptionalUuid(orderIdRaw);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order id.");
        }
        FoundOrder found = findOrderGlobal(centralBank, orderId);
        if (found == null) {
            return new ShopActionResult(false, "Order not found.");
        }
        CompoundTag order = found.orderTag();
        String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
        if (ORDER_STATUS_COMPLETED.equals(status) || ORDER_STATUS_CANCELED.equals(status) || ORDER_STATUS_EXPIRED.equals(status)) {
            return new ShopActionResult(false, "Order is no longer available.");
        }

        long now = System.currentTimeMillis();
        if (ORDER_STATUS_ACCEPTED.equals(status)) {
            UUID acceptedBy = order.contains(TAG_ORDER_ACCEPTED_BY) ? order.getUUID(TAG_ORDER_ACCEPTED_BY) : null;
            if (acceptedBy != null && acceptedBy.equals(courierId)) {
                long seconds = Math.max(0L, (order.getLong(TAG_ORDER_EXPIRES_AT) - now + 999L) / 1000L);
                return new ShopActionResult(true, "Order already accepted by you. Time left: " + seconds + "s.");
            }
            return new ShopActionResult(false, "Order is already accepted by another courier.");
        }

        int mine = countActiveCourierOrders(centralBank, courierId, now);
        int cap = maxActiveCourierOrders();
        if (mine >= cap) {
            return new ShopActionResult(false, "Active order limit reached (" + cap + "). Complete or cancel one first.");
        }

        int timeoutMinutes = Mth.clamp(order.getInt(TAG_ORDER_TIMEOUT_MINUTES),
                ORDER_TIMEOUT_MINUTES_MIN,
                ORDER_TIMEOUT_MINUTES_MAX);
        String itemName = sanitizeTokenText(order.getString(TAG_ORDER_ITEM_NAME));
        int quantity = Math.max(1, order.getInt(TAG_ORDER_QTY));
        long rewardCents = Math.max(0L, order.getLong(TAG_ORDER_REWARD_CENTS));
        ServerPlayer courier = server.getPlayerList() == null ? null : server.getPlayerList().getPlayer(courierId);
        String courierName = courierDisplayName(courier, courierId);
        int routeDistanceBlocks = courier == null
                ? -1
                : blocksAwayFromShopClaims(
                found.shopTag().getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                courier.serverLevel().dimension().location().toString(),
                courier.blockPosition()
        );
        order.putString(TAG_ORDER_STATUS, ORDER_STATUS_ACCEPTED);
        order.putUUID(TAG_ORDER_ACCEPTED_BY, courierId);
        order.putString(TAG_ORDER_ACCEPTED_BY_NAME, courierName);
        order.putLong(TAG_ORDER_ACCEPTED_AT, now);
        order.putLong(TAG_ORDER_EXPIRES_AT, now + (timeoutMinutes * 60_000L));
        order.putInt(TAG_ORDER_ROUTE_DISTANCE_BLOCKS, routeDistanceBlocks);
        saveShopTag(centralBank, found.shopTag());
        notifyShopOwnerOrderAccepted(server, found.shopTag(), courierId, itemName, quantity, rewardCents, timeoutMinutes);
        recordCourierSeen(centralBank, courierId, courierName, now);
        CourierProgress progress = readCourierProgress(centralBank, courierId);
        String boundPallet = normalizeOrderPalletBindingKey(order);
        if (courier != null) {
            courier.sendSystemMessage(UbsTranslations.literal("Order accepted: ")
                    .withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(itemName + " x" + quantity).withStyle(ChatFormatting.WHITE)));
            courier.sendSystemMessage(UbsTranslations.literal("Reward: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(MoneyText.abbreviateWithDollar(BigDecimal.valueOf(rewardCents, 2))).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(UbsTranslations.literal("Time limit: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(timeoutMinutes + "m").withStyle(ChatFormatting.WHITE)));
            if (boundPallet.isBlank()) {
                courier.sendSystemMessage(UbsTranslations.literal("Drop target: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(UbsTranslations.literal("Any delivery pallet assigned to the shop.").withStyle(ChatFormatting.GREEN)));
            } else {
                courier.sendSystemMessage(UbsTranslations.literal("Drop target: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(formatPalletRef(boundPallet)).withStyle(ChatFormatting.GREEN)));
            }
            courier.sendSystemMessage(UbsTranslations.literal("§8Tip: keep a completion streak for payout bonuses."));
            pushShopAlert(
                    courier,
                    "Order Board",
                    "Order accepted: " + itemName + " x" + quantity
                            + " | Reward " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(rewardCents, 2))
                            + " | Time limit " + timeoutMinutes + "m"
                            + (boundPallet.isBlank() ? " | Drop target: any delivery pallet" : " | Drop target: " + formatPalletRef(boundPallet)),
                    DeliveryAlertPayload.AlertTone.SUCCESS,
                    5600
            );
        }
        return new ShopActionResult(true,
                "Order accepted. " + itemName + " x" + quantity
                        + " | Reward " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(rewardCents, 2))
                        + " | Time " + timeoutMinutes + "m"
                        + " | Streak " + progress.streak());
    }

    public static ShopActionResult orderBoardCancel(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID courierId,
                                                    String orderIdRaw) {
        if (server == null || centralBank == null || courierId == null) {
            return new ShopActionResult(false, "Order board is unavailable.");
        }
        UUID orderId = parseOptionalUuid(orderIdRaw);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order id.");
        }
        FoundOrder found = findOrderGlobal(centralBank, orderId);
        if (found == null) {
            return new ShopActionResult(false, "Order not found.");
        }
        CompoundTag order = found.orderTag();
        String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
        if (!ORDER_STATUS_ACCEPTED.equals(status)) {
            return new ShopActionResult(false, "Only accepted orders can be canceled.");
        }
        UUID acceptedBy = order.contains(TAG_ORDER_ACCEPTED_BY) ? order.getUUID(TAG_ORDER_ACCEPTED_BY) : null;
        if (acceptedBy == null || !acceptedBy.equals(courierId)) {
            return new ShopActionResult(false, "You can only cancel your own accepted orders.");
        }
        ServerPlayer courier = server.getPlayerList() == null ? null : server.getPlayerList().getPlayer(courierId);
        String courierName = courierDisplayName(courier, courierId);
        order.putString(TAG_ORDER_STATUS, ORDER_STATUS_OPEN);
        order.putString(TAG_ORDER_ACCEPTED_BY_NAME, courierName);
        clearOrderAcceptance(order);
        saveShopTag(centralBank, found.shopTag());
        CourierProgress progress = recordCourierCancel(centralBank, courierId, courierName, System.currentTimeMillis());
        if (courier != null) {
            courier.sendSystemMessage(UbsTranslations.literal("§eOrder canceled. It is now available for other couriers."));
            courier.sendSystemMessage(UbsTranslations.literal("Streak reset. Current success rate: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(progress.successRatePct() + "%").withStyle(ChatFormatting.WHITE)));
            pushShopAlert(
                    courier,
                    "Order Board",
                    "Order canceled. It is now available for other couriers. Streak reset. Success rate: "
                            + progress.successRatePct() + "%.",
                    DeliveryAlertPayload.AlertTone.WARNING,
                    5600
            );
        }
        return new ShopActionResult(true, "Order canceled and returned to open pool. Streak reset.");
    }

    public static ShopActionResult webshopReport(MinecraftServer server,
                                                 CentralBank centralBank,
                                                 UUID playerId) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        Map<String, WebshopCatalogItem> catalogMap = buildWebshopCatalogMap();
        List<WebshopCatalogItem> catalog = new ArrayList<>(catalogMap.values());
        catalog.sort(Comparator.comparing(WebshopCatalogItem::category, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(WebshopCatalogItem::itemName, String.CASE_INSENSITIVE_ORDER));

        List<AccountHolder> accounts = collectPlayerAccountsForWebshop(centralBank, playerId);
        if (session.selectedAccountId != null && accounts.stream().noneMatch(a -> session.selectedAccountId.equals(a.getAccountUUID()))) {
            session.selectedAccountId = null;
        }
        if (session.selectedAccountId == null) {
            for (AccountHolder account : accounts) {
                if (account != null && account.isPrimaryAccount()) {
                    session.selectedAccountId = account.getAccountUUID();
                    break;
                }
            }
            if (session.selectedAccountId == null && !accounts.isEmpty()) {
                session.selectedAccountId = accounts.get(0).getAccountUUID();
            }
        }

        List<CompoundTag> ownedShops = getOwnerShops(centralBank, playerId);
        CompoundTag selectedShop = resolveOwnedShopById(ownedShops, session.selectedShopId);
        if (selectedShop == null) {
            session.selectedShopId = null;
            session.selectedPalletId = "";
        }

        String mode = normalizeWebshopMode(session.deliveryMode);
        List<AssignedPalletView> availablePallets = selectedShop == null
                ? List.of()
                : collectDeliveryPalletViews(server, selectedShop);
        if (!session.selectedPalletId.isBlank()) {
            boolean found = false;
            for (AssignedPalletView view : availablePallets) {
                if (view != null && view.palletId() != null && view.palletId().equalsIgnoreCase(session.selectedPalletId)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                session.selectedPalletId = "";
            }
        }

        List<WebshopCartLine> cartLines = buildWebshopCartLines(session, catalogMap);
        long subtotalCents = 0L;
        int cartUnits = 0;
        for (WebshopCartLine line : cartLines) {
            subtotalCents = safeAdd(subtotalCents, Math.max(0L, line.lineTotalCents()));
            cartUnits += Math.max(0, line.quantity());
        }
        int cartBoxCount = estimateWebshopBoxCount(cartLines);
        long surchargeCents = webshopExpediteSurchargeCents(subtotalCents, session.expedite);
        long totalCents = safeAdd(subtotalCents, surchargeCents);
        int freePalletSlots = 0;
        for (AssignedPalletView view : availablePallets) {
            if (view != null && view.ref() != null) {
                freePalletSlots += Math.max(0, countPalletFreeSlots(server, view.ref()));
            }
        }

        List<WebshopOrderEntry> orders = readWebshopOrdersForBuyer(centralBank, playerId);
        int queuedCount = 0;
        for (WebshopOrderEntry order : orders) {
            if (order != null && WEBSHOP_STATUS_QUEUED.equals(order.status())) {
                queuedCount++;
            }
        }

        List<String> lines = new ArrayList<>();
        lines.add("Retail Webshop");
        lines.add("@webshop.summary.catalog_count=" + catalog.size());
        lines.add("@webshop.summary.cart_lines=" + cartLines.size());
        lines.add("@webshop.summary.cart_units=" + cartUnits);
        lines.add("@webshop.summary.subtotal_cents=" + subtotalCents);
        lines.add("@webshop.summary.surcharge_cents=" + surchargeCents);
        lines.add("@webshop.summary.total_cents=" + totalCents);
        lines.add("@webshop.summary.queued_orders=" + queuedCount);
        lines.add("@webshop.summary.max_active_orders=" + maxActiveWebshopOrders());
        lines.add("@webshop.delivery.required_boxes=" + cartBoxCount);
        lines.add("@webshop.delivery.free_slots=" + freePalletSlots);
        lines.add("@webshop.delivery.assigned_pallets=" + availablePallets.size());
        lines.add("@webshop.selected.account_id=" + (session.selectedAccountId == null ? "" : session.selectedAccountId));
        lines.add("@webshop.selected.mode=" + sanitizeTokenText(mode));
        lines.add("@webshop.selected.shop_id=" + (session.selectedShopId == null ? "" : session.selectedShopId));
        lines.add("@webshop.selected.pallet_id=" + sanitizeTokenText(session.selectedPalletId));
        lines.add("@webshop.selected.expedite=" + (session.expedite ? "1" : "0"));

        int idx = 1;
        for (WebshopCatalogItem item : catalog) {
            lines.add("@webshop_catalog=" + idx
                    + "|" + sanitizeTokenText(item.itemId())
                    + "|" + sanitizeTokenText(item.itemName())
                    + "|" + sanitizeTokenText(item.category())
                    + "|" + Math.max(0L, item.unitPriceCents())
                    + "|" + sanitizeTokenText(item.description()));
            idx++;
        }

        idx = 1;
        for (WebshopCartLine line : cartLines) {
            lines.add("@webshop_cart=" + idx
                    + "|" + sanitizeTokenText(line.itemId())
                    + "|" + sanitizeTokenText(line.itemName())
                    + "|" + Math.max(0, line.quantity())
                    + "|" + Math.max(0L, line.unitPriceCents())
                    + "|" + Math.max(0L, line.lineTotalCents()));
            idx++;
        }

        idx = 1;
        for (AccountHolder account : accounts) {
            if (account == null) {
                continue;
            }
            String typeLabel = account.getAccountType() == null ? "Account" : account.getAccountType().label;
            String bankName = "-";
            if (account.getBankId() != null) {
                var bank = centralBank.getBank(account.getBankId());
                if (bank != null && bank.getBankName() != null && !bank.getBankName().isBlank()) {
                    bankName = bank.getBankName().trim();
                }
            }
            long balanceCents = account.getBalance() == null ? 0L : account.getBalance().movePointRight(2).longValue();
            lines.add("@webshop_account=" + idx
                    + "|" + account.getAccountUUID()
                    + "|" + sanitizeTokenText(typeLabel)
                    + "|" + sanitizeTokenText(bankName)
                    + "|" + Math.max(0L, balanceCents)
                    + "|" + (account.isPrimaryAccount() ? "1" : "0"));
            idx++;
        }

        idx = 1;
        for (CompoundTag shop : ownedShops) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            Set<String> assigned = collectAssignedPalletRefSet(shop);
            lines.add("@webshop_shop=" + idx
                    + "|" + shop.getUUID(TAG_ID)
                    + "|" + sanitizeTokenText(shop.getString(TAG_NAME))
                    + "|" + sanitizeTokenText(prettyShopType(shop.getString(TAG_TYPE)))
                    + "|" + assigned.size());
            idx++;
        }

        idx = 1;
        for (AssignedPalletView view : availablePallets) {
            if (view == null || view.ref() == null || view.ref().pos() == null) {
                continue;
            }
            boolean full = !palletHasFreeSlot(server, view.ref());
            lines.add("@webshop_pallet=" + idx
                    + "|" + sanitizeTokenText(view.palletId())
                    + "|" + (selectedShop == null || !selectedShop.contains(TAG_ID) ? "" : selectedShop.getUUID(TAG_ID))
                    + "|" + sanitizeTokenText(selectedShop == null ? "-" : selectedShop.getString(TAG_NAME))
                    + "|" + sanitizeTokenText(view.ref().dimensionId())
                    + "|" + view.ref().pos().getX()
                    + "|" + view.ref().pos().getY()
                    + "|" + view.ref().pos().getZ()
                    + "|" + (full ? "1" : "0"));
            idx++;
        }

        idx = 1;
        for (WebshopOrderEntry order : orders) {
            if (order == null) {
                continue;
            }
            String target = switch (normalizeWebshopMode(order.deliveryMode())) {
                case WEBSHOP_MODE_PALLET_SPECIFIC -> "Pallet " + shortPalletId(order.palletId());
                case WEBSHOP_MODE_PALLET_RANDOM -> "Random delivery pallet";
                default -> normalizedDim(order.dimensionId()) + " (" + order.pos().getX() + "," + order.pos().getY() + "," + order.pos().getZ() + ")";
            };
            lines.add("@webshop_order=" + idx
                    + "|" + order.orderId()
                    + "|" + sanitizeTokenText(order.status())
                    + "|" + Math.max(0L, order.totalCents())
                    + "|" + Math.max(0L, order.createdAtMillis())
                    + "|" + Math.max(0L, order.etaAtMillis())
                    + "|" + sanitizeTokenText(order.deliveryMode())
                    + "|" + sanitizeTokenText(target)
                    + "|" + Math.max(0, order.boxCount())
                    + "|" + Math.max(0, order.attempts())
                    + "|" + sanitizeTokenText(order.lastError())
                    + "|" + sanitizeTokenText(webshopOrderItemSummary(centralBank, order.orderId())));
            idx++;
        }

        if (catalog.isEmpty()) {
            lines.add("(No webshop items are configured.)");
        } else {
            lines.add("Browse catalog cards, add items to cart, select account, then checkout.");
        }
        if (accounts.isEmpty()) {
            lines.add("No bank accounts found for this player. Create an account first.");
        }
        lines.add("Delivery requirements: select one of your shops, then assign at least one live delivery pallet in Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets.");
        lines.add("Use the Delivery Pallet Claim Tool on a pallet inside the shop claim, then press Paper=Save.");
        if (selectedShop == null) {
            lines.add("Requirement missing: choose one of your shops in Retail Webshop > Delivery before checkout.");
        } else if (availablePallets.isEmpty()) {
            lines.add("Requirement missing: selected shop has no valid delivery pallets assigned. Open Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets.");
        } else if (cartBoxCount > 0 && freePalletSlots < cartBoxCount) {
            lines.add("Requirement missing: current cart needs " + formatWebshopBoxSlotCount(cartBoxCount)
                    + ", but selected shop has " + freePalletSlots + " free pallet slot(s). Clear boxes or assign more pallets.");
        }
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult webshopAddToCart(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID playerId,
                                                    String itemIdRaw,
                                                    String quantityRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        String itemId = itemIdRaw == null ? "" : itemIdRaw.trim().toLowerCase(Locale.ROOT);
        int quantity = Mth.clamp(parseSafeInt(quantityRaw, 1), 1, 4096);
        Map<String, WebshopCatalogItem> catalog = buildWebshopCatalogMap();
        WebshopCatalogItem selected = catalog.get(itemId);
        if (selected == null) {
            return new ShopActionResult(false, "That catalog item is not available.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        int current = session.cart.getOrDefault(selected.itemId(), 0);
        int next = Math.min(4096, Math.max(0, current + quantity));
        session.cart.put(selected.itemId(), next);
        touchWebshopSession(session);
        return new ShopActionResult(true,
                "Added " + quantity + "x " + selected.itemName() + " to cart (now " + next + ").");
    }

    public static ShopActionResult webshopSetCartQuantity(MinecraftServer server,
                                                          CentralBank centralBank,
                                                          UUID playerId,
                                                          String itemIdRaw,
                                                          String quantityRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        String itemId = itemIdRaw == null ? "" : itemIdRaw.trim().toLowerCase(Locale.ROOT);
        if (itemId.isBlank()) {
            return new ShopActionResult(false, "Select a cart item first.");
        }
        int quantity = parseSafeInt(quantityRaw, 0);
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        if (!session.cart.containsKey(itemId)) {
            return new ShopActionResult(false, "That item is not currently in your cart.");
        }
        if (quantity <= 0) {
            session.cart.remove(itemId);
            touchWebshopSession(session);
            return new ShopActionResult(true, "Removed item from cart.");
        }
        session.cart.put(itemId, Math.min(4096, quantity));
        touchWebshopSession(session);
        return new ShopActionResult(true, "Cart quantity updated.");
    }

    public static ShopActionResult webshopRemoveFromCart(MinecraftServer server,
                                                         CentralBank centralBank,
                                                         UUID playerId,
                                                         String itemIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        String itemId = itemIdRaw == null ? "" : itemIdRaw.trim().toLowerCase(Locale.ROOT);
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        if (itemId.isBlank() || !session.cart.containsKey(itemId)) {
            return new ShopActionResult(false, "That item is not in your cart.");
        }
        session.cart.remove(itemId);
        touchWebshopSession(session);
        return new ShopActionResult(true, "Item removed from cart.");
    }

    public static ShopActionResult webshopClearCart(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID playerId) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        session.cart.clear();
        touchWebshopSession(session);
        return new ShopActionResult(true, "Cart cleared.");
    }

    public static ShopActionResult webshopSelectAccount(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID playerId,
                                                        String accountIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        UUID accountId = parseOptionalUuid(accountIdRaw);
        if (accountId == null) {
            return new ShopActionResult(false, "Invalid account selection.");
        }
        List<AccountHolder> accounts = collectPlayerAccountsForWebshop(centralBank, playerId);
        boolean ownsAccount = accounts.stream().anyMatch(a -> a != null && accountId.equals(a.getAccountUUID()));
        if (!ownsAccount) {
            return new ShopActionResult(false, "That account does not belong to this player.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        session.selectedAccountId = accountId;
        touchWebshopSession(session);
        return new ShopActionResult(true, "Checkout account selected.");
    }

    public static ShopActionResult webshopSetDeliveryMode(MinecraftServer server,
                                                          CentralBank centralBank,
                                                          UUID playerId,
                                                          String modeRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        session.deliveryMode = normalizeWebshopMode(modeRaw);
        if (!WEBSHOP_MODE_PALLET_SPECIFIC.equals(session.deliveryMode)) {
            session.selectedPalletId = "";
        }
        touchWebshopSession(session);
        return new ShopActionResult(true, "Delivery mode set to " + prettyWebshopMode(session.deliveryMode) + ".");
    }

    public static ShopActionResult webshopSetDeliveryCoordinates(MinecraftServer server,
                                                                 CentralBank centralBank,
                                                                 UUID playerId,
                                                                 String encoded) {
        return new ShopActionResult(false, "Coordinates delivery mode has been removed. Use assigned delivery pallets.");
    }

    public static ShopActionResult webshopSelectShop(MinecraftServer server,
                                                     CentralBank centralBank,
                                                     UUID playerId,
                                                     String shopIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        UUID shopId = parseOptionalUuid(shopIdRaw);
        if (shopId == null) {
            return new ShopActionResult(false, "Invalid shop selection.");
        }
        List<CompoundTag> ownedShops = getOwnerShops(centralBank, playerId);
        CompoundTag selected = resolveOwnedShopById(ownedShops, shopId);
        if (selected == null) {
            return new ShopActionResult(false, "That shop is not owned by this player.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        session.selectedShopId = shopId;
        session.selectedPalletId = "";
        session.deliveryMode = normalizeWebshopMode(session.deliveryMode);
        touchWebshopSession(session);
        return new ShopActionResult(true, "Selected shop " + selected.getString(TAG_NAME) + ".");
    }

    public static ShopActionResult webshopSelectPallet(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID playerId,
                                                       String palletIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        String palletId = normalizeDeliveryPalletId(palletIdRaw);
        if (palletId.isBlank()) {
            return new ShopActionResult(false, "Invalid pallet selection.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        if (session.selectedShopId == null) {
            return new ShopActionResult(false, "Select a shop before selecting a delivery pallet.");
        }
        CompoundTag selectedShop = resolveShopTag(centralBank, playerId, session.selectedShopId);
        if (selectedShop == null || !selectedShop.contains(TAG_ID)) {
            return new ShopActionResult(false, "Selected shop is unavailable.");
        }
        Set<String> assigned = collectAssignedPalletRefSet(selectedShop);
        if (!assigned.contains(palletId)) {
            return new ShopActionResult(false, "Selected pallet is not assigned to that shop.");
        }
        session.deliveryMode = WEBSHOP_MODE_PALLET_SPECIFIC;
        session.selectedPalletId = palletId;
        touchWebshopSession(session);
        return new ShopActionResult(true, "Selected delivery pallet " + shortPalletId(palletId) + ".");
    }

    public static ShopActionResult webshopSetExpedite(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      UUID playerId,
                                                      String enabledRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        boolean enabled = "1".equals(enabledRaw)
                || "true".equalsIgnoreCase(enabledRaw)
                || "yes".equalsIgnoreCase(enabledRaw)
                || "on".equalsIgnoreCase(enabledRaw);
        session.expedite = enabled;
        touchWebshopSession(session);
        return new ShopActionResult(true, enabled ? "Expedite delivery enabled." : "Expedite delivery disabled.");
    }

    public static ShopActionResult webshopCheckout(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   UUID playerId) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        WebshopSessionState session = getOrCreateWebshopSession(server, playerId);
        Map<String, WebshopCatalogItem> catalog = buildWebshopCatalogMap();
        List<WebshopCartLine> cartLines = buildWebshopCartLines(session, catalog);
        if (cartLines.isEmpty()) {
            return new ShopActionResult(false, "Cart is empty.");
        }

        long subtotalCents = 0L;
        for (WebshopCartLine line : cartLines) {
            subtotalCents = safeAdd(subtotalCents, Math.max(0L, line.lineTotalCents()));
        }
        long surchargeCents = webshopExpediteSurchargeCents(subtotalCents, session.expedite);
        long totalCents = safeAdd(subtotalCents, surchargeCents);
        if (totalCents <= 0L) {
            return new ShopActionResult(false, "Checkout total must be above $0.00.");
        }

        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orderTags = getOrCreateWebshopOrders(root);
        int activeOrders = countActiveWebshopOrders(orderTags, playerId);
        if (activeOrders >= maxActiveWebshopOrders()) {
            return new ShopActionResult(false,
                    "Queued order limit reached (" + maxActiveWebshopOrders() + "). Wait for delivery or cancel one.");
        }

        List<AccountHolder> accounts = collectPlayerAccountsForWebshop(centralBank, playerId);
        AccountHolder selectedAccount = resolveWebshopCheckoutAccount(accounts, session.selectedAccountId);
        if (selectedAccount == null) {
            return new ShopActionResult(false, "Select a checkout account first.");
        }
        if (selectedAccount.getBalance() == null
                || selectedAccount.getBalance().compareTo(BigDecimal.valueOf(totalCents, 2)) < 0) {
            return new ShopActionResult(false, "Insufficient account funds for this checkout.");
        }

        int boxCount = estimateWebshopBoxCount(cartLines);
        if (boxCount <= 0) {
            return new ShopActionResult(false, "Cart items cannot be packed into delivery boxes.");
        }

        String mode = normalizeWebshopMode(session.deliveryMode);
        UUID selectedShopId = session.selectedShopId;
        String selectedPalletId = normalizeDeliveryPalletId(session.selectedPalletId);
        CompoundTag selectedShop = null;
        selectedShop = resolveShopTag(centralBank, playerId, selectedShopId);
        if (selectedShop == null || !selectedShop.contains(TAG_ID)) {
            return new ShopActionResult(false, "Select one of your shops in Retail Webshop > Delivery before checkout.");
        }
        Set<String> assigned = collectAssignedPalletRefSet(selectedShop);
        if (assigned.isEmpty()) {
            return new ShopActionResult(false,
                    "Selected shop has no delivery pallets assigned. Open Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets, use the Delivery Pallet Claim Tool on a pallet inside the shop claim, then Paper=Save.");
        }
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(selectedShop));
        if (WEBSHOP_MODE_PALLET_SPECIFIC.equals(mode)) {
            if (selectedPalletId.isBlank()) {
                return new ShopActionResult(false, "Select a specific delivery pallet in Retail Webshop > Delivery, or switch mode to Random Pallet.");
            }
            if (!assigned.contains(selectedPalletId)) {
                return new ShopActionResult(false,
                        "Selected pallet is not assigned to that shop. Reassign it in Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets.");
            }
            PalletRef selectedRef = liveLookup.get(selectedPalletId);
            if (selectedRef == null) {
                return new ShopActionResult(false,
                        "Selected pallet is assigned but not found in the loaded shop claim. Place/load that pallet inside the shop claim or pick another pallet.");
            }
            int freeSlots = countPalletFreeSlots(server, selectedRef);
            if (freeSlots < boxCount) {
                return new ShopActionResult(false,
                        "Selected pallet has " + freeSlots + " free slot(s), but this cart needs "
                                + formatWebshopBoxSlotCount(boxCount) + ". Clear boxes or choose another pallet.");
            }
        } else {
            int liveAssigned = 0;
            int freeSlots = 0;
            for (String palletId : assigned) {
                PalletRef ref = liveLookup.get(palletId);
                if (ref == null) {
                    continue;
                }
                liveAssigned++;
                freeSlots += Math.max(0, countPalletFreeSlots(server, ref));
            }
            if (liveAssigned <= 0) {
                return new ShopActionResult(false,
                        "Assigned delivery pallets were not found in the loaded shop claim. Place/load a pallet inside the claim, then save it with the Delivery Pallet Claim Tool.");
            }
            if (freeSlots < boxCount) {
                return new ShopActionResult(false,
                        "Assigned delivery pallets have " + freeSlots + " free slot(s), but this cart needs "
                                + formatWebshopBoxSlotCount(boxCount) + ". Clear boxes or assign more pallets.");
            }
            selectedPalletId = "";
        }

        BigDecimal charge = BigDecimal.valueOf(totalCents, 2);
        if (!selectedAccount.RemoveBalance(charge)) {
            return new ShopActionResult(false, "Failed to charge checkout account.");
        }

        UUID orderId = UUID.randomUUID();
        int etaSeconds = session.expedite ? webshopExpediteEtaSeconds() : webshopDefaultEtaSeconds();
        long now = System.currentTimeMillis();
        long etaAt = safeAdd(now, Math.max(3L, etaSeconds) * 1000L);
        UserTransaction checkoutTx = new UserTransaction(
                selectedAccount.getAccountUUID(),
                selectedAccount.getAccountUUID(),
                charge,
                LocalDateTime.now(),
                "SHOP_WEBSHOP_CHECKOUT:" + shortUuid(orderId)
        );
        selectedAccount.addTransaction(checkoutTx);

        CompoundTag order = new CompoundTag();
        order.putUUID(TAG_WEBSHOP_ORDER_ID, orderId);
        order.putUUID(TAG_WEBSHOP_ORDER_BUYER, playerId);
        order.putUUID(TAG_WEBSHOP_ORDER_ACCOUNT, selectedAccount.getAccountUUID());
        order.putLong(TAG_WEBSHOP_ORDER_SUBTOTAL_CENTS, Math.max(0L, subtotalCents));
        order.putLong(TAG_WEBSHOP_ORDER_SURCHARGE_CENTS, Math.max(0L, surchargeCents));
        order.putLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS, Math.max(0L, totalCents));
        order.putString(TAG_WEBSHOP_ORDER_STATUS, WEBSHOP_STATUS_QUEUED);
        order.putLong(TAG_WEBSHOP_ORDER_CREATED_AT, now);
        order.putLong(TAG_WEBSHOP_ORDER_ETA_AT, etaAt);
        order.putString(TAG_WEBSHOP_ORDER_MODE, mode);
        if (selectedShopId != null) {
            order.putUUID(TAG_WEBSHOP_ORDER_SHOP_ID, selectedShopId);
        }
        if (!selectedPalletId.isBlank()) {
            order.putString(TAG_WEBSHOP_ORDER_PALLET_ID, selectedPalletId);
        }
        order.putString(TAG_WEBSHOP_ORDER_DIM, normalizedDim(session.deliveryDimensionId));
        order.putInt(TAG_WEBSHOP_ORDER_X, session.deliveryPos.getX());
        order.putInt(TAG_WEBSHOP_ORDER_Y, session.deliveryPos.getY());
        order.putInt(TAG_WEBSHOP_ORDER_Z, session.deliveryPos.getZ());
        order.putInt(TAG_WEBSHOP_ORDER_ATTEMPTS, 0);

        ListTag items = new ListTag();
        for (WebshopCartLine line : cartLines) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.putString(TAG_WEBSHOP_ITEM_ID, line.itemId());
            itemTag.putString(TAG_WEBSHOP_ITEM_NAME, line.itemName());
            itemTag.putInt(TAG_WEBSHOP_ITEM_QTY, Math.max(0, line.quantity()));
            itemTag.putLong(TAG_WEBSHOP_ITEM_UNIT_CENTS, Math.max(0L, line.unitPriceCents()));
            items.add(itemTag);
        }
        order.put(TAG_WEBSHOP_ORDER_ITEMS, items);
        orderTags.add(order);
        root.put(TAG_WEBSHOP_ORDERS, orderTags);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);

        session.cart.clear();
        touchWebshopSession(session);
        return new ShopActionResult(true,
                "Order placed: " + shortUuid(orderId)
                        + " | Total " + MoneyText.abbreviateWithDollar(charge)
                        + " | ETA ~" + Math.max(1, etaSeconds) + "s.");
    }

    public static ShopActionResult webshopCancelOrder(MinecraftServer server,
                                                      CentralBank centralBank,
                                                      UUID playerId,
                                                      String orderIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        UUID orderId = parseOptionalUuid(orderIdRaw);
        if (orderId == null) {
            return new ShopActionResult(false, "Invalid order selection.");
        }

        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orders = getOrCreateWebshopOrders(root);
        for (int i = 0; i < orders.size(); i++) {
            if (!(orders.get(i) instanceof CompoundTag order)) {
                continue;
            }
            if (!order.contains(TAG_WEBSHOP_ORDER_ID) || !order.contains(TAG_WEBSHOP_ORDER_BUYER)) {
                continue;
            }
            UUID id;
            UUID buyer;
            try {
                id = order.getUUID(TAG_WEBSHOP_ORDER_ID);
                buyer = order.getUUID(TAG_WEBSHOP_ORDER_BUYER);
            } catch (Exception ignored) {
                continue;
            }
            if (!orderId.equals(id) || !playerId.equals(buyer)) {
                continue;
            }
            String status = normalizeWebshopStatus(order.getString(TAG_WEBSHOP_ORDER_STATUS));
            if (!WEBSHOP_STATUS_QUEUED.equals(status)) {
                return new ShopActionResult(false, "Only queued webshop orders can be canceled.");
            }

            long totalCents = Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS));
            long feeCents = webshopCancelFeeCents(totalCents);
            long refundCents = Math.max(0L, totalCents - feeCents);
            UUID accountId = order.contains(TAG_WEBSHOP_ORDER_ACCOUNT) ? order.getUUID(TAG_WEBSHOP_ORDER_ACCOUNT) : null;
            if (accountId != null && refundCents > 0L) {
                AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
                if (account != null && account.forceAddBalance(BigDecimal.valueOf(refundCents, 2))) {
                    UserTransaction tx = new UserTransaction(
                            accountId,
                            accountId,
                            BigDecimal.valueOf(refundCents, 2),
                            LocalDateTime.now(),
                            "SHOP_WEBSHOP_CANCEL_REFUND:" + shortUuid(orderId)
                    );
                    account.addTransaction(tx);
                }
            }
            order.putString(TAG_WEBSHOP_ORDER_STATUS, WEBSHOP_STATUS_CANCELED);
            order.putLong(TAG_WEBSHOP_ORDER_CANCELED_AT, System.currentTimeMillis());
            order.putString(TAG_WEBSHOP_ORDER_LAST_ERROR, feeCents <= 0L
                    ? "Canceled by buyer"
                    : ("Canceled by buyer, fee $" + ShelfPrice.abbreviateFromCents(feeCents)));
            root.put(TAG_WEBSHOP_ORDERS, orders);
            centralMeta.put(TAG_ROOT, root);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
            return new ShopActionResult(true,
                    "Order canceled. Refund: "
                            + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(refundCents, 2))
                            + (feeCents > 0L ? (" (fee " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(feeCents, 2)) + ").") : "."));
        }
        return new ShopActionResult(false, "Order not found.");
    }

    public static ShopActionResult webshopReplaceOrder(MinecraftServer server,
                                                       CentralBank centralBank,
                                                       UUID playerId,
                                                       String orderIdRaw) {
        if (server == null || centralBank == null || playerId == null) {
            return new ShopActionResult(false, "Webshop service is unavailable.");
        }
        UUID sourceOrderId = parseOptionalUuid(orderIdRaw);
        if (sourceOrderId == null) {
            return new ShopActionResult(false, "Invalid order selection.");
        }

        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orders = getOrCreateWebshopOrders(root);
        if (countActiveWebshopOrders(orders, playerId) >= maxActiveWebshopOrders()) {
            return new ShopActionResult(false,
                    "Queued order limit reached (" + maxActiveWebshopOrders() + "). Wait for delivery or cancel one.");
        }

        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag source)) {
                continue;
            }
            UUID id;
            UUID buyer;
            try {
                id = source.getUUID(TAG_WEBSHOP_ORDER_ID);
                buyer = source.getUUID(TAG_WEBSHOP_ORDER_BUYER);
            } catch (Exception ignored) {
                continue;
            }
            if (!sourceOrderId.equals(id) || !playerId.equals(buyer)) {
                continue;
            }

            String status = normalizeWebshopStatus(source.getString(TAG_WEBSHOP_ORDER_STATUS));
            if (WEBSHOP_STATUS_QUEUED.equals(status)) {
                return new ShopActionResult(false, "Queued orders are already active. Cancel that order instead.");
            }

            List<WebshopCartLine> sourceLines = readWebshopCartLines(source);
            if (sourceLines.isEmpty()) {
                return new ShopActionResult(false, "Selected order has no saved item lines to replace.");
            }
            int boxCount = estimateWebshopBoxCount(sourceLines);
            if (boxCount <= 0) {
                return new ShopActionResult(false, "Selected order cannot be packed into delivery boxes.");
            }

            UUID accountId;
            try {
                accountId = source.contains(TAG_WEBSHOP_ORDER_ACCOUNT) ? source.getUUID(TAG_WEBSHOP_ORDER_ACCOUNT) : null;
            } catch (Exception ignored) {
                accountId = null;
            }
            if (accountId == null) {
                return new ShopActionResult(false, "Selected order has no saved checkout account.");
            }
            AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
            if (account == null) {
                return new ShopActionResult(false, "Saved checkout account no longer exists.");
            }

            long subtotalCents = Math.max(0L, source.getLong(TAG_WEBSHOP_ORDER_SUBTOTAL_CENTS));
            if (subtotalCents <= 0L) {
                for (WebshopCartLine line : sourceLines) {
                    subtotalCents = safeAdd(subtotalCents, Math.max(0L, line.lineTotalCents()));
                }
            }
            long surchargeCents = Math.max(0L, source.getLong(TAG_WEBSHOP_ORDER_SURCHARGE_CENTS));
            long totalCents = Math.max(0L, source.getLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS));
            if (totalCents <= 0L) {
                totalCents = safeAdd(subtotalCents, surchargeCents);
            }
            if (totalCents <= 0L) {
                return new ShopActionResult(false, "Replacement total must be above $0.00.");
            }
            BigDecimal charge = BigDecimal.valueOf(totalCents, 2);
            if (account.getBalance() == null || account.getBalance().compareTo(charge) < 0) {
                return new ShopActionResult(false, "Insufficient account funds for replacement order.");
            }

            String mode = normalizeWebshopMode(source.getString(TAG_WEBSHOP_ORDER_MODE));
            UUID selectedShopId = null;
            try {
                selectedShopId = source.contains(TAG_WEBSHOP_ORDER_SHOP_ID) ? source.getUUID(TAG_WEBSHOP_ORDER_SHOP_ID) : null;
            } catch (Exception ignored) {
                selectedShopId = null;
            }
            CompoundTag selectedShop = resolveShopTag(centralBank, playerId, selectedShopId);
            if (selectedShop == null || !selectedShop.contains(TAG_ID)) {
                return new ShopActionResult(false, "Saved delivery shop no longer exists or is no longer owned by you.");
            }
            Set<String> assigned = collectAssignedPalletRefSet(selectedShop);
            if (assigned.isEmpty()) {
                return new ShopActionResult(false,
                        "Saved delivery shop has no delivery pallets assigned. Assign pallets before replacing this order.");
            }
            Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(selectedShop));
            String selectedPalletId = normalizeDeliveryPalletId(source.getString(TAG_WEBSHOP_ORDER_PALLET_ID));
            if (WEBSHOP_MODE_PALLET_SPECIFIC.equals(mode)) {
                if (selectedPalletId.isBlank()) {
                    return new ShopActionResult(false, "Saved specific pallet target is missing.");
                }
                if (!assigned.contains(selectedPalletId)) {
                    return new ShopActionResult(false, "Saved pallet is no longer assigned to that shop.");
                }
                PalletRef selectedRef = liveLookup.get(selectedPalletId);
                if (selectedRef == null) {
                    return new ShopActionResult(false, "Saved pallet is assigned but not currently loaded in the shop claim.");
                }
                int freeSlots = countPalletFreeSlots(server, selectedRef);
                if (freeSlots < boxCount) {
                    return new ShopActionResult(false,
                            "Saved pallet has " + freeSlots + " free slot(s), but this replacement needs "
                                    + formatWebshopBoxSlotCount(boxCount) + ".");
                }
            } else {
                int liveAssigned = 0;
                int freeSlots = 0;
                for (String palletId : assigned) {
                    PalletRef ref = liveLookup.get(palletId);
                    if (ref == null) {
                        continue;
                    }
                    liveAssigned++;
                    freeSlots += Math.max(0, countPalletFreeSlots(server, ref));
                }
                if (liveAssigned <= 0) {
                    return new ShopActionResult(false, "No assigned delivery pallets are currently loaded for the saved shop.");
                }
                if (freeSlots < boxCount) {
                    return new ShopActionResult(false,
                            "Assigned delivery pallets have " + freeSlots + " free slot(s), but this replacement needs "
                                    + formatWebshopBoxSlotCount(boxCount) + ".");
                }
                selectedPalletId = "";
            }

            if (!account.RemoveBalance(charge)) {
                return new ShopActionResult(false, "Failed to charge checkout account for replacement.");
            }

            UUID newOrderId = UUID.randomUUID();
            boolean expedited = surchargeCents > 0L;
            int etaSeconds = expedited ? webshopExpediteEtaSeconds() : webshopDefaultEtaSeconds();
            long now = System.currentTimeMillis();
            long etaAt = safeAdd(now, Math.max(3L, etaSeconds) * 1000L);
            UserTransaction checkoutTx = new UserTransaction(
                    account.getAccountUUID(),
                    account.getAccountUUID(),
                    charge,
                    LocalDateTime.now(),
                    "SHOP_WEBSHOP_REPLACE:" + shortUuid(sourceOrderId) + "->" + shortUuid(newOrderId)
            );
            account.addTransaction(checkoutTx);

            CompoundTag replacement = new CompoundTag();
            replacement.putUUID(TAG_WEBSHOP_ORDER_ID, newOrderId);
            replacement.putUUID(TAG_WEBSHOP_ORDER_BUYER, playerId);
            replacement.putUUID(TAG_WEBSHOP_ORDER_ACCOUNT, account.getAccountUUID());
            replacement.putLong(TAG_WEBSHOP_ORDER_SUBTOTAL_CENTS, subtotalCents);
            replacement.putLong(TAG_WEBSHOP_ORDER_SURCHARGE_CENTS, surchargeCents);
            replacement.putLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS, totalCents);
            replacement.putString(TAG_WEBSHOP_ORDER_STATUS, WEBSHOP_STATUS_QUEUED);
            replacement.putLong(TAG_WEBSHOP_ORDER_CREATED_AT, now);
            replacement.putLong(TAG_WEBSHOP_ORDER_ETA_AT, etaAt);
            replacement.putString(TAG_WEBSHOP_ORDER_MODE, mode);
            if (selectedShopId != null) {
                replacement.putUUID(TAG_WEBSHOP_ORDER_SHOP_ID, selectedShopId);
            }
            if (!selectedPalletId.isBlank()) {
                replacement.putString(TAG_WEBSHOP_ORDER_PALLET_ID, selectedPalletId);
            }
            replacement.putString(TAG_WEBSHOP_ORDER_DIM, normalizedDim(source.getString(TAG_WEBSHOP_ORDER_DIM)));
            replacement.putInt(TAG_WEBSHOP_ORDER_X, source.getInt(TAG_WEBSHOP_ORDER_X));
            replacement.putInt(TAG_WEBSHOP_ORDER_Y, source.getInt(TAG_WEBSHOP_ORDER_Y));
            replacement.putInt(TAG_WEBSHOP_ORDER_Z, source.getInt(TAG_WEBSHOP_ORDER_Z));
            replacement.putInt(TAG_WEBSHOP_ORDER_ATTEMPTS, 0);

            ListTag copiedItems = new ListTag();
            ListTag sourceItems = source.getList(TAG_WEBSHOP_ORDER_ITEMS, Tag.TAG_COMPOUND);
            for (Tag itemTag : sourceItems) {
                if (itemTag instanceof CompoundTag item) {
                    copiedItems.add(item.copy());
                }
            }
            replacement.put(TAG_WEBSHOP_ORDER_ITEMS, copiedItems);
            orders.add(replacement);
            root.put(TAG_WEBSHOP_ORDERS, orders);
            centralMeta.put(TAG_ROOT, root);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
            return new ShopActionResult(true,
                    "Replacement order placed: " + shortUuid(newOrderId)
                            + " | Total " + MoneyText.abbreviateWithDollar(charge)
                            + " | ETA ~" + Math.max(1, etaSeconds) + "s.");
        }
        return new ShopActionResult(false, "Order not found.");
    }

    private static WebshopSessionState getOrCreateWebshopSession(MinecraftServer server, UUID playerId) {
        WebshopSessionState session = WEBSHOP_SESSIONS.computeIfAbsent(playerId, WebshopSessionState::new);
        if (server != null && server.getPlayerList() != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && (session.deliveryPos == null || BlockPos.ZERO.equals(session.deliveryPos))) {
                session.deliveryPos = player.blockPosition();
                session.deliveryDimensionId = normalizedDim(player.serverLevel().dimension().location().toString());
            }
        }
        touchWebshopSession(session);
        return session;
    }

    private static void touchWebshopSession(WebshopSessionState session) {
        if (session == null) {
            return;
        }
        session.updatedAtMillis = System.currentTimeMillis();
    }

    private static List<AccountHolder> collectPlayerAccountsForWebshop(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return List.of();
        }
        Map<UUID, AccountHolder> accounts = centralBank.SearchForAccount(playerId);
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        List<AccountHolder> sorted = new ArrayList<>();
        for (AccountHolder account : accounts.values()) {
            if (account != null && playerId.equals(account.getPlayerUUID())) {
                sorted.add(account);
            }
        }
        sorted.sort(Comparator
                .comparing(AccountHolder::isPrimaryAccount).reversed()
                .thenComparing(a -> a.getDateOfCreation() == null ? "" : a.getDateOfCreation().toString(), String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    private static String normalizeWebshopMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEBSHOP_MODE_PALLET_RANDOM;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case WEBSHOP_MODE_PALLET_RANDOM -> WEBSHOP_MODE_PALLET_RANDOM;
            case WEBSHOP_MODE_PALLET_SPECIFIC -> WEBSHOP_MODE_PALLET_SPECIFIC;
            default -> WEBSHOP_MODE_PALLET_RANDOM;
        };
    }

    private static String normalizeWebshopStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return WEBSHOP_STATUS_QUEUED;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case WEBSHOP_STATUS_DELIVERED -> WEBSHOP_STATUS_DELIVERED;
            case WEBSHOP_STATUS_CANCELED -> WEBSHOP_STATUS_CANCELED;
            case WEBSHOP_STATUS_FAILED -> WEBSHOP_STATUS_FAILED;
            default -> WEBSHOP_STATUS_QUEUED;
        };
    }

    private static String prettyWebshopMode(String mode) {
        return switch (normalizeWebshopMode(mode)) {
            case WEBSHOP_MODE_PALLET_RANDOM -> "Random Delivery Pallet";
            case WEBSHOP_MODE_PALLET_SPECIFIC -> "Specific Delivery Pallet";
            default -> "Random Delivery Pallet";
        };
    }

    private static Map<String, WebshopCatalogItem> buildWebshopCatalogMap() {
        List<WebshopCatalogItem> list = new ArrayList<>();
        addWebshopCatalogEntry(list, ModBlocks.SHOP_SHELF.get().asItem(), 12_500L, "Shelving", "Core retail shelf unit.");
        addWebshopCatalogEntry(list, ModBlocks.TALL_WALL_SHELF.get().asItem(), 16_900L, "Shelving", "Wall-mounted tall display shelf.");
        addWebshopCatalogEntry(list, ModBlocks.MODULAR_WALL_DISPLAY.get().asItem(), 19_900L, "Shelving", "Modular wall display panel.");
        addWebshopCatalogEntry(list, ModBlocks.GLASS_COUNTER_DISPLAY.get().asItem(), 25_000L, "Shelving", "Counter display with glass shelves.");
        addWebshopCatalogEntry(list, ModBlocks.GLASS_COUNTER_DISPLAY_OPEN.get().asItem(), 25_000L, "Shelving", "Open glass counter display.");
        addWebshopCatalogEntry(list, ModBlocks.SHOP_SELLING_TABLE.get().asItem(), 22_000L, "Displays", "Single-item showcase table.");
        addWebshopCatalogEntry(list, ModBlocks.SHOP_SELLING_TABLE_LARGE.get().asItem(), 39_000L, "Displays", "Large 2x2 hero showcase table.");
        addWebshopCatalogEntry(list, ModBlocks.INVISIBLE_DISPLAY_SMALL.get().asItem(), 14_500L, "Displays", "Invisible small single-item display.");
        addWebshopCatalogEntry(list, ModBlocks.INVISIBLE_DISPLAY_MEDIUM.get().asItem(), 18_500L, "Displays", "Invisible medium single-item display.");
        addWebshopCatalogEntry(list, ModBlocks.INVISIBLE_DISPLAY_LARGE.get().asItem(), 23_500L, "Displays", "Invisible large single-item display.");
        addWebshopCatalogEntry(list, ModBlocks.PALLET.get().asItem(), 9_000L, "Logistics", "3x3 pallet for stockroom and deliveries.");
        addWebshopCatalogEntry(list, ModBlocks.CARDBOARD_BOX.get().asItem(), 400L, "Logistics", "18-slot box for stock transfer.");
        addWebshopCatalogEntry(list, ModBlocks.SHOPPING_BASKET_HOLDER.get().asItem(), 18_000L, "Checkout", "Basket pickup/return stand.");
        addWebshopCatalogEntry(list, ModBlocks.SHOPPING_BAG.get().asItem(), 250L, "Checkout", "One-row takeaway bag.");
        addWebshopCatalogEntry(list, ModBlocks.PAYMENT_TERMINAL.get().asItem(), 95_000L, "Checkout", "Card terminal for cashier flows.");

        Map<String, WebshopCatalogItem> out = new LinkedHashMap<>();
        for (WebshopCatalogItem item : list) {
            if (item == null || item.itemId() == null || item.itemId().isBlank()) {
                continue;
            }
            out.put(item.itemId().trim().toLowerCase(Locale.ROOT), item);
        }
        return out;
    }

    private static void addWebshopCatalogEntry(List<WebshopCatalogItem> out,
                                               net.minecraft.world.item.Item item,
                                               long unitPriceCents,
                                               String category,
                                               String description) {
        if (out == null || item == null) {
            return;
        }
        String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(item));
        if (itemId == null || itemId.isBlank() || "minecraft:air".equalsIgnoreCase(itemId)) {
            return;
        }
        ItemStack preview = new ItemStack(item);
        String name = preview.getHoverName().getString();
        out.add(new WebshopCatalogItem(
                itemId.trim().toLowerCase(Locale.ROOT),
                sanitizeTokenText(name),
                sanitizeTokenText(category),
                Math.max(0L, unitPriceCents),
                sanitizeTokenText(description)
        ));
    }

    private static List<WebshopCartLine> buildWebshopCartLines(WebshopSessionState session,
                                                                Map<String, WebshopCatalogItem> catalogMap) {
        if (session == null || session.cart.isEmpty() || catalogMap == null || catalogMap.isEmpty()) {
            return List.of();
        }
        List<WebshopCartLine> out = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : session.cart.entrySet()) {
            if (entry == null) {
                continue;
            }
            String itemId = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
            int qty = Math.max(0, entry.getValue() == null ? 0 : entry.getValue());
            if (itemId.isBlank() || qty <= 0) {
                continue;
            }
            WebshopCatalogItem catalogItem = catalogMap.get(itemId);
            if (catalogItem == null) {
                continue;
            }
            long lineTotal = safeAdd(0L, Math.max(0L, catalogItem.unitPriceCents()) * (long) qty);
            out.add(new WebshopCartLine(
                    catalogItem.itemId(),
                    catalogItem.itemName(),
                    qty,
                    Math.max(0L, catalogItem.unitPriceCents()),
                    Math.max(0L, lineTotal)
            ));
        }
        return out;
    }

    private static AccountHolder resolveWebshopCheckoutAccount(List<AccountHolder> accounts, UUID selectedAccountId) {
        if (accounts == null || accounts.isEmpty()) {
            return null;
        }
        if (selectedAccountId != null) {
            for (AccountHolder account : accounts) {
                if (account != null && selectedAccountId.equals(account.getAccountUUID())) {
                    return account;
                }
            }
        }
        for (AccountHolder account : accounts) {
            if (account != null && account.isPrimaryAccount()) {
                return account;
            }
        }
        return accounts.get(0);
    }

    private static CompoundTag resolveOwnedShopById(List<CompoundTag> ownerShops, UUID shopId) {
        if (ownerShops == null || ownerShops.isEmpty() || shopId == null) {
            return null;
        }
        for (CompoundTag shop : ownerShops) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            try {
                if (shopId.equals(shop.getUUID(TAG_ID))) {
                    return shop;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static CompoundTag resolveShopById(CentralBank centralBank, UUID shopId) {
        if (centralBank == null || shopId == null) {
            return null;
        }
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            try {
                if (shopId.equals(shop.getUUID(TAG_ID))) {
                    return shop;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static List<AssignedPalletView> collectDeliveryPalletViews(MinecraftServer server, CompoundTag shop) {
        if (server == null || shop == null) {
            return List.of();
        }
        Set<String> assigned = collectAssignedPalletRefSet(shop);
        if (assigned.isEmpty()) {
            return List.of();
        }
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
        List<AssignedPalletView> out = new ArrayList<>();
        for (String assignedKey : assigned) {
            PalletRef ref = liveLookup.get(assignedKey);
            if (ref == null) {
                continue;
            }
            out.add(new AssignedPalletView(assignedKey, ref));
        }
        out.sort(Comparator
                .comparing((AssignedPalletView p) -> p.ref().dimensionId(), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(p -> p.ref().pos().getX())
                .thenComparingInt(p -> p.ref().pos().getY())
                .thenComparingInt(p -> p.ref().pos().getZ()));
        return out;
    }

    private static ListTag getOrCreateWebshopOrders(CompoundTag root) {
        if (root == null) {
            return new ListTag();
        }
        if (!root.contains(TAG_WEBSHOP_ORDERS, Tag.TAG_LIST)) {
            root.put(TAG_WEBSHOP_ORDERS, new ListTag());
        }
        return root.getList(TAG_WEBSHOP_ORDERS, Tag.TAG_COMPOUND);
    }

    private static int countActiveWebshopOrders(ListTag orders, UUID buyerId) {
        if (orders == null || buyerId == null) {
            return 0;
        }
        int count = 0;
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            if (!order.contains(TAG_WEBSHOP_ORDER_BUYER)) {
                continue;
            }
            try {
                if (!buyerId.equals(order.getUUID(TAG_WEBSHOP_ORDER_BUYER))) {
                    continue;
                }
            } catch (Exception ignored) {
                continue;
            }
            if (WEBSHOP_STATUS_QUEUED.equals(normalizeWebshopStatus(order.getString(TAG_WEBSHOP_ORDER_STATUS)))) {
                count++;
            }
        }
        return count;
    }

    private static List<WebshopOrderEntry> readWebshopOrdersForBuyer(CentralBank centralBank, UUID buyerId) {
        if (centralBank == null || buyerId == null) {
            return List.of();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orders = getOrCreateWebshopOrders(root);
        List<WebshopOrderEntry> out = new ArrayList<>();
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            UUID orderId;
            UUID buyer;
            UUID account = null;
            UUID shopId = null;
            try {
                orderId = order.getUUID(TAG_WEBSHOP_ORDER_ID);
                buyer = order.getUUID(TAG_WEBSHOP_ORDER_BUYER);
                if (order.contains(TAG_WEBSHOP_ORDER_ACCOUNT)) {
                    account = order.getUUID(TAG_WEBSHOP_ORDER_ACCOUNT);
                }
                if (order.contains(TAG_WEBSHOP_ORDER_SHOP_ID)) {
                    shopId = order.getUUID(TAG_WEBSHOP_ORDER_SHOP_ID);
                }
            } catch (Exception ignored) {
                continue;
            }
            if (!buyerId.equals(buyer)) {
                continue;
            }
            out.add(new WebshopOrderEntry(
                    orderId,
                    buyer,
                    account,
                    Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS)),
                    normalizeWebshopStatus(order.getString(TAG_WEBSHOP_ORDER_STATUS)),
                    Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_CREATED_AT)),
                    Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_ETA_AT)),
                    normalizeWebshopMode(order.getString(TAG_WEBSHOP_ORDER_MODE)),
                    shopId,
                    normalizeDeliveryPalletId(order.getString(TAG_WEBSHOP_ORDER_PALLET_ID)),
                    normalizedDim(order.getString(TAG_WEBSHOP_ORDER_DIM)),
                    new BlockPos(order.getInt(TAG_WEBSHOP_ORDER_X), order.getInt(TAG_WEBSHOP_ORDER_Y), order.getInt(TAG_WEBSHOP_ORDER_Z)),
                    Math.max(0, estimateWebshopBoxCount(readWebshopCartLines(order))),
                    Math.max(0, order.getInt(TAG_WEBSHOP_ORDER_ATTEMPTS)),
                    order.contains(TAG_WEBSHOP_ORDER_LAST_ERROR)
                            ? normalizeWebshopDeliveryReason(order.getString(TAG_WEBSHOP_ORDER_LAST_ERROR))
                            : ""
            ));
        }
        out.sort(Comparator.comparingLong(WebshopOrderEntry::createdAtMillis).reversed());
        return out;
    }

    private static List<WebshopCartLine> readWebshopCartLines(CompoundTag order) {
        if (order == null || !order.contains(TAG_WEBSHOP_ORDER_ITEMS, Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag items = order.getList(TAG_WEBSHOP_ORDER_ITEMS, Tag.TAG_COMPOUND);
        List<WebshopCartLine> out = new ArrayList<>();
        for (Tag tag : items) {
            if (!(tag instanceof CompoundTag item)) {
                continue;
            }
            String itemId = item.getString(TAG_WEBSHOP_ITEM_ID);
            String itemName = item.getString(TAG_WEBSHOP_ITEM_NAME);
            int qty = Math.max(0, item.getInt(TAG_WEBSHOP_ITEM_QTY));
            long unit = Math.max(0L, item.getLong(TAG_WEBSHOP_ITEM_UNIT_CENTS));
            if (itemId == null || itemId.isBlank() || qty <= 0) {
                continue;
            }
            out.add(new WebshopCartLine(
                    itemId.trim().toLowerCase(Locale.ROOT),
                    itemName == null || itemName.isBlank() ? itemId : itemName,
                    qty,
                    unit,
                    safeAdd(0L, unit * (long) qty)
            ));
        }
        return out;
    }

    private static String webshopOrderItemSummary(CentralBank centralBank, UUID orderId) {
        if (centralBank == null || orderId == null) {
            return "Saved order items";
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orders = getOrCreateWebshopOrders(root);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order) || !order.contains(TAG_WEBSHOP_ORDER_ID)) {
                continue;
            }
            try {
                if (!orderId.equals(order.getUUID(TAG_WEBSHOP_ORDER_ID))) {
                    continue;
                }
            } catch (Exception ignored) {
                continue;
            }
            List<WebshopCartLine> lines = readWebshopCartLines(order);
            if (lines.isEmpty()) {
                return "Saved order items";
            }
            List<String> labels = new ArrayList<>();
            int shown = 0;
            int remainingLines = 0;
            for (WebshopCartLine line : lines) {
                if (line == null || line.quantity() <= 0) {
                    continue;
                }
                if (shown < 3) {
                    labels.add(line.itemName() + " x" + line.quantity());
                    shown++;
                } else {
                    remainingLines++;
                }
            }
            if (remainingLines > 0) {
                labels.add("+" + remainingLines + " more");
            }
            return labels.isEmpty() ? "Saved order items" : String.join(", ", labels);
        }
        return "Saved order items";
    }

    private static int estimateWebshopBoxCount(List<WebshopCartLine> lines) {
        List<ItemStack> boxes = buildWebshopDeliveryBoxes(lines);
        return boxes.size();
    }

    private static String formatWebshopBoxSlotCount(int requiredBoxes) {
        int count = Math.max(0, requiredBoxes);
        return count + " cardboard box slot" + (count == 1 ? "" : "s");
    }

    private static String normalizeWebshopDeliveryReason(String reason) {
        if (reason == null || reason.isBlank() || "-".equals(reason.trim())) {
            return "Delivery setup is unavailable.";
        }
        return sanitizeTokenText(reason);
    }

    private static String webshopDeliveryFixHint(String reason, int requiredBoxes) {
        String clean = normalizeWebshopDeliveryReason(reason);
        String slotNeed = formatWebshopBoxSlotCount(requiredBoxes);
        return switch (clean) {
            case "Delivery context unavailable." ->
                    "Server delivery data was unavailable. Retry after the server finishes loading, or ask an admin to check the bank/shop data.";
            case "Order has no deliverable items." ->
                    "Add catalog items to the cart and place a new order. This order had nothing that could be packed into cardboard boxes.";
            case "Delivery dimension is unavailable." ->
                    "The saved delivery dimension is not loaded or no longer exists. Place a new order after selecting a valid delivery shop.";
            case "Missing shop binding for pallet delivery." ->
                    "Select a shop in Retail Webshop > Delivery before checkout. For this old order, place a new order after selecting the shop.";
            case "Linked shop no longer exists." ->
                    "The selected shop was deleted or is no longer owned. Recreate/select a shop, then place a new order.";
            case "No assigned delivery pallets available." ->
                    "Open Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets, use the Delivery Pallet Claim Tool on a pallet inside the shop claim, then Paper=Save.";
            case "Specific pallet is no longer assigned." ->
                    "Reassign that pallet in Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets, or choose another pallet in Retail Webshop > Delivery.";
            case "Specific pallet is currently missing.", "Target pallet is not loaded.", "Target pallet data is unavailable." ->
                    "Place/load the assigned pallet inside the shop claim, keep that chunk loaded, or select another live pallet in Retail Webshop > Delivery.";
            case "All assigned delivery pallets are full.", "Target pallet does not have enough free space.", "Pallet became full during delivery." ->
                    "Clear assigned delivery pallet space or assign more pallets. This order needs " + slotNeed + ".";
            case "Pallet dimension is unavailable." ->
                    "Load the dimension containing the assigned delivery pallet, or reassign a pallet in a loaded dimension.";
            default ->
                    "Check Retail Webshop > Delivery and Bank Owner PC > Retail Shop > Order Manager > Delivery Pallets. The order needs " + slotNeed + ".";
        };
    }

    private static List<ItemStack> buildWebshopDeliveryBoxes(List<WebshopCartLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<ItemStack> looseStacks = new ArrayList<>();
        for (WebshopCartLine line : lines) {
            if (line == null || line.quantity() <= 0 || line.itemId() == null || line.itemId().isBlank()) {
                continue;
            }
            net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(line.itemId());
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(key);
            int remaining = Math.max(0, line.quantity());
            int stackMax = Math.max(1, new ItemStack(item).getMaxStackSize());
            while (remaining > 0) {
                int take = Math.min(stackMax, remaining);
                ItemStack stack = new ItemStack(item, take);
                looseStacks.add(stack);
                remaining -= take;
            }
        }
        if (looseStacks.isEmpty()) {
            return List.of();
        }

        List<ItemStack> boxes = new ArrayList<>();
        int index = 0;
        while (index < looseStacks.size()) {
            ItemStackHandler inv = new ItemStackHandler(CardboardBoxBlockEntity.SLOT_COUNT);
            for (int slot = 0; slot < CardboardBoxBlockEntity.SLOT_COUNT && index < looseStacks.size(); slot++) {
                inv.setStackInSlot(slot, looseStacks.get(index).copy());
                index++;
            }
            ItemStack box = new ItemStack(ModBlocks.CARDBOARD_BOX.get().asItem());
            ItemStackDataCompat.putCustomData(CardboardBoxDataKeys.BOX_DATA_KEY, box, ItemStackDataCompat.serializeHandler(inv));
            boxes.add(box);
        }
        return boxes;
    }

    private static void tickWebshopOrders(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag orders = getOrCreateWebshopOrders(root);
        if (orders.isEmpty()) {
            return;
        }

        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            String status = normalizeWebshopStatus(order.getString(TAG_WEBSHOP_ORDER_STATUS));
            if (!WEBSHOP_STATUS_QUEUED.equals(status)) {
                continue;
            }
            long etaAt = Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_ETA_AT));
            if (etaAt > now) {
                continue;
            }

            WebshopDeliveryAttempt attempt = deliverWebshopOrder(server, centralBank, order);
            if (attempt.success()) {
                order.putString(TAG_WEBSHOP_ORDER_STATUS, WEBSHOP_STATUS_DELIVERED);
                order.putLong(TAG_WEBSHOP_ORDER_DELIVERED_AT, now);
                order.remove(TAG_WEBSHOP_ORDER_LAST_ERROR);
                String shortOrderId = shortUuid(readWebshopOrderId(order));
                String chatMessage = "§aWebshop delivery arrived: §fOrder " + shortOrderId + " was delivered.";
                String popupMessage = "Order " + shortOrderId + " delivered";
                notifyWebshopBuyer(server, order, chatMessage, new DeliveryAlertSpec("Delivery Complete", popupMessage, true));
                changed = true;
                continue;
            }

            int attempts = Math.max(0, order.getInt(TAG_WEBSHOP_ORDER_ATTEMPTS)) + 1;
            order.putInt(TAG_WEBSHOP_ORDER_ATTEMPTS, attempts);
            String deliveryReason = normalizeWebshopDeliveryReason(attempt.message());
            int requiredBoxes = estimateWebshopBoxCount(readWebshopCartLines(order));
            String fixHint = webshopDeliveryFixHint(deliveryReason, requiredBoxes);
            order.putString(TAG_WEBSHOP_ORDER_LAST_ERROR, deliveryReason);
            if (!attempt.retryable() || attempts >= webshopMaxRetryAttempts()) {
                order.putString(TAG_WEBSHOP_ORDER_STATUS, WEBSHOP_STATUS_FAILED);
                order.putLong(TAG_WEBSHOP_ORDER_FAILED_AT, now);
                refundWebshopOrder(centralBank, order, "DELIVERY_FAILED:" + deliveryReason);
                String shortOrderId = shortUuid(readWebshopOrderId(order));
                String chatMessage = "§cWebshop delivery failed: §fOrder " + shortOrderId
                        + " was refunded. §7Reason: §f" + deliveryReason
                        + " §7Fix: §e" + fixHint;
                notifyWebshopBuyer(server, order, chatMessage,
                        new DeliveryAlertSpec("Delivery Failed", "Reason: " + deliveryReason, false));
                changed = true;
                continue;
            }

            long nextEta = safeAdd(now, webshopRetryDelaySeconds() * 1000L);
            order.putLong(TAG_WEBSHOP_ORDER_ETA_AT, nextEta);
            changed = true;
        }

        if (changed) {
            root.put(TAG_WEBSHOP_ORDERS, orders);
            centralMeta.put(TAG_ROOT, root);
            centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        }
    }

    private static WebshopDeliveryAttempt deliverWebshopOrder(MinecraftServer server,
                                                              CentralBank centralBank,
                                                              CompoundTag order) {
        if (server == null || centralBank == null || order == null) {
            return new WebshopDeliveryAttempt(false, false, "Delivery context unavailable.");
        }
        List<WebshopCartLine> lines = readWebshopCartLines(order);
        List<ItemStack> boxes = buildWebshopDeliveryBoxes(lines);
        if (boxes.isEmpty()) {
            return new WebshopDeliveryAttempt(false, false, "Order has no deliverable items.");
        }

        String mode = normalizeWebshopMode(order.getString(TAG_WEBSHOP_ORDER_MODE));
        if (WEBSHOP_MODE_COORDS.equals(mode)) {
            ServerLevel level = server.getLevel(serverLevelKey(order.getString(TAG_WEBSHOP_ORDER_DIM)));
            if (level == null) {
                return new WebshopDeliveryAttempt(false, false, "Delivery dimension is unavailable.");
            }
            double x = order.getInt(TAG_WEBSHOP_ORDER_X) + 0.5D;
            double y = order.getInt(TAG_WEBSHOP_ORDER_Y) + 0.8D;
            double z = order.getInt(TAG_WEBSHOP_ORDER_Z) + 0.5D;
            for (ItemStack box : boxes) {
                if (box == null || box.isEmpty()) {
                    continue;
                }
                Containers.dropItemStack(level, x, y, z, box.copy());
            }
            return new WebshopDeliveryAttempt(true, false, "Coordinate delivery complete.");
        }

        UUID shopId;
        try {
            shopId = order.contains(TAG_WEBSHOP_ORDER_SHOP_ID) ? order.getUUID(TAG_WEBSHOP_ORDER_SHOP_ID) : null;
        } catch (Exception ignored) {
            shopId = null;
        }
        if (shopId == null) {
            return new WebshopDeliveryAttempt(false, false, "Missing shop binding for pallet delivery.");
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new WebshopDeliveryAttempt(false, false, "Linked shop no longer exists.");
        }
        Set<String> assignedSet = collectAssignedPalletRefSet(shop);
        if (assignedSet.isEmpty()) {
            return new WebshopDeliveryAttempt(false, true, "No assigned delivery pallets available.");
        }
        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));

        String targetPalletId = normalizeDeliveryPalletId(order.getString(TAG_WEBSHOP_ORDER_PALLET_ID));
        PalletRef targetRef = null;
        if (WEBSHOP_MODE_PALLET_SPECIFIC.equals(mode)) {
            if (targetPalletId.isBlank() || !assignedSet.contains(targetPalletId)) {
                return new WebshopDeliveryAttempt(false, false, "Specific pallet is no longer assigned.");
            }
            targetRef = liveLookup.get(targetPalletId);
            if (targetRef == null) {
                return new WebshopDeliveryAttempt(false, true, "Specific pallet is currently missing.");
            }
        } else {
            for (String palletId : assignedSet) {
                PalletRef ref = liveLookup.get(palletId);
                if (ref == null) {
                    continue;
                }
                if (countPalletFreeSlots(server, ref) >= boxes.size()) {
                    targetRef = ref;
                    targetPalletId = palletId;
                    break;
                }
            }
            if (targetRef == null) {
                return new WebshopDeliveryAttempt(false, true, "All assigned delivery pallets are full.");
            }
        }

        ServerLevel level = server.getLevel(serverLevelKey(targetRef.dimensionId()));
        if (level == null) {
            return new WebshopDeliveryAttempt(false, true, "Pallet dimension is unavailable.");
        }
        BlockState state = level.getBlockState(targetRef.pos());
        if (!state.is(ModBlocks.PALLET.get())) {
            return new WebshopDeliveryAttempt(false, true, "Target pallet is not loaded.");
        }
        BlockEntity blockEntity = level.getBlockEntity(targetRef.pos());
        if (!(blockEntity instanceof PalletBlockEntity pallet)) {
            return new WebshopDeliveryAttempt(false, true, "Target pallet data is unavailable.");
        }
        if (countPalletFreeSlots(pallet) < boxes.size()) {
            return new WebshopDeliveryAttempt(false, true, "Target pallet does not have enough free space.");
        }

        List<Integer> insertedColumns = new ArrayList<>();
        for (ItemStack box : boxes) {
            int column = findFirstPalletColumnWithSpace(pallet);
            if (column < 0 || !pallet.addBoxToColumn(column, box.copy())) {
                rollbackPalletInsertions(pallet, insertedColumns);
                return new WebshopDeliveryAttempt(false, true, "Pallet became full during delivery.");
            }
            insertedColumns.add(column);
        }
        if (!targetPalletId.isBlank()) {
            order.putString(TAG_WEBSHOP_ORDER_PALLET_ID, targetPalletId);
        }
        return new WebshopDeliveryAttempt(true, false, "Pallet delivery complete.");
    }

    private static int findFirstPalletColumnWithSpace(PalletBlockEntity pallet) {
        if (pallet == null) {
            return -1;
        }
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack stack = pallet.getBox(column, layer);
                if (stack == null || stack.isEmpty()) {
                    return column;
                }
            }
        }
        return -1;
    }

    private static int countPalletFreeSlots(MinecraftServer server, PalletRef ref) {
        if (server == null || ref == null) {
            return 0;
        }
        PalletBlockEntity pallet = getPalletBlockEntity(server, ref);
        if (pallet == null) {
            return 0;
        }
        return countPalletFreeSlots(pallet);
    }

    private static int countPalletFreeSlots(PalletBlockEntity pallet) {
        if (pallet == null) {
            return 0;
        }
        int free = 0;
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack stack = pallet.getBox(column, layer);
                if (stack == null || stack.isEmpty()) {
                    free++;
                }
            }
        }
        return free;
    }

    private static void rollbackPalletInsertions(PalletBlockEntity pallet, List<Integer> insertedColumns) {
        if (pallet == null || insertedColumns == null || insertedColumns.isEmpty()) {
            return;
        }
        for (int i = insertedColumns.size() - 1; i >= 0; i--) {
            int column = insertedColumns.get(i);
            pallet.removeBoxFromColumn(column, false);
        }
    }

    private static void refundWebshopOrder(CentralBank centralBank, CompoundTag order, String reason) {
        if (centralBank == null || order == null) {
            return;
        }
        if (!order.contains(TAG_WEBSHOP_ORDER_ACCOUNT)) {
            return;
        }
        UUID accountId;
        try {
            accountId = order.getUUID(TAG_WEBSHOP_ORDER_ACCOUNT);
        } catch (Exception ignored) {
            return;
        }
        long totalCents = Math.max(0L, order.getLong(TAG_WEBSHOP_ORDER_TOTAL_CENTS));
        if (totalCents <= 0L) {
            return;
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(accountId);
        if (account == null) {
            return;
        }
        BigDecimal amount = BigDecimal.valueOf(totalCents, 2);
        if (!account.forceAddBalance(amount)) {
            return;
        }
        UserTransaction tx = new UserTransaction(
                accountId,
                accountId,
                amount,
                LocalDateTime.now(),
                "SHOP_WEBSHOP_REFUND:" + sanitizeTokenText(reason)
        );
        account.addTransaction(tx);
    }

    private static void notifyShopOwnerOrderAccepted(MinecraftServer server,
                                                     CompoundTag shop,
                                                     UUID courierId,
                                                     String itemName,
                                                     int quantity,
                                                     long rewardCents,
                                                     int timeoutMinutes) {
        if (server == null || shop == null || !shop.contains(TAG_OWNER) || server.getPlayerList() == null) {
            return;
        }
        UUID ownerId;
        try {
            ownerId = shop.getUUID(TAG_OWNER);
        } catch (Exception ignored) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        String courierName = resolvePlayerLabel(server, courierId);
        ServerPlayer courier = courierId == null ? null : server.getPlayerList().getPlayer(courierId);
        String distanceLabel = "distance unavailable";
        MutableComponent distanceComponent = UbsTranslations.literal("distance unavailable").withStyle(ChatFormatting.GRAY);
        if (courier != null) {
            int blocksAway = blocksAwayFromShopClaims(
                    shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND),
                    courier.serverLevel().dimension().location().toString(),
                    courier.blockPosition()
            );
            if (blocksAway >= 0) {
                distanceLabel = blocksAway + " block" + (blocksAway == 1 ? "" : "s") + " away";
                distanceComponent = Component.literal(String.valueOf(blocksAway)).withStyle(ChatFormatting.GRAY)
                        .append(UbsTranslations.literal(blocksAway == 1 ? " block away" : " blocks away")
                                .withStyle(ChatFormatting.GRAY));
            } else {
                distanceLabel = "different dimension";
                distanceComponent = UbsTranslations.literal("different dimension").withStyle(ChatFormatting.GRAY);
            }
        }

        String cleanItem = sanitizeTokenText(itemName);
        owner.sendSystemMessage(UbsTranslations.literal("Order accepted: ")
                .withStyle(ChatFormatting.AQUA)
                .append(Component.literal(courierName).withStyle(ChatFormatting.WHITE))
                .append(UbsTranslations.literal(" accepted ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(cleanItem + " x" + Math.max(1, quantity)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(distanceComponent));
        pushShopAlert(
                owner,
                "Order Accepted",
                courierName + " accepted " + cleanItem + " x" + Math.max(1, quantity)
                        + " | " + distanceLabel
                        + " | Reward " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, rewardCents), 2))
                        + " | Time limit " + Math.max(ORDER_TIMEOUT_MINUTES_MIN, timeoutMinutes) + "m",
                DeliveryAlertPayload.AlertTone.INFO,
                5600
        );
    }

    private static void notifyShopOwnerOrderDelivered(MinecraftServer server,
                                                      CompoundTag shop,
                                                      UUID courierId,
                                                      String itemName,
                                                      int quantity,
                                                      long payoutCents) {
        if (server == null || shop == null || !shop.contains(TAG_OWNER) || server.getPlayerList() == null) {
            return;
        }
        UUID ownerId;
        try {
            ownerId = shop.getUUID(TAG_OWNER);
        } catch (Exception ignored) {
            return;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        String courierName = resolvePlayerLabel(server, courierId);
        String cleanItem = sanitizeTokenText(itemName);
        owner.sendSystemMessage(UbsTranslations.literal("Order delivered: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(courierName).withStyle(ChatFormatting.WHITE))
                .append(UbsTranslations.literal(" delivered ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(cleanItem + " x" + Math.max(1, quantity)).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(UbsTranslations.literal("Courier payout: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, payoutCents), 2)))
                        .withStyle(ChatFormatting.GREEN)));
        pushShopAlert(
                owner,
                "Order Delivered",
                courierName + " delivered " + cleanItem + " x" + Math.max(1, quantity)
                        + " | Courier paid "
                        + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, payoutCents), 2)),
                DeliveryAlertPayload.AlertTone.SUCCESS,
                5600
        );
    }

    private static int blocksAwayFromShopClaims(ListTag claims, String dimensionId, BlockPos position) {
        if (claims == null || claims.isEmpty() || position == null) {
            return -1;
        }
        String dim = normalizedDim(dimensionId);
        double closest = Double.POSITIVE_INFINITY;
        // Measure shortest horizontal distance to any claimed shop region so "blocks away"
        // maps to practical player travel distance back to the store.
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);
            double dx = position.getX() < minX ? (minX - position.getX())
                    : (position.getX() > maxX ? (position.getX() - maxX) : 0.0D);
            double dz = position.getZ() < minZ ? (minZ - position.getZ())
                    : (position.getZ() > maxZ ? (position.getZ() - maxZ) : 0.0D);
            double candidate = Math.sqrt((dx * dx) + (dz * dz));
            if (candidate < closest) {
                closest = candidate;
                if (closest <= 0.0D) {
                    break;
                }
            }
        }
        if (!Double.isFinite(closest)) {
            return -1;
        }
        return Math.max(0, (int) Math.ceil(closest));
    }

    private static void notifyWebshopBuyer(MinecraftServer server, CompoundTag order, String message) {
        notifyWebshopBuyer(server, order, message, null);
    }

    private static void notifyWebshopBuyer(MinecraftServer server,
                                           CompoundTag order,
                                           String message,
                                           DeliveryAlertSpec deliveryAlert) {
        if (server == null || order == null || server.getPlayerList() == null || message == null || message.isBlank()) {
            return;
        }
        UUID buyerId;
        try {
            buyerId = order.getUUID(TAG_WEBSHOP_ORDER_BUYER);
        } catch (Exception ignored) {
            return;
        }
        ServerPlayer buyer = server.getPlayerList().getPlayer(buyerId);
        if (buyer != null) {
            buyer.sendSystemMessage(UbsTranslations.literal(message));
            if (deliveryAlert != null && !deliveryAlert.message().isBlank()) {
                ServerNotification.send(
                        buyer,
                        deliveryAlert.title(),
                        stripLegacyFormatting(deliveryAlert.message()),
                        deliveryAlert.success()
                                ? DeliveryAlertPayload.AlertTone.SUCCESS
                                : DeliveryAlertPayload.AlertTone.ERROR,
                        WEBSHOP_DELIVERY_ALERT_DURATION_MS
                );
            }
        }
    }

    private static void pushShopAlert(ServerPlayer player,
                                      String title,
                                      String message,
                                      DeliveryAlertPayload.AlertTone tone) {
        pushShopAlert(player, title, message, tone, 4600);
    }

    private static void pushShopAlert(ServerPlayer player,
                                      String title,
                                      String message,
                                      DeliveryAlertPayload.AlertTone tone,
                                      int durationMs) {
        if (player == null || message == null || message.isBlank()) {
            return;
        }
        ServerNotification.sendLegacy(
                player,
                title == null || title.isBlank() ? "Shop Manager" : title,
                message,
                tone == null ? DeliveryAlertPayload.AlertTone.INFO : tone,
                durationMs
        );
    }

    private static String stripLegacyFormatting(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }

    private record DeliveryAlertSpec(String title, String message, boolean success) {
    }

    private static UUID readWebshopOrderId(CompoundTag order) {
        if (order == null || !order.contains(TAG_WEBSHOP_ORDER_ID)) {
            return null;
        }
        try {
            return order.getUUID(TAG_WEBSHOP_ORDER_ID);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ShopActionResult handlePalletDelivery(MinecraftServer server,
                                                        ServerPlayer courier,
                                                        ServerLevel level,
                                                        BlockPos palletMasterPos,
                                                        ItemStack placedBox) {
        if (server == null || courier == null || level == null || palletMasterPos == null) {
            return new ShopActionResult(false, "Delivery context is unavailable.");
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new ShopActionResult(false, "Bank data is unavailable.");
        }
        FoundAssignedPallet found = findAssignedPalletShop(centralBank, level.dimension().location().toString(), palletMasterPos);
        if (found == null) {
            return new ShopActionResult(false, "This pallet is not assigned for delivery orders.");
        }
        String targetPalletRef = resolvePalletAssignmentKey(level, palletMasterPos, false);
        if (targetPalletRef.isBlank()) {
            targetPalletRef = encodeOrderPalletRef(level.dimension().location().toString(), palletMasterPos);
        }
        CompoundTag shop = found.shopTag();
        long now = System.currentTimeMillis();
        if (expireOrdersInPlace(centralBank, shop, now)) {
            saveShopTag(centralBank, shop);
        }

        BoxDeliverySummary summary = summarizeCardboardBox(placedBox);
        if (!summary.valid()) {
            return new ShopActionResult(false, "Delivery box must contain exactly one item type and no extra items.");
        }

        CompoundTag matched = null;
        String requiredPalletRef = "";
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
            if (!ORDER_STATUS_ACCEPTED.equals(status)) {
                continue;
            }
            UUID acceptedBy = order.contains(TAG_ORDER_ACCEPTED_BY) ? order.getUUID(TAG_ORDER_ACCEPTED_BY) : null;
            if (acceptedBy == null || !acceptedBy.equals(courier.getUUID())) {
                continue;
            }
            String itemId = order.getString(TAG_ORDER_ITEM_ID);
            int qty = Math.max(1, order.getInt(TAG_ORDER_QTY));
            if (!itemId.equalsIgnoreCase(summary.itemId()) || qty != summary.totalCount()) {
                continue;
            }
            String boundRef = normalizeOrderPalletBindingKey(order);
            if (!boundRef.isBlank()) {
                if (boundRef.equalsIgnoreCase(targetPalletRef)) {
                    matched = order;
                    break;
                }
                if (requiredPalletRef.isBlank()) {
                    requiredPalletRef = boundRef;
                }
                continue;
            }
            if (matched == null) {
                matched = order;
            }
        }

        if (matched == null) {
            if (!requiredPalletRef.isBlank()) {
                return new ShopActionResult(false,
                        "This order is bound to pallet " + formatPalletRef(requiredPalletRef) + ".");
            }
            return new ShopActionResult(false, "No matching accepted order for this box. Verify item and exact quantity.");
        }

        UUID ownerId = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
        UUID shopId = shop.contains(TAG_ID) ? shop.getUUID(TAG_ID) : null;
        if (ownerId == null || shopId == null) {
            return new ShopActionResult(false, "Shop ownership metadata is invalid.");
        }
        UUID settlementId = resolveSettlementAccountId(centralBank, ownerId, shopId, null);
        AccountHolder settlement = settlementId == null ? null : centralBank.SearchForAccountByAccountId(settlementId);
        AccountHolder courierPrimary = resolvePrimaryAccountForPlayer(centralBank, courier.getUUID());
        if (courierPrimary == null) {
            return new ShopActionResult(false, "Set a primary account before completing deliveries.");
        }

        long rewardCents = Math.max(0L, matched.getLong(TAG_ORDER_REWARD_CENTS));
        if (rewardCents <= 0L) {
            return new ShopActionResult(false, "Order reward is invalid.");
        }
        CourierProgress previousProgress = readCourierProgress(centralBank, courier.getUUID());
        DeliveryPayout bonus = computeDeliveryPayout(matched, rewardCents, previousProgress.streak(), now);
        long reservedCents = Math.max(0L, matched.getLong(TAG_ORDER_RESERVED_CENTS));
        BigDecimal payoutAmount = BigDecimal.valueOf(bonus.totalPayoutCents(), 2);
        String shopName = sanitizeTokenText(shop.getString(TAG_NAME));
        String payoutWarning = "";

        if (reservedCents > 0L) {
            if (bonus.totalPayoutCents() > reservedCents) {
                return new ShopActionResult(false,
                        "Reserved payout is below required payout. Contact shop owner to recreate this order.");
            }
            AccountHolder reserveSource = resolveOrderReserveSource(centralBank, shop, matched);
            if (reserveSource == null) {
                return new ShopActionResult(false, "Reserved payout account is unavailable.");
            }
            if (!courierPrimary.AddBalance(payoutAmount)) {
                return new ShopActionResult(false, "Courier payout failed. Receiver account may be frozen.");
            }
            // Reserve already left the source account at order creation. Here we only
            // credit the courier and persist a matching statement entry.
            UserTransaction payout = new UserTransaction(
                    reserveSource.getAccountUUID(),
                    courierPrimary.getAccountUUID(),
                    payoutAmount,
                    LocalDateTime.now(),
                    "SHOP_ORDER_DELIVERY:" + shopName
            );
            reserveSource.addTransaction(payout);
            courierPrimary.addTransaction(payout);

            long refundUnusedCents = Math.max(0L, reservedCents - bonus.totalPayoutCents());
            if (refundUnusedCents > 0L) {
                BigDecimal refund = BigDecimal.valueOf(refundUnusedCents, 2);
                if (!reserveSource.forceAddBalance(refund)) {
                    payoutWarning = " Unused reserve release failed; verify settlement account.";
                } else {
                    UserTransaction releaseUnused = new UserTransaction(
                            reserveSource.getAccountUUID(),
                            reserveSource.getAccountUUID(),
                            refund,
                            LocalDateTime.now(),
                            "SHOP_ORDER_RESERVE_UNUSED:" + shopName
                    );
                    reserveSource.addTransaction(releaseUnused);
                }
            }
            clearOrderReservation(matched);
        } else {
            if (settlement == null) {
                return new ShopActionResult(false, "Shop settlement account is unavailable.");
            }
            UserTransaction payout = new UserTransaction(
                    settlement.getAccountUUID(),
                    courierPrimary.getAccountUUID(),
                    payoutAmount,
                    LocalDateTime.now(),
                    "SHOP_ORDER_DELIVERY:" + shopName
            );
            if (!payout.makeTransaction(server)) {
                return new ShopActionResult(false, "Payout failed. Shop settlement account may have insufficient funds.");
            }
        }

        long routeStartedAt = Math.max(0L, matched.getLong(TAG_ORDER_ACCEPTED_AT));
        long routeMillis = routeStartedAt <= 0L ? 0L : Math.max(0L, now - routeStartedAt);
        int routeDistanceBlocks = matched.contains(TAG_ORDER_ROUTE_DISTANCE_BLOCKS)
                ? matched.getInt(TAG_ORDER_ROUTE_DISTANCE_BLOCKS)
                : -1;
        String courierName = courierDisplayName(courier, courier.getUUID());
        matched.putString(TAG_ORDER_STATUS, ORDER_STATUS_COMPLETED);
        matched.putLong(TAG_ORDER_COMPLETED_AT, now);
        matched.putUUID(TAG_ORDER_COMPLETED_BY, courier.getUUID());
        matched.putString(TAG_ORDER_ACCEPTED_BY_NAME, courierName);
        matched.putLong(TAG_ORDER_ROUTE_COMPLETED_MILLIS, routeMillis);
        matched.putLong(TAG_ORDER_BONUS_CENTS, bonus.bonusCents());
        matched.putLong(TAG_ORDER_PAYOUT_CENTS, bonus.totalPayoutCents());
        clearOrderAcceptance(matched);
        saveShopTag(centralBank, shop);
        CourierProgress progress = recordCourierCompletion(
                centralBank,
                courier.getUUID(),
                courierName,
                bonus.totalPayoutCents(),
                routeDistanceBlocks,
                routeMillis,
                now
        );
        notifyShopOwnerOrderDelivered(
                server,
                shop,
                courier.getUUID(),
                sanitizeTokenText(matched.getString(TAG_ORDER_ITEM_NAME)),
                Math.max(1, matched.getInt(TAG_ORDER_QTY)),
                bonus.totalPayoutCents()
        );

        courier.sendSystemMessage(UbsTranslations.literal("Delivery complete: ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(sanitizeTokenText(matched.getString(TAG_ORDER_ITEM_NAME))
                        + " x" + Math.max(1, matched.getInt(TAG_ORDER_QTY))).withStyle(ChatFormatting.WHITE)));

        MutableComponent payoutMessage = UbsTranslations.literal("Base ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(MoneyText.abbreviateWithDollar(BigDecimal.valueOf(rewardCents, 2))).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        if (bonus.bonusCents() > 0L) {
            payoutMessage.append(UbsTranslations.literal("Bonus ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(MoneyText.abbreviateWithDollar(BigDecimal.valueOf(bonus.bonusCents(), 2))).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(UbsTranslations.literal("speed +").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(bonus.speedBonusPct() + "%, ").withStyle(ChatFormatting.WHITE))
                    .append(UbsTranslations.literal("streak +").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(bonus.streakBonusPct() + "%)").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            payoutMessage.append(UbsTranslations.literal("No bonus this run").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
        }
        payoutMessage.append(UbsTranslations.literal("Paid ").withStyle(ChatFormatting.GREEN))
                .append(Component.literal(MoneyText.abbreviateWithDollar(BigDecimal.valueOf(bonus.totalPayoutCents(), 2))).withStyle(ChatFormatting.GOLD));
        courier.sendSystemMessage(payoutMessage);

        courier.sendSystemMessage(UbsTranslations.literal("Courier rank: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(courierRankLabel(progress.completed())).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(UbsTranslations.literal("Streak: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(String.valueOf(progress.streak())).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                .append(UbsTranslations.literal("best ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(String.valueOf(progress.bestStreak())).withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(") | ").withStyle(ChatFormatting.DARK_GRAY))
                .append(UbsTranslations.literal("Success: ").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(progress.successRatePct() + "%").withStyle(ChatFormatting.WHITE)));
        if (!payoutWarning.isBlank()) {
            courier.sendSystemMessage(UbsTranslations.literal(payoutWarning.trim()).withStyle(ChatFormatting.YELLOW));
        }
        pushShopAlert(
                courier,
                "Order Delivery",
                "Delivery complete. Paid " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(bonus.totalPayoutCents(), 2))
                        + ". Rank: " + courierRankLabel(progress.completed())
                        + " | Streak: " + progress.streak()
                        + (payoutWarning.isBlank() ? "" : " | " + payoutWarning.trim()),
                DeliveryAlertPayload.AlertTone.SUCCESS,
                6200
        );

        return new ShopActionResult(true,
                "Delivery complete. Paid " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(bonus.totalPayoutCents(), 2))
                        + " (" + (bonus.bonusCents() > 0L
                        ? "base " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(rewardCents, 2))
                        + " + bonus " + MoneyText.abbreviateWithDollar(BigDecimal.valueOf(bonus.bonusCents(), 2))
                        : "no bonus")
                        + ")." + payoutWarning);
    }

    public static boolean isDeliveryPallet(CentralBank centralBank, String dimensionId, BlockPos palletMasterPos) {
        if (centralBank == null || palletMasterPos == null) {
            return false;
        }
        return findAssignedPalletShop(centralBank, dimensionId, palletMasterPos) != null;
    }

    public static ShopActionResult validateDeliveryPalletBoxPlacement(MinecraftServer server,
                                                                      ServerPlayer courier,
                                                                      ServerLevel level,
                                                                      BlockPos palletMasterPos,
                                                                      ItemStack placedBox) {
        if (server == null || courier == null || level == null || palletMasterPos == null) {
            return new ShopActionResult(false, "Delivery context is unavailable.");
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new ShopActionResult(false, "Bank data is unavailable.");
        }

        FoundAssignedPallet found = findAssignedPalletShop(centralBank, level.dimension().location().toString(), palletMasterPos);
        if (found == null) {
            // Non-delivery pallets keep normal storage behavior.
            return new ShopActionResult(true, "");
        }

        BoxDeliverySummary summary = summarizeCardboardBox(placedBox);
        if (!summary.valid()) {
            return new ShopActionResult(false, "Delivery pallet accepts only valid order boxes (single item type, exact order quantity).");
        }

        CompoundTag shop = found.shopTag();
        long now = System.currentTimeMillis();
        if (expireOrdersInPlace(centralBank, shop, now)) {
            saveShopTag(centralBank, shop);
        }

        String targetPalletRef = encodeOrderPalletRef(level.dimension().location().toString(), palletMasterPos);
        String targetPalletKey = resolvePalletAssignmentKey(level, palletMasterPos, false);
        if (targetPalletKey.isBlank()) {
            targetPalletKey = targetPalletRef;
        }
        String requiredPalletRef = "";
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
            if (!ORDER_STATUS_ACCEPTED.equals(status)) {
                continue;
            }
            UUID acceptedBy = order.contains(TAG_ORDER_ACCEPTED_BY) ? order.getUUID(TAG_ORDER_ACCEPTED_BY) : null;
            if (acceptedBy == null || !acceptedBy.equals(courier.getUUID())) {
                continue;
            }
            String itemId = order.getString(TAG_ORDER_ITEM_ID);
            int qty = Math.max(1, order.getInt(TAG_ORDER_QTY));
            if (!itemId.equalsIgnoreCase(summary.itemId()) || qty != summary.totalCount()) {
                continue;
            }
            String boundRef = normalizeOrderPalletBindingKey(order);
            if (!boundRef.isBlank()) {
                if (boundRef.equalsIgnoreCase(targetPalletKey)) {
                    return new ShopActionResult(true, "");
                }
                if (requiredPalletRef.isBlank()) {
                    requiredPalletRef = boundRef;
                }
                continue;
            }
            return new ShopActionResult(true, "");
        }

        if (!requiredPalletRef.isBlank()) {
            return new ShopActionResult(false,
                    "This delivery order is bound to pallet " + formatPalletRef(requiredPalletRef) + ".");
        }
        return new ShopActionResult(false,
                "No accepted delivery order matches this box for you. Check item, quantity, and assigned pallet.");
    }

    private static int parseSafeInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseCurrencyToCents(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String cleaned = raw.trim()
                .replace("$", "")
                .replace(",", "")
                .replace("_", "")
                .replace(" ", "");
        if (cleaned.isBlank()) {
            return 0L;
        }

        String lower = cleaned.toLowerCase(Locale.ROOT);
        BigDecimal multiplier = BigDecimal.ONE;
        String numberPart = cleaned;

        String[][] suffixes = new String[][]{
                {"qad", "1000000000000000"},
                {"qid", "1000000000000000000"},
                {"sxd", "1000000000000000000000"},
                {"spd", "1000000000000000000000000"},
                {"ocd", "1000000000000000000000000000"},
                {"nod", "1000000000000000000000000000000"},
                {"dc", "1000000000000000000000000000000000"},
                {"qa", "1000000000000000"},
                {"qi", "1000000000000000000"},
                {"sx", "1000000000000000000000"},
                {"sp", "1000000000000000000000000"},
                {"oc", "1000000000000000000000000000"},
                {"no", "1000000000000000000000000000000"},
                {"k", "1000"},
                {"m", "1000000"},
                {"b", "1000000000"},
                {"t", "1000000000000"}
        };

        for (String[] suffix : suffixes) {
            String key = suffix[0];
            if (lower.endsWith(key) && cleaned.length() > key.length()) {
                numberPart = cleaned.substring(0, cleaned.length() - key.length());
                multiplier = new BigDecimal(suffix[1]);
                break;
            }
        }

        try {
            BigDecimal value = new BigDecimal(numberPart.trim());
            BigDecimal cents = value.multiply(multiplier).movePointRight(2);
            return cents.setScale(0, RoundingMode.DOWN).max(BigDecimal.ZERO).longValueExact();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static long parseLongOrDefault(String raw, long fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(raw.trim().replace("_", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long parseContractExpiryMillis(String raw) {
        if (raw == null || raw.isBlank() || "-".equals(raw.trim())) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(raw.trim().replace("_", "")));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static String formatContractExpiry(long expiresAtMillis) {
        if (expiresAtMillis <= 0L) {
            return "no end date";
        }
        try {
            long displayMillis = Math.max(0L, expiresAtMillis - 1L);
            return Instant.ofEpochMilli(displayMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .toString();
        } catch (RuntimeException ignored) {
            return String.valueOf(expiresAtMillis);
        }
    }

    private static int parseIntOrDefault(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim().replace("_", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDoubleOrDefault(String raw, double fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace("%", ""));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long dollarsToCents(long dollars) {
        if (dollars <= 0L) {
            return 0L;
        }
        try {
            return Math.multiplyExact(dollars, 100L);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static double clampPercent(double raw) {
        if (!Double.isFinite(raw)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, raw));
    }

    private static String formatPercent(double raw) {
        return String.format(Locale.ROOT, "%.2f%%", clampPercent(raw));
    }

    private static String formatCents(long cents) {
        return MoneyText.abbreviateWithDollar(BigDecimal.valueOf(Math.max(0L, cents), 2));
    }

    public static int franchiseLicenseCapacityForLevel(int level) {
        int safeLevel = Math.max(1, level);
        int unlock = Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get());
        if (safeLevel < unlock) {
            return 0;
        }
        int base = Math.max(0, Config.SHOP_FRANCHISE_BASE_LICENSE_CAPACITY.get());
        int step = Math.max(0, Config.SHOP_FRANCHISE_LICENSE_CAPACITY_PER_10_LEVELS.get());
        int extraBuckets = Math.max(0, (safeLevel - unlock) / 10);
        int cap = base + (extraBuckets * step);
        return Math.max(0, Math.min(Math.max(0, Config.SHOP_FRANCHISE_MAX_LICENSE_CAPACITY.get()), cap));
    }

    public static int corporateBranchCapacityForLevel(int level) {
        int safeLevel = Math.max(1, level);
        int max = Math.max(1, Config.SHOP_CORPORATE_MAX_BRANCHES.get());
        int firstExtra = Math.max(1, Config.SHOP_CORPORATE_FIRST_EXTRA_BRANCH_LEVEL.get());
        if (safeLevel < firstExtra) {
            return 1;
        }
        int step = Math.max(1, Config.SHOP_CORPORATE_BRANCH_LEVEL_STEP.get());
        int extras = 1 + Math.max(0, (safeLevel - firstExtra) / step);
        return Math.max(1, Math.min(max, 1 + extras));
    }

    private static void normalizeTypeStateForExistingShop(CompoundTag shop) {
        if (shop == null) {
            return;
        }
        if (!shop.contains(TAG_TYPE_FREE_RECLASS_AVAILABLE)) {
            shop.putBoolean(TAG_TYPE_FREE_RECLASS_AVAILABLE, true);
        }
        if (!shop.contains(TAG_TYPE_LAST_CONVERSION_MILLIS)) {
            shop.putLong(TAG_TYPE_LAST_CONVERSION_MILLIS, 0L);
        }
        if (!shop.contains(TAG_TYPE_PAYABLE_CENTS)) {
            shop.putLong(TAG_TYPE_PAYABLE_CENTS, 0L);
        }
        if (!shop.contains(TAG_TYPE_FEES_PAID_CENTS)) {
            shop.putLong(TAG_TYPE_FEES_PAID_CENTS, 0L);
        }
        if (!shop.contains(TAG_TYPE_FEES_ACCRUED_CENTS)) {
            shop.putLong(TAG_TYPE_FEES_ACCRUED_CENTS, 0L);
        }
        if (SHOP_TYPE_CORPORATE_CHAIN.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
            ensureCorporateHqState(shop);
        }
    }

    private static boolean isFreeTypeReclassAvailable(CompoundTag shop) {
        if (shop == null) {
            return false;
        }
        normalizeTypeStateForExistingShop(shop);
        return shop.getBoolean(TAG_TYPE_FREE_RECLASS_AVAILABLE);
    }

    private static void clearTypeSpecificState(CompoundTag shop, String oldType, String newType) {
        if (shop == null) {
            return;
        }
        if (!Objects.equals(oldType, newType)) {
            if (!SHOP_TYPE_FRANCHISE.equals(newType)) {
                shop.remove(TAG_FRANCHISE_BRAND);
                shop.remove(TAG_FRANCHISE_OFFERS);
                shop.remove(TAG_FRANCHISE_CONTRACTS);
                shop.remove(TAG_FRANCHISE_CONTRACT);
            }
            if (!SHOP_TYPE_CORPORATE_CHAIN.equals(newType)) {
                shop.remove(TAG_CORPORATE_HQ);
                shop.remove(TAG_CORPORATE_BRANCHES);
            }
        }
    }

    private static void ensureCorporateHqState(CompoundTag shop) {
        if (shop == null || !shop.contains(TAG_ID)) {
            return;
        }
        ListTag branches = shop.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND);
        UUID shopId = shop.getUUID(TAG_ID);
        if (!containsShopRef(branches, shopId)) {
            CompoundTag self = new CompoundTag();
            self.putUUID(TAG_REF_SHOP_ID, shopId);
            if (shop.contains(TAG_OWNER)) {
                self.putUUID(TAG_REF_OWNER_ID, shop.getUUID(TAG_OWNER));
            }
            self.putString(TAG_REF_NAME, shop.getString(TAG_NAME));
            self.putBoolean(TAG_REF_ACTIVE, true);
            self.putLong(TAG_REF_CREATED_MILLIS, Math.max(0L, shop.getLong(TAG_CREATED_MILLIS)));
            branches.add(0, self);
        }
        shop.put(TAG_CORPORATE_BRANCHES, branches);
        shop.putUUID(TAG_CORPORATE_HQ, shopId);
    }

    private static boolean containsShopRef(ListTag refs, UUID shopId) {
        if (refs == null || shopId == null) {
            return false;
        }
        for (Tag tag : refs) {
            if (!(tag instanceof CompoundTag ref) || !ref.contains(TAG_REF_SHOP_ID)) {
                continue;
            }
            if (shopId.equals(ref.getUUID(TAG_REF_SHOP_ID)) && ref.getBoolean(TAG_REF_ACTIVE)) {
                return true;
            }
        }
        return false;
    }

    private static int countCorporateBranches(CompoundTag hq) {
        if (hq == null) {
            return 0;
        }
        ensureCorporateHqState(hq);
        int count = 0;
        for (Tag tag : hq.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND)) {
            if (tag instanceof CompoundTag ref && ref.getBoolean(TAG_REF_ACTIVE)) {
                count++;
            }
        }
        return count;
    }

    private static int countActiveFranchiseContracts(CompoundTag franchisorShop) {
        if (franchisorShop == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int count = 0;
        for (Tag tag : franchisorShop.getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND)) {
            if (tag instanceof CompoundTag contract
                    && contract.getBoolean(TAG_REF_ACTIVE)
                    && !isFranchiseContractExpired(contract, now)) {
                count++;
            }
        }
        return count;
    }

    private static void expireFranchiseAgreements(CentralBank centralBank) {
        if (centralBank == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !SHOP_TYPE_FRANCHISE.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
                continue;
            }
            boolean changed = false;
            changed |= removeExpiredAcceptedFranchiseContract(shop, now);
            changed |= deactivateExpiredFranchiseContracts(shop, now);
            changed |= deactivateExpiredFranchiseOffers(shop, now);
            if (changed) {
                saveShopTag(centralBank, shop);
            }
        }
    }

    private static boolean removeExpiredAcceptedFranchiseContract(CompoundTag shop, long now) {
        if (shop == null || !shop.contains(TAG_FRANCHISE_CONTRACT, Tag.TAG_COMPOUND)) {
            return false;
        }
        CompoundTag contract = shop.getCompound(TAG_FRANCHISE_CONTRACT);
        if (!contract.getBoolean(TAG_REF_ACTIVE) || !isFranchiseContractExpired(contract, now)) {
            return false;
        }
        shop.remove(TAG_FRANCHISE_CONTRACT);
        return true;
    }

    private static boolean deactivateExpiredFranchiseContracts(CompoundTag shop, long now) {
        if (shop == null) {
            return false;
        }
        boolean changed = false;
        ListTag contracts = shop.getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND);
        for (Tag tag : contracts) {
            if (!(tag instanceof CompoundTag contract)
                    || !contract.getBoolean(TAG_REF_ACTIVE)
                    || !isFranchiseContractExpired(contract, now)) {
                continue;
            }
            contract.putBoolean(TAG_REF_ACTIVE, false);
            contract.putLong(TAG_CONTRACT_EXPIRED_AT_MILLIS, now);
            changed = true;
        }
        if (changed) {
            shop.put(TAG_FRANCHISE_CONTRACTS, contracts);
        }
        return changed;
    }

    private static boolean deactivateExpiredFranchiseOffers(CompoundTag shop, long now) {
        if (shop == null) {
            return false;
        }
        boolean changed = false;
        ListTag offers = shop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND);
        for (Tag tag : offers) {
            if (!(tag instanceof CompoundTag offer)
                    || !offer.getBoolean(TAG_REF_ACTIVE)
                    || !isFranchiseOfferExpired(offer, now)) {
                continue;
            }
            offer.putBoolean(TAG_REF_ACTIVE, false);
            changed = true;
        }
        if (changed) {
            shop.put(TAG_FRANCHISE_OFFERS, offers);
        }
        return changed;
    }

    private static boolean isFranchiseOfferExpired(CompoundTag offer, long now) {
        long expiresAtMillis = offerContractExpiryMillis(offer);
        return expiresAtMillis > 0L && now >= expiresAtMillis;
    }

    private static boolean isFranchiseContractExpired(CompoundTag contract, long now) {
        long expiresAtMillis = contractExpiryMillis(contract);
        return expiresAtMillis > 0L && now >= expiresAtMillis;
    }

    private static long offerContractExpiryMillis(CompoundTag offer) {
        if (offer == null) {
            return 0L;
        }
        long expiresAtMillis = Math.max(0L, offer.getLong(TAG_OFFER_CONTRACT_EXPIRES_AT_MILLIS));
        if (expiresAtMillis <= 0L) {
            expiresAtMillis = Math.max(0L, offer.getLong(TAG_CONTRACT_EXPIRES_AT_MILLIS));
        }
        return expiresAtMillis;
    }

    private static long contractExpiryMillis(CompoundTag contract) {
        if (contract == null) {
            return 0L;
        }
        return Math.max(0L, contract.getLong(TAG_CONTRACT_EXPIRES_AT_MILLIS));
    }

    private static FranchiseOfferRef findActiveFranchiseOffer(CentralBank centralBank, UUID offerId) {
        if (centralBank == null || offerId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !SHOP_TYPE_FRANCHISE.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
                continue;
            }
            for (Tag tag : shop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND)) {
                if (!(tag instanceof CompoundTag offer) || !offer.contains(TAG_OFFER_ID)) {
                    continue;
                }
                if (offerId.equals(offer.getUUID(TAG_OFFER_ID))
                        && offer.getBoolean(TAG_REF_ACTIVE)
                        && !isFranchiseOfferExpired(offer, now)) {
                    return new FranchiseOfferRef(shop, offer);
                }
            }
        }
        return null;
    }

    private static CompoundTag buildFranchiseContractTag(UUID contractId,
                                                         CompoundTag franchisorShop,
                                                         CompoundTag franchiseeShop,
                                                         CompoundTag offer,
                                                         boolean npc) {
        CompoundTag contract = new CompoundTag();
        contract.putUUID(TAG_CONTRACT_ID, contractId == null ? UUID.randomUUID() : contractId);
        contract.putUUID(TAG_CONTRACT_FRANCHISOR_SHOP_ID, franchisorShop.getUUID(TAG_ID));
        contract.putUUID(TAG_CONTRACT_FRANCHISEE_SHOP_ID, franchiseeShop.getUUID(TAG_ID));
        contract.putUUID(TAG_CONTRACT_FRANCHISOR_OWNER_ID, franchisorShop.getUUID(TAG_OWNER));
        contract.putUUID(TAG_CONTRACT_FRANCHISEE_OWNER_ID, franchiseeShop.getUUID(TAG_OWNER));
        contract.putString(TAG_CONTRACT_BRAND_NAME, offer.getString(TAG_OFFER_BRAND_NAME));
        contract.putDouble(TAG_CONTRACT_ROYALTY_PERCENT, clampPercent(offer.getDouble(TAG_OFFER_ROYALTY_PERCENT)));
        contract.putDouble(TAG_CONTRACT_MARKETING_PERCENT, clampPercent(offer.getDouble(TAG_OFFER_MARKETING_PERCENT)));
        contract.putString(TAG_CONTRACT_RULES, sanitizeTokenText(offer.getString(TAG_OFFER_RULES)));
        if (offer.contains(TAG_FRANCHISE_REQUIRED_ITEMS, Tag.TAG_LIST)) {
            contract.put(TAG_FRANCHISE_REQUIRED_ITEMS, offer.getList(TAG_FRANCHISE_REQUIRED_ITEMS, Tag.TAG_COMPOUND).copy());
        }
        long expiresAtMillis = offerContractExpiryMillis(offer);
        if (expiresAtMillis > 0L) {
            contract.putLong(TAG_CONTRACT_EXPIRES_AT_MILLIS, expiresAtMillis);
        }
        contract.putBoolean(TAG_CONTRACT_NPC, npc);
        contract.putBoolean(TAG_REF_ACTIVE, true);
        contract.putLong(TAG_REF_CREATED_MILLIS, System.currentTimeMillis());
        return contract;
    }

    private static ShopActionResult debitShopSettlement(CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        long cents,
                                                        String reason) {
        if (cents <= 0L) {
            return new ShopActionResult(true, "No fee due.");
        }
        UUID settlementId = resolveSettlementAccountId(centralBank, ownerId, shopId, null);
        if (settlementId == null) {
            return new ShopActionResult(false, "No settlement account is configured.");
        }
        AccountHolder account = centralBank.SearchForAccountByAccountId(settlementId);
        if (account == null) {
            return new ShopActionResult(false, "Settlement account is unavailable.");
        }
        BigDecimal amount = BigDecimal.valueOf(cents, 2);
        if (!account.RemoveBalance(amount)) {
            return new ShopActionResult(false, "Settlement account has insufficient available funds.");
        }
        account.addTransaction(new UserTransaction(
                account.getAccountUUID(),
                account.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                sanitizeTokenText(reason)
        ));
        return new ShopActionResult(true, "Paid " + formatCents(cents) + ".");
    }

    private static ShopActionResult transferAccountCents(CentralBank centralBank,
                                                         UUID sourceAccountId,
                                                         UUID targetAccountId,
                                                         long cents,
                                                         String reason) {
        if (cents <= 0L) {
            return new ShopActionResult(true, "No transfer required.");
        }
        if (sourceAccountId == null || targetAccountId == null) {
            return new ShopActionResult(false, "Source and target accounts are required.");
        }
        AccountHolder source = centralBank.SearchForAccountByAccountId(sourceAccountId);
        AccountHolder target = centralBank.SearchForAccountByAccountId(targetAccountId);
        if (source == null || target == null) {
            return new ShopActionResult(false, "Source or target account is unavailable.");
        }
        BigDecimal amount = BigDecimal.valueOf(cents, 2);
        if (sourceAccountId.equals(targetAccountId)) {
            source.addTransaction(new UserTransaction(sourceAccountId, targetAccountId, amount, LocalDateTime.now(), sanitizeTokenText(reason)));
            return new ShopActionResult(true, "Recorded self-transfer " + formatCents(cents) + ".");
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new ShopActionResult(false, "Server context is unavailable.");
        }
        UserTransaction tx = new UserTransaction(sourceAccountId, targetAccountId, amount, LocalDateTime.now(), sanitizeTokenText(reason));
        if (!tx.makeTransaction(server)) {
            return new ShopActionResult(false, "Transfer failed.");
        }
        return new ShopActionResult(true, "Transferred " + formatCents(cents) + ".");
    }

    private static void accrueTypeFee(CompoundTag shop, long cents) {
        if (shop == null || cents <= 0L) {
            return;
        }
        shop.putLong(TAG_TYPE_PAYABLE_CENTS, safeAdd(Math.max(0L, shop.getLong(TAG_TYPE_PAYABLE_CENTS)), cents));
        shop.putLong(TAG_TYPE_FEES_ACCRUED_CENTS, safeAdd(Math.max(0L, shop.getLong(TAG_TYPE_FEES_ACCRUED_CENTS)), cents));
    }

    private static long percentOfDollarsToCents(long dollars, double percent) {
        if (dollars <= 0L || percent <= 0.0D) {
            return 0L;
        }
        BigDecimal cents = BigDecimal.valueOf(dollars)
                .multiply(BigDecimal.valueOf(100L))
                .multiply(BigDecimal.valueOf(clampPercent(percent)))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.HALF_UP);
        return cents.max(BigDecimal.ZERO).longValue();
    }

    private static void applyShopTypeSaleFees(CentralBank centralBank, CompoundTag shop, long grossDollars) {
        if (centralBank == null || shop == null || grossDollars <= 0L || !shop.contains(TAG_ID) || !shop.contains(TAG_OWNER)) {
            return;
        }
        expireFranchiseAgreements(centralBank);
        removeExpiredAcceptedFranchiseContract(shop, System.currentTimeMillis());
        normalizeTypeStateForExistingShop(shop);
        String type = normalizeShopType(shop.getString(TAG_TYPE));
        long feeCents = 0L;
        long nonCompliancePenaltyCents = 0L;
        UUID targetAccountId = null;
        String reason = "";
        if (SHOP_TYPE_FRANCHISE.equals(type) && shop.contains(TAG_FRANCHISE_CONTRACT, Tag.TAG_COMPOUND)) {
            CompoundTag contract = shop.getCompound(TAG_FRANCHISE_CONTRACT);
            if (contract.getBoolean(TAG_REF_ACTIVE)) {
                double feePercent = clampPercent(contract.getDouble(TAG_CONTRACT_ROYALTY_PERCENT))
                        + clampPercent(contract.getDouble(TAG_CONTRACT_MARKETING_PERCENT));
                feeCents = percentOfDollarsToCents(grossDollars, feePercent);
                UUID franchisorOwner = contract.contains(TAG_CONTRACT_FRANCHISOR_OWNER_ID)
                        ? contract.getUUID(TAG_CONTRACT_FRANCHISOR_OWNER_ID)
                        : null;
                UUID franchisorShopId = contract.contains(TAG_CONTRACT_FRANCHISOR_SHOP_ID)
                        ? contract.getUUID(TAG_CONTRACT_FRANCHISOR_SHOP_ID)
                        : null;
                targetAccountId = resolveSettlementAccountId(centralBank, franchisorOwner, franchisorShopId, null);
                reason = "SHOP_FRANCHISE_ROYALTY:" + sanitizeTokenText(contract.getString(TAG_CONTRACT_BRAND_NAME));
                FranchiseRequirementStatus status = evaluateFranchiseRequirements(ServerLifecycleHooks.getCurrentServer(), shop, contract);
                if (!status.compliant()) {
                    nonCompliancePenaltyCents = percentOfDollarsToCents(
                            grossDollars,
                            Config.SHOP_FRANCHISE_NONCOMPLIANCE_PENALTY_PERCENT.get()
                    );
                }
            }
        } else if (SHOP_TYPE_CORPORATE_CHAIN.equals(type)) {
            feeCents = percentOfDollarsToCents(grossDollars, Config.SHOP_CORPORATE_OVERHEAD_PERCENT.get());
            reason = "SHOP_CORPORATE_OVERHEAD:" + sanitizeTokenText(shop.getString(TAG_NAME));
        }
        if (nonCompliancePenaltyCents > 0L) {
            accrueTypeFee(shop, nonCompliancePenaltyCents);
        }
        if (feeCents <= 0L) {
            return;
        }
        UUID ownerId = shop.getUUID(TAG_OWNER);
        UUID shopId = shop.getUUID(TAG_ID);
        UUID sourceAccountId = resolveSettlementAccountId(centralBank, ownerId, shopId, null);
        ShopActionResult paid;
        if (targetAccountId != null) {
            paid = transferAccountCents(centralBank, sourceAccountId, targetAccountId, feeCents, reason);
        } else {
            paid = debitShopSettlement(centralBank, ownerId, shopId, feeCents, reason);
        }
        if (paid.success()) {
            shop.putLong(TAG_TYPE_FEES_PAID_CENTS, safeAdd(Math.max(0L, shop.getLong(TAG_TYPE_FEES_PAID_CENTS)), feeCents));
        } else {
            accrueTypeFee(shop, feeCents);
        }
    }

    private static void appendFranchiseOfferReportLines(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        CompoundTag currentShop,
                                                        List<String> lines) {
        int index = 0;
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !SHOP_TYPE_FRANCHISE.equals(normalizeShopType(shop.getString(TAG_TYPE)))) {
                continue;
            }
            UUID franchisorOwner = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
            for (Tag tag : shop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND)) {
                if (!(tag instanceof CompoundTag offer)
                        || !offer.getBoolean(TAG_REF_ACTIVE)
                        || !offer.contains(TAG_OFFER_ID)
                        || isFranchiseOfferExpired(offer, System.currentTimeMillis())) {
                    continue;
                }
                String visibility = "PUBLIC";
                if (offer.contains(TAG_OFFER_DIRECT_PLAYER)) {
                    UUID direct = offer.getUUID(TAG_OFFER_DIRECT_PLAYER);
                    if (!Objects.equals(direct, ownerId) && !Objects.equals(franchisorOwner, ownerId)) {
                        continue;
                    }
                    visibility = "DIRECT";
                }
                index++;
                lines.add("@franchise.offer=" + index
                        + "|" + offer.getUUID(TAG_OFFER_ID)
                        + "|" + sanitizeTokenText(offer.getString(TAG_OFFER_BRAND_NAME))
                        + "|" + Math.max(0L, offer.getLong(TAG_OFFER_UPFRONT_CENTS))
                        + "|" + clampPercent(offer.getDouble(TAG_OFFER_ROYALTY_PERCENT))
                        + "|" + clampPercent(offer.getDouble(TAG_OFFER_MARKETING_PERCENT))
                        + "|" + sanitizeTokenText(shop.getString(TAG_NAME))
                        + "|" + visibility
                        + "|" + sanitizeTokenText(offer.getString(TAG_OFFER_RULES))
                        + "|" + offerContractExpiryMillis(offer));
                FranchiseRequirementStatus status = evaluateFranchiseRequirements(server, currentShop, offer);
                lines.add("@franchise.offer_compliance=" + offer.getUUID(TAG_OFFER_ID)
                        + "|" + (status.compliant() ? "1" : "0")
                        + "|" + status.missingCount()
                        + "|" + Config.SHOP_FRANCHISE_NONCOMPLIANCE_PENALTY_PERCENT.get()
                        + "|" + sanitizeTokenText(status.missingSummary()));
                appendFranchiseRequirementReportLines(lines, "franchise.offer_req", offer.getUUID(TAG_OFFER_ID), status);
            }
        }
        if (currentShop != null) {
            for (Tag tag : currentShop.getList(TAG_FRANCHISE_OFFERS, Tag.TAG_COMPOUND)) {
                if (tag instanceof CompoundTag offer && !offer.getBoolean(TAG_REF_ACTIVE) && offer.contains(TAG_OFFER_ID)) {
                    lines.add("@franchise.inactive_offer=" + offer.getUUID(TAG_OFFER_ID)
                            + "|" + sanitizeTokenText(offer.getString(TAG_OFFER_BRAND_NAME)));
                }
            }
        }
    }

    private static void appendFranchiseContractReportLines(MinecraftServer server, CentralBank centralBank, CompoundTag shop, List<String> lines) {
        int index = 0;
        for (Tag tag : shop.getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag contract)
                    || !contract.getBoolean(TAG_REF_ACTIVE)
                    || isFranchiseContractExpired(contract, System.currentTimeMillis())) {
                continue;
            }
            index++;
            String franchiseeName = "Franchisee";
            UUID franchiseeShopId = contract.contains(TAG_CONTRACT_FRANCHISEE_SHOP_ID)
                    ? contract.getUUID(TAG_CONTRACT_FRANCHISEE_SHOP_ID)
                    : null;
            CompoundTag franchiseeShop = franchiseeShopId == null ? null : resolveShopById(centralBank, franchiseeShopId);
            if (franchiseeShop != null) {
                franchiseeName = franchiseeShop.getString(TAG_NAME);
            } else if (contract.getBoolean(TAG_CONTRACT_NPC)) {
                franchiseeName = "NPC Franchisee";
            }
            lines.add("@franchise.contract=" + index
                    + "|" + contract.getUUID(TAG_CONTRACT_ID)
                    + "|" + sanitizeTokenText(contract.getString(TAG_CONTRACT_BRAND_NAME))
                    + "|" + sanitizeTokenText(franchiseeName)
                    + "|" + clampPercent(contract.getDouble(TAG_CONTRACT_ROYALTY_PERCENT))
                    + "|" + clampPercent(contract.getDouble(TAG_CONTRACT_MARKETING_PERCENT))
                    + "|" + (contract.getBoolean(TAG_CONTRACT_NPC) ? "NPC" : "PLAYER")
                    + "|" + sanitizeTokenText(contract.getString(TAG_CONTRACT_RULES))
                    + "|" + contractExpiryMillis(contract));
            FranchiseRequirementStatus status = evaluateFranchiseRequirements(server, franchiseeShop, contract);
            lines.add("@franchise.contract_compliance=" + contract.getUUID(TAG_CONTRACT_ID)
                    + "|" + (status.compliant() ? "1" : "0")
                    + "|" + status.missingCount()
                    + "|" + Config.SHOP_FRANCHISE_NONCOMPLIANCE_PENALTY_PERCENT.get()
                    + "|" + sanitizeTokenText(status.missingSummary()));
            appendFranchiseRequirementReportLines(lines, "franchise.contract_req", contract.getUUID(TAG_CONTRACT_ID), status);
        }
    }

    private static ListTag parseFranchiseRequiredItemsPayload(String rawRequiredItems) {
        ListTag out = new ListTag();
        if (rawRequiredItems == null || rawRequiredItems.isBlank() || "-".equals(rawRequiredItems.trim())) {
            return out;
        }
        String[] rows = rawRequiredItems.split(";", -1);
        for (String rawRow : rows) {
            if (rawRow == null || rawRow.isBlank()) {
                continue;
            }
            String[] parts = rawRow.split(",", -1);
            if (parts.length < 3) {
                continue;
            }
            String itemId = parts[0] == null ? "" : parts[0].trim();
            net.minecraft.resources.ResourceLocation itemKey = net.minecraft.resources.ResourceLocation.tryParse(itemId);
            if (itemKey == null) {
                continue;
            }
            ItemStack itemStack = new ItemStack(BuiltInRegistries.ITEM.get(itemKey));
            if (itemStack.isEmpty() || !itemKey.equals(BuiltInRegistries.ITEM.getKey(itemStack.getItem()))) {
                continue;
            }
            int quantity = Math.max(1, parseIntOrDefault(parts[1], 1));
            boolean exact = "1".equals(parts[2]) || "true".equalsIgnoreCase(parts[2]);
            String note = parts.length > 3 ? decodeRequirementField(parts[3]) : "";
            CompoundTag stackTag = parts.length > 4 ? decodeRequirementStack(parts[4]) : new CompoundTag();
            if (exact && stackTag.isEmpty()) {
                exact = false;
            }
            CompoundTag requirement = new CompoundTag();
            requirement.putString(TAG_REQ_ITEM_ID, itemId);
            requirement.putInt(TAG_REQ_QUANTITY, quantity);
            requirement.putBoolean(TAG_REQ_EXACT, exact);
            requirement.putString(TAG_REQ_NOTE, sanitizeTokenText(note));
            if (!stackTag.isEmpty()) {
                requirement.put(TAG_REQ_STACK, stackTag);
            }
            out.add(requirement);
        }
        return out;
    }

    private static String decodeRequirementField(String encoded) {
        if (encoded == null || encoded.isBlank() || "-".equals(encoded)) {
            return "";
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return new String(bytes, StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private static CompoundTag decodeRequirementStack(String encoded) {
        String snbt = decodeRequirementField(encoded);
        if (snbt.isBlank()) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(snbt);
        } catch (Exception ignored) {
            return new CompoundTag();
        }
    }

    private static void appendFranchiseRequirementReportLines(List<String> lines,
                                                              String key,
                                                              UUID parentId,
                                                              FranchiseRequirementStatus status) {
        if (lines == null || key == null || key.isBlank() || parentId == null || status == null) {
            return;
        }
        for (FranchiseRequirementProgress progress : status.items()) {
            FranchiseRequiredItem requirement = progress.requirement();
            lines.add("@" + key + "=" + parentId
                    + "|" + sanitizeTokenText(requirement.itemId())
                    + "|" + Math.max(1, requirement.quantity())
                    + "|" + (requirement.exact() ? "1" : "0")
                    + "|" + Math.max(0, progress.current())
                    + "|" + (progress.satisfied() ? "1" : "0")
                    + "|" + sanitizeTokenText(progress.displayName())
                    + "|" + sanitizeTokenText(requirement.note()));
        }
    }

    private static FranchiseRequirementStatus evaluateFranchiseRequirements(MinecraftServer server,
                                                                            CompoundTag shop,
                                                                            CompoundTag holder) {
        List<FranchiseRequiredItem> requirements = readFranchiseRequirements(holder);
        if (requirements.isEmpty()) {
            return new FranchiseRequirementStatus(true, 0, "-", List.of());
        }
        List<ShelfRef> shelves = server == null || shop == null ? List.of() : collectShelvesForShop(server, shop);
        List<FranchiseRequirementProgress> progress = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (FranchiseRequiredItem requirement : requirements) {
            int available = countFranchiseRequirementStock(server, shelves, requirement);
            boolean satisfied = available >= Math.max(1, requirement.quantity());
            String displayName = resolveFranchiseRequirementDisplayName(server, requirement);
            progress.add(new FranchiseRequirementProgress(requirement, available, satisfied, displayName));
            if (!satisfied) {
                missing.add(Math.max(1, requirement.quantity()) + "x " + displayName + " (" + Math.max(0, available) + " ready)");
            }
        }
        boolean compliant = missing.isEmpty();
        return new FranchiseRequirementStatus(
                compliant,
                missing.size(),
                compliant ? "-" : String.join(", ", missing),
                List.copyOf(progress)
        );
    }

    private static List<FranchiseRequiredItem> readFranchiseRequirements(CompoundTag holder) {
        if (holder == null || !holder.contains(TAG_FRANCHISE_REQUIRED_ITEMS, Tag.TAG_LIST)) {
            return List.of();
        }
        List<FranchiseRequiredItem> out = new ArrayList<>();
        for (Tag tag : holder.getList(TAG_FRANCHISE_REQUIRED_ITEMS, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag requirement)) {
                continue;
            }
            String itemId = requirement.getString(TAG_REQ_ITEM_ID);
            if (itemId == null || itemId.isBlank()) {
                continue;
            }
            int quantity = Math.max(1, requirement.getInt(TAG_REQ_QUANTITY));
            boolean exact = requirement.getBoolean(TAG_REQ_EXACT);
            String note = requirement.getString(TAG_REQ_NOTE);
            CompoundTag stackTag = requirement.contains(TAG_REQ_STACK, Tag.TAG_COMPOUND)
                    ? requirement.getCompound(TAG_REQ_STACK).copy()
                    : new CompoundTag();
            out.add(new FranchiseRequiredItem(itemId, quantity, exact, note, stackTag));
        }
        return List.copyOf(out);
    }

    private static int countFranchiseRequirementStock(MinecraftServer server,
                                                      List<ShelfRef> shelves,
                                                      FranchiseRequiredItem requirement) {
        if (shelves == null || shelves.isEmpty() || requirement == null) {
            return 0;
        }
        int needed = Math.max(1, requirement.quantity());
        int available = 0;
        for (ShelfRef ref : shelves) {
            if (ref == null || ref.shelf() == null || !ref.shelf().isShopMode()) {
                continue;
            }
            ShelfDisplayBlockEntity shelf = ref.shelf();
            for (int slot = 0; slot < Math.max(0, shelf.getSlotCount()); slot++) {
                ItemStack display = shelf.getDisplayItem(slot);
                if (display == null || display.isEmpty() || shelf.getSlotPrice(slot) < 0L) {
                    continue;
                }
                if (!matchesFranchiseRequirement(server, display, requirement)) {
                    continue;
                }
                if (shelf.isCreativeShelf()) {
                    return needed;
                }
                available = safeAddInt(available, Math.max(0, shelf.getSlotStock(slot)));
                if (available >= needed) {
                    return available;
                }
            }
        }
        return available;
    }

    private static boolean matchesFranchiseRequirement(MinecraftServer server,
                                                       ItemStack display,
                                                       FranchiseRequiredItem requirement) {
        if (display == null || display.isEmpty() || requirement == null) {
            return false;
        }
        if (requirement.exact()) {
            ItemStack expected = ItemStackDataCompat.parseStack(
                    requirement.stackTag(),
                    server == null ? ItemStackDataCompat.DEFAULT_REGISTRIES : server.registryAccess()
            );
            return !expected.isEmpty() && ItemStackDataCompat.sameItemSameComponents(display, expected);
        }
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(requirement.itemId());
        return key != null && key.equals(BuiltInRegistries.ITEM.getKey(display.getItem()));
    }

    private static String resolveFranchiseRequirementDisplayName(MinecraftServer server, FranchiseRequiredItem requirement) {
        if (requirement == null) {
            return "Item";
        }
        if (requirement.exact() && !requirement.stackTag().isEmpty()) {
            ItemStack parsed = ItemStackDataCompat.parseStack(
                    requirement.stackTag(),
                    server == null ? ItemStackDataCompat.DEFAULT_REGISTRIES : server.registryAccess()
            );
            if (!parsed.isEmpty()) {
                return parsed.getHoverName().getString();
            }
        }
        net.minecraft.resources.ResourceLocation key = net.minecraft.resources.ResourceLocation.tryParse(requirement.itemId());
        if (key != null) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(key));
            if (!stack.isEmpty()) {
                return stack.getHoverName().getString();
            }
        }
        return requirement.itemId();
    }

    private static int safeAddInt(int left, int right) {
        long sum = (long) Math.max(0, left) + Math.max(0, right);
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static void processNpcFranchiseRoyalties(CentralBank centralBank, CompoundTag franchisorShop) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || !server.isSingleplayer() || centralBank == null || franchisorShop == null || !franchisorShop.contains(TAG_OWNER)) {
            return;
        }
        UUID ownerId = franchisorShop.getUUID(TAG_OWNER);
        UUID shopId = franchisorShop.getUUID(TAG_ID);
        UUID settlementId = resolveSettlementAccountId(centralBank, ownerId, shopId, null);
        if (settlementId == null) {
            return;
        }
        AccountHolder settlement = centralBank.SearchForAccountByAccountId(settlementId);
        if (settlement == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long intervalMillis = FRANCHISE_NPC_ROYALTY_INTERVAL_HOURS * 60L * 60L * 1000L;
        boolean changed = false;
        for (Tag tag : franchisorShop.getList(TAG_FRANCHISE_CONTRACTS, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag contract)
                    || !contract.getBoolean(TAG_REF_ACTIVE)
                    || !contract.getBoolean(TAG_CONTRACT_NPC)
                    || isFranchiseContractExpired(contract, now)) {
                continue;
            }
            long last = Math.max(0L, contract.getLong(TAG_CONTRACT_LAST_NPC_ROYALTY_MILLIS));
            if (last > 0L && now - last < intervalMillis) {
                continue;
            }
            int level = Math.max(1, franchisorShop.getInt(TAG_LEVEL));
            long royaltyCents = FRANCHISE_NPC_ROYALTY_BASE_CENTS + (long) Math.max(0, level - 1) * 125L * 100L;
            BigDecimal amount = BigDecimal.valueOf(royaltyCents, 2);
            if (settlement.forceAddBalance(amount)) {
                settlement.addTransaction(new UserTransaction(
                        settlementId,
                        settlementId,
                        amount,
                        LocalDateTime.now(),
                        "SHOP_FRANCHISE_NPC_ROYALTY:" + sanitizeTokenText(contract.getString(TAG_CONTRACT_BRAND_NAME))
                ));
                contract.putLong(TAG_CONTRACT_LAST_NPC_ROYALTY_MILLIS, now);
                changed = true;
            }
        }
        if (changed) {
            saveShopTag(centralBank, franchisorShop);
        }
    }

    private static void appendCorporateBranchReportLines(CentralBank centralBank, CompoundTag hq, List<String> lines) {
        ensureCorporateHqState(hq);
        int index = 0;
        for (Tag tag : hq.getList(TAG_CORPORATE_BRANCHES, Tag.TAG_COMPOUND)) {
            if (!(tag instanceof CompoundTag ref) || !ref.getBoolean(TAG_REF_ACTIVE) || !ref.contains(TAG_REF_SHOP_ID)) {
                continue;
            }
            index++;
            UUID branchId = ref.getUUID(TAG_REF_SHOP_ID);
            CompoundTag branch = resolveShopById(centralBank, branchId);
            String name = branch == null ? ref.getString(TAG_REF_NAME) : branch.getString(TAG_NAME);
            int level = branch == null ? 0 : Math.max(1, branch.getInt(TAG_LEVEL));
            long revenue = branch == null ? 0L : Math.max(0L, branch.getLong(TAG_REVENUE_DOLLARS));
            lines.add("@corporate.branch=" + index
                    + "|" + branchId
                    + "|" + sanitizeTokenText(name)
                    + "|" + level
                    + "|" + revenue);
        }
    }

    private static String validateOrderItemAllowed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "Select an item first.";
        }
        if (stack.is(ModItems.BANK_NOTE.get())
                || stack.is(ModItems.CHEQUE.get())
                || stack.is(ModItems.CREDIT_CARD.get())
                || stack.is(ModItems.HANDHELD_PAYMENT_TERMINAL.get())
                || stack.is(ModItems.BANK_TELLER_SPAWN_EGG.get())
                || stack.is(ModItems.CASHIER_SPAWN_EGG.get())) {
            return "Financial/security items cannot be used for delivery orders.";
        }
        for (var bill : ModItems.USD_BILLS) {
            if (bill != null && stack.is(bill.get())) {
                return "Legal tender cannot be used for delivery orders.";
            }
        }
        for (var coin : ModItems.USD_COINS) {
            if (coin != null && stack.is(coin.get())) {
                return "Legal tender cannot be used for delivery orders.";
            }
        }
        return null;
    }

    private static List<CompoundTag> getAllShops(CentralBank centralBank) {
        List<CompoundTag> out = new ArrayList<>();
        if (centralBank == null) {
            return out;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (Tag tag : shops) {
            if (tag instanceof CompoundTag shop && shop.contains(TAG_ID)) {
                out.add(shop.copy());
            }
        }
        return out;
    }

    private static List<OrderView> collectOrderViews(CompoundTag shop) {
        List<OrderView> out = new ArrayList<>();
        if (shop == null) {
            return out;
        }
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order) || !order.contains(TAG_ORDER_ID)) {
                continue;
            }
            UUID orderId;
            try {
                orderId = order.getUUID(TAG_ORDER_ID);
            } catch (Exception ignored) {
                continue;
            }
            UUID acceptedBy = order.contains(TAG_ORDER_ACCEPTED_BY) ? order.getUUID(TAG_ORDER_ACCEPTED_BY) : null;
            if (acceptedBy == null && order.contains(TAG_ORDER_COMPLETED_BY)) {
                acceptedBy = order.getUUID(TAG_ORDER_COMPLETED_BY);
            }
            String acceptedByName = sanitizeTokenText(order.getString(TAG_ORDER_ACCEPTED_BY_NAME));
            if (acceptedByName.isBlank() || "-".equals(acceptedByName)) {
                acceptedByName = acceptedBy == null ? "-" : shortUuid(acceptedBy);
            }
            out.add(new OrderView(
                    orderId,
                    sanitizeTokenText(order.getString(TAG_ORDER_ITEM_ID)),
                    sanitizeTokenText(order.getString(TAG_ORDER_ITEM_NAME)),
                    Math.max(1, order.getInt(TAG_ORDER_QTY)),
                    Math.max(0L, order.getLong(TAG_ORDER_REWARD_CENTS)),
                    normalizeOrderStatus(order.getString(TAG_ORDER_STATUS)),
                    acceptedBy,
                    acceptedByName,
                    Math.max(0L, order.getLong(TAG_ORDER_EXPIRES_AT)),
                    Math.max(ORDER_TIMEOUT_MINUTES_MIN, order.getInt(TAG_ORDER_TIMEOUT_MINUTES)),
                    Math.max(0L, order.getLong(TAG_ORDER_CREATED_AT)),
                    normalizeOrderPalletBindingKey(order),
                    Math.max(0L, order.getLong(TAG_ORDER_COMPLETED_AT)),
                    Math.max(0L, order.getLong(TAG_ORDER_ROUTE_COMPLETED_MILLIS)),
                    order.contains(TAG_ORDER_ROUTE_DISTANCE_BLOCKS) ? order.getInt(TAG_ORDER_ROUTE_DISTANCE_BLOCKS) : -1,
                    Math.max(0L, order.getLong(TAG_ORDER_PAYOUT_CENTS))
            ));
        }
        out.sort(Comparator.comparingLong(OrderView::createdAtMillis).reversed());
        return out;
    }

    private static Set<String> collectAssignedPalletRefSet(CompoundTag shop) {
        Set<String> refs = new HashSet<>();
        if (shop == null) {
            return refs;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag pallet)) {
                continue;
            }
            String key = assignedPalletKey(pallet);
            if (!key.isBlank()) {
                refs.add(key);
            }
        }
        return refs;
    }

    private static int pruneLegacyCoordinateOrderPallets(CompoundTag shop) {
        if (shop == null) {
            return 0;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        if (pallets.isEmpty()) {
            return 0;
        }
        ListTag next = new ListTag();
        Set<String> seen = new HashSet<>();
        int removed = 0;
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag pallet)) {
                removed++;
                continue;
            }
            String key = assignedPalletKey(pallet);
            if (key.isBlank()) {
                // Legacy coord-only entries are no longer valid assignment records.
                removed++;
                continue;
            }
            if (!seen.add(key)) {
                removed++;
                continue;
            }
            CompoundTag copy = pallet.copy();
            copy.putString(TAG_PALLET_ID, key);
            next.add(copy);
        }
        if (removed > 0) {
            shop.put(TAG_ORDER_PALLETS, next);
        }
        return removed;
    }

    private static int pruneOrderPalletsOutsideClaims(CompoundTag shop) {
        if (shop == null) {
            return 0;
        }
        ListTag claims = shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        if (pallets.isEmpty()) {
            return 0;
        }
        ListTag next = new ListTag();
        int removed = 0;
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag pallet)) {
                continue;
            }
            String key = assignedPalletKey(pallet);
            if (key.isBlank()) {
                removed++;
                continue;
            }
            // ID-based assignments are position-independent; keep them even
            // if last-known coords are stale until explicit unassign.
            next.add(pallet.copy());
        }
        if (removed > 0) {
            shop.put(TAG_ORDER_PALLETS, next);
        }
        return removed;
    }

    /**
     * Reconciles assigned delivery pallet IDs with live pallet entities in world.
     * Entries are removed when their pallet no longer exists, and stale last-known
     * coordinates are repaired in-place when the same pallet ID is still present.
     *
     * Optimized path:
     * - First validates against the pallet's last-known coordinates.
     * - Falls back to one claim-scan lookup only when needed.
     */
    private static int pruneAssignedPalletsMissingInWorld(MinecraftServer server, CompoundTag shop) {
        if (server == null || shop == null) {
            return 0;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        if (pallets.isEmpty()) {
            return 0;
        }

        ListTag claims = deliveryPalletSearchClaims(shop);
        ListTag next = new ListTag();
        Map<String, PalletRef> liveLookup = null;
        Set<String> seen = new HashSet<>();
        int removed = 0;
        boolean changed = false;

        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                removed++;
                changed = true;
                continue;
            }

            CompoundTag copy = palletTag.copy();
            String key = assignedPalletKey(copy);
            if (key.isBlank()) {
                removed++;
                changed = true;
                continue;
            }
            if (!seen.add(key)) {
                removed++;
                changed = true;
                continue;
            }

            PalletRef resolved = null;
            PalletRef lastKnown = legacyAssignedPalletRef(copy);
            boolean chunkLoaded = true;
            if (lastKnown != null) {
                chunkLoaded = server.getLevel(serverLevelKey(lastKnown.dimensionId())) != null
                        && server.getLevel(serverLevelKey(lastKnown.dimensionId())).hasChunk(lastKnown.pos().getX() >> 4, lastKnown.pos().getZ() >> 4);

                PalletRef liveAtLastKnown = resolveLivePalletRef(server, lastKnown);
                if (liveAtLastKnown != null) {
                    String liveId = normalizeDeliveryPalletId(readPalletId(server, liveAtLastKnown));
                    if (key.equalsIgnoreCase(liveId)) {
                        resolved = liveAtLastKnown;
                    }
                }
            }

            if (resolved == null) {
                if (liveLookup == null) {
                    liveLookup = buildLivePalletLookup(server, claims);
                }
                resolved = liveLookup.get(key);
            }

            if (resolved == null) {
                // Only prune if we are certain the pallet is gone from its last known location.
                // If the chunk is not loaded, we assume it's still there.
                if (!chunkLoaded) {
                    next.add(copy);
                    continue;
                }
                removed++;
                changed = true;
                continue;
            }

            String normDim = normalizedDim(resolved.dimensionId());
            BlockPos pos = resolved.pos();
            if (!normDim.equalsIgnoreCase(normalizedDim(copy.getString(TAG_DIM)))
                    || copy.getInt(TAG_X) != pos.getX()
                    || copy.getInt(TAG_Y) != pos.getY()
                    || copy.getInt(TAG_Z) != pos.getZ()) {
                copy.putString(TAG_DIM, normDim);
                copy.putInt(TAG_X, pos.getX());
                copy.putInt(TAG_Y, pos.getY());
                copy.putInt(TAG_Z, pos.getZ());
                changed = true;
            }
            if (!key.equals(normalizeDeliveryPalletId(copy.getString(TAG_PALLET_ID)))) {
                copy.putString(TAG_PALLET_ID, key);
                changed = true;
            }

            next.add(copy);
        }

        if (changed) {
            shop.put(TAG_ORDER_PALLETS, next);
        }
        return removed;
    }

    private static int pruneOrderPalletBindingsOutsideAssigned(CompoundTag shop) {
        if (shop == null) {
            return 0;
        }
        Set<String> assigned = collectAssignedPalletRefSet(shop);
        int changed = 0;
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            if (!order.contains(TAG_ORDER_PALLET_REF) && !order.contains(TAG_ORDER_PALLET_ID)) {
                continue;
            }
            String normalized = normalizeOrderPalletBindingKey(order);
            if (normalized.isBlank() || !assigned.contains(normalized)) {
                order.remove(TAG_ORDER_PALLET_ID);
                order.remove(TAG_ORDER_PALLET_REF);
                changed++;
                continue;
            }
            String current = normalizeAssignedPalletKey(order.getString(TAG_ORDER_PALLET_ID));
            if (!normalized.equalsIgnoreCase(current)) {
                order.putString(TAG_ORDER_PALLET_ID, normalized);
                order.remove(TAG_ORDER_PALLET_REF);
                changed++;
            }
        }
        return changed;
    }

    private static boolean updateAssignedPalletLastKnownPosition(CompoundTag shop,
                                                                 String palletKey,
                                                                 String dimensionId,
                                                                 BlockPos pos) {
        if (shop == null || pos == null) {
            return false;
        }
        String target = normalizeAssignedPalletKey(palletKey);
        if (target.isBlank()) {
            return false;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        boolean changed = false;
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                continue;
            }
            String key = assignedPalletKey(palletTag);
            if (key.isBlank() || !key.equalsIgnoreCase(target)) {
                continue;
            }
            String normDim = normalizedDim(dimensionId);
            if (!normDim.equalsIgnoreCase(normalizedDim(palletTag.getString(TAG_DIM)))
                    || palletTag.getInt(TAG_X) != pos.getX()
                    || palletTag.getInt(TAG_Y) != pos.getY()
                    || palletTag.getInt(TAG_Z) != pos.getZ()) {
                palletTag.putString(TAG_DIM, normDim);
                palletTag.putInt(TAG_X, pos.getX());
                palletTag.putInt(TAG_Y, pos.getY());
                palletTag.putInt(TAG_Z, pos.getZ());
                changed = true;
            }
            if (target.contains(";")) {
                // Legacy key path; no ID to synchronize.
                return changed;
            }
            String currentId = normalizeDeliveryPalletId(palletTag.getString(TAG_PALLET_ID));
            if (!target.equals(currentId)) {
                palletTag.putString(TAG_PALLET_ID, target);
                changed = true;
            }
            return changed;
        }
        return false;
    }

    private static boolean expireOrdersInPlace(CentralBank centralBank, CompoundTag shop, long now) {
        if (shop == null) {
            return false;
        }
        boolean changed = false;
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order)) {
                continue;
            }
            String status = normalizeOrderStatus(order.getString(TAG_ORDER_STATUS));
            if (!ORDER_STATUS_ACCEPTED.equals(status)) {
                continue;
            }
            long expiresAt = Math.max(0L, order.getLong(TAG_ORDER_EXPIRES_AT));
            if (expiresAt <= 0L || expiresAt > now) {
                continue;
            }
            ShopActionResult release = releaseOrderReservation(centralBank, shop, order, "EXPIRED");
            if (!release.success()) {
                continue;
            }
            order.putString(TAG_ORDER_STATUS, ORDER_STATUS_EXPIRED);
            clearOrderAcceptance(order);
            changed = true;
        }
        return changed;
    }

    private static CompoundTag findOrderById(CompoundTag shop, UUID orderId) {
        if (shop == null || orderId == null) {
            return null;
        }
        ListTag orders = shop.getList(TAG_ORDERS, Tag.TAG_COMPOUND);
        for (Tag tag : orders) {
            if (!(tag instanceof CompoundTag order) || !order.contains(TAG_ORDER_ID)) {
                continue;
            }
            try {
                if (orderId.equals(order.getUUID(TAG_ORDER_ID))) {
                    return order;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void clearOrderAcceptance(CompoundTag order) {
        if (order == null) {
            return;
        }
        order.remove(TAG_ORDER_ACCEPTED_BY);
        order.remove(TAG_ORDER_ACCEPTED_AT);
        order.remove(TAG_ORDER_EXPIRES_AT);
    }

    private static long computeOrderReservedCents(long rewardCents) {
        long safeReward = Math.max(0L, rewardCents);
        if (safeReward <= 0L) {
            return 0L;
        }
        long bonusBuffer = BigDecimal.valueOf(safeReward)
                .multiply(BigDecimal.valueOf(ORDER_RESERVE_BONUS_BUFFER_PCT))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO)
                .longValue();
        return safeAdd(safeReward, bonusBuffer);
    }

    private static void clearOrderReservation(CompoundTag order) {
        if (order == null) {
            return;
        }
        order.remove(TAG_ORDER_RESERVED_CENTS);
        order.remove(TAG_ORDER_RESERVED_FROM_ACCOUNT);
    }

    private static UUID readOrderReservedSourceAccount(CompoundTag order) {
        if (order == null || !order.contains(TAG_ORDER_RESERVED_FROM_ACCOUNT)) {
            return null;
        }
        try {
            return order.getUUID(TAG_ORDER_RESERVED_FROM_ACCOUNT);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static AccountHolder resolveOrderReserveSource(CentralBank centralBank, CompoundTag shop, CompoundTag order) {
        if (centralBank == null) {
            return null;
        }
        UUID sourceId = readOrderReservedSourceAccount(order);
        if (sourceId != null) {
            AccountHolder source = centralBank.SearchForAccountByAccountId(sourceId);
            if (source != null) {
                return source;
            }
        }
        UUID ownerId = null;
        UUID shopId = null;
        try {
            if (shop != null && shop.contains(TAG_OWNER)) {
                ownerId = shop.getUUID(TAG_OWNER);
            }
            if (shop != null && shop.contains(TAG_ID)) {
                shopId = shop.getUUID(TAG_ID);
            }
        } catch (Exception ignored) {
            ownerId = null;
            shopId = null;
        }
        UUID settlementId = resolveSettlementAccountId(centralBank, ownerId, shopId, null);
        if (settlementId == null) {
            return null;
        }
        return centralBank.SearchForAccountByAccountId(settlementId);
    }

    private static ShopActionResult releaseOrderReservation(CentralBank centralBank,
                                                            CompoundTag shop,
                                                            CompoundTag order,
                                                            String reason) {
        if (order == null) {
            return new ShopActionResult(false, "Order is unavailable.");
        }
        long reservedCents = Math.max(0L, order.getLong(TAG_ORDER_RESERVED_CENTS));
        if (reservedCents <= 0L) {
            clearOrderReservation(order);
            return new ShopActionResult(true, "No reserved funds.");
        }
        AccountHolder source = resolveOrderReserveSource(centralBank, shop, order);
        if (source == null) {
            return new ShopActionResult(false, "Reserved payout account is unavailable.");
        }
        BigDecimal amount = BigDecimal.valueOf(reservedCents, 2);
        if (!source.forceAddBalance(amount)) {
            return new ShopActionResult(false, "Failed to release reserved payout funds.");
        }
        UserTransaction refundTx = new UserTransaction(
                source.getAccountUUID(),
                source.getAccountUUID(),
                amount,
                LocalDateTime.now(),
                "SHOP_ORDER_RESERVE_RELEASE:" + sanitizeTokenText(reason)
        );
        source.addTransaction(refundTx);
        clearOrderReservation(order);
        return new ShopActionResult(true, "Reserved payout released.");
    }

    private static String normalizeOrderStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return ORDER_STATUS_OPEN;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case ORDER_STATUS_OPEN,
                 ORDER_STATUS_ACCEPTED,
                 ORDER_STATUS_COMPLETED,
                 ORDER_STATUS_CANCELED,
                 ORDER_STATUS_EXPIRED -> normalized;
            default -> ORDER_STATUS_OPEN;
        };
    }

    private static String encodeOrderPalletRef(String dimensionId, BlockPos pos) {
        if (pos == null) {
            return "";
        }
        return normalizedDim(dimensionId)
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ();
    }

    private static PalletRef decodeOrderPalletRef(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            String dim = normalizedDim(parts[0]);
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            return new PalletRef(dim, new BlockPos(x, y, z));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeOrderPalletRef(String raw) {
        PalletRef ref = decodeOrderPalletRef(raw);
        return ref == null ? "" : encodeOrderPalletRef(ref.dimensionId(), ref.pos());
    }

    private static String normalizeDeliveryPalletId(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeAssignedPalletKey(String raw) {
        String normalizedRef = normalizeOrderPalletRef(raw);
        if (!normalizedRef.isBlank()) {
            return normalizedRef;
        }
        return normalizeDeliveryPalletId(raw);
    }

    private static String normalizeOrderPalletBindingKey(CompoundTag order) {
        if (order == null) {
            return "";
        }
        String byId = normalizeAssignedPalletKey(order.getString(TAG_ORDER_PALLET_ID));
        if (!byId.isBlank()) {
            return byId;
        }
        return normalizeOrderPalletRef(order.getString(TAG_ORDER_PALLET_REF));
    }

    private static String assignedPalletKey(CompoundTag palletTag) {
        if (palletTag == null) {
            return "";
        }
        // Delivery pallet assignment is ID-only.
        return normalizeDeliveryPalletId(palletTag.getString(TAG_PALLET_ID));
    }

    private static PalletRef legacyAssignedPalletRef(CompoundTag palletTag) {
        if (palletTag == null || !palletTag.contains(TAG_DIM)) {
            return null;
        }
        String dim = normalizedDim(palletTag.getString(TAG_DIM));
        BlockPos pos = new BlockPos(palletTag.getInt(TAG_X), palletTag.getInt(TAG_Y), palletTag.getInt(TAG_Z));
        return new PalletRef(dim, pos);
    }

    private static boolean normalizeOrderPalletMode(String rawMode, String rawSelectedRef) {
        if (rawMode != null && !rawMode.isBlank()) {
            String normalized = rawMode.trim().toUpperCase(Locale.ROOT);
            if ("SPECIFIC".equals(normalized)) {
                return true;
            }
            if ("RANDOM".equals(normalized)) {
                return false;
            }
        }
        return rawSelectedRef != null && !rawSelectedRef.isBlank();
    }

    private static PalletRef resolveLivePalletRef(MinecraftServer server, PalletRef ref) {
        if (server == null || ref == null || ref.pos() == null) {
            return null;
        }
        ServerLevel level = server.getLevel(serverLevelKey(ref.dimensionId()));
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(ref.pos());
        if (!state.is(ModBlocks.PALLET.get())) {
            return null;
        }
        BlockPos master = PalletBlock.getMasterPos(state, ref.pos());
        if (!level.getBlockState(master).is(ModBlocks.PALLET.get())) {
            return null;
        }
        return new PalletRef(normalizedDim(level.dimension().location().toString()), master);
    }

    private static String resolvePalletAssignmentKey(ServerLevel level, BlockPos masterPos, boolean createIfMissing) {
        if (level == null || masterPos == null) {
            return "";
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (blockEntity instanceof PalletBlockEntity pallet) {
            String id = createIfMissing
                    ? normalizeDeliveryPalletId(pallet.ensureDeliveryPalletId())
                    : normalizeDeliveryPalletId(pallet.getDeliveryPalletId());
            if (!id.isBlank()) {
                return id;
            }
        }
        return encodeOrderPalletRef(level.dimension().location().toString(), masterPos);
    }

    public static ShopActionResult validateDeliveryPalletPlacement(MinecraftServer server,
                                                                   ServerLevel level,
                                                                   BlockPos palletMasterPos,
                                                                   String deliveryPalletId) {
        if (server == null || level == null || palletMasterPos == null) {
            return new ShopActionResult(false, "Delivery pallet placement context is unavailable.");
        }
        String id = normalizeDeliveryPalletId(deliveryPalletId);
        if (id.isBlank()) {
            return new ShopActionResult(true, "");
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return new ShopActionResult(false, "Bank data is unavailable.");
        }
        String dim = normalizedDim(level.dimension().location().toString());
        for (CompoundTag shop : getAllShops(centralBank)) {
            Set<String> assigned = collectAssignedPalletRefSet(shop);
            if (!assigned.contains(id)) {
                continue;
            }
            if (!isInsideDeliveryPalletClaims(shop, dim, palletMasterPos)) {
                return new ShopActionResult(false, "Delivery pallet must be placed inside its assigned shop plot or stockroom claim.");
            }
            updateAssignedPalletLastKnownPosition(shop, id, dim, palletMasterPos);
            saveShopTag(centralBank, shop);
            String shopName = sanitizeTokenText(shop.getString(TAG_NAME));
            if (shopName.isBlank()) {
                shopName = "Shop";
            }
            PalletRef ref = new PalletRef(dim, palletMasterPos);
            String encoded = encodeOrderPalletRef(dim, palletMasterPos);
            if (!encoded.isBlank()) {
                DELIVERY_PALLET_LABEL_CACHE.put(encoded, shopName);
            }
            applyDeliveryPalletLabelToWorld(server, ref, true, shopName);
            syncDeliveryPalletLabelsNow(server);
            return new ShopActionResult(true, "");
        }
        // Unknown IDs are allowed for backward compatibility; assignment occurs through claim tools.
        return new ShopActionResult(true, "");
    }

    /**
     * Removes a delivery pallet assignment when the backing pallet block no longer exists.
     * This keeps shop registry state live and avoids stale "assigned pallet" entries.
     */
    public static void handleDeliveryPalletRemoved(MinecraftServer server, String deliveryPalletId) {
        if (server == null) {
            return;
        }
        String id = normalizeDeliveryPalletId(deliveryPalletId);
        if (id.isBlank()) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            int removed = removeAssignedPalletById(shop, id);
            if (removed <= 0) {
                continue;
            }
            pruneOrderPalletBindingsOutsideAssigned(shop);
            saveShopTag(centralBank, shop);
        }
    }

    private static int removeAssignedPalletById(CompoundTag shop, String palletId) {
        if (shop == null) {
            return 0;
        }
        String normalizedId = normalizeDeliveryPalletId(palletId);
        if (normalizedId.isBlank()) {
            return 0;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        if (pallets.isEmpty()) {
            return 0;
        }
        ListTag next = new ListTag();
        int removed = 0;
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                continue;
            }
            String key = assignedPalletKey(palletTag);
            if (normalizedId.equalsIgnoreCase(key)) {
                removed++;
                continue;
            }
            next.add(palletTag.copy());
        }
        if (removed > 0) {
            shop.put(TAG_ORDER_PALLETS, next);
        }
        return removed;
    }

    private static boolean palletHasFreeSlot(MinecraftServer server, PalletRef ref) {
        PalletRef liveRef = resolveLivePalletRef(server, ref);
        if (liveRef == null) {
            return false;
        }
        ServerLevel level = server.getLevel(serverLevelKey(liveRef.dimensionId()));
        if (level == null) {
            return false;
        }
        BlockEntity blockEntity = level.getBlockEntity(liveRef.pos());
        if (!(blockEntity instanceof PalletBlockEntity pallet)) {
            return false;
        }
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack stack = pallet.getBox(column, layer);
                if (stack == null || stack.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countAssignedPalletsWithFreeSlots(MinecraftServer server,
                                                         Set<String> assignedRefs,
                                                         Map<String, PalletRef> liveLookup) {
        if (server == null || assignedRefs == null || assignedRefs.isEmpty()) {
            return 0;
        }
        int available = 0;
        for (String rawRef : assignedRefs) {
            if (rawRef == null || rawRef.isBlank()) {
                continue;
            }
            PalletRef ref = liveLookup == null ? null : liveLookup.get(rawRef);
            if (ref == null) {
                ref = decodeOrderPalletRef(rawRef);
            }
            if (ref == null) {
                continue;
            }
            if (palletHasFreeSlot(server, ref)) {
                available++;
            }
        }
        return available;
    }

    private static String formatPalletRef(String encodedRef) {
        PalletRef ref = decodeOrderPalletRef(encodedRef);
        if (ref == null) {
            String key = normalizeAssignedPalletKey(encodedRef);
            return key.isBlank() ? "-" : ("id " + shortPalletId(key));
        }
        return ref.dimensionId() + " (" + ref.pos().getX() + "," + ref.pos().getY() + "," + ref.pos().getZ() + ")";
    }

    private static String shortPalletId(String palletId) {
        String safe = normalizeAssignedPalletKey(palletId);
        if (safe.isBlank()) {
            return "-";
        }
        if (safe.contains(";")) {
            return safe;
        }
        return safe.length() <= 8 ? safe : safe.substring(0, 8);
    }

    private static String readPalletId(MinecraftServer server, PalletRef ref) {
        PalletBlockEntity pallet = getPalletBlockEntity(server, ref);
        if (pallet == null) {
            return "";
        }
        return normalizeDeliveryPalletId(pallet.getDeliveryPalletId());
    }

    private static String ensurePalletId(MinecraftServer server, PalletRef ref) {
        PalletBlockEntity pallet = getPalletBlockEntity(server, ref);
        if (pallet == null) {
            return "";
        }
        return normalizeDeliveryPalletId(pallet.ensureDeliveryPalletId());
    }

    private static PalletBlockEntity getPalletBlockEntity(MinecraftServer server, PalletRef ref) {
        if (server == null || ref == null || ref.pos() == null) {
            return null;
        }
        ServerLevel level = server.getLevel(serverLevelKey(ref.dimensionId()));
        return getPalletBlockEntity(level, ref.pos());
    }

    private static PalletBlockEntity getPalletBlockEntity(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null || !level.hasChunkAt(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.PALLET.get())) {
            return null;
        }
        BlockPos masterPos = PalletBlock.getMasterPos(state, pos);
        if (masterPos == null || !level.hasChunkAt(masterPos) || !level.getBlockState(masterPos).is(ModBlocks.PALLET.get())) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        return blockEntity instanceof PalletBlockEntity pallet ? pallet : null;
    }

    private static List<PalletRef> collectPalletsInClaims(MinecraftServer server, ListTag claims) {
        List<PalletRef> out = new ArrayList<>();
        if (server == null || claims == null || claims.isEmpty()) {
            return out;
        }
        Set<String> visited = new HashSet<>();
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            String dim = normalizedDim(claim.getString(TAG_DIM));
            ServerLevel level = server.getLevel(serverLevelKey(dim));
            if (level == null) {
                continue;
            }
            int minX = regionMinX(claim);
            int minY = regionMinY(claim);
            int minZ = regionMinZ(claim);
            int maxX = regionMaxX(claim);
            int maxY = regionMaxY(claim);
            int maxZ = regionMaxZ(claim);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!level.getBlockState(pos).is(ModBlocks.PALLET.get())) {
                            continue;
                        }
                        BlockPos master = PalletBlock.getMasterPos(level.getBlockState(pos), pos);
                        String ref = encodeOrderPalletRef(dim, master);
                        if (visited.add(ref)) {
                            out.add(new PalletRef(dim, master));
                        }
                    }
                }
            }
        }
        out.sort(Comparator
                .comparing(PalletRef::dimensionId, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(p -> p.pos().getX())
                .thenComparingInt(p -> p.pos().getY())
                .thenComparingInt(p -> p.pos().getZ()));
        return out;
    }

    private static Map<String, PalletRef> buildLivePalletLookup(MinecraftServer server, ListTag claims) {
        Map<String, PalletRef> out = new HashMap<>();
        if (server == null || claims == null || claims.isEmpty()) {
            return out;
        }
        for (PalletRef ref : collectPalletsInClaims(server, claims)) {
            if (ref == null || ref.pos() == null) {
                continue;
            }
            String legacyKey = encodeOrderPalletRef(ref.dimensionId(), ref.pos());
            if (!legacyKey.isBlank()) {
                out.putIfAbsent(legacyKey, ref);
            }
            String id = readPalletId(server, ref);
            if (!id.isBlank()) {
                out.putIfAbsent(id, ref);
            }
        }
        return out;
    }

    private static PalletRef resolveAssignedPalletLiveRef(MinecraftServer server,
                                                          CompoundTag shop,
                                                          String assignedKey,
                                                          Map<String, PalletRef> liveLookup) {
        if (server == null || shop == null) {
            return null;
        }
        String target = normalizeAssignedPalletKey(assignedKey);
        if (target.isBlank()) {
            return null;
        }

        PalletRef savedRef = resolveAssignedPalletLastKnownRef(server, shop, target);
        if (savedRef != null) {
            return savedRef;
        }

        PalletRef scannedRef = liveLookup == null ? null : liveLookup.get(target);
        if (scannedRef != null) {
            return scannedRef;
        }

        PalletRef legacyRef = decodeOrderPalletRef(target);
        return legacyRef == null ? null : resolveLivePalletRef(server, legacyRef);
    }

    private static PalletRef resolveAssignedPalletLastKnownRef(MinecraftServer server,
                                                               CompoundTag shop,
                                                               String assignedKey) {
        if (server == null || shop == null) {
            return null;
        }
        String target = normalizeAssignedPalletKey(assignedKey);
        if (target.isBlank()) {
            return null;
        }
        ListTag pallets = shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND);
        for (Tag tag : pallets) {
            if (!(tag instanceof CompoundTag palletTag)) {
                continue;
            }
            String key = assignedPalletKey(palletTag);
            if (key.isBlank() || !key.equalsIgnoreCase(target)) {
                continue;
            }
            PalletRef lastKnown = legacyAssignedPalletRef(palletTag);
            PalletRef resolved = resolveLivePalletRef(server, lastKnown);
            if (resolved != null && livePalletMatchesAssignedKey(server, resolved, target)) {
                return resolved;
            }
        }
        return null;
    }

    private static boolean livePalletMatchesAssignedKey(MinecraftServer server, PalletRef ref, String assignedKey) {
        if (server == null || ref == null || ref.pos() == null) {
            return false;
        }
        String target = normalizeAssignedPalletKey(assignedKey);
        if (target.isBlank()) {
            return false;
        }
        String liveRef = encodeOrderPalletRef(ref.dimensionId(), ref.pos());
        if (!liveRef.isBlank() && liveRef.equalsIgnoreCase(target)) {
            return true;
        }
        String liveId = readPalletId(server, ref);
        return !liveId.isBlank() && liveId.equalsIgnoreCase(target);
    }

    private static int countActiveCourierOrders(CentralBank centralBank, UUID courierId, long now) {
        if (centralBank == null || courierId == null) {
            return 0;
        }
        int count = 0;
        for (CompoundTag shop : getAllShops(centralBank)) {
            boolean changed = expireOrdersInPlace(centralBank, shop, now);
            for (OrderView order : collectOrderViews(shop)) {
                if (ORDER_STATUS_ACCEPTED.equals(order.status()) && courierId.equals(order.acceptedBy())) {
                    count++;
                }
            }
            if (changed) {
                saveShopTag(centralBank, shop);
            }
        }
        return count;
    }

    private static CourierProgress readCourierProgress(CentralBank centralBank, UUID courierId) {
        if (centralBank == null || courierId == null) {
            return CourierProgress.empty();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        CompoundTag entry = getCourierStatsEntry(root, courierId, false);
        if (entry == null) {
            return CourierProgress.empty();
        }
        long completed = Math.max(0L, entry.getLong(TAG_COURIER_COMPLETED));
        long canceled = Math.max(0L, entry.getLong(TAG_COURIER_CANCELED));
        long streak = Math.max(0L, entry.getLong(TAG_COURIER_STREAK));
        long bestStreak = Math.max(0L, entry.getLong(TAG_COURIER_BEST_STREAK));
        long totalPayoutCents = Math.max(0L, entry.getLong(TAG_COURIER_TOTAL_PAYOUT_CENTS));
        long lastActivityAt = Math.max(0L, entry.getLong(TAG_COURIER_LAST_ACTIVITY_AT));
        return new CourierProgress(
                completed,
                canceled,
                streak,
                bestStreak,
                totalPayoutCents,
                lastActivityAt,
                computeCourierSuccessRate(completed, canceled)
        );
    }

    private static CourierProgress recordCourierCompletion(CentralBank centralBank,
                                                           UUID courierId,
                                                           String courierName,
                                                           long payoutCents,
                                                           int routeDistanceBlocks,
                                                           long routeMillis,
                                                           long now) {
        if (centralBank == null || courierId == null) {
            return CourierProgress.empty();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        CompoundTag entry = getCourierStatsEntry(root, courierId, true);
        writeCourierName(entry, courierName, courierId);
        long completed = safeAdd(Math.max(0L, entry.getLong(TAG_COURIER_COMPLETED)), 1L);
        long canceled = Math.max(0L, entry.getLong(TAG_COURIER_CANCELED));
        long streak = safeAdd(Math.max(0L, entry.getLong(TAG_COURIER_STREAK)), 1L);
        long bestStreak = Math.max(Math.max(0L, entry.getLong(TAG_COURIER_BEST_STREAK)), streak);
        long totalPayoutCents = safeAdd(Math.max(0L, entry.getLong(TAG_COURIER_TOTAL_PAYOUT_CENTS)), Math.max(0L, payoutCents));

        entry.putLong(TAG_COURIER_COMPLETED, completed);
        entry.putLong(TAG_COURIER_CANCELED, canceled);
        entry.putLong(TAG_COURIER_STREAK, streak);
        entry.putLong(TAG_COURIER_BEST_STREAK, bestStreak);
        entry.putLong(TAG_COURIER_TOTAL_PAYOUT_CENTS, totalPayoutCents);
        entry.putLong(TAG_COURIER_LAST_ACTIVITY_AT, Math.max(0L, now));
        maybeRecordBestRoute(entry, routeDistanceBlocks, routeMillis, now);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);

        return new CourierProgress(
                completed,
                canceled,
                streak,
                bestStreak,
                totalPayoutCents,
                Math.max(0L, now),
                computeCourierSuccessRate(completed, canceled)
        );
    }

    private static CourierProgress recordCourierCancel(CentralBank centralBank, UUID courierId, String courierName, long now) {
        if (centralBank == null || courierId == null) {
            return CourierProgress.empty();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        CompoundTag entry = getCourierStatsEntry(root, courierId, true);
        writeCourierName(entry, courierName, courierId);
        long completed = Math.max(0L, entry.getLong(TAG_COURIER_COMPLETED));
        long canceled = safeAdd(Math.max(0L, entry.getLong(TAG_COURIER_CANCELED)), 1L);
        long bestStreak = Math.max(0L, entry.getLong(TAG_COURIER_BEST_STREAK));
        long totalPayoutCents = Math.max(0L, entry.getLong(TAG_COURIER_TOTAL_PAYOUT_CENTS));

        entry.putLong(TAG_COURIER_COMPLETED, completed);
        entry.putLong(TAG_COURIER_CANCELED, canceled);
        entry.putLong(TAG_COURIER_STREAK, 0L);
        entry.putLong(TAG_COURIER_BEST_STREAK, bestStreak);
        entry.putLong(TAG_COURIER_TOTAL_PAYOUT_CENTS, totalPayoutCents);
        entry.putLong(TAG_COURIER_LAST_ACTIVITY_AT, Math.max(0L, now));
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);

        return new CourierProgress(
                completed,
                canceled,
                0L,
                bestStreak,
                totalPayoutCents,
                Math.max(0L, now),
                computeCourierSuccessRate(completed, canceled)
        );
    }

    private static void recordCourierSeen(CentralBank centralBank, UUID courierId, String courierName, long now) {
        if (centralBank == null || courierId == null) {
            return;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        CompoundTag entry = getCourierStatsEntry(root, courierId, true);
        writeCourierName(entry, courierName, courierId);
        entry.putLong(TAG_COURIER_LAST_ACTIVITY_AT, Math.max(0L, now));
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
    }

    private static void writeCourierName(CompoundTag entry, String courierName, UUID courierId) {
        if (entry == null) {
            return;
        }
        String current = sanitizeTokenText(entry.getString(TAG_COURIER_NAME));
        String name = sanitizeTokenText(courierName);
        if (isCourierNameFallback(name, courierId)) {
            if (!isCourierNameFallback(current, courierId)) {
                return;
            }
            name = courierId == null ? "-" : shortUuid(courierId);
        }
        entry.putString(TAG_COURIER_NAME, name);
    }

    private static boolean isCourierNameFallback(String name, UUID courierId) {
        String value = sanitizeTokenText(name);
        if (value.isBlank() || "-".equals(value)) {
            return true;
        }
        if (courierId == null) {
            return false;
        }
        return value.equalsIgnoreCase(shortUuid(courierId))
                || value.equalsIgnoreCase(courierId.toString());
    }

    private static String resolveCourierProfileName(MinecraftServer server, UUID courierId) {
        if (server == null || courierId == null) {
            return "";
        }
        ServerPlayer online = server.getPlayerList().getPlayer(courierId);
        if (online != null && online.getName() != null) {
            String name = sanitizeTokenText(online.getName().getString());
            if (!name.isBlank()) {
                return name;
            }
        }
        if (server.getProfileCache() != null) {
            var cached = server.getProfileCache().get(courierId);
            if (cached.isPresent()) {
                String name = sanitizeTokenText(cached.get().getName());
                if (!name.isBlank()) {
                    return name;
                }
            }
        }
        return "";
    }

    private static void maybeRecordBestRoute(CompoundTag entry, int distanceBlocks, long routeMillis, long now) {
        if (entry == null || distanceBlocks < 1 || routeMillis <= 0L) {
            return;
        }
        long score = Math.max(1L, routeMillis / Math.max(1, distanceBlocks));
        long currentScore = Math.max(0L, entry.getLong(TAG_COURIER_BEST_ROUTE_SCORE));
        if (currentScore > 0L && currentScore <= score) {
            return;
        }
        entry.putLong(TAG_COURIER_BEST_ROUTE_SCORE, score);
        entry.putLong(TAG_COURIER_BEST_ROUTE_MILLIS, routeMillis);
        entry.putInt(TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS, distanceBlocks);
        entry.putLong(TAG_COURIER_BEST_ROUTE_AT, Math.max(0L, now));
    }

    private static int computeCourierSuccessRate(long completed, long canceled) {
        long done = Math.max(0L, completed);
        long failed = Math.max(0L, canceled);
        long total = done + failed;
        if (total <= 0L) {
            return 100;
        }
        return (int) Math.max(0L, Math.min(100L, (done * 100L) / total));
    }

    private static String courierDisplayName(ServerPlayer player, UUID courierId) {
        if (player != null && player.getGameProfile() != null) {
            String name = sanitizeTokenText(player.getGameProfile().getName());
            if (!name.isBlank() && !"-".equals(name)) {
                return name;
            }
        }
        return courierId == null ? "-" : shortUuid(courierId);
    }

    private static CompoundTag getCourierStatsEntry(CompoundTag root, UUID courierId, boolean create) {
        if (root == null || courierId == null) {
            return null;
        }
        ListTag couriers = root.getList(TAG_ORDER_COURIERS, Tag.TAG_COMPOUND);
        for (Tag tag : couriers) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            if (!entry.contains(TAG_COURIER_ID)) {
                continue;
            }
            try {
                if (courierId.equals(entry.getUUID(TAG_COURIER_ID))) {
                    return entry;
                }
            } catch (Exception ignored) {
            }
        }
        if (!create) {
            return null;
        }
        // Persist courier progression centrally so all shops share the same delivery profile.
        CompoundTag created = new CompoundTag();
        created.putUUID(TAG_COURIER_ID, courierId);
        created.putString(TAG_COURIER_NAME, shortUuid(courierId));
        created.putLong(TAG_COURIER_COMPLETED, 0L);
        created.putLong(TAG_COURIER_CANCELED, 0L);
        created.putLong(TAG_COURIER_STREAK, 0L);
        created.putLong(TAG_COURIER_BEST_STREAK, 0L);
        created.putLong(TAG_COURIER_TOTAL_PAYOUT_CENTS, 0L);
        created.putLong(TAG_COURIER_LAST_ACTIVITY_AT, 0L);
        created.putLong(TAG_COURIER_BEST_ROUTE_MILLIS, 0L);
        created.putInt(TAG_COURIER_BEST_ROUTE_DISTANCE_BLOCKS, -1);
        created.putLong(TAG_COURIER_BEST_ROUTE_SCORE, 0L);
        created.putLong(TAG_COURIER_BEST_ROUTE_AT, 0L);
        couriers.add(created);
        root.put(TAG_ORDER_COURIERS, couriers);
        return created;
    }

    private static DeliveryPayout computeDeliveryPayout(CompoundTag order,
                                                        long baseRewardCents,
                                                        long currentStreak,
                                                        long completedAtMillis) {
        long safeBase = Math.max(0L, baseRewardCents);
        if (safeBase <= 0L) {
            return new DeliveryPayout(0L, 0L, 0L, 0, 0, 0);
        }

        long acceptedAt = order == null ? 0L : Math.max(0L, order.getLong(TAG_ORDER_ACCEPTED_AT));
        int timeoutMinutes = order == null
                ? ORDER_TIMEOUT_MINUTES_DEFAULT
                : Mth.clamp(order.getInt(TAG_ORDER_TIMEOUT_MINUTES), ORDER_TIMEOUT_MINUTES_MIN, ORDER_TIMEOUT_MINUTES_MAX);
        long timeoutMillis = Math.max(1L, timeoutMinutes * 60_000L);
        long elapsedMillis = acceptedAt <= 0L ? timeoutMillis : Math.max(0L, completedAtMillis - acceptedAt);

        int speedBonusPct;
        if (elapsedMillis <= (timeoutMillis / 4L)) {
            speedBonusPct = 10;
        } else if (elapsedMillis <= (timeoutMillis / 2L)) {
            speedBonusPct = 6;
        } else if (elapsedMillis <= ((timeoutMillis * 3L) / 4L)) {
            speedBonusPct = 3;
        } else {
            speedBonusPct = 0;
        }

        long streakAfter = Math.max(0L, currentStreak) + 1L;
        int streakBonusPct = Math.min(10, (int) ((streakAfter / 3L) * 2L));
        int totalBonusPct = Math.min(20, speedBonusPct + streakBonusPct);

        long bonusCents = BigDecimal.valueOf(safeBase)
                .multiply(BigDecimal.valueOf(totalBonusPct))
                .divide(BigDecimal.valueOf(100L), 0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO)
                .longValue();
        long payout = safeAdd(safeBase, bonusCents);
        return new DeliveryPayout(safeBase, bonusCents, payout, speedBonusPct, streakBonusPct, totalBonusPct);
    }

    private static String courierRankLabel(long completedOrders) {
        long safe = Math.max(0L, completedOrders);
        if (safe >= 250L) {
            return "S-Class Courier";
        }
        if (safe >= 120L) {
            return "Master Courier";
        }
        if (safe >= 60L) {
            return "Veteran Courier";
        }
        if (safe >= 25L) {
            return "Senior Courier";
        }
        if (safe >= 10L) {
            return "Courier II";
        }
        return "Courier I";
    }

    private static long nextCourierRankMilestone(long completedOrders) {
        long safe = Math.max(0L, completedOrders);
        long[] milestones = new long[]{10L, 25L, 60L, 120L, 250L};
        for (long milestone : milestones) {
            if (safe < milestone) {
                return milestone;
            }
        }
        return safe;
    }

    private static long nextCourierStreakMilestone(long streak) {
        long safe = Math.max(0L, streak);
        long tier = (safe / 3L) + 1L;
        return tier * 3L;
    }

    private static FoundOrder findOrderGlobal(CentralBank centralBank, UUID orderId) {
        if (centralBank == null || orderId == null) {
            return null;
        }
        for (CompoundTag shop : getAllShops(centralBank)) {
            CompoundTag order = findOrderById(shop, orderId);
            if (order != null) {
                return new FoundOrder(shop, order);
            }
        }
        return null;
    }

    private static FoundAssignedPallet findAssignedPalletShop(CentralBank centralBank,
                                                              String dimensionId,
                                                              BlockPos pos) {
        if (centralBank == null || pos == null) {
            return null;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        String dim = normalizedDim(dimensionId);
        ServerLevel level = server.getLevel(serverLevelKey(dim));
        if (level == null) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.PALLET.get())) {
            return null;
        }
        BlockPos master = PalletBlock.getMasterPos(state, pos);
        String target = resolvePalletAssignmentKey(level, master, false);
        if (target.isBlank()) {
            target = encodeOrderPalletRef(dim, master);
        }
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (!isInsideDeliveryPalletClaims(shop, dim, master)) {
                continue;
            }
            Set<String> assigned = collectAssignedPalletRefSet(shop);
            if (assigned.contains(target)) {
                return new FoundAssignedPallet(shop);
            }
        }
        return null;
    }

    private static BoxDeliverySummary summarizeCardboardBox(ItemStack placedBox) {
        if (placedBox == null || placedBox.isEmpty() || !placedBox.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
            return new BoxDeliverySummary(false, "", 0);
        }
        ItemStackHandler inv = readCardboardBoxInventory(placedBox);
        String itemId = "";
        int total = 0;
        boolean invalid = false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String id = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (itemId.isBlank()) {
                itemId = id;
            } else if (!itemId.equalsIgnoreCase(id)) {
                invalid = true;
                break;
            }
            total += Math.max(1, stack.getCount());
        }
        if (invalid || itemId.isBlank() || total <= 0) {
            return new BoxDeliverySummary(false, "", 0);
        }
        return new BoxDeliverySummary(true, itemId, total);
    }

    private static AccountHolder resolvePrimaryAccountForPlayer(CentralBank centralBank, UUID playerId) {
        if (centralBank == null || playerId == null) {
            return null;
        }
        List<AccountHolder> candidates = new ArrayList<>(centralBank.SearchForAccount(playerId).values());
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparing(AccountHolder::getAccountUUID));
        for (AccountHolder candidate : candidates) {
            if (candidate != null && candidate.isPrimaryAccount()) {
                return candidate;
            }
        }
        for (AccountHolder candidate : candidates) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    private static String shortUuid(UUID id) {
        if (id == null) {
            return "-";
        }
        String raw = id.toString();
        return raw.substring(0, Math.min(8, raw.length()));
    }

    public static ShopActionResult overview(CentralBank centralBank, UUID ownerId) {
        return overview(null, centralBank, ownerId, null);
    }

    public static ShopActionResult overview(CentralBank centralBank, UUID ownerId, UUID shopId) {
        return overview(null, centralBank, ownerId, shopId);
    }

    public static ShopActionResult overview(MinecraftServer server,
                                            CentralBank centralBank,
                                            UUID ownerId,
                                            UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        List<CompoundTag> shops;
        if (shopId == null) {
            shops = getOwnerShops(centralBank, ownerId);
        } else {
            CompoundTag selected = resolveShopTag(centralBank, ownerId, shopId);
            shops = selected == null ? List.of() : List.of(selected);
        }
        if (shops.isEmpty()) {
            return new ShopActionResult(false, "No shops found. Create one first.");
        }

        CompoundTag selectedShop = shops.get(0);
        long revenue = Math.max(0L, selectedShop.getLong(TAG_REVENUE_DOLLARS));
        long target = Math.max(1L, selectedShop.getLong(TAG_NEXT_TARGET_DOLLARS));
        int level = Math.max(1, selectedShop.getInt(TAG_LEVEL));
        ListTag claims = selectedShop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag stockroomClaims = selectedShop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        long used = computeVolume(claims);
        long cap = claimCapacityForLevel(level);
        long stockroomUsed = computeVolume(stockroomClaims);
        long stockroomCap = stockroomCapacityForLevel(level);
        int displayCap = maxDisplayBlocksForLevel(level);

        int shelves = 0;
        int configuredSlots = 0;
        int lowStockSlots = 0;
        int outOfStockSlots = 0;
        long stockUnits = 0L;
        boolean stockUnitsInfinite = false;
        long priceSumCents = 0L;
        int pricedSlots = 0;
        Map<String, Long> categoryValueDollars = new HashMap<>();

        if (server != null) {
            for (ShelfRef ref : collectShelvesForShop(server, selectedShop)) {
                ShelfDisplayBlockEntity shelf = ref.shelf();
                shelves++;
                for (int slot = 0; slot < Math.max(1, shelf.getSlotCount()); slot++) {
                    ItemStack item = shelf.getDisplayItem(slot);
                    long priceCents = shelf.getSlotPrice(slot);
                    if (item.isEmpty() || priceCents < 0L) {
                        continue;
                    }
                    configuredSlots++;
                    int stock = Math.max(0, shelf.getSlotStock(slot));
                    boolean infiniteStock = shelf.isCreativeShelf() || stock >= Integer.MAX_VALUE;
                    if (infiniteStock) {
                        stockUnitsInfinite = true;
                    } else {
                        stockUnits = safeAdd(stockUnits, stock);
                        if (stock <= 0) {
                            outOfStockSlots++;
                        } else if (stock <= 8) {
                            lowStockSlots++;
                        }
                    }
                    priceSumCents = safeAdd(priceSumCents, priceCents);
                    pricedSlots++;
                    long valueDollars = infiniteStock
                            ? 0L
                            : Math.max(0L, Math.round((priceCents * (long) stock) / 100.0D));
                    String category = classifyShelfItemCategory(item);
                    categoryValueDollars.put(category, safeAdd(categoryValueDollars.getOrDefault(category, 0L), valueDollars));
                }
            }
        }

        List<CashierSummary> cashiers = server == null
                ? List.of()
                : collectCashiers(server, centralBank, ownerId, selectedShop.contains(TAG_ID) ? selectedShop.getUUID(TAG_ID) : null);
        // Merge live entities with persisted terminal links so cashiers standing in
        // unloaded chunks still count instead of reading as lost configuration.
        Set<UUID> knownCashierIds = new HashSet<>();
        for (CashierSummary summary : cashiers) {
            if (summary.cashierId() != null) {
                knownCashierIds.add(summary.cashierId());
            }
        }
        Set<UUID> persistedLinkedIds = collectPersistedLinkedCashierIds(selectedShop);
        knownCashierIds.addAll(persistedLinkedIds);
        Set<UUID> linkedCashierIds = new HashSet<>(persistedLinkedIds);
        for (CashierSummary summary : cashiers) {
            if (summary.linkedTerminal() && summary.cashierId() != null) {
                linkedCashierIds.add(summary.cashierId());
            }
        }
        int cashierCount = knownCashierIds.size();
        int linkedCashiers = linkedCashierIds.size();
        int shoppingBags = 0;
        if (server != null && centralBank != null && ownerId != null && selectedShop.contains(TAG_ID)) {
            shoppingBags = countShoppingBagsInStockroom(server, centralBank, ownerId, selectedShop.getUUID(TAG_ID));
        }
        int currentMinute = resolveCurrentMinuteOfDay(server);
        String currentDayKey = resolveCurrentScheduleDayKey(server);
        ensureWeeklySchedule(selectedShop);
        boolean shopOpenNow = isShopSetupComplete(selectedShop) && isShopOpenAtMinute(selectedShop, currentMinute, currentDayKey);
        String shopHoursLabel = friendlyScheduleDay(currentDayKey)
                + " "
                + formatMinuteAmPm(getShopOpenMinuteForDay(selectedShop, currentDayKey))
                + " - "
                + formatMinuteAmPm(getShopCloseMinuteForDay(selectedShop, currentDayKey))
                + " [" + resolveServerZone().getId() + "]";
        UUID shopIdForSnapshot = selectedShop.contains(TAG_ID) ? selectedShop.getUUID(TAG_ID) : null;
        UUID settlementAccountId = shopIdForSnapshot == null
                ? null
                : resolveSettlementAccountId(centralBank, ownerId, shopIdForSnapshot, null);
        boolean settlementAccountReady = settlementAccountId != null
                && centralBank != null
                && centralBank.SearchForAccountByAccountId(settlementAccountId) != null;
        String settlementAccountLabel = settlementAccountId == null ? "-" : shortUuid(settlementAccountId);
        boolean checkoutTerminalBound = false;
        String checkoutTerminalLabel = "-";
        if (selectedShop.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            CompoundTag terminalTag = selectedShop.getCompound(TAG_CHECKOUT_TERMINAL);
            checkoutTerminalLabel = terminalPosLabel(terminalTag);
            if (server != null && selectedShop.contains(TAG_ID)) {
                CheckoutTerminalTarget terminalTarget = resolveCheckoutTerminal(
                        server,
                        centralBank,
                        ownerId,
                        selectedShop.getUUID(TAG_ID),
                        null
                );
                if (terminalTarget != null) {
                    checkoutTerminalBound = true;
                    checkoutTerminalLabel = normalizedDim(terminalTarget.dimensionId()) + " ("
                            + terminalTarget.pos().getX() + ", "
                            + terminalTarget.pos().getY() + ", "
                            + terminalTarget.pos().getZ() + ")";
                }
            }
        }
        Set<String> assignedPalletSet = collectAssignedPalletRefSet(selectedShop);
        int availablePallets = 0;
        if (server != null && !assignedPalletSet.isEmpty()) {
            Map<String, PalletRef> livePalletLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(selectedShop));
            for (String assignedKey : assignedPalletSet) {
                if (assignedKey != null && livePalletLookup.containsKey(assignedKey)) {
                    availablePallets++;
                }
            }
        }
        int assignedPalletMax = maxAssignedOrderPalletsForLevel(level);
        long cashTxCount = Math.max(0L, selectedShop.getLong(TAG_METRIC_CASH_TX_COUNT));
        long cashTotalCents = Math.max(0L, selectedShop.getLong(TAG_METRIC_CASH_TOTAL_CENTS));
        long terminalTxCount = Math.max(0L, selectedShop.getLong(TAG_METRIC_TERMINAL_TX_COUNT));
        long terminalTotalCents = Math.max(0L, selectedShop.getLong(TAG_METRIC_TERMINAL_TOTAL_CENTS));
        int cashCustomers = decodeUuidStringList(selectedShop.getList(TAG_METRIC_CASH_UNIQUE_PLAYERS, Tag.TAG_STRING)).size();
        int terminalCustomers = decodeUuidStringList(selectedShop.getList(TAG_METRIC_TERMINAL_UNIQUE_PLAYERS, Tag.TAG_STRING)).size();
        long vaultTotalCents = computeVaultTotalCents(readCashVaultCounts(selectedShop));

        double averagePriceDollars = pricedSlots <= 0 ? 0.0D : (priceSumCents / 100.0D) / pricedSlots;
        long estimatedTransactions = averagePriceDollars <= 0.0D ? 0L : Math.max(0L, Math.round(revenue / Math.max(0.01D, averagePriceDollars)));
        long estimatedFootTraffic = Math.max(estimatedTransactions, estimatedTransactions + (long) Math.max(0, cashierCount * 12));
        double conversionRate = estimatedFootTraffic <= 0L
                ? 0.0D
                : Math.max(0.0D, Math.min(100.0D, (estimatedTransactions * 100.0D) / estimatedFootTraffic));
        double aov = estimatedTransactions <= 0L ? 0.0D : revenue / (double) estimatedTransactions;

        double grossMarginPct = switch (normalizeShopType(selectedShop.getString(TAG_TYPE))) {
            case SHOP_TYPE_FRANCHISE -> 26.0D;
            case SHOP_TYPE_CORPORATE_CHAIN -> 22.0D;
            default -> 32.0D;
        };
        double grossProfit = revenue * (grossMarginPct / 100.0D);
        double operatingExpenseRatio = switch (normalizeShopType(selectedShop.getString(TAG_TYPE))) {
            case SHOP_TYPE_FRANCHISE -> 0.20D;
            case SHOP_TYPE_CORPORATE_CHAIN -> 0.18D;
            default -> 0.22D;
        };
        double operatingExpenses = revenue * operatingExpenseRatio;
        double netProfit = grossProfit - operatingExpenses;
        double inventoryTurnover = stockUnits <= 0L ? 0.0D : estimatedTransactions / (double) stockUnits;
        double stockToSalesRatio = estimatedTransactions <= 0L ? stockUnits : stockUnits / (double) Math.max(1L, estimatedTransactions);
        double salesPerLaborHour = cashierCount <= 0 ? revenue : revenue / Math.max(1.0D, cashierCount * 8.0D);
        double waitSeconds = cashierCount <= 0 ? 35.0D : Math.max(6.0D, 30.0D - (linkedCashiers * 2.2D));
        double serviceSeconds = cashierCount <= 0 ? 48.0D : Math.max(8.0D, 42.0D - (cashierCount * 1.5D));
        double csat = Math.max(50.0D, Math.min(99.0D, 70.0D + (conversionRate * 0.2D) + (linkedCashiers * 1.0D)));
        double nps = Math.max(-100.0D, Math.min(100.0D, (csat - 70.0D) * 2.0D));

        List<Long> trend = buildSevenDayTrend(selectedShop, revenue);
        long avgPerDay = 0L;
        if (!trend.isEmpty()) {
            long sum = 0L;
            for (Long val : trend) {
                sum = safeAdd(sum, Math.max(0L, val == null ? 0L : val));
            }
            avgPerDay = Math.max(0L, Math.round(sum / (double) trend.size()));
        }
        long sixMonthForecast = Math.max(0L, avgPerDay * 30L * 6L);

        List<CompoundTag> ownerShopList = getOwnerShops(centralBank, ownerId);
        long allShopsRevenue = 0L;
        String bestShopName = selectedShop.getString(TAG_NAME);
        long bestRevenue = -1L;
        for (CompoundTag shop : ownerShopList) {
            long shopRevenue = Math.max(0L, shop.getLong(TAG_REVENUE_DOLLARS));
            allShopsRevenue = safeAdd(allShopsRevenue, shopRevenue);
            if (shopRevenue > bestRevenue) {
                bestRevenue = shopRevenue;
                bestShopName = shop.getString(TAG_NAME);
            }
        }

        String rag;
        if (outOfStockSlots > Math.max(2, configuredSlots / 3) || conversionRate < 20.0D) {
            rag = "RED";
        } else if (lowStockSlots > Math.max(2, configuredSlots / 4) || conversionRate < 40.0D) {
            rag = "AMBER";
        } else {
            rag = "GREEN";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Shop Dashboard");
        lines.add("@shop.name=" + selectedShop.getString(TAG_NAME));
        lines.add("@shop.type=" + normalizeShopType(selectedShop.getString(TAG_TYPE)));
        lines.add("@status.rag=" + rag);
        lines.add("@kpi.level=" + level);
        lines.add("@kpi.revenue_dollars=" + revenue);
        lines.add("@kpi.target_dollars=" + target);
        lines.add("@kpi.next_level_target_dollars=" + Math.max(1L, targetForLevel(level + 1)));
        lines.add("@kpi.claim_used_blocks=" + used);
        lines.add("@kpi.claim_capacity_blocks=" + cap);
        lines.add("@kpi.claim_regions=" + claims.size());
        lines.add("@kpi.stockroom_used_blocks=" + stockroomUsed);
        lines.add("@kpi.stockroom_capacity_blocks=" + stockroomCap);
        lines.add("@kpi.stockroom_regions=" + stockroomClaims.size());
        lines.add("@kpi.shelves=" + shelves);
        lines.add("@kpi.display_capacity=" + displayCap);
        lines.add("@kpi.configured_slots=" + configuredSlots);
        lines.add("@kpi.low_stock_slots=" + lowStockSlots);
        lines.add("@kpi.out_of_stock_slots=" + outOfStockSlots);
        lines.add("@kpi.stock_units=" + stockUnits);
        lines.add("@kpi.stock_units_infinite=" + (stockUnitsInfinite ? 1 : 0));
        lines.add("@kpi.shopping_bags=" + Math.max(0, shoppingBags));
        lines.add("@kpi.shop_open=" + (shopOpenNow ? 1 : 0));
        lines.add("@kpi.shop_hours_label=" + sanitizeTokenText(shopHoursLabel));
        lines.add("@kpi.settlement_account_ready=" + (settlementAccountReady ? 1 : 0));
        lines.add("@kpi.settlement_account_label=" + sanitizeTokenText(settlementAccountLabel));
        lines.add("@kpi.cashiers=" + cashierCount);
        lines.add("@kpi.linked_cashiers=" + linkedCashiers);
        lines.add("@kpi.checkout_terminal_bound=" + (checkoutTerminalBound ? 1 : 0));
        lines.add("@kpi.checkout_terminal_label=" + sanitizeTokenText(checkoutTerminalLabel));
        lines.add("@kpi.assigned_pallets=" + assignedPalletSet.size());
        lines.add("@kpi.available_pallets=" + availablePallets);
        lines.add("@kpi.assigned_pallets_max=" + assignedPalletMax);
        lines.add("@kpi.cash_tx_count=" + cashTxCount);
        lines.add("@kpi.terminal_tx_count=" + terminalTxCount);
        lines.add("@kpi.cash_customers=" + cashCustomers);
        lines.add("@kpi.terminal_customers=" + terminalCustomers);
        lines.add("@kpi.cash_total_cents=" + cashTotalCents);
        lines.add("@kpi.terminal_total_cents=" + terminalTotalCents);
        lines.add("@kpi.vault_total_cents=" + vaultTotalCents);
        lines.add("@kpi.aov=" + String.format(Locale.ROOT, "%.2f", aov));
        lines.add("@kpi.conversion_rate=" + String.format(Locale.ROOT, "%.2f", conversionRate));
        lines.add("@kpi.gross_margin_pct=" + String.format(Locale.ROOT, "%.2f", grossMarginPct));
        lines.add("@kpi.operating_expenses=" + String.format(Locale.ROOT, "%.2f", operatingExpenses));
        lines.add("@kpi.net_profit=" + String.format(Locale.ROOT, "%.2f", netProfit));
        lines.add("@kpi.inventory_turnover=" + String.format(Locale.ROOT, "%.3f", inventoryTurnover));
        lines.add("@kpi.stock_to_sales_ratio=" + String.format(Locale.ROOT, "%.3f", stockToSalesRatio));
        lines.add("@kpi.sales_per_labor_hour=" + String.format(Locale.ROOT, "%.2f", salesPerLaborHour));
        lines.add("@kpi.foot_traffic=" + estimatedFootTraffic);
        lines.add("@kpi.wait_seconds=" + String.format(Locale.ROOT, "%.2f", waitSeconds));
        lines.add("@kpi.service_seconds=" + String.format(Locale.ROOT, "%.2f", serviceSeconds));
        lines.add("@kpi.csat=" + String.format(Locale.ROOT, "%.2f", csat));
        lines.add("@kpi.nps=" + String.format(Locale.ROOT, "%.2f", nps));
        lines.add("@kpi.cashflow_6m=" + sixMonthForecast);
        lines.add("@kpi.all_shops_revenue_dollars=" + allShopsRevenue);
        lines.add("@kpi.all_shops_count=" + ownerShopList.size());
        lines.add("@kpi.best_shop_name=" + (bestShopName == null || bestShopName.isBlank() ? "-" : bestShopName));

        StringBuilder trendLine = new StringBuilder("@trend.daily=");
        for (int i = 0; i < trend.size(); i++) {
            if (i > 0) {
                trendLine.append(',');
            }
            trendLine.append(Math.max(0L, trend.get(i) == null ? 0L : trend.get(i)));
        }
        lines.add(trendLine.toString());

        lines.add("@category.sales=" + formatCategoryMetrics(categoryValueDollars));

        lines.add("- " + selectedShop.getString(TAG_NAME)
                + " | Type " + prettyShopType(selectedShop.getString(TAG_TYPE))
                + " | Level " + level
                + " | Revenue $" + ShelfPrice.abbreviateFromCents(revenue * 100L)
                + " / $" + ShelfPrice.abbreviateFromCents(target * 100L));
        lines.add("- Shelves: " + shelves + " | Configured Slots: " + configuredSlots
                + " | Low Stock: " + lowStockSlots + " | Out of Stock: " + outOfStockSlots);
        lines.add("- Cashiers: " + cashierCount + " (linked " + linkedCashiers + ")"
                + " | CSAT " + String.format(Locale.ROOT, "%.1f%%", csat)
                + " | NPS " + String.format(Locale.ROOT, "%.0f", nps));
        lines.add("- Cash payments: " + cashTxCount + " tx / " + cashCustomers + " customers"
                + " / $" + ShelfPrice.abbreviateFromCents(cashTotalCents));
        lines.add("- Terminal payments: " + terminalTxCount + " tx / " + terminalCustomers + " customers"
                + " / $" + ShelfPrice.abbreviateFromCents(terminalTotalCents)
                + " | Vault $" + ShelfPrice.abbreviateFromCents(vaultTotalCents));
        lines.add("- Net Profit: $" + ShelfPrice.abbreviateFromCents(Math.round(Math.max(0.0D, netProfit) * 100.0D))
                + " | 6M Cash Flow Forecast: $" + ShelfPrice.abbreviateFromCents(sixMonthForecast * 100L));

        return new ShopActionResult(true, String.join("\n", lines));
    }

    /**
     * Builds a dense, tokenized roadmap payload consumed by the Shop dashboard timeline UI.
     * The client expects one node per level so it can render a horizontal milestone graph.
     */
    public static ShopActionResult levelRoadmapReport(CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag selectedShop = resolveShopTag(centralBank, ownerId, shopId);
        if (selectedShop == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }

        int currentLevel = Math.max(1, Math.min(100, selectedShop.getInt(TAG_LEVEL)));
        long revenue = Math.max(0L, selectedShop.getLong(TAG_REVENUE_DOLLARS));
        long nextTarget = Math.max(1L, selectedShop.getLong(TAG_NEXT_TARGET_DOLLARS));
        long currentFloor = requiredRevenueForLevel(currentLevel);
        if (nextTarget <= currentFloor) {
            nextTarget = Math.max(currentFloor + 1L, requiredRevenueForLevel(currentLevel + 1));
        }
        long requiredForNext = requiredRevenueForLevel(Math.min(100, currentLevel + 1));
        if (requiredForNext > currentFloor) {
            nextTarget = Math.max(nextTarget, requiredForNext);
        }
        double progressRatio = currentLevel >= 100
                ? 1.0D
                : (revenue - currentFloor) / (double) Math.max(1L, nextTarget - currentFloor);
        progressRatio = Math.max(0.0D, Math.min(1.0D, progressRatio));

        List<String> lines = new ArrayList<>();
        lines.add("Shop Level Roadmap");
        lines.add("@roadmap.enabled=1");
        lines.add("@roadmap.shop_name=" + selectedShop.getString(TAG_NAME));
        lines.add("@roadmap.shop_type=" + normalizeShopType(selectedShop.getString(TAG_TYPE)));
        lines.add("@roadmap.current_level=" + currentLevel);
        lines.add("@roadmap.current_revenue_dollars=" + revenue);
        lines.add("@roadmap.current_level_floor_dollars=" + currentFloor);
        lines.add("@roadmap.next_level_target_dollars=" + nextTarget);
        lines.add("@roadmap.progress_ratio=" + String.format(Locale.ROOT, "%.6f", progressRatio));
        lines.add("@roadmap.max_level=100");
        lines.add("@roadmap.leveling_enabled=" + (Config.SHOP_LEVELING_ENABLED.get() ? "1" : "0"));
        String shopType = normalizeShopType(selectedShop.getString(TAG_TYPE));
        for (int level = 1; level <= 100; level++) {
            long requiredRevenue = requiredRevenueForLevel(level);
            long claimCap = claimCapacityForLevel(level);
            long stockroomCap = stockroomCapacityForLevel(level);
            int displayCap = maxDisplayBlocksForLevel(level);
            int cashierCap = maxCashierSpawnEggsForLevel(level);
            int palletCap = maxAssignedOrderPalletsForLevel(level);
            List<String> businessUnlocks = businessTypeUnlocksForRoadmapLevel(shopType, level);
            String state = level < currentLevel
                    ? "COMPLETED"
                    : (level == currentLevel ? "CURRENT" : "LOCKED");
            lines.add("@roadmap.node="
                    + level
                    + "|" + requiredRevenue
                    + "|" + claimCap
                    + "|" + stockroomCap
                    + "|" + displayCap
                    + "|" + cashierCap
                    + "|" + palletCap
                    + "|" + state
                    + "|" + encodeRoadmapUnlocks(businessUnlocks));
        }
        lines.add("- Current level: " + currentLevel
                + " | Revenue $" + ShelfPrice.abbreviateFromCents(revenue * 100L)
                + " | Next target $" + ShelfPrice.abbreviateFromCents(nextTarget * 100L));
        lines.add("- Use the roadmap panel to inspect unlocks for each level milestone.");
        return new ShopActionResult(true, String.join("\n", lines));
    }

    private static List<String> businessTypeUnlocksForRoadmapLevel(String rawType, int level) {
        int safeLevel = Math.max(1, Math.min(100, level));
        String type = normalizeShopType(rawType);
        List<String> unlocks = new ArrayList<>();
        if (SHOP_TYPE_FRANCHISE.equals(type)) {
            int unlockLevel = Math.max(1, Config.SHOP_FRANCHISE_BRAND_OWNER_UNLOCK_LEVEL.get());
            int capacity = franchiseLicenseCapacityForLevel(safeLevel);
            int previousCapacity = safeLevel <= 1 ? 0 : franchiseLicenseCapacityForLevel(safeLevel - 1);
            if (safeLevel == unlockLevel) {
                unlocks.add("Franchise brand offers unlock");
            }
            if (capacity > previousCapacity) {
                unlocks.add("Franchise license capacity " + capacity);
            }
            if (safeLevel > unlockLevel
                    && ((safeLevel - unlockLevel) % 10) == 0
                    && capacity >= previousCapacity
                    && Config.SHOP_FRANCHISE_LICENSE_CAPACITY_PER_10_LEVELS.get() <= 0) {
                unlocks.add("Franchise milestone: brand reach");
            }
        } else if (SHOP_TYPE_CORPORATE_CHAIN.equals(type)) {
            int capacity = corporateBranchCapacityForLevel(safeLevel);
            int previousCapacity = safeLevel <= 1 ? 1 : corporateBranchCapacityForLevel(safeLevel - 1);
            int firstExtra = Math.max(1, Config.SHOP_CORPORATE_FIRST_EXTRA_BRANCH_LEVEL.get());
            if (safeLevel == firstExtra && capacity > 1) {
                unlocks.add("First extra corporate branch unlocks");
            }
            if (capacity > previousCapacity) {
                unlocks.add("Corporate branch capacity " + capacity);
            }
        }
        return unlocks;
    }

    private static String encodeRoadmapUnlocks(List<String> unlocks) {
        if (unlocks == null || unlocks.isEmpty()) {
            return "-";
        }
        List<String> encoded = new ArrayList<>();
        for (String unlock : unlocks) {
            String clean = sanitizeTokenText(unlock).replace(";", ",");
            if (!clean.isBlank() && !"-".equals(clean)) {
                encoded.add(clean);
            }
        }
        return encoded.isEmpty() ? "-" : String.join(";", encoded);
    }

    public static ShopActionResult shelfReport(MinecraftServer server, CentralBank centralBank, UUID ownerId) {
        return shelfReport(server, centralBank, ownerId, null);
    }

    public static ShopActionResult shelfReport(MinecraftServer server, CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        boolean hadLegacyStockroom = primary.contains(TAG_STOCKROOM, Tag.TAG_COMPOUND);
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (hadLegacyStockroom && !stockroomClaims.isEmpty()) {
            saveShopTag(centralBank, primary);
        }
        List<InventoryAccess> stockroomInventories = stockroomClaims.isEmpty()
                ? List.of()
                : collectInventoriesInClaims(server, stockroomClaims);
        Map<String, Integer> stockroomPreviewCache = new HashMap<>();
        long nowMillis = System.currentTimeMillis();

        List<String> lines = new ArrayList<>();
        lines.add("@inventory.source=LIVE_SHELF_BLOCK_ENTITIES");
        lines.add("@inventory.generated=0");
        lines.add("@shelves.count=" + shelves.size());
        lines.add("Shelves for " + primary.getString(TAG_NAME) + " (" + shelves.size() + ")");
        for (int i = 0; i < shelves.size(); i++) {
            ShelfRef ref = shelves.get(i);
            ShelfDisplayBlockEntity shelf = ref.shelf();
            boolean creative = shelf.isCreativeShelf();
            int shelfIndex = i + 1;
            lines.add("- " + ref.dimensionId() + " (" + ref.pos().getX() + "," + ref.pos().getY() + "," + ref.pos().getZ() + ")"
                    + " | " + (creative ? "Creative Shelf" : "Shop Shelf"));
            int configured = 0;
            int totalStock = 0;
            int lowStock = 0;
            int outOfStock = 0;
            List<String> slotLines = new ArrayList<>();
            for (int slot = 0; slot < Math.max(1, shelf.getSlotCount()); slot++) {
                ItemStack stack = shelf.getDisplayItem(slot);
                long price = shelf.getSlotPrice(slot);
                if (stack.isEmpty() || price < 0L) {
                    continue;
                }
                configured++;
                int stockCount = Math.max(0, shelf.getSlotStock(slot));
                String encodedTarget = encodeShelfSlotTarget(ref.dimensionId(), ref.pos(), slot);
                SlotStockTargets targets = getSlotStockTargets(primary, encodedTarget);
                int minTarget = targets.minStockTarget();
                int maxTarget = targets.maxStockTarget();
                long lastSoldMillis = getSlotLastSoldMillis(primary, encodedTarget);
                double velocityPerDay = getSlotVelocityPerDay(primary, encodedTarget, nowMillis);
                if (!creative) {
                    totalStock += stockCount;
                    if (stockCount <= 0) {
                        outOfStock++;
                    } else if (stockCount <= minTarget) {
                        lowStock++;
                    }
                }
                String stock = creative ? "INF" : String.valueOf(stockCount);
                String itemName = stack.getHoverName().getString();
                int stockroomAvailable = -1;
                if (!creative && !stockroomInventories.isEmpty()) {
                    String previewKey = buildStockPreviewCacheKey(stack);
                    stockroomAvailable = stockroomPreviewCache.computeIfAbsent(
                            previewKey,
                            ignored -> countMatchingInInventories(stockroomInventories, stack)
                    );
                }
                slotLines.add("  " + (slot + 1) + ") " + itemName
                        + " | " + (price == 0L ? "Free" : "$" + ShelfPrice.abbreviateFromCents(price))
                        + " | stock " + stock
                        + (creative ? "" : " | target " + minTarget + "-" + maxTarget)
                        + (creative || stockroomAvailable < 0 ? "" : " | stockroom " + stockroomAvailable));
                String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                int tokenStock = creative ? -1 : stockCount;
                int restockable = !creative && stockCount < maxTarget ? 1 : 0;
                lines.add("@shelf_item=" + shelfIndex
                        + "|" + (slot + 1)
                        + "|" + itemId
                        + "|" + price
                        + "|" + tokenStock
                        + "|" + restockable
                        + "|" + encodedTarget
                        + "|" + sanitizeTokenText(itemName)
                        + "|" + minTarget
                        + "|" + maxTarget
                        + "|" + Math.max(-1, stockroomAvailable)
                        + "|" + Math.max(0L, lastSoldMillis)
                        + "|" + String.format(Locale.ROOT, "%.3f", Math.max(0.0D, velocityPerDay)));
            }
            lines.add("@shelf_card=" + shelfIndex
                    + "|" + normalizedDim(ref.dimensionId())
                    + "|" + ref.pos().getX()
                    + "|" + ref.pos().getY()
                    + "|" + ref.pos().getZ()
                    + "|" + (creative ? "CREATIVE" : "SHOP")
                    + "|" + configured
                    + "|" + (creative ? -1 : totalStock)
                    + "|" + lowStock
                    + "|" + outOfStock
                    + "|" + encodeShelfTarget(ref.dimensionId(), ref.pos()));
            if (configured == 0) {
                lines.add("  (no configured slots)");
                continue;
            }
            lines.addAll(slotLines);
        }
        List<StockroomItemEntry> stockroomEntries = stockroomClaims.isEmpty()
                ? List.of()
                : collectStockroomEntries(server, stockroomClaims);
        appendStockroomEntryTokens(lines, stockroomEntries);
        return new ShopActionResult(true, String.join("\n", lines));
    }

    public static ShopActionResult stockroomReport(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   UUID ownerId,
                                                   UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        boolean hadLegacyStockroom = primary.contains(TAG_STOCKROOM, Tag.TAG_COMPOUND);
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (hadLegacyStockroom && !stockroomClaims.isEmpty()) {
            saveShopTag(centralBank, primary);
        }
        if (stockroomClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim stockroom region(s) first.");
        }

        List<StockroomItemEntry> entries = collectStockroomEntries(server, stockroomClaims);
        List<String> lines = new ArrayList<>();
        lines.add("Stockroom entries for " + primary.getString(TAG_NAME) + ": " + entries.size());
        appendStockroomEntryTokens(lines, entries);
        if (entries.isEmpty()) {
            lines.add("(No items found in stockroom inventories.)");
            return new ShopActionResult(true, String.join("\n", lines));
        }

        return new ShopActionResult(true, String.join("\n", lines));
    }

    private static void appendStockroomEntryTokens(List<String> lines, List<StockroomItemEntry> entries) {
        if (lines == null) {
            return;
        }
        List<StockroomItemEntry> safeEntries = entries == null ? List.of() : entries;
        lines.add("@stockroom.count=" + safeEntries.size());
        int idx = 1;
        for (StockroomItemEntry entry : safeEntries) {
            if (entry == null) {
                continue;
            }
            lines.add("@stockroom_item=" + idx
                    + "|" + entry.itemId()
                    + "|" + sanitizeTokenText(entry.itemName())
                    + "|" + Math.max(1, entry.count())
                    + "|" + sanitizeTokenText(entry.inventoryType())
                    + "|" + normalizedDim(entry.dimensionId())
                    + "|" + entry.pos().getX()
                    + "|" + entry.pos().getY()
                    + "|" + entry.pos().getZ()
                    + "|" + Math.max(1, entry.slot())
                    + "|" + Math.max(1, entry.totalSlots())
                    + "|" + entry.locateTarget());
            lines.add(idx + ") " + entry.itemName()
                    + " x" + entry.count()
                    + " | " + entry.inventoryType()
                    + " | " + normalizedDim(entry.dimensionId()) + " (" + entry.pos().getX() + "," + entry.pos().getY() + "," + entry.pos().getZ() + ")"
                    + " | slot " + entry.slot() + "/" + entry.totalSlots());
            idx++;
        }
    }

    public static ShopActionResult beginStockroomLocate(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        ServerPlayer player,
                                                        String rawTarget) {
        if (server == null || centralBank == null || ownerId == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (stockroomClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim stockroom region(s) first.");
        }

        String normalizedRawTarget = rawTarget == null ? "" : rawTarget.trim();
        StockroomLocateTarget target = parseStockroomLocateTarget(rawTarget);
        if (target == null) {
            return new ShopActionResult(false, "Invalid stockroom locate target.");
        }
        if (!isInsideClaims(stockroomClaims, target.dimensionId(), target.pos())) {
            return new ShopActionResult(false, "Target is outside your stockroom claims.");
        }

        List<StockroomItemEntry> entries = collectStockroomEntries(server, stockroomClaims);
        StockroomItemEntry matched = null;
        for (StockroomItemEntry entry : entries) {
            if (normalizedRawTarget.equalsIgnoreCase(entry.locateTarget())) {
                matched = entry;
                break;
            }
        }
        for (StockroomItemEntry entry : entries) {
            if (matched != null) {
                break;
            }
            if (!normalizedDim(entry.dimensionId()).equals(target.dimensionId())) {
                continue;
            }
            if (!entry.pos().equals(target.pos())) {
                continue;
            }
            if (entry.slot() != target.slot()) {
                continue;
            }
            matched = entry;
            break;
        }
        if (matched == null) {
            return new ShopActionResult(false, "That stockroom entry no longer exists. Refresh stockroom and retry.");
        }

        long nowTick = player.serverLevel().getGameTime();
        StockroomLocateSession session = new StockroomLocateSession(
                player.getUUID(),
                ownerId,
                primary.contains(TAG_ID) ? primary.getUUID(TAG_ID) : shopId,
                normalizedDim(matched.dimensionId()),
                matched.pos(),
                Math.max(1, target.slot()),
                sanitizeTokenText(matched.inventoryType()),
                nowTick
        );
        STOCKROOM_LOCATE_SESSIONS.put(player.getUUID(), session);
        pushStockroomLocateRender(player, session);
        player.sendSystemMessage(UbsTranslations.literal("Stockroom locate started for ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(matched.itemName()).withStyle(ChatFormatting.WHITE))
                .append(UbsTranslations.literal(". Sneak + right click to cancel.").withStyle(ChatFormatting.GREEN)));
        pushShopAlert(
                player,
                "Stockroom Locate",
                "Stockroom locate started for " + matched.itemName() + ". Sneak + right click to cancel.",
                DeliveryAlertPayload.AlertTone.INFO,
                5200
        );
        return new ShopActionResult(true,
                "Locating " + matched.itemName() + " at "
                        + normalizedDim(matched.dimensionId()) + " ("
                        + matched.pos().getX() + ", " + matched.pos().getY() + ", " + matched.pos().getZ()
                + "), slot " + matched.slot() + "/" + matched.totalSlots() + ".");
    }

    public static ShopActionResult beginDeliveryPalletLocate(MinecraftServer server,
                                                             CentralBank centralBank,
                                                             UUID ownerId,
                                                             UUID shopId,
                                                             ServerPlayer player,
                                                             String rawPalletRef) {
        if (server == null || centralBank == null || ownerId == null || player == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ListTag palletClaims = deliveryPalletSearchClaims(primary);
        if (palletClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim delivery pallet region(s) first.");
        }

        Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, palletClaims);
        if (liveLookup.isEmpty()) {
            return new ShopActionResult(false, "No live delivery pallets found in your pallet claim.");
        }

        String requested = normalizeOrderPalletRef(rawPalletRef);
        PalletRef target = requested.isBlank() ? null : liveLookup.get(requested);
        if (target == null && requested.isBlank()) {
            Set<String> assigned = collectAssignedPalletRefSet(primary);
            for (String assignedKey : assigned) {
                if (assignedKey == null || assignedKey.isBlank()) {
                    continue;
                }
                target = liveLookup.get(assignedKey.trim());
                if (target != null) {
                    requested = assignedKey.trim();
                    break;
                }
            }
            if (target == null) {
                Map.Entry<String, PalletRef> first = liveLookup.entrySet().iterator().next();
                requested = first.getKey();
                target = first.getValue();
            }
        }
        if (target == null && !requested.isBlank()) {
            PalletRef decoded = decodeOrderPalletRef(requested);
            PalletRef live = resolveLivePalletRef(server, decoded);
            if (live != null && isInsideClaims(palletClaims, live.dimensionId(), live.pos())) {
                target = live;
            }
        }
        if (target == null) {
            return new ShopActionResult(false, "Selected pallet is no longer inside this shop's pallet claim.");
        }

        String encoded = encodeOrderPalletRef(target.dimensionId(), target.pos());
        long nowTick = player.serverLevel().getGameTime();
        StockroomLocateSession session = new StockroomLocateSession(
                player.getUUID(),
                ownerId,
                primary.contains(TAG_ID) ? primary.getUUID(TAG_ID) : shopId,
                normalizedDim(target.dimensionId()),
                target.pos(),
                encodePalletLocateSlot(4, 0),
                "Delivery Pallet",
                nowTick
        );
        STOCKROOM_LOCATE_SESSIONS.put(player.getUUID(), session);
        pushStockroomLocateRender(player, session);
        player.sendSystemMessage(UbsTranslations.literal("Delivery pallet trace started for ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(formatPalletRef(encoded)).withStyle(ChatFormatting.WHITE))
                .append(UbsTranslations.literal(". Sneak + right click to cancel.").withStyle(ChatFormatting.GREEN)));
        pushShopAlert(
                player,
                "Pallet Trace",
                "Delivery pallet trace started for " + formatPalletRef(encoded) + ". Sneak + right click to cancel.",
                DeliveryAlertPayload.AlertTone.INFO,
                5200
        );
        return new ShopActionResult(true,
                "Tracing delivery pallet " + formatPalletRef(encoded) + ".");
    }

    public static ShopActionResult restockFromStockroom(MinecraftServer server, CentralBank centralBank, UUID ownerId) {
        return restockFromStockroom(server, centralBank, ownerId, null);
    }

    public static ShopActionResult restockFromStockroom(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ListTag stockroomClaims = primary.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        if (stockroomClaims.isEmpty()) {
            if (primary.contains(TAG_STOCKROOM, Tag.TAG_COMPOUND)) {
                CompoundTag legacy = primary.getCompound(TAG_STOCKROOM);
                String dim = normalizedDim(legacy.getString(TAG_DIM));
                int x = legacy.getInt(TAG_X);
                int y = legacy.getInt(TAG_Y);
                int z = legacy.getInt(TAG_Z);
                stockroomClaims.add(buildRegionTag(dim, x, y, z, x, y, z));
                primary.remove(TAG_STOCKROOM);
                primary.put(TAG_STOCKROOM_CLAIMS, stockroomClaims);
                saveShopTag(centralBank, primary);
            } else {
                return new ShopActionResult(false, "Claim stockroom region(s) first.");
            }
        }

        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
        if (inventories.isEmpty()) {
            return new ShopActionResult(false, "No inventories found inside stockroom claims.");
        }

        int totalMoved = 0;
        int touchedSlots = 0;
        for (ShelfRef ref : shelves) {
            ShelfDisplayBlockEntity shelf = ref.shelf();
            if (shelf.isCreativeShelf()) {
                continue;
            }
            for (int slot = 0; slot < Math.max(1, shelf.getSlotCount()); slot++) {
                ItemStack template = shelf.getDisplayItem(slot);
                if (template.isEmpty() || shelf.getSlotPrice(slot) < 0L) {
                    continue;
                }
                int current = shelf.getSlotStock(slot);
                String encodedTarget = encodeShelfSlotTarget(ref.dimensionId(), ref.pos(), slot);
                SlotStockTargets targets = getSlotStockTargets(primary, encodedTarget);
                int maxTarget = targets.maxStockTarget();
                if (current >= maxTarget) {
                    continue;
                }
                int needed = maxTarget - current;
                int moved = pullMatchingFromInventories(inventories, template, needed);
                if (moved <= 0) {
                    continue;
                }
                shelf.addStock(slot, moved);
                totalMoved += moved;
                touchedSlots++;
            }
        }

        if (totalMoved <= 0) {
            return new ShopActionResult(false, "No matching stock found in stockroom.");
        }
        return new ShopActionResult(true, "Restocked " + totalMoved + " item(s) across " + touchedSlots + " shelf slot(s).");
    }

    public static ShopActionResult restockShelfSlot(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID ownerId,
                                                    UUID shopId,
                                                    String encodedTarget) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        boolean hadLegacyStockroom = primary.contains(TAG_STOCKROOM, Tag.TAG_COMPOUND);
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (hadLegacyStockroom && !stockroomClaims.isEmpty()) {
            saveShopTag(centralBank, primary);
        }
        if (stockroomClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim stockroom region(s) first.");
        }
        ShelfSlotTarget target = parseShelfSlotTarget(encodedTarget);
        if (target == null) {
            return new ShopActionResult(false, "Invalid shelf slot target.");
        }

        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        ShelfRef selected = null;
        for (ShelfRef ref : shelves) {
            if (!normalizedDim(ref.dimensionId()).equals(target.dimensionId())) {
                continue;
            }
            if (!ref.pos().equals(target.pos())) {
                continue;
            }
            selected = ref;
            break;
        }
        if (selected == null) {
            return new ShopActionResult(false, "That shelf no longer exists in your claimed plots.");
        }

        ShelfDisplayBlockEntity shelf = selected.shelf();
        if (shelf.isCreativeShelf()) {
            return new ShopActionResult(false, "Creative shelf slots do not need restocking.");
        }
        int slot = target.slot();
        if (slot < 0 || slot >= Math.max(1, shelf.getSlotCount())) {
            return new ShopActionResult(false, "Invalid shelf slot.");
        }
        ItemStack template = shelf.getDisplayItem(slot);
        long price = shelf.getSlotPrice(slot);
        if (template.isEmpty() || price < 0L) {
            return new ShopActionResult(false, "This shelf slot is not configured.");
        }

        int current = Math.max(0, shelf.getSlotStock(slot));
        SlotStockTargets targets = getSlotStockTargets(primary, encodedTarget);
        int maxTarget = targets.maxStockTarget();
        if (current >= maxTarget) {
            return new ShopActionResult(false, "Slot is already fully stocked (" + current + "/" + maxTarget + ").");
        }

        List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
        if (inventories.isEmpty()) {
            return new ShopActionResult(false, "No inventories found inside stockroom claims.");
        }

        int needed = maxTarget - current;
        int moved = pullMatchingFromInventories(inventories, template, needed);
        if (moved <= 0) {
            return new ShopActionResult(false, "No matching stock found in stockroom for " + template.getHoverName().getString() + ".");
        }
        shelf.addStock(slot, moved);
        int after = Math.max(0, shelf.getSlotStock(slot));
        return new ShopActionResult(true,
                "Restocked " + template.getHoverName().getString()
                        + " by " + moved + " unit(s) (" + after + "/" + maxTarget + ").");
    }

    public static ShopActionResult restockLowStockSlots(MinecraftServer server,
                                                        CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (stockroomClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim stockroom region(s) first.");
        }
        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
        if (inventories.isEmpty()) {
            return new ShopActionResult(false, "No inventories found inside stockroom claims.");
        }

        int touchedSlots = 0;
        int movedTotal = 0;
        for (ShelfRef ref : shelves) {
            ShelfDisplayBlockEntity shelf = ref.shelf();
            if (shelf.isCreativeShelf()) {
                continue;
            }
            for (int slot = 0; slot < Math.max(1, shelf.getSlotCount()); slot++) {
                ItemStack template = shelf.getDisplayItem(slot);
                long price = shelf.getSlotPrice(slot);
                if (template.isEmpty() || price < 0L) {
                    continue;
                }
                int current = Math.max(0, shelf.getSlotStock(slot));
                String slotKey = encodeShelfSlotTarget(ref.dimensionId(), ref.pos(), slot);
                SlotStockTargets targets = getSlotStockTargets(primary, slotKey);
                if (current > targets.minStockTarget() || current >= targets.maxStockTarget()) {
                    continue;
                }
                int needed = targets.maxStockTarget() - current;
                if (needed <= 0) {
                    continue;
                }
                int moved = pullMatchingFromInventories(inventories, template, needed);
                if (moved <= 0) {
                    continue;
                }
                shelf.addStock(slot, moved);
                movedTotal += moved;
                touchedSlots++;
            }
        }
        if (movedTotal <= 0) {
            return new ShopActionResult(false, "No low-stock slots could be restocked from stockroom.");
        }
        return new ShopActionResult(true, "Restocked " + movedTotal + " item(s) across " + touchedSlots + " low-stock slot(s).");
    }

    public static ShopActionResult restockShelf(MinecraftServer server,
                                                CentralBank centralBank,
                                                UUID ownerId,
                                                UUID shopId,
                                                String encodedShelfTarget) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
        if (stockroomClaims.isEmpty()) {
            return new ShopActionResult(false, "Claim stockroom region(s) first.");
        }
        ShelfTarget shelfTarget = parseShelfTarget(encodedShelfTarget);
        if (shelfTarget == null) {
            return new ShopActionResult(false, "Invalid shelf target.");
        }
        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        ShelfRef selected = null;
        for (ShelfRef ref : shelves) {
            if (!normalizedDim(ref.dimensionId()).equals(shelfTarget.dimensionId())) {
                continue;
            }
            if (!ref.pos().equals(shelfTarget.pos())) {
                continue;
            }
            selected = ref;
            break;
        }
        if (selected == null) {
            return new ShopActionResult(false, "That shelf no longer exists in your claimed plots.");
        }
        if (selected.shelf().isCreativeShelf()) {
            return new ShopActionResult(false, "Creative shelves do not need restocking.");
        }
        List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
        if (inventories.isEmpty()) {
            return new ShopActionResult(false, "No inventories found inside stockroom claims.");
        }

        int movedTotal = 0;
        int touchedSlots = 0;
        ShelfDisplayBlockEntity shelf = selected.shelf();
        for (int slot = 0; slot < Math.max(1, shelf.getSlotCount()); slot++) {
            ItemStack template = shelf.getDisplayItem(slot);
            long price = shelf.getSlotPrice(slot);
            if (template.isEmpty() || price < 0L) {
                continue;
            }
            int current = Math.max(0, shelf.getSlotStock(slot));
            String slotKey = encodeShelfSlotTarget(selected.dimensionId(), selected.pos(), slot);
            SlotStockTargets targets = getSlotStockTargets(primary, slotKey);
            if (current >= targets.maxStockTarget()) {
                continue;
            }
            int needed = targets.maxStockTarget() - current;
            if (needed <= 0) {
                continue;
            }
            int moved = pullMatchingFromInventories(inventories, template, needed);
            if (moved <= 0) {
                continue;
            }
            shelf.addStock(slot, moved);
            movedTotal += moved;
            touchedSlots++;
        }

        if (movedTotal <= 0) {
            return new ShopActionResult(false, "No matching stock found for this shelf in stockroom.");
        }
        return new ShopActionResult(true, "Restocked " + movedTotal + " item(s) across " + touchedSlots + " slot(s) in selected shelf.");
    }

    public static ShopActionResult removeShelfSlotToStockroom(MinecraftServer server,
                                                              CentralBank centralBank,
                                                              UUID ownerId,
                                                              UUID shopId,
                                                              String encodedTarget) {
        if (server == null || centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        ShelfSlotTarget target = parseShelfSlotTarget(encodedTarget);
        if (target == null) {
            return new ShopActionResult(false, "Invalid shelf slot target.");
        }
        List<ShelfRef> shelves = collectShelvesForShop(server, primary);
        if (shelves.isEmpty()) {
            return new ShopActionResult(false, "No shelves found inside claimed plots.");
        }
        ShelfRef selected = null;
        for (ShelfRef ref : shelves) {
            if (!normalizedDim(ref.dimensionId()).equals(target.dimensionId())) {
                continue;
            }
            if (!ref.pos().equals(target.pos())) {
                continue;
            }
            selected = ref;
            break;
        }
        if (selected == null) {
            return new ShopActionResult(false, "That shelf no longer exists in your claimed plots.");
        }

        ShelfDisplayBlockEntity shelf = selected.shelf();
        if (shelf.isCreativeShelf()) {
            return new ShopActionResult(false, "Creative shelf slots cannot be removed to stockroom.");
        }
        int slot = target.slot();
        if (slot < 0 || slot >= Math.max(1, shelf.getSlotCount())) {
            return new ShopActionResult(false, "Invalid shelf slot.");
        }
        ItemStack template = shelf.getDisplayItem(slot);
        long price = shelf.getSlotPrice(slot);
        if (template.isEmpty() || price < 0L) {
            return new ShopActionResult(false, "This shelf slot is not configured.");
        }

        int stockCount = Math.max(0, shelf.getSlotStock(slot));
        if (stockCount > 0) {
            ListTag stockroomClaims = getOrUpgradeStockroomClaims(primary);
            if (stockroomClaims.isEmpty()) {
                return new ShopActionResult(false, "Claim stockroom region(s) first.");
            }
            List<InventoryAccess> inventories = collectInventoriesInClaims(server, stockroomClaims);
            if (inventories.isEmpty()) {
                return new ShopActionResult(false, "No inventories found inside stockroom claims.");
            }
            int capacity = simulateInsertIntoInventories(inventories, template, stockCount);
            if (capacity < stockCount) {
                return new ShopActionResult(false,
                        "Not enough stockroom space. Clear stockroom or manually remove stock before removing this display.");
            }
            int inserted = pushMatchingIntoInventories(inventories, template, stockCount);
            if (inserted < stockCount) {
                return new ShopActionResult(false,
                        "Could not move full stock to stockroom. Clear storage and try again.");
            }
        }

        shelf.clearSlot(slot);
        removeSlotMeta(primary, encodedTarget);
        saveShopTag(centralBank, primary);
        return new ShopActionResult(true,
                "Removed shelf slot and moved " + stockCount + " item(s) back to stockroom.");
    }

    public static ShopActionResult setShelfSlotTargets(CentralBank centralBank,
                                                       UUID ownerId,
                                                       UUID shopId,
                                                       String rawPayload) {
        if (centralBank == null || ownerId == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return new ShopActionResult(false, "No shop found. Create one first.");
        }
        if (rawPayload == null || rawPayload.isBlank()) {
            return new ShopActionResult(false, "Invalid stock target payload.");
        }
        String[] parts = rawPayload.trim().split("\\|", -1);
        if (parts.length < 3) {
            return new ShopActionResult(false, "Invalid stock target payload.");
        }
        String slotKey = parts[0].trim();
        int minTarget;
        int maxTarget;
        try {
            minTarget = Integer.parseInt(parts[1].trim());
            maxTarget = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException ex) {
            return new ShopActionResult(false, "Stock targets must be numbers.");
        }
        minTarget = Mth.clamp(minTarget, 0, MAX_SLOT_TARGET);
        maxTarget = Mth.clamp(maxTarget, 0, MAX_SLOT_TARGET);
        if (minTarget > maxTarget) {
            return new ShopActionResult(false, "Min stock target cannot be higher than max target.");
        }
        if (parseShelfSlotTarget(slotKey) == null) {
            return new ShopActionResult(false, "Invalid shelf slot target.");
        }
        setSlotStockTargets(primary, slotKey, minTarget, maxTarget);
        saveShopTag(centralBank, primary);
        return new ShopActionResult(true, "Slot stock targets updated: min " + minTarget + ", max " + maxTarget + ".");
    }

    public static void recordSlotSalesFromBasketEntries(CentralBank centralBank,
                                                        UUID ownerId,
                                                        UUID shopId,
                                                        List<ShelfCartService.BasketEntryView> entries) {
        if (centralBank == null || ownerId == null || entries == null || entries.isEmpty()) {
            return;
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            return;
        }

        boolean changed = false;
        long now = System.currentTimeMillis();
        for (ShelfCartService.BasketEntryView entry : entries) {
            if (entry == null || entry.quantity() <= 0) {
                continue;
            }
            String rawKey = entry.source() == null ? "" : entry.source().trim();
            ShelfSlotTarget target = parseShelfSlotTarget(rawKey);
            if (target == null) {
                continue;
            }
            String slotKey = encodeShelfSlotTarget(target.dimensionId(), target.pos(), target.slot());
            CompoundTag meta = findOrCreateSlotMeta(primary, slotKey, true);
            if (meta == null) {
                continue;
            }
            meta.putLong(TAG_SLOT_LAST_SOLD_MILLIS, now);
            addSlotDailySales(meta, now, entry.quantity());
            changed = true;
        }
        if (changed) {
            saveShopTag(centralBank, primary);
        }
    }

    public static void recordSaleForOwner(CentralBank centralBank, UUID ownerId, long amountDollars) {
        recordSaleForShop(centralBank, ownerId, null, amountDollars);
    }

    public static void recordSaleForShop(CentralBank centralBank, UUID ownerId, UUID shopId, long amountDollars) {
        if (centralBank == null || ownerId == null || amountDollars <= 0L) {
            return;
        }
        CompoundTag primary = resolveShopTag(centralBank, ownerId, shopId);
        if (primary == null) {
            primary = getPrimaryShopTag(centralBank, ownerId);
        }
        if (primary == null) {
            return;
        }
        long revenue = Math.max(0L, primary.getLong(TAG_REVENUE_DOLLARS));
        long target = Math.max(1L, primary.getLong(TAG_NEXT_TARGET_DOLLARS));
        int level = Math.max(1, primary.getInt(TAG_LEVEL));
        try {
            revenue = Math.addExact(revenue, amountDollars);
        } catch (ArithmeticException overflow) {
            revenue = Long.MAX_VALUE;
        }
        primary.putLong(TAG_REVENUE_DOLLARS, Math.max(0L, revenue));
        appendDailySalesHistory(primary, amountDollars);
        if (Config.SHOP_LEVELING_ENABLED.get()) {
            while (revenue >= target && level < 100) {
                level++;
                long nextStep = targetForLevel(level);
                long nextTarget;
                try {
                    nextTarget = Math.addExact(target, nextStep);
                } catch (ArithmeticException overflow) {
                    nextTarget = Long.MAX_VALUE;
                }
                target = Math.max(target + 1L, nextTarget);
            }
        }
        primary.putInt(TAG_LEVEL, level);
        primary.putLong(TAG_NEXT_TARGET_DOLLARS, Math.max(1L, target));
        applyShopTypeSaleFees(centralBank, primary, amountDollars);
        saveShopTag(centralBank, primary);
    }

    public static UUID resolveOwnerShopAtPos(CentralBank centralBank, UUID ownerId, String dimensionId, BlockPos pos) {
        if (centralBank == null || ownerId == null || pos == null) {
            return null;
        }
        List<CompoundTag> shops = getOwnerShops(centralBank, ownerId);
        String dim = normalizedDim(dimensionId);
        for (CompoundTag shop : shops) {
            if (isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), dim, pos)) {
                return shop.contains(TAG_ID) ? shop.getUUID(TAG_ID) : null;
            }
        }
        return null;
    }

    public static boolean canPlaceShopShelf(CentralBank centralBank, UUID ownerId, String dimensionId, BlockPos pos) {
        return validateShopShelfPlacement(centralBank, ownerId, dimensionId, pos).success();
    }

    /**
     * Validates display placement against both claim ownership and level-based display capacity.
     */
    public static ShopActionResult validateShopShelfPlacement(CentralBank centralBank,
                                                              UUID actorId,
                                                              String dimensionId,
                                                              BlockPos pos) {
        if (centralBank == null || actorId == null || pos == null) {
            return new ShopActionResult(false, "Shop service is unavailable.");
        }
        UUID shopId = resolveShopAtPosForActor(centralBank, actorId, dimensionId, pos, true);
        if (shopId == null) {
            String dim = normalizedDim(dimensionId);
            if (isInsideAnyShopClaim(centralBank, dim, pos)) {
                return new ShopActionResult(false, "You do not have build permission for this shop plot.");
            }
            return new ShopActionResult(false, "Claim shop land with the Business Manager PC before placing display blocks.");
        }
        CompoundTag shop = resolveShopById(centralBank, shopId);
        if (shop == null) {
            return new ShopActionResult(false, "Create a shop first.");
        }

        int level = Math.max(1, shop.getInt(TAG_LEVEL));
        int cap = maxDisplayBlocksForLevel(level);
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return new ShopActionResult(false, "Server is unavailable right now.");
        }
        int displayCount = collectShelvesForShop(server, shop).size();
        // Placement path calls this after block entity creation; allow up to cap inclusive.
        if (displayCount > cap) {
            return new ShopActionResult(false,
                    "Display limit reached for this shop level (" + cap + "). Remove displays or level up.");
        }
        return new ShopActionResult(true, "Display placement allowed.");
    }

    public static boolean canPlaceCashierAt(CentralBank centralBank,
                                            UUID ownerId,
                                            UUID shopId,
                                            String dimensionId,
                                            BlockPos pos) {
        if (centralBank == null || ownerId == null || pos == null) {
            return false;
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return false;
        }
        return isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), normalizedDim(dimensionId), pos);
    }

    public static int shopCashierLowBagThreshold() {
        return Math.max(0, Config.SHOP_CASHIER_LOW_BAG_THRESHOLD.get());
    }

    public static int countShoppingBagsInStockroom(MinecraftServer server,
                                                   CentralBank centralBank,
                                                   UUID ownerId,
                                                   UUID shopId) {
        if (server == null || centralBank == null || ownerId == null) {
            return 0;
        }
        ListTag stockroomClaims = resolveStockroomClaimsForShop(centralBank, ownerId, shopId);
        if (stockroomClaims.isEmpty()) {
            return 0;
        }
        ItemStack bagTemplate = new ItemStack(ModBlocks.SHOPPING_BAG.get().asItem());
        // Count plain shopping-bag items across all scanned stockroom inventories.
        return countMatchingInInventories(collectInventoriesInClaims(server, stockroomClaims), bagTemplate);
    }

    public static int pullShoppingBagsFromStockroom(MinecraftServer server,
                                                    CentralBank centralBank,
                                                    UUID ownerId,
                                                    UUID shopId,
                                                    int wantedBags) {
        if (server == null || centralBank == null || ownerId == null || wantedBags <= 0) {
            return 0;
        }
        ListTag stockroomClaims = resolveStockroomClaimsForShop(centralBank, ownerId, shopId);
        if (stockroomClaims.isEmpty()) {
            return 0;
        }
        ItemStack bagTemplate = new ItemStack(ModBlocks.SHOPPING_BAG.get().asItem());
        // Remove bags from storage to reserve them for an in-progress cashier checkout.
        return pullMatchingFromInventories(
                collectInventoriesInClaims(server, stockroomClaims),
                bagTemplate,
                Math.max(0, wantedBags)
        );
    }

    public static int pushShoppingBagsToStockroom(MinecraftServer server,
                                                  CentralBank centralBank,
                                                  UUID ownerId,
                                                  UUID shopId,
                                                  int bagCount) {
        if (server == null || centralBank == null || ownerId == null || bagCount <= 0) {
            return 0;
        }
        ListTag stockroomClaims = resolveStockroomClaimsForShop(centralBank, ownerId, shopId);
        if (stockroomClaims.isEmpty()) {
            return 0;
        }
        ItemStack bagTemplate = new ItemStack(ModBlocks.SHOPPING_BAG.get().asItem());
        // Return reserved/unused bags back into stockroom inventories.
        return pushMatchingIntoInventories(
                collectInventoriesInClaims(server, stockroomClaims),
                bagTemplate,
                Math.max(0, bagCount)
        );
    }

    public static UUID resolveShopOwnerId(CentralBank centralBank, UUID shopId) {
        if (centralBank == null || shopId == null) {
            return null;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop)) {
                continue;
            }
            if (!shop.contains(TAG_ID) || !shopId.equals(shop.getUUID(TAG_ID))) {
                continue;
            }
            return shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
        }
        return null;
    }

    private static boolean isInsideAnyShopClaim(CentralBank centralBank, String dimensionId, BlockPos pos) {
        if (centralBank == null || pos == null) {
            return false;
        }
        String dim = normalizedDim(dimensionId);
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null) {
                continue;
            }
            if (isInsideClaims(shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), dim, pos)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveShopRole(CompoundTag shop, UUID playerId) {
        if (shop == null || playerId == null) {
            return "";
        }
        if (shop.contains(TAG_OWNER) && playerId.equals(shop.getUUID(TAG_OWNER))) {
            return SHOP_ROLE_OWNER;
        }
        for (ShopPermissionEntry entry : decodeShopPermissions(shop)) {
            if (playerId.equals(entry.playerId())) {
                return entry.role();
            }
        }
        return "";
    }

    /**
     * Returns true when the actor has at least the requested delegated role rank
     * on this shop (OWNER > MANAGER > BUILDER > STAFF).
     */
    private static boolean hasShopRoleAtLeast(CompoundTag shop, UUID actorId, String minRole) {
        if (shop == null || actorId == null) {
            return false;
        }
        int actorRank = shopRoleRank(resolveShopRole(shop, actorId));
        int minRank = shopRoleRank(minRole);
        return actorRank >= minRank && minRank > 0;
    }

    private static int shopRoleRank(String roleRaw) {
        String role = normalizeShopPermissionRole(roleRaw);
        return switch (role) {
            case SHOP_ROLE_OWNER -> 4;
            case SHOP_ROLE_MANAGER -> 3;
            case SHOP_ROLE_BUILDER -> 2;
            case SHOP_ROLE_STAFF -> 1;
            default -> 0;
        };
    }

    private static boolean isOpActor(MinecraftServer server, UUID actorId) {
        if (server == null || actorId == null) {
            return false;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(actorId);
        return player != null && player.hasPermissions(3);
    }

    private static UUID resolveShopOwnerIdFromTag(CompoundTag shop) {
        if (shop == null || !shop.contains(TAG_OWNER)) {
            return null;
        }
        try {
            return shop.getUUID(TAG_OWNER);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean canManageShop(CompoundTag shop, UUID playerId) {
        String role = resolveShopRole(shop, playerId);
        return SHOP_ROLE_OWNER.equals(role) || SHOP_ROLE_MANAGER.equals(role);
    }

    private static boolean canBuildInShop(CompoundTag shop, UUID playerId) {
        String role = resolveShopRole(shop, playerId);
        return SHOP_ROLE_OWNER.equals(role) || SHOP_ROLE_MANAGER.equals(role) || SHOP_ROLE_BUILDER.equals(role);
    }

    private static ListTag getOrCreatePermissionsList(CompoundTag shop) {
        if (shop == null) {
            return new ListTag();
        }
        if (!shop.contains(TAG_SHOP_PERMISSIONS, Tag.TAG_LIST)) {
            shop.put(TAG_SHOP_PERMISSIONS, new ListTag());
        }
        return shop.getList(TAG_SHOP_PERMISSIONS, Tag.TAG_COMPOUND);
    }

    private static List<ShopPermissionEntry> decodeShopPermissions(CompoundTag shop) {
        List<ShopPermissionEntry> entries = new ArrayList<>();
        if (shop == null) {
            return entries;
        }
        ListTag list = getOrCreatePermissionsList(shop);
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag permission)) {
                continue;
            }
            if (!permission.contains(TAG_PERMISSION_PLAYER)) {
                continue;
            }
            UUID playerId;
            try {
                playerId = permission.getUUID(TAG_PERMISSION_PLAYER);
            } catch (Exception ignored) {
                continue;
            }
            String role = normalizeShopPermissionRole(permission.getString(TAG_PERMISSION_ROLE));
            if (role.isBlank() || SHOP_ROLE_OWNER.equals(role)) {
                continue;
            }
            entries.add(new ShopPermissionEntry(
                    playerId,
                    role,
                    Math.max(0L, permission.getLong(TAG_PERMISSION_GRANTED_AT))
            ));
        }
        return entries;
    }

    private static void upsertShopPermission(CompoundTag shop, UUID playerId, String role) {
        if (shop == null || playerId == null || role == null || role.isBlank()) {
            return;
        }
        ListTag list = getOrCreatePermissionsList(shop);
        long now = System.currentTimeMillis();
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag permission)) {
                continue;
            }
            if (!permission.contains(TAG_PERMISSION_PLAYER)) {
                continue;
            }
            try {
                if (!playerId.equals(permission.getUUID(TAG_PERMISSION_PLAYER))) {
                    continue;
                }
            } catch (Exception ignored) {
                continue;
            }
            permission.putUUID(TAG_PERMISSION_PLAYER, playerId);
            permission.putString(TAG_PERMISSION_ROLE, role);
            permission.putLong(TAG_PERMISSION_GRANTED_AT, now);
            shop.put(TAG_SHOP_PERMISSIONS, list);
            return;
        }

        CompoundTag created = new CompoundTag();
        created.putUUID(TAG_PERMISSION_PLAYER, playerId);
        created.putString(TAG_PERMISSION_ROLE, role);
        created.putLong(TAG_PERMISSION_GRANTED_AT, now);
        list.add(created);
        shop.put(TAG_SHOP_PERMISSIONS, list);
    }

    private static boolean removeShopPermission(CompoundTag shop, UUID playerId) {
        if (shop == null || playerId == null) {
            return false;
        }
        ListTag list = getOrCreatePermissionsList(shop);
        if (list.isEmpty()) {
            return false;
        }
        ListTag next = new ListTag();
        boolean removed = false;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag permission)) {
                continue;
            }
            if (!permission.contains(TAG_PERMISSION_PLAYER)) {
                next.add(permission.copy());
                continue;
            }
            UUID existing;
            try {
                existing = permission.getUUID(TAG_PERMISSION_PLAYER);
            } catch (Exception ignored) {
                next.add(permission.copy());
                continue;
            }
            if (playerId.equals(existing)) {
                removed = true;
                continue;
            }
            next.add(permission.copy());
        }
        if (removed) {
            shop.put(TAG_SHOP_PERMISSIONS, next);
        }
        return removed;
    }

    private static String normalizeShopPermissionRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return "";
        }
        return switch (rawRole.trim().toUpperCase(Locale.ROOT)) {
            case "OWNER" -> SHOP_ROLE_OWNER;
            case "MANAGER", "MANAGE", "ADMIN", "EDITOR" -> SHOP_ROLE_MANAGER;
            case "BUILDER", "BUILD" -> SHOP_ROLE_BUILDER;
            case "STAFF", "VIEWER", "VIEW", "READONLY", "READ_ONLY" -> SHOP_ROLE_STAFF;
            default -> "";
        };
    }

    private static UUID resolvePlayerSelection(MinecraftServer server, String raw) {
        if (server == null || raw == null || raw.isBlank()) {
            return null;
        }
        String token = raw.trim();
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException ignored) {
        }
        ServerPlayer byExactName = server.getPlayerList().getPlayerByName(token);
        if (byExactName != null) {
            return byExactName.getUUID();
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (online != null && online.getName().getString().equalsIgnoreCase(token)) {
                return online.getUUID();
            }
        }
        return null;
    }

    private static String resolvePlayerLabel(MinecraftServer server, UUID playerId) {
        if (playerId == null) {
            return "-";
        }
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            if (online != null) {
                return sanitizeTokenText(online.getName().getString());
            }
        }
        return shortUuid(playerId);
    }

    public static boolean isTerminalLinkedToAnyCashier(CentralBank centralBank,
                                                        String dimensionId,
                                                        BlockPos pos) {
        if (centralBank == null || pos == null) {
            return false;
        }
        String dim = normalizedDim(dimensionId);
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop)) {
                continue;
            }
            if (containsTerminalRef(shop.getCompound(TAG_CHECKOUT_TERMINAL), dim, pos)) {
                return true;
            }
            if (containsLinkedTerminal(shop.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND), dim, pos)) {
                return true;
            }
        }
        return false;
    }

    private static ListTag resolveStockroomClaimsForShop(CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return new ListTag();
        }
        CompoundTag shop = resolveShopTag(centralBank, ownerId, shopId);
        if (shop == null || !shop.contains(TAG_ID)) {
            return new ListTag();
        }
        ListTag stockroomClaims = shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        return stockroomClaims == null ? new ListTag() : stockroomClaims;
    }

    private static CompoundTag resolveShopTag(CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null) {
            return null;
        }
        List<CompoundTag> ownerShops = getOwnerShops(centralBank, ownerId);
        if (!ownerShops.isEmpty()) {
            if (shopId == null) {
                return ownerShops.get(0).copy();
            }
            for (CompoundTag shop : ownerShops) {
                if (shop.contains(TAG_ID) && shopId.equals(shop.getUUID(TAG_ID))) {
                    return shop.copy();
                }
            }
        }

        // Delegated-access fallback: allow MANAGER/BUILDER/STAFF actors to resolve
        // their assigned shop context by role, even when they are not the owner.
        if (shopId != null) {
            CompoundTag byId = resolveShopById(centralBank, shopId);
            if (byId == null || !byId.contains(TAG_ID)) {
                return null;
            }
            return resolveShopRole(byId, ownerId).isBlank() ? null : byId.copy();
        }

        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            if (!resolveShopRole(shop, ownerId).isBlank()) {
                return shop.copy();
            }
        }
        return null;
    }

    private static boolean containsLinkedTerminal(ListTag linked, String dim, BlockPos pos) {
        if (linked == null || linked.isEmpty() || pos == null) {
            return false;
        }
        for (Tag tag : linked) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            if (!normalizedDim(entry.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            if (entry.getInt(TAG_X) == pos.getX()
                    && entry.getInt(TAG_Y) == pos.getY()
                    && entry.getInt(TAG_Z) == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTerminalRef(CompoundTag terminal, String dim, BlockPos pos) {
        if (terminal == null || terminal.isEmpty() || pos == null) {
            return false;
        }
        return normalizedDim(terminal.getString(TAG_DIM)).equals(dim)
                && terminal.getInt(TAG_X) == pos.getX()
                && terminal.getInt(TAG_Y) == pos.getY()
                && terminal.getInt(TAG_Z) == pos.getZ();
    }

    private static long computeVolume(ListTag claims) {
        if (claims == null || claims.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            total = safeAdd(total, computeRegionVolume(claim));
        }
        return Math.max(0L, total);
    }

    private static long computeRegionVolume(CompoundTag claim) {
        if (claim == null) {
            return 0L;
        }
        return computeRegionVolume(
                claim.getInt(TAG_MIN_X),
                claim.getInt(TAG_MIN_Y),
                claim.getInt(TAG_MIN_Z),
                claim.getInt(TAG_MAX_X),
                claim.getInt(TAG_MAX_Y),
                claim.getInt(TAG_MAX_Z)
        );
    }

    private static long computeRegionVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long dx = (long) Math.abs(maxX - minX) + 1L;
        long dy = (long) Math.abs(maxY - minY) + 1L;
        long dz = (long) Math.abs(maxZ - minZ) + 1L;
        try {
            return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long base, long add) {
        try {
            return Math.addExact(base, add);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static List<CompoundTag> getOwnerShops(CentralBank centralBank, UUID ownerId) {
        List<CompoundTag> out = new ArrayList<>();
        if (centralBank == null || ownerId == null) {
            return out;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop)) {
                continue;
            }
            if (shop.contains(TAG_OWNER) && ownerId.equals(shop.getUUID(TAG_OWNER))) {
                out.add(shop.copy());
            }
        }
        out.sort(Comparator.comparingLong(t -> t.getLong(TAG_CREATED_MILLIS)));
        return out;
    }

    private static CompoundTag getPrimaryShopTag(CentralBank centralBank, UUID ownerId) {
        if (centralBank == null || ownerId == null) {
            return null;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        CompoundTag best = null;
        long created = Long.MAX_VALUE;
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop)) {
                continue;
            }
            if (!shop.contains(TAG_OWNER) || !ownerId.equals(shop.getUUID(TAG_OWNER))) {
                continue;
            }
            long c = shop.getLong(TAG_CREATED_MILLIS);
            if (best == null || c < created) {
                best = shop.copy();
                created = c;
            }
        }
        return best;
    }

    private static boolean deleteShopTag(CentralBank centralBank, UUID ownerId, UUID shopId) {
        if (centralBank == null || ownerId == null || shopId == null) {
            return false;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        ListTag next = new ListTag();
        boolean removed = false;
        for (Tag tag : shops) {
            if (!(tag instanceof CompoundTag shop)) {
                continue;
            }
            boolean sameOwner = shop.contains(TAG_OWNER) && ownerId.equals(shop.getUUID(TAG_OWNER));
            boolean sameShop = shop.contains(TAG_ID) && shopId.equals(shop.getUUID(TAG_ID));
            if (sameOwner && sameShop) {
                removed = true;
                continue;
            }
            next.add(shop.copy());
        }
        if (!removed) {
            return false;
        }
        root.put(TAG_SHOPS, next);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
        return true;
    }

    private static void clearSessionsForDeletedShop(MinecraftServer server, UUID ownerId, UUID shopId) {
        if (ownerId == null || shopId == null) {
            return;
        }
        for (UUID playerId : new ArrayList<>(CLAIM_TOOL_SESSIONS.keySet())) {
            ClaimToolSession session = CLAIM_TOOL_SESSIONS.get(playerId);
            if (session == null || !ownerId.equals(session.ownerId()) || !shopId.equals(session.shopId())) {
                continue;
            }
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                finishClaimToolSession(player, "Claim tool closed: shop was deleted.");
                player.sendSystemMessage(UbsTranslations.literal("§eShop claim tool closed because the shop was deleted."));
                pushShopAlert(player, "Claim Tool", "Shop claim tool closed because the shop was deleted.", DeliveryAlertPayload.AlertTone.WARNING, 5200);
            } else {
                CLAIM_TOOL_SESSIONS.remove(playerId);
            }
        }
        for (UUID playerId : new ArrayList<>(CASHIER_TERMINAL_SELECTIONS.keySet())) {
            CashierTerminalSelection selection = CASHIER_TERMINAL_SELECTIONS.get(playerId);
            if (selection == null || !ownerId.equals(selection.ownerId()) || !shopId.equals(selection.shopId())) {
                continue;
            }
            CASHIER_TERMINAL_SELECTIONS.remove(playerId);
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.sendSystemMessage(UbsTranslations.literal("§eCashier terminal selection closed because the shop was deleted."));
                pushShopAlert(player, "Cashier Link", "Cashier terminal selection closed because the shop was deleted.", DeliveryAlertPayload.AlertTone.WARNING, 5200);
            }
        }
        for (UUID playerId : new ArrayList<>(STOCKROOM_LOCATE_SESSIONS.keySet())) {
            StockroomLocateSession session = STOCKROOM_LOCATE_SESSIONS.get(playerId);
            if (session == null || !ownerId.equals(session.ownerId()) || !shopId.equals(session.shopId())) {
                continue;
            }
            STOCKROOM_LOCATE_SESSIONS.remove(playerId);
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                clearStockroomLocateRender(player);
                player.sendSystemMessage(UbsTranslations.literal("§eStockroom locate closed because the shop was deleted."));
                pushShopAlert(player, "Stockroom Locate", "Stockroom locate closed because the shop was deleted.", DeliveryAlertPayload.AlertTone.WARNING, 5200);
                player.displayClientMessage(UbsTranslations.literal("§8Stockroom locate inactive"), true);
            }
        }
    }

    private static void saveShopTag(CentralBank centralBank, CompoundTag shopTag) {
        if (centralBank == null || shopTag == null || !shopTag.contains(TAG_ID)) {
            return;
        }
        UUID shopId = shopTag.getUUID(TAG_ID);
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        for (int i = 0; i < shops.size(); i++) {
            CompoundTag existing = shops.getCompound(i);
            if (existing.contains(TAG_ID) && shopId.equals(existing.getUUID(TAG_ID))) {
                shops.set(i, shopTag.copy());
                root.put(TAG_SHOPS, shops);
                centralMeta.put(TAG_ROOT, root);
                centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
                return;
            }
        }
        shops.add(shopTag.copy());
        root.put(TAG_SHOPS, shops);
        centralMeta.put(TAG_ROOT, root);
        centralBank.putBankMetadata(centralBank.getBankId(), centralMeta);
    }

    private static List<ShelfRef> collectShelvesForShop(MinecraftServer server, CompoundTag shopTag) {
        List<ShelfRef> out = new ArrayList<>();
        if (server == null || shopTag == null) {
            return out;
        }
        UUID shopId = shopTag.contains(TAG_ID) ? shopTag.getUUID(TAG_ID) : null;
        UUID ownerId = shopTag.contains(TAG_OWNER) ? shopTag.getUUID(TAG_OWNER) : null;
        if (shopId == null || ownerId == null) {
            return out;
        }
        ListTag claims = shopTag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        Set<String> visited = new HashSet<>();
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            String claimDim = normalizedDim(claim.getString(TAG_DIM));
            ServerLevel level = server.getLevel(serverLevelKey(claimDim));
            if (level == null) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        String key = claimDim + "|" + x + "|" + y + "|" + z;
                        if (!visited.add(key)) {
                            continue;
                        }
                        if (!(level.getBlockEntity(pos) instanceof ShelfDisplayBlockEntity shelf)) {
                            continue;
                        }
                        if (shelf.getOwnerUuid() == null || !ownerId.equals(shelf.getOwnerUuid())) {
                            continue;
                        }
                        UUID shelfShopId = shelf.getShopId();
                        if (shelfShopId == null || !shelfShopId.equals(shopId)) {
                            shelf.setShopId(shopId);
                        }
                        out.add(new ShelfRef(claimDim, pos, shelf));
                    }
                }
            }
        }
        out.sort(Comparator.comparing((ShelfRef ref) -> ref.dimensionId().toLowerCase(Locale.ROOT))
                .thenComparingInt(ref -> ref.pos().getY())
                .thenComparingInt(ref -> ref.pos().getZ())
                .thenComparingInt(ref -> ref.pos().getX()));
        return out;
    }

    private interface InventoryAccess {
        int extractMatching(ItemStack template, int wanted);

        int countMatching(ItemStack template);

        int insertMatching(ItemStack template, int wanted, boolean simulate);
    }

    private static int pullMatchingFromInventories(List<InventoryAccess> inventories, ItemStack template, int wanted) {
        if (inventories == null || inventories.isEmpty() || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int moved = 0;
        for (InventoryAccess access : inventories) {
            if (access == null || moved >= wanted) {
                continue;
            }
            int extracted = access.extractMatching(template, wanted - moved);
            moved += Math.max(0, extracted);
        }
        return moved;
    }

    private static int countMatchingInInventories(List<InventoryAccess> inventories, ItemStack template) {
        if (inventories == null || inventories.isEmpty() || template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (InventoryAccess access : inventories) {
            if (access == null) {
                continue;
            }
            int count = Math.max(0, access.countMatching(template));
            if (count <= 0) {
                continue;
            }
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += count;
        }
        return Math.max(0, total);
    }

    private static int simulateInsertIntoInventories(List<InventoryAccess> inventories, ItemStack template, int wanted) {
        if (inventories == null || inventories.isEmpty() || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        for (InventoryAccess access : inventories) {
            if (access == null || inserted >= wanted) {
                continue;
            }
            int accepted = Math.max(0, access.insertMatching(template, wanted - inserted, true));
            inserted += accepted;
        }
        return Math.max(0, inserted);
    }

    private static int pushMatchingIntoInventories(List<InventoryAccess> inventories, ItemStack template, int wanted) {
        if (inventories == null || inventories.isEmpty() || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        for (InventoryAccess access : inventories) {
            if (access == null || inserted >= wanted) {
                continue;
            }
            int accepted = Math.max(0, access.insertMatching(template, wanted - inserted, false));
            inserted += accepted;
        }
        return Math.max(0, inserted);
    }

    private static int pullMatchingFromContainer(Container container, ItemStack template, int wanted) {
        if (container == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int moved = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (moved >= wanted) {
                break;
            }
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                continue;
            }
            int take = Math.min(wanted - moved, stack.getCount());
            stack.shrink(take);
            moved += take;
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            } else {
                container.setItem(slot, stack);
            }
        }
        container.setChanged();
        return moved;
    }

    private static int pullMatchingFromItemHandler(IItemHandler handler, ItemStack template, int wanted) {
        if (handler == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int moved = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (moved >= wanted) {
                break;
            }
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                continue;
            }
            int take = Math.min(wanted - moved, stack.getCount());
            if (take <= 0) {
                continue;
            }
            ItemStack extracted = handler.extractItem(slot, take, false);
            moved += extracted == null ? 0 : extracted.getCount();
        }
        return moved;
    }

    private static int countMatchingInContainer(Container container, ItemStack template) {
        if (container == null || template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                continue;
            }
            int count = Math.max(0, stack.getCount());
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += count;
        }
        return Math.max(0, total);
    }

    private static int countMatchingInItemHandler(IItemHandler handler, ItemStack template) {
        if (handler == null || template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                continue;
            }
            int count = Math.max(0, stack.getCount());
            if (Integer.MAX_VALUE - total < count) {
                return Integer.MAX_VALUE;
            }
            total += count;
        }
        return Math.max(0, total);
    }

    private static int insertMatchingIntoContainer(Container container, ItemStack template, int wanted, boolean simulate) {
        if (container == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        int maxStack = Math.max(1, Math.min(template.getMaxStackSize(), container.getMaxStackSize()));
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (inserted >= wanted) {
                break;
            }
            ItemStack stack = container.getItem(slot);
            int capacity;
            if (stack.isEmpty()) {
                capacity = maxStack;
            } else if (ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                capacity = maxStack - Math.max(0, stack.getCount());
            } else {
                capacity = 0;
            }
            if (capacity <= 0) {
                continue;
            }
            int toInsert = Math.min(capacity, wanted - inserted);
            if (!simulate) {
                if (stack.isEmpty()) {
                    ItemStack copy = template.copy();
                    copy.setCount(toInsert);
                    container.setItem(slot, copy);
                } else {
                    stack.grow(toInsert);
                    container.setItem(slot, stack);
                }
            }
            inserted += toInsert;
        }
        if (!simulate && inserted > 0) {
            container.setChanged();
        }
        return Math.max(0, inserted);
    }

    private static int insertMatchingIntoItemHandler(IItemHandler handler, ItemStack template, int wanted, boolean simulate) {
        if (handler == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        int remaining = wanted;
        int maxStack = Math.max(1, template.getMaxStackSize());
        for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
            int batch = Math.min(maxStack, remaining);
            ItemStack probe = template.copy();
            probe.setCount(batch);
            ItemStack remainder = handler.insertItem(slot, probe, simulate);
            int accepted = batch - (remainder == null ? 0 : Math.max(0, remainder.getCount()));
            if (accepted <= 0) {
                continue;
            }
            inserted += accepted;
            remaining -= accepted;
        }
        return Math.max(0, inserted);
    }

    private static int pullMatchingFromPalletBoxes(PalletBlockEntity pallet, ItemStack template, int wanted) {
        if (pallet == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int moved = 0;
        boolean palletChanged = false;
        for (int column = 0; column < PalletBlockEntity.COLUMNS && moved < wanted; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS && moved < wanted; layer++) {
                ItemStack box = pallet.getBox(column, layer);
                if (box == null || box.isEmpty() || !box.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
                    continue;
                }
                ItemStackHandler boxInventory = readCardboardBoxInventory(box);
                boolean boxChanged = false;
                for (int slot = 0; slot < boxInventory.getSlots() && moved < wanted; slot++) {
                    ItemStack stack = boxInventory.getStackInSlot(slot);
                    if (stack == null || stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                        continue;
                    }
                    int take = Math.min(wanted - moved, Math.max(0, stack.getCount()));
                    if (take <= 0) {
                        continue;
                    }
                    if (take >= stack.getCount()) {
                        boxInventory.setStackInSlot(slot, ItemStack.EMPTY);
                    } else {
                        ItemStack updated = stack.copy();
                        updated.shrink(take);
                        boxInventory.setStackInSlot(slot, updated);
                    }
                    moved += take;
                    boxChanged = true;
                }
                if (boxChanged) {
                    writeCardboardBoxInventory(box, boxInventory);
                    palletChanged = true;
                }
            }
        }
        if (palletChanged) {
            markPalletInventoryChanged(pallet);
        }
        return Math.max(0, moved);
    }

    private static int countMatchingInPalletBoxes(PalletBlockEntity pallet, ItemStack template) {
        if (pallet == null || template == null || template.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack box = pallet.getBox(column, layer);
                if (box == null || box.isEmpty() || !box.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
                    continue;
                }
                ItemStackHandler boxInventory = readCardboardBoxInventory(box);
                for (int slot = 0; slot < boxInventory.getSlots(); slot++) {
                    ItemStack stack = boxInventory.getStackInSlot(slot);
                    if (stack == null || stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                        continue;
                    }
                    int count = Math.max(0, stack.getCount());
                    if (Integer.MAX_VALUE - total < count) {
                        return Integer.MAX_VALUE;
                    }
                    total += count;
                }
            }
        }
        return Math.max(0, total);
    }

    private static int insertMatchingIntoPalletBoxes(PalletBlockEntity pallet,
                                                     ItemStack template,
                                                     int wanted,
                                                     boolean simulate) {
        if (pallet == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        boolean palletChanged = false;
        for (int column = 0; column < PalletBlockEntity.COLUMNS && inserted < wanted; column++) {
            for (int layer = 0; layer < PalletBlockEntity.LAYERS && inserted < wanted; layer++) {
                ItemStack box = pallet.getBox(column, layer);
                if (box == null || box.isEmpty() || !box.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
                    continue;
                }
                ItemStackHandler boxInventory = readCardboardBoxInventory(box);
                ItemStackHandler target = simulate ? copyItemHandler(boxInventory) : boxInventory;
                int accepted = insertMatchingIntoBoxInventory(target, template, wanted - inserted);
                if (accepted <= 0) {
                    continue;
                }
                inserted += accepted;
                if (!simulate) {
                    writeCardboardBoxInventory(box, target);
                    palletChanged = true;
                }
            }
        }
        if (!simulate && palletChanged) {
            markPalletInventoryChanged(pallet);
        }
        return Math.max(0, inserted);
    }

    private static ItemStackHandler copyItemHandler(ItemStackHandler source) {
        int slots = source == null ? Math.max(1, CardboardBoxBlockEntity.SLOT_COUNT) : Math.max(1, source.getSlots());
        ItemStackHandler copy = new ItemStackHandler(slots);
        if (source != null) {
            ItemStackDataCompat.deserializeHandler(copy, ItemStackDataCompat.serializeHandler(source));
        }
        return copy;
    }

    private static int insertMatchingIntoBoxInventory(ItemStackHandler handler, ItemStack template, int wanted) {
        if (handler == null || template == null || template.isEmpty() || wanted <= 0) {
            return 0;
        }
        int inserted = 0;
        int maxStack = Math.max(1, template.getMaxStackSize());

        for (int slot = 0; slot < handler.getSlots() && inserted < wanted; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty() || !ItemStackDataCompat.sameItemSameComponents(stack, template)) {
                continue;
            }
            int capacity = Math.max(0, maxStack - stack.getCount());
            if (capacity <= 0) {
                continue;
            }
            int toInsert = Math.min(capacity, wanted - inserted);
            if (toInsert <= 0) {
                continue;
            }
            ItemStack updated = stack.copy();
            updated.grow(toInsert);
            handler.setStackInSlot(slot, updated);
            inserted += toInsert;
        }

        for (int slot = 0; slot < handler.getSlots() && inserted < wanted; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack != null && !stack.isEmpty()) {
                continue;
            }
            int toInsert = Math.min(maxStack, wanted - inserted);
            if (toInsert <= 0) {
                continue;
            }
            ItemStack add = template.copy();
            add.setCount(toInsert);
            handler.setStackInSlot(slot, add);
            inserted += toInsert;
        }
        return Math.max(0, inserted);
    }

    private static void writeCardboardBoxInventory(ItemStack boxStack, ItemStackHandler handler) {
        if (boxStack == null || boxStack.isEmpty() || handler == null || !boxStack.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
            return;
        }
        ItemStackDataCompat.putCustomData(CardboardBoxDataKeys.BOX_DATA_KEY, boxStack, ItemStackDataCompat.serializeHandler(handler));
    }

    private static void markPalletInventoryChanged(PalletBlockEntity pallet) {
        if (pallet == null) {
            return;
        }
        pallet.setChanged();
        Level level = pallet.getLevel();
        if (level != null) {
            BlockPos pos = pallet.getBlockPos();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private static List<InventoryAccess> collectInventoriesInClaims(MinecraftServer server, ListTag claims) {
        List<InventoryAccess> out = new ArrayList<>();
        if (server == null || claims == null || claims.isEmpty()) {
            return out;
        }
        Set<String> seenBlocks = new HashSet<>();
        Set<UUID> seenEntities = new HashSet<>();

        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(claim.getString(TAG_DIM)));
            if (level == null) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockPos scanPos = pos;
                        BlockState scanState = level.getBlockState(pos);
                        if (scanState.is(ModBlocks.PALLET.get())) {
                            scanPos = PalletBlock.getMasterPos(scanState, pos);
                        }
                        String key = level.dimension().location()
                                + "|" + scanPos.getX()
                                + "|" + scanPos.getY()
                                + "|" + scanPos.getZ();
                        if (!seenBlocks.add(key)) {
                            continue;
                        }
                        BlockEntity be = level.getBlockEntity(scanPos);
                        if (be == null) {
                            continue;
                        }
                        if (be instanceof PalletBlockEntity pallet) {
                            // Pallets store cardboard boxes; expose box contents as stockroom inventory items.
                            out.add(new InventoryAccess() {
                                @Override
                                public int extractMatching(ItemStack template, int wanted) {
                                    return pullMatchingFromPalletBoxes(pallet, template, wanted);
                                }

                                @Override
                                public int countMatching(ItemStack template) {
                                    return countMatchingInPalletBoxes(pallet, template);
                                }

                                @Override
                                public int insertMatching(ItemStack template, int wanted, boolean simulate) {
                                    return insertMatchingIntoPalletBoxes(pallet, template, wanted, simulate);
                                }
                            });
                            continue;
                        }
                        if (be instanceof Container container) {
                            out.add(new InventoryAccess() {
                                @Override
                                public int extractMatching(ItemStack template, int wanted) {
                                    return pullMatchingFromContainer(container, template, wanted);
                                }

                                @Override
                                public int countMatching(ItemStack template) {
                                    return countMatchingInContainer(container, template);
                                }

                                @Override
                                public int insertMatching(ItemStack template, int wanted, boolean simulate) {
                                    return insertMatchingIntoContainer(container, template, wanted, simulate);
                                }
                            });
                            continue;
                        }
                        IItemHandler handler = findBlockItemHandler(level, be.getBlockPos());
                        if (handler != null) {
                            out.add(new InventoryAccess() {
                                    @Override
                                    public int extractMatching(ItemStack template, int wanted) {
                                        return pullMatchingFromItemHandler(handler, template, wanted);
                                    }

                                    @Override
                                    public int countMatching(ItemStack template) {
                                        return countMatchingInItemHandler(handler, template);
                                    }

                                    @Override
                                    public int insertMatching(ItemStack template, int wanted, boolean simulate) {
                                        return insertMatchingIntoItemHandler(handler, template, wanted, simulate);
                                    }
                                });
                        }
                    }
                }
            }

            AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
            List<Entity> entities = level.getEntities((Entity) null, bounds, Entity::isAlive);
            for (Entity entity : entities) {
                if (entity == null || !seenEntities.add(entity.getUUID())) {
                    continue;
                }
                if (entity instanceof Container container) {
                    out.add(new InventoryAccess() {
                        @Override
                        public int extractMatching(ItemStack template, int wanted) {
                            return pullMatchingFromContainer(container, template, wanted);
                        }

                        @Override
                        public int countMatching(ItemStack template) {
                            return countMatchingInContainer(container, template);
                        }

                        @Override
                        public int insertMatching(ItemStack template, int wanted, boolean simulate) {
                            return insertMatchingIntoContainer(container, template, wanted, simulate);
                        }
                    });
                    continue;
                }
                IItemHandler handler = findEntityItemHandler(entity);
                if (handler != null) {
                    out.add(new InventoryAccess() {
                            @Override
                            public int extractMatching(ItemStack template, int wanted) {
                                return pullMatchingFromItemHandler(handler, template, wanted);
                            }

                            @Override
                            public int countMatching(ItemStack template) {
                                return countMatchingInItemHandler(handler, template);
                            }

                            @Override
                            public int insertMatching(ItemStack template, int wanted, boolean simulate) {
                                return insertMatchingIntoItemHandler(handler, template, wanted, simulate);
                            }
                        });
                }
            }
        }
        return out;
    }

    private static List<StockroomItemEntry> collectStockroomEntries(MinecraftServer server, ListTag claims) {
        List<StockroomItemEntry> out = new ArrayList<>();
        if (server == null || claims == null || claims.isEmpty()) {
            return out;
        }
        Set<String> seenBlocks = new HashSet<>();
        Set<UUID> seenEntities = new HashSet<>();

        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(claim.getString(TAG_DIM)));
            if (level == null) {
                continue;
            }
            String dim = normalizedDim(level.dimension().location().toString());
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockPos scanPos = pos;
                        BlockState scanState = level.getBlockState(pos);
                        if (scanState.is(ModBlocks.PALLET.get())) {
                            scanPos = PalletBlock.getMasterPos(scanState, pos);
                        }
                        String key = dim + "|" + scanPos.getX() + "|" + scanPos.getY() + "|" + scanPos.getZ();
                        if (!seenBlocks.add(key)) {
                            continue;
                        }
                        BlockEntity be = level.getBlockEntity(scanPos);
                        if (be == null) {
                            continue;
                        }
                        String inventoryType = be.getBlockState().getBlock().getName().getString();
                        if (inventoryType == null || inventoryType.isBlank()) {
                            inventoryType = be.getClass().getSimpleName();
                        }
                        if (be instanceof PalletBlockEntity pallet) {
                            appendStockroomPalletEntries(out, dim, scanPos, sanitizeTokenText(inventoryType), pallet);
                            continue;
                        }
                        if (be instanceof Container container) {
                            appendStockroomContainerEntries(out, dim, scanPos, sanitizeTokenText(inventoryType), container);
                            continue;
                        }
                        IItemHandler handler = findBlockItemHandler(level, be.getBlockPos());
                        if (handler != null) {
                            appendStockroomItemHandlerEntries(out, dim, scanPos, sanitizeTokenText(inventoryType), handler);
                        }
                    }
                }
            }

            AABB bounds = new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
            List<Entity> entities = level.getEntities((Entity) null, bounds, Entity::isAlive);
            for (Entity entity : entities) {
                if (entity == null || !seenEntities.add(entity.getUUID())) {
                    continue;
                }
                BlockPos pos = entity.blockPosition();
                String inventoryType = entity.getDisplayName().getString();
                if (inventoryType == null || inventoryType.isBlank()) {
                    inventoryType = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
                }
                String sanitizedType = sanitizeTokenText(inventoryType);
                if (entity instanceof Container container) {
                    appendStockroomContainerEntries(out, dim, pos, sanitizedType, container);
                    continue;
                }
                IItemHandler handler = findEntityItemHandler(entity);
                if (handler != null) {
                    appendStockroomItemHandlerEntries(out, dim, pos, sanitizedType, handler);
                }
            }
        }

        out.sort(Comparator
                .comparing((StockroomItemEntry e) -> normalizedDim(e.dimensionId()))
                .thenComparingInt(e -> e.pos().getX())
                .thenComparingInt(e -> e.pos().getY())
                .thenComparingInt(e -> e.pos().getZ())
                .thenComparingInt(StockroomItemEntry::slot)
                .thenComparing(StockroomItemEntry::itemName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    private static IItemHandler findBlockItemHandler(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) {
            return null;
        }
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) {
            return handler;
        }
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (handler != null) {
                return handler;
            }
        }
        return null;
    }

    private static IItemHandler findEntityItemHandler(Entity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getCapability(Capabilities.ItemHandler.ENTITY);
    }

    private static void appendStockroomContainerEntries(List<StockroomItemEntry> out,
                                                        String dimensionId,
                                                        BlockPos pos,
                                                        String inventoryType,
                                                        Container container) {
        if (out == null || dimensionId == null || pos == null || container == null) {
            return;
        }
        int slots = Math.max(1, container.getContainerSize());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            String itemName = stack.getHoverName().getString();
            out.add(new StockroomItemEntry(
                    itemId,
                    sanitizeTokenText(itemName),
                    Math.max(1, stack.getCount()),
                    inventoryType,
                    normalizedDim(dimensionId),
                    pos,
                    slot + 1,
                    slots,
                    encodeStockroomLocateTarget(dimensionId, pos, slot + 1, inventoryType)
            ));
        }
    }

    private static void appendStockroomItemHandlerEntries(List<StockroomItemEntry> out,
                                                          String dimensionId,
                                                          BlockPos pos,
                                                          String inventoryType,
                                                          IItemHandler handler) {
        if (out == null || dimensionId == null || pos == null || handler == null) {
            return;
        }
        int slots = Math.max(1, handler.getSlots());
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            String itemName = stack.getHoverName().getString();
            out.add(new StockroomItemEntry(
                    itemId,
                    sanitizeTokenText(itemName),
                    Math.max(1, stack.getCount()),
                    inventoryType,
                    normalizedDim(dimensionId),
                    pos,
                    slot + 1,
                    slots,
                    encodeStockroomLocateTarget(dimensionId, pos, slot + 1, inventoryType)
            ));
        }
    }

    private static void appendStockroomPalletEntries(List<StockroomItemEntry> out,
                                                     String dimensionId,
                                                     BlockPos pos,
                                                     String inventoryType,
                                                     PalletBlockEntity pallet) {
        if (out == null || dimensionId == null || pos == null || pallet == null) {
            return;
        }
        String baseType = sanitizeTokenText((inventoryType == null || inventoryType.isBlank()) ? "Pallet" : inventoryType);
        int boxSlots = Math.max(1, CardboardBoxBlockEntity.SLOT_COUNT);
        for (int column = 0; column < PalletBlockEntity.COLUMNS; column++) {
            int row = (column / 3) + 1;
            int col = (column % 3) + 1;
            for (int layer = 0; layer < PalletBlockEntity.LAYERS; layer++) {
                ItemStack box = pallet.getBox(column, layer);
                if (box == null || box.isEmpty() || !box.is(ModBlocks.CARDBOARD_BOX.get().asItem())) {
                    continue;
                }
                String boxLabel = sanitizeTokenText(baseType + " R" + row + " C" + col + " L" + (layer + 1));
                int locateSlot = encodePalletLocateSlot(column, layer);
                String locateTarget = encodeStockroomLocateTarget(dimensionId, pos, locateSlot, boxLabel);
                ItemStackHandler boxInventory = readCardboardBoxInventory(box);
                for (int slot = 0; slot < boxSlots; slot++) {
                    ItemStack stack = boxInventory.getStackInSlot(slot);
                    if (stack == null || stack.isEmpty()) {
                        continue;
                    }
                    String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
                    String itemName = stack.getHoverName().getString();
                    out.add(new StockroomItemEntry(
                            itemId,
                            sanitizeTokenText(itemName),
                            Math.max(1, stack.getCount()),
                            boxLabel,
                            normalizedDim(dimensionId),
                            pos,
                            slot + 1,
                            boxSlots,
                            locateTarget
                    ));
                }
            }
        }
    }

    private static ItemStackHandler readCardboardBoxInventory(ItemStack boxStack) {
        ItemStackHandler handler = new ItemStackHandler(Math.max(1, CardboardBoxBlockEntity.SLOT_COUNT));
        if (boxStack == null || boxStack.isEmpty()) {
            return handler;
        }
        CompoundTag root = ItemStackDataCompat.getCustomData(boxStack);
        if (root == null || !root.contains(CardboardBoxDataKeys.BOX_DATA_KEY, Tag.TAG_COMPOUND)) {
            return handler;
        }
        ItemStackDataCompat.deserializeHandler(handler, root.getCompound(CardboardBoxDataKeys.BOX_DATA_KEY));
        return handler;
    }

    private static BlockPos findNearestContainer(ServerLevel level, BlockPos origin, int horizontalRadius, int verticalRadius) {
        if (level == null || origin == null) {
            return null;
        }
        BlockPos bestPos = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
            int y = origin.getY() + dy;
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                int x = origin.getX() + dx;
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    int z = origin.getZ() + dz;
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockEntity be = level.getBlockEntity(pos);
                    if (!(be instanceof Container)) {
                        continue;
                    }
                    int score = Math.abs(dx) + Math.abs(dy * 2) + Math.abs(dz);
                    if (score < bestScore) {
                        bestScore = score;
                        bestPos = pos;
                    }
                }
            }
        }
        return bestPos;
    }

    private static BlockPos findNearestTerminal(ServerLevel level, BlockPos origin, int horizontalRadius, int verticalRadius) {
        if (level == null || origin == null) {
            return null;
        }
        BlockPos bestPos = null;
        int bestScore = Integer.MAX_VALUE;
        for (int dy = -verticalRadius; dy <= verticalRadius; dy++) {
            int y = origin.getY() + dy;
            for (int dx = -horizontalRadius; dx <= horizontalRadius; dx++) {
                int x = origin.getX() + dx;
                for (int dz = -horizontalRadius; dz <= horizontalRadius; dz++) {
                    int z = origin.getZ() + dz;
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(ModBlocks.PAYMENT_TERMINAL.get())) {
                        continue;
                    }
                    if (!(level.getBlockEntity(pos) instanceof ShopTerminalBlockEntity)) {
                        continue;
                    }
                    int score = Math.abs(dx) + Math.abs(dy * 2) + Math.abs(dz);
                    if (score < bestScore) {
                        bestScore = score;
                        bestPos = pos;
                    }
                }
            }
        }
        return bestPos;
    }

    private static CompoundTag buildRegionTag(String dimensionId,
                                              int minX,
                                              int minY,
                                              int minZ,
                                              int maxX,
                                              int maxY,
                                              int maxZ) {
        CompoundTag claim = new CompoundTag();
        claim.putString(TAG_DIM, normalizedDim(dimensionId));
        claim.putInt(TAG_MIN_X, Math.min(minX, maxX));
        claim.putInt(TAG_MIN_Y, Math.min(minY, maxY));
        claim.putInt(TAG_MIN_Z, Math.min(minZ, maxZ));
        claim.putInt(TAG_MAX_X, Math.max(minX, maxX));
        claim.putInt(TAG_MAX_Y, Math.max(minY, maxY));
        claim.putInt(TAG_MAX_Z, Math.max(minZ, maxZ));
        return claim;
    }

    private static int regionMin(CompoundTag region, String minKey, String maxKey, String legacyKey) {
        if (region == null) {
            return 0;
        }
        int a = region.contains(minKey) ? region.getInt(minKey)
                : (region.contains(legacyKey) ? region.getInt(legacyKey) : 0);
        int b = region.contains(maxKey) ? region.getInt(maxKey) : a;
        return Math.min(a, b);
    }

    private static int regionMax(CompoundTag region, String minKey, String maxKey, String legacyKey) {
        if (region == null) {
            return 0;
        }
        int a = region.contains(minKey) ? region.getInt(minKey)
                : (region.contains(legacyKey) ? region.getInt(legacyKey) : 0);
        int b = region.contains(maxKey) ? region.getInt(maxKey) : a;
        return Math.max(a, b);
    }

    private static int regionMinX(CompoundTag region) {
        return regionMin(region, TAG_MIN_X, TAG_MAX_X, TAG_X);
    }

    private static int regionMaxX(CompoundTag region) {
        return regionMax(region, TAG_MIN_X, TAG_MAX_X, TAG_X);
    }

    private static int regionMinY(CompoundTag region) {
        return regionMin(region, TAG_MIN_Y, TAG_MAX_Y, TAG_Y);
    }

    private static int regionMaxY(CompoundTag region) {
        return regionMax(region, TAG_MIN_Y, TAG_MAX_Y, TAG_Y);
    }

    private static int regionMinZ(CompoundTag region) {
        return regionMin(region, TAG_MIN_Z, TAG_MAX_Z, TAG_Z);
    }

    private static int regionMaxZ(CompoundTag region) {
        return regionMax(region, TAG_MIN_Z, TAG_MAX_Z, TAG_Z);
    }

    private static List<CompoundTag> subtractRegion(CompoundTag source,
                                                    int removeMinX,
                                                    int removeMinY,
                                                    int removeMinZ,
                                                    int removeMaxX,
                                                    int removeMaxY,
                                                    int removeMaxZ) {
        List<CompoundTag> out = new ArrayList<>();
        if (source == null) {
            return out;
        }
        String dim = normalizedDim(source.getString(TAG_DIM));
        int ax1 = Math.min(source.getInt(TAG_MIN_X), source.getInt(TAG_MAX_X));
        int ay1 = Math.min(source.getInt(TAG_MIN_Y), source.getInt(TAG_MAX_Y));
        int az1 = Math.min(source.getInt(TAG_MIN_Z), source.getInt(TAG_MAX_Z));
        int ax2 = Math.max(source.getInt(TAG_MIN_X), source.getInt(TAG_MAX_X));
        int ay2 = Math.max(source.getInt(TAG_MIN_Y), source.getInt(TAG_MAX_Y));
        int az2 = Math.max(source.getInt(TAG_MIN_Z), source.getInt(TAG_MAX_Z));

        int bx1 = Math.min(removeMinX, removeMaxX);
        int by1 = Math.min(removeMinY, removeMaxY);
        int bz1 = Math.min(removeMinZ, removeMaxZ);
        int bx2 = Math.max(removeMinX, removeMaxX);
        int by2 = Math.max(removeMinY, removeMaxY);
        int bz2 = Math.max(removeMinZ, removeMaxZ);

        if (!claimsOverlap(ax1, ay1, az1, ax2, ay2, az2, bx1, by1, bz1, bx2, by2, bz2)) {
            out.add(buildRegionTag(dim, ax1, ay1, az1, ax2, ay2, az2));
            return out;
        }

        int ox1 = Math.max(ax1, bx1);
        int oy1 = Math.max(ay1, by1);
        int oz1 = Math.max(az1, bz1);
        int ox2 = Math.min(ax2, bx2);
        int oy2 = Math.min(ay2, by2);
        int oz2 = Math.min(az2, bz2);

        // Left / right slabs.
        addRegionIfValid(out, dim, ax1, ay1, az1, ox1 - 1, ay2, az2);
        addRegionIfValid(out, dim, ox2 + 1, ay1, az1, ax2, ay2, az2);

        // Remaining x-range.
        int rx1 = Math.max(ax1, ox1);
        int rx2 = Math.min(ax2, ox2);

        // Bottom / top slabs.
        addRegionIfValid(out, dim, rx1, ay1, az1, rx2, oy1 - 1, az2);
        addRegionIfValid(out, dim, rx1, oy2 + 1, az1, rx2, ay2, az2);

        // Remaining x + y-range.
        int ry1 = Math.max(ay1, oy1);
        int ry2 = Math.min(ay2, oy2);

        // Front / back slabs.
        addRegionIfValid(out, dim, rx1, ry1, az1, rx2, ry2, oz1 - 1);
        addRegionIfValid(out, dim, rx1, ry1, oz2 + 1, rx2, ry2, az2);

        return out;
    }

    private static void addRegionIfValid(List<CompoundTag> out,
                                         String dimensionId,
                                         int minX,
                                         int minY,
                                         int minZ,
                                         int maxX,
                                         int maxY,
                                         int maxZ) {
        if (out == null) {
            return;
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return;
        }
        out.add(buildRegionTag(dimensionId, minX, minY, minZ, maxX, maxY, maxZ));
    }

    private static void clampStockroomClaimsToPlot(CompoundTag shopTag) {
        if (shopTag == null) {
            return;
        }
        ListTag claims = shopTag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag stockroom = shopTag.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        ListTag next = new ListTag();
        for (Tag tag : stockroom) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            String dim = normalizedDim(region.getString(TAG_DIM));
            int minX = Math.min(region.getInt(TAG_MIN_X), region.getInt(TAG_MAX_X));
            int minY = Math.min(region.getInt(TAG_MIN_Y), region.getInt(TAG_MAX_Y));
            int minZ = Math.min(region.getInt(TAG_MIN_Z), region.getInt(TAG_MAX_Z));
            int maxX = Math.max(region.getInt(TAG_MIN_X), region.getInt(TAG_MAX_X));
            int maxY = Math.max(region.getInt(TAG_MIN_Y), region.getInt(TAG_MAX_Y));
            int maxZ = Math.max(region.getInt(TAG_MIN_Z), region.getInt(TAG_MAX_Z));
            if (isRegionInsideClaims(claims, dim, minX, minY, minZ, maxX, maxY, maxZ)) {
                next.add(buildRegionTag(dim, minX, minY, minZ, maxX, maxY, maxZ));
            }
        }
        shopTag.put(TAG_STOCKROOM_CLAIMS, next);
    }

    private static boolean isRegionInsideClaims(ListTag claims,
                                                String dimensionId,
                                                int minX,
                                                int minY,
                                                int minZ,
                                                int maxX,
                                                int maxY,
                                                int maxZ) {
        if (claims == null || claims.isEmpty()) {
            return false;
        }
        String dim = normalizedDim(dimensionId);
        int cx1 = Math.min(minX, maxX);
        int cy1 = Math.min(minY, maxY);
        int cz1 = Math.min(minZ, maxZ);
        int cx2 = Math.max(minX, maxX);
        int cy2 = Math.max(minY, maxY);
        int cz2 = Math.max(minZ, maxZ);

        // Validate against the union of all claim regions in this dimension,
        // not just one region, so selections spanning adjacent claims are accepted.
        List<CompoundTag> uncovered = new ArrayList<>();
        uncovered.add(buildRegionTag(dim, cx1, cy1, cz1, cx2, cy2, cz2));

        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            int px1 = regionMinX(claim);
            int py1 = regionMinY(claim);
            int pz1 = regionMinZ(claim);
            int px2 = regionMaxX(claim);
            int py2 = regionMaxY(claim);
            int pz2 = regionMaxZ(claim);

            List<CompoundTag> nextUncovered = new ArrayList<>();
            for (CompoundTag missing : uncovered) {
                nextUncovered.addAll(subtractRegion(missing, px1, py1, pz1, px2, py2, pz2));
            }
            uncovered = nextUncovered;
            if (uncovered.isEmpty()) {
                return true;
            }
        }
        if (uncovered.isEmpty()) {
            return true;
        }

        // Fallback safety path for legacy/edge claim data:
        // verify block-wise for reasonably sized selections to avoid false negatives.
        long sizeX = (long) cx2 - (long) cx1 + 1L;
        long sizeY = (long) cy2 - (long) cy1 + 1L;
        long sizeZ = (long) cz2 - (long) cz1 + 1L;
        long volume = sizeX * sizeY * sizeZ;
        if (volume > 0L && volume <= 250_000L) {
            for (int x = cx1; x <= cx2; x++) {
                for (int y = cy1; y <= cy2; y++) {
                    for (int z = cz1; z <= cz2; z++) {
                        if (!isInsideClaims(claims, dim, new BlockPos(x, y, z))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    private static int[] claimVerticalBounds(ListTag claims, String dimensionId) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        String dim = normalizedDim(dimensionId);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        boolean found = false;
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            int claimMinY = regionMinY(claim);
            int claimMaxY = regionMaxY(claim);
            min = Math.min(min, claimMinY);
            max = Math.max(max, claimMaxY);
            found = true;
        }
        return found ? new int[]{min, max} : null;
    }

    public static boolean overlapsClaimOwnedByAnotherPlayer(CentralBank centralBank,
                                                             UUID requestedOwnerId,
                                                             String dimensionId,
                                                             int minX,
                                                             int minY,
                                                             int minZ,
                                                             int maxX,
                                                             int maxY,
                                                             int maxZ) {
        if (centralBank == null || requestedOwnerId == null) {
            return false;
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        ListTag shops = getOrCreateRoot(centralMeta).getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        String dimension = normalizedDim(dimensionId);
        for (Tag raw : shops) {
            if (!(raw instanceof CompoundTag shop)) {
                continue;
            }
            UUID ownerId = shop.contains(TAG_OWNER) ? shop.getUUID(TAG_OWNER) : null;
            if (requestedOwnerId.equals(ownerId)) {
                continue;
            }
            for (Tag claimRaw : shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND)) {
                if (!(claimRaw instanceof CompoundTag claim)
                        || !dimension.equals(normalizedDim(claim.getString(TAG_DIM)))) {
                    continue;
                }
                if (claimsOverlap(
                        minX, minY, minZ, maxX, maxY, maxZ,
                        claim.getInt(TAG_MIN_X), claim.getInt(TAG_MIN_Y), claim.getInt(TAG_MIN_Z),
                        claim.getInt(TAG_MAX_X), claim.getInt(TAG_MAX_Y), claim.getInt(TAG_MAX_Z))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static ShopOverlapResult overlapsForeignShopClaim(CentralBank centralBank,
                                                              UUID ownShopId,
                                                              String dimensionId,
                                                              int minX,
                                                              int minY,
                                                              int minZ,
                                                              int maxX,
                                                              int maxY,
                                                              int maxZ) {
        if (centralBank == null) {
            return ShopOverlapResult.no();
        }
        CompoundTag centralMeta = centralBank.getOrCreateBankMetadata(centralBank.getBankId());
        CompoundTag root = getOrCreateRoot(centralMeta);
        ListTag shops = root.getList(TAG_SHOPS, Tag.TAG_COMPOUND);
        String dim = normalizedDim(dimensionId);
        for (Tag shopTagRaw : shops) {
            if (!(shopTagRaw instanceof CompoundTag otherShop)) {
                continue;
            }
            if (ownShopId != null && otherShop.contains(TAG_ID) && ownShopId.equals(otherShop.getUUID(TAG_ID))) {
                continue;
            }
            ListTag otherClaims = otherShop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
            for (Tag claimRaw : otherClaims) {
                if (!(claimRaw instanceof CompoundTag claim)) {
                    continue;
                }
                if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                    continue;
                }
                if (claimsOverlap(
                        minX, minY, minZ, maxX, maxY, maxZ,
                        claim.getInt(TAG_MIN_X), claim.getInt(TAG_MIN_Y), claim.getInt(TAG_MIN_Z),
                        claim.getInt(TAG_MAX_X), claim.getInt(TAG_MAX_Y), claim.getInt(TAG_MAX_Z)
                )) {
                    return ShopOverlapResult.yes();
                }
            }
        }
        return ShopOverlapResult.no();
    }

    private static void renderClaimOverlay(MinecraftServer server, ServerPlayer player, ClaimToolSession session) {
        if (server == null || player == null || session == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }
        CompoundTag shop = resolveShopTag(centralBank, session.ownerId(), session.shopId());
        if (shop == null) {
            return;
        }
        String playerDim = normalizedDim(player.serverLevel().dimension().location().toString());
        int[] budget = new int[]{220};
        renderRegionsForPlayer(player, shop.getList(TAG_CLAIMS, Tag.TAG_COMPOUND), playerDim, ParticleTypes.HAPPY_VILLAGER, budget);
        if (session.stockroomMode()) {
            renderRegionsForPlayer(player, shop.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND), playerDim, ParticleTypes.SOUL_FIRE_FLAME, budget);
        }
        if (session.firstCorner() != null
                && session.firstDimensionId() != null
                && normalizedDim(session.firstDimensionId()).equals(playerDim)) {
            sendOverlayParticle(
                    player,
                    ParticleTypes.END_ROD,
                    session.firstCorner().getX() + 0.5D,
                    session.firstCorner().getY() + 1.05D,
                    session.firstCorner().getZ() + 0.5D,
                    budget
            );
            if (session.secondCorner() != null) {
                sendOverlayParticle(
                        player,
                        ParticleTypes.GLOW,
                        session.secondCorner().getX() + 0.5D,
                        session.secondCorner().getY() + 1.05D,
                        session.secondCorner().getZ() + 0.5D,
                        budget
                );
                BlockPos first = session.firstCorner();
                BlockPos second = session.secondCorner();
                drawRegionOutlineForPlayer(
                        player,
                        Math.min(first.getX(), second.getX()),
                        Math.min(first.getY(), second.getY()),
                        Math.min(first.getZ(), second.getZ()),
                        Math.max(first.getX(), second.getX()),
                        Math.max(first.getY(), second.getY()),
                        Math.max(first.getZ(), second.getZ()),
                        session.addMode() ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME,
                        budget
                );
            }
        }
    }

    private static void sendClaimToolStatusActionBar(ServerPlayer player, ClaimToolSession session) {
        if (player == null || session == null) {
            return;
        }
        String type = session.stockroomMode() ? "Stockroom" : "Plot";
        String mode = session.addMode() ? "Add" : "Remove";
        String pos1 = session.firstCorner() == null ? "Pos1: -" : "Pos1: set";
        String pos2 = session.secondCorner() == null ? "Pos2: -" : "Pos2: set";
        String selectionInfo = "";
        if (session.firstCorner() != null && session.secondCorner() != null) {
            long volume = computeRegionVolume(
                    session.firstCorner().getX(),
                    session.firstCorner().getY(),
                    session.firstCorner().getZ(),
                    session.secondCorner().getX(),
                    session.secondCorner().getY(),
                    session.secondCorner().getZ()
            );
            selectionInfo = " | Sel: " + volume + " blocks";
        }
        player.displayClientMessage(
                UbsTranslations.literal("§b" + type + " Claim §7| §fMode: " + mode + " §7| §f" + pos1 + " §7| §f" + pos2
                        + selectionInfo + " §7| §ePaper=Apply"),
                true
        );
    }

    private static void sendPalletClaimToolStatusActionBar(ServerPlayer player, PalletClaimToolSession session) {
        if (player == null || session == null) {
            return;
        }
        int base = session.baseAssignedRefs() == null ? 0 : session.baseAssignedRefs().size();
        int add = session.pendingAddRefs() == null ? 0 : session.pendingAddRefs().size();
        int remove = session.pendingRemoveRefs() == null ? 0 : session.pendingRemoveRefs().size();
        int effective = Math.max(0, base + add - remove);
        player.displayClientMessage(
                UbsTranslations.literal("§bDelivery Pallet Tool §7| §fMode: "
                        + (session.addMode() ? "Add" : "Remove")
                        + " §7| §fPending: +" + add + " / -" + remove
                        + " §7| §fTotal after save: " + effective
                        + " §7| §ePaper=Save"),
                true
        );
    }

    private static void tickDeliveryPalletLabelSync(MinecraftServer server) {
        if (server == null) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        long gameTime = overworld.getGameTime();
        if ((gameTime % DELIVERY_PALLET_LABEL_SYNC_INTERVAL_TICKS) != 0L) {
            return;
        }

        syncDeliveryPalletLabelsNow(server);
    }

    public static void refreshDeliveryPalletLabelsForPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.remove(playerId);
        DELIVERY_PALLET_HOVER_CACHE.remove(playerId);
        syncDeliveryPalletLabelsNow(player.getServer());
    }

    public static void clearDeliveryPalletLabelStateForPlayer(ServerPlayer player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.remove(playerId);
        DELIVERY_PALLET_HOVER_CACHE.remove(playerId);
    }

    private static void syncDeliveryPalletLabelsNow(MinecraftServer server) {
        if (server == null) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            if (!DELIVERY_PALLET_LABEL_CACHE.isEmpty()) {
                for (String refRaw : new ArrayList<>(DELIVERY_PALLET_LABEL_CACHE.keySet())) {
                    PalletRef ref = decodeOrderPalletRef(refRaw);
                    if (ref != null) {
                        applyDeliveryPalletLabelToWorld(server, ref, false, "");
                    }
                }
                DELIVERY_PALLET_LABEL_CACHE.clear();
            }
            pushDeliveryPalletLabelPayloads(server, Map.of());
            return;
        }

        // Build desired label state from assigned delivery pallets.
        Map<String, String> desired = new HashMap<>();
        for (CompoundTag shop : getAllShops(centralBank)) {
            if (shop == null || !shop.contains(TAG_ID)) {
                continue;
            }
            String shopName = sanitizeTokenText(shop.getString(TAG_NAME));
            if (shopName.isBlank()) {
                shopName = "Shop";
            }
            Set<String> assigned = collectAssignedPalletRefSet(shop);
            if (assigned.isEmpty()) {
                // No assigned delivery pallets means no labels to compute; skipping the
                // claim scan avoids force-loading this shop's chunks every second.
                continue;
            }
            if (anyShopChunksUnloaded(server, shop)) {
                // Shop area not observable: keep the last-known label targets without
                // scanning. Label application is chunk-guarded, so unloaded targets
                // are no-ops until a player loads the area again.
                for (Tag palletTag : shop.getList(TAG_ORDER_PALLETS, Tag.TAG_COMPOUND)) {
                    if (!(palletTag instanceof CompoundTag assignedPallet)) {
                        continue;
                    }
                    PalletRef lastKnown = legacyAssignedPalletRef(assignedPallet);
                    if (lastKnown == null || lastKnown.pos() == null) {
                        continue;
                    }
                    String encoded = encodeOrderPalletRef(lastKnown.dimensionId(), lastKnown.pos());
                    if (!encoded.isBlank()) {
                        desired.put(encoded, shopName);
                    }
                }
                continue;
            }
            boolean changed = false;
            Map<String, PalletRef> liveLookup = buildLivePalletLookup(server, deliveryPalletSearchClaims(shop));
            for (String key : assigned) {
                if (key == null || key.isBlank()) {
                    continue;
                }
                PalletRef liveRef = resolveAssignedPalletLiveRef(server, shop, key, liveLookup);
                if (liveRef == null) {
                    continue;
                }
                String encoded = encodeOrderPalletRef(liveRef.dimensionId(), liveRef.pos());
                if (!encoded.isBlank()) {
                    desired.put(encoded, shopName);
                    if (updateAssignedPalletLastKnownPosition(shop, key, liveRef.dimensionId(), liveRef.pos())) {
                        changed = true;
                    }
                }
            }
            if (changed) {
                saveShopTag(centralBank, shop);
            }
        }

        for (String encoded : new ArrayList<>(DELIVERY_PALLET_LABEL_CACHE.keySet())) {
            if (desired.containsKey(encoded)) {
                continue;
            }
            PalletRef ref = decodeOrderPalletRef(encoded);
            if (ref != null) {
                applyDeliveryPalletLabelToWorld(server, ref, false, "");
            }
        }
        for (Map.Entry<String, String> entry : desired.entrySet()) {
            String encoded = entry.getKey();
            String shopName = entry.getValue();
            PalletRef ref = decodeOrderPalletRef(encoded);
            if (ref != null) {
                applyDeliveryPalletLabelToWorld(server, ref, true, shopName);
            }
        }

        DELIVERY_PALLET_LABEL_CACHE.clear();
        DELIVERY_PALLET_LABEL_CACHE.putAll(desired);
        syncDeliveryPalletHolograms(server, desired);
        pushDeliveryPalletLabelPayloads(server, desired);
    }

    private static void pushDeliveryPalletLabelPayloads(MinecraftServer server, Map<String, String> desiredLabels) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.clear();
            return;
        }

        Set<UUID> onlineIds = new HashSet<>();
        Map<String, String> safeDesired = desiredLabels == null ? Map.of() : desiredLabels;
        for (ServerPlayer player : players) {
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUUID();
            onlineIds.add(playerId);
            String dimensionId = normalizedDim(player.serverLevel().dimension().location().toString());
            List<DeliveryPalletLabelSummary> labels = buildDeliveryPalletLabelsForPlayer(player, dimensionId, safeDesired);
            pushDeliveryPalletLabels(player, new DeliveryPalletLabelsPayload(dimensionId, labels));
        }
        clearDeliveryPalletLabelPayloadCacheForOfflinePlayers(onlineIds);
    }

    private static List<DeliveryPalletLabelSummary> buildDeliveryPalletLabelsForPlayer(ServerPlayer player,
                                                                                       String dimensionId,
                                                                                       Map<String, String> desiredLabels) {
        if (player == null || desiredLabels == null || desiredLabels.isEmpty()) {
            return List.of();
        }
        String currentDimension = normalizedDim(dimensionId);
        List<DeliveryPalletLabelSummary> labels = new ArrayList<>();
        for (Map.Entry<String, String> entry : desiredLabels.entrySet()) {
            PalletRef ref = decodeOrderPalletRef(entry.getKey());
            if (ref == null || ref.pos() == null || !currentDimension.equals(normalizedDim(ref.dimensionId()))) {
                continue;
            }
            BlockPos pos = ref.pos();
            double dx = (pos.getX() + 0.5D) - player.getX();
            double dy = (pos.getY() + 0.5D) - player.getY();
            double dz = (pos.getZ() + 0.5D) - player.getZ();
            if ((dx * dx) + (dy * dy) + (dz * dz) > DELIVERY_PALLET_LABEL_SYNC_RANGE_SQ) {
                continue;
            }
            String shopName = sanitizeTokenText(entry.getValue());
            if (shopName.isBlank()) {
                shopName = "Shop";
            }
            labels.add(new DeliveryPalletLabelSummary(pos.getX(), pos.getY(), pos.getZ(), shopName));
        }
        labels.sort(Comparator
                .comparingInt(DeliveryPalletLabelSummary::x)
                .thenComparingInt(DeliveryPalletLabelSummary::y)
                .thenComparingInt(DeliveryPalletLabelSummary::z)
                .thenComparing(DeliveryPalletLabelSummary::shopName, String.CASE_INSENSITIVE_ORDER));
        if (labels.size() > DELIVERY_PALLET_LABEL_MAX_SYNCED_PER_PLAYER) {
            return new ArrayList<>(labels.subList(0, DELIVERY_PALLET_LABEL_MAX_SYNCED_PER_PLAYER));
        }
        return labels;
    }

    private static void pushDeliveryPalletLabels(ServerPlayer player, DeliveryPalletLabelsPayload payload) {
        if (player == null) {
            return;
        }
        DeliveryPalletLabelsPayload safePayload = payload == null
                ? DeliveryPalletLabelsPayload.empty(normalizedDim(player.serverLevel().dimension().location().toString()))
                : payload;
        String fingerprint = deliveryPalletLabelsFingerprint(safePayload);
        UUID playerId = player.getUUID();
        if (Objects.equals(DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.get(playerId), fingerprint)) {
            return;
        }
        PacketDistributor.sendToPlayer(player, safePayload);
        DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.put(playerId, fingerprint);
    }

    private static String deliveryPalletLabelsFingerprint(DeliveryPalletLabelsPayload payload) {
        if (payload == null) {
            return "empty";
        }
        StringBuilder builder = new StringBuilder(normalizedDim(payload.dimensionId()));
        for (DeliveryPalletLabelSummary label : payload.labels()) {
            if (label == null) {
                continue;
            }
            builder.append('|')
                    .append(label.x()).append(',')
                    .append(label.y()).append(',')
                    .append(label.z()).append(',')
                    .append(sanitizeTokenText(label.shopName()));
        }
        return builder.toString();
    }

    private static void clearDeliveryPalletLabelPayloadCacheForOfflinePlayers(Set<UUID> onlineIds) {
        Set<UUID> activeOnline = onlineIds == null ? Set.of() : onlineIds;
        for (UUID cachedPlayerId : new HashSet<>(DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.keySet())) {
            if (!activeOnline.contains(cachedPlayerId)) {
                DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.remove(cachedPlayerId);
            }
        }
    }

    private static void applyDeliveryPalletLabelToWorld(MinecraftServer server,
                                                        PalletRef ref,
                                                        boolean enabled,
                                                        String shopName) {
        if (server == null || ref == null || ref.pos() == null) {
            return;
        }
        ServerLevel level = server.getLevel(serverLevelKey(ref.dimensionId()));
        if (level == null || !level.hasChunkAt(ref.pos())) {
            return;
        }
        BlockState state = level.getBlockState(ref.pos());
        if (!state.is(ModBlocks.PALLET.get())) {
            return;
        }
        BlockPos masterPos = PalletBlock.getMasterPos(state, ref.pos());
        if (!level.getBlockState(masterPos).is(ModBlocks.PALLET.get())) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(masterPos);
        if (!(blockEntity instanceof PalletBlockEntity pallet)) {
            return;
        }
        if (enabled) {
            pallet.setDeliveryLabel(shopName);
        } else {
            pallet.clearDeliveryLabel();
            removeDeliveryPalletHologramsAt(level, masterPos);
        }
    }

    private static void syncDeliveryPalletHolograms(MinecraftServer server, Map<String, String> desiredLabels) {
        if (server == null) {
            return;
        }
        Map<String, String> safeDesired = desiredLabels == null ? Map.of() : desiredLabels;
        Set<String> desiredRefs = new HashSet<>();
        for (Map.Entry<String, String> entry : safeDesired.entrySet()) {
            PalletRef ref = decodeOrderPalletRef(entry.getKey());
            if (ref == null || ref.pos() == null) {
                continue;
            }
            ServerLevel level = server.getLevel(serverLevelKey(ref.dimensionId()));
            if (level == null || !level.hasChunkAt(ref.pos())) {
                continue;
            }
            BlockState state = level.getBlockState(ref.pos());
            if (!state.is(ModBlocks.PALLET.get())) {
                continue;
            }
            BlockPos masterPos = PalletBlock.getMasterPos(state, ref.pos());
            if (!level.getBlockState(masterPos).is(ModBlocks.PALLET.get())) {
                continue;
            }
            String canonicalRef = normalizeOrderPalletRef(encodeOrderPalletRef(
                    level.dimension().location().toString(),
                    masterPos
            ));
            if (!canonicalRef.isBlank()) {
                desiredRefs.add(canonicalRef);
            }
            upsertDeliveryPalletHologram(level, masterPos, entry.getValue());
        }
        removeLoadedStaleDeliveryPalletHolograms(server, desiredRefs);
    }

    private static void upsertDeliveryPalletHologram(ServerLevel level, BlockPos masterPos, String shopName) {
        if (level == null || masterPos == null || !level.hasChunkAt(masterPos)) {
            return;
        }
        String encodedRef = encodeOrderPalletRef(level.dimension().location().toString(), masterPos);
        if (encodedRef.isBlank()) {
            return;
        }

        List<Display.TextDisplay> existing = findDeliveryPalletHolograms(level, masterPos);
        Display.TextDisplay display = null;
        for (Display.TextDisplay candidate : existing) {
            if (candidate == null || !candidate.isAlive()) {
                continue;
            }
            String candidateRef = normalizeOrderPalletRef(deliveryPalletHologramRef(candidate));
            if (!encodedRef.equals(candidateRef)) {
                candidate.discard();
                continue;
            }
            if (display == null) {
                display = candidate;
            } else {
                candidate.discard();
            }
        }

        if (display == null) {
            display = EntityType.TEXT_DISPLAY.create(level);
            if (display == null) {
                return;
            }
            retagDeliveryPalletHologram(display, encodedRef);
            configureDeliveryPalletHologram(display, level, masterPos, shopName);
            retagDeliveryPalletHologram(display, encodedRef);
            level.addFreshEntity(display);
            return;
        }

        retagDeliveryPalletHologram(display, encodedRef);
        configureDeliveryPalletHologram(display, level, masterPos, shopName);
        retagDeliveryPalletHologram(display, encodedRef);
    }

    private static void configureDeliveryPalletHologram(Display.TextDisplay display,
                                                        ServerLevel level,
                                                        BlockPos masterPos,
                                                        String shopName) {
        if (display == null || level == null || masterPos == null) {
            return;
        }
        String safeShop = sanitizeTokenText(shopName);
        if (safeShop.isBlank()) {
            safeShop = "Shop";
        }
        double yOffset = computeDeliveryPalletHologramYOffset(level, masterPos);
        double x = masterPos.getX() + 0.5D;
        double y = masterPos.getY() + yOffset;
        double z = masterPos.getZ() + 0.5D;
        display.setPos(x, y, z);

        CompoundTag tag = display.saveWithoutId(new CompoundTag());
        tag.putString("text", Component.Serializer.toJson(deliveryPalletHologramText(safeShop, masterPos), level.registryAccess()));
        tag.putInt("line_width", 220);
        tag.putByte("text_opacity", (byte) 0xFF);
        tag.putInt("background", DELIVERY_PALLET_HOLOGRAM_BACKGROUND);
        tag.putBoolean("shadow", true);
        tag.putBoolean("see_through", false);
        tag.putBoolean("default_background", false);
        tag.putString("alignment", "center");
        tag.putString("billboard", "center");
        tag.putFloat("view_range", 96.0F);
        tag.putFloat("shadow_radius", 0.0F);
        tag.putFloat("shadow_strength", 0.0F);
        tag.putFloat("width", 3.0F);
        tag.putFloat("height", 1.25F);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);
        display.load(tag);
        display.setPos(x, y, z);
        display.setNoGravity(true);
        display.setSilent(true);
        display.setInvulnerable(true);
        display.setCustomName(UbsTranslations.literal("UBS Delivery Pallet Hologram"));
        display.setCustomNameVisible(false);
    }

    private static Component deliveryPalletHologramText(String shopName, BlockPos pos) {
        String safeShop = sanitizeTokenText(shopName);
        if (safeShop.isBlank()) {
            safeShop = "Shop";
        }
        String location = pos == null
                ? "(?, ?, ?)"
                : "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
        return Component.empty()
                .append(UbsTranslations.literal("Delivery Pallet").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("\n"))
                .append(Component.literal(safeShop).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("\n"))
                .append(Component.literal(location).withStyle(ChatFormatting.GRAY));
    }

    private static double computeDeliveryPalletHologramYOffset(ServerLevel level, BlockPos masterPos) {
        if (level == null || masterPos == null) {
            return DELIVERY_PALLET_HOLOGRAM_MIN_Y_OFFSET;
        }
        if (!(level.getBlockEntity(masterPos) instanceof PalletBlockEntity pallet)) {
            return DELIVERY_PALLET_HOLOGRAM_MIN_Y_OFFSET;
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
            return DELIVERY_PALLET_HOLOGRAM_MIN_Y_OFFSET;
        }
        double aboveBoxes = 0.5625D + (0.75D * (highestLayer + 1)) + DELIVERY_PALLET_HOLOGRAM_TOP_PADDING;
        return Math.max(DELIVERY_PALLET_HOLOGRAM_MIN_Y_OFFSET, aboveBoxes);
    }

    private static List<Display.TextDisplay> findDeliveryPalletHolograms(ServerLevel level, BlockPos masterPos) {
        if (level == null || masterPos == null) {
            return List.of();
        }
        AABB search = new AABB(masterPos).inflate(
                DELIVERY_PALLET_HOLOGRAM_SEARCH_RADIUS,
                5.0D,
                DELIVERY_PALLET_HOLOGRAM_SEARCH_RADIUS
        );
        return level.getEntitiesOfClass(
                Display.TextDisplay.class,
                search,
                ShopService::isDeliveryPalletHologram
        );
    }

    private static void removeDeliveryPalletHologramsAt(ServerLevel level, BlockPos masterPos) {
        for (Display.TextDisplay display : findDeliveryPalletHolograms(level, masterPos)) {
            if (display != null) {
                display.discard();
            }
        }
    }

    private static void removeLoadedStaleDeliveryPalletHolograms(MinecraftServer server, Set<String> desiredRefs) {
        if (server == null) {
            return;
        }
        Set<String> safeDesired = desiredRefs == null ? Set.of() : desiredRefs;
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (level == null) {
                continue;
            }
            for (ServerPlayer player : level.players()) {
                if (player == null) {
                    continue;
                }
                AABB area = player.getBoundingBox().inflate(
                        DELIVERY_PALLET_HOLOGRAM_PRUNE_RANGE,
                        DELIVERY_PALLET_HOLOGRAM_PRUNE_RANGE,
                        DELIVERY_PALLET_HOLOGRAM_PRUNE_RANGE
                );
                for (Display.TextDisplay display : level.getEntitiesOfClass(
                        Display.TextDisplay.class,
                        area,
                        ShopService::isDeliveryPalletHologram
                )) {
                    if (display == null || !seen.add(display.getUUID())) {
                        continue;
                    }
                    String ref = normalizeOrderPalletRef(deliveryPalletHologramRef(display));
                    if (ref.isBlank() || !safeDesired.contains(ref)) {
                        display.discard();
                    }
                }
            }
        }
    }

    private static boolean isDeliveryPalletHologram(Entity entity) {
        return entity != null && entity.getTags().contains(DELIVERY_PALLET_HOLOGRAM_TAG);
    }

    private static void retagDeliveryPalletHologram(Entity entity, String encodedRef) {
        if (entity == null || encodedRef == null || encodedRef.isBlank()) {
            return;
        }
        entity.addTag(DELIVERY_PALLET_HOLOGRAM_TAG);
        for (String tag : new HashSet<>(entity.getTags())) {
            if (tag != null && tag.startsWith(DELIVERY_PALLET_HOLOGRAM_REF_TAG_PREFIX)) {
                entity.removeTag(tag);
            }
        }
        entity.addTag(DELIVERY_PALLET_HOLOGRAM_REF_TAG_PREFIX + encodedRef);
    }

    private static String deliveryPalletHologramRef(Entity entity) {
        if (entity == null) {
            return "";
        }
        for (String tag : entity.getTags()) {
            if (tag != null && tag.startsWith(DELIVERY_PALLET_HOLOGRAM_REF_TAG_PREFIX)) {
                return tag.substring(DELIVERY_PALLET_HOLOGRAM_REF_TAG_PREFIX.length());
            }
        }
        return "";
    }

    private static void tickDeliveryPalletHoverHints(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        long gameTime = overworld.getGameTime();
        if ((gameTime % DELIVERY_PALLET_HOVER_UPDATE_INTERVAL_TICKS) != 0L) {
            return;
        }
        CentralBank centralBank = BankManager.getCentralBank(server);
        if (centralBank == null) {
            return;
        }

        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }
            UUID playerId = player.getUUID();
            online.add(playerId);

            // Do not override active claim/locate action bars.
            if (hasAnyClaimToolSession(playerId) || hasStockroomLocateSession(playerId)) {
                clearDeliveryPalletHoverMessage(player, playerId);
                continue;
            }

            String signature = "";
            Component hoverMessage = null;
            CompoundTag hoverShop = null;
            String hoverShopName = "";
            String hoverDimensionId = "";
            BlockPos hoverMasterPos = null;
            HitResult hit = player.pick(6.0D, 0.0F, false);
            if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
                BlockPos hitPos = blockHit.getBlockPos();
                BlockState hitState = player.serverLevel().getBlockState(hitPos);
                if (hitState.is(ModBlocks.PALLET.get())) {
                    BlockPos masterPos = PalletBlock.getMasterPos(hitState, hitPos);
                    String dim = player.serverLevel().dimension().location().toString();
                    FoundAssignedPallet found = findAssignedPalletShop(centralBank, dim, masterPos);
                    if (found != null && found.shopTag() != null) {
                        String shopName = sanitizeTokenText(found.shopTag().getString(TAG_NAME));
                        if (shopName.isBlank()) {
                            shopName = "Shop";
                        }
                        hoverShop = found.shopTag();
                        hoverShopName = shopName;
                        hoverDimensionId = dim;
                        hoverMasterPos = masterPos;
                        signature = normalizedDim(dim) + "|" + masterPos.getX() + "|" + masterPos.getY() + "|" + masterPos.getZ() + "|" + shopName;
                        hoverMessage = buildDeliveryPalletHoverMessage(shopName, masterPos);
                    }
                }
            }

            String previous = DELIVERY_PALLET_HOVER_CACHE.put(playerId, signature);
            if (!signature.isBlank()) {
                // Refresh periodically so the styled hover stays visible while aiming.
                if (!signature.equals(previous) || (gameTime % 20L) == 0L) {
                    refreshDeliveryPalletLabelFromKnownPosition(
                            server,
                            player,
                            centralBank,
                            hoverShop,
                            hoverDimensionId,
                            hoverMasterPos,
                            hoverShopName
                    );
                    player.displayClientMessage(hoverMessage, true);
                }
            } else {
                clearDeliveryPalletHoverMessage(player, playerId);
            }
        }

        DELIVERY_PALLET_HOVER_CACHE.keySet().removeIf(id -> !online.contains(id));
    }

    private static void refreshDeliveryPalletLabelFromKnownPosition(MinecraftServer server,
                                                                    ServerPlayer player,
                                                                    CentralBank centralBank,
                                                                    CompoundTag shop,
                                                                    String dimensionId,
                                                                    BlockPos masterPos,
                                                                    String shopName) {
        if (server == null || player == null || centralBank == null || shop == null || masterPos == null) {
            return;
        }
        ServerLevel level = server.getLevel(serverLevelKey(dimensionId));
        if (level == null || !level.getBlockState(masterPos).is(ModBlocks.PALLET.get())) {
            return;
        }
        String dim = normalizedDim(dimensionId);
        String key = resolvePalletAssignmentKey(level, masterPos, false);
        if (key.isBlank()) {
            key = encodeOrderPalletRef(dim, masterPos);
        }
        if (updateAssignedPalletLastKnownPosition(shop, key, dim, masterPos)) {
            saveShopTag(centralBank, shop);
        }

        String safeShop = sanitizeTokenText(shopName);
        if (safeShop.isBlank()) {
            safeShop = "Shop";
        }
        PalletRef ref = new PalletRef(dim, masterPos);
        String encoded = encodeOrderPalletRef(dim, masterPos);
        if (!encoded.isBlank()) {
            DELIVERY_PALLET_LABEL_CACHE.put(encoded, safeShop);
        }
        applyDeliveryPalletLabelToWorld(server, ref, true, safeShop);

        Map<String, String> payloadSource = DELIVERY_PALLET_LABEL_CACHE.isEmpty()
                ? Map.of(encoded, safeShop)
                : DELIVERY_PALLET_LABEL_CACHE;
        List<DeliveryPalletLabelSummary> labels = buildDeliveryPalletLabelsForPlayer(player, dim, payloadSource);
        DELIVERY_PALLET_LABEL_PAYLOAD_CACHE.remove(player.getUUID());
        pushDeliveryPalletLabels(player, new DeliveryPalletLabelsPayload(dim, labels));
    }

    private static void clearDeliveryPalletHoverMessage(ServerPlayer player, UUID playerId) {
        if (player == null || playerId == null) {
            return;
        }
        String previous = DELIVERY_PALLET_HOVER_CACHE.get(playerId);
        if (previous != null && !previous.isBlank()) {
            player.displayClientMessage(Component.literal(""), true);
        }
        DELIVERY_PALLET_HOVER_CACHE.put(playerId, "");
    }

    private static Component buildDeliveryPalletHoverMessage(String shopName, BlockPos palletPos) {
        String safeShop = (shopName == null || shopName.isBlank()) ? "Shop" : shopName;
        return Component.literal("◆ ").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                .append(UbsTranslations.literal("Delivery Pallet").withStyle(ChatFormatting.GREEN))
                .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(safeShop).withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  |  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(UbsTranslations.literal("Pallet ").withStyle(ChatFormatting.WHITE))
                .append(Component.literal("(" + palletPos.getX() + ", " + palletPos.getY() + ", " + palletPos.getZ() + ")")
                        .withStyle(ChatFormatting.YELLOW));
    }

    private static void sendStockroomLocateActionBar(ServerPlayer player, StockroomLocateSession session) {
        if (player == null || session == null) {
            return;
        }
        String inventoryLabel = session.inventoryLabel() == null || session.inventoryLabel().isBlank()
                ? "Inventory"
                : session.inventoryLabel().trim();
        String slotLabel = isPalletLocateSlot(session.slot())
                ? inventoryLabel
                : inventoryLabel + " Slot " + session.slot();
        String traceTitle = isPalletLocateSlot(session.slot()) ? "Pallet Trace" : "Stockroom Locate";
        player.displayClientMessage(
                UbsTranslations.literal("§b" + traceTitle + " §7| §fSneak + Right-click = Cancel §7| §f"
                        + normalizedDim(session.dimensionId()) + " ("
                        + session.pos().getX() + ", " + session.pos().getY() + ", " + session.pos().getZ()
                        + ") §7| §f" + slotLabel),
                true
        );
    }

    private static void pushStockroomLocateRender(ServerPlayer player, StockroomLocateSession session) {
        if (player == null || session == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, new StockroomLocateRenderPayload(
                true,
                normalizedDim(session.dimensionId()),
                session.pos().getX(),
                session.pos().getY(),
                session.pos().getZ(),
                Math.max(1, session.slot())
        ));
    }

    private static void clearStockroomLocateRender(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, StockroomLocateRenderPayload.inactive());
    }

    private static void renderRegionsForPlayer(ServerPlayer player,
                                               ListTag regions,
                                               String playerDim,
                                               ParticleOptions particle,
                                               int[] budget) {
        if (player == null || regions == null || regions.isEmpty() || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        BlockPos viewer = player.blockPosition();
        for (Tag tag : regions) {
            if (!(tag instanceof CompoundTag region)) {
                continue;
            }
            if (!normalizedDim(region.getString(TAG_DIM)).equals(playerDim)) {
                continue;
            }
            int minX = Math.min(region.getInt(TAG_MIN_X), region.getInt(TAG_MAX_X));
            int minY = Math.min(region.getInt(TAG_MIN_Y), region.getInt(TAG_MAX_Y));
            int minZ = Math.min(region.getInt(TAG_MIN_Z), region.getInt(TAG_MAX_Z));
            int maxX = Math.max(region.getInt(TAG_MIN_X), region.getInt(TAG_MAX_X));
            int maxY = Math.max(region.getInt(TAG_MIN_Y), region.getInt(TAG_MAX_Y));
            int maxZ = Math.max(region.getInt(TAG_MIN_Z), region.getInt(TAG_MAX_Z));

            if (!regionCloseToPlayer(viewer, minX, minY, minZ, maxX, maxY, maxZ, 96)) {
                continue;
            }
            drawRegionOutlineForPlayer(player, minX, minY, minZ, maxX, maxY, maxZ, particle, budget);
            if (budget[0] <= 0) {
                return;
            }
        }
    }

    private static boolean regionCloseToPlayer(BlockPos viewer,
                                               int minX, int minY, int minZ,
                                               int maxX, int maxY, int maxZ,
                                               int margin) {
        if (viewer == null) {
            return false;
        }
        return viewer.getX() >= (minX - margin) && viewer.getX() <= (maxX + margin)
                && viewer.getY() >= (minY - margin) && viewer.getY() <= (maxY + margin)
                && viewer.getZ() >= (minZ - margin) && viewer.getZ() <= (maxZ + margin);
    }

    private static void drawRegionOutlineForPlayer(ServerPlayer player,
                                                   int minX, int minY, int minZ,
                                                   int maxX, int maxY, int maxZ,
                                                   ParticleOptions particle,
                                                   int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return;
        }
        int xSpan = Math.max(1, maxX - minX + 1);
        int ySpan = Math.max(1, maxY - minY + 1);
        int zSpan = Math.max(1, maxZ - minZ + 1);
        int hStep = Math.max(1, Math.max(xSpan, zSpan) / 24);
        int vStep = Math.max(1, ySpan / 12);
        double topY = maxY + 1.02D;

        for (int x = minX; x <= maxX; x += hStep) {
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, x + 0.5D, topY, maxZ + 0.5D, budget)) {
                return;
            }
        }
        for (int z = minZ; z <= maxZ; z += hStep) {
            if (!sendOverlayParticle(player, particle, minX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, topY, z + 0.5D, budget)) {
                return;
            }
        }
        for (int y = minY; y <= maxY; y += vStep) {
            double py = y + 0.08D;
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, minX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, minZ + 0.5D, budget)) {
                return;
            }
            if (!sendOverlayParticle(player, particle, maxX + 0.5D, py, maxZ + 0.5D, budget)) {
                return;
            }
        }
    }

    private static boolean sendOverlayParticle(ServerPlayer player,
                                               ParticleOptions particle,
                                               double x,
                                               double y,
                                               double z,
                                               int[] budget) {
        if (player == null || particle == null || budget == null || budget.length == 0 || budget[0] <= 0) {
            return false;
        }
        player.serverLevel().sendParticles(player, particle, true, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        budget[0]--;
        return budget[0] > 0;
    }

    private static boolean isInsideClaims(ListTag claims, String dimensionId, BlockPos pos) {
        if (claims == null || pos == null) {
            return false;
        }
        String dim = normalizedDim(dimensionId);
        for (Tag tag : claims) {
            if (!(tag instanceof CompoundTag claim)) {
                continue;
            }
            if (!normalizedDim(claim.getString(TAG_DIM)).equals(dim)) {
                continue;
            }
            int minX = regionMinX(claim);
            int maxX = regionMaxX(claim);
            int minY = regionMinY(claim);
            int maxY = regionMaxY(claim);
            int minZ = regionMinZ(claim);
            int maxZ = regionMaxZ(claim);
            if (pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ) {
                return true;
            }
        }
        return false;
    }

    private static boolean claimsOverlap(int aMinX, int aMinY, int aMinZ,
                                         int aMaxX, int aMaxY, int aMaxZ,
                                         int bMinX, int bMinY, int bMinZ,
                                         int bMaxX, int bMaxY, int bMaxZ) {
        int ax1 = Math.min(aMinX, aMaxX);
        int ay1 = Math.min(aMinY, aMaxY);
        int az1 = Math.min(aMinZ, aMaxZ);
        int ax2 = Math.max(aMinX, aMaxX);
        int ay2 = Math.max(aMinY, aMaxY);
        int az2 = Math.max(aMinZ, aMaxZ);

        int bx1 = Math.min(bMinX, bMaxX);
        int by1 = Math.min(bMinY, bMaxY);
        int bz1 = Math.min(bMinZ, bMaxZ);
        int bx2 = Math.max(bMinX, bMaxX);
        int by2 = Math.max(bMinY, bMaxY);
        int bz2 = Math.max(bMinZ, bMaxZ);

        boolean xOverlap = ax1 <= bx2 && bx1 <= ax2;
        boolean yOverlap = ay1 <= by2 && by1 <= ay2;
        boolean zOverlap = az1 <= bz2 && bz1 <= az2;
        return xOverlap && yOverlap && zOverlap;
    }

    private static boolean claimsTouchOrOverlap(int aMinX, int aMinY, int aMinZ,
                                                int aMaxX, int aMaxY, int aMaxZ,
                                                int bMinX, int bMinY, int bMinZ,
                                                int bMaxX, int bMaxY, int bMaxZ) {
        int ax1 = Math.min(aMinX, aMaxX);
        int ay1 = Math.min(aMinY, aMaxY);
        int az1 = Math.min(aMinZ, aMaxZ);
        int ax2 = Math.max(aMinX, aMaxX);
        int ay2 = Math.max(aMinY, aMaxY);
        int az2 = Math.max(aMinZ, aMaxZ);

        int bx1 = Math.min(bMinX, bMaxX);
        int by1 = Math.min(bMinY, bMaxY);
        int bz1 = Math.min(bMinZ, bMaxZ);
        int bx2 = Math.max(bMinX, bMaxX);
        int by2 = Math.max(bMinY, bMaxY);
        int bz2 = Math.max(bMinZ, bMaxZ);

        boolean xConnected = ax1 <= (bx2 + 1) && bx1 <= (ax2 + 1);
        boolean yConnected = ay1 <= (by2 + 1) && by1 <= (ay2 + 1);
        boolean zConnected = az1 <= (bz2 + 1) && bz1 <= (az2 + 1);
        return xConnected && yConnected && zConnected;
    }

    private static CompoundTag getOrCreateRoot(CompoundTag centralMeta) {
        if (centralMeta == null) {
            return new CompoundTag();
        }
        if (centralMeta.contains(TAG_ROOT, Tag.TAG_COMPOUND)) {
            CompoundTag root = centralMeta.getCompound(TAG_ROOT);
            if (!root.contains(TAG_SHOPS, Tag.TAG_LIST)) {
                root.put(TAG_SHOPS, new ListTag());
            }
            return root;
        }
        CompoundTag root = new CompoundTag();
        root.put(TAG_SHOPS, new ListTag());
        return root;
    }

    private static long targetForLevel(int level) {
        int safeLevel = Math.max(1, level);
        return 10_000L * safeLevel;
    }

    /**
     * Returns cumulative revenue required to finish levels [1..level].
     */
    private static long cumulativeTargetForLevel(int level) {
        int safeLevel = Math.max(0, level);
        long cumulative = 0L;
        for (int step = 1; step <= safeLevel; step++) {
            cumulative = safeAdd(cumulative, targetForLevel(step));
        }
        return cumulative;
    }

    /**
     * Returns revenue floor required to enter a given level.
     * Level 1 always starts at zero.
     */
    private static long requiredRevenueForLevel(int level) {
        int safeLevel = Math.max(1, level);
        if (safeLevel <= 1) {
            return 0L;
        }
        return cumulativeTargetForLevel(safeLevel - 1);
    }

    private static void appendDailySalesHistory(CompoundTag shopTag, long amountDollars) {
        if (shopTag == null || amountDollars <= 0L) {
            return;
        }
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        ListTag history = shopTag.getList(TAG_DAILY_SALES_HISTORY, Tag.TAG_COMPOUND);
        boolean updated = false;
        for (int i = 0; i < history.size(); i++) {
            CompoundTag entry = history.getCompound(i);
            if (entry.getLong(TAG_DAY) == today) {
                long current = Math.max(0L, entry.getLong(TAG_AMOUNT));
                entry.putLong(TAG_AMOUNT, safeAdd(current, amountDollars));
                history.set(i, entry);
                updated = true;
                break;
            }
        }
        if (!updated) {
            CompoundTag fresh = new CompoundTag();
            fresh.putLong(TAG_DAY, today);
            fresh.putLong(TAG_AMOUNT, amountDollars);
            history.add(fresh);
        }
        while (history.size() > 31) {
            history.remove(0);
        }
        shopTag.put(TAG_DAILY_SALES_HISTORY, history);
    }

    private static List<Long> buildSevenDayTrend(CompoundTag shopTag, long fallbackRevenueDollars) {
        List<Long> trend = new ArrayList<>();
        long today = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        Map<Long, Long> byDay = new HashMap<>();
        if (shopTag != null && shopTag.contains(TAG_DAILY_SALES_HISTORY, Tag.TAG_LIST)) {
            ListTag history = shopTag.getList(TAG_DAILY_SALES_HISTORY, Tag.TAG_COMPOUND);
            for (Tag tag : history) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                long day = entry.getLong(TAG_DAY);
                long amount = Math.max(0L, entry.getLong(TAG_AMOUNT));
                byDay.put(day, safeAdd(byDay.getOrDefault(day, 0L), amount));
            }
        }
        boolean hasRealData = !byDay.isEmpty();
        for (int i = 6; i >= 0; i--) {
            long day = today - i;
            long value = byDay.getOrDefault(day, 0L);
            if (!hasRealData && fallbackRevenueDollars > 0L) {
                // Fallback synthetic split so charts remain readable before enough history accumulates.
                double weight = 0.7D + (0.05D * (6 - i));
                value = Math.max(0L, Math.round((fallbackRevenueDollars / 7.0D) * weight));
            }
            trend.add(Math.max(0L, value));
        }
        return trend;
    }

    private static String classifyShelfItemCategory(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() == null) {
            return "Misc";
        }
        String itemName = stack.getItem().toString().toLowerCase(Locale.ROOT);
        if (itemName.contains("food") || itemName.contains("apple")
                || itemName.contains("bread") || itemName.contains("meat")) {
            return "Food";
        }
        if (itemName.contains("drink") || itemName.contains("bottle") || itemName.contains("potion")) {
            return "Beverage";
        }
        if (itemName.contains("tool") || itemName.contains("pickaxe")
                || itemName.contains("axe") || itemName.contains("sword")) {
            return "Tools";
        }
        if (itemName.contains("armor") || itemName.contains("helmet")
                || itemName.contains("chestplate") || itemName.contains("leggings")
                || itemName.contains("boots")) {
            return "Equipment";
        }
        if (itemName.contains("block") || itemName.contains("plank")
                || itemName.contains("stone") || itemName.contains("glass")) {
            return "Building";
        }
        return "Misc";
    }

    private static String formatCategoryMetrics(Map<String, Long> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) {
            return "Food:0,Beverage:0,Tools:0,Misc:0";
        }
        Map<String, Long> merged = new LinkedHashMap<>();
        merged.put("Food", 0L);
        merged.put("Beverage", 0L);
        merged.put("Tools", 0L);
        merged.put("Misc", 0L);
        for (Map.Entry<String, Long> entry : rawValues.entrySet()) {
            if (entry == null) {
                continue;
            }
            String key = entry.getKey() == null ? "Misc" : entry.getKey().trim();
            long amount = Math.max(0L, entry.getValue() == null ? 0L : entry.getValue());
            if (!merged.containsKey(key)) {
                key = "Misc";
            }
            merged.put(key, safeAdd(merged.getOrDefault(key, 0L), amount));
        }
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Long> entry : merged.entrySet()) {
            if (!first) {
                out.append(',');
            }
            out.append(entry.getKey()).append(':').append(Math.max(0L, entry.getValue()));
            first = false;
        }
        return out.toString();
    }

    private static String normalizeName(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String normalizeShopType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return SHOP_TYPE_INDEPENDENT;
        }
        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        for (String known : SHOP_TYPES) {
            if (known.equalsIgnoreCase(normalized)) {
                return known;
            }
        }
        return SHOP_TYPE_INDEPENDENT;
    }

    private static String normalizedDim(String dim) {
        if (dim == null || dim.isBlank()) {
            return "minecraft:overworld";
        }
        return dim.trim().toLowerCase(Locale.ROOT);
    }

    private static net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> serverLevelKey(String dimId) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(normalizedDim(dimId));
        if (id == null) {
            id = net.minecraft.world.level.Level.OVERWORLD.location();
        }
        return net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.createValueKey(
                net.austizz.ultimatebankingsystem.util.RegistryKeysCompat.DIMENSION_REGISTRY_KEY,
                id
        );
    }

    private static String stockroomPosLabel(CompoundTag stockroom) {
        if (stockroom == null) {
            return "-";
        }
        return normalizedDim(stockroom.getString(TAG_DIM))
                + " (" + stockroom.getInt(TAG_X)
                + ", " + stockroom.getInt(TAG_Y)
                + ", " + stockroom.getInt(TAG_Z) + ")";
    }

    private static String terminalPosLabel(CompoundTag terminal) {
        if (terminal == null) {
            return "-";
        }
        return normalizedDim(terminal.getString(TAG_DIM))
                + " (" + terminal.getInt(TAG_X)
                + ", " + terminal.getInt(TAG_Y)
                + ", " + terminal.getInt(TAG_Z) + ")";
    }

    private static CompoundTag resolveCheckoutTerminalTagForCashier(CompoundTag shopTag, UUID cashierId) {
        if (shopTag == null) {
            return null;
        }
        CompoundTag linked = resolveLinkedTerminalTag(shopTag, cashierId);
        if (linked != null) {
            return linked;
        }
        if (shopTag.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            return shopTag.getCompound(TAG_CHECKOUT_TERMINAL);
        }
        return null;
    }

    private static CompoundTag resolveLinkedTerminalTag(CompoundTag shopTag, UUID cashierId) {
        if (shopTag == null || cashierId == null) {
            return null;
        }
        ListTag linked = shopTag.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND);
        for (Tag tag : linked) {
            if (!(tag instanceof CompoundTag entry) || !entry.contains(TAG_CASHIER_ID)) {
                continue;
            }
            if (cashierId.equals(entry.getUUID(TAG_CASHIER_ID))) {
                return entry.copy();
            }
        }
        return null;
    }

    private static void setLinkedTerminalTag(CompoundTag shopTag,
                                             UUID cashierId,
                                             String dimensionId,
                                             BlockPos terminalPos) {
        if (shopTag == null || cashierId == null || terminalPos == null) {
            return;
        }
        String dim = normalizedDim(dimensionId);
        ListTag linked = shopTag.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND);
        CompoundTag newEntry = new CompoundTag();
        newEntry.putUUID(TAG_CASHIER_ID, cashierId);
        newEntry.putString(TAG_DIM, dim);
        newEntry.putInt(TAG_X, terminalPos.getX());
        newEntry.putInt(TAG_Y, terminalPos.getY());
        newEntry.putInt(TAG_Z, terminalPos.getZ());

        ListTag next = new ListTag();
        boolean replaced = false;
        for (Tag tag : linked) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            if (entry.contains(TAG_CASHIER_ID) && cashierId.equals(entry.getUUID(TAG_CASHIER_ID))) {
                // Replace all stale links for this cashier with a single fresh link.
                if (!replaced) {
                    next.add(newEntry.copy());
                    replaced = true;
                }
            } else {
                next.add(entry.copy());
            }
        }
        if (!replaced) {
            next.add(newEntry.copy());
        }
        shopTag.put(TAG_CASHIER_TERMINALS, next);
        if (!shopTag.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            CompoundTag fallback = new CompoundTag();
            fallback.putString(TAG_DIM, dim);
            fallback.putInt(TAG_X, terminalPos.getX());
            fallback.putInt(TAG_Y, terminalPos.getY());
            fallback.putInt(TAG_Z, terminalPos.getZ());
            shopTag.put(TAG_CHECKOUT_TERMINAL, fallback);
        }
    }

    private static void removeLinkedTerminalTag(CompoundTag shopTag, UUID cashierId) {
        if (shopTag == null || cashierId == null) {
            return;
        }
        ListTag linked = shopTag.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND);
        if (linked.isEmpty()) {
            return;
        }
        ListTag next = new ListTag();
        for (Tag tag : linked) {
            if (!(tag instanceof CompoundTag entry)) {
                continue;
            }
            if (entry.contains(TAG_CASHIER_ID) && cashierId.equals(entry.getUUID(TAG_CASHIER_ID))) {
                continue;
            }
            next.add(entry.copy());
        }
        shopTag.put(TAG_CASHIER_TERMINALS, next);
    }

    private static List<BankTellerEntity> collectCashierEntitiesInClaims(MinecraftServer server,
                                                                         ListTag claims,
                                                                         UUID ownerId,
                                                                         UUID shopId) {
        if (server == null || claims == null || claims.isEmpty() || ownerId == null) {
            return List.of();
        }
        List<BankTellerEntity> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (level == null) {
                continue;
            }
            String levelDim = normalizedDim(level.dimension().location().toString());
            for (Tag tag : claims) {
                if (!(tag instanceof CompoundTag claim)) {
                    continue;
                }
                if (!normalizedDim(claim.getString(TAG_DIM)).equals(levelDim)) {
                    continue;
                }
                AABB bounds = claimEntitySearchBounds(claim, level);
                if (bounds == null) {
                    continue;
                }
                for (BankTellerEntity teller : level.getEntitiesOfClass(
                        BankTellerEntity.class,
                        bounds,
                        BankTellerEntity::isCashier
                )) {
                    if (teller == null || !seen.add(teller.getUUID()) || !ownerId.equals(teller.getOwnerUUID())) {
                        continue;
                    }
                    UUID tellerShopId = teller.getShopId();
                    if (shopId != null && tellerShopId != null && !shopId.equals(tellerShopId)) {
                        continue;
                    }
                    if (!isInsideClaims(claims, levelDim, teller.blockPosition())) {
                        continue;
                    }
                    out.add(teller);
                }
            }
        }
        return out;
    }

    private static AABB claimEntitySearchBounds(CompoundTag claim, ServerLevel level) {
        if (claim == null || level == null) {
            return null;
        }
        int minX = regionMinX(claim);
        int maxX = regionMaxX(claim);
        int minY = Math.max(regionMinY(claim), level.getMinBuildHeight() - 1);
        int maxY = Math.min(regionMaxY(claim), level.getMaxBuildHeight() + 1);
        int minZ = regionMinZ(claim);
        int maxZ = regionMaxZ(claim);
        if (maxX < minX || maxY < minY || maxZ < minZ) {
            return null;
        }
        return new AABB(minX, minY, minZ, maxX + 1.0D, maxY + 1.0D, maxZ + 1.0D);
    }

    private static int pruneCashiersOutsideClaims(CompoundTag shopTag, List<BankTellerEntity> candidates) {
        if (shopTag == null || candidates == null || candidates.isEmpty()
                || !shopTag.contains(TAG_OWNER) || !shopTag.contains(TAG_ID)) {
            return 0;
        }
        UUID ownerId = shopTag.getUUID(TAG_OWNER);
        UUID shopId = shopTag.getUUID(TAG_ID);
        ListTag claims = shopTag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        int removed = 0;
        Set<UUID> seen = new HashSet<>();
        for (BankTellerEntity teller : candidates) {
            if (teller == null || !seen.add(teller.getUUID()) || !ownerId.equals(teller.getOwnerUUID())) {
                continue;
            }
            UUID tellerShopId = teller.getShopId();
            if (tellerShopId != null && !shopId.equals(tellerShopId)) {
                continue;
            }
            BlockPos pos = teller.blockPosition();
            String dim = teller.level().dimension().location().toString();
            if (isInsideClaims(claims, dim, pos)) {
                if (tellerShopId == null) {
                    teller.setShopId(shopId);
                }
                continue;
            }
            ShopCashierInteractionManager.cancelForCashier(
                    teller.getUUID(),
                    "Cashier removed because its plot claim was removed."
            );
            teller.discard();
            // Drop the discarded cashier's terminal link too, or a link whose terminal
            // is still inside the remaining claims would survive as a ghost employee.
            removeLinkedTerminalTag(shopTag, teller.getUUID());
            removed++;
        }
        return removed;
    }

    private static int pruneTerminalLinksOutsideClaims(CompoundTag shopTag) {
        if (shopTag == null) {
            return 0;
        }
        int removed = 0;
        ListTag claims = shopTag.getList(TAG_CLAIMS, Tag.TAG_COMPOUND);
        ListTag linked = shopTag.getList(TAG_CASHIER_TERMINALS, Tag.TAG_COMPOUND);
        if (!linked.isEmpty()) {
            ListTag next = new ListTag();
            for (Tag tag : linked) {
                if (!(tag instanceof CompoundTag entry)) {
                    continue;
                }
                if (!entry.contains(TAG_CASHIER_ID)) {
                    removed++;
                    continue;
                }
                String dim = entry.getString(TAG_DIM);
                BlockPos pos = new BlockPos(entry.getInt(TAG_X), entry.getInt(TAG_Y), entry.getInt(TAG_Z));
                if (!isInsideClaims(claims, dim, pos)) {
                    removed++;
                    continue;
                }
                next.add(entry.copy());
            }
            shopTag.put(TAG_CASHIER_TERMINALS, next);
        }

        if (shopTag.contains(TAG_CHECKOUT_TERMINAL, Tag.TAG_COMPOUND)) {
            CompoundTag checkout = shopTag.getCompound(TAG_CHECKOUT_TERMINAL);
            BlockPos checkoutPos = new BlockPos(checkout.getInt(TAG_X), checkout.getInt(TAG_Y), checkout.getInt(TAG_Z));
            if (!isInsideClaims(claims, checkout.getString(TAG_DIM), checkoutPos)) {
                shopTag.remove(TAG_CHECKOUT_TERMINAL);
                removed++;
            }
        }
        return removed;
    }

    private static CashierSummary selectCashier(List<CashierSummary> cashiers, String selection) {
        if (cashiers == null || cashiers.isEmpty()) {
            return null;
        }
        String raw = selection == null ? "" : selection.trim();
        if (raw.isBlank()) {
            return cashiers.get(0);
        }
        try {
            int idx = Integer.parseInt(raw);
            if (idx >= 1 && idx <= cashiers.size()) {
                return cashiers.get(idx - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            UUID id = UUID.fromString(raw);
            for (CashierSummary summary : cashiers) {
                if (id.equals(summary.employeeId()) || id.equals(summary.cashierId())) {
                    return summary;
                }
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static CashierTerminalSelection activeCashierTerminalSelection(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        CashierTerminalSelection selection = CASHIER_TERMINAL_SELECTIONS.get(player.getUUID());
        if (selection == null) {
            return null;
        }
        long now = player.serverLevel().getGameTime();
        if (Math.abs(now - selection.startedTick()) > CASHIER_TERMINAL_SELECTION_TIMEOUT_TICKS) {
            CASHIER_TERMINAL_SELECTIONS.remove(player.getUUID());
            return null;
        }
        return selection;
    }

    private static BankTellerEntity findCashierById(MinecraftServer server, UUID cashierId) {
        if (server == null || cashierId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(cashierId);
            if (entity instanceof BankTellerEntity teller && teller.isCashier()) {
                return teller;
            }
        }
        return null;
    }

    private static int[] readCashVaultCounts(CompoundTag shopTag) {
        int size = DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length;
        int[] counts = new int[size];
        if (shopTag == null || !shopTag.contains(TAG_CASH_VAULT_COUNTS, Tag.TAG_INT_ARRAY)) {
            return counts;
        }
        int[] raw = shopTag.getIntArray(TAG_CASH_VAULT_COUNTS);
        for (int i = 0; i < size && i < raw.length; i++) {
            counts[i] = Math.max(0, raw[i]);
        }
        return counts;
    }

    private static long computeVaultTotalCents(int[] counts) {
        if (counts == null || counts.length == 0) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length && i < counts.length; i++) {
            int amount = Math.max(0, counts[i]);
            if (amount <= 0) {
                continue;
            }
            long cents = (long) DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i] * (long) amount;
            total = safeAdd(total, Math.max(0L, cents));
        }
        return Math.max(0L, total);
    }

    private static long parseMoneyToCents(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String cleaned = raw.trim().replace("$", "").replace(",", "");
        try {
            BigDecimal dollars = new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_EVEN);
            if (dollars.compareTo(BigDecimal.ZERO) <= 0) {
                return 0L;
            }
            return dollars.movePointRight(2).longValue();
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static int[] parseVaultWithdrawPlan(String raw) {
        int[] requested = new int[DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length];
        if (raw == null || raw.isBlank()) {
            return requested;
        }
        String trimmed = raw.trim();
        if (trimmed.contains(":")) {
            String[] pairs = trimmed.split(",");
            for (String pair : pairs) {
                if (pair == null || pair.isBlank()) {
                    continue;
                }
                int sep = pair.indexOf(':');
                if (sep <= 0 || sep >= pair.length() - 1) {
                    return null;
                }
                long denom;
                int count;
                try {
                    denom = Long.parseLong(pair.substring(0, sep).trim());
                    count = Math.max(0, Integer.parseInt(pair.substring(sep + 1).trim()));
                } catch (NumberFormatException ex) {
                    return null;
                }
                int idx = -1;
                for (int i = 0; i < DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length; i++) {
                    if (DollarBills.CASH_DENOMINATIONS_CENTS_DESC[i] == denom) {
                        idx = i;
                        break;
                    }
                }
                if (idx < 0) {
                    return null;
                }
                requested[idx] = count;
            }
            return requested;
        }

        String[] tokens = trimmed.split(",");
        if (tokens.length != DollarBills.CASH_DENOMINATIONS_CENTS_DESC.length) {
            return null;
        }
        for (int i = 0; i < tokens.length; i++) {
            try {
                requested[i] = Math.max(0, Integer.parseInt(tokens[i].trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return requested;
    }

    private static ListTag getOrUpgradeStockroomClaims(CompoundTag shopTag) {
        if (shopTag == null) {
            return new ListTag();
        }
        ListTag stockroomClaims = shopTag.getList(TAG_STOCKROOM_CLAIMS, Tag.TAG_COMPOUND);
        if (!stockroomClaims.isEmpty()) {
            return stockroomClaims;
        }
        if (!shopTag.contains(TAG_STOCKROOM, Tag.TAG_COMPOUND)) {
            return stockroomClaims;
        }
        CompoundTag legacy = shopTag.getCompound(TAG_STOCKROOM);
        String dim = normalizedDim(legacy.getString(TAG_DIM));
        int x = legacy.getInt(TAG_X);
        int y = legacy.getInt(TAG_Y);
        int z = legacy.getInt(TAG_Z);
        stockroomClaims.add(buildRegionTag(dim, x, y, z, x, y, z));
        shopTag.remove(TAG_STOCKROOM);
        shopTag.put(TAG_STOCKROOM_CLAIMS, stockroomClaims);
        return stockroomClaims;
    }

    private static String buildStockPreviewCacheKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "empty";
        }
        String itemId = String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        CompoundTag stackTag = ItemStackDataCompat.getCustomData(stack);
        return itemId + "|" + (stackTag == null ? "" : stackTag.toString());
    }

    private static SlotStockTargets getSlotStockTargets(CompoundTag shopTag, String slotKey) {
        CompoundTag meta = findOrCreateSlotMeta(shopTag, slotKey, false);
        if (meta == null) {
            return new SlotStockTargets(DEFAULT_MIN_STOCK_TARGET, TARGET_STOCK_PER_SLOT);
        }
        int min = Mth.clamp(meta.getInt(TAG_SLOT_MIN_STOCK), 0, MAX_SLOT_TARGET);
        int max = Mth.clamp(meta.getInt(TAG_SLOT_MAX_STOCK), 0, MAX_SLOT_TARGET);
        if (max < min) {
            max = min;
        }
        return new SlotStockTargets(min, max);
    }

    private static void setSlotStockTargets(CompoundTag shopTag, String slotKey, int min, int max) {
        if (shopTag == null || slotKey == null || slotKey.isBlank()) {
            return;
        }
        CompoundTag meta = findOrCreateSlotMeta(shopTag, slotKey, true);
        if (meta == null) {
            return;
        }
        meta.putString(TAG_SLOT_KEY, slotKey);
        meta.putInt(TAG_SLOT_MIN_STOCK, Mth.clamp(min, 0, MAX_SLOT_TARGET));
        meta.putInt(TAG_SLOT_MAX_STOCK, Mth.clamp(Math.max(min, max), 0, MAX_SLOT_TARGET));
    }

    private static void removeSlotMeta(CompoundTag shopTag, String slotKey) {
        if (shopTag == null || slotKey == null || slotKey.isBlank()) {
            return;
        }
        ListTag list = shopTag.getList(TAG_SHELF_SLOT_META, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return;
        }
        ListTag next = new ListTag();
        boolean changed = false;
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag meta)) {
                continue;
            }
            if (slotKey.equalsIgnoreCase(meta.getString(TAG_SLOT_KEY))) {
                changed = true;
                continue;
            }
            next.add(meta.copy());
        }
        if (changed) {
            shopTag.put(TAG_SHELF_SLOT_META, next);
        }
    }

    private static long getSlotLastSoldMillis(CompoundTag shopTag, String slotKey) {
        CompoundTag meta = findOrCreateSlotMeta(shopTag, slotKey, false);
        if (meta == null) {
            return 0L;
        }
        return Math.max(0L, meta.getLong(TAG_SLOT_LAST_SOLD_MILLIS));
    }

    private static double getSlotVelocityPerDay(CompoundTag shopTag, String slotKey, long nowMillis) {
        CompoundTag meta = findOrCreateSlotMeta(shopTag, slotKey, false);
        if (meta == null) {
            return 0.0D;
        }
        long nowDay = Math.floorDiv(Math.max(0L, nowMillis), 86_400_000L);
        long minDay = nowDay - Math.max(0, SALES_VELOCITY_WINDOW_DAYS - 1);
        ListTag sales = meta.getList(TAG_SLOT_DAILY_SALES, Tag.TAG_COMPOUND);
        long sum = 0L;
        for (Tag tag : sales) {
            if (!(tag instanceof CompoundTag dayTag)) {
                continue;
            }
            long day = dayTag.getLong(TAG_DAY);
            if (day < minDay || day > nowDay) {
                continue;
            }
            sum += Math.max(0L, dayTag.getLong(TAG_AMOUNT));
        }
        return Math.max(0.0D, sum / (double) Math.max(1, SALES_VELOCITY_WINDOW_DAYS));
    }

    private static void addSlotDailySales(CompoundTag slotMeta, long nowMillis, int quantity) {
        if (slotMeta == null || quantity <= 0) {
            return;
        }
        long day = Math.floorDiv(Math.max(0L, nowMillis), 86_400_000L);
        long minKeepDay = day - Math.max(0, SALES_META_RETENTION_DAYS);
        ListTag sales = slotMeta.getList(TAG_SLOT_DAILY_SALES, Tag.TAG_COMPOUND);
        boolean merged = false;
        ListTag next = new ListTag();
        for (Tag tag : sales) {
            if (!(tag instanceof CompoundTag dayTag)) {
                continue;
            }
            long existingDay = dayTag.getLong(TAG_DAY);
            if (existingDay < minKeepDay) {
                continue;
            }
            CompoundTag copy = dayTag.copy();
            if (existingDay == day) {
                long current = Math.max(0L, copy.getLong(TAG_AMOUNT));
                copy.putLong(TAG_AMOUNT, Math.max(0L, current + quantity));
                merged = true;
            }
            next.add(copy);
        }
        if (!merged) {
            CompoundTag created = new CompoundTag();
            created.putLong(TAG_DAY, day);
            created.putLong(TAG_AMOUNT, Math.max(0, quantity));
            next.add(created);
        }
        slotMeta.put(TAG_SLOT_DAILY_SALES, next);
    }

    private static CompoundTag findOrCreateSlotMeta(CompoundTag shopTag, String slotKey, boolean create) {
        if (shopTag == null || slotKey == null || slotKey.isBlank()) {
            return null;
        }
        ListTag list = shopTag.getList(TAG_SHELF_SLOT_META, Tag.TAG_COMPOUND);
        for (Tag tag : list) {
            if (!(tag instanceof CompoundTag meta)) {
                continue;
            }
            if (slotKey.equalsIgnoreCase(meta.getString(TAG_SLOT_KEY))) {
                return meta;
            }
        }
        if (!create) {
            return null;
        }
        CompoundTag created = new CompoundTag();
        created.putString(TAG_SLOT_KEY, slotKey);
        created.putInt(TAG_SLOT_MIN_STOCK, DEFAULT_MIN_STOCK_TARGET);
        created.putInt(TAG_SLOT_MAX_STOCK, TARGET_STOCK_PER_SLOT);
        created.putLong(TAG_SLOT_LAST_SOLD_MILLIS, 0L);
        created.put(TAG_SLOT_DAILY_SALES, new ListTag());
        list.add(created);
        shopTag.put(TAG_SHELF_SLOT_META, list);
        return created;
    }

    private static String encodeShelfTarget(String dimensionId, BlockPos pos) {
        if (pos == null) {
            return "";
        }
        return normalizedDim(dimensionId)
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ();
    }

    private static String encodeStockroomLocateTarget(String dimensionId, BlockPos pos, int slot, String inventoryLabel) {
        if (pos == null) {
            return "";
        }
        String safeLabel = sanitizeTokenText(inventoryLabel).replace(";", ",");
        return normalizedDim(dimensionId)
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ()
                + ";" + Math.max(1, slot)
                + ";" + safeLabel;
    }

    private static int encodePalletLocateSlot(int column, int layer) {
        if (column < 0 || column >= PalletBlockEntity.COLUMNS || layer < 0 || layer >= PalletBlockEntity.LAYERS) {
            return 1;
        }
        return PALLET_LOCATE_SLOT_BASE + (layer * PalletBlockEntity.COLUMNS) + column + 1;
    }

    private static boolean isPalletLocateSlot(int slot) {
        return slot > PALLET_LOCATE_SLOT_BASE && slot <= PALLET_LOCATE_SLOT_BASE + PALLET_LOCATE_SLOT_COUNT;
    }

    private static StockroomLocateTarget parseStockroomLocateTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        if (parts.length < 5) {
            return null;
        }
        String dim = normalizedDim(parts[0]);
        try {
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            int slot = Integer.parseInt(parts[4].trim());
            String label = parts.length >= 6 ? sanitizeTokenText(parts[5]) : "Inventory";
            if (slot <= 0 || slot > 100_000) {
                return null;
            }
            return new StockroomLocateTarget(dim, new BlockPos(x, y, z), slot, label);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static ShelfTarget parseShelfTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        if (parts.length != 4) {
            return null;
        }
        String dim = normalizedDim(parts[0]);
        try {
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            return new ShelfTarget(dim, new BlockPos(x, y, z));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String encodeShelfSlotTarget(String dimensionId, BlockPos pos, int slot) {
        if (pos == null) {
            return "";
        }
        return normalizedDim(dimensionId)
                + ";" + pos.getX()
                + ";" + pos.getY()
                + ";" + pos.getZ()
                + ";" + slot;
    }

    private static ShelfSlotTarget parseShelfSlotTarget(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";", -1);
        if (parts.length != 5) {
            return null;
        }
        String dim = normalizedDim(parts[0]);
        try {
            int x = Integer.parseInt(parts[1].trim());
            int y = Integer.parseInt(parts[2].trim());
            int z = Integer.parseInt(parts[3].trim());
            int slot = Integer.parseInt(parts[4].trim());
            if (slot < 0 || slot > 63) {
                return null;
            }
            return new ShelfSlotTarget(dim, new BlockPos(x, y, z), slot);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void applyVaultWithdrawalPlan(CompoundTag shopTag, int[] plan) {
        if (shopTag == null || plan == null || plan.length == 0) {
            return;
        }
        int[] current = readCashVaultCounts(shopTag);
        for (int i = 0; i < current.length && i < plan.length; i++) {
            int remove = Math.max(0, plan[i]);
            if (remove <= 0) {
                continue;
            }
            current[i] = Math.max(0, current[i] - remove);
        }
        shopTag.putIntArray(TAG_CASH_VAULT_COUNTS, current);
    }

    private static boolean containsUuidString(ListTag list, UUID id) {
        if (list == null || id == null) {
            return false;
        }
        String raw = id.toString();
        for (Tag tag : list) {
            if (tag instanceof net.minecraft.nbt.StringTag s && raw.equalsIgnoreCase(s.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static List<UUID> decodeUuidStringList(ListTag list) {
        List<UUID> out = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return out;
        }
        for (Tag tag : list) {
            if (!(tag instanceof net.minecraft.nbt.StringTag s)) {
                continue;
            }
            try {
                out.add(UUID.fromString(s.getAsString().trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }

    private static String sanitizeTokenText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "-";
        }
        return raw.replace("|", "/").replace("\n", " ").replace("\r", " ").trim();
    }

    private record FranchiseRequiredItem(String itemId,
                                         int quantity,
                                         boolean exact,
                                         String note,
                                         CompoundTag stackTag) {}

    private record FranchiseRequirementProgress(FranchiseRequiredItem requirement,
                                                int current,
                                                boolean satisfied,
                                                String displayName) {}

    private record FranchiseRequirementStatus(boolean compliant,
                                             int missingCount,
                                             String missingSummary,
                                             List<FranchiseRequirementProgress> items) {}

    private record ItemPickEntry(String itemId, String itemName, int maxStack, int count) {}
    private record PalletRef(String dimensionId, BlockPos pos) {}
    private record AssignedPalletView(String palletId, PalletRef ref) {}
    private record OrderView(UUID orderId,
                             String itemId,
                             String itemName,
                             int quantity,
                             long rewardCents,
                             String status,
                             UUID acceptedBy,
                             String acceptedByName,
                             long expiresAt,
                             int timeoutMinutes,
                             long createdAtMillis,
                             String boundPalletRef,
                             long completedAtMillis,
                             long routeMillis,
                             int routeDistanceBlocks,
                             long payoutCents) {}
    private record FoundOrder(CompoundTag shopTag, CompoundTag orderTag) {}
    private record FoundAssignedPallet(CompoundTag shopTag) {}
    private record OrderBoardEntry(UUID orderId,
                                   UUID shopId,
                                   UUID ownerId,
                                   String shopName,
                                   String itemId,
                                   String itemName,
                                   int quantity,
                                   long rewardCents,
                                   String status,
                                   UUID acceptedBy,
                                   String acceptedByName,
                                   long remainingSeconds,
                                   int timeoutMinutes,
                                   long createdAtMillis,
                                   String boundPalletRef,
                                   long completedAtMillis,
                                   long routeMillis,
                                   int routeDistanceBlocks,
                                   long payoutCents) {
        private long sortMillis() {
            return completedAtMillis > 0L ? completedAtMillis : createdAtMillis;
        }
    }
    private record BoxDeliverySummary(boolean valid, String itemId, int totalCount) {}
    private record DeliveryBoardOrder(String shopName,
                                      String itemName,
                                      int quantity,
                                      long rewardCents,
                                      long expiresAtMillis,
                                      int timeoutMinutes,
                                      String dropTarget,
                                      ListTag shopClaims,
                                      long createdAtMillis) {}
    private record CourierProgress(long completed,
                                   long canceled,
                                   long streak,
                                   long bestStreak,
                                   long totalPayoutCents,
                                   long lastActivityAt,
                                   int successRatePct) {
        private static CourierProgress empty() {
            return new CourierProgress(0L, 0L, 0L, 0L, 0L, 0L, 100);
        }
    }
    private record CourierRankEntry(UUID courierId,
                                    String courierName,
                                    long completed,
                                    long canceled,
                                    long streak,
                                    long bestStreak,
                                    long totalPayoutCents,
                                    long bestRouteMillis,
                                    int bestRouteDistanceBlocks,
                                    long bestRouteScore,
                                    int successRatePct) {}
    private record DeliveryPayout(long baseRewardCents,
                                  long bonusCents,
                                  long totalPayoutCents,
                                  int speedBonusPct,
                                  int streakBonusPct,
                                  int totalBonusPct) {}

    private record ShopOverlapResult(boolean overlap) {
        private static ShopOverlapResult yes() {
            return new ShopOverlapResult(true);
        }

        private static ShopOverlapResult no() {
            return new ShopOverlapResult(false);
        }
    }

    private record SlotStockTargets(int minStockTarget, int maxStockTarget) {}
    private record ShelfRef(String dimensionId, BlockPos pos, ShelfDisplayBlockEntity shelf) {}
    private record ShelfTarget(String dimensionId, BlockPos pos) {}
    private record ShelfSlotTarget(String dimensionId, BlockPos pos, int slot) {}
    private record StockroomItemEntry(String itemId,
                                      String itemName,
                                      int count,
                                      String inventoryType,
                                      String dimensionId,
                                      BlockPos pos,
                                      int slot,
                                      int totalSlots,
                                      String locateTarget) {}
    private record StockroomLocateTarget(String dimensionId, BlockPos pos, int slot, String inventoryLabel) {}
    public record CheckoutTerminalTarget(String shopName,
                                         String dimensionId,
                                         BlockPos pos,
                                         ShopTerminalBlockEntity terminal) {}
}
