import { useMemo, useState, type FormEvent } from 'react'
import { ArrowLeft, Archive, ArchiveRestore, ExternalLink, FolderPlus, Pencil, Search, Trash2 } from 'lucide-react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import { canManage, type Project, type Team } from '../../lib/api'
import { projectColorHex, projectColorKeys, projectColors } from '../../lib/projectColors'
import { useArchiveProject, useDeleteProject, useProject, useRestoreProject, useSaveProject } from '../../lib/projects'
import { useTeams } from '../../lib/teams'
import { useTimeEntryHistory } from '../../lib/time'
import { Button, Dialog, Dot, Empty, Field, LoadError, Loading, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { AddTimeCard } from '../work/AddTime'
import { useAllProjects, type WorkProject } from '../work/data'
import { MeasureCard } from '../work/Measure'
import './projects.css'

const messages = {
  pl: {
    title: 'Projekty',
    create: 'Nowy projekt',
    search: 'Szukaj projektu',
    status: 'Stan',
    active: 'Aktywne',
    archived: 'Zarchiwizowane',
    all: 'Wszystkie zespoły',
    none: 'Brak projektów',
    noneBody: 'W wybranym zakresie nie ma projektów.',
    noneManage: 'Utwórz pierwszy projekt, aby zacząć zapisywać czas.',
    noneMember: 'Projekty tworzą osoby zarządzające zespołem.',
    noMatch: 'Brak projektów pasujących do wyszukiwania.',
    clear: 'Wyczyść wyszukiwanie',
    archivedBadge: 'Zarchiwizowany',
    teamArchived: 'Zespół zarchiwizowany',
    name: 'Nazwa projektu',
    description: 'Opis',
    repo: 'Repozytorium GitHub',
    repoHint: 'Adres https://github.com/właściciel/repozytorium',
    color: 'Kolor',
    noColor: 'Bez koloru',
    team: 'Zespół',
    save: 'Zapisz',
    saving: 'Zapisywanie…',
    created: 'Utworzono projekt {name}.',
    saved: 'Zapisano zmiany.',
    editTitle: 'Edytuj projekt',
    createTitle: 'Nowy projekt',
    needName: 'Wpisz nazwę projektu.',
    edit: 'Edytuj',
    archive: 'Archiwizuj',
    restore: 'Przywróć',
    delete: 'Usuń',
    archiveTitle: 'Zarchiwizować projekt {name}?',
    archiveBody:
      'Projekt zniknie z wyboru przy timerze. Zapisany czas zostaje, a ręczne wpisy do niego nadal są możliwe. Możesz go przywrócić.',
    archivedDone: 'Projekt zarchiwizowany.',
    restored: 'Projekt przywrócony.',
    deleteTitle: 'Usunąć projekt {name}?',
    deleteBody: 'Usunąć można tylko projekt bez zapisanego czasu. Tej operacji nie można cofnąć.',
    deleted: 'Projekt usunięty.',
    details: 'Szczegóły',
    mine: 'Twoja praca w projekcie',
    noMine: 'Nie masz jeszcze czasu w tym projekcie.',
    entries: 'Wpisów: {count}',
    history: 'Pełna historia projektu',
    back: 'Wszystkie projekty',
    notFound: 'Nie znaleziono projektu albo nie masz już do niego dostępu.',
    created_at: 'Utworzony {date}',
    manageOnly: 'Projekty zarządzane przez OWNER i ADMIN zespołu.',
  },
  en: {
    title: 'Projects',
    create: 'New project',
    search: 'Search projects',
    status: 'State',
    active: 'Active',
    archived: 'Archived',
    all: 'All teams',
    none: 'No projects',
    noneBody: 'There are no projects in the selected scope.',
    noneManage: 'Create the first project to start recording time.',
    noneMember: 'Projects are created by the people managing the team.',
    noMatch: 'No projects match the search.',
    clear: 'Clear the search',
    archivedBadge: 'Archived',
    teamArchived: 'Team archived',
    name: 'Project name',
    description: 'Description',
    repo: 'GitHub repository',
    repoHint: 'Address https://github.com/owner/repository',
    color: 'Colour',
    noColor: 'No colour',
    team: 'Team',
    save: 'Save',
    saving: 'Saving…',
    created: 'Project {name} created.',
    saved: 'Changes saved.',
    editTitle: 'Edit project',
    createTitle: 'New project',
    needName: 'Enter the project name.',
    edit: 'Edit',
    archive: 'Archive',
    restore: 'Restore',
    delete: 'Delete',
    archiveTitle: 'Archive {name}?',
    archiveBody:
      'The project disappears from the timer choice. Recorded time stays and manual entries are still possible. You can restore it.',
    archivedDone: 'Project archived.',
    restored: 'Project restored.',
    deleteTitle: 'Delete {name}?',
    deleteBody: 'Only a project without recorded time can be deleted. This cannot be undone.',
    deleted: 'Project deleted.',
    details: 'Details',
    mine: 'Your work in the project',
    noMine: 'You have no time in this project yet.',
    entries: 'Entries: {count}',
    history: 'Full project history',
    back: 'All projects',
    notFound: 'Project not found or you no longer have access.',
    created_at: 'Created {date}',
    manageOnly: 'Projects are managed by the OWNER and ADMIN of the team.',
  },
}

const projectPath = (project: { teamId: string; id: string }) =>
  `/app/teams/${encodeURIComponent(project.teamId)}/projects/${encodeURIComponent(project.id)}`

/** Projects of all teams, distinguishable by team, with search, state and management where the role allows it. */
export function Projects() {
  const user = useUser()
  const m = useMessages(messages)
  const [search, setSearch] = useSearchParams()
  const teamsQuery = useTeams(user.id)
  const teams = useMemo(() => teamsQuery.data ?? [], [teamsQuery.data])
  const status = search.get('status') === 'archived' ? 'archived' : 'active'
  const all = useAllProjects(user.id, teams, true)
  const scope = teams.find(team => team.id === search.get('team')) ?? null
  const [text, setText] = useState('')
  const [dialog, setDialog] = useState<
    { kind: 'create' } | { kind: 'edit' | 'archive' | 'delete'; project: WorkProject } | null
  >(null)
  const toast = useToast()
  const manageable = teams.filter(team => canManage(team.role) && !team.archivedAt)
  const set = (key: string, value: string | null) =>
    setSearch(
      previous => {
        const next = new URLSearchParams(previous)
        if (value) next.set(key, value)
        else next.delete(key)
        return next
      },
      { replace: true },
    )

  if (teamsQuery.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (teamsQuery.isError)
    return (
      <div className="page">
        <LoadError onRetry={() => void teamsQuery.refetch()} message={teamsQuery.error.message} />
      </div>
    )

  const query = text.trim().toLocaleLowerCase()
  const visible = all.projects
    .filter(
      project =>
        (!scope || project.teamId === scope.id) &&
        (status === 'archived' ? !!project.archivedAt : !project.archivedAt) &&
        (!query ||
          project.name.toLocaleLowerCase().includes(query) ||
          project.team.name.toLocaleLowerCase().includes(query)),
    )
    .sort((a, b) => a.name.localeCompare(b.name) || a.team.name.localeCompare(b.team.name))

  return (
    <div className="page projects">
      <header className="page-head">
        <div>
          <h1>{m('title')}</h1>
        </div>
        {!!manageable.length && (
          <Button variant="primary" onClick={() => setDialog({ kind: 'create' })}>
            <FolderPlus size={16} aria-hidden="true" />
            {m('create')}
          </Button>
        )}
      </header>
      <div className="toolbar">
        <div className="input-wrap search-box">
          <Search size={16} aria-hidden="true" />
          <input
            className="input"
            type="search"
            aria-label={m('search')}
            placeholder={m('search')}
            value={text}
            onChange={event => setText(event.target.value)}
          />
        </div>
        <div className="segmented" role="group" aria-label={m('status')}>
          <button type="button" aria-pressed={status === 'active'} onClick={() => set('status', null)}>
            {m('active')}
          </button>
          <button type="button" aria-pressed={status === 'archived'} onClick={() => set('status', 'archived')}>
            {m('archived')}
          </button>
        </div>
        {teams.length > 1 && (
          <div className="segmented" role="group" aria-label={m('team')}>
            <button type="button" aria-pressed={!scope} onClick={() => set('team', null)}>
              {m('all')}
            </button>
            {teams.map(team => (
              <button
                key={team.id}
                type="button"
                aria-pressed={scope?.id === team.id}
                onClick={() => set('team', team.id)}
              >
                {team.name}
              </button>
            ))}
          </div>
        )}
      </div>
      {all.isError && <LoadError onRetry={() => void Promise.all(all.queries.map(item => item.refetch()))} />}
      {all.isPending ? (
        <div className="skeleton" style={{ height: 200 }} />
      ) : !visible.length ? (
        query ? (
          <Empty title={m('noMatch')} action={<Button onClick={() => setText('')}>{m('clear')}</Button>} />
        ) : (
          <Empty
            title={m('none')}
            action={
              status === 'active' && manageable.length ? (
                <Button variant="primary" onClick={() => setDialog({ kind: 'create' })}>
                  {m('create')}
                </Button>
              ) : undefined
            }
          >
            {status === 'active' ? (manageable.length ? m('noneManage') : m('noneMember')) : m('noneBody')}
          </Empty>
        )
      ) : (
        <ul className="project-grid">
          {visible.map(project => {
            const manage = canManage(project.team.role) && !project.team.archivedAt
            return (
              <li key={project.id} className="card project-card">
                <Link className="project-link" to={projectPath(project)}>
                  <Dot color={projectColorHex(project.color)} />
                  <span>
                    <b>{project.name}</b>
                    <small>{project.team.name}</small>
                  </span>
                </Link>
                {project.description && <p className="muted clamp">{project.description}</p>}
                <div className="project-meta">
                  {project.archivedAt && <span className="badge">{m('archivedBadge')}</span>}
                  {project.team.archivedAt && <span className="badge">{m('teamArchived')}</span>}
                </div>
                {manage && (
                  <div className="row-actions">
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`${m('edit')}: ${project.name}`}
                      onClick={() => setDialog({ kind: 'edit', project })}
                    >
                      <Pencil size={16} />
                    </button>
                    {project.archivedAt ? (
                      <RestoreButton project={project} onDone={() => toast(m('restored'))} />
                    ) : (
                      <button
                        type="button"
                        className="icon-btn"
                        aria-label={`${m('archive')}: ${project.name}`}
                        onClick={() => setDialog({ kind: 'archive', project })}
                      >
                        <Archive size={16} />
                      </button>
                    )}
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`${m('delete')}: ${project.name}`}
                      onClick={() => setDialog({ kind: 'delete', project })}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                )}
              </li>
            )
          })}
        </ul>
      )}
      {!manageable.length && !!teams.length && <p className="field-hint">{m('manageOnly')}</p>}
      {dialog?.kind === 'create' && (
        <ProjectDialog
          teams={manageable}
          defaultTeam={scope && manageable.includes(scope) ? scope : manageable[0]!}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'edit' && (
        <ProjectDialog
          teams={[dialog.project.team]}
          defaultTeam={dialog.project.team}
          project={dialog.project}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === 'archive' && <ArchiveDialog project={dialog.project} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'delete' && <DeleteDialog project={dialog.project} onClose={() => setDialog(null)} />}
    </div>
  )
}

function RestoreButton({ project, onDone }: { project: WorkProject; onDone: () => void }) {
  const user = useUser()
  const m = useMessages(messages)
  const restore = useRestoreProject(user.id, project.teamId)
  return (
    <button
      type="button"
      className="icon-btn"
      aria-label={`${m('restore')}: ${project.name}`}
      disabled={restore.isPending}
      onClick={() => restore.mutate(project.id, { onSuccess: onDone })}
    >
      <ArchiveRestore size={16} />
    </button>
  )
}

export function ProjectDialog({
  teams,
  defaultTeam,
  project,
  onClose,
}: {
  teams: Team[]
  defaultTeam: Team
  project?: Project
  onClose: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const navigate = useNavigate()
  const [teamId, setTeamId] = useState(project?.teamId ?? defaultTeam.id)
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [repo, setRepo] = useState(project?.repositoryUrl ?? '')
  const [color, setColor] = useState<string>(project?.color ?? '')
  const [error, setError] = useState('')
  const save = useSaveProject(user.id, teamId, project?.id)
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      setError(m('needName'))
      return
    }
    setError('')
    save.mutate(
      {
        name: name.trim(),
        description: description.trim() || null,
        repositoryUrl: repo.trim() || null,
        color: color || null,
      },
      {
        onSuccess: saved => {
          toast(project ? m('saved') : m('created', { name: saved.name }))
          onClose()
          if (!project) navigate(projectPath(saved))
        },
      },
    )
  }
  return (
    <Dialog open title={m(project ? 'editTitle' : 'createTitle')} onClose={onClose} busy={save.isPending}>
      <form className="stack" onSubmit={submit} noValidate>
        {!project && teams.length > 1 && (
          <Field label={m('team')}>
            {props => (
              <select
                {...props}
                className="select input"
                value={teamId}
                onChange={event => setTeamId(event.target.value)}
              >
                {teams.map(team => (
                  <option key={team.id} value={team.id}>
                    {team.name}
                  </option>
                ))}
              </select>
            )}
          </Field>
        )}
        <Field label={m('name')} error={error || undefined}>
          {props => (
            <input
              {...props}
              className="input"
              value={name}
              maxLength={255}
              required
              autoFocus
              onChange={event => setName(event.target.value)}
            />
          )}
        </Field>
        <Field label={m('description')} optional>
          {props => (
            <textarea
              {...props}
              className="textarea input"
              rows={3}
              maxLength={2000}
              value={description}
              onChange={event => setDescription(event.target.value)}
            />
          )}
        </Field>
        <Field label={m('repo')} optional hint={m('repoHint')}>
          {props => (
            <input
              {...props}
              className="input"
              type="url"
              inputMode="url"
              value={repo}
              maxLength={2048}
              placeholder="https://github.com/…"
              onChange={event => setRepo(event.target.value)}
            />
          )}
        </Field>
        <fieldset className="field">
          <legend className="field-label">
            {m('color')} <i>{t('common.optional')}</i>
          </legend>
          <div className="swatches">
            <label className="swatch">
              <input type="radio" name="project-color" checked={!color} onChange={() => setColor('')} />
              <span className="swatch-dot swatch-none" aria-hidden="true" />
              {m('noColor')}
            </label>
            {projectColorKeys.map(key => (
              <label key={key} className="swatch">
                <input type="radio" name="project-color" checked={color === key} onChange={() => setColor(key)} />
                <span className="swatch-dot" style={{ background: projectColors[key].hex }} aria-hidden="true" />
                {projectColors[key].label}
              </label>
            ))}
          </div>
        </fieldset>
        {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        <div className="dialog-actions">
          <Button onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="primary" pending={save.isPending}>
            {m(save.isPending ? 'saving' : 'save')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function ArchiveDialog({ project, onClose }: { project: WorkProject | Project; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const archive = useArchiveProject(user.id, project.teamId)
  return (
    <Dialog open title={m('archiveTitle', { name: project.name })} onClose={onClose} busy={archive.isPending}>
      <p>{m('archiveBody')}</p>
      {archive.isError && <Notice tone="error">{archive.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={archive.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="primary"
          pending={archive.isPending}
          onClick={() =>
            archive.mutate(project.id, {
              onSuccess: () => {
                toast(m('archivedDone'))
                onClose()
              },
            })
          }
        >
          {m('archive')}
        </Button>
      </div>
    </Dialog>
  )
}

function DeleteDialog({
  project,
  onClose,
  onDeleted,
}: {
  project: WorkProject | Project
  onClose: () => void
  onDeleted?: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const remove = useDeleteProject(user.id, project.teamId)
  return (
    <Dialog open title={m('deleteTitle', { name: project.name })} onClose={onClose} busy={remove.isPending}>
      <p>{m('deleteBody')}</p>
      {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={remove.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={remove.isPending}
          onClick={() =>
            remove.mutate(project.id, {
              onSuccess: () => {
                toast(m('deleted'))
                onClose()
                onDeleted?.()
              },
            })
          }
        >
          {m('delete')}
        </Button>
      </div>
    </Dialog>
  )
}

/** One project with its information, the person's own work in it, quick recording and management for OWNER/ADMIN. */
export function ProjectDetail() {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const navigate = useNavigate()
  const { teamId = '', projectId = '' } = useParams()
  const teams = useTeams(user.id)
  const team = teams.data?.find(item => item.id === teamId)
  const project = useProject(user.id, teamId, projectId, !!team)
  const history = useTimeEntryHistory(user.id, teamId, { projectId, from: '', to: '' }, 0)
  const restore = useRestoreProject(user.id, teamId)
  const [dialog, setDialog] = useState<'edit' | 'archive' | 'delete' | null>(null)
  if (teams.isPending || (team && project.isPending))
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (!team || project.isError)
    return (
      <div className="page">
        <Empty
          title={m('notFound')}
          action={
            <Link className="btn" to="/app/projects">
              {m('back')}
            </Link>
          }
        />
      </div>
    )
  const data = project.data!
  const workProject: WorkProject = { ...data, team }
  const manage = canManage(team.role) && !team.archivedAt
  const recordable = !team.archivedAt
  const total = history.data?.content.reduce((sum, entry) => sum + entry.durationSeconds, 0) ?? 0

  return (
    <div className="page project-detail">
      <header className="page-head">
        <div>
          <div className="row-inline">
            <Link className="back-link" to="/app/projects">
              <ArrowLeft size={16} aria-hidden="true" />
              {m('back')}
            </Link>
            <span className="muted">{team.name}</span>
          </div>
          <h1 className="title-with-dot">
            <Dot color={projectColorHex(data.color)} />
            {data.name}
          </h1>
          <div className="project-meta">
            {data.archivedAt && <span className="badge">{m('archivedBadge')}</span>}
            {team.archivedAt && <span className="badge">{m('teamArchived')}</span>}
            <span className="muted">
              {m('created_at', { date: formatWorkDate(data.createdAt.slice(0, 10), locale) })}
            </span>
          </div>
        </div>
        {manage && (
          <div className="row">
            <Button onClick={() => setDialog('edit')}>
              <Pencil size={16} aria-hidden="true" />
              {m('edit')}
            </Button>
            {data.archivedAt ? (
              <Button
                pending={restore.isPending}
                onClick={() => restore.mutate(data.id, { onSuccess: () => toast(m('restored')) })}
              >
                <ArchiveRestore size={16} aria-hidden="true" />
                {m('restore')}
              </Button>
            ) : (
              <Button onClick={() => setDialog('archive')}>
                <Archive size={16} aria-hidden="true" />
                {m('archive')}
              </Button>
            )}
            <Button variant="ghost" onClick={() => setDialog('delete')}>
              <Trash2 size={16} aria-hidden="true" />
              {m('delete')}
            </Button>
          </div>
        )}
      </header>
      {(data.description || data.repositoryUrl) && (
        <section className="card stack-sm" aria-label={m('details')}>
          {data.description && <p className="pre-line">{data.description}</p>}
          {data.repositoryUrl && (
            <a className="action-link" href={data.repositoryUrl} target="_blank" rel="noopener noreferrer">
              <ExternalLink size={14} aria-hidden="true" /> {data.repositoryUrl.replace('https://github.com/', '')}
            </a>
          )}
        </section>
      )}
      {recordable && (
        <div className="record">
          {!data.archivedAt && (
            <MeasureCard projects={[workProject]} allProjects={[workProject]} recentProjectIds={[]} loading={false} />
          )}
          <AddTimeCard projects={[workProject]} loading={false} />
        </div>
      )}
      <section className="card" aria-labelledby="mine-title">
        <div className="card-head">
          <h2 id="mine-title">{m('mine')}</h2>
          {!!history.data?.content.length && <b className="num">{formatDuration(total)}</b>}
        </div>
        {history.isPending ? (
          <Loading />
        ) : history.isError ? (
          <LoadError onRetry={() => void history.refetch()} message={history.error.message} />
        ) : !history.data.content.length ? (
          <p className="muted">{m('noMine')}</p>
        ) : (
          <>
            <ul className="entries">
              {history.data.content.slice(0, 8).map(entry => (
                <li key={entry.id}>
                  <span className="entry-main">
                    <b>{entry.description || t('common.noDescription')}</b>
                    <small>{t(entry.entryType === 'TIMER' ? 'work.kind.timer' : 'work.kind.manual')}</small>
                  </span>
                  <span className="entry-side">
                    <b className="num">{formatDuration(entry.durationSeconds)}</b>
                    <small>{entry.workDate ? formatWorkDate(entry.workDate, locale) : t('work.undated')}</small>
                  </span>
                </li>
              ))}
            </ul>
            <p className="more">
              {m('entries', { count: history.data.totalElements })} ·{' '}
              <Link className="text-link" to={`/app/work/history?view=list&team=${encodeURIComponent(team.id)}`}>
                {m('history')}
              </Link>
            </p>
          </>
        )}
      </section>
      {dialog === 'edit' && (
        <ProjectDialog teams={[team]} defaultTeam={team} project={data} onClose={() => setDialog(null)} />
      )}
      {dialog === 'archive' && <ArchiveDialog project={data} onClose={() => setDialog(null)} />}
      {dialog === 'delete' && (
        <DeleteDialog project={data} onClose={() => setDialog(null)} onDeleted={() => navigate('/app/projects')} />
      )}
    </div>
  )
}
