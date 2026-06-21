# Changelog

All notable changes to this project are documented in this file.

## [1.4.2] - 2026-06-21

### Removed
- Removed the browser-based admin panel, its server-management routes, static UI assets, embedded HTTP runtime, and addon dashboard API from the distributed mod.

### Changed
- Updated admin and developer documentation so the release no longer advertises browser-based server management features.

## [1.4.0] - 2026-06-20

### Added
- Added the wallet item, wallet menu/screen, wallet model/texture/recipe, and wallet placeholder textures.
- Added wallet cash and card tender support for bank teller and shop cashier payment flows.
- Added bank level admin tooling and demo banking seed commands.
- Added leaderboard seed/remove commands and diagnostics for release validation.

### Changed
- Account access checks now use centralized denial messaging for frozen source/destination accounts.
- Primary-account updates now route through a central bank helper so only one account is primary per player.

### Fixed
- Preserved oversized desktop response guarding for large desktop payloads.
- Kept safety deposit box, bank vault door, and iron gate work excluded from this release.

## [1.3.1] - 2026-06-20

### Fixed
- Fixed the bank Accounts drilldown buttons by adding the missing `ACCOUNT_DETAIL`, freeze/unfreeze, and temporary-limit action handlers.
- Account detail responses now return the structured profile/history payload expected by the redesigned owner PC UI.

## [1.3.0] - 2026-06-20

### Added
- Redesigned Retail Webshop catalog, cart, checkout, delivery-target selection, and tracking flows.
- Added payment account and delivery target modal workflows for webshop checkout.
- Added replacement ordering for completed/failed webshop orders with confirmation summary.
- Added shop type, franchise, and corporate retail configuration keys.

### Changed
- Updated UBS desktop, shop, order-board, and retail-webshop controls to use the darker commerce UI theme.
- Removed creative invisible displays from the retail webshop catalog.
- Cart items now expose per-item quantity and remove controls next to checkout totals.
- Tracking no longer shows failure reasons/fix cards for successful orders.

### Fixed
- Fixed retail-webshop modal layering so modal buttons render and click above background controls.
- Fixed product-detail quantity/add/back controls so they scroll with the product detail panel.
- Guarded oversized desktop action responses from crashing order-board/report refreshes.

### Docs
- Updated README and wiki sources for the redesigned shopping system, tracking flow, and new shop configuration keys.

## [1.2.1] - 2026-06-01

### Added
- French (`fr_fr`) in-game translation coverage for UBS blocks, items, and configuration labels/descriptions.
- New UI alert helper APIs in the public developer API.
- Additional account helper APIs for safer account access and balance checks.

### Changed
- Decoupled UI alert API calls from alert internals to keep external integrations stable.

### Fixed
- Primary account helper behavior in API utility methods.

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
