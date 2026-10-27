package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpticalPathTraceTest {
    private static void close(double a,double b) {
        assertEquals(a,b,1e-11*Math.max(1,Math.abs(a)));
    }
    @Test void orderedOpticalTrainPreservesHistoryAndPowerBudget() {
        var base=OpticalPathTrace.start(ParaxialModeFieldTest.start());
        var train=base.freeSpace(0.1).thinLens(0.3,0)
                .passiveJones(JonesMatrix.idealLinearPolarizer(Math.PI/4))
                .freeSpace(0.2).idealMirrorPhaseOnly(0.3).phaseShift(-0.1);
        assertEquals(0,base.steps().size());
        assertEquals(6,train.steps().size());
        close(0.02,train.steps().get(0).input().powerWatts());
        close(0.01,train.current().powerWatts());
        close(0.01,train.totalRemovedFromTrackedModeWatts());
        assertEquals("MIRROR_PHASE_PROXY",train.steps().get(4).operation());
        assertThrows(UnsupportedOperationException.class,()->train.steps().clear());
    }
    @Test void idealOpticsDoNotRemovePowerFromTheTrackedMode() {
        var trace=OpticalPathTrace.start(ParaxialModeFieldTest.start())
                .freeSpace(0.25).thinLens(0.4,0)
                .passiveJones(JonesMatrix.quarterWave(0))
                .idealMirrorPhaseOnly(Math.PI/3);
        close(0,trace.totalRemovedFromTrackedModeWatts());
        close(0.02,trace.current().powerWatts());
    }
    @Test void arbitraryJonesGainIsRejectedNotCountedAsNegativeLoss() {
        var trace=OpticalPathTrace.start(ParaxialModeFieldTest.start());
        var gain=new JonesMatrix(new ComplexAmplitude(3,0),ComplexAmplitude.ZERO,
                ComplexAmplitude.ZERO,ComplexAmplitude.ONE);
        assertThrows(IllegalArgumentException.class,()->trace.passiveJones(gain));
    }
    @Test void twoPortSplitterIsDeliberatelySeparateFromSingleBranchTrace() {
        var arms=new IdealTwoPortBeamSplitter(0.5)
                .split(OpticalPathTrace.start(ParaxialModeFieldTest.start()).current());
        var branch0=OpticalPathTrace.start(arms.output0()).freeSpace(0.1);
        var branch1=OpticalPathTrace.start(arms.output1()).freeSpace(0.1);
        var out=new IdealTwoPortBeamSplitter(0.5).scatter(branch0.current(),branch1.current());
        close(0.02,out.totalPowerWatts());
    }
}
