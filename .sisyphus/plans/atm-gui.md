# ATM GUI System — Complete Overhaul

## TL;DR

> **Quick Summary**: Fix 3 critical bugs in the existing GUI code, then build a complete ATM interface with 6 functional screens (Withdraw, Deposit, Transfer, Balance Inquiry, Transaction History, Account Settings), stack-based layer navigation, NeoForge networking packets for real server-side operations, and a themed ATM visual design — all using the existing `NineSliceTexturedButton` widget with `atm_buttons.png`.
>
> **Deliverables**:
> - 3 bug fixes (rendering order, ATMBlock client safety, RemoveBalance off-by-one)
> - Stack-based layer navigation system in `BankScreen`
> - NeoForge networking infrastructure (`ModPayloads` registry + 8 packet pairs)
> - Themed ATM panel background rendering
> - Overhauled `MainMenuLayer` with 6 buttons + account selector
> - 6 new sub-screen layers (Withdraw, Deposit, Transfer, BalanceInquiry, TransactionHistory, AccountSettings)
> - Client-side ATM data cache for account list
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES — 4 waves (5 → 2 → 6 → 4 review)
> **Critical Path**: Task 1 (rendering fix + nav) → Task 6 (handshake) → Task 7 (main menu) → Tasks 8-13 (sub-screens) → F1-F4

---

## Context

### Original Request
User wants to build a complete GUI for the ATM Machine block in their Minecraft NeoForge 1.21.1 banking mod. A previous AI model started the work but only created a skeleton with 1 placeholder button. The user wants the main menu to have standard American ATM buttons, each opening a dedicated sub-screen. All buttons must use the `NineSliceTexturedButton` widget with `atm_buttons.png` as the texture.

### Interview Summary
**Key Discussions**:
- **ATM Buttons**: All 6 standard buttons — Withdraw Cash, Deposit, Transfer Funds, Balance Inquiry, Transaction History, Account Settings
- **Withdraw flow**: Both preset amounts ($20/$50/$100/$200/$500) AND custom amount text field
- **Deposit flow**: Amount input field + confirm button
- **Transfer flow**: Recipient account UUID text field + amount input + confirm
- **Balance Inquiry**: Display current balance, account type, bank name
- **Transaction History**: Scrollable list of recent transactions
- **Account Settings**: View info, set primary account, change PIN/password
- **Account selector**: Widget on main menu to pick which account to operate on
- **Navigation**: Back button (NineSliceTexturedButton) on every sub-screen
- **Scope**: Full networking included (NeoForge packets for real server operations)
- **Visual**: Themed ATM look (custom panel, header bar, borders)
- **Texture**: Confirmed 120x40 PNG, two 120x20 frames (normal + hover), 4px 9-slice borders

### Metis Review
**Identified Gaps** (addressed):

- **Rendering order bug in BankScreen**: `graphics.fill()` (white panel) draws ON TOP of widgets from `super.render()`, making all buttons invisible. Fixed by making it the first task.
- **ATMBlock imports client class in common code**: `import net.minecraft.client.Minecraft` in `ATMBlock.java` can crash dedicated servers. Fixed by moving to client helper.
- **RemoveBalance off-by-one**: Uses `<=` instead of `<`, meaning exact-balance withdrawals fail. Fixed as separate task.
- **No account data on client at screen open**: Client has zero knowledge of player accounts when ATM opens. Solved with OpenATM handshake packet (client requests → server responds with account list).
- **Zero accounts edge case**: If player has no accounts, main menu shows "No accounts found" message with guidance to use commands.
- **Data flow pattern**: Every sub-screen follows request→response packet cycle. Server is single source of truth.
- **Thread safety**: All packet handlers must use `context.enqueueWork()` for main-thread safety.

---

## Work Objectives

### Core Objective
Transform the skeleton ATM GUI into a fully functional, visually themed, network-connected banking interface with 6 operational screens, proper layer navigation, and real server-side data flow.

### Concrete Deliverables
- `BankScreen.java` — Rewritten with stack-based layer navigation, themed panel rendering, proper render order
- `MainMenuLayer.java` — Rewritten with 6 NineSliceTexturedButtons + account selector dropdown
- `WithdrawLayer.java` — New: preset buttons + custom amount + confirm
- `DepositLayer.java` — New: amount input + confirm
- `TransferLayer.java` — New: recipient UUID + amount + confirm
- `BalanceInquiryLayer.java` — New: read-only balance/account display
- `TransactionHistoryLayer.java` — New: scrollable transaction list
- `AccountSettingsLayer.java` — New: view info, set primary, change PIN
- `ModPayloads.java` — New: packet registry
- 8+ packet payload records in `net.austizz.ultimatebankingsystem.network` package
- `ClientATMData.java` — New: client-side cache for current ATM session data
- `ATMBlock.java` — Fixed: client-safe screen opening
- `AccountHolder.java` — Fixed: RemoveBalance comparison

### Definition of Done
- [ ] All 6 ATM buttons visible on main menu with NineSliceTexturedButton
- [ ] Each button opens its corresponding sub-screen layer
- [ ] Back button on every sub-screen returns to main menu
- [ ] Account selector on main menu shows player's accounts from server
- [ ] Withdraw/Deposit/Transfer operations modify real server-side balances
- [ ] Transaction History displays real transaction records
- [ ] Account Settings displays real account info and allows changes
- [ ] No client-class imports in common-side code
- [ ] Game launches without crash on both client and dedicated server

### Must Have
- All buttons use `NineSliceTexturedButton` with `atm_buttons.png` texture (120x40, 4px borders)
- Stack-based layer navigation with push/pop in `BankScreen`
- Back button on every sub-screen
- Account selector on main menu
- Request→response packet pattern for every server operation
- `context.enqueueWork()` in every packet handler
- Themed ATM panel (dark background, header bar, styled borders)
- "No accounts found" handling when player has zero accounts
- Confirmation step for destructive operations (Transfer, Change PIN)
- Error feedback shown to player in GUI when operations fail

### Must NOT Have (Guardrails)
- No `net.minecraft.client.Minecraft` or `client.*` imports in common-side code (blocks, packets, entities)
- No account creation/deletion via ATM GUI (handled by existing commands)
- No item-based deposit/withdraw (virtual currency only for this plan)
- No new texture assets beyond ATM panel — reuse `atm_buttons.png` for all buttons
- No filtering, sorting, or search in Transaction History (simple scrollable list, most-recent-first, max 50 entries)
- No sound effects, particles, or animations
- No modifications to `Bank.java`, `CentralBank.java`, or `BankManager.java` (backend stays as-is)
- No long-term client-side caching of balance data — always request fresh data from server for operations
- No PIN/password verification required before operations (password is stored/changeable but not enforced as auth)

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: NO (Minecraft NeoForge mod — no JUnit/test framework configured)
- **Automated tests**: None (in-game QA only)
- **Framework**: None
- **Rationale**: NeoForge mods require a running Minecraft client/server for meaningful testing. Verification is done through in-game interaction and server log inspection.

### QA Policy
Every task MUST include agent-executed QA scenarios using:
- **GUI screens**: Playwright skill with Minecraft client screenshots (or tmux with game launch)
- **Networking**: Server console log assertions (`[UBS]` prefixed log messages)
- **Navigation**: Push/pop layer state verification via visual inspection
- **Backend effects**: Server log confirms balance changes, data persistence

Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

---

## Execution Strategy

### Parallel Execution Waves

> 4 waves. Max 6 parallel tasks in Wave 3. Total: 13 implementation + 4 review = 17 tasks.

```
Wave 1 (Start Immediately — bug fixes + infrastructure, 5 parallel):
  Task 1: Fix BankScreen rendering order + build stack-based layer navigation [deep]
  Task 2: Fix ATMBlock client-class safety (DistExecutor/helper) [quick]
  Task 3: Fix AccountHolder.RemoveBalance off-by-one [quick]
  Task 4: Create themed ATM panel background rendering [visual-engineering]
  Task 5: Create networking infrastructure (ModPayloads registry) [unspecified-high]

Wave 2 (After Wave 1 — handshake + main menu, 2 sequential):
  Task 6: OpenATM handshake packets + client data cache (depends: 5) [unspecified-high]
  Task 7: MainMenuLayer overhaul — 6 buttons + account selector (depends: 1, 4, 6) [visual-engineering]

Wave 3 (After Wave 2 — 6 parallel sub-screens):
  Task 8:  BalanceInquiryLayer + BalancePacket (depends: 7) [unspecified-high]
  Task 9:  WithdrawLayer + WithdrawPacket (depends: 7) [unspecified-high]
  Task 10: DepositLayer + DepositPacket (depends: 7) [unspecified-high]
  Task 11: TransferLayer + TransferPacket (depends: 7) [unspecified-high]
  Task 12: TransactionHistoryLayer + TxHistoryPacket (depends: 7) [unspecified-high]
  Task 13: AccountSettingsLayer + SettingsPackets (depends: 7) [unspecified-high]

Wave FINAL (After ALL tasks — 4 parallel reviews, then user okay):
  F1: Plan compliance audit (oracle)
  F2: Code quality review (unspecified-high)
  F3: Real manual QA (unspecified-high)
  F4: Scope fidelity check (deep)
  -> Present results -> Get explicit user okay

Critical Path: Task 1 -> Task 6 -> Task 7 -> Tasks 8-13 -> F1-F4 -> user okay
Parallel Speedup: ~65% faster than sequential
Max Concurrent: 6 (Wave 3)
```

### Dependency Matrix

