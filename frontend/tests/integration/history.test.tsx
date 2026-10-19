import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TimeEntryHistory } from '../../src/pages/TimeEntryHistory'
import { renderApp } from '../support/render'
import { mockApi, json, entry, history } from '../support/api'

it('does not issue a request for an inverted date range', async () => {
  const fetcher = mockApi()
  renderApp(<TimeEntryHistory userId="actor" teamId="team" />)
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
  renderApp(<TimeEntryHistory userId="actor" teamId="team" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Usuń' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.click(modal.getByRole('button', { name: 'Usuń wpis' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Nie masz uprawnień')
  reject = false
  await userEvent.click(modal.getByRole('button', { name: 'Usuń wpis' }))
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  expect(await screen.findByText('Nie masz jeszcze zapisanych wpisów.')).toBeVisible()
})
it('retains edit values when another operation has removed the entry', async () => {
  mockApi({ 'PATCH /api/time-entries/entry': () => json({}, 404) })
  renderApp(<TimeEntryHistory userId="actor" teamId="team" />)
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Opis pracy', { exact: false }), ' correction')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Wpis został usunięty')
  expect(modal.getByLabelText('Opis pracy', { exact: false })).toHaveValue('Original work correction')
})
