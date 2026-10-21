import { useEffect, useState, type FormEvent } from 'react'
import { Archive, Folder, LoaderCircle, Pencil, Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Modal } from '../components/Modal'
import { ProjectDot } from '../components/ProjectDot'
import { projectColorKeys, projectColors, type ProjectColor } from '../lib/projectColors'
import { canManage, roleLabel, type Project, type Team } from '../lib/api'
import { useArchiveProject, useRestoreProject, useDeleteProject, useProject, useProjects, useSaveProject } from '../lib/projects'
import { useTeams } from '../lib/teams'
import { pickTeam, usePreferences } from '../lib/preferences'

const dateLabel = (value: string) => new Date(value).toLocaleString('pl-PL', { dateStyle: 'medium', timeStyle: 'short' })

function ProjectFormModal({ userId, team, project, onClose, onSaved }: {
  userId: string; team: Team; project?: Project; onClose: () => void; onSaved: () => void
}) {
  const save = useSaveProject(userId, team.id, project?.id)
  const [name, setName] = useState(project?.name ?? '')
  const [description, setDescription] = useState(project?.description ?? '')
  const [repositoryUrl, setRepositoryUrl] = useState(project?.repositoryUrl ?? '')
  const [color, setColor] = useState<ProjectColor | ''>((project?.color as ProjectColor | null | undefined) ?? '')
  const [validation, setValidation] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) { setValidation('Wpisz nazwę projektu.'); return }
    setValidation('')
    save.mutate({ name: name.trim(), description: description.trim() || null, repositoryUrl: repositoryUrl.trim() || null, color: color || null }, { onSuccess: onSaved })
  }

  return <Modal title={project ? 'Edytuj projekt' : 'Utwórz projekt'} busy={save.isPending} onClose={onClose}>
    <p className="dialog-description">Zespół: <strong>{team.name}</strong></p>
    <form onSubmit={submit}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="project-name">Nazwa projektu</label><input className="text-input" id="project-name" required maxLength={255} value={name} onChange={event => { setName(event.target.value); setValidation('') }} /></div>
        <div className="field-group"><label htmlFor="project-description">Opis <span className="optional-label">(opcjonalnie)</span></label><textarea className="text-input project-description-input" id="project-description" rows={5} maxLength={2000} value={description} onChange={event => setDescription(event.target.value)} /></div>
      </fieldset>
      <fieldset className="color-choice" disabled={save.isPending}><legend>Kolor projektu <span className="optional-label">(opcjonalnie)</span></legend>
        <label className="color-option"><input type="radio" name="project-color" checked={color === ''} onChange={() => setColor('')} /><span className="color-swatch color-none" aria-hidden="true" /><span>Bez koloru</span></label>
        {projectColorKeys.map(key => <label className="color-option" key={key}><input type="radio" name="project-color" checked={color === key} onChange={() => setColor(key)} /><span className="color-swatch" style={{ background: projectColors[key].hex }} aria-hidden="true" /><span>{projectColors[key].label}</span></label>)}
      </fieldset>
      <div className="field-group"><label htmlFor="project-repository">Repozytorium GitHub (opcjonalnie)</label><input className="text-input" id="project-repository" type="url" maxLength={2048} disabled={save.isPending} placeholder="https://github.com/owner/repo" value={repositoryUrl} onChange={event => setRepositoryUrl(event.target.value)} /><p className="field-hint">Adres HTTPS repozytorium, bez danych logowania i parametrów.</p></div>
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
      : <><h3 className="project-detail-name"><ProjectDot color={project.data.color} />{project.data.name}</h3>
        <p className="project-full-description">{project.data.description || 'Brak opisu projektu.'}</p>
        {project.data.repositoryUrl && <a className="text-link" href={project.data.repositoryUrl} target="_blank" rel="noopener noreferrer">Otwórz repozytorium</a>}
        <dl className="invite-summary project-summary"><dt>Zespół</dt><dd>{team.name}</dd><dt>Utworzono</dt><dd>{dateLabel(project.data.createdAt)}</dd><dt>Status</dt><dd>{project.data.archivedAt ? 'Archiwalny' : 'Aktywny'}</dd>{project.data.archivedAt && <><dt>Archiwizacja</dt><dd>{dateLabel(project.data.archivedAt)}</dd></>}</dl>
      </>}
    <footer className="dialog-actions"><button className="button-secondary" onClick={onClose}>Zamknij</button>{canManage(team.role) && !team.archivedAt && project.data && !project.isError && !project.data.archivedAt && <button className="button-primary" onClick={() => onEdit(project.data)}><Pencil size={16} />Edytuj projekt</button>}</footer>
  </Modal>
}

