package dev.coldatomcraft.verification;

import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.experiment.motion.LaboratoryMountFrame;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Deterministic independent lifecycle model; NOT a Minecraft world test. */
public final class LoadedWorldSmoke {
    private static long assertions;
    private LoadedWorldSmoke() { }
    private static void check(boolean yes, String why) {
        assertions++;
        if (!yes) throw new AssertionError(why);
    }
    private static void rejects(Runnable action,String name) {
        boolean threw=false;
        try {action.run();}catch(IllegalArgumentException | IllegalStateException exc){threw=true;}
        check(threw,name);
    }
    private static LaboratoryMountFrame.BlockAddress at(long x,long y,long z) {
        return new LaboratoryMountFrame.BlockAddress(x,y,z);
    }
    private static final class SimWorld implements Reader {
        final Object world=new Object();
        final Map<LaboratoryMountFrame.BlockAddress,Observed> devices=new HashMap<>();
        String dim="minecraft:overworld";
        long tick=20;
        boolean onThread=true;
        @Override public Object serverWorldIdentity() { return world; }
        @Override public String dimensionId() { return dim; }
        @Override public long gameTick() { return tick; }
        @Override public boolean serverThread() { return onThread; }
        @Override public boolean isLoaded(LaboratoryMountFrame.BlockAddress p) {return devices.containsKey(p);}
        @Override public Observed readLoaded(LaboratoryMountFrame.BlockAddress p) {return devices.get(p);}
    }
    private static State state(int selector) {
        return new State(Kind.MIRROR,Axis.SOUTH,selector,false,null,Binding.BLOCKSTATE_ONLY);
    }
    public static void main(String[] args) {
        var origin=at(29_999_980,90,-29_999_980);
        var frame=LaboratoryMountFrame.nominalMinecraftMetres("minecraft:overworld",origin);
        var source=at(29_999_981,90,-29_999_980);
        var sensor=at(29_999_981,90,-29_999_978);
        var stage=at(29_999_981,89,-29_999_979);
        var world=new SimWorld();
        Object sourceBe=new Object();Object stageBe=new Object();
        world.devices.put(source,new Observed(new State(Kind.LASER,Axis.SOUTH,-1,true,
                new LaserTuning(20,780,500,0),Binding.BLOCK_ENTITY_INSTANCE),sourceBe));
        world.devices.put(sensor,new Observed(new State(Kind.METER,Axis.NORTH,1,false,null,
                Binding.BLOCKSTATE_ONLY),null));
        world.devices.put(stage,new Observed(new State(Kind.AUTO_PRESET_STAGE,Axis.NONE,-1,false,
                null,Binding.BLOCK_ENTITY_INSTANCE),stageBe));
        var captured=LoadedApparatusSnapshot.capture(world,frame,List.of(source,sensor,stage));
        check(captured.devices().size()==3,"three real typed apparatus descriptions");
        check(captured.verify(world)==Check.VALID,"correct same-tick observation");
        check(captured.devices().get(0).centerMetres().x()==1.0,"block SI x translation");
        check(captured.devices().get(1).centerMetres().z()==2.0,"block SI z translation");
        check(captured.devices().get(1).indicatedAxisLab().z()==-1.0,"detector front faces source");
        check(captured.devices().get(0).indicatedAxisLab().z()==1.0,"laser emits toward SOUTH");
        check(captured.devices().get(1).state().binding()==Binding.BLOCKSTATE_ONLY,
                "meter does not have persistent BE identity");
        check(!captured.devices().get(0).authenticatedHardwarePose(),"not real calibrated hardware");
        Random rng=new Random(0xD22E2026L);
        // Constant true identity and all discrete state permutations: when
        // the setting changes, the recorded snapshot MUST become invalid.
        for(int i=0;i<25000;i++) {
            world.tick++;
            var optic=at(29_999_980+rng.nextInt(3),90,-29_999_980+rng.nextInt(3));
            int selector=rng.nextInt(4);
            world.devices.put(optic,new Observed(state(selector),null));
            var snap=LoadedApparatusSnapshot.capture(world,frame,List.of(optic));
            check(snap.verify(world)==Check.VALID,"same tick capture");
            check(!snap.devices().getFirst().authenticatedHardwarePose(),
                    "no fake encoder auth");
            var center=snap.devices().getFirst().centerMetres();
            check(center.x()==optic.x()-origin.x() && center.z()==optic.z()-origin.z(),
                    "integer-relative coordinates under distant origin");
            world.devices.put(optic,new Observed(state((selector+1)%4),null));
            check(snap.verify(world)==Check.BLOCKSTATE_CHANGED,
                    "actual optic state invalidates old snapshot");
            world.devices.put(optic,new Observed(state(selector),null));
            check(snap.verify(world)==Check.VALID,
                    "stateless exact replacement is NOT authenticated");
            world.tick++;
            check(snap.verify(world)==Check.TICK_CHANGED,
                    "never reuse snapshot in later tick");
            world.devices.remove(optic);
            // Avoid clobbering the fixed apparatus identities used below.
            if(!optic.equals(source)&&!optic.equals(sensor)&&!optic.equals(stage))continue;
            world.devices.put(optic,optic.equals(source) ?
                    new Observed(new State(Kind.LASER,Axis.SOUTH,-1,true,
                            LaserTuning.DEFAULT,Binding.BLOCK_ENTITY_INSTANCE),sourceBe) :
                    new Observed(new State(Kind.METER,Axis.NORTH,1,false,null,
                            Binding.BLOCKSTATE_ONLY),null));
        }
        // Independent restart/teleport/stale checks on the original snapshot.
        check(captured.verify(world)==Check.TICK_CHANGED,"original observation expires");
        world.tick=captured.gameTick();
        world.dim="minecraft:the_nether";
        check(captured.verify(world)==Check.DIMENSION_CHANGED,"teleport invalidates");
        world.dim="minecraft:overworld";
        world.onThread=false;
        check(captured.verify(world)==Check.OFF_THREAD,"no async world read");
        world.onThread=true;
        world.devices.put(source,new Observed(new State(Kind.LASER,Axis.SOUTH,-1,true,
                new LaserTuning(20,780,500,0),Binding.BLOCK_ENTITY_INSTANCE),new Object()));
        check(captured.verify(world)==Check.BLOCK_ENTITY_REPLACED,"actual BE identity differs");
        var other=new SimWorld();other.tick=captured.gameTick();
        check(captured.verify(other)==Check.SERVER_CHANGED,"server world identity differs");
        world.devices.put(source,new Observed(new State(Kind.LASER,Axis.SOUTH,-1,true,
                new LaserTuning(20,780,500,0),Binding.BLOCK_ENTITY_INSTANCE),sourceBe));
        world.devices.remove(sensor);
        check(captured.verify(world)==Check.CHUNK_UNLOADED,"cannot load unavailable chunk");
        rejects(()->LoadedApparatusSnapshot.capture(world,frame,List.of()),"zero devices");
        rejects(()->LoadedApparatusSnapshot.capture(world,frame,List.of(source,source)),"duplicate");
        rejects(()->new State(Kind.METER,Axis.NORTH,3,false,null,Binding.BLOCKSTATE_ONLY),
                "invalid meter preset");
        System.out.println("D22E LoadedWorldSmoke PASS "+assertions+"/"+assertions);
    }
}
