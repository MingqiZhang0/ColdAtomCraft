package dev.coldatomcraft.experiment.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.coldatomcraft.experiment.world.ContractTestSupport.ALL_SERVER_FACTS;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.TEST_MODEL_CATALOG;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.candidate;
import static dev.coldatomcraft.experiment.world.ContractTestSupport.precheck;
import static org.junit.jupiter.api.Assertions.*;

/** Pure Java attempt fencing. Does NOT stand in for the missing server adapter. */
class ExperimentRunSchedulerTest {
    private static final SimulationInterval INTERVAL = new SimulationInterval(0, 1_000);
    private static final ApparatusKey APPARATUS = new ApparatusKey(
            FakeWorldState.DIMENSION, FakeWorldState.DEVICE_X,
            FakeWorldState.DEVICE_Y, FakeWorldState.DEVICE_Z);

    private static ExperimentRunScheduler.StartedSession create(ExperimentRunScheduler scheduler) {
        return create(scheduler, 0, 10);
    }

    private static ExperimentRunScheduler.StartedSession create(ExperimentRunScheduler scheduler,
                                                               long firstTick, long lastTick) {
        var outcome = scheduler.createExperiment(ExperimentReservation.single(APPARATUS,
                new ServerTickWindow(firstTick, lastTick)));
        assertEquals(ExperimentRunScheduler.CreateStatus.CREATED, outcome.status());
        return outcome.started();
    }

