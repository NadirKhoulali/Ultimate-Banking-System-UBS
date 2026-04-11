# Player Guide

## Getting Started

1. Create an account:
   - `/account create CheckingAccount Central Bank`
2. Open an ATM block.
3. Select account and complete PIN flow:
   - If no PIN exists, set a 4-digit PIN (with confirmation).
   - If PIN exists, authenticate with the PIN.

## ATM Actions

- Balance Inquiry
- Withdraw Cash (whole dollars only)
- Deposit Cash (whole dollars only, exact bill matching required)
- Transfer Funds
- Transaction History
- Account Settings

## Physical Cash Rules

Withdraw:

- UBS gives bill items using available denominations.
- Inventory overflow is handled by normal item drop behavior.

Deposit:

- UBS checks bill items in your inventory.
- Deposit must be representable exactly by your bill denominations.

## Security and Limits

- PIN is exactly 4 digits.
- Per-account ATM transaction limit applies.
- Per-account daily ATM withdrawal limit applies.
- Frozen accounts cannot withdraw/deposit/transfer.

## Useful Player Commands

- `/account info`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/account transaction list <accountUUID>`
