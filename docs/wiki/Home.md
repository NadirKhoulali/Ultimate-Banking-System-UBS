# Ultimate Banking System Wiki

This wiki documents UBS `2.0.0` for Minecraft `1.21.1` on NeoForge.

## Player Guides

- [Player Guide](Player-Guide.md)
- [ATM Flow](ATM-Flow.md)
- [Currency and Legal Tender](Currency-Legal-Tender.md)
- [Payment Terminal Guide](Payment-Terminal-Guide.md)
- [Smartphone](Smartphone.md)
- [Retail and Shop System](Retail-Shop-System.md)
- [Safety Deposit Boxes](Safety-Deposit-Boxes.md)
- [Bank Heists](Bank-Heists.md)
- [RFID and Security](RFID-and-Security.md)

## Owner and Admin Guides

- [Bank Owner PC](Bank-Owner-PC.md)
- [Claim Tools](Claim-Tools.md)
- [Admin Commands](Admin-Commands.md)
- [Configuration](Configuration.md)
- [Migration Guide](Migration-Guide.md)

## Developer Guides

- [Developer API](Developer-API.md)
- [Developer Integration Tutorial](Developer-Integration-Tutorial.md)

## UBS 2.0

The 2.0 release expands UBS from banking and shopping into a connected physical-economy platform:

- responsive smartphone banking and realtime communication
- bank premises, vault operations, alarms, deposit rows, and private viewing rooms
- physical safes, money stacks, bullion, metal pallets, and spot-market valuation
- RFID access control and per-player protected passage
- multiplayer bank heists with physical tools, loot, alarms, extraction, and recovery
- unified tactical claim tools
- a Java API covering finance, notifications, market pricing, banks, shops, and heists

For server safety and distribution-policy compliance, UBS does not ship an embedded browser-based administration server. Administration is performed in game; trusted mods integrate through the Java API.

## Quick Start

1. Install the UBS jar on both the server and client.
2. Run `/account open` to create a Central Bank checking account.
3. Use an ATM or smartphone to set the account PIN and manage banking.
4. Use the Business Manager PC to create and operate a bank or shop.
5. Review [Configuration](Configuration.md) before enabling or balancing production features.

