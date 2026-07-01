# Smartphone

The smartphone is a pocket UI for player-facing banking and communication. It is separate from the Business Manager PC and uses a smooth in-world overlay instead of pausing gameplay.

## Access

- Item: `smartphone`
- Open by right-clicking the item.
- Open by pressing `P` while the phone is in the player's inventory.
- The phone slides in from the bottom-right of the screen.
- The player can keep moving while the phone is open.
- Clicking outside the phone hides interaction and returns the mouse to the game. Press `P` again to interact with the phone.

## Ownership

Phones are owner-locked by default. The first player who opens a new phone becomes its owner.

Servers can change this with `PhoneAccessMode`:

- `OWNER_LOCKED`: only the owner can use the phone.
- `OPEN_ACCESS`: whoever holds the phone can use it.

## Built-In Apps

- Banking: lists the player's UBS accounts, balances, primary state, frozen state, credit score, access type, role, and recent transactions.
- Per-account apps: each account appears as its own bank/type entry inside Banking.
- Tap to Pay: selects the account used when the phone is held at supported payment flows.
- Calculator: simple expression calculator.
- Paint: small phone sketch pad saved on the phone item.
- Contacts: online server player directory plus known contacts from messages.
- Messenger: private phone messages with offline history, unread counts, mute, block, favorite, and report actions.
- Notes: up to 12 saved phone notes.
- Settings: phone accent and wallpaper customization.

Optional bridge apps appear only when the related mod is loaded:

- JourneyMap (`journeymap`)
- Auction House (`ultimate_auction_system`)
- Real Estate / claiming (`ucs`)

## Tap to Pay

Tap to Pay is an additional payment method. It does not replace wallets, credit cards, cash, payment terminals, or shop cashier flows.

Current Tap to Pay support:

- shop cashier card/terminal checkout
- bank teller external payment sessions

The phone uses the selected Tap to Pay account. If no account was selected, the player's primary UBS account is used as a fallback.

Each phone-account pair gets a stable virtual card number stored on the phone item. The number is cosmetic; the actual payment is still validated server-side against the UBS account.

## Messaging Moderation

Players can report a contact from Messenger. Reports are stored server-side and can be reviewed by admins:

- `/ubs phone reports`
- `/ubs phone reports all`
- `/ubs phone reports resolve <reportId>`

The larger PC admin report UI is planned separately.
