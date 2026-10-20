import type { DashboardActivity } from '../lib/api'
import { formatDuration } from '../lib/time'

const level = (seconds: number, max: number) => seconds <= 0 || max <= 0 ? 0 : Math.min(4, Math.ceil((seconds / max) * 4))
const dayLabel = (date: string) => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/** A week-by-week grid of how much was worked each day. Every day is also readable as text. */
export function ActivityHeatmap({ activity }: { activity: DashboardActivity }) {
  const busiest = activity.days.find(day => day.seconds === activity.maxSeconds && day.seconds > 0)
  const weeks: DashboardActivity['days'][] = []
  for (let index = 0; index < activity.days.length; index += 7) weeks.push(activity.days.slice(index, index + 7))
  return <figure className="heatmap" aria-label={`Twoja aktywność w ostatnich ${weeks.length} tygodniach`}>
    <figcaption className="chart-title">Twoja aktywność w ostatnich {weeks.length} tygodniach</figcaption>
    <div className="heatmap-grid" role="list">
      {weeks.map(week => <div className="heatmap-week" key={week[0]!.workDate} role="presentation">
        {week.map(day => {
          const future = day.workDate > activity.today
          const text = future ? `${dayLabel(day.workDate)}: jeszcze nie ma tego dnia` : `${dayLabel(day.workDate)}: ${day.seconds > 0 ? formatDuration(day.seconds) : 'brak czasu'}`
          return <span key={day.workDate} role="listitem" tabIndex={-1} className={`heatmap-cell heatmap-level-${future ? 'future' : level(day.seconds, activity.maxSeconds)}`} title={text} aria-label={text} />
        })}
      </div>)}
    </div>
    <p className="field-hint heatmap-legend"><span>Mniej</span>{[0, 1, 2, 3, 4].map(step => <span key={step} className={`heatmap-cell heatmap-level-${step}`} aria-hidden="true" />)}<span>Więcej</span>
      {busiest && <span className="heatmap-busiest"> · Najbardziej pracowity dzień: {dayLabel(busiest.workDate)} ({formatDuration(busiest.seconds)})</span>}</p>
  </figure>
}
