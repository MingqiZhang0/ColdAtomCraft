package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** D22D reference optics tests, not detector calibration or game observations. */
class FinitePlaneGaussianFluxTest {
    private static GaussianBeam beam() {
        return new GaussianBeam(.02, 780e-9, .0005,
                new Vec3(0,0,0), new Vec3(0,0,1));
    }
    private static FinitePlaneGaussianFlux.Disk face(double x, double degrees) {
        double angle = Math.toRadians(degrees);
        return FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(x,0,1),
                new Vec3(Math.sin(angle),0,-Math.cos(angle)),
                new Vec3(0,1,0),.00065);
    }

    @Test void perpendicularDiskReusesExistingGaussianAperture() {
        var predicted = FinitePlaneGaussianFlux.predict(beam(),face(.0003,0));
        double w = beam().radiusMeters(1);
        double reference = GaussianCircularAperture.capture(.02,w,.00065,.0003)
                .capturedPowerWatts();
        assertEquals(reference,predicted.capturedWatts(),1e-14);
        assertEquals(FinitePlaneGaussianFlux.Method.NORMAL_ANALYTIC,predicted.method());
        assertEquals("REFERENCE_ONLY",predicted.provenance());
    }

    @Test void tiltedDiskIntegratesProjectedFluxAndExposesMethod() {
        var normal=FinitePlaneGaussianFlux.predict(beam(),face(0,0));
        var tilted=FinitePlaneGaussianFlux.predict(beam(),face(0,30));
        assertTrue(tilted.capturedWatts()>0);
        assertTrue(tilted.capturedWatts()<normal.capturedWatts());
        assertEquals(FinitePlaneGaussianFlux.Method.OBLIQUE_PARAXIAL_QUADRATURE,
                tilted.method());
        assertEquals(Math.cos(Math.toRadians(30)),tilted.forwardCosine(),1e-14);
        assertTrue(tilted.estimatedQuadratureErrorWatts()<.02*3e-5);
        assertEquals(.02,tilted.capturedWatts()+tilted.uncapturedWatts(),1e-14);
    }

    @Test void axisOutsideDiskDoesNotZeroGaussianTails() {
        var face=face(.0015,30);
        var result=FinitePlaneGaussianFlux.predict(beam(),face);
        assertFalse(result.axisIntersectsDisk());
        assertTrue(result.capturedWatts()>0);
        var crossing=FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,0),
                new Vec3(0,0,1),face);
        assertEquals(FinitePlaneGaussianFlux.CrossingStatus.FORWARD,crossing.status());
        assertFalse(crossing.insideDisk());
        assertEquals(crossing.diskOffsetMetres(),result.axisOffsetMetres(),1e-12);
    }

    @Test void rayPlaneCrossingHonoursBacksideParallelAndSourceOrder() {
        var face=face(0,0);
        assertEquals(FinitePlaneGaussianFlux.CrossingStatus.FORWARD,
                FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,0),
                        new Vec3(0,0,1),face).status());
        assertEquals(FinitePlaneGaussianFlux.CrossingStatus.BEHIND_START,
                FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,2),
                        new Vec3(0,0,1),face).status());
        assertEquals(FinitePlaneGaussianFlux.CrossingStatus.PARALLEL,
                FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,0),
                        new Vec3(1,0,0),face).status());
        assertEquals(FinitePlaneGaussianFlux.CrossingStatus.BACK_FACE,
                FinitePlaneGaussianFlux.axisCrossing(new Vec3(0,0,2),
                        new Vec3(0,0,-1),face).status());
    }

    @Test void backsideAndGrazingAnglesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> FinitePlaneGaussianFlux.predict(
                beam(), FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(0,0,1),
                        new Vec3(0,0,1),new Vec3(1,0,0),.00065)));
        assertThrows(IllegalArgumentException.class, () -> FinitePlaneGaussianFlux.predict(
                beam(), FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(0,0,1),
                        new Vec3(1,0,-.1),new Vec3(0,1,0),.00065)));
    }

    @Test void oversizedTiltedApertureIsExplicitlyUnsupported() {
        assertThrows(IllegalArgumentException.class,
                () -> FinitePlaneGaussianFlux.predict(beam(),
                        FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(0,0,1),
                                new Vec3(.5,0,-.8660254037844386),new Vec3(0,1,0),.01)));
    }

    @Test void impossibleWorldScaleDoubleCoordinatesAreRejected() {
        var distant = new GaussianBeam(.02,780e-9,.0005,
                new Vec3(1e15,0,0),new Vec3(0,0,1));
        assertThrows(IllegalArgumentException.class,
                () -> FinitePlaneGaussianFlux.predict(distant,
                        FinitePlaneGaussianFlux.Disk.fromNormal(new Vec3(1e15,0,1),
                                new Vec3(0,0,-1),new Vec3(1,0,0),.00065)));
    }

    @Test void beamWaistBehindDetectorDoesNotEquateToEmitterBehindDetector() {
        var b=new GaussianBeam(.02,780e-9,.0005,
                new Vec3(0,0,2),new Vec3(0,0,1));
        var p=FinitePlaneGaussianFlux.predict(b,face(0,0));
        assertTrue(p.capturedWatts()>0);
        assertEquals(.02,p.capturedWatts()+p.uncapturedWatts(),1e-14);
    }

    @Test void sourceOffIsExactlyZeroWithoutFabricatingObservation() {
        var b=new GaussianBeam(0,780e-9,.0005,
                new Vec3(0,0,0),new Vec3(0,0,1));
        var p=FinitePlaneGaussianFlux.predict(b,face(0,15));
        assertEquals(0,p.capturedWatts(),0);
        assertEquals(0,p.uncapturedWatts(),0);
        assertEquals("REFERENCE_ONLY",p.provenance());
    }
}
