package net.austizz.ultimatebankingsystem.bank.safebox.setup;

public record SafeTellerRouteHook(String tellerId,
                                  boolean bankBound,
                                  String outboundRouteRef,
                                  String returnRouteRef) {
    public boolean ready() {
        return bankBound
                && outboundRouteRef != null && !outboundRouteRef.isBlank()
                && returnRouteRef != null && !returnRouteRef.isBlank();
    }
}
