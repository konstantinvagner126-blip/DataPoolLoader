import { expect, test } from "@playwright/test";

function buildWorkspaceId(prefix) {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function trackPageErrors(page) {
  const pageErrors = [];
  page.on("pageerror", error => {
    pageErrors.push(error?.stack || error?.message || `${error}`);
  });
  return pageErrors;
}

async function waitForSqlConsoleEditor(page) {
  await page.waitForFunction(() =>
    Boolean(window.ComposeMonaco?.getEditorValue) && window.ComposeMonaco.getEditorValue() !== null
  );
}

async function gotoSqlConsole(page, workspaceId) {
  await page.goto(`/sql-console?workspaceId=${encodeURIComponent(workspaceId)}`);
  await expect(page.getByRole("heading", { name: "SQL-консоль по источникам" })).toBeVisible();
  await waitForSqlConsoleEditor(page);
}

async function gotoSqlConsoleObjects(page, workspaceId, params) {
  const query = new URLSearchParams({ workspaceId, ...params });
  await page.goto(`/sql-console-objects?${query.toString()}`);
  await expect(page.getByRole("heading", { name: "Объекты БД" })).toBeVisible();
}

async function gotoSqlConsoleHistory(page, workspaceId) {
  await page.goto(`/sql-console-history?workspaceId=${encodeURIComponent(workspaceId)}`);
  await expect(page.getByRole("heading", { name: "История запусков SQL-консоли" })).toBeVisible();
}

async function gotoKafkaMessages(page, params = {}) {
  const query = new URLSearchParams({
    clusterId: "local-docker",
    topic: "datapool-test",
    pane: "messages",
    ...params
  });
  await page.goto(`/kafka?${query.toString()}`);
  await expect(page.getByRole("heading", { name: "Kafka" })).toBeVisible();
  await expect(page.getByText("Bounded чтение через assign + seek без commit offsets и без background consumer session.")).toBeVisible();
}

async function setEditorValue(page, value) {
  const success = await page.evaluate(nextValue => window.ComposeMonaco.setEditorValue(nextValue), value);
  expect(success).toBe(true);
}

async function getEditorValue(page) {
  return page.evaluate(() => window.ComposeMonaco.getEditorValue());
}

function settingCheckbox(page, label) {
  return page.locator("label", { hasText: label }).locator("input[type='checkbox']");
}

test("new browser tab clones workspace draft without false blocked banner", async ({ context, page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-tab");
  await gotoSqlConsole(page, workspaceId);
  await setEditorValue(page, "select 1 as workspace_clone;");

  const popupPromise = context.waitForEvent("page");
  await page.getByTitle("Открыть новую вкладку консоли").click();
  const popup = await popupPromise;
  const popupErrors = trackPageErrors(popup);

  await popup.waitForLoadState("domcontentloaded");
  await waitForSqlConsoleEditor(popup);
  await expect(page.getByText("Браузер заблокировал открытие новой вкладки SQL-консоли.")).toHaveCount(0);
  await expect
    .poll(async () => getEditorValue(popup))
    .toBe("select 1 as workspace_clone;");

  expect(pageErrors).toEqual([]);
  expect(popupErrors).toEqual([]);
});

test("manual transaction reaches pending commit and can rollback safely", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-txn");
  await gotoSqlConsole(page, workspaceId);

  await settingCheckbox(page, "Read-only").uncheck();
  await settingCheckbox(page, "Autocommit").uncheck();
  await setEditorValue(page, `create temporary table ${buildWorkspaceId("codex_smoke_tx").replaceAll("-", "_")}(id int);`);

  try {
    await page.getByTitle("Выполнить текущий statement").click();
    await expect(page.getByText("Транзакция ждет решения: Commit или Rollback.")).toBeVisible();

    await page.getByTitle("Rollback").click();
    await expect(page.getByText("Rollback выполнен.")).toBeVisible();
  } finally {
    const rollbackButton = page.getByTitle("Rollback");
    if (await rollbackButton.isEnabled().catch(() => false)) {
      await rollbackButton.click().catch(() => {});
    }
  }

  expect(pageErrors).toEqual([]);
});

test("object inspector can open SELECT back in SQL console workspace", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-object");
  await gotoSqlConsoleObjects(page, workspaceId, {
    source: "db1",
    query: "source_1",
    schema: "datapool_manual",
    object: "source_1",
    type: "TABLE"
  });

  await expect(page.getByRole("button", { name: "Открыть SELECT в консоли" })).toBeVisible();
  await page.getByRole("button", { name: "Открыть SELECT в консоли" }).click();
  await page.waitForURL(/screen=sql-console/);
  await waitForSqlConsoleEditor(page);
  await expect
    .poll(async () => getEditorValue(page))
    .toContain('from "datapool_manual"."source_1"');

  expect(pageErrors).toEqual([]);
});

