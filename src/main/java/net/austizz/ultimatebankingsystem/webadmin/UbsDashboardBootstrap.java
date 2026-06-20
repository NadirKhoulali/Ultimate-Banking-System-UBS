package net.austizz.ultimatebankingsystem.webadmin;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardActionDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponentDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardComponents;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardLayoutDefaults;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPageDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardPanelDefinition;
import net.austizz.ultimatebankingsystem.api.dashboard.DashboardRegistrationResult;
import net.austizz.ultimatebankingsystem.api.dashboard.UltimateBankingDashboardApiProvider;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UbsDashboardBootstrap {
    private UbsDashboardBootstrap() {
    }

    static void register() {
        DashboardDefinition dashboard = DashboardDefinition.builder(UltimateBankingSystem.MODID, "Ultimate Banking System")
                .subtitle("Banking, retail, users, and server health")
                .icon("UBS")
                .order(0)
                .defaults(DashboardLayoutDefaults.ubs())
                .panel(nav("dashboard", "Dashboard", "Economy health and live operations.", 0))
                .panel(nav("health", "Server Health", "Live server health, lag pressure, and UBS resource impact.", 10))
                .panel(nav("banks", "Banks", "Full banking registry, status, reserves, and account coverage.", 20))
                .panel(nav("shops", "Shops", "All shops with ownership, level progression, and revenue KPIs.", 30))
                .panel(nav("shop-items", "Shop Items", "Global item pricing index across all shops.", 40))
                .panel(nav("users", "Users", "All known players with UBS footprint summaries.", 50))
                .panel(nav("audit", "Audit", "Operational event stream and command trail.", 60))
                .page(dashboardPage())
                .page(healthPage())
                .page(entitiesPage("banks", "Banks", "Full banking registry, status, reserves, and account coverage.", "/api/webadmin/banks", 20, "bankId", "#/banks/{bankId}"))
                .page(entitiesPage("shops", "Shops", "All shops with ownership, level progression, and revenue KPIs.", "/api/webadmin/shops", 30, "shopId", "#/shops/{shopId}"))
                .page(shopItemsPage())
                .page(shopItemDetailPage())
                .page(bankDetailPage())
                .page(shopDetailPage())
                .page(usersPage())
                .page(playerDetailPage())
                .page(accountDetailPage())
                .page(auditPage())
                .build();

        DashboardRegistrationResult result = UltimateBankingDashboardApiProvider.registry().registerDashboard(dashboard);
        if (!result.success()) {
            UltimateBankingSystem.LOGGER.warn("[UBS WebAdmin] Failed to register built-in dashboard: {}", result.message());
        }
    }

    private static DashboardPanelDefinition nav(String id, String title, String subtitle, int order) {
        return DashboardPanelDefinition.builder(id, title)
                .subtitle(subtitle)
                .order(order)
                .build();
    }

    private static DashboardPageDefinition dashboardPage() {
        return page("dashboard", "Dashboard", "Economy health and live operations.", 0, "/api/webadmin/dashboard", "#/dashboard",
                DashboardComponents.kpiGroup("dashboard-kpis", "kpis", List.of(
                        card("Money Circulating", "moneyCirculating", "money"),
                        card("Total Deposits", "totalDeposits", "money"),
                        card("Total Reserves", "totalReserves", "money"),
                        card("Banks", "banksTotal", "number"),
                        card("Shops", "shopsTotal", "number"),
                        card("Accounts", "accountsTotal", "number"),
                        card("Issued Cards", "issuedCards", "number"),
                        card("Online Players", "onlinePlayers", "number"),
                        card("Reserve Ratio", "reserveRatioPct", "percent-string"),
                        card("Shop Revenue", "shopsRevenueDollars", "money"),
                        card("Web Clients", "wsClients", "number"),
                        card("Flagged Banks", "flaggedBanks", "number")
                )).option("placement", "top").build(),
                DashboardComponents.panel("dashboard-alerts-panel", "Alerts")
                        .child(component("dashboard-alerts", DashboardComponents.ALERT_LIST).dataPath("highlights.warnings").build())
                        .build(),
                DashboardComponents.chartPanel("economy-trend", "Economy Trend", DashboardComponents.LINE_CHART, "charts.economyHistory")
                        .subtitle("30-day hourly history")
                        .build(),
                DashboardComponents.twoColumn("dashboard-chart-split")
                        .child(DashboardComponents.chartPanel("bank-status", "Bank Status", DashboardComponents.STATUS_CHART, "charts.bankStatus").build())
                        .child(DashboardComponents.chartPanel("shop-type", "Shop Type Mix", DashboardComponents.SHOP_TYPE_CHART, "charts.shopType").build())
                        .build(),
                DashboardComponents.twoColumn("dashboard-top-tables")
                        .child(DashboardComponents.table("top-banks", "Top Banks by Deposits", "charts.topBanksByDeposits", List.of(
                                col("Bank", "name"),
                                col("Status", "status"),
                                col("Deposits", "deposits", "money"),
                                col("Reserve", "reserve", "money")
                        )).build())
                        .child(DashboardComponents.table("top-shops", "Top Shops by Revenue", "charts.topShopsByRevenue", List.of(
                                col("Shop", "name"),
                                col("Type", "type"),
                                col("Level", "level"),
                                col("Revenue", "revenueDollars", "money")
                        )).build())
                        .build(),
                DashboardComponents.panel("server-tools", "Server Tools")
                        .child(component("server-command-runner", DashboardComponents.COMMAND_RUNNER)
                                .option("endpoint", "/api/webadmin/command")
                                .option("quickCommands", List.of("/ubs web status", "/ubs web on", "/ubs web off", "/ubs web link"))
                                .build())
                        .build()
        );
    }

    private static DashboardPageDefinition healthPage() {
        return page("health", "Server Health", "Live server health, lag pressure, and UBS resource impact.", 10, "/api/webadmin/health", "#/health",
                DashboardComponents.panel("health-overview", "Server Health")
                        .subtitle("Live server health, lag pressure, and UBS resource impact.")
                        .child(DashboardComponents.kpiGroup("health-kpis", "performance", List.of(
                                card("Server MSPT", "serverAvgMspt", "ms"),
                                card("Estimated TPS", "serverEstimatedTps", "decimal2"),
                                card("Process CPU", "processCpuLoadPct", "percent-or-na"),
                                card("Heap Used", "jvmHeapUsedPct", "percent"),
                                card("UBS Avg MSPT", "modAvgMspt", "ms"),
                                card("UBS P95 MSPT", "modP95Mspt", "ms"),
                                card("UBS Tick Budget", "modTickBudgetPct", "percent"),
                                card("UBS Share of Tick", "modShareOfServerTickPct", "percent")
                        )).option("compact", true).build())
                        .child(component("health-meters", DashboardComponents.HEALTH_METERS).build())
                        .child(component("health-report", DashboardComponents.ALERT_LIST).option("healthReport", true).build())
                        .build()
        );
    }

    private static DashboardPageDefinition entitiesPage(String id, String title, String subtitle, String dataUrl, int order, String detailKey, String detailRoute) {
        return page(id, title, subtitle, order, dataUrl, "#/" + id,
                DashboardComponents.kpiGroup(id + "-kpis", "metrics", List.of(
                        id.equals("banks") ? card("Banks", "banksTotal", "number") : card("Shops", "shopsTotal", "number"),
                        id.equals("banks") ? card("Active", "activeBanks", "number") : card("Revenue", "revenueDollars", "money"),
                        id.equals("banks") ? card("Flagged", "flaggedBanks", "number") : card("Average Level", "avgLevel", "number"),
                        id.equals("banks") ? card("Accounts", "accountsTotal", "number") : card("Visible Rows", "shopsTotal", "number"),
                        id.equals("banks") ? card("Deposits", "totalDeposits", "money") : card("Revenue", "revenueDollars", "money"),
                        id.equals("banks") ? card("Reserves", "totalReserves", "money") : card("Average Level", "avgLevel", "number")
                )).option("compact", true).build(),
                DashboardComponents.panel(id + "-table-panel", title)
                        .child(component(id + "-table", DashboardComponents.TABLE)
                                .dataPath("rows")
                                .option("mode", id)
                                .option("detailKey", detailKey)
                                .option("detailRoute", detailRoute)
                                .build())
                        .build()
        );
    }

    private static DashboardPageDefinition shopItemsPage() {
        return page("shop-items", "Shop Items", "Global item pricing index across all shops.", 40, "/api/webadmin/shop-items", "#/shop-items",
                DashboardComponents.kpiGroup("shop-items-kpis", "metrics", List.of(
                        card("Items", "itemsTotal", "number"),
                        card("Shops Scanned", "shopsScanned", "number"),
                        card("Shops With Listings", "shopsWithListings", "number"),
                        card("Listings", "listingsTotal", "number")
                )).option("compact", true).build(),
                component("shop-item-list", DashboardComponents.ITEM_CARD_LIST)
                        .dataPath("rows")
                        .option("detailRoute", "#/shop-items/{itemId}")
                        .build()
        );
    }

    private static DashboardPageDefinition shopItemDetailPage() {
        return page("shop-item", "Shop Item", "Price, demand, listings, and distribution for one item.", 41, "/api/webadmin/shop-items/{itemId}", "#/shop-items/{itemId}",
                DashboardComponents.kpiGroup("shop-item-kpis", "metrics", List.of(
                        card("Shops Selling", "shopsSelling", "number"),
                        card("Listings", "listingCount", "number"),
                        card("Lowest Price", "priceLowCents", "cents-money"),
                        card("Average Price", "priceAvgCents", "cents-money"),
                        card("Highest Price", "priceHighCents", "cents-money"),
                        card("Spread", "priceSpreadPct", "percent-string")
                )).option("compact", true).build(),
                DashboardComponents.panel("shop-item-preview", "Item Preview")
                        .child(component("shop-item-model", "item-model").dataPath("").build())
                        .child(component("shop-item-alerts", DashboardComponents.ALERT_LIST).dataPath("alerts").build())
                        .build(),
                DashboardComponents.chartPanel("shop-item-demand", "Demand History", DashboardComponents.LINE_CHART, "demandRows").build(),
                DashboardComponents.twoColumn("shop-item-chart-split")
                        .child(DashboardComponents.chartPanel("shop-item-price", "Shop Price Spread", DashboardComponents.BAR_CHART, "shopRows").build())
                        .child(DashboardComponents.chartPanel("shop-item-distribution", "Distribution", DashboardComponents.BAR_CHART, "distributionRows").build())
                        .build(),
                DashboardComponents.table("shop-item-shops", "Shops Carrying Item", "shopRows", List.of(
                        col("Shop", "shopName"),
                        col("Type", "shopType"),
                        col("Listings", "listings", "number"),
                        col("Average", "averagePrice", "money")
                )).build(),
                DashboardComponents.table("shop-item-listings", "Listings", "listings", List.of(
                        col("Shop", "shopName"),
                        col("Shelf", "shelf"),
                        col("Price", "price", "money"),
                        col("Stock", "stock", "number")
                )).build()
        );
    }

    private static DashboardPageDefinition bankDetailPage() {
        return detailPage("bank", "Bank Detail", "Bank accounts, roles, products, loans, and admin actions.", 21, "/api/webadmin/bank/{bankId}", "#/banks/{bankId}",
                DashboardComponents.kpiGroup("bank-kpis", "bank", List.of(
                        card("Deposits", "deposits", "money"),
                        card("Reserve", "reserve", "money"),
                        card("Accounts", "accountsCount", "number"),
                        card("Loans", "outstandingLoans", "number")
                )).option("compact", true).build(),
                DashboardComponents.panel("bank-overview", "Bank Overview")
                        .child(DashboardComponents.twoColumn("bank-overview-split")
                                .child(component("bank-summary", DashboardComponents.KEY_VALUE).dataPath("bank").build())
                                .child(component("bank-limits", DashboardComponents.KEY_VALUE).dataPath("limits").build())
                                .build())
                        .build(),
                table("bank-accounts", "Bank Accounts", "accounts"),
                table("bank-roles", "Access Roles", "roles"),
                table("bank-shares", "Shareholders", "shares"),
                table("bank-cofounders", "Cofounders", "cofounders"),
                table("bank-employees", "Employees", "employees"),
                table("bank-products", "Loan Products", "loanProducts"),
                table("bank-offers", "Interbank Offers", "interbankOffers"),
                table("bank-loans", "Interbank Loans", "interbankLoans"),
                bankActionFormPanel()
        );
    }

    private static DashboardPageDefinition shopDetailPage() {
        return detailPage("shop", "Shop", "Shop permissions, regions, finance, stock, orders, shelves, and admin actions.", 31, "/api/webadmin/shop/{shopId}", "#/shops/{shopId}",
                DashboardComponents.kpiGroup("shop-kpis", "kpis", List.of(
                        card("Revenue", "revenueDollars", "money"),
                        card("Next Target", "nextTargetDollars", "money"),
                        card("Claim Usage", "claimUsedBlocks", "number"),
                        card("Stockroom Usage", "stockroomUsedBlocks", "number"),
                        card("Cashiers", "cashiers", "number"),
                        card("Display Capacity", "displayCapacity", "number")
                )).option("compact", true).build(),
                component("shop-summary", DashboardComponents.KEY_VALUE).dataPath("shop").build(),
                DashboardComponents.panel("shop-business-type", "Business Type Economy")
                        .child(DashboardComponents.twoColumn("shop-business-type-split")
                                .child(component("shop-type-economy", DashboardComponents.KEY_VALUE).dataPath("typeEconomy").build())
                                .child(shopBusinessActionForm())
                                .build())
                        .build(),
                DashboardComponents.twoColumn("shop-business-networks")
                        .child(DashboardComponents.table("shop-franchise-offers", "Franchise Offers", "franchiseOffers", List.of(
                                col("Brand", "brandName"),
                                col("Upfront", "upfrontCents", "cents-money"),
                                col("Royalty", "royaltyPercent", "percent"),
                                col("Marketing", "marketingPercent", "percent"),
                                col("Visibility", "visibility"),
                                col("Offer ID", "offerId")
                        )).build())
                        .child(DashboardComponents.table("shop-franchise-contracts", "Franchise Contracts", "franchiseContracts", List.of(
                                col("Brand", "brandName"),
                                col("Franchisee", "franchisee"),
                                col("Royalty", "royaltyPercent", "percent"),
                                col("Marketing", "marketingPercent", "percent"),
                                col("Mode", "mode"),
                                col("Contract ID", "contractId")
                        )).build())
                        .build(),
                DashboardComponents.table("shop-corporate-branches", "Corporate Branch Network", "corporateBranches", List.of(
                        col("Shop", "name"),
                        col("Level", "level", "number"),
                        col("Revenue", "revenueDollars", "money"),
                        col("Shop ID", "shopId")
                )).build(),
                table("shop-permissions", "Permissions", "permissions"),
                DashboardComponents.twoColumn("shop-regions")
                        .child(table("shop-claims", "Claims", "claims"))
                        .child(table("shop-stockrooms", "Stockrooms", "stockrooms"))
                        .build(),
                component("shop-hours", DashboardComponents.KEY_VALUE).dataPath("hours").build(),
                component("shop-finance", DashboardComponents.KEY_VALUE).dataPath("finance").build(),
                table("shop-vault", "Vault", "vault"),
                table("shop-employees", "Employees", "employees"),
                table("shop-orders", "Orders", "orders"),
                table("shop-order-pallets", "Order Pallets", "orderPallets"),
                table("shop-shelves", "Shelves", "shelves"),
                table("shop-stockroom-items", "Stockroom Items", "stockroomItems"),
                component("shop-roadmap", DashboardComponents.ROADMAP).dataPath("roadmap").build(),
                actionPanel("shop-actions", "Shop Admin Actions", "/api/webadmin/shop/{shopId}/action", List.of(
                        "PERMISSION_SET", "PERMISSION_REMOVE", "CLAIM_ADD", "CLAIM_REMOVE", "STOCKROOM_ADD", "STOCKROOM_REMOVE",
                        "SET_HOURS", "SET_CLOSED_DELIVERER_ACCESS", "SET_LIGHTING", "SET_SETTLEMENT_ACCOUNT",
                        "PAY_TYPE_FEES", "CLEAR_CHECKOUT_TERMINAL", "CLEAR_CASHIER_LINKS", "EMPLOYEE_FIRE", "DELETE_SHOP"
                ))
        );
    }

    private static DashboardPageDefinition usersPage() {
        return page("users", "Users", "All known players with UBS footprint summaries.", 50, "/api/webadmin/users", "#/users",
                DashboardComponents.kpiGroup("users-kpis", "metrics", List.of(
                        card("Known Users", "knownUsers", "number"),
                        card("Online Users", "onlineUsers", "number"),
                        card("Accounts", "accounts", "number"),
                        card("Cards", "cards", "number")
                )).option("compact", true).build(),
                DashboardComponents.panel("users-table-panel", "Users")
                        .child(component("users-table", DashboardComponents.TABLE)
                                .dataPath("rows")
                                .option("detailKey", "playerId")
                                .option("detailRoute", "#/users/{playerId}")
                                .build())
                        .build()
        );
    }

    private static DashboardPageDefinition playerDetailPage() {
        return detailPage("player", "Player", "Accounts, cards, shops, and transaction activity for one player.", 51, "/api/webadmin/users/{playerId}", "#/users/{playerId}",
                DashboardComponents.kpiGroup("player-kpis", "summary", List.of(
                        card("Balance", "totalBalance", "money"),
                        card("Accounts", "accounts", "number"),
                        card("Active Cards", "activeCards", "number"),
                        card("Blocked Cards", "blockedCards", "number"),
                        card("Shops", "shops", "number"),
                        card("Transactions", "transactions", "number")
                )).build(),
                DashboardComponents.chartPanel("player-accounts-chart", "Account Balances", DashboardComponents.BAR_CHART, "accounts").build(),
                DashboardComponents.chartPanel("player-transactions-chart", "Transactions", DashboardComponents.LINE_CHART, "transactions").build(),
                table("player-accounts", "Accounts", "accounts"),
                component("player-cards", DashboardComponents.CARD_CAROUSEL).dataPath("creditCards").build(),
                component("player-shops", "plain-list").dataPath("shops").build(),
                table("player-transactions", "Transactions", "transactions")
        );
    }

    private static DashboardPageDefinition accountDetailPage() {
        return detailPage("account", "Account", "Account cards, safebox, loans, transactions, roles, and admin actions.", 52, "/api/webadmin/account/{accountId}", "#/users/{playerId}/accounts/{accountId}",
                DashboardComponents.kpiGroup("account-kpis", "account", List.of(
                        card("Balance", "balance", "money"),
                        card("Credit", "creditScore", "number"),
                        card("Active Loans", "activeLoans", "number"),
                        card("Transactions", "transactions", "number")
                )).option("compact", true).build(),
                DashboardComponents.twoColumn("account-summary-split")
                        .child(component("account-summary", DashboardComponents.KEY_VALUE).dataPath("account").build())
                        .child(component("account-limits", DashboardComponents.KEY_VALUE).dataPath("limits").build())
                        .build(),
                table("account-roles", "Roles", "roles"),
                table("account-safebox", "Safebox", "safeBox"),
                table("account-cards", "Cards", "cards"),
                table("account-loans", "Loans", "loans"),
                table("account-transactions", "Transactions", "transactions"),
                actionPanel("account-actions", "Account Admin Actions", "/api/webadmin/account/{accountId}/action", List.of(
                        "ROLE_GRANT", "ROLE_REVOKE", "SAFEBOX_ADD", "SAFEBOX_DELIVER", "SAFEBOX_DELETE",
                        "CARD_CREATE", "CARD_BLOCK_ALL", "CARD_ADD_TO_INVENTORY", "CARD_REPLACE", "CARD_DELETE",
                        "LOAN_DEFAULTED", "LOAN_REMAINING", "LOAN_DELETE", "FREEZE", "UNFREEZE", "SET_PRIMARY",
                        "DEPOSIT", "WITHDRAW", "SET_CREDIT", "ADJUST_CREDIT", "SET_DEFAULTED", "SET_PIN",
                        "SAVE_ACCESS_PROFILE", "SET_TEMP_LIMIT", "CLEAR_TEMP_LIMIT", "RESET_DAILY", "DELETE_ACCOUNT"
                ))
        );
    }

    private static DashboardPageDefinition auditPage() {
        return page("audit", "Audit", "Operational event stream and command trail.", 60, "/api/webadmin/audit", "#/audit",
                component("audit-output", DashboardComponents.OUTPUT).dataPath("entries").build()
        );
    }

    private static DashboardPageDefinition page(String id, String title, String subtitle, int order, String dataUrl, String routePattern, DashboardComponentDefinition... components) {
        DashboardPageDefinition.Builder builder = DashboardPageDefinition.builder(id, title)
                .subtitle(subtitle)
                .order(order)
                .dataUrl(dataUrl)
                .routePattern(routePattern);
        for (DashboardComponentDefinition component : components) {
            builder.component(component);
        }
        return builder.build();
    }

    private static DashboardPageDefinition detailPage(String id, String title, String subtitle, int order, String dataUrl, String routePattern, DashboardComponentDefinition... components) {
        return page(id, title, subtitle, order, dataUrl, routePattern, components);
    }

    private static DashboardComponentDefinition.Builder component(String id, String type) {
        return DashboardComponentDefinition.builder(id, type);
    }

    private static DashboardComponentDefinition table(String id, String title, String dataPath) {
        return DashboardComponents.panel(id, title)
                .child(component(id + "-table", DashboardComponents.TABLE).dataPath(dataPath).build())
                .build();
    }

    private static DashboardComponentDefinition actionPanel(String id, String title, String endpoint, List<String> actions) {
        DashboardComponentDefinition.Builder form = component(id + "-form", DashboardComponents.ACTION_BUTTONS)
                .option("endpoint", endpoint);
        for (String action : actions) {
            form.action(DashboardActionDefinition.builder(action.toLowerCase().replace('_', '-'), label(action))
                    .endpoint(endpoint)
                    .option("action", action)
                    .build());
        }
        return DashboardComponents.panel(id, title)
                .child(form.build())
                .build();
    }

    private static DashboardComponentDefinition bankActionFormPanel() {
        return DashboardComponents.panel("bank-actions", "Bank Admin Actions")
                .child(component("bank-actions-form", DashboardComponents.ACTION_FORM)
                        .option("endpoint", "/api/webadmin/bank/{bankId}/action")
                        .option("preset", "bank-admin")
                        .build())
                .build();
    }

    private static DashboardComponentDefinition shopBusinessActionForm() {
        return component("shop-business-actions", DashboardComponents.ACTION_FORM)
                .option("endpoint", "/api/webadmin/shop/{shopId}/action")
                .option("sections", List.of(
                        map(
                                "title", "Type Fees",
                                "subtitle", "Pay royalties, marketing fees, corporate overhead, or accrued unpaid shop-type fees.",
                                "fields", List.of(),
                                "actions", List.of(action("Pay Due Fees", "PAY_TYPE_FEES", map(), List.of(), "primary"))
                        ),
                        map(
                                "title", "Franchise Brand Owner",
                                "subtitle", "Publish a sellable brand license. Brand owners unlock license capacity through shop levels.",
                                "fields", List.of(
                                        field("brandName", "Brand Name", "text", "Brand shown to buyers", ""),
                                        field("upfrontDollars", "Upfront Fee ($)", "text", "5000", "5000"),
                                        field("royaltyPercent", "Royalty %", "text", "6.0", "6.0"),
                                        field("marketingPercent", "Marketing %", "text", "2.0", "2.0"),
                                        field("rules", "Optional Rules", "text", "Template/rule summary", ""),
                                        field("directPlayerId", "Direct Player UUID", "text", "Optional private buyer", "")
                                ),
                                "actions", List.of(action("Publish Offer", "FRANCHISE_PUBLISH_OFFER", map(
                                        "brandName", "$brandName",
                                        "upfrontDollars", "$upfrontDollars",
                                        "royaltyPercent", "$royaltyPercent",
                                        "marketingPercent", "$marketingPercent",
                                        "rules", "$rules",
                                        "directPlayerId", "$directPlayerId"
                                ), List.of("brandName"), "primary"))
                        ),
                        map(
                                "title", "Franchise Buyer / Solo World",
                                "subtitle", "Accept another shop's franchise offer, cancel your own offer, or add a solo-world NPC license.",
                                "fields", List.of(
                                        field("offerId", "Offer UUID", "text", "Offer UUID", "")
                                ),
                                "actions", List.of(
                                        action("Accept Offer", "FRANCHISE_ACCEPT_OFFER", map("offerId", "$offerId"), List.of("offerId"), "primary"),
                                        action("Cancel My Offer", "FRANCHISE_CANCEL_OFFER", map("offerId", "$offerId"), List.of("offerId"), "danger"),
                                        action("Add NPC Franchisee", "FRANCHISE_NPC_LICENSE", map(), List.of(), "")
                                )
                        ),
                        map(
                                "title", "Corporate Branches",
                                "subtitle", "Attach owned shops as branches. Corporate stores pay overhead and gain centralized reporting.",
                                "fields", List.of(
                                        field("branchShopId", "Branch Shop UUID", "text", "Owned shop UUID", "")
                                ),
                                "actions", List.of(
                                        action("Add Branch", "CORPORATE_ADD_BRANCH", map("branchShopId", "$branchShopId"), List.of("branchShopId"), "primary"),
                                        action("Remove Branch", "CORPORATE_REMOVE_BRANCH", map("branchShopId", "$branchShopId"), List.of("branchShopId"), "danger")
                                )
                        )
                ))
                .build();
    }

    private static Map<String, Object> card(String label, String path, String format) {
        return map("label", label, "path", path, "format", format);
    }

    private static Map<String, Object> col(String label, String path) {
        return map("label", label, "path", path);
    }

    private static Map<String, Object> col(String label, String path, String format) {
        return map("label", label, "path", path, "format", format);
    }

    private static Map<String, Object> field(String id, String label, String type, String placeholder, String value) {
        return map("id", id, "label", label, "type", type, "placeholder", placeholder, "value", value);
    }

    private static Map<String, Object> action(String label, String action, Map<String, Object> payload, List<String> required, String tone) {
        return map("label", label, "action", action, "payload", payload, "required", required, "tone", tone);
    }

    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static String label(String action) {
        String[] parts = action.toLowerCase().split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
