package dev.coldatomcraft.experiment.measurement;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PowerTracePageTest {
    @Test void pagesAreNewestFirstAndDoNotDiscardArchiveData() {
        PowerTrace trace = new PowerTrace();
        for (int i = 0; i < 512; i++) trace.append(new PowerTrace.Sample(i * 100L,
                PowerTrace.Status.MEASURED, i / 1000.0));
        var filtered = PowerTracePage.filter(trace.samples(), PowerTracePage.RANGE_ALL);
        assertEquals(16, PowerTracePage.pages(filtered));
        assertEquals(48000L, PowerTracePage.page(filtered, 0).getFirst().gameTick());
        assertEquals(51100L, PowerTracePage.page(filtered, 0).getLast().gameTick());
        assertEquals(0L, PowerTracePage.page(filtered, 15).getFirst().gameTick());
        assertEquals(512, trace.samples().size());
    }

    @Test void filteredTimeRangesAreRelativeToLatestAndInclusive() {
        var history = new ArrayList<PowerTrace.Sample>();
        for (int i = 0; i <= 200; i++) {
            history.add(new PowerTrace.Sample(i * 100L, PowerTrace.Status.NO_BEAM, 0));
        }
        assertEquals(13, PowerTracePage.filter(history, PowerTracePage.RANGE_ONE_MINUTE).size());
        assertEquals(61, PowerTracePage.filter(history, PowerTracePage.RANGE_FIVE_MINUTES).size());
        assertEquals(181, PowerTracePage.filter(history, PowerTracePage.RANGE_FIFTEEN_MINUTES).size());
        assertEquals(201, PowerTracePage.filter(history, PowerTracePage.RANGE_ALL).size());
    }

    @Test void gapsAndInvaliditySurvivePageFiltering() {
        var history = List.of(new PowerTrace.Sample(3,PowerTrace.Status.MEASURED,0.02),
                new PowerTrace.Sample(5,PowerTrace.Status.MULTIPLE_UNRESOLVED,0),
                new PowerTrace.Sample(200,PowerTrace.Status.NO_BEAM,0),
                new PowerTrace.Sample(203,PowerTrace.Status.MEASURED,0.01));
        assertEquals(history,PowerTracePage.page(PowerTracePage.filter(history,3),0));
    }

    @Test void emptyAndInvalidInputAreBounded() {
        assertEquals(1,PowerTracePage.pages(List.of()));
        assertTrue(PowerTracePage.page(List.of(),0).isEmpty());
        assertThrows(IllegalArgumentException.class,()->PowerTracePage.page(List.of(),1));
        assertThrows(IllegalArgumentException.class,()->PowerTracePage.rangeTicks(-1));
        var same = List.of(new PowerTrace.Sample(5,PowerTrace.Status.NO_BEAM,0),
                new PowerTrace.Sample(5,PowerTrace.Status.NO_BEAM,0));
        assertThrows(IllegalArgumentException.class,()->PowerTracePage.filter(same,3));
        var oversized = new ArrayList<PowerTrace.Sample>();
        for(int i=0;i<513;i++) oversized.add(new PowerTrace.Sample(i,PowerTrace.Status.NO_BEAM,0));
        assertThrows(IllegalArgumentException.class,()->PowerTracePage.filter(oversized,3));
    }

    @Test void exact64BitTickAndDoubleSurviveSignedShortTransport() {
        long tick = (1L << 54) + 12345L;
        for(PowerTrace.Status status:PowerTrace.Status.values()) {
            double value = status == PowerTrace.Status.MEASURED ? 0.01923456789012345 : 0;
            PowerTrace.Sample original = new PowerTrace.Sample(tick,status,value);
            int[] packed=PowerTraceMenuCodec.encode(original);
            PowerTrace.Sample decoded=PowerTraceMenuCodec.decode(i -> (short) packed[i],0);
            assertEquals(original,decoded);
            assertEquals(Double.doubleToRawLongBits(original.watts()),
                    Double.doubleToRawLongBits(decoded.watts()));
        }
    }

    @Test void snapshotChecksumDetectsSignedShortPacketCorruption() {
        int[] words = PowerTraceMenuCodec.encode(new PowerTrace.Sample(18000,
                PowerTrace.Status.MEASURED,0.0123456789));
        int checksum = PowerTraceMenuCodec.checksum(i -> (short)words[i],words.length);
        assertEquals(checksum,PowerTraceMenuCodec.checksum(i -> words[i],words.length));
        words[5] ^= 1;
        assertNotEquals(checksum,PowerTraceMenuCodec.checksum(i -> words[i],words.length));
        assertThrows(IllegalArgumentException.class,()->PowerTraceMenuCodec.checksum(i->0,4097));
    }

    @Test void emptyAndCorruptedSamplesCannotBecomeMeasured() {
        var empty=PowerTraceMenuCodec.empty();
        assertTrue(PowerTraceMenuCodec.decode(i->(short) empty[i],0) == null);
        int[] invalid=PowerTraceMenuCodec.encode(new PowerTrace.Sample(1,PowerTrace.Status.NO_BEAM,0));
        invalid[8]=12;
        assertThrows(IllegalArgumentException.class,()->PowerTraceMenuCodec.decode(i->invalid[i],0));
        var broken=PowerTraceMenuCodec.encode(new PowerTrace.Sample(1,PowerTrace.Status.MEASURED,0.1));
        broken[4]=0xffff;broken[5]=0xffff;broken[6]=0xffff;broken[7]=0x7fff;
        assertThrows(IllegalArgumentException.class,()->PowerTraceMenuCodec.decode(i->broken[i],0));
    }
}
