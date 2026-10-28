package dev.coldatomcraft.experiment.measurement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Real on-disk CSV tests; server side and pure experiment-core only. */
class PowerTraceHistoryTest {
    private static final String ID = "12345678-1234-4234-a234-123456789abc_e22_3_4_-12";
    private static Path root() throws IOException { return Files.createTempDirectory("cac-d21c-junit-"); }

    @Test void missingArchiveIsEmpty() throws IOException {
        assertEquals(PowerTraceHistory.Page.empty(), PowerTraceHistory.newest(root(), ID));
    }

    @Test void oneSampleAndExactZeroStayMeasured() throws IOException {
        Path root = root();
        PowerTrace.Sample sample = new PowerTrace.Sample((1L << 58)+9, PowerTrace.Status.MEASURED,0);
        PowerTraceArchive.append(root,ID,sample);
        var page = PowerTraceHistory.newest(root,ID);
        assertEquals(List.of(sample),page.samples());
        assertFalse(page.hasOlder());
        assertFalse(page.hasNewer());
    }

    @Test void allPagesRebuildOriginalDataExactly() throws IOException {
        Path root=root();
        List<PowerTrace.Sample> original = new ArrayList<>();
        for(int i=0;i<257;i++) {
            var status = i%3==0?PowerTrace.Status.NO_BEAM:PowerTrace.Status.MEASURED;
            var sample = new PowerTrace.Sample(10_000_000_000L+i*100L,status,
                    status==PowerTrace.Status.MEASURED?i/1000.0:0);
            PowerTraceArchive.append(root,ID,sample);
            original.add(sample);
        }
        var page=PowerTraceHistory.newest(root,ID);
        long snapshotEnd=page.snapshotEnd();
        List<PowerTrace.Sample> backward=new ArrayList<>();
        while (true) {
            List<PowerTrace.Sample> reverse = new ArrayList<>(page.samples());
            Collections.reverse(reverse);
            backward.addAll(reverse);
            if (!page.hasOlder()) break;
            page=PowerTraceHistory.older(root,ID,page.startOffset(),snapshotEnd);
        }
        Collections.reverse(backward);
        assertEquals(original,backward);
        assertEquals(9, (original.size()+31)/32);
    }

    @Test void snapshotRemainsStableAcrossAppend() throws IOException {
        Path root=root();
        for(int i=1;i<=99;i++) PowerTraceArchive.append(root,ID,
                new PowerTrace.Sample(i*100L,PowerTrace.Status.MEASURED,i*0.01));
        var last=PowerTraceHistory.newest(root,ID);
        PowerTraceArchive.append(root,ID,new PowerTrace.Sample(10000,PowerTrace.Status.NO_BEAM,0));
        var previous=PowerTraceHistory.older(root,ID,last.startOffset(),last.snapshotEnd());
        assertTrue(previous.hasNewer());
        var latest=PowerTraceHistory.newer(root,ID,previous.endOffset(),last.snapshotEnd());
        assertEquals(last.samples(),latest.samples());
        assertEquals(10000,PowerTraceHistory.newest(root,ID).samples().getLast().gameTick());
    }

    @Test void corruptedRowsAndTornWritesAreRejected() throws IOException {
        Path root=root(); Path file=PowerTraceArchive.csv(root,ID);
        Files.createDirectories(file.getParent());
        for(String contents:List.of(
                "wrong-header\n1,MEASURED,1\n",
                PowerTraceArchive.HEADER+"1,MEASURED,1",
                PowerTraceArchive.HEADER+"1,MEASURED,NaN\n",
                PowerTraceArchive.HEADER+"1,INCOMPLETE,0\n",
                PowerTraceArchive.HEADER+"1,MEASURED,1\n1,MEASURED,2\n",
                PowerTraceArchive.HEADER+"1,MEASURED,"+"9".repeat(150)+"\n")) {
            Files.writeString(file,contents,StandardCharsets.US_ASCII);
            assertThrows(IOException.class,()->PowerTraceHistory.newest(root,ID));
        }
    }

    @Test void forgedCursorAndTruncationAreRejected() throws IOException {
        Path root=root();
        for(int i=1;i<=50;i++) PowerTraceArchive.append(root,ID,
                new PowerTrace.Sample(i,PowerTrace.Status.NO_BEAM,0));
        var page=PowerTraceHistory.newest(root,ID);
        assertThrows(IOException.class,()->PowerTraceHistory.older(root,ID,
                page.startOffset()+1,page.snapshotEnd()));
        Files.writeString(PowerTraceArchive.csv(root,ID),PowerTraceArchive.HEADER);
        assertThrows(IOException.class,()->PowerTraceHistory.older(root,ID,
                page.startOffset(),page.snapshotEnd()));
    }

    @Test void historicalPageExportsOnlySelectedRowsWithoutMutatingArchive() throws IOException {
        Path root = root();
        for (int i=0;i<80;i++) {
            var status=i%5==0?PowerTrace.Status.INCOMPLETE:PowerTrace.Status.MEASURED;
            PowerTraceArchive.append(root,ID,new PowerTrace.Sample(i*100L,status,
                    status==PowerTrace.Status.MEASURED?i/1000.0:0));
        }
        Path archive = PowerTraceArchive.csv(root,ID);
        String original = Files.readString(archive);
        var history=PowerTraceHistory.newest(root,ID);
        PowerTraceArchive.exportHistoryPage(root,ID,history.samples());
        Path exportCsv=archive.resolveSibling(archive.getFileName().toString().replace(".csv", ".history-page.csv"));
        Path exportSvg=archive.resolveSibling(archive.getFileName().toString().replace(".csv", ".history-page.svg"));
        String csv=Files.readString(exportCsv);
        String svg=Files.readString(exportSvg);
        assertEquals(33,csv.split("\n").length);
        assertTrue(csv.contains("INCOMPLETE,"));
        assertTrue(svg.contains("invalid samples are gaps"));
        assertEquals(original,Files.readString(archive));
        var older=PowerTraceHistory.older(root,ID,history.startOffset(),history.snapshotEnd());
        PowerTraceArchive.exportHistoryPage(root,ID,older.samples());
        assertNotEquals(csv,Files.readString(exportCsv));
        assertEquals(original,Files.readString(archive));
    }

    @Test void emptyOrOverfullHistoricalExportIsRejected() throws IOException {
        Path root=root();
        assertThrows(IllegalArgumentException.class,
                () -> PowerTraceArchive.exportHistoryPage(root,ID,List.of()));
        List<PowerTrace.Sample> rows=new ArrayList<>();
        for(int i=0;i<33;i++)rows.add(new PowerTrace.Sample(i,PowerTrace.Status.NO_BEAM,0));
        assertThrows(IllegalArgumentException.class,
                () -> PowerTraceArchive.exportHistoryPage(root,ID,rows));
    }

    @Test void readBudgetAndInvalidIdentityFailClosed() throws IOException {
        assertEquals(16*1024,PowerTraceHistory.MAX_QUERY_BYTES);
        assertEquals(32,PowerTraceHistory.PAGE_SIZE);
        assertThrows(IllegalArgumentException.class,()->PowerTraceHistory.newest(root(),"../secret"));
    }
}
