(function registerDbModulesRunRenderer(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};

  function createRunRenderer(ctx, runtimeRenderer) {
    const { refs, state } = ctx;
    const { escapeHtml } = ctx.common;
    const formatters = modules.formatters;

    function renderModuleRuns(onSelectRun) {
      if (!state.currentRuns.length) {
        refs.dbRunsList.innerHTML = '<div class="text-secondary small">Запусков пока нет.</div>';
        return;
      }

      refs.dbRunsList.innerHTML = state.currentRuns.slice(0, 20).map(run => `
        <button
          type="button"
          class="run-history-item ${run.runId === state.selectedRunId ? 'run-history-item-active' : ''}"
          data-run-id="${escapeHtml(run.runId)}"
        >
          <div class="run-history-head">
            <span class="run-history-title">${escapeHtml(run.moduleTitle || run.moduleCode)}</span>
            <span class="${formatters.statusBadgeClass(run.status)}">${escapeHtml(formatters.translateStatus(run.status))}</span>
          </div>
          <div class="run-history-meta">${escapeHtml(formatters.formatDateTime(run.requestedAt))}</div>
          <div class="run-history-meta">Источник запуска: ${escapeHtml(formatters.translateLaunchSource(run.launchSourceKind))}</div>
          <div class="run-history-meta">
            merged: ${escapeHtml(formatters.formatNumber(run.mergedRowCount || 0))}
            · target: ${escapeHtml(formatters.translateStatus(run.targetStatus))}
          </div>
          <div class="run-history-meta">
            success: ${escapeHtml(String(run.successfulSourceCount))}
            · errors: ${escapeHtml(String(run.failedSourceCount))}
            · skipped: ${escapeHtml(String(run.skippedSourceCount))}
          </div>
        </button>
      `).join('');

      refs.dbRunsList.querySelectorAll('[data-run-id]').forEach(button => {
        button.addEventListener('click', async () => {
          if (!state.selectedModuleId) return;
          state.selectedRunId = button.dataset.runId;
          await onSelectRun(state.selectedModuleId, state.selectedRunId);
        });
      });
    }

    function renderSelectedRunDetails() {
      if (!state.selectedRunDetails?.run) {
        refs.dbRunDetails.classList.add('d-none');
        refs.dbRunDetailsEmpty.classList.remove('d-none');
        refs.dbRunSummary.innerHTML = '';
        refs.dbRunStructuredSummary.innerHTML = '<div class="text-secondary">Summary еще не сформирован.</div>';
        refs.dbRunSummaryJson.textContent = 'Summary еще не сформирован.';
        refs.dbRunSourceResults.innerHTML = '<div class="text-secondary small">Данные по источникам пока недоступны.</div>';
        refs.dbRunEventTimeline.innerHTML = '<div class="text-secondary small">События запуска пока недоступны.</div>';
        refs.dbRunArtifacts.innerHTML = '<div class="text-secondary small">Артефакты запуска пока недоступны.</div>';
        return;
      }

      const run = state.selectedRunDetails.run;
      refs.dbRunDetails.classList.remove('d-none');
      refs.dbRunDetailsEmpty.classList.add('d-none');
      refs.dbRunSummary.innerHTML = renderRunSummary(run);
      refs.dbRunStructuredSummary.innerHTML = renderStructuredSummary(run, state.selectedRunDetails.summaryJson);
      refs.dbRunSummaryJson.textContent = formatters.formatRawJson(state.selectedRunDetails.summaryJson);
      refs.dbRunSourceResults.innerHTML = renderSourceResults(state.selectedRunDetails.sourceResults || []);
      refs.dbRunEventTimeline.innerHTML = renderEventTimeline(state.selectedRunDetails.events || []);
      refs.dbRunArtifacts.innerHTML = renderArtifacts(state.selectedRunDetails.artifacts || []);
    }

    function renderRunSummary(run) {
      const warningsCount = Number(run.failedSourceCount || 0) + Number(run.skippedSourceCount || 0);
      return `
        <div class="run-summary-header">
          <div>
            <div class="run-summary-title">${escapeHtml(run.moduleTitle || run.moduleCode)}</div>
            <div class="run-summary-subtitle">
              Run ID: <code>${escapeHtml(run.runId)}</code>
              · Snapshot: <code>${escapeHtml(run.executionSnapshotId)}</code>
            </div>
          </div>
          <span class="${formatters.statusBadgeClass(run.status)}">${escapeHtml(formatters.translateStatus(run.status))}</span>
        </div>

        <div class="run-summary-metrics">
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Строк в merged</div>
            <div class="run-summary-metric-value">${escapeHtml(formatters.formatNumber(run.mergedRowCount || 0))}</div>
          </div>
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Успешные sources</div>
            <div class="run-summary-metric-value">${escapeHtml(formatters.formatNumber(run.successfulSourceCount || 0))}</div>
          </div>
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Warnings и ошибки</div>
            <div class="run-summary-metric-value">${escapeHtml(formatters.formatNumber(warningsCount))}</div>
          </div>
          <div class="run-summary-metric">
            <div class="run-summary-metric-label">Загружено в target</div>
            <div class="run-summary-metric-value">${escapeHtml(formatters.formatNumber(run.targetRowsLoaded || 0))}</div>
          </div>
        </div>

        <div class="run-summary-list">
          ${renderSummaryKeyValue('Запрошен', formatters.formatDateTime(run.requestedAt))}
          ${renderSummaryKeyValue('Старт', formatters.formatDateTime(run.startedAt))}
          ${renderSummaryKeyValue('Завершение', run.finishedAt ? formatters.formatDateTime(run.finishedAt) : 'еще выполняется')}
          ${renderSummaryKeyValue('Источник запуска', formatters.translateLaunchSource(run.launchSourceKind))}
          ${renderSummaryKeyValue('Target', run.targetTableName || '-')}
          ${renderSummaryKeyValue('Статус target', formatters.translateStatus(run.targetStatus))}
          ${renderSummaryKeyValue('Output', run.outputDir || '-')}
          ${renderSummaryKeyValue('Ошибка', run.errorMessage || '-')}
        </div>
      `;
    }

    function renderStructuredSummary(run, summaryJson) {
      if (!summaryJson || summaryJson === '{}') {
        return '<div class="text-secondary">Summary еще не сформирован.</div>';
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
            { label: 'Старт', value: formatters.formatDateTime(summary.startedAt) },
            { label: 'Завершение', value: formatters.formatDateTime(summary.finishedAt) },
            { label: 'Merge mode', value: summary.mergeMode || '-', tone: 'important' },
            { label: 'Файл merged', value: summary.mergedFile || '-' },
            { label: 'Строк в merged', value: formatters.formatNumber(summary.mergedRowCount), tone: 'important' },
            { label: 'Max merged rows', value: summary.maxMergedRows == null ? 'без ограничения' : formatters.formatNumber(summary.maxMergedRows) },
            {
              label: 'Target status',
              value: `<span class="${formatters.statusBadgeClass(targetLoad.status)}">${escapeHtml(formatters.translateStatus(targetLoad.status))}</span>`,
              html: true,
              tone: formatters.statusTone(targetLoad.status),
            },
            { label: 'Target table', value: targetLoad.table || '-', tone: targetLoad.table ? 'important' : 'default' },
            { label: 'Загружено', value: formatters.formatNumber(targetLoad.rowCount || 0), tone: 'important' },
            { label: 'Успешных sources', value: formatters.formatNumber(successfulSources.length), tone: successfulSources.length > 0 ? 'success' : 'default' },
            { label: 'Ошибочных sources', value: formatters.formatNumber(failedSources.length), tone: failedSources.length > 0 ? 'failed' : 'success' },
            { label: 'Output', value: run.outputDir || '-' },
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
                <th>Exported</th>
                <th>Merged</th>
                <th>Старт</th>
                <th>Финиш</th>
                <th>Ошибка</th>
              </tr>
            </thead>
            <tbody>
              ${sourceResults.map(item => `
                <tr>
                  <td><strong>${escapeHtml(item.sourceName)}</strong></td>
                  <td><span class="${formatters.statusBadgeClass(item.status)}">${escapeHtml(formatters.translateStatus(item.status))}</span></td>
                  <td>${escapeHtml(formatters.formatNumber(item.exportedRowCount || 0))}</td>
                  <td>${escapeHtml(formatters.formatNumber(item.mergedRowCount || 0))}</td>
                  <td>${escapeHtml(formatters.formatDateTime(item.startedAt))}</td>
                  <td>${escapeHtml(formatters.formatDateTime(item.finishedAt))}</td>
                  <td>${escapeHtml(item.errorMessage || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    function renderEventTimeline(events) {
      if (!events.length) {
        return '<div class="text-secondary small">События запуска пока недоступны.</div>';
      }

      return events.slice(-60).map(event => `
        <div class="${formatters.eventEntryClass(event.severity)}">
          <div class="human-log-time">
            ${escapeHtml(formatters.formatDateTime(event.createdAt))}
            · ${escapeHtml(formatters.translateStage(event.stage))}
            ${event.sourceName ? ` · ${escapeHtml(event.sourceName)}` : ''}
          </div>
          <div class="human-log-text">${escapeHtml(event.message)}</div>
        </div>
      `).join('');
    }

    function renderArtifacts(artifacts) {
      if (!artifacts.length) {
        return '<div class="text-secondary small">Артефакты запуска пока недоступны.</div>';
      }

      return `
        <div class="table-responsive">
          <table class="table source-status-table align-middle mb-0">
            <thead>
              <tr>
                <th>Артефакт</th>
                <th>Ключ</th>
                <th>Статус</th>
                <th>Размер</th>
                <th>Путь</th>
              </tr>
            </thead>
            <tbody>
              ${artifacts.map(item => `
                <tr>
                  <td><strong>${escapeHtml(item.artifactKind)}</strong></td>
                  <td>${escapeHtml(item.artifactKey)}</td>
                  <td><span class="${formatters.artifactStatusClass(item.storageStatus)}">${escapeHtml(formatters.translateArtifactStatus(item.storageStatus))}</span></td>
                  <td>${escapeHtml(formatters.formatFileSize(item.fileSizeBytes))}</td>
                  <td><code>${escapeHtml(item.filePath)}</code></td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
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
                  <td>${escapeHtml(formatters.formatNumber(item.availableRows))}</td>
                  <td>${escapeHtml(formatters.formatNumber(item.mergedRows))}</td>
                  <td>${escapeHtml(formatters.formatPercent(item.mergedPercent))}</td>
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
                  <td><span class="${formatters.statusBadgeClass(source.status)}">${escapeHtml(formatters.translateStatus(source.status))}</span></td>
                  <td>${escapeHtml(formatters.formatNumber(source.rowCount || 0))}</td>
                  <td>${escapeHtml(source.errorMessage || '-')}</td>
                </tr>
              `).join('')}
            </tbody>
          </table>
        </div>
      `;
    }

    return {
      renderModuleRuns,
      renderSelectedRunDetails,
    };
  }

  modules.createRunRenderer = createRunRenderer;
})(window);
