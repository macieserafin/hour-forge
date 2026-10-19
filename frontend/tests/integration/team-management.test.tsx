import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TeamsPanel } from '../../src/pages/TeamsPanel'
import { renderApp } from '../support/render'
import { mockApi, json, team, user } from '../support/api'

const panel = () => renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')
const summary = (activeTimers: boolean) => () => json({ activeMembers: 2, projects: 1, timeEntries: 5, activeTimers })
const openSettings = async () => {
  await userEvent.click(await screen.findByRole('button', { name: 'Ustawienia zespołu' }))
  return within(screen.getByRole('dialog'))
}

it('edits the team name and time zone from the settings', async () => {
  let current = { ...team }
  let sent: unknown = null
  mockApi({ 'GET /api/teams': () => json([current]),
    'PATCH /api/teams/team': (_url, options) => { sent = JSON.parse(String(options.body)); current = { ...current, ...(sent as object) }; return json(current) } })
  panel()
  const modal = await openSettings()
  await userEvent.clear(modal.getByLabelText('Nazwa zespołu'))
  await userEvent.type(modal.getByLabelText('Nazwa zespołu'), 'Nowa nazwa')
  await userEvent.selectOptions(modal.getByLabelText('Strefa czasowa'), 'UTC')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Zapisano ustawienia zespołu Nowa nazwa.')
  expect(sent).toEqual({ name: 'Nowa nazwa', timezone: 'UTC' })
})

it('archives a team only after showing the irreversible effects', async () => {
  let current = { ...team }
  const fetcher = mockApi({ 'GET /api/teams': () => json([current]),
    'POST /api/teams/team/archive': () => { current = { ...current, archivedAt: '2026-10-19T12:00:00Z' }; return json(current) } })
  panel()
  let modal = await openSettings()
  await userEvent.click(modal.getByRole('button', { name: 'Zarchiwizuj zespół' }))
  modal = within(screen.getByRole('dialog'))
  expect(modal.getByText('Tej operacji nie można cofnąć.')).toBeVisible()
  await userEvent.click(modal.getByRole('button', { name: 'Wróć' }))
  expect(fetcher.mock.calls.some(([path]) => String(path).endsWith('/archive'))).toBe(false)
  await userEvent.click(modal.getByRole('button', { name: 'Zarchiwizuj zespół' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Zarchiwizuj na stałe' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Zespół Studio został zarchiwizowany.')
  expect(await screen.findByText('Zarchiwizowany')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Zaproś do zespołu' })).not.toBeInTheDocument()
  const settings = await openSettings()
  expect(settings.getByLabelText('Nazwa zespołu')).toBeDisabled()
  expect(settings.queryByRole('button', { name: 'Zarchiwizuj zespół' })).not.toBeInTheDocument()
})

it('deletes a team only after typing its exact name', async () => {
  let teams = [team]
  const fetcher = mockApi({ 'GET /api/teams': () => json(teams), 'GET /api/teams/team/summary': summary(false),
    'DELETE /api/teams/team': () => { teams = []; return json(null, 204) } })
  panel()
  let modal = await openSettings()
  await userEvent.click(modal.getByRole('button', { name: 'Usuń zespół' }))
  modal = within(screen.getByRole('dialog'))
  expect(await modal.findByText('5')).toBeVisible()
  const remove = modal.getByRole('button', { name: 'Usuń zespół na zawsze' })
  expect(remove).toBeDisabled()
  await userEvent.type(modal.getByLabelText('Wpisz nazwę zespołu, aby potwierdzić'), 'studio')
  expect(remove).toBeDisabled()
  await userEvent.clear(modal.getByLabelText('Wpisz nazwę zespołu, aby potwierdzić'))
  await userEvent.type(modal.getByLabelText('Wpisz nazwę zespołu, aby potwierdzić'), team.name)
  await userEvent.click(remove)
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('status')).toHaveTextContent('Usunięto zespół Studio.')
  expect(await screen.findByText('Nie należysz jeszcze do żadnego zespołu.')).toBeVisible()
  expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1)
})

it('blocks deletion while timers are running in the team', async () => {
  mockApi({ 'GET /api/teams/team/summary': summary(true) })
  panel()
  await userEvent.click((await openSettings()).getByRole('button', { name: 'Usuń zespół' }))
  const modal = within(screen.getByRole('dialog'))
  expect(await modal.findByRole('alert')).toHaveTextContent('działają timery')
  await userEvent.type(modal.getByLabelText('Wpisz nazwę zespołu, aby potwierdzić'), team.name)
  expect(modal.getByRole('button', { name: 'Usuń zespół na zawsze' })).toBeDisabled()
})

it('does not offer team settings to a member', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]) })
  panel()
  expect(await screen.findByText('Twoja rola: Członek')).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Ustawienia zespołu' })).not.toBeInTheDocument()
})
