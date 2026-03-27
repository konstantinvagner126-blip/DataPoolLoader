let configEditor;
let sqlEditor;
let selectedModuleId = null;
let currentModule = null;
let currentSqlPath = null;
let sqlContents = {};
let persistedConfigText = "";
let persistedSqlContents = {};
let currentCredentialsStatus = null;
let isApplyingConfigFromForm = false;
let isUpdatingFormFromYaml = false;
let configSyncDebounceId = null;
let configSyncRequestId = 0;
let configApplyRequestId = 0;
let activeConfigSectionId = "configSectionGeneral";
let expandedConfigCards = {
  general: true,
  sql: true,
  sources: true,
  quotas: true,
  target: true
};
let expandedSourceCards = new Set();
let expandedQuotaCards = new Set();

const moduleList = document.getElementById("moduleList");
const selectedModuleLabel = document.getElementById("selectedModuleLabel");
const moduleDraftStatus = document.getElementById("moduleDraftStatus");
const sqlFileSelect = document.getElementById("sqlFileSelect");
const configForm = document.getElementById("configForm");
const configFormWarning = document.getElementById("configFormWarning");
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

  configEditor.onDidChangeModelContent(() => {
    if (isApplyingConfigFromForm) {
      return;
    }
    updateDraftState();
    window.clearTimeout(configSyncDebounceId);
    configSyncDebounceId = window.setTimeout(() => {
      syncFormWithYaml();
    }, 120);
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
      updateDraftState();
    }
  });

  loadModules();
  refreshCredentialsStatus();
  connectWebSocket();
  renderConfigForm(buildDefaultFormState());
});

