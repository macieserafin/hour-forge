export interface Release {
  id: string
  date: string
  title: string
  items: string[]
}

/** What changed, newest first. The id of the newest entry is what "seen" is compared against. */
export const releases: Release[] = [
  { id: '2026-10-21', date: '2026-10-21', title: 'Szybsza codzienna praca', items: [
    'Przełącz zadanie: zatrzymaj bieżący timer i od razu uruchom nowy jednym krokiem.',
    'Zmień projekt, opis lub zadanie Jira działającego timera bez jego zatrzymywania.',
    'Szablony wpisów, kopiowanie wpisu i „Powtórz wczorajszy dzień”.',
    'Czas działającego timera w tytule karty przeglądarki i opcjonalne powiadomienie o zapomnianym timerze.',
    'Preferencje: domyślny zespół, strona startowa, zapis czasu (1,50 h), pierwszy dzień tygodnia, przypomnienia.',
    'Zapamiętaj mnie: 30 dni na wybranym urządzeniu. Bez tego sesja kończy się po 8 godzinach bezczynności.',
    'Pulpit: czas ze wszystkich zespołów i mapa aktywności z ostatnich tygodni.',
    'Kolory projektów oraz znacznik „edytowano” w raporcie zespołu.',
  ] },
  { id: '2026-10-20', date: '2026-10-20', title: 'Integracja z Jira Cloud', items: [
    'Połącz swoje konto Jira i wybieraj zadania przy timerze oraz wpisach ręcznych.',
    'Klucz zadania widać w historii, w raporcie zespołu oraz w plikach CSV i PDF.',
    'Najedź na klucz zadania, aby zobaczyć jego tytuł, albo uruchom timer dla tego zadania z historii.',
  ] },
  { id: '2026-10-19', date: '2026-10-19', title: 'Raporty i wykresy', items: [
    'Raport zespołu z filtrami, eksport CSV i PDF oraz osobny raport własnego czasu.',
    'Wykresy dni i projektów dostępne także jako tabele.',
  ] },
]

export const latestReleaseId = releases[0]!.id

const seenKey = (userId: string) => `hourforge:whats-new-seen:${userId}`

export function readSeenRelease(userId: string): string | null {
  try { return window.localStorage.getItem(seenKey(userId)) } catch { return null }
}

export function markReleasesSeen(userId: string) {
  try { window.localStorage.setItem(seenKey(userId), latestReleaseId) } catch { /* The marker may then show again after a reload. */ }
}

/** Entries the person has not looked at yet; everything counts as new for a person who never opened the page. */
export function unseenReleases(userId: string): Release[] {
  const seen = readSeenRelease(userId)
  if (!seen) return releases
  const index = releases.findIndex(release => release.id === seen)
  return index < 0 ? releases : releases.slice(0, index)
}
