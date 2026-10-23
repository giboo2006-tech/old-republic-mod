#!/usr/bin/env python3
"""File ownership check (docs/MILESTONE_3.md and docs/MILESTONE_4.md, 15.2 and 15.3 step 2; docs/MILESTONE_5.md 17.2).

A worker's work is Phase 0 plus that worker's own files. This script reads an ownership table (the
machine-readable copy of a milestone's ownership tables: tools/ownership_m3.json by default, tools/ownership_m4.json or
tools/ownership_m5.json with --table) and fails when a commit range touches a file outside the worker's globs. Added, changed, deleted and renamed
files all count (a rename counts as its old and its new path).

Usage:
  python3 tools/check_ownership.py W3 m3-phase0..HEAD        # a worker and a git diff range
  python3 tools/check_ownership.py W3 --files a/b.java c.json # check explicit paths instead of a range
  python3 tools/check_ownership.py --overlaps                 # no tracked file may belong to two workers (needs a git checkout;
                                                              # outside one, pass --overlaps --files <paths>)
  python3 tools/check_ownership.py W3 --list                  # print a worker's globs
  python3 tools/check_ownership.py --table tools/ownership_m4.json W6 --files ...   # Milestone 4
  python3 tools/check_ownership.py --table tools/ownership_m5.json W8 --files ...   # Milestone 5

Glob syntax: ** spans directories (also none), * and ? stay inside one path segment, {a,b} is either alternative.
Only stdlib.
"""
import argparse
import json
import re
import subprocess
import sys
from functools import lru_cache
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TABLE = ROOT / "tools/ownership_m3.json"


def split_top_level(text):
    """Split a brace body on the commas that are not inside a nested brace."""
    parts, depth, start = [], 0, 0
    for i, c in enumerate(text):
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        elif c == "," and depth == 0:
            parts.append(text[start:i])
            start = i + 1
    parts.append(text[start:])
    return parts


def translate(glob):
    """The regular expression (without anchors) for one glob."""
    out, i, n = [], 0, len(glob)
    while i < n:
        c = glob[i]
        if c == "*" and glob.startswith("**", i):
            i += 2
            if glob.startswith("/", i):
                i += 1
                out.append("(?:.*/)?")
            else:
                out.append(".*")
            continue
        if c == "*":
            out.append("[^/]*")
        elif c == "?":
            out.append("[^/]")
        elif c == "{":
            depth, j = 1, i + 1
            while j < n and depth:
                depth += {"{": 1, "}": -1}.get(glob[j], 0)
                j += 1
            if depth:
                raise ValueError(f"unbalanced braces in glob {glob!r}")
            out.append("(?:" + "|".join(translate(a) for a in split_top_level(glob[i + 1:j - 1])) + ")")
            i = j
            continue
        else:
            out.append(re.escape(c))
        i += 1
    return "".join(out)


@lru_cache(maxsize=None)
def compile_glob(glob):
    return re.compile(translate(glob) + r"\Z")


def load_table(path=TABLE):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    table = dict(data["workers"])
    table["LEAD"] = data["lead"]
    return table


def resolve(table, name):
    key = name.upper()
    if key not in table:
        sys.exit(f"check_ownership: unknown worker {name!r} (known: {', '.join(sorted(table))})")
    return key


def owns(entry, path):
    return (any(compile_glob(g).match(path) for g in entry["owns"])
            and not any(compile_glob(g).match(path) for g in entry.get("except", [])))


def git(*args):
    result = subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"check_ownership: git {' '.join(args)} failed: {result.stderr.strip()}")
    return [line for line in result.stdout.splitlines() if line]


def check_paths(table, worker, paths):
    entry = table[worker]
    outside = [p for p in paths if not owns(entry, p)]
    if not paths:
        print(f"check_ownership: {worker}: no changed files")
    for p in outside:
        others = [w for w, e in table.items() if w not in (worker, "LEAD") and owns(e, p)]
        print(f"check_ownership: {worker} may not change {p} (owner: {others[0] if others else 'lead'})")
    if outside:
        sys.exit(f"check_ownership: {len(outside)} file(s) outside {worker}'s ownership; "
                 "ask the lead for these changes in your report instead")
    if paths:
        print(f"check_ownership: {worker}: all {len(paths)} changed files are owned")


def check_overlaps(table, paths=None):
    bad = 0
    for path in paths if paths is not None else git("ls-files"):
        holders = [w for w, e in table.items() if w != "LEAD" and owns(e, path)]
        if len(holders) > 1:
            bad += 1
            print(f"check_ownership: {path} belongs to {', '.join(holders)}")
    if bad:
        sys.exit(f"check_ownership: {bad} tracked file(s) belong to more than one worker")
    print("check_ownership: no tracked file belongs to two workers")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--table", default=str(TABLE), help="the ownership table (default tools/ownership_m3.json)")
    ap.add_argument("worker", nargs="?", help="W1 to W5 in M3, W1 to W6 in M4, W1 to W8 in M5 (or lead)")
    ap.add_argument("range", nargs="?", help="git diff range, e.g. m3-phase0..HEAD")
    ap.add_argument("--files", nargs="+", help="check these repository-relative paths instead of a range")
    ap.add_argument("--overlaps", action="store_true",
                    help="fail when a tracked file (or each of --files) belongs to two workers")
    ap.add_argument("--list", action="store_true", help="print the worker's globs")
    args = ap.parse_args()

    table = load_table(ROOT / args.table if not Path(args.table).is_absolute() else args.table)
    for entry in table.values():
        for g in entry["owns"] + entry.get("except", []):
            compile_glob(g)  # fail early on a malformed glob
    if args.overlaps:
        check_overlaps(table, args.files)
        return
    if not args.worker:
        ap.error("a worker is required")
    worker = resolve(table, args.worker)
    if args.list:
        entry = table[worker]
        print("\n".join(["owns:"] + entry["owns"] + ["except:"] + entry.get("except", [])))
        return
    if args.files:
        paths = args.files
    elif args.range:
        paths = git("diff", "--name-only", "--no-renames", args.range)
    else:
        ap.error("give a git diff range or --files")
    check_paths(table, worker, sorted(set(paths)))


if __name__ == "__main__":
    main()
