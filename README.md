# FactoryGuard

FactoryGuard watches a web application after a new version is released. If the new version
breaks (for example, sensor readings stop being processed), FactoryGuard notices, records the
incident, alerts the team, and **automatically goes back to the last version that worked**.

This project is a demonstration: the "factory" is simulated, no real machines are involved.

**You do not need to know anything about cloud, Docker or MQTT to run it.** Follow the steps below.

**Status:** the build is complete. Results are pending the first real run: no measured numbers are claimed
anywhere until `python3 scripts/e2e.py --runs 5` has produced `docs/EVIDENCE.md` on a live stack.
The AWS package is prepared, not deployed. See [docs/FINAL_CHECKLIST.md](docs/FINAL_CHECKLIST.md).

---

## What you need (one-time)

Just one program: **Docker Desktop** (free for learning and small teams).

1. Download it from https://www.docker.com/products/docker-desktop/ and install it.
2. Open Docker Desktop and wait until it says it is running (a green indicator at the bottom left).
3. Windows users: if Docker asks to enable **WSL 2**, accept and restart when asked.

You do **not** need to install Java, Maven, PostgreSQL or an MQTT broker. Docker handles all of it.

## Run it (3 steps)

1. Unzip this folder anywhere.
2. Start it:
   - **Windows:** double-click `run.bat`
   - **Mac / Linux:** open a terminal in this folder and run `./run.sh`
3. Open **http://localhost:3000** in your browser.

The first start takes several minutes because Docker downloads and builds everything.
Later starts take seconds.

## The demo (about 3 minutes)

1. Open **http://localhost:3000**. You land on the sign-in page.
2. Click **Create account**, choose a username, email and password (8+ characters), and continue.
3. A new account starts at **zero**: no deployments, no incidents, 0 events per minute. Nothing is running yet.
4. Click **Start monitoring from scratch**. FactoryGuard deploys the healthy release **v1.0**.
   Wait about 30 seconds for the status to turn **Healthy** (the sensor rate settles near 100 per minute).
5. Click **Deploy v1.1 (has a bug)**. After a warm-up the rate falls to about 24 per minute.
   The app still looks "alive", but it is not doing its job.
6. After a few bad checks in a row, FactoryGuard:
   - records an **incident** with the reason and the numbers,
   - **alerts** (log line, plus an optional webhook),
   - **rolls back** to v1.0,
   - waits, **checks again**, and writes down how long recovery took.
7. The **Incidents** table shows the result (`RECOVERED`) and the recovery time in seconds.

Those recovery numbers are real measurements from your own run. Use them in your presentation.

**Start over at any time:** **Stop and reset to zero** clears the live status. **Restart from scratch**
stops everything and deploys a fresh v1.0. Each account only sees its own history, so a second
new account also starts at zero.

## Stop it

- **Windows:** double-click `stop.bat`
- **Mac / Linux:** `./stop.sh` (use `./stop.sh reset` to also wipe saved history)

---

## What is what, in plain English

| Word | What it means here |
|------|--------------------|
| **Docker / container** | A way to package a program with everything it needs, so it runs the same on any computer. Each part of this project runs in its own container. |
| **Docker Compose** | A file (`docker-compose.yml`) that starts all the containers together. |
| **MQTT** | A very lightweight messaging system used by machines and sensors. A "broker" (Mosquitto) is like a post office: senders drop messages on a *topic*, receivers pick them up. |
| **Cloud** | Someone else's computers that you rent. For this project it is just a normal computer (a "virtual machine") running Docker. Running on your laptop works exactly the same. |
| **Release / version** | A numbered build of the app. Here: v1.0 (good) and v1.1 (deliberately broken). |
| **Rollback** | Going back to the previous working version. |
| **Throughput** | How many sensor messages the app handles per minute. |

## The parts

| Container | Job |
|-----------|-----|
| `mosquitto` | MQTT broker that carries the sensor messages |
| `simulator` | Pretends to be factory machines, sends about 100 readings a minute |
| `demo-app` (v1.0 / v1.1) | The factory monitoring app being protected. v1.1 contains the bug. Started and replaced by FactoryGuard. |
| `guard` | **FactoryGuard**: sign-in, monitors, decides, rolls back, and serves the dashboard |
| `postgres` | Database for accounts, deployment history and incidents |

How the decision is made is explained in [docs/HOW_IT_WORKS.md](docs/HOW_IT_WORKS.md).
The dashboard is a React app; its compiled bundle is included, so you do not need Node.js
(see [dashboard/README.md](dashboard/README.md) only if you want to change it).

## Useful addresses

- Dashboard (sign-in required): http://localhost:3000
- Demo app stats (this computer only): http://localhost:8081/api/stats
- Demo app health: http://localhost:8081/actuator/health

## Changing the rules

Open the `.env` file in a text editor. For example, change `GUARD_THRESHOLD` or
`MESSAGES_PER_MINUTE`, save, then run the start script again.

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Docker is not installed" or "not running" | Install and open Docker Desktop, wait until it says it is running, try again. |
| First start is slow | Normal. Docker is downloading and building. Wait for it to finish. |
| "port is already allocated" | Something else uses port 3000, 8081 or 1883. Close it, or change the left-hand number in `docker-compose.yml` (for example `"3001:8080"`). |
| Dashboard says `offline` | Give it a minute after starting. Then check logs: `docker compose logs guard` |
| Status stays `WAITING FOR DEPLOYMENT` | Run the start script again; it rebuilds the demo app images. |
| Want a totally fresh start | `./stop.sh reset` (or `docker compose down -v`), then start again. |
| See the raw MQTT messages (optional) | `docker compose exec mosquitto mosquitto_sub -t factory/sensors -C 5` |

