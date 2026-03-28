(function initDataPoolSqlConsoleTools(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolSqlConsole || (global.DataPoolSqlConsole = {});
  const { escapeHtml, loadJsonStorage, loadTextStorage, removeStorageValue, saveJsonStorage, saveTextStorage } = common;

  const SQL_DRAFT_STORAGE_KEY = "datapool.sqlConsole.draft";
  const SQL_RECENT_STORAGE_KEY = "datapool.sqlConsole.recentQueries";
  const MAX_RECENT_QUERIES = 10;
  const DANGEROUS_STATEMENTS = ["DROP", "TRUNCATE", "ALTER", "DELETE"];
  const MUTATING_STATEMENTS = ["INSERT", "UPDATE", "CREATE", "GRANT", "REVOKE", "VACUUM", "ANALYZE", "REINDEX"];
  const QUERY_TEMPLATES = [
    {
      key: "health-check",
      label: "Проверка подключения",
      sql: "select current_database() as database_name, current_user as current_user, now() as server_time"
    },
    {
      key: "table-list",
      label: "Список таблиц",
      sql: "select table_schema, table_name\nfrom information_schema.tables\nwhere table_schema not in ('pg_catalog', 'information_schema')\norder by table_schema, table_name"
    },
    {
      key: "row-count",
      label: "Подсчет строк",
      sql: "select count(*) as total_rows\nfrom your_table_name"
    }
  ];

  function createQueryToolsController({
    editor,
    recentQuerySelectEl,
    applyRecentQueryButtonEl,
    clearRecentQueriesButtonEl,
    queryTemplatesEl,
    commandGuardrailEl,
    runButtonEl
  }) {
    function initialize() {
      renderRecentQueries();
      renderQueryTemplates();
      renderCommandGuardrail(editor.getValue());

      recentQuerySelectEl.addEventListener("change", () => {
        applyRecentQueryButtonEl.disabled = !recentQuerySelectEl.value;
      });

      applyRecentQueryButtonEl.addEventListener("click", () => {
        applySelectedRecentQuery();
      });

      clearRecentQueriesButtonEl.addEventListener("click", () => {
        removeStorageValue(window.localStorage, SQL_RECENT_STORAGE_KEY);
        renderRecentQueries();
      });
    }

    function getInitialDraft() {
      return loadTextStorage(window.sessionStorage, SQL_DRAFT_STORAGE_KEY, "select 1 as check_value");
    }

    function handleEditorChange(sql) {
      saveTextStorage(window.sessionStorage, SQL_DRAFT_STORAGE_KEY, sql);
      renderCommandGuardrail(sql);
    }

    function rememberRecentQuery(sql) {
      const normalized = String(sql || "").trim();
      if (!normalized) {
        return;
      }
      const queries = loadRecentQueries().filter(item => item !== normalized);
      queries.unshift(normalized);
      saveRecentQueries(queries.slice(0, MAX_RECENT_QUERIES));
      renderRecentQueries();
    }

    function renderRecentQueries() {
      const queries = loadRecentQueries();
      if (queries.length === 0) {
        recentQuerySelectEl.innerHTML = `<option value="">История пока пуста</option>`;
        applyRecentQueryButtonEl.disabled = true;
        clearRecentQueriesButtonEl.disabled = true;
        return;
      }

      recentQuerySelectEl.innerHTML = `
        <option value="">Выбери запрос из истории</option>
        ${queries.map((query, index) => `
          <option value="${index}">${index + 1}. ${escapeHtml(compactSql(query))}</option>
        `).join("")}
      `;
      applyRecentQueryButtonEl.disabled = !recentQuerySelectEl.value;
      clearRecentQueriesButtonEl.disabled = false;
    }

    function applySelectedRecentQuery() {
      const selectedIndex = recentQuerySelectEl.value;
      if (selectedIndex === "") {
        return;
      }
      const query = loadRecentQueries()[Number(selectedIndex)];
      if (!query) {
        return;
      }
      editor.setValue(query);
      editor.focus();
      handleEditorChange(query);
      applyRecentQueryButtonEl.disabled = false;
    }

    function renderQueryTemplates() {
      queryTemplatesEl.innerHTML = QUERY_TEMPLATES.map(template => `
        <button
          class="btn btn-outline-dark btn-sm"
          type="button"
          data-query-template="${escapeHtml(template.key)}"
        >
          ${escapeHtml(template.label)}
        </button>
      `).join("");

      queryTemplatesEl.querySelectorAll("[data-query-template]").forEach(button => {
        button.addEventListener("click", () => {
          const template = QUERY_TEMPLATES.find(item => item.key === button.dataset.queryTemplate);
          if (!template) {
            return;
          }
          editor.setValue(template.sql);
          editor.focus();
          handleEditorChange(template.sql);
        });
      });
    }

    function renderCommandGuardrail(sql) {
      const keyword = detectStatementKeyword(sql);
      const isEmpty = !String(sql || "").trim();

      runButtonEl.classList.remove("btn-primary", "btn-warning", "btn-danger");

      if (isEmpty || keyword === "SQL") {
        commandGuardrailEl.textContent = "Текущий запрос не определен. Введи SQL, чтобы UI показал тип команды и предупредил о потенциально опасных операциях.";
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-neutral";
        runButtonEl.classList.add("btn-primary");
        return;
      }

      if (isReadOnlyLikeStatement(keyword)) {
        commandGuardrailEl.textContent = `Обнаружен запрос типа ${keyword}. Это read-only сценарий: UI покажет табличные данные отдельно по каждому source.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-safe";
        runButtonEl.classList.add("btn-primary");
        return;
      }

      if (isDangerousStatement(keyword)) {
        commandGuardrailEl.textContent = `Обнаружена команда ${keyword}. Она может удалить данные или изменить схему на всех выбранных sources. Перед запуском UI потребует отдельное подтверждение.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-danger";
        runButtonEl.classList.add("btn-danger");
        return;
      }

      if (MUTATING_STATEMENTS.includes(keyword)) {
        commandGuardrailEl.textContent = `Обнаружена команда ${keyword}. Она может изменить данные, индексы или служебное состояние на всех выбранных sources. Перед запуском UI запросит подтверждение.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-warning";
        runButtonEl.classList.add("btn-warning");
        return;
      }

      commandGuardrailEl.textContent = `Обнаружена команда ${keyword}. UI выполнит ее на всех выбранных sources и покажет статусы по каждому из них.`;
      commandGuardrailEl.className = "sql-guardrail sql-guardrail-warning";
      runButtonEl.classList.add("btn-warning");
    }

    return {
      initialize,
      getInitialDraft,
      handleEditorChange,
      rememberRecentQuery,
      detectStatementKeyword,
      isReadOnlyLikeStatement,
      isDangerousStatement
    };
  }

  function detectStatementKeyword(sql) {
    const normalized = String(sql)
      .replace(/\/\*[\s\S]*?\*\//g, " ")
      .split("\n")
      .map(line => line.split("--")[0])
      .join(" ")
      .trim()
      .replace(/;$/, "")
      .trim()
      .toUpperCase();

    return normalized.split(/\s+/)[0] || "SQL";
  }

  function isReadOnlyLikeStatement(keyword) {
    return ["SELECT", "WITH", "SHOW", "EXPLAIN", "VALUES"].includes(keyword);
  }

  function isDangerousStatement(keyword) {
    return DANGEROUS_STATEMENTS.includes(keyword);
  }

  function loadRecentQueries() {
    const parsed = loadJsonStorage(window.localStorage, SQL_RECENT_STORAGE_KEY, []);
    return Array.isArray(parsed)
      ? parsed.filter(item => typeof item === "string" && item.trim().length > 0)
      : [];
  }

  function saveRecentQueries(queries) {
    saveJsonStorage(window.localStorage, SQL_RECENT_STORAGE_KEY, queries);
  }

  function compactSql(sql) {
    return String(sql)
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 80);
  }

  namespace.createQueryToolsController = createQueryToolsController;
})(window);
