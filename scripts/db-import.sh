#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_ENV_FILE="$ROOT_DIR/.env.local"
DUMP_FILE="${1:-$ROOT_DIR/database/busgo.sql}"

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
DB_ROOT_USER="${BUSGO_DB_ROOT_USER:-root}"
DB_ROOT_PASS="${BUSGO_DB_ROOT_PASS:-root123}"

if ! command -v mysql >/dev/null 2>&1; then
  printf 'mysql client is required.\n' >&2
  exit 1
fi

if [[ ! -f "$DUMP_FILE" ]]; then
  printf 'Dump file not found: %s\n' "$DUMP_FILE" >&2
  exit 1
fi

MYSQL_PWD="$DB_ROOT_PASS" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_ROOT_USER" \
  --execute="CREATE DATABASE IF NOT EXISTS \`$DB_NAME\`; CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASS'; GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%'; FLUSH PRIVILEGES;"

MYSQL_PWD="$DB_PASS" mysql \
  --host="$DB_HOST" \
  --port="$DB_PORT" \
  --user="$DB_USER" \
  "$DB_NAME" <"$DUMP_FILE"

printf 'Dump imported into %s on %s:%s\n' "$DB_NAME" "$DB_HOST" "$DB_PORT"
