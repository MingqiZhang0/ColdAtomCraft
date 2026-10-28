package dev.coldatomcraft.experiment.measurement;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Bounded preview of a long-term measurement trace. Invalid observations are
 * deliberately missing values, never zero-valued optical readings.
 *
 * <p>Game ticks are a logical server clock, not actual elapsed SI seconds.
 * Full session history belongs to the server CSV archive, not this ring.</p>
 */
public final class PowerTrace {
    public static final int PREVIEW_CAPACITY = 512;
    public static final long MAX_CONNECTED_TICK_GAP = 100;
    public static final int MAX_SAVED_CHARACTERS = 65536;

    public enum Status {
        MEASURED, NO_BEAM, MULTIPLE_UNRESOLVED, INCOMPLETE,
        NO_METER, MULTIPLE_METERS, ADJACENT_UNLOADED
    }

    public record Sample(long gameTick, Status status, double watts) {
        public Sample {
            Objects.requireNonNull(status, "status");
            if (gameTick < 0 || !Double.isFinite(watts) || watts < 0
                    || (status != Status.MEASURED && watts != 0.0)) {
                throw new IllegalArgumentException("invalid optical sample");
            }
        }
        public boolean valid() { return status == Status.MEASURED; }
        public String csvRow() {
            return gameTick + "," + status.name() + "," +
                    (valid() ? Double.toString(watts) : "") + "\n";
        }
    }

    private final ArrayDeque<Sample> recent = new ArrayDeque<>();

    public int size() { return recent.size(); }
    public List<Sample> samples() { return List.copyOf(recent); }
    public long lastTick() { return recent.isEmpty() ? -1 : recent.getLast().gameTick(); }

    public void append(Sample sample) {
        Objects.requireNonNull(sample, "sample");
        if (!recent.isEmpty() && sample.gameTick() <= lastTick()) {
            throw new IllegalArgumentException("non-monotonic measurement clock");
        }
        if (recent.size() == PREVIEW_CAPACITY) recent.removeFirst();
        recent.addLast(sample);
    }

    /** Strict, bounded, non-silent save parser. Callers should pause on errors. */
    public static PowerTrace decode(String text) {
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_SAVED_CHARACTERS) {
            throw new IllegalArgumentException("saved power trace exceeds limit");
        }
        PowerTrace trace = new PowerTrace();
        if (text.isEmpty()) return trace;
        String[] rows = text.split("\\n", -1);
        if (rows.length > PREVIEW_CAPACITY + 1 || !rows[rows.length - 1].isEmpty()) {
            throw new IllegalArgumentException("malformed power trace rows");
        }
        for (int i = 0; i < rows.length - 1; i++) {
            String[] fields = rows[i].split(",", -1);
            if (fields.length != 3 || fields[0].isEmpty() || fields[1].isEmpty()) {
                throw new IllegalArgumentException("malformed power sample");
            }
            try {
                Status status = Status.valueOf(fields[1]);
                if (status != Status.MEASURED && !fields[2].isEmpty()) {
                    throw new IllegalArgumentException("invalid missing reading");
                }
                if (status == Status.MEASURED && fields[2].isEmpty()) {
                    throw new IllegalArgumentException("missing measured value");
                }
                trace.append(new Sample(Long.parseLong(fields[0]), status,
                        status == Status.MEASURED ? Double.parseDouble(fields[2]) : 0));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("invalid saved sample " + i, exception);
            }
        }
        return trace;
    }

    public String encode() {
        StringBuilder result = new StringBuilder();
        for (Sample sample : recent) result.append(sample.csvRow());
        if (result.length() > MAX_SAVED_CHARACTERS) {
            throw new IllegalStateException("encoded power trace exceeds limit");
        }
        return result.toString();
    }

    /** Self-contained SVG; gaps stay disconnected and cannot become fake zeroes. */
    public String renderSvg() {
        final int width = 880, height = 430;
        final double left = 82, right = 839, top = 48, bottom = 353;
        List<Sample> data = samples();
        long t0 = data.isEmpty() ? 0 : data.getFirst().gameTick();
        long t1 = data.isEmpty() ? 1 : data.getLast().gameTick();
        double ymax = 0;
        long valid = 0;
        for (Sample sample : data) {
            if (sample.valid()) {
                valid++;
                ymax = Math.max(ymax, sample.watts());
            }
        }
        if (!(ymax > 0)) ymax = 0.001;
        StringBuilder svg = new StringBuilder(4096 + data.size() * 30);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"880\" height=\"430\" viewBox=\"0 0 880 430\">\n")
           .append("<rect width=\"880\" height=\"430\" fill=\"#111820\"/>\n")
           .append("<text x=\"82\" y=\"29\" fill=\"#eef3f8\" font-size=\"18\">CAC diagnostic power trace (DEV)</text>\n")
           .append("<text x=\"82\" y=\"403\" fill=\"#b8c6d5\" font-size=\"12\">Server game ticks (not real elapsed seconds)</text>\n")
           .append("<text x=\"12\" y=\"42\" fill=\"#b8c6d5\" font-size=\"12\">Power (mW)</text>\n");
        for (int k = 0; k <= 4; k++) {
            double y = bottom - (bottom - top) * k / 4;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" stroke=\"#32404e\"/>\n", left,y,right,y));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"8\" y=\"%.1f\" fill=\"#b8c6d5\" font-size=\"11\">%.4g</text>\n",
                    y+4, ymax * 1000 * k / 4));
        }
        for (int k = 0; k <= 4; k++) {
            double x = left + (right - left) * k / 4;
            long tick = t0 + (long)((t1 - t0) * (k / 4.0));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"%.1f\" y=\"370\" fill=\"#b8c6d5\" font-size=\"11\" text-anchor=\"middle\">%d</text>\n",x,tick));
        }
        // Each invalid sample breaks the polyline. Never interpolate through gaps.
        ArrayList<String> path = new ArrayList<>();
        long previousTick = -1;
        for (Sample sample : data) {
            if (previousTick >= 0 && sample.gameTick() - previousTick > MAX_CONNECTED_TICK_GAP) {
                addPath(svg, path);
                path.clear();
            }
            previousTick = sample.gameTick();
            if (!sample.valid()) {
                addPath(svg, path);
                path.clear();
                continue;
            }
            double x = left + (right-left) * (t1 == t0 ? 0.5 : (sample.gameTick()-t0)/(double)(t1-t0));
            double y = bottom - (bottom-top) * sample.watts()/ymax;
            path.add(String.format(Locale.ROOT,"%.2f,%.2f",x,y));
        }
        addPath(svg, path);
        svg.append("<text x=\"82\" y=\"387\" fill=\"#b8c6d5\" font-size=\"12\">Valid points: ")
           .append(valid).append(" / ").append(data.size()).append("; invalid samples are gaps</text>\n")
           .append("</svg>\n");
        return svg.toString();
    }

    private static void addPath(StringBuilder svg, List<String> path) {
        if (path.isEmpty()) return;
        if (path.size() == 1) {
            String[] xy = path.getFirst().split(",");
            svg.append("<circle cx=\"").append(xy[0]).append("\" cy=\"").append(xy[1])
               .append("\" r=\"3\" fill=\"#70e1c3\"/>\n");
        } else {
            svg.append("<polyline fill=\"none\" stroke=\"#70e1c3\" stroke-width=\"2\" points=\"")
               .append(String.join(" ", path)).append("\"/>\n");
        }
    }
}
