#!/usr/bin/env python3
# Compare every res/values/strings*.xml of the phone modules with its values-<loc> twin.
# Usage: python3 check_ru.py <repo root> [locale=ru] [--allow-missing]
# Exit 1 on: missing/extra keys, translatable=false keys translated, ICU argument mismatch,
# plural without `other`, printf placeholder mismatch, lost formatted="false", unescaped ' or ",
# Android <plurals> without one/few/many/other, malformed XML, unbalanced braces.
import glob, os, re, sys
import xml.etree.ElementTree as ET

root = sys.argv[1]
loc = next((a for a in sys.argv[2:] if not a.startswith("--")), "ru")
allow_missing = "--allow-missing" in sys.argv
MODULES = ["app", "ui/design", "feature/connect", "feature/setup", "feature/manage", "feature/launcher", "feature/modules", "feature/backup"]
RU_PLURALS = {"one", "few", "many", "other"}
PRINTF = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]")
errors, warns = [], []


def raw_texts(path):
    """name -> (raw inner xml text, attrs, kind, items) without aapt unescaping."""
    src = open(path, encoding="utf-8").read()
    out = {}
    for e in ET.fromstring(src):
        if e.tag not in ("string", "plurals", "string-array"):
            continue
        if e.tag == "string":
            out[e.get("name")] = ("string", e.attrib, e.text or "", None)
        else:
            items = {(i.get("quantity") or str(n)): (i.text or "") for n, i in enumerate(e)}
            out[e.get("name")] = (e.tag, e.attrib, None, items)
    return out


def icu_args(s):
    """Top-level and nested ICU argument names with their type ('plural', 'select', or '')."""
    args, i, depth, n = set(), 0, 0, len(s)
    while i < n:
        c = s[i]
        if c == "'" and i + 1 < n and s[i + 1] in "{}#":   # ICU quoting (DOUBLE_OPTIONAL)
            warns.append(f"ICU quote before {s[i + 1]!r}: {s[:60]!r}")
        if c == "{":
            m = re.match(r"\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?:,\s*(plural|select|selectordinal|number|date|time)\s*)?[,}]", s[i:])
            # A plural branch body like `one {row}` is text, not an argument.
            branch = re.search(r"(?:=\d+|zero|one|two|few|many|other)\s*$", s[:i])
            if m and not branch:
                args.add((m.group(1), m.group(2) or ""))
            depth += 1
        elif c == "}":
            depth -= 1
            if depth < 0:
                raise ValueError("unbalanced }")
        i += 1
    if depth != 0:
        raise ValueError("unbalanced {")
    return args


def plural_blocks(s):
    """For each `{x, plural, ...}` return the set of selectors."""
    res = []
    for m in re.finditer(r"\{\s*([a-z_]+)\s*,\s*plural\s*,", s):
        i, depth, sel = m.end(), 1, set()
        while i < len(s) and depth > 0:
            if depth == 1:
                k = re.match(r"\s*(=\d+|zero|one|two|few|many|other)\s*\{", s[i:])
                if k:
                    sel.add(k.group(1)); i += k.end(); depth += 1; continue
            if s[i] == "{": depth += 1
            elif s[i] == "}": depth -= 1
            i += 1
        res.append((m.group(1), sel))
    return res


def unescaped(s, ch):
    return any(s[i] == ch and (i == 0 or s[i - 1] != "\\") for i in range(len(s)))


for mod in MODULES:
    for en in sorted(glob.glob(os.path.join(root, mod, "src/main/res/values/strings*.xml"))):
        en = os.path.normpath(en)
        tr = en.replace(os.sep + "values" + os.sep, os.sep + f"values-{loc}" + os.sep)
        rel = os.path.relpath(tr, root)
        E = raw_texts(en)
        if not os.path.exists(tr):
            (warns if allow_missing else errors).append(f"{rel}: file missing"); continue
        try:
            T = raw_texts(tr)
        except ET.ParseError as x:
            errors.append(f"{rel}: XML {x}"); continue
        for name, (kind, attrs, text, items) in E.items():
            if attrs.get("translatable") == "false":
                if name in T: errors.append(f"{rel}: {name} is translatable=false in values but present here")
                continue
            if name not in T:
                (warns if allow_missing else errors).append(f"{rel}: missing {name}"); continue
            tk, tattrs, ttext, titems = T[name]
            if tk != kind: errors.append(f"{rel}: {name} is <{tk}>, values has <{kind}>"); continue
            if kind == "plurals":
                if set(titems) != RU_PLURALS: errors.append(f"{rel}: {name} quantities {sorted(titems)} != one/few/many/other")
                ep = sorted(m.group(0) for m in PRINTF.finditer(list(items.values())[-1]))
                for q, t in titems.items():
                    if sorted(m.group(0) for m in PRINTF.finditer(t)) != ep: errors.append(f"{rel}: {name}[{q}] printf {t!r}")
                continue
            if attrs.get("formatted") == "false" and tattrs.get("formatted") != "false":
                errors.append(f"{rel}: {name} lost formatted=\"false\" (aapt2 fails on several % signs)")
            for ch in "'\"":
                if unescaped(ttext, ch): errors.append(f"{rel}: {name} has an unescaped {ch} (use \\{ch} or «»)")
            if attrs.get("formatted") != "false" and sorted(m.group(0) for m in PRINTF.finditer(text)) != sorted(m.group(0) for m in PRINTF.finditer(ttext)):
                errors.append(f"{rel}: {name} printf placeholders differ")
            try:
                ea, ta = icu_args(text), icu_args(ttext)
            except ValueError as x:
                errors.append(f"{rel}: {name} {x}"); continue
            if ea != ta: errors.append(f"{rel}: {name} ICU args {sorted(ta)} != {sorted(ea)}")
            for arg, sel in plural_blocks(ttext.replace("\\'", "'")):
                if "other" not in sel: errors.append(f"{rel}: {name} plural {arg} has no `other` (ICU throws)")
                missing = {"one", "few", "many"} - sel
                if missing: warns.append(f"{rel}: {name} plural {arg} lacks {sorted(missing)} (falls back to other)")
            if "—" in ttext or "--" in ttext:
                warns.append(f"{rel}: {name} has an em dash or --, against the house rule CopyTest enforces in values/")
        for name in T:
            if name not in E: errors.append(f"{rel}: extra {name} (lint ExtraTranslation)")

for w in warns: print("WARN ", w)
for e in errors: print("ERROR", e)
print(f"{len(errors)} errors, {len(warns)} warnings")
sys.exit(1 if errors else 0)
