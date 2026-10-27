package dev.coldatomcraft.physics.optics;

/**
 * A lossless, reciprocal, symmetric two-port optical-mode coupler with
 * the fixed phase convention S=[[sqrt(T),i*sqrt(R)],
 *                                    [i*sqrt(R),sqrt(T)]], R=1-T.
 * Both output ports are retained: interference redistributes power between
 * ports, never creates or destroys it in the ideal model.
 *
 * <p>Ports must have identical q, wavelength, declared coherence reference
 * and declared polarization mode-frame key. This is a strict matched-mode
 * model, NOT an overlap integral for differently shaped/aligned beams,
 * a thin-film coating model, or a geometrical ray direction transform.</p>
 */
public record IdealTwoPortBeamSplitter(double powerTransmission) {
    public IdealTwoPortBeamSplitter {
        if (!Double.isFinite(powerTransmission)
                || powerTransmission < 0 || powerTransmission > 1) {
            throw new IllegalArgumentException("power transmission must be in [0,1]");
        }
    }

    public double powerReflection() {
        return 1.0 - powerTransmission;
    }

    public record Ports(ParaxialModeField output0, ParaxialModeField output1) {
        public Ports {
            if (output0 == null || output1 == null) {
                throw new IllegalArgumentException("both output ports required");
            }
        }
        public double totalPowerWatts() {
            return output0.powerWatts() + output1.powerWatts();
        }
    }

    /** One populated classical input; the other is an empty classical mode. Quantum vacuum noise is not modeled. */
    public Ports split(ParaxialModeField incident) {
        if (incident == null) throw new IllegalArgumentException("input required");
        return scatter(incident, incident.zeroLike());
    }

    /** Two coherent, matched input modes at the same reference plane. */
    public Ports scatter(ParaxialModeField input0, ParaxialModeField input1) {
        if (input0 == null || !input0.sameModeAs(input1)) {
            throw new IllegalArgumentException("both coherent input modes must match exactly");
        }
        ComplexAmplitude transmission = new ComplexAmplitude(Math.sqrt(powerTransmission), 0);
        ComplexAmplitude reflection = new ComplexAmplitude(0, Math.sqrt(powerReflection()));
        JonesVector a = input0.modalAmplitudeSqrtWatts();
        JonesVector b = input1.modalAmplitudeSqrtWatts();
        JonesVector out0 = a.times(transmission).plus(b.times(reflection));
        JonesVector out1 = a.times(reflection).plus(b.times(transmission));
        return new Ports(input0.withModalAmplitude(out0), input0.withModalAmplitude(out1));
    }
}
