# Player Guide

## Getting Started

1. Create an account:
   - `/account open checking "Central Bank"`
2. Open an ATM block.
3. Select an account.
4. Complete PIN flow:
   - first-time account: set + confirm a 4-digit PIN
   - existing account: enter PIN

## ATM Features

- Balance inquiry
- Cash withdrawal (whole dollars)
- Cash deposit (bills + coins, exact inventory match)
- Transfer funds
- Transaction history
- Account settings
- Pay requests (inbox + create)

## Pay Requests

Send request:

- `/account payrequest <player> <amount> [destinationAccountId]`

Receive request:

- If you have a primary account, chat offers `Accept`, `Decline`, `Choose Account`.
- If you do not have a primary account, UBS provides clickable account options with balances.

ATM side:

- open `Pay Requests`
- review incoming requests
- accept/decline or choose a specific paying account
- create outgoing requests from the same menu

## Physical Cash Rules

Withdraw:

- ATM dispenses bills only (`$1, $2, $5, $10, $20, $50, $100`).
- Overflow follows normal item-drop behavior.

Deposit:

- UBS scans inventory/offhand for legal tender cash items (bills + coins).
- Deposit requires exact denomination fit for requested amount.

Bank Teller:

- Teller cash-out can dispense bills + coins.
- Teller withdrawal limit can be configured per bank (`teller` limit type in Bank Limits).

Legal tender list with textures:

- [Currency & Legal Tender](Currency-Legal-Tender.md)

## Payment Terminal

- Place `payment_terminal` block for merchant checkout.
- Right-click to pay.
- Shift + right-click opens config (owner/OP only).
- Terminal payment source:
  - held valid credit card account, else primary account.
- Result feedback:
  - success/denied display state with temporary interaction lock.
- Optional redstone output for success/failure/idle.

Handheld mode:

- Use `handheld_payment_terminal` to charge players directly.
- While aiming at a player with handheld equipped, HUD panel shows amount + target.
- Right-click player to charge that player.
- Shift + right-click to open handheld terminal config.
- Handheld has no redstone output controls.

Full guide:

- [Payment Terminal Guide](Payment-Terminal-Guide.md)

## Limits and Security

- PIN must be exactly 4 digits.
- per-transaction ATM limit applies
- daily ATM withdrawal limit applies
- frozen accounts cannot withdraw, deposit, or transfer

## Useful Commands

- `/account info`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/account transaction list <accountUUID>`
- `/bank safebox list`
- `/bank safebox deposit`
- `/bank safebox withdraw <slot>`
- `/bank heist start <bankName>` (Coming Soon)
