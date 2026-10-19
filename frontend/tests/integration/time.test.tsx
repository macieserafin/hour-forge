import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, history, timer, entry } from '../support/api'

it('rejects zero manual time without posting and permits cancelling the form', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/time?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Dodaj wpis ręczny' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.selectOptions(modal.getByLabelText('Projekt'), 'project')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz wpis' }))
  expect(modal.getByRole('alert')).toHaveTextContent('Podaj dodatni czas')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'POST')).toBe(false)
  await userEvent.click(modal.getByRole('button', { name: 'Anuluj' }))
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
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
  await userEvent.click(activePanel.getByRole('button', { name: 'Zakończ pracę' }))
  expect(await screen.findByText('Finished timer')).toBeVisible()
})
