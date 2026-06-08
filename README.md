# Ultimate Banking System (UBS)

UBS is a Forge `1.20.1` banking/economy mod focused on an in-world loop: ATM UI, physical legal tender, payment terminals, player-owned banks, central-bank policy, retail commerce systems, and admin migration tooling.

Current release target: `1.2.0`  
Current worktree: large post-`1.2.0` additions (retail/shop stack, world cash economy, pickpocket, extended desktop apps).

## What UBS Includes

- Multi-account support per player: `Checking`, `Saving`, `Money Market`, `Certificate`
- ATM flow with account selection, PIN setup/login, and account switching
- Physical USD legal tender:
  - bills: `$1`, `$2`, `$5`, `$10`, `$20`, `$50`, `$100`
  - coins: `$0.01`, `$0.05`, `$0.10`, `$0.25`, `$0.50`
- ATM cash behavior:
  - withdraw: bills only
  - deposit: bills + coins (exact inventory match)
- Teller and terminal payment flows:
  - bank teller cash-out (bills + coins)
  - `payment_terminal` block + `handheld_payment_terminal`
  - success/denied feedback and configurable terminal redstone output
- Expanded retail/shop systems:
  - shelf/display blocks (shelves, tables, coolers, modular/glass displays)
  - shopping baskets/bags and cashier interaction flow
  - stockroom claims, pallet assignment, restock tooling, order workflows
  - webshop cart/checkout/delivery queue and courier board features
- World cash economy systems:
  - structure chest cash loot injection
  - mob cash drops
  - configurable player death cash-drop handling
- Pickpocket system:
  - hold-to-complete pickpocket interaction
  - per-player opt-out toggle and cooldowns
- Banking and governance systems:
  - transfers, transaction history, limits, pay requests
  - joint/business accounts, notes, cheques, safe boxes
  - player-owned bank ownership modes, staffing, loan products, interbank lending
  - central bank policy (`rate`, `opm`, audits, report, clearing ledger)
- Admin moderation and migration tooling (`csv`, `EssentialsX`, `CMI`, `iConomy`)

## Core Commands

Player-facing examples:

- `/account open <accountType> [certificateTier] <bankName>`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/account payrequest <player> <amount> [destinationAccountId]`
- `/account hud toggle|primary|account <accountId>`
- `/account pickpocket toggle|status`
- `/account safebox list|deposit|withdraw <slot>`
- `/account shop pay <amount> [shop]`
- `/bank reserve`
- `/bank dashboard`
- `/cashier cancel` and `/bankteller cancel` for active NPC sessions

Admin-facing examples:

- `/ubs admin view <player>`
- `/ubs admin freeze <player> [reason]`
- `/ubs admin applications`, `/ubs admin appeals`
- `/ubs admin import csv|essentialsx|cmi|iconomy <path>`
- `/centralbank rate`, `/centralbank rate set <rate>`
- `/centralbank opm inject|withdraw <amount>`
- `/centralbank audit [bankName]`
- `/centralbank report [history]`
- `/centralbank ledger [suspense]`

## Build

Requirements:

- Java `17+` (CI currently runs JDK `21`)
- Forge toolchain for Minecraft `1.20.1`

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
  - [`Currency-Legal-Tender.md`](docs/wiki/Currency-Legal-Tender.md)
  - [`Payment-Terminal-Guide.md`](docs/wiki/Payment-Terminal-Guide.md)
  - [`Retail-Shop-System.md`](docs/wiki/Retail-Shop-System.md)
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
  - give/take USD coins by `denominationCents + coinCount`
  - cash inventory helpers (`getPlayerBillCount`, `getPlayerCoinCount`, `getPlayerCashOnHand`)
- Typed snapshots:
  - `ApiAccountSnapshot` via account/player/bank lookup
  - `ApiBankSnapshot` via bank lookup/list
  - `ApiTransactionSnapshot` via transaction/account/player history
- Pickpocket metrics:
  - `hasPlayerEverStolen(playerId)`
  - `getPlayersStolenFrom(playerId)`
- Placeholder resolver for scoreboards/HUD:
  - `resolvePlaceholder(playerId, token)`
  - `resolvePlaceholders(playerId, text)`
  - `getSupportedPlaceholders()`

Full reference: [`docs/wiki/Developer-API.md`](docs/wiki/Developer-API.md)

Integration walkthrough: [`docs/wiki/Developer-Integration-Tutorial.md`](docs/wiki/Developer-Integration-Tutorial.md)

## Release

- Changelog: [`CHANGELOG.md`](CHANGELOG.md)
- Release checklist: [`docs/release/RELEASE_CHECKLIST.md`](docs/release/RELEASE_CHECKLIST.md)
- CurseForge WYSIWYG description source: [`docs/release/CURSEFORGE_DESCRIPTION_WYSIWYG.html`](docs/release/CURSEFORGE_DESCRIPTION_WYSIWYG.html)
