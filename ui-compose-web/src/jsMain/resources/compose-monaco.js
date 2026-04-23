(function initComposeMonaco(global) {
  const namespace = global.ComposeMonaco || (global.ComposeMonaco = {});
  let monacoConfigured = false;
  let sqlSupportRegistered = false;
  const monacoVsPath = "/static/compose-app/vendor/monaco-editor/min/vs";

  const SQL_KEYWORDS = [
    {
      label: "select",
      insertText: "select ",
      detail: "Чтение данных",
      documentation: "Извлекает строки из таблиц, view или подзапросов."
    },
    {
      label: "from",
      insertText: "from ",
      detail: "Источник данных",
      documentation: "Указывает таблицу, view или подзапрос для SELECT."
    },
    {
      label: "where",
      insertText: "where ",
      detail: "Фильтрация",
      documentation: "Ограничивает строки условием."
    },
    {
      label: "join",
      insertText: "join ",
      detail: "Соединение",
      documentation: "Соединяет строки из нескольких источников."
    },
    {
      label: "left join",
      insertText: "left join ",
      detail: "Соединение",
      documentation: "Сохраняет все строки из левого источника и подставляет совпадения справа."
    },
    {
      label: "group by",
      insertText: "group by ",
      detail: "Группировка",
      documentation: "Группирует строки для агрегатных вычислений."
    },
    {
      label: "order by",
      insertText: "order by ",
      detail: "Сортировка",
      documentation: "Управляет порядком строк в результате."
    },
    {
      label: "limit",
      insertText: "limit ",
      detail: "Ограничение",
      documentation: "Ограничивает количество возвращаемых строк."
    },
    {
      label: "insert",
      insertText: "insert ",
      detail: "Изменение данных",
      documentation: "Добавляет новые строки в таблицу."
    },
    {
      label: "update",
      insertText: "update ",
      detail: "Изменение данных",
      documentation: "Изменяет существующие строки."
    },
    {
      label: "delete",
      insertText: "delete ",
      detail: "Изменение данных",
      documentation: "Удаляет строки из таблицы."
    },
    {
      label: "create",
      insertText: "create ",
      detail: "DDL",
      documentation: "Создает новый объект БД."
    },
    {
      label: "alter",
      insertText: "alter ",
      detail: "DDL",
      documentation: "Изменяет существующий объект БД."
    },
    {
      label: "drop",
      insertText: "drop ",
      detail: "DDL",
      documentation: "Удаляет объект БД."
    },
    {
      label: "with",
      insertText: "with ",
      detail: "CTE",
      documentation: "Создает common table expression для текущего запроса."
    }
  ];

  const SQL_FUNCTIONS = [
    {
      label: "count",
      insertText: "count(${1:*})",
      detail: "Агрегатная функция",
      documentation: "Считает количество строк или непустых значений."
    },
    {
      label: "sum",
      insertText: "sum(${1:column})",
      detail: "Агрегатная функция",
      documentation: "Суммирует числовые значения."
    },
    {
      label: "avg",
      insertText: "avg(${1:column})",
      detail: "Агрегатная функция",
      documentation: "Вычисляет среднее значение."
    },
    {
      label: "coalesce",
      insertText: "coalesce(${1:value}, ${2:fallback})",
      detail: "Функция",
      documentation: "Возвращает первое непустое значение."
    },
    {
      label: "now",
      insertText: "now()",
      detail: "Функция времени",
      documentation: "Текущее время на стороне PostgreSQL."
    },
    {
      label: "date_trunc",
      insertText: "date_trunc('${1:day}', ${2:timestamp_column})",
      detail: "Функция времени",
      documentation: "Обрезает timestamp до указанной точности."
    },
    {
      label: "jsonb_build_object",
      insertText: "jsonb_build_object(${1:'key'}, ${2:value})",
      detail: "JSONB",
      documentation: "Собирает JSONB-объект из пар ключ-значение."
    },
    {
      label: "jsonb_agg",
      insertText: "jsonb_agg(${1:expression})",
      detail: "JSONB",
      documentation: "Агрегирует выражения в JSONB-массив."
    },
    {
      label: "unnest",
      insertText: "unnest(${1:array_column})",
      detail: "Массивы",
      documentation: "Разворачивает массив в набор строк."
    }
  ];

  const SQL_SNIPPETS = [
    {
      label: "snippet: select",
      insertText: "select ${1:*}\nfrom ${2:schema.table}\nwhere ${3:true}\nlimit ${4:100};",
      detail: "Шаблон",
      documentation: "Базовый шаблон SELECT с фильтром и лимитом."
    },
    {
      label: "snippet: insert",
      insertText: "insert into ${1:schema.table} (${2:column})\nvalues (${3:value});",
      detail: "Шаблон",
      documentation: "Базовый шаблон INSERT."
    },
    {
      label: "snippet: update",
      insertText: "update ${1:schema.table}\nset ${2:column} = ${3:value}\nwhere ${4:id} = ${5:value};",
      detail: "Шаблон",
      documentation: "Базовый шаблон UPDATE с WHERE."
    },
    {
      label: "snippet: with",
      insertText: "with ${1:cte_name} as (\n    ${2:select * from schema.table}\n)\nselect ${3:*}\nfrom ${1:cte_name};",
      detail: "Шаблон",
      documentation: "CTE-шаблон для многошагового чтения данных."
    }
  ];

  function keywordRange(monaco, model, position) {
    const word = model.getWordUntilPosition(position);
    return new monaco.Range(
      position.lineNumber,
      word.startColumn,
      position.lineNumber,
      word.endColumn
    );
  }

  function buildSuggestion(monaco, item, kind, range, snippet) {
    return {
      label: item.label,
      kind,
      insertText: item.insertText,
      insertTextRules: snippet ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
      detail: item.detail,
      documentation: item.documentation,
      range
    };
  }

  function buildHoverMap() {
    const map = new Map();
    [...SQL_KEYWORDS, ...SQL_FUNCTIONS].forEach(item => {
      map.set(item.label.toLowerCase(), item);
    });
    return map;
  }

  function ensureSqlSupport() {
    if (sqlSupportRegistered) {
      return;
    }
    const monaco = global.monaco;
    if (!monaco?.languages) {
      return;
    }

    const hoverMap = buildHoverMap();

    monaco.languages.registerCompletionItemProvider("sql", {
      provideCompletionItems(model, position) {
        const range = keywordRange(monaco, model, position);
        const currentWord = model.getWordUntilPosition(position).word.toLowerCase();
        const startsWithToken = (label) => currentWord === "" || label.toLowerCase().startsWith(currentWord);
        const suggestions = [
          ...SQL_KEYWORDS.filter(item => startsWithToken(item.label)).map(item =>
            buildSuggestion(monaco, item, monaco.languages.CompletionItemKind.Keyword, range, false)
          ),
          ...SQL_FUNCTIONS.filter(item => startsWithToken(item.label)).map(item =>
            buildSuggestion(monaco, item, monaco.languages.CompletionItemKind.Function, range, true)
          ),
          ...SQL_SNIPPETS.filter(item => currentWord === "" || item.label.toLowerCase().includes(currentWord)).map(item =>
            buildSuggestion(monaco, item, monaco.languages.CompletionItemKind.Snippet, range, true)
          )
        ];
        return { suggestions };
      }
    });

    monaco.languages.registerHoverProvider("sql", {
      provideHover(model, position) {
        const word = model.getWordAtPosition(position)?.word?.toLowerCase();
        if (!word) {
          return null;
        }
        const item = hoverMap.get(word);
        if (!item) {
          return null;
        }
        return {
          range: new monaco.Range(
            position.lineNumber,
            model.getWordAtPosition(position).startColumn,
            position.lineNumber,
            model.getWordAtPosition(position).endColumn
          ),
          contents: [
            { value: `**${item.label}**` },
            { value: item.detail },
            { value: item.documentation }
          ]
        };
      }
    });

    sqlSupportRegistered = true;
  }

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
    requireFn(["vs/editor/editor.main"], () => {
      ensureSqlSupport();
      callback();
    });
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
  namespace.ensureSqlSupport = ensureSqlSupport;
})(window);
