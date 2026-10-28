package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone independent 16-bit menu and ring-paging contract: Java 21+. */
public final class D21BMeasurementUiSmoke {
    public static void main(String[] args) {
        Random random = new Random(0xC21B2026L);
        int checks=0;
        for(int run=0;run<500;run++) {
            PowerTrace trace=new PowerTrace();
            int count=1+random.nextInt(512);
            long tick=Math.abs(random.nextLong()%100_000_000L);
            for(int i=0;i<count;i++) {
                tick += 1 + random.nextInt(150);
                PowerTrace.Status status=PowerTrace.Status.values()[random.nextInt(PowerTrace.Status.values().length)];
                double watts=status==PowerTrace.Status.MEASURED ? random.nextDouble()*0.2 : 0;
                PowerTrace.Sample sample=new PowerTrace.Sample(tick,status,watts);
                trace.append(sample);
                int[] words=PowerTraceMenuCodec.encode(sample);
                PowerTrace.Sample copy=PowerTraceMenuCodec.decode(slot -> (short)words[slot],0);
                if(!sample.equals(copy))throw new AssertionError("short transport corrupts sample");
                checks++;
            }
            for(int range=0;range<4;range++) {
                List<PowerTrace.Sample> filtered=PowerTracePage.filter(trace.samples(),range);
                ArrayList<PowerTrace.Sample> restored=new ArrayList<>();
                for(int page=PowerTracePage.pages(filtered)-1;page>=0;page--) {
                    restored.addAll(PowerTracePage.page(filtered,page));
                }
                if(!filtered.equals(restored))throw new AssertionError("paging changes history");
                if(!filtered.isEmpty() && filtered.getLast().gameTick()!=trace.lastTick())
                    throw new AssertionError("newest point vanished");
                checks += filtered.size()+2;
            }
        }
        System.out.println("PASS D21B signed-short wire and UI history paging: "+checks+" checks");
    }
}