document.getElementById("reloadButton").addEventListener("click", () => {
  if (selectedModuleId) {
    if (hasUnsavedChanges() && !window.confirm("Несохраненные изменения будут потеряны. Продолжить?")) {
      return;
    }
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
  persistedConfigText = configEditor.getValue();
  persistedSqlContents = cloneSqlContents(sqlContents);
  updateDraftState();
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
  expandedConfigCards = {
    general: true,
    sql: true,
    sources: true,
    quotas: true,
    target: true
  };
  expandedSourceCards = new Set();
  expandedQuotaCards = new Set();
  selectedModuleLabel.textContent = `${currentModule.title} · ${currentModule.configPath}`;
  persistedConfigText = currentModule.configText;
  configEditor.setValue(currentModule.configText);
  sqlContents = {};
  currentModule.sqlFiles.forEach(file => {
    sqlContents[file.path] = file.content;
  });
  persistedSqlContents = cloneSqlContents(sqlContents);
  renderSqlSelect();
  renderCredentialsWarning();
  updateDraftState();
  [...moduleList.children].forEach(button => {
    button.classList.toggle("active", button.dataset.moduleId === currentModule.id);
  });
  syncFormWithYaml();
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

function cloneSqlContents(source) {
  return JSON.parse(JSON.stringify(source || {}));
}

function hasUnsavedChanges() {
  if (!configEditor) {
    return false;
  }
  if ((persistedConfigText || "") !== configEditor.getValue()) {
    return true;
  }
  const currentKeys = Object.keys(sqlContents).sort();
  const persistedKeys = Object.keys(persistedSqlContents).sort();
  if (JSON.stringify(currentKeys) !== JSON.stringify(persistedKeys)) {
    return true;
  }
  return currentKeys.some(key => (sqlContents[key] || "") !== (persistedSqlContents[key] || ""));
}

function updateDraftState() {
  const dirty = hasUnsavedChanges();
  document.getElementById("saveButton").disabled = !dirty || !selectedModuleId;
  if (!selectedModuleId) {
    moduleDraftStatus.innerHTML = `
      <span class="module-draft-dot module-draft-dot-neutral" aria-hidden="true"></span>
      <span>Модуль не выбран.</span>
    `;
    moduleDraftStatus.className = "small mt-1 text-secondary";
    return;
  }
  if (dirty) {
    moduleDraftStatus.innerHTML = `
      <span class="module-draft-dot module-draft-dot-dirty" aria-hidden="true"></span>
      <span>Есть несохраненные изменения. Запуск использует текущие правки.</span>
    `;
    moduleDraftStatus.className = "module-draft-status small mt-1 text-primary";
    return;
  }
  moduleDraftStatus.innerHTML = `
    <span class="module-draft-dot module-draft-dot-saved" aria-hidden="true"></span>
    <span>Изменений нет.</span>
  `;
  moduleDraftStatus.className = "module-draft-status small mt-1 text-secondary";
}

function buildDefaultFormState() {
  return {
    outputDir: "./output",
    fileFormat: "csv",
    mergeMode: "plain",
    errorMode: "continue_on_error",
    parallelism: 5,
    fetchSize: 1000,
    queryTimeoutSec: "",
    progressLogEveryRows: 10000,
    maxMergedRows: "",
    deleteOutputFilesAfterCompletion: false,
    commonSql: "",
    commonSqlFile: "",
    sources: [buildEmptySourceState()],
    quotas: [],
    targetEnabled: true,
    targetJdbcUrl: "",
    targetUsername: "",
    targetPassword: "",
    targetTable: "",
    targetTruncateBeforeLoad: false
  };
}

function buildEmptySourceState() {
  return {
    name: "",
    jdbcUrl: "",
    username: "",
    password: "",
    sql: "",
    sqlFile: ""
  };
}

function buildEmptyQuotaState() {
  return {
    source: "",
    percent: ""
  };
}

async function syncFormWithYaml() {
  if (!configEditor || isUpdatingFormFromYaml) {
    return;
  }

  const requestId = ++configSyncRequestId;
  try {
    const response = await fetch("/api/config-form/parse", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ configText: configEditor.getValue() })
    });
    if (!response.ok) {
      const errorBody = await safeReadJsonError(response);
      throw new Error(errorBody);
    }
    if (requestId !== configSyncRequestId) {
      return;
    }
    const formState = await response.json();
    renderConfigForm(formState);
    setFormWarning(null);
  } catch (error) {
    if (requestId !== configSyncRequestId) {
      return;
    }
    renderConfigForm(buildDefaultFormState(), true);
    setFormWarning(error.message || "Не удалось разобрать application.yml для визуальной формы.");
  }
}

