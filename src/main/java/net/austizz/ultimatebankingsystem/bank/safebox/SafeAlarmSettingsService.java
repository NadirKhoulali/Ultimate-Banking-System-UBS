package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeDepositSetupSnapshot;
import net.austizz.ultimatebankingsystem.block.entity.custom.RfidScannerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bank-specific alarm audio policy. Custom IDs are supplied by client resource packs. */
public final class SafeAlarmSettingsService {
    public static final String DEFAULT_SOUND_EVENT = "minecraft:block.note_block.bell";
    public static final float DEFAULT_VOLUME = 2.0F;
    public static final float DEFAULT_PRIMARY_PITCH = 0.55F;
    public static final float DEFAULT_SECONDARY_PITCH = 0.8F;
    public static final int DEFAULT_INTERVAL_TICKS = 40;

    private static final String ALARM_TAG = "safeAlarmSettings";
    private static final Map<UUID, ResourceLocation> ACTIVE_PREVIEWS = new ConcurrentHashMap<>();

    private SafeAlarmSettingsService() {
    }

    public record Settings(boolean enabled,
                           String soundEventId,
                           float volume,
                           float primaryPitch,
                           float secondaryPitch,
                           int intervalTicks) {
        public Settings {
            ResourceLocation sound = ResourceLocation.tryParse(soundEventId == null ? "" : soundEventId.trim());
            soundEventId = sound == null ? DEFAULT_SOUND_EVENT : sound.toString();
            volume = clamp(volume, 0.1F, 4.0F);
            primaryPitch = clamp(primaryPitch, 0.05F, 2.0F);
            secondaryPitch = secondaryPitch <= 0.0F ? 0.0F : clamp(secondaryPitch, 0.05F, 2.0F);
            intervalTicks = Math.max(5, Math.min(1200, intervalTicks));
        }
    }

    public static Settings defaults() {
        return new Settings(true, DEFAULT_SOUND_EVENT, DEFAULT_VOLUME,
                DEFAULT_PRIMARY_PITCH, DEFAULT_SECONDARY_PITCH, DEFAULT_INTERVAL_TICKS);
    }

    public static Settings read(CompoundTag metadata) {
        if (metadata == null || !metadata.contains(ALARM_TAG, Tag.TAG_COMPOUND)) {
            return defaults();
        }
        CompoundTag tag = metadata.getCompound(ALARM_TAG);
        Settings defaults = defaults();
        return new Settings(
                !tag.contains("enabled") || tag.getBoolean("enabled"),
                tag.contains("sound", Tag.TAG_STRING) ? tag.getString("sound") : defaults.soundEventId(),
                tag.contains("volume", Tag.TAG_FLOAT) ? tag.getFloat("volume") : defaults.volume(),
                tag.contains("primaryPitch", Tag.TAG_FLOAT) ? tag.getFloat("primaryPitch") : defaults.primaryPitch(),
                tag.contains("secondaryPitch", Tag.TAG_FLOAT) ? tag.getFloat("secondaryPitch") : defaults.secondaryPitch(),
                tag.contains("intervalTicks", Tag.TAG_INT) ? tag.getInt("intervalTicks") : defaults.intervalTicks()
        );
    }

    public static void save(CentralBank centralBank, UUID bankId, Settings settings) {
        if (centralBank == null || bankId == null || settings == null) return;
        Settings normalized = new Settings(settings.enabled(), settings.soundEventId(), settings.volume(),
                settings.primaryPitch(), settings.secondaryPitch(), settings.intervalTicks());
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("enabled", normalized.enabled());
        tag.putString("sound", normalized.soundEventId());
        tag.putFloat("volume", normalized.volume());
        tag.putFloat("primaryPitch", normalized.primaryPitch());
        tag.putFloat("secondaryPitch", normalized.secondaryPitch());
        tag.putInt("intervalTicks", normalized.intervalTicks());
        metadata.put(ALARM_TAG, tag);
        centralBank.putBankMetadata(bankId, metadata);
    }

    public static void reset(CentralBank centralBank, UUID bankId) {
        if (centralBank == null || bankId == null) return;
        CompoundTag metadata = centralBank.getOrCreateBankMetadata(bankId);
        metadata.remove(ALARM_TAG);
        centralBank.putBankMetadata(bankId, metadata);
    }

    public static void play(ServerLevel level, double x, double y, double z, Settings settings) {
        if (level == null || settings == null || !settings.enabled()) return;
        SoundEvent sound = resolveSound(settings.soundEventId());
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS,
                settings.volume(), settings.primaryPitch());
        if (settings.secondaryPitch() > 0.0F) {
            level.playSound(null, x, y, z, sound, SoundSource.BLOCKS,
                    settings.volume() * 0.8F, settings.secondaryPitch());
        }
    }

    public static void playPreview(ServerPlayer player, Settings settings) {
        if (player == null || settings == null || !settings.enabled()) return;
        stopPreview(player);
        ResourceLocation soundId = ResourceLocation.tryParse(settings.soundEventId());
        if (soundId == null) {
            soundId = ResourceLocation.parse(DEFAULT_SOUND_EVENT);
        }
        SoundEvent sound = resolveSound(soundId.toString());
        ACTIVE_PREVIEWS.put(player.getUUID(), soundId);
        player.playNotifySound(sound, SoundSource.BLOCKS, settings.volume(), settings.primaryPitch());
        if (settings.secondaryPitch() > 0.0F) {
            player.playNotifySound(sound, SoundSource.BLOCKS,
                    settings.volume() * 0.8F, settings.secondaryPitch());
        }
    }

    public static boolean stopPreview(ServerPlayer player) {
        if (player == null) return false;
        ResourceLocation soundId = ACTIVE_PREVIEWS.remove(player.getUUID());
        if (soundId == null) return false;
        player.connection.send(new ClientboundStopSoundPacket(soundId, SoundSource.BLOCKS));
        return true;
    }

    public static int countLoadedLinkedScanners(MinecraftServer server,
                                                SafeDepositSetupSnapshot setup,
                                                UUID bankId,
                                                String bankName) {
        if (server == null || setup == null || bankId == null) return 0;
        Set<String> counted = new HashSet<>();
        for (var premise : setup.premises()) {
            SafeBlockBounds bounds = premise == null ? null : premise.bounds();
            ServerLevel level = VaultStorageSnapshotService.level(server,
                    bounds == null ? "" : bounds.dimension());
            if (bounds == null || level == null) continue;
            for (int chunkX = bounds.minX() >> 4; chunkX <= bounds.maxX() >> 4; chunkX++) {
                for (int chunkZ = bounds.minZ() >> 4; chunkZ <= bounds.maxZ() >> 4; chunkZ++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    if (chunk == null) continue;
                    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                        BlockPos pos = blockEntity.getBlockPos();
                        if (!(blockEntity instanceof RfidScannerBlockEntity scanner)
                                || !bounds.contains(pos.getX(), pos.getY(), pos.getZ())
                                || !scanner.isAlarmLinkedToBank(bankId, bankName)) {
                            continue;
                        }
                        counted.add(bounds.dimension() + ":" + pos.asLong());
                    }
                }
            }
        }
        return counted.size();
    }

    private static SoundEvent resolveSound(String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId == null ? "" : rawId.trim());
        if (id == null) id = ResourceLocation.parse(DEFAULT_SOUND_EVENT);
        ResourceLocation resolvedId = id;
        return BuiltInRegistries.SOUND_EVENT.getOptional(id)
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(resolvedId));
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
