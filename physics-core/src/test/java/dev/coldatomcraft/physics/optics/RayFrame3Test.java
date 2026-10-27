package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RayFrame3Test {
    private static final Vec3 X=new Vec3(1,0,0), Y=new Vec3(0,1,0), Z=new Vec3(0,0,1);

    @Test void rightHandedOrthonormalBasis() {
        var frame=RayFrame3.fromDirectionAndXAxis(Z,X);
        assertEquals(Z,frame.direction());
        assertEquals(X,frame.xAxis());
        assertEquals(Y,frame.yAxis());
        assertEquals(frame.direction(),frame.xAxis().cross(frame.yAxis()));
    }
    @Test void nonunitInputVectorsAreNormalized() {
        var frame=RayFrame3.fromDirectionAndXAxis(new Vec3(0,0,8),new Vec3(4,0,2));
        assertEquals(1.0,frame.xAxis().magnitude(),1e-12);
        assertEquals(0.0,frame.xAxis().dot(frame.direction()),1e-12);
    }
    @Test void invalidFramesCannotEnterCalculation() {
        assertThrows(IllegalArgumentException.class,()->RayFrame3.fromDirectionAndXAxis(Z,Z));
        assertThrows(IllegalArgumentException.class,()->new RayFrame3(Z,X,new Vec3(0,-1,0)));
        assertThrows(IllegalArgumentException.class,()->new RayFrame3(Z,X,X));
        assertThrows(IllegalArgumentException.class,()->RayFrame3.fromDirectionAndXAxis(new Vec3(0,0,0),X));
    }
    @Test void planeWavePathKeepsComplexPowerAndPhase() {
        var in=new GeometricJonesRay(new Vec3(0,0,0),RayFrame3.fromDirectionAndXAxis(Z,X),
                800e-9,"same-laser",JonesVector.horizontal().scale(Math.sqrt(0.012)));
        var out=in.advance(200e-9,1);
        assertEquals(0.012,out.powerWatts(),1e-15);
        assertEquals(0.0,out.amplitudeSqrtWatts().x().real(),1e-14);
        assertEquals(Math.sqrt(0.012),out.amplitudeSqrtWatts().x().imaginary(),1e-14);
        assertEquals(200e-9,out.referencePointMeters().z(),1e-18);
    }
    @Test void normalBasisRotationPreservesLabFieldAndPower() {
        var left=RayFrame3.fromDirectionAndXAxis(Z,X);
        var right=RayFrame3.fromDirectionAndXAxis(Z,Y);
        var a=new GeometricJonesRay(new Vec3(0,0,0),left,780e-9,"laser",
                JonesVector.horizontal());
        var b=new GeometricJonesRay(new Vec3(0,0,0),right,780e-9,"laser",
                JonesVector.vertical().scale(-1));
        assertEquals(a.laboratoryAmplitudeSqrtWatts(),b.laboratoryAmplitudeSqrtWatts());
        assertEquals(1.0,a.laboratoryAmplitudeSqrtWatts().squaredNormWatts(),1e-14);
    }
    @Test void microscopicDisplacementCannotSilentlyDisappearAtHugeCoordinates() {
        var ray=new GeometricJonesRay(new Vec3(0,0,1e12),
                RayFrame3.fromDirectionAndXAxis(Z,X),780e-9,"laser",JonesVector.horizontal());
        assertThrows(IllegalArgumentException.class,()->ray.advance(100e-9,1));
        assertEquals(1e12 + .125,ray.advance(.125,1).referencePointMeters().z(),.001);
    }
    @Test void noImplicitGaussianPropagation() {
        var mode=ParaxialModeField.fromPlane("laser","local",new GaussianPlane(
                0.01,780e-9,new GaussianQ(0,0.75)),JonesVector.horizontal(),0);
        var geo=GeometricJonesRay.atGaussianReferencePlane(mode,new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,X));
        assertEquals(mode.powerWatts(),geo.powerWatts(),1e-15);
        assertEquals(mode.phaseReferenceKey(),geo.phaseReferenceKey());
        assertEquals(mode.modalAmplitudeSqrtWatts(),geo.amplitudeSqrtWatts());
        assertThrows(IllegalArgumentException.class,()->geo.advance(-1,1));
        assertThrows(IllegalArgumentException.class,()->geo.advance(1,-1));
    }
}