function renderConfigForm(state, disabled = false) {
  const currentActiveSection = configForm.querySelector(".config-section-tabs .nav-link.active")?.dataset.configSectionTarget;
  if (currentActiveSection) {
    activeConfigSectionId = currentActiveSection;
  }

  if (disabled) {
    configForm.innerHTML = `
      <div class="config-form-empty-state">
        <div class="config-form-empty-title">Визуальная форма временно недоступна</div>
        <div class="config-form-empty-text">
          Исправь структуру или синтаксис <code>application.yml</code>, после чего форма снова соберется автоматически.
        </div>
        <button class="btn btn-outline-primary" type="button" data-open-yaml-editor="true">Перейти к application.yml</button>
      </div>
    `;
    configForm.querySelector("[data-open-yaml-editor='true']")?.addEventListener("click", () => {
      document.querySelector('[data-bs-target="#configTab"]')?.click();
    });
    return;
  }

  configForm.innerHTML = `
    <ul class="nav nav-pills config-section-tabs mb-3" role="tablist">
      <li class="nav-item" role="presentation">
        <button class="nav-link ${activeConfigSectionId === "configSectionGeneral" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionGeneral" data-config-section-target="configSectionGeneral" type="button">Общие</button>
      </li>
      <li class="nav-item" role="presentation">
        <button class="nav-link ${activeConfigSectionId === "configSectionSql" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionSql" data-config-section-target="configSectionSql" type="button">SQL</button>
      </li>
      <li class="nav-item" role="presentation">
        <button class="nav-link ${activeConfigSectionId === "configSectionSources" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionSources" data-config-section-target="configSectionSources" type="button">Источники</button>
      </li>
      <li class="nav-item" role="presentation">
        <button class="nav-link ${activeConfigSectionId === "configSectionQuotas" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionQuotas" data-config-section-target="configSectionQuotas" type="button">Квоты</button>
      </li>
      <li class="nav-item" role="presentation">
        <button class="nav-link ${activeConfigSectionId === "configSectionTarget" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionTarget" data-config-section-target="configSectionTarget" type="button">Target</button>
      </li>
    </ul>

    <div class="tab-content config-section-content">
      <div class="tab-pane fade ${activeConfigSectionId === "configSectionGeneral" ? "show active" : ""}" id="configSectionGeneral">
        ${renderCollapsibleConfigCard("general", "Общие настройки", `
          <div class="config-form-fields">
            ${renderTextField("outputDir", "Каталог output", state.outputDir, disabled, "Обычно оставляем относительный путь ./output.")}
            ${renderSelectField("fileFormat", "Формат файла", state.fileFormat, [
              ["csv", "csv"]
            ], disabled, "Сейчас поддерживается только CSV.")}
            ${renderSelectField("mergeMode", "Режим merge", state.mergeMode, [
              ["plain", "plain"],
              ["round_robin", "round_robin"],
              ["proportional", "proportional"],
              ["quota", "quota"]
            ], disabled, "Как объединять данные из source БД.")}
            ${renderSelectField("errorMode", "Режим ошибок", state.errorMode, [
              ["continue_on_error", "continue_on_error"]
            ], disabled, "Сейчас поддерживается продолжение обработки при ошибке источника.")}
            ${renderNumberField("parallelism", "Параллелизм", state.parallelism, disabled, false, "Сколько source обрабатывать одновременно.")}
            ${renderNumberField("fetchSize", "Fetch size", state.fetchSize, disabled, false, "Размер JDBC-порции при чтении результата.")}
            ${renderNumberField("queryTimeoutSec", "Query timeout (сек)", state.queryTimeoutSec, disabled, true, "Если пусто, явный timeout не задается.")}
            ${renderNumberField("progressLogEveryRows", "Логировать каждые N строк", state.progressLogEveryRows, disabled, false, "Частота progress-логов по source.")}
            ${renderNumberField("maxMergedRows", "Максимум строк в merged", state.maxMergedRows, disabled, true, "Если пусто, итоговый файл не ограничивается.")}
            ${renderCheckboxField("deleteOutputFilesAfterCompletion", "Удалять выходные файлы после завершения", state.deleteOutputFilesAfterCompletion, disabled, "Оставить только summary и результат загрузки.")}
          </div>
        `)}
      </div>

      <div class="tab-pane fade ${activeConfigSectionId === "configSectionSql" ? "show active" : ""}" id="configSectionSql">
        ${renderCollapsibleConfigCard("sql", "SQL по умолчанию", `
          <div class="config-form-fields">
            ${renderTextareaField("commonSql", "commonSql", state.commonSql, disabled, "Общий SQL для всех sources, если у источника не указан свой sql/sqlFile.", 6)}
            ${renderTextField("commonSqlFile", "commonSqlFile", state.commonSqlFile, disabled, "Например classpath:sql/common.sql или относительный путь к файлу.")}
          </div>
        `)}
      </div>

      <div class="tab-pane fade ${activeConfigSectionId === "configSectionSources" ? "show active" : ""}" id="configSectionSources">
        ${renderCollapsibleConfigCard("sources", "Источники БД", `
          <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
            <button class="btn btn-outline-primary btn-sm" type="button" data-config-action="add-source" ${disabled ? "disabled" : ""}>Добавить source</button>
            <div class="config-form-help ms-auto">${state.sources.length} шт.</div>
          </div>
          <div class="config-form-list">
            ${state.sources.map((source, index) => renderSourceCard(source, index, disabled)).join("")}
          </div>
        `)}
      </div>

      <div class="tab-pane fade ${activeConfigSectionId === "configSectionQuotas" ? "show active" : ""}" id="configSectionQuotas">
        ${renderCollapsibleConfigCard("quotas", "Квоты", `
          <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
            <div>
              <div class="config-form-help">Используются в режиме <code>quota</code>.</div>
            </div>
            <button class="btn btn-outline-primary btn-sm" type="button" data-config-action="add-quota" ${disabled ? "disabled" : ""}>Добавить quota</button>
            <div class="config-form-help ms-auto">${state.quotas.length} шт.</div>
          </div>
          <div class="config-form-list">
            ${state.quotas.length > 0 ? state.quotas.map((quota, index) => renderQuotaRow(quota, index, state.sources, disabled)).join("") : `
              <div class="config-form-inline-note">Квоты не заданы.</div>
            `}
          </div>
        `)}
      </div>

      <div class="tab-pane fade ${activeConfigSectionId === "configSectionTarget" ? "show active" : ""}" id="configSectionTarget">
        ${renderCollapsibleConfigCard("target", "Target", `
          <div class="config-form-fields">
            ${renderRadioGroupField("targetEnabled", "Загрузка в target БД", state.targetEnabled ? "true" : "false", [
              ["true", "Включена"],
              ["false", "Выключена"]
            ], disabled, "Если выключено, merged.csv формируется, но загрузка в БД не выполняется.")}
            ${renderTextField("targetJdbcUrl", "jdbcUrl", state.targetJdbcUrl, disabled, "Подключение к target PostgreSQL.")}
            ${renderTextField("targetUsername", "username", state.targetUsername, disabled, "Пользователь target БД.")}
            ${renderTextField("targetPassword", "password", state.targetPassword, disabled, "Пароль target БД.")}
            ${renderTextField("targetTable", "Целевая таблица", state.targetTable, disabled, "Например schema.table.")}
            ${renderCheckboxField("targetTruncateBeforeLoad", "Очищать target таблицу перед загрузкой", state.targetTruncateBeforeLoad, disabled, "Перед импортом выполнить TRUNCATE TABLE.")}
          </div>
        `)}
      </div>
    </div>
  `;

  bindConfigFormEvents(disabled);
}

