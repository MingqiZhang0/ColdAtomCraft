#!/usr/bin/env python3
"""Compile/run D22E formal JUnit SOURCE through a tiny offline test API shim.

NOT JUnit5 evidence. The real Gradle JUnit run remains a separate CI gate.
"""
from pathlib import Path
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[1]
PHY=ROOT/'physics-core/src/main/java/dev/coldatomcraft/physics'
EXP=ROOT/'experiment-core/src/main/java/dev/coldatomcraft/experiment'
TEST=ROOT/'experiment-core/src/test/java/dev/coldatomcraft/experiment/motion/LoadedApparatusSnapshotTest.java'
SHIMS={
 'org/junit/jupiter/api/Test.java': '''package org.junit.jupiter.api;
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
public @interface Test {}
''',
 'org/junit/jupiter/api/function/Executable.java': '''package org.junit.jupiter.api.function;
@FunctionalInterface public interface Executable {void execute() throws Throwable;}
''',
 'org/junit/jupiter/api/Assertions.java': '''package org.junit.jupiter.api;
import org.junit.jupiter.api.function.Executable;
public final class Assertions {
 private Assertions(){}
 public static void assertTrue(boolean x){if(!x)throw new AssertionError("assertTrue");}
 public static void assertFalse(boolean x){if(x)throw new AssertionError("assertFalse");}
 public static void assertEquals(Object a,Object b){if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
 public static void assertEquals(double a,double b,double delta){if(!(Double.isFinite(a)&&Double.isFinite(b)&&Math.abs(a-b)<=delta))throw new AssertionError(a+" != "+b);}
 public static <T extends Throwable> T assertThrows(Class<T> expected, Executable action){
  try{action.execute();}catch(Throwable t){if(expected.isInstance(t))return expected.cast(t);throw new AssertionError("Unexpected "+t,t);}
  throw new AssertionError("Expected "+expected.getName());
 }
}
''',
 'dev/coldatomcraft/experiment/motion/OfflineTestRunner.java': '''package dev.coldatomcraft.experiment.motion;
public final class OfflineTestRunner {
 public static void main(String[] args) throws Exception {
  Object suite=new LoadedApparatusSnapshotTest();int count=0;
  for(var method:LoadedApparatusSnapshotTest.class.getDeclaredMethods()) {
   if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)){
    method.setAccessible(true);try{method.invoke(suite);}catch(java.lang.reflect.InvocationTargetException e){throw new AssertionError(method.getName(),e.getCause());}
    count++;
   }
  }
  if(count!=18)throw new AssertionError("Missing formal JUnit tests: "+count);
  System.out.println("D22E JUnit-source offline compatibility PASS "+count+"/"+count);
 }
}
''',
}

def main():
 with tempfile.TemporaryDirectory(prefix='cac-d22e-junit-') as folder:
  tmp=Path(folder); shims=[]
  for rel,code in SHIMS.items():
   p=tmp/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(code,encoding='utf8');shims.append(p)
  files=[PHY/'Vec3.java',*sorted((PHY/'optics').glob('*.java')),
         EXP/'LaserTuning.java',EXP/'motion/LaboratoryMountFrame.java',
         EXP/'motion/LoadedApparatusSnapshot.java',
         EXP/'motion/PersistentApparatusIdentity.java',TEST,*shims]
  classes=tmp/'classes'
  subprocess.run(['javac','--release','21','-encoding','UTF-8','-Xlint:all','-Werror',
                  '-d',str(classes),*(str(x) for x in files)],cwd=ROOT,check=True)
  subprocess.run(['java','-cp',str(classes),
                  'dev.coldatomcraft.experiment.motion.OfflineTestRunner'],cwd=ROOT,check=True)

if __name__=='__main__':main()
