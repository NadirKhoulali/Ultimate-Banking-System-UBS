package net.austizz.ultimatebankingsystem.bank.safebox.route;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record SafeTellerRoute(String id,
                              String bankId,
                              String vaultId,
                              String tellerId,
                              SafeTellerRouteDirection direction,
                              String dimension,
                              SafeTellerRoutePosition start,
                              SafeTellerRoutePosition finish,
                              List<SafeTellerRouteStep> steps) {
    public SafeTellerRoute {
        id = clean(id);
        bankId = clean(bankId);
        vaultId = clean(vaultId);
        tellerId = clean(tellerId);
        dimension = clean(dimension);
        steps = steps == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(steps));
    }

    public static SafeTellerRoute create(String bankId,
                                         String vaultId,
                                         String tellerId,
                                         SafeTellerRouteDirection direction,
                                         String dimension,
                                         SafeTellerRoutePosition start,
                                         SafeTellerRoutePosition finish,
                                         List<? extends SafeTellerRouteStep> steps) {
        List<SafeTellerRouteStep> copied = steps == null ? List.of() : new ArrayList<>(steps);
        return new SafeTellerRoute(stableId(bankId, vaultId, tellerId, direction), bankId, vaultId,
                tellerId, direction, dimension, start, finish, copied);
    }

    public static String stableId(String bankId,
                                  String vaultId,
                                  String tellerId,
                                  SafeTellerRouteDirection direction) {
        String bank = clean(bankId);
        String vault = clean(vaultId);
        String teller = clean(tellerId);
        if (bank.isBlank() || vault.isBlank() || teller.isBlank() || direction == null) {
            return "";
        }
        String key = bank + '\u001f' + vault + '\u001f' + teller + '\u001f' + direction.name();
        return "safe-teller-route-" + UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }
}
