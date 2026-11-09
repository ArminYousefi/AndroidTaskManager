#!/usr/bin/env python3
"""WCAG 2.x contrast check of the app palette (ui/theme/Color.kt + Theme.kt). Exit code 1 if a text pair is below 4.5:1.
Run: python3 tools/contrast_check.py"""
import sys

def rgb(h): h = h.lstrip('#'); return [int(h[i:i+2], 16) for i in (0, 2, 4)]
def lum(c):
    f = lambda v: (v/255)/12.92 if v/255 <= 0.03928 else (((v/255)+0.055)/1.055)**2.4
    r, g, b = map(f, c); return 0.2126*r + 0.7152*g + 0.0722*b
def ratio(a, b):
    la, lb = lum(rgb(a)), lum(rgb(b)); return (max(la, lb)+0.05)/(min(la, lb)+0.05)
def mix(a, b, t): return ''.join(f"{round(x*(1-t)+y*t):02X}" for x, y in zip(rgb(a), rgb(b)))

THEMES = {
  "light": dict(bg="F5F6FB", surface="FFFFFF", column="EBEDF6", onSurface="11142B", onVariant="53587A", primary="4F46E5", onPrimary="FFFFFF", error="B91C1C",
                accents=dict(success="15803D", warning="B45309", danger="B91C1C", todo="475569", inProgress="1D4ED8", indigo="4F46E5")),
  "dark":  dict(bg="0A0C18", surface="141830", column="10132A", onSurface="EDEFFD", onVariant="A6ABD0", primary="9DA0FF", onPrimary="14134A", error="F87171",
                accents=dict(success="4ADE80", warning="FBBF24", danger="F87171", todo="94A3B8", inProgress="60A5FA", indigo="9DA0FF")),
}
fail = False
def check(name, v, need=4.5):
    global fail
    ok = v >= need; fail |= not ok
    print(f"  {'ok  ' if ok else 'FAIL'} {v:5.2f}  {name}")
for tname, t in THEMES.items():
    print(tname)
    check("text on background", ratio(t["onSurface"], t["bg"]))
    check("text on surface", ratio(t["onSurface"], t["surface"]))
    check("secondary text on surface", ratio(t["onVariant"], t["surface"]))
    check("secondary text on column", ratio(t["onVariant"], t["column"]))
    check("primary on background", ratio(t["primary"], t["bg"]))
    check("button text on primary", ratio(t["onPrimary"], t["primary"]))
    check("error text on surface", ratio(t["error"], t["surface"]))
    for n, c in t["accents"].items():            # Tag(): text = accent blended 30% towards onSurface, on a 10% accent tint
        for bgn in ("surface", "column"):
            text = mix(c, t["onSurface"], 0.3); tint = mix(t[bgn], c, 0.10)
            check(f"tag '{n}' on {bgn}", ratio(text, tint))
sys.exit(1 if fail else 0)
