package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.motion.DiscretePresetReport;
import dev.coldatomcraft.experiment.motion.DiscretePresetSweep;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Independent Java reference test of D22B four-actual-preset server protocol. */
public final class PresetSweepSmoke {
    static int checks;
    static void check(boolean condition, String msg) {
        checks++; if (!condition) throw new AssertionError(msg + " @check " + checks);
    }
    static PowerTrace.Sample sample(long tick, PowerTrace.Status status, double power) {
        return new PowerTrace.Sample(tick,status,status == PowerTrace.Status.MEASURED ? power : 0);
    }
    static void rejects(Runnable task) {
        boolean failed = false;
        try {task.run();} catch (IllegalArgumentException exc) {failed=true;}
        check(failed,"must reject invalid dataset");
    }
    public static void main(String[] args) {
        Random random = new Random(22022);
        for (int run=0;run<1600;run++) {
            long tick = 3 + run * 100L;
            DiscretePresetSweep sweep = new DiscretePresetSweep();
            int actual = random.nextInt(4);
            var first = sweep.advance(tick++, actual, true, null);
            check(first.action()==DiscretePresetSweep.Action.SET_PRESET && first.preset()==0,"first command must select 0");
            actual = 0; // trusted world adapter successfully applied the BlockState
            for(int preset=0;preset<4;preset++) {
                for(int stable=0;stable<DiscretePresetSweep.SETTLE_TICKS;stable++) {
                    var decision = sweep.advance(tick++, actual, true, null);
                    check(decision.preset()==preset,"preset unchanged during settling");
                    check(decision.action()==(stable==DiscretePresetSweep.SETTLE_TICKS-1
                            ? DiscretePresetSweep.Action.READ : DiscretePresetSweep.Action.WAIT),"stability threshold");
                }
                var status = PowerTrace.Status.values()[random.nextInt(4)];
                double watts = status==PowerTrace.Status.MEASURED ? random.nextDouble()*3 : 0;
                var response = sweep.advance(tick, actual,true,sample(tick,status,watts));
                tick++;
                check(response.action()==(preset==3 ? DiscretePresetSweep.Action.FINISHED
                                : DiscretePresetSweep.Action.SET_PRESET),"next action after sample");
                if(preset<3) actual=preset+1;
            }
            check(sweep.phase()==DiscretePresetSweep.Phase.COMPLETE,"scan completes");
            var points=sweep.readings();
            check(points.size()==4,"four raw observations");
            String csv = DiscretePresetReport.csv(points);
            check(csv.startsWith(DiscretePresetReport.HEADER),"csv header");
            check(DiscretePresetReport.decode(csv).equals(points),"persistence roundtrip");
            String svg=DiscretePresetReport.svg(points);
            check(svg.contains("D14 optical selector") && !svg.contains("NaN")
                    && !svg.contains("Infinity"),"safe honest SVG");
            check(sweep.advance(tick++,actual,true,null).action()==DiscretePresetSweep.Action.FINISHED,
                    "completed scans never start again");
        }
        // Explicit interlock and fault-injection paths.
        for (int fault=0;fault<600;fault++) {
            var scan=new DiscretePresetSweep();
            long tick=5+fault;
            check(scan.advance(tick++,2,true,null).action()==DiscretePresetSweep.Action.SET_PRESET,"set");
            var action = switch(fault%6){
                case 0 -> scan.advance(tick,1,true,null); // wrong actual preset
                case 1 -> scan.advance(tick,0,false,null); // lost device
                case 2 -> scan.advance(tick+1,0,true,null); // skipped Tick
                case 3 -> scan.advance(tick,0,true,sample(tick,PowerTrace.Status.NO_BEAM,0)); // unsolicited
                case 4 -> scan.advance(tick,4,true,null); // invalid selector
                default -> {scan.cancel();yield scan.advance(tick,0,true,null);} // operator stop
            };
            check(action.action()==DiscretePresetSweep.Action.STOP,"interlock stops");
            check(scan.phase()==DiscretePresetSweep.Phase.ABORTED,"no unsafe resume");
        }
        rejects(()->DiscretePresetReport.decode("bad"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"0,1,MEASURED,\n"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"0,1,NO_BEAM,0\n"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"1,1,NO_BEAM,\n"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"0,-1,NO_BEAM,\n"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"0,2,MEASURED,NaN\n"));
        rejects(()->DiscretePresetReport.decode(DiscretePresetReport.HEADER+"0,2,MEASURED,Infinity\n"));
        rejects(()->DiscretePresetReport.decode("x".repeat(1000)));
        check(DiscretePresetReport.decode("").isEmpty(),"empty save valid");
        check(DiscretePresetReport.decode(DiscretePresetReport.HEADER).isEmpty(),"empty csv valid");
        check(!DiscretePresetReport.svg(List.of()).contains("NaN"),"empty chart finite");
        var extreme = List.of(new DiscretePresetSweep.Point(0, sample(10,PowerTrace.Status.MEASURED,Double.MAX_VALUE)));
        check(!DiscretePresetReport.svg(extreme).contains("Infinity"),"extreme power scale finite");
        System.out.println("D22B PresetSweepSmoke PASS " + checks + "/" + checks);
    }
}
