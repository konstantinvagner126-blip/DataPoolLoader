(function registerDbModulesModuleRenderer(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};
  const shared = root.shared || {};
  const moduleEditorSharedNamespace = global.DataPoolModuleEditorShared || {};
  const { renderModuleMetadata: renderSharedModuleMetadata } = moduleEditorSharedNamespace;

  function createModuleRenderer(ctx, runtimeRenderer) {
    const { refs, editors, state } = ctx;
    const { escapeHtml } = ctx.common;

    function translateModuleSourceKind(sourceKind) {
      return sourceKind === 'WORKING_COPY' ? 'Рабочая копия' : (sourceKind === 'CURRENT_REVISION' ? 'Текущая ревизия' : (sourceKind || '-'));
    }

    function renderCatalog(modulesList, onSelectModule) {
      refs.dbModuleList.innerHTML = '';
      modulesList.forEach(module => {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'list-group-item list-group-item-action';
        button.innerHTML = renderModuleListItem(module);
        button.dataset.moduleId = module.id;
        button.addEventListener('click', () => onSelectModule(module.id));
        refs.dbModuleList.appendChild(button);
      });
      runtimeRenderer.highlightSelectedModule();
    }

    function applyCurrentModule() {
      const module = state.currentModule?.module;
      if (!module) {
        return;
      }

      refs.selectedModuleLabel.textContent = `${module.title} · ${module.configPath}`;
      if (module.description) {
        refs.selectedModuleDescription.textContent = module.description;
        refs.selectedModuleDescription.classList.remove('d-none');
      } else {
        refs.selectedModuleDescription.classList.add('d-none');
        refs.selectedModuleDescription.textContent = '';
      }

      refs.moduleSourceKind.innerHTML = `
        <span class="source-kind-badge ${state.currentModule.sourceKind === 'WORKING_COPY' ? 'source-kind-badge-working-copy' : 'source-kind-badge-revision'}">
          ${state.currentModule.sourceKind === 'WORKING_COPY' ? 'Рабочая копия' : 'Текущая ревизия'}
        </span>
      `;

      if (editors.configEditor) {
        editors.configEditor.setValue(module.configText);
      }

      ctx.sqlCatalogController?.render();
      renderModuleValidation(module);
      renderWorkingCopyInfo();
      renderModuleMetadata();
      renderDraftStatus();
      ctx.credentialsController?.renderWarning();
      runtimeRenderer.updateSaveDiscardButtons();
      runtimeRenderer.renderRuntimeContext();
      runtimeRenderer.renderDbCatalogStatus();
      runtimeRenderer.highlightSelectedModule();
    }

    function renderModuleListItem(module) {
      const activeSync = modules.activeSingleSyncFor(ctx, module.id);
      const validationBadge = renderValidationBadge(module.validationStatus);
      const syncBadge = activeSync ? '<span class="badge text-bg-warning">Sync</span>' : '';
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
        ? `<div class="module-list-issues text-warning-emphasis">${escapeHtml(shared.describeActiveSingleSync(activeSync))}</div>`
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
      const label = normalized === 'VALID' ? 'OK' : (normalized === 'WARNING' ? 'Предупреждение' : 'Ошибка');
      return `<span class="module-validation-badge module-validation-badge-${normalized.toLowerCase()}">${label}</span>`;
    }

    function renderModuleValidation(module) {
      const issues = Array.isArray(module.validationIssues) ? module.validationIssues : [];
      const status = String(module.validationStatus || 'VALID').toUpperCase();
      if (status === 'VALID' && issues.length === 0) {
        refs.moduleValidationAlert.classList.add('d-none');
        refs.moduleValidationAlert.textContent = '';
        return;
      }

      const alertClass = status === 'INVALID' ? 'alert-danger' : 'alert-warning';
      const title = status === 'INVALID'
        ? 'У модуля есть проблемы, которые стоит исправить.'
        : 'У модуля есть предупреждения.';
      refs.moduleValidationAlert.className = `alert ${alertClass} mb-3`;
      refs.moduleValidationAlert.innerHTML = `
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
      const hasWorkingCopy = state.currentModule?.workingCopyId != null;
      refs.workingCopyDetails.innerHTML = `
        <div class="mt-2">
          <div><strong>Источник:</strong> ${escapeHtml(translateModuleSourceKind(state.currentModule?.sourceKind))}</div>
          <div><strong>Текущая ревизия:</strong> <code>${escapeHtml(state.currentModule?.currentRevisionId || '-')}</code></div>
          ${hasWorkingCopy ? `
            <div><strong>ID рабочей копии:</strong> <code>${escapeHtml(state.currentModule.workingCopyId)}</code></div>
            <div><strong>Статус рабочей копии:</strong> <span class="badge bg-warning">${escapeHtml(state.currentModule.workingCopyStatus)}</span></div>
            <div><strong>Базовая ревизия:</strong> <code>${escapeHtml(state.currentModule.baseRevisionId)}</code></div>
          ` : '<div class="text-success mt-1">Личной рабочей копии нет. Сейчас открыта текущая ревизия.</div>'}
        </div>
      `;
    }

    function renderModuleMetadata() {
      renderSharedModuleMetadata?.(refs.moduleMetadata, state.currentModule);
    }

    function renderDraftStatus() {
      if (!refs.moduleDraftStatus) {
        return;
      }

      if (!state.currentModule) {
        refs.moduleDraftStatus.innerHTML = `
          <span class="module-draft-dot module-draft-dot-neutral" aria-hidden="true"></span>
          <span>Модуль не выбран.</span>
        `;
        refs.moduleDraftStatus.className = 'module-draft-status small mt-1 text-secondary';
      } else if (modules.hasUnsavedChanges(ctx)) {
        refs.moduleDraftStatus.innerHTML = `
          <span class="module-draft-dot module-draft-dot-dirty" aria-hidden="true"></span>
          <span>Есть несохраненные изменения. Сначала сохрани рабочую копию, затем запускай модуль.</span>
        `;
        refs.moduleDraftStatus.className = 'module-draft-status small mt-1 text-primary';
      } else if (state.currentModule.workingCopyId) {
        refs.moduleDraftStatus.innerHTML = `
          <span class="module-draft-dot module-draft-dot-saved" aria-hidden="true"></span>
          <span>Изменения сохранены в рабочей копии.</span>
        `;
        refs.moduleDraftStatus.className = 'module-draft-status small mt-1 text-secondary';
      } else {
        refs.moduleDraftStatus.innerHTML = `
          <span class="module-draft-dot module-draft-dot-saved" aria-hidden="true"></span>
          <span>Изменений нет. Сейчас открыта текущая ревизия.</span>
        `;
        refs.moduleDraftStatus.className = 'module-draft-status small mt-1 text-secondary';
      }

      runtimeRenderer.updateSaveDiscardButtons();
    }

    function renderMetadataRow(label, value, html = false) {
      return `
        <div class="module-metadata-row">
          <div class="module-metadata-label">${escapeHtml(label)}</div>
          <div class="module-metadata-value">${html ? value : escapeHtml(value)}</div>
        </div>
      `;
    }

    return {
      renderCatalog,
      applyCurrentModule,
      renderDraftStatus,
      renderModuleMetadata,
    };
  }

  modules.createModuleRenderer = createModuleRenderer;
})(window);
