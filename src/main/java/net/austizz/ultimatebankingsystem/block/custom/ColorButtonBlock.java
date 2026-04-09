package net.austizz.ultimatebankingsystem.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ColorButtonBlock extends Block {

    public static final EnumProperty<ButtonColor> COLOR = EnumProperty.create("color", ButtonColor.class);

    public ColorButtonBlock(Properties properties) {
        super(properties.sound(SoundType.STONE));
        this.registerDefaultState(this.stateDefinition.any().setValue(COLOR, ButtonColor.WHITE));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hitResult) {
        if (hitResult.getDirection() != Direction.UP) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        double localX = hitResult.getLocation().x - pos.getX();
        double localZ = hitResult.getLocation().z - pos.getZ();
        ButtonColor clickedColor = resolveButtonColor(localX, localZ);

        if (clickedColor == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide() && state.getValue(COLOR) != clickedColor) {
            level.setBlock(pos, state.setValue(COLOR, clickedColor), Block.UPDATE_ALL);
        }

        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    private static ButtonColor resolveButtonColor(double localX, double localZ) {
        if (isInside(localX, localZ, 2, 7, 2, 7)) {
            return ButtonColor.RED;
        }
        if (isInside(localX, localZ, 9, 14, 2, 7)) {
            return ButtonColor.GREEN;
        }
        if (isInside(localX, localZ, 2, 7, 9, 14)) {
            return ButtonColor.BLUE;
        }
        if (isInside(localX, localZ, 9, 14, 9, 14)) {
            return ButtonColor.YELLOW;
        }
        return null;
    }

    private static boolean isInside(double localX, double localZ, int minX, int maxX, int minZ, int maxZ) {
        double px = localX * 16.0;
        double pz = localZ * 16.0;
        return px >= minX && px < maxX && pz >= minZ && pz < maxZ;
    }

    public enum ButtonColor implements StringRepresentable {
        WHITE("white"),
        RED("red"),
        GREEN("green"),
        BLUE("blue"),
        YELLOW("yellow");

        private final String name;

        ButtonColor(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
