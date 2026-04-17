(function initDbCreateModulePage(global) {
  const common = global.DataPoolCommon || {};
  const { withMonacoReady, createMonacoEditor, postJson } = common;

  const refs = {
    form: document.getElementById('createDbModuleForm'),
    moduleCodeInput: document.getElementById('moduleCodeInput'),
    moduleTitleInput: document.getElementById('moduleTitleInput'),
    moduleDescriptionInput: document.getElementById('moduleDescriptionInput'),
    moduleTagsInput: document.getElementById('moduleTagsInput'),
    moduleHiddenInput: document.getElementById('moduleHiddenInput'),
    restoreTemplateButton: document.getElementById('restoreCreateTemplateButton'),
    submitButton: document.getElementById('submitCreateModuleButton'),
    errorAlert: document.getElementById('createModuleErrorAlert'),
  };

  let configEditor = null;

  function defaultConfigTemplate() {
    return [
      'app:',
      '  outputDir: ./output',
      '  mergeMode: plain',
      '  parallelism: 5',
      '  fetchSize: 1000',
      '  queryTimeoutSec: 60',
      '  progressLogEveryRows: 10000',
      '  deleteOutputFilesAfterCompletion: false',
      '',
      '  sources:',
      '    - name: source1',
      '      jdbcUrl: ${SOURCE1_JDBC_URL}',
      '      username: ${SOURCE1_USERNAME}',
      '      password: ${SOURCE1_PASSWORD}',
      '      sql: |',
      '        select 1 as id',
      '',
      '  target:',
      '    enabled: false',
      '    jdbcUrl: ${TARGET_JDBC_URL}',
      '    username: ${TARGET_USERNAME}',
      '    password: ${TARGET_PASSWORD}',
      '    table: public.target_table',
      '    truncateBeforeLoad: false',
      '',
    ].join('\n');
  }

  function parseTags(rawValue) {
    const uniqueTags = new Set();
    String(rawValue || '')
      .split(',')
      .map(value => value.trim())
      .filter(Boolean)
      .forEach(tag => uniqueTags.add(tag));
    return [...uniqueTags];
  }

  function showError(message) {
    refs.errorAlert.textContent = message;
    refs.errorAlert.classList.remove('d-none');
  }

  function hideError() {
    refs.errorAlert.textContent = '';
    refs.errorAlert.classList.add('d-none');
  }

  function restoreTemplate() {
    configEditor?.setValue(defaultConfigTemplate());
  }

  async function submitForm(event) {
    event.preventDefault();
    hideError();

    const moduleCode = refs.moduleCodeInput.value.trim();
    const title = refs.moduleTitleInput.value.trim();
    const description = refs.moduleDescriptionInput.value.trim();
    const configText = configEditor?.getValue()?.trim() || '';

    if (!moduleCode) {
      showError('Укажи код модуля.');
      refs.moduleCodeInput.focus();
      return;
    }
    if (!title) {
      showError('Укажи название модуля.');
      refs.moduleTitleInput.focus();
      return;
    }
    if (!configText) {
      showError('Стартовый application.yml не должен быть пустым.');
      return;
    }

    refs.submitButton.disabled = true;
    try {
      const hiddenFromUi = refs.moduleHiddenInput.checked;
      const result = await postJson(
        '/api/db/modules',
        {
          moduleCode,
          title,
          description: description || null,
          tags: parseTags(refs.moduleTagsInput.value),
          configText,
          hiddenFromUi,
        },
        'Не удалось создать модуль.',
      );

      const nextUrl = new URL('/db-modules', global.location.origin);
      nextUrl.searchParams.set('module', result.moduleCode || moduleCode);
      if (hiddenFromUi) {
        nextUrl.searchParams.set('includeHidden', '1');
      }
      global.location.assign(nextUrl.toString());
    } catch (error) {
      showError(error?.message || 'Не удалось создать модуль.');
    } finally {
      refs.submitButton.disabled = false;
    }
  }

  refs.form?.addEventListener('submit', submitForm);
  refs.restoreTemplateButton?.addEventListener('click', restoreTemplate);

  withMonacoReady(() => {
    configEditor = createMonacoEditor('createModuleConfigEditor', {
      value: defaultConfigTemplate(),
      language: 'yaml',
    });
  });
})(window);
