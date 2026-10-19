import { it, expect } from 'vitest'
import { formatDuration } from '../../src/lib/time'

it.each([[0, '00:00:00'], [-10, '00:00:00'], [59.9, '00:00:59'], [60, '00:01:00'], [3661, '01:01:01'], [360000, '100:00:00']])
  ('formats %s seconds without rolling hours into days', (seconds, formatted) => { expect(formatDuration(Number(seconds))).toBe(formatted) })
