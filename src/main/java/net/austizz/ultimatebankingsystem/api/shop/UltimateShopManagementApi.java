package net.austizz.ultimatebankingsystem.api.shop;

import net.austizz.ultimatebankingsystem.api.ApiManagementResult;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApiStatus.NonExtendable
@ApiStatus.AvailableSince("2.0.0")
public interface UltimateShopManagementApi {
    boolean isAvailable();

    List<ApiShopManagementSnapshot> getShops();

    Optional<ApiShopManagementSnapshot> getShop(UUID shopId);

    Optional<ApiShopManagementSnapshot> findShop(String nameOrId);

    List<ApiShopManagementSnapshot> getOwnedShops(UUID playerId);

    List<ApiShopManagementSnapshot> getAccessibleShops(UUID playerId);

    boolean shopExists(UUID shopId);

    boolean playerOwnsShop(UUID playerId, UUID shopId);

    boolean playerOwnsAnyShop(UUID playerId);

    Optional<String> getPlayerRole(UUID playerId, UUID shopId);

    boolean playerCanManageShop(UUID playerId, UUID shopId);

    boolean playerCanBuildInShop(UUID playerId, UUID shopId);

    boolean isShopSetupComplete(UUID shopId);

    boolean isShopCurrentlyOpen(UUID shopId);

    int getMaximumShopsPerOwner();

    List<String> getSupportedShopTypes();

    List<String> getSupportedParticipantRoles();

    ApiManagementResult createShop(UUID ownerId, String name, String type);

    ApiManagementResult renameShop(UUID ownerId, UUID shopId, String newName);

    ApiManagementResult setShopType(UUID ownerId, UUID shopId, String type);

    ApiManagementResult setOpeningHours(UUID ownerId, UUID shopId, String schedule);

    ApiManagementResult setParticipantRole(UUID ownerId, UUID shopId, UUID playerId, String role);

    ApiManagementResult removeParticipant(UUID ownerId, UUID shopId, UUID playerId);

    ApiManagementResult deleteShop(UUID ownerId, UUID shopId, String confirmationName);
}
