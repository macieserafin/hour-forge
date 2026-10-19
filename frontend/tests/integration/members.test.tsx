import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TeamsPanel } from '../../src/pages/TeamsPanel'
import { renderApp } from '../support/render'
import { mockApi, json, team, user } from '../support/api'
import type { TeamMember } from '../../src/lib/api'

const owner: TeamMember = { userId: user.id, displayName: user.displayName, role: 'OWNER', joinedAt: team.createdAt, leftAt: null }
const worker: TeamMember = { ...owner, userId: 'worker', displayName: 'Anna Kowalska', role: 'MEMBER' }
const former: TeamMember = { ...worker, userId: 'former', displayName: 'Była osoba', leftAt: team.createdAt }
const membersPage = (content: TeamMember[], page = 0, total = content.length) => ({ content, page, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) })
const listPath = 'GET /api/teams/team/members'
const panel = () => renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')

it('shows active people and lets only the owner include historical memberships', async () => {
  const fetcher = mockApi({ [listPath]: url => json(membersPage(url.searchParams.get('includeFormer') === 'true' ? [owner, worker, former] : [owner, worker])) })
  panel()
  const list = await screen.findByRole('region', { name: 'Lista członków zespołu' })
  expect(list).toHaveTextContent(worker.displayName)
  expect(list).not.toHaveTextContent(former.displayName)
  expect(screen.queryByRole('button', { name: 'Opuść zespół' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: `Usuń z zespołu: ${owner.displayName}` })).not.toBeInTheDocument()
  await userEvent.click(screen.getByLabelText('Pokaż również byłych członków'))
  const historicalList = await screen.findByRole('region', { name: 'Lista członków zespołu' })
  expect(await within(historicalList).findByText(former.displayName)).toBeVisible()
  expect(historicalList).toHaveTextContent('Były członek')
  expect(screen.queryByRole('button', { name: `Usuń z zespołu: ${former.displayName}` })).not.toBeInTheDocument()
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('includeFormer=true'))).toBe(true)
})

it('requires confirmation for the selected person and preserves cancellation', async () => {
  let removed = false
  const fetcher = mockApi({ [listPath]: () => json(membersPage(removed ? [owner] : [owner, worker])),
    'DELETE /api/teams/team/members/worker': () => { removed = true; return json(null, 204) } })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: `Usuń z zespołu: ${worker.displayName}` }))
  let modal = within(screen.getByRole('dialog'))
  expect(modal.getByText(worker.displayName)).toBeVisible()
  expect(modal.getByText(team.name)).toBeVisible()
  await userEvent.click(modal.getByRole('button', { name: 'Anuluj' }))
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false)
  await userEvent.click(screen.getByRole('button', { name: `Usuń z zespołu: ${worker.displayName}` }))
  modal = within(screen.getByRole('dialog'))
  await userEvent.click(modal.getByRole('button', { name: 'Potwierdź usunięcie' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(screen.getByRole('region', { name: 'Lista członków zespołu' })).not.toHaveTextContent(worker.displayName)
  expect(screen.getByText(new RegExp(`Zakończono dostęp osoby ${worker.displayName}`))).toBeVisible()
  expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1)
})

it('transfers ownership to an active member after confirmation', async () => {
  let transferred = false
  let sent: unknown = null
  const fetcher = mockApi({ 'GET /api/teams': () => json([{ ...team, role: transferred ? 'MEMBER' : 'OWNER' }]),
    [listPath]: () => json(membersPage(transferred ? [{ ...worker, role: 'OWNER' }, { ...owner, role: 'MEMBER' }] : [owner, worker])),
    'POST /api/teams/team/transfer-ownership': (_url, options) => { sent = JSON.parse(String(options.body)); transferred = true; return json(null, 204) } })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: `Przekaż własność: ${worker.displayName}` }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByText(worker.displayName)).toBeVisible()
  await userEvent.click(modal.getByRole('button', { name: 'Przekaż własność' }))
  expect(await screen.findByText('Twoja rola: Członek')).toBeVisible()
  expect(sent).toEqual({ userId: worker.userId })
  expect(screen.queryByRole('button', { name: /Przekaż własność/ })).not.toBeInTheDocument()
  expect(fetcher.mock.calls.filter(([path]) => String(path).endsWith('/transfer-ownership'))).toHaveLength(1)
})

