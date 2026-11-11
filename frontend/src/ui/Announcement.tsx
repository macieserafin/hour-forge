import { AlertTriangle, Info } from 'lucide-react'
import { usePublicConfig } from '../lib/operator'

/** The operator's notice, on every page and before sign-in. */
export function Announcement() {
  const announcement = usePublicConfig().data?.announcement
  if (!announcement) return null
  const warning = announcement.level === 'WARNING'
  return (
    <div className={`announcement ${warning ? 'is-warning' : ''}`} role={warning ? 'alert' : 'status'}>
      {warning ? <AlertTriangle size={16} aria-hidden="true" /> : <Info size={16} aria-hidden="true" />}
      <span>{announcement.message}</span>
    </div>
  )
}
