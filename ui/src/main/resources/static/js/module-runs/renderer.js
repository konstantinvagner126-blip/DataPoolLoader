(function registerModuleRunsRenderer(global) {
  const namespace = global.DataPoolModuleRuns = global.DataPoolModuleRuns || {};
  const runProgress = global.DataPoolRunProgress || {};
  const dbRoot = global.DataPoolDb || {};
  const dbModules = dbRoot.modules || {};
  const formatters = dbModules.formatters || {};

  function createRenderer(ctx) {
    const { refs, state } = ctx;
    const { escapeHtml, scrollToElement, revealCollapse } = ctx.common;
    const historyLimit = ctx.historyLimit || 20;
    const eventTimelineLimit = ctx.eventTimelineLimit || 60;
    let latestHandlers = {
      onSelectRun: null,
      onHistoryLimitChange: null,
    };

    function render(handlers) {
      latestHandlers = {
        onSelectRun: handlers?.onSelectRun || latestHandlers.onSelectRun,
        onHistoryLimitChange: handlers?.onHistoryLimitChange || latestHandlers.onHistoryLimitChange,
      };
      const onSelectRun = latestHandlers.onSelectRun;
      const onHistoryLimitChange = latestHandlers.onHistoryLimitChange;
      renderRunHistoryFilters(onSelectRun);
      renderHistoryControls(onHistoryLimitChange);
      renderRunHistory(onSelectRun);
      renderSelectedRunDetails();
      applyPanelVisibility();
    }

    function renderRunHistory(onSelectRun) {
      const filteredRuns = getFilteredRuns();
      if (!filteredRuns.length) {
        refs.runHistory.innerHTML = state.currentRuns.length === 0
          ? '<div class="text-secondary small">Запусков пока нет.</div>'
          : '<div class="text-secondary small">По выбранным фильтрам или поиску запусков нет.</div>';
        return;
      }

      refs.runHistory.innerHTML = filteredRuns.slice(0, currentHistoryLimit()).map(run => `
        <button
          type="button"
          class="run-history-item ${run.runId === state.selectedRunId ? 'run-history-item-active' : ''}"
          data-run-id="${escapeHtml(run.runId)}"
        >
          <div class="run-history-head">
            <span class="run-history-title">${escapeHtml(run.moduleTitle || run.moduleId)}</span>
            <span class="${statusBadgeClass(run.status)}">${escapeHtml(translateStatus(run.status))}</span>
          </div>
          <div class="run-history-meta">${escapeHtml(formatDateTime(run.requestedAt || run.startedAt))}</div>
          <div class="run-history-meta">
            Строк в merged: ${escapeHtml(formatNumber(run.mergedRowCount || 0))}
            ${run.targetStatus ? ` · target: ${escapeHtml(translateStatus(run.targetStatus))}` : ''}
          </div>
          ${renderOptionalHistoryMeta(run)}
        </button>
      `).join('');

      refs.runHistory.querySelectorAll('[data-run-id]').forEach(button => {
        button.addEventListener('click', async () => {
          await onSelectRun(button.dataset.runId);
        });
      });
    }

    function renderOptionalHistoryMeta(run) {
      const parts = [];
      if (run.launchSourceKind) {
        parts.push(`Источник запуска: ${escapeHtml(translateLaunchSource(run.launchSourceKind))}`);
      }
      const successful = Number(run.successfulSourceCount || 0);
      const failed = Number(run.failedSourceCount || 0);
      const skipped = Number(run.skippedSourceCount || 0);
      if (successful > 0 || failed > 0 || skipped > 0) {
        parts.push(
          `Успешных: ${escapeHtml(String(successful))} · ошибок: ${escapeHtml(String(failed))} · пропущено: ${escapeHtml(String(skipped))}`,
        );
      }
      if (!parts.length) {
        return '';
      }
      return `<div class="run-history-meta">${parts.join('</div><div class="run-history-meta">')}</div>`;
    }

    function renderSelectedRunDetails() {
      if (!state.selectedRunDetails?.run) {
        renderEmptyDetails();
        return;
      }

      const details = state.selectedRunDetails;
      const run = details.run;
      refs.runSummary.innerHTML = renderRunSummary(run, details);
      refs.runStructuredSummary.innerHTML = renderStructuredSummary(run, details.summaryJson);
      refs.runSummaryJson.textContent = formatRawJson(details.summaryJson);
      refs.runSourceResults.innerHTML = renderSourceResults(details.sourceResults || []);
      refs.runArtifacts.innerHTML = renderArtifacts(details.artifacts || []);
      refs.eventLog.innerHTML = renderHumanTimeline(details.events || []);
      refs.technicalEventLog.textContent = renderTechnicalDiagnostics(details.events || []);
      bindRunQuickActions();
    }

    function renderEmptyDetails() {
      const placeholder = state.currentRuns.length === 0
        ? 'Запусков пока нет.'
        : 'Выбери запуск из списка слева, чтобы посмотреть детали.';
      refs.runSummary.innerHTML = `<div class="text-secondary small">${escapeHtml(placeholder)}</div>`;
      refs.runStructuredSummary.innerHTML = '<div class="text-secondary">Итоги запуска еще не сформированы.</div>';
      refs.runSummaryJson.textContent = 'Итоги запуска еще не сформированы.';
      refs.runSourceResults.innerHTML = '<div class="text-secondary small">Данные по источникам пока недоступны.</div>';
      refs.runArtifacts.innerHTML = '<div class="text-secondary small">Результаты запуска пока недоступны.</div>';
      refs.eventLog.innerHTML = '<div class="text-secondary small">События запуска пока недоступны.</div>';
      refs.technicalEventLog.textContent = 'Технические события пока недоступны.';
    }

    function renderRunSummary(run, details) {
      const sourceResults = details.sourceResults || [];
      const successCount = Number(run.successfulSourceCount ?? sourceResults.filter(item => item.status === 'SUCCESS').length);
      const failedCount = Number(run.failedSourceCount ?? sourceResults.filter(item => item.status === 'FAILED').length);
      const skippedCount = Number(run.skippedSourceCount ?? sourceResults.filter(item => item.status === 'SKIPPED').length);
      const warningCount = failedCount + skippedCount;
      const stageKey = detectRunStageKey(run, details.events || []);
      const stageLabel = stageLabelByKey(stageKey);
      const stageStates = runProgress.buildStageStates
        ? runProgress.buildStageStates(stageKey, run.status)
        : [];
      const progressWidget = runProgress.renderRunProgressWidget
        ? runProgress.renderRunProgressWidget({
            title: run.moduleTitle || run.moduleId,
            subtitle: `${stageLabel} · запуск ${run.runId}`,
            statusLabel: translateStatus(run.status),
            statusClass: statusBadgeClass(run.status),
            running: String(run.status || '').toUpperCase() === 'RUNNING',
            stages: stageStates,
            metrics: [
              { label: 'Строк в merged', value: formatNumber(run.mergedRowCount || 0), tone: 'important' },
              { label: 'Успешные источники', value: formatNumber(successCount), tone: successCount > 0 ? 'success' : 'default' },
              { label: 'Предупреждения и ошибки', value: formatNumber(warningCount), tone: warningCount > 0 ? 'failed' : 'default' },
              { label: 'Загружено', value: formatNumber(run.targetRowsLoaded || 0), tone: Number(run.targetRowsLoaded || 0) > 0 ? 'important' : 'default' },
            ],
          })
        : '';

      return `
        ${progressWidget}
        ${renderQuickActions(details)}
        <div class="run-summary-list">
          ${renderSummaryKeyValue('Запрошен', formatDateTime(run.requestedAt || run.startedAt))}
          ${renderSummaryKeyValue('Старт', formatDateTime(run.startedAt))}
          ${renderSummaryKeyValue('Завершение', run.finishedAt ? formatDateTime(run.finishedAt) : 'еще выполняется')}
          ${renderSummaryKeyValue('Источник запуска', translateLaunchSource(run.launchSourceKind))}
          ${renderSummaryKeyValue('Целевая таблица', run.targetTableName || '-')}
          ${renderSummaryKeyValue('Статус target', translateStatus(run.targetStatus))}
          ${renderSummaryKeyValue('Каталог результата', run.outputDir || '-')}
          ${renderSummaryKeyValue('Снимок', run.executionSnapshotId || '-')}
          ${renderSummaryKeyValue('Ошибка', run.errorMessage || '-')}
        </div>
      `;
    }

    function renderQuickActions(details) {
      const actions = [];
      if ((details.artifacts || []).length > 0) {
        actions.push({ label: 'К результатам запуска', targetId: 'runArtifactsPanel' });
      }
      if ((details.sourceResults || []).length > 0) {
        actions.push({ label: 'К результатам по источникам', targetId: 'runSourceResultsPanel' });
      }
      if (details.summaryJson && details.summaryJson !== '{}' && state.uiSettings?.showRawSummaryJson !== false) {
        actions.push({
          label: 'Открыть summary.json',
          targetId: 'runSummaryRawPanel',
          collapseId: 'runSummaryJsonCollapse',
        });
      }
      if (!actions.length) {
        return '';
      }
      return `
        <div class="run-result-actions">
          ${actions.map(action => `
            <button
              type="button"
              class="run-result-action-button"
              data-run-action-target="${escapeHtml(action.targetId)}"
              ${action.collapseId ? `data-run-action-collapse="${escapeHtml(action.collapseId)}"` : ''}
            >
              ${escapeHtml(action.label)}
            </button>
          `).join('')}
        </div>
      `;
    }

    function bindRunQuickActions() {
      refs.runSummary.querySelectorAll('[data-run-action-target]').forEach(button => {
        button.addEventListener('click', () => {
          const collapseId = button.dataset.runActionCollapse;
          if (collapseId) {
            revealCollapse?.(collapseId);
          }
          scrollToElement?.(button.dataset.runActionTarget);
        });
      });
    }

    function renderHumanTimeline(events) {
      if (!events.length) {
        return '<div class="text-secondary small">События запуска пока недоступны.</div>';
      }
      return events.slice(-eventTimelineLimit).map(event => `
        <div class="${eventEntryClass(event.severity)}">
          <div class="human-log-time">
            ${escapeHtml(formatDateTime(event.timestamp))}
            ${event.stage ? ` · ${escapeHtml(translateStage(event.stage))}` : ''}
            ${event.sourceName ? ` · ${escapeHtml(event.sourceName)}` : ''}
          </div>
          <div class="human-log-text">${escapeHtml(event.message || event.eventType)}</div>
        </div>
      `).join('');
    }

    function renderTechnicalDiagnostics(events) {
      if (!events.length) {
        return 'Технические события пока недоступны.';
      }
      return events
        .slice(-200)
        .map(event => JSON.stringify(event, null, 2))
        .join('\n\n');
    }

    function renderStructuredSummary(run, summaryJson) {
      if (!summaryJson || summaryJson === '{}') {
        return '<div class="text-secondary">Итоги запуска еще не сформированы.</div>';
      }

      let summary;
      try {
        summary = JSON.parse(summaryJson);
      } catch (_) {
        return '<div class="text-secondary">Не удалось разобрать summary.json. Raw JSON доступен ниже.</div>';
      }

      const targetLoad = summary.targetLoad || {};
      const allocations = summary.mergeDetails?.sourceAllocations || [];
      const successfulSources = summary.successfulSources || [];
      const failedSources = summary.failedSources || [];

      return `
        <div class="summary-section-card">
          <div class="summary-section-title">Итог запуска</div>
          ${renderSummaryOverviewTable([
            { label: 'Старт', value: formatDateTime(summary.startedAt) },
            { label: 'Завершение', value: formatDateTime(summary.finishedAt) },
            { label: 'Режим объединения', value: summary.mergeMode || '-', tone: 'important' },
            { label: 'Файл merged', value: summary.mergedFile || '-' },
            { label: 'Строк в merged', value: formatNumber(summary.mergedRowCount), tone: 'important' },
            { label: 'Макс. строк merged', value: summary.maxMergedRows == null ? 'без ограничения' : formatNumber(summary.maxMergedRows) },
            {
              label: 'Статус target',
              value: `<span class="${statusBadgeClass(targetLoad.status)}">${escapeHtml(translateStatus(targetLoad.status))}</span>`,
              html: true,
              tone: statusTone(targetLoad.status),
            },
            { label: 'Таблица target', value: targetLoad.table || '-', tone: targetLoad.table ? 'important' : 'default' },
            { label: 'Загружено', value: formatNumber(targetLoad.rowCount || 0), tone: 'important' },
            { label: 'Успешных источников', value: formatNumber(successfulSources.length), tone: successfulSources.length > 0 ? 'success' : 'default' },
            { label: 'Ошибочных источников', value: formatNumber(failedSources.length), tone: failedSources.length > 0 ? 'failed' : 'success' },
            { label: 'Каталог результата', value: run.outputDir || '-' },
            { label: 'Ошибка target', value: targetLoad.errorMessage || '-', tone: targetLoad.errorMessage ? 'failed' : 'success' },
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

    function renderSourceResults(sourceResults) {
      if (!sourceResults.length) {
        return '<div class="text-secondary small">Данные по источникам пока недоступны.</div>';
      }
      return `
        <div class="table-responsive">
          <table class="table source-status-table align-middle mb-0">
            <thead>
              <tr>
                <th>Источник</th>
                <th>Статус</th>
                <th>Экспортировано</th>
                <th>Попало в merged</th>
                <th>Старт</th>
                <th>Финиш</th>
                <th>Ошибка</th>
              </tr>
            </thead>
            <tbody>
              ${sourceResults.map(item => `
                <tr>
                  <td><strong>${escapeHtml(item.sourceName)}</strong></td>
                  <td><span class="${statusBadgeClass(item.status)}">${escapeHtml(translateStatus(item.status))}</span></td>
                  <td>${escapeHtml(formatNumber(item.exportedRowCount || 0))}</td>
                  <td>${escapeHtml(formatNumber(item.mergedRowCount || 0))}</td>
                  <td>${escapeHtml(formatDateTime(item.startedAt))}</td>
                  <td>${escapeHtml(formatDateTime(item.finishedAt))}</td>
                  <td>${escapeHtml(item.errorMessage || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function renderArtifacts(artifacts) {
      if (!artifacts.length) {
        return '<div class="text-secondary small">Результаты запуска пока недоступны.</div>';
      }
      return `
        <div class="table-responsive">
          <table class="table source-status-table align-middle mb-0">
            <thead>
              <tr>
                <th>Тип результата</th>
                <th>Файл</th>
                <th>Статус</th>
                <th>Размер</th>
                <th>Путь</th>
              </tr>
            </thead>
            <tbody>
              ${artifacts.map(item => `
                <tr>
                  <td><strong>${escapeHtml(translateArtifactKind(item.artifactKind))}</strong></td>
                  <td>${escapeHtml(extractFileName(item.filePath, item.artifactKey))}</td>
                  <td><span class="${artifactStatusClass(item.storageStatus)}">${escapeHtml(translateArtifactStatus(item.storageStatus))}</span></td>
                  <td>${escapeHtml(formatFileSize(item.fileSizeBytes))}</td>
                  <td><code>${escapeHtml(item.filePath)}</code></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function renderRunHistoryFilters(onSelectRun) {
      const filters = [
        ['ALL', 'Все'],
        ['RUNNING', 'Активные'],
        ['SUCCESS', 'Успешные'],
        ['WARNINGS', 'С предупреждениями'],
        ['FAILED', 'С ошибкой'],
      ];

      refs.runHistoryFilters.innerHTML = filters.map(([value, label]) => `
        <button
          type="button"
          class="run-history-filter ${state.runHistoryFilter === value ? 'run-history-filter-active' : ''}"
          data-run-history-filter="${value}"
        >
          ${label}
        </button>
      `).join('');

      refs.runHistoryFilters.querySelectorAll('[data-run-history-filter]').forEach(button => {
        button.addEventListener('click', async () => {
          state.runHistoryFilter = button.dataset.runHistoryFilter || 'ALL';
          const filteredRuns = getFilteredRuns();
          if (!filteredRuns.some(run => run.runId === state.selectedRunId)) {
            state.selectedRunId = filteredRuns[0]?.runId || null;
            if (state.selectedRunId) {
              await onSelectRun(state.selectedRunId);
              return;
            }
            state.selectedRunDetails = null;
          }
          render(latestHandlers);
        });
      });
    }

    function renderHistoryControls(onHistoryLimitChange) {
      if (!refs.runHistoryControls) {
        return;
      }
      refs.runHistoryControls.innerHTML = `
        <label class="run-history-controls" for="runHistoryLimitSelect">
          <span class="run-history-control-label">Показывать</span>
          <select id="runHistoryLimitSelect" class="run-history-limit-select">
            ${[20, 50, 100].map(limit => `
              <option value="${limit}" ${Number(state.historyLimit || historyLimit) === limit ? 'selected' : ''}>${limit}</option>
            `).join('')}
          </select>
        </label>
        <label class="run-history-controls run-history-controls-search" for="runHistorySearchInput">
          <span class="run-history-control-label">Поиск</span>
          <input
            id="runHistorySearchInput"
            class="run-history-search-input"
            type="search"
            placeholder="runId, модуль, target, output..."
            value="${escapeHtml(state.searchQuery || '')}"
          >
        </label>
      `;
      refs.runHistoryControls.querySelector('#runHistoryLimitSelect')?.addEventListener('change', async event => {
        const nextLimit = Number(event.target.value || historyLimit);
        if (!Number.isFinite(nextLimit) || nextLimit <= 0) {
          return;
        }
        state.historyLimit = nextLimit;
        await onHistoryLimitChange?.(nextLimit);
      });
      refs.runHistoryControls.querySelector('#runHistorySearchInput')?.addEventListener('input', event => {
        state.searchQuery = String(event.target.value || '').trim();
        render(latestHandlers);
      });
    }

    function getFilteredRuns() {
      return (state.currentRuns || [])
        .filter(matchesRunHistoryFilter)
        .filter(matchesSearchQuery);
    }

    function matchesRunHistoryFilter(run) {
      switch (state.runHistoryFilter) {
        case 'RUNNING':
          return run.status === 'RUNNING';
        case 'SUCCESS':
          return run.status === 'SUCCESS' || run.status === 'SUCCESS_WITH_WARNINGS';
        case 'WARNINGS':
          return hasWarnings(run);
        case 'FAILED':
          return run.status === 'FAILED';
        default:
          return true;
      }
    }

    function matchesSearchQuery(run) {
      const query = String(state.searchQuery || '').trim().toLowerCase();
      if (!query) {
        return true;
      }
      const haystack = [
        run.runId,
        run.moduleTitle,
        run.moduleId,
        run.outputDir,
        run.targetTableName,
        run.executionSnapshotId,
        run.launchSourceKind,
        translateLaunchSource(run.launchSourceKind),
        translateStatus(run.status),
        translateStatus(run.targetStatus),
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    }

    function hasWarnings(run) {
      const status = String(run.status || '').toUpperCase();
      if (status === 'SUCCESS_WITH_WARNINGS') {
        return true;
      }
      if (Number(run.failedSourceCount || 0) > 0) {
        return true;
      }
      if (Number(run.skippedSourceCount || 0) > 0) {
        return true;
      }
      const targetStatus = String(run.targetStatus || '').toUpperCase();
      return targetStatus === 'FAILED' || targetStatus === 'SKIPPED';
    }

    function applyPanelVisibility() {
      refs.technicalDiagnosticsPanel?.classList.toggle('d-none', state.uiSettings?.showTechnicalDiagnostics === false);
      refs.runSummaryRawPanel?.classList.toggle('d-none', state.uiSettings?.showRawSummaryJson === false);
    }

    function currentHistoryLimit() {
      const numericLimit = Number(state.historyLimit || historyLimit);
      return Number.isFinite(numericLimit) && numericLimit > 0 ? numericLimit : historyLimit;
    }

    function detectRunStageKey(run, events) {
      const normalizedStatus = String(run.status || 'PENDING').toUpperCase();
      if (normalizedStatus === 'SUCCESS' || normalizedStatus === 'SUCCESS_WITH_WARNINGS') {
        return 'finish';
      }

      const stageSequence = (events || [])
        .slice()
        .reverse()
        .map(event => mapStageToKey(event.stage))
        .filter(Boolean);

      const lastOperationalStage = stageSequence.find(stage => stage !== 'finish');
      const lastStage = lastOperationalStage || stageSequence[0] || null;
      if (lastStage) {
        return lastStage;
      }
      if (run.targetTableName || run.targetStatus) {
        return normalizedStatus === 'FAILED' ? 'target' : 'prepare';
      }
      return normalizedStatus === 'FAILED' ? 'finish' : 'prepare';
    }

    function mapStageToKey(stage) {
      switch (String(stage || '').toUpperCase()) {
        case 'PREPARE':
          return 'prepare';
        case 'SOURCE':
          return 'sources';
        case 'MERGE':
          return 'merge';
        case 'TARGET':
          return 'target';
        case 'RUN':
          return 'finish';
        default:
          return null;
      }
    }

    function stageLabelByKey(stageKey) {
      const sharedLabel = runProgress.getStageDefinitions?.().find(stage => stage.key === stageKey)?.label;
      if (sharedLabel) {
        return sharedLabel;
      }
      switch (stageKey) {
        case 'sources':
          return 'Источники';
        case 'merge':
          return 'Объединение';
        case 'target':
          return 'Загрузка';
        case 'finish':
          return 'Завершение';
        default:
          return 'Подготовка';
      }
    }

    function renderSummaryOverviewTable(rows) {
      return `
        <div class="table-responsive mt-3">
          <table class="table source-status-table align-middle mb-0 summary-overview-table">
            <tbody>
              ${rows.map(renderSummaryOverviewRow).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function renderSummaryOverviewRow(row) {
      const tone = row.tone || 'default';
      const valueClass = row.compact ? 'summary-overview-value summary-overview-value-compact' : 'summary-overview-value';
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
        return '<div class="text-secondary small">Нет данных по распределению строк.</div>';
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
                  <td>${escapeHtml(formatNumber(item.availableRows))}</td>
                  <td>${escapeHtml(formatNumber(item.mergedRows))}</td>
                  <td>${escapeHtml(formatPercent(item.mergedPercent))}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function renderSummaryFailedSources(failedSources) {
      if (!failedSources || failedSources.length === 0) {
        return '<div class="text-secondary small">Ошибочных источников нет.</div>';
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
                  <td><span class="${statusBadgeClass(source.status)}">${escapeHtml(translateStatus(source.status))}</span></td>
                  <td>${escapeHtml(formatNumber(source.rowCount || 0))}</td>
                  <td>${escapeHtml(source.errorMessage || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function extractFileName(filePath, fallback) {
      const normalized = String(filePath || '').replace(/\\/g, '/');
      const fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
      return fileName || fallback || '-';
    }

    return {
      render,
    };
  }

  function statusBadgeClass(status) {
    return formatters.statusBadgeClass ? formatters.statusBadgeClass(status) : 'status-badge';
  }

  function artifactStatusClass(status) {
    return formatters.artifactStatusClass ? formatters.artifactStatusClass(status) : 'status-badge';
  }

  function eventEntryClass(severity) {
    return formatters.eventEntryClass ? formatters.eventEntryClass(severity) : 'human-log-entry';
  }

  function translateStatus(status) {
    return formatters.translateStatus ? formatters.translateStatus(status) : (status || '-');
  }

  function translateLaunchSource(sourceKind) {
    if (!sourceKind) {
      return '-';
    }
    return formatters.translateLaunchSource ? formatters.translateLaunchSource(sourceKind) : sourceKind;
  }

  function translateStage(stage) {
    return formatters.translateStage ? formatters.translateStage(stage) : (stage || '-');
  }

  function translateArtifactStatus(status) {
    return formatters.translateArtifactStatus ? formatters.translateArtifactStatus(status) : (status || '-');
  }

  function translateArtifactKind(kind) {
    return formatters.translateArtifactKind ? formatters.translateArtifactKind(kind) : (kind || '-');
  }

  function statusTone(status) {
    return formatters.statusTone ? formatters.statusTone(status) : 'default';
  }

  function formatDateTime(value) {
    return formatters.formatDateTime ? formatters.formatDateTime(value) : (value || '-');
  }

  function formatNumber(value) {
    return formatters.formatNumber ? formatters.formatNumber(value) : String(value ?? '-');
  }

  function formatPercent(value) {
    return formatters.formatPercent ? formatters.formatPercent(value) : String(value ?? '-');
  }

  function formatFileSize(value) {
    return formatters.formatFileSize ? formatters.formatFileSize(value) : String(value ?? '-');
  }

  function formatRawJson(value) {
    return formatters.formatRawJson ? formatters.formatRawJson(value) : String(value ?? '');
  }

  namespace.createRenderer = createRenderer;
})(window);
