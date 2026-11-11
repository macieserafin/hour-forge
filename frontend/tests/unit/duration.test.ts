import { describe, expect, it } from 'vitest'
import { afterEach } from 'vitest'
import { formatClock, formatDuration, formatWorkDate, parseDuration, setDurationStyle } from '../../src/domain/duration'

describe('reading a typed duration', () => {
  it.each([
    ['45m', 2700],
    ['45 min', 2700],
    ['90 min', 5400],
    ['1h 30m', 5400],
    ['1h30', 5400],
    ['1 h 30 min', 5400],
    ['1h', 3600],
    ['1,5', 5400],
    ['1.5', 5400],
    ['1,5 h', 5400],
    ['2 godz', 7200],
    ['1:30', 5400],
    ['0:45', 2700],
    ['8', 28800],
    ['24', 86400],
    ['  2H  15M ', 8100],
    ['3 hours', 10800],
    ['20 minut', 1200],
  ])('%s is %i seconds', (text, seconds) => {
    expect(parseDuration(text)).toBe(seconds)
  })

  it.each(['', ' ', 'abc', '0', '0m', '1:75', '1h 30x', '-1'])('%s is rejected', text => {
    expect(parseDuration(text)).toBeNull()
  })
  it('reads a bare number as hours; the form shows the reading and refuses more than 24 h', () => {
    expect(parseDuration('30')).toBe(30 * 3600)
  })
})

describe('writing durations and dates', () => {
  it.each([
    [0, '0 min'],
    [60, '1 min'],
    [2700, '45 min'],
    [3600, '1 h'],
    [5400, '1 h 30 min'],
    [86400, '24 h'],
    [5429, '1 h 30 min'],
  ])('%i seconds read as %s', (seconds, text) => {
    expect(formatDuration(seconds)).toBe(text)
  })
  it('shows a running clock without hours padding', () => {
    expect(formatClock(3909)).toBe('1:05:09')
  })
  it('writes a work date without shifting it by the time zone', () => {
    expect(formatWorkDate('2026-11-11', 'pl')).toBe('11 lis')
    expect(formatWorkDate('2026-11-11', 'en')).toBe('11 Nov')
  })
})

describe('decimal style from the preference', () => {
  afterEach(() => setDurationStyle('CLOCK', 'pl'))
  it('writes hours with the separator of the language', () => {
    setDurationStyle('DECIMAL', 'pl')
    expect(formatDuration(5400)).toBe('1,5 h')
    expect(formatDuration(3600)).toBe('1 h')
    expect(formatDuration(900)).toBe('0,25 h')
    setDurationStyle('DECIMAL', 'en')
    expect(formatDuration(5400)).toBe('1.5 h')
  })
})
