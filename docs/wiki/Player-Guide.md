# Player Guide

## Getting Started

1. Create an account:
   - `/account open`
2. Open an ATM block.
3. Select an account.
4. Complete PIN flow:
   - first-time account: set + confirm a 4-digit PIN
   - existing account: enter PIN

## ATM Features

- Balance inquiry
- Cash withdrawal (whole dollars)
- Cash deposit (bills + coins, exact inventory match)
- Transfer funds
- Transaction history
- Account settings
- Pay requests (inbox + create)

## Pay Requests

Send request:

- `/account payrequest <player> <amount> [destinationAccountId]`

Receive request:

- If you have a primary account, chat offers `Accept`, `Decline`, `Choose Account`.
- If you do not have a primary account, UBS provides clickable account options with balances.

ATM side:

- open `Pay Requests`
- review incoming requests
- accept/decline or choose a specific paying account
- create outgoing requests from the same menu

## Physical Cash Rules

Withdraw:

- ATM dispenses bills only (`$1, $2, $5, $10, $20, $50, $100`).
- Overflow follows normal item-drop behavior.

Deposit:

- UBS scans inventory/offhand for legal tender cash items (bills + coins).
- Deposit requires exact denomination fit for requested amount.

Bank Teller:

- Teller cash-out can dispense bills + coins.
- Teller withdrawal limit can be configured per bank (`teller` bank limit type).

Legal tender list with textures:

- [Currency & Legal Tender](Currency-Legal-Tender.md)

## Payment Terminal

- Place `payment_terminal` block for merchant checkout.
- Right-click to pay.
- Shift + right-click opens config (owner/OP only).
- Terminal payment source:
  - held valid credit card account, else primary account.
- Result feedback:
  - success/denied display state with temporary interaction lock.
- Optional redstone output for success/failure/idle.

Handheld mode:

- Use `handheld_payment_terminal` to charge players directly.
- While aiming at a player with handheld equipped, HUD panel shows amount + target.
- Right-click player to charge that player.
- Shift + right-click to open handheld terminal config.
- Handheld has no redstone output controls.

Full guide:

- [Payment Terminal Guide](Payment-Terminal-Guide.md)

## Retail and Shop Systems

New retail surfaces include shelves, display tables, coolers, modular displays, shopping baskets/bags, pallets, and cashier NPC workflows.

High-level loop:

1. Browse shelf/display stock.
2. Add/remove items with basket flow.
3. Checkout via cashier/terminal.
4. Funds settle through UBS account/payment systems.

The desktop shop app also supports stockroom claims, pallet assignment, order flows, permissions, lighting/hours controls, and webshop/courier workflows.

Retail Webshop:

- Catalog search/filter/sort helps find checkout, display, shelving, and logistics items.
- Cart lines have per-item add quantity, remove quantity, and remove item controls.
- Checkout uses modals for payment account and delivery target selection.
- Delivery target selection is step-based: choose shop/location, choose random or specific pallet mode, then choose a pallet when specific mode is selected.
- Tracking shows queued and past orders. Successful orders do not show a failure reason; failed or completed orders can be replaced after confirmation.

See:

- [Retail & Shop System](Retail-Shop-System.md)
- [Bank Owner PC](Bank-Owner-PC.md)

## Pickpocket

- Client keybind defaults to `Shift + F` (rebindable key + Shift safety modifier).
- Players can opt in/out:
  - `/account pickpocket toggle`
  - `/account pickpocket status`

If disabled, you are immune and also cannot pickpocket others.

## HUD and Safety Deposit Boxes

- `/account hud toggle`
- `/account hud primary`
- `/account hud account <accountId>`
- `/account hud move <top-right|top-left|middle-right|middle-left|bottom-right|bottom-left>`

A bank's physical `Safe Box` teller tab is enabled only after its owner completes the vault setup. To inspect an assigned box:

1. Select an account at that bank's teller.
2. Open `Safe Box` and choose `Request to Open Safe Box`.
3. UBS reserves a private viewing room and moves you and the teller to it.
4. Use the full-size physical tray for your exact assigned box.
5. Interact with the teller and confirm when finished; UBS restores the box and returns you.

Only the selected account's exact assigned box is accessible. Other interactions inside the viewing room are blocked. The session closes on completion, timeout, disconnect, death, dimension change, or server recovery. If service is unavailable, the bank owner must restore the missing premise exit, vault door, full row, viewing room, or Safe Access requirement; assignments and contents remain preserved.

Legacy safebox commands remain available where enabled:

- `/account safebox list`
- `/account safebox deposit`
- `/account safebox withdraw <slot>`

## World Cash Economy

Depending on server config, UBS can add:

- structure chest cash loot
- cash drops from mobs/villagers
- forced percentage cash drop on player death

## Smartphone

- Right-click a smartphone or press the rebindable phone key (default `[`).
- The phone does not pause the game and supports banking, per-account login, contacts, realtime/offline messages, pay requests, gifts, notes, settings, and Tap to Pay.
- First use requires a phone password.
- `Esc` navigates back, then puts the phone away from Home.

See [Smartphone](Smartphone.md).

## Bank Heists

Open the multiplayer heist planner with `/heist`. Build a crew, select an eligible bank premise, ready up, breach physical security, fill duffel bags, and extract before the timer expires. Use `/heist abandon` to leave.

See [Bank Heists](Bank-Heists.md).

## Limits and Security

- PIN must be exactly 4 digits.
- per-transaction ATM limit applies
- daily ATM withdrawal limits apply
- frozen accounts cannot withdraw, deposit, or transfer

## Useful Commands

- `/account info`
- `/account info list`
- `/account transfer <senderAccountUUID> <receiverAccountUUID> <amount>`
- `/account transaction list <accountUUID>`
- `/account shop pay <amount> [shop]`
- `/bank reserve`
- `/bank dashboard`
- `/heist`
- `/heist abandon`
