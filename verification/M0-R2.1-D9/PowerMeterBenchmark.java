package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.optics.GaussianCircularAperture;

/** No JMH, no real Minecraft TPS claim; deterministic exploratory micro-benchmark. */
public final class PowerMeterBenchmark {
    private static volatile double sink;
    private PowerMeterBenchmark() {}

    public static void main(String[] ignored) {
        final int warmup = 30_000;
        final int count = 100_000;
        for (int j = 0; j < warmup; j++) {
            sink = GaussianCircularAperture.capturedFraction(1, 0.7,
                    0.1 + (j % 37) * 0.01);
        }
        long t0 = System.nanoTime();
        double sum = 0;
        for (int j=0; j<count; j++) {
            sum+=GaussianCircularAperture.capturedFraction(1,0.7,0);
        }
        long t1 = System.nanoTime();
        for (int j=0; j<count; j++) {
            sum+=GaussianCircularAperture.capturedFraction(1,0.7,
                    0.1+(j%37)*0.01);
        }
        long t2 = System.nanoTime();
        sink=sum;
        System.out.printf(java.util.Locale.ROOT,
                "D9 exploratory JDK21 measurement (not JMH/TPS): centered=%d ns/op, offset=%d ns/op, sum=%.9g%n",
                (t1-t0)/count,(t2-t1)/count,sink);
    }
}
