import { expect, test } from "@playwright/test";

test.use({
  viewport: { width: 1440, height: 1600 }
});

async function prepareVisualPage(page, path, heading) {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto(path);
  await expect(page.getByRole("heading", { name: heading })).toBeVisible();
  await expect(page.locator(".compose-home-root")).toBeVisible();
}

test("home page visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/", "Load Testing Data Platform");
  await expect(page.locator(".compose-home-root")).toHaveScreenshot("home-page.png", {
    animations: "disabled"
  });
});

test("about page visual baseline", async ({ page }) => {
  await prepareVisualPage(page, "/about", "О проекте");
  await expect(page.getByText("Назначение")).toHaveCount(0);
  await expect(page.getByText("Фокус")).toHaveCount(0);
  await expect(page.getByText("Вагнер Константин")).toBeVisible();
  await expect(page.getByText("Родионов Сергей")).toBeVisible();
  await expect(page.locator(".compose-home-root")).toHaveScreenshot("about-page.png", {
    animations: "disabled"
  });
});
