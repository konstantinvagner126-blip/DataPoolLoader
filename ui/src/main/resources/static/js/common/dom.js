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

  function resolveElement(target) {
    if (!target) {
      return null;
    }
    if (typeof target === "string") {
      return global.document.getElementById(target);
    }
    return target;
  }

  function scrollToElement(target, options = {}) {
    const element = resolveElement(target);
    if (!element || typeof element.scrollIntoView !== "function") {
      return;
    }
    element.scrollIntoView({
      behavior: options.behavior || "smooth",
      block: options.block || "start",
    });
  }

  function revealCollapse(target) {
    const element = resolveElement(target);
    if (!element) {
      return;
    }
    const Collapse = global.bootstrap?.Collapse;
    if (Collapse?.getOrCreateInstance) {
      Collapse.getOrCreateInstance(element, { toggle: false }).show();
      return;
    }
    element.classList.add("show");
  }

  namespace.escapeHtml = escapeHtml;
  namespace.scrollToElement = scrollToElement;
  namespace.revealCollapse = revealCollapse;
})(window);
