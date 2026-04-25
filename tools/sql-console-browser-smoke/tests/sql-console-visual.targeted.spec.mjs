import { expect, test } from "@playwright/test";

test.use({
  viewport: { width: 1440, height: 1800 }
});

const STABLE_NOW = "2026-04-25T08:10:00Z";

function buildWorkspaceId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

async function prepareVisualPage(page, path, heading) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(path);
  await waitForVisualStyles(page);
  await expect(page.getByText(heading, { exact: true }).first()).toBeVisible();
  await expect(page.locator(".compose-home-root")).toBeVisible();
}

async function waitForVisualStyles(page) {
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForFunction(() => {
    const button = document.querySelector(".btn, .btn-dark, .btn-outline-secondary");
    if (!button) {
      return true;
    }
    const style = window.getComputedStyle(button);
    return parseFloat(style.borderTopLeftRadius || "0") >= 4 && style.lineHeight !== "normal";
  }, null, { timeout: 30000 });
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

async function mockRuntimeContext(page) {
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
}

async function mockSqlConsoleInfo(page) {
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
}

async function mockSqlConsoleState(page, overrides = {}) {
  await page.route("**/api/sql-console/state?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftSql: 'select * from "datapool_manual"."source_1" order by id limit 25;',
        recentQueries: [],
        favoriteQueries: [],
        favoriteObjects: [
          {
            sourceName: "db1",
            schemaName: "datapool_manual",
            objectName: "source_1",
            objectType: "TABLE",
            tableName: null
          }
        ],
        selectedGroupNames: ["dev"],
        selectedSourceNames: ["db1", "db2"],
        pageSize: 50,
        strictSafetyEnabled: false,
        transactionMode: "AUTO_COMMIT",
        ...overrides
      })
    });
  });
}

async function mockSqlExecutionHistory(page, entries = []) {
  await page.route("**/api/sql-console/history?workspaceId=*", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ entries })
    });
  });
}

async function mockCredentials(page) {
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
}

async function mockSqlObjectInspector(page) {
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
          { name: "id", type: "integer", nullable: false },
          { name: "payload", type: "text", nullable: true },
          { name: "created_at", type: "timestamp without time zone", nullable: true }
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
}

async function mockSqlSourceSettings(page) {
  await page.route("**/api/sql-console/source-settings", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        editableConfigPath: "/Users/kwdev/DataPoolLoader/config/ui-application.yml",
        defaultCredentialsFile: "/Users/kwdev/DataPoolLoader/config/credential.properties",
        secretProvider: {
          providerId: "macos-keychain",
          displayName: "macOS Keychain",
          available: true,
          unavailableReason: null
        },
        sources: [
          {
            originalName: "db1",
            name: "db1",
            credentialsMode: "SYSTEM_KEYCHAIN",
            jdbcUrl: "jdbc:postgresql://localhost:5432/app",
            username: "app_user",
            passwordReference: "",
            passwordConfigured: true,
            secretKey: "sqlConsole.sources.db1.password",
            secretConfigured: true,
            passwordPlainText: ""
          },
          {
            originalName: "db2",
            name: "db2",
            credentialsMode: "PLACEHOLDERS",
            jdbcUrl: "${db2.jdbcUrl}",
            username: "${db2.user}",
            passwordReference: "${db2.password}",
            passwordConfigured: true,
            secretKey: "sqlConsole.sources.db2.password",
            secretConfigured: false,
            passwordPlainText: ""
          }
        ],
        groups: [
          { originalName: "dev", name: "dev", sources: ["db1", "db2"] },
          { originalName: "lt", name: "lt", sources: ["db2"] }
        ]
      })
    });
  });
}

