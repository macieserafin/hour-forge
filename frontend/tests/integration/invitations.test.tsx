import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'

it.each([[404, 'Nie znaleziono zaproszenia'], [409, 'Limit użyć'], [410, 'Zaproszenie wygasło']])
  ('shows an actionable invitation HTTP %s error', async (status, message) => {
    const token = 'a'.repeat(43)
    mockApi({ [`POST /api/invites/${token}/accept`]: () => json({}, Number(status)) })
    renderApp(<App />, `/invite/${token}`)
    await userEvent.click(await screen.findByRole('button', { name: 'Dołącz do zespołu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(String(message))
  })

it('updates membership after accepting an invitation', async () => {
  const token = 'a'.repeat(43)
  mockApi({ [`POST /api/invites/${token}/accept`]: () => json({ ...team, role: 'MEMBER' }) })
  renderApp(<App />, `/invite/${token}`)
  await userEvent.click(await screen.findByRole('button', { name: 'Dołącz do zespołu' }))
  expect(await screen.findByRole('heading', { name: 'Masz dostęp do zespołu' })).toBeVisible()
})
