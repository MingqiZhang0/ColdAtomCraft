package dev.coldatomcraft.physics.optics;

import dev.coldatomcraft.physics.Vec3;
import java.util.Optional;

/**
 * Single planar, uncoated, non-absorbing, isotropic and non-magnetic interface
 * between REAL, positive refractive indices. Applies plane-wave s/p Fresnel
 * amplitudes at the central ray of a paraxial optical beam. This is NOT a
 * multilayer dielectric mirror, finite beam angular-spectrum solver, or heat
 * deposition model.
 *
 * <p>Convention: exp(-i omega t); s=normalize(N cross k_incident),
 * p_j=k_j cross s, s cross p_j=k_j. At normal incidence use the input port x
 * axis as the s gauge. Fresnel r_p is relative to the REFLECTED p basis, so
 * r_p=-r_s at normal incidence (their LAB E-reflections have the same sign).
 * For transmitted travelling-mode amplitudes in sqrt(W), the Fresnel electric
 * transmission t must be multiplied by sqrt(n2*cos(theta_t)/(n1*cos(theta_i))).</p>
 */
public record PlanarFresnelInterface(OpticalPlane3 plane,
                                     double incidentIndex,
                                     double transmittedIndex) {
    public PlanarFresnelInterface {
        if (plane == null || !Double.isFinite(incidentIndex) || !(incidentIndex > 0)
                || !Double.isFinite(transmittedIndex) || !(transmittedIndex > 0)) {
            throw new IllegalArgumentException("plane and real positive refractive indices required");
        }
    }

    public record Coefficients(ComplexAmplitude rS, ComplexAmplitude rP,
                               ComplexAmplitude tSElectric, ComplexAmplitude tPElectric,
                               double transmissionAmplitudeFluxScale,
                               double incidentCosine, double transmittedCosine,
                               boolean totalInternalReflection) {
        public Coefficients {
            if (rS == null || rP == null || tSElectric == null || tPElectric == null
                    || !Double.isFinite(transmissionAmplitudeFluxScale)
                    || transmissionAmplitudeFluxScale < 0
                    || !Double.isFinite(incidentCosine) || !(incidentCosine > 0)
                    || !Double.isFinite(transmittedCosine) || transmittedCosine < 0) {
                throw new IllegalArgumentException("Fresnel coefficients must be finite");
            }
        }
        /** Power reflectance for a purely s incident wave. */
        public double reflectanceS() { return rS.magnitudeSquared(); }
        /** Power reflectance for a purely p incident wave. */
        public double reflectanceP() { return rP.magnitudeSquared(); }
        /** Power transmittance for a purely s incident wave; no propagating TIR mode. */
        public double transmittanceS() {
            return tSElectric.magnitudeSquared() * transmissionAmplitudeFluxScale
                    * transmissionAmplitudeFluxScale;
        }
        public double transmittanceP() {
            return tPElectric.magnitudeSquared() * transmissionAmplitudeFluxScale
                    * transmissionAmplitudeFluxScale;
        }
    }

    public record Interaction(OpticalPlane3.Hit hit,
                              GeometricJonesRay incidentAtSurface,
                              GeometricJonesRay reflected,
                              Optional<GeometricJonesRay> transmitted,
                              Coefficients coefficients) {
        public Interaction {
            if (hit == null || incidentAtSurface == null || reflected == null
                    || transmitted == null || coefficients == null) {
                throw new IllegalArgumentException("complete Fresnel interaction required");
            }
        }
        public double outputPowerWatts() {
            return reflected.powerWatts()
                    + transmitted.map(GeometricJonesRay::powerWatts).orElse(0.0);
        }
    }

    public Interaction scatter(GeometricJonesRay incident) {
        OpticalPlane3.Hit hit = plane.intersect(incident);
        GeometricJonesRay surfaceInput = incident.advance(hit.distanceMeters(), incidentIndex);
        double ci = hit.cosineIncident();
        // Ratio is evaluated only once and must be representable; the very
        // extreme n1/n2 regime needs a different numerical treatment.
        double eta = incidentIndex / transmittedIndex;
        if (!Double.isFinite(eta)) {
            throw new IllegalArgumentException("index ratio not representable");
        }
        double sin2i = Math.max(0.0, 1.0 - ci * ci);
        double sin2t = (eta * eta) * sin2i;
        if (!Double.isFinite(sin2t)) {
            throw new IllegalArgumentException("Snell refracted sine not representable");
        }
        boolean tir = sin2t >= 1.0;
        ComplexAmplitude rs, rp, ts, tp;
        double ct = 0.0, fluxScale = 0.0;
        Vec3 kTransmitted = null;

        if (tir) {
            // A single infinite interface supports an evanescent transmitted
            // field, but no outgoing transmitted travelling-power ray. The
            // evanescent field is not represented by this API.
            double alpha = Math.sqrt(Math.max(0, sin2t - 1));
            rs = ComplexAmplitude.fromPhaseRadians(-2 * Math.atan2(transmittedIndex * alpha,
                    incidentIndex * ci));
            rp = ComplexAmplitude.fromPhaseRadians(-2 * Math.atan2(incidentIndex * alpha,
                    transmittedIndex * ci));
            ts = ComplexAmplitude.ZERO;
            tp = ComplexAmplitude.ZERO;
        } else {
            ct = Math.sqrt(Math.max(0.0, 1.0 - sin2t));
            double n1ci = incidentIndex * ci;
            double n2ct = transmittedIndex * ct;
            double denomS = n1ci + n2ct;
            double denomP = transmittedIndex * ci + incidentIndex * ct;
            if (!(denomS > 0) || !(denomP > 0) || !Double.isFinite(denomS)
                    || !Double.isFinite(denomP)) {
                throw new IllegalArgumentException("unrepresentable Fresnel denominators");
            }
            rs = new ComplexAmplitude((n1ci - n2ct) / denomS, 0);
            rp = new ComplexAmplitude((transmittedIndex * ci - incidentIndex * ct) / denomP, 0);
            ts = new ComplexAmplitude(2 * n1ci / denomS, 0);
            tp = new ComplexAmplitude(2 * n1ci / denomP, 0);
            fluxScale = Math.sqrt(n2ct / n1ci);
            Vec3 ki = incident.frame().direction();
            kTransmitted = ki.subtract(plane.normal1To2().scale(ci)).scale(eta)
                    .add(plane.normal1To2().scale(ct)).normalized();
        }

        Coefficients coeffs = new Coefficients(rs, rp, ts, tp,
                fluxScale, ci, ct, tir);
        JonesVector incomingSP = OpticalPlane3.rotateIntoSP(surfaceInput, hit);
        var reflectedSP = new JonesVector(incomingSP.x().times(rs), incomingSP.y().times(rp));
        var reflectedFrame = OpticalPlane3.frameFor(hit.reflectedDirection(), hit.s());
        var reflected = surfaceInput.withSurfaceFrame(hit.pointMeters(), reflectedFrame, reflectedSP);
        Optional<GeometricJonesRay> transmitted = Optional.empty();
        if (!tir) {
            var transmittedSP = new JonesVector(
                    incomingSP.x().times(ts).scale(fluxScale),
                    incomingSP.y().times(tp).scale(fluxScale));
            var transmitFrame = OpticalPlane3.frameFor(kTransmitted, hit.s());
            transmitted = Optional.of(surfaceInput.withSurfaceFrame(
                    hit.pointMeters(), transmitFrame, transmittedSP));
        }
        // A normalized plane-wave model is strictly passive. Guard against
        // signs or missing flux factors when changing polarization gauges.
        double inputPower = surfaceInput.powerWatts();
        double outputPower = reflected.powerWatts()
                + transmitted.map(GeometricJonesRay::powerWatts).orElse(0.0);
        if (Math.abs(inputPower - outputPower) > 3e-11 * Math.max(inputPower, 1e-300)) {
            throw new ArithmeticException("Fresnel travelling-mode energy conservation failed");
        }
        return new Interaction(hit, surfaceInput, reflected, transmitted, coeffs);
    }
}
