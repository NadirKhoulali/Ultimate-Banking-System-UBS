# Admin Commands

UBS admin commands are server-operator tools for account moderation, central-bank policy, bank governance, premises, heist recovery, migration, shop repair, and test-data seeding.

Required permission: Minecraft permission level `3` for players. Server console can run these commands directly.

## Command Roots and Aliases

| Root | Purpose |
| --- | --- |
| `/ubs admin ...` | Primary UBS admin command root. |
| `/bank admin ...` | Compatibility alias for the same command tree as `/ubs admin ...`. |
| `/centralbank ...` | Central-bank policy, audit, report, ledger, and open-market controls. |
| `/ubs centralbank ...` | Central-bank panel shortcut and legacy interest-rate setter. |
| `/ubs bank ...` | Central-bank save and rename helpers. |
| `/ubs money ...` | Direct account balance adjustment tools. |
| `/ubs seed ...` | Demo/test-data seed tools. |

Parameter notes:

- `<player>` is an online player selector accepted by Minecraft's command parser.
- `<accountId>`, `<paymentId>`, `<applicationId>`, `<appealId>`, and `<shopId>` are UUIDs.
- `<bankName>` is a greedy string, so names with spaces work when placed at the end of the command. Premise commands also accept a bank UUID.
- `<amount>` should be a positive numeric value unless the command explicitly says it can clear a value.
- `[reason]` is optional. If omitted, UBS stores or shows a blank reason.

## Account and Player Moderation

### `/ubs admin view <player>`

Lists all UBS accounts owned by the selected online player.

The output includes:

- account type
- bank name
- balance
- primary-account flag
- frozen state and freeze reason
- whether a PIN is set
- daily withdrawal usage
- full account UUID with click-to-copy behavior
- up to 10 recent transactions per account

Use this before freezing, unfreezing, depositing into, or withdrawing from a specific account.

### `/ubs admin freeze <player> [reason]`

Freezes every account owned by the selected player.

Frozen accounts cannot perform normal outgoing operations. The command records an admin audit marker on each affected account and notifies the player with the optional reason.

Use this for suspected abuse, fraud review, or temporary account lockdowns.

### `/ubs admin unfreeze <player>`

Unfreezes every account owned by the selected player.

The command records an admin audit marker and notifies the player that banking access has been restored.

### `/ubs admin freeze account <accountId> [reason]`

Freezes one account by UUID.

Use this when only one account should be held for review and the player's other accounts should remain usable.

### `/ubs admin unfreeze account <accountId>`

Unfreezes one account by UUID.

Use this after a review is complete or when only one account was incorrectly frozen.

## Direct Money Tools

### `/ubs money deposit <accountId> <amount>`

Adds money directly to the specified account.

The command records an admin-oriented transaction marker and fires the same balance-changed event that normal account changes use. Use it for compensation, migration fixes, or support corrections.

### `/ubs money withdraw <accountId> <amount>`

Removes money directly from the specified account.

The command records an admin-oriented transaction marker and fires a balance-changed event. It fails if the account cannot cover the amount.

Use this for rollback, fraud correction, or manual economy cleanup.

## Central Bank Shortcuts

### `/ubs centralbank`

Shows a central-bank panel in chat with current central-bank information and clickable command suggestions.

Use this as the quick admin entry point when inspecting bank health.

### `/ubs centralbank interest set <rate>`

Sets the central bank object's normal bank interest rate.

This is a legacy/control shortcut. For the policy rate that affects player-bank savings floors and ceilings, use `/centralbank rate set <rate>`.

### `/ubs bank save`

Marks/saves UBS bank data.

Use this after large admin operations if you want to force persistence instead of waiting for the normal save cycle.

### `/ubs bank rename <newName>`

Renames the Central Bank.

This changes the displayed central-bank name used by UBS panels and bank references.

## Central Bank Policy Controls

### `/centralbank`

Shows the current Federal Funds Rate and derived savings-rate policy range.

This is the same display as `/centralbank rate`.

### `/centralbank rate`

Shows:

- Federal Funds Rate
- implied savings floor
- implied savings ceiling
- configured minimum and maximum Federal Funds Rate

Use this before changing policy so admins can see the current effective range.

### `/centralbank rate set <rate>`

