package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanarFresnelInterfaceTest {
    private static final Vec3 X=new Vec3(1,0,0), Y=new Vec3(0,1,0), Z=new Vec3(0,0,1);
    private static final double LAMBDA=780e-9;
    private static final OpticalPlane3 PLANE=new OpticalPlane3(new Vec3(0,0,0),Z);
    private static GeometricJonesRay ray(double angle, JonesVector jones) {
        Vec3 k=new Vec3(Math.sin(angle),0,Math.cos(angle));
        return new GeometricJonesRay(new Vec3(0,0,-1),
                RayFrame3.fromDirectionAndXAxis(k,Y), LAMBDA,"laser",jones);
    }
    private static GeometricJonesRay rayAtSurface(double angle, JonesVector jones) {
        var initial=ray(angle,jones);
        return new GeometricJonesRay(new Vec3(0,0,0),initial.frame(),LAMBDA,
                "laser",jones);
    }
    private static void near(double actual,double expected) {
        assertEquals(expected,actual,2e-11*Math.max(1,Math.abs(expected)));
    }
    @Test void normalIncidenceScalarFresnelAndPowerNormalizedTransmission() {
        var interaction=new PlanarFresnelInterface(PLANE,1,1.5)
                .scatter(rayAtSurface(0,JonesVector.horizontal().scale(Math.sqrt(0.020))));
        var c=interaction.coefficients();
        near(c.rS().real(),-0.2);
        near(c.rP().real(),0.2);
        near(c.tSElectric().real(),0.8);
        near(c.tPElectric().real(),0.8);
        near(c.transmittanceS(),0.96);
        near(c.transmittanceP(),0.96);
        near(c.reflectanceS(),0.04);
        near(interaction.reflected().powerWatts(),0.0008);
        near(interaction.transmitted().orElseThrow().powerWatts(),0.0192);
        near(interaction.outputPowerWatts(),0.020);
        // At normal incidence s and p Fresnel r have OPPOSITE scalar signs,
        // because the REFLECTED p unit vector flips relative to incoming p.
        near(interaction.reflected().laboratoryAmplitudeSqrtWatts().y().real(),
                -0.2*Math.sqrt(0.020));
    }
    @Test void obliqueSnellAndSpecularReflection() {
        double theta=Math.toRadians(40);
        var interaction=new PlanarFresnelInterface(PLANE,1,1.5)
                .scatter(ray(theta,JonesVector.linearRadians(0.3)));
        near(interaction.reflected().frame().direction().z(),-Math.cos(theta));
        near(interaction.reflected().frame().direction().x(),Math.sin(theta));
        var refracted=interaction.transmitted().orElseThrow();
        near(refracted.frame().direction().x(),Math.sin(theta)/1.5);
        near(refracted.frame().direction().magnitude(),1);
        near(interaction.outputPowerWatts(),1);
        near(interaction.hit().pointMeters().z(),0);
        near(interaction.hit().distanceMeters(),1/Math.cos(theta));
    }
    @Test void brewsterAngleEliminatesPReflectanceNotS() {
        double angle=Math.atan(1.5);
        var p=new PlanarFresnelInterface(PLANE,1,1.5)
                .scatter(ray(angle,JonesVector.vertical()));
        near(p.coefficients().rP().real(),0);
        near(p.reflected().powerWatts(),0);
        near(p.transmitted().orElseThrow().powerWatts(),1);
        var s=new PlanarFresnelInterface(PLANE,1,1.5)
                .scatter(ray(angle,JonesVector.horizontal()));
        assertTrue(s.reflected().powerWatts()>0.10);
    }
    @Test void totalInternalReflectionHasNoPropagatingOutputAndComplexPolarizationPhase() {
        var interaction=new PlanarFresnelInterface(PLANE,1.5,1)
                .scatter(ray(Math.toRadians(60), JonesVector.linearRadians(Math.PI/4)));
        assertTrue(interaction.coefficients().totalInternalReflection());
        assertTrue(interaction.transmitted().isEmpty());
        near(interaction.outputPowerWatts(),1);
        near(interaction.coefficients().reflectanceS(),1);
        near(interaction.coefficients().reflectanceP(),1);
        assertTrue(Math.abs(interaction.coefficients().rS().imaginary())>0.1);
        assertTrue(Math.abs(interaction.coefficients().rP().imaginary())>0.1);
        assertTrue(Math.abs(interaction.coefficients().rS().imaginary()
                -interaction.coefficients().rP().imaginary())>0.01);
    }
    @Test void randomLinearPolarizationPowerConservationAcrossAngles() {
        var interface1=new PlanarFresnelInterface(PLANE,1,1.52);
        for(int j=0;j<80;j++) {
            double theta=(j+1)*Math.PI/200;
            double a=(j*17.0)/19.0;
            var result=interface1.scatter(ray(theta,JonesVector.linearRadians(a).scale(Math.sqrt(0.02))));
            near(result.outputPowerWatts(),0.02);
            assertTrue(result.coefficients().transmittanceS()>=0);
            assertTrue(result.coefficients().transmittanceP()>=0);
        }
    }
    @Test void incidenceBasisGaugeDoesNotChangePhysicalLabOutput() {
        var in=ray(Math.PI/5,JonesVector.linearRadians(.34));
        // Rotate the incoming port basis by 90 degrees but preserve the
        // SAME physical lab polarization via Jones (-b, a) in new basis.
        var u=in.frame().xAxis(); var v=in.frame().yAxis(); var d=in.frame().direction();
        var newFrame=new RayFrame3(d,v,u.scale(-1));
        var a=in.amplitudeSqrtWatts();
        var rotated=new JonesVector(a.y(),a.x().scale(-1));
        var in2=new GeometricJonesRay(in.referencePointMeters(),newFrame,LAMBDA,"laser",rotated);
        assertEquals(in.laboratoryAmplitudeSqrtWatts(),in2.laboratoryAmplitudeSqrtWatts());
        var splitter=new PlanarFresnelInterface(PLANE,1,1.45);
        var result1=splitter.scatter(in);
        var result2=splitter.scatter(in2);
        assertEquals(result1.reflected().laboratoryAmplitudeSqrtWatts(),
                result2.reflected().laboratoryAmplitudeSqrtWatts());
        assertEquals(result1.transmitted().orElseThrow().laboratoryAmplitudeSqrtWatts(),
                result2.transmitted().orElseThrow().laboratoryAmplitudeSqrtWatts());
    }
    @Test void tangentialElectricFieldIsContinuousInBothPolarizations() {
        // The transmitted port contains sqrt(W), not the raw E amplitude.
        // Divide by sqrt(eta_transmit/eta_incident) to compare E-boundary
        // conditions; otherwise an erroneous raw-t-in-sqrtW model can pass
        // field continuity tests or violate energy conservation.
        for(double angle: new double[]{0,.2,.65,1.05}) {
            var incoming=rayAtSurface(angle,JonesVector.linearRadians(.71));
            var result=new PlanarFresnelInterface(PLANE,1,1.5).scatter(incoming);
            var i=incoming.laboratoryAmplitudeSqrtWatts();
            var r=result.reflected().laboratoryAmplitudeSqrtWatts();
            var t=result.transmitted().orElseThrow().laboratoryAmplitudeSqrtWatts();
            double invScale=1/result.coefficients().transmissionAmplitudeFluxScale();
            near(i.x().real()+r.x().real(),t.x().real()*invScale);
            near(i.y().real()+r.y().real(),t.y().real()*invScale);
            near(i.x().imaginary()+r.x().imaginary(),t.x().imaginary()*invScale);
            near(i.y().imaginary()+r.y().imaginary(),t.y().imaginary()*invScale);
        }
    }
    @Test void nearCriticalAngleRemainsEnergyConservingWithoutFakeTransmissionUnderTIR() {
        var iface=new PlanarFresnelInterface(PLANE,1.5,1);
        var below=iface.scatter(ray(Math.toRadians(41.8),JonesVector.linearRadians(.3)));
        var above=iface.scatter(ray(Math.toRadians(41.9),JonesVector.linearRadians(.3)));
        assertFalse(below.coefficients().totalInternalReflection());
        assertTrue(below.transmitted().isPresent());
        assertTrue(above.coefficients().totalInternalReflection());
        assertTrue(above.transmitted().isEmpty());
        near(below.outputPowerWatts(),1);
        near(above.outputPowerWatts(),1);
    }
    @Test void consecutiveInterfacesRequireExplicitAccountingForEveryReflectedBranch() {
        var front=new PlanarFresnelInterface(PLANE,1,1.5)
                .scatter(rayAtSurface(0,JonesVector.horizontal()));
        var exitPlane=new OpticalPlane3(new Vec3(0,0,.01),Z);
        var rear=new PlanarFresnelInterface(exitPlane,1.5,1)
                .scatter(front.transmitted().orElseThrow());
        near(front.reflected().powerWatts(),.04);
        near(rear.reflected().powerWatts(),.96*.04);
        near(rear.transmitted().orElseThrow().powerWatts(),.96*.96);
        near(front.reflected().powerWatts()+rear.outputPowerWatts(),1);
        // This tree is only the first two surface interactions. The light
        // reflected inside the slab will hit the FRONT boundary again,
        // which is explicitly outside this single-branch test.
    }
    @Test void arbitraryThreeDimensionalSurfaceNormalObeysSpecularAndSnellLaws() {
        var n=new Vec3(.2,-.4,.9).normalized();
        var k=new Vec3(.1,.15,.98).normalized();
        var spot=new Vec3(1.25,-2.5,3.0);
        var ray=new GeometricJonesRay(spot.subtract(k.scale(.3)),
                RayFrame3.fromDirectionAndXAxis(k,new Vec3(1,0,0)),LAMBDA,"3d-lab",
                JonesVector.linearRadians(.34));
        var result=new PlanarFresnelInterface(new OpticalPlane3(spot,n),1,1.52).scatter(ray);
        near(result.hit().distanceMeters(),.3);
        near(result.hit().pointMeters().subtract(spot).magnitude(),0);
        var kr=result.reflected().frame().direction();
        near(kr.dot(n),-k.dot(n));
        near(kr.subtract(k.subtract(n.scale(2*k.dot(n)))).magnitude(),0);
        var kt=result.transmitted().orElseThrow().frame().direction();
        near(kt.subtract(n.scale(kt.dot(n))).magnitude(),
                k.subtract(n.scale(k.dot(n))).magnitude()/1.52);
        near(result.outputPowerWatts(),1);
        near(result.reflected().frame().xAxis().dot(kr),0);
        near(result.transmitted().orElseThrow().frame().xAxis().dot(kt),0);
    }
    private static Vec3 rotate90(Vec3 a) { return new Vec3(-a.y(),a.x(),a.z()); }
    private static void sameComplex(ComplexAmplitude a, ComplexAmplitude b) {
        near(a.real(),b.real()); near(a.imaginary(),b.imaginary());
    }
    @Test void rotatingEntireExperimentChangesOnlyLaboratoryCoordinates() {
        var normal=new Vec3(.15,.23,.96).normalized();
        var k=new Vec3(.28,-.24,.92).normalized();
        var center=new Vec3(.1,.2,.3);
        var original=new GeometricJonesRay(center.subtract(k.scale(.4)),
                RayFrame3.fromDirectionAndXAxis(k,new Vec3(.2,1,.1)),LAMBDA,"source",
                JonesVector.linearRadians(.7).times(ComplexAmplitude.fromPhaseRadians(.3)));
        var plane=new OpticalPlane3(center,normal);
        var originalResult=new PlanarFresnelInterface(plane,1,1.45).scatter(original);
        var f=original.frame();
        var rotated=new GeometricJonesRay(rotate90(original.referencePointMeters()),
                new RayFrame3(rotate90(f.direction()),rotate90(f.xAxis()),rotate90(f.yAxis())),
                LAMBDA,"source",original.amplitudeSqrtWatts());
        var newResult=new PlanarFresnelInterface(new OpticalPlane3(rotate90(center),rotate90(normal)),
                1,1.45).scatter(rotated);
        var before=originalResult.reflected().laboratoryAmplitudeSqrtWatts();
        var after=newResult.reflected().laboratoryAmplitudeSqrtWatts();
        sameComplex(after.x(),before.y().scale(-1));
        sameComplex(after.y(),before.x());
        sameComplex(after.z(),before.z());
        near(originalResult.outputPowerWatts(),newResult.outputPowerWatts());
        near(originalResult.reflected().frame().direction().dot(normal),
                newResult.reflected().frame().direction().dot(rotate90(normal)));
    }
    @Test void reversedOrGrazingIncidenceIsNotSilentlyAccepted() {
        var iface=new PlanarFresnelInterface(PLANE,1,1.5);
        var backwards=new GeometricJonesRay(new Vec3(0,0,1),
                RayFrame3.fromDirectionAndXAxis(new Vec3(0,0,-1),Y),LAMBDA,"a",
                JonesVector.horizontal());
        assertThrows(IllegalArgumentException.class,()->iface.scatter(backwards));
        var grazing=new GeometricJonesRay(new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(X,Y),LAMBDA,"a",JonesVector.horizontal());
        assertThrows(IllegalArgumentException.class,()->iface.scatter(grazing));
    }
    @Test void surfaceBehindRayAndInvalidIndexFailClosed() {
        var reversePlane=new OpticalPlane3(new Vec3(0,0,-2),Z);
        assertThrows(IllegalArgumentException.class,()->new PlanarFresnelInterface(reversePlane,1,0));
        assertThrows(IllegalArgumentException.class,()->new PlanarFresnelInterface(reversePlane,1,1.5)
                .scatter(ray(0,JonesVector.horizontal())));
    }
    @Test void normalIncidenceBasisDoesNotDependOnArbitraryGlobalXAxis() {
        var incoming=new GeometricJonesRay(new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(Z,Y),LAMBDA,"coherent",JonesVector.horizontal());
        var response=new PlanarFresnelInterface(PLANE,1,1.5).scatter(incoming);
        near(response.reflected().powerWatts(),0.04);
        near(response.reflected().laboratoryAmplitudeSqrtWatts().y().real(),-0.2);
    }
}
