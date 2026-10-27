#!/usr/bin/env python3
"""Independent, dependency-free, high-precision OPT-001 reference oracle.

Uses Python stdlib decimal arithmetic (80 digits), rather than copying Java
floating-point formulas. Compiles the pure Java optics classes under the
available javac (JDK21 here); never builds Minecraft or grants Owner approval.
"""
from __future__ import annotations

from decimal import Decimal as D, localcontext
from pathlib import Path
import math
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
PI = D('3.14159265358979323846264338327950288419716939937510582097494459230781640628620899')
GAUSSIAN_CASES = [
    ('780e-9', '0.0005', '0.02', '0', '0'),
    ('780e-9', '0.0005', '0.02', '1', '0.0005'),
    ('1064e-9', '0.001', '0.5', '7', '0.0007'),
    ('532e-9', '0.0002', '0.003', '-0.2', '0.0001'),
    ('780e-9', '0.0005', '0.02', '0.00000001', '0.000001'),
    ('780e-9', '0.0005', '0', '4', '0.00000001'),
]


def gaussian_values(lam: D, w0: D, power: D, z: D, r: D) -> list[D]:
    zr = PI * w0 * w0 / lam
    w = (w0 * w0 * (D(1) + (z / zr) ** 2)).sqrt()
    x = -D(2) * r * r / (w * w)
    irr = power * D(2) / (PI * w * w) * x.exp()
    enclosed = power * (D(1) - x.exp())
    return [zr, w, irr, enclosed]


def transform(q: tuple[D, D], a: D, b: D, c: D, d: D) -> tuple[D, D]:
    x, y = q
    real_num, imag_num = a * x + b, a * y
    real_den, imag_den = c * x + d, c * y
    den_sq = real_den ** 2 + imag_den ** 2
    return ((real_num * real_den + imag_num * imag_den) / den_sq,
            (imag_num * real_den - real_num * imag_den) / den_sq)


def q_values() -> list[tuple[D, D]]:
    wavelength, w0 = D('780e-9'), D('0.0005')
    zr = PI * w0 * w0 / wavelength
    q = transform((D(0), zr), D(1), D('0.15'), D(0), D(1))
    q = transform(q, D(1), D(0), -D(1) / D('0.2'), D(1))
    q = transform(q, D(1), D('0.4'), D(0), D(1))
    return [q, transform((D('1e16'), D(1)), D(1), D(0), -D(1), D(1)),
            transform((D('0.2'), D('1.5')), D(1), D(0), -D(1) / D('-0.3'), D(1))]


def plane_values() -> list[D]:
    lam, w0, power = D('780e-9'), D('0.0005'), D('0.02')
    q = (D(0), PI * w0 * w0 / lam)
    q = transform(q, D(1), D('0.1'), D(0), D(1))
    q = transform(q, D(1), D(0), -D(1) / D('0.2'), D(1))
    q = transform(q, D(1), D('0.4'), D(0), D(1))
    x, y = q
    w = (lam / PI * (y + x*x/y)).sqrt()
    r = D('0.0002')
    irradiance = D(2)*power/(PI*w*w) * (-D(2)*(r/w)**2).exp()
    aperture = D('0.0004')
    enclosed = power * (D(1) - (-D(2)*(aperture/w)**2).exp())
    return [x, y, w, irradiance, enclosed, power]


def assert_close(measured: float, expected: D, label: str, tolerance: float = 3e-13) -> None:
    if not math.isfinite(measured) or (expected != 0 and not math.isclose(
            measured, float(expected), rel_tol=tolerance, abs_tol=1e-300)) or (
            expected == 0 and measured != 0):
        raise AssertionError(f'{label}: got {measured:.17g}, expected {expected}')


def main() -> None:
    source_root = ROOT / 'physics-core' / 'src' / 'main' / 'java'
    java_sources = sorted(source_root.rglob('*.java'))
    probe = Path(__file__).with_name('OpticsReferenceProbe.java')
    with tempfile.TemporaryDirectory(prefix='cac-optics-ref-') as tmp:
        classes = Path(tmp) / 'classes'
        classes.mkdir()
        subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(classes),
                        *map(str, java_sources), str(probe)], check=True)
        result = subprocess.run(['java', '-cp', str(classes), 'OpticsReferenceProbe'],
                                check=True, capture_output=True, text=True)
    actual = {}
    for line in result.stdout.strip().splitlines():
        fields = line.strip().split(',')
        key = (fields[0], int(fields[1]))
        if key in actual:
            raise AssertionError(f'duplicate case {key}')
        actual[key] = [float(v) for v in fields[2:]]
    if len(actual) != len(GAUSSIAN_CASES) + 3 + 1:
        raise AssertionError(f'incomplete Java reference probe: {len(actual)} cases')

    with localcontext() as context:
        context.prec = 80
        for i, inputs in enumerate(GAUSSIAN_CASES):
            expected = gaussian_values(*map(D, inputs))
            values = actual[('G', i)]
            if len(values) != len(expected):
                raise AssertionError(f'bad Gaussian output {i}')
            for field, measured, truth in zip(('zR', 'w', 'I', 'P_enc'), values, expected):
                assert_close(measured, truth, f'Gaussian[{i}].{field}')

        for i, expected in enumerate(q_values()):
            values = actual[('Q', i)]
            for field, measured, truth in zip(('q.real', 'q.imag'), values, expected):
                assert_close(measured, truth, f'ABCD[{i}].{field}')
        expected = plane_values()
        for field, measured, truth in zip(
                ('q.real', 'q.imag', 'radius', 'I', 'P_enc', 'P'),
                actual[('P', 0)], expected):
            assert_close(measured, truth, f'Lens-plane.{field}')
    print(f'PASS: {len(GAUSSIAN_CASES)} Gaussian + 3 ABCD + 1 lens-plane cases = '
          f'{len(GAUSSIAN_CASES)*4 + 3*2 + 6} independent high-precision scalar comparisons')
    print('reference: decimal(80 digits), SI units; no NeoForge or game-world physics')


if __name__ == '__main__':
    main()
