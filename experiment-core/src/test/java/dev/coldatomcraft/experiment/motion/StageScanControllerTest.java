package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StageScanControllerTest {
    private static final StageScanPlan.Axis AXIS = new StageScanPlan.Axis(
            StageScanPlan.Unit.NANOMETRE, -10_000, 10_000, 5);
    private static StageScanPlan plan(List<Long> targets, int settle, int moveTimeout,
                                      int readTimeout, int spacing, int count) {
        return new StageScanPlan(AXIS, targets, settle, moveTimeout, readTimeout, spacing, count);
    }
    private static StageScanController.Feedback good(long pos) {
        return new StageScanController.Feedback("device:trusted", 7, pos, true, true, true);
    }
    private static StageScanController.Decision step(StageScanController c, long tick, long pos) {
        return c.advance(tick, good(pos), null);
    }
    private static PowerTrace.Sample measured(long tick, double watts) {
        return new PowerTrace.Sample(tick, PowerTrace.Status.MEASURED, watts);
    }
    private static void expect(StageScanController.Action action, StageScanController.Decision result) {
        assertEquals(action, result.action());
    }

    @Test void validOnePointScanOnlyAfterSettlingAndRequestedRead() {
        var controller = new StageScanController(plan(List.of(100L), 3, 20, 4, 1, 1));
        expect(StageScanController.Action.MOVE, step(controller, 100, 0));
        expect(StageScanController.Action.WAIT, step(controller, 101, 60));
        expect(StageScanController.Action.WAIT, step(controller, 102, 96));
        expect(StageScanController.Action.WAIT, step(controller, 103, 100));
        expect(StageScanController.Action.WAIT, step(controller, 104, 101));
        expect(StageScanController.Action.READ, step(controller, 105, 101));
        expect(StageScanController.Action.FINISHED, controller.advance(106, good(102), measured(106, .012)));
        assertEquals(StageScanController.Phase.COMPLETE, controller.phase());
        var point = controller.completedPoints().getFirst();
        assertEquals(100, point.commandedUnits());
        assertEquals(102, point.readings().getFirst().actualUnits());
        assertEquals(.012, point.meanWatts().orElseThrow());
    }

    @Test void settleCounterMustRestartAfterDrift() {
        var controller = new StageScanController(plan(List.of(100L), 3, 20, 2, 1, 1));
        step(controller, 0, 0);
        step(controller, 1, 100);
        step(controller, 2, 90);
        step(controller, 3, 100);
        step(controller, 4, 100);
        assertEquals(StageScanController.Phase.SAMPLING, step(controller, 5, 100).phase());
        expect(StageScanController.Action.READ, step(controller, 6, 100));
    }

    @Test void invalidOpticalSampleIsPreservedAndNeverAveragedAsZero() {
        var controller = new StageScanController(plan(List.of(5L), 1, 5, 3, 1, 2));
        step(controller, 0, 0);
        step(controller, 1, 5);
        expect(StageScanController.Action.READ, step(controller, 2, 5));
        step(controller, 3, 5); // missing reading, request still outstanding
        expect(StageScanController.Action.WAIT,
                controller.advance(4, good(5), new PowerTrace.Sample(4, PowerTrace.Status.NO_BEAM, 0)));
        expect(StageScanController.Action.READ, step(controller, 5, 5));
        expect(StageScanController.Action.FINISHED, controller.advance(6, good(5), measured(6, .1)));
        var point = controller.completedPoints().getFirst();
        assertTrue(point.meanWatts().isEmpty());
        String csv = StageScanDataset.csv(StageScanPlan.Unit.NANOMETRE, controller.completedPoints());
        assertTrue(csv.contains("4,NO_BEAM,\n"));
        assertTrue(csv.contains("6,MEASURED,0.1\n"));
        assertFalse(csv.contains("4,NO_BEAM,0"));
    }

    @Test void noSampleBeforeRequestedReadIsAccepted() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        expect(StageScanController.Action.STOP, controller.advance(0, good(0), measured(0, .1)));
        assertEquals(StageScanController.Phase.ABORTED, controller.phase());
    }
    @Test void readReplyCannotBeReturnedInSameTickOrStaleTick() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        step(controller, 0, 0);
        step(controller, 1, 0);
        step(controller, 2, 0);
        expect(StageScanController.Action.STOP, controller.advance(3, good(0), measured(2, .1)));
    }
    @Test void aMissingServerTickAbortsAndNeverResumes() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        step(controller, 12, 0);
        expect(StageScanController.Action.STOP, step(controller, 14, 0));
        expect(StageScanController.Action.STOP, step(controller, 15, 0));
        assertTrue(controller.failureReason().contains("tick"));
    }
    @Test void repeatedServerTickIsRejected() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        step(controller, 12, 0);
        expect(StageScanController.Action.STOP, step(controller, 12, 0));
    }
    @Test void deviceReplacementIsRejected() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        step(controller, 0, 0);
        expect(StageScanController.Action.STOP, controller.advance(1,
                new StageScanController.Feedback("other",7,0,true,true,true), null));
    }
    @Test void configurationChangeIsRejected() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
        step(controller, 0, 0);
        expect(StageScanController.Action.STOP, controller.advance(1,
                new StageScanController.Feedback("device:trusted",8,0,true,true,true), null));
    }
    @Test void unloadedOrInterlockFailureAborts() {
        for (boolean[] statuses : List.of(new boolean[]{false,true},new boolean[]{true,false})) {
            var controller = new StageScanController(plan(List.of(0L), 1, 8, 3, 1, 1));
            step(controller, 0, 0);
            expect(StageScanController.Action.STOP, controller.advance(1,
                    new StageScanController.Feedback("device:trusted",7,0,statuses[0],statuses[1],true),null));
        }
    }
    @Test void motionTimeoutIsFailClosed() {
        var controller = new StageScanController(plan(List.of(100L), 2, 2, 3, 1, 1));
        step(controller, 0, 0);
        step(controller, 1, 0);
        step(controller, 2, 0);
        expect(StageScanController.Action.STOP, step(controller, 3, 0));
    }
    @Test void readTimeoutIsFailClosed() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 2, 1, 1));
        step(controller, 0, 0); step(controller, 1, 0); step(controller, 2, 0);
        step(controller, 3, 0); step(controller, 4, 0);
        expect(StageScanController.Action.STOP, step(controller, 5, 0));
    }
    @Test void positionDriftDuringReadAbortsWithoutFabricatingAResult() {
        var controller = new StageScanController(plan(List.of(0L), 1, 8, 4, 1, 1));
        step(controller, 0, 0); step(controller, 1, 0); step(controller, 2, 0);
        expect(StageScanController.Action.STOP, controller.advance(3, good(15), measured(3,.1)));
        assertTrue(controller.completedPoints().isEmpty());
    }
    @Test void nextMoveWaitsForAllSamples() {
        var controller = new StageScanController(plan(List.of(0L, 50L), 1, 12, 4, 2, 2));
        step(controller, 0, 0); step(controller, 1, 0);
        expect(StageScanController.Action.READ, step(controller, 2, 0));
        expect(StageScanController.Action.WAIT, controller.advance(3,good(0),measured(3,.1)));
        expect(StageScanController.Action.WAIT, step(controller, 4, 0));
        expect(StageScanController.Action.READ, step(controller, 5, 0));
        expect(StageScanController.Action.MOVE, controller.advance(6,good(0),measured(6,.2)));
        assertEquals(1, controller.currentPointIndex());
        assertEquals(1, controller.completedPoints().size());
        assertEquals(.15, controller.completedPoints().getFirst().meanWatts().orElseThrow(), 1e-14);
        assertEquals(50, controller.advance(7,good(50),null).targetUnits());
    }
    @Test void planRejectsTargetsOutsideAxisLimits() {
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(10_001L), 1, 10, 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(), 1, 10, 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(0L), 0, 10, 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StageScanPlan.Axis(
                StageScanPlan.Unit.NANORADIAN,Long.MIN_VALUE,Long.MAX_VALUE,0));
    }
    @Test void datasetRejectsInvalidTickOrderOrOverflowEncoder() {
        assertThrows(IllegalArgumentException.class, () -> new StageScanDataset.Reading(Long.MIN_VALUE,measured(1, .1)));
        assertThrows(IllegalArgumentException.class, () -> new StageScanDataset.Point(0, 0,
                List.of(new StageScanDataset.Reading(0,measured(3,.1)),new StageScanDataset.Reading(0,measured(2,.1)))));
        assertThrows(IllegalArgumentException.class, () -> StageScanDataset.validate(List.of(
                new StageScanDataset.Point(1, 0,List.of(new StageScanDataset.Reading(0,measured(2,.1)))))));
    }
    @Test void motorMustReportStoppedBeforeSettlingOrMeasuring() {
        var controller = new StageScanController(plan(List.of(100L), 1, 8, 3, 1, 1));
        step(controller,0,0);
        assertEquals(StageScanController.Phase.MOVING, controller.advance(1,
                new StageScanController.Feedback("device:trusted",7,100,true,true,false),null).phase());
        assertEquals(StageScanController.Phase.SAMPLING, step(controller,2,100).phase());
        expect(StageScanController.Action.READ, step(controller,3,100));
        expect(StageScanController.Action.STOP, controller.advance(4,
                new StageScanController.Feedback("device:trusted",7,100,true,true,false),measured(4,.1)));
        assertTrue(controller.completedPoints().isEmpty());
    }
    @Test void hugeButFiniteMeasuredPowerDoesNotOverflowSvgPlot() {
        var points = List.of(new StageScanDataset.Point(0, 0,
                List.of(new StageScanDataset.Reading(0, measured(1,Double.MAX_VALUE)))));
        var svg = StageScanDataset.svg(StageScanPlan.Unit.NANOMETRE,points);
        assertFalse(svg.contains("Infinity"));
        assertFalse(svg.contains("NaN"));
        assertTrue(svg.contains("Power (W)"));
    }
    @Test void svgLeavesGapsAtInvalidPoints() {
        ArrayList<StageScanDataset.Point> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            var observation = i == 2 ? new PowerTrace.Sample(i,PowerTrace.Status.NO_BEAM,0)
                    : measured(i,.01 + i*.01);
            points.add(new StageScanDataset.Point(i, i*10,
                    List.of(new StageScanDataset.Reading(i*10,observation))));
        }
        String svg=StageScanDataset.svg(StageScanPlan.Unit.NANORADIAN, points);
        assertEquals(2, svg.split("<polyline",-1).length-1);
        assertFalse(svg.contains("NaN"));
        assertTrue(svg.contains("Encoder position (nrad)"));
    }
}
