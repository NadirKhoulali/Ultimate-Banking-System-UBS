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

Creative variants exist for several display blocks.

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
- webshop flows (cart/account/shop/pallet selection, checkout, cancel)
- courier order board actions (report/accept/cancel)

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
