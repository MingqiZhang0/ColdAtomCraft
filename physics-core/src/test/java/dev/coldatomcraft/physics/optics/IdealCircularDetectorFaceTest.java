package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdealCircularDetectorFaceTest {
    private static GaussianBeam referenceBeam() {
        return new GaussianBeam(0.02,780e-9,0.0005,
                new Vec3(0,0,0),new Vec3(0,0,1));
    }

    @Test void centeredFaceCollectsPowerFromWaistRadius() {
        var face=new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-4),0.0005);
        var r=face.capture(referenceBeam());
        assertEquals(0.02*(1-Math.exp(-2)),r.capturedPowerWatts(),1e-14);
    }

    @Test void farFieldDetectorSeesLargerSpotAndSmallerCapturedFraction() {
        var waist=new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-1),0.0004);
        var far=new IdealCircularDetectorFace(new Vec3(0,0,4.0),new Vec3(0,0,-1),0.0004);
        assertTrue(waist.capture(referenceBeam()).capturedPowerWatts()>
                far.capture(referenceBeam()).capturedPowerWatts());
    }

    @Test void decenteredFaceMustCaptureLessThanCentered() {
        var centered=new IdealCircularDetectorFace(new Vec3(0,0,0.3),new Vec3(0,0,-1),0.0002);
        var offset=new IdealCircularDetectorFace(new Vec3(0.0004,0,0.3),new Vec3(0,0,-1),0.0002);
        assertTrue(centered.capture(referenceBeam()).capturedPowerWatts()>
                offset.capture(referenceBeam()).capturedPowerWatts());
    }

    @Test void laboratoryTranslationDoesNotChangeRelativeCapture() {
        var origin=new Vec3(2.0,-3.0,1.5);
        var beam=new GaussianBeam(0.02,780e-9,0.0005,origin,new Vec3(0,0,1));
        var face=new IdealCircularDetectorFace(origin.add(new Vec3(0.0002,0,0.1)),
                new Vec3(0,0,-1),0.0005);
        var baseline=new IdealCircularDetectorFace(new Vec3(0.0002,0,0.1),
                new Vec3(0,0,-1),0.0005);
        assertEquals(baseline.capture(referenceBeam()).capturedPowerWatts(),
                face.capture(beam).capturedPowerWatts(),1e-12);
    }

    @Test void rotatedLaboratoryAxesProduceSamePower() {
        var beam=new GaussianBeam(0.02,780e-9,0.0005,
                new Vec3(0,0,0),new Vec3(1,0,0));
        var face=new IdealCircularDetectorFace(new Vec3(0.1,0.0004,0),new Vec3(-1,0,0),0.0002);
        var equivalent=new IdealCircularDetectorFace(new Vec3(0,0.0004,0.1),new Vec3(0,0,-1),0.0002);
        assertEquals(equivalent.capture(referenceBeam()).capturedPowerWatts(),
                face.capture(beam).capturedPowerWatts(),1e-13);
    }

    @Test void backFacingDetectorCannotSilentlyReportIncidentPower() {
        var face=new IdealCircularDetectorFace(new Vec3(0,0,0.1),new Vec3(0,0,1),0.0005);
        assertThrows(IllegalArgumentException.class,()->face.capture(referenceBeam()));
    }

    @Test void obliqueFaceMustBeRejectedInsteadOfUsingNormalIncidenceFormula() {
        var face=new IdealCircularDetectorFace(new Vec3(0,0,0.1),new Vec3(0.3,0,-1),0.0005);
        assertThrows(IllegalArgumentException.class,()->face.capture(referenceBeam()));
    }

    @Test void oversizedGlobalCoordinatesAreRejectedInsteadOfSilentlyLosingMicronOffsets() {
        var beam=new GaussianBeam(0.02,780e-9,0.0005,
                new Vec3(1e16,0,0),new Vec3(0,0,1));
        var face=new IdealCircularDetectorFace(new Vec3(1e16,0,0.1),
                new Vec3(0,0,-1),0.0005);
        assertThrows(IllegalArgumentException.class,()->face.capture(beam));
    }

    @Test void physicalFaceInputValidation() {
        assertThrows(IllegalArgumentException.class,()->new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,0),1));
        assertThrows(IllegalArgumentException.class,()->new IdealCircularDetectorFace(null,new Vec3(0,0,-1),1));
        assertThrows(IllegalArgumentException.class,()->new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-1),0));
        assertThrows(IllegalArgumentException.class,()->new IdealCircularDetectorFace(new Vec3(0,0,0),new Vec3(0,0,-1),Double.NaN));
    }
}
