# Developer API

UBS exposes a server-side Java API for NeoForge `1.21.1`. API version `2.0.0` adds management surfaces for banks, shops, heists, and general server discovery while retaining the existing finance, cash, notification, and market-price methods.

## Entry Points

```java
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

var finance = UltimateBankingApiProvider.get();
var server = UltimateBankingApiProvider.server();
var banks = UltimateBankingApiProvider.banks();
var shops = UltimateBankingApiProvider.shops();
var heists = UltimateBankingApiProvider.heists();
```

`finance.getApiVersion()` returns `2.0.0`.

## Contract and Threading

- Read models are records whose collection fields use defensive immutable copies.
- API methods return empty/failed results when UBS world data is unavailable; they do not expose mutable `Bank`, `CompoundTag`, or internal service objects.
- Mutations must run on the logical Minecraft server thread.
- Mutations reuse UBS authorization and validation. An addon does not bypass ownership, role, cooldown, capacity, setup, or heist rules.
- Operations that require an acting player may require that player to be online.
- `getTargets()` performs live heist/world eligibility scans and therefore returns no targets when called off the server thread.
- Monetary values use `BigDecimal`, dollars, or cents as stated by the method/record. Do not infer units from formatting.
- The API is an in-process Java surface. UBS does not ship an HTTP/WebSocket admin server.

## Result Types

- `ApiResult`: simple finance result and resulting balance.
- `ApiTransactionResult`: success/reason, transaction ID, source/destination IDs, amount, resulting balances, and reference.
- `ApiManagementResult`: `success` plus a human-readable message for bank/shop/heist actions.
- `ApiCashResult`: physical cash inventory operation result.
- `ApiItemResult`: issued paper instrument and reference ID.
- `ApiNotificationResult` and `ApiAlertResult`: UI-delivery outcomes.

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

Account transaction lists are bounded by the requested limit and by the server's retained history (`AccountTransactionLogLimit`, default `20`).

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

Feature flags: `BANKING`, `SHOPS`, `HEISTS`, `SMARTPHONE`, `SAFETY_DEPOSIT_BOXES`, `RFID_ACCESS`, `PHYSICAL_CURRENCY`, `WALLET`, `OWNER_PC`.

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

`resolvePlaceholder`, `resolvePlaceholders`, and `getSupportedPlaceholders` support player balances/accounts and bank identity/status/reserve/deposit values. Raw variants return decimal strings for sorting; formatted variants use UBS money formatting.

## Compatibility

- Check `getApiVersion()` before using a surface introduced after your minimum version.
- Declare UBS as a required or optional NeoForge dependency; never shade UBS into your jar.
- Keep optional integration classes isolated until `ModList.get().isLoaded("ultimatebankingsystem")` is true.
- Do not retain internal UBS objects or mutate NBT directly. Use IDs, snapshots, and API methods.