function bindConfigFormEvents(disabled) {
  if (disabled) {
    return;
  }

  configForm.querySelectorAll("[data-config-section-target]").forEach(button => {
    button.addEventListener("click", () => {
      activeConfigSectionId = button.dataset.configSectionTarget || "configSectionGeneral";
    });
  });

  configForm.querySelectorAll("[data-config-card-key]").forEach(details => {
    details.addEventListener("toggle", () => {
      expandedConfigCards[details.dataset.configCardKey] = details.open;
    });
  });

  configForm.querySelectorAll("[data-config-source-card-index]").forEach(details => {
    details.addEventListener("toggle", () => {
      const index = Number(details.dataset.configSourceCardIndex);
      if (details.open) {
        expandedSourceCards.add(index);
      } else {
        expandedSourceCards.delete(index);
      }
    });
  });

  configForm.querySelectorAll("[data-config-quota-card-index]").forEach(details => {
    details.addEventListener("toggle", () => {
      const index = Number(details.dataset.configQuotaCardIndex);
      if (details.open) {
        expandedQuotaCards.add(index);
      } else {
        expandedQuotaCards.delete(index);
      }
    });
  });

  configForm.querySelectorAll("[data-config-field]").forEach(element => {
    const eventName = element.type === "checkbox" || element.type === "radio" || element.tagName === "SELECT" ? "change" : "input";
    element.addEventListener(eventName, () => applyFormToYaml());
  });

  configForm.querySelectorAll("[data-config-source-field]").forEach(element => {
    const eventName = element.type === "checkbox" || element.tagName === "SELECT" ? "change" : "input";
    element.addEventListener(eventName, () => applyFormToYaml());
  });

  configForm.querySelectorAll("[data-config-quota-field]").forEach(element => {
    const eventName = element.type === "checkbox" || element.tagName === "SELECT" ? "change" : "input";
    element.addEventListener(eventName, () => applyFormToYaml());
  });

  configForm.querySelectorAll("[data-config-action]").forEach(button => {
    button.addEventListener("click", (event) => {
      event.preventDefault();
      event.stopPropagation();
      handleConfigAction(button.dataset.configAction, button.dataset.index);
    });
  });
}

