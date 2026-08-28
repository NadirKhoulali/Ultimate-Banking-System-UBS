# Ultimate Banking System 2.1.0

UBS 2.1 makes the official Minecraft server the reliable source of truth for connected Ages of War economy features. It adds the primitives a trusted server-side adapter needs without embedding a web server or exposing banking internals.

## Headline Features

- Institutional accounts for nation treasuries and system escrow
- Explicit player grants with view, deposit, withdraw, and manage capabilities
- Durable idempotent command receipts that survive restarts
- Monotonic revisions and complete reconciliation snapshots
- Atomic matched war-stake escrow with full release or refund
- Trusted operations for treasury provisioning, grant changes, freezing, and approved adjustments

## Integration Safety

- UBS remains the sole monetary authority; external databases hold replaceable projections, not balances.
- Every mutation uses a stable idempotency key. Exact retries never double-charge an account.
- Player-scoped snapshots do not leak other players' grants or unrelated escrow.
- Nation payout limits come from current UBS roles and are checked again at execution time.
- Primary accounts remain personal player-owned accounts.
- The trusted economy module is an in-process Java interface and opens no public network port.

## Upgrade Notes

- Back up the world before installing the release.
- Existing player accounts load as `PLAYER` principals without migration commands.
- New institution and escrow records are added lazily by trusted integrations.
- `AccountTransactionLogLimit` now defaults to `10,000` and accepts up to `50,000`; review world-save size and tune this value for your server activity.
- Integrations using the authoritative economy module must require UBS `2.1.0` or newer and call it only on the logical server thread.

See [Developer API](../wiki/Developer-API.md) and [Developer Integration Tutorial](../wiki/Developer-Integration-Tutorial.md) for the complete interface contract.
