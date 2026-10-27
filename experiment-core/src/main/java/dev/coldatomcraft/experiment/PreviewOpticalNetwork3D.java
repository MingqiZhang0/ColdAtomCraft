package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.ComplexAmplitude;
import dev.coldatomcraft.physics.optics.GaussianCircularAperture;
import dev.coldatomcraft.physics.optics.GaussianQ;
import dev.coldatomcraft.physics.optics.JonesMatrix;
import dev.coldatomcraft.physics.optics.JonesVector;
import dev.coldatomcraft.physics.optics.ParaxialAbcd;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * D14 development-only laboratory-local, axis-aligned 3D optical bench.
 *
 * <p>This solver has no Minecraft or NeoForge dependencies. A caller supplies an
 * already-loaded, immutable-for-this-call view of devices and obstacles. One
 * block represents one metre only in the diagnostic fixture; optics are thin,
 * centered elements with unit throughput unless explicitly split. All beams
 * are ideal, monochromatic TEM00 modes. Jones basis is tied to the propagation
 * direction, with a specified geometric transport on an ideal 90-degree turn.
 * This is NOT a thin-film mirror/coating or a calibrated instrument model.</p>
 *
 * <p>Critically, this does NOT calculate coherent recombination, arbitrary
 * finite-aperture truncation at optics, or world effects. If two distinct
 * branches illuminate one meter, no power sum is reported as a measurement.</p>
 */
public final class PreviewOpticalNetwork3D {
    public static final int MAX_CELLS_PER_SEGMENT = 24;
    public static final int MAX_SEGMENTS_PER_TRACE = 160;
    public static final int MAX_ELEMENTS_PER_BRANCH = 16;
    public static final double EMITTER_PORT_OFFSET = PreviewOpticalBench.DEMO_PORT_OFFSET_METERS;
    public static final double DETECTOR_FACE_OFFSET = PreviewOpticalBench.DEMO_PORT_OFFSET_METERS;
    private static final double SPLIT_FRACTION = 0.5;

    private PreviewOpticalNetwork3D() { }

    public enum Direction3 {
        NORTH(0, 0, -1), EAST(1, 0, 0), SOUTH(0, 0, 1), WEST(-1, 0, 0), UP(0, 1, 0), DOWN(0, -1, 0);
        private final int x;
        private final int y;
        private final int z;