function handleConfigAction(action, indexValue) {
  activeConfigSectionId = configForm.querySelector(".config-section-tabs .nav-link.active")?.dataset.configSectionTarget || activeConfigSectionId;
  const state = readFormState();
  const index = Number(indexValue);
  switch (action) {
    case "add-source":
      state.sources.push(buildEmptySourceState());
      break;
    case "remove-source":
      state.sources.splice(index, 1);
      if (state.sources.length === 0) {
        state.sources.push(buildEmptySourceState());
      }
      break;
    case "add-quota":
      state.quotas.push(buildEmptyQuotaState());
      break;
    case "remove-quota":
      state.quotas.splice(index, 1);
      break;
    default:
      return;
  }
  renderConfigForm(state);
  applyFormToYaml();
}

async function applyFormToYaml() {
  if (isUpdatingFormFromYaml) {
    return;
  }

  const requestId = ++configApplyRequestId;
  try {
    const formState = readFormState();
    const response = await fetch("/api/config-form/update", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        configText: configEditor.getValue(),
        formState
      })
    });
    if (!response.ok) {
      const errorBody = await safeReadJsonError(response);
      throw new Error(errorBody);
    }
    const payload = await response.json();
    if (requestId !== configApplyRequestId) {
      return;
    }

    isApplyingConfigFromForm = true;
    configEditor.setValue(payload.configText);
    isApplyingConfigFromForm = false;
    renderConfigForm(payload.formState);
    updateDraftState();
    setFormWarning(null);
  } catch (error) {
    isApplyingConfigFromForm = false;
    setFormWarning(error.message || "Не удалось синхронизировать YAML с формой.");
  }
}

function readFormState() {
  const sourceIndexes = uniqueIndexes("data-config-source-index");
  const quotaIndexes = uniqueIndexes("data-config-quota-index");
  return {
    outputDir: getFormFieldValue("outputDir"),
    fileFormat: getFormFieldValue("fileFormat"),
    mergeMode: getFormFieldValue("mergeMode"),
    errorMode: getFormFieldValue("errorMode"),
    parallelism: requiredNumber(getFormFieldValue("parallelism"), "parallelism"),
    fetchSize: requiredNumber(getFormFieldValue("fetchSize"), "fetchSize"),
    queryTimeoutSec: optionalNumber(getFormFieldValue("queryTimeoutSec")),
    progressLogEveryRows: requiredNumber(getFormFieldValue("progressLogEveryRows"), "progressLogEveryRows"),
    maxMergedRows: optionalNumber(getFormFieldValue("maxMergedRows")),
    deleteOutputFilesAfterCompletion: getCheckboxValue("deleteOutputFilesAfterCompletion"),
    commonSql: getFormFieldValue("commonSql"),
    commonSqlFile: emptyToNull(getFormFieldValue("commonSqlFile")),
    sources: sourceIndexes.map(index => ({
      name: getIndexedFieldValue("source", index, "name"),
      jdbcUrl: getIndexedFieldValue("source", index, "jdbcUrl"),
      username: getIndexedFieldValue("source", index, "username"),
      password: getIndexedFieldValue("source", index, "password"),
      sql: emptyToNull(getIndexedFieldValue("source", index, "sql")),
      sqlFile: emptyToNull(getIndexedFieldValue("source", index, "sqlFile"))
    })),
    quotas: quotaIndexes.map(index => ({
      source: getIndexedFieldValue("quota", index, "source"),
      percent: optionalNumber(getIndexedFieldValue("quota", index, "percent"))
    })),
    targetEnabled: getFormFieldValue("targetEnabled") === "true",
    targetJdbcUrl: getFormFieldValue("targetJdbcUrl"),
    targetUsername: getFormFieldValue("targetUsername"),
    targetPassword: getFormFieldValue("targetPassword"),
    targetTable: getFormFieldValue("targetTable"),
    targetTruncateBeforeLoad: getCheckboxValue("targetTruncateBeforeLoad")
  };
}

