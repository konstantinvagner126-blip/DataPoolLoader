(function initDataPoolRunPanels(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolRunPanels || (global.DataPoolRunPanels = {});
  const { escapeHtml } = common;

  function createRunPanelsController({
    runSummaryEl,
    eventLogEl,
    technicalEventLogEl,
    runHistoryFiltersEl,
    runHistoryEl,
    summaryStructuredEl,
    summaryJsonEl
  }) {
    let latestUiState = null;
    let selectedHistoryRunId = null;
    let historySelectionPinned = false;
    let runHistoryFilter = "ALL";

    function renderState(state) {
      latestUiState = state;
      const latestRun = resolveSelectedRun(state);
      renderRunHistory(state.history || [], latestRun?.id || null);

      if (!latestRun) {
        runSummaryEl.textContent = "Запусков пока нет.";
        eventLogEl.textContent = "";
        technicalEventLogEl.textContent = "";
        runHistoryEl.innerHTML = `<div class="text-secondary small">История запусков пока пуста.</div>`;
        summaryStructuredEl.innerHTML = `<div class="text-secondary">Пока нет данных summary.</div>`;
        summaryJsonEl.textContent = "Пока нет данных summary.";
        return;
      }

      const sourceStates = buildSourceStates(latestRun);
      const timeline = buildHumanTimeline(latestRun, sourceStates);

      runSummaryEl.innerHTML = renderRunSummary(latestRun, sourceStates);
      eventLogEl.innerHTML = timeline.length > 0
        ? timeline.map(item => `
            <div class="human-log-entry human-log-entry-${escapeHtml(item.tone || "neutral")}">
              <div class="human-log-time">${formatDateTime(item.timestamp)}</div>
              <div class="human-log-text">${item.message}</div>
            </div>
          `).join("")
        : `<div class="text-secondary">Пока нет значимых событий для отображения.</div>`;

      technicalEventLogEl.textContent = (latestRun.events || [])
        .slice(-200)
        .map(event => JSON.stringify(event, null, 2))
        .join("\n\n");

      renderSummaryStructured(latestRun);
      summaryJsonEl.textContent = latestRun.summaryJson || "Summary еще не сформирован.";
    }

    function resolveSelectedRun(state) {
      const history = state.history || [];
      if (history.length === 0) {
        selectedHistoryRunId = null;
        historySelectionPinned = false;
        return null;
      }

      const activeRun = state.activeRun || history.find(run => run.status !== "SUCCESS" && run.status !== "FAILED") || null;
      const selected = history.find(run => run.id === selectedHistoryRunId);
      if (activeRun && (!historySelectionPinned || !selected)) {
        selectedHistoryRunId = activeRun.id;
        historySelectionPinned = false;
        return activeRun;
      }

      if (selected) {
        return selected;
      }

      const preferred = activeRun || history[0];
      selectedHistoryRunId = preferred.id;
      historySelectionPinned = false;
      return preferred;
    }

    function renderRunHistory(history, activeRunId) {
      renderRunHistoryFilters();

      if (!history || history.length === 0) {
        runHistoryEl.innerHTML = `<div class="text-secondary small">История запусков пока пуста.</div>`;
        return;
      }

      const filteredHistory = history.filter(matchesRunHistoryFilter);
      if (filteredHistory.length === 0) {
        runHistoryEl.innerHTML = `<div class="text-secondary small">По выбранному фильтру запусков нет.</div>`;
        return;
      }

      runHistoryEl.innerHTML = filteredHistory.slice(0, 20).map(run => `
        <button
          type="button"
          class="run-history-item ${run.id === activeRunId ? "run-history-item-active" : ""}"
          data-run-id="${escapeHtml(run.id)}"
        >
          <div class="run-history-head">
            <span class="run-history-title">${escapeHtml(run.moduleTitle)}</span>
            <span class="status-badge status-${(run.status || "PENDING").toLowerCase()}">${translateStatus(run.status)}</span>
          </div>
          <div class="run-history-meta">${formatDateTime(run.startedAt)}</div>
          <div class="run-history-meta">merged.csv: ${run.mergedRowCount || 0} строк</div>
          <div class="run-history-meta">${run.finishedAt ? `Завершен: ${formatDateTime(run.finishedAt)}` : "Еще выполняется"}</div>
        </button>
      `).join("");

      runHistoryEl.querySelectorAll("[data-run-id]").forEach(button => {
        button.addEventListener("click", () => {
          selectedHistoryRunId = button.dataset.runId;
          historySelectionPinned = true;
          renderState(latestUiState);
        });
      });
    }

    function renderRunHistoryFilters() {
      const filters = [
        ["ALL", "Все"],
        ["RUNNING", "Активные"],
        ["SUCCESS", "Успешные"],
        ["FAILED", "С ошибкой"]
      ];
      runHistoryFiltersEl.innerHTML = filters.map(([value, label]) => `
        <button
          type="button"
          class="run-history-filter ${runHistoryFilter === value ? "run-history-filter-active" : ""}"
          data-run-history-filter="${value}"
        >
          ${label}
        </button>
      `).join("");

      runHistoryFiltersEl.querySelectorAll("[data-run-history-filter]").forEach(button => {
        button.addEventListener("click", () => {
          runHistoryFilter = button.dataset.runHistoryFilter;
          if (latestUiState) {
            renderState(latestUiState);
          }
        });
      });
    }

    function matchesRunHistoryFilter(run) {
      switch (runHistoryFilter) {
        case "RUNNING":
          return run.status === "RUNNING";
        case "SUCCESS":
          return run.status === "SUCCESS";
        case "FAILED":
          return run.status === "FAILED";
        default:
          return true;
      }
    }

    function renderRunSummary(run, sourceStates) {
      const overallStatus = translateStatus(run.status);
      const stageLabel = detectCurrentStage(run);
      const successCount = Object.values(sourceStates).filter(state => state.status === "SUCCESS").length;
      const failedCount = Object.values(sourceStates).filter(state => state.status === "FAILED" || state.status === "SKIPPED_SCHEMA_MISMATCH").length;

      return `
        <div class="run-summary-header">
          <div>
            <div class="run-summary-title">${escapeHtml(run.moduleTitle)}</div>
            <div class="run-summary-subtitle">${escapeHtml(stageLabel)}</div>
          </div>
          <span class="status-badge status-${(run.status || "PENDING").toLowerCase()}">${overallStatus}</span>
        </div>

        <div class="run-summary-metrics">
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Строк в merged</div>
            <div class="run-summary-metric-value">${formatNumber(run.mergedRowCount || 0)}</div>
          </div>
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Успешные sources</div>
            <div class="run-summary-metric-value">${formatNumber(successCount)}</div>
          </div>
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Ошибки sources</div>
            <div class="run-summary-metric-value">${formatNumber(failedCount)}</div>
          </div>
        </div>

        <div class="run-summary-list">
          ${renderSummaryKeyValue("Старт", formatDateTime(run.startedAt))}
          ${renderSummaryKeyValue("Завершение", run.finishedAt ? formatDateTime(run.finishedAt) : "еще выполняется")}
          ${renderSummaryKeyValue("Папка результата", run.outputDir || "-")}
          ${renderSummaryKeyValue("Ошибка", run.errorMessage || "-")}
        </div>

        <div class="run-summary-section">
          <div class="run-summary-section-title">Состояние источников</div>
          ${renderSourceStatusTable(sourceStates)}
        </div>
      `;
    }

    function renderSummaryStructured(run) {
      if (!run.summaryJson) {
        summaryStructuredEl.innerHTML = `
          <div class="text-secondary">Summary еще не сформирован.</div>
        `;
        return;
      }

      let summary;
      try {
        summary = JSON.parse(run.summaryJson);
      } catch (_) {
        summaryStructuredEl.innerHTML = `
          <div class="text-secondary">Не удалось разобрать summary.json. Raw JSON доступен ниже.</div>
        `;
        return;
      }

      const targetLoad = summary.targetLoad || {};
      const allocations = summary.mergeDetails?.sourceAllocations || [];
      const successfulSources = summary.successfulSources || [];
      const failedSources = summary.failedSources || [];
      const targetStatus = String(targetLoad.status || "PENDING").toLowerCase();
      const targetError = targetLoad.errorMessage || "-";

      summaryStructuredEl.innerHTML = `
        <div class="summary-section-card">
          <div class="summary-section-title">Итог запуска</div>
          ${renderSummaryOverviewTable([
            { label: "Старт", value: formatDateTime(summary.startedAt) },
            { label: "Завершение", value: formatDateTime(summary.finishedAt) },
            { label: "Merge mode", value: summary.mergeMode || "-", tone: "important" },
            { label: "Файл merged", value: summary.mergedFile || "-" },
            { label: "Строк в merged", value: formatNumber(summary.mergedRowCount), tone: "important" },
            { label: "Max merged rows", value: summary.maxMergedRows == null ? "без ограничения" : formatNumber(summary.maxMergedRows) },
            { label: "Output", value: run.outputDir || "-" },
            {
              label: "Target status",
              value: `<span class="status-badge status-${targetStatus}">${translateStatus(targetLoad.status)}</span>`,
              html: true,
              tone: statusTone(targetLoad.status)
            },
            { label: "Target table", value: targetLoad.table || "-", tone: targetLoad.table ? "important" : "default" },
            { label: "Загружено", value: formatNumber(targetLoad.rowCount || 0), tone: "important" },
            { label: "Успешных sources", value: formatNumber(successfulSources.length), tone: successfulSources.length > 0 ? "success" : "default" },
            { label: "Ошибочных sources", value: formatNumber(failedSources.length), tone: failedSources.length > 0 ? "failed" : "success" },
            { label: "Ошибка target", value: targetError, tone: targetError !== "-" ? "failed" : "success" }
          ])}
        </div>

        <div class="summary-section-card mt-4">
          <div class="summary-section-title">Распределение merged по источникам</div>
          ${renderSummaryAllocations(allocations)}
        </div>

        <div class="summary-section-card mt-4">
          <div class="summary-section-title">Проблемные источники</div>
          ${renderSummaryFailedSources(failedSources)}
        </div>
      `;
    }

    return {
      renderState
    };
  }

  function statusTone(status) {
    const normalized = String(status || "PENDING").toUpperCase();
    if (normalized === "SUCCESS") {
      return "success";
    }
    if (normalized === "FAILED" || normalized === "SKIPPED_SCHEMA_MISMATCH") {
      return "failed";
    }
    if (normalized === "RUNNING") {
      return "important";
    }
    return "default";
  }

  function renderSummaryOverviewTable(rows) {
    return `
      <div class="table-responsive mt-3">
        <table class="table source-status-table align-middle mb-0 summary-overview-table">
          <tbody>
            ${rows.map(renderSummaryOverviewRow).join("")}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderSummaryOverviewRow(row) {
    const tone = row.tone || "default";
    const valueClass = row.compact ? "summary-overview-value summary-overview-value-compact" : "summary-overview-value";
    return `
      <tr class="summary-overview-row summary-overview-row-${escapeHtml(tone)}">
        <th class="summary-overview-label">${escapeHtml(row.label)}</th>
        <td class="${valueClass}">${row.html ? row.value : escapeHtml(row.value)}</td>
      </tr>
    `;
  }

  function renderSummaryKeyValue(label, value) {
    return `
      <div class="summary-key-value-row">
        <span class="summary-key-value-label">${escapeHtml(label)}</span>
        <span class="summary-key-value-value">${escapeHtml(value)}</span>
      </div>
    `;
  }

  function renderSummaryAllocations(allocations) {
    if (!allocations || allocations.length === 0) {
      return `<div class="text-secondary small">Нет данных по распределению строк.</div>`;
    }

    return `
      <div class="table-responsive">
        <table class="table source-status-table align-middle mb-0">
          <thead>
            <tr>
              <th>Источник</th>
              <th>Доступно строк</th>
              <th>Попало в merged</th>
              <th>Доля</th>
            </tr>
          </thead>
          <tbody>
            ${allocations.map(item => `
              <tr>
                <td><strong>${escapeHtml(item.sourceName)}</strong></td>
                <td>${formatNumber(item.availableRows)}</td>
                <td>${formatNumber(item.mergedRows)}</td>
                <td>${formatPercent(item.mergedPercent)}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    `;
  }

  function renderSummaryFailedSources(failedSources) {
    if (!failedSources || failedSources.length === 0) {
      return `<div class="text-secondary small">Ошибочных источников нет.</div>`;
    }

    return `
      <div class="table-responsive">
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
            ${failedSources.map(source => `
              <tr>
                <td><strong>${escapeHtml(source.sourceName)}</strong></td>
                <td><span class="status-badge status-${String(source.status || "PENDING").toLowerCase()}">${translateStatus(source.status)}</span></td>
                <td>${formatNumber(source.rowCount || 0)}</td>
                <td>${escapeHtml(source.errorMessage || "-")}</td>
              </tr>
            `).join("")}
          </tbody>
        </table>
      </div>
    `;
  }

  function buildSourceStates(run) {
    const states = {};
    (run.events || []).forEach(event => {
      if (!event.sourceName) {
        return;
      }
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
      if (entry) {
        timeline.push(entry);
      }
    });
    return timeline.slice(-40);
  }

  function toHumanEvent(event) {
    switch (detectEventKind(event)) {
      case "runStarted":
        return { timestamp: event.timestamp, message: `Запуск начат. Источников: ${event.sourceNames.length}, режим merge: ${event.mergeMode}.`, tone: "neutral" };
      case "sourceStarted":
        return { timestamp: event.timestamp, message: `Начата выгрузка из источника ${event.sourceName}.`, tone: "neutral" };
      case "sourceProgress":
        return { timestamp: event.timestamp, message: `Источник ${event.sourceName}: выгружено ${event.rowCount} строк.`, tone: "neutral" };
      case "sourceFinished":
        if (event.status === "SUCCESS") {
          return { timestamp: event.timestamp, message: `Источник ${event.sourceName} завершен успешно. Получено ${event.rowCount} строк.`, tone: "success" };
        }
        return { timestamp: event.timestamp, message: `Источник ${event.sourceName} завершился с ошибкой: ${event.errorMessage || "неизвестная ошибка"}.`, tone: "error" };
      case "schemaMismatch":
        return { timestamp: event.timestamp, message: `Источник ${event.sourceName} исключен из объединения: набор колонок отличается от базового.`, tone: "error" };
      case "mergeStarted":
        return { timestamp: event.timestamp, message: `Начато объединение данных из ${event.sourceNames.length} успешных источников.`, tone: "neutral" };
      case "mergeFinished":
        return { timestamp: event.timestamp, message: `Объединение завершено. В merged.csv записано ${event.rowCount} строк.`, tone: "success" };
      case "targetStarted":
        return { timestamp: event.timestamp, message: `Начата загрузка merged.csv в таблицу ${event.table}.`, tone: "neutral" };
      case "targetFinished":
        if (event.status === "SUCCESS") {
          return { timestamp: event.timestamp, message: `Загрузка в таблицу ${event.table} завершена. Загружено ${event.rowCount} строк.`, tone: "success" };
        }
        if (event.status === "SKIPPED") {
          return { timestamp: event.timestamp, message: "Загрузка в target пропущена.", tone: "neutral" };
        }
        return { timestamp: event.timestamp, message: `Загрузка в таблицу ${event.table} завершилась ошибкой: ${event.errorMessage || "неизвестная ошибка"}.`, tone: "error" };
      case "outputCleanup":
        return { timestamp: event.timestamp, message: `Удален временный файл ${event.fileName}.`, tone: "neutral" };
      case "runFinished":
        if (event.status === "SUCCESS") {
          return { timestamp: event.timestamp, message: "Запуск завершен успешно.", tone: "success" };
        }
        return { timestamp: event.timestamp, message: `Запуск завершен с ошибкой: ${event.errorMessage || "неизвестная ошибка"}.`, tone: "error" };
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
      <div class="source-state-grid">
        ${rows.sort((a, b) => a[0].localeCompare(b[0])).map(([source, state]) => `
          <div class="source-state-card source-state-card-${statusTone(state.status)}">
            <div class="source-state-head">
              <div class="source-state-title">${escapeHtml(source)}</div>
              <span class="status-badge status-${(state.status || "PENDING").toLowerCase()}">${translateStatus(state.status)}</span>
            </div>
            <div class="source-state-meta">
              <span class="source-state-label">Строк</span>
              <strong class="source-state-value">${formatNumber(state.rowCount || 0)}</strong>
            </div>
            ${state.errorMessage ? `
              <div class="source-state-error">${escapeHtml(state.errorMessage)}</div>
            ` : ""}
          </div>
        `).join("")}
      </div>
    `;
  }

  function detectCurrentStage(run) {
    if (run.status === "SUCCESS") {
      return "Завершено";
    }
    if (run.status === "FAILED") {
      return "Завершено с ошибкой";
    }
    const events = run.events || [];
    if (events.some(event => detectEventKind(event) === "targetStarted")) {
      return "Загрузка в target";
    }
    if (events.some(event => detectEventKind(event) === "mergeStarted")) {
      return "Объединение";
    }
    if (events.some(event => detectEventKind(event) === "sourceStarted")) {
      return "Выгрузка из источников";
    }
    return "Подготовка";
  }

  function detectEventKind(event) {
    if (event.type) {
      return normalizeEventType(event.type);
    }
    if (Array.isArray(event.sourceNames) && "mergeMode" in event && "targetEnabled" in event) {
      return "runStarted";
    }
    if ("fileName" in event && !("sourceName" in event)) {
      return "outputCleanup";
    }
    if ("summaryFile" in event && "mergedRowCount" in event && "status" in event) {
      return "runFinished";
    }
    if ("table" in event && "expectedRowCount" in event) {
      return "targetStarted";
    }
    if ("table" in event && "rowCount" in event && "status" in event) {
      return "targetFinished";
    }
    if (Array.isArray(event.sourceCounts) || (event.sourceCounts && typeof event.sourceCounts === "object")) {
      return "mergeFinished";
    }
    if (Array.isArray(event.sourceNames) && "outputFile" in event) {
      return "mergeStarted";
    }
    if ("expectedColumns" in event && "actualColumns" in event) {
      return "schemaMismatch";
    }
    if ("sourceName" in event && "columns" in event && "status" in event) {
      return "sourceFinished";
    }
    if ("sourceName" in event && "rowCount" in event) {
      return "sourceProgress";
    }
    if ("sourceName" in event) {
      return "sourceStarted";
    }
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
    if (!value) {
      return "-";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString("ru-RU");
  }

  function formatNumber(value) {
    if (value == null || value === "") {
      return "-";
    }
    const numeric = Number(value);
    if (Number.isNaN(numeric)) {
      return String(value);
    }
    return numeric.toLocaleString("ru-RU");
  }

  function formatPercent(value) {
    if (value == null || value === "") {
      return "-";
    }
    const numeric = Number(value);
    if (Number.isNaN(numeric)) {
      return String(value);
    }
    return `${numeric.toLocaleString("ru-RU", { minimumFractionDigits: 0, maximumFractionDigits: 2 })}%`;
  }

  namespace.createRunPanelsController = createRunPanelsController;
})(window);