function ArchiveProjectModal({ userId, team, project, onClose, onArchived }: {
  userId: string; team: Team; project: Project; onClose: () => void; onArchived: () => void
}) {
  const archive = useArchiveProject(userId, team.id)
  return <Modal title="Archiwizuj projekt" busy={archive.isPending} onClose={onClose}>
    <p className="dialog-description">Projekt <strong>{project.name}</strong> w zespole <strong>{team.name}</strong> trafi do archiwum. Historia pracy zostanie zachowana. Timer i edycja projektu będą zablokowane, ale nadal można dodawać ręczne historyczne wpisy czasu. Właściciel może później przywrócić projekt.</p>
    {archive.isError && <p className="form-error" role="alert">{archive.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={archive.isPending} onClick={onClose}>Anuluj</button><button className="button-primary" disabled={archive.isPending} onClick={() => archive.mutate(project.id, { onSuccess: onArchived })}>{archive.isPending ? <LoaderCircle size={16} className="spin" /> : <Archive size={16} />}{archive.isPending ? 'Archiwizowanie…' : 'Archiwizuj projekt'}</button></footer>
  </Modal>
}

function ProjectActionModal({ userId, team, project, action, onClose, onSaved }: {
  userId: string; team: Team; project: Project; action: 'restore' | 'delete'; onClose: () => void; onSaved: () => void
}) {
  const restore = useRestoreProject(userId, team.id)
  const remove = useDeleteProject(userId, team.id)
  const mutation = action === 'restore' ? restore : remove
  const label = action === 'restore' ? 'Przywróć projekt' : 'Usuń projekt'
  return <Modal title={label} busy={mutation.isPending} onClose={onClose}>
    <p className="dialog-description">{action === 'restore' ? <>Projekt <strong>{project.name}</strong> będzie ponownie dostępny do edycji i uruchamiania timera.</> : <>Trwale usunąć projekt <strong>{project.name}</strong>? Usunięcie jest możliwe tylko wtedy, gdy projekt nie ma żadnych wpisów ani timerów.</>}</p>
    {mutation.isError && <p className="form-error" role="alert">{mutation.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={mutation.isPending} onClick={onClose}>Anuluj</button><button className="button-primary" disabled={mutation.isPending} onClick={() => mutation.mutate(project.id, { onSuccess: onSaved })}>{mutation.isPending ? 'Zapisywanie…' : label}</button></footer>
  </Modal>
}

type ProjectModal = { type: 'create' } | { type: 'details' | 'edit' | 'archive' | 'restore' | 'delete'; project: Project }

function TeamProjects({ userId, team }: { userId: string; team: Team }) {
  const [status, setStatus] = useState('active')
  const [search, setSearch] = useState('')
  const includeArchived = status !== 'active'
  const [modal, setModal] = useState<ProjectModal | null>(null)
  const [notice, setNotice] = useState('')
  const projects = useProjects(userId, team.id, includeArchived)
  const visible = projects.data?.filter(project => (status !== 'archived' || project.archivedAt)
    && project.name.toLocaleLowerCase('pl-PL').includes(search.trim().toLocaleLowerCase('pl-PL'))) ?? []
  // Projects of an archived team are read-only, also for its owner.
  const owner = canManage(team.role) && !team.archivedAt
  const close = () => setModal(null)
  const saved = (message: string) => { close(); setNotice(message) }

  return <section aria-label="Projekty wybranego zespołu">
    <header className="teams-toolbar projects-toolbar"><div><h2>{team.name}</h2><p>{owner ? 'Zarządzaj projektami swojego zespołu.' : 'Przeglądaj projekty i ich szczegóły. Projektami zarządza właściciel zespołu.'}</p></div>{owner && <button className="button-primary" disabled={projects.isError} onClick={() => { setNotice(''); setModal({ type: 'create' }) }}><Plus size={16} />Utwórz projekt</button>}</header>
    {team.archivedAt && <p className="project-notice" role="status">Zespół jest zarchiwizowany. Projekty można przeglądać, ale nie można ich zmieniać.</p>}
    <div className="projects-filter"><div className="field-group"><label htmlFor="project-search">Szukaj projektu</label><input className="text-input" id="project-search" type="search" value={search} onChange={event => setSearch(event.target.value)} /></div><div className="field-group"><label htmlFor="project-status">Status projektów</label><select className="text-input" id="project-status" value={status} onChange={event => setStatus(event.target.value)}><option value="active">Aktywne</option><option value="archived">Archiwalne</option><option value="all">Wszystkie</option></select></div>{projects.isFetching && !projects.isPending && <span role="status"><LoaderCircle size={14} className="spin" />Odświeżanie…</span>}</div>
    {notice && <p className="project-notice" role="status">{notice}</p>}
    {projects.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie projektów…</p>
      : projects.isError ? <div className="dashboard-error" role="alert">{projects.error.message}<button onClick={() => void projects.refetch()}>Spróbuj ponownie</button></div>
      : visible.length === 0 ? <div className="overview-panel empty-state"><Folder size={28} strokeWidth={1.4} /><h3>{search.trim() || status === 'archived' ? 'Brak projektów pasujących do filtrów.' : includeArchived ? 'Ten zespół nie ma jeszcze projektów.' : 'Brak aktywnych projektów.'}</h3><p>{owner ? 'Zmień filtry lub utwórz projekt, aby uporządkować pracę zespołu.' : 'Zmień filtry lub poproś właściciela zespołu o utworzenie projektu.'}</p></div>
      : <div className="team-table-wrap"><table className="team-table project-table"><caption className="sr-only">Projekty zespołu {team.name}</caption><thead><tr><th>Projekt</th><th>Status</th>{owner && <th><span className="sr-only">Działania</span></th>}</tr></thead><tbody>
        {visible.map(project => <tr key={project.id}><td><button className="project-name-button" onClick={() => setModal({ type: 'details', project })}><ProjectDot color={project.color} />{project.name}</button>{project.description && <p className="project-row-description">{project.description}</p>}</td><td><span className={`project-status ${project.archivedAt ? 'archived' : ''}`}>{project.archivedAt ? 'Archiwalny' : 'Aktywny'}</span></td>{owner && <td><div className="project-row-actions">{!project.archivedAt ? <><button aria-label={`Edytuj projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'edit', project }) }}><Pencil size={14} />Edytuj</button><button aria-label={`Archiwizuj projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'archive', project }) }}><Archive size={14} />Archiwizuj</button></> : <button aria-label={`Przywróć projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'restore', project }) }}>Przywróć</button>}<button aria-label={`Usuń projekt ${project.name}`} onClick={() => { setNotice(''); setModal({ type: 'delete', project }) }}>Usuń</button></div></td>}</tr>)}
      </tbody></table></div>}
    {modal?.type === 'create' && owner && <ProjectFormModal userId={userId} team={team} onClose={close} onSaved={() => saved('Projekt został utworzony.')} />}
    {modal?.type === 'edit' && owner && <ProjectFormModal key={modal.project.id} userId={userId} team={team} project={modal.project} onClose={close} onSaved={() => saved('Zmiany zostały zapisane.')} />}
    {modal?.type === 'details' && <ProjectDetailsModal userId={userId} team={team} projectId={modal.project.id} onClose={close} onEdit={project => setModal({ type: 'edit', project })} />}
    {modal?.type === 'archive' && owner && <ArchiveProjectModal userId={userId} team={team} project={modal.project} onClose={close} onArchived={() => saved('Projekt został zarchiwizowany.')} />}
    {(modal?.type === 'restore' || modal?.type === 'delete') && owner && <ProjectActionModal userId={userId} team={team} project={modal.project} action={modal.type} onClose={close} onSaved={() => saved(modal.type === 'restore' ? 'Projekt został przywrócony.' : 'Projekt został usunięty.')} />}
  </section>
}

export function ProjectsPanel({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const [search, setSearch] = useSearchParams()
  const selectedId = search.get('team')
  const defaultTeamId = usePreferences(userId).data?.defaultTeamId
  const selected = pickTeam(teams.data, selectedId, defaultTeamId)

  useEffect(() => {
    if (!selectedId && selected) setSearch({ team: selected.id }, { replace: true })
  }, [selectedId, selected, setSearch])

  return <section className="projects-panel" aria-label="Zarządzanie projektami">
    {teams.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Spróbuj ponownie</button></div>
      : teams.data.length === 0 ? <div className="overview-panel empty-state"><Folder size={28} strokeWidth={1.4} /><h3>Najpierw dołącz do zespołu.</h3><p>Projekt zawsze należy do zespołu. Utwórz zespół lub skorzystaj z otrzymanego zaproszenia.</p><Link className="text-link dashboard-teams-link" to="/teams">Przejdź do zespołów</Link></div>
      : <><div className="project-team-picker"><div className="field-group"><label htmlFor="project-team">Zespół</label><select className="text-input" id="project-team" value={selected?.id ?? ''} onChange={event => setSearch({ team: event.target.value })}>{!selected && <option value="" disabled>Wybierz dostępny zespół</option>}{teams.data.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div>{selected && <p className="field-hint">Twoja rola: {roleLabel(selected.role)}</p>}</div>
        {selected ? <TeamProjects key={selected.id} userId={userId} team={selected} /> : <p className="form-error" role="alert">Ten zespół nie istnieje lub nie masz do niego dostępu. Wybierz inny zespół.</p>}
      </>}
  </section>
}
