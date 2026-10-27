package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.BeamInputs;
import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.experiment.Measurement;
import dev.coldatomcraft.experiment.ModelApproval;
import dev.coldatomcraft.experiment.ModelCatalog;
import dev.coldatomcraft.experiment.ModelDefinition;
import dev.coldatomcraft.experiment.ModelGate;
import dev.coldatomcraft.experiment.ModelReference;
import dev.coldatomcraft.experiment.ModelVerification;
import dev.coldatomcraft.experiment.world.CalculationStep;
import dev.coldatomcraft.experiment.world.CardinalFacing;
import dev.coldatomcraft.experiment.world.DevicePlacement;
import dev.coldatomcraft.experiment.world.DeviceSnapshot;
import dev.coldatomcraft.experiment.world.EnergyTransferCandidate;
import dev.coldatomcraft.experiment.world.ExperimentRunScheduler;
import dev.coldatomcraft.experiment.world.ExperimentReservation;
import dev.coldatomcraft.experiment.world.ApparatusKey;
import dev.coldatomcraft.experiment.world.ServerTickWindow;
import dev.coldatomcraft.experiment.world.ServerEffectCommitCoordinator;
import dev.coldatomcraft.experiment.world.ServerEffectGate;
import dev.coldatomcraft.experiment.world.ServerMutationFacts;
import dev.coldatomcraft.experiment.world.ServerWorldSnapshot;
import dev.coldatomcraft.experiment.world.SimulationInterval;
import dev.coldatomcraft.experiment.world.TargetKind;
import dev.coldatomcraft.experiment.world.TargetSnapshot;
import dev.coldatomcraft.experiment.world.TransferEventKey;
import dev.coldatomcraft.experiment.world.WorldEffectGate;
import dev.coldatomcraft.experiment.world.WorldEffectLedger;
import dev.coldatomcraft.experiment.world.WorldReadSet;
import dev.coldatomcraft.experiment.world.WorldRevision;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Dependency-free engineering checks: these are not tests of a laser model. */
public final class Smoke {
    private static int checked;

    private static void check(boolean condition, String label) {
        checked++;
        if (!condition) throw new AssertionError(label);
    }

