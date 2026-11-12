import type { Locale } from './i18n'
import type { Instance } from './instance'

/** The three public documents. Texts describe what HourForge does today; a lawyer reviews them before launch. */
export type LegalKind = 'privacy' | 'terms' | 'subprocessors'

export interface LegalTable {
  head: string[]
  rows: string[][]
}

export interface LegalSection {
  title: string
  paragraphs?: string[]
  items?: string[]
  table?: LegalTable
}

export interface LegalDocument {
  title: string
  intro: string
  sections: LegalSection[]
}

export const legalKinds: LegalKind[] = ['privacy', 'terms', 'subprocessors']
export const legalPaths: Record<LegalKind, string> = {
  privacy: '/privacy',
  terms: '/terms',
  subprocessors: '/subprocessors',
}

export const legalLabels: Record<Locale, Record<LegalKind | 'updated' | 'back' | 'nav', string>> = {
  pl: {
    privacy: 'Polityka prywatności',
    terms: 'Regulamin',
    subprocessors: 'Podprocesorzy',
    updated: 'Ostatnia zmiana',
    back: 'Wróć do HourForge',
    nav: 'Dokumenty prawne',
  },
  en: {
    privacy: 'Privacy policy',
    terms: 'Terms of service',
    subprocessors: 'Subprocessors',
    updated: 'Last updated',
    back: 'Back to HourForge',
    nav: 'Legal documents',
  },
}

export function legalDocument(locale: Locale, kind: LegalKind, i: Instance): LegalDocument {
  return (locale === 'pl' ? polish : english)(i)[kind]
}

type Documents = Record<LegalKind, LegalDocument>

