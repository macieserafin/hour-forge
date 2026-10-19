import { test, expect, type Page, type APIRequestContext } from '@playwright/test'

const email = process.env.E2E_EMAIL
const password = process.env.E2E_PASSWORD

async function create(request: APIRequestContext, path: string, data: unknown) {
  const csrf = await (await request.get('/api/auth/csrf')).json()
  const response = await request.post(path, { data, headers: { [csrf.headerName]: csrf.token } })
  expect(response.ok()).toBe(true)
  return response.json()
}

async function setup(page: Page, suffix: string) {
  await page.goto('/login')
  await page.getByLabel('Adres e-mail', { exact: true }).fill(email!)
  await page.getByLabel('Hasło', { exact: true }).fill(password!)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  const team = await create(page.request, '/api/teams', { name: `History ${suffix}`, timezone: 'Europe/Warsaw' })
  const project = await create(page.request, `/api/teams/${team.id}/projects`, { name: `Project ${suffix}`, description: null })
  return { team, project }
}

const historyPanel = (page: Page) => page.getByRole('region', { name: 'Historia czasu', exact: true })
const timeInput = (page: Page) => page.getByRole('region', { name: 'Rejestrowanie czasu w zespole' })

async function addManual(page: Page, description: string) {
  await timeInput(page).getByRole('button', { name: 'Dodaj wpis ręczny' }).click()
  const modal = page.getByRole('dialog', { name: 'Dodaj wpis ręczny' })
  await modal.getByLabel('Data pracy').fill('2026-01-05')
  await modal.getByLabel('Minuty').fill('15')
  await modal.getByLabel('Opis pracy', { exact: false }).fill(description)
  return modal
}

test.beforeEach(() => { test.skip(!email || !password, 'Set credentials for a real test account.') })

test.afterEach(async ({ page }) => {
  const response = await page.request.get('/api/timer/active')
  if (response.status() !== 200) return
  const timer = await response.json()
  await expect.poll(() => Date.now() - Date.parse(timer.startedAt)).toBeGreaterThan(1100)
  await create(page.request, '/api/timer/stop', { entryId: timer.id })
})

