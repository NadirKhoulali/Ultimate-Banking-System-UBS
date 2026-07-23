package net.austizz.ultimatebankingsystem.bank.safebox;

import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolLifecycle;
import net.austizz.ultimatebankingsystem.bank.safebox.claim.SafeClaimToolPurpose;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeClaimToolLifecycleRestorationTest {
    @Test
    void transientRestoreFailureRemainsRetryableUntilSuccess() {
        AtomicInteger restoreCalls = new AtomicInteger();
        SafeClaimToolLifecycle lifecycle = lifecycle((token, slot) -> {
            if (restoreCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("transient restore failure");
            }
        });

        assertThrows(IllegalStateException.class,
                () -> lifecycle.close(SafeClaimToolLifecycle.TerminalReason.FINISH));
        assertEquals(1, restoreCalls.get());
        assertTrue(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.BARRIER_CANCEL));
        assertEquals(2, restoreCalls.get());
        assertFalse(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.TIMEOUT));
        assertEquals(2, restoreCalls.get());
    }

    @Test
    void reentrantCloseCannotDuplicateSuccessfulRestore() {
        AtomicInteger restoreCalls = new AtomicInteger();
        AtomicReference<SafeClaimToolLifecycle> lifecycleRef = new AtomicReference<>();
        AtomicReference<Boolean> reentrantResult = new AtomicReference<>();
        SafeClaimToolLifecycle lifecycle = lifecycle((token, slot) -> {
            restoreCalls.incrementAndGet();
            reentrantResult.set(lifecycleRef.get().close(
                    SafeClaimToolLifecycle.TerminalReason.BARRIER_CANCEL));
        });
        lifecycleRef.set(lifecycle);

        assertTrue(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.FINISH));
        assertEquals(Boolean.FALSE, reentrantResult.get());
        assertEquals(1, restoreCalls.get());
        assertFalse(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.SERVER_STOP));
        assertEquals(1, restoreCalls.get());
    }

    @RepeatedTest(20)
    void concurrentCloseCannotDuplicateCallbackWhileRestoreIsInProgress() throws InterruptedException {
        AtomicInteger restoreCalls = new AtomicInteger();
        CountDownLatch restoreEntered = new CountDownLatch(1);
        CountDownLatch releaseRestore = new CountDownLatch(1);
        AtomicReference<Boolean> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        SafeClaimToolLifecycle lifecycle = lifecycle((token, slot) -> {
            restoreCalls.incrementAndGet();
            restoreEntered.countDown();
            await(releaseRestore);
        });
        Thread firstClose = new Thread(() -> {
            try {
                firstResult.set(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.FINISH));
            } catch (Throwable throwable) {
                firstFailure.set(throwable);
            }
        }, "safe-claim-first-close");

        firstClose.start();
        try {
            assertTrue(restoreEntered.await(5, TimeUnit.SECONDS));
            assertFalse(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.TIMEOUT));
            assertTrue(firstClose.isAlive(), "first close must still be inside the blocked restore callback");
            assertEquals(1, restoreCalls.get());
        } finally {
            releaseRestore.countDown();
            firstClose.join(TimeUnit.SECONDS.toMillis(5));
        }

        assertFalse(firstClose.isAlive());
        assertNull(firstFailure.get());
        assertEquals(Boolean.TRUE, firstResult.get());
        assertFalse(lifecycle.close(SafeClaimToolLifecycle.TerminalReason.SERVER_STOP));
        assertEquals(1, restoreCalls.get());
    }

    private static SafeClaimToolLifecycle lifecycle(java.util.function.BiConsumer<Object, Integer> restore) {
        return new SafeClaimToolLifecycle(SafeClaimToolPurpose.SAFE_AREA, new Object(), 4, restore);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("restore callback did not receive its release signal");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
