# Developer API

UBS exposes server-side Java interfaces for NeoForge `1.21.1`. Version `2.1.1` is the current API release and includes the authoritative economy module used by the Ages of War web gateway: institutional accounts, explicit grants, reconciliation snapshots, durable idempotent operations, revisions, and monetary escrow. The `2.0.0` finance, cash, notification, market-price, bank, shop, heist, and discovery interfaces remain available.

## Entry Points

```java
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

var finance = UltimateBankingApiProvider.get();
var server = UltimateBankingApiProvider.server();
var banks = UltimateBankingApiProvider.banks();
var shops = UltimateBankingApiProvider.shops();
var heists = UltimateBankingApiProvider.heists();
var economy = UltimateBankingApiProvider.economy();
```

`finance.getApiVersion()` and `economy.getApiVersion()` return `2.1.1`.

## Contract and Threading

- Read models are records whose collection fields use defensive immutable copies.
- API methods return empty/failed results when UBS world data is unavailable; they do not expose mutable `Bank`, `CompoundTag`, or internal service objects.
- Mutations must run on the logical Minecraft server thread.
- Mutations reuse UBS authorization and validation. An addon does not bypass ownership, role, cooldown, capacity, setup, or heist rules.
- Operations that require an acting player may require that player to be online.
- `getTargets()` performs live heist/world eligibility scans and therefore returns no targets when called off the server thread.
- Monetary values use `BigDecimal`, dollars, or cents as stated by the method/record. Economy mutation amounts must be positive dollar values with no more than two decimal places. Snapshot amounts are normalized to two decimal places with half-even rounding for legacy data.
- The API is an in-process Java surface. UBS does not ship an HTTP/WebSocket admin server.
- `OFFICIAL_SYSTEM` is a trusted in-process authority intended for the appointed official-server adapter. Do not expose it directly to browser input, commands, or an untrusted addon.

## Result Types

- `ApiResult`: simple finance result and resulting balance.
- `ApiTransactionResult`: success/reason, transaction ID, source/destination IDs, amount, resulting balances, and reference.
- `ApiManagementResult`: `success` plus a human-readable message for bank/shop/heist actions.
- `ApiCashResult`: physical cash inventory operation result.
- `ApiItemResult`: issued paper instrument and reference ID.
- `ApiNotificationResult` and `ApiAlertResult`: UI-delivery outcomes.
- `ApiEconomyOperationResult`: durable authoritative receipt with status/code, stable operation ID, duplicate flag, economy revision, transaction IDs, affected accounts, and optional escrow state.

## Authoritative Economy Module (2.1.1)

`UltimateBankingApiProvider.economy()` is the integration seam for remote projections and commands. Its interface deliberately has four methods:

```java
String getApiVersion();
ApiEconomySnapshot snapshot(ApiEconomySnapshotRequest request);
Optional<ApiEconomyOperationResult> findOperation(String idempotencyKey);
ApiEconomyOperationResult execute(ApiEconomyOperationRequest request);
```

All calls belong on the logical server thread. The module owns validation, authorization, atomic balance movement, limits, deterministic transaction IDs, operation receipts, and persistence. Network code should translate a verified command into one request, execute it once, and serialize the returned result; it must not reproduce banking rules.

### Reconciliation snapshots

- `ApiEconomySnapshotRequest.forPlayer(playerId)` returns accounts visible to that player, their effective role/capabilities, retained statements for the preceding year, and escrow summaries.
- `ApiEconomySnapshotRequest.reconciliation()` returns every account, access grant, retained transaction, and escrow for a trusted official adapter.
- `revision` is monotonic for economy mutations. A projection that observes a gap must request reconciliation rather than inventing the missing state.
- `PLAYER` principals represent human-owned accounts. `INSTITUTION` principals represent durable non-player owners such as `nation:<id>` and `escrow:<id>`.
- An accessible institutional account is not part of a player's owned wealth. Use `principalType`, `principalId`, and `playerId`; do not infer ownership from the grants map.
- Account grants use `VIEW`, `DEPOSIT`, `WITHDRAW`, and `MANAGE`. `OWNER` is reserved for the principal of a personal account.

