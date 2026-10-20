import { useState } from 'react'
import { expect, it } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { JiraIssuePicker, type JiraIssueLink } from '../../src/components/JiraIssuePicker'
import { renderApp } from '../support/render'
import { json, mockApi } from '../support/api'

const status = (overrides = {}) => ({ available: true, siteConnected: true, siteName: 'Studio', siteUrl: 'https://studio.atlassian.net', userConnected: true, canManage: false, ...overrides })
const issue = (n: number, extra = {}) => ({ id: String(1000 + n), key: `PROJ-${n}`, summary: `Task number ${n}`, status: 'To Do', issueType: 'Task', projectKey: 'PROJ', ...extra })

function Host() {
  const [value, setValue] = useState<JiraIssueLink | null>(null)
  return <><JiraIssuePicker userId="actor" teamId="team" value={value} onChange={setValue} /><span data-testid="chosen">{value?.key ?? 'brak'}</span></>
}

it.each([
  ['the integration is off', { available: false }],
  ['the team has no site', { siteConnected: false }],
])('renders nothing when %s', async (_name, overrides) => {
  const fetcher = mockApi({ 'GET /api/integrations/jira/status': () => json(status(overrides)) })
  renderApp(<Host />)
  await waitFor(() => expect(fetcher).toHaveBeenCalled())
  await waitFor(() => expect(screen.queryByRole('button', { name: /Wybierz zadanie/ })).not.toBeInTheDocument())
  expect(screen.queryByText(/połącz swoje konto/)).not.toBeInTheDocument()
})

it('points a person without a connected account to the integrations page', async () => {
  mockApi({ 'GET /api/integrations/jira/status': () => json(status({ userConnected: false })) })
  renderApp(<Host />)
  const link = await screen.findByRole('link', { name: 'połącz swoje konto Jira' })
  expect(link).toHaveAttribute('href', '/integrations?team=team')
  expect(screen.queryByRole('button', { name: /Wybierz zadanie/ })).not.toBeInTheDocument()
})

it('searches, picks an issue and lets the person remove the link again', async () => {
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'GET /api/integrations/jira/issues': () => json({ issues: [issue(1), issue(2)], nextPageToken: null }),
  })
  renderApp(<Host />)
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Znaleziono zadań: 2')
  await userEvent.click(await screen.findByRole('button', { name: /PROJ-2 Task number 2/ }))
  expect(screen.getByTestId('chosen')).toHaveTextContent('PROJ-2')
  expect(screen.queryByRole('searchbox')).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'Usuń powiązanie z zadaniem PROJ-2' }))
  expect(screen.getByTestId('chosen')).toHaveTextContent('brak')
  expect(screen.getByRole('button', { name: 'Wybierz zadanie z Jiry' })).toBeVisible()
})

it('waits for the person to stop typing and sends the filters the person chose', async () => {
  const queries: URLSearchParams[] = []
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'GET /api/integrations/jira/issues': url => { queries.push(url.searchParams); return json({ issues: [issue(1)], nextPageToken: null }) },
  })
  renderApp(<Host />)
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  await screen.findByRole('button', { name: /PROJ-1/ })
  expect(queries[0].get('assignee')).toBe('ME')
  expect(queries[0].get('sprint')).toBe('ANY')
  expect(queries[0].get('includeDone')).toBe('false')
  expect(queries[0].has('q')).toBe(false)

  await userEvent.type(screen.getByRole('searchbox', { name: 'Szukaj zadania' }), 'login')
  await waitFor(() => expect(queries.at(-1)?.get('q')).toBe('login'))
  expect(queries.filter(query => query.has('q'))).toHaveLength(1)

  await userEvent.selectOptions(screen.getByLabelText('Sprint'), 'ACTIVE')
  await userEvent.selectOptions(screen.getByLabelText('Zadania'), 'ANYONE')
  await userEvent.click(screen.getByRole('checkbox', { name: 'Pokaż także ukończone' }))
  await waitFor(() => expect(queries.at(-1)?.get('includeDone')).toBe('true'))
  expect(queries.at(-1)?.get('sprint')).toBe('ACTIVE')
  expect(queries.at(-1)?.get('assignee')).toBe('ANYONE')
})

it('loads more results with the page token', async () => {
  const tokens: (string | null)[] = []
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'GET /api/integrations/jira/issues': url => {
      tokens.push(url.searchParams.get('pageToken'))
      return url.searchParams.get('pageToken') ? json({ issues: [issue(3)], nextPageToken: null }) : json({ issues: [issue(1), issue(2)], nextPageToken: 'next-1' })
    },
  })
  renderApp(<Host />)
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Pokaż więcej zadań' }))
  expect(await screen.findByRole('button', { name: /PROJ-3/ })).toBeVisible()
  expect(tokens).toEqual([null, 'next-1'])
  expect(screen.queryByRole('button', { name: 'Pokaż więcej zadań' })).not.toBeInTheDocument()
})

it('says when nothing matches', async () => {
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'GET /api/integrations/jira/issues': () => json({ issues: [], nextPageToken: null }),
  })
  renderApp(<Host />)
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  expect(await screen.findByText('Nie znaleziono zadań.')).toBeVisible()
})

it('explains a Jira rate limit and allows trying again', async () => {
  let calls = 0
  mockApi({
    'GET /api/integrations/jira/status': () => json(status()),
    'GET /api/integrations/jira/issues': () => {
      calls += 1
      return calls === 1 ? new Response('{}', { status: 429, headers: { 'Retry-After': '120' } }) : json({ issues: [issue(1)], nextPageToken: null })
    },
  })
  renderApp(<Host />)
  await userEvent.click(await screen.findByRole('button', { name: 'Wybierz zadanie z Jiry' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Zbyt wiele prób')
  await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))
  expect(await screen.findByRole('button', { name: /PROJ-1/ })).toBeVisible()
})
