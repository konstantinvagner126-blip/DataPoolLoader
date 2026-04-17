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

    function canRequestDatabaseMode(context) {
      return context?.database?.available === true;
    }

    function render() {
      if (!indicatorEl || !dotEl || !textEl) {
        return;
      }

      indicatorEl.classList.remove("d-none");

      const isDb = runtimeContext?.effectiveMode === "DATABASE";
      const requestedDb = runtimeContext?.requestedMode === "DATABASE";
      dotEl.className = "db-mode-indicator-dot" + (isDb ? " db-mode-active" : " db-mode-inactive");
      textEl.textContent = `Режим: ${isDb ? "DATABASE" : "FILES"}`;

      if (statusEl) {
        const parts = [];
        parts.push(runtimeContext?.database?.available ? "PostgreSQL доступен" : "PostgreSQL недоступен");
        if (requestedDb !== isDb) {
          parts.push(`запрошен ${requestedDb ? "DATABASE" : "FILES"}`);
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
