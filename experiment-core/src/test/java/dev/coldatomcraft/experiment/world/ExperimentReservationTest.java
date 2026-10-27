package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Random;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** In-memory device/time reservation; NOT a NeoForge ServerLevel authority test. */
class ExperimentReservationTest {
    private static final ApparatusKey LASER = new ApparatusKey(
            FakeWorldState.DIMENSION, FakeWorldState.DEVICE_X,
            FakeWorldState.DEVICE_Y, FakeWorldState.DEVICE_Z);
    private static final ApparatusKey MIRROR = new ApparatusKey(
            FakeWorldState.DIMENSION, 20, 64, 8);
    private static final ApparatusKey REMOTE = new ApparatusKey(
            "minecraft:the_nether", 8, 64, 8);
    private static final SimulationInterval SOLVER_INTERVAL = new SimulationInterval(0, 1_000);

    private static ExperimentReservation single(ApparatusKey key, long startTick, long endTick) {
        return ExperimentReservation.single(key, new ServerTickWindow(startTick, endTick));
    }

    private static ExperimentRunScheduler.StartedSession started(ExperimentRunScheduler scheduler,
                                                                  ExperimentReservation reservation) {
        var result = scheduler.createExperiment(reservation);
        assertEquals(ExperimentRunScheduler.CreateStatus.CREATED, result.status());
        return result.started();
    }

