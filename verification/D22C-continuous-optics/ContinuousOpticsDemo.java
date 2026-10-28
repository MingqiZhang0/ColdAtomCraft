package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.motion.ContinuousBenchScanReference;
import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.GaussianBeam;
import dev.coldatomcraft.physics.optics.JonesVector;
import dev.coldatomcraft.physics.optics.RayFrame3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/** Reference-only synthetic plots; never label as real Minecraft measurements. */
public final class ContinuousOpticsDemo {
    private ContinuousOpticsDemo() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("output directory required");
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        var beam = new GaussianBeam(0.020, 780e-9, 0.0005,
                new Vec3(0, 0, 0), new Vec3(0, 0, 1));
        var bench = new ContinuousBenchScanReference(beam,
                RayFrame3.fromDirectionAndXAxis(new Vec3(0, 0, 1), new Vec3(1, 0, 0)),
                1.0, 0.00065, JonesVector.horizontal());
        var scanX = new ArrayList<ContinuousBenchScanReference.PredictedPoint>();
        for (int i=0;i<=120;i++) {
            long x=(i-60L)*25_000L;
            scanX.add(bench.predict(i, ContinuousBenchScanReference.Pose.transverse(x,0),
                    ContinuousBenchScanReference.Train.NO_ANALYSER));
        }
        var scanAngle = new ArrayList<ContinuousBenchScanReference.PredictedPoint>();
        for (int i=0;i<=120;i++) {
            long theta=Math.round(Math.PI*i/120*1e9);
            scanAngle.add(bench.predict(i,
                    new ContinuousBenchScanReference.Pose(0,0,theta,0),
                    ContinuousBenchScanReference.Train.ANALYSER));
        }
        Files.writeString(out.resolve("CAC-D22C-Reference-Px.csv"),
                ContinuousBenchScanReference.predictionCsv(scanX), StandardCharsets.UTF_8);
        Files.writeString(out.resolve("CAC-D22C-Reference-Ptheta.csv"),
                ContinuousBenchScanReference.predictionCsv(scanAngle), StandardCharsets.UTF_8);
        System.out.println("D22C REFERENCE_ONLY CSV demo generated");
    }
}
