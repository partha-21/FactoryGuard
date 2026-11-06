#!/usr/bin/env python3
"""
Copies the numbers from docs/EVIDENCE.md into the Results table of docs/SUBMISSION.md.
Needs only Python 3. Run it AFTER a real `python3 scripts/e2e.py --runs 5`.

It refuses to do anything if docs/EVIDENCE.md is missing or contains failures, so the write-up can
never show numbers that were not measured by a passing run.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EVIDENCE = os.path.join(ROOT, "docs", "EVIDENCE.md")
SUBMISSION = os.path.join(ROOT, "docs", "SUBMISSION.md")


def die(msg):
    print("Not updated: " + msg)
    sys.exit(1)


if not os.path.exists(EVIDENCE):
    die("docs/EVIDENCE.md does not exist. Run `python3 scripts/e2e.py --runs 5` against a running stack first.")
text = open(EVIDENCE).read()
if "## Failures" in text:
    die("docs/EVIDENCE.md lists failures. Fix them and re-run the test before filling in the write-up.")

rows = []
for line in text.splitlines():
    m = re.match(r"^\|\s*(\d+)\s*\|\s*([\d.]+)\s*\|\s*([\d.]+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*([\d.]+)\s*\|\s*(\w+)\s*\|$", line)
    if m:
        rows.append(dict(v10=float(m[2]), v11=float(m[3]), detect=int(m[4]), recover=int(m[5]), ok=m[7] == "RECOVERED"))
if not rows:
    die("no run rows found in docs/EVIDENCE.md.")
if not all(r["ok"] for r in rows):
    die("not every run ended RECOVERED.")


def rng(key, unit=""):
    vals = [r[key] for r in rows]
    avg = sum(vals) / len(vals)
    return "%s (range %s to %s)" % (("%.1f" % avg) + unit, min(vals), max(vals))


values = {
    "Throughput on v1.0 (events/min)": rng("v10"),
    "Throughput on v1.1 (events/min)": rng("v11"),
    "Time to detect (s)": rng("detect"),
    "Recovery time (s)": rng("recover"),
    "Runs passed": "%d of %d (see docs/EVIDENCE.md)" % (len(rows), len(rows)),
}

sub = open(SUBMISSION).read()
for label, value in values.items():
    pattern = r"(\| %s \| )[^|]*( \|)" % re.escape(label)
    if not re.search(pattern, sub):
        die("could not find the row '%s' in docs/SUBMISSION.md." % label)
    sub = re.sub(pattern, lambda m: m.group(1) + value + m.group(2), sub, count=1)
sub = sub.replace("**Not yet measured.** The workflow has been built and its checks written, but no real Docker run has been\nrecorded yet, so no result is claimed here.",
                  "Measured by `scripts/e2e.py` on a live run; the raw record is `docs/EVIDENCE.md`.")
open(SUBMISSION, "w").write(sub)
print("docs/SUBMISSION.md updated from docs/EVIDENCE.md:")
for k, v in values.items():
    print("  %-34s %s" % (k, v))
