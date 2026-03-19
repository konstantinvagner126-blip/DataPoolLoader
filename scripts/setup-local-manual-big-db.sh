#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_FILE="$ROOT_DIR/scripts/local-postgres-manual-big-setup.sql"

if command -v psql >/dev/null 2>&1; then
  PSQL_BIN="$(command -v psql)"
elif [[ -x "/opt/homebrew/opt/postgresql@18/bin/psql" ]]; then
  PSQL_BIN="/opt/homebrew/opt/postgresql@18/bin/psql"
else
  echo "psql не найден. Установи PostgreSQL client или добавь psql в PATH." >&2
  exit 1
fi

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-postgres}"
PGUSER="${PGUSER:-kwdev}"
PGPASSWORD="${PGPASSWORD:-dummy}"

echo "Подготовка схемы datapool_manual_big в $PGDATABASE@$PGHOST:$PGPORT пользователем $PGUSER"
"$PSQL_BIN" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -f "$SQL_FILE"
echo "Схема datapool_manual_big подготовлена."
