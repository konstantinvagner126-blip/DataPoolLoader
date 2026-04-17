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
      refs.selectedModuleLabel.textContent = 'Модуль не выбран';
      refs.selectedModuleDescription.classList.add('d-none');
      refs.selectedModuleDescription.textContent = '';
      refs.moduleSourceKind.innerHTML = '';
      refs.workingCopyDetails.textContent = 'Working copy не загружена.';
      refs.moduleValidationAlert.classList.add('d-none');
      refs.moduleValidationAlert.textContent = '';
      refs.sqlFileSelect.innerHTML = '';
      refs.dbRunsList.innerHTML = '<div class="text-secondary small">Запусков пока нет.</div>';
      runRenderer.renderSelectedRunDetails();
      if (editors.configEditor) {
        editors.configEditor.setValue('');
      }
      if (editors.sqlEditor) {
        editors.sqlEditor.setValue('');
      }
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
