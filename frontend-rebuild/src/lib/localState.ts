/**
 * What this browser keeps for one person: drafts, reminders already shown, the last project, the seen release notes.
 * Keys are `hourforge:<kind>:<userId>[:<more>]`. Storage may be missing or blocked, so every access is guarded.
 */
export function clearBrowserStateOf(userId: string) {
  try {
    const storage = window.localStorage
    for (const key of Object.keys(storage)) {
      const [prefix, , owner] = key.split(':')
      if (prefix === 'hourforge' && owner === userId) storage.removeItem(key)
    }
  } catch { /* Nothing can be read or removed then. */ }
}
