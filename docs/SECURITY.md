# Security notes and limits

FactoryGuard is a **prototype for a local demonstration**. These notes say what is protected, what is not,
and what must change before anything is exposed to other people or the internet.

## 1. The Docker socket (the most important risk)

The `guard` container mounts `/var/run/docker.sock`. That is how it starts and replaces the demo app, and it is
also **root-equivalent control of the Docker host**. Anyone who could make FactoryGuard run arbitrary Docker
commands could take over the machine.

What the prototype does about it:

- The dashboard and the demo app listen on **127.0.0.1 only** by default (`GUARD_BIND=127.0.0.1`), so only the
  computer running them can reach them.
- FactoryGuard only runs a fixed set of Docker commands (`image inspect`, `inspect`, `rm -f`, `run`) with a fixed
  image name. The only caller-supplied value is the version, and it must match `v<number>.<number>`
  (anything else, including shell characters, is rejected). No shell is involved.
- Every deploy needs a signed-in session.

What it does **not** do: limit what a compromised FactoryGuard could do with the socket. A hardened design would
put a restricted Docker proxy or a purpose-built deployment agent in between. That is deliberately not part of this
prototype.

**Rule: keep it on a laptop or an isolated test machine. Do not expose the dashboard to the internet.**

## 2. Network exposure

- Default: localhost only. To reach it from another computer you must set `GUARD_BIND=0.0.0.0` yourself
  (`deploy/setup-vm.sh` does this for cloud servers).
- If you do, the cloud firewall must allow port 3000 from **your own IP only**.
- Never open the MQTT port (1883) or the demo app's port (8081). The broker allows anonymous connections.

## 3. Accounts, sessions and HTTPS

- Passwords are stored as BCrypt hashes. Sessions are random tokens stored hashed, valid 7 days, sent in an
  HttpOnly, SameSite=Lax cookie. Five failed sign-ins lock that username for five minutes.
- The prototype serves **plain HTTP**. On plain HTTP, passwords and session cookies can be read by anyone on the
  network path. That is acceptable on `localhost` only.
- **Before any public or shared-network use** you need HTTPS in front of FactoryGuard (for example a reverse
  proxy or cloud load balancer with a certificate), and `GUARD_COOKIE_SECURE=true` so the cookie is marked
  `Secure`. Neither is included.
- No password reset, no email verification, no roles.

## 4. One shared environment (user isolation)

There is one demo app and one set of recovery state, shared by everyone who can sign in. What is isolated per
user: their deployment and incident history and their summary numbers. What is not: the running environment itself.

To avoid two people disturbing each other during a demo, **Start monitoring** is refused while another user's
session has been active in the last 10 minutes; after 10 minutes of inactivity it may be taken over. This is a
simple safeguard, not real multi-tenancy. For a presentation, use one account.

## 5. Recovery state after a restart

The running version, last known-good version, owner, deployment time and rollback counter are stored in the
database. After restarting FactoryGuard it resumes only if the demo app container is still running the saved
version; otherwise it starts clean. Incidents still open at the moment of a restart are marked `INTERRUPTED`.

## 6. Other

- Default database password is a development value (`.env`). Change `POSTGRES_PASSWORD` for anything non-local.
- The simulator and all sensor data are fictional. FactoryGuard does not replace PLCs or industrial safety systems.
