package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.motion.StageScanController;
import dev.coldatomcraft.experiment.motion.StageScanDataset;
import dev.coldatomcraft.experiment.motion.StageScanPlan;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** D22A seeded independent motor/encoder/measurement simulation, not live Minecraft. */
public final class D22StageScanSmoke {
    private static long checks;
    private D22StageScanSmoke() { }
    private static void check(boolean condition, String context) {
        checks++;
        if (!condition) throw new AssertionError(context);
    }
    private static long approach(long current, long target, long maximumStep) {
        if (current < target) return current + Math.min(maximumStep, target - current);
        if (current > target) return current - Math.min(maximumStep, current - target);
        return current;
    }

    private static List<StageScanDataset.Point> simulated(int run, Random random) {
        int count = 2 + random.nextInt(24);
        List<Long> targets = new ArrayList<>();
        for (int j = 0; j < count; j++) targets.add((long)random.nextInt(2001)-1000);
        StageScanPlan.Axis axis = new StageScanPlan.Axis(
                run % 2 == 0 ? StageScanPlan.Unit.NANOMETRE : StageScanPlan.Unit.NANORADIAN,
                -2_000, 2_000, 3);
        int settle = 1+random.nextInt(5);
        int nSamples = 1+random.nextInt(4);
        int sampleSpacing = 1+random.nextInt(4);
        StageScanPlan plan = new StageScanPlan(axis, targets, settle, 200, 3, sampleSpacing, nSamples);
        StageScanController controller = new StageScanController(plan);
        long position = 0;
        long commandedPosition = 0;
        long speed = 25+random.nextInt(80);
        long lastTick = -1;
        boolean pendingRead = false;
        boolean complete = false;
        for (long tick = (1L<<45)+run*100_000L; tick < (1L<<45)+run*100_000L+40_000; tick++) {
            position=approach(position,commandedPosition,speed);
            PowerTrace.Sample response = null;
            if (pendingRead) {
                boolean invalid = random.nextInt(9)==0;
                response = new PowerTrace.Sample(tick,
                        invalid ? PowerTrace.Status.INCOMPLETE : PowerTrace.Status.MEASURED,
                        invalid ? 0.0 : 0.0001 + (position+2_000.0)/100_000.0);
                pendingRead = false;
            }
            StageScanController.Decision d = controller.advance(tick,
                    new StageScanController.Feedback("motor-"+run,1,position,true,true,position == commandedPosition), response);
            check(d.action()!=StageScanController.Action.STOP,"unexpected stop in valid simulated scan");
            check(d.pointIndex()<=targets.size(),"index exceeds plan");
            if (d.action()==StageScanController.Action.MOVE) {
                commandedPosition=d.targetUnits();
                check(commandedPosition==targets.get(d.pointIndex()),"commanded wrong target");
            }
            if (d.action()==StageScanController.Action.READ) {
                check(position>=axis.minimum()&&position<=axis.maximum(),"encoder out of range");
                check(Math.abs(position-targets.get(d.pointIndex()))<=axis.tolerance(),
                        "read was requested before encoder settled");
                pendingRead=true;
            }
            if (d.action()==StageScanController.Action.FINISHED) {
                check(d.phase()==StageScanController.Phase.COMPLETE,"incorrect completion phase");
                complete=true;
                break;
            }
            lastTick=tick;
        }
        check(complete,"seeded valid scan failed to complete");
        List<StageScanDataset.Point> points=controller.completedPoints();
        check(points.size()==count,"missing finished points");
        long prevReadTick=-1;
        for(int j=0;j<points.size();j++) {
            StageScanDataset.Point point=points.get(j);
            check(point.index()==j,"wrong result order");
            check(point.commandedUnits()==targets.get(j),"target/result mismatch");
            check(point.readings().size()==nSamples,"missing raw acquisitions");
            for(StageScanDataset.Reading reading:point.readings()) {
                check(reading.sample().gameTick()>prevReadTick,"nonmonotonic timestamp");
                check(Math.abs(reading.actualUnits()-point.commandedUnits())<=axis.tolerance(),
                        "encoder did not settle before read");
                prevReadTick=reading.sample().gameTick();
            }
        }
        String csv=StageScanDataset.csv(axis.unit(),points);
        check(csv.lines().count()==1L+count*nSamples,"CSV silently dropped raw readings");
        check(!csv.contains("INCOMPLETE,0.0"),"invalid optical sample became zero");
        String svg=StageScanDataset.svg(axis.unit(),points);
        check(svg.contains("Encoder position"),"plot uses wrong x axis");
        check(!svg.contains("NaN")&&!svg.contains("Infinity"),"invalid chart geometry");
        return points;
    }

    public static void main(String[] args) throws IOException {
        Random random = new Random(0xD22A2026L);
        List<StageScanDataset.Point> demo = List.of();
        for(int run=0;run<700;run++) {
            List<StageScanDataset.Point> points=simulated(run,random);
            if (run==0) demo=points;
        }
        if(args.length==1) {
            Path folder=Path.of(args[0]);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("CAC-D22A-Demo-Stage-Scan.csv"),
                    StageScanDataset.csv(StageScanPlan.Unit.NANOMETRE,demo),StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("CAC-D22A-Demo-Stage-Scan.svg"),
                    StageScanDataset.svg(StageScanPlan.Unit.NANOMETRE,demo),StandardCharsets.UTF_8);
        } else if (args.length!=0) throw new IllegalArgumentException("expected zero args or output directory");
        System.out.println("PASS CAC D22A stage/encoder/observation simulation: "+checks+" checks");
    }
}
