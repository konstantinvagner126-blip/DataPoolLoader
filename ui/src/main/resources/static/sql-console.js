(function initDataPoolSqlConsolePage(global) {
  const common = global.DataPoolCommon || {};
  const sqlConsoleNamespace = global.DataPoolSqlConsole || {};
  const { escapeHtml, fetchJson, postFormData, downloadExport, withMonacoReady, createMonacoEditor } = common;
  const { createQueryToolsController, createResultsController } = sqlConsoleNamespace;

  let sqlConsoleEditor = null;
  let sqlConsoleInfo = null;
  let sqlConsoleCredentialsStatus = null;
  let sourceConnectionStatuses = {};
  let sqlConsoleState = {
    draftSql: "select 1 as check_value",
    recentQueries: [],
    selectedSourceNames: [],
    pageSize: 50
  };
  let selectedSourceNames = [];
  let currentExecutionId = null;
  let currentExecutionStartedAt = null;
  let currentExecutionType = "SQL";
  let pollTimerId = null;
  let elapsedTimerId = null;
  let persistStateTimerId = null;
  let queryToolsController = null;
  let resultsController = null;

  const credentialsStatusEl = document.getElementById("sqlCredentialsStatus");
  const credentialsWarningEl = document.getElementById("sqlCredentialsWarning");
  const credentialsFileInputEl = document.getElementById("sqlCredentialsFileInput");
  const consoleInfoEl = document.getElementById("sqlConsoleInfo");
  const maxRowsPerShardInputEl = document.getElementById("sqlMaxRowsPerShardInput");
  const queryTimeoutInputEl = document.getElementById("sqlQueryTimeoutInput");
  const saveConsoleSettingsButtonEl = document.getElementById("sqlSaveConsoleSettingsButton");
  const saveConsoleSettingsStatusEl = document.getElementById("sqlSaveConsoleSettingsStatus");
  const connectionCheckStatusEl = document.getElementById("sqlConnectionCheckStatus");
  const checkConnectionsButtonEl = document.getElementById("sqlCheckConnectionsButton");
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
  const runButtonEl = document.getElementById("sqlRunButton");
  const cancelButtonEl = document.getElementById("sqlCancelButton");
  const exportActiveCsvButtonEl = document.getElementById("sqlExportActiveCsvButton");
  const exportAllZipButtonEl = document.getElementById("sqlExportAllZipButton");
  const recentQuerySelectEl = document.getElementById("sqlRecentQuerySelect");
  const applyRecentQueryButtonEl = document.getElementById("sqlApplyRecentQueryButton");
  const clearRecentQueriesButtonEl = document.getElementById("sqlClearRecentQueriesButton");
  const commandGuardrailEl = document.getElementById("sqlCommandGuardrail");
  const uploadCredentialsButtonEl = document.getElementById("sqlUploadCredentialsButton");

  global.addEventListener("pagehide", () => {
    if (persistStateTimerId) {
      global.clearTimeout(persistStateTimerId);
      persistStateTimerId = null;
    }
    persistSqlConsoleState();
  });

  uploadCredentialsButtonEl.addEventListener("click", async () => {
    const file = credentialsFileInputEl.files[0];
    if (!file) {
      return;
    }
    const formData = new FormData();
    formData.append("file", file);
    sqlConsoleCredentialsStatus = await postFormData("/api/credentials/upload", formData, "Не удалось загрузить credential.properties.");
    renderCredentialsStatus(sqlConsoleCredentialsStatus);
    renderCredentialsWarning();
    await checkConnections();
  });

  checkConnectionsButtonEl.addEventListener("click", async () => {
    await checkConnections();
  });

  maxRowsPerShardInputEl.addEventListener("input", () => {
    updateConsoleSettingsDirtyState();
  });

  queryTimeoutInputEl.addEventListener("input", () => {
    updateConsoleSettingsDirtyState();
  });

  saveConsoleSettingsButtonEl.addEventListener("click", async () => {
    if (!sqlConsoleInfo || !sqlConsoleInfo.configured) {
      return;
    }
    const maxRowsPerShard = Number(maxRowsPerShardInputEl.value);
    if (!Number.isInteger(maxRowsPerShard) || maxRowsPerShard <= 0) {
      setConsoleSettingsSaveStatus("Укажи корректный лимит строк: целое число больше 0.", "failed");
      return;
    }
    const queryTimeoutSec = parseQueryTimeoutInput();
    if (Number.isNaN(queryTimeoutSec)) {
      setConsoleSettingsSaveStatus("Укажи корректный таймаут: пусто или целое число больше 0.", "failed");
      return;
    }

    saveConsoleSettingsButtonEl.disabled = true;
    setConsoleSettingsSaveStatus("Сохраняем настройки SQL-консоли...", "neutral");
    try {
      sqlConsoleInfo = await fetchJson(
        "/api/sql-console/settings",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ maxRowsPerShard, queryTimeoutSec })
        },
        "Не удалось сохранить настройки SQL-консоли."
      );
      renderConsoleInfo(sqlConsoleInfo);
      setConsoleSettingsSaveStatus("Настройки SQL-консоли сохранены.", "success");
    } catch (error) {
      setConsoleSettingsSaveStatus(error.message || "Не удалось сохранить настройки SQL-консоли.", "failed");
      updateConsoleSettingsDirtyState();
    }
  });

  runButtonEl.addEventListener("click", async () => {
    const sql = sqlConsoleEditor.getValue();
    const statementKeyword = queryToolsController.detectStatementKeyword(sql);
    if (!queryToolsController.isReadOnlyLikeStatement(statementKeyword)) {
      const confirmMessage = queryToolsController.isDangerousStatement(statementKeyword)
        ? `Команда ${statementKeyword} считается потенциально опасной и будет выполнена на всех выбранных sources. Проверь SQL еще раз. Продолжить?`
        : `Запрос типа ${statementKeyword} может изменить данные или структуру на всех настроенных sources. Продолжить?`;
      if (!window.confirm(confirmMessage)) {
        return;
      }
    }

    resultsController.resetResultArea();
    currentExecutionType = statementKeyword;
    queryToolsController.rememberRecentQuery(sql);
    resultsController.setRunningState(true);

    try {
      const started = await fetchJson("/api/sql-console/query/start", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sql, selectedSourceNames })
      }, "Не удалось запустить запрос.");
      currentExecutionId = started.id;
      currentExecutionStartedAt = new Date(started.startedAt);
      resultsController.renderRunningStatus({
        executionType: currentExecutionType,
        selectedSourceNames,
        startedAt: currentExecutionStartedAt,
        cancelRequested: false
      });
      startExecutionPolling();
    } catch (error) {
      resultsController.setRunningState(false);
      resultsController.renderExecutionError(error.message || "Не удалось запустить запрос.");
    }
  });

  cancelButtonEl.addEventListener("click", async () => {
    if (!currentExecutionId) {
      return;
    }
    cancelButtonEl.disabled = true;
    try {
      await fetchJson(`/api/sql-console/query/${currentExecutionId}/cancel`, {
        method: "POST"
      }, "Не удалось отменить запрос.");
      resultsController.renderRunningStatus({
        executionType: currentExecutionType,
        selectedSourceNames,
        startedAt: currentExecutionStartedAt,
        cancelRequested: true
      });
    } catch (error) {
      cancelButtonEl.disabled = false;
      executionStatusEl.textContent = error.message || "Не удалось отменить запрос.";
      executionStatusEl.className = "sql-status-strip sql-status-strip-failed";
    }
  });

  exportActiveCsvButtonEl.addEventListener("click", async () => {
    const result = resultsController.getCurrentResult();
    const shardName = resultsController.getCurrentShard();
    if (!result || result.statementType !== "RESULT_SET" || !shardName) {
      return;
    }
    try {
      await downloadExport("/api/sql-console/export/source-csv", { result, shardName }, `${shardName}.csv`);
    } catch (error) {
      global.alert(error.message || "Не удалось скачать файл.");
    }
  });

  exportAllZipButtonEl.addEventListener("click", async () => {
    const result = resultsController.getCurrentResult();
    if (!result || result.statementType !== "RESULT_SET") {
      return;
    }
    try {
      await downloadExport("/api/sql-console/export/all-zip", { result }, "sql-console-results.zip");
    } catch (error) {
      global.alert(error.message || "Не удалось скачать файл.");
    }
  });

  withMonacoReady(async () => {
    await loadSqlConsoleState();
    sqlConsoleEditor = createMonacoEditor("sqlEditor", {
      value: sqlConsoleState.draftSql || "select 1 as check_value",
      language: "sql"
    });

    pageSizeSelectEl.value = String(normalizePageSize(sqlConsoleState.pageSize));

    queryToolsController = createQueryToolsController({
      editor: sqlConsoleEditor,
      recentQuerySelectEl,
      applyRecentQueryButtonEl,
      clearRecentQueriesButtonEl,
      commandGuardrailEl,
      runButtonEl,
      initialDraft: sqlConsoleState.draftSql,
      initialRecentQueries: sqlConsoleState.recentQueries,
      onStateChanged: partialState => {
        sqlConsoleState = {
          ...sqlConsoleState,
          ...partialState
        };
        schedulePersistSqlConsoleState();
      }
    });

    resultsController = createResultsController({
      executionStatusEl,
      resultMetaEl,
      resultSummaryEl,
      resultTableEl,
      pageSizeSelectEl,
      dataTabButtonEl,
      statusTabButtonEl,
      dataPaneEl,
      statusPaneEl,
      runButtonEl,
      cancelButtonEl,
      exportActiveCsvButtonEl,
      exportAllZipButtonEl
    });

    sqlConsoleEditor.setValue(queryToolsController.getInitialDraft());
    sqlConsoleEditor.onDidChangeModelContent(() => {
      queryToolsController.handleEditorChange(sqlConsoleEditor.getValue());
    });

    pageSizeSelectEl.addEventListener("change", () => {
      sqlConsoleState = {
        ...sqlConsoleState,
        pageSize: normalizePageSize(pageSizeSelectEl.value)
      };
      schedulePersistSqlConsoleState();
    });

    queryToolsController.initialize();
    resultsController.initialize();

    await Promise.all([
      loadSqlConsoleInfo(),
      refreshCredentialsStatus()
    ]);
    await checkConnections();
  });

  async function loadSqlConsoleInfo() {
    sqlConsoleInfo = await fetchJson("/api/sql-console/info", {}, "Не удалось загрузить конфигурацию SQL-консоли.");
    renderConsoleInfo(sqlConsoleInfo);
    renderCredentialsWarning();
  }

  async function loadSqlConsoleState() {
    sqlConsoleState = await fetchJson("/api/sql-console/state", {}, "Не удалось загрузить состояние SQL-консоли.");
    sqlConsoleState = {
      draftSql: sqlConsoleState.draftSql || "select 1 as check_value",
      recentQueries: Array.isArray(sqlConsoleState.recentQueries) ? sqlConsoleState.recentQueries : [],
      selectedSourceNames: Array.isArray(sqlConsoleState.selectedSourceNames) ? sqlConsoleState.selectedSourceNames : [],
      pageSize: normalizePageSize(sqlConsoleState.pageSize)
    };
    selectedSourceNames = [...sqlConsoleState.selectedSourceNames];
  }

  async function refreshCredentialsStatus() {
    sqlConsoleCredentialsStatus = await fetchJson("/api/credentials", {}, "Не удалось получить статус credential.properties.");
    renderCredentialsStatus(sqlConsoleCredentialsStatus);
    renderCredentialsWarning();
  }

  function renderConsoleInfo(info) {
    if (!info || !info.configured) {
      consoleInfoEl.textContent = "В ui.application.yml пока не настроены source-подключения.";
      setConnectionCheckStatus("Проверка подключений недоступна, пока не настроены sources.");
      sourceSelectionEl.innerHTML = "";
      sourceConnectionStatuses = {};
      checkConnectionsButtonEl.disabled = true;
      maxRowsPerShardInputEl.disabled = true;
      queryTimeoutInputEl.disabled = true;
      saveConsoleSettingsButtonEl.disabled = true;
      maxRowsPerShardInputEl.value = "200";
      queryTimeoutInputEl.value = "";
      setConsoleSettingsSaveStatus("Сохранение недоступно, пока не настроены sources.");
      return;
    }

    checkConnectionsButtonEl.disabled = false;
    maxRowsPerShardInputEl.disabled = false;
    queryTimeoutInputEl.disabled = false;
    consoleInfoEl.innerHTML = `
      <div><strong>Sources настроено:</strong> ${info.sourceNames.length}</div>
      <div><strong>Лимит строк на SELECT с одного source:</strong> ${info.maxRowsPerShard}</div>
    `;
    maxRowsPerShardInputEl.value = String(info.maxRowsPerShard);
    queryTimeoutInputEl.value = info.queryTimeoutSec ? String(info.queryTimeoutSec) : "";
    updateConsoleSettingsDirtyState();

    sourceConnectionStatuses = Object.fromEntries(
      Object.entries(sourceConnectionStatuses).filter(([name]) => info.sourceNames.includes(name))
    );

    if (selectedSourceNames.length === 0) {
      selectedSourceNames = sqlConsoleState.selectedSourceNames.length > 0
        ? sqlConsoleState.selectedSourceNames.filter(name => info.sourceNames.includes(name))
        : [...info.sourceNames];
      if (selectedSourceNames.length === 0) {
        selectedSourceNames = [...info.sourceNames];
      }
    } else {
      selectedSourceNames = selectedSourceNames.filter(name => info.sourceNames.includes(name));
      if (selectedSourceNames.length === 0) {
        selectedSourceNames = [...info.sourceNames];
      }
    }
    sqlConsoleState = {
      ...sqlConsoleState,
      selectedSourceNames: [...selectedSourceNames]
    };
    schedulePersistSqlConsoleState();

    renderSourceSelection(info);
  }

  function renderSourceSelection(info) {
    sourceSelectionEl.innerHTML = info.sourceNames.map(name => {
      const connectionState = sourceConnectionStatuses[name] || null;
      const stateClass = connectionState
        ? `sql-source-checkbox-${String(connectionState.status || "UNKNOWN").toLowerCase()}`
        : "sql-source-checkbox-unknown";
      const stateLabel = connectionState
        ? (connectionState.status === "SUCCESS" ? "Подключение доступно" : "Ошибка подключения")
        : "Статус не проверен";
      const stateMessage = connectionState
        ? (connectionState.errorMessage || connectionState.message || stateLabel)
        : "Нажми «Проверить подключение», чтобы увидеть статус.";
      return `
        <label class="sql-source-checkbox ${stateClass}">
          <input type="checkbox" value="${escapeHtml(name)}" ${selectedSourceNames.includes(name) ? "checked" : ""}>
          <span class="sql-source-checkbox-body">
            <span class="sql-source-checkbox-head">
              <span class="sql-source-checkbox-name">${escapeHtml(name)}</span>
              <span class="sql-source-checkbox-status">${escapeHtml(stateLabel)}</span>
            </span>
            <span class="sql-source-checkbox-message">${escapeHtml(stateMessage)}</span>
          </span>
        </label>
      `;
    }).join("");

    sourceSelectionEl.querySelectorAll("input[type=\"checkbox\"]").forEach(input => {
      input.addEventListener("change", () => {
        selectedSourceNames = [...sourceSelectionEl.querySelectorAll("input[type=\"checkbox\"]:checked")]
          .map(checkbox => checkbox.value);
        sqlConsoleState = {
          ...sqlConsoleState,
          selectedSourceNames: [...selectedSourceNames]
        };
        schedulePersistSqlConsoleState();
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
    const hasSources = !!(sqlConsoleInfo && sqlConsoleInfo.configured);
    if (!hasSources) {
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

  async function checkConnections() {
    if (!sqlConsoleInfo || !sqlConsoleInfo.configured) {
      return;
    }
    checkConnectionsButtonEl.disabled = true;
    setConnectionCheckStatus("Проверяем подключение ко всем sources...");
    try {
      const response = await fetchJson(
        "/api/sql-console/connections/check",
        {
          method: "POST"
        },
        "Не удалось проверить подключение к sources."
      );
      sourceConnectionStatuses = Object.fromEntries(
        (response.sourceResults || []).map(item => [item.sourceName, item])
      );
      const successCount = (response.sourceResults || []).filter(item => item.status === "SUCCESS").length;
      const failedCount = (response.sourceResults || []).filter(item => item.status !== "SUCCESS").length;
      setConnectionCheckStatus(
        failedCount > 0
          ? `Проверка завершена: успешных подключений ${successCount}, с ошибкой ${failedCount}.`
          : `Проверка завершена: все ${successCount} sources доступны.`,
        failedCount > 0 ? "failed" : "success"
      );
      renderSourceSelection(sqlConsoleInfo);
    } catch (error) {
      setConnectionCheckStatus(error.message || "Не удалось проверить подключение к sources.", "failed");
      renderSourceSelection(sqlConsoleInfo);
    } finally {
      checkConnectionsButtonEl.disabled = false;
    }
  }

  function setConnectionCheckStatus(message, tone = "neutral") {
    connectionCheckStatusEl.textContent = message;
    connectionCheckStatusEl.className = `small mt-2 ${
      tone === "success"
        ? "text-success"
        : tone === "failed"
          ? "text-danger"
          : "text-secondary"
    }`;
  }

  function updateConsoleSettingsDirtyState() {
    if (!sqlConsoleInfo || !sqlConsoleInfo.configured) {
      saveConsoleSettingsButtonEl.disabled = true;
      return;
    }
    const inputValue = Number(maxRowsPerShardInputEl.value);
    const timeoutValue = parseQueryTimeoutInput();
    const timeoutInvalid = Number.isNaN(timeoutValue);
    const timeoutChanged = !timeoutInvalid && timeoutValue !== sqlConsoleInfo.queryTimeoutSec;
    const maxRowsChanged = Number.isInteger(inputValue) && inputValue > 0 && inputValue !== sqlConsoleInfo.maxRowsPerShard;
    const changed = maxRowsChanged || timeoutChanged;
    saveConsoleSettingsButtonEl.disabled = !changed;
    if (timeoutInvalid) {
      setConsoleSettingsSaveStatus("Укажи корректный таймаут: пусто или целое число больше 0.", "failed");
      return;
    }
    if (changed) {
      setConsoleSettingsSaveStatus("Есть несохраненные изменения настроек SQL-консоли.", "neutral");
    } else if (!saveConsoleSettingsStatusEl.dataset.lockedTone) {
      setConsoleSettingsSaveStatus("Изменений нет.");
    }
  }

  function setConsoleSettingsSaveStatus(message, tone = "neutral") {
    saveConsoleSettingsStatusEl.textContent = message;
    saveConsoleSettingsStatusEl.dataset.lockedTone = tone === "success" || tone === "failed" ? "true" : "";
    saveConsoleSettingsStatusEl.className = `small mt-2 ${
      tone === "success"
        ? "text-success"
        : tone === "failed"
          ? "text-danger"
          : "text-secondary"
    }`;
    if (tone === "success" || tone === "failed") {
      global.setTimeout(() => {
        saveConsoleSettingsStatusEl.dataset.lockedTone = "";
        updateConsoleSettingsDirtyState();
      }, 2500);
    }
  }

  function parseQueryTimeoutInput() {
    const raw = String(queryTimeoutInputEl.value || "").trim();
    if (!raw) {
      return null;
    }
    const value = Number(raw);
    if (!Number.isInteger(value) || value <= 0) {
      return Number.NaN;
    }
    return value;
  }

  function startExecutionPolling() {
    stopExecutionPolling();
    pollTimerId = global.setInterval(async () => {
      try {
        await pollExecutionStatus();
      } catch (error) {
        stopExecutionPolling();
        resultsController.setRunningState(false);
        resultsController.renderExecutionError(error.message || "Не удалось получить статус SQL-запроса.");
        currentExecutionId = null;
        currentExecutionStartedAt = null;
      }
    }, 1000);

    elapsedTimerId = global.setInterval(() => {
      resultsController.renderRunningStatus({
        executionType: currentExecutionType,
        selectedSourceNames,
        startedAt: currentExecutionStartedAt,
        cancelRequested: false
      });
    }, 1000);
  }

  function stopExecutionPolling() {
    if (pollTimerId) {
      global.clearInterval(pollTimerId);
      pollTimerId = null;
    }
    if (elapsedTimerId) {
      global.clearInterval(elapsedTimerId);
      elapsedTimerId = null;
    }
  }

  async function pollExecutionStatus() {
    if (!currentExecutionId) {
      return;
    }
    const snapshot = await fetchJson(`/api/sql-console/query/${currentExecutionId}`, {}, "Не удалось получить статус SQL-запроса.");
    if (snapshot.status === "RUNNING") {
      resultsController.renderRunningStatus({
        executionType: currentExecutionType,
        selectedSourceNames,
        startedAt: currentExecutionStartedAt,
        cancelRequested: snapshot.cancelRequested
      });
      return;
    }

    stopExecutionPolling();
    resultsController.setRunningState(false);
    if (snapshot.status === "SUCCESS" && snapshot.result) {
      resultsController.renderResult(snapshot.result, selectedSourceNames);
    } else if (snapshot.status === "CANCELLED") {
      resultsController.renderExecutionCancelled(snapshot.errorMessage || "Запрос отменен пользователем.");
    } else {
      resultsController.renderExecutionError(snapshot.errorMessage || "Не удалось выполнить запрос.");
    }
    currentExecutionId = null;
    currentExecutionStartedAt = null;
  }

  function schedulePersistSqlConsoleState() {
    if (persistStateTimerId) {
      global.clearTimeout(persistStateTimerId);
    }
    persistStateTimerId = global.setTimeout(() => {
      persistStateTimerId = null;
      persistSqlConsoleState();
    }, 300);
  }

  async function persistSqlConsoleState() {
    try {
      sqlConsoleState = await fetchJson(
        "/api/sql-console/state",
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            draftSql: sqlConsoleState.draftSql || queryToolsController?.getInitialDraft?.() || "select 1 as check_value",
            recentQueries: sqlConsoleState.recentQueries || [],
            selectedSourceNames: selectedSourceNames,
            pageSize: normalizePageSize(pageSizeSelectEl.value)
          })
        },
        "Не удалось сохранить состояние SQL-консоли."
      );
    } catch (_) {
      // Состояние SQL-консоли не должно ломать основной workflow страницы.
    }
  }

  function normalizePageSize(value) {
    const parsed = Number(value);
    return [25, 50, 100].includes(parsed) ? parsed : 50;
  }
})(window);
