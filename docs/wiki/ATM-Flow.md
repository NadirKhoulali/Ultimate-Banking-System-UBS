# ATM Flow

## Login Sequence

1. Open ATM.
2. Select account:
   - If primary account exists, it is preselected.
   - Player can switch account at any time.
3. PIN step:
   - No PIN set: prompt to create + confirm.
   - PIN set: prompt to enter PIN.
4. On success, main ATM menu opens.

## Withdraw

- Amount must be a positive whole number.
- Validation order:
  - ownership check
  - account freeze check
  - per-transaction ATM limit check
  - daily ATM limit check
  - balance check
  - bill-dispense feasibility check
- On success:
  - account balance decreases
  - daily withdrawn counter increases
  - bills are given
  - transaction log entry is added

## Deposit

- Amount must be a positive whole number.
- Validation order:
  - ownership check
  - account freeze check
  - enough bills in inventory
  - exact bill combination available
- On success:
  - bills are removed
  - balance increases
  - transaction log entry is added

## Transfer

- Sender must belong to requesting player.
- Sender and recipient must be different accounts.
- Frozen sender/recipient accounts are rejected.
- On success:
  - transaction is logged on both accounts
  - recipient receives balance-changed chat event