async function mockSqlResultExecution(page, result = buildSqlResult()) {
  await page.route("**/api/sql-console/query/start", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        id: "visual-exec-result",
        status: "RUNNING",
        startedAt: STABLE_NOW,
        cancelRequested: false,
        autoCommitEnabled: true,
        transactionState: "NONE",
        ownerToken: null,
        ownerLeaseExpiresAt: null,
        pendingCommitExpiresAt: null
      })
    });
  });
  await page.route("**/api/sql-console/query/visual-exec-result", async route => {
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        id: "visual-exec-result",
        status: "SUCCESS",
        startedAt: STABLE_NOW,
        finishedAt: "2026-04-25T08:10:03Z",
        cancelRequested: false,
        autoCommitEnabled: true,
        transactionState: "NONE",
        transactionShardNames: [],
        ownerToken: null,
        ownerLeaseExpiresAt: null,
        pendingCommitExpiresAt: null,
        result,
        errorMessage: null
      })
    });
  });
}

function buildSqlResult() {
  const db1Rows = [
    { id: "1", payload: "alpha", status: "READY", updated_at: "2026-04-25 08:00:01" },
    { id: "2", payload: "beta", status: "READY", updated_at: "2026-04-25 08:00:02" },
    { id: "3", payload: "gamma", status: "ARCHIVED", updated_at: "2026-04-25 08:00:03" }
  ];
  const db2Rows = [
    { id: "1", payload: "alpha", status: "READY", updated_at: "2026-04-25 08:00:01" },
    { id: "2", payload: "beta_changed", status: "READY", updated_at: "2026-04-25 08:00:02" },
    { id: "4", payload: "delta", status: "READY", updated_at: "2026-04-25 08:00:04" }
  ];
  return {
    sql: "select id, payload, status, updated_at from datapool_manual.source_1 order by id",
    statementType: "RESULT_SET",
    statementKeyword: "SELECT",
    maxRowsPerShard: 50,
    statementResults: [],
    shardResults: [
      buildShardResult("db1", db1Rows),
      buildShardResult("db2", db2Rows)
    ]
  };
}

function buildShardResult(shardName, rows) {
  return {
    shardName,
    status: "SUCCESS",
    rows,
    rowCount: rows.length,
    columns: ["id", "payload", "status", "updated_at"],
    truncated: false,
    affectedRows: null,
    message: "OK",
    errorMessage: null,
    connectionState: "OPEN",
    startedAt: STABLE_NOW,
    finishedAt: "2026-04-25T08:10:03Z",
    durationMillis: 27
  };
}

async function mockSqlConsoleBase(page, stateOverrides = {}) {
  await mockRuntimeContext(page);
  await mockSqlConsoleInfo(page);
  await mockSqlConsoleState(page, stateOverrides);
  await mockSqlExecutionHistory(page);
  await mockCredentials(page);
}

async function openSqlConsole(page, stateOverrides = {}) {
  const workspaceId = buildWorkspaceId("sql-targeted-visual");
  await mockSqlConsoleBase(page, stateOverrides);
  await prepareVisualPage(page, `/sql-console?workspaceId=${encodeURIComponent(workspaceId)}`, "SQL-консоль по источникам");
  await waitForSqlConsoleEditor(page);
  await page.addStyleTag({
    content: "html, body { overflow-y: hidden !important; } .sql-output-panel { min-height: 420px !important; max-height: 720px !important; overflow: hidden !important; }"
  });
  return workspaceId;
}

async function runVisualQuery(page) {
  await setEditorValue(page, "select id, payload, status, updated_at from datapool_manual.source_1 order by id;");
  await page.getByTitle("Выполнить текущий statement").click();
  await expect(page.locator(".sql-result-grid-table")).toBeVisible({ timeout: 30000 });
}

test("sql console shell targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2400 });
  await openSqlConsole(page);
  await page.waitForTimeout(500);
  await expect(page.locator(".sql-console-shell-row")).toBeVisible();
  await expect(page.getByText("История выполнения этой вкладки")).toHaveCount(0);
  await expect(page.locator(".sql-console-shell-row")).toHaveScreenshot("sql-targeted-shell.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 256
  });
});

test("sql result empty-state targeted visual baseline", async ({ page }) => {
  await openSqlConsole(page, { draftSql: "" });
  await expect(page.getByText("Пока нет данных для отображения.")).toBeVisible();
  await expect(page.locator(".sql-output-panel")).toHaveScreenshot("sql-targeted-result-empty.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 128
  });
});

