import { it, expect } from 'vitest'
import { screen, within, waitFor, renderHook, act } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { PersonalDashboard } from '../../src/pages/PersonalDashboard'
import { useManualTimeEntry, useUpdateTimeEntry, useDeleteTimeEntry, useStopTimer } from '../../src/lib/time'
import { renderApp } from '../support/render'
import { mockApi, json, team, timer } from '../support/api'

it('shows saved totals and keeps the global paused timer separate from team selection', async () => {
  mockApi({ 'GET /api/teams': () => json([team, { ...team, id: 'other', name: 'Other team' }]),
    'GET /api/timer/active': () => json({ ...timer, pausedAt: '2026-10-19T10:02:00Z', pausedDurationSeconds: 30 }) })
  renderApp(<PersonalDashboard userId="actor" />)
  expect(await within(await screen.findByRole('region', { name: 'Czas dzisiaj' })).findByText('00:15:00')).toBeVisible()
  expect(within(screen.getByRole('region', { name: 'Czas w tym tygodniu' })).getByText('01:00:00')).toBeVisible()
  const active = within(await screen.findByRole('region', { name: 'Aktywny timer na pulpicie' }))
  expect(active.getByRole('timer')).toHaveTextContent('00:01:30')
  await userEvent.selectOptions(screen.getByLabelText('Zespół na pulpicie'), 'other')
  expect(active.getByRole('link', { name: 'Przejdź do timera' })).toHaveAttribute('href', '/time?team=team')
  expect(active.getByText('Studio')).toBeVisible()
})

it('handles no teams and rejects an inaccessible team without choosing another', async () => {
  mockApi()
  renderApp(<PersonalDashboard userId="actor" />, '/dashboard?team=missing')
  expect(await screen.findByRole('alert')).toHaveTextContent('Ten zespół nie istnieje')
  expect(screen.queryByRole('region', { name: 'Czas dzisiaj' })).not.toBeInTheDocument()
})

it('shows onboarding when the user has no teams', async () => {
  mockApi({ 'GET /api/teams': () => json([]) })
  renderApp(<PersonalDashboard userId="actor" />)
  expect(await screen.findByRole('link', { name: 'Przejdź do zespołów' })).toHaveAttribute('href', '/teams')
})

it('keeps an own timer visible after departure without exposing the cached project name', async () => {
  mockApi({ 'GET /api/teams': () => json([]), 'GET /api/timer/active': () => json(timer) })
  const { client } = renderApp(<PersonalDashboard userId="actor" />)
  client.setQueryData(['projects', 'actor', 'team', 'detail', 'project'], { id: 'project', name: 'Cached private project' })
  const active = await screen.findByRole('region', { name: 'Aktywny timer na pulpicie' })
  expect(active).toHaveTextContent('Projekt niedostępny')
  expect(screen.queryByText('Cached private project')).not.toBeInTheDocument()
})

it('allows retrying a failed summary', async () => {
  let failed = true
  mockApi({ 'GET /api/dashboard/summary': () => failed ? json({}, 404) : json({ teamId: 'team', timezone: 'UTC',
    today: '2026-10-19', weekStart: '2026-10-19', weekEnd: '2026-10-25', todaySeconds: 0, weekSeconds: 0 }) })
  renderApp(<PersonalDashboard userId="actor" />)
  expect(await screen.findByRole('alert')).toHaveTextContent('Nie znaleziono zespołu')
  failed = false
  await userEvent.click(screen.getByRole('button', { name: 'Spróbuj ponownie' }))
  expect(await screen.findByRole('region', { name: 'Czas dzisiaj' })).toHaveTextContent('00:00:00')
})

const writes = [
  ['manual', useManualTimeEntry, { teamId: 'team', projectId: 'project', workDate: '2026-10-19', durationSeconds: 60, description: null }, 'POST /api/time-entries'],
  ['update', useUpdateTimeEntry, { id: 'entry', projectId: 'project', workDate: '2026-10-19', durationSeconds: 60, description: null }, 'PATCH /api/time-entries/entry'],
  ['delete', useDeleteTimeEntry, 'entry', 'DELETE /api/time-entries/entry'],
  ['stop', useStopTimer, { entryId: 'entry', description: null }, 'POST /api/timer/stop'],
] as const

it.each(writes)('invalidates cached dashboard summaries after %s', async (_name, hook, input, path) => {
  mockApi({ [path]: () => json(timer) })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  client.setQueryData(['dashboard', 'actor', 'team'], { todaySeconds: 0 })
  client.setQueryData(['dashboard', 'actor', 'team', 'reports', 'summary'], { totals: { durationSeconds: 0 } })
  const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  const { result, unmount } = renderHook(() => hook('actor'), { wrapper })
  // Each row couples the hook with its corresponding input shape.
  await act(async () => { await (result.current.mutateAsync as (data: unknown) => Promise<unknown>)(input) })
  await waitFor(() => expect(client.getQueryState(['dashboard', 'actor', 'team'])?.isInvalidated).toBe(true))
  expect(client.getQueryState(['dashboard', 'actor', 'team', 'reports', 'summary'])?.isInvalidated).toBe(true)
  unmount()
  client.clear()
})