it('a member can leave after confirmation and loses the team view without losing other teams', async () => {
  const another = { ...team, id: 'other', name: 'Other team', role: 'MEMBER' as const }
  let left = false
  mockApi({ 'GET /api/teams': () => json(left ? [another] : [{ ...team, role: 'MEMBER' }, another]),
    [listPath]: () => left ? json({}, 404) : json(membersPage([{ ...owner, role: 'MEMBER' }, worker])),
    'POST /api/teams/team/leave': () => { left = true; return json(null, 204) },
    'GET /api/teams/other/members': () => json(membersPage([{ ...owner, role: 'MEMBER' }])),
  })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: 'Opuść zespół' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByText(team.name)).toBeVisible()
  expect(screen.queryByLabelText('Pokaż również byłych członków')).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Usuń z zespołu:/ })).not.toBeInTheDocument()
  await userEvent.click(modal.getByRole('button', { name: 'Potwierdź opuszczenie' }))
  expect(await screen.findByText(`Opuszczono zespół ${team.name}. Historia Twojej pracy została zachowana.`)).toBeVisible()
  expect(screen.getByRole('region', { name: 'Wybrany zespół' })).toHaveTextContent(another.name)
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('reconciles a committed removal after the response was lost without repeating the write', async () => {
  let removed = false
  const fetcher = mockApi({ [listPath]: () => json(membersPage(removed ? [owner] : [owner, worker])),
    'DELETE /api/teams/team/members/worker': () => { removed = true; throw new TypeError('Lost response') } })
  const { client } = panel()
  client.setQueryData(['dashboard', user.id, team.id, 'reports', 'summary'], { totals: { durationSeconds: 1 } })
  await userEvent.click(await screen.findByRole('button', { name: `Usuń z zespołu: ${worker.displayName}` }))
  await userEvent.click(screen.getByRole('button', { name: 'Potwierdź usunięcie' }))
  expect(await within(screen.getByRole('dialog')).findByRole('alert')).toHaveTextContent('Nie udało się połączyć')
  expect(screen.getByRole('region', { name: 'Lista członków zespołu' })).not.toHaveTextContent(worker.displayName)
  expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1)
  expect(client.getQueryState(['dashboard', user.id, team.id, 'reports', 'summary'])?.isInvalidated).toBe(true)
})

it('supports retry and pagination, resetting the page when former memberships are included', async () => {
  let failed = true
  const fetcher = mockApi({ [listPath]: url => failed ? json({}, 400) : json(membersPage([Number(url.searchParams.get('page')) === 0 ? owner : worker], Number(url.searchParams.get('page')), 21)) })
  panel()
  expect(await screen.findByRole('alert')).toHaveTextContent('Sprawdź wybraną stronę')
  failed = false
  await userEvent.click(screen.getByRole('button', { name: 'Wczytaj członków ponownie' }))
  const pages = await screen.findByRole('navigation', { name: 'Strony członków zespołu' })
  await userEvent.click(within(pages).getByRole('button', { name: 'Następna' }))
  expect(await screen.findByText('Strona 2 z 2')).toBeVisible()
  await userEvent.click(screen.getByLabelText('Pokaż również byłych członków'))
  expect(await screen.findByText('Strona 1 z 2')).toBeVisible()
  expect(fetcher.mock.calls.some(([path]) => String(path).includes('includeFormer=true&page=0'))).toBe(true)
})

it('does not silently select another team when access to the requested team has ended', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, id: 'other' }]) })
  panel()
  expect(await screen.findByRole('alert')).toHaveTextContent('Ten zespół jest niedostępny')
  expect(screen.queryByRole('region', { name: 'Członkowie zespołu' })).not.toBeInTheDocument()
})
