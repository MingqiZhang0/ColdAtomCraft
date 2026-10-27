import dev.coldatomcraft.experiment.*;
import dev.coldatomcraft.experiment.world.*;
import java.util.*;
/**
 * Non-JUnit synthetic microbenchmark. One JVM, five runs; no claims about TPS.
 * Compile against either D1 or D2 to compare on exactly the same host/JVM.
 */
public class LedgerBenchmark {
    private static final ModelDefinition MODEL = new ModelDefinition("BENCH-SYNTHETIC", "A",
            "test-only:never-production", ModelApproval.OWNER_APPROVED,
            ModelVerification.NUMERICALLY_VERIFIED);
    private static final WorldReadSet READS = WorldReadSet.of(
            new WorldRevision("overworld", 0, 0, 1, 1, true));
    private static final DeviceSnapshot DEVICE =
            new DeviceSnapshot("overworld",0,64,0,"benchmark-laser",1,true);
    private static final TargetSnapshot TARGET =
            new TargetSnapshot("benchmark-target",TargetKind.ENTITY,"overworld",1,64,1,1,true);

    private static double register(int n) {
        var ledger = new WorldEffectLedger();
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            var step = new CalculationStep("benchmark-run",i,new SimulationInterval(i*1000L,(i+1)*1000L));
            if (ledger.beginCalculation(step) != WorldEffectLedger.BeginResult.STARTED) {
                throw new AssertionError("registration failed: " + i);
            }
        }
        return (System.nanoTime()-start)/1e6;
    }
    private static double claim(int n) {
        var ledger = new WorldEffectLedger();
        for (int i = 0; i < n; i++) {
            var step = new CalculationStep("benchmark-run",i,new SimulationInterval(i*1000L,(i+1)*1000L));
            if (ledger.beginCalculation(step) != WorldEffectLedger.BeginResult.STARTED) throw new AssertionError();
        }
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            var step = new CalculationStep("benchmark-run",i,new SimulationInterval(i*1000L,(i+1)*1000L));
            var candidate = new EnergyTransferCandidate("BENCH-SYNTHETIC","A",DEVICE,TARGET,0.01,
                    step,0,READS);
            var precheck = WorldEffectGate.precheck(candidate, MODEL, READS, DEVICE, TARGET);
            if (ledger.claim(candidate,precheck) != WorldEffectLedger.ClaimResult.CLAIMED) {
                throw new AssertionError("claim failed: " + i);
            }
            if (!ledger.markApplied(candidate.eventKey())) throw new AssertionError("mark failed");
        }
        return (System.nanoTime()-start)/1e6;
    }
    private static void print(String label,int n, java.util.function.IntToDoubleFunction function) {
        for (int i=0;i<3;i++) function.applyAsDouble(Math.min(n,3000));
        var times = new double[5];
        for (int i=0;i<5;i++) times[i]=function.applyAsDouble(n);
        Arrays.sort(times);
        System.out.printf(java.util.Locale.ROOT,"%s %d median %.3f ms trials %s%n",
                label,n,times[2],Arrays.toString(times));
    }
    public static void main(String[] args) {
        System.out.println("Java="+System.getProperty("java.version")+" cp="+args[0]);
        for(int n:new int[]{1_000,10_000,50_000}) print("register",n,LedgerBenchmark::register);
        print("claim+mark",20_000,LedgerBenchmark::claim);
    }
}
