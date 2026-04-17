(function initDataPoolModulePage(global) {
  const common = global.DataPoolCommon || {};
  const uiBlocksNamespace = global.DataPoolUiBlocks || {};
  const uiCredentialsNamespace = global.DataPoolUiCredentials || {};
  const editorBlocksNamespace = global.DataPoolEditorBlocks || {};
  const moduleEditorNamespace = global.DataPoolModuleEditor || {};
  const moduleEditorSharedNamespace = global.DataPoolModuleEditorShared || {};
  const runPanelsNamespace = global.DataPoolRunPanels || {};
  const { escapeHtml, fetchJson, postJson, withMonacoReady, createMonacoEditor, loadJsonStorage, saveJsonStorage } = common;
  const { renderCredentialsPanel } = uiBlocksNamespace;
  const { createCredentialsController } = uiCredentialsNamespace;
  const { createConfigFormController } = moduleEditorNamespace;
  const { createSqlCatalogController, renderModuleMetadata } = moduleEditorSharedNamespace;
  const { renderExecutionLogPanel, renderHistoryCurrentPanel, renderTechnicalDiagnosticsPanel, renderSummaryPanel, renderInfoPanel } = editorBlocksNamespace;
  const { createRunPanelsController } = runPanelsNamespace;

  const MODULE_UI_STATE_STORAGE_KEY = "datapool.moduleEditor.uiState";

  renderCredentialsPanel?.(document.getElementById("credentialsPanelHost"));
  renderExecutionLogPanel?.(document.getElementById("executionLogPanelHost"), { eventLogId: "eventLog" });
  renderHistoryCurrentPanel?.(document.getElementById("historyCurrentPanelHost"), {
    filtersId: "runHistoryFilters",
    historyId: "runHistory",
    summaryId: "runSummary",
    historyTitle: "Последние запуски",
  });
  renderTechnicalDiagnosticsPanel?.(document.getElementById("technicalDiagnosticsPanelHost"), {
    panelId: "technicalDiagnosticsPanel",
    collapseId: "technicalEventsCollapse",
    logId: "technicalEventLog",
  });
  renderSummaryPanel?.(document.getElementById("summaryPanelHost"), {
    structuredId: "summaryStructured",
    rawPanelId: "summaryRawPanel",
    rawCollapseId: "summaryRawCollapse",
    rawJsonId: "summaryJson",
    emptyText: "Пока нет данных summary.",
    rawPanelInitiallyHidden: true,
  });
  renderInfoPanel?.(document.getElementById("resultsPanelHost"), {
    panelId: "runResultsPanel",
    title: "Результаты запуска",
    contentId: "runResultArtifacts",
    contentClass: "mt-3 text-secondary small",
    emptyText: "Пути к результатам запуска появятся после выполнения.",
  });

  let configEditor = null;
  let sqlEditor = null;
  let selectedModuleId = null;
  let currentModule = null;
  let currentSqlPath = null;
  let sqlContents = {};
  let persistedConfigText = "";
  let persistedSqlContents = {};

  const moduleList = document.getElementById("moduleList");
  const moduleCatalogStatus = document.getElementById("moduleCatalogStatus");
  const selectedModuleLabel = document.getElementById("selectedModuleLabel");
  const selectedModuleDescription = document.getElementById("selectedModuleDescription");
  const moduleDraftStatus = document.getElementById("moduleDraftStatus");
  const moduleValidationAlert = document.getElementById("moduleValidationAlert");
  const moduleMetadata = document.getElementById("moduleMetadata");
  const configForm = document.getElementById("configForm");
  const configFormWarning = document.getElementById("configFormWarning");
  const sqlCatalogList = document.getElementById("sqlCatalogList");
  const sqlCreateButton = document.getElementById("sqlCreateButton");
  const sqlRenameButton = document.getElementById("sqlRenameButton");
  const sqlDeleteButton = document.getElementById("sqlDeleteButton");
  const sqlResourceTitle = document.getElementById("sqlResourceTitle");
  const sqlResourceMeta = document.getElementById("sqlResourceMeta");
  const sqlResourceUsage = document.getElementById("sqlResourceUsage");
  const runSummary = document.getElementById("runSummary");
  const eventLog = document.getElementById("eventLog");
  const technicalEventLog = document.getElementById("technicalEventLog");
  const runHistoryFilters = document.getElementById("runHistoryFilters");
  const runHistory = document.getElementById("runHistory");
  const summaryStructured = document.getElementById("summaryStructured");
  const summaryJson = document.getElementById("summaryJson");
  const summaryRawPanel = document.getElementById("summaryRawPanel");
  const runResultArtifacts = document.getElementById("runResultArtifacts");
  const technicalDiagnosticsPanel = document.getElementById("technicalDiagnosticsPanel");
  const credentialsStatus = document.getElementById("credentialsStatus");
  const credentialsWarning = document.getElementById("credentialsWarning");
  const credentialsFileInput = document.getElementById("credentialsFileInput");
  const reloadButton = document.getElementById("reloadButton");
  const saveButton = document.getElementById("saveButton");
  const historyButton = document.getElementById("historyButton");
  const runButton = document.getElementById("runButton");
  const uploadCredentialsButton = document.getElementById("uploadCredentialsButton");

  const credentialsController = createCredentialsController({
    refs: {
      credentialsStatus,
      credentialsWarning,
      credentialsFileInput,
    },
    getRequirementTarget: () => currentModule?.module || null,
  });

  const formController = createConfigFormController({
    configForm,
    configFormWarning,
    getConfigText: () => configEditor?.getValue() || "",
    setConfigText: value => {
      if (configEditor) {
        configEditor.setValue(value);
      }
    },
    getCurrentModuleId: () => currentModule?.module?.id || selectedModuleId,
    getSqlResources: () => currentModule?.module?.sqlFiles || [],
    getStorageMode: () => currentModule?.storageMode || "FILES",
    onDraftStateChange: () => {
      updateDraftState();
      sqlCatalogController.render();
    },
    onPersistUiState: () => persistModuleUiState(),
    openYamlEditor: () => {
      document.querySelector('[data-bs-target="#configTab"]')?.click();
    }
  });

  const sqlCatalogController = createSqlCatalogController({
    refs: {
      sqlCatalogList,
      sqlCreateButton,
      sqlRenameButton,
      sqlDeleteButton,
      sqlResourceTitle,
      sqlResourceMeta,
      sqlResourceUsage,
    },
    editors: {
      get sqlEditor() {
        return sqlEditor;
      }
    },
    getSession: () => currentModule,
    getSqlContents: () => sqlContents,
    setSqlContents: next => {
      sqlContents = next;
    },
    getCurrentPath: () => currentSqlPath,
    setCurrentPath: value => {
      currentSqlPath = value;
    },
    getFormState: () => formController.currentFormState?.(),
    formController,
    onDraftStateChange: () => updateDraftState(),
    onCatalogChanged: () => {
      renderModuleMetadata?.(moduleMetadata, currentModule);
    },
  });

  const runPanelsController = createRunPanelsController({
    runSummaryEl: runSummary,
    eventLogEl: eventLog,
    technicalEventLogEl: technicalEventLog,
    runHistoryFiltersEl: runHistoryFilters,
    runHistoryEl: runHistory,
    summaryStructuredEl: summaryStructured,
    summaryJsonEl: summaryJson,
    resultArtifactsEl: runResultArtifacts,
    resultPanelId: "runResultsPanel",
    rawSummaryPanelId: "summaryRawPanel",
    rawSummaryCollapseId: "summaryRawCollapse",
    compactMode: true,
    historyLimit: 5,
    humanTimelineLimit: 12,
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

  historyButton.addEventListener("click", () => {
    if (!selectedModuleId) {
      return;
    }
    window.location.assign(`/module-runs?storage=files&module=${encodeURIComponent(selectedModuleId)}`);
  });

  uploadCredentialsButton.addEventListener("click", async () => {
    await credentialsController.uploadSelectedFile();
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
    await credentialsController.refreshStatus();
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
        message: error.message || "Не удалось определить состояние каталога файловых модулей."
      });
      renderModuleListEmpty(error.message || "Не удалось загрузить список модулей.");
    }
  }

  function renderModuleCatalogStatus(status) {
    if (!status) {
      moduleCatalogStatus.className = "module-catalog-status mt-3 mb-3 text-secondary small";
      moduleCatalogStatus.textContent = "Состояние каталога файловых модулей пока недоступно.";
      return;
    }

    const isReady = status.mode === "READY";
    moduleCatalogStatus.className = `module-catalog-status mt-3 mb-3 small ${isReady ? "module-catalog-ready" : "module-catalog-warning"}`;
    moduleCatalogStatus.innerHTML = `
      <div>${escapeHtml(status.message || "Состояние каталога файловых модулей неизвестно.")}</div>
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
    if (sqlCatalogList) {
      sqlCatalogList.innerHTML = "";
    }
    if (moduleMetadata) {
      renderModuleMetadata?.(moduleMetadata, null);
    }
    formController.initialize();
    sqlCatalogController.render();
    credentialsController.renderWarning();
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
    formController.restoreExpansionStateForModule(currentModule.module.id);
    selectedModuleLabel.textContent = `${currentModule.module.title} · ${currentModule.module.configPath}`;
    if (currentModule.module.description) {
      selectedModuleDescription.textContent = currentModule.module.description;
      selectedModuleDescription.classList.remove("d-none");
    } else {
      selectedModuleDescription.classList.add("d-none");
      selectedModuleDescription.textContent = "";
    }
    persistedConfigText = currentModule.module.configText;
    configEditor.setValue(currentModule.module.configText);

    sqlContents = {};
    currentModule.module.sqlFiles.forEach(file => {
      sqlContents[file.path] = file.content;
    });
    persistedSqlContents = cloneSqlContents(sqlContents);
    sqlCatalogController.render();
    renderModuleValidation(currentModule.module);
    renderModuleMetadata?.(moduleMetadata, currentModule);
    credentialsController.renderWarning();
    updateDraftState();

    [...moduleList.children].forEach(button => {
      button.classList.toggle("active", button.dataset.moduleId === currentModule.module.id);
    });

    await formController.syncFromYaml();
    sqlCatalogController.render();
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
    const label = normalized === "VALID" ? "Исправен" : (normalized === "WARNING" ? "Предупреждение" : "Ошибка");
    return `<span class="module-validation-badge module-validation-badge-${normalized.toLowerCase()}">${label}</span>`;
  }

  function translateValidationSeverity(severity) {
    switch (String(severity || "INFO").toUpperCase()) {
      case "ERROR":
        return "Ошибка";
      case "WARNING":
        return "Предупреждение";
      default:
        return "Информация";
    }
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
            <span class="module-validation-severity module-validation-severity-${String(issue.severity || "").toLowerCase()}">${escapeHtml(translateValidationSeverity(issue.severity))}</span>
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
    const capabilities = currentModule?.capabilities || {};
    saveButton.disabled = !dirty || !selectedModuleId || capabilities.save !== true;
    runButton.disabled = !selectedModuleId || capabilities.run !== true;
    historyButton.disabled = !selectedModuleId;

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
    const params = new URLSearchParams(window.location.search);
    const requestedModuleId = params.get("module");
    const parsed = loadJsonStorage(window.localStorage, MODULE_UI_STATE_STORAGE_KEY, null);
    if (parsed && typeof parsed === "object") {
      selectedModuleId = typeof parsed.selectedModuleId === "string" && parsed.selectedModuleId
        ? parsed.selectedModuleId
        : null;
      formController.loadSerializedExpansionState(parsed.moduleExpansionState);
    }
    if (requestedModuleId) {
      selectedModuleId = requestedModuleId;
    }
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
      renderState(state);
    };
  }

  function renderState(state) {
    technicalDiagnosticsPanel.classList.toggle("d-none", state.uiSettings?.showTechnicalDiagnostics === false);
    summaryRawPanel.classList.toggle("d-none", state.uiSettings?.showRawSummaryJson === false);
    credentialsController.setStatus(state.credentialsStatus);
    runPanelsController.renderState(state);
  }
})(window);
