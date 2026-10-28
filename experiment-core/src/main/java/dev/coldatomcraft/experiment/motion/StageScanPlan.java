package dev.coldatomcraft.experiment.motion;

import java.util.List;
import java.util.Objects;

/**
 * D22 reference scan plan in laboratory SI coordinates. One integer coordinate
 * unit is 1 nm of translation or 1 nrad of rotation; it is NOT a claim about
 * actual positioning accuracy. No Minecraft block coordinates occur here.
 */
public record StageScanPlan(Axis axis, List<Long> targets, int settleTicks,
                            int moveTimeoutTicks, int readTimeoutTicks,
                            int sampleSpacingTicks, int samplesPerTarget) {
    public static final int MAX_POINTS = 128;
    public static final long MAX_ABS_UNITS = 1_000_000_000_000L;

    public enum Unit { NANOMETRE, NANORADIAN }

    public record Axis(Unit unit, long minimum, long maximum, long tolerance) {
        public Axis {
            Objects.requireNonNull(unit, "axis unit");
            if (minimum < -MAX_ABS_UNITS || maximum > MAX_ABS_UNITS
                    || minimum > maximum || tolerance < 0
                    || tolerance > maximum - minimum) {
                throw new IllegalArgumentException("invalid laboratory axis limits/tolerance");
            }
        }
        public boolean inRange(long position) {
            return position >= minimum && position <= maximum;
        }
        public boolean withinTolerance(long actual, long target) {
            return inRange(actual) && inRange(target)
                    && Math.abs(actual - target) <= tolerance;
        }
        public String label() {
            return unit == Unit.NANOMETRE ? "nm" : "nrad";
        }
    }

    public StageScanPlan {
        Objects.requireNonNull(axis, "axis");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets"));
        if (targets.isEmpty() || targets.size() > MAX_POINTS) {
            throw new IllegalArgumentException("scan must contain 1..128 targets");
        }
        for (Long target : targets) {
            if (target == null || !axis.inRange(target)) {
                throw new IllegalArgumentException("scan target outside axis limits");
            }
        }
        if (settleTicks < 1 || settleTicks > 10_000 || settleTicks > moveTimeoutTicks
                || moveTimeoutTicks < 1
                || moveTimeoutTicks > 100_000 || readTimeoutTicks < 1
                || readTimeoutTicks > 10_000 || sampleSpacingTicks < 1
                || sampleSpacingTicks > 10_000 || samplesPerTarget < 1
                || samplesPerTarget > 64) {
            throw new IllegalArgumentException("unsafe scan timing/size budget");
        }
    }
}
