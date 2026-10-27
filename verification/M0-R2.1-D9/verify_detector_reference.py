#!/usr/bin/env python3
"""Independent pure-stdlib Decimal(80) reference for offset-Gaussian apertures.

Java uses transformed angular integration and Gauss-Legendre nodes.
This independently computes the noncentral-chi-square CDF using a
Poisson/Gamma mixture (2 degrees of freedom). Not JUnit/NeoForge.
"""

from decimal import Decimal, localcontext
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
D = Decimal


def independent_fraction(a: Decimal, d: Decimal) -> Decimal:
    if a < 0 or d < 0:
        raise ValueError("nonnegative normalized dimensions required")
    # Noncentrality lambda=4*d^2 and threshold x=4*a^2.
    # s=lambda/2=2*d^2, t=x/2=2*a^2.
    s, t = 2*d*d, 2*a*a
    poisson_s = (-s).exp()
    poisson_t = (-t).exp()
    cumulative_t = poisson_t
    result = D(0)
    # Integral = sum_k Poisson(s,k) * GammaCDF(k+1,t),
    # where GammaCDF(k+1,t) = 1 - PoissonCDF(t,k).
    max_iterations = max(400, int(max(s,t))+500)
    for k in range(max_iterations):
        tail_t = 1 - cumulative_t
        if tail_t < 0 and abs(tail_t) < D("1e-72"):
            tail_t = D(0)
        result += poisson_s * tail_t
        kp1 = k+1
        poisson_s *= s / kp1
        poisson_t *= t / kp1
        cumulative_t += poisson_t
        if kp1 > s+100 and poisson_s < D("1e-75"):
            break
    else:
        raise AssertionError("Poisson/Gamma decimal series did not converge")
    return result


def main():
    sources=sorted(str(x) for x in (ROOT/'physics-core/src/main/java').rglob('*.java'))
    probe=ROOT/'verification/M0-R2.1-D9/DetectorReferenceProbe.java'
    with tempfile.TemporaryDirectory(prefix='cac-d9-optical-power-') as output:
        subprocess.run(['javac','--release','21','-encoding','UTF-8',
                        '-Xlint:all','-Werror','-d',output,*sources,str(probe)],check=True)
        results=subprocess.check_output(['java','-cp',output,
                       'dev.coldatomcraft.verification.DetectorReferenceProbe'],text=True)
    comparisons=0
    with localcontext() as ctx:
        ctx.prec=80
        for line in results.splitlines():
            fields=line.split('|')
            if len(fields)!=4 or fields[0]!='CASE':
                raise AssertionError('malformed Java detector oracle row: '+line)
            a,d,observed = map(D,fields[1:])
            target=independent_fraction(a,d)
            error=abs(observed-target)
            if error>D('3e-11'):
                raise AssertionError(f'offset disk a/w={a}, d/w={d}: Java={observed}, '
                                     f'Decimal80={target}, error={error}')
            comparisons+=1
    assert comparisons == 26, comparisons
    print(f'PASS: {comparisons}/{comparisons} independent Decimal80 offset Gaussian '
          'aperture power checks (noncentral chi-square / Poisson-Gamma oracle); NOT JUnit')


if __name__=='__main__':
    main()