| Task | Depends On | Blocks | Wave |
|------|-----------|--------|------|
| 1 | — | 7 | 1 |
| 2 | — | — | 1 |
| 3 | — | — | 1 |
| 4 | — | 7 | 1 |
| 5 | — | 6 | 1 |
| 6 | 5 | 7 | 2 |
| 7 | 1, 4, 6 | 8-13 | 2 |
| 8 | 7 | — | 3 |
| 9 | 7 | — | 3 |
| 10 | 7 | — | 3 |
| 11 | 7 | — | 3 |
| 12 | 7 | — | 3 |
| 13 | 7 | — | 3 |
| F1-F4 | 1-13 | — | Final |

### Agent Dispatch Summary

- **Wave 1**: **5 tasks** — T1 `deep`, T2 `quick`, T3 `quick`, T4 `visual-engineering`, T5 `unspecified-high`
- **Wave 2**: **2 tasks** — T6 `unspecified-high`, T7 `visual-engineering`
- **Wave 3**: **6 tasks** — T8-T13 all `unspecified-high`
- **Wave FINAL**: **4 tasks** — F1 `oracle`, F2 `unspecified-high`, F3 `unspecified-high`, F4 `deep`

---

## TODOs

- [x] 1. Fix BankScreen Rendering Order + Build Stack-Based Layer Navigation

  **What to do**:
  - **Rendering fix**: In `BankScreen.render()`, move the `graphics.fill()` background panel drawing to BEFORE `super.render()`. Currently the white rectangle draws ON TOP of all widgets, making them invisible. The correct order is: (1) draw background panel, (2) `super.render()` (renders widgets), (3) layer custom render.
  - **Remove old code**: Delete the hardcoded `PlainTextButton` "Close ATM" from `init()`. Delete the duplicated `boxWidth`/`boxHeight` local variables in `init()` (lines 47-50) that are unused.
  - **Layer navigation stack**: Replace the flat `List<ScreenLayer> layers` with a stack-based system:
    - Add `pushLayer(ScreenLayer layer)` — clears current widgets, pushes new layer, calls `layer.init()`, re-registers widgets
    - Add `popLayer()` — removes top layer, reveals previous layer, re-registers its widgets
    - Add `setRootLayer(ScreenLayer layer)` — clears stack entirely, sets new root
    - Widgets must be re-registered with `addRenderableWidget()` / `addRenderableOnly()` on each push/pop since Minecraft's `Screen` clears its internal widget list on `rebuildWidgets()`
  - **Visibility**: Only the topmost layer's widgets should be active/visible. When pushing, remove old layer's widgets. When popping, re-add previous layer's widgets.
  - **Store reference**: Layers need a back-reference to `BankScreen` so they can call `pushLayer()`/`popLayer()`. Add a `setBankScreen(BankScreen)` method to `ScreenLayer` interface or pass it in constructors.
  - **ESC behavior**: Pressing ESC should `popLayer()` if stack has >1 layer, otherwise close the screen (default `onClose()`).

  **Must NOT do**:
  - Do not modify `ScreenLayer` interface beyond adding the bank screen reference
  - Do not modify `AbstractScreenLayer` beyond accommodating the reference
  - Do not add any sub-screen layers yet (that's later tasks)

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: Architectural change to core screen management — requires understanding Minecraft's Screen widget lifecycle, careful state management for push/pop, and proper widget re-registration
  - **Skills**: []
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: This is architecture, not visual design
    - `playwright`: No browser testing for Minecraft GUI

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 2, 3, 4, 5)
  - **Blocks**: Task 7 (MainMenuLayer needs navigation to push sub-screens)
  - **Blocked By**: None (can start immediately)

  **References** (CRITICAL):

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/BankScreen.java:94-108` — The broken render method. Line 102 `graphics.fill()` must move before line 96 `super.render()`.
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/BankScreen.java:25-59` — The `init()` method. Lines 47-50 are dead code (unused boxWidth/boxHeight). Lines 53-59 are the PlainTextButton to remove.
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/BankScreen.java:36-44` — Widget registration loop pattern. This logic must be callable on push/pop (extract to helper method).
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/ScreenLayer.java:18-31` — Interface contract. Needs bank screen reference method added.
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/AbstractScreenLayer.java:16-66` — Base class. Store BankScreen reference here.

  **API/Type References**:
  - Minecraft's `Screen.clearWidgets()` — Clears all registered widgets. Call before re-registering new layer's widgets.
  - Minecraft's `Screen.addRenderableWidget()` — Re-registers widgets after layer change.
  - `Screen.rebuildWidgets()` — Called on resize; your layer stack must survive this.

  **WHY Each Reference Matters**:
  - BankScreen.java:94-108 — This IS the bug to fix. The executor must see the exact broken line ordering.
  - BankScreen.java:36-44 — This widget-registration loop is the pattern to reuse in `pushLayer()`/`popLayer()`.
  - ScreenLayer interface — Must be extended without breaking existing MainMenuLayer.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Background renders behind widgets (rendering fix)
    Tool: Bash (./gradlew runClient)
    Preconditions: Game launched, ATM block placed in world
    Steps:
      1. Right-click ATM block to open BankScreen
      2. Verify the ATM panel background (colored rectangle) renders BEHIND all widgets
      3. Verify at least 1 button/widget is visible on screen (not hidden behind panel)
    Expected Result: Widgets render on top of the background panel, not hidden behind it
    Failure Indicators: Blank white/colored rectangle with no visible buttons or text
    Evidence: .sisyphus/evidence/task-1-rendering-fix.png

  Scenario: Push and pop layer navigation works
    Tool: Bash (./gradlew runClient)
    Preconditions: Game launched, ATM open, at least MainMenuLayer showing
    Steps:
      1. Open ATM — MainMenuLayer is the root layer (visible)
      2. Programmatically verify pushLayer/popLayer compiles and the stack size changes
      3. Press ESC with only root layer — screen closes
    Expected Result: Stack manages layers correctly, ESC closes when at root
    Failure Indicators: Crash on push/pop, widgets from old layer still visible, ESC doesn't close
    Evidence: .sisyphus/evidence/task-1-layer-nav.png
  ```

  **Evidence to Capture:**
  - [ ] Screenshot of ATM screen with visible widgets (rendering fix proof)
  - [ ] Server/client log showing no errors on screen open/close

  **Commit**: YES
  - Message: `fix(gui): correct rendering order in BankScreen, add layer navigation stack`
  - Files: `BankScreen.java`, `ScreenLayer.java`, `AbstractScreenLayer.java`
  - Pre-commit: `./gradlew build`

- [x] 2. Fix ATMBlock Client-Class Safety

  **What to do**:
  - The current `ATMBlock.java` imports `net.minecraft.client.Minecraft` directly in a common-side block class. This can cause `NoClassDefFoundError` on dedicated servers.
  - Create a client-only helper class: `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/ATMScreenHelper.java` annotated with `@OnlyIn(Dist.CLIENT)` or placed in a client-only package.
  - Move the `Minecraft.getInstance().setScreen(new BankScreen(...))` call to this helper.
  - In `ATMBlock.useItemOn()`, replace the direct call with a safe pattern. Two options:
    - **Option A (recommended for NeoForge 1.21.1)**: Use `if (level.isClientSide()) { ATMScreenHelper.openATMScreen(); }` where `ATMScreenHelper` is a separate class that only exists on client. The key is that `ATMBlock` must NOT import any client class — the helper reference is resolved lazily.
    - **Option B**: Use `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> { ... })` — but this is deprecated in newer NeoForge.
  - Remove the `import net.minecraft.client.Minecraft;` from `ATMBlock.java`.
  - Also clean up the diamond→gold_block test code in `useItemOn()` (lines 63-77) — this is debug/test code that shouldn't ship.
  - The ATM should open the screen on right-click regardless of held item (use `useWithoutItem` override instead of `useItemOn` if appropriate, or handle empty-hand case).

  **Must NOT do**:
  - Do not change ATM block placement/destruction/state logic
  - Do not add networking to this task (that's Task 5-6)
  - Do not modify any other block classes

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Small, focused fix — one file to change, one new helper class to create
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 3, 4, 5)
  - **Blocks**: None directly (but ATMBlock will be modified again in Task 6 for networking)
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/block/custom/ATMBlock.java:56-81` — The `useItemOn` method with the unsafe client import and debug diamond code
  - `src/main/java/net/austizz/ultimatebankingsystem/UltimateBankingSystemClient.java:14-16` — Example of `@Mod(dist = Dist.CLIENT)` pattern already used in the project

  **External References**:
  - NeoForge side safety: The standard pattern is to isolate client calls in a separate class that is only classloaded on the client side. Avoid `DistExecutor` (deprecated). Use a plain helper class with `@OnlyIn(Dist.CLIENT)`.

  **WHY Each Reference Matters**:
  - ATMBlock.java:56-81 — This IS the code to fix. Executor needs to see the debug code to remove and the unsafe import to relocate.
  - UltimateBankingSystemClient.java — Shows the project already uses `Dist.CLIENT` annotation pattern, so the helper should follow suit.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Dedicated server starts without crash
    Tool: Bash (./gradlew runServer)
    Preconditions: None
    Steps:
      1. Run ./gradlew runServer
      2. Wait for server to fully start (look for "Done" in console)
      3. Check server log for NoClassDefFoundError or ClassNotFoundException related to Minecraft client classes
    Expected Result: Server starts successfully with no client-class errors
    Failure Indicators: Server crashes with NoClassDefFoundError for net.minecraft.client.Minecraft
    Evidence: .sisyphus/evidence/task-2-server-start.txt

  Scenario: ATM screen still opens on client
    Tool: Bash (./gradlew runClient)
    Preconditions: Game launched, ATM block placed
    Steps:
      1. Right-click ATM block with empty hand
      2. Verify BankScreen opens
      3. Right-click ATM block while holding any item
      4. Verify BankScreen opens (no diamond→gold behavior)
    Expected Result: ATM GUI opens regardless of held item, no debug behavior
    Failure Indicators: Nothing happens on right-click, or block transforms to gold
    Evidence: .sisyphus/evidence/task-2-client-screen.png
  ```

  **Commit**: YES
  - Message: `fix(block): move client-only screen opening to safe helper in ATMBlock`
  - Files: `ATMBlock.java`, new `ATMScreenHelper.java`
  - Pre-commit: `./gradlew build`

