(function registerDbModulesContext(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};
  const shared = root.shared || {};

  function createPageContext() {
    return {
      common: global.DataPoolCommon || {},
      refs: {
        credentialsStatus: document.getElementById('credentialsStatus'),
        credentialsWarning: document.getElementById('credentialsWarning'),
        credentialsFileInput: document.getElementById('credentialsFileInput'),
        uploadCredentialsButton: document.getElementById('uploadCredentialsButton'),
        dbContextAlert: document.getElementById('dbContextAlert'),
        dbContextAlertTitle: document.getElementById('dbContextAlertTitle'),
        dbContextAlertText: document.getElementById('dbContextAlertText'),
        dbModuleList: document.getElementById('dbModuleList'),
        dbCatalogStatus: document.getElementById('dbCatalogStatus'),
        selectedModuleLabel: document.getElementById('selectedModuleLabel'),
        selectedModuleDescription: document.getElementById('selectedModuleDescription'),
        moduleDraftStatus: document.getElementById('moduleDraftStatus'),
        moduleSourceKind: document.getElementById('moduleSourceKind'),
        moduleValidationAlert: document.getElementById('moduleValidationAlert'),
        moduleMetadata: document.getElementById('moduleMetadata'),
        configForm: document.getElementById('configForm'),
        configFormWarning: document.getElementById('configFormWarning'),
        sqlCatalogList: document.getElementById('sqlCatalogList'),
        sqlCreateButton: document.getElementById('sqlCreateButton'),
        sqlRenameButton: document.getElementById('sqlRenameButton'),
        sqlDeleteButton: document.getElementById('sqlDeleteButton'),
        sqlResourceTitle: document.getElementById('sqlResourceTitle'),
        sqlResourceMeta: document.getElementById('sqlResourceMeta'),
        sqlResourceUsage: document.getElementById('sqlResourceUsage'),
        reloadButton: document.getElementById('reloadButton'),
        runModuleButton: document.getElementById('runModuleButton'),
        saveWorkingCopyButton: document.getElementById('saveWorkingCopyButton'),
        discardWorkingCopyButton: document.getElementById('discardWorkingCopyButton'),
        publishButton: document.getElementById('publishButton'),
        createModuleButton: document.getElementById('createModuleButton'),
        deleteModuleButton: document.getElementById('deleteModuleButton'),
        workingCopyDetails: document.getElementById('workingCopyDetails'),
        dbRunHistoryFilters: document.getElementById('dbRunHistoryFilters'),
        dbRunsList: document.getElementById('dbRunsList'),
        dbRunSummary: document.getElementById('dbRunSummary'),
        dbRunStructuredSummary: document.getElementById('dbRunStructuredSummary'),
        dbRunSummaryJson: document.getElementById('dbRunSummaryJson'),
        dbRunSourceResults: document.getElementById('dbRunSourceResults'),
        dbRunEventTimeline: document.getElementById('dbRunEventTimeline'),
        dbRunArtifacts: document.getElementById('dbRunArtifacts'),
      },
      editors: {
        configEditor: null,
        sqlEditor: null,
      },
      formController: null,
      state: {
        selectedModuleId: null,
        currentModule: null,
        currentSqlPath: null,
        sqlContents: {},
        persistedConfigText: '',
        persistedSqlContents: {},
        runtimeContext: null,
        syncState: null,
        currentRuns: [],
        runHistoryFilter: 'ALL',
        selectedRunId: null,
        selectedRunDetails: null,
      },
    };
  }

  function cloneSqlContents(source) {
    return JSON.parse(JSON.stringify(source || {}));
  }

  function hasUnsavedChanges(ctx) {
    const { state, editors } = ctx;
    if (!state.currentModule || !editors.configEditor) {
      return false;
    }

    if ((state.persistedConfigText || '') !== editors.configEditor.getValue()) {
      return true;
    }

    const currentKeys = Object.keys(state.sqlContents || {}).sort();
    const persistedKeys = Object.keys(state.persistedSqlContents || {}).sort();
    if (JSON.stringify(currentKeys) !== JSON.stringify(persistedKeys)) {
      return true;
    }

    return currentKeys.some(key => (state.sqlContents[key] || '') !== (state.persistedSqlContents[key] || ''));
  }

  function canWorkWithDbModules(ctx) {
    return shared.isDatabaseMode(ctx.state.runtimeContext) && ctx.state.syncState?.maintenanceMode !== true;
  }

  function hasActiveSingleSyncs(ctx) {
    return shared.hasActiveSingleSyncs(ctx.state.syncState);
  }

  function activeSingleSyncFor(ctx, moduleCode) {
    return shared.activeSingleSyncFor(ctx.state.syncState, moduleCode);
  }

  function selectedModuleIsSyncing(ctx) {
    return activeSingleSyncFor(ctx, ctx.state.selectedModuleId) != null;
  }

  function hasRunningDbRun(ctx) {
    return ctx.state.currentRuns.some(run => run.status === 'RUNNING');
  }

  modules.createPageContext = createPageContext;
  modules.canWorkWithDbModules = canWorkWithDbModules;
  modules.hasActiveSingleSyncs = hasActiveSingleSyncs;
  modules.activeSingleSyncFor = activeSingleSyncFor;
  modules.selectedModuleIsSyncing = selectedModuleIsSyncing;
  modules.hasRunningDbRun = hasRunningDbRun;
  modules.cloneSqlContents = cloneSqlContents;
  modules.hasUnsavedChanges = hasUnsavedChanges;
})(window);
