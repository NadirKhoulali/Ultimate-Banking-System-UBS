package net.austizz.ultimatebankingsystem.bank.owner.route;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRoute;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteDirection;
import net.austizz.ultimatebankingsystem.bank.safebox.route.SafeTellerRouteNbtStore;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRoutePosition;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteRequestPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteSavePayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcVaultRouteStepPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class OwnerPcVaultRouteGameTests {
    private static final UUID BANK_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001");
    private static final UUID TELLER_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000002");
    private static final UUID PLAYER_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000003");
    private static final UUID SESSION_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000004");
    private static final String VAULT_ID = "vault-main";

    private OwnerPcVaultRouteGameTests() {
    }

    @GameTest(template = "empty3x3x3", timeoutTicks = 80)
    public static void publicPayloadBoundaryAuthorizesAndCommitsAtomically(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        OwnerPcVaultRoutePosition start = position(helper.absolutePos(new BlockPos(1, 1, 1)));
        OwnerPcVaultRoutePosition finish = position(helper.absolutePos(new BlockPos(2, 1, 2)));
        OwnerPcVaultRouteSavePayload draft = draft(SESSION_ID, dimension, start, finish,
                new OwnerPcVaultRouteStepPayload.Walk(finish));
        OwnerPcVaultRoutePorts.WorldView world = OwnerPcVaultRouteServerPorts.worldView(level);
        OwnerPcVaultRouteEditSessionStore sessions = new OwnerPcVaultRouteEditSessionStore(
                () -> 1_000L, () -> SESSION_ID, 60_000L);

        CompoundTag lockedMetadata = metadata(dimension, finish);
        String lockedBefore = lockedMetadata.toString();
        TestPorts locked = new TestPorts(lockedMetadata, false, world, start);
        OwnerPcVaultRouteService.Result lockedResult = OwnerPcVaultRouteService.request(
                locked, sessions, PLAYER_ID, request());
        require(helper, !lockedResult.editor().success(), "Locked PC request must be denied");
        require(helper, locked.commits == 0, "Locked PC save must not commit metadata");
        require(helper, locked.worldCalls == 0, "World lookup must follow authorization");
        require(helper, lockedBefore.equals(lockedMetadata.toString()),
                "Locked PC save mutated metadata");

        CompoundTag original = metadata(dimension, finish);
        String originalBefore = original.toString();
        TestPorts owner = new TestPorts(original, true, world, start);
        OwnerPcVaultRouteService.Result opened = OwnerPcVaultRouteService.request(
                owner, sessions, PLAYER_ID, request());
        require(helper, SESSION_ID.equals(opened.editor().editSessionId()),
                "Live PC request did not issue the route edit session");
        OwnerPcVaultRouteService.Result saved = OwnerPcVaultRouteService.save(
                owner, sessions, PLAYER_ID, draft);
        require(helper, saved.editor().success() && saved.persisted(),
                "Authorized typed save did not persist");
        require(helper, owner.commits == 1, "Authorized save must commit exactly once");
        require(helper, originalBefore.equals(original.toString()),
                "Save must stage a copy before committing");
        List<SafeTellerRoute> routes = SafeTellerRouteNbtStore.readAll(owner.metadata);
        require(helper, routes.size() == 1, "Committed metadata must contain one route");
        SafeTellerRoute committed = routes.getFirst();
        require(helper, BANK_ID.toString().equals(committed.bankId()),
                "Committed route changed the bank identity");
        require(helper, VAULT_ID.equals(committed.vaultId()),
                "Committed route changed the vault identity");
        require(helper, TELLER_ID.toString().equals(committed.tellerId()),
                "Committed route changed the teller identity");
        require(helper, SafeTellerRouteDirection.OUTBOUND == committed.direction(),
                "Committed route changed direction");

        OwnerPcVaultRouteService.Result requested = OwnerPcVaultRouteService.request(
                owner, sessions, PLAYER_ID, request());
        require(helper, requested.editor().success() && requested.editor().hasRoute(),
                "Public request did not read the committed route");
        require(helper, requested.editor().steps().equals(draft.steps()),
                "Public request changed ordered route steps");

        CompoundTag invalidMetadata = metadata(dimension, finish);
        String invalidBefore = invalidMetadata.toString();
        TestPorts invalid = new TestPorts(invalidMetadata, true, world, start);
        OwnerPcVaultRouteService.request(invalid, sessions, PLAYER_ID, request());
        OwnerPcVaultRoutePosition aboveBuildHeight = new OwnerPcVaultRoutePosition(
                start.x(), level.getMaxBuildHeight(), start.z());
        OwnerPcVaultRouteService.Result invalidResult = OwnerPcVaultRouteService.save(
                invalid, sessions, PLAYER_ID, draft(SESSION_ID, dimension, start, finish,
                        new OwnerPcVaultRouteStepPayload.Walk(aboveBuildHeight)));
        require(helper, !invalidResult.editor().success() && invalid.commits == 0,
                "Out-of-build-height route must be rejected without commit");
        require(helper, invalidBefore.equals(invalidMetadata.toString()),
                "Rejected coordinate mutated metadata");
        helper.succeed();
    }

    private static OwnerPcVaultRouteRequestPayload request() {
        return new OwnerPcVaultRouteRequestPayload(BANK_ID, VAULT_ID, TELLER_ID,
                SafeTellerRouteDirection.OUTBOUND);
    }

    private static OwnerPcVaultRouteSavePayload draft(
            UUID editSessionId,
            String dimension,
            OwnerPcVaultRoutePosition start,
            OwnerPcVaultRoutePosition finish,
            OwnerPcVaultRouteStepPayload step) {
        return new OwnerPcVaultRouteSavePayload(editSessionId, BANK_ID, VAULT_ID,
                TELLER_ID, SafeTellerRouteDirection.OUTBOUND, dimension, start, finish,
                List.of(step));
    }

    private static OwnerPcVaultRoutePosition position(BlockPos position) {
        return new OwnerPcVaultRoutePosition(position.getX(), position.getY(), position.getZ());
    }

    private static CompoundTag metadata(String dimension, OwnerPcVaultRoutePosition finish) {
        CompoundTag vault = new CompoundTag();
        vault.putString("id", VAULT_ID);
        vault.putString("safeAreaId", "area-main");
        vault.putString("dimension", dimension);
        vault.putString("status", "ROUTES_PENDING");
        vault.put("routeHooks", new ListTag());

        CompoundTag area = new CompoundTag();
        area.putString("id", "area-main");
        area.putString("premiseId", "premise-main");
        bounds(area, dimension, finish.x() - 1, finish.y() - 1, finish.z() - 1,
                finish.x() + 1, finish.y() + 1, finish.z() + 1);
        area.put("vaults", list(vault));

        CompoundTag premise = new CompoundTag();
        premise.putString("id", "premise-main");
        premise.putString("bankId", BANK_ID.toString());
        premise.putString("mode", "PUBLIC");
        bounds(premise, dimension, finish.x() - 8, finish.y() - 4, finish.z() - 8,
                finish.x() + 8, finish.y() + 4, finish.z() + 8);
        premise.putInt("exitX", finish.x() + 9);
        premise.putInt("exitY", finish.y());
        premise.putInt("exitZ", finish.z());
        premise.putFloat("exitYaw", 0.0F);
        premise.put("safeAreas", list(area));

        CompoundTag metadata = new CompoundTag();
        metadata.put("safeDepositPremises", list(premise));
        metadata.put("safeTellerRoutes", new ListTag());
        return metadata;
    }

    private static void bounds(CompoundTag tag, String dimension,
                               int minX, int minY, int minZ,
                               int maxX, int maxY, int maxZ) {
        tag.putString("dimension", dimension);
        tag.putInt("minX", minX);
        tag.putInt("minY", minY);
        tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX);
        tag.putInt("maxY", maxY);
        tag.putInt("maxZ", maxZ);
    }

    private static ListTag list(CompoundTag value) {
        ListTag list = new ListTag();
        list.add(value);
        return list;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }

    private static final class TestPorts implements OwnerPcVaultRoutePorts {
        private CompoundTag metadata;
        private final boolean unlocked;
        private final WorldView world;
        private final OwnerPcVaultRoutePosition teller;
        private int commits;
        private int worldCalls;

        private TestPorts(CompoundTag metadata, boolean unlocked, WorldView world,
                          OwnerPcVaultRoutePosition teller) {
            this.metadata = metadata;
            this.unlocked = unlocked;
            this.world = world;
            this.teller = teller;
        }

        @Override
        public Authority requestAuthority(UUID bankId, UUID tellerId) {
            return new Authority(metadata, BANK_ID.equals(bankId), true, true, unlocked,
                    true, false, TELLER_ID.equals(tellerId), true, true, false,
                    new OwnerPcVaultRouteEditSession.Origin(
                            "game-pc", world.dimension(), 0, 0, 0),
                    world.dimension(), teller.x() + 0.5D, teller.y(), teller.z() + 0.5D);
        }

        @Override
        public Authority saveAuthority(UUID bankId, UUID tellerId) {
            return new Authority(metadata, BANK_ID.equals(bankId), false, false, false,
                    true, false, TELLER_ID.equals(tellerId), true, true, false, null,
                    world.dimension(), teller.x() + 0.5D, teller.y(), teller.z() + 0.5D);
        }

        @Override
        public WorldView world(String dimension) {
            worldCalls++;
            return world;
        }

        @Override
        public void commit(UUID bankId, CompoundTag staged) {
            if (!BANK_ID.equals(bankId)) {
                throw new IllegalArgumentException("unexpected bank ID");
            }
            commits++;
            metadata = staged;
        }
    }
}
