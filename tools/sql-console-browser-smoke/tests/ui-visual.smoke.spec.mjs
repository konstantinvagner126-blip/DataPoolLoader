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
  await expect(page.locator(".home-visual-shell")).toBeVisible();
  await expect(page.locator(".home-visual-shell")).toHaveScreenshot("home-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("about page visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/about", "О проекте");
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
  await prepareVisualPage(page, `/sql-console-objects?${query.toString()}`, "Объекты БД");
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
    await route.abort("failed");
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
  await expect(page.locator(".module-editor-toolbar")).toBeVisible();
  await expect(page.locator(".module-catalog-status")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toBeVisible();
  await expect(page.locator(".module-editor-content-shell")).toHaveScreenshot("module-editor-shell-layout.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module runs empty-state visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 4000 });
  await prepareVisualPage(page, "/module-runs?storage=files&module=local-manual-test", "История и результаты");
  await expect(page.getByText("Для этого модуля запусков пока нет.")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toHaveScreenshot("module-runs-empty-shell.png", {
    animations: "disabled",
    caret: "hide"
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
  await prepareVisualPage(page, "/compose-sync", "Импорт модулей из файлов");
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.getByText("Для импорта нужен активный режим «База данных».")).toBeVisible();
  await expect(page.getByRole("button", { name: "Синхронизировать все модули" })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Выбрать модули" })).toBeDisabled();
  await expect(page.locator(".module-sync-content-shell")).toBeVisible();
  await expect(page.locator(".module-sync-content-shell > .panel")).toHaveScreenshot("module-sync-runtime-fallback-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("run history cleanup shell visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/run-history-cleanup", "Обслуживание запусков");
  await expect(page.getByText("Retention output-каталогов", { exact: true })).toBeVisible();
  await expect(page.locator(".run-history-cleanup-content-shell")).toBeVisible();
  await expect(page.locator(".run-history-cleanup-content-shell")).toHaveScreenshot("run-history-cleanup-shell.png", {
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
  await page.addStyleTag({ content: "html, body { overflow-y: hidden !important; }" });
  await expect(page.getByText("операции для БД будут недоступны", { exact: false })).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toBeVisible();
  await expect(page.locator(".run-history-cleanup-panels-shell")).toHaveScreenshot("run-history-cleanup-runtime-fallback-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});

test("sql console history empty-state visual baseline", async ({ page }) => {
  const workspaceId = buildWorkspaceId("sql-history-empty");
  await prepareVisualPage(
    page,
    `/sql-console-history?workspaceId=${encodeURIComponent(workspaceId)}`,
    "История запусков SQL-консоли"
  );
  await expect(page.getByText("История пока пуста")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toHaveScreenshot("sql-console-history-empty-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 1024
  });
});
