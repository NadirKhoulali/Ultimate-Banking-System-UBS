package net.austizz.ultimatebankingsystem.bank.owner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerPcMutationContextCollectorTest {
    private static final OwnerPcMutationContextCollector.RememberedPc REMEMBERED =
            new OwnerPcMutationContextCollector.RememberedPc(
                    "minecraft:overworld", 10, 64, 10, "machine-a");
    private static final OwnerPcMutationContextCollector.LoadedPc LIVE_PC =
            new OwnerPcMutationContextCollector.LoadedPc(
                    true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                    "machine-a", true, true);
    private static final OwnerPcMutationContextCollector.PlayerLocation NEARBY =
            new OwnerPcMutationContextCollector.PlayerLocation(
                    "minecraft:overworld", 10.5D, 64.5D, 10.5D);

    @ParameterizedTest(name = "real context collector denies {0}")
    @MethodSource("invalidWorldFacts")
    void everyInvalidPhysicalContextDeniesDirectMutation(NamedFacts facts) {
        OwnerPcActionPolicy.MutationContext context = OwnerPcMutationContextCollector.collect(
                facts.remembered(), facts.loadedPc(), facts.player());

        OwnerPcActionPolicy.Decision decision = OwnerPcActionPolicy.authorize(
                "SET_MOTTO", OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC, context);
        assertFalse(decision.allowed(), facts.name());
    }

    @Test
    void loadedMatchingNearbyPoweredUnlockedPcProducesLiveContext() {
        OwnerPcActionPolicy.MutationContext context = OwnerPcMutationContextCollector.collect(
                REMEMBERED, LIVE_PC, NEARBY);

        assertTrue(context.remembered());
        assertTrue(context.levelLoaded());
        assertTrue(context.ownerPcBlock());
        assertTrue(context.machineMatches());
        assertTrue(context.sameDimension());
        assertTrue(context.withinRange());
        assertTrue(context.poweredOn());
        assertTrue(context.sessionUnlocked());
        assertTrue(OwnerPcActionPolicy.authorize(
                "SET_MOTTO", OwnerPcActionPolicy.Channel.DIRECT_OWNER_PC, context).allowed());
    }

    private static Stream<NamedFacts> invalidWorldFacts() {
        return Stream.of(
                new NamedFacts("absent", null, null, NEARBY),
                new NamedFacts("unloaded", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, false, OwnerPcMutationContextCollector.BlockKind.UNAVAILABLE,
                                "machine-a", true, true), NEARBY),
                new NamedFacts("wrong block", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OTHER,
                                "machine-a", true, true), NEARBY),
                new NamedFacts("wrong machine", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-b", true, true), NEARBY),
                new NamedFacts("cross dimension", REMEMBERED, LIVE_PC,
                        new OwnerPcMutationContextCollector.PlayerLocation(
                                "minecraft:the_nether", 10.5D, 64.5D, 10.5D)),
                new NamedFacts("out of range", REMEMBERED, LIVE_PC,
                        new OwnerPcMutationContextCollector.PlayerLocation(
                                "minecraft:overworld", 40.5D, 64.5D, 10.5D)),
                new NamedFacts("powered off", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-a", false, false), NEARBY),
                new NamedFacts("locked", REMEMBERED,
                        new OwnerPcMutationContextCollector.LoadedPc(
                                true, true, OwnerPcMutationContextCollector.BlockKind.OWNER_PC,
                                "machine-a", true, false), NEARBY)
        );
    }

    private record NamedFacts(String name,
                              OwnerPcMutationContextCollector.RememberedPc remembered,
                              OwnerPcMutationContextCollector.LoadedPc loadedPc,
                              OwnerPcMutationContextCollector.PlayerLocation player) {
        @Override
        public String toString() {
            return name;
        }
    }
}
