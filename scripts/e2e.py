#!/usr/bin/env python3
"""
End-to-end proof of FactoryGuard's central workflow. Needs only Python 3 (no extra packages).

    healthy v1.0 -> faulty v1.1 -> KPI failure detected -> automatic rollback
                 -> healthy v1.0 -> incident recorded (with full evidence)

Run it against a running stack (start it first with ./run.sh):

    python3 scripts/e2e.py              one run
    python3 scripts/e2e.py --runs 5     five runs in a row (to show it works reliably)

Every run checks the evidence stored in the incident, and the script writes docs/EVIDENCE.md with the
numbers it MEASURED (throughput, time to detect, recovery time, verification). If a run fails, the failure
is written there too. Only a file produced by a real run counts as evidence.

    BASE_URL=http://my-server:3000 python3 scripts/e2e.py     to test another machine
"""
import argparse
import json
import os
import platform
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from http.cookiejar import CookieJar

BASE = os.environ.get("BASE_URL", "http://localhost:3000").rstrip("/")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


class Failure(Exception):
    pass


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with opener.open(req, timeout=20) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else {})
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, {"error": raw}


def step(msg):
    print("\n==> " + msg, flush=True)


def check(cond, msg):
    if not cond:
        print("FAIL: " + msg, flush=True)
        raise Failure(msg)
    print("ok:   " + msg, flush=True)


def wait_until(label, fn, timeout, every=3):
    end = time.time() + timeout
    while time.time() < end:
        value = fn()
        if value:
            return value
        time.sleep(every)
    print("FAIL: timed out waiting for " + label, flush=True)
    raise Failure("timed out waiting for " + label)


def dashboard_up():
    try:
        with urllib.request.urlopen(BASE + "/login.html", timeout=5) as r:
            return r.status == 200
    except Exception:
        return False


def one_run(n, total):
    """Plays the whole story once and returns the measured evidence."""
    step("Run %d of %d: start monitoring from scratch (deploys v1.0)" % (n, total))
    _, before = call("GET", "/api/incidents")
    last_id = max([i["id"] for i in before], default=0)

    code, body = call("POST", "/api/environment/start")
    check(code == 200, "start accepted: " + json.dumps(body))
    t_start = time.time()
    st = wait_until("v1.0 to become HEALTHY",
                    lambda: (lambda s: s if s["state"] == "HEALTHY" else None)(call("GET", "/api/status")[1]), 240)
    healthy_after = round(time.time() - t_start)
    check(st["currentVersion"] == "v1.0", "v1.0 is running and HEALTHY")
    epm_v10 = st["latest"]["eventsPerMinute"]
    check(epm_v10 >= st["threshold"], "throughput on v1.0 is %.1f events/min (threshold %.0f)" % (epm_v10, st["threshold"]))

    step("Run %d: deploy the faulty v1.1" % n)
    code, body = call("POST", "/api/deploy/v1.1")
    check(code == 200, "v1.1 deployed: " + json.dumps(body))
    t_deploy = time.time()

    def finished_incident():
        _, inc = call("GET", "/api/incidents")
        new = [i for i in inc if i["id"] > last_id and i["outcome"] != "OPEN"]
        return new[0] if new else None

    inc = wait_until("the incident to finish", finished_incident, 360, every=5)
    wall_seconds = round(time.time() - t_deploy)

    step("Run %d: check the evidence stored in the incident" % n)
    check(inc["outcome"] == "RECOVERED", "outcome is RECOVERED (got %s)" % inc["outcome"])
    check(inc["version"] == "v1.1", "failed release recorded: %s" % inc["version"])
    check(inc["rollback_target"] == "v1.0", "rollback target recorded: %s" % inc["rollback_target"])
    check(inc["detected_events_per_minute"] is not None and inc["detected_events_per_minute"] < inc["threshold"],
          "detected metric recorded: %.1f events/min below threshold %.0f" % (inc["detected_events_per_minute"], inc["threshold"]))
    check(bool(inc["reason"]), "failure reason recorded: " + inc["reason"])
    check(inc["detection_seconds"] is not None, "time to detect recorded: %s s after deploy" % inc["detection_seconds"])
    check(inc["recovery_seconds"] is not None, "recovery time recorded: %s s" % inc["recovery_seconds"])
    check(inc["verified_events_per_minute"] is not None and inc["verified_events_per_minute"] >= inc["threshold"],
          "final verification throughput: %.1f events/min" % inc["verified_events_per_minute"])
    check(inc["verified_health_up"] is True and "PASSED" in (inc["verification"] or ""), "final verification: " + inc["verification"])

    _, st = call("GET", "/api/status")
    check(st["currentVersion"] == "v1.0", "v1.0 is running again after the rollback")
    wait_until("v1.0 to be HEALTHY again",
               lambda: (lambda s: s if s["state"] == "HEALTHY" else None)(call("GET", "/api/status")[1]), 120)
    print("ok:   v1.0 is HEALTHY again", flush=True)
    _, deps = call("GET", "/api/deployments")
    check(any(d["version"] == "v1.1" and d["status"] == "ROLLED_BACK" for d in deps), "v1.1 is marked ROLLED_BACK")

    return {
        "run": n,
        "healthy_after_s": healthy_after,
        "v10_events_per_min": round(epm_v10, 1),
        "v11_events_per_min": round(inc["detected_events_per_minute"], 1),
        "detect_s": inc["detection_seconds"],
        "recovery_s": inc["recovery_seconds"],
        "verified_events_per_min": round(inc["verified_events_per_minute"], 1),
        "verification": inc["verification"],
        "wall_s": wall_seconds,
        "outcome": inc["outcome"],
    }


