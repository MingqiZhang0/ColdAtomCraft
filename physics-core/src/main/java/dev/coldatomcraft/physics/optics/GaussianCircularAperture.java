package dev.coldatomcraft.physics.optics;

/**
 * Ideal, normally illuminated circular-aperture collection of a scalar TEM00
 * Gaussian irradiance profile. No detector responsivity, absorption, time
 * averaging, electronic readout or material model is implied.
 *
 * <p>The beam has a 1/e^2-intensity radius w at the aperture plane. A circular
 * aperture of radius a is laterally displaced from the optical axis by d.
 * All dimensions are in metres. The collected fraction is the integral of
 * (2/(pi*w^2))*exp(-2*r^2/w^2) over the offset disk.</p>
 *
 * <p>For d=0 this reduces analytically to 1-exp(-2*a^2/w^2). Otherwise the
 * implementation integrates the azimuthal fraction of concentric irradiance
 * rings, using a fixed 96-point Gauss-Legendre quadrature with a sin^2 endpoint
 * transform. It is a numerically tested reference, not a certified error-bound
 * implementation for arbitrarily extreme floating-point geometries.</p>
 */
public final class GaussianCircularAperture {
    private static final int QUADRATURE_POINTS = 96;
    private static final double[] NODES = new double[QUADRATURE_POINTS];
    private static final double[] WEIGHTS = new double[QUADRATURE_POINTS];
    // At r/w=9, the omitted total Gaussian power is exp(-162), < 5e-71.
    private static final double CUTOFF_RADII = 9.0;

    static {
        // Legendre roots/weights on [-1,1]. Arrays are private and immutable
        // after initialization, so repeated measurement is deterministic.
        int n = QUADRATURE_POINTS;
        for (int i = 0; i < (n + 1) / 2; i++) {
            double root = Math.cos(Math.PI * (i + 0.75) / (n + 0.5));
            double derivative = 0;
            boolean converged = false;
            for (int iteration = 0; iteration < 32; iteration++) {
                double previous = 1, beforePrevious = 0;
                for (int k = 1; k <= n; k++) {
                    double current = ((2 * k - 1) * root * previous
                            - (k - 1) * beforePrevious) / k;
                    beforePrevious = previous;
                    previous = current;
                }
                derivative = n * (root * previous - beforePrevious)
                        / (root * root - 1.0);
                double next = root - previous / derivative;
                if (Math.abs(next - root) <= 1e-15) {
                    root = next;
                    converged = true;
                    break;
                }
                root = next;
            }
            if (!converged || !Double.isFinite(root)) {
                throw new ExceptionInInitializerError("Gauss-Legendre roots did not converge");
            }
            // Recompute derivative at the final root for the quadrature weight.
            double previous = 1, beforePrevious = 0;
            for (int k = 1; k <= n; k++) {
                double current = ((2 * k - 1) * root * previous
                        - (k - 1) * beforePrevious) / k;
                beforePrevious = previous;
                previous = current;
            }
            derivative = n * (root * previous - beforePrevious)
                    / (root * root - 1.0);
            double weight = 2.0 / ((1.0 - root * root) * derivative * derivative);
            NODES[i] = -root;
            NODES[n - 1 - i] = root;
            WEIGHTS[i] = weight;
            WEIGHTS[n - 1 - i] = weight;
        }
    }

    private GaussianCircularAperture() {}

    public record Capture(double incidentPowerWatts,
                          double capturedPowerWatts,
                          double uncapturedPowerWatts,
                          double spotRadiusMeters,
                          double apertureRadiusMeters,
                          double offsetMeters) {
        public Capture {
            if (!Double.isFinite(incidentPowerWatts) || incidentPowerWatts < 0
                    || !Double.isFinite(capturedPowerWatts) || capturedPowerWatts < 0
                    || !Double.isFinite(uncapturedPowerWatts) || uncapturedPowerWatts < 0
                    || !Double.isFinite(spotRadiusMeters) || spotRadiusMeters <= 0
                    || !Double.isFinite(apertureRadiusMeters) || apertureRadiusMeters < 0
                    || !Double.isFinite(offsetMeters) || offsetMeters < 0) {
                throw new IllegalArgumentException("invalid ideal aperture capture");
            }
            // Up to one ULP may be lost when P - captured rounds; do not
            // silently assert that missed power represents material absorption.
            double mismatch = Math.abs((capturedPowerWatts + uncapturedPowerWatts)
                    - incidentPowerWatts);
            if (mismatch > 4 * Math.ulp(incidentPowerWatts)) {
                throw new IllegalArgumentException("optical power budget not conserved");
            }
        }

        public double capturedFraction() {
            return incidentPowerWatts == 0 ? 0 : capturedPowerWatts / incidentPowerWatts;
        }
    }

