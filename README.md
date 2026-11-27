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

**Start over at any time:**  
**Stop and reset to zero** clears the live status.  
**Restart from scratch** stops everything and deploys a fresh v1.0.  
Each account only sees its own history, so a second new account also starts at zero.

## Stop it

- **Windows:** double-click `stop.bat`
- **Mac / Linux:** `./stop.sh` (use `./stop.sh reset` to also wipe saved history)

---

## Architecture

```text
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
