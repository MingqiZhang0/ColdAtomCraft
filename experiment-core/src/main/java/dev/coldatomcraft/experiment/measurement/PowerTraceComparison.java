package dev.coldatomcraft.experiment.measurement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * D21D two-channel observation comparison, using original server game ticks.
 * Independent instruments are NEVER assumed to sample simultaneously.
 * No interpolation, resampling, subtraction, or summation of powers occurs.
 *
 * <p>This class is pure Java and performs no world, file or network access.</p>
 */
public final class PowerTraceComparison {
    private PowerTraceComparison() { }

    public record Frame(List<PowerTrace.Sample> a, List<PowerTrace.Sample> b,
                        long firstTick, long lastTick, double maxWatts) {
        public Frame {
            a = validate(a);
            b = validate(b);
            if (firstTick < 0 || lastTick < firstTick
                    || !(maxWatts > 0) || !Double.isFinite(maxWatts)) {
                throw new IllegalArgumentException("invalid comparison plot bounds");
            }
        }
    }

    /**
     * Compare one authoritative page A with all B samples within A's absolute
     * tick interval. Reject an overfull B window instead of silently
     * downsampling or fabricating aligned readings.
     */
    public static List<PowerTrace.Sample> secondaryWindow(
            List<PowerTrace.Sample> primaryPage, List<PowerTrace.Sample> secondaryHistory) {
        List<PowerTrace.Sample> primary = validate(primaryPage);
        Objects.requireNonNull(secondaryHistory, "secondary history");
        if (secondaryHistory.size() > PowerTrace.PREVIEW_CAPACITY) {
            throw new IllegalArgumentException("secondary preview exceeds bound");
        }
        if (primary.isEmpty()) return List.of();
        long start = primary.getFirst().gameTick();
        long end = primary.getLast().gameTick();
        ArrayList<PowerTrace.Sample> page = new ArrayList<>();
        long previous = -1;
        for (PowerTrace.Sample sample : secondaryHistory) {
            Objects.requireNonNull(sample, "secondary sample");
            if (sample.gameTick() <= previous) {
                throw new IllegalArgumentException("secondary ticks are not strictly increasing");
            }
            previous = sample.gameTick();
            if (sample.gameTick() < start) continue;
            if (sample.gameTick() > end) continue;
            if (page.size() == PowerTracePage.PAGE_SIZE) {
                throw new IllegalArgumentException("secondary window exceeds 32 raw samples");
            }
            page.add(sample);
        }
        return List.copyOf(page);
    }

    public static Frame frame(List<PowerTrace.Sample> a, List<PowerTrace.Sample> b) {
        a = validate(a);
        b = validate(b);
        long first = Math.min(a.isEmpty() ? Long.MAX_VALUE : a.getFirst().gameTick(),
                              b.isEmpty() ? Long.MAX_VALUE : b.getFirst().gameTick());
        if (first == Long.MAX_VALUE) first = 0;
        long last = Math.max(a.isEmpty() ? 0 : a.getLast().gameTick(),
                             b.isEmpty() ? 0 : b.getLast().gameTick());
        if (last < first) last = first;
        double max = 0;
        for (PowerTrace.Sample sample : a) if (sample.valid()) max = Math.max(max,sample.watts());
        for (PowerTrace.Sample sample : b) if (sample.valid()) max = Math.max(max,sample.watts());
        return new Frame(a,b,first,last,max > 0 ? Math.min(Double.MAX_VALUE,
                max > Double.MAX_VALUE / 1.08 ? Double.MAX_VALUE : max * 1.08) : 0.001);
    }

    public static String csv(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        StringBuilder csv = new StringBuilder("channel,server_game_tick,status,captured_watts\n");
        for (PowerTrace.Sample sample : frame.a()) csv.append("A,").append(sample.csvRow());
        for (PowerTrace.Sample sample : frame.b()) csv.append("B,").append(sample.csvRow());
        return csv.toString();
    }

