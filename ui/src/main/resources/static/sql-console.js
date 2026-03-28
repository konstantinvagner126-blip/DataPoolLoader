(function initDataPoolSqlConsolePage(global) {
  const common = global.DataPoolCommon || {};
  const sqlConsoleNamespace = global.DataPoolSqlConsole || {};
  const { escapeHtml, fetchJson, postFormData, downloadExport, withMonacoReady, createMonacoEditor } = common;
  const { createQueryToolsController, createResultsController } = sqlConsoleNamespace;

  let sqlConsoleEditor = null;
  let sqlConsoleInfo = null;
  let sqlConsoleCredentialsStatus = null;
  let selectedSourceNames = [];
  let currentExecutionId = null;
  let currentExecutionStartedAt = null;
  let currentExecutionType = "SQL";
  let pollTimerId = null;
  let elapsedTimerId = null;
  let queryToolsController = null;
  let resultsController = null;

  const credentialsStatusEl = document.getElementById("sqlCredentialsStatus");
  const credentialsWarningEl = document.getElementById("sqlCredentialsWarning");
  const credentialsFileInputEl = document.getElementById("sqlCredentialsFileInput");
  const consoleInfoEl = document.getElementById("sqlConsoleInfo");
  const consoleLimitsInfoEl = document.getElementById("sqlConsoleLimitsInfo");
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
  const queryTemplatesEl = document.getElementById("sqlQueryTemplates");
  const commandGuardrailEl = document.getElementById("sqlCommandGuardrail");
  const uploadCredentialsButtonEl = document.getElementById("sqlUploadCredentialsButton");

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
    sqlConsoleEditor = createMonacoEditor("sqlEditor", {
      value: "select 1 as check_value",
      language: "sql"
    });

    queryToolsController = createQueryToolsController({
      editor: sqlConsoleEditor,
      recentQuerySelectEl,
      applyRecentQueryButtonEl,
      clearRecentQueriesButtonEl,
      queryTemplatesEl,
      commandGuardrailEl,
      runButtonEl
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

    queryToolsController.initialize();
    resultsController.initialize();

    await Promise.all([
      loadSqlConsoleInfo(),
      refreshCredentialsStatus()
    ]);
  });

  async function loadSqlConsoleInfo() {
    sqlConsoleInfo = await fetchJson("/api/sql-console/info", {}, "Не удалось загрузить конфигурацию SQL-консоли.");
    renderConsoleInfo(sqlConsoleInfo);
    renderCredentialsWarning();
  }

  async function refreshCredentialsStatus() {
    sqlConsoleCredentialsStatus = await fetchJson("/api/credentials", {}, "Не удалось получить статус credential.properties.");
    renderCredentialsStatus(sqlConsoleCredentialsStatus);
    renderCredentialsWarning();
  }

  function renderConsoleInfo(info) {
    if (!info || !info.configured) {
      consoleInfoEl.textContent = "В ui.application.yml пока не настроены source-подключения.";
      consoleLimitsInfoEl.textContent = "";
      sourceSelectionEl.innerHTML = "";
      return;
    }

    consoleInfoEl.innerHTML = `
      <div><strong>Sources настроено:</strong> ${info.sourceNames.length}</div>
      <div><strong>Лимит строк на SELECT с одного source:</strong> ${info.maxRowsPerShard}</div>
    `;
    consoleLimitsInfoEl.textContent = info.queryTimeoutSec
      ? `Таймаут запроса на один source: ${info.queryTimeoutSec} сек.`
      : "Таймаут запроса не задан.";

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

    sourceSelectionEl.innerHTML = info.sourceNames.map(name => `
      <label class="sql-source-checkbox">
        <input type="checkbox" value="${escapeHtml(name)}" ${selectedSourceNames.includes(name) ? "checked" : ""}>
        <span>${escapeHtml(name)}</span>
      </label>
    `).join("");

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
})(window);
