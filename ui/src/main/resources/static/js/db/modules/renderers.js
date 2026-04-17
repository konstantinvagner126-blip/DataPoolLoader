(function registerDbModulesRenderers(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};

  function createRenderer(ctx) {
    const runtimeRenderer = modules.createRuntimeRenderer(ctx);
    const moduleRenderer = modules.createModuleRenderer(ctx, runtimeRenderer);
    const runRenderer = modules.createRunRenderer(ctx, runtimeRenderer);

    function resetSelectedModuleState() {
      const { refs, editors, state } = ctx;
      state.selectedModuleId = null;
      state.currentModule = null;
      state.currentSqlPath = null;
      state.currentRuns = [];
      state.selectedRunId = null;
      state.selectedRunDetails = null;
      state.sqlContents = {};
      state.persistedConfigText = '';
      state.persistedSqlContents = {};
      refs.selectedModuleLabel.textContent = 'Модуль не выбран';
      refs.selectedModuleDescription.classList.add('d-none');
      refs.selectedModuleDescription.textContent = '';
      refs.moduleDraftStatus.innerHTML = `
        <span class="module-draft-dot module-draft-dot-neutral" aria-hidden="true"></span>
        <span>Модуль не выбран.</span>
      `;
      refs.moduleDraftStatus.className = 'module-draft-status small mt-1 text-secondary';
      refs.moduleSourceKind.innerHTML = '';
      refs.workingCopyDetails.textContent = 'Рабочая копия пока не загружена.';
      refs.moduleMetadata.innerHTML = '<div class="text-secondary small">Модуль не выбран.</div>';
      refs.moduleValidationAlert.classList.add('d-none');
      refs.moduleValidationAlert.textContent = '';
      refs.sqlCatalogList.innerHTML = '';
      refs.sqlResourceTitle.textContent = 'SQL-ресурс не выбран';
      refs.sqlResourceMeta.innerHTML = '<span class="text-secondary">Создай новый SQL или выбери существующий ресурс.</span>';
      refs.sqlResourceUsage.innerHTML = '<span class="text-secondary">Usage пока недоступен.</span>';
      refs.dbRunsList.innerHTML = '<div class="text-secondary small">Запусков пока нет.</div>';
      runRenderer.renderSelectedRunDetails();
      if (editors.configEditor) {
        editors.configEditor.setValue('');
      }
      if (editors.sqlEditor) {
        editors.sqlEditor.setValue('');
      }
      ctx.formController?.initialize();
      ctx.sqlCatalogController?.render();
      ctx.credentialsController?.renderWarning();
      runtimeRenderer.updateSaveDiscardButtons();
    }

    function renderUnavailableCatalogState(message) {
      const { escapeHtml } = ctx.common;
      const { refs } = ctx;
      resetSelectedModuleState();
      refs.dbModuleList.innerHTML = `
        <div class="list-group-item text-secondary">
          ${escapeHtml(message)}
        </div>
      `;
    }

    return {
      ...runtimeRenderer,
      ...moduleRenderer,
      ...runRenderer,
      resetSelectedModuleState,
      renderUnavailableCatalogState,
    };
  }

  modules.createRenderer = createRenderer;
})(window);
