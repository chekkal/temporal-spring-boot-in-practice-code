#!/usr/bin/env bash
# test-all.sh — end-to-end verification of the companion code.
#
# What this script does (in order):
#   1. Prereqs check (java 21+, mvn, docker, curl, nc).
#   2. Start the Temporal infra stack (Postgres + server + UI). Idempotent.
#   3. Wait for Temporal gRPC (7233) and UI (8233).
#   4. Build + unit-test the order-platform reference app.
#   5. Boot order-service, exercise happy-path + payment-decline paths via REST, kill.
#   6. Build + run the agentic-coordination use case end-to-end.
#   7. Build all 6 katas.
#   8. Boot each kata in turn, verify it binds its port (= Spring context + Temporal
#      worker came up cleanly), kill. Workflow bodies are TODOs so we don't exercise
#      business endpoints.
#
# Usage:
#   ./test-all.sh                  # full run (~3-4 minutes first time, ~90s on rebuild)
#   ./test-all.sh --skip-infra     # assume Temporal is already running on :7233
#   ./test-all.sh --skip-katas     # only verify reference app
#   ./test-all.sh --keep-infra     # leave Temporal stack running on exit (default)
#   ./test-all.sh --teardown       # stop the Temporal stack at the end
#
# Logs from each app go to .test-logs/ — inspect there if something fails.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DOCKER_DIR="$SCRIPT_DIR/order-platform/docker"
LOG_DIR="$SCRIPT_DIR/.test-logs"
mkdir -p "$LOG_DIR"

SKIP_INFRA=false
SKIP_KATAS=false
TEARDOWN=false
for arg in "$@"; do
  case "$arg" in
    --skip-infra) SKIP_INFRA=true ;;
    --skip-katas) SKIP_KATAS=true ;;
    --teardown)   TEARDOWN=true ;;
    --keep-infra) TEARDOWN=false ;;
    -h|--help)
      sed -n '3,25p' "$0" | sed 's/^# //; s/^#//'
      exit 0
      ;;
    *) echo "Unknown arg: $arg" >&2; exit 2 ;;
  esac
done

GREEN='\033[0;32m'; RED='\033[0;31m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
ok()     { echo -e "  ${GREEN}\xe2\x9c\x93${NC} $1"; }
xfail()  { echo -e "  ${RED}\xe2\x9c\x97${NC} $1"; exit 1; }
info()   { echo -e "  ${YELLOW}\xe2\x84\xb9${NC}  $1"; }
header() { echo; echo -e "${BLUE}\xe2\x95\x90\xe2\x95\x90 $1 \xe2\x95\x90\xe2\x95\x90${NC}"; }

