# Developer API & Placeholders

This page describes UBS API access for other mods/plugins and the built-in placeholder resolver.

API baseline in this release: `1.2.2`

Need implementation guidance? Start with the [Developer Integration Tutorial](Developer-Integration-Tutorial.md).

## Java API Entry Point

Use:

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
```

## Core API Operations

Banking actions:

- `getBalance(accountId)`
- `deposit(accountId, amount)`
- `deposit(accountId, amount, reference)`
- `withdraw(accountId, amount)`
- `withdraw(accountId, amount, reference)`
- `transfer(senderAccountId, receiverAccountId, amount)`
- `transfer(senderAccountId, receiverAccountId, amount, reference)`
- `depositToPrimary(playerId, amount, reference)`
- `withdrawFromPrimary(playerId, amount, reference)`
- `transferFromPrimary(senderPlayerId, receiverAccountId, amount, reference)`
- `transferToPrimary(senderAccountId, receiverPlayerId, amount, reference)`
- `shopPurchase(accountId, amount, shopName)`
- `shopPurchase(payerAccountId, merchantAccountId, amount, shopName, reference)`
- `issueBankNote(sourceAccountId, amountDollars, issuerPlayerId, issuerName)`
- `issueCheque(sourceAccountId, recipientPlayerId, amountDollars, writerPlayerId, writerName, recipientName)`
- `giveDollarBills(playerId, denomination, billCount)`
- `takeDollarBills(playerId, denomination, billCount)`
- `giveCoins(playerId, denominationCents, coinCount)`
- `takeCoins(playerId, denominationCents, coinCount)`

`amount` can be a `long` for whole-dollar style integrations. Reference-aware deposit, withdraw, transfer, and primary-account helpers also accept `BigDecimal` for precise prices such as auction bids with cents.

Service/runtime checks:

- `getApiVersion()`
- `isServerAvailable()`
- `getPrimaryAccountId(playerId)`
- `accountExists(accountId)`
- `bankExists(bankId)`
- `getAccountStatus(accountId)`
- `validateAccountCanSend(accountId, amount)`
- `validateAccountCanReceive(accountId)`
- `sendUiAlert(playerId, title, message, tone, durationMs)`
- `sendUiAlert(playerId, title, message, success, durationMs, toneCode)`
- `sendLegacyUiAlert(playerId, title, legacyMessage, durationMs)`
- `sendSuccessUiAlert(playerId, title, message, durationMs)`
- `sendErrorUiAlert(playerId, title, message, durationMs)`
- `sendInfoUiAlert(playerId, title, message, durationMs)`
- `sendWarningUiAlert(playerId, title, message, durationMs)`
- `getSupportedUiAlertTones()`
- `playerHasAnyAccount(playerId)`
- `playerHasPrimaryAccount(playerId)`
- `playerHasAvailableAccount(playerId)`
- `playerHasAvailablePrimaryAccount(playerId)`
- `playerHasFrozenAccount(playerId)`
- `playerOwnsAccount(playerId, accountId)`
- `playerOwnsBank(playerId, bankId)`
- `accountBelongsToBank(accountId, bankId)`
- `accountIsFrozen(accountId)`
- `accountIsPrimary(accountId)`
- `accountCanSend(accountId, amount)`
- `accountCanReceive(accountId)`
- `primaryAccountCanSend(playerId, amount)`
- `primaryAccountCanReceive(playerId)`
- `bankAcceptsTransactions(bankId)`

Formatting helpers:

- `formatMoneyRounded(amount)` -> configured-currency display string

`formatMoneyRounded` accepts `BigDecimal` or `long` amounts and returns a compact display string using the configured UBS currency symbol. It rounds to two decimals and carries rounded suffixes to the next scale, so `999999` can display as `$1M`. Use this for user-facing integration UIs such as auction-house cards, shop screens, and alerts. Keep using raw `BigDecimal` values for storage, sorting, validation, and transactions.

`shopPurchase` overload note:

- `shopPurchase(accountId, amount, shopName)` is a simple label-based purchase.
- `shopPurchase(payerAccountId, merchantAccountId, amount, shopName, reference)` is the terminal-grade path (explicit merchant routing + external reference string).

Reference-aware transaction methods:

- The overloads with `reference` return `ApiTransactionResult` instead of `ApiResult`.
- `reference` is stored in the transaction description after a stable operation prefix, making external systems such as auction houses, shops, jobs, and quest rewards auditable in UBS transaction history.
- `depositToPrimary`, `withdrawFromPrimary`, `transferFromPrimary`, and `transferToPrimary` are convenience helpers for integrations that only know a player UUID.
- These helpers still validate frozen accounts, bank status, account ownership, balance, and transaction limits server-side.
- For auction-house settlement, prefer `transferFromPrimary(winningBidderId, sellerAccountId, bidAmount, "YOURMOD_AUCTION:<auction-id>")` over minting money with `depositToPrimary`.

`ApiTransactionResult` fields:

- `success`
- `reason`
- `transactionId`
- `senderAccountId`
- `receiverAccountId`
- `amount`
- `balanceAfter`
- `description`

## Snapshot API (Typed Data)

These methods expose stable read models for integration UIs, HUDs, dashboards, and leaderboards.

### Account snapshots

- `getAccountSnapshot(accountId)` -> `Optional<ApiAccountSnapshot>`
- `getPrimaryAccountSnapshot(playerId)` -> `Optional<ApiAccountSnapshot>`
- `getPlayerAccounts(playerId)` -> `List<ApiAccountSnapshot>`
- `getPlayerAccountIds(playerId)` -> `List<UUID>`
- `getBankAccounts(bankId)` -> `List<ApiAccountSnapshot>`
- `setPrimaryAccount(playerId, accountId)` -> `ApiResult`
- `getPrimaryAccountId(playerId)` and `getPrimaryAccountSnapshot(playerId)` return empty when no owned account is explicitly marked primary.

`ApiAccountSnapshot` fields:
- `accountId`
- `playerId`
- `bankId`
- `accountType`
- `accountTypeLabel`
- `balance`
- `primary`
- `frozen`
- `frozenReason`
- `createdAt`

### Account ownership and boolean helpers

Use these helpers when an integration only needs a yes/no answer and should not duplicate UBS account-status rules.

- `getPlayerAccountIds(playerId)` returns the player's account UUIDs, sorted the same way as `getPlayerAccounts`: primary accounts first, then newest accounts.
- `playerHasAnyAccount(playerId)` returns `true` when UBS has at least one account registered to that player UUID.
- `playerHasPrimaryAccount(playerId)` returns `true` when the player has a primary account selected.
- `playerHasAvailableAccount(playerId)` returns `true` when at least one owned account can currently receive normal banking activity.
- `playerHasAvailablePrimaryAccount(playerId)` returns `true` when the player's primary account exists and has status `AVAILABLE`.
- `playerHasFrozenAccount(playerId)` returns `true` when any owned account is frozen.
- `playerOwnsAccount(playerId, accountId)` checks account ownership.
- `playerOwnsBank(playerId, bankId)` checks bank ownership.
- `accountBelongsToBank(accountId, bankId)` checks account-to-bank membership.
- `accountIsFrozen(accountId)` checks whether an account is frozen.
- `accountIsPrimary(accountId)` checks whether an account is marked as the owner's primary account.
- `accountCanSend(accountId, amount)` returns `true` only if `validateAccountCanSend` would succeed.
- `accountCanReceive(accountId)` returns `true` only if `validateAccountCanReceive` would succeed.
- `primaryAccountCanSend(playerId, amount)` checks the player's primary account and returns `false` if no primary account exists.
- `primaryAccountCanReceive(playerId)` checks whether the player's primary account can receive funds.
- `bankAcceptsTransactions(bankId)` returns `false` if the bank is missing or in a transaction-blocking state such as `SUSPENDED`, `REVOKED`, or `LOCKDOWN`.

`amount` accepts `long` or `BigDecimal` for `accountCanSend` and `primaryAccountCanSend`.

## UI Alert API

UBS exposes its client-side action alert card to integrations. Alerts are sent server-side to an online player UUID and render on that player's client using the same queued alert UI as UBS banking, shop, teller, and payment flows.

Methods:

- `sendUiAlert(playerId, title, message, tone, durationMs)` -> `ApiAlertResult`
- `sendUiAlert(playerId, title, message, success, durationMs, toneCode)` -> `ApiAlertResult`
- `sendLegacyUiAlert(playerId, title, legacyMessage, durationMs)` -> `ApiAlertResult`
- `sendSuccessUiAlert(playerId, title, message, durationMs)` -> `ApiAlertResult`
- `sendErrorUiAlert(playerId, title, message, durationMs)` -> `ApiAlertResult`
- `sendInfoUiAlert(playerId, title, message, durationMs)` -> `ApiAlertResult`
- `sendWarningUiAlert(playerId, title, message, durationMs)` -> `ApiAlertResult`
- `getSupportedUiAlertTones()` -> `List<ApiAlertTone>`

`ApiAlertTone` values:

- `SUCCESS` (`toneCode` 0)
- `ERROR` (`toneCode` 1)
- `INFO` (`toneCode` 2)
- `WARNING` (`toneCode` 3)

`ApiAlertResult` fields:

- `success`
- `reason`
- `playerId`
- `title`
- `message`
- `alertSuccess`
- `durationMs`
- `tone`
- `toneCode`

Behavior:

- The target player must be online; offline players return `success=false` with reason `Player is not online`.
- `message` is required and blank messages are rejected.
- `durationMs` is clamped by the UBS alert payload to the supported display window.
- `sendLegacyUiAlert` strips legacy formatting codes and infers tone from color/error wording.
- The raw overload with `success` and `toneCode` exists for integrations that need every payload parameter. Prefer the `ApiAlertTone` overload for normal use.

### Bank snapshots

- `getBankSnapshot(bankId)` -> `Optional<ApiBankSnapshot>`
- `getBanks()` -> `List<ApiBankSnapshot>`

`ApiBankSnapshot` fields:
- `bankId`
- `bankName`
- `ownerId`
- `status`
- `declaredReserve`
- `totalDeposits`
- `minimumRequiredReserve`
- `reserveRatio`
- `outstandingLoanBalance`
- `maxLendableAmount`
- `interestRate`
- `accountCount`

### Transaction snapshots

- `getTransactionSnapshot(transactionId)` -> `Optional<ApiTransactionSnapshot>`
- `getAccountTransactions(accountId, limit)` -> `List<ApiTransactionSnapshot>`
- `getPlayerTransactions(playerId, limit)` -> `List<ApiTransactionSnapshot>`

`ApiTransactionSnapshot` fields:
- `transactionId`
- `senderAccountId`
- `receiverAccountId`
- `amount`
- `timestamp`
- `description`

## Cash & Paper Instruments API

These methods let integrations issue real UBS instruments and physical USD legal tender cash items.

### Bank notes and cheques

- `issueBankNote(sourceAccountId, amountDollars, issuerPlayerId, issuerName)` -> `ApiItemResult`
- `issueCheque(sourceAccountId, recipientPlayerId, amountDollars, writerPlayerId, writerName, recipientName)` -> `ApiItemResult`

Behavior:

- Withdraws the amount from `sourceAccountId`.
- Returns a fully tagged `ItemStack` (`bank_note` or `cheque`) ready to give/store.
- Returns the generated serial/ID in `referenceId`.

`ApiItemResult` fields:

- `success`
- `reason`
- `itemStack`
- `referenceId`
- `amount`

### Dollar bills (denomination + bill count)

- `giveDollarBills(playerId, denomination, billCount)` -> `ApiCashResult`
- `takeDollarBills(playerId, denomination, billCount)` -> `ApiCashResult`
- `getSupportedBillDenominations()` -> `List<Integer>`
- `createDollarBillStacks(denomination, billCount)` -> `List<ItemStack>`
- `getPlayerBillCount(playerId, denomination)` -> `int`
- `getPlayerCashOnHand(playerId)` -> `int`
- `getPlayerCashOnHandCents(playerId)` -> `int`

`denomination` values are face-value dollars: `1, 2, 5, 10, 20, 50, 100`.
`billCount` means count of bill items, not dollar amount.

### Coins (denomination in cents + coin count)

- `giveCoins(playerId, denominationCents, coinCount)` -> `ApiCashResult`
- `takeCoins(playerId, denominationCents, coinCount)` -> `ApiCashResult`
- `getSupportedCoinDenominations()` -> `List<Integer>`
- `createCoinStacks(denominationCents, coinCount)` -> `List<ItemStack>`
- `getPlayerCoinCount(playerId, denominationCents)` -> `int`
- `getPlayerCashOnHand(playerId)` -> `int` (bills + coins)
- `getPlayerCashOnHandCents(playerId)` -> `int` (bills + coins in cents)

`denominationCents` values: `1, 5, 10, 25, 50`.
`coinCount` means count of coin items, not cent total.

`getPlayerCashOnHand(playerId)` returns whole dollars and truncates leftover cents. Use `getPlayerCashOnHandCents(playerId)` when exact coin-aware value is needed.

`ApiCashResult` fields:

- `success`
- `reason`
- `denomination`
- `billCount`
- `totalDollarValue`

For coin operations, `denomination` and `totalDollarValue` use the provided cent-denomination value. Treat `totalDollarValue` as a legacy integer total for the returned denomination/count pair, not as a precise cross-denomination wallet value.

## Aggregated Metrics API

UBS now also exposes aggregate values for leaderboards and HUD overlays:

- `getPlayerTotalBalance(playerId)`
- `getPlayerPrimaryBalance(playerId)`
- `getPlayerAccountCount(playerId)`
- `getBankTotalDeposits(bankId)`
- `getBankReserve(bankId)`
- `getBankStatus(bankId)`

## Pickpocket Metrics API

UBS now exposes read methods for pickpocket history checks:

- `hasPlayerEverStolen(playerId)` -> `boolean`
- `getPlayersStolenFrom(playerId)` -> `List<UUID>`

These methods are intended for moderation dashboards, custom HUD stats, and server-side progression hooks.

## Placeholder Resolver API

Use this when you want token-based text expansion:

- `resolvePlaceholder(playerId, token)`
- `resolvePlaceholders(playerId, text)`
- `getSupportedPlaceholders()`

If a token is unknown, `resolvePlaceholder` returns empty string.  
`resolvePlaceholders` leaves unknown `%token%` values unchanged.

## Supported Placeholder Tokens

Player scope:

- `%ubs_player_total_balance%`
- `%ubs_player_total_balance_raw%`
- `%ubs_player_primary_balance%`
- `%ubs_player_primary_balance_raw%`
- `%ubs_player_account_count%`
- `%ubs_player_primary_account_id%`
- `%ubs_player_primary_account_type%`
- `%ubs_player_primary_bank_id%`
- `%ubs_player_primary_bank_name%`

Primary-bank scope (uses player's primary bank):

- `%ubs_bank_name%`
- `%ubs_bank_id%`
- `%ubs_bank_status%`
- `%ubs_bank_reserve%`
- `%ubs_bank_reserve_raw%`
- `%ubs_bank_total_deposits%`
- `%ubs_bank_total_deposits_raw%`

Explicit bank-id scope:

- `%ubs_bank_name_<bank-uuid>%`
- `%ubs_bank_status_<bank-uuid>%`
- `%ubs_bank_reserve_<bank-uuid>%`
- `%ubs_bank_reserve_raw_<bank-uuid>%`
- `%ubs_bank_total_deposits_<bank-uuid>%`
- `%ubs_bank_total_deposits_raw_<bank-uuid>%`

## Formatted vs Raw Values

- Non-raw money placeholders return abbreviated display values (example: `$1.2M`).
- `formatMoneyRounded(amount)` returns rounded abbreviated display values (example: `$1.23K`) for integration UI text.
- `_raw` placeholders return plain numeric decimal strings (example: `1234567.89`) suitable for sorting/ranking systems.

## Example: Rounded Money Display

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
String label = api.formatMoneyRounded(new BigDecimal("1234.56")); // "$1.23K" by default
```

