(function initDbSyncPage(global) {
  const { fetchJson, postJson, escapeHtml } = global.DataPoolCommon || {};
  const shared = global.DataPoolDb?.shared || {};

  const dbContextAlert = document.getElementById('dbContextAlert');
  const dbContextAlertTitle = document.getElementById('dbContextAlertTitle');
  const dbContextAlertText = document.getElementById('dbContextAlertText');
  const syncAllButton = document.getElementById('syncAllButton');
  const syncOneButton = document.getElementById('syncOneButton');
  const syncOneModuleInput = document.getElementById('syncOneModuleInput');
  const syncOneModuleCode = document.getElementById('syncOneModuleCode');
  const syncOneConfirmButton = document.getElementById('syncOneConfirmButton');
  const syncRunsLimit = document.getElementById('syncRunsLimit');
  const syncRunsHistory = document.getElementById('syncRunsHistory');
  const syncResultSummary = document.getElementById('syncResultSummary');
  const syncResultItems = document.getElementById('syncResultItems');

  let runtimeContext = null;
  let syncState = null;
  let syncRuns = [];
  let selectedSyncRunId = null;
  let selectedSyncRunDetails = null;
  let syncHistoryLimit = Number(syncRunsLimit?.value || 20);

  function translateSyncStatus(status) {
    switch (String(status || '').toUpperCase()) {
      case 'SUCCESS':
        return 'Успешно';
      case 'FAILED':
        return 'Ошибка';
      case 'RUNNING':
        return 'Выполняется';
      case 'PARTIAL_SUCCESS':
        return 'Частично успешно';
      default:
        return status || '-';
    }
  }

  function translateSyncScope(scope) {
    switch (String(scope || '').toUpperCase()) {
      case 'ALL':
        return 'Все модули';
      case 'ONE':
        return 'Один модуль';
      default:
        return scope || '-';
    }
  }

  function translateSyncAction(action) {
    switch (String(action || '').toUpperCase()) {
      case 'CREATED':
        return 'Создан';
      case 'UPDATED':
        return 'Обновлён';
      case 'SKIPPED':
        return 'Пропущен';
      case 'SKIPPED_NO_CHANGES':
        return 'Пропущен без изменений';
      case 'SKIPPED_CODE_CONFLICT':
        return 'Пропущен из-за конфликта кода';
      case 'FAILED':
        return 'Ошибка';
      default:
        return action || '-';
    }
  }

  function statusBadgeClass(status) {
    switch (String(status || '').toUpperCase()) {
      case 'SUCCESS':
        return 'bg-success';
      case 'FAILED':
        return 'bg-danger';
      case 'RUNNING':
        return 'bg-primary';
      case 'PARTIAL_SUCCESS':
        return 'bg-warning text-dark';
      default:
        return 'bg-secondary';
    }
  }

  function actionBadgeClass(action) {
    switch (String(action || '').toUpperCase()) {
      case 'CREATED':
        return 'bg-success';
      case 'UPDATED':
        return 'bg-primary';
      case 'SKIPPED':
      case 'SKIPPED_NO_CHANGES':
      case 'SKIPPED_CODE_CONFLICT':
        return 'bg-secondary';
      case 'FAILED':
        return 'bg-danger';
      default:
        return 'bg-secondary';
    }
  }

  syncAllButton.addEventListener('click', async () => {
    if (!confirm('Синхронизировать все файловые модули в базу данных?')) return;
    await syncAll();
  });

  syncOneButton.addEventListener('click', () => {
    syncOneModuleInput.classList.toggle('d-none');
    if (!syncOneModuleInput.classList.contains('d-none')) {
      syncOneModuleCode.focus();
    }
  });

  syncOneConfirmButton.addEventListener('click', async () => {
    const moduleCode = syncOneModuleCode.value?.trim();
    if (!moduleCode) {
      alert('Введите код модуля.');
      return;
    }
    await syncOne(moduleCode);
  });

  syncOneModuleCode.addEventListener('keydown', e => {
    if (e.key === 'Enter') {
      syncOneConfirmButton.click();
    }
  });

  syncOneModuleCode.addEventListener('input', () => {
    renderRuntimeContext();
  });

  syncRunsLimit?.addEventListener('change', async () => {
    syncHistoryLimit = Number(syncRunsLimit.value || 20);
    await refreshPageState(selectedSyncRunId);
  });

  async function loadRuntimeContext() {
    try {
      runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить состояние режима UI.');
    } catch (e) {
      console.error(e);
    }
  }

  async function loadSyncState() {
    try {
      syncState = await fetchJson('/api/db/sync/state', {}, 'Не удалось загрузить состояние импорта.');
    } catch (e) {
      console.error(e);
      syncState = null;
    }
  }

  async function loadSyncRuns() {
    const query = new URLSearchParams({ limit: String(syncHistoryLimit || 20) });
    const payload = await fetchJson(`/api/db/sync/runs?${query.toString()}`, {}, 'Не удалось загрузить историю импортов.');
    syncRuns = Array.isArray(payload?.runs) ? payload.runs : [];
  }

  async function loadSyncRunDetails(syncRunId) {
    return fetchJson(
      `/api/db/sync/runs/${encodeURIComponent(syncRunId)}`,
      {},
      'Не удалось загрузить детали импорта.',
    );
  }

  function renderRuntimeContext() {
    const maintenanceMode = syncState?.maintenanceMode === true;
    const activeSingleSyncs = shared.activeSingleSyncs(syncState);

    if (!shared.isDatabaseMode(runtimeContext)) {
      showAlert('Режим базы данных недоступен', runtimeContext?.fallbackReason || 'Для импорта нужен активный режим «База данных».');
      setSyncActionsState({ canSyncAll: false, canSyncOne: false, canConfirmSyncOne: false });
      return;
    }

    if (maintenanceMode) {
      const activeSync = syncState.activeFullSync;
      const actorName = activeSync?.startedByActorDisplayName || activeSync?.startedByActorId;
      const startedAt = activeSync?.startedAt
        ? new Date(activeSync.startedAt).toLocaleString()
        : null;
      showAlert(
        'Идет массовый импорт модулей',
        [
          syncState.message,
          actorName ? `Инициатор: ${actorName}.` : null,
          startedAt ? `Запуск: ${startedAt}.` : null,
        ].filter(Boolean).join(' '),
      );
      setSyncActionsState({ canSyncAll: false, canSyncOne: false, canConfirmSyncOne: false });
      return;
    }

    if (activeSingleSyncs.length > 0) {
      showAlert(
        'Идут точечные импорты модулей',
        activeSingleSyncs.map(shared.describeActiveSingleSync).join(' '),
        'info',
      );
      setSyncActionsState({
        canSyncAll: false,
        canSyncOne: true,
        canConfirmSyncOne: !isSelectedModuleSyncing(),
      });
      return;
    }

    dbContextAlert.classList.add('d-none');
    setSyncActionsState({ canSyncAll: true, canSyncOne: true, canConfirmSyncOne: true });
  }

  function showAlert(title, message, kind = 'warning') {
    dbContextAlert.className = `alert alert-${kind} mb-4`;
    dbContextAlert.classList.remove('d-none');
    dbContextAlertTitle.textContent = title;
    dbContextAlertText.textContent = message;
  }

  function setSyncActionsState({ canSyncAll, canSyncOne, canConfirmSyncOne }) {
    syncAllButton.disabled = !canSyncAll;
    syncOneButton.disabled = !canSyncOne;
    syncOneConfirmButton.disabled = !canConfirmSyncOne;
    if (!canSyncOne) {
      syncOneModuleInput.classList.add('d-none');
    }
  }

  function isSelectedModuleSyncing() {
    const moduleCode = syncOneModuleCode.value?.trim();
    if (!moduleCode) {
      return false;
    }
    return shared.activeSingleSyncFor(syncState, moduleCode) != null;
  }

  async function refreshPageState(preferredSyncRunId = null) {
    await loadRuntimeContext();
    await loadSyncState();

    if (!shared.isDatabaseMode(runtimeContext)) {
      syncRuns = [];
      selectedSyncRunId = null;
      selectedSyncRunDetails = null;
      renderRuntimeContext();
      renderSyncHistory();
      renderSyncDetails();
      return;
    }

    try {
      await loadSyncRuns();
      await selectSyncRun(preferredSyncRunId);
    } catch (e) {
      console.error(e);
      syncRuns = [];
      selectedSyncRunId = null;
      selectedSyncRunDetails = null;
    }

    renderRuntimeContext();
    renderSyncHistory();
    renderSyncDetails();
  }

  async function selectSyncRun(preferredSyncRunId = null) {
    if (!syncRuns.length) {
      selectedSyncRunId = null;
      selectedSyncRunDetails = null;
      return;
    }

    const activePreferredSyncRunId = preferredSyncRunId
      || syncState?.activeFullSync?.syncRunId
      || syncState?.activeSingleSyncs?.[0]?.syncRunId
      || null;

    if (activePreferredSyncRunId && syncRuns.some(run => run.syncRunId === activePreferredSyncRunId)) {
      selectedSyncRunId = activePreferredSyncRunId;
    } else if (!selectedSyncRunId || !syncRuns.some(run => run.syncRunId === selectedSyncRunId)) {
      selectedSyncRunId = syncRuns[0].syncRunId;
    }

    if (!selectedSyncRunId) {
      selectedSyncRunDetails = null;
      return;
    }

    selectedSyncRunDetails = await loadSyncRunDetails(selectedSyncRunId);
  }

  async function syncAll() {
    syncAllButton.disabled = true;
    syncAllButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Синхронизация...';

    try {
      const result = await postJson('/api/db/sync/all', {}, 'Не удалось синхронизировать модули.');
      await refreshPageState(result.syncRunId);
    } catch (e) {
      alert('Ошибка синхронизации: ' + (e.message || e));
    } finally {
      syncAllButton.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-arrow-down-up" viewBox="0 0 16 16">
          <path fill-rule="evenodd" d="M11.5 15a.5.5 0 0 0 .5-.5V2.707l3.146 3.147a.5.5 0 0 0 .708-.708l-4-4a.5.5 0 0 0-.708 0l-4 4a.5.5 0 1 0 .708.708L11 2.707V14.5a.5.5 0 0 0 .5.5zm-7-14a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L4 13.293V1.5a.5.5 0 0 1 .5-.5z"/>
        </svg>
        Синхронизировать все модули
      `;
      renderRuntimeContext();
    }
  }

  async function syncOne(moduleCode) {
    const activeSync = shared.activeSingleSyncFor(syncState, moduleCode);
    if (activeSync) {
      showAlert(
        'Импорт уже выполняется',
        shared.describeActiveSingleSync(activeSync),
        'info',
      );
      setSyncActionsState({
        canSyncAll: false,
        canSyncOne: true,
        canConfirmSyncOne: false,
      });
      return;
    }

    syncOneConfirmButton.disabled = true;
    syncOneConfirmButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Синхронизация...';

    try {
      const result = await postJson('/api/db/sync/one', { moduleCode }, 'Не удалось синхронизировать модуль.');
      await refreshPageState(result.syncRunId);
    } catch (e) {
      alert('Ошибка синхронизации: ' + (e.message || e));
    } finally {
      syncOneConfirmButton.textContent = 'Синхронизировать';
      renderRuntimeContext();
    }
  }

  function renderSyncHistory() {
    if (!shared.isDatabaseMode(runtimeContext)) {
      syncRunsHistory.innerHTML = '<div class="text-secondary small">История импорта станет доступна после переключения в режим «База данных».</div>';
      return;
    }

    if (!syncRuns.length) {
      syncRunsHistory.innerHTML = '<div class="text-secondary small">Импорты пока не запускались.</div>';
      return;
    }

    syncRunsHistory.innerHTML = syncRuns.map(run => `
      <button
        type="button"
        class="run-history-item ${run.syncRunId === selectedSyncRunId ? 'run-history-item-active' : ''}"
        data-sync-run-id="${escapeHtml(run.syncRunId)}"
      >
        <div class="run-history-head">
          <span class="run-history-title">${escapeHtml(syncRunTitle(run))}</span>
          <span class="badge ${statusBadgeClass(run.status)}">${escapeHtml(translateSyncStatus(run.status))}</span>
        </div>
        <div class="run-history-meta">${escapeHtml(formatDateTime(run.startedAt))}</div>
        <div class="run-history-meta">
          Обработано: ${escapeHtml(String(run.totalProcessed || 0))}
          · создано: ${escapeHtml(String(run.totalCreated || 0))}
          · пропущено: ${escapeHtml(String(run.totalSkipped || 0))}
          · ошибок: ${escapeHtml(String(run.totalFailed || 0))}
        </div>
        <div class="run-history-meta">
          ${escapeHtml(syncRunMeta(run))}
        </div>
      </button>
    `).join('');

    syncRunsHistory.querySelectorAll('[data-sync-run-id]').forEach(button => {
      button.addEventListener('click', async () => {
        await refreshPageState(button.dataset.syncRunId);
      });
    });
  }

  function renderSyncDetails() {
    if (!selectedSyncRunDetails?.run) {
      syncResultSummary.innerHTML = '<div class="text-secondary small">Выбери запуск из истории слева, чтобы посмотреть детали.</div>';
      syncResultItems.innerHTML = '';
      return;
    }

    const run = selectedSyncRunDetails.run;
    syncResultSummary.innerHTML = `
      <div class="d-flex flex-wrap gap-3 mb-3">
        <span class="badge ${statusBadgeClass(run.status)}">${escapeHtml(translateSyncStatus(run.status))}</span>
        <span>Область: <strong>${escapeHtml(translateSyncScope(run.scope))}</strong></span>
        <span>Обработано: <strong>${escapeHtml(String(run.totalProcessed || 0))}</strong></span>
        <span>Создано: <strong>${escapeHtml(String(run.totalCreated || 0))}</strong></span>
        <span>Обновлено: <strong>${escapeHtml(String(run.totalUpdated || 0))}</strong></span>
        <span>Пропущено: <strong>${escapeHtml(String(run.totalSkipped || 0))}</strong></span>
        <span>С ошибкой: <strong>${escapeHtml(String(run.totalFailed || 0))}</strong></span>
      </div>
      <div class="run-summary-list">
        ${renderKeyValue('Запуск', syncRunTitle(run))}
        ${renderKeyValue('Инициатор', run.startedByActorDisplayName || run.startedByActorId || '-')}
        ${renderKeyValue('Старт', formatDateTime(run.startedAt))}
        ${renderKeyValue('Завершение', run.finishedAt ? formatDateTime(run.finishedAt) : 'еще выполняется')}
        ${renderKeyValue('Идентификатор', run.syncRunId)}
      </div>
    `;

    const items = Array.isArray(selectedSyncRunDetails.items) ? selectedSyncRunDetails.items : [];
    if (!items.length) {
      syncResultItems.innerHTML = '<div class="text-secondary small">Детали по модулям появятся после завершения импорта.</div>';
      return;
    }

    syncResultItems.innerHTML = items.map(item => `
      <div class="card mb-3">
        <div class="card-body py-3 px-3">
          <div class="d-flex flex-wrap align-items-center gap-2">
            <code class="fw-semibold">${escapeHtml(item.moduleCode)}</code>
            <span class="badge ${actionBadgeClass(item.action)}">${escapeHtml(translateSyncAction(item.action))}</span>
            <span class="badge ${statusBadgeClass(item.status)}">${escapeHtml(translateSyncStatus(item.status))}</span>
            ${item.resultRevisionId ? `<span class="small text-secondary">Ревизия: ${escapeHtml(item.resultRevisionId)}</span>` : ''}
          </div>
          ${item.errorMessage ? `<div class="small text-danger mt-2">${escapeHtml(item.errorMessage)}</div>` : ''}
          ${renderSyncItemDetails(item.details)}
        </div>
      </div>
    `).join('');
  }

  function renderKeyValue(label, value) {
    return `
      <div class="run-summary-item">
        <div class="run-summary-label">${escapeHtml(label)}</div>
        <div class="run-summary-value">${escapeHtml(value || '-')}</div>
      </div>
    `;
  }

  function renderSyncItemDetails(details) {
    if (!details || typeof details !== 'object' || Object.keys(details).length === 0) {
      return '';
    }

    const reason = details.reason ? `<div class="small text-secondary mt-2">Причина: ${escapeHtml(String(details.reason))}</div>` : '';
    const message = details.message ? `<div class="small text-secondary">${escapeHtml(String(details.message))}</div>` : '';
    const activeActor = details.activeSyncStartedByActorDisplayName || details.activeSyncStartedByActorId;
    const activeSyncNote = activeActor || details.activeSyncModuleCode
      ? `
        <div class="small text-secondary">
          ${details.activeSyncModuleCode ? `Активный импорт: ${escapeHtml(String(details.activeSyncModuleCode))}. ` : ''}
          ${activeActor ? `Пользователь: ${escapeHtml(String(activeActor))}.` : ''}
        </div>
      `
      : '';
    const rawJson = JSON.stringify(details, null, 2);

    return `
      <div class="mt-2">
        ${reason}
        ${message}
        ${activeSyncNote}
        <details class="mt-2">
          <summary class="small text-secondary">Технические детали</summary>
          <pre class="bg-body-tertiary rounded p-2 mt-2 small mb-0">${escapeHtml(rawJson)}</pre>
        </details>
      </div>
    `;
  }

  function syncRunTitle(run) {
    if (String(run.scope || '').toUpperCase() === 'ONE' && run.moduleCode) {
      return `Модуль ${run.moduleCode}`;
    }
    return translateSyncScope(run.scope);
  }

  function syncRunMeta(run) {
    const actor = run.startedByActorDisplayName || run.startedByActorId;
    const finishedAt = run.finishedAt ? `Завершение: ${formatDateTime(run.finishedAt)}` : 'Запуск еще выполняется';
    return [
      actor ? `Инициатор: ${actor}` : null,
      finishedAt,
    ].filter(Boolean).join(' · ');
  }

  function formatDateTime(value) {
    if (!value) {
      return '-';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return date.toLocaleString();
  }

  refreshPageState().catch(console.error);
  setInterval(() => {
    refreshPageState(selectedSyncRunId).catch(console.error);
  }, 5000);
})(window);
