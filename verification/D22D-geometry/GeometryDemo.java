package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.Vec3;
import dev.coldatomcraft.physics.optics.FinitePlaneGaussianFlux;
import dev.coldatomcraft.physics.optics.GaussianBeam;

import java.util.Locale;

/** Prints deterministic REFERENCE_ONLY theoretical CSV, no motor or power meter. */
public final class GeometryDemo {
    private GeometryDemo() { }

    public static void main(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("no arguments; CSV to stdout");
        var beam = new GaussianBeam(.02, 780e-9, .0005,
                new Vec3(0,0,0),new Vec3(0,0,1));
        System.out.println("provenance,reported_offset_nm,detector_tilt_degrees," +
                "optical_axis_inside_disk,ideal_captured_watts,quadrature_delta_watts,method");
        for (int degrees: new int[]{0,15,30,45,60}) {
            double alpha = Math.toRadians(degrees);
            Vec3 front = new Vec3(Math.sin(alpha), 0, -Math.cos(alpha));
            for (int index=-30;index<=30;index++) {
                long nanometres=index*50_000L;
                var disk=FinitePlaneGaussianFlux.Disk.fromNormal(
                        new Vec3(nanometres*1e-9,0,1), front, new Vec3(0,1,0), .00065);
                var prediction=FinitePlaneGaussianFlux.predict(beam,disk);
                System.out.printf(Locale.ROOT,
                        "REFERENCE_ONLY,%d,%d,%b,%.17g,%.17g,%s%n",
                        nanometres,degrees,prediction.axisIntersectsDisk(),
                        prediction.capturedWatts(),prediction.estimatedQuadratureErrorWatts(),
                        prediction.method());
            }
        }
    }
}
