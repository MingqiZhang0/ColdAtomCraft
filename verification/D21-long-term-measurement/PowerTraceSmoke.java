package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceArchive;
import java.nio.file.Files;
import java.util.Random;

public final class PowerTraceSmoke {
    private static int checks;
    private static void expect(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("D21 measurement invariant " + checks);
    }
    public static void main(String[] args) throws Exception {
        Random random = new Random(0xD21L);
        for (int round=0;round<240;round++) {
            PowerTrace trace=new PowerTrace();
            for (int k=0;k<600;k++) {
                long tick=k*100L;
                PowerTrace.Status status = PowerTrace.Status.values()[random.nextInt(PowerTrace.Status.values().length)];
                double watts=status==PowerTrace.Status.MEASURED ? random.nextDouble()*0.2 : 0;
                trace.append(new PowerTrace.Sample(tick,status,watts));
                expect(trace.size()<=PowerTrace.PREVIEW_CAPACITY);
            }
            expect(trace.samples().getFirst().gameTick()==88*100L);
            expect(trace.samples().equals(PowerTrace.decode(trace.encode()).samples()));
            String svg=trace.renderSvg();
            expect(svg.startsWith("<svg") && svg.endsWith("</svg>\n"));
            expect(!svg.contains("NaN") && !svg.contains("Infinity"));
        }
        var root=Files.createTempDirectory("cac-d21-smoke-");
        var id="00000000-0000-4000-8000-000000000021_1234_0_64_0";
        for(int i=0;i<100;i++) {
            PowerTraceArchive.append(root,id,new PowerTrace.Sample(i,PowerTrace.Status.MEASURED,0.04));
        }
        var content=Files.readString(PowerTraceArchive.csv(root,id));
        expect(content.split("\\n").length==101);
        Files.deleteIfExists(PowerTraceArchive.csv(root,id));
        Files.deleteIfExists(PowerTraceArchive.directory(root));
        Files.deleteIfExists(root.resolve("coldatomcraft"));
        Files.deleteIfExists(root);
        System.out.println("PASS D21 independent long-term measurement: "+checks+" assertions");
    }
}
