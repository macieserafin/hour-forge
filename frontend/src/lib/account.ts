import { useMutation, useQueryClient } from '@tanstack/react-query'
import { changePassword, deleteAccount, downloadAccountData, requestEmailChange, signOutEverywhere, updateProfile } from './api'
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
    onSuccess: ({ blob, filename }) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename
      link.click()
      URL.revokeObjectURL(url)
    } })
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