    /** Self-contained plot; invalid/missing points are gaps, never zeroes. */
    public static String svg(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        int left = 82, right = 836, top = 52, bottom = 350;
        StringBuilder svg = new StringBuilder(4096);
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"880\" height=\"430\" viewBox=\"0 0 880 430\">\n")
           .append("<rect width=\"880\" height=\"430\" fill=\"#111820\"/>\n")
           .append("<text x=\"82\" y=\"29\" fill=\"#eef3f8\" font-size=\"18\">CAC dual-channel diagnostic power (DEV)</text>\n")
           .append("<text x=\"82\" y=\"402\" fill=\"#b8c6d5\" font-size=\"12\">Server game ticks; channels are NOT synchronized physical time</text>\n")
           .append("<text x=\"12\" y=\"42\" fill=\"#b8c6d5\" font-size=\"12\">mW</text>\n");
        for (int k=0;k<=4;k++) {
            double y=bottom-(bottom-top)*k/4.0;
            svg.append(String.format(Locale.ROOT,
                    "<line x1=\"%d\" y1=\"%.1f\" x2=\"%d\" y2=\"%.1f\" stroke=\"#32404e\"/>\n",
                    left,y,right,y));
            svg.append(String.format(Locale.ROOT,
                    "<text x=\"8\" y=\"%.1f\" fill=\"#b8c6d5\" font-size=\"11\">%s</text>\n",
                    y+4,milliLabel((frame.maxWatts()/4)*k)));
        }
        svg.append("<text x=\"690\" y=\"32\" fill=\"#70e1c3\" font-size=\"12\">A</text>\n")
           .append("<text x=\"735\" y=\"32\" fill=\"#ffbb70\" font-size=\"12\">B</text>\n");
        renderChannel(svg,frame.a(),frame,"#70e1c3",left,right,top,bottom);
        renderChannel(svg,frame.b(),frame,"#ffbb70",left,right,top,bottom);
        svg.append("<text x=\"82\" y=\"377\" fill=\"#b8c6d5\" font-size=\"12\">tick ")
           .append(frame.firstTick()).append(" - ").append(frame.lastTick())
           .append("; no interpolation across missing observations</text>\n")
           .append("</svg>\n");
        return svg.toString();
    }

    private static String milliLabel(double watts) {
        if (watts <= Double.MAX_VALUE / 1000) {
            return String.format(Locale.ROOT, "%.5g", watts * 1000);
        }
        return java.math.BigDecimal.valueOf(watts).movePointRight(3)
                .round(new java.math.MathContext(5)).toEngineeringString();
    }

    private static void renderChannel(StringBuilder svg, List<PowerTrace.Sample> data,
                                      Frame frame, String color, int left, int right, int top, int bottom) {
        List<String> path = new ArrayList<>();
        long previousTick=-1;
        for (PowerTrace.Sample sample : data) {
            if (previousTick >= 0 && sample.gameTick()-previousTick > PowerTrace.MAX_CONNECTED_TICK_GAP) {
                flush(svg,path,color);
                path.clear();
            }
            previousTick=sample.gameTick();
            if (!sample.valid()) {
                flush(svg,path,color);
                path.clear();
                continue;
            }
            double fraction=frame.firstTick()==frame.lastTick() ? 0.5
                    : (sample.gameTick()-frame.firstTick())/(double)(frame.lastTick()-frame.firstTick());
            double x=left+(right-left)*fraction;
            double y=bottom-(bottom-top)*sample.watts()/frame.maxWatts();
            path.add(String.format(Locale.ROOT,"%.2f,%.2f",x,y));
        }
        flush(svg,path,color);
    }

    private static void flush(StringBuilder svg,List<String> path,String color) {
        if(path.isEmpty()) return;
        if(path.size()==1) {
            String[] xy=path.getFirst().split(",",-1);
            svg.append("<circle cx=\"").append(xy[0]).append("\" cy=\"").append(xy[1])
               .append("\" r=\"3\" fill=\"").append(color).append("\"/>\n");
        }else{
            svg.append("<polyline fill=\"none\" stroke=\"").append(color)
               .append("\" stroke-width=\"2\" points=\"").append(String.join(" ",path)).append("\"/>\n");
        }
    }

    private static List<PowerTrace.Sample> validate(List<PowerTrace.Sample> samples) {
        Objects.requireNonNull(samples,"channel samples");
        if(samples.size()>PowerTracePage.PAGE_SIZE) throw new IllegalArgumentException("page too long");
        long prev=-1;
        for(PowerTrace.Sample sample:samples) {
            Objects.requireNonNull(sample,"sample");
            if(sample.gameTick()<=prev) throw new IllegalArgumentException("non-monotonic channel");
            prev=sample.gameTick();
        }
        return List.copyOf(samples);
    }
}
