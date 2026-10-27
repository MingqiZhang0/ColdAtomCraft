package dev.coldatomcraft.verification;

import dev.coldatomcraft.physics.optics.GaussianCircularAperture;

/** Numerical output only; independent Python oracle owns the expected answers. */
public final class DetectorReferenceProbe {
    private DetectorReferenceProbe() {}
    public static void main(String[] args) {
        double[][] cases = {
                {0.1, 0}, {0.1, 0.1}, {0.2, 0.5}, {0.4, 0.1},
                {0.5, 0.5}, {0.5, 1}, {1, 0}, {1, 0.2},
                {1, 0.9}, {1, 0.99}, {1, 1}, {1, 1.001},
                {1, 1.5}, {2, 0.5}, {2, 2}, {3, 3},
                {6, 5.94}, {6, 6}, {6, 6.00006},
                {10, 9.9}, {10, 10}, {10, 10.0001},
                {0.01, 0.01}, {0.001, 0.001}, {8, 1}, {1, 8}
        };
        for (double[] point : cases) {
            double fraction=GaussianCircularAperture.capturedFraction(1,point[0],point[1]);
            System.out.printf(java.util.Locale.ROOT,"CASE|%.17g|%.17g|%.17g%n",
                    point[0],point[1],fraction);
        }
    }
}
