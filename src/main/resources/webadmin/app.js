(function () {
    const storageKey = "ubs.webadmin.sessionId";
    const themeStorageKey = "ubs.webadmin.theme";
    const state = {
        sessionId: localStorage.getItem(storageKey) || ("web-" + Math.random().toString(36).slice(2, 10)),
        ws: null,
        reconnectTimer: null,
        healthTimer: null,
        refreshTimer: null,
        route: "dashboard",
        entityMode: "banks",
        dashboard: null,
        banksRows: [],
        banksMetrics: {},
        shopsRows: [],
        shopsMetrics: {},
        shopItemsRows: [],
        shopItemsMetrics: {},
        shopItemDetail: null,
        shopItemWindowDays: 30,
        usersRows: [],
        usersMetrics: {},
        playerDetail: null,
        bankDetail: null,
        shopDetail: null,
        accountDetail: null,
        cardCarouselIndex: 0,
        bankOverviewModalOpen: false,
        shopItemIncludeNormal: true,
        shopItemIncludeCreative: true,
        theme: localStorage.getItem(themeStorageKey) === "dark" ? "dark" : "light"
    };
    localStorage.setItem(storageKey, state.sessionId);

    const el = {
        pageTitle: document.getElementById("page-title"),
        pageSubtitle: document.getElementById("page-subtitle"),
        globalAlerts: document.getElementById("global-alerts"),
        serverBind: document.getElementById("server-bind"),
        themeToggle: document.getElementById("theme-toggle"),
        themeToggleLabel: document.getElementById("theme-toggle-label"),
        sessionId: document.getElementById("session-id"),
        wsState: document.getElementById("ws-state"),
        healthState: document.getElementById("health-state"),

        pages: {
            dashboard: document.getElementById("page-dashboard"),
            health: document.getElementById("page-health"),
            entities: document.getElementById("page-entities"),
            "shop-items": document.getElementById("page-shop-items"),
            "shop-item": document.getElementById("page-shop-item"),
            bank: document.getElementById("page-bank"),
            shop: document.getElementById("page-shop"),
            users: document.getElementById("page-users"),
            player: document.getElementById("page-player"),
            account: document.getElementById("page-account"),
            audit: document.getElementById("page-audit")
        },

        navLinks: Array.from(document.querySelectorAll(".nav-link")),

        refreshAll: document.getElementById("refresh-all"),
        refreshAudit: document.getElementById("refresh-audit"),

        dashboardKpis: document.getElementById("dashboard-kpis"),
        dashboardAlerts: document.getElementById("dashboard-alerts"),
        healthSampleNote: document.getElementById("health-sample-note"),
        healthOverviewKpis: document.getElementById("health-overview-kpis"),
        healthMeterGrid: document.getElementById("health-meter-grid"),
        healthReportLines: document.getElementById("health-report-lines"),
        economyLineChart: document.getElementById("economy-line-chart"),
        economyTrendInfo: document.getElementById("economy-trend-info"),
        bankStatusChart: document.getElementById("bank-status-chart"),
        bankStatusInfo: document.getElementById("bank-status-info"),
        shopTypeChart: document.getElementById("shop-type-chart"),
        shopTypeInfo: document.getElementById("shop-type-info"),
        topBanksBody: document.getElementById("top-banks-body"),
        topShopsBody: document.getElementById("top-shops-body"),

        entitiesKpis: document.getElementById("entities-kpis"),
        entityPanelTitle: document.getElementById("entity-panel-title"),

        entitySearch: document.getElementById("entity-search"),
        entityCount: document.getElementById("entity-count"),
        entityHead: document.getElementById("entity-head"),
        entityBody: document.getElementById("entity-body"),
        shopItemsKpis: document.getElementById("shop-items-kpis"),
        shopItemsCount: document.getElementById("shop-items-count"),
        shopItemsSearch: document.getElementById("shop-items-search"),
        shopItemsIncludeNormal: document.getElementById("shop-items-include-normal"),
        shopItemsIncludeCreative: document.getElementById("shop-items-include-creative"),
        shopItemsList: document.getElementById("shop-items-list"),

        shopItemBack: document.getElementById("shop-item-back"),
        shopItemTitle: document.getElementById("shop-item-title"),
        shopItemSubtitle: document.getElementById("shop-item-subtitle"),
        shopItemKpis: document.getElementById("shop-item-kpis"),
        shopItemAlerts: document.getElementById("shop-item-alerts"),
        shopItemWindowSegment: document.getElementById("shop-item-window-segment"),
        shopItemIncludeNormal: document.getElementById("shop-item-include-normal"),
        shopItemIncludeCreative: document.getElementById("shop-item-include-creative"),
        shopItemDemandChart: document.getElementById("shop-item-demand-chart"),
        shopItemDemandInfo: document.getElementById("shop-item-demand-info"),
        shopItemShopPriceChart: document.getElementById("shop-item-shop-price-chart"),
        shopItemShopPriceInfo: document.getElementById("shop-item-shop-price-info"),
        shopItemDistributionChart: document.getElementById("shop-item-distribution-chart"),
        shopItemDistributionInfo: document.getElementById("shop-item-distribution-info"),
        shopItemShopsCount: document.getElementById("shop-item-shops-count"),
        shopItemShopsBody: document.getElementById("shop-item-shops-body"),
        shopItemListingsCount: document.getElementById("shop-item-listings-count"),
        shopItemListingsBody: document.getElementById("shop-item-listings-body"),

        usersSearch: document.getElementById("users-search"),
        usersCount: document.getElementById("users-count"),
        usersKpis: document.getElementById("users-kpis"),
        usersBody: document.getElementById("users-body"),

        playerBack: document.getElementById("player-back"),
        playerName: document.getElementById("player-name"),
        playerId: document.getElementById("player-id"),
        playerKpis: document.getElementById("player-kpis"),
        playerAccountsChart: document.getElementById("player-accounts-chart"),
        playerTransactionsChart: document.getElementById("player-transactions-chart"),
        playerAccountsBody: document.getElementById("player-accounts-body"),
        playerCardsCount: document.getElementById("player-cards-count"),
        cardsViewport: document.getElementById("cards-viewport"),
        playerCardsTrack: document.getElementById("player-cards-track"),
        cardsPrev: document.getElementById("cards-prev"),
        cardsNext: document.getElementById("cards-next"),
        playerShopsList: document.getElementById("player-shops-list"),
        playerBankFootprint: document.getElementById("player-bank-footprint"),
        playerTransactionsBody: document.getElementById("player-transactions-body"),

        bankBack: document.getElementById("bank-back"),
        bankTitle: document.getElementById("bank-title"),
        bankSubtitle: document.getElementById("bank-subtitle"),
        bankKpis: document.getElementById("bank-kpis"),
        bankSummaryGrid: document.getElementById("bank-summary-grid"),
        bankRiskLines: document.getElementById("bank-risk-lines"),
        bankAccountsCount: document.getElementById("bank-accounts-count"),
        bankAccountsBody: document.getElementById("bank-accounts-body"),
        bankRolesBody: document.getElementById("bank-roles-body"),
        bankSharesBody: document.getElementById("bank-shares-body"),
        bankCofoundersBody: document.getElementById("bank-cofounders-body"),
        bankEmployeesBody: document.getElementById("bank-employees-body"),
        bankProductsBody: document.getElementById("bank-products-body"),
        bankOffersBody: document.getElementById("bank-offers-body"),
        bankLoansBody: document.getElementById("bank-loans-body"),
        bankActionOutput: document.getElementById("bank-action-output"),
        bankOverviewEditBtn: document.getElementById("bank-overview-edit-btn"),
        bankOverviewModal: document.getElementById("bank-overview-modal"),
        bankOverviewCloseBtn: document.getElementById("bank-overview-close-btn"),
        bankOverviewCancelBtn: document.getElementById("bank-overview-cancel-btn"),
        bankOverviewSaveBtn: document.getElementById("bank-overview-save-btn"),
        bankOverviewBankIdInput: document.getElementById("bank-overview-bank-id-input"),
        bankOverviewOwnerInput: document.getElementById("bank-overview-owner-input"),
        bankOverviewOwnerUuidInput: document.getElementById("bank-overview-owner-uuid-input"),
        bankOverviewStatusInput: document.getElementById("bank-overview-status-input"),
        bankOverviewOwnershipInput: document.getElementById("bank-overview-ownership-input"),
        bankOverviewColorInput: document.getElementById("bank-overview-color-input"),
        bankOverviewMottoInput: document.getElementById("bank-overview-motto-input"),
        bankOverviewFederalRateInput: document.getElementById("bank-overview-federal-rate-input"),
        bankOverviewIssueFeeInput: document.getElementById("bank-overview-issue-fee-input"),
        bankOverviewReplacementFeeInput: document.getElementById("bank-overview-replacement-fee-input"),
        bankOverviewSingleLimitInput: document.getElementById("bank-overview-single-limit-input"),
        bankOverviewDailyPlayerLimitInput: document.getElementById("bank-overview-daily-player-limit-input"),
        bankOverviewDailyBankLimitInput: document.getElementById("bank-overview-daily-bank-limit-input"),
        bankOverviewTellerLimitInput: document.getElementById("bank-overview-teller-limit-input"),

        bankRolePlayerInput: document.getElementById("bank-role-player-input"),
        bankRoleSelect: document.getElementById("bank-role-select"),
        bankRoleSaveBtn: document.getElementById("bank-role-save-btn"),
        bankRoleRevokeBtn: document.getElementById("bank-role-revoke-btn"),

        bankSharePlayerInput: document.getElementById("bank-share-player-input"),
        bankSharePercentInput: document.getElementById("bank-share-percent-input"),
        bankShareSetBtn: document.getElementById("bank-share-set-btn"),
        bankShareRemoveBtn: document.getElementById("bank-share-remove-btn"),

        bankCofounderPlayerInput: document.getElementById("bank-cofounder-player-input"),
        bankCofounderAddBtn: document.getElementById("bank-cofounder-add-btn"),
        bankCofounderRemoveBtn: document.getElementById("bank-cofounder-remove-btn"),

        bankEmployeePlayerInput: document.getElementById("bank-employee-player-input"),
        bankEmployeeRoleSelect: document.getElementById("bank-employee-role-select"),
        bankEmployeeSalaryInput: document.getElementById("bank-employee-salary-input"),
        bankEmployeeSaveBtn: document.getElementById("bank-employee-save-btn"),
        bankEmployeeFireBtn: document.getElementById("bank-employee-fire-btn"),

        bankProductNameInput: document.getElementById("bank-product-name-input"),
        bankProductMaxInput: document.getElementById("bank-product-max-input"),
        bankProductAprInput: document.getElementById("bank-product-apr-input"),
        bankProductDurationInput: document.getElementById("bank-product-duration-input"),
        bankProductSaveBtn: document.getElementById("bank-product-save-btn"),
        bankProductDeleteBtn: document.getElementById("bank-product-delete-btn"),

        bankOfferAmountInput: document.getElementById("bank-offer-amount-input"),
        bankOfferAprInput: document.getElementById("bank-offer-apr-input"),
        bankOfferTermInput: document.getElementById("bank-offer-term-input"),
        bankOfferCreateBtn: document.getElementById("bank-offer-create-btn"),
        bankOfferIdInput: document.getElementById("bank-offer-id-input"),
        bankOfferAcceptBtn: document.getElementById("bank-offer-accept-btn"),

        bankMottoInput: document.getElementById("bank-motto-input"),
        bankMottoSaveBtn: document.getElementById("bank-motto-save-btn"),
        bankColorInput: document.getElementById("bank-color-input"),
        bankColorSaveBtn: document.getElementById("bank-color-save-btn"),
        bankLimitTypeSelect: document.getElementById("bank-limit-type-select"),
        bankLimitAmountInput: document.getElementById("bank-limit-amount-input"),
        bankLimitSaveBtn: document.getElementById("bank-limit-save-btn"),
        bankCardIssueFeeInput: document.getElementById("bank-card-issue-fee-input"),
        bankCardReplacementFeeInput: document.getElementById("bank-card-replacement-fee-input"),
        bankCardFeesSaveBtn: document.getElementById("bank-card-fees-save-btn"),

        bankBorrowAmountInput: document.getElementById("bank-borrow-amount-input"),
        bankBorrowBtn: document.getElementById("bank-borrow-btn"),
        bankAppealMessageInput: document.getElementById("bank-appeal-message-input"),
        bankAppealBtn: document.getElementById("bank-appeal-btn"),
        bankTellerIssueBtn: document.getElementById("bank-teller-issue-btn"),
        bankTellerCountBtn: document.getElementById("bank-teller-count-btn"),

        shopBack: document.getElementById("shop-back"),
        shopTitle: document.getElementById("shop-title"),
        shopSubtitle: document.getElementById("shop-subtitle"),
        shopKpis: document.getElementById("shop-kpis"),
        shopSummaryGrid: document.getElementById("shop-summary-grid"),
        shopActionOutput: document.getElementById("shop-action-output"),

        shopNameInput: document.getElementById("shop-name-input"),
        shopTypeSelect: document.getElementById("shop-type-select"),
        shopLevelInput: document.getElementById("shop-level-input"),
        shopOverviewSaveBtn: document.getElementById("shop-overview-save-btn"),

        shopPermissionsBody: document.getElementById("shop-permissions-body"),
        shopPermissionPlayerInput: document.getElementById("shop-permission-player-input"),
        shopPermissionRoleSelect: document.getElementById("shop-permission-role-select"),
        shopPermissionSetBtn: document.getElementById("shop-permission-set-btn"),
        shopPermissionRemoveBtn: document.getElementById("shop-permission-remove-btn"),

        shopClaimsBody: document.getElementById("shop-claims-body"),
        shopClaimDimInput: document.getElementById("shop-claim-dim-input"),
        shopClaimMinXInput: document.getElementById("shop-claim-minx-input"),
        shopClaimMinYInput: document.getElementById("shop-claim-miny-input"),
        shopClaimMinZInput: document.getElementById("shop-claim-minz-input"),
        shopClaimMaxXInput: document.getElementById("shop-claim-maxx-input"),
        shopClaimMaxYInput: document.getElementById("shop-claim-maxy-input"),
        shopClaimMaxZInput: document.getElementById("shop-claim-maxz-input"),
        shopClaimAddBtn: document.getElementById("shop-claim-add-btn"),
        shopClaimRemoveBtn: document.getElementById("shop-claim-remove-btn"),

        shopStockroomsBody: document.getElementById("shop-stockrooms-body"),
        shopStockroomDimInput: document.getElementById("shop-stockroom-dim-input"),
        shopStockroomMinXInput: document.getElementById("shop-stockroom-minx-input"),
        shopStockroomMinYInput: document.getElementById("shop-stockroom-miny-input"),
        shopStockroomMinZInput: document.getElementById("shop-stockroom-minz-input"),
        shopStockroomMaxXInput: document.getElementById("shop-stockroom-maxx-input"),
        shopStockroomMaxYInput: document.getElementById("shop-stockroom-maxy-input"),
        shopStockroomMaxZInput: document.getElementById("shop-stockroom-maxz-input"),
        shopStockroomAddBtn: document.getElementById("shop-stockroom-add-btn"),
        shopStockroomRemoveBtn: document.getElementById("shop-stockroom-remove-btn"),

        shopHoursGrid: document.getElementById("shop-hours-grid"),
        shopHoursOpenInput: document.getElementById("shop-hours-open-input"),
        shopHoursCloseInput: document.getElementById("shop-hours-close-input"),
        shopHoursSaveBtn: document.getElementById("shop-hours-save-btn"),
        shopClosedDelivererSelect: document.getElementById("shop-closed-deliverer-select"),
        shopClosedDelivererSaveBtn: document.getElementById("shop-closed-deliverer-save-btn"),
        shopLightingEnabledSelect: document.getElementById("shop-lighting-enabled-select"),
        shopLightingMainModeSelect: document.getElementById("shop-lighting-main-mode-select"),
        shopLightingStockroomModeSelect: document.getElementById("shop-lighting-stockroom-mode-select"),
        shopLightingExcludeSelect: document.getElementById("shop-lighting-exclude-select"),
        shopLightingLevelInput: document.getElementById("shop-lighting-level-input"),
        shopLightingSaveBtn: document.getElementById("shop-lighting-save-btn"),

        shopFinanceGrid: document.getElementById("shop-finance-grid"),
        shopVaultBody: document.getElementById("shop-vault-body"),
        shopSettlementAccountInput: document.getElementById("shop-settlement-account-input"),
        shopSettlementAccountSaveBtn: document.getElementById("shop-settlement-account-save-btn"),
        shopClearCheckoutBtn: document.getElementById("shop-clear-checkout-btn"),
        shopClearCashierLinksBtn: document.getElementById("shop-clear-cashier-links-btn"),

        shopEmployeesBody: document.getElementById("shop-employees-body"),
        shopEmployeeSelectionInput: document.getElementById("shop-employee-selection-input"),
        shopEmployeeFireBtn: document.getElementById("shop-employee-fire-btn"),

        shopOrdersBody: document.getElementById("shop-orders-body"),
        shopOrderPalletsBody: document.getElementById("shop-order-pallets-body"),
        shopShelfBody: document.getElementById("shop-shelf-body"),
        shopStockroomItemsBody: document.getElementById("shop-stockroom-items-body"),
        shopRoadmapTrack: document.getElementById("shop-roadmap-track"),

        shopDeleteConfirmInput: document.getElementById("shop-delete-confirm-input"),
        shopDeleteBtn: document.getElementById("shop-delete-btn"),

        accountBack: document.getElementById("account-back"),
        accountOpenPlayer: document.getElementById("account-open-player"),
        accountTitle: document.getElementById("account-title"),
        accountSubtitle: document.getElementById("account-subtitle"),
        accountKpis: document.getElementById("account-kpis"),
        accountSummaryGrid: document.getElementById("account-summary-grid"),
        accountLimitsGrid: document.getElementById("account-limits-grid"),
        accountRolesBody: document.getElementById("account-roles-body"),
        accountSafeboxBody: document.getElementById("account-safebox-body"),
        accountCardsBody: document.getElementById("account-cards-body"),
        accountLoansBody: document.getElementById("account-loans-body"),
        accountTransactionsBody: document.getElementById("account-transactions-body"),
        accountActionOutput: document.getElementById("account-action-output"),

        accountFreezeReason: document.getElementById("account-freeze-reason"),
        accountFreezeBtn: document.getElementById("account-freeze-btn"),
        accountUnfreezeBtn: document.getElementById("account-unfreeze-btn"),
        accountSetPrimaryBtn: document.getElementById("account-set-primary-btn"),

        accountAmountInput: document.getElementById("account-amount-input"),
        accountDepositBtn: document.getElementById("account-deposit-btn"),
        accountWithdrawBtn: document.getElementById("account-withdraw-btn"),

        accountCreditInput: document.getElementById("account-credit-input"),
        accountCreditSetBtn: document.getElementById("account-credit-set-btn"),
        accountCreditDeltaInput: document.getElementById("account-credit-delta-input"),
        accountCreditAdjustBtn: document.getElementById("account-credit-adjust-btn"),

        accountDefaultedSelect: document.getElementById("account-defaulted-select"),
        accountDefaultedSetBtn: document.getElementById("account-defaulted-set-btn"),
        accountPinInput: document.getElementById("account-pin-input"),
        accountPinSetBtn: document.getElementById("account-pin-set-btn"),

        accountAccessTypeSelect: document.getElementById("account-access-type-select"),
        accountBusinessLabelInput: document.getElementById("account-business-label-input"),
        accountAccessSaveBtn: document.getElementById("account-access-save-btn"),

        accountTempLimitInput: document.getElementById("account-temp-limit-input"),
        accountTempLimitSetBtn: document.getElementById("account-temp-limit-set-btn"),
        accountTempLimitClearBtn: document.getElementById("account-temp-limit-clear-btn"),
        accountDailyClearBtn: document.getElementById("account-daily-clear-btn"),

        accountRolePlayerIdInput: document.getElementById("account-role-player-id-input"),
        accountRoleSelect: document.getElementById("account-role-select"),
        accountRoleGrantBtn: document.getElementById("account-role-grant-btn"),
        accountRoleRevokeBtn: document.getElementById("account-role-revoke-btn"),

        accountSafeboxSlotInput: document.getElementById("account-safebox-slot-input"),
        accountSafeboxItemInput: document.getElementById("account-safebox-item-input"),
        accountSafeboxCountInput: document.getElementById("account-safebox-count-input"),
        accountSafeboxAddBtn: document.getElementById("account-safebox-add-btn"),
        accountSafeboxDeliverBtn: document.getElementById("account-safebox-deliver-btn"),
        accountSafeboxDeleteBtn: document.getElementById("account-safebox-delete-btn"),

        accountReplacementHolderInput: document.getElementById("account-replacement-holder-input"),
        accountCreateCardBtn: document.getElementById("account-create-card-btn"),
        accountBlockCardsBtn: document.getElementById("account-block-cards-btn"),

        accountLoanIdInput: document.getElementById("account-loan-id-input"),
        accountLoanDefaultedSelect: document.getElementById("account-loan-defaulted-select"),
        accountLoanRemainingInput: document.getElementById("account-loan-remaining-input"),
        accountLoanDefaultedBtn: document.getElementById("account-loan-defaulted-btn"),
        accountLoanRemainingBtn: document.getElementById("account-loan-remaining-btn"),
        accountLoanDeleteBtn: document.getElementById("account-loan-delete-btn"),

        accountDeleteConfirmInput: document.getElementById("account-delete-confirm-input"),
        accountDeleteBtn: document.getElementById("account-delete-btn"),

        webStatusCmd: document.getElementById("web-status-cmd"),
        webOnCmd: document.getElementById("web-on-cmd"),
        webOffCmd: document.getElementById("web-off-cmd"),
        webLinkCmd: document.getElementById("web-link-cmd"),
        serverCommand: document.getElementById("server-command"),
        runCommand: document.getElementById("run-command"),
        commandOutput: document.getElementById("command-output"),

        auditOutput: document.getElementById("audit-output"),
        hoverTooltip: document.getElementById("hover-tooltip")
    };

    el.sessionId.textContent = state.sessionId;
    const hoverTipState = {
        target: null,
        showTimer: null,
        visible: false,
        x: 0,
        y: 0
    };
    const itemModelState = {
        modelByItemId: new Map(),
        textureImageByUrl: new Map(),
        textureColorByUrl: new Map()
    };

    applyTheme(state.theme);

    function api(path, options) {
        const opts = options || {};
        const headers = Object.assign({}, opts.headers || {}, {"X-Session-Id": state.sessionId});
        if (opts.body && !headers["Content-Type"]) {
            headers["Content-Type"] = "application/json";
        }
        return fetch(path, Object.assign({}, opts, {headers: headers}))
            .then(async response => {
                const json = await response.json().catch(() => ({}));
                if (!response.ok) {
                    throw new Error(json.message || ("HTTP " + response.status));
                }
                return json;
            });
    }

    function html(text) {
        return String(text == null ? "" : text)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;");
    }

    const SCALE_SUFFIXES = [
        "", "K", "M", "B", "T",
        "Qa", "Qi", "Sx", "Sp", "Oc", "No",
        "Dc", "Ud", "Dd", "Td", "Qad", "Qid", "Sxd", "Spd", "Ocd", "Nod"
    ];

    function truncateTowardZero(value, decimals) {
        const factor = Math.pow(10, decimals);
        if (value < 0) {
            return Math.ceil(value * factor) / factor;
        }
        return Math.floor(value * factor) / factor;
    }

    function parseLooseNumber(value) {
        if (typeof value === "number") {
            return Number.isFinite(value) ? value : null;
        }
        if (typeof value === "bigint") {
            const cast = Number(value);
            return Number.isFinite(cast) ? cast : null;
        }

        const raw = String(value == null ? "" : value).trim();
        if (!raw) {
            return 0;
        }
        const cleaned = raw.replace(/[$€£¥\s]/g, "");
        if (!cleaned) {
            return 0;
        }

        const direct = Number(cleaned);
        if (Number.isFinite(direct)) {
            return direct;
        }

        const commaCount = (cleaned.match(/,/g) || []).length;
        const dotCount = (cleaned.match(/\./g) || []).length;
        let normalized = cleaned;

        if (commaCount > 0 && dotCount > 0) {
            const lastComma = cleaned.lastIndexOf(",");
            const lastDot = cleaned.lastIndexOf(".");
            const decimalIsComma = lastComma > lastDot;
            if (decimalIsComma) {
                normalized = cleaned.replace(/\./g, "").replace(",", ".");
            } else {
                normalized = cleaned.replace(/,/g, "");
            }
        } else if (commaCount > 1 && dotCount === 0) {
            normalized = cleaned.replace(/,/g, "");
        } else if (dotCount > 1 && commaCount === 0) {
            normalized = cleaned.replace(/\./g, "");
        } else if (commaCount === 1 && dotCount === 0) {
            const commaIndex = cleaned.lastIndexOf(",");
            const decimals = cleaned.length - commaIndex - 1;
            normalized = decimals <= 2 ? cleaned.replace(",", ".") : cleaned.replace(/,/g, "");
        } else if (dotCount === 1 && commaCount === 0) {
            const dotIndex = cleaned.lastIndexOf(".");
            const decimals = cleaned.length - dotIndex - 1;
            normalized = decimals <= 2 ? cleaned : cleaned.replace(/\./g, "");
        }

        const parsed = Number(normalized);
        return Number.isFinite(parsed) ? parsed : null;
    }

    function abbreviateDigitsString(raw) {
        const signed = String(raw == null ? "" : raw).trim();
        if (!signed) {
            return "0";
        }

        const negative = signed.startsWith("-");
        const digits = signed.replace(/[^\d]/g, "").replace(/^0+/, "");
        if (!digits) {
            return "0";
        }

        const scaleIndex = Math.min(
            SCALE_SUFFIXES.length - 1,
            Math.max(0, Math.floor((digits.length - 1) / 3))
        );
        const leadLen = Math.max(1, digits.length - (scaleIndex * 3));
        const lead = digits.slice(0, leadLen);
        const decimalTail = digits.slice(leadLen, leadLen + 2).replace(/0+$/, "");
        const core = decimalTail ? (lead + "." + decimalTail) : lead;
        return (negative ? "-" : "") + core + SCALE_SUFFIXES[scaleIndex];
    }

    function abbreviateNumber(value) {
        const num = parseLooseNumber(value);
        if (num == null) {
            return abbreviateDigitsString(value);
        }

        let scaleIndex = 0;
        let abs = Math.abs(num);
        while (scaleIndex + 1 < SCALE_SUFFIXES.length && abs >= 1000) {
            abs /= 1000;
            scaleIndex++;
        }

        const divisor = Math.pow(1000, scaleIndex);
        const shortened = truncateTowardZero(num / divisor, 2);
        const normalized = shortened.toFixed(2).replace(/\.?0+$/, "");
        const safe = normalized === "-0" ? "0" : normalized;
        return safe + SCALE_SUFFIXES[scaleIndex];
    }

    function money(value) {
        return "$" + abbreviateNumber(value);
    }

    function clamp(num, min, max) {
        const value = Number(num || 0);
        if (!Number.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    function percent(value, digits) {
        const safeDigits = Number.isFinite(digits) ? Math.max(0, Math.min(3, Math.round(digits))) : 1;
        const num = Number(value || 0);
        if (!Number.isFinite(num)) {
            return "0%";
        }
        return num.toFixed(safeDigits) + "%";
    }

    // Shared numeric formatter for analytics tiles/tables (keeps 0-2 decimals, strips trailing zeroes).
    function round2(value) {
        const parsed = parseLooseNumber(value);
        const num = Number(parsed == null ? value : parsed);
        if (!Number.isFinite(num)) {
            return "0";
        }
        const rounded = Math.round(num * 100) / 100;
        const normalized = rounded.toFixed(2).replace(/\.?0+$/, "");
        return normalized === "-0" ? "0" : normalized;
    }

    function formatMs(value) {
        const num = Number(value || 0);
        if (!Number.isFinite(num)) {
            return "0.00 ms";
        }
        return num.toFixed(2) + " ms";
    }

    function formatBytes(bytes) {
        const value = Number(bytes || 0);
        if (!Number.isFinite(value) || value <= 0) {
            return "0 B";
        }
        const units = ["B", "KB", "MB", "GB", "TB"];
        let idx = 0;
        let scaled = value;
        while (scaled >= 1024 && idx < units.length - 1) {
            scaled /= 1024;
            idx++;
        }
        const fixed = scaled >= 100 ? 0 : (scaled >= 10 ? 1 : 2);
        return scaled.toFixed(fixed) + " " + units[idx];
    }

    function dateLabel(epochMillis, includeTime) {
        const ms = Number(epochMillis || 0);
        if (!Number.isFinite(ms) || ms <= 0) {
            return "-";
        }
        const date = new Date(ms);
        if (Number.isNaN(date.getTime())) {
            return "-";
        }
        const mm = String(date.getMonth() + 1).padStart(2, "0");
        const dd = String(date.getDate()).padStart(2, "0");
        if (!includeTime) {
            return mm + "-" + dd;
        }
        const hh = String(date.getHours()).padStart(2, "0");
        return mm + "-" + dd + " " + hh + ":00";
    }

    function renderInfoCards(container, entries) {
        if (!container) {
            return;
        }
        container.textContent = "";
        for (const entry of entries || []) {
            const item = document.createElement("article");
            item.className = "kv-item";
            const key = String(entry.label || "");
            const value = String(entry.value == null ? "-" : entry.value);
            const hint = String(entry.hint || "").trim();
            if (hint) {
                item.dataset.tooltip = hint;
                item.tabIndex = 0;
            }
            item.innerHTML = "<span class=\"k\">" + html(key) + "</span><span class=\"v\">" + html(value) + "</span>";
            container.appendChild(item);
        }
    }

    function normalizeLabelKey(label) {
        return String(label || "").trim().toLowerCase();
    }

    function classifyImpactStage(pct) {
        const value = clamp(pct, 0, 100);
        if (value < 10) {
            return {band: "Excellent", effect: "very low impact", tone: "good"};
        }
        if (value < 25) {
            return {band: "Good", effect: "low impact", tone: "good"};
        }
        if (value < 40) {
            return {band: "Moderate", effect: "noticeable under peak load", tone: "watch"};
        }
        if (value < 60) {
            return {band: "High", effect: "likely contributes to lag", tone: "bad"};
        }
        return {band: "Critical", effect: "major lag contributor", tone: "bad"};
    }

    function impactStageGuideText() {
        return "Guide: 0-10 Excellent, 10-25 Good, 25-40 Moderate, 40-60 High, 60-100 Critical. Lower is better.";
    }

    function explainUbsShareOfTick(pct) {
        const stage = classifyImpactStage(pct);
        return "Current: " + percent(pct, 1) + ". Stage: " + stage.band + " (" + stage.effect + "). " + impactStageGuideText();
    }

    function explainUbsTickBudget(pct) {
        const stage = classifyImpactStage(pct);
        return "Current: " + percent(pct, 1) + " of the 50ms tick budget. Stage: " + stage.band + " (" + stage.effect + "). " + impactStageGuideText();
    }

    function explainLagShare(pct) {
        const stage = classifyImpactStage(pct);
        return "Current estimated lag contribution: " + percent(pct, 1) + ". Stage: " + stage.band + " (" + stage.effect + "). " + impactStageGuideText();
    }

    function explainServerMspt(mspt) {
        const value = Number(mspt || 0);
        if (value <= 0) {
            return "Average server tick duration. Lower is better; above 50ms means the server cannot maintain 20 TPS.";
        }
        let state = "Healthy";
        if (value >= 50) {
            state = "Critical";
        } else if (value >= 40) {
            state = "Warning";
        } else if (value >= 30) {
            state = "Moderate";
        }
        return "Current: " + formatMs(value) + " (" + state + "). Under 30ms is good. 40-50ms is degraded. 50ms+ is lag.";
    }

    function explainServerTps(tps) {
        const value = Number(tps || 0);
        let state = "Healthy";
        if (value < 15) {
            state = "Critical";
        } else if (value < 18) {
            state = "Warning";
        } else if (value < 19.5) {
            state = "Moderate";
        }
        return "Current: " + value.toFixed(2) + " TPS (" + state + "). Target is 20 TPS. Lower TPS means delayed game logic.";
    }

    function explainCpuUsage(pct) {
        const value = Number(pct || 0);
        if (value < 0) {
            return "Process CPU load of the game server process. Lower is better.";
        }
        let state = "Healthy";
        if (value >= 90) {
            state = "Critical";
        } else if (value >= 80) {
            state = "Warning";
        } else if (value >= 60) {
            state = "Moderate";
        }
        return "Current: " + percent(value, 1) + " (" + state + "). Sustained 80%+ usually risks lag spikes.";
    }

    function explainHeapUsage(pct) {
        const value = Number(pct || 0);
        let state = "Healthy";
        if (value >= 90) {
            state = "Critical";
        } else if (value >= 80) {
            state = "Warning";
        } else if (value >= 70) {
            state = "Moderate";
        }
        return "Current: " + percent(value, 1) + " (" + state + "). Higher heap usage can increase GC pressure and stutter.";
    }

    function defaultKpiHint(label, value) {
        const key = normalizeLabelKey(label);
        const map = {
            "money circulating": "Total in-game economy money tracked by UBS across accounts.",
            "total deposits": "Combined deposits across all banks/accounts in UBS.",
            "total reserves": "Combined declared reserve funds held by all banks.",
            "banks": "Total number of banks known to UBS.",
            "shops": "Total number of shops tracked by UBS.",
            "accounts": "Total number of accounts tracked by UBS.",
            "issued cards": "Total credit cards issued by UBS.",
            "online players": "Players currently online on the server.",
            "reserve ratio": "Global reserve coverage percentage. Higher generally means stronger liquidity.",
            "shop revenue": "Aggregate shop revenue tracked by UBS.",
            "web clients": "Connected web dashboard clients.",
            "flagged banks": "Banks currently in non-active status.",
            "active": "Entities currently marked active.",
            "flagged": "Entities currently marked flagged/attention state.",
            "deposits": "Deposited money held for this scope.",
            "reserves": "Reserve money held for this scope.",
            "revenue": "Revenue earned in this scope.",
            "average level": "Average progression level for listed shops.",
            "known users": "Unique users known to UBS data.",
            "offline users": "Known users not currently online.",
            "tracked balance": "Combined balance across the currently listed users.",
            "balance": "Current total balance for this scope.",
            "cards active": "Cards currently usable (not blocked).",
            "cards blocked": "Cards currently blocked and unusable.",
            "owned banks": "Banks owned by this player.",
            "bank roles": "Delegated/cofounder/staff banking roles held by this player.",
            "owned shops": "Shops owned by this player.",
            "avg credit": "Average credit score across the player's accounts.",
            "transactions": "Tracked transaction count for this scope."
        };
        if (map[key]) {
            return map[key] + " Current value: " + value + ".";
        }
        return String(label || "KPI") + ": " + value + ".";
    }

    function meterTone(pct) {
        const safe = Number(pct || 0);
        if (safe >= 75) {
            return "bad";
        }
        if (safe >= 45) {
            return "warn";
        }
        return "ok";
    }

    function shortId(raw) {
        const text = String(raw || "");
        if (text.length <= 14) {
            return text;
        }
        return text.slice(0, 8) + "..." + text.slice(-4);
    }

    function initials(name) {
        const parts = String(name || "")
            .trim()
            .split(/\s+/)
            .filter(Boolean);
        if (parts.length === 0) {
            return "U";
        }
        if (parts.length === 1) {
            return parts[0].slice(0, 1).toUpperCase();
        }
        return (parts[0].slice(0, 1) + parts[1].slice(0, 1)).toUpperCase();
    }

    function itemIconUrl(itemId) {
        const raw = String(itemId || "").trim();
        if (!raw) {
            return "/api/webadmin/item-icon/" + encodeURIComponent("minecraft:barrier");
        }
        return "/api/webadmin/item-icon/" + encodeURIComponent(raw);
    }

    function itemModelUrl(itemId) {
        const raw = String(itemId || "").trim();
        const normalized = raw || "minecraft:barrier";
        return "/api/webadmin/item-model/" + encodeURIComponent(normalized);
    }

    function buildItemVisual(itemId, label) {
        const raw = String(itemId || "").trim() || "minecraft:barrier";
        const title = String(label || raw);
        return "<span class=\"item-visual\" data-item-id=\"" + html(raw) + "\" data-item-title=\"" + html(title) + "\">" +
            "<canvas class=\"item-model-canvas\" width=\"64\" height=\"64\" aria-hidden=\"true\"></canvas>" +
            "<img class=\"item-icon item-icon-fallback\" loading=\"lazy\" src=\"" + html(itemIconUrl(raw)) + "\" alt=\"" + html(title) + "\">" +
            "</span>";
    }

    function colorToCss(color, alpha) {
        const source = color || {r: 70, g: 106, b: 210};
        return "rgba(" + source.r + "," + source.g + "," + source.b + "," + (alpha == null ? 1 : alpha) + ")";
    }

    function shadeColor(color, factor) {
        const base = color || {r: 70, g: 106, b: 210};
        const scale = Number.isFinite(factor) ? factor : 1;
        return {
            r: Math.max(0, Math.min(255, Math.round(base.r * scale))),
            g: Math.max(0, Math.min(255, Math.round(base.g * scale))),
            b: Math.max(0, Math.min(255, Math.round(base.b * scale)))
        };
    }

    function loadTextureImage(url) {
        const key = String(url || "").trim();
        if (!key) {
            return Promise.resolve(null);
        }
        if (itemModelState.textureImageByUrl.has(key)) {
            return itemModelState.textureImageByUrl.get(key);
        }
        const promise = new Promise(resolve => {
            const image = new Image();
            image.crossOrigin = "anonymous";
            image.onload = () => resolve(image);
            image.onerror = () => resolve(null);
            image.src = key;
        });
        itemModelState.textureImageByUrl.set(key, promise);
        return promise;
    }

    function averageTextureColor(url) {
        const key = String(url || "").trim();
        if (!key) {
            return Promise.resolve({r: 70, g: 106, b: 210});
        }
        if (itemModelState.textureColorByUrl.has(key)) {
            return itemModelState.textureColorByUrl.get(key);
        }
        const promise = loadTextureImage(key).then(image => {
            if (!image) {
                return {r: 70, g: 106, b: 210};
            }
            const canvas = document.createElement("canvas");
            const sampleSize = 12;
            canvas.width = sampleSize;
            canvas.height = sampleSize;
            const ctx = canvas.getContext("2d");
            if (!ctx) {
                return {r: 70, g: 106, b: 210};
            }
            ctx.imageSmoothingEnabled = false;
            ctx.drawImage(image, 0, 0, sampleSize, sampleSize);
            const pixels = ctx.getImageData(0, 0, sampleSize, sampleSize).data;
            let sumR = 0;
            let sumG = 0;
            let sumB = 0;
            let count = 0;
            for (let i = 0; i < pixels.length; i += 4) {
                const alpha = pixels[i + 3];
                if (alpha < 24) {
                    continue;
                }
                sumR += pixels[i];
                sumG += pixels[i + 1];
                sumB += pixels[i + 2];
                count += 1;
            }
            if (count <= 0) {
                return {r: 70, g: 106, b: 210};
            }
            return {
                r: Math.round(sumR / count),
                g: Math.round(sumG / count),
                b: Math.round(sumB / count)
            };
        });
        itemModelState.textureColorByUrl.set(key, promise);
        return promise;
    }

    function fetchItemModel(itemId) {
        const key = String(itemId || "").trim() || "minecraft:barrier";
        if (itemModelState.modelByItemId.has(key)) {
            return itemModelState.modelByItemId.get(key);
        }
        const promise = api(itemModelUrl(key))
            .catch(() => null);
        itemModelState.modelByItemId.set(key, promise);
        return promise;
    }

    function dominantTextureUrlsFromElements(elements) {
        const counts = new Map();
        const directionWeight = {
            north: 4,
            south: 4,
            east: 4,
            west: 4,
            up: 2,
            down: 2
        };
        for (const element of Array.isArray(elements) ? elements : []) {
            const faces = (element && typeof element.faces === "object" && element.faces) ? element.faces : {};
            for (const [direction, face] of Object.entries(faces)) {
                const url = String(face && face.textureUrl ? face.textureUrl : "").trim();
                if (!url) {
                    continue;
                }
                const weight = directionWeight[String(direction || "").toLowerCase()] || 1;
                counts.set(url, (counts.get(url) || 0) + weight);
            }
        }
        return Array.from(counts.entries())
            .sort((a, b) => b[1] - a[1])
            .map(entry => entry[0]);
    }

    function drawFlatTextureStack(ctx, images) {
        if (!ctx || !images || images.length === 0) {
            return false;
        }
        const width = ctx.canvas.width;
        const height = ctx.canvas.height;
        const drawSize = Math.floor(Math.min(width, height) * 0.72);
        const x = Math.floor((width - drawSize) / 2);
        const y = Math.floor((height - drawSize) / 2);
        ctx.clearRect(0, 0, width, height);
        ctx.imageSmoothingEnabled = false;

        // Draw subtle hard shadow without tilting the item.
        ctx.save();
        ctx.fillStyle = "rgba(17, 30, 58, 0.12)";
        ctx.fillRect(x + 2, y + drawSize + 1, drawSize - 2, 2);
        ctx.restore();

        for (let i = images.length - 1; i >= 0; i -= 1) {
            const image = images[i];
            const inset = Math.min(2, images.length - 1 - i);
            ctx.globalAlpha = i === 0 ? 1 : 0.96;
            ctx.drawImage(
                image,
                x - inset,
                y - inset,
                drawSize,
                drawSize
            );
        }
        ctx.globalAlpha = 1;
        return true;
    }

    async function drawGeneratedModel(ctx, model) {
        const layers = Array.isArray(model && model.layers) ? model.layers : [];
        if (layers.length === 0) {
            return false;
        }
        const loaded = [];
        for (const layer of layers) {
            const image = await loadTextureImage(layer && layer.textureUrl);
            if (image) {
                loaded.push(image);
            }
        }
        if (loaded.length === 0) {
            return false;
        }
        return drawFlatTextureStack(ctx, loaded);
    }

    async function drawElementModel(ctx, model) {
        const elements = Array.isArray(model && model.elements) ? model.elements : [];
        if (elements.length === 0) {
            return false;
        }
        const textureUrls = dominantTextureUrlsFromElements(elements);
        if (textureUrls.length === 0) {
            return false;
        }
        const loaded = [];
        for (const url of textureUrls.slice(0, 3)) {
            const image = await loadTextureImage(url);
            if (image) {
                loaded.push(image);
            }
        }
        if (loaded.length === 0) {
            return false;
        }
        return drawFlatTextureStack(ctx, loaded);
    }

    async function renderItemModelNode(node) {
        if (!node || node.dataset.modelRendered === "1") {
            return;
        }
        const itemId = String(node.dataset.itemId || "").trim();
        if (!itemId) {
            return;
        }
        const canvas = node.querySelector(".item-model-canvas");
        if (!canvas) {
            return;
        }
        const ctx = canvas.getContext("2d");
        if (!ctx) {
            return;
        }

        node.dataset.modelRendered = "1";
        const model = await fetchItemModel(itemId);
        if (!model || model.ok === false) {
            node.dataset.modelRendered = "0";
            return;
        }

        let rendered = false;
        const mode = String(model.renderMode || "").toLowerCase();
        if (mode === "elements") {
            rendered = await drawElementModel(ctx, model);
        }
        if (!rendered && (mode === "generated" || mode === "elements")) {
            rendered = await drawGeneratedModel(ctx, model);
        }
        if (!rendered) {
            node.dataset.modelRendered = "0";
            return;
        }
        node.classList.add("model-ready");
        const tooltip = model.itemName
            ? (model.itemName + " (" + itemId + ")")
            : itemId;
        node.setAttribute("title", tooltip);
    }

    function hydrateItemModels(root) {
        const scope = root || document;
        const nodes = scope.querySelectorAll(".item-visual[data-item-id]");
        for (const node of nodes) {
            renderItemModelNode(node);
        }
    }

    function classifyRoadmapState(stateLabel, levelValue, shopLevel) {
        const level = Number(levelValue || 0);
        const currentLevel = Number(shopLevel || 0);
        const label = String(stateLabel || "").trim().toUpperCase();
        if (label.includes("CURRENT")) {
            return "current";
        }
        if (label.includes("NEXT")) {
            return "next";
        }
        if (label.includes("UNLOCK") || label.includes("REACHED") || label.includes("ACTIVE")) {
            return "unlocked";
        }
        if (label.includes("LOCK")) {
            return "locked";
        }
        if (currentLevel > 0) {
            if (level < currentLevel) {
                return "unlocked";
            }
            if (level === currentLevel) {
                return "current";
            }
            if (level === currentLevel + 1) {
                return "next";
            }
            return "locked";
        }
        return "locked";
    }

    function setWsState(label, tone) {
        el.wsState.textContent = label;
        el.wsState.dataset.state = tone || "pending";
    }

    function setHealthState(label, tone) {
        el.healthState.textContent = label;
        el.healthState.dataset.state = tone || "pending";
    }

    /**
     * Theme is persisted so admins keep their preferred mode across sessions.
     */
    function applyTheme(theme) {
        const normalized = theme === "dark" ? "dark" : "light";
        state.theme = normalized;
        document.body.dataset.theme = normalized;
        localStorage.setItem(themeStorageKey, normalized);
        if (el.themeToggle) {
            const dark = normalized === "dark";
            el.themeToggle.setAttribute("aria-pressed", dark ? "true" : "false");
            el.themeToggle.setAttribute("aria-label", dark ? "Switch to light mode" : "Switch to dark mode");
        }
        if (el.themeToggleLabel) {
            el.themeToggleLabel.textContent = normalized === "dark" ? "Dark" : "Light";
        }
    }

    function toggleTheme() {
        applyTheme(state.theme === "dark" ? "light" : "dark");
    }

    function parseRoute() {
        const hash = (location.hash || "#/dashboard").replace(/^#/, "");
        const parts = hash.split("/").filter(Boolean);
        if (parts.length === 0) {
            return {page: "dashboard"};
        }
        if (parts[0] === "shop-items" && parts[1]) {
            const rawId = parts.slice(1).join("/");
            let itemId = rawId;
            try {
                itemId = decodeURIComponent(rawId);
            } catch (ignored) {
            }
            return {page: "shop-item", itemId: itemId};
        }
        if (parts[0] === "accounts" && parts[1]) {
            return {page: "account", accountId: parts[1]};
        }
        if (parts[0] === "players" && parts[1] && parts[2] === "accounts" && parts[3]) {
            return {page: "account", playerId: parts[1], accountId: parts[3]};
        }
        if (parts[0] === "players" && parts[1]) {
            return {page: "player", playerId: parts[1]};
        }
        if (parts[0] === "banks" && parts[1]) {
            return {page: "bank", bankId: parts[1]};
        }
        if (parts[0] === "shops" && parts[1]) {
            return {page: "shop", shopId: parts[1]};
        }
        if (parts[0] === "entities") {
            return {page: "banks"};
        }
        if (parts[0] === "health" || parts[0] === "banks" || parts[0] === "shops" || parts[0] === "shop-items" || parts[0] === "users" || parts[0] === "audit" || parts[0] === "dashboard") {
            return {page: parts[0]};
        }
        return {page: "dashboard"};
    }

    function applyRouteMeta(page) {
        const titles = {
            dashboard: ["Dashboard", "Economy health and live operations."],
            health: ["Server Health", "Live server health, lag pressure, and UBS resource impact."],
            banks: ["Banks", "Full banking registry, status, reserves, and account coverage."],
            shops: ["Shops", "All shops with ownership, level progression, and revenue KPIs."],
            "shop-items": ["Shop Items", "Global item pricing index across all shops, with spread and source visibility."],
            "shop-item": ["Item Analytics", "Item-level pricing, demand, and market behavior across all shops."],
            users: ["Users", "All known players with UBS footprint summaries."],
            player: ["Player Detail", "Full saved UBS profile for selected player."],
            bank: ["Bank Detail", "Complete bank profile with governance, accounts, and policy actions."],
            shop: ["Shop Detail", "Full shop profile, operations, and section-level CRUD actions."],
            account: ["Account Detail", "Full account data and direct account-level admin actions."],
            audit: ["Audit", "Operational event stream and command trail."]
        };
        const meta = titles[page] || titles.dashboard;
        el.pageTitle.textContent = meta[0];
        el.pageSubtitle.textContent = meta[1];
    }

    function setActivePage(page, navRoute, metaRoute) {
        for (const [key, node] of Object.entries(el.pages)) {
            node.classList.toggle("active", key === page);
        }
        for (const link of el.navLinks) {
            link.classList.toggle("active", link.dataset.route === navRoute);
        }
        applyRouteMeta(metaRoute || navRoute);
    }

    function renderKpiCards(container, items) {
        container.textContent = "";
        for (const item of items) {
            const label = String(item.label || "");
            const value = String(item.value == null ? "0" : item.value).replace(/\s+/g, " ").trim();
            const hint = String(item.hint || defaultKpiHint(label, value)).replace(/\s+/g, " ").trim();
            const card = document.createElement("article");
            card.className = "kpi-card";
            card.dataset.tooltip = hint;
            card.tabIndex = 0;
            card.innerHTML = "<span>" + html(label) + "</span><strong>" + html(value) + "</strong>";
            container.appendChild(card);
        }
    }

    function clearHoverTipTimer() {
        if (hoverTipState.showTimer) {
            clearTimeout(hoverTipState.showTimer);
            hoverTipState.showTimer = null;
        }
    }

    function tooltipTargetFromNode(node) {
        if (!(node instanceof Element)) {
            return null;
        }
        return node.closest("[data-tooltip]");
    }

    function tooltipTextFromTarget(target) {
        if (!target || !(target instanceof Element)) {
            return "";
        }
        return String(target.dataset.tooltip || "").trim();
    }

    function positionHoverTip(clientX, clientY) {
        const tip = el.hoverTooltip;
        if (!tip) {
            return;
        }
        const margin = 10;
        const offsetX = 14;
        const offsetY = 16;
        const viewportW = window.innerWidth || document.documentElement.clientWidth || 0;
        const viewportH = window.innerHeight || document.documentElement.clientHeight || 0;
        const rect = tip.getBoundingClientRect();

        let left = clientX + offsetX;
        let top = clientY + offsetY;
        if ((left + rect.width + margin) > viewportW) {
            left = Math.max(margin, clientX - rect.width - 10);
        }
        if ((top + rect.height + margin) > viewportH) {
            top = Math.max(margin, clientY - rect.height - 10);
        }

        tip.style.left = Math.round(left) + "px";
        tip.style.top = Math.round(top) + "px";
    }

    function showHoverTip(target) {
        const tip = el.hoverTooltip;
        if (!tip) {
            return;
        }
        const text = tooltipTextFromTarget(target);
        if (!text) {
            hideHoverTip();
            return;
        }
        hoverTipState.target = target;
        tip.textContent = text;
        tip.setAttribute("aria-hidden", "false");
        tip.classList.add("visible");
        hoverTipState.visible = true;
        positionHoverTip(hoverTipState.x, hoverTipState.y);
    }

    function hideHoverTip() {
        clearHoverTipTimer();
        hoverTipState.target = null;
        hoverTipState.visible = false;
        const tip = el.hoverTooltip;
        if (!tip) {
            return;
        }
        tip.classList.remove("visible");
        tip.setAttribute("aria-hidden", "true");
        tip.textContent = "";
    }

    function queueHoverTip(target, immediate) {
        clearHoverTipTimer();
        if (!target) {
            hideHoverTip();
            return;
        }
        if (immediate) {
            showHoverTip(target);
            return;
        }
        hoverTipState.showTimer = setTimeout(() => {
            hoverTipState.showTimer = null;
            showHoverTip(target);
        }, 45);
    }

    /**
     * Custom tooltip enables fast, styled KPI descriptions for the full card area.
     */
    function bindHoverTooltips() {
        document.addEventListener("pointerover", event => {
            const target = tooltipTargetFromNode(event.target);
            if (!target) {
                hideHoverTip();
                return;
            }
            hoverTipState.x = event.clientX;
            hoverTipState.y = event.clientY;
            queueHoverTip(target, false);
        });

        document.addEventListener("pointermove", event => {
            if (!hoverTipState.target && !hoverTipState.visible) {
                return;
            }
            hoverTipState.x = event.clientX;
            hoverTipState.y = event.clientY;
            if (hoverTipState.visible) {
                positionHoverTip(event.clientX, event.clientY);
            }
        });

        document.addEventListener("pointerout", event => {
            if (!hoverTipState.target) {
                return;
            }
            const from = tooltipTargetFromNode(event.target);
            const to = tooltipTargetFromNode(event.relatedTarget);
            if (from && to && from === to) {
                return;
            }
            if (from && from === hoverTipState.target) {
                hideHoverTip();
            }
        });

        document.addEventListener("focusin", event => {
            const target = tooltipTargetFromNode(event.target);
            if (!target) {
                return;
            }
            const rect = target.getBoundingClientRect();
            hoverTipState.x = rect.left + (rect.width / 2);
            hoverTipState.y = rect.top + Math.min(24, rect.height / 2);
            queueHoverTip(target, true);
        });

        document.addEventListener("focusout", event => {
            const target = tooltipTargetFromNode(event.target);
            if (target && hoverTipState.target === target) {
                hideHoverTip();
            }
        });

        window.addEventListener("scroll", hideHoverTip, true);
        window.addEventListener("blur", hideHoverTip);
    }

    function clearSvg(svg) {
        while (svg.firstChild) {
            svg.removeChild(svg.firstChild);
        }
    }

    function svgNode(name, attrs) {
        const node = document.createElementNS("http://www.w3.org/2000/svg", name);
        const config = attrs || {};
        for (const key of Object.keys(config)) {
            node.setAttribute(key, String(config[key]));
        }
        if (name === "text") {
            if (!("font-family" in config)) {
                node.setAttribute("font-family", "Inter, Segoe UI, system-ui, sans-serif");
            }
            if (!("letter-spacing" in config)) {
                node.setAttribute("letter-spacing", "0");
            }
            if (!("font-stretch" in config)) {
                node.setAttribute("font-stretch", "normal");
            }
        }
        return node;
    }

    function clipText(raw, maxLen) {
        const text = String(raw || "");
        if (text.length <= maxLen) {
            return text;
        }
        return text.slice(0, Math.max(1, maxLen - 1)) + "...";
    }

    function chartViewport(svg, fallbackWidth, fallbackHeight) {
        const width = Math.max(1, Math.round(svg.clientWidth || fallbackWidth));
        const height = Math.max(1, Math.round(svg.clientHeight || fallbackHeight));
        svg.setAttribute("viewBox", "0 0 " + width + " " + height);
        return {width: width, height: height};
    }

    function renderLineChart(svg, seriesA, seriesB) {
        clearSvg(svg);
        const width = Number(svg.viewBox.baseVal.width || 900);
        const height = Number(svg.viewBox.baseVal.height || 250);
        const pad = 28;
        const a = seriesA || [];
        const b = seriesB || [];
        const values = a.concat(b).map(point => Number(point.value || 0));
        const max = Math.max(1, ...values);

        for (let i = 0; i < 4; i++) {
            const y = pad + ((height - (pad * 2)) * i / 3);
            svg.appendChild(svgNode("line", {
                x1: pad,
                y1: y,
                x2: width - pad,
                y2: y,
                stroke: "#e5ebf5",
                "stroke-width": 1
            }));
        }

        function draw(points, stroke, fill) {
            if (points.length < 2) {
                return;
            }
            const step = (width - (pad * 2)) / Math.max(1, points.length - 1);
            const poly = [];
            for (let i = 0; i < points.length; i++) {
                const x = pad + (step * i);
                const y = height - pad - ((height - (pad * 2)) * (Number(points[i].value || 0) / max));
                poly.push(x + "," + y);
            }
            svg.appendChild(svgNode("polyline", {
                points: poly.join(" "),
                fill: "none",
                stroke: stroke,
                "stroke-width": 2.5,
                "stroke-linejoin": "round",
                "stroke-linecap": "round"
            }));

            if (fill) {
                const area = poly.concat((width - pad) + "," + (height - pad), pad + "," + (height - pad));
                svg.appendChild(svgNode("polygon", {
                    points: area.join(" "),
                    fill: fill
                }));
            }
        }

        draw(b, "#7ec8ff", "rgba(126, 200, 255, 0.16)");
        draw(a, "#2f55d4", "rgba(47, 85, 212, 0.14)");
    }

    function renderBarChart(svg, rows, labelKey) {
        clearSvg(svg);
        const width = Number(svg.viewBox.baseVal.width || 420);
        const height = Number(svg.viewBox.baseVal.height || 230);
        const data = rows || [];
        if (data.length === 0) {
            return;
        }
        const pad = 24;
        const max = Math.max(1, ...data.map(row => Number(row.count || 0)));
        const barW = (width - (pad * 2)) / data.length;

        for (let i = 0; i < data.length; i++) {
            const item = data[i];
            const h = (height - (pad * 2)) * (Number(item.count || 0) / max);
            const x = pad + (i * barW) + 8;
            const y = height - pad - h;

            svg.appendChild(svgNode("rect", {
                x: x,
                y: y,
                width: Math.max(12, barW - 16),
                height: h,
                rx: 5,
                fill: "#2f55d4"
            }));

            svg.appendChild(svgNode("text", {
                x: x + ((Math.max(12, barW - 16)) / 2),
                y: height - 8,
                "text-anchor": "middle",
                "font-size": "11",
                fill: "#5f6f89"
            })).textContent = String(item[labelKey] || "").slice(0, 8);
        }
    }

    function renderDonutChart(svg, segments) {
        clearSvg(svg);
        const width = Number(svg.viewBox.baseVal.width || 420);
        const height = Number(svg.viewBox.baseVal.height || 230);
        const cx = width / 2;
        const cy = height / 2;
        const radius = Math.min(width, height) * 0.28;
        const total = Math.max(1, ...[segments.reduce((acc, seg) => acc + Number(seg.value || 0), 0)]);
        const colors = ["#2f55d4", "#4b7ef7", "#7aa2ff", "#9fb8ff", "#c1d3ff", "#d4e1ff"];
        let start = -Math.PI / 2;

        function arcPath(from, to) {
            const x1 = cx + (radius * Math.cos(from));
            const y1 = cy + (radius * Math.sin(from));
            const x2 = cx + (radius * Math.cos(to));
            const y2 = cy + (radius * Math.sin(to));
            const large = (to - from) > Math.PI ? 1 : 0;
            return [
                "M", x1, y1,
                "A", radius, radius, 0, large, 1, x2, y2
            ].join(" ");
        }

        for (let i = 0; i < segments.length; i++) {
            const seg = segments[i];
            const value = Number(seg.value || 0);
            if (value <= 0) {
                continue;
            }
            const angle = (value / total) * Math.PI * 2;
            const end = start + angle;
            svg.appendChild(svgNode("path", {
                d: arcPath(start, end),
                stroke: colors[i % colors.length],
                "stroke-width": 20,
                fill: "none",
                "stroke-linecap": "round"
            }));
            start = end;
        }

        svg.appendChild(svgNode("text", {
            x: cx,
            y: cy + 5,
            "text-anchor": "middle",
            "font-size": "14",
            "font-weight": "700",
            fill: "#1c2a45"
        })).textContent = String(total);
    }

    function sampleRows(rows, maxPoints) {
        const list = rows || [];
        if (list.length <= maxPoints) {
            return list.slice();
        }
        const out = [];
        const slots = Math.max(2, maxPoints);
        const step = (list.length - 1) / (slots - 1);
        let lastIndex = -1;
        for (let i = 0; i < slots; i++) {
            const index = Math.round(i * step);
            if (index !== lastIndex && list[index]) {
                out.push(list[index]);
                lastIndex = index;
            }
        }
        const tail = list[list.length - 1];
        if (tail && out[out.length - 1] !== tail) {
            out.push(tail);
        }
        return out;
    }

    function renderEconomyTrendChart(svg, historyRows, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 900, 250);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (historyRows || [])
            .map(row => ({
                ts: Number(row.capturedAtMillis || 0),
                deposits: parseLooseNumber(row.totalDeposits) || 0,
                reserves: parseLooseNumber(row.totalReserves) || 0,
                circulating: parseLooseNumber(row.moneyCirculating) || 0
            }))
            .filter(row => row.ts > 0)
            .sort((a, b) => a.ts - b.ts);

        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No economy history available";
            renderInfoCards(infoContainer, [
                {label: "History", value: "No data"},
                {label: "Action", value: "Wait for snapshots"}
            ]);
            return;
        }

        const sampled = sampleRows(rows, 96);
        const seriesDefs = [
            {key: "deposits", label: "Deposits", color: "#2f55d4", fill: "rgba(47, 85, 212, 0.10)", dash: ""},
            {key: "reserves", label: "Reserves", color: "#1a9d5c", fill: "rgba(26, 157, 92, 0.08)", dash: "4 3"}
        ];
        const includeCirculating = sampled.some(row => Math.abs(row.circulating - row.deposits) > 0.01);
        if (includeCirculating) {
            seriesDefs.push({key: "circulating", label: "Circulating", color: "#7b8dac", fill: "", dash: "6 4"});
        }

        const values = [];
        for (const row of sampled) {
            for (const def of seriesDefs) {
                values.push(Number(row[def.key] || 0));
            }
        }
        let min = Math.min(...values);
        let max = Math.max(...values);
        if (!Number.isFinite(min)) {
            min = 0;
        }
        if (!Number.isFinite(max)) {
            max = 1;
        }
        if (max <= min) {
            max = min + 1;
        }
        const rangePad = (max - min) * 0.1;
        const floor = Math.max(0, min - rangePad);
        const ceiling = max + rangePad;
        const plotMin = floor;
        const plotMax = Math.max(plotMin + 1, ceiling);

        const padL = 60;
        const padR = 16;
        const padT = 16;
        const padB = 36;
        const plotW = Math.max(120, width - padL - padR);
        const plotH = Math.max(100, height - padT - padB);

        for (let i = 0; i <= 4; i++) {
            const ratio = i / 4;
            const y = padT + (plotH * ratio);
            const value = plotMax - ((plotMax - plotMin) * ratio);
            svg.appendChild(svgNode("line", {
                x1: padL,
                y1: y,
                x2: width - padR,
                y2: y,
                stroke: "#e3eaf6",
                "stroke-width": 1
            }));
            svg.appendChild(svgNode("text", {
                x: padL - 8,
                y: y + 4,
                "text-anchor": "end",
                "font-size": 10,
                fill: "#6b7890"
            })).textContent = money(value);
        }

        const step = sampled.length > 1 ? (plotW / (sampled.length - 1)) : 0;
        const xTickEvery = Math.max(1, Math.ceil(sampled.length / 6));
        for (let i = 0; i < sampled.length; i++) {
            if (i !== 0 && i !== sampled.length - 1 && (i % xTickEvery) !== 0) {
                continue;
            }
            const x = padL + (step * i);
            svg.appendChild(svgNode("text", {
                x: x,
                y: height - 10,
                "text-anchor": "middle",
                "font-size": 10,
                fill: "#6b7890"
            })).textContent = dateLabel(sampled[i].ts, false);
        }

        function yFor(value) {
            const ratio = (Number(value || 0) - plotMin) / (plotMax - plotMin);
            return padT + (plotH - (ratio * plotH));
        }

        for (const def of seriesDefs) {
            const points = [];
            for (let i = 0; i < sampled.length; i++) {
                const x = padL + (step * i);
                const y = yFor(sampled[i][def.key]);
                points.push(x + "," + y);
            }
            if (points.length >= 2 && def.fill) {
                const area = points.concat((width - padR) + "," + (height - padB), padL + "," + (height - padB));
                svg.appendChild(svgNode("polygon", {
                    points: area.join(" "),
                    fill: def.fill
                }));
            }
            svg.appendChild(svgNode("polyline", {
                points: points.join(" "),
                fill: "none",
                stroke: def.color,
                "stroke-width": 2.3,
                "stroke-dasharray": def.dash,
                "stroke-linecap": "round",
                "stroke-linejoin": "round"
            }));

            const markerStep = Math.max(1, Math.ceil(sampled.length / 20));
            for (let i = 0; i < sampled.length; i += markerStep) {
                const row = sampled[i];
                const x = padL + (step * i);
                const y = yFor(row[def.key]);
                const marker = svgNode("circle", {
                    cx: x,
                    cy: y,
                    r: 3,
                    fill: def.color,
                    stroke: "#ffffff",
                    "stroke-width": 1.2
                });
                const reserveRatio = row.deposits > 0 ? ((row.reserves / row.deposits) * 100) : 0;
                const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
                title.textContent = dateLabel(row.ts, true)
                    + " | Deposits: " + money(row.deposits)
                    + " | Reserves: " + money(row.reserves)
                    + " | Reserve ratio: " + percent(reserveRatio, 2);
                marker.appendChild(title);
                svg.appendChild(marker);
            }
        }

        // Legend
        let legendX = padL + 4;
        const legendY = padT + 10;
        for (const def of seriesDefs) {
            svg.appendChild(svgNode("line", {
                x1: legendX,
                y1: legendY,
                x2: legendX + 16,
                y2: legendY,
                stroke: def.color,
                "stroke-width": 2.4,
                "stroke-dasharray": def.dash
            }));
            svg.appendChild(svgNode("text", {
                x: legendX + 20,
                y: legendY + 4,
                "font-size": 10,
                fill: "#42577a"
            })).textContent = def.label;
            legendX += 96;
        }

        const latest = rows[rows.length - 1];
        const baseline24 = rows.find(row => row.ts >= (latest.ts - (24 * 60 * 60 * 1000))) || rows[0];
        const deltaDeposits = latest.deposits - baseline24.deposits;
        const deltaPct = baseline24.deposits > 0 ? (deltaDeposits / baseline24.deposits) * 100 : 0;
        const latestReservePct = latest.deposits > 0 ? (latest.reserves / latest.deposits) * 100 : 0;

        let directionLabel = "Flat";
        if (deltaDeposits > 0.009) {
            directionLabel = "Rising";
        } else if (deltaDeposits < -0.009) {
            directionLabel = "Falling";
        }

        renderInfoCards(infoContainer, [
            {
                label: "Latest Deposits",
                value: money(latest.deposits),
                hint: "Most recent total deposits snapshot."
            },
            {
                label: "Latest Reserves",
                value: money(latest.reserves),
                hint: "Most recent reserve pool snapshot."
            },
            {
                label: "24h Deposit Change",
                value: (deltaDeposits >= 0 ? "+" : "-") + money(Math.abs(deltaDeposits)) + " (" + percent(Math.abs(deltaPct), 2) + ")",
                hint: "Change in deposits between now and ~24h ago."
            },
            {
                label: "Trend",
                value: directionLabel + " | Reserve " + percent(latestReservePct, 2),
                hint: "Direction summary and current reserve coverage."
            },
            {
                label: "Data Window",
                value: dateLabel(rows[0].ts, true) + " -> " + dateLabel(latest.ts, true),
                hint: "Time range currently displayed."
            }
        ]);
    }

    function statusColor(status) {
        const key = String(status || "").toUpperCase();
        if (key === "ACTIVE") {
            return "#1a9d5c";
        }
        if (key === "FLAGGED" || key === "SUSPENDED" || key === "BLOCKED") {
            return "#c13d3d";
        }
        if (key === "PENDING" || key === "REVIEW") {
            return "#c38914";
        }
        return "#2f55d4";
    }

    function renderBankStatusChart(svg, statusRows, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 420, 230);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (statusRows || [])
            .map(row => ({
                label: String(row.status || "UNKNOWN"),
                count: Math.max(0, Math.round(parseLooseNumber(row.count) || 0))
            }))
            .filter(row => row.count > 0)
            .sort((a, b) => b.count - a.count);

        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No bank status data";
            renderInfoCards(infoContainer, [{label: "Status", value: "No data"}]);
            return;
        }

        const total = rows.reduce((acc, row) => acc + row.count, 0);
        const max = Math.max(1, ...rows.map(row => row.count));
        const shown = rows.slice(0, 6);
        const padL = 86;
        const padR = 58;
        const padT = 20;
        const rowGap = 12;
        const rowH = Math.max(14, Math.floor((height - padT - 14 - ((shown.length - 1) * rowGap)) / shown.length));
        const barAreaW = Math.max(80, width - padL - padR);

        for (let i = 0; i < shown.length; i++) {
            const row = shown[i];
            const y = padT + (i * (rowH + rowGap));
            const fillW = Math.max(2, Math.round((row.count / max) * barAreaW));
            const pct = total > 0 ? (row.count / total) * 100 : 0;
            const color = statusColor(row.label);

            svg.appendChild(svgNode("text", {
                x: 8,
                y: y + rowH - 2,
                "font-size": 11,
                fill: "#3c4f70",
                "font-weight": "600"
            })).textContent = clipText(row.label, 10);

            svg.appendChild(svgNode("rect", {
                x: padL,
                y: y,
                width: barAreaW,
                height: rowH,
                rx: 6,
                fill: "#edf2fb"
            }));

            const bar = svgNode("rect", {
                x: padL,
                y: y,
                width: fillW,
                height: rowH,
                rx: 6,
                fill: color
            });
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent = row.label + ": " + row.count + " bank(s), " + percent(pct, 2);
            bar.appendChild(title);
            svg.appendChild(bar);

            svg.appendChild(svgNode("text", {
                x: padL + barAreaW + 8,
                y: y + rowH - 2,
                "font-size": 11,
                fill: "#2e3f5f",
                "font-weight": "700"
            })).textContent = row.count + " (" + percent(pct, 1) + ")";
        }

        const activeCount = rows.find(row => row.label.toUpperCase() === "ACTIVE")?.count || 0;
        const activePct = total > 0 ? (activeCount / total) * 100 : 0;
        let health = "Needs Attention";
        if (activePct >= 95) {
            health = "Healthy";
        } else if (activePct >= 80) {
            health = "Mostly Healthy";
        }

        renderInfoCards(infoContainer, [
            {label: "Total Banks", value: abbreviateNumber(total), hint: "Total banks counted in status distribution."},
            {label: "Active Ratio", value: percent(activePct, 1), hint: "Share of banks in ACTIVE state."},
            {label: "Top Status", value: shown[0].label + " (" + shown[0].count + ")", hint: "Most common current status."},
            {label: "Readout", value: health, hint: "Status health summary based on active-bank ratio."}
        ]);
    }

    function renderShopTypeMixChart(svg, typeRows, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 420, 230);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (typeRows || [])
            .map(row => ({
                label: String(row.type || "Unknown"),
                count: Math.max(0, Math.round(parseLooseNumber(row.count) || 0))
            }))
            .filter(row => row.count > 0)
            .sort((a, b) => b.count - a.count);

        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No shop type data";
            renderInfoCards(infoContainer, [{label: "Mix", value: "No data"}]);
            return;
        }

        const total = rows.reduce((acc, row) => acc + row.count, 0);
        const shown = rows.slice(0, 6);
        const palette = ["#2f55d4", "#4d79f4", "#6f97ff", "#1a9d5c", "#c38914", "#7b8dac"];
        const padL = 92;
        const padR = 64;
        const padT = 20;
        const rowGap = 12;
        const rowH = Math.max(14, Math.floor((height - padT - 14 - ((shown.length - 1) * rowGap)) / shown.length));
        const barAreaW = Math.max(90, width - padL - padR);

        for (let i = 0; i < shown.length; i++) {
            const row = shown[i];
            const y = padT + (i * (rowH + rowGap));
            const pct = total > 0 ? (row.count / total) * 100 : 0;
            const fillW = Math.max(2, Math.round((pct / 100) * barAreaW));
            const color = palette[i % palette.length];

            svg.appendChild(svgNode("text", {
                x: 8,
                y: y + rowH - 2,
                "font-size": 11,
                fill: "#3c4f70",
                "font-weight": "600"
            })).textContent = clipText(row.label, 12);

            svg.appendChild(svgNode("rect", {
                x: padL,
                y: y,
                width: barAreaW,
                height: rowH,
                rx: 6,
                fill: "#edf2fb"
            }));

            const bar = svgNode("rect", {
                x: padL,
                y: y,
                width: fillW,
                height: rowH,
                rx: 6,
                fill: color
            });
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent = row.label + ": " + row.count + " shop(s), " + percent(pct, 2);
            bar.appendChild(title);
            svg.appendChild(bar);

            svg.appendChild(svgNode("text", {
                x: padL + barAreaW + 8,
                y: y + rowH - 2,
                "font-size": 11,
                fill: "#2e3f5f",
                "font-weight": "700"
            })).textContent = percent(pct, 1);
        }

        let hhi = 0;
        for (const row of rows) {
            const share = total > 0 ? (row.count / total) : 0;
            hhi += share * share;
        }
        const concentration = hhi >= 0.5 ? "Highly Concentrated"
            : (hhi >= 0.25 ? "Moderate Concentration" : "Diversified");
        const top = rows[0];
        const topPct = total > 0 ? (top.count / total) * 100 : 0;

        renderInfoCards(infoContainer, [
            {label: "Total Shops", value: abbreviateNumber(total), hint: "Total shops represented in this mix."},
            {label: "Unique Types", value: abbreviateNumber(rows.length), hint: "Number of distinct shop types."},
            {label: "Top Type", value: top.label + " (" + percent(topPct, 1) + ")", hint: "Most represented shop type."},
            {label: "Mix Readout", value: concentration, hint: "Concentration signal based on type-share distribution."}
        ]);
    }

    function renderValueBarChart(svg, rows) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 980, 320);
        const width = viewport.width;
        const height = viewport.height;
        const data = (rows || [])
            .map(row => ({
                bankName: row.bankName || row.bank || "Unknown Bank",
                type: row.type || row.label || "Account",
                value: parseLooseNumber(row.value) || 0
            }))
            .filter(row => row.value > 0)
            .sort((a, b) => b.value - a.value)
            .slice(0, 8);
        if (data.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No account balance data";
            return;
        }

        const leftPad = 44;
        const rightPad = 18;
        const topPad = 26;
        const bottomPad = 78;
        const plotW = Math.max(120, width - leftPad - rightPad);
        const plotH = Math.max(100, height - topPad - bottomPad);
        const max = Math.max(1, ...data.map(row => row.value));
        const slotW = plotW / data.length;
        const barW = Math.min(72, Math.max(24, Math.floor(slotW * 0.56)));
        const palette = ["#2f55d4", "#3c65e6", "#4d79f4", "#5a86fb", "#6f97ff", "#86a9ff"];

        for (let i = 0; i <= 4; i++) {
            const y = topPad + ((plotH * i) / 4);
            const ratio = (4 - i) / 4;
            const tickValue = max * ratio;
            svg.appendChild(svgNode("line", {
                x1: leftPad,
                y1: y,
                x2: width - rightPad,
                y2: y,
                stroke: "#e4ebf7",
                "stroke-width": 1
            }));
            svg.appendChild(svgNode("text", {
                x: leftPad - 6,
                y: y + 4,
                "text-anchor": "end",
                "font-size": 11,
                fill: "#6b7890"
            })).textContent = money(tickValue);
        }

        for (let i = 0; i < data.length; i++) {
            const row = data[i];
            const xCenter = leftPad + (slotW * i) + (slotW / 2);
            const h = Math.max(3, Math.round((row.value / max) * plotH));
            const y = topPad + (plotH - h);
            const barColor = palette[i % palette.length];

            const bar = svgNode("rect", {
                x: Math.round(xCenter - (barW / 2)),
                y: y,
                width: barW,
                height: h,
                rx: 8,
                fill: barColor
            });
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent =
                row.type + " @ " + row.bankName +
                " | Balance: " + money(row.value);
            bar.appendChild(title);
            svg.appendChild(bar);

            svg.appendChild(svgNode("text", {
                x: xCenter,
                y: Math.max(14, y - 6),
                "text-anchor": "middle",
                "font-size": 11,
                fill: "#1f3f9c",
                "font-weight": "700"
            })).textContent = money(row.value);

            svg.appendChild(svgNode("text", {
                x: xCenter,
                y: height - 40,
                "text-anchor": "middle",
                "font-size": 11,
                fill: "#2b3f63",
                "font-weight": "600"
            })).textContent = clipText(row.type, 14);

            svg.appendChild(svgNode("text", {
                x: xCenter,
                y: height - 24,
                "text-anchor": "middle",
                "font-size": 10,
                fill: "#6c7b96"
            })).textContent = clipText(row.bankName, 16);
        }
    }

    function transactionKind(direction, amount) {
        const raw = String(direction || "").toUpperCase();
        if (raw.includes("INTERNAL") || raw.includes("TRANSFER")) {
            return {key: "internal", label: "Internal"};
        }
        if (raw.includes("OUT") || raw.includes("DEBIT") || raw.includes("WITHDRAW") || raw.includes("PAY")) {
            return {key: "outgoing", label: "Outgoing"};
        }
        if (raw.includes("IN") || raw.includes("CREDIT") || raw.includes("DEPOSIT") || raw.includes("EARN") || raw.includes("RECEIVE")) {
            return {key: "incoming", label: "Incoming"};
        }

        const numeric = parseLooseNumber(amount) || 0;
        if (numeric > 0) {
            return {key: "incoming", label: "Incoming"};
        }
        if (numeric < 0) {
            return {key: "outgoing", label: "Outgoing"};
        }
        return {key: "internal", label: "Internal"};
    }

    function signedMoney(value, tone) {
        const numeric = parseLooseNumber(value) || 0;
        const abs = Math.abs(numeric);
        if (tone === "incoming") {
            return "+" + money(abs);
        }
        if (tone === "outgoing") {
            return "-" + money(abs);
        }
        if (numeric > 0) {
            return "+" + money(abs);
        }
        if (numeric < 0) {
            return "-" + money(abs);
        }
        return money(abs);
    }

    function shortDayLabel(isoDate) {
        const raw = String(isoDate || "");
        if (!raw) {
            return "-";
        }
        if (/^\d{4}-\d{2}-\d{2}$/.test(raw)) {
            return raw.slice(5);
        }
        return raw.slice(0, 10);
    }

    function formatIsoDateTime(value) {
        const raw = String(value || "");
        if (!raw) {
            return "-";
        }
        return raw.replace("T", " ").slice(0, 19);
    }

    function formatEpochMillis(value) {
        const ms = Number(value || 0);
        if (!Number.isFinite(ms) || ms <= 0) {
            return "-";
        }
        return formatIsoDateTime(new Date(ms).toISOString());
    }

    function renderTransactionCountChart(svg, points) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 980, 300);
        const width = viewport.width;
        const height = viewport.height;
        const data = (points || []).slice(-21);
        if (data.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No transaction history";
            return;
        }

        const padL = 44;
        const padR = 22;
        const padT = 20;
        const padB = 34;
        const plotW = Math.max(120, width - padL - padR);
        const plotH = Math.max(100, height - padT - padB);
        const maxCount = Math.max(1, ...data.map(point => Number(point.count || 0)));
        const step = data.length > 1 ? (plotW / (data.length - 1)) : 0;

        for (let i = 0; i <= 4; i++) {
            const y = padT + ((plotH * i) / 4);
            const ratio = (4 - i) / 4;
            const tick = Math.round(maxCount * ratio);
            svg.appendChild(svgNode("line", {
                x1: padL,
                y1: y,
                x2: width - padR,
                y2: y,
                stroke: "#e4ebf7",
                "stroke-width": 1
            }));
            svg.appendChild(svgNode("text", {
                x: padL - 7,
                y: y + 4,
                "text-anchor": "end",
                "font-size": 11,
                fill: "#6b7890"
            })).textContent = String(tick);
        }

        const linePoints = [];
        for (let i = 0; i < data.length; i++) {
            const value = Number(data[i].count || 0);
            const x = padL + (step * i);
            const y = padT + (plotH - ((value / maxCount) * plotH));
            linePoints.push(x + "," + y);
        }

        if (linePoints.length >= 2) {
            const area = linePoints.concat((width - padR) + "," + (height - padB), padL + "," + (height - padB));
            svg.appendChild(svgNode("polygon", {
                points: area.join(" "),
                fill: "rgba(47, 85, 212, 0.12)"
            }));
        }

        if (linePoints.length > 0) {
            svg.appendChild(svgNode("polyline", {
                points: linePoints.join(" "),
                fill: "none",
                stroke: "#2f55d4",
                "stroke-width": 2.5,
                "stroke-linecap": "round",
                "stroke-linejoin": "round"
            }));
        }

        for (let i = 0; i < data.length; i++) {
            const point = data[i];
            const count = Number(point.count || 0);
            const x = padL + (step * i);
            const y = padT + (plotH - ((count / maxCount) * plotH));
            const incoming = Number(point.incoming || 0);
            const outgoing = Number(point.outgoing || 0);
            const internal = Number(point.internal || 0);
            let pointColor = "#2f55d4";
            if (incoming > outgoing && incoming > internal) {
                pointColor = "#1a9d5c";
            } else if (outgoing > incoming && outgoing > internal) {
                pointColor = "#c13d3d";
            }

            const marker = svgNode("circle", {
                cx: x,
                cy: y,
                r: 4.2,
                fill: pointColor,
                stroke: "#ffffff",
                "stroke-width": 1.6
            });
            const markerTitle = document.createElementNS("http://www.w3.org/2000/svg", "title");
            markerTitle.textContent =
                shortDayLabel(point.label) +
                " | Transactions: " + count +
                " | In: " + incoming +
                " | Out: " + outgoing +
                " | Internal: " + internal;
            marker.appendChild(markerTitle);
            svg.appendChild(marker);

            if (i === 0 || i === data.length - 1 || (i % Math.ceil(data.length / 6) === 0)) {
                svg.appendChild(svgNode("text", {
                    x: x,
                    y: height - 10,
                    "text-anchor": "middle",
                    "font-size": 10,
                    fill: "#6b7890"
                })).textContent = shortDayLabel(point.label);
            }
        }
    }

    function applyTopHealthStatus(health) {
        const perf = (health || {}).performance || {};
        const mspt = Number(perf.serverAvgMspt || 0);
        const tps = Number(perf.serverEstimatedTps || 20);
        const uptime = Number((health || {}).uptimeSeconds || 0);
        const status = String((health || {}).status || "").toLowerCase();
        const tone = status === "critical" || mspt >= 50
            ? "error"
            : (status === "warn" || mspt >= 40 ? "pending" : "healthy");
        const label = mspt > 0
            ? ("TPS " + tps.toFixed(2) + " | " + mspt.toFixed(2) + " mspt")
            : ("uptime " + uptime + "s");
        setHealthState(label, tone);
    }

    function renderHealthPanel(health) {
        if (!el.healthOverviewKpis || !el.healthMeterGrid || !el.healthReportLines || !el.healthSampleNote) {
            return;
        }

        const payload = health || {};
        const perf = payload.performance || {};

        const serverMspt = Number(perf.serverAvgMspt || 0);
        const serverTps = Number(perf.serverEstimatedTps || 20);
        const cpuPct = Number(perf.processCpuLoadPct || 0);
        const heapUsedPct = Number(perf.jvmHeapUsedPct || 0);
        const modAvgMspt = Number(perf.modAvgMspt || 0);
        const modP95Mspt = Number(perf.modP95Mspt || 0);
        const modTickBudgetPct = Number(perf.modTickBudgetPct || 0);
        const modShareOfServerTickPct = Number(perf.modShareOfServerTickPct || 0);
        const modLagSharePct = Number(perf.modEstimatedLagSharePct || 0);
        const modAllocHeapSharePct = Number(perf.modAllocHeapSharePct || 0);
        const sampleSize = Number(perf.sampleSizeTicks || 0);
        const sampleAt = Number(perf.lastSampleEpochMillis || 0);
        const uptime = Number(payload.uptimeSeconds || 0);

        renderKpiCards(el.healthOverviewKpis, [
            {label: "Server MSPT", value: formatMs(serverMspt), hint: explainServerMspt(serverMspt)},
            {label: "Estimated TPS", value: Number(serverTps || 0).toFixed(2), hint: explainServerTps(serverTps)},
            {label: "Process CPU", value: cpuPct >= 0 ? percent(cpuPct, 1) : "n/a", hint: explainCpuUsage(cpuPct)},
            {label: "Heap Used", value: percent(heapUsedPct, 1), hint: explainHeapUsage(heapUsedPct)},
            {label: "UBS Avg MSPT", value: formatMs(modAvgMspt), hint: "Average UBS tick cost over the sample window. Lower is better."},
            {label: "UBS P95 MSPT", value: formatMs(modP95Mspt), hint: "95th percentile UBS tick cost. High p95 means spike-heavy behavior."},
            {label: "UBS Tick Budget", value: percent(modTickBudgetPct, 1), hint: explainUbsTickBudget(modTickBudgetPct)},
            {label: "UBS Share of Tick", value: percent(modShareOfServerTickPct, 1), hint: explainUbsShareOfTick(modShareOfServerTickPct)}
        ]);

        const meters = [
            {
                label: "UBS Tick Budget",
                value: modTickBudgetPct,
                hint: percent(modTickBudgetPct, 1),
                tooltip: explainUbsTickBudget(modTickBudgetPct)
            },
            {
                label: "UBS Share of Server Tick",
                value: modShareOfServerTickPct,
                hint: percent(modShareOfServerTickPct, 1),
                tooltip: explainUbsShareOfTick(modShareOfServerTickPct)
            },
            {
                label: "Estimated UBS Lag Share",
                value: modLagSharePct,
                hint: percent(modLagSharePct, 1),
                tooltip: explainLagShare(modLagSharePct)
            },
            {
                label: "UBS Heap Churn Share",
                value: modAllocHeapSharePct,
                hint: percent(modAllocHeapSharePct, 2),
                tooltip: "Estimated share of heap churn per tick attributed to UBS. Lower is better."
            }
        ];

        el.healthMeterGrid.textContent = "";
        for (const meter of meters) {
            const safeValue = clamp(meter.value, 0, 100);
            const tone = meterTone(safeValue);
            const node = document.createElement("article");
            node.className = "health-meter";
            node.dataset.tooltip = meter.tooltip || (meter.label + ": " + meter.hint);
            node.tabIndex = 0;
            node.innerHTML =
                "<div class=\"health-meter-head\">" +
                "<span>" + html(meter.label) + "</span>" +
                "<strong>" + html(meter.hint) + "</strong>" +
                "</div>" +
                "<div class=\"health-meter-track\">" +
                "<div class=\"health-meter-fill " + tone + "\" style=\"width:" + safeValue.toFixed(1) + "%\"></div>" +
                "</div>";
            el.healthMeterGrid.appendChild(node);
        }

        const lines = [];
        if (serverMspt >= 50) {
            lines.push({tone: "warn", text: "Server is over 50 mspt. UBS estimated lag share is " + percent(modLagSharePct, 1) + "."});
        } else if (serverMspt > 0) {
            lines.push({tone: "ok", text: "Server tick time is stable at " + formatMs(serverMspt) + "."});
        }
        if (modTickBudgetPct >= 35) {
            lines.push({tone: "warn", text: "UBS is using " + percent(modTickBudgetPct, 1) + " of the 50 ms/tick budget."});
        } else {
            lines.push({tone: "ok", text: "UBS tick budget usage is " + percent(modTickBudgetPct, 1) + "."});
        }
        lines.push({tone: "ok", text: "UBS average heap churn per tick: " + formatBytes(perf.modAvgAllocBytesPerTick || 0) + "."});
        if (cpuPct >= 85) {
            lines.push({tone: "warn", text: "Process CPU load is high at " + percent(cpuPct, 1) + "."});
        } else if (cpuPct >= 0) {
            lines.push({tone: "ok", text: "Process CPU load is " + percent(cpuPct, 1) + "."});
        }

        el.healthReportLines.textContent = "";
        for (const line of lines) {
            const li = document.createElement("li");
            li.className = line.tone === "warn" ? "warn" : "ok";
            li.textContent = line.text;
            el.healthReportLines.appendChild(li);
        }

        if (sampleSize > 0 && sampleAt > 0) {
            const ageSeconds = Math.max(0, Math.floor((Date.now() - sampleAt) / 1000));
            el.healthSampleNote.textContent = "Window: " + sampleSize + " ticks | sample age " + ageSeconds + "s | uptime " + uptime + "s";
        } else {
            el.healthSampleNote.textContent = "Collecting performance samples...";
        }

        applyTopHealthStatus(payload);
    }

    function renderDashboard() {
        const data = state.dashboard;
        if (!data || !data.ok) {
            return;
        }

        const webAdmin = data.webAdmin || {};
        el.serverBind.textContent = "Server: " + (webAdmin.bindHost || "0.0.0.0") + ":" + (webAdmin.bindPort || "8080");

        const kpis = data.kpis || {};
        renderKpiCards(el.dashboardKpis, [
            {label: "Money Circulating", value: money(kpis.moneyCirculating)},
            {label: "Total Deposits", value: money(kpis.totalDeposits)},
            {label: "Total Reserves", value: money(kpis.totalReserves)},
            {label: "Banks", value: abbreviateNumber(kpis.banksTotal)},
            {label: "Shops", value: abbreviateNumber(kpis.shopsTotal)},
            {label: "Accounts", value: abbreviateNumber(kpis.accountsTotal)},
            {label: "Issued Cards", value: abbreviateNumber(kpis.issuedCards)},
            {label: "Online Players", value: abbreviateNumber(kpis.onlinePlayers)},
            {label: "Reserve Ratio", value: (kpis.reserveRatioPct || "0.00") + "%"},
            {label: "Shop Revenue", value: money(kpis.shopsRevenueDollars)},
            {label: "Web Clients", value: abbreviateNumber(kpis.wsClients)},
            {label: "Flagged Banks", value: abbreviateNumber(kpis.flaggedBanks)}
        ]);

        const charts = data.charts || {};
        renderEconomyTrendChart(el.economyLineChart, charts.economyHistory || [], el.economyTrendInfo);
        renderBankStatusChart(el.bankStatusChart, charts.bankStatus || [], el.bankStatusInfo);
        renderShopTypeMixChart(el.shopTypeChart, charts.shopType || [], el.shopTypeInfo);

        // Surface backend health/warning hints directly as dashboard alert rows.
        const warningRows = ((data.highlights || {}).warnings || []);
        el.dashboardAlerts.textContent = "";
        for (const warning of warningRows) {
            const li = document.createElement("li");
            const safe = String(warning || "").trim();
            const ok = safe.toLowerCase().includes("no critical");
            li.className = ok ? "ok" : "warn";
            li.textContent = safe || "No alerts.";
            el.dashboardAlerts.appendChild(li);
        }
        if (warningRows.length === 0) {
            const li = document.createElement("li");
            li.className = "ok";
            li.textContent = "No alerts.";
            el.dashboardAlerts.appendChild(li);
        }

        el.topBanksBody.textContent = "";
        for (const row of charts.topBanksByDeposits || []) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.name || "-") + "</td>" +
                "<td>" + html(row.status || "-") + "</td>" +
                "<td>" + money(row.deposits) + "</td>" +
                "<td>" + money(row.reserve) + "</td>";
            el.topBanksBody.appendChild(tr);
        }

        el.topShopsBody.textContent = "";
        for (const row of charts.topShopsByRevenue || []) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.name || "-") + "</td>" +
                "<td>" + html(row.type || "-") + "</td>" +
                "<td>" + html(row.level || "-") + "</td>" +
                "<td>" + money(row.revenueDollars) + "</td>";
            el.topShopsBody.appendChild(tr);
        }
    }

    function filteredEntities() {
        const modeRows = state.entityMode === "banks" ? state.banksRows : state.shopsRows;
        const token = String(el.entitySearch.value || "").trim().toLowerCase();
        if (!token) {
            return modeRows.slice();
        }
        return modeRows.filter(row => JSON.stringify(row).toLowerCase().includes(token));
    }

    function renderEntities() {
        const isBanks = state.entityMode === "banks";
        el.entityPanelTitle.textContent = isBanks ? "Bank Registry" : "Shop Registry";
        el.entitySearch.placeholder = isBanks ? "Search banks" : "Search shops";

        if (isBanks) {
            const metrics = state.banksMetrics || {};
            renderKpiCards(el.entitiesKpis, [
                {label: "Banks", value: abbreviateNumber(metrics.banksTotal)},
                {label: "Active", value: abbreviateNumber(metrics.activeBanks)},
                {label: "Flagged", value: abbreviateNumber(metrics.flaggedBanks)},
                {label: "Accounts", value: abbreviateNumber(metrics.accountsTotal)},
                {label: "Deposits", value: money(metrics.totalDeposits)},
                {label: "Reserves", value: money(metrics.totalReserves)}
            ]);
        } else {
            const metrics = state.shopsMetrics || {};
            renderKpiCards(el.entitiesKpis, [
                {label: "Shops", value: abbreviateNumber(metrics.shopsTotal)},
                {label: "Revenue", value: money(metrics.revenueDollars)},
                {label: "Average Level", value: Number(metrics.avgLevel || 0).toFixed(2)}
            ]);
        }

        if (isBanks) {
            el.entityHead.innerHTML = "<tr><th>Bank</th><th>Owner</th><th>Status</th><th>Accounts</th><th>Deposits</th><th>Reserve</th><th>Reserve %</th><th>Actions</th></tr>";
        } else {
            el.entityHead.innerHTML = "<tr><th>Shop</th><th>Owner</th><th>Type</th><th>Level</th><th>Revenue</th><th>Claims</th><th>Stockrooms</th><th>Actions</th></tr>";
        }

        const rows = filteredEntities();
        el.entityCount.textContent = rows.length + " rows";
        el.entityBody.textContent = "";
        for (const row of rows) {
            const tr = document.createElement("tr");
            if (isBanks) {
                tr.innerHTML =
                    "<td><button type=\"button\" class=\"link-btn bank-open-btn\" data-bank-id=\"" + html(row.bankId || "") + "\">" + html(row.name || "-") + "</button></td>" +
                    "<td>" + html(row.ownerName || shortId(row.ownerId)) + "</td>" +
                    "<td>" + html(row.status || "-") + "</td>" +
                    "<td>" + html(row.accounts || 0) + "</td>" +
                    "<td>" + money(row.deposits) + "</td>" +
                    "<td>" + money(row.reserve) + "</td>" +
                    "<td>" + html((row.reserveRatioPct || 0) + "%") + "</td>" +
                    "<td><button type=\"button\" class=\"btn btn-ghost bank-open-btn\" data-bank-id=\"" + html(row.bankId || "") + "\">Manage</button></td>";
            } else {
                tr.innerHTML =
                    "<td><button type=\"button\" class=\"link-btn shop-open-btn\" data-shop-id=\"" + html(row.shopId || "") + "\">" + html(row.name || "-") + "</button></td>" +
                    "<td>" + html(row.ownerName || shortId(row.ownerId)) + "</td>" +
                    "<td>" + html(row.type || "-") + "</td>" +
                    "<td>" + html(row.level || 0) + "</td>" +
                    "<td>" + money(row.revenueDollars) + "</td>" +
                    "<td>" + html(row.claimRegions || 0) + "</td>" +
                    "<td>" + html(row.stockroomRegions || 0) + "</td>" +
                    "<td><button type=\"button\" class=\"btn btn-ghost shop-open-btn\" data-shop-id=\"" + html(row.shopId || "") + "\">Manage</button></td>";
            }
            el.entityBody.appendChild(tr);
        }
    }

    function filteredShopItems() {
        const rows = state.shopItemsRows || [];
        const token = String((el.shopItemsSearch && el.shopItemsSearch.value) || "").trim().toLowerCase();
        const includeNormal = !el.shopItemsIncludeNormal || !!el.shopItemsIncludeNormal.checked;
        const includeCreative = !el.shopItemsIncludeCreative || !!el.shopItemsIncludeCreative.checked;
        if (!includeNormal && !includeCreative) {
            return [];
        }

        return rows.filter(row => {
            const hasNormal = !!row.hasNormalSources;
            const hasCreative = !!row.hasCreativeSources;
            const sourceAllowed = (includeNormal && hasNormal) || (includeCreative && hasCreative);
            if (!sourceAllowed) {
                return false;
            }
            if (!token) {
                return true;
            }
            const haystack = [
                row.itemName,
                row.itemId,
                row.lowShopName,
                row.highShopName
            ].map(value => String(value || "").toLowerCase()).join(" ");
            return haystack.includes(token);
        });
    }

    function renderShopItems() {
        const rows = filteredShopItems();
        const metrics = state.shopItemsMetrics || {};
        const includeNormal = !el.shopItemsIncludeNormal || !!el.shopItemsIncludeNormal.checked;
        const includeCreative = !el.shopItemsIncludeCreative || !!el.shopItemsIncludeCreative.checked;

        const visibleAvgCents = rows.length === 0
            ? 0
            : rows.reduce((acc, row) => acc + Number(row.priceAvgCents || 0), 0) / rows.length;
        const visibleCreative = rows.filter(row => !!row.hasCreativeSources).length;
        const visibleNormal = rows.filter(row => !!row.hasNormalSources).length;

        renderKpiCards(el.shopItemsKpis, [
            {label: "Indexed Items", value: abbreviateNumber(metrics.itemsTotal || 0)},
            {label: "Visible Items", value: abbreviateNumber(rows.length)},
            {label: "Shops Scanned", value: abbreviateNumber(metrics.shopsScanned || 0)},
            {label: "Shops With Listings", value: abbreviateNumber(metrics.shopsWithListings || 0)},
            {label: "Listings", value: abbreviateNumber(metrics.listingsTotal || 0)},
            {label: "Visible Avg Price", value: money(visibleAvgCents / 100)},
            {label: "Normal Source Items", value: abbreviateNumber(visibleNormal)},
            {label: "Creative Source Items", value: abbreviateNumber(visibleCreative)}
        ]);

        if (el.shopItemsCount) {
            const sourceLabel = includeNormal && includeCreative
                ? "normal + creative"
                : (includeNormal ? "normal only" : "creative only");
            el.shopItemsCount.textContent = rows.length + " items (" + sourceLabel + ")";
        }
        if (!el.shopItemsList) {
            return;
        }
        el.shopItemsList.textContent = "";

        if (rows.length === 0) {
            const empty = document.createElement("div");
            empty.className = "cards-empty";
            empty.textContent = "No items match the current search/filter selection.";
            el.shopItemsList.appendChild(empty);
            return;
        }

        for (const row of rows) {
            const itemId = String(row.itemId || "");
            const itemName = String(row.itemName || itemId || "Unknown Item");
            const lowPrice = Number(row.priceLowCents || 0);
            const avgPrice = Number(row.priceAvgCents || 0);
            const highPrice = Number(row.priceHighCents || 0);
            const lowShop = String(row.lowShopName || shortId(row.lowShopId) || "Unknown Shop");
            const highShop = String(row.highShopName || shortId(row.highShopId) || "Unknown Shop");

            const sourceBadges = [];
            if (row.hasNormalSources) {
                sourceBadges.push("<span class=\"status-badge status-muted\">Normal</span>");
            }
            if (row.hasCreativeSources) {
                sourceBadges.push("<span class=\"status-badge status-private\">Creative</span>");
            }

            const card = document.createElement("article");
            card.className = "shop-item-card";
            card.tabIndex = 0;
            card.dataset.itemId = itemId;
            card.innerHTML =
                "<div class=\"shop-item-card-head\">" +
                "<div class=\"item-cell\">" +
                buildItemVisual(itemId, itemName) +
                "<div class=\"item-label\">" +
                "<strong>" + html(itemName) + "</strong>" +
                "<span class=\"hint\">" + html(itemId || "unknown:item") + "</span>" +
                "</div>" +
                "</div>" +
                "<div class=\"shop-item-badges\">" + sourceBadges.join("") + "</div>" +
                "</div>" +
                "<div class=\"shop-item-prices\">" +
                "<div class=\"shop-item-price\" data-price-role=\"low\"><span>Lowest</span><strong>" + money(lowPrice / 100) + "</strong></div>" +
                "<div class=\"shop-item-price\" data-price-role=\"avg\"><span>Average</span><strong>" + money(avgPrice / 100) + "</strong></div>" +
                "<div class=\"shop-item-price\" data-price-role=\"high\"><span>Highest</span><strong>" + money(highPrice / 100) + "</strong></div>" +
                "</div>" +
                "<div class=\"shop-item-meta\">" +
                "<span>Shops selling: <strong>" + html(abbreviateNumber(row.shopsSelling || 0)) + "</strong></span>" +
                "<span>Listings: <strong>" + html(abbreviateNumber(row.listingCount || 0)) + "</strong></span>" +
                "<span>Lowest at: <strong>" + html(lowShop) + "</strong></span>" +
                "<span>Highest at: <strong>" + html(highShop) + "</strong></span>" +
                "</div>";

            card.addEventListener("click", () => {
                if (!itemId) {
                    return;
                }
                location.hash = "#/shop-items/" + encodeURIComponent(itemId);
            });
            card.addEventListener("keydown", event => {
                if ((event.key === "Enter" || event.key === " ") && itemId) {
                    event.preventDefault();
                    location.hash = "#/shop-items/" + encodeURIComponent(itemId);
                }
            });

            const lowNode = card.querySelector("[data-price-role=\"low\"]");
            if (lowNode) {
                lowNode.dataset.tooltip = "Cheapest shop: " + lowShop + " at " + money(lowPrice / 100)
                    + ". Most expensive shop: " + highShop + " at " + money(highPrice / 100) + ".";
            }
            const avgNode = card.querySelector("[data-price-role=\"avg\"]");
            if (avgNode) {
                avgNode.dataset.tooltip = "Average is calculated from one blended price per shop that sells this item."
                    + " Cheapest: " + lowShop + ". Highest: " + highShop + ".";
            }
            const highNode = card.querySelector("[data-price-role=\"high\"]");
            if (highNode) {
                highNode.dataset.tooltip = "Most expensive shop: " + highShop + " at " + money(highPrice / 100)
                    + ". Cheapest shop: " + lowShop + " at " + money(lowPrice / 100) + ".";
            }

            el.shopItemsList.appendChild(card);
        }
        hydrateItemModels(el.shopItemsList);
    }

    function filteredShopItemDetailShops(data) {
        const rows = (data && Array.isArray(data.shops)) ? data.shops : [];
        const includeNormal = !!state.shopItemIncludeNormal;
        const includeCreative = !!state.shopItemIncludeCreative;
        return rows.filter(row => {
            const creative = !!row.creativeSource;
            return (creative && includeCreative) || (!creative && includeNormal);
        });
    }

    function filteredShopItemDetailListings(data) {
        const rows = (data && Array.isArray(data.listings)) ? data.listings : [];
        const includeNormal = !!state.shopItemIncludeNormal;
        const includeCreative = !!state.shopItemIncludeCreative;
        return rows.filter(row => {
            const creative = !!row.creativeSource;
            return (creative && includeCreative) || (!creative && includeNormal);
        });
    }

    function buildDistributionFromListings(listings) {
        const prices = (listings || [])
            .map(row => Number(row.priceCents || 0))
            .filter(value => Number.isFinite(value) && value >= 0);
        if (prices.length === 0) {
            return [];
        }
        const min = Math.min(...prices);
        const max = Math.max(...prices);
        if (min === max) {
            return [{label: money(min / 100), fromCents: min, toCents: max, count: prices.length}];
        }
        const buckets = Math.max(4, Math.min(8, Math.ceil(Math.sqrt(prices.length))));
        const span = max - min;
        const step = Math.max(1, span / buckets);
        const counts = new Array(buckets).fill(0);
        for (const price of prices) {
            let index = Math.floor((price - min) / step);
            if (index >= buckets) {
                index = buckets - 1;
            }
            if (index < 0) {
                index = 0;
            }
            counts[index] += 1;
        }
        const rows = [];
        for (let i = 0; i < buckets; i++) {
            const from = Math.round(min + (step * i));
            const to = i === buckets - 1 ? Math.round(max) : Math.round(min + (step * (i + 1)) - 1);
            rows.push({
                label: money(from / 100) + " - " + money(to / 100),
                fromCents: from,
                toCents: to,
                count: counts[i]
            });
        }
        return rows;
    }

    function renderShopItemAlerts(rows, listings, demandRows) {
        if (!el.shopItemAlerts) {
            return;
        }
        el.shopItemAlerts.textContent = "";
        const list = [];
        const shops = rows || [];
        const items = listings || [];
        const demand = demandRows || [];
        const now = Date.now();
        const latestSale = items.reduce((max, row) => Math.max(max, Number(row.lastSoldMillis || 0)), 0);
        const hasEstimated = demand.some(point => !!point.estimated);

        if (shops.length <= 1) {
            list.push({tone: "warn", text: "Single-shop market coverage. Price manipulation risk is higher when only one seller exists."});
        } else {
            list.push({tone: "ok", text: "Multi-shop coverage detected. Competitive pricing pressure is active."});
        }

        const prices = items.map(row => Number(row.priceCents || 0)).filter(v => Number.isFinite(v) && v >= 0);
        const low = prices.length ? Math.min(...prices) : 0;
        const high = prices.length ? Math.max(...prices) : 0;
        const avg = prices.length ? (prices.reduce((a, b) => a + b, 0) / prices.length) : 0;
        const spreadPct = avg > 0 ? ((high - low) / avg) * 100 : 0;
        if (spreadPct >= 40) {
            list.push({tone: "warn", text: "High price spread (" + percent(spreadPct, 1) + "). Buyers face inconsistent pricing across shops."});
        } else {
            list.push({tone: "ok", text: "Price spread is " + percent(spreadPct, 1) + ", indicating stable market pricing for this item."});
        }

        if (latestSale <= 0) {
            list.push({tone: "warn", text: "No recorded last-sale timestamp is available for current listings."});
        } else if ((now - latestSale) > (14 * 24 * 60 * 60 * 1000)) {
            list.push({tone: "warn", text: "Latest sale is older than 14 days. Demand is likely weakening."});
        } else {
            list.push({tone: "ok", text: "Recent sale activity detected within the last 14 days."});
        }

        if (hasEstimated) {
            list.push({tone: "warn", text: "Parts of the demand timeline are estimated from velocity where direct slot sales history is sparse."});
        }

        for (const row of list) {
            const li = document.createElement("li");
            li.className = row.tone === "ok" ? "ok" : "warn";
            li.textContent = row.text;
            el.shopItemAlerts.appendChild(li);
        }
    }

    function renderShopItemDemandChart(svg, dailyRows, windowDays, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 980, 300);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (dailyRows || []).slice(-Math.max(1, windowDays));
        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No daily item analytics available";
            renderInfoCards(infoContainer, [{label: "Demand", value: "No data"}]);
            return;
        }

        const padL = 48;
        const padR = 52;
        const padT = 18;
        const padB = 34;
        const plotW = Math.max(120, width - padL - padR);
        const plotH = Math.max(100, height - padT - padB);
        const step = rows.length > 1 ? (plotW / (rows.length - 1)) : 0;
        const maxUnits = Math.max(1, ...rows.map(row => Number(row.unitsSold || 0)));
        const maxPrice = Math.max(1, ...rows.map(row => Number(row.avgPriceCents || 0)));

        for (let i = 0; i <= 4; i++) {
            const ratio = i / 4;
            const y = padT + (plotH * ratio);
            const unitsTick = Math.round(maxUnits * (1 - ratio));
            const priceTick = (maxPrice * (1 - ratio)) / 100;
            svg.appendChild(svgNode("line", {
                x1: padL,
                y1: y,
                x2: width - padR,
                y2: y,
                stroke: "#e3eaf6",
                "stroke-width": 1
            }));
            svg.appendChild(svgNode("text", {
                x: padL - 6,
                y: y + 4,
                "text-anchor": "end",
                "font-size": 10,
                fill: "#6b7890"
            })).textContent = String(unitsTick);
            svg.appendChild(svgNode("text", {
                x: width - padR + 6,
                y: y + 4,
                "text-anchor": "start",
                "font-size": 10,
                fill: "#6b7890"
            })).textContent = money(priceTick);
        }

        const linePoints = [];
        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            const x = padL + (step * i);
            const units = Number(row.unitsSold || 0);
            const priceCents = Number(row.avgPriceCents || 0);
            const barH = Math.max(2, (units / maxUnits) * plotH);
            const yBar = padT + plotH - barH;
            svg.appendChild(svgNode("rect", {
                x: Math.round(x - 4),
                y: yBar,
                width: 8,
                height: barH,
                rx: 3,
                fill: "#7fa5ff"
            }));

            const yLine = padT + plotH - ((priceCents / maxPrice) * plotH);
            linePoints.push(x + "," + yLine);

            if (i % Math.max(1, Math.ceil(rows.length / 8)) === 0 || i === rows.length - 1) {
                svg.appendChild(svgNode("text", {
                    x: x,
                    y: height - 10,
                    "text-anchor": "middle",
                    "font-size": 10,
                    fill: "#6b7890"
                })).textContent = shortDayLabel(row.date);
            }
        }

        svg.appendChild(svgNode("polyline", {
            points: linePoints.join(" "),
            fill: "none",
            stroke: "#2f55d4",
            "stroke-width": 2.2,
            "stroke-linecap": "round",
            "stroke-linejoin": "round"
        }));

        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            const x = padL + (step * i);
            const yLine = padT + plotH - ((Number(row.avgPriceCents || 0) / maxPrice) * plotH);
            const marker = svgNode("circle", {
                cx: x,
                cy: yLine,
                r: 3.2,
                fill: row.estimated ? "#c38914" : "#2f55d4",
                stroke: "#ffffff",
                "stroke-width": 1.2
            });
            const markerTitle = document.createElementNS("http://www.w3.org/2000/svg", "title");
            markerTitle.textContent =
                String(row.date || "-") +
                " | Units sold: " + Math.round(Number(row.unitsSold || 0)) +
                " | Avg price: " + money((Number(row.avgPriceCents || 0)) / 100) +
                " | Revenue: " + money((Number(row.revenueCents || 0)) / 100) +
                (row.estimated ? " | Estimated from velocity" : "");
            marker.appendChild(markerTitle);
            svg.appendChild(marker);
        }

        const sold7 = rows.slice(-7).reduce((acc, row) => acc + Math.round(Number(row.unitsSold || 0)), 0);
        const soldWindow = rows.reduce((acc, row) => acc + Math.round(Number(row.unitsSold || 0)), 0);
        const revenueWindow = rows.reduce((acc, row) => acc + Number(row.revenueCents || 0), 0);
        const avgDaily = rows.length > 0 ? soldWindow / rows.length : 0;
        const latest = rows[rows.length - 1] || {};

        renderInfoCards(infoContainer, [
            {label: "Sold (Last 7D)", value: abbreviateNumber(sold7), hint: "Total units sold over the last 7 days."},
            {label: "Sold (" + rows.length + "D)", value: abbreviateNumber(soldWindow), hint: "Total units sold in the visible chart window."},
            {label: "Revenue (" + rows.length + "D)", value: money(revenueWindow / 100), hint: "Estimated revenue using slot sales history and current shelf prices."},
            {label: "Avg Daily Units", value: round2(avgDaily), hint: "Average units sold per day in current window."},
            {label: "Latest Day", value: shortDayLabel(latest.date) + " @ " + money((Number(latest.avgPriceCents || 0)) / 100), hint: "Most recent point in the chart."}
        ]);
    }

    function renderShopItemShopPriceChart(svg, shopRows, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 520, 230);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (shopRows || [])
            .slice()
            .sort((a, b) => Number(a.priceAvgCents || 0) - Number(b.priceAvgCents || 0))
            .slice(0, 8);
        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No shop pricing rows";
            renderInfoCards(infoContainer, [{label: "Coverage", value: "No data"}]);
            return;
        }

        const maxPrice = Math.max(1, ...rows.map(row => Number(row.priceHighCents || row.priceAvgCents || 0)));
        const padL = 92;
        const padR = 62;
        const padT = 16;
        const rowGap = 8;
        const rowH = Math.max(12, Math.floor((height - padT - 12 - ((rows.length - 1) * rowGap)) / rows.length));
        const barAreaW = Math.max(120, width - padL - padR);

        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            const y = padT + (i * (rowH + rowGap));
            const low = Number(row.priceLowCents || 0);
            const avg = Number(row.priceAvgCents || 0);
            const high = Number(row.priceHighCents || 0);
            const lowX = padL + ((low / maxPrice) * barAreaW);
            const avgX = padL + ((avg / maxPrice) * barAreaW);
            const highX = padL + ((high / maxPrice) * barAreaW);

            svg.appendChild(svgNode("text", {
                x: 8,
                y: y + rowH - 2,
                "font-size": 10,
                fill: "#3c4f70",
                "font-weight": "600"
            })).textContent = clipText(row.shopName || "-", 12);

            svg.appendChild(svgNode("rect", {
                x: padL,
                y: y,
                width: barAreaW,
                height: rowH,
                rx: 5,
                fill: "#edf2fb"
            }));

            svg.appendChild(svgNode("line", {
                x1: lowX,
                y1: y + (rowH / 2),
                x2: highX,
                y2: y + (rowH / 2),
                stroke: "#7b8dac",
                "stroke-width": 2
            }));

            const avgRect = svgNode("rect", {
                x: Math.max(padL, avgX - 5),
                y: y + 1,
                width: 10,
                height: Math.max(4, rowH - 2),
                rx: 3,
                fill: "#2f55d4"
            });
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent = (row.shopName || "-")
                + " | Low " + money(low / 100)
                + " | Avg " + money(avg / 100)
                + " | High " + money(high / 100)
                + " | Sales/day " + round2(Number(row.velocityPerDay || 0));
            avgRect.appendChild(title);
            svg.appendChild(avgRect);

            svg.appendChild(svgNode("text", {
                x: padL + barAreaW + 8,
                y: y + rowH - 2,
                "font-size": 10,
                fill: "#2e3f5f",
                "font-weight": "700"
            })).textContent = money(avg / 100);
        }

        const cheapest = rows[0];
        const expensive = rows[rows.length - 1];
        const avgSpread = Math.max(0, Number(expensive.priceAvgCents || 0) - Number(cheapest.priceAvgCents || 0));
        const avgVelocity = rows.length > 0
            ? rows.reduce((acc, row) => acc + Number(row.velocityPerDay || 0), 0) / rows.length
            : 0;

        renderInfoCards(infoContainer, [
            {label: "Cheapest Avg", value: (cheapest.shopName || "-") + " " + money((Number(cheapest.priceAvgCents || 0)) / 100), hint: "Lowest average listing price by shop."},
            {label: "Highest Avg", value: (expensive.shopName || "-") + " " + money((Number(expensive.priceAvgCents || 0)) / 100), hint: "Highest average listing price by shop."},
            {label: "Avg Spread", value: money(avgSpread / 100), hint: "Difference between highest and lowest average shop price."},
            {label: "Avg Sales/Day", value: round2(avgVelocity), hint: "Average daily slot velocity across displayed shops."}
        ]);
    }

    function renderShopItemDistributionChart(svg, distributionRows, infoContainer) {
        clearSvg(svg);
        const viewport = chartViewport(svg, 520, 230);
        const width = viewport.width;
        const height = viewport.height;
        const rows = (distributionRows || []).slice(0, 8);
        if (rows.length === 0) {
            svg.appendChild(svgNode("text", {
                x: width / 2,
                y: height / 2,
                "text-anchor": "middle",
                "font-size": 14,
                fill: "#6b7890"
            })).textContent = "No distribution data";
            renderInfoCards(infoContainer, [{label: "Distribution", value: "No data"}]);
            return;
        }

        const padL = 36;
        const padR = 12;
        const padT = 16;
        const padB = 38;
        const plotW = Math.max(120, width - padL - padR);
        const plotH = Math.max(80, height - padT - padB);
        const maxCount = Math.max(1, ...rows.map(row => Number(row.count || 0)));
        const slotW = plotW / rows.length;
        const barW = Math.max(12, Math.floor(slotW * 0.6));

        for (let i = 0; i <= 3; i++) {
            const y = padT + ((plotH * i) / 3);
            const tick = Math.round(maxCount * ((3 - i) / 3));
            svg.appendChild(svgNode("line", {
                x1: padL,
                y1: y,
                x2: width - padR,
                y2: y,
                stroke: "#e4ebf7",
                "stroke-width": 1
            }));
            svg.appendChild(svgNode("text", {
                x: padL - 6,
                y: y + 4,
                "text-anchor": "end",
                "font-size": 10,
                fill: "#6b7890"
            })).textContent = String(tick);
        }

        for (let i = 0; i < rows.length; i++) {
            const row = rows[i];
            const count = Number(row.count || 0);
            const h = Math.max(2, Math.round((count / maxCount) * plotH));
            const x = padL + (slotW * i) + ((slotW - barW) / 2);
            const y = padT + plotH - h;
            const bar = svgNode("rect", {
                x: Math.round(x),
                y: y,
                width: barW,
                height: h,
                rx: 4,
                fill: "#4d79f4"
            });
            const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
            title.textContent = String(row.label || "-") + " | Listings: " + count;
            bar.appendChild(title);
            svg.appendChild(bar);

            svg.appendChild(svgNode("text", {
                x: x + (barW / 2),
                y: height - 10,
                "text-anchor": "middle",
                "font-size": 9,
                fill: "#6b7890"
            })).textContent = clipText(String(row.label || "-"), 12);
        }

        const dominant = rows.slice().sort((a, b) => Number(b.count || 0) - Number(a.count || 0))[0];
        const total = rows.reduce((acc, row) => acc + Number(row.count || 0), 0);
        renderInfoCards(infoContainer, [
            {label: "Total Listings", value: abbreviateNumber(total), hint: "Listings represented in current price buckets."},
            {label: "Buckets", value: rows.length, hint: "Number of active price buckets shown."},
            {label: "Dominant Bucket", value: (dominant ? clipText(String(dominant.label || "-"), 24) : "-"), hint: "Price range with the most listings."}
        ]);
    }

    function renderShopItemDetail() {
        const data = state.shopItemDetail || {};
        const item = data.item || {};
        const itemId = String(item.itemId || "");
        const itemName = String(item.itemName || itemId || "Unknown Item");

        if (el.shopItemTitle) {
            el.shopItemTitle.textContent = itemName;
        }
        if (el.shopItemSubtitle) {
            el.shopItemSubtitle.textContent = itemId || (data.message || "");
        }
        if (el.shopItemWindowSegment) {
            const buttons = el.shopItemWindowSegment.querySelectorAll("[data-shop-item-window]");
            for (const button of buttons) {
                button.classList.toggle("active", Number(button.dataset.shopItemWindow || 0) === Number(state.shopItemWindowDays || 30));
            }
        }
        if (el.shopItemIncludeNormal) {
            el.shopItemIncludeNormal.checked = !!state.shopItemIncludeNormal;
        }
        if (el.shopItemIncludeCreative) {
            el.shopItemIncludeCreative.checked = !!state.shopItemIncludeCreative;
        }

        if (!data.ok) {
            renderKpiCards(el.shopItemKpis, [{label: "Item", value: "Unavailable"}]);
            if (el.shopItemAlerts) {
                el.shopItemAlerts.textContent = "";
                const li = document.createElement("li");
                li.className = "warn";
                li.textContent = String(data.message || "Item analytics unavailable.");
                el.shopItemAlerts.appendChild(li);
            }
            clearSvg(el.shopItemDemandChart);
            clearSvg(el.shopItemShopPriceChart);
            clearSvg(el.shopItemDistributionChart);
            renderInfoCards(el.shopItemDemandInfo, []);
            renderInfoCards(el.shopItemShopPriceInfo, []);
            renderInfoCards(el.shopItemDistributionInfo, []);
            if (el.shopItemShopsBody) {
                el.shopItemShopsBody.textContent = "";
            }
            if (el.shopItemListingsBody) {
                el.shopItemListingsBody.textContent = "";
            }
            return;
        }

        const shops = filteredShopItemDetailShops(data);
        const listings = filteredShopItemDetailListings(data);
        const daily = ((data.charts || {}).daily || []).slice();
        const distribution = buildDistributionFromListings(listings);

        const prices = listings.map(row => Number(row.priceCents || 0)).filter(value => Number.isFinite(value) && value >= 0);
        const low = prices.length > 0 ? Math.min(...prices) : 0;
        const high = prices.length > 0 ? Math.max(...prices) : 0;
        const avg = prices.length > 0 ? (prices.reduce((acc, value) => acc + value, 0) / prices.length) : 0;
        const spreadPct = avg > 0 ? ((high - low) / avg) * 100 : 0;
        const velocityPerDay = listings.reduce((acc, row) => acc + Number(row.velocityPerDay || 0), 0);
        const monthlyUnits = Math.max(0, Math.round(velocityPerDay * 30));
        const trackedStock = listings.reduce((acc, row) => {
            const stock = Number(row.stock);
            return acc + (Number.isFinite(stock) && stock > 0 ? stock : 0);
        }, 0);
        const latestSale = listings.reduce((max, row) => Math.max(max, Number(row.lastSoldMillis || 0)), 0);

        renderKpiCards(el.shopItemKpis, [
            {label: "Shops Selling", value: abbreviateNumber(shops.length), hint: "Number of shops currently listing this item under active filters."},
            {label: "Listings", value: abbreviateNumber(listings.length), hint: "Total active shelf listings for this item."},
            {label: "Lowest Price", value: money(low / 100), hint: "Lowest current listing price."},
            {label: "Average Price", value: money(avg / 100), hint: "Average price across all filtered listings."},
            {label: "Highest Price", value: money(high / 100), hint: "Highest current listing price."},
            {label: "Spread", value: percent(spreadPct, 1), hint: "Price spread percentage across filtered listings."},
            {label: "Sales / Day", value: round2(velocityPerDay), hint: "Sum of slot velocity per day across filtered listings."},
            {label: "Est. Monthly Units", value: abbreviateNumber(monthlyUnits), hint: "Velocity-based monthly unit estimate."},
            {label: "Tracked Stock", value: abbreviateNumber(trackedStock), hint: "Current total stock units for non-creative listings."},
            {label: "Last Sale", value: latestSale > 0 ? formatEpochMillis(latestSale) : "-", hint: "Most recent last-sold timestamp among filtered listings."}
        ]);

        renderShopItemAlerts(shops, listings, daily);
        renderShopItemDemandChart(el.shopItemDemandChart, daily, state.shopItemWindowDays, el.shopItemDemandInfo);
        renderShopItemShopPriceChart(el.shopItemShopPriceChart, shops, el.shopItemShopPriceInfo);
        renderShopItemDistributionChart(el.shopItemDistributionChart, distribution, el.shopItemDistributionInfo);

        if (el.shopItemShopsCount) {
            el.shopItemShopsCount.textContent = shops.length + " shops";
        }
        if (el.shopItemShopsBody) {
            el.shopItemShopsBody.textContent = "";
            for (const row of shops) {
                const tr = document.createElement("tr");
                tr.innerHTML =
                    "<td>" + html(row.shopName || "-") + "</td>" +
                    "<td>" + html(row.shopType || "-") + "</td>" +
                    "<td>" + html(row.listingCount || 0) + "</td>" +
                    "<td>" + money((Number(row.priceLowCents || 0)) / 100) + "</td>" +
                    "<td>" + money((Number(row.priceAvgCents || 0)) / 100) + "</td>" +
                    "<td>" + money((Number(row.priceHighCents || 0)) / 100) + "</td>" +
                    "<td>" + html(round2(Number(row.velocityPerDay || 0))) + "</td>" +
                    "<td>" + html(abbreviateNumber(Number(row.stockUnits || 0))) + "</td>" +
                    "<td>" + html(Number(row.lastSoldMillis || 0) > 0 ? formatEpochMillis(row.lastSoldMillis) : "-") + "</td>" +
                    "<td><button type=\"button\" class=\"btn btn-ghost shop-item-open-shop-btn\" data-shop-id=\"" + html(row.shopId || "") + "\">Open Shop</button></td>";
                el.shopItemShopsBody.appendChild(tr);
            }
            if (shops.length === 0) {
                const tr = document.createElement("tr");
                tr.innerHTML = "<td colspan=\"10\">No shop rows match current filter.</td>";
                el.shopItemShopsBody.appendChild(tr);
            }
        }

        if (el.shopItemListingsCount) {
            el.shopItemListingsCount.textContent = listings.length + " listings";
        }
        if (el.shopItemListingsBody) {
            el.shopItemListingsBody.textContent = "";
            for (const row of listings) {
                const creative = !!row.creativeSource;
                const modeBadge = creative
                    ? statusBadge("Creative", "status-private")
                    : statusBadge("Normal", "status-muted");
                const targetText = String(row.minTarget || 0) + "-" + String(row.maxTarget || 0);
                const stockroomText = Number(row.stockroomAvailable || -1) < 0 ? "-" : String(row.stockroomAvailable);
                const tr = document.createElement("tr");
                tr.innerHTML =
                    "<td>" + html(row.shopName || "-") + "</td>" +
                    "<td>" + html(row.shelfIndex || 0) + "</td>" +
                    "<td>" + html(row.slot || 0) + "</td>" +
                    "<td>" + money((Number(row.priceCents || 0)) / 100) + "</td>" +
                    "<td>" + html(Number(row.stock) < 0 ? "INF" : (row.stock || 0)) + "</td>" +
                    "<td>" + html(targetText) + "</td>" +
                    "<td>" + html(stockroomText) + "</td>" +
                    "<td>" + html(round2(Number(row.velocityPerDay || 0))) + "</td>" +
                    "<td>" + html(Number(row.lastSoldMillis || 0) > 0 ? formatEpochMillis(row.lastSoldMillis) : "-") + "</td>" +
                    "<td>" + modeBadge + "</td>";
                el.shopItemListingsBody.appendChild(tr);
            }
            if (listings.length === 0) {
                const tr = document.createElement("tr");
                tr.innerHTML = "<td colspan=\"10\">No listings match current filter.</td>";
                el.shopItemListingsBody.appendChild(tr);
            }
        }
    }

    function filteredUsers() {
        const token = String(el.usersSearch.value || "").trim().toLowerCase();
        if (!token) {
            return state.usersRows.slice();
        }
        return state.usersRows.filter(row => JSON.stringify(row).toLowerCase().includes(token));
    }

    function statusBadge(text, cls) {
        return "<span class=\"status-badge " + cls + "\">" + html(text) + "</span>";
    }

    function renderUsers() {
        const rows = filteredUsers();
        el.usersCount.textContent = rows.length + " users";
        el.usersBody.textContent = "";

        const metrics = state.usersMetrics || {};
        const onlineUsers = Number(metrics.onlineUsers || 0);
        const knownUsers = Number(metrics.knownUsers || 0);
        const aggregateBalance = rows.reduce((acc, row) => acc + (parseLooseNumber(row.totalBalance) || 0), 0);
        renderKpiCards(el.usersKpis, [
            {label: "Known Users", value: abbreviateNumber(knownUsers)},
            {label: "Online Users", value: abbreviateNumber(onlineUsers)},
            {label: "Offline Users", value: abbreviateNumber(Math.max(0, knownUsers - onlineUsers))},
            {label: "Tracked Balance", value: money(aggregateBalance)}
        ]);

        for (const row of rows) {
            const tr = document.createElement("tr");
            tr.className = "clickable-row";
            const online = !!row.online;
            tr.innerHTML =
                "<td>" +
                "<div class=\"user-cell\"><span class=\"avatar\">" + html(initials(row.name)) + "</span>" +
                "<div><strong>" + html(row.name || "Unknown") + "</strong><br><span class=\"hint\">" + html(shortId(row.playerId)) + "</span></div></div>" +
                "</td>" +
                "<td>" + html(row.accounts || 0) + "</td>" +
                "<td>" + money(row.totalBalance) + "</td>" +
                "<td>" + html((row.activeCards || 0) + " / " + (row.cards || 0)) + "</td>" +
                "<td>" + html((row.ownedBanks || 0) + " + " + (row.bankRoles || 0)) + "</td>" +
                "<td>" + html((row.ownedShops || 0) + " + " + (row.shops || 0)) + "</td>" +
                "<td>" + (online ? statusBadge("Online", "status-online") : statusBadge("Offline", "status-offline")) + "</td>";

            tr.addEventListener("click", () => {
                location.hash = "#/players/" + encodeURIComponent(row.playerId);
            });
            el.usersBody.appendChild(tr);
        }
    }

    function setTopNotice(message, isError) {
        if (!message) {
            return;
        }
        el.pageSubtitle.textContent = String(message);
        if (isError) {
            el.pageSubtitle.style.color = "#c13d3d";
            return;
        }
        el.pageSubtitle.style.color = "#1a9d5c";
        window.setTimeout(() => {
            el.pageSubtitle.style.color = "";
        }, 1800);
    }

    function pushAlert(message, tone) {
        const root = el.globalAlerts;
        if (!root || !message) {
            return;
        }
        const item = document.createElement("article");
        const safeTone = tone || "info";
        item.className = "global-alert " + safeTone;
        item.innerHTML =
            "<div class=\"global-alert-main\">" + html(message) + "</div>" +
            "<button type=\"button\" class=\"global-alert-close\" aria-label=\"Dismiss\">x</button>";
        const close = item.querySelector(".global-alert-close");
        if (close) {
            close.addEventListener("click", () => item.remove());
        }
        root.prepend(item);
        while (root.children.length > 5) {
            root.removeChild(root.lastElementChild);
        }
        window.setTimeout(() => {
            if (item.parentElement) {
                item.remove();
            }
        }, 6400);
    }

    function actionTone(ok) {
        return ok ? "success" : "error";
    }

    function resetCardCarouselPosition() {
        state.cardCarouselIndex = 0;
        if (el.playerCardsTrack) {
            el.playerCardsTrack.style.transform = "translateX(0px)";
        }
    }

    function updateCardCarouselButtons() {
        const track = el.playerCardsTrack;
        const viewport = el.cardsViewport;
        if (!track || !viewport) {
            return;
        }

        const cards = Array.from(track.querySelectorAll(".credit-card"));
        if (cards.length === 0) {
            el.cardsPrev.disabled = true;
            el.cardsNext.disabled = true;
            return;
        }
        const cardWidth = cards[0].offsetWidth + 12;
        const visible = Math.max(1, Math.floor(viewport.clientWidth / cardWidth));
        const maxIndex = Math.max(0, cards.length - visible);
        if (state.cardCarouselIndex > maxIndex) {
            state.cardCarouselIndex = maxIndex;
        }
        const offset = -(state.cardCarouselIndex * cardWidth);
        track.style.transform = "translateX(" + offset + "px)";
        el.cardsPrev.disabled = state.cardCarouselIndex <= 0;
        el.cardsNext.disabled = state.cardCarouselIndex >= maxIndex;
    }

    function scrollCardCarousel(delta) {
        state.cardCarouselIndex = Math.max(0, state.cardCarouselIndex + delta);
        updateCardCarouselButtons();
    }

    function cardLabelExpiry(epochMillis) {
        const raw = Number(epochMillis || 0);
        if (!Number.isFinite(raw) || raw <= 0) {
            return "--/--";
        }
        const date = new Date(raw);
        if (Number.isNaN(date.getTime())) {
            return "--/--";
        }
        const month = String(date.getMonth() + 1).padStart(2, "0");
        const year = String(date.getFullYear()).slice(-2);
        return month + "/" + year;
    }

    async function copyText(value, label) {
        const safe = String(value || "");
        if (!safe) {
            return;
        }
        try {
            if (navigator.clipboard && navigator.clipboard.writeText) {
                await navigator.clipboard.writeText(safe);
            } else {
                const area = document.createElement("textarea");
                area.value = safe;
                area.style.position = "fixed";
                area.style.opacity = "0";
                document.body.appendChild(area);
                area.select();
                document.execCommand("copy");
                document.body.removeChild(area);
            }
            setTopNotice((label || "Value") + " copied.", false);
        } catch (error) {
            setTopNotice("Clipboard copy failed.", true);
        }
    }

    async function performCardAction(action, card) {
        if (!action || !card) {
            return;
        }

        if (action === "copy-card-id") {
            await copyText(card.cardId, "Card ID");
            return;
        }
        if (action === "copy-account-id") {
            await copyText(card.accountId, "Account ID");
            return;
        }
        if (action === "copy-masked") {
            await copyText(card.maskedNumber, "Card number");
            return;
        }

        const playerId = (((state.playerDetail || {}).profile || {}).playerId || "");
        if (!playerId) {
            setTopNotice("Cannot run card action without loaded player profile.", true);
            return;
        }

        if (action === "block-account-cards" || action === "issue-replacement") {
            const apiAction = action === "block-account-cards" ? "BLOCK_ACCOUNT_CARDS" : "ISSUE_REPLACEMENT";
            try {
                const result = await api("/api/webadmin/cards/action", {
                    method: "POST",
                    body: JSON.stringify({
                        action: apiAction,
                        cardId: card.cardId || "",
                        accountId: card.accountId || "",
                        holderName: card.holderName || ""
                    })
                });
                if (result.ok) {
                    const msg = result.message || "Card action completed.";
                    setTopNotice(msg, false);
                    pushAlert(msg, "success");
                    await loadPlayerDetail(playerId);
                } else {
                    const msg = result.message || "Card action failed.";
                    setTopNotice(msg, true);
                    pushAlert(msg, "error");
                }
            } catch (error) {
                const msg = "Card action failed: " + error.message;
                setTopNotice(msg, true);
                pushAlert(msg, "error");
            }
        }
    }

    function renderCardCarousel(cards) {
        const rows = cards || [];
        el.playerCardsCount.textContent = rows.length + " cards";
        resetCardCarouselPosition();
        el.playerCardsTrack.textContent = "";

        if (rows.length === 0) {
            const empty = document.createElement("div");
            empty.className = "cards-empty";
            empty.textContent = "No credit cards.";
            el.playerCardsTrack.appendChild(empty);
            updateCardCarouselButtons();
            return;
        }

        for (const row of rows) {
            const blocked = String(row.status || "").toUpperCase() === "BLOCKED";
            const privateCard = Boolean(row.privateCard);
            const card = document.createElement("article");
            card.className = "credit-card" + (privateCard ? " private" : "") + (blocked ? " blocked" : "");
            card.innerHTML =
                "<div class=\"cc-head\">" +
                "<span class=\"cc-bank\">" + html(row.bankName || "Unknown Bank") + "</span>" +
                "<span class=\"cc-status\">" + html(blocked ? "BLOCKED" : (privateCard ? "PRIVATE" : "ACTIVE")) + "</span>" +
                "</div>" +
                "<div class=\"cc-number\">" + html(row.maskedNumber || "**** **** **** ****") + "</div>" +
                "<div class=\"cc-meta\">" +
                "<span>Holder: " + html(row.holderName || "-") + "</span>" +
                "<span>Expires: " + html(cardLabelExpiry(row.expiryEpochMillis)) + "</span>" +
                "<span>Account: " + html(shortId(row.accountId)) + "</span>" +
                "</div>" +
                "<div class=\"cc-actions\">" +
                "<button type=\"button\" class=\"cc-action-btn\" data-card-action=\"copy-card-id\">Copy Card ID</button>" +
                "<button type=\"button\" class=\"cc-action-btn\" data-card-action=\"copy-account-id\">Copy Account</button>" +
                "<button type=\"button\" class=\"cc-action-btn\" data-card-action=\"copy-masked\">Copy Number</button>" +
                "<button type=\"button\" class=\"cc-action-btn warn\" data-card-action=\"block-account-cards\">Block Account Cards</button>" +
                "<button type=\"button\" class=\"cc-action-btn\" data-card-action=\"issue-replacement\">Issue Replacement</button>" +
                "</div>";

            card.dataset.cardId = row.cardId || "";
            card.dataset.accountId = row.accountId || "";
            card.dataset.holderName = row.holderName || "";
            card.dataset.maskedNumber = row.maskedNumber || "";
            card.dataset.privateCard = privateCard ? "true" : "false";
            el.playerCardsTrack.appendChild(card);
        }

        updateCardCarouselButtons();
    }

    function renderPlayerDetail() {
        const data = state.playerDetail;
        if (!data || !data.ok) {
            return;
        }

        const profile = data.profile || {};
        const summary = data.summary || {};
        el.playerName.textContent = profile.name || "Player";
        el.playerId.textContent = profile.playerId || "";

        renderKpiCards(el.playerKpis, [
            {label: "Accounts", value: String(summary.accounts || 0)},
            {label: "Balance", value: money(summary.totalBalance)},
            {label: "Cards Active", value: String(summary.activeCards || 0)},
            {label: "Cards Blocked", value: String(summary.blockedCards || 0)},
            {label: "Owned Banks", value: String(summary.ownedBanks || 0)},
            {label: "Bank Roles", value: String(summary.delegatedBankRoles || 0)},
            {label: "Shops", value: String(summary.shops || 0)},
            {label: "Owned Shops", value: String(summary.ownedShops || 0)},
            {label: "Avg Credit", value: String(summary.avgCreditScore || 0)},
            {label: "Transactions", value: String(summary.transactions || 0)}
        ]);

        const accounts = data.accounts || [];
        const balancesByType = {};
        for (const row of accounts) {
            const bankName = String(row.bankName || "Unknown Bank");
            const type = String(row.type || "Account");
            const key = bankName + "|" + type;
            if (!balancesByType[key]) {
                balancesByType[key] = {
                    bankName: bankName,
                    type: type,
                    value: 0
                };
            }
            balancesByType[key].value += (parseLooseNumber(row.balance) || 0);
        }
        const balanceSegments = Object.keys(balancesByType)
            .map(key => balancesByType[key])
            .sort((a, b) => Number(b.value || 0) - Number(a.value || 0));
        renderValueBarChart(el.playerAccountsChart, balanceSegments);

        const txRows = data.transactions || [];
        const byDay = {};
        for (const tx of txRows) {
            const day = String(tx.timestamp || "").slice(0, 10);
            if (!day) {
                continue;
            }
            if (!byDay[day]) {
                byDay[day] = {label: day, count: 0, incoming: 0, outgoing: 0, internal: 0};
            }
            byDay[day].count += 1;
            const tone = transactionKind(tx.direction, tx.amount);
            byDay[day][tone.key] += 1;
        }
        const daySeries = Object.keys(byDay)
            .sort()
            .map(day => byDay[day]);
        renderTransactionCountChart(el.playerTransactionsChart, daySeries);

        el.playerAccountsBody.textContent = "";
        for (const row of accounts) {
            const tr = document.createElement("tr");
            const flags = [];
            if (row.primary) {
                flags.push("Primary");
            }
            if (row.frozen) {
                flags.push("Frozen");
            }
            if (row.defaulted) {
                flags.push("Defaulted");
            }
            tr.innerHTML =
                "<td><button type=\"button\" class=\"link-btn account-open-btn\" data-account-id=\"" + html(row.accountId) + "\">" + html(shortId(row.accountId)) + "</button></td>" +
                "<td>" + html(row.bankName || "-") + "</td>" +
                "<td>" + html(row.type || "-") + "</td>" +
                "<td>" + money(row.balance) + "</td>" +
                "<td>" + html(row.creditScore || 0) + "</td>" +
                "<td>" + html(flags.join(", ") || "-") + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost account-open-btn\" data-account-id=\"" + html(row.accountId) + "\">Manage</button></td>";
            el.playerAccountsBody.appendChild(tr);
        }

        renderCardCarousel(data.creditCards || []);

        el.playerShopsList.textContent = "";
        for (const row of data.shops || []) {
            const li = document.createElement("li");
            li.textContent = (row.name || "Shop") + " - " + (row.role || "STAFF") + " - Level " + (row.level || 1);
            el.playerShopsList.appendChild(li);
        }
        if ((data.shops || []).length === 0) {
            const li = document.createElement("li");
            li.textContent = "No shop access records.";
            el.playerShopsList.appendChild(li);
        }

        // Player footprint combines all non-account identity links (roles, ownership, shares).
        const footprint = data.bankFootprint || {};
        const ownedBanks = footprint.ownedBanks || [];
        const delegatedRoles = footprint.delegatedRoles || [];
        const cofounderAt = footprint.cofounderAt || [];
        const employeeAt = footprint.employeeAt || [];
        const shareholdings = footprint.shareholdings || {};
        const shareLines = Object.entries(shareholdings).map(entry => entry[0] + ": " + entry[1]);

        el.playerBankFootprint.textContent = "";
        const footprintEntries = [
            ["Owned Banks", ownedBanks.length > 0 ? ownedBanks.join(", ") : "None"],
            ["Delegated Roles", delegatedRoles.length > 0 ? delegatedRoles.join(", ") : "None"],
            ["Cofounder At", cofounderAt.length > 0 ? cofounderAt.join(", ") : "None"],
            ["Employee At", employeeAt.length > 0 ? employeeAt.join(", ") : "None"],
            ["Shareholdings", shareLines.length > 0 ? shareLines.join(", ") : "None"]
        ];
        for (const entry of footprintEntries) {
            const item = document.createElement("article");
            item.className = "kv-item";
            item.innerHTML = "<span class=\"k\">" + html(entry[0]) + "</span><span class=\"v\">" + html(entry[1]) + "</span>";
            el.playerBankFootprint.appendChild(item);
        }

        el.playerTransactionsBody.textContent = "";
        for (const row of txRows) {
            const tone = transactionKind(row.direction, row.amount);
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(String(row.timestamp || "").replace("T", " ").slice(0, 19)) + "</td>" +
                "<td><span class=\"tx-pill tx-" + html(tone.key) + "\">" + html(tone.label) + "</span></td>" +
                "<td class=\"tx-amount " + html(tone.key) + "\">" + html(signedMoney(row.amount, tone.key)) + "</td>" +
                "<td>" + html(row.description || "-") + "</td>" +
                "<td>" + html(shortId(row.transactionId)) + "</td>";
            el.playerTransactionsBody.appendChild(tr);
        }
    }

    function currentBankId() {
        const detail = state.bankDetail || {};
        const bank = detail.bank || {};
        return String(bank.bankId || "");
    }

    function setBankOverviewModalOpen(open) {
        const modal = el.bankOverviewModal;
        if (!modal) {
            return;
        }
        state.bankOverviewModalOpen = !!open;
        modal.classList.toggle("open", state.bankOverviewModalOpen);
        modal.setAttribute("aria-hidden", state.bankOverviewModalOpen ? "false" : "true");
        document.body.classList.toggle("modal-open", state.bankOverviewModalOpen);
    }

    function closeBankOverviewModal() {
        setBankOverviewModalOpen(false);
    }

    function setSelectIfPresent(select, next, fallback) {
        if (!select) {
            return;
        }
        const requested = String(next || "").trim();
        for (const option of Array.from(select.options || [])) {
            if (option.value === requested) {
                select.value = requested;
                return;
            }
        }
        select.value = String(fallback || (select.value || ""));
    }

    // Keeps the modal form values in sync with the currently loaded bank detail payload.
    function fillBankOverviewModalFromState() {
        const detail = state.bankDetail || {};
        const bank = detail.bank || {};
        const limits = detail.limits || {};
        if (el.bankOverviewModal) {
            el.bankOverviewModal.dataset.ownerId = String(bank.ownerId || "");
            el.bankOverviewModal.dataset.ownerName = String(bank.ownerName || "");
        }
        if (el.bankOverviewBankIdInput) {
            el.bankOverviewBankIdInput.value = String(bank.bankId || "");
        }
        if (el.bankOverviewOwnerInput) {
            el.bankOverviewOwnerInput.value = String(bank.ownerName || "");
        }
        if (el.bankOverviewOwnerUuidInput) {
            el.bankOverviewOwnerUuidInput.value = String(bank.ownerId || "");
        }
        setSelectIfPresent(el.bankOverviewStatusInput, String(bank.status || "ACTIVE").toUpperCase(), "ACTIVE");
        setSelectIfPresent(el.bankOverviewOwnershipInput, String(bank.ownershipModel || "SOLE").toUpperCase(), "SOLE");
        if (el.bankOverviewColorInput) {
            el.bankOverviewColorInput.value = String(bank.color || "#55AAFF");
        }
        if (el.bankOverviewMottoInput) {
            el.bankOverviewMottoInput.value = String(bank.motto || "");
        }
        if (el.bankOverviewFederalRateInput) {
            el.bankOverviewFederalRateInput.value = String(bank.federalFundsRate == null ? "" : bank.federalFundsRate);
        }
        if (el.bankOverviewIssueFeeInput) {
            el.bankOverviewIssueFeeInput.value = String(bank.cardIssueFee == null ? "" : bank.cardIssueFee);
        }
        if (el.bankOverviewReplacementFeeInput) {
            el.bankOverviewReplacementFeeInput.value = String(bank.cardReplacementFee == null ? "" : bank.cardReplacementFee);
        }
        if (el.bankOverviewSingleLimitInput) {
            el.bankOverviewSingleLimitInput.value = String(limits.singleLimit == null ? "" : limits.singleLimit);
        }
        if (el.bankOverviewDailyPlayerLimitInput) {
            el.bankOverviewDailyPlayerLimitInput.value = String(limits.dailyPlayerLimit == null ? "" : limits.dailyPlayerLimit);
        }
        if (el.bankOverviewDailyBankLimitInput) {
            el.bankOverviewDailyBankLimitInput.value = String(limits.dailyBankLimit == null ? "" : limits.dailyBankLimit);
        }
        if (el.bankOverviewTellerLimitInput) {
            el.bankOverviewTellerLimitInput.value = String(limits.tellerLimit == null ? "" : limits.tellerLimit);
        }
    }

    function openBankOverviewModal() {
        const bankId = currentBankId();
        if (!bankId) {
            pushAlert("Load a bank first.", "warn");
            return;
        }
        fillBankOverviewModalFromState();
        setBankOverviewModalOpen(true);
    }

    function saveBankOverviewFromModal() {
        const bankId = currentBankId();
        if (!bankId) {
            pushAlert("No bank loaded.", "error");
            return Promise.resolve({ok: false, message: "No bank loaded."});
        }
        const originalOwnerId = String((el.bankOverviewModal && el.bankOverviewModal.dataset.ownerId) || "").trim();
        const originalOwnerName = String((el.bankOverviewModal && el.bankOverviewModal.dataset.ownerName) || "").trim();
        const nextOwnerLookup = String((el.bankOverviewOwnerInput && el.bankOverviewOwnerInput.value) || "").trim();
        const nextOwnerId = String((el.bankOverviewOwnerUuidInput && el.bankOverviewOwnerUuidInput.value) || "").trim();

        let ownerLookupPayload = "";
        let ownerIdPayload = "";
        if (nextOwnerId && nextOwnerId !== originalOwnerId) {
            ownerIdPayload = nextOwnerId;
        } else if (nextOwnerLookup && nextOwnerLookup !== originalOwnerName) {
            ownerLookupPayload = nextOwnerLookup;
        } else {
            ownerIdPayload = originalOwnerId;
        }

        const payload = {
            ownerLookup: ownerLookupPayload,
            ownerId: ownerIdPayload,
            status: String((el.bankOverviewStatusInput && el.bankOverviewStatusInput.value) || "").trim(),
            ownershipModel: String((el.bankOverviewOwnershipInput && el.bankOverviewOwnershipInput.value) || "").trim(),
            color: String((el.bankOverviewColorInput && el.bankOverviewColorInput.value) || "").trim(),
            motto: String((el.bankOverviewMottoInput && el.bankOverviewMottoInput.value) || "").trim(),
            federalFundsRate: String((el.bankOverviewFederalRateInput && el.bankOverviewFederalRateInput.value) || "").trim(),
            issueFee: String((el.bankOverviewIssueFeeInput && el.bankOverviewIssueFeeInput.value) || "").trim(),
            replacementFee: String((el.bankOverviewReplacementFeeInput && el.bankOverviewReplacementFeeInput.value) || "").trim(),
            singleLimit: String((el.bankOverviewSingleLimitInput && el.bankOverviewSingleLimitInput.value) || "").trim(),
            dailyPlayerLimit: String((el.bankOverviewDailyPlayerLimitInput && el.bankOverviewDailyPlayerLimitInput.value) || "").trim(),
            dailyBankLimit: String((el.bankOverviewDailyBankLimitInput && el.bankOverviewDailyBankLimitInput.value) || "").trim(),
            tellerLimit: String((el.bankOverviewTellerLimitInput && el.bankOverviewTellerLimitInput.value) || "").trim()
        };

        const saveBtn = el.bankOverviewSaveBtn;
        const previousLabel = saveBtn ? saveBtn.textContent : "";
        if (saveBtn) {
            saveBtn.disabled = true;
            saveBtn.textContent = "Saving...";
        }
        return runBankAction("UPDATE_OVERVIEW", payload)
            .then(result => {
                if (result && result.ok) {
                    closeBankOverviewModal();
                }
                return result;
            })
            .finally(() => {
                if (saveBtn) {
                    saveBtn.disabled = false;
                    saveBtn.textContent = previousLabel || "Save Changes";
                }
            });
    }

    function prependBankActionLog(message, tone, details) {
        if (!el.bankActionOutput) {
            return;
        }
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, "0");
        const mm = String(now.getMinutes()).padStart(2, "0");
        const ss = String(now.getSeconds()).padStart(2, "0");
        const level = tone === "success" ? "SUCCESS" : (tone === "error" ? "ERROR" : "INFO");
        const entry = "[" + hh + ":" + mm + ":" + ss + "] " + level + " - " + (message || "Bank action completed.")
            + (details ? "\n" + details : "");
        const previous = String(el.bankActionOutput.textContent || "").trim();
        el.bankActionOutput.textContent = previous ? (entry + "\n\n" + previous) : entry;
    }

    function renderBankRiskLines(bank) {
        const lines = [];
        const status = String(bank.status || "ACTIVE").toUpperCase();
        const reserve = parseLooseNumber(bank.reserve) || 0;
        const minReserve = parseLooseNumber(bank.minReserve) || 0;
        const dailyCap = parseLooseNumber(bank.dailyCap) || 0;
        const dailyUsed = parseLooseNumber(bank.dailyUsed) || 0;
        const dailyUtilizationPct = dailyCap > 0 ? (dailyUsed / dailyCap) * 100 : 0;

        if (status !== "ACTIVE") {
            lines.push({tone: "warn", text: "Bank is currently " + status + ". Lending and governance actions may be restricted."});
        } else {
            lines.push({tone: "ok", text: "Bank status is ACTIVE."});
        }

        if (reserve < minReserve) {
            lines.push({tone: "warn", text: "Reserve is below minimum requirement by " + money(minReserve - reserve) + "."});
        } else {
            lines.push({tone: "ok", text: "Reserve is above minimum requirement by " + money(reserve - minReserve) + "."});
        }

        if (dailyUtilizationPct >= 90) {
            lines.push({tone: "warn", text: "Daily liquidity usage is very high: " + percent(dailyUtilizationPct, 1) + "."});
        } else if (dailyUtilizationPct >= 70) {
            lines.push({tone: "warn", text: "Daily liquidity usage is elevated: " + percent(dailyUtilizationPct, 1) + "."});
        } else {
            lines.push({tone: "ok", text: "Daily liquidity usage is " + percent(dailyUtilizationPct, 1) + "."});
        }

        el.bankRiskLines.textContent = "";
        for (const line of lines) {
            const li = document.createElement("li");
            li.className = line.tone === "warn" ? "warn" : "ok";
            li.textContent = line.text;
            el.bankRiskLines.appendChild(li);
        }
    }

    function renderBankDetail() {
        const data = state.bankDetail;
        if (!data || !data.ok) {
            el.bankTitle.textContent = "Bank not found";
            el.bankSubtitle.textContent = data && data.message ? data.message : "No bank payload.";
            return;
        }

        const bank = data.bank || {};
        const limits = data.limits || {};
        const roles = data.roles || [];
        const shares = data.shares || [];
        const cofounders = data.cofounders || [];
        const employees = data.employees || [];
        const loanProducts = data.loanProducts || [];
        const accounts = data.accounts || [];
        const offers = data.interbankOffers || [];
        const loans = data.interbankLoans || [];

        el.bankTitle.textContent = (bank.name || "Bank") + " (" + shortId(bank.bankId) + ")";
        el.bankSubtitle.textContent = "Owner " + (bank.ownerName || "Unknown")
            + " | Status " + (bank.status || "ACTIVE")
            + " | Model " + (bank.ownershipModel || "SOLE");

        if (el.bankMottoInput) {
            el.bankMottoInput.value = String(bank.motto || "");
        }
        if (el.bankColorInput) {
            el.bankColorInput.value = String(bank.color || "");
        }
        if (el.bankCardIssueFeeInput) {
            el.bankCardIssueFeeInput.value = String(bank.cardIssueFee || "0");
        }
        if (el.bankCardReplacementFeeInput) {
            el.bankCardReplacementFeeInput.value = String(bank.cardReplacementFee || "0");
        }

        renderKpiCards(el.bankKpis, [
            {label: "Deposits", value: money(bank.deposits)},
            {label: "Reserve", value: money(bank.reserve)},
            {label: "Reserve Ratio", value: percent(bank.reserveRatioPct, 2)},
            {label: "Minimum Reserve", value: money(bank.minReserve)},
            {label: "Daily Cap", value: money(bank.dailyCap)},
            {label: "Daily Used", value: money(bank.dailyUsed)},
            {label: "Daily Remaining", value: money(bank.dailyRemaining)},
            {label: "Accounts", value: abbreviateNumber(accounts.length)},
            {label: "Employees", value: abbreviateNumber(employees.length)},
            {label: "Loan Products", value: abbreviateNumber(loanProducts.length)},
            {label: "Interbank Offers", value: abbreviateNumber(offers.length)},
            {label: "Interbank Loans", value: abbreviateNumber(loans.length)}
        ]);

        renderInfoCards(el.bankSummaryGrid, [
            {label: "Bank ID", value: String(bank.bankId || "-"), hint: "Unique bank UUID."},
            {label: "Owner", value: String(bank.ownerName || "-"), hint: "Current owner profile name."},
            {label: "Owner UUID", value: String(bank.ownerId || "-"), hint: "Current owner UUID."},
            {label: "Status", value: String(bank.status || "ACTIVE"), hint: "Operational status from bank metadata."},
            {label: "Ownership Model", value: String(bank.ownershipModel || "SOLE"), hint: "SOLE, ROLE_BASED, PERCENTAGE_SHARES, or FIXED_COFOUNDERS."},
            {label: "Brand Color", value: String(bank.color || "-"), hint: "Configured bank color token."},
            {label: "Motto", value: String(bank.motto || "-"), hint: "Configured bank tagline shown in bank surfaces."},
            {label: "Federal Funds Rate", value: percent(bank.federalFundsRate, 2), hint: "Current central bank reference rate."},
            {label: "Issue Fee", value: money(bank.cardIssueFee), hint: "Fee charged when issuing a new credit card."},
            {label: "Replacement Fee", value: money(bank.cardReplacementFee), hint: "Fee charged when replacing a credit card."},
            {label: "Single Tx Limit", value: money(limits.singleLimit), hint: "Per-transaction maximum amount."},
            {label: "Daily Player Limit", value: money(limits.dailyPlayerLimit), hint: "Maximum daily amount per player."},
            {label: "Daily Bank Limit", value: money(limits.dailyBankLimit), hint: "Maximum daily amount across the bank."},
            {label: "Teller Cash Limit", value: money(limits.tellerLimit), hint: "Per-teller withdrawal cash limit."}
        ]);
        renderBankRiskLines(bank);

        el.bankAccountsCount.textContent = accounts.length + " accounts";
        el.bankAccountsBody.textContent = "";
        for (const row of accounts) {
            const flags = [];
            if (row.primary) {
                flags.push("Primary");
            }
            if (row.frozen) {
                flags.push("Frozen");
            }
            if (row.defaulted) {
                flags.push("Defaulted");
            }
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.ownerName || shortId(row.ownerId)) + "</td>" +
                "<td>" + html(shortId(row.accountId)) + "</td>" +
                "<td>" + html(row.type || "-") + "</td>" +
                "<td>" + money(row.balance) + "</td>" +
                "<td>" + html(row.creditScore || 0) + "</td>" +
                "<td>" + html(flags.join(", ") || "Active") + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-account-open-btn\" data-account-id=\"" + html(row.accountId || "") + "\">Open Account</button></td>";
            el.bankAccountsBody.appendChild(tr);
        }
        if (accounts.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"7\">No accounts for this bank.</td>";
            el.bankAccountsBody.appendChild(tr);
        }

        el.bankRolesBody.textContent = "";
        for (const row of roles) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td>" + html(row.role || "-") + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-role-row-action\" data-player-id=\"" + html(row.playerId || "") + "\">Revoke</button></td>";
            el.bankRolesBody.appendChild(tr);
        }
        if (roles.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"3\">No delegated roles.</td>";
            el.bankRolesBody.appendChild(tr);
        }

        el.bankSharesBody.textContent = "";
        for (const row of shares) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td>" + percent(row.percent, 2) + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-share-row-action\" data-player-id=\"" + html(row.playerId || "") + "\">Remove</button></td>";
            el.bankSharesBody.appendChild(tr);
        }
        if (shares.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"3\">No share records.</td>";
            el.bankSharesBody.appendChild(tr);
        }

        el.bankCofoundersBody.textContent = "";
        for (const row of cofounders) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-cofounder-row-action\" data-player-id=\"" + html(row.playerId || "") + "\">Remove</button></td>";
            el.bankCofoundersBody.appendChild(tr);
        }
        if (cofounders.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"2\">No cofounders.</td>";
            el.bankCofoundersBody.appendChild(tr);
        }

        el.bankEmployeesBody.textContent = "";
        for (const row of employees) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td>" + html(row.role || "-") + "</td>" +
                "<td>" + money(row.salary) + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-employee-row-action\" data-player-id=\"" + html(row.playerId || "") + "\">Fire</button></td>";
            el.bankEmployeesBody.appendChild(tr);
        }
        if (employees.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"4\">No employees.</td>";
            el.bankEmployeesBody.appendChild(tr);
        }

        el.bankProductsBody.textContent = "";
        for (const row of loanProducts) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.name || "-") + "</td>" +
                "<td>" + money(row.maxAmount) + "</td>" +
                "<td>" + percent(row.annualRate, 2) + "</td>" +
                "<td>" + html(row.durationTicks || 0) + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost bank-product-row-action\" data-name=\"" + html(row.name || "") + "\">Delete</button></td>";
            el.bankProductsBody.appendChild(tr);
        }
        if (loanProducts.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"5\">No loan products configured.</td>";
            el.bankProductsBody.appendChild(tr);
        }

        el.bankOffersBody.textContent = "";
        for (const row of offers) {
            const status = String(row.status || "UNKNOWN").toUpperCase();
            const canAccept = status === "OPEN" && !row.lenderIsCurrentBank;
            const actionCell = canAccept
                ? "<button type=\"button\" class=\"btn btn-ghost bank-offer-row-action\" data-offer-id=\"" + html(row.offerId || "") + "\">Accept</button>"
                : "<span class=\"hint\">-</span>";
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(shortId(row.offerId)) + "</td>" +
                "<td>" + html(row.lenderBankName || shortId(row.lenderBankId)) + "</td>" +
                "<td>" + html(row.acceptedByBankName || (row.acceptedByBankId ? shortId(row.acceptedByBankId) : "-")) + "</td>" +
                "<td>" + money(row.amount) + "</td>" +
                "<td>" + percent(row.annualRate, 2) + "</td>" +
                "<td>" + html(row.termTicks || 0) + "</td>" +
                "<td>" + html(status) + "</td>" +
                "<td>" + actionCell + "</td>";
            el.bankOffersBody.appendChild(tr);
        }
        if (offers.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"8\">No interbank offers linked to this bank.</td>";
            el.bankOffersBody.appendChild(tr);
        }

        el.bankLoansBody.textContent = "";
        for (const row of loans) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(shortId(row.loanId)) + "</td>" +
                "<td>" + html(row.type || "-") + "</td>" +
                "<td>" + html(row.lenderBankName || shortId(row.lenderBankId)) + "</td>" +
                "<td>" + html(row.borrowerBankName || shortId(row.borrowerBankId)) + "</td>" +
                "<td>" + money(row.principal) + "</td>" +
                "<td>" + money(row.remaining) + "</td>" +
                "<td>" + percent(row.annualRate, 2) + "</td>" +
                "<td>" + html(String(row.status || "-").toUpperCase()) + "</td>";
            el.bankLoansBody.appendChild(tr);
        }
        if (loans.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"8\">No interbank loans linked to this bank.</td>";
            el.bankLoansBody.appendChild(tr);
        }
    }

    function runBankAction(action, payload) {
        const bankId = currentBankId();
        if (!bankId) {
            pushAlert("No bank loaded.", "error");
            return Promise.resolve({ok: false, message: "No bank loaded."});
        }
        const body = Object.assign({action: action}, payload || {});
        return api("/api/webadmin/bank/" + encodeURIComponent(bankId) + "/action", {
            method: "POST",
            body: JSON.stringify(body)
        }).then(result => {
            const explicitOk = (result.result && typeof result.result.success === "boolean")
                ? result.result.success
                : !!result.ok;
            const message = (result.result && result.result.message)
                ? result.result.message
                : (result.message || (explicitOk ? "Bank action completed." : "Bank action failed."));
            prependBankActionLog(message, explicitOk ? "success" : "error", "");
            pushAlert(message, actionTone(explicitOk));
            if (explicitOk) {
                setTopNotice(message, false);
            } else {
                setTopNotice(message, true);
            }

            if (result.bank) {
                state.bankDetail = Object.assign({}, result, {ok: true});
                renderBankDetail();
                return result;
            }
            return loadBankDetail(bankId).then(() => result);
        }).catch(error => {
            const message = "Bank action failed: " + error.message;
            prependBankActionLog(message, "error", "");
            pushAlert(message, "error");
            setTopNotice(message, true);
            return {ok: false, message: error.message};
        });
    }

    function currentShopId() {
        const detail = state.shopDetail || {};
        const shop = detail.shop || {};
        return String(shop.shopId || "");
    }

    function prependShopActionLog(message, tone, details) {
        if (!el.shopActionOutput) {
            return;
        }
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, "0");
        const mm = String(now.getMinutes()).padStart(2, "0");
        const ss = String(now.getSeconds()).padStart(2, "0");
        const level = tone === "success" ? "SUCCESS" : (tone === "error" ? "ERROR" : "INFO");
        const entry = "[" + hh + ":" + mm + ":" + ss + "] " + level + " - " + (message || "Shop action completed.")
            + (details ? "\n" + details : "");
        const previous = String(el.shopActionOutput.textContent || "").trim();
        el.shopActionOutput.textContent = previous ? (entry + "\n\n" + previous) : entry;
    }

    function renderShopRegionTable(body, rows, kind) {
        if (!body) {
            return;
        }
        body.textContent = "";
        const list = rows || [];
        for (const row of list) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.index || "-") + "</td>" +
                "<td>" + html(row.dimensionId || "-") + "</td>" +
                "<td>" + html((row.minX || 0) + ", " + (row.minY || 0) + ", " + (row.minZ || 0)) + "</td>" +
                "<td>" + html((row.maxX || 0) + ", " + (row.maxY || 0) + ", " + (row.maxZ || 0)) + "</td>" +
                "<td>" + abbreviateNumber(row.volumeBlocks || 0) + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost shop-region-row-action\" data-kind=\"" + html(kind) + "\" data-dim=\"" + html(row.dimensionId || "") + "\" data-min-x=\"" + html(row.minX || 0) + "\" data-min-y=\"" + html(row.minY || 0) + "\" data-min-z=\"" + html(row.minZ || 0) + "\" data-max-x=\"" + html(row.maxX || 0) + "\" data-max-y=\"" + html(row.maxY || 0) + "\" data-max-z=\"" + html(row.maxZ || 0) + "\">Remove</button></td>";
            body.appendChild(tr);
        }
        if (list.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"6\">No regions.</td>";
            body.appendChild(tr);
        }
    }

    function renderShopDetail() {
        const data = state.shopDetail;
        if (!data || (!data.ok && !data.shop)) {
            el.shopTitle.textContent = "Shop not found";
            el.shopSubtitle.textContent = data && data.message ? data.message : "No shop payload.";
            return;
        }

        const shop = data.shop || {};
        const kpis = data.kpis || {};
        const claims = data.claims || [];
        const stockrooms = data.stockrooms || [];
        const permissions = data.permissions || [];
        const hours = data.hours || {};
        const lighting = data.lighting || {};
        const finance = data.finance || {};
        const vault = data.vault || [];
        const employees = data.employees || [];
        const orders = data.orders || [];
        const pallets = data.orderPallets || [];
        const shelves = data.shelves || [];
        const stockroomItems = data.stockroomItems || [];
        const roadmap = data.roadmap || [];

        el.shopTitle.textContent = (shop.name || "Shop") + " (" + shortId(shop.shopId) + ")";
        el.shopSubtitle.textContent = "Owner " + (shop.ownerName || "Unknown")
            + " | Type " + (shop.type || "-")
            + " | Level " + (shop.level || 1);

        if (el.shopNameInput) {
            el.shopNameInput.value = String(shop.name || "");
        }
        if (el.shopTypeSelect) {
            el.shopTypeSelect.value = String(shop.type || "Retail");
        }
        if (el.shopLevelInput) {
            el.shopLevelInput.value = String(shop.level || 1);
        }
        if (el.shopHoursOpenInput) {
            el.shopHoursOpenInput.value = String(hours.openLabel || "");
        }
        if (el.shopHoursCloseInput) {
            el.shopHoursCloseInput.value = String(hours.closeLabel || "");
        }
        if (el.shopClosedDelivererSelect) {
            el.shopClosedDelivererSelect.value = hours.closedDelivererStockroomAccess ? "true" : "false";
        }
        if (el.shopLightingEnabledSelect) {
            el.shopLightingEnabledSelect.value = "";
        }
        if (el.shopLightingMainModeSelect) {
            el.shopLightingMainModeSelect.value = "";
        }
        if (el.shopLightingStockroomModeSelect) {
            el.shopLightingStockroomModeSelect.value = "";
        }
        if (el.shopLightingExcludeSelect) {
            el.shopLightingExcludeSelect.value = "";
        }
        if (el.shopLightingLevelInput) {
            el.shopLightingLevelInput.value = String(lighting.level || "");
        }
        if (el.shopSettlementAccountInput) {
            el.shopSettlementAccountInput.value = String(finance.settlementAccountId || shop.settlementAccountId || "");
        }

        renderKpiCards(el.shopKpis, [
            {label: "Revenue", value: money(kpis.revenueDollars || shop.revenueDollars)},
            {label: "Next Target", value: money(kpis.nextTargetDollars || shop.nextTargetDollars)},
            {label: "Claim Usage", value: abbreviateNumber(kpis.claimUsedBlocks || 0) + " / " + abbreviateNumber(kpis.claimCapacityBlocks || 0)},
            {label: "Stockroom Usage", value: abbreviateNumber(kpis.stockroomUsedBlocks || 0) + " / " + abbreviateNumber(kpis.stockroomCapacityBlocks || 0)},
            {label: "Cashiers", value: abbreviateNumber(kpis.cashiers || 0) + " (" + abbreviateNumber(kpis.linkedCashiers || 0) + " linked)"},
            {label: "Display Capacity", value: abbreviateNumber(kpis.displayCapacity || 0)},
            {label: "Cashier Capacity", value: abbreviateNumber(kpis.cashierCapacity || 0)},
            {label: "Delivery Pallets", value: abbreviateNumber(kpis.assignedPalletCapacity || 0)},
            {label: "Cash Tx", value: abbreviateNumber(kpis.cashTxCount || 0)},
            {label: "Terminal Tx", value: abbreviateNumber(kpis.terminalTxCount || 0)},
            {label: "Cash Total", value: money((kpis.cashTotalCents || 0) / 100)},
            {label: "Terminal Total", value: money((kpis.terminalTotalCents || 0) / 100)}
        ]);

        renderInfoCards(el.shopSummaryGrid, [
            {label: "Shop ID", value: String(shop.shopId || "-")},
            {label: "Owner", value: String(shop.ownerName || "-")},
            {label: "Owner UUID", value: String(shop.ownerId || "-")},
            {label: "Type", value: String(shop.type || "-")},
            {label: "Level", value: String(shop.level || 1)},
            {label: "Created", value: formatEpochMillis(shop.createdAtMillis)},
            {label: "Checkout Terminal", value: String(shop.checkoutTerminal || "-")},
            {label: "Settlement Account", value: String(shop.settlementAccountId || finance.settlementAccountId || "-")}
        ]);

        renderShopRegionTable(el.shopClaimsBody, claims, "claim");
        renderShopRegionTable(el.shopStockroomsBody, stockrooms, "stockroom");

        el.shopPermissionsBody.textContent = "";
        for (const row of permissions) {
            const tr = document.createElement("tr");
            const action = row.owner
                ? "<span class=\"hint\">Owner role locked</span>"
                : "<button type=\"button\" class=\"btn btn-ghost shop-permission-row-action\" data-player-id=\"" + html(row.playerId || "") + "\">Remove</button>";
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td>" + html(row.role || "-") + "</td>" +
                "<td>" + (row.online ? statusBadge("Online", "status-online") : statusBadge("Offline", "status-offline")) + "</td>" +
                "<td>" + (row.owner ? statusBadge("Owner", "status-active") : statusBadge("Delegated", "status-muted")) + "</td>" +
                "<td>" + action + "</td>";
            el.shopPermissionsBody.appendChild(tr);
        }
        if (permissions.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"5\">No permission rows.</td>";
            el.shopPermissionsBody.appendChild(tr);
        }

        renderInfoCards(el.shopHoursGrid, [
            {label: "Open", value: String(hours.openLabel || "-")},
            {label: "Close", value: String(hours.closeLabel || "-")},
            {label: "Status", value: hours.openNow ? "OPEN" : "CLOSED"},
            {label: "Until Change", value: String(hours.untilChangeSeconds || "-") + "s"},
            {label: "Closed Deliverer Stockroom Access", value: hours.closedDelivererStockroomAccess ? "Enabled" : "Disabled"},
            {label: "Lighting Enabled", value: lighting.enabled ? "Yes" : "No"},
            {label: "Main Lighting Mode", value: String(lighting.mainMode || "-")},
            {label: "Stockroom Lighting Mode", value: String(lighting.stockroomMode || "-")},
            {label: "Exclude Stockroom", value: lighting.excludeStockroom ? "Yes" : "No"},
            {label: "Light Level", value: String(lighting.level || "-")},
            {label: "Managed Light Blocks", value: String(lighting.managedBlocks || 0)}
        ]);

        renderInfoCards(el.shopFinanceGrid, [
            {label: "Settlement Account", value: String(finance.settlementAccountId || "-")},
            {label: "Checkout Account", value: String(finance.checkoutAccountId || "-")},
            {label: "Checkout Terminal", value: String(finance.checkoutTerminal || "-")},
            {label: "Cash Tx", value: abbreviateNumber(finance.cashTxCount || 0)},
            {label: "Cash Total", value: money((finance.cashTotalCents || 0) / 100)},
            {label: "Cash Customers", value: abbreviateNumber(finance.cashCustomers || 0)},
            {label: "Terminal Tx", value: abbreviateNumber(finance.terminalTxCount || 0)},
            {label: "Terminal Total", value: money((finance.terminalTotalCents || 0) / 100)},
            {label: "Terminal Customers", value: abbreviateNumber(finance.terminalCustomers || 0)},
            {label: "Vault Total", value: money((finance.vaultTotalCents || 0) / 100)}
        ]);

        el.shopVaultBody.textContent = "";
        for (const row of vault) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>$" + html(((row.denominationCents || 0) / 100).toFixed(2)) + "</td>" +
                "<td>" + html(row.count || 0) + "</td>";
            el.shopVaultBody.appendChild(tr);
        }
        if (vault.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"2\">No vault data.</td>";
            el.shopVaultBody.appendChild(tr);
        }

        el.shopEmployeesBody.textContent = "";
        for (const row of employees) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.label || "-") + "</td>" +
                "<td>" + html(shortId(row.employeeId)) + "</td>" +
                "<td>" + html(shortId(row.cashierId)) + "</td>" +
                "<td>" + html((row.dimensionId || "-") + " (" + (row.x || 0) + ", " + (row.y || 0) + ", " + (row.z || 0) + ")") + "</td>" +
                "<td>" + html(row.terminal || "-") + "</td>" +
                "<td><button type=\"button\" class=\"btn btn-ghost shop-employee-row-action\" data-selection=\"" + html(row.employeeId || row.cashierId || "") + "\">Fire</button></td>";
            el.shopEmployeesBody.appendChild(tr);
        }
        if (employees.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"6\">No active employees found.</td>";
            el.shopEmployeesBody.appendChild(tr);
        }

        el.shopOrdersBody.textContent = "";
        for (const row of orders) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(shortId(row.orderId)) + "</td>" +
                "<td>" + html(row.itemName || row.itemId || "-") + "</td>" +
                "<td>" + html(row.quantity || 0) + "</td>" +
                "<td>" + money((row.rewardCents || 0) / 100) + "</td>" +
                "<td>" + html(row.status || "-") + "</td>" +
                "<td>" + html(row.acceptedBy || "-") + "</td>" +
                "<td>" + html(row.remainingSeconds || 0) + "s</td>";
            el.shopOrdersBody.appendChild(tr);
        }
        if (orders.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"7\">No orders.</td>";
            el.shopOrdersBody.appendChild(tr);
        }

        el.shopOrderPalletsBody.textContent = "";
        for (const row of pallets) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(shortId(row.palletId)) + "</td>" +
                "<td>" + html((row.dimensionId || "-") + " (" + (row.x || 0) + ", " + (row.y || 0) + ", " + (row.z || 0) + ")") + "</td>" +
                "<td>" + (row.active ? statusBadge("Yes", "status-online") : statusBadge("No", "status-offline")) + "</td>" +
                "<td>" + (row.full ? statusBadge("Full", "status-blocked") : statusBadge("Available", "status-active")) + "</td>";
            el.shopOrderPalletsBody.appendChild(tr);
        }
        if (pallets.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"4\">No assigned pallets.</td>";
            el.shopOrderPalletsBody.appendChild(tr);
        }

        el.shopShelfBody.textContent = "";
        for (const row of shelves) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html((row.dimensionId || "-") + " (" + (row.x || 0) + ", " + (row.y || 0) + ", " + (row.z || 0) + ")") + "</td>" +
                "<td>" + html(row.shelfType || "-") + "</td>" +
                "<td>" + html(row.configuredSlots || 0) + "</td>" +
                "<td>" + html(row.stockUnits || 0) + "</td>" +
                "<td>" + html(row.lowStockSlots || 0) + "</td>" +
                "<td>" + html(row.outOfStockSlots || 0) + "</td>";
            el.shopShelfBody.appendChild(tr);
        }
        if (shelves.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"6\">No shelf data.</td>";
            el.shopShelfBody.appendChild(tr);
        }

        el.shopStockroomItemsBody.textContent = "";
        for (const row of stockroomItems) {
            const tr = document.createElement("tr");
            const itemLabel = String(row.itemName || row.itemId || "-");
            const itemId = String(row.itemId || "");
            const itemCell =
                "<div class=\"item-cell\">" +
                buildItemVisual(itemId, itemLabel) +
                "<div class=\"item-label\">" +
                "<strong>" + html(itemLabel) + "</strong>" +
                "<span class=\"hint\">" + html(itemId || "unknown:item") + "</span>" +
                "</div>" +
                "</div>";
            tr.innerHTML =
                "<td>" + itemCell + "</td>" +
                "<td>" + html(row.count || 0) + "</td>" +
                "<td>" + html(row.inventoryType || "-") + "</td>" +
                "<td>" + html((row.dimensionId || "-") + " (" + (row.x || 0) + ", " + (row.y || 0) + ", " + (row.z || 0) + ")") + "</td>";
            el.shopStockroomItemsBody.appendChild(tr);
        }
        if (stockroomItems.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"4\">No stockroom inventory data.</td>";
            el.shopStockroomItemsBody.appendChild(tr);
        }
        hydrateItemModels(el.pages.shop);

        if (!el.shopRoadmapTrack) {
            return;
        }
        el.shopRoadmapTrack.textContent = "";
        for (const row of roadmap) {
            const stateClass = classifyRoadmapState(row.state, row.level, shop.level);
            const node = document.createElement("article");
            node.className = "roadmap-node " + stateClass;
            node.innerHTML =
                "<header class=\"roadmap-head\">" +
                "<span class=\"roadmap-level\">Level " + html(row.level || 0) + "</span>" +
                "<span class=\"roadmap-state\">" + html(row.state || stateClass.toUpperCase()) + "</span>" +
                "</header>" +
                "<div class=\"roadmap-target\">" +
                "<span>Required Revenue</span>" +
                "<strong>" + money(row.requiredRevenueDollars || 0) + "</strong>" +
                "</div>" +
                "<div class=\"roadmap-metrics\">" +
                "<div class=\"roadmap-metric\"><span>Claims</span><strong>" + html(abbreviateNumber(row.claimCapacityBlocks || 0)) + "</strong></div>" +
                "<div class=\"roadmap-metric\"><span>Stockrooms</span><strong>" + html(abbreviateNumber(row.stockroomCapacityBlocks || 0)) + "</strong></div>" +
                "<div class=\"roadmap-metric\"><span>Display</span><strong>" + html(abbreviateNumber(row.displayCapacity || 0)) + "</strong></div>" +
                "<div class=\"roadmap-metric\"><span>Cashiers</span><strong>" + html(abbreviateNumber(row.cashierCapacity || 0)) + "</strong></div>" +
                "<div class=\"roadmap-metric\"><span>Pallets</span><strong>" + html(abbreviateNumber(row.palletCapacity || 0)) + "</strong></div>" +
                "</div>";
            el.shopRoadmapTrack.appendChild(node);
        }
        if (roadmap.length === 0) {
            const node = document.createElement("article");
            node.className = "roadmap-node locked";
            node.innerHTML =
                "<header class=\"roadmap-head\">" +
                "<span class=\"roadmap-level\">No roadmap data</span>" +
                "<span class=\"roadmap-state\">N/A</span>" +
                "</header>" +
                "<div class=\"roadmap-target\"><span>Add a shop progression config to populate this panel.</span></div>";
            el.shopRoadmapTrack.appendChild(node);
        }
    }

    function runShopAction(action, payload) {
        const shopId = currentShopId();
        if (!shopId) {
            pushAlert("No shop loaded.", "error");
            return Promise.resolve({ok: false, message: "No shop loaded."});
        }
        const body = Object.assign({action: action}, payload || {});
        return api("/api/webadmin/shop/" + encodeURIComponent(shopId) + "/action", {
            method: "POST",
            body: JSON.stringify(body)
        }).then(result => {
            const ok = !!result.ok;
            const message = result.message || (ok ? "Shop action completed." : "Shop action failed.");
            prependShopActionLog(message, ok ? "success" : "error", "");
            pushAlert(message, actionTone(ok));
            if (ok) {
                setTopNotice(message, false);
            } else {
                setTopNotice(message, true);
            }

            if (result.deleted) {
                location.hash = "#/shops";
                return result;
            }

            if (result.shop) {
                state.shopDetail = result;
                renderShopDetail();
                return result;
            }
            return loadShopDetail(shopId).then(() => result);
        }).catch(error => {
            const message = "Shop action failed: " + error.message;
            prependShopActionLog(message, "error", "");
            pushAlert(message, "error");
            setTopNotice(message, true);
            return {ok: false, message: error.message};
        });
    }

    function currentAccountId() {
        const detail = state.accountDetail || {};
        const account = detail.account || {};
        return String(account.accountId || "");
    }

    function renderAccountDetail(routePlayerId) {
        const data = state.accountDetail;
        if (!data || !data.ok) {
            el.accountTitle.textContent = "Account not found";
            el.accountSubtitle.textContent = "";
            return;
        }

        const account = data.account || {};
        const owner = data.owner || {};
        const bank = data.bank || {};
        const limits = data.limits || {};
        const certificate = data.certificate || {};
        const safeBox = data.safeBox || {};
        const roles = data.roles || [];
        const cards = data.cards || [];
        const loans = data.loans || [];
        const transactions = data.transactions || [];

        const playerId = String(routePlayerId || owner.playerId || "");
        el.accountTitle.textContent = (bank.name || "Bank") + " / " + shortId(account.accountId);
        el.accountSubtitle.textContent = "Owner " + (owner.name || "Unknown") + " (" + shortId(owner.playerId) + ")";

        if (playerId) {
            el.accountBack.dataset.playerId = playerId;
            el.accountOpenPlayer.dataset.playerId = playerId;
        } else {
            el.accountBack.dataset.playerId = "";
            el.accountOpenPlayer.dataset.playerId = "";
        }

        if (el.accountAccessTypeSelect) {
            const accessType = String(account.accessType || "PERSONAL").toUpperCase();
            const values = ["PERSONAL", "JOINT", "BUSINESS"];
            if (!values.includes(accessType)) {
                el.accountAccessTypeSelect.value = "PERSONAL";
            } else {
                el.accountAccessTypeSelect.value = accessType;
            }
        }
        if (el.accountBusinessLabelInput) {
            el.accountBusinessLabelInput.value = String(account.businessLabel || "");
        }
        if (el.accountDefaultedSelect) {
            el.accountDefaultedSelect.value = account.defaulted ? "true" : "false";
        }
        if (el.accountFreezeReason) {
            el.accountFreezeReason.value = String(account.frozenReason || "");
        }
        if (el.accountCreditInput) {
            el.accountCreditInput.value = String(account.creditScore || 0);
        }

        renderKpiCards(el.accountKpis, [
            {label: "Balance", value: money(account.balance)},
            {label: "Credit Score", value: String(account.creditScore || 0)},
            {label: "Bank Status", value: String(bank.status || "UNKNOWN")},
            {label: "Account Type", value: String(account.type || "-")},
            {label: "Primary", value: account.primary ? "Yes" : "No"},
            {label: "Frozen", value: account.frozen ? "Yes" : "No"},
            {label: "Defaulted", value: account.defaulted ? "Yes" : "No"},
            {label: "Active Loans", value: abbreviateNumber(account.activeLoans || 0)},
            {label: "Cards", value: abbreviateNumber(cards.length)},
            {label: "Transactions", value: abbreviateNumber(account.transactions || 0)},
            {label: "Owner Online", value: owner.online ? "Yes" : "No"},
            {label: "Access Roles", value: abbreviateNumber(roles.length)}
        ]);

        renderInfoCards(el.accountSummaryGrid, [
            {label: "Account ID", value: String(account.accountId || "-"), hint: "Unique account UUID."},
            {label: "Owner", value: String(owner.name || "Unknown"), hint: "Account owner profile."},
            {label: "Owner UUID", value: String(owner.playerId || "-"), hint: "Player UUID tied to this account."},
            {label: "Bank", value: String(bank.name || "-"), hint: "Bank hosting this account."},
            {label: "Bank ID", value: String(bank.bankId || "-"), hint: "Unique bank UUID."},
            {label: "Bank Owner", value: String(bank.ownerName || "-"), hint: "Owner of the bank."},
            {label: "Access Type", value: String(account.accessType || "PERSONAL"), hint: "PERSONAL / JOINT / BUSINESS."},
            {label: "Business Label", value: String(account.businessLabel || "-"), hint: "Business display label if set."},
            {label: "PIN Set", value: account.pinSet ? "Yes" : "No", hint: "Shows whether PIN exists."},
            {label: "Created", value: formatIsoDateTime(account.createdAt), hint: "Account creation timestamp."},
            {label: "Bank Reserve Ratio", value: percent(bank.reserveRatioPct, 2), hint: "Reserve/deposit ratio of the host bank."},
            {label: "Bank Interest Rate", value: percent(bank.interestRate, 2), hint: "Current base annual bank rate."}
        ]);

        renderInfoCards(el.accountLimitsGrid, [
            {label: "Default Withdrawal Limit", value: money(limits.configuredWithdrawalLimit), hint: "Configured per-withdrawal cap."},
            {label: "Effective Withdrawal Limit", value: money(limits.effectiveWithdrawalLimit), hint: "Current active per-withdrawal cap."},
            {label: "Daily Withdrawal Limit", value: money(limits.dailyWithdrawalLimit), hint: "Maximum outgoing ATM withdrawals per day."},
            {label: "Daily Withdrawn", value: money(limits.dailyWithdrawn), hint: "Current consumed daily withdrawal amount."},
            {label: "Daily Remaining", value: money(limits.dailyRemaining), hint: "Remaining daily withdrawal capacity."},
            {label: "Daily Reset Time", value: formatEpochMillis(limits.dailyResetEpochMillis), hint: "Next automatic daily reset."},
            {label: "Temp Limit", value: limits.tempWithdrawalLimit ? money(limits.tempWithdrawalLimit) : "None", hint: "Temporary override limit if active."},
            {label: "Temp Limit Expiry (Game Time)", value: String(limits.tempWithdrawalLimitExpiresAtGameTime || "-"), hint: "Temporary limit expiry in game ticks."},
            {label: "Certificate Tier", value: String(certificate.tier || "None"), hint: "Certificate account tier if applicable."},
            {label: "Certificate Locked", value: certificate.locked ? "Yes" : "No", hint: "Whether certificate funds are locked."},
            {label: "Certificate Rate", value: percent(certificate.rate, 2), hint: "Certificate APR."},
            {label: "Certificate Maturity", value: String(certificate.maturityGameTime || "-"), hint: "Certificate maturity game time."},
            {label: "Certificate Settled", value: certificate.maturitySettled ? "Yes" : "No", hint: "Whether maturity payout already settled."},
            {label: "Safe Box Usage", value: abbreviateNumber(safeBox.usedSlots || 0) + "/" + abbreviateNumber(safeBox.totalSlots || 0), hint: "Used and total safe box slots."}
        ]);

        el.accountRolesBody.textContent = "";
        for (const row of roles) {
            const tr = document.createElement("tr");
            const revokeBtn = row.owner
                ? "<span class=\"hint\">Owner role locked</span>"
                : "<button type=\"button\" class=\"btn btn-ghost role-row-action\" data-role-action=\"revoke\" data-player-id=\"" + html(row.playerId) + "\">Revoke</button>";
            tr.innerHTML =
                "<td>" + html((row.name || "Unknown") + " (" + shortId(row.playerId) + ")") + "</td>" +
                "<td>" + html(row.role || "-") + "</td>" +
                "<td>" + (row.owner ? statusBadge("Owner", "status-online") : statusBadge("Delegated", "status-offline")) + "</td>" +
                "<td>" + revokeBtn + "</td>";
            el.accountRolesBody.appendChild(tr);
        }
        if (roles.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"4\">No access roles.</td>";
            el.accountRolesBody.appendChild(tr);
        }

        el.accountSafeboxBody.textContent = "";
        const safeRows = safeBox.rows || [];
        for (const row of safeRows) {
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.slot) + "</td>" +
                "<td>" + html(row.itemId || "-") + "</td>" +
                "<td>" + html(row.count || 0) + "</td>" +
                "<td>" +
                "<button type=\"button\" class=\"btn btn-ghost safebox-row-action\" data-safebox-action=\"deliver\" data-slot=\"" + html(row.slot) + "\">Deliver</button> " +
                "<button type=\"button\" class=\"btn btn-ghost safebox-row-action\" data-safebox-action=\"delete\" data-slot=\"" + html(row.slot) + "\">Delete</button>" +
                "</td>";
            el.accountSafeboxBody.appendChild(tr);
        }
        if (safeRows.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"4\">No items in safe box.</td>";
            el.accountSafeboxBody.appendChild(tr);
        }

        el.accountCardsBody.textContent = "";
        for (const row of cards) {
            const blocked = String(row.status || "").toUpperCase() === "BLOCKED";
            const tierBadge = row.privateCard
                ? "<span class=\"status-chip status-private\">Private</span>"
                : "<span class=\"status-chip status-muted\">Standard</span>";
            const cardActionBtn = blocked
                ? "<button type=\"button\" class=\"btn btn-ghost card-row-action\" data-card-action=\"unblock\" data-card-id=\"" + html(row.cardId) + "\">Unblock</button>"
                : "<button type=\"button\" class=\"btn btn-ghost card-row-action\" data-card-action=\"block\" data-card-id=\"" + html(row.cardId) + "\">Block</button>";
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(row.maskedNumber || shortId(row.cardId)) + "<br><span class=\"hint\">" + html(shortId(row.cardId)) + "</span><br>" + tierBadge + "</td>" +
                "<td>" + (blocked ? statusBadge("Blocked", "status-blocked") : statusBadge("Active", "status-active")) + "</td>" +
                "<td>" + html(row.holderName || "-") + "</td>" +
                "<td>" + html(formatEpochMillis(row.issuedEpochMillis)) + "</td>" +
                "<td>" + html(formatEpochMillis(row.expiryEpochMillis)) + "</td>" +
                "<td>" +
                "<button type=\"button\" class=\"btn btn-ghost card-row-action\" data-card-action=\"add\" data-card-id=\"" + html(row.cardId) + "\">Add To Inventory</button> " +
                "<button type=\"button\" class=\"btn btn-ghost card-row-action\" data-card-action=\"replace\" data-card-id=\"" + html(row.cardId) + "\">Replace</button> " +
                cardActionBtn + " " +
                "<button type=\"button\" class=\"btn btn-danger card-row-action\" data-card-action=\"delete\" data-card-id=\"" + html(row.cardId) + "\">Delete</button>" +
                "</td>";
            el.accountCardsBody.appendChild(tr);
        }
        if (cards.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"6\">No linked cards.</td>";
            el.accountCardsBody.appendChild(tr);
        }

        el.accountLoansBody.textContent = "";
        for (const row of loans) {
            const stateLabel = row.defaulted ? statusBadge("Defaulted", "status-blocked") : statusBadge("Active", "status-active");
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(shortId(row.loanId)) + "</td>" +
                "<td>" + html(row.lenderName || "-") + "</td>" +
                "<td>" + money(row.principal) + "</td>" +
                "<td>" + money(row.remainingBalance) + "</td>" +
                "<td>" + percent(row.annualInterestRate, 2) + "</td>" +
                "<td>" + html((row.paymentsMade || 0) + "/" + (row.totalPayments || 0)) + "</td>" +
                "<td>" + stateLabel + "</td>" +
                "<td>" +
                "<button type=\"button\" class=\"btn btn-ghost loan-row-action\" data-loan-action=\"toggle-default\" data-loan-id=\"" + html(row.loanId) + "\" data-defaulted=\"" + (row.defaulted ? "true" : "false") + "\">Toggle Default</button> " +
                "<button type=\"button\" class=\"btn btn-danger loan-row-action\" data-loan-action=\"delete\" data-loan-id=\"" + html(row.loanId) + "\">Delete</button>" +
                "</td>";
            el.accountLoansBody.appendChild(tr);
        }
        if (loans.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"8\">No active loans.</td>";
            el.accountLoansBody.appendChild(tr);
        }

        el.accountTransactionsBody.textContent = "";
        for (const row of transactions) {
            const tone = transactionKind(row.direction, row.amount);
            const tr = document.createElement("tr");
            tr.innerHTML =
                "<td>" + html(formatIsoDateTime(row.timestamp)) + "</td>" +
                "<td><span class=\"tx-pill tx-" + html(tone.key) + "\">" + html(String(row.direction || tone.label)) + "</span></td>" +
                "<td class=\"tx-amount " + html(tone.key) + "\">" + html(signedMoney(row.amount, tone.key)) + "</td>" +
                "<td>" + html(row.description || "-") + "</td>" +
                "<td>" + html(shortId(row.senderAccountId)) + "</td>" +
                "<td>" + html(shortId(row.receiverAccountId)) + "</td>" +
                "<td>" + html(shortId(row.transactionId)) + "</td>";
            el.accountTransactionsBody.appendChild(tr);
        }
        if (transactions.length === 0) {
            const tr = document.createElement("tr");
            tr.innerHTML = "<td colspan=\"7\">No account transactions recorded.</td>";
            el.accountTransactionsBody.appendChild(tr);
        }
    }

    function accountActionDetailText(result) {
        if (!result || typeof result !== "object") {
            return "";
        }
        const parts = [];
        if (result.deliveryStatus === "queued_offline") {
            parts.push("Owner is offline; delivery is queued.");
        } else if (result.deliveryStatus === "queued_inventory_full") {
            parts.push("Owner inventory is full; delivery is queued.");
        } else if (result.deliveryStatus === "delivered") {
            parts.push("Delivered directly to owner inventory.");
        }
        if (result.cardId) {
            parts.push("Card: " + shortId(result.cardId));
        }
        if (result.deleted) {
            parts.push("Account removed.");
        }
        return parts.join(" ");
    }

    function prependAccountActionLog(message, tone, details) {
        if (!el.accountActionOutput) {
            return;
        }
        const now = new Date();
        const hh = String(now.getHours()).padStart(2, "0");
        const mm = String(now.getMinutes()).padStart(2, "0");
        const ss = String(now.getSeconds()).padStart(2, "0");
        const level = tone === "success" ? "SUCCESS" : (tone === "error" ? "ERROR" : "INFO");
        const entry = "[" + hh + ":" + mm + ":" + ss + "] " + level + " - " + (message || "Action completed.")
            + (details ? "\n" + details : "");
        const previous = String(el.accountActionOutput.textContent || "").trim();
        el.accountActionOutput.textContent = previous ? (entry + "\n\n" + previous) : entry;
    }

    function runAccountAction(action, payload) {
        const accountId = currentAccountId();
        if (!accountId) {
            pushAlert("No account loaded.", "error");
            return Promise.resolve({ok: false, message: "No account loaded."});
        }
        const body = Object.assign({action: action}, payload || {});
        return api("/api/webadmin/account/" + encodeURIComponent(accountId) + "/action", {
            method: "POST",
            body: JSON.stringify(body)
        }).then(result => {
            const ok = !!result.ok;
            const message = result.message || (ok ? "Account action completed." : "Account action failed.");
            const details = accountActionDetailText(result);
            prependAccountActionLog(message, ok ? "success" : "error", details);
            pushAlert(message, actionTone(ok));
            if (result.ok) {
                setTopNotice(message, false);
            } else {
                setTopNotice(message, true);
            }
            if (result.deleted) {
                const playerId = String(result.playerId || "");
                if (playerId) {
                    location.hash = "#/players/" + encodeURIComponent(playerId);
                } else {
                    location.hash = "#/users";
                }
                return result;
            }
            if (result.account && result.ok) {
                state.accountDetail = result;
                renderAccountDetail(parseRoute().playerId || "");
                return result;
            }
            return loadAccountDetail(parseRoute().playerId || "", accountId).then(() => result);
        }).catch(error => {
            const message = "Account action failed: " + error.message;
            prependAccountActionLog(message, "error", "");
            pushAlert(message, "error");
            setTopNotice(message, true);
            return {ok: false, message: error.message};
        });
    }

    function renderAudit(entries) {
        const lines = [];
        for (const entry of entries || []) {
            lines.push(
                "[" + (entry.timestamp || "-") + "] " +
                (entry.category || "-") +
                " | " + (entry.success ? "OK" : "FAIL") +
                " | " + (entry.detail || "-")
            );
        }
        el.auditOutput.textContent = lines.length > 0 ? lines.join("\n") : "No audit entries yet.";
    }

    function runServerCommand(raw) {
        const command = (raw == null ? el.serverCommand.value : raw).trim();
        if (!command) {
            return Promise.resolve();
        }
        return api("/api/webadmin/command", {
            method: "POST",
            body: JSON.stringify({command: command})
        }).then(result => {
            const rendered = "/" + command.replace(/^\//, "");
            const resultCode = Number(result.result || 0);
            const ok = resultCode >= 0;
            const summary = ok
                ? "Command ran successfully."
                : "Command returned a negative result.";
            el.commandOutput.textContent =
                "Command: " + rendered + "\n" +
                "Result code: " + resultCode + "\n" +
                "Status: " + summary;
            pushAlert(rendered + " executed (result " + resultCode + ").", ok ? "success" : "warn");
        }).catch(error => {
            el.commandOutput.textContent = "Error: " + error.message;
            pushAlert("Command failed: " + error.message, "error");
        });
    }

    function refreshHealth() {
        return api("/api/webadmin/health")
            .then(health => {
                renderHealthPanel(health);
            })
            .catch(() => {
                setHealthState("error", "error");
            });
    }

    function refreshDashboard() {
        return api("/api/webadmin/dashboard")
            .then(data => {
                state.dashboard = data;
                renderDashboard();
            })
            .catch(error => {
                el.pageSubtitle.textContent = "Dashboard error: " + error.message;
            });
    }

    function refreshEntities() {
        return Promise.allSettled([
            api("/api/webadmin/banks"),
            api("/api/webadmin/shops")
        ]).then(results => {
            const banks = results[0].status === "fulfilled" ? results[0].value : {rows: []};
            const shops = results[1].status === "fulfilled" ? results[1].value : {rows: []};
            state.banksRows = banks.rows || [];
            state.shopsRows = shops.rows || [];
            state.banksMetrics = banks.metrics || {};
            state.shopsMetrics = shops.metrics || {};
            renderEntities();
        });
    }

    function refreshShopItems() {
        return api("/api/webadmin/shop-items")
            .then(data => {
                state.shopItemsRows = data.rows || [];
                state.shopItemsMetrics = data.metrics || {};
                renderShopItems();
            });
    }

    function loadShopItemDetail(itemId) {
        const raw = String(itemId || "").trim();
        if (!raw) {
            return Promise.resolve();
        }
        return api("/api/webadmin/shop-items/" + encodeURIComponent(raw))
            .then(data => {
                state.shopItemDetail = data;
                renderShopItemDetail();
            })
            .catch(error => {
                state.shopItemDetail = {ok: false, message: error.message, item: {itemId: raw, itemName: raw}};
                renderShopItemDetail();
            });
    }

    function refreshUsers() {
        return api("/api/webadmin/users")
            .then(data => {
                state.usersRows = data.rows || [];
                state.usersMetrics = data.metrics || {};
                renderUsers();
            });
    }

    function refreshAudit() {
        return api("/api/webadmin/audit")
            .then(data => {
                renderAudit(data.entries || []);
            })
            .catch(error => {
                el.auditOutput.textContent = "Audit error: " + error.message;
            });
    }

    function loadBankDetail(bankId) {
        if (!bankId) {
            return Promise.resolve();
        }
        return api("/api/webadmin/bank/" + encodeURIComponent(bankId))
            .then(data => {
                state.bankDetail = data;
                renderBankDetail();
            })
            .catch(error => {
                state.bankDetail = {ok: false, message: error.message};
                el.bankTitle.textContent = "Bank not found";
                el.bankSubtitle.textContent = error.message;
            });
    }

    function loadShopDetail(shopId) {
        if (!shopId) {
            return Promise.resolve();
        }
        return api("/api/webadmin/shop/" + encodeURIComponent(shopId))
            .then(data => {
                state.shopDetail = data;
                renderShopDetail();
            })
            .catch(error => {
                state.shopDetail = {ok: false, message: error.message};
                el.shopTitle.textContent = "Shop not found";
                el.shopSubtitle.textContent = error.message;
            });
    }

    function loadPlayerDetail(playerId) {
        if (!playerId) {
            return Promise.resolve();
        }
        return api("/api/webadmin/users/" + encodeURIComponent(playerId))
            .then(data => {
                state.playerDetail = data;
                renderPlayerDetail();
            })
            .catch(error => {
                state.playerDetail = null;
                el.playerName.textContent = "Player not found";
                el.playerId.textContent = error.message;
            });
    }

    function loadAccountDetail(playerId, accountId) {
        if (!accountId) {
            return Promise.resolve();
        }
        return api("/api/webadmin/account/" + encodeURIComponent(accountId))
            .then(data => {
                state.accountDetail = data;
                renderAccountDetail(playerId);
            })
            .catch(error => {
                state.accountDetail = null;
                el.accountTitle.textContent = "Account not found";
                el.accountSubtitle.textContent = error.message;
            });
    }

    function refreshCurrentPage(routeInfo) {
        const route = routeInfo || parseRoute();
        if (route.page === "dashboard") {
            return Promise.allSettled([refreshDashboard(), refreshHealth()]);
        }
        if (route.page === "health") {
            return Promise.allSettled([refreshHealth()]);
        }
        if (route.page === "banks" || route.page === "shops") {
            state.entityMode = route.page === "shops" ? "shops" : "banks";
            return Promise.allSettled([refreshEntities(), refreshHealth()]);
        }
        if (route.page === "shop-items") {
            return Promise.allSettled([refreshShopItems(), refreshHealth()]);
        }
        if (route.page === "shop-item") {
            return Promise.allSettled([loadShopItemDetail(route.itemId), refreshHealth()]);
        }
        if (route.page === "bank") {
            return Promise.allSettled([loadBankDetail(route.bankId), refreshHealth()]);
        }
        if (route.page === "shop") {
            return Promise.allSettled([loadShopDetail(route.shopId), refreshHealth()]);
        }
        if (route.page === "users") {
            return Promise.allSettled([refreshUsers(), refreshHealth()]);
        }
        if (route.page === "player") {
            return Promise.allSettled([loadPlayerDetail(route.playerId), refreshHealth()]);
        }
        if (route.page === "account") {
            return Promise.allSettled([loadAccountDetail(route.playerId, route.accountId), refreshHealth()]);
        }
        if (route.page === "audit") {
            return Promise.allSettled([refreshAudit(), refreshHealth()]);
        }
        return Promise.allSettled([refreshDashboard(), refreshHealth()]);
    }

    function handleRoute() {
        const route = parseRoute();
        state.route = route.page;
        if (state.bankOverviewModalOpen) {
            closeBankOverviewModal();
        }
        window.scrollTo(0, 0);
        const displayPage = (route.page === "banks" || route.page === "shops") ? "entities"
            : (route.page === "player" ? "player"
                : (route.page === "account" ? "account"
                    : (route.page === "bank" ? "bank"
                        : (route.page === "shop" ? "shop" : route.page))));
        const activeNavRoute = (route.page === "player" || route.page === "account")
            ? "users"
            : (route.page === "bank" ? "banks"
                : (route.page === "shop" ? "shops" : (route.page === "shop-item" ? "shop-items" : route.page)));
        setActivePage(displayPage, activeNavRoute, route.page);
        refreshCurrentPage(route);
    }

    function clearReconnectTimer() {
        if (state.reconnectTimer) {
            clearTimeout(state.reconnectTimer);
            state.reconnectTimer = null;
        }
    }

    function connectWebSocket() {
        clearReconnectTimer();
        const protocol = location.protocol === "https:" ? "wss" : "ws";
        const url = protocol + "://" + location.host + "/ws/webadmin?sessionId=" + encodeURIComponent(state.sessionId);
        const ws = new WebSocket(url);
        state.ws = ws;
        setWsState("connecting", "pending");

        ws.addEventListener("open", () => {
            setWsState("connected", "connected");
        });
        ws.addEventListener("close", () => {
            setWsState("disconnected", "disconnected");
            state.reconnectTimer = setTimeout(connectWebSocket, 1800);
        });
        ws.addEventListener("error", () => {
            setWsState("error", "error");
        });
        ws.addEventListener("message", event => {
            try {
                const envelope = JSON.parse(event.data);
                if (envelope.event === "server_health" && envelope.data) {
                    renderHealthPanel(envelope.data);
                }
            } catch (ignored) {
            }
        });
    }

    function bindEvents() {
        bindHoverTooltips();
        window.addEventListener("hashchange", handleRoute);
        window.addEventListener("resize", updateCardCarouselButtons);
        window.addEventListener("keydown", event => {
            if (event.key === "Escape" && state.bankOverviewModalOpen) {
                closeBankOverviewModal();
            }
        });

        el.refreshAll.addEventListener("click", () => {
            refreshCurrentPage(parseRoute());
        });
        el.refreshAudit.addEventListener("click", () => {
            refreshAudit();
        });
        if (el.themeToggle) {
            el.themeToggle.addEventListener("click", toggleTheme);
        }

        el.entitySearch.addEventListener("input", renderEntities);
        if (el.shopItemsSearch) {
            el.shopItemsSearch.addEventListener("input", renderShopItems);
        }
        if (el.shopItemsIncludeNormal) {
            el.shopItemsIncludeNormal.addEventListener("change", renderShopItems);
        }
        if (el.shopItemsIncludeCreative) {
            el.shopItemsIncludeCreative.addEventListener("change", renderShopItems);
        }
        if (el.shopItemBack) {
            el.shopItemBack.addEventListener("click", () => {
                location.hash = "#/shop-items";
            });
        }
        if (el.shopItemIncludeNormal) {
            state.shopItemIncludeNormal = !!el.shopItemIncludeNormal.checked;
            el.shopItemIncludeNormal.addEventListener("change", () => {
                state.shopItemIncludeNormal = !!el.shopItemIncludeNormal.checked;
                renderShopItemDetail();
            });
        }
        if (el.shopItemIncludeCreative) {
            state.shopItemIncludeCreative = !!el.shopItemIncludeCreative.checked;
            el.shopItemIncludeCreative.addEventListener("change", () => {
                state.shopItemIncludeCreative = !!el.shopItemIncludeCreative.checked;
                renderShopItemDetail();
            });
        }
        if (el.shopItemWindowSegment) {
            el.shopItemWindowSegment.addEventListener("click", event => {
                const button = event.target.closest("[data-shop-item-window]");
                if (!button) {
                    return;
                }
                const days = Number(button.dataset.shopItemWindow || 30);
                if (!Number.isFinite(days) || days <= 0) {
                    return;
                }
                state.shopItemWindowDays = days;
                renderShopItemDetail();
            });
        }
        if (el.shopItemShopsBody) {
            el.shopItemShopsBody.addEventListener("click", event => {
                const button = event.target.closest(".shop-item-open-shop-btn");
                if (!button) {
                    return;
                }
                const shopId = String(button.dataset.shopId || "").trim();
                if (!shopId) {
                    return;
                }
                location.hash = "#/shops/" + encodeURIComponent(shopId);
            });
        }
        el.entityBody.addEventListener("click", event => {
            const bankBtn = event.target.closest(".bank-open-btn");
            if (bankBtn) {
                const bankId = String(bankBtn.dataset.bankId || "").trim();
                if (!bankId) {
                    return;
                }
                location.hash = "#/banks/" + encodeURIComponent(bankId);
                return;
            }
            const shopBtn = event.target.closest(".shop-open-btn");
            if (shopBtn) {
                const shopId = String(shopBtn.dataset.shopId || "").trim();
                if (!shopId) {
                    return;
                }
                location.hash = "#/shops/" + encodeURIComponent(shopId);
            }
        });
        el.usersSearch.addEventListener("input", renderUsers);

        el.bankBack.addEventListener("click", () => {
            location.hash = "#/banks";
        });
        if (el.bankOverviewEditBtn) {
            el.bankOverviewEditBtn.addEventListener("click", openBankOverviewModal);
        }
        if (el.bankOverviewCloseBtn) {
            el.bankOverviewCloseBtn.addEventListener("click", closeBankOverviewModal);
        }
        if (el.bankOverviewCancelBtn) {
            el.bankOverviewCancelBtn.addEventListener("click", closeBankOverviewModal);
        }
        if (el.bankOverviewSaveBtn) {
            el.bankOverviewSaveBtn.addEventListener("click", () => {
                saveBankOverviewFromModal();
            });
        }
        if (el.bankOverviewModal) {
            el.bankOverviewModal.addEventListener("click", event => {
                if (event.target === el.bankOverviewModal) {
                    closeBankOverviewModal();
                }
            });
        }

        el.bankAccountsBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-account-open-btn");
            if (!button) {
                return;
            }
            const accountId = String(button.dataset.accountId || "").trim();
            if (!accountId) {
                return;
            }
            location.hash = "#/accounts/" + encodeURIComponent(accountId);
        });

        el.bankRolesBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-role-row-action");
            if (!button) {
                return;
            }
            const playerId = String(button.dataset.playerId || "").trim();
            if (!playerId) {
                return;
            }
            if (el.bankRolePlayerInput) {
                el.bankRolePlayerInput.value = playerId;
            }
            runBankAction("ROLE_REVOKE", {arg1: playerId});
        });

        el.bankSharesBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-share-row-action");
            if (!button) {
                return;
            }
            const playerId = String(button.dataset.playerId || "").trim();
            if (!playerId) {
                return;
            }
            if (el.bankSharePlayerInput) {
                el.bankSharePlayerInput.value = playerId;
            }
            runBankAction("SHARES_REMOVE", {playerId: playerId, arg1: playerId});
        });

        el.bankCofoundersBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-cofounder-row-action");
            if (!button) {
                return;
            }
            const playerId = String(button.dataset.playerId || "").trim();
            if (!playerId) {
                return;
            }
            if (el.bankCofounderPlayerInput) {
                el.bankCofounderPlayerInput.value = playerId;
            }
            runBankAction("COFOUNDER_REMOVE", {playerId: playerId, arg1: playerId});
        });

        el.bankEmployeesBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-employee-row-action");
            if (!button) {
                return;
            }
            const playerId = String(button.dataset.playerId || "").trim();
            if (!playerId) {
                return;
            }
            if (el.bankEmployeePlayerInput) {
                el.bankEmployeePlayerInput.value = playerId;
            }
            runBankAction("FIRE", {arg1: playerId});
        });

        el.bankProductsBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-product-row-action");
            if (!button) {
                return;
            }
            const name = String(button.dataset.name || "").trim();
            if (!name) {
                return;
            }
            if (el.bankProductNameInput) {
                el.bankProductNameInput.value = name;
            }
            runBankAction("LOAN_PRODUCT_DELETE", {name: name, arg1: name});
        });

        el.bankOffersBody.addEventListener("click", event => {
            const button = event.target.closest(".bank-offer-row-action");
            if (!button) {
                return;
            }
            const offerId = String(button.dataset.offerId || "").trim();
            if (!offerId) {
                return;
            }
            if (el.bankOfferIdInput) {
                el.bankOfferIdInput.value = offerId;
            }
            runBankAction("LEND_ACCEPT", {arg1: offerId});
        });

        el.bankRoleSaveBtn.addEventListener("click", () => runBankAction("ROLE_ASSIGN", {
            arg1: el.bankRolePlayerInput.value,
            arg2: el.bankRoleSelect.value
        }));
        el.bankRoleRevokeBtn.addEventListener("click", () => runBankAction("ROLE_REVOKE", {
            arg1: el.bankRolePlayerInput.value
        }));

        el.bankShareSetBtn.addEventListener("click", () => runBankAction("SHARES_SET", {
            arg1: el.bankSharePlayerInput.value,
            arg2: el.bankSharePercentInput.value
        }));
        el.bankShareRemoveBtn.addEventListener("click", () => runBankAction("SHARES_REMOVE", {
            playerId: el.bankSharePlayerInput.value,
            arg1: el.bankSharePlayerInput.value
        }));

        el.bankCofounderAddBtn.addEventListener("click", () => runBankAction("COFOUNDER_ADD", {
            arg1: el.bankCofounderPlayerInput.value
        }));
        el.bankCofounderRemoveBtn.addEventListener("click", () => runBankAction("COFOUNDER_REMOVE", {
            playerId: el.bankCofounderPlayerInput.value,
            arg1: el.bankCofounderPlayerInput.value
        }));

        el.bankEmployeeSaveBtn.addEventListener("click", () => runBankAction("HIRE", {
            arg1: el.bankEmployeePlayerInput.value,
            arg2: el.bankEmployeeRoleSelect.value,
            arg3: el.bankEmployeeSalaryInput.value
        }));
        el.bankEmployeeFireBtn.addEventListener("click", () => runBankAction("FIRE", {
            arg1: el.bankEmployeePlayerInput.value
        }));

        el.bankProductSaveBtn.addEventListener("click", () => runBankAction("CREATE_LOAN_PRODUCT", {
            arg1: el.bankProductNameInput.value,
            arg2: el.bankProductMaxInput.value,
            arg3: el.bankProductAprInput.value,
            arg4: el.bankProductDurationInput.value
        }));
        el.bankProductDeleteBtn.addEventListener("click", () => runBankAction("LOAN_PRODUCT_DELETE", {
            name: el.bankProductNameInput.value,
            arg1: el.bankProductNameInput.value
        }));

        el.bankOfferCreateBtn.addEventListener("click", () => runBankAction("LEND_OFFER", {
            arg1: el.bankOfferAmountInput.value,
            arg2: el.bankOfferAprInput.value,
            arg3: el.bankOfferTermInput.value
        }));
        el.bankOfferAcceptBtn.addEventListener("click", () => runBankAction("LEND_ACCEPT", {
            arg1: el.bankOfferIdInput.value
        }));

        el.bankMottoSaveBtn.addEventListener("click", () => runBankAction("SET_MOTTO", {
            arg1: el.bankMottoInput.value
        }));
        el.bankColorSaveBtn.addEventListener("click", () => runBankAction("SET_COLOR", {
            arg1: el.bankColorInput.value
        }));
        el.bankLimitSaveBtn.addEventListener("click", () => runBankAction("SET_LIMIT", {
            arg1: el.bankLimitTypeSelect.value,
            arg2: el.bankLimitAmountInput.value
        }));
        el.bankCardFeesSaveBtn.addEventListener("click", () => runBankAction("SET_CARD_FEES", {
            arg1: el.bankCardIssueFeeInput.value,
            arg2: el.bankCardReplacementFeeInput.value
        }));

        el.bankBorrowBtn.addEventListener("click", () => runBankAction("BORROW", {
            arg1: el.bankBorrowAmountInput.value
        }));
        el.bankAppealBtn.addEventListener("click", () => runBankAction("APPEAL", {
            arg1: el.bankAppealMessageInput.value
        }));
        el.bankTellerIssueBtn.addEventListener("click", () => runBankAction("TELLER_ISSUE"));
        el.bankTellerCountBtn.addEventListener("click", () => runBankAction("TELLER_COUNT"));

        if (el.shopBack) {
            el.shopBack.addEventListener("click", () => {
                location.hash = "#/shops";
            });
        }
        if (el.shopOverviewSaveBtn) {
            el.shopOverviewSaveBtn.addEventListener("click", () => runShopAction("UPDATE_OVERVIEW", {
                name: el.shopNameInput ? el.shopNameInput.value : "",
                type: el.shopTypeSelect ? el.shopTypeSelect.value : "",
                level: el.shopLevelInput ? el.shopLevelInput.value : ""
            }));
        }
        if (el.shopPermissionSetBtn) {
            el.shopPermissionSetBtn.addEventListener("click", () => runShopAction("PERMISSION_SET", {
                playerId: el.shopPermissionPlayerInput ? el.shopPermissionPlayerInput.value : "",
                role: el.shopPermissionRoleSelect ? el.shopPermissionRoleSelect.value : ""
            }));
        }
        if (el.shopPermissionRemoveBtn) {
            el.shopPermissionRemoveBtn.addEventListener("click", () => runShopAction("PERMISSION_REMOVE", {
                playerId: el.shopPermissionPlayerInput ? el.shopPermissionPlayerInput.value : ""
            }));
        }
        if (el.shopPermissionsBody) {
            el.shopPermissionsBody.addEventListener("click", event => {
                const button = event.target.closest(".shop-permission-row-action");
                if (!button) {
                    return;
                }
                const playerId = String(button.dataset.playerId || "").trim();
                if (!playerId) {
                    return;
                }
                if (el.shopPermissionPlayerInput) {
                    el.shopPermissionPlayerInput.value = playerId;
                }
                runShopAction("PERMISSION_REMOVE", {playerId: playerId});
            });
        }

        function claimPayloadFromInputs(prefix) {
            const lower = prefix === "stockroom" ? "shopStockroom" : "shopClaim";
            return {
                dimensionId: el[lower + "DimInput"] ? el[lower + "DimInput"].value : "",
                minX: el[lower + "MinXInput"] ? el[lower + "MinXInput"].value : "",
                minY: el[lower + "MinYInput"] ? el[lower + "MinYInput"].value : "",
                minZ: el[lower + "MinZInput"] ? el[lower + "MinZInput"].value : "",
                maxX: el[lower + "MaxXInput"] ? el[lower + "MaxXInput"].value : "",
                maxY: el[lower + "MaxYInput"] ? el[lower + "MaxYInput"].value : "",
                maxZ: el[lower + "MaxZInput"] ? el[lower + "MaxZInput"].value : ""
            };
        }

        if (el.shopClaimAddBtn) {
            el.shopClaimAddBtn.addEventListener("click", () => runShopAction("CLAIM_ADD", claimPayloadFromInputs("claim")));
        }
        if (el.shopClaimRemoveBtn) {
            el.shopClaimRemoveBtn.addEventListener("click", () => runShopAction("CLAIM_REMOVE", claimPayloadFromInputs("claim")));
        }
        if (el.shopStockroomAddBtn) {
            el.shopStockroomAddBtn.addEventListener("click", () => runShopAction("STOCKROOM_ADD", claimPayloadFromInputs("stockroom")));
        }
        if (el.shopStockroomRemoveBtn) {
            el.shopStockroomRemoveBtn.addEventListener("click", () => runShopAction("STOCKROOM_REMOVE", claimPayloadFromInputs("stockroom")));
        }

        function bindRegionRowRemoval(table, action) {
            if (!table) {
                return;
            }
            table.addEventListener("click", event => {
                const button = event.target.closest(".shop-region-row-action");
                if (!button) {
                    return;
                }
                runShopAction(action, {
                    dimensionId: button.dataset.dim || "",
                    minX: button.dataset.minX || "",
                    minY: button.dataset.minY || "",
                    minZ: button.dataset.minZ || "",
                    maxX: button.dataset.maxX || "",
                    maxY: button.dataset.maxY || "",
                    maxZ: button.dataset.maxZ || ""
                });
            });
        }
        bindRegionRowRemoval(el.shopClaimsBody, "CLAIM_REMOVE");
        bindRegionRowRemoval(el.shopStockroomsBody, "STOCKROOM_REMOVE");

        if (el.shopHoursSaveBtn) {
            el.shopHoursSaveBtn.addEventListener("click", () => runShopAction("SET_HOURS", {
                open: el.shopHoursOpenInput ? el.shopHoursOpenInput.value : "",
                close: el.shopHoursCloseInput ? el.shopHoursCloseInput.value : ""
            }));
        }
        if (el.shopClosedDelivererSaveBtn) {
            el.shopClosedDelivererSaveBtn.addEventListener("click", () => runShopAction("SET_CLOSED_DELIVERER_ACCESS", {
                enabled: el.shopClosedDelivererSelect ? el.shopClosedDelivererSelect.value : ""
            }));
        }
        if (el.shopLightingSaveBtn) {
            el.shopLightingSaveBtn.addEventListener("click", () => runShopAction("SET_LIGHTING", {
                enabled: el.shopLightingEnabledSelect ? el.shopLightingEnabledSelect.value : "",
                mainMode: el.shopLightingMainModeSelect ? el.shopLightingMainModeSelect.value : "",
                stockroomMode: el.shopLightingStockroomModeSelect ? el.shopLightingStockroomModeSelect.value : "",
                excludeStockroom: el.shopLightingExcludeSelect ? el.shopLightingExcludeSelect.value : "",
                lightLevel: el.shopLightingLevelInput ? el.shopLightingLevelInput.value : ""
            }));
        }
        if (el.shopSettlementAccountSaveBtn) {
            el.shopSettlementAccountSaveBtn.addEventListener("click", () => runShopAction("SET_SETTLEMENT_ACCOUNT", {
                accountId: el.shopSettlementAccountInput ? el.shopSettlementAccountInput.value : ""
            }));
        }
        if (el.shopClearCheckoutBtn) {
            el.shopClearCheckoutBtn.addEventListener("click", () => runShopAction("CLEAR_CHECKOUT_TERMINAL"));
        }
        if (el.shopClearCashierLinksBtn) {
            el.shopClearCashierLinksBtn.addEventListener("click", () => runShopAction("CLEAR_CASHIER_TERMINALS"));
        }
        if (el.shopEmployeesBody) {
            el.shopEmployeesBody.addEventListener("click", event => {
                const button = event.target.closest(".shop-employee-row-action");
                if (!button) {
                    return;
                }
                const selection = String(button.dataset.selection || "").trim();
                if (!selection) {
                    return;
                }
                if (el.shopEmployeeSelectionInput) {
                    el.shopEmployeeSelectionInput.value = selection;
                }
                runShopAction("FIRE_EMPLOYEE", {employeeId: selection});
            });
        }
        if (el.shopEmployeeFireBtn) {
            el.shopEmployeeFireBtn.addEventListener("click", () => runShopAction("FIRE_EMPLOYEE", {
                employeeId: el.shopEmployeeSelectionInput ? el.shopEmployeeSelectionInput.value : ""
            }));
        }
        if (el.shopDeleteBtn) {
            el.shopDeleteBtn.addEventListener("click", () => runShopAction("DELETE_SHOP", {
                confirmName: el.shopDeleteConfirmInput ? el.shopDeleteConfirmInput.value : ""
            }));
        }

        el.cardsPrev.addEventListener("click", () => scrollCardCarousel(-1));
        el.cardsNext.addEventListener("click", () => scrollCardCarousel(1));
        el.playerCardsTrack.addEventListener("click", event => {
            const button = event.target.closest("[data-card-action]");
            if (!button) {
                return;
            }
            const cardNode = button.closest(".credit-card");
            if (!cardNode) {
                return;
            }
            performCardAction(button.dataset.cardAction, {
                cardId: cardNode.dataset.cardId || "",
                accountId: cardNode.dataset.accountId || "",
                holderName: cardNode.dataset.holderName || "",
                maskedNumber: cardNode.dataset.maskedNumber || ""
            });
        });

        el.playerAccountsBody.addEventListener("click", event => {
            const button = event.target.closest(".account-open-btn");
            if (!button) {
                return;
            }
            const accountId = String(button.dataset.accountId || "").trim();
            const playerId = String((((state.playerDetail || {}).profile || {}).playerId) || "").trim();
            if (!accountId || !playerId) {
                return;
            }
            location.hash = "#/players/" + encodeURIComponent(playerId) + "/accounts/" + encodeURIComponent(accountId);
        });

        el.playerBack.addEventListener("click", () => {
            location.hash = "#/users";
        });

        el.accountBack.addEventListener("click", () => {
            const playerId = String(el.accountBack.dataset.playerId || "").trim();
            if (playerId) {
                location.hash = "#/players/" + encodeURIComponent(playerId);
                return;
            }
            location.hash = "#/users";
        });
        el.accountOpenPlayer.addEventListener("click", () => {
            const playerId = String(el.accountOpenPlayer.dataset.playerId || "").trim();
            if (!playerId) {
                location.hash = "#/users";
                return;
            }
            location.hash = "#/players/" + encodeURIComponent(playerId);
        });

        el.accountRolesBody.addEventListener("click", event => {
            const button = event.target.closest(".role-row-action");
            if (!button) {
                return;
            }
            const playerId = String(button.dataset.playerId || "").trim();
            if (!playerId) {
                return;
            }
            if (el.accountRolePlayerIdInput) {
                el.accountRolePlayerIdInput.value = playerId;
            }
            runAccountAction("REVOKE_ACCESS_ROLE", {playerId: playerId});
        });

        el.accountSafeboxBody.addEventListener("click", event => {
            const button = event.target.closest(".safebox-row-action");
            if (!button) {
                return;
            }
            const slot = String(button.dataset.slot || "").trim();
            const action = String(button.dataset.safeboxAction || "").trim().toLowerCase();
            if (!slot || !action) {
                return;
            }
            if (el.accountSafeboxSlotInput) {
                el.accountSafeboxSlotInput.value = slot;
            }
            if (action === "deliver") {
                runAccountAction("SAFEBOX_DELIVER_SLOT", {slot: slot});
                return;
            }
            if (action === "delete") {
                runAccountAction("SAFEBOX_DELETE_SLOT", {slot: slot});
            }
        });

        el.accountCardsBody.addEventListener("click", event => {
            const button = event.target.closest(".card-row-action");
            if (!button) {
                return;
            }
            const cardId = String(button.dataset.cardId || "").trim();
            const action = String(button.dataset.cardAction || "").trim().toLowerCase();
            if (!cardId || !action) {
                return;
            }
            if (action === "add") {
                runAccountAction("CARD_ADD_TO_INVENTORY", {cardId: cardId});
                return;
            }
            if (action === "replace") {
                runAccountAction("CARD_REPLACE", {
                    cardId: cardId,
                    holderName: el.accountReplacementHolderInput ? el.accountReplacementHolderInput.value : ""
                });
                return;
            }
            if (action === "delete") {
                runAccountAction("CARD_DELETE", {cardId: cardId});
                return;
            }
            if (action === "block") {
                runAccountAction("CARD_BLOCK", {cardId: cardId});
                return;
            }
            if (action === "unblock") {
                runAccountAction("CARD_UNBLOCK", {cardId: cardId});
            }
        });

        el.accountLoansBody.addEventListener("click", event => {
            const button = event.target.closest(".loan-row-action");
            if (!button) {
                return;
            }
            const loanId = String(button.dataset.loanId || "").trim();
            const action = String(button.dataset.loanAction || "").trim().toLowerCase();
            if (!loanId || !action) {
                return;
            }
            if (el.accountLoanIdInput) {
                el.accountLoanIdInput.value = loanId;
            }
            if (action === "delete") {
                runAccountAction("LOAN_DELETE", {loanId: loanId});
                return;
            }
            if (action === "toggle-default") {
                const currentlyDefaulted = String(button.dataset.defaulted || "").toLowerCase() === "true";
                const next = (!currentlyDefaulted).toString();
                if (el.accountLoanDefaultedSelect) {
                    el.accountLoanDefaultedSelect.value = next;
                }
                runAccountAction("LOAN_SET_DEFAULTED", {
                    loanId: loanId,
                    defaulted: next
                });
            }
        });

        el.accountFreezeBtn.addEventListener("click", () => runAccountAction("FREEZE", {
            reason: el.accountFreezeReason.value
        }));
        el.accountUnfreezeBtn.addEventListener("click", () => runAccountAction("UNFREEZE"));
        el.accountSetPrimaryBtn.addEventListener("click", () => runAccountAction("SET_PRIMARY"));

        el.accountDepositBtn.addEventListener("click", () => runAccountAction("FORCE_DEPOSIT", {
            amount: el.accountAmountInput.value
        }));
        el.accountWithdrawBtn.addEventListener("click", () => runAccountAction("FORCE_WITHDRAW", {
            amount: el.accountAmountInput.value
        }));

        el.accountCreditSetBtn.addEventListener("click", () => runAccountAction("SET_CREDIT_SCORE", {
            creditScore: el.accountCreditInput.value
        }));
        el.accountCreditAdjustBtn.addEventListener("click", () => runAccountAction("ADJUST_CREDIT_SCORE", {
            delta: el.accountCreditDeltaInput.value
        }));

        el.accountDefaultedSetBtn.addEventListener("click", () => runAccountAction("SET_DEFAULTED", {
            defaulted: el.accountDefaultedSelect.value
        }));
        el.accountPinSetBtn.addEventListener("click", () => runAccountAction("SET_PIN", {
            pin: el.accountPinInput.value
        }));

        el.accountAccessSaveBtn.addEventListener("click", () => runAccountAction("SET_ACCESS_TYPE", {
            accessType: el.accountAccessTypeSelect.value
        }).then(result => {
            if (!result || !result.ok) {
                return result;
            }
            return runAccountAction("SET_BUSINESS_LABEL", {
                businessLabel: el.accountBusinessLabelInput.value
            });
        }));

        el.accountTempLimitSetBtn.addEventListener("click", () => runAccountAction("SET_TEMP_WITHDRAWAL_LIMIT", {
            amount: el.accountTempLimitInput.value
        }));
        el.accountTempLimitClearBtn.addEventListener("click", () => runAccountAction("CLEAR_TEMP_WITHDRAWAL_LIMIT"));
        el.accountDailyClearBtn.addEventListener("click", () => runAccountAction("CLEAR_DAILY_WITHDRAWN"));

        el.accountRoleGrantBtn.addEventListener("click", () => runAccountAction("GRANT_ACCESS_ROLE", {
            playerId: el.accountRolePlayerIdInput.value,
            role: el.accountRoleSelect.value
        }));
        el.accountRoleRevokeBtn.addEventListener("click", () => runAccountAction("REVOKE_ACCESS_ROLE", {
            playerId: el.accountRolePlayerIdInput.value
        }));

        el.accountSafeboxAddBtn.addEventListener("click", () => runAccountAction("SAFEBOX_ADD_ITEM", {
            slot: el.accountSafeboxSlotInput.value,
            itemId: el.accountSafeboxItemInput.value,
            count: el.accountSafeboxCountInput.value
        }));
        el.accountSafeboxDeliverBtn.addEventListener("click", () => runAccountAction("SAFEBOX_DELIVER_SLOT", {
            slot: el.accountSafeboxSlotInput.value
        }));
        el.accountSafeboxDeleteBtn.addEventListener("click", () => runAccountAction("SAFEBOX_DELETE_SLOT", {
            slot: el.accountSafeboxSlotInput.value
        }));

        el.accountCreateCardBtn.addEventListener("click", () => runAccountAction("ISSUE_NEW_CARD", {
            holderName: el.accountReplacementHolderInput.value
        }));
        el.accountBlockCardsBtn.addEventListener("click", () => runAccountAction("BLOCK_ACCOUNT_CARDS"));

        el.accountLoanDefaultedBtn.addEventListener("click", () => runAccountAction("LOAN_SET_DEFAULTED", {
            loanId: el.accountLoanIdInput.value,
            defaulted: el.accountLoanDefaultedSelect.value
        }));
        el.accountLoanRemainingBtn.addEventListener("click", () => runAccountAction("LOAN_SET_REMAINING", {
            loanId: el.accountLoanIdInput.value,
            remaining: el.accountLoanRemainingInput.value
        }));
        el.accountLoanDeleteBtn.addEventListener("click", () => runAccountAction("LOAN_DELETE", {
            loanId: el.accountLoanIdInput.value
        }));

        el.accountDeleteBtn.addEventListener("click", () => runAccountAction("DELETE_ACCOUNT", {
            confirm: el.accountDeleteConfirmInput.value
        }));

        el.runCommand.addEventListener("click", () => runServerCommand());
        el.webStatusCmd.addEventListener("click", () => runServerCommand("ubs web status"));
        el.webOnCmd.addEventListener("click", () => runServerCommand("ubs web on"));
        el.webOffCmd.addEventListener("click", () => runServerCommand("ubs web off"));
        el.webLinkCmd.addEventListener("click", () => runServerCommand("ubs web link"));
    }

    function startTimers() {
        if (state.healthTimer) {
            clearInterval(state.healthTimer);
        }
        if (state.refreshTimer) {
            clearInterval(state.refreshTimer);
        }
        state.healthTimer = setInterval(() => {
            refreshHealth();
        }, 6000);
        state.refreshTimer = setInterval(() => {
            refreshCurrentPage(parseRoute());
        }, 15000);
    }

    bindEvents();
    connectWebSocket();
    startTimers();
    handleRoute();
})();
