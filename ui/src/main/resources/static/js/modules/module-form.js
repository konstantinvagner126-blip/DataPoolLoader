(function initDataPoolModuleForm(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolModuleEditor || (global.DataPoolModuleEditor = {});
  const { escapeHtml, postJson } = common;

  function buildEmptySourceState() {
    return {
      name: "",
      jdbcUrl: "",
      username: "",
      password: "",
      sql: "",
      sqlFile: ""
    };
  }

  function buildEmptyQuotaState() {
    return {
      source: "",
      percent: ""
    };
  }

  function buildDefaultFormState() {
    return {
      outputDir: "./output",
      fileFormat: "csv",
      mergeMode: "plain",
      errorMode: "continue_on_error",
      parallelism: 5,
      fetchSize: 1000,
      queryTimeoutSec: "",
      progressLogEveryRows: 10000,
      maxMergedRows: "",
      deleteOutputFilesAfterCompletion: false,
      commonSql: "",
      commonSqlFile: "",
      sources: [buildEmptySourceState()],
      quotas: [],
      targetEnabled: true,
      targetJdbcUrl: "",
      targetUsername: "",
      targetPassword: "",
      targetTable: "",
      targetTruncateBeforeLoad: false
    };
  }

  function defaultExpandedConfigCards() {
    return {
      general: true,
      sql: true,
      sources: true,
      quotas: true,
      target: true
    };
  }

  function reindexExpandedSet(sourceSet, removedIndex) {
    const next = new Set();
    [...sourceSet].forEach(index => {
      if (index === removedIndex) {
        return;
      }
      next.add(index > removedIndex ? index - 1 : index);
    });
    return next;
  }

  function createConfigFormController({
    configForm,
    configFormWarning,
    getConfigText,
    setConfigText,
    getCurrentModuleId,
    getSqlResources,
    getStorageMode,
    onDraftStateChange,
    onPersistUiState,
    openYamlEditor
  }) {
    let isApplyingConfigFromForm = false;
    let isUpdatingFormFromYaml = false;
    let configSyncDebounceId = null;
    let configSyncRequestId = 0;
    let configApplyRequestId = 0;
    let activeConfigSectionId = "configSectionGeneral";
    let expandedConfigCards = defaultExpandedConfigCards();
    let expandedSourceCards = new Set();
    let expandedQuotaCards = new Set();
    let lastFormState = buildDefaultFormState();
    const moduleExpansionState = new Map();

    function notifyStateChange() {
      onPersistUiState?.();
    }

    function initialize() {
      lastFormState = buildDefaultFormState();
      renderConfigForm(lastFormState);
    }

    function isApplyingFromForm() {
      return isApplyingConfigFromForm;
    }

    function loadSerializedExpansionState(serializedState) {
      moduleExpansionState.clear();
      if (!serializedState || typeof serializedState !== "object") {
        return;
      }
      Object.entries(serializedState).forEach(([moduleId, expansionState]) => {
        if (!moduleId || !expansionState || typeof expansionState !== "object") {
          return;
        }
        moduleExpansionState.set(moduleId, {
          configCards: { ...defaultExpandedConfigCards(), ...(expansionState.configCards || {}) },
          sourceCards: Array.isArray(expansionState.sourceCards) ? expansionState.sourceCards : [],
          quotaCards: Array.isArray(expansionState.quotaCards) ? expansionState.quotaCards : []
        });
      });
    }

    function serializeExpansionState() {
      const serialized = {};
      moduleExpansionState.forEach((expansionState, moduleId) => {
        serialized[moduleId] = {
          configCards: { ...defaultExpandedConfigCards(), ...(expansionState.configCards || {}) },
          sourceCards: Array.isArray(expansionState.sourceCards) ? expansionState.sourceCards : [],
          quotaCards: Array.isArray(expansionState.quotaCards) ? expansionState.quotaCards : []
        };
      });
      return serialized;
    }

    function restoreExpansionStateForModule(moduleId) {
      const saved = moduleExpansionState.get(moduleId);
      if (!saved) {
        expandedConfigCards = defaultExpandedConfigCards();
        expandedSourceCards = new Set();
        expandedQuotaCards = new Set();
        return;
      }
      expandedConfigCards = {
        ...defaultExpandedConfigCards(),
        ...(saved.configCards || {})
      };
      expandedSourceCards = new Set(Array.isArray(saved.sourceCards) ? saved.sourceCards : []);
      expandedQuotaCards = new Set(Array.isArray(saved.quotaCards) ? saved.quotaCards : []);
    }

    function syncExpansionStateFromDom() {
      if (!configForm || !configForm.children.length) {
        return;
      }
      const nextConfigCards = { ...defaultExpandedConfigCards(), ...expandedConfigCards };
      configForm.querySelectorAll("[data-config-card-key]").forEach(details => {
        nextConfigCards[details.dataset.configCardKey] = details.open;
      });
      expandedConfigCards = nextConfigCards;

      expandedSourceCards = new Set(
        [...configForm.querySelectorAll("[data-config-source-card-index]")]
          .filter(details => details.open)
          .map(details => Number(details.dataset.configSourceCardIndex))
          .filter(index => !Number.isNaN(index))
      );

      expandedQuotaCards = new Set(
        [...configForm.querySelectorAll("[data-config-quota-card-index]")]
          .filter(details => details.open)
          .map(details => Number(details.dataset.configQuotaCardIndex))
          .filter(index => !Number.isNaN(index))
      );
    }

    function saveExpansionStateForCurrentModule() {
      const moduleId = getCurrentModuleId?.();
      if (!moduleId) {
        return;
      }
      syncExpansionStateFromDom();
      moduleExpansionState.set(moduleId, {
        configCards: { ...expandedConfigCards },
        sourceCards: [...expandedSourceCards].sort((a, b) => a - b),
        quotaCards: [...expandedQuotaCards].sort((a, b) => a - b)
      });
      notifyStateChange();
    }

    function scheduleSyncFromYaml() {
      if (isUpdatingFormFromYaml) {
        return;
      }
      global.clearTimeout(configSyncDebounceId);
      configSyncDebounceId = global.setTimeout(() => {
        syncFromYaml();
      }, 120);
    }

    async function syncFromYaml() {
      if (isUpdatingFormFromYaml) {
        return;
      }

      const requestId = ++configSyncRequestId;
      try {
        const formState = await postJson(
          "/api/config-form/parse",
          { configText: getConfigText() },
          "Не удалось разобрать application.yml для визуальной формы."
        );
        if (requestId !== configSyncRequestId) {
          return;
        }
        lastFormState = formState;
        renderConfigForm(formState);
        setFormWarning(null);
        onDraftStateChange?.();
      } catch (error) {
        if (requestId !== configSyncRequestId) {
          return;
        }
        renderConfigForm(buildDefaultFormState(), true);
        setFormWarning(error.message || "Не удалось разобрать application.yml для визуальной формы.");
        onDraftStateChange?.();
      }
    }

    function renderConfigForm(state, disabled = false) {
      lastFormState = JSON.parse(JSON.stringify(state));
      const currentActiveSection = configForm.querySelector(".config-section-tabs .nav-link.active")?.dataset.configSectionTarget;
      if (currentActiveSection) {
        activeConfigSectionId = currentActiveSection;
      }
      const sqlResources = normalizeSqlResources(getSqlResources?.());
      const storageMode = String(getStorageMode?.() || "FILES").toUpperCase();
      const defaultSqlState = buildDefaultSqlState(state, sqlResources);

      if (disabled) {
        configForm.innerHTML = `
          <div class="config-form-empty-state">
            <div class="config-form-empty-title">Визуальная форма временно недоступна</div>
            <div class="config-form-empty-text">
              Исправь структуру или синтаксис <code>application.yml</code>, после чего форма снова соберется автоматически.
            </div>
            <button class="btn btn-outline-primary" type="button" data-open-yaml-editor="true">Перейти к application.yml</button>
          </div>
        `;
        configForm.querySelector("[data-open-yaml-editor='true']")?.addEventListener("click", () => {
          openYamlEditor?.();
        });
        return;
      }

      configForm.innerHTML = `
        <ul class="nav nav-pills config-section-tabs mb-3" role="tablist">
          <li class="nav-item" role="presentation">
            <button class="nav-link ${activeConfigSectionId === "configSectionGeneral" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionGeneral" data-config-section-target="configSectionGeneral" type="button">Общие</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link ${activeConfigSectionId === "configSectionSql" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionSql" data-config-section-target="configSectionSql" type="button">SQL</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link ${activeConfigSectionId === "configSectionSources" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionSources" data-config-section-target="configSectionSources" type="button">Источники</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link ${activeConfigSectionId === "configSectionQuotas" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionQuotas" data-config-section-target="configSectionQuotas" type="button">Квоты</button>
          </li>
          <li class="nav-item" role="presentation">
            <button class="nav-link ${activeConfigSectionId === "configSectionTarget" ? "active" : ""}" data-bs-toggle="pill" data-bs-target="#configSectionTarget" data-config-section-target="configSectionTarget" type="button">Target</button>
          </li>
        </ul>

        <div class="tab-content config-section-content">
          <div class="tab-pane fade ${activeConfigSectionId === "configSectionGeneral" ? "show active" : ""}" id="configSectionGeneral">
            ${renderCollapsibleConfigCard("general", "Общие настройки", expandedConfigCards.general !== false, `
              <div class="config-form-fields">
                ${renderTextField("outputDir", "Каталог output", state.outputDir, disabled, "Обычно оставляем относительный путь ./output.")}
                ${renderSelectField("fileFormat", "Формат файла", state.fileFormat, [["csv", "csv"]], disabled, "Сейчас поддерживается только CSV.")}
                ${renderSelectField("mergeMode", "Режим merge", state.mergeMode, [
                  ["plain", "plain"],
                  ["round_robin", "round_robin"],
                  ["proportional", "proportional"],
                  ["quota", "quota"]
                ], disabled, "Как объединять данные из source БД.")}
                ${renderSelectField("errorMode", "Режим ошибок", state.errorMode, [["continue_on_error", "continue_on_error"]], disabled, "Сейчас поддерживается продолжение обработки при ошибке источника.")}
                ${renderNumberField("parallelism", "Параллелизм", state.parallelism, disabled, false, "Сколько source обрабатывать одновременно.")}
                ${renderNumberField("fetchSize", "Fetch size", state.fetchSize, disabled, false, "Размер JDBC-порции при чтении результата.")}
                ${renderNumberField("queryTimeoutSec", "Query timeout (сек)", state.queryTimeoutSec, disabled, true, "Если пусто, явный timeout не задается.")}
                ${renderNumberField("progressLogEveryRows", "Логировать каждые N строк", state.progressLogEveryRows, disabled, false, "Частота progress-логов по source.")}
                ${renderNumberField("maxMergedRows", "Максимум строк в merged", state.maxMergedRows, disabled, true, "Если пусто, итоговый файл не ограничивается.")}
                ${renderCheckboxField("deleteOutputFilesAfterCompletion", "Удалять выходные файлы после завершения", state.deleteOutputFilesAfterCompletion, disabled, "Оставить только summary и результат загрузки.")}
              </div>
            `)}
          </div>

          <div class="tab-pane fade ${activeConfigSectionId === "configSectionSql" ? "show active" : ""}" id="configSectionSql">
            ${renderCollapsibleConfigCard("sql", "SQL по умолчанию", expandedConfigCards.sql !== false, `
              <div class="config-form-fields">
                ${renderDefaultSqlEditor(defaultSqlState, sqlResources, storageMode, disabled)}
              </div>
            `)}
          </div>

          <div class="tab-pane fade ${activeConfigSectionId === "configSectionSources" ? "show active" : ""}" id="configSectionSources">
            ${renderCollapsibleConfigCard("sources", "Источники БД", expandedConfigCards.sources !== false, `
              <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
                <button class="btn btn-outline-primary btn-sm" type="button" data-config-action="add-source" ${disabled ? "disabled" : ""}>Добавить source</button>
                <div class="config-form-help ms-auto">${state.sources.length} шт.</div>
              </div>
              <div class="config-form-list">
                ${state.sources.map((source, index) => renderSourceCard(source, index, disabled, expandedSourceCards.has(index), sqlResources, storageMode)).join("")}
              </div>
            `)}
          </div>

          <div class="tab-pane fade ${activeConfigSectionId === "configSectionQuotas" ? "show active" : ""}" id="configSectionQuotas">
            ${renderCollapsibleConfigCard("quotas", "Квоты", expandedConfigCards.quotas !== false, `
              <div class="d-flex align-items-center justify-content-between gap-3 mb-3">
                <div>
                  <div class="config-form-help">Используются в режиме <code>quota</code>.</div>
                </div>
                <button class="btn btn-outline-primary btn-sm" type="button" data-config-action="add-quota" ${disabled ? "disabled" : ""}>Добавить quota</button>
                <div class="config-form-help ms-auto">${state.quotas.length} шт.</div>
              </div>
              <div class="config-form-list">
                ${state.quotas.length > 0 ? state.quotas.map((quota, index) => renderQuotaRow(quota, index, state.sources, disabled, expandedQuotaCards.has(index))).join("") : `
                  <div class="config-form-inline-note">Квоты не заданы.</div>
                `}
              </div>
            `)}
          </div>

          <div class="tab-pane fade ${activeConfigSectionId === "configSectionTarget" ? "show active" : ""}" id="configSectionTarget">
            ${renderCollapsibleConfigCard("target", "Target", expandedConfigCards.target !== false, `
              <div class="config-form-fields">
                ${renderRadioGroupField("targetEnabled", "Загрузка в target БД", state.targetEnabled ? "true" : "false", [
                  ["true", "Включена"],
                  ["false", "Выключена"]
                ], disabled, "Если выключено, merged.csv формируется, но загрузка в БД не выполняется.")}
                ${renderTextField("targetJdbcUrl", "jdbcUrl", state.targetJdbcUrl, disabled, "Подключение к target PostgreSQL.")}
                ${renderTextField("targetUsername", "username", state.targetUsername, disabled, "Пользователь target БД.")}
                ${renderTextField("targetPassword", "password", state.targetPassword, disabled, "Пароль target БД.")}
                ${renderTextField("targetTable", "Целевая таблица", state.targetTable, disabled, "Например schema.table.")}
                ${renderCheckboxField("targetTruncateBeforeLoad", "Очищать target таблицу перед загрузкой", state.targetTruncateBeforeLoad, disabled, "Перед импортом выполнить TRUNCATE TABLE.")}
              </div>
            `)}
          </div>
        </div>
      `;

      bindConfigFormEvents(disabled);
    }

    function bindConfigFormEvents(disabled) {
      if (disabled) {
        return;
      }

      configForm.querySelectorAll("[data-config-section-target]").forEach(button => {
        button.addEventListener("click", () => {
          activeConfigSectionId = button.dataset.configSectionTarget || "configSectionGeneral";
        });
      });

      configForm.querySelectorAll("[data-config-card-key]").forEach(details => {
        details.addEventListener("toggle", () => {
          expandedConfigCards[details.dataset.configCardKey] = details.open;
          saveExpansionStateForCurrentModule();
        });
      });

      configForm.querySelectorAll("[data-config-source-card-index]").forEach(details => {
        details.addEventListener("toggle", () => {
          const index = Number(details.dataset.configSourceCardIndex);
          if (details.open) {
            expandedSourceCards.add(index);
          } else {
            expandedSourceCards.delete(index);
          }
          saveExpansionStateForCurrentModule();
        });
      });

      configForm.querySelectorAll("[data-config-quota-card-index]").forEach(details => {
        details.addEventListener("toggle", () => {
          const index = Number(details.dataset.configQuotaCardIndex);
          if (details.open) {
            expandedQuotaCards.add(index);
          } else {
            expandedQuotaCards.delete(index);
          }
          saveExpansionStateForCurrentModule();
        });
      });

      configForm.querySelectorAll("[data-config-field]").forEach(element => {
        const eventName = element.type === "checkbox" || element.type === "radio" || element.tagName === "SELECT" ? "change" : "input";
        element.addEventListener(eventName, () => applyFormToYaml());
      });

      configForm.querySelectorAll("[data-config-source-field]").forEach(element => {
        const eventName = element.type === "checkbox" || element.tagName === "SELECT" ? "change" : "input";
        element.addEventListener(eventName, () => applyFormToYaml());
      });

      configForm.querySelectorAll("[data-config-quota-field]").forEach(element => {
        const eventName = element.type === "checkbox" || element.tagName === "SELECT" ? "change" : "input";
        element.addEventListener(eventName, () => applyFormToYaml());
      });

      configForm.querySelectorAll("[data-config-action]").forEach(button => {
        button.addEventListener("click", event => {
          event.preventDefault();
          event.stopPropagation();
          handleConfigAction(button.dataset.configAction, button.dataset.index);
        });
      });
    }

    function handleConfigAction(action, indexValue) {
      activeConfigSectionId = configForm.querySelector(".config-section-tabs .nav-link.active")?.dataset.configSectionTarget || activeConfigSectionId;
      const state = readFormState();
      const index = Number(indexValue);
      switch (action) {
        case "add-source":
          state.sources.push(buildEmptySourceState());
          break;
        case "remove-source":
          state.sources.splice(index, 1);
          expandedSourceCards = reindexExpandedSet(expandedSourceCards, index);
          if (state.sources.length === 0) {
            state.sources.push(buildEmptySourceState());
          }
          break;
        case "add-quota":
          state.quotas.push(buildEmptyQuotaState());
          break;
        case "remove-quota":
          state.quotas.splice(index, 1);
          expandedQuotaCards = reindexExpandedSet(expandedQuotaCards, index);
          break;
        default:
          return;
      }
      renderConfigForm(state);
      saveExpansionStateForCurrentModule();
      applyFormToYaml();
    }

    async function applyFormToYaml() {
      if (isUpdatingFormFromYaml) {
        return;
      }

      return applyExplicitFormState(readFormState())
    }

    async function applyExplicitFormState(formState) {
      const requestId = ++configApplyRequestId;
      try {
        const payload = await postJson(
          "/api/config-form/update",
          {
            configText: getConfigText(),
            formState
          },
          "Не удалось синхронизировать YAML с формой."
        );
        if (requestId !== configApplyRequestId) {
          return;
        }

        isApplyingConfigFromForm = true;
        setConfigText(payload.configText);
        isApplyingConfigFromForm = false;
        lastFormState = payload.formState;
        renderConfigForm(payload.formState);
        onDraftStateChange?.();
        setFormWarning(null);
      } catch (error) {
        isApplyingConfigFromForm = false;
        setFormWarning(error.message || "Не удалось синхронизировать YAML с формой.");
      }
    }

    function readFormState() {
      const sourceIndexes = uniqueIndexes("data-config-source-index");
      const quotaIndexes = uniqueIndexes("data-config-quota-index");
      const defaultSqlMode = getFormFieldValue("defaultSqlMode");
      const defaultSqlExternalRef = emptyToNull(getFormFieldValue("defaultSqlExternalRef"));
      return {
        outputDir: getFormFieldValue("outputDir"),
        fileFormat: getFormFieldValue("fileFormat"),
        mergeMode: getFormFieldValue("mergeMode"),
        errorMode: getFormFieldValue("errorMode"),
        parallelism: requiredNumber(getFormFieldValue("parallelism"), "parallelism"),
        fetchSize: requiredNumber(getFormFieldValue("fetchSize"), "fetchSize"),
        queryTimeoutSec: optionalNumber(getFormFieldValue("queryTimeoutSec")),
        progressLogEveryRows: requiredNumber(getFormFieldValue("progressLogEveryRows"), "progressLogEveryRows"),
        maxMergedRows: optionalNumber(getFormFieldValue("maxMergedRows")),
        deleteOutputFilesAfterCompletion: getCheckboxValue("deleteOutputFilesAfterCompletion"),
        commonSql: serializeDefaultSqlValue(defaultSqlMode),
        commonSqlFile: serializeDefaultSqlFile(defaultSqlMode, defaultSqlExternalRef),
        sources: sourceIndexes.map(index => ({
          name: getIndexedFieldValue("source", index, "name"),
          jdbcUrl: getIndexedFieldValue("source", index, "jdbcUrl"),
          username: getIndexedFieldValue("source", index, "username"),
          password: getIndexedFieldValue("source", index, "password"),
          sql: serializeSourceSqlValue(index),
          sqlFile: serializeSourceSqlFile(index)
        })),
        quotas: quotaIndexes.map(index => ({
          source: getIndexedFieldValue("quota", index, "source"),
          percent: optionalNumber(getIndexedFieldValue("quota", index, "percent"))
        })),
        targetEnabled: getFormFieldValue("targetEnabled") === "true",
        targetJdbcUrl: getFormFieldValue("targetJdbcUrl"),
        targetUsername: getFormFieldValue("targetUsername"),
        targetPassword: getFormFieldValue("targetPassword"),
        targetTable: getFormFieldValue("targetTable"),
        targetTruncateBeforeLoad: getCheckboxValue("targetTruncateBeforeLoad")
      };
    }

    function getFormFieldValue(fieldName) {
      const checkedRadio = configForm.querySelector(`[data-config-field="${fieldName}"]:checked`);
      if (checkedRadio) {
        return checkedRadio.value ?? "";
      }
      return configForm.querySelector(`[data-config-field="${fieldName}"]`)?.value ?? "";
    }

    function getCheckboxValue(fieldName) {
      return Boolean(configForm.querySelector(`[data-config-field="${fieldName}"]`)?.checked);
    }

    function getIndexedFieldValue(kind, index, fieldName) {
      return configForm.querySelector(`[data-config-${kind}-index="${index}"][data-config-${kind}-field="${fieldName}"]`)?.value ?? "";
    }

    function serializeDefaultSqlValue(mode) {
      switch (mode) {
        case "INLINE":
          return getFormFieldValue("defaultSqlInlineText");
        case "NONE":
        case "CATALOG":
        case "EXTERNAL":
        default:
          return "";
      }
    }

    function serializeDefaultSqlFile(mode, externalRef) {
      switch (mode) {
        case "CATALOG":
          return emptyToNull(getFormFieldValue("defaultSqlCatalogPath"));
        case "EXTERNAL":
          return externalRef;
        case "NONE":
        case "INLINE":
        default:
          return null;
      }
    }

    function serializeSourceSqlValue(index) {
      const mode = getIndexedFieldValue("source", index, "sqlMode");
      return mode === "INLINE" ? emptyToNull(getIndexedFieldValue("source", index, "sqlInlineText")) : null;
    }

    function serializeSourceSqlFile(index) {
      const mode = getIndexedFieldValue("source", index, "sqlMode");
      if (mode === "CATALOG") {
        return emptyToNull(getIndexedFieldValue("source", index, "sqlCatalogPath"));
      }
      if (mode === "EXTERNAL") {
        return emptyToNull(getIndexedFieldValue("source", index, "sqlExternalRef"));
      }
      return null;
    }

    function uniqueIndexes(attributeName) {
      return [...configForm.querySelectorAll(`[${attributeName}]`)]
        .map(element => Number(element.getAttribute(attributeName)))
        .filter(value => !Number.isNaN(value))
        .filter((value, index, array) => array.indexOf(value) === index)
        .sort((a, b) => a - b);
    }

    function emptyToNull(value) {
      const normalized = String(value ?? "").trim();
      return normalized ? normalized : null;
    }

    function requiredNumber(rawValue, fieldName) {
      const normalized = String(rawValue).trim();
      const numeric = Number(normalized);
      if (!normalized || Number.isNaN(numeric)) {
        throw new Error(`Поле ${fieldName} должно быть числом.`);
      }
      return numeric;
    }

    function optionalNumber(rawValue) {
      const normalized = String(rawValue).trim();
      if (!normalized) {
        return null;
      }
      const numeric = Number(normalized);
      if (Number.isNaN(numeric)) {
        throw new Error("Одно из числовых полей формы заполнено некорректно.");
      }
      return numeric;
    }

    function setFormWarning(message) {
      if (!message) {
        configFormWarning.classList.add("d-none");
        configFormWarning.textContent = "";
        return;
      }
      configFormWarning.classList.remove("d-none");
      configFormWarning.innerHTML = `
        <div><strong>Форма временно недоступна.</strong></div>
        <div class="mt-1">${escapeHtml(message)}</div>
      `;
    }

    return {
      initialize,
      isApplyingFromForm,
      scheduleSyncFromYaml,
      syncFromYaml,
      currentFormState: () => JSON.parse(JSON.stringify(lastFormState)),
      refreshSqlResources: () => renderConfigForm(lastFormState),
      renameSqlResource: (oldPath, newPath) => {
        const nextState = readFormState();
        if (nextState.commonSqlFile === oldPath) {
          nextState.commonSqlFile = newPath;
        }
        nextState.sources = nextState.sources.map(source => source.sqlFile === oldPath ? { ...source, sqlFile: newPath } : source);
        return applyExplicitFormState(nextState);
      },
      loadSerializedExpansionState,
      serializeExpansionState,
      restoreExpansionStateForModule,
      saveExpansionStateForCurrentModule
    };
  }

  function renderTextField(fieldName, label, value, disabled, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <input
          class="form-control"
          type="text"
          data-config-field="${fieldName}"
          value="${escapeHtml(value ?? "")}"
          ${disabled ? "disabled" : ""}
        >
      </label>
    `;
  }

  function renderTextareaField(fieldName, label, value, disabled, helpText = "", rows = 4) {
    return `
      <label class="config-form-field config-form-field-wide">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <textarea
          class="form-control"
          rows="${rows}"
          data-config-field="${fieldName}"
          ${disabled ? "disabled" : ""}
        >${escapeHtml(value ?? "")}</textarea>
      </label>
    `;
  }

  function renderNumberField(fieldName, label, value, disabled, optional = false, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        <span class="config-form-help">${helpText || (optional ? "Можно оставить пустым." : "Обязательное числовое поле.")}</span>
        <input
          class="form-control"
          type="number"
          data-config-field="${fieldName}"
          value="${escapeHtml(value ?? "")}"
          ${disabled ? "disabled" : ""}
        >
      </label>
    `;
  }

  function renderSelectField(fieldName, label, value, options, disabled, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <select class="form-select" data-config-field="${fieldName}" ${disabled ? "disabled" : ""}>
          ${options.map(([optionValue, optionLabel]) => `
            <option value="${escapeHtml(optionValue)}" ${value === optionValue ? "selected" : ""}>${escapeHtml(optionLabel)}</option>
          `).join("")}
        </select>
      </label>
    `;
  }

  function renderCheckboxField(fieldName, label, checked, disabled, helpText = "") {
    return `
      <div class="config-form-check-group">
        <label class="config-form-check">
          <input
            class="form-check-input"
            type="checkbox"
            data-config-field="${fieldName}"
            ${checked ? "checked" : ""}
            ${disabled ? "disabled" : ""}
          >
          <span>${label}</span>
        </label>
        ${helpText ? `<div class="config-form-help">${helpText}</div>` : ""}
      </div>
    `;
  }

  function renderRadioGroupField(fieldName, label, value, options, disabled, helpText = "") {
    return `
      <div class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <div class="config-radio-group">
          ${options.map(([optionValue, optionLabel]) => `
            <label class="config-radio-option">
              <input
                type="radio"
                name="${fieldName}"
                data-config-field="${fieldName}"
                value="${escapeHtml(optionValue)}"
                ${value === optionValue ? "checked" : ""}
                ${disabled ? "disabled" : ""}
              >
              <span>${escapeHtml(optionLabel)}</span>
            </label>
          `).join("")}
        </div>
      </div>
    `;
  }

  function renderCollapsibleConfigCard(cardKey, title, isOpen, bodyHtml) {
    return `
      <details class="config-form-card config-form-collapsible-card" data-config-card-key="${cardKey}" ${isOpen ? "open" : ""}>
        <summary class="config-form-card-summary">
          <span class="config-form-card-title mb-0">${title}</span>
          <span class="config-collapse-indicator" aria-hidden="true"></span>
        </summary>
        <div class="config-form-card-body">
          ${bodyHtml}
        </div>
      </details>
    `;
  }

  function renderIndexedTextField(kind, index, fieldName, label, value, disabled, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <input
          class="form-control"
          type="text"
          data-config-${kind}-index="${index}"
          data-config-${kind}-field="${fieldName}"
          value="${escapeHtml(value ?? "")}"
          ${disabled ? "disabled" : ""}
        >
      </label>
    `;
  }

  function renderIndexedNumberField(kind, index, fieldName, label, value, disabled, helpText = "", step = "any") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <input
          class="form-control"
          type="number"
          step="${step}"
          data-config-${kind}-index="${index}"
          data-config-${kind}-field="${fieldName}"
          value="${escapeHtml(value ?? "")}"
          ${disabled ? "disabled" : ""}
        >
      </label>
    `;
  }

  function renderIndexedTextareaField(kind, index, fieldName, label, value, disabled, helpText = "", rows = 5) {
    return `
      <label class="config-form-field config-form-field-wide">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <textarea
          class="form-control"
          rows="${rows}"
          data-config-${kind}-index="${index}"
          data-config-${kind}-field="${fieldName}"
          ${disabled ? "disabled" : ""}
        >${escapeHtml(value ?? "")}</textarea>
      </label>
    `;
  }

  function renderIndexedSelectField(kind, index, fieldName, label, value, options, disabled, helpText = "", includeEmptyOption = true) {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <select
          class="form-select"
          data-config-${kind}-index="${index}"
          data-config-${kind}-field="${fieldName}"
          ${disabled ? "disabled" : ""}
        >
          ${includeEmptyOption ? '<option value=""></option>' : ''}
          ${options.map(([optionValue, optionLabel]) => `
            <option value="${escapeHtml(optionValue)}" ${value === optionValue ? "selected" : ""}>${escapeHtml(optionLabel)}</option>
          `).join("")}
        </select>
      </label>
    `;
  }

  function renderSourceCard(source, index, disabled, isOpen, sqlResources, storageMode) {
    const sourceSqlState = buildSourceSqlState(source, sqlResources);
    const title = source.name?.trim() ? source.name : `Source ${index + 1}`;
    const subtitle = source.jdbcUrl?.trim() || "jdbcUrl не задан";
    return `
      <details class="config-form-subcard config-form-subcard-collapsible" data-config-source-card-index="${index}" ${isOpen ? "open" : ""}>
        <summary class="config-form-subcard-summary">
          <div class="config-form-subcard-summary-text">
            <div class="config-form-subtitle">${escapeHtml(title)}</div>
            <div class="config-form-help">${escapeHtml(subtitle)}</div>
          </div>
          <div class="config-form-subcard-summary-actions">
            <button class="btn btn-outline-danger btn-sm" type="button" data-config-action="remove-source" data-index="${index}" ${disabled ? "disabled" : ""}>Удалить</button>
            <span class="config-collapse-indicator" aria-hidden="true"></span>
          </div>
        </summary>
        <div class="config-form-subcard-body">
          <div class="config-form-fields">
            ${renderIndexedTextField("source", index, "name", "name", source.name, disabled, "Уникальное имя источника.")}
            ${renderIndexedTextField("source", index, "jdbcUrl", "jdbcUrl", source.jdbcUrl, disabled, "Подключение к source PostgreSQL.")}
            ${renderIndexedTextField("source", index, "username", "username", source.username, disabled)}
            ${renderIndexedTextField("source", index, "password", "password", source.password, disabled)}
            ${renderSourceSqlEditor(index, sourceSqlState, sqlResources, storageMode, disabled)}
          </div>
        </div>
      </details>
    `;
  }

  function renderQuotaRow(quota, index, sources, disabled, isOpen) {
    const sourceOptions = sources.map(source => [source.name, source.name]).filter(([value]) => value);
    const selectOptions = quota.source && !sourceOptions.some(([value]) => value === quota.source)
      ? [[quota.source, quota.source], ...sourceOptions]
      : sourceOptions;
    const title = quota.source?.trim() ? quota.source : `Quota ${index + 1}`;
    const subtitle = quota.percent !== null && quota.percent !== undefined && quota.percent !== "" ? `${quota.percent}%` : "Процент не задан";
    return `
      <details class="config-form-subcard config-form-subcard-collapsible" data-config-quota-card-index="${index}" ${isOpen ? "open" : ""}>
        <summary class="config-form-subcard-summary">
          <div class="config-form-subcard-summary-text">
            <div class="config-form-subtitle">${escapeHtml(title)}</div>
            <div class="config-form-help">${escapeHtml(subtitle)}</div>
          </div>
          <div class="config-form-subcard-summary-actions">
            <button class="btn btn-outline-danger btn-sm" type="button" data-config-action="remove-quota" data-index="${index}" ${disabled ? "disabled" : ""}>Удалить</button>
            <span class="config-collapse-indicator" aria-hidden="true"></span>
          </div>
        </summary>
        <div class="config-form-subcard-body">
          <div class="config-form-fields">
            ${selectOptions.length > 0
              ? renderIndexedSelectField("quota", index, "source", "source", quota.source, selectOptions, disabled, "Источник, для которого задается процент.")
              : renderIndexedTextField("quota", index, "source", "source", quota.source, disabled, "Имя источника для quota.")}
            ${renderIndexedNumberField("quota", index, "percent", "percent", quota.percent, disabled, "Процент для режима quota.", "0.01")}
          </div>
        </div>
      </details>
    `;
  }

  function normalizeSqlResources(sqlResources) {
    return Array.isArray(sqlResources) ? sqlResources.map(resource => ({
      label: resource?.label || resource?.path || "",
      path: resource?.path || "",
      exists: resource?.exists !== false,
    })).filter(resource => resource.path) : [];
  }

  function buildDefaultSqlState(state, sqlResources) {
    const resourceMap = new Map(sqlResources.map(resource => [resource.path, resource]));
    const inlineSql = String(state.commonSql || "");
    const sqlFile = String(state.commonSqlFile || "").trim();
    if (inlineSql.trim()) {
      return { mode: "INLINE", inlineText: inlineSql, catalogPath: "", externalRef: null, resource: null };
    }
    if (!sqlFile) {
      return { mode: "NONE", inlineText: "", catalogPath: "", externalRef: null, resource: null };
    }
    if (resourceMap.has(sqlFile)) {
      return { mode: "CATALOG", inlineText: "", catalogPath: sqlFile, externalRef: null, resource: resourceMap.get(sqlFile) };
    }
    return { mode: "EXTERNAL", inlineText: "", catalogPath: "", externalRef: sqlFile, resource: null };
  }

  function buildSourceSqlState(source, sqlResources) {
    const resourceMap = new Map(sqlResources.map(resource => [resource.path, resource]));
    const inlineSql = String(source.sql || "");
    const sqlFile = String(source.sqlFile || "").trim();
    if (inlineSql.trim()) {
      return {
        mode: "INLINE",
        inlineText: inlineSql,
        catalogPath: "",
        externalRef: null,
        summary: "Использует inline SQL",
        resource: null
      };
    }
    if (!sqlFile) {
      return {
        mode: "INHERIT",
        inlineText: "",
        catalogPath: "",
        externalRef: null,
        summary: "Наследует SQL по умолчанию",
        resource: null
      };
    }
    if (resourceMap.has(sqlFile)) {
      return {
        mode: "CATALOG",
        inlineText: "",
        catalogPath: sqlFile,
        externalRef: null,
        summary: `Использует SQL-ресурс: ${resourceMap.get(sqlFile).label || sqlFile}`,
        resource: resourceMap.get(sqlFile)
      };
    }
    return {
      mode: "EXTERNAL",
      inlineText: "",
      catalogPath: "",
      externalRef: sqlFile,
      summary: "Использует внешнюю SQL-ссылку",
      resource: null
    };
  }

  function renderDefaultSqlEditor(sqlState, sqlResources, storageMode, disabled) {
    return `
      ${renderSelectField("defaultSqlMode", "Источник SQL", sqlState.mode, [
        ["NONE", "Не задан"],
        ["INLINE", "Inline SQL"],
        ["CATALOG", "SQL из каталога"],
        ...(sqlState.mode === "EXTERNAL" ? [["EXTERNAL", storageMode === "DATABASE" ? "Внешняя ссылка (нештатно)" : "Внешняя ссылка (только YAML)"]] : [])
      ], disabled, "SQL по умолчанию используется для sources без собственного SQL.")}
      ${renderTextHiddenField("defaultSqlExternalRef", sqlState.externalRef || "")}
      ${sqlState.mode === "INLINE" ? renderTextareaField("defaultSqlInlineText", "SQL по умолчанию", sqlState.inlineText, disabled, "Будет применяться ко всем источникам без собственного SQL.", 6) : ""}
      ${sqlState.mode === "CATALOG" ? renderSqlCatalogSelectField("defaultSqlCatalogPath", "SQL-ресурс", sqlState.catalogPath, sqlResources, disabled, "Выбирается из вкладки SQL.") : ""}
      ${sqlState.mode === "EXTERNAL" ? renderSqlExternalReference(sqlState.externalRef, storageMode) : ""}
      ${sqlState.mode === "CATALOG" && sqlState.resource?.exists === false ? renderSqlMissingWarning(sqlState.catalogPath) : ""}
      <div class="config-form-inline-note">Используется для источников без собственного SQL.</div>
    `;
  }

  function renderSourceSqlEditor(index, sqlState, sqlResources, storageMode, disabled) {
    return `
      <div class="config-form-field config-form-field-wide">
        <span class="config-form-label">SQL источника</span>
        <span class="config-form-help">${escapeHtml(sqlState.summary)}</span>
      </div>
      ${renderIndexedSelectField("source", index, "sqlMode", "Источник SQL", sqlState.mode, [
        ["INHERIT", "Наследовать SQL по умолчанию"],
        ["INLINE", "Inline SQL"],
        ["CATALOG", "SQL из каталога"],
        ...(sqlState.mode === "EXTERNAL" ? [["EXTERNAL", storageMode === "DATABASE" ? "Внешняя ссылка (нештатно)" : "Внешняя ссылка (только YAML)"]] : [])
      ], disabled, "Источник SQL для конкретного source.", false)}
      ${renderIndexedHiddenField("source", index, "sqlExternalRef", sqlState.externalRef || "")}
      ${sqlState.mode === "INLINE" ? renderIndexedTextareaField("source", index, "sqlInlineText", "Inline SQL", sqlState.inlineText, disabled, "Если задан, перекрывает SQL по умолчанию.", 5) : ""}
      ${sqlState.mode === "CATALOG" ? renderIndexedSqlCatalogSelectField(index, "sqlCatalogPath", "SQL-ресурс", sqlState.catalogPath, sqlResources, disabled, "Выбирается из вкладки SQL.") : ""}
      ${sqlState.mode === "EXTERNAL" ? renderSqlExternalReference(sqlState.externalRef, storageMode) : ""}
      ${sqlState.mode === "CATALOG" && sqlState.resource?.exists === false ? renderSqlMissingWarning(sqlState.catalogPath) : ""}
    `;
  }

  function renderSqlCatalogSelectField(fieldName, label, value, sqlResources, disabled, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <select class="form-select" data-config-field="${fieldName}" ${disabled ? "disabled" : ""}>
          <option value=""></option>
          ${sqlResources.map(resource => `
            <option value="${escapeHtml(resource.path)}" ${value === resource.path ? "selected" : ""}>
              ${escapeHtml(resource.exists ? resource.path : `[Отсутствует] ${resource.path}`)}
            </option>
          `).join("")}
        </select>
      </label>
    `;
  }

  function renderIndexedSqlCatalogSelectField(index, fieldName, label, value, sqlResources, disabled, helpText = "") {
    return `
      <label class="config-form-field">
        <span class="config-form-label">${label}</span>
        ${helpText ? `<span class="config-form-help">${helpText}</span>` : ""}
        <select
          class="form-select"
          data-config-source-index="${index}"
          data-config-source-field="${fieldName}"
          ${disabled ? "disabled" : ""}
        >
          <option value=""></option>
          ${sqlResources.map(resource => `
            <option value="${escapeHtml(resource.path)}" ${value === resource.path ? "selected" : ""}>
              ${escapeHtml(resource.exists ? resource.path : `[Отсутствует] ${resource.path}`)}
            </option>
          `).join("")}
        </select>
      </label>
    `;
  }

  function renderTextHiddenField(fieldName, value) {
    return `<input type="hidden" data-config-field="${fieldName}" value="${escapeHtml(value ?? "")}">`;
  }

  function renderIndexedHiddenField(kind, index, fieldName, value) {
    return `
      <input
        type="hidden"
        data-config-${kind}-index="${index}"
        data-config-${kind}-field="${fieldName}"
        value="${escapeHtml(value ?? "")}"
      >
    `;
  }

  function renderSqlExternalReference(externalRef, storageMode) {
    if (!externalRef) {
      return "";
    }
    return `
      <div class="config-form-inline-alert">
        <div class="fw-semibold mb-1">${storageMode === "DATABASE" ? "Нештатная внешняя SQL-ссылка" : "Внешняя SQL-ссылка"}</div>
        <div><code>${escapeHtml(externalRef)}</code></div>
        <div class="config-form-help mt-1">${storageMode === "DATABASE"
          ? "Для DB-режима visual editor считает нормальным только inline SQL и SQL-ресурсы самого модуля."
          : "Эта ссылка сохраняется, но полноценно управляется только через application.yml."}</div>
      </div>
    `;
  }

  function renderSqlMissingWarning(path) {
    return `
      <div class="config-form-inline-alert config-form-inline-alert-warning">
        <div class="fw-semibold mb-1">SQL-ресурс не найден</div>
        <div><code>${escapeHtml(path)}</code></div>
      </div>
    `;
  }

  namespace.createConfigFormController = createConfigFormController;
})(window);
