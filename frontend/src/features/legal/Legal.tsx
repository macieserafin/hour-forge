import { Link, NavLink } from 'react-router-dom'
import { AnvilMark } from '../../brand/Anvil'
import { useI18n, type Locale } from '../../content/i18n'
import { instance } from '../../content/instance'
import { legalDocument, legalKinds, legalLabels, legalPaths, type LegalKind } from '../../content/legal'
import './legal.css'

/** Public page for one legal document; it opens without signing in so people can read it before they register. */
export function LegalPage({ kind }: { kind: LegalKind }) {
  const { locale, setLocale } = useI18n()
  const labels = legalLabels[locale]
  const document = legalDocument(locale, kind, instance)
  return (
    <div className="legal">
      <header className="legal-head">
        <Link className="brand" to="/" aria-label={labels.back}>
          <AnvilMark size={28} />
          <span>
            Hour<b>Forge</b>
          </span>
        </Link>
        <div className="segmented" role="group" aria-label="Language">
          {(['pl', 'en'] as Locale[]).map(choice => (
            <button
              key={choice}
              type="button"
              aria-pressed={locale === choice}
              lang={choice}
              onClick={() => setLocale(choice)}
            >
              {choice === 'pl' ? 'Polski' : 'English'}
            </button>
          ))}
        </div>
      </header>
      <nav className="legal-nav" aria-label={labels.nav}>
        {legalKinds.map(item => (
          <NavLink key={item} to={legalPaths[item]}>
            {labels[item]}
          </NavLink>
        ))}
      </nav>
      <main className="legal-body" id="main">
        <h1>{document.title}</h1>
        <p className="muted">
          {labels.updated}: <time dateTime={instance.legalUpdated}>{instance.legalUpdated}</time>
        </p>
        <p>{document.intro}</p>
        {document.sections.map(section => (
          <section key={section.title} aria-labelledby={`legal-${slug(section.title)}`}>
            <h2 id={`legal-${slug(section.title)}`}>{section.title}</h2>
            {section.items && (
              <ul>
                {section.items.map(item => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            )}
            {section.paragraphs?.map(text => (
              <p key={text}>{text}</p>
            ))}
            {section.table && (
              <div className="legal-table" tabIndex={0} role="group" aria-label={section.title}>
                <table>
                  <thead>
                    <tr>
                      {section.table.head.map(cell => (
                        <th key={cell} scope="col">
                          {cell}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {section.table.rows.map(row => (
                      <tr key={row[0]}>
                        {row.map((cell, index) =>
                          index === 0 ? (
                            <th key={index} scope="row">
                              {cell}
                            </th>
                          ) : (
                            <td key={index}>{cell}</td>
                          ),
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        ))}
      </main>
    </div>
  )
}

/** The three links shown at the foot of sign-in pages and in the account menu. */
export function LegalLinks({ className }: { className?: string }) {
  const { locale } = useI18n()
  const labels = legalLabels[locale]
  return (
    <nav className={className} aria-label={labels.nav}>
      {legalKinds.map(kind => (
        <Link key={kind} to={legalPaths[kind]}>
          {labels[kind]}
        </Link>
      ))}
    </nav>
  )
}

function slug(text: string) {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
}
