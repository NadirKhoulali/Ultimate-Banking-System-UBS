package net.austizz.ultimatebankingsystem.bank.safebox.escort;

public enum SafeBoxEscortPhase {
    OUTBOUND,
    AT_VAULT,
    INSPECTING,
    WAITING_FOR_EXIT,
    RETURNING,
    COMPLETE,
    TIMED_OUT
}
