package net.austizz.ultimatebankingsystem.api.shop;

import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import net.austizz.ultimatebankingsystem.api.internal.ApiInternals;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.shop.ShopService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.Internal
public final class UltimateShopManagementApiImpl implements UltimateShopManagementApi {
    @Override
    public boolean isAvailable() {
        return ApiInternals.centralBank() != null;
    }

    @Override
    public List<ApiShopManagementSnapshot> getShops() {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null) {
            return List.of();
        }
        List<ApiShopManagementSnapshot> shops = ShopService.listAllShopSummaries(centralBank).stream()
                .map(summary -> snapshot(centralBank, summary))
                .sorted(Comparator.comparing(ApiShopManagementSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return List.copyOf(shops);
    }

    @Override
    public Optional<ApiShopManagementSnapshot> getShop(UUID shopId) {
        if (shopId == null) {
            return Optional.empty();
        }
        return getShops().stream().filter(shop -> shopId.equals(shop.shopId())).findFirst();
    }

    @Override
    public Optional<ApiShopManagementSnapshot> findShop(String nameOrId) {
        String query = nameOrId == null ? "" : nameOrId.trim();
        if (query.isEmpty()) {
            return Optional.empty();
        }
        try {
            Optional<ApiShopManagementSnapshot> byId = getShop(UUID.fromString(query));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return getShops().stream().filter(shop -> shop.name().equalsIgnoreCase(query)).findFirst();
    }

    @Override
    public List<ApiShopManagementSnapshot> getOwnedShops(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return getShops().stream().filter(shop -> playerId.equals(shop.ownerId())).toList();
    }

    @Override
    public List<ApiShopManagementSnapshot> getAccessibleShops(UUID playerId) {
        if (playerId == null) {
            return List.of();
        }
        return getShops().stream()
                .filter(shop -> shop.participants().stream().anyMatch(member -> playerId.equals(member.playerId())))
                .toList();
    }

    @Override
    public boolean shopExists(UUID shopId) {
        return getShop(shopId).isPresent();
    }

    @Override
    public boolean playerOwnsShop(UUID playerId, UUID shopId) {
        return playerId != null && getShop(shopId).map(shop -> playerId.equals(shop.ownerId())).orElse(false);
    }

    @Override
    public boolean playerOwnsAnyShop(UUID playerId) {
        return !getOwnedShops(playerId).isEmpty();
    }

    @Override
    public Optional<String> getPlayerRole(UUID playerId, UUID shopId) {
        CentralBank centralBank = ApiInternals.centralBank();
        if (centralBank == null || playerId == null || shopId == null) {
            return Optional.empty();
        }
        String role = ShopService.resolveShopRole(centralBank, playerId, shopId);
        return role == null || role.isBlank() ? Optional.empty() : Optional.of(role);
    }

    @Override
    public boolean playerCanManageShop(UUID playerId, UUID shopId) {
        CentralBank centralBank = ApiInternals.centralBank();
        return centralBank != null && ShopService.canManageShop(centralBank, playerId, shopId);
    }

    @Override
    public boolean playerCanBuildInShop(UUID playerId, UUID shopId) {
        CentralBank centralBank = ApiInternals.centralBank();
        return centralBank != null && ShopService.canBuildInShop(centralBank, playerId, shopId);
    }

    @Override
    public boolean isShopSetupComplete(UUID shopId) {
        CentralBank centralBank = ApiInternals.centralBank();
        return centralBank != null && ShopService.isShopSetupComplete(centralBank, shopId);
    }

    @Override
    public boolean isShopCurrentlyOpen(UUID shopId) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        return server != null && centralBank != null
                && ShopService.isShopOpenForShopping(centralBank, shopId, server.overworld().getGameTime());
    }

    @Override
    public int getMaximumShopsPerOwner() {
        return ShopService.maxShopsPerOwner();
    }

    @Override
    public List<String> getSupportedShopTypes() {
        return List.copyOf(ShopService.SHOP_TYPES);
    }

    @Override
    public List<String> getSupportedParticipantRoles() {
        return List.copyOf(ShopService.SHOP_PERMISSION_ROLES);
    }

    @Override
    public ApiManagementResult createShop(UUID ownerId, String name, String type) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = ApiInternals.centralBank();
        ServerPlayer owner = ApiInternals.onlinePlayer(ownerId);
        if (!ApiInternals.canMutate(server)) return ApiManagementResult.fail("Shop mutations must run on the server thread.");
        if (centralBank == null || owner == null) return ApiManagementResult.fail("Owner must be online and the shop service available.");
        return result(ShopService.createShop(centralBank, owner, name, type));
    }

