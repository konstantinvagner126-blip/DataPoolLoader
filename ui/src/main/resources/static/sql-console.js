let sqlConsoleEditor;
let sqlConsoleInfo = null;
let sqlConsoleCredentialsStatus = null;
let sqlConsoleResult = null;
let currentSelectShard = null;
let selectedSourceNames = [];
const currentPages = {};
const SQL_DRAFT_STORAGE_KEY = "datapool.sqlConsole.draft";

const credentialsStatusEl = document.getElementById("sqlCredentialsStatus");
const credentialsWarningEl = document.getElementById("sqlCredentialsWarning");
const credentialsFileInputEl = document.getElementById("sqlCredentialsFileInput");
const consoleInfoEl = document.getElementById("sqlConsoleInfo");
const sourceSelectionEl = document.getElementById("sqlSourceSelection");
const executionStatusEl = document.getElementById("sqlExecutionStatus");
const resultMetaEl = document.getElementById("sqlResultMeta");
const resultSummaryEl = document.getElementById("sqlResultSummary");
const resultTableEl = document.getElementById("sqlResultTable");
const pageSizeSelectEl = document.getElementById("sqlPageSizeSelect");
const dataTabButtonEl = document.getElementById("sqlDataTabButton");
const statusTabButtonEl = document.getElementById("sqlStatusTabButton");
const dataPaneEl = document.getElementById("sqlDataPane");
const statusPaneEl = document.getElementById("sqlStatusPane");

require.config({ paths: { vs: "https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs" } });
require(["vs/editor/editor.main"], async () => {
  sqlConsoleEditor = monaco.editor.create(document.getElementById("sqlEditor"), {
    value: loadSqlDraft(),
    language: "sql",
    theme: "vs",
    automaticLayout: true,
    minimap: { enabled: false }
  });

  sqlConsoleEditor.onDidChangeModelContent(() => {
    saveSqlDraft(sqlConsoleEditor.getValue());
  });

  await Promise.all([
    loadSqlConsoleInfo(),
    refreshCredentialsStatus()
  ]);
});

document.getElementById("sqlUploadCredentialsButton").addEventListener("click", async () => {
  const file = credentialsFileInputEl.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch("/api/credentials/upload", {
    method: "POST",
    body: formData
  });
  sqlConsoleCredentialsStatus = await response.json();
  renderCredentialsStatus(sqlConsoleCredentialsStatus);
  renderCredentialsWarning();
});

[dataTabButtonEl, statusTabButtonEl].forEach(button => {
  button.addEventListener("click", () => {
    setActiveOutputTab(button.dataset.outputTab);
  });
});

pageSizeSelectEl.addEventListener("change", () => {
  if (sqlConsoleResult && sqlConsoleResult.statementType === "SELECT") {
    const shardResults = successfulSelectShards(sqlConsoleResult);
    if (currentSelectShard && shardResults.some(item => item.shardName === currentSelectShard)) {
      currentPages[currentSelectShard] = 1;
    }
    renderResult(sqlConsoleResult);
  }
});

document.getElementById("sqlRunButton").addEventListener("click", async () => {
  const sql = sqlConsoleEditor.getValue();
  const statementKeyword = detectStatementKeyword(sql);
  if (!isReadOnlyLikeStatement(statementKeyword)) {
    const confirmMessage = `Запрос типа ${statementKeyword} может изменить данные или структуру на всех настроенных sources. Продолжить?`;
    if (!window.confirm(confirmMessage)) {
      return;
    }
  }

  executionStatusEl.textContent = "Запрос выполняется...";
  executionStatusEl.className = "small text-secondary";

  try {
    const response = await fetch("/api/sql-console/query", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sql, selectedSourceNames })
    });
    if (!response.ok) {
      const error = await response.json();
      throw new Error(error.error || "Не удалось выполнить запрос.");
    }
    sqlConsoleResult = await response.json();
    const successfulShards = successfulSelectShards(sqlConsoleResult);
    currentSelectShard = successfulShards.length > 0 ? successfulShards[0].shardName : null;
    successfulShards.forEach(item => {
      if (!currentPages[item.shardName]) currentPages[item.shardName] = 1;
    });
    renderResult(sqlConsoleResult);
  } catch (error) {
    executionStatusEl.textContent = error.message || "Не удалось выполнить запрос.";
    executionStatusEl.className = "sql-status-strip sql-status-strip-failed";
    resultMetaEl.textContent = "Результат не получен.";
    resultSummaryEl.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(error.message || "Не удалось выполнить запрос.")}</div>`;
    resultTableEl.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(error.message || "Не удалось выполнить запрос.")}</div>`;
    setActiveOutputTab("status");
  }
});

