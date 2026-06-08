# Ultimate Banking System (UBS) - Player Guide

This guide covers the current playable UBS systems and command surface for players and server operators.

## What UBS Adds

- Banking core:
  - multi-account support (`checking`, `saving`, `moneymarket`, `certificate`)
  - PIN-protected ATM access
  - transfers, transaction history, account primary selection
  - pay requests and account HUD monitoring
- Physical legal tender:
  - bills: `$1`, `$2`, `$5`, `$10`, `$20`, `$50`, `$100`
  - coins: `$0.01`, `$0.05`, `$0.10`, `$0.25`, `$0.50`
- Instruments and cards:
  - `bank_note`
  - `cheque`
  - `credit_card`
- Payment systems:
  - `payment_terminal` block (merchant checkout + redstone output)
  - `handheld_payment_terminal` (portable player-to-player checkout)
  - bank teller cash-out (bills + coins)
- Retail/shop stack:
  - shelf/display/cooler/table blocks
  - shopping baskets/bags
  - cashier NPC flow
  - stockroom + pallet + order workflows
  - webshop/courier board support
- Other gameplay systems:
  - safe box storage per account type
  - pickpocket system (player opt-in/out toggle)
  - world cash economy (structure loot, mob drops, configurable death cash drops)

## Quick Start

1. Place an ATM or use one in-world.
2. Create an account if needed:
   - `/account open checking "Central Bank"`
3. Open ATM and select account.
4. Set/confirm your 4-digit PIN on first login.
5. Use ATM actions (balance, withdraw, deposit, transfer, history).

## ATM Features

- account selection
- balance inquiry
- withdraw (whole-dollar amounts, bills only)
- deposit (bills + coins, exact amount required)
- transfer
- transaction history
- account settings (including PIN change)
- pay request inbox + creation

## Physical Cash Rules

### Withdraw

- ATM dispenses bills only.
- Denomination breakdown is highest-first: `$100`, `$50`, `$20`, `$10`, `$5`, `$2`, `$1`.

### Deposit

- UBS scans inventory and offhand for legal tender cash.
- Deposit only succeeds if an exact denomination combination exists.

### Teller Cash-Out

- Teller can dispense bills and coins.
- Teller cap is controlled via bank limit type `teller`.

## Payment Terminal and Handheld

### Terminal Block

- Right-click: pay configured amount.
- Shift + right-click: open config (owner/OP).
- Payment source:
  - held valid credit card account, else primary account.
- Terminal shows success/denied result and enforces a short interaction lock.
- Optional idle/success/failure redstone output.

### Handheld Terminal

- Hold item and right-click target player to charge.
- Shift + right-click opens handheld config.
- Uses same payment source rules as terminal.
- No redstone controls.

## Retail and Shop Systems

Retail features now include:

- shop shelves/tables/coolers/displays
- shopping basket session flow
- cashier-led checkout
- stockroom + pallet assignment + restock loops
- webshop order/cart/delivery flows

For shop management details:

- `docs/wiki/Retail-Shop-System.md`
- `docs/wiki/Bank-Owner-PC.md`

## Pickpocket

- Default keybind is a Shift-modified chord (default key `F`, so `Shift + F`).
- Toggle participation:
  - `/account pickpocket toggle`
  - `/account pickpocket status`

If disabled, you cannot steal and cannot be targeted.

## Safe Box and HUD

- `/account safebox list`
- `/account safebox deposit`
- `/account safebox withdraw <slot>`
- `/account hud toggle`
- `/account hud primary`
- `/account hud account <accountId>`

## Player Command Highlights

### Account

- `/account open <accountType> [certificateTier] <bankName>`
- `/account close <bankName>`
- `/account info`
- `/account info list`
- `/account info bank <bankName>`
- `/account info <accountId>`
- `/account balance`
- `/account primary set <accountId>`
- `/account primary bank <bankName>`
- `/account transfer <fromAccountId> <toAccountId> <amount>`
- `/account transfer bank <fromBank> <toBank> <amount>`
- `/account send <player> <amount> [bankName]`
- `/account payrequest <player> <amount> [destinationAccountId]`
- `/account transaction <transactionId>`
- `/account transaction list <accountId>`
- `/account shop pay <amount> [shop]`
- `/account note write <amount>`
- `/account cheque write <player> <amount>`
- `/account loan request|confirm|status ...`
- `/account cd break|confirm ...`
- `/account joint ...`
- `/account business ...`

### Bank

- `/bank list`
- `/bank create <name> [ownershipModel]`
- `/bank info <bankName>`
- `/bank reserve`
- `/bank dashboard`
- `/bank accounts`
- `/bank limit set <type> <amount>`
- `/bank role ...`
- `/bank shares ...`
- `/bank cofounder ...`
- `/bank hire|fire|employees`
- `/bank lend ...`
- `/bank loan ...`
- `/bank appeal <message>`
- `/bank heist start <bankName>` (Coming Soon)

### Session Control

- `/cashier cancel`
- `/bankteller cancel`

## Admin Commands (Permission Level 3)

- `/ubs admin view <player>`
- `/ubs admin freeze|unfreeze ...`
- `/ubs admin applications ...`
- `/ubs admin appeals ...`
- `/ubs admin import csv|essentialsx|cmi|iconomy <path>`
- `/centralbank rate [set <rate>]`
- `/centralbank opm inject|withdraw|history ...`
- `/centralbank audit [bankName]`
- `/centralbank report [history]`
- `/centralbank ledger [suspense]`

## Migration Import Notes

- `csv` supports:
  - `player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history`
- Optional `history` format:
  - `timestamp|signedAmount|description;timestamp|signedAmount|description`
- `essentialsx` and `cmi` accept userdata folder or single YAML file.
- `iconomy` accepts `player,balance` or `player:balance`.

## Troubleshooting

### "No accounts found"

Create an account first:

- `/account open checking "Central Bank"`

### "Not enough cash on hand"

You do not carry enough legal tender items for the requested action.

### "Cannot form that exact amount"

You may have enough total value, but not a matching denomination combination.

### Transfer/payment fails

Common causes:

- insufficient balance
- invalid destination account
- frozen account
- daily/transaction limit hit

## Suggested Server Setup Flow

1. Place ATMs and initial terminals.
2. Tell players to create accounts and set PINs.
3. Configure limits/rates in common config.
4. Set central-bank policy values (`rate`, reserve/tax intervals, audit windows).
5. Decide whether to enable pickpocket and world-cash systems for your server style.
