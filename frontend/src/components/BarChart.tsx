export interface ChartItem {
  key: string
  label: string
  value: number
  /** Full text for the data table, e.g. 01:20:00. */
  display: string
  /** Compact text printed on the bar, e.g. 1:20. */
  short?: string
  /** A colour for this bar (a project colour); the theme colour is used when absent. */
  color?: string
}

const WIDTH = 420
const COLUMN_HEIGHT = 200
const ROW_HEIGHT = 26

const truncate = (text: string, max: number) => text.length > max ? `${text.slice(0, max - 1)}…` : text

/**
 * A simple bar chart in SVG. The drawing is hidden from assistive technology and replaced by a real data table, so
 * the numbers are available to everyone; the chart colours come from the page theme.
 */
export function BarChart({ title, items, layout = 'columns', valueHeading = 'Czas', labelHeading = 'Pozycja' }: {
  title: string; items: ChartItem[]; layout?: 'columns' | 'rows'; valueHeading?: string; labelHeading?: string
}) {
  const max = Math.max(1, ...items.map(item => item.value))
  return <figure className="chart" aria-label={title}>
    <figcaption className="chart-title">{title}</figcaption>
    {layout === 'columns' ? <Columns items={items} max={max} /> : <Rows items={items} max={max} />}
    <table className="sr-only">
      <caption>{title}</caption>
      <thead><tr><th scope="col">{labelHeading}</th><th scope="col">{valueHeading}</th></tr></thead>
      <tbody>{items.map(item => <tr key={item.key}><th scope="row">{item.label}</th><td>{item.display}</td></tr>)}</tbody>
    </table>
  </figure>
}

function Columns({ items, max }: { items: ChartItem[]; max: number }) {
  const top = 22
  const bottom = 28
  const plot = COLUMN_HEIGHT - top - bottom
  const slot = (WIDTH - 16) / Math.max(1, items.length)
  const barWidth = Math.min(48, slot * 0.64)
  // With many bars only some labels fit; the rest stay available in the table.
  const labelEvery = Math.max(1, Math.ceil(16 / slot))
  return <svg viewBox={`0 0 ${WIDTH} ${COLUMN_HEIGHT}`} aria-hidden="true" focusable="false">
    <line className="chart-axis" x1="8" x2={WIDTH - 8} y1={COLUMN_HEIGHT - bottom} y2={COLUMN_HEIGHT - bottom} />
    {items.map((item, index) => {
      const height = item.value > 0 ? Math.max(2, (item.value / max) * plot) : 0
      const x = 8 + slot * index + (slot - barWidth) / 2
      const centre = x + barWidth / 2
      return <g key={item.key}>
        {height > 0 && <rect className="chart-bar" style={item.color ? { fill: item.color } : undefined} x={x} y={COLUMN_HEIGHT - bottom - height} width={barWidth} height={height} rx="2" />}
        {height > 0 && slot >= 28 && item.short && <text className="chart-value" x={centre} y={COLUMN_HEIGHT - bottom - height - 6} textAnchor="middle">{item.short}</text>}
        {index % labelEvery === 0 && <text className="chart-label" x={centre} y={COLUMN_HEIGHT - 10} textAnchor="middle">{truncate(item.label, 8)}</text>}
      </g>
    })}
  </svg>
}

function Rows({ items, max }: { items: ChartItem[]; max: number }) {
  const labelWidth = 132
  const valueWidth = 56
  const plot = WIDTH - labelWidth - valueWidth - 8
  return <svg viewBox={`0 0 ${WIDTH} ${items.length * ROW_HEIGHT + 4}`} aria-hidden="true" focusable="false">
    {items.map((item, index) => {
      const y = index * ROW_HEIGHT + 2
      const width = item.value > 0 ? Math.max(2, (item.value / max) * plot) : 0
      return <g key={item.key}>
        <text className="chart-label" x="0" y={y + 17}>{truncate(item.label, 19)}</text>
        {width > 0 && <rect className="chart-bar" style={item.color ? { fill: item.color } : undefined} x={labelWidth} y={y + 4} width={width} height={ROW_HEIGHT - 10} rx="2" />}
        <text className="chart-value" x={WIDTH} y={y + 17} textAnchor="end">{item.short ?? item.display}</text>
      </g>
    })}
  </svg>
}
