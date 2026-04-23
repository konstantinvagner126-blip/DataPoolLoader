(function initComposeMonaco(global) {
  const namespace = global.ComposeMonaco || (global.ComposeMonaco = {});
  let monacoConfigured = false;
  let sqlSupportRegistered = false;
  const sqlStatementMarkerDecorations = new WeakMap();
  const monacoVsPath = "/static/compose-app/vendor/monaco-editor/min/vs";
  const DEFAULT_SQL_OBJECT_COMPLETION_LIMIT = 8;
  const MAX_SQL_OBJECT_SUGGESTIONS = 24;
  const MAX_SQL_COLUMN_SUGGESTIONS = 48;
  // Keep alias parsing intentionally bounded and browser-safe.
  // Quoted identifiers still work for exact `schema.table.` completion,
  // but alias-aware hints only target simple unquoted FROM/JOIN/UPDATE aliases.
  const SQL_ALIAS_DEFINITION_PATTERN = /\b(?:from|join|update)\s+([A-Za-z_][A-Za-z0-9_$]*(?:\s*\.\s*[A-Za-z_][A-Za-z0-9_$]*)?)(?:\s+(?:as\s+)?)([A-Za-z_][A-Za-z0-9_$]*)/gi;
  const sqlMetadataSearchCache = new Map();
  const sqlObjectColumnsCache = new Map();
  let sqlMetadataContext = normalizeSqlMetadataContext(global.__composeSqlConsoleMetadataContext);
  let sqlObjectNavigationHandlers = normalizeSqlObjectNavigationHandlers(global.__composeSqlConsoleObjectNavigationHandlers);

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

  function normalizeSqlMetadataContext(context) {
    const selectedSourceNames = Array.isArray(context?.selectedSourceNames)
      ? [...new Set(
          context.selectedSourceNames
            .map(value => `${value ?? ""}`.trim())
            .filter(Boolean)
        )]
      : [];
    const favoriteObjects = Array.isArray(context?.favoriteObjects)
      ? deduplicateFavoriteObjects(context.favoriteObjects.map(normalizeFavoriteObject).filter(Boolean))
      : [];
    const requestedLimit = Number(context?.maxObjectsPerSource);
    const maxObjectsPerSource = Number.isFinite(requestedLimit)
      ? Math.max(1, Math.min(20, Math.trunc(requestedLimit)))
      : DEFAULT_SQL_OBJECT_COMPLETION_LIMIT;
    return {
      selectedSourceNames,
      favoriteObjects,
      maxObjectsPerSource
    };
  }

  function normalizeFavoriteObject(value) {
    const sourceName = `${value?.sourceName ?? ""}`.trim();
    const schemaName = `${value?.schemaName ?? ""}`.trim();
    const objectName = `${value?.objectName ?? ""}`.trim();
    const objectType = `${value?.objectType ?? ""}`.trim().toUpperCase();
    const tableName = `${value?.tableName ?? ""}`.trim() || null;
    if (!sourceName || !schemaName || !objectName || !objectType) {
      return null;
    }
    return {
      sourceName,
      schemaName,
      objectName,
      objectType,
      tableName
    };
  }

  function deduplicateFavoriteObjects(values) {
    const uniqueValues = new Map();
    values.forEach(value => {
      const key = [
        value.sourceName,
        value.schemaName,
        value.objectName,
        value.objectType,
        value.tableName || ""
      ].join("|");
      if (!uniqueValues.has(key)) {
        uniqueValues.set(key, value);
      }
    });
    return Array.from(uniqueValues.values());
  }

  function setSqlMetadataContext(context) {
    sqlMetadataContext = normalizeSqlMetadataContext(context);
    global.__composeSqlConsoleMetadataContext = sqlMetadataContext;
    sqlMetadataSearchCache.clear();
    sqlObjectColumnsCache.clear();
  }

  function normalizeSqlObjectNavigationHandlers(handlers) {
    return {
      openInspector: typeof handlers?.openInspector === "function" ? handlers.openInspector : null,
      openSelect: typeof handlers?.openSelect === "function" ? handlers.openSelect : null
    };
  }

  function setSqlObjectNavigationHandlers(handlers) {
    sqlObjectNavigationHandlers = normalizeSqlObjectNavigationHandlers(handlers);
    global.__composeSqlConsoleObjectNavigationHandlers = sqlObjectNavigationHandlers;
  }

  function buildCompletionContext(model, position) {
    const range = keywordRange(global.monaco, model, position);
    const linePrefix = model.getLineContent(position.lineNumber).slice(0, position.column - 1);
    const chainMatch = linePrefix.match(/((?:[A-Za-z_][A-Za-z0-9_$]*\.)*)([A-Za-z_][A-Za-z0-9_$]*)?$/);
    const qualifiers = chainMatch?.[1]
      ? chainMatch[1].split(".").filter(Boolean)
      : [];
    const token = (chainMatch?.[2] || "").toLowerCase();
    if (qualifiers.length === 0) {
      return {
        range,
        mode: "global",
        token,
        schemaName: null,
        remoteQuery: token.length >= 2 ? token : null
      };
    }
    if (qualifiers.length === 1) {
      const aliasTarget = resolveSqlAliasColumnTarget(model, position, qualifiers[0]);
      if (aliasTarget) {
        return {
          range,
          mode: "alias-columns",
          token,
          aliasName: qualifiers[0].toLowerCase(),
          schemaName: aliasTarget.schemaName,
          objectName: aliasTarget.objectName,
          remoteQuery: null
        };
      }
      const schemaName = qualifiers[0].toLowerCase();
      return {
        range,
        mode: "schema",
        token,
        schemaName,
        remoteQuery: token.length >= 2 ? token : (schemaName.length >= 2 ? schemaName : null)
      };
    }
    if (qualifiers.length === 2) {
      return {
        range,
        mode: "columns",
        token,
        schemaName: qualifiers[0].toLowerCase(),
        objectName: qualifiers[1].toLowerCase(),
        remoteQuery: null
      };
    }
    return {
      range,
      mode: "unsupported",
      token,
      schemaName: qualifiers[0].toLowerCase(),
      remoteQuery: null
    };
  }

  function resolveSqlAliasColumnTarget(model, position, aliasName) {
    const normalizedAliasName = normalizeSqlIdentifier(aliasName).toLowerCase();
    if (!normalizedAliasName) {
      return null;
    }
    const sqlPrefix = sanitizeSqlForAliasLookup(buildSqlPrefix(model, position));
    let resolvedTarget = null;
    let match;
    while ((match = SQL_ALIAS_DEFINITION_PATTERN.exec(sqlPrefix)) !== null) {
      const parsedTarget = parseSqlAliasTarget(match[1], match[2]);
      if (!parsedTarget || parsedTarget.aliasName !== normalizedAliasName) {
        continue;
      }
      resolvedTarget = parsedTarget;
    }
    return resolvedTarget;
  }

  function buildSqlPrefix(model, position) {
    return model.getValueInRange(new global.monaco.Range(1, 1, position.lineNumber, position.column));
  }

  function sanitizeSqlForAliasLookup(sql) {
    let sanitized = "";
    let inSingleQuote = false;
    let inDoubleQuote = false;
    let inLineComment = false;
    let inBlockComment = false;

    for (let index = 0; index < sql.length; index += 1) {
      const char = sql[index];
      const next = sql[index + 1];

      if (inLineComment) {
        if (char === "\n") {
          inLineComment = false;
          sanitized += "\n";
        } else {
          sanitized += " ";
        }
        continue;
      }

      if (inBlockComment) {
        if (char === "*" && next === "/") {
          sanitized += "  ";
          index += 1;
          inBlockComment = false;
        } else {
          sanitized += char === "\n" ? "\n" : " ";
        }
        continue;
      }

      if (!inSingleQuote && !inDoubleQuote && char === "-" && next === "-") {
        sanitized += "  ";
        index += 1;
        inLineComment = true;
        continue;
      }

      if (!inSingleQuote && !inDoubleQuote && char === "/" && next === "*") {
        sanitized += "  ";
        index += 1;
        inBlockComment = true;
        continue;
      }

      if (inSingleQuote) {
        sanitized += char === "\n" ? "\n" : " ";
        if (char === "'" && next === "'") {
          sanitized += " ";
          index += 1;
        } else if (char === "'") {
          inSingleQuote = false;
        }
        continue;
      }

      if (inDoubleQuote) {
        sanitized += char;
        if (char === '"' && next === '"') {
          sanitized += '"';
          index += 1;
        } else if (char === '"') {
          inDoubleQuote = false;
        }
        continue;
      }

      if (char === "'") {
        sanitized += " ";
        inSingleQuote = true;
        continue;
      }

      if (char === '"') {
        sanitized += '"';
        inDoubleQuote = true;
        continue;
      }

      sanitized += char;
    }

    return sanitized;
  }

  function parseSqlAliasTarget(objectReference, aliasName) {
    const normalizedAliasName = normalizeSqlIdentifier(aliasName).toLowerCase();
    const normalizedReference = `${objectReference ?? ""}`.replace(/\s*\.\s*/g, ".").trim();
    if (!normalizedAliasName || !normalizedReference) {
      return null;
    }
    const parts = normalizedReference.split(".").map(normalizeSqlIdentifier).filter(Boolean);
    if (parts.length === 0 || parts.length > 2) {
      return null;
    }
    return {
      aliasName: normalizedAliasName,
      schemaName: parts.length === 2 ? parts[0].toLowerCase() : null,
      objectName: parts[parts.length - 1].toLowerCase()
    };
  }

  function normalizeSqlIdentifier(value) {
    const trimmed = `${value ?? ""}`.trim();
    if (trimmed.startsWith('"') && trimmed.endsWith('"') && trimmed.length >= 2) {
      return trimmed.slice(1, -1).replace(/""/g, '"');
    }
    return trimmed;
  }

  function extractSqlReferenceAtPosition(model, position) {
    const lineContent = model.getLineContent(position.lineNumber);
    const lineIndex = Math.max(0, position.column - 1);
    const isReferenceChar = char => /[A-Za-z0-9_$".]/.test(char);
    let startIndex = lineIndex;
    let endIndex = lineIndex;

    while (startIndex > 0 && isReferenceChar(lineContent[startIndex - 1])) {
      startIndex -= 1;
    }
    while (endIndex < lineContent.length && isReferenceChar(lineContent[endIndex])) {
      endIndex += 1;
    }

    const rawReference = lineContent.slice(startIndex, endIndex).trim();
    const parts = splitSqlReferenceChain(rawReference);
    if (parts.length === 0) {
      return null;
    }

    return {
      rawReference,
      parts,
      startColumn: startIndex + 1,
      endColumn: endIndex + 1
    };
  }

  function splitSqlReferenceChain(value) {
    const rawValue = `${value ?? ""}`.trim();
    if (!rawValue || rawValue === ".") {
      return [];
    }
    const parts = [];
    let current = "";
    let inDoubleQuote = false;

    for (let index = 0; index < rawValue.length; index += 1) {
      const char = rawValue[index];
      const next = rawValue[index + 1];
      if (char === '"') {
        current += char;
        if (inDoubleQuote && next === '"') {
          current += next;
          index += 1;
          continue;
        }
        inDoubleQuote = !inDoubleQuote;
        continue;
      }
      if (char === "." && !inDoubleQuote) {
        if (current.trim() !== "") {
          parts.push(normalizeSqlIdentifier(current));
        }
        current = "";
        continue;
      }
      current += char;
    }

    if (current.trim() !== "") {
      parts.push(normalizeSqlIdentifier(current));
    }

    return parts.map(part => `${part ?? ""}`.trim()).filter(Boolean);
  }

  function buildSqlObjectNavigationContext(model, position) {
    const reference = extractSqlReferenceAtPosition(model, position);
    if (!reference) {
      return null;
    }
    if (reference.parts.length === 2) {
      const aliasTarget = resolveSqlAliasColumnTarget(model, position, reference.parts[0]);
      if (aliasTarget) {
        return {
          range: new global.monaco.Range(position.lineNumber, reference.startColumn, position.lineNumber, reference.endColumn),
          sourceMode: "alias-columns",
          schemaName: aliasTarget.schemaName?.toLowerCase() || null,
          objectName: aliasTarget.objectName.toLowerCase(),
          referenceText: reference.rawReference
        };
      }
      return {
        range: new global.monaco.Range(position.lineNumber, reference.startColumn, position.lineNumber, reference.endColumn),
        sourceMode: "qualified-object",
        schemaName: reference.parts[0].toLowerCase(),
        objectName: reference.parts[1].toLowerCase(),
        referenceText: reference.rawReference
      };
    }
    if (reference.parts.length === 1 && isLikelyBareObjectReference(model, position, reference)) {
      return {
        range: new global.monaco.Range(position.lineNumber, reference.startColumn, position.lineNumber, reference.endColumn),
        sourceMode: "bare-object",
        schemaName: null,
        objectName: reference.parts[0].toLowerCase(),
        referenceText: reference.rawReference
      };
    }
    return null;
  }

  function isLikelyBareObjectReference(model, position, reference) {
    const linePrefix = model
      .getLineContent(position.lineNumber)
      .slice(0, Math.max(0, reference.startColumn - 1));
    const sanitizedPrefix = sanitizeSqlForAliasLookup(linePrefix);
    return /\b(?:from|join|update|into|table|truncate)\s*$/i.test(sanitizedPrefix);
  }

  function buildObjectSearchCacheKey(request) {
    return JSON.stringify({
      query: request.query.toLowerCase(),
      selectedSourceNames: request.selectedSourceNames,
      maxObjectsPerSource: request.maxObjectsPerSource
    });
  }

  function buildObjectColumnsCacheKey(request) {
    return JSON.stringify({
      schemaName: request.schemaName.toLowerCase(),
      objectName: request.objectName.toLowerCase(),
      objectType: request.objectType.toUpperCase(),
      selectedSourceNames: request.selectedSourceNames
    });
  }

  async function loadSqlMetadataSearchEntries(request) {
    const cacheKey = buildObjectSearchCacheKey(request);
    if (sqlMetadataSearchCache.has(cacheKey)) {
      return sqlMetadataSearchCache.get(cacheKey);
    }
    const promise = fetch("/api/sql-console/objects/search", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request)
    })
      .then(async response => {
        if (!response.ok) {
          return [];
        }
        const payload = await response.json();
        return flattenSqlMetadataSearchEntries(payload?.sourceResults);
      })
      .catch(() => []);
    sqlMetadataSearchCache.set(cacheKey, promise);
    return promise;
  }

  async function loadSqlObjectColumns(request) {
    const cacheKey = buildObjectColumnsCacheKey(request);
    if (sqlObjectColumnsCache.has(cacheKey)) {
      return sqlObjectColumnsCache.get(cacheKey);
    }
    const promise = fetch("/api/sql-console/objects/columns", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request)
    })
      .then(async response => {
        if (!response.ok) {
          return [];
        }
        const payload = await response.json();
        return flattenSqlObjectColumns(payload?.sourceResults);
      })
      .catch(() => []);
    sqlObjectColumnsCache.set(cacheKey, promise);
    return promise;
  }

  function flattenSqlMetadataSearchEntries(sourceResults) {
    if (!Array.isArray(sourceResults)) {
      return [];
    }
    return sourceResults.flatMap(sourceResult => {
      if (sourceResult?.status !== "SUCCESS" || !Array.isArray(sourceResult.objects)) {
        return [];
      }
      return sourceResult.objects.map(dbObject => ({
        sourceName: `${sourceResult.sourceName ?? ""}`.trim(),
        dbObject: {
          schemaName: `${dbObject?.schemaName ?? ""}`.trim(),
          objectName: `${dbObject?.objectName ?? ""}`.trim(),
          objectType: `${dbObject?.objectType ?? ""}`.trim().toUpperCase(),
          tableName: `${dbObject?.tableName ?? ""}`.trim() || null
        }
      })).filter(entry => entry.sourceName && entry.dbObject.schemaName && entry.dbObject.objectName && entry.dbObject.objectType);
    });
  }

  function flattenSqlObjectColumns(sourceResults) {
    if (!Array.isArray(sourceResults)) {
      return [];
    }
    return sourceResults.flatMap(sourceResult => {
      if (sourceResult?.status !== "SUCCESS" || !Array.isArray(sourceResult.columns)) {
        return [];
      }
      return sourceResult.columns
        .map(column => ({
          sourceName: `${sourceResult.sourceName ?? ""}`.trim(),
          name: `${column?.name ?? ""}`.trim(),
          type: `${column?.type ?? ""}`.trim(),
          nullable: Boolean(column?.nullable)
        }))
        .filter(column => column.sourceName && column.name);
    });
  }

  function filterFavoriteObjectEntries(completionContext) {
    const selectedSourceNames = sqlMetadataContext.selectedSourceNames;
    const hasSelectedSources = selectedSourceNames.length > 0;
    return sqlMetadataContext.favoriteObjects
      .filter(value => !hasSelectedSources || selectedSourceNames.includes(value.sourceName))
      .map(value => ({
        sourceName: value.sourceName,
        dbObject: {
          schemaName: value.schemaName,
          objectName: value.objectName,
          objectType: value.objectType,
          tableName: value.tableName
        }
      }))
      .filter(entry => matchesObjectEntryCompletionContext(entry, completionContext));
  }

  function matchesObjectEntryCompletionContext(entry, completionContext) {
    const dbObject = entry.dbObject;
    const token = completionContext.token;
    if (completionContext.mode === "schema") {
      if (dbObject.objectType === "SCHEMA") {
        return false;
      }
      if (dbObject.schemaName.toLowerCase() !== completionContext.schemaName) {
        return false;
      }
      return token === "" || dbObject.objectName.toLowerCase().startsWith(token);
    }
    if (completionContext.mode !== "global") {
      return false;
    }
    if (token === "") {
      return false;
    }
    const schemaName = dbObject.schemaName.toLowerCase();
    const objectName = dbObject.objectName.toLowerCase();
    const qualifiedName = `${schemaName}.${objectName}`;
    return schemaName.startsWith(token) || objectName.startsWith(token) || qualifiedName.startsWith(token);
  }

  function matchesColumnTargetEntry(entry, completionContext) {
    if (completionContext.mode !== "columns" && completionContext.mode !== "alias-columns") {
      return false;
    }
    const dbObject = entry.dbObject;
    if (!isColumnLookupObjectType(dbObject.objectType)) {
      return false;
    }
    if (dbObject.objectName.toLowerCase() !== completionContext.objectName) {
      return false;
    }
    if (completionContext.schemaName == null) {
      return true;
    }
    return dbObject.schemaName.toLowerCase() === completionContext.schemaName;
  }

  function matchesExactNavigationEntry(entry, navigationContext) {
    const dbObject = entry.dbObject;
    if (dbObject.objectName.toLowerCase() !== navigationContext.objectName) {
      return false;
    }
    if (navigationContext.schemaName == null) {
      return true;
    }
    return dbObject.schemaName.toLowerCase() === navigationContext.schemaName;
  }

  function findExactFavoriteNavigationEntries(navigationContext) {
    const selectedSourceNames = sqlMetadataContext.selectedSourceNames;
    const hasSelectedSources = selectedSourceNames.length > 0;
    return sqlMetadataContext.favoriteObjects
      .filter(value => !hasSelectedSources || selectedSourceNames.includes(value.sourceName))
      .map(value => ({
        sourceName: value.sourceName,
        dbObject: {
          schemaName: value.schemaName,
          objectName: value.objectName,
          objectType: value.objectType,
          tableName: value.tableName
        }
      }))
      .filter(entry => matchesExactNavigationEntry(entry, navigationContext));
  }

  function isColumnLookupObjectType(objectType) {
    return objectType === "TABLE" || objectType === "VIEW" || objectType === "MATERIALIZED_VIEW";
  }

  function findExactFavoriteObjectEntries(completionContext) {
    const selectedSourceNames = sqlMetadataContext.selectedSourceNames;
    const hasSelectedSources = selectedSourceNames.length > 0;
    return sqlMetadataContext.favoriteObjects
      .filter(value => !hasSelectedSources || selectedSourceNames.includes(value.sourceName))
      .map(value => ({
        sourceName: value.sourceName,
        dbObject: {
          schemaName: value.schemaName,
          objectName: value.objectName,
          objectType: value.objectType,
          tableName: value.tableName
        }
      }))
      .filter(entry => matchesColumnTargetEntry(entry, completionContext));
  }

  function mergeUniqueObjectEntries(localEntries, remoteEntries) {
    const uniqueEntries = new Map();
    [...localEntries, ...remoteEntries].forEach(entry => {
      const dbObject = entry.dbObject;
      const key = [
        entry.sourceName,
        dbObject.schemaName,
        dbObject.objectName,
        dbObject.objectType,
        dbObject.tableName || ""
      ].join("|");
      if (!uniqueEntries.has(key)) {
        uniqueEntries.set(key, entry);
      }
    });
    return Array.from(uniqueEntries.values());
  }

  function pickExactObjectTarget(localEntries, remoteEntries) {
    const mergedEntries = mergeUniqueObjectEntries(localEntries, remoteEntries);
    const preferredTypeOrder = ["TABLE", "VIEW", "MATERIALIZED_VIEW"];
    return preferredTypeOrder
      .map(type => mergedEntries.find(entry => entry.dbObject.objectType === type))
      .find(Boolean) || null;
  }

  function pickSqlObjectNavigationTarget(localEntries, remoteEntries) {
    const mergedEntries = mergeUniqueObjectEntries(localEntries, remoteEntries);
    if (mergedEntries.length === 0) {
      return null;
    }
    const preferredSourceNames = sqlMetadataContext.selectedSourceNames;
    if (preferredSourceNames.length > 0) {
      const preferredEntry = preferredSourceNames
        .map(sourceName => mergedEntries.find(entry => entry.sourceName === sourceName))
        .find(Boolean);
      if (preferredEntry) {
        return preferredEntry;
      }
    }
    return mergedEntries[0] || null;
  }

  async function resolveSqlObjectNavigationTarget(model, position) {
    if (!sqlObjectNavigationHandlers.openInspector && !sqlObjectNavigationHandlers.openSelect) {
      return null;
    }
    const navigationContext = buildSqlObjectNavigationContext(model, position);
    if (!navigationContext) {
      return null;
    }
    const localEntries = findExactFavoriteNavigationEntries(navigationContext);
    const remoteEntries = navigationContext.objectName.length >= 2
      ? (await loadSqlMetadataSearchEntries({
          query: navigationContext.objectName,
          selectedSourceNames: sqlMetadataContext.selectedSourceNames,
          maxObjectsPerSource: sqlMetadataContext.maxObjectsPerSource
        })).filter(entry => matchesExactNavigationEntry(entry, navigationContext))
      : [];
    const targetEntry = pickSqlObjectNavigationTarget(localEntries, remoteEntries);
    if (!targetEntry) {
      return null;
    }
    return {
      sourceName: targetEntry.sourceName,
      schemaName: targetEntry.dbObject.schemaName,
      objectName: targetEntry.dbObject.objectName,
      objectType: targetEntry.dbObject.objectType,
      range: navigationContext.range,
      referenceText: navigationContext.referenceText
    };
  }

  function buildColumnSuggestion(monaco, column, completionContext, index) {
    const typeSuffix = column.types.length == 1 ? column.types[0] : column.types.join(" / ");
    return {
      label: column.name,
      kind: monaco.languages.CompletionItemKind.Field,
      insertText: quoteSqlIdentifierIfNeeded(column.name),
      detail: `column · ${typeSuffix}`,
      documentation: buildColumnDocumentation(column),
      range: completionContext.range,
      sortText: `${index.toString().padStart(4, "0")}-${column.name.toLowerCase()}`
    };
  }

  function buildColumnDocumentation(column) {
    const lines = [
      `**${column.name}**`,
      `Тип: ${column.types.join(", ")}`,
      `NULL: ${column.nullabilityLabel}`,
      `Source: ${column.sourceNames.join(", ")}`
    ];
    return lines.join("\n\n");
  }

  function mergeSqlColumnEntries(entries) {
    const mergedEntries = new Map();
    entries.forEach(entry => {
      const key = entry.name.toLowerCase();
      const existing = mergedEntries.get(key);
      if (existing == null) {
        mergedEntries.set(key, {
          name: entry.name,
          types: entry.type ? [entry.type] : [],
          nullability: new Set([entry.nullable]),
          sourceNames: [entry.sourceName]
        });
        return;
      }
      if (entry.type && !existing.types.includes(entry.type)) {
        existing.types.push(entry.type);
      }
      existing.nullability.add(entry.nullable);
      if (!existing.sourceNames.includes(entry.sourceName)) {
        existing.sourceNames.push(entry.sourceName);
      }
    });
    return Array.from(mergedEntries.values()).map(entry => ({
      name: entry.name,
      types: entry.types.length > 0 ? entry.types : ["unknown"],
      sourceNames: entry.sourceNames,
      nullabilityLabel: entry.nullability.size === 1
        ? (entry.nullability.has(true) ? "YES" : "NO")
        : "MIXED"
    }));
  }

  async function buildSqlColumnSuggestions(monaco, completionContext) {
    const localEntries = findExactFavoriteObjectEntries(completionContext);
    const remoteEntries = completionContext.objectName.length >= 2
      ? (await loadSqlMetadataSearchEntries({
          query: completionContext.objectName,
          selectedSourceNames: sqlMetadataContext.selectedSourceNames,
          maxObjectsPerSource: sqlMetadataContext.maxObjectsPerSource
        })).filter(entry => matchesColumnTargetEntry(entry, completionContext))
      : [];

    const target = pickExactObjectTarget(localEntries, remoteEntries);
    if (!target) {
      return [];
    }

    const columnEntries = await loadSqlObjectColumns({
      schemaName: target.dbObject.schemaName,
      objectName: target.dbObject.objectName,
      objectType: target.dbObject.objectType,
      selectedSourceNames: sqlMetadataContext.selectedSourceNames
    });
    const token = completionContext.token;
    return mergeSqlColumnEntries(columnEntries)
      .filter(entry => token === "" || entry.name.toLowerCase().startsWith(token))
      .slice(0, MAX_SQL_COLUMN_SUGGESTIONS)
      .map((entry, index) => buildColumnSuggestion(monaco, entry, completionContext, index));
  }

  function buildSqlObjectSuggestions(monaco, entries, completionContext) {
    return entries.map((entry, index) =>
      buildSqlObjectSuggestion(monaco, entry, completionContext, index)
    );
  }

  function buildSqlObjectSuggestion(monaco, entry, completionContext, index) {
    const dbObject = entry.dbObject;
    const label = dbObject.objectType === "SCHEMA"
      ? dbObject.schemaName
      : `${dbObject.schemaName}.${dbObject.objectName}`;
    const insertText = buildSqlObjectInsertText(dbObject, completionContext.mode);
    return {
      label,
      kind: completionKindForObjectType(monaco, dbObject.objectType),
      insertText,
      detail: buildSqlObjectDetail(entry),
      documentation: buildSqlObjectDocumentation(entry),
      range: completionContext.range,
      sortText: `${index.toString().padStart(4, "0")}-${label.toLowerCase()}`
    };
  }

  function buildSqlObjectInsertText(dbObject, completionMode) {
    if (completionMode === "schema") {
      return quoteSqlIdentifierIfNeeded(dbObject.objectName);
    }
    if (dbObject.objectType === "SCHEMA") {
      return quoteSqlIdentifierIfNeeded(dbObject.schemaName);
    }
    return `${quoteSqlIdentifierIfNeeded(dbObject.schemaName)}.${quoteSqlIdentifierIfNeeded(dbObject.objectName)}`;
  }

  function buildSqlObjectDetail(entry) {
    const dbObject = entry.dbObject;
    return [
      entry.sourceName,
      dbObject.objectType,
      dbObject.tableName ? `table ${dbObject.tableName}` : null
    ].filter(Boolean).join(" · ");
  }

  function buildSqlObjectDocumentation(entry) {
    const dbObject = entry.dbObject;
    const details = [
      `**${dbObject.objectType}**`,
      `Source: ${entry.sourceName}`,
      `Schema: ${dbObject.schemaName}`
    ];
    if (dbObject.objectType !== "SCHEMA") {
      details.push(`Object: ${dbObject.objectName}`);
    }
    if (dbObject.tableName) {
      details.push(`Table: ${dbObject.tableName}`);
    }
    return details.join("\n\n");
  }

  function supportsSqlObjectSelectNavigation(objectType) {
    return objectType === "TABLE" || objectType === "VIEW" || objectType === "MATERIALIZED_VIEW";
  }

  function buildSqlObjectNavigationHover(target) {
    const contents = [
      { value: `**${target.schemaName}.${target.objectName}**` },
      { value: `${target.objectType} · ${target.sourceName}` }
    ];
    const quickActions = ["Inspector"];
    if (supportsSqlObjectSelectNavigation(target.objectType)) {
      quickActions.push("Columns", "SELECT");
    } else {
      quickActions.push("Columns");
    }
    contents.push({
      value: `Быстрые действия: ${quickActions.join(" · ")}\n\nПКМ: открыть actions Monaco`
    });
    return {
      range: target.range,
      contents
    };
  }

  async function runSqlObjectNavigationAction(editor, actionKind) {
    if (!editor || !sqlObjectNavigationHandlers) {
      return null;
    }
    const model = editor.getModel?.();
    const position = editor.getPosition?.();
    if (!model || !position) {
      return null;
    }
    const target = await resolveSqlObjectNavigationTarget(model, position);
    if (!target) {
      return null;
    }
    if (actionKind === "inspector") {
      return sqlObjectNavigationHandlers.openInspector?.(target, null) ?? null;
    }
    if (actionKind === "columns") {
      return sqlObjectNavigationHandlers.openInspector?.(target, "columns") ?? null;
    }
    if (actionKind === "select" && supportsSqlObjectSelectNavigation(target.objectType)) {
      return sqlObjectNavigationHandlers.openSelect?.(target) ?? null;
    }
    return null;
  }

  function registerSqlObjectNavigationActions(editor) {
    editor.addAction({
      id: "compose-sql-open-object-inspector",
      label: "Открыть inspector объекта",
      contextMenuGroupId: "navigation",
      contextMenuOrder: 1.2,
      run: () => runSqlObjectNavigationAction(editor, "inspector")
    });
    editor.addAction({
      id: "compose-sql-open-object-columns",
      label: "Открыть columns metadata",
      contextMenuGroupId: "navigation",
      contextMenuOrder: 1.3,
      run: () => runSqlObjectNavigationAction(editor, "columns")
    });
    editor.addAction({
      id: "compose-sql-open-object-select",
      label: "Открыть SELECT в новой вкладке консоли",
      contextMenuGroupId: "navigation",
      contextMenuOrder: 1.4,
      run: () => runSqlObjectNavigationAction(editor, "select")
    });
  }

  function completionKindForObjectType(monaco, objectType) {
    switch (objectType) {
      case "SCHEMA":
        return monaco.languages.CompletionItemKind.Module;
      case "INDEX":
        return monaco.languages.CompletionItemKind.Reference;
      case "SEQUENCE":
        return monaco.languages.CompletionItemKind.Value;
      case "TRIGGER":
        return monaco.languages.CompletionItemKind.Event;
      case "VIEW":
      case "MATERIALIZED_VIEW":
        return monaco.languages.CompletionItemKind.Interface;
      case "TABLE":
      default:
        return monaco.languages.CompletionItemKind.Struct;
    }
  }

  function quoteSqlIdentifierIfNeeded(value) {
    if (/^[a-z_][a-z0-9_$]*$/.test(value)) {
      return value;
    }
    return `"${`${value}`.replace(/"/g, "\"\"")}"`;
  }

  async function buildSqlMetadataSuggestions(monaco, model, position) {
    const completionContext = buildCompletionContext(model, position);
    if (completionContext.mode === "unsupported") {
      return [];
    }
    if (completionContext.mode === "columns") {
      return buildSqlColumnSuggestions(monaco, completionContext);
    }

    const localEntries = filterFavoriteObjectEntries(completionContext);
    const remoteEntries = completionContext.remoteQuery
      ? (await loadSqlMetadataSearchEntries({
          query: completionContext.remoteQuery,
          selectedSourceNames: sqlMetadataContext.selectedSourceNames,
          maxObjectsPerSource: sqlMetadataContext.maxObjectsPerSource
        })).filter(entry => matchesObjectEntryCompletionContext(entry, completionContext))
      : [];

    return buildSqlObjectSuggestions(
      monaco,
      mergeUniqueObjectEntries(localEntries, remoteEntries).slice(0, MAX_SQL_OBJECT_SUGGESTIONS),
      completionContext
    );
  }

  function buildLocalSuggestions(monaco, model, position) {
    const range = keywordRange(monaco, model, position);
    const currentWord = model.getWordUntilPosition(position).word.toLowerCase();
    const startsWithToken = label => currentWord === "" || label.toLowerCase().startsWith(currentWord);
    return [
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
      triggerCharacters: ["."],
      async provideCompletionItems(model, position) {
        const localSuggestions = buildLocalSuggestions(monaco, model, position);
        const metadataSuggestions = await buildSqlMetadataSuggestions(monaco, model, position);
        return { suggestions: [...localSuggestions, ...metadataSuggestions] };
      }
    });

    monaco.languages.registerHoverProvider("sql", {
      async provideHover(model, position) {
        const objectHover = await resolveSqlObjectNavigationTarget(model, position);
        if (objectHover) {
          return buildSqlObjectNavigationHover(objectHover);
        }
        const wordAtPosition = model.getWordAtPosition(position);
        const word = wordAtPosition?.word?.toLowerCase();
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
            wordAtPosition.startColumn,
            position.lineNumber,
            wordAtPosition.endColumn
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
    const editor = global.monaco.editor.create(element, {
      theme: "vs",
      automaticLayout: true,
      minimap: { enabled: false },
      ...options
    });
    if (options?.language === "sql" && options?.sqlObjectNavigation === true) {
      registerSqlObjectNavigationActions(editor);
    }
    return editor;
  }

  function setSqlStatementMarkers(editor, markers) {
    if (!editor || !global.monaco?.Range) {
      return;
    }
    const previousDecorations = sqlStatementMarkerDecorations.get(editor) || [];
    const nextDecorations = [];
    (Array.isArray(markers) ? markers : []).forEach(marker => {
      const startLine = Number(marker?.startLine);
      const endLine = Number(marker?.endLine);
      if (!Number.isFinite(startLine) || startLine < 1) {
        return;
      }
      const normalizedEndLine = Number.isFinite(endLine) && endLine >= startLine ? endLine : startLine;
      const status = `${marker?.status ?? "success"}`.trim().toLowerCase() || "success";
      const hoverMessage = buildSqlStatementMarkerHoverMessage(marker);
      nextDecorations.push({
        range: new global.monaco.Range(startLine, 1, normalizedEndLine, 1),
        options: {
          isWholeLine: true,
          className: `compose-sql-statement-block compose-sql-statement-block-${status}`,
          hoverMessage
        }
      });
      nextDecorations.push({
        range: new global.monaco.Range(startLine, 1, startLine, 1),
        options: {
          isWholeLine: true,
          glyphMarginClassName: `compose-sql-statement-glyph compose-sql-statement-glyph-${status}`,
          glyphMarginHoverMessage: hoverMessage,
          stickiness: global.monaco.editor.TrackedRangeStickiness.NeverGrowsWhenTypingAtEdges
        }
      });
    });
    const appliedDecorations = editor.deltaDecorations(previousDecorations, nextDecorations);
    sqlStatementMarkerDecorations.set(editor, appliedDecorations);
  }

  function buildSqlStatementMarkerHoverMessage(marker) {
    const parts = [];
    const title = `${marker?.title ?? ""}`.trim();
    if (title) {
      parts.push({ value: `**${title}**` });
    }
    const details = Array.isArray(marker?.details) ? marker.details : [];
    details
      .map(value => `${value ?? ""}`.trim())
      .filter(Boolean)
      .forEach(value => {
        parts.push({ value });
      });
    return parts.length > 0 ? parts : undefined;
  }

  namespace.withMonacoReady = withMonacoReady;
  namespace.createMonacoEditor = createMonacoEditor;
  namespace.ensureSqlSupport = ensureSqlSupport;
  namespace.setSqlMetadataContext = setSqlMetadataContext;
  namespace.setSqlObjectNavigationHandlers = setSqlObjectNavigationHandlers;
  namespace.setSqlStatementMarkers = setSqlStatementMarkers;
})(window);
