# Developer Integration Tutorial

This guide targets UBS `2.1.1`, Minecraft `1.21.1`, NeoForge, and Java `21`.

## 1. Identity

- Mod ID: `ultimatebankingsystem`
- Group: `net.austizz.ultimatebankingsystem`
- Artifact: `ultimatebankingsystem`
- API version: `2.1.1`

## 2. Add UBS to Development

For a local jar:

```gradle
repositories {
    flatDir { dirs "libs" }
}

dependencies {
    compileOnly name: "ultimatebankingsystem-2.1.1"
    localRuntime name: "ultimatebankingsystem-2.1.1"
}
```

For local Maven publication, run `gradlew.bat publishToMavenLocal` in UBS and add `mavenLocal()` plus:

```gradle
compileOnly "net.austizz.ultimatebankingsystem:ultimatebankingsystem:2.1.1"
localRuntime "net.austizz.ultimatebankingsystem:ultimatebankingsystem:2.1.1"
```

Do not shade or jarJar UBS.

## 3. Declare the NeoForge Dependency

Required integration:

```toml
[[dependencies.yourmod]]
modId="ultimatebankingsystem"
type="required"
versionRange="[2.1.1,)"
ordering="AFTER"
side="BOTH"
```

For optional integration, use `type="optional"` and isolate all UBS-linked classes until the mod is present.

```java
if (!ModList.get().isLoaded("ultimatebankingsystem")) {
    return;
}
```

## 4. Obtain APIs

```java
UltimateBankingApi finance = UltimateBankingApiProvider.get();
UltimateServerApi server = UltimateBankingApiProvider.server();
UltimateBankManagementApi banks = UltimateBankingApiProvider.banks();
UltimateShopManagementApi shops = UltimateBankingApiProvider.shops();
UltimateHeistApi heists = UltimateBankingApiProvider.heists();
UltimateEconomyApi economy = UltimateBankingApiProvider.economy();
```

At server startup, `isAvailable()` can be false until the world and Central Bank data are ready.

## 5. Settle a Payment

Run on the server thread:

```java
ApiTransactionResult result = finance.transfer(
        buyerAccountId,
        sellerAccountId,
        new BigDecimal("249.95"),
        "AUCTION:" + auctionId
);

if (!result.success()) {
    // Show result.reason(); do not apply the sale.
}
```

Use unique, meaningful references for auditability and idempotency in your own system.

## 6. Ownership Checks

```java
if (finance.playerOwnsAnyShop(playerId)) {
    List<UUID> shopIds = finance.getPlayerOwnedShopIds(playerId);
}

if (banks.playerOwnsAnyBank(playerId)) {
    List<ApiBankManagementSnapshot> owned = banks.getOwnedBanks(playerId);
}

boolean canManage = shops.playerCanManageShop(playerId, shopId);
boolean canBuild = shops.playerCanBuildInShop(playerId, shopId);
```

Use management APIs when you need full snapshots; root finance helpers are intended for lightweight compatibility checks.

## 7. Create or Manage a Shop

```java
ApiManagementResult created = shops.createShop(ownerId, "North Market", "RETAIL");
if (!created.success()) {
    // owner may be offline, at capacity, or validation may have failed
}

shops.setOpeningHours(ownerId, shopId, "ALL|09:00|21:00");
shops.setParticipantRole(ownerId, shopId, employeeId, "MANAGER");
```

Never assume role strings. Read `getSupportedParticipantRoles()` and shop types from `getSupportedShopTypes()`.

## 8. Read Bank Operations

```java
banks.getBank(bankId).ifPresent(bank -> {
    BigDecimal deposits = bank.totalDeposits();
    boolean attacked = bank.underAttack();
    int readyVaults = bank.readyVaultCount();
});

ApiSafeDepositSetupSnapshot setup = banks.getSafeDepositSetup(bankId);
if (!setup.enabled()) {
    setup.missingRequirements().forEach(logger::warn);
}
```

