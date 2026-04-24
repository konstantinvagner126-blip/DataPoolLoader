import { expect, test } from "@playwright/test";

test.use({
  viewport: { width: 1440, height: 1600 }
});

function buildWorkspaceId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function prepareVisualPage(page, path, heading) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(path);
  await expect(page.getByRole("heading", { name: heading })).toBeVisible();
  await expect(page.locator(".compose-home-root")).toBeVisible();
}

async function waitForSqlConsoleEditor(page) {
  await page.waitForFunction(() =>
    Boolean(window.ComposeMonaco?.getEditorValue) && window.ComposeMonaco.getEditorValue() !== null
  );
}

async function setEditorValue(page, value) {
  const success = await page.evaluate(nextValue => window.ComposeMonaco.setEditorValue(nextValue), value);
  expect(success).toBe(true);
}

test("home page visual baseline", async ({ page }) => {
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "database",
        effectiveMode: "database",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await prepareVisualPage(page, "/", "Load Testing Data Platform");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .sql-console-shell-row { width: 1432px !important; height: 1433px !important; overflow: hidden !important; }"
  });
  await expect(page.locator(".home-visual-shell")).toBeVisible();
  await expect(page.locator(".home-visual-shell")).toHaveScreenshot("home-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("about page visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/about", "О проекте");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .sql-object-content-shell { max-height: 1400px; overflow: hidden !important; }"
  });
  await expect(page.getByText("Назначение")).toHaveCount(0);
  await expect(page.getByText("Фокус")).toHaveCount(0);
  await expect(page.getByText("Вагнер Константин")).toBeVisible();
  await expect(page.getByText("Родионов Сергей")).toBeVisible();
  await expect(page.locator(".about-developer-grid")).toBeVisible();
  await expect(page.locator(".about-developer-grid")).toHaveScreenshot("about-developers.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("sql console shell visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2600 });
  const workspaceId = buildWorkspaceId("sql-visual-shell");
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/sql-console/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        configured: true,
        sourceCatalog: [
          { name: "db1" },
          { name: "db2" },
          { name: "db3" },
          { name: "db4" },
          { name: "db5" }
        ],
        groups: [
          { name: "dev", sources: ["db1", "db2"], synthetic: false },
          { name: "ift", sources: ["db2", "db3", "db4"], synthetic: false },
          { name: "lt", sources: ["db4", "db5"], synthetic: false }
        ],
        maxRowsPerShard: 200,
        queryTimeoutSec: 30
      })
    });
  });
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: 'select * from "datapool_manual"."source_1" limit 25;',
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1", "db2"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT"
      })
    });
  });
  await page.route("**/api/sql-console/history?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        entries: []
      })
    });
  });
  await page.route("**/api/credentials", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        mode: "file",
        displayName: "credential.properties",
        fileAvailable: true,
        uploaded: false
      })
    });
  });
  await prepareVisualPage(page, `/sql-console?workspaceId=${encodeURIComponent(workspaceId)}`, "SQL-консоль по источникам");
  await waitForSqlConsoleEditor(page);
  await page.waitForTimeout(750);
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await page.getByRole("heading", { name: "SQL-консоль по источникам" }).click();
  await expect(page.getByText("История выполнения этой вкладки")).toHaveCount(0);
  await expect(page.locator(".sql-working-status-bar")).toBeVisible();
  await expect(page.locator(".sql-console-shell-row")).toHaveScreenshot("sql-console-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 128
  });
});

