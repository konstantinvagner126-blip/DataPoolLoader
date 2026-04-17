(function registerDbRuntimeState(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const shared = root.shared = root.shared || {};

  function isDatabaseMode(runtimeContext) {
    return String(runtimeContext?.effectiveMode || '').toLowerCase() === 'database';
  }

  function activeSingleSyncs(syncState) {
    return Array.isArray(syncState?.activeSingleSyncs) ? syncState.activeSingleSyncs : [];
  }

  function hasActiveSingleSyncs(syncState) {
    return activeSingleSyncs(syncState).length > 0;
  }

  function activeSingleSyncFor(syncState, moduleCode) {
    if (!moduleCode) {
      return null;
    }
    return activeSingleSyncs(syncState).find(item => item.moduleCode === moduleCode) || null;
  }

  function describeActiveSingleSync(activeSync) {
    if (!activeSync) {
      return '';
    }
    const actorName = activeSync.startedByActorDisplayName || activeSync.startedByActorId || 'неизвестным пользователем';
    const moduleCode = activeSync.moduleCode || 'неизвестный модуль';
    const startedAt = activeSync.startedAt ? new Date(activeSync.startedAt).toLocaleString() : null;
    return `Модуль ${moduleCode} импортируется пользователем ${actorName}${startedAt ? ` с ${startedAt}` : ''}.`;
  }

  shared.isDatabaseMode = isDatabaseMode;
  shared.activeSingleSyncs = activeSingleSyncs;
  shared.hasActiveSingleSyncs = hasActiveSingleSyncs;
  shared.activeSingleSyncFor = activeSingleSyncFor;
  shared.describeActiveSingleSync = describeActiveSingleSync;
})(window);
