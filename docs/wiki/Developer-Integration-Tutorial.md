# Developer Integration Tutorial

This guide explains exactly how to integrate UBS into another NeoForge mod dev environment.

## 1. UBS coordinates and mod id

Current UBS identity:

- Maven group: `net.austizz.ultimatebankingsystem`
- Artifact: `ultimatebankingsystem`
- Mod id: `ultimatebankingsystem`
- Current version in this repo: `1.1.0`

Dependency coordinate format:

```text
net.austizz.ultimatebankingsystem:ultimatebankingsystem:<ubs_version>
```

## 2. Do you need Gradle dependency?

Yes, if your code imports UBS classes (for example `UltimateBankingApiProvider`), your mod must have UBS on the compile classpath.

You also need UBS present at runtime for tests/dev runs if you actually call UBS APIs.

## 3. Choose integration mode

Use one:

1. Composite build (best when developing both mods locally)
2. `mavenLocal()` (best if UBS is built/published locally)
3. `libs/` jar dependency (quick local setup)

## 4. Mode A: Composite build (recommended)

### 4.1 In your mod's `settings.gradle`

```gradle
pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id 'org.gradle.toolchains.foojay-resolver-convention' version '1.0.0'
}

includeBuild("../Ultimate-Banking-System-UBS-")
```

Point the path to your local UBS clone.

### 4.2 In your mod's `build.gradle`

```gradle
dependencies {
    compileOnly "net.austizz.ultimatebankingsystem:ultimatebankingsystem:1.1.0"

    // Prefer localRuntime for NeoForge dev runs.
    // If your template does not have localRuntime, use runtimeOnly instead.
    localRuntime "net.austizz.ultimatebankingsystem:ultimatebankingsystem:1.1.0"
}
```

## 5. Mode B: Publish UBS to `mavenLocal()`

### 5.1 In UBS repo

```bash
./gradlew publishToMavenLocal
```

Windows:

```bat
gradlew.bat publishToMavenLocal
```

### 5.2 In your mod `build.gradle`

```gradle
repositories {
    mavenLocal()
    maven { url "https://libraries.minecraft.net" }
    // other repos...
}

dependencies {
    compileOnly "net.austizz.ultimatebankingsystem:ultimatebankingsystem:1.1.0"
    localRuntime "net.austizz.ultimatebankingsystem:ultimatebankingsystem:1.1.0"
}
```

## 6. Mode C: Local `libs/` jar

### 6.1 Build UBS jar

```bash
./gradlew build
```

Take the produced UBS jar from `build/libs/` and place it in your mod's `libs/`.

### 6.2 In your mod `build.gradle`

```gradle
repositories {
    flatDir {
        dirs "libs"
    }
}

dependencies {
    compileOnly name: "ultimatebankingsystem-1.1.0"
    localRuntime name: "ultimatebankingsystem-1.1.0"
}
```

If your setup does not have `localRuntime`, use `runtimeOnly`.

## 7. Declare mod dependency in `neoforge.mods.toml`

In your mod's `src/main/resources/META-INF/neoforge.mods.toml`, replace `<your_modid>` with your mod id.

### Required UBS

```toml
[[dependencies.<your_modid>]]
modId="ultimatebankingsystem"
type="required"
versionRange="[1.1.0,)"
ordering="AFTER"
side="BOTH"
```

### Optional UBS integration

```toml
[[dependencies.<your_modid>]]
modId="ultimatebankingsystem"
type="optional"
versionRange="[1.1.0,)"
ordering="AFTER"
side="BOTH"
```

Use `required` if your mod cannot function without UBS.  
Use `optional` if UBS features are add-ons.

## 8. Required vs optional code patterns

### 8.1 Required pattern

You can call UBS API directly during normal server lifecycle points.

```java
UltimateBankingApi api = UltimateBankingApiProvider.get();
if (!api.isServerAvailable()) {
    return;
}
```

### 8.2 Optional pattern (important)

Guard integration so your mod does not crash when UBS is missing.

```java
import net.neoforged.fml.ModList;

boolean hasUbs = ModList.get().isLoaded("ultimatebankingsystem");
if (!hasUbs) {
    return;
}
```

Keep UBS-specific code in classes only touched when UBS is loaded.

Do not `jarJar`/shade UBS into your own mod jar. UBS should load as a separate mod.

## 9. Basic API bootstrap

```java
import net.austizz.ultimatebankingsystem.api.UltimateBankingApi;
import net.austizz.ultimatebankingsystem.api.UltimateBankingApiProvider;

UltimateBankingApi api = UltimateBankingApiProvider.get();
String apiVersion = api.getApiVersion();
```

## 10. Common usage examples

### Account read/write

```java
var bal = api.getBalance(accountId);
if (bal.success()) {
    // bal.balanceAfter()
}

var tx = api.transfer(senderAccountId, receiverAccountId, 250L);
if (!tx.success()) {
    // tx.reason()
}
```

### Cheque / note issue

```java
var cheque = api.issueCheque(
        sourceAccountId,
        recipientPlayerId,
        300L,
        writerPlayerId,
        "Cashier",
        "Recipient"
);

if (cheque.success()) {
    ItemStack stack = cheque.itemStack();
}
```

### Cash bills

```java
var cash = api.giveDollarBills(playerId, 20, 5); // 5x $20 bills
int cashOnHand = api.getPlayerCashOnHand(playerId);
```

## 11. Verification checklist

After setup:

1. `./gradlew build` succeeds in your mod.
2. Game starts with both mods in dev run.
3. `ModList.get().isLoaded("ultimatebankingsystem")` reports true when expected.
4. `api.isServerAvailable()` becomes true after server data init.
5. A test API call (for example `getPlayerAccountCount`) returns expected values.

## 12. Common mistakes

- Missing UBS on compile classpath:
  - Causes compile errors for UBS imports.
- Missing UBS on runtime classpath:
  - Causes `ClassNotFound`/`NoClassDefFound` during dev run.
- `optional` mods.toml but unguarded UBS class usage:
  - Crashes when UBS is not installed.
- Wrong version range in mods.toml:
  - Dependency mismatch at load.

## 13. Related docs

- [Developer API](Developer-API.md)
- [Configuration](Configuration.md)
- [Admin Commands](Admin-Commands.md)