## Example: Leaderboard Line

```java
UUID playerId = player.getUUID();
UltimateBankingApi api = UltimateBankingApiProvider.get();

String line = api.resolvePlaceholders(
        playerId,
        "Net Worth: %ubs_player_total_balance% | Accounts: %ubs_player_account_count%"
);
```

## Example: Numeric Sort Key

```java
String raw = api.resolvePlaceholder(playerId, "%ubs_player_total_balance_raw%");
BigDecimal value = new BigDecimal(raw);
```

## Example: Snapshot Usage

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();

api.getPrimaryAccountSnapshot(player.getUUID()).ifPresent(primary -> {
    System.out.println("Primary account: " + primary.accountId());
    System.out.println("Balance: " + primary.balance());
});

for (ApiBankSnapshot bank : api.getBanks()) {
    System.out.println(bank.bankName() + " reserve ratio = " + bank.reserveRatio());
}
```

## Example: Give Bills

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
ApiCashResult result = api.giveDollarBills(player.getUUID(), 20, 6); // six $20 bills

if (!result.success()) {
    System.out.println("Failed to give bills: " + result.reason());
}
```

## Example: Give Coins

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
ApiCashResult result = api.giveCoins(player.getUUID(), 25, 12); // twelve quarters

if (!result.success()) {
    System.out.println("Failed to give coins: " + result.reason());
}
```

## Example: Issue Cheque

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
ApiItemResult cheque = api.issueCheque(
        sourceAccountId,
        recipientPlayerId,
        250L,
        writerPlayerId,
        "Bank Admin",
        "RecipientName"
);

if (cheque.success()) {
    ItemStack stack = cheque.itemStack();
    // give to player inventory or store for later
}
```
