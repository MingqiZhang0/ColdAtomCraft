package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.*;
import java.util.Locale;

/** Numeric probe for independent Decimal80 Fresnel oracle. */
public final class FresnelReferenceProbe {
    public static void main(String[] args) {
        double[][] cases = {
            {1,1.5,0}, {1,1.5,20}, {1,1.5,40}, {1,1.5,60},
            {1.5,1,0}, {1.5,1,20}, {1.5,1,45}, {1.5,1,60}, {1.5,1,75},
        };
        for (int i=0;i<cases.length;i++) {
            var c=cases[i]; double theta=Math.toRadians(c[2]);
            var k=new Vec3(Math.sin(theta),0,Math.cos(theta));
            var amp=JonesVector.linearRadians(0.41);
            var ray=new GeometricJonesRay(new Vec3(0,0,0),
                    RayFrame3.fromDirectionAndXAxis(k,new Vec3(0,1,0)),780e-9,"test",amp);
            var result=new PlanarFresnelInterface(new OpticalPlane3(
                    new Vec3(0,0,0),new Vec3(0,0,1)),c[0],c[1]).scatter(ray);
            var coeff=result.coefficients();
            System.out.printf(Locale.ROOT,
                    "C|%d|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%.17g|%s%n",
                    i,coeff.rS().real(),coeff.rS().imaginary(),
                    coeff.rP().real(),coeff.rP().imaginary(),
                    coeff.reflectanceS(),coeff.reflectanceP(),
                    coeff.transmittanceS(),coeff.transmittanceP(),
                    coeff.incidentCosine(),coeff.transmittedCosine(),
                    result.reflected().powerWatts(),
                    result.transmitted().map(GeometricJonesRay::powerWatts).orElse(0.0),
                    result.reflected().frame().direction().z(),
                    result.transmitted().map(r->r.frame().direction().x()).orElse(0.0),
                    result.outputPowerWatts(),coeff.totalInternalReflection());
        }
        double brewster=Math.atan(1.5);
        var kb=new Vec3(Math.sin(brewster),0,Math.cos(brewster));
        var pRay=new GeometricJonesRay(new Vec3(0,0,0),
                RayFrame3.fromDirectionAndXAxis(kb,new Vec3(0,1,0)),780e-9,"test",JonesVector.vertical());
        var br=new PlanarFresnelInterface(new OpticalPlane3(new Vec3(0,0,0),
                new Vec3(0,0,1)),1,1.5).scatter(pRay);
        System.out.printf(Locale.ROOT,"BREWSTER|%.17g|%.17g%n",
                br.coefficients().rP().real(),br.reflected().powerWatts());
    }
}
