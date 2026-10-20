import { useEffect, useState } from 'react'
import { CheckCircle2, ExternalLink, LoaderCircle, PlugZap, Unplug } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Modal } from '../components/Modal'
import type { Team } from '../lib/api'
import { jiraOutcomeMessages, useDisconnectJiraAccount, useDisconnectJiraSite, useJiraSites, useJiraStatus, useSelectJiraSite, useStartJira } from '../lib/jira'
import { useTeams } from '../lib/teams'

function SiteChooser({ userId, team, onChosen }: { userId: string; team: Team; onChosen: () => void }) {
  const sites = useJiraSites(userId, team.id, true)
  const select = useSelectJiraSite(userId, team.id)
  const [chosen, setChosen] = useState('')
  return <div className="integration-choice" role="group" aria-labelledby="site-choice-title">
    <h3 id="site-choice-title">Wybierz witrynę Jira zespołu</h3>
    {sites.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie witryn…</p>
      : sites.isError ? <div className="dashboard-error" role="alert">{sites.error.message}<button onClick={() => void sites.refetch()}>Spróbuj ponownie</button></div>
      : <form onSubmit={event => { event.preventDefault(); if (chosen) select.mutate(chosen, { onSuccess: onChosen }) }}>
        <fieldset disabled={select.isPending}>
          <legend className="visually-hidden">Dostępne witryny Jira</legend>
          {sites.data.map(site => <label key={site.cloudId} className="integration-site">
            <input type="radio" name="jira-site" value={site.cloudId} checked={chosen === site.cloudId} onChange={() => setChosen(site.cloudId)} />
            <span><strong>{site.name}</strong><span className="field-hint">{site.url}</span></span>
          </label>)}
        </fieldset>
        {select.isError && <p role="alert" className="form-error">{select.error.message}</p>}
        <button className="button-primary" type="submit" disabled={!chosen || select.isPending}>{select.isPending && <LoaderCircle size={16} className="spin" />}Użyj tej witryny</button>
      </form>}
  </div>
}

function DisconnectSiteModal({ userId, team, onClose }: { userId: string; team: Team; onClose: () => void }) {
  const disconnect = useDisconnectJiraSite(userId, team.id)
  return <Modal title="Odłącz witrynę Jira" busy={disconnect.isPending} onClose={onClose}>
    <p className="dialog-description">Zespół <strong>{team.name}</strong> przestanie korzystać z Jiry, a połączenia kont wszystkich członków z tą witryną zostaną usunięte. Zapisany czas pracy nie zmieni się, ale jego powiązania z zadaniami Jira zostaną usunięte.</p>
    {disconnect.isError && <p className="form-error" role="alert">{disconnect.error.message}</p>}
    <footer className="dialog-actions"><button className="button-secondary" disabled={disconnect.isPending} onClick={onClose}>Anuluj</button>
      <button className="button-primary" disabled={disconnect.isPending} onClick={() => disconnect.mutate(undefined, { onSuccess: onClose })}>{disconnect.isPending ? <LoaderCircle size={16} className="spin" /> : <Unplug size={16} />}Odłącz witrynę</button></footer>
  </Modal>
}

