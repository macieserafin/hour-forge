import { useEffect } from 'react'

/** Scrolls to the element named by the address fragment; e-mail links point at sections that appear only after their data loads, so it waits briefly. */
export function useScrollToHash(hash: string, timeout = 5000) {
  useEffect(() => {
    const id = decodeURIComponent(hash.slice(1))
    if (!id) return
    const reveal = () => {
      const target = document.getElementById(id)
      if (!target) return false
      target.scrollIntoView?.({ block: 'start' })
      return true
    }
    if (reveal()) return
    const observer = new MutationObserver(() => {
      if (reveal()) stop()
    })
    const timer = window.setTimeout(() => stop(), timeout)
    const stop = () => {
      observer.disconnect()
      window.clearTimeout(timer)
    }
    observer.observe(document.body, { childList: true, subtree: true })
    return stop
  }, [hash, timeout])
}
