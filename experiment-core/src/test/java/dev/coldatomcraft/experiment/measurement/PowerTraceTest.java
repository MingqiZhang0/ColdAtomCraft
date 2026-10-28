package dev.coldatomcraft.experiment.measurement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerTraceTest {
    @Test void validMeasuredAndMissingValuesAreDifferent() {
        var trace = new PowerTrace();
        trace.append(new PowerTrace.Sample(100, PowerTrace.Status.MEASURED, 0));
        trace.append(new PowerTrace.Sample(200, PowerTrace.Status.NO_BEAM, 0));
        trace.append(new PowerTrace.Sample(300, PowerTrace.Status.MEASURED, 0.015));
        assertEquals("100,MEASURED,0.0\n200,NO_BEAM,\n300,MEASURED,0.015\n", trace.encode());
        assertEquals(trace.samples(), PowerTrace.decode(trace.encode()).samples());
        assertThrows(IllegalArgumentException.class, () -> new PowerTrace.Sample(0, PowerTrace.Status.INCOMPLETE, 0.2));
        assertThrows(IllegalArgumentException.class, () -> new PowerTrace.Sample(0, PowerTrace.Status.MEASURED, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new PowerTrace.Sample(-1, PowerTrace.Status.MEASURED, 0));
    }
    @Test void sampleTimeIsStrictlyIncreasing() {
        var trace = new PowerTrace();
        trace.append(new PowerTrace.Sample(100, PowerTrace.Status.NO_BEAM, 0));
        assertThrows(IllegalArgumentException.class, () -> trace.append(new PowerTrace.Sample(100, PowerTrace.Status.NO_BEAM, 0)));
        assertThrows(IllegalArgumentException.class, () -> trace.append(new PowerTrace.Sample(99, PowerTrace.Status.NO_BEAM, 0)));
    }
    @Test void ringIsBoundedWithoutDestroyingCsv() {
        var trace = new PowerTrace();
        for (int i = 0; i < 1200; i++) trace.append(new PowerTrace.Sample(i, PowerTrace.Status.NO_BEAM, 0));
        assertEquals(PowerTrace.PREVIEW_CAPACITY, trace.size());
        assertEquals(688, trace.samples().getFirst().gameTick());
        assertEquals(1199, PowerTrace.decode(trace.encode()).lastTick());
    }
    @Test void savedDataRejectsSpoofingAndTruncation() {
        for (String value : List.of("10,NO_BEAM,1.0\n", "10,MEASURED,\n", "10,MEASURED,NaN\n",
                "10,MEASURED,-1\n", "10,MEASURED,3\n9,MEASURED,1\n", "10,MEASURED,3",
                "x,MEASURED,4\n", "10,MEASURED,4\n\n")) {
            assertThrows(IllegalArgumentException.class, () -> PowerTrace.decode(value), value);
        }
        assertThrows(IllegalArgumentException.class, () -> PowerTrace.decode("X".repeat(65537)));
    }
    @Test void invalidRowsAppearAsChartGaps() {
        var trace = new PowerTrace();
        trace.append(new PowerTrace.Sample(1, PowerTrace.Status.MEASURED, 0.01));
        trace.append(new PowerTrace.Sample(2, PowerTrace.Status.MEASURED, 0.02));
        trace.append(new PowerTrace.Sample(3, PowerTrace.Status.INCOMPLETE, 0));
        trace.append(new PowerTrace.Sample(4, PowerTrace.Status.MEASURED, 0.03));
        trace.append(new PowerTrace.Sample(5, PowerTrace.Status.MEASURED, 0.04));
        var svg = trace.renderSvg();
        assertEquals(2, svg.split("<polyline", -1).length - 1);
        assertTrue(svg.contains("invalid samples are gaps"));
        assertFalse(svg.contains("NaN"));
        assertTrue(svg.startsWith("<svg"));
    }
    @Test void pausedTimeIntervalsNeverLookContinuouslyMeasured() {
        var trace = new PowerTrace();
        trace.append(new PowerTrace.Sample(0, PowerTrace.Status.MEASURED,0.01));
        trace.append(new PowerTrace.Sample(100, PowerTrace.Status.MEASURED,0.01));
        trace.append(new PowerTrace.Sample(400, PowerTrace.Status.MEASURED,0.02));
        trace.append(new PowerTrace.Sample(500, PowerTrace.Status.MEASURED,0.02));
        assertEquals(2, trace.renderSvg().split("<polyline",-1).length-1);
    }
    @Test void emptyAndZeroValidDataChartHasFiniteCoordinates() {
        assertFalse(new PowerTrace().renderSvg().contains("NaN"));
        var trace = new PowerTrace();
        trace.append(new PowerTrace.Sample(5, PowerTrace.Status.MEASURED, 0));
        assertTrue(trace.renderSvg().contains("<circle"));
        assertFalse(trace.renderSvg().contains("Infinity"));
    }
    @Test void csvArchiveAppendsWithoutConfusingMissingWithZero() throws IOException {
        Path dir = Files.createTempDirectory("cac-power-trace-");
        try {
            String id = "d481c75a-46aa-4f07-a4a2-110000000002_1234_12_64_-8";
            PowerTraceArchive.append(dir,id,new PowerTrace.Sample(1, PowerTrace.Status.NO_BEAM, 0));
            PowerTraceArchive.append(dir,id,new PowerTrace.Sample(2, PowerTrace.Status.MEASURED, 0));
            var csv = Files.readString(PowerTraceArchive.csv(dir,id));
            assertEquals(PowerTraceArchive.HEADER + "1,NO_BEAM,\n2,MEASURED,0.0\n",csv);
            var trace = new PowerTrace();
            trace.append(new PowerTrace.Sample(2, PowerTrace.Status.MEASURED, 0));
            assertTrue(Files.readString(PowerTraceArchive.exportSvg(dir,id,trace)).contains("<svg"));
        } finally {
            try (var walk = Files.walk(dir)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException e) { throw new IllegalStateException(e); }
            }); }
        }
    }
    @Test void archiveRejectsPathsAndLimit() throws IOException {
        Path dir = Files.createTempDirectory("cac-reject-");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> PowerTraceArchive.csv(dir,"../../etc/passwd"));
            assertThrows(IllegalArgumentException.class,
                    () -> PowerTraceArchive.svg(dir,"RECORDER"));
            String id = "d481c75a-46aa-4f07-a4a2-110000000002_1234_12_64_-8";
            Files.createDirectories(PowerTraceArchive.directory(dir));
            Path path = PowerTraceArchive.csv(dir,id);
            try (var out = Files.newByteChannel(path, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE)) {
                out.position(PowerTraceArchive.MAX_CSV_BYTES);
                out.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
            }
            assertThrows(IOException.class,
                    () -> PowerTraceArchive.append(dir,id,new PowerTrace.Sample(1,PowerTrace.Status.MEASURED,0)));
        } finally {
            try(var walk=Files.walk(dir)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch(IOException e) { throw new IllegalStateException(e); }
            }); }
        }
    }
}
