package dev.coldatomcraft.physics.optics;

/**
 * Jones field amplitudes along an explicitly supplied transverse basis.
 * The pair is NOT automatically normalized: after an ideal polarizer its
 * squared norm is the fraction of incident optical power transmitted.
 * For a physical input state use a unit-norm Jones vector.
 *
 * <p>The phasor uses exp(-i omega t) time dependence. Handedness labels for
 * circular polarization additionally require the propagation/basis convention.</p>
 */
public record JonesVector(ComplexAmplitude x, ComplexAmplitude y) {
    public JonesVector {
        if (x == null || y == null) throw new IllegalArgumentException("both Jones components required");
    }

    public static JonesVector horizontal() {
        return new JonesVector(ComplexAmplitude.ONE, ComplexAmplitude.ZERO);
    }

    public static JonesVector vertical() {
        return new JonesVector(ComplexAmplitude.ZERO, ComplexAmplitude.ONE);
    }

    public static JonesVector linearRadians(double angle) {
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("finite angle required");
        return new JonesVector(new ComplexAmplitude(Math.cos(angle), 0),
                new ComplexAmplitude(Math.sin(angle), 0));
    }

    public double squaredNorm() {
        double total = x.magnitudeSquared() + y.magnitudeSquared();
        if (!Double.isFinite(total)) throw new ArithmeticException("Jones norm overflow");
        return total;
    }

    public JonesVector times(ComplexAmplitude scalar) {
        if (scalar == null) throw new IllegalArgumentException("complex scale required");
        return new JonesVector(x.times(scalar), y.times(scalar));
    }

    public JonesVector scale(double scalar) {
        return new JonesVector(x.scale(scalar), y.scale(scalar));
    }

    public JonesVector plus(JonesVector other) {
        if (other == null) throw new IllegalArgumentException("other required");
        return new JonesVector(x.plus(other.x), y.plus(other.y));
    }
}
