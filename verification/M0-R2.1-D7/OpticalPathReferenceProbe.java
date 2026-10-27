import dev.coldatomcraft.physics.optics.*;

/** Print actual Java scalars for an independent Decimal optical-path oracle. */
public final class OpticalPathReferenceProbe {
    private static final double LAMBDA=780e-9, W0=500e-6, POWER=0.02;
    private static void emit(String label, double... values) {
        System.out.print(label);
        for (double value:values) System.out.print(","+Double.toString(value));
        System.out.println();
    }
    private static ParaxialModeField input() {
        var q=new GaussianQ(0, Math.PI*W0*W0/LAMBDA);
        return ParaxialModeField.fromPlane("one-real-oscillator","aligned-transverse-mode",
                new GaussianPlane(POWER,LAMBDA,q),JonesVector.horizontal(),0);
    }
    public static void main(String[] args) {
        var beginning=input();
        var propagated=beginning.freeSpace(0.125);
        var a=propagated.modalAmplitudeSqrtWatts().x();
        emit("free",propagated.q().realMeters(),propagated.q().imaginaryMeters(),
                propagated.powerWatts(),a.real(),a.imaginary(),
                propagated.fieldAtRadiusMeters(0.0001).squaredNorm());

        var afterLens=beginning.freeSpace(0.15).thinLens(0.2,0);
        var l=afterLens.modalAmplitudeSqrtWatts().x();
        emit("lens",afterLens.q().realMeters(),afterLens.q().imaginaryMeters(),
                l.real(),l.imaginary(),afterLens.fieldAtRadiusMeters(0.0001).squaredNorm());

        var splitter=new IdealTwoPortBeamSplitter(0.3);
        var split=splitter.split(beginning);
        emit("split",split.output0().powerWatts(),split.output1().powerWatts(),
                split.output0().modalAmplitudeSqrtWatts().x().real(),
                split.output1().modalAmplitudeSqrtWatts().x().imaginary(),
                split.totalPowerWatts());

        var balanced=new IdealTwoPortBeamSplitter(0.5);
        var arms=balanced.split(beginning);
        var interferometer=balanced.scatter(arms.output0().freeSpace(0.125),
                arms.output1().freeSpace(0.125).withUniformPhaseRadians(Math.PI/3));
        var m0=interferometer.output0().modalAmplitudeSqrtWatts().x();
        var m1=interferometer.output1().modalAmplitudeSqrtWatts().x();
        emit("mach",interferometer.output0().powerWatts(),
                interferometer.output1().powerWatts(),m0.real(),m0.imaginary(),
                m1.real(),m1.imaginary(),interferometer.totalPowerWatts());

        var diag=ParaxialModeField.fromPlane("one-real-oscillator","aligned-transverse-mode",
                beginning.gaussianPlane(),JonesVector.linearRadians(Math.PI/4),0);
        var qwp=diag.throughPassiveJones(JonesMatrix.quarterWave(0));
        emit("qwp",qwp.powerWatts(),qwp.modalAmplitudeSqrtWatts().x().real(),
                qwp.modalAmplitudeSqrtWatts().y().imaginary());

        var path=OpticalPathTrace.start(beginning).freeSpace(0.1)
                .thinLens(0.2,0).passiveJones(JonesMatrix.idealLinearPolarizer(Math.PI/4));
        emit("trace",path.steps().size(),path.current().powerWatts(),
                path.totalRemovedFromTrackedModeWatts());
    }
}
