**Currency Name: US Dollar (The Standard)**

Overview

This document records the currency decision for the Ultimate Banking System (UBS) mod and provides a small config schema so server admins can customise currency behaviour.

Decision summary (final)

- Currency name: US Dollar
- Currency symbol: $
- Currency model: Virtual currency (primary). Item-based tier support is available via configuration.

Rationale

- A virtual currency is the simplest and most flexible for banking operations (accounts, transfers, interest). It avoids item duplication and sync complexity.
- Item-based currency is supported as a configurable alternative for servers that want tangible coin items and in-world money mechanics.

Currency contract (inputs/outputs)

- Inputs: amount (integer or decimal depending on decimal_places), optional item representation when item-based.
- Outputs: normalized base unit (integer smallest unit), display string with symbol and formatted decimals.
- Errors: invalid currency type, unknown tier item, overflow on conversions.

Edge cases considered

- Very large balances (use long integer representation for base units).
- Fractional amounts (controlled via decimal_places).
- Item stacking and inventory limits when using item-based currency (server admin responsibility when enabling item-based currency).

Currency configuration (TOML)

This is the schema exposed in the mod config file (see `run/config/ultimatebankingsystem-common.toml`). Server admins can edit these values.

[currency]
# name: human-readable currency name
# symbol: short symbol used in UI (may be multi-character)
# type: "virtual" or "item"
# base_unit: how many smallest units make 1 displayed unit (e.g. 100 for cents)
# decimal_places: number of decimals to display
# item_id: (item-based only) the item id to use as the primary coin
# tiers: optional list of item-based tiers; each entry defines name, item id and value in base units

Example configuration (defaults shipped with mod):

[currency]
name = "US Dollar"
symbol = "$"
type = "virtual"
base_unit = 100
decimal_places = 2
# If you set type = "item" you should also set item_id or provide `tiers` below.

# Example item-based tiers (uncomment/use if type = "item")
# [[currency.tiers]]
# name = "copper"
# item = "ultimatebankingsystem:copper_coin"
# value = 1
# [[currency.tiers]]
# name = "silver"
# item = "ultimatebankingsystem:silver_coin"
# value = 100
# [[currency.tiers]]
# name = "gold"
# item = "ultimatebankingsystem:gold_coin"
# value = 10000
 
Acceptance criteria mapping

- Currency name and symbol documented: Done (name = "US Dollar", symbol = "$").
- Decision made: virtual currency by default; item-based supported via config: Done.
- If item-based, specific item and tier conversion rates documented: Done (example `currency.tiers` shown above).
- Currency config exposed in mod config file so server admins can customise it: Done (see `run/config/ultimatebankingsystem-common.toml` additions).

Notes for implementers

- Internally, store balances as a 64-bit integer counting base units (smallest indivisible unit).
- Formatting/display: divide stored integer by `base_unit` and display with `decimal_places`.
- If `type = "item"` the economy subsystem should implement conversion helpers:
  - fromItems(Map<itemId, count>) -> baseUnits
  - toItems(baseUnits) -> Map<itemId, count> using `tiers` to minimise item count

Next steps

- Wire the config into the mod's common config loader and expose an API to read currency settings at runtime.
- Implement item-based conversion helpers if `type = "item"` is enabled.

Revision history

- 2026-03-21: Initial document and config schema added.
