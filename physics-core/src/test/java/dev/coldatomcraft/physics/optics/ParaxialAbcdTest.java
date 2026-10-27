package dev.coldatomcraft.physics.optics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ParaxialAbcdTest {
    private static final double LAMBDA = 780e-9;
    private static final double W0 = 0.5e-3;
    private static final double ZR = Math.PI * W0 * W0 / LAMBDA;

    private static void relative(double expected, double actual, double maxError) {
        assertEquals(expected, actual, Math.abs(expected) * maxError + 1e-300);
    }

    @Test void freeSpaceTransformsQExactlyAndAgreesWithGaussianRadius() {
        GaussianQ q = new GaussianQ(0, ZR);
        for (double distance : new double[]{0, 0.01, ZR, -ZR}) {
            GaussianQ out = ParaxialAbcd.freeSpace(distance).transform(q);
            relative(distance, out.realMeters(), 1e-14);
            relative(ZR, out.imaginaryMeters(), 1e-14);
            relative(W0 * Math.hypot(1, distance / ZR), out.radiusMeters(LAMBDA), 1e-13);
        }
    }

    @Test void thinLensLeavesIrradianceRadiusContinuousAtLensPlane() {
        GaussianQ q = new GaussianQ(0.2, ZR);
        for (double f : new double[]{0.1, 0.5, -0.3}) {
            GaussianQ out = ParaxialAbcd.thinLens(f).transform(q);
            relative(q.radiusMeters(LAMBDA), out.radiusMeters(LAMBDA), 1e-13);
            // 1/q_out = 1/q_in - 1/f; the lens adds real curvature.
            double rin = q.realMeters() / (q.realMeters() * q.realMeters() + ZR * ZR);
            double rout = out.realMeters() /
                    (out.realMeters() * out.realMeters() + out.imaginaryMeters() * out.imaginaryMeters());
            assertEquals(rin - 1 / f, rout, 1e-11);
        }
    }

    @Test void chainCompositionFollowsPhysicalPropagationOrder() {
        GaussianQ initial = new GaussianQ(0, ZR);
        var first = ParaxialAbcd.freeSpace(0.15);
        var lens = ParaxialAbcd.thinLens(0.2);
        var second = ParaxialAbcd.freeSpace(0.4);
        var viaSteps = second.transform(lens.transform(first.transform(initial)));
        var combined = first.then(lens).then(second).transform(initial);
        relative(viaSteps.realMeters(), combined.realMeters(), 2e-13);
        relative(viaSteps.imaginaryMeters(), combined.imaginaryMeters(), 2e-13);
    }

    @Test void thinLensChangesWaistPositionAndRayleighRange() {
        GaussianQ input = new GaussianQ(0, 1.0); // beam waist at lens input plane
        GaussianQ output = ParaxialAbcd.thinLens(0.5).transform(input);
        relative(-0.4, output.realMeters(), 1e-14);
        relative(0.2, output.imaginaryMeters(), 1e-14);
        relative(Math.sqrt(LAMBDA * 0.2 / Math.PI),
                output.waistRadiusMeters(LAMBDA), 1e-14);
    }

    @Test void identityAndInverseDriftDoNotModifyQ() {
        GaussianQ q = new GaussianQ(-0.7, 0.33);
        assertEquals(q, ParaxialAbcd.identity().transform(q));
        GaussianQ out = ParaxialAbcd.freeSpace(0.02)
                .then(ParaxialAbcd.freeSpace(-0.02)).transform(q);
        relative(q.realMeters(), out.realMeters(), 1e-14);
        relative(q.imaginaryMeters(), out.imaginaryMeters(), 1e-14);
    }

    @Test void strongLensPreservesPositiveImaginaryPartWithoutCancellation() {
        // The naive Smith imaginary expression subtracts two ~1.0 terms and
        // rounds to zero at q.real=1e16. The analytic determinant identity
        // gives y_out = 1 / ((1-1e16)^2 + 1) ~ 1e-32, not zero.
        GaussianQ q = ParaxialAbcd.thinLens(1)
                .transform(new GaussianQ(1e16, 1));
        relative(1e-32, q.imaginaryMeters(), 1e-14);
        relative(-1.0, q.realMeters(), 1e-14);
        // Lossless thin lens changes phase curvature, not spot size exactly at
        // the lens plane, even when q has a very large real/imaginary ratio.
        relative(new GaussianQ(1e16, 1).radiusMeters(LAMBDA),
                q.radiusMeters(LAMBDA), 1e-14);
    }

    @Test void invalidMatricesAndQFailExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> new GaussianQ(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new GaussianQ(0, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new ParaxialAbcd(1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> ParaxialAbcd.thinLens(0));
        assertThrows(IllegalArgumentException.class, () -> ParaxialAbcd.thinLens(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new GaussianQ(0, 1).radiusMeters(-1));
        assertThrows(IllegalArgumentException.class, () -> ParaxialAbcd.identity().transform(null));
    }
}
