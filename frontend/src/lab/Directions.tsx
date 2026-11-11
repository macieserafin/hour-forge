import { useEffect, useRef, useState } from 'react'
import { BarChart3, CalendarDays, Check, ChevronDown, Clock3, FolderKanban, HelpCircle, Pause, Play, Plus, Search, Square, Users } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { AnvilMark } from '../brand/Anvil'
import { clock, entries as sampleEntries, hours, parseDuration, projects, teams, undated as sampleUndated, week, type SampleEntry } from './data'
import './directions.css'

const directions = [
  { id: 'forge', name: 'Kuźnia', note: 'Ciepły, wyrazisty. Głęboki fiolet, żar bursztynu i stal morska. Space Grotesk w nagłówkach, Inter w treści, ciemne menu boczne, duże liczby.' },
  { id: 'atelier', name: 'Pracownia', note: 'Spokojny, redakcyjny. Fiolet, szałwia i koral. Szeryfowy Fraunces w nagłówkach, Manrope w treści, menu u góry, dużo powietrza.' },
  { id: 'precision', name: 'Precyzja', note: 'Zwarty, narzędziowy. Fiolet, cyjan i limonka. IBM Plex Sans z cyframi Plex Mono, wąska szyna ikon, gęstsze dane.' },
] as const
type Direction = typeof directions[number]['id']

const nav = [
  { label: 'Moja praca', icon: Clock3, active: true },
  { label: 'Projekty', icon: FolderKanban },
  { label: 'Raporty', icon: BarChart3 },
  { label: 'Zespoły', icon: Users },
]

const dateChoices = ['Dziś', 'Wczoraj', 'Inny dzień', 'Nie pamiętam daty'] as const

/** Design lab: three visual directions of the same "Moja praca" screen, for the owner's choice before P02 continues. */
export function Directions() {
  const [search, setSearch] = useSearchParams()
  const direction = (directions.find(item => item.id === search.get('v'))?.id ?? 'forge') as Direction
  const theme = search.get('theme') === 'dark' ? 'dark' : 'light'
  const set = (key: string, value: string) => setSearch(previous => { const next = new URLSearchParams(previous); next.set(key, value); return next }, { replace: true })

  return <div className="lab" data-direction={direction} data-theme={theme}>
    <div className="lab-bar" role="toolbar" aria-label="Wybór kierunku">
      {directions.map((item, index) => <button key={item.id} aria-pressed={item.id === direction} onClick={() => set('v', item.id)}>{index + 1}. {item.name}</button>)}
      <button aria-pressed={theme === 'dark'} onClick={() => set('theme', theme === 'dark' ? 'light' : 'dark')}>{theme === 'dark' ? 'Motyw ciemny' : 'Motyw jasny'}</button>
      <p>{directions.find(item => item.id === direction)!.note}</p>
    </div>
    <MyWork key={direction} />
  </div>
}

