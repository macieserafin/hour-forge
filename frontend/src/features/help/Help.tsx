import { useEffect } from 'react'
import { LifeBuoy, MessageSquareWarning, Sparkles } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useI18n, useMessages } from '../../content/i18n'
import { markSeen, releases } from '../../content/releases'
import { formatWorkDate } from '../../domain/duration'
import { useUser } from '../../app/session'
import { Notice } from '../../ui'
import './help.css'

const messages = {
  pl: {
    title: 'Pomoc', intro: 'Odpowiedzi na najczęstsze pytania. Każdy ekran ma też podpowiedzi przy polach.', whatsNew: 'Co nowego', report: 'Zgłoś problem lub pomysł',
    reportBody: 'Prywatne zgłoszenie do operatora tej instancji uruchomimy razem z formularzem zgłoszeń (B10). Do tego czasu skontaktuj się z osobą, która zaprosiła Cię do HourForge, albo z operatorem instancji.',
    q1: 'Timer czy dodawanie czasu?', a1: 'Oba sposoby są równorzędne. Timer mierzy pracę na bieżąco; „Dodaj czas” zapisuje pracę wykonaną wcześniej. Możesz dopisać czas także wtedy, gdy timer działa — nie zmienia to pomiaru.',
    q2: 'Jak wpisać czas?', a2: 'Tak, jak mówisz: „45m”, „1h 30m”, „90 min”, „1,5” albo „1:30”. Pod polem zobaczysz, jak HourForge odczytał wartość. Jeden wpis może mieć najwyżej 24 godziny.',
    q3: 'Nie pamiętam, kiedy to było', a3: 'Wybierz „Nie pamiętam daty”. Taki wpis ma osobną sumę i nie wlicza się do dni ani tygodni. Datę możesz nadać później w historii.',
    q4: 'Zespoły i przestrzeń osobista', a4: 'Każdy projekt należy do jednego zespołu. Przestrzeń osobista to zespół, w którym jesteś sam. Gdy kogoś zaprosisz jako administratora, zobaczy pracę zapisaną w tym zespole.',
    q5: 'Kto widzi moją pracę?', a5: 'Właściciel i administratorzy widzą czas w swoim zespole. Członkowie widzą swoją pracę. Twój adres e-mail nie jest pokazywany innym członkom. Podgląd „kto teraz pracuje” zespół może wyłączyć.',
    q6: 'Raporty i eksport', a6: '„Mój raport” pokazuje Twoją pracę ze wszystkich zespołów. Raport zespołu (dla zarządzających) ma filtry zapisane w adresie, przejście do wpisów i eksport CSV, XLSX i PDF z tymi samymi filtrami.',
    q7: 'Jira', a7: 'Zespół może połączyć witrynę Jira, a każda osoba swoje konto. HourForge tylko odczytuje zadania. Bez Jiry wszystko działa tak samo.',
    q8: 'Bezpieczeństwo konta', a8: 'W ustawieniach konta włączysz logowanie dwuetapowe, zobaczysz zalogowane urządzenia i historię logowań oraz pobierzesz kopię swoich danych.',
  },
  en: {
    title: 'Help', intro: 'Answers to common questions. Every screen also has hints next to its fields.', whatsNew: "What's new", report: 'Report a problem or idea',
    reportBody: 'Private reports to the operator of this instance arrive with the report form (B10). Until then, contact the person who invited you to HourForge or the instance operator.',
    q1: 'Timer or adding time?', a1: 'Both are equal. The timer tracks work as it happens; "Add time" records work done earlier. You can add time while the timer runs — it does not change the tracking.',
    q2: 'How do I type time?', a2: 'The way you say it: "45m", "1h 30m", "90 min", "1.5" or "1:30". Below the field you see how HourForge read it. One entry can last at most 24 hours.',
    q3: "I don't remember when it was", a3: 'Choose "I don\'t remember the date". Such an entry has its own total and is not counted in days or weeks. You can give it a date later in the history.',
    q4: 'Teams and the personal space', a4: 'Every project belongs to one team. A personal space is a team with only you. When you invite someone as an admin, they see the work saved in that team.',
    q5: 'Who sees my work?', a5: 'The owner and admins see time in their team. Members see their own work. Your e-mail address is not shown to other members. The team can turn off the "who is working now" view.',
    q6: 'Reports and export', a6: '"My report" shows your work from all teams. The team report (for managers) keeps filters in the address, leads to the entries and exports CSV, XLSX and PDF with the same filters.',
    q7: 'Jira', a7: 'A team can connect a Jira site and each person their account. HourForge only reads issues. Without Jira everything works the same.',
    q8: 'Account security', a8: 'In account settings you turn on two-step sign-in, see signed-in devices and sign-in history, and download a copy of your data.',
  },
}

/** U23 help: questions about the main ideas of the product and where to report a problem. */
export function Help() {
  const m = useMessages(messages)
  const questions = (['1', '2', '3', '4', '5', '6', '7', '8'] as const)
  return <div className="page help">
    <header className="page-head"><div><h1>{m('title')}</h1><p className="muted">{m('intro')}</p></div><Link className="btn" to="/app/whats-new"><Sparkles size={16} aria-hidden="true" />{m('whatsNew')}</Link></header>
    <section className="card faq" aria-label={m('title')}>{questions.map(key => <details key={key}><summary>{m(`q${key}`)}</summary><p>{m(`a${key}`)}</p></details>)}</section>
    <section className="card stack" aria-labelledby="report-title"><h2 id="report-title"><MessageSquareWarning size={18} aria-hidden="true" /> {m('report')}</h2><Notice tone="info">{m('reportBody')}</Notice></section>
  </div>
}

export function WhatsNew() {
  const user = useUser()
  const { locale } = useI18n()
  const m = useMessages(messages)
  useEffect(() => { markSeen(user.id) }, [user.id])
  return <div className="page whats-new">
    <header className="page-head"><div><h1>{m('whatsNew')}</h1></div><Link className="btn" to="/app/help"><LifeBuoy size={16} aria-hidden="true" />{m('title')}</Link></header>
    <ol className="timeline">{releases.map(release => <li key={release.id} className="card">
      <p className="eyebrow">{formatWorkDate(release.date, locale)}</p><h2>{release.title[locale]}</h2>
      <ul>{release.items[locale].map((item, index) => <li key={index}>{item}</li>)}</ul></li>)}</ol>
  </div>
}