Sets the Federal Funds Rate.

The command validates the value against config bounds, saves the change, and broadcasts the policy update to players. Player-bank savings rates are constrained from this policy rate unless the bank is rate-exempt.

### `/centralbank opm inject <amount>`

Runs a central-bank open-market injection.

This increases Central Bank reserve by the amount and records an open-market-operation history entry with:

- operation ID
- timestamp
- actor
- amount
- reserve before
- reserve after

Use this when the server economy needs liquidity.

### `/centralbank opm withdraw <amount>`

Runs a central-bank open-market withdrawal.

This decreases Central Bank reserve by the amount and records an open-market-operation history entry. The operation is rejected if it would make Central Bank reserve negative.

Use this when the server economy needs liquidity removed.

### `/centralbank opm history`

Shows recent open-market operations.

History length is controlled by `OMO_HISTORY_LIMIT`.

### `/centralbank audit [bankName]`

Audits one bank or all banks.

Without a bank name, the command inspects all non-central banks. With a bank name, it reports only that bank. The audit focuses on reserve/deposit health and operational risk.

### `/centralbank report`

Builds a central-bank economy report and stores a snapshot.

The report includes:

- total circulation
- total Central Bank plus bank reserves
- active player bank count
- average reserve ratio
- warning/restricted/suspended bank count
- outstanding loans
- Federal Funds Rate
- net open-market-operation amount
- settlement count in the last 24 hours

### `/centralbank report history`

Shows the economy report plus recent stored report snapshots.

Use this to compare the current economy with earlier report points.

### `/centralbank ledger`

Shows recent successful settlement ledger entries.

This is useful when reviewing inter-bank transfers, loan disbursements, and clearing activity.

### `/centralbank ledger suspense`

Shows failed or suspense settlement entries.

Use this to diagnose failed clearing, insufficient-reserve failures, or settlement problems.

## Economy, Reports, and Flags

### `/ubs admin report`

Shows an admin economy report.

Use this as the UBS admin-side economy status check. It is separate from `/centralbank report`, which also writes central-bank report snapshots.

### `/ubs admin flags`

Shows the fraud/flag queue.

Use this to inspect UBS account or activity flags that need admin review.

## Loan Approval and Scheduled Payments

### `/ubs admin loan pending`

Lists pending loan applications that require admin approval.

Use this queue when player loan products or policy settings require manual review.

### `/ubs admin loan approve <player>`

Approves the selected online player's pending loan.

The command completes the pending loan flow for that player if a valid pending approval exists.

### `/ubs admin loan deny <player> [reason]`

Denies the selected online player's pending loan.

The optional reason is shown to the borrower and retained as part of the admin decision flow.

### `/ubs admin schedule list`

Lists scheduled payments known to the Central Bank.

Use this to audit recurring payments, subscription-like flows, or scheduled server-side transfers.

### `/ubs admin schedule add <sourceAccountId> <targetAccountId> <amount> <frequencyTicks>`

Creates a scheduled payment from one account to another.

`frequencyTicks` controls how often the payment repeats. For reference, 20 ticks is about 1 second and 24,000 ticks is about one Minecraft day.

Use this for recurring fees, server-managed rent, test schedules, or controlled economy automation.

### `/ubs admin schedule remove <paymentId>`

Removes a scheduled payment by UUID.

Use `/ubs admin schedule list` first to find the payment ID.

## Bank Applications and Appeals

### `/ubs admin applications`

Lists pending player bank applications.

Use this when player-created banks require admin approval.

### `/ubs admin applications approve <applicationId>`

Approves a pending bank application.

The command creates/finalizes the player bank from the application data and updates the application status.

### `/ubs admin applications deny <applicationId> [reason]`

Denies a pending bank application.

The optional reason is recorded and can be shown to the applicant.

### `/ubs admin appeals`

Lists pending bank appeals.

Appeals are submitted by bank owners when they want an admin to review a bank decision or restriction.

### `/ubs admin appeal <appealId> approve [reason]`

Approves an appeal.

The optional reason is stored as the admin review note.

### `/ubs admin appeal <appealId> deny [reason]`

Denies an appeal.

The optional reason is stored as the admin review note.

## Bank Governance and Compliance

