#!/usr/bin/env python3
"""Independent 70-digit Decimal oracle for Jones matrices and coherent Gaussian fields.

No numpy/mpmath dependency. Angular/phase expectations come from closed-form
Malus, retardance, Gaussian/Gouy and Maxwell plane-wave identities; the Java
implementation is independently compiled and sampled. NOT formal JUnit.
"""
from __future__ import annotations
from decimal import Decimal as D, localcontext
from pathlib import Path
import math
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PI = D('3.14159265358979323846264338327950288419716939937510582097494459230781640628620899')
LAMBDA, W0, P = D('780e-9'), D('0.0005'), D('0.02')

def cosine(theta: D) -> D:
    """Power series at 70 decimal digits on small arguments; values here |theta| <= pi/2."""
    value = D(1)
    summand = D(1)
    threshold = D('1e-76')
    for n in range(1, 95):
        summand *= -theta * theta / D((2*n-1)*(2*n))
        value += summand
        if abs(summand) < threshold:
            return value
    raise ArithmeticError('cosine series did not converge')

def sine(theta: D) -> D:
    summand, value = theta, theta
    threshold = D('1e-76')
    for n in range(1, 95):
        summand *= -theta*theta/D((2*n)*(2*n+1))
        value += summand
        if abs(summand) < threshold:
            return value
    raise ArithmeticError('sine series did not converge')

def arctangent_small(x: D) -> D:
    # Our use has x ~ 1e-7, thus just a handful of terms suffice.
    value = D(0)
    power = x
    threshold = D('1e-76')
    for n in range(150):
        term = power / D(2*n+1)
        value += term if n % 2 == 0 else -term
        if abs(term) < threshold:
            return value
        power *= x*x
    raise ArithmeticError('atan series did not converge')

def expected() -> dict[str, list[D]]:
    I0 = D(2)*P/(PI*W0*W0)
    malus = cosine(PI/D(7) - PI/D(9)) ** 2
    c = D('299792458')
    results = {
        'singleEnergy': [I0/(D(2)*c),I0/(D(2)*c),I0/c],
        'malus': [malus],
        'qwp': [D(1)/D(2).sqrt(), D(1)/D(2).sqrt(), D(1)],
        'hwp': [D(1)/D(2).sqrt(), D(1)/D(2).sqrt(), D(1)],
        'commonphase': [D(1), D(0), D(1)],
        'coprop': [D(4)*I0, D(4)*I0],
        'quadrature': [D(2)*I0],
        'orthogonal': [D(2)*I0],
        'cross': [D(4)*I0, D(2)*I0, D(0), D(2)*I0],
    }
    zR = PI*W0*W0/LAMBDA
    for i, z in enumerate((D(0), LAMBDA/D(8), LAMBDA/D(4))):
        ratio = z/zR
        I_z = I0/(D(1)+ratio*ratio)
        psi = arctangent_small(ratio)
        carrier = PI*D(i)/D(4) if i else D(0)  # i=1: pi/4, i=2: pi/2
        # 2 counterpropagating phasors with opposite Gouy phase.
        electric = D(4)*I_z*cosine(carrier-psi)**2
        magnetic = D(4)*I_z*sine(carrier-psi)**2
        results[f'standing{i}'] = [electric, D(0)]
        results[f'standingEnergy{i}'] = [electric/(D(2)*c),
                                           magnetic/(D(2)*c), D(2)*I_z/c]
    amplitude = (I0 * (-D(2)/D(9)).exp()).sqrt()
    results['waistPhasor'] = [amplitude*cosine(D('0.75')),
                               amplitude*sine(D('0.75')),
                               amplitude*amplitude]
    return results

def close(actual: float, oracle: D, key: str, relative: float = 5e-12) -> None:
    if not math.isfinite(actual):
        raise AssertionError(f'{key}: nonfinite {actual}')
    reference = float(oracle)
    # True zeros of the interference/Poynting results have numerical noise.
    atol = 3e-11 if oracle == 0 else max(2e-14, abs(reference)*relative)
    if not math.isclose(actual, reference, rel_tol=relative, abs_tol=atol):
        raise AssertionError(f'{key}: Java {actual:.17g}, Decimal70 {oracle}')

def main() -> None:
    java_root = ROOT/'physics-core'/'src'/'main'/'java'
    sources = sorted(java_root.rglob('*.java'))
    probe = Path(__file__).with_name('CoherentReferenceProbe.java')
    with tempfile.TemporaryDirectory(prefix='cac-d6-decimal-oracle-') as temp:
        classes = Path(temp)/'classes'
        classes.mkdir()
        subprocess.run(['javac','-encoding','UTF-8','-d',str(classes),
                        *map(str,sources),str(probe)],check=True)
        r = subprocess.run(['java','-cp',str(classes),'CoherentReferenceProbe'],
                           check=True,capture_output=True,text=True)
    actual = {}
    for line in r.stdout.splitlines():
        fields=line.strip().split(',')
        if not fields or len(fields) < 2 or fields[0] in actual:
            raise AssertionError(f'invalid probe line: {line}')
        actual[fields[0]]=[float(x) for x in fields[1:]]
    with localcontext() as context:
        context.prec=70
        checks=expected()
        if actual.keys()!=checks.keys():
            raise AssertionError(f'case mismatch actual={set(actual)} expected={set(checks)}')
        verified=0
        for key, numbers in checks.items():
            if len(actual[key])!=len(numbers):
                raise AssertionError(f'field count mismatch: {key}')
            for i,(observed,reference) in enumerate(zip(actual[key],numbers)):
                close(observed,reference,f'{key}[{i}]')
                verified+=1
    print(f'PASS: {verified} independent Decimal(70-digit) Jones/coherent Gaussian scalar comparisons')
    print('Standard physical reference only; no formal JUnit, NeoForge or Owner model gate')

if __name__=='__main__':
    main()
