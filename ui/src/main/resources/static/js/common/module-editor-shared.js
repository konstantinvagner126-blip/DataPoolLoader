(function initDataPoolModuleEditorShared(global) {
  const common = global.DataPoolCommon || {};
  const namespace = global.DataPoolModuleEditorShared || (global.DataPoolModuleEditorShared = {});
  const { escapeHtml } = common;

  function createSqlCatalogController({
    refs,
    editors,
    getSession,
    getSqlContents,
    setSqlContents,
    getCurrentPath,
    setCurrentPath,
    getFormState,
    formController,
    onDraftStateChange,
    onCatalogChanged,
  }) {
    function sqlFiles() {
      return getSession?.()?.module?.sqlFiles || [];
    }

    function usageMap() {
      return buildSqlResourceUsageMap(getFormState?.());
    }

    function selectedFile() {
      const path = getCurrentPath?.();
      return sqlFiles().find(file => file.path === path) || null;
    }

    function render() {
      const files = sqlFiles();
      const currentPath = getCurrentPath?.();
      const selectedPath = files.some(file => file.path === currentPath) ? currentPath : (files[0]?.path || null);
      if (selectedPath !== currentPath) {
        setCurrentPath?.(selectedPath);
      }

      renderList();
      renderWorkspace();
      updateEditorValue();
    }

    function renderList() {
      const files = sqlFiles();
      const currentPath = getCurrentPath?.();
      const usages = usageMap();
      refs.sqlCatalogList.innerHTML = files.length > 0
        ? files.map(file => `
          <button type="button" class="sql-catalog-item ${file.path === currentPath ? 'active' : ''}" data-sql-resource-path="${escapeHtml(file.path)}">
            <div class="sql-catalog-item-title">${escapeHtml(file.path)}</div>
            <div class="sql-catalog-item-meta">${escapeHtml(buildSqlUsageSummary(file.path, usages))}</div>
          </button>
        `).join('')
        : '<div class="text-secondary small">SQL-ресурсы пока не созданы.</div>';

      refs.sqlCatalogList.querySelectorAll('[data-sql-resource-path]').forEach(button => {
        button.addEventListener('click', () => {
          select(button.dataset.sqlResourcePath || null);
        });
      });
    }

    function renderWorkspace() {
      const file = selectedFile();
      const usages = usageMap();

      if (!file) {
        refs.sqlResourceTitle.textContent = 'SQL-ресурс не выбран';
        refs.sqlResourceMeta.innerHTML = '<span class="text-secondary">Создай новый SQL или выбери существующий ресурс.</span>';
        refs.sqlResourceUsage.innerHTML = '<span class="text-secondary">Usage пока недоступен.</span>';
        refs.sqlRenameButton.disabled = true;
        refs.sqlDeleteButton.disabled = true;
        return;
      }

      refs.sqlResourceTitle.textContent = file.path;
      const resourceName = sqlResourceDisplayName(file);
      refs.sqlResourceMeta.innerHTML = `
        <span class="sql-resource-chip">
          ${escapeHtml(resourceName)}
        </span>
        <span class="sql-resource-chip">${escapeHtml(file.path)}</span>
        <span class="sql-resource-chip">${escapeHtml(file.exists === false ? 'Отсутствует' : 'Доступен')}</span>
      `;

      const items = usages.get(file.path) || [];
      refs.sqlResourceUsage.innerHTML = items.length > 0
        ? items.map(item => `<span class="sql-resource-usage-badge">${escapeHtml(item)}</span>`).join('')
        : '<span class="sql-resource-usage-badge sql-resource-usage-badge-muted">Не используется</span>';

      refs.sqlRenameButton.disabled = false;
      refs.sqlDeleteButton.disabled = false;
    }

    function updateEditorValue() {
      if (!editors.sqlEditor) {
        return;
      }
      const path = getCurrentPath?.();
      const contents = getSqlContents?.() || {};
      editors.sqlEditor.setValue(path ? (contents[path] || '') : '');
    }

    function select(path) {
      setCurrentPath?.(path);
      render();
    }

    function createResource() {
      const rawName = global.prompt('Введите имя SQL-ресурса:');
      const nextPath = normalizeSqlResourceKey(rawName);
      if (!nextPath) {
        return;
      }
      if (sqlFiles().some(file => file.path === nextPath)) {
        global.alert(`SQL-ресурс '${nextPath}' уже существует.`);
        return;
      }

      getSession().module.sqlFiles = [
        ...sqlFiles(),
        {
          label: defaultSqlLabel(nextPath),
          path: nextPath,
          content: '',
          exists: true,
        },
      ].sort((left, right) => left.path.localeCompare(right.path));
      setSqlContents({
        ...(getSqlContents?.() || {}),
        [nextPath]: '',
      });
      setCurrentPath?.(nextPath);
      formController?.refreshSqlResources?.();
      onCatalogChanged?.();
      onDraftStateChange?.();
      render();
    }

    async function renameSelected() {
      const file = selectedFile();
      if (!file) {
        return;
      }
      const rawName = global.prompt('Введите новое имя SQL-ресурса:', file.path);
      const nextPath = normalizeSqlResourceKey(rawName, file.path);
      if (!nextPath || nextPath === file.path) {
        return;
      }
      if (sqlFiles().some(item => item.path === nextPath)) {
        global.alert(`SQL-ресурс '${nextPath}' уже существует.`);
        return;
      }

      const previousFiles = [...sqlFiles()];
      const previousContents = { ...(getSqlContents?.() || {}) };
      getSession().module.sqlFiles = previousFiles
        .map(item => item.path === file.path ? { ...item, path: nextPath } : item)
        .sort((left, right) => left.path.localeCompare(right.path));
      const nextContents = { ...previousContents };
      nextContents[nextPath] = nextContents[file.path] || '';
      delete nextContents[file.path];
      setSqlContents(nextContents);
      setCurrentPath?.(nextPath);

      try {
        await formController?.renameSqlResource?.(file.path, nextPath);
      } catch (error) {
        getSession().module.sqlFiles = previousFiles;
        setSqlContents(previousContents);
        setCurrentPath?.(file.path);
        throw error;
      }

      formController?.refreshSqlResources?.();
      onCatalogChanged?.();
      onDraftStateChange?.();
      render();
    }

    function deleteSelected() {
      const file = selectedFile();
      if (!file) {
        return;
      }
      const items = usageMap().get(file.path) || [];
      if (items.length > 0) {
        global.alert(`Нельзя удалить SQL-ресурс, пока он используется: ${items.join(', ')}`);
        return;
      }
      if (!global.confirm(`Удалить SQL-ресурс '${file.path}'?`)) {
        return;
      }

      getSession().module.sqlFiles = sqlFiles().filter(item => item.path !== file.path);
      const nextContents = { ...(getSqlContents?.() || {}) };
      delete nextContents[file.path];
      setSqlContents(nextContents);
      setCurrentPath?.(sqlFiles()[0]?.path || null);
      formController?.refreshSqlResources?.();
      onCatalogChanged?.();
      onDraftStateChange?.();
      render();
    }

    refs.sqlCreateButton?.addEventListener('click', createResource);
    refs.sqlRenameButton?.addEventListener('click', () => {
      Promise.resolve(renameSelected()).catch(error => {
        global.alert(error?.message || 'Не удалось переименовать SQL-ресурс.');
      });
    });
    refs.sqlDeleteButton?.addEventListener('click', deleteSelected);

    return {
      render,
      select,
      createResource,
      renameSelected,
      deleteSelected,
    };
  }

  function buildSqlResourceUsageMap(formState) {
    const usages = new Map();
    if (!formState || typeof formState !== 'object') {
      return usages;
    }
    addUsage(usages, formState.commonSqlFile, 'SQL по умолчанию');
    (Array.isArray(formState.sources) ? formState.sources : []).forEach(source => {
      if (!source?.sqlFile) {
        return;
      }
      addUsage(usages, source.sqlFile, `Источник: ${source.name || '-'}`);
    });
    return usages;
  }

  function addUsage(map, sqlPath, label) {
    if (!sqlPath) {
      return;
    }
    const items = map.get(sqlPath) || [];
    if (!items.includes(label)) {
      items.push(label);
    }
    map.set(sqlPath, items);
  }

  function buildSqlUsageSummary(path, usages) {
    const items = usages.get(path) || [];
    if (items.length === 0) {
      return 'Не используется';
    }
    if (items.length === 1) {
      return items[0];
    }
    return 'Используется в нескольких местах';
  }

  function renderModuleMetadata(container, session) {
    if (!container) {
      return;
    }
    if (!session?.module) {
      container.innerHTML = '<div class="text-secondary small">Модуль не выбран.</div>';
      return;
    }

    const module = session.module;
    const tags = Array.isArray(module.tags) && module.tags.length > 0
      ? module.tags.map(tag => `<span class="module-tag">${escapeHtml(tag)}</span>`).join('')
      : '<span class="text-secondary">не заданы</span>';

    const rows = [
      renderMetadataRow('Код модуля', `<code>${escapeHtml(module.id)}</code>`, true),
      renderMetadataRow('Название', escapeHtml(module.title), true),
      renderMetadataRow('Путь конфигурации', `<code>${escapeHtml(module.configPath)}</code>`, true),
      renderMetadataRow('SQL-ресурсы', escapeHtml(String(module.sqlFiles.length)), true),
      renderMetadataRow('Storage mode', escapeHtml(session.storageMode || '-'), true),
      renderMetadataRow('Источник редактора', escapeHtml(session.sourceKind || '-'), true),
      renderMetadataRow('Текущая ревизия', session.currentRevisionId ? `<code>${escapeHtml(session.currentRevisionId)}</code>` : '<span class="text-secondary">нет</span>', true),
      renderMetadataRow('Базовая ревизия', session.baseRevisionId ? `<code>${escapeHtml(session.baseRevisionId)}</code>` : '<span class="text-secondary">не задана</span>', true),
      renderMetadataRow('Рабочая копия', session.workingCopyId ? `<code>${escapeHtml(session.workingCopyId)}</code>` : '<span class="text-secondary">нет</span>', true),
      renderMetadataRow('Теги', tags, true),
    ];

    container.innerHTML = rows.join('');
  }

  function renderMetadataRow(label, value, html = false) {
    return `
      <div class="module-metadata-row">
        <div class="module-metadata-label">${escapeHtml(label)}</div>
        <div class="module-metadata-value">${html ? value : escapeHtml(value)}</div>
      </div>
    `;
  }

  function normalizeSqlResourceKey(rawName, fallbackValue = '') {
    const value = String(rawName || '').trim();
    if (!value) {
      return null;
    }
    if (value.startsWith('classpath:')) {
      return value;
    }
    if (value.endsWith('.sql')) {
      return value.startsWith('sql/') ? `classpath:${value}` : `classpath:sql/${value.replace(/^\/+/, '')}`;
    }

    const normalized = value
      .normalize('NFKD')
      .replace(/[^\w\s-]/g, '')
      .trim()
      .replace(/\s+/g, '-')
      .replace(/_+/g, '-')
      .toLowerCase();

    const fallback = String(fallbackValue || '')
      .replace(/^classpath:sql\//, '')
      .replace(/\.sql$/i, '')
      .trim();
    const baseName = normalized || fallback;
    return baseName ? `classpath:sql/${baseName}.sql` : null;
  }

  function defaultSqlLabel(path) {
    const normalized = String(path || '').replace(/\\/g, '/');
    return normalized.substring(normalized.lastIndexOf('/') + 1) || normalized;
  }

  function sqlResourceDisplayName(file) {
    if (!file) {
      return 'SQL-ресурс';
    }
    return defaultSqlLabel(file.path || file.label || 'SQL-ресурс');
  }

  namespace.createSqlCatalogController = createSqlCatalogController;
  namespace.buildSqlResourceUsageMap = buildSqlResourceUsageMap;
  namespace.renderModuleMetadata = renderModuleMetadata;
})(window);
