import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, changeTimerState, createManualTimeEntry, deleteTimeEntry, discardTimer, getActiveTimer, getTimeEntryHistory, startTimer, stopTimer, updateTimeEntry, type TimeEntry, type TimeEntryHistoryFilters, switchTimer, updateTimer, copyDay } from './api'
import { teamsKey, useAuthError } from './teams'
import { dashboardKey } from './dashboard'

export const activeTimerKey = (userId: string) => ['timer', userId, 'active'] as const
const historyKey = (userId: string) => ['time-entries', userId] as const

export function useTimeEntryHistory(userId: string, teamId: string, filters: TimeEntryHistoryFilters, page: number) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: [...historyKey(userId), teamId, filters, page],
    queryFn: ({ signal }) => getTimeEntryHistory(teamId, filters, page, signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useActiveTimer(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: activeTimerKey(userId), queryFn: ({ signal }) => getActiveTimer(signal),
    refetchInterval: 15_000, refetchOnWindowFocus: 'always',
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

function useTimeCallbacks(userId: string, refreshHistory = false) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return {
    // Reconcile with the server even when a response was lost after a successful write.
    onSettled: () => Promise.all([
      client.invalidateQueries({ queryKey: activeTimerKey(userId) }),
      ...(refreshHistory ? [client.invalidateQueries({ queryKey: historyKey(userId) }),
        client.invalidateQueries({ queryKey: dashboardKey(userId) })] : []),
    ]),
    onError: (error: Error) => {
      handleError(error)
      if (error instanceof ApiError && [403, 404, 409].includes(error.status)) {
        void client.invalidateQueries({ queryKey: teamsKey(userId) })
        void client.invalidateQueries({ queryKey: ['projects', userId] })
      }
    },
    onMutate: () => Promise.all([
      client.cancelQueries({ queryKey: activeTimerKey(userId) }),
      ...(refreshHistory ? [client.cancelQueries({ queryKey: historyKey(userId) }),
        client.cancelQueries({ queryKey: dashboardKey(userId) })] : []),
    ]),
  }
}

export function useStartTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId)
  return useMutation({ mutationFn: startTimer, retry: false, ...callbacks,
    onSuccess: entry => client.setQueryData(activeTimerKey(userId), entry) })
}

export function useStopTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: stopTimer, retry: false, ...callbacks,
    onSuccess: () => client.setQueryData(activeTimerKey(userId), null) })
}

export function useChangeTimerState(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId)
  return useMutation({ mutationFn: changeTimerState, retry: false, ...callbacks,
    onSuccess: entry => client.setQueryData(activeTimerKey(userId), entry) })
}

export function useUpdateTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId)
  return useMutation({ mutationFn: updateTimer, retry: false, ...callbacks,
    onSuccess: entry => client.setQueryData(activeTimerKey(userId), entry) })
}

export function useSwitchTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: switchTimer, retry: false, ...callbacks,
    onSuccess: result => client.setQueryData(activeTimerKey(userId), result.started) })
}

/** Whole seconds of work on a running timer: pauses are not counted, and a paused timer stands still. */
export function timerElapsedSeconds(entry: TimeEntry, now: number) {
  const end = entry.pausedAt ? Date.parse(entry.pausedAt) : Math.max(now, Date.parse(entry.updatedAt))
  return Math.max(0, Math.floor((end - Date.parse(entry.startedAt!)) / 1000 - entry.pausedDurationSeconds))
}

export function useCopyDay(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  return useMutation({ mutationFn: copyDay, onError, retry: false,
    onSettled: () => Promise.all([client.invalidateQueries({ queryKey: historyKey(userId) }), client.invalidateQueries({ queryKey: dashboardKey(userId) })]) })
}

/** An ISO date (YYYY-MM-DD) a number of days later, or earlier when negative. */
export function addDays(isoDate: string, days: number) {
  const [year, month, day] = isoDate.split('-').map(Number) as [number, number, number]
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10)
}

export function useDiscardTimer(userId: string) {
  const client = useQueryClient()
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: discardTimer, retry: false, ...callbacks,
    onSuccess: () => client.setQueryData(activeTimerKey(userId), null) })
}

export function useManualTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: createManualTimeEntry, retry: false, ...callbacks })
}

