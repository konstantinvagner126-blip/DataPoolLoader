(function initDbModulesPage(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules || {};
  const { withMonacoReady, createMonacoEditor } = global.DataPoolCommon || {};

  const ctx = modules.createPageContext();
  const renderer = modules.createRenderer(ctx);
  const controller = modules.createController(ctx, renderer);

  ctx.refs.reloadButton.addEventListener('click', async () => {
    if (!ctx.state.selectedModuleId) return;
    await controller.loadModule(ctx.state.selectedModuleId);
  });

  ctx.refs.runModuleButton.addEventListener('click', controller.runSelectedModule);
  ctx.refs.saveWorkingCopyButton.addEventListener('click', controller.saveWorkingCopy);
  ctx.refs.discardWorkingCopyButton.addEventListener('click', controller.discardWorkingCopy);
  ctx.refs.publishButton.addEventListener('click', controller.publishWorkingCopy);
  ctx.refs.createModuleButton.addEventListener('click', controller.createModule);
  ctx.refs.deleteModuleButton.addEventListener('click', controller.deleteSelectedModule);

  ctx.refs.sqlFileSelect.addEventListener('change', () => {
    controller.updateSqlSelection(ctx.refs.sqlFileSelect.value || null);
  });

  withMonacoReady(async () => {
    ctx.editors.configEditor = createMonacoEditor('configEditor', {
      value: '',
      language: 'yaml',
    });

    ctx.editors.sqlEditor = createMonacoEditor('sqlEditor', {
      value: '',
      language: 'sql',
    });

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
})(window);
