import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.GaussianQ;
import dev.coldatomcraft.physics.optics.GaussianPlane;
import dev.coldatomcraft.physics.optics.ParaxialAbcd;
import java.util.Locale;

/** Java numerical output for the independent Python Decimal oracle. NOT a mod entrypoint. */
public final class OpticsReferenceProbe {
    private static void one(int id, double wavelength, double waist, double power,
                            double z, double r) {
        GaussianBeam beam = new GaussianBeam(power, wavelength, waist,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
        System.out.printf(Locale.ROOT, "G,%d,%.17e,%.17e,%.17e,%.17e%n",
                id, beam.rayleighRangeMeters(), beam.radiusMeters(z),
                beam.intensityWattsPerSquareMeter(r, z),
                beam.powerWithinRadiusWatts(r, z));
    }

    private static void q(int id, GaussianQ output) {
        System.out.printf(Locale.ROOT, "Q,%d,%.17e,%.17e%n",
                id, output.realMeters(), output.imaginaryMeters());
    }

    public static void main(String[] args) {
        one(0, 780e-9, 0.0005, 0.02, 0, 0);
        one(1, 780e-9, 0.0005, 0.02, 1, 0.0005);
        one(2, 1064e-9, 0.001, 0.5, 7, 0.0007);
        one(3, 532e-9, 0.0002, 0.003, -0.2, 0.0001);
        one(4, 780e-9, 0.0005, 0.02, 0.00000001, 0.000001);
        one(5, 780e-9, 0.0005, 0, 4, 0.00000001);
        double zr = Math.PI * 0.0005 * 0.0005 / (780e-9);
        var initial = new GaussianQ(0, zr);
        q(0, ParaxialAbcd.freeSpace(0.15)
                .then(ParaxialAbcd.thinLens(0.2))
                .then(ParaxialAbcd.freeSpace(0.4)).transform(initial));
        q(1, ParaxialAbcd.thinLens(1).transform(new GaussianQ(1e16, 1)));
        q(2, ParaxialAbcd.thinLens(-0.3).transform(new GaussianQ(0.2, 1.5)));
        GaussianPlane plane = GaussianPlane.atAxialDistance(
                new GaussianBeam(0.02, 780e-9, 0.0005,
                        new Vec3(0, 0, 0), new Vec3(0, 0, 1)), 0)
                .through(ParaxialAbcd.freeSpace(0.1))
                .through(ParaxialAbcd.thinLens(0.2))
                .through(ParaxialAbcd.freeSpace(0.4));
        System.out.printf(Locale.ROOT, "P,0,%.17e,%.17e,%.17e,%.17e,%.17e,%.17e%n",
                plane.q().realMeters(), plane.q().imaginaryMeters(),
                plane.radiusMeters(), plane.intensityWattsPerSquareMeter(0.0002),
                plane.powerWithinRadiusWatts(0.0004), plane.powerWatts());
    }
}
