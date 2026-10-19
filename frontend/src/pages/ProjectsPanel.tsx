import { useEffect, useState, type FormEvent } from 'react'
import { Archive, Folder, LoaderCircle, Pencil, Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Modal } from '../components/Modal'
import type { Project, Team } from '../lib/api'
import { useArchiveProject, useProject, useProjects, useSaveProject } from '../lib/projects'
import { useTeams } from '../lib/teams'

const dateLabel = (value: string) => new Date(value).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })

function ProjectFormModal({ userId, team, project, onClose, onSaved }: {
  userId: string; team: Team; project?: Project; onClose: () => void; onSaved: () => void
}) {
  const save = useSaveProject(userId, team.id, project?.id)
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [validation, setValidation] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) { setValidation('Wpisz nazwę projektu.'); return }
    setValidation('')
    save.mutate({ name: name.trim(), description: description.trim() || null }, { onSuccess: onSaved })
  }

  return <Modal title={project ? 'Edytuj projekt' : 'Utwórz projekt'} busy={save.isPending} onClose={onClose}>
    <p className="dialog-description">Zespół: <strong>{team.name}</strong></p>
    <form onSubmit={submit}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="project-name">Nazwa projektu</label><input className="text-input" id="project-name" required maxLength={255} value={name} onChange={event => { setName(event.target.value); setValidation('') }} /></div>
        <div className="field-group"><label htmlFor="project-description">Opis <span className="optional-label">(opcjonalnie)</span></label><textarea className="text-input project-description-input" id="project-description" rows={5} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></div>
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={save.isPending} onClick={onClose}>Anuluj</button><button className="button-primary" type="submit" disabled={save.isPending}>{save.isPending && <LoaderCircle size={16} className="spin" />}{save.isPending ? 'Zapisywanie…' : project ? 'Zapisz zmiany' : 'Utwórz projekt'}</button></footer>
    </form>
  </Modal>
}

function ProjectDetailsModal({ userId, team, projectId, onClose, onEdit }: {
  userId: string; team: Team; projectId: string; onClose: () => void; onEdit: (project: Project) => void
}) {
  const project = useProject(userId, team.id, projectId)
  return <Modal title="Szczegóły projektu" busy={false} onClose={onClose}>
    {project.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie projektu…</p>
      : project.isError ? <div className="dashboard-error" role="alert">{project.error.message}<button onClick={() => void project.refetch()}>Spróbuj ponownie</button></div>
      : <><h3 className="project-detail-name">{project.data.name}</h3>
        <p className="project-full-description">{project.data.description || 'Brak opisu projektu.'}</p>
        <dl className="invite-summary project-summary"><dt>Zespół</dt><dd>{team.name}</dd><dt>Utworzono</dt><dd>{dateLabel(project.data.createdAt)}</dd><dt>Status</dt><dd>{project.data.archivedAt ? 'Archiwalny' : 'Aktywny'}</dd>{project.data.archivedAt && <><dt>Archiwizacja</dt><dd>{dateLabel(project.data.archivedAt)}</dd></>}</dl>
      </>}
    <footer className="dialog-actions"><button className="button-secondary" onClick={onClose}>Zamknij</button>{team.role === 'OWNER' && project.data && !project.isError && !project.data.archivedAt && <button className="button-primary" onClick={() => onEdit(project.data)}><Pencil size={16} />Edytuj projekt</button>}</footer>
  </Modal>
}

