import { useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import { JiraIssuePicker, type JiraIssueLink } from '../components/JiraIssuePicker'
import { Modal } from '../components/Modal'
import type { Team, TimeEntry } from '../lib/api'
import { useProjects } from '../lib/projects'
import { useSwitchTimer, useUpdateTimer } from '../lib/time'

const currentLink = (entry: TimeEntry): JiraIssueLink | null => entry.jiraIssueId && entry.jiraIssueKey
  ? { id: entry.jiraIssueId, key: entry.jiraIssueKey, summary: '' } : null

/** Changes what the running timer is for, without stopping it. */
export function ChangeTimerModal({ userId, entry, team, description, onClose, onChanged }: {
  userId: string; entry: TimeEntry; team: Team; description: string; onClose: () => void; onChanged: (entry: TimeEntry) => void
}) {
  const update = useUpdateTimer(userId)
  const projects = useProjects(userId, team.id, true)
  const [projectId, setProjectId] = useState(entry.projectId)
  const [note, setNote] = useState(description)
  const [issue, setIssue] = useState<JiraIssueLink | null>(() => currentLink(entry))
  const options = projects.data?.filter(project => !project.archivedAt || project.id === entry.projectId) ?? []
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    update.mutate({ entryId: entry.id, projectId, description: note.trim(), jiraIssueId: issue?.id ?? null }, { onSuccess: onChanged })
  }
  return <Modal title="Zmień projekt lub zadanie" busy={update.isPending} onClose={onClose}>
    <p className="dialog-description">Timer działa dalej, a czas liczy się od jego uruchomienia. Zespół: <strong>{team.name}</strong>.</p>
    <form onSubmit={submit}>
      <fieldset disabled={update.isPending}>
        <div className="field-group"><label htmlFor="change-project">Projekt</label>
          <select id="change-project" className="text-input" value={projectId} required onChange={event => setProjectId(event.target.value)}>
            {options.length === 0 && <option value={entry.projectId}>Wczytywanie…</option>}
            {options.map(project => <option key={project.id} value={project.id}>{project.name}{project.archivedAt ? ' (archiwalny)' : ''}</option>)}</select></div>
        <div className="field-group"><label htmlFor="change-note">Opis pracy <span className="optional-label">(opcjonalnie)</span></label>
          <textarea id="change-note" className="text-input timer-description" rows={3} maxLength={2000} value={note} onChange={event => setNote(event.target.value)} /></div>
        <JiraIssuePicker userId={userId} teamId={entry.teamId} value={issue} onChange={setIssue} />
      </fieldset>
      {update.isError && <p className="form-error" role="alert">{update.error.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={update.isPending} onClick={onClose}>Anuluj</button>
        <button type="submit" className="button-primary" disabled={update.isPending}>{update.isPending && <LoaderCircle size={16} className="spin" />}{update.isPending ? 'Zapisywanie…' : 'Zapisz zmiany'}</button></footer>
    </form>
  </Modal>
}

/** Stops the running timer and starts the next one in a single step. */
export function SwitchTimerModal({ userId, entry, teams, description, onClose, onSwitched }: {
  userId: string; entry: TimeEntry; teams: Team[]; description: string; onClose: () => void; onSwitched: (stopped: TimeEntry) => void
}) {
  const choices = teams.filter(team => !team.archivedAt)
  const [teamId, setTeamId] = useState(choices.some(team => team.id === entry.teamId) ? entry.teamId : choices[0]?.id ?? '')
  const projects = useProjects(userId, teamId, false, teamId !== '')
  const [projectId, setProjectId] = useState('')
  const [finished, setFinished] = useState(description)
  const [next, setNext] = useState('')
  const [issue, setIssue] = useState<JiraIssueLink | null>(null)
  const [validation, setValidation] = useState('')
  const swap = useSwitchTimer(userId, onSwitched)
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!projects.data?.some(project => project.id === projectId)) { setValidation('Wybierz projekt, nad którym teraz pracujesz.'); return }
    setValidation('')
    swap.mutate({ stop: { entryId: entry.id, description: finished.trim() },
      start: { teamId, projectId, description: next.trim() || null, jiraIssueId: issue?.id ?? null } })
  }
  return <Modal title="Przełącz zadanie" busy={swap.isPending} onClose={onClose}>
    <p className="dialog-description">Zakończymy bieżący timer i od razu uruchomimy nowy. Jeśli nowego nie uda się uruchomić, bieżący działa dalej.</p>
    <form onSubmit={submit}>
      <fieldset disabled={swap.isPending}>
        <div className="field-group"><label htmlFor="switch-finished">Opis kończonej pracy <span className="optional-label">(opcjonalnie)</span></label>
          <textarea id="switch-finished" className="text-input timer-description" rows={2} maxLength={2000} value={finished} onChange={event => setFinished(event.target.value)} /></div>
        {choices.length > 1 && <div className="field-group"><label htmlFor="switch-team">Zespół nowej pracy</label>
          <select id="switch-team" className="text-input" value={teamId} onChange={event => { setTeamId(event.target.value); setProjectId(''); setIssue(null) }}>
            {choices.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div>}
        <div className="field-group"><label htmlFor="switch-project">Nowy projekt</label>
          <select id="switch-project" className="text-input" value={projectId} onChange={event => { setProjectId(event.target.value); setValidation('') }}>
            <option value="" disabled>Wybierz projekt</option>{projects.data?.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
        <div className="field-group"><label htmlFor="switch-next">Opis nowej pracy <span className="optional-label">(opcjonalnie)</span></label>
          <textarea id="switch-next" className="text-input timer-description" rows={2} maxLength={2000} value={next} onChange={event => setNext(event.target.value)} /></div>
        {teamId && <JiraIssuePicker key={teamId} userId={userId} teamId={teamId} value={issue} onChange={setIssue} />}
      </fieldset>
      {(validation || swap.isError) && <p className="form-error" role="alert">{validation || swap.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={swap.isPending} onClick={onClose}>Anuluj</button>
        <button type="submit" className="button-primary" disabled={swap.isPending}>{swap.isPending && <LoaderCircle size={16} className="spin" />}{swap.isPending ? 'Przełączanie…' : 'Przełącz'}</button></footer>
    </form>
  </Modal>
}
