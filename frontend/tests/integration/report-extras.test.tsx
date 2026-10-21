import { expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { renderApp } from '../support/render'
import { json, mockApi, team } from '../support/api'
import { reportBucket, reportHandlers, reportPage } from '../support/reports'

const connected = { available: true, siteConnected: true, siteName: 'Site', siteUrl: 'https://x.atlassian.net', userConnected: true, canManage: true }

it('shows a timesheet with a row per person, a column per day and totals', async () => {
  mockApi({ ...reportHandlers() })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  const table = within(await screen.findByRole('region', { name: 'Tabela karty czasu' }))
  expect(table.getByRole('rowheader', { name: 'Anna Kowalska' })).toBeVisible()
  expect(table.getAllByRole('columnheader')).toHaveLength(4)
  expect(table.getAllByText('0:15').length).toBeGreaterThan(1)
})

it('explains why there is no timesheet for entries without a date', async () => {
  mockApi({ ...reportHandlers() })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await screen.findByRole('region', { name: 'Tabela karty czasu' })
  await userEvent.click(screen.getByLabelText('Bez daty', { exact: true }))
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  expect(await screen.findByText(/Karta czasu wymaga zakresu dat/)).toBeVisible()
})

it('offers the Jira task filter and grouping only when a Jira site is connected', async () => {
  const requests: string[] = []
  mockApi({ ...reportHandlers(), 'GET /api/integrations/jira/status': () => json(connected),
    'GET /api/teams/team/reports/breakdown': url => { requests.push(url.search)
      return json({ ...reportPage([{ ...reportBucket, jiraIssueKey: url.searchParams.get('groupBy') === 'JIRA_ISSUE' ? 'PROJ-7' : null }]), groupBy: url.searchParams.get('groupBy') }) } })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  expect(await screen.findByRole('region', { name: 'Tabela czasu zadań Jira' })).toHaveTextContent('PROJ-7')
  await userEvent.type(screen.getByLabelText('Zadanie Jira (klucz)'), 'proj-7')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  await waitFor(() => expect(requests.some(search => search.includes('issueKey=proj-7'))).toBe(true))
})

it('does not show Jira controls without a connected site', async () => {
  mockApi({ ...reportHandlers() })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  await screen.findByRole('region', { name: 'Tabela karty czasu' })
  expect(screen.queryByLabelText('Zadanie Jira (klucz)')).not.toBeInTheDocument()
  expect(screen.queryByRole('region', { name: 'Tabela czasu zadań Jira' })).not.toBeInTheDocument()
})
