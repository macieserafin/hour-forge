import { it, expect } from 'vitest'
import { register } from '../../src/lib/api'
import { registrationError } from '../../src/lib/registration'
import { json, mockApi } from '../support/api'

const input = { displayName: 'New User', email: 'new@example.test', password: 'Valid-password-2026!' }

it('requires a name, a long enough password and matching confirmation', () => {
  expect(registrationError({ ...input, displayName: ' ' }, input.password)).toContain('nazwę')
  expect(registrationError({ ...input, password: 'short' }, 'short')).toContain('12 znaków')
  expect(registrationError(input, 'Different-password')).toContain('identyczne')
  expect(registrationError(input, input.password)).toBe('')
})

it('applies the bcrypt byte limit and counts Unicode characters without splitting emoji', () => {
  expect(registrationError({ ...input, password: '😀'.repeat(11) }, '😀'.repeat(11))).toContain('12 znaków')
  expect(registrationError({ ...input, password: '😀'.repeat(12) }, '😀'.repeat(12))).toBe('')
  expect(registrationError({ ...input, password: 'ą'.repeat(37) }, 'ą'.repeat(37))).toContain('za długie')
  expect(registrationError({ ...input, password: 'a'.repeat(72) }, 'a'.repeat(72))).toBe('')
})

it.each([400, 403, 409, 500])('reports registration HTTP %s without retrying the write', async status => {
  const fetcher = mockApi({ 'POST /api/auth/register': () => json({}, status) })
  await expect(register(input)).rejects.toMatchObject({ name: 'ApiError', status })
  expect(fetcher.mock.calls.filter(([path]) => String(path) === '/api/auth/register')).toHaveLength(1)
})

it('registers using fresh CSRF and cookies while trimming identity but preserving the password', async () => {
  const fetcher = mockApi({ 'POST /api/auth/register': () => json(null, 201) })
  await register({ displayName: ' New User ', email: ' New@Example.test ', password: ' password 2026! ' })
  expect(fetcher).toHaveBeenCalledTimes(2)
  const [, options] = fetcher.mock.calls[1]!
  expect(options).toMatchObject({ credentials: 'include', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': 'csrf' } })
  expect(JSON.parse(String(options?.body))).toEqual({ displayName: input.displayName, email: 'New@Example.test', password: ' password 2026! ' })
})
