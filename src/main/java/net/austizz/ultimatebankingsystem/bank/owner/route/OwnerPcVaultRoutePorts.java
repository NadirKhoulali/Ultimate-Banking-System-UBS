package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;
import java.util.function.Predicate;

public interface OwnerPcVaultRoutePorts {
    Authority requestAuthority(UUID bankId, UUID tellerId);

    Authority saveAuthority(UUID bankId, UUID tellerId);

    WorldView world(String dimension);

    void commit(UUID bankId, CompoundTag metadata);

    record Authority(CompoundTag metadata,
                     boolean bankExists,
                     boolean activePc,
                     boolean poweredOn,
                     boolean sessionUnlocked,
                     boolean owner,
                     boolean permissionLevelThree,
                     boolean tellerLoaded,
                     boolean tellerBound,
                     boolean tellerSameBank,
                     boolean cashier,
                     OwnerPcVaultRouteEditSession.Origin origin,
                     String tellerDimension,
                     double tellerX,
                     double tellerY,
                     double tellerZ) {
        public Authority {
            tellerDimension = tellerDimension == null ? "" : tellerDimension;
        }
    }

    record WorldView(String dimension,
                     boolean available,
                     OwnerPcVaultRouteWorldBounds bounds,
                     Predicate<OwnerPcVaultRoutePosition> loaded,
                     Predicate<OwnerPcVaultRoutePosition> rfidScanner) {
        public WorldView {
            dimension = dimension == null ? "" : dimension;
        }

        public static WorldView unavailable(String dimension) {
            return new WorldView(dimension, false, null, null, null);
        }

        public boolean accepts(OwnerPcVaultRoutePosition position) {
            return available && bounds != null && bounds.contains(position)
                    && loaded != null && loaded.test(position);
        }

        public boolean isRfidScanner(OwnerPcVaultRoutePosition position) {
            return accepts(position) && rfidScanner != null && rfidScanner.test(position);
        }
    }
}