Retained transaction statements are bounded by `AccountTransactionLogLimit` (default `10,000`, configurable up to `50,000`) and the requested limit. The snapshot's time filter cannot recover entries already pruned by server retention.

### Idempotent operations

Every `execute` request requires a stable 8–160 character idempotency key containing only safe ASCII characters. Replaying the same key and exact payload returns the original receipt with `duplicate=true` and performs no second mutation. Reusing a key with different content fails with `IDEMPOTENCY_CONFLICT`. Completed receipts survive server restarts; keep command keys stable across network retries.

Supported operation types:

- `TRANSFER`, `TRANSFER_TO_PRIMARY`, `SET_PRIMARY_ACCOUNT`
- `PROVISION_INSTITUTION_ACCOUNT`, `SET_ACCESS_ROLE`, `SET_ACCOUNT_FROZEN`
- `ADMIN_DEPOSIT`, `ADMIN_WITHDRAW`
- `CREATE_ESCROW`, `FUND_ESCROW`, `RELEASE_ESCROW`, `REFUND_ESCROW`

`PLAYER` transfers enforce the acting player's current account grant. Personal transfers are limited to `$10,000.00` each and `$25,000.00` over a rolling 24 hours. Institutional nation payouts derive officer/leader limits from the current `WITHDRAW` or `MANAGE` grant. Primary-account selection only accepts a player-owned personal account.

Provisioning and account freezing, administrative adjustments, and escrow lifecycle operations require `OFFICIAL_SYSTEM`. Administrative adjustments require an exact account, positive amount, and non-empty audit reason. Approval separation belongs in the official workflow before that trusted request reaches UBS.

### Institutional account example

```java
var request = new ApiEconomyOperationRequest(
        "nation:provision:" + nationId,
        ApiEconomyOperationType.PROVISION_INSTITUTION_ACCOUNT,
        ApiEconomyActorType.OFFICIAL_SYSTEM,
        null,
        null,
        null,
        bankId,
        founderPlayerId,
        "nation:" + nationId,
        "",
        ApiAccountRole.MANAGE.name(),
        BigDecimal.ZERO,
        "Provision nation treasury",
        Map.of("label", nationName + " Treasury"),
        List.of()
);

ApiEconomyOperationResult receipt = economy.execute(request);
if (!receipt.success()) {
    // Keep the website command failed/pending; never update a projected balance locally.
}
```

### Matched escrow

Create an escrow in a real bank, then fund it with exactly two `ApiEconomyTransferLeg` entries of equal value. Each side may contribute at most `$25,000.00`. Funding prevalidates the complete movement before changing any balance. A release must consume the complete holding balance; a refund returns the recorded contributions. Terminal escrow operations are idempotent through their command keys.

## Finance API

`UltimateBankingApiProvider.get()` exposes:

### Money operations

```java
ApiResult getBalance(UUID accountId);
ApiTransactionResult deposit(UUID accountId, BigDecimal amount, String reference);
ApiTransactionResult withdraw(UUID accountId, BigDecimal amount, String reference);
ApiTransactionResult transfer(UUID from, UUID to, BigDecimal amount, String reference);
ApiTransactionResult depositToPrimary(UUID playerId, BigDecimal amount, String reference);
ApiTransactionResult withdrawFromPrimary(UUID playerId, BigDecimal amount, String reference);
ApiTransactionResult transferFromPrimary(UUID playerId, UUID to, BigDecimal amount, String reference);
ApiTransactionResult transferToPrimary(UUID from, UUID playerId, BigDecimal amount, String reference);
```

Whole-dollar `long` overloads and legacy `ApiResult` deposit/withdraw/transfer overloads remain available.

### Validation

