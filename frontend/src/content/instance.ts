/**
 * Who runs this instance. The privacy policy, the terms and the list of subprocessors print these values.
 * Whoever hosts HourForge puts their own details here before the instance opens to other people.
 */
export const instance = {
  operatorName: 'Maciej Serafin',
  /** Postal address; empty when the operator is a private person who is reachable by e-mail. */
  operatorAddress: '',
  /** Tax or company registration number (NIP, KRS) when the operator runs a business; leave empty otherwise. */
  businessId: '',
  contactEmail: 'macieserafin@gmail.com',
  /** Date of the latest change to the legal texts, as YYYY-MM-DD. */
  legalUpdated: '2026-11-12',
  hosting: { name: 'OVHcloud', location: 'EU' },
  backups: { name: 'OVHcloud (Object Storage)', location: 'EU' },
  mail: { name: 'Brevo', location: 'EU' },
} as const

export type Instance = typeof instance
