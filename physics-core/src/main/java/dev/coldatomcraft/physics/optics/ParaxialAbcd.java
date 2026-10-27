package dev.coldatomcraft.physics.optics;

/**
 * Real ABCD ray-transfer matrix at a reference plane, same homogeneous medium
 * on both sides (in this milestone: vacuum), with determinant +1.
 *
 * <p>Transforms (transverse position [m], ray angle [rad]) and the Gaussian
 * q parameter: q_out=(A*q_in+B)/(C*q_in+D). A/D are dimensionless,
 * B has units m and C has units 1/m. The matrix describes only ideal
 * free space and infinitely thin lenses, not polarization, transmission loss,
 * aberrations, or surface apertures.</p>
 */
public record ParaxialAbcd(double a, double bMeters, double cPerMeter, double d) {
    public ParaxialAbcd {
        if (!Double.isFinite(a) || !Double.isFinite(bMeters)
                || !Double.isFinite(cPerMeter) || !Double.isFinite(d)) {
            throw new IllegalArgumentException("finite ABCD coefficients required");
        }
        double det = Math.fma(a, d, -bMeters * cPerMeter);
        if (!Double.isFinite(det) || Math.abs(det - 1.0) > 1e-9) {
            throw new IllegalArgumentException("vacuum ABCD must have determinant +1");
        }
    }

    public static ParaxialAbcd identity() {
        return new ParaxialAbcd(1, 0, 0, 1);
    }

    /** Signed propagation distance; negative values model inverse propagation. */
    public static ParaxialAbcd freeSpace(double distanceMeters) {
        return new ParaxialAbcd(1, distanceMeters, 0, 1);
    }

    /** Positive f focuses; negative f diverges. An ideal, lossless thin lens. */
    public static ParaxialAbcd thinLens(double focalLengthMeters) {
        if (!Double.isFinite(focalLengthMeters) || focalLengthMeters == 0) {
            throw new IllegalArgumentException("finite nonzero focal length required");
        }
        return new ParaxialAbcd(1, 0, -1.0 / focalLengthMeters, 1);
    }

    /** Apply this train first, then the {@code following} matrix. */
    public ParaxialAbcd then(ParaxialAbcd following) {
        if (following == null) throw new IllegalArgumentException("following matrix required");
        return new ParaxialAbcd(
                Math.fma(following.a, a, following.bMeters * cPerMeter),
                Math.fma(following.a, bMeters, following.bMeters * d),
                Math.fma(following.cPerMeter, a, following.d * cPerMeter),
                Math.fma(following.cPerMeter, bMeters, following.d * d));
    }

    /** Transform a physical q in metres, preserving a positive imaginary part. */
    public GaussianQ transform(GaussianQ q) {
        if (q == null) throw new IllegalArgumentException("incident q required");
        double x = q.realMeters();
        double y = q.imaginaryMeters();
        double u = Math.fma(a, x, bMeters);
        double v = a * y;
        double p = Math.fma(cPerMeter, x, d);
        double t = cPerMeter * y;
        double real;
        // For real ABCD, Im(q_out) = det(M)*Im(q_in)/|C*q_in+D|^2.
        // Computing Im by subtracting almost equal terms loses every bit of
        // significance for a strongly focusing lens / nearly real q.
        double denominatorMagnitude = Math.hypot(p, t);
        double determinant = Math.fma(a, d, -bMeters * cPerMeter);
        double imag = ((y / denominatorMagnitude) * determinant) / denominatorMagnitude;
        // Complex division by Smith's algorithm avoids squaring the
        // denominator, which needlessly overflows for strongly focused beams.
        if (Math.abs(p) >= Math.abs(t)) {
            double ratio = t / p;
            double denom = p + t * ratio;
            real = (u + v * ratio) / denom;
        } else {
            double ratio = p / t;
            double denom = t + p * ratio;
            real = (u * ratio + v) / denom;
        }
        return new GaussianQ(real, imag);
    }
}
