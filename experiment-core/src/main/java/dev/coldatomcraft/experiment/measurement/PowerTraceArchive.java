package dev.coldatomcraft.experiment.measurement;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.List;

/** Server-side, append-only CSV with a hard per-recorder file limit. */
public final class PowerTraceArchive {
    public static final long MAX_CSV_BYTES = 64L * 1024 * 1024;
    public static final String HEADER = "server_game_tick,status,captured_watts\n";
    private PowerTraceArchive() { }

    public static Path directory(Path worldRoot) {
        return Objects.requireNonNull(worldRoot, "worldRoot")
                .resolve("coldatomcraft").resolve("measurements");
    }

    public static Path csv(Path worldRoot, String id) { return path(worldRoot,id,".csv"); }
    public static Path svg(Path worldRoot, String id) { return path(worldRoot,id,".svg"); }

    private static Path path(Path worldRoot, String id, String extension) {
        if (id == null || !id.matches("[a-f0-9-]{36}_[0-9a-f]{1,8}_-?[0-9]+_-?[0-9]+_-?[0-9]+")) {
            throw new IllegalArgumentException("invalid recorder identity");
        }
        return directory(worldRoot).resolve(id + extension);
    }

    public static void append(Path root, String id, PowerTrace.Sample sample) throws IOException {
        Objects.requireNonNull(sample,"sample");
        Path file = csv(root, id);
        Files.createDirectories(file.getParent());
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())
                || Files.isSymbolicLink(file.getParent().getParent())) {
            throw new IOException("unsafe recorder archive link");
        }
        long size = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? Files.size(file) : 0;
        String body = (size == 0 ? HEADER : "") + sample.csvRow();
        long bytes = body.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_CSV_BYTES - bytes) throw new IOException("recorder archive full");
        Files.writeString(file, body, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * Export only the currently viewed, server-selected history page. Files
     * overwrite the previous page export instead of proliferating forever.
     * The immutable long-term CSV is NEVER truncated by this helper.
     */
    public static void exportHistoryPage(Path root, String id, List<PowerTrace.Sample> samples)
            throws IOException {
        Objects.requireNonNull(samples, "samples");
        if (samples.isEmpty() || samples.size() > PowerTraceHistory.PAGE_SIZE) {
            throw new IllegalArgumentException("historical export requires one nonempty page");
        }
        Path data = path(root,id,".history-page.csv");
        Path graph = path(root,id,".history-page.svg");
        Files.createDirectories(data.getParent());
        for (Path file : List.of(data,graph)) {
            if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())
                    || Files.isSymbolicLink(file.getParent().getParent())) {
                throw new IOException("unsafe history page export link");
            }
        }
        StringBuilder csv = new StringBuilder(HEADER);
        PowerTrace trace = new PowerTrace();
        for (PowerTrace.Sample sample : samples) {
            trace.append(Objects.requireNonNull(sample,"sample"));
            csv.append(sample.csvRow());
        }
        // Page exports are not an archival replacement: the primary append-only
        // CSV remains untouched. Never put a user-controlled name in either path.
        Files.writeString(data,csv.toString(),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(graph,trace.renderSvg(),StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Export only two server-authorized 32-point views; never expose an
     * instrument identity or filesystem path through a client request.
     */
    public static void exportComparisonPage(Path root, String primaryId,
            PowerTraceComparison.Frame frame) throws IOException {
        Objects.requireNonNull(frame, "frame");
        Path data = path(root, primaryId, ".compare-page.csv");
        Path graph = path(root, primaryId, ".compare-page.svg");
        Files.createDirectories(data.getParent());
        for (Path file : List.of(data, graph)) {
            if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())
                    || Files.isSymbolicLink(file.getParent().getParent())) {
                throw new IOException("unsafe comparison export link");
            }
        }
        // Current GUI view only; never rewrite either long-term raw CSV.
        Files.writeString(data, PowerTraceComparison.csv(frame), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.writeString(graph, PowerTraceComparison.svg(frame), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static Path exportSvg(Path root, String id, PowerTrace trace) throws IOException {
        Path file = svg(root, id);
        Files.createDirectories(file.getParent());
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())
                || Files.isSymbolicLink(file.getParent().getParent())) {
            throw new IOException("unsafe recorder archive link");
        }
        Files.writeString(file, Objects.requireNonNull(trace,"trace").renderSvg(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }
}
