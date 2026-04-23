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
  await prepareVisualPage(page, `/sql-console?workspaceId=${encodeURIComponent(workspaceId)}`, "SQL-консоль по источникам");
  await waitForSqlConsoleEditor(page);
  await setEditorValue(page, 'select * from "datapool_manual"."source_1" limit 25;');
  await page.getByRole("heading", { name: "SQL-консоль по источникам" }).click();
  await expect(page.getByText("История выполнения этой вкладки")).toHaveCount(0);
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
  await prepareVisualPage(page, "/module-runs?storage=files&module=local-manual-test", "История и результаты");
  await expect(page.getByText("Для этого модуля запусков пока нет.")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toBeVisible();
  await expect(page.locator(".module-runs-content-shell")).toHaveScreenshot("module-runs-empty-shell.png", {
    animations: "disabled",
    caret: "hide"
  });
});

test("module sync shell visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/compose-sync", "Импорт модулей из файлов");
  await expect(page.getByText("Импорт модулей", { exact: true })).toBeVisible();
  await expect(
    page.locator(".module-sync-content-shell .panel-title").filter({ hasText: /^История импортов$/ }).first()
  ).toBeVisible();
  await expect(page.locator(".module-sync-content-shell")).toBeVisible();
  await expect(page.locator(".module-sync-content-shell")).toHaveScreenshot("module-sync-shell.png", {
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