- `accountExists`, `bankExists`, `shopExists`
- `accountCanSend`, `accountCanReceive`
- `primaryAccountCanSend`, `primaryAccountCanReceive`
- `validateAccountCanSend`, `validateAccountCanReceive`, `validateAccountCanInteract`
- `accountBelongsToBank`, `accountIsFrozen`, `accountIsPrimary`
- `bankAcceptsTransactions`

Call validation immediately before an operation only as UX assistance; the operation still performs authoritative validation.

### Account and bank snapshots

- `getAccountSnapshot`, `getPrimaryAccountSnapshot`
- `getPlayerAccounts`, `getPlayerAccountIds`, `getBankAccounts`
- `getBankSnapshot`, `getBanks`
- `getTransactionSnapshot`, `getAccountTransactions`, `getPlayerTransactions`
- `setPrimaryAccount`

Account transaction lists are bounded by the requested limit and by the server's retained history (`AccountTransactionLogLimit`, default `10,000`).

### Ownership and aggregate helpers

- `playerOwnsAccount`, `playerOwnsBank`, `playerOwnsAnyBank`
- `getPlayerOwnedBanks`
- `playerOwnsShop`, `playerOwnsAnyShop`, `getPlayerOwnedShopIds`
- `playerHasAnyAccount`, `playerHasPrimaryAccount`
- `playerHasAvailableAccount`, `playerHasAvailablePrimaryAccount`, `playerHasFrozenAccount`
- `getPlayerTotalBalance`, `getPlayerPrimaryBalance`, `getPlayerAccountCount`
- `getBankTotalDeposits`, `getBankReserve`, `getBankStatus`

### Merchant and instruments

- `shopPurchase` with payer, merchant, shop name, and reference
- `issueBankNote`
- `issueCheque`
- `giveDollarBills`, `takeDollarBills`
- `giveCoins`, `takeCoins`
- supported denomination, stack-creation, count, and cash-on-hand helpers

Bill denominations are dollars: `1, 2, 5, 10, 20, 50, 100`. Coin denominations are cents: `1, 5, 10, 25, 50`.

## Notification API

New integrations should use `sendNotification(playerId, request)`.

```java
var request = ApiNotificationRequest.transaction("Payment settled")
        .id("auction:settlement:" + orderId)
        .channel("auction_house")
        .source("Ultimate Auction System")
        .title("Settlement complete")
        .detail("The seller account was credited.")
        .priority(ApiNotificationPriority.NORMAL)
        .durationMs(5500)
        .build();

ApiNotificationResult result = finance.sendNotification(playerId, request);
```

Types: `SUCCESS`, `ERROR`, `WARNING`, `INFO`, `TRANSACTION`, `SECURITY`, `MESSAGE`, `PROGRESS`, `SYSTEM`.

Priorities: `LOW`, `NORMAL`, `HIGH`, `CRITICAL`.

Placements: `AUTO`, `TOP_RIGHT`, `TOP_CENTER`, `BOTTOM_RIGHT`.

Use a stable request ID and `replaceExisting=true` to update progress/state in place. Use `dismissNotification`, `clearNotificationChannel`, or `clearNotifications` for cleanup. Offline targets are not silently queued.

The original `sendUiAlert` overloads and `ApiAlertTone` remain binary-compatible for older addons.

## Market Price API

```java
ApiShopPriceStatistics stats = finance.getItemShopPriceStatistics(
        stack,
        ApiShopPriceScope.REGULAR
);
```

Scopes:

- `REGULAR`: registered, setup-complete, currently open shops; creative displays excluded
- `INCLUDE_ALL`: every indexed shop display, including creative/unregistered displays
- `ALL_SHELVES_EXCLUDE_CREATIVE`: every indexed non-creative display
- `CREATIVE_ONLY`: creative displays only

The result exposes availability, item ID, sample count, and median/average/minimum/maximum price in cents. Convenience methods exist for every scope.

## General Server API

`UltimateBankingApiProvider.server()` provides:

