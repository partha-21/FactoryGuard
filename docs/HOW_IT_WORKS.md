# How FactoryGuard works (the core)

## The loop in one picture

```
 simulator --MQTT--> mosquitto --MQTT--> demo-app (v1.0 or v1.1)
                                              ^
                                              | every 5 s: GET /actuator/health
                                              |            GET /api/stats
                                            guard
                                              |
                          healthy? ---- yes --> keep watching, promote to "known-good"
                              |
                              no, several times in a row
                              v
                  record incident -> alert -> deploy last known-good
                                           -> wait for warm-up -> check again -> record result
```

## What "healthy" means

A check passes only if **both** are true:

1. the health page answers `UP`, and
2. throughput (events processed per minute, measured over a 20-second window) is **at or above the threshold** (default 60; normal is about 100).

Process-alive alone is not enough. v1.1 is the proof: its health page is `UP` the whole time, yet it
processes only about 24 events a minute, and FactoryGuard still catches it.

## States shown on the dashboard

| State | Meaning |
|-------|---------|
| WARMING UP | A version was just deployed. We wait (default 25 s) so the measurement window can fill. |
| HEALTHY | Checks are passing. After 6 passes in a row, the version becomes **last known-good**. |
| DEGRADED (n/3) | A check failed. Three bad checks in a row (about 15 s) are needed to act, so one blip does not trigger a rollback. |
| RECOVERING / VERIFYING | Rolling back, then re-checking the restored version. |

## Safety rules built in

- **Sustained failure only:** several bad checks in a row, not a single one.
- **Warm-up grace period** after every deployment.
- **Known-good tracking:** only a version that passed repeated checks can be a rollback target.
- **Rollback limit:** at most 2 automatic rollbacks per 10 minutes (`GUARD_MAX_ROLLBACKS_PER_10_MIN`). After that it stops and tells a human.
- **No target, no rollback:** if the broken version is also the only known-good one, it records the incident and does nothing risky.
- **Verification:** a rollback only counts as success if the restored version passes the same checks.

## Where the real bug lives

The bug is baked into the **v1.1 image** (`BUG_MODE=true`), in the message handler of the demo app.
Rolling back to v1.0 therefore genuinely fixes the problem; no flag is flipped.

## How a rollback physically happens

`guard` runs `docker rm -f demo-app` and then `docker run ... factoryguard/demo-app:<version>` on the
same Docker host, using the mounted Docker socket. Only the demo app is replaced; the broker, the
simulator and the database keep running.

## Recorded for every incident

Release version, reason, the metric values at detection, the action taken, the outcome
(`RECOVERED`, `RECOVERY_FAILED`, `ROLLBACK_LIMIT_REACHED`, `NO_ROLLBACK_TARGET`) and the recovery time in seconds.

## Code map

| Class (`guard/`) | Job |
|------------------|-----|
| `MonitoringService` | The 5-second loop; applies the health rules |
| `RecoveryService` | Incident, alert, rollback, verification, rollback limit |
| `DeploymentService` | Current version, known-good version, deployment history |
| `DockerService` | Starts and replaces the demo app container |
| `Prober` | Calls the demo app's health and stats endpoints |
| `NotificationService` | Log alert plus optional webhook |
| `GuardController` | API used by the dashboard, scoped to the signed-in user |
| `AuthService`, `AuthFilter`, `AuthController` | Register, sign in, sessions; every `/api/` call except sign-in needs a session |

The demo app (`demo-app/`) is deliberately small: `MqttConsumer` receives messages (slowly in v1.1),
`StatsService` computes events per minute.

## Accounts and the "zero" state

Nothing runs when FactoryGuard boots. A signed-in user presses **Start monitoring from scratch**, which
removes any old demo container, clears the measurements and rollback counters, and deploys v1.0.
Until then (and for any account that has never started anything) the status endpoint returns zeros,
and the summary counts come from rows tagged with that user's id, so a new account shows 0 deployments,
0 incidents and 0 s average recovery.

## KPI definition (what is measured, over what window, against what threshold)

| Item | Definition |
|------|-----------|
| KPI | Sensor throughput of the demo app, in events processed per minute |
| Measurement window | The last **20 seconds** of processed events, scaled to a minute. For an app connected less than 20 seconds ago, the time since it connected is used (minimum 5 s), so a freshly started app is not mistaken for a slow one |
| Sampling | FactoryGuard samples every **5 seconds** |
| Threshold | **60 events/min** (normal is about 100; the faulty v1.1 gives about 24). Changeable in `.env` |
| Healthy check | Health page answers UP **and** throughput is at or above the threshold |
| Sustained failure | **3 bad checks in a row** (about 15 s) |
| Grace after a deployment | 25 s warm-up, extended up to 75 s while the app has not come up at all |
| Verification after rollback | Same health rule, re-checked after the warm-up, retried every 5 s up to 75 s after detection |

### Not implemented yet (proposed definitions)

These are deliberately left out of the prototype; adding them would be a design change. If added, each needs
the same explicit definition before it is allowed to trigger a rollback:

| Proposed KPI | Proposed measurement | Proposed threshold |
|--------------|----------------------|--------------------|
| Processing latency | 95th percentile of time from message publish to processed, over the same 20 s window | above 2 s for 3 checks in a row |
| Error rate | Failed messages divided by received messages, over the 20 s window | above 5 percent for 3 checks in a row |
| Message backlog | Messages received but not yet processed, sampled each check | above 50 messages for 3 checks in a row |

## Recovery evidence recorded per incident

Failed release, rollback target, detected metric (events/min, threshold and health), failure reason,
detection time (and seconds from deployment to detection), recovery time (detection to verified recovery),
the final verification measurement and its result, and the outcome. All appear in the dashboard's
**Incident timeline**, and `scripts/e2e.py` checks each of them on every run.
