package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.accountTypes.AccountTypes;
import net.austizz.ultimatebankingsystem.bank.Bank;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.BankVaultDoorBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.entity.ModEntities;
import net.austizz.ultimatebankingsystem.entity.custom.BankTellerEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.function.Consumer;

final class SafetyDepositBoxOpenAuthorityGameTestWorld {
    private SafetyDepositBoxOpenAuthorityGameTestWorld() {
    }

    static void placeStructures(GameTestHelper helper, ServerLevel level,
                                BlockPos relativeRow, BlockPos doorMaster) {
        helper.setBlock(relativeRow, ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get());
        BankVaultDoorBlock door = (BankVaultDoorBlock) ModBlocks.BANK_VAULT_DOOR.get();
        BlockState state = door.defaultBlockState();
        level.setBlock(doorMaster, state, Block.UPDATE_ALL);
        door.setPlacedBy(level, doorMaster, state, null, ItemStack.EMPTY);
    }

    static SafetyDepositBoxRowBlockEntity requireRow(ServerLevel level, BlockPos rowPos) {
        if (level.getBlockEntity(rowPos) instanceof SafetyDepositBoxRowBlockEntity row) {
            return row;
        }
        throw new IllegalStateException("row block entity was not created");
    }

    static void installModules(SafetyDepositBoxRowBlockEntity row, Consumer<String> fail) {
        for (int door = 0; door < SafetyDepositBoxRowBlockEntity.DOOR_COUNT; door++) {
            if (!row.installModule(door, SafetyDepositBoxRowBlockEntity.ModuleType.SMALL)) {
                fail.accept("could not install module " + door);
            }
        }
    }

    static AccountHolder installAccounts(
            CentralBank centralBank,
            SafetyDepositBoxOpenAuthorityGameTestSpec.Identities ids) {
        Bank bank = new Bank(ids.bankId(), "Task 14 Authority Bank", BigDecimal.valueOf(1_000L),
                1.0D, ids.playerId());
        centralBank.addBank(bank);
        AccountHolder primary = account(ids.playerId(), ids.bankId(), ids.accountId());
        bank.getBankAccounts().put(ids.accountId(), primary);
        bank.getBankAccounts().put(ids.siblingAccountId(),
                account(ids.playerId(), ids.bankId(), ids.siblingAccountId()));
        return primary;
    }

    static void grantOperator(MinecraftServer server, ServerPlayer player) {
        server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
        server.getPlayerList().sendPlayerPermissionLevel(player);
    }

    static BankTellerEntity installTeller(ServerLevel level, BlockPos rowPos,
                                          ServerPlayer player, UUID bankId,
                                          Consumer<String> fail) {
        BankTellerEntity teller = new BankTellerEntity(ModEntities.BANK_TELLER.get(), level);
        BlockPos position = rowPos.offset(0, 0, 2);
        teller.moveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        teller.initializeFromSpawn(player, BankTellerEntity.VARIANT_MALE, bankId);
        if (!level.addFreshEntity(teller)) {
            fail.accept("could not add bank teller");
        }
        return teller;
    }

    @SuppressWarnings("removal")
    static ServerPlayer makeRegisteredMockPlayer(GameTestHelper helper) {
        try {
            return helper.makeMockServerPlayerInLevel();
        } catch (UnsupportedOperationException exception) {
            ServerPlayer player = helper.getLevel().getServer().getPlayerList()
                    .getPlayerByName("test-mock-player");
            if (player == null) {
                throw exception;
            }
            return player;
        }
    }

    private static AccountHolder account(UUID playerId, UUID bankId, UUID accountId) {
        return new AccountHolder(playerId, BigDecimal.TEN, AccountTypes.CheckingAccount,
                "1234", bankId, accountId);
    }
}
