import { useEffect, useState } from 'react'
import { markReleasesSeen, releases, unseenReleases } from '../lib/whatsNew'

const dateLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { dateStyle: 'long' })

export function WhatsNewPanel({ userId }: { userId: string }) {
  // What was new when the page opened stays marked, even though opening the page counts as seeing it.
  const [fresh] = useState(() => new Set(unseenReleases(userId).map(release => release.id)))
  useEffect(() => {
    markReleasesSeen(userId)
    window.dispatchEvent(new Event('hourforge:whats-new-seen'))
  }, [userId])
  return <section className="whats-new" aria-label="Co nowego">
    {releases.map(release => <article className="overview-panel whats-new-release" key={release.id} aria-labelledby={`release-${release.id}`}>
      <header><h2 id={`release-${release.id}`}>{release.title}</h2>
        <p className="field-hint"><time dateTime={release.date}>{dateLabel(release.date)}</time>{fresh.has(release.id) && <span className="whats-new-badge">Nowe</span>}</p></header>
      <ul>{release.items.map(item => <li key={item}>{item}</li>)}</ul>
    </article>)}
  </section>
}
