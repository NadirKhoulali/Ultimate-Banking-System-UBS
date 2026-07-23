# Smartphone

The UBS smartphone is a non-pausing in-world overlay. Players keep moving while the phone is visible; pointer and keyboard input are captured only while interacting with phone controls.

## Access and Scale

- Item: `smartphone`
- Right-click the held phone, or use the rebindable `Toggle Phone` key (default `[`) while a phone is in inventory.
- The phone uses a smooth bottom-right slide animation.
- Phone dimensions and all app content scale together independently from extreme Minecraft GUI-scale values, preventing the device from growing off-screen.
- `Esc` navigates backward until Home; pressing it on Home puts the phone away.

Phones are owner-locked by default. `PhoneAccessMode=OPEN_ACCESS` permits the current holder to use one.

## Lock Screen

The first use prompts for a phone password and confirmation. Later opens show the lock screen, local and server time, and a swipe-up password panel. Settings includes password change.

The home status bar shows local time. Content is clipped to the rounded screen bounds and never renders across the black bezel.

## Banking

The main Banking app represents the player's primary account. Every other bank account appears as a separate app named by bank and account type.

Features include:

- real balances, account/card state, bank name, primary badge, copyable account ID, and credit score
- per-account login/PIN session isolation
- account-specific PIN setup, reusing a PIN set at an ATM
- send/receive money using online players with primary accounts or a directly entered account ID
- pending pay requests and Tap to Pay
- month-selectable statistics based on real transaction data
- scrollable transaction history and transaction detail
- dark/light banking-app preference

Logging into one account does not unlock another account app.

If the player has no account, Banking opens an onboarding screen. `/account open` creates a Central Bank checking account and makes it primary when no other account exists.

## Messenger and Contacts

- searchable player/contact directory
- private realtime messages to online clients
- durable offline message history and unread state
- multiline expanding composer with four visible lines and internal auto-scroll
- typing indicator with animated dots, activity timeout, and immediate clear on send/delete/exit
- pay-request and money-gift actions inside conversations
- accept/decline state embedded in chat; completed requests/gifts cannot be claimed twice
- offline delivery for messages, requests, and gifts with join/phone-open notifications
- block, mute, favorite, and report actions

Money gifts reserve funds when sent. Declined or expired gifts return the funds to the sender.

While a phone input is focused, movement, inventory/chat, and unrelated keybinds are suppressed. Shifted characters such as `?` and `@` still work normally.

## Notifications

Phone feedback uses a top slide-down notification card. When the phone is closed but present in inventory, eligible phone notifications use the same visual style in the game HUD rather than legacy UBS alerts.

## Other Apps

- Calculator
- Paint
- Notes
- Settings for accent, wallpaper, theme, and password
- Contacts
- Messenger
- Tap to Pay

Optional bridge apps appear only when the corresponding mod is loaded:

- JourneyMap
- Ultimate Auction System
- Ultimate Claiming System / real estate

## Tap to Pay

Tap to Pay uses the selected phone account, falling back to the primary account. Each phone/account pair receives a stable cosmetic virtual-card number; settlement remains server-authoritative.

## Moderation

Messenger reports are reviewed with:

```text
/ubs phone reports
/ubs phone reports all
/ubs phone reports resolve <reportId>
```

