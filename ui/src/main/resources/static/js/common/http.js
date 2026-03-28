(function initDataPoolHttp(global) {
  const namespace = global.DataPoolCommon || (global.DataPoolCommon = {});

  async function safeReadJsonError(response, fallbackMessage = "Не удалось выполнить операцию.") {
    try {
      const payload = await response.json();
      return payload.error || fallbackMessage;
    } catch (_) {
      return fallbackMessage;
    }
  }

  async function fetchJson(url, options = {}, fallbackMessage = "Не удалось выполнить запрос.") {
    const response = await fetch(url, options);
    let payload = null;
    try {
      payload = await response.json();
    } catch (_) {
      payload = null;
    }
    if (!response.ok) {
      throw new Error(payload?.error || fallbackMessage);
    }
    return payload;
  }

  function postJson(url, body, fallbackMessage = "Не удалось выполнить запрос.") {
    return fetchJson(
      url,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      },
      fallbackMessage
    );
  }

  function postFormData(url, formData, fallbackMessage = "Не удалось выполнить запрос.") {
    return fetchJson(
      url,
      {
        method: "POST",
        body: formData
      },
      fallbackMessage
    );
  }

  function extractFileName(contentDisposition) {
    if (!contentDisposition) {
      return null;
    }
    const match = /filename="?([^";]+)"?/i.exec(contentDisposition);
    return match ? match[1] : null;
  }

  async function downloadExport(url, payload, fallbackFileName) {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!response.ok) {
      throw new Error(await safeReadJsonError(response));
    }
    const blob = await response.blob();
    const fileName = extractFileName(response.headers.get("content-disposition")) || fallbackFileName;
    const objectUrl = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
  }

  namespace.safeReadJsonError = safeReadJsonError;
  namespace.fetchJson = fetchJson;
  namespace.postJson = postJson;
  namespace.postFormData = postFormData;
  namespace.downloadExport = downloadExport;
  namespace.extractFileName = extractFileName;
})(window);
