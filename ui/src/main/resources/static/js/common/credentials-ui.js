(function registerCredentialsUi(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolUiCredentials = global.DataPoolUiCredentials || {};
  const { fetchJson, postFormData } = common;

  function createCredentialsController(options) {
    const refs = options.refs || {};
    const getRequirementTarget = options.getRequirementTarget || (() => null);
    const onStatusChanged = options.onStatusChanged || (() => {});
    const statusErrorMessage = options.statusErrorMessage || 'Не удалось получить статус credential.properties.';
    const uploadErrorMessage = options.uploadErrorMessage || 'Не удалось загрузить credential.properties.';
    let currentStatus = options.initialStatus || null;

    async function refreshStatus() {
      currentStatus = await fetchJson('/api/credentials', {}, statusErrorMessage);
      renderStatus();
      renderWarning();
      onStatusChanged(currentStatus);
      return currentStatus;
    }

    async function uploadSelectedFile() {
      const file = refs.credentialsFileInput?.files?.[0];
      if (!file) {
        return currentStatus;
      }
      const formData = new FormData();
      formData.append('file', file);
      currentStatus = await postFormData('/api/credentials/upload', formData, uploadErrorMessage);
      renderStatus();
      renderWarning();
      onStatusChanged(currentStatus);
      return currentStatus;
    }

    function setStatus(status) {
      currentStatus = status || null;
      renderStatus();
      renderWarning();
      onStatusChanged(currentStatus);
    }

    function getStatus() {
      return currentStatus;
    }

    function renderStatus() {
      if (!refs.credentialsStatus) {
        return;
      }
      if (!currentStatus) {
        refs.credentialsStatus.textContent = 'Файл не задан.';
        return;
      }
      const sourceLabel = currentStatus.uploaded
        ? 'загружен через UI'
        : (currentStatus.mode === 'FILE' ? 'файл по умолчанию' : 'файл не задан');
      const availability = currentStatus.fileAvailable ? 'доступен' : 'не найден';
      refs.credentialsStatus.textContent = `${sourceLabel}: ${currentStatus.displayName} (${availability})`;
    }

    function renderWarning() {
      if (!refs.credentialsWarning) {
        return;
      }

      const target = unwrapRequirementTarget(getRequirementTarget());
      if (!target) {
        refs.credentialsWarning.classList.add('d-none');
        refs.credentialsWarning.textContent = '';
        return;
      }

      if (!target.requiresCredentials) {
        refs.credentialsWarning.classList.remove('d-none');
        refs.credentialsWarning.className = 'alert alert-light mt-3 mb-0';
        refs.credentialsWarning.textContent = 'У этого модуля нет обязательных placeholders ${...}. credential.properties нужен только для модулей и SQL-источников, где параметры вынесены во внешний файл.';
        return;
      }

      refs.credentialsWarning.classList.remove('d-none');
      if (target.credentialsReady) {
        refs.credentialsWarning.className = 'alert alert-success mt-3 mb-0';
        refs.credentialsWarning.textContent = 'Для модуля все обязательные placeholders ${...} сейчас разрешаются. При необходимости credential.properties можно заменить загрузкой через UI.';
        return;
      }

      refs.credentialsWarning.className = 'alert alert-warning mt-3 mb-0';
      const missingKeys = Array.isArray(target.missingCredentialKeys) ? target.missingCredentialKeys : [];
      const missingKeysText = missingKeys.length ? ` Не хватает значений: ${missingKeys.join(', ')}.` : '';
      if (currentStatus && currentStatus.fileAvailable) {
        refs.credentialsWarning.textContent = `Для модуля найден credential.properties, но обязательные placeholders разрешены не полностью.${missingKeysText} Также проверяются переменные окружения и JVM system properties.`;
        return;
      }
      refs.credentialsWarning.textContent = `В конфиге модуля найдены placeholders \${...}, но подходящие значения сейчас не найдены.${missingKeysText} Сначала ищется ui.defaultCredentialsFile, затем gradle/credential.properties в проекте, затем ~/.gradle/credential.properties. Если значений нет, загрузи файл через UI или задай их через env/JVM.`;
    }

    return {
      refreshStatus,
      uploadSelectedFile,
      setStatus,
      getStatus,
      renderStatus,
      renderWarning,
    };
  }

  function unwrapRequirementTarget(target) {
    if (!target || typeof target !== 'object') {
      return null;
    }
    if (target.module && typeof target.requiresCredentials === 'undefined') {
      return target.module;
    }
    return target;
  }

  namespace.createCredentialsController = createCredentialsController;
})(window);