        Direction3(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public int dx() { return x; }
        public int dy() { return y; }
        public int dz() { return z; }
        public Vec3 vector() { return new Vec3(x, y, z); }
        public Direction3 opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case EAST -> WEST;
                case WEST -> EAST;
                case UP -> DOWN;
                case DOWN -> UP;
            };
        }
        public boolean perpendicular(Direction3 other) {
            return other != null && x * other.x + y * other.y + z * other.z == 0;
        }
        public Direction3 perpendicularOutput(int selector) {
            if (selector < 0 || selector > 3) throw new IllegalArgumentException("turn selector must be 0..3");
            int index = 0;
            for (Direction3 candidate : values()) {
                if (perpendicular(candidate) && index++ == selector) return candidate;
            }
            throw new IllegalStateException("no orthogonal output");
        }
        /** One well-defined transverse basis per cardinal direction. */
        public Vec3 transverseX() {
            return this == UP || this == DOWN ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        }
        public Vec3 transverseY() { return vector().cross(transverseX()); }
    }

    /** Minecraft/world absolute block positions are integers; optical offsets remain local doubles. */
    public record Position(int x, int y, int z) {
        public Position step(Direction3 direction, int distance) {
            Objects.requireNonNull(direction, "direction");
            return new Position(Math.addExact(x, Math.multiplyExact(direction.dx(), distance)),
                    Math.addExact(y, Math.multiplyExact(direction.dy(), distance)),
                    Math.addExact(z, Math.multiplyExact(direction.dz(), distance)));
        }
    }

    /** Per-surface variants, intentionally scoped to one explicitly oriented input port. */
    public sealed interface Surface permits Air, Obstacle, Unloaded, Detector, Mirror, Splitter, Lens, HalfWavePlate { }
    public record Air() implements Surface { }
    public record Obstacle() implements Surface { }
    public record Unloaded() implements Surface { }
    public record Detector(Direction3 front, int aperturePreset) implements Surface {
        public Detector {
            Objects.requireNonNull(front, "front");
            PreviewOpticalBench.apertureRadiusMeters(aperturePreset);
        }
    }
    public record Mirror(Direction3 incoming, Direction3 reflected) implements Surface {
        public Mirror { requirePerpendicular(incoming, reflected); }
    }
    public record Splitter(Direction3 incoming, Direction3 reflected) implements Surface {
        public Splitter { requirePerpendicular(incoming, reflected); }
    }
    public record Lens(Direction3 incoming, double focalLengthMeters) implements Surface {
        public Lens {
            Objects.requireNonNull(incoming, "incoming");
            if (!Double.isFinite(focalLengthMeters) || focalLengthMeters == 0) {
                throw new IllegalArgumentException("finite nonzero focal length required");
            }
        }
    }
    public record HalfWavePlate(Direction3 incoming, double axisAngleRadians) implements Surface {
        public HalfWavePlate {
            Objects.requireNonNull(incoming, "incoming");
            if (!Double.isFinite(axisAngleRadians)) throw new IllegalArgumentException("finite waveplate angle required");
        }
    }
    private static void requirePerpendicular(Direction3 incoming, Direction3 reflected) {
        Objects.requireNonNull(incoming, "incoming");
        if (!incoming.perpendicular(reflected)) {
            throw new IllegalArgumentException("ideal 90-degree optical port must be perpendicular");
        }
    }

    @FunctionalInterface
    public interface WorldSnapshot { Surface at(Position position); }

    public record Source(Position position, Direction3 direction, LaserTuning tuning) {
        public Source {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(tuning, "tuning");
        }
    }

    /** Polarization has unit Jones norm; source optical power is kept separately in W. */
    public record Wave(double watts, double wavelengthMeters, GaussianQ q, JonesVector polarization) {
        public Wave {
            if (!(watts >= 0) || !Double.isFinite(watts) || !(wavelengthMeters > 0)
                    || !Double.isFinite(wavelengthMeters) || q == null || polarization == null
                    || Math.abs(polarization.squaredNorm() - 1) > 2e-10) {
                throw new IllegalArgumentException("valid nonnegative W, wavelength, q and unit Jones mode required");
            }
        }
        public Wave propagate(double meters) {
            if (meters < 0 || !Double.isFinite(meters)) throw new IllegalArgumentException("nonnegative finite distance");
            return new Wave(watts, wavelengthMeters, ParaxialAbcd.freeSpace(meters).transform(q), polarization);
        }
        public Wave lens(double focalLengthMeters) {
            return new Wave(watts, wavelengthMeters,
                    ParaxialAbcd.thinLens(focalLengthMeters).transform(q), polarization);
        }
        public Wave halfWave(double axisAngleRadians) {
            return new Wave(watts, wavelengthMeters, q,
                    JonesMatrix.halfWave(axisAngleRadians).apply(polarization));
        }
        public Wave scaled(double fraction, ComplexAmplitude phase) {
            if (!Double.isFinite(fraction) || fraction < 0 || fraction > 1 || phase == null
                    || Math.abs(phase.magnitudeSquared() - 1) > 1e-12) {
                throw new IllegalArgumentException("passive power and phase required");
            }
            return new Wave(watts * fraction, wavelengthMeters, q, polarization.times(phase));
        }
        /** Orthogonal Householder transport. Does not assert real coating s/p phase. */
        public Wave turn(Direction3 from, Direction3 to) {
            requirePerpendicular(from, to);
            Vec3 normal = from.vector().subtract(to.vector()).normalized();
            Vec3 u = from.transverseX();
            Vec3 v = from.transverseY();
            ComplexAmplitude dot = polarization.x().scale(u.dot(normal))
                    .plus(polarization.y().scale(v.dot(normal)));
            Vec3 outU = to.transverseX();
            Vec3 outV = to.transverseY();
            ComplexAmplitude xu = polarization.x().scale(u.dot(outU))
                    .plus(polarization.y().scale(v.dot(outU)))
                    .minus(dot.scale(2 * normal.dot(outU)));
            ComplexAmplitude yv = polarization.x().scale(u.dot(outV))
                    .plus(polarization.y().scale(v.dot(outV)))
                    .minus(dot.scale(2 * normal.dot(outV)));
            return new Wave(watts, wavelengthMeters, q, new JonesVector(xu, yv));
        }
        public double radiusMeters() { return q.radiusMeters(wavelengthMeters); }
    }

    public enum End { ELEMENT, DETECTOR, BLOCKED, UNLOADED, RANGE_LIMIT }
    /** Axial offsets are measured from the center of the segment's origin block. */
    public record Segment(Position origin, Direction3 direction, double startOffsetMeters,
                          double endOffsetMeters, Wave atStart, End end) {
        public Segment {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(atStart, "atStart");
            Objects.requireNonNull(end, "end");
            if (!Double.isFinite(startOffsetMeters) || !Double.isFinite(endOffsetMeters)
                    || startOffsetMeters < 0 || endOffsetMeters <= startOffsetMeters
                    || endOffsetMeters - startOffsetMeters > MAX_CELLS_PER_SEGMENT + 1) {
                throw new IllegalArgumentException("invalid optical segment length");
            }
        }
        public double lengthMeters() { return endOffsetMeters - startOffsetMeters; }
        public Wave waveAt(double fractionFromStart) {
            if (!Double.isFinite(fractionFromStart) || fractionFromStart < 0 || fractionFromStart > 1) {
                throw new IllegalArgumentException("segment interpolation fraction outside [0,1]");
            }
            return atStart.propagate(lengthMeters() * fractionFromStart);
        }
    }
    public record Hit(int sourceIndex, Position position, Direction3 propagation,
                      Wave atDetector, double capturedWatts, int aperturePreset) {
        public Hit {
            if (sourceIndex < 0 || position == null || propagation == null || atDetector == null
                    || !Double.isFinite(capturedWatts) || capturedWatts < 0
                    || capturedWatts > atDetector.watts() + 1e-14) {
                throw new IllegalArgumentException("invalid ideal detector hit");
            }
            PreviewOpticalBench.apertureRadiusMeters(aperturePreset);
        }
    }
    public record Trace(List<Segment> segments, List<Hit> hits, boolean incomplete) {
        public Trace {
            segments = List.copyOf(segments);
            hits = List.copyOf(hits);
        }
        /** One hit is a valid preview measurement, multiple hits are not additive. */
        public MeterReading readingAt(Position position) {
            if (position == null) throw new IllegalArgumentException("detector position required");
            if (incomplete) return new MeterReading(MeterStatus.INCOMPLETE, 0, 0);
            int hitsHere = 0;
            double watts = 0;
            for (Hit hit : hits) {
                if (hit.position().equals(position)) {
                    hitsHere++;
                    if (hitsHere == 1) watts = hit.capturedWatts();
                }
            }
            if (hitsHere == 0) return new MeterReading(MeterStatus.NO_BEAM, 0, 0);
            if (hitsHere > 1) return new MeterReading(MeterStatus.MULTIPLE_UNRESOLVED, 0, hitsHere);
            return new MeterReading(MeterStatus.MEASURED, watts, 1);
        }
    }
    public enum MeterStatus { MEASURED, NO_BEAM, MULTIPLE_UNRESOLVED, INCOMPLETE }
    public record MeterReading(MeterStatus status, double watts, int incidentBranches) {
        public MeterReading {
            Objects.requireNonNull(status, "status");
            if (!Double.isFinite(watts) || watts < 0 || incidentBranches < 0
                    || (status != MeterStatus.MEASURED && watts != 0)) {
                throw new IllegalArgumentException("inconsistent optical meter reading");
            }
        }
    }

    private record Visit(Position position, Direction3 incoming) { }
    private record Branch(int sourceIndex, Position origin, Direction3 direction,
                          double originOffset, Wave wave, int elementCount, Set<Visit> visited) { }

    /**
     * Deterministic bounded trace over an external world snapshot. Caller MUST
     * prevent world/chunk loads and must not use client results as server truth.
     * The cutoff is a development fixture limit, not a free-space physics law.
     */
    public static Trace solve(List<Source> sources, WorldSnapshot snapshot) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(snapshot, "snapshot");
        if (sources.size() > 512) {
            // Never crash the game thread for an intentionally bounded preview.
            return new Trace(List.of(), List.of(), true);
        }
        List<Segment> segments = new ArrayList<>();
        List<Hit> hits = new ArrayList<>();
        boolean incomplete = false;
        ArrayDeque<Branch> queue = new ArrayDeque<>();
        for (int i = 0; i < sources.size(); i++) {
            Source source = Objects.requireNonNull(sources.get(i), "source");
            LaserTuning tuning = source.tuning();
            Wave wave = new Wave(tuning.powerWatts(), tuning.wavelengthMeters(),
                    tuning.laboratoryBeam().complexParameterAt(-tuning.waistOffsetMeters()),
                    JonesVector.horizontal());
            queue.addLast(new Branch(i, source.position(), source.direction(), EMITTER_PORT_OFFSET,
                    wave, 0, Set.of()));
        }
        while (!queue.isEmpty()) {
            if (segments.size() >= MAX_SEGMENTS_PER_TRACE) {
                incomplete = true;
                break;
            }
            Branch branch = queue.removeFirst();
            boolean stopped = false;
            for (int step = 1; step <= MAX_CELLS_PER_SEGMENT; step++) {
                Position target;
                try { target = branch.origin().step(branch.direction(), step); }
                catch (ArithmeticException overflow) {
                    incomplete = true;
                    stopped = true;
                    break;
                }
                Surface surface = Objects.requireNonNull(snapshot.at(target), "world snapshot returned null");
                if (surface instanceof Air) continue;
                if (surface instanceof Unloaded) {
                    add(segments, branch, step - 0.5, End.UNLOADED);
                    stopped = true;
                    break;
                }
                if (surface instanceof Obstacle) {
                    add(segments, branch, step - 0.5, End.BLOCKED);
                    stopped = true;
                    break;
                }
                if (surface instanceof Detector detector) {
                    if (detector.front() != branch.direction().opposite()) {
                        add(segments, branch, step - 0.5, End.BLOCKED);
                    } else {
                        double endOffset = step - DETECTOR_FACE_OFFSET;
                        add(segments, branch, endOffset, End.DETECTOR);
                        Wave arrival = branch.wave().propagate(endOffset - branch.originOffset());
                        double radius = arrival.radiusMeters();
                        double aperture = PreviewOpticalBench.apertureRadiusMeters(detector.aperturePreset());
                        double captured = GaussianCircularAperture.capture(
                                arrival.watts(), radius, aperture, 0).capturedPowerWatts();
                        hits.add(new Hit(branch.sourceIndex(), target, branch.direction(), arrival,
                                captured, detector.aperturePreset()));
                    }
                    stopped = true;
                    break;
                }
                Direction3 expected;
                if (surface instanceof Mirror mirror) expected = mirror.incoming();
                else if (surface instanceof Splitter splitter) expected = splitter.incoming();
                else if (surface instanceof Lens lens) expected = lens.incoming();
                else if (surface instanceof HalfWavePlate plate) expected = plate.incoming();
                else throw new IllegalStateException("unhandled diagnostic surface");

                if (expected != branch.direction()) {
                    add(segments, branch, step - 0.5, End.BLOCKED);
                    stopped = true;
                    break;
                }
                add(segments, branch, step, End.ELEMENT);
                Visit visit = new Visit(target, branch.direction());
                if (branch.elementCount() >= MAX_ELEMENTS_PER_BRANCH || branch.visited().contains(visit)) {
                    incomplete = true;
                    stopped = true;
                    break;
                }
                Set<Visit> path = new HashSet<>(branch.visited());
                path.add(visit);
                Wave arrival = branch.wave().propagate(step - branch.originOffset());
                int nextCount = branch.elementCount() + 1;
                if (surface instanceof Mirror mirror) {
                    queue.addLast(new Branch(branch.sourceIndex(), target, mirror.reflected(), 0,
                            arrival.turn(branch.direction(), mirror.reflected()), nextCount, Set.copyOf(path)));
                } else if (surface instanceof Splitter splitter) {
                    queue.addLast(new Branch(branch.sourceIndex(), target, branch.direction(), 0,
                            arrival.scaled(SPLIT_FRACTION, ComplexAmplitude.ONE), nextCount, Set.copyOf(path)));
                    queue.addLast(new Branch(branch.sourceIndex(), target, splitter.reflected(), 0,
                            arrival.turn(branch.direction(), splitter.reflected())
                                    .scaled(SPLIT_FRACTION, ComplexAmplitude.I), nextCount, Set.copyOf(path)));
                } else if (surface instanceof Lens lens) {
                    queue.addLast(new Branch(branch.sourceIndex(), target, branch.direction(), 0,
                            arrival.lens(lens.focalLengthMeters()), nextCount, Set.copyOf(path)));
                } else if (surface instanceof HalfWavePlate plate) {
                    queue.addLast(new Branch(branch.sourceIndex(), target, branch.direction(), 0,
                            arrival.halfWave(plate.axisAngleRadians()), nextCount, Set.copyOf(path)));
                }
                stopped = true;
                break;
            }
            if (!stopped) {
                add(segments, branch, MAX_CELLS_PER_SEGMENT + 0.4375, End.RANGE_LIMIT);
            }
        }
        return new Trace(segments, hits, incomplete);
    }

    private static void add(List<Segment> segments, Branch branch, double endOffset, End end) {
        if (endOffset > branch.originOffset()) {
            segments.add(new Segment(branch.origin(), branch.direction(), branch.originOffset(),
                    endOffset, branch.wave(), end));
        }
    }
}
