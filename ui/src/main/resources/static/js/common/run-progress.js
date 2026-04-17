(function registerRunProgress(global) {
  const namespace = global.DataPoolRunProgress = global.DataPoolRunProgress || {};

  const STAGES = [
    { key: 'prepare', label: 'Подготовка' },
    { key: 'sources', label: 'Источники' },
    { key: 'merge', label: 'Объединение' },
    { key: 'target', label: 'Загрузка' },
    { key: 'finish', label: 'Завершение' },
  ];

  function getStageDefinitions() {
    return STAGES.slice();
  }

  function normalizeStageKey(stageKey) {
    return STAGES.some(stage => stage.key === stageKey) ? stageKey : 'prepare';
  }

  function buildStageStates(currentStageKey, overallStatus) {
    const normalizedStageKey = normalizeStageKey(currentStageKey);
    const normalizedStatus = String(overallStatus || 'PENDING').toUpperCase();
    const currentIndex = STAGES.findIndex(stage => stage.key === normalizedStageKey);

    if (normalizedStatus === 'SUCCESS' || normalizedStatus === 'SUCCESS_WITH_WARNINGS') {
      return STAGES.map(stage => ({ ...stage, status: 'success' }));
    }

    if (normalizedStatus === 'FAILED') {
      return STAGES.map((stage, index) => {
        if (index < currentIndex) {
          return { ...stage, status: 'success' };
        }
        if (index === currentIndex) {
          return { ...stage, status: 'failed' };
        }
        return { ...stage, status: 'pending' };
      });
    }

    if (normalizedStatus === 'RUNNING') {
      return STAGES.map((stage, index) => {
        if (index < currentIndex) {
          return { ...stage, status: 'success' };
        }
        if (index === currentIndex) {
          return { ...stage, status: 'active' };
        }
        return { ...stage, status: 'pending' };
      });
    }

    return STAGES.map((stage, index) => ({
      ...stage,
      status: index === currentIndex ? 'active' : 'pending',
    }));
  }

  function renderRunProgressWidget({
    title,
    subtitle,
    statusLabel,
    statusClass,
    running = false,
    stages = [],
    metrics = [],
  }) {
    return `
      <div class="run-progress-widget">
        <div class="run-progress-widget-head">
          <div>
            <div class="run-progress-title">${escapeHtml(title || '-')}</div>
            <div class="run-progress-subtitle">${escapeHtml(subtitle || '-')}</div>
          </div>
          <div class="run-progress-status-wrap">
            ${running ? `
              <span class="run-progress-indicator run-progress-indicator-running" aria-hidden="true"></span>
            ` : `
              <span class="run-progress-indicator" aria-hidden="true"></span>
            `}
            <span class="${escapeHtml(statusClass || 'status-badge status-pending')}">${escapeHtml(statusLabel || '-')}</span>
          </div>
        </div>

        <div class="run-progress-stage-track">
          ${stages.map(stage => `
            <div class="run-progress-stage run-progress-stage-${escapeHtml(stage.status || 'pending')}">
              <span class="run-progress-stage-marker" aria-hidden="true"></span>
              <span class="run-progress-stage-label">${escapeHtml(stage.label)}</span>
            </div>
          `).join('')}
        </div>

        ${metrics.length > 0 ? `
          <div class="run-progress-metrics">
            ${metrics.map(metric => `
              <div class="run-progress-metric run-progress-metric-${escapeHtml(metric.tone || 'default')}">
                <div class="run-progress-metric-label">${escapeHtml(metric.label)}</div>
                <div class="run-progress-metric-value">${escapeHtml(metric.value)}</div>
              </div>
            `).join('')}
          </div>
        ` : ''}
      </div>
    `;
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  namespace.getStageDefinitions = getStageDefinitions;
  namespace.normalizeStageKey = normalizeStageKey;
  namespace.buildStageStates = buildStageStates;
  namespace.renderRunProgressWidget = renderRunProgressWidget;
})(window);
