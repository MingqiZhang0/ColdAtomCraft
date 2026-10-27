package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.ALL_SERVER_FACTS;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.STEP;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.SYNTHETIC_APPROVED_MODEL;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEffectGateTest {

    private record Fixture(FakeWorldState world,
                           EnergyTransferCandidate candidate,
                           WorldEffectGate.Precheck precheck) { }

    private static Fixture fixture() {
        FakeWorldState world = new FakeWorldState();
        var candidate = candidate(world, STEP, 0);
        return new Fixture(world, candidate, precheck(world, candidate));
    }

    private static ServerEffectGate.Authorization authorize(Fixture fixture,
                                                             ServerMutationFacts facts) {
        return ServerEffectGate.authorize(fixture.candidate(), fixture.precheck(),
                SYNTHETIC_APPROVED_MODEL, fixture.world().snapshot(), facts);
    }

    @Test
    void worldEffectGateHasNoMutationApi() {
        List<String> methods = Arrays.stream(WorldEffectGate.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methods.stream().anyMatch(name -> name.matches(
                "apply|mutate|setBlock|hurt|damage|explode|destroy|consume|spawn")));
    }

    @Test
    void serverGateHasNoMutationApi() {
        List<String> methods = Arrays.stream(ServerEffectGate.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();
        assertFalse(methods.stream().anyMatch(name -> name.matches(
                "apply|mutate|setBlock|hurt|damage|explode|destroy|consume|spawn")));
    }

    @Test
    void allFreshServerFactsAndCurrentSnapshotAreRequired() {
        Fixture fixture = fixture();
        assertTrue(fixture.precheck().allowed());
        assertTrue(authorize(fixture, ALL_SERVER_FACTS).allowed());

        assertRejected(fixture, new ServerMutationFacts(false, true, true, true, true, true, 0),
                ServerEffectGate.RejectionReason.NOT_SERVER_THREAD);
        assertRejected(fixture, new ServerMutationFacts(true, false, true, true, true, true, 0),
                ServerEffectGate.RejectionReason.SOURCE_DEVICE_UNRESOLVED);
        assertRejected(fixture, new ServerMutationFacts(true, true, false, true, true, true, 0),
                ServerEffectGate.RejectionReason.TARGET_IDENTITY_UNRESOLVED);
        assertRejected(fixture, new ServerMutationFacts(true, true, true, false, true, true, 0),
                ServerEffectGate.RejectionReason.TARGET_POSITION_STALE);
        assertRejected(fixture, new ServerMutationFacts(true, true, true, true, false, true, 0),
                ServerEffectGate.RejectionReason.TARGET_CHUNK_UNLOADED);
        assertRejected(fixture, new ServerMutationFacts(true, true, true, true, true, false, 0),
                ServerEffectGate.RejectionReason.PERMISSION_DENIED);
    }

    private static void assertRejected(Fixture fixture,
                                       ServerMutationFacts facts,
                                       ServerEffectGate.RejectionReason expected) {
        var result = authorize(fixture, facts);
        assertFalse(result.allowed());
        assertTrue(result.reasons().contains(expected), () -> "missing " + expected + " in " + result.reasons());
    }

    @Test
    void staleIntermediateChunkFailsCurrentWorldRevalidation() {
        Fixture fixture = fixture();
        fixture.world().changeMirrorContent();
        var result = authorize(fixture, ALL_SERVER_FACTS);
        assertFalse(result.allowed());
        assertTrue(result.reasons().contains(
                ServerEffectGate.RejectionReason.CURRENT_WORLD_NOT_REVALIDATED));
        assertTrue(result.worldReasons().contains(WorldEffectGate.RejectionReason.READ_SET_MISMATCH));
    }

    @Test
    void rejectedPrecheckCannotBeAuthorizedByFreshFacts() {
        Fixture fixture = fixture();
        fixture.world().changeMirrorContent();
        var rejected = precheck(fixture.world(), fixture.candidate());
        assertFalse(rejected.allowed());

        var authorization = ServerEffectGate.authorize(fixture.candidate(), rejected,
                SYNTHETIC_APPROVED_MODEL, fixture.world().snapshot(), ALL_SERVER_FACTS);
        assertFalse(authorization.allowed());
        assertTrue(authorization.reasons().contains(
                ServerEffectGate.RejectionReason.PRECHECK_NOT_ALLOWED));
    }

    @Test
    void precheckBoundToDifferentCandidateIsRejected() {
        Fixture fixture = fixture();
        var changed = candidate(fixture.world(), STEP, 0, 99.0);
        var authorization = ServerEffectGate.authorize(changed, fixture.precheck(),
                SYNTHETIC_APPROVED_MODEL, fixture.world().snapshot(), ALL_SERVER_FACTS);
        assertFalse(authorization.allowed());
        assertTrue(authorization.reasons().contains(
                ServerEffectGate.RejectionReason.PRECHECK_CANDIDATE_MISMATCH));
    }

    @Test
    void missingInputsFailClosed() {
        var missing = ServerEffectGate.authorize(null, null, null, null, null);
        assertFalse(missing.allowed());
        assertEquals(List.of(ServerEffectGate.RejectionReason.MISSING_INPUT), missing.reasons());
    }
}
