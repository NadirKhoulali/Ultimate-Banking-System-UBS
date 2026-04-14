# Changelog

All notable changes to this project are documented in this file.

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

