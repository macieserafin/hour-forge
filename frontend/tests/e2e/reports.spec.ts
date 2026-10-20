import { test, expect, setupTeam, create, login, member } from '../support/e2e'

test('owner filters and pages team reports while a member only sees personal time', { tag: '@responsive' }, async ({ page, browser }, info) => {
  const { team, project } = await setupTeam(page, `Reports ${info.project.name} ${Date.now()}`)
  const historical = await create(page.request, `/api/teams/${team.id}/projects`, { name: 'Historical project', description: null })
  const dates = await (await page.request.get(`/api/dashboard/summary?teamId=${team.id}`)).json()
  const invite = await create(page.request, `/api/teams/${team.id}/invites`, { expiresAt: new Date(Date.now() + 3600000).toISOString(), maxUses: 1 })
  const memberContext = await browser.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const memberPage = await memberContext.newPage()
    await login(memberPage, member)
    await create(memberPage.request, `/api/invites/${invite.token}/accept`, {})
    await create(memberPage.request, '/api/time-entries', { teamId: team.id, projectId: historical.id,
      workDate: dates.today, durationSeconds: 1800, description: 'Member historical work' })
    await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id,
      workDate: dates.today, durationSeconds: 900, description: 'Owner planning work' })
    for (let index = 0; index < 21; index++) {
      await create(page.request, '/api/time-entries', { teamId: team.id, projectId: project.id,
        workDate: dates.today, durationSeconds: 60, description: `Owner task ${index}` })
    }
    await create(page.request, `/api/teams/${team.id}/projects/${historical.id}/archive`, {})
    const foreign = await create(page.request, '/api/teams', { name: 'Unrelated team', timezone: 'Europe/Warsaw' })
    const foreignProject = await create(page.request, `/api/teams/${foreign.id}/projects`, { name: 'Unrelated project', description: null })
    await create(page.request, '/api/time-entries', { teamId: foreign.id, projectId: foreignProject.id,
      workDate: dates.today, durationSeconds: 10000, description: 'Foreign team work' })

    await page.goto(`/dashboard?team=${team.id}`)
    await page.getByRole('button', { name: 'Zespół', exact: true }).click()
    const total = page.getByRole('region', { name: 'Łączny czas zespołu', exact: true })
    await expect(total).toContainText('01:06:00')
    await expect(page.getByRole('region', { name: 'Liczba wpisów zespołu' })).toContainText('23')
    await expect(page.getByRole('region', { name: 'Czas według osób', exact: true })).toContainText('User Two')
    await expect(page.getByRole('region', { name: 'Czas według osób', exact: true })).toContainText('00:36:00')
    await expect(page.getByRole('region', { name: 'Czas według projektów', exact: true })).toContainText('Historical project')
    await expect(page.getByRole('region', { name: 'Czas według projektów', exact: true })).toContainText('Projekt archiwalny')
    await expect(page.getByText('Foreign team work')).toHaveCount(0)
    const history = page.getByRole('region', { name: 'Wpisy zespołu', exact: true })
    await history.getByRole('button', { name: 'Następna' }).click()
    await expect(history).toContainText('Strona 2')
    await expect(history.getByRole('button', { name: 'Następna' })).toBeDisabled()
    await page.getByLabel('Kolejność wpisów').selectOption('DURATION:DESC')
    await expect(history).toContainText('Strona 1')
    await expect(history).toContainText('Member historical work')

    const memberData = await (await memberPage.request.get('/api/me')).json()
    await page.getByLabel('Osoba', { exact: true }).selectOption(memberData.id)
    await page.getByLabel('Projekt w raporcie').selectOption(historical.id)
    await page.getByLabel('Rodzaj wpisu').selectOption('MANUAL')
    await page.getByRole('button', { name: 'Filtruj raport' }).click()
    await expect(total).toContainText('00:30:00')
    await expect(history).toContainText('Member historical work')
    await expect(history).not.toContainText('Owner planning work')
    await expect(history.getByRole('button', { name: 'Edytuj', exact: true })).toHaveCount(0)
    await page.screenshot({ path: info.outputPath('owner-dashboard.png'), fullPage: true, animations: 'disabled' })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)

    await page.getByRole('button', { name: 'Wyczyść filtry' }).click()
    await expect(total).toContainText('01:06:00')
    await page.getByLabel('Zespół na pulpicie').selectOption(foreign.id)
    await expect(total).toContainText('02:46:40')
    await expect(history).toContainText('Foreign team work')
    await expect(history).not.toContainText('Member historical work')

    await memberPage.goto(`/dashboard?team=${team.id}&view=team`)
    await expect(memberPage.getByRole('region', { name: 'Czas dzisiaj', exact: true })).toContainText('00:30:00')
    await expect(memberPage.getByRole('group', { name: 'Widok pulpitu' })).toHaveCount(0)
    expect((await memberPage.request.get(`/api/teams/${team.id}/reports/summary`)).status()).toBe(403)
  } finally {
    await memberContext.close()
  }
})
