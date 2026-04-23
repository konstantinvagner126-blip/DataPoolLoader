#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SMOKE_ROOT="${PROJECT_ROOT}/tools/sql-console-browser-smoke"
BASE_URL="${SQL_CONSOLE_BASE_URL:-http://127.0.0.1:18080}"
REUSE_SERVER="${SQL_CONSOLE_SMOKE_REUSE_SERVER:-0}"
SERVER_LOG_DIR="${PROJECT_ROOT}/build/sql-console-browser-smoke"
SERVER_LOG_FILE="${SERVER_LOG_DIR}/ui-server.log"
SERVER_CONFIG_FILE="${SERVER_LOG_DIR}/application.yml"
SERVER_STORAGE_DIR="${SQL_CONSOLE_SMOKE_STORAGE_DIR:-${SERVER_LOG_DIR}/storage}"
SERVER_PORT="$(node -e 'const url = new URL(process.argv[1]); process.stdout.write(url.port || (url.protocol === "https:" ? "443" : "80"));' "${BASE_URL}")"
SQL_CONSOLE_DB_JDBC_URL="${LOCAL_MANUAL_DB_JDBC_URL:-jdbc:postgresql://127.0.0.1:5432/postgres}"
SQL_CONSOLE_DB_USERNAME="${LOCAL_MANUAL_DB_USERNAME:-kwdev}"
SQL_CONSOLE_DB_PASSWORD="${LOCAL_MANUAL_DB_PASSWORD:-dummy}"
SQL_CONSOLE_SMOKE_SKIP_DB_SETUP="${SQL_CONSOLE_SMOKE_SKIP_DB_SETUP:-0}"
SERVER_PID=""

DB_HOST="$(node -e 'const url = new URL(process.argv[1].replace(/^jdbc:/, "")); process.stdout.write(url.hostname);' "${SQL_CONSOLE_DB_JDBC_URL}")"
DB_PORT="$(node -e 'const url = new URL(process.argv[1].replace(/^jdbc:/, "")); process.stdout.write(url.port || "5432");' "${SQL_CONSOLE_DB_JDBC_URL}")"
DB_NAME="$(node -e 'const url = new URL(process.argv[1].replace(/^jdbc:/, "")); const path = url.pathname.startsWith("/") ? url.pathname.slice(1) : url.pathname; process.stdout.write(path || "postgres");' "${SQL_CONSOLE_DB_JDBC_URL}")"

write_smoke_config() {
  cat >"${SERVER_CONFIG_FILE}" <<EOF
ui:
  port: ${SERVER_PORT}
  appsRoot: "${PROJECT_ROOT}/apps"
  storageDir: "${SERVER_STORAGE_DIR}"
  moduleStore:
    mode: files
  showTechnicalDiagnostics: false
  showRawSummaryJson: false
  defaultCredentialsFile: null
  sqlConsole:
    fetchSize: 1000
    maxRowsPerShard: 200
    queryTimeoutSec: 60
    groups:
      - name: "dev"
        sources: ["db1", "db2"]
      - name: "ift"
        sources: ["db2", "db3", "db4"]
      - name: "lt"
        sources: ["db4", "db5"]
    sourceCatalog:
      - name: "db1"
        jdbcUrl: "${SQL_CONSOLE_DB_JDBC_URL}"
        username: "${SQL_CONSOLE_DB_USERNAME}"
        password: "${SQL_CONSOLE_DB_PASSWORD}"
      - name: "db2"
        jdbcUrl: "${SQL_CONSOLE_DB_JDBC_URL}"
        username: "${SQL_CONSOLE_DB_USERNAME}"
        password: "${SQL_CONSOLE_DB_PASSWORD}"
      - name: "db3"
        jdbcUrl: "${SQL_CONSOLE_DB_JDBC_URL}"
        username: "${SQL_CONSOLE_DB_USERNAME}"
        password: "${SQL_CONSOLE_DB_PASSWORD}"
      - name: "db4"
        jdbcUrl: "${SQL_CONSOLE_DB_JDBC_URL}"
        username: "${SQL_CONSOLE_DB_USERNAME}"
        password: "${SQL_CONSOLE_DB_PASSWORD}"
      - name: "db5"
        jdbcUrl: "${SQL_CONSOLE_DB_JDBC_URL}"
        username: "${SQL_CONSOLE_DB_USERNAME}"
        password: "${SQL_CONSOLE_DB_PASSWORD}"
EOF
}

wait_for_server() {
  local url="$1"
  local attempt
  for attempt in $(seq 1 150); do
    if curl --fail --silent --show-error "${url}/sql-console" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "UI server did not become ready at ${url}" >&2
  return 1
}

cleanup() {
  if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" >/dev/null 2>&1; then
    kill "${SERVER_PID}" >/dev/null 2>&1 || true
    wait "${SERVER_PID}" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

cd "${PROJECT_ROOT}"

mkdir -p "${SERVER_LOG_DIR}"

npm --prefix "${SMOKE_ROOT}" ci
npm --prefix "${SMOKE_ROOT}" exec playwright install chromium

if [[ "${SQL_CONSOLE_SMOKE_SKIP_DB_SETUP}" != "1" ]]; then
  PGHOST="${DB_HOST}" \
  PGPORT="${DB_PORT}" \
  PGDATABASE="${DB_NAME}" \
  PGUSER="${SQL_CONSOLE_DB_USERNAME}" \
  PGPASSWORD="${SQL_CONSOLE_DB_PASSWORD}" \
  ./scripts/setup-local-manual-db.sh
fi

if [[ "${REUSE_SERVER}" != "1" ]]; then
  rm -rf "${SERVER_STORAGE_DIR}"
  write_smoke_config
  DATAPOOL_UI_CONFIG="${SERVER_CONFIG_FILE}" ./scripts/run-ui-server.sh >"${SERVER_LOG_FILE}" 2>&1 &
  SERVER_PID="$!"
fi

wait_for_server "${BASE_URL}"

SQL_CONSOLE_BASE_URL="${BASE_URL}" npm --prefix "${SMOKE_ROOT}" test
