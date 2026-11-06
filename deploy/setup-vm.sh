#!/usr/bin/env bash
# Sets up FactoryGuard on a fresh Ubuntu/Debian Linux server (any cloud, any VM).
# Run it ON the server, inside the unzipped project folder:   bash deploy/setup-vm.sh
# It installs Docker if needed, then builds and starts everything. Use a server with at least 2 GB of memory.
set -e
cd "$(dirname "$0")/.."

# Small servers: add swap so building the Java images does not run out of memory
if [ "$(free -m | awk '/^Mem:/{print $2}')" -lt 3000 ] && [ ! -f /swapfile ]; then
  echo "Adding 2 GB of swap..."
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

if ! command -v docker >/dev/null 2>&1; then
  echo "Installing Docker..."
  curl -fsSL https://get.docker.com | sudo sh
  sudo usermod -aG docker "$USER" || true
fi

# Use sudo for docker if the current shell is not in the docker group yet
DOCKER="docker"
if ! docker info >/dev/null 2>&1; then DOCKER="sudo docker"; fi

# This server is meant to be reached from your own computer, so listen on all addresses.
# The cloud firewall must then allow port 3000 from YOUR IP ONLY (see docs/SECURITY.md).
touch .env
if grep -q '^GUARD_BIND=' .env; then sed -i 's/^GUARD_BIND=.*/GUARD_BIND=0.0.0.0/' .env; else echo 'GUARD_BIND=0.0.0.0' >> .env; fi

echo "Building the demo app versions..."
$DOCKER compose --profile images build
echo "Starting FactoryGuard..."
$DOCKER compose up -d --build

IP=$(curl -fsS https://api.ipify.org 2>/dev/null || hostname -I | awk '{print $1}')
echo
echo "Done. Open  http://$IP:3000  (allow inbound TCP port 3000 in the server's firewall / security rules)."
echo "Do NOT open port 1883. The dashboard has no HTTPS, so restrict port 3000 to your own IP address."
