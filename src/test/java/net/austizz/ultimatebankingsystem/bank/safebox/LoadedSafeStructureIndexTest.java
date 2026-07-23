package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeBlockBounds;
import net.austizz.ultimatebankingsystem.bank.safebox.setup.SafeVaultDoorSelection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadedSafeStructureIndexTest {
    @AfterEach
    void clearIndex() {
        LoadedSafeStructureIndex.clearAll();
    }

    @Test
    void indexedQueriesScaleWithRegisteredStructuresInsideBoundsNotEmptyVolume() {
        Object serverKey = new Object();
        LoadedSafeStructureIndex.Position firstRow =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", -250_000, 70, -250_000);
        LoadedSafeStructureIndex.Position secondRow =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 250_000, 70, 250_000);
        LoadedSafeStructureIndex.Position outside =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 250_001, 70, 250_001);
        LoadedSafeStructureIndex.register(serverKey, firstRow.dimension(), firstRow.x(), firstRow.y(), firstRow.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        LoadedSafeStructureIndex.register(serverKey, secondRow.dimension(), secondRow.x(), secondRow.y(), secondRow.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        LoadedSafeStructureIndex.register(serverKey, outside.dimension(), outside.x(), outside.y(), outside.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        AtomicInteger validatorCalls = new AtomicInteger();

        List<LoadedSafeStructureIndex.Entry> matches = LoadedSafeStructureIndex.findInBounds(
                serverKey,
                LoadedSafeStructureIndex.Kind.ROW,
                List.of(new SafeBlockBounds("minecraft:overworld", -250_000, 0, -250_000,
                        250_000, 255, 250_000)),
                entry -> {
                    validatorCalls.incrementAndGet();
                    return true;
                }
        );

        assertEquals(List.of(firstRow, secondRow), matches.stream().map(LoadedSafeStructureIndex.Entry::position).toList());
        assertEquals(2, validatorCalls.get());
    }

    @Test
    void unregisterAndStaleValidationRemoveEntriesFromReadinessQueries() {
        Object serverKey = new Object();
        LoadedSafeStructureIndex.Position live =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 1, 64, 1);
        LoadedSafeStructureIndex.Position unregistered =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 2, 64, 2);
        LoadedSafeStructureIndex.Position stale =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 3, 64, 3);
        LoadedSafeStructureIndex.register(serverKey, live.dimension(), live.x(), live.y(), live.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        LoadedSafeStructureIndex.register(serverKey, unregistered.dimension(), unregistered.x(), unregistered.y(), unregistered.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        LoadedSafeStructureIndex.register(serverKey, stale.dimension(), stale.x(), stale.y(), stale.z(),
                LoadedSafeStructureIndex.Kind.ROW);
        LoadedSafeStructureIndex.unregister(serverKey, unregistered.dimension(),
                unregistered.x(), unregistered.y(), unregistered.z(),
                LoadedSafeStructureIndex.Kind.ROW);

        AtomicInteger firstPassCalls = new AtomicInteger();
        List<LoadedSafeStructureIndex.Entry> firstPass = LoadedSafeStructureIndex.findInBounds(
                serverKey,
                LoadedSafeStructureIndex.Kind.ROW,
                List.of(new SafeBlockBounds("minecraft:overworld", 0, 0, 0, 4, 255, 4)),
                entry -> {
                    firstPassCalls.incrementAndGet();
                    return !entry.position().equals(stale);
                }
        );

        assertEquals(List.of(live), firstPass.stream().map(LoadedSafeStructureIndex.Entry::position).toList());
        assertEquals(2, firstPassCalls.get(), "unregistered rows must not be revalidated");

        AtomicInteger secondPassCalls = new AtomicInteger();
        List<LoadedSafeStructureIndex.Entry> secondPass = LoadedSafeStructureIndex.findInBounds(
                serverKey,
                LoadedSafeStructureIndex.Kind.ROW,
                List.of(new SafeBlockBounds("minecraft:overworld", 0, 0, 0, 4, 255, 4)),
                entry -> {
                    secondPassCalls.incrementAndGet();
                    return true;
                }
        );

        assertEquals(List.of(live), secondPass.stream().map(LoadedSafeStructureIndex.Entry::position).toList());
        assertEquals(1, secondPassCalls.get(), "stale rows must be pruned after failed validation");
    }

    @Test
    void contextMismatchDoesNotPruneAValidDoorNeededByAnotherSafeArea() {
        Object serverKey = new Object();
        LoadedSafeStructureIndex.Position firstDoor =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 10, 64, 10);
        LoadedSafeStructureIndex.Position secondDoor =
                new LoadedSafeStructureIndex.Position("minecraft:overworld", 30, 64, 30);
        LoadedSafeStructureIndex.register(serverKey, firstDoor.dimension(),
                firstDoor.x(), firstDoor.y(), firstDoor.z(),
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
        LoadedSafeStructureIndex.register(serverKey, secondDoor.dimension(),
                secondDoor.x(), secondDoor.y(), secondDoor.z(),
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER);
        List<SafeBlockBounds> premise = List.of(new SafeBlockBounds(
                "minecraft:overworld", 0, 50, 0, 40, 80, 40));

        List<LoadedSafeStructureIndex.Entry> firstArea = LoadedSafeStructureIndex.findInBounds(
                serverKey,
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER,
                premise,
                entry -> true,
                entry -> entry.position().equals(firstDoor));
        List<LoadedSafeStructureIndex.Entry> secondArea = LoadedSafeStructureIndex.findInBounds(
                serverKey,
                LoadedSafeStructureIndex.Kind.VAULT_DOOR_MASTER,
                premise,
                entry -> true,
                entry -> entry.position().equals(secondDoor));

        assertEquals(List.of(firstDoor), firstArea.stream()
                .map(LoadedSafeStructureIndex.Entry::position).toList());
        assertEquals(List.of(secondDoor), secondArea.stream()
                .map(LoadedSafeStructureIndex.Entry::position).toList(),
                "a door rejected only by the first safe-area matcher must remain indexed");
    }

    @Test
    void automaticDoorSelectionRejectsAmbiguousDoorsUnlessPersistedAnchorSelectsOne() {
        String firstDoor = "minecraft:overworld|10|64|10";
        String secondDoor = "minecraft:overworld|20|64|20";
        Set<String> twoCompleteDoors = new LinkedHashSet<>(List.of(firstDoor, secondDoor));

        assertTrue(SafeVaultDoorSelection.select(Optional.empty(), twoCompleteDoors).isEmpty(),
                "automatic discovery must reject multiple complete contained doors");
        assertEquals(Optional.of(secondDoor),
                SafeVaultDoorSelection.select(Optional.of(secondDoor), twoCompleteDoors));
        assertTrue(SafeVaultDoorSelection.select(Optional.of("minecraft:overworld|30|64|30"), twoCompleteDoors).isEmpty(),
                "an invalid persisted anchor must not disambiguate multiple doors");
        assertEquals(Optional.of(firstDoor),
                SafeVaultDoorSelection.select(Optional.empty(), Set.of(firstDoor)));
    }
}
