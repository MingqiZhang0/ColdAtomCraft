package dev.coldatomcraft.experiment.world;

import dev.coldatomcraft.experiment.ModelCatalog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pure-Java microbenchmark of session allocation + per-apparatus indexed windows.
 * Includes random UUID generation and allocation, excludes solver/world/tick load.
 * NOT a TPS benchmark, no hard performance gate or scientific simulation claim.
 */
public final class ReservationIndexBenchmark {
    private static final ApparatusKey KEY = new ApparatusKey("minecraft:overworld",8,64,8);

    private static double measure(int sessions) {
        var scheduler = new ExperimentRunScheduler(ModelCatalog.fromTrustedServerBootstrap(List.of()));
        long started = System.nanoTime();
        for (int i=0; i<sessions; i++) {
            var result = scheduler.createExperiment(ExperimentReservation.single(KEY,
                    new ServerTickWindow((long)i*2, (long)i*2+1)));
            if (result.status()!=ExperimentRunScheduler.CreateStatus.CREATED) {
                throw new AssertionError("benchmark allocation failed at " + i);
            }
        }
        return (System.nanoTime()-started)/1_000_000.0;
    }

    public static void main(String[] args) {
        for (int i=0;i<3;i++) measure(1_000);
        for (int n : new int[] {1_000, 10_000, 50_000}) {
            List<Double> runs=new ArrayList<>();
            for (int rep=0;rep<5;rep++) runs.add(measure(n));
            Collections.sort(runs);
            System.out.printf("N=%d samples_ms=%s median_ms=%.3f%n",n,runs,runs.get(2));
        }
        System.out.println("OBSERVATIONAL ONLY: includes JVM allocation and UUID, NOT physics/TPS");
    }
}
