(function initDbModulesPage() {
  const { escapeHtml, fetchJson, postJson, withMonacoReady, createMonacoEditor } = window.DataPoolCommon || {};
  const { createRuntimeModeController } = window.DataPoolRuntimeMode || {};

  let configEditor = null;
  let sqlEditor = null;
  let selectedModuleId = null;
  let currentModule = null;
  let currentSqlPath = null;
  let sqlContents = {};
  let runtimeContext = null;
  let syncState = null;
  let currentRuns = [];

  const dbModeIndicator = document.getElementById('dbModeIndicator');
  const dbModeDot = document.getElementById('dbModeDot');
  const dbModeText = document.getElementById('dbModeText');
  const dbModeStatus = document.getElementById('dbModeStatus');
  const dbModeToggle = document.getElementById('dbModeToggle');
  const dbContextAlert = document.getElementById('dbContextAlert');
  const dbModuleList = document.getElementById('dbModuleList');
  const dbCatalogStatus = document.getElementById('dbCatalogStatus');
  const selectedModuleLabel = document.getElementById('selectedModuleLabel');
  const selectedModuleDescription = document.getElementById('selectedModuleDescription');
  const moduleSourceKind = document.getElementById('moduleSourceKind');
  const moduleValidationAlert = document.getElementById('moduleValidationAlert');
  const sqlFileSelect = document.getElementById('sqlFileSelect');
  const reloadButton = document.getElementById('reloadButton');
  const runModuleButton = document.getElementById('runModuleButton');
  const saveWorkingCopyButton = document.getElementById('saveWorkingCopyButton');
  const discardWorkingCopyButton = document.getElementById('discardWorkingCopyButton');
  const publishButton = document.getElementById('publishButton');
  const createModuleButton = document.getElementById('createModuleButton');
  const deleteModuleButton = document.getElementById('deleteModuleButton');
  const workingCopyDetails = document.getElementById('workingCopyDetails');
  const dbRunsList = document.getElementById('dbRunsList');
  const runtimeModeController = createRuntimeModeController ? createRuntimeModeController({
    indicatorEl: dbModeIndicator,
    dotEl: dbModeDot,
    textEl: dbModeText,
    statusEl: dbModeStatus,
    toggleEl: dbModeToggle,
    onContextChanged: handleRuntimeContextChanged,
  }) : null;

  reloadButton.addEventListener('click', () => {
    if (!selectedModuleId) return;
    loadModule(selectedModuleId);
  });

  runModuleButton.addEventListener('click', async () => {
    if (!selectedModuleId) return;
    try {
      const result = await postJson(
        `/api/db/modules/${selectedModuleId}/run`,
        {},
        'Не удалось запустить DB-модуль.'
      );
      alert(result.message);
      await loadModuleRuns(selectedModuleId);
    } catch (e) {
      console.error(e);
    }
  });

  saveWorkingCopyButton.addEventListener('click', async () => {
    if (!selectedModuleId) return;
    await postJson(
      `/api/db/modules/${selectedModuleId}/save`,
      {
        configText: configEditor.getValue(),
        sqlFiles: sqlContents,
      },
      'Не удалось сохранить working copy.'
    );
    loadModule(selectedModuleId);
  });

  discardWorkingCopyButton.addEventListener('click', async () => {
    if (!selectedModuleId) return;
    if (!confirm('Удалить личную working copy? Это действие нельзя отменить.')) return;
    await postJson(
      `/api/db/modules/${selectedModuleId}/discard-working-copy`,
      {},
      'Не удалось удалить working copy.'
    );
    loadModule(selectedModuleId);
  });

  publishButton.addEventListener('click', async () => {
    if (!selectedModuleId) return;
    if (!confirm('Опубликовать working copy как новую ревизию? Working copy будет удалена.')) return;
    try {
      const result = await postJson(
        `/api/db/modules/${selectedModuleId}/publish`,
        {},
        'Не удалось опубликовать working copy.'
      );
      alert(result.message);
      loadModule(selectedModuleId);
      loadDbModuleCatalog();
    } catch (e) {
      console.error(e);
    }
  });

  createModuleButton.addEventListener('click', async () => {
    const moduleCode = prompt('Введите код модуля (уникальный идентификатор):');
    if (!moduleCode || !moduleCode.trim()) return;
    
    const title = prompt('Введите название модуля:', moduleCode) || moduleCode;
    
    try {
      const result = await postJson(
        '/api/db/modules',
        {
          moduleCode: moduleCode.trim(),
          title: title,
          description: '',
          tags: [],
          configText: 'app:\n  title: ' + title + '\n  sources: []\n',
        },
        'Не удалось создать модуль.'
      );
      alert(result.message);
      loadDbModuleCatalog();
    } catch (e) {
      console.error(e);
    }
  });

  deleteModuleButton.addEventListener('click', async () => {
    if (!selectedModuleId) return;
    if (!confirm(`Удалить модуль '${selectedModuleId}'? Это действие необратимо.`)) return;
    try {
      const result = await fetch(`/api/db/modules/${selectedModuleId}`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
      }).then(r => {
        if (!r.ok) throw new Error(`HTTP ${r.status}: ${r.statusText}`);
        return r.json();
      });
      alert(result.message);
      selectedModuleId = null;
      loadDbModuleCatalog();
    } catch (e) {
      alert('Ошибка удаления: ' + (e.message || e));
    }
  });

  sqlFileSelect.addEventListener('change', () => {
    currentSqlPath = sqlFileSelect.value || null;
    if (sqlEditor) {
      sqlEditor.setValue(currentSqlPath ? (sqlContents[currentSqlPath] || '') : '');
    }
  });

  withMonacoReady(async () => {
    configEditor = createMonacoEditor('configEditor', {
      value: '',
      language: 'yaml',
    });

    sqlEditor = createMonacoEditor('sqlEditor', {
      value: '',
      language: 'sql',
    });

    await refreshDbState();
    if (canWorkWithDbModules()) {
      await loadDbModuleCatalog();
    }

    setInterval(async () => {
      const maintenanceWasActive = syncState?.maintenanceMode === true;
      const hadActiveSingleSyncs = hasActiveSingleSyncs();
      await refreshDbState();
      if (canWorkWithDbModules() && (maintenanceWasActive || hadActiveSingleSyncs || hasActiveSingleSyncs())) {
        await loadDbModuleCatalog();
      }
      if (canWorkWithDbModules() && selectedModuleId && hasRunningDbRun()) {
        await loadModuleRuns(selectedModuleId);
      }
    }, 5000);
  });

  async function loadRuntimeContext() {
    try {
      if (runtimeModeController) {
        runtimeContext = await runtimeModeController.loadContext();
      } else {
        runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить runtime context.');
      }
    } catch (e) {
      console.error(e);
      showDbContextError('Не удалось определить состояние DB-режима.');
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

  async function refreshDbState() {
    await loadRuntimeContext();
    await loadSyncState();
    renderRuntimeContext();
    renderDbCatalogStatus(runtimeContext);
  }

  async function handleRuntimeContextChanged(context) {
    runtimeContext = context;
    await loadSyncState();
    renderRuntimeContext();
    renderDbCatalogStatus(runtimeContext);
    if (canWorkWithDbModules()) {
      await loadDbModuleCatalog();
      if (selectedModuleId) {
        await loadModuleRuns(selectedModuleId);
      }
      return;
    }
    renderUnavailableCatalogState('Каталог DB-модулей временно недоступен.');
  }

  function canWorkWithDbModules() {
    return runtimeContext?.effectiveMode === 'DATABASE' && syncState?.maintenanceMode !== true;
  }

  function hasActiveSingleSyncs() {
    return Array.isArray(syncState?.activeSingleSyncs) && syncState.activeSingleSyncs.length > 0;
  }

  function activeSingleSyncFor(moduleCode) {
    if (!moduleCode) {
      return null;
    }
    return (syncState?.activeSingleSyncs || []).find(item => item.moduleCode === moduleCode) || null;
  }

  function selectedModuleIsSyncing() {
    return activeSingleSyncFor(selectedModuleId) != null;
  }

  function describeActiveSingleSync(activeSync) {
    if (!activeSync) {
      return '';
    }
    const actorName = activeSync.startedByActorDisplayName || activeSync.startedByActorId || 'неизвестным пользователем';
    const moduleCode = activeSync.moduleCode || 'unknown';
    const startedAt = activeSync.startedAt
      ? new Date(activeSync.startedAt).toLocaleString()
      : null;
    return `Модуль ${moduleCode} импортируется пользователем ${actorName}${startedAt ? ` с ${startedAt}` : ''}.`;
  }

  function renderRuntimeContext() {
    const isDb = runtimeContext?.effectiveMode === 'DATABASE';
    const maintenanceMode = syncState?.maintenanceMode === true;

    if (!isDb) {
      showDbContextError(runtimeContext.fallbackReason || 'DB-режим не активен.');
      setDbControlsDisabled(true);
    } else if (maintenanceMode) {
      const activeSync = syncState.activeFullSync;
      const actorName = activeSync?.startedByActorDisplayName || activeSync?.startedByActorId;
      const startedAt = activeSync?.startedAt
        ? new Date(activeSync.startedAt).toLocaleString()
        : null;
      showDbContextError(
        [
          syncState.message,
          actorName ? `Инициатор: ${actorName}.` : null,
          startedAt ? `Запуск: ${startedAt}.` : null,
        ].filter(Boolean).join(' '),
        'Идет массовый импорт модулей'
      );
      setDbControlsDisabled(true);
    } else if (selectedModuleIsSyncing()) {
      showDbContextError(
        describeActiveSingleSync(activeSingleSyncFor(selectedModuleId)),
        'Идет импорт выбранного модуля',
        'warning',
      );
      setDbControlsDisabled(false);
    } else if (hasActiveSingleSyncs()) {
      showDbContextError(
        `Точечный импорт выполняется для ${syncState.activeSingleSyncs.length} модулей. Можно работать с остальными модулями.`,
        'Идут точечные импорты',
        'info',
      );
      setDbControlsDisabled(false);
    } else {
      dbContextAlert.classList.add('d-none');
      setDbControlsDisabled(false);
    }
  }

  function showDbContextError(message, title = 'DB-режим недоступен', kind = 'warning') {
    dbContextAlert.className = `alert alert-${kind} mb-4`;
    dbContextAlert.classList.remove('d-none');
    document.getElementById('dbContextAlertTitle').textContent = title;
    document.getElementById('dbContextAlertText').textContent = message;
  }

  function setDbControlsDisabled(disabled) {
    reloadButton.disabled = disabled;
    createModuleButton.disabled = disabled;
    deleteModuleButton.disabled = disabled || !selectedModuleId;
    if (disabled) {
      saveWorkingCopyButton.disabled = true;
      discardWorkingCopyButton.disabled = true;
      publishButton.disabled = true;
      [...dbModuleList.querySelectorAll('button')].forEach(button => {
        button.disabled = true;
      });
      return;
    }
    updateSaveDiscardButtons();
    [...dbModuleList.querySelectorAll('button')].forEach(button => {
      button.disabled = false;
    });
  }

  function resetSelectedModuleState() {
    selectedModuleId = null;
    currentModule = null;
    currentSqlPath = null;
    currentRuns = [];
    sqlContents = {};
    selectedModuleLabel.textContent = 'Модуль не выбран';
    selectedModuleDescription.classList.add('d-none');
    selectedModuleDescription.textContent = '';
    moduleSourceKind.innerHTML = '';
    workingCopyDetails.textContent = 'Working copy не загружена.';
    moduleValidationAlert.classList.add('d-none');
    moduleValidationAlert.textContent = '';
    sqlFileSelect.innerHTML = '';
    dbRunsList.textContent = 'Запусков пока нет.';
    if (configEditor) {
      configEditor.setValue('');
    }
    if (sqlEditor) {
      sqlEditor.setValue('');
    }
    updateSaveDiscardButtons();
  }

  function renderUnavailableCatalogState(message) {
    resetSelectedModuleState();
    dbModuleList.innerHTML = `
      <div class="list-group-item text-secondary">
        ${escapeHtml(message)}
      </div>
    `;
  }

  async function loadDbModuleCatalog() {
    if (!canWorkWithDbModules()) {
      renderUnavailableCatalogState('Каталог DB-модулей временно недоступен.');
      return;
    }

    try {
      const payload = await fetchJson('/api/db/modules/catalog', {}, 'Не удалось загрузить каталог DB-модулей.');
      const modules = Array.isArray(payload.modules) ? payload.modules : [];
      dbModuleList.innerHTML = '';

      if (modules.length === 0) {
        resetSelectedModuleState();
        dbModuleList.innerHTML = `
          <div class="list-group-item text-secondary">
            DB-модули не найдены. Импортируйте файловые модули или создайте новый модуль.
          </div>
        `;
        return;
      }

      modules.forEach(module => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'list-group-item list-group-item-action';
        button.innerHTML = renderModuleListItem(module);
        button.dataset.moduleId = module.id;
        button.addEventListener('click', () => selectModule(module.id));
        dbModuleList.appendChild(button);
      });

      const selectedModuleExists = modules.some(module => module.id === selectedModuleId);
      if (selectedModuleExists) {
        [...dbModuleList.children].forEach(button => {
          button.classList.toggle('active', button.dataset.moduleId === selectedModuleId);
        });
        updateSaveDiscardButtons();
        return;
      }

      if (modules.length > 0) {
        await selectModule(modules[0].id);
      }
    } catch (e) {
      console.error(e);
      dbCatalogStatus.className = 'module-catalog-status mt-3 mb-3 text-danger small';
      dbCatalogStatus.textContent = e.message || 'Не удалось загрузить каталог DB-модулей.';
    }
  }

  function renderDbCatalogStatus(ctx) {
    if (!ctx) return;
    const isDb = ctx.effectiveMode === 'DATABASE';
    const maintenanceMode = syncState?.maintenanceMode === true;
    const activeSingleSyncCount = hasActiveSingleSyncs() ? syncState.activeSingleSyncs.length : 0;
    dbCatalogStatus.className = `module-catalog-status mt-3 mb-3 small ${(isDb && !maintenanceMode) ? 'module-catalog-ready' : 'module-catalog-warning'}`;
    dbCatalogStatus.innerHTML = `
      <div>Режим: <strong>${isDb ? 'DATABASE' : 'FILES'}</strong></div>
      <div>PostgreSQL: ${ctx.database.available ? 'доступен' : 'недоступен'}</div>
      ${ctx.actor.actorId ? `<div>Actor: ${escapeHtml(ctx.actor.actorId)}</div>` : ''}
      ${maintenanceMode ? '<div class="text-warning-emphasis mt-1">Mass import: выполняется</div>' : ''}
      ${activeSingleSyncCount > 0 ? `<div class="text-info-emphasis mt-1">Single import: ${activeSingleSyncCount}</div>` : ''}
      ${ctx.fallbackReason ? `<div class="text-danger mt-1">Fallback: ${escapeHtml(ctx.fallbackReason)}</div>` : ''}
    `;
  }

  async function selectModule(moduleId) {
    if (!canWorkWithDbModules()) return;
    selectedModuleId = moduleId;
    [...dbModuleList.children].forEach(child => {
      child.classList.toggle('active', child.dataset.moduleId === moduleId);
    });
    await loadModule(moduleId);
    await loadModuleRuns(moduleId);
  }

  async function loadModule(moduleId) {
    if (!canWorkWithDbModules()) return;
    currentModule = await fetchJson(`/api/db/modules/${moduleId}`, {}, 'Не удалось загрузить DB-модуль.');
    
    selectedModuleLabel.textContent = `${currentModule.module.title} · ${currentModule.module.configPath}`;
    if (currentModule.module.description) {
      selectedModuleDescription.textContent = currentModule.module.description;
      selectedModuleDescription.classList.remove('d-none');
    } else {
      selectedModuleDescription.classList.add('d-none');
    }

    moduleSourceKind.innerHTML = `
      <span class="badge bg-${currentModule.sourceKind === 'WORKING_COPY' ? 'warning' : 'success'}">
        ${currentModule.sourceKind === 'WORKING_COPY' ? 'Working Copy' : 'Current Revision'}
      </span>
    `;

    configEditor.setValue(currentModule.module.configText);

    sqlContents = {};
    currentModule.module.sqlFiles.forEach(file => {
      sqlContents[file.path] = file.content;
    });
    renderSqlSelect();
    renderModuleValidation(currentModule.module);
    renderWorkingCopyInfo();

    updateSaveDiscardButtons();
    renderRuntimeContext();
    renderDbCatalogStatus(runtimeContext);

    [...dbModuleList.children].forEach(button => {
      button.classList.toggle('active', button.dataset.moduleId === currentModule.module.id);
    });
  }

  async function loadModuleRuns(moduleId) {
    if (!canWorkWithDbModules() || !moduleId) {
      currentRuns = [];
      renderModuleRuns();
      return;
    }
    try {
      const payload = await fetchJson(`/api/db/modules/${moduleId}/runs`, {}, 'Не удалось загрузить историю DB-запусков.');
      currentRuns = Array.isArray(payload.runs) ? payload.runs : [];
    } catch (e) {
      console.error(e);
      currentRuns = [];
    }
    renderModuleRuns();
    updateSaveDiscardButtons();
  }

  function renderSqlSelect() {
    sqlFileSelect.innerHTML = '';
    currentSqlPath = null;
    (currentModule?.module.sqlFiles || []).forEach((file, index) => {
      const option = document.createElement('option');
      option.value = file.path;
      option.textContent = `${file.label} · ${file.path}`;
      sqlFileSelect.appendChild(option);
      if (index === 0) {
        currentSqlPath = file.path;
      }
    });
    if (sqlEditor && currentSqlPath) {
      sqlEditor.setValue(sqlContents[currentSqlPath] || '');
    }
  }

  function renderModuleListItem(module) {
    const activeSync = activeSingleSyncFor(module.id);
    const validationBadge = renderValidationBadge(module.validationStatus);
    const syncBadge = activeSync
      ? '<span class="badge text-bg-warning">Sync</span>'
      : '';
    const description = module.description
      ? `<div class="module-list-description">${escapeHtml(module.description)}</div>`
      : '';
    const tags = Array.isArray(module.tags) && module.tags.length > 0
      ? `<div class="module-list-tags">${module.tags.map(tag => `<span class="module-tag">${escapeHtml(tag)}</span>`).join('')}</div>`
      : '';
    const issues = Array.isArray(module.validationIssues) && module.validationIssues.length > 0
      ? `<div class="module-list-issues">${escapeHtml(module.validationIssues[0].message)}</div>`
      : '';
    const syncDetails = activeSync
      ? `<div class="module-list-issues text-warning-emphasis">${escapeHtml(describeActiveSingleSync(activeSync))}</div>`
      : '';
    return `
      <div class="module-list-head">
        <span class="module-list-title">${escapeHtml(module.title)}</span>
        ${validationBadge}
        ${syncBadge}
      </div>
      ${description}
      ${tags}
      ${issues}
      ${syncDetails}
    `;
  }

  function renderValidationBadge(status) {
    const normalized = String(status || 'VALID').toUpperCase();
    const label = normalized === 'VALID' ? 'OK' : (normalized === 'WARNING' ? 'Warning' : 'Invalid');
    return `<span class="module-validation-badge module-validation-badge-${normalized.toLowerCase()}">${label}</span>`;
  }

  function renderModuleValidation(module) {
    const issues = Array.isArray(module.validationIssues) ? module.validationIssues : [];
    const status = String(module.validationStatus || 'VALID').toUpperCase();
    if (status === 'VALID' && issues.length === 0) {
      moduleValidationAlert.classList.add('d-none');
      moduleValidationAlert.textContent = '';
      return;
    }

    const alertClass = status === 'INVALID' ? 'alert-danger' : 'alert-warning';
    const title = status === 'INVALID'
      ? 'У модуля есть проблемы, которые стоит исправить.'
      : 'У модуля есть предупреждения.';
    moduleValidationAlert.className = `alert ${alertClass} mb-3`;
    moduleValidationAlert.innerHTML = `
      <div class="fw-semibold mb-2">${escapeHtml(title)}</div>
      <ul class="module-validation-list mb-0">
        ${issues.map(issue => `
          <li>
            <span class="module-validation-severity module-validation-severity-${String(issue.severity || '').toLowerCase()}">${escapeHtml(issue.severity || 'INFO')}</span>
            <span>${escapeHtml(issue.message || '')}</span>
          </li>
        `).join('')}
      </ul>
    `;
  }

  function renderWorkingCopyInfo() {
    const hasWorkingCopy = currentModule.workingCopyId !== null;
    workingCopyDetails.innerHTML = `
      <div class="mt-2">
        <div><strong>Source:</strong> ${escapeHtml(currentModule.sourceKind)}</div>
        <div><strong>Current Revision:</strong> <code>${escapeHtml(currentModule.currentRevisionId)}</code></div>
        ${hasWorkingCopy ? `
          <div><strong>Working Copy ID:</strong> <code>${escapeHtml(currentModule.workingCopyId)}</code></div>
          <div><strong>Working Copy Status:</strong> <span class="badge bg-warning">${escapeHtml(currentModule.workingCopyStatus)}</span></div>
          <div><strong>Base Revision:</strong> <code>${escapeHtml(currentModule.baseRevisionId)}</code></div>
        ` : '<div class="text-success mt-1">Нет личной working copy — вы просматриваете текущую ревизию.</div>'}
      </div>
    `;
  }

  function renderModuleRuns() {
    if (!dbRunsList) return;
    if (!currentRuns.length) {
      dbRunsList.innerHTML = '<div class="text-secondary small">Запусков пока нет.</div>';
      return;
    }

    dbRunsList.innerHTML = currentRuns.slice(0, 5).map(run => {
      const statusClass = run.status === 'SUCCESS'
        ? 'text-bg-success'
        : (run.status === 'SUCCESS_WITH_WARNINGS'
          ? 'text-bg-warning'
          : (run.status === 'RUNNING' ? 'text-bg-primary' : 'text-bg-danger'));
      const requestedAt = run.requestedAt ? new Date(run.requestedAt).toLocaleString() : '';
      const finishedAt = run.finishedAt ? new Date(run.finishedAt).toLocaleString() : null;
      const targetLine = run.targetTableName
        ? `<span class="text-secondary">Target: ${escapeHtml(run.targetTableName)} (${escapeHtml(run.targetStatus)})</span>`
        : `<span class="text-secondary">Target: ${escapeHtml(run.targetStatus)}</span>`;
      return `
        <div class="card mb-2">
          <div class="card-body py-2 px-3">
            <div class="d-flex flex-wrap align-items-center gap-2 mb-1">
              <span class="badge ${statusClass}">${escapeHtml(run.status)}</span>
              <code>${escapeHtml(run.runId)}</code>
              <span>${escapeHtml(run.launchSourceKind)}</span>
            </div>
            <div class="small text-secondary">
              Запрошен: ${escapeHtml(requestedAt)}
              ${finishedAt ? ` · Завершен: ${escapeHtml(finishedAt)}` : ''}
            </div>
            <div class="small mt-1">
              <span>Успешных источников: <strong>${escapeHtml(String(run.successfulSourceCount))}</strong></span>
              <span class="ms-2">Ошибок: <strong>${escapeHtml(String(run.failedSourceCount))}</strong></span>
              <span class="ms-2">Пропущено: <strong>${escapeHtml(String(run.skippedSourceCount))}</strong></span>
              ${run.mergedRowCount != null ? `<span class="ms-2">Merged: <strong>${escapeHtml(String(run.mergedRowCount))}</strong></span>` : ''}
            </div>
            <div class="small mt-1">${targetLine}</div>
            ${run.errorMessage ? `<div class="small text-danger mt-1">${escapeHtml(run.errorMessage)}</div>` : ''}
          </div>
        </div>
      `;
    }).join('');
  }

  function hasRunningDbRun() {
    return currentRuns.some(run => run.status === 'RUNNING');
  }

  function updateSaveDiscardButtons() {
    if (!canWorkWithDbModules()) {
      runModuleButton.disabled = true;
      saveWorkingCopyButton.disabled = true;
      discardWorkingCopyButton.disabled = true;
      publishButton.disabled = true;
      deleteModuleButton.disabled = true;
      return;
    }
    if (!currentModule) {
      runModuleButton.disabled = true;
      saveWorkingCopyButton.disabled = true;
      discardWorkingCopyButton.disabled = true;
      publishButton.disabled = true;
      deleteModuleButton.disabled = !selectedModuleId;
      return;
    }
    if (hasRunningDbRun()) {
      runModuleButton.disabled = true;
    } else {
      runModuleButton.disabled = false;
    }
    if (selectedModuleIsSyncing()) {
      runModuleButton.disabled = true;
      saveWorkingCopyButton.disabled = true;
      discardWorkingCopyButton.disabled = true;
      publishButton.disabled = true;
      deleteModuleButton.disabled = true;
      return;
    }
    const hasWorkingCopy = currentModule.workingCopyId !== null;
    saveWorkingCopyButton.disabled = false;
    discardWorkingCopyButton.disabled = !hasWorkingCopy;
    publishButton.disabled = !hasWorkingCopy;
    deleteModuleButton.disabled = false;
  }

})();