test("object inspector direct-load works even when search result is empty", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-object-direct");
  await gotoSqlConsoleObjects(page, workspaceId, {
    source: "db1",
    query: "zz_codex_no_search_match",
    schema: "datapool_manual",
    object: "source_1",
    type: "TABLE"
  });

  await expect(page.getByRole("button", { name: "Открыть SELECT в консоли" })).toBeVisible();
  await expect(page.getByText("По запросу ничего не найдено.")).toBeVisible();
  await expect(
    page.getByText("Объект не найден в текущем search result. Инспектор работает по прямому deep-link metadata path.")
  ).toBeVisible();

  expect(pageErrors).toEqual([]);
});

test("execution history lives on separate workspace-scoped screen", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-history");
  await gotoSqlConsole(page, workspaceId);
  await setEditorValue(page, "select 1 as history_screen_value;");

  await page.getByTitle("Выполнить текущий statement").click();
  await expect(page.getByText("История выполнения этой вкладки")).toHaveCount(0);
  await page.getByRole("button", { name: "История запусков" }).click();

  await page.waitForURL(/screen=sql-console-history/);
  await expect(page.getByText("history_screen_value")).toBeVisible();
  await page.getByRole("button", { name: "Подставить" }).click();

  await page.waitForURL(/screen=sql-console/);
  await waitForSqlConsoleEditor(page);
  await expect
    .poll(async () => getEditorValue(page))
    .toContain("history_screen_value");

  expect(pageErrors).toEqual([]);
});

test("result navigator switches between Grid and Diff views", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  const workspaceId = buildWorkspaceId("sql-smoke-diff");
  await gotoSqlConsole(page, workspaceId);
  await setEditorValue(
    page,
    "select current_database() as db_name, 1 as smoke_value order by db_name;"
  );

  await page.getByTitle("Выполнить текущий statement").click();
  await expect(page.getByRole("button", { name: "Grid" })).toBeVisible();

  await page.getByRole("button", { name: "Diff" }).click();
  await expect(page.getByText("Сравнение строится относительно baseline source")).toBeVisible();
  await expect(page.getByText("Различий по source не найдено.")).toBeVisible();

  await page.getByRole("button", { name: "Grid" }).click();
  await expect(page.getByRole("button", { name: "Diff" })).toBeVisible();

  expect(pageErrors).toEqual([]);
});

test("kafka message browser reads all partitions without browser runtime errors", async ({ page }) => {
  const pageErrors = trackPageErrors(page);
  await gotoKafkaMessages(page);

  await page.locator("select").nth(0).selectOption("ALL_PARTITIONS");
  await page.getByRole("button", { name: "Читать сообщения" }).click();

  await expect(page.getByText("Результат собран по всем partition и отсортирован по timestamp.")).toBeVisible();
  await expect(page.locator(".kafka-message-detail-shell")).toBeVisible();
  await expect(page.locator(".kafka-message-row")).toHaveCount(5);

  expect(pageErrors).toEqual([]);
});
