# FactoryGuard: project write-up

**Event:** Visionet Techthon 2026 | **Track:** Cloud Engineering | **Theme:** Manufacturing
**Team:** Cloud Pilot, Sri Shakthi Institute of Engineering and Technology
(Kaarthikeyan B, Gopinath P, Partha Sarathi K)

## The problem
Factories rely on web applications for machine status, dashboards and sensor collection. A faulty
release can leave such an app running yet useless: the page loads, but sensor data is no longer
processed. Small manufacturing teams rarely have a DevOps engineer, so recovery means reading logs,
finding the bad version and redeploying the old one by hand, while operational data is missing.

## The solution
FactoryGuard watches an application **after** each deployment. It judges a release by two things:
an HTTP health check **and** a business signal, sensor throughput. When both stay healthy it keeps
going. When the health check fails, or throughput stays below a configured threshold for several
checks in a row, it records an incident, alerts the team, restores the last known-good version, and
verifies that the restored version works.

## How it works
1. A signed-in user starts monitoring from scratch; FactoryGuard deploys the healthy release v1.0 in a container.
2. A simulator publishes about 100 sensor messages per minute over MQTT to a broker (Mosquitto).
3. The demo app consumes them. Release v1.1 contains a deliberate bug and handles them slowly (about 24/min) while its health page still says UP.
4. Every 5 seconds FactoryGuard checks health and throughput. Three bad checks in a row, after a warm-up period, trigger recovery.
5. Recovery: incident recorded, alert sent (log and optional webhook), the container is replaced with the last known-good image, and health and throughput are checked again. The recovery time is stored.

Safeguards: sustained-failure rule, warm-up after each deployment, rollback only to a version that
already proved healthy, and at most 2 automatic rollbacks per 10 minutes.

## Technology
Java 21 and Spring Boot (monitoring, recovery rules, API, sign-in), PostgreSQL, Docker and Docker
Compose, Eclipse Mosquitto (MQTT), Eclipse Paho Java MQTT client, Spring Boot Actuator, GitHub
Actions (unit tests and an end-to-end rollback test), AWS (EC2, ECR, CloudWatch logs, metrics and alarms, SNS email alerts, CloudFormation; prepared), React dashboard.

## Results
**Not yet measured.** The workflow has been built and its checks written, but no real Docker run has been
recorded yet, so no result is claimed here.

To produce the evidence, start the system (`./run.sh`) and run `python3 scripts/e2e.py --runs 5`. The script
writes **`docs/EVIDENCE.md`** with the measured throughput on v1.0 and v1.1, time to detect, recovery time and the
final verification for every run, or the failure if a run fails. Copy those numbers here only after that file exists.

| Measurement | Value |
|-------------|-------|
| Throughput on v1.0 (events/min) | not yet measured |
| Throughput on v1.1 (events/min) | not yet measured |
| Time to detect (s) | not yet measured |
| Recovery time (s) | not yet measured |
| Runs passed | not yet measured |

## What is original
Rollback decisions based on a business KPI (sensor throughput) rather than process liveness alone,
a recorded incident timeline with measured recovery time, a real MQTT sensor simulator, repeatable
failure tests, and safety limits on automatic recovery.

## Limits
Simulated sensor data and a demonstration app only. It does not replace PLCs, industrial safety
systems or certified factory control infrastructure. One shared demo environment, HTTP only, and the guard controls Docker through the Docker socket (see `docs/SECURITY.md`). Prototype for local demonstration; AWS deployment is prepared but not deployed.

## Next steps
Deploy with the prepared AWS package (CloudFormation, EC2, ECR, CloudWatch Logs, GitHub Actions),
add CloudWatch alarms on throughput, HTTPS with a domain, role-based access, more deployment targets,
and real factory telemetry.
