package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.OptionalDouble;

/** Completed, strictly ordered scan data; command positions and encoder data remain separate. */
public final class StageScanDataset {
    private StageScanDataset() { }

    public record Reading(long actualUnits, PowerTrace.Sample sample) {
        public Reading {
            Objects.requireNonNull(sample, "sample");
            if (actualUnits < -StageScanPlan.MAX_ABS_UNITS
                    || actualUnits > StageScanPlan.MAX_ABS_UNITS) {
                throw new IllegalArgumentException("invalid encoder position");
            }
        }
    }

    public record Point(int index, long commandedUnits, List<Reading> readings) {
        public Point {
            if (index < 0 || readings == null || readings.isEmpty() || readings.size() > 64) {
                throw new IllegalArgumentException("incomplete scan point");
            }
            readings = List.copyOf(readings);
            long previous = -1;
            for (Reading reading : readings) {
                Objects.requireNonNull(reading, "reading");
                if (reading.sample().gameTick() <= previous) {
                    throw new IllegalArgumentException("nonmonotonic power observation");
                }
                previous = reading.sample().gameTick();
            }
        }
        public double meanActualUnits() {
            double sum = 0;
            for (Reading reading : readings) sum += reading.actualUnits();
            return sum / readings.size();
        }
        /** A single invalid measurement invalidates the point aggregate; no zero substitution. */
        public OptionalDouble meanWatts() {
            double mean = 0;
            int n = 0;
            for (Reading reading : readings) {
                if (!reading.sample().valid()) return OptionalDouble.empty();
                n++;
                mean += (reading.sample().watts() - mean) / n;
            }
            return OptionalDouble.of(mean);
        }
    }

    /** Immutable validated completed points. Aborted partial points are not exported as complete. */
    public static List<Point> validate(List<Point> points) {
        Objects.requireNonNull(points, "points");
        List<Point> copy = List.copyOf(points);
        if (copy.size() > StageScanPlan.MAX_POINTS) {
            throw new IllegalArgumentException("too many scan points");
        }
        long lastTick = -1;
        for (int i = 0; i < copy.size(); i++) {
            Point point = Objects.requireNonNull(copy.get(i));
            if (point.index() != i || point.readings().getFirst().sample().gameTick() <= lastTick) {
                throw new IllegalArgumentException("out of order points");
            }
            lastTick = point.readings().getLast().sample().gameTick();
        }
        return copy;
    }

    /** Every raw acquisition is preserved with its target, encoder feedback, tick and status. */
    public static String csv(StageScanPlan.Unit unit, List<Point> points) {
        Objects.requireNonNull(unit, "unit");
        String suffix = unit == StageScanPlan.Unit.NANOMETRE ? "nm" : "nrad";
        StringBuilder csv = new StringBuilder("point_index,commanded_" + suffix
                + ",encoder_" + suffix + ",server_game_tick,status,watts\n");
        for (Point point : validate(points)) {
            for (Reading reading : point.readings()) {
                PowerTrace.Sample sample = reading.sample();
                csv.append(point.index()).append(',').append(point.commandedUnits()).append(',')
                   .append(reading.actualUnits()).append(',').append(sample.gameTick())
                   .append(',').append(sample.status()).append(',');
                if (sample.valid()) csv.append(Double.toString(sample.watts()));
                csv.append('\n');
            }
        }
        return csv.toString();
    }

    /** Self-contained diagnostic SVG, x=encoder position, not requested position. */
    public static String svg(StageScanPlan.Unit unit, List<Point> points) {
        Objects.requireNonNull(unit, "unit");
        List<Point> data = validate(points);
        final int left = 92, right = 830, top = 52, bottom = 348;
        double xmin = Double.POSITIVE_INFINITY;
        double xmax = Double.NEGATIVE_INFINITY;
        double ymax = 0;
        for (Point point : data) {
            double actual = point.meanActualUnits();
            xmin = Math.min(xmin, actual);
            xmax = Math.max(xmax, actual);
            if (point.meanWatts().isPresent()) {
                ymax = Math.max(ymax, point.meanWatts().getAsDouble());
            }
        }
        if (data.isEmpty()) { xmin = 0; xmax = 1; }
        if (xmin == xmax) { xmin -= 0.5; xmax += 0.5; }
        if (!(ymax > 0)) ymax = 0.001;
        boolean milliwatts = ymax <= Double.MAX_VALUE / 1000.0;
        StringBuilder svg = new StringBuilder(2048 + 90 * data.size());
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"900\" height=\"470\" viewBox=\"0 0 900 470\">\n")
           .append("<rect width=\"900\" height=\"470\" fill=\"#111820\"/>\n")
           .append("<text x=\"92\" y=\"30\" fill=\"#eef3f8\" font-size=\"18\">CAC D22 stage scan (DEV)</text>\n")
           .append("<text x=\"92\" y=\"448\" fill=\"#b8c6d5\" font-size=\"13\">Encoder position (")
           .append(unit == StageScanPlan.Unit.NANOMETRE ? "nm" : "nrad")
           .append("); NOT Minecraft block position</text>\n")
           .append("<text x=\"12\" y=\"43\" fill=\"#b8c6d5\" font-size=\"12\">Power (")
           .append(milliwatts ? "mW" : "W").append(")</text>\n");
        for (int k = 0; k <= 4; k++) {
            double y = bottom - (bottom - top) * k / 4.0;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#32404e\"/>\n",
                    left, y, right, y));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"9\" y=\"%.1f\" fill=\"#b8c6d5\" font-size=\"11\">%.4g</text>\n",
                    y + 4, (milliwatts ? ymax * 1000.0 : ymax) * (k / 4.0)));
        }
        for (int k = 0; k <= 4; k++) {
            double x = left + (right - left) * k / 4.0;
            double encoderTick = xmin + (xmax - xmin) * (k / 4.0);
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"%.1f\" y=\"367\" fill=\"#b8c6d5\" font-size=\"11\" text-anchor=\"middle\">%.5g</text>\n",
                    x, encoderTick));
        }
        ArrayList<String> path = new ArrayList<>();
        for (Point point : data) {
            OptionalDouble watts = point.meanWatts();
            if (watts.isEmpty()) {
                flush(svg, path);
                path.clear();
                continue;
            }
            double x = left + (right-left) * (point.meanActualUnits() - xmin) / (xmax-xmin);
            double y = bottom - (bottom-top) * (watts.getAsDouble() / ymax);
            path.add(String.format(Locale.ROOT,"%.3f,%.3f",x,y));
        }
        flush(svg, path);
        svg.append("<text x=\"92\" y=\"417\" fill=\"#b8c6d5\" font-size=\"12\">Invalid readings break lines; all values are server observations</text>\n")
           .append("</svg>\n");
        return svg.toString();
    }

    private static void flush(StringBuilder svg, List<String> path) {
        if (path.size() == 1) {
            String[] xy = path.getFirst().split(",");
            svg.append("<circle cx=\"").append(xy[0]).append("\" cy=\"")
               .append(xy[1]).append("\" r=\"3\" fill=\"#70e1c3\"/>\n");
        } else if (!path.isEmpty()) {
            svg.append("<polyline fill=\"none\" stroke=\"#70e1c3\" stroke-width=\"2\" points=\"")
               .append(String.join(" ", path)).append("\"/>\n");
        }
    }
}
