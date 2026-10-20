import { projectColorHex } from '../lib/projectColors'

/** A small coloured dot before a project name. Decorative: the name always stays next to it. */
export function ProjectDot({ color }: { color: string | null | undefined }) {
  const hex = projectColorHex(color)
  return hex ? <span className="project-dot" style={{ background: hex }} aria-hidden="true" /> : null
}
