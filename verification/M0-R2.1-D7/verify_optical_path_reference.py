#!/usr/bin/env python3
"""Independent decimal(80 digits) phase/ABCD/Jones/two-port checks for D7.

No third-party Python dependency, and not a formal JUnit or NeoForge test.
Python reference uses high precision complex pairs and analytic formulae,
not Java's floating point optical-path implementation.
"""
from __future__ import annotations

from decimal import Decimal as D, localcontext, ROUND_HALF_EVEN
from pathlib import Path
import math
import subprocess
import tempfile

ROOT=Path(__file__).resolve().parents[2]
PI=D('3.14159265358979323846264338327950288419716939937510582097494459230781640628620899')
LAMBDA=D.from_float(780e-9)
W0=D.from_float(500e-6)
POWER=D.from_float(0.02)


def reduce(theta: D) -> D:
    whole=(theta/(D(2)*PI)).to_integral_value(rounding=ROUND_HALF_EVEN)
    return theta-whole*D(2)*PI


def sine(theta: D) -> D:
    theta=reduce(theta)
    term=theta
    value=theta
    for n in range(1,160):
        term *= -(theta*theta)/D((2*n)*(2*n+1))
        value += term
        if abs(term)<D('1e-76'):
            return value
    raise ArithmeticError('sine did not converge')


def cosine(theta: D) -> D:
    theta=reduce(theta)
    term=D(1)
    value=term
    for n in range(1,160):
        term *= -(theta*theta)/D((2*n-1)*(2*n))
        value += term
        if abs(term)<D('1e-76'):
            return value
    raise ArithmeticError('cosine did not converge')


def atan_small(theta: D) -> D:
    assert abs(theta)<D('0.3')
    term=theta
    value=D(0)
    for i in range(220):
        part=term/D(2*i+1)
        value += part if i%2==0 else -part
        if abs(part)<D('1e-76'):
            return value
        term *= theta*theta
    raise ArithmeticError('atan did not converge')


def modal_phase(distance: D, zr: D) -> D:
    # Equivalent to the phase of q*_in/q*_out at the waist.
    return reduce(D(2)*PI*distance/LAMBDA-atan_small(distance/zr))


def cadd(a:tuple[D,D],b:tuple[D,D])->tuple[D,D]:
    return (a[0]+b[0],a[1]+b[1])


def cmul(a:tuple[D,D],b:tuple[D,D])->tuple[D,D]:
    return (a[0]*b[0]-a[1]*b[1],a[0]*b[1]+a[1]*b[0])


def phase(theta:D)->tuple[D,D]:
    return (cosine(theta),sine(theta))


def qlens(q:tuple[D,D],f:D)->tuple[D,D]:
    # Independent q/(1-q/f) complex division.
    x,y=q
    p=D(1)-x/f
    t=-y/f
    denominator=p*p+t*t
    return ((x*p+y*t)/denominator,(y*p-x*t)/denominator)


def beam_intensity(q:tuple[D,D],r:D,power:D)->D:
    x,y=q
    w2=(LAMBDA/PI)*(x*x+y*y)/y
    return D(2)*power/(PI*w2)*(-D(2)*r*r/w2).exp()


def expected()->dict[str,list[D]]:
    zr=PI*W0*W0/LAMBDA
    amplitude=POWER.sqrt()
    length=D.from_float(0.125)
    phase_free=modal_phase(length,zr)
    amp_free=(amplitude*cosine(phase_free),amplitude*sine(phase_free))
    qfree=(length,zr)

    lens_length=D.from_float(0.15)
    q_after_lens=qlens((lens_length,zr),D.from_float(0.2))
    lens_phase=modal_phase(lens_length,zr)
    lens_amp=(amplitude*cosine(lens_phase),amplitude*sine(lens_phase))

    t=D.from_float(0.3)
    r=D(1)-t
    split=(POWER*t,POWER*r,amplitude*t.sqrt(),amplitude*r.sqrt(),POWER)

    half=(D(1)/D(2)).sqrt()
    arm0=(amplitude*half,D(0))
    arm1=(D(0),amplitude*half)
    # Same free-space phase in both arms; second arm additionally +pi/3.
    path_common=phase(phase_free)
    arm0=cmul(arm0,path_common)
    arm1=cmul(arm1,cmul(path_common,phase(PI/D(3))))
    out0=cadd((arm0[0]*half,arm0[1]*half),(-arm1[1]*half,arm1[0]*half))
    out1=cadd((-arm0[1]*half,arm0[0]*half),(arm1[0]*half,arm1[1]*half))

    return {
        'free':[length,zr,POWER,amp_free[0],amp_free[1],beam_intensity(qfree,D.from_float(0.0001),POWER)],
        'lens':[q_after_lens[0],q_after_lens[1],lens_amp[0],lens_amp[1],
                beam_intensity(q_after_lens,D.from_float(0.0001),POWER)],
        'split':list(split),
        'mach':[POWER/D(4),POWER*D(3)/D(4),out0[0],out0[1],out1[0],out1[1],POWER],
        'qwp':[POWER,amplitude/D(2).sqrt(),amplitude/D(2).sqrt()],
        'trace':[D(3),POWER/D(2),POWER/D(2)],
    }


def main()->None:
    source_root=ROOT/'physics-core'/'src'/'main'/'java'
    sources=sorted(source_root.rglob('*.java'))
    probe=Path(__file__).with_name('OpticalPathReferenceProbe.java')
    with tempfile.TemporaryDirectory(prefix='cac-d7-decimal-') as temp:
        output=Path(temp)/'classes';output.mkdir()
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(output),
                        *map(str,sources),str(probe)],check=True)
        raw=subprocess.run(['java','-cp',str(output),'OpticalPathReferenceProbe'],
                           check=True,capture_output=True,text=True).stdout
    actual={}
    for line in raw.splitlines():
        name,*values=line.strip().split(',')
        if name in actual:raise AssertionError(f'duplicate case {name}')
        actual[name]=[float(value) for value in values]
    with localcontext() as context:
        context.prec=80
        oracle=expected()
        if actual.keys()!=oracle.keys():
            raise AssertionError(f'case mismatch: {set(actual)^set(oracle)}')
        total=0
        for name,expected_values in oracle.items():
            measured=actual[name]
            if len(measured)!=len(expected_values):
                raise AssertionError(f'{name} field count mismatch')
            for index,(observed,reference) in enumerate(zip(measured,expected_values)):
                # Inputs are the same exact binary64 values; require tight
                # agreement with the independent 80-digit computation.
                tolerance=max(5e-13,abs(float(reference))*3e-12)
                if not math.isfinite(observed) or abs(observed-float(reference))>tolerance:
                    raise AssertionError(f'{name}[{index}]: {observed} vs Decimal80 {reference}')
                total+=1
    print(f'PASS: {total} independent Decimal(80-digit) free-space, lens, Jones,')
    print('two-port splitter, Mach-Zehnder and power-budget scalars')
    print('NOT JDK25/Gradle/JUnit/NeoForge nor Owner-approved laser physics')


if __name__=='__main__':
    main()
