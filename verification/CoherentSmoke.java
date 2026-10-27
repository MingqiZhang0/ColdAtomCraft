package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;
import java.util.List;

/** Standalone analytic smoke: no JUnit, Gradle or Minecraft dependency. */
public final class CoherentSmoke {
    private static int checks;
    private static final Vec3 ORIGIN = new Vec3(0, 0, 0);
    private static final Vec3 X = new Vec3(1, 0, 0);
    private static final Vec3 Z = new Vec3(0, 0, 1);
    private static final double LAMBDA = 780e-9;
    private static final double P = 0.02;
    private static final double W0 = 0.0005;
    private static final GaussianBeam BEAM = new GaussianBeam(P, LAMBDA, W0, ORIGIN, Z);

    private static void check(boolean valid, String what) {
        checks++;
        if (!valid) throw new AssertionError(what);
    }

    private static void near(double actual, double expected, double scale, String what) {
        check(Double.isFinite(actual) && Math.abs(actual - expected)
                <= Math.max(1e-13, Math.abs(scale) * 2e-12), what);
    }

    private static JonesGaussianBeam source(String name, JonesVector pol, double phase) {
        return new JonesGaussianBeam(name, "one-phase-reference", BEAM, X, pol, phase);
    }

    public static void main(String[] args) {
        var first = source("arm-a", JonesVector.horizontal(), 0);
        var same = source("arm-b", JonesVector.horizontal(), 0);
        var opposite = source("arm-c", JonesVector.horizontal(), Math.PI);
        var vertical = source("arm-d", JonesVector.vertical(), 0);
        var quadrature = source("arm-e", JonesVector.horizontal(), Math.PI / 2);
        double i = BEAM.onAxisIntensityWattsPerSquareMeter(0);
        near(first.sampleAtLabPoint(ORIGIN).electricIntensityEquivalentWattsPerSquareMeter(),i,i,"one-beam intensity");
        near(first.transmittedPowerWatts(),P,P,"one-beam power");
        near(CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,same))
                .electricIntensityEquivalentWattsPerSquareMeter(),4*i,i,"constructive");
        near(CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,opposite))
                .electricIntensityEquivalentWattsPerSquareMeter(),0,i,"destructive");
        near(CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,vertical))
                .electricIntensityEquivalentWattsPerSquareMeter(),2*i,i,"orthogonal");
        near(CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,quadrature))
                .electricIntensityEquivalentWattsPerSquareMeter(),2*i,i,"pi/2 phase");
        near(CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,same))
                .timeAveragePoyntingWattsPerSquareMeter().z(),4*i,i,"co-prop flux");
        var pol45=JonesMatrix.idealLinearPolarizer(Math.PI/4).apply(JonesVector.horizontal());
        near(pol45.squaredNorm(),0.5,1,"Malus law");
        near(source("after-pol",pol45,0).transmittedPowerWatts(),P/2,P,"polarized power");
        near(JonesMatrix.quarterWave(0).apply(JonesVector.linearRadians(Math.PI/4))
                .squaredNorm(),1,1,"QWP lossless");
        near(JonesMatrix.halfWave(Math.PI/8).apply(JonesVector.horizontal()).y().real(),
                Math.sqrt(0.5),1,"HWP rotates");
        JonesGaussianBeam backward=new JonesGaussianBeam("counter","one-phase-reference",
                new GaussianBeam(P,LAMBDA,W0,ORIGIN,new Vec3(0,0,-1)),X,
                JonesVector.horizontal(),0);
        var standing=CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,backward));
        near(standing.electricIntensityEquivalentWattsPerSquareMeter(),4*i,i,"standing antinode");
        near(standing.timeAveragePoyntingWattsPerSquareMeter().magnitude(),0,i,"zero standing net flux");
        double z=LAMBDA/4;
        var nearNode=CoherentOptics.coherentAtLabPoint(new Vec3(0,0,z),List.of(first,backward));
        double gouy=BEAM.gouyPhaseRadians(z);
        near(nearNode.electricIntensityEquivalentWattsPerSquareMeter(),
                4*BEAM.onAxisIntensityWattsPerSquareMeter(z)*Math.sin(gouy)*Math.sin(gouy),
                4*BEAM.onAxisIntensityWattsPerSquareMeter(z)*Math.sin(gouy)*Math.sin(gouy),
                "Gaussian Gouy shifts plane-wave node");
        boolean rejected=false;
        try { CoherentOptics.coherentAtLabPoint(ORIGIN,List.of(first,first)); }
        catch(IllegalArgumentException expected){ rejected=true; }
        check(rejected,"no duplicate beam physical ID");
        rejected=false;
        try { new JonesGaussianBeam("wrong","one-phase-reference",BEAM,Z,JonesVector.horizontal(),0); }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"transverse basis must be orthogonal");
        rejected=false;
        try { new JonesGaussianBeam("gain","one-phase-reference",BEAM,X,
                    JonesVector.horizontal().scale(2),0); }
        catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,"gain cannot enter passive reference");
        near(ComplexAmplitude.I.times(ComplexAmplitude.I).real(),-1,1,"complex multiply");
        near(ComplexAmplitude.fromPhaseRadians(Math.PI).magnitudeSquared(),1,1,"complex unit phase");
        System.out.println("PASS: " + checks + " D6 coherent optics smoke checks; pure Java draft reference");
    }
}
