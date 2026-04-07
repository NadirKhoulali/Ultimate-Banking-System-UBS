# Issues — ATM GUI

## Known Bugs (to fix)
- BankScreen.render(): graphics.fill() draws ON TOP of widgets (invisible buttons)
- ATMBlock.java imports net.minecraft.client.Minecraft in common code (server crash risk)
- AccountHolder.RemoveBalance uses <= instead of < (can't withdraw exact balance)
  - **FIXED**: Changed `<=` to `<` on line 89, added debug log line. Build passes.
