# Ultimate Banking System (UBS)

UBS is a NeoForge `1.21.1` Minecraft mod that adds persistent banking accounts, ATM workflows, physical dollar bills, transfers, transaction history, PIN security, and admin banking controls.

## Current Features

- Multi-account support per player (`Checking`, `Saving`, `Money Market`, `Certificate`)
- ATM UI with:
  - account chooser
  - PIN authentication / PIN setup
  - balance inquiry
  - cash withdraw + cash deposit using real bill items
  - transfer funds
  - transaction history
  - account settings
- Physical USD bill items (`$1, $2, $5, $10, $20, $50, $100`)
- Transaction logging per account
- Transfer rate limiting (Bucket4j)
- Admin money controls and account moderation
- Data persistence via world `SavedData`

## New Admin/Backend Capabilities Added

- Account freeze/unfreeze state on each account
- Freeze enforcement in ATM deposit/withdraw/transfer and `/account transfer`
- Per-account daily ATM withdrawal tracking and daily limit enforcement
- Periodic autosave dirty-mark scheduling (`AutoSaveIntervalMinutes`)
- Periodic savings-interest payout scheduling (`SavingsInterestIntervalTicks`)
- Admin audit transaction tags for admin deposits/withdrawals (`ADMIN_DEPOSIT`, `ADMIN_WITHDRAW`)
- Admin account tools:
  - view player accounts
  - freeze/unfreeze player accounts
  - freeze/unfreeze specific account (legacy path)
  - economy report
  - multi-source import tools (`csv`, `essentialsx`, `cmi`, `iconomy`)

## Commands

### Player

- `/account create <AccountType> <Bank Name>`
- `/account info`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/account transaction <transactionUUID>`
- `/account transaction list <accountUUID>`

### Admin (`permission level 3`)

- `/ubs centralbank`
- `/ubs centralbank interest set <rate>`
- `/ubs bank save`
- `/ubs bank rename <new name>`
- `/ubs money deposit <accountUUID> <amount>`
- `/ubs money withdraw <accountUUID> <amount>`

New admin set (also available as `/bank admin ...` alias):

- `/ubs admin view <player>`
- `/ubs admin freeze <player> [reason]`
- `/ubs admin unfreeze <player>`
- `/ubs admin freeze account <accountUUID> [reason]`
- `/ubs admin unfreeze account <accountUUID>`
- `/ubs admin report`
- `/ubs admin import csv <path>`
- `/ubs admin import essentialsx <path>`
- `/ubs admin import cmi <path>`
- `/ubs admin import iconomy <path>`

## CSV Import Format

`/ubs admin import csv <path>` expects CSV rows with:

`player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history`

Notes:

- Header row is optional.
- `pin` can be empty or exactly 4 digits.
- `is_primary` is optional (`true`/`false`).
- `account_type` accepts enum names or labels.
- `history` is optional. Format: `timestamp|signedAmount|description;timestamp|signedAmount|description`
- `timestamp` uses ISO format (`YYYY-MM-DDTHH:mm:ss`), `signedAmount` positive=incoming, negative=outgoing.

Example:

```csv
player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history
8b0b6d69-2ac5-4fa0-86de-6f6b9d9e1e7e,Central Bank,CheckingAccount,1500,1234,true,2026-04-11T09:00:00|+1500|Initial migration
Steve,Central Bank,SavingAccount,3000,,false,
```

## Plugin Migration Sources

- `essentialsx`: pass a userdata folder or a single `.yml/.yaml` file. Reads `money`/`balance` and maps to UBS accounts.
- `cmi`: pass a userdata folder or a single `.yml/.yaml` file. Reads `money`/`balance` and maps to UBS accounts.
- `iconomy`: pass a text/CSV file with `player,balance` or `player:balance` per line.

Import commands print a summary in chat and also write summary + warnings to the server log.

## Config Highlights (`common` config)

- `TransactionsPerMinute`
- `DefaultATMWithdrawalLimit`
- `DailyWithdrawalLimit`
- `AutoSaveIntervalMinutes`
- `SavingsInterestIntervalTicks`
- `AllowBankCustomInterestRate`
- `ServerInterestRate`
- `FederalFundsRate`
- `MinCustomBankInterestRate`
- `MaxCustomBankInterestRate`
- `CurrencySymbol`
- `CurrencyName`

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

- Player guide: [`PLAYER_GUIDE.md`](PLAYER_GUIDE.md)
- Wiki pages for GitHub wiki publishing: [`docs/wiki`](docs/wiki)
