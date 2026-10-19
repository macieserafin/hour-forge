import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TimeEntryHistory } from '../../src/pages/TimeEntryHistory'
import { renderApp } from '../support/render'
import { mockApi, json, entry, history } from '../support/api'

it('does not issue a request for an inverted date range', async () => {
  const fetcher = mockApi()
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  await screen.findByText(entry.description!)
  await userEvent.type(screen.getByLabelText('Data od'), '2026-10-20')
  await userEvent.type(screen.getByLabelText('Data do'), '2026-10-01')
  const count = fetcher.mock.calls.length
  await userEvent.click(screen.getByRole('button', { name: 'Filtruj' }))
  expect(screen.getByRole('alert')).toHaveTextContent('nie może być późniejsza')
  expect(fetcher).toHaveBeenCalledTimes(count)
})
it('refreshes after deleting an entry and retains a recoverable error on failed deletion', async () => {
  let removed = false
  let reject = true
  mockApi({ 'GET /api/time-entries': () => json(history(removed ? [] : [entry])),
    'DELETE /api/time-entries/entry': () => { if (reject) return json({}, 403); removed = true; return json(null, 204) } })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.click(modal.getByRole('button', { name: 'Usuń wpis' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Nie masz uprawnień')
  reject = false
  await userEvent.click(modal.getByRole('button', { name: 'Usuń wpis' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(await screen.findByText('Nie masz jeszcze zapisanych wpisów.')).toBeVisible()
})
it('keeps an existing duration above 24 hours but rejects a changed duration above the limit', async () => {
  const long = { ...entry, entryType: 'TIMER' as const, durationSeconds: 108_000, startedAt: '2026-10-18T00:00:00Z', endedAt: '2026-10-19T06:00:00Z' }
  let patched: Record<string, unknown> | null = null
  const fetcher = mockApi({ 'GET /api/time-entries': () => json(history([long])),
    'PATCH /api/time-entries/entry': (_url, options) => { patched = JSON.parse(String(options.body)); return json({}) } })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Godziny')).not.toHaveAttribute('max')
  await userEvent.clear(modal.getByLabelText('Minuty'))
  await userEvent.type(modal.getByLabelText('Minuty'), '1')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  expect(modal.getByRole('alert')).toHaveTextContent('najwyżej 24 godziny')
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'PATCH')).toBe(false)
  await userEvent.clear(modal.getByLabelText('Minuty'))
  await userEvent.type(modal.getByLabelText('Minuty'), '0')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await waitFor(() => expect(patched).toMatchObject({ durationSeconds: 108_000, workDate: long.workDate }))
})
it('retains edit values when another operation has removed the entry', async () => {
  mockApi({ 'PATCH /api/time-entries/entry': () => json({}, 404) })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" timezone="Europe/Warsaw" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveAttribute('maxlength', '2000')
  await userEvent.type(modal.getByLabelText('Opis pracy', { exact: false }), ' correction')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Wpis został usunięty')
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveValue('Original work correction')
})
