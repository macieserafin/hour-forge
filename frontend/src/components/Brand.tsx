import { Anvil } from 'lucide-react'

export function Brand({ compact = false }: { compact?: boolean }) {
  return <span className={`brand ${compact ? 'brand-compact' : ''}`}>
    <span className="brand-icon"><Anvil size={22} strokeWidth={1.8} aria-hidden="true" /></span>
    <span>Hour<span className="brand-light">Forge</span><span className="brand-dot">.</span></span>
  </span>
}
