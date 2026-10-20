import { useId, useState, type FormEvent } from 'react'
import { LoaderCircle } from 'lucide-react'
import type { EntryTemplate } from '../lib/api'
import { useCreateTemplate, useDeleteTemplate, useTemplates } from '../lib/templates'
import type { JiraIssueLink } from './JiraIssuePicker'

interface Props {
  userId: string
  teamId: string
  /** What the form holds now, saved when the person stores a template. */
  current: { projectId: string; description: string; issue: JiraIssueLink | null }
  onApply: (template: EntryTemplate) => void
}

/** Starting points of one person in one team: pick one to fill the form, or save the current form as a new one. */
export function TemplatePicker({ userId, teamId, current, onApply }: Props) {
  const ids = useId()
  const templates = useTemplates(userId, teamId)
  const create = useCreateTemplate(userId, teamId)
  const remove = useDeleteTemplate(userId, teamId)
  const [selected, setSelected] = useState('')
  const [saving, setSaving] = useState(false)
  const [name, setName] = useState('')
  const [validation, setValidation] = useState('')
  const list = templates.data ?? []
  const chosen = list.find(template => template.id === selected)

  function save(event: FormEvent) {
    event.preventDefault()
    event.stopPropagation()
    if (!name.trim()) { setValidation('Wpisz nazwę szablonu.'); return }
    if (!current.projectId) { setValidation('Najpierw wybierz projekt, który ma być w szablonie.'); return }
    setValidation('')
    create.mutate({ name: name.trim(), projectId: current.projectId, description: current.description.trim() || null, jiraIssueId: current.issue?.id ?? null }, {
      onSuccess: created => { setSelected(created.id); setSaving(false); setName('') },
    })
  }

  if (templates.isError) return null
  return <div className="template-picker">
    <div className="field-group"><label htmlFor={`${ids}-template`}>Szablon <span className="optional-label">(opcjonalnie)</span></label>
      <select id={`${ids}-template`} className="text-input" value={selected} disabled={templates.isPending}
        onChange={event => { setSelected(event.target.value); const found = list.find(template => template.id === event.target.value); if (found) onApply(found) }}>
        <option value="">Bez szablonu</option>{list.map(template => <option key={template.id} value={template.id}>{template.name}</option>)}</select></div>
    <div className="template-actions">
      <button type="button" className="text-link" onClick={() => { setSaving(!saving); setValidation('') }}>{saving ? 'Anuluj zapisywanie szablonu' : 'Zapisz bieżące jako szablon'}</button>
      {chosen && <button type="button" className="text-link" disabled={remove.isPending} onClick={() => remove.mutate(chosen.id, { onSuccess: () => setSelected('') })}>Usuń szablon „{chosen.name}”</button>}
    </div>
    {saving && <div className="template-save"><label htmlFor={`${ids}-name`}>Nazwa szablonu</label>
      <div className="template-save-row"><input id={`${ids}-name`} className="text-input" maxLength={80} value={name} onChange={event => { setName(event.target.value); setValidation('') }}
        onKeyDown={event => { if (event.key === 'Enter') save(event) }} />
        <button type="button" className="button-secondary" disabled={create.isPending} onClick={save}>{create.isPending && <LoaderCircle size={16} className="spin" />}Zapisz szablon</button></div></div>}
    {(validation || create.isError || remove.isError) && <p className="form-error" role="alert">{validation || create.error?.message || remove.error?.message}</p>}
  </div>
}
