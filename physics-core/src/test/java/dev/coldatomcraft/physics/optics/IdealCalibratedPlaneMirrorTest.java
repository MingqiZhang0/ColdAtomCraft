package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdealCalibratedPlaneMirrorTest {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static GeometricJonesRay incident(Vec3 direction, Vec3 location, JonesVector polarization) {
        return new GeometricJonesRay(location,RayFrame3.fromDirectionAndXAxis(direction,Y),
                780e-9,"laser",polarization);
    }
    private static void near(double a,double b) {
        assertEquals(b,a,1e-10*Math.max(1,Math.abs(b)));
    }
    @Test void fortyFiveDegreeMirrorTurnsBeamThroughRightAngle() {
        var plane=new OpticalPlane3(new Vec3(0,0,0),new Vec3(-1,0,1));
        var mirror=new IdealCalibratedPlaneMirror(plane,0,0);
        var result=mirror.reflect(incident(Z,new Vec3(0,0,-1),JonesVector.horizontal()));
        near(result.reflected().frame().direction().x(),1);
        near(result.reflected().frame().direction().z(),0);
        near(result.reflected().powerWatts(),1);
        near(result.hit().distanceMeters(),1);
    }
    @Test void sAndPPhasesTransformPolarizationWithoutAddingPower() {
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),
                0,Math.PI/2);
        var ray=incident(Z,new Vec3(0,0,0),JonesVector.linearRadians(Math.PI/4));
        var result=mirror.reflect(ray).reflected();
        near(result.powerWatts(),1);
        near(result.amplitudeSqrtWatts().x().real(),Math.sqrt(.5));
        near(result.amplitudeSqrtWatts().y().imaginary(),Math.sqrt(.5));
        assertEquals(-Z.z(),result.frame().direction().z(),1e-12);
    }
    @Test void mirrorWithSamePhaseHasSamePhysicalLabPolarizationSign() {
        var m=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),0,Math.PI);
        var r=m.reflect(incident(Z,new Vec3(0,0,0),JonesVector.vertical())).reflected();
        // Incoming p points -X, reflected p points +X; phase pi restores -X.
        near(r.laboratoryAmplitudeSqrtWatts().x().real(),-1);
    }
    @Test void oppositeDirectionAfterReflectionAndNoGeometricalJitter() {
        var m=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),0,0);
        var r=m.reflect(incident(Z,new Vec3(0,0,-1),JonesVector.horizontal()));
        near(r.reflected().referencePointMeters().z(),0);
        near(r.reflected().frame().direction().z(),-1);
        near(r.reflected().powerWatts(),1);
    }
    @Test void flatMirrorPreservesLocalCurvatureAndRayleighRange() {
        var mode=ParaxialModeField.fromPlane("laser","incoming-frame",new GaussianPlane(
                .04,780e-9,new GaussianQ(.125,1.01)),JonesVector.horizontal(),0.2);
        var geom=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        var m=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),.4,.6);
        var reflected=m.reflectGaussianModeAtPlane(mode,geom,"outgoing-frame");
        near(reflected.q().realMeters(),.125);
        near(reflected.q().imaginaryMeters(),1.01);
        near(reflected.gaussianPlane().radiusMeters(),mode.gaussianPlane().radiusMeters());
        near(reflected.powerWatts(),.04);
        assertEquals("outgoing-frame",reflected.modeFrameKey());
        assertEquals(mode.phaseReferenceKey(),reflected.phaseReferenceKey());
    }
    @Test void mirrorDoesNotInventGaussianPropagationPhase() {
        var mode=ParaxialModeField.fromPlane("laser","before",new GaussianPlane(.01,780e-9,
                new GaussianQ(0,.7)),JonesVector.horizontal(),0);
        var geom=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,-1),
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),0,0);
        assertThrows(IllegalArgumentException.class,()->mirror.reflectGaussianModeAtPlane(
                mode,geom,"after"));
        var propagated=mode.freeSpace(1);
        var incidentAtMirror=GeometricJonesRay.atGaussianReferencePlane(propagated,
                new Vec3(0,0,0),geom.frame());
        var reflected=mirror.reflectGaussianModeAtPlane(propagated,incidentAtMirror,"after");
        near(reflected.q().realMeters(),1);
        near(reflected.q().imaginaryMeters(),.7);
        near(reflected.powerWatts(),.01);
    }
    @Test void radialWavefrontPhaseIsContinuousAtFlatMirror() {
        var mode=ParaxialModeField.fromPlane("laser","incoming",new GaussianPlane(
                .015,780e-9,new GaussianQ(.2,.8)), JonesVector.horizontal(),0.31);
        var geometric=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        double phase=.47;
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),phase,.84);
        var output=mirror.reflectGaussianModeAtPlane(mode,geometric,"outgoing");
        var coating=ComplexAmplitude.fromPhaseRadians(phase);
        for(double r: new double[]{0,.0001,.0002,.0005}) {
            var before=mode.fieldAtRadiusMeters(r).x();
            var after=output.fieldAtRadiusMeters(r).x();
            var predicted=before.times(coating);
            near(after.real(),predicted.real());
            near(after.imaginary(),predicted.imaginary());
        }
    }
    @Test void gaussianMirrorRejectsAmplitudeMismatch() {
        var mode=ParaxialModeField.fromPlane("laser","one",new GaussianPlane(.01,780e-9,
                new GaussianQ(.1,.7)),JonesVector.horizontal(),0);
        var geo=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        var modified=new GeometricJonesRay(geo.referencePointMeters(),geo.frame(),780e-9,
                geo.phaseReferenceKey(),geo.amplitudeSqrtWatts().scale(.5));
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),0,0);
        assertThrows(IllegalArgumentException.class,()->mirror.reflectGaussianModeAtPlane(
                mode,modified,"out"));
    }
    @Test void mirrorReflectedPortMustNotReuseIncomingFrameIdentity() {
        var mode=ParaxialModeField.fromPlane("laser","in-port",new GaussianPlane(
                .01,780e-9,new GaussianQ(.1,.8)),JonesVector.horizontal(),0);
        var geom=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),0,0);
        assertThrows(IllegalArgumentException.class,()->mirror.reflectGaussianModeAtPlane(
                mode,geom,"in-port"));
    }
    @Test void mirrorDoesNotCreateEnergyFromZeroInput() {
        var zero=incident(Z,new Vec3(0,0,0),new JonesVector(
                ComplexAmplitude.ZERO,ComplexAmplitude.ZERO));
        var mirror=new IdealCalibratedPlaneMirror(new OpticalPlane3(new Vec3(0,0,0),Z),.8,-1.5);
        near(mirror.reflect(zero).reflected().powerWatts(),0);
    }
}
