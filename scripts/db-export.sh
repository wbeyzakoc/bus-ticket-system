#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_ENV_FILE="$ROOT_DIR/.env.local"
DUMP_DIR="$ROOT_DIR/database"
DUMP_FILE="${1:-$DUMP_DIR/busgo.sql}"
EXPORT_MODE="${BUSGO_DB_EXPORT_MODE:-public}"
SANITIZE_SCRIPT="$ROOT_DIR/scripts/db-sanitize-public-dump.sh"

load_local_env() {
  if [[ -f "$LOCAL_ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$LOCAL_ENV_FILE"
    set +a
  fi
}

load_local_env

DB_HOST="${BUSGO_DB_HOST:-127.0.0.1}"
DB_PORT="${BUSGO_DB_PORT:-3306}"
DB_NAME="${BUSGO_DB_NAME:-busgo}"
DB_USER="${BUSGO_DB_USER:-busgo}"
DB_PASS="${BUSGO_DB_PASS:-busgo123}"

if ! command -v mysqldump >/dev/null 2>&1; then
  printf 'mysqldump is required.\n' >&2
  exit 1
fi

mkdir -p "$DUMP_DIR"

MYSQL_PWD="$DB_PASS" mysqldump \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  --default-character-set=utf8mb4 \
  --set-gtid-purged=OFF \
  --no-tablespaces \
  --routines \
  --triggers \
  --single-transaction \
  "$DB_NAME" >"$DUMP_FILE"

if [[ "$EXPORT_MODE" != "private" ]]; then
  "$SANITIZE_SCRIPT" "$DUMP_FILE"
fi

printf 'Dump created: %s\n' "$DUMP_FILE"