### `/ubs admin reserve <bankName>`

Shows reserve information for a bank.

Use this before suspension, rate changes, or compliance actions.

### `/ubs admin compliance [bankName]`

Shows compliance status.

Without a bank name, this reports across banks. With a bank name, it focuses on that bank. Use it to spot banks that are under reserve pressure or in an unsafe state.

### `/ubs admin audit <bankName>`

Runs an admin audit for a specific bank.

Use this for targeted investigation of a bank's status, reserves, deposits, and operational condition.

### `/ubs admin suspend <bankName> [reason]`

Suspends a bank.

Suspension prevents normal bank operation paths that check bank status. The bank owner is notified if they are online. Use this for severe compliance issues or active abuse investigations.

### `/ubs admin unsuspend <bankName>`

Restores a suspended bank to active operation.

Use this after the bank has corrected the issue or after an admin review clears the bank.

### `/ubs admin unlock <bankName>`

Lifts bank lockdown state.

Use this if a bank was placed into lockdown by system rules or admin intervention and should be allowed to operate again.

### `/ubs admin bankrun <bankName>`

Runs the bank-run status check for a bank.

Use this to inspect or trigger the bank-run health flow after heavy withdrawals, low reserves, or manual economy tests.

### `/ubs admin revoke <bankName> [reason]`

Revokes a bank.

This is a stronger governance action than suspension. Use it for banks that should no longer operate. Include a reason for operator audit clarity.

### `/ubs admin rateexempt <bankName>`

Toggles rate exemption for a bank.

Rate-exempt banks are not constrained by the Federal Funds Rate savings floor/ceiling policy. Use this only for special-purpose banks, admin test banks, or banks with explicit server approval.

### `/ubs admin setcap <bankName> <amount>`

Sets a bank's daily cap override.

Use this to force a specific daily movement cap instead of the normal cap derived from reserve/liquidity config. Set carefully; this can materially affect how much money a bank can move in one day.

### `/ubs admin waivefee <player>`

Waives the next bank charter fee for the selected player.

Use this for support grants, event rewards, test setup, or manual correction after a failed bank creation flow.

### `/ubs admin deferrenewal <bankName>`

Defers the bank license renewal timer.

Use this to give a bank more time before renewal enforcement.

### `/ubs admin deferrenwal <bankName>`

Legacy typo alias for `/ubs admin deferrenewal <bankName>`.

Keep this documented because older operator notes or command blocks may still reference it.

## Bank Premise Administration

### `/ubs admin bank premise`

Shows the premise administration command list.

### `/ubs admin bank premise list [bankNameOrId]`

Without a bank, shows premise counts for every bank. With a bank name or UUID, lists that bank's premise IDs, readiness, access mode, safe-area count, and ready/total vault count. Listed premise IDs are underlined; hover for the copy hint and click an ID to copy it to the clipboard.

### `/ubs admin bank premise info <premiseId>`

Shows the owning bank, exact bounds, outside exit, access mode, readiness, safe-area/vault counts, and the blockers that would apply to an Owner-PC deletion. The premise ID is underlined and click-to-copy.

### `/ubs admin bank premise add <bankNameOrId>`

Starts the existing premise claim tool for the selected bank. This command must be run by an in-game admin because it temporarily replaces the hotbar with the corner, exit, apply, overlay, clear, and cancel tools. Applying the selection rechecks permission and commits through the same atomic premise mutation path as the Owner PC.

### `/ubs admin bank premise delete <premiseId>`

Force-deletes the selected premise without requiring an active Owner PC and without applying normal Owner-PC deletion blockers. This destructive admin path cancels active escorts for the premise, removes its teller routes, removes contained legacy safe-area records, clears affected assignment records and loaded row-door labels, then removes the premise. Account-held safe-box item data and existing escrow records are preserved rather than silently destroyed.

The command still rejects a missing or ambiguous premise ID. Those are invalid targets, not gameplay enforcement rules.

### `/ubs admin bank premise mode <premiseId> <public|staff_only>`

Changes the premise access mode. `staff-only` is accepted as an alias for `staff_only`.

### `/ubs admin bank premise exit <premiseId>`

Starts the outside-exit replacement tool for the selected premise. This must be run in game and requires the captured exit to remain outside the premise in the same dimension.

