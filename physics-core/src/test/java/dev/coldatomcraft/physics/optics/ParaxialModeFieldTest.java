package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParaxialModeFieldTest {
    private static final double LAMBDA=780e-9, W0=500e-6, POWER=0.02;
    static ParaxialModeField start() {
        var q = new GaussianQ(0,Math.PI*W0*W0/LAMBDA);
        return ParaxialModeField.fromPlane("source-1","mode-frame-1",
                new GaussianPlane(POWER,LAMBDA,q),JonesVector.horizontal(),0);
    }
    private static void close(double a,double b) {
        assertEquals(a,b,1e-11*Math.max(1,Math.abs(a)));
    }
    private static void closeComplex(ComplexAmplitude a,ComplexAmplitude b) {
        close(a.real(),b.real());close(a.imaginary(),b.imaginary());
    }

    @Test void constructorPreservesPowerAndPhaseAndNeedsNormalizedJones() {
        var initial=start();
        close(POWER,initial.powerWatts());
        close(POWER,initial.gaussianPlane().powerWatts());
        assertThrows(IllegalArgumentException.class,()->ParaxialModeField.fromPlane(
                "x","frame",initial.gaussianPlane(),JonesVector.horizontal().scale(2),0));
        assertThrows(IllegalArgumentException.class,()->new ParaxialModeField(
                "","frame",LAMBDA,initial.q(),initial.modalAmplitudeSqrtWatts()));
        assertThrows(IllegalArgumentException.class,()->new ParaxialModeField(
                "source","",LAMBDA,initial.q(),initial.modalAmplitudeSqrtWatts()));
        assertThrows(IllegalArgumentException.class,()->new ParaxialModeField(
                "source","frame",-1,initial.q(),initial.modalAmplitudeSqrtWatts()));
    }

    @Test void freeSpaceRetainsPowerAndTransportsGaussianQ() {
        var initial=start();double distance=0.35;
        var advanced=initial.freeSpace(distance);
        close(initial.powerWatts(),advanced.powerWatts());
        close(distance,advanced.q().realMeters());
        close(initial.q().imaginaryMeters(),advanced.q().imaginaryMeters());
        close(initial.gaussianPlane().through(ParaxialAbcd.freeSpace(distance)).radiusMeters(),
                advanced.gaussianPlane().radiusMeters());
    }

    @Test void freeSpaceAddsCarrierMinusGouyPhase() {
        var initial=start();double distance=0.25;
        var advanced=initial.freeSpace(distance);
        double phase=2*Math.PI*(Math.IEEEremainder(distance,LAMBDA)/LAMBDA)
                - Math.atan(distance/initial.q().imaginaryMeters());
        ComplexAmplitude expected=ComplexAmplitude.fromPhaseRadians(phase).scale(Math.sqrt(POWER));
        closeComplex(expected,advanced.modalAmplitudeSqrtWatts().x());
        close(0,advanced.modalAmplitudeSqrtWatts().y().magnitudeSquared());
    }

    @Test void sequentialFreeSpaceAgreesWithSingleEquivalentPropagation() {
        var initial=start();
        var serial=initial.freeSpace(0.125).freeSpace(0.125);
        var combined=initial.freeSpace(0.25);
        close(serial.q().realMeters(),combined.q().realMeters());
        close(serial.q().imaginaryMeters(),combined.q().imaginaryMeters());
        closeComplex(serial.modalAmplitudeSqrtWatts().x(),combined.modalAmplitudeSqrtWatts().x());
    }

    @Test void gaussianPlaneFieldModulusMatchesIrradianceAtAnyRadius() {
        var advanced=start().freeSpace(0.12);
        double w=advanced.q().radiusMeters(LAMBDA);
        for (double r:new double[]{0,w/5,w,3*w}) {
            var sample=advanced.fieldAtRadiusMeters(r);
            close(advanced.gaussianPlane().intensityWattsPerSquareMeter(r),
                    sample.squaredNorm());
        }
    }

    @Test void thinLensDoesNotInventOnAxisPhaseOrPowerAndChangesCurvature() {
        var before=start().freeSpace(0.09);
        var after=before.thinLens(0.2,0);
        close(before.powerWatts(),after.powerWatts());
        close(before.gaussianPlane().radiusMeters(),after.gaussianPlane().radiusMeters());
        closeComplex(before.fieldAtRadiusMeters(0).x(),after.fieldAtRadiusMeters(0).x());
        double r=W0*0.5;
        double lensPhase=-Math.PI*r*r/(LAMBDA*0.2);
        closeComplex(before.fieldAtRadiusMeters(r).x().times(
                ComplexAmplitude.fromPhaseRadians(lensPhase)),after.fieldAtRadiusMeters(r).x());
    }

    @Test void lensRequiresSuppliedCommonPhaseWhenInterfering() {
        var before=start();
        var normal=before.thinLens(0.3,0);
        var calibrated=before.thinLens(0.3,Math.PI);
        close(normal.powerWatts(),calibrated.powerWatts());
        closeComplex(normal.modalAmplitudeSqrtWatts().x().scale(-1),
                calibrated.modalAmplitudeSqrtWatts().x());
    }

    @Test void passivePolarizerReducesTrackedModalPower() {
        var before=start();
        var after=before.throughPassiveJones(JonesMatrix.idealLinearPolarizer(Math.PI/4));
        close(POWER*0.5,after.powerWatts());
    }

    @Test void quarterWaveAndHalfWaveKeepPowerAndRetainRelativePhase() {
        var initial=ParaxialModeField.fromPlane("ref","frame",start().gaussianPlane(),
                JonesVector.linearRadians(Math.PI/4),0);
        var qwp=initial.throughPassiveJones(JonesMatrix.quarterWave(0));
        close(initial.powerWatts(),qwp.powerWatts());
        close(0,qwp.modalAmplitudeSqrtWatts().y().real());
        close(Math.sqrt(POWER/2),qwp.modalAmplitudeSqrtWatts().y().imaginary());
        var hwp=initial.throughPassiveJones(JonesMatrix.halfWave(0));
        close(initial.powerWatts(),hwp.powerWatts());
        close(-Math.sqrt(POWER/2),hwp.modalAmplitudeSqrtWatts().y().real());
    }

    @Test void passiveJonesRejectsGainAndPhasePreservesPower() {
        var before=start();
        var active=new JonesMatrix(new ComplexAmplitude(2,0),ComplexAmplitude.ZERO,
                ComplexAmplitude.ZERO,ComplexAmplitude.ONE);
        assertThrows(IllegalArgumentException.class,()->before.throughPassiveJones(active));
        var phase=before.withUniformPhaseRadians(Math.PI/3);
        close(before.powerWatts(),phase.powerWatts());
        closeComplex(before.modalAmplitudeSqrtWatts().x().times(
                ComplexAmplitude.fromPhaseRadians(Math.PI/3)),phase.modalAmplitudeSqrtWatts().x());
    }

    @Test void propagationPhaseUlpDiagnosticExposesInsufficientWorldCoordinatePrecision() {
        var initial=start();
        double resolution=initial.inputPathUlpPhaseRadians(0.125);
        assertTrue(resolution > 0 && resolution < 1e-8);
        assertTrue(initial.inputPathUlpPhaseRadians(1e12) > Math.PI);
        assertThrows(IllegalArgumentException.class,
                ()->initial.inputPathUlpPhaseRadians(Double.NaN));
    }

    @Test void zeroModeTravelsWithoutCreatingEnergyAndRetainsModeLabels() {
        var zero=start().zeroLike();
        assertEquals(0.0,zero.powerWatts(),0);
        assertEquals(0.0,zero.freeSpace(0.2).thinLens(0.3,0).powerWatts(),0);
        assertEquals(0.0,zero.fieldAtRadiusMeters(0).squaredNorm(),0);
        assertTrue(start().sameModeAs(zero));
    }

    @Test void rejectsNegativeTravelAndInvalidSamplesAndPhases() {
        var initial=start();
        assertThrows(IllegalArgumentException.class,()->initial.freeSpace(-0.1));
        assertThrows(IllegalArgumentException.class,()->initial.freeSpace(Double.NaN));
        assertThrows(IllegalArgumentException.class,()->initial.fieldAtRadiusMeters(-1));
        assertThrows(IllegalArgumentException.class,()->initial.thinLens(0,0));
        assertThrows(IllegalArgumentException.class,()->initial.thinLens(0.2,Double.NaN));
        assertThrows(IllegalArgumentException.class,()->initial.throughPassiveJones(null));
        assertThrows(IllegalArgumentException.class,()->initial.withIdealMirrorReferencePhaseRadians(Double.POSITIVE_INFINITY));
    }
}
