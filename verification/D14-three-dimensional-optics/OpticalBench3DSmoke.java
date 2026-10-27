package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.experiment.PreviewOpticalBench;
import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D;
import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D.*;
import dev.coldatomcraft.physics.optics.GaussianCircularAperture;
import dev.coldatomcraft.physics.optics.JonesVector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** No dependency on Minecraft or on a fake JUnit implementation. */
public final class OpticalBench3DSmoke {
    private static int checks;
    private static final LaserTuning DEFAULT = new LaserTuning(100, 780, 500, 0);
    private static final Position ZERO = new Position(0, 0, 0);

    private static void check(boolean expression, String label) {
        checks++;
        if (!expression) throw new AssertionError("D14: " + label);
    }
    private static void close(double got, double expected, double tol, String label) {
        check(Double.isFinite(got) && Math.abs(got - expected) <= tol,
                label + " got " + got + " wanted " + expected);
    }
    private static void reject(Runnable action, String label) {
        boolean rejected = false;
        try { action.run(); } catch (RuntimeException exception) { rejected = true; }
        check(rejected, label);
    }
    private static Trace trace(Direction3 direction, Map<Position, Surface> setup) {
        return solve(List.of(new Source(ZERO, direction, DEFAULT)), setup);
    }
    private static Trace solve(List<Source> sources, Map<Position, Surface> setup) {
        return PreviewOpticalNetwork3D.solve(sources, position ->
                Objects.requireNonNullElse(setup.get(position), new Air()));
    }
    private static Hit soleHit(Trace trace) {
        check(!trace.incomplete(), "complete optical trace");
        check(trace.hits().size() == 1, "one target receives one branch");
        return trace.hits().get(0);
    }
    private static void testAirAndD10Parity() {
        for (int d=1; d<=24; d++) {
            Position meter = new Position(0, 0, d);
            Trace result = trace(Direction3.SOUTH, Map.of(meter, new Detector(Direction3.NORTH, 1)));
            Hit hit = soleHit(result);
            close(hit.capturedWatts(), PreviewOpticalBench.sampleAligned(d, 1, DEFAULT)
                    .observation().value(), 2e-15, "D10 straight-line power parity at d=" + d);
            check(result.segments().get(0).end() == End.DETECTOR, "beam stops at detector");
            check(result.readingAt(meter).status() == MeterStatus.MEASURED, "meter reading present");
            close(result.readingAt(meter).watts(), hit.capturedWatts(), 0, "meter/trace parity");
        }
        Trace free = trace(Direction3.SOUTH, Map.of());
        check(free.segments().size() == 1 && free.segments().get(0).end() == End.RANGE_LIMIT,
                "air-only route reaches fixture cap without exploding");
        check(!free.incomplete(), "ordinary finite range is not a numerical failure");
    }
    private static void testMirrorRightAngles() {
        Position mirror = new Position(0, 0, 3);
        Position eastMeter = new Position(4, 0, 3);
        Trace bent = trace(Direction3.SOUTH, Map.of(mirror, new Mirror(Direction3.SOUTH, Direction3.EAST),
                eastMeter, new Detector(Direction3.WEST, 2)));
        Hit hit = soleHit(bent);
        close(hit.atDetector().watts(), DEFAULT.powerWatts(), 1e-16, "ideal mirror total power unchanged");
        close(bent.segments().get(0).lengthMeters(), 3-PreviewOpticalNetwork3D.EMITTER_PORT_OFFSET,
                0, "emitter starts at its output port");
        check(bent.segments().get(1).direction() == Direction3.EAST, "90 degree horizontal reflection");
        close(hit.atDetector().polarization().squaredNorm(), 1, 2e-14, "mirror Jones norm");
        Position upMeter = new Position(0, 5, 3);
        Trace vertical = trace(Direction3.SOUTH, Map.of(mirror, new Mirror(Direction3.SOUTH, Direction3.UP),
                upMeter, new Detector(Direction3.DOWN, 2)));
        close(soleHit(vertical).atDetector().watts(), DEFAULT.powerWatts(), 1e-16,
                "vertical mirror power parity");
        check(vertical.segments().get(1).direction() == Direction3.UP, "upward optical branch supported");
    }
    private static void testSplitterPowerBudget() {
        Position splitter = new Position(0,0,2);
        Position straight = new Position(0,0,5);
        Position turned = new Position(3,0,2);
        Trace result = trace(Direction3.SOUTH, Map.of(
                splitter, new Splitter(Direction3.SOUTH, Direction3.EAST),
                straight, new Detector(Direction3.NORTH,2),
                turned, new Detector(Direction3.WEST,2)));
        check(result.segments().size()==3, "splitter produces exactly two outgoing segments");
        check(result.hits().size()==2, "splitter feeds two detectors");
        for (Hit h : result.hits()) close(h.atDetector().watts(),0.05,1e-16,"50:50 branching");
        close(result.hits().stream().mapToDouble(x->x.atDetector().watts()).sum(), DEFAULT.powerWatts(),
                2e-16, "output-port total watts conserved");
        check(result.readingAt(straight).status()==MeterStatus.MEASURED, "single branch meter A");
        check(result.readingAt(turned).status()==MeterStatus.MEASURED, "single branch meter B");
    }
    private static void testLensAndWaist() {
        Position lens = new Position(0,0,2);
        Position detector = new Position(0,0,5);
        Trace reference = trace(Direction3.SOUTH, Map.of(detector, new Detector(Direction3.NORTH,1)));
        Trace focused = trace(Direction3.SOUTH, Map.of(lens, new Lens(Direction3.SOUTH, 1.0),
                detector, new Detector(Direction3.NORTH,1)));
        Hit before = soleHit(reference), after = soleHit(focused);
        close(after.atDetector().watts(),before.atDetector().watts(),1e-16,"lossless lens power");
        check(Math.abs(after.atDetector().radiusMeters()-before.atDetector().radiusMeters())>1e-4,
                "thin lens changes q/spot radius, not just particle appearance");
        check(focused.segments().get(1).atStart().q().imaginaryMeters() > 0,
                "Gaussian q imaginary component stays positive after lens");
        close(after.capturedWatts(), GaussianCircularAperture.capture(after.atDetector().watts(),
                after.atDetector().radiusMeters(), PreviewOpticalBench.apertureRadiusMeters(1), 0)
                .capturedPowerWatts(), 0, "meter uses post-lens q");
        Trace defocused = trace(Direction3.SOUTH, Map.of(lens, new Lens(Direction3.SOUTH,-1.0),
                detector,new Detector(Direction3.NORTH,1)));
        check(soleHit(defocused).atDetector().radiusMeters() > before.atDetector().radiusMeters(),
                "negative focal length diverges beam");
    }
    private static void testHalfWavePlate() {
        Position plate = new Position(0,0,2);
        Position detector = new Position(0,0,4);
        Trace result = trace(Direction3.SOUTH, Map.of(plate,
                new HalfWavePlate(Direction3.SOUTH,Math.PI/4),
                detector,new Detector(Direction3.NORTH,1)));
        Hit hit = soleHit(result);
        close(hit.atDetector().polarization().x().magnitudeSquared(),0,2e-14,
                "45-degree HWP converts local linear H to V");
        close(hit.atDetector().polarization().y().magnitudeSquared(),1,2e-14,
                "45-degree HWP preserves total polarization power");
        close(hit.atDetector().watts(),DEFAULT.powerWatts(),1e-16,
                "lossless HWP preserves total watts");
        Trace zero = trace(Direction3.SOUTH, Map.of(plate,
                new HalfWavePlate(Direction3.SOUTH,0),detector,new Detector(Direction3.NORTH,1)));
        close(soleHit(zero).atDetector().polarization().x().magnitudeSquared(),1,2e-14,
                "zero-degree HWP retains linear H component");
    }
    private static void testFailClosed() {
        Position meter = new Position(0,0,5);
        Trace blocked = trace(Direction3.SOUTH, Map.of(new Position(0,0,2),new Obstacle(),
                meter,new Detector(Direction3.NORTH,0)));
        check(blocked.hits().isEmpty() && blocked.segments().get(0).end()==End.BLOCKED,
                "opaque block terminates all beams");
        Trace unloaded = trace(Direction3.SOUTH, Map.of(new Position(0,0,2),new Unloaded(),
                meter,new Detector(Direction3.NORTH,0)));
        check(unloaded.hits().isEmpty() && unloaded.segments().get(0).end()==End.UNLOADED,
                "unloaded chunk terminates beam without loading it");
        Trace wrong = trace(Direction3.SOUTH, Map.of(meter,new Detector(Direction3.SOUTH,0)));
        check(wrong.hits().isEmpty() && wrong.segments().get(0).end()==End.BLOCKED,
                "misoriented detector cannot be read");
        Trace wrongMirror = trace(Direction3.SOUTH, Map.of(new Position(0,0,2),
                new Mirror(Direction3.NORTH,Direction3.UP)));
        check(wrongMirror.segments().get(0).end()==End.BLOCKED,
                "wrongly oriented optical port does not bend beam");
        reject(()->new Mirror(Direction3.SOUTH,Direction3.NORTH),"180-degree turn not silently supported");
        reject(()->new Lens(Direction3.SOUTH,0),"zero focal length rejected");
        reject(()->new Detector(Direction3.WEST,-1),"illegal aperture rejected");
        reject(()->new Source(ZERO,Direction3.EAST,null),"missing settings rejected");
    }
    private static void testAmbiguousMeasurementsAndLoops() {
        Position meter = new Position(0,0,5);
        Source a = new Source(ZERO, Direction3.SOUTH, DEFAULT);
        Source b = new Source(new Position(0,0,6), Direction3.NORTH, DEFAULT);
        Trace collision = solve(List.of(a,b), Map.of(meter,new Detector(Direction3.NORTH,0)));
        // Two opposite directions cannot be measured through a single front face.
        check(collision.hits().size()==1, "front face rejects counterpropagating ray");
        Position mirror1 = new Position(0,0,2);
        Position mirror2 = new Position(2,0,2);
        Position mirror3 = new Position(2,0,0);
        Position mirror4 = new Position(0,0,0);
        Trace loop = trace(Direction3.SOUTH, Map.of(mirror1,new Mirror(Direction3.SOUTH,Direction3.EAST),
                mirror2,new Mirror(Direction3.EAST,Direction3.NORTH),
                mirror3,new Mirror(Direction3.NORTH,Direction3.WEST),
                mirror4,new Mirror(Direction3.WEST,Direction3.SOUTH)));
        check(loop.segments().size()<PreviewOpticalNetwork3D.MAX_SEGMENTS_PER_TRACE,
                "optical loop terminated within finite segment budget");
        check(loop.incomplete(),"cycle explicitly flagged as incomplete, never treated as success");
        check(loop.readingAt(new Position(200,0,0)).status()==MeterStatus.INCOMPLETE,
                "truncated paths fail closed at detector status");
    }
    private static void testIndependentSources() {
        Position meter = new Position(0,0,5);
        // Two beams can reach the same detector face through a 90-degree mirror.
        Map<Position,Surface> cells = new HashMap<>();
        cells.put(meter,new Detector(Direction3.NORTH,0));
        cells.put(new Position(0,0,3),new Splitter(Direction3.SOUTH,Direction3.EAST));
        // Route one separate source through a different way to detector from same direction:
        // two different sources at different upstream locations aimed SOUTH.
        Trace multiple = solve(List.of(new Source(ZERO,Direction3.SOUTH,DEFAULT),
                new Source(new Position(0,0,-2),Direction3.SOUTH,DEFAULT)),
                Map.of(meter,new Detector(Direction3.NORTH,0)));
        check(multiple.hits().size()==2,"two independent sources both reach detector");
        check(multiple.readingAt(meter).status()==MeterStatus.MULTIPLE_UNRESOLVED,
                "ambiguous multi-beam coherence cannot be added as optical power");
        close(multiple.readingAt(meter).watts(),0,0,"no false sum");
    }
    private static void testDirectionsAndBoundaries() {
        for (Direction3 direction : Direction3.values()) {
            check(direction.perpendicularOutput(0).perpendicular(direction), "orthogonal port exists");
            check(direction.perpendicularOutput(3).perpendicular(direction), "fourth port exists");
            check(!direction.perpendicular(direction.opposite()), "parallel directions not orthogonal");
            Position meter = ZERO.step(direction,2);
            Hit hit=soleHit(trace(direction,Map.of(meter,new Detector(direction.opposite(),0))));
            close(hit.atDetector().watts(),DEFAULT.powerWatts(),0,"six cardinal free-space beam directions");
        }
        reject(()->Direction3.SOUTH.perpendicularOutput(4),"bad turn selector rejected");
        Trace extreme=PreviewOpticalNetwork3D.solve(List.of(
                new Source(new Position(Integer.MAX_VALUE,0,0),Direction3.EAST,DEFAULT)), x->new Air());
        check(extreme.incomplete(),"overflowing grid position fails closed");
    }
    public static void main(String[] args) {
        testAirAndD10Parity();
        testMirrorRightAngles();
        testSplitterPowerBudget();
        testLensAndWaist();
        testHalfWavePlate();
        testFailClosed();
        testAmbiguousMeasurementsAndLoops();
        testIndependentSources();
        testDirectionsAndBoundaries();
        System.out.println("D14 pure Java optical network: "+ checks +" independent fixture assertions passed");
    }
}
