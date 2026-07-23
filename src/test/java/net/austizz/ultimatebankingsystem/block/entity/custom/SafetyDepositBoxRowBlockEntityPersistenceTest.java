package net.austizz.ultimatebankingsystem.block.entity.custom;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafetyDepositBoxRowBlockEntityPersistenceTest {
    @Test
    void threeLoadSaveGenerationsPreserveEveryHiddenField() throws Exception {
        Object expected = SafetyDepositBoxRowPersistenceFixture.sevenDoorTag();
        Object generation = expected;

        for (int number = 1; number <= 3; number++) {
            SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
            row.load(generation);
            generation = row.save();

            assertHiddenFieldsEqual(expected, generation, "generation " + number);
        }
    }

    @Test
    void loadingTagWithoutHiddenFieldsClearsPriorHiddenState() throws Exception {
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
        row.load(SafetyDepositBoxRowPersistenceFixture.sevenDoorTag());
        Object activeOnly = SafetyDepositBoxRowPersistenceFixture.activeOnlyTag();

        row.load(activeOnly);
        Object saved = row.save();

        assertActiveFieldsEqual(activeOnly, saved);
        for (int index = 4; index < 7; index++) {
            for (String field : SafetyDepositBoxRowPersistenceFixture.PERSISTED_FIELDS) {
                assertFalse(SafetyDepositBoxRowPersistenceFixture.contains(saved, field + index),
                        field + index + " retained stale hidden state");
            }
        }
    }

    @Test
    void activeIndexZeroMutationsPreserveHiddenFields() throws Exception {
        Object original = SafetyDepositBoxRowPersistenceFixture.sevenDoorTag();
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
        row.load(original);
        UUID replacementAccount = UUID.fromString("90000000-0000-0000-0000-000000000001");

        row.assignDoor(0, replacementAccount, " active-box ");
        row.openDoor(0, 9_999L);
        Object saved = row.save();

        assertHiddenFieldsEqual(original, saved, "active mutation");
        assertEquals(replacementAccount, SafetyDepositBoxRowPersistenceFixture.getUuid(saved, "account_0"));
        assertEquals("active-box", SafetyDepositBoxRowPersistenceFixture.getString(saved, "box_number_0"));
        assertEquals(9_999L, SafetyDepositBoxRowPersistenceFixture.getLong(saved, "open_until_0"));
    }

    @Test
    void hiddenIndexAccessorsReturnDefaultsWithoutChangingState() throws Exception {
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
        row.load(SafetyDepositBoxRowPersistenceFixture.sevenDoorTag());
        Object before = row.save();
        SafetyDepositBoxRowPersistenceFixture.RuntimeDoorState activeBefore = row.runtimeState(0);
        SafetyDepositBoxRowPersistenceFixture.RuntimeDoorState expected =
                new SafetyDepositBoxRowPersistenceFixture.RuntimeDoorState(
                        "EMPTY", -1, false, false, null, "", 0.0F, 0.0F
                );

        for (int index = 4; index < 7; index++) {
            assertEquals(expected, row.runtimeState(index), "runtime state leaked at index " + index);
        }

        assertEquals(4, row.moduleSnapshotLength());
        assertPersistedFieldsEqual(before, row.save(), "hidden accessor calls");
        assertEquals(activeBefore, row.runtimeState(0), "hidden accessors changed active runtime state");
    }

    @Test
    void hiddenIndexMutatorsCannotChangeActiveOrHiddenState() throws Exception {
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
        row.load(SafetyDepositBoxRowPersistenceFixture.sevenDoorTag());
        Object expected = row.save();
        SafetyDepositBoxRowPersistenceFixture.RuntimeDoorState activeExpected = row.runtimeState(0);
        UUID replacement = UUID.fromString("90000000-0000-0000-0000-000000000002");

        for (int index = 4; index < 7; index++) {
            row.assignDoor(index, replacement, "forbidden");
            assertUnchanged(expected, activeExpected, row, "assignDoor(" + index + ")");
            row.clearDoorAssignment(index);
            assertUnchanged(expected, activeExpected, row, "clearDoorAssignment(" + index + ")");
            row.openDoor(index, 99_999L);
            assertUnchanged(expected, activeExpected, row, "openDoor(" + index + ")");
            row.closeDoor(index);
            assertUnchanged(expected, activeExpected, row, "closeDoor(" + index + ")");
            assertFalse(row.installSmallModule(index), "installModule accepted hidden index " + index);
            assertUnchanged(expected, activeExpected, row, "installModule(" + index + ")");
        }
    }

    @Test
    void nestedTagCopiesDoNotAliasInputOrSavedOutput() throws Exception {
        Object input = SafetyDepositBoxRowPersistenceFixture.newCompoundTag();
        Object nestedInput = SafetyDepositBoxRowPersistenceFixture.newCompoundTag();
        SafetyDepositBoxRowPersistenceFixture.putString(nestedInput, "value", "original");
        SafetyDepositBoxRowPersistenceFixture.putTag(input, "module_4", nestedInput);
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();

        row.load(input);
        SafetyDepositBoxRowPersistenceFixture.putString(nestedInput, "value", "input-mutated");
        Object firstSaved = row.save();
        Object nestedOutput = SafetyDepositBoxRowPersistenceFixture.getCompound(firstSaved, "module_4");
        assertEquals("original", SafetyDepositBoxRowPersistenceFixture.getString(nestedOutput, "value"));

        SafetyDepositBoxRowPersistenceFixture.putString(nestedOutput, "value", "output-mutated");
        Object secondSaved = row.save();
        Object secondNestedOutput = SafetyDepositBoxRowPersistenceFixture.getCompound(secondSaved, "module_4");
        assertEquals("original", SafetyDepositBoxRowPersistenceFixture.getString(secondNestedOutput, "value"));
    }

    @Test
    void viewingTransferIsExclusivePerBoxAndSurvivesReload() throws Exception {
        SafetyDepositBoxRowPersistenceFixture row = SafetyDepositBoxRowPersistenceFixture.create();
        row.load(SafetyDepositBoxRowPersistenceFixture.activeOnlyTag());
        UUID session = UUID.fromString("90000000-0000-0000-0000-000000000010");
        UUID other = UUID.fromString("90000000-0000-0000-0000-000000000011");
        UUID replacement = UUID.fromString("90000000-0000-0000-0000-000000000012");
        UUID assigned = SafetyDepositBoxRowPersistenceFixture.getUuid(
                SafetyDepositBoxRowPersistenceFixture.activeOnlyTag(), "account_0");

        assertTrue(row.beginViewingTransfer(0, session));
        assertFalse(row.beginViewingTransfer(0, other));
        row.clearDoorAssignment(0);
        row.assignDoor(0, replacement, "replacement");
        row.closeDoor(0);
        Object leased = row.save();
        assertEquals(assigned, SafetyDepositBoxRowPersistenceFixture.getUuid(leased, "account_0"));
        assertEquals(session, SafetyDepositBoxRowPersistenceFixture.getUuid(leased, "viewing_transfer_0"));
        assertEquals(Long.MAX_VALUE, SafetyDepositBoxRowPersistenceFixture.getLong(leased, "open_until_0"));
        assertTrue(row.hasAnyViewingTransfer());

        SafetyDepositBoxRowPersistenceFixture restored = SafetyDepositBoxRowPersistenceFixture.create();
        restored.load(leased);
        assertTrue(restored.hasAnyViewingTransfer());
        assertFalse(restored.endViewingTransfer(0, other));
        assertTrue(restored.endViewingTransfer(0, session));
        assertFalse(restored.hasAnyViewingTransfer());
    }

    private static void assertHiddenFieldsEqual(Object expected, Object actual, String scenario) throws Exception {
        assertFieldsEqual(expected, actual, 4, 7, scenario);
    }

    private static void assertActiveFieldsEqual(Object expected, Object actual) throws Exception {
        assertFieldsEqual(expected, actual, 0, 4, "active reload");
    }

    private static void assertPersistedFieldsEqual(Object expected, Object actual, String scenario) throws Exception {
        assertFieldsEqual(expected, actual, 0, 7, scenario);
    }

    private static void assertUnchanged(Object expected,
                                        SafetyDepositBoxRowPersistenceFixture.RuntimeDoorState activeExpected,
                                        SafetyDepositBoxRowPersistenceFixture row,
                                        String scenario) throws Exception {
        assertPersistedFieldsEqual(expected, row.save(), scenario);
        assertEquals(activeExpected, row.runtimeState(0), scenario + " changed active runtime state");
    }

    private static void assertFieldsEqual(Object expected,
                                          Object actual,
                                          int firstIndex,
                                          int endIndex,
                                          String scenario) throws Exception {
        for (int index = firstIndex; index < endIndex; index++) {
            for (String field : SafetyDepositBoxRowPersistenceFixture.PERSISTED_FIELDS) {
                String key = field + index;
                assertEquals(
                        SafetyDepositBoxRowPersistenceFixture.getTag(expected, key),
                        SafetyDepositBoxRowPersistenceFixture.getTag(actual, key),
                        scenario + " changed " + key
                );
            }
        }
    }
}