- [x] 3. Fix AccountHolder RemoveBalance Off-by-One

  **What to do**:
  - In `AccountHolder.java` line 88, change the comparison from `<=` to `<`:
    - **Current** (broken): `if(this.balance.compareTo(balance) <= 0)` — Returns false when balance equals withdrawal amount, meaning players can never fully empty their account.
    - **Fixed**: `if(this.balance.compareTo(balance) < 0)` — Returns false only when balance is strictly less than the withdrawal amount. Players can now withdraw their exact balance.
  - Add a `UltimateBankingSystem.LOGGER.debug()` call when a withdrawal is processed, logging the account UUID, amount, and new balance. This helps with QA for later tasks.

  **Must NOT do**:
  - Do not change `AddBalance` logic
  - Do not modify any other AccountHolder methods
  - Do not add networking or GUI code

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: Single-line bug fix with minimal context needed
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 4, 5)
  - **Blocks**: None (but Tasks 9-10 depend on correct withdrawal logic)
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:86-97` — The `RemoveBalance` method. Line 88 has the comparison bug.
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:77-84` — `AddBalance` method for comparison (uses `<= 0` correctly since you shouldn't add zero or negative amounts).

  **WHY Each Reference Matters**:
  - AccountHolder.java:86-97 — This IS the bug. The executor must change `<=` to `<` on line 88.
  - AddBalance for comparison — Shows the intended guard pattern; helps executor understand the fix is isolated.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Player can withdraw exact balance amount
    Tool: Bash (game console commands)
    Preconditions: Player has account with exactly $100.00 balance
    Steps:
      1. Via commands, create account with $100 balance
      2. Call RemoveBalance(new BigDecimal("100.00")) on the account
      3. Verify method returns true
      4. Verify account balance is now $0.00
    Expected Result: RemoveBalance returns true, balance is 0
    Failure Indicators: RemoveBalance returns false when attempting exact balance withdrawal
    Evidence: .sisyphus/evidence/task-3-exact-withdrawal.txt

  Scenario: Overdraft still prevented
    Tool: Bash (game console commands)
    Preconditions: Player has account with $50.00 balance
    Steps:
      1. Call RemoveBalance(new BigDecimal("100.00")) on the account
      2. Verify method returns false
      3. Verify account balance is still $50.00
    Expected Result: RemoveBalance returns false, balance unchanged
    Failure Indicators: Balance goes negative or method returns true
    Evidence: .sisyphus/evidence/task-3-overdraft-prevented.txt
  ```

  **Commit**: YES
  - Message: `fix(account): correct RemoveBalance comparison to allow exact withdrawals`
  - Files: `AccountHolder.java`
  - Pre-commit: `./gradlew build`

- [x] 4. Create Themed ATM Panel Background Rendering

  **What to do**:
  - Replace the plain white `graphics.fill()` rectangle in `BankScreen.render()` with a themed ATM panel:
    - **Outer panel**: Dark charcoal/navy background (`0xFF1A1A2E` or similar dark color), with a 2px lighter border (`0xFF3A3A5E`)
    - **Header bar**: A colored strip at the top of the panel (bank-blue `0xFF0D47A1` or ATM-green `0xFF00695C`) with the title "ATM Machine" in white, centered
    - **Content area**: Slightly lighter than outer panel (`0xFF252540`), where layer widgets render
    - **Panel dimensions**: Adapt to a sensible size like 260x220 pixels (enough for 6 buttons + selector + padding). Center on screen.
  - All drawing should be in `BankScreen.render()` BEFORE `super.render()` (which draws widgets) and before layer custom render.
  - Use `graphics.fill()` for solid rectangles, `graphics.hLine()`/`graphics.vLine()` for borders.
  - Store panel dimensions (`panelLeft`, `panelTop`, `panelWidth`, `panelHeight`) as fields so layers can reference them for positioning their widgets.
  - Add getter methods: `getPanelLeft()`, `getPanelTop()`, `getPanelWidth()`, `getPanelHeight()` — layers use these to position widgets within the panel.
  - Draw the title text in the header bar: "ATM Machine" using `graphics.drawCenteredString()` in white.

  **Must NOT do**:
  - Do not create new PNG texture files for the panel (use code-drawn rectangles/fills)
  - Do not add any interactive widgets in this task (buttons are in Task 7)
  - Do not modify layer classes

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: Visual design task — color selection, layout, styling of the ATM panel
  - **Skills**: []
  - **Skills Evaluated but Omitted**:
    - `frontend-ui-ux`: Minecraft GUI uses a completely different rendering system than web/HTML

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 5)
  - **Blocks**: Task 7 (MainMenuLayer needs panel dimensions for widget positioning)
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/BankScreen.java:94-108` — Current render method with the white rectangle. Replace the `graphics.fill()` call with themed panel drawing.
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/MainMenuLayer.java:28-29` — Shows how layers use `screenWidth`/`screenHeight` for centering. Layers will need panel dimensions instead.

  **External References**:
  - Minecraft `GuiGraphics` API: `fill(x1, y1, x2, y2, color)` for solid rectangles, `drawCenteredString(font, text, x, y, color)` for centered text, `hLine(x1, x2, y, color)` and `vLine(x, y1, y2, color)` for lines.

  **WHY Each Reference Matters**:
  - BankScreen.java:94-108 — This is WHERE the drawing code goes. Executor replaces the white fill with themed drawing.
  - MainMenuLayer.java:28-29 — Shows layers currently use screen dimensions; they'll need panel dimensions instead, so the getter methods are essential.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Themed panel renders correctly
    Tool: Bash (./gradlew runClient)
    Preconditions: Game launched, ATM block placed
    Steps:
      1. Right-click ATM block
      2. Verify dark-colored panel appears centered on screen (not a plain white rectangle)
      3. Verify header bar at top with "ATM Machine" text in white
      4. Verify panel has visible border/outline distinguishing it from game background
      5. Verify content area below header is visible and slightly lighter than outer panel
    Expected Result: Themed ATM panel with dark background, colored header, border, centered on screen
    Failure Indicators: White rectangle, no header, panel off-center, text not visible
    Evidence: .sisyphus/evidence/task-4-themed-panel.png

  Scenario: Panel dimensions accessible to layers
    Tool: Bash (./gradlew build)
    Preconditions: Code compiles
    Steps:
      1. Verify BankScreen has public methods: getPanelLeft(), getPanelTop(), getPanelWidth(), getPanelHeight()
      2. Verify these return sensible integer values (not zero, not negative)
    Expected Result: Panel dimension getters compile and return valid values
    Failure Indicators: Compilation error, methods missing
    Evidence: .sisyphus/evidence/task-4-panel-api.txt
  ```

  **Commit**: YES
  - Message: `feat(gui): add themed ATM panel background rendering`
  - Files: `BankScreen.java`
  - Pre-commit: `./gradlew build`

- [x] 5. Create Networking Infrastructure (ModPayloads Registry)

  **What to do**:
  - Create new package: `net.austizz.ultimatebankingsystem.network`
  - Create `ModPayloads.java` — central registration class for all custom packets:
    - Register a `RegisterPayloadHandlersEvent` handler on the **mod event bus** (use `@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)`)
    - Define a registrar with `event.registrar(UltimateBankingSystem.MODID).versioned("1")`
    - This class will be populated with actual payload registrations in Tasks 6-13
    - For now, create the skeleton with one example/placeholder registration commented out showing the pattern
  - Document the packet pattern all future tasks must follow:
    - Each packet is a Java `record` implementing `CustomPacketPayload`
    - Each record has a `public static final CustomPacketPayload.Type<XxxPayload> TYPE` field
    - Each record has a `public static final StreamCodec<RegistryFriendlyByteBuf, XxxPayload> STREAM_CODEC` using `StreamCodec.composite()`
    - Registration: `registrar.playToServer(TYPE, STREAM_CODEC, ModPayloads::handleXxx)` or `playToClient()`
    - Handler method signature: `static void handleXxx(XxxPayload payload, IPayloadContext context)`
    - Handler MUST use `context.enqueueWork(() -> { ... })` for main-thread safety
  - Register the `ModPayloads` event in the mod's main class or ensure the `@EventBusSubscriber` annotation picks it up.
  - Verify the event fires on game launch (add a `LOGGER.info("[UBS] Registering network payloads")` in the handler).

  **Must NOT do**:
  - Do not create actual operation packets yet (those are Tasks 6-13)
  - Do not modify any GUI code
  - Do not add dependencies to build.gradle

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Requires understanding NeoForge 1.21.1 networking API — StreamCodec, CustomPacketPayload, payload registration
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2, 3, 4)
  - **Blocks**: Task 6 (OpenATM handshake depends on this infrastructure)
  - **Blocked By**: None

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/UltimateBankingSystem.java` — Main mod class. Check how event bus subscribers are registered. The `ModPayloads` class needs to be picked up by the mod event bus.

  **API/Type References**:
  - `net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent` — The event to subscribe to
  - `net.minecraft.network.protocol.common.custom.CustomPacketPayload` — Interface all payloads implement
  - `net.minecraft.network.codec.StreamCodec` — Encode/decode codec for payloads
  - `net.minecraft.network.RegistryFriendlyByteBuf` — Buffer type for play-phase packets
  - `net.neoforged.neoforge.network.handling.IPayloadContext` — Handler context with `enqueueWork()` and `player()`

  **External References**:
  - NeoForge 1.21.1 networking docs: `CustomPacketPayload` record pattern with `StreamCodec.composite()` for field serialization. `PayloadRegistrar.playToServer()` / `playToClient()` for directional registration.

  **WHY Each Reference Matters**:
  - UltimateBankingSystem.java — Executor needs to verify mod bus subscriber auto-detection or manually register the event handler
  - The NeoForge networking API types — Executor must use exact API: `StreamCodec.composite()`, `CustomPacketPayload.Type<>`, `IPayloadContext`

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: ModPayloads class compiles and event fires
    Tool: Bash (./gradlew runClient)
    Preconditions: None
    Steps:
      1. Run ./gradlew build — verify compilation succeeds
      2. Run ./gradlew runClient
      3. Check client log for "[UBS] Registering network payloads" message
    Expected Result: Build succeeds, log message confirms payload registration event fires
    Failure Indicators: Compilation error, or missing log message (event not firing)
    Evidence: .sisyphus/evidence/task-5-payload-registration.txt

  Scenario: Packet pattern documented and compilable
    Tool: Bash (./gradlew build)
    Preconditions: ModPayloads.java exists with commented example
    Steps:
      1. Verify ModPayloads.java has a clear commented example showing the full record + StreamCodec + registration pattern
      2. Verify the file compiles without errors
    Expected Result: Clean compilation, clear documentation of packet pattern
    Failure Indicators: Compilation error or missing pattern documentation
    Evidence: .sisyphus/evidence/task-5-pattern-doc.txt
  ```

  **Commit**: YES
  - Message: `feat(network): add ModPayloads registry and base packet infrastructure`
  - Files: `network/ModPayloads.java`, possibly `UltimateBankingSystem.java` (if manual registration needed)
  - Pre-commit: `./gradlew build`

