package dev.coldatomcraft.experiment.measurement;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * D21C bounded, read-only paging over the existing D21A CSV format.
 *
 * <p>A page reads no more than 16 KiB of on-disk data. Cursors are private
 * byte offsets held by the server menu, not player-supplied paths or indices.
 * A snapshot end stops new appends from changing an older history session.
 * No block/chunk is loaded and nothing is written during a query.</p>
 *
 * <p>Failures are explicit: malformed rows, truncated archives, symlinks and
 * unexpectedly long rows must not be quietly interpreted as zero watts.</p>
 */
public final class PowerTraceHistory {
    public static final int PAGE_SIZE = 32;
    public static final int MAX_QUERY_BYTES = 16 * 1024;
    public static final int MAX_ROW_BYTES = 128;
    private static final byte[] HEADER = PowerTraceArchive.HEADER.getBytes(StandardCharsets.US_ASCII);

    private PowerTraceHistory() { }

    public record Page(List<PowerTrace.Sample> samples, long startOffset, long endOffset,
                       long snapshotEnd, boolean hasOlder, boolean hasNewer) {
        public Page {
            samples = List.copyOf(Objects.requireNonNull(samples, "samples"));
            if (samples.size() > PAGE_SIZE || startOffset < 0 || endOffset < startOffset
                    || snapshotEnd < endOffset) {
                throw new IllegalArgumentException("invalid archive page");
            }
        }
        public static Page empty() { return new Page(List.of(), 0, 0, 0, false, false); }
    }

    public static Page newest(Path root, String id) throws IOException {
        Path file = safeFile(root, id);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return Page.empty();
        try (FileChannel channel = open(file)) {
            long size = validateFile(channel);
            return previous(channel, size, size);
        }
    }

    /** Reads the next older page, ending just before the current oldest row. */
    public static Page older(Path root, String id, long end, long snapshotEnd) throws IOException {
        try (FileChannel channel = open(safeFile(root, id))) {
            validateSnapshot(channel, end, snapshotEnd);
            return previous(channel, end, snapshotEnd);
        }
    }

    /** Reads the next newer page, beginning just after the current newest row. */
    public static Page newer(Path root, String id, long start, long snapshotEnd) throws IOException {
        try (FileChannel channel = open(safeFile(root, id))) {
            validateSnapshot(channel, start, snapshotEnd);
            return forward(channel, start, snapshotEnd);
        }
    }

    private static Path safeFile(Path root, String id) throws IOException {
        Path file = PowerTraceArchive.csv(root, id);
        Path dir = file.getParent();
        if (Files.isSymbolicLink(root) || Files.isSymbolicLink(dir.getParent())
                || Files.isSymbolicLink(dir) || Files.isSymbolicLink(file)) {
            throw new IOException("unsafe archive symlink");
        }
        return file;
    }

