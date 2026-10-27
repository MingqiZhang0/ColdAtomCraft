package dev.coldatomcraft.verification;
import dev.coldatomcraft.experiment.*;
public final class LaserSettingsSmoke {
    private static int passed;
    private static void yes(boolean condition, String message) {
        passed++; if (!condition) throw new AssertionError(message);
    }
    private static void invalid(Runnable r) {
        boolean bad = false;
        try { r.run(); } catch (IllegalArgumentException expected) { bad = true; }
        yes(bad, "expected invalid parameter rejected");
    }
    public static void main(String[] args) {
        var baseline = LaserTuning.DEFAULT;
        yes(baseline.powerMilliwatts() == 20, "default power");
        yes(baseline.wavelengthNanometers() == 780, "default wavelength");
        yes(baseline.waistRadiusMicrometers() == 500, "default waist");
        yes(baseline.waistOffsetMillimeters() == 0, "default waist origin");
        for (int power = 1; power <= 200; power += 7) {
            var source = baseline.withField(0, power);
            double actual = PreviewOpticalBench.sampleAligned(4, 0, source).observation().value();
            double expected = PreviewOpticalBench.sampleAligned(4, 0).observation().value() * power / 20.0;
            yes(Math.abs(actual-expected) < 1e-12, "linear power " + power);
            yes(actual >= 0 && actual <= source.powerWatts(), "bounded power " + power);
        }
        for (int wave = 400; wave <= 1100; wave += 10) {
            var source = baseline.withField(1, wave);
            yes(source.beamRadiusAtPortDistanceMeters(4) > 0, "finite radius " + wave);
            yes(PreviewOpticalBench.sampleAligned(7, 0, source).observation().value() > 0,
                    "finite meter " + wave);
        }
        for (int waist = 100; waist <= 3000; waist += 50) {
            var source = baseline.withField(2, waist);
            yes(source.beamRadiusAtPortDistanceMeters(24) > 0, "waist " + waist);
        }
        for (int offset = -2000; offset <= 2000; offset += 125) {
            var source = baseline.withField(3, offset);
            yes(source.beamRadiusAtPortDistanceMeters(2) > 0, "waist offset " + offset);
        }
        invalid(() -> baseline.withField(0, 201));
        invalid(() -> baseline.withField(1, 399));
        invalid(() -> baseline.withField(2, 3001));
        invalid(() -> baseline.withField(3, 2001));
        invalid(() -> baseline.beamRadiusAtPortDistanceMeters(-1));
        var source = baseline.withField(0, 70).withField(1, 650);
        var measured = PreviewBeamRoute.scan(0,
                d -> d == 4 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR,
                d -> source);
        yes(measured.status() == PreviewBeamRoute.Status.MEASURED, "authoritative source");
        yes(Math.abs(measured.powerWatts() - PreviewOpticalBench.sampleAligned(4, 0, source)
                .observation().value()) < 1e-15, "matching settings");
        yes(PreviewBeamRoute.scan(0,
                d -> d == 4 ? PreviewBeamRoute.Cell.EMITTER_READY : PreviewBeamRoute.Cell.AIR,
                d -> null).status() == PreviewBeamRoute.Status.SOURCE_CONFIG_MISSING, "fail closed");
        System.out.println("D12 LASER TUNING SMOKE PASS " + passed + "/" + passed);
    }
}
