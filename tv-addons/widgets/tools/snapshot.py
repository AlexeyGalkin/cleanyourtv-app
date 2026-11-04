#!/usr/bin/env python3
"""Mirror the four Jolpica-F1 files the TV add-on reads into a directory (stdlib only).

The fork's f1-data branch serves them from raw.githubusercontent.com for networks that throttle Cloudflare, where
Jolpica lives. Usage: python3 snapshot.py OUT_DIR. A file is replaced only by a response that parses and has MRData.
"""
import json
import os
import sys
import urllib.request

BASE = "https://api.jolpi.ca/ergast/f1/"
FILES = {
    "current.json": "current.json",
    "driverstandings.json": "current/driverstandings.json",
    "constructorstandings.json": "current/constructorstandings.json",
    "last-results.json": "current/last/results.json",
}


def main(out):
    os.makedirs(out, exist_ok=True)
    failed = 0
    for name, path in FILES.items():
        req = urllib.request.Request(BASE + path, headers={"User-Agent": "CleanYourTV-RU-snapshot/1"})
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                body = r.read()
            json.loads(body)["MRData"]
        except Exception as e:  # keep the previous copy
            print(f"skip {name}: {e}", file=sys.stderr)
            failed += 1
            continue
        with open(os.path.join(out, name), "wb") as f:
            f.write(body)
        print(f"ok {name} {len(body)} bytes")
    return 1 if failed == len(FILES) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "f1"))