- [x] 6. OpenATM Handshake Packets + Client Data Cache

  **What to do**:
  - **The problem**: When the player right-clicks the ATM, the client has ZERO knowledge of the player's bank accounts. Every sub-screen needs account data. This task builds the foundational request→response handshake.
  - **Create `OpenATMPayload`** (client → server):
    - Record with no fields (player identity comes from `context.player()`)
    - Type ID: `ultimatebankingsystem:open_atm`
    - Sent when ATM block is right-clicked (replaces direct `setScreen()` call)
  - **Create `AccountListPayload`** (server → client):
    - Record containing a list of account summary objects. Each summary: `UUID accountId`, `String accountType`, `String bankName`, `String balance` (as string to avoid floating-point serialization issues), `boolean isPrimary`
    - Type ID: `ultimatebankingsystem:account_list`
    - Use `StreamCodec.composite()` with `ByteBufCodecs.collection()` for the list
  - **Create `AccountSummary`** helper record/class for the account data transferred to client (NOT the full `AccountHolder` — only the fields needed for display).
  - **Server handler for `OpenATMPayload`**:
    - `context.enqueueWork(() -> { ... })`
    - Get `ServerPlayer` from context
    - Call `BankManager.getCentralBank(server)` → `SearchForAccount(player.getUUID())`
    - For each `AccountHolder`, look up the `Bank` name via `centralBank.getBank(account.getBankId())`
    - Build list of `AccountSummary` objects
    - Send `AccountListPayload` back to the player via `PacketDistributor.sendToPlayer()`
    - Log: `LOGGER.info("[UBS] Sending {} accounts to player {}", count, playerName)`
  - **Client handler for `AccountListPayload`**:
    - Store received account list in a `ClientATMData` class (static client-side cache)
    - Open `BankScreen` with the account data
    - The handler calls `Minecraft.getInstance().setScreen(new BankScreen(..., accountList))` inside `context.enqueueWork()`
  - **Create `ClientATMData.java`** in the GUI package (client-side only):
    - Static fields to hold current ATM session data: `List<AccountSummary> accounts`, `AccountSummary selectedAccount`
    - Methods: `setAccounts(List<AccountSummary>)`, `getAccounts()`, `getSelectedAccount()`, `setSelectedAccount(AccountSummary)`
    - Cleared when ATM screen closes
  - **Update `ATMBlock.useItemOn()`**:
    - Instead of directly opening BankScreen, send `OpenATMPayload` to server
    - On client side: `PacketDistributor.sendToServer(new OpenATMPayload())`
    - The screen will open when the server responds with `AccountListPayload`
  - Register both payloads in `ModPayloads.java`.

  **Must NOT do**:
  - Do not send full `AccountHolder` objects to client (security risk — don't expose passwords, internal UUIDs unnecessarily)
  - Do not cache data between ATM sessions (clear on close)
  - Do not create sub-screen packets yet (Tasks 8-13)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Core networking task — requires NeoForge payload API, StreamCodec for complex types (lists of records), server→client data flow, client-side state management
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO (depends on Task 5)
  - **Parallel Group**: Wave 2 (sequential with Task 7)
  - **Blocks**: Task 7 (MainMenuLayer needs account data to populate selector)
  - **Blocked By**: Task 5 (needs ModPayloads registry)

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/block/custom/ATMBlock.java:56-61` — Current screen opening code to replace with packet send
  - `src/main/java/net/austizz/ultimatebankingsystem/bank/handler/BankManager.java:47-56` — `getCentralBank()` server-side entry point
  - `src/main/java/net/austizz/ultimatebankingsystem/bank/centralbank/CentralBank.java:47-58` — `SearchForAccount(playerUUID)` returns all player accounts across all banks
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:55-75` — Account fields to expose in summary: `getAccountUUID()`, `getAccountType()`, `getBalance()`, `getBankId()`, `isPrimaryAccount()`
  - `src/main/java/net/austizz/ultimatebankingsystem/bank/Bank.java:46-47` — `getBankName()` to include in account summary

  **API/Type References**:
  - `net.neoforged.neoforge.network.PacketDistributor` — `sendToServer(payload)` from client, `sendToPlayer(player, payload)` from server
  - `net.minecraft.network.codec.ByteBufCodecs` — Has `STRING_UTF8`, `BOOL`, codecs for primitives. Use `ByteBufCodecs.collection(ArrayList::new, elementCodec)` for lists.
  - `StreamCodec.composite()` — Up to 6 fields per composite; for more, nest records or use custom codec.

  **WHY Each Reference Matters**:
  - ATMBlock.java:56-61 — This is WHERE the packet send replaces the direct screen open
  - CentralBank.java:47-58 — This is the server-side lookup the handler must call
  - AccountHolder.java:55-75 — These are the fields to extract for the summary (NOT the password or internal fields)

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: ATM open triggers handshake and screen opens with account data
    Tool: Bash (./gradlew runClient)
    Preconditions: Single-player world with an existing bank account (create via /ubs commands)
    Steps:
      1. Place ATM block
      2. Right-click ATM block
      3. Check server log for "[UBS] Sending N accounts to player ..." message
      4. Verify BankScreen opens on client
      5. Verify ClientATMData.getAccounts() is non-empty (visible via account selector in later task)
    Expected Result: Server log shows account count, client screen opens
    Failure Indicators: Screen doesn't open, server log missing, crash on packet send
    Evidence: .sisyphus/evidence/task-6-handshake.txt

  Scenario: ATM open with zero accounts shows empty state
    Tool: Bash (./gradlew runClient)
    Preconditions: New world, no bank accounts created
    Steps:
      1. Place ATM block
      2. Right-click ATM block
      3. Verify BankScreen opens
      4. Verify ClientATMData.getAccounts() is empty (server sent empty list)
    Expected Result: Screen opens, account list is empty but no crash
    Failure Indicators: Crash on empty list, screen doesn't open
    Evidence: .sisyphus/evidence/task-6-zero-accounts.txt
  ```

  **Commit**: YES
  - Message: `feat(network): add OpenATM handshake packets and client data cache`
  - Files: `network/OpenATMPayload.java`, `network/AccountListPayload.java`, `network/AccountSummary.java`, `gui/screens/ClientATMData.java`, `network/ModPayloads.java`, `block/custom/ATMBlock.java`
  - Pre-commit: `./gradlew build`

- [x] 7. MainMenuLayer Overhaul — 6 ATM Buttons + Account Selector

  **What to do**:
  - **Completely rewrite `MainMenuLayer.java`** to be the full ATM main menu.
  - **Account Selector** (top of panel, below header):
    - If `ClientATMData.getAccounts()` is empty: show a `MultiLineTextWidget` with text "No accounts found. Use /ubs commands to create an account." in red/gray. Disable all 6 operation buttons.
    - If accounts exist: Render a row of NineSliceTexturedButtons, one per account, showing "[Type] at [Bank]" (e.g., "Checking at Central Bank"). The selected account is visually highlighted (different text color or prefix arrow ">" marker). Clicking an account button sets `ClientATMData.setSelectedAccount()`.
    - If only one account: auto-select it, still show it as a button but pre-highlighted.
  - **6 Operation Buttons** (arranged in a 2-column, 3-row grid below the account selector):
    - Layout: Two columns of buttons, evenly spaced within the panel content area
    - Each button: `NineSliceTexturedButton` using `ATM_BUTTONS` texture (120x40 png, frames 120x20, 4px borders)
    - Button size on screen: 110x20 pixels each (slightly smaller than texture frame to fit 2 columns)
    - Left column: Withdraw Cash, Transfer Funds, Transaction History
    - Right column: Deposit, Balance Inquiry, Account Settings
    - Each button's `onPress`: calls `bankScreen.pushLayer(new XxxLayer(minecraft, bankScreen))` to open the sub-screen
    - Buttons are disabled (grayed out / non-clickable) when no account is selected
  - **Button labels**: Use `Component.literal("Withdraw Cash").withStyle(ChatFormatting.BLACK)` etc.
  - **Pass BankScreen reference** to MainMenuLayer constructor so it can call `pushLayer()`.
  - **Pass account data** from `ClientATMData` so the selector can render.
  - The layer's `onInit()` must use `bankScreen.getPanelLeft()` / `getPanelTop()` / `getPanelWidth()` for positioning widgets inside the themed panel (from Task 4).

  **Must NOT do**:
  - Do not implement any sub-screen layers (Tasks 8-13 create those)
  - Do not create placeholder/stub layer classes — the push calls will reference classes that don't exist yet. Use a temporary no-op lambda or comment until sub-screens are built. Actually, create minimal stub classes (`extends AbstractScreenLayer` with empty `onInit()`) so the code compiles.
  - Do not add new textures — reuse `atm_buttons.png` for everything

  **Recommended Agent Profile**:
  - **Category**: `visual-engineering`
    - Reason: UI layout task — button grid, account selector, visual hierarchy within the themed panel
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: NO
  - **Parallel Group**: Wave 2 (after Task 6, depends on Tasks 1, 4, 6)
  - **Blocks**: Tasks 8-13 (all sub-screens need the main menu buttons to navigate to them)
  - **Blocked By**: Task 1 (layer navigation), Task 4 (panel dimensions), Task 6 (account data)

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/MainMenuLayer.java:1-61` — Current MainMenuLayer to REWRITE. The existing `NineSliceTexturedButton` instantiation pattern on lines 41-54 is the template for all 6 buttons.
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/widgets/NineSliceTexturedButton.java:39-47` — Constructor signature: `(x, y, width, height, texture, u, v, frameWidth, frameHeight, textureWidth, textureHeight, leftBorder, rightBorder, topBorder, bottomBorder, message, onPress)`
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/AbstractScreenLayer.java:61-65` — `addWidget()` method for registering widgets in the layer

  **API/Type References**:
  - `net.minecraft.client.gui.components.MultiLineTextWidget` — For "No accounts found" message
  - `net.minecraft.ChatFormatting` — `BLACK` for button text, `RED`/`GRAY` for error messages
  - `ClientATMData.getAccounts()` / `ClientATMData.getSelectedAccount()` — Account data source (from Task 6)

  **WHY Each Reference Matters**:
  - MainMenuLayer.java:41-54 — This is the EXACT constructor call pattern for NineSliceTexturedButton that all 6 buttons must follow. Copy and adjust x/y positions.
  - NineSliceTexturedButton constructor — Executor needs the full parameter list to construct buttons correctly
  - AbstractScreenLayer.addWidget() — Every widget must be added via this method

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Main menu renders 6 buttons with correct labels
    Tool: Bash (./gradlew runClient)
    Preconditions: ATM placed, player has at least 1 bank account
    Steps:
      1. Right-click ATM block
      2. Verify 6 buttons are visible in a 2x3 grid layout
      3. Verify button labels: "Withdraw Cash", "Deposit", "Transfer Funds", "Balance Inquiry", "Transaction History", "Account Settings"
      4. Verify all buttons use the NineSliceTexturedButton style (not plain vanilla buttons)
      5. Verify buttons have hover state (texture changes on mouseover)
    Expected Result: 6 themed ATM buttons in grid layout with correct labels and hover states
    Failure Indicators: Missing buttons, wrong labels, vanilla button style, no hover effect
    Evidence: .sisyphus/evidence/task-7-main-menu.png

  Scenario: Account selector shows player accounts
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has 2+ bank accounts
    Steps:
      1. Right-click ATM
      2. Verify account selector area shows buttons for each account
      3. Click an account — verify it becomes highlighted/selected
      4. Verify 6 operation buttons become enabled after account selection
    Expected Result: Accounts displayed, selectable, buttons enabled after selection
    Failure Indicators: No accounts shown, buttons stay disabled, crash on selection
    Evidence: .sisyphus/evidence/task-7-account-selector.png

  Scenario: Zero accounts shows error message
    Tool: Bash (./gradlew runClient)
    Preconditions: New world, player has no bank accounts
    Steps:
      1. Right-click ATM
      2. Verify "No accounts found" message is displayed
      3. Verify all 6 operation buttons are disabled/grayed out
    Expected Result: Error message visible, buttons disabled
    Failure Indicators: Buttons clickable with no account, crash, no message shown
    Evidence: .sisyphus/evidence/task-7-no-accounts.png
  ```

  **Commit**: YES
  - Message: `feat(gui): overhaul MainMenuLayer with 6 ATM buttons and account selector`
  - Files: `MainMenuLayer.java`, stub layer files for compilation
  - Pre-commit: `./gradlew build`

- [x] 8. BalanceInquiryLayer + Balance Request Packet

  **What to do**:
  - **Create `BalanceInquiryLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Constructor takes `Minecraft`, `BankScreen` reference
    - Header text: "Balance Inquiry" (centered, white, at top of content area)
    - Display fields (read-only text widgets, using `MultiLineTextWidget` or `graphics.drawString()` in `render()`):
      - **Account Type**: e.g., "Checking Account"
      - **Bank Name**: e.g., "Central Bank"
      - **Account ID**: truncated UUID (first 8 chars + "...")
      - **Balance**: formatted as "$X,XXX.XX" with the currency symbol
      - **Created**: date of account creation
    - Data source: On layer init, send `BalanceRequestPayload` to server. Display "Loading..." until response arrives.
    - On response: update displayed fields with fresh data from server.
    - **Back button**: `NineSliceTexturedButton` labeled "Back" at bottom of layer, calls `bankScreen.popLayer()`
  - **Create `BalanceRequestPayload`** (client → server):
    - Fields: `UUID accountId`
    - Type ID: `ultimatebankingsystem:balance_request`
    - Server handler: Look up `AccountHolder` by ID, get balance, account type, bank name, creation date. Send response.
    - Log: `LOGGER.info("[UBS] Balance inquiry for account {}", accountId)`
  - **Create `BalanceResponsePayload`** (server → client):
    - Fields: `String accountType`, `String bankName`, `String balance`, `String accountId`, `String createdDate`
    - Type ID: `ultimatebankingsystem:balance_response`
    - Client handler: Update the `BalanceInquiryLayer` with received data. Use `context.enqueueWork()` to update on main thread.
  - Register both payloads in `ModPayloads.java`.
  - **Client update mechanism**: The layer needs a method like `updateData(BalanceResponsePayload)` that the client handler calls to populate the display fields. Store the response data in fields and re-render.

  **Must NOT do**:
  - Do not allow editing any values on this screen (read-only)
  - Do not show the password/PIN field
  - Do not add transaction list here (that's Task 12)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Combined GUI + networking task — layer layout, packet pair, server lookup, client-side update
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 9, 10, 11, 12, 13)
  - **Blocks**: None
  - **Blocked By**: Task 7 (needs MainMenuLayer button to navigate here)

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/MainMenuLayer.java:22-55` — Layer `onInit()` pattern: create widgets, use `addWidget()`, position relative to screen dimensions
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/MainMenuLayer.java:41-54` — NineSliceTexturedButton constructor pattern for the Back button
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:55-75` — Server-side fields to read: `getAccountUUID()`, `getAccountType()`, `getBalance()`, `getBankId()`, `getDateOfCreation()`
  - `src/main/java/net/austizz/ultimatebankingsystem/bank/Bank.java:46-47` — `getBankName()` for the bank name field
  - `src/main/java/net/austizz/ultimatebankingsystem/accountTypes/AccountTypes.java:4-13` — Enum with `.label` field for display name

  **WHY Each Reference Matters**:
  - MainMenuLayer pattern — All layers follow this exact onInit pattern
  - AccountHolder fields — Server handler reads these to build the response
  - AccountTypes.label — Use this for human-readable type name, not the enum name

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Balance screen displays account data from server
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has a Checking Account at Central Bank with $500.00 balance
    Steps:
      1. Open ATM, select the Checking Account
      2. Click "Balance Inquiry" button
      3. Verify sub-screen opens with header "Balance Inquiry"
      4. Verify displayed fields: account type shows "Checking Account", bank name shows "Central Bank", balance shows "$500.00"
      5. Verify server log shows "[UBS] Balance inquiry for account ..."
    Expected Result: All fields populated with correct account data
    Failure Indicators: "Loading..." never resolves, wrong data, missing fields
    Evidence: .sisyphus/evidence/task-8-balance-inquiry.png

  Scenario: Back button returns to main menu
    Tool: Bash (./gradlew runClient)
    Preconditions: Balance Inquiry screen is open
    Steps:
      1. Click "Back" button
      2. Verify main menu with 6 buttons is shown again
      3. Verify account selector still shows the previously selected account
    Expected Result: Main menu restored, account selection preserved
    Failure Indicators: Crash, blank screen, account selection lost
    Evidence: .sisyphus/evidence/task-8-back-navigation.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add BalanceInquiryLayer with balance display packet`
  - Files: `layers/BalanceInquiryLayer.java`, `network/BalanceRequestPayload.java`, `network/BalanceResponsePayload.java`, `network/ModPayloads.java`
  - Pre-commit: `./gradlew build`

- [x] 9. WithdrawLayer + Withdraw Packets

  **What to do**:
  - **Create `WithdrawLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Header text: "Withdraw Cash" (centered, white)
    - **Preset amount buttons** (5 NineSliceTexturedButtons in a row or 2-row grid):
      - "$20", "$50", "$100", "$200", "$500"
      - Clicking a preset button immediately sends the withdraw request (no confirmation needed for presets)
    - **Custom amount section**:
      - A `NineSliceTexturedButton` labeled "Custom Amount" that reveals a text input field
      - An `EditBox` (Minecraft's text input widget) for typing a custom dollar amount
      - Input validation: only allow digits and one decimal point, max 2 decimal places
      - A "Confirm" `NineSliceTexturedButton` to submit the custom amount
    - **Result display area**: After sending request, show "Processing..." then update with success message ("Withdrew $X.XX — New balance: $Y.YY") or error message ("Insufficient funds" / "Invalid amount") in red.
    - **Back button**: NineSliceTexturedButton "Back" at bottom, calls `bankScreen.popLayer()`
  - **Create `WithdrawRequestPayload`** (client → server):
    - Fields: `UUID accountId`, `String amount` (string to preserve decimal precision)
    - Type ID: `ultimatebankingsystem:withdraw_request`
    - Server handler:
      - Parse amount as BigDecimal
      - Validate: amount > 0, account exists, balance sufficient
      - Call `accountHolder.RemoveBalance(amount)`
      - Send response with success/failure and new balance
      - Log: `LOGGER.info("[UBS] Withdraw ${} from account {} — success: {}", amount, accountId, success)`
  - **Create `WithdrawResponsePayload`** (server → client):
    - Fields: `boolean success`, `String newBalance`, `String errorMessage`
    - Type ID: `ultimatebankingsystem:withdraw_response`
    - Client handler: Update WithdrawLayer's result display area
  - Register both payloads in `ModPayloads.java`.

  **Must NOT do**:
  - Do not give the player physical items on withdraw (virtual currency only)
  - Do not allow negative or zero amounts
  - Do not modify AccountHolder.RemoveBalance logic (already fixed in Task 3)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Combined GUI (preset buttons + text input + result display) + networking (request/response + validation)
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 10, 11, 12, 13)
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/gui/screens/layers/MainMenuLayer.java:41-54` — NineSliceTexturedButton pattern for all buttons
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:86-97` — `RemoveBalance()` method the server handler calls

  **API/Type References**:
  - `net.minecraft.client.gui.components.EditBox` — Minecraft's text input field widget. Constructor: `EditBox(font, x, y, width, height, message)`. Use `setFilter()` for input validation, `getValue()` to read input.
  - `java.math.BigDecimal` — Parse and validate the amount string server-side

  **WHY Each Reference Matters**:
  - NineSliceTexturedButton pattern — 5 preset buttons + custom confirm + back all follow this pattern
  - RemoveBalance — Server handler calls this; the off-by-one fix from Task 3 must be in place
  - EditBox API — Custom amount input requires Minecraft's text field widget

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Preset withdraw deducts from balance
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has Checking Account with $500.00 balance
    Steps:
      1. Open ATM, select account, click "Withdraw Cash"
      2. Click "$100" preset button
      3. Verify "Processing..." appears briefly
      4. Verify success message shows "Withdrew $100.00 — New balance: $400.00"
      5. Check server log for "[UBS] Withdraw $100 from account ... — success: true"
    Expected Result: Balance reduced by $100, success message shown
    Failure Indicators: No balance change, error shown for valid amount, server log missing
    Evidence: .sisyphus/evidence/task-9-preset-withdraw.png

  Scenario: Insufficient funds shows error
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has Checking Account with $30.00 balance
    Steps:
      1. Open ATM, select account, click "Withdraw Cash"
      2. Click "$50" preset button
      3. Verify error message displayed in red: "Insufficient funds"
      4. Verify balance unchanged at $30.00
    Expected Result: Error message shown, balance not modified
    Failure Indicators: Balance goes negative, no error shown, crash
    Evidence: .sisyphus/evidence/task-9-insufficient-funds.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add WithdrawLayer with preset amounts and custom input`
  - Files: `layers/WithdrawLayer.java`, `network/WithdrawRequestPayload.java`, `network/WithdrawResponsePayload.java`, `network/ModPayloads.java`
  - Pre-commit: `./gradlew build`

- [x] 10. DepositLayer + Deposit Packets

  **What to do**:
  - **Create `DepositLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Header text: "Deposit Funds" (centered, white)
    - **Amount input**: `EditBox` for typing the deposit amount. Same validation as Withdraw: digits + one decimal point, max 2 decimal places.
    - **Quick deposit buttons** (optional convenience, 3 NineSliceTexturedButtons): "$50", "$100", "$500" — clicking fills the EditBox with that amount.
    - **Confirm button**: NineSliceTexturedButton "Confirm Deposit" — sends deposit request with the entered amount
    - **Result display**: "Processing..." → success message ("Deposited $X.XX — New balance: $Y.YY") or error ("Invalid amount")
    - **Back button**: NineSliceTexturedButton "Back", calls `bankScreen.popLayer()`
  - **Create `DepositRequestPayload`** (client → server):
    - Fields: `UUID accountId`, `String amount`
    - Type ID: `ultimatebankingsystem:deposit_request`
    - Server handler:
      - Parse amount as BigDecimal, validate > 0
      - Call `accountHolder.AddBalance(amount)`
      - Send response with new balance
      - Log: `LOGGER.info("[UBS] Deposit ${} to account {} — success: {}", amount, accountId, success)`
  - **Create `DepositResponsePayload`** (server → client):
    - Fields: `boolean success`, `String newBalance`, `String errorMessage`
    - Type ID: `ultimatebankingsystem:deposit_response`
    - Client handler: Update DepositLayer's result display
  - Register both payloads in `ModPayloads.java`.

  **Must NOT do**:
  - Do not take items from player inventory (virtual deposit only)
  - Do not allow negative or zero amounts
  - Do not add withdrawal logic here

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: GUI + networking combined task, similar pattern to WithdrawLayer
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 9, 11, 12, 13)
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - Task 9's `WithdrawLayer` — Near-identical structure (amount input + confirm + result display). Follow the same layout pattern.
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:77-84` — `AddBalance()` method the server handler calls

  **API/Type References**:
  - `EditBox` — Same as Task 9
  - `AccountHolder.AddBalance(BigDecimal)` — Returns boolean, validates > 0 internally

  **WHY Each Reference Matters**:
  - WithdrawLayer pattern — DepositLayer is structurally almost identical; follow the same code organization
  - AddBalance — Server handler calls this to add funds

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Deposit adds to balance
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has Checking Account with $200.00 balance
    Steps:
      1. Open ATM, select account, click "Deposit"
      2. Type "150.00" in the amount field
      3. Click "Confirm Deposit"
      4. Verify success message: "Deposited $150.00 — New balance: $350.00"
      5. Check server log for "[UBS] Deposit $150 to account ... — success: true"
    Expected Result: Balance increased to $350.00, success shown
    Failure Indicators: Balance unchanged, error for valid amount
    Evidence: .sisyphus/evidence/task-10-deposit.png

  Scenario: Zero/negative amount rejected
    Tool: Bash (./gradlew runClient)
    Preconditions: Deposit screen open
    Steps:
      1. Type "0" in amount field, click Confirm
      2. Verify error message shown (e.g., "Invalid amount")
      3. Type "-50" in amount field, click Confirm
      4. Verify error message shown
    Expected Result: Both amounts rejected with error, balance unchanged
    Failure Indicators: Zero/negative deposit succeeds
    Evidence: .sisyphus/evidence/task-10-invalid-amount.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add DepositLayer with amount input and confirmation`
  - Files: `layers/DepositLayer.java`, `network/DepositRequestPayload.java`, `network/DepositResponsePayload.java`, `network/ModPayloads.java`
  - Pre-commit: `./gradlew build`

- [x] 11. TransferLayer + Transfer Packets

  **What to do**:
  - **Create `TransferLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Header text: "Transfer Funds" (centered, white)
    - **Recipient field**: `EditBox` labeled "Recipient Account ID" — player types the target account UUID. Full UUID format (e.g., `550e8400-e29b-41d4-a716-446655440000`).
    - **Amount field**: `EditBox` labeled "Amount ($)" — same digit+decimal validation as Deposit/Withdraw.
    - **Confirm button**: NineSliceTexturedButton "Confirm Transfer"
    - **Confirmation step**: After clicking Confirm, show a confirmation panel: "Transfer $X.XX to account [UUID]? This cannot be undone." with two buttons: "Yes, Transfer" and "Cancel". Only send the packet on "Yes."
    - **Result display**: "Processing..." → success ("Transferred $X.XX — New balance: $Y.YY") or error ("Account not found" / "Insufficient funds" / "Cannot transfer to yourself") in red.
    - **Back button**: NineSliceTexturedButton "Back"
  - **Create `TransferRequestPayload`** (client → server):
    - Fields: `UUID senderAccountId`, `UUID recipientAccountId`, `String amount`
    - Type ID: `ultimatebankingsystem:transfer_request`
    - Server handler:
      - Parse amount, validate > 0
      - Look up both accounts via CentralBank
      - Validate: sender exists, recipient exists, sender != recipient, sufficient balance
      - Create a `UserTransaction` and call `makeTransaction(server)`
      - Send response with result
      - Log: `LOGGER.info("[UBS] Transfer ${} from {} to {} — success: {}", amount, senderId, recipientId, success)`
  - **Create `TransferResponsePayload`** (server → client):
    - Fields: `boolean success`, `String newBalance`, `String errorMessage`
    - Type ID: `ultimatebankingsystem:transfer_response`
    - Client handler: Update TransferLayer result display
  - Register both payloads in `ModPayloads.java`.

  **Must NOT do**:
  - Do not implement player-name lookup (user chose UUID input)
  - Do not bypass the existing `UserTransaction.makeTransaction()` logic (use it as-is for rate limiting, etc.)
  - Do not skip the confirmation step for transfers

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Most complex sub-screen — two inputs, confirmation dialog, transaction creation, multiple error cases
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 9, 10, 12, 13)
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/account/transaction/UserTransaction.java:63-103` — `makeTransaction()` method. The server handler should create a `UserTransaction` and call this. It handles rate limiting, balance checks, and recording.
  - `src/main/java/net/austizz/ultimatebankingsystem/bank/centralbank/CentralBank.java:60-70` — `SearchForAccountByAccountId(UUID)` for looking up recipient account

  **API/Type References**:
  - `UserTransaction(senderUUID, receiverUUID, amount, timestamp, description)` — Constructor for creating a transfer transaction
  - `EditBox` — For both recipient UUID and amount fields
  - `UUID.fromString(String)` — Parse the typed UUID, catch `IllegalArgumentException` for validation

  **WHY Each Reference Matters**:
  - UserTransaction.makeTransaction() — MUST reuse this. It has rate limiting (Bucket4j) and proper balance checks. Don't re-implement.
  - CentralBank.SearchForAccountByAccountId — Server handler needs this to validate recipient exists

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Successful transfer between accounts
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has Account A with $500, another account (Account B) exists (can be same player's second account or another player's)
    Steps:
      1. Open ATM, select Account A, click "Transfer Funds"
      2. Enter Account B's UUID in recipient field
      3. Enter "100.00" in amount field
      4. Click "Confirm Transfer"
      5. Verify confirmation dialog appears: "Transfer $100.00 to account [UUID]?"
      6. Click "Yes, Transfer"
      7. Verify success message: "Transferred $100.00 — New balance: $400.00"
      8. Check server log for transfer confirmation
    Expected Result: $100 moved from A to B, success message shown
    Failure Indicators: Balance unchanged, no confirmation dialog, error on valid transfer
    Evidence: .sisyphus/evidence/task-11-transfer-success.png

  Scenario: Invalid recipient UUID shows error
    Tool: Bash (./gradlew runClient)
    Preconditions: Transfer screen open
    Steps:
      1. Enter "not-a-valid-uuid" in recipient field
      2. Enter "50.00" in amount field
      3. Click Confirm → Yes
      4. Verify error: "Account not found" or "Invalid account ID"
    Expected Result: Error message shown, no balance change
    Failure Indicators: Crash on invalid UUID, transfer succeeds to nonexistent account
    Evidence: .sisyphus/evidence/task-11-invalid-recipient.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add TransferLayer with recipient UUID and amount fields`
  - Files: `layers/TransferLayer.java`, `network/TransferRequestPayload.java`, `network/TransferResponsePayload.java`, `network/ModPayloads.java`
  - Pre-commit: `./gradlew build`

