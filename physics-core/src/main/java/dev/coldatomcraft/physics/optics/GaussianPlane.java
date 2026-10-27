package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Optical power and Gaussian q at one transverse reference plane in vacuum.
 *
 * <p>Ideal free space and infinitely thin, lossless lenses change q but do not
 * change P. This class deliberately has no absolute beam phase or physical
 * position/pointing geometry; optical elements are modeled in the local beam
 * frame. Aperture loss, polarization, interfaces and interference are outside
 * the OPT-001 draft reference, and nothing here can mutate the game world.</p>
 */
public record GaussianPlane(double powerWatts, double vacuumWavelengthMeters,
                            GaussianQ q) {
    public GaussianPlane {
        Vec3.requireFinite(powerWatts, "powerWatts");
        Vec3.requireFinite(vacuumWavelengthMeters, "vacuumWavelengthMeters");
        if (powerWatts < 0 || vacuumWavelengthMeters <= 0 || q == null) {
            throw new IllegalArgumentException("nonnegative P, positive wavelength, q required");
        }
        double radius = q.radiusMeters(vacuumWavelengthMeters);
        double peak = powerWatts == 0 ? 0 : (2 / Math.PI) * (powerWatts / radius) / radius;
        if (!Double.isFinite(peak)) {
            throw new IllegalArgumentException("plane peak intensity is not representable");
        }
    }

    /** Explicitly sample a waist-centred beam at its signed axial position. */
    public static GaussianPlane atAxialDistance(GaussianBeam beam, double zMeters) {
        if (beam == null) throw new IllegalArgumentException("beam required");
        return new GaussianPlane(beam.powerWatts(), beam.vacuumWavelengthMeters(),
                beam.complexParameterAt(zMeters));
    }

    public double radiusMeters() {
        return q.radiusMeters(vacuumWavelengthMeters);
    }

    /** Average irradiance I(r) on this plane, W/m^2. */
    public double intensityWattsPerSquareMeter(double radiusFromAxisMeters) {
        if (!Double.isFinite(radiusFromAxisMeters) || radiusFromAxisMeters < 0) {
            throw new IllegalArgumentException("nonnegative finite radial coordinate required");
        }
        double w = radiusMeters();
        double normalizedRadius = radiusFromAxisMeters / w;
        return (powerWatts == 0 ? 0 : (2 / Math.PI) * (powerWatts / w) / w)
                * Math.exp(-2 * normalizedRadius * normalizedRadius);
    }

    /** Power enclosed by an unobstructed, centred circular test aperture, W. */
    public double powerWithinRadiusWatts(double apertureRadiusMeters) {
        if (!Double.isFinite(apertureRadiusMeters) || apertureRadiusMeters < 0) {
            throw new IllegalArgumentException("nonnegative finite aperture radius required");
        }
        double r = apertureRadiusMeters / radiusMeters();
        return powerWatts * (-Math.expm1(-2 * r * r));
    }

    /** Lossless propagation in the local optical frame; output P equals input P. */
    public GaussianPlane through(ParaxialAbcd optics) {
        if (optics == null) throw new IllegalArgumentException("optical system required");
        return new GaussianPlane(powerWatts, vacuumWavelengthMeters, optics.transform(q));
    }
}
