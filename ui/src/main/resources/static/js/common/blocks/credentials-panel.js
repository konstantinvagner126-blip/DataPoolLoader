(function registerCredentialsPanelBlock(global) {
  const namespace = global.DataPoolUiBlocks = global.DataPoolUiBlocks || {};

  function renderCredentialsPanel(host, options = {}) {
    if (!host) {
      return;
    }
    const title = options.title || 'credential.properties';
    host.innerHTML = `
      <div class="panel mb-4">
        <div class="d-flex flex-wrap align-items-center justify-content-between gap-3">
          <div>
            <div class="panel-title mb-1">${title}</div>
            <div id="credentialsStatus" class="text-secondary small">Файл не загружен.</div>
          </div>
          <div class="d-flex flex-wrap align-items-center gap-2">
            <input id="credentialsFileInput" class="form-control" type="file" accept=".properties,text/plain">
            <button id="uploadCredentialsButton" class="btn btn-outline-dark">Загрузить файл</button>
          </div>
        </div>
        <div id="credentialsWarning" class="alert alert-warning mt-3 mb-0 d-none"></div>
      </div>
    `;
  }

  namespace.renderCredentialsPanel = renderCredentialsPanel;
})(window);
