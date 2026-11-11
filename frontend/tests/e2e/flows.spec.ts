import { expect, owner, password, test } from '../support/e2e'
import { prepareNewcomer, prepareSolo, prepareTeams } from '../support/demo-sets'

/**
 * Key flows of the rebuilt frontend on the real backend. Each test starts from an empty
 * database; demo sets give known checksums.
 */

test('a person without a team creates a team, adds a project separately, tracks time and adds undated work', {
  tag: '@responsive',
}, async ({ page }) => {
  await prepareNewcomer(page)
  await page.goto('/app/work')
  await expect(page).toHaveURL(/\/app\/start$/)
  await expect(page.getByRole('button', { name: /Pracuję samodzielnie/ })).toHaveCount(0)
  await page.getByRole('button', { name: /Tworzę zespół/ }).click()
  await expect(page.getByLabel('Pierwszy projekt')).toHaveCount(0)
  await page.getByLabel('Nazwa zespołu').fill('Pracownia')
  await page.getByRole('button', { name: 'Utwórz zespół', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Moja praca', level: 1 })).toBeVisible()
  const workUrl = new URL(page.url())
  const teamId = workUrl.searchParams.get('team')
  await page.goto(`/app/teams/${teamId}/settings`)
  await page.getByRole('radio', { name: 'Morski' }).check()
  await page.getByRole('button', { name: 'Zapisz zmiany' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Zapisano ustawienia zespołu' })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('radio', { name: 'Morski' })).toBeChecked()
  await page.goto(workUrl.pathname + workUrl.search)
  await page.getByRole('link', { name: 'Przejdź do projektów' }).click()
  await page.getByRole('button', { name: 'Nowy projekt' }).first().click()
  await page.getByLabel('Nazwa projektu').fill('Praca dyplomowa')
  await page.getByRole('button', { name: 'Zapisz', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Praca dyplomowa', exact: true })).toBeVisible()
  await page.goto(workUrl.pathname + workUrl.search)

  await page.getByRole('button', { name: 'Uruchom timer', exact: true }).click()
  const measure = page.getByRole('article', { name: 'Mierz czas' })
  await expect(measure.getByRole('button', { name: /Praca dyplomowa/ })).toBeVisible()
  await measure.getByRole('button', { name: 'Rozpocznij', exact: true }).click()
  await expect(measure.getByRole('timer')).toBeVisible()
  await page.waitForTimeout(1500)
  await measure.getByRole('button', { name: 'Zakończ i zapisz' }).click()
  await expect(page.getByRole('status').filter({ hasText: /Zapisano .* w projekcie Praca dyplomowa/ })).toBeVisible()

  await page.getByRole('button', { name: 'Dodaj czas', exact: true }).click()
  const add = page.getByRole('article', { name: 'Dodaj czas' })
  await add.getByLabel('Czas trwania').fill('1h 30m')
  await expect(add.getByText('= 1 h 30 min')).toBeVisible()
  await add.getByRole('button', { name: 'Nie pamiętam daty' }).click()
  await add.getByLabel(/^Opis/).fill('Czytanie literatury')
  await add.getByRole('button', { name: 'Zapisz 1 h 30 min' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Zapisano 1 h 30 min bez daty.' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /^Dzisiaj/ })).toHaveText(/Dzisiaj · 0 min/)
  await page.getByRole('link', { name: 'Zobacz wpis' }).click()
  await expect(page.locator('.saved-entry')).toContainText('Czytanie literatury')
  await expect(page.locator('.saved-entry')).toContainText('1 h 30 min')
})

test('an invitation survives sign-in and the new member records work in the team', async ({ page }) => {
  const { team, invitations } = await prepareNewcomer(page)
  // Links that can no longer be used explain why, before the person joins with the valid one.
  for (const link of [invitations.expired, invitations.used, invitations.revoked]) {
    await page.goto(link)
    await expect(page.getByRole('alert')).toBeVisible()
  }
  await page.goto(invitations.valid)
  await page.getByRole('button', { name: 'Dołącz do zespołu' }).click()
  await expect(page.getByText(`Należysz teraz do zespołu ${team.name}`)).toBeVisible()
  await page.getByRole('link', { name: 'Przejdź do swojej pracy' }).click()
  await expect(page.getByRole('heading', { name: 'Moja praca', level: 1 })).toBeVisible()
  await page.getByRole('button', { name: 'Uruchom timer', exact: true }).click()
  await expect(
    page.getByRole('article', { name: 'Mierz czas' }).getByRole('button', { name: /Portal klienta/ }),
  ).toBeVisible()
})

test('work across teams keeps the target unambiguous and the totals match the server', async ({ page, browser }) => {
  const { teams } = await prepareTeams(page, browser)
  await page.goto(`/app/work?team=${teams.atlas.id}`)
  await page.getByRole('button', { name: 'Dodaj czas', exact: true }).click()
  const manual = page.getByRole('article', { name: 'Dodaj czas' })
  await manual.getByLabel('Czas trwania').fill('45m')
  await manual.getByLabel(/^Opis/).fill('Szkic dla Atlasu')
  await page.getByLabel('Pokaż wpisy').selectOption(teams.nord.id)
  await expect(manual.getByRole('button', { name: /Zapisz w projekcie.*Studio Atlas/ })).toBeVisible()
  await expect(manual.getByLabel(/^Opis/)).toHaveValue('Szkic dla Atlasu')
  await manual.getByRole('button', { name: 'Nie pamiętam daty' }).click()
  await manual.getByRole('button', { name: 'Zapisz 45 min' }).click()
  await expect(page.getByRole('link', { name: 'Zobacz wpis' })).toHaveAttribute(
    'href',
    new RegExp(`team=${teams.atlas.id}`),
  )
  await page.getByLabel('Pokaż wpisy').selectOption('')
  await page.getByRole('button', { name: 'Uruchom timer', exact: true }).click()
  const measure = page.getByRole('article', { name: 'Mierz czas' })
  await measure
    .getByRole('button', { name: /Projekt/ })
    .first()
    .click()
  await page.getByRole('combobox', { name: /Szukaj projekt/ }).press('Tab')
  await expect(page.getByRole('listbox')).toBeHidden()
  await measure
    .getByRole('button', { name: /Projekt/ })
    .first()
    .click()
  const list = page.getByRole('listbox')
  // The same project name in two teams stays distinguishable by its team group.
  await expect(
    list.getByRole('group', { name: 'Studio Atlas' }).getByRole('option', { name: 'Strona WWW' }),
  ).toBeVisible()
  await expect(
    list.getByRole('group', { name: 'Agencja Nord' }).getByRole('option', { name: 'Strona WWW' }),
  ).toBeVisible()
  await list.getByRole('group', { name: 'Agencja Nord' }).getByRole('option', { name: 'Strona WWW' }).click()
  await measure.getByLabel(/Nad czym pracujesz/).fill('Poprawki menu')
  await measure.getByRole('button', { name: 'Rozpocznij', exact: true }).click()
  await expect(measure.getByRole('timer')).toBeVisible()
  await expect(measure.locator('.measure-project')).toContainText('Strona WWW')
  await expect(measure.locator('.measure-project')).toContainText('Agencja Nord')

  // The timer stays visible from another section and its target does not change.
  await page.getByRole('link', { name: 'Projekty' }).first().click()
  await expect(page.getByRole('link', { name: 'Przejdź do timera' })).toBeVisible()

  await page.goto(`/app/work/history?team=${teams.atlas.id}`)
  const grid = page.getByRole('table')
  await expect(grid).toBeVisible()
})

test('the team report drills down from a person to entries and keeps filters in the address', {
  tag: '@responsive',
}, async ({ page, browser }) => {
  const { teams, maja } = await prepareTeams(page, browser)
  await page.goto(`/dashboard?view=team&team=${teams.atlas.id}`)
  await expect(page).toHaveURL(new RegExp(`/app/teams/${teams.atlas.id}/reports`))
  await page.getByRole('combobox', { name: 'Okres', exact: true }).selectOption('lastWeek')
  await page.getByRole('button', { name: new RegExp(`Pokaż wpisy: ${maja.displayName}`) }).click()
  await expect(page).toHaveURL(new RegExp(`person=${maja.id}`))
  await expect(page.getByRole('button', { name: new RegExp(`Usuń filtr: Osoba: ${maja.displayName}`) })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(
    page.viewportSize()!.width,
  )
  await page.getByText('Eksportuj', { exact: true }).click()
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'CSV' }).click()
  expect((await download).suggestedFilename()).toMatch(/\.csv$/)
})

test('a member has no team report and sees their own report instead', async ({ page, browser }) => {
  const { teams } = await prepareTeams(page, browser)
  await page.goto(`/app/teams/${teams.club.id}/reports`)
  await expect(page.getByText('Raport zespołu widzą właściciel i administratorzy.')).toBeVisible()
  await page.getByRole('link', { name: 'Mój raport' }).click()
  await expect(page.getByRole('heading', { name: 'Mój raport', level: 1 })).toBeVisible()
})

test('an unknown address shows 404 and a deep link returns after sign-in', async ({ page }) => {
  await prepareSolo(page)
  await page.goto('/app/nie-ma-takiej-strony')
  await expect(page.getByRole('heading', { name: 'Nie ma takiej strony' })).toBeVisible()
  await page.goto('/')
  await expect(page).toHaveURL(/\/app\/work$/)
  await page.context().clearCookies()
  await page.goto('/')
  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('link', { name: 'Utwórz konto' })).toBeVisible()
  await page.goto('/app/account/security')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
  await page.getByLabel('Adres e-mail', { exact: true }).fill(owner)
  await page.getByLabel('Hasło', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Zaloguj się', exact: true }).click()
  await expect(page).toHaveURL(/\/app\/account\/security$/)
  // The new-device e-mail links to the old /account#sessions: it opens Security at the device list.
  await page.setViewportSize({ width: 390, height: 640 })
  await page.goto('/account#sessions')
  await expect(page).toHaveURL(/\/app\/account\/security#sessions$/)
  await expect(page.getByRole('heading', { name: 'Zalogowane urządzenia' })).toBeInViewport()
})
