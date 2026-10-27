package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.optics.*;

/** Standalone D7 optical path checks; dependency-free, not formal JUnit. */
public final class OpticalPathSmoke {
    private static int checks;
    private static final double LAMBDA=780e-9, W0=500e-6, P=0.02;

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static void near(double actual,double expected,String label) {
        check(Math.abs(actual-expected)<1e-11*Math.max(1,Math.abs(expected)),label);
    }

    private static ParaxialModeField input() {
        return ParaxialModeField.fromPlane("laser","matched-port-frame",
                new GaussianPlane(P,LAMBDA,new GaussianQ(0,Math.PI*W0*W0/LAMBDA)),
                JonesVector.horizontal(),0);
    }

    public static void main(String[] args) {
        var first=input();
        var traveled=first.freeSpace(0.125);
        near(traveled.powerWatts(),P,"vacuum power");
        near(traveled.q().realMeters(),0.125,"free space q");
        near(traveled.q().imaginaryMeters(),first.q().imaginaryMeters(),"Rayleigh range");
        near(traveled.fieldAtRadiusMeters(W0/5).squaredNorm(),
                traveled.gaussianPlane().intensityWattsPerSquareMeter(W0/5),"field norm");
        check(traveled.inputPathUlpPhaseRadians(0.125)>0,"phase path length diagnostic");
        var lens=traveled.thinLens(0.2,0);
        near(lens.powerWatts(),P,"lens power");
        near(lens.gaussianPlane().radiusMeters(),traveled.gaussianPlane().radiusMeters(),"lens spot at plane");
        var split=new IdealTwoPortBeamSplitter(0.5).split(first);
        near(split.totalPowerWatts(),P,"both splitter outputs budget");
        near(split.output0().powerWatts(),P/2,"first arm power");
        near(split.output1().powerWatts(),P/2,"second arm power");
        var recombined=new IdealTwoPortBeamSplitter(0.5)
                .scatter(split.output0().freeSpace(0.125),split.output1().freeSpace(0.125));
        near(recombined.output0().powerWatts(),0,"Mach-Zehnder dark port");
        near(recombined.output1().powerWatts(),P,"Mach-Zehnder bright port");
        var switched=new IdealTwoPortBeamSplitter(0.5)
                .scatter(split.output0(),split.output1().withUniformPhaseRadians(Math.PI));
        near(switched.output0().powerWatts(),P,"phase pi switches bright port");
        near(switched.output1().powerWatts(),0,"phase pi switches dark port");
        var path=OpticalPathTrace.start(first).freeSpace(0.1).thinLens(0.2,0)
                .passiveJones(JonesMatrix.idealLinearPolarizer(Math.PI/4));
        near(path.current().powerWatts(),P/2,"Malus law on path");
        near(path.totalRemovedFromTrackedModeWatts(),P/2,"tracked mode budget");
        check(path.steps().size()==3,"trace length");
        boolean rejected=false;
        try { new IdealTwoPortBeamSplitter(0.5).scatter(first,traveled); }
        catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"unmatched Gaussian q rejected");
        rejected=false;
        try {first.freeSpace(-1);}
        catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"negative propagation rejected");
        System.out.println("PASS: " + checks + " D7 optical-path smoke checks (NOT JUnit / NeoForge)");
    }
}
