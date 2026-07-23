# Changelog

All notable changes to Ultimate Banking System are documented here.

## [2.0.0] - 2026-07-23

### Added

- Added a complete smartphone platform with a lock screen, password setup, per-account bank apps and PIN sessions, realtime messenger, contacts/search, typing state, pay requests, money gifts, notifications, notes, calculator, paint, settings, themes, and optional JourneyMap, real-estate, and auction-house bridges.
- Added physical bank premises, safe-area claims, vault-readiness checks, safety-deposit rows and sizes, teller leasing, private viewing rooms, physical deposit trays, contents rendering, rent policy, audit logs, alarms, and vault-storage mapping.
- Added standing and compact safes with animated doors, PIN access, physical shelf slots, cash/bullion stacking, drill support, and optional chest storage upgrades.
- Added gold and silver bars, metal pallets, placeable money stacks, a global bullion spot market, market-aware lore/value display, and corrected recipes/models.
- Added programmable RFID scanners, unique cards, access levels, per-reader card authorization, signal targets, configurable strengths/durations, access logs, per-player passage protection, and an RFID spoofer for heists.
- Added multiplayer bank heists with crew planning, invites/readiness, target selection, masks, lockpicks, OVE9000 saws, thermal drills, concurrent drills and computer hacks, destructible deposit boxes, pallet/safe theft, duffel bags, alarms, tactical HUDs, extraction borders, holograms, cooldowns, abandonment, timeout evacuation, and crash/disconnect recovery.
- Added a unified claim-mode framework for bank premises, safe areas, viewing rooms, shop plots, stockrooms, and pallet operations with tactical outlines and a dedicated responsive HUD.
- Added Notification API v2 with semantic types, priorities, adaptive placement, stable-ID updates, sticky notices, deduplication, sounds, and dismiss/channel-clear operations.
- Added public Java API `2.0.0` entry points for server feature discovery, player portfolios, bank management, shop management, and heist state/actions.
- Added immutable bank snapshots for reserves, deposits, lending, staffing, tellers, premises, safe readiness, and attack state.
- Added immutable shop snapshots and APIs for ownership/access checks, roles, setup/open state, progression/capacity, create/rename/type/hours, participants, and deletion.
- Added immutable heist snapshots and APIs for sessions, members, drills, hacks, targets, eligibility blockers, cooldowns, victim protection, planning, invitations, readiness, countdowns, leaving, and abandonment.
- Added shop price-statistics APIs for regular registered shops, every shelf, non-creative shelves, and creative-only shelves.
- Added a movable balance HUD with six supported anchor positions.

### Changed

- Redesigned the banking phone app, messenger, retail webshop, business type panel, Bank Owner PC, safe operations, claim mode, notifications, and balance HUD for responsive UI scales and smaller displays.
- Replaced path-walking safety-deposit escorts with isolated viewing-room sessions that temporarily present only the customer's physical box and restore all state when the visit ends.
- Safety-deposit availability now depends on current physical setup and is suspended, without deleting customer data, when required vault infrastructure is removed.
- Bank premises now prevent unauthorized breaking, placement, destructive interactions, projectiles, and explosions while preserving the player's inventory state.
- Shop opening-hours displays now expose server timezone and client-local conversions.
- `/account open` now creates a Central Bank checking account; custom-bank accounts are opened through bank tellers.
- Transaction history persistence is bounded per account. The default is the newest `20` entries to reduce world-save time and saved-data growth.
- Shop, delivery, safe, and heist systems now support additional admin repair and force-management commands.
- The public API version is now `2.0.0`; existing finance and legacy alert entry points remain available.

### Fixed

- Fixed duplicate/reordered messenger delivery and added realtime client updates.
- Fixed phone scaling, clipping, keyboard modifiers, multiline input, scroll behavior, app alignment, and loading/session behavior.
- Fixed PC modal layering, fixed-position controls, scroll clipping, small-screen layout loss, and GUI-scale handling.
- Fixed deposit-row capacity, live readiness refresh, vault-door detection, viewing-room tray models, and item rendering.
- Fixed heist drill placement/orientation, safe textures, extraction completion, concurrent interactions, loot prompts, cooldown administration, and forced evacuation.
- Fixed protected-premise placement rollback so client inventory and hotbar state remain synchronized.
- Fixed account type translation keys leaking into player-facing UI.

### Security and Distribution

- Removed the embedded Netty HTTP/WebSocket administration server, browser assets, remote web commands, and dashboard-host extension API. The release exposes no browser-based server-management panel.
- Retained in-game administration and expanded the server-side Java API for trusted mod integrations.

## [1.4.3] - 2026-06-27

- Published the moderation-safe NeoForge 1.21.1 maintenance build.
- Removed the embedded web administration runtime from the distributed jar.
- Fixed owner-PC account detail actions and retained the shopping-system update.

## [1.3.0] - 2026-06-20

- Redesigned Retail Webshop catalog, cart, checkout, delivery-target selection, and tracking.
- Added payment-account and delivery-target modal workflows, replacement ordering, and shop type metadata.
- Updated desktop commerce styling and fixed modal/input/scroll regressions.

## [1.2.0] - 2026-04-15

- Added payment terminals, handheld terminals, credit cards, teller services, legal-tender coins, and expanded public finance APIs.

## [1.1.0] - 2026-04-14

- Added the Bank Owner PC, desktop utilities, typed account/bank/transaction snapshots, cash and paper-instrument APIs, and teller NPCs.

