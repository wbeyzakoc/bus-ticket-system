#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_DIR="$ROOT_DIR/backend-app"
LOCAL_ENV_FILE="$ROOT_DIR/.env.local"

BACKEND_PID_FILE="$RUN_DIR/backend.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend.pid"
BACKEND_LOG_FILE="$RUN_DIR/backend.log"
FRONTEND_LOG_FILE="$RUN_DIR/frontend.log"

BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/cities"
FRONTEND_URL="http://127.0.0.1:8000/index.html"
DB_HOST="127.0.0.1"
DB_PORT="3306"
DOCKER_COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
DB_SERVICE_NAME="db"

mkdir -p "$RUN_DIR"

load_local_env() {
  if [[ -f "$LOCAL_ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$LOCAL_ENV_FILE"
    set +a
  fi
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/dev.sh up
  ./scripts/dev.sh down
  ./scripts/dev.sh restart
  ./scripts/dev.sh status
EOF
}

resolve_java_home() {
  if [[ -n "${JAVA_HOME:-}" ]]; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    /usr/libexec/java_home -v 21 2>/dev/null && return 0
    /usr/libexec/java_home -v 23 2>/dev/null && return 0
    /usr/libexec/java_home 2>/dev/null && return 0
  fi

  return 1
}

docker_compose_cmd() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    docker compose "$@"
    return 0
  fi

  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
    return 0
  fi

  return 1
}

is_pid_running() {
  local pid="$1"
  kill -0 "$pid" >/dev/null 2>&1
}

pid_from_file() {
  local pid_file="$1"
  if [[ -f "$pid_file" ]]; then
    tr -d '[:space:]' <"$pid_file"
  fi
}

is_url_ready() {
  local url="$1"
  curl -fsS --max-time 2 "$url" >/dev/null 2>&1
}

is_port_open() {
  local host="$1"
  local port="$2"
  if command -v nc >/dev/null 2>&1; then
    nc -z "$host" "$port" >/dev/null 2>&1
    return $?
  fi
  return 1
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local timeout="${3:-60}"

  for ((i = 1; i <= timeout; i++)); do
    if is_url_ready "$url"; then
      return 0
    fi
    sleep 1
  done

  printf '%s did not become ready within %ss.\n' "$label" "$timeout" >&2
  return 1
}

wait_for_port() {
  local host="$1"
  local port="$2"
  local label="$3"
  local timeout="${4:-60}"

  for ((i = 1; i <= timeout; i++)); do
    if is_port_open "$host" "$port"; then
      return 0
    fi
    sleep 1
  done

  printf '%s did not become ready on %s:%s within %ss.\n' "$label" "$host" "$port" "$timeout" >&2
  return 1
}

start_database() {
  if is_port_open "$DB_HOST" "$DB_PORT"; then
    printf 'Database already reachable at %s:%s\n' "$DB_HOST" "$DB_PORT"
    return 0
  fi

  if [[ ! -f "$DOCKER_COMPOSE_FILE" ]]; then
    printf 'Database is not reachable at %s:%s and %s was not found.\n' "$DB_HOST" "$DB_PORT" "$DOCKER_COMPOSE_FILE" >&2
    return 1
  fi

  if ! docker_compose_cmd version >/dev/null 2>&1; then
    printf 'Database is not reachable and Docker Compose is not available.\n' >&2
    return 1
  fi

  (
    cd "$ROOT_DIR"
    docker_compose_cmd up -d "$DB_SERVICE_NAME" >/dev/null
  )

  if ! wait_for_port "$DB_HOST" "$DB_PORT" "Database" 60; then
    return 1
  fi

  printf 'Database started: %s:%s\n' "$DB_HOST" "$DB_PORT"
}

start_backend() {
  local backend_pid
  backend_pid="$(pid_from_file "$BACKEND_PID_FILE" || true)"
  if [[ -n "$backend_pid" ]] && is_pid_running "$backend_pid"; then
    printf 'Backend already running with PID %s\n' "$backend_pid"
    return 0
  fi

  if is_url_ready "$BACKEND_HEALTH_URL"; then
    printf 'Backend already reachable at %s\n' "$BACKEND_HEALTH_URL"
    return 0
  fi

  if ! command -v mvn >/dev/null 2>&1; then
    printf 'Maven is required to start the backend.\n' >&2
    return 1
  fi

  local java_home=""
  java_home="$(resolve_java_home || true)"
  local -a backend_cmd=(mvn spring-boot:run)
  if [[ -n "$java_home" ]]; then
    backend_cmd=(env "JAVA_HOME=$java_home" "${backend_cmd[@]}")
  fi

  (
    cd "$BACKEND_DIR"
    nohup "${backend_cmd[@]}" >"$BACKEND_LOG_FILE" 2>&1 &
    echo $! >"$BACKEND_PID_FILE"
  )

  if ! wait_for_url "$BACKEND_HEALTH_URL" "Backend" 60; then
    printf 'Backend log: %s\n' "$BACKEND_LOG_FILE" >&2
    return 1
  fi

  printf 'Backend started: http://127.0.0.1:8080/api\n'
}

stop_database() {
  if [[ ! -f "$DOCKER_COMPOSE_FILE" ]]; then
    return 0
  fi

  if ! docker_compose_cmd version >/dev/null 2>&1; then
    return 0
  fi

  (
    cd "$ROOT_DIR"
    docker_compose_cmd stop "$DB_SERVICE_NAME" >/dev/null 2>&1 || true
  )
}

start_frontend() {
  local frontend_pid
  frontend_pid="$(pid_from_file "$FRONTEND_PID_FILE" || true)"
  if [[ -n "$frontend_pid" ]] && is_pid_running "$frontend_pid"; then
    printf 'Frontend already running with PID %s\n' "$frontend_pid"
    return 0
  fi

  if is_url_ready "$FRONTEND_URL"; then
    printf 'Frontend already reachable at %s\n' "$FRONTEND_URL"
    return 0
  fi

  if ! command -v python3 >/dev/null 2>&1; then
    printf 'Python 3 is required to start the frontend.\n' >&2
    return 1
  fi

  (
    cd "$ROOT_DIR"
    nohup python3 -m http.server 8000 --bind 127.0.0.1 >"$FRONTEND_LOG_FILE" 2>&1 &
    echo $! >"$FRONTEND_PID_FILE"
  )

  if ! wait_for_url "$FRONTEND_URL" "Frontend" 15; then
    printf 'Frontend log: %s\n' "$FRONTEND_LOG_FILE" >&2
    return 1
  fi

  printf 'Frontend started: http://127.0.0.1:8000\n'
}

stop_service() {
  local label="$1"
  local pid_file="$2"

  local pid
  pid="$(pid_from_file "$pid_file" || true)"
  if [[ -z "$pid" ]]; then
    printf '%s is not managed by this script.\n' "$label"
    return 0
  fi

  if ! is_pid_running "$pid"; then
    rm -f "$pid_file"
    printf '%s PID file was stale and has been removed.\n' "$label"
    return 0
  fi

  kill "$pid"
  for ((i = 1; i <= 10; i++)); do
    if ! is_pid_running "$pid"; then
      rm -f "$pid_file"
      printf '%s stopped.\n' "$label"
      return 0
    fi
    sleep 1
  done

  printf '%s did not stop cleanly. PID: %s\n' "$label" "$pid" >&2
  return 1
}

print_status() {
  if is_port_open "$DB_HOST" "$DB_PORT"; then
    printf 'Database: up (%s:%s)\n' "$DB_HOST" "$DB_PORT"
  else
    printf 'Database: down\n'
  fi

  if is_url_ready "$BACKEND_HEALTH_URL"; then
    printf 'Backend: up (%s)\n' "$BACKEND_HEALTH_URL"
  else
    printf 'Backend: down\n'
  fi

  if is_url_ready "$FRONTEND_URL"; then
    printf 'Frontend: up (%s)\n' "$FRONTEND_URL"
  else
    printf 'Frontend: down\n'
  fi

  if [[ -f "$BACKEND_PID_FILE" ]]; then
    printf 'Backend PID file: %s (%s)\n' "$BACKEND_PID_FILE" "$(pid_from_file "$BACKEND_PID_FILE")"
  fi

  if [[ -f "$FRONTEND_PID_FILE" ]]; then
    printf 'Frontend PID file: %s (%s)\n' "$FRONTEND_PID_FILE" "$(pid_from_file "$FRONTEND_PID_FILE")"
  fi

  printf 'Logs: %s %s\n' "$BACKEND_LOG_FILE" "$FRONTEND_LOG_FILE"
}

main() {
  local command="${1:-}"

  load_local_env
  DB_HOST="${BUSGO_DB_HOST:-127.0.0.1}"
  DB_PORT="${BUSGO_DB_PORT:-3306}"

  case "$command" in
    up)
      start_database
      start_backend
      start_frontend
      printf 'Application ready.\n'
      printf 'Open: http://127.0.0.1:8000\n'
      ;;
    down)
      stop_service "Frontend" "$FRONTEND_PID_FILE"
      stop_service "Backend" "$BACKEND_PID_FILE"
      stop_database
      ;;
    restart)
      stop_service "Frontend" "$FRONTEND_PID_FILE"
      stop_service "Backend" "$BACKEND_PID_FILE"
      stop_database
      start_database
      start_backend
      start_frontend
      printf 'Application restarted.\n'
      ;;
    status)
      print_status
      ;;
    *)
      usage
      return 1
      ;;
  esac
}

main "$@"
