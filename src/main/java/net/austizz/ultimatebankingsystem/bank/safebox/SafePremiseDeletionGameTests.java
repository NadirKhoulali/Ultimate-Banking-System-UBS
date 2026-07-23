package net.austizz.ultimatebankingsystem.bank.safebox;

import com.mojang.authlib.GameProfile;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.owner.BankOwnerPcService;
import net.austizz.ultimatebankingsystem.bank.owner.premise.OwnerPcPremiseService;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.network.OwnerPcDesktopDataPayload;
import net.austizz.ultimatebankingsystem.network.OwnerPcPremiseActionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigDecimal;
import java.util.UUID;

@GameTestHolder(UltimateBankingSystem.MODID)
@PrefixGameTestTemplate(false)
public final class SafePremiseDeletionGameTests {
    private static final String EMPTY_TEMPLATE = "empty3x3x3";

    private SafePremiseDeletionGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void assignedPremiseDeletionPreservesRowAndAccountContents(GameTestHelper helper) {
        BlockPos relativePc = new BlockPos(0, 1, 1);
        BlockPos relativeRow = new BlockPos(1, 1, 1);
        BlockPos worldPc = helper.absolutePos(relativePc);
        BlockPos worldRow = helper.absolutePos(relativeRow);
        ServerLevel level = helper.getLevel();
        helper.setBlock(relativePc, ModBlocks.BANK_OWNER_PC.get());
        helper.setBlock(relativeRow, ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get());

        helper.runAfterDelay(1, () -> {
            require(helper,
                    level.getBlockEntity(worldRow) instanceof SafetyDepositBoxRowBlockEntity,
                    "Placed safety-deposit row must have its block entity");
            SafetyDepositBoxRowBlockEntity row =
                    (SafetyDepositBoxRowBlockEntity) level.getBlockEntity(worldRow);
            UUID bankId = UUID.fromString("10000000-0000-0000-0000-000000000901");
            UUID accountId = UUID.fromString("30000000-0000-0000-0000-000000000901");
            UUID operationId = UUID.fromString("40000000-0000-0000-0000-000000000901");
            require(helper, row.installModule(0, SafetyDepositBoxRowBlockEntity.ModuleType.SMALL),
                    "Test row must accept a small assignable door");
            row.assignDoor(0, accountId, "Box G-1");

            MinecraftServer server = level.getServer();
            ServerPlayer player = new ServerPlayer(
                    server, level,
                    new GameProfile(UUID.randomUUID(), "ubs-gametest-owner"),
                    ClientInformation.createDefault());
            player.setPos(worldPc.getX() + 0.5D, worldPc.getY() + 0.5D, worldPc.getZ() + 0.5D);
            UUID playerId = player.getUUID();
            CentralBank centralBank = new CentralBank();
            Bank bank = new Bank(bankId, "Guarded Delete Bank", BigDecimal.valueOf(1000L),
                    1.5D, playerId);
            centralBank.addBank(bank);
            AccountHolder account = new AccountHolder(
                    playerId, BigDecimal.TEN, AccountTypes.CheckingAccount, "1234", bankId, accountId);
            bank.getBankAccounts().put(accountId, account);
            CompoundTag seededContents = new CompoundTag();
            seededContents.putString("marker", "guarded-delete-must-preserve");
            seededContents.putInt("count", 17);
            account.getSafeBoxSlots().put(0, seededContents.copy());

            String dimension = level.dimension().location().toString();
            String premiseId = "premise-guarded-delete";
            CompoundTag metadata = metadata(
                    bankId, accountId, premiseId, dimension, worldRow);
            centralBank.putBankMetadata(bankId, metadata);

            BankOwnerPcService.rememberDesktopContext(
                    centralBank, playerId, dimension, worldPc.getX(), worldPc.getY(), worldPc.getZ());
            BankOwnerPcService.ActionResult authenticated = BankOwnerPcService.executeDesktopAction(
                    server, centralBank, player,
                    "AUTH_SET_PIN", "2468", "guarded delete recovery", "");
            require(helper, authenticated.success(),
                    "Owner PC authentication must succeed: " + authenticated.message());
            OwnerPcDesktopDataPayload desktop = BankOwnerPcService.buildDesktopData(centralBank, playerId);
            require(helper, desktop.poweredOn(), "Authenticated owner PC must be powered on");
            require(helper, desktop.sessionUnlocked(), "Authenticated owner PC session must be unlocked");

            CompoundTag metadataBefore = centralBank.getBankMetadata().get(bankId).copy();
            OwnerPcPremiseActionPayload payload = new OwnerPcPremiseActionPayload(
                    bankId,
                    operationId,
                    OwnerPcPremiseActionPayload.Action.DELETE,
                    premiseId,
                    null);
            OwnerPcPremiseService.Result result = OwnerPcPremiseService.execute(
                    server, centralBank, player, payload);

            require(helper, !result.success(), "Assigned descendant must block premise deletion");
            require(helper, operationId.equals(result.operationId()),
                    "Delete response must preserve the request operation id");
            require(helper, result.action() == OwnerPcPremiseActionPayload.Action.DELETE,
                    "Delete response must preserve the action");
            require(helper, premiseId.equals(result.premiseId()),
                    "Delete response must preserve the premise id");
            require(helper, result.message().contains("NON_EMPTY"),
                    "Populated premise must report NON_EMPTY");
            require(helper, result.message().contains("ASSIGNED"),
                    "Assigned descendant must report ASSIGNED");
            require(helper, metadataBefore.equals(centralBank.getBankMetadata().get(bankId)),
                    "Rejected production delete must preserve committed bank metadata");
            require(helper, level.getBlockState(worldRow).is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get()),
                    "Rejected deletion must not remove the row block");
            require(helper, level.getBlockEntity(worldRow) == row,
                    "Rejected deletion must not replace or remove the row block entity");
            require(helper, accountId.equals(row.getAssignedAccountId(0)),
                    "Rejected deletion must preserve the physical door assignment");
            require(helper, "Box G-1".equals(row.getBoxNumber(0)),
                    "Rejected deletion must preserve the physical box label");
            require(helper, bank.getBankAccount(accountId) == account,
                    "Rejected deletion must preserve the registered bank account");
            require(helper, seededContents.equals(account.getSafeBoxSlots().get(0)),
                    "Rejected deletion must preserve seeded account safe-box contents");
            helper.succeed();
        });
    }

    private static CompoundTag metadata(UUID bankId,
                                        UUID accountId,
                                        String premiseId,
                                        String dimension,
                                        BlockPos row) {
        CompoundTag vault = new CompoundTag();
        vault.putString("id", "vault-guarded-delete");
        vault.putString("safeAreaId", "area-guarded-delete");
        vault.putString("dimension", dimension);
        vault.putString("status", "READY");
        ListTag vaults = new ListTag();
        vaults.add(vault);

        CompoundTag area = new CompoundTag();
        area.putString("id", "area-guarded-delete");
        area.putString("premiseId", premiseId);
        area.putString("dimension", dimension);
        area.putInt("minX", row.getX());
        area.putInt("minY", row.getY());
        area.putInt("minZ", row.getZ());
        area.putInt("maxX", row.getX());
        area.putInt("maxY", row.getY());
        area.putInt("maxZ", row.getZ());
        area.put("vaults", vaults);
        ListTag areas = new ListTag();
        areas.add(area);

        CompoundTag premise = new CompoundTag();
        premise.putString("id", premiseId);
        premise.putString("bankId", bankId.toString());
        premise.putString("dimension", dimension);
        premise.putInt("minX", row.getX() - 1);
        premise.putInt("minY", row.getY() - 1);
        premise.putInt("minZ", row.getZ() - 1);
        premise.putInt("maxX", row.getX() + 1);
        premise.putInt("maxY", row.getY() + 1);
        premise.putInt("maxZ", row.getZ() + 1);
        premise.putInt("exitX", row.getX() - 2);
        premise.putInt("exitY", row.getY());
        premise.putInt("exitZ", row.getZ());
        premise.putFloat("exitYaw", 90.0F);
        premise.putString("mode", "PUBLIC");
        premise.put("safeAreas", areas);
        ListTag premises = new ListTag();
        premises.add(premise);

        CompoundTag assignment = new CompoundTag();
        assignment.putString("bankId", bankId.toString());
        assignment.putString("accountId", accountId.toString());
        assignment.putString("dimension", dimension);
        assignment.putInt("x", row.getX());
        assignment.putInt("y", row.getY());
        assignment.putInt("z", row.getZ());
        assignment.putInt("doorIndex", 0);
        assignment.putString("boxNumber", "Box G-1");
        ListTag assignments = new ListTag();
        assignments.add(assignment);

        CompoundTag metadata = new CompoundTag();
        metadata.putInt("safeDepositSetupVersion", 1);
        metadata.put("safeDepositPremises", premises);
        metadata.put("safeDepositAssignments", assignments);
        return metadata;
    }

    private static void require(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            helper.fail(message);
        }
    }
}
