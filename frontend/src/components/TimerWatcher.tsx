import { useEffect, useState } from 'react'
import { usePreferences } from '../lib/preferences'
import { useProject } from '../lib/projects'
import { formatClock, timerElapsedSeconds, useActiveTimer } from '../lib/time'

const notifiedKey = (userId: string, entryId: string) => `hourforge:long-timer-notified:${userId}:${entryId}`

function alreadyNotified(userId: string, entryId: string) {
  try { return window.localStorage.getItem(notifiedKey(userId, entryId)) === '1' } catch { return false }
}

function rememberNotified(userId: string, entryId: string) {
  try { window.localStorage.setItem(notifiedKey(userId, entryId), '1') } catch { /* The reminder may then repeat after a reload. */ }
}

/**
 * Keeps the running timer visible when the page is in the background (its time in the tab title) and, if the person
 * allowed it, shows one browser notification when the timer has run for longer than their limit.
 */
export function TimerWatcher({ userId }: { userId: string }) {
  const preferences = usePreferences(userId).data
  const active = useActiveTimer(userId).data ?? null
  const project = useProject(userId, active?.teamId ?? '', active?.projectId ?? '', active !== null)
  const [now, setNow] = useState(Date.now)
  const paused = active?.pausedAt != null
  const showTitle = (preferences?.titleTimer ?? true) && active !== null
  const watching = active !== null && (showTitle || preferences?.browserNotifications === true)

  useEffect(() => {
    if (!watching || paused) return
    const interval = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(interval)
  }, [watching, paused])

  const elapsed = active ? timerElapsedSeconds(active, now) : 0
  const name = project.data?.name ?? 'HourForge'

  useEffect(() => {
    if (!showTitle) return
    const original = document.title
    document.title = `${paused ? '⏸' : '▶'} ${formatClock(elapsed)} · ${name}`
    return () => { document.title = original }
  }, [showTitle, paused, elapsed, name])

  const limitSeconds = (preferences?.longTimerHours ?? 10) * 3600
  const entryId = active?.id
  useEffect(() => {
    // The project name is part of the message, so wait until it is known (or known to be unavailable).
    if (!entryId || paused || project.isPending || !preferences?.browserNotifications || elapsed < limitSeconds) return
    if (typeof Notification === 'undefined' || Notification.permission !== 'granted' || alreadyNotified(userId, entryId)) return
    rememberNotified(userId, entryId)
    new Notification(`Timer działa od ponad ${Math.floor(limitSeconds / 3600)} godz.`, {
      body: `Projekt: ${name}. Jeśli o nim zapomniałeś, zatrzymaj go i popraw czas wpisu.`, tag: `hourforge-long-timer-${entryId}` })
  }, [entryId, paused, project.isPending, preferences?.browserNotifications, elapsed, limitSeconds, userId, name])

  return null
}
