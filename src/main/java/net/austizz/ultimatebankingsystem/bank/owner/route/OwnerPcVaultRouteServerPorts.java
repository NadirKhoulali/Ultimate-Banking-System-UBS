package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.util.RegistryKeysCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.UUID;

final class OwnerPcVaultRouteServerPorts implements OwnerPcVaultRoutePorts {
    private final MinecraftServer server;
    private final CentralBank centralBank;
    private final ServerPlayer player;

    OwnerPcVaultRouteServerPorts(MinecraftServer server,
                                 CentralBank centralBank,
                                 ServerPlayer player) {
        this.server = server;
        this.centralBank = centralBank;
        this.player = player;
    }

    @Override
    public Authority requestAuthority(UUID bankId, UUID tellerId) {
        if (server == null || centralBank == null || player == null) {
            return null;
        }
        BankOwnerPcService.ValidDesktopContext context =
                BankOwnerPcService.getValidDesktopContext(server, centralBank, player, true);
        OwnerPcDesktopDataPayload desktop = context == null
                ? null
                : BankOwnerPcService.buildDesktopData(centralBank, player.getUUID());
        boolean activeComputer = context != null && desktop != null
                && context.computerId().equals(desktop.computerId());
        OwnerPcVaultRouteEditSession.Origin origin = activeComputer
                ? new OwnerPcVaultRouteEditSession.Origin(
                        context.computerId(), context.dimensionId(),
                        context.x(), context.y(), context.z())
                : null;
        return authority(bankId, tellerId, activeComputer,
                desktop != null && desktop.poweredOn(),
                desktop != null && desktop.sessionUnlocked(), origin);
    }

    @Override
    public Authority saveAuthority(UUID bankId, UUID tellerId) {
        if (server == null || centralBank == null || player == null) {
            return null;
        }
        return authority(bankId, tellerId, false, false, false, null);
    }

    private Authority authority(UUID bankId,
                                UUID tellerId,
                                boolean activeComputer,
                                boolean poweredOn,
                                boolean sessionUnlocked,
                                OwnerPcVaultRouteEditSession.Origin origin) {
        BankTellerEntity teller = findTeller(tellerId);
        UUID boundBank = teller == null ? null : teller.getBoundBankId();
        String tellerDimension = teller == null ? ""
                : teller.level().dimension().location().toString();
        return new Authority(
                centralBank.getBankMetadata().get(bankId),
                centralBank.getBank(bankId) != null,
                activeComputer,
                poweredOn,
                sessionUnlocked,
                BankOwnerPcService.isOwner(centralBank, player.getUUID(), bankId),
                player.hasPermissions(3),
                teller != null,
                boundBank != null,
                bankId != null && bankId.equals(boundBank),
                teller != null && teller.isCashier(),
                origin,
                tellerDimension,
                teller == null ? 0.0D : teller.getX(),
                teller == null ? 0.0D : teller.getY(),
                teller == null ? 0.0D : teller.getZ());
    }

    @Override
    public WorldView world(String dimension) {
        ResourceLocation location = ResourceLocation.tryParse(dimension);
        ResourceKey<Level> key = location == null ? null : RegistryKeysCompat.createValueKey(
                RegistryKeysCompat.DIMENSION_REGISTRY_KEY, location);
        ServerLevel level = key == null || server == null ? null : server.getLevel(key);
        if (level == null) {
            return WorldView.unavailable(dimension);
        }
        return worldView(level);
    }

    static WorldView worldView(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        OwnerPcVaultRouteWorldBounds bounds = new OwnerPcVaultRouteWorldBounds(
                level.getMinBuildHeight(), level.getMaxBuildHeight(),
                border.getMinX(), border.getMaxX(), border.getMinZ(), border.getMaxZ());
        return new WorldView(level.dimension().location().toString(), true, bounds,
                position -> isLoadedWorldPosition(level, position),
                position -> isLoadedRfidScanner(level, position));
    }

    @Override
    public void commit(UUID bankId, CompoundTag metadata) {
        centralBank.putBankMetadata(bankId, metadata);
    }

    private BankTellerEntity findTeller(UUID tellerId) {
        if (tellerId == null) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(tellerId);
            if (entity != null) {
                return entity instanceof BankTellerEntity teller ? teller : null;
            }
        }
        return null;
    }

    private static boolean isLoadedWorldPosition(ServerLevel level,
                                                 OwnerPcVaultRoutePosition position) {
        BlockPos blockPos = new BlockPos(position.x(), position.y(), position.z());
        return level.isInWorldBounds(blockPos)
                && level.getWorldBorder().isWithinBounds(blockPos)
                && level.hasChunkAt(blockPos);
    }

    private static boolean isLoadedRfidScanner(ServerLevel level,
                                               OwnerPcVaultRoutePosition position) {
        if (!isLoadedWorldPosition(level, position)) {
            return false;
        }
        BlockPos blockPos = new BlockPos(position.x(), position.y(), position.z());
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4);
        return chunk != null
                && chunk.getBlockState(blockPos).is(ModBlocks.RFID_SCANNER.get())
                && chunk.getBlockEntities().get(blockPos) instanceof RfidScannerBlockEntity;
    }
}
