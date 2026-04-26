#!/usr/bin/env python3
"""Extract (seed, table_class) pairs from xiaodash jadx sources.

For each Java file, track which `strArr = com.dalvik.PKG....OO00000OOOOOOOO0000O`
is currently bound, then for each call to OOOOOOO0OOOOO0O00OO0(seed, ...)
emit (seed, full_table_class).
"""
import os
import re
import sys

SRC = sys.argv[1] if len(sys.argv) > 1 else \
    "/Users/martin/claude/_escooter/zt3pro/reverse-engineering/apps/xiaodash/decompiled/jadx/sources"

# Two key patterns:
#   strArr binding: "String[] strArr = com.dalvik.PKG.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O;"
#                   (also strArr2, strArrN variations)
RE_BIND = re.compile(
    r'String\[\]\s+strArr\d*\s*=\s*(com\.dalvik\.[A-Za-z0-9_]+\.OO00000OOOOOOOO0000O\.OO00000OOOOOOOO0000O)\s*;')

#   Direct inline: ".OOOOOOO0OOOOO0O00OO0(SEED_L, com.dalvik.PKG.OO00000OOOOOOOO0000O.OO00000OOOOOOOO0000O)"
RE_INLINE = re.compile(
    r'OOOOOOO0OOOOO0O00OO0\((-?\d+)L,\s*(com\.dalvik\.[A-Za-z0-9_]+\.OO00000OOOOOOOO0000O\.OO00000OOOOOOOO0000O)')

#   strArr-bound: ".OOOOOOO0OOOOO0O00OO0(SEED_L, strArrN)"
RE_BOUND = re.compile(
    r'OOOOOOO0OOOOO0O00OO0\((-?\d+)L,\s*(strArr\d*)\)')

#   1-arg form: ".OO00000OOOOOOOO0000O(SEED_L)" — uses global b1 table
RE_GLOBAL = re.compile(r'\.OO00000OOOOOOOO0000O\((-?\d+)L\)')

GLOBAL_TABLE = "com.dalvik.b1.OO00000OOOOOOOO0000O"

results = set()

for root, _, files in os.walk(SRC):
    for fn in files:
        if not fn.endswith(".java"):
            continue
        path = os.path.join(root, fn)
        try:
            with open(path, encoding="utf-8") as f:
                content = f.read()
        except Exception:
            continue

        # Track strArr bindings per file (rough scoping — in practice these
        # are method-local but per-file is good enough for matching).
        bindings = {}  # name → table_class
        for line in content.split("\n"):
            m_bind = RE_BIND.search(line)
            if m_bind:
                # Extract variable name e.g. strArr / strArr2
                lhs = re.search(r'String\[\]\s+(strArr\d*)\s*=', line)
                if lhs:
                    bindings[lhs.group(1)] = m_bind.group(1)

            for m in RE_INLINE.finditer(line):
                results.add((m.group(1), m.group(2)))
            for m in RE_BOUND.finditer(line):
                tbl = bindings.get(m.group(2))
                if tbl:
                    results.add((m.group(1), tbl))
            for m in RE_GLOBAL.finditer(line):
                results.add((m.group(1), GLOBAL_TABLE))

for seed, table in sorted(results):
    print(f"{seed}\t{table}")
