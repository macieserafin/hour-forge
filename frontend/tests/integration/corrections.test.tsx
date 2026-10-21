import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TeamsPanel } from '../../src/pages/TeamsPanel'
import { renderApp } from '../support/render'
import { mockApi, json, team, user, entry, history } from '../support/api'
import type { TeamMember, TimeEntryHistoryItem } from '../../src/lib/api'

const owner: TeamMember = { userId: user.id, displayName: user.displayName, role: 'OWNER', joinedAt: team.createdAt, leftAt: null }
const worker: TeamMember = { ...owner, userId: 'worker', displayName: 'Anna Kowalska', role: 'MEMBER' }
const membersPage = { content: [owner, worker], page: 0, size: 20, totalElements: 2, totalPages: 1 }
const memberEntries = 'GET /api/teams/team/members/worker/time-entries'
const panel = () => renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')
const open = async () => {
  await userEvent.click(await screen.findByRole('button', { name: `Korekty czasu: ${worker.displayName}` }))
  return await screen.findByRole('dialog')
}

it('lets a manager list, add, correct and delete a members entries, marking who corrected them', async () => {
  let items: TimeEntryHistoryItem[] = [{ ...entry, id: 'e1' }]
  const sent: unknown[] = []
  const fetcher = mockApi({ [`GET /api/teams/team/members`]: () => json(membersPage),
    [memberEntries]: () => json(history(items)),
    'POST /api/teams/team/members/worker/time-entries': (_url, options) => { sent.push(JSON.parse(String(options.body)))
      items = [{ ...entry, id: 'e2', durationSeconds: 1800, description: 'Added' }, ...items]; return json({}, 201) },
    'PATCH /api/teams/team/time-entries/e1': (_url, options) => { sent.push(JSON.parse(String(options.body)))
      items = items.map(item => item.id === 'e1' ? { ...item, durationSeconds: 1200, editedAt: '2026-10-20T10:00:00Z', editedByName: user.displayName } : item); return json({}) },
    'DELETE /api/teams/team/time-entries/e2': () => { items = items.filter(item => item.id !== 'e2'); return json(null, 204) } })
  panel()
  let dialog = within(await open())
  expect(await dialog.findByText('Original work')).toBeVisible()

  await userEvent.click(dialog.getByRole('button', { name: 'Dodaj wpis' }))
  dialog = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(dialog.getByLabelText('Projekt'), 'project')
  await userEvent.type(dialog.getByLabelText('Data pracy'), '2026-10-19')
  await userEvent.clear(dialog.getByLabelText('Minuty'))
  await userEvent.type(dialog.getByLabelText('Minuty'), '30')
  await userEvent.type(dialog.getByLabelText(/Opis pracy/), 'Added')
  await userEvent.click(dialog.getByRole('button', { name: 'Dodaj wpis' }))
  expect(await screen.findByText('Added')).toBeVisible()
  expect(sent[0]).toEqual({ projectId: 'project', workDate: '2026-10-19', durationSeconds: 1800, description: 'Added' })

  await userEvent.click(screen.getAllByRole('button', { name: /Popraw wpis: Website 2026-10-19/ })[1]!)
  dialog = within(screen.getByRole('dialog'))
  await userEvent.clear(dialog.getByLabelText('Minuty'))
  await userEvent.type(dialog.getByLabelText('Minuty'), '20')
  await userEvent.click(dialog.getByRole('button', { name: 'Zapisz korektę' }))
  expect(await screen.findByText(`poprawił(a): ${user.displayName}`)).toBeVisible()
  expect(sent[1]).toMatchObject({ durationSeconds: 1200 })

  await userEvent.click(screen.getAllByRole('button', { name: /Usuń wpis: Website/ })[0]!)
  expect(screen.getByText(/Trwale usunąć wpis osoby/)).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Usuń wpis' }))
  await waitFor(() => expect(screen.queryByText('Added')).not.toBeInTheDocument())
  expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1)
})

it('shows the server reason when a correction is refused and keeps the form open', async () => {
  mockApi({ 'GET /api/teams/team/members': () => json(membersPage), [memberEntries]: () => json(history([entry])),
    'PATCH /api/teams/team/time-entries/entry': () => json({}, 409) })
  panel()
  await open()
  await userEvent.click(await screen.findByRole('button', { name: /Popraw wpis/ }))
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz korektę' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Zespół jest zarchiwizowany')
  expect(screen.getByRole('button', { name: 'Zapisz korektę' })).toBeEnabled()
})

it('offers no corrections to a plain member and none to oneself', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]), 'GET /api/teams/team/members': () => json(membersPage) })
  panel()
  await screen.findByRole('region', { name: 'Lista członków zespołu' })
  expect(screen.queryByRole('button', { name: /Korekty czasu/ })).not.toBeInTheDocument()
})

it('does not offer corrections for ones own row', async () => {
  mockApi({ 'GET /api/teams/team/members': () => json(membersPage) })
  panel()
  await screen.findByRole('button', { name: `Korekty czasu: ${worker.displayName}` })
  expect(screen.queryByRole('button', { name: `Korekty czasu: ${owner.displayName}` })).not.toBeInTheDocument()
})
