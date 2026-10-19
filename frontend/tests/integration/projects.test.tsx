import { it, expect } from 'vitest'
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, team, project } from '../support/api'

it('hides project management from a member', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, role: 'MEMBER' }]) })
  renderApp(<App />, '/projects?team=team')
  expect(await screen.findByRole('button', { name: project.name })).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Utwórz projekt' })).not.toBeInTheDocument()
  expect(screen.queryByRole('button', { name: /Archiwizuj projekt/ })).not.toBeInTheDocument()
})
it('keeps unsaved edits in the dialog after a rejected save', async () => {
  mockApi({ 'POST /api/teams/team/projects': () => json({}, 403) })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Utwórz projekt' }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Nazwa projektu'), 'New project')
  await userEvent.click(modal.getByRole('button', { name: 'Utwórz projekt' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Nie masz uprawnień')
  expect(modal.getByLabelText('Nazwa projektu')).toHaveValue('New project')
})
it('limits the project description and explains a rejected long description', async () => {
  mockApi({ 'POST /api/teams/team/projects': () => json({}, 400) })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: 'Utwórz projekt' }))
  const modal = within(screen.getByRole('dialog'))
  expect(modal.getByLabelText('Opis', { exact: false })).toHaveAttribute('maxlength', '2000')
  await userEvent.type(modal.getByLabelText('Nazwa projektu'), 'New project')
  await userEvent.click(modal.getByRole('button', { name: 'Utwórz projekt' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Opis może mieć najwyżej 2000 znaków.')
})

it('updates a project and refreshes its archived state', async () => {
  let current = { ...project }
  mockApi({ 'GET /api/teams/team/projects': url => json(current.archivedAt && url.searchParams.get('includeArchived') !== 'true' ? [] : [current]),
    'GET /api/teams/team/projects/project': () => json(current),
    'PUT /api/teams/team/projects/project': async (_url, options) => { current = { ...current, ...JSON.parse(String(options.body)) }; return json(current) },
    'POST /api/teams/team/projects/project/archive': () => { current = { ...current, archivedAt: '2026-10-19T10:00:00Z' }; return json(current) } })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: project.name }))
  await userEvent.click(await screen.findByRole('button', { name: 'Edytuj projekt' }))
  let modal = within(screen.getByRole('dialog'))
  await userEvent.clear(modal.getByLabelText('Nazwa projektu'))
  await userEvent.type(modal.getByLabelText('Nazwa projektu'), 'Changed project')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await userEvent.click(await screen.findByRole('button', { name: 'Archiwizuj projekt Changed project' }))
  modal = within(screen.getByRole('dialog'))
  await userEvent.click(modal.getByRole('button', { name: 'Archiwizuj projekt' }))
  expect(await screen.findByText('Brak aktywnych projektów.')).toBeVisible()
  await userEvent.click(screen.getByLabelText('Pokaż również archiwalne'))
  expect(await screen.findByRole('button', { name: 'Changed project' })).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Archiwizuj projekt Changed project' })).not.toBeInTheDocument()
})
