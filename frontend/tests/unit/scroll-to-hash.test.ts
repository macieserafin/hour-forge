import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook } from '@testing-library/react'
import { useScrollToHash } from '../../src/lib/scrollToHash'

/** E-mail links such as /account#sessions open the right section, even when it appears only after its data loads. */
describe('scrolling to a section from the address', () => {
  afterEach(() => {
    document.body.replaceChildren()
    vi.useRealTimers()
  })

  const section = (id: string) => {
    const element = document.createElement('section')
    element.id = id
    element.scrollIntoView = vi.fn()
    return element
  }

  it('scrolls to a section that is already on the page', () => {
    const sessions = section('sessions')
    document.body.append(sessions)
    renderHook(() => useScrollToHash('#sessions'))
    expect(sessions.scrollIntoView).toHaveBeenCalledOnce()
  })

  it('waits for a section that appears later', async () => {
    renderHook(() => useScrollToHash('#google'))
    const google = section('google')
    document.body.append(google)
    await vi.waitFor(() => expect(google.scrollIntoView).toHaveBeenCalledOnce())
  })

  it('stops waiting after the timeout', async () => {
    vi.useFakeTimers()
    renderHook(() => useScrollToHash('#google', 100))
    vi.advanceTimersByTime(150)
    const google = section('google')
    document.body.append(google)
    await Promise.resolve()
    expect(google.scrollIntoView).not.toHaveBeenCalled()
  })

  it('does nothing without a section in the address', () => {
    const sessions = section('sessions')
    document.body.append(sessions)
    renderHook(() => useScrollToHash(''))
    expect(sessions.scrollIntoView).not.toHaveBeenCalled()
  })
})
