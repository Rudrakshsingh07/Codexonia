#!/usr/bin/env bash
# Launches the full Codexonia stack: sandbox image + Spring Boot web app,
# then opens the Submit Code page in the default browser.
set -euo pipefail
cd "$(dirname "$0")"

MAXVN="$HOME/tools/apache-maven-3.9.9/bin"
command -v mvn >/dev/null 2>&1 || export PATH="$MAXVN:$PATH"
command -v mvn >/dev/null 2>&1 || { echo "ERROR: Maven not found."; exit 1; }

# 1. Sandbox image (needed by RunnerEngine)
if ! docker image inspect codexonia-sandbox:latest >/dev/null 2>&1; then
  echo "Building codexonia-sandbox:latest ..."
  docker build -t codexonia-sandbox:latest -f sandbox/Dockerfile .
fi

# 2. Start the web app
echo "Starting Codexonia on http://localhost:8080 ..."
mvn -q spring-boot:run &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT INT TERM

# 3. Wait for the server, then open the browser
for _ in $(seq 1 60); do
  curl -sf -o /dev/null http://localhost:8080/ && break
  sleep 1
done
xdg-open http://localhost:8080/ >/dev/null 2>&1 || true

wait $SERVER_PID
