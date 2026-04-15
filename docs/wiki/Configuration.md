# Configuration

UBS uses common config values to tune security, banking behavior, and macro-economy systems.

## Core Limits

- `TransactionsPerMinute`
- `DefaultATMWithdrawalLimit`
- `DailyWithdrawalLimit`
- `GlobalMaxSingleTransaction` (also caps handheld terminal max save value; default `50000`)
- `DailyWithdrawalLimitChecking`
- `DailyWithdrawalLimitSaving`
- `DailyWithdrawalLimitMoneyMarket`
- `DailyWithdrawalLimitCertificate`
- `PaymentTerminalFeedbackTicks` (legacy key, terminal feedback lock is currently fixed to 2 seconds)

## Scheduling

- `AutoSaveIntervalMinutes`
- `SavingsInterestIntervalTicks`
- `LoanPaymentIntervalTicks`
- `LoanWarningBeforeDueTicks`
- `BankAnnualLicenseIntervalTicks`
- `BankTaxIntervalTicks`

## Monetary Policy and Rates

- `ServerInterestRate`
- `FederalFundsRate`
- `MinFederalFundsRate`
- `MaxFederalFundsRate`
- `SavingsRateFloorMultiplier`
- `SavingsRateCeilingMultiplier`
- `MoneyMarketRateMultiplier`
- `AllowBankCustomInterestRate`
- `MinCustomBankInterestRate`
- `MaxCustomBankInterestRate`

## Bank Stability and Liquidity

- `BankMinReserveRatio`
- `BankReserveGraceTicks`
- `BankDailyLiquidityRatio`
- `WithdrawalQueueExpiryTicks`
- `BankRunWindowTicks`
- `BankRunThresholdRatio`
- `BankRunLockdownTicks`

## Player Bank Creation Controls

- `PlayerBanksEnabled`
- `PlayerBanksRequireAdminApproval`
- `PlayerBanksMinBalance`
- `PlayerBanksMinPlaytimeHours`
- `PlayerBanksCreationFee`
- `PlayerBanksMaxPerPlayer`
- `PlayerBanksNameMaxLength`
- `PlayerBanksCreationCooldownHours`
- `BankCharterFee`
- `BankAnnualLicenseFee`

## Loan and Credit Controls

- `LoanAutoApproveThreshold`
- `LoanAutoApproveMinCredit`
- `LoanBaseInterestRate`
- `LoanMinInterestRate`
- `LoanMaxInterestRate`
- `LoanTermPayments`
- `CreditScoreDefault`
- `CreditScoreOnTimeBoost`
- `CreditScoreMissedPenalty`
- `CreditScoreDefaultPenalty`

## Special Systems

- `OpenMarketHistoryLimit`
- `ClearingLedgerLimit`
- `SafeBoxSlotsChecking`
- `SafeBoxSlotsSaving`
- `SafeBoxSlotsMoneyMarket`
- `SafeBoxSlotsCertificate`
- `HeistDurationTicks` (Coming Soon)
- `HeistMinPlayers` (Coming Soon)
- `HeistSuccessChance` (Coming Soon)
- `HeistPayoutRatio` (Coming Soon)
- `HeistCooldownTicks` (Coming Soon)

## Display

- `CurrencySymbol`
- `CurrencyName`
- `HudEnabledByDefault`
- `HudCorner`
- `HudTextColor`
