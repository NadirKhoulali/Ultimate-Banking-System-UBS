# Developer API & Placeholders

This page describes UBS API access for other mods/plugins and the built-in placeholder resolver.

## Java API Entry Point

Use:

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
```

## Core API Operations

Banking actions:

- `getBalance(accountId)`
- `deposit(accountId, amount)`
- `withdraw(accountId, amount)`
- `transfer(senderAccountId, receiverAccountId, amount)`
- `shopPurchase(accountId, amount, shopName)`
- `issueBankNote(sourceAccountId, amountDollars, issuerPlayerId, issuerName)`
- `issueCheque(sourceAccountId, recipientPlayerId, amountDollars, writerPlayerId, writerName, recipientName)`
- `giveDollarBills(playerId, denomination, billCount)`
- `takeDollarBills(playerId, denomination, billCount)`

Service/runtime checks:

- `getApiVersion()`
- `isServerAvailable()`
- `accountExists(accountId)`
- `bankExists(bankId)`

## Snapshot API (Typed Data)

These methods expose stable read models for integration UIs, HUDs, dashboards, and leaderboards.

### Account snapshots

- `getAccountSnapshot(accountId)` -> `Optional<ApiAccountSnapshot>`
- `getPrimaryAccountSnapshot(playerId)` -> `Optional<ApiAccountSnapshot>`
- `getPlayerAccounts(playerId)` -> `List<ApiAccountSnapshot>`
- `getBankAccounts(bankId)` -> `List<ApiAccountSnapshot>`
- `setPrimaryAccount(playerId, accountId)` -> `ApiResult`

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

These methods let integrations issue real UBS instruments and physical USD bill items.

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

`denomination` values are face-value dollars: `1, 2, 5, 10, 20, 50, 100`.
`billCount` means count of bill items, not dollar amount.

`ApiCashResult` fields:

- `success`
- `reason`
- `denomination`
- `billCount`
- `totalDollarValue`

## Aggregated Metrics API

UBS now also exposes aggregate values for leaderboards and HUD overlays:

- `getPlayerTotalBalance(playerId)`
- `getPlayerPrimaryBalance(playerId)`
- `getPlayerAccountCount(playerId)`
- `getBankTotalDeposits(bankId)`
- `getBankReserve(bankId)`
- `getBankStatus(bankId)`

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
- `_raw` placeholders return plain numeric decimal strings (example: `1234567.89`) suitable for sorting/ranking systems.

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