function getFormFieldValue(fieldName) {
  const checkedRadio = configForm.querySelector(`[data-config-field="${fieldName}"]:checked`);
  if (checkedRadio) {
    return checkedRadio.value ?? "";
  }
  return configForm.querySelector(`[data-config-field="${fieldName}"]`)?.value ?? "";
}

function getCheckboxValue(fieldName) {
  return Boolean(configForm.querySelector(`[data-config-field="${fieldName}"]`)?.checked);
}

function getIndexedFieldValue(kind, index, fieldName) {
  return configForm.querySelector(`[data-config-${kind}-index="${index}"][data-config-${kind}-field="${fieldName}"]`)?.value ?? "";
}

function uniqueIndexes(attributeName) {
  return [...configForm.querySelectorAll(`[${attributeName}]`)]
    .map(element => Number(element.getAttribute(attributeName)))
    .filter(value => !Number.isNaN(value))
    .filter((value, index, array) => array.indexOf(value) === index)
    .sort((a, b) => a - b);
}

function emptyToNull(value) {
  const normalized = String(value ?? "").trim();
  return normalized ? normalized : null;
}

function requiredNumber(rawValue, fieldName) {
  const normalized = String(rawValue).trim();
  const numeric = Number(normalized);
  if (!normalized || Number.isNaN(numeric)) {
    throw new Error(`Поле ${fieldName} должно быть числом.`);
  }
  return numeric;
}

function optionalNumber(rawValue) {
  const normalized = String(rawValue).trim();
  if (!normalized) {
    return null;
  }
  const numeric = Number(normalized);
  if (Number.isNaN(numeric)) {
    throw new Error("Одно из числовых полей формы заполнено некорректно.");
  }
  return numeric;
}

function renderTextField(fieldName, label, value, disabled, helpText = "") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <input
        class="form-control"
        type="text"
        data-config-field="${fieldName}"
        value="${escapeHtml(value ?? "")}"
        ${disabled ? "disabled" : ""}
      >
    </label>
  `;
}

function renderTextareaField(fieldName, label, value, disabled, helpText = "", rows = 4) {
  return `
    <label class="config-form-field config-form-field-wide">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <textarea
        class="form-control"
        rows="${rows}"
        data-config-field="${fieldName}"
        ${disabled ? "disabled" : ""}
      >${escapeHtml(value ?? "")}</textarea>
    </label>
  `;
}

function renderNumberField(fieldName, label, value, disabled, optional = false, helpText = "") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      <span class="config-form-help">${helpText || (optional ? "Можно оставить пустым." : "Обязательное числовое поле.")}</span>
      <input
        class="form-control"
        type="number"
        data-config-field="${fieldName}"
        value="${escapeHtml(value ?? "")}"
        ${disabled ? "disabled" : ""}
      >
    </label>
  `;
}

