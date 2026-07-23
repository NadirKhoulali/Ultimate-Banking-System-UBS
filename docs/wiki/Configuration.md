# Configuration

UBS uses common config values to tune security, banking behavior, and macro-economy systems.

## Core Limits

- `TransactionsPerMinute`
- `AccountTransactionLogLimit` (default `20`, range `1..1000`)
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
- `ShopTypeConversionFeeDollars`
- `ShopTypeConversionCooldownHours`
- `ShopFranchiseBrandOwnerUnlockLevel`
- `ShopFranchiseBaseLicenseCapacity`
- `ShopFranchiseLicenseCapacityPer10Levels`
- `ShopFranchiseMaxLicenseCapacity`
- `ShopFranchiseDefaultUpfrontFeeDollars`
- `ShopFranchiseDefaultRoyaltyPercent`
- `ShopFranchiseDefaultMarketingPercent`
- `ShopFranchiseNoncompliancePenaltyPercent`
- `ShopCorporateFirstExtraBranchLevel`
- `ShopCorporateBranchLevelStep`
- `ShopCorporateMaxBranches`
- `ShopCorporateOverheadPercent`

Webshop-related keys control active order limits, ETA/retry behavior, cancel fees, and expedite surcharges. Shop type/franchise/corporate keys control reclassification cost, franchise offer capacity/defaults, noncompliance penalties, and corporate branch scaling.

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

## Legacy Heist Keys

- `HeistDurationTicks`
- `HeistMinPlayers`
- `HeistSuccessChance`
- `HeistPayoutRatio`
- `HeistCooldownTicks`

These keys are retained for config compatibility. The physical UBS 2.0 heist loop uses its server-authoritative planning/session constants; integrations should read effective values through `UltimateHeistApi` rather than assume these legacy values control every phase.

## Smartphone

- `PhoneAccessMode`

Values:

- `OWNER_LOCKED`: the first owner is required to use the phone
- `OPEN_ACCESS`: the current holder may use it

## Claim Visibility

- `ClaimOutlinesShowAllPlayers` (default `false`)

When false, players see only claims owned by them. Collision checks still include every bank/shop owner and deny overlaps. When true, the tactical claim renderer can show nearby claims from other owners.

## Display

- `CurrencySymbol`
- `CurrencyName`
- `HudEnabledByDefault`
- `HudCorner` (`TOP_LEFT`, `TOP_RIGHT`, `MIDDLE_LEFT`, `MIDDLE_RIGHT`, `BOTTOM_LEFT`, or `BOTTOM_RIGHT`)
- `HudTextColor` (packed RGB amount/accent color)

The balance HUD identifies the monitored bank and account type, condenses to an amount-only card on small GUI
dimensions, and briefly marks credits or debits when a synchronized balance changes. Top-right cards automatically
move below active UBS notifications instead of overlapping them.

Players can override the default persistently with
`/account hud move <top-right|top-left|middle-right|middle-left|bottom-right|bottom-left>`. This changes only the
balance HUD; notification and alert placement is unaffected.