test("sql object inspector visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2400 });
  const workspaceId = buildWorkspaceId("sql-visual-object");
  const query = new URLSearchParams({
    workspaceId,
    source: "db1",
    query: "source_1",
    schema: "datapool_manual",
    object: "source_1",
    type: "TABLE",
    tab: "columns"
  });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/sql-console/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        configured: true,
        sourceCatalog: [
          { name: "db1" },
          { name: "db2" },
          { name: "db3" },
          { name: "db4" },
          { name: "db5" }
        ],
        groups: [
          { name: "dev", sources: ["db1", "db2"], synthetic: false },
          { name: "ift", sources: ["db2", "db3", "db4"], synthetic: false },
          { name: "lt", sources: ["db4", "db5"], synthetic: false }
        ],
        maxRowsPerShard: 200,
        queryTimeoutSec: 30
      })
    });
  });
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: "",
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT"
      })
    });
  });
  await page.route("**/api/sql-console/objects/search", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        query: "source_1",
        maxObjectsPerSource: 30,
        sourceResults: [
          {
            sourceName: "db1",
            status: "OK",
            objects: [
              {
                schemaName: "datapool_manual",
                objectName: "source_1",
                objectType: "TABLE",
                tableName: null
              }
            ],
            truncated: false,
            errorMessage: null
          }
        ]
      })
    });
  });
  await page.route("**/api/sql-console/objects/inspect", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        sourceName: "db1",
        dbObject: {
          schemaName: "datapool_manual",
          objectName: "source_1",
          objectType: "TABLE",
          tableName: null
        },
        definition: "CREATE TABLE datapool_manual.source_1 (id integer, payload text, created_at timestamp without time zone);",
        columns: [
          {
            name: "id",
            type: "integer",
            nullable: false
          },
          {
            name: "payload",
            type: "text",
            nullable: true
          },
          {
            name: "created_at",
            type: "timestamp without time zone",
            nullable: true
          }
        ],
        indexes: [
          {
            name: "source_1_pkey",
            tableName: "source_1",
            columns: ["id"],
            unique: true,
            primary: true,
            definition: "CREATE UNIQUE INDEX source_1_pkey ON datapool_manual.source_1 USING btree (id)"
          }
        ],
        constraints: [
          {
            name: "source_1_pkey",
            type: "PRIMARY KEY",
            columns: ["id"],
            definition: "PRIMARY KEY (id)"
          }
        ],
        relatedTriggers: [],
        trigger: null,
        sequence: null,
        schema: null
      })
    });
  });
  await prepareVisualPage(page, `/sql-console-objects?${query.toString()}`, "Объекты БД");
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.getByRole("button", { name: "Открыть SELECT в консоли" })).toBeVisible();
  await expect(page.getByText("Текущий объект инспектора")).toBeVisible();
  await expect(page.locator(".sql-object-target-name")).toHaveText("datapool_manual.source_1");
  await expect(page.locator(".sql-object-content-shell")).toBeVisible();
  await expect(page.locator(".sql-object-content-shell")).toHaveScreenshot("sql-object-inspector-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("sql object inspector error-state visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2400 });
  const workspaceId = buildWorkspaceId("sql-visual-object-error");
  const query = new URLSearchParams({
    workspaceId,
    source: "db1",
    query: "source_1",
    schema: "datapool_manual",
    object: "source_1",
    type: "TABLE",
    tab: "columns"
  });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/sql-console/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        configured: true,
        sourceCatalog: [
          { name: "db1" },
          { name: "db2" },
          { name: "db3" },
          { name: "db4" },
          { name: "db5" }
        ],
        groups: [
          { name: "dev", sources: ["db1", "db2"], synthetic: false },
          { name: "ift", sources: ["db2", "db3", "db4"], synthetic: false },
          { name: "lt", sources: ["db4", "db5"], synthetic: false }
        ],
        maxRowsPerShard: 200,
        queryTimeoutSec: 30
      })
    });
  });
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: "",
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT"
      })
    });
  });
  await page.route("**/api/sql-console/objects/search", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        query: "source_1",
        maxObjectsPerSource: 30,
        sourceResults: [
          {
            sourceName: "db1",
            status: "OK",
            objects: [
              {
                schemaName: "datapool_manual",
                objectName: "source_1",
                objectType: "TABLE",
                tableName: null
              }
            ],
            truncated: false,
            errorMessage: null
          }
        ]
      })
    });
  });
  await page.route("**/api/sql-console/objects/inspect", async route => {
    await route.fulfill({
      status: 500,
      contentType: "application/json",
      body: JSON.stringify({
        error: "Synthetic visual regression failure"
      })
    });
  });
  await prepareVisualPage(page, `/sql-console-objects?${query.toString()}`, "Объекты БД");
  await expect(page.getByText("Не удалось загрузить инспектор")).toBeVisible();
  await expect(page.locator(".sql-object-result-list")).toContainText("source_1");
  await expect(page.locator(".sql-object-content-shell")).toBeVisible();
  await expect(page.locator(".sql-object-content-shell")).toHaveScreenshot("sql-object-inspector-error-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module editor shell visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/modules?module=local-manual-test", "Управление пулами данных НТ");
  await page.addStyleTag({
    content: "html, body { overflow-y: hidden !important; } .module-editor-content-shell { max-height: 2400px; overflow: hidden !important; }"
  });
  await expect(page.locator(".module-editor-toolbar")).toBeVisible();
  await expect(page.locator(".module-catalog-status")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toHaveScreenshot("module-editor-shell-layout.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module editor loading-state visual baseline", async ({ page }) => {
  let releaseCatalog;
  const catalogGate = new Promise(resolve => {
    releaseCatalog = resolve;
  });

  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/modules/catalog", async route => {
    await catalogGate;
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        appsRootStatus: {
          mode: "READY",
          configuredPath: "/Users/kwdev/DataPoolLoader/apps",
          message: "Каталог apps найден и готов к работе."
        },
        diagnostics: {
          totalModules: 1,
          validModules: 1,
          warningModules: 0,
          invalidModules: 0,
          totalIssues: 0
        },
        modules: [
          {
            id: "local-manual-test",
            descriptor: {
              title: "local-manual-test",
              description: "Локальный модуль для ручной проверки",
              tags: ["local", "manual"],
              hiddenFromUi: false
            },
            validationStatus: "VALID",
            hasActiveRun: false
          }
        ]
      })
    });
  });
  await page.route("**/api/modules/local-manual-test", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        module: {
          id: "local-manual-test",
          descriptor: {
            title: "local-manual-test",
            description: "Локальный модуль для ручной проверки",
            tags: ["local", "manual"],
            hiddenFromUi: false
          },
          validationStatus: "VALID",
          validationIssues: [],
          configPath: "/Users/kwdev/DataPoolLoader/apps/local-manual-test/src/main/resources/application.yml",
          configText: "app:\\n  outputDir: ./output\\n",
          sqlFiles: [],
          requiresCredentials: false,
          credentialsStatus: {
            mode: "NONE",
            displayName: "Файл не задан",
            fileAvailable: false,
            uploaded: false
          },
          requiredCredentialKeys: [],
          missingCredentialKeys: [],
          credentialsReady: true
        },
        capabilities: {
          save: true,
          saveWorkingCopy: false,
          discardWorkingCopy: false,
          publish: false,
          run: true,
          createModule: false,
          deleteModule: false
        },
        sourceKind: "FILES"
      })
    });
  });
  await prepareVisualPage(page, "/modules?module=local-manual-test", "Управление пулами данных НТ");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .module-editor-content-shell { max-height: 320px; overflow: hidden !important; }"
  });
  await expect(page.getByText("Загружаю compose-shell выбранного модуля.")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toHaveScreenshot("module-editor-loading-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
  releaseCatalog();
});

