package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.PreviewOpticalBench;
import dev.coldatomcraft.experiment.PreviewBeamRoute;

/** Non-JUnit, independent numeric smoke checks for the dev optical fixture. */
public final class PreviewBenchSmoke {
    private static int count;
    private static void check(boolean condition, String message) {
        count++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reject(Runnable action, String message) {
        boolean rejected = false;
        try { action.run(); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, message);
    }
    public static void main(String[] args) {
        check(PreviewOpticalBench.aperturePresetCount() == 3, "three test apertures");
        check(PreviewOpticalBench.DEMO_POWER_WATTS == 0.02, "20 mW source");
        check(PreviewOpticalBench.DEMO_WAVELENGTH_METERS == 780e-9, "780nm source");
        check(PreviewOpticalBench.DEMO_WAIST_RADIUS_METERS == 0.0005, "0.5mm waist");
        reject(() -> PreviewOpticalBench.sampleAligned(0, 0), "too near");
        reject(() -> PreviewOpticalBench.sampleAligned(25, 0), "too far");
        reject(() -> PreviewOpticalBench.sampleAligned(5, 3), "invalid aperture");
        reject(() -> PreviewOpticalBench.sampleAligned(5, -1), "negative aperture index");
        double prev = -1;
        for (int aperture = 0; aperture < 3; aperture++) {
            var r = PreviewOpticalBench.sampleAligned(5, aperture);
            double measured = r.observation().value();
            check(measured > prev, "wider aperture increases collected power");
            check(measured >= 0 && measured <= 0.02, "power bounded by source");
            check("W".equals(r.observation().siUnit()), "reading SI unit");
            check(r.observation().simulatedTimeNanos() == 0L, "static preview, not tick-time");
            check(Math.abs(r.apertureBudget().capturedPowerWatts()
                        + r.apertureBudget().uncapturedPowerWatts() - 0.02) < 1e-14,
                    "capture budget conserved");
            prev = measured;
        }
        for (int d = 1; d < 24; d++) {
            double near = PreviewOpticalBench.sampleAligned(d, 0).observation().value();
            double farther = PreviewOpticalBench.sampleAligned(d + 1, 0).observation().value();
            check(near > farther, "radial spreading decreases narrow-aperture reading for distance " + d);
        }
        check(PreviewOpticalBench.sampleAligned(9, 1).equals(
                PreviewOpticalBench.sampleAligned(9, 1)), "determinism");
        var ready = PreviewBeamRoute.scan(0, d -> d == 4
                ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR);
        check(ready.status() == PreviewBeamRoute.Status.MEASURED && ready.powerWatts() > 0,
                "source can be measured");
        var blocked = PreviewBeamRoute.scan(0, d -> d == 2
                ? PreviewBeamRoute.Cell.OBSTACLE : (d == 4 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR));
        check(blocked.status() == PreviewBeamRoute.Status.BLOCKED && blocked.powerWatts() == 0,
                "non-air block prevents measurement");
        var unloaded = PreviewBeamRoute.scan(0, d -> d == 2
                ? PreviewBeamRoute.Cell.UNLOADED : PreviewBeamRoute.Cell.AIR);
        check(unloaded.status() == PreviewBeamRoute.Status.UNLOADED,
                "unloaded chunk prevents measurement");
        var off = PreviewBeamRoute.scan(0, d -> d == 3
                ? PreviewBeamRoute.Cell.EMITTER_OFF : PreviewBeamRoute.Cell.AIR);
        check(off.status() == PreviewBeamRoute.Status.SHUTTER_CLOSED,
                "shutter off is not interpreted as a measured zero");
        var wrong = PreviewBeamRoute.scan(0, d -> d == 3
                ? PreviewBeamRoute.Cell.EMITTER_WRONG_FACING : PreviewBeamRoute.Cell.AIR);
        check(wrong.status() == PreviewBeamRoute.Status.WRONG_FACING,
                "misaligned emitter rejected");
        System.out.println("D10 PLACEHOLDER BENCH SMOKE " + count + "/" + count + " PASS (JDK21, NON-JUNIT)");
    }
}
