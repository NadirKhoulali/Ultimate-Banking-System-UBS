# Developer API & Placeholders

This page describes UBS API access for other mods/plugins and the built-in placeholder resolver.

## Java API Entry Point

Use:

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
```

Main account operations:

- `getBalance(accountId)`
- `deposit(accountId, amount)`
- `withdraw(accountId, amount)`
- `transfer(senderAccountId, receiverAccountId, amount)`
- `shopPurchase(accountId, amount, shopName)`

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
