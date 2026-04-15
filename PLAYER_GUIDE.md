# Ultimate Banking System (UBS) - Player Guide

This file explains the current playable features of UBS in a user-friendly way for players and server admins.

## What UBS Adds

- `ATM Machine` block: `ultimatebankingsystem:atm_machine`
- `Payment Terminal` block: `ultimatebankingsystem:payment_terminal`
- Legal tender bills:
  - `$1`: `ultimatebankingsystem:one_dollar_bill`
  - `$2`: `ultimatebankingsystem:two_dollar_bill`
  - `$5`: `ultimatebankingsystem:five_dollar_bill`
  - `$10`: `ultimatebankingsystem:ten_dollar_bill`
  - `$20`: `ultimatebankingsystem:twenty_dollar_bill`
  - `$50`: `ultimatebankingsystem:fifty_dollar_bill`
  - `$100`: `ultimatebankingsystem:hundred_dollar_bill`
- Legal tender coins:
  - `$0.01`: `ultimatebankingsystem:penny_coin`
  - `$0.05`: `ultimatebankingsystem:nickel_coin`
  - `$0.10`: `ultimatebankingsystem:dime_coin`
  - `$0.25`: `ultimatebankingsystem:quarter_coin`
  - `$0.50`: `ultimatebankingsystem:half_dollar_coin`
- Credit card item: `ultimatebankingsystem:credit_card`
- Bank note + cheque instruments:
  - `ultimatebankingsystem:bank_note`
  - `ultimatebankingsystem:cheque`
- Full account system with balance storage, transfers, PIN security, primary account flag, and transaction history.

Visual references:
- Currency texture catalog: `docs/wiki/Currency-Legal-Tender.md`
- Payment terminal guide: `docs/wiki/Payment-Terminal-Guide.md`

## Quick Start (Players)

1. Place an ATM or find one in the world.
2. Right-click the ATM to open UBS.
3. If you do not have an account yet, create one with:
`/account open checking "Central Bank"`
4. Open ATM again, choose your account, then use ATM actions.

## First-Time Important Notes

- New accounts no longer use a default PIN.
- On first ATM login, you are prompted to create a 4-digit PIN.
- Most ATM actions are disabled until an account is selected.
- ATM withdraw uses real bill items (physical currency).
- ATM deposit accepts legal tender bills + coins.
- Frozen accounts cannot withdraw, deposit, or transfer.
- Daily ATM withdrawal limits apply per account.

## ATM Features

### Select Account

- Opens a scrollable account list.
- Shows account type and bank name for each account.
- Choose account and press `Use`.

### Withdraw Cash

- Quick amount buttons: `$20`, `$50`, `$100`, `$200`, `$500`.
- Custom amount input is supported.
- Whole-dollar amounts only (`ATM dispenses bills only`).
- Success result:
- Balance decreases
- Bill items are given
- Overflow bills are dropped if inventory is full
- Transaction log entry: `ATM Cash Withdrawal`

### Deposit Cash

- Enter amount and confirm.
- Supports up to 2 decimal places.
- ATM checks your inventory and offhand for legal tender cash items (bills + coins).
- Deposit only succeeds if exact amount can be built from cash you carry.
- Success result:
- Required cash items are removed
- Balance increases
- Transaction log entry: `ATM Cash Deposit`

### Transfer Funds

- Enter recipient account UUID + amount.
- Uses selected account as sender.
- Includes confirmation step.
- Success result:
- Funds move from sender to recipient
- Transaction log entry: `ATM Transfer` on both accounts
- Recipient receives chat message via Balance Changed event

### Balance Inquiry

Shows:
- Account type
- Bank name
- Account ID
- Balance
- Creation date (formatted `MM/dd/yyyy HH:mm`)

### Transaction History

- Scrollable list, newest first.
- Maximum returned entries per request: `50`.
- Each entry shows:
- Date/time
- Description
- Amount (incoming green, outgoing red)
- Counterparty short ID (`ATM` when terminal is counterparty)

### Account Settings

`Info` tab:
- Account ID + copy button
- Account type
- Bank name
- Created date
- Primary toggle

`Security` tab:
- Current PIN
- New PIN
- Confirm new PIN
- Confirmation dialog before applying PIN change

## Payment Terminal

`Payment Terminal` block enables in-world merchant checkout.

Basic use:
- Right-click terminal to pay configured price.
- Shift + right-click terminal to configure it (owner or OP only).

Payment source:
- If you hold a valid UBS credit card, payment uses the card-linked account.
- Otherwise payment uses your primary account.

Terminal feedback:
- Terminal shows success/denied state for 2 seconds.
- During that period, interaction is blocked for all players.

Redstone:
- Terminal can output configurable success/failure signal strength.
- Optional idle signal can remain active continuously while terminal is idle.