function polish(i: Instance): Documents {
  return {
    privacy: {
      title: 'Polityka prywatności',
      intro:
        'Ta strona opisuje, jakie dane o Tobie przetwarza HourForge, po co, komu je powierzamy i jak długo je przechowujemy.',
      sections: [
        {
          title: '1. Kto odpowiada za Twoje dane',
          paragraphs: [
            `Administratorem danych konta jest ${i.operatorName}, ${i.operatorAddress}. Pytania o dane osobowe i wnioski kieruj na adres ${i.contactEmail}.`,
            'Jeśli Twój pracodawca lub organizacja założyła zespół, ta organizacja decyduje o tym, jakie wpisy czasu pracy w nim zapisujecie. Odpowiada wtedy za te dane wobec Ciebie i może wymagać od Ciebie dodatkowych informacji.',
          ],
        },
        {
          title: '2. Jakie dane przetwarzamy',
          items: [
            'Konto: nazwa wyświetlana, adres e-mail, hasło (zapisane wyłącznie jako skrót), opcjonalnie zdjęcie profilowe.',
            'Praca: wpisy czasu (data, czas trwania, opis, projekt, tagi, klucz zadania Jira), projekty, zespoły, role i zaproszenia.',
            'Bezpieczeństwo: aktywne sesje, historia logowań z ostatnich 90 dni (przeglądarka, system, skrócony adres IP i przybliżone miejsce), sekret logowania dwuetapowego i kody zapasowe (szyfrowane), zdarzenia bezpieczeństwa w dziennikach serwera.',
            'Opcjonalne połączenia: identyfikator konta Google, jeśli logujesz się przez Google, oraz tokeny dostępu do Jira (szyfrowane), jeśli połączysz swoje konto Jira.',
            'Poczta: treść i adres wiadomości, które wysyła do Ciebie HourForge (potwierdzenia, reset hasła, powiadomienia), do czasu ich dostarczenia.',
            'Ustawienia: preferencje powiadomień, język i strona startowa.',
          ],
          paragraphs: ['Nie profilujemy użytkowników, nie sprzedajemy danych i nie wyświetlamy reklam.'],
        },
        {
          title: '3. Po co i na jakiej podstawie',
          items: [
            'Prowadzenie konta i działanie usługi (art. 6 ust. 1 lit. b RODO): rejestracja, logowanie, zapis i raportowanie czasu pracy, zespoły, powiadomienia o Twoich sprawach.',
            'Bezpieczeństwo usługi (art. 6 ust. 1 lit. f RODO): ograniczanie prób logowania, wykrywanie nadużyć, historia logowań, kopie zapasowe.',
            'Obowiązki prawne (art. 6 ust. 1 lit. c RODO): odpowiedzi na wnioski o prawa osób i zgłaszanie naruszeń ochrony danych.',
            'Zgoda (art. 6 ust. 1 lit. a RODO): logowanie przez Google i połączenie z Jira włączasz sam i możesz je odłączyć w każdej chwili.',
          ],
        },
        {
          title: '4. Komu przekazujemy dane',
          paragraphs: [
            'Dane trafiają tylko do dostawców potrzebnych do działania usługi: hostingu, przechowywania kopii zapasowych i wysyłki poczty. Pełna lista z miejscem przetwarzania jest na stronie „Podprocesorzy”.',
            'Dostawcy opcjonalnych funkcji (Google, Atlassian, kontrola botów przy rejestracji) dostają dane tylko wtedy, gdy z tych funkcji korzystasz.',
            'Członkowie Twojego zespołu widzą to, co pozwalają im role: właściciel i administratorzy widzą czas pracy w zespole, członkowie widzą swoją pracę. Twój adres e-mail nie jest pokazywany innym członkom.',
          ],
        },
        {
          title: '5. Jak długo przechowujemy dane',
          table: {
            head: ['Dane', 'Okres'],
            rows: [
              [
                'Konto i wpisy czasu',
                'Do usunięcia konta. Po usunięciu zostaje anonimowa nazwa „Użytkownik usunięty”, a wpisy czasu pozostają w raportach zespołu bez danych identyfikujących.',
              ],
              ['Konto niepotwierdzone', '7 dni'],
              ['Jednorazowe linki i tokeny po użyciu', '7 dni'],
              ['Nieprzyjęte zaproszenia', '90 dni'],
              ['Historia logowań', '90 dni'],
              ['Konto bez aktywności', 'Około 2 lata. Wcześniej wysyłamy ostrzeżenie e-mailem.'],
              ['Niewysłane wiadomości z błędem', '7 dni'],
              [
                'Kopie zapasowe',
                'Siedem dziennych, cztery tygodniowe i sześć miesięcznych. Usunięte dane znikają z kopii w ciągu około 6 miesięcy.',
              ],
              [
                'Dzienniki serwera',
                'Rotowane według rozmiaru; zdarzenia bezpieczeństwa nie zawierają haseł ani jednorazowych tokenów.',
              ],
            ],
          },
        },
        {
          title: '6. Twoje prawa',
          paragraphs: [
            'Masz prawo dostępu do danych, ich sprostowania, usunięcia, ograniczenia przetwarzania, przenoszenia oraz sprzeciwu wobec przetwarzania opartego na prawnie uzasadnionym interesie.',
            'Większość tych praw wykonasz sam: w ustawieniach konta zmienisz dane, pobierzesz kopię swoich danych w formacie JSON i usuniesz konto. W pozostałych sprawach napisz na adres z punktu 1.',
            'Skargę możesz złożyć do Prezesa Urzędu Ochrony Danych Osobowych (ul. Stawki 2, 00-193 Warszawa).',
          ],
        },
        {
          title: '7. Pliki cookie i pamięć przeglądarki',
          paragraphs: [
            'HourForge używa jednego ciasteczka sesji, niezbędnego do logowania. Jest niedostępne dla skryptów, wysyłane tylko do tej witryny i oznaczone jako bezpieczne na serwerze produkcyjnym. W pamięci przeglądarki zapisujemy też wybrany język i informację o przeczytanych nowościach.',
            'Nie używamy ciasteczek analitycznych ani reklamowych. Przy rejestracji usługa kontroli botów może zapisać własne dane techniczne, jeśli operator ją włączył.',
          ],
        },
        {
          title: '8. Jak chronimy dane',
          paragraphs: [
            'Połączenie jest szyfrowane (HTTPS z HSTS). Hasła zapisujemy jako skróty, a sekrety logowania dwuetapowego i tokeny Jira szyfrujemy. Baza danych nie ma dostępu z internetu, a kopie zapasowe są szyfrowane. Naruszenia ochrony danych zgłaszamy organowi nadzorczemu w ciągu 72 godzin, a osoby, których dotyczą, informujemy, gdy wymaga tego prawo.',
          ],
        },
        {
          title: '9. Zmiany',
          paragraphs: [
            'Gdy zmienimy te zasady, pokażemy nową datę u góry strony. O istotnych zmianach powiadomimy e-mailem.',
          ],
        },
      ],
    },
    terms: {
      title: 'Regulamin',
      intro: 'Regulamin określa zasady korzystania z HourForge. Korzystając z usługi, akceptujesz te zasady.',
      sections: [
        {
          title: '1. Usługa',
          paragraphs: [
            `HourForge to aplikacja internetowa do rejestrowania czasu pracy. Usługę prowadzi ${i.operatorName}, ${i.operatorAddress} (dalej: operator). Kontakt: ${i.contactEmail}.`,
            'Kod aplikacji jest dostępny na licencji MIT. Ten regulamin dotyczy instancji prowadzonej przez operatora, a nie samego kodu.',
          ],
        },
        {
          title: '2. Konto',
          items: [
            'Konto zakładasz, podając prawdziwy adres e-mail i potwierdzając go linkiem z wiadomości.',
            'Chronisz swoje hasło i urządzenia. Zalecamy włączenie logowania dwuetapowego.',
            'Jedna osoba ma jedno konto. Nie udostępniasz konta innym osobom.',
            'Możesz usunąć konto w każdej chwili w ustawieniach. Jeśli jesteś właścicielem zespołu, najpierw przekaż własność albo usuń zespół.',
          ],
        },
        {
          title: '3. Zasady korzystania',
          items: [
            'Nie łamiesz prawa i nie naruszasz praw innych osób.',
            'Nie próbujesz obejść zabezpieczeń, przeciążyć usługi ani uzyskać dostępu do cudzych danych.',
            'Nie wysyłasz złośliwego oprogramowania ani treści bezprawnych w opisach, nazwach i zdjęciach.',
            'Podejrzenie luki w zabezpieczeniach zgłaszasz prywatnie, zgodnie z plikiem SECURITY.md w repozytorium.',
          ],
          paragraphs: ['Operator może zablokować konto naruszające te zasady, a w poważnych przypadkach je usunąć.'],
        },
        {
          title: '4. Twoje dane i zespoły',
          paragraphs: [
            'Dane, które zapisujesz, pozostają Twoje lub Twojego zespołu. Operator używa ich tylko do świadczenia usługi, zgodnie z polityką prywatności.',
            'Właściciel i administratorzy zespołu widzą czas pracy zapisany w tym zespole. Zakładając zespół lub dołączając do niego, godzisz się na to w zakresie opisanym w aplikacji.',
            'Możesz w każdej chwili pobrać kopię swoich danych i raporty zespołu w formatach CSV, XLSX i PDF.',
          ],
        },
        {
          title: '5. Dostępność i kopie zapasowe',
          paragraphs: [
            'Staramy się, aby usługa działała bez przerw, ale jej dostępność nie jest gwarantowana. Planowane prace i wdrożenia mogą powodować krótkie przerwy.',
            'Baza danych jest kopiowana co 6 godzin. W razie awarii możemy utracić zmiany z ostatnich kilku godzin.',
          ],
        },
        {
          title: '6. Odpowiedzialność',
          paragraphs: [
            'Usługa jest udostępniana w stanie, w jakim jest. Operator odpowiada za szkody w granicach przewidzianych prawem. Nie odpowiada za decyzje, które podejmujesz na podstawie raportów, ani za rozliczenia czasu pracy między Tobą a Twoim zleceniodawcą lub pracodawcą.',
          ],
        },
        {
          title: '7. Zakończenie korzystania',
          paragraphs: [
            'Możesz przestać korzystać z usługi w każdej chwili. Operator może zakończyć świadczenie usługi, powiadamiając użytkowników co najmniej 30 dni wcześniej i umożliwiając pobranie danych.',
          ],
        },
        {
          title: '8. Zmiany regulaminu i prawo właściwe',
          paragraphs: [
            'O zmianach regulaminu informujemy w aplikacji i e-mailem z wyprzedzeniem. Do regulaminu stosuje się prawo polskie, z zachowaniem praw konsumenta wynikających z bezwzględnie obowiązujących przepisów.',
          ],
        },
      ],
    },
    subprocessors: {
      title: 'Podprocesorzy',
      intro:
        'Podmioty, którym operator powierza przetwarzanie danych osobowych, oraz dostawcy funkcji, które włączasz sam.',
      sections: [
        {
          title: 'Dostawcy niezbędni do działania usługi',
          table: {
            head: ['Cel', 'Dostawca', 'Dane', 'Miejsce'],
            rows: [
              ['Serwer aplikacji i bazy danych', i.hosting.name, 'Wszystkie dane konta i pracy', i.hosting.location],
              ['Kopie zapasowe (szyfrowane)', i.backups.name, 'Zaszyfrowana kopia bazy danych', i.backups.location],
              ['Wysyłka wiadomości e-mail', i.mail.name, 'Adres e-mail, treść wiadomości', i.mail.location],
            ],
          },
        },
        {
          title: 'Funkcje opcjonalne',
          paragraphs: ['Ci dostawcy dostają dane tylko wtedy, gdy włączysz daną funkcję lub gdy operator ją uruchomi.'],
          table: {
            head: ['Funkcja', 'Dostawca', 'Dane'],
            rows: [
              ['Logowanie przez Google', 'Google', 'Identyfikator konta, adres e-mail, nazwa'],
              ['Połączenie z Jira', 'Atlassian', 'Tokeny dostępu, klucze i tytuły zadań'],
              ['Kontrola botów przy rejestracji', 'Cloudflare (Turnstile)', 'Dane techniczne przeglądarki, adres IP'],
              [
                'Przybliżone miejsce w historii logowań',
                'DB-IP (baza danych na serwerze)',
                'Brak przekazywania danych: baza działa lokalnie',
              ],
            ],
          },
        },
        {
          title: 'Zmiany',
          paragraphs: ['Przed dodaniem nowego dostawcy zmienimy tę listę i poinformujemy o tym w aplikacji.'],
        },
      ],
    },
  }
}

