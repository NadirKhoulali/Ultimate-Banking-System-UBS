# Ultimate Banking System Wiki

This wiki tracks the current UBS feature set and command surface.

Current release target: `1.2.0`

## Start Here

- [Player Guide](Player-Guide.md)
- [ATM Flow](ATM-Flow.md)
- [Currency & Legal Tender](Currency-Legal-Tender.md)
- [Payment Terminal Guide](Payment-Terminal-Guide.md)
- [Bank Owner PC](Bank-Owner-PC.md)
- [Admin Commands](Admin-Commands.md)
- [Configuration](Configuration.md)
- [Migration Guide](Migration-Guide.md)
- [Developer API](Developer-API.md)
- [Developer Integration Tutorial](Developer-Integration-Tutorial.md)

## System Overview

UBS provides:

- account-based banking with PIN-protected ATM access
- physical USD bills and coins as legal tender
- ATM cash flow:
  - withdraw is bills only
  - deposit accepts bills + coins (exact match)
- bank teller cash-out supports bills + coins
- transfer history, limits, and anti-abuse controls
- pay request workflows in chat and ATM UI
- payment terminal block for merchant checkout with configurable redstone outputs
- handheld payment terminal for direct player-to-player checkout
- player-owned banks with governance, staffing, products, and reserve controls
- central-bank policy and settlement controls
- admin moderation, audit, and import tools

## Notes

- Daily withdrawal resets and many lifecycle systems are tick-driven.
- Interest, renewals, audits, and taxes are server-side scheduled flows.
- Keep config values aligned with your server economy scale before launch.
