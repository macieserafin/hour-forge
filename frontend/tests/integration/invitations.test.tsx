import { it, expect } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'

const token = 'a'.repeat(43)
const preview = (status: string) => () => json({ teamId: team.id, teamName: team.name, status, expiresAt: '2026-10-26T10:00:00Z' })

it.each([[409, 'Limit użyć'], [410, 'Zaproszenie wygasło']])
  ('shows an actionable error when accepting fails with HTTP %s', async (status, message) => {
    mockApi({ [`GET /api/invites/${token}`]: preview('ACTIVE'), [`POST /api/invites/${token}/accept`]: () => json({}, Number(status)) })
    renderApp(<App />, `/invite/${token}`)
    await userEvent.click(await screen.findByRole('button', { name: 'Dołącz do zespołu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(String(message))
  })

it('names the team before joining and updates membership after accepting', async () => {
  mockApi({ [`GET /api/invites/${token}`]: preview('ACTIVE'), [`POST /api/invites/${token}/accept`]: () => json({ ...team, role: 'MEMBER' }) })
  renderApp(<App />, `/invite/${token}`)
  expect(await screen.findByText(team.name)).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Dołącz do zespołu' }))
  expect(await screen.findByRole('heading', { name: 'Masz dostęp do zespołu' })).toBeVisible()
})

it('explains a missing invitation without offering to join', async () => {
  mockApi({ [`GET /api/invites/${token}`]: () => json({}, 404) })
  renderApp(<App />, `/invite/${token}`)
  expect(await screen.findByRole('alert')).toHaveTextContent('Nie znaleziono zaproszenia')
  expect(screen.queryByRole('button', { name: 'Dołącz do zespołu' })).not.toBeInTheDocument()
})

it.each([['EXPIRED', 'wygasło'], ['REVOKED', 'unieważnione'], ['EXHAUSTED', 'Limit użyć'], ['TEAM_ARCHIVED', 'zarchiwizowany']])
  ('explains an unusable %s invitation without offering to join', async (status, message) => {
    mockApi({ [`GET /api/invites/${token}`]: preview(status) })
    renderApp(<App />, `/invite/${token}`)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByRole('button', { name: 'Dołącz do zespołu' })).not.toBeInTheDocument()
  })

it('links an existing member straight to the team', async () => {
  mockApi({ [`GET /api/invites/${token}`]: preview('ALREADY_MEMBER') })
  renderApp(<App />, `/invite/${token}`)
  expect(await screen.findByRole('link', { name: /Przejdź do zespołu/ })).toHaveAttribute('href', `/teams?team=${team.id}`)
})
