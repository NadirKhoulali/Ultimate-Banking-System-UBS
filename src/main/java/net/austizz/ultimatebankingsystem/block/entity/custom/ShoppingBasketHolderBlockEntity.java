package net.austizz.ultimatebankingsystem.block.entity.custom;

import net.austizz.ultimatebankingsystem.block.entity.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class ShoppingBasketHolderBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {
    private UUID ownerId;
    private UUID shopId;
    private UUID holderId;
    private String ownerName = "";

    public ShoppingBasketHolderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHOPPING_BASKET_HOLDER.get(), pos, state);
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getShopId() {
        return shopId;
    }

    public UUID getHolderId() {
        return holderId;
    }

    public String getOwnerName() {
        return ownerName == null ? "" : ownerName;
    }

    public void setOwnerAndShop(UUID ownerId, String ownerName, UUID shopId) {
        this.ownerId = ownerId;
        this.shopId = shopId;
        this.ownerName = ownerName == null ? "" : ownerName.trim();
        if (this.holderId == null) {
            this.holderId = UUID.randomUUID();
        }
        markUpdated();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerId != null) {
            tag.putUUID("owner_id", ownerId);
        }
        if (shopId != null) {
            tag.putUUID("shop_id", shopId);
        }
        if (holderId != null) {
            tag.putUUID("holder_id", holderId);
        }
        if (ownerName != null && !ownerName.isBlank()) {
            tag.putString("owner_name", ownerName);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ownerId = tag.hasUUID("owner_id") ? tag.getUUID("owner_id") : null;
        shopId = tag.hasUUID("shop_id") ? tag.getUUID("shop_id") : null;
        holderId = tag.hasUUID("holder_id") ? tag.getUUID("holder_id") : null;
        ownerName = tag.contains("owner_name") ? tag.getString("owner_name") : "";
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markUpdated() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }
}
