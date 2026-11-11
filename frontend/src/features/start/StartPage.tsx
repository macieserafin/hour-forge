import { useState, type FormEvent } from 'react'
import { ArrowLeft, ArrowRight, Link2, Users } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { useMessages } from '../../content/i18n'
import { useUser } from '../../app/session'
import { useCreateTeam, useTeams } from '../../lib/teams'
import { Button, Field, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import './start.css'

const messages = {
  pl: {
    title: 'Od czego zaczynasz?',
    intro: 'Wybierz, jak chcesz pracować. Później możesz dołączyć do kolejnych zespołów albo zaprosić innych.',
    team: 'Tworzę zespół',
    'team.body': 'Wspólne projekty, role i raporty. Zaprosisz osoby po utworzeniu zespołu.',
    invite: 'Mam zaproszenie',
    'invite.body': 'Wklej link z wiadomości albo poproś osobę zarządzającą zespołem o nowy.',
    'team.name': 'Nazwa zespołu',
    zone: 'Strefa czasowa',
    'zone.hint': 'Według niej liczymy „dzisiaj” i daty pracy z timera.',
    create: 'Utwórz zespół',
    creating: 'Tworzenie…',
    'invite.link': 'Link z zaproszenia',
    'invite.go': 'Otwórz zaproszenie',
    'invite.invalid': 'To nie wygląda na link zaproszenia HourForge. Skopiuj cały adres z wiadomości.',
    'need.name': 'Wpisz nazwę.',
    done: 'Utworzono zespół.',
    back: 'Wróć do swojej pracy',
  },
  en: {
    title: 'How do you start?',
    intro: 'Choose how you want to work. Later you can join more teams or invite others.',
    team: "I'm creating a team",
    'team.body': 'Shared projects, roles and reports. You invite people after the team is created.',
    invite: 'I have an invitation',
    'invite.body': 'Paste the link from the message or ask the person managing the team for a new one.',
    'team.name': 'Team name',
    zone: 'Time zone',
    'zone.hint': 'Used for "today" and the work dates of the timer.',
    create: 'Create team',
    creating: 'Creating…',
    'invite.link': 'Invitation link',
    'invite.go': 'Open the invitation',
    'invite.invalid': 'This does not look like a HourForge invitation link. Copy the whole address from the message.',
    'need.name': 'Enter a name.',
    done: 'Team created.',
    back: 'Back to your work',
  },
}

type Mode = 'team' | 'invite'
const browserZone = () => {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Europe/Warsaw'
  } catch {
    return 'Europe/Warsaw'
  }
}

export function StartPage() {
  const m = useMessages(messages)
  const user = useUser()
  const teams = useTeams(user.id)
  const [mode, setMode] = useState<Mode | null>(null)
  const hasTeams = !!teams.data?.length
  return (
    <div className="page start">
      <header className="page-head">
        <div>
          <h1>{m('title')}</h1>
          <p className="muted">{m('intro')}</p>
        </div>
      </header>
      <div className="start-choices" role="group" aria-label={m('title')}>
        <Choice
          icon={<Users size={22} />}
          title={m('team')}
          body={m('team.body')}
          active={mode === 'team'}
          onSelect={() => setMode('team')}
        />
        <Choice
          icon={<Link2 size={22} />}
          title={m('invite')}
          body={m('invite.body')}
          active={mode === 'invite'}
          onSelect={() => setMode('invite')}
        />
      </div>
      {mode === 'invite' ? <InviteForm /> : mode && <CreateForm />}
      {hasTeams && (
        <Link className="back-link" to="/app/work">
          <ArrowLeft size={16} aria-hidden="true" />
          {m('back')}
        </Link>
      )}
    </div>
  )
}

function Choice({
  icon,
  title,
  body,
  active,
  onSelect,
}: {
  icon: React.ReactNode
  title: string
  body: string
  active: boolean
  onSelect: () => void
}) {
  return (
    <button type="button" className="start-choice" aria-pressed={active} onClick={onSelect}>
      <span className="start-icon" aria-hidden="true">
        {icon}
      </span>
      <b>{title}</b>
      <span className="muted">{body}</span>
    </button>
  )
}

function CreateForm() {
  const m = useMessages(messages)
  const user = useUser()
  const navigate = useNavigate()
  const toast = useToast()
  const createTeam = useCreateTeam(user.id)
  const [name, setName] = useState('')
  const [zone, setZone] = useState(browserZone)
  const [error, setError] = useState('')
  const zones = [...new Set([zone, 'UTC', ...Intl.supportedValuesOf('timeZone')])].sort()

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      setError(m('need.name'))
      return
    }
    setError('')
    createTeam.mutate(
      { name: name.trim(), timezone: zone },
      {
        onSuccess: team => {
          toast(m('done'))
          navigate(`/app/work?team=${encodeURIComponent(team.id)}`, { replace: true })
        },
      },
    )
  }

  return (
    <form className="card stack start-form" onSubmit={submit} noValidate>
      <Field label={m('team.name')} error={error || undefined}>
        {props => (
          <input
            {...props}
            className="input"
            value={name}
            disabled={createTeam.isPending}
            maxLength={255}
            required
            autoFocus
            onChange={event => {
              setName(event.target.value)
              setError('')
            }}
          />
        )}
      </Field>
      <Field label={m('zone')} hint={m('zone.hint')}>
        {props => (
          <select
            {...props}
            className="select input"
            value={zone}
            disabled={createTeam.isPending}
            onChange={event => setZone(event.target.value)}
          >
            {zones.map(item => (
              <option key={item}>{item}</option>
            ))}
          </select>
        )}
      </Field>
      {createTeam.isError && <Notice tone="error">{createTeam.error.message}</Notice>}
      <Button type="submit" variant="primary" pending={createTeam.isPending}>
        {createTeam.isPending ? (
          m('creating')
        ) : (
          <>
            {m('create')}
            <ArrowRight size={16} aria-hidden="true" />
          </>
        )}
      </Button>
    </form>
  )
}

function InviteForm() {
  const m = useMessages(messages)
  const navigate = useNavigate()
  const [link, setLink] = useState('')
  const [error, setError] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const token = /\/invite\/([A-Za-z0-9_-]{1,255})/.exec(link.trim())?.[1]
    if (!token) {
      setError(m('invite.invalid'))
      return
    }
    navigate(`/invite/${token}`)
  }
  return (
    <form className="card stack start-form" onSubmit={submit} noValidate>
      <Field label={m('invite.link')} error={error || undefined}>
        {props => (
          <input
            {...props}
            className="input"
            value={link}
            inputMode="url"
            placeholder="https://…/invite/…"
            autoFocus
            onChange={event => {
              setLink(event.target.value)
              setError('')
            }}
          />
        )}
      </Field>
      <Button type="submit" variant="primary">
        {m('invite.go')}
        <ArrowRight size={16} aria-hidden="true" />
      </Button>
    </form>
  )
}
