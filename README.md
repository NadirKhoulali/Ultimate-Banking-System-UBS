# Ultimate Banking System (UBS)

UBS is a NeoForge `1.21.1` banking mod focused on a full in-world economy loop: ATM UI, physical cash bills, account security, player-owned banks, central-bank regulation, loans, and admin tooling.

## What UBS Includes

- Multi-account support per player: `Checking`, `Saving`, `Money Market`, `Certificate`
- ATM workflow with account selection, PIN setup/login, and account switching
- Physical USD bill items (`$1`, `$2`, `$5`, `$10`, `$20`, `$50`, `$100`) for real withdraw/deposit flow
- Transfers, transaction history, and per-account controls
- Pay request system:
  - inbox + accept/decline/choose account
  - ATM pay request creation UI with player picker + search + destination account picker
  - command flow via `/payrequest ...`
- Scheduled interest, daily withdrawal limits, and transaction rate limiting
- Joint accounts, business accounts, cheques, and note withdrawal/deposit commands
- Player-owned bank systems:
  - bank application/approval and appeal flows
  - ownership modes (roles/shares/cofounders)
  - employee payroll, loan products, reserve dashboards, interbank lending market
- Central Bank systems:
  - Federal Funds Rate controls
  - open market operations
  - reserve audits/compliance + clearing ledger/suspense records
  - annual license renewals and periodic bank tax handling
- Safe deposit box storage and bank heist mechanics
- Admin moderation and migration tooling (`csv`, `EssentialsX`, `CMI`, `iConomy`)

## Core Commands

Player-facing (examples):

- `/account create <AccountType> <Bank Name>`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/payrequest <player> <amount> [destinationAccountId]`
- `/bank reserve`
- `/bank dashboard`
- `/bank safebox list|deposit|withdraw <slot>`
- `/bank heist start <bankName>`

Admin-facing (examples):

- `/ubs admin view <player>`
- `/ubs admin freeze <player> [reason]`
- `/ubs admin unfreeze <player>`
- `/ubs admin applications`, `/ubs admin appeals`
- `/centralbank rate`, `/centralbank rate set <rate>`
- `/centralbank opm inject|withdraw <amount>`
- `/centralbank audit [bankName]`
- `/centralbank ledger [suspense]`

## Build

Requirements:

- Java `21+`
- NeoForge toolchain

Build:

```bash
./gradlew build
```

On Windows shell environments, run via `gradlew.bat`.

## Documentation

- Player quick guide: [`PLAYER_GUIDE.md`](PLAYER_GUIDE.md)
- Wiki sources for GitHub wiki publishing: [`docs/wiki`](docs/wiki)
  - [`Home.md`](docs/wiki/Home.md)
  - [`Player-Guide.md`](docs/wiki/Player-Guide.md)
  - [`ATM-Flow.md`](docs/wiki/ATM-Flow.md)
  - [`Bank-Owner-PC.md`](docs/wiki/Bank-Owner-PC.md)
  - [`Admin-Commands.md`](docs/wiki/Admin-Commands.md)
  - [`Developer-API.md`](docs/wiki/Developer-API.md)
  - [`Developer-Integration-Tutorial.md`](docs/wiki/Developer-Integration-Tutorial.md)
  - [`Configuration.md`](docs/wiki/Configuration.md)
  - [`Migration-Guide.md`](docs/wiki/Migration-Guide.md)

## Developer API (Quick View)

Java entry point:

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
```

Highlights:

- Core money ops: balance/deposit/withdraw/transfer/shopPurchase
- Paper instruments + cash API:
  - issue tagged `bank_note` and `cheque` item stacks
  - give/take USD bills by `denomination + billCount`
  - cash inventory helpers (`getPlayerBillCount`, `getPlayerCashOnHand`)
- Typed snapshots:
  - `ApiAccountSnapshot` via account/player/bank lookup
  - `ApiBankSnapshot` via bank lookup/list
  - `ApiTransactionSnapshot` via transaction/account/player history
- Placeholder resolver for scoreboards/HUD:
  - `resolvePlaceholder(playerId, token)`
  - `resolvePlaceholders(playerId, text)`
  - `getSupportedPlaceholders()`

Full reference: [`docs/wiki/Developer-API.md`](docs/wiki/Developer-API.md)

Integration walkthrough: [`docs/wiki/Developer-Integration-Tutorial.md`](docs/wiki/Developer-Integration-Tutorial.md)
Includes exact `settings.gradle`, `build.gradle`, and `neoforge.mods.toml` dependency setup (required + optional modes).
