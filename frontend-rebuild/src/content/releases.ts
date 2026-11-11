import type { Locale } from './i18n'

export interface Release { id: string; date: string; title: Record<Locale, string>; items: Record<Locale, string[]> }

/** What changed, newest first. The id of the newest entry is compared with what this person has seen. */
export const releases: Release[] = [
  { id: '2026-11-11', date: '2026-11-11', title: { pl: 'Nowy HourForge', en: 'The new HourForge' }, items: {
    pl: ['„Moja praca”: praca ze wszystkich zespołów w jednym miejscu, timer i dodawanie czasu obok siebie.', 'Czas wpisujesz tak, jak mówisz: „1h 30m”, „90 min” albo „1,5”.',
      'Praca bez daty: przycisk „Nie pamiętam daty” i osobna suma.', 'Tydzień jako siatka dni i projektów, z dodawaniem czasu w komórce.', 'Ciemny motyw, język angielski i nowy wygląd na telefonie.'],
    en: ['"My work": work from all teams in one place, with the timer and adding time side by side.', 'Type time the way you say it: "1h 30m", "90 min" or "1.5".',
      'Work without a date: an "I don\'t remember the date" button and its own total.', 'The week as a grid of days and projects, with adding time in a cell.', 'Dark theme, English and a new look on phones.'],
  } },
  { id: '2026-10-21', date: '2026-10-21', title: { pl: 'Szybsza codzienna praca', en: 'Faster daily work' }, items: {
    pl: ['Przełączanie zadania jednym krokiem i zmiana działającego timera.', 'Szablony wpisów, kopiowanie wpisu i „Powtórz wczorajszy dzień”.', 'Czas timera w tytule karty i przypomnienie o zapomnianym timerze.'],
    en: ['Switching work in one step and changing a running timer.', 'Entry templates, copying an entry and "Repeat yesterday".', 'Timer in the tab title and a reminder about a forgotten timer.'],
  } },
  { id: '2026-10-20', date: '2026-10-20', title: { pl: 'Integracja z Jira Cloud', en: 'Jira Cloud integration' }, items: {
    pl: ['Wybór zadania Jira przy timerze i wpisach.', 'Klucz zadania w historii, raportach i eksportach.'],
    en: ['Choose a Jira issue for the timer and entries.', 'The issue key in history, reports and exports.'],
  } },
  { id: '2026-10-19', date: '2026-10-19', title: { pl: 'Raporty i wykresy', en: 'Reports and charts' }, items: {
    pl: ['Raport zespołu z filtrami i eksportem CSV, XLSX i PDF.', 'Raport własnego czasu.'],
    en: ['Team report with filters and CSV, XLSX and PDF export.', 'Report of your own time.'],
  } },
]

const seenKey = (userId: string) => `hourforge:whats-new-seen:${userId}`
export const EVENT = 'hourforge:whats-new-seen'

export function unseenCount(userId: string) {
  let seen: string | null = null
  try { seen = window.localStorage.getItem(seenKey(userId)) } catch { /* Counted as unseen. */ }
  const index = seen ? releases.findIndex(release => release.id === seen) : -1
  return index < 0 ? releases.length : index
}

export function markSeen(userId: string) {
  try { window.localStorage.setItem(seenKey(userId), releases[0]!.id) } catch { /* May show again after a reload. */ }
  window.dispatchEvent(new Event(EVENT))
}
