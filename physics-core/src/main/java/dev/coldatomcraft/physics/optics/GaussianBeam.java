package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Ideal, monochromatic, continuous-wave TEM00 paraxial Gaussian beam in vacuum.
 *
 * <p>The given position is the <b>beam waist</b> (not necessarily a laser's
 * physical output port). Coordinates are laboratory-local metres, power is W,
 * and wavelength is the vacuum wavelength in metres. This model does not
 * include polarization, interference, optical coatings, absorption, damage,
 * or any Minecraft world effects. It is a numerical reference implementation,
 * not an Owner-approved production physics model.</p>
 *
 * <p>With z measured from the waist, zR = pi*w0^2/lambda,
 * w(z) = hypot(w0, lambda*z/(pi*w0)) and
 * I(r,z) = 2P/(pi*w(z)^2)*exp(-2*r^2/w(z)^2).</p>
 *
 * <p>The paraxial approximation requires a sufficiently small divergence;
 * {@link #divergenceParameter()} exposes lambda/(pi*w0) rather than silently
 * imposing an arbitrary numerical acceptance threshold.</p>
 */
public record GaussianBeam(double powerWatts,
                           double vacuumWavelengthMeters,
                           double waistRadiusMeters,
                           Vec3 waistPositionLabMeters,
                           Vec3 directionLab) {
    public GaussianBeam {
        Vec3.requireFinite(powerWatts, "powerWatts");
        Vec3.requireFinite(vacuumWavelengthMeters, "vacuumWavelengthMeters");
        Vec3.requireFinite(waistRadiusMeters, "waistRadiusMeters");
        if (powerWatts < 0 || vacuumWavelengthMeters <= 0 || waistRadiusMeters <= 0) {
            throw new IllegalArgumentException("P >= 0, vacuum wavelength > 0, waist radius > 0");
        }
        if (waistPositionLabMeters == null || directionLab == null) {
            throw new IllegalArgumentException("waist position and propagation direction required");
        }
        directionLab = directionLab.normalized();
        double zr = rayleighRange(vacuumWavelengthMeters, waistRadiusMeters);
        double div = vacuumWavelengthMeters / (Math.PI * waistRadiusMeters);
        if (!(zr > 0) || !Double.isFinite(zr) || !(div > 0) || !Double.isFinite(div)) {
            throw new IllegalArgumentException("beam parameters exceed representable SI range");
        }
        // Demand a representable on-axis intensity at the waist; otherwise an
        // extreme, but finite, input could propagate infinity silently.
        if (!Double.isFinite(centralIntensity(powerWatts, waistRadiusMeters))) {
            throw new IllegalArgumentException("waist intensity exceeds representable range");
        }
    }

    private static double rayleighRange(double wavelength, double w0) {
        double zr = Math.PI * (w0 / wavelength) * w0;
        if (!(zr > 0) || !Double.isFinite(zr)) {
            // Recover finite answers when an intermediate floating-point ratio
            // over/underflows even though pi*w0*w0/lambda is representable.
            zr = Math.exp(Math.log(Math.PI) + 2 * Math.log(w0) - Math.log(wavelength));
        }
        return zr;
    }

    private static double centralIntensity(double power, double radius) {
        if (power == 0) return 0;
        return (2.0 / Math.PI) * (power / radius) / radius;
    }

    private static double nonNegativeDistance(double r, String name) {
        Vec3.requireFinite(r, name);
        if (r < 0) throw new IllegalArgumentException(name + " must be nonnegative");
        return r;
    }

    public double rayleighRangeMeters() {
        return rayleighRange(vacuumWavelengthMeters, waistRadiusMeters);
    }

    /** Dimensionless far-field divergence parameter theta=lambda/(pi*w0). */
    public double divergenceParameter() {
        return vacuumWavelengthMeters / (Math.PI * waistRadiusMeters);
    }

    /** Beam 1/e^2-intensity radius at signed axial coordinate z [m]. */
    public double radiusMeters(double axialDistanceMeters) {
        Vec3.requireFinite(axialDistanceMeters, "axialDistanceMeters");
        double w = Math.hypot(waistRadiusMeters,
                divergenceParameter() * axialDistanceMeters);
        if (!(w > 0) || !Double.isFinite(w)) {
            throw new ArithmeticException("beam radius is not representable");
        }
        return w;
    }

    /** On-axis irradiance I(0,z), in W/m^2. */
    public double onAxisIntensityWattsPerSquareMeter(double axialDistanceMeters) {
        double intensity = centralIntensity(powerWatts, radiusMeters(axialDistanceMeters));
        if (!Double.isFinite(intensity)) {
            throw new ArithmeticException("on-axis intensity not representable");
        }
        return intensity;
    }

    /** Scalar Gaussian irradiance in W/m^2, with radial distance r >= 0. */
    public double intensityWattsPerSquareMeter(double radialMeters,
                                               double axialDistanceMeters) {
        nonNegativeDistance(radialMeters, "radialMeters");
        double normalizedRadius = radialMeters / radiusMeters(axialDistanceMeters);
        return onAxisIntensityWattsPerSquareMeter(axialDistanceMeters)
                * Math.exp(-2.0 * normalizedRadius * normalizedRadius);
    }

    /** Power within a coaxial circular aperture, with no obstruction or loss. */
    public double powerWithinRadiusWatts(double radialMeters,
                                         double axialDistanceMeters) {
        nonNegativeDistance(radialMeters, "radialMeters");
        double normalizedRadius = radialMeters / radiusMeters(axialDistanceMeters);
        return powerWatts * (-Math.expm1(-2.0 * normalizedRadius * normalizedRadius));
    }

    /** Signed z relative to the waist; mathematically valid for z on either side. */
    public double axialCoordinateMeters(Vec3 labPointMeters) {
        if (labPointMeters == null) throw new IllegalArgumentException("point required");
        return labPointMeters.subtract(waistPositionLabMeters).dot(directionLab);
    }

    public double intensityAtLabPointWattsPerSquareMeter(Vec3 labPointMeters) {
        if (labPointMeters == null) throw new IllegalArgumentException("point required");
        Vec3 delta = labPointMeters.subtract(waistPositionLabMeters);
        double z = delta.dot(directionLab);
        // Project orthogonally to the propagation axis, avoiding subtraction
        // of large almost-equal squared distances (catastrophic cancellation).
        double r = delta.subtract(directionLab.scale(z)).magnitude();
        return intensityWattsPerSquareMeter(r, z);
    }

    /** Signed Gouy phase, atan(z/zR), in radians. */
    public double gouyPhaseRadians(double axialDistanceMeters) {
        Vec3.requireFinite(axialDistanceMeters, "axialDistanceMeters");
        return Math.atan2(axialDistanceMeters, rayleighRangeMeters());
    }

    /** Phase-front radius of curvature, infinite at the waist. */
    public double curvatureRadiusMeters(double axialDistanceMeters) {
        Vec3.requireFinite(axialDistanceMeters, "axialDistanceMeters");
        if (axialDistanceMeters == 0) return Double.POSITIVE_INFINITY;
        double zr = rayleighRangeMeters();
        return axialDistanceMeters + (zr / axialDistanceMeters) * zr;
    }

    /** q(z)=z+i*zR, expressed in metres; independent of absolute optical phase. */
    public GaussianQ complexParameterAt(double axialDistanceMeters) {
        Vec3.requireFinite(axialDistanceMeters, "axialDistanceMeters");
        return new GaussianQ(axialDistanceMeters, rayleighRangeMeters());
    }
}
