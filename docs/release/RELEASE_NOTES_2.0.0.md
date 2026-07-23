# Ultimate Banking System 2.0.0

UBS 2.0 is the largest update to the mod so far. It connects banking, retail, physical security, communication, and multiplayer heists into one server-authoritative economy platform.

## Headline Features

- Smartphone banking and realtime messenger with pay requests, gifts, contacts, notifications, passwords, and per-account login
- Bank premises, vault readiness, safety-deposit rows, private viewing rooms, alarms, access logs, and storage maps
- Physical safes, cash stacks, gold/silver bars, metal pallets, and spot-market values
- RFID scanners/cards, per-player passage security, programmable signals, and a heist spoofer
- Full multiplayer bank-heist loop with crews, tools, drills, hacks, duffel loot, alarms, tactical extraction, cooldowns, and recovery
- Unified claim mode for bank and shop world operations
- Public Java API 2.0 for finance, notifications, market prices, banks, shops, and heists

## Retail and UI

- Responsive Business Manager PC and phone layouts
- Redesigned retail webshop catalog/cart/checkout/tracking
- Shop timezone display and local/server time conversion
- Improved modals, clipping, scrolling, small-screen behavior, and GUI-scale handling
- New notification renderer and movable balance HUD

## Server and Data Safety

- Account transaction history now retains the newest 20 entries by default to reduce save-time spikes and world-data growth.
- Premise protection blocks unauthorized destruction while preserving inventory synchronization.
- Heist cleanup evacuates players and restores tracked assets after timeout, abandonment, disconnect, crash recovery, or server stop.
- The release contains no embedded HTTP/WebSocket admin server or browser-based management panel.

See the wiki for setup and operator details.

