package dev.coldatomcraft.physics.optics;

/**
 * Jones transfer matrix of ideal thin, uniform, passive polarization optics.
 * Applies to a single common transverse basis; does not include non-paraxial
 * scattering, finite apertures, Fresnel coefficients or frequency conversion.
 * Relative phase is retained. Absolute path phase is NOT inferred from
 * a plate thickness: a separate common phase can be specified explicitly.
 */
public record JonesMatrix(ComplexAmplitude xx, ComplexAmplitude xy,
                          ComplexAmplitude yx, ComplexAmplitude yy) {
    public JonesMatrix {
        if (xx == null || xy == null || yx == null || yy == null) {
            throw new IllegalArgumentException("all Jones matrix coefficients required");
        }
    }

    public static JonesMatrix identity() {
        return new JonesMatrix(ComplexAmplitude.ONE, ComplexAmplitude.ZERO,
                ComplexAmplitude.ZERO, ComplexAmplitude.ONE);
    }

    /** Ideal absorbing linear polarizer, its transmitted axis is at angle theta. */
    public static JonesMatrix idealLinearPolarizer(double thetaRadians) {
        if (!Double.isFinite(thetaRadians)) throw new IllegalArgumentException("finite angle required");
        double c = Math.cos(thetaRadians);
        double s = Math.sin(thetaRadians);
        return real(c*c, c*s, c*s, s*s);
    }

    /**
     * Ideal retarder. The axis at theta has multiplier 1; its perpendicular
     * axis acquires relative phase +delta under exp(-i omega t) convention.
     * Uniform, lossless optical element, without Fresnel/interface loss.
     */
    public static JonesMatrix idealRetarder(double thetaRadians, double deltaRadians) {
        if (!Double.isFinite(thetaRadians) || !Double.isFinite(deltaRadians)) {
            throw new IllegalArgumentException("finite angle and retardance required");
        }
        double c = Math.cos(thetaRadians);
        double s = Math.sin(thetaRadians);
        ComplexAmplitude e = ComplexAmplitude.fromPhaseRadians(deltaRadians);
        ComplexAmplitude oneMinusE = ComplexAmplitude.ONE.minus(e);
        return new JonesMatrix(
                new ComplexAmplitude(c*c, 0).plus(e.scale(s*s)),
                oneMinusE.scale(c*s),
                oneMinusE.scale(c*s),
                new ComplexAmplitude(s*s, 0).plus(e.scale(c*c)));
    }

    /**
     * Explicit relative retardance AND common path phase. The two-argument
     * factory chooses common phase zero as a gauge convention only; a real
     * interferometer must supply the physically measured/calculated common
     * phase accumulated through the plate and all other optical path elements.
     */
    public static JonesMatrix idealRetarder(double thetaRadians,
                                            double deltaRadians,
                                            double commonPhaseRadians) {
        return idealRetarder(thetaRadians, deltaRadians)
                .withUniformPhaseRadians(commonPhaseRadians);
    }

    /** Uniform optical-path phase multiplier; does not change Jones power. */
    public JonesMatrix withUniformPhaseRadians(double phaseRadians) {
        ComplexAmplitude multiplier = ComplexAmplitude.fromPhaseRadians(phaseRadians);
        return new JonesMatrix(xx.times(multiplier), xy.times(multiplier),
                yx.times(multiplier), yy.times(multiplier));
    }

    public static JonesMatrix quarterWave(double thetaRadians) {
        return idealRetarder(thetaRadians, Math.PI / 2);
    }

    public static JonesMatrix halfWave(double thetaRadians) {
        return idealRetarder(thetaRadians, Math.PI);
    }

    private static JonesMatrix real(double a, double b, double c, double d) {
        return new JonesMatrix(new ComplexAmplitude(a, 0), new ComplexAmplitude(b, 0),
                new ComplexAmplitude(c, 0), new ComplexAmplitude(d, 0));
    }

    public JonesVector apply(JonesVector incident) {
        if (incident == null) throw new IllegalArgumentException("incident Jones vector required");
        return new JonesVector(xx.times(incident.x()).plus(xy.times(incident.y())),
                yx.times(incident.x()).plus(yy.times(incident.y())));
    }

    /** Apply this element first, then {@code following}. */
    public JonesMatrix then(JonesMatrix following) {
        if (following == null) throw new IllegalArgumentException("following matrix required");
        return new JonesMatrix(
                following.xx.times(xx).plus(following.xy.times(yx)),
                following.xx.times(xy).plus(following.xy.times(yy)),
                following.yx.times(xx).plus(following.yy.times(yx)),
                following.yx.times(xy).plus(following.yy.times(yy)));
    }
}
