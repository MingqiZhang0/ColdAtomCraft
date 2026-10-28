package dev.coldatomcraft.experiment.motion;

import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** D22E same-tick provenance and read-only SI conversion. */
class LoadedApparatusSnapshotTest {
    private static final String DIM="minecraft:overworld";
    private static LaboratoryMountFrame.BlockAddress addr(long x,long y,long z) {
        return new LaboratoryMountFrame.BlockAddress(x,y,z);
    }
    private static State mirror(int selector) {
        return new State(Kind.MIRROR,Axis.SOUTH,selector,false,null,Binding.BLOCKSTATE_ONLY);
    }
    private static State source(int mw) {
        return new State(Kind.LASER,Axis.EAST,-1,true,
                new LaserTuning(mw,780,500,0),Binding.BLOCK_ENTITY_INSTANCE);
    }
    private static class ReaderStub implements Reader {
        final Object world=new Object();
        final Map<LaboratoryMountFrame.BlockAddress,Observed> map=new HashMap<>();
        long tick=120;
        boolean onThread=true;
        String dim=DIM;
        @Override public Object serverWorldIdentity() { return world; }
        @Override public String dimensionId() { return dim; }
        @Override public long gameTick() { return tick; }
        @Override public boolean serverThread() { return onThread; }
        @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress p) { return map.containsKey(p); }
        @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress p) { return map.get(p); }
    }
    private static LaboratoryMountFrame frame() {
        return LaboratoryMountFrame.nominalMinecraftMetres(DIM,addr(29_999_998,70,-29_999_998));
    }

    @Test void capturesTrueLocalPositionAndOrientationAtLargeWorldCoordinates() {
        var r=new ReaderStub();
        var at=addr(29_999_999,71,-29_999_997);
        r.map.put(at,new Observed(mirror(2),null));
        var snapshot=LoadedApparatusSnapshot.capture(r,frame(),List.of(at));
        var device=snapshot.devices().getFirst();
        assertEquals(1,device.centerMetres().x(),0);
        assertEquals(1,device.centerMetres().y(),0);
        assertEquals(1,device.centerMetres().z(),0);
        assertEquals(1,device.indicatedAxisLab().z(),0);
        assertEquals("INCIDENT_TRAVEL",device.state().axisMeaning());
        assertEquals("READ_ONLY_SERVER_SNAPSHOT",snapshot.provenance());
        assertFalse(device.authenticatedHardwarePose());
        assertEquals(Check.VALID,snapshot.verify(r));
    }
    @Test void laserReferencesActualInstanceAndExactParameterRecord() {
        var r=new ReaderStub(); var a=addr(29_999_998,70,-29_999_998);
        Object be=new Object();
        r.map.put(a,new Observed(source(20),be));
        var snapshot=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        assertEquals(Check.VALID,snapshot.verify(r));
        assertEquals(20,snapshot.devices().getFirst().state().laserSettings().powerMilliwatts());
        assertEquals("EMISSION_TRAVEL",snapshot.devices().getFirst().state().axisMeaning());
        assertEquals(1,snapshot.devices().getFirst().indicatedAxisLab().x(),0);
        r.map.put(a,new Observed(source(20),new Object()));
        assertEquals(Check.BLOCK_ENTITY_REPLACED,snapshot.verify(r));
    }
    @Test void laserTuningChangeInSameEntityIsNotTrusted() {
        var r=new ReaderStub();var a=frame().originBlock();Object be=new Object();
        r.map.put(a,new Observed(source(20),be));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        r.map.put(a,new Observed(source(25),be));
        assertEquals(Check.BLOCKSTATE_CHANGED,snap.verify(r));
    }
    @Test void snapshotExpiresOnNextTick() {
        var r=new ReaderStub(); var a=frame().originBlock();
        r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        r.tick++;
        assertEquals(Check.TICK_CHANGED,snap.verify(r));
    }
    @Test void sourceWorldReferenceAndDimensionBothBound() {
        var r=new ReaderStub(); var a=frame().originBlock();
        r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        var other=new ReaderStub();other.map.put(a,new Observed(mirror(0),null));
        assertEquals(Check.SERVER_CHANGED,snap.verify(other));
        r.dim="minecraft:the_nether";
        assertEquals(Check.DIMENSION_CHANGED,snap.verify(r));
    }
    @Test void offThreadCaptureIsRejectedAndVerifyFailsClosed() {
        var r=new ReaderStub();var a=frame().originBlock();r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        r.onThread=false;
        assertEquals(Check.OFF_THREAD,snap.verify(r));
        assertThrows(IllegalStateException.class,()->LoadedApparatusSnapshot.capture(r,frame(),List.of(a)));
    }
    @Test void unloadedDeviceIsRejectedBeforeRead() {
        var r=new ReaderStub();var a=frame().originBlock();
        assertThrows(IllegalStateException.class,()->LoadedApparatusSnapshot.capture(r,frame(),List.of(a)));
        r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        r.map.remove(a);
        assertEquals(Check.CHUNK_UNLOADED,snap.verify(r));
    }
    @Test void opticRotationAndSelectorChangeInvalidateSnapshot() {
        var r=new ReaderStub();var a=frame().originBlock();
        r.map.put(a,new Observed(mirror(2),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        r.map.put(a,new Observed(mirror(3),null));
        assertEquals(Check.BLOCKSTATE_CHANGED,snap.verify(r));
        r.map.put(a,new Observed(new State(Kind.MIRROR,Axis.NORTH,2,false,null,Binding.BLOCKSTATE_ONLY),null));
        assertEquals(Check.BLOCKSTATE_CHANGED,snap.verify(r));
    }
    @Test void invalidApparatusSettingsFailBeforeCapture() {
        assertThrows(IllegalArgumentException.class,()->new State(Kind.MIRROR,Axis.SOUTH,4,false,null,Binding.BLOCKSTATE_ONLY));
        assertThrows(IllegalArgumentException.class,()->new State(Kind.METER,Axis.NORTH,3,false,null,Binding.BLOCKSTATE_ONLY));
        assertThrows(IllegalArgumentException.class,()->new State(Kind.LASER,Axis.EAST,-1,true,LaserTuning.DEFAULT,Binding.BLOCKSTATE_ONLY));
        assertThrows(IllegalArgumentException.class,()->new State(Kind.OPTICAL_TABLE,Axis.EAST,-1,false,null,Binding.BLOCKSTATE_ONLY));
        assertThrows(IllegalArgumentException.class,()->new State(Kind.OPTICAL_TABLE,Axis.NONE,-1,true,null,Binding.BLOCKSTATE_ONLY));
    }
    @Test void incorrectBlockEntityIdentityIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new Observed(mirror(0),new Object()));
        assertThrows(IllegalArgumentException.class,()->new Observed(source(20),null));
    }
    @Test void duplicateOrOversizedQueryRejected() {
        var r=new ReaderStub();var a=frame().originBlock();r.map.put(a,new Observed(mirror(0),null));
        assertThrows(IllegalArgumentException.class,()->LoadedApparatusSnapshot.capture(r,frame(),List.of(a,a)));
        assertThrows(IllegalArgumentException.class,()->LoadedApparatusSnapshot.capture(r,frame(),List.of()));
        var many=new java.util.ArrayList<LaboratoryMountFrame.BlockAddress>();
        for(int i=0;i<LoadedApparatusSnapshot.MAX_DEVICES+1;i++)many.add(addr(i,0,0));
        assertThrows(IllegalArgumentException.class,()->LoadedApparatusSnapshot.capture(r,frame(),many));
    }
    @Test void wrongDimensionFrameRejected() {
        var r=new ReaderStub();var a=frame().originBlock();r.map.put(a,new Observed(mirror(0),null));
        var wrong=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:the_nether",a);
        assertThrows(IllegalStateException.class,()->LoadedApparatusSnapshot.capture(r,wrong,List.of(a)));
    }
    @Test void farAwayBlocksRejectedBySIFrame() {
        var r=new ReaderStub();var a=addr(-29_999_999,70,-29_999_998);
        r.map.put(a,new Observed(mirror(0),null));
        assertThrows(IllegalArgumentException.class,()->LoadedApparatusSnapshot.capture(r,frame(),List.of(a)));
    }
    @Test void devicesListIsImmutable() {
        var r=new ReaderStub();var a=frame().originBlock();r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        assertThrows(UnsupportedOperationException.class,()->snap.devices().clear());
    }
    @Test void statelessSameStateIsOnlyBestEffortNotPhysicalIdentity() {
        var r=new ReaderStub();var a=frame().originBlock();r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        // Exactly same replacement is NOT DETECTABLE for a stateless block.
        r.map.put(a,new Observed(mirror(0),null));
        assertEquals(Check.VALID,snap.verify(r));
        assertEquals(Binding.BLOCKSTATE_ONLY,snap.devices().getFirst().state().binding());
    }
    @Test void tickMutationDuringVerificationCannotProduceValidResult() {
        var r=new ReaderStub();var a=frame().originBlock();
        r.map.put(a,new Observed(mirror(0),null));
        var snap=LoadedApparatusSnapshot.capture(r,frame(),List.of(a));
        Reader changing=new Reader() {
            @Override public Object serverWorldIdentity(){return r.serverWorldIdentity();}
            @Override public String dimensionId(){return r.dimensionId();}
            @Override public long gameTick(){return r.gameTick();}
            @Override public boolean serverThread(){return r.serverThread();}
            @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress p){return r.isLoaded(p);}
            @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress p){
                var result=r.readLoaded(p);
                r.tick++;
                return result;
            }
        };
        assertEquals(Check.TICK_CHANGED,snap.verify(changing));
    }
    @Test void tickMutationDuringCaptureIsRejected() {
        var r=new ReaderStub();var a=frame().originBlock();
        r.map.put(a,new Observed(mirror(0),null));
        Reader changing=new Reader() {
            @Override public Object serverWorldIdentity(){return r.serverWorldIdentity();}
            @Override public String dimensionId(){return r.dimensionId();}
            @Override public long gameTick(){return r.gameTick();}
            @Override public boolean serverThread(){return r.serverThread();}
            @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress p){return r.isLoaded(p);}
            @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress p){
                var result=r.readLoaded(p);
                r.tick++;
                return result;
            }
        };
        assertThrows(IllegalStateException.class,
                () -> LoadedApparatusSnapshot.capture(changing,frame(),List.of(a)));
    }
    @Test void meterOrientationIsFrontNormalNotRayDirection() {
        var r=new ReaderStub();var a=frame().originBlock();
        r.map.put(a,new Observed(new State(Kind.METER,Axis.NORTH,2,false,null,Binding.BLOCKSTATE_ONLY),null));
        var d=LoadedApparatusSnapshot.capture(r,frame(),List.of(a)).devices().getFirst();
        assertEquals(-1,d.indicatedAxisLab().z(),0);
        assertEquals("DETECTOR_FRONT_NORMAL",d.state().axisMeaning());
    }
}
