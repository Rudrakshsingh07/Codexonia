:<<BASH_SECTION
@echo off
setlocal
cd /d "%~dp0"

where docker >nul 2>nul
if errorlevel 1 (
  echo ERROR: Docker is not installed or not on PATH.
  exit /b 1
)

docker info >nul 2>nul
if errorlevel 1 (
  echo ERROR: Docker is not running. Start Docker Desktop and try again.
  exit /b 1
)

docker compose version >nul 2>nul
if errorlevel 1 (
  echo ERROR: Docker Compose is not available.
  exit /b 1
)

echo Starting Codexonia...
echo Waiting for http://localhost:8080/ ...
start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 180;$i++){ try { Invoke-WebRequest -UseBasicParsing -TimeoutSec 1 'http://localhost:8080/' | Out-Null; Start-Process 'http://localhost:8080/'; break } catch { Start-Sleep -Seconds 1 } }"

docker compose up --build
exit /b %ERRORLEVEL%

BASH_SECTION
#!/bin/sh
# Cross-platform launcher: this file is a batch script for cmd.exe and a POSIX
# shell script for macOS/Linux (and Git Bash/WSL). It starts the Docker stack and
# opens the site once the server answers. Docker cannot open a host GUI browser
# from inside a container, so this small host-side step is the portable part.
set -eu
cd "$(dirname "$0")"

if ! command -v docker >/dev/null 2>&1; then
  echo "ERROR: Docker is not installed or not on PATH." >&2
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "ERROR: Docker is not running. Start Docker Desktop and try again." >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "ERROR: Docker Compose is not available." >&2
  exit 1
fi

URL="${CODEXONIA_URL:-http://localhost:8080/}"

open_browser() {
  if [ -n "${WSL_DISTRO_NAME:-}" ] || grep -qi microsoft /proc/version 2>/dev/null; then
    cmd.exe /c start "" "$1" >/dev/null 2>&1 && return 0
  fi
  case "$(uname -s 2>/dev/null || echo unknown)" in
    Darwin)
      open "$1" >/dev/null 2>&1 || true
      ;;
    Linux)
      if command -v xdg-open >/dev/null 2>&1; then
        xdg-open "$1" >/dev/null 2>&1 || true
      elif command -v sensible-browser >/dev/null 2>&1; then
        sensible-browser "$1" >/dev/null 2>&1 || true
      fi
      ;;
  esac
}

wait_and_open() {
  i=0
  while [ "$i" -lt 180 ]; do
    if command -v curl >/dev/null 2>&1; then
      if curl -fsS -o /dev/null "$URL" 2>/dev/null; then
        open_browser "$URL"
        return 0
      fi
    elif command -v wget >/dev/null 2>&1; then
      if wget -q -O /dev/null "$URL" 2>/dev/null; then
        open_browser "$URL"
        return 0
      fi
    else
      sleep 10
      open_browser "$URL"
      return 0
    fi
    i=$((i + 1))
    sleep 1
  done
}

echo "Starting Codexonia..."
echo "Waiting for $URL ..."
wait_and_open &
OPENER_PID=$!
trap 'kill "$OPENER_PID" 2>/dev/null || true' EXIT INT TERM

docker compose up --build
