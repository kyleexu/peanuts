#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"

usage() {
  cat <<'EOF'
Usage: ./restart-service.sh <service>

Supported services:
  driver
  match
  account
  order
  market
  maker

Example:
  ./restart-service.sh market
EOF
}

jar_path_for_service() {
  local name="$1"
  case "$name" in
    driver) echo "$ROOT_DIR/driver/target/driver-1.0.0-SNAPSHOT.jar" ;;
    match) echo "$ROOT_DIR/match/target/match-1.0.0-SNAPSHOT.jar" ;;
    account) echo "$ROOT_DIR/account/target/account-1.0.0-SNAPSHOT.jar" ;;
    order) echo "$ROOT_DIR/order/target/order-1.0.0-SNAPSHOT.jar" ;;
    market) echo "$ROOT_DIR/market/target/market-1.0.0-SNAPSHOT.jar" ;;
    maker) echo "$ROOT_DIR/maker/target/maker-1.0.0-SNAPSHOT.jar" ;;
    *) return 1 ;;
  esac
}

stop_service() {
  local name="$1"
  local pid_file="$RUN_DIR/${name}.pid"

  if [[ ! -f "$pid_file" ]]; then
    echo "INFO: $name pid file not found, skip stop."
    return
  fi

  local pid
  pid="$(cat "$pid_file" || true)"
  if [[ -z "${pid:-}" ]]; then
    echo "WARN: $name pid file is empty, removing stale file."
    rm -f "$pid_file"
    return
  fi

  if ! kill -0 "$pid" 2>/dev/null; then
    echo "INFO: $name not running (pid=$pid), removing stale pid file."
    rm -f "$pid_file"
    return
  fi

  echo "Stopping $name (pid=$pid) ..."
  kill "$pid" 2>/dev/null || true

  for _ in {1..20}; do
    if ! kill -0 "$pid" 2>/dev/null; then
      rm -f "$pid_file"
      echo "$name stopped."
      return
    fi
    sleep 0.5
  done

  echo "WARN: $name did not exit in time, force killing (pid=$pid)."
  kill -9 "$pid" 2>/dev/null || true
  rm -f "$pid_file"
  echo "$name killed."
}

start_service() {
  local name="$1"
  local jar_path="$2"
  local log_file="$LOG_DIR/${name}.log"
  local pid_file="$RUN_DIR/${name}.pid"

  if [[ ! -f "$jar_path" ]]; then
    echo "ERROR: Jar not found: $jar_path"
    echo "Hint: build it first, e.g. mvn -pl $name -am -DskipTests package"
    exit 1
  fi

  echo "Starting $name ..."
  nohup java -jar "$jar_path" >"$log_file" 2>&1 &
  local new_pid=$!
  echo "$new_pid" >"$pid_file"
  echo "$name started (pid=$new_pid), log=$log_file"
}

check_service_health() {
  local name="$1"
  local timeout_sec="${2:-20}"
  local pid_file="$RUN_DIR/${name}.pid"
  local log_file="$LOG_DIR/${name}.log"
  local elapsed=0

  while (( elapsed < timeout_sec )); do
    local pid
    pid="$(cat "$pid_file" 2>/dev/null || true)"

    if [[ -z "${pid:-}" ]] || ! kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: $name process is not running."
      [[ -f "$log_file" ]] && tail -n 80 "$log_file" || true
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

SERVICE="${1:-}"
if [[ -z "$SERVICE" || "$SERVICE" == "-h" || "$SERVICE" == "--help" ]]; then
  usage
  exit 1
fi

mkdir -p "$LOG_DIR" "$RUN_DIR"

if ! JAR_PATH="$(jar_path_for_service "$SERVICE")"; then
  echo "ERROR: Unsupported service: $SERVICE"
  usage
  exit 1
fi

echo "Restarting service: $SERVICE"
stop_service "$SERVICE"
start_service "$SERVICE" "$JAR_PATH"
check_service_health "$SERVICE" 20

echo "Done."
