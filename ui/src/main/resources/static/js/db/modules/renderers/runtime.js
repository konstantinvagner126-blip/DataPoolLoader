(function registerDbModulesRuntimeRenderer(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};
  const shared = root.shared || {};

  function createRuntimeRenderer(ctx) {
    const { refs, state } = ctx;
    const { escapeHtml } = ctx.common;

    function renderRuntimeContext() {
      const maintenanceMode = state.syncState?.maintenanceMode === true;

      if (!shared.isDatabaseMode(state.runtimeContext)) {
        showDbContextAlert(state.runtimeContext?.fallbackReason || 'DB-режим не активен.');
        setDbControlsDisabled(true);
      } else if (maintenanceMode) {
        const activeSync = state.syncState.activeFullSync;
        const actorName = activeSync?.startedByActorDisplayName || activeSync?.startedByActorId;
        const startedAt = activeSync?.startedAt ? new Date(activeSync.startedAt).toLocaleString() : null;
        showDbContextAlert(
          [state.syncState.message, actorName ? `Инициатор: ${actorName}.` : null, startedAt ? `Запуск: ${startedAt}.` : null]
            .filter(Boolean)
            .join(' '),
          'Идет массовый импорт модулей',
        );
        setDbControlsDisabled(true);
      } else if (modules.selectedModuleIsSyncing(ctx)) {
        showDbContextAlert(
          shared.describeActiveSingleSync(modules.activeSingleSyncFor(ctx, state.selectedModuleId)),
          'Идет импорт выбранного модуля',
          'warning',
        );
        setDbControlsDisabled(false);
      } else if (modules.hasActiveSingleSyncs(ctx)) {
        showDbContextAlert(
          `Точечный импорт выполняется для ${shared.activeSingleSyncs(state.syncState).length} модулей. Можно работать с остальными модулями.`,
          'Идут точечные импорты',
          'info',
        );
        setDbControlsDisabled(false);
      } else {
        refs.dbContextAlert.classList.add('d-none');
        setDbControlsDisabled(false);
      }
    }

    function showDbContextAlert(message, title = 'DB-режим недоступен', kind = 'warning') {
      refs.dbContextAlert.className = `alert alert-${kind} mb-4`;
      refs.dbContextAlert.classList.remove('d-none');
      refs.dbContextAlertTitle.textContent = title;
      refs.dbContextAlertText.textContent = message;
    }

    function setDbControlsDisabled(disabled) {
      refs.reloadButton.disabled = disabled;
      refs.createModuleButton.disabled = disabled;
      refs.deleteModuleButton.disabled = disabled || !state.selectedModuleId;
      if (disabled) {
        refs.saveWorkingCopyButton.disabled = true;
        refs.discardWorkingCopyButton.disabled = true;
        refs.publishButton.disabled = true;
        [...refs.dbModuleList.querySelectorAll('button')].forEach(button => {
          button.disabled = true;
        });
        return;
      }
      updateSaveDiscardButtons();
      [...refs.dbModuleList.querySelectorAll('button')].forEach(button => {
        button.disabled = false;
      });
    }

    function highlightSelectedModule() {
      [...refs.dbModuleList.children].forEach(button => {
        button.classList.toggle('active', button.dataset.moduleId === state.selectedModuleId);
      });
    }

    function renderDbCatalogStatus() {
      if (!state.runtimeContext) {
        return;
      }
      const maintenanceMode = state.syncState?.maintenanceMode === true;
      const activeSingleSyncCount = modules.hasActiveSingleSyncs(ctx) ? shared.activeSingleSyncs(state.syncState).length : 0;
      refs.dbCatalogStatus.className = `module-catalog-status mt-3 mb-3 small ${shared.isDatabaseMode(state.runtimeContext) && !maintenanceMode ? 'module-catalog-ready' : 'module-catalog-warning'}`;
      refs.dbCatalogStatus.innerHTML = `
        <div>Режим: <strong>${shared.isDatabaseMode(state.runtimeContext) ? 'DATABASE' : 'FILES'}</strong></div>
        <div>PostgreSQL: ${state.runtimeContext.database.available ? 'доступен' : 'недоступен'}</div>
        ${state.runtimeContext.actor?.actorId ? `<div>Actor: ${escapeHtml(state.runtimeContext.actor.actorId)}</div>` : ''}
        ${maintenanceMode ? '<div class="text-warning-emphasis mt-1">Mass import: выполняется</div>' : ''}
        ${activeSingleSyncCount > 0 ? `<div class="text-info-emphasis mt-1">Single import: ${activeSingleSyncCount}</div>` : ''}
        ${state.runtimeContext.fallbackReason ? `<div class="text-danger mt-1">Fallback: ${escapeHtml(state.runtimeContext.fallbackReason)}</div>` : ''}
      `;
    }

    function updateSaveDiscardButtons() {
      if (!modules.canWorkWithDbModules(ctx)) {
        refs.runModuleButton.disabled = true;
        refs.saveWorkingCopyButton.disabled = true;
        refs.discardWorkingCopyButton.disabled = true;
        refs.publishButton.disabled = true;
        refs.deleteModuleButton.disabled = true;
        return;
      }

      if (!state.currentModule) {
        refs.runModuleButton.disabled = true;
        refs.saveWorkingCopyButton.disabled = true;
        refs.discardWorkingCopyButton.disabled = true;
        refs.publishButton.disabled = true;
        refs.deleteModuleButton.disabled = !state.selectedModuleId;
        return;
      }

      refs.runModuleButton.disabled = modules.hasRunningDbRun(ctx);
      if (modules.selectedModuleIsSyncing(ctx)) {
        refs.runModuleButton.disabled = true;
        refs.saveWorkingCopyButton.disabled = true;
        refs.discardWorkingCopyButton.disabled = true;
        refs.publishButton.disabled = true;
        refs.deleteModuleButton.disabled = true;
        return;
      }

      const hasWorkingCopy = state.currentModule.workingCopyId !== null;
      refs.saveWorkingCopyButton.disabled = false;
      refs.discardWorkingCopyButton.disabled = !hasWorkingCopy;
      refs.publishButton.disabled = !hasWorkingCopy;
      refs.deleteModuleButton.disabled = false;
    }

    return {
      renderRuntimeContext,
      showDbContextAlert,
      setDbControlsDisabled,
      highlightSelectedModule,
      renderDbCatalogStatus,
      updateSaveDiscardButtons,
    };
  }

  modules.createRuntimeRenderer = createRuntimeRenderer;
})(window);