- `isAvailable`
- `getSnapshot`
- `getAvailableFeatures`, `isFeatureAvailable`
- `getOnlinePlayerIds`
- `getPlayerPortfolio`

`ApiServerSnapshot` contains API version, online-player count, bank/account/shop counts, active-heist count, and feature flags.

Feature flags: `BANKING`, `SHOPS`, `HEISTS`, `SMARTPHONE`, `SAFETY_DEPOSIT_BOXES`, `RFID_ACCESS`, `PHYSICAL_CURRENCY`, `WALLET`, `OWNER_PC`, `INSTITUTIONAL_ECONOMY`, `IDEMPOTENT_OPERATIONS`, `MONETARY_ESCROW`.

`ApiPlayerPortfolioSnapshot` contains account count, primary account, total balance, owned/accessed bank IDs, owned/accessed shop IDs, and current heist session ID.

## Bank Management API

`UltimateBankingApiProvider.banks()` provides:

### Reads

- `getBanks`, `getBank`, `findBank`
- `getOwnedBanks`, `getAccessibleBanks`
- `playerOwnsBank`, `playerOwnsAnyBank`
- `playerCanAccessBank`
- `playerCanManageSafeArea`, `playerCanAccessProtectedSafeArea`
- `isBankUnderAttack`
- `getStaffing`
- `getSafeDepositSetup`

`ApiBankManagementSnapshot` includes identity/owner/status, central-bank flag, account count, deposits, reserve requirements/ratio, outstanding loans, lendable amount, rate, premise/safe/vault/readiness counts, staff/teller counts, and attack state.

`ApiBankStaffingSnapshot` contains immutable employee and teller records. Employees include role, salary, online state, and Safe Access. Tellers include entity identity, variant, position, active state, and bank-bound state.

`ApiSafeDepositSetupSnapshot` contains enabled/readiness counts, human-readable blockers, and premises. Each `ApiBankPremiseSnapshot` provides bounds, exit/facing, access mode, safe/vault counts, and ready-vault count.

### Mutations

```java
ApiManagementResult setEmployeeSafeAccess(
        UUID actorId, UUID bankId, UUID employeeId, boolean allowed);

ApiManagementResult setInterestRate(
        UUID actorId, UUID bankId, double annualPercent);
```

The actor must have the same permission the in-game management action requires.

## Shop Management API

`UltimateBankingApiProvider.shops()` provides:

### Discovery and ownership

- `getShops`, `getShop`, `findShop`
- `getOwnedShops`, `getAccessibleShops`
- `shopExists`, `playerOwnsShop`, `playerOwnsAnyShop`
- `getPlayerRole`, `playerCanManageShop`, `playerCanBuildInShop`

### State and limits

- `isShopSetupComplete`
- `isShopCurrentlyOpen`
- `getMaximumShopsPerOwner`
- `getSupportedShopTypes`
- `getSupportedParticipantRoles`

`ApiShopManagementSnapshot` includes owner/name/type/display type, level/revenue/next target, used/capacity claim volume, plot and stockroom counts, setup/open state, display/cashier/order-pallet limits, and participants. Each participant includes role plus computed management/build permissions.

### Mutations

```java
createShop(ownerId, name, type);
renameShop(ownerId, shopId, newName);
setShopType(ownerId, shopId, type);
setOpeningHours(ownerId, shopId, schedule);
setParticipantRole(ownerId, shopId, playerId, role);
removeParticipant(ownerId, shopId, playerId);
deleteShop(ownerId, shopId, confirmationName);
```

The schedule accepts the same payload as the Owner PC, for example `ALL|09:00|21:00` or `MON|9:00 AM|5:30 PM`. Roles are the values returned by `getSupportedParticipantRoles`.

## Heist API

`UltimateBankingApiProvider.heists()` provides:

### State

