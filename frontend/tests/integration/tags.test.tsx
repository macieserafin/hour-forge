import { expect, it } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import type { Tag } from '../../src/lib/api'
import { TeamsPanel } from '../../src/pages/TeamsPanel'
import { OwnerDashboard } from '../../src/pages/OwnerDashboard'
import { renderApp } from '../support/render'
import { entry, history, json, mockApi, team, user } from '../support/api'
import { reportBucket, reportEntry, reportHandlers, reportPage } from '../support/reports'

const review: Tag = { id: 'review', teamId: 'team', name: 'Przegląd kodu', color: 'TEAL', archivedAt: null }
const old: Tag = { id: 'old', teamId: 'team', name: 'Stary', color: null, archivedAt: '2026-10-01T00:00:00Z' }
const tagsPath = 'GET /api/teams/team/tags'

it('lets a manager add, rename, archive and restore tags', async () => {
  let items: Tag[] = [review]
  const sent: unknown[] = []
  mockApi({ [tagsPath]: () => json(items),
    'POST /api/teams/team/tags': (_url, options) => { const body = JSON.parse(String(options.body)); sent.push(body); items = [...items, { ...review, id: 'new', ...body }]; return json(items.at(-1), 201) },
    'PUT /api/teams/team/tags/review': (_url, options) => { const body = JSON.parse(String(options.body)); sent.push(body); items = items.map(tag => tag.id === 'review' ? { ...tag, ...body } : tag); return json(items[0]) },
    'POST /api/teams/team/tags/review/archive': () => { items = items.map(tag => tag.id === 'review' ? { ...tag, archivedAt: '2026-10-20T00:00:00Z' } : tag); return json(items[0]) },
    'POST /api/teams/team/tags/review/restore': () => { items = items.map(tag => tag.id === 'review' ? { ...tag, archivedAt: null } : tag); return json(items[0]) } })
  renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')
  const list = await screen.findByRole('list', { name: 'Lista tagów' })
  expect(list).toHaveTextContent('Przegląd kodu')

  await userEvent.click(screen.getByRole('button', { name: 'Dodaj tag' }))
  let dialog = within(screen.getByRole('dialog'))
  await userEvent.type(dialog.getByLabelText('Nazwa tagu'), 'Spotkanie')
  await userEvent.click(dialog.getByLabelText('Zielony'))
  await userEvent.click(dialog.getByRole('button', { name: 'Zapisz tag' }))
  expect(await screen.findByText('Spotkanie')).toBeVisible()
  expect(sent[0]).toEqual({ name: 'Spotkanie', color: 'GREEN' })

  await userEvent.click(screen.getByRole('button', { name: 'Edytuj tag: Przegląd kodu' }))
  dialog = within(screen.getByRole('dialog'))
  await userEvent.clear(dialog.getByLabelText('Nazwa tagu'))
  await userEvent.type(dialog.getByLabelText('Nazwa tagu'), 'Review')
  await userEvent.click(dialog.getByRole('button', { name: 'Zapisz tag' }))
  expect(await screen.findByText('Review')).toBeVisible()

  await userEvent.click(screen.getByRole('button', { name: 'Archiwizuj tag: Review' }))
  expect(await screen.findByRole('button', { name: 'Przywróć tag: Review' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Przywróć tag: Review' }))
  expect(await screen.findByRole('button', { name: 'Archiwizuj tag: Review' })).toBeVisible()
})

it('shows the server reason when a tag name is taken', async () => {
  mockApi({ [tagsPath]: () => json([]), 'POST /api/teams/team/tags': () => json({}, 409) })
  renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj tag' }))
  await userEvent.type(screen.getByLabelText('Nazwa tagu'), 'Duplikat')
  await userEvent.click(screen.getByRole('button', { name: 'Zapisz tag' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Tag o tej nazwie już istnieje')
})

it('offers no tag management to a plain member', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]), [tagsPath]: () => json([review]) })
  renderApp(<TeamsPanel userId={user.id} />, '/teams?team=team')
  await screen.findByRole('region', { name: 'Lista członków zespołu' })
  expect(screen.queryByRole('button', { name: 'Dodaj tag' })).not.toBeInTheDocument()
})

it('puts the chosen tag on a manual entry', async () => {
  let posted: Record<string, unknown> = {}
  mockApi({ [tagsPath]: () => json([review]),
    'POST /api/time-entries': (_url, options) => { posted = JSON.parse(String(options.body)); return json({ ...entry, userId: 'actor', createdAt: '', updatedAt: '', pausedAt: null, pausedDurationSeconds: 0 }, 201) } })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(await screen.findByRole('dialog', { name: 'Dodaj wpis ręczny' }))
  await userEvent.selectOptions(modal.getByLabelText('Projekt'), 'project')
  await userEvent.selectOptions(await modal.findByLabelText(/Tag/), 'review')
  await userEvent.clear(modal.getByLabelText('Minuty'))
  await userEvent.type(modal.getByLabelText('Minuty'), '15')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  await waitFor(() => expect(posted).toMatchObject({ tagId: 'review', durationSeconds: 900 }))
})

it('does not show a tag choice for a team without tags', async () => {
  mockApi({ 'GET /api/time-entries': () => json(history()) })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(await screen.findByRole('dialog', { name: 'Dodaj wpis ręczny' }))
  expect(modal.queryByLabelText(/^Tag/)).not.toBeInTheDocument()
})

it('filters and groups the team report by tag and shows the tag next to entries', async () => {
  const requests: string[] = []
  mockApi({ ...reportHandlers(), [tagsPath]: () => json([review, old]),
    'GET /api/teams/team/reports/entries': () => json(reportPage([{ ...reportEntry, tagName: 'Przegląd kodu' }])),
    'GET /api/teams/team/reports/breakdown': url => { requests.push(url.search); return json({ ...reportPage([{ ...reportBucket, tagId: 'review', tagName: 'Przegląd kodu', durationSeconds: 900, entryCount: 1 }]), groupBy: url.searchParams.get('groupBy') }) } })
  renderApp(<OwnerDashboard userId="actor" team={team} />)
  expect(await screen.findByRole('region', { name: 'Tabela czasu tagów' })).toHaveTextContent('Przegląd kodu')
  expect(await screen.findAllByText('Przegląd kodu')).not.toHaveLength(0)
  await userEvent.selectOptions(screen.getByLabelText('Tag'), 'review')
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj raport' }))
  await waitFor(() => expect(requests.some(search => search.includes('tagIds=review'))).toBe(true))
})