    private static void near(double actual, double expected, double tolerance, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance, label);
    }

    private static void reject(Runnable action, Class<? extends Throwable> expected, String label) {
        boolean rejected = false;
        try { action.run(); }
        catch (Throwable failure) {
            if (!expected.isInstance(failure)) throw failure;
            rejected = true;
        }
        check(rejected, label);
    }

    public static void main(String[] args) throws Exception {
        Vec3 unit = new Vec3(3, 4, 0).normalized();
        near(unit.magnitude(), 1, 1e-12, "unit length");
        near(unit.dot(new Vec3(0, 1, 0)), 0.8, 1e-12, "dot");
        near(new Vec3(Double.MIN_VALUE, 0, 0).normalized().x(), 1, 0, "subnormal normalization");
        near(new Vec3(Double.MAX_VALUE, 0, 0).normalized().x(), 1, 0, "large normalization");
        check(new Vec3(1, 0, 0).cross(new Vec3(0, 1, 0)).equals(new Vec3(0, 0, 1)), "right-handed cross");
        reject(() -> new Vec3(Double.NaN, 0, 0), IllegalArgumentException.class, "finite vector");
        reject(() -> new Vec3(0, 0, 0).normalized(), IllegalArgumentException.class, "no zero normal");

        BeamInputs b = new BeamInputs(0.02, 780e-9, 0.001,
                new Vec3(2, 3, 4), new Vec3(0, 0, -2));
        near(b.outputDirectionLab().z(), -1, 0, "output direction normal");
        near(b.vacuumWavelengthMeters(), 780e-9, 0, "wavelength units");
        reject(() -> new BeamInputs(-1, 780e-9, .001,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1)), IllegalArgumentException.class, "negative power");
        reject(() -> new BeamInputs(1, 0, .001,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1)), IllegalArgumentException.class, "zero wavelength");

        Measurement m = new Measurement("detector-A", "power", "W", 0.02, 0.001, 1000);
        near(m.standardUncertainty(), 0.001, 0, "uncertainty retained");
        reject(() -> new Measurement("d", "power", "W", 0.1, -1, 100),
                IllegalArgumentException.class, "bad uncertainty");

        ModelDefinition draft = new ModelDefinition("THM-001", "A", "PENDING", ModelApproval.DRAFT);
        ModelDefinition approved = new ModelDefinition("THM-001", "A", "owner-notes:THM-001-A",
                ModelApproval.OWNER_APPROVED, ModelVerification.IMPLEMENTED);
        ModelDefinition verified = new ModelDefinition("THM-001", "A", "owner-notes:THM-001-A",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        check(!draft.ownerApproved(), "unapproved model gate");
        check(approved.ownerApproved(), "approved model provenance");
        check(!approved.numericallyVerified(), "approval does not imply verification");
        reject(() -> ModelGate.requireOwnerApproved(draft), IllegalStateException.class, "draft fail-closed");
        reject(() -> ModelGate.requireNumericallyVerified(approved), IllegalStateException.class,
                "approval not numeric evidence");
        reject(() -> new ModelDefinition("THM-001", "A", "PENDING", ModelApproval.OWNER_APPROVED),
                IllegalArgumentException.class, "no placeholder approval reference");
        reject(() -> new ModelDefinition("THM-001", "A", "PENDING", ModelApproval.DRAFT,
                ModelVerification.NUMERICALLY_VERIFIED), IllegalArgumentException.class, "no verified draft");

        var catalog = ModelCatalog.fromTrustedServerBootstrap(List.of(verified));
        var reference = new ModelReference("THM-001", "A");
        check(ModelGate.resolveWorldEffectModel(catalog, reference).equals(verified), "catalog exact resolution");
        reject(() -> ModelGate.resolveWorldEffectModel(catalog, new ModelReference("THM-001", "B")),
                IllegalStateException.class, "unknown model reference fails");
        reject(() -> ModelCatalog.fromTrustedServerBootstrap(List.of(verified, approved)),
                IllegalArgumentException.class, "duplicate catalog entry rejected");
        reject(() -> new ModelReference("", "A"), IllegalArgumentException.class, "blank model reference");

        DevicePlacement south = DevicePlacement.fromBlock(4, 72, -6, CardinalFacing.SOUTH);
        check(south.blenderPointToWorldBlocks(new Vec3(0, 0, 0)).equals(new Vec3(4.5, 72, -5.5)),
                "base footprint pivot");
        check(south.blenderDirectionToWorld(new Vec3(0, -1, 0)).equals(new Vec3(0, 0, 1)),
                "blender -Y to MC south");
        check(DevicePlacement.fromBlock(0, 0, 0, CardinalFacing.NORTH)
                .blenderDirectionToWorld(new Vec3(0, -1, 0)).equals(new Vec3(0, 0, -1)),
                "blender -Y to MC north");
        check(DevicePlacement.fromBlock(0, 0, 0, CardinalFacing.EAST)
                .blenderDirectionToWorld(new Vec3(0, -1, 0)).equals(new Vec3(1, 0, 0)),
                "blender -Y to MC east");
        check(DevicePlacement.fromBlock(0, 0, 0, CardinalFacing.WEST)
                .blenderDirectionToWorld(new Vec3(0, -1, 0)).equals(new Vec3(-1, 0, 0)),
                "blender -Y to MC west");
        Vec3 anchor = south.blenderPointToWorldBlocks(new Vec3(0, -0.25, 0.4));
        check(anchor.equals(new Vec3(4.5, 72.4, -5.25)), "optical anchor position");
        Vec3 gltf = DevicePlacement.blenderToGltf(new Vec3(0.3, -0.4, 1.2));
        near(south.gltfPointToWorldBlocks(gltf).x(),
                south.blenderPointToWorldBlocks(new Vec3(0.3, -0.4, 1.2)).x(), 1e-12, "GLB anchor agrees");
        check(south.gltfDirectionToWorld(new Vec3(0, 0, 1)).equals(new Vec3(0, 0, 1)),
                "glTF +Z is south forward");
        Vec3 ex = south.blenderVectorToWorld(new Vec3(1, 0, 0));
        Vec3 ey = south.blenderVectorToWorld(new Vec3(0, 1, 0));
        Vec3 ez = south.blenderVectorToWorld(new Vec3(0, 0, 1));
        near(ex.cross(ey).dot(ez), 1, 0, "basis preserves handedness");
        near(south.blenderVectorToWorld(new Vec3(3, 0, 4)).magnitude(), 5, 1e-12,
                "rotation preserves distances");

        String dim = "minecraft:overworld";
        WorldRevision deviceChunk = new WorldRevision(dim, 0, 0, 1, 10, true);
        WorldRevision targetChunk = new WorldRevision(dim, 1, 0, 1, 11, true);
        WorldRevision mirror = new WorldRevision(dim, 2, 2, 1, 12, true);
        WorldReadSet readSet = new WorldReadSet(List.of(mirror, targetChunk, deviceChunk));
        DeviceSnapshot device = new DeviceSnapshot(dim, 8, 64, 8,
                "coldatomcraft:laser_source", 20, true);
        TargetSnapshot target = new TargetSnapshot("entity:42", TargetKind.ENTITY, dim,
                20.5, 65, 8.5, 30, true);
        CalculationStep step = new CalculationStep("run-smoke", 0, new SimulationInterval(0, 1_000));
        EnergyTransferCandidate effect = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, step, 0, readSet);
        var precheck = WorldEffectGate.precheck(effect, verified, readSet, device, target);
        check(precheck.allowed(), "current snapshots pass precheck");
        check(precheck.candidate().orElseThrow().equals(effect), "precheck binds complete candidate");
        var ledger = new WorldEffectLedger();
        check(ledger.beginCalculation(step) == WorldEffectLedger.BeginResult.STARTED,
                "calculation step reserved");
        check(ledger.beginCalculation(step) == WorldEffectLedger.BeginResult.DUPLICATE_STEP,
                "duplicate calculation step rejected");
        var changedEnergy = new EnergyTransferCandidate("THM-001", "A", device, target,
                99.0, step, 0, readSet);
        check(ledger.claim(changedEnergy, precheck) == WorldEffectLedger.ClaimResult.PRECHECK_CANDIDATE_MISMATCH,
                "changed energy cannot reuse precheck");
        var changedStep = new CalculationStep("run-smoke", 0, new SimulationInterval(200, 300));
        var changedStepCandidate = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, changedStep, 0, readSet);
        var changedStepPrecheck = WorldEffectGate.precheck(changedStepCandidate, verified,
                readSet, device, target);
        check(ledger.claim(changedStepCandidate, changedStepPrecheck)
                        == WorldEffectLedger.ClaimResult.STEP_INTERVAL_MISMATCH,
                "changed interval cannot reuse registered step");
        check(ledger.claim(effect, precheck) == WorldEffectLedger.ClaimResult.CLAIMED,
                "event claimed once");
        check(ledger.claim(effect, precheck) == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                "duplicate event rejected");
        TransferEventKey eventKey = effect.eventKey();
        check(ledger.isTransferClaimed(eventKey), "event ledger records key");
        var changedReadSet = new WorldReadSet(List.of(
                new WorldRevision(dim, 0, 0, 1, 10, true),
                new WorldRevision(dim, 1, 0, 1, 99, true),
                mirror));
        var stale = WorldEffectGate.precheck(effect, verified, changedReadSet, device, target);
        check(!stale.allowed(), "stale target chunk rejected");
        check(stale.reasons().contains(WorldEffectGate.RejectionReason.READ_SET_MISMATCH),
                "stale read-set reason recorded");
        reject(() -> new WorldReadSet(List.of(deviceChunk,
                        new WorldRevision(dim, 4, 4, 1, 1, false))),
                IllegalArgumentException.class, "unloaded chunk excluded from read set");
        reject(() -> new SimulationInterval(5, 5), IllegalArgumentException.class,
                "empty interval rejected");

        var currentWorld = new ServerWorldSnapshot(readSet, device, target);
        var allFacts = new ServerMutationFacts(true, true, true, true, true, true, 10);
        var authorization = ServerEffectGate.authorize(effect, precheck, verified, currentWorld, allFacts);
        check(authorization.allowed(), "fresh server snapshot and facts revalidate");
        check(!ServerEffectGate.authorize(effect, precheck, verified, currentWorld,
                new ServerMutationFacts(true, true, true, true, true, false, 0)).allowed(),
                "missing permission fails closed");
        var staleWorld = new ServerWorldSnapshot(changedReadSet, device, target);
        check(!ServerEffectGate.authorize(effect, precheck, verified, staleWorld, allFacts).allowed(),
                "stale server snapshot fails closed");

        var coordinatorLedger = new WorldEffectLedger();
        check(coordinatorLedger.beginCalculation(step) == WorldEffectLedger.BeginResult.STARTED,
                "coordinator step reserved");
        var coordinator = new ServerEffectCommitCoordinator(coordinatorLedger, catalog);
        var freshPrecheck = WorldEffectGate.precheck(effect, verified, readSet, device, target);
        check(coordinator.submit(effect, freshPrecheck, currentWorld, allFacts)
                        == ServerEffectCommitCoordinator.Decision.SETTLEMENT_CLAIMED,
                "coordinator claims after server revalidation");
        check(coordinatorLedger.stateOf(eventKey) == WorldEffectLedger.SettlementState.CLAIMED,
                "coordinator does not claim APPLIED");
        var emptyCatalogCoordinator = new ServerEffectCommitCoordinator(
                new WorldEffectLedger(), ModelCatalog.fromTrustedServerBootstrap(List.of()));
        check(emptyCatalogCoordinator.submit(effect, freshPrecheck, currentWorld, allFacts)
                        == ServerEffectCommitCoordinator.Decision.MODEL_NOT_TRUSTED,
                "empty trusted catalog fails closed");

        // M0-R2.1-D1: explicit run abandonment prevents late stale results.
        var abortStep = new CalculationStep("run-abandoned", 0, new SimulationInterval(0, 500));
        var abortLedger = new WorldEffectLedger();
        check(abortLedger.beginCalculation(abortStep) == WorldEffectLedger.BeginResult.STARTED,
                "abandonment test run started");
        var abortCandidate = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, abortStep, 0, readSet);
        var abortCheck = WorldEffectGate.precheck(abortCandidate, verified, readSet, device, target);
        check(abortLedger.abandonRun("run-abandoned") == WorldEffectLedger.AbandonResult.ABANDONED,
                "unclaimed run can be abandoned");
        check(abortLedger.isRunAbandoned("run-abandoned"), "abandoned run is recorded");
        check(abortLedger.beginCalculation(abortStep) == WorldEffectLedger.BeginResult.RUN_ABANDONED,
                "abandoned step cannot restart");
        check(abortLedger.claim(abortCandidate, abortCheck) == WorldEffectLedger.ClaimResult.RUN_ABANDONED,
                "late abandoned candidate cannot claim");
        var replacementStep = new CalculationStep("new-server-run", 0, abortStep.interval());
        check(abortLedger.beginCalculation(replacementStep) == WorldEffectLedger.BeginResult.STARTED,
                "new run may reserve same time interval");
        var replacement = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, replacementStep, 0, readSet);
        var replacementPrecheck = WorldEffectGate.precheck(replacement, verified, readSet, device, target);
        check(abortLedger.claim(replacement, replacementPrecheck) == WorldEffectLedger.ClaimResult.CLAIMED,
                "fresh run can claim");
        check(abortLedger.abandonRun("new-server-run")
                        == WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS,
                "run with claimed effects cannot be abandoned");

        // Repeated two-thread arbitration: no run may be both abandoned AND
        // permitted to claim an effect. This stress probe is not a formal
        // concurrency proof; the corresponding JUnit test is also retained.
        var racePool = Executors.newFixedThreadPool(2);
        try {
            for (int index = 0; index < 100; index++) {
                var raceStep = new CalculationStep("race-" + index, 0,
                        new SimulationInterval(0, 1_000));
                var raceLedger = new WorldEffectLedger();
                check(raceLedger.beginCalculation(raceStep) == WorldEffectLedger.BeginResult.STARTED,
                        "race test reserved step " + index);
                var raceCandidate = new EnergyTransferCandidate("THM-001", "A", device, target,
                        1.5, raceStep, 0, readSet);
                var raceCheck = WorldEffectGate.precheck(raceCandidate, verified,
                        readSet, device, target);
                var ready = new CountDownLatch(2);
                var start = new CountDownLatch(1);
                var claimJob = racePool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("claim timed out");
                    return raceLedger.claim(raceCandidate, raceCheck);
                });
                var abortJob = racePool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("abort timed out");
                    return raceLedger.abandonRun(raceStep.calculationRunId());
                });
                check(ready.await(5, TimeUnit.SECONDS), "both contenders started " + index);
                start.countDown();
                var claimStatus = claimJob.get(5, TimeUnit.SECONDS);
                var abortStatus = abortJob.get(5, TimeUnit.SECONDS);
                check((claimStatus == WorldEffectLedger.ClaimResult.CLAIMED
                        && abortStatus == WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS)
                        || (claimStatus == WorldEffectLedger.ClaimResult.RUN_ABANDONED
                        && abortStatus == WorldEffectLedger.AbandonResult.ABANDONED),
                        "only one race operation succeeds " + index);
            }
        } finally {
            racePool.shutdownNow();
            check(racePool.awaitTermination(5, TimeUnit.SECONDS),
                    "race test executor terminates");
        }

        // M0-R2.1-D2: eventIndex is unique per run+step, not per model revision.
        var slotLedger = new WorldEffectLedger();
        var slotStep = new CalculationStep("run-slot", 0, new SimulationInterval(0, 500));
        check(slotLedger.beginCalculation(slotStep) == WorldEffectLedger.BeginResult.STARTED,
                "slot guard step reserved");
        var slotA = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, slotStep, 0, readSet);
        var slotB = new EnergyTransferCandidate("THM-001", "B", device, target,
                1.5, slotStep, 0, readSet);
        var verifiedB = new ModelDefinition("THM-001", "B", "test-only:THM-001-B",
                ModelApproval.OWNER_APPROVED, ModelVerification.NUMERICALLY_VERIFIED);
        check(slotLedger.claim(slotA, WorldEffectGate.precheck(slotA, verified,
                readSet, device, target)) == WorldEffectLedger.ClaimResult.CLAIMED,
                "initial event slot claimed");
        check(slotLedger.claim(slotB, WorldEffectGate.precheck(slotB, verifiedB,
                readSet, device, target)) == WorldEffectLedger.ClaimResult.DUPLICATE_TRANSFER,
                "model revision cannot duplicate claimed event slot");
        check(slotLedger.claimedTransferCount() == 1, "event slot counted exactly once");

        // Server-issued logical session: abandoning a stale unclaimed attempt
        // fences late arrivals; replay can only run under the same session's
        // replacement attempt. This does not substitute for world authority.
        var scheduler = new ExperimentRunScheduler(catalog);
        var reservation = ExperimentReservation.single(ApparatusKey.of(device),
                new ServerTickWindow(10, 20));
        var allocated = scheduler.createExperiment(reservation);
        check(allocated.status() == ExperimentRunScheduler.CreateStatus.CREATED,
                "server operation reserve created");
        var session = allocated.started();
        check(scheduler.createExperiment(reservation).status()
                == ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                "two sessions cannot reserve same apparatus/time");
        var initialAttempt = session.attempt();
        check(initialAttempt.runId().startsWith("cac-run-"), "run token minted by scheduler");
        check(scheduler.beginStep(initialAttempt, 0, new SimulationInterval(0, 500))
                == WorldEffectLedger.BeginResult.STARTED, "logical attempt step reserved");
        var original = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, new CalculationStep(initialAttempt.runId(), 0,
                new SimulationInterval(0, 500)), 0, readSet);
        var initialPrecheck = WorldEffectGate.precheck(original, verified, readSet, device, target);
        check(scheduler.retry(session.session()).status()
                == ExperimentRunScheduler.RetryStatus.ACTIVE_ATTEMPT,
                "cannot duplicate active attempt");
        check(scheduler.abandon(initialAttempt) == ExperimentRunScheduler.AbandonStatus.ABANDONED,
                "stale unclaimed attempt retired");
        check(scheduler.submit(initialAttempt, original, initialPrecheck,
                currentWorld, allFacts).status() == ExperimentRunScheduler.SubmissionStatus.STALE_ATTEMPT,
                "late result from retired attempt blocked");
        var next = scheduler.retry(session.session());
        check(next.status() == ExperimentRunScheduler.RetryStatus.STARTED,
                "replacement attempt issued");
        check(!next.attempt().runId().equals(initialAttempt.runId()),
                "replacement has different run identity");
        check(scheduler.beginStep(next.attempt(), 0, new SimulationInterval(0, 500))
                == WorldEffectLedger.BeginResult.STARTED,
                "replacement can reserve same simulated interval");
        var replacementEffect = new EnergyTransferCandidate("THM-001", "A", device, target,
                1.5, new CalculationStep(next.attempt().runId(), 0,
                new SimulationInterval(0, 500)), 0, readSet);
        var replacementCheck = WorldEffectGate.precheck(replacementEffect, verified,
                readSet, device, target);
        check(scheduler.submit(next.attempt(), replacementEffect, replacementCheck,
                currentWorld, allFacts).claimed(), "new run claims one valid result");
        check(scheduler.abandon(next.attempt())
                == ExperimentRunScheduler.AbandonStatus.CLAIMS_PREVENT_ABANDONMENT,
                "claimed run cannot be replaced");
        check(scheduler.claimedTransferCount() == 1, "replacement claimed count is one");
        check(scheduler.cancelUnclaimedSession(session.session())
                == ExperimentRunScheduler.CancelStatus.SETTLEMENT_HISTORY_PRESENT,
                "claimed session holds its apparatus/time reservation");
        check(scheduler.createExperiment(reservation).status()
                == ExperimentRunScheduler.CreateStatus.APPARATUS_WINDOW_CONFLICT,
                "claimed reservation remains a tombstone");
        var secondScheduler = new ExperimentRunScheduler(catalog);
        check(secondScheduler.beginStep(next.attempt(), 1, new SimulationInterval(500, 1000))
                == WorldEffectLedger.BeginResult.INVALID,
                "foreign scheduler cannot use another scheduler token");

        System.out.println("PASS: " + checked + " standalone engineering checks; no physics models claimed");
    }
}
