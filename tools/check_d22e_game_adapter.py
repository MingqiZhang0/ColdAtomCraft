#!/usr/bin/env python3
"""Compile and exercise D22E NeoForge glue against STRICT SHAPE STUBS.

This is NOT a real NeoForge/JDK25 compile. Run after check_d22e_live_geometry.py.
Actual Minecraft API bindings still require the GitHub Actions Gradle build.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PHY = ROOT / 'physics-core/src/main/java/dev/coldatomcraft/physics'
EXP = ROOT / 'experiment-core/src/main/java/dev/coldatomcraft/experiment'
NEO = ROOT / 'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview'

STUBS = {
'net/minecraft/core/BlockPos.java': '''package net.minecraft.core;
public record BlockPos(int x,int y,int z) {
 public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
 public BlockPos above(){return new BlockPos(x,y+1,z);}
}
''',
'net/minecraft/world/level/block/EntityBlock.java': '''package net.minecraft.world.level.block;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
public interface EntityBlock {BlockEntity newBlockEntity(BlockPos pos,BlockState state);}
''',
'net/minecraft/core/Direction.java': '''package net.minecraft.core;
public enum Direction {NORTH,SOUTH,EAST,WEST,UP,DOWN}
''',
'net/minecraft/server/MinecraftServer.java': '''package net.minecraft.server;
public final class MinecraftServer {
 public boolean onThread=true;
 public boolean isSameThread(){return onThread;}
}
''',
'net/minecraft/world/level/block/state/BlockBehaviour.java': '''package net.minecraft.world.level.block.state;
public abstract class BlockBehaviour {public static final class Properties {}}
''',
'net/minecraft/world/level/block/state/Property.java': '''package net.minecraft.world.level.block.state;
public record Property<T>(String key) {}
''',
'net/minecraft/world/level/block/Block.java': '''package net.minecraft.world.level.block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public class Block {
 public Block(BlockBehaviour.Properties props){}
 protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                             Player player, BlockHitResult hit){return InteractionResult.SUCCESS;}
}
''',
'net/minecraft/world/level/block/state/BlockState.java': '''package net.minecraft.world.level.block.state;
import java.util.HashMap;
import java.util.Map;
public final class BlockState {
 private final Object block; private final Map<Property<?>,Object> properties=new HashMap<>();
 public BlockState(Object b){block=b;}
 public Object getBlock(){return block;}
 public boolean is(Object b){return block==b;}
 public <T> BlockState with(Property<T> property,T value){properties.put(property,value);return this;}
 @SuppressWarnings("unchecked")
 public <T> T getValue(Property<T> property){return (T)properties.get(property);}
}
''',
'net/minecraft/world/level/block/entity/BlockEntity.java': '''package net.minecraft.world.level.block.entity;
public class BlockEntity {
 public BlockEntity(){}
 public boolean removed;
 public boolean isRemoved(){return removed;}
}
''',
'net/minecraft/world/level/Level.java': '''package net.minecraft.world.level;
public abstract class Level {}
''',
'net/minecraft/server/level/ServerLevel.java': '''package net.minecraft.server.level;
import java.util.HashMap;import java.util.HashSet;import java.util.Map;import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public class ServerLevel extends Level {
 public static final class Key {public final String value;public Key(String x){value=x;}
                             public String identifier(){return value;}}
 public final MinecraftServer minecraftServer=new MinecraftServer();
 public final Map<BlockPos,BlockState> states=new HashMap<>();
 public final Map<BlockPos,BlockEntity> entities=new HashMap<>();
 public final Set<BlockPos> unloaded=new HashSet<>();
 public String dim="minecraft:overworld";public long tick=100;
 public int stateReads=0;public int entityReads=0;
 public Key dimension(){return new Key(dim);}
 public long getGameTime(){return tick;}
 public MinecraftServer getServer(){return minecraftServer;}
 public boolean hasChunkAt(BlockPos p){return !unloaded.contains(p);}
 public BlockState getBlockState(BlockPos p){
  if(!hasChunkAt(p))throw new AssertionError("UNLOADED world read");
  stateReads++;return states.getOrDefault(p,new BlockState(new Object()));
 }
 public BlockEntity getBlockEntity(BlockPos p){
  if(!hasChunkAt(p))throw new AssertionError("UNLOADED entity read");
  entityReads++;return entities.get(p);
 }
}
''',
'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;
public record Component(String text) {
 public static Component literal(String s){return new Component(s);}
}
''',
'net/minecraft/world/InteractionResult.java': '''package net.minecraft.world;
public enum InteractionResult {SUCCESS}
''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;
import net.minecraft.network.chat.Component;
public final class Player {
 public Component last;
 public void sendSystemMessage(Component c){last=c;}
}
''',
'net/minecraft/world/phys/BlockHitResult.java': '''package net.minecraft.world.phys;
public final class BlockHitResult {}
''',
'dev/coldatomcraft/neoforge/preview/PreviewBlocks.java': '''package dev.coldatomcraft.neoforge.preview;
public final class PreviewBlocks {
 public record Holder(Object value){public Object get(){return value;}}
 public static final Holder LASER=new Holder(new Object()),
  AUTO_PRESET_STAGE=new Holder(new Object()),METER=new Holder(new Object()),
  OPTICAL_TABLE=new Holder(new Object()),APPARATUS_IDENTITY_BLOCK_ENTITY=new Holder(new Object()),
  LINEAR_STAGE=new Holder(new Object()),
  ROTARY_STAGE=new Holder(new Object());
}
''',
'dev/coldatomcraft/neoforge/preview/DiagnosticLaserBlock.java': '''package dev.coldatomcraft.neoforge.preview;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.Property;
public final class DiagnosticLaserBlock {
 public static final Property<Direction> FACING=new Property<>("facing");
 public static final Property<Boolean> ENABLED=new Property<>("enabled");
}
''',
'dev/coldatomcraft/neoforge/preview/DiagnosticPowerMeterBlock.java': '''package dev.coldatomcraft.neoforge.preview;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.Property;
public final class DiagnosticPowerMeterBlock {
 public static final Property<Direction> FACING=new Property<>("facing");
 public static final Property<Integer> APERTURE=new Property<>("aperture");
}
''',
'dev/coldatomcraft/neoforge/preview/DiagnosticStageBlock.java': '''package dev.coldatomcraft.neoforge.preview;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.Property;
public final class DiagnosticStageBlock {
 public static final Property<Direction> FACING=new Property<>("facing");
}
''',
'dev/coldatomcraft/neoforge/preview/OpticPortPreset.java': '''package dev.coldatomcraft.neoforge.preview;
public record OpticPortPreset(Direction3 incoming,int selector) {
 public enum Direction3 {NORTH,SOUTH,EAST,WEST,UP,DOWN}
}
''',
'dev/coldatomcraft/neoforge/preview/DiagnosticOpticBlock.java': '''package dev.coldatomcraft.neoforge.preview;
import net.minecraft.world.level.block.state.Property;
public final class DiagnosticOpticBlock {
 public enum Kind {MIRROR,SPLITTER,LENS,HALF_WAVE_PLATE}
 public static final Property<OpticPortPreset> PORT=new Property<>("port");
 private final Kind kind;
 public DiagnosticOpticBlock(Kind k){kind=k;}
 public Kind kind(){return kind;}
}
''',
'dev/coldatomcraft/neoforge/preview/DiagnosticApparatusIdentityBlockEntity.java': '''package dev.coldatomcraft.neoforge.preview;
import dev.coldatomcraft.experiment.motion.PersistentApparatusIdentity;
import net.minecraft.core.BlockPos;import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class DiagnosticApparatusIdentityBlockEntity extends BlockEntity {
 private PersistentApparatusIdentity identity=PersistentApparatusIdentity.placed();
 public DiagnosticApparatusIdentityBlockEntity(BlockPos p, BlockState s){}
 public boolean identityUsable(){return identity.usable();}
 public PersistentApparatusIdentity.Token identityToken(){return identity.token();}
}
''',
'dev/coldatomcraft/neoforge/preview/LaserSettingsBlockEntity.java': '''package dev.coldatomcraft.neoforge.preview;
import dev.coldatomcraft.experiment.LaserTuning;
import net.minecraft.world.level.block.entity.BlockEntity;
public final class LaserSettingsBlockEntity extends BlockEntity {
 private LaserTuning data;private boolean valid;
 public LaserSettingsBlockEntity(LaserTuning x,boolean v){data=x;valid=v;}
 public boolean configurationValid(){return valid;}
 public LaserTuning tuning(){return data;}
}
''',
'dev/coldatomcraft/neoforge/preview/AutoPresetStageBlockEntity.java': '''package dev.coldatomcraft.neoforge.preview;
import net.minecraft.world.level.block.entity.BlockEntity;
public final class AutoPresetStageBlockEntity extends BlockEntity {}
''',
}

SMOKE = '''package dev.coldatomcraft.neoforge.preview;
import java.util.List;
import dev.coldatomcraft.experiment.LaserTuning;
import dev.coldatomcraft.experiment.motion.LoadedApparatusSnapshot.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
public final class AdapterSmoke {
 private static int tests;
 private static void check(boolean yes,String msg){tests++;if(!yes)throw new AssertionError(msg);}
 private static void reject(Runnable action,String msg){boolean rejected=false;
  try{action.run();}catch(IllegalStateException | IllegalArgumentException ex){rejected=true;}
  check(rejected,msg);
 }
 public static void main(String[] args){
  var world=new ServerLevel();
  var table=new BlockPos(29_999_995,80,-29_999_995);
  var optic=table.above();
  var laser=new BlockPos(29_999_996,81,-29_999_997);
  var meter=new BlockPos(29_999_997,81,-29_999_992);
  var stage=new BlockPos(29_999_998,81,-29_999_994);
  world.states.put(table,new BlockState(PreviewBlocks.OPTICAL_TABLE.get()));
  world.states.put(optic,new BlockState(new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.MIRROR))
      .with(DiagnosticOpticBlock.PORT,new OpticPortPreset(OpticPortPreset.Direction3.SOUTH,2)));
  world.states.put(laser,new BlockState(PreviewBlocks.LASER.get())
      .with(DiagnosticLaserBlock.FACING,Direction.SOUTH).with(DiagnosticLaserBlock.ENABLED,true));
  Object laserBe=new LaserSettingsBlockEntity(LaserTuning.DEFAULT,true);
  world.entities.put(laser,(LaserSettingsBlockEntity)laserBe);
  world.states.put(meter,new BlockState(PreviewBlocks.METER.get())
      .with(DiagnosticPowerMeterBlock.FACING,Direction.NORTH)
      .with(DiagnosticPowerMeterBlock.APERTURE,1));
  world.entities.put(table,new DiagnosticApparatusIdentityBlockEntity(table,world.states.get(table)));
  world.entities.put(optic,new DiagnosticApparatusIdentityBlockEntity(optic,world.states.get(optic)));
  world.entities.put(meter,new DiagnosticApparatusIdentityBlockEntity(meter,world.states.get(meter)));
  world.states.put(stage,new BlockState(PreviewBlocks.AUTO_PRESET_STAGE.get()));
  var stageBe=new AutoPresetStageBlockEntity();world.entities.put(stage,stageBe);
  var rig=LoadedLabGeometryWorld.captureNominal(world,table,List.of(table,optic,laser,meter,stage));
  check(rig.devices().size()==5,"typed apparatus count");
  check(rig.devices().get(1).state().kind()==Kind.MIRROR,"actual optic kind");
  check(rig.devices().get(1).state().setting()==2,"actual selector");
  check(rig.devices().get(1).state().axisMeaning().equals("INCIDENT_TRAVEL"),"no inverted front");
  check(rig.devices().get(2).state().binding()==Binding.BLOCK_ENTITY_INSTANCE,"laser instance bound");
  check(rig.devices().get(3).state().binding()==Binding.PERSISTENT_BLOCK_ENTITY,"meter durable BE token");
  check(rig.persistentReferences().size()==3,"table optic meter have saved tokens");
  check(rig.devices().get(1).centerMetres().y()==1.0,"local meter-scale nominal offset");
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.VALID,"valid now");
  var player=new Player();
  var inspector=new DiagnosticOpticalTableBlock(new BlockBehaviour.Properties());
  inspector.useWithoutItem(world.states.get(table),world,table,player,new BlockHitResult());
  check(player.last.text().contains("READ_ONLY_SERVER_SNAPSHOT"),"visible inspector message");
  check(player.last.text().contains("INCIDENT_TRAVEL=SOUTH"),"correct incoming optics convention");
  world.states.put(optic,new BlockState(new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.MIRROR))
      .with(DiagnosticOpticBlock.PORT,new OpticPortPreset(OpticPortPreset.Direction3.NORTH,2)));
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.BLOCKSTATE_CHANGED,"optical rotation invalidates");
  world.states.put(optic,new BlockState(new DiagnosticOpticBlock(DiagnosticOpticBlock.Kind.MIRROR))
      .with(DiagnosticOpticBlock.PORT,new OpticPortPreset(OpticPortPreset.Direction3.SOUTH,2)));
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.VALID,"same identity restored in stub");
  var oldOpticIdentity=world.entities.get(optic);
  world.entities.put(optic,new DiagnosticApparatusIdentityBlockEntity(optic,world.states.get(optic)));
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.PERSISTENT_ID_CHANGED,
         "identical replacement must be rejected");
  world.entities.put(optic,oldOpticIdentity);
  world.entities.put(laser,new LaserSettingsBlockEntity(LaserTuning.DEFAULT,true));
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.BLOCK_ENTITY_REPLACED,"laser BE replaced");
  world.entities.put(laser,(LaserSettingsBlockEntity)laserBe);
  world.unloaded.add(meter);
  int priorReads=world.stateReads;
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.CHUNK_UNLOADED,"no forced chunk load");
  check(world.stateReads==priorReads+3,"stops before unloaded meter read");
  reject(()->LoadedLabGeometryWorld.captureNominal(world,table,List.of(table,meter)),"unloaded capture rejected");
  world.unloaded.clear();
  world.tick++;
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.TICK_CHANGED,"expires next tick");
  world.tick--;
  world.dim="minecraft:the_nether";
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.DIMENSION_CHANGED,"dimension invalidates");
  world.dim="minecraft:overworld";
  world.minecraftServer.onThread=false;
  check(LoadedLabGeometryWorld.verify(world,rig)==Check.OFF_THREAD,"off thread fails");
  reject(()->LoadedLabGeometryWorld.captureNominal(world,table,List.of(table,optic)),"offthread capture rejected");
  world.minecraftServer.onThread=true;
  world.entities.put(laser,new LaserSettingsBlockEntity(LaserTuning.DEFAULT,false));
  reject(()->LoadedLabGeometryWorld.captureNominal(world,table,List.of(laser)),"invalid laser config rejected");
  world.entities.put(laser,(LaserSettingsBlockEntity)laserBe);
  ((LaserSettingsBlockEntity)laserBe).removed=true;
  reject(()->LoadedLabGeometryWorld.captureNominal(world,table,List.of(laser)),
         "removed block entity rejected");
  ((LaserSettingsBlockEntity)laserBe).removed=false;
  world.states.remove(optic);
  inspector.useWithoutItem(world.states.get(table),world,table,player,new BlockHitResult());
  check(player.last.text().contains("No supported loaded apparatus"),"inspector ignores absent optic");
  System.out.println("D22E AdapterSmoke PASS "+tests+"/"+tests);
 }
}
'''

def main():
    with tempfile.TemporaryDirectory(prefix='cac-d22e-adapter-') as folder:
        folder = Path(folder)
        stubs=[]
        for rel, source in STUBS.items():
            path = folder/rel
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_text(source,encoding='utf-8')
            stubs.append(path)
        harness = folder/'dev/coldatomcraft/neoforge/preview/AdapterSmoke.java'
        harness.parent.mkdir(parents=True,exist_ok=True)
        harness.write_text(SMOKE,encoding='utf-8')
        files = [PHY / 'Vec3.java', *sorted((PHY / 'optics').glob('*.java')),
                 EXP / 'LaserTuning.java', EXP / 'motion/LaboratoryMountFrame.java',
                 EXP / 'motion/LoadedApparatusSnapshot.java',
                 EXP / 'motion/PersistentApparatusIdentity.java',
                 NEO / 'LoadedLabGeometryWorld.java',
                 NEO / 'DiagnosticOpticalTableBlock.java', *stubs, harness]
        classes = folder/'classes'
        subprocess.run(['javac','--release','21','-Xlint:all','-Werror',
                        '-encoding','UTF-8','-d',str(classes),
                        *(str(path) for path in files)],cwd=ROOT,check=True)
        subprocess.run(['java','-cp',str(classes),
                        'dev.coldatomcraft.neoforge.preview.AdapterSmoke'],cwd=ROOT,check=True)

if __name__=='__main__':
    main()
