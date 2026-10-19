import { test, expect, setupTeam, create, login, member } from '../support/e2e'

test('owner renames and hands over a team, then the new owner archives and deletes it', async ({ page, browser }) => {
  const { team, project } = await setupTeam(page, `Manage ${Date.now()}`)
  const invite = await create(page.request, `/api/teams/${team.id}/invites`, { expiresAt: new Date(Date.now() + 3600000).toISOString(), maxUses: 1 })
  const memberContext = await browser.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const memberPage = await memberContext.newPage()
    await login(memberPage, member)
    await create(memberPage.request, `/api/invites/${invite.token}/accept`, {})
    await create(memberPage.request, '/api/time-entries', { teamId: team.id, projectId: project.id,
      workDate: (await (await memberPage.request.get(`/api/dashboard/summary?teamId=${team.id}`)).json()).today, durationSeconds: 900, description: 'Kept work' })

    await page.goto(`/teams?team=${team.id}`)
    const selected = page.getByRole('region', { name: 'Wybrany zespół' })
    await selected.getByRole('button', { name: 'Ustawienia zespołu' }).click()
    let settings = page.getByRole('dialog', { name: 'Ustawienia zespołu' })
    await settings.getByLabel('Nazwa zespołu').fill(`Renamed ${team.name}`)
    await settings.getByLabel('Strefa czasowa').selectOption('UTC')
    await settings.getByRole('button', { name: 'Zapisz zmiany' }).click()
    await expect(selected.getByRole('heading')).toHaveText(`Renamed ${team.name}`)

    await page.getByRole('button', { name: 'Przekaż własność: User Two', exact: true }).click()
    await page.getByRole('dialog', { name: 'Przekaż własność zespołu' }).getByRole('button', { name: 'Przekaż własność' }).click()
    await expect(selected).toContainText('Twoja rola: Członek')
    await expect(selected.getByRole('button', { name: 'Ustawienia zespołu' })).toHaveCount(0)

    await memberPage.goto(`/teams?team=${team.id}`)
    const memberSelected = memberPage.getByRole('region', { name: 'Wybrany zespół' })
    await memberSelected.getByRole('button', { name: 'Ustawienia zespołu' }).click()
    settings = memberPage.getByRole('dialog', { name: 'Ustawienia zespołu' })
    await settings.getByRole('button', { name: 'Zarchiwizuj zespół' }).click()
    await memberPage.getByRole('dialog', { name: 'Zarchiwizuj zespół' }).getByRole('button', { name: 'Zarchiwizuj na stałe' }).click()
    await expect(memberSelected).toContainText('Zespół jest zarchiwizowany')
    await memberPage.goto(`/time?team=${team.id}`)
    await expect(memberPage.getByText(/jest zarchiwizowany/)).toBeVisible()
    await expect(memberPage.getByText('Kept work')).toBeVisible()
    await expect(memberPage.getByRole('button', { name: 'Uruchom timer' })).toHaveCount(0)

    await memberPage.goto(`/teams?team=${team.id}`)
    await memberSelected.getByRole('button', { name: 'Ustawienia zespołu' }).click()
    await memberPage.getByRole('dialog', { name: 'Ustawienia zespołu' }).getByRole('button', { name: 'Usuń zespół' }).click()
    const removal = memberPage.getByRole('dialog', { name: 'Usuń zespół' })
    await expect(removal).toContainText('Wpisy czasu')
    await removal.getByLabel('Wpisz nazwę zespołu, aby potwierdzić').fill(`Renamed ${team.name}`)
    await removal.getByRole('button', { name: 'Usuń zespół na zawsze' }).click()
    await expect(memberPage.getByText(`Usunięto zespół Renamed ${team.name}.`)).toBeVisible()
    expect((await (await page.request.get('/api/teams')).json()).some((item: { id: string }) => item.id === team.id)).toBe(false)
  } finally {
    await memberContext.close()
  }
})
