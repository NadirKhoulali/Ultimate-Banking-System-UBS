# Bank Heists

UBS 2.0 adds server-authoritative multiplayer bank heists inspired by coordinated tactical heist games. Heists use physical bank premises and world objects; they are not a chat-only payout roll.

## Starting a Heist

Open the planner with `/heist`, `/heist plan`, or `/bank heist`.

1. Create or open a planning session.
2. Invite crew members and wait for acceptance.
3. Select an eligible bank premise.
4. Have every accepted member ready up.
5. The leader starts the countdown.
6. Enter the target, breach security, collect loot, and extract before the deadline.

Use `/heist abandon` to leave. If the final member abandons, the session ends and recoverable world state is restored.

## Eligibility

A target can be blocked by server cooldowns, player cooldowns, missing premise exits, invalid physical setup, active protection, or another active heist. The planner reports blockers before countdown.

The default maximum crew size, countdown, heist duration, and cooldown behavior are server-controlled. API integrations can read the effective values through `UltimateHeistApi`.

## Tools

- Dallas mask: heist identity state and equip/remove animation
- Lockpicking tool: opens supported breach points over time
- OVE9000 saw: two-handed deposit-box breach tool
- Thermal drill: vault-door drill with timer and possible stalls
- Heist drill: compact/standing-safe drill
- RFID spoofer: triggers authorized success targets on vulnerable RFID readers during a heist
- Duffel bag: stores stolen items; hold the action key while aiming at supported loot

The action key defaults to `F` and can be rebound.

## Physical Objectives

Heist members can:

- hack each Bank Owner PC inside the target premise
- operate multiple computer hacks and drills concurrently
- drill a bank vault door, restart stalled drills, and remove a completed drill
- drill compact and standing safes
- pick or saw individual safety-deposit doors, including empty boxes
- steal visible contents from opened deposit boxes and safes
- steal bullion/cash from the full metal-pallet footprint

Deposit-box ownership/content labels remain hidden from robbers. Empty boxes can be breached, so players must decide whether the extra time is worth it.

## Alarm and HUD

The tactical HUD shows:

- bank and current phase
- time remaining
- alarm state
- crew status, health, and score
- accumulated loot and duffel capacity

Banks can configure the alarm sound in the Safe app. Custom imported `.ogg` audio is copied into a generated client resource pack and looped during alarms. The test controls support play/restart and explicit stop.

## Extraction

The target premise exit defines the extraction area. UBS renders a grounded tactical border rather than vanilla floating particles:

- gold while idle
- green while extraction is active
- red/orange when contested or unavailable

The extraction hologram reports the current action. A crew completes the heist by carrying valid loot to extraction and satisfying the countdown. Dropping a bag alone is not the completion action unless the current prompt explicitly requests it.

When time expires, the heist is abandoned, a member walks too far away, the server stops, or a session is recovered after a crash, UBS evacuates affected players and restores tracked assets.

## Administration

```text
/ubs admin heist list
/ubs admin heist inspect <sessionId>
/ubs admin heist abort <sessionId>
/ubs admin heist recover <sessionId>
/ubs admin heist clearcooldown player <player>
/ubs admin heist clearcooldown bank <bankId>
/ubs admin heist clearvictimprotection <playerId>
/ubs admin heist allowinsider [player]
/ubs admin heist disallowinsider [player]
```

`allowinsider` is a development/admin bypass for testing a bank a player could normally not rob.

## Addon Integration

Use `UltimateBankingApiProvider.heists()` for immutable session/target snapshots and planning actions. Register modded doors through `HeistDoorAdapterRegistry` and custom loot values through `HeistLootValueRegistry`.

