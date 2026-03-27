#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"

mkdir -p "$LOG_DIR" "$RUN_DIR"

echo "[0/7] Stopping existing services (for restart)"
if [[ -x "$ROOT_DIR/stop-core.sh" ]]; then
  "$ROOT_DIR/stop-core.sh" || true
else
  echo "WARN: stop-core.sh not found or not executable, skip stop step."
fi

echo "[0.5/7] Clearing old logs"
rm -f "$LOG_DIR"/*.log

echo "[1/7] Building project: mvn clean package -U"
(cd "$ROOT_DIR" && mvn clean package -U)

start_service() {
  local name="$1"
  local jar_path="$2"
  local log_file="$LOG_DIR/${name}.log"
  local pid_file="$RUN_DIR/${name}.pid"

  if [[ ! -f "$jar_path" ]]; then
    echo "ERROR: Jar not found: $jar_path"
    exit 1
  fi

  if [[ -f "$pid_file" ]]; then
    local old_pid
    old_pid="$(cat "$pid_file" || true)"
    if [[ -n "${old_pid:-}" ]] && kill -0 "$old_pid" 2>/dev/null; then
      echo "WARN: $name is already running (pid=$old_pid), skip start."
      return
    fi
  fi

  echo "Starting $name ..."
  nohup java -jar "$jar_path" >"$log_file" 2>&1 &
  local new_pid=$!
  echo "$new_pid" >"$pid_file"
  echo "$name started (pid=$new_pid), log=$log_file"
}

check_service_health() {
  local name="$1"
  local pid_file="$RUN_DIR/${name}.pid"
  local log_file="$LOG_DIR/${name}.log"
  local timeout_sec="${2:-20}"
  local elapsed=0

  while (( elapsed < timeout_sec )); do
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"
    if [[ -z "${pid:-}" ]] || ! kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: $name process is not running."
      [[ -f "$log_file" ]] && tail -n 60 "$log_file" || true
      exit 1
    fi

    if [[ -f "$log_file" ]] && grep -E -q "APPLICATION FAILED TO START|Error starting ApplicationContext|Exception" "$log_file"; then
      echo "ERROR: $name failed during startup."
      tail -n 120 "$log_file" || true
      exit 1
    fi

    sleep 1
    elapsed=$((elapsed + 1))
  done

  echo "Health check passed: $name"
}

echo "[2/7] Starting driver"
start_service "driver" "$ROOT_DIR/driver/target/driver-1.0.0-SNAPSHOT.jar"
check_service_health "driver" 20
sleep 2

echo "[3/7] Starting match"
start_service "match" "$ROOT_DIR/match/target/match-1.0.0-SNAPSHOT.jar"
check_service_health "match" 20
sleep 2

echo "[4/7] Starting account"
start_service "account" "$ROOT_DIR/account/target/account-1.0.0-SNAPSHOT.jar"
check_service_health "account" 20
sleep 2

echo "[5/7] Starting order"
start_service "order" "$ROOT_DIR/order/target/order-1.0.0-SNAPSHOT.jar"
check_service_health "order" 20
sleep 2

echo "[6/7] Starting market"
start_service "market" "$ROOT_DIR/market/target/market-1.0.0-SNAPSHOT.jar"
check_service_health "market" 20
sleep 2

echo "[7/7] Starting maker"
start_service "maker" "$ROOT_DIR/maker/target/maker-1.0.0-SNAPSHOT.jar"
check_service_health "maker" 20

echo ""
echo "Done. Check logs:"
echo "  tail -f \"$LOG_DIR/driver.log\""
echo "  tail -f \"$LOG_DIR/match.log\""
echo "  tail -f \"$LOG_DIR/account.log\""
echo "  tail -f \"$LOG_DIR/order.log\""
echo "  tail -f \"$LOG_DIR/market.log\""
echo "  tail -f \"$LOG_DIR/maker.log\""
