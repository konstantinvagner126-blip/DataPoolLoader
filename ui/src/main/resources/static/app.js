let configEditor;
let sqlEditor;
let selectedModuleId = null;
let currentModule = null;
let currentSqlPath = null;
let sqlContents = {};
let currentCredentialsStatus = null;

const moduleList = document.getElementById("moduleList");
const selectedModuleLabel = document.getElementById("selectedModuleLabel");
const sqlFileSelect = document.getElementById("sqlFileSelect");
const runSummary = document.getElementById("runSummary");
const sourceProgress = document.getElementById("sourceProgress");
const eventLog = document.getElementById("eventLog");
const technicalEventLog = document.getElementById("technicalEventLog");
const sourceStatusTable = document.getElementById("sourceStatusTable");
const summaryJson = document.getElementById("summaryJson");
const credentialsStatus = document.getElementById("credentialsStatus");
const credentialsWarning = document.getElementById("credentialsWarning");
const credentialsFileInput = document.getElementById("credentialsFileInput");

require.config({ paths: { vs: "https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs" } });
require(["vs/editor/editor.main"], () => {
  configEditor = monaco.editor.create(document.getElementById("configEditor"), {
    value: "",
    language: "yaml",
    theme: "vs",
    automaticLayout: true,
    minimap: { enabled: false }
  });

  sqlEditor = monaco.editor.create(document.getElementById("sqlEditor"), {
    value: "",
    language: "sql",
    theme: "vs",
    automaticLayout: true,
    minimap: { enabled: false }
  });

  sqlEditor.onDidChangeModelContent(() => {
    if (currentSqlPath) {
      sqlContents[currentSqlPath] = sqlEditor.getValue();
    }
  });

  loadModules();
  refreshCredentialsStatus();
  connectWebSocket();
});

document.getElementById("reloadButton").addEventListener("click", () => {
  if (selectedModuleId) {
    loadModule(selectedModuleId);
  }
});

document.getElementById("saveButton").addEventListener("click", async () => {
  if (!selectedModuleId) return;
  await fetch(`/api/modules/${selectedModuleId}/save`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      configText: configEditor.getValue(),
      sqlFiles: sqlContents
    })
  });
});

document.getElementById("runButton").addEventListener("click", async () => {
  if (!selectedModuleId) return;
  await fetch("/api/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      moduleId: selectedModuleId,
      configText: configEditor.getValue(),
      sqlFiles: sqlContents
    })
  });
});

document.getElementById("uploadCredentialsButton").addEventListener("click", async () => {
  const file = credentialsFileInput.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append("file", file);
  const response = await fetch("/api/credentials/upload", {
    method: "POST",
    body: formData
  });
  currentCredentialsStatus = await response.json();
  renderCredentialsStatus(currentCredentialsStatus);
  renderCredentialsWarning();
});

sqlFileSelect.addEventListener("change", () => {
  currentSqlPath = sqlFileSelect.value;
  sqlEditor.setValue(sqlContents[currentSqlPath] || "");
});

async function loadModules() {
  const response = await fetch("/api/modules");
  const modules = await response.json();
  moduleList.innerHTML = "";
  modules.forEach((module, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "list-group-item list-group-item-action";
    button.textContent = module.title;
    button.dataset.moduleId = module.id;
    button.addEventListener("click", () => selectModule(module.id));
    moduleList.appendChild(button);
    if (index === 0) {
      selectModule(module.id);
    }
  });
}

async function selectModule(moduleId) {
  selectedModuleId = moduleId;
  [...moduleList.children].forEach((child, index) => {
    child.classList.toggle("active", child.dataset.moduleId === moduleId);
  });
  await loadModule(moduleId);
}

async function loadModule(moduleId) {
  const response = await fetch(`/api/modules/${moduleId}`);
  currentModule = await response.json();
  selectedModuleLabel.textContent = `${currentModule.title} · ${currentModule.configPath}`;
  configEditor.setValue(currentModule.configText);
  sqlContents = {};
  currentModule.sqlFiles.forEach(file => {
    sqlContents[file.path] = file.content;
  });
  renderSqlSelect();
  renderCredentialsWarning();
  [...moduleList.children].forEach(button => {
    button.classList.toggle("active", button.dataset.moduleId === currentModule.id);
  });
}

