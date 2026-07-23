package net.austizz.ultimatebankingsystem.api.heist;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Adapter for opening and restoring third-party doors during a UBS heist. */
public interface HeistDoorAdapter {
    boolean matches(BlockState state);

    /** Returns true when the adapter handled the requested breached state. */
    boolean setBreached(ServerLevel level, BlockPos pos, BlockState state, boolean breached);
}
