(function initDataPoolSqlConsoleTools(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolSqlConsole || (global.DataPoolSqlConsole = {});
  const { escapeHtml } = common;
  const MAX_RECENT_QUERIES = 10;
  const DANGEROUS_STATEMENTS = ["DROP", "TRUNCATE", "ALTER", "DELETE"];
  const MUTATING_STATEMENTS = ["INSERT", "UPDATE", "CREATE", "GRANT", "REVOKE", "VACUUM", "ANALYZE", "REINDEX"];

  function createQueryToolsController({
    editor,
    recentQuerySelectEl,
    applyRecentQueryButtonEl,
    clearRecentQueriesButtonEl,
    commandGuardrailEl,
    runButtonEl,
    initialDraft = "select 1 as check_value",
    initialRecentQueries = [],
    onStateChanged = () => {}
  }) {
    let draftSql = String(initialDraft || "select 1 as check_value");
    let recentQueries = normalizeRecentQueries(initialRecentQueries);

    function initialize() {
      renderRecentQueries();
      renderCommandGuardrail(draftSql);

      recentQuerySelectEl.addEventListener("change", () => {
        applyRecentQueryButtonEl.disabled = !recentQuerySelectEl.value;
      });

      applyRecentQueryButtonEl.addEventListener("click", () => {
        applySelectedRecentQuery();
      });

      clearRecentQueriesButtonEl.addEventListener("click", () => {
        recentQueries = [];
        emitStateChanged();
        renderRecentQueries();
      });
    }

    function getInitialDraft() {
      return draftSql;
    }

    function handleEditorChange(sql) {
      draftSql = String(sql || "");
      emitStateChanged();
      renderCommandGuardrail(sql);
    }

    function rememberRecentQuery(sql) {
      const normalized = String(sql || "").trim();
      if (!normalized) {
        return;
      }
      recentQueries = [normalized, ...recentQueries.filter(item => item !== normalized)].slice(0, MAX_RECENT_QUERIES);
      emitStateChanged();
      renderRecentQueries();
    }

    function renderRecentQueries() {
      if (recentQueries.length === 0) {
        recentQuerySelectEl.innerHTML = `<option value="">История пока пуста</option>`;
        applyRecentQueryButtonEl.disabled = true;
        clearRecentQueriesButtonEl.disabled = true;
        return;
      }

      recentQuerySelectEl.innerHTML = `
        <option value="">Выбери запрос из истории</option>
        ${recentQueries.map((query, index) => `
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
      const query = recentQueries[Number(selectedIndex)];
      if (!query) {
        return;
      }
      editor.setValue(query);
      editor.focus();
      handleEditorChange(query);
      applyRecentQueryButtonEl.disabled = false;
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

    function getState() {
      return {
        draftSql,
        recentQueries: [...recentQueries]
      };
    }

    function emitStateChanged() {
      onStateChanged(getState());
    }

    return {
      initialize,
      getInitialDraft,
      getState,
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

  function normalizeRecentQueries(queries) {
    return Array.isArray(queries)
      ? queries
        .filter(item => typeof item === "string" && item.trim().length > 0)
        .map(item => item.trim())
        .filter((item, index, all) => all.indexOf(item) === index)
        .slice(0, MAX_RECENT_QUERIES)
      : [];
  }

  function compactSql(sql) {
    return String(sql)
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 80);
  }

  namespace.createQueryToolsController = createQueryToolsController;
})(window);