function renderSelectField(fieldName, label, value, options, disabled, helpText = "") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <select class="form-select" data-config-field="${fieldName}" ${disabled ? "disabled" : ""}>
        ${options.map(([optionValue, optionLabel]) => `
          <option value="${escapeHtml(optionValue)}" ${value === optionValue ? "selected" : ""}>${escapeHtml(optionLabel)}</option>
        `).join("")}
      </select>
    </label>
  `;
}

function renderCheckboxField(fieldName, label, checked, disabled, helpText = "") {
  return `
    <div class="config-form-check-group">
      <label class="config-form-check">
        <input
          class="form-check-input"
          type="checkbox"
          data-config-field="${fieldName}"
          ${checked ? "checked" : ""}
          ${disabled ? "disabled" : ""}
        >
        <span>${label}</span>
      </label>
      ${helpText ? `<div class="config-form-help">${helpText}</div>` : ""}
    </div>
  `;
}

function renderRadioGroupField(fieldName, label, value, options, disabled, helpText = "") {
  return `
    <div class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <div class="config-radio-group">
        ${options.map(([optionValue, optionLabel]) => `
          <label class="config-radio-option">
            <input
              type="radio"
              name="${fieldName}"
              data-config-field="${fieldName}"
              value="${escapeHtml(optionValue)}"
              ${value === optionValue ? "checked" : ""}
              ${disabled ? "disabled" : ""}
            >
            <span>${escapeHtml(optionLabel)}</span>
          </label>
        `).join("")}
      </div>
    </div>
  `;
}

function renderCollapsibleConfigCard(cardKey, title, bodyHtml) {
  const isOpen = expandedConfigCards[cardKey] !== false;
  return `
    <details class="config-form-card config-form-collapsible-card" data-config-card-key="${cardKey}" ${isOpen ? "open" : ""}>
      <summary class="config-form-card-summary">
        <span class="config-form-card-title mb-0">${title}</span>
        <span class="config-collapse-indicator" aria-hidden="true"></span>
      </summary>
      <div class="config-form-card-body">
        ${bodyHtml}
      </div>
    </details>
  `;
}

function renderIndexedTextField(kind, index, fieldName, label, value, disabled, helpText = "") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <input
        class="form-control"
        type="text"
        data-config-${kind}-index="${index}"
        data-config-${kind}-field="${fieldName}"
        value="${escapeHtml(value ?? "")}"
        ${disabled ? "disabled" : ""}
      >
    </label>
  `;
}

