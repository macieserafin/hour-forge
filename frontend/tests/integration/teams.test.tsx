import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'

it('handles team loading failure and retry', async () => {
  let failed = true
  mockApi({ 'GET /api/teams': () => failed ? json({}, 500) : json([team]) })
  renderApp(<App />, '/teams')
  expect(await screen.findByRole('alert')).toBeVisible()
  failed = false
  await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))
  expect(await screen.findByRole('button', { name: new RegExp(team.name) })).toBeVisible()
})
it('validates a team name before sending a create request', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/teams')
  await screen.findByRole('button', { name: 'Utwórz zespół' })
  await userEvent.click(screen.getByRole('button', { name: 'Utwórz zespół' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Nazwa zespołu'), '   ')
  await userEvent.click(modal.getByRole('button', { name: 'Utwórz zespół' }))
  expect(modal.getByRole('alert')).toHaveTextContent('Wpisz nazwę zespołu.')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
})

it('creates a team and generates an invitation through the shared API boundary', async () => {
  let teams = [] as typeof team[]
  mockApi({ 'GET /api/teams': () => json(teams), 'POST /api/teams': () => { teams = [team]; return json(team, 201) },
    'POST /api/teams/team/invites': () => json({ id: 'invite', teamId: team.id, invitationUrl: 'https://example.test/invite/token', token: 'token', maxUses: 1, expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString() }) })
  renderApp(<App />, '/teams')
  await userEvent.click(await screen.findByRole('button', { name: 'Utwórz zespół' }))
  let modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Nazwa zespołu'), 'Studio')
  await userEvent.click(modal.getByRole('button', { name: 'Utwórz zespół' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Zaproś do zespołu' }))
  modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Limit użyć', { exact: false })).toHaveValue(1)
  await userEvent.click(modal.getByRole('button', { name: 'Generuj link' }))
  expect(await modal.findByLabelText('Link zaproszeniowy')).toHaveValue('https://example.test/invite/token')
})

it('also mails the invitation when an address is given', async () => {
  let sent: Record<string, unknown> = {}
  mockApi({ 'POST /api/teams/team/invites': (_url, options) => { sent = JSON.parse(String(options.body))
    return json({ id: 'invite', teamId: team.id, invitationUrl: 'https://example.test/invite/token', token: 'token', maxUses: 1, expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString() }, 201) } })
  renderApp(<App />, '/teams?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Zaproś do zespołu' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText(/Adres e-mail osoby/), 'gosc@example.com')
  await userEvent.click(modal.getByRole('button', { name: 'Generuj link' }))
  expect(await screen.findByText('Wysłaliśmy zaproszenie na adres gosc@example.com.')).toBeVisible()
  expect(sent).toMatchObject({ email: 'gosc@example.com', maxUses: 1 })
})
