# Ultimate Banking System (UBS)

Ultimate Banking System is a NeoForge `1.21.1` economy, banking, retail, security, and heist mod built around physical world interactions and multiplayer server management.

Current release: `2.1.2`

## Highlights

- Multi-account banking with checking, saving, money-market, certificate, joint, and business workflows
- ATMs, bank tellers, payment terminals, cards, wallets, cheques, notes, and physical USD cash
- Player-owned banks with staffing, lending, governance, premises, vaults, alarms, and safety-deposit operations
- Player-owned shops with claims, staff roles, stockrooms, displays, pallets, cashiers, delivery orders, webshop checkout, and opening hours
- Smartphone with banking, per-account login, contacts, realtime messenger, pay requests, gifts, notes, settings, and optional mod bridges
- Physical standing and compact safes, cash stacks, gold/silver bars, metal pallets, and market-linked valuation
- RFID readers, programmable access cards, signal targets, per-player door authorization, and heist spoofing
- Multiplayer bank heists with crews, planning, masks, saws, lockpicks, thermal drills, computer hacks, duffel loot, alarms, extraction zones, cooldowns, and recovery safeguards
- Unified claim mode with tactical outlines, collision validation, private claim visibility, and responsive controls
- Public Java API `2.1.1` for finance, institutional accounts, durable idempotent operations, monetary escrow, notifications, market prices, server discovery, bank management, shop management, and heist state/actions

The release jar does not contain an embedded HTTP server or browser-based administration panel. Server management remains in game and through the Java API.

## Core Commands

```text
/account open
/account info list
/account transfer <senderAccountId> <receiverAccountId> <amount>
/account payrequest <player> <amount> [destinationAccountId]
/account hud toggle|primary|account <accountId>|move <position>
/account safebox list|deposit|withdraw <slot>
/heist
/heist abandon
/bank reserve
/centralbank report [history]
/ubs admin ...
```

`/account open` creates a default Central Bank checking account. Additional accounts at player-owned banks are opened through that bank's teller.

## Build

Requirements:

- Java `21`
- NeoForge `21.1.x`
- Minecraft `1.21.1`

```bash
./gradlew build
```

On Windows, use `gradlew.bat build`.

## Documentation

- [Player Guide](PLAYER_GUIDE.md)
- [Wiki Home](docs/wiki/Home.md)
- [Retail and Shop System](docs/wiki/Retail-Shop-System.md)
- [Safety Deposit Boxes](docs/wiki/Safety-Deposit-Boxes.md)
- [Bank Heists](docs/wiki/Bank-Heists.md)
- [RFID and Security](docs/wiki/RFID-and-Security.md)
- [Claim Tools](docs/wiki/Claim-Tools.md)
- [Smartphone](docs/wiki/Smartphone.md)
- [Admin Commands](docs/wiki/Admin-Commands.md)
- [Developer API](docs/wiki/Developer-API.md)
- [Integration Tutorial](docs/wiki/Developer-Integration-Tutorial.md)
- [Configuration](docs/wiki/Configuration.md)
- [Changelog](CHANGELOG.md)

## Developer API

```java
UltimateBankingApi finance = UltimateBankingApiProvider.get();
UltimateServerApi server = UltimateBankingApiProvider.server();
UltimateBankManagementApi banks = UltimateBankingApiProvider.banks();
UltimateShopManagementApi shops = UltimateBankingApiProvider.shops();
UltimateHeistApi heists = UltimateBankingApiProvider.heists();
UltimateEconomyApi economy = UltimateBankingApiProvider.economy();
```

API snapshots are immutable. Mutations validate existing UBS permissions and must run on the logical server thread. The 2.1 economy module adds institutional principals, grants, revisions, durable idempotency, reconciliation, and monetary escrow behind one four-method interface. See the [API reference](docs/wiki/Developer-API.md) for complete method and data-model coverage.

## Project

- Organization: [PixelForgeMods](https://github.com/PixelForgeMods)
- Repository: [Ultimate-Banking-System-UBS](https://github.com/PixelForgeMods/Ultimate-Banking-System-UBS)
