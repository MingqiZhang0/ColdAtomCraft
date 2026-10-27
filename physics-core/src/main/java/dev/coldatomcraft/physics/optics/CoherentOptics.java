package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Optical field superposition, separate from the Minecraft world.
 *
 * <p>Fully coherent sources must share the exact vacuum carrier wavelength,
 * a single physical phase origin and a matching phaseReferenceKey. Otherwise
 * optical beating/time averaging is outside this model. The scalar quantity
 * |F|^2 is 2c times the cycle-averaged ELECTRIC energy density, NOT net
 * transmitted power for standing waves. The magnetic term is distinct. The leading-order paraxial Poynting vector is returned
 * separately.</p>
 */
public final class CoherentOptics {
    public static final double VACUUM_LIGHT_SPEED_METERS_PER_SECOND = 299_792_458.0;
    private CoherentOptics() {}

    /** Fields use sqrt(W/m^2) normalization; H = sum(k_hat x F) in that scale. */
    public record Result(VectorFieldPhasor electric, VectorFieldPhasor normalizedMagnetic) {
        public Result {
            if (electric == null || normalizedMagnetic == null) {
                throw new IllegalArgumentException("electric/magnetic fields required");
            }
        }

        public double electricIntensityEquivalentWattsPerSquareMeter() {
            return electric.electricIntensityEquivalentWattsPerSquareMeter();
        }

        /** Cycle-averaged electric energy density epsilon0*|E_peak|^2/4. */
        public double electricEnergyDensityJoulesPerCubicMeter() {
            return electric.electricIntensityEquivalentWattsPerSquareMeter()
                    / (2 * VACUUM_LIGHT_SPEED_METERS_PER_SECOND);
        }

        /** Cycle-averaged magnetic energy density B_peak^2/(4*mu0). */
        public double magneticEnergyDensityJoulesPerCubicMeter() {
            return normalizedMagnetic.electricIntensityEquivalentWattsPerSquareMeter()
                    / (2 * VACUUM_LIGHT_SPEED_METERS_PER_SECOND);
        }

        /** Leading-order total electromagnetic energy density. */
        public double totalEnergyDensityJoulesPerCubicMeter() {
            return electricEnergyDensityJoulesPerCubicMeter()
                    + magneticEnergyDensityJoulesPerCubicMeter();
        }

        /** Leading-order paraxial, time-averaged Poynting vector [W/m^2]. */
        public Vec3 timeAveragePoyntingWattsPerSquareMeter() {
            return electric.averagePoyntingWith(normalizedMagnetic);
        }
    }

    /** Coherently sum electric and normalized magnetic phasors at one lab point. */
    public static Result coherentAtLabPoint(Vec3 pointMeters, List<JonesGaussianBeam> beams) {
        if (pointMeters == null) throw new IllegalArgumentException("sample point required");
        validateNonemptyUniqueBeams(beams);
        String reference = beams.get(0).phaseReferenceKey();
        double wavelength = beams.get(0).beam().vacuumWavelengthMeters();
        VectorFieldPhasor e = VectorFieldPhasor.ZERO;
        VectorFieldPhasor h = VectorFieldPhasor.ZERO;
        for (JonesGaussianBeam beam : beams) {
            if (!reference.equals(beam.phaseReferenceKey())
                    || Double.compare(wavelength, beam.beam().vacuumWavelengthMeters()) != 0) {
                throw new IllegalArgumentException("coherent beams require matching phase reference and carrier wavelength");
            }
            VectorFieldPhasor sample = beam.sampleAtLabPoint(pointMeters);
            e = e.plus(sample);
            h = h.plus(sample.directionCross(beam.beam().directionLab()));
        }
        return new Result(e, h);
    }

    /**
     * Time/ensemble-averaged electric intensity equivalent for independent
     * phase reference groups. Different wavelengths are permitted here.
     * Deliberately rejects two beams from the SAME declared coherence group.
     */
    public static double incoherentElectricIntensityEquivalentAtLabPoint(
            Vec3 pointMeters, List<JonesGaussianBeam> beams) {
        if (pointMeters == null) throw new IllegalArgumentException("sample point required");
        validateNonemptyUniqueBeams(beams);
        Set<String> groups = new HashSet<>();
        double sum = 0;
        for (JonesGaussianBeam beam : beams) {
            if (!groups.add(beam.phaseReferenceKey())) {
                throw new IllegalArgumentException("shared phase reference must not be silently summed incoherently");
            }
            sum += beam.sampleAtLabPoint(pointMeters).electricIntensityEquivalentWattsPerSquareMeter();
        }
        if (!Double.isFinite(sum)) throw new ArithmeticException("incoherent intensity overflow");
        return sum;
    }

    private static void validateNonemptyUniqueBeams(List<JonesGaussianBeam> beams) {
        if (beams == null || beams.isEmpty()) {
            throw new IllegalArgumentException("at least one beam required");
        }
        Set<String> ids = new HashSet<>();
        for (JonesGaussianBeam beam : beams) {
            if (beam == null || !ids.add(beam.beamId())) {
                throw new IllegalArgumentException("null or duplicate physical beam ID");
            }
        }
    }
}
