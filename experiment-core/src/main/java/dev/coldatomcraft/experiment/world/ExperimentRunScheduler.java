package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelCatalog;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * In-memory, server-intended *logical experiment* attempt fencing.
 *
 * <p>One {@link Session} represents one logical execution, not an apparatus or
 * an entire research project. Its first {@link Attempt} is issued here, not by
 * a caller-supplied string. When that attempt becomes stale <em>before any
 * event is claimed</em>, {@link #abandon(Attempt)} allows exactly one replacement
 * attempt to become current. Every callback from a retired attempt is rejected.
 * Once an event has been claimed, replacement is forbidden because even FAILED
 * or outstanding CLAIMED outcomes may have affected the world.</p>
 *
 * <p>All state transitions here and in the owned ledger occur under this
 * scheduler's monitor. Cross-session apparatus/time reservations are also
 * guarded by the same monitor and are allocated atomically.
 * No computation, game-world operation, network call or
 * user callback is executed by this class. The underlying ledger is private:
 * production code must not bypass the attempt fence by making direct claims.</p>
 *
 * <p><strong>Important limitations:</strong> this is ordinary pure Java code;
 * construction and IDs do not prove a caller is a Minecraft server. The future
 * NeoForge adapter must instantiate and invoke it only in a trusted server
 * context. This scheduler refuses overlapping server-tick windows for the same
 * reserved apparatus block positions within its own instance; a separate
 * scheduler, a forged scheduling window or a restarted JVM can still duplicate
 * the physical operation. Only the future authoritative game adapter can bind
 * reservations to live devices, one server clock and actual mutation time.
 * The model and world
 * snapshots passed to {@link #submit} remain caller-provided records; a real
 * NeoForge adapter must capture them on the server thread immediately before
 * mutation. No persistent or cross-restart exactly-once guarantee exists.</p>
 */
public final class ExperimentRunScheduler {
    public enum CreateStatus {
        CREATED,
        APPARATUS_WINDOW_CONFLICT,
        INVALID_RESERVATION
    }

    public enum CancelStatus {
        CANCELLED,
        SESSION_CLOSED,
        SETTLEMENT_HISTORY_PRESENT,
        INVALID_SESSION
    }

    public enum RetryStatus {
        STARTED,
        SESSION_CLOSED,
        ACTIVE_ATTEMPT,
        SETTLEMENT_HISTORY_PRESENT,
        INVALID_SESSION
    }

    public enum AbandonStatus {
        ABANDONED,
        ALREADY_ABANDONED,
        CLAIMS_PREVENT_ABANDONMENT,
        INVALID_ATTEMPT
    }

    public enum SubmissionStatus {
        CLAIMED,
        INVALID_ATTEMPT,
        STALE_ATTEMPT,
        FOREIGN_RUN,
        FOREIGN_APPARATUS,
        OUTSIDE_RESERVED_SERVER_WINDOW,
        REJECTED_BY_COORDINATOR
    }

    /** Internal reference: constructor intentionally inaccessible to callers. */
    public static final class Session {
        private final ExperimentRunScheduler owner;
        private final String sessionId;
        private Attempt current;
        private final ExperimentReservation reservation;
        private boolean closed;
        private boolean claimedAny;
        private long nextAttemptOrdinal;

        private Session(ExperimentRunScheduler owner, String sessionId,
                        ExperimentReservation reservation) {
            this.owner = owner;
            this.sessionId = sessionId;
            this.reservation = reservation;
        }

        public String sessionId() {
            return sessionId;
        }

        public ExperimentReservation reservation() {
            return reservation;
        }
    }

    /** Scheduler-issued attempt token; its textual ID is not authorization. */
    public static final class Attempt {
        private final Session session;
        private final String runId;
        private final long ordinal;
        private boolean abandoned;

        private Attempt(Session session, String runId, long ordinal) {
            this.session = session;
            this.runId = runId;
            this.ordinal = ordinal;
        }

        public String runId() {
            return runId;
        }

        public long ordinal() {
            return ordinal;
        }

        public String sessionId() {
            return session.sessionId;
        }
    }

    public record StartedSession(Session session, Attempt attempt) { }

    public record CreateOutcome(CreateStatus status, StartedSession started) {
        public CreateOutcome {
            if ((status == CreateStatus.CREATED) != (started != null)) {
                throw new IllegalArgumentException("only CREATED carries a session");
            }
        }
    }

    private record Lease(Session session, ServerTickWindow window) { }

    public record RetryOutcome(RetryStatus status, Attempt attempt) {
        public RetryOutcome {
            if ((status == RetryStatus.STARTED) != (attempt != null)) {
                throw new IllegalArgumentException("only STARTED can carry a new attempt");
            }
        }
    }

    /** A claimed result is only a reservation, never an assertion of mutation. */
    public record Submission(SubmissionStatus status,
                             ServerEffectCommitCoordinator.Decision coordinatorDecision) {
        public boolean claimed() {
            return status == SubmissionStatus.CLAIMED;
        }
    }

    private final WorldEffectLedger ledger = new WorldEffectLedger();
    // Starts are unique per apparatus because overlapping windows are refused.
    // Claimed sessions retain tombstones indefinitely; see retention policy docs.
    private final Map<ApparatusKey, NavigableMap<Long, Lease>> reservations = new HashMap<>();
    private final ServerEffectCommitCoordinator coordinator;

    public ExperimentRunScheduler(ModelCatalog trustedCatalog) {
        if (trustedCatalog == null) {
            throw new IllegalArgumentException("trusted catalog required");
        }
        coordinator = new ServerEffectCommitCoordinator(ledger, trustedCatalog);
    }

    /**
     * Reserves all required physical apparatus locations over a common server
     * scheduling window (distinct from each solver's simulated nanoseconds).
     * A conflict refuses the WHOLE allocation with no partial state changes.
     * No unreserved/no-argument allocation API is provided, because it could
     * bypass cross-session resource arbitration.
     */
    public synchronized CreateOutcome createExperiment(ExperimentReservation request) {
        if (request == null) {
            return new CreateOutcome(CreateStatus.INVALID_RESERVATION, null);
        }
        for (ApparatusKey key : request.apparatus()) {
            NavigableMap<Long, Lease> timeline = reservations.get(key);
            if (timeline == null) continue;
            var lower = timeline.floorEntry(request.window().startTick());
            var upper = timeline.ceilingEntry(request.window().startTick());
            if ((lower != null && lower.getValue().window().overlaps(request.window()))
                    || (upper != null && upper.getValue().window().overlaps(request.window()))) {
                return new CreateOutcome(CreateStatus.APPARATUS_WINDOW_CONFLICT, null);
            }
        }
        Session session = new Session(this, "cac-exp-" + UUID.randomUUID(), request);
        Attempt attempt = issue(session);
        for (ApparatusKey key : request.apparatus()) {
            var timeline = reservations.computeIfAbsent(key, ignored -> new TreeMap<>());
            Lease previous = timeline.put(request.window().startTick(),
                    new Lease(session, request.window()));
            if (previous != null) {
                throw new IllegalStateException("duplicate apparatus lease start despite precheck");
            }
        }
        return new CreateOutcome(CreateStatus.CREATED, new StartedSession(session, attempt));
    }

    /**
     * Releases an entire unused session's apparatus reservations. If any event
     * has ever been CLAIMED (including FAILED), its historical window remains
     * reserved as an in-memory tombstone, even after application outcome.
     * Cancel never changes ledger settlement state.
     */
    public synchronized CancelStatus cancelUnclaimedSession(Session session) {
        if (!owns(session)) return CancelStatus.INVALID_SESSION;
        if (session.closed) return CancelStatus.SESSION_CLOSED;
        if (session.claimedAny) return CancelStatus.SETTLEMENT_HISTORY_PRESENT;
        Attempt current = session.current;
        if (current != null) {
            var result = ledger.abandonRun(current.runId);
            if (result == WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS) {
                return CancelStatus.SETTLEMENT_HISTORY_PRESENT;
            }
            if (result != WorldEffectLedger.AbandonResult.ABANDONED
                    && result != WorldEffectLedger.AbandonResult.UNKNOWN_RUN) {
                throw new IllegalStateException("unexpected ledger run state: " + result);
            }
            current.abandoned = true;
            session.current = null;
        }
        for (ApparatusKey key : session.reservation.apparatus()) {
            var timeline = reservations.get(key);
            Lease existing = timeline == null ? null
                    : timeline.get(session.reservation.window().startTick());
            if (existing == null || existing.session() != session) {
                throw new IllegalStateException("missing owned apparatus reservation");
            }
            timeline.remove(session.reservation.window().startTick());
            if (timeline.isEmpty()) reservations.remove(key);
        }
        session.closed = true;
        return CancelStatus.CANCELLED;
    }

    /** A second run of this experiment is allowed ONLY after unclaimed abandonment. */
    public synchronized RetryOutcome retry(Session session) {
        if (!owns(session)) {
            return new RetryOutcome(RetryStatus.INVALID_SESSION, null);
        }
        if (session.closed) {
            return new RetryOutcome(RetryStatus.SESSION_CLOSED, null);
        }
        if (session.current != null) {
            return new RetryOutcome(RetryStatus.ACTIVE_ATTEMPT, null);
        }
        if (session.claimedAny) {
            return new RetryOutcome(RetryStatus.SETTLEMENT_HISTORY_PRESENT, null);
        }
        return new RetryOutcome(RetryStatus.STARTED, issue(session));
    }

    /** Reserves a calculation step under this exact active attempt. */
    public synchronized WorldEffectLedger.BeginResult beginStep(Attempt attempt,
                                                                 long stepIndex,
                                                                 SimulationInterval interval) {
        if (!owns(attempt) || stepIndex < 0 || interval == null) {
            return WorldEffectLedger.BeginResult.INVALID;
        }
        if (!isCurrent(attempt)) {
            return WorldEffectLedger.BeginResult.RUN_ABANDONED;
        }
        return ledger.beginCalculation(new CalculationStep(attempt.runId, stepIndex, interval));
    }

    /**
     * Retires an unclaimed attempt. The old attempt may not submit late results;
     * {@link #retry(Session)} can then create a new run ID for this same session.
     */
    public synchronized AbandonStatus abandon(Attempt attempt) {
        if (!owns(attempt)) {
            return AbandonStatus.INVALID_ATTEMPT;
        }
        if (attempt.abandoned) {
            return AbandonStatus.ALREADY_ABANDONED;
        }
        if (!isCurrent(attempt)) {
            return AbandonStatus.INVALID_ATTEMPT;
        }
        WorldEffectLedger.AbandonResult result = ledger.abandonRun(attempt.runId);
        if (result == WorldEffectLedger.AbandonResult.HAS_SETTLEMENT_CLAIMS) {
            return AbandonStatus.CLAIMS_PREVENT_ABANDONMENT;
        }
        // UNKNOWN_RUN means no step has yet been registered: there cannot be a
        // claim, and private-ownership of the ledger prevents later registration
        // by bypassing this scheduler. Thus an unused attempt may be retired.
        if (result != WorldEffectLedger.AbandonResult.ABANDONED
                && result != WorldEffectLedger.AbandonResult.UNKNOWN_RUN) {
            throw new IllegalStateException("unexpected ledger abandonment: " + result);
        }
        attempt.abandoned = true;
        attempt.session.current = null;
        return AbandonStatus.ABANDONED;
    }

    /**
     * Pure-Java revalidation plus claim, with the attempt fence held until the
     * claim outcome is known. This does NOT write to the Minecraft world.
     */
    public synchronized Submission submit(Attempt attempt,
                                          EnergyTransferCandidate candidate,
                                          WorldEffectGate.Precheck precheck,
                                          ServerWorldSnapshot current,
                                          ServerMutationFacts facts) {
        if (!owns(attempt)) {
            return new Submission(SubmissionStatus.INVALID_ATTEMPT, null);
        }
        if (!isCurrent(attempt)) {
            return new Submission(SubmissionStatus.STALE_ATTEMPT, null);
        }
        if (candidate == null || !attempt.runId.equals(candidate.step().calculationRunId())) {
            return new Submission(candidate == null ? SubmissionStatus.INVALID_ATTEMPT
                    : SubmissionStatus.FOREIGN_RUN, null);
        }
        if (!attempt.session.reservation.includes(candidate.sourceDevice())) {
            return new Submission(SubmissionStatus.FOREIGN_APPARATUS, null);
        }
        if (facts == null || !attempt.session.reservation.window().contains(facts.currentServerTick())) {
            return new Submission(facts == null ? SubmissionStatus.REJECTED_BY_COORDINATOR
                    : SubmissionStatus.OUTSIDE_RESERVED_SERVER_WINDOW,
                    facts == null ? ServerEffectCommitCoordinator.Decision.INVALID : null);
        }
        var result = coordinator.submit(candidate, precheck, current, facts);
        if (result == ServerEffectCommitCoordinator.Decision.SETTLEMENT_CLAIMED) {
            attempt.session.claimedAny = true;
            return new Submission(SubmissionStatus.CLAIMED, result);
        }
        return new Submission(SubmissionStatus.REJECTED_BY_COORDINATOR, result);
    }

    /** Records outcome only for an event claimed by this active attempt. */
    public synchronized boolean markApplied(Attempt attempt, TransferEventKey key) {
        return isCurrent(attempt) && belongsTo(attempt, key) && ledger.markApplied(key);
    }

    /** Records failure without allowing event retry or run replacement. */
    public synchronized boolean markFailed(Attempt attempt, TransferEventKey key, String reason) {
        return isCurrent(attempt) && belongsTo(attempt, key) && ledger.markFailed(key, reason);
    }

    public synchronized WorldEffectLedger.SettlementState stateOf(TransferEventKey key) {
        return ledger.stateOf(key);
    }

    public synchronized int claimedTransferCount() {
        return ledger.claimedTransferCount();
    }

    private Attempt issue(Session session) {
        Attempt attempt = new Attempt(session, "cac-run-" + UUID.randomUUID(),
                session.nextAttemptOrdinal++);
        session.current = attempt;
        return attempt;
    }

    private boolean owns(Session session) {
        return session != null && session.owner == this;
    }

    private boolean owns(Attempt attempt) {
        return attempt != null && owns(attempt.session);
    }

    private boolean isCurrent(Attempt attempt) {
        return owns(attempt) && !attempt.session.closed && !attempt.abandoned
                && attempt.session.current == attempt;
    }

    private static boolean belongsTo(Attempt attempt, TransferEventKey key) {
        return key != null && attempt.runId.equals(key.calculationRunId());
    }
}
