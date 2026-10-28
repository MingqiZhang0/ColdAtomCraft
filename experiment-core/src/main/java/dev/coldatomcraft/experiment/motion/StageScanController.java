package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D22A pure server-side *request generator* for a feedback-verified 1-axis scan.
 *
 * No motor, encoder, power reservation, authority proof, Minecraft block
 * mutation, or physically calibrated optical measurement is implemented here.
 * An eventual trusted adapter must authenticate every feedback/reading and
 * confirm STOP before releasing its external motor/device leases.
 */
public final class StageScanController {
    public enum Phase { NEW, MOVING, SETTLING, SAMPLING, COMPLETE, ABORTED }
    public enum Action { MOVE, READ, WAIT, FINISHED, STOP }

    /** The trusted server adapter must verify identity, revision, loading and interlock. */
    public record Feedback(String identity, long revision, long encoderUnits,
                           boolean loaded, boolean interlockApproved,
                           boolean motionStopped) {
        public Feedback {
            if (identity == null || identity.isBlank() || identity.length() > 128 || revision < 0) {
                throw new IllegalArgumentException("invalid stage feedback identity");
            }
        }
    }

    /** The target is a laboratory integer SI unit, not an MC block coordinate. */
    public record Decision(Action action, Phase phase, int pointIndex,
                           long targetUnits, long moveSequence, String reason) {
        public Decision {
            Objects.requireNonNull(action);
            Objects.requireNonNull(phase);
            Objects.requireNonNull(reason);
        }
    }

    private final StageScanPlan plan;
    private final ArrayList<StageScanDataset.Point> points = new ArrayList<>();
    private final ArrayList<StageScanDataset.Reading> readings = new ArrayList<>();
    private Phase phase = Phase.NEW;
    private String identity;
    private long revision;
    private long lastTick = -1;
    private long moveStartedTick = -1;
    private long moveSequence;
    private long requestReadTick = -1;
    private long nextReadTick = -1;
    private boolean waitingRead;
    private int stableTicks;
    private int pointIndex;
    private String failureReason = "";

    public StageScanController(StageScanPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }
    public Phase phase() { return phase; }
    public String failureReason() { return failureReason; }
    public int currentPointIndex() { return pointIndex; }
    public List<StageScanDataset.Point> completedPoints() { return List.copyOf(points); }

    /**
     * Call exactly once per consecutive authoritative server game tick.
     * READ responses are asynchronous: only accept a response in a later tick
     * after this instance actually emitted READ, and only with that tick's
     * authenticated server observation. Never fabricate missing readings.
     */
    public Decision advance(long tick, Feedback feedback, PowerTrace.Sample observation) {
        if (phase == Phase.ABORTED) return decision(Action.STOP, failureReason);
        if (phase == Phase.COMPLETE) return decision(Action.FINISHED, "completed");
        if (tick < 0 || (lastTick >= 0 && (lastTick == Long.MAX_VALUE
                || tick != lastTick + 1))) {
            return abort("missing, repeated or reversed server game tick");
        }
        lastTick = tick;
        if (feedback == null || !feedback.loaded() || !feedback.interlockApproved()
                || !plan.axis().inRange(feedback.encoderUnits())) {
            return abort("encoder, chunk or trusted interlock unavailable");
        }
        if (phase == Phase.NEW) {
            if (observation != null) return abort("unsolicited optical sample");
            identity = feedback.identity();
            revision = feedback.revision();
            return startMove(tick);
        }
        if (!identity.equals(feedback.identity()) || revision != feedback.revision()) {
            return abort("motor identity or configuration revision changed");
        }
        if (phase != Phase.SAMPLING && observation != null) {
            return abort("unsolicited optical sample");
        }

        long target = plan.targets().get(pointIndex);
        boolean arrived = feedback.motionStopped()
                && plan.axis().withinTolerance(feedback.encoderUnits(), target);
        if (phase == Phase.MOVING || phase == Phase.SETTLING) {
            if (tick - moveStartedTick > plan.moveTimeoutTicks()) {
                return abort("motor position timeout");
            }
            if (!arrived) {
                stableTicks = 0;
                phase = Phase.MOVING;
                return decision(Action.WAIT, "awaiting encoder position");
            }
            phase = Phase.SETTLING;
            stableTicks++;
            if (stableTicks >= plan.settleTicks()) {
                phase = Phase.SAMPLING;
                nextReadTick = tick == Long.MAX_VALUE ? Long.MAX_VALUE : tick + 1;
                waitingRead = false;
                return decision(Action.WAIT, "position settled; waiting for read schedule");
            }
            return decision(Action.WAIT, "settling at actual encoder position");
        }
        if (phase != Phase.SAMPLING) throw new IllegalStateException("unreachable scan phase");
        if (!arrived) return abort("motor moved or encoder drifted during optical acquisition");
        if (waitingRead) {
            if (tick - requestReadTick > plan.readTimeoutTicks()) {
                return abort("server optical read timeout");
            }
            if (observation == null) return decision(Action.WAIT, "awaiting authoritative power reading");
            if (tick <= requestReadTick || observation.gameTick() != tick) {
                return abort("late, early or mismatched optical sample");
            }
            readings.add(new StageScanDataset.Reading(feedback.encoderUnits(), observation));
            waitingRead = false;
            if (readings.size() == plan.samplesPerTarget()) {
                points.add(new StageScanDataset.Point(pointIndex, target, List.copyOf(readings)));
                readings.clear();
                pointIndex++;
                if (pointIndex == plan.targets().size()) {
                    phase = Phase.COMPLETE;
                    return decision(Action.FINISHED, "completed; external lease must be released safely");
                }
                return startMove(tick);
            }
            nextReadTick = tick > Long.MAX_VALUE - plan.sampleSpacingTicks()
                    ? Long.MAX_VALUE : tick + plan.sampleSpacingTicks();
            return decision(Action.WAIT, "measurement accepted; waiting next sample window");
        }
        if (observation != null) return abort("sample without a pending read request");
        if (tick < nextReadTick) return decision(Action.WAIT, "sampling interval");
        requestReadTick = tick;
        waitingRead = true;
        return decision(Action.READ, "request one current server-side power observation");
    }

    /** A new move sequence must be acknowledged by an external trusted actuator. */
    private Decision startMove(long tick) {
        phase = Phase.MOVING;
        moveStartedTick = tick;
        moveSequence++;
        stableTicks = 0;
        nextReadTick = -1;
        waitingRead = false;
        return decision(Action.MOVE, "request move; no motor actuation performed");
    }

    private Decision abort(String reason) {
        failureReason = reason;
        phase = Phase.ABORTED;
        return decision(Action.STOP, reason);
    }

    private Decision decision(Action action, String reason) {
        long target = pointIndex < plan.targets().size() ? plan.targets().get(pointIndex) : 0;
        return new Decision(action, phase, pointIndex, target, moveSequence, reason);
    }
}
