package net.austizz.ultimatebankingsystem.bank.safebox.escort;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoutePairResolver;
import java.util.Optional;

final class SafeBoxEscortMetadataRoutes {
    private SafeBoxEscortMetadataRoutes() {
    }

    static Optional<SafeTellerRoutePairResolver.Pair> exactPair(
            SafeTellerRoutePairResolver.TellerRequest request) {
        return SafeTellerRoutePairResolver.resolve(request);
    }
}