function renderSqlSelect() {
  sqlFileSelect.innerHTML = "";
  currentModule.sqlFiles.forEach((file, index) => {
    const option = document.createElement("option");
    option.value = file.path;
    option.textContent = `${file.label} · ${file.path}`;
    sqlFileSelect.appendChild(option);
    if (index === 0) {
      currentSqlPath = file.path;
    }
  });
  sqlEditor.setValue(currentSqlPath ? sqlContents[currentSqlPath] || "" : "");
}

function connectWebSocket() {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const ws = new WebSocket(`${protocol}://${window.location.host}/ws`);
  ws.onmessage = (event) => {
    const state = JSON.parse(event.data);
    currentCredentialsStatus = state.credentialsStatus;
    renderState(state);
  };
}

async function refreshCredentialsStatus() {
  const response = await fetch("/api/credentials");
  currentCredentialsStatus = await response.json();
  renderCredentialsStatus(currentCredentialsStatus);
}

function renderState(state) {
  renderCredentialsStatus(state.credentialsStatus);
  renderCredentialsWarning();
  const activeRun = state.activeRun;
  const latestRun = activeRun || (state.history && state.history.length > 0 ? state.history[0] : null);
  if (!latestRun) {
    runSummary.textContent = "Запусков пока нет.";
    sourceProgress.innerHTML = "";
    eventLog.textContent = "";
    technicalEventLog.textContent = "";
    sourceStatusTable.innerHTML = "";
    summaryJson.textContent = "Пока нет данных summary.";
    return;
  }

  const sourceStates = buildSourceStates(latestRun);
  const timeline = buildHumanTimeline(latestRun, sourceStates);
  const overallStatus = translateStatus(latestRun.status);
  const stageLabel = detectCurrentStage(latestRun);

  runSummary.innerHTML = `
    <div><strong>Модуль:</strong> ${latestRun.moduleTitle}</div>
    <div><strong>Статус:</strong> ${overallStatus}</div>
    <div><strong>Этап:</strong> ${stageLabel}</div>
    <div><strong>Старт:</strong> ${formatDateTime(latestRun.startedAt)}</div>
    <div><strong>Завершение:</strong> ${latestRun.finishedAt ? formatDateTime(latestRun.finishedAt) : "еще выполняется"}</div>
    <div><strong>Папка результата:</strong> ${latestRun.outputDir || "-"}</div>
    <div><strong>Строк в merged.csv:</strong> ${latestRun.mergedRowCount}</div>
    <div><strong>Источников успешно:</strong> ${Object.values(sourceStates).filter(state => state.status === "SUCCESS").length}</div>
    <div><strong>Источников с ошибкой:</strong> ${Object.values(sourceStates).filter(state => state.status === "FAILED" || state.status === "SKIPPED_SCHEMA_MISMATCH").length}</div>
    <div><strong>Ошибка:</strong> ${latestRun.errorMessage || "-"}</div>
  `;

  sourceProgress.innerHTML = Object.entries(latestRun.sourceProgress || {})
    .map(([source, count]) => `<div class="progress-row"><span>${source}</span><strong>${count} строк</strong></div>`)
    .join("");

  eventLog.innerHTML = timeline.length > 0
    ? timeline.map(item => `
        <div class="human-log-entry">
          <div class="human-log-time">${formatDateTime(item.timestamp)}</div>
          <div class="human-log-text">${item.message}</div>
        </div>
      `).join("")
    : `<div class="text-secondary">Пока нет значимых событий для отображения.</div>`;

  technicalEventLog.textContent = (latestRun.events || [])
    .slice(-200)
    .map(event => JSON.stringify(event, null, 2))
    .join("\n\n");

  sourceStatusTable.innerHTML = renderSourceStatusTable(sourceStates);

  summaryJson.textContent = latestRun.summaryJson || "Summary еще не сформирован.";
}

