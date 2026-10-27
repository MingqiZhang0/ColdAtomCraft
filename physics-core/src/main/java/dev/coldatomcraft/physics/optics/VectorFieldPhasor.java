package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;

/**
 * Three Cartesian components of a complex electric-field phasor, scaled to
 * sqrt(W/m^2) instead of V/m: F = sqrt(epsilon0*c/2) * E_peak in vacuum.
 * Hence |F|^2 is an ELECTRIC-field intensity equivalent. For a single
 * paraxial travelling beam it equals the usual irradiance. For counter-
 * propagating waves it does not equal net energy flux; see Poynting calculation.
 */
public record VectorFieldPhasor(ComplexAmplitude x,
                                ComplexAmplitude y,
                                ComplexAmplitude z) {
    public static final VectorFieldPhasor ZERO = new VectorFieldPhasor(
            ComplexAmplitude.ZERO, ComplexAmplitude.ZERO, ComplexAmplitude.ZERO);

    public VectorFieldPhasor {
        if (x == null || y == null || z == null) {
            throw new IllegalArgumentException("all 3 complex components required");
        }
    }

    public VectorFieldPhasor plus(VectorFieldPhasor other) {
        if (other == null) throw new IllegalArgumentException("other required");
        return new VectorFieldPhasor(x.plus(other.x), y.plus(other.y), z.plus(other.z));
    }

    public double electricIntensityEquivalentWattsPerSquareMeter() {
        double value = x.magnitudeSquared() + y.magnitudeSquared() + z.magnitudeSquared();
        if (!Double.isFinite(value)) throw new ArithmeticException("electric intensity overflow");
        return value;
    }

    /** Direction x this electric phasor: normalized magnetic H amplitude. */
    public VectorFieldPhasor directionCross(Vec3 direction) {
        if (direction == null) throw new IllegalArgumentException("direction required");
        return new VectorFieldPhasor(z.scale(direction.y()).minus(y.scale(direction.z())),
                x.scale(direction.z()).minus(z.scale(direction.x())),
                y.scale(direction.x()).minus(x.scale(direction.y())));
    }

    /**
     * Real(E x H*) in normalized vacuum units [W/m^2]. H must have already
     * been assembled from each beam's k_hat x E. This is leading-order
     * paraxial Poynting; vectorial nonparaxial corrections are NOT included.
     */
    public Vec3 averagePoyntingWith(VectorFieldPhasor normalizedMagnetic) {
        if (normalizedMagnetic == null) throw new IllegalArgumentException("magnetic phasor required");
        return new Vec3(
                y.times(normalizedMagnetic.z.conjugate()).minus(
                        z.times(normalizedMagnetic.y.conjugate())).real(),
                z.times(normalizedMagnetic.x.conjugate()).minus(
                        x.times(normalizedMagnetic.z.conjugate())).real(),
                x.times(normalizedMagnetic.y.conjugate()).minus(
                        y.times(normalizedMagnetic.x.conjugate())).real());
    }
}
