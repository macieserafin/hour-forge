import { Info, TriangleAlert } from 'lucide-react'
import { usePublicConfig } from '../lib/operator'

/** The notice of the instance operator, on every page; nothing is shown when there is none. */
export function AnnouncementBanner() {
  const announcement = usePublicConfig().data?.announcement
  if (!announcement) return null
  const warning = announcement.level === 'WARNING'
  return <div className={`announcement-banner${warning ? ' warning' : ''}`} role="status" aria-label="Ogłoszenie operatora serwisu">
    {warning ? <TriangleAlert size={16} aria-hidden="true" /> : <Info size={16} aria-hidden="true" />}<p>{announcement.message}</p>
  </div>
}
