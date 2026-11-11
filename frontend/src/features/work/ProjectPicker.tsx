import { useEffect, useId, useMemo, useRef, useState } from 'react'
import { Check, ChevronDown, Search } from 'lucide-react'
import { useT } from '../../content/i18n'
import { projectColorHex } from '../../lib/projectColors'
import { Dot } from '../../ui'
import type { WorkProject } from './data'

/**
 * Project combobox for every place that records time. Projects are grouped by team, so two projects with the same
 * name stay distinguishable before saving. Keyboard: arrows move, Enter chooses, Escape closes.
 */
export function ProjectPicker({
  projects,
  value,
  onChange,
  labelledBy,
  disabled,
}: {
  projects: WorkProject[]
  value: WorkProject | null
  onChange: (project: WorkProject) => void
  labelledBy: string
  disabled?: boolean
}) {
  const t = useT()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)
  const listId = useId()
  const root = useRef<HTMLDivElement>(null)
  const button = useRef<HTMLButtonElement>(null)
  const search = useRef<HTMLInputElement>(null)

  const matches = useMemo(() => {
    const text = query.trim().toLocaleLowerCase()
    return projects.filter(
      project =>
        !text ||
        project.name.toLocaleLowerCase().includes(text) ||
        project.team.name.toLocaleLowerCase().includes(text),
    )
  }, [projects, query])
  const groups = useMemo(() => {
    const map = new Map<string, WorkProject[]>()
    for (const project of matches) map.set(project.team.name, [...(map.get(project.team.name) ?? []), project])
    return [...map.entries()]
  }, [matches])

  useEffect(() => {
    if (!open) return
    search.current?.focus()
    const close = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', close)
    return () => document.removeEventListener('pointerdown', close)
  }, [open])

  useEffect(() => {
    if (!open) return
    const list = root.current?.querySelector<HTMLElement>('.picker-list')
    const option = list?.querySelector('.is-active')
    if (!list || !option) return
    const viewport = list.getBoundingClientRect()
    const row = option.getBoundingClientRect()
    if (row.top < viewport.top) list.scrollTop -= viewport.top - row.top
    if (row.bottom > viewport.bottom) list.scrollTop += row.bottom - viewport.bottom
  }, [open, active])

  const choose = (project: WorkProject) => {
    onChange(project)
    setOpen(false)
    setQuery('')
    button.current?.focus()
  }
  const optionId = (index: number) => `${listId}-${index}`

  return (
    <div
      className="picker"
      ref={root}
      onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}
    >
      <button
        ref={button}
        type="button"
        className="picker-button"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={`${labelledBy} ${listId}-value`}
        disabled={disabled}
        onClick={() => {
          setOpen(!open)
          setActive(0)
        }}
      >
        <span id={`${listId}-value`} className="picker-value">
          {value ? (
            <>
              <Dot color={projectColorHex(value.color)} />
              <span className="picker-name">{value.name}</span>
              <small>{value.team.name}</small>
            </>
          ) : (
            <span className="muted">{t('work.chooseProject')}</span>
          )}
        </span>
        <ChevronDown size={16} aria-hidden="true" />
      </button>
      {open && (
        <div className="picker-pop">
          <div className="input-wrap">
            <Search size={16} aria-hidden="true" />
            <input
              ref={search}
              className="input"
              value={query}
              placeholder={t('work.searchProjects')}
              aria-label={t('work.searchProjects')}
              role="combobox"
              aria-expanded="true"
              aria-controls={listId}
              aria-activedescendant={matches.length ? optionId(active) : undefined}
              onChange={event => {
                setQuery(event.target.value)
                setActive(0)
              }}
              onKeyDown={event => {
                if (event.key === 'ArrowDown') {
                  event.preventDefault()
                  setActive(index => Math.max(0, Math.min(matches.length - 1, index + 1)))
                }
                if (event.key === 'ArrowUp') {
                  event.preventDefault()
                  setActive(index => Math.max(0, index - 1))
                }
                if (event.key === 'Enter' && matches[active]) {
                  event.preventDefault()
                  choose(matches[active]!)
                }
                if (event.key === 'Escape') {
                  event.preventDefault()
                  setOpen(false)
                  button.current?.focus()
                }
              }}
            />
          </div>
          <ul className="picker-list" role="listbox" tabIndex={-1} id={listId} aria-labelledby={labelledBy}>
            {!matches.length && (
              <li className="picker-empty" role="presentation">
                {t('work.noProjects')}
              </li>
            )}
            {groups.map(([team, items]) => (
              <li key={team} role="presentation">
                <p className="picker-group" aria-hidden="true">
                  {team}
                </p>
                <ul role="group" aria-label={team}>
                  {items.map(project => {
                    const index = matches.indexOf(project)
                    const selected = value?.id === project.id
                    return (
                      <li
                        key={project.id}
                        id={optionId(index)}
                        role="option"
                        aria-selected={selected}
                        className={index === active ? 'is-active' : ''}
                        onPointerDown={event => event.preventDefault()}
                        onClick={() => choose(project)}
                      >
                        <Dot color={projectColorHex(project.color)} />
                        <span>{project.name}</span>
                        {selected && <Check size={16} aria-hidden="true" />}
                      </li>
                    )
                  })}
                </ul>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}
