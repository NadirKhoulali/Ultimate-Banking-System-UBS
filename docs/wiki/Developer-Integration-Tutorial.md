# Developer Integration Tutorial

This tutorial shows how to integrate your mod with Ultimate Banking System (UBS).

## 1. Prerequisites

- Minecraft `1.21.1`
- NeoForge `21.1.x`
- Java `21`
- UBS installed on the server/client where your mod runs

## 2. Add UBS as a dependency

Use one of these approaches:

- Development workspace: add UBS source as an included project.
- JAR dependency: place UBS jar in your `libs/` folder and add it to your mod dependencies.

At runtime, UBS must be loaded, otherwise API calls will return unavailable/empty results.

## 3. Access the API

```java
import net.austizz.ultimatebankingsystem.api.UltimateBankingApi;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

UltimateBankingApi api = UltimateBankingApiProvider.get();
```

## 4. Do a safe startup check

```java
if (!api.isServerAvailable()) {
    // Server not ready yet or UBS data not loaded
    return;
}
```

You can also log API version:

```java
String version = api.getApiVersion();
```

## 5. Basic account operations

```java
var balance = api.getBalance(accountId);
if (!balance.success()) {
    // handle error: balance.reason()
    return;
}

var withdraw = api.withdraw(accountId, 150L); // whole-dollar amount
if (!withdraw.success()) {
    // handle error: withdraw.reason()
}
```

## 6. Read snapshot data for UI/HUD

```java
api.getPrimaryAccountSnapshot(playerId).ifPresent(primary -> {
    // primary.balance(), primary.accountTypeLabel(), primary.bankId(), etc.
});

for (var bank : api.getBanks()) {
    // bank.bankName(), bank.reserveRatio(), bank.status(), etc.
}
```

## 7. Work with physical bills

Bill APIs use:

- `denomination`: one of `1, 2, 5, 10, 20, 50, 100`
- `billCount`: number of bill items, not total dollars

Examples:

```java
var cashResult = api.giveDollarBills(playerId, 20, 4); // gives 4x $20 bills
if (!cashResult.success()) {
    // cashResult.reason()
}

int twenties = api.getPlayerBillCount(playerId, 20);
int cashOnHand = api.getPlayerCashOnHand(playerId);
```

## 8. Create cheques and bank notes

These methods deduct balance from source account and return tagged item stacks you can give to players.

```java
var note = api.issueBankNote(sourceAccountId, 500L, issuerId, issuerName);
if (note.success()) {
    ItemStack stack = note.itemStack();
    // give stack to player inventory
}

var cheque = api.issueCheque(
        sourceAccountId,
        recipientId,
        250L,
        writerId,
        writerName,
        recipientName
);
if (cheque.success()) {
    ItemStack stack = cheque.itemStack();
    // give stack to player inventory
}
```

## 9. Placeholder integration

Use placeholders for scoreboard/HUD text without manually formatting account data:

```java
String line = api.resolvePlaceholders(
        playerId,
        "Balance: %ubs_player_primary_balance% | Net: %ubs_player_total_balance%"
);
```

For sort/ranking logic, use `_raw` placeholders:

```java
String raw = api.resolvePlaceholder(playerId, "%ubs_player_total_balance_raw%");
```

## 10. Recommended integration pattern

- Check `isServerAvailable()` before heavy API usage.
- Always check `result.success()` for `ApiResult`, `ApiCashResult`, `ApiItemResult`.
- Treat player-inventory methods as server-side actions.
- Use snapshot methods for read-only UI.
- Keep writes (`withdraw`, `transfer`, `issueCheque`, `issueBankNote`) behind permission checks in your own mod.

## 11. Troubleshooting

- API returns empty/false for everything:
  - UBS may not be loaded or server data is not initialized yet.
- Cash methods fail with player errors:
  - Bill methods require the target player to be online.
- Instrument issue fails:
  - Source account may not exist, be unavailable, or have insufficient balance.

## 12. Related docs

- [Developer API](Developer-API.md)
- [Configuration](Configuration.md)
- [Admin Commands](Admin-Commands.md)
