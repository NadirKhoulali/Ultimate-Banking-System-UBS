# Bank Owner PC

The Bank Owner PC is the full-screen management interface for bank owners (and OP access to the Central Bank app).

## Access

- Place/use the Bank Owner PC block to open UBS Desktop.
- Bank apps shown on desktop are based on your ownership/role access.
- OP users (permission level `3`) also see and can open the Central Bank app.

## Desktop Behavior

- Desktop lists all accessible bank apps as launch buttons.
- `Create Bank` opens the in-UI bank creation flow.
- Max banks per player is enforced by config (`PLAYER_BANKS_MAX_BANKS_PER_PLAYER`).
- UI scale is forced to `2` while this screen is open, then restored on close.

## Bank Manager Layout

- Left nav: `Overview`, `Branding`, `Limits`, `Governance`, `Staffing`, `Lending`, `Compliance`
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

- hire/fire
- employee list

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

## Input Assistant

When focusing key fields (especially lending/limits), the output panel can show a short explanation and example input to help new players.

## Permissions

- Most mutation actions require owner-level access for that bank app.
- View-only actions can still be available for role-based access.

## Troubleshooting

- If market accept fails, verify offer is still `OPEN` and not expired.
- Offer acceptance requires a full UUID (the market `Copy ID` button provides this).
- If no apps are visible, verify your role/ownership and that player bank features are enabled in config.
