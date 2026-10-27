package dev.coldatomcraft.physics.optics;

/**
 * Physical complex beam parameter q=x+i*y in metres, y>0.
 * For a vacuum Gaussian at a plane: 1/q = 1/R - i*lambda/(pi*w^2).
 * This class contains no global phase or amplitude information.
 */
public record GaussianQ(double realMeters, double imaginaryMeters) {
    public GaussianQ {
        if (!Double.isFinite(realMeters) || !Double.isFinite(imaginaryMeters)
                || imaginaryMeters <= 0) {
            throw new IllegalArgumentException("finite q with positive imaginary part required");
        }
    }

    /** 1/e^2 beam radius of a q parameter at this plane, in metres. */
    public double radiusMeters(double vacuumWavelengthMeters) {
        if (!(vacuumWavelengthMeters > 0) || !Double.isFinite(vacuumWavelengthMeters)) {
            throw new IllegalArgumentException("positive finite vacuum wavelength required");
        }
        // pi*w^2/lambda = imag(q) + real(q)^2/imag(q)
        double squared = (vacuumWavelengthMeters / Math.PI)
                * (imaginaryMeters + realMeters * (realMeters / imaginaryMeters));
        double result = Math.sqrt(squared);
        if (!(result > 0) || !Double.isFinite(result)) {
            throw new ArithmeticException("q-derived beam radius not representable");
        }
        return result;
    }

    /** Signed distance to the current waist along propagation, at this plane. */
    public double waistOffsetMeters() {
        return realMeters;
    }

    /** New Rayleigh range of the same vacuum beam after an optical train. */
    public double rayleighRangeMeters() {
        return imaginaryMeters;
    }

    /** The actual 1/e^2 waist radius after propagation through this system. */
    public double waistRadiusMeters(double vacuumWavelengthMeters) {
        if (!(vacuumWavelengthMeters > 0) || !Double.isFinite(vacuumWavelengthMeters)) {
            throw new IllegalArgumentException("positive finite vacuum wavelength required");
        }
        double result = Math.sqrt(vacuumWavelengthMeters / Math.PI * imaginaryMeters);
        if (!(result > 0) || !Double.isFinite(result)) {
            throw new ArithmeticException("q-derived waist radius not representable");
        }
        return result;
    }
}
