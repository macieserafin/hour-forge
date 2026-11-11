import { useId, useState, type FormEvent } from 'react'
import {
  Check,
  ChevronLeft,
  ChevronRight,
  Copy,
  Crown,
  Link2,
  LogOut,
  Pencil,
  Plus,
  ShieldCheck,
  ShieldOff,
  Timer,
  Trash2,
  UserMinus,
  UserRound,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { formatDuration, formatWorkDate, MAX_ENTRY_SECONDS, parseDuration } from '../../domain/duration'
import { useUser } from '../../app/session'
import { canManage, type InvitationSummary, type Team, type TeamMember, type TimeEntryHistoryItem } from '../../lib/api'
import { useAddMemberEntry, useCorrectEntry, useDeleteMemberEntry, useMemberTimeEntries } from '../../lib/corrections'
import { useEndMembership, useTeamMembers } from '../../lib/members'
import { projectColorHex } from '../../lib/projectColors'
import { useProjects } from '../../lib/projects'
import {
  useChangeRole,
  useCreateInvitation,
  useInvitations,
  useRevokeInvitation,
  useTransferOwnership,
  useUpdateProfile,
} from '../../lib/teams'
import { teamToday, workDateRange } from '../../lib/time'
import { Button, Dialog, Dot, Empty, Field, LoadError, Loading, Notice } from '../../ui'
import { useToast } from '../../ui/toast'
import { TagField } from '../entries/EntryExtras'
import { useTeamContext } from './Teams'

const messages = {
  pl: {
    intro: 'Osoby, z którymi pracujesz w tym zespole.',
    introManage: 'Zarządzaj dostępem i rolami w zespole.',
    former: 'Pokaż byłych członków',
    count: 'Osób: {count}',
    profile: 'Mój profil w zespole',
    leave: 'Opuść zespół',
    you: 'To Ty',
    joined: 'Dołączenie: {date}',
    left: 'Odejście: {date}',
    formerBadge: 'Były członek',
    fte: '{percent}% etatu',
    corrections: 'Korekty czasu',
    remove: 'Usuń z zespołu',
    makeAdmin: 'Nadaj administratora',
    takeAdmin: 'Odbierz administratora',
    transfer: 'Przekaż własność',
    madeAdmin: '{name} jest administratorem zespołu.',
    tookAdmin: '{name} nie jest już administratorem.',
    prev: 'Poprzednia',
    next: 'Następna',
    page: 'Strona {page} z {pages}',
    profileBody: 'Stanowisko i wymiar etatu widzą tylko właściciel i administratorzy zespołu.',
    jobTitle: 'Stanowisko',
    fte_label: 'Wymiar etatu w procentach',
    fteError: 'Wymiar etatu to liczba całkowita od 1 do 100.',
    saveProfile: 'Zapisz profil',
    profileSaved: 'Zapisano Twój profil w zespole.',
    transferTitle: 'Przekazać własność zespołu?',
    transferBody:
      '{name} zostanie właścicielem zespołu {team}. Ty staniesz się zwykłym członkiem: stracisz zarządzanie członkami, projektami, zaproszeniami i raportami. Później możesz opuścić zespół.',
    transferDo: 'Przekaż własność',
    transferred: 'Własność przekazana.',
    leaveTitle: 'Opuścić zespół {team}?',
    removeTitle: 'Usunąć {name} z zespołu?',
    endBody:
      'Historia pracy pozostanie w raportach zespołu. Uruchomiony timer będzie można zakończyć, ale nie wznowić. Ponowne dołączenie wymaga ważnego zaproszenia.',
    leaveDo: 'Opuść zespół',
    removeDo: 'Usuń z zespołu',
    removed: 'Zakończono dostęp: {name}.',
    saving: 'Zapisywanie…',
    corrTitle: 'Korekty czasu: {name}',
    corrBody:
      'Każda zmiana jest oznaczona jako poprawiona przez Ciebie i widoczna dla tej osoby. Reguły zespołu nie dotyczą korekt.',
    corrAdd: 'Dodaj wpis',
    corrEmpty: 'Ta osoba nie ma jeszcze zapisanych wpisów.',
    corrEdit: 'Popraw wpis',
    corrNew: 'Dodaj wpis za {name}',
    corrDelete: 'Usunąć wpis {project} ({duration})?',
    corrDeleted: 'Wpis usunięty.',
    corrDeleteDo: 'Usuń wpis',
    corrSaved: 'Zapisano korektę.',
    undated: 'Bez daty',
    back: 'Wróć do listy',
    edited: 'poprawił(a): {name}',
    inviteTitle: 'Zaproś do zespołu',
    inviteBody: 'Osoba z linkiem dołączy po zalogowaniu lub założeniu konta.',
    expires: 'Ważne do',
    expiresHint: 'Najwyżej 30 dni od dziś.',
    uses: 'Limit użyć',
    usesHint: 'Puste pole oznacza jedno użycie.',
    inviteEmail: 'Adres e-mail osoby',
    inviteEmailHint:
      'Wyślemy wiadomość z linkiem. Zaproszenie nie jest przypisane do adresu: kto ma link, może go użyć.',
    create: 'Utwórz link',
    creating: 'Tworzenie…',
    badExpiry: 'Wybierz datę w ciągu najbliższych 30 dni.',
    badUses: 'Limit użyć musi być dodatnią liczbą całkowitą.',
    link: 'Link zaproszenia',
    copy: 'Kopiuj link',
    copied: 'Skopiowano',
    copyFailed: 'Nie udało się skopiować. Zaznacz i skopiuj link ręcznie.',
    linkOnce: 'Link widać tylko teraz. Lista zaproszeń nie pozwala go odzyskać.',
    mailed: 'Wysłaliśmy zaproszenie na adres {email}.',
    noLimit: 'Bez limitu',
    invites: 'Utworzone zaproszenia',
    noInvites: 'Nie utworzono jeszcze zaproszeń.',
    created: 'Utworzono',
    valid: 'Ważne do',
    used: 'Użycia',
    status: 'Stan',
    revoke: 'Unieważnij',
    revokeTitle: 'Unieważnić zaproszenie?',
    revokeBody: 'Link przestanie działać. Osoby, które już dołączyły, zachowają dostęp.',
    revoked: 'Zaproszenie unieważnione.',
    ACTIVE: 'Aktywne',
    EXPIRED: 'Wygasłe',
    EXHAUSTED: 'Wykorzystane',
    REVOKED: 'Unieważnione',
    soloWarn:
      'To jest Twoja przestrzeń. Osoby, które dołączą jako administratorzy, zobaczą zapisaną w niej pracę w raportach zespołu.',
  },
  en: {
    intro: 'People you work with in this team.',
    introManage: 'Manage access and roles in the team.',
    former: 'Show former members',
    count: 'People: {count}',
    profile: 'My team profile',
    leave: 'Leave the team',
    you: 'You',
    joined: 'Joined: {date}',
    left: 'Left: {date}',
    formerBadge: 'Former member',
    fte: '{percent}% FTE',
    corrections: 'Time corrections',
    remove: 'Remove from the team',
    makeAdmin: 'Make admin',
    takeAdmin: 'Remove admin role',
    transfer: 'Transfer ownership',
    madeAdmin: '{name} is now an admin of the team.',
    tookAdmin: '{name} is no longer an admin.',
    prev: 'Previous',
    next: 'Next',
    page: 'Page {page} of {pages}',
    profileBody: 'Job title and FTE are visible only to the owner and admins of the team.',
    jobTitle: 'Job title',
    fte_label: 'FTE in percent',
    fteError: 'FTE is a whole number from 1 to 100.',
    saveProfile: 'Save profile',
    profileSaved: 'Your team profile was saved.',
    transferTitle: 'Transfer team ownership?',
    transferBody:
      '{name} becomes the owner of {team}. You become a regular member and lose management of members, projects, invitations and reports. You can leave the team later.',
    transferDo: 'Transfer ownership',
    transferred: 'Ownership transferred.',
    leaveTitle: 'Leave {team}?',
    removeTitle: 'Remove {name} from the team?',
    endBody:
      'Work history stays in the team reports. A running timer can be stopped but not resumed. Joining again needs a valid invitation.',
    leaveDo: 'Leave the team',
    removeDo: 'Remove from the team',
    removed: 'Access ended: {name}.',
    saving: 'Saving…',
    corrTitle: 'Time corrections: {name}',
    corrBody:
      'Every change is marked as edited by you and visible to this person. Team rules do not apply to corrections.',
    corrAdd: 'Add entry',
    corrEmpty: 'This person has no saved entries yet.',
    corrEdit: 'Correct entry',
    corrNew: 'Add an entry for {name}',
    corrDelete: 'Delete the entry {project} ({duration})?',
    corrDeleted: 'Entry deleted.',
    corrDeleteDo: 'Delete entry',
    corrSaved: 'Correction saved.',
    undated: 'No date',
    back: 'Back to the list',
    edited: 'edited by {name}',
    inviteTitle: 'Invite to the team',
    inviteBody: 'A person with the link joins after signing in or creating an account.',
    expires: 'Valid until',
    expiresHint: 'At most 30 days from today.',
    uses: 'Use limit',
    usesHint: 'Empty means one use.',
    inviteEmail: "Person's e-mail",
    inviteEmailHint:
      'We send a message with the link. The invitation is not tied to the address: anyone with the link can use it.',
    create: 'Create link',
    creating: 'Creating…',
    badExpiry: 'Choose a date within the next 30 days.',
    badUses: 'The use limit must be a positive whole number.',
    link: 'Invitation link',
    copy: 'Copy link',
    copied: 'Copied',
    copyFailed: 'Copying failed. Select and copy the link manually.',
    linkOnce: 'The link is shown only now. The invitation list cannot recover it.',
    mailed: 'We sent the invitation to {email}.',
    noLimit: 'No limit',
    invites: 'Created invitations',
    noInvites: 'No invitations created yet.',
    created: 'Created',
    valid: 'Valid until',
    used: 'Uses',
    status: 'State',
    revoke: 'Revoke',
    revokeTitle: 'Revoke the invitation?',
    revokeBody: 'The link stops working. People who already joined keep their access.',
    revoked: 'Invitation revoked.',
    ACTIVE: 'Active',
    EXPIRED: 'Expired',
    EXHAUSTED: 'Used up',
    REVOKED: 'Revoked',
    soloWarn: 'This is your space. People who join as admins will see the work saved in it in the team reports.',
  },
}

const dateTime = (iso: string, locale: string, timeZone?: string) =>
  new Date(iso).toLocaleString(locale === 'pl' ? 'pl-PL' : 'en-GB', {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone,
  })

/** Members: roles by OWNER/ADMIN/MEMBER rules, removal, ownership, own profile, leaving and corrections. */
export function TeamMembers() {
  const { team } = useTeamContext()
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const navigate = useNavigate()
  const owner = team.role === 'OWNER'
  const manager = canManage(team.role)
  const [former, setFormer] = useState(false)
  const [page, setPage] = useState(0)
  const members = useTeamMembers(user.id, team.id, former, page)
  const changeRole = useChangeRole(user.id, team.id)
  const [dialog, setDialog] = useState<
    { kind: 'profile' } | { kind: 'leave' } | { kind: 'remove' | 'transfer' | 'corrections'; member: TeamMember } | null
  >(null)
  const me = members.data?.content.find(member => member.userId === user.id)

  return (
    <section className="stack" aria-label={t('nav.teams')}>
      <div className="toolbar">
        <p className="muted">{m(manager ? 'introManage' : 'intro')}</p>
        <div className="row-inline">
          {!team.archivedAt && (
            <Button small onClick={() => setDialog({ kind: 'profile' })}>
              <UserRound size={15} aria-hidden="true" />
              {m('profile')}
            </Button>
          )}
          {!owner && (
            <Button small variant="ghost" onClick={() => setDialog({ kind: 'leave' })}>
              <LogOut size={15} aria-hidden="true" />
              {m('leave')}
            </Button>
          )}
        </div>
      </div>
      {manager && (
        <label className="check">
          <input
            type="checkbox"
            checked={former}
            onChange={event => {
              setFormer(event.target.checked)
              setPage(0)
            }}
          />
          {m('former')}
        </label>
      )}
      {changeRole.isError && <Notice tone="error">{changeRole.error.message}</Notice>}
      {members.isPending ? (
        <Loading />
      ) : members.isError ? (
        <LoadError onRetry={() => void members.refetch()} message={members.error.message} />
      ) : (
        <>
          <p className="muted" role="status">
            {m('count', { count: members.data.totalElements })}
          </p>
          <ul className="card list-card people">
            {members.data.content.map(member => {
              const self = member.userId === user.id
              const canTouch =
                manager &&
                !self &&
                !member.leftAt &&
                member.role !== 'OWNER' &&
                (owner || member.role === 'MEMBER') &&
                !team.archivedAt
              return (
                <li key={member.userId}>
                  <span className="person-avatar" aria-hidden="true">
                    {member.displayName
                      .trim()
                      .split(/\s+/)
                      .slice(0, 2)
                      .map(part => part[0]?.toUpperCase())
                      .join('')}
                  </span>
                  <span className="list-main stack-xs">
                    <b>
                      {member.displayName}
                      {self && <span className="badge">{m('you')}</span>}
                      {member.leftAt && <span className="badge">{m('formerBadge')}</span>}
                    </b>
                    <small>
                      {t(`role.${member.role}`)}
                      {manager && member.jobTitle ? ` · ${member.jobTitle}` : ''}
                      {manager && member.ftePercent ? ` · ${m('fte', { percent: member.ftePercent })}` : ''}
                      {' · '}
                      {member.leftAt
                        ? m('left', { date: dateTime(member.leftAt, locale, team.timezone) })
                        : m('joined', { date: dateTime(member.joinedAt, locale, team.timezone) })}
                    </small>
                  </span>
                  {manager && (
                    <span className="row-actions">
                      {!self && (
                        <button
                          type="button"
                          className="icon-btn"
                          aria-label={`${m('corrections')}: ${member.displayName}`}
                          onClick={() => setDialog({ kind: 'corrections', member })}
                        >
                          <Timer size={16} />
                        </button>
                      )}
                      {canTouch && owner && (
                        <button
                          type="button"
                          className="icon-btn"
                          disabled={changeRole.isPending}
                          aria-label={`${m(member.role === 'ADMIN' ? 'takeAdmin' : 'makeAdmin')}: ${member.displayName}`}
                          onClick={() =>
                            changeRole.mutate(
                              { targetUserId: member.userId, role: member.role === 'ADMIN' ? 'MEMBER' : 'ADMIN' },
                              {
                                onSuccess: () =>
                                  toast(
                                    m(member.role === 'ADMIN' ? 'tookAdmin' : 'madeAdmin', {
                                      name: member.displayName,
                                    }),
                                  ),
                              },
                            )
                          }
                        >
                          {member.role === 'ADMIN' ? <ShieldOff size={16} /> : <ShieldCheck size={16} />}
                        </button>
                      )}
                      {canTouch && owner && (
                        <button
                          type="button"
                          className="icon-btn"
                          aria-label={`${m('transfer')}: ${member.displayName}`}
                          onClick={() => setDialog({ kind: 'transfer', member })}
                        >
                          <Crown size={16} />
                        </button>
                      )}
                      {canTouch && (
                        <button
                          type="button"
                          className="icon-btn"
                          aria-label={`${m('remove')}: ${member.displayName}`}
                          onClick={() => setDialog({ kind: 'remove', member })}
                        >
                          <UserMinus size={16} />
                        </button>
                      )}
                    </span>
                  )}
                </li>
              )
            })}
          </ul>
          {members.data.totalPages > 1 && (
            <Pager
              page={page}
              pages={members.data.totalPages}
              busy={members.isFetching}
              onPage={setPage}
              label={m('page', { page: page + 1, pages: members.data.totalPages })}
            />
          )}
        </>
      )}
      {dialog?.kind === 'profile' && <ProfileDialog team={team} current={me} onClose={() => setDialog(null)} />}
      {dialog?.kind === 'transfer' && (
        <TransferDialog team={team} member={dialog.member} onClose={() => setDialog(null)} />
      )}
      {(dialog?.kind === 'leave' || dialog?.kind === 'remove') && (
        <EndDialog
          team={team}
          member={dialog.kind === 'remove' ? dialog.member : null}
          onClose={() => setDialog(null)}
          onDone={name => {
            setDialog(null)
            if (name === null) navigate('/app/teams')
            else {
              toast(m('removed', { name }))
              if (page > 0 && members.data?.content.length === 1) setPage(page - 1)
            }
          }}
        />
      )}
      {dialog?.kind === 'corrections' && (
        <CorrectionsDialog team={team} member={dialog.member} onClose={() => setDialog(null)} />
      )}
    </section>
  )
}

function Pager({
  page,
  pages,
  busy,
  onPage,
  label,
}: {
  page: number
  pages: number
  busy: boolean
  onPage: (page: number) => void
  label: string
}) {
  const m = useMessages(messages)
  return (
    <nav className="pager" aria-label={label}>
      <Button small disabled={page === 0 || busy} onClick={() => onPage(page - 1)}>
        <ChevronLeft size={15} aria-hidden="true" />
        {m('prev')}
      </Button>
      <span>{label}</span>
      <Button small disabled={page + 1 >= pages || busy} onClick={() => onPage(page + 1)}>
        {m('next')}
        <ChevronRight size={15} aria-hidden="true" />
      </Button>
    </nav>
  )
}

function ProfileDialog({ team, current, onClose }: { team: Team; current?: TeamMember; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const save = useUpdateProfile(user.id, team.id)
  const [title, setTitle] = useState(current?.jobTitle ?? '')
  const [fte, setFte] = useState(current?.ftePercent ? String(current.ftePercent) : '')
  const [error, setError] = useState('')
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const percent = fte.trim() === '' ? null : Number(fte)
    if (percent !== null && (!Number.isInteger(percent) || percent < 1 || percent > 100)) {
      setError(m('fteError'))
      return
    }
    save.mutate(
      { jobTitle: title.trim() || null, ftePercent: percent },
      {
        onSuccess: () => {
          toast(m('profileSaved'))
          onClose()
        },
      },
    )
  }
  return (
    <Dialog open title={m('profile')} onClose={onClose} busy={save.isPending}>
      <p className="muted">{m('profileBody')}</p>
      <form className="stack" onSubmit={submit} noValidate>
        <Field label={m('jobTitle')} optional>
          {props => (
            <input
              {...props}
              className="input"
              maxLength={80}
              value={title}
              onChange={event => setTitle(event.target.value)}
            />
          )}
        </Field>
        <Field label={m('fte_label')} optional error={error || undefined}>
          {props => (
            <input
              {...props}
              className="input"
              type="number"
              min={1}
              max={100}
              step={1}
              inputMode="numeric"
              value={fte}
              onChange={event => {
                setFte(event.target.value)
                setError('')
              }}
            />
          )}
        </Field>
        {save.isError && <Notice tone="error">{save.error.message}</Notice>}
        <div className="dialog-actions">
          <Button onClick={onClose} disabled={save.isPending}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" variant="primary" pending={save.isPending}>
            {m('saveProfile')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function TransferDialog({ team, member, onClose }: { team: Team; member: TeamMember; onClose: () => void }) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const transfer = useTransferOwnership(user.id, team.id)
  return (
    <Dialog open title={m('transferTitle')} onClose={onClose} busy={transfer.isPending}>
      <p>{m('transferBody', { name: member.displayName, team: team.name })}</p>
      {transfer.isError && <Notice tone="error">{transfer.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={transfer.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={transfer.isPending}
          onClick={() =>
            transfer.mutate(member.userId, {
              onSuccess: () => {
                toast(m('transferred'))
                onClose()
              },
            })
          }
        >
          {m('transferDo')}
        </Button>
      </div>
    </Dialog>
  )
}

function EndDialog({
  team,
  member,
  onClose,
  onDone,
}: {
  team: Team
  member: TeamMember | null
  onClose: () => void
  onDone: (name: string | null) => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const end = useEndMembership(user.id, team.id)
  return (
    <Dialog
      open
      title={member ? m('removeTitle', { name: member.displayName }) : m('leaveTitle', { team: team.name })}
      onClose={onClose}
      busy={end.isPending}
    >
      <p>{m('endBody')}</p>
      {end.isError && <Notice tone="error">{end.error.message}</Notice>}
      <div className="dialog-actions">
        <Button onClick={onClose} disabled={end.isPending}>
          {t('common.cancel')}
        </Button>
        <Button
          variant="danger"
          pending={end.isPending}
          onClick={() =>
            void end.mutateAsync(member?.userId ?? null).then(
              () => onDone(member?.displayName ?? null),
              () => undefined,
            )
          }
        >
          {m(member ? 'removeDo' : 'leaveDo')}
        </Button>
      </div>
    </Dialog>
  )
}

/** Corrections of another person's time by OWNER/ADMIN, with versions and the "edited by" mark. */
export function CorrectionsDialog({ team, member, onClose }: { team: Team; member: TeamMember; onClose: () => void }) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [view, setView] = useState<
    | { kind: 'list' }
    | { kind: 'form'; entry: TimeEntryHistoryItem | null }
    | { kind: 'delete'; entry: TimeEntryHistoryItem }
  >({ kind: 'list' })
  const entries = useMemberTimeEntries(user.id, team.id, member.userId, page)
  const remove = useDeleteMemberEntry(user.id, team.id, member.userId)
  const back = () => setView({ kind: 'list' })
  if (view.kind === 'form') return <CorrectionForm team={team} member={member} entry={view.entry} onDone={back} />
  return (
    <Dialog open title={m('corrTitle', { name: member.displayName })} onClose={onClose} busy={remove.isPending}>
      {view.kind === 'delete' ? (
        <>
          <p>
            {m('corrDelete', { project: view.entry.projectName, duration: formatDuration(view.entry.durationSeconds) })}
          </p>
          {remove.isError && <Notice tone="error">{remove.error.message}</Notice>}
          <div className="dialog-actions">
            <Button onClick={back} disabled={remove.isPending}>
              {m('back')}
            </Button>
            <Button
              variant="danger"
              pending={remove.isPending}
              onClick={() =>
                remove.mutate(
                  { teamId: team.id, id: view.entry.id },
                  {
                    onSuccess: () => {
                      toast(m('corrDeleted'))
                      back()
                    },
                  },
                )
              }
            >
              <Trash2 size={16} aria-hidden="true" />
              {m('corrDeleteDo')}
            </Button>
          </div>
        </>
      ) : (
        <>
          <p className="muted">{m('corrBody')}</p>
          {!member.leftAt && !team.archivedAt && (
            <Button small onClick={() => setView({ kind: 'form', entry: null })}>
              <Plus size={15} aria-hidden="true" />
              {m('corrAdd')}
            </Button>
          )}
          {entries.isPending ? (
            <Loading />
          ) : entries.isError ? (
            <LoadError onRetry={() => void entries.refetch()} message={entries.error.message} />
          ) : !entries.data.content.length ? (
            <p className="muted">{m('corrEmpty')}</p>
          ) : (
            <ul className="entries">
              {entries.data.content.map(entry => (
                <li key={entry.id}>
                  <Dot color={projectColorHex(entry.projectColor)} />
                  <span className="entry-main">
                    <b>
                      {entry.projectName}
                      {entry.tagName ? ` · ${entry.tagName}` : ''}
                    </b>
                    <small>
                      {entry.workDate ? formatWorkDate(entry.workDate, locale, true) : m('undated')} ·{' '}
                      {entry.description || t('common.noDescription')}
                      {entry.editedByName ? ` · ${m('edited', { name: entry.editedByName })}` : ''}
                    </small>
                  </span>
                  <b className="num">{formatDuration(entry.durationSeconds)}</b>
                  {!team.archivedAt && (
                    <span className="row-actions">
                      <button
                        type="button"
                        className="icon-btn"
                        aria-label={`${m('corrEdit')}: ${entry.projectName} ${entry.workDate ?? m('undated')}`}
                        onClick={() => setView({ kind: 'form', entry })}
                      >
                        <Pencil size={16} />
                      </button>
                      <button
                        type="button"
                        className="icon-btn"
                        aria-label={`${m('remove')}: ${entry.projectName} ${entry.workDate ?? m('undated')}`}
                        onClick={() => setView({ kind: 'delete', entry })}
                      >
                        <Trash2 size={16} />
                      </button>
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
          {entries.data && entries.data.totalPages > 1 && (
            <Pager
              page={page}
              pages={entries.data.totalPages}
              busy={entries.isFetching}
              onPage={setPage}
              label={m('page', { page: page + 1, pages: entries.data.totalPages })}
            />
          )}
        </>
      )}
    </Dialog>
  )
}

function CorrectionForm({
  team,
  member,
  entry,
  onDone,
}: {
  team: Team
  member: TeamMember
  entry: TimeEntryHistoryItem | null
  onDone: () => void
}) {
  const user = useUser()
  const { t } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const projects = useProjects(user.id, team.id, false)
  const add = useAddMemberEntry(user.id, team.id, member.userId)
  const correct = useCorrectEntry(user.id, team.id, member.userId)
  const [projectId, setProjectId] = useState(entry?.projectId ?? '')
  const [undated, setUndated] = useState(entry ? entry.workDate === null : false)
  const [date, setDate] = useState(entry?.workDate ?? teamToday(team.timezone))
  const [duration, setDuration] = useState(
    entry ? `${Math.floor(entry.durationSeconds / 3600)}h ${Math.round((entry.durationSeconds % 3600) / 60)}m` : '',
  )
  const [description, setDescription] = useState(entry?.description ?? '')
  const [tagId, setTagId] = useState(entry?.tagId ?? '')
  const id = useId()
  const seconds = parseDuration(duration)
  const tooLong = seconds !== null && seconds > MAX_ENTRY_SECONDS
  const range = workDateRange(team.timezone)
  const busy = add.isPending || correct.isPending
  const failure = add.error ?? correct.error
  const chosen = projectId || projects.data?.[0]?.id || ''
  const isTimer = entry?.entryType === 'TIMER'
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!chosen || !seconds || tooLong || (!undated && !date)) return
    const body = {
      projectId: chosen,
      workDate: undated && !isTimer ? null : date,
      durationSeconds: seconds,
      description: description.trim() || null,
      tagId: tagId || null,
    }
    const done = {
      onSuccess: () => {
        toast(m('corrSaved'))
        onDone()
      },
    }
    if (entry) correct.mutate({ teamId: team.id, id: entry.id, ...body, expectedVersion: entry.version }, done)
    else add.mutate({ teamId: team.id, memberId: member.userId, ...body }, done)
  }
  return (
    <Dialog
      open
      title={entry ? m('corrEdit') : m('corrNew', { name: member.displayName })}
      onClose={onDone}
      busy={busy}
    >
      <form className="stack" onSubmit={submit} noValidate>
        <div className="field">
          <label className="field-label" htmlFor={`${id}-project`}>
            {t('work.project')}
          </label>
          <select
            id={`${id}-project`}
            className="select input"
            value={chosen}
            onChange={event => setProjectId(event.target.value)}
          >
            {(projects.data ?? []).map(project => (
              <option key={project.id} value={project.id}>
                {project.name}
              </option>
            ))}
            {entry && !projects.data?.some(project => project.id === entry.projectId) && (
              <option value={entry.projectId}>{entry.projectName}</option>
            )}
          </select>
        </div>
        <Field
          label={t('work.howLong')}
          ok={seconds && !tooLong ? t('work.howLong.ok', { duration: formatDuration(seconds) }) : undefined}
          error={duration && !seconds ? t('work.howLong.invalid') : tooLong ? t('work.howLong.tooLong') : undefined}
        >
          {props => (
            <input
              {...props}
              className="input"
              value={duration}
              autoComplete="off"
              placeholder="1h 30m"
              onChange={event => setDuration(event.target.value)}
            />
          )}
        </Field>
        {!isTimer && (
          <label className="check">
            <input type="checkbox" checked={undated} onChange={event => setUndated(event.target.checked)} />
            {m('undated')}
          </label>
        )}
        {(!undated || isTimer) && (
          <Field label={t('work.when.date')}>
            {props => (
              <input
                {...props}
                type="date"
                className="input"
                value={date}
                min={range.min}
                max={range.max}
                onChange={event => setDate(event.target.value)}
              />
            )}
          </Field>
        )}
        <Field label={t('work.description')} optional>
          {props => (
            <textarea
              {...props}
              className="textarea input"
              rows={2}
              maxLength={2000}
              value={description}
              onChange={event => setDescription(event.target.value)}
            />
          )}
        </Field>
        <TagField
          userId={user.id}
          teamId={team.id}
          value={tagId}
          current={entry?.tagId && entry.tagName ? { id: entry.tagId, name: entry.tagName } : null}
          onChange={setTagId}
        />
        {failure && <Notice tone="error">{failure.message}</Notice>}
        <div className="dialog-actions">
          <Button onClick={onDone} disabled={busy}>
            {m('back')}
          </Button>
          <Button type="submit" variant="primary" pending={busy} disabled={!seconds || tooLong}>
            {t('common.save')}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

const localDateTime = (days = 0) => {
  const date = new Date(Date.now() + days * 86_400_000)
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset())
  return date.toISOString().slice(0, 16)
}

/** Invitations: a link shown once after creation, optional e-mail, list with state and revoking. */
export function TeamInvitations() {
  const { team } = useTeamContext()
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [creating, setCreating] = useState(false)
  const [revoking, setRevoking] = useState<InvitationSummary | null>(null)
  const list = useInvitations(user.id, team.id, page)
  const revoke = useRevokeInvitation(user.id, team.id)
  return (
    <section className="stack" aria-label={m('invites')}>
      <div className="toolbar">
        <p className="muted">{m('inviteBody')}</p>
        {!team.archivedAt && (
          <Button variant="primary" onClick={() => setCreating(true)}>
            <Link2 size={16} aria-hidden="true" />
            {m('inviteTitle')}
          </Button>
        )}
      </div>
      {list.isPending ? (
        <Loading />
      ) : list.isError ? (
        <LoadError onRetry={() => void list.refetch()} message={list.error.message} />
      ) : !list.data.content.length ? (
        <Empty title={m('noInvites')} />
      ) : (
        <div className="card table-card" role="region" aria-label={m('invites')} tabIndex={0}>
          <table className="table">
            <thead>
              <tr>
                <th scope="col">{m('created')}</th>
                <th scope="col">{m('valid')}</th>
                <th scope="col">{m('used')}</th>
                <th scope="col">{m('status')}</th>
                {!team.archivedAt && (
                  <th scope="col">
                    <span className="sr-only">{m('revoke')}</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {list.data.content.map(invite => (
                <tr key={invite.id}>
                  <td>{dateTime(invite.createdAt, locale)}</td>
                  <td>{dateTime(invite.expiresAt, locale)}</td>
                  <td className="num">
                    {invite.usedCount} / {invite.maxUses ?? m('noLimit')}
                  </td>
                  <td>
                    <span className={`badge ${invite.status === 'ACTIVE' ? 'badge-ok' : ''}`}>{m(invite.status)}</span>
                  </td>
                  {!team.archivedAt && (
                    <td>
                      {invite.status === 'ACTIVE' && (
                        <Button
                          small
                          variant="ghost"
                          onClick={() => {
                            revoke.reset()
                            setRevoking(invite)
                          }}
                        >
                          {m('revoke')}
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {list.data && list.data.totalPages > 1 && (
        <Pager
          page={page}
          pages={list.data.totalPages}
          busy={list.isFetching}
          onPage={setPage}
          label={m('page', { page: page + 1, pages: list.data.totalPages })}
        />
      )}
      {creating && <InviteDialog team={team} onClose={() => setCreating(false)} />}
      {revoking && (
        <Dialog open title={m('revokeTitle')} onClose={() => setRevoking(null)} busy={revoke.isPending}>
          <p>{m('revokeBody')}</p>
          {revoke.isError && <Notice tone="error">{revoke.error.message}</Notice>}
          <div className="dialog-actions">
            <Button onClick={() => setRevoking(null)} disabled={revoke.isPending}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="danger"
              pending={revoke.isPending}
              onClick={() =>
                revoke.mutate(revoking.id, {
                  onSuccess: () => {
                    toast(m('revoked'))
                    setRevoking(null)
                  },
                })
              }
            >
              {m('revoke')}
            </Button>
          </div>
        </Dialog>
      )}
    </section>
  )
}

function InviteDialog({ team, onClose }: { team: Team; onClose: () => void }) {
  const user = useUser()
  const { t, locale } = useI18n()
  const m = useMessages(messages)
  const invite = useCreateInvitation(user.id)
  const [expires, setExpires] = useState(() => localDateTime(7))
  const [limit, setLimit] = useState('')
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [copied, setCopied] = useState(false)
  // The first invitation to a one-person space says that managers will see the work already saved there.
  const members = useTeamMembers(user.id, team.id, false, 0)
  const solo = members.data?.totalElements === 1
  const submit = (event: FormEvent) => {
    event.preventDefault()
    const date = new Date(expires)
    if (
      !Number.isFinite(date.getTime()) ||
      date.getTime() <= Date.now() ||
      date.getTime() > Date.now() + 30 * 86_400_000
    ) {
      setError(m('badExpiry'))
      return
    }
    const maxUses = limit.trim() === '' ? 1 : Number(limit)
    if (!Number.isInteger(maxUses) || maxUses < 1) {
      setError(m('badUses'))
      return
    }
    setError('')
    invite.mutate({
      teamId: team.id,
      expiresAt: date.toISOString(),
      maxUses,
      ...(email.trim() ? { email: email.trim() } : {}),
    })
  }
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(invite.data!.invitationUrl)
      setCopied(true)
      setError('')
    } catch {
      setError(m('copyFailed'))
    }
  }
  return (
    <Dialog open title={m('inviteTitle')} onClose={onClose} busy={invite.isPending}>
      {invite.data ? (
        <div className="stack">
          <Field label={m('link')}>
            {props => (
              <input
                {...props}
                className="input"
                value={invite.data!.invitationUrl}
                readOnly
                onFocus={event => event.currentTarget.select()}
              />
            )}
          </Field>
          <dl className="facts">
            <div>
              <dt>{m('valid')}</dt>
              <dd>{dateTime(invite.data.expiresAt, locale)}</dd>
            </div>
            <div>
              <dt>{m('uses')}</dt>
              <dd>{invite.data.maxUses ?? m('noLimit')}</dd>
            </div>
          </dl>
          {email.trim() && <Notice tone="success">{m('mailed', { email: email.trim() })}</Notice>}
          <p className="field-hint">{m('linkOnce')}</p>
          {error && (
            <p className="field-error" role="alert">
              {error}
            </p>
          )}
          <p className="sr-only" role="status">
            {copied ? m('copied') : ''}
          </p>
          <div className="dialog-actions">
            <Button onClick={onClose}>{t('common.close')}</Button>
            <Button variant="primary" onClick={() => void copy()}>
              {copied ? <Check size={16} aria-hidden="true" /> : <Copy size={16} aria-hidden="true" />}
              {m(copied ? 'copied' : 'copy')}
            </Button>
          </div>
        </div>
      ) : (
        <form className="stack" onSubmit={submit} noValidate>
          <p className="muted">{m('inviteBody')}</p>
          {solo && <Notice tone="warning">{m('soloWarn')}</Notice>}
          <Field label={m('expires')} hint={m('expiresHint')}>
            {props => (
              <input
                {...props}
                className="input"
                type="datetime-local"
                min={localDateTime()}
                max={localDateTime(30)}
                value={expires}
                onChange={event => setExpires(event.target.value)}
              />
            )}
          </Field>
          <Field label={m('uses')} hint={m('usesHint')}>
            {props => (
              <input
                {...props}
                className="input"
                type="number"
                min={1}
                step={1}
                inputMode="numeric"
                value={limit}
                placeholder="1"
                onChange={event => setLimit(event.target.value)}
              />
            )}
          </Field>
          <Field label={m('inviteEmail')} optional hint={m('inviteEmailHint')}>
            {props => (
              <input
                {...props}
                className="input"
                type="email"
                maxLength={255}
                autoComplete="off"
                value={email}
                onChange={event => setEmail(event.target.value)}
              />
            )}
          </Field>
          {(error || invite.isError) && <Notice tone="error">{error || invite.error?.message}</Notice>}
          <div className="dialog-actions">
            <Button onClick={onClose} disabled={invite.isPending}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" variant="primary" pending={invite.isPending}>
              {m(invite.isPending ? 'creating' : 'create')}
            </Button>
          </div>
        </form>
      )}
    </Dialog>
  )
}
