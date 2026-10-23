import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, changePassword, deleteAccount, downloadAccountData, endSession, getSessions, requestEmailChange, signOutEverywhere, updateProfile } from './api'
import { saveBlob } from './download'
import { sessionOptions } from './auth'
import { useAuthError } from './teams'

export function useUpdateProfile() {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({ mutationFn: updateProfile, retry: false, onError: handleError,
    onSuccess: user => client.setQueryData(sessionOptions.queryKey, user) })
}

export function useChangePassword() {
  const handleError = useAuthError()
  return useMutation({ mutationFn: changePassword, retry: false, gcTime: 0, onError: handleError })
}

export function useRequestEmailChange() {
  const handleError = useAuthError()
  return useMutation({ mutationFn: requestEmailChange, retry: false, gcTime: 0, onError: handleError })
}

export function useSignOutEverywhere() {
  const client = useQueryClient()
  return useMutation({ mutationFn: signOutEverywhere, retry: false,
    onSuccess: async () => {
      await client.cancelQueries()
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, null)
    } })
}

export const sessionsKey = (userId: string) => ['account-sessions', userId] as const

export function useSessions(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: sessionsKey(userId), queryFn: ({ signal }) => getSessions(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

export function useEndSession(userId: string) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({ mutationFn: endSession, retry: false, onError: handleError,
    onSettled: () => client.invalidateQueries({ queryKey: sessionsKey(userId) }) })
}

/** "Firefox · Windows", with a plain label when the browser could not be recognised. */
export function deviceLabel(session: { browser: string | null; system: string | null }) {
  return [session.browser ?? 'Nieznana przeglądarka', session.system].filter(Boolean).join(' · ')
}

export function passwordChangeError(current: string, next: string, confirmation: string): string {
  if (!current) return 'Podaj obecne hasło.'
  if (!next.trim() || [...next].length < 12) return 'Nowe hasło musi mieć co najmniej 12 znaków.'
  if (new TextEncoder().encode(next).length > 72) return 'Nowe hasło jest za długie. Użyj krótszego hasła; znaki specjalne i emoji mogą zajmować więcej miejsca.'
  if (next === current) return 'Nowe hasło musi różnić się od obecnego.'
  if (next !== confirmation) return 'Nowe hasła muszą być identyczne.'
  return ''
}

export function useDownloadAccountData() {
  const handleError = useAuthError()
  return useMutation({ mutationFn: downloadAccountData, retry: false, gcTime: 0, onError: handleError,
    onSuccess: ({ blob, filename }) => saveBlob(blob, filename) })
}

export function useDeleteAccount() {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({ mutationFn: deleteAccount, retry: false, gcTime: 0, onError: handleError,
    onSuccess: async () => {
      await client.cancelQueries()
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, null)
    } })
}