    @Override
    public ApiManagementResult renameShop(UUID ownerId, UUID shopId, String newName) {
        CentralBank centralBank = mutationBank();
        return centralBank == null ? unavailable() : result(ShopService.renameShop(centralBank, ownerId, shopId, newName));
    }

    @Override
    public ApiManagementResult setShopType(UUID ownerId, UUID shopId, String type) {
        CentralBank centralBank = mutationBank();
        return centralBank == null ? unavailable() : result(ShopService.setShopType(centralBank, ownerId, shopId, type));
    }

    @Override
    public ApiManagementResult setOpeningHours(UUID ownerId, UUID shopId, String schedule) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = mutationBank();
        return centralBank == null ? unavailable() : result(ShopService.setShopHours(server, centralBank, ownerId, shopId, schedule));
    }

    @Override
    public ApiManagementResult setParticipantRole(UUID ownerId, UUID shopId, UUID playerId, String role) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = mutationBank();
        if (centralBank == null || playerId == null) return unavailable();
        return result(ShopService.setPermissionRole(server, centralBank, ownerId, shopId,
                playerId + "|" + (role == null ? "" : role.toUpperCase(Locale.ROOT))));
    }

    @Override
    public ApiManagementResult removeParticipant(UUID ownerId, UUID shopId, UUID playerId) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = mutationBank();
        if (centralBank == null || playerId == null) return unavailable();
        return result(ShopService.removePermissionRole(server, centralBank, ownerId, shopId, playerId.toString()));
    }

    @Override
    public ApiManagementResult deleteShop(UUID ownerId, UUID shopId, String confirmationName) {
        MinecraftServer server = ApiInternals.server();
        CentralBank centralBank = mutationBank();
        return centralBank == null ? unavailable() : result(ShopService.deleteShop(server, centralBank, ownerId, shopId, confirmationName));
    }

    private ApiShopManagementSnapshot snapshot(CentralBank centralBank, ShopService.ShopSummary summary) {
        UUID ownerId = ShopService.resolveShopOwnerId(centralBank, summary.shopId());
        Map<UUID, String> roleMap = ShopService.listShopParticipantRoles(centralBank, summary.shopId());
        List<ApiShopParticipantSnapshot> participants = new ArrayList<>();
        roleMap.forEach((playerId, role) -> participants.add(new ApiShopParticipantSnapshot(
                playerId, role,
                ShopService.canManageShop(centralBank, playerId, summary.shopId()),
                ShopService.canBuildInShop(centralBank, playerId, summary.shopId()))));
        participants.sort(Comparator.comparing(ApiShopParticipantSnapshot::role)
                .thenComparing(member -> member.playerId().toString()));
        MinecraftServer server = ApiInternals.server();
        boolean open = server != null && ShopService.isShopOpenForShopping(
                centralBank, summary.shopId(), server.overworld().getGameTime());
        return new ApiShopManagementSnapshot(summary.shopId(), ownerId, summary.name(), summary.type(),
                ShopService.prettyShopType(summary.type()), summary.level(), summary.revenueDollars(),
                summary.nextTargetDollars(), summary.usedClaimBlocks(), summary.claimCapacityBlocks(),
                summary.claimRegions(), summary.stockroomRegions(),
                ShopService.isShopSetupComplete(centralBank, summary.shopId()), open,
                ShopService.maxDisplayBlocksForLevel(summary.level()),
                ShopService.maxCashierSpawnEggsForLevel(summary.level()),
                ShopService.maxAssignedOrderPalletsForLevel(summary.level()), participants);
    }

    private CentralBank mutationBank() {
        MinecraftServer server = ApiInternals.server();
        return ApiInternals.canMutate(server) ? ApiInternals.centralBank() : null;
    }

    private ApiManagementResult unavailable() {
        return ApiManagementResult.fail("Shop service is unavailable or the call is not on the server thread.");
    }

    private ApiManagementResult result(ShopService.ShopActionResult result) {
        return result == null ? ApiManagementResult.fail("Shop action returned no result.")
                : new ApiManagementResult(result.success(), result.message());
    }
}