- [x] 12. TransactionHistoryLayer + Transaction History Packet

  **What to do**:
  - **Create `TransactionHistoryLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Header text: "Transaction History" (centered, white)
    - **Scrollable list**: Display up to 50 most recent transactions, ordered newest-first.
    - Each transaction entry shows:
      - Date/time (formatted: "MM/dd/yyyy HH:mm")
      - Description (from `UserTransaction.getTransactionDescription()`)
      - Amount with direction indicator: green "+$X.XX" for incoming, red "-$X.XX" for outgoing
      - Sender/Receiver account UUID (truncated to first 8 chars)
    - **Scrolling**: Use vanilla Minecraft scrolling mechanism. If the list exceeds the visible area, allow mouse wheel scrolling. Implement via a custom render method that clips and offsets entries based on a `scrollOffset` int field. Mouse wheel events adjust `scrollOffset`.
    - **Empty state**: If no transactions, show "No transactions yet." centered in gray.
    - **Loading state**: Show "Loading..." until server responds.
    - **Back button**: NineSliceTexturedButton "Back"
  - **Create `TxHistoryRequestPayload`** (client → server):
    - Fields: `UUID accountId`, `int maxEntries` (default 50)
    - Type ID: `ultimatebankingsystem:tx_history_request`
    - Server handler:
      - Get `AccountHolder`, retrieve `getTransactions()` map
      - Sort by timestamp descending, take top `maxEntries`
      - Build list of transaction summaries
      - Send response
      - Log: `LOGGER.info("[UBS] Tx history for account {}: {} entries", accountId, count)`
  - **Create `TxHistoryResponsePayload`** (server → client):
    - Fields: List of transaction summaries. Each summary: `String date`, `String description`, `String amount`, `boolean isIncoming`, `String counterpartyId` (first 8 chars of sender/receiver UUID)
    - Type ID: `ultimatebankingsystem:tx_history_response`
    - Client handler: Update TransactionHistoryLayer with received list
  - **Create `TransactionSummary`** helper record for the transferred data.
  - Register both payloads in `ModPayloads.java`.

  **Must NOT do**:
  - Do not add filtering, sorting, or search functionality
  - Do not add pagination beyond the 50-entry limit
  - Do not allow clicking on transactions for details
  - Do not show internal UUIDs fully (truncate for privacy/readability)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Complex rendering (scrollable list with formatted entries) + networking (list serialization with StreamCodec)
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 9, 10, 11, 13)
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:171-176` — `getTransactions()` returns `ConcurrentHashMap<UUID, UserTransaction>`
  - `src/main/java/net/austizz/ultimatebankingsystem/account/transaction/UserTransaction.java:44-61` — Transaction getters: `getSenderUUID()`, `getReceiverUUID()`, `getAmount()`, `getTimestamp()`, `getTransactionDescription()`

  **API/Type References**:
  - `net.minecraft.client.gui.GuiGraphics.enableScissor(x1, y1, x2, y2)` / `disableScissor()` — For clipping the scrollable area
  - `java.time.format.DateTimeFormatter` — Format `LocalDateTime` to "MM/dd/yyyy HH:mm"
  - `ByteBufCodecs.collection(ArrayList::new, summaryCodec)` — For serializing the list of summaries

  **WHY Each Reference Matters**:
  - AccountHolder.getTransactions() — Server handler reads this, must sort by timestamp
  - UserTransaction getters — Each field maps to a display column in the list
  - GuiGraphics.enableScissor — Essential for clipping the scrollable area so entries don't overflow the panel

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Transaction history displays entries
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has account with at least 3 transactions (create via transfers)
    Steps:
      1. Open ATM, select account, click "Transaction History"
      2. Verify list shows transactions with date, description, amount, direction
      3. Verify newest transaction is at the top
      4. Verify incoming amounts show in green with "+" prefix
      5. Verify outgoing amounts show in red with "-" prefix
    Expected Result: Sorted transaction list with correct formatting
    Failure Indicators: Empty list despite transactions existing, wrong order, missing fields
    Evidence: .sisyphus/evidence/task-12-tx-history.png

  Scenario: Empty transaction history
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has account with zero transactions
    Steps:
      1. Open ATM, select account, click "Transaction History"
      2. Verify "No transactions yet." message displayed
    Expected Result: Empty state message shown
    Failure Indicators: Crash on empty list, blank screen
    Evidence: .sisyphus/evidence/task-12-empty-history.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add TransactionHistoryLayer with scrollable list`
  - Files: `layers/TransactionHistoryLayer.java`, `network/TxHistoryRequestPayload.java`, `network/TxHistoryResponsePayload.java`, `network/TransactionSummary.java`, `network/ModPayloads.java`
  - Pre-commit: `./gradlew build`

- [x] 13. AccountSettingsLayer + Settings Packets

  **What to do**:
  - **Create `AccountSettingsLayer.java`** in `gui/screens/layers/`:
    - Extends `AbstractScreenLayer`
    - Header text: "Account Settings" (centered, white)
    - **Three sections**, stacked vertically:
    - **Section 1 — Account Info** (read-only display):
      - Account ID: full UUID (with a small NineSliceTexturedButton "Copy" next to it that copies to clipboard — useful for the Transfer feature)
      - Account Type: e.g., "Checking Account"
      - Bank Name: e.g., "Central Bank"
      - Created: date of creation
      - Balance: current balance
    - **Section 2 — Primary Account Toggle**:
      - A NineSliceTexturedButton showing current state: "Primary: YES" (green) or "Primary: NO" (gray)
      - Clicking toggles the primary flag and sends a packet to server
      - After server response, button text updates
    - **Section 3 — Change PIN/Password**:
      - Current password `EditBox` (masked/hidden characters if Minecraft supports it, otherwise plain text with a note)
      - New password `EditBox`
      - Confirm new password `EditBox`
      - NineSliceTexturedButton "Change PIN"
      - **Confirmation**: "Are you sure you want to change your PIN?" — "Yes" / "Cancel"
      - Validation: new password matches confirm, not empty
    - **Back button**: NineSliceTexturedButton "Back"
  - **Create `SetPrimaryPayload`** (client → server):
    - Fields: `UUID accountId`, `boolean setPrimary`
    - Type ID: `ultimatebankingsystem:set_primary`
    - Server handler: Toggle `accountHolder.setPrimaryAccount(setPrimary)`. Send response.
    - Log: `LOGGER.info("[UBS] Set primary={} for account {}", setPrimary, accountId)`
  - **Create `SetPrimaryResponsePayload`** (server → client):
    - Fields: `boolean success`, `boolean newPrimaryState`
    - Type ID: `ultimatebankingsystem:set_primary_response`
  - **Create `ChangePinPayload`** (client → server):
    - Fields: `UUID accountId`, `String currentPin`, `String newPin`
    - Type ID: `ultimatebankingsystem:change_pin`
    - Server handler: Verify current PIN matches `accountHolder`'s stored password. If match, update password field. Send response.
    - Note: AccountHolder doesn't have a public `setPassword()` method. Add one: `public void setPassword(String newPassword)` that sets `this.password = newPassword` and calls `BankManager.markDirty()`. This is the ONE allowed modification to AccountHolder beyond the Task 3 fix.
    - Log: `LOGGER.info("[UBS] PIN change for account {} — success: {}", accountId, success)`
  - **Create `ChangePinResponsePayload`** (server → client):
    - Fields: `boolean success`, `String errorMessage`
    - Type ID: `ultimatebankingsystem:change_pin_response`
  - Register all 4 payloads in `ModPayloads.java`.
  - **Clipboard copy**: Use `Minecraft.getInstance().keyboardHandler.setClipboard(uuid)` for the "Copy" button on account ID.

  **Must NOT do**:
  - Do not add account creation or deletion features
  - Do not allow changing account type or bank
  - Do not allow editing balance (that's what Deposit/Withdraw are for)
  - Do not expose the password in the Account Info section (it's only in the Change PIN section inputs)

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
    - Reason: Most widgets of any screen (info display + toggle + 3 text fields + copy button + confirmation). Plus 4 packet types to register.
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 3 (with Tasks 8, 9, 10, 11, 12)
  - **Blocks**: None
  - **Blocked By**: Task 7

  **References**:

  **Pattern References**:
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:159-165` — `setPrimaryAccount()` / `isPrimaryAccount()` — Server handler toggles this
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:43-53` — Constructor shows `password` is stored as a String field. There's no public setter — **add `setPassword(String)` method**.
  - `src/main/java/net/austizz/ultimatebankingsystem/account/AccountHolder.java:178-205` — `save()` method that persists `password` — confirms the field is saved/loaded

  **API/Type References**:
  - `Minecraft.getInstance().keyboardHandler.setClipboard(String)` — Copy text to system clipboard
  - `EditBox.setFormatter()` — Can be used to mask password characters (replace each char with '*')
  - `AccountHolder.setPrimaryAccount(boolean)` — Existing method for the toggle

  **WHY Each Reference Matters**:
  - AccountHolder.java:159-165 — setPrimaryAccount already exists and calls markDirty. Server handler just calls this.
  - AccountHolder.java:43-53 — password field exists but has no public setter. Must add one (the ONLY AccountHolder modification beyond Task 3).
  - AccountHolder.save() — Confirms password is persisted, so the change is durable.

  **Acceptance Criteria**:

  **QA Scenarios (MANDATORY):**

  ```
  Scenario: Account info displays correctly
    Tool: Bash (./gradlew runClient)
    Preconditions: Player has a Checking Account at Central Bank
    Steps:
      1. Open ATM, select account, click "Account Settings"
      2. Verify Account ID, Type, Bank Name, Created, Balance fields are displayed
      3. Click "Copy" next to Account ID
      4. Verify UUID is now in system clipboard (paste somewhere to verify)
    Expected Result: All info fields populated, Copy works
    Failure Indicators: Missing fields, wrong data, Copy doesn't work
    Evidence: .sisyphus/evidence/task-13-account-info.png

  Scenario: Primary toggle updates state
    Tool: Bash (./gradlew runClient)
    Preconditions: Account Settings open, account is NOT primary
    Steps:
      1. Verify button shows "Primary: NO"
      2. Click the primary toggle button
      3. Verify button updates to "Primary: YES" (green)
      4. Check server log for "[UBS] Set primary=true for account ..."
    Expected Result: Primary state toggled, button text updated
    Failure Indicators: Button doesn't change, server error, no log message
    Evidence: .sisyphus/evidence/task-13-primary-toggle.png

  Scenario: PIN change with confirmation
    Tool: Bash (./gradlew runClient)
    Preconditions: Account Settings open, current PIN is "1234"
    Steps:
      1. Enter "1234" in current password field
      2. Enter "5678" in new password field
      3. Enter "5678" in confirm password field
      4. Click "Change PIN"
      5. Verify confirmation dialog: "Are you sure you want to change your PIN?"
      6. Click "Yes"
      7. Verify success message
      8. Check server log for "[UBS] PIN change for account ... — success: true"
    Expected Result: PIN changed successfully
    Failure Indicators: Wrong password accepted, mismatched confirm accepted, no confirmation dialog
    Evidence: .sisyphus/evidence/task-13-pin-change.png
  ```

  **Commit**: YES
  - Message: `feat(gui): add AccountSettingsLayer with info view, primary toggle, PIN change`
  - Files: `layers/AccountSettingsLayer.java`, `network/SetPrimaryPayload.java`, `network/SetPrimaryResponsePayload.java`, `network/ChangePinPayload.java`, `network/ChangePinResponsePayload.java`, `network/ModPayloads.java`, `AccountHolder.java` (add setPassword method)
  - Pre-commit: `./gradlew build`

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

> 4 review agents run in PARALLEL. ALL must APPROVE. Present consolidated results to user and get explicit "okay" before completing.
>
> **Do NOT auto-proceed after verification. Wait for user's explicit approval before marking work complete.**

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Read the plan end-to-end. For each "Must Have": verify implementation exists (read file, check class/method). For each "Must NOT Have": search codebase for forbidden patterns (client imports in common code, item-based deposit logic, etc.) — reject with file:line if found. Check evidence files exist in `.sisyphus/evidence/`. Compare deliverables against plan.
  Output: `Must Have [N/N] | Must NOT Have [N/N] | Tasks [N/N] | VERDICT: APPROVE/REJECT`

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run `./gradlew build` to verify compilation. Review all changed/new files for: `as any`/`@SuppressWarnings` abuse, empty catches, `System.out.println` in prod (use `UltimateBankingSystem.LOGGER`), commented-out code, unused imports. Check AI slop: excessive comments, over-abstraction, generic variable names (data/result/item/temp). Verify all `NineSliceTexturedButton` instances use consistent constructor pattern.
  Output: `Build [PASS/FAIL] | Files [N clean/N issues] | VERDICT`

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Launch game client via `./gradlew runClient`. Place ATM block, right-click to open. Verify: main menu shows 6 buttons + account selector. Click each button, verify sub-screen opens with correct widgets. Click Back on each sub-screen, verify return to main menu. Test with zero accounts (new world). Test with existing account (create via commands first). Capture screenshots of every screen state. Save to `.sisyphus/evidence/final-qa/`.
  Output: `Screens [N/N pass] | Navigation [N/N] | Edge Cases [N tested] | VERDICT`

- [ ] F4. **Scope Fidelity Check** — `deep`
  For each task: read "What to do", read actual implementation. Verify 1:1 — everything in spec was built (no missing), nothing beyond spec was built (no creep). Check "Must NOT do" compliance. Detect cross-task contamination. Flag unaccounted changes (files modified that no task specified).
  Output: `Tasks [N/N compliant] | Contamination [CLEAN/N issues] | Unaccounted [CLEAN/N files] | VERDICT`

---

## Commit Strategy

| Commit | Message | Key Files | Pre-commit Check |
|--------|---------|-----------|-----------------|
| 1 | `fix(gui): correct rendering order in BankScreen, add layer navigation stack` | `BankScreen.java` | `./gradlew build` |
| 2 | `fix(block): move client-only screen opening to safe helper in ATMBlock` | `ATMBlock.java`, new helper class | `./gradlew build` |
| 3 | `fix(account): correct RemoveBalance comparison to allow exact withdrawals` | `AccountHolder.java` | `./gradlew build` |
| 4 | `feat(gui): add themed ATM panel background rendering` | `BankScreen.java` | `./gradlew build` |
| 5 | `feat(network): add ModPayloads registry and base packet infrastructure` | `ModPayloads.java`, `UltimateBankingSystem.java` | `./gradlew build` |
| 6 | `feat(network): add OpenATM handshake packets and client data cache` | `OpenATMPayload.java`, `AccountListPayload.java`, `ClientATMData.java` | `./gradlew build` |
| 7 | `feat(gui): overhaul MainMenuLayer with 6 ATM buttons and account selector` | `MainMenuLayer.java` | `./gradlew build` |
| 8 | `feat(gui): add BalanceInquiryLayer with balance display packet` | `BalanceInquiryLayer.java`, packet files | `./gradlew build` |
| 9 | `feat(gui): add WithdrawLayer with preset amounts and custom input` | `WithdrawLayer.java`, packet files | `./gradlew build` |
| 10 | `feat(gui): add DepositLayer with amount input and confirmation` | `DepositLayer.java`, packet files | `./gradlew build` |
| 11 | `feat(gui): add TransferLayer with recipient UUID and amount fields` | `TransferLayer.java`, packet files | `./gradlew build` |
| 12 | `feat(gui): add TransactionHistoryLayer with scrollable list` | `TransactionHistoryLayer.java`, packet files | `./gradlew build` |
| 13 | `feat(gui): add AccountSettingsLayer with info view, primary toggle, PIN change` | `AccountSettingsLayer.java`, packet files | `./gradlew build` |

---

## Success Criteria

### Verification Commands
```bash
./gradlew build            # Expected: BUILD SUCCESSFUL
./gradlew runClient        # Expected: Game launches, ATM block placeable, GUI opens on right-click
./gradlew runServer        # Expected: Dedicated server starts without crash (no client class errors)
```

### Final Checklist
- [ ] All 6 ATM buttons visible on main menu with NineSliceTexturedButton styling
- [ ] Account selector populated from server data
- [ ] Each button navigates to correct sub-screen
- [ ] Back button returns to main menu from every sub-screen
- [ ] Withdraw modifies server balance (visible in logs)
- [ ] Deposit modifies server balance (visible in logs)
- [ ] Transfer moves funds between accounts (visible in logs)
- [ ] Balance Inquiry shows real account data
- [ ] Transaction History shows real transaction records
- [ ] Account Settings displays info and allows changes
- [ ] Game compiles and runs on both client and dedicated server
- [ ] No `net.minecraft.client.*` imports in common-side code
- [ ] Themed ATM panel renders correctly (dark background, header, borders)
