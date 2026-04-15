package net.austizz.ultimatebankingsystem.compat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 1.21 compatibility shim for projects that still reference RegistryFriendlyByteBuf.
 * On 1.20.1 we can safely wrap a plain FriendlyByteBuf.
 */
public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(ByteBuf source) {
        super(source);
    }
}
