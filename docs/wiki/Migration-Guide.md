# Migration Guide

UBS can import account data from common server economy/banking plugins.

## Supported Sources

- Generic CSV export
- EssentialsX userdata (`.yml/.yaml`)
- CMI userdata (`.yml/.yaml`)
- iConomy text/CSV exports

## Commands

- `/ubs admin import csv <path>`
- `/ubs admin import essentialsx <path>`
- `/ubs admin import cmi <path>`
- `/ubs admin import iconomy <path>`

Alias: `/bank admin import ...`

## CSV Format

Header is optional. Columns:

`player_uuid_or_name,bank_name,account_type,balance,pin,is_primary,history`

Notes:

- `player_uuid_or_name`: UUID or player name.
- `account_type`: enum name or display label.
- `pin`: blank or 4 digits.
- `is_primary`: optional (`true/false/yes/no/1/0`).
- `history`: optional:
`timestamp|signedAmount|description;timestamp|signedAmount|description`
- Timestamp format: `YYYY-MM-DDTHH:mm:ss`.
- Signed amount: `+` incoming, `-` outgoing.

## EssentialsX / CMI

Use a userdata folder path or a single YAML file path.
Importer reads values like `money` / `balance` and maps them to UBS accounts.

## iConomy

Importer accepts lines in either format:

- `player,balance`
- `player:balance`

Player can be UUID or name.

## Import Feedback

Each import command provides:

- created account count
- updated account count
- imported history entry count
- failed row/file count
- first errors in chat

Detailed warnings are also logged in server logs.

## Limitations

- Histories are imported when source data provides a compatible field/value.
- Some plugins do not store transaction history in the main balance files.
- Unsupported fields are ignored instead of failing the whole import run.