async function loadSqlConsoleInfo() {
  const response = await fetch("/api/sql-console/info");
  sqlConsoleInfo = await response.json();
  renderConsoleInfo(sqlConsoleInfo);
  renderCredentialsWarning();
}

async function refreshCredentialsStatus() {
  const response = await fetch("/api/credentials");
  sqlConsoleCredentialsStatus = await response.json();
  renderCredentialsStatus(sqlConsoleCredentialsStatus);
  renderCredentialsWarning();
}

function renderConsoleInfo(info) {
  if (!info || !info.configured) {
    consoleInfoEl.textContent = "В ui.application.yml пока не настроены source-подключения.";
    sourceSelectionEl.innerHTML = "";
    return;
  }

  consoleInfoEl.innerHTML = `
    <div><strong>Sources настроено:</strong> ${info.sourceNames.length}</div>
    <div><strong>Лимит строк на SELECT с одного source:</strong> ${info.maxRowsPerShard}</div>
  `;

  if (selectedSourceNames.length === 0) {
    selectedSourceNames = [...info.sourceNames];
  } else {
    selectedSourceNames = selectedSourceNames.filter(name => info.sourceNames.includes(name));
    info.sourceNames.forEach(name => {
      if (!selectedSourceNames.includes(name)) {
        selectedSourceNames.push(name);
      }
    });
  }

  sourceSelectionEl.innerHTML = info.sourceNames
    .map(name => `
      <label class="sql-source-checkbox">
        <input type="checkbox" value="${escapeHtml(name)}" ${selectedSourceNames.includes(name) ? "checked" : ""}>
        <span>${escapeHtml(name)}</span>
      </label>
    `)
    .join("");

  sourceSelectionEl.querySelectorAll("input[type=\"checkbox\"]").forEach(input => {
    input.addEventListener("change", () => {
      selectedSourceNames = [...sourceSelectionEl.querySelectorAll("input[type=\"checkbox\"]:checked")]
        .map(checkbox => checkbox.value);
    });
  });
}

function renderCredentialsStatus(status) {
  if (!status) {
    credentialsStatusEl.textContent = "Файл не задан.";
    return;
  }
  const sourceLabel = status.uploaded ? "загружен через UI" : (status.mode === "FILE" ? "файл по умолчанию" : "файл не задан");
  const availability = status.fileAvailable ? "доступен" : "не найден";
  credentialsStatusEl.textContent = `${sourceLabel}: ${status.displayName} (${availability})`;
}

function renderCredentialsWarning() {
  const hasShards = !!(sqlConsoleInfo && sqlConsoleInfo.configured);
  if (!hasShards) {
    credentialsWarningEl.classList.add("d-none");
    credentialsWarningEl.textContent = "";
    return;
  }
  const missingCredentials = !sqlConsoleCredentialsStatus || !sqlConsoleCredentialsStatus.fileAvailable;
  if (!missingCredentials) {
    credentialsWarningEl.classList.add("d-none");
    credentialsWarningEl.textContent = "";
    return;
  }
  credentialsWarningEl.classList.remove("d-none");
  credentialsWarningEl.textContent = "Если source-подключения в ui.application.yml используют placeholders ${...}, загрузи credential.properties перед выполнением запроса.";
}

