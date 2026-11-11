import { useState, type FormEvent } from 'react'
import {
  Archive,
  ArchiveRestore,
  ArrowLeft,
  BarChart3,
  Check,
  ChevronRight,
  Pencil,
  Plug,
  Plus,
  Settings,
  Tags as TagsIcon,
  Trash2,
  UserPlus,
  Users,
} from 'lucide-react'
import { Link, NavLink, Outlet, useNavigate, useOutletContext, useParams } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { useUser } from '../../app/session'
import { canManage, teamLogoUrl, type Tag, type Team } from '../../lib/api'
import { useTeamLogo } from '../../lib/account'
import { projectColorHex, projectColorKeys, projectColors } from '../../lib/projectColors'
import { useArchiveTag, useCreateTag, useRestoreTag, useTags, useUpdateTag } from '../../lib/tags'
import { useArchiveTeam, useDeleteTeam, useTeamSummary, useTeams, useUpdateTeam } from '../../lib/teams'
import { Button, Dialog, Dot, Empty, Field, LoadError, Loading, Notice } from '../../ui'
import { PictureEditor } from '../../ui/PictureEditor'
import { useToast } from '../../ui/toast'
import './teams.css'

export const teamMessages = {
  pl: {
    title: 'Zespoły',
    create: 'Nowy zespół',
    none: 'Nie należysz jeszcze do żadnego zespołu',
    noneBody: 'Utwórz zespół albo otwórz zaproszenie.',
    start: 'Zacznij',
    archived: 'Zarchiwizowany',
    members: 'Członkowie',
    invitations: 'Zaproszenia',
    tags: 'Tagi',
    settings: 'Ustawienia',
    integrations: 'Jira',
    reports: 'Raport zespołu',
    back: 'Wszystkie zespoły',
    notFound: 'Nie znaleziono zespołu albo nie masz już do niego dostępu.',
    zone: 'Strefa {zone}',
    invite: 'Zaproś',
    identity: 'Dane zespołu',
    reporting: 'Raporty i aktywność',
    name: 'Nazwa zespołu',
    timezone: 'Strefa czasowa',
    timezoneHint: 'Nowa strefa dotyczy nowych wpisów i bieżących dat. Zapisane daty pracy się nie zmieniają.',
    live: 'Pokaż zarządzającym, kto teraz pracuje',
    liveHint:
      'Podgląd aktywnych timerów w raporcie zespołu: osoba, projekt i czas. Wyłączenie ukrywa go przed wszystkimi.',
    color: 'Kolor zespołu',
    noColor: 'Bez koloru',
    rules: 'Reguły wpisów',
    requireDescription: 'Wymagaj opisu pracy',
    requireTag: 'Wymagaj tagu lub zadania Jira',
    rulesHint:
      'Reguły dotyczą wpisów, które członkowie zapisują sami. Korekty właściciela i administratora są od nich wolne. Wymóg tagu lub zadania działa, dopóki zespół ma aktywny tag albo połączoną Jirę.',
    weekStart: 'Pierwszy dzień tygodnia w raportach zespołu',
    monday: 'Poniedziałek',
    sunday: 'Niedziela',
    weekly: 'Wysyłaj cotygodniowy raport PDF e-mailem',
    weeklyHint:
      'W poniedziałek od 8:00 (czas zespołu) właściciel i administratorzy dostaną raport z poprzedniego tygodnia. Każda osoba może go wyłączyć w koncie.',
    save: 'Zapisz zmiany',
    saving: 'Zapisywanie…',
    saved: 'Zapisano ustawienia zespołu.',
    needName: 'Wpisz nazwę zespołu.',
    archivedReadOnly: 'Zespół jest zarchiwizowany, więc jego ustawień nie można zmieniać.',
    logo: 'Logo zespołu',
    logoHint: 'Logo widać na liście zespołów i w raporcie zespołu, zamiast kropki z kolorem.',
    addLogo: 'Dodaj logo',
    changeLogo: 'Zmień logo',
    removeLogo: 'Usuń logo',
    newLogo: 'Nowe logo zespołu',
    danger: 'Operacje nieodwracalne',
    archive: 'Zarchiwizuj zespół',
    archiveTitle: 'Zarchiwizować zespół {name}?',
    archiveBody:
      'Historia pracy i raporty pozostaną dostępne. Nie będzie można dodawać ani zmieniać wpisów, projektów, zaproszeń ani ustawień. Wszystkie aktywne timery muszą być wcześniej zatrzymane. Zarchiwizowany zespół można później tylko usunąć. Tej operacji nie można cofnąć.',
    archiveDo: 'Zarchiwizuj na stałe',
    archiving: 'Archiwizowanie…',
    archivedDone: 'Zespół zarchiwizowany.',
    delete: 'Usuń zespół',
    deleteTitle: 'Usunąć zespół {name} na zawsze?',
    deleteBody: 'Zespół zostanie trwale usunięty razem z projektami i wpisami czasu. Tej operacji nie można cofnąć.',
    activeMembers: 'Aktywni członkowie',
    projects: 'Projekty',
    entries: 'Wpisy czasu',
    timersRunning: 'W zespole działają timery. Poproś członków o ich zatrzymanie przed usunięciem.',
    confirmName: 'Wpisz nazwę zespołu, aby potwierdzić',
    deleteDo: 'Usuń zespół na zawsze',
    deleting: 'Usuwanie…',
    deleted: 'Zespół usunięty.',
    ownerOnly: 'Usunąć zespół może tylko właściciel.',
    tagsIntro: 'Tag opisuje rodzaj pracy (np. spotkanie, testy). Każdy wpis może mieć jeden tag.',
    addTag: 'Dodaj tag',
    tagName: 'Nazwa tagu',
    tagColor: 'Kolor',
    editTag: 'Edytuj tag',
    noTags: 'Zespół nie ma jeszcze tagów.',
    showArchivedTags: 'Pokaż zarchiwizowane',
    archiveTag: 'Archiwizuj',
    restoreTag: 'Przywróć',
    edit: 'Edytuj',
    tagSaved: 'Zapisano tag.',
    needTag: 'Wpisz nazwę tagu (do 40 znaków).',
  },
  en: {
    title: 'Teams',
    create: 'New team',
    none: 'You do not belong to any team yet',
    noneBody: 'Create a team or open an invitation.',
    start: 'Get started',
    archived: 'Archived',
    members: 'Members',
    invitations: 'Invitations',
    tags: 'Tags',
    settings: 'Settings',
    integrations: 'Jira',
    reports: 'Team report',
    back: 'All teams',
    notFound: 'Team not found or you no longer have access.',
    zone: 'Time zone {zone}',
    invite: 'Invite',
    identity: 'Team details',
    reporting: 'Reports and activity',
    name: 'Team name',
    timezone: 'Time zone',
    timezoneHint: 'A new zone applies to new entries and current dates. Saved work dates do not change.',
    live: 'Show managers who is working now',
    liveHint: 'Running timers in the team report: person, project and time. Turning it off hides it from everyone.',
    color: 'Team colour',
    noColor: 'No colour',
    rules: 'Entry rules',
    requireDescription: 'Require a work description',
    requireTag: 'Require a tag or a Jira issue',
    rulesHint:
      'Rules apply to entries members save themselves. Corrections by the owner and admins are exempt. The tag-or-issue rule works while the team has an active tag or a connected Jira.',
    weekStart: 'First day of the week in team reports',
    monday: 'Monday',
    sunday: 'Sunday',
    weekly: 'Send a weekly PDF report by e-mail',
    weeklyHint:
      'On Monday from 8:00 (team time) the owner and admins get the report of the previous week. Everyone can turn it off in their account.',
    save: 'Save changes',
    saving: 'Saving…',
    saved: 'Team settings saved.',
    needName: 'Enter the team name.',
    archivedReadOnly: 'The team is archived, so its settings cannot change.',
    logo: 'Team logo',
    logoHint: 'The logo appears in the team list and the team report instead of the colour dot.',
    addLogo: 'Add logo',
    changeLogo: 'Change logo',
    removeLogo: 'Remove logo',
    newLogo: 'New team logo',
    danger: 'Irreversible actions',
    archive: 'Archive the team',
    archiveTitle: 'Archive {name}?',
    archiveBody:
      'Work history and reports stay available. Entries, projects, invitations and settings can no longer be added or changed. All running timers must be stopped first. An archived team can later only be deleted. This cannot be undone.',
    archiveDo: 'Archive permanently',
    archiving: 'Archiving…',
    archivedDone: 'Team archived.',
    delete: 'Delete the team',
    deleteTitle: 'Delete {name} forever?',
    deleteBody: 'The team is permanently deleted with its projects and time entries. This cannot be undone.',
    activeMembers: 'Active members',
    projects: 'Projects',
    entries: 'Time entries',
    timersRunning: 'Timers are running in the team. Ask members to stop them before deleting.',
    confirmName: 'Type the team name to confirm',
    deleteDo: 'Delete the team forever',
    deleting: 'Deleting…',
    deleted: 'Team deleted.',
    ownerOnly: 'Only the owner can delete the team.',
    tagsIntro: 'A tag describes the kind of work (e.g. meeting, testing). Each entry can have one tag.',
    addTag: 'Add tag',
    tagName: 'Tag name',
    tagColor: 'Colour',
    editTag: 'Edit tag',
    noTags: 'The team has no tags yet.',
    showArchivedTags: 'Show archived',
    archiveTag: 'Archive',
    restoreTag: 'Restore',
    edit: 'Edit',
    tagSaved: 'Tag saved.',
    needTag: 'Enter a tag name (up to 40 characters).',
  },
}

