# Ultimate Banking System 2.1.1

UBS 2.1.1 improves live integration accuracy for NeoEssentials leaderboards and player cash placeholders while preserving compatibility with existing UBS APIs.

## Headline Features

- Live NeoEssentials leaderboards for UBS player, shop, and bank metrics.
- PAPI-compatible and NeoEssentials-compatible UBS placeholders.
- Physical tender totals now include loose bills, coins, money stacks, and cash stored in wallets.
- Nested inventory scanning through the standard NeoForge item-handler capability, covering compatible backpacks and container items.

## Accuracy Fixes

- Operator accounts are no longer hidden from UBS leaderboards by an unintended exemption permission.
- Central Bank is collected once when calculating player leaderboard values, preventing doubled balances and account counts.
- Cash values are calculated with long-safe arithmetic and clamped only at the legacy integer API boundary.

## Compatibility

- Backpack support is capability-based and does not add a hard dependency on Sophisticated Backpacks.
- Existing placeholder names remain unchanged: `{ubs_cash_balance}`, `{ubs_cash_balance_raw}`, and their PAPI-style equivalents continue to work.

See the [Developer API](../wiki/Developer-API.md) for current placeholder and integration details.
