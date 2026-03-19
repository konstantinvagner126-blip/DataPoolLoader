let configEditor;
let sqlEditor;
let selectedModuleId = null;
let currentModule = null;
let currentSqlPath = null;
let sqlContents = {};

const moduleList = document.getElementById("moduleList");
const selectedModuleLabel = document.getElementById("selectedModuleLabel");
const sqlFileSelect = document.getElementById("sqlFileSelect");
const runSummary = document.getElementById("runSummary");
const sourceProgress = document.getElementById("sourceProgress");
const eventLog = document.getElementById("eventLog");
const summaryJson = document.getElementById("summaryJson");

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
  [...moduleList.children].forEach(button => {
    button.classList.toggle("active", button.dataset.moduleId === currentModule.id);
  });
}

function renderSqlSelect() {
  sqlFileSelect.innerHTML = "";
  currentModule.sqlFiles.forEach((file, index) => {
    const option = document.createElement("option");
    option.value = file.path;
    option.textContent = file.path;
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
    renderState(state);
  };
}

function renderState(state) {
  const activeRun = state.activeRun;
  const latestRun = activeRun || (state.history && state.history.length > 0 ? state.history[0] : null);
  if (!latestRun) {
    runSummary.textContent = "Запусков пока нет.";
    sourceProgress.innerHTML = "";
    eventLog.textContent = "";
    summaryJson.textContent = "Пока нет данных summary.";
    return;
  }

  runSummary.innerHTML = `
    <div><strong>Модуль:</strong> ${latestRun.moduleTitle}</div>
    <div><strong>Статус:</strong> ${latestRun.status}</div>
    <div><strong>Старт:</strong> ${latestRun.startedAt}</div>
    <div><strong>Output:</strong> ${latestRun.outputDir || "-"}</div>
    <div><strong>merged rows:</strong> ${latestRun.mergedRowCount}</div>
    <div><strong>Ошибка:</strong> ${latestRun.errorMessage || "-"}</div>
  `;

  sourceProgress.innerHTML = Object.entries(latestRun.sourceProgress || {})
    .map(([source, count]) => `<div class="progress-row"><span>${source}</span><strong>${count}</strong></div>`)
    .join("");

  eventLog.textContent = (latestRun.events || [])
    .slice(-200)
    .map(event => JSON.stringify(event, null, 2))
    .join("\n\n");

  summaryJson.textContent = latestRun.summaryJson || "Summary еще не сформирован.";
}
