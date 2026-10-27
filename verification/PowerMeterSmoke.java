package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.IdealOpticalPowerMeter;
import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;

/** Standalone scientific smoke, not JUnit or Minecraft runtime. */
public final class PowerMeterSmoke {
    private static int checks;
    private PowerMeterSmoke() {}

    private static void check(boolean condition,String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void close(double expected,double actual,double abs,String message) {
        checks++;
        if (!Double.isFinite(actual)||Math.abs(expected-actual)>abs)
            throw new AssertionError(message+": expected="+expected+" actual="+actual);
    }

    public static void main(String[] args) {
        double power = 0.02, w0 = 0.0005, lambda=780e-9;
        var beam=new GaussianBeam(power,lambda,w0,new Vec3(0,0,0),new Vec3(0,0,1));
        var waist=GaussianPlane.atAxialDistance(beam,0);
        close(1-Math.exp(-2),GaussianCircularAperture.capturedFraction(w0,w0,0),1e-14,"centered disk");
        var matched=GaussianCircularAperture.capture(waist,w0,0);
        close(power*(1-Math.exp(-2)),matched.capturedPowerWatts(),1e-14,"captured watts");
        close(power,matched.capturedPowerWatts()+matched.uncapturedPowerWatts(),1e-14,"budget");
        var offset=GaussianCircularAperture.capture(waist,w0,w0);
        check(offset.capturedPowerWatts()<matched.capturedPowerWatts(),"misalignment");
        var far=new IdealCircularDetectorFace(new Vec3(0,0,4),new Vec3(0,0,-1),w0);
        check(far.capture(beam).capturedPowerWatts()<matched.capturedPowerWatts(),"diffractive widening");
        var face=new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-1),w0);
        close(matched.capturedPowerWatts(),face.capture(beam).capturedPowerWatts(),1e-14,"3D face");
        var meter=new IdealOpticalPowerMeter("D9-meter",w0);
        var mode=ParaxialModeField.fromPlane("phase-reference","port-frame",waist,
                new JonesVector(ComplexAmplitude.ONE,ComplexAmplitude.ZERO),0);
        var reading=meter.sampleMode(mode,0,123);
        close(matched.capturedPowerWatts(),reading.observation().value(),1e-14,"mode readout");
        check("W".equals(reading.observation().siUnit()),"SI units");
        check("ideal_collected_optical_power".equals(reading.observation().observable()),"explicit ideal observable");
        check(reading.observation().simulatedTimeNanos()==123,"measurement timestamp");
        close(power,mode.powerWatts(),1e-14,"noninvasive probe");
        close(reading.observation().value(),meter.sampleAtFace(beam,face,0).observation().value(),
                1e-14,"mode and geometry readout");
        var splitter=new IdealTwoPortBeamSplitter(0.5);
        var arms=splitter.split(mode);
        var out=splitter.scatter(arms.output0(),arms.output1());
        var a=meter.sampleMode(out.output0(),0,0);
        var b=meter.sampleMode(out.output1(),0,0);
        close(reading.observation().value(),a.observation().value()+b.observation().value(),
                1e-13,"MZI power budgets");
        check(a.observation().value()<b.observation().value(),"MZI dark/bright ports");
        var phaseOut=splitter.scatter(arms.output0(),arms.output1().withUniformPhaseRadians(Math.PI));
        check(meter.sampleMode(phaseOut.output0(),0,0).observation().value()>
                meter.sampleMode(phaseOut.output1(),0,0).observation().value(),"MZI phase reversal");
        try {
            new IdealCircularDetectorFace(new Vec3(0,0,1),new Vec3(0,0,1),w0).capture(beam);
            throw new AssertionError("backside detector accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            meter.sampleMode(mode,Double.NaN,0);
            throw new AssertionError("nonfinite offset accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        System.out.printf("PASS: %d/%d standalone D9 optical power checks; NOT JUnit%n",checks,checks);
    }
}
