/**
 * Who runs this instance. The privacy policy, the terms and the list of subprocessors print these values.
 * Whoever hosts HourForge replaces the bracketed placeholders before the instance opens to other people.
 */
export const instance = {
  /** Person or company that runs the service and decides what happens with the data. */
  operatorName: 'Maciej Serafin',
  operatorAddress: '[operator address]',
  /** Tax or company registration number (NIP, KRS) when the operator runs a business; leave empty otherwise. */
  businessId: '',
  /** Address for privacy requests and for notices about the service. */
  contactEmail: '[contact e-mail]',
  /** Date of the latest change to the legal texts, as YYYY-MM-DD. */
  legalUpdated: '2026-11-12',
  /** Providers named in the list of subprocessors. */
  hosting: { name: '[server provider]', location: '[country in the EU]' },
  backups: { name: '[backup storage provider]', location: '[country in the EU]' },
  mail: { name: '[e-mail provider]', location: '[country in the EU]' },
} as const

export type Instance = typeof instance