# Track PIDs of background apps so we can clean up reliably on exit.
APP_PIDS=()
cleanup() {
  for pid in "${APP_PIDS[@]}"; do
    kill "$pid" 2>/dev/null || true
  done
  # Give them a moment to exit cleanly before SIGKILL.
  sleep 1
  for pid in "${APP_PIDS[@]}"; do
    kill -9 "$pid" 2>/dev/null || true
  done
  if [[ "$TEARDOWN" == true ]]; then
    info "Tearing down Temporal stack..."
    ( cd "$DOCKER_DIR" && docker compose down ) >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

# -----------------------------------------------------------------------------
header "Prerequisites"
# -----------------------------------------------------------------------------

for cmd in java mvn docker curl nc; do
  command -v "$cmd" >/dev/null || xfail "$cmd not found in PATH"
done
ok "Tools present: java, mvn, docker, curl, nc"

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"' || true)
if [[ -z "${JAVA_VER:-}" || "$JAVA_VER" -lt 21 ]]; then
  xfail "Java 21+ required (detected: ${JAVA_VER:-unknown})"
fi
ok "Java $JAVA_VER"

docker info >/dev/null 2>&1 || xfail "Docker daemon not reachable"
ok "Docker daemon reachable"

# Application ports must be free before we start anything.
#
# Without this check the failure is baffling: an unrelated service already
# listening on :8080 answers /actuator/health with "UP", the script accepts that
# as "our app booted", and the first POST comes back 403 from somebody else's
# application. Better to name the occupied port up front.
check_port_free() {
  local port=$1 what=$2
  if nc -z localhost "$port" 2>/dev/null; then
    xfail "port $port is already in use, but is needed for $what.
     Stop whatever is listening there and re-run. To find it:  lsof -i:$port"
  fi
}

check_port_free 8080 "order-service"
check_port_free 8090 "use-cases/agentic-coordination"
if [[ "$SKIP_KATAS" == false ]]; then
  for p in 8081 8082 8083 8084 8085 8086; do
    check_port_free "$p" "the kata on :$p"
  done
fi
ok "Application ports are free"

# -----------------------------------------------------------------------------
header "Temporal infrastructure"
# -----------------------------------------------------------------------------

wait_for_port() {
  local port=$1 name=$2 max=${3:-90} elapsed=0
  while ! nc -z localhost "$port" 2>/dev/null; do
    sleep 1; elapsed=$((elapsed + 1))
    if [[ $elapsed -ge $max ]]; then
      xfail "$name not ready on :$port after ${max}s"
    fi
  done
  ok "$name ready on :$port"
}

if [[ "$SKIP_INFRA" == true ]]; then
  info "Skipping infra start (--skip-infra); assuming Temporal is up on :7233"
  wait_for_port 7233 "Temporal gRPC" 10
else
  ( cd "$DOCKER_DIR" && docker compose up -d ) >/dev/null
  ok "docker compose up -d"
  wait_for_port 7233 "Temporal gRPC" 120
  wait_for_port 8233 "Temporal UI"   30
fi

# -----------------------------------------------------------------------------
header "Reference app — build + unit tests"
# -----------------------------------------------------------------------------

( cd "$SCRIPT_DIR/order-platform" && mvn -q -B install ) \
  || { tail -50 "$LOG_DIR/order-service.log" 2>/dev/null || true; \
       xfail "order-platform build/tests failed"; }
ok "mvn install — order-api + order-service (workflow tests passed)"

# -----------------------------------------------------------------------------
header "Reference app — boot + REST smoke test"
# -----------------------------------------------------------------------------

REF_JAR="$SCRIPT_DIR/order-platform/order-service/target/order-service-1.0.0-SNAPSHOT.jar"
[[ -f "$REF_JAR" ]] || xfail "executable jar not produced: $REF_JAR"

# Boot the reference app. Disable banner + reduce log noise for cleaner logs.
java -jar "$REF_JAR" \
    --spring.main.banner-mode=off \
    --logging.level.root=WARN \
    --logging.level.com.example.order=INFO \
  > "$LOG_DIR/order-service.log" 2>&1 &
REF_PID=$!
APP_PIDS+=("$REF_PID")

# Wait for /actuator/health to report UP.
elapsed=0
until curl -fsS "localhost:8080/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; do
  sleep 2; elapsed=$((elapsed + 2))
  if ! kill -0 "$REF_PID" 2>/dev/null; then
    tail -50 "$LOG_DIR/order-service.log"
    xfail "order-service crashed during startup (see log)"
  fi
  if [[ $elapsed -ge 90 ]]; then
    tail -50 "$LOG_DIR/order-service.log"
    xfail "order-service didn't reach UP within 90s"
  fi
done
ok "order-service up on :8080 (actuator UP)"

# Unique order ids per run so reruns don't collide with REJECT_DUPLICATE history.
STAMP=$(date +%s)
HAPPY_ID="smoke-happy-$STAMP"
DECLINE_ID="smoke-decline-$STAMP"

# Happy path: place an order, give the workflow ~3s, then read status.
HAPPY_ORDER='{
  "id":"'"$HAPPY_ID"'",
  "customerId":"c1",
  "items":[{"sku":"A","quantity":1,"unitPrice":{"amount":19.99,"currency":"USD"}}],
  "total":{"amount":19.99,"currency":"USD"},
  "paymentMethod":"card-good",
  "shippingAddress":"1 Demo St",
  "customerEmail":"d@e.com"
}'
curl -fsS -X POST -H 'Content-Type: application/json' \
  -d "$HAPPY_ORDER" "localhost:8080/api/orders" >/dev/null \
  || xfail "POST /api/orders rejected on happy path"