def stats(values):
    return "%s / %.1f / %s" % (min(values), sum(values) / len(values), max(values)) if values else "n/a"


def write_evidence(results, failures, total, started):
    path = os.path.join(ROOT, "docs", "EVIDENCE.md")
    lines = [
        "# Test evidence (generated by scripts/e2e.py)",
        "",
        "This file is written by the script from a **live run**. Do not edit the numbers by hand.",
        "",
        "- Generated: %s" % datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC"),
        "- Target: %s" % BASE,
        "- Test computer: %s, Python %s" % (platform.platform(), platform.python_version()),
        "- Runs requested: %d, passed: %d, failed: %d" % (total, len(results), len(failures)),
        "- Total time: %d s" % round(time.time() - started),
        "",
        "Workflow tested: healthy v1.0 -> faulty v1.1 -> KPI failure detected -> automatic rollback -> healthy v1.0 -> incident recorded.",
        "",
    ]
    if results:
        lines += [
            "## Runs",
            "",
            "| Run | v1.0 events/min | v1.1 events/min (detected) | Time to detect (s) | Recovery time (s) | Verified events/min | Outcome |",
            "|-----|-----------------|----------------------------|--------------------|-------------------|---------------------|---------|",
        ]
        for r in results:
            lines.append("| %d | %s | %s | %s | %s | %s | %s |" % (
                r["run"], r["v10_events_per_min"], r["v11_events_per_min"], r["detect_s"], r["recovery_s"],
                r["verified_events_per_min"], r["outcome"]))
        lines += [
            "",
            "Summary (min / average / max): time to detect %s s, recovery time %s s." % (
                stats([r["detect_s"] for r in results]), stats([r["recovery_s"] for r in results])),
            "",
            "Definitions: *time to detect* = seconds from deploying the faulty release to the incident being recorded. "
            "*Recovery time* = seconds from detection to the restored version passing verification.",
            "",
            "Final verification text of the last run: `%s`" % results[-1]["verification"],
            "",
        ]
    if failures:
        lines += ["## Failures", ""] + ["- Run %d: %s" % (n, msg) for n, msg in failures] + [""]
    with open(path, "w") as f:
        f.write("\n".join(lines))
    print("\nEvidence written to docs/EVIDENCE.md", flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--runs", type=int, default=1, help="how many times to repeat the whole story")
    args = parser.parse_args()
    started = time.time()
    results, failures = [], []

    try:
        step("Waiting for the dashboard to come up")
        wait_until("dashboard", dashboard_up, 180)

        step("Register a brand-new account")
        name = "e2e_" + uuid.uuid4().hex[:8]
        code, _ = call("POST", "/api/auth/register",
                       {"username": name, "email": name + "@example.com", "password": "correct-horse-1"})
        check(code == 200, "registered " + name)

        step("A new account must start at zero")
        _, summary = call("GET", "/api/summary")
        check(summary["deployments"] == 0 and summary["incidents"] == 0 and summary["avg_recovery_seconds"] == 0,
              "summary is all zeros: " + json.dumps(summary))
        _, st = call("GET", "/api/status")
        check(st["started"] is False and st["state"] == "NOT_STARTED", "status is NOT_STARTED")
        check(st["latest"] is None and st["samples"] == [], "no measurements yet")
    except Failure as e:
        failures.append((0, "setup: %s" % e))

    if not failures:
        for n in range(1, args.runs + 1):
            try:
                results.append(one_run(n, args.runs))
            except Failure as e:
                failures.append((n, str(e)))
                break

        try:
            step("Stop and reset")
            code, _ = call("POST", "/api/environment/stop")
            check(code == 200, "stopped")
            _, st = call("GET", "/api/status")
            check(st["started"] is False, "status is back to NOT_STARTED")
        except Failure as e:
            failures.append((0, "cleanup: %s" % e))

    write_evidence(results, failures, args.runs, started)
    if failures:
        print("\nFAILED. See docs/EVIDENCE.md and the messages above.", flush=True)
        sys.exit(1)
    print("\nALL CHECKS PASSED in %d run(s). Recovery time (s) min/avg/max: %s" % (
        len(results), stats([r["recovery_s"] for r in results])), flush=True)


if __name__ == "__main__":
    main()
