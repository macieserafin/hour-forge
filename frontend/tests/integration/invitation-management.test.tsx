import { it, expect } from 'vitest'
import { screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { TeamInvitationsPanel } from '../../src/pages/TeamInvitationsPanel'
import { renderApp } from '../support/render'
import { mockApi, json, team } from '../support/api'
import type { InvitationSummary } from '../../src/lib/api'

const invitation: InvitationSummary = { id: 'invite', createdAt: '2026-10-19T10:00:00Z', expiresAt: '2026-10-26T10:00:00Z',
  maxUses: 2, usedCount: 0, revokedAt: null, status: 'ACTIVE' }
const result = (content = [invitation], page = 0, totalPages = 1) => ({ content, page, size: 20, totalElements: content.length, totalPages })
const panel = (archived = false) => renderApp(<TeamInvitationsPanel userId="actor" team={archived ? { ...team, archivedAt: team.createdAt } : team} />)

it('confirms revocation, preserves cancellation and refreshes the list after a write', async () => {
  let revoked = false
  const fetcher = mockApi({ 'GET /api/teams/team/invites': () => json(result([{ ...invitation, status: revoked ? 'REVOKED' : 'ACTIVE' }])),
    'DELETE /api/teams/team/invites/invite': () => { revoked = true; return json(null, 204) } })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: 'Unieważnij' }))
  let dialog = within(screen.getByRole('dialog'))
  expect(dialog.getByText(team.name)).toBeVisible()
  await userEvent.click(dialog.getByRole('button', { name: 'Anuluj' }))
  expect(fetcher.mock.calls.some(([, options]) => options?.method === 'DELETE')).toBe(false)
  await userEvent.click(screen.getByRole('button', { name: 'Unieważnij' }))
  dialog = within(screen.getByRole('dialog'))
  await userEvent.click(dialog.getByRole('button', { name: 'Unieważnij zaproszenie' }))
  expect(await screen.findByRole('cell', { name: 'Unieważnione' })).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Unieważnij' })).not.toBeInTheDocument()
  const write = fetcher.mock.calls.find(([, options]) => options?.method === 'DELETE')!
  expect(write[1]?.headers).toEqual({ 'X-CSRF-TOKEN': 'csrf' })
})

it('paginates invitations and hides cached data after losing owner access', async () => {
  let forbidden = false
  mockApi({ 'GET /api/teams/team/invites': url => forbidden ? json({}, 403) : json(result([invitation], Number(url.searchParams.get('page')), 2)) })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: 'Następna' }))
  expect(await screen.findByText('Strona 2 z 2')).toBeVisible()
  forbidden = true
  await userEvent.click(screen.getByRole('button', { name: 'Odśwież zaproszenia' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('wyłącznie dla właściciela')
  expect(screen.queryByRole('region', { name: 'Lista zaproszeń' })).not.toBeInTheDocument()
})

it('shows archived teams read-only and offers no action for expired or exhausted links', async () => {
  mockApi({ 'GET /api/teams/team/invites': () => json(result([invitation, { ...invitation, id: 'expired', status: 'EXPIRED' }, { ...invitation, id: 'spent', status: 'EXHAUSTED' }])) })
  panel(true)
  expect(await screen.findByRole('cell', { name: 'Wygasłe' })).toBeVisible()
  expect(screen.getByRole('cell', { name: 'Wykorzystane' })).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Unieważnij' })).not.toBeInTheDocument()
})

it('reconciles a lost revoke response instead of repeating the write automatically', async () => {
  let revoked = false
  const fetcher = mockApi({ 'GET /api/teams/team/invites': () => json(result([{ ...invitation, status: revoked ? 'REVOKED' : 'ACTIVE' }])),
    'DELETE /api/teams/team/invites/invite': () => { revoked = true; throw new TypeError('Lost response') } })
  panel()
  await userEvent.click(await screen.findByRole('button', { name: 'Unieważnij' }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Unieważnij zaproszenie' }))
  expect(await screen.findByRole('cell', { name: 'Unieważnione' })).toBeVisible()
  await waitFor(() => expect(fetcher.mock.calls.filter(([, options]) => options?.method === 'DELETE')).toHaveLength(1))
})
