# Claim Tools

UBS uses one server-authoritative claim-mode framework for shop plots, stockrooms, delivery pallets, bank premises, safe areas, and private viewing rooms.

## Supported Modes

- Shop Plot
- Shop Stockroom
- Delivery Pallets
- Bank Premise
- Premise Exit
- Safe Area
- Viewing Room
- Customer, Teller, and Deposit Box viewing-room anchors

Each management screen opens the correct mode; players do not need separate permanent tools in their inventory.

## Claim HUD

While claim mode is active, UBS temporarily hides the normal hotbar and compatible HUD overlays and displays a purpose-built interface containing:

- current operation and owner/shop/bank
- add/remove mode
- Pos 1 and Pos 2 or staged target
- selected volume/capacity
- nearby visible claims
- apply, clear, overlay, save/exit, and discard controls
- current validation result

Hold `Tab` to expose pointer-driven controls. World movement remains available when the controls are not capturing input.

## Selection

- Left-click selects Pos 1.
- Right-click selects Pos 2.
- Position/facing tools capture the player's current position and look direction.
- Delivery-pallet mode stages individual pallet blocks before saving.
- Apply validates the live world and commits atomically.

After a successful apply, the exit confirmation says `Save & Exit` even though the change is already durable. This makes the saved state explicit. Delivery-pallet add/remove selections remain visibly green/red until saved.

## Outlines

Claims use grounded tactical line rendering derived from the heist extraction renderer, not vanilla barrier particles. Outlines follow the selected cuboid and nearby claims.

By default, a player sees only claims owned by that player. Servers can set `ClaimOutlinesShowAllPlayers=true` to show everyone. Visibility does not weaken collision protection: a new claim that overlaps another owner's shop or bank claim is rejected even when that claim is hidden.

## Safety

- Target blocks must match the player's current crosshair and interaction range.
- Claims time out after five minutes of inactivity/session loss.
- Server stop and disconnect clear temporary sessions.
- Foreign-owner collision checks run again when applying.
- Admin-authorized bank premise operations can bypass gameplay ownership blockers but still reject structurally invalid data.

