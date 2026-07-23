# Safety Deposit Boxes

Safety-deposit service is a physical bank feature. A bank cannot lease boxes until its premises, vault, rows, staff, and private viewing infrastructure are valid.

## Required Setup

At least one vault must have all of the following:

1. A claimed bank premise with a valid outside exit.
2. A safe area nested inside that premise.
3. A complete loaded `bank_vault_door` multiblock inside the safe area.
4. At least one loaded deposit-row block whose usable positions are fully populated with box modules.
5. At least one current bank employee with `Safe Access` (operators satisfy this for the Central Bank).
6. At least one ready private viewing room associated with the premise.

Readiness is recalculated from current world state. Removing a required door, row, permission, or room suspends new/physical access but does not delete assignments, rent records, escrow, or customer contents. Restoring the requirement re-enables service.

## Deposit Rows

Each row block can hold up to four physical module positions. Modules can be small, medium, large, extra-large, or a cover where allowed. At least one row in a ready vault must be completely assignable; an empty cover cannot be used to bypass the only-row requirement.

The Safe app maps every loaded row and reports:

- position and dimension
- module types
- free, assigned, locked, covered, and unavailable positions
- owner and rental status for assigned boxes
- locate controls without exposing box contents

## Private Viewing Rooms

The former teller path-walking flow has been replaced by isolated viewing rooms.

For each room, the owner claims bounds and captures three anchors:

- customer position and facing
- teller position and facing
- full-size deposit-box tray display position and facing

Rooms cannot overlap and all anchors must remain inside the room. Multiple rooms can be configured as bank level capacity allows.

When a customer requests access:

1. UBS reserves one ready room and one teller.
2. The customer and teller are moved to their room anchors.
3. The customer's exact box is temporarily removed from its row; the row slot remains reserved and visibly open.
4. A full-size tray is rendered at the display anchor with the box inventory arranged in layers above it.
5. Only that customer can interact with that tray/box. Other room/world interactions are denied.
6. The customer interacts with the teller and confirms completion.
7. UBS restores the box, row door, teller, and both players' original state/position.

Timeout, death, disconnect, dimension change, server stop, or recovery also runs cleanup. A teller serves one customer at a time.

## Leasing and Pricing

The Bank Teller `Safe Box` tab shows available sizes and the bank's price per size. Sold-out sizes are disabled. A bank can price small, medium, large, and extra-large boxes independently.

Customers can inspect current leases and request a room from the same tab. A box remains associated with its account and bank.

## Safe Operations App

The Bank Owner PC Safe app includes:

- Box Wall: live physical row map and assignment status
- Private Viewing Rooms: claims, anchors, availability, rename/suspend/delete
- Access Logs: box, door, pallet, inventory, and safe interaction audit records
- Alarms: alarm state, default/custom sound, test/restart/stop controls
- Vault Storage: simplified safe-area map with rows, metal pallets, chests, counts, and value details
- Pricing Policy: size-specific rent configuration
- Locked Queue: overdue/locked review

Vault Storage values use actual cash value or shop-market statistics. Detail modals show item counts and distinguish pallets from other inventories; contents are never exposed through the public row ownership map.

## Premise Protection

Non-staff players inside a protected bank premise cannot break/place blocks, use destructive interactions, or damage the site with projectiles/explosions. Modded explosion events are handled where NeoForge exposes cancellable world/block damage hooks. Denied placement restores/synchronizes the player's held stack rather than consuming it.

## Commands

Legacy account commands remain for compatible worlds:

```text
/account safebox list
/account safebox deposit
/account safebox withdraw <slot>
```

Normal physical access should use the Bank Teller.

