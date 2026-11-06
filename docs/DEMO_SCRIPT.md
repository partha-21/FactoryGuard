# Demo script (about 4 minutes)

Before you present: run `./run.sh` (or `run.bat`) once so everything is built, sign in, and keep the
dashboard open at http://localhost:3000. Do a dry run first. Optional safety net:
`python3 scripts/e2e.py` plays the whole story automatically and prints the real recovery time.

## Talk track

| Time | What you do | What you say |
|------|-------------|--------------|
| 0:00 | Show the sign-in page, create a new account | "Every new account starts from zero. Nothing is faked: no deployments, no incidents." |
| 0:30 | Point at the zero tiles and the empty chart | "A factory app is not running yet. FactoryGuard has nothing to protect." |
| 0:45 | Click **Start monitoring from scratch** | "This deploys a healthy v1.0 in a container. A simulator sends about 100 sensor messages a minute over MQTT." |
| 1:30 | Status turns **Healthy**; show throughput near 100 and the belt moving | "Healthy means two things: the health page answers, and sensor throughput is above 60 a minute." |
| 1:45 | Click **Deploy v1.1 (has a bug)** | "This is a bad release. It starts fine, the health page says UP, but it handles sensor data very slowly." |
| 2:15 | Watch the line drop and the status go **Degraded 1/3, 2/3, 3/3** | "A container check alone would call this healthy. We also watch the business signal: sensor throughput. We wait for three bad checks in a row, so one blip does not trigger anything." |
| 2:45 | Status goes **Rolling back**, then **Healthy** again | "FactoryGuard records the incident, alerts the team, restores v1.0 and checks again before calling it recovered." |
| 3:30 | Scroll to **Incidents** and **Deployments** | "Here is the full story: the release, the reason, the numbers, what we did, and how many seconds recovery took. This is a real measurement from this run." |
| 3:50 | Close | "Safety limits: at most two automatic rollbacks in ten minutes, and it only rolls back to a version that already proved healthy." |

## Your measured numbers

Do not quote numbers you have not measured. Run `python3 scripts/e2e.py --runs 5` after `./run.sh`; it writes
`docs/EVIDENCE.md` with throughput on v1.0 and v1.1, time to detect, recovery time and the verification result
for each run. Quote those, and show the Incident timeline on the dashboard during the demo.

## Questions judges may ask

**Why not just use Docker's own health check?** It only knows if the process answers. v1.1 answers
perfectly and is still broken. FactoryGuard also measures what the app is supposed to deliver.

**Is the factory data real?** No. It is simulated on purpose. FactoryGuard manages a demonstration
app and does not replace PLCs or safety systems.

**What stops it from rolling back forever?** A sustained-failure rule (three bad checks in a row),
a warm-up period after each deployment, and a limit of two automatic rollbacks per ten minutes.

**Where does the cloud come in?** Everything runs in containers, so the same package runs on a laptop
or on a cloud server. An AWS package is prepared (EC2 for the app, ECR for versioned images, CloudWatch for logs, CloudFormation to create it); if you have run it, say so and show the CloudWatch log group. If not, say it is prepared but not yet deployed.

**What would you add next?** Run the prepared AWS pipeline, CloudWatch alarms on throughput,
HTTPS with a domain, role-based access, and real factory telemetry.
