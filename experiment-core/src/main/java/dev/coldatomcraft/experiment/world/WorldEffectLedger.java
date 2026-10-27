package dev.coldatomcraft.experiment.world;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * In-memory, fail-closed accounting for calculation steps and transfer events.
 *
 * <p>Two independent duplicate risks are handled **within this in-memory ledger instance**:</p>
 * <ul>
 *   <li>{@link #beginCalculation(CalculationStep)} reserves a step identity and
 *       rejects a second execution, including a different step index covering an
 *       overlapping interval in the same calculation run.</li>
 *   <li>{@link #claim(EnergyTransferCandidate, WorldEffectGate.Precheck)} reserves
 *       each (runId, stepIndex, eventIndex) slot at most once even if a caller
 *       changes model provenance. The complete transfer key is retained for
 *       diagnostics and outcome tracking.</li>
 * </ul>
 *
 * <p><strong>Failure semantics:</strong> a claimed event slot is never claimable again.
 * The caller must call {@link #markApplied(TransferEventKey)} after success or
 * {@link #markFailed(TransferEventKey, String)} after an exception/failure. A
 * missing outcome record leaves the event {@code CLAIMED}; it is still not
 * retried. A crash between claim and mutation therefore fails closed with a
 * possible lost effect, never a double effect.</p>
 *
 * <p><strong>Restart limitation:</strong> the ledger is intentionally in-memory.
 * A new server process starts with no knowledge of old claims ({@code UNCLAIMED}).
 * Cross-restart exactly-once is <strong>NOT</strong> guaranteed and requires a
 * durable server-owned ledger committed atomically with the mutation, which is
 * not implemented in M0-R2. The tests explicitly demonstrate this limitation.</p>
 *
 * <p><strong>Lifecycle limitation:</strong> this round does not evict calculation
 * history, settlements or failure reasons. Removing them without a durable
 * tombstone would silently re-enable duplicate settlement. See
 * {@code docs/PERFORMANCE_AUDIT.md}.</p>
 *
 * <p>This class has no Minecraft dependency and performs no world mutation.</p>
 */
public final class WorldEffectLedger {
    public enum BeginResult {
        STARTED,
        DUPLICATE_STEP,
        OVERLAPPING_INTERVAL,
        RUN_ABANDONED,
        INVALID
    }

    public enum ClaimResult {
        CLAIMED,
        DUPLICATE_TRANSFER,
        UNREGISTERED_CALCULATION,
        PRECHECK_REQUIRED,
        PRECHECK_CANDIDATE_MISMATCH,
        STEP_INTERVAL_MISMATCH,
        RUN_ABANDONED,
        INVALID
    }

    /**
     * Abandoning a stale solver run is permitted only while no event has been
     * claimed. Once a claim exists, a world mutation may already have happened
     * and the run cannot safely be replaced using this in-memory ledger alone.
     */
    public enum AbandonResult {
        ABANDONED,
        ALREADY_ABANDONED,
        UNKNOWN_RUN,
        HAS_SETTLEMENT_CLAIMS,
        INVALID
    }

    public enum SettlementState {
        UNCLAIMED,
        CLAIMED,
        APPLIED,
        FAILED
    }

    private final Map<StepKey, SimulationInterval> calculations = new HashMap<>();
    private final Set<String> abandonedRuns = new HashSet<>();
    // Monotonic claim history per run; every terminal state still counts as claimed.
    // Maintained under the same monitor as settlements, so abandon is O(1)
    // rather than rescanning all prior claims on every stale-run retry.
    private final Set<String> runsWithClaims = new HashSet<>();
    private final Map<String, NavigableMap<Long, SimulationInterval>> intervalsByRun = new HashMap<>();
    // Event identity is unique per (run, step, eventIndex), regardless of model
    // or model revision. TransferEventKey deliberately carries model provenance,
    // but changing provenance must NEVER create another settlement slot.
    private final Map<EventSlot, TransferEventKey> claimedSlots = new HashMap<>();
    private final Map<TransferEventKey, SettlementState> settlements = new HashMap<>();
    private final Map<TransferEventKey, String> failureReasons = new HashMap<>();

    /**
     * Must be called before invoking the solver. A duplicate step is never
     * calculated a second time.
     *
     * <p>Intervals are indexed per calculation run by start time. Accepted
     * intervals in a run are disjoint by construction, so an overlap can only
     * involve the immediate predecessor or successor of the proposed start.
     * This keeps registration at O(log n) per step instead of scanning all
     * historical steps.</p>
     */
    public synchronized BeginResult beginCalculation(CalculationStep step) {
        if (step == null) {
            return BeginResult.INVALID;
        }
        if (abandonedRuns.contains(step.calculationRunId())) {
            return BeginResult.RUN_ABANDONED;
        }
        StepKey key = new StepKey(step.calculationRunId(), step.stepIndex());
        if (calculations.containsKey(key)) {
            return BeginResult.DUPLICATE_STEP;
        }
        SimulationInterval proposed = step.interval();
        NavigableMap<Long, SimulationInterval> intervals = intervalsByRun.computeIfAbsent(
                step.calculationRunId(), ignored -> new TreeMap<>());
        Map.Entry<Long, SimulationInterval> predecessor = intervals.lowerEntry(proposed.startNanos());
        if (predecessor != null && predecessor.getValue().overlaps(proposed)) {
            return BeginResult.OVERLAPPING_INTERVAL;
        }
        Map.Entry<Long, SimulationInterval> successor = intervals.ceilingEntry(proposed.startNanos());
        if (successor != null && successor.getValue().overlaps(proposed)) {
            return BeginResult.OVERLAPPING_INTERVAL;
        }
        calculations.put(key, proposed);
        intervals.put(proposed.startNanos(), proposed);
        return BeginResult.STARTED;
    }

    /**
     * Must be called after a fresh {@link WorldEffectGate#precheck} and
     * immediately before applying the effect. The first call for an event key
     * wins; every later submission is rejected regardless of payload changes,
     * model identity/revision changes, or previous application failure.
     *
     * <p>The precheck must have been produced for this exact candidate value.
     * Matching only the event key is insufficient because the key deliberately
     * omits payload, target and read set. The candidate's complete calculation
     * interval must also equal the interval registered by
     * {@link #beginCalculation(CalculationStep)}.</p>
     */
    public synchronized ClaimResult claim(EnergyTransferCandidate candidate,
                                          WorldEffectGate.Precheck precheck) {
        if (candidate == null || precheck == null) {
            return ClaimResult.INVALID;
        }
        if (!precheck.allowed()) {
            return ClaimResult.PRECHECK_REQUIRED;
        }
        EnergyTransferCandidate boundCandidate = precheck.candidate().orElse(null);
        if (boundCandidate == null
                || !boundCandidate.equals(candidate)
                || !candidate.eventKey().equals(precheck.candidateKey())) {
            return ClaimResult.PRECHECK_CANDIDATE_MISMATCH;
        }
        StepKey stepKey = new StepKey(candidate.step().calculationRunId(),
                candidate.step().stepIndex());
        SimulationInterval registeredInterval = calculations.get(stepKey);
        if (registeredInterval == null) {
            return ClaimResult.UNREGISTERED_CALCULATION;
        }
        if (abandonedRuns.contains(candidate.step().calculationRunId())) {
            return ClaimResult.RUN_ABANDONED;
        }
        // An already-settled event remains a duplicate regardless of any later
        // payload edit, even if that edit would also fail step validation.
        EventSlot slot = new EventSlot(candidate.step().calculationRunId(),
                candidate.step().stepIndex(), candidate.eventIndex());
        if (claimedSlots.containsKey(slot)) {
            return ClaimResult.DUPLICATE_TRANSFER;
        }
        if (!registeredInterval.equals(candidate.step().interval())) {
            return ClaimResult.STEP_INTERVAL_MISMATCH;
        }
        claimedSlots.put(slot, candidate.eventKey());
        settlements.put(candidate.eventKey(), SettlementState.CLAIMED);
        runsWithClaims.add(candidate.step().calculationRunId());
        return ClaimResult.CLAIMED;
    }

    /**
     * Retires a solver run whose snapshots became stale BEFORE any event was
     * claimed. Existing step and run identities remain reserved as tombstones;
     * no late result from the abandoned run can subsequently be claimed.
     *
     * <p>The authoritative experiment scheduler must allocate a DIFFERENT,
     * server-owned run ID to recompute the stale simulation interval. This method
     * is not a general cancellation of previously committed world effects. A run
     * containing even one CLAIMED/APPLIED/FAILED event is conservatively refused
     * because an effect may have been applied already.</p>
     *
     * <p>Synchronization with {@link #claim} prevents an in-process race between
     * abandonment and claiming: at most one of them can succeed. The ledger
     * cannot prevent an unrelated scheduler from creating a different run ID
     * representing a previously applied world effect, so the production scheduler
     * MUST serialize retries for the same physical experiment.</p>
     *
     * <p>In-memory limitation: no restart-safe cancellation/tombstone guarantee.</p>
     */
    public synchronized AbandonResult abandonRun(String calculationRunId) {
        if (calculationRunId == null || calculationRunId.isBlank()) {
            return AbandonResult.INVALID;
        }
        if (abandonedRuns.contains(calculationRunId)) {
            return AbandonResult.ALREADY_ABANDONED;
        }
        if (!intervalsByRun.containsKey(calculationRunId)) {
            return AbandonResult.UNKNOWN_RUN;
        }
        // A FAILED claim might have partially mutated the world; an APPLIED
        // claim might already have been observed. Neither may be abandoned.
        // Claim history is monotonic and indexed by run ID under this lock.
        if (runsWithClaims.contains(calculationRunId)) {
            return AbandonResult.HAS_SETTLEMENT_CLAIMS;
        }
        abandonedRuns.add(calculationRunId);
        return AbandonResult.ABANDONED;
    }

    /** True only for a run explicitly retired without any settlement claim. */
    public synchronized boolean isRunAbandoned(String calculationRunId) {
        return calculationRunId != null && abandonedRuns.contains(calculationRunId);
    }

    /**
     * Records successful application. Only a currently {@code CLAIMED} event can
     * transition. Repeat or out-of-order outcome records are rejected.
     */
    public synchronized boolean markApplied(TransferEventKey key) {
        if (key == null || settlements.get(key) != SettlementState.CLAIMED) {
            return false;
        }
        settlements.put(key, SettlementState.APPLIED);
        failureReasons.remove(key);
        return true;
    }

    /**
     * Records a failed application or exception. The event remains permanently
     * unclaimable in this ledger; callers must not retry it. A nonblank reason is
     * required for diagnostics.
     */
    public synchronized boolean markFailed(TransferEventKey key, String reason) {
        if (key == null || settlements.get(key) != SettlementState.CLAIMED) {
            return false;
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("failure reason required");
        }
        settlements.put(key, SettlementState.FAILED);
        failureReasons.put(key, reason);
        return true;
    }

    public synchronized SettlementState stateOf(TransferEventKey key) {
        if (key == null) return SettlementState.UNCLAIMED;
        return settlements.getOrDefault(key, SettlementState.UNCLAIMED);
    }

    public synchronized Optional<String> failureReason(TransferEventKey key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(failureReasons.get(key));
    }

    public synchronized boolean isCalculationStarted(String calculationRunId, long stepIndex) {
        return calculations.containsKey(new StepKey(calculationRunId, stepIndex));
    }

    public synchronized boolean isTransferClaimed(TransferEventKey key) {
        return key != null && settlements.containsKey(key);
    }

    public synchronized int claimedTransferCount() {
        return settlements.size();
    }

    /**
     * Original successful claim at the provenance-independent physical event
     * slot, if any. A later attempt with another model revision is rejected even
     * though {@link #stateOf(TransferEventKey)} for the unused alternative key
     * correctly reports UNCLAIMED.
     */
    public synchronized Optional<TransferEventKey> claimedEventForSlot(String runId,
                                                                         long stepIndex,
                                                                         int eventIndex) {
        if (runId == null || runId.isBlank() || stepIndex < 0 || eventIndex < 0) {
            return Optional.empty();
        }
        return Optional.ofNullable(claimedSlots.get(new EventSlot(runId, stepIndex, eventIndex)));
    }


    private record StepKey(String calculationRunId, long stepIndex) { }

    private record EventSlot(String calculationRunId, long stepIndex, int eventIndex) { }
}
