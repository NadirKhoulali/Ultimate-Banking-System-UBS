# ATM Flow

## Access Sequence

1. Open ATM block.
2. Account selection phase:
   - if a primary account exists, it is preselected
   - player can switch to another owned account
3. PIN phase:
   - no PIN set: player must create and confirm a 4-digit PIN
   - PIN set: player must authenticate with the 4-digit PIN
4. Main ATM menu opens after successful PIN auth.

## Main ATM Actions

- `Balance Inquiry`
- `Withdraw Cash`
- `Deposit Cash`
- `Transfer Funds`
- `Transaction History`
- `Account Settings`
- `Pay Requests`

## Withdraw Rules

Validation path:

- account ownership
- account freeze state
- per-transaction ATM limit
- daily ATM limit
- balance availability
- bill dispense feasibility

Success result:

- balance decreases
- daily withdrawn amount updates
- bill items are dispensed
- transaction entry is recorded

## Deposit Rules

Validation path:

- account ownership
- account freeze state
- required bill denominations in inventory
- exact amount representation by available bills

Success result:

- bills are removed
- balance increases
- transaction entry is recorded

## Transfer Rules

- sender must belong to requesting player
- sender and receiver must be different accounts
- frozen accounts are blocked
- result logs transactions on both sides
- recipient receives balance-changed notification

## Pay Request UI

`Pay Requests` includes:

- inbox list
- `Accept`, `Decline`, and `Choose Account` controls
- `Create Pay Request` action

Create flow:

- choose online target player (search + scroll list)
- enter amount
- choose destination account (primary is default)
- submit request

