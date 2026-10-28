package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceComparison;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone deterministic oracle, no Minecraft runtime or client renderer. */
public final class D21DMultiChannelSmoke {
    private static long checks;
    private static void ok(boolean result,String reason) {
        checks++;
        if (!result) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        Random random=new Random(0xD21D2026L);
        for(int test=0;test<1_600;test++) {
            long t=(1L<<50)+random.nextInt(100_000);
            List<PowerTrace.Sample> a=new ArrayList<>();
            for(int i=0;i<32;i++) {
                t+=100;
                boolean valid=random.nextInt(8)!=0;
                a.add(new PowerTrace.Sample(t,valid?PowerTrace.Status.MEASURED:PowerTrace.Status.NO_BEAM,
                        valid?random.nextDouble()*.1:0));
            }
            List<PowerTrace.Sample> b=new ArrayList<>();
            long base=a.getFirst().gameTick()-200;
            int steps=0;
            while(base<=a.getLast().gameTick()+200) {
                base+=100+random.nextInt(7);
                boolean valid=random.nextInt(5)!=0;
                b.add(new PowerTrace.Sample(base,valid?PowerTrace.Status.MEASURED:PowerTrace.Status.INCOMPLETE,
                        valid?random.nextDouble()*.1:0));
                if (++steps>36) break;
            }
            List<PowerTrace.Sample> found=PowerTraceComparison.secondaryWindow(a,b);
            ArrayList<PowerTrace.Sample> expected=new ArrayList<>();
            for(PowerTrace.Sample x:b) {
                if(x.gameTick()>=a.getFirst().gameTick()&&x.gameTick()<=a.getLast().gameTick()) expected.add(x);
            }
            ok(found.equals(expected),"comparison added/missed raw readings");
            var frame=PowerTraceComparison.frame(a,found);
            ok(frame.firstTick()==a.getFirst().gameTick(),"x-min changed");
            ok(frame.lastTick()==a.getLast().gameTick(),"x-max changed");
            String csv=PowerTraceComparison.csv(frame);
            ok(csv.lines().count()==1L+a.size()+found.size(),"export row mismatch");
            ok(csv.startsWith("channel,server_game_tick,status,captured_watts\n"),"wrong header");
            ok(PowerTraceComparison.svg(frame).contains("same") == false,"unexpected label");
            for(PowerTrace.Sample s:found){
                ok(csv.contains("B,"+s.csvRow()),"export altered B sample");
                ok(s.gameTick()>=frame.firstTick()&&s.gameTick()<=frame.lastTick(),"cross-time view");
            }
            for(PowerTrace.Sample s:a){
                ok(csv.contains("A,"+s.csvRow()),"export altered A sample");
            }
        }
        System.out.println("PASS D21D independent dual-channel comparisons: "+checks+" checks");
    }
}
