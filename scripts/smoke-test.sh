#!/usr/bin/env bash
# Deployment-readiness smoke test: run the runtime image with the *prod* profile against a
# throwaway PostgreSQL and require /actuator/health UP plus JSON logs.
# Same script locally (`make smoke`) and in CI. Usage: scripts/smoke-test.sh <image>
set -euo pipefail

IMAGE="${1:?usage: $0 <image>}"
PORT="${SMOKE_PORT:-18080}"
RUN="smoke-$$"
LOG_DIR="target"

cleanup() {
  mkdir -p "$LOG_DIR"
  docker logs "$RUN-app" >"$LOG_DIR/smoke-app.log" 2>&1 || true
  docker rm -f "$RUN-app" "$RUN-db" >/dev/null 2>&1 || true
  docker network rm "$RUN" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  echo "smoke: FAIL - $*" >&2
  docker logs --tail 50 "$RUN-app" >&2 || true
  exit 1
}

docker network create "$RUN" >/dev/null
docker run -d --name "$RUN-db" --network "$RUN" \
  -e POSTGRES_DB=app -e POSTGRES_USER=app -e POSTGRES_PASSWORD=smoke \
  postgres:17-alpine >/dev/null

for _ in $(seq 1 30); do
  docker exec "$RUN-db" pg_isready -U app -d app >/dev/null 2>&1 && break
  sleep 1
done

# Real prod profile: no defaults for secrets, so every required variable is set explicitly.
docker run -d --name "$RUN-app" --network "$RUN" -p "$PORT:8080" \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL="jdbc:postgresql://$RUN-db:5432/app" -e DB_USERNAME=app -e DB_PASSWORD=smoke \
  -e JWT_SECRET="$(openssl rand -hex 32)" \
  -e CORS_ALLOWED_ORIGINS=http://localhost \
  "$IMAGE" >/dev/null

health=""
for _ in $(seq 1 60); do
  [ "$(docker inspect -f '{{.State.Running}}' "$RUN-app")" = true ] || fail "container exited"
  health=$(curl -fsS "http://localhost:$PORT/actuator/health" 2>/dev/null || true)
  case "$health" in *'"status":"UP"'*) break ;; esac
  sleep 2
done
case "$health" in *'"status":"UP"'*) ;; *) fail "health never reported UP (last: ${health:-no response})" ;; esac

logs=$(docker logs "$RUN-app" 2>&1)
if printf '%s\n' "$logs" | grep -q 'ERROR in ch.qos.logback'; then fail "logback configuration error"; fi
json_lines=$(printf '%s\n' "$logs" | grep -c '^{"@timestamp"' || true)
[ "$json_lines" -gt 0 ] || fail "prod logs are not JSON"

# Prod must not expose the generic metrics endpoint (Prometheus scraping stays available).
code=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$PORT/actuator/metrics")
[ "$code" != 200 ] || fail "/actuator/metrics is publicly exposed in prod"

echo "smoke: OK - health UP, $json_lines JSON log lines, metrics endpoint not public"