ok "POST /api/orders accepted (happy path)"

sleep 3
HAPPY_STATUS=$(curl -fsS "localhost:8080/api/orders/$HAPPY_ID/status" \
               | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)
case "$HAPPY_STATUS" in
  COMPLETED) ok "happy path reached COMPLETED" ;;
  NOTIFYING|SCHEDULING_SHIPMENT) info "happy path progressing ($HAPPY_STATUS); polling..."; sleep 4
    HAPPY_STATUS=$(curl -fsS "localhost:8080/api/orders/$HAPPY_ID/status" \
                   | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)
    [[ "$HAPPY_STATUS" == "COMPLETED" ]] || xfail "happy path stuck at $HAPPY_STATUS"
    ok "happy path reached COMPLETED" ;;
  *) tail -50 "$LOG_DIR/order-service.log"
     xfail "happy path unexpected status: $HAPPY_STATUS" ;;
esac

# Decline path: payment method "decline" → PaymentDeclined → workflow FAILED.
DECLINE_ORDER=$(echo "$HAPPY_ORDER" | sed "s/$HAPPY_ID/$DECLINE_ID/; s/card-good/card-decline-test/")
curl -fsS -X POST -H 'Content-Type: application/json' \
  -d "$DECLINE_ORDER" "localhost:8080/api/orders" >/dev/null \
  || xfail "POST /api/orders rejected on decline path"
ok "POST /api/orders accepted (decline path)"

sleep 4
DECLINE_STATUS=$(curl -fsS "localhost:8080/api/orders/$DECLINE_ID/status" \
                 | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)
case "$DECLINE_STATUS" in
  FAILED) ok "decline path reached FAILED (compensation path verified)" ;;
  COMPENSATING|AUTHORIZING_PAYMENT) info "decline path in flight ($DECLINE_STATUS); polling..."; sleep 4
    DECLINE_STATUS=$(curl -fsS "localhost:8080/api/orders/$DECLINE_ID/status" \
                     | grep -oE '"status":"[A-Z_]+"' | head -1 | cut -d'"' -f4)
    [[ "$DECLINE_STATUS" == "FAILED" ]] || xfail "decline path stuck at $DECLINE_STATUS"
    ok "decline path reached FAILED" ;;
  *) tail -50 "$LOG_DIR/order-service.log"
     xfail "decline path unexpected status: $DECLINE_STATUS" ;;
esac

# Shut down the reference app cleanly.
kill "$REF_PID" 2>/dev/null || true
wait "$REF_PID" 2>/dev/null || true
APP_PIDS=("${APP_PIDS[@]/$REF_PID}")

# -----------------------------------------------------------------------------
header "Use cases — agentic coordination"
# -----------------------------------------------------------------------------
# Runs before the katas section so --skip-katas does not skip it: this one has a
# real workflow body, so unlike the katas it can be verified end-to-end.

( cd "$SCRIPT_DIR/use-cases/agentic-coordination" && mvn -q -B install ) \
  || xfail "agentic-coordination build failed"
ok "mvn install — agentic-coordination (5 workflow tests, no server needed)"

AGENT_JAR="$SCRIPT_DIR/use-cases/agentic-coordination/target/agentic-coordination-1.0.0-SNAPSHOT.jar"
[[ -f "$AGENT_JAR" ]] || xfail "agentic-coordination: jar not built at $AGENT_JAR"

java -jar "$AGENT_JAR" \
    --spring.main.banner-mode=off \
    --logging.level.root=WARN \
  > "$LOG_DIR/agentic-coordination.log" 2>&1 &
AGENT_PID=$!
APP_PIDS+=("$AGENT_PID")

elapsed=0
while ! nc -z localhost 8090 2>/dev/null; do
  sleep 2; elapsed=$((elapsed + 2))
  if ! kill -0 "$AGENT_PID" 2>/dev/null; then
    tail -30 "$LOG_DIR/agentic-coordination.log"
    xfail "agentic-coordination crashed during startup (see log)"
  fi
  if [[ $elapsed -ge 90 ]]; then
    tail -30 "$LOG_DIR/agentic-coordination.log"
    xfail "agentic-coordination didn't bind :8090 within 90s"
  fi
