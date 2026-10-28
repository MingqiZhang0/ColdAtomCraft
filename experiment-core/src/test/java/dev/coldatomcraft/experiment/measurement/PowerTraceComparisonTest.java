package dev.coldatomcraft.experiment.measurement;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PowerTraceComparisonTest {
    private static PowerTrace.Sample p(long tick, double watts) {
        return new PowerTrace.Sample(tick, PowerTrace.Status.MEASURED, watts);
    }
    private static PowerTrace.Sample gap(long tick) {
        return new PowerTrace.Sample(tick, PowerTrace.Status.NO_BEAM, 0);
    }

    @Test void distinctSamplingTicksAreNotInterpolated() {
        var a = List.of(p(100,.01),p(200,.02),p(300,.03));
        var b = List.of(p(150,.02),p(250,.01));
        assertEquals(b,PowerTraceComparison.secondaryWindow(a,b));
        var frame=PowerTraceComparison.frame(a,b);
        assertEquals(100,frame.firstTick());
        assertEquals(300,frame.lastTick());
        assertEquals(3,frame.a().size());
        assertEquals(2,frame.b().size());
        assertEquals(5,PowerTraceComparison.csv(frame).lines().count()-1);
        assertFalse(PowerTraceComparison.csv(frame).contains("A,150,"));
    }

    @Test void exactZeroRemainsDifferentFromNoBeam() {
        var frame=PowerTraceComparison.frame(List.of(p(100,0),gap(200)),List.of(gap(150),p(250,0)));
        String csv=PowerTraceComparison.csv(frame);
        assertTrue(csv.contains("A,100,MEASURED,0.0\n"));
        assertTrue(csv.contains("A,200,NO_BEAM,\n"));
        assertTrue(csv.contains("B,150,NO_BEAM,\n"));
        assertFalse(csv.contains("NO_BEAM,0"));
    }

    @Test void outOfWindowSamplesStayExcludedWithoutEndpointSynthesis() {
        var a=List.of(p(200,.02),p(300,.03));
        var b=List.of(p(100,.01),p(250,.02),p(350,.01));
        assertEquals(List.of(p(250,.02)),PowerTraceComparison.secondaryWindow(a,b));
    }

    @Test void emptyIndependentSecondaryIsValid() {
        var a=List.of(p(100,.04),p(200,.02));
        assertEquals(List.of(),PowerTraceComparison.secondaryWindow(a,List.of()));
        var frame=PowerTraceComparison.frame(a,List.of());
        assertEquals(2,PowerTraceComparison.csv(frame).lines().count()-1);
    }

    @Test void rejectOverflowInsteadOfSilentDownsampling() {
        var a=List.of(p(0,.001),p(4000,.001));
        List<PowerTrace.Sample> b=new ArrayList<>();
        for (int i=0;i<33;i++) b.add(p(i*100,.01));
        assertThrows(IllegalArgumentException.class,()->PowerTraceComparison.secondaryWindow(a,b));
    }

    @Test void rejectNonMonotonicOrOversizedPages() {
        assertThrows(IllegalArgumentException.class,()->PowerTraceComparison.frame(
                List.of(p(100,.01),p(100,.02)),List.of()));
        assertThrows(IllegalArgumentException.class,()->PowerTraceComparison.secondaryWindow(
                List.of(p(0,.01)),List.of(p(3,.01),p(2,.01))));
        List<PowerTrace.Sample> over=new ArrayList<>();
        for(int i=0;i<33;i++) over.add(p(i,.01));
        assertThrows(IllegalArgumentException.class,()->PowerTraceComparison.frame(over,List.of()));
    }

    @Test void precisionAndIndependentSeriesArePreserved() {
        long tick=(1L<<54)+333;
        var frame=PowerTraceComparison.frame(List.of(p(tick,Double.MIN_NORMAL)),
                List.of(p(tick,Double.MAX_VALUE/2)));
        String csv=PowerTraceComparison.csv(frame);
        assertTrue(csv.contains(Long.toString(tick)));
        assertTrue(csv.contains(Double.toString(Double.MIN_NORMAL)));
        assertEquals(2,csv.lines().count()-1);
        assertTrue(PowerTraceComparison.svg(frame).contains("<polyline")
                || PowerTraceComparison.svg(frame).contains("<circle"));
    }

    @Test void invalidGapSeparatesGraphSegments() {
        var frame=PowerTraceComparison.frame(List.of(p(100,.01),gap(200),p(300,.01)),List.of());
        String svg=PowerTraceComparison.svg(frame);
        assertEquals(2,svg.split("<circle",-1).length-1);
        assertEquals(0,svg.split("<polyline",-1).length-1);
    }

    @Test void comparisonExportDoesNotRewriteOriginalArchives() throws IOException {
        Path root=Files.createTempDirectory("cac-d21d-compare");
        String id="12345678-1234-4234-a234-123456789abc_a_1_2_3";
        PowerTraceArchive.append(root,id,p(100,.02));
        String before=Files.readString(PowerTraceArchive.csv(root,id));
        var frame=PowerTraceComparison.frame(List.of(p(100,.02)),List.of(p(100,.03)));
        PowerTraceArchive.exportComparisonPage(root,id,frame);
        assertEquals(before,Files.readString(PowerTraceArchive.csv(root,id)));
        String secondary=Files.readString(PowerTraceArchive.directory(root).resolve(id+".compare-page.csv"));
        assertTrue(secondary.contains("channel,server_game_tick,status,captured_watts"));
        assertTrue(secondary.contains("A,100,MEASURED,0.02"));
        assertTrue(secondary.contains("B,100,MEASURED,0.03"));
        assertTrue(Files.exists(PowerTraceArchive.directory(root).resolve(id+".compare-page.svg")));
    }

    @Test void comparisonExportRejectsUnsafeIdentity() throws IOException {
        Path root=Files.createTempDirectory("cac-d21d-reject");
        var frame=PowerTraceComparison.frame(List.of(p(100,.02)),List.of());
        assertThrows(IllegalArgumentException.class,()->PowerTraceArchive.exportComparisonPage(
                root,"../../another-world",frame));
    }
}
