#!/usr/bin/env bash
# Stops everything. Add the word "reset" to also wipe saved history: ./stop.sh reset
cd "$(dirname "$0")"
docker rm -f demo-app >/dev/null 2>&1 || true
if [ "$1" = "reset" ]; then docker compose down -v; else docker compose down; fi
echo "Stopped."
