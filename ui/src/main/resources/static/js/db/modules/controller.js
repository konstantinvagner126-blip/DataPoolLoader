(function registerDbModulesController(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};
  const moduleEditorSharedNamespace = global.DataPoolModuleEditorShared || {};
  const { normalizeModuleMetadata } = moduleEditorSharedNamespace;

  function createController(ctx, renderer) {
    const { fetchJson, postJson } = ctx.common;
    const { state, editors } = ctx;

    async function loadRuntimeContext() {
      try {
        state.runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить состояние режима интерфейса.');
      } catch (e) {
        console.error(e);
        renderer.showDbContextAlert('Не удалось определить состояние режима базы данных.');
      }
    }

    async function loadSyncState() {
      try {
        state.syncState = await fetchJson('/api/db/sync/state', {}, 'Не удалось загрузить состояние импорта.');
      } catch (e) {
        console.error(e);
        state.syncState = null;
      }
    }

    async function refreshDbState() {
      await loadRuntimeContext();
      await loadSyncState();
      renderer.renderRuntimeContext();
      renderer.renderDbCatalogStatus();
    }

    async function loadDbModuleCatalog() {
      if (!modules.canWorkWithDbModules(ctx)) {
        renderer.renderUnavailableCatalogState('Каталог модулей из базы данных временно недоступен.');
        return;
      }

      try {
        const catalogUrl = state.includeHiddenCatalog ? '/api/db/modules/catalog?includeHidden=true' : '/api/db/modules/catalog';
        const payload = await fetchJson(catalogUrl, {}, 'Не удалось загрузить каталог модулей из базы данных.');
        state.dbCatalogDiagnostics = payload.diagnostics || null;
        const modulesList = Array.isArray(payload.modules) ? payload.modules : [];

        if (modulesList.length === 0) {
          renderer.resetSelectedModuleState();
          ctx.refs.dbModuleList.innerHTML = `
            <div class="list-group-item text-secondary">
              Модули в базе данных не найдены. Импортируй файловые модули или создай новый модуль.
            </div>
          `;
          return;
        }

        renderer.renderCatalog(modulesList, selectModule);

        const selectedModuleExists = modulesList.some(module => module.id === state.selectedModuleId);
        if (selectedModuleExists) {
          renderer.highlightSelectedModule();
          renderer.updateSaveDiscardButtons();
          if (state.currentModule?.module?.id !== state.selectedModuleId) {
            await loadModule(state.selectedModuleId);
            await loadModuleRuns(state.selectedModuleId);
          }
          return;
        }

        await selectModule(modulesList[0].id);
      } catch (e) {
        console.error(e);
        ctx.refs.dbCatalogStatus.className = 'module-catalog-status mt-3 mb-3 text-danger small';
        ctx.refs.dbCatalogStatus.textContent = e.message || 'Не удалось загрузить каталог модулей из базы данных.';
      }
    }

    async function selectModule(moduleId) {
      if (!modules.canWorkWithDbModules(ctx)) return;
      if (state.selectedModuleId !== moduleId) {
        state.selectedRunId = null;
        state.selectedRunDetails = null;
      }
      state.selectedModuleId = moduleId;
      ctx.persistUiState?.();
      renderer.highlightSelectedModule();
      await loadModule(moduleId);
      await loadModuleRuns(moduleId);
    }

    async function loadModule(moduleId) {
      if (!modules.canWorkWithDbModules(ctx)) return;
      state.currentModule = await fetchJson(`/api/db/modules/${moduleId}`, {}, 'Не удалось загрузить модуль из базы данных.');
      ctx.formController?.restoreExpansionStateForModule(state.currentModule.module.id);
      state.sqlContents = {};
      state.currentModule.module.sqlFiles.forEach(file => {
        state.sqlContents[file.path] = file.content;
      });
      state.persistedConfigText = state.currentModule.module.configText;
      state.persistedSqlContents = modules.cloneSqlContents(state.sqlContents);
      state.persistedModuleMetadata = normalizeModuleMetadata(state.currentModule.module);
      renderer.applyCurrentModule();
      await ctx.formController?.syncFromYaml();
      ctx.sqlCatalogController?.render();
      renderer.renderDraftStatus();
    }

    async function loadModuleRuns(moduleId) {
      if (!modules.canWorkWithDbModules(ctx) || !moduleId) {
        state.currentRuns = [];
        state.selectedRunId = null;
        state.selectedRunDetails = null;
        renderer.renderModuleRuns(loadRunDetails);
        renderer.renderSelectedRunDetails();
        renderer.updateSaveDiscardButtons();
        return;
      }

      try {
        const payload = await fetchJson(`/api/db/modules/${moduleId}/runs`, {}, 'Не удалось загрузить историю запусков модуля.');
        state.currentRuns = Array.isArray(payload.runs) ? payload.runs : [];
      } catch (e) {
        console.error(e);
        state.currentRuns = [];
      }

      if (state.currentRuns.length === 0) {
        state.selectedRunId = null;
        state.selectedRunDetails = null;
        renderer.renderModuleRuns(loadRunDetails);
        renderer.renderSelectedRunDetails();
        renderer.updateSaveDiscardButtons();
        return;
      }

      if (!state.selectedRunId || !state.currentRuns.some(run => run.runId === state.selectedRunId)) {
        state.selectedRunId = state.currentRuns[0].runId;
      }

      await loadRunDetails(moduleId, state.selectedRunId);
    }

    async function loadRunDetails(moduleId, runId) {
      if (!moduleId || !runId || !modules.canWorkWithDbModules(ctx)) {
        state.selectedRunDetails = null;
        renderer.renderSelectedRunDetails();
        renderer.updateSaveDiscardButtons();
        return;
      }

      try {
        state.selectedRunDetails = await fetchJson(
          `/api/db/modules/${moduleId}/runs/${runId}`,
          {},
          'Не удалось загрузить детали запуска.',
        );
      } catch (e) {
        console.error(e);
        state.selectedRunDetails = null;
      }

      renderer.renderModuleRuns(loadRunDetails);
      renderer.renderSelectedRunDetails();
      renderer.updateSaveDiscardButtons();
    }

    async function runSelectedModule() {
      if (!state.selectedModuleId) return;
      if (modules.hasUnsavedChanges(ctx)) {
        alert('Сначала сохрани изменения в личный черновик или отмени их, затем запускай модуль.');
        return;
      }
      try {
        const result = await postJson(
          `/api/db/modules/${state.selectedModuleId}/run`,
          {},
          'Не удалось запустить модуль из базы данных.',
        );
        alert(result.message);
        await loadModuleRuns(state.selectedModuleId);
      } catch (e) {
        console.error(e);
      }
    }

    async function saveWorkingCopy() {
      if (!state.selectedModuleId || !editors.configEditor) return;
      if (!modules.hasUnsavedChanges(ctx)) {
        return;
      }
      await postJson(
        `/api/db/modules/${state.selectedModuleId}/save`,
        {
          configText: editors.configEditor.getValue(),
          sqlFiles: state.sqlContents,
          ...normalizeModuleMetadata(state.currentModule?.module),
        },
        'Не удалось сохранить черновик.',
      );
      await loadModule(state.selectedModuleId);
    }

    async function discardWorkingCopy() {
      if (!state.selectedModuleId) return;
      if (!confirm('Сбросить личный черновик? Несохраненные изменения будут потеряны.')) return;
      await postJson(
        `/api/db/modules/${state.selectedModuleId}/discard-working-copy`,
        {},
        'Не удалось сбросить черновик.',
      );
      await loadModule(state.selectedModuleId);
    }

    async function publishWorkingCopy() {
      if (!state.selectedModuleId) return;
      if (modules.hasUnsavedChanges(ctx)) {
        alert('Сначала сохрани текущие изменения в личный черновик или отмени их.');
        return;
      }
      if (!confirm('Опубликовать черновик как новую ревизию? После публикации личный черновик будет удален.')) return;
      try {
        const result = await postJson(
          `/api/db/modules/${state.selectedModuleId}/publish`,
          {},
          'Не удалось опубликовать черновик.',
        );
        alert(result.message);
        await loadModule(state.selectedModuleId);
        await loadDbModuleCatalog();
      } catch (e) {
        console.error(e);
      }
    }

    async function createModule() {
      global.location.assign('/db-modules/new');
    }

    function openRunHistory() {
      if (!state.selectedModuleId) {
        return;
      }
      const query = new URLSearchParams({
        storage: 'database',
        module: state.selectedModuleId,
      });
      if (state.includeHiddenCatalog) {
        query.set('includeHidden', 'true');
      }
      global.location.assign(`/module-runs?${query.toString()}`);
    }

    async function deleteSelectedModule() {
      if (!state.selectedModuleId) return;
      if (!confirm(`Удалить модуль '${state.selectedModuleId}'? Это действие необратимо.`)) return;
      try {
        const result = await fetch(`/api/db/modules/${state.selectedModuleId}`, {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
        }).then(response => {
          if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
          }
          return response.json();
        });
        alert(result.message);
        state.selectedModuleId = null;
        ctx.persistUiState?.();
        await loadDbModuleCatalog();
      } catch (e) {
        alert(`Ошибка удаления: ${e.message || e}`);
      }
    }

    return {
      refreshDbState,
      loadDbModuleCatalog,
      loadModule,
      loadModuleRuns,
      loadRunDetails,
      runSelectedModule,
      saveWorkingCopy,
      discardWorkingCopy,
      publishWorkingCopy,
      createModule,
      openRunHistory,
      deleteSelectedModule,
    };
  }

  modules.createController = createController;
})(window);
