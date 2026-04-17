(function initDataPoolModulePage(global) {
  const common = global.DataPoolCommon || {};
  const moduleEditorNamespace = global.DataPoolModuleEditor || {};
  const runPanelsNamespace = global.DataPoolRunPanels || {};
  const { escapeHtml, fetchJson, postJson, postFormData, withMonacoReady, createMonacoEditor, loadJsonStorage, saveJsonStorage } = common;
  const { createConfigFormController } = moduleEditorNamespace;
  const { createRunPanelsController } = runPanelsNamespace;

  const MODULE_UI_STATE_STORAGE_KEY = "datapool.moduleEditor.uiState";

  let configEditor = null;
  let sqlEditor = null;
  let selectedModuleId = null;
  let currentModule = null;
  let currentSqlPath = null;
  let sqlContents = {};
  let persistedConfigText = "";
  let persistedSqlContents = {};
  let currentCredentialsStatus = null;

  const moduleList = document.getElementById("moduleList");
  const moduleCatalogStatus = document.getElementById("moduleCatalogStatus");
  const selectedModuleLabel = document.getElementById("selectedModuleLabel");
  const selectedModuleDescription = document.getElementById("selectedModuleDescription");
  const moduleDraftStatus = document.getElementById("moduleDraftStatus");
  const moduleValidationAlert = document.getElementById("moduleValidationAlert");
  const sqlFileSelect = document.getElementById("sqlFileSelect");
  const configForm = document.getElementById("configForm");
  const configFormWarning = document.getElementById("configFormWarning");
  const runSummary = document.getElementById("runSummary");
  const eventLog = document.getElementById("eventLog");
  const technicalEventLog = document.getElementById("technicalEventLog");
  const runHistoryFilters = document.getElementById("runHistoryFilters");
  const runHistory = document.getElementById("runHistory");
  const summaryStructured = document.getElementById("summaryStructured");
  const summaryJson = document.getElementById("summaryJson");
  const summaryRawPanel = document.getElementById("summaryRawPanel");
  const technicalDiagnosticsPanel = document.getElementById("technicalDiagnosticsPanel");
  const credentialsStatus = document.getElementById("credentialsStatus");
  const credentialsWarning = document.getElementById("credentialsWarning");
  const credentialsFileInput = document.getElementById("credentialsFileInput");
  const reloadButton = document.getElementById("reloadButton");
  const saveButton = document.getElementById("saveButton");
  const runButton = document.getElementById("runButton");
  const uploadCredentialsButton = document.getElementById("uploadCredentialsButton");

  const formController = createConfigFormController({
    configForm,
    configFormWarning,
    getConfigText: () => configEditor?.getValue() || "",
    setConfigText: value => {
      if (configEditor) {
        configEditor.setValue(value);
      }
    },
    getCurrentModuleId: () => currentModule?.id || selectedModuleId,
    onDraftStateChange: () => updateDraftState(),
    onPersistUiState: () => persistModuleUiState(),
    openYamlEditor: () => {
      document.querySelector('[data-bs-target="#configTab"]')?.click();
    }
  });

  const runPanelsController = createRunPanelsController({
    runSummaryEl: runSummary,
    eventLogEl: eventLog,
    technicalEventLogEl: technicalEventLog,
    runHistoryFiltersEl: runHistoryFilters,
    runHistoryEl: runHistory,
    summaryStructuredEl: summaryStructured,
    summaryJsonEl: summaryJson
  });

  reloadButton.addEventListener("click", () => {
    if (!selectedModuleId) {
      return;
    }
    if (hasUnsavedChanges() && !window.confirm("Несохраненные изменения будут потеряны. Продолжить?")) {
      return;
    }
    loadModule(selectedModuleId);
  });

  saveButton.addEventListener("click", async () => {
    if (!selectedModuleId) {
      return;
    }
    await postJson(`/api/modules/${selectedModuleId}/save`, {
      configText: configEditor.getValue(),
      sqlFiles: sqlContents
    }, "Не удалось сохранить изменения модуля.");
    persistedConfigText = configEditor.getValue();
    persistedSqlContents = cloneSqlContents(sqlContents);
    updateDraftState();
  });

  runButton.addEventListener("click", async () => {
    if (!selectedModuleId) {
      return;
    }
    await postJson("/api/runs", {
      moduleId: selectedModuleId,
      configText: configEditor.getValue(),
      sqlFiles: sqlContents
    }, "Не удалось запустить модуль.");
  });

  uploadCredentialsButton.addEventListener("click", async () => {
    const file = credentialsFileInput.files[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append("file", file);
    currentCredentialsStatus = await postFormData("/api/credentials/upload", formData, "Не удалось загрузить credential.properties.");
    renderCredentialsStatus(currentCredentialsStatus);
    renderCredentialsWarning();
  });

  sqlFileSelect.addEventListener("change", () => {
    currentSqlPath = sqlFileSelect.value || null;
    if (sqlEditor) {
      sqlEditor.setValue(currentSqlPath ? (sqlContents[currentSqlPath] || "") : "");
    }
  });

  global.addEventListener("pagehide", () => {
    formController.saveExpansionStateForCurrentModule();
    persistModuleUiState();
  });

  withMonacoReady(async () => {
    restorePersistedModuleUiState();

    configEditor = createMonacoEditor("configEditor", {
      value: "",
      language: "yaml"
    });
    configEditor.onDidChangeModelContent(() => {
      if (formController.isApplyingFromForm()) {
        return;
      }
      updateDraftState();
      formController.scheduleSyncFromYaml();
    });

    sqlEditor = createMonacoEditor("sqlEditor", {
      value: "",
      language: "sql"
    });
    sqlEditor.onDidChangeModelContent(() => {
      if (!currentSqlPath) {
        return;
      }
      sqlContents[currentSqlPath] = sqlEditor.getValue();
      updateDraftState();
    });

    formController.initialize();
    await loadModules();
    await refreshCredentialsStatus();
    connectWebSocket();
  });

  async function loadModules() {
    try {
      const payload = await fetchJson("/api/modules/catalog", {}, "Не удалось загрузить список модулей.");
      renderModuleCatalogStatus(payload.appsRootStatus);
      const modules = Array.isArray(payload.modules) ? payload.modules : [];
      moduleList.innerHTML = "";

      if (modules.length === 0) {
        renderModuleListEmpty(payload.appsRootStatus?.message || "Модули не найдены.");
        return;
      }

      modules.forEach(module => {
        const button = document.createElement("button");
        button.type = "button";
        button.className = "list-group-item list-group-item-action";
        button.innerHTML = renderModuleListItem(module);
        button.dataset.moduleId = module.id;
        button.addEventListener("click", () => selectModule(module.id));
        moduleList.appendChild(button);
      });

      const preferredModuleId = modules.some(module => module.id === selectedModuleId)
        ? selectedModuleId
        : modules[0]?.id;
      if (preferredModuleId) {
        await selectModule(preferredModuleId);
      }
    } catch (error) {
      console.error(error);
      renderModuleCatalogStatus({
        mode: "ERROR",
        message: error.message || "Не удалось определить состояние каталога app-модулей."
      });
      renderModuleListEmpty(error.message || "Не удалось загрузить список модулей.");
    }
  }

  function renderModuleCatalogStatus(status) {
    if (!status) {
      moduleCatalogStatus.className = "module-catalog-status mt-3 mb-3 text-secondary small";
      moduleCatalogStatus.textContent = "Состояние каталога app-модулей пока недоступно.";
      return;
    }

    const isReady = status.mode === "READY";
    moduleCatalogStatus.className = `module-catalog-status mt-3 mb-3 small ${isReady ? "module-catalog-ready" : "module-catalog-warning"}`;
    moduleCatalogStatus.innerHTML = `
      <div>${escapeHtml(status.message || "Состояние каталога app-модулей неизвестно.")}</div>
      ${status.configuredPath ? `<span class="module-catalog-path">${escapeHtml(status.configuredPath)}</span>` : ""}
    `;
  }

  function renderModuleListEmpty(message) {
    moduleList.innerHTML = `
      <div class="list-group-item text-secondary">
        ${escapeHtml(message)}
      </div>
    `;
    selectedModuleId = null;
    currentModule = null;
    currentSqlPath = null;
    sqlContents = {};
    persistedSqlContents = {};
    persistedConfigText = "";
    selectedModuleLabel.textContent = "Модуль не выбран";
    selectedModuleDescription.classList.add("d-none");
    selectedModuleDescription.textContent = "";
    moduleValidationAlert.classList.add("d-none");
    moduleValidationAlert.textContent = "";
    sqlFileSelect.innerHTML = "";
    formController.initialize();
    renderCredentialsWarning();
    updateDraftState();
  }

  async function selectModule(moduleId) {
    selectedModuleId = moduleId;
    persistModuleUiState();
    [...moduleList.children].forEach(child => {
      child.classList.toggle("active", child.dataset.moduleId === moduleId);
    });
    await loadModule(moduleId);
  }

  async function loadModule(moduleId) {
    currentModule = await fetchJson(`/api/modules/${moduleId}`, {}, "Не удалось загрузить модуль.");
    formController.restoreExpansionStateForModule(currentModule.id);
    selectedModuleLabel.textContent = `${currentModule.title} · ${currentModule.configPath}`;
    if (currentModule.description) {
      selectedModuleDescription.textContent = currentModule.description;
      selectedModuleDescription.classList.remove("d-none");
    } else {
      selectedModuleDescription.classList.add("d-none");
      selectedModuleDescription.textContent = "";
    }
    persistedConfigText = currentModule.configText;
    configEditor.setValue(currentModule.configText);

    sqlContents = {};
    currentModule.sqlFiles.forEach(file => {
      sqlContents[file.path] = file.content;
    });
    persistedSqlContents = cloneSqlContents(sqlContents);
    renderSqlSelect();
    renderModuleValidation(currentModule);
    renderCredentialsWarning();
    updateDraftState();

    [...moduleList.children].forEach(button => {
      button.classList.toggle("active", button.dataset.moduleId === currentModule.id);
    });

    await formController.syncFromYaml();
  }

  function renderSqlSelect() {
    sqlFileSelect.innerHTML = "";
    currentSqlPath = null;
    (currentModule?.sqlFiles || []).forEach((file, index) => {
      const option = document.createElement("option");
      option.value = file.path;
      option.textContent = `${file.label} · ${file.path}`;
      sqlFileSelect.appendChild(option);
      if (index === 0) {
        currentSqlPath = file.path;
      }
    });
    if (sqlEditor) {
      sqlEditor.setValue(currentSqlPath ? (sqlContents[currentSqlPath] || "") : "");
    }
  }

  function renderModuleListItem(module) {
    const validationBadge = renderValidationBadge(module.validationStatus);
    const description = module.description
      ? `<div class="module-list-description">${escapeHtml(module.description)}</div>`
      : "";
    const tags = Array.isArray(module.tags) && module.tags.length > 0
      ? `<div class="module-list-tags">${module.tags.map(tag => `<span class="module-tag">${escapeHtml(tag)}</span>`).join("")}</div>`
      : "";
    const issues = Array.isArray(module.validationIssues) && module.validationIssues.length > 0
      ? `<div class="module-list-issues">${escapeHtml(module.validationIssues[0].message)}</div>`
      : "";
    return `
      <div class="module-list-head">
        <span class="module-list-title">${escapeHtml(module.title)}</span>
        ${validationBadge}
      </div>
      ${description}
      ${tags}
      ${issues}
    `;
  }

  function renderValidationBadge(status) {
    const normalized = String(status || "VALID").toUpperCase();
    const label = normalized === "VALID" ? "OK" : (normalized === "WARNING" ? "Warning" : "Invalid");
    return `<span class="module-validation-badge module-validation-badge-${normalized.toLowerCase()}">${label}</span>`;
  }

  function renderModuleValidation(module) {
    const issues = Array.isArray(module.validationIssues) ? module.validationIssues : [];
    const status = String(module.validationStatus || "VALID").toUpperCase();
    if (status === "VALID" && issues.length === 0) {
      moduleValidationAlert.classList.add("d-none");
      moduleValidationAlert.textContent = "";
      return;
    }

    const alertClass = status === "INVALID" ? "alert-danger" : "alert-warning";
    const title = status === "INVALID"
      ? "У модуля есть проблемы, которые стоит исправить."
      : "У модуля есть предупреждения.";
    moduleValidationAlert.className = `alert ${alertClass} mb-3`;
    moduleValidationAlert.innerHTML = `
      <div class="fw-semibold mb-2">${escapeHtml(title)}</div>
      <ul class="module-validation-list mb-0">
        ${issues.map(issue => `
          <li>
            <span class="module-validation-severity module-validation-severity-${String(issue.severity || "").toLowerCase()}">${escapeHtml(issue.severity || "INFO")}</span>
            <span>${escapeHtml(issue.message || "")}</span>
          </li>
        `).join("")}
      </ul>
    `;
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
    saveButton.disabled = !dirty || !selectedModuleId;

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

  function restorePersistedModuleUiState() {
    const parsed = loadJsonStorage(window.localStorage, MODULE_UI_STATE_STORAGE_KEY, null);
    if (!parsed || typeof parsed !== "object") {
      return;
    }
    selectedModuleId = typeof parsed.selectedModuleId === "string" && parsed.selectedModuleId
      ? parsed.selectedModuleId
      : null;
    formController.loadSerializedExpansionState(parsed.moduleExpansionState);
  }

  function persistModuleUiState() {
    saveJsonStorage(window.localStorage, MODULE_UI_STATE_STORAGE_KEY, {
      selectedModuleId,
      moduleExpansionState: formController.serializeExpansionState()
    });
  }

  function connectWebSocket() {
    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const ws = new WebSocket(`${protocol}://${window.location.host}/ws`);
    ws.onmessage = event => {
      const state = JSON.parse(event.data);
      currentCredentialsStatus = state.credentialsStatus;
      renderState(state);
    };
  }

  async function refreshCredentialsStatus() {
    currentCredentialsStatus = await fetchJson("/api/credentials", {}, "Не удалось получить статус credential.properties.");
    renderCredentialsStatus(currentCredentialsStatus);
    renderCredentialsWarning();
  }

  function renderState(state) {
    technicalDiagnosticsPanel.classList.toggle("d-none", state.uiSettings?.showTechnicalDiagnostics === false);
    summaryRawPanel.classList.toggle("d-none", state.uiSettings?.showRawSummaryJson === false);
    renderCredentialsStatus(state.credentialsStatus);
    renderCredentialsWarning();
    runPanelsController.renderState(state);
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
      credentialsWarning.textContent = "";
      return;
    }

    if (!currentModule.requiresCredentials) {
      credentialsWarning.classList.remove("d-none");
      credentialsWarning.className = "alert alert-light mt-3 mb-0";
      credentialsWarning.textContent = "У этого модуля нет обязательных placeholders ${...}. credential.properties нужен только для модулей и SQL-источников, где параметры вынесены во внешний файл.";
      return;
    }

    credentialsWarning.classList.remove("d-none");
    if (currentModule.credentialsReady) {
      credentialsWarning.className = "alert alert-success mt-3 mb-0";
      credentialsWarning.textContent = "Для модуля все обязательные placeholders ${...} сейчас разрешаются. При необходимости credential.properties можно заменить загрузкой через UI.";
      return;
    }

    credentialsWarning.className = "alert alert-warning mt-3 mb-0";
    const missingKeys = Array.isArray(currentModule.missingCredentialKeys) ? currentModule.missingCredentialKeys : [];
    const missingKeysText = missingKeys.length ? ` Не хватает значений: ${missingKeys.join(", ")}.` : "";
    if (currentCredentialsStatus && currentCredentialsStatus.fileAvailable) {
      credentialsWarning.textContent = `Для модуля найден credential.properties, но обязательные placeholders разрешены не полностью.${missingKeysText} Также проверяются переменные окружения и JVM system properties.`;
      return;
    }
    credentialsWarning.textContent = `В конфиге модуля найдены placeholders \${...}, но подходящие значения сейчас не найдены.${missingKeysText} Сначала ищется ui.defaultCredentialsFile, затем gradle/credential.properties в проекте, затем ~/.gradle/credential.properties. Если значений нет, загрузи файл через UI или задай их через env/JVM.`;
  }
})(window);
