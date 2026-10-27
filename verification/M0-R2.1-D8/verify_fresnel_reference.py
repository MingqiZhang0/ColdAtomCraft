#!/usr/bin/env python3
"""Independent Decimal(80) planar Fresnel oracle; JDK21-compatible, NOT JUnit.

The oracle intentionally uses distinct algorithms from Java: Decimal Taylor
series for sin/cos and a rational complex-unit-circle formula for TIR phases.
No external package dependency; does not validate arbitrary optical coatings.
"""
from decimal import Decimal, localcontext
from pathlib import Path
import subprocess
import tempfile

D = Decimal
PI = D('3.1415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679')
ROOT = Path(__file__).resolve().parents[2]
NUM = D('0.41')


def sincos(theta):
    theta = theta % (2 * PI)
    xx = theta * theta
    sin = theta
    cos = D(1)
    term_s = theta
    term_c = D(1)
    for i in range(1, 65):
        term_s = -term_s * xx / D((2*i) * (2*i+1))
        term_c = -term_c * xx / D((2*i-1) * (2*i))
        sin += term_s
        cos += term_c
    return sin, cos


def scalar_fresnel(n1, n2, degrees):
    theta = PI * D(degrees) / 180
    si, ci = sincos(theta)
    sint = n1 * si / n2
    sin2t = sint * sint
    tir = sin2t >= 1
    if tir:
        alpha = (sin2t-1).sqrt()
        def pure_phase(a,b):
            den=a*a+b*b
            return ((a*a-b*b)/den, (-2*a*b)/den)
        rs = pure_phase(n1*ci,n2*alpha)
        rp = pure_phase(n2*ci,n1*alpha)
        ct=D(0)
        Ts=Tp=D(0)
        tx=D(0)
    else:
        ct=(1-sin2t).sqrt()
        rs = ((n1*ci-n2*ct)/(n1*ci+n2*ct),D(0))
        rp = ((n2*ci-n1*ct)/(n2*ci+n1*ct),D(0))
        ts=2*n1*ci/(n1*ci+n2*ct)
        tp=2*n1*ci/(n2*ci+n1*ct)
        factor=n2*ct/(n1*ci)
        Ts=factor*ts*ts
        Tp=factor*tp*tp
        tx=n1*si/n2
    Rs=sum(v*v for v in rs)
    Rp=sum(v*v for v in rp)
    sn,cn=sincos(NUM)
    Pr=Rs*cn*cn+Rp*sn*sn
    Pt=Ts*cn*cn+Tp*sn*sn
    return [rs[0],rs[1],rp[0],rp[1],Rs,Rp,Ts,Tp,ci,ct,
            Pr,Pt,-ci,tx,Pr+Pt],tir


def compare(val, target, label):
    tolerance=D('3e-12') * max(D(1),abs(target))
    if abs(D(val)-target) > tolerance:
        raise AssertionError(f'{label}: Java={val} Decimal80={target} delta={abs(D(val)-target)}')


def main():
    sources=sorted(str(x) for x in (ROOT/'physics-core/src/main/java').rglob('*.java'))
    probe=ROOT/'verification/M0-R2.1-D8/FresnelReferenceProbe.java'
    with tempfile.TemporaryDirectory(prefix='cac-d8-fresnel-') as tmp:
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',tmp,*sources,str(probe)],check=True)
        output=subprocess.check_output(['java','-cp',tmp,
            'dev.coldatomcraft.verification.FresnelReferenceProbe'],text=True)
    cases=[(D('1'),D('1.5'),0),(D('1'),D('1.5'),20),(D('1'),D('1.5'),40),
           (D('1'),D('1.5'),60),(D('1.5'),D('1'),0),(D('1.5'),D('1'),20),
           (D('1.5'),D('1'),45),(D('1.5'),D('1'),60),(D('1.5'),D('1'),75)]
    comparisons=0
    with localcontext() as ctx:
        ctx.prec=80
        for line in output.splitlines():
            fields=line.split('|')
            if fields[0]=='BREWSTER':
                compare(fields[1],D(0),'Brewster r_p')
                compare(fields[2],D(0),'Brewster reflected power')
                comparisons+=2
                continue
            assert fields[0]=='C',line
            idx=int(fields[1]); expected,tir=scalar_fresnel(*cases[idx])
            for j,(actual,standard) in enumerate(zip(fields[2:17],expected)):
                compare(actual,standard,f'case {idx}, quantity {j}')
                comparisons+=1
            if (fields[17].lower()=='true') != tir:
                raise AssertionError('TIR classification mismatch in case '+str(idx))
            comparisons+=1
    assert comparisons==9*16+2,comparisons
    print(f'PASS: {comparisons}/{comparisons} independent Decimal80 Fresnel checks across 9 cases + Brewster, NOT JUnit')

if __name__=='__main__':
    main()