### `/ubs admin bank premise cancel`

Cancels the active UBS safe/premise claim tool and restores the admin's original hotbar.

## Heist Administration

### `/ubs admin heist list`

Lists planned and active sessions with ID, phase, target, and crew size.

### `/ubs admin heist inspect <sessionId>`

Shows phase, bank/premise, crew, alarm state, and loot for one session.

### `/ubs admin heist abort <sessionId>`

Stops a session, evacuates affected players, and restores tracked assets.

### `/ubs admin heist recover <sessionId>`

Runs the same authoritative cleanup for a session left behind after an interrupted server lifecycle.

### `/ubs admin heist clearcooldown player <player>`

Clears the selected online player's heist cooldown.

### `/ubs admin heist clearcooldown bank <bankId>`

Clears the target bank's cooldown by UUID.

### `/ubs admin heist clearvictimprotection <playerId>`

Clears post-heist victim protection for a player UUID.

### `/ubs admin heist allowinsider [player]`

Enables the development insider bypass for the executor or selected online player.

### `/ubs admin heist disallowinsider [player]`

Removes that bypass.

## Shop Admin

### `/ubs admin shop view <player>`

Shows shop data owned by the selected online player.

Use this to locate shop IDs before changing shop levels.

### `/ubs admin shop level set <shopId> <level>`

Sets a shop to an exact level.

The level must be at least `1`. Use this for support correction, testing level-gated features, or repairing corrupted shop progression.

### `/ubs admin shop level add <shopId> <levels>`

Adds levels to a shop.

The amount must be at least `1`. Use this for rewards or progression correction.

### `/ubs admin shop level remove <shopId> <levels>`

Removes levels from a shop.

The amount must be at least `1`. Use this carefully because lowering levels may hide or lock level-gated shop features.

## Import and Migration

These commands read files from the server filesystem, not from the client machine.

See [Migration Guide](Migration-Guide.md) for file-format expectations and safer migration workflow.

### `/ubs admin import csv <path>`

Imports account balances from a CSV file.

Use this for controlled migrations from spreadsheets or exported economy data.

### `/ubs admin import essentialsx <path>`

Imports balances from an EssentialsX data directory or file path supported by the importer.

Use this when migrating from EssentialsX economy data.

### `/ubs admin import cmi <path>`

Imports balances from CMI economy data.

Use this when migrating from CMI-based server economies.

### `/ubs admin import iconomy <path>`

Imports balances from iConomy data.

Use this when migrating legacy iConomy balances.

## Demo and Test Data

### `/ubs seed banking`

Seeds the current world with deterministic UBS demo banking data.

The seed creates or refreshes UBS-owned demo records instead of duplicating them on every run. It leaves real player banks alone.

Seeded data includes:

- three demo banks: Aurora Credit Union, Pioneer Merchant Bank, and Atlas Capital
- a player-owned sandbox bank when a player runs the command
- demo checking, savings, money-market, and certificate accounts
- account balances, credit scores, and business labels
- bank metadata, ownership models, colors, and mottos
- bank loan products
- open inter-bank lending offers
- one active inter-bank loan
- settlement ledger and suspense rows
- economy history snapshots

Use this on development worlds when the Bank Owner PC, lending market, settlement ledger, or central-bank reports need realistic data.

Do not run it on a production economy unless you intentionally want test banks and test balances in that world.

## Practical Admin Workflow

1. Use `/ubs admin view <player>` to find account IDs before direct account changes.
2. Use `/ubs admin compliance` or `/centralbank audit` before suspending or revoking a bank.
3. Use `/centralbank report history` to confirm whether economy changes are improving or worsening the world state.
4. Use `/ubs admin schedule list` before removing scheduled payments.
5. Use `/ubs bank save` after large manual migrations or support corrections.

## Audit Tags

Admin-driven balance changes and moderation actions write admin-oriented descriptors into account transaction or audit history where the backing account supports it.

Common admin-visible descriptors include:

- manual admin deposit
- manual admin withdraw
- account freeze
- account unfreeze
- bank governance decisions
- migration/import-created records

Keep reasons specific. "Fraud review: duplicate payouts on 2026-06-14" is more useful than "fix".