- `getSessions`, `getActiveSessions`, `getSession`, `getPlayerSession`
- `getTargets`, `getTarget`
- `isPlayerInHeist`, `isPlayerInActiveHeist`, `isBankUnderAttack`
- player/bank cooldown and victim-protection remaining milliseconds
- effective maximum crew size, countdown ticks, and duration ticks

`ApiHeistSessionSnapshot` includes phase/timestamps/deadline, target bounds/exit, loot/alarm state, members, active drills/hacks, completed hacks, breached targets, and cancel votes.

`ApiHeistTargetSnapshot` includes premise bounds/exit, owner-PC/vault-door positions, eligibility/blockers, physical-loot source count, and bank cooldown.

### Planning actions

```java
createPlanningSession(playerId);
invite(leaderId, playerName);
respondToInvite(playerId, accepted);
leave(playerId);
selectTarget(leaderId, bankId, premiseId);
setReady(playerId, ready);
startCountdown(leaderId);
cancelCountdown(leaderId);
abandon(playerId);
```

Actors must be online and calls must run on the server thread.

### Heist extension points

- Listen for `HeistLifecycleEvent` stages: `STARTED`, `ALARMED`, `SUCCEEDED`, `FAILED`.
- Register `HeistDoorAdapter` with `HeistDoorAdapterRegistry` to breach/restore modded doors.
- Register `HeistLootValueProvider` with `HeistLootValueRegistry` to value custom items.
- Unregister adapters/providers when your integration unloads where applicable.

Registry implementations isolate provider exceptions so one addon does not break the heist loop.

## Geometry Records

`ApiBlockPosition` stores dimension and integer coordinates.

`ApiBlockBounds` normalizes min/max coordinates and exposes `volume()` and `contains(position)`. Use these API records instead of depending on internal claim classes.

## Placeholders

UBS resolves placeholders live from the current server state. Values are not cached, so account balances, ownership, account status, and shop/bank counts reflect the result of the most recent completed server-side operation.

The public resolver is available through `UltimateBankingApiProvider.get()`:

```java
String value = UltimateBankingApiProvider.get().resolvePlaceholder(player.getUUID(), "%ubs_primary_balance_raw%");
String text = UltimateBankingApiProvider.get().resolvePlaceholders(player.getUUID(),
        "Balance: %ubs_primary_balance% | Bank: {ubs_primary_bank_name}");
```

The resolver accepts both PAPI-style `%ubs_<key>%` and NeoEssentials-style `{ubs_<key>}` syntax. Existing `ubs_player_*` and bank UUID placeholder names remain supported. Unknown placeholders resolve to an empty value through the direct API and remain unchanged when embedded in text.

### Canonical player placeholders

| Placeholder | Meaning |
|---|---|
| `{ubs_balance}` / `{ubs_balance_raw}` | Primary account balance, formatted or plain decimal |
| `{ubs_total_balance}` / `{ubs_total_balance_raw}` | Sum of all player accounts |
| `{ubs_primary_account_id}` | Primary account UUID |
| `{ubs_primary_account_type}` | Display label for the primary account type |
| `{ubs_primary_bank_id}` / `{ubs_primary_bank_name}` | Primary account's bank |
| `{ubs_account_count}` | Number of accounts owned by the player |
| `{ubs_has_account}` / `{ubs_has_primary_account}` | `true` or `false` |
| `{ubs_cash_balance}` / `{ubs_cash_balance_raw}` | Total UBS physical tender currently carried by the online player, including loose bills, coins, money stacks, cash stored in wallets, and tender inside backpacks or other containers exposing the standard NeoForge item-handler capability |
| `{ubs_account_status}` | Primary account status, or `NONE` |
| `{ubs_owned_bank_count}` / `{ubs_owns_bank}` | Bank ownership summary |
| `{ubs_owned_shop_count}` / `{ubs_owns_shop}` | Shop ownership summary |
| `{ubs_player_name}` | Current online player name |

`raw` values are decimal strings suitable for sorting or machine processing. Formatted money values use the configured UBS currency symbol and display abbreviation.

