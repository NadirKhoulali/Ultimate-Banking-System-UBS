package net.austizz.ultimatebankingsystem.menu;

import net.austizz.ultimatebankingsystem.account.AccountHolder;
import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.austizz.ultimatebankingsystem.bank.safebox.SafetyDepositBoxService;
import net.austizz.ultimatebankingsystem.bank.safebox.SafeAccessLogService;
import net.austizz.ultimatebankingsystem.bank.safebox.viewing.SafeBoxViewingCoordinator;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.SafetyDepositBoxRowBlockEntity;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SafetyDepositBoxMenu extends AbstractContainerMenu {
    public static final int COLUMNS = 9;
    public static final int PLAYER_INV_X = 8;

    private final UUID accountId;
    private final BlockPos rowPos;
    private final int doorIndex;
    private final int slotCount;
    private final int rows;
    private final String boxNumber;
    private final SimpleContainer container;
    private List<ItemStack> lastDisplayContents = List.of();

    public static SafetyDepositBoxMenu fromNetwork(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        UUID accountId = new UUID(data.readLong(), data.readLong());
        BlockPos rowPos = data.readBlockPos();
        int doorIndex = data.readVarInt();
        int slotCount = data.readVarInt();
        String boxNumber = data.readUtf(64);
        return new SafetyDepositBoxMenu(containerId, playerInventory, accountId, rowPos, doorIndex, slotCount, boxNumber);
    }

    public SafetyDepositBoxMenu(int containerId,
                                Inventory playerInventory,
                                UUID accountId,
                                BlockPos rowPos,
                                int doorIndex,
                                int slotCount,
                                String boxNumber) {
        super(ModMenus.SAFETY_DEPOSIT_BOX.get(), containerId);
        this.accountId = accountId;
        this.rowPos = rowPos;
        this.doorIndex = Math.max(0, Math.min(SafetyDepositBoxRowBlockEntity.DOOR_COUNT - 1, doorIndex));
        this.slotCount = Math.max(1, Math.min(54, slotCount));
        this.rows = Math.max(1, (int) Math.ceil(this.slotCount / (double) COLUMNS));
        this.boxNumber = boxNumber == null || boxNumber.isBlank() ? "Safety Box" : boxNumber;
        this.container = new SimpleContainer(this.rows * COLUMNS);

        loadSafeBoxContents(playerInventory.player);
        lastDisplayContents = displayContentsSnapshot();
        addSafetyBoxSlots();
        addPlayerInventorySlots(playerInventory);
    }

    public int getRows() {
        return rows;
    }

    public String getBoxNumber() {
        return boxNumber;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!mayInteract(player)) {
            closeAfterAuthorityLoss(player);
            return ItemStack.EMPTY;
        }
        ItemStack copied = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        copied = original.copy();
        if (index < this.slotCount) {
            if (!this.moveItemStackTo(original, this.rows * COLUMNS, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(original, 0, this.slotCount, false)) {
            return ItemStack.EMPTY;
        }

        if (original.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copied;
    }

    @Override
    public void clicked(int slotId, int buttonId, ClickType clickType, Player player) {
        if (!mayInteract(player)) {
            closeAfterAuthorityLoss(player);
            return;
        }
        super.clicked(slotId, buttonId, clickType, player);
        syncViewingDisplay(player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.getServer() != null
                && SafeBoxViewingCoordinator.hasMenuAuthority(serverPlayer.getServer(),
                player.getUUID(), accountId, rowPos, doorIndex)) {
            return true;
        }
        return player != null
                && rowPos != null
                && player.level().getBlockState(rowPos).is(ModBlocks.SAFETY_DEPOSIT_BOX_ROW.get())
                && player.distanceToSqr(rowPos.getX() + 0.5D, rowPos.getY() + 0.5D, rowPos.getZ() + 0.5D) <= 64.0D
                && mayInteract(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player == null || player.level().isClientSide()) {
            return;
        }
        syncViewingDisplay(player);
        if (persistSafeBoxContents(player)) {
            container.clearContent();
        }
        AccountHolder account = resolveAccount(player);
        if (account != null && player instanceof ServerPlayer serverPlayer) {
            CentralBank centralBank = BankManager.getCentralBank(serverPlayer.getServer());
            SafeAccessLogService.record(centralBank, account.getBankId(), serverPlayer,
                    SafeAccessLogService.CATEGORY_BOX_ACCESS, SafeAccessLogService.OUTCOME_SUCCESS,
                    "BOX_CLOSED", boxNumber, "Safety deposit box inventory closed.",
                    player.level().dimension().location().toString(), rowPos);
        }
        if (player.level().getBlockEntity(rowPos) instanceof SafetyDepositBoxRowBlockEntity row
                && !row.isViewingTransferActive(doorIndex)) {
            row.closeDoor(doorIndex);
        }
    }

    private void addSafetyBoxSlots() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                int slot = row * COLUMNS + col;
                if (slot >= slotCount) {
                    continue;
                }
                this.addSlot(new Slot(container, slot, 8 + col * 18, 18 + row * 18));
            }
        }
    }

    private void addPlayerInventorySlots(Inventory playerInventory) {
        int inventoryStartY = 32 + rows * 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(
                        playerInventory,
                        col + row * 9 + 9,
                        PLAYER_INV_X + col * 18,
                        inventoryStartY + row * 18
                ));
            }
        }

        int hotbarY = inventoryStartY + 58;
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, PLAYER_INV_X + col * 18, hotbarY));
        }
    }

    private void loadSafeBoxContents(Player player) {
        AccountHolder account = resolveAccount(player);
        if (account == null) {
            return;
        }
        account.getSafeBoxSlots().forEach((slot, stackTag) -> {
            if (slot == null || slot < 0 || slot >= slotCount || stackTag == null) {
                return;
            }
            container.setItem(slot, ItemStackDataCompat.parseStack(stackTag, player.level().registryAccess()));
        });
    }

    private boolean persistSafeBoxContents(Player player) {
        AccountHolder account = resolveAccount(player);
        if (account == null) {
            return false;
        }
        for (int slot = 0; slot < slotCount; slot++) {
            account.getSafeBoxSlots().remove(slot);
        }
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            CompoundTag stackTag = ItemStackDataCompat.saveStack(stack, player.level().registryAccess());
            if (!stackTag.isEmpty()) {
                account.getSafeBoxSlots().put(slot, stackTag);
            }
        }
        BankManager.markDirty();
        return true;
    }

    private void syncViewingDisplay(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.getServer() == null) {
            return;
        }
        List<ItemStack> current = displayContentsSnapshot();
        if (sameDisplayContents(lastDisplayContents, current)) {
            return;
        }
        lastDisplayContents = current;
        SafeBoxViewingCoordinator.updateDisplayContents(
                serverPlayer.getServer(), player.getUUID(), accountId, rowPos, doorIndex, current);
    }

    private List<ItemStack> displayContentsSnapshot() {
        List<ItemStack> contents = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            ItemStack stack = container.getItem(slot);
            contents.add(stack == null || stack.isEmpty()
                    ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return List.copyOf(contents);
    }

    private static boolean sameDisplayContents(List<ItemStack> left, List<ItemStack> right) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            ItemStack a = left.get(i);
            ItemStack b = right.get(i);
            if (a.isEmpty() != b.isEmpty()) {
                return false;
            }
            if (!a.isEmpty() && !ItemStack.isSameItemSameComponents(a, b)) {
                return false;
            }
        }
        return true;
    }

    private AccountHolder resolveAccount(Player player) {
        if (player == null || player.getServer() == null || accountId == null) {
            return null;
        }
        CentralBank centralBank = BankManager.getCentralBank(player.getServer());
        return centralBank == null ? null : centralBank.SearchForAccountByAccountId(accountId);
    }

    private boolean mayInteract(Player player) {
        if (player == null) {
            return false;
        }
        if (player.level().isClientSide()) {
            return true;
        }
        return player instanceof ServerPlayer serverPlayer
                && SafetyDepositBoxService.hasOpenBoxAuthority(serverPlayer, accountId, rowPos, doorIndex);
    }

    private static void closeAfterAuthorityLoss(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.closeContainer();
        }
    }
}
