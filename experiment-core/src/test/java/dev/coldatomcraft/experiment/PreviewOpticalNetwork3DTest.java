package dev.coldatomcraft.experiment;

import dev.coldatomcraft.experiment.PreviewOpticalNetwork3D.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Formal JUnit5 tests; also retain separate executable offline reference smoke. */
class PreviewOpticalNetwork3DTest {
    private static final LaserTuning SETTING = new LaserTuning(100,780,500,0);
    private static final Position START = new Position(0,0,0);

    private static Trace solve(Map<Position,Surface> devices) {
        return solve(List.of(new Source(START,Direction3.SOUTH,SETTING)),devices);
    }
    private static Trace solve(List<Source> sources, Map<Position,Surface> devices) {
        return PreviewOpticalNetwork3D.solve(sources, p -> devices.getOrDefault(p,new Air()));
    }

    @Test void d10PowerMeterParityAtAll24Distances() {
        for (int d=1; d<=24; d++) {
            Position receiver = new Position(0,0,d);
            var result=solve(Map.of(receiver,new Detector(Direction3.NORTH,1)));
            assertEquals(1,result.hits().size());
            assertEquals(PreviewOpticalBench.sampleAligned(d,1,SETTING).observation().value(),
                    result.readingAt(receiver).watts(),2e-15);
        }
    }
    @Test void horizontalMirrorRotatesWithoutPowerLoss() {
        Position receiver=new Position(4,0,3);
        var result=solve(Map.of(new Position(0,0,3),new Mirror(Direction3.SOUTH,Direction3.EAST),
                receiver,new Detector(Direction3.WEST,1)));
        assertEquals(2,result.segments().size());
        assertEquals(Direction3.EAST,result.segments().get(1).direction());
        assertEquals(SETTING.powerWatts(),result.hits().get(0).atDetector().watts(),1e-15);
    }
    @Test void verticalMirrorWorks() {
        var result=solve(Map.of(new Position(0,0,2),new Mirror(Direction3.SOUTH,Direction3.UP),
                new Position(0,3,2),new Detector(Direction3.DOWN,1)));
        assertEquals(1,result.hits().size());
        assertEquals(Direction3.UP,result.hits().get(0).propagation());
    }
    @Test void splitterConservesPortPower() {
        var result=solve(Map.of(new Position(0,0,2),new Splitter(Direction3.SOUTH,Direction3.EAST),
                new Position(0,0,5),new Detector(Direction3.NORTH,2),
                new Position(3,0,2),new Detector(Direction3.WEST,2)));
        assertEquals(2,result.hits().size());
        assertEquals(SETTING.powerWatts(),result.hits().stream()
                .mapToDouble(h->h.atDetector().watts()).sum(),1e-15);
    }
    @Test void thinLensChangesSpotNotTotalPower() {
        Position detector =new Position(0,0,5);
        var direct=solve(Map.of(detector,new Detector(Direction3.NORTH,1)));
        var lens=solve(Map.of(new Position(0,0,2),new Lens(Direction3.SOUTH,1),
                detector,new Detector(Direction3.NORTH,1)));
        assertEquals(direct.hits().get(0).atDetector().watts(),lens.hits().get(0).atDetector().watts(),1e-15);
        assertNotEquals(direct.hits().get(0).atDetector().radiusMeters(),
                lens.hits().get(0).atDetector().radiusMeters());
    }
    @Test void halfWavePlateRotatesPolarizationAndPreservesPower() {
        var result=solve(Map.of(new Position(0,0,2),new HalfWavePlate(Direction3.SOUTH,Math.PI/4),
                new Position(0,0,5),new Detector(Direction3.NORTH,1)));
        var j=result.hits().get(0).atDetector().polarization();
        assertEquals(0,j.x().magnitudeSquared(),1e-14);
        assertEquals(1,j.y().magnitudeSquared(),1e-14);
        assertEquals(SETTING.powerWatts(),result.hits().get(0).atDetector().watts(),1e-15);
    }
    @Test void blocksAndUnloadedCellsStopBeam() {
        for (Surface block : List.of(new Obstacle(),new Unloaded())) {
            var result=solve(Map.of(new Position(0,0,2),block,
                    new Position(0,0,5),new Detector(Direction3.NORTH,1)));
            assertTrue(result.hits().isEmpty());
            assertEquals(block instanceof Unloaded?End.UNLOADED:End.BLOCKED,
                    result.segments().get(0).end());
        }
    }
    @Test void wrongInputDirectionBlocksOptic() {
        var result=solve(Map.of(new Position(0,0,2),new Mirror(Direction3.NORTH,Direction3.UP)));
        assertTrue(result.hits().isEmpty());
        assertEquals(End.BLOCKED,result.segments().get(0).end());
    }
    @Test void multiRayDetectorDoesNotInventCoherenceMeasurement() {
        Position receiver=new Position(0,0,5);
        var result=solve(List.of(new Source(START,Direction3.SOUTH,SETTING),
                new Source(new Position(0,0,-2),Direction3.SOUTH,SETTING)),
                Map.of(receiver,new Detector(Direction3.NORTH,1)));
        assertEquals(2,result.hits().size());
        assertEquals(MeterStatus.MULTIPLE_UNRESOLVED,result.readingAt(receiver).status());
        assertEquals(0,result.readingAt(receiver).watts());
    }
    @Test void loopIsBoundedAndNeverMeasured() {
        var result=solve(Map.of(new Position(0,0,2),new Mirror(Direction3.SOUTH,Direction3.EAST),
                new Position(2,0,2),new Mirror(Direction3.EAST,Direction3.NORTH),
                new Position(2,0,0),new Mirror(Direction3.NORTH,Direction3.WEST),
                START,new Mirror(Direction3.WEST,Direction3.SOUTH)));
        assertTrue(result.incomplete());
        assertTrue(result.segments().size()<PreviewOpticalNetwork3D.MAX_SEGMENTS_PER_TRACE);
        assertEquals(MeterStatus.INCOMPLETE,result.readingAt(new Position(0,0,50)).status());
    }
    @Test void sixDirectionsAndValidatedPorts() {
        for (Direction3 d:Direction3.values()) {
            Position detector=START.step(d,1);
            var result=PreviewOpticalNetwork3D.solve(List.of(new Source(START,d,SETTING)),
                    p->p.equals(detector)?new Detector(d.opposite(),1):new Air());
            assertEquals(1,result.hits().size());
            for (int turn=0;turn<4;turn++) assertTrue(d.perpendicular(d.perpendicularOutput(turn)));
        }
        assertThrows(IllegalArgumentException.class,()->new Mirror(Direction3.NORTH,Direction3.SOUTH));
    }
    @Test void invalidGeometryOrSpectrumIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new Lens(Direction3.SOUTH,0));
        assertThrows(IllegalArgumentException.class,()->new Detector(Direction3.NORTH,10));
        assertThrows(NullPointerException.class,()->new Source(START,Direction3.UP,null));
        assertThrows(IllegalArgumentException.class,()->Direction3.UP.perpendicularOutput(-1));
        assertThrows(IllegalArgumentException.class,()->new HalfWavePlate(Direction3.UP,Double.NaN));
    }
}
