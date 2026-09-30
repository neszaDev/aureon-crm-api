#!/bin/sh
# Make sure the Docker engine is reachable before a target that needs it (make verify/smoke/
# watch/up/...). On macOS, starts Docker Desktop and waits; elsewhere, explains what to do.
# Timeout in seconds: DOCKER_START_TIMEOUT (default 90).
docker info >/dev/null 2>&1 && exit 0

if ! command -v docker >/dev/null 2>&1; then
  echo "ensure-docker: docker CLI not found. Install Docker Desktop or Docker Engine." >&2
  exit 1
fi

case "$(uname -s)" in
  Darwin)
    echo "ensure-docker: Docker is not running; starting Docker Desktop..." >&2
    open -a Docker || exit 1
    ;;
  *)
    echo "ensure-docker: Docker is not running. Start it (e.g. 'sudo systemctl start docker')." >&2
    exit 1
    ;;
esac

timeout="${DOCKER_START_TIMEOUT:-90}"
waited=0
until docker info >/dev/null 2>&1; do
  if [ "$waited" -ge "$timeout" ]; then
    echo "ensure-docker: Docker did not become ready within ${timeout}s." >&2
    exit 1
  fi
  sleep 2
  waited=$((waited + 2))
done
echo "ensure-docker: Docker is ready (${waited}s)." >&2
