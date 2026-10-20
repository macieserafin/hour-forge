import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup, configure } from '@testing-library/react'
import { setTimeFormat } from '../../src/lib/time'

configure({ asyncUtilTimeout: 10000 })
afterEach(() => { cleanup(); setTimeFormat('CLOCK'); vi.unstubAllGlobals(); vi.useRealTimers(); window.localStorage.clear() })
Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value() { this.open = true } })
Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value() { this.open = false } })
