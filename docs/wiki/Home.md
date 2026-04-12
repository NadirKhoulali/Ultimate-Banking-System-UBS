# Ultimate Banking System Wiki

This wiki tracks the current UBS feature set and command surface.

## Start Here

- [Player Guide](Player-Guide.md)
- [ATM Flow](ATM-Flow.md)
- [Admin Commands](Admin-Commands.md)
- [Configuration](Configuration.md)
- [Migration Guide](Migration-Guide.md)

## System Overview

UBS provides:

- account-based banking with PIN-protected ATM access
- physical cash bills for ATM withdraw/deposit
- transfer history, limits, and anti-abuse controls
- pay request workflows in chat and ATM UI
- player-owned banks with governance, staffing, products, and reserve controls
- central-bank policy and settlement controls
- admin moderation, audit, and import tools

## Notes

- Daily withdrawal resets and many lifecycle systems are tick-driven.
- Interest, renewals, audits, and taxes are server-side scheduled flows.
- Keep config values aligned with your server economy scale before launch.
