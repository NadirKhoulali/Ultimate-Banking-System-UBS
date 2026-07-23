package net.austizz.ultimatebankingsystem.bank.owner;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

final class OwnerPcGameTestSerialization {
    private OwnerPcGameTestSerialization() {
    }

    static byte[] save(CentralBank centralBank, MinecraftServer server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            NbtIo.write(centralBank.save(new CompoundTag(), server.registryAccess()), output);
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize Owner-PC GameTest state", exception);
        }
    }
}