    @Test
    void sessionAndRunIdsAreIssuedAndDistinct() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var a = create(scheduler);
        var b = create(scheduler, 10, 20);
        assertNotEquals(a.session().sessionId(), b.session().sessionId());
        assertNotEquals(a.attempt().runId(), b.attempt().runId());
        assertEquals(0L, a.attempt().ordinal());
        assertEquals(a.session().sessionId(), a.attempt().sessionId());
        assertEquals(ExperimentRunScheduler.RetryStatus.ACTIVE_ATTEMPT,
                scheduler.retry(a.session()).status());
    }

    @Test
    void staleWorldRejectionThenAbandonThenFreshRunMayClaim() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var session = create(scheduler);
        var world = new FakeWorldState();
        var first = session.attempt();
        assertEquals(WorldEffectLedger.BeginResult.STARTED, scheduler.beginStep(first, 0, INTERVAL));
        var old = candidate(world, new CalculationStep(first.runId(), 0, INTERVAL), 0);
        var check = precheck(world, old);
        world.changeMirrorContent();
        var stale = scheduler.submit(first, old, check, world.snapshot(), ALL_SERVER_FACTS);
        assertEquals(ExperimentRunScheduler.SubmissionStatus.REJECTED_BY_COORDINATOR, stale.status());
        assertEquals(ServerEffectCommitCoordinator.Decision.SERVER_REVALIDATION_FAILED,
                stale.coordinatorDecision());
        assertEquals(0, scheduler.claimedTransferCount());
        assertEquals(ExperimentRunScheduler.AbandonStatus.ABANDONED, scheduler.abandon(first));
        var replacement = scheduler.retry(session.session());
        assertEquals(ExperimentRunScheduler.RetryStatus.STARTED, replacement.status());
        var second = replacement.attempt();
        assertNotEquals(first.runId(), second.runId());
        assertEquals(1L, second.ordinal());
        assertEquals(WorldEffectLedger.BeginResult.RUN_ABANDONED,
                scheduler.beginStep(first, 1, new SimulationInterval(1_000, 2_000)));
        assertEquals(ExperimentRunScheduler.SubmissionStatus.STALE_ATTEMPT,
                scheduler.submit(first, old, check, world.snapshot(), ALL_SERVER_FACTS).status());
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(second, 0, INTERVAL));
        var fresh = candidate(world, new CalculationStep(second.runId(), 0, INTERVAL), 0);
        var result = scheduler.submit(second, fresh, precheck(world, fresh),
                world.snapshot(), ALL_SERVER_FACTS);
        assertTrue(result.claimed());
        assertEquals(1, scheduler.claimedTransferCount());
        assertEquals(ExperimentRunScheduler.AbandonStatus.CLAIMS_PREVENT_ABANDONMENT,
                scheduler.abandon(second));
    }

    @Test
    void unstartedAttemptCanBeAbandonedWithoutAllocatingLedgerSteps() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = create(scheduler);
        assertEquals(ExperimentRunScheduler.AbandonStatus.ABANDONED,
                scheduler.abandon(created.attempt()));
        assertEquals(ExperimentRunScheduler.AbandonStatus.ALREADY_ABANDONED,
                scheduler.abandon(created.attempt()));
        assertEquals(ExperimentRunScheduler.RetryStatus.STARTED,
                scheduler.retry(created.session()).status());
    }

    @Test
    void afterClaimCannotAbandonOrRetryEvenIfMarkedFailed() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = create(scheduler);
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(created.attempt(), 0, INTERVAL));
        var transfer = candidate(world, new CalculationStep(created.attempt().runId(), 0, INTERVAL), 0);
        assertTrue(scheduler.submit(created.attempt(), transfer, precheck(world, transfer),
                world.snapshot(), ALL_SERVER_FACTS).claimed());
        assertTrue(scheduler.markFailed(created.attempt(), transfer.eventKey(), "test-only partial effect"));
        assertFalse(scheduler.markApplied(created.attempt(), transfer.eventKey()));
        assertEquals(WorldEffectLedger.SettlementState.FAILED, scheduler.stateOf(transfer.eventKey()));
        assertEquals(ExperimentRunScheduler.AbandonStatus.CLAIMS_PREVENT_ABANDONMENT,
                scheduler.abandon(created.attempt()));
        assertEquals(ExperimentRunScheduler.RetryStatus.ACTIVE_ATTEMPT,
                scheduler.retry(created.session()).status());
    }

    @Test
    void foreignAttemptFromAnotherSchedulerCannotAccessThisLedger() {
        var a = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var b = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var attempt = create(a).attempt();
        assertEquals(WorldEffectLedger.BeginResult.INVALID, b.beginStep(attempt, 0, INTERVAL));
        assertEquals(ExperimentRunScheduler.AbandonStatus.INVALID_ATTEMPT, b.abandon(attempt));
        assertEquals(ExperimentRunScheduler.SubmissionStatus.INVALID_ATTEMPT,
                b.submit(attempt, null, null, null, null).status());
    }

    @Test
    void mismatchedRunIdCannotSubmitToCurrentAttempt() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = create(scheduler);
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(created.attempt(), 0, INTERVAL));
        var wrong = candidate(world, new CalculationStep("forged-different-run", 0, INTERVAL), 0);
        var result = scheduler.submit(created.attempt(), wrong, precheck(world, wrong),
                world.snapshot(), ALL_SERVER_FACTS);
        assertEquals(ExperimentRunScheduler.SubmissionStatus.FOREIGN_RUN, result.status());
        assertEquals(0, scheduler.claimedTransferCount());
    }

    @Test
    void wrongModelOrRevokedPermissionDoNotConsumeAttempt() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var created = create(scheduler);
        var world = new FakeWorldState();
        scheduler.beginStep(created.attempt(), 0, INTERVAL);
        var transfer = candidate(world, new CalculationStep(created.attempt().runId(),0,INTERVAL),0);
        var revoked = new ServerMutationFacts(true, true, true, true, true, false, 0);
        var result = scheduler.submit(created.attempt(), transfer, precheck(world, transfer),
                world.snapshot(), revoked);
        assertEquals(ExperimentRunScheduler.SubmissionStatus.REJECTED_BY_COORDINATOR,result.status());
        assertEquals(0, scheduler.claimedTransferCount());
        assertEquals(ExperimentRunScheduler.AbandonStatus.ABANDONED, scheduler.abandon(created.attempt()));
    }

    @Test
    void overlappingSessionCannotReserveSameApparatusWindow() {
        var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
        var first = create(scheduler).attempt();
        var rejected = scheduler.createExperiment(ExperimentReservation.single(APPARATUS,
                new ServerTickWindow(9, 15)));
        assertEquals(ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                rejected.status());
        assertTrue(rejected.started() == null);
        var world = new FakeWorldState();
        assertEquals(WorldEffectLedger.BeginResult.STARTED,
                scheduler.beginStep(first, 0, INTERVAL));
        var a = candidate(world, new CalculationStep(first.runId(), 0, INTERVAL), 0);
        assertTrue(scheduler.submit(first, a, precheck(world,a), world.snapshot(), ALL_SERVER_FACTS).claimed());
        assertEquals(1, scheduler.claimedTransferCount());
    }

    @Test
    void claimAndAbandonRaceCannotBothWin() throws Exception {
        for (int repetition = 0; repetition < 60; repetition++) {
            var scheduler = new ExperimentRunScheduler(TEST_MODEL_CATALOG);
            var created = create(scheduler);
            var world = new FakeWorldState();
            scheduler.beginStep(created.attempt(), 0, INTERVAL);
            var transfer = candidate(world, new CalculationStep(created.attempt().runId(),0,INTERVAL),0);
            var check = precheck(world,transfer);
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            var pool = Executors.newFixedThreadPool(2);
            try {
                var submit = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("submit timeout");
                    return scheduler.submit(created.attempt(), transfer, check,
                            world.snapshot(), ALL_SERVER_FACTS).status();
                });
                var abandon = pool.submit(() -> {
                    ready.countDown();
                    if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("abandon timeout");
                    return scheduler.abandon(created.attempt());
                });
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                go.countDown();
                var claimResult = submit.get(10, TimeUnit.SECONDS);
                var abandonResult = abandon.get(10, TimeUnit.SECONDS);
                assertTrue((claimResult == ExperimentRunScheduler.SubmissionStatus.CLAIMED
                        && abandonResult == ExperimentRunScheduler.AbandonStatus.CLAIMS_PREVENT_ABANDONMENT)
                        || (claimResult == ExperimentRunScheduler.SubmissionStatus.STALE_ATTEMPT
                        && abandonResult == ExperimentRunScheduler.AbandonStatus.ABANDONED));
                assertEquals(claimResult == ExperimentRunScheduler.SubmissionStatus.CLAIMED ? 1 : 0,
                        scheduler.claimedTransferCount());
            } finally {
                go.countDown();
                pool.shutdownNow();
                assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }
}