### Server placeholders

`{ubs_server_bank_count}` and `{ubs_server_shop_count}` expose the current number of registered banks and shops. These are available only when the UBS server data is loaded.

### NeoEssentials

When NeoEssentials is installed, UBS detects its placeholder API at server start and registers the canonical keys as individual live providers. This avoids a hard dependency and keeps UBS loadable without NeoEssentials. NeoEssentials consumers can therefore use `{ubs_primary_balance}`, `{ubs_primary_account_id}`, and the other canonical keys directly.

#### NeoEssentials leaderboards

When NeoEssentials is installed, UBS also registers these live boards through `com.zerog.neoessentials.leaderboard.LeaderboardAPI`:

| Board ID | Ranking |
| --- | --- |
| `ubs_player_balance` | Total player account balances |
| `ubs_player_accounts` | Number of player accounts |
| `ubs_player_businesses` | Owned shops plus owned custom banks |
| `ubs_shop_revenue` | Best shop revenue per shop operator |
| `ubs_shop_level` | Highest shop level per shop operator |
| `ubs_shop_claims` | Total claimed shop regions per shop operator |
| `ubs_bank_deposits` | Customer deposits per bank operator |
| `ubs_bank_accounts` | Customer account count per bank operator |
| `ubs_bank_reserves` | Declared reserves per bank operator |

The boards use NeoEssentials' standard `{leaderboard_<board_id>:<rank>:name}` and `{leaderboard_<board_id>:<rank>:value}` placeholders and are available to `/leaderboard`, holograms, tablists, and other NeoEssentials consumers. Values are read from UBS data when NeoEssentials refreshes its leaderboard cache; UBS does not create demo rows or placeholder values.

NeoEssentials currently accepts only `UUID -> Number` leaderboard providers and resolves names from player profiles. Consequently, shop and bank boards are operator-backed: a player with multiple shops or banks appears once, with shop revenue/level/claims using the best or aggregate operator metric described above. This preserves correct player names and compatibility with the current NeoEssentials API. A future NeoEssentials named-entity provider can expose individual shop and bank names without changing the UBS metric definitions.

Example tablist lines:

```text
Top player: {leaderboard_ubs_player_balance:1:name} {leaderboard_ubs_player_balance:1:value}
Top shop operator: {leaderboard_ubs_shop_revenue:1:name} {leaderboard_ubs_shop_revenue:1:value}
Top bank operator: {leaderboard_ubs_bank_deposits:1:name} {leaderboard_ubs_bank_deposits:1:value}
```

The registrations are optional and reflection-based, so UBS remains loadable when NeoEssentials is absent. They are installed again on each server start, matching NeoEssentials' in-memory registration lifecycle.

### PAPI compatibility

The canonical names deliberately follow the PlaceholderAPI expansion convention: `%ubs_primary_balance%`, `%ubs_balance_raw%`, and so on. A pure NeoForge server cannot load Bukkit's PlaceholderAPI expansion classes, so UBS does not bundle or shade PAPI. Hybrid servers or a PAPI bridge should delegate the expansion's `onRequest` parameter to `net.austizz.ultimatebankingsystem.api.placeholder.PapiPlaceholderBridge.resolve(UUID, String)`; text consumers can delegate to `resolveText`. This keeps PAPI optional while preserving the standard `%ubs_<key>%` contract.

### Full legacy list

`getSupportedPlaceholders()` returns both the legacy `%ubs_player_*%`/bank UUID forms and the canonical brace forms. Check this list instead of hardcoding a version-specific set.

## Compatibility

- Check `getApiVersion()` before using a surface introduced after your minimum version.
- Declare UBS as a required or optional NeoForge dependency; never shade UBS into your jar.
- Keep optional integration classes isolated until `ModList.get().isLoaded("ultimatebankingsystem")` is true.
- Do not retain internal UBS objects or mutate NBT directly. Use IDs, snapshots, and API methods.
