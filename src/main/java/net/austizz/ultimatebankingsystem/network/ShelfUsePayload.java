package net.austizz.ultimatebankingsystem.network;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

public record ShelfUsePayload(
        String dimensionId,
        int x,
        int y,
        int z,
        double hitX,
        double hitY,
        double hitZ,
        boolean configureAction,
        boolean mainHand
) implements CustomPacketPayload {

    public static final Type<ShelfUsePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "shelf_use"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Double> DOUBLE_CODEC =
            StreamCodec.of(RegistryFriendlyByteBuf::writeDouble, RegistryFriendlyByteBuf::readDouble);

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelfUsePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.dimensionId());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.x());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.y());
                        ByteBufCodecs.VAR_INT.encode(buf, payload.z());
                        DOUBLE_CODEC.encode(buf, payload.hitX());
                        DOUBLE_CODEC.encode(buf, payload.hitY());
                        DOUBLE_CODEC.encode(buf, payload.hitZ());
                        ByteBufCodecs.BOOL.encode(buf, payload.configureAction());
                        ByteBufCodecs.BOOL.encode(buf, payload.mainHand());
                    },
                    buf -> new ShelfUsePayload(
                            ByteBufCodecs.STRING_UTF8.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            ByteBufCodecs.VAR_INT.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            DOUBLE_CODEC.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf),
                            ByteBufCodecs.BOOL.decode(buf)
                    )
            );

    public static void sendToServer(Level level,
                                    BlockPos targetPos,
                                    BlockHitResult hit,
                                    Player player,
                                    boolean mainHand) {
        if (level == null || targetPos == null || hit == null || player == null || !level.isClientSide()) {
            return;
        }
        PacketDistributor.sendToServer(new ShelfUsePayload(
                level.dimension().location().toString(),
                targetPos.getX(),
                targetPos.getY(),
                targetPos.getZ(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                player.isShiftKeyDown(),
                mainHand
        ));
    }

    public static void sendToServer(Level level,
                                    BlockPos targetPos,
                                    BlockHitResult hit,
                                    Player player,
                                    InteractionHand hand) {
        sendToServer(level, targetPos, hit, player, hand == InteractionHand.MAIN_HAND);
    }

    @Override
    public Type<ShelfUsePayload> type() {
        return TYPE;
    }
}
