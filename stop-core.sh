#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_DIR="$ROOT_DIR/run"

stop_service() {
  local name="$1"
  local pid_file="$RUN_DIR/${name}.pid"

  if [[ ! -f "$pid_file" ]]; then
    echo "INFO: $name pid file not found, skip."
    return
  fi

  local pid
  pid="$(cat "$pid_file" || true)"
  if [[ -z "${pid:-}" ]]; then
    echo "WARN: $name pid file is empty, removing: $pid_file"
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

mkdir -p "$RUN_DIR"

# Stop in reverse dependency order.
stop_service "market"
stop_service "order"
stop_service "account"
stop_service "match"
stop_service "driver"

echo "Done."