    @Test
    void sameApparatusOverlappingWindowFailsBeforeAnyCalculationOrClaim() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        started(scheduler, single(LASER, 10, 20));
        var rejected = scheduler.createExperiment(single(LASER, 19, 30));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                rejected.status());
        assertTrue(rejected.started() == null);
        assertEquals(0, scheduler.claimedTransferCount());
    }

    @Test
    void intervalIndexHandlesOutOfOrderRequestsAndHalfOpenBoundaries() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        started(scheduler, single(LASER, 20, 30));
        started(scheduler, single(LASER, 0, 10));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(single(LASER, 5, 25)).status());
        started(scheduler, single(LASER, 10, 20));
        started(scheduler, single(LASER, 30, 40));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(single(LASER, 19, 21)).status());
    }

    @Test
    void distinctDevicesMayShareAWindow() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        started(scheduler, single(LASER, 10, 20));
        started(scheduler, single(MIRROR, 10, 20));
        started(scheduler, single(REMOTE, 10, 20));
    }

    @Test
    void sameBlockWithReplacedDeviceIdCannotEvadeLease() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var world = new FakeWorldState();
        started(scheduler, single(ApparatusKey.of(world.device()), 0, 10));
        world.changeDeviceIdentity();
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(single(ApparatusKey.of(world.device()), 0, 10)).status());
    }

    @Test
    void multiApparatusConflictCausesNoPartialReservation() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        started(scheduler, single(MIRROR, 10, 20));
        var group = new ExperimentReservation(Set.of(LASER, MIRROR),
                new ServerTickWindow(10, 20));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(group).status());
        // If the failed multi-reservation inserted LASER prematurely, this would fail.
        started(scheduler, single(LASER, 10, 20));
    }

    @Test
    void cancelledUnclaimedSessionFreesWholeReservationAndFencesOldAttempt() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var req = new ExperimentReservation(Set.of(LASER, MIRROR), new ServerTickWindow(0, 10));
        var created = started(scheduler, req);
        var attempt = created.attempt();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(attempt, 0, SOLVER_INTERVAL));
        assertEquals(ExperimentRunScheduler.CancelStatus.CANCELLED,
                scheduler.cancelUnclaimedSession(created.session()));
        assertEquals(ExperimentRunScheduler.CancelStatus.SESSION_CLOSED,
                scheduler.cancelUnclaimedSession(created.session()));
        assertEquals(ExperimentRunScheduler.RetryStatus.SESSION_CLOSED,
                scheduler.retry(created.session()).status());
        assertEquals(WorldEffectLedger.BeginResult.RUN_ABANDONED,
                scheduler.beginStep(attempt, 1, new SimulationInterval(1_000, 2_000)));
        var world = new FakeWorldState();
        var transfer = candidate(world, new CalculationStep(attempt.runId(), 0, SOLVER_INTERVAL), 0);
        assertEquals(ExperimentRunScheduler.SubmissionStatus.STALE_ATTEMPT,
                scheduler.submit(attempt, transfer, precheck(world, transfer),
                        world.snapshot(), ALL_SERVER_FACTS).status());
        started(scheduler, req);
        assertEquals(0, scheduler.claimedTransferCount());
    }

    @Test
    void cancelledUnusedSessionWithoutStepsAlsoReleasesLease() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var req = single(LASER, 0, 10);
        var created = started(scheduler, req);
        assertEquals(ExperimentRunScheduler.CancelStatus.CANCELLED,
                scheduler.cancelUnclaimedSession(created.session()));
        started(scheduler, req);
    }

    @Test
    void abandoningAttemptForRetryRetainsLease() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var req = single(LASER, 0, 10);
        var created = started(scheduler, req);
        assertEquals(ExperimentRunScheduler.AbandonStatus.ABANDONED,
                scheduler.abandon(created.attempt()));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(req).status());
        assertEquals(ExperimentRunScheduler.RetryStatus.STARTED,
                scheduler.retry(created.session()).status());
    }

    @Test
    void anyClaimEvenFailedRetainsOriginalWindowTombstone() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var req = single(LASER, 0, 10);
        var created = started(scheduler, req);
        var attempt = created.attempt();
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(attempt, 0, SOLVER_INTERVAL));
        var event = candidate(world, new CalculationStep(attempt.runId(), 0, SOLVER_INTERVAL), 0);
        assertTrue(scheduler.submit(attempt, event, precheck(world, event),
                world.snapshot(), ALL_SERVER_FACTS).claimed());
        assertTrue(scheduler.markFailed(attempt, event.eventKey(), "synthetic failure"));
        assertEquals(ExperimentRunScheduler.CancelStatus.SETTLEMENT_HISTORY_PRESENT,
                scheduler.cancelUnclaimedSession(created.session()));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                scheduler.createExperiment(req).status());
        // Different non-overlapping time slots are not frozen by an older claim.
        started(scheduler, single(LASER, 10, 20));
    }

    @Test
    void anAttemptCannotSubmitForDeviceOutsideReservedSet() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = started(scheduler, single(MIRROR, 0, 10));
        var attempt = created.attempt();
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(attempt, 0, SOLVER_INTERVAL));
        var event = candidate(world, new CalculationStep(attempt.runId(), 0, SOLVER_INTERVAL), 0);
        assertEquals(ExperimentRunScheduler.SubmissionStatus.FOREIGN_APPARATUS,
                scheduler.submit(attempt, event, precheck(world, event),
                        world.snapshot(), ALL_SERVER_FACTS).status());
        assertEquals(0, scheduler.claimedTransferCount());
        assertEquals(ExperimentRunScheduler.CancelStatus.CANCELLED,
                scheduler.cancelUnclaimedSession(created.session()));
    }

    @Test
    void submitMustOccurWithinTrustedScheduledTickWindow() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = started(scheduler, single(LASER, 10, 20));
        var attempt = created.attempt();
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(attempt, 0, SOLVER_INTERVAL));
        var event = candidate(world, new CalculationStep(attempt.runId(), 0, SOLVER_INTERVAL), 0);
        var ready = precheck(world, event);
        for (long outOfWindow : List.of(9L, 20L, 21L)) {
            var facts = new ServerMutationFacts(true, true, true, true, true, true, outOfWindow);
            assertEquals(ExperimentRunScheduler.SubmissionStatus.OUTSIDE_RESERVED_SERVER_WINDOW,
                    scheduler.submit(attempt, event, ready, world.snapshot(), facts).status());
            assertEquals(0, scheduler.claimedTransferCount());
        }
        var inside = new ServerMutationFacts(true, true, true, true, true, true, 19);
        assertTrue(scheduler.submit(attempt, event, ready, world.snapshot(), inside).claimed());
        assertEquals(1, scheduler.claimedTransferCount());
    }

    @Test
    void serverClockAndSolverNanosecondsAreNeverComparedDirectly() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = started(scheduler, single(LASER, 10, 20));
        var attempt = created.attempt();
        var simulatedNanos = new SimulationInterval(10_000_000, 100_000_000);
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(attempt, 0, simulatedNanos));
        var event = candidate(world, new CalculationStep(attempt.runId(), 0, simulatedNanos), 0);
        var facts = new ServerMutationFacts(true, true, true, true, true, true, 10);
        assertTrue(scheduler.submit(attempt, event, precheck(world, event),
                world.snapshot(), facts).claimed());
    }

    @Test
    void requestsAndClockIntervalsAreValidatedAndDefensivelyCopied() {
        assertThrows(IllegalArgumentException.class,
                () -> new ServerTickWindow(9, 9));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerTickWindow(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerMutationFacts(true, true, true, true, true, true, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentReservation(Set.of(), new ServerTickWindow(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentReservation(null, new ServerTickWindow(0, 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentReservation(Set.of(LASER), null));
        var input = new HashSet<>(List.of(LASER));
        var req = new ExperimentReservation(input, new ServerTickWindow(0, 1));
        input.clear();
        assertEquals(1, req.apparatus().size());
        assertTrue(req.apparatus().contains(LASER));
        assertThrows(UnsupportedOperationException.class, () -> req.apparatus().clear());
    }

    @Test
    void nullRequestFailsClosedWithoutCreatingSession() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        assertEquals(ExperimentRunScheduler.CreateStatus.INVALID_RESERVATION,
                scheduler.createExperiment(null).status());
        assertTrue(scheduler.createExperiment(null).started() == null);
    }

    @Test
    void distinctSchedulerInstancesAreStillNotGloballyCoordinated() {
        var first = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var second = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var req = single(LASER, 0, 10);
        started(first, req);
        started(second, req);
        // DOCUMENTED BOUNDARY: only one authoritative scheduler instance per
        // server allocation domain, and still no restart-safe allocation.
    }

    @Test
    void indexedReservationMatchesDeterministicNaiveOverlapOracle() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var keys = List.of(LASER, MIRROR, REMOTE);
        var accepted = new ArrayList<ExperimentReservation>();
        var random = new Random(20261026);
        for (int i = 0; i < 2_000; i++) {
            var key = keys.get(random.nextInt(keys.size()));
            long start = random.nextInt(15_000);
            var request = single(key, start, start + 1 + random.nextInt(15));
            boolean conflict = accepted.stream().anyMatch(prior ->
                    prior.apparatus().contains(key) && prior.window().overlaps(request.window()));
            var result = scheduler.createExperiment(request);
            assertEquals(conflict ? ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT
                            : ExperimentRunScheduler.CreateStatus.CREATED,
                    result.status(), "reservation oracle case " + i);
            if (!conflict) accepted.add(request);
        }
    }

    @Test
    void racingCancelAndSubmitCannotFreeAnActuallyClaimedWindow() throws Exception {
        for (int repetition = 0; repetition < 30; repetition++) {
            var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
            var req = single(LASER, 0, 10);
            var created = started(scheduler, req);
            var attempt = created.attempt();
            var world = new FakeWorldState();
            assertEquals(WorldEffectLedger.BeginResult.STARTED,
                    scheduler.beginStep(attempt, 0, SOLVER_INTERVAL));
            var event = candidate(world,
                    new CalculationStep(attempt.runId(), 0, SOLVER_INTERVAL), 0);
            var check = precheck(world, event);
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            try {
                var submitFuture = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return scheduler.submit(attempt, event, check, world.snapshot(),
                            ALL_SERVER_FACTS).status();
                });
                var cancelFuture = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return scheduler.cancelUnclaimedSession(created.session());
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                go.countDown();
                var submit = submitFuture.get(5, TimeUnit.SECONDS);
                var cancel = cancelFuture.get(5, TimeUnit.SECONDS);
                if (submit == ExperimentRunScheduler.SubmissionStatus.CLAIMED) {
                    assertEquals(ExperimentRunScheduler.CancelStatus.SETTLEMENT_HISTORY_PRESENT, cancel);
                    assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                            scheduler.createExperiment(req).status());
                    assertEquals(1, scheduler.claimedTransferCount());
                } else {
                    assertEquals(ExperimentRunScheduler.SubmissionStatus.STALE_ATTEMPT, submit);
                    assertEquals(ExperimentRunScheduler.CancelStatus.CANCELLED, cancel);
                    assertEquals(0, scheduler.claimedTransferCount());
                    started(scheduler, req);
                }
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void concurrentConflictingAllocationsHaveExactlyOneWinner() throws Exception {
        for (int round = 0; round < 24; round++) {
            var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
            var req = single(LASER, 0, 10);
            var pool = Executors.newFixedThreadPool(2);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            try {
                var a = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return scheduler.createExperiment(req).status();
                });
                var b = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
                    return scheduler.createExperiment(req).status();
                });
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                go.countDown();
                var winnerA = a.get(5, TimeUnit.SECONDS);
                var winnerB = b.get(5, TimeUnit.SECONDS);
                assertTrue((winnerA == ExperimentRunScheduler.CreateStatus.CREATED
                        && winnerB == ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT)
                        || (winnerB == ExperimentRunScheduler.CreateStatus.CREATED
                        && winnerA == ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT));
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }
}
