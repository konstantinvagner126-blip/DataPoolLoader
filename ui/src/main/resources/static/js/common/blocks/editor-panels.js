(function registerEditorPanels(global) {
  const namespace = global.DataPoolEditorBlocks = global.DataPoolEditorBlocks || {};

  function renderExecutionLogPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const title = options.title || 'Ход выполнения';
    const eventLogId = options.eventLogId || 'eventLog';
    const eventLogClass = options.eventLogClass || 'event-log human-log';
    host.innerHTML = `
      <div class="row g-4">
        <div class="col-12">
          <div class="panel">
            <div class="panel-title">${title}</div>
            <div id="${eventLogId}" class="${eventLogClass}"></div>
          </div>
        </div>
      </div>
    `;
  }

  function renderHistoryCurrentPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const filtersId = options.filtersId || 'runHistoryFilters';
    const historyId = options.historyId || 'runHistory';
    const summaryId = options.summaryId || 'runSummary';
    const historyTitle = options.historyTitle || 'История запусков';
    const currentTitle = options.currentTitle || 'Текущий запуск';
    const emptyText = options.emptyText || 'Запусков пока нет.';
    host.innerHTML = `
      <div class="row g-4 mt-1">
        <div class="col-12 col-xl-4">
          <div class="panel h-100">
            <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
              <div class="panel-title mb-0">${historyTitle}</div>
              <div id="${filtersId}" class="run-history-filters"></div>
            </div>
            <div id="${historyId}" class="run-history-list">
              <div class="text-secondary small">${emptyText}</div>
            </div>
          </div>
        </div>
        <div class="col-12 col-xl-8">
          <div class="panel">
            <div class="panel-title">${currentTitle}</div>
            <div id="${summaryId}" class="small text-secondary">${emptyText}</div>
          </div>
        </div>
      </div>
    `;
  }

  function renderTechnicalDiagnosticsPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const panelId = options.panelId || 'technicalDiagnosticsPanel';
    const collapseId = options.collapseId || 'technicalEventsCollapse';
    const logId = options.logId || 'technicalEventLog';
    const title = options.title || 'Техническая диагностика';
    host.innerHTML = `
      <div id="${panelId}" class="panel mt-4">
        <div class="d-flex align-items-center justify-content-between gap-3">
          <div class="panel-title mb-0">${title}</div>
          <button class="btn btn-outline-secondary btn-sm" type="button" data-bs-toggle="collapse" data-bs-target="#${collapseId}" aria-expanded="false" aria-controls="${collapseId}">
            Показать / скрыть
          </button>
        </div>
        <div class="collapse mt-3" id="${collapseId}">
          <div id="${logId}" class="event-log technical-log"></div>
        </div>
      </div>
    `;
  }

  function renderSummaryPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const title = options.title || 'Итоги запуска';
    const structuredId = options.structuredId || 'summaryStructured';
    const emptyText = options.emptyText || 'Пока нет данных summary.';
    const rawPanelId = options.rawPanelId || null;
    const rawCollapseId = options.rawCollapseId || 'summaryRawCollapse';
    const rawJsonId = options.rawJsonId || 'summaryJson';
    const rawTitle = options.rawTitle || 'Технический raw JSON summary';
    const rawPanelClass = rawPanelId ? ` class="mt-4${options.rawPanelInitiallyHidden ? ' d-none' : ''}"` : '';
    const rawPanelHtml = rawPanelId ? `
      <div id="${rawPanelId}"${rawPanelClass}>
        <div class="d-flex align-items-center justify-content-between gap-3">
          <div class="small text-secondary">${rawTitle}</div>
          <button class="btn btn-outline-secondary btn-sm" type="button" data-bs-toggle="collapse" data-bs-target="#${rawCollapseId}" aria-expanded="false" aria-controls="${rawCollapseId}">
            Показать / скрыть
          </button>
        </div>
        <div class="collapse mt-3" id="${rawCollapseId}">
          <pre id="${rawJsonId}" class="summary-box">${emptyText}</pre>
        </div>
      </div>
    ` : '';

    host.innerHTML = `
      <div class="panel mt-4">
        <div class="panel-title">${title}</div>
        <div id="${structuredId}" class="summary-structured">
          <div class="text-secondary">${emptyText}</div>
        </div>
        ${rawPanelHtml}
      </div>
    `;
  }

  function renderInfoPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const panelId = options.panelId || null;
    const panelClass = options.panelClass || 'panel mt-4';
    const title = options.title || '';
    const contentId = options.contentId || 'panelContent';
    const contentClass = options.contentClass || 'mt-3 text-secondary small';
    const emptyText = options.emptyText || '';
    host.innerHTML = `
      <div${panelId ? ` id="${panelId}"` : ''} class="${panelClass}">
        <div class="panel-title">${title}</div>
        <div id="${contentId}" class="${contentClass}">${emptyText}</div>
      </div>
    `;
  }

  namespace.renderExecutionLogPanel = renderExecutionLogPanel;
  namespace.renderHistoryCurrentPanel = renderHistoryCurrentPanel;
  namespace.renderTechnicalDiagnosticsPanel = renderTechnicalDiagnosticsPanel;
  namespace.renderSummaryPanel = renderSummaryPanel;
  namespace.renderInfoPanel = renderInfoPanel;
})(window);
