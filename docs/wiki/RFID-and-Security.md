# RFID and Security

UBS RFID readers provide physical, server-authoritative access control for banks, shops, doors, and redstone targets.

## RFID Scanner

Place the scanner against a vertical surface. Its reader face points toward the placing player. The scanner receives a new unique reader ID whenever a new block is placed; breaking and replacing it does not preserve the former identity.

Shift-right-click opens settings. The first owner sets a PIN; later configuration requires that PIN.

The tabbed UI supports:

- General: enabled state, access level `1..100`, bank/shop link, normal/forced-open/forced-closed mode
- Signals: independent idle, success, and failure strengths and durations; failed-attempt threshold/reset
- Cards: write access to a held card, inspect authorized cards, revoke one card, scroll long lists
- Targets: choose exact success and failure blocks/faces within the allowed bank/shop premise

The scanner itself does not emit redstone. Only the selected target block/face receives the configured directed signal, avoiding accidental power to adjacent blocks.

## RFID Cards

Cards contain their own unique card ID and can hold access grants for multiple readers. Writing one reader grant does not erase grants for other readers.

A scan succeeds only when:

- the reader is enabled and not forced closed
- the card includes this exact reader ID
- the grant meets the configured access level
- the card/reader authorization has not been revoked

Matching access level alone is not enough.

## Door Passage

When a success target opens a supported door, authorization is tracked per player. The validated player can pass during the grant window; another player cannot simply follow through the opening. Passage checks work from either side of the protected door.

Modded doors can integrate using the heist/security door adapter surface where applicable.

## Visual States

- Idle texture while waiting
- Success texture for the configured success duration
- Failure texture for the configured failure duration

Failed scans increment the reader attempt counter. At the configured threshold, failure targets activate and the counter resets.

## RFID Spoofer

The RFID spoofer is a heist tool. During a valid active heist it attempts to compromise a scanner and activate its configured success targets. It does not permanently write a legitimate card grant and remains subject to heist target/premise validation.

## Security Guidance

- Use different reader IDs for separate secure areas.
- Revoke lost card IDs instead of only lowering the reader level.
- Keep signal targets inside the protected premise.
- Use short success windows for public corridors.
- Review Safe Access/door logs after an alarm.