Safe Access changes and rates still require an authorized actor ID.

## 9. Read and Control Heist Planning

```java
Optional<ApiHeistSessionSnapshot> current = heists.getPlayerSession(playerId);
long cooldownMs = heists.getPlayerCooldownRemainingMillis(playerId);

ApiManagementResult ready = heists.setReady(playerId, true);
```

World target scans and all actions belong on the server thread. Do not use these methods from client render code.

## 10. Listen for Heists

```java
@SubscribeEvent
public static void onHeist(HeistLifecycleEvent event) {
    if (event.stage() == HeistLifecycleEvent.Stage.ALARMED) {
        // Trigger your own server-side security integration.
    }
}
```

Register the listener on `NeoForge.EVENT_BUS`.

## 11. Value Custom Loot

Implement `HeistLootValueProvider`, then:

```java
HeistLootValueRegistry.register(provider);
```

Return a value only for stacks your mod owns. Let other providers or UBS handle unknown stacks.

For modded doors, implement `HeistDoorAdapter` and register it with `HeistDoorAdapterRegistry`.

## 12. Notifications

```java
finance.sendNotification(playerId,
        ApiNotificationRequest.security("Vault access was denied")
                .source("Your Security Mod")
                .channel("security")
                .priority(ApiNotificationPriority.HIGH)
                .build());
```

Use stable IDs for progress/state replacement and channels for scoped cleanup. Use legacy alerts only when maintaining an older integration.

## 13. Market Values

```java
ApiShopPriceStatistics prices = finance.getItemShopPriceStatistics(
        itemStack,
        ApiShopPriceScope.REGULAR
);

if (prices.available()) {
    long fairValueCents = prices.medianPriceCents();
}
```

Median is usually safer for player markets; average is also exposed when your design explicitly needs it.

## 14. Thread Handoff

When an asynchronous service needs UBS data, schedule the call:

```java
minecraftServer.execute(() -> {
    ApiManagementResult result = shops.renameShop(actorId, shopId, newName);
    // Return the result to your async system after this block.
});
```

Do not call mutations or live target scans directly from HTTP threads, database pools, render threads, or arbitrary executors.

## 15. Failure Handling

- Treat empty `Optional` and `success=false` as expected outcomes.
- Show `reason`/`message` to operators where appropriate.
- Do not retry financial mutations blindly.
- Re-fetch snapshots after a successful mutation.
- Never edit UBS saved NBT to simulate an API operation.

## 16. Build a Remote Economy Adapter

Use `UltimateBankingApiProvider.economy()` when your mod must synchronize an external read model or execute commands that can be retried across process/network failures. Keep transport and authentication outside UBS; after your adapter verifies a command, schedule exactly one call to the economy module on the server thread.

```java
ApiEconomyOperationRequest request = new ApiEconomyOperationRequest(
        commandId,
        ApiEconomyOperationType.TRANSFER_TO_PRIMARY,
        ApiEconomyActorType.PLAYER,
        linkedPlayerId,
        sourceAccountId,
        null,
        null,
        recipientPlayerId,
        "",
        "",
        "",
        new BigDecimal("125.00"),
        "Website transfer " + commandId,
        Map.of(),
        List.of()
);

ApiEconomyOperationResult receipt = economy.execute(request);
```

Persist and reuse `commandId`. A network timeout is not permission to generate a new key: call `findOperation(commandId)` or retry the exact request. An exact replay returns the original receipt with `duplicate=true`; a changed payload under the same key returns `IDEMPOTENCY_CONFLICT`.

Use `snapshot(ApiEconomySnapshotRequest.reconciliation())` only from a trusted official adapter because it contains all accounts and grants. Player-scoped reads use `forPlayer(playerId)`. Never make a browser or remote caller an `OFFICIAL_SYSTEM` actor directly; that authority is for narrowly scoped, authenticated in-process workflows such as provisioning, freezing, approved adjustments, and escrow settlement.

Full method and record reference: [Developer API](Developer-API.md).
