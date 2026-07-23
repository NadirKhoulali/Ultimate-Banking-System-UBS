package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

final class SafetyDepositBoxExactRouteMalformedCases {
    private SafetyDepositBoxExactRouteMalformedCases() {
    }

    static List<Case> all(UUID bankId, UUID tellerId) {
        UUID wrongBank = new UUID(0L, 801L);
        UUID wrongTeller = new UUID(0L, 802L);
        return List.of(
                testCase("no-hook", metadata -> hooks(metadata).clear()),
                testCase("duplicate-hook", metadata -> hooks(metadata).add(hook(metadata).copy())),
                testCase("unbound-hook", metadata -> hook(metadata).putBoolean("bankBound", false)),
                missingHookTag("bankBound"),
                wrongTypeHookTag("bankBound"),
                missingHookTag("outboundRouteRef"),
                wrongTypeHookTag("outboundRouteRef"),
                missingHookTag("returnRouteRef"),
                wrongTypeHookTag("returnRouteRef"),
                testCase("blank-outbound-ref", metadata -> hook(metadata).putString("outboundRouteRef", " ")),
                testCase("blank-return-ref", metadata -> hook(metadata).putString("returnRouteRef", "")),
                testCase("same-route-refs", metadata -> hook(metadata).putString(
                        "returnRouteRef", hook(metadata).getString("outboundRouteRef"))),
                testCase("missing-outbound-route", metadata -> removeRoute(
                        metadata, hook(metadata).getString("outboundRouteRef"))),
                testCase("stale-outbound-ref", metadata -> hook(metadata).putString(
                        "outboundRouteRef", "stale-route-id")),
                duplicateRoute("duplicate-outbound-id", "OUTBOUND", false),
                duplicateRoute("malformed-duplicate-outbound-id", "OUTBOUND", true),
                duplicateRoute("duplicate-return-id", "RETURN", false),
                testCase("missing-required-route-field", metadata -> route(metadata, "OUTBOUND").remove("finishX")),
                testCase("malformed-route-steps", metadata -> route(metadata, "RETURN").putInt("steps", 7)),
                testCase("malformed-route-storage", metadata -> metadata.putInt("safeTellerRoutes", 7)),
                replacement("outbound-wrong-bank", "OUTBOUND",
                        new RouteIdentity(wrongBank, vaultId(), tellerId)),
                replacement("outbound-wrong-vault", "OUTBOUND",
                        new RouteIdentity(bankId, "wrong-vault", tellerId)),
                replacement("outbound-wrong-teller", "OUTBOUND",
                        new RouteIdentity(bankId, vaultId(), wrongTeller)),
                replacement("return-wrong-bank", "RETURN",
                        new RouteIdentity(wrongBank, vaultId(), tellerId)),
                replacement("return-wrong-vault", "RETURN",
                        new RouteIdentity(bankId, "wrong-vault", tellerId)),
                replacement("return-wrong-teller", "RETURN",
                        new RouteIdentity(bankId, vaultId(), wrongTeller)),
                testCase("outbound-direction-missing", metadata -> route(metadata, "OUTBOUND").remove("direction")),
                testCase("outbound-direction-invalid", metadata -> route(metadata, "OUTBOUND")
                        .putString("direction", "NOT_A_DIRECTION")),
                testCase("return-direction-missing", metadata -> route(metadata, "RETURN").remove("direction")),
                testCase("return-direction-invalid", metadata -> route(metadata, "RETURN")
                        .putString("direction", "NOT_A_DIRECTION")),
                testCase("swapped-directions", metadata -> {
                    CompoundTag routeHook = hook(metadata);
                    String outbound = routeHook.getString("outboundRouteRef");
                    routeHook.putString("outboundRouteRef", routeHook.getString("returnRouteRef"));
                    routeHook.putString("returnRouteRef", outbound);
                })
        );
    }

    private static Case missingHookTag(String key) {
        return testCase("missing-hook-" + key, metadata -> hook(metadata).remove(key));
    }

    private static Case wrongTypeHookTag(String key) {
        return testCase("wrong-type-hook-" + key, metadata -> hook(metadata).putInt(key, 1));
    }

    private static Case duplicateRoute(String name, String direction, boolean malformed) {
        return testCase(name, metadata -> {
            CompoundTag duplicate = route(metadata, direction).copy();
            if (malformed) {
                duplicate.putString("direction", "NOT_A_DIRECTION");
            }
            routes(metadata).add(duplicate);
        });
    }

    private static Case replacement(String name, String direction, RouteIdentity identity) {
        return testCase(name, metadata -> {
            SafeTellerRouteDirection routeDirection = SafeTellerRouteDirection.valueOf(direction);
            CompoundTag route = route(metadata, direction);
            String id = SafeTellerRoute.stableId(
                    identity.bankId().toString(), identity.vaultId(), identity.tellerId().toString(), routeDirection);
            route.putString("id", id);
            route.putString("bankId", identity.bankId().toString());
            route.putString("vaultId", identity.vaultId());
            route.putString("tellerId", identity.tellerId().toString());
            hook(metadata).putString(direction.equals("OUTBOUND")
                    ? "outboundRouteRef" : "returnRouteRef", id);
        });
    }

    private static Case testCase(String name, Consumer<CompoundTag> mutation) {
        return new Case(name, mutation);
    }

    private static void removeRoute(CompoundTag metadata, String id) {
        ListTag routes = routes(metadata);
        for (int index = 0; index < routes.size(); index++) {
            if (id.equals(routes.getCompound(index).getString("id"))) {
                routes.remove(index);
                return;
            }
        }
    }

    private static CompoundTag route(CompoundTag metadata, String direction) {
        ListTag routes = routes(metadata);
        for (int index = 0; index < routes.size(); index++) {
            CompoundTag route = routes.getCompound(index);
            if (direction.equals(route.getString("direction"))) {
                return route;
            }
        }
        throw new IllegalStateException("Missing route direction " + direction);
    }

    private static ListTag routes(CompoundTag metadata) {
        return metadata.getList("safeTellerRoutes", Tag.TAG_COMPOUND);
    }

    private static CompoundTag hook(CompoundTag metadata) {
        return hooks(metadata).getCompound(0);
    }

    private static ListTag hooks(CompoundTag metadata) {
        CompoundTag premise = metadata.getList("safeDepositPremises", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag area = premise.getList("safeAreas", Tag.TAG_COMPOUND).getCompound(0);
        CompoundTag vault = area.getList("vaults", Tag.TAG_COMPOUND).getCompound(0);
        return vault.getList("routeHooks", Tag.TAG_COMPOUND);
    }

    private static String vaultId() {
        return SafetyDepositBoxOpenAuthorityGameTestMetadata.VAULT_ID;
    }

    record Case(String name, Consumer<CompoundTag> mutation) {
        void apply(CompoundTag metadata) {
            mutation.accept(metadata);
        }
    }

    private record RouteIdentity(UUID bankId, String vaultId, UUID tellerId) {
    }
}