function buildSourceStates(run) {
  const states = {};
  (run.events || []).forEach(event => {
    if (!event.sourceName) return;
    const source = states[event.sourceName] || {
      status: "PENDING",
      rowCount: 0,
      columns: [],
      errorMessage: null
    };
    switch (detectEventKind(event)) {
      case "sourceStarted":
        source.status = "RUNNING";
        break;
      case "sourceProgress":
        source.status = source.status === "PENDING" ? "RUNNING" : source.status;
        source.rowCount = event.rowCount || source.rowCount;
        break;
      case "sourceFinished":
        source.status = event.status || source.status;
        source.rowCount = event.rowCount || source.rowCount;
        source.columns = event.columns || source.columns;
        source.errorMessage = event.errorMessage || null;
        break;
      case "schemaMismatch":
        source.status = "SKIPPED_SCHEMA_MISMATCH";
        source.errorMessage = "Источник исключен из merge из-за несовпадения колонок.";
        source.columns = event.actualColumns || source.columns;
        break;
      default:
        break;
    }
    states[event.sourceName] = source;
  });
  return states;
}

function buildHumanTimeline(run, sourceStates) {
  const timeline = [];
  (run.events || []).forEach(event => {
    const entry = toHumanEvent(event, sourceStates);
    if (entry) timeline.push(entry);
  });
  return timeline.slice(-40);
}

function toHumanEvent(event, sourceStates) {
  switch (detectEventKind(event)) {
    case "runStarted":
      return { timestamp: event.timestamp, message: `Запуск начат. Источников: ${event.sourceNames.length}, режим merge: ${event.mergeMode}.` };
    case "sourceStarted":
      return { timestamp: event.timestamp, message: `Начата выгрузка из источника ${event.sourceName}.` };
    case "sourceProgress":
      return { timestamp: event.timestamp, message: `Источник ${event.sourceName}: выгружено ${event.rowCount} строк.` };
    case "sourceFinished":
      if (event.status === "SUCCESS") {
        return { timestamp: event.timestamp, message: `Источник ${event.sourceName} завершен успешно. Получено ${event.rowCount} строк.` };
      }
      return { timestamp: event.timestamp, message: `Источник ${event.sourceName} завершился с ошибкой: ${event.errorMessage || "неизвестная ошибка"}.` };
    case "schemaMismatch":
      return { timestamp: event.timestamp, message: `Источник ${event.sourceName} исключен из объединения: набор колонок отличается от базового.` };
    case "mergeStarted":
      return { timestamp: event.timestamp, message: `Начато объединение данных из ${event.sourceNames.length} успешных источников.` };
    case "mergeFinished":
      return { timestamp: event.timestamp, message: `Объединение завершено. В merged.csv записано ${event.rowCount} строк.` };
    case "targetStarted":
      return { timestamp: event.timestamp, message: `Начата загрузка merged.csv в таблицу ${event.table}.` };
    case "targetFinished":
      if (event.status === "SUCCESS") {
        return { timestamp: event.timestamp, message: `Загрузка в таблицу ${event.table} завершена. Загружено ${event.rowCount} строк.` };
      }
      if (event.status === "SKIPPED") {
        return { timestamp: event.timestamp, message: `Загрузка в target пропущена.` };
      }
      return { timestamp: event.timestamp, message: `Загрузка в таблицу ${event.table} завершилась ошибкой: ${event.errorMessage || "неизвестная ошибка"}.` };
    case "outputCleanup":
      return { timestamp: event.timestamp, message: `Удален временный файл ${event.fileName}.` };
    case "runFinished":
      if (event.status === "SUCCESS") {
        return { timestamp: event.timestamp, message: `Запуск завершен успешно.` };
      }
      return { timestamp: event.timestamp, message: `Запуск завершен с ошибкой: ${event.errorMessage || "неизвестная ошибка"}.` };
    default:
      return null;
  }
}

function renderSourceStatusTable(sourceStates) {
  const rows = Object.entries(sourceStates);
  if (rows.length === 0) {
    return `<div class="text-secondary small">Информация по источникам появится после старта запуска.</div>`;
  }

  return `
    <table class="table source-status-table align-middle mb-0">
      <thead>
        <tr>
          <th>Источник</th>
          <th>Статус</th>
          <th>Строк</th>
          <th>Ошибка</th>
        </tr>
      </thead>
      <tbody>
        ${rows.sort((a, b) => a[0].localeCompare(b[0])).map(([source, state]) => `
          <tr>
            <td><strong>${source}</strong></td>
            <td><span class="status-badge status-${(state.status || "PENDING").toLowerCase()}">${translateStatus(state.status)}</span></td>
            <td>${state.rowCount || 0}</td>
            <td>${state.errorMessage || "-"}</td>
          </tr>
        `).join("")}
      </tbody>
    </table>
  `;
}

