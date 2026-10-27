package dev.coldatomcraft.experiment;

/**
 * Visualization-only approximate visible-spectrum colour. The beam in clean
 * air is not naturally visible; >700 nm is given an explicitly artificial red
 * diagnostic colour. This is not a calibrated spectrometer or CIE transform.
 */
public final class PreviewBeamColour {
    private static final int[] WAVELENGTHS = {400, 450, 500, 532, 589, 620, 660, 700};
    private static final int[] RGB = {0x974DFF, 0x3A55FF, 0x24C8FF, 0x2FE65D,
                                      0xFFCE2E, 0xFF861F, 0xFF3340, 0xEF1717};
    private PreviewBeamColour() { }

    public static boolean infraredFalseColour(int wavelengthNanometers) {
        return wavelengthNanometers > 700;
    }

    public static int rgb(int wavelengthNanometers) {
        if (wavelengthNanometers < 400 || wavelengthNanometers > 1100) {
            throw new IllegalArgumentException("preview wavelength outside range");
        }
        if (infraredFalseColour(wavelengthNanometers)) return 0xFF4538;
        for (int i = 1; i < WAVELENGTHS.length; i++) {
            if (wavelengthNanometers <= WAVELENGTHS[i]) {
                double f = (double)(wavelengthNanometers - WAVELENGTHS[i - 1])
                        / (WAVELENGTHS[i] - WAVELENGTHS[i - 1]);
                return mixRgb(RGB[i - 1], RGB[i], f);
            }
        }
        return RGB[RGB.length - 1];
    }

    public static int haloRgb(int wavelengthNanometers) {
        return mixRgb(rgb(wavelengthNanometers), 0xFFFFFF, .30);
    }

    private static int mixRgb(int a, int b, double f) {
        int color = 0;
        for (int shift : new int[] {16, 8, 0}) {
            int left = (a >>> shift) & 255;
            int right = (b >>> shift) & 255;
            color |= (int)Math.round(left + (right - left) * f) << shift;
        }
        return color;
    }
}
