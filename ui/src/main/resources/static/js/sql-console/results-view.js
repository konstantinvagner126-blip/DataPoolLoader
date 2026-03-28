(function initDataPoolSqlConsoleResults(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolSqlConsole || (global.DataPoolSqlConsole = {});
  const { escapeHtml } = common;

  const LONG_RUNNING_THRESHOLD_MS = 5000;

  function createResultsController({
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
  }) {
    let currentResult = null;
    let currentSelectShard = null;
    let currentSelectedSourceNames = [];
    const currentPages = {};

    function initialize() {
      [dataTabButtonEl, statusTabButtonEl].forEach(button => {
        button.addEventListener("click", () => {
          setActiveOutputTab(button.dataset.outputTab);
        });
      });

      pageSizeSelectEl.addEventListener("change", () => {
        if (currentResult && currentResult.statementType === "RESULT_SET") {
          const shardResults = successfulSelectShards(currentResult);
          if (currentSelectShard && shardResults.some(item => item.shardName === currentSelectShard)) {
            currentPages[currentSelectShard] = 1;
          }
          renderResult(currentResult, currentSelectedSourceNames);
        }
      });
    }

    function resetResultArea() {
      currentResult = null;
      currentSelectShard = null;
      resultMetaEl.textContent = "Выполняется запрос...";
      resultSummaryEl.innerHTML = `<div class="alert alert-secondary mb-0">Ожидается завершение запроса.</div>`;
      resultTableEl.innerHTML = `<div class="alert alert-secondary mb-0">Ожидается завершение запроса.</div>`;
      updateExportButtons();
    }

    function setRunningState(isRunning) {
      runButtonEl.disabled = isRunning;
      cancelButtonEl.disabled = !isRunning;
      if (isRunning) {
        exportActiveCsvButtonEl.disabled = true;
        exportAllZipButtonEl.disabled = true;
      }
    }

    function renderRunningStatus({ executionType, selectedSourceNames, startedAt, cancelRequested = false }) {
      const elapsedMs = startedAt ? (Date.now() - startedAt.getTime()) : 0;
      const seconds = Math.max(1, Math.floor(elapsedMs / 1000));
      const selectedSourcesLabel = selectedSourceNames.length > 0
        ? selectedSourceNames.join(", ")
        : "все sources";
      const isLongRunning = elapsedMs >= LONG_RUNNING_THRESHOLD_MS;
      executionStatusEl.textContent = cancelRequested
        ? `Отмена запроса типа ${executionType} отправлена. Sources: ${selectedSourcesLabel}. Ожидание завершения...`
        : isLongRunning
          ? `Запрос типа ${executionType} выполняется дольше обычного: ${seconds} сек. Sources: ${selectedSourcesLabel}.`
          : `Запрос типа ${executionType} выполняется: ${seconds} сек. Sources: ${selectedSourcesLabel}.`;
      executionStatusEl.className = cancelRequested
        ? "sql-status-strip sql-status-strip-warning"
        : isLongRunning
          ? "sql-status-strip sql-status-strip-warning"
          : "sql-status-strip sql-status-strip-running";
    }

    function renderResult(result, selectedSourceNames) {
      currentResult = result;
      currentSelectedSourceNames = [...selectedSourceNames];
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
          : "Результат команды показан по каждому source отдельно.";

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

    function renderExecutionError(message) {
      currentResult = null;
      currentSelectShard = null;
      executionStatusEl.textContent = message;
      executionStatusEl.className = "sql-status-strip sql-status-strip-failed";
      resultMetaEl.textContent = "Результат не получен.";
      resultSummaryEl.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(message)}</div>`;
      resultTableEl.innerHTML = `<div class="alert alert-danger mb-0">${escapeHtml(message)}</div>`;
      setActiveOutputTab("status");
      updateExportButtons();
    }

    function renderExecutionCancelled(message) {
      currentResult = null;
      currentSelectShard = null;
      executionStatusEl.textContent = message;
      executionStatusEl.className = "sql-status-strip sql-status-strip-warning";
      resultMetaEl.textContent = "Выполнение было остановлено.";
      resultSummaryEl.innerHTML = `<div class="alert alert-warning mb-0">${escapeHtml(message)}</div>`;
      resultTableEl.innerHTML = `<div class="alert alert-warning mb-0">${escapeHtml(message)}</div>`;
      setActiveOutputTab("status");
      updateExportButtons();
    }

    function getCurrentResult() {
      return currentResult;
    }

    function getCurrentShard() {
      return currentSelectShard;
    }

    function renderSelectResults(result) {
      const shards = successfulSelectShards(result);
      if (shards.length === 0) {
        resultTableEl.innerHTML = `<div class="alert alert-secondary mb-0">Ни один source не вернул данные для отображения.</div>`;
        updateExportButtons();
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

      const paginationHtml = renderPagination(currentPage, totalPages);
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

      updateExportButtons();
    }

    function renderCommandResults(result) {
      resultTableEl.innerHTML = `<div class="alert alert-secondary mb-0">Команда ${result.statementKeyword} не возвращает табличные данные. Смотри вкладку "Статусы".</div>`;
      updateExportButtons();
    }

    function updateExportButtons() {
      const canExportResultSet = !!(currentResult && currentResult.statementType === "RESULT_SET");
      const activeShardAvailable = canExportResultSet && !!currentSelectShard;
      exportActiveCsvButtonEl.disabled = !activeShardAvailable;
      exportAllZipButtonEl.disabled = !canExportResultSet;
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

    function renderPagination(currentPage, totalPages) {
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

    return {
      initialize,
      getCurrentResult,
      getCurrentShard,
      resetResultArea,
      setRunningState,
      renderRunningStatus,
      renderResult,
      renderExecutionError,
      renderExecutionCancelled
    };
  }

  function successfulSelectShards(result) {
    return result.shardResults.filter(item => item.status === "SUCCESS");
  }

  function translateShardStatus(status) {
    switch (status) {
      case "SUCCESS": return "Успешно";
      case "FAILED": return "Ошибка";
      default: return status || "-";
    }
  }

  namespace.createResultsController = createResultsController;
})(window);
