#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"

mkdir -p "$LOG_DIR" "$RUN_DIR"

echo "[0/4] Stopping existing services (for restart)"
if [[ -x "$ROOT_DIR/stop-core.sh" ]]; then
  "$ROOT_DIR/stop-core.sh" || true
else
  echo "WARN: stop-core.sh not found or not executable, skip stop step."
fi

echo "[0.5/4] Clearing old logs"
rm -f "$LOG_DIR"/*.log

echo "[1/4] Building project: mvn clean package -U"
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

echo "[2/4] Starting match"
start_service "match" "$ROOT_DIR/match/target/match-1.0.0-SNAPSHOT.jar"
sleep 2

echo "[3/4] Starting account"
start_service "account" "$ROOT_DIR/account/target/account-1.0.0-SNAPSHOT.jar"
sleep 2

echo "[4/4] Starting order"
start_service "order" "$ROOT_DIR/order/target/order-1.0.0-SNAPSHOT.jar"

echo ""
echo "Done. Check logs:"
echo "  tail -f \"$LOG_DIR/match.log\""
echo "  tail -f \"$LOG_DIR/account.log\""
echo "  tail -f \"$LOG_DIR/order.log\""
