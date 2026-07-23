# Bank Owner PC

The Bank Owner PC (displayed in-game as **Business Manager PC**) is the full-screen management interface for bank owners and retail/shop management flows.

## Access

- Place/use the Bank Owner PC block to open UBS Desktop.
- Bank and shop apps shown on desktop are based on your ownership/role access.
- OP users (permission level `3`) also see and can open the Central Bank app.

## Desktop Behavior

- Desktop lists all accessible bank apps as launch buttons.
- `Create Bank` opens the in-UI bank creation flow.
- Max banks per player is enforced by config (`PLAYER_BANKS_MAX_BANKS_PER_PLAYER`).
- The desktop follows the supported custom PC scaling rules, with compact layouts for small framebuffers and Minecraft-style scale exemptions for inventory-style screens.
- Desktop, shop, order-board, and retail-webshop controls use the same dark UBS commerce theme so app chrome and modals stack consistently.

Shop-capable desktop actions are role-gated (`OWNER`, `MANAGER`, `BUILDER`, `STAFF`) and validated server-side.

## Bank Manager Layout

- Left nav: `Overview`, `Accounts`, `Safe`, `Premises`, `Branding`, `Limits`, `Governance`, `Staffing`, `Lending`, `Compliance`
- Top bar: current tool title + `Minimize` + `Refresh`
- Upper content area: section controls
- Lower content area: output panel / dashboard / card views

Both left nav and section controls are scrollable and clipped to stay inside their containers.

## Overview

- Overview opens tool cards:
  - `Info`
  - `Dashboard`
  - `Reserve`
  - `Accounts`
  - `Certificates`
  - `Loan Summary`
- Clicking an overview tool opens a detail mode with a back/refresh workflow.
- `Accounts` uses clickable user cards; clicking a card opens a dedicated account profile subview (with back navigation).

## Branding

- Set bank motto
- Set bank color (`#RRGGBB` or supported color names)

## Limits

Limits now use explicit type selectors (instead of free-text guessing):

- `Single`
- `Daily Player`
- `Daily Bank`
- `Teller Cash`

Then enter amount and apply.

Type behavior is functional server-side:

- `single` -> transaction cap
- `dailyplayer` -> per-player daily cap
- `dailybank` -> bank-wide daily cap
- `teller` -> max cash amount per teller withdrawal action (high-value teller counter limit)

## Governance

Actions shown in UI depend on ownership model and permissions:

- role assignment/revoke/list
- shares set/list
- cofounder add/list

## Staffing

- hire/fire player employees
- issue and inspect bound bank-teller NPCs
- view player employees and regular bank NPCs in separate roster sections
- grant or revoke per-employee `Safe Access`
- open teller details and inspect teller availability

`Safe Access` is required for customer deposit-box service; ownership alone does not silently bypass the employee permission. Permission-level-3 operators satisfy this requirement for the Central Bank.

## Premises and Safety Deposit Setup

`Premises` owns the bank's physical location records. Bank owners can:

- claim a premise by selecting two corners and an outside exit
- review bounds, exit, access mode, child safe areas, and vault readiness
- switch between `PUBLIC` and `STAFF_ONLY`
- update the outside exit
- delete only an empty custom premise with no assignments, routes, migration dependency, or active escort

Deleting a premise never cascades into customer box contents, assignments, rent/escrow data, routes, or world blocks. The server recomputes blockers when the action is confirmed.

Permission-level-3 operators can inspect and manage premises without opening an Owner PC through `/ubs admin bank premise`. Premise IDs in admin list/detail output are click-to-copy. Admin deletion is an explicit destructive force path: it bypasses Owner-PC deletion blockers, cancels matching escorts, and removes affected assignments, routes, contained legacy area records, and loaded physical row labels before deleting the premise. Account-held box contents and escrow records remain preserved. `add` and `exit` launch protected world-selection tools; structural bounds and target-existence checks remain because bypassing those would persist invalid world data.

The `Safe` panel contains safety-deposit setup and operations. Service stays disabled until at least one vault has all of these:

1. A safe area nested inside a claimed premise.
2. A loaded bank vault door in that safe area.
3. At least one deposit-row block with every slot physically filled.
4. At least one employee with `Safe Access`.
5. A ready private viewing room in the premise.

Removing a required door, row, room, or permission changes the vault back to unavailable without deleting customer data. Restoring the requirement makes it eligible again.

### Private Viewing Rooms

The former teller path-walking system is disabled. Owners now claim isolated viewing rooms and capture:

- customer position/facing
- teller position/facing
- deposit-box tray position/facing

Customers request access from the teller's `Safe Box` tab. UBS reserves a room/teller, temporarily removes the exact box from its physical row, presents a full-size tray and its contents in the room, restricts every unrelated interaction, then restores the room, teller, customer, row, and box when the customer confirms completion or the session is cleaned up.

### Safe Operations Tabs

- `Box Wall`: physical rows, sizes, assignment/rent/lock status, locate actions
- `Private Viewing Rooms`: room claims, anchors, availability, rename/suspend/delete
- `Access Logs`: customer, box, door, pallet, safe, and inventory activity
- `Alarms`: alarm state, default/custom OGG sound, test/restart/stop
- `Vault Storage`: simplified claim map, pallet/chest markers, counts, and market-value detail modals
- `Pricing Policy`: independent rent for every deposit-box size
- `Locked Queue`: overdue review and seizure confirmation

See [Safety Deposit Boxes](Safety-Deposit-Boxes.md).

## Lending

Base lending tools:

- borrow from central bank
- post interbank offer
- accept offer by UUID
- create loan product
- view products and summary

### Market Sub-Menu

Press `Market` to open dedicated interbank market mode:

- back to lending
- sort controls (`Amount`, `APR`, `Term`, `Lender`, `Offer ID`)
- order toggle (`High-Low` / `Low-High`)
- refresh market
- offer cards with:
  - lender
  - amount
  - APR
  - term
  - `Accept Offer`
  - `Copy ID`

`Accept Offer` opens a confirmation overlay before sending the action.
`Copy ID` copies the full offer UUID.

## Compliance

- submit appeal
- quick links to dashboard/reserve views

## Shop App Highlights

The same desktop environment now hosts retail/shop actions, including:

- shop create/overview/rename/type/delete
- claim + stockroom claim tools
- checkout terminal and cashier terminal link flows
- cashier staffing and role-based permission management
- shelf/stockroom reports and restock actions
- order manager + pallet assignment flows
- finance/cash-vault reporting and settlement account controls
- webshop catalog/cart/checkout/tracking controls and courier order board actions
- shop hours and lighting configuration

### Retail Webshop Utility

The Retail Webshop utility app includes:

- a filtered catalog with search, category filtering, sorting, product cards, and product-detail quantity controls
- a cart page with per-item quantity controls and a side checkout summary
- a checkout page with payment account selection, delivery target selection, pallet mode selection, pallet search, and final review/place order controls
- a tracking page for queued, delivered, failed, cancelled, and replacement orders

Payment account and delivery target selectors open modal overlays above the app and block background clicks until closed or confirmed.

## Input Assistant

When focusing key fields (especially lending/limits), the output panel can show a short explanation and example input to help new players.

## Permissions

- Most mutation actions require owner-level access for that bank app.
- View-only actions can still be available for role-based access.

## Troubleshooting

- If market accept fails, verify offer is still `OPEN` and not expired.
- Offer acceptance requires a full UUID (the market `Copy ID` button provides this).
- If no apps are visible, verify your role/ownership and that player bank features are enabled in config.