test("module runs empty-state visual baseline", async ({ page }) => {
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/module-runs/files/local-manual-test", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        moduleId: "local-manual-test",
        moduleTitle: "local-manual-test",
        moduleMeta: "Файловый модуль · запусков пока нет"
      })
    });
  });
  await page.route("**/api/module-runs/files/local-manual-test/runs?limit=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        moduleId: "local-manual-test",
        activeRunId: null,
        uiSettings: {
          showTechnicalDiagnostics: false,
          showRawSummaryJson: false
        },
        runs: []
      })
    });
  });
  await page.setViewportSize({ width: 1440, height: 4000 });
  await prepareVisualPage(page, "/module-runs?storage=files&module=local-manual-test", "История и результаты");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .module-runs-content-shell { width: 1424px !important; height: 276px !important; overflow: hidden !important; }"
  });
  await expect(page.getByText("Для этого модуля запусков пока нет.")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toHaveScreenshot("module-runs-empty-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module runs selected-run visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 3400 });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/module-runs/files/local-manual-test", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        moduleId: "local-manual-test",
        moduleTitle: "local-manual-test",
        moduleMeta: "Файловый модуль · 2 запуска в истории"
      })
    });
  });
  await page.route("**/api/module-runs/files/local-manual-test/runs?limit=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        moduleId: "local-manual-test",
        activeRunId: "run-2026-04-20-1000",
        uiSettings: {
          showTechnicalDiagnostics: false,
          showRawSummaryJson: false
        },
        runs: [
          {
            runId: "run-2026-04-20-1000",
            moduleId: "local-manual-test",
            moduleTitle: "local-manual-test",
            status: "SUCCESS",
            startedAt: "2026-04-20T10:00:01Z",
            finishedAt: "2026-04-20T10:01:00Z",
            requestedAt: "2026-04-20T10:00:00Z",
            outputDir: "/tmp/out/run-1000",
            mergedRowCount: 120,
            errorMessage: null,
            launchSourceKind: "WORKING_COPY",
            executionSnapshotId: "snapshot-1000",
            successfulSourceCount: 2,
            failedSourceCount: 0,
            skippedSourceCount: 0,
            targetStatus: "SUCCESS",
            targetTableName: "demo_target",
            targetRowsLoaded: 120
          },
          {
            runId: "run-2026-04-19-2200",
            moduleId: "local-manual-test",
            moduleTitle: "local-manual-test",
            status: "FAILED",
            startedAt: "2026-04-19T22:00:05Z",
            finishedAt: "2026-04-19T22:00:21Z",
            requestedAt: "2026-04-19T22:00:00Z",
            outputDir: "/tmp/out/run-2200",
            mergedRowCount: 40,
            errorMessage: "Источник db2 завершился с ошибкой.",
            launchSourceKind: "CURRENT_REVISION",
            executionSnapshotId: "snapshot-2200",
            successfulSourceCount: 1,
            failedSourceCount: 1,
            skippedSourceCount: 0,
            targetStatus: "FAILED",
            targetTableName: "demo_target",
            targetRowsLoaded: 0
          }
        ]
      })
    });
  });
  await page.route("**/api/module-runs/files/local-manual-test/runs/run-2026-04-20-1000", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        run: {
          runId: "run-2026-04-20-1000",
          moduleId: "local-manual-test",
          moduleTitle: "local-manual-test",
          status: "SUCCESS",
          startedAt: "2026-04-20T10:00:01Z",
          finishedAt: "2026-04-20T10:01:00Z",
          requestedAt: "2026-04-20T10:00:00Z",
          outputDir: "/tmp/out/run-1000",
          mergedRowCount: 120,
          errorMessage: null,
          launchSourceKind: "WORKING_COPY",
          executionSnapshotId: "snapshot-1000",
          successfulSourceCount: 2,
          failedSourceCount: 0,
          skippedSourceCount: 0,
          targetStatus: "SUCCESS",
          targetTableName: "demo_target",
          targetRowsLoaded: 120
        },
        summaryJson: JSON.stringify({
          mergeMode: "UNION_ALL",
          parallelism: 4,
          fetchSize: 5000,
          queryTimeoutSec: 120,
          progressLogEveryRows: 10000,
          mergedFile: "/tmp/out/run-1000/merged.csv",
          mergedRowCount: 120,
          maxMergedRows: 500000,
          startedAt: "2026-04-20T10:00:01Z",
          finishedAt: "2026-04-20T10:01:00Z",
          targetEnabled: true,
          targetLoad: {
            status: "SUCCESS",
            table: "demo_target",
            rowCount: 120,
            errorMessage: null
          },
          successfulSources: [
            { sourceName: "db1" },
            { sourceName: "db2" }
          ],
          failedSources: [],
          mergeDetails: {
            sourceAllocations: [
              {
                sourceName: "db1",
                availableRows: 70,
                mergedRows: 70,
                mergedPercent: 100
              },
              {
                sourceName: "db2",
                availableRows: 50,
                mergedRows: 50,
                mergedPercent: 100
              }
            ]
          }
        }),
        sourceResults: [
          {
            runSourceResultId: "source-1",
            sourceName: "db1",
            sortOrder: 0,
            status: "SUCCESS",
            startedAt: "2026-04-20T10:00:02Z",
            finishedAt: "2026-04-20T10:00:20Z",
            exportedRowCount: 70,
            mergedRowCount: 70,
            errorMessage: null
          },
          {
            runSourceResultId: "source-2",
            sourceName: "db2",
            sortOrder: 1,
            status: "SUCCESS",
            startedAt: "2026-04-20T10:00:05Z",
            finishedAt: "2026-04-20T10:00:35Z",
            exportedRowCount: 50,
            mergedRowCount: 50,
            errorMessage: null
          }
        ],
        events: [
          {
            runEventId: "event-1",
            seqNo: 1,
            timestamp: "2026-04-20T10:00:01Z",
            stage: "PREPARE",
            eventType: "RUN_STARTED",
            severity: "INFO",
            sourceName: null,
            message: "Запуск стартовал."
          },
          {
            runEventId: "event-2",
            seqNo: 2,
            timestamp: "2026-04-20T10:00:15Z",
            stage: "SOURCE",
            eventType: "SOURCE_FINISHED",
            severity: "SUCCESS",
            sourceName: "db1",
            message: "Источник db1 успешно выгружен."
          },
          {
            runEventId: "event-3",
            seqNo: 3,
            timestamp: "2026-04-20T10:00:55Z",
            stage: "TARGET",
            eventType: "TARGET_FINISHED",
            severity: "SUCCESS",
            sourceName: null,
            message: "Загрузка в target завершена."
          }
        ],
        artifacts: [
          {
            runArtifactId: "artifact-1",
            artifactKind: "MERGED_OUTPUT",
            artifactKey: "merged",
            filePath: "/tmp/out/run-1000/merged.csv",
            storageStatus: "PRESENT",
            fileSizeBytes: 4096,
            contentHash: null,
            createdAt: "2026-04-20T10:01:00Z"
          },
          {
            runArtifactId: "artifact-2",
            artifactKind: "SUMMARY_JSON",
            artifactKey: "summary",
            filePath: "/tmp/out/run-1000/summary.json",
            storageStatus: "PRESENT",
            fileSizeBytes: 512,
            contentHash: null,
            createdAt: "2026-04-20T10:01:00Z"
          }
        ]
      })
    });
  });
  await prepareVisualPage(page, "/module-runs?storage=files&module=local-manual-test", "История и результаты");
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.locator(".run-history-item-active")).toBeVisible();
  await expect(page.locator(".runs-overview-grid")).toContainText("run-2026-04-20-1000");
  await expect(page.locator(".run-summary-title")).toHaveText("local-manual-test");
  await expect(page.getByText("Результаты запуска", { exact: true })).toBeVisible();
  await page.addStyleTag({
    content: ".module-runs-content-shell { max-height: 2800px; overflow: hidden !important; }"
  });
  await expect(page.locator(".module-runs-content-shell")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toHaveScreenshot("module-runs-selected-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});

test("module sync shell visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "database",
        effectiveMode: "database",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/db/sync/state", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        maintenanceMode: false,
        activeFullSync: null,
        activeSingleSyncs: [],
        message: ""
      })
    });
  });
  await page.route("**/api/db/sync/runs?limit=20", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        runs: []
      })
    });
  });
  await prepareVisualPage(page, "/compose-sync", "Импорт модулей из файлов");
  await page.waitForTimeout(750);
  await expect(page.getByText("Импорт модулей", { exact: true })).toBeVisible();
  await expect(page.getByText("Импорты пока не запускались.")).toBeVisible();
  await expect(
    page.locator(".module-sync-content-shell .panel-title").filter({ hasText: /^История импортов$/ }).first()
  ).toBeVisible();
  await expect(page.locator(".module-sync-content-shell")).toBeVisible();
  await expect(page.locator(".module-sync-content-shell")).toHaveScreenshot("module-sync-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module sync runtime-fallback visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 3000 });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "database",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: false,
          schema: "datapool_manual",
          message: "Режим базы данных недоступен.",
          errorMessage: "Connection refused"
        }
      })
    });
  });
  await page.route("**/api/db/sync/state", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        maintenanceMode: false,
        activeFullSync: null,
        activeSingleSyncs: [],
        message: "Импорт недоступен в файловом режиме."
      })
    });
  });
  await page.route("**/api/db/sync/runs?limit=20", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        runs: []
      })
    });
  });
  await prepareVisualPage(page, "/compose-sync", "Импорт модулей из файлов");
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.getByText("Для импорта нужен активный режим «База данных».")).toBeVisible();
  await expect(page.getByRole("button", { name: "Синхронизировать все модули" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Выбрать модули" })).toBeDisabled();
  await expect(page.locator(".module-sync-content-shell")).toBeVisible();
  await expect(page.locator(".module-sync-content-shell")).toHaveScreenshot("module-sync-runtime-fallback-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("run history cleanup shell visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2600 });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "database",
        effectiveMode: "database",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/run-history/cleanup/preview?disableSafeguard=false", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "DATABASE",
        safeguardEnabled: true,
        retentionDays: 30,
        keepMinRunsPerModule: 20,
        cutoffTimestamp: "2026-03-24T10:00:00Z",
        currentRunsCount: 42,
        currentModulesCount: 4,
        currentStorageBytes: 7340032,
        currentOldestRequestedAt: "2026-02-21T09:00:00Z",
        currentNewestRequestedAt: "2026-04-20T18:00:00Z",
        currentTopModules: [
          {
            moduleCode: "local-manual-test",
            currentRunsCount: 18,
            currentStorageBytes: 3145728,
            currentOutputDirs: 0,
            oldestRequestedAt: "2026-02-21T09:00:00Z",
            newestRequestedAt: "2026-04-20T18:00:00Z"
          }
        ],
        estimatedBytesToFree: 1048576,
        totalModulesAffected: 2,
        totalRunsToDelete: 9,
        totalSourceResultsToDelete: 28,
        totalEventsToDelete: 16,
        totalArtifactsToDelete: 0,
        totalOrphanExecutionSnapshotsToDelete: 1,
        modules: [
          {
            moduleCode: "local-manual-test",
            totalRunsToDelete: 6,
            oldestRequestedAt: "2026-02-21T09:00:00Z",
            newestRequestedAt: "2026-03-18T08:00:00Z"
          },
          {
            moduleCode: "demo-batch",
            totalRunsToDelete: 3,
            oldestRequestedAt: "2026-03-01T09:00:00Z",
            newestRequestedAt: "2026-03-10T08:00:00Z"
          }
        ]
      })
    });
  });
  await page.route("**/api/output-retention/preview?disableSafeguard=false", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "DATABASE",
        safeguardEnabled: true,
        retentionDays: 30,
        keepMinRunsPerModule: 20,
        cutoffTimestamp: "2026-03-24T10:00:00Z",
        currentRunsWithOutput: 22,
        currentModulesWithOutput: 3,
        currentOutputDirs: 27,
        currentBytes: 12582912,
        currentOldestRequestedAt: "2026-02-25T07:00:00Z",
        currentNewestRequestedAt: "2026-04-20T18:00:00Z",
        currentTopModules: [
          {
            moduleCode: "local-manual-test",
            currentRunsCount: 9,
            currentStorageBytes: 4194304,
            currentOutputDirs: 12,
            oldestRequestedAt: "2026-02-25T07:00:00Z",
            newestRequestedAt: "2026-04-20T18:00:00Z"
          }
        ],
        totalModulesAffected: 2,
        totalRunsAffected: 7,
        totalOutputDirsToDelete: 10,
        totalMissingOutputDirs: 1,
        totalBytesToFree: 2097152,
        modules: [
          {
            moduleCode: "local-manual-test",
            totalRunsAffected: 4,
            totalOutputDirsToDelete: 6,
            totalBytesToFree: 1048576,
            oldestRequestedAt: "2026-02-25T07:00:00Z",
            newestRequestedAt: "2026-03-15T09:30:00Z"
          },
          {
            moduleCode: "demo-batch",
            totalRunsAffected: 3,
            totalOutputDirsToDelete: 4,
            totalBytesToFree: 1048576,
            oldestRequestedAt: "2026-03-02T07:00:00Z",
            newestRequestedAt: "2026-03-16T09:30:00Z"
          }
        ]
      })
    });
  });
  await prepareVisualPage(page, "/run-history-cleanup", "Обслуживание запусков");
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await page.waitForTimeout(750);
  await expect(page.getByText("Retention output-каталогов", { exact: true })).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toHaveScreenshot("run-history-cleanup-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});

test("run history cleanup runtime-fallback visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "database",
        effectiveMode: "files",
        fallbackReason: "Connection refused",
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: false,
          schema: "datapool_manual",
          message: "Режим базы данных недоступен.",
          errorMessage: "Connection refused"
        }
      })
    });
  });
  await page.route("**/api/run-history/cleanup/preview?disableSafeguard=false", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        safeguardEnabled: true,
        retentionDays: 30,
        keepMinRunsPerModule: 20,
        cutoffTimestamp: "2026-03-24T10:00:00Z",
        currentRunsCount: 18,
        currentModulesCount: 3,
        currentStorageBytes: 1048576,
        currentOldestRequestedAt: "2026-03-01T09:00:00Z",
        currentNewestRequestedAt: "2026-04-20T18:00:00Z",
        currentTopModules: [
          {
            moduleCode: "local-manual-test",
            currentRunsCount: 9,
            currentStorageBytes: 524288,
            currentOutputDirs: 0,
            oldestRequestedAt: "2026-03-01T09:00:00Z",
            newestRequestedAt: "2026-04-20T18:00:00Z"
          }
        ],
        estimatedBytesToFree: 262144,
        totalModulesAffected: 1,
        totalRunsToDelete: 4,
        totalSourceResultsToDelete: 12,
        totalEventsToDelete: 8,
        totalArtifactsToDelete: 0,
        totalOrphanExecutionSnapshotsToDelete: 1,
        modules: [
          {
            moduleCode: "local-manual-test",
            totalRunsToDelete: 4,
            oldestRequestedAt: "2026-03-01T09:00:00Z",
            newestRequestedAt: "2026-03-18T08:00:00Z"
          }
        ]
      })
    });
  });
  await page.route("**/api/output-retention/preview?disableSafeguard=false", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        storageMode: "FILES",
        safeguardEnabled: true,
        retentionDays: 30,
        keepMinRunsPerModule: 20,
        cutoffTimestamp: "2026-03-24T10:00:00Z",
        currentRunsWithOutput: 12,
        currentModulesWithOutput: 2,
        currentOutputDirs: 15,
        currentBytes: 2097152,
        currentOldestRequestedAt: "2026-03-02T07:00:00Z",
        currentNewestRequestedAt: "2026-04-20T18:00:00Z",
        currentTopModules: [
          {
            moduleCode: "local-manual-test",
            currentRunsCount: 7,
            currentStorageBytes: 1048576,
            currentOutputDirs: 9,
            oldestRequestedAt: "2026-03-02T07:00:00Z",
            newestRequestedAt: "2026-04-20T18:00:00Z"
          }
        ],
        totalModulesAffected: 1,
        totalRunsAffected: 3,
        totalOutputDirsToDelete: 5,
        totalMissingOutputDirs: 1,
        totalBytesToFree: 524288,
        modules: [
          {
            moduleCode: "local-manual-test",
            totalRunsAffected: 3,
            totalOutputDirsToDelete: 5,
            totalBytesToFree: 524288,
            oldestRequestedAt: "2026-03-02T07:00:00Z",
            newestRequestedAt: "2026-03-15T09:30:00Z"
          }
        ]
      })
    });
  });
  await prepareVisualPage(page, "/run-history-cleanup", "Обслуживание запусков");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .run-history-cleanup-panels-shell { width: 1416px !important; height: 1347px !important; overflow: hidden !important; }"
  });
  await page.waitForTimeout(750);
  await expect(page.getByText("операции для БД будут недоступны", { exact: false })).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toHaveScreenshot("run-history-cleanup-runtime-fallback-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});

test("sql console history empty-state visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1600 });
  const workspaceId = "sql-history-empty-visual";
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/sql-console/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        configured: true,
        sourceCatalog: [
          { name: "db1" },
          { name: "db2" },
          { name: "db3" },
          { name: "db4" },
          { name: "db5" }
        ],
        groups: [
          { name: "dev", sources: ["db1", "db2"], synthetic: false },
          { name: "ift", sources: ["db2", "db3", "db4"], synthetic: false },
          { name: "lt", sources: ["db4", "db5"], synthetic: false }
        ],
        maxRowsPerShard: 200,
        queryTimeoutSec: 30
      })
    });
  });
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: "",
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1", "db2"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT"
      })
    });
  });
  await page.route("**/api/sql-console/history?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        entries: []
      })
    });
  });
  await prepareVisualPage(
    page,
    `/sql-console-history?workspaceId=${encodeURIComponent(workspaceId)}`,
    "История запусков SQL-консоли"
  );
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; }"
  });
  await expect(page.getByText("История пока пуста")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toHaveScreenshot("sql-console-history-empty-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});

test("sql console history populated-state visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  const workspaceId = "sql-history-populated-visual";
  await page.route("**/api/ui/runtime-context", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        requestedMode: "files",
        effectiveMode: "files",
        fallbackReason: null,
        actor: {
          resolved: true,
          actorId: "local-user",
          actorSource: "local",
          actorDisplayName: "Local User",
          requiresManualInput: false,
          message: "Локальный пользователь определен."
        },
        database: {
          configured: true,
          available: true,
          schema: "datapool_manual",
          message: "Подключение активно.",
          errorMessage: null
        }
      })
    });
  });
  await page.route("**/api/sql-console/info", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        configured: true,
        sourceCatalog: [
          { name: "db1" },
          { name: "db2" },
          { name: "db3" },
          { name: "db4" },
          { name: "db5" }
        ],
        groups: [
          { name: "dev", sources: ["db1", "db2"], synthetic: false },
          { name: "ift", sources: ["db2", "db3", "db4"], synthetic: false },
          { name: "lt", sources: ["db4", "db5"], synthetic: false }
        ],
        maxRowsPerShard: 200,
        queryTimeoutSec: 30
      })
    });
  });
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: "select * from datapool_manual.source_1;",
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1", "db2"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT"
      })
    });
  });
  await page.route("**/api/sql-console/history?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        entries: [
          {
            executionId: "exec-history-1",
            sql: "select id, payload from datapool_manual.source_1 order by id limit 25",
            selectedSourceNames: ["db1", "db2"],
            autoCommitEnabled: false,
            status: "SUCCESS",
            transactionState: "PENDING_COMMIT",
            startedAt: "2026-04-24T08:10:00Z",
            finishedAt: "2026-04-24T08:10:08Z",
            durationMillis: 8123,
            errorMessage: null
          },
          {
            executionId: "exec-history-2",
            sql: "delete from datapool_manual.target_pool where id < 100",
            selectedSourceNames: ["db3"],
            autoCommitEnabled: true,
            status: "FAILED",
            transactionState: "NONE",
            startedAt: "2026-04-24T07:42:10Z",
            finishedAt: "2026-04-24T07:42:11Z",
            durationMillis: 1321,
            errorMessage: "Источник db3 завершился с ошибкой."
          }
        ]
      })
    });
  });
  await prepareVisualPage(
    page,
    `/sql-console-history?workspaceId=${encodeURIComponent(workspaceId)}`,
    "История запусков SQL-консоли"
  );
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.getByText("История текущей вкладки")).toBeVisible();
  await expect(page.getByText("PENDING COMMIT")).toBeVisible();
  await expect(page.getByText("Источник db3 завершился с ошибкой.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Подставить" })).toHaveCount(2);
  await expect(page.getByRole("button", { name: "Повторить" })).toHaveCount(2);
  await expect(page.locator(".sql-history-content-shell")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toHaveScreenshot("sql-console-history-populated-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});
