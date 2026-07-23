package net.austizz.ultimatebankingsystem.bank.safebox.route;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteSaveResult.Status;
public final class SafeTellerRouteNbtStore {
    public static final String ROUTES_KEY = "safeTellerRoutes";
    private static final String PREMISES_KEY = "safeDepositPremises";
    private SafeTellerRouteNbtStore() {
    }
    public static List<SafeTellerRoute> readAll(CompoundTag metadata) {
        ListTag tags = metadata == null ? null : compoundList(metadata, ROUTES_KEY, false);
        if (tags == null) {
            return List.of();
        }
        Map<String, SafeTellerRoute> routes = new LinkedHashMap<>();
        for (int i = 0; i < tags.size(); i++) {
            SafeTellerRouteNbtCodec.decode(tags.getCompound(i))
                    .ifPresent(route -> routes.putIfAbsent(route.id(), route));
        }
        return List.copyOf(routes.values());
    }
    public static Optional<SafeTellerRoute> resolve(CompoundTag metadata, String routeId) {
        if (routeId == null || routeId.isBlank()) {
            return Optional.empty();
        }
        return readAll(metadata).stream().filter(route -> route.id().equals(routeId)).findFirst();
    }
    public static List<SafeTellerRoute> listForVaultTeller(CompoundTag metadata,
                                                           String vaultId,
                                                           String tellerId) {
        if (vaultId == null || vaultId.isBlank() || tellerId == null || tellerId.isBlank()) {
            return List.of();
        }
        List<SafeTellerRoute> matches = readAll(metadata).stream()
                .filter(route -> route.vaultId().equals(vaultId) && route.tellerId().equals(tellerId))
                .toList();
        if (matches.isEmpty()) {
            return matches;
        }
        String bankId = matches.get(0).bankId();
        return matches.stream().allMatch(route -> route.bankId().equals(bankId)) ? matches : List.of();
    }
    public static List<SafeTellerRoute> listForVaultTeller(CompoundTag metadata,
                                                           String bankId,
                                                           String vaultId,
                                                           String tellerId) {
        if (bankId == null || bankId.isBlank() || vaultId == null || vaultId.isBlank()
                || tellerId == null || tellerId.isBlank()) {
            return List.of();
        }
        return readAll(metadata).stream()
                .filter(route -> route.bankId().equals(bankId) && route.vaultId().equals(vaultId)
                        && route.tellerId().equals(tellerId))
                .toList();
    }
    public static SafeTellerRouteSaveResult saveAndBind(CompoundTag metadata, SafeTellerRoute route) {
        SafeTellerRouteValidation validation = SafeTellerRouteValidator.validate(route);
        if (!validation.valid()) {
            return SafeTellerRouteSaveResult.failure(Status.INVALID_ROUTE, route, validation);
        }
        if (metadata == null) {
            return failure(Status.METADATA_MISSING, route, validation);
        }
        ListTag routes = compoundList(metadata, ROUTES_KEY, false);
        if (routes == null) {
            return failure(Status.ROUTE_STORAGE_MALFORMED, route, validation);
        }
        ListTag premises = compoundList(metadata, PREMISES_KEY, true);
        if (premises == null) {
            return failure(Status.PREMISES_MALFORMED, route, validation);
        }
        VaultSearch search = findVault(premises, route);
        if (search.malformed()) {
            return failure(Status.PREMISES_MALFORMED, route, validation);
        }
        if (search.count() == 0) {
            return failure(Status.VAULT_NOT_FOUND, route, validation);
        }
        if (search.count() > 1) {
            return failure(Status.VAULT_AMBIGUOUS, route, validation);
        }
        CompoundTag vault = search.vault(premises);
        ListTag hooks = compoundList(vault, "routeHooks", false);
        if (hooks == null) {
            return failure(Status.ROUTE_HOOKS_MALFORMED, route, validation);
        }
        HookUpdate hookUpdate = updateHook(hooks, route);
        if (hookUpdate.failure() != null) {
            return failure(hookUpdate.failure(), route, validation);
        }
        ListTag updatedPremises = replaceVault(premises, search, hookUpdate.hooks());
        ListTag updatedRoutes = upsertRoute(routes, route);
        metadata.put(PREMISES_KEY, updatedPremises);
        metadata.put(ROUTES_KEY, updatedRoutes);
        return SafeTellerRouteSaveResult.saved(route);
    }
    private static VaultSearch findVault(ListTag premises, SafeTellerRoute route) {
        VaultSearch found = new VaultSearch(-1, -1, -1, 0, false);
        for (int premiseIndex = 0; premiseIndex < premises.size(); premiseIndex++) {
            CompoundTag premise = premises.getCompound(premiseIndex);
            if (!route.bankId().equals(premise.getString("bankId"))) {
                continue;
            }
            ListTag areas = compoundList(premise, "safeAreas", true);
            if (areas == null) {
                return found.malformedResult();
            }
            for (int areaIndex = 0; areaIndex < areas.size(); areaIndex++) {
                ListTag vaults = compoundList(areas.getCompound(areaIndex), "vaults", true);
                if (vaults == null) {
                    return found.malformedResult();
                }
                for (int vaultIndex = 0; vaultIndex < vaults.size(); vaultIndex++) {
                    if (route.vaultId().equals(vaults.getCompound(vaultIndex).getString("id"))) {
                        found = new VaultSearch(premiseIndex, areaIndex, vaultIndex, found.count() + 1, false);
                    }
                }
            }
        }
        return found;
    }
    private static HookUpdate updateHook(ListTag hooks, SafeTellerRoute route) {
        int found = -1;
        for (int i = 0; i < hooks.size(); i++) {
            CompoundTag candidate = hooks.getCompound(i);
            if (!candidate.contains("tellerId", Tag.TAG_STRING)) {
                return new HookUpdate(null, Status.ROUTE_HOOKS_MALFORMED);
            }
            if (!route.tellerId().equals(candidate.getString("tellerId"))) {
                continue;
            }
            if (!validHook(candidate)) {
                return new HookUpdate(null, Status.ROUTE_HOOKS_MALFORMED);
            }
            if (found >= 0) {
                return new HookUpdate(null, Status.ROUTE_HOOK_AMBIGUOUS);
            }
            found = i;
        }
        ListTag updated = copyList(hooks);
        CompoundTag hook = found < 0 ? new CompoundTag() : hooks.getCompound(found).copy();
        hook.putString("tellerId", route.tellerId());
        hook.putBoolean("bankBound", true);
        ensureRef(hook, "outboundRouteRef");
        ensureRef(hook, "returnRouteRef");
        String key = route.direction() == SafeTellerRouteDirection.OUTBOUND
                ? "outboundRouteRef" : "returnRouteRef";
        hook.putString(key, route.id());
        if (found < 0) {
            updated.add(hook);
        } else {
            updated.set(found, hook);
        }
        return new HookUpdate(updated, null);
    }
    private static boolean validHook(CompoundTag hook) {
        return hook.contains("bankBound", Tag.TAG_BYTE)
                && stringOrMissing(hook, "outboundRouteRef")
                && stringOrMissing(hook, "returnRouteRef");
    }
    private static boolean stringOrMissing(CompoundTag hook, String key) {
        return !hook.contains(key) || hook.contains(key, Tag.TAG_STRING);
    }
    private static void ensureRef(CompoundTag hook, String key) {
        if (!hook.contains(key, Tag.TAG_STRING)) {
            hook.putString(key, "");
        }
    }
    private static ListTag replaceVault(ListTag premises, VaultSearch search, ListTag hooks) {
        ListTag updatedPremises = copyList(premises);
        CompoundTag premise = premises.getCompound(search.premiseIndex()).copy();
        ListTag areas = compoundList(premise, "safeAreas", true);
        ListTag updatedAreas = copyList(areas);
        CompoundTag area = areas.getCompound(search.areaIndex()).copy();
        ListTag vaults = compoundList(area, "vaults", true);
        ListTag updatedVaults = copyList(vaults);
        CompoundTag vault = vaults.getCompound(search.vaultIndex()).copy();
        vault.put("routeHooks", hooks);
        updatedVaults.set(search.vaultIndex(), vault);
        area.put("vaults", updatedVaults);
        updatedAreas.set(search.areaIndex(), area);
        premise.put("safeAreas", updatedAreas);
        updatedPremises.set(search.premiseIndex(), premise);
        return updatedPremises;
    }
    private static ListTag upsertRoute(ListTag routes, SafeTellerRoute route) {
        ListTag updated = new ListTag();
        boolean replaced = false;
        for (int i = 0; i < routes.size(); i++) {
            CompoundTag existing = routes.getCompound(i);
            if (route.id().equals(existing.getString("id"))) {
                if (!replaced) {
                    updated.add(SafeTellerRouteNbtCodec.encode(route, existing));
                    replaced = true;
                }
            } else {
                updated.add(existing.copy());
            }
        }
        if (!replaced) {
            updated.add(SafeTellerRouteNbtCodec.encode(route, null));
        }
        return updated;
    }
    private static ListTag compoundList(CompoundTag parent, String key, boolean required) {
        Tag raw = parent.get(key);
        if (raw == null) {
            return required ? null : new ListTag();
        }
        if (!(raw instanceof ListTag list) || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return null;
        }
        return list;
    }
    private static ListTag copyList(ListTag source) {
        ListTag copy = new ListTag();
        for (Tag tag : source) {
            copy.add(tag.copy());
        }
        return copy;
    }
    private static SafeTellerRouteSaveResult failure(Status status,
                                                      SafeTellerRoute route,
                                                      SafeTellerRouteValidation validation) {
        return SafeTellerRouteSaveResult.failure(status, route, validation);
    }
    private record HookUpdate(ListTag hooks, Status failure) {
    }
    private record VaultSearch(int premiseIndex,
                               int areaIndex,
                               int vaultIndex,
                               int count,
                               boolean malformed) {
        VaultSearch malformedResult() {
            return new VaultSearch(premiseIndex, areaIndex, vaultIndex, count, true);
        }
        CompoundTag vault(ListTag premises) {
            CompoundTag premise = premises.getCompound(premiseIndex);
            CompoundTag area = compoundList(premise, "safeAreas", true).getCompound(areaIndex);
            return compoundList(area, "vaults", true).getCompound(vaultIndex);
        }
    }
}
