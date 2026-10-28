package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compact persisted results and SVG/CSV for integer D14 optical preset scans. */
public final class DiscretePresetReport {
    public static final String HEADER = "optic_preset,server_game_tick,status,watts\n";
    public static final int MAX_SAVED_CHARS = 512;
    private DiscretePresetReport() { }

    public static List<DiscretePresetSweep.Point> validated(List<DiscretePresetSweep.Point> input) {
        if (input == null || input.size() > DiscretePresetSweep.PRESETS) {
            throw new IllegalArgumentException("invalid D22B result size");
        }
        List<DiscretePresetSweep.Point> points = List.copyOf(input);
        long lastTick = -1;
        for (int i = 0; i < points.size(); i++) {
            var point = points.get(i);
            if (point.preset() != i || point.sample().gameTick() <= lastTick) {
                throw new IllegalArgumentException("invalid D22B sample order");
            }
            lastTick = point.sample().gameTick();
        }
        return points;
    }

    public static String csv(List<DiscretePresetSweep.Point> input) {
        var points = validated(input);
        StringBuilder out = new StringBuilder(HEADER);
        for (var point : points) {
            var sample = point.sample();
            out.append(point.preset()).append(',').append(sample.gameTick()).append(',')
               .append(sample.status()).append(',');
            if (sample.valid()) out.append(Double.toString(sample.watts()));
            out.append('\n');
        }
        return out.toString();
    }

    public static List<DiscretePresetSweep.Point> decode(String encoded) {
        if (encoded == null || encoded.length() > MAX_SAVED_CHARS) {
            throw new IllegalArgumentException("untrusted preset dataset length");
        }
        if (encoded.isEmpty() || encoded.equals(HEADER)) return List.of();
        if (!encoded.startsWith(HEADER) || !encoded.endsWith("\n")) {
            throw new IllegalArgumentException("malformed preset dataset header");
        }
        var rows = encoded.substring(HEADER.length()).split("\n", -1);
        if (rows.length < 2 || rows.length > DiscretePresetSweep.PRESETS + 1 || !rows[rows.length-1].isEmpty()) {
            throw new IllegalArgumentException("malformed preset dataset rows");
        }
        ArrayList<DiscretePresetSweep.Point> data = new ArrayList<>();
        for (int i = 0; i < rows.length - 1; i++) {
            var fields = rows[i].split(",", -1);
            if (fields.length != 4) throw new IllegalArgumentException("invalid preset sample fields");
            try {
                int preset = Integer.parseInt(fields[0]);
                long tick = Long.parseLong(fields[1]);
                var status = PowerTrace.Status.valueOf(fields[2]);
                if (status == PowerTrace.Status.MEASURED ? fields[3].isEmpty() : !fields[3].isEmpty()) {
                    throw new IllegalArgumentException("invalid missing/valid power field");
                }
                double watts = status == PowerTrace.Status.MEASURED ? Double.parseDouble(fields[3]) : 0.0;
                data.add(new DiscretePresetSweep.Point(preset, new PowerTrace.Sample(tick, status, watts)));
            } catch (IllegalArgumentException exc) {
                throw new IllegalArgumentException("invalid preset row " + i, exc);
            }
        }
        return validated(data);
    }

    /** Plot of actual measured D14 selector (not calibrated angle). Invalids remain gaps. */
    public static String svg(List<DiscretePresetSweep.Point> input) {
        var points = validated(input);
        final int left = 90, right = 782, top = 58, bottom = 344;
        double max = 0;
        for (var point : points) if (point.sample().valid()) max = Math.max(max,point.sample().watts());
        if (!(max > 0)) max = 0.001;
        boolean milli = max < 1e300;
        StringBuilder svg = new StringBuilder(1400);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"850\" height=\"420\" viewBox=\"0 0 850 420\">\n")
           .append("<rect width=\"850\" height=\"420\" fill=\"#111820\"/>\n")
           .append("<text x=\"90\" y=\"30\" fill=\"#ffffff\" font-size=\"18\">CAC D22B four-preset scan (DEV)</text>\n")
           .append("<text x=\"90\" y=\"400\" fill=\"#b8c6d5\" font-size=\"12\">D14 optical selector (integer 0..3; NOT calibrated angle)</text>\n")
           .append("<text x=\"12\" y=\"45\" fill=\"#b8c6d5\" font-size=\"12\">Power (")
           .append(milli ? "mW" : "W").append(")</text>\n");
        for (int j=0;j<4;j++) {
            double x=left+(right-left)*j/3.0;
            svg.append(String.format(Locale.ROOT,"<text x=\"%.1f\" y=\"365\" text-anchor=\"middle\" fill=\"#b8c6d5\">%d</text>\n",x,j));
        }
        ArrayList<String> segment = new ArrayList<>();
        for (var point : points) {
            if (!point.sample().valid()) { flush(svg,segment); segment.clear(); continue; }
            double x = left+(right-left)*point.preset()/3.0;
            double y = bottom-(bottom-top)*(point.sample().watts()/max);
            segment.add(String.format(Locale.ROOT,"%.3f,%.3f",x,y));
        }
        flush(svg,segment);
        svg.append("<text x=\"90\" y=\"383\" fill=\"#b8c6d5\" font-size=\"12\">No interpolation across invalid observations; actual server optical solve</text>\n")
           .append("</svg>\n");
        return svg.toString();
    }
    private static void flush(StringBuilder svg, List<String> segment) {
        if (segment.size()==1) {
            var pair=segment.getFirst().split(",");
            svg.append("<circle cx=\"").append(pair[0]).append("\" cy=\"")
               .append(pair[1]).append("\" r=\"4\" fill=\"#70e1c3\"/>\n");
        } else if (!segment.isEmpty()) {
            svg.append("<polyline points=\"").append(String.join(" ",segment))
               .append("\" fill=\"none\" stroke=\"#70e1c3\" stroke-width=\"3\"/>\n");
        }
    }
}
