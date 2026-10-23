import { useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, changePassword, deleteAccount, disableTwoFactor, downloadAccountData, enableTwoFactor, endSession, getLoginHistory, getSessions,
  getTwoFactorStatus, removeAvatar, removeTeamLogo, renewBackupCodes, requestEmailChange, signOutEverywhere, startTwoFactorSetup, updateProfile,
  uploadAvatar, uploadTeamLogo } from './api'
import { saveBlob } from './download'
import { sessionOptions } from './auth'
import { teamsKey, useAuthError } from './teams'

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

export function useLoginHistory(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: ['login-history', userId], queryFn: ({ signal }) => getLoginHistory(signal),
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

export const twoFactorKey = (userId: string) => ['two-factor', userId] as const

export function useTwoFactorStatus(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({ queryKey: twoFactorKey(userId), queryFn: ({ signal }) => getTwoFactorStatus(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500) })
  useEffect(() => { if (query.error) handleError(query.error) }, [query.error, handleError])
  return query
}

/** Setup, turning on and off and new backup codes; every change refreshes the status. */
export function useTwoFactorActions(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  const onSettled = () => client.invalidateQueries({ queryKey: twoFactorKey(userId) })
  return {
    setup: useMutation({ mutationFn: startTwoFactorSetup, retry: false, gcTime: 0, onError }),
    enable: useMutation({ mutationFn: enableTwoFactor, retry: false, gcTime: 0, onError, onSettled }),
    disable: useMutation({ mutationFn: disableTwoFactor, retry: false, gcTime: 0, onError, onSettled }),
    renew: useMutation({ mutationFn: renewBackupCodes, retry: false, gcTime: 0, onError, onSettled }),
  }
}

/** Uploading or removing the profile picture; the session is read again for the new picture version. */
export function useProfilePicture() {
  const client = useQueryClient()
  const onError = useAuthError()
  const onSuccess = () => client.invalidateQueries({ queryKey: sessionOptions.queryKey })
  return {
    upload: useMutation({ mutationFn: uploadAvatar, retry: false, onError, onSuccess }),
    remove: useMutation({ mutationFn: removeAvatar, retry: false, onError, onSuccess }),
  }
}

export function useTeamLogo(userId: string) {
  const client = useQueryClient()
  const onError = useAuthError()
  const onSuccess = () => client.invalidateQueries({ queryKey: teamsKey(userId) })
  return {
    upload: useMutation({ mutationFn: uploadTeamLogo, retry: false, onError, onSuccess }),
    remove: useMutation({ mutationFn: removeTeamLogo, retry: false, onError, onSuccess }),
  }
}