function renderResult(result) {
  const successfulShards = result.shardResults.filter(item => item.status === "SUCCESS");
  const failedShards = result.shardResults.filter(item => item.status !== "SUCCESS");
  const selectedSourcesLabel = selectedSourceNames.length > 0
    ? selectedSourceNames.join(", ")
    : "все sources";

  executionStatusEl.textContent =
    `Запрос типа ${result.statementKeyword} выполнен. Sources: ${selectedSourcesLabel}. Успешных: ${successfulShards.length}, с ошибкой: ${failedShards.length}.`;
  executionStatusEl.className = failedShards.length > 0
    ? "sql-status-strip sql-status-strip-warning"
    : "sql-status-strip sql-status-strip-success";

  resultMetaEl.textContent =
    result.statementType === "RESULT_SET"
      ? `Данные показываются отдельно по каждому source. Лимит на source: ${result.maxRowsPerShard}.`
      : `Результат команды показан по каждому source отдельно.`;

  resultSummaryEl.innerHTML = `
    ${renderShardCards(result.shardResults)}
    <div class="mt-3">${renderStatusTable(result.shardResults)}</div>
  `;

  if (result.statementType === "RESULT_SET") {
    renderSelectResults(result);
    setActiveOutputTab("data");
  } else {
    renderCommandResults(result);
    setActiveOutputTab("status");
  }
}

function renderSelectResults(result) {
  const shards = successfulSelectShards(result);
  if (shards.length === 0) {
    resultTableEl.innerHTML = `<div class="alert alert-secondary mb-0">Ни один source не вернул данные для отображения.</div>`;
    return;
  }

  if (!currentSelectShard || !shards.some(item => item.shardName === currentSelectShard)) {
    currentSelectShard = shards[0].shardName;
  }

  const pageSize = Number(pageSizeSelectEl.value || 50);
  const activeShard = shards.find(item => item.shardName === currentSelectShard) || shards[0];
  const totalRows = activeShard.rows.length;
  const totalPages = Math.max(1, Math.ceil(totalRows / pageSize));
  const currentPage = Math.min(currentPages[activeShard.shardName] || 1, totalPages);
  currentPages[activeShard.shardName] = currentPage;

  const startIndex = (currentPage - 1) * pageSize;
  const endIndexExclusive = Math.min(startIndex + pageSize, totalRows);
  const pageRows = activeShard.rows.slice(startIndex, endIndexExclusive);

  const tabsHtml = shards.map(item => `
    <button
      class="nav-link ${item.shardName === activeShard.shardName ? "active" : ""}"
      type="button"
      data-shard-tab="${escapeHtml(item.shardName)}"
    >
      ${escapeHtml(item.shardName)} <span class="text-secondary">(${item.rowCount})</span>
    </button>
  `).join("");

  const tableHtml = activeShard.columns.length === 0
    ? `<div class="alert alert-secondary mb-0">Source ${escapeHtml(activeShard.shardName)} не вернул колонок.</div>`
    : `
      <table class="table table-striped table-hover sql-result-table align-middle mb-0">
        <thead>
          <tr>${activeShard.columns.map(column => `<th>${escapeHtml(column)}</th>`).join("")}</tr>
        </thead>
        <tbody>
          ${pageRows.map(row => `
            <tr>
              ${activeShard.columns.map(column => `<td>${escapeHtml(row[column] ?? "")}</td>`).join("")}
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;

  const paginationHtml = renderPagination(activeShard.shardName, currentPage, totalPages);
  const from = totalRows === 0 ? 0 : startIndex + 1;
  const to = endIndexExclusive;

  resultTableEl.innerHTML = `
    <ul class="nav nav-tabs sql-result-tabs mb-3">${tabsHtml}</ul>
    <div class="mb-3 small text-secondary">
      Source <strong>${escapeHtml(activeShard.shardName)}</strong>.
      Показано строк: ${from}-${to} из ${totalRows}.
      ${activeShard.truncated ? `Результат усечен лимитом ${result.maxRowsPerShard} строк на source.` : ""}
    </div>
    <div class="table-responsive">${tableHtml}</div>
    <div class="sql-pagination-footer">
      <div class="small text-secondary">Страница ${currentPage} из ${totalPages}</div>
      ${paginationHtml}
    </div>
  `;

  resultTableEl.querySelectorAll("[data-shard-tab]").forEach(button => {
    button.addEventListener("click", () => {
      currentSelectShard = button.dataset.shardTab;
      renderSelectResults(result);
    });
  });

  resultTableEl.querySelectorAll("[data-page]").forEach(button => {
    button.addEventListener("click", () => {
      currentPages[activeShard.shardName] = Number(button.dataset.page);
      renderSelectResults(result);
    });
  });
}

