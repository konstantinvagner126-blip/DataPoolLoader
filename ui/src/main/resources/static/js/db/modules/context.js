(function registerDbModulesContext(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};
  const shared = root.shared || {};

  function createPageContext() {
    return {
      common: global.DataPoolCommon || {},
      refs: {
        dbContextAlert: document.getElementById('dbContextAlert'),
        dbContextAlertTitle: document.getElementById('dbContextAlertTitle'),
        dbContextAlertText: document.getElementById('dbContextAlertText'),
        dbModuleList: document.getElementById('dbModuleList'),
        dbCatalogStatus: document.getElementById('dbCatalogStatus'),
        selectedModuleLabel: document.getElementById('selectedModuleLabel'),
        selectedModuleDescription: document.getElementById('selectedModuleDescription'),
        moduleSourceKind: document.getElementById('moduleSourceKind'),
        moduleValidationAlert: document.getElementById('moduleValidationAlert'),
        sqlFileSelect: document.getElementById('sqlFileSelect'),
        reloadButton: document.getElementById('reloadButton'),
        runModuleButton: document.getElementById('runModuleButton'),
        saveWorkingCopyButton: document.getElementById('saveWorkingCopyButton'),
        discardWorkingCopyButton: document.getElementById('discardWorkingCopyButton'),
        publishButton: document.getElementById('publishButton'),
        createModuleButton: document.getElementById('createModuleButton'),
        deleteModuleButton: document.getElementById('deleteModuleButton'),
        workingCopyDetails: document.getElementById('workingCopyDetails'),
        dbRunsList: document.getElementById('dbRunsList'),
        dbRunDetailsEmpty: document.getElementById('dbRunDetailsEmpty'),
        dbRunDetails: document.getElementById('dbRunDetails'),
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
      state: {
        selectedModuleId: null,
        currentModule: null,
        currentSqlPath: null,
        sqlContents: {},
        runtimeContext: null,
        syncState: null,
        currentRuns: [],
        selectedRunId: null,
        selectedRunDetails: null,
      },
    };
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
})(window);
