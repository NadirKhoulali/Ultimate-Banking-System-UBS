package net.austizz.ultimatebankingsystem.migration.numismatics;

import net.austizz.ultimatebankingsystem.bank.centralbank.CentralBank;
import net.austizz.ultimatebankingsystem.bank.handler.BankManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class NumismaticsLiveMigration {
    private NumismaticsLiveMigration() {
    }

    public static Result migrateOnlinePlayers(MinecraftServer server,
                                              NumismaticsMigrationSavedData journal) {
        MutableResult total = new MutableResult();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            total.add(migrateSlots(server, journal, new ContainerSlots(player.getInventory()),
                    "player/" + player.getUUID() + "/inventory"));
            total.add(migrateSlots(server, journal, new ContainerSlots(player.getEnderChestInventory()),
                    "player/" + player.getUUID() + "/ender_chest"));
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            String playerFile = "playerdata/" + player.getUUID() + ".dat";
            if (journal.preflight().candidatePlayerFiles().contains(playerFile)) {
                journal.markPlayerFileComplete(playerFile);
            }
        }
        return total.freeze();
    }

    public static Result migratePlayerFile(MinecraftServer server,
                                           NumismaticsMigrationSavedData journal,
                                           String relativePath) throws IOException {
        Path worldRoot = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path file = worldRoot.resolve(relativePath).normalize();
        if (!file.startsWith(worldRoot) || !Files.isRegularFile(file)) {
            return Result.withUnresolved("Player file is missing or outside the world: " + relativePath);
        }
        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(
                Math.max(64L * 1024L * 1024L, Files.size(file) * 64L)));
        CentralBank centralBank = requireCentralBank(server);
        NumismaticsNbtMigrator.Result migrated = NumismaticsNbtMigrator.migrate(root,
                new NumismaticsNbtMigrator.Context(
                        journal.migrationId(), journal.options().centsPerSpur(),
                        journal.options().convertBankCards(), centralBank, journal.accountMappings(),
                        server.registryAccess(), holderNameFromPath(relativePath)), relativePath);
        if (migrated.changed()) {
            Path temporary = file.resolveSibling(file.getFileName() + ".ubs.tmp");
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return Result.from(migrated);
    }

    public static Result migrateChunk(MinecraftServer server,
                                      NumismaticsMigrationSavedData journal,
                                      NumismaticsPreflightResult.ChunkRef reference) {
        ResourceLocation dimensionId;
        try {
            dimensionId = ResourceLocation.parse(reference.dimension());
        } catch (RuntimeException malformed) {
            return Result.withUnresolved("Invalid dimension in migration index: " + reference.dimension());
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionId));
        if (level == null) return Result.withUnresolved("Dimension is unavailable: " + reference.dimension());
        LevelChunk chunk = level.getChunk(reference.x(), reference.z());
        MutableResult total = new MutableResult();

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            String location = reference.dimension() + "/block/" + blockEntity.getBlockPos().toShortString();
            NumismaticsInventoryMigrator.Slots slots = mutableSlots(level, blockEntity);
            if (slots != null) {
                total.add(migrateSlots(server, journal, slots, location));
                ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
                if (typeId != null && "numismatics".equals(typeId.getNamespace())) {
                    drainMachineInventory(server, journal, slots, location, total);
                    journal.audit("RECOVERY_MACHINE", typeId + " at " + location);
                }
                blockEntity.setChanged();
            } else {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                        blockEntity.getBlockPos(), null);
                detectUnmodifiableAssets(server, handler, location, total);
            }
        }

        ChunkPos chunkPos = chunk.getPos();
        AABB bounds = new AABB(chunkPos.getMinBlockX(), level.getMinBuildHeight(), chunkPos.getMinBlockZ(),
                chunkPos.getMaxBlockX() + 1.0D, level.getMaxBuildHeight(), chunkPos.getMaxBlockZ() + 1.0D);
        for (Entity entity : level.getEntities((Entity) null, bounds, candidate -> !(candidate instanceof ServerPlayer))) {
            String location = reference.dimension() + "/entity/" + entity.getUUID();
            if (entity instanceof ItemEntity itemEntity) {
                total.add(migrateSlots(server, journal, new SingleItemSlots(itemEntity), location));
                continue;
            }
            if (entity instanceof ItemFrame itemFrame) {
                total.add(migrateSlots(server, journal, new ItemFrameSlots(itemFrame), location));
                continue;
            }
            if (entity instanceof Display.ItemDisplay itemDisplay) {
                total.add(migrateEntityNbt(server, journal, itemDisplay, location));
                continue;
            }
            NumismaticsInventoryMigrator.Slots slots = mutableSlots(entity);
            if (slots != null) total.add(migrateSlots(server, journal, slots, location));
            else if (entity instanceof LivingEntity living) {
                total.add(migrateSlots(server, journal, new LivingEquipmentSlots(living), location + "/equipment"));
            } else detectUnmodifiableAssets(server, entity.getCapability(Capabilities.ItemHandler.ENTITY), location, total);
        }
        chunk.setUnsaved(true);
        return total.freeze();
    }

    private static Result migrateSlots(MinecraftServer server,
                                       NumismaticsMigrationSavedData journal,
                                       NumismaticsInventoryMigrator.Slots slots,
                                       String location) {
        CentralBank centralBank = requireCentralBank(server);
        NumismaticsInventoryMigrator.Result result = NumismaticsInventoryMigrator.migrate(slots,
                new NumismaticsInventoryMigrator.Context(
                        journal.migrationId(), journal.options().centsPerSpur(),
                        journal.options().convertBankCards(), centralBank, journal.accountMappings(),
                        "Migrated Account Holder", stack -> journal.addRecoveryItem(
                        serializedStack(stack, server))), location);
        if (result.changed()) journal.audit("PHYSICAL", location + " cents=" + result.convertedCents());
        return Result.from(result);
    }

    private static Result migrateEntityNbt(MinecraftServer server,
                                           NumismaticsMigrationSavedData journal,
                                           Entity entity,
                                           String location) {
        CompoundTag serialized = entity.saveWithoutId(new CompoundTag());
        NumismaticsNbtMigrator.Result result = NumismaticsNbtMigrator.migrate(serialized,
                new NumismaticsNbtMigrator.Context(
                        journal.migrationId(), journal.options().centsPerSpur(),
                        journal.options().convertBankCards(), requireCentralBank(server),
                        journal.accountMappings(), server.registryAccess(), "Migrated Account Holder"), location);
        if (result.changed()) entity.load(serialized);
        return Result.from(result);
    }

    private static NumismaticsInventoryMigrator.Slots mutableSlots(ServerLevel level, BlockEntity blockEntity) {
        if (blockEntity instanceof Container container) return new ContainerSlots(container);
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, blockEntity.getBlockPos(), null);
        return handler instanceof IItemHandlerModifiable mutable ? new HandlerSlots(mutable) : null;
    }

    private static NumismaticsInventoryMigrator.Slots mutableSlots(Entity entity) {
        if (entity instanceof Container container) return new ContainerSlots(container);
        IItemHandler handler = entity.getCapability(Capabilities.ItemHandler.ENTITY);
        return handler instanceof IItemHandlerModifiable mutable ? new HandlerSlots(mutable) : null;
    }

    private static void detectUnmodifiableAssets(MinecraftServer server, IItemHandler handler,
                                                  String location, MutableResult total) {
        if (handler == null) return;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            NumismaticsWorldScanner.ScanCounts counts = NumismaticsWorldScanner.inspect(
                    stack.saveOptional(server.registryAccess()));
            if (counts.hasNumismaticsAssets()) {
                total.unresolved.add("Inventory cannot be modified safely at " + location + " slot " + slot + ".");
            }
        }
    }

    private static void drainMachineInventory(MinecraftServer server,
                                              NumismaticsMigrationSavedData journal,
                                              NumismaticsInventoryMigrator.Slots slots,
                                              String location,
                                              MutableResult total) {
        for (int slot = 0; slot < slots.size(); slot++) {
            ItemStack stack = slots.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            journal.addRecoveryItem(serializedStack(stack, server));
            slots.set(slot, ItemStack.EMPTY);
            total.recoveryItems++;
        }
        slots.changed();
        total.changed = true;
    }

    private static CentralBank requireCentralBank(MinecraftServer server) {
        CentralBank bank = BankManager.getCentralBank(server);
        if (bank == null) throw new IllegalStateException("Central Bank data is unavailable.");
        return bank;
    }

    private static CompoundTag serializedStack(ItemStack stack, MinecraftServer server) {
        if (stack.saveOptional(server.registryAccess()) instanceof CompoundTag compound) return compound;
        throw new IllegalStateException("Could not serialize a recovery item stack.");
    }

    private static String holderNameFromPath(String path) {
        if (path == null) return "Migrated Account Holder";
        String name = Path.of(path).getFileName().toString();
        int dot = name.indexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        try {
            return UUID.fromString(name).toString();
        } catch (RuntimeException ignored) {
            return "Migrated Account Holder";
        }
    }

    public record Result(boolean changed, long coinItems, long convertedCents,
                         long boundCards, long blankCards, long idCards,
                         long migrationNotes, int recoveryItems, List<String> unresolved) {
        public Result {
            unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
        }

        static Result from(NumismaticsInventoryMigrator.Result value) {
            return new Result(value.changed(), value.coinItems(), value.convertedCents(), value.boundCards(),
                    value.blankCards(), value.idCards(), value.migrationNotes(), 0, value.unresolved());
        }

        static Result from(NumismaticsNbtMigrator.Result value) {
            return new Result(value.changed(), value.coinItems(), value.convertedCents(), value.boundCards(),
                    value.blankCards(), value.idCards(), value.migrationNotes(), 0, value.unresolved());
        }

        static Result withUnresolved(String message) {
            return new Result(false, 0, 0, 0, 0, 0, 0, 0, List.of(message));
        }
    }

    private static final class MutableResult {
        boolean changed;
        long coinItems;
        long convertedCents;
        long boundCards;
        long blankCards;
        long idCards;
        long migrationNotes;
        int recoveryItems;
        final List<String> unresolved = new ArrayList<>();

        void add(Result value) {
            changed |= value.changed();
            coinItems += value.coinItems();
            convertedCents += value.convertedCents();
            boundCards += value.boundCards();
            blankCards += value.blankCards();
            idCards += value.idCards();
            migrationNotes += value.migrationNotes();
            recoveryItems += value.recoveryItems();
            unresolved.addAll(value.unresolved());
        }

        Result freeze() {
            return new Result(changed, coinItems, convertedCents, boundCards, blankCards, idCards,
                    migrationNotes, recoveryItems, unresolved);
        }
    }

    private record ContainerSlots(Container container) implements NumismaticsInventoryMigrator.Slots {
        @Override public int size() { return container.getContainerSize(); }
        @Override public ItemStack get(int slot) { return container.getItem(slot); }
        @Override public void set(int slot, ItemStack stack) { container.setItem(slot, stack); }
        @Override public void changed() { container.setChanged(); }
    }

    private record HandlerSlots(IItemHandlerModifiable handler) implements NumismaticsInventoryMigrator.Slots {
        @Override public int size() { return handler.getSlots(); }
        @Override public ItemStack get(int slot) { return handler.getStackInSlot(slot); }
        @Override public void set(int slot, ItemStack stack) { handler.setStackInSlot(slot, stack); }
    }

    private record SingleItemSlots(ItemEntity entity) implements NumismaticsInventoryMigrator.Slots {
        @Override public int size() { return 1; }
        @Override public ItemStack get(int slot) { return entity.getItem(); }
        @Override public void set(int slot, ItemStack stack) { entity.setItem(stack); }
    }

    private record ItemFrameSlots(ItemFrame entity) implements NumismaticsInventoryMigrator.Slots {
        @Override public int size() { return 1; }
        @Override public ItemStack get(int slot) { return entity.getItem(); }
        @Override public void set(int slot, ItemStack stack) { entity.setItem(stack, true); }
    }

    private static final class LivingEquipmentSlots implements NumismaticsInventoryMigrator.Slots {
        private final LivingEntity entity;
        private final EquipmentSlot[] slots = EquipmentSlot.values();
        private LivingEquipmentSlots(LivingEntity entity) { this.entity = entity; }
        @Override public int size() { return slots.length; }
        @Override public ItemStack get(int slot) { return entity.getItemBySlot(slots[slot]); }
        @Override public void set(int slot, ItemStack stack) { entity.setItemSlot(slots[slot], stack); }
    }
}
