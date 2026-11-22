import { expect, test } from '@playwright/test';

test('the demo opens on a read-only example', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/simulations\//);
  await expect(page.getByText('Misión de ejemplo', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /Ejecutar/ })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /^Reconocer/ })).toHaveCount(0);

  await page.getByRole('button', { name: 'GENERATION_DEFICIT', exact: true }).click();
  await expect(page.getByText('Revisando tick 120')).toBeVisible();

  const example = page.url();
  await page.getByRole('button', { name: 'Probar mi propia simulación' }).click();
  await expect(page).not.toHaveURL(example);
  await expect(page.getByRole('button', { name: /Ejecutar/ })).toBeEnabled();
});

test('an acknowledged alarm stays acknowledged after a reload', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Nueva simulación' }).click();
  await expect(page.getByText('tick 0')).toBeVisible();
  const own = page.url();

  // solar-degradation opens GENERATION_DEFICIT at tick 120
  await page.getByRole('button', { name: '+100' }).click();
  await expect(page.getByText('tick 100')).toBeVisible();
  await page.getByRole('button', { name: '+100' }).click();
  const acknowledge = page.getByRole('button', { name: 'Reconocer GENERATION_DEFICIT' });
  await acknowledge.click();

  const row = page.locator('.incident-row', { hasText: 'GENERATION_DEFICIT' });
  await expect(row.getByText(/reconocida t=\d+/)).toBeVisible();
  await expect(acknowledge).toHaveCount(0);

  await page.reload();
  await expect(page).toHaveURL(own);
  await expect(row.getByText(/reconocida t=\d+/)).toBeVisible();
  await expect(acknowledge).toHaveCount(0);
});
