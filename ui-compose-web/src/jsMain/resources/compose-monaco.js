(function initComposeMonaco(global) {
  const namespace = global.ComposeMonaco || (global.ComposeMonaco = {});
  let monacoConfigured = false;
  const monacoVsPath = "/static/compose-app/vendor/monaco-editor/min/vs";

  function withMonacoReady(callback) {
    const requireFn = global.require;
    if (typeof requireFn !== "function") {
      throw new Error("Monaco loader не загружен.");
    }
    if (!monacoConfigured) {
      requireFn.config({
        paths: { vs: monacoVsPath }
      });
      monacoConfigured = true;
    }
    requireFn(["vs/editor/editor.main"], callback);
  }

  function createMonacoEditor(elementId, options) {
    const element = document.getElementById(elementId);
    if (!element) {
      throw new Error(`Элемент ${elementId} для Monaco не найден.`);
    }
    if (!global.monaco?.editor) {
      throw new Error("Monaco editor не инициализирован.");
    }
    return global.monaco.editor.create(element, {
      theme: "vs",
      automaticLayout: true,
      minimap: { enabled: false },
      ...options
    });
  }

  namespace.withMonacoReady = withMonacoReady;
  namespace.createMonacoEditor = createMonacoEditor;
})(window);