function ArchiveProjectModal({ userId, team, project, onClose, onArchived }: {
  userId: string; team: Team; project: Project; onClose: () => void; onArchived: () => void
}) {
  const archive = useArchiveProject(userId, team.id)
  return <Modal title="Archiwizuj projekt" busy={archive.isPending} onClose={onClose}>
    <p className="dialog-description">Projekt <strong>{project.name}</strong> w zespole <strong>{team.name}</strong> trafi do archiwum. Historia pracy zostanie zachowana. Projekt nie będzie dostępny do edycji ani nowych wpisów czasu.</p>
    {archive.isError && <p className="form-error" role="alert">{archive.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={archive.isPending} onClick={onClose}>Anuluj</button><button className="button-primary" disabled={archive.isPending} onClick={() => archive.mutate(project.id, { onSuccess: onArchived })}>{archive.isPending ? <LoaderCircle size={16} className="spin" /> : <Archive size={16} />}{archive.isPending ? 'Archiwizowanie…' : 'Archiwizuj projekt'}</button></footer>
  </Modal>
}

type ProjectModal = { type: 'create' } | { type: 'details' | 'edit' | 'archive'; project: Project }

function TeamProjects({ userId, team }: { userId: string; team: Team }) {
  const [includeArchived, setIncludeArchived] = useState(false)
  const [modal, setModal] = useState<ProjectModal | null>(null)
  const [notice, setNotice] = useState('')
  const projects = useProjects(userId, team.id, includeArchived)
  const owner = team.role === 'OWNER'
  const close = () => setModal(null)
  const saved = (message: string) => { close(); setNotice(message) }

  return <section aria-label="Projekty wybranego zespołu">
    <header className="teams-toolbar projects-toolbar"><div><h2>{team.name}</h2><p>{owner ? 'Zarządzaj projektami swojego zespołu.' : 'Przeglądaj projekty i ich szczegóły. Projektami zarządza właściciel zespołu.'}</p></div>{owner && <button className="button-primary" disabled={projects.isError} onClick={() => { setNotice(''); setModal({ type: 'create' }) }}><Plus size={16} />Utwórz projekt</button>}</header>
    <div className="projects-filter"><label><input type="checkbox" checked={includeArchived} onChange={event => setIncludeArchived(event.target.checked)} />Pokaż również archiwalne</label>{projects.isFetching && !projects.isPending && <span role="status"><LoaderCircle size={14} className="spin" />Odświeżanie…</span>}</div>
    {notice && <p className="project-notice" role="status">{notice}</p>}
    {projects.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie projektów…</p>
      : projects.isError ? <div className="dashboard-error" role="alert">{projects.error.message}<button onClick={() => void projects.refetch()}>Spróbuj ponownie</button></div>
      : projects.data.length === 0 ? <div className="overview-panel empty-state"><Folder size={28} strokeWidth={1.4} /><h3>{includeArchived ? 'Ten zespół nie ma jeszcze projektów.' : 'Brak aktywnych projektów.'}</h3><p>{owner ? 'Utwórz projekt, aby uporządkować pracę zespołu.' : 'Projekty pojawią się tutaj, gdy utworzy je właściciel zespołu.'}</p></div>
      : <div className="team-table-wrap"><table className="team-table project-table"><caption className="sr-only">Projekty zespołu {team.name}</caption><thead><tr><th>Projekt</th><th>Status</th>{owner && <th><span className="sr-only">Działania</span></th>}</tr></thead><tbody>
        {projects.data.map(project => <tr key={project.id}><td><button className="project-name-button" onClick={() => setModal({ type: 'details', project })}>{project.name}</button>{project.description && <p className="project-row-description">{project.description}</p>}</td><td><span className={`project-status ${project.archivedAt ? 'archived' : ''}`}>{project.archivedAt ? 'Archiwalny' : 'Aktywny'}</span></td>{owner && <td>{!project.archivedAt && <div className="project-row-actions"><button aria-label={`Edytuj projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'edit', project }) }}><Pencil size={14} />Edytuj</button><button aria-label={`Archiwizuj projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'archive', project }) }}><Archive size={14} />Archiwizuj</button></div>}</td>}</tr>)}
      </tbody></table></div>}
    {modal?.type === 'create' && owner && <ProjectFormModal userId={userId} team={team} onClose={close} onSaved={() => saved('Projekt został utworzony.')} />}
    {modal?.type === 'edit' && owner && <ProjectFormModal key={modal.project.id} userId={userId} team={team} project={modal.project} onClose={close} onSaved={() => saved('Zmiany zostały zapisane.')} />}
    {modal?.type === 'details' && <ProjectDetailsModal userId={userId} team={team} projectId={modal.project.id} onClose={close} onEdit={project => setModal({ type: 'edit', project })} />}
    {modal?.type === 'archive' && owner && <ArchiveProjectModal userId={userId} team={team} project={modal.project} onClose={close} onArchived={() => saved('Projekt został zarchiwizowany.')} />}
  </section>
}

export function ProjectsPanel({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const [search, setSearch] = useSearchParams()
  const selectedId = search.get('team')
  const selected = selectedId ? teams.data?.find(team => team.id === selectedId) : teams.data?.[0]

  useEffect(() => {
    if (!selectedId && selected) setSearch({ team: selected.id }, { replace: true })
  }, [selectedId, selected, setSearch])

  return <section className="projects-panel" aria-label="Zarządzanie projektami">
    {teams.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Spróbuj ponownie</button></div>
      : teams.data.length === 0 ? <div className="overview-panel empty-state"><Folder size={28} strokeWidth={1.4} /><h3>Najpierw dołącz do zespołu.</h3><p>Projekt zawsze należy do zespołu. Utwórz zespół lub skorzystaj z otrzymanego zaproszenia.</p><Link className="text-link dashboard-teams-link" to="/teams">Przejdź do zespołów</Link></div>
      : <><div className="project-team-picker"><div className="field-group"><label htmlFor="project-team">Zespół</label><select className="text-input" id="project-team" value={selected?.id ?? ''} onChange={event => setSearch({ team: event.target.value })}>{!selected && <option value="" disabled>Wybierz dostępny zespół</option>}{teams.data.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div>{selected && <p className="field-hint">Twoja rola: {selected.role === 'OWNER' ? 'Właściciel' : 'Członek'}</p>}</div>
        {selected ? <TeamProjects key={selected.id} userId={userId} team={selected} /> : <p className="form-error" role="alert">Ten zespół nie istnieje lub nie masz do niego dostępu. Wybierz inny zespół.</p>}
      </>}
  </section>
}
