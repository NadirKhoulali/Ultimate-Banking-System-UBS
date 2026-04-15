# Changelog

All notable changes to this project are documented in this file.

## [1.2.0] - 2026-04-15

### Added
- New merchant checkout stack:
  - world `payment_terminal` block with configurable idle/success/failure states
  - handheld payment terminal item for direct player-to-player checkout
  - dedicated terminal configuration UIs and merchant account targeting
- Credit card system with issuance/replacement flows and validation paths.
- Bank teller service + GUI flow for:
  - cheque/bank note/cash workflows
  - account opening
  - card issuance and replacement handling
- Legal tender expansion with U.S. coins (front/back textured models) alongside dollar bills.
- Creative tab support for UBS items and broader item/model registration coverage.
- Payment terminal and currency documentation pages in the wiki set.

### Changed
- Currency display formatting now abbreviates without rounding up and preserves up to 2 decimal places.
- Handheld terminal save validation now enforces configurable max price limits using `GlobalMaxSingleTransaction`.
- Handheld hover payment overlay is rendered with corrected GUI layer ordering.
- Shift + right-click handheld configuration opening restored.
- Bank owner PC and ATM UX received additional spacing/layout/panel behavior refinements.

### Fixed
- Handheld interaction flow issues around role direction and repeated interaction feedback.
- Multiple UI collision, overflow, and layering defects across PC/teller/terminal related screens.
- Payment terminal behavior fixes for facing/state feedback timing and merchant account handling paths.
- Assorted command/help, transaction display, and account flow consistency fixes.

### Docs
- Updated repo docs/wiki content for new payment terminal, legal tender list, and integration references.
- Added/updated wiki pages in `docs/wiki` for release-aligned feature coverage.

## [1.1.0] - 2026-04-14

### Added
- Full bank-owner PC experience with desktop-style workflow and multi-app/taskbar handling.
- New utility apps in the PC: Calculator, Notepad, Paint, File Explorer, and System tools.
- Per-computer local file storage flow for notes/canvas files.
- Expanded public Developer API:
  - typed snapshots for accounts, banks, and transactions
  - cash and item issuance helpers (`bank_note`, `cheque`, USD bills)
  - aggregate balance/reserve metrics
  - placeholder resolver for integrations and leaderboards
- Bank Teller NPC spawn egg and teller interaction flow for cheque handling.

### Changed
- ATM and PC interfaces received major layout and spacing updates for better usability.
- Money displays now support abbreviated formatting in player-facing contexts.
- Bank and account interaction flows were refined (PIN/account switching/feedback behavior).

### Fixed
- Primary account handling now enforces a single primary account per player.
- Multiple GUI collision/overflow cases across ATM and bank-owner PC screens.
- Networking payload and command/runtime compatibility issues reported during compile/tests.
- Bank command handling for names with spaces in close/management flows.

### Docs
- Updated README and wiki with API, integration, and configuration coverage.
- Added release-oriented documentation artifacts and publish-ready description source.

### Coming Soon
- Bank Heist remains intentionally disabled and is marked as Coming Soon in commands, config labels, and docs.
