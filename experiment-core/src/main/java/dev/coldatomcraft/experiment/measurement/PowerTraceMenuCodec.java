package dev.coldatomcraft.experiment.measurement;

import java.util.Objects;
import java.util.function.IntUnaryOperator;
import java.util.zip.CRC32;

/**
 * Lossless transport of one sample through Minecraft's *signed 16-bit* menu
 * data-slot wire representation. No custom client payload or file access.
 * Each sample is 4 tick words + 4 IEEE-754 binary64 words + 1 status word.
 */
public final class PowerTraceMenuCodec {
    public static final int WORDS_PER_SAMPLE = 9;
    public static final int EMPTY_STATUS_WORD = 0xffff;

    private PowerTraceMenuCodec() { }

    /** All returned words are unsigned 16-bit; decoding also accepts signed shorts. */
    public static int[] encode(PowerTrace.Sample sample) {
        Objects.requireNonNull(sample, "sample");
        int[] words = new int[WORDS_PER_SAMPLE];
        encodeLong(sample.gameTick(), words, 0);
        encodeLong(Double.doubleToRawLongBits(sample.watts()), words, 4);
        words[8] = sample.status().ordinal();
        return words;
    }

    public static PowerTrace.Sample decode(IntUnaryOperator words, int base) {
        Objects.requireNonNull(words, "words");
        if (base < 0) throw new IllegalArgumentException("negative menu offset");
        int status = words.applyAsInt(base + 8) & 0xffff;
        if (status == EMPTY_STATUS_WORD) return null;
        PowerTrace.Status[] values = PowerTrace.Status.values();
        if (status >= values.length) throw new IllegalArgumentException("invalid menu status");
        long tick = decodeLong(words, base);
        long bits = decodeLong(words, base + 4);
        return new PowerTrace.Sample(tick, values[status], Double.longBitsToDouble(bits));
    }

    public static int[] empty() {
        int[] words = new int[WORDS_PER_SAMPLE];
        words[8] = EMPTY_STATUS_WORD;
        return words;
    }

    /** CRC of the complete unsigned-16-bit menu page, including page metadata. */
    public static int checksum(IntUnaryOperator words, int count) {
        Objects.requireNonNull(words, "words");
        if (count < 0 || count > 4096) throw new IllegalArgumentException("menu page too large");
        CRC32 crc = new CRC32();
        for (int i = 0; i < count; i++) {
            int word = words.applyAsInt(i) & 0xffff;
            crc.update(word & 0xff);
            crc.update((word >>> 8) & 0xff);
        }
        return (int) crc.getValue();
    }

    private static void encodeLong(long value, int[] words, int offset) {
        for (int shift = 0; shift < 64; shift += 16) {
            words[offset + shift / 16] = (int) ((value >>> shift) & 0xffffL);
        }
    }

    private static long decodeLong(IntUnaryOperator words, int offset) {
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result |= (long)(words.applyAsInt(offset + i) & 0xffff) << (i * 16);
        }
        return result;
    }
}