done
ok "agentic-coordination boots on :8090"

# Drive a full agent run against the live Temporal server.
AGENT_RUN_ID="verify-$$"
curl -sf -X POST localhost:8090/api/agents \
    -H 'Content-Type: application/json' \
    -d "{\"runId\":\"$AGENT_RUN_ID\",\"goal\":\"verify durable agents\",\"budgetMinorUnits\":500}" \
    >/dev/null \
  || xfail "agentic-coordination: could not start an agent run"

elapsed=0
agent_phase=""
while [[ "$agent_phase" != "COMPLETED" ]]; do
  sleep 2; elapsed=$((elapsed + 2))
  agent_phase=$(curl -sf "localhost:8090/api/agents/$AGENT_RUN_ID" \
                | grep -o '"phase":"[^"]*"' | cut -d'"' -f4 || true)
  if [[ $elapsed -ge 60 ]]; then
    tail -30 "$LOG_DIR/agentic-coordination.log"
    xfail "agent run did not complete within 60s (last phase: ${agent_phase:-unknown})"
  fi
done
ok "agent run completed end-to-end — plan, 4 research steps, synthesis"

kill "$AGENT_PID" 2>/dev/null || true
wait "$AGENT_PID" 2>/dev/null || true
APP_PIDS=("${APP_PIDS[@]/$AGENT_PID}")

# -----------------------------------------------------------------------------
if [[ "$SKIP_KATAS" == true ]]; then
  header "All checks passed (katas skipped)"
  info "Temporal UI: http://localhost:8233   |   Logs: $LOG_DIR"
  exit 0
fi
header "Katas — build all 6"
# -----------------------------------------------------------------------------

( cd "$SCRIPT_DIR/katas" && mvn -q -B install ) \
  || xfail "katas build failed"
ok "mvn install — all 6 katas"

# -----------------------------------------------------------------------------
header "Katas — bootup smoke test (workflow bodies are TODOs)"
# -----------------------------------------------------------------------------

KATAS=(
  "kata-01-lost-order:8081"
  "kata-02-compensation-dance:8082"
  "kata-03-approval-bottleneck:8083"
  "kata-04-midnight-migration:8084"
  "kata-05-cascading-failure:8085"
  "kata-06-schema-evolution:8086"
)

for entry in "${KATAS[@]}"; do
  IFS=':' read -r dir port <<< "$entry"
  JAR="$SCRIPT_DIR/katas/$dir/target/$dir-1.0.0-SNAPSHOT.jar"
  [[ -f "$JAR" ]] || xfail "$dir: jar not built at $JAR"

  java -jar "$JAR" \
      --spring.main.banner-mode=off \
      --logging.level.root=WARN \
    > "$LOG_DIR/$dir.log" 2>&1 &
  PID=$!
  APP_PIDS+=("$PID")

  elapsed=0
  while ! nc -z localhost "$port" 2>/dev/null; do
    sleep 2; elapsed=$((elapsed + 2))
    if ! kill -0 "$PID" 2>/dev/null; then
      tail -30 "$LOG_DIR/$dir.log"
      xfail "$dir crashed during startup (see log)"
    fi
    if [[ $elapsed -ge 90 ]]; then
      tail -30 "$LOG_DIR/$dir.log"
      xfail "$dir didn't bind :$port within 90s"
    fi
  done
  ok "$dir boots on :$port (worker registered with Temporal)"

  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
  APP_PIDS=("${APP_PIDS[@]/$PID}")
done

# -----------------------------------------------------------------------------
header "All checks passed"
# -----------------------------------------------------------------------------

echo
info "Summary:"
info "  - Temporal infra started"
info "  - order-platform: build OK, unit tests OK, happy + decline workflows OK"
info "  - use-cases/agentic-coordination: build, 5 tests, full agent run OK"
info "  - katas: all 6 build, boot, and register their worker with Temporal"
echo
info "Temporal UI is at http://localhost:8233"
info "Logs from this run: $LOG_DIR"
if [[ "$TEARDOWN" == false ]]; then
  info "Stop Temporal when you're done:"
  info "    cd $DOCKER_DIR && docker compose down"
fi