## How Physical Cash Works

### ATM withdraw bill breakdown

ATM uses denominations in this order:
`$100, $50, $20, $10, $5, $2, $1`

Example:
- Withdraw `137` -> `$100 x1, $20 x1, $10 x1, $5 x1, $2 x1`

### ATM deposit cash matching

ATM attempts to build your exact requested amount from your current legal tender cash.

Example:
- Deposit `37.41` can work with `20 + 10 + 5 + 2 + 0.25 + 0.10 + 0.05 + 0.01`.

If exact combination is impossible, deposit is rejected.

### Bank Teller cash-out

Bank Teller cash payout can dispense bills and coins.
Server owners can set a higher teller counter limit from bank limits (`teller` type).

## Player Commands (`/account`)

### Help

- `/account`
- `/account help`

### Account info

- `/account info`
Shows primary account details.

- `/account info list`
Shows all your accounts.

- `/account info bank <Bank Name>`
Shows your account at a specific bank.

- `/account info <accountUUID>`
Shows specific account details.

### Account creation

- `/account open <accountType> [certificateTier] <bankName>`

Valid account types:
- `checking`
- `saving`
- `moneymarket`
- `certificate`

### Transfers

- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`

### Transaction lookup

- `/account transaction <transactionUUID>`
- `/account transaction list <accountUUID>`

### Account management

- `/account delete <accountUUID>`
Shows clickable confirmation in chat before final deletion.

- `/account primary set <accountUUID>`
Sets one account as primary and clears primary on your other accounts.

## Admin Commands (`/ubs`, permission level 3)

- `/ubs centralbank`
- `/ubs centralbank interest set <rate>`
- `/ubs bank save`
- `/ubs bank rename <new name>`
- `/ubs money deposit <accountUUID> <amount>`
- `/ubs money withdraw <accountUUID> <amount>`
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

Alias:
- `/bank admin ...` supports the same admin subcommands.

## Migration Import Notes

- `csv` format supports:
`player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history`
- `history` is optional:
`timestamp|signedAmount|description;timestamp|signedAmount|description`
- `essentialsx` and `cmi` imports accept either a userdata folder or a single `.yml/.yaml` file.
- `iconomy` import accepts file lines in `player,balance` or `player:balance`.
- Every import command reports created/updated/failed counts and logs detailed warnings to server logs.

## Server Config Options

- `TransactionsPerMinute` (default `10`)
Per-account outgoing transfer limit.

- `DefaultATMWithdrawalLimit`
Base per-transaction ATM withdrawal cap.

- `DailyWithdrawalLimit`
Per-account ATM withdrawal cap per Minecraft day.

- `AutoSaveIntervalMinutes`
Interval for scheduled banking data dirty-mark autosave.

- `SavingsInterestIntervalTicks`
Interval for scheduled savings interest payout.

- `AllowBankCustomInterestRate`
Enables custom bank interest control.

- `ServerInterestRate`
Server default interest rate value.

- `FederalFundsRate`
Economic reference rate value.

- `MinCustomBankInterestRate`
Minimum allowed custom rate.

- `MaxCustomBankInterestRate`
Maximum allowed custom rate.

- `CurrencySymbol`
Display symbol used in textual outputs.

- `CurrencyName`
Display currency name used in textual outputs.

## Chat Notifications

When balance-change events fire, UBS sends chat updates to account owner:

- Positive change message (deposit-style)
- Negative change message (withdrawal-style)

Recipient of successful ATM transfer receives a positive balance-change chat message.

## Data Saving

- Account and transaction data is world-persistent.
- Stored in world saved data key: `ultimate_banking_system`.

## Current Known Behavior / Limitations

- ATM withdraw supports whole-dollar amounts only (bills only).
- ATM deposit supports up to 2 decimals and uses UBS bills + coins.
- No crafting recipes are currently present in this codebase for ATM, terminal, or cash items.
- If no account exists, ATM actions remain unavailable.
- Account creation requires existing bank name.
- Default bank is `Central Bank` unless renamed by admin.

## Troubleshooting

### "No accounts found"

Create one account first:
`/account open checking "Central Bank"`

### "Not enough cash on hand"

You do not carry enough legal tender cash items for requested deposit.

### "Cannot form that exact amount"

You have enough total value, but wrong denominations for exact amount.

### "Transfer failed" or transfer speed warning

Possible causes:
- Not enough balance
- Invalid recipient account
- Per-account transfer rate limit (`TransactionsPerMinute`) reached

## Suggested Setup Flow (Server Owners)

1. Place ATM machines in spawn/city/bank areas.
2. Tell players to create/open accounts with `/account open ...`.
3. Tell players to set a 4-digit PIN on first ATM use.
4. Adjust config and admin rates as needed.
