package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdealTwoPortBeamSplitterTest {
    private static final double P=0.02;
    private static ParaxialModeField input() { return ParaxialModeFieldTest.start(); }
    private static void close(double a, double b) {
        assertEquals(a,b,Math.max(1e-13,Math.abs(a)*1e-11));
    }

    @Test void onePortSplitConservesPowerAtEveryTransmission() {
        for (double t:new double[]{0,0.01,0.25,0.5,0.8,0.999,1.0}) {
            var ports=new IdealTwoPortBeamSplitter(t).split(input());
            close(P*t,ports.output0().powerWatts());
            close(P*(1-t),ports.output1().powerWatts());
            close(P,ports.totalPowerWatts());
        }
    }

    @Test void splitterReflectionIsInQuadratureWithTransmission() {
        var ports=new IdealTwoPortBeamSplitter(0.5).split(input());
        close(Math.sqrt(P/2),ports.output0().modalAmplitudeSqrtWatts().x().real());
        close(0,ports.output0().modalAmplitudeSqrtWatts().x().imaginary());
        close(0,ports.output1().modalAmplitudeSqrtWatts().x().real());
        close(Math.sqrt(P/2),ports.output1().modalAmplitudeSqrtWatts().x().imaginary());
    }

    @Test void phaseDependentTwoPortInterferencePreservesTotalPower() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        for (double delta:new double[]{0,Math.PI/7,Math.PI/3,Math.PI/2,Math.PI}) {
            var second=input().withUniformPhaseRadians(delta);
            var ports=splitter.scatter(input(),second);
            close(2*P,ports.totalPowerWatts());
            close(P*(1-Math.sin(delta)),ports.output0().powerWatts());
            close(P*(1+Math.sin(delta)),ports.output1().powerWatts());
        }
    }

    @Test void balancedMachZehnderRoutesAllPowerToOnePortWhenArmsMatch() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var arms=splitter.split(input());
        var output=splitter.scatter(arms.output0().freeSpace(0.2),
                arms.output1().freeSpace(0.2));
        close(0,output.output0().powerWatts());
        close(P,output.output1().powerWatts());
        close(P,output.totalPowerWatts());
    }

    @Test void machZehnderPhaseShiftMovesBrightPortContinuously() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var arms=splitter.split(input());
        for(double delta:new double[]{0,Math.PI/6,Math.PI/3,Math.PI/2,Math.PI}) {
            var out=splitter.scatter(arms.output0().freeSpace(0.2),
                    arms.output1().freeSpace(0.2).withUniformPhaseRadians(delta));
            close(P*Math.pow(Math.sin(delta/2),2),out.output0().powerWatts());
            close(P*Math.pow(Math.cos(delta/2),2),out.output1().powerWatts());
            close(P,out.totalPowerWatts());
        }
    }

    @Test void twoPortComplexJonesPolarizationIsMixedWithoutFlattening() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var h=input();
        var v=h.withModalAmplitude(new JonesVector(ComplexAmplitude.ZERO,
                h.modalAmplitudeSqrtWatts().x()));
        var outputs=splitter.scatter(h,v);
        close(P,outputs.output0().powerWatts());
        close(P,outputs.output1().powerWatts());
        close(2*P,outputs.totalPowerWatts());
        close(0,outputs.output0().modalAmplitudeSqrtWatts().x().imaginary());
        close(Math.sqrt(P/2),outputs.output0().modalAmplitudeSqrtWatts().y().imaginary());
    }

    @Test void mismatchedQCannotBeSilentlyCollapsedIntoSingleGaussian() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        assertThrows(IllegalArgumentException.class,()->splitter.scatter(input(),
                input().freeSpace(0.001)));
        assertThrows(IllegalArgumentException.class,()->splitter.scatter(input(),
                input().thinLens(0.25,0)));
    }

    @Test void incoherentPhaseReferenceCannotBeCombinedAsCoherentFields() {
        var b=input();
        var other=new ParaxialModeField("different-source",b.modeFrameKey(),
                b.vacuumWavelengthMeters(),b.q(),b.modalAmplitudeSqrtWatts());
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(0.5)
                .scatter(b,other));
    }

    @Test void differentModeFramesAndCarrierWavelengthMustBeRejected() {
        var b=input();
        var frame=new ParaxialModeField(b.phaseReferenceKey(),"different-frame",
                b.vacuumWavelengthMeters(),b.q(),b.modalAmplitudeSqrtWatts());
        var wavelength=new ParaxialModeField(b.phaseReferenceKey(),b.modeFrameKey(),
                781e-9,b.q(),b.modalAmplitudeSqrtWatts());
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        assertThrows(IllegalArgumentException.class,()->splitter.scatter(b,frame));
        assertThrows(IllegalArgumentException.class,()->splitter.scatter(b,wavelength));
    }

    @Test void twoIndependentPhysicalSourcesAreNotAutomaticallyCoherent() {
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var b=input();
        var outside=new ParaxialModeField("independent-laser",b.modeFrameKey(),
                b.vacuumWavelengthMeters(),b.q(),b.modalAmplitudeSqrtWatts());
        assertThrows(IllegalArgumentException.class,()->splitter.scatter(b,outside));
    }

    @Test void deterministicComplexPolarizationStressConservesTwoPortPower() {
        var random=new java.util.Random(20261026L);
        var standard=input();
        for (int i=0;i<400;i++) {
            var a=new JonesVector(new ComplexAmplitude(random.nextDouble(),random.nextDouble()),
                    new ComplexAmplitude(random.nextDouble(),random.nextDouble()));
            var b=new JonesVector(new ComplexAmplitude(random.nextDouble(),random.nextDouble()),
                    new ComplexAmplitude(random.nextDouble(),random.nextDouble()));
            var left=standard.withModalAmplitude(a);
            var right=standard.withModalAmplitude(b);
            var result=new IdealTwoPortBeamSplitter(random.nextDouble()).scatter(left,right);
            close(left.powerWatts()+right.powerWatts(),result.totalPowerWatts());
        }
    }

    @Test void nonBalancedMachZehnderStillConservesBothOutputs() {
        var splitter=new IdealTwoPortBeamSplitter(0.3);
        var arms=splitter.split(input());
        var output=splitter.scatter(arms.output0(),arms.output1());
        close(P*0.16,output.output0().powerWatts());
        close(P*0.84,output.output1().powerWatts());
        close(P,output.totalPowerWatts());
    }

    @Test void noNegativeTransmissionOrNullInputIsAccepted() {
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(-0.001));
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(1.001));
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(0.5).split(null));
        assertThrows(IllegalArgumentException.class,()->new IdealTwoPortBeamSplitter(0.5).scatter(input(),null));
    }
}
