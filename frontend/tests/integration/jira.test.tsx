import { beforeEach, expect, it, vi } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { navigation } from '../../src/lib/jira'
import { renderApp } from '../support/render'
import { json, mockApi, team } from '../support/api'

const status = (overrides = {}) => ({ available: true, siteConnected: false, siteName: null, siteUrl: null, userConnected: false, canManage: true, ...overrides })
const connectedSite = { siteConnected: true, siteName: 'Studio Jira', siteUrl: 'https://studio.atlassian.net' }

beforeEach(() => { vi.spyOn(navigation, 'go').mockImplementation(() => undefined) })

it('says the integration is off when the server has it switched off', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(status({ available: false })) })
  renderApp(<App />, '/integrations?team=team')
  expect(await screen.findByText(/wyłączona na tym serwerze/)).toBeVisible()
  expect(screen.queryByRole('button', { name: /Połącz/ })).not.toBeInTheDocument()
})

it('lets the owner start connecting the team and sends the browser to Atlassian', async () => {
  const requests: unknown[] = []
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'POST /api/integrations/jira/authorize': (_url, options) => { requests.push(JSON.parse(String(options.body))); return json({ url: 'https://auth.atlassian.com/authorize?state=abc' }) },
  })
  renderApp(<App />, '/integrations?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Połącz zespół z Jira Cloud' }))
  await waitFor(() => expect(navigation.go).toHaveBeenCalledWith('https://auth.atlassian.com/authorize?state=abc'))
  expect(requests).toEqual([{ teamId: 'team', intent: 'SITE' }])
})

it('does not offer a member to choose the site, only to wait for the owner', async () => {
  mockApi({
    'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]),
    'GET /api/integrations/jira/status': () => json(status({ canManage: false })),
  })
  renderApp(<App />, '/integrations?team=team')
  expect(await screen.findByText(/Połączyć witrynę może właściciel zespołu/)).toBeVisible()
  expect(screen.queryByRole('button', { name: /Połącz zespół/ })).not.toBeInTheDocument()
})

it('lets a member connect their own account to the chosen site', async () => {
  const intents: unknown[] = []
  mockApi({
    'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]),
    'GET /api/integrations/jira/status': () => json(status({ ...connectedSite, canManage: false })),
    'POST /api/integrations/jira/authorize': (_url, options) => { intents.push(JSON.parse(String(options.body)).intent); return json({ url: 'https://auth.atlassian.com/authorize?state=m' }) },
  })
  renderApp(<App />, '/integrations?team=team')
  expect(await screen.findByText('Niepołączone')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Odłącz witrynę' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Połącz moje konto Jira' }))
  await waitFor(() => expect(navigation.go).toHaveBeenCalled())
  expect(intents).toEqual(['MEMBER'])
})

it('lets a connected member disconnect their own account', async () => {
  let connected = true
  mockApi({
    'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]),
    'GET /api/integrations/jira/status': () => json(status({ ...connectedSite, canManage: false, userConnected: connected })),
    'DELETE /api/integrations/jira/connection': () => { connected = false; return json(null, 204) },
  })
  renderApp(<App />, '/integrations?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Rozłącz moje konto Jira' }))
  expect(await screen.findByText('Niepołączone')).toBeVisible()
})

it('shows the result of the consent and then lets the owner pick one of several sites', async () => {
  let chosen = ''
  mockApi({
    'GET /api/integrations/jira/status': () => json(chosen ? status({ ...connectedSite, userConnected: true }) : status()),
    'GET /api/integrations/jira/sites': () => json([{ cloudId: 'c1', name: 'One', url: 'https://one.atlassian.net' }, { cloudId: 'c2', name: 'Two', url: 'https://two.atlassian.net' }]),
    'POST /api/integrations/jira/site': (_url, options) => { chosen = JSON.parse(String(options.body)).cloudId; return json(null, 204) },
  })
  renderApp(<App />, '/integrations?team=team&jira=choose-site')
  expect(await screen.findByRole('heading', { name: 'Wybierz witrynę Jira zespołu' })).toBeVisible()
  expect(screen.getByText(/Konto Jira zostało połączone/)).toBeVisible()
  const two = await screen.findByRole('radio', { name: /Two/ })
  const submit = screen.getByRole('button', { name: 'Użyj tej witryny' })
  expect(submit).toBeDisabled()
  await userEvent.click(two)
  await userEvent.click(submit)
  await waitFor(() => expect(chosen).toBe('c2'))
  expect(await screen.findByText('Studio Jira', { exact: false })).toBeVisible()
})

it.each([
  ['denied', /nie zostało zatwierdzone/],
  ['invalid-state', /wygasło lub zostało już użyte/],
  ['no-access', /nie ma dostępu do witryny/],
  ['unavailable', /niedostępna/],
])('explains the failed consent outcome %s', async (outcome, text) => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(status()) })
  renderApp(<App />, `/integrations?team=team&jira=${outcome}`)
  expect(await screen.findByRole('alert')).toHaveTextContent(text)
})

it('ignores an unknown outcome word in the address', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(status()) })
  renderApp(<App />, '/integrations?team=team&jira=unknown-word')
  expect(await screen.findByRole('button', { name: 'Połącz zespół z Jira Cloud' })).toBeVisible()
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

it('asks before the owner disconnects the whole site', async () => {
  let removed = false
  mockApi({
    'GET /api/integrations/jira/status': () => json(removed ? status() : status({ ...connectedSite, userConnected: true })),
    'DELETE /api/integrations/jira/site': () => { removed = true; return json(null, 204) },
  })
  renderApp(<App />, '/integrations?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Odłącz witrynę' }))
  const dialog = within(screen.getByRole('dialog'))
  expect(dialog.getByText(/połączenia kont wszystkich członków/)).toBeVisible()
  await userEvent.click(dialog.getByRole('button', { name: 'Odłącz witrynę' }))
  expect(await screen.findByRole('button', { name: 'Połącz zespół z Jira Cloud' })).toBeVisible()
})

it('keeps the integration out of reach of an archived team', async () => {
  mockApi({
    'GET /api/teams': () => json([{ ...team, archivedAt: '2026-10-19T12:00:00Z' }]),
    'GET /api/integrations/jira/status': () => json(status()),
  })
  renderApp(<App />, '/integrations?team=team')
  expect(await screen.findByText(/Połączyć witrynę może właściciel zespołu/)).toBeVisible()
})
