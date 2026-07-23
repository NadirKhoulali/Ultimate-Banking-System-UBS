# Currency & Legal Tender

This page lists every UBS legal-tender cash item currently in the mod.

## Notes

- Legal tender in UBS is physical USD cash: bills and coins.
- `bank_note` and `cheque` are negotiable instruments, not base legal-tender cash items.
- ATM behavior:
  - withdraw: bills only
  - deposit: bills + coins (exact combination required)
- Bank Teller can dispense bills + coins.

## Bills

| Denomination | Item ID | Texture |
|---|---|---|
| $1 bill | `ultimatebankingsystem:one_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/one_dollar_american.png" alt="$1 bill" width="96"/> |
| $2 bill | `ultimatebankingsystem:two_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/two_dollar_american.png" alt="$2 bill" width="96"/> |
| $5 bill | `ultimatebankingsystem:five_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/five_dollar_american.png" alt="$5 bill" width="96"/> |
| $10 bill | `ultimatebankingsystem:ten_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/ten_dollar_american.png" alt="$10 bill" width="96"/> |
| $20 bill | `ultimatebankingsystem:twenty_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/twenty_dollar_american.png" alt="$20 bill" width="96"/> |
| $50 bill | `ultimatebankingsystem:fifty_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/fifty_dollar_american.png" alt="$50 bill" width="96"/> |
| $100 bill | `ultimatebankingsystem:hundred_dollar_bill` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/hundred_dollar_american.png" alt="$100 bill" width="96"/> |

## Coins

| Denomination | Item ID | Front | Back |
|---|---|---|---|
| $0.01 (Penny) | `ultimatebankingsystem:penny_coin` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/penny_american_front.png" alt="Penny front" width="92"/> | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/penny_american_back.png" alt="Penny back" width="92"/> |
| $0.05 (Nickel) | `ultimatebankingsystem:nickel_coin` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/nickel_american_front.png" alt="Nickel front" width="92"/> | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/nickel_american_back.png" alt="Nickel back" width="92"/> |
| $0.10 (Dime) | `ultimatebankingsystem:dime_coin` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/dime_american_front.png" alt="Dime front" width="92"/> | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/dime_american_back.png" alt="Dime back" width="92"/> |
| $0.25 (Quarter) | `ultimatebankingsystem:quarter_coin` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/quarter_american_front.png" alt="Quarter front" width="92"/> | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/quarter_american_back.png" alt="Quarter back" width="92"/> |
| $0.50 (Half Dollar) | `ultimatebankingsystem:half_dollar_coin` | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/half_american_front.png" alt="Half dollar front" width="92"/> | <img src="https://raw.githubusercontent.com/PixelForgeMods/Ultimate-Banking-System-UBS/main/src/main/resources/assets/ultimatebankingsystem/textures/item/half_american_back.png" alt="Half dollar back" width="92"/> |

## Placeable Money Stacks

Every bill denomination has a strapped stack item, from `one_dollar_money_stack` through `hundred_dollar_money_stack`. Right-clicking an existing matching stack adds quantity to the physical pile up to its supported visual/storage limit. Stacks can be placed in compatible safes and stolen into heist duffels during an active heist.

## Bullion

- `ultimatebankingsystem:gold_bar`
- `ultimatebankingsystem:silver_bar`
- `ultimatebankingsystem:metal_pallet`

Bars are physical storage/loot objects rather than legal tender. Their lore displays the Minecraft ingot equivalence and current market-linked value. Gold uses Minecraft gold ingots; silver recipes accept the NeoForge `c:ingots/silver` ingredient tag so compatible mods can supply silver.

The Central Bank spot market stores gold/silver quotes. The phone market and bullion lore reflect the current quote rather than a hardcoded real-world price. Server operators can inspect or update it through the Central Bank market controls.

Metal pallets hold visible bullion/cash lines and are indexed by the Bank Owner PC Vault Storage map. Multiple vertically aligned pallets are grouped in storage detail views.
