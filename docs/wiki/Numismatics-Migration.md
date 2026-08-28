# Create: Numismatics Migration

UBS can migrate a Create: Numismatics economy without inventing an exchange rate or silently skipping stored currency. The migration is an administrator-only, resumable operation for Minecraft 1.21.1 NeoForge servers.

Open the setup with:

```text
/ubs admin migrate numismatics
```

Permission level `3` is required.

## Official Denominations

Numismatics stores every account balance in base **Spurs**. UBS uses the denomination values from Numismatics itself:

| Coin | Spurs |
|---|---:|
| Spur | 1 |
| Bevel | 8 |
| Sprocket | 16 |
| Cog | 64 |
| Crown | 512 |
| Sun | 4,096 |

The default policy is **100 cents ($1.00) per Spur**. The wizard accepts only whole-cent rates so every account and physical item converts exactly without rounding.

## 1. Choose a Source

- **Use This World's Data** reads `world/data/numismatics_bank.dat`.
- **Import Numismatics File** opens the native operating-system file picker on the administrator's client and uploads a maximum 32 MiB `.dat` file for server-side NBT and SHA-256 validation.
- Headless servers can use `/ubs admin migrate numismatics file <server path>` and then open the wizard in game.

An imported file supplies account balances. Physical conversion always scans the active server world.

## 2. Choose a Policy

- **Full Economy** converts accounts, ordinary inventories, unloaded candidate chunks, dropped/equipped/displayed items, nested containers and bundles, Numismatics bank cards, and Numismatics machine inventories.
- **Accounts Only** converts bank balances but leaves physical assets untouched. The administrator must explicitly acknowledge that removing Numismatics afterward is unsafe while those assets remain.
- Bound Numismatics bank cards become issued UBS credit cards for their mapped account. Blank bank cards become blank UBS cards. Numismatics ID cards are reported but intentionally not converted.

Player balances credit the player's current global primary UBS account. If no account exists, UBS creates a primary Central Bank Checking Account. Blaze Banker/shared accounts become Central Bank joint accounts, and every trusted player becomes an owner. Shared accounts without a trusted owner block migration.

## 3. Run Preflight

Preflight indexes player data, standard SavedData, region files, entity-region files, item entities, block inventories, and compatible NeoForge item handlers. It reports account and physical totals, cards, candidate chunks/files, warnings, and hard blockers.

Full conversion requires Create: Numismatics to remain installed for the conversion run. External storage mods such as Applied Energistics or Refined Storage are treated as unresolved storage boundaries; move Numismatics assets into ordinary inventories before continuing. Unsupported SavedData assets also block completion.

## 4. Convert

Starting conversion:

1. Enables a persistent migration maintenance lock and disconnects non-operators.
2. Saves the world and runs a second authoritative preflight.
3. Creates a checksum-verified backup under `world/ubs-migrations/numismatics/<migration-id>/`.
4. Converts accounts idempotently and records source-to-UBS account mappings.
5. Converts online/offline player inventories and indexed world chunks incrementally.
6. Moves ordinary contents from Numismatics machines into administrator recovery storage.
7. Saves and re-scans the world. Any remaining convertible coin/card or unsupported storage prevents success.

The journal is stored as Minecraft SavedData, so an interrupted run can be resumed from the wizard. The server remains locked after a partial mutation failure; resume or restore the verified backup instead of editing files manually.

## 5. Finish or Roll Back

On success, the wizard exposes recovery items and writes:

- `migration-report.json`
- `account-mappings.csv`
- `REMOVE-NUMISMATICS.txt`
- the verified backup manifest and files

Use **Save & Stop Server**, remove Create: Numismatics from the server and clients, and restart. On that first restart without Numismatics, UBS automatically releases the maintenance lock.

**Restore Backup** writes a rollback request and stops the server. UBS verifies and restores every backup checksum before bank/player/world data loads on the next start. If verification fails, UBS refuses to continue loading the affected world.

Useful commands:

```text
/ubs admin migrate numismatics
/ubs admin migrate numismatics status
/ubs admin migrate numismatics world
/ubs admin migrate numismatics file <server path>
/ubs admin migrate numismatics report
```

Keep the migration directory until account totals, physical cash, issued cards, and recovery contents have been checked on the converted server.
