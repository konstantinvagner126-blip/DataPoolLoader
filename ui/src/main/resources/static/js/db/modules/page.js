(function initDbModulesPage(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules || {};
  const common = global.DataPoolCommon || {};
  const uiBlocksNamespace = global.DataPoolUiBlocks || {};
  const uiCredentialsNamespace = global.DataPoolUiCredentials || {};
  const editorBlocksNamespace = global.DataPoolEditorBlocks || {};
  const moduleEditorNamespace = global.DataPoolModuleEditor || {};
  const moduleEditorSharedNamespace = global.DataPoolModuleEditorShared || {};
  const { withMonacoReady, createMonacoEditor, loadJsonStorage, saveJsonStorage } = common;
  const { renderCredentialsPanel } = uiBlocksNamespace;
  const { createCredentialsController } = uiCredentialsNamespace;
  const { renderExecutionLogPanel, renderHistoryCurrentPanel, renderSummaryPanel, renderInfoPanel } = editorBlocksNamespace;
  const { createConfigFormController } = moduleEditorNamespace;
  const { createSqlCatalogController } = moduleEditorSharedNamespace;

  const DB_MODULE_UI_STATE_STORAGE_KEY = 'datapool.dbModuleEditor.uiState';

  renderCredentialsPanel?.(document.getElementById('credentialsPanelHost'));
  renderInfoPanel?.(document.getElementById('dbLifecyclePanelHost'), {
    panelId: 'workingCopyInfo',
    panelClass: 'panel',
    title: 'Информация о личном черновике',
    contentId: 'workingCopyDetails',
    contentClass: 'text-secondary small',
    emptyText: 'Личный черновик пока не загружен.',
  });
  renderExecutionLogPanel?.(document.getElementById('dbExecutionLogPanelHost'), {
    eventLogId: 'dbRunEventTimeline',
  });
  renderHistoryCurrentPanel?.(document.getElementById('dbHistoryCurrentPanelHost'), {
    filtersId: 'dbRunHistoryFilters',
    historyId: 'dbRunsList',
    summaryId: 'dbRunSummary',
    historyTitle: 'Последние запуски',
  });

  const ctx = modules.createPageContext();
  ctx.viewMode = 'compact';
  ctx.historyLimit = 5;
  ctx.eventTimelineLimit = 12;
  ctx.persistUiState = persistDbModuleUiState;
  const renderer = modules.createRenderer(ctx);
  const controller = modules.createController(ctx, renderer);
  ctx.credentialsController = createCredentialsController({
    refs: {
      credentialsStatus: ctx.refs.credentialsStatus,
      credentialsWarning: ctx.refs.credentialsWarning,
      credentialsFileInput: ctx.refs.credentialsFileInput,
    },
    getRequirementTarget: () => ctx.state.currentModule?.module || null,
  });
  ctx.formController = createConfigFormController({
    configForm: ctx.refs.configForm,
    configFormWarning: ctx.refs.configFormWarning,
    getConfigText: () => ctx.editors.configEditor?.getValue() || '',
    setConfigText: value => {
      if (ctx.editors.configEditor) {
        ctx.editors.configEditor.setValue(value);
      }
    },
    getCurrentModuleId: () => ctx.state.currentModule?.module?.id || ctx.state.selectedModuleId,
    getSqlResources: () => ctx.state.currentModule?.module?.sqlFiles || [],
    getStorageMode: () => ctx.state.currentModule?.storageMode || 'DATABASE',
    onDraftStateChange: () => {
      renderer.renderDraftStatus();
      ctx.sqlCatalogController?.render();
    },
    onPersistUiState: () => persistDbModuleUiState(),
    openYamlEditor: () => {
      document.querySelector('[data-bs-target="#configTab"]')?.click();
    },
  });
  ctx.sqlCatalogController = createSqlCatalogController({
    refs: {
      sqlCatalogList: ctx.refs.sqlCatalogList,
      sqlCreateButton: ctx.refs.sqlCreateButton,
      sqlRenameButton: ctx.refs.sqlRenameButton,
      sqlDeleteButton: ctx.refs.sqlDeleteButton,
      sqlResourceTitle: ctx.refs.sqlResourceTitle,
      sqlResourceMeta: ctx.refs.sqlResourceMeta,
      sqlResourceUsage: ctx.refs.sqlResourceUsage,
    },
    editors: ctx.editors,
    getSession: () => ctx.state.currentModule,
    getSqlContents: () => ctx.state.sqlContents,
    setSqlContents: next => {
      ctx.state.sqlContents = next;
    },
    getCurrentPath: () => ctx.state.currentSqlPath,
    setCurrentPath: value => {
      ctx.state.currentSqlPath = value;
    },
    getFormState: () => ctx.formController?.currentFormState?.(),
    formController: ctx.formController,
    onDraftStateChange: () => renderer.renderDraftStatus(),
    onCatalogChanged: () => renderer.renderModuleMetadata(),
  });

  ctx.refs.reloadButton.addEventListener('click', async () => {
    if (!ctx.state.selectedModuleId) return;
    await controller.loadModule(ctx.state.selectedModuleId);
  });

  ctx.refs.uploadCredentialsButton.addEventListener('click', async () => {
    await ctx.credentialsController.uploadSelectedFile();
    await controller.refreshDbState();
    if (modules.canWorkWithDbModules(ctx)) {
      await controller.loadDbModuleCatalog();
      if (ctx.state.selectedModuleId) {
        await controller.loadModule(ctx.state.selectedModuleId);
      }
    }
  });

  ctx.refs.runModuleButton.addEventListener('click', controller.runSelectedModule);
  ctx.refs.historyButton.addEventListener('click', controller.openRunHistory);
  ctx.refs.saveWorkingCopyButton.addEventListener('click', controller.saveWorkingCopy);
  ctx.refs.discardWorkingCopyButton.addEventListener('click', controller.discardWorkingCopy);
  ctx.refs.publishButton.addEventListener('click', controller.publishWorkingCopy);
  ctx.refs.createModuleButton.addEventListener('click', controller.createModule);
  ctx.refs.deleteModuleButton.addEventListener('click', controller.deleteSelectedModule);

  global.addEventListener('pagehide', () => {
    ctx.formController?.saveExpansionStateForCurrentModule();
    persistDbModuleUiState();
  });

  withMonacoReady(async () => {
    restorePersistedDbModuleUiState();

    ctx.editors.configEditor = createMonacoEditor('configEditor', {
      value: '',
      language: 'yaml',
    });
    ctx.editors.configEditor.onDidChangeModelContent(() => {
      if (ctx.formController?.isApplyingFromForm()) {
        return;
      }
      renderer.renderDraftStatus();
      ctx.formController?.scheduleSyncFromYaml();
    });

    ctx.editors.sqlEditor = createMonacoEditor('sqlEditor', {
      value: '',
      language: 'sql',
    });
    ctx.editors.sqlEditor.onDidChangeModelContent(() => {
      if (!ctx.state.currentSqlPath) {
        return;
      }
      ctx.state.sqlContents[ctx.state.currentSqlPath] = ctx.editors.sqlEditor.getValue();
      renderer.renderDraftStatus();
    });

    ctx.formController?.initialize();
    ctx.sqlCatalogController?.render();
    await ctx.credentialsController.refreshStatus();
    await controller.refreshDbState();
    if (modules.canWorkWithDbModules(ctx)) {
      await controller.loadDbModuleCatalog();
    }

    setInterval(async () => {
      const maintenanceWasActive = ctx.state.syncState?.maintenanceMode === true;
      const hadActiveSingleSyncs = modules.hasActiveSingleSyncs(ctx);
      await controller.refreshDbState();
      if (modules.canWorkWithDbModules(ctx) && (maintenanceWasActive || hadActiveSingleSyncs || modules.hasActiveSingleSyncs(ctx))) {
        await controller.loadDbModuleCatalog();
      }
      if (modules.canWorkWithDbModules(ctx) && ctx.state.selectedModuleId && modules.hasRunningDbRun(ctx)) {
        await controller.loadModuleRuns(ctx.state.selectedModuleId);
      }
    }, 5000);
  });

  function restorePersistedDbModuleUiState() {
    const params = new URLSearchParams(global.location.search);
    const requestedModuleId = params.get('module');
    const includeHiddenCatalog = params.get('includeHidden') === '1' || params.get('includeHidden') === 'true';
    const parsed = loadJsonStorage(global.localStorage, DB_MODULE_UI_STATE_STORAGE_KEY, null);
    if (parsed && typeof parsed === 'object') {
      ctx.state.selectedModuleId = typeof parsed.selectedModuleId === 'string' && parsed.selectedModuleId
        ? parsed.selectedModuleId
        : null;
      ctx.formController?.loadSerializedExpansionState(parsed.moduleExpansionState);
    }
    ctx.state.includeHiddenCatalog = includeHiddenCatalog;
    if (requestedModuleId) {
      ctx.state.selectedModuleId = requestedModuleId;
    }
  }

  function persistDbModuleUiState() {
    saveJsonStorage(global.localStorage, DB_MODULE_UI_STATE_STORAGE_KEY, {
      selectedModuleId: ctx.state.selectedModuleId,
      moduleExpansionState: ctx.formController?.serializeExpansionState() || {},
    });
  }
})(window);
