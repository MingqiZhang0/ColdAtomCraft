package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * D22B coarse four-preset actuator protocol. These are integer *D14 optic
 * selectors*, never calibrated angular or linear SI encoder positions.
 * The trusted server adapter must actually apply a selector to a mounted
 * BlockState, then independently read it back and measure the optical network.
 */
public final class DiscretePresetSweep {
    public static final int PRESETS = 4;
    public static final int SETTLE_TICKS = 5;
    public enum Phase { NEW, SETTING, SETTLING, AWAIT_SAMPLE, COMPLETE, ABORTED }
    public enum Action { SET_PRESET, WAIT, READ, FINISHED, STOP }
    public record Decision(Action action, int preset, Phase phase, String reason) {
        public Decision { Objects.requireNonNull(action); Objects.requireNonNull(phase); Objects.requireNonNull(reason); }
    }
    public record Point(int preset, PowerTrace.Sample sample) {
        public Point {
            if (preset < 0 || preset >= PRESETS) throw new IllegalArgumentException("invalid preset");
            Objects.requireNonNull(sample, "sample");
        }
    }

    private Phase phase = Phase.NEW;
    private final ArrayList<Point> samples = new ArrayList<>(PRESETS);
    private int preset;
    private int stableTicks;
    private long lastTick = -1;
    private String failure = "";

    public Phase phase() { return phase; }
    public int requestedPreset() { return preset; }
    public String failure() { return failure; }
    public List<Point> readings() { return List.copyOf(samples); }
    public boolean active() { return phase != Phase.COMPLETE && phase != Phase.ABORTED; }
    public void cancel() {
        if (active()) { phase = Phase.ABORTED; failure = "operator cancelled"; }
    }

    /** Exactly one invocation per consecutive server tick, no fabricated observations. */
    public Decision advance(long gameTick, int observedPreset, boolean boundDevicesValid,
                            PowerTrace.Sample observation) {
        if (phase == Phase.ABORTED) return decision(Action.STOP, failure);
        if (phase == Phase.COMPLETE) return decision(Action.FINISHED, "complete");
        if (gameTick < 0 || gameTick == Long.MAX_VALUE
                || (lastTick >= 0 && gameTick != lastTick + 1)) return abort("nonconsecutive game tick");
        lastTick = gameTick;
        if (!boundDevicesValid || observedPreset < 0 || observedPreset >= PRESETS) {
            return abort("mounted optic, meter or bench unavailable");
        }
        if (phase == Phase.NEW) {
            if (observation != null) return abort("unsolicited first observation");
            phase = Phase.SETTING;
            return decision(Action.SET_PRESET, "request actual optic state update");
        }
        if (observedPreset != preset) return abort("optical preset was modified externally");
        if (phase != Phase.AWAIT_SAMPLE && observation != null) {
            return abort("unsolicited optical sample");
        }
        switch (phase) {
            case SETTING -> {
                phase = Phase.SETTLING;
                stableTicks = 1;
                return decision(Action.WAIT, "first readback tick");
            }
            case SETTLING -> {
                stableTicks++;
                if (stableTicks >= SETTLE_TICKS) {
                    phase = Phase.AWAIT_SAMPLE;
                    return decision(Action.READ, "read optical network on next server tick");
                }
                return decision(Action.WAIT, "waiting for stable preset readback");
            }
            case AWAIT_SAMPLE -> {
                if (observation == null || observation.gameTick() != gameTick) {
                    return abort("missing or mismatched authoritative optical sample");
                }
                samples.add(new Point(preset, observation));
                if (samples.size() == PRESETS) {
                    phase = Phase.COMPLETE;
                    return decision(Action.FINISHED, "all four states sampled");
                }
                preset++;
                phase = Phase.SETTING;
                stableTicks = 0;
                return decision(Action.SET_PRESET, "next diagnostic optical preset");
            }
            default -> throw new IllegalStateException("unexpected scan phase " + phase);
        }
    }

    private Decision abort(String reason) {
        phase = Phase.ABORTED;
        failure = reason;
        return decision(Action.STOP, reason);
    }
    private Decision decision(Action action, String reason) {
        return new Decision(action, preset, phase, reason);
    }
}
