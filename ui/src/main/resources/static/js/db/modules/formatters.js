(function registerDbModulesFormatters(global) {
  const root = global.DataPoolDb = global.DataPoolDb || {};
  const modules = root.modules = root.modules || {};

  function eventEntryClass(severity) {
    switch (String(severity || '').toUpperCase()) {
      case 'SUCCESS':
        return 'human-log-entry human-log-entry-success';
      case 'ERROR':
        return 'human-log-entry human-log-entry-error';
      case 'WARNING':
        return 'human-log-entry human-log-entry-warning';
      default:
        return 'human-log-entry';
    }
  }

  function statusBadgeClass(status) {
    const normalized = String(status || 'PENDING').trim().toLowerCase();
    if (normalized === 'success_with_warnings') return 'status-badge status-success_with_warnings';
    if (normalized === 'not_enabled') return 'status-badge status-not_enabled';
    return `status-badge status-${normalized}`;
  }

  function artifactStatusClass(status) {
    switch (String(status || '').toUpperCase()) {
      case 'PRESENT':
        return 'status-badge status-success';
      case 'DELETED':
        return 'status-badge status-skipped';
      default:
        return 'status-badge status-failed';
    }
  }

  function translateStatus(status) {
    switch (String(status || '').toUpperCase()) {
      case 'RUNNING': return 'Выполняется';
      case 'SUCCESS': return 'Успешно';
      case 'SUCCESS_WITH_WARNINGS': return 'Успешно с предупреждениями';
      case 'FAILED': return 'Ошибка';
      case 'SKIPPED': return 'Пропущено';
      case 'PENDING': return 'Ожидание';
      case 'NOT_ENABLED': return 'Отключено';
      default: return status || '-';
    }
  }

  function translateLaunchSource(sourceKind) {
    switch (String(sourceKind || '').toUpperCase()) {
      case 'WORKING_COPY': return 'Личный черновик';
      case 'CURRENT_REVISION': return 'Текущая ревизия';
      default: return sourceKind || '-';
    }
  }

  function translateStage(stage) {
    switch (String(stage || '').toUpperCase()) {
      case 'PREPARE': return 'Подготовка';
      case 'SOURCE': return 'Источники';
      case 'MERGE': return 'Объединение';
      case 'TARGET': return 'Загрузка в целевую таблицу';
      case 'RUN': return 'Завершение';
      default: return stage || '-';
    }
  }

  function translateArtifactStatus(status) {
    switch (String(status || '').toUpperCase()) {
      case 'PRESENT': return 'Доступен';
      case 'DELETED': return 'Удален';
      case 'MISSING': return 'Не найден';
      default: return status || '-';
    }
  }

  function translateArtifactKind(kind) {
    switch (String(kind || '').toUpperCase()) {
      case 'SOURCE_OUTPUT': return 'CSV источника';
      case 'MERGED_OUTPUT': return 'Итоговый merged.csv';
      case 'SUMMARY_JSON': return 'Файл summary.json';
      default: return kind || '-';
    }
  }

  function statusTone(status) {
    const normalized = String(status || 'PENDING').toUpperCase();
    if (normalized === 'SUCCESS') return 'success';
    if (normalized === 'FAILED') return 'failed';
    if (normalized === 'RUNNING') return 'important';
    return 'default';
  }

  function formatDateTime(value) {
    if (!value) return '-';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleString('ru-RU');
  }

  function formatNumber(value) {
    if (value == null || value === '') return '-';
    const numeric = Number(value);
    if (Number.isNaN(numeric)) return String(value);
    return numeric.toLocaleString('ru-RU');
  }

  function formatPercent(value) {
    if (value == null || value === '') return '-';
    const numeric = Number(value);
    if (Number.isNaN(numeric)) return String(value);
    return `${numeric.toLocaleString('ru-RU', { minimumFractionDigits: 0, maximumFractionDigits: 2 })}%`;
  }

  function formatFileSize(value) {
    if (value == null || value === '') return '-';
    const size = Number(value);
    if (Number.isNaN(size)) return String(value);
    if (size < 1024) return `${size} B`;
    if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
    return `${(size / (1024 * 1024)).toFixed(1)} MB`;
  }

  function formatRawJson(value) {
    if (!value || value === '{}') return 'Итоги запуска еще не сформированы.';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch (_) {
      return String(value);
    }
  }

  modules.formatters = {
    eventEntryClass,
    statusBadgeClass,
    artifactStatusClass,
    translateStatus,
    translateLaunchSource,
    translateStage,
    translateArtifactStatus,
    translateArtifactKind,
    statusTone,
    formatDateTime,
    formatNumber,
    formatPercent,
    formatFileSize,
    formatRawJson,
  };
})(window);
