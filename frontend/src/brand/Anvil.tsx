/**
 * The HourForge mark: an anvil with three sparks in the brand triad. Draft for the direction choice; the final
 * drawing, clear space and minimum size are fixed with the brand.
 */
export function AnvilMark({ size = 32, title }: { size?: number; title?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 32 32"
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      aria-label={title}
    >
      <path
        fill="var(--brand-mark, currentColor)"
        d="M2.5 11.2c3.3.3 5.6-.7 7.6-1.4H28v5.1h-5.2c-1.9 0-3 1.4-3 3.2v2.6h3.4v3.3H8.8v-3.3h3.4v-2.6c0-1.8-1.1-3.2-3-3.2-3.3 0-5.6-1.5-6.7-3.7z"
      />
      <circle cx="17" cy="4.6" r="1.6" fill="var(--brand-spark-1, currentColor)" />
      <circle cx="21.6" cy="3.2" r="1.25" fill="var(--brand-spark-2, currentColor)" />
      <circle cx="25.4" cy="5.6" r="1" fill="var(--brand-spark-3, currentColor)" />
    </svg>
  )
}

export function Wordmark() {
  return (
    <span className="wordmark">
      <AnvilMark size={28} />
      <span>
        Hour<b>Forge</b>
      </span>
    </span>
  )
}