    private static FileChannel open(Path file) throws IOException {
        // LinkOption is also an OpenOption. Protect against final-component link
        // substitution between the explicit symlink check and channel open.
        return FileChannel.open(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    private static long validateFile(FileChannel channel) throws IOException {
        long size = channel.size();
        if (size < HEADER.length || size > PowerTraceArchive.MAX_CSV_BYTES) {
            throw new IOException("invalid archive size");
        }
        byte[] header = readExact(channel, 0, HEADER.length);
        if (!java.util.Arrays.equals(header, HEADER)) throw new IOException("invalid archive header");
        return size;
    }

    private static void validateSnapshot(FileChannel channel, long cursor, long snapshotEnd)
            throws IOException {
        long size = validateFile(channel);
        if (snapshotEnd < HEADER.length || snapshotEnd > size
                || cursor < HEADER.length || cursor > snapshotEnd) {
            throw new IOException("invalid or truncated historical snapshot");
        }
        if (cursor > HEADER.length && readExact(channel, cursor - 1, 1)[0] != '\n') {
            throw new IOException("history cursor is not on a row boundary");
        }
    }

    private static Page previous(FileChannel channel, long end, long snapshotEnd) throws IOException {
        if (end == HEADER.length) {
            return new Page(List.of(), HEADER.length, HEADER.length, snapshotEnd, false,
                    end < snapshotEnd);
        }
        if (end < HEADER.length || end > snapshotEnd) throw new IOException("bad history cursor");
        int count = (int)Math.min(MAX_QUERY_BYTES, end - HEADER.length);
        long base = end - count;
        byte[] block = readExact(channel, base, count);
        if (block[count - 1] != '\n') throw new IOException("incomplete archive row");
        ArrayList<PowerTrace.Sample> reverse = new ArrayList<>(PAGE_SIZE);
        long oldestStart = end;
        int lineEnd = count - 1;
        for (int i = count - 2; i >= 0 && reverse.size() < PAGE_SIZE; i--) {
            if (block[i] == '\n') {
                PowerTrace.Sample row = parse(block, i + 1, lineEnd - (i + 1));
                if (!reverse.isEmpty() && row.gameTick() >= reverse.getLast().gameTick()) {
                    throw new IOException("non-monotonic archive");
                }
                reverse.add(row);
                oldestStart = base + i + 1;
                lineEnd = i;
            } else if (lineEnd - i > MAX_ROW_BYTES) {
                throw new IOException("archive row exceeds limit");
            }
        }
        if (reverse.size() < PAGE_SIZE) {
            if (base != HEADER.length) throw new IOException("archive page exceeds byte budget");
            if (lineEnd > 0) {
                PowerTrace.Sample row = parse(block, 0, lineEnd);
                if (!reverse.isEmpty() && row.gameTick() >= reverse.getLast().gameTick()) {
                    throw new IOException("non-monotonic archive");
                }
                reverse.add(row);
                oldestStart = HEADER.length;
            }
        }
        Collections.reverse(reverse);
        return new Page(reverse, oldestStart, end, snapshotEnd,
                oldestStart > HEADER.length, end < snapshotEnd);
    }

    private static Page forward(FileChannel channel, long start, long snapshotEnd) throws IOException {
        int count = (int)Math.min(MAX_QUERY_BYTES, snapshotEnd - start);
        if (count == 0) {
            return new Page(List.of(), start, start, snapshotEnd, start > HEADER.length, false);
        }
        byte[] block = readExact(channel, start, count);
        ArrayList<PowerTrace.Sample> rows = new ArrayList<>(PAGE_SIZE);
        int lastBoundary = 0;
        long end = start;
        for (int i = 0; i < count && rows.size() < PAGE_SIZE; i++) {
            if (block[i] == '\n') {
                PowerTrace.Sample row = parse(block, lastBoundary, i - lastBoundary);
                if (!rows.isEmpty() && row.gameTick() <= rows.getLast().gameTick()) {
                    throw new IOException("non-monotonic archive");
                }
                rows.add(row);
                end = start + i + 1;
                lastBoundary = i + 1;
            } else if (i - lastBoundary + 1 > MAX_ROW_BYTES) {
                throw new IOException("archive row exceeds limit");
            }
        }
        if (rows.size() < PAGE_SIZE && end != snapshotEnd) {
            throw new IOException("archive row incomplete or byte budget exhausted");
        }
        return new Page(rows, start, end, snapshotEnd, start > HEADER.length, end < snapshotEnd);
    }

    private static PowerTrace.Sample parse(byte[] bytes, int offset, int count) throws IOException {
        if (count <= 0 || count > MAX_ROW_BYTES) throw new IOException("invalid archive row length");
        for (int i = offset; i < offset + count; i++) {
            if (bytes[i] < 32 || bytes[i] > 126) throw new IOException("non-ASCII archive record");
        }
        String[] fields = new String(bytes, offset, count, StandardCharsets.US_ASCII)
                .split(",", -1);
        if (fields.length != 3 || fields[0].isEmpty() || fields[1].isEmpty()) {
            throw new IOException("invalid archive CSV columns");
        }
        try {
            PowerTrace.Status status = PowerTrace.Status.valueOf(fields[1]);
            if (status == PowerTrace.Status.MEASURED && fields[2].isEmpty()) {
                throw new IllegalArgumentException("missing measured power");
            }
            if (status != PowerTrace.Status.MEASURED && !fields[2].isEmpty()) {
                throw new IllegalArgumentException("unreliable power represented as measured");
            }
            return new PowerTrace.Sample(Long.parseLong(fields[0]), status,
                    status == PowerTrace.Status.MEASURED ? Double.parseDouble(fields[2]) : 0);
        } catch (IllegalArgumentException exception) {
            throw new IOException("invalid archive CSV sample", exception);
        }
    }

    private static byte[] readExact(FileChannel channel, long position, int count) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(count);
        long at = position;
        while (buffer.hasRemaining()) {
            int n = channel.read(buffer, at);
            if (n <= 0) throw new IOException("short or stalled archive read");
            at += n;
        }
        return buffer.array();
    }
}
