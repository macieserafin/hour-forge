import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, user } from '../support/api'

const operator = { ...user, operator: true }
const stats = { accounts: 12, unconfirmed: 1, blocked: 0, activeLastWeek: 5, activeLastMonth: 9, twoFactor: 2, teams: 4, timeEntries: 300 }
const spammer = { id: 'spammer', email: 'spam@example.test', displayName: 'Spammer', createdAt: '2026-10-01T10:00:00Z', lastActiveAt: null,
  confirmed: true, blockedAt: null, blockedReason: null, twoFactor: true, ownedTeams: 1, inactiveOwnerKept: true }
const spamTeam = { id: 'spam-team', name: 'Spam Team', createdAt: '2026-10-01T10:00:00Z', archivedAt: null, members: 1, ownerEmail: 'spam@example.test', timeEntries: 3 }

function operatorApi(overrides = {}) {
  return mockApi({ 'GET /api/me': () => json(operator), 'GET /api/operator/stats': () => json(stats),
    'GET /api/operator/accounts': () => json({ items: [{ ...spammer }, { ...spammer, id: operator.id, email: operator.email, displayName: operator.displayName }], page: 0, hasMore: false }),
    'GET /api/operator/teams': () => json({ items: [spamTeam], page: 0, hasMore: false }), ...overrides })
}

it('offers the panel only to operators', async () => {
  mockApi()
  renderApp(<App />, '/operator')
  expect(await screen.findByRole('heading', { name: 'Cześć, Test.' })).toBeVisible()
  expect(screen.queryByRole('link', { name: 'Operator' })).toBeNull()
})

it('shows the statistics and publishes a notice that appears as a banner', async () => {
  let announcement: unknown = null
  let sent: unknown
  operatorApi({ 'GET /api/config': () => json({ botCheckSiteKey: null, announcement }),
    'PUT /api/operator/announcement': (_url: URL, options: RequestInit) => {
      sent = JSON.parse(String(options.body))
      announcement = { ...(sent as object), updatedAt: '2026-10-23T10:00:00Z' }
      return json(announcement)
    } })
  renderApp(<App />, '/dashboard')
  await userEvent.click((await screen.findAllByRole('link', { name: 'Operator' }))[0]!)
  const statsRegion = await screen.findByRole('region', { name: 'Statystyki' })
  expect(await within(statsRegion).findByText('Aktywne w 7 dni')).toBeVisible()
  expect(statsRegion).toHaveTextContent('300')

  const notice = screen.getByRole('region', { name: 'Ogłoszenie dla wszystkich' })
  await userEvent.type(within(notice).getByLabelText('Treść ogłoszenia'), 'Prace serwisowe w sobotę')
  await userEvent.selectOptions(within(notice).getByLabelText('Rodzaj'), 'WARNING')
  await userEvent.click(within(notice).getByRole('button', { name: 'Opublikuj' }))
  expect(await within(notice).findByText('Ogłoszenie zostało opublikowane.')).toBeVisible()
  expect(sent).toEqual({ message: 'Prace serwisowe w sobotę', level: 'WARNING' })
  expect(await screen.findByRole('status', { name: 'Ogłoszenie operatora serwisu' })).toHaveTextContent('Prace serwisowe w sobotę')
})

it('blocks an account with a reason and never offers actions on the operator', async () => {
  let blocked: unknown
  operatorApi({ 'POST /api/operator/accounts/spammer/block': (_url: URL, options: RequestInit) => { blocked = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, '/operator')
  const table = await screen.findByRole('region', { name: 'Lista kont' })
  const rows = within(table).getAllByRole('row').slice(1)
  expect(rows[1]).toHaveTextContent('To Ty')
  expect(within(rows[1]!).queryByRole('button')).toBeNull()
  expect(rows[0]).toHaveTextContent('Logowanie dwuetapowe')
  expect(rows[0]).toHaveTextContent('Nieaktywne od 24 miesięcy, zachowane dla zespołów')

  await userEvent.click(within(rows[0]!).getByRole('button', { name: 'Zablokuj' }))
  const dialog = screen.getByRole('dialog')
  expect(within(dialog).getByRole('button', { name: 'Zablokuj' })).toBeDisabled()
  await userEvent.type(within(dialog).getByLabelText(/Powód/), 'Spam w zaproszeniach')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Zablokuj' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  expect(blocked).toEqual({ reason: 'Spam w zaproszeniach' })
})

it('deletes an account or a team only after typing its address or name', async () => {
  const deleted: string[] = []
  operatorApi({ 'DELETE /api/operator/accounts/spammer': () => { deleted.push('account'); return json(null, 204) },
    'DELETE /api/operator/teams/spam-team': () => { deleted.push('team'); return json(null, 204) } })
  renderApp(<App />, '/operator')
  const accounts = await screen.findByRole('region', { name: 'Lista kont' })
  await userEvent.click(within(within(accounts).getAllByRole('row')[1]!).getByRole('button', { name: 'Usuń' }))
  let dialog = screen.getByRole('dialog')
  await userEvent.type(within(dialog).getByLabelText('Adres e-mail konta'), 'spam@example')
  expect(within(dialog).getByRole('button', { name: 'Usuń konto' })).toBeDisabled()
  await userEvent.type(within(dialog).getByLabelText('Adres e-mail konta'), '.test')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Usuń konto' }))
  await waitFor(() => expect(deleted).toEqual(['account']))

  const teams = await screen.findByRole('region', { name: 'Lista zespołów' })
  await userEvent.click(within(teams).getByRole('button', { name: 'Usuń' }))
  dialog = screen.getByRole('dialog')
  expect(dialog).toHaveTextContent('wszystkie wpisy czasu (3)')
  await userEvent.type(within(dialog).getByLabelText('Nazwa zespołu'), 'Spam Team')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Usuń zespół' }))
  await waitFor(() => expect(deleted).toEqual(['account', 'team']))
})

it('tells a blocked person why the sign-in failed', async () => {
  mockApi({ 'GET /api/me': () => json(null, 401), 'POST /api/auth/login': () => json({ code: 'ACCOUNT_BLOCKED' }, 403) })
  renderApp(<App />, '/login')
  await userEvent.type(await screen.findByLabelText('Adres e-mail'), user.email)
  await userEvent.type(screen.getByLabelText('Hasło', { exact: true }), 'secret')
  await userEvent.click(screen.getByRole('button', { name: 'Zaloguj się' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('zablokowane przez operatora')
})
