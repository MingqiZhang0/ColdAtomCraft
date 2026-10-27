package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Lab-local geometric reference plane for an ideal, front-illuminated power
 * receiver. The face normal points TOWARD the arriving beam, so for a beam
 * propagating along k the accepted orientation is n dot k = -1.
 *
 * <p>Only a detector perpendicular to one TEM00 Gaussian axis is supported.
 * Oblique incidence, finite-angle diffraction, surface reflection, backside
 * pickup, detector electrical response and beam occlusion are NOT modeled.</p>
 */
public record IdealCircularDetectorFace(Vec3 centerLabMeters,
                                        Vec3 facingNormalLab,
                                        double apertureRadiusMeters) {
    public IdealCircularDetectorFace {
        if (centerLabMeters == null || facingNormalLab == null
                || !Double.isFinite(apertureRadiusMeters) || apertureRadiusMeters <= 0) {
            throw new IllegalArgumentException("finite face position, normal and positive aperture radius required");
        }
        facingNormalLab = facingNormalLab.normalized();
    }

    /**
     * Ideal cross-sectional power captured at the face. The waist may be
     * upstream or downstream of this plane because waist != emitter port;
     * source emission and obscuration must be checked by the future apparatus
     * geometry layer. The analytic beam is not itself truncated by this probe.
     */
    public GaussianCircularAperture.Capture capture(GaussianBeam beam) {
        if (beam == null) throw new IllegalArgumentException("beam required");
        double alignment = facingNormalLab.dot(beam.directionLab());
        if (Math.abs(alignment + 1.0) > 1e-12) {
            throw new IllegalArgumentException("front-illuminated detector must be normal to the beam; oblique incidence unsupported");
        }
        Vec3 displacement = centerLabMeters.subtract(beam.waistPositionLabMeters());
        double z = displacement.dot(beam.directionLab());
        double spotRadius = beam.radiusMeters(z);
        // Global Minecraft-scale doubles may have already erased a micron
        // offset. Reject when one coordinate ULP exceeds a relevant spatial
        // measurement scale, instead of reporting a fictitious centred hit.
        double coordinateUlp = Math.max(
                Math.max(Math.ulp(centerLabMeters.x()), Math.ulp(centerLabMeters.y())),
                Math.max(Math.ulp(centerLabMeters.z()), Math.max(
                        Math.max(Math.ulp(beam.waistPositionLabMeters().x()),
                                Math.ulp(beam.waistPositionLabMeters().y())),
                        Math.ulp(beam.waistPositionLabMeters().z()))));
        if (coordinateUlp >= Math.min(spotRadius, apertureRadiusMeters)) {
            throw new IllegalArgumentException("lab coordinate precision exceeds optical feature size; use a local origin");
        }
        double offset = displacement.subtract(beam.directionLab().scale(z)).magnitude();
        return GaussianCircularAperture.capture(beam.powerWatts(),
                spotRadius, apertureRadiusMeters, offset);
    }
}
