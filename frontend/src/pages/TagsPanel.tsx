import { useState, type FormEvent } from 'react'
import { Archive, LoaderCircle, Pencil, Plus, RotateCcw } from 'lucide-react'
import { Modal } from '../components/Modal'
import { ProjectDot } from '../components/ProjectDot'
import type { Tag, Team } from '../lib/api'
import { projectColorKeys, projectColors, type ProjectColor } from '../lib/projectColors'
import { useArchiveTag, useCreateTag, useRestoreTag, useTags, useUpdateTag } from '../lib/tags'

function TagForm({ userId, team, tag, onClose }: { userId: string; team: Team; tag: Tag | null; onClose: () => void }) {
  const create = useCreateTag(userId, team.id)
  const update = useUpdateTag(userId, team.id)
  const save = tag ? update : create
  const [name, setName] = useState(tag?.name ?? '')
  const [color, setColor] = useState<ProjectColor | null>(tag?.color && tag.color in projectColors ? tag.color as ProjectColor : null)
  const [validation, setValidation] = useState('')
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) { setValidation('Podaj nazwę tagu.'); return }
    setValidation('')
    const body = { teamId: team.id, name: name.trim(), color }
    if (tag) update.mutate({ ...body, id: tag.id }, { onSuccess: onClose })
    else create.mutate(body, { onSuccess: onClose })
  }
  return <Modal title={tag ? 'Edytuj tag' : 'Dodaj tag'} busy={save.isPending} onClose={onClose}>
    <form onSubmit={submit} onInput={() => setValidation('')}>
      <fieldset disabled={save.isPending}>
        <div className="field-group"><label htmlFor="tag-name">Nazwa tagu</label><input id="tag-name" className="text-input" maxLength={40} required value={name} onChange={event => setName(event.target.value)} /></div>
        <fieldset className="color-picker"><legend>Kolor <span className="optional-label">(opcjonalnie)</span></legend>
          <label className="color-option"><input type="radio" name="tag-color" checked={color === null} onChange={() => setColor(null)} /><span>Bez koloru</span></label>
          {projectColorKeys.map(key => <label className="color-option" key={key}><input type="radio" name="tag-color" checked={color === key} onChange={() => setColor(key)} /><span className="color-swatch" style={{ background: projectColors[key].hex }} aria-hidden="true" /><span>{projectColors[key].label}</span></label>)}
        </fieldset>
      </fieldset>
      {(validation || save.isError) && <p className="form-error" role="alert">{validation || save.error?.message}</p>}
      <footer className="dialog-actions"><button type="button" className="button-secondary" disabled={save.isPending} onClick={onClose}>Anuluj</button>
        <button className="button-primary" disabled={save.isPending}>{save.isPending && <LoaderCircle className="spin" size={16} />}{save.isPending ? 'Zapisywanie…' : 'Zapisz tag'}</button></footer>
    </form>
  </Modal>
}

/** Tags of a team, managed by its owner and admins. Members only choose them on their entries. */
export function TagsPanel({ userId, team }: { userId: string; team: Team }) {
  const tags = useTags(userId, team.id, true)
  const archive = useArchiveTag(userId, team.id)
  const restore = useRestoreTag(userId, team.id)
  const [editing, setEditing] = useState<{ tag: Tag | null } | null>(null)
  const error = archive.error ?? restore.error
  return <section className="team-tags" aria-label="Tagi pracy">
    <header className="teams-toolbar"><div><h2>Tagi pracy</h2><p>Krótkie etykiety, którymi członkowie oznaczają wpisy. Tagi filtrują raporty i trafiają do eksportów.</p></div>
      {!team.archivedAt && <button className="button-secondary" onClick={() => setEditing({ tag: null })}><Plus size={15} />Dodaj tag</button>}</header>
    {error && <p className="form-error" role="alert">{error.message}</p>}
    {tags.isPending ? <p className="teams-loading" role="status"><LoaderCircle size={18} className="spin" />Wczytywanie tagów…</p>
      : tags.isError ? <div className="dashboard-error" role="alert">{tags.error.message}<button onClick={() => void tags.refetch()}>Wczytaj tagi ponownie</button></div>
      : tags.data.length === 0 ? <p className="report-empty">Zespół nie ma jeszcze tagów.</p>
      : <ul className="correction-list" aria-label="Lista tagów">{tags.data.map(tag => <li key={tag.id}>
        <div><strong className="history-project-name"><ProjectDot color={tag.color} />{tag.name}</strong>{tag.archivedAt && <span className="project-status archived">Zarchiwizowany</span>}</div>
        {!team.archivedAt && <div className="history-actions">
          {!tag.archivedAt && <button className="text-link" aria-label={`Edytuj tag: ${tag.name}`} onClick={() => setEditing({ tag })}><Pencil size={15} />Edytuj</button>}
          {tag.archivedAt ? <button className="text-link" aria-label={`Przywróć tag: ${tag.name}`} disabled={restore.isPending} onClick={() => restore.mutate({ teamId: team.id, id: tag.id })}><RotateCcw size={15} />Przywróć</button>
            : <button className="text-link" aria-label={`Archiwizuj tag: ${tag.name}`} disabled={archive.isPending} onClick={() => archive.mutate({ teamId: team.id, id: tag.id })}><Archive size={15} />Archiwizuj</button>}
        </div>}
      </li>)}</ul>}
    {editing && <TagForm key={editing.tag?.id ?? 'new'} userId={userId} team={team} tag={editing.tag} onClose={() => setEditing(null)} />}
  </section>
}
