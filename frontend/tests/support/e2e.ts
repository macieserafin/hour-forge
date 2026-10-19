import { test as base, expect, type Page, type APIRequestContext } from '@playwright/test'
import { execFileSync } from 'node:child_process'

export { expect }
export const owner = 'user1@hourforge.local'
export const member = 'user2@hourforge.local'
export const password = 'Test-user-1-2026!'
export const memberPassword = 'Test-user-2-2026!'

export const test = base.extend<{ isolatedDatabase: void }>({
  isolatedDatabase: [async ({}, use) => {
    const container = process.env.HOUR_FORGE_E2E_CONTAINER
    if (!container?.startsWith('hour-forge-e2e-')) throw new Error('Managed E2E database was not started. Run tests through playwright.config.ts.')
    const reset = () => execFileSync('docker', ['exec', container, 'psql', '-U', 'hourforge_e2e', '-d', 'hourforge_e2e',
      '-v', 'ON_ERROR_STOP=1', '-c', "TRUNCATE TABLE teams CASCADE; DELETE FROM user_credentials WHERE user_id IN (SELECT id FROM users WHERE email NOT IN ('user1@hourforge.local', 'user2@hourforge.local')); DELETE FROM users WHERE email NOT IN ('user1@hourforge.local', 'user2@hourforge.local');"], { stdio: 'pipe' })
    reset()
    try { await use() } finally { reset() }
  }, { auto: true }],
})

export async function submitLogin(page: Page, email = owner, secret = email === member ? memberPassword : password) {
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email)
  await page.getByLabel('Hasło', { exact: true }).fill(secret)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
}

export async function fillRegistration(page: Page, email: string, secret: string, displayName = 'Registration User') {
  await page.getByLabel('Nazwa wyświetlana').fill(displayName)
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email)
  await page.getByLabel('Hasło', { exact: true }).fill(secret)
  await page.getByLabel('Powtórz hasło').fill(secret)
}

export async function login(page: Page, email = owner, secret = email === member ? memberPassword : password) {
  await page.goto('/login')
  await submitLogin(page, email, secret)
  await expect(page).toHaveURL(/\/dashboard$/)
}

export async function signOut(page: Page) {
  const menu = page.getByRole('button', { name: 'Otwórz menu' })
  if (await menu.isVisible()) await menu.click()
  await page.getByRole('button', { name: 'Wyloguj się', exact: true }).filter({ visible: true }).click()
  await expect(page).toHaveURL(/\/login(?:\?|$)/)
}

export async function post(request: APIRequestContext, path: string, data: unknown) {
  const tokenResponse = await request.get('/api/auth/csrf')
  expect(tokenResponse.ok()).toBe(true)
  const token = await tokenResponse.json()
  return request.post(path, { data, headers: { [token.headerName]: token.token } })
}

export async function create(request: APIRequestContext, path: string, data: unknown) {
  const response = await post(request, path, data)
  expect(response.ok(), `${path}: ${response.status()} ${await response.text()}`).toBe(true)
  return response.status() === 204 ? null : response.json()
}
export const write = create

export async function setupTeam(page: Page, suffix: string) {
  await login(page)
  return createTeamProject(page, suffix)
}

export async function createTeamProject(page: Page, suffix: string) {
  const team = await create(page.request, '/api/teams', { name: `Team ${suffix}`, timezone: 'Europe/Warsaw' })
  const project = await create(page.request, `/api/teams/${team.id}/projects`, { name: `Project ${suffix}`, description: null })
  return { team, project }
}
