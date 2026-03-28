(function initDataPoolMonaco(global) {
  const namespace = global.DataPoolCommon || (global.DataPoolCommon = {});
  let monacoConfigured = false;

  function withMonacoReady(callback) {
    const requireFn = global.require;
    if (typeof requireFn !== "function") {
      throw new Error("Monaco loader не загружен.");
    }
    if (!monacoConfigured) {
      requireFn.config({
        paths: { vs: "https://cdnjs.cloudflare.com/ajax/libs/monaco-editor/0.52.2/min/vs" }
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
