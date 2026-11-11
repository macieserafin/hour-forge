import type { RegistrationInput } from './api'

export function registrationError(input: RegistrationInput, confirmation: string): string {
  if (!input.displayName.trim() || input.displayName.trim().length > 255) return 'Wpisz nazwę wyświetlaną (maksymalnie 255 znaków).'
  if (!input.email.trim() || input.email.trim().length > 255) return 'Wpisz poprawny adres e-mail (maksymalnie 255 znaków).'
  if (!input.password.trim() || [...input.password].length < 12) return 'Hasło musi mieć co najmniej 12 znaków.'
  if (new TextEncoder().encode(input.password).length > 72) return 'Hasło jest za długie. Użyj krótszego hasła; znaki specjalne i emoji mogą zajmować więcej miejsca.'
  if (input.password !== confirmation) return 'Hasła muszą być identyczne.'
  return ''
}
