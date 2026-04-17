(function initDataPoolSqlConsoleTools(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolSqlConsole || (global.DataPoolSqlConsole = {});
  const { escapeHtml } = common;
  const MAX_RECENT_QUERIES = 10;
  const MAX_FAVORITE_QUERIES = 15;
  const DANGEROUS_STATEMENTS = ["DROP", "TRUNCATE", "ALTER", "DELETE"];
  const MUTATING_STATEMENTS = ["INSERT", "UPDATE", "CREATE", "GRANT", "REVOKE", "VACUUM", "ANALYZE", "REINDEX"];

  function createQueryToolsController({
    editor,
    recentQuerySelectEl,
    applyRecentQueryButtonEl,
    clearRecentQueriesButtonEl,
    favoriteQuerySelectEl,
    applyFavoriteQueryButtonEl,
    rememberFavoriteQueryButtonEl,
    removeFavoriteQueryButtonEl,
    strictSafetyCheckboxEl,
    commandGuardrailEl,
    runButtonEl,
    initialDraft = "select 1 as check_value",
    initialRecentQueries = [],
    initialFavoriteQueries = [],
    initialStrictSafetyEnabled = false,
    onStateChanged = () => {}
  }) {
    let draftSql = String(initialDraft || "select 1 as check_value");
    let recentQueries = normalizeRecentQueries(initialRecentQueries);
    let favoriteQueries = normalizeFavoriteQueries(initialFavoriteQueries);
    let strictSafetyEnabled = initialStrictSafetyEnabled === true;

    function initialize() {
      renderRecentQueries();
      renderFavoriteQueries();
      renderCommandGuardrail(draftSql);

      recentQuerySelectEl.addEventListener("change", () => {
        applyRecentQueryButtonEl.disabled = !recentQuerySelectEl.value;
      });
      favoriteQuerySelectEl.addEventListener("change", () => {
        applyFavoriteQueryButtonEl.disabled = !favoriteQuerySelectEl.value;
        removeFavoriteQueryButtonEl.disabled = !favoriteQuerySelectEl.value;
      });

      applyRecentQueryButtonEl.addEventListener("click", () => {
        applySelectedRecentQuery();
      });
      applyFavoriteQueryButtonEl.addEventListener("click", () => {
        applySelectedFavoriteQuery();
      });
      rememberFavoriteQueryButtonEl.addEventListener("click", () => {
        rememberFavoriteQuery(editor.getValue());
      });
      removeFavoriteQueryButtonEl.addEventListener("click", () => {
        removeSelectedFavoriteQuery();
      });

      clearRecentQueriesButtonEl.addEventListener("click", () => {
        recentQueries = [];
        emitStateChanged();
        renderRecentQueries();
      });
      strictSafetyCheckboxEl.addEventListener("change", () => {
        strictSafetyEnabled = strictSafetyCheckboxEl.checked;
        emitStateChanged();
        renderCommandGuardrail(draftSql);
      });
      strictSafetyCheckboxEl.checked = strictSafetyEnabled;
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

    function rememberFavoriteQuery(sql) {
      const normalized = String(sql || "").trim();
      if (!normalized) {
        return;
      }
      favoriteQueries = [normalized, ...favoriteQueries.filter(item => item !== normalized)].slice(0, MAX_FAVORITE_QUERIES);
      emitStateChanged();
      renderFavoriteQueries();
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

    function renderFavoriteQueries() {
      if (favoriteQueries.length === 0) {
        favoriteQuerySelectEl.innerHTML = `<option value="">Избранное пока пусто</option>`;
        applyFavoriteQueryButtonEl.disabled = true;
        removeFavoriteQueryButtonEl.disabled = true;
        return;
      }

      favoriteQuerySelectEl.innerHTML = `
        <option value="">Выбери запрос из избранного</option>
        ${favoriteQueries.map((query, index) => `
          <option value="${index}">${index + 1}. ${escapeHtml(compactSql(query))}</option>
        `).join("")}
      `;
      applyFavoriteQueryButtonEl.disabled = !favoriteQuerySelectEl.value;
      removeFavoriteQueryButtonEl.disabled = !favoriteQuerySelectEl.value;
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

    function applySelectedFavoriteQuery() {
      const selectedIndex = favoriteQuerySelectEl.value;
      if (selectedIndex === "") {
        return;
      }
      const query = favoriteQueries[Number(selectedIndex)];
      if (!query) {
        return;
      }
      editor.setValue(query);
      editor.focus();
      handleEditorChange(query);
      applyFavoriteQueryButtonEl.disabled = false;
      removeFavoriteQueryButtonEl.disabled = false;
    }

    function removeSelectedFavoriteQuery() {
      const selectedIndex = favoriteQuerySelectEl.value;
      if (selectedIndex === "") {
        return;
      }
      favoriteQueries = favoriteQueries.filter((_, index) => String(index) !== String(selectedIndex));
      emitStateChanged();
      renderFavoriteQueries();
    }

    function renderCommandGuardrail(sql) {
      const analysis = analyzeStatement(sql);
      const keyword = analysis.keyword;
      const isEmpty = !String(sql || "").trim();

      runButtonEl.classList.remove("btn-primary", "btn-warning", "btn-danger");

      if (isEmpty || keyword === "SQL") {
        commandGuardrailEl.textContent = "Текущий запрос не определен. Введи SQL, чтобы UI показал тип команды и предупредил о потенциально опасных операциях.";
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-neutral";
        runButtonEl.classList.add("btn-primary");
        return;
      }

      if (analysis.readOnly) {
        const strictSuffix = strictSafetyEnabled ? " Строгая защита включена." : "";
        commandGuardrailEl.textContent = `Обнаружен запрос типа ${keyword}. Это read-only сценарий: UI покажет табличные данные отдельно по каждому source.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-safe";
        runButtonEl.classList.add("btn-primary");
        if (strictSuffix) {
          commandGuardrailEl.textContent += strictSuffix;
        }
        return;
      }

      if (strictSafetyEnabled) {
        commandGuardrailEl.textContent = `Строгая защита включена. Запрос типа ${keyword} сейчас заблокирован, потому что он может изменить данные или схему на выбранных sources.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-danger";
        runButtonEl.classList.add("btn-danger");
        return;
      }

      if (analysis.dangerous) {
        commandGuardrailEl.textContent = `Обнаружена команда ${keyword}. Она может удалить данные или изменить схему на всех выбранных sources. Перед запуском UI потребует отдельное подтверждение.`;
        commandGuardrailEl.className = "sql-guardrail sql-guardrail-danger";
        runButtonEl.classList.add("btn-danger");
        return;
      }

      if (analysis.mutating) {
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
        recentQueries: [...recentQueries],
        favoriteQueries: [...favoriteQueries],
        strictSafetyEnabled
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
      rememberFavoriteQuery,
      detectStatementKeyword,
      isReadOnlyLikeStatement,
      isDangerousStatement,
      analyzeStatement
    };
  }

  function analyzeStatement(sql) {
    const normalizedSql = normalizeSql(sql);
    const keyword = normalizedSql.split(/\s+/)[0] || "SQL";
    const dangerous = containsKeyword(normalizedSql, DANGEROUS_STATEMENTS);
    const mutating = dangerous || containsKeyword(normalizedSql, MUTATING_STATEMENTS);
    const readOnly = !dangerous && !mutating && isReadOnlyLikeStatement(keyword);
    return {
      keyword,
      readOnly,
      dangerous,
      mutating: mutating || (!readOnly && !dangerous)
    };
  }

  function detectStatementKeyword(sql) {
    return analyzeStatement(sql).keyword;
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

  function normalizeFavoriteQueries(queries) {
    return Array.isArray(queries)
      ? queries
        .filter(item => typeof item === "string" && item.trim().length > 0)
        .map(item => item.trim())
        .filter((item, index, all) => all.indexOf(item) === index)
        .slice(0, MAX_FAVORITE_QUERIES)
      : [];
  }

  function normalizeSql(sql) {
    return String(sql || "")
      .replace(/\/\*[\s\S]*?\*\//g, " ")
      .split("\n")
      .map(line => line.split("--")[0])
      .join(" ")
      .trim()
      .replace(/;$/, "")
      .trim()
      .toUpperCase();
  }

  function containsKeyword(normalizedSql, keywords) {
    return keywords.some(keyword => new RegExp(`(^|\\W)${keyword}(\\W|$)`).test(normalizedSql));
  }

  function compactSql(sql) {
    return String(sql)
      .replace(/\s+/g, " ")
      .trim()
      .slice(0, 80);
  }

  namespace.createQueryToolsController = createQueryToolsController;
})(window);
