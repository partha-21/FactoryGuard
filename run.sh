#!/usr/bin/env bash
# One-command start for Mac / Linux.
set -e
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker is not installed. Install Docker Desktop first: https://www.docker.com/products/docker-desktop/"
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "Docker is installed but not running. Open Docker Desktop, wait until it says it is running, then try again."
  exit 1
fi

echo "Step 1/3: building the two demo app versions (first run takes a few minutes)..."
docker compose --profile images build
echo "Step 2/3: starting FactoryGuard, the MQTT broker, the database and the sensor simulator..."
docker compose up -d --build
echo "Step 3/3: waiting for the dashboard to start (up to 2 minutes)..."
if command -v curl >/dev/null 2>&1; then
  for i in $(seq 1 60); do
    if curl -fs http://localhost:3000/login.html >/dev/null 2>&1; then break; fi
    sleep 2
  done
fi
echo "Ready."
echo
echo "  Open the dashboard:  http://localhost:3000"
echo "  Demo app (once up):  http://localhost:8081/api/stats"
echo "  Stop everything:     ./stop.sh"