test("sql result combined grid targeted visual baseline", async ({ page }) => {
  await mockSqlResultExecution(page);
  await openSqlConsole(page);
  await runVisualQuery(page);
  await expect(page.getByRole("button", { name: "Общий grid" })).toBeVisible();
  await expect(page.locator(".sql-output-panel")).toHaveScreenshot("sql-targeted-result-combined.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 256
  });
});

test("sql result source grid targeted visual baseline", async ({ page }) => {
  await mockSqlResultExecution(page);
  await openSqlConsole(page);
  await runVisualQuery(page);
  await page.getByRole("button", { name: "По источникам" }).click();
  await expect(page.getByRole("button", { name: "db1 (3)" })).toBeVisible();
  await expect(page.getByText("Активный source: db1")).toBeVisible();
  await expect(page.locator(".sql-output-panel")).toHaveScreenshot("sql-targeted-result-source.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 256
  });
});

test("sql result diff targeted visual baseline", async ({ page }) => {
  await mockSqlResultExecution(page);
  await openSqlConsole(page);
  await runVisualQuery(page);
  await page.getByRole("button", { name: "Diff" }).click();
  await expect(page.getByText("Сравнение строится относительно baseline source db1")).toBeVisible();
  await expect(page.getByText("Найдено различий:")).toBeVisible();
  await expect(page.locator(".sql-output-panel")).toHaveScreenshot("sql-targeted-result-diff.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 256
  });
});

test("sql object inspector targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  const workspaceId = buildWorkspaceId("sql-targeted-object");
  const query = new URLSearchParams({
    workspaceId,
    source: "db1",
    query: "source_1",
    schema: "datapool_manual",
    object: "source_1",
    type: "TABLE",
    tab: "columns"
  });
  await mockRuntimeContext(page);
  await mockSqlConsoleInfo(page);
  await mockSqlConsoleState(page, { draftSql: "", selectedSourceNames: ["db1"] });
  await mockSqlObjectInspector(page);
  await prepareVisualPage(page, `/sql-console-objects?${query.toString()}`, "Объекты БД");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .sql-object-content-shell { width: 1416px !important; height: 900px !important; overflow: hidden !important; }"
  });
  await expect(page.getByRole("button", { name: "Открыть SELECT в консоли" })).toBeVisible();
  await expect(page.locator(".sql-object-target-name")).toHaveText("datapool_manual.source_1");
  await expect(page.locator(".sql-object-content-shell")).toHaveScreenshot("sql-targeted-object-inspector.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("sql source settings targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 2200 });
  await mockRuntimeContext(page);
  await mockSqlSourceSettings(page);
  await prepareVisualPage(page, "/static/compose-app/index.html?screen=sql-console-sources", "Источники SQL-консоли");
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .sql-source-settings-layout { width: 1416px !important; height: 1040px !important; overflow: hidden !important; }"
  });
  await expect(page.getByText("Файл credentials")).toBeVisible();
  await expect(page.locator(".sql-source-settings-meta").filter({ hasText: "System keychain" })).toBeVisible();
  await expect(page.locator(".sql-source-settings-layout")).toHaveScreenshot("sql-targeted-source-settings.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});

test("sql execution history targeted visual baseline", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1800 });
  const workspaceId = "sql-targeted-history";
  await mockRuntimeContext(page);
  await mockSqlConsoleInfo(page);
  await mockSqlConsoleState(page);
  await mockSqlExecutionHistory(page, [
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
  ]);
  await prepareVisualPage(
    page,
    `/sql-console-history?workspaceId=${encodeURIComponent(workspaceId)}`,
    "История запусков SQL-консоли"
  );
  await page.addStyleTag({
    content: "html { scrollbar-gutter: stable both-edges !important; } body { overflow-y: scroll !important; } .sql-history-content-shell { width: 1416px !important; height: 420px !important; overflow: hidden !important; }"
  });
  await expect(page.getByText("История текущей вкладки")).toBeVisible();
  await expect(page.getByText("PENDING COMMIT")).toBeVisible();
  await expect(page.locator(".sql-history-content-shell")).toHaveScreenshot("sql-targeted-history.png", {
    animations: "disabled",
    caret: "hide",
    maxDiffPixels: 512
  });
});