export function useUpdateTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: updateTimeEntry, retry: false, ...callbacks })
}

export function useDeleteTimeEntry(userId: string) {
  const callbacks = useTimeCallbacks(userId, true)
  return useMutation({ mutationFn: deleteTimeEntry, retry: false, ...callbacks })
}

/** A running timer is flagged as possibly forgotten after this much counted work. */
export const LONG_TIMER_SECONDS = 12 * 60 * 60

// Per-browser conveniences only: storage may be missing or blocked, so every access is guarded.
function readStored(key: string) {
  try { return window.localStorage.getItem(key) } catch { return null }
}

function writeStored(key: string, value: string | null) {
  try {
    if (value === null) window.localStorage.removeItem(key)
    else window.localStorage.setItem(key, value)
  } catch { /* storage unavailable */ }
}

const lastProjectKey = (userId: string, teamId: string) => `hourforge:last-project:${userId}:${teamId}`
const timerDraftKey = (userId: string, entryId: string) => `hourforge:timer-draft:${userId}:${entryId}`
const longTimerKey = (userId: string, entryId: string) => `hourforge:long-timer-dismissed:${userId}:${entryId}`

export const readLastProject = (userId: string, teamId: string) => readStored(lastProjectKey(userId, teamId))
export const saveLastProject = (userId: string, teamId: string, projectId: string) => writeStored(lastProjectKey(userId, teamId), projectId)
export const readTimerDraft = (userId: string, entryId: string) => readStored(timerDraftKey(userId, entryId))
export const saveTimerDraft = (userId: string, entryId: string, description: string) => writeStored(timerDraftKey(userId, entryId), description)
export const clearTimerLocalState = (userId: string, entryId: string) => {
  writeStored(timerDraftKey(userId, entryId), null)
  writeStored(longTimerKey(userId, entryId), null)
}
export const isLongTimerDismissed = (userId: string, entryId: string) => readStored(longTimerKey(userId, entryId)) === '1'
export const dismissLongTimer = (userId: string, entryId: string) => writeStored(longTimerKey(userId, entryId), '1')

export const MAX_MANUAL_DURATION_SECONDS = 24 * 60 * 60
export const workDateRangeMessage = 'Data pracy musi mieścić się w ostatnich 2 latach i nie może być późniejsza niż dziś.'
export const maxDurationMessage = 'Jeden wpis może mieć najwyżej 24 godziny.'

/** Today's date (YYYY-MM-DD) in the team's time zone, matching the backend's work date rules. */
export function teamToday(timezone: string, now = new Date()) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: timezone, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
}

/** Allowed work dates: from two years ago (Feb 29 becomes Feb 28, like Java minusYears) up to today. */
export function workDateRange(timezone: string, now = new Date()) {
  const max = teamToday(timezone, now)
  const [year, month, day] = max.split('-').map(Number) as [number, number, number]
  const lastDay = new Date(Date.UTC(year - 2, month, 0)).getUTCDate()
  const min = `${year - 2}-${String(month).padStart(2, '0')}-${String(Math.min(day, lastDay)).padStart(2, '0')}`
  return { min, max }
}

/** Hours and minutes only (1:05), compact enough to print on a chart bar. */
export function formatHours(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds))
  return `${Math.floor(safe / 3600)}:${String(Math.floor(safe / 60) % 60).padStart(2, '0')}`
}

export type TimeFormat = 'CLOCK' | 'DECIMAL'
let preferredFormat: TimeFormat = 'CLOCK'
/** The person's chosen way of writing saved time; set once their settings are known. */
export const setTimeFormat = (format: TimeFormat) => { preferredFormat = format }

/** Always hours:minutes:seconds, for counters that change every second. */
export function formatClock(seconds: number) {
  const safe = Math.max(0, Math.floor(seconds))
  return [Math.floor(safe / 3600), Math.floor(safe / 60) % 60, safe % 60]
    .map(part => String(part).padStart(2, '0')).join(':')
}

/** Saved time, in the person's chosen format: 01:30:00 or 1,50 h. */
export function formatDuration(seconds: number) {
  if (preferredFormat === 'DECIMAL') return `${(Math.max(0, Math.floor(seconds)) / 3600).toFixed(2).replace('.', ',')} h`
  return formatClock(seconds)
}
