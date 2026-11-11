import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/** WCAG 2.2 contrast of the token pairs the interface actually uses. Text needs 4.5:1, large text and UI parts 3:1. */
const css = readFileSync(resolve(__dirname, '../../src/ui/tokens.css'), 'utf8')
const block = (selector: string) => css.slice(css.indexOf(selector), css.indexOf('}', css.indexOf(selector)))
const read = (source: string) =>
  Object.fromEntries([...source.matchAll(/--([\w-]+):\s*(#[0-9A-Fa-f]{6})/g)].map(match => [match[1]!, match[2]!]))
const light = read(block(':root {'))
const dark = { ...light, ...read(block(":root[data-theme='dark']")) }

function luminance(hex: string) {
  const [r, g, b] = [1, 3, 5]
    .map(start => parseInt(hex.slice(start, start + 2), 16) / 255)
    .map(channel => (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!
}
const ratio = (a: string, b: string) => {
  const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p)
  return (x! + 0.05) / (y! + 0.05)
}

const textPairs: [string, string][] = [
  ['text', 'bg'],
  ['text', 'surface'],
  ['text', 'surface-2'],
  ['muted', 'bg'],
  ['muted', 'surface'],
  ['muted', 'surface-2'],
  ['primary', 'surface'],
  ['primary', 'bg'],
  ['on-primary', 'primary-fill'],
  ['on-accent', 'accent-fill'],
  ['third', 'surface'],
  ['danger', 'surface'],
  ['nav-text', 'nav-bg'],
  ['nav-active', 'nav-bg'],
]
const partPairs: [string, string][] = [
  ['field-border', 'surface'],
  ['field-border', 'bg'],
  ['accent', 'surface'],
  ['primary-fill', 'surface'],
]

describe.each([
  ['light', light],
  ['dark', dark],
] as const)('%s theme', (_, theme) => {
  it.each(textPairs)('%s on %s reaches 4.5:1', (fg, bg) => {
    expect(theme[fg], fg).toBeDefined()
    expect(theme[bg], bg).toBeDefined()
    expect(ratio(theme[fg]!, theme[bg]!)).toBeGreaterThanOrEqual(4.5)
  })
  it.each(partPairs)('%s against %s reaches 3:1', (fg, bg) => {
    expect(ratio(theme[fg]!, theme[bg]!)).toBeGreaterThanOrEqual(3)
  })
})
