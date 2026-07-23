package net.austizz.ultimatebankingsystem.bank.safebox.route;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SafeTellerRoutePairResolver {
    private static final String PREMISES_KEY = "safeDepositPremises";

    private SafeTellerRoutePairResolver() {
    }

    public static Optional<Pair> resolve(TellerRequest request) {
        if (request == null || request.vault() == null || request.vault().context() == null
                || request.vault().bankId() == null || blank(request.vault().vaultId())
                || request.tellerId() == null) {
            return Optional.empty();
        }
        CompoundTag vault = exactVault(request.vault()).orElse(null);
        return vault == null ? Optional.empty() : resolve(request, vault);
    }

    public static boolean hasAnyExactPair(VaultRequest request) {
        if (request == null || request.context() == null || request.bankId() == null
                || blank(request.vaultId())) {
            return false;
        }
        CompoundTag vault = exactVault(request).orElse(null);
        if (vault == null) {
            return false;
        }
        ListTag hooks = compoundList(vault, "routeHooks");
        if (hooks == null) {
            return false;
        }
        Set<UUID> tellers = new LinkedHashSet<>();
        for (int index = 0; index < hooks.size(); index++) {
            CompoundTag hook = hooks.getCompound(index);
            if (!hook.contains("tellerId", Tag.TAG_STRING)) {
                return false;
            }
            UUID tellerId = uuid(hook.getString("tellerId"));
            if (tellerId != null) {
                tellers.add(tellerId);
            }
        }
        return tellers.stream()
                .anyMatch(tellerId -> resolve(new TellerRequest(request, tellerId)).isPresent());
    }

    private static Optional<Pair> resolve(TellerRequest request, CompoundTag vault) {
        RouteRefs refs = exactHook(vault, request.tellerId()).orElse(null);
        if (refs == null) {
            return Optional.empty();
        }
        ListTag routes = compoundList(request.vault().context().metadata(), SafeTellerRouteNbtStore.ROUTES_KEY);
        if (routes == null) {
            return Optional.empty();
        }
        CompoundTag outboundTag = null;
        CompoundTag returningTag = null;
        int outboundCount = 0;
        int returningCount = 0;
        for (int index = 0; index < routes.size(); index++) {
            CompoundTag route = routes.getCompound(index);
            if (!route.contains("id", Tag.TAG_STRING)) {
                continue;
            }
            String routeId = route.getString("id");
            if (refs.outbound().equals(routeId)) {
                outboundCount++;
                outboundTag = route;
            }
            if (refs.returning().equals(routeId)) {
                returningCount++;
                returningTag = route;
            }
        }
        if (outboundCount != 1 || returningCount != 1) {
            return Optional.empty();
        }
        SafeTellerRoute outbound = SafeTellerRouteNbtCodec.decode(outboundTag).orElse(null);
        SafeTellerRoute returning = SafeTellerRouteNbtCodec.decode(returningTag).orElse(null);
        if (!matches(outbound, request, SafeTellerRouteDirection.OUTBOUND)
                || !matches(returning, request, SafeTellerRouteDirection.RETURN)) {
            return Optional.empty();
        }
        return Optional.of(new Pair(outbound, returning));
    }

    private static Optional<CompoundTag> exactVault(VaultRequest request) {
        ListTag premises = compoundList(request.context().metadata(), PREMISES_KEY);
        if (premises == null) {
            return Optional.empty();
        }
        CompoundTag found = null;
        int count = 0;
        for (int premiseIndex = 0; premiseIndex < premises.size(); premiseIndex++) {
            CompoundTag premise = premises.getCompound(premiseIndex);
            if (!premise.contains("bankId", Tag.TAG_STRING)
                    || !request.bankId().toString().equalsIgnoreCase(premise.getString("bankId"))) {
                continue;
            }
            ListTag areas = compoundList(premise, "safeAreas");
            if (areas == null) {
                return Optional.empty();
            }
            for (int areaIndex = 0; areaIndex < areas.size(); areaIndex++) {
                ListTag vaults = compoundList(areas.getCompound(areaIndex), "vaults");
                if (vaults == null) {
                    return Optional.empty();
                }
                for (int vaultIndex = 0; vaultIndex < vaults.size(); vaultIndex++) {
                    CompoundTag vault = vaults.getCompound(vaultIndex);
                    if (vault.contains("id", Tag.TAG_STRING)
                            && request.vaultId().equals(vault.getString("id"))) {
                        count++;
                        found = vault;
                    }
                }
            }
        }
        return count == 1 ? Optional.of(found) : Optional.empty();
    }

    private static Optional<RouteRefs> exactHook(CompoundTag vault, UUID tellerId) {
        ListTag hooks = compoundList(vault, "routeHooks");
        if (hooks == null) {
            return Optional.empty();
        }
        RouteRefs found = null;
        int count = 0;
        for (int index = 0; index < hooks.size(); index++) {
            CompoundTag hook = hooks.getCompound(index);
            if (!hook.contains("tellerId", Tag.TAG_STRING)) {
                return Optional.empty();
            }
            if (!tellerId.toString().equalsIgnoreCase(hook.getString("tellerId"))) {
                continue;
            }
            count++;
            if (!hook.contains("bankBound", Tag.TAG_BYTE) || !hook.getBoolean("bankBound")
                    || !hook.contains("outboundRouteRef", Tag.TAG_STRING)
                    || !hook.contains("returnRouteRef", Tag.TAG_STRING)) {
                return Optional.empty();
            }
            String outbound = hook.getString("outboundRouteRef").trim();
            String returning = hook.getString("returnRouteRef").trim();
            if (blank(outbound) || blank(returning) || outbound.equals(returning)) {
                return Optional.empty();
            }
            found = new RouteRefs(outbound, returning);
        }
        return count == 1 ? Optional.of(found) : Optional.empty();
    }

    private static boolean matches(SafeTellerRoute route, TellerRequest request,
                                   SafeTellerRouteDirection direction) {
        return route != null
                && request.vault().bankId().toString().equalsIgnoreCase(route.bankId())
                && request.vault().vaultId().equals(route.vaultId())
                && request.tellerId().toString().equalsIgnoreCase(route.tellerId())
                && route.direction() == direction;
    }

    private static ListTag compoundList(CompoundTag parent, String key) {
        Tag raw = parent.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return null;
        }
        return list;
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public static final class Context {
        private final CompoundTag metadata;

        public Context(CompoundTag metadata) {
            this.metadata = metadata == null ? null : metadata.copy();
        }

        private CompoundTag metadata() {
            return metadata;
        }
    }

    public record VaultRequest(Context context, UUID bankId, String vaultId) {
        public VaultRequest {
            vaultId = vaultId == null ? "" : vaultId.strip();
        }

        public VaultRequest(CompoundTag metadata, UUID bankId, String vaultId) {
            this(new Context(metadata), bankId, vaultId);
        }

        public VaultRequest(Context context, String bankId, String vaultId) {
            this(context, uuid(bankId), vaultId);
        }

        public VaultRequest(CompoundTag metadata, String bankId, String vaultId) {
            this(metadata, uuid(bankId), vaultId);
        }
    }

    public record TellerRequest(VaultRequest vault, UUID tellerId) {
    }

    public record Pair(SafeTellerRoute outbound, SafeTellerRoute returning) {
        public String outboundRef() {
            return outbound.id();
        }

        public String returnRef() {
            return returning.id();
        }
    }

    private record RouteRefs(String outbound, String returning) {
    }
}
