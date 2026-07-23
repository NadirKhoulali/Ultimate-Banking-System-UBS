package net.austizz.ultimatebankingsystem.bank.owner.staffing;

import net.austizz.ultimatebankingsystem.test.NeoForgeTestClassLoader;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CentralBankOperatorStaffingTest {
    @Test
    void operatorSatisfiesOnlyCentralBankStaffingPolicy() throws Exception {
        Class<?> service = Class.forName(
                "net.austizz.ultimatebankingsystem.bank.owner.staffing.BankStaffingService",
                true,
                NeoForgeTestClassLoader.get());
        Method policy = service.getDeclaredMethod("centralBankOperatorEligible", UUID.class, boolean.class);
        policy.setAccessible(true);

        assertTrue((boolean) policy.invoke(null, new UUID(0L, 0L), true));
        assertFalse((boolean) policy.invoke(null, UUID.randomUUID(), true));
        assertFalse((boolean) policy.invoke(null, new UUID(0L, 0L), false));
    }
}
