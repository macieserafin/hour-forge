import { expect, it } from 'vitest'
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { latestReleaseId, releases } from '../../src/lib/whatsNew'
import { renderApp } from '../support/render'
import { entry, history, json, mockApi, timer } from '../support/api'

const connected = { available: true, siteConnected: true, siteName: 'Studio', siteUrl: 'https://studio.atlassian.net', userConnected: true, canManage: false }
const linked = { ...entry, jiraIssueId: '1001', jiraIssueKey: 'PROJ-1' }
const issue = { id: '1001', key: 'PROJ-1', summary: 'Fix the login page', status: 'In Progress', issueType: 'Bug', projectKey: 'PROJ' }

it('shows the title and state of the issue when the key is hovered, and reads it only then', async () => {
  const fetcher = mockApi({ 'GET /api/integrations/jira/status': () => json(connected), 'GET /api/time-entries': () => json(history([linked])),
    'GET /api/integrations/jira/issues/1001': () => json(issue) })
  renderApp(<App />, '/time?team=team')
  const key = await screen.findByRole('link', { name: /PROJ-1/ })
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/issues/1001'))).toBe(false)
  await userEvent.hover(key)
  const card = await screen.findByRole('tooltip')
  expect(card).toHaveTextContent('Fix the login page')
  expect(card).toHaveTextContent('Bug · In Progress')
  expect(key).toHaveAttribute('aria-describedby', card.id)
  await userEvent.unhover(key)
  await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
})

it('also opens the card from the keyboard and closes it with Escape', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(connected), 'GET /api/time-entries': () => json(history([linked])), 'GET /api/integrations/jira/issues/1001': () => json(issue) })
  renderApp(<App />, '/time?team=team')
  const key = await screen.findByRole('link', { name: /PROJ-1/ })
  act(() => key.focus())
  await waitFor(() => expect(screen.getByRole('tooltip')).toHaveTextContent('Fix the login page'))
  await userEvent.keyboard('{Escape}')
  await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
})

it('says so when the title cannot be read, and shows no card without a connected account', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(connected), 'GET /api/time-entries': () => json(history([linked])), 'GET /api/integrations/jira/issues/1001': () => json({}, 404) })
  const view = renderApp(<App />, '/time?team=team')
  await userEvent.hover(await screen.findByRole('link', { name: /PROJ-1/ }))
  expect(await screen.findByRole('tooltip')).toHaveTextContent('Nie udało się wczytać zadania')
  view.unmount()

  const fetcher = mockApi({ 'GET /api/integrations/jira/status': () => json({ ...connected, userConnected: false }), 'GET /api/time-entries': () => json(history([linked])) })
  renderApp(<App />, '/time?team=team')
  await userEvent.hover(await screen.findByRole('link', { name: /PROJ-1/ }))
  expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/issues/'))).toBe(false)
})

it('starts a timer for the task of a past entry', async () => {
  let started: Record<string, unknown> = {}
  mockApi({ 'GET /api/integrations/jira/status': () => json(connected), 'GET /api/time-entries': () => json(history([linked])), 'GET /api/integrations/jira/issues/1001': () => json(issue),
    'POST /api/timer/start': (_url, options) => { started = JSON.parse(String(options.body)); return json({ ...timer, startedAt: new Date().toISOString(), updatedAt: new Date().toISOString() }, 201) } })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Uruchom timer dla zadania PROJ-1' }))
  await waitFor(() => expect(started).toEqual({ teamId: 'team', projectId: 'project', description: null, jiraIssueId: '1001' }))
})

it('offers no task timer for an entry without a Jira link or while a timer runs', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(connected), 'GET /api/time-entries': () => json(history([entry, { ...linked, id: 'two' }])),
    'GET /api/timer/active': () => json({ ...timer, startedAt: new Date(Date.now() - 60_000).toISOString(), updatedAt: new Date().toISOString() }) })
  renderApp(<App />, '/time?team=team')
  const button = await screen.findByRole('button', { name: 'Uruchom timer dla zadania PROJ-1' })
  expect(screen.getAllByRole('button', { name: /Uruchom timer dla zadania/ })).toHaveLength(1)
  expect(button).toBeDisabled()
})

it('lists the news, marks them as new once and counts them in the menu', async () => {
  mockApi()
  renderApp(<App />, '/dashboard?team=team')
  const link = await screen.findByRole('link', { name: /Co nowego/ })
  expect(within(link).getByLabelText(`Nowości: ${releases.length}`)).toBeVisible()
  await userEvent.click(link)
  expect(await screen.findByRole('heading', { name: releases[0]!.title })).toBeVisible()
  expect(screen.getAllByText('Nowe')).toHaveLength(releases.length)
  await waitFor(() => expect(screen.queryByLabelText(/Nowości:/)).not.toBeInTheDocument())
  expect(window.localStorage.getItem('hourforge:whats-new-seen:actor')).toBe(latestReleaseId)
})

it('shows no marker and no "new" badge after the news were seen', async () => {
  window.localStorage.setItem('hourforge:whats-new-seen:actor', latestReleaseId)
  mockApi()
  renderApp(<App />, '/whats-new')
  await screen.findByRole('heading', { name: releases[0]!.title })
  expect(screen.queryByText('Nowe')).not.toBeInTheDocument()
  expect(screen.queryByLabelText(/Nowości:/)).not.toBeInTheDocument()
})
