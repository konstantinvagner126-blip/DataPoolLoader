(function initDataPoolDom(global) {
  const namespace = global.DataPoolCommon || (global.DataPoolCommon = {});

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll("\"", "&quot;")
      .replaceAll("'", "&#39;");
  }

  namespace.escapeHtml = escapeHtml;
})(window);
