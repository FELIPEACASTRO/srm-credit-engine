# tools/oracle.py — oráculo independente do motor (aritmética exata com Fraction).
# NÃO importa código do backend: serve para conferir qualquer valor produzido pelo motor
# (inclusive ao vivo, na defesa). Verificado contra os golden cases da seção 4.3.
# uso: python -X utf8 tools/oracle.py --face 100000.00 --n 3 --type DUPLICATA [--fx 5.4321]
#      [--minor 2] [--mode HALF_EVEN|HALF_UP] [--base 0.01] [--spread 0.02]
import argparse
from fractions import Fraction as F
from math import floor

SPREAD = {"DUPLICATA": "0.015", "CHEQUE": "0.025"}


def rnd(x: F, minor: int, mode: str) -> F:
    q = F(10) ** minor
    c = x * q
    n = floor(c)
    r = c - n
    if r > F(1, 2) or (r == F(1, 2) and (mode == "HALF_UP" or n % 2 == 1)):
        n += 1
    return F(n) / q


def fmt(x: F, minor: int) -> str:
    q = 10**minor
    v = x * q
    assert v.denominator == 1
    s = str(abs(v.numerator)).rjust(minor + 1, "0")
    return ("-" if v < 0 else "") + (s[:-minor] + "." + s[-minor:] if minor else s)


def main() -> None:
    p = argparse.ArgumentParser()
    p.add_argument("--face", required=True)
    p.add_argument("--n", type=int, required=True)
    p.add_argument("--type")
    p.add_argument("--spread")
    p.add_argument("--base", default="0.01")
    p.add_argument("--fx")
    p.add_argument("--minor", type=int, default=2)
    p.add_argument("--mode", default="HALF_EVEN", choices=["HALF_EVEN", "HALF_UP"])
    a = p.parse_args()
    spread = F(a.spread) if a.spread else F(SPREAD[a.type])
    face = F(a.face)
    pv = rnd(face / (1 + F(a.base) + spread) ** a.n, 2, a.mode)
    out = {"pv_brl": fmt(pv, 2), "desagio_brl": fmt(face - pv, 2)}
    if a.fx:
        out["pago"] = fmt(rnd(pv / F(a.fx), a.minor, a.mode), a.minor)
    print(out)


if __name__ == "__main__":
    main()
