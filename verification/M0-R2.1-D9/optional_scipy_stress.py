#!/usr/bin/env python3
"""OPTIONAL secondary oracle requiring scipy (not a project dependency).

Compares 1500 deterministic aperture offsets against scipy.stats.ncx2.cdf.
For the mandatory dependency-free 80-digit independent oracle, use
verify_detector_reference.py instead.
"""
from pathlib import Path
import random
import subprocess
import tempfile

try:
    from scipy.stats import ncx2
except ImportError as exc:
    raise SystemExit('NOT RUN: optional scipy missing') from exc

ROOT=Path(__file__).resolve().parents[2]
BASE=Path(__file__).parent
random.seed(20261027)
pairs=[]
for _ in range(1500):
    a=10**random.uniform(-2,1.7)
    d=(10**random.uniform(-2,1.7)) if random.random()<0.5 else a*(1+random.uniform(-1e-3,1e-3))
    pairs.append((a,d))

with tempfile.TemporaryDirectory(prefix='cac-d9-scipy-') as tmp:
    src=Path(tmp)/'DetectorSweep.java'
    src.write_text('''import dev.coldatomcraft.physics.optics.GaussianCircularAperture;
import java.util.Scanner;
public final class DetectorSweep {
 public static void main(String[] args) {
  try(var input=new Scanner(System.in)) {
   input.useLocale(java.util.Locale.ROOT);
   while (input.hasNextDouble()) {
    double a=input.nextDouble(), d=input.nextDouble();
    System.out.printf(java.util.Locale.ROOT,"%.17g%n",GaussianCircularAperture.capturedFraction(1,a,d));
   }
  }
 }
}''',encoding='utf-8')
    sources=sorted(str(x) for x in (ROOT/'physics-core/src/main/java').rglob('*.java'))
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,*sources,str(src)],check=True)
    output=subprocess.run(['java','-cp',tmp,'DetectorSweep'],
           input=''.join(f'{a:.17g} {d:.17g}\n' for a,d in pairs),
           capture_output=True,text=True,check=True).stdout
    vals=[float(x) for x in output.splitlines()]
    assert len(vals)==len(pairs)
    worst=(0,None)
    for (a,d),fraction in zip(pairs,vals):
        reference=ncx2.cdf(4*a*a,2,4*d*d)
        error=abs(reference-fraction)
        if error>worst[0]:worst=(error,(a,d,reference,fraction))
    print(f'OPTIONAL SciPy secondary stress: {len(pairs)} pairs; maximum abs error {worst[0]:.6g}; case {worst[1]}')
    assert worst[0]<3e-11
