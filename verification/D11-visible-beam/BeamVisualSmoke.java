package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.PreviewBeamDisplay;
import dev.coldatomcraft.experiment.PreviewOpticalBench;
import java.util.concurrent.atomic.AtomicInteger;

/** JDK21+ independent executable D11 visual route/optics test (not JUnit). */
public final class BeamVisualSmoke {
    private static int checks;
    private static void check(boolean yes, String why) {
        checks++;
        if (!yes) throw new AssertionError(why);
    }
    private static void approx(double actual, double expected, double tolerance, String why) {
        check(Math.abs(actual - expected) <= tolerance,
                why + " actual=" + actual + " expected=" + expected);
    }
    private static void invalid(Runnable test) {
        boolean rejected = false;
        try { test.run(); } catch (IllegalArgumentException | NullPointerException expected) { rejected = true; }
        check(rejected, "invalid argument must be rejected");
    }
    public static void main(String[] args) {
        var air = PreviewBeamDisplay.trace(d -> PreviewBeamDisplay.Cell.AIR);
        check(air.stop() == PreviewBeamDisplay.Stop.REACH_LIMIT, "unobstructed is capped");
        check(air.cellDistance() == 24, "24-block cap");
        approx(air.endFromSourceCenterBlocks(), 24.4375, 1e-12, "max checked air extent");
        for (int d = 1; d <= PreviewOpticalBench.MAX_SEPARATION_BLOCKS; d++) {
            final int hit = d;
            AtomicInteger queries = new AtomicInteger();
            var receiver = PreviewBeamDisplay.trace(distance -> {
                queries.incrementAndGet();
                return distance == hit ? PreviewBeamDisplay.Cell.RECEIVER : PreviewBeamDisplay.Cell.AIR;
            });
            check(receiver.stop() == PreviewBeamDisplay.Stop.RECEIVER, "receiver stop " + d);
            check(receiver.cellDistance() == d, "receiver index " + d);
            approx(receiver.endFromSourceCenterBlocks(), d - 7.0 / 16.0, 1e-12,
                    "receiver face centre " + d);
            check(queries.get() == d, "must not scan beyond receiver " + d);
            for (var condition : new PreviewBeamDisplay.Cell[] {
                    PreviewBeamDisplay.Cell.OBSTACLE, PreviewBeamDisplay.Cell.UNLOADED }) {
                var stop = PreviewBeamDisplay.trace(distance -> distance == hit
                        ? condition : PreviewBeamDisplay.Cell.AIR);
                check(stop.stop() == (condition == PreviewBeamDisplay.Cell.OBSTACLE
                        ? PreviewBeamDisplay.Stop.OBSTACLE : PreviewBeamDisplay.Stop.UNLOADED),
                        "fail closed " + condition + " " + d);
                approx(stop.endFromSourceCenterBlocks(), d - 0.5, 1e-12,
                        "solid face stop " + d);
            }
        }
        approx(PreviewBeamDisplay.physicalRadiusMeters(0), 5e-4, 1e-16, "beam waist");
        double zr = Math.PI * Math.pow(PreviewOpticalBench.DEMO_WAIST_RADIUS_METERS, 2)
                / PreviewOpticalBench.DEMO_WAVELENGTH_METERS;
        approx(PreviewBeamDisplay.physicalRadiusMeters(zr), Math.sqrt(2) * 5e-4,
                1e-15, "Rayleigh radius sqrt2");
        double lastPhysical = -1, lastDisplay = -1;
        for (int d = 0; d <= 24; d++) {
            double physical = PreviewBeamDisplay.physicalRadiusMeters(d);
            double visual = PreviewBeamDisplay.displayedRadiusBlocks(d);
            check(physical > lastPhysical, "nondecreasing physical radius");
            check(visual >= lastDisplay && visual <= .16, "bounded monotone display radius");
            check(visual > physical, "visualisation deliberately wider than actual beam");
            lastPhysical = physical;
            lastDisplay = visual;
        }
        invalid(() -> PreviewBeamDisplay.physicalRadiusMeters(-1));
        invalid(() -> PreviewBeamDisplay.physicalRadiusMeters(Double.NaN));
        invalid(() -> PreviewBeamDisplay.physicalRadiusMeters(Double.POSITIVE_INFINITY));
        invalid(() -> PreviewBeamDisplay.trace(null));
        invalid(() -> PreviewBeamDisplay.trace(d -> null));
        check(PreviewBeamDisplay.trace(d -> d == 1
                ? PreviewBeamDisplay.Cell.OBSTACLE : PreviewBeamDisplay.Cell.AIR)
                .endFromSourceCenterBlocks() > PreviewOpticalBench.DEMO_PORT_OFFSET_METERS,
                "near collision terminates just beyond emission port");
        System.out.println("D11 beam optics+route smoke PASS: " + checks + "/" + checks);
    }
}