export function TeamMark({ team, size = 36 }: { team: Team; size?: number }) {
  return team.logoUpdatedAt ? (
    <img className="team-mark" src={teamLogoUrl(team.id, team.logoUpdatedAt)} alt="" width={size} height={size} />
  ) : (
    <span
      className="team-mark team-mark-letter"
      style={{ width: size, height: size, background: projectColorHex(team.color) ?? undefined }}
      aria-hidden="true"
    >
      {team.name.trim()[0]?.toUpperCase()}
    </span>
  )
}

/** List of the person's teams with role and state. */
export function TeamsList() {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(teamMessages)
  const teams = useTeams(user.id)
  if (teams.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (teams.isError)
    return (
      <div className="page">
        <LoadError onRetry={() => void teams.refetch()} message={teams.error.message} />
      </div>
    )
  return (
    <div className="page teams">
      <header className="page-head">
        <h1>{m('title')}</h1>
        <Link className="btn btn-primary" to="/app/start">
          <Plus size={16} aria-hidden="true" />
          {m('create')}
        </Link>
      </header>
      {!teams.data.length ? (
        <Empty
          title={m('none')}
          action={
            <Link className="btn btn-primary" to="/app/start">
              {m('start')}
            </Link>
          }
        >
          {m('noneBody')}
        </Empty>
      ) : (
        <ul className="team-list">
          {teams.data.map(team => (
            <li key={team.id}>
              <Link className="card team-row" to={`/app/teams/${encodeURIComponent(team.id)}`}>
                <TeamMark team={team} />
                <span className="team-row-main">
                  <b>{team.name}</b>
                  <small>
                    {t(`role.${team.role}`)} · {m('zone', { zone: team.timezone })}
                    {team.archivedAt ? ` · ${m('archived')}` : ''}
                  </small>
                </span>
                <ChevronRight size={18} aria-hidden="true" />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export interface TeamContext {
  team: Team
}
export const useTeamContext = () => useOutletContext<TeamContext>()

/** Frame of one team: header and sections by role, each with its own address. */
export function TeamLayout() {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(teamMessages)
  const { teamId = '' } = useParams()
  const teams = useTeams(user.id)
  const team = teams.data?.find(item => item.id === teamId)
  if (teams.isPending)
    return (
      <div className="page">
        <Loading />
      </div>
    )
  if (!team)
    return (
      <div className="page">
        <Empty
          title={m('notFound')}
          action={
            <Link className="btn" to="/app/teams">
              {m('back')}
            </Link>
          }
        />
      </div>
    )
  const manage = canManage(team.role)
  const base = `/app/teams/${encodeURIComponent(team.id)}`
  const tabs = [
    { to: base, label: m('members'), icon: <Users size={16} aria-hidden="true" />, end: true },
    ...(manage
      ? [
          { to: `${base}/invitations`, label: m('invitations'), icon: <UserPlus size={16} aria-hidden="true" /> },
          { to: `${base}/tags`, label: m('tags'), icon: <TagsIcon size={16} aria-hidden="true" /> },
        ]
      : []),
    { to: `${base}/integrations`, label: m('integrations'), icon: <Plug size={16} aria-hidden="true" /> },
    ...(manage
      ? [{ to: `${base}/settings`, label: m('settings'), icon: <Settings size={16} aria-hidden="true" /> }]
      : []),
  ]
  return (
    <div className="page team-page">
      <header className="page-head">
        <div className="stack-sm">
          <Link className="back-link" to="/app/teams">
            <ArrowLeft size={16} aria-hidden="true" />
            {m('back')}
          </Link>
          <div className="team-head">
            <TeamMark team={team} size={52} />
            <div>
              <h1>{team.name}</h1>
              <p className="muted">
                {t(`role.${team.role}`)} · {m('zone', { zone: team.timezone })}
                {team.archivedAt && (
                  <>
                    {' '}
                    · <span className="badge">{m('archived')}</span>
                  </>
                )}
              </p>
            </div>
          </div>
        </div>
        {manage && (
          <Link className="btn" to={`${base}/reports`}>
            <BarChart3 size={16} aria-hidden="true" />
            {m('reports')}
          </Link>
        )}
      </header>
      <nav className="tabs" aria-label={team.name}>
        {tabs.map(tab => (
          <NavLink key={tab.to} to={tab.to} end={tab.end}>
            {tab.icon}
            {tab.label}
          </NavLink>
        ))}
      </nav>
      <Outlet context={{ team } satisfies TeamContext} />
    </div>
  )
}

export function TeamSettings() {
  const { team } = useTeamContext()
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(teamMessages)
  const toast = useToast()
  const navigate = useNavigate()
  const update = useUpdateTeam(user.id)
  const archived = !!team.archivedAt
  const [name, setName] = useState(team.name)
  const [timezone, setTimezone] = useState(team.timezone)
  const [live, setLive] = useState(team.activeTimersVisible)
  const [requireDescription, setRequireDescription] = useState(team.requireDescription)
  const [requireTag, setRequireTag] = useState(team.requireTagOrIssue)
  const [weekStart, setWeekStart] = useState(team.weekStart)
  const [weekly, setWeekly] = useState(team.weeklyReport)
  const [color, setColor] = useState(team.color ?? '')
  const [error, setError] = useState('')
  const [dialog, setDialog] = useState<'archive' | 'delete' | null>(null)
  if (!canManage(team.role)) return <Notice tone="info">{m('ownerOnly')}</Notice>
  const zones = [...new Set([team.timezone, 'UTC', ...Intl.supportedValuesOf('timeZone')])].sort()
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim()) {
      setError(m('needName'))
      return
    }
    setError('')
    update.mutate(
      {
        teamId: team.id,
        name: name.trim(),
        timezone,
        activeTimersVisible: live,
        requireDescription,
        requireTagOrIssue: requireTag,
        weekStart,
        weeklyReport: weekly,
        ...(color ? { color } : { clearColor: true }),
      },
      { onSuccess: () => toast(m('saved')) },
    )
  }
  return (
    <div className="settings-grid">
      <form className="stack team-settings-form" onSubmit={submit} noValidate>
        <fieldset className="settings-fields stack" disabled={archived || update.isPending}>
          <section className="card stack" aria-labelledby="team-identity-title">
            <h2 id="team-identity-title">{m('identity')}</h2>
            <Field label={m('name')} error={error || undefined}>
              {props => (
                <input
                  {...props}
                  className="input"
                  value={name}
                  maxLength={255}
                  onChange={event => setName(event.target.value)}
                />
              )}
            </Field>
            <Field label={m('timezone')} hint={m('timezoneHint')}>
              {props => (
                <select
                  {...props}
                  className="select input"
                  value={timezone}
                  onChange={event => setTimezone(event.target.value)}
                >
                  {zones.map(zone => (
                    <option key={zone}>{zone}</option>
                  ))}
                </select>
              )}
            </Field>
            <TeamPalette value={color} onChange={setColor} locale={locale} label={m('color')} noColor={m('noColor')} />
          </section>
          <section className="card stack" aria-labelledby="team-rules-title">
            <h2 id="team-rules-title">{m('rules')}</h2>
            <label className="check">
              <input
                type="checkbox"
                checked={requireDescription}
                onChange={event => setRequireDescription(event.target.checked)}
              />
              {m('requireDescription')}
            </label>
            <label className="check">
              <input type="checkbox" checked={requireTag} onChange={event => setRequireTag(event.target.checked)} />
              {m('requireTag')}
            </label>
            <p className="field-hint">{m('rulesHint')}</p>
          </section>
          <section className="card stack" aria-labelledby="team-reporting-title">
            <h2 id="team-reporting-title">{m('reporting')}</h2>
            <Field label={m('weekStart')}>
              {props => (
                <select
                  {...props}
                  className="select input"
                  value={weekStart}
                  onChange={event => setWeekStart(event.target.value as 'MONDAY' | 'SUNDAY')}
                >
                  <option value="MONDAY">{m('monday')}</option>
                  <option value="SUNDAY">{m('sunday')}</option>
                </select>
              )}
            </Field>
            <div className="stack-sm">
              <label className="check">
                <input type="checkbox" checked={live} onChange={event => setLive(event.target.checked)} />
                {m('live')}
              </label>
              <p className="field-hint">{m('liveHint')}</p>
            </div>
            <div className="stack-sm">
              <label className="check">
                <input type="checkbox" checked={weekly} onChange={event => setWeekly(event.target.checked)} />
                {m('weekly')}
              </label>
              <p className="field-hint">{m('weeklyHint')}</p>
            </div>
          </section>
        </fieldset>
        {archived && <Notice tone="info">{m('archivedReadOnly')}</Notice>}
        {update.isError && <Notice tone="error">{update.error.message}</Notice>}
        {!archived && (
          <Button type="submit" variant="primary" className="settings-save" pending={update.isPending}>
            {m(update.isPending ? 'saving' : 'save')}
          </Button>
        )}
      </form>
      <div className="stack">
        {!archived && <LogoCard team={team} />}
        <section className="card stack danger-zone" aria-labelledby="danger-title">
          <h2 id="danger-title">{m('danger')}</h2>
          {!archived && (
            <Button onClick={() => setDialog('archive')}>
              <Archive size={16} aria-hidden="true" />
              {m('archive')}
            </Button>
          )}
          {team.role === 'OWNER' ? (
            <Button variant="ghost" className="danger-text" onClick={() => setDialog('delete')}>
              <Trash2 size={16} aria-hidden="true" />
              {m('delete')}
            </Button>
          ) : (
            <p className="field-hint">{m('ownerOnly')}</p>
          )}
        </section>
      </div>
      {dialog === 'archive' && <ArchiveTeamDialog team={team} onClose={() => setDialog(null)} />}
      {dialog === 'delete' && (
        <DeleteTeamDialog team={team} onClose={() => setDialog(null)} onDeleted={() => navigate('/app/teams')} />
      )}
    </div>
  )
}

const englishColours = {
  VIOLET: 'Violet',
  BLUE: 'Blue',
  TEAL: 'Teal',
  GREEN: 'Green',
  AMBER: 'Amber',
  ORANGE: 'Orange',
  RED: 'Red',
  PINK: 'Pink',
  SLATE: 'Slate',
  BROWN: 'Brown',
}

function TeamPalette({
  value,
  onChange,
  locale,
  label,
  noColor,
}: {
  value: string
  onChange: (value: string) => void
  locale: 'pl' | 'en'
  label: string
  noColor: string
}) {
  const colours = projectColorKeys.map(key => ({
    key,
    name: locale === 'pl' ? projectColors[key].label : englishColours[key],
    hex: projectColors[key].hex,
  }))
  const selected = colours.find(colour => colour.key === value)
  const options = [{ key: '', name: noColor, hex: undefined }, ...colours]
  return (
    <fieldset className="field team-palette">
      <legend className="field-label">{label}</legend>
      <div className="team-palette-options">
        {options.map(option => (
          <label key={option.key} className="team-swatch" title={option.name}>
            <input
              type="radio"
              name="team-color"
              aria-label={option.name}
              checked={value === option.key}
              onChange={() => onChange(option.key)}
            />
            <span className="team-swatch-colour" style={{ backgroundColor: option.hex }} aria-hidden="true">
              {!option.hex && <span className="team-swatch-slash" />}
            </span>
            {value === option.key && (
              <span className="team-swatch-check" aria-hidden="true">
                <Check size={12} />
              </span>
            )}
          </label>
        ))}
      </div>
      <span className="field-hint">{selected?.name ?? noColor}</span>
    </fieldset>
  )
}

function LogoCard({ team }: { team: Team }) {
  const user = useUser()
  const m = useMessages(teamMessages)
  const { upload, remove } = useTeamLogo(user.id)
  const [editing, setEditing] = useState(false)
  return (
    <section className="card stack" aria-labelledby="logo-title">
      <h2 id="logo-title">{m('logo')}</h2>
      <p className="field-hint">{m('logoHint')}</p>
      {editing ? (
        <PictureEditor
          label={m('newLogo')}
          saving={upload.isPending}
          error={upload.error?.message}
          onSave={picture => upload.mutate({ teamId: team.id, picture }, { onSuccess: () => setEditing(false) })}
          onCancel={() => {
            upload.reset()
            setEditing(false)
          }}
        />
      ) : (
        <div className="row-inline">
          <TeamMark team={team} size={56} />
          <Button
            onClick={() => {
              remove.reset()
              setEditing(true)
            }}
          >
            {m(team.logoUpdatedAt ? 'changeLogo' : 'addLogo')}
          </Button>
          {team.logoUpdatedAt && (
            <Button variant="ghost" pending={remove.isPending} onClick={() => remove.mutate(team.id)}>
              {m('removeLogo')}
            </Button>
          )}
        </div>
      )}
      {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
    </section>
  )
}

function ArchiveTeamDialog({ team, onClose }: { team: Team; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(teamMessages)
  const toast = useToast()
  const archive = useArchiveTeam(user.id)
  return (
    <Dialog open title={m('archiveTitle', { name: team.name })} onClose={onClose} busy={archive.isPending}>
      <p>{m('archiveBody')}</p>
      {archive.isError && <Notice tone="error">{archive.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={archive.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={archive.isPending}
          onClick={() =>
            archive.mutate(team.id, {
              onSuccess: () => {
                toast(m('archivedDone'))
                onClose()
              },
            })
          }
        >
          {m(archive.isPending ? 'archiving' : 'archiveDo')}
        </Button>
      </div>
    </Dialog>
  )
}

function DeleteTeamDialog({ team, onClose, onDeleted }: { team: Team; onClose: () => void; onDeleted: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(teamMessages)
  const toast = useToast()
  const remove = useDeleteTeam(user.id)
  const summary = useTeamSummary(user.id, team.id, true)
  const [confirmation, setConfirmation] = useState('')
  return (
    <Dialog open title={m('deleteTitle', { name: team.name })} onClose={onClose} busy={remove.isPending}>
      <p>{m('deleteBody')}</p>
      {summary.isPending ? (
        <Loading />
      ) : summary.isError ? (
        <Notice tone="error">{summary.error.message}</Notice>
      ) : (
        <>
          <dl className="facts">
            <div>
              <dt>{m('activeMembers')}</dt>
              <dd>{summary.data.activeMembers}</dd>
            </div>
            <div>
              <dt>{m('projects')}</dt>
              <dd>{summary.data.projects}</dd>
            </div>
            <div>
              <dt>{m('entries')}</dt>
              <dd>{summary.data.timeEntries}</dd>
            </div>
          </dl>
          {summary.data.activeTimers && <Notice tone="warning">{m('timersRunning')}</Notice>}
        </>
      )}
      <Field label={m('confirmName')}>
        {props => (
          <input
            {...props}
            className="input"
            value={confirmation}
            autoComplete="off"
            disabled={remove.isPending}
            onChange={event => setConfirmation(event.target.value)}
          />
        )}
      </Field>
      {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={remove.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={remove.isPending}
          disabled={confirmation !== team.name || !summary.data || summary.data.activeTimers}
          onClick={() =>
            void remove.mutateAsync(team.id).then(
              () => {
                toast(m('deleted'))
                onDeleted()
              },
              () => undefined,
            )
          }
        >
          {m(remove.isPending ? 'deleting' : 'deleteDo')}
        </Button>
      </div>
    </Dialog>
  )
}

/** Work tags: one per entry, colour, archive and restore. */
export function TeamTags() {
  const { team } = useTeamContext()
  const user = useUser()
  const m = useMessages(teamMessages)
  const [archivedToo, setArchivedToo] = useState(false)
  const tags = useTags(user.id, team.id, archivedToo)
  const archive = useArchiveTag(user.id, team.id)
  const restore = useRestoreTag(user.id, team.id)
  const [editing, setEditing] = useState<Tag | 'new' | null>(null)
  const readOnly = !!team.archivedAt
  return (
    <section className="stack" aria-label={m('tags')}>
      <div className="toolbar">
        <p className="muted">{m('tagsIntro')}</p>
        {!readOnly && (
          <Button variant="primary" onClick={() => setEditing('new')}>
            <Plus size={16} aria-hidden="true" />
            {m('addTag')}
          </Button>
        )}
      </div>
      <label className="check">
        <input type="checkbox" checked={archivedToo} onChange={event => setArchivedToo(event.target.checked)} />
        {m('showArchivedTags')}
      </label>
      {tags.isPending ? (
        <Loading />
      ) : tags.isError ? (
        <LoadError onRetry={() => void tags.refetch()} message={tags.error.message} />
      ) : !tags.data.length ? (
        <Empty title={m('noTags')} />
      ) : (
        <ul className="card list-card">
          {tags.data.map(tag => (
            <li key={tag.id}>
              <span className="list-main">
                <Dot color={projectColorHex(tag.color)} />
                <b>{tag.name}</b>
                {tag.archivedAt && <span className="badge">{m('archived')}</span>}
              </span>
              {!readOnly && (
                <span className="row-actions">
                  {!tag.archivedAt && (
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`${m('edit')}: ${tag.name}`}
                      onClick={() => setEditing(tag)}
                    >
                      <Pencil size={16} />
                    </button>
                  )}
                  {tag.archivedAt ? (
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`${m('restoreTag')}: ${tag.name}`}
                      disabled={restore.isPending}
                      onClick={() => restore.mutate({ teamId: team.id, id: tag.id })}
                    >
                      <ArchiveRestore size={16} />
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="icon-btn"
                      aria-label={`${m('archiveTag')}: ${tag.name}`}
                      disabled={archive.isPending}
                      onClick={() => archive.mutate({ teamId: team.id, id: tag.id })}
                    >
                      <Archive size={16} />
                    </button>
                  )}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {(archive.isError || restore.isError) && (
        <Notice tone="error">{(archive.error ?? restore.error)!.message}</Notice>
      )}
      {editing && <TagDialog team={team} tag={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />}
    </section>
  )
}

function TagDialog({ team, tag, onClose }: { team: Team; tag: Tag | null; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(teamMessages)
  const toast = useToast()
  const create = useCreateTag(user.id, team.id)
  const update = useUpdateTag(user.id, team.id)
  const [name, setName] = useState(tag?.name ?? '')
  const [color, setColor] = useState(tag?.color ?? '')
  const [error, setError] = useState('')
  const busy = create.isPending || update.isPending
  const failure = create.error ?? update.error
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!name.trim() || name.trim().length > 40) {
      setError(m('needTag'))
      return
    }
    const done = {
      onSuccess: () => {
        toast(m('tagSaved'))
        onClose()
      },
    }
    if (tag) update.mutate({ teamId: team.id, id: tag.id, name: name.trim(), color: color || null }, done)
    else create.mutate({ teamId: team.id, name: name.trim(), color: color || null }, done)
  }
  return (
    <Dialog open title={m(tag ? 'editTag' : 'addTag')} onClose={onClose} busy={busy}>
      <form className="stack" onSubmit={submit} noValidate>
        <Field label={m('tagName')} error={error || undefined}>
          {props => (
            <input
              {...props}
              className="input"
              value={name}
              maxLength={40}
              autoFocus
              onChange={event => {
                setName(event.target.value)
                setError('')
              }}
            />
          )}
        </Field>
        <fieldset className="field">
          <legend className="field-label">
            {m('tagColor')} <i>{t('common.optional')}</i>
          </legend>
          <div className="swatches">
            <label className="swatch">
              <input type="radio" name="tag-color" checked={!color} onChange={() => setColor('')} />
              <span className="swatch-dot swatch-none" aria-hidden="true" />
              {m('noColor')}
            </label>
            {projectColorKeys.map(key => (
              <label key={key} className="swatch">
                <input type="radio" name="tag-color" checked={color === key} onChange={() => setColor(key)} />
                <span className="swatch-dot" style={{ background: projectColors[key].hex }} aria-hidden="true" />
                {projectColors[key].label}
              </label>
            ))}
          </div>
        </fieldset>
        {failure && <Notice tone="error">{failure.message}</Notice>}
        <div className="dialog-actions">
          <Button onClick={onClose} disabled={busy}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="primary" pending={busy}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}
