# Decisions — ATM GUI

## Architecture
- Stack-based layer navigation (push/pop) in BankScreen
- Request→response packet pattern for all server operations
- Client never caches data long-term; always request fresh from server
- OpenATM handshake: client sends packet on block click → server responds with account list → client opens screen
- All packet handlers use context.enqueueWork() for main thread safety

## Scope
- Virtual currency only (no item exchange for deposit/withdraw)
- No PIN verification before operations
- Transaction History: max 50 entries, newest first, no filtering
- Confirmation dialogs for Transfer and PIN change only
