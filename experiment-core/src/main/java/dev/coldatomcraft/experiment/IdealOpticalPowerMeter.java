package dev.coldatomcraft.experiment;

import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.GaussianCircularAperture;
import dev.coldatomcraft.physics.optics.IdealCircularDetectorFace;
import dev.coldatomcraft.physics.optics.ParaxialModeField;

/**
 * An ideal, noninvasive simulated optical-power probe with a finite circular
 * entrance aperture. This is a reference observable, not an actual calibrated
 * photodiode, an absorbing in-path component, or a server-authoritative device.
 *
 * <p>The reading is collected optical power in watts, with ideal model
 * uncertainty zero. Real detector calibration, responsivity, spectral range,
 * electronics noise, temperature dependence, saturation, finite bandwidth,
 * power/heat deposition, and measurement disturbance require separate models
 * and Owner-provided parameters.</p>
 */
public record IdealOpticalPowerMeter(String deviceId, double apertureRadiusMeters) {
    public IdealOpticalPowerMeter {
        if (deviceId == null || deviceId.isBlank()
                || !Double.isFinite(apertureRadiusMeters) || apertureRadiusMeters <= 0) {
            throw new IllegalArgumentException("meter identity and positive finite aperture radius required");
        }
    }

    /**
     * Sample a collinear, matched TEM00 mode at its declared optical reference
     * plane. Lateral offset is explicitly supplied in metres. The mode is NOT
     * deleted or attenuated by this virtual measurement.
     */
    public Reading sampleMode(ParaxialModeField field, double offsetMeters,
                              long simulatedTimeNanos) {
        if (field == null) throw new IllegalArgumentException("modal field required");
        return reading(GaussianCircularAperture.capture(
                field.powerWatts(), field.q().radiusMeters(field.vacuumWavelengthMeters()),
                apertureRadiusMeters, offsetMeters), simulatedTimeNanos);
    }

    /**
     * Sample an oriented detector face against a laboratory-local Gaussian.
     * The detector and this meter must declare the SAME entrance radius;
     * an unknown implicit aperture mapping is not permitted.
     */
    public Reading sampleAtFace(GaussianBeam beam, IdealCircularDetectorFace face,
                                long simulatedTimeNanos) {
        if (face == null || face.apertureRadiusMeters() != apertureRadiusMeters) {
            throw new IllegalArgumentException("face and meter aperture radius must match exactly");
        }
        return reading(face.capture(beam), simulatedTimeNanos);
    }

    private Reading reading(GaussianCircularAperture.Capture capture,
                            long simulatedTimeNanos) {
        if (simulatedTimeNanos < 0) {
            throw new IllegalArgumentException("nonnegative simulated time required");
        }
        Measurement ideal = new Measurement(deviceId, "ideal_collected_optical_power",
                "W", capture.capturedPowerWatts(), 0.0, simulatedTimeNanos);
        return new Reading(ideal, capture);
    }

    /** Both measured fraction and uncollected part are accounted, never heat. */
    public record Reading(Measurement observation,
                          GaussianCircularAperture.Capture apertureBudget) {
        public Reading {
            if (observation == null || apertureBudget == null
                    || !"W".equals(observation.siUnit())
                    || !"ideal_collected_optical_power".equals(observation.observable())
                    || Double.compare(observation.value(), apertureBudget.capturedPowerWatts()) != 0) {
                throw new IllegalArgumentException("inconsistent ideal optical reading");
            }
        }
    }
}
