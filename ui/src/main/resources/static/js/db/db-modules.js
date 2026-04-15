(function initDbModulesPage() {
  const { escapeHtml, fetchJson, postJson, withMonacoReady, createMonacoEditor } = window.DataPoolCommon || {};

  let configEditor = null;
  let sqlEditor = null;
  let selectedModuleId = null;
  let currentModule = null;
  let currentSqlPath = null;
  let sqlContents = {};
  let runtimeContext = null;

  const dbModeIndicator = document.getElementById('dbModeIndicator');
  const dbModeDot = document.getElementById('dbModeDot');
  const dbModeText = document.getElementById('dbModeText');
  const dbContextAlert = document.getElementById('dbContextAlert');
  const dbModuleList = document.getElementById('dbModuleList');
  const dbCatalogStatus = document.getElementById('dbCatalogStatus');
  const selectedModuleLabel = document.getElementById('selectedModuleLabel');
  const selectedModuleDescription = document.getElementById('selectedModuleDescription');
  const moduleSourceKind = document.getElementById('moduleSourceKind');
  const moduleValidationAlert = document.getElementById('moduleValidationAlert');
  const sqlFileSelect = document.getElementById('sqlFileSelect');
  const reloadButton = document.getElementById('reloadButton');
  const saveWorkingCopyButton = document.getElementById('saveWorkingCopyButton');
  const discardWorkingCopyButton = document.getElementById('discardWorkingCopyButton');
  const publishButton = document.getElementById('publishButton');
  const createModuleButton = document.getElementById('createModuleButton');
  const deleteModuleButton = document.getElementById('deleteModuleButton');
  const workingCopyDetails = document.getElementById('workingCopyDetails');

  reloadButton.addEventListener('click', () => {
    if (!selectedModuleId) return;
    loadModule(selectedModuleId);
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
        'Не удалось опублиовать working copy.'
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

    await loadRuntimeContext();
    await loadDbModuleCatalog();
  });

  async function loadRuntimeContext() {
    try {
      runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить runtime context.');
      renderRuntimeContext();
    } catch (e) {
      console.error(e);
      showDbContextError('Не удалось определить состояние DB-режима.');
    }
  }

  function renderRuntimeContext() {
    dbModeIndicator.classList.remove('d-none');
    const isDb = runtimeContext.effectiveMode === 'DATABASE';
    dbModeDot.className = 'db-mode-indicator-dot' + (isDb ? ' db-mode-active' : ' db-mode-inactive');
    dbModeText.textContent = `Режим: ${isDb ? 'DATABASE' : 'FILES'}`;

    if (runtimeContext.fallbackReason) {
      dbModeText.textContent += ` (fallback: ${runtimeContext.fallbackReason})`;
    }

    if (!isDb) {
      showDbContextError(runtimeContext.fallbackReason || 'DB-режим не активен.');
    } else {
      dbContextAlert.classList.add('d-none');
    }
  }

  function showDbContextError(message) {
    dbContextAlert.classList.remove('d-none');
    document.getElementById('dbContextAlertTitle').textContent = 'DB-режим недоступен';
    document.getElementById('dbContextAlertText').textContent = message;
  }

  async function loadDbModuleCatalog() {
    try {
      const payload = await fetchJson('/api/db/modules/catalog', {}, 'Не удалось загрузить каталог DB-модулей.');
      renderDbCatalogStatus(payload.runtimeContext);
      const modules = Array.isArray(payload.modules) ? payload.modules : [];
      dbModuleList.innerHTML = '';

      if (modules.length === 0) {
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
    dbCatalogStatus.className = `module-catalog-status mt-3 mb-3 small ${isDb ? 'module-catalog-ready' : 'module-catalog-warning'}`;
    dbCatalogStatus.innerHTML = `
      <div>Режим: <strong>${isDb ? 'DATABASE' : 'FILES'}</strong></div>
      <div>PostgreSQL: ${ctx.database.available ? 'доступен' : 'недоступен'}</div>
      ${ctx.actor.actorId ? `<div>Actor: ${escapeHtml(ctx.actor.actorId)}</div>` : ''}
      ${ctx.fallbackReason ? `<div class="text-danger mt-1">Fallback: ${escapeHtml(ctx.fallbackReason)}</div>` : ''}
    `;
  }

  async function selectModule(moduleId) {
    selectedModuleId = moduleId;
    [...dbModuleList.children].forEach(child => {
      child.classList.toggle('active', child.dataset.moduleId === moduleId);
    });
    await loadModule(moduleId);
  }

  async function loadModule(moduleId) {
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

    [...dbModuleList.children].forEach(button => {
      button.classList.toggle('active', button.dataset.moduleId === currentModule.module.id);
    });
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
    const validationBadge = renderValidationBadge(module.validationStatus);
    const description = module.description
      ? `<div class="module-list-description">${escapeHtml(module.description)}</div>`
      : '';
    const tags = Array.isArray(module.tags) && module.tags.length > 0
      ? `<div class="module-list-tags">${module.tags.map(tag => `<span class="module-tag">${escapeHtml(tag)}</span>`).join('')}</div>`
      : '';
    const issues = Array.isArray(module.validationIssues) && module.validationIssues.length > 0
      ? `<div class="module-list-issues">${escapeHtml(module.validationIssues[0].message)}</div>`
      : '';
    return `
      <div class="module-list-head">
        <span class="module-list-title">${escapeHtml(module.title)}</span>
        ${validationBadge}
      </div>
      ${description}
      ${tags}
      ${issues}
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

  function updateSaveDiscardButtons() {
    const hasWorkingCopy = currentModule.workingCopyId !== null;
    saveWorkingCopyButton.disabled = false;
    discardWorkingCopyButton.disabled = !hasWorkingCopy;
    publishButton.disabled = !hasWorkingCopy;
    deleteModuleButton.disabled = false;
  }

})();