    public static Capture capture(GaussianPlane plane, double apertureRadiusMeters,
                                  double offsetMeters) {
        if (plane == null) throw new IllegalArgumentException("Gaussian plane required");
        return capture(plane.powerWatts(), plane.radiusMeters(),
                apertureRadiusMeters, offsetMeters);
    }

    public static Capture capture(double incidentPowerWatts,
                                  double spotRadiusMeters,
                                  double apertureRadiusMeters,
                                  double offsetMeters) {
        if (!Double.isFinite(incidentPowerWatts) || incidentPowerWatts < 0) {
            throw new IllegalArgumentException("incident power must be finite and nonnegative");
        }
        double fraction = capturedFraction(spotRadiusMeters, apertureRadiusMeters,
                offsetMeters);
        double captured = incidentPowerWatts * fraction;
        if (!Double.isFinite(captured)) {
            throw new ArithmeticException("captured power is not representable");
        }
        return new Capture(incidentPowerWatts, captured, incidentPowerWatts - captured,
                spotRadiusMeters, apertureRadiusMeters, offsetMeters);
    }

    /** Fraction of ideal TEM00 power within an offset disk, dimensionless [0,1]. */
    public static double capturedFraction(double spotRadiusMeters,
                                          double apertureRadiusMeters,
                                          double offsetMeters) {
        if (!Double.isFinite(spotRadiusMeters) || spotRadiusMeters <= 0
                || !Double.isFinite(apertureRadiusMeters) || apertureRadiusMeters < 0
                || !Double.isFinite(offsetMeters) || offsetMeters < 0) {
            throw new IllegalArgumentException("finite w>0, a>=0, d>=0 required");
        }
        double a = apertureRadiusMeters / spotRadiusMeters;
        double d = offsetMeters / spotRadiusMeters;
        if (!Double.isFinite(a) || !Double.isFinite(d)
                || !Double.isFinite(a + d) || a + d > 1e150) {
            throw new IllegalArgumentException("unrepresentable normalized aperture geometry");
        }
        if (a == 0) return 0;
        if (d == 0) return -Math.expm1(-2.0 * a * a);
        if (a - d >= CUTOFF_RADII) return 1.0;
        if (d - a >= CUTOFF_RADII) return 0.0;
        // In the tiny-geometry regime the disk's irradiance is constant to
        // relative O((a+d)^2). Avoid the 0/0 risk in the angular fraction.
        if (a + d < 1e-8) return 2.0 * a * a * Math.exp(-2.0 * d * d);

        double interiorRadius = Math.max(0.0, a - d);
        double interiorFraction = -Math.expm1(-2.0 * interiorRadius * interiorRadius);
        double lower = Math.abs(a - d);
        double upper = Math.min(CUTOFF_RADII, a + d);
        if (upper <= lower) return interiorFraction;

        // For each ring r/w=u, the angular fraction enclosed by the offset
        // disk is acos((u^2 + d^2 - a^2)/(2*u*d))/pi. Integrate the radial
        // power density 4*u*exp(-2*u^2). The sin^2 transform smooths the
        // square-root endpoint behavior at internal tangencies.
        double total = 0.0;
        double width = upper - lower;
        for (int i = 0; i < QUADRATURE_POINTS; i++) {
            double t = (NODES[i] + 1.0) * (Math.PI / 4.0);
            double s = Math.sin(t);
            double c = Math.cos(t);
            double u = lower + width * s * s;
            double duDt = 2.0 * width * s * c;
            // Factor d^2-a^2 so offset~radius retains precision.
            double angularCosine = (u * u + (d - a) * (d + a)) / (2.0 * u * d);
            double angle = Math.acos(Math.max(-1.0, Math.min(1.0, angularCosine)));
            double integrand = (4.0 / Math.PI) * u * Math.exp(-2.0 * u * u)
                    * angle * duDt;
            total += WEIGHTS[i] * integrand;
        }
        double fraction = interiorFraction + (Math.PI / 4.0) * total;
        if (!Double.isFinite(fraction) || fraction < -1e-13 || fraction > 1 + 1e-13) {
            throw new ArithmeticException("aperture integral outside physical power bounds");
        }
        return Math.max(0.0, Math.min(1.0, fraction));
    }
}