function renderCommandResults(result) {
  resultTableEl.innerHTML = `<div class="alert alert-secondary mb-0">Команда ${result.statementKeyword} не возвращает табличные данные. Смотри вкладку "Статусы".</div>`;
}

function renderShardCards(shardResults) {
  return `
    <div class="sql-shard-card-grid">
      ${shardResults.map(item => `
        <div class="sql-shard-card status-${item.status.toLowerCase()}">
          <div class="sql-shard-card-title">${escapeHtml(item.shardName)}</div>
          <div>Статус: ${translateShardStatus(item.status)}</div>
          <div>Строк: ${item.rowCount}</div>
          <div>Колонки: ${item.columns.length > 0 ? escapeHtml(item.columns.join(", ")) : "-"}</div>
          <div>Затронуто строк: ${item.affectedRows ?? "-"}</div>
          <div>${item.truncated ? "Результат усечен по лимиту." : (item.message ? escapeHtml(item.message) : "Без дополнительных сообщений.")}</div>
          <div>${item.errorMessage ? escapeHtml(item.errorMessage) : ""}</div>
        </div>
      `).join("")}
    </div>
  `;
}

function renderStatusTable(rows) {
  return `
    <table class="table table-striped table-hover align-middle mb-0">
      <thead>
        <tr>
          <th>Source</th>
          <th>Статус</th>
          <th>Затронуто строк</th>
          <th>Сообщение</th>
          <th>Ошибка</th>
        </tr>
      </thead>
      <tbody>
        ${rows.map(item => `
          <tr>
            <td><strong>${escapeHtml(item.shardName)}</strong></td>
            <td><span class="status-badge status-${item.status.toLowerCase()}">${translateShardStatus(item.status)}</span></td>
            <td>${item.affectedRows ?? "-"}</td>
            <td>${escapeHtml(item.message ?? "-")}</td>
            <td>${escapeHtml(item.errorMessage ?? "-")}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function setActiveOutputTab(tab) {
  const isData = tab === "data";
  dataTabButtonEl.classList.toggle("active", isData);
  statusTabButtonEl.classList.toggle("active", !isData);
  dataPaneEl.classList.toggle("active", isData);
  statusPaneEl.classList.toggle("active", !isData);
}

function renderPagination(shardName, currentPage, totalPages) {
  if (totalPages <= 1) {
    return "";
  }

  const pages = [];
  for (let page = 1; page <= totalPages; page += 1) {
    pages.push(`
      <button
        class="btn btn-sm ${page === currentPage ? "btn-dark" : "btn-outline-secondary"}"
        type="button"
        data-page="${page}"
      >
        ${page}
      </button>
    `);
  }

  return `<div class="d-flex flex-wrap gap-2">${pages.join("")}</div>`;
}

function successfulSelectShards(result) {
  return result.shardResults.filter(item => item.status === "SUCCESS");
}

function detectStatementKeyword(sql) {
  const normalized = String(sql)
    .replace(/\/\*[\s\S]*?\*\//g, " ")
    .split("\n")
    .map(line => line.split("--")[0])
    .join(" ")
    .trim()
    .replace(/;$/, "")
    .trim()
    .toUpperCase();

  return normalized.split(/\s+/)[0] || "SQL";
}

function isReadOnlyLikeStatement(keyword) {
  return ["SELECT", "WITH", "SHOW", "EXPLAIN", "VALUES"].includes(keyword);
}

function loadSqlDraft() {
  try {
    return window.sessionStorage.getItem(SQL_DRAFT_STORAGE_KEY) || "select 1 as check_value";
  } catch (_) {
    return "select 1 as check_value";
  }
}

function saveSqlDraft(value) {
  try {
    window.sessionStorage.setItem(SQL_DRAFT_STORAGE_KEY, value);
  } catch (_) {
    // ignore storage errors
  }
}

function translateShardStatus(status) {
  switch (status) {
    case "SUCCESS": return "Успешно";
    case "FAILED": return "Ошибка";
    default: return status || "-";
  }
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
}