function JiraCard({ userId, team }: { userId: string; team: Team }) {
  const status = useJiraStatus(userId, team.id)
  const start = useStartJira(team.id)
  const disconnectAccount = useDisconnectJiraAccount(userId, team.id)
  const [search, setSearch] = useSearchParams()
  const [confirmSite, setConfirmSite] = useState(false)
  const outcome = search.get('jira')
  const message = outcome ? jiraOutcomeMessages[outcome] : undefined
  const choosing = outcome === 'choose-site' && status.data?.canManage === true && !status.data.siteConnected

  function clearOutcome() {
    const next = new URLSearchParams(search)
    next.delete('jira')
    setSearch(next, { replace: true })
  }

  if (status.isPending) return <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie integracji…</p>
  if (status.isError) return <div className="dashboard-error" role="alert">{status.error.message}<button onClick={() => void status.refetch()}>Spróbuj ponownie</button></div>
  const jira = status.data
  const archived = team.archivedAt !== null

  return <article className="overview-panel integration-card" aria-labelledby="jira-title">
    <header className="integration-heading"><PlugZap size={22} strokeWidth={1.6} /><div><h2 id="jira-title">Jira Cloud</h2>
      <p className="field-hint">HourForge tylko odczytuje listę zadań (zakres read:jira-work). Niczego nie zapisuje w Jirze.</p></div></header>
    {message && <p className={message.tone === 'error' ? 'form-error' : 'form-success'} role={message.tone === 'error' ? 'alert' : 'status'}>{message.text}
      {!choosing && <button type="button" className="text-link" onClick={clearOutcome}> Ukryj</button>}</p>}
    {!jira.available ? <p>Integracja z Jira jest wyłączona na tym serwerze.</p>
      : choosing ? <SiteChooser userId={userId} team={team} onChosen={clearOutcome} />
      : !jira.siteConnected ? <>
        <p>Zespół nie ma jeszcze połączonej witryny Jira.</p>
        {jira.canManage && !archived ? <button className="button-primary" disabled={start.isPending} onClick={() => start.mutate('SITE')}>{start.isPending && <LoaderCircle size={16} className="spin" />}Połącz zespół z Jira Cloud</button>
          : <p className="field-hint">Połączyć witrynę może właściciel zespołu.</p>}
      </> : <>
        <dl className="invite-summary"><dt>Witryna</dt><dd>{jira.siteName} <a className="text-link" href={jira.siteUrl ?? undefined} target="_blank" rel="noopener noreferrer">{jira.siteUrl}<ExternalLink size={13} aria-hidden="true" /></a></dd>
          <dt>Twoje konto Jira</dt><dd>{jira.userConnected ? <span className="integration-ok"><CheckCircle2 size={15} aria-hidden="true" />Połączone</span> : 'Niepołączone'}</dd></dl>
        <div className="integration-actions">
          {jira.userConnected
            ? <button className="button-secondary" disabled={disconnectAccount.isPending} onClick={() => disconnectAccount.mutate()}>{disconnectAccount.isPending ? <LoaderCircle size={16} className="spin" /> : <Unplug size={16} />}Rozłącz moje konto Jira</button>
            : <button className="button-primary" disabled={start.isPending} onClick={() => start.mutate('MEMBER')}>{start.isPending && <LoaderCircle size={16} className="spin" />}Połącz moje konto Jira</button>}
          {jira.canManage && !archived && <>
            <button className="button-secondary" disabled={start.isPending} onClick={() => start.mutate('SITE')}>Zmień witrynę</button>
            <button className="button-secondary" onClick={() => setConfirmSite(true)}>Odłącz witrynę</button></>}
        </div>
      </>}
    {(start.isError || disconnectAccount.isError) && <p className="form-error" role="alert">{(start.error ?? disconnectAccount.error)?.message}</p>}
    {confirmSite && <DisconnectSiteModal userId={userId} team={team} onClose={() => setConfirmSite(false)} />}
  </article>
}

export function IntegrationsPanel({ userId }: { userId: string }) {
  const teams = useTeams(userId)
  const [search, setSearch] = useSearchParams()
  const selectedId = search.get('team')
  const selected = selectedId ? teams.data?.find(team => team.id === selectedId) : teams.data?.[0]

  useEffect(() => {
    if (!selectedId && selected) {
      const next = new URLSearchParams(search)
      next.set('team', selected.id)
      setSearch(next, { replace: true })
    }
  }, [selectedId, selected, search, setSearch])

  return <section className="projects-panel" aria-label="Integracje">
    {teams.isPending ? <p role="status" className="teams-loading"><LoaderCircle size={18} className="spin" />Ładowanie zespołów…</p>
      : teams.isError ? <div className="dashboard-error" role="alert">{teams.error.message}<button onClick={() => void teams.refetch()}>Spróbuj ponownie</button></div>
      : teams.data.length === 0 ? <div className="overview-panel empty-state"><PlugZap size={28} strokeWidth={1.4} /><h3>Najpierw dołącz do zespołu.</h3><p>Integracje działają w ramach zespołu.</p><Link className="text-link dashboard-teams-link" to="/teams">Przejdź do zespołów</Link></div>
      : <><div className="project-team-picker"><div className="field-group"><label htmlFor="integration-team">Zespół</label>
        <select className="text-input" id="integration-team" value={selected?.id ?? ''} onChange={event => setSearch({ team: event.target.value })}>{!selected && <option value="" disabled>Wybierz dostępny zespół</option>}{teams.data.map(team => <option key={team.id} value={team.id}>{team.name}</option>)}</select></div></div>
        {selected ? <JiraCard key={selected.id} userId={userId} team={selected} /> : <p className="form-error" role="alert">Ten zespół nie istnieje lub nie masz do niego dostępu. Wybierz inny zespół.</p>}
      </>}
  </section>
}
