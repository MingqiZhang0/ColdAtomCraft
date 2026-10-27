package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JonesMatrixTest {
    private static void close(double expected, double measured) {
        assertEquals(expected, measured, 2e-13 * Math.max(1, Math.abs(expected)));
    }

    @Test void idealPolarizerObeysMalusLaw() {
        JonesVector incident = JonesVector.horizontal();
        for (double theta : new double[]{0, Math.PI/12, Math.PI/6, Math.PI/4, Math.PI/2}) {
            JonesVector transmitted = JonesMatrix.idealLinearPolarizer(theta).apply(incident);
            close(Math.cos(theta) * Math.cos(theta), transmitted.squaredNorm());
        }
    }

    @Test void crossedPolarizersExtinguishAndOrderIsPhysical() {
        var horizontal = JonesMatrix.idealLinearPolarizer(0);
        var vertical = JonesMatrix.idealLinearPolarizer(Math.PI / 2);
        close(0, horizontal.then(vertical).apply(JonesVector.horizontal()).squaredNorm());
        var tilted = JonesMatrix.idealLinearPolarizer(Math.PI / 4);
        close(0.25, horizontal.then(tilted).then(vertical)
                .apply(JonesVector.horizontal()).squaredNorm());
    }

    @Test void quarterWaveProducesQuadratureWithoutChangingPower() {
        JonesVector diagonal = JonesVector.linearRadians(Math.PI / 4);
        JonesVector result = JonesMatrix.quarterWave(0).apply(diagonal);
        close(1, result.squaredNorm());
        close(0, result.x().imaginary());
        close(0, result.y().real());
        close(Math.sqrt(0.5), result.x().real());
        close(Math.sqrt(0.5), result.y().imaginary());
    }

    @Test void halfWaveRotatesLinearAxisByTwiceOrientation() {
        JonesVector output = JonesMatrix.halfWave(Math.PI / 8).apply(JonesVector.horizontal());
        close(Math.sqrt(0.5), output.x().real());
        close(Math.sqrt(0.5), output.y().real());
        close(1, output.squaredNorm());
    }

    @Test void idealRetarderIsUnitaryForArbitraryInputEllipses() {
        JonesVector state = new JonesVector(new ComplexAmplitude(0.2, 0.3),
                new ComplexAmplitude(-0.4, 0.5));
        double original = state.squaredNorm();
        for (double angle : new double[]{0, 0.17, Math.PI/4, Math.PI/2}) {
            for (double delay : new double[]{0, Math.PI/2, Math.PI, -1.4}) {
                close(original, JonesMatrix.idealRetarder(angle, delay).apply(state).squaredNorm());
            }
        }
    }

    @Test void composingMatricesMatchesSequentialApplicationIncludingPhase() {
        JonesVector state = new JonesVector(new ComplexAmplitude(0.4, -0.2),
                new ComplexAmplitude(0.3, 0.1));
        var a = JonesMatrix.idealRetarder(0.3, 1.2);
        var b = JonesMatrix.idealLinearPolarizer(-0.17);
        JonesVector serial = b.apply(a.apply(state));
        JonesVector combined = a.then(b).apply(state);
        close(serial.x().real(), combined.x().real());
        close(serial.x().imaginary(), combined.x().imaginary());
        close(serial.y().real(), combined.y().real());
        close(serial.y().imaginary(), combined.y().imaginary());
    }

    @Test void globalPhaseDoesNotChangeSingleBeamPowerButChangesComplexAmplitude() {
        JonesVector original = JonesVector.horizontal();
        JonesVector shifted = original.times(ComplexAmplitude.fromPhaseRadians(Math.PI/2));
        close(original.squaredNorm(), shifted.squaredNorm());
        close(0, shifted.x().real());
        close(1, shifted.x().imaginary());
    }

    @Test void wavePlateCommonPathPhaseMustBeExplicitForCoherentArms() {
        JonesVector input=JonesVector.horizontal();
        JonesVector reference=JonesMatrix.idealRetarder(Math.PI/2, Math.PI).apply(input);
        JonesVector compensated=JonesMatrix.idealRetarder(Math.PI/2, Math.PI, Math.PI).apply(input);
        close(-1, reference.x().real());
        close(1, compensated.x().real());
        close(1, reference.squaredNorm());
        close(1, compensated.squaredNorm());
        assertThrows(IllegalArgumentException.class,
                () -> JonesMatrix.identity().withUniformPhaseRadians(Double.NaN));
    }

    @Test void rejectsNonFiniteInputsAndNullJonesElements() {
        assertThrows(IllegalArgumentException.class, () -> ComplexAmplitude.fromPhaseRadians(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ComplexAmplitude(Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> JonesMatrix.idealLinearPolarizer(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JonesMatrix.idealRetarder(0, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JonesMatrix.identity().apply(null));
        assertThrows(IllegalArgumentException.class, () -> new JonesVector(null, ComplexAmplitude.ONE));
        assertThrows(IllegalArgumentException.class, () -> ComplexAmplitude.ONE.times(null));
        assertThrows(IllegalArgumentException.class, () -> ComplexAmplitude.ONE.scale(Double.NaN));
    }
}
