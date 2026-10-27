package dev.coldatomcraft.physics.optics;

/**
 * Finite dimensionless complex arithmetic (or a complex optical phasor when
 * paired with explicit units). No carrier-frequency, power or time semantics
 * are attached to this value itself.
 */
public record ComplexAmplitude(double real, double imaginary) {
    public static final ComplexAmplitude ZERO = new ComplexAmplitude(0, 0);
    public static final ComplexAmplitude ONE = new ComplexAmplitude(1, 0);
    public static final ComplexAmplitude I = new ComplexAmplitude(0, 1);

    public ComplexAmplitude {
        if (!Double.isFinite(real) || !Double.isFinite(imaginary)) {
            throw new IllegalArgumentException("complex amplitude components must be finite");
        }
        if (real == 0) real = 0.0;
        if (imaginary == 0) imaginary = 0.0;
    }

    public static ComplexAmplitude fromPhaseRadians(double phaseRadians) {
        if (!Double.isFinite(phaseRadians)) {
            throw new IllegalArgumentException("finite phase required");
        }
        // Avoid direct sin/cos evaluation on a large input. This does not
        // restore phase detail already lost in the caller's input double.
        double phase = Math.IEEEremainder(phaseRadians, 2 * Math.PI);
        return new ComplexAmplitude(Math.cos(phase), Math.sin(phase));
    }

    public ComplexAmplitude plus(ComplexAmplitude other) {
        if (other == null) throw new IllegalArgumentException("other required");
        return new ComplexAmplitude(real + other.real, imaginary + other.imaginary);
    }

    public ComplexAmplitude minus(ComplexAmplitude other) {
        if (other == null) throw new IllegalArgumentException("other required");
        return new ComplexAmplitude(real - other.real, imaginary - other.imaginary);
    }

    public ComplexAmplitude times(ComplexAmplitude other) {
        if (other == null) throw new IllegalArgumentException("other required");
        return new ComplexAmplitude(
                Math.fma(real, other.real, -imaginary * other.imaginary),
                Math.fma(real, other.imaginary, imaginary * other.real));
    }

    public ComplexAmplitude scale(double scalar) {
        if (!Double.isFinite(scalar)) throw new IllegalArgumentException("finite scalar required");
        return new ComplexAmplitude(real * scalar, imaginary * scalar);
    }

    public ComplexAmplitude conjugate() {
        return new ComplexAmplitude(real, -imaginary);
    }

    public double magnitudeSquared() {
        double magnitude = Math.hypot(real, imaginary);
        double square = magnitude * magnitude;
        if (!Double.isFinite(square)) throw new ArithmeticException("phasor intensity overflow");
        return square;
    }
}
