(function registerDbModulesController(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};

  function createController(ctx, renderer) {
    const { fetchJson, postJson } = ctx.common;
    const { state, editors } = ctx;

    async function loadRuntimeContext() {
      try {
        state.runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить runtime context.');
      } catch (e) {
        console.error(e);
        renderer.showDbContextAlert('Не удалось определить состояние DB-режима.');
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
        renderer.renderUnavailableCatalogState('Каталог DB-модулей временно недоступен.');
        return;
      }

      try {
        const payload = await fetchJson('/api/db/modules/catalog', {}, 'Не удалось загрузить каталог DB-модулей.');
        const modulesList = Array.isArray(payload.modules) ? payload.modules : [];

        if (modulesList.length === 0) {
          renderer.resetSelectedModuleState();
          ctx.refs.dbModuleList.innerHTML = `
            <div class="list-group-item text-secondary">
              DB-модули не найдены. Импортируйте файловые модули или создайте новый модуль.
            </div>
          `;
          return;
        }

        renderer.renderCatalog(modulesList, selectModule);

        const selectedModuleExists = modulesList.some(module => module.id === state.selectedModuleId);
        if (selectedModuleExists) {
          renderer.highlightSelectedModule();
          renderer.updateSaveDiscardButtons();
          return;
        }

        await selectModule(modulesList[0].id);
      } catch (e) {
        console.error(e);
        ctx.refs.dbCatalogStatus.className = 'module-catalog-status mt-3 mb-3 text-danger small';
        ctx.refs.dbCatalogStatus.textContent = e.message || 'Не удалось загрузить каталог DB-модулей.';
      }
    }

    async function selectModule(moduleId) {
      if (!modules.canWorkWithDbModules(ctx)) return;
      if (state.selectedModuleId !== moduleId) {
        state.selectedRunId = null;
        state.selectedRunDetails = null;
      }
      state.selectedModuleId = moduleId;
      renderer.highlightSelectedModule();
      await loadModule(moduleId);
      await loadModuleRuns(moduleId);
    }

    async function loadModule(moduleId) {
      if (!modules.canWorkWithDbModules(ctx)) return;
      state.currentModule = await fetchJson(`/api/db/modules/${moduleId}`, {}, 'Не удалось загрузить DB-модуль.');
      state.sqlContents = {};
      state.currentModule.module.sqlFiles.forEach(file => {
        state.sqlContents[file.path] = file.content;
      });
      renderer.applyCurrentModule();
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
        const payload = await fetchJson(`/api/db/modules/${moduleId}/runs`, {}, 'Не удалось загрузить историю DB-запусков.');
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
          'Не удалось загрузить детали DB-запуска.',
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
      try {
        const result = await postJson(
          `/api/db/modules/${state.selectedModuleId}/run`,
          {},
          'Не удалось запустить DB-модуль.',
        );
        alert(result.message);
        await loadModuleRuns(state.selectedModuleId);
      } catch (e) {
        console.error(e);
      }
    }

    async function saveWorkingCopy() {
      if (!state.selectedModuleId || !editors.configEditor) return;
      await postJson(
        `/api/db/modules/${state.selectedModuleId}/save`,
        {
          configText: editors.configEditor.getValue(),
          sqlFiles: state.sqlContents,
        },
        'Не удалось сохранить working copy.',
      );
      await loadModule(state.selectedModuleId);
    }

    async function discardWorkingCopy() {
      if (!state.selectedModuleId) return;
      if (!confirm('Удалить личную working copy? Это действие нельзя отменить.')) return;
      await postJson(
        `/api/db/modules/${state.selectedModuleId}/discard-working-copy`,
        {},
        'Не удалось удалить working copy.',
      );
      await loadModule(state.selectedModuleId);
    }

    async function publishWorkingCopy() {
      if (!state.selectedModuleId) return;
      if (!confirm('Опубликовать working copy как новую ревизию? Working copy будет удалена.')) return;
      try {
        const result = await postJson(
          `/api/db/modules/${state.selectedModuleId}/publish`,
          {},
          'Не удалось опубликовать working copy.',
        );
        alert(result.message);
        await loadModule(state.selectedModuleId);
        await loadDbModuleCatalog();
      } catch (e) {
        console.error(e);
      }
    }

    async function createModule() {
      const moduleCode = prompt('Введите код модуля (уникальный идентификатор):');
      if (!moduleCode || !moduleCode.trim()) return;

      const title = prompt('Введите название модуля:', moduleCode) || moduleCode;

      try {
        const result = await postJson(
          '/api/db/modules',
          {
            moduleCode: moduleCode.trim(),
            title,
            description: '',
            tags: [],
            configText: `app:\n  title: ${title}\n  sources: []\n`,
          },
          'Не удалось создать модуль.',
        );
        alert(result.message);
        await loadDbModuleCatalog();
      } catch (e) {
        console.error(e);
      }
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
        await loadDbModuleCatalog();
      } catch (e) {
        alert(`Ошибка удаления: ${e.message || e}`);
      }
    }

    function updateSqlSelection(path) {
      state.currentSqlPath = path || null;
      if (editors.sqlEditor) {
        editors.sqlEditor.setValue(state.currentSqlPath ? (state.sqlContents[state.currentSqlPath] || '') : '');
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
      deleteSelectedModule,
      updateSqlSelection,
    };
  }

  modules.createController = createController;
})(window);
