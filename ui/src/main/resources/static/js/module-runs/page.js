(function initModuleRunsPage(global) {
  const common = global.DataPoolCommon || {};
  const editorBlocksNamespace = global.DataPoolEditorBlocks || {};
  const moduleRunsNamespace = global.DataPoolModuleRuns || {};
  const { fetchJson } = common;
  const {
    renderExecutionLogPanel,
    renderHistoryCurrentPanel,
    renderTechnicalDiagnosticsPanel,
    renderSummaryPanel,
    renderInfoPanel,
  } = editorBlocksNamespace;

  const params = new URLSearchParams(global.location.search);
  const storageMode = String(params.get('storage') || '').toLowerCase();
  const moduleId = params.get('module');
  const includeHidden = params.get('includeHidden') === 'true' || params.get('includeHidden') === '1';
  const FILES_MODE = 'files';
  const DATABASE_MODE = 'database';

  const refs = {
    eyebrow: document.getElementById('runsPageEyebrow'),
    subtitle: document.getElementById('runsPageSubtitle'),
    moduleTitle: document.getElementById('runsPageModuleTitle'),
    moduleMeta: document.getElementById('runsPageModuleMeta'),
    backToModuleButton: document.getElementById('backToModuleButton'),
    alert: document.getElementById('runsPageAlert'),
    runHistoryControls: document.getElementById('runHistoryControls'),
    runHistoryFilters: document.getElementById('runHistoryFilters'),
    runHistory: document.getElementById('runHistory'),
    runSummary: document.getElementById('runSummary'),
    eventLog: document.getElementById('eventLog'),
    technicalDiagnosticsPanel: document.getElementById('technicalDiagnosticsPanel'),
    technicalEventLog: document.getElementById('technicalEventLog'),
    runStructuredSummary: document.getElementById('runStructuredSummary'),
    runSummaryRawPanel: document.getElementById('runSummaryRawPanel'),
    runSummaryJson: document.getElementById('runSummaryJson'),
    runSourceResults: document.getElementById('runSourceResults'),
    runArtifacts: document.getElementById('runArtifacts'),
  };

  const state = {
    storageMode,
    moduleId,
    currentRuns: [],
    selectedRunId: null,
    selectedRunDetails: null,
    runHistoryFilter: 'ALL',
    historyLimit: 20,
    searchQuery: '',
    uiSettings: {},
  };

  let renderer = null;
  let dbPollingTimer = null;
  let dbPollTick = 0;
  let fileSocket = null;

  bootstrapPage();

  async function bootstrapPage() {
    renderPageSkeleton();

    if (!moduleId || (storageMode !== FILES_MODE && storageMode !== DATABASE_MODE)) {
      showAlert('Не указан режим хранения или код модуля. Открой этот экран из карточки нужного модуля.');
      refs.backToModuleButton.href = '/';
      return;
    }

    renderer = moduleRunsNamespace.createRenderer?.({
      common,
      refs,
      state,
      historyLimit: 20,
      eventTimelineLimit: 60,
    });

    try {
      const session = await loadRunSession(storageMode, moduleId);
      applyHeader({
        title: session.moduleTitle,
        meta: session.moduleMeta,
        backHref: storageMode === DATABASE_MODE ? buildDbBackHref() : `/modules?module=${encodeURIComponent(moduleId)}`,
      });

      await reloadRuns();

      if (storageMode === FILES_MODE) {
        connectFileSocket();
      } else {
        dbPollingTimer = global.setInterval(() => {
          pollDatabaseRuns().catch(handlePageError);
        }, 3000);
      }
    } catch (error) {
      handlePageError(error);
    }
  }

  function renderPageSkeleton() {
    refs.eyebrow.textContent = storageMode === DATABASE_MODE
      ? 'История запусков · База данных'
      : 'История запусков · Файлы';
    refs.subtitle.textContent = storageMode === DATABASE_MODE
      ? 'Полная история DB-запусков, подробный ход выполнения, результаты по источникам и итоговые артефакты.'
      : 'Полная история файловых запусков, подробный ход выполнения, результаты по источникам и итоговые артефакты.';

    renderExecutionLogPanel?.(document.getElementById('executionLogPanelHost'), {
      eventLogId: 'eventLog',
    });
    renderHistoryCurrentPanel?.(document.getElementById('historyCurrentPanelHost'), {
      controlsId: 'runHistoryControls',
      filtersId: 'runHistoryFilters',
      historyId: 'runHistory',
      summaryId: 'runSummary',
      currentTitle: 'Выбранный запуск',
    });
    renderTechnicalDiagnosticsPanel?.(document.getElementById('technicalDiagnosticsPanelHost'), {
      panelId: 'technicalDiagnosticsPanel',
      collapseId: 'technicalEventsCollapse',
      logId: 'technicalEventLog',
    });
    renderSummaryPanel?.(document.getElementById('summaryPanelHost'), {
      structuredId: 'runStructuredSummary',
      rawPanelId: 'runSummaryRawPanel',
      rawCollapseId: 'runSummaryJsonCollapse',
      rawJsonId: 'runSummaryJson',
      emptyText: 'Итоги запуска еще не сформированы.',
    });
    renderInfoPanel?.(document.getElementById('sourceResultsPanelHost'), {
      panelId: 'runSourceResultsPanel',
      title: 'Результаты по источникам',
      contentId: 'runSourceResults',
      contentClass: 'mt-3 text-secondary small',
      emptyText: 'Данные по источникам пока недоступны.',
    });
    renderInfoPanel?.(document.getElementById('artifactsPanelHost'), {
      panelId: 'runArtifactsPanel',
      title: 'Результаты запуска',
      contentId: 'runArtifacts',
      contentClass: 'mt-3 text-secondary small',
      emptyText: 'Результаты запуска пока недоступны.',
    });

    refs.runHistoryFilters = document.getElementById('runHistoryFilters');
    refs.runHistoryControls = document.getElementById('runHistoryControls');
    refs.runHistory = document.getElementById('runHistory');
    refs.runSummary = document.getElementById('runSummary');
    refs.eventLog = document.getElementById('eventLog');
    refs.technicalDiagnosticsPanel = document.getElementById('technicalDiagnosticsPanel');
    refs.technicalEventLog = document.getElementById('technicalEventLog');
    refs.runStructuredSummary = document.getElementById('runStructuredSummary');
    refs.runSummaryRawPanel = document.getElementById('runSummaryRawPanel');
    refs.runSummaryJson = document.getElementById('runSummaryJson');
    refs.runSourceResults = document.getElementById('runSourceResults');
    refs.runArtifacts = document.getElementById('runArtifacts');
  }

  async function reloadRuns() {
    const payload = await loadRunHistory(storageMode, moduleId);
    state.currentRuns = Array.isArray(payload.runs) ? payload.runs : [];
    state.uiSettings = payload.uiSettings || {};

    if (!state.currentRuns.length) {
      state.selectedRunId = null;
      state.selectedRunDetails = null;
      renderHistoryPage();
      return;
    }

    if (!state.selectedRunId || !state.currentRuns.some(run => run.runId === state.selectedRunId)) {
      state.selectedRunId = payload.activeRunId || state.currentRuns[0].runId;
    }

    await loadRunDetailsForSelection(state.selectedRunId);
  }

  async function loadRunDetailsForSelection(runId) {
    if (!runId) {
      state.selectedRunId = null;
      state.selectedRunDetails = null;
      renderHistoryPage();
      return;
    }
    state.selectedRunId = runId;
    state.selectedRunDetails = await loadRunDetails(storageMode, moduleId, runId);
    renderHistoryPage();
  }

  function renderHistoryPage() {
    renderer?.render({
      onSelectRun: loadRunDetailsForSelection,
      onHistoryLimitChange: async nextLimit => {
        state.historyLimit = nextLimit;
        await reloadRuns();
      },
    });
  }

  function connectFileSocket() {
    const protocol = global.location.protocol === 'https:' ? 'wss' : 'ws';
    fileSocket = new global.WebSocket(`${protocol}://${global.location.host}/ws`);
    fileSocket.onmessage = () => {
      reloadRuns().catch(handlePageError);
    };
  }

  function hasRunningRun() {
    return state.currentRuns.some(run => run.status === 'RUNNING');
  }

  async function pollDatabaseRuns() {
    if (!hasRunningRun()) {
      return;
    }
    dbPollTick += 1;
    const selectedRun = state.currentRuns.find(run => run.runId === state.selectedRunId) || null;
    if (selectedRun?.status === 'RUNNING' && state.selectedRunId) {
      const freshDetails = await loadRunDetails(storageMode, moduleId, state.selectedRunId);
      state.selectedRunDetails = freshDetails;
      patchCurrentRunSummary(freshDetails.run);
      renderHistoryPage();
      if (String(freshDetails.run.status || '').toUpperCase() !== 'RUNNING') {
        await reloadRuns();
        return;
      }
    }
    if (!selectedRun || selectedRun.status !== 'RUNNING' || dbPollTick % 3 === 0) {
      await reloadRuns();
    }
  }

  function patchCurrentRunSummary(runSummary) {
    const index = state.currentRuns.findIndex(run => run.runId === runSummary.runId);
    if (index < 0) {
      return;
    }
    state.currentRuns.splice(index, 1, {
      ...state.currentRuns[index],
      ...runSummary,
    });
  }

  function buildDbBackHref() {
    const query = new URLSearchParams({ module: moduleId });
    if (includeHidden) {
      query.set('includeHidden', 'true');
    }
    return `/db-modules?${query.toString()}`;
  }

  function applyHeader({ title, meta, backHref }) {
    refs.moduleTitle.textContent = title;
    refs.moduleMeta.textContent = meta;
    refs.backToModuleButton.href = backHref;
  }

  function showAlert(message) {
    refs.alert.classList.remove('d-none');
    refs.alert.textContent = message;
  }

  async function loadRunSession(mode, currentModuleId) {
    return fetchJson(
      `/api/module-runs/${encodeURIComponent(mode)}/${encodeURIComponent(currentModuleId)}`,
      {},
      'Не удалось загрузить данные модуля для экрана истории запусков.',
    );
  }

  async function loadRunHistory(mode, currentModuleId) {
    const query = new URLSearchParams();
    query.set('limit', String(state.historyLimit || 20));
    return fetchJson(
      `/api/module-runs/${encodeURIComponent(mode)}/${encodeURIComponent(currentModuleId)}/runs?${query.toString()}`,
      {},
      'Не удалось загрузить историю запусков модуля.',
    );
  }

  async function loadRunDetails(mode, currentModuleId, runId) {
    return fetchJson(
      `/api/module-runs/${encodeURIComponent(mode)}/${encodeURIComponent(currentModuleId)}/runs/${encodeURIComponent(runId)}`,
      {},
      'Не удалось загрузить детали запуска.',
    );
  }

  function handlePageError(error) {
    console.error(error);
    showAlert(error?.message || 'Не удалось загрузить историю и результаты запуска.');
  }

  global.addEventListener('pagehide', () => {
    if (dbPollingTimer) {
      global.clearInterval(dbPollingTimer);
    }
    if (fileSocket) {
      fileSocket.close();
    }
  });
})(window);
