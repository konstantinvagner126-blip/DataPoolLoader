(function initDataPoolRunPanels(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolRunPanels || (global.DataPoolRunPanels = {});
  const runProgress = global.DataPoolRunProgress || {};
  const { escapeHtml, scrollToElement, revealCollapse } = common;

  function createRunPanelsController({
    runSummaryEl,
    eventLogEl,
    technicalEventLogEl,
    runHistoryFiltersEl,
    runHistoryEl,
    summaryStructuredEl,
    summaryJsonEl,
    resultArtifactsEl,
    resultPanelId,
    rawSummaryPanelId,
    rawSummaryCollapseId,
    compactMode = false,
    historyLimit = 20,
    humanTimelineLimit = 40,
    onRunSelected = null,
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
        if (technicalEventLogEl) {
          technicalEventLogEl.textContent = "";
        }
        runHistoryEl.innerHTML = `<div class="text-secondary small">История запусков пока пуста.</div>`;
        if (summaryStructuredEl) {
          summaryStructuredEl.innerHTML = `<div class="text-secondary">Пока нет данных summary.</div>`;
        }
        if (summaryJsonEl) {
          summaryJsonEl.textContent = "Пока нет данных summary.";
        }
        if (resultArtifactsEl) {
          resultArtifactsEl.innerHTML = `<div class="text-secondary small">Пути к результатам запуска появятся после выполнения.</div>`;
        }
        return;
      }

      const sourceStates = buildSourceStates(latestRun);
      const timeline = buildHumanTimeline(latestRun, sourceStates, humanTimelineLimit);

      runSummaryEl.innerHTML = renderRunSummary(latestRun, sourceStates);
      bindRunQuickActions();
      eventLogEl.innerHTML = timeline.length > 0
        ? timeline.map(item => `
            <div class="human-log-entry human-log-entry-${escapeHtml(item.tone || "neutral")}">
              <div class="human-log-time">${formatDateTime(item.timestamp)}</div>
              <div class="human-log-text">${item.message}</div>
            </div>
          `).join("")
        : `<div class="text-secondary">Пока нет значимых событий для отображения.</div>`;

      if (technicalEventLogEl) {
        technicalEventLogEl.textContent = (latestRun.events || [])
          .slice(-200)
          .map(event => JSON.stringify(event, null, 2))
          .join("\n\n");
      }

      if (summaryStructuredEl) {
        renderSummaryStructured(latestRun);
      }
      if (summaryJsonEl) {
        summaryJsonEl.textContent = latestRun.summaryJson || "Итоги запуска еще не сформированы.";
      }
      if (resultArtifactsEl) {
        resultArtifactsEl.innerHTML = renderRunArtifacts(latestRun, sourceStates);
      }
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

      runHistoryEl.innerHTML = filteredHistory.slice(0, historyLimit).map(run => `
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
          onRunSelected?.(selectedHistoryRunId);
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
      const stageKey = detectCurrentStageKey(run);
      const stageLabel = stageLabelByKey(stageKey);
      const successCount = Object.values(sourceStates).filter(state => state.status === "SUCCESS").length;
      const failedCount = Object.values(sourceStates).filter(state => state.status === "FAILED" || state.status === "SKIPPED_SCHEMA_MISMATCH").length;
      const stageStates = runProgress.buildStageStates
        ? runProgress.buildStageStates(stageKey, run.status)
        : [];
      const progressWidget = runProgress.renderRunProgressWidget
        ? runProgress.renderRunProgressWidget({
            title: run.moduleTitle,
            subtitle: compactMode ? `Текущий этап: ${stageLabel}` : stageLabel,
            statusLabel: overallStatus,
            statusClass: `status-badge status-${(run.status || "PENDING").toLowerCase()}`,
            running: run.status === "RUNNING",
            stages: stageStates,
            metrics: [
              { label: "Строк в merged", value: formatNumber(run.mergedRowCount || 0), tone: "important" },
              { label: "Успешные источники", value: formatNumber(successCount), tone: successCount > 0 ? "success" : "default" },
              { label: "Ошибочные источники", value: formatNumber(failedCount), tone: failedCount > 0 ? "failed" : "default" },
            ],
          })
        : "";

      return `
        ${progressWidget}

        ${renderQuickActions(run)}

        <div class="run-summary-list">
          ${renderSummaryKeyValue("Старт", formatDateTime(run.startedAt))}
          ${renderSummaryKeyValue("Завершение", run.finishedAt ? formatDateTime(run.finishedAt) : "еще выполняется")}
          ${compactMode ? "" : renderSummaryKeyValue("Папка результата", run.outputDir || "-")}
          ${renderSummaryKeyValue("Ошибка", run.errorMessage || "-")}
        </div>

        <div class="run-summary-section">
          <div class="run-summary-section-title">Состояние источников</div>
          ${renderSourceStatusTable(sourceStates)}
        </div>
      `;
    }

    function renderQuickActions(run) {
      if (compactMode) {
        return "";
      }
      const actions = [];
      if (run.outputDir && resultPanelId) {
        actions.push({
          label: "К результатам запуска",
          targetId: resultPanelId,
        });
      }
      if (run.summaryJson && rawSummaryPanelId && rawSummaryCollapseId) {
        actions.push({
          label: "Открыть summary.json",
          targetId: rawSummaryPanelId,
          collapseId: rawSummaryCollapseId,
        });
      }
      if (actions.length === 0) {
        return "";
      }

      return `
        <div class="run-result-actions">
          ${actions.map(action => `
            <button
              type="button"
              class="run-result-action-button"
              data-run-action-target="${escapeHtml(action.targetId)}"
              ${action.collapseId ? `data-run-action-collapse="${escapeHtml(action.collapseId)}"` : ""}
            >
              ${escapeHtml(action.label)}
            </button>
          `).join("")}
        </div>
      `;
    }

    function bindRunQuickActions() {
      runSummaryEl.querySelectorAll("[data-run-action-target]").forEach(button => {
        button.addEventListener("click", () => {
          const collapseId = button.dataset.runActionCollapse;
          if (collapseId) {
            revealCollapse?.(collapseId);
          }
          scrollToElement?.(button.dataset.runActionTarget);
        });
      });
    }

    function renderSummaryStructured(run) {
      if (!run.summaryJson) {
        summaryStructuredEl.innerHTML = `
          <div class="text-secondary">Итоги запуска еще не сформированы.</div>
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
            { label: "Режим объединения", value: summary.mergeMode || "-", tone: "important" },
            { label: "Файл merged", value: summary.mergedFile || "-" },
            { label: "Строк в merged", value: formatNumber(summary.mergedRowCount), tone: "important" },
            { label: "Макс. строк merged", value: summary.maxMergedRows == null ? "без ограничения" : formatNumber(summary.maxMergedRows) },
            { label: "Каталог результата", value: run.outputDir || "-" },
            {
              label: "Статус target",
              value: `<span class="status-badge status-${targetStatus}">${translateStatus(targetLoad.status)}</span>`,
              html: true,
              tone: statusTone(targetLoad.status)
            },
            { label: "Таблица target", value: targetLoad.table || "-", tone: targetLoad.table ? "important" : "default" },
            { label: "Загружено", value: formatNumber(targetLoad.rowCount || 0), tone: "important" },
            { label: "Успешных источников", value: formatNumber(successfulSources.length), tone: successfulSources.length > 0 ? "success" : "default" },
            { label: "Ошибочных источников", value: formatNumber(failedSources.length), tone: failedSources.length > 0 ? "failed" : "success" },
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

    function renderRunArtifacts(run, sourceStates) {
      const artifacts = collectRunArtifacts(run, sourceStates);
      if (artifacts.length === 0) {
        return `<div class="text-secondary small">Пути к результатам запуска пока неизвестны.</div>`;
      }

      return `
        <div class="run-artifact-grid">
          ${artifacts.map(item => `
            <div class="run-artifact-card">
              <div class="run-artifact-kind">${escapeHtml(item.kind)}</div>
              <div class="run-artifact-title">${escapeHtml(item.title)}</div>
              <div class="run-artifact-note">${escapeHtml(item.note)}</div>
              <div class="run-artifact-path"><code>${escapeHtml(item.path)}</code></div>
            </div>
          `).join("")}
        </div>
      `;
    }

    function collectRunArtifacts(run, sourceStates) {
      if (!run?.outputDir) {
        return [];
      }

      const artifacts = [];
      const outputDir = run.outputDir;
      const summary = safeParseJson(run.summaryJson);

      artifacts.push({
        kind: "Каталог",
        title: "Каталог результата",
        note: "В этой папке лежат файлы выбранного запуска.",
        path: outputDir,
      });

      const mergedFile = summary?.mergedFile || joinPath(outputDir, "merged.csv");
      artifacts.push({
        kind: "Итоговый файл",
        title: "merged.csv",
        note: "Основной объединенный результат запуска.",
        path: mergedFile,
      });

      if (run.summaryJson) {
        artifacts.push({
          kind: "Итоги запуска",
          title: "summary.json",
          note: "Файл итогов и сводных метрик. Raw JSON доступен ниже.",
          path: joinPath(outputDir, "summary.json"),
        });
      }

      Object.entries(sourceStates)
        .filter(([, state]) => state.status === "SUCCESS")
        .sort((left, right) => left[0].localeCompare(right[0]))
        .forEach(([sourceName]) => {
          artifacts.push({
            kind: "Источник",
            title: `${sourceName}.csv`,
            note: `Выгрузка по источнику ${sourceName}.`,
            path: joinPath(outputDir, `${sourceName}.csv`),
          });
        });

      return artifacts;
    }

    return {
      renderState,
      selectedRunId: () => selectedHistoryRunId,
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

  function safeParseJson(value) {
    if (!value) {
      return null;
    }
    try {
      return JSON.parse(value);
    } catch (_) {
      return null;
    }
  }

  function joinPath(basePath, fileName) {
    const base = String(basePath || "").trim();
    if (!base) {
      return fileName;
    }
    const separator = base.includes("\\") && !base.includes("/") ? "\\" : "/";
    return `${base.replace(/[\\/]+$/, "")}${separator}${fileName}`;
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

  function buildHumanTimeline(run, sourceStates, limit = 40) {
    const timeline = [];
    (run.events || []).forEach(event => {
      const entry = toHumanEvent(event, sourceStates);
      if (entry) {
        timeline.push(entry);
      }
    });
    return timeline.slice(-limit);
  }

  function toHumanEvent(event) {
    switch (detectEventKind(event)) {
      case "runStarted":
        return { timestamp: event.timestamp, message: `Запуск начат. Источников: ${event.sourceNames.length}, режим объединения: ${event.mergeMode}.`, tone: "neutral" };
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

  function detectCurrentStageKey(run) {
    if (run.status === "SUCCESS") {
      return "finish";
    }
    const events = run.events || [];
    if (events.some(event => ["targetStarted", "targetFinished"].includes(detectEventKind(event)))) {
      return "target";
    }
    if (events.some(event => ["mergeStarted", "mergeFinished"].includes(detectEventKind(event)))) {
      return "merge";
    }
    if (events.some(event => ["sourceStarted", "sourceProgress", "sourceFinished", "schemaMismatch"].includes(detectEventKind(event)))) {
      return "sources";
    }
    return run.status === "FAILED" ? "finish" : "prepare";
  }

  function stageLabelByKey(stageKey) {
    const fallback = {
      prepare: "Подготовка",
      sources: "Выгрузка из источников",
      merge: "Объединение",
      target: "Загрузка в target",
      finish: "Завершение",
    };
    const sharedLabel = runProgress.getStageDefinitions?.().find(stage => stage.key === stageKey)?.label;
    return sharedLabel || fallback[stageKey] || fallback.prepare;
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
