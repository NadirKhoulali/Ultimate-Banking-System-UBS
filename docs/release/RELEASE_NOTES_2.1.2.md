# Ultimate Banking System 2.1.2

UBS 2.1.2 updates the NeoEssentials leaderboard integration for the development runtime and restores useful player rankings alongside named shop and bank rankings.

## Leaderboards

- Player rankings: wealth, banking activity, and business ownership.
- Shop rankings: revenue, level, and seven-day growth momentum.
- Bank rankings: deposits, customer accounts, and reserves.
- Money values use dollar formatting with thousands separators and cents.
- Shop and bank entries display their actual names rather than owner profiles.
- Obsolete claims and legacy owner-based shop/bank rankings are removed.

## Development

- NeoEssentials Build 11 is available through the local Gradle runtime configuration.
- Leaderboards register again after server startup so NeoEssentials' final manager receives the current UBS providers.
- The public UBS Java API remains version `2.1.1` for compatibility.
