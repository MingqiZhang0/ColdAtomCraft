package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.GaussianQ;
import dev.coldatomcraft.physics.optics.ParaxialAbcd;

/** Standalone analytic checks for the draft OPT-001 reference solver. */
public final class OpticsSmoke {
    private static int checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    private static void close(double actual, double truth, double relative, String what) {
        check(Double.isFinite(actual) && Math.abs(actual-truth)
                <= Math.abs(truth)*relative + 1e-300, what);
    }

    public static void main(String[] args) {
        double power = 0.02, lambda = 780e-9, w0 = 0.0005;
        var beam = new GaussianBeam(power, lambda, w0,
                new Vec3(1, 2, 3), new Vec3(0, 0, -5));
        double zr = Math.PI * w0 * w0 / lambda;
        close(beam.rayleighRangeMeters(), zr, 1e-14, "Rayleigh distance");
        close(beam.radiusMeters(0), w0, 1e-14, "waist radius");
        close(beam.radiusMeters(zr), w0*Math.sqrt(2), 1e-14, "radius at zR");
        close(beam.radiusMeters(-zr), w0*Math.sqrt(2), 1e-14, "negative z");
        close(beam.onAxisIntensityWattsPerSquareMeter(0), 2*power/(Math.PI*w0*w0),
                1e-14, "on-axis irradiance");
        close(beam.intensityWattsPerSquareMeter(w0,0),
                beam.onAxisIntensityWattsPerSquareMeter(0)*Math.exp(-2),
                1e-14, "1/e^2 waist");
        close(beam.powerWithinRadiusWatts(w0, 0),
                power*(-Math.expm1(-2)), 1e-14, "encircled power");
        close(beam.powerWithinRadiusWatts(100*w0,0), power, 1e-14, "power tail");
        close(beam.divergenceParameter(), lambda/(Math.PI*w0), 1e-14, "divergence");
        close(beam.gouyPhaseRadians(zr), Math.PI/4, 1e-14, "Gouy phase");
        close(beam.curvatureRadiusMeters(zr), 2*zr, 1e-14, "curvature");
        check(Double.isInfinite(beam.curvatureRadiusMeters(0)), "waist curvature infinity");
        check(beam.directionLab().equals(new Vec3(0,0,-1)), "direction normalized");
        var point = new Vec3(1.0001,2,3-zr);
        close(beam.intensityAtLabPointWattsPerSquareMeter(point),
                beam.intensityWattsPerSquareMeter(0.0001, zr), 1e-13,
                "laboratory coordinate projection");
        var q = beam.complexParameterAt(0);
        close(ParaxialAbcd.freeSpace(0.23).transform(q).realMeters(), 0.23, 1e-14,
                "free propagation ABCD");
        close(ParaxialAbcd.thinLens(0.23).transform(q).radiusMeters(lambda),w0,1e-14,
                "thin lens beam radius continuity");
        var strong = ParaxialAbcd.thinLens(1).transform(new GaussianQ(1e16,1));
        close(strong.imaginaryMeters(),1e-32,1e-14,"strong-lens positive imag");
        check(strong.imaginaryMeters()>0, "no cancellation to zero");
        var off = new GaussianBeam(0,lambda,w0,new Vec3(0,0,0),new Vec3(0,0,1));
        check(off.intensityWattsPerSquareMeter(0,0)==0,"source off");
        check(off.powerWithinRadiusWatts(w0,0)==0,"off power");
        boolean rejected=false;
        try { new GaussianBeam(-1,lambda,w0,new Vec3(0,0,0),new Vec3(0,0,1)); }
        catch (IllegalArgumentException expected) { rejected=true; }
        check(rejected,"negative beam power rejected");
        System.out.println("PASS: "+checks+" OPT-001 draft analytic smoke checks; no game/world model implied");
    }
}
