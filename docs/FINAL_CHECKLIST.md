# Final checklist (zero cost)

Prove the central workflow first, on your own computer:

> healthy v1.0 -> faulty v1.1 -> KPI failure detected -> automatic rollback -> healthy v1.0 -> incident recorded

Do not create paid AWS resources. The AWS package is a prepared deployment path; the working system is demonstrated locally.

## 1. Prove it works (laptop, Docker Desktop)
- [ ] `./run.sh` (or `run.bat`) finishes and prints "Ready"
- [ ] http://localhost:3000 shows sign-in; a new account shows all zeros and "Nothing is running yet"
- [ ] **Start monitoring from scratch** reaches **Healthy**, throughput near 100
- [ ] **Deploy v1.1** leads to an automatic rollback and an incident marked RECOVERED
- [ ] The **Incident timeline** shows: failed release, rollback target, detected metric, time to detect, recovery time, final verification
- [ ] `python3 scripts/e2e.py --runs 5` prints ALL CHECKS PASSED and writes `docs/EVIDENCE.md`
- [ ] If any run fails: keep `docs/EVIDENCE.md`, copy the error text, and get it fixed before going on

## 2. Check restart safety
- [ ] While v1.0 is Healthy, run `docker compose restart guard`, refresh the dashboard: it still shows v1.0 and the same last known-good version
- [ ] Deploy v1.1, and restart guard during the rollback: the incident shows INTERRUPTED rather than staying open forever

## 3. Evidence for the judges
- [ ] `python3 scripts/fill_results.py` copies the measured averages into `docs/SUBMISSION.md` (it refuses if the evidence is missing or has failures)
- [ ] Deck, slide 3 "An example run": replace the illustrative 100 and 20 with your measured v1.0 and v1.1 events/min, and delete the words "illustrative numbers"
- [ ] Deck, slide 5: replace the 60 s target with your measured recovery time (or keep it labelled as a target)
- [ ] Real screenshots: zero state, healthy, degraded, rolled back, incident timeline
- [ ] Put the project on GitHub; the **Actions** tab should show green tests
- [ ] Practise the demo twice (`docs/DEMO_SCRIPT.md`)

## 4. Be honest in the presentation
- [ ] Say the data is simulated and the guard is a prototype (`docs/SECURITY.md`)
- [ ] Say AWS is a prepared deployment path (not deployed) unless you really ran it
- [ ] Say latency, error rate and backlog are future KPIs

## 5. Hand-in
- [ ] Deck with real numbers and screenshots, contact line on slide 1 filled in
- [ ] Repository link, `docs/SUBMISSION.md`, `docs/EVIDENCE.md`
