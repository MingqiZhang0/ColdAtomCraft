import dev.coldatomcraft.physics.optics.GaussianQ;
import dev.coldatomcraft.physics.optics.ParaxialAbcd;

/** Verification-only demonstration of why a naive Smith Im subtraction is unsafe. */
public final class LegacyImaginaryCancellationProbe {
    public static void main(String[] args) {
        double x = 1e16, y = 1;
        // Thin lens f=1, matrix (A,B,C,D) = (1,0,-1,1).
        double u = x, v = y, p = Math.fma(-1, x, 1), t = -y;
        double ratio = t / p;
        double naiveImag = (v - u * ratio) / (p + t * ratio);
        GaussianQ safe = ParaxialAbcd.thinLens(1).transform(new GaussianQ(x, y));
        System.out.println("naive Im(q_out)=" + naiveImag);
        System.out.println("D5 robust Im(q_out)=" + safe.imaginaryMeters());
        if (naiveImag > 0 || !(safe.imaginaryMeters() > 0)) {
            throw new AssertionError("expected reproduced cancellation and positive corrected imag");
        }
        System.out.println("PASS: catastrophic cancellation reproduced; determinant formulation avoids it");
    }
}
