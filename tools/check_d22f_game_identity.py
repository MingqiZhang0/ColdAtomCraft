#!/usr/bin/env python3
"""D22F REAL BlockEntity source against minimal 26.3 API shape stubs (NOT a NeoForge build)."""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
NEO=ROOT/'neoforge-mod/src/main/java/dev/coldatomcraft/neoforge/preview'
CORE=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment/motion'
STUBS={
'net/minecraft/core/BlockPos.java':'''package net.minecraft.core;
public record BlockPos(int x,int y,int z) {}
''',
'net/minecraft/world/level/block/state/BlockState.java':'''package net.minecraft.world.level.block.state;
public final class BlockState {
 private final Object block;
 public BlockState(Object block){this.block=block;}
 public Object getBlock(){return block;}
}
''',
'net/minecraft/world/level/Level.java':'''package net.minecraft.world.level;
public class Level {}
''',
'net/minecraft/server/level/ServerLevel.java':'''package net.minecraft.server.level;
import java.util.HashMap;import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class ServerLevel extends Level {
 public final Map<BlockPos,BlockState> blocks=new HashMap<>();
 public final Map<BlockPos,BlockEntity> entities=new HashMap<>();
 public BlockEntity getBlockEntity(BlockPos p){return entities.get(p);}
 public BlockState getBlockState(BlockPos p){return blocks.get(p);}
}
''',
'net/minecraft/world/level/storage/ValueInput.java':'''package net.minecraft.world.level.storage;
public interface ValueInput {
 String getStringOr(String name,String fallback);
 long getLongOr(String name,long fallback);
 java.util.Optional<Long> getLong(String name);
}
''',
'net/minecraft/world/level/storage/ValueOutput.java':'''package net.minecraft.world.level.storage;
public interface ValueOutput {
 void putString(String name,String value);
 void putLong(String name,long value);
}
''',
'net/minecraft/world/level/block/entity/BlockEntity.java':'''package net.minecraft.world.level.block.entity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
public class BlockEntity {
 protected Level level;
 protected final BlockPos worldPosition;
 private BlockState state;
 private int dirty;
 public BlockEntity(Object type,BlockPos pos,BlockState state){worldPosition=pos;this.state=state;}
 public void setLevel(Level level){this.level=level;}
 public BlockState getBlockState(){return state;}
 public void setBlockState(BlockState state){this.state=state;}
 public int dirtyCount(){return dirty;}
 public void setChanged(){dirty++;}
 public void onLoad(){}
 protected void loadAdditional(ValueInput input){}
 protected void saveAdditional(ValueOutput output){}
}
''',
'dev/coldatomcraft/neoforge/preview/PreviewBlocks.java':'''package dev.coldatomcraft.neoforge.preview;
public final class PreviewBlocks {
 public record Holder(Object type){public Object get(){return type;}}
 public static final Holder APPARATUS_IDENTITY_BLOCK_ENTITY=new Holder(new Object());
}
''',
}
HARNESS='''package dev.coldatomcraft.neoforge.preview;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import dev.coldatomcraft.experiment.motion.PersistentApparatusIdentity;
public final class IdentityAdapterSmoke {
 private static int checks;
 private static void check(boolean yes,String msg){checks++;if(!yes)throw new AssertionError(msg);}
 private static final class Store implements ValueInput,ValueOutput {
  final Map<String,String> values=new HashMap<>();
  public String getStringOr(String n,String d){return values.getOrDefault(n,d);}
  public long getLongOr(String n,long d){return values.containsKey(n)?Long.parseLong(values.get(n)):d;}
  public java.util.Optional<Long> getLong(String n){return values.containsKey(n)
      ?java.util.Optional.of(Long.parseLong(values.get(n))):java.util.Optional.empty();}
  public void putString(String n,String v){values.put(n,v);}
  public void putLong(String n,long v){values.put(n,Long.toString(v));}
 }
 public static void main(String[] args){
  var block=new Object();var pos=new BlockPos(100,75,200);
  var a=new BlockState(block);var b=new BlockState(block);
  var world=new ServerLevel();world.blocks.put(pos,a);
  var first=new DiagnosticApparatusIdentityBlockEntity(pos,a);
  first.setLevel(world);world.entities.put(pos,first);first.onLoad();
  check(first.identityUsable(),"fresh identity usable");
  check(first.dirtyCount()>0,"placement marks dirty for storage");
  var original=first.identityToken();
  var save=new Store();first.saveAdditional(save);
  check(save.values.size()==2,"uuid and revision persisted");
  var reloaded=new DiagnosticApparatusIdentityBlockEntity(pos,a);
  reloaded.loadAdditional(save);reloaded.setLevel(world);world.entities.put(pos,reloaded);reloaded.onLoad();
  check(reloaded.identityToken().equals(original),"same persisted identity across BE recreation");
  var old=reloaded.identityToken();
  world.blocks.put(pos,b);
  reloaded.setBlockState(b);check(reloaded.identityToken().revision()==1,"state property updates revision");
  var once=reloaded.identityToken();
  reloaded.confirmControlledChange(a,b,old);
  check(reloaded.identityToken().equals(once),"double count prevented");
  world.blocks.put(pos,a);
  var prior=reloaded.identityToken();
  reloaded.confirmControlledChange(b,a,prior);
  check(reloaded.identityToken().revision()==2,"explicit controlled fallback increments once");
  var identical=reloaded.identityToken();
  reloaded.confirmControlledChange(a,a,identical);
  check(reloaded.identityToken().equals(identical),"same selector never increments");
  var bad=new Store();bad.putString("d22f_apparatus_uuid","BROKEN");bad.putLong("d22f_apparatus_revision",0);
  var corrupt=new DiagnosticApparatusIdentityBlockEntity(pos,a);corrupt.loadAdditional(bad);
  check(!corrupt.identityUsable(),"malformed storage quarantined");
  var missingRevision=new Store();missingRevision.putString("d22f_apparatus_uuid",original.uuid().toString());
  var incomplete=new DiagnosticApparatusIdentityBlockEntity(pos,a);incomplete.loadAdditional(missingRevision);
  check(!incomplete.identityUsable(),"saved ID with missing revision quarantined");
  var corruptedSaved=new Store();corrupt.saveAdditional(corruptedSaved);
  check("BROKEN".equals(corruptedSaved.getStringOr("d22f_apparatus_uuid","")),"bad storage preserved");
  var legacy=new DiagnosticApparatusIdentityBlockEntity(pos,a);legacy.loadAdditional(new Store());
  legacy.setLevel(world);world.entities.put(pos,legacy);legacy.onLoad();
  check(legacy.identityOrigin()==PersistentApparatusIdentity.Origin.LEGACY_MIGRATED,
        "legacy data rekeyed explicitly");
  check(legacy.dirtyCount()>0,"legacy migration marked for save");
  check(!legacy.identityToken().equals(original),"no old lineage invented");
  var replacement=new DiagnosticApparatusIdentityBlockEntity(pos,a);
  check(!replacement.identityToken().uuid().equals(original.uuid()),"destroy/re-place creates new ID");
  System.out.println("D22F IdentityAdapterSmoke PASS "+checks+"/"+checks);
 }
}
'''


def main():
 with tempfile.TemporaryDirectory(prefix='cac-d22f-game-') as directory:
  tmp=Path(directory);files=[]
  for relative,content in STUBS.items():
   p=tmp/relative;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf8');files.append(p)
  harness=tmp/'dev/coldatomcraft/neoforge/preview/IdentityAdapterSmoke.java'
  harness.parent.mkdir(parents=True,exist_ok=True);harness.write_text(HARNESS,encoding='utf8')
  files.extend([CORE/'PersistentApparatusIdentity.java',
                NEO/'DiagnosticApparatusIdentityBlockEntity.java',harness])
  classes=tmp/'classes'
  subprocess.run(['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror',
                  '-d',str(classes),*map(str,files)],cwd=ROOT,check=True)
  subprocess.run(['java','-cp',str(classes),
                  'dev.coldatomcraft.neoforge.preview.IdentityAdapterSmoke'],cwd=ROOT,check=True)


if __name__=='__main__': main()