function english(i: Instance): Documents {
  return {
    privacy: {
      title: 'Privacy policy',
      intro:
        'This page explains which personal data HourForge processes, why, who receives it and how long we keep it.',
      sections: [
        {
          title: '1. Who is responsible for your data',
          paragraphs: [
            `The controller of account data is ${i.operatorName}, ${i.operatorAddress}. Send questions and requests about personal data to ${i.contactEmail}.`,
            'If your employer or organisation created a team, that organisation decides which work-time entries you record in it. It is then responsible to you for that data and may ask you for further information.',
          ],
        },
        {
          title: '2. What data we process',
          items: [
            'Account: display name, e-mail address, password (stored only as a hash), optionally a profile picture.',
            'Work: time entries (date, duration, description, project, tags, Jira issue key), projects, teams, roles and invitations.',
            'Security: active sessions, sign-in history of the last 90 days (browser, system, shortened IP address and approximate place), the two-step sign-in secret and backup codes (encrypted), security events in the server logs.',
            'Optional connections: your Google account identifier if you sign in with Google, and Jira access tokens (encrypted) if you connect your Jira account.',
            'Mail: the content and address of messages HourForge sends you (confirmations, password reset, notifications), until delivery.',
            'Settings: notification preferences, language and start page.',
          ],
          paragraphs: ['We do not profile users, sell data or show advertising.'],
        },
        {
          title: '3. Why and on what basis',
          items: [
            'Running your account and the service (Art. 6(1)(b) GDPR): registration, sign-in, recording and reporting work time, teams, notifications about your matters.',
            'Security of the service (Art. 6(1)(f) GDPR): limiting sign-in attempts, detecting abuse, sign-in history, backups.',
            'Legal obligations (Art. 6(1)(c) GDPR): answering data subject requests and reporting personal data breaches.',
            'Consent (Art. 6(1)(a) GDPR): you turn on sign-in with Google and the Jira connection yourself and can disconnect them at any time.',
          ],
        },
        {
          title: '4. Who receives your data',
          paragraphs: [
            'Data goes only to providers the service needs: hosting, backup storage and mail delivery. The "Subprocessors" page lists them with the place of processing.',
            'Providers of optional features (Google, Atlassian, the bot check at registration) receive data only when you use those features.',
            'Members of your team see what their roles allow: the owner and admins see work time in the team, members see their own work. Your e-mail address is not shown to other members.',
          ],
        },
        {
          title: '5. How long we keep data',
          table: {
            head: ['Data', 'Period'],
            rows: [
              [
                'Account and time entries',
                'Until you delete the account. Afterwards only the anonymous name "Deleted user" remains, and time entries stay in team reports without identifying data.',
              ],
              ['Unconfirmed account', '7 days'],
              ['One-time links and tokens after use', '7 days'],
              ['Invitations nobody accepted', '90 days'],
              ['Sign-in history', '90 days'],
              ['Account without activity', 'About 2 years. We send a warning by e-mail first.'],
              ['Mail that failed to send', '7 days'],
              [
                'Backups',
                'Seven daily, four weekly and six monthly copies. Deleted data leaves the backups within about 6 months.',
              ],
              ['Server logs', 'Rotated by size; security events contain no passwords or one-time tokens.'],
            ],
          },
        },
        {
          title: '6. Your rights',
          paragraphs: [
            'You have the right to access, rectify, erase, restrict and port your data, and to object to processing based on legitimate interest.',
            'You can exercise most of these rights yourself: in the account settings you change your data, download a copy of your data as JSON and delete the account. For anything else write to the address in section 1.',
            'You may lodge a complaint with the President of the Personal Data Protection Office (UODO), ul. Stawki 2, 00-193 Warsaw, Poland, or with the authority in your country.',
          ],
        },
        {
          title: '7. Cookies and browser storage',
          paragraphs: [
            'HourForge uses one session cookie, which sign-in needs. Scripts cannot read it, the browser sends it only to this site, and it is marked secure on the production server. The browser also stores your language and which news you have read.',
            'We use no analytics or advertising cookies. At registration, the bot check service may store its own technical data if the operator turned it on.',
          ],
        },
        {
          title: '8. How we protect data',
          paragraphs: [
            'Connections are encrypted (HTTPS with HSTS). We store passwords as hashes and encrypt two-step sign-in secrets and Jira tokens. The database has no access from the internet, and backups are encrypted. We report personal data breaches to the supervisory authority within 72 hours and inform the people affected when the law requires it.',
          ],
        },
        {
          title: '9. Changes',
          paragraphs: [
            'When we change these rules, the new date appears at the top of the page. We tell you about significant changes by e-mail.',
          ],
        },
      ],
    },
    terms: {
      title: 'Terms of service',
      intro: 'These terms set the rules for using HourForge. By using the service you accept them.',
      sections: [
        {
          title: '1. The service',
          paragraphs: [
            `HourForge is a web application for recording working time. ${i.operatorName}, ${i.operatorAddress} (the operator) runs the service. Contact: ${i.contactEmail}.`,
            'The application code is available under the MIT License. These terms cover the instance the operator runs, not the code itself.',
          ],
        },
        {
          title: '2. Your account',
          items: [
            'You create an account with a real e-mail address and confirm it with the link in the message.',
            'You protect your password and your devices. We recommend turning on two-step sign-in.',
            'One person has one account. You do not share your account.',
            'You can delete your account at any time in the settings. If you own a team, hand over ownership or delete the team first.',
          ],
        },
        {
          title: '3. Rules of use',
          items: [
            'You obey the law and respect the rights of others.',
            'You do not try to bypass protections, overload the service or reach other people’s data.',
            'You do not upload malware or unlawful content in descriptions, names and pictures.',
            'You report a suspected vulnerability privately, as the SECURITY.md file in the repository describes.',
          ],
          paragraphs: ['The operator may block an account that breaks these rules and, in serious cases, delete it.'],
        },
        {
          title: '4. Your data and teams',
          paragraphs: [
            'The data you record stays yours or your team’s. The operator uses it only to provide the service, as the privacy policy describes.',
            'The owner and admins of a team see the work time recorded in that team. By creating or joining a team you agree to this to the extent the application shows.',
            'You can download a copy of your data and team reports as CSV, XLSX and PDF at any time.',
          ],
        },
        {
          title: '5. Availability and backups',
          paragraphs: [
            'We aim to keep the service running without interruption, but we do not guarantee availability. Planned work and deployments can cause short outages.',
            'The database is backed up every 6 hours. After a failure we may lose changes from the last few hours.',
          ],
        },
        {
          title: '6. Liability',
          paragraphs: [
            'The service is provided as it is. The operator is liable for damage within the limits the law allows. The operator is not responsible for decisions you take based on reports, or for settlements of working time between you and your client or employer.',
          ],
        },
        {
          title: '7. Ending use',
          paragraphs: [
            'You can stop using the service at any time. The operator may end the service by telling users at least 30 days ahead and allowing them to download their data.',
          ],
        },
        {
          title: '8. Changes and governing law',
          paragraphs: [
            'We announce changes to these terms in advance, in the application and by e-mail. Polish law governs these terms, without affecting consumer rights that mandatory provisions give you.',
          ],
        },
      ],
    },
    subprocessors: {
      title: 'Subprocessors',
      intro:
        'The companies the operator entrusts with processing personal data, and the providers of features you turn on yourself.',
      sections: [
        {
          title: 'Providers the service needs',
          table: {
            head: ['Purpose', 'Provider', 'Data', 'Location'],
            rows: [
              ['Application and database server', i.hosting.name, 'All account and work data', i.hosting.location],
              ['Backups (encrypted)', i.backups.name, 'Encrypted copy of the database', i.backups.location],
              ['E-mail delivery', i.mail.name, 'E-mail address, message content', i.mail.location],
            ],
          },
        },
        {
          title: 'Optional features',
          paragraphs: ['These providers receive data only when you use the feature or the operator turns it on.'],
          table: {
            head: ['Feature', 'Provider', 'Data'],
            rows: [
              ['Sign-in with Google', 'Google', 'Account identifier, e-mail address, name'],
              ['Jira connection', 'Atlassian', 'Access tokens, issue keys and titles'],
              ['Bot check at registration', 'Cloudflare (Turnstile)', 'Technical browser data, IP address'],
              [
                'Approximate place in sign-in history',
                'DB-IP (database on the server)',
                'No data leaves the server: the database runs locally',
              ],
            ],
          },
        },
        {
          title: 'Changes',
          paragraphs: ['Before we add a provider we update this list and tell you in the application.'],
        },
      ],
    },
  }
}
