# Retail & Shop System

UBS now includes a large retail subsystem that sits on top of the banking layer.

## Scope

The shop stack includes:

- Shop creation/management in the desktop app (Business Manager PC)
- Claim and stockroom management
- Shelf/display inventory surfaces
- Cashier NPC interactions
- Payment terminal integration
- Webshop cart/checkout/order delivery flows
- Courier order board flows

## Main Blocks and Items

- `shop_shelf` / `tall_wall_shelf`
- `shop_selling_table` / `shop_selling_table_large`
- `shop_cooler`
- `modular_wall_display`
- `glass_counter_display` (+ open variants)
- `shopping_basket` + `shopping_bag`
- `shopping_basket_holder`
- `cardboard_box`
- `pallet`
- `cashier_spawn_egg`

Creative variants exist for several display blocks. The retail webshop catalog intentionally lists normal shop displays and logistics items only; creative invisible displays are not purchasable through the webshop.

## Desktop Shop App Capabilities

The desktop app supports owner/delegated-role actions for:

- shop creation, rename, type changes, and delete
- claim regions + stockroom regions
- claim tool and pallet-claim tool sessions
- checkout terminal assignment and cashier terminal linking
- cashier hiring/reporting and employee management
- shelf/stockroom reporting and restock actions
- order manager and order-item picker reports
- pallet assignment/unassignment for orders
- finance reporting and cash-vault withdrawal/settlement account actions
- permissions and delegated roles (`OWNER`, `MANAGER`, `BUILDER`, `STAFF`)
- shop hours, closed-hours deliverer stockroom access, and lighting controls
- webshop flows (filtered catalog, cart, account selection, delivery target selection, checkout, cancel/refund, replacement order)
- courier order board actions (report/accept/cancel)

## Retail Webshop

The Retail Webshop utility app uses the same desktop theme as the shop and order-board tools.

Catalog:

- Search by product name, category, or description.
- Filter by category.
- Sort by name, price, or category.
- Open product details without leaving the scrollable product panel.
- Add selected quantity to the basket from the product detail panel.

Cart:

- Each line item has its own add quantity, remove quantity, and remove item controls.
- The cart panel is paired with a checkout summary so players can see subtotal, surcharge, and checkout total before continuing.
- `Clear cart` removes the current basket.
- `Continue delivery` moves to checkout when the basket is valid.

Checkout:

- Payment account selection opens a modal with eligible banking accounts.
- Delivery target selection opens a three-step modal:
  1. choose the delivery shop/location
  2. choose random pallet or specific pallet mode
  3. when specific pallet mode is selected, search and select an assigned delivery pallet
- Random pallet mode confirms after the mode step if a valid assigned pallet is available.
- Specific pallet mode confirms after a pallet is selected.
- Review and place order shows the final account, target, delivery mode, subtotal, surcharge, and total before checkout.

Tracking:

- The old `Orders` view is now the `Tracking` view.
- Queued orders can be cancelled/refunded.
- Delivered orders show success state without a failure reason or fix card.
- Failed or completed orders can be replaced from their saved item/account/target data after a confirmation summary.
- Recovery details do not expose direct "open delivery pallet" actions; delivery pallets are physical assigned drop targets used by the server delivery flow.

## In-World Shopping Flow

High-level player loop:

1. Browse shelf/display inventory.
2. Use basket workflow to add/remove items.
3. Check out at cashier/terminal.
4. Payment settles through UBS account/terminal paths.

While a basket or checkout session is active, interaction restrictions are applied server-side to keep session state consistent.

## Delivery and Alerts

Retail workflows emit dedicated client payloads for:

- action alerts
- delivery alerts
- delivery info board updates
- setup objective overlays
- stockroom locate rendering

## Related Systems

- [Payment Terminal Guide](Payment-Terminal-Guide.md)
- [Bank Owner PC](Bank-Owner-PC.md)
- [Configuration](Configuration.md)
