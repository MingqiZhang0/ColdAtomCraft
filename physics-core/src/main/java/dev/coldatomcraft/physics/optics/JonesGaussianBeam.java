package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * A fully polarized CW Gaussian beam, as a complex electric-field reference.
 * Source power and Gaussian spatial mode come from {@link GaussianBeam}.
 * Jones amplitudes are in a local right-handed frame (x, k x x, k), with
 * |J|^2 <= 1 to represent passive uniform polarization optics. The beam's
 * actual optical power after Jones attenuation is P_source * |J|^2.
 *
 * <p>All beams with a common phaseReferenceKey MUST have the same optical
 * carrier frequency and a phaseAtWaistRadians expressed against the same
 * physical time/phase origin. The string is a declaration, NOT proof of laser
 * coherence or an Owner physics approval. It is never accepted as a world
 * mutation authority.</p>
 *
 * <p>Valid only for monochromatic scalar-paraxial TEM00 spatial profiles
 * with transverse Jones polarization. The phase at the waist is a supplied
 * optical phase, not inferred from a Minecraft laser port.</p>
 */
public record JonesGaussianBeam(String beamId,
                                String phaseReferenceKey,
                                GaussianBeam beam,
                                Vec3 transverseXAxisLab,
                                JonesVector jones,
                                double phaseAtWaistRadians) {
    public JonesGaussianBeam {
        if (beamId == null || beamId.isBlank() || phaseReferenceKey == null
                || phaseReferenceKey.isBlank()) {
            throw new IllegalArgumentException("nonempty beam ID and shared phase reference required");
        }
        if (beam == null || transverseXAxisLab == null || jones == null
                || !Double.isFinite(phaseAtWaistRadians)) {
            throw new IllegalArgumentException("beam, Jones field, basis and finite phase required");
        }
        transverseXAxisLab = transverseXAxisLab.normalized();
        if (Math.abs(transverseXAxisLab.dot(beam.directionLab())) > 1e-12) {
            throw new IllegalArgumentException("transverse X must be perpendicular to beam direction");
        }
        double fraction = jones.squaredNorm();
        if (fraction > 1 + 1e-12) {
            throw new IllegalArgumentException("reference only supports passive Jones amplitudes");
        }
        phaseAtWaistRadians = Math.IEEEremainder(phaseAtWaistRadians, 2 * Math.PI);
    }

    public Vec3 transverseYAxisLab() {
        return beam.directionLab().cross(transverseXAxisLab).normalized();
    }

    /** Optical power remaining after the uniform Jones element(s). */
    public double transmittedPowerWatts() {
        return beam.powerWatts() * jones.squaredNorm();
    }

    /**
     * Electric phasor at a laboratory point in normalized sqrt(W/m^2) units.
     * Standard Gaussian phase: kz + k*r^2/(2R) - Gouy + phaseAtWaist.
     * Time convention is exp(-i omega t). Relative phases must share a
     * physical reference, not merely similar numerical wavelengths.
     */
    public VectorFieldPhasor sampleAtLabPoint(Vec3 labPointMeters) {
        if (labPointMeters == null) throw new IllegalArgumentException("lab point required");
        Vec3 displacement = labPointMeters.subtract(beam.waistPositionLabMeters());
        double z = displacement.dot(beam.directionLab());
        double r = displacement.subtract(beam.directionLab().scale(z)).magnitude();
        double sqrtIrradiance = Math.sqrt(beam.intensityWattsPerSquareMeter(r, z));
        if (sqrtIrradiance == 0 || jones.squaredNorm() == 0) {
            return VectorFieldPhasor.ZERO;
        }
        double wavelength = beam.vacuumWavelengthMeters();
        // Reduce propagation phase modulo wavelength *before* multiplying by
        // k: direct 2*pi*z/lambda has avoidable overflow/precision problems.
        // Large position uncertainty remains a physical input limitation.
        double carrier = 2 * Math.PI * (Math.IEEEremainder(z, wavelength) / wavelength);
        double h = Math.hypot(z, beam.rayleighRangeMeters());
        // k*r^2/(2R): algebraically pi*(r/lambda)*(r/h)*(z/h).
        double curvature = Math.PI * (r / wavelength) * (r / h) * (z / h);
        double phase = carrier + curvature - beam.gouyPhaseRadians(z) + phaseAtWaistRadians;
        ComplexAmplitude factor = ComplexAmplitude.fromPhaseRadians(phase).scale(sqrtIrradiance);
        ComplexAmplitude localX = jones.x().times(factor);
        ComplexAmplitude localY = jones.y().times(factor);
        Vec3 basisY = transverseYAxisLab();
        return new VectorFieldPhasor(
                localX.scale(transverseXAxisLab.x()).plus(localY.scale(basisY.x())),
                localX.scale(transverseXAxisLab.y()).plus(localY.scale(basisY.y())),
                localX.scale(transverseXAxisLab.z()).plus(localY.scale(basisY.z())));
    }
}
