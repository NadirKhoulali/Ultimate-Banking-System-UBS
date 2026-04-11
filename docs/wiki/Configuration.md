# Configuration

UBS common config keys:

- `TransactionsPerMinute`
- `DefaultATMWithdrawalLimit`
- `DailyWithdrawalLimit`
- `AutoSaveIntervalMinutes`
- `SavingsInterestIntervalTicks`
- `AllowBankCustomInterestRate`
- `ServerInterestRate`
- `FederalFundsRate`
- `MinCustomBankInterestRate`
- `MaxCustomBankInterestRate`
- `CurrencySymbol`
- `CurrencyName`

## Operational Notes

- `AutoSaveIntervalMinutes` marks banking data dirty on schedule so world saves persist updates reliably.
- `SavingsInterestIntervalTicks` controls how often savings payout runs across banks.
- Daily withdrawal resets are based on Minecraft day windows (`24000` ticks).
