# Admin Commands

Admin level required: permission level `3`.

## Core Admin Root

- `/ubs admin ...`
- `/bank admin ...`
- `/centralbank ...`
- `/ubs centralbank interest set <rate>`
- `/ubs bank save`
- `/ubs bank rename <newName>`
- `/ubs money deposit <accountId> <amount>`
- `/ubs money withdraw <accountId> <amount>`

## Account and Player Moderation

- `/ubs admin view <player>`
- `/ubs admin freeze <player> [reason]`
- `/ubs admin unfreeze <player>`
- `/ubs admin freeze account <accountId> [reason]`
- `/ubs admin unfreeze account <accountId>`

## Economy and Reporting

- `/ubs admin report`
- `/centralbank report`
- `/centralbank report history`
- `/centralbank ledger`
- `/centralbank ledger suspense`

## Loan and Schedule Overrides

- `/ubs admin loan pending`
- `/ubs admin loan approve <player>`
- `/ubs admin loan deny <player> [reason]`
- `/ubs admin schedule list`
- `/ubs admin schedule add <sourceAccountId> <targetAccountId> <amount> <frequencyTicks>`
- `/ubs admin schedule remove <paymentId>`

## Bank Governance and Compliance

- `/ubs admin applications`
- `/ubs admin applications approve <applicationId>`
- `/ubs admin applications deny <applicationId> [reason]`
- `/ubs admin appeals`
- `/ubs admin appeal <appealId> approve [reason]`
- `/ubs admin appeal <appealId> deny [reason]`
- `/ubs admin reserve <bankName>`
- `/ubs admin compliance [bankName]`
- `/ubs admin audit <bankName>`
- `/ubs admin suspend <bankName> [reason]`
- `/ubs admin unsuspend <bankName>`
- `/ubs admin revoke <bankName> [reason]`
- `/ubs admin unlock <bankName>`
- `/ubs admin bankrun <bankName>`
- `/ubs admin setcap <bankName> <amount>`
- `/ubs admin rateexempt <bankName>`
- `/ubs admin waivefee <player>`
- `/ubs admin deferrenewal <bankName>`
- `/ubs admin deferrenwal <bankName>` (legacy alias)
- `/ubs admin flags`

## Shop Admin

- `/ubs admin shop view <player>`
- `/ubs admin shop level set <shopId> <level>`
- `/ubs admin shop level add <shopId> <levels>`
- `/ubs admin shop level remove <shopId> <levels>`

## Central Bank Policy Controls

- `/centralbank rate`
- `/centralbank rate set <rate>`
- `/centralbank opm inject <amount>`
- `/centralbank opm withdraw <amount>`
- `/centralbank opm history`
- `/centralbank audit [bankName]`

## Import/Migration

- `/ubs admin import csv <path>`
- `/ubs admin import essentialsx <path>`
- `/ubs admin import cmi <path>`
- `/ubs admin import iconomy <path>`

## Audit Tags

Admin-driven balance changes are tagged in transaction history with admin-oriented descriptors, including manual deposit/withdraw actions.