function MyWork() {
  const [scope, setScope] = useState('all')
  const [running, setRunning] = useState<{ started: number; paused: boolean; pausedFor: number; pausedAt: number | null } | null>(null)
  const [now, setNow] = useState(Date.now)
  const [list, setList] = useState<SampleEntry[]>(sampleEntries)
  const [undated, setUndated] = useState(sampleUndated)
  const [fresh, setFresh] = useState<string | null>(null)
  const [duration, setDuration] = useState('1h 30m')
  const [when, setWhen] = useState<typeof dateChoices[number]>('Dziś')
  const [note, setNote] = useState('')
  const parsed = parseDuration(duration)
  const measureCard = useRef<HTMLElement>(null)
  const [cardVisible, setCardVisible] = useState(true)
  useEffect(() => {
    const card = measureCard.current
    if (!card) return
    const observer = new IntersectionObserver(([entry]) => setCardVisible(entry!.isIntersecting), { threshold: 0.2 })
    observer.observe(card)
    return () => observer.disconnect()
  }, [])
  const start = () => { setNow(Date.now()); setRunning({ started: Date.now(), paused: false, pausedFor: 0, pausedAt: null }) }

  useEffect(() => {
    if (!running || running.paused) return
    const tick = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(tick)
  }, [running])

  const elapsed = running ? Math.floor(((running.paused ? running.pausedAt! : now) - running.started - running.pausedFor) / 1000) + 3125 : 0
  const visibleTeams = scope === 'all' ? teams : teams.filter(team => team.id === scope)
  const today = visibleTeams.reduce((total, team) => total + team.today, 0)
  const weekTotal = visibleTeams.reduce((total, team) => total + team.week, 0)
  const max = Math.max(...week.map(day => day.seconds))

  const save = () => {
    if (!parsed) return
    const entry: SampleEntry = { id: `n${Date.now()}`, team: 'Studio Atlas', project: 'Strona WWW', color: '#0F766E', description: note || 'Bez opisu', seconds: parsed,
      date: when === 'Nie pamiętam daty' ? null : when === 'Dziś' ? 'dziś' : when === 'Wczoraj' ? 'wczoraj' : '6 lis', kind: 'manual' }
    if (entry.date === null) setUndated(value => ({ seconds: value.seconds + parsed, count: value.count + 1 }))
    else setList(value => [entry, ...value])
    setFresh(entry.id)
    setNote('')
  }

  return <div className="shell">
    <nav className="shell-nav" aria-label="Nawigacja główna">
      <a className="shell-brand" href="#"><AnvilMark size={30} /><span>Hour<b>Forge</b></span></a>
      <ul>{nav.map(item => <li key={item.label}><a href="#" aria-current={item.active ? 'page' : undefined}><item.icon size={19} aria-hidden="true" /><span>{item.label}</span></a></li>)}</ul>
      <div className="shell-nav-end">
        <button className="icon-only" aria-label="Szukaj i polecenia (Ctrl K)"><Search size={18} /></button>
        <button className="icon-only" aria-label="Pomoc"><HelpCircle size={18} /></button>
        <span className="avatar" aria-label="Konto: Maja Nowak">MN</span>
      </div>
    </nav>

    {running && !cardVisible && <div className="timer-dock" role="status" aria-live="off">
      <span className={`pulse ${running.paused ? 'paused' : ''}`} aria-hidden="true" />
      <span className="timer-dock-text"><b>{running.paused ? 'Wstrzymano' : 'Mierzysz czas'}</b> Strona WWW · Studio Atlas</span>
      <span className="digits">{clock(elapsed)}</span>
    </div>}

    <main className="page">
      <header className="page-head">
        <div>
          <p className="eyebrow">Środa, 11 listopada</p>
          <h1>Moja praca</h1>
        </div>
        <div className="scope" role="group" aria-label="Zakres">
          <button aria-pressed={scope === 'all'} onClick={() => setScope('all')}>Wszystkie zespoły</button>
          {teams.map(team => <button key={team.id} aria-pressed={scope === team.id} onClick={() => setScope(team.id)}>{team.name}</button>)}
        </div>
      </header>

      <section className="record" aria-label="Zapis czasu">
        <article ref={measureCard} className={`card measure ${running ? 'is-running' : ''}`}>
          <h2><Clock3 size={18} aria-hidden="true" />Mierz czas</h2>
          {running ? <>
            <p className="measure-project"><span className="dot" style={{ background: '#0F766E' }} />Strona WWW <small>Studio Atlas</small></p>
            <p className="big-digits" aria-label={`Upłynęło ${hours(elapsed)}`}>{clock(elapsed)}</p>
            <div className="row">
              <button className="btn" onClick={() => setRunning(value => value && (value.paused
                ? { ...value, paused: false, pausedFor: value.pausedFor + Date.now() - value.pausedAt!, pausedAt: null }
                : { ...value, paused: true, pausedAt: Date.now() }))}>
                {running.paused ? <Play size={16} /> : <Pause size={16} />}{running.paused ? 'Wznów' : 'Wstrzymaj'}</button>
              <button className="btn btn-primary" onClick={() => { const id = `t${Date.now()}`; setRunning(null); setList(value => [{ id, team: 'Studio Atlas', project: 'Strona WWW', color: '#0F766E', description: 'Praca z timerem', seconds: elapsed, date: 'dziś', kind: 'timer' }, ...value]); setFresh(id) }}>
                <Square size={15} />Zakończ i zapisz</button>
            </div>
          </> : <>
            <label className="field"><span>Projekt</span>
              <button className="picker" type="button"><span className="dot" style={{ background: '#0F766E' }} />Strona WWW <small>Studio Atlas</small><ChevronDown size={16} /></button></label>
            <label className="field"><span>Nad czym pracujesz? <i>opcjonalnie</i></span><input placeholder="np. poprawki formularza" /></label>
            <button className="btn btn-primary btn-wide" onClick={start}><Play size={16} />Rozpocznij</button>
            <div className="quick"><p>Wróć do ostatniej pracy</p><ul>{projects.slice(1, 4).map(project =>
              <li key={project.id}><button type="button" onClick={start} aria-label={`Rozpocznij: ${project.name}, ${project.team}`}><span className="dot" style={{ background: project.color }} />{project.name}<small>{project.team}</small></button></li>)}</ul></div>
          </>}
        </article>

        <article className="card add">
          <h2><Plus size={18} aria-hidden="true" />Dodaj czas</h2>
          <div className="add-grid">
            <label className="field"><span>Ile?</span><input value={duration} onChange={event => setDuration(event.target.value)} inputMode="text" />
              <small className={parsed ? 'hint' : 'hint error'}>{parsed ? `= ${hours(parsed)}` : 'Wpisz np. 45m, 1h 30m albo 1,5'}</small></label>
            <label className="field"><span>Projekt</span>
              <button className="picker" type="button"><span className="dot" style={{ background: '#0F766E' }} />Strona WWW <small>Studio Atlas</small><ChevronDown size={16} /></button></label>
          </div>
          <fieldset className="field when"><legend>Kiedy?</legend>
            {dateChoices.map(choice => <button key={choice} type="button" aria-pressed={when === choice} onClick={() => setWhen(choice)}>{choice === 'Inny dzień' && <CalendarDays size={14} />}{choice}</button>)}
          </fieldset>
          <label className="field"><span>Opis <i>opcjonalnie</i></span><input value={note} onChange={event => setNote(event.target.value)} placeholder="Co zostało zrobione?" /></label>
          <button className="btn btn-accent btn-wide" disabled={!parsed} onClick={save}><Check size={16} />Zapisz {parsed ? hours(parsed) : ''}</button>
        </article>
      </section>

      <section className="totals" aria-label="Podsumowanie">
        <div className="stat"><span>Dzisiaj</span><b>{hours(today)}</b></div>
        <div className="stat"><span>Ten tydzień</span><b>{hours(weekTotal)}</b></div>
        <div className="stat stat-undated"><span>Bez daty</span><b>{hours(undated.seconds)}</b><small>{undated.count} wpisy, poza sumami dni</small></div>
      </section>

      <div className="split">
        <section className="card" aria-labelledby="week-title">
          <h2 id="week-title">Tydzień 9–15 listopada</h2>
          <ol className="week">{week.map(day => <li key={day.day} className={day.today ? 'today' : ''}>
            <span className="bar" style={{ ['--h' as string]: `${max ? day.seconds / max : 0}` }} aria-hidden="true" />
            <b>{day.seconds ? hours(day.seconds) : '—'}</b><span>{day.day} {day.date}</span></li>)}</ol>
          <ul className="by-team">{visibleTeams.map(team => <li key={team.id}><span>{team.name}<small>{team.role} · {team.zone}</small></span><b>{hours(team.week)}</b></li>)}</ul>
        </section>

        <section className="card" aria-labelledby="recent-title">
          <h2 id="recent-title">Ostatnie wpisy</h2>
          <ul className="entries">{list.slice(0, 5).map(entry => <li key={entry.id} className={entry.id === fresh ? 'fresh' : ''}>
            <span className="dot" style={{ background: entry.color }} />
            <span className="entry-main"><b>{entry.description}</b><small>{entry.project} · {entry.team} · {entry.kind === 'timer' ? 'timer' : 'ręcznie'}</small></span>
            <span className="entry-side"><b className="digits">{hours(entry.seconds)}</b><small>{entry.date}</small></span>
          </li>)}</ul>
          <p className="more"><a className="text-link" href="#">Cała historia</a> · <a className="text-link" href="#">Wpisy bez daty ({undated.count})</a></p>
        </section>
      </div>
    </main>
  </div>
}
