# Configuration

UBS uses common config values to tune security, banking behavior, and macro-economy systems.

## Core Limits

- `TransactionsPerMinute`
- `DefaultATMWithdrawalLimit`
- `DailyWithdrawalLimit`
- `GlobalMaxSingleTransaction` (also caps handheld terminal max save value; default `50000`)
- `GlobalMaxDailyPlayerVolume`
- `GlobalMaxDailyBankVolume`
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

## Pickpocket

- `PickpocketEnabled`
- `PickpocketDurationTicks`
- `PickpocketCooldownTicks`

## World Cash Economy

- `ChestCashLootEnabled`
- `ChestCashLootChancePercent`
- `MobCashDropsEnabled`
- `MobCashDropsPlayerKillOnly`
- `MobCashDropHealthWeight`
- `MobCashDropAttackWeight`
- `MobCashDropArmorWeight`
- `MobCashDropVariancePercent`
- `MobCashDropMinCents`
- `MobCashDropMaxCents`
- `DeathCashDropEnabled`
- `DeathCashDropPercent`
- `DeathCashDropApplyWithKeepInventory`
- `DeathCashDropDespawnTicks`
- `DeathCashDropLabelRefreshTicks`

## Shop and Retail

- `OpenMarketHistoryLimit`
- `ClearingLedgerLimit`
- `ShopLevelingEnabled`
- `ShopLevelScaleClaimCapacity`
- `ShopLevelScaleStockroomCapacity`
- `ShopLevelScaleDisplayLimit`
- `ShopLevelScaleCashierLimit`
- `ShopLevelScaleDeliveryPalletLimit`
- `ShopStockroomBaseCapacityBlocks`
- `ShopStockroomCapacityPerLevelBlocks`
- `ShopStockroomMaxCapacityBlocks`
- `ShopDisplayBaseLimit`
- `ShopDisplayLimitPerLevel`
- `ShopDisplayMaxLimit`
- `ShopMaxCashierSpawnEggsPerShop`
- `ShopMaxAssignedOrderPalletsPerShop`
- `ShopMaxActiveCourierOrders`
- `ShopCashierLowBagThreshold`
- `ShopWebshopMaxActiveOrders`
- `ShopWebshopDefaultEtaSeconds`
- `ShopWebshopRetryDelaySeconds`
- `ShopWebshopMaxRetryAttempts`
- `ShopWebshopCancelFeePercent`
- `ShopWebshopExpediteSurchargePercent`
- `ShopWebshopExpediteEtaSeconds`

## Account Storage and CDs

- `SafeBoxSlotsChecking`
- `SafeBoxSlotsSaving`
- `SafeBoxSlotsMoneyMarket`
- `SafeBoxSlotsCertificate`
- `CDShortTermTicks`
- `CDMediumTermTicks`
- `CDLongTermTicks`
- `CDShortRate`
- `CDMediumRate`
- `CDLongRate`
- `CDEarlyPenaltyFactorShort`
- `CDEarlyPenaltyFactorMedium`
- `CDEarlyPenaltyFactorLong`

## Coming Soon / Reserved Heist Keys

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
