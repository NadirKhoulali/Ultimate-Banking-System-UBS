# Admin Commands

Admin level required: permission level `3`.

## Core Admin Commands

- `/ubs centralbank`
- `/ubs centralbank interest set <rate>`
- `/ubs bank save`
- `/ubs bank rename <new name>`
- `/ubs money deposit <accountUUID> <amount>`
- `/ubs money withdraw <accountUUID> <amount>`

## Account Moderation

Also available under `/bank admin ...` alias.

- `/ubs admin view <player>`
- `/ubs admin freeze <player> [reason]`
- `/ubs admin unfreeze <player>`
- `/ubs admin freeze account <accountUUID> [reason]`
- `/ubs admin unfreeze account <accountUUID>`
- `/ubs admin report`

## Import Commands

- `/ubs admin import csv <path>`
- `/ubs admin import essentialsx <path>`
- `/ubs admin import cmi <path>`
- `/ubs admin import iconomy <path>`

CSV expected columns:

`player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history`

Rules:

- Header is optional.
- `pin` empty or 4 digits.
- Negative balances are rejected.
- Existing matching accounts are updated; otherwise created.
- `history` is optional and formatted as:
`timestamp|signedAmount|description;timestamp|signedAmount|description`

Plugin import notes:

- `essentialsx` and `cmi` support importing from userdata YAML files.
- `iconomy` supports `player,balance` or `player:balance` file formats.
- All import commands print a summary and log warning details for failed rows/files.

## Audit Logging

Admin balance actions create transaction entries:

- `ADMIN_DEPOSIT by <actor>`
- `ADMIN_WITHDRAW by <actor>`
