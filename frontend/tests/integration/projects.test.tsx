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
it('shows projects of an archived team without management actions', async () => {
  mockApi({ 'GET /api/teams': () => json([{ ...team, archivedAt: '2026-10-19T12:00:00Z' }]) })
  renderApp(<App />, '/projects?team=team')
  expect(await screen.findByRole('button', { name: project.name })).toBeVisible()
  expect(screen.getByText(/Zespół jest zarchiwizowany/)).toBeVisible()
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
  await userEvent.selectOptions(screen.getByLabelText('Status projektów'), 'all')
  expect(await screen.findByRole('button', { name: 'Changed project' })).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Archiwizuj projekt Changed project' })).not.toBeInTheDocument()
})

it('saves a repository link and opens it safely from project details', async () => {
  let current = { ...project, repositoryUrl: null as string | null }
  mockApi({
    'GET /api/teams/team/projects/project': () => json(current),
    'PUT /api/teams/team/projects/project': (_url, options) => { current = { ...current, ...JSON.parse(String(options.body)) }; return json(current) },
  })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: `Edytuj projekt ${project.name}` }))
  const modal = within(screen.getByRole('dialog'))
  await userEvent.type(modal.getByLabelText('Repozytorium GitHub', { exact: false }), 'https://github.com/owner/repo')
  await userEvent.click(modal.getByRole('button', { name: 'Zapisz zmiany' }))
  await screen.findByText('Zmiany zostały zapisane.')
  expect(current.repositoryUrl).toBe('https://github.com/owner/repo')
  await userEvent.click(screen.getByRole('button', { name: project.name }))
  const link = await screen.findByRole('link', { name: 'Otwórz repozytorium' })
  expect(link).toHaveAttribute('href', current.repositoryUrl)
  expect(link).toHaveAttribute('target', '_blank')
  expect(link).toHaveAttribute('rel', 'noopener noreferrer')
})

it('filters project names without case sensitivity and supports archived-only results', async () => {
  const archived = { ...project, id: 'archived', name: 'Old Website', archivedAt: team.createdAt }
  mockApi({ 'GET /api/teams/team/projects': url => json(url.searchParams.get('includeArchived') === 'true' ? [project, archived] : [project]) })
  renderApp(<App />, '/projects?team=team')
  await screen.findByRole('button', { name: project.name })
  await userEvent.selectOptions(screen.getByLabelText('Status projektów'), 'all')
  expect(await screen.findByRole('button', { name: archived.name })).toBeVisible()
  await userEvent.type(screen.getByLabelText('Szukaj projektu'), 'OLD web')
  expect(screen.queryByRole('button', { name: project.name })).not.toBeInTheDocument()
  await userEvent.clear(screen.getByLabelText('Szukaj projektu'))
  await userEvent.selectOptions(screen.getByLabelText('Status projektów'), 'archived')
  expect(screen.queryByRole('button', { name: project.name })).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: archived.name })).toBeVisible()
})

it('restores an archived project and refreshes the available actions', async () => {
  let current = { ...project, archivedAt: team.createdAt as string | null }
  mockApi({
    'GET /api/teams/team/projects': url => json(current.archivedAt && url.searchParams.get('includeArchived') !== 'true' ? [] : [current]),
    'POST /api/teams/team/projects/project/restore': () => { current = { ...current, archivedAt: null }; return json(current) },
  })
  renderApp(<App />, '/projects?team=team')
  await userEvent.selectOptions(await screen.findByLabelText('Status projektów'), 'all')
  await userEvent.click(await screen.findByRole('button', { name: `Przywróć projekt ${project.name}` }))
  await userEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Przywróć projekt' }))
  expect(await screen.findByRole('button', { name: `Edytuj projekt ${project.name}` })).toBeVisible()
  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
})

it('confirms deletion, explains a nonempty project rejection and accepts a 204 response', async () => {
  let removed = false
  let allowDelete = false
  mockApi({
    'GET /api/teams/team/projects': () => json(removed ? [] : [project]),
    'DELETE /api/teams/team/projects/project': () => { if (!allowDelete) return json({}, 409); removed = true; return json(null, 204) },
  })
  renderApp(<App />, '/projects?team=team')
  await userEvent.click(await screen.findByRole('button', { name: `Usuń projekt ${project.name}` }))
  expect(removed).toBe(false)
  const modal = within(screen.getByRole('dialog'))
  await userEvent.click(modal.getByRole('button', { name: 'Usuń projekt' }))
  expect(await modal.findByRole('alert')).toHaveTextContent('Można usunąć tylko pusty projekt')
  allowDelete = true
  await userEvent.click(modal.getByRole('button', { name: 'Usuń projekt' }))
  expect(await screen.findByText('Projekt został usunięty.')).toBeVisible()
  expect(screen.queryByRole('button', { name: project.name })).not.toBeInTheDocument()
})