function renderIndexedNumberField(kind, index, fieldName, label, value, disabled, helpText = "", step = "any") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <input
        class="form-control"
        type="number"
        step="${step}"
        data-config-${kind}-index="${index}"
        data-config-${kind}-field="${fieldName}"
        value="${escapeHtml(value ?? "")}"
        ${disabled ? "disabled" : ""}
      >
    </label>
  `;
}

function renderIndexedTextareaField(kind, index, fieldName, label, value, disabled, helpText = "", rows = 5) {
  return `
    <label class="config-form-field config-form-field-wide">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <textarea
        class="form-control"
        rows="${rows}"
        data-config-${kind}-index="${index}"
        data-config-${kind}-field="${fieldName}"
        ${disabled ? "disabled" : ""}
      >${escapeHtml(value ?? "")}</textarea>
    </label>
  `;
}

function renderSourceCard(source, index, disabled) {
  const isOpen = expandedSourceCards.has(index);
  const title = source.name?.trim() ? source.name : `Source ${index + 1}`;
  const subtitle = source.jdbcUrl?.trim() || "jdbcUrl не задан";
  return `
    <details class="config-form-subcard config-form-subcard-collapsible" data-config-source-card-index="${index}" ${isOpen ? "open" : ""}>
      <summary class="config-form-subcard-summary">
        <div class="config-form-subcard-summary-text">
          <div class="config-form-subtitle">${escapeHtml(title)}</div>
          <div class="config-form-help">${escapeHtml(subtitle)}</div>
        </div>
        <div class="config-form-subcard-summary-actions">
          <button class="btn btn-outline-danger btn-sm" type="button" data-config-action="remove-source" data-index="${index}" ${disabled ? "disabled" : ""}>Удалить</button>
          <span class="config-collapse-indicator" aria-hidden="true"></span>
        </div>
      </summary>
      <div class="config-form-subcard-body">
        <div class="config-form-fields">
          ${renderIndexedTextField("source", index, "name", "name", source.name, disabled, "Уникальное имя источника.")}
          ${renderIndexedTextField("source", index, "jdbcUrl", "jdbcUrl", source.jdbcUrl, disabled, "Подключение к source PostgreSQL.")}
          ${renderIndexedTextField("source", index, "username", "username", source.username, disabled)}
          ${renderIndexedTextField("source", index, "password", "password", source.password, disabled)}
          ${renderIndexedTextField("source", index, "sqlFile", "sqlFile", source.sqlFile, disabled, "Путь к SQL-файлу, если SQL хранится отдельно.")}
          ${renderIndexedTextareaField("source", index, "sql", "sql", source.sql, disabled, "SQL для конкретного источника. Если пусто, используется commonSql/commonSqlFile.", 5)}
        </div>
      </div>
    </details>
  `;
}

function renderQuotaRow(quota, index, sources, disabled) {
  const isOpen = expandedQuotaCards.has(index);
  const sourceOptions = sources.map(source => [source.name, source.name]).filter(([value]) => value);
  const selectOptions = quota.source && !sourceOptions.some(([value]) => value === quota.source)
    ? [[quota.source, quota.source], ...sourceOptions]
    : sourceOptions;
  const title = quota.source?.trim() ? quota.source : `Quota ${index + 1}`;
  const subtitle = quota.percent !== null && quota.percent !== undefined && quota.percent !== "" ? `${quota.percent}%` : "Процент не задан";
  return `
    <details class="config-form-subcard config-form-subcard-collapsible" data-config-quota-card-index="${index}" ${isOpen ? "open" : ""}>
      <summary class="config-form-subcard-summary">
        <div class="config-form-subcard-summary-text">
          <div class="config-form-subtitle">${escapeHtml(title)}</div>
          <div class="config-form-help">${escapeHtml(subtitle)}</div>
        </div>
        <div class="config-form-subcard-summary-actions">
          <button class="btn btn-outline-danger btn-sm" type="button" data-config-action="remove-quota" data-index="${index}" ${disabled ? "disabled" : ""}>Удалить</button>
          <span class="config-collapse-indicator" aria-hidden="true"></span>
        </div>
      </summary>
      <div class="config-form-subcard-body">
        <div class="config-form-fields">
          ${selectOptions.length > 0
            ? renderIndexedSelectField("quota", index, "source", "source", quota.source, selectOptions, disabled, "Источник, для которого задается процент.")
            : renderIndexedTextField("quota", index, "source", "source", quota.source, disabled, "Имя источника для quota.")}
          ${renderIndexedNumberField("quota", index, "percent", "percent", quota.percent, disabled, "Процент для режима quota.", "0.01")}
        </div>
      </div>
    </details>
  `;
}

function renderIndexedSelectField(kind, index, fieldName, label, value, options, disabled, helpText = "") {
  return `
    <label class="config-form-field">
      <span class="config-form-label">${label}</span>
      ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
      <select
        class="form-select"
        data-config-${kind}-index="${index}"
        data-config-${kind}-field="${fieldName}"
        ${disabled ? "disabled" : ""}
      >
        <option value=""></option>
        ${options.map(([optionValue, optionLabel]) => `
          <option value="${escapeHtml(optionValue)}" ${value === optionValue ? "selected" : ""}>${escapeHtml(optionLabel)}</option>
        `).join("")}
      </select>
    </label>
  `;
}

function setFormWarning(message) {
  if (!message) {
    configFormWarning.classList.add("d-none");
    configFormWarning.textContent = "";
    return;
  }
  configFormWarning.classList.remove("d-none");
  configFormWarning.innerHTML = `
    <div><strong>Форма временно недоступна.</strong></div>
    <div class="mt-1">${escapeHtml(message)}</div>
  `;
}

async function safeReadJsonError(response) {
  try {
    const payload = await response.json();
    return payload.error || "Неизвестная ошибка.";
  } catch (_) {
    return `HTTP ${response.status}`;
  }
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll("\"", "&quot;")
    .replaceAll("'", "&#39;");
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
