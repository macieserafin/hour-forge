import { expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { json, mockApi } from '../support/api'

const token = 'a'.repeat(43)

it('switches the kind in the link off without signing in', async () => {
  let sent: Record<string, unknown> = {}
  mockApi({ 'GET /api/me': () => json({}, 401), 'POST /api/notifications/unsubscribe': (_url, options) => { sent = JSON.parse(String(options.body)); return json(null, 204) } })
  renderApp(<App />, `/unsubscribe/${token}?kind=WEEKLY_REPORT`)
  expect(await screen.findByText('Nie będziemy już wysyłać cotygodniowych raportów zespołu.')).toBeVisible()
  expect(sent).toEqual({ token, kind: 'WEEKLY_REPORT' })
})

it('says so when the link is no longer valid or incomplete', async () => {
  mockApi({ 'GET /api/me': () => json({}, 401), 'POST /api/notifications/unsubscribe': () => json({}, 404) })
  const view = renderApp(<App />, `/unsubscribe/${token}?kind=TEAM_EVENTS`)
  expect(await screen.findByRole('alert')).toHaveTextContent('nie jest już aktualny')
  view.unmount()
  renderApp(<App />, `/unsubscribe/${token}`)
  expect(await screen.findByRole('alert')).toHaveTextContent('niekompletny')
})
