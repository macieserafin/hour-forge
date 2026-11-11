/** The palette a project colour is chosen from. Every shade has at least 3:1 contrast against white (non-text contrast). */
export const projectColors = {
  VIOLET: { label: 'Fioletowy', hex: '#7966df' },
  BLUE: { label: 'Niebieski', hex: '#3573b5' },
  TEAL: { label: 'Morski', hex: '#1a8f82' },
  GREEN: { label: 'Zielony', hex: '#3f8f4f' },
  AMBER: { label: 'Bursztynowy', hex: '#b87a00' },
  ORANGE: { label: 'Pomarańczowy', hex: '#d1651b' },
  RED: { label: 'Czerwony', hex: '#cf4f5a' },
  PINK: { label: 'Różowy', hex: '#c14a96' },
  SLATE: { label: 'Szary', hex: '#6b7a90' },
  BROWN: { label: 'Brązowy', hex: '#8b6b4a' },
} as const

export type ProjectColor = keyof typeof projectColors

export const projectColorKeys = Object.keys(projectColors) as ProjectColor[]

/** The shade of a colour name from the server; anything unknown means "no colour". */
export function projectColorHex(color: string | null | undefined): string | undefined {
  return color && color in projectColors ? projectColors[color as ProjectColor].hex : undefined
}
