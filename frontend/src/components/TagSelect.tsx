import { useId } from 'react'
import { useTags } from '../lib/tags'

/**
 * Picks the work tag of an entry. Nothing is shown while the team has no tags, so teams that do not use them see no change.
 * A tag the entry already has stays selectable after it was archived.
 */
export function TagSelect({ userId, teamId, value, currentTag, onChange, disabled }: {
  userId: string; teamId: string; value: string; currentTag?: { id: string; name: string } | null; onChange: (tagId: string) => void; disabled?: boolean
}) {
  const id = useId()
  const tags = useTags(userId, teamId)
  const options = tags.data ?? []
  const kept = currentTag && !options.some(tag => tag.id === currentTag.id) ? currentTag : null
  if (options.length === 0 && !kept) return null
  return <div className="field-group"><label htmlFor={id}>Tag <span className="optional-label">(opcjonalnie)</span></label>
    <select id={id} className="text-input" value={value} disabled={disabled} onChange={event => onChange(event.target.value)}>
      <option value="">Bez tagu</option>
      {kept && <option value={kept.id}>{kept.name} (archiwalny)</option>}
      {options.map(tag => <option key={tag.id} value={tag.id}>{tag.name}</option>)}
    </select></div>
}
