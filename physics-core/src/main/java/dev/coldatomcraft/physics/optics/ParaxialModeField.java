package dev.coldatomcraft.physics.optics;

/**
 * Complex modal amplitudes (sqrt(W)) for one matched, transverse TEM00 mode
 * at one reference plane in vacuum. The full optical carrier phase is in the
 * Jones amplitudes; the q parameter contains the spatial envelope/curvature.
 *
 * <p>This is a declared mode, not a physical ray geometry, port location,
 * server authority, or coherent-source measurement. Both reference keys must
 * be supplied by an external laboratory calibration. No world effects.</p>
 *
 * <p>With exp(-i omega t), the transverse field is proportional to
 * a*sqrt(2/(pi*w^2))*exp(-r^2/w^2)*exp(+i*k*r^2/(2*R)).
 * A free-space step L advances the modal phase by
 * k*L + arg(q_in*) - arg(q_out*), while preserving its power.</p>
 */
public record ParaxialModeField(String phaseReferenceKey,
                                String modeFrameKey,
                                double vacuumWavelengthMeters,
                                GaussianQ q,
                                JonesVector modalAmplitudeSqrtWatts) {
    public ParaxialModeField {
        if (phaseReferenceKey == null || phaseReferenceKey.isBlank()
                || modeFrameKey == null || modeFrameKey.isBlank()
                || !Double.isFinite(vacuumWavelengthMeters)
                || vacuumWavelengthMeters <= 0 || q == null
                || modalAmplitudeSqrtWatts == null) {
            throw new IllegalArgumentException("phase/mode keys, wavelength, q and amplitude required");
        }
        double power = modalAmplitudeSqrtWatts.squaredNorm();
        double radius = q.radiusMeters(vacuumWavelengthMeters);
        double peak = (2.0 / Math.PI) * (power / radius) / radius;
        if (!Double.isFinite(peak)) {
            throw new IllegalArgumentException("modal peak irradiance is not representable");
        }
    }

    /** Create a field at an explicitly specified plane and phase origin. */
    public static ParaxialModeField fromPlane(String phaseReferenceKey,
                                              String modeFrameKey,
                                              GaussianPlane plane,
                                              JonesVector unitPolarization,
                                              double phaseRadiansAtPlane) {
        if (plane == null || unitPolarization == null) {
            throw new IllegalArgumentException("plane and normalized Jones polarization required");
        }
        double norm = unitPolarization.squaredNorm();
        if (Math.abs(norm - 1.0) > 1e-12) {
            throw new IllegalArgumentException("incident Jones polarization must have unit power");
        }
        JonesVector modal = unitPolarization.scale(Math.sqrt(plane.powerWatts()))
                .times(ComplexAmplitude.fromPhaseRadians(phaseRadiansAtPlane));
        return new ParaxialModeField(phaseReferenceKey, modeFrameKey,
                plane.vacuumWavelengthMeters(), plane.q(), modal);
    }

    public double powerWatts() {
        return modalAmplitudeSqrtWatts.squaredNorm();
    }

    public GaussianPlane gaussianPlane() {
        return new GaussianPlane(powerWatts(), vacuumWavelengthMeters, q);
    }

    public ParaxialModeField zeroLike() {
        return new ParaxialModeField(phaseReferenceKey, modeFrameKey,
                vacuumWavelengthMeters, q,
                new JonesVector(ComplexAmplitude.ZERO, ComplexAmplitude.ZERO));
    }

    /**
     * A numerical precision diagnostic, NOT a measurement uncertainty:
     * carrier-phase radians corresponding to one floating-point ULP of a
     * supplied optical path length. A relative interferometer requiring
     * tighter phase resolution must use a calibrated path-length/phase input,
     * not infer sub-wavelength detail from imprecise world coordinates.
     */
    public double inputPathUlpPhaseRadians(double distanceMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("nonnegative finite optical path required");
        }
        return 2.0 * Math.PI * (Math.ulp(distanceMeters) / vacuumWavelengthMeters);
    }

    /** Reference-plane intensity-equivalent Jones field in sqrt(W/m^2). */
    public JonesVector fieldAtRadiusMeters(double radiusMeters) {
        if (!Double.isFinite(radiusMeters) || radiusMeters < 0) {
            throw new IllegalArgumentException("finite nonnegative radius required");
        }
        double width = q.radiusMeters(vacuumWavelengthMeters);
        double normalizedRadius = radiusMeters / width;
        double envelope = (Math.sqrt(2.0 / Math.PI) / width)
                * Math.exp(-normalizedRadius * normalizedRadius);
        if (envelope == 0) {
            return new JonesVector(ComplexAmplitude.ZERO, ComplexAmplitude.ZERO);
        }
        // For q=x+i*y, 1/R=x/(x*x+y*y). Factorization avoids squaring
        // either a large x or y and preserves the correct lens phase.
        double h = Math.hypot(q.realMeters(), q.imaginaryMeters());
        double curvature = Math.PI * (radiusMeters / vacuumWavelengthMeters)
                * (radiusMeters / h) * (q.realMeters() / h);
        ComplexAmplitude factor = ComplexAmplitude.fromPhaseRadians(curvature).scale(envelope);
        return modalAmplitudeSqrtWatts.times(factor);
    }

    /** Physical forward travel along the current local axis; no world geometry. */
    public ParaxialModeField freeSpace(double distanceMeters) {
        if (!Double.isFinite(distanceMeters) || distanceMeters < 0) {
            throw new IllegalArgumentException("nonnegative finite forward optical path required");
        }
        GaussianQ outQ = ParaxialAbcd.freeSpace(distanceMeters).transform(q);
        // q*=x-i*y. At the waist the change is -atan(L/zR), i.e. Gouy.
        // Keeping it as a difference of atan2 angles handles waist crossings.
        double gouyPhaseIncrement = Math.atan2(q.imaginaryMeters(), outQ.realMeters())
                - Math.atan2(q.imaginaryMeters(), q.realMeters());
        // Modular reduction is an improvement, not a promise to reconstruct
        // sub-wavelength precision lost in the caller's input distance.
        double carrierCyclesFraction = Math.IEEEremainder(distanceMeters,
                vacuumWavelengthMeters) / vacuumWavelengthMeters;
        double phase = 2.0 * Math.PI * carrierCyclesFraction + gouyPhaseIncrement;
        return withQAndModalAmplitude(outQ,
                modalAmplitudeSqrtWatts.times(ComplexAmplitude.fromPhaseRadians(phase)));
    }

    /** Thin lossless lens: q changes; on-axis phase at the same plane does not. */
    public ParaxialModeField thinLens(double focalLengthMeters,
                                     double calibratedOnAxisPhaseRadians) {
        GaussianQ outQ = ParaxialAbcd.thinLens(focalLengthMeters).transform(q);
        return withQAndModalAmplitude(outQ,
                modalAmplitudeSqrtWatts.times(
                        ComplexAmplitude.fromPhaseRadians(calibratedOnAxisPhaseRadians)));
    }

    /** Explicit optical-path phase; no inference from coating or thickness. */
    public ParaxialModeField withUniformPhaseRadians(double phaseRadians) {
        return withQAndModalAmplitude(q,
                modalAmplitudeSqrtWatts.times(ComplexAmplitude.fromPhaseRadians(phaseRadians)));
    }

    /**
     * Ideal fold-mirror phase proxy ONLY: no direction change, Fresnel
     * coefficients, s/p basis transport, or actual world reflection geometry.
     * The coating/port phase must be an explicit laboratory input.
     */
    public ParaxialModeField withIdealMirrorReferencePhaseRadians(double phaseRadians) {
        return withUniformPhaseRadians(phaseRadians);
    }

    /** Uniform passive Jones element; any gain for this actual input is rejected. */
    public ParaxialModeField throughPassiveJones(JonesMatrix matrix) {
        if (matrix == null) throw new IllegalArgumentException("Jones matrix required");
        JonesVector out = matrix.apply(modalAmplitudeSqrtWatts);
        double before = powerWatts();
        double after = out.squaredNorm();
        if ((before == 0 && after != 0)
                || (before > 0 && after / before > 1.0 + 1e-12)) {
            throw new IllegalArgumentException("active Jones gain is not an allowed passive element");
        }
        return withQAndModalAmplitude(q, out);
    }

    /** Only one identical, collinear modal basis can be coherently recombined. */
    public boolean sameModeAs(ParaxialModeField other) {
        return other != null
                && phaseReferenceKey.equals(other.phaseReferenceKey)
                && modeFrameKey.equals(other.modeFrameKey)
                && Double.compare(vacuumWavelengthMeters, other.vacuumWavelengthMeters) == 0
                && q.equals(other.q);
    }

    ParaxialModeField withModalAmplitude(JonesVector amplitude) {
        return withQAndModalAmplitude(q, amplitude);
    }

    private ParaxialModeField withQAndModalAmplitude(GaussianQ updatedQ, JonesVector amplitude) {
        return new ParaxialModeField(phaseReferenceKey, modeFrameKey,
                vacuumWavelengthMeters, updatedQ, amplitude);
    }
}