function detectCurrentStage(run) {
  if (run.status === "SUCCESS") return "Завершено";
  if (run.status === "FAILED") return "Завершено с ошибкой";
  const events = run.events || [];
  if (events.some(event => detectEventKind(event) === "targetStarted")) return "Загрузка в target";
  if (events.some(event => detectEventKind(event) === "mergeStarted")) return "Объединение";
  if (events.some(event => detectEventKind(event) === "sourceStarted")) return "Выгрузка из источников";
  return "Подготовка";
}

function detectEventKind(event) {
  if (event.type) return normalizeEventType(event.type);
  if (Array.isArray(event.sourceNames) && "mergeMode" in event && "targetEnabled" in event) return "runStarted";
  if ("fileName" in event && !("sourceName" in event)) return "outputCleanup";
  if ("summaryFile" in event && "mergedRowCount" in event && "status" in event) return "runFinished";
  if ("table" in event && "expectedRowCount" in event) return "targetStarted";
  if ("table" in event && "rowCount" in event && "status" in event) return "targetFinished";
  if (Array.isArray(event.sourceCounts) || (event.sourceCounts && typeof event.sourceCounts === "object")) return "mergeFinished";
  if (Array.isArray(event.sourceNames) && "outputFile" in event) return "mergeStarted";
  if ("expectedColumns" in event && "actualColumns" in event) return "schemaMismatch";
  if ("sourceName" in event && "columns" in event && "status" in event) return "sourceFinished";
  if ("sourceName" in event && "rowCount" in event) return "sourceProgress";
  if ("sourceName" in event) return "sourceStarted";
  return "unknown";
}

function normalizeEventType(type) {
  const simple = String(type).split(".").pop();
  switch (simple) {
    case "RunStartedEvent": return "runStarted";
    case "SourceExportStartedEvent": return "sourceStarted";
    case "SourceExportProgressEvent": return "sourceProgress";
    case "SourceExportFinishedEvent": return "sourceFinished";
    case "SourceSchemaMismatchEvent": return "schemaMismatch";
    case "MergeStartedEvent": return "mergeStarted";
    case "MergeFinishedEvent": return "mergeFinished";
    case "TargetImportStartedEvent": return "targetStarted";
    case "TargetImportFinishedEvent": return "targetFinished";
    case "OutputCleanupEvent": return "outputCleanup";
    case "RunFinishedEvent": return "runFinished";
    default: return "unknown";
  }
}

function translateStatus(status) {
  switch (status) {
    case "RUNNING": return "Выполняется";
    case "SUCCESS": return "Успешно";
    case "FAILED": return "Ошибка";
    case "SKIPPED": return "Пропущено";
    case "SKIPPED_SCHEMA_MISMATCH": return "Пропущено из-за схемы";
    case "PENDING": return "Ожидание";
    default: return status || "-";
  }
}

function formatDateTime(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("ru-RU");
}

function renderCredentialsStatus(status) {
  if (!status) {
    credentialsStatus.textContent = "Файл не задан.";
    return;
  }
  const sourceLabel = status.uploaded ? "загружен через UI" : (status.mode === "FILE" ? "файл по умолчанию" : "файл не задан");
  const availability = status.fileAvailable ? "доступен" : "не найден";
  credentialsStatus.textContent = `${sourceLabel}: ${status.displayName} (${availability})`;
}

function renderCredentialsWarning() {
  if (!currentModule) {
    credentialsWarning.classList.add("d-none");
    return;
  }
  const missingCredentials = currentModule.requiresCredentials && (!currentCredentialsStatus || !currentCredentialsStatus.fileAvailable);
  if (!missingCredentials) {
    credentialsWarning.classList.add("d-none");
    credentialsWarning.textContent = "";
    return;
  }
  credentialsWarning.classList.remove("d-none");
  credentialsWarning.textContent = "В конфиге модуля найдены placeholders ${...}, но credential.properties не загружен и не найден по fallback-настройкам UI.";
}
