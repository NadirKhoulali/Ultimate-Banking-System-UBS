# Learnings — ATM GUI

## Project Conventions
- Package: `net.austizz.ultimatebankingsystem`
- Mod ID: `ultimatebankingsystem`
- NeoForge 1.21.1, Java 21
- Logging: `UltimateBankingSystem.LOGGER` (not System.out)
- GUI textures: `assets/ultimatebankingsystem/textures/gui/`
- NineSliceTexturedButton constructor: (x, y, width, height, texture, u, v, frameWidth, frameHeight, textureWidth, textureHeight, leftBorder, rightBorder, topBorder, bottomBorder, message, onPress)
- atm_buttons.png: 120x40, two 120x20 frames (normal at v=0, hover at v=20), 4px borders all sides
- Currency: US Dollar ($), virtual, 2 decimal places
- Backend entry: BankManager.getCentralBank(server) → CentralBank → Bank → AccountHolder
- Account types enum: AccountTypes (CheckingAccount, SavingAccount, MoneyMarketAccount, CertificateAccount) with .label field

## Code Patterns
- Layers extend AbstractScreenLayer, override onInit(), use addWidget() to register widgets
- ScreenLayer interface: init(w,h), render(), tick(), removed(), children(), renderables()
- BankScreen manages layers, registers their widgets via addRenderableWidget()

## NeoForge 1.21.1 Networking Pattern
- Registration class: `ModPayloads.java` in `network` package
- Annotation: `@EventBusSubscriber(modid = UltimateBankingSystem.MODID)` — do NOT use deprecated `bus = Bus.MOD`; NeoForge auto-routes based on event type (`IModBusEvent`)
- Event: `RegisterPayloadHandlersEvent` fires on mod bus during startup
- Registrar: `event.registrar("1")` — parameter is a version string (NOT mod ID)
- Imports: `net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent`, `net.neoforged.neoforge.network.registration.PayloadRegistrar`
- Payload record pattern:
  - `implements CustomPacketPayload`
  - `Type<T>` with `ResourceLocation.fromNamespaceAndPath("ultimatebankingsystem", "name")`
  - `StreamCodec<RegistryFriendlyByteBuf, T>` via `StreamCodec.composite()`
  - `ByteBufCodecs.STRING_UTF8`, `ByteBufCodecs.VAR_INT` etc. for field codecs
- Registration: `registrar.playToServer(TYPE, STREAM_CODEC, handler)` or `.playToClient()` or `.playBidirectional()`
- Handler signature: `(PayloadType payload, IPayloadContext context)` — use `context.enqueueWork()` for thread safety
- Sending: `PacketDistributor.sendToServer(payload)` (client→server), `PacketDistributor.sendToPlayer(player, payload)` (server→client)

## Task 2: BankScreen Stack Navigation
- BankScreen uses Deque<ScreenLayer> (ArrayDeque) as a layer stack (push/pop)
- pushLayer(): clearWidgets, push, setBankScreen, init, registerLayerWidgets
- popLayer(): if size<=1 close screen, else pop+removed, clearWidgets, re-init top
- Render order fix: graphics.fill() BEFORE super.render() — background draws under widgets
- Panel: PANEL_WIDTH=260, PANEL_HEIGHT=220, centered. Getters: getPanelLeft/Top/Width/Height
- ESC key (code 256) pops layer if stack>1, otherwise default close behavior
- Screen.init() called on resize — stack survives, only top layer re-inited
- ScreenLayer.setBankScreen() is a default method on the interface
- AbstractScreenLayer stores bankScreen as protected field

## Task 5: Client-Side Isolation
- ATMBlock must NOT import `net.minecraft.client.*` (common-side code)
- Client-only screen opening extracted to `ATMScreenHelper` with `@OnlyIn(Dist.CLIENT)`
- `Blocks` import kept in ATMBlock — used by `updateShape()` and `playerWillDestroy()` for AIR
- `Items` import was only used by debug diamond→gold code (removed)
- Pre-existing unused imports: `SubscribeEvent`, `PlayerInteractEvent`, `BlockEntity`, `LevelReader` — left untouched (not caused by our changes)

## Task 6: OpenATM Handshake & Client Cache
- ATM interaction flow: client right-click → `OpenATMPayload` (empty, C→S) → server looks up accounts → `AccountListPayload` (S→C) → client caches in `ClientATMData` → opens screen
- `AccountSummary` record: lightweight client-safe DTO with (accountId, accountType, bankName, balance, isPrimary)
- UUID StreamCodec: no built-in `UUIDUtil.STREAM_CODEC` in NeoForge 1.21.1; use manual `StreamCodec.of()` with `writeLong`/`readLong` for most/least significant bits
- Empty payload (no fields): `StreamCodec.of((buf, val) -> {}, buf -> new OpenATMPayload())` — `StreamCodec.unit()` may not exist
- List serialization: `AccountSummary.STREAM_CODEC.apply(ByteBufCodecs.list(256))` — 256 is max list size
- `StreamCodec.composite()` supports 5 fields in NeoForge 1.21.1 (confirmed by build)
- All StreamCodecs use `RegistryFriendlyByteBuf` for consistency (even AccountSummary, to avoid type mismatch with list codec)
- `@OnlyIn(Dist.CLIENT)` imports in ModPayloads: safe because `handleAccountList` is only registered as `playToClient`, so the client-only classes are never loaded on server
- `ClientATMData.clear()` called in `BankScreen.onClose()` to prevent stale data between ATM sessions
- `AccountHolder.isPrimaryAccount()` getter already exists (line 164) — field is `private boolean isPrimaryAccount` initialized to `false`
- `CentralBank.getBank(UUID)` returns Bank from the banks ConcurrentHashMap
- ATMBlock now sends `PacketDistributor.sendToServer(new OpenATMPayload())` instead of directly calling `ATMScreenHelper.openATMScreen()`

## Task 7: MainMenuLayer + Stub Layers
- MainMenuLayer reads accounts from ClientATMData.getAccounts(), builds account selector buttons and 6 operation buttons
- Account selector: NineSliceTexturedButton per account, showing "accountType @ bankName", selected one prefixed with ">" in YELLOW
- Auto-selects single account when only one exists
- 6 operation buttons in 2x3 grid (110x20, 28px row spacing), disabled when no account selected via `button.active = false`
- selectAccount() updates button labels via setMessage() and enables operation buttons — no re-init needed
- "No accounts found" uses MultiLineTextWidget with setMaxWidth() for text wrapping
- render() draws "Select Account:" label at panelTop+27 in AQUA (0xFF55FFFF)
- 6 stub layers (WithdrawLayer, DepositLayer, TransferLayer, BalanceInquiryLayer, TransactionHistoryLayer, AccountSettingsLayer) each extend AbstractScreenLayer with just a Back button
- MultiLineTextWidget: constructor (x, y, Component, Font), call setMaxWidth() for line wrapping
- AbstractWidget.setMessage(Component) is public — works for updating button labels dynamically
- AbstractWidget.active is a public boolean field — set false to disable buttons