test('manual save and timer stop refresh history while preserving applied filters', async ({ page }, info) => {
  const { team, project } = await setup(page, `${info.project.name} ${Date.now()}`)
  await page.goto(`/time?team=${team.id}`)
  const history = historyPanel(page)
  await expect(history.getByText('Nie masz jeszcze zapisanych wpisów.')).toBeVisible()
  await history.getByLabel('Projekt w historii').selectOption(project.id)
  await history.getByLabel('Data od').fill('2000-01-01')
  await history.getByLabel('Data do').fill('2099-12-31')
  await history.getByRole('button', { name: 'Filtruj', exact: true }).click()
  await timeInput(page).getByLabel('Projekt', { exact: true }).selectOption(project.id)
  const modal = await addManual(page, 'Ręczny wpis widoczny bez odświeżania')
  await modal.getByRole('button', { name: 'Zapisz wpis' }).click()
  await expect(modal).not.toBeVisible()
  await expect(history.getByText('Ręczny wpis widoczny bez odświeżania', { exact: true })).toBeVisible()
  await expect(history.getByText('00:15:00', { exact: true })).toBeVisible()
  await timeInput(page).getByRole('button', { name: 'Uruchom timer', exact: true }).click()
  const active = page.getByRole('region', { name: 'Aktywny timer', exact: true })
  await expect(active.getByRole('button', { name: 'Zakończ pracę' })).toBeEnabled()
  await expect(history.getByRole('status')).toContainText('Liczba wpisów: 1')
  await active.getByLabel('Opis wykonanej pracy', { exact: false }).fill('Zakończony timer w historii')
  await active.getByRole('button', { name: 'Zakończ pracę' }).click()
  await expect(active).toHaveCount(0)
  await expect(history.getByText('Zakończony timer w historii', { exact: true })).toBeVisible()
  await expect(history.getByRole('status')).toContainText('Liczba wpisów: 2')
  await expect(history.getByLabel('Projekt w historii')).toHaveValue(project.id)
  await expect(history.getByLabel('Data od')).toHaveValue('2000-01-01')
  await expect(history.getByLabel('Data do')).toHaveValue('2099-12-31')
  await page.reload()
  await expect(history.getByRole('row')).toHaveCount(3)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('history paginates, filters archived projects and dates, and resets on team change', async ({ page }, info) => {
  const { team, project } = await setup(page, `Filters ${info.project.name} ${Date.now()}`)
  for (let i = 0; i < 21; i++) {
    await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id,
      workDate: '2026-01-05', durationSeconds: 60, description: `Entry ${i}` })
  }
  const archived = await create(page.request, `/api/teams/${team.id}/projects`, { name: 'Archived history', description: null })
  await create(page.request, '/api/time-entries', { teamId: team.id, projectId: archived.id,
    workDate: '2026-01-06', durationSeconds: 120, description: 'History from archived project' })
  await create(page.request, `/api/teams/${team.id}/projects/${archived.id}/archive`, {})
  const emptyTeam = await create(page.request, '/api/teams', { name: 'Empty history', timezone: 'UTC' })
  await page.goto(`/time?team=${team.id}`)
  const history = historyPanel(page)
  await expect(history.getByRole('row')).toHaveCount(21)
  await history.getByRole('button', { name: 'Następna', exact: true }).click()
  await expect(history.getByText('Strona 2 z 2')).toBeVisible()
  await expect(history.getByRole('row')).toHaveCount(3)
  await timeInput(page).getByLabel('Projekt', { exact: true }).selectOption(project.id)
  const modal = await addManual(page, 'Saved on second page')
  await modal.getByRole('button', { name: 'Zapisz wpis' }).click()
  await expect(modal).not.toBeVisible()
  await expect(history.getByRole('status')).toContainText('Liczba wpisów: 23')
  await expect(history.getByText('Strona 2 z 2')).toBeVisible()
  await expect(history.getByRole('row')).toHaveCount(4)
  await history.getByLabel('Projekt w historii').selectOption(archived.id)
  await history.getByLabel('Data od').fill('2026-01-06')
  await history.getByLabel('Data do').fill('2026-01-06')
  await history.getByRole('button', { name: 'Filtruj', exact: true }).click()
  await expect(history.getByText('Strona 1 z 1')).toBeVisible()
  await expect(history.getByText('History from archived project', { exact: true })).toBeVisible()
  await expect(history.getByRole('row')).toHaveCount(2)
  await history.getByLabel('Data od').fill('2026-01-07')
  await history.getByRole('button', { name: 'Filtruj', exact: true }).click()
  await expect(history.getByRole('alert')).toContainText('nie może być późniejsza')
  await history.getByLabel('Data do').fill('2026-01-08')
  await history.getByRole('button', { name: 'Filtruj', exact: true }).click()
  await expect(history.getByText('Brak wpisów dla wybranych filtrów.')).toBeVisible()
  await page.getByLabel('Zespół', { exact: true }).selectOption(emptyTeam.id)
  await expect(history.getByText('Nie masz jeszcze zapisanych wpisów.')).toBeVisible()
  await expect(history.getByLabel('Data od')).toHaveValue('')
  await expect(history.getByLabel('Projekt w historii')).toHaveValue('')
  await page.getByLabel('Zespół', { exact: true }).selectOption(team.id)
  await expect(history.getByRole('status')).toContainText('Liczba wpisów: 23')
})

for (const operation of ['manual', 'timer'] as const) {
  test(`history reconciles after a lost ${operation} response without repeating the write`, async ({ page }, info) => {
    const { team, project } = await setup(page, `Lost ${operation} ${info.project.name} ${Date.now()}`)
    await page.goto(`/time?team=${team.id}`)
    await timeInput(page).getByLabel('Projekt', { exact: true }).selectOption(project.id)
    const path = operation === 'manual' ? '/api/time-entries' : '/api/timer/stop'
    let writes = 0
    await page.route(`**${path}`, async route => {
      if (route.request().method() !== 'POST') return route.continue()
      writes++
      const response = await route.fetch()
      expect(response.ok()).toBe(true)
      await route.abort('failed')
    })
    const description = `Saved despite lost ${operation} response`
    if (operation === 'manual') {
      const modal = await addManual(page, description)
      await modal.getByRole('button', { name: 'Zapisz wpis' }).click()
      await expect(modal.getByRole('alert')).toContainText('Nie udało się połączyć')
      await modal.getByRole('button', { name: 'Anuluj' }).click()
    } else {
      await timeInput(page).getByRole('button', { name: 'Uruchom timer', exact: true }).click()
      const active = page.getByRole('region', { name: 'Aktywny timer', exact: true })
      await expect(active.getByRole('button', { name: 'Zakończ pracę' })).toBeEnabled()
      await active.getByLabel('Opis wykonanej pracy', { exact: false }).fill(description)
      await active.getByRole('button', { name: 'Zakończ pracę' }).click()
      await expect(active).toHaveCount(0)
    }
    const history = historyPanel(page)
    await expect(history.getByText(description, { exact: true })).toBeVisible()
    await expect(history.getByRole('status')).toContainText('Liczba wpisów: 1')
    expect(writes).toBe(1)
    const response = await page.request.get(`/api/time-entries?teamId=${team.id}`)
    expect((await response.json()).totalElements).toBe(1)
  })
}
