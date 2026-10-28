package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.measurement.PowerTrace;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** JUnit5 Gradle tests; also runnable under a separate offline compatibility shim. */
class DiscretePresetSweepTest {
    static PowerTrace.Sample measured(long tick,double watts) {
        return new PowerTrace.Sample(tick,PowerTrace.Status.MEASURED,watts);
    }
    @Test void startsWithActualPresetRequest() {
        var c=new DiscretePresetSweep();
        var d=c.advance(100,3,true,null);
        assertEquals(DiscretePresetSweep.Action.SET_PRESET,d.action());
        assertEquals(0,d.preset());
    }
    @Test void cannotReadBeforeFiveStableTicks() {
        var c=new DiscretePresetSweep();c.advance(0,2,true,null);
        for(int t=1;t<5;t++) assertEquals(DiscretePresetSweep.Action.WAIT,c.advance(t,0,true,null).action());
        assertEquals(DiscretePresetSweep.Action.READ,c.advance(5,0,true,null).action());
        assertEquals(DiscretePresetSweep.Phase.AWAIT_SAMPLE,c.phase());
    }
    @Test void readsNextTickAndThenSetsNextRealPreset() {
        var c=new DiscretePresetSweep();c.advance(0,1,true,null);
        for(int t=1;t<=5;t++)c.advance(t,0,true,null);
        var d=c.advance(6,0,true,measured(6,.024));
        assertEquals(DiscretePresetSweep.Action.SET_PRESET,d.action());
        assertEquals(1,d.preset());
        assertEquals(.024,c.readings().getFirst().sample().watts());
    }
    @Test void missingTickStopsAndCannotResume() {
        var c=new DiscretePresetSweep();c.advance(10,2,true,null);
        assertEquals(DiscretePresetSweep.Action.STOP,c.advance(12,0,true,null).action());
        assertEquals(DiscretePresetSweep.Action.STOP,c.advance(13,0,true,null).action());
    }
    @Test void externalPresetEditStopsWithoutAcceptingData() {
        var c=new DiscretePresetSweep();c.advance(0,2,true,null);
        assertEquals(DiscretePresetSweep.Action.STOP,c.advance(1,3,true,null).action());
        assertTrue(c.readings().isEmpty());
    }
    @Test void noDeviceAndUnexpectedSampleStop() {
        var c=new DiscretePresetSweep();c.advance(0,1,true,null);
        assertEquals(DiscretePresetSweep.Action.STOP,c.advance(1,0,false,null).action());
        var d=new DiscretePresetSweep();
        assertEquals(DiscretePresetSweep.Action.STOP,d.advance(0,1,true,measured(0,.5)).action());
    }
    @Test void cancelAlwaysStops() {
        var c=new DiscretePresetSweep();c.cancel();
        assertEquals(DiscretePresetSweep.Action.STOP,c.advance(1,0,true,null).action());
    }
    @Test void invalidObservationsAreMissingValues() {
        var points=List.of(new DiscretePresetSweep.Point(0,
                new PowerTrace.Sample(17,PowerTrace.Status.NO_BEAM,0)));
        String csv=DiscretePresetReport.csv(points);
        assertTrue(csv.contains("0,17,NO_BEAM,\n"));
        assertFalse(csv.contains("0,17,NO_BEAM,0"));
        assertEquals(points,DiscretePresetReport.decode(csv));
    }
    @Test void invalidSavedDataRejected() {
        assertThrows(IllegalArgumentException.class,()->DiscretePresetReport.decode("wrong"));
        assertThrows(IllegalArgumentException.class,()->DiscretePresetReport.decode(
                DiscretePresetReport.HEADER+"0,1,MEASURED,NaN\n"));
        assertThrows(IllegalArgumentException.class,()->DiscretePresetReport.decode(
                DiscretePresetReport.HEADER+"1,1,NO_BEAM,\n"));
    }
    @Test void emptySaveRoundtripAndGiganticPowerSvgFinite() {
        assertTrue(DiscretePresetReport.decode(DiscretePresetReport.csv(List.of())).isEmpty());
        String svg=DiscretePresetReport.svg(List.of(new DiscretePresetSweep.Point(0,
                measured(1,Double.MAX_VALUE))));
        assertFalse(svg.contains("NaN"));
        assertFalse(svg.contains("Infinity"));
        assertTrue(svg.contains("NOT calibrated angle"));
    }
}
