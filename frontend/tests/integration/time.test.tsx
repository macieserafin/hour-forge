import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, history, timer, entry, team } from '../support/api'
import { workDateRange } from '../../src/lib/time'

it('rejects zero manual time without posting and permits cancelling the form', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(modal.getByLabelText('Projekt'), 'project')
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveAttribute('maxlength', '2000')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  expect(modal.getByRole('alert')).toHaveTextContent('Podaj dodatni czas')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  await userEvent.click(modal.getByRole('button', { name: 'Anuluj' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('limits a manual entry to the team work date range and 24 hours without posting', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  const range = workDateRange(team.timezone)
  expect(modal.getByLabelText('Data pracy')).toHaveAttribute('min', range.min)
  expect(modal.getByLabelText('Data pracy')).toHaveAttribute('max', range.max)
  expect(modal.getByLabelText('Data pracy')).toHaveValue(range.max)
  await userEvent.selectOptions(modal.getByLabelText('Projekt'), 'project')
  await userEvent.clear(modal.getByLabelText('Godziny'))
  await userEvent.type(modal.getByLabelText('Godziny'), '24')
  await userEvent.clear(modal.getByLabelText('Minuty'))
  await userEvent.type(modal.getByLabelText('Minuty'), '1')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  expect(modal.getByRole('alert')).toHaveTextContent('najwyżej 24 godziny')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
})

it('explains a rejected long description of a manual entry', async () => {
  mockApi({ 'POST /api/time-entries': () => json({}, 400) })
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(modal.getByLabelText('Projekt'), 'project')
  await userEvent.type(modal.getByLabelText('Minuty'), '15')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('opis może mieć najwyżej 2000 znaków')
})

it('a former member can finish a paused timer but cannot resume it or see a cached project name', async () => {
  let active = true
  mockApi({ 'GET /api/teams': () => json([]),
    'GET /api/timer/active': () => active ? json({ ...timer, pausedAt: '2026-10-19T10:02:00Z' }) : json(null, 204),
    'POST /api/timer/stop': () => { active = false; return json({ ...timer, endedAt: '2026-10-19T10:02:00Z', durationSeconds: 120 }) },
  })
  const { client } = renderApp(<App />, '/time?team=team')
  client.setQueryData(['projects', 'actor', 'team', 'detail', 'project'], { id: 'project', name: 'Cached private project' })
  const panel = within(await screen.findByRole('region', { name: 'Aktywny timer' }))
  expect(panel.getByRole('button', { name: 'Wznów pracę' })).toBeDisabled()
  expect(panel.getByRole('button', { name: 'Zakończ pracę' })).toBeEnabled()
  expect(screen.queryByText('Cached private project')).not.toBeInTheDocument()
  await userEvent.click(panel.getByRole('button', { name: 'Zakończ pracę' }))
  expect(await screen.findByText(/Praca zakończona i zapisana/)).toBeVisible()
})
it('recovers a running timer after a start conflict', async () => {
  let running = false
  mockApi({ 'GET /api/timer/active': () => running ? json({ ...timer, startedAt: new Date(Date.now() - 120000).toISOString() }) : json(null, 204),
    'POST /api/timer/start': () => { running = true; return json({}, 409) }, 'GET /api/time-entries': () => json(history([])) })
  renderApp(<App />, '/time?team=team')
  const input = within(await screen.findByRole('region', { name: 'Rejestrowanie czasu w zespole' }))
  await userEvent.selectOptions(await input.findByLabelText('Projekt'), 'project')
  await userEvent.click(input.getByRole('button', { name: 'Uruchom timer' }))
  expect(await screen.findByRole('region', { name: 'Aktywny timer' })).toHaveTextContent('Website')
  expect(input.getByRole('button', { name: 'Uruchom timer' })).toBeDisabled()
})

it('reconciles manual writes and timer pause, resume and stop through query cache', async () => {
  let active: typeof timer | null = null
  let saved: typeof entry[] = []
  const running = { ...timer, startedAt: new Date(Date.now() - 120000).toISOString() }
  mockApi({ 'GET /api/timer/active': () => active ? json(active) : json(null, 204),
    'GET /api/time-entries': () => json(history(saved)),
    'POST /api/time-entries': () => { saved = [entry]; return json(entry, 201) },
    'POST /api/timer/start': () => { active = running; return json(active, 201) },
    'POST /api/timer/pause': () => { active = { ...running, pausedAt: new Date().toISOString() }; return json(active) },
    'POST /api/timer/resume': () => { active = running; return json(active) },
    'POST /api/timer/stop': () => { active = null; saved = [entry, { ...entry, id: 'stopped', description: 'Finished timer' }]; return json(saved[1]) } })
  renderApp(<App />, '/time?team=team')
  const input = within(await screen.findByRole('region', { name: 'Rejestrowanie czasu w zespole' }))
  await userEvent.selectOptions(await input.findByLabelText('Projekt'), 'project')
  expect(input.getByLabelText('Opis pracy', { exact: false })).toHaveAttribute('maxlength', '2000')
  await userEvent.click(input.getByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Minuty'), '15')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  expect(await screen.findByText('Original work')).toBeVisible()
  await userEvent.click(input.getByRole('button', { name: 'Uruchom timer' }))
  const activePanel = within(await screen.findByRole('region', { name: 'Aktywny timer' }))
  await userEvent.click(activePanel.getByRole('button', { name: 'Wstrzymaj timer' }))
  expect(await activePanel.findByText('Praca wstrzymana')).toBeVisible()
  await userEvent.click(activePanel.getByRole('button', { name: 'Wznów pracę' }))
  await activePanel.findByText('Timer działa')
  expect(activePanel.getByLabelText('Opis wykonanej pracy', { exact: false })).toHaveAttribute('maxlength', '2000')
  await userEvent.click(activePanel.getByRole('button', { name: 'Zakończ pracę' }))
  expect(await screen.findByText('Finished timer')).toBeVisible()
})
