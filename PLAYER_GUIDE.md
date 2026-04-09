# Ultimate Banking System (UBS) - Player Guide

This file explains the current playable features of UBS in a user-friendly way for players and server admins.

## What UBS Adds

- `ATM Machine` block: `ultimatebankingsystem:atm_machine`
- Bill items:
- `$1` bill: `ultimatebankingsystem:one_dollar_bill`
- `$2` bill: `ultimatebankingsystem:two_dollar_bill`
- `$5` bill: `ultimatebankingsystem:five_dollar_bill`
- `$10` bill: `ultimatebankingsystem:ten_dollar_bill`
- `$20` bill: `ultimatebankingsystem:twenty_dollar_bill`
- `$50` bill: `ultimatebankingsystem:fifty_dollar_bill`
- `$100` bill: `ultimatebankingsystem:hundred_dollar_bill`
- Legacy item: `ultimatebankingsystem:cash`
- Full account system with:
- Balance storage
- Transfer support
- PIN changes
- Primary account flag
- Transaction history

## Quick Start (Players)

1. Place an ATM or find one in the world.
2. Right-click the ATM to open UBS.
3. If you do not have an account yet, create one with:
`/account create CheckingAccount Central Bank`
4. Open ATM again, choose your account, then use ATM actions.

## First-Time Important Notes

- New accounts currently start with default PIN: `test`.
- Change your PIN in `Account Settings` immediately.
- Most ATM actions are disabled until an account is selected.
- ATM withdraw/deposit uses real bill items (physical currency).

## ATM Features

### Select Account

- Opens a scrollable account list.
- Shows account type and bank name for each account.
- Choose account and press `Use`.

### Withdraw Cash

- Quick amount buttons: `$20`, `$50`, `$100`, `$200`, `$500`.
- Custom amount input is supported.
- Whole-dollar amounts only.
- Success result:
- Balance decreases
- Bill items are given
- Overflow bills are dropped if inventory is full
- Transaction log entry: `ATM Cash Withdrawal`

### Deposit Cash

- Enter amount and confirm.
- Whole-dollar amounts only.
- ATM checks your inventory and offhand for bill items.
- Deposit only succeeds if exact amount can be built from bills you carry.
- Success result:
- Required bill items are removed
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

## How Bills Work

### Withdraw bill breakdown

ATM uses denominations in this order:
`$100, $50, $20, $10, $5, $2, $1`

Example:
- Withdraw `137` -> `$100 x1, $20 x1, $10 x1, $5 x1, $2 x1`

### Deposit bill matching

ATM attempts to build your exact requested amount from your current bills.

Example:
- Deposit `37` can work with `20 + 10 + 5 + 2`.

If exact combination is impossible, deposit is rejected.

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

- `/account create <AccountType> <Bank Name>`

Valid account types:
- `CheckingAccount`
- `SavingAccount`
- `MoneyMarketAccount`
- `CertificateAccount`

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

## Server Config Options

- `TransactionsPerMinute` (default `10`)
Per-account outgoing transfer limit.

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

## Chat Notifications

When balance-change events fire, UBS sends chat updates to account owner:

- Positive change message (deposit-style)
- Negative change message (withdrawal-style)

Recipient of successful ATM transfer receives a positive balance-change chat message.

## Data Saving

- Account and transaction data is world-persistent.
- Stored in world saved data key: `ultimate_banking_system`.

## Current Known Behavior / Limitations

- ATM withdraw/deposit supports whole-dollar amounts only.
- ATM physical cash flow only works with UBS bill items.
- No crafting recipes are currently present in this codebase for ATM or bills.
- If no account exists, ATM actions remain unavailable.
- Account creation requires existing bank name.
- Default bank is `Central Bank` unless renamed by admin.

## Troubleshooting

### "No accounts found"

Create one account first:
`/account create CheckingAccount Central Bank`

### "Not enough cash on hand"

You do not carry enough bill items for requested deposit.

### "Cannot form that exact amount"

You have enough total value, but wrong denominations for exact amount.

### "Transfer failed" or transfer speed warning

Possible causes:
- Not enough balance
- Invalid recipient account
- Per-account transfer rate limit (`TransactionsPerMinute`) reached

## Suggested Setup Flow (Server Owners)

1. Place ATM machines in spawn/city/bank areas.
2. Tell players to create accounts with `/account create ...`.
3. Tell players to change default PIN (`test`) immediately.
4. Adjust config and admin rates as needed.