---


## Check that everything works (optional, automatic)

With the stack running (`./run.sh`), this plays the whole story by itself: new account at zero,
start from scratch, deploy the buggy v1.1, wait for the automatic rollback, and print the real
recovery time. You only need Python 3.

    python3 scripts/e2e.py --runs 5

It also writes `docs/EVIDENCE.md` with the numbers it measured (throughput, time to detect, recovery time,
final verification). Only a file produced by a real run counts as evidence.

The same check runs on GitHub for every push (`.github/workflows/ci.yml`), together with the unit tests.

## Running it on AWS

Two paths, both described step by step in [docs/AWS_DEPLOY.md](docs/AWS_DEPLOY.md):

- **Path A (simple, works on student and lab accounts):** launch one Ubuntu server, upload this folder, run
  `bash deploy/setup-vm.sh`. No IAM users or roles needed.
- **Path B (automated):** a CloudFormation template (`deploy/aws/factoryguard-stack.yaml`) creates the server,
  ECR image repositories, CloudWatch logs, metrics and alarms with email alerts; a GitHub Actions workflow
  (`.github/workflows/deploy-aws.yml`) builds, pushes and deploys.

The AWS package has **not been tested** because it needs a real account. It uses your credits while it
runs: set a billing alert, stop the server when idle, and delete everything when finished.

## Running it on any other Linux server

Any Linux server with a public address works (another cloud, a college lab machine, a cheap VPS):

1. Copy this folder to the server and unzip it.
2. Run `bash deploy/setup-vm.sh`. It installs Docker if needed, builds everything and starts it. Use a
   server with at least 2 GB of memory.
3. In the server's firewall allow inbound TCP **3000** from your own IP only. Never open port 1883.
4. Open `http://<server-address>:3000`.

For the presentation script and likely judge questions, see [docs/DEMO_SCRIPT.md](docs/DEMO_SCRIPT.md).
To finish the project in order, follow [docs/FINAL_CHECKLIST.md](docs/FINAL_CHECKLIST.md). Read [docs/SECURITY.md](docs/SECURITY.md) before exposing it to anyone else.

## Scope and limits

- Uses simulated sensor data and a demonstration app only.
- It does not replace industrial safety systems, PLCs or certified factory control infrastructure.
- It is a **prototype for local demonstration**. The dashboard listens on this computer only by default.
  The `guard` container controls Docker through the Docker socket, which is powerful: keep it on a laptop or
  isolated test machine and never expose it to the internet. Details and what must change first: [docs/SECURITY.md](docs/SECURITY.md).
- Accounts are real (BCrypt-hashed passwords, 7-day session cookie, lock-out after 5 failed sign-ins), but the app runs on
  plain HTTP and **one** demo environment is shared by all accounts. Use HTTPS before any shared use.
- FactoryGuard's recovery state (running version, last known-good version, rollback counter) is stored in the database
  and survives a restart.
- Forgotten passwords cannot be reset. Create a new account or run `./stop.sh reset` to wipe all data.

flowchart TB
    %% Styles
    classDef frontend fill:#0f172a,stroke:#3b82f6,stroke-width:2px,color:#e2e8f0
    classDef backend  fill:#052e16,stroke:#22c55e,stroke-width:2px,color:#e2e8f0
    classDef data     fill:#1e1b4b,stroke:#818cf8,stroke-width:2px,color:#e2e8f0
    classDef mqtt     fill:#1c1917,stroke:#f59e0b,stroke-width:2px,color:#e2e8f0
    classDef app      fill:#171717,stroke:#64748b,stroke-width:2px,color:#e2e8f0

## FlowChart
<img width="901" height="708" alt="image" src="https://github.com/user-attachments/assets/b551d9cb-0005-407a-a14d-fae000dfeeba" />

## Architecture:
┌─────────────────────────────────────────────────────┐
│           Factory Guard Frontend                    │
│           React / Web Dashboard                     │
│           (served by Guard on :3000)                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 Guard Backend                       │
│     Spring Boot (Java 21)                           │
│     • Authentication & sessions                     │
│     • Health + throughput monitoring (every 5s)     │
│     • Incident recording + alerts                   │
│     • Automatic rollback via Docker socket          │
└──────────┬──────────────────────────┬───────────────┘
           │                          │
           ▼                          ▼
┌─────────────────────┐     ┌─────────────────────────┐
│     Database        │     │      Messaging          │
│     PostgreSQL      │     │   Mosquitto (MQTT)      │
│  accounts, history, │     │                         │
│  incidents, state   │     │                         │
└─────────────────────┘     └────────────┬────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    │                    │                    │
                    ▼                    ▼                    ▼
           ┌──────────────┐     ┌────────────────┐   ┌─────────────────┐
           │  Simulator   │     │   Demo App     │   │   Demo App      │
           │  (Java)      │────►│   v1.0         │   │   v1.1 (buggy)  │
           │  ~100 msg/min│ MQTT│  (healthy)     │   │  (low throughput)│
           └──────────────┘     └────────────────┘   └─────────────────┘

    

    
