package dev.coldatomcraft.physics.optics;

/**
 * Lossless planar specular mirror with calibrated s and p reflected phases.
 * Real cold-atom mirrors are coated, wavelength/angle/polarization dependent;
 * no such physical coating data is asserted here. Norms are exactly unity.
 */
public record IdealCalibratedPlaneMirror(OpticalPlane3 plane,
                                         double sPhaseRadians,
                                         double pPhaseRadians) {
    public IdealCalibratedPlaneMirror {
        if (plane == null || !Double.isFinite(sPhaseRadians)
                || !Double.isFinite(pPhaseRadians)) {
            throw new IllegalArgumentException("plane and calibrated phases required");
        }
    }

    public record Reflection(OpticalPlane3.Hit hit, GeometricJonesRay reflected) {
        public Reflection {
            if (hit == null || reflected == null) {
                throw new IllegalArgumentException("mirror result required");
            }
        }
    }

    /** Incident medium is VACUUM; phase advance before the mirror is k0*L. */
    public Reflection reflect(GeometricJonesRay incident) {
        OpticalPlane3.Hit hit = plane.intersect(incident);
        GeometricJonesRay atPlane = incident.advance(hit.distanceMeters(), 1);
        var incomingSP = OpticalPlane3.rotateIntoSP(atPlane, hit);
        var reflectedSP = new JonesVector(
                incomingSP.x().times(ComplexAmplitude.fromPhaseRadians(sPhaseRadians)),
                incomingSP.y().times(ComplexAmplitude.fromPhaseRadians(pPhaseRadians)));
        return new Reflection(hit, atPlane.withSurfaceFrame(hit.pointMeters(),
                OpticalPlane3.frameFor(hit.reflectedDirection(), hit.s()), reflectedSP));
    }

    /**
     * For a narrow angular-spectrum vacuum Gaussian, a planar mirror flips the
     * A FLAT mirror leaves q unchanged at its surface in the new forward
     * propagation coordinate: q_out = q_in. Its LAB transverse field must
     * remain continuous up to the explicitly supplied coating coefficient.
     * In particular, a divergent beam stays divergent after a flat mirror;
     * a phase-conjugating mirror would be a different physical component.
     * Does not calculate coating or nonparaxial distortion.
     *
     * <p>The input modal state MUST already be at the mirror plane; this method
     * does not advance it. Its matching input geometric ray must be at the same
     * plane and carry the EXACT same complex modal amplitude. An explicit new
     * mode-frame reference key is required after the physical direction flip.</p>
     */
    public ParaxialModeField reflectGaussianModeAtPlane(
            ParaxialModeField modeAtPlane, GeometricJonesRay geometricAtPlane,
            String calibratedOutgoingModeFrameKey) {
        if (modeAtPlane == null || geometricAtPlane == null
                || calibratedOutgoingModeFrameKey == null
                || calibratedOutgoingModeFrameKey.isBlank()) {
            throw new IllegalArgumentException("matched Gaussian mirror inputs required");
        }
        if (calibratedOutgoingModeFrameKey.equals(modeAtPlane.modeFrameKey())) {
            throw new IllegalArgumentException("physical reflection requires a distinct outgoing mode-frame declaration");
        }
        if (!modeAtPlane.phaseReferenceKey().equals(geometricAtPlane.phaseReferenceKey())
                || modeAtPlane.vacuumWavelengthMeters() != geometricAtPlane.vacuumWavelengthMeters()
                || !modeAtPlane.modalAmplitudeSqrtWatts().equals(geometricAtPlane.amplitudeSqrtWatts())) {
            throw new IllegalArgumentException("mode and geometric port amplitudes/phase must match");
        }
        OpticalPlane3.Hit hit = plane.intersect(geometricAtPlane);
        if (hit.distanceMeters() != 0.0) {
            throw new IllegalArgumentException("Gaussian modal state must already be at the mirror plane");
        }
        Reflection reflected = reflect(geometricAtPlane);
        GaussianQ q = modeAtPlane.q();
        return new ParaxialModeField(modeAtPlane.phaseReferenceKey(),
                calibratedOutgoingModeFrameKey, modeAtPlane.vacuumWavelengthMeters(),
                q, reflected.reflected().amplitudeSqrtWatts());
    }
}
