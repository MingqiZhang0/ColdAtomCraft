package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;

/** Pure Java D8 geometry/Fresnel/mirror checks; not official JUnit. */
public final class GeometryOpticsSmoke {
    private static int checks;
    private static void check(boolean value,String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    private static void near(double a,double b,double abs,String reason) {
        check(Math.abs(a-b)<=abs,reason+": "+a+" vs "+b);
    }
    private static final Vec3 ORIGIN=new Vec3(0,0,0);
    private static final Vec3 Z=new Vec3(0,0,1);
    private static final Vec3 Y=new Vec3(0,1,0);
    private static GeometricJonesRay ray(double angle, JonesVector polarization) {
        var k=new Vec3(Math.sin(angle),0,Math.cos(angle));
        return new GeometricJonesRay(new Vec3(0,0,-1),
                RayFrame3.fromDirectionAndXAxis(k,Y),780e-9,"laser",polarization);
    }
    public static void main(String[] args) {
        var surface=new OpticalPlane3(ORIGIN,Z);
        var fs=new PlanarFresnelInterface(surface,1,1.5).scatter(ray(0,JonesVector.horizontal()));
        near(fs.coefficients().rS().real(),-.2,1e-12,"normal r_s");
        near(fs.coefficients().rP().real(),.2,1e-12,"normal r_p sign");
        near(fs.coefficients().transmittanceS(),.96,1e-12,"normal T_s");
        near(fs.reflected().powerWatts(),.04,1e-12,"normal R");
        near(fs.transmitted().orElseThrow().powerWatts(),.96,1e-12,"normal T");
        near(fs.outputPowerWatts(),1,1e-12,"power conservation normal");
        var bp=new PlanarFresnelInterface(surface,1,1.5).scatter(
                ray(Math.atan(1.5),JonesVector.vertical()));
        near(bp.reflected().powerWatts(),0,1e-26,"Brewster p zero");
        near(bp.outputPowerWatts(),1,1e-12,"Brewster total power");
        var tir=new PlanarFresnelInterface(surface,1.5,1).scatter(
                ray(Math.toRadians(60),JonesVector.linearRadians(.3)));
        check(tir.transmitted().isEmpty(),"TIR no power ray");
        check(tir.coefficients().totalInternalReflection(),"TIR flagged");
        near(tir.reflected().powerWatts(),1,1e-12,"TIR all power reflected");
        check(tir.coefficients().rS().imaginary()!=0,"TIR complex phase");
        var fold=new IdealCalibratedPlaneMirror(new OpticalPlane3(ORIGIN,new Vec3(-1,0,1)),0,0)
                .reflect(ray(0,JonesVector.horizontal()));
        near(fold.reflected().frame().direction().x(),1,1e-12,"90 degree fold");
        near(fold.reflected().powerWatts(),1,1e-12,"ideal mirror power");
        var mode=ParaxialModeField.fromPlane("laser","in",new GaussianPlane(.02,780e-9,
                new GaussianQ(.3,1.2)),JonesVector.horizontal(),.2);
        var modeRay=GeometricJonesRay.atGaussianReferencePlane(mode,ORIGIN,
                RayFrame3.fromDirectionAndXAxis(Z,Y));
        var mirror=new IdealCalibratedPlaneMirror(surface,0,0);
        var reflectedMode=mirror.reflectGaussianModeAtPlane(mode,modeRay,"out");
        near(reflectedMode.q().realMeters(),.3,1e-12,"mirror Gaussian q curvature");
        near(reflectedMode.q().imaginaryMeters(),1.2,1e-12,"mirror Gaussian q rayleigh");
        near(reflectedMode.powerWatts(),.02,1e-12,"mirror Gaussian power");
        boolean bad=false;
        try {new PlanarFresnelInterface(surface,1,1.5).scatter(ray(0,JonesVector.horizontal())
                 .advance(-1,1));} catch(IllegalArgumentException ex){bad=true;}
        check(bad,"negative travel rejected");
        System.out.println("PASS: "+checks+" D8 geometry/Fresnel/mirror smoke checks (NOT JUnit)");
    }
}
