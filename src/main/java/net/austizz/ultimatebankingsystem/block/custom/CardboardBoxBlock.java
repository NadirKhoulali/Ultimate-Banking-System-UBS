package net.austizz.ultimatebankingsystem.block.custom;

import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.entity.custom.CardboardBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

public class CardboardBoxBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 0.0D, 14.0D, 12.0D, 16.0D);

    public CardboardBoxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CardboardBoxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level,
                                                                             BlockState state,
                                                                             BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Override
    public void setPlacedBy(Level level,
                            BlockPos pos,
                            BlockState state,
                            @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (level.isClientSide()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof CardboardBoxBlockEntity boxEntity) {
            boxEntity.loadFromItem(stack);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack,
                                         BlockState state,
                                 Level level,
                                 BlockPos pos,
                                 Player player,
                                 InteractionHand hand,
                                 BlockHitResult hit) {
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CardboardBoxBlockEntity boxEntity)) {
            return ItemInteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(boxEntity, pos);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() == newState.getBlock()) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof CardboardBoxBlockEntity boxEntity) {
                ItemStack dropped = new ItemStack(ModBlocks.CARDBOARD_BOX.get().asItem());
                boxEntity.toItemStack(dropped);
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.35D, pos.getZ() + 0.5D, dropped);
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }
}
