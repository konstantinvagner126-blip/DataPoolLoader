(function initRuntimeMode(global) {
  const common = global.DataPoolCommon || {};
  const { fetchJson, postJson } = common;

  function createRuntimeModeController({
    indicatorEl,
    dotEl,
    textEl,
    statusEl = null,
    toggleEl = null,
    onContextChanged = null,
  }) {
    let runtimeContext = null;
    let pending = false;

    function normalizedMode(mode) {
      return String(mode || "").trim().toLowerCase();
    }

    function canRequestDatabaseMode(context) {
      return context?.database?.available === true;
    }

    function render() {
      if (!indicatorEl || !dotEl || !textEl) {
        return;
      }

      indicatorEl.classList.remove("d-none");

      const isDb = normalizedMode(runtimeContext?.effectiveMode) === "database";
      const requestedDb = normalizedMode(runtimeContext?.requestedMode) === "database";
      dotEl.className = "db-mode-indicator-dot" + (isDb ? " db-mode-active" : " db-mode-inactive");
      textEl.textContent = `Режим: ${isDb ? "База данных" : "Файлы"}`;

      if (statusEl) {
        const parts = [];
        parts.push(runtimeContext?.database?.available ? "PostgreSQL доступен" : "PostgreSQL недоступен");
        if (requestedDb !== isDb) {
          parts.push(`запрошен режим ${requestedDb ? "«База данных»" : "«Файлы»"}`);
        }
        statusEl.textContent = parts.join(" · ");
      }

      if (toggleEl) {
        toggleEl.checked = requestedDb;
        toggleEl.disabled = pending || (!canRequestDatabaseMode(runtimeContext) && !requestedDb);
      }
    }

    async function notifyContextChanged() {
      if (typeof onContextChanged === "function") {
        await onContextChanged(runtimeContext);
      }
    }

    async function loadContext() {
      runtimeContext = await fetchJson("/api/ui/runtime-context", {}, "Не удалось загрузить состояние режима UI.");
      render();
      await notifyContextChanged();
      return runtimeContext;
    }

    async function setMode(mode) {
      pending = true;
      render();
      try {
        const response = await postJson(
          "/api/ui/runtime-mode",
          { mode },
          "Не удалось переключить режим UI."
        );
        runtimeContext = response.runtimeContext;
        render();
        await notifyContextChanged();
        return runtimeContext;
      } finally {
        pending = false;
        render();
      }
    }

    toggleEl?.addEventListener("change", async () => {
      const nextMode = toggleEl.checked ? "database" : "files";
      try {
        await setMode(nextMode);
      } catch (error) {
        alert(error.message || "Не удалось переключить режим UI.");
        await loadContext();
      }
    });

    return {
      getContext() {
        return runtimeContext;
      },
      loadContext,
      render,
      async setContext(context) {
        runtimeContext = context;
        render();
        await notifyContextChanged();
      },
      setMode,
    };
  }

  global.DataPoolRuntimeMode = {
    createRuntimeModeController,
  };
})(window);
