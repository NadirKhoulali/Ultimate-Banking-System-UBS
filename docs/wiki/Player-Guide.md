# Player Guide

## Getting Started

1. Create an account:
   - `/account create CheckingAccount Central Bank`
2. Open an ATM block.
3. Select an account.
4. Complete PIN flow:
   - first-time account: set + confirm a 4-digit PIN
   - existing account: enter PIN

## ATM Features

- Balance inquiry
- Cash withdrawal (whole dollars)
- Cash deposit (whole dollars, exact bill match)
- Transfer funds
- Transaction history
- Account settings
- Pay requests (inbox + create)

## Pay Requests

Send request:

- `/payrequest <player> <amount> [destinationAccountId]`

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

- UBS dispenses available bill denominations.
- Overflow follows normal item-drop behavior.

Deposit:

- UBS scans your inventory for bill items.
- Deposit requires exact denomination fit.

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
- `/bank heist start <bankName>`
