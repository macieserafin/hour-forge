import { useCallback, useEffect } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ApiError,
  changePassword,
  confirmAccountDeletion,
  deleteAccount,
  getUser,
  type User,
  requestAccountDeletionByEmail,
  startGoogleLink,
  unlinkGoogle,
  disableTwoFactor,
  downloadAccountData,
  enableTwoFactor,
  endSession,
  getLoginHistory,
  getSessions,
  getTwoFactorStatus,
  removeAvatar,
  removeTeamLogo,
  renewBackupCodes,
  requestEmailChange,
  signOutEverywhere,
  startTwoFactorSetup,
  updateProfile,
  uploadAvatar,
  uploadTeamLogo,
} from './api'
import { saveBlob } from './download'
import { googleNavigation } from './navigation'
import { sessionOptions } from './auth'
import { clearBrowserStateOf } from './localState'
import { teamsKey, useAuthError } from './teams'

export function useUpdateProfile() {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({
    mutationFn: updateProfile,
    retry: false,
    onError: handleError,
    onSuccess: user => client.setQueryData(sessionOptions.queryKey, user),
  })
}

/** Like {@link useAuthError}; when the server says the account has no password, the session is read again so "Ustaw hasło" appears. */
function useAccountError() {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useCallback(
    (error: Error) => {
      handleError(error)
      if (error instanceof ApiError && error.code === 'PASSWORD_NOT_SET')
        void client.invalidateQueries({ queryKey: sessionOptions.queryKey })
    },
    [client, handleError],
  )
}

export function useChangePassword() {
  const handleError = useAccountError()
  return useMutation({ mutationFn: changePassword, retry: false, gcTime: 0, onError: handleError })
}

export function useRequestEmailChange() {
  const handleError = useAccountError()
  return useMutation({ mutationFn: requestEmailChange, retry: false, gcTime: 0, onError: handleError })
}

export function useSignOutEverywhere() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: signOutEverywhere,
    retry: false,
    onSuccess: async () => {
      await client.cancelQueries()
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, null)
    },
  })
}

export const sessionsKey = (userId: string) => ['account-sessions', userId] as const

export function useSessions(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: sessionsKey(userId),
    queryFn: ({ signal }) => getSessions(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

export function useLoginHistory(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: ['login-history', userId],
    queryFn: ({ signal }) => getLoginHistory(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

export function useEndSession(userId: string) {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({
    mutationFn: endSession,
    retry: false,
    onError: handleError,
    onSettled: () => client.invalidateQueries({ queryKey: sessionsKey(userId) }),
  })
}

/** "Firefox · Windows", with a plain label when the browser could not be recognised. */
export function deviceLabel(session: { browser: string | null; system: string | null }) {
  return [session.browser ?? 'Nieznana przeglądarka', session.system].filter(Boolean).join(' · ')
}

export function passwordChangeError(current: string, next: string, confirmation: string): string {
  if (!current) return 'Podaj obecne hasło.'
  if (!next.trim() || [...next].length < 12) return 'Nowe hasło musi mieć co najmniej 12 znaków.'
  if (new TextEncoder().encode(next).length > 72)
    return 'Nowe hasło jest za długie. Użyj krótszego hasła; znaki specjalne i emoji mogą zajmować więcej miejsca.'
  if (next === current) return 'Nowe hasło musi różnić się od obecnego.'
  if (next !== confirmation) return 'Nowe hasła muszą być identyczne.'
  return ''
}

export function useDownloadAccountData() {
  const handleError = useAuthError()
  return useMutation({
    mutationFn: downloadAccountData,
    retry: false,
    gcTime: 0,
    onError: handleError,
    onSuccess: ({ blob, filename }) => saveBlob(blob, filename),
  })
}

export function useDeleteAccount(userId: string) {
  const client = useQueryClient()
  const handleError = useAccountError()
  return useMutation({
    mutationFn: deleteAccount,
    retry: false,
    gcTime: 0,
    onError: handleError,
    onSuccess: async () => {
      await client.cancelQueries()
      clearBrowserStateOf(userId)
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, null)
    },
  })
}

/**
 * The link may be opened by the deleted account itself, by somebody else signed in, or with no session at all.
 * Without a session nothing is read. Otherwise the session is read outside the cache, so a failed read never turns into
 * the app's error screen; a failure counts as "signed out". Only when the signed-in account is gone is its browser data
 * cleared, as after deleting from the settings.
 */
export function useConfirmAccountDeletion() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: confirmAccountDeletion,
    retry: false,
    gcTime: 0,
    onSuccess: async () => {
      const before = client.getQueryData<User | null>(sessionOptions.queryKey)
      if (!before) return
      let after: User | null
      try {
        after = await getUser()
      } catch {
        after = null
      }
      if (after?.id === before.id) return
      await client.cancelQueries()
      clearBrowserStateOf(before.id)
      client.removeQueries({ predicate: query => query.queryKey[0] !== 'session' })
      client.setQueryData(sessionOptions.queryKey, after)
    },
  })
}

/** An account without a password asks for a deletion link by e-mail; the account stays until the link is used. */
export function useRequestAccountDeletionByEmail() {
  const handleError = useAuthError()
  return useMutation({ mutationFn: requestAccountDeletionByEmail, retry: false, gcTime: 0, onError: handleError })
}

/** Connecting Google leaves the app: the server keeps the intent in the session and the browser goes to Google. */
export function useStartGoogleLink() {
  const handleError = useAuthError()
  return useMutation({
    mutationFn: startGoogleLink,
    retry: false,
    gcTime: 0,
    onError: handleError,
    onSuccess: ({ authorizationUrl }) => googleNavigation.go(authorizationUrl),
  })
}

export function useUnlinkGoogle() {
  const client = useQueryClient()
  const handleError = useAuthError()
  return useMutation({
    mutationFn: unlinkGoogle,
    retry: false,
    gcTime: 0,
    onError: handleError,
    onSuccess: () => client.invalidateQueries({ queryKey: sessionOptions.queryKey }),
  })
}

export const twoFactorKey = (userId: string) => ['two-factor', userId] as const

export function useTwoFactorStatus(userId: string) {
  const handleError = useAuthError()
  const query = useQuery({
    queryKey: twoFactorKey(userId),
    queryFn: ({ signal }) => getTwoFactorStatus(signal),
    retry: (count, error) => count < 1 && error instanceof ApiError && (error.status === 0 || error.status >= 500),
  })
  useEffect(() => {
    if (query.error) handleError(query.error)
  }, [query.error, handleError])
  return query
}

/** Setup, turning on and off and new backup codes; every change refreshes the status. */
export function useTwoFactorActions(userId: string) {
  const client = useQueryClient()
  const onError = useAccountError()
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
