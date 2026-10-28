package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import dev.coldatomcraft.experiment.measurement.PowerTraceArchive;
import dev.coldatomcraft.experiment.measurement.PowerTraceHistory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** D21C standalone Java archive paging oracle: actual files, no Minecraft runtime. */
public final class D21CArchiveHistorySmoke {
    private static final String ID = "12345678-1234-4234-a234-123456789abc_d9_7_64_-11";
    private static long checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void fails(CheckedOp op, String message) throws Exception {
        boolean failed = false;
        try { op.run(); } catch (IOException exception) { failed = true; }
        check(failed, message);
    }
    @FunctionalInterface private interface CheckedOp { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("cac-d21c-history-");
        Path file = PowerTraceArchive.csv(root, ID);
        check(PowerTraceHistory.newest(root, ID).samples().isEmpty(), "missing archive must be empty");
        List<PowerTrace.Sample> original = new ArrayList<>();
        Random random = new Random(0xD21C2026);
        long tick = 1L << 54; // exercise the full 64-bit historical tick path
        for (int i=0;i<1_731;i++) {
            tick += 100L + random.nextInt(80);
            PowerTrace.Status status = PowerTrace.Status.values()[random.nextInt(PowerTrace.Status.values().length)];
            double power = status == PowerTrace.Status.MEASURED ? random.nextDouble() * .17 : 0;
            PowerTrace.Sample sample = new PowerTrace.Sample(tick,status,power);
            PowerTraceArchive.append(root,ID,sample);
            original.add(sample);
        }
        PowerTraceHistory.Page newest = PowerTraceHistory.newest(root,ID);
        check(newest.samples().size()==32,"newest page has 32 records");
        check(newest.hasOlder()&&!newest.hasNewer(),"newest controls");
        ArrayList<PowerTrace.Sample> reversed = new ArrayList<>();
        PowerTraceHistory.Page page = newest;
        long initialSnapshot = newest.snapshotEnd();
        int pages=0;
        while (true) {
            for (int i=page.samples().size()-1;i>=0;i--) reversed.add(page.samples().get(i));
            check(page.snapshotEnd() == initialSnapshot,"snapshot must not move");
            pages++;
            if (!page.hasOlder()) break;
            page = PowerTraceHistory.older(root,ID,page.startOffset(),initialSnapshot);
        }
        check(pages==55,"page count");
        check(reversed.size()==original.size(),"no lost or duplicate rows");
        for (int i=0;i<original.size();i++) {
            check(reversed.get(i).equals(original.get(original.size()-1-i)),"reverse paging changed data");
        }
        check(!page.hasOlder(),"oldest page older disabled");
        int backPages=1;
        while (page.hasNewer()) {
            page = PowerTraceHistory.newer(root,ID,page.endOffset(),initialSnapshot);
            backPages++;
        }
        check(backPages==55 && page.samples().equals(newest.samples()),"newer traversal recovered latest page");

        // Snapshot must not drift when additional legitimate samples are appended.
        PowerTrace.Sample extra = new PowerTrace.Sample(tick+100,PowerTrace.Status.MEASURED,.001);
        PowerTraceArchive.append(root,ID,extra);
        PowerTraceHistory.Page older = PowerTraceHistory.older(root,ID,newest.startOffset(),initialSnapshot);
        check(older.samples().getLast().gameTick() < newest.samples().getFirst().gameTick(),"append changed frozen page");
        check(PowerTraceHistory.newest(root,ID).samples().getLast().equals(extra),"new sample missing");
        fails(() -> PowerTraceHistory.older(root,ID,initialSnapshot+2,initialSnapshot),"out-of-snapshot cursor accepted");
        fails(() -> PowerTraceHistory.older(root,ID,newest.startOffset()+1,initialSnapshot),"nonboundary cursor accepted");

        Files.writeString(file,"server_game_tick,status,captured_watts\n1,MEASURED,0.12\n2,NO_BEAM,\n",StandardCharsets.US_ASCII);
        check(PowerTraceHistory.newest(root,ID).samples().size()==2,"small CSV parsing");
        Files.writeString(file,"server_game_tick,status,captured_watts\n1,MEASURED,0.12",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"torn end row accepted");
        Files.writeString(file,"server_game_tick,status,captured_watts\n1,NO_BEAM,0.2\n",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"fabricated invalid optical reading accepted");
        Files.writeString(file,"server_game_tick,status,captured_watts\n1,MEASURED,NaN\n",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"nonfinite optical reading accepted");
        Files.writeString(file,"server_game_tick,status,captured_watts\n1,MEASURED,0.1\n1,MEASURED,0.2\n",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"nonmonotonic ticks accepted");
        Files.writeString(file,"bad-header\n1,MEASURED,0.1\n",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"bad header accepted");
        Files.writeString(file,PowerTraceArchive.HEADER+"1,MEASURED,"+"1".repeat(300)+"\n",StandardCharsets.US_ASCII);
        fails(() -> PowerTraceHistory.newest(root,ID),"oversize row accepted");
        Files.delete(file);
        try {
            Files.createSymbolicLink(file, Path.of("missing-outside-data"));
            fails(() -> PowerTraceHistory.newest(root,ID),"symbolic-link archive accepted");
            Files.delete(file);
        } catch (UnsupportedOperationException | IOException | SecurityException ignored) {
            // On hosts without symlink privileges, other invariant tests still run.
        }
        check(PowerTraceHistory.newest(root,ID).samples().isEmpty(),"missing after deletion must be empty");
        System.out.println("PASS D21C bounded historical CSV paging: " + checks + " checks");
    }
}
