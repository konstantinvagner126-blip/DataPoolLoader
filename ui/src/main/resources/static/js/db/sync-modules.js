(function initDbSyncPage() {
  const { fetchJson, postJson, escapeHtml } = window.DataPoolCommon || {};

  const dbModeIndicator = document.getElementById('dbModeIndicator');
  const dbModeDot = document.getElementById('dbModeDot');
  const dbModeText = document.getElementById('dbModeText');
  const dbContextAlert = document.getElementById('dbContextAlert');
  const syncAllButton = document.getElementById('syncAllButton');
  const syncOneButton = document.getElementById('syncOneButton');
  const syncOneModuleInput = document.getElementById('syncOneModuleInput');
  const syncOneModuleCode = document.getElementById('syncOneModuleCode');
  const syncOneConfirmButton = document.getElementById('syncOneConfirmButton');
  const syncResultPanel = document.getElementById('syncResultPanel');
  const syncResultSummary = document.getElementById('syncResultSummary');
  const syncResultItems = document.getElementById('syncResultItems');

  let runtimeContext = null;

  syncAllButton.addEventListener('click', async () => {
    if (!confirm('Синхронизировать все файловые модули в PostgreSQL?')) return;
    await syncAll();
  });

  syncOneButton.addEventListener('click', () => {
    syncOneModuleInput.classList.toggle('d-none');
    if (!syncOneModuleInput.classList.contains('d-none')) {
      syncOneModuleCode.focus();
    }
  });

  syncOneConfirmButton.addEventListener('click', async () => {
    const moduleCode = syncOneModuleCode.value?.trim();
    if (!moduleCode) {
      alert('Введите код модуля.');
      return;
    }
    await syncOne(moduleCode);
  });

  syncOneModuleCode.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') {
      syncOneConfirmButton.click();
    }
  });

  async function loadRuntimeContext() {
    try {
      runtimeContext = await fetchJson('/api/ui/runtime-context', {}, 'Не удалось загрузить runtime context.');
      renderRuntimeContext();
    } catch (e) {
      console.error(e);
    }
  }

  function renderRuntimeContext() {
    dbModeIndicator.classList.remove('d-none');
    const isDb = runtimeContext.effectiveMode === 'DATABASE';
    dbModeDot.className = 'db-mode-indicator-dot' + (isDb ? ' db-mode-active' : ' db-mode-inactive');
    dbModeText.textContent = `Режим: ${isDb ? 'DATABASE' : 'FILES'}`;

    if (!isDb) {
      dbContextAlert.classList.remove('d-none');
      syncAllButton.disabled = true;
      syncOneButton.disabled = true;
    } else {
      dbContextAlert.classList.add('d-none');
      syncAllButton.disabled = false;
      syncOneButton.disabled = false;
    }
  }

  async function syncAll() {
    syncAllButton.disabled = true;
    syncAllButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Синхронизация...';

    try {
      const result = await postJson('/api/db/sync/all', {}, 'Не удалось синхронизировать модули.');
      renderSyncResult(result);
    } catch (e) {
      alert('Ошибка синхронизации: ' + (e.message || e));
    } finally {
      syncAllButton.disabled = false;
      syncAllButton.innerHTML = `
        <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-arrow-down-up" viewBox="0 0 16 16">
          <path fill-rule="evenodd" d="M11.5 15a.5.5 0 0 0 .5-.5V2.707l3.146 3.147a.5.5 0 0 0 .708-.708l-4-4a.5.5 0 0 0-.708 0l-4 4a.5.5 0 1 0 .708.708L11 2.707V14.5a.5.5 0 0 0 .5.5zm-7-14a.5.5 0 0 1 .5.5v11.793l3.146-3.147a.5.5 0 0 1 .708.708l-4 4a.5.5 0 0 1-.708 0l-4-4a.5.5 0 0 1 .708-.708L4 13.293V1.5a.5.5 0 0 1 .5-.5z"/>
        </svg>
        Синхронизировать все модули
      `;
    }
  }

  async function syncOne(moduleCode) {
    syncOneConfirmButton.disabled = true;
    syncOneConfirmButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Синхронизация...';

    try {
      const result = await postJson('/api/db/sync/one', { moduleCode: moduleCode }, 'Не удалось синхронизировать модуль.');
      renderSyncResult(result);
    } catch (e) {
      alert('Ошибка синхронизации: ' + (e.message || e));
    } finally {
      syncOneConfirmButton.disabled = false;
      syncOneConfirmButton.textContent = 'Синхронизировать';
    }
  }

  function renderSyncResult(result) {
    syncResultPanel.classList.remove('d-none');

    const statusBadge = result.status === 'SUCCESS' ? 'bg-success' : (result.status === 'FAILED' ? 'bg-danger' : 'bg-warning');
    syncResultSummary.innerHTML = `
      <div class="d-flex flex-wrap gap-3 mb-2">
        <span class="badge ${statusBadge}">${result.status}</span>
        <span>Обработано: <strong>${result.totalProcessed}</strong></span>
        <span>Создано: <strong>${result.totalCreated}</strong></span>
        <span>Обновлено: <strong>${result.totalUpdated}</strong></span>
        <span>Пропущено: <strong>${result.totalSkipped}</strong></span>
        <span>Ошибка: <strong>${result.totalFailed}</strong></span>
      </div>
      <div class="small text-secondary">
        Scope: ${result.scope} | Started: ${new Date(result.startedAt).toLocaleString()} | Finished: ${new Date(result.finishedAt).toLocaleString()}
      </div>
    `;

    syncResultItems.innerHTML = result.items.map(item => {
      const actionBadge = item.action === 'CREATED' ? 'bg-success' :
        (item.action === 'UPDATED' ? 'bg-primary' :
          (item.action.startsWith('SKIPPED') ? 'bg-secondary' : 'bg-danger'));
      return `
        <div class="card mb-2">
          <div class="card-body py-2 px-3">
            <div class="d-flex flex-wrap align-items-center gap-2">
              <code class="fw-semibold">${escapeHtml(item.moduleCode)}</code>
              <span class="badge ${actionBadge}">${item.action}</span>
              ${item.errorMessage ? `<span class="text-danger small">${escapeHtml(item.errorMessage)}</span>` : ''}
              ${item.resultRevisionId ? `<span class="small text-secondary">revision: ${escapeHtml(item.resultRevisionId)}</span>` : ''}
            </div>
          </div>
        </div>
      `;
    }).join('');
  }

  loadRuntimeContext();
})();
