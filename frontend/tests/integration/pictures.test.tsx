import { it, expect, beforeEach, afterEach, vi } from 'vitest'
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from '../../src/App'
import { renderApp } from '../support/render'
import { mockApi, json, team, user } from '../support/api'

const drawn: unknown[][] = []

beforeEach(() => {
  drawn.length = 0
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:picture'), revokeObjectURL: vi.fn() }))
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockImplementation(() =>
    ({ fillRect: vi.fn(), drawImage: (...args: unknown[]) => drawn.push(args), fillStyle: '' }) as unknown as CanvasRenderingContext2D)
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(callback => callback(new Blob(['jpeg'], { type: 'image/jpeg' })))
})
afterEach(() => vi.restoreAllMocks())

/** Chooses a file and lets the hidden picture "load" with the given size, as a browser would. */
async function choosePicture(width = 800, height = 600, file = new File(['png'], 'me.png', { type: 'image/png' })) {
  await userEvent.upload(screen.getByLabelText(/Plik ze zdjęciem/), file)
  const image = document.querySelector('.picture-editor img') as HTMLImageElement
  Object.defineProperty(image, 'naturalWidth', { value: width })
  Object.defineProperty(image, 'naturalHeight', { value: height })
  fireEvent.load(image)
}

it('crops and uploads a profile picture, then shows it instead of the initials', async () => {
  let version: string | null = null
  let uploaded: FormDataEntryValue | null = null
  mockApi({ 'GET /api/me': () => json({ ...user, avatarUpdatedAt: version }),
    'PUT /api/account/avatar': (_url, options) => { uploaded = (options.body as FormData).get('image'); version = '2026-10-23T10:00:00Z'; return json(null, 204) } })
  renderApp(<App />, '/account')
  const section = await screen.findByRole('region', { name: 'Zdjęcie profilowe' })
  await userEvent.click(within(section).getByRole('button', { name: 'Dodaj zdjęcie' }))
  expect(within(section).getByRole('button', { name: 'Zapisz zdjęcie' })).toBeDisabled()

  await choosePicture(800, 600)
  expect(await within(section).findByRole('img', { name: 'Podgląd wykadrowanego zdjęcia' })).toBeVisible()
  // A centred 600 x 600 square of the 800 x 600 picture, drawn at 256 x 256.
  expect(drawn.at(-1)!.slice(1)).toEqual([100, 0, 600, 600, 0, 0, 256, 256])
  fireEvent.change(within(section).getByLabelText('Powiększenie'), { target: { value: '2' } })
  expect(drawn.at(-1)!.slice(1, 5)).toEqual([250, 150, 300, 300])

  await userEvent.click(within(section).getByRole('button', { name: 'Zapisz zdjęcie' }))
  await waitFor(() => expect(uploaded).toBeInstanceOf(Blob))
  const pictures = await screen.findAllByRole('img', { name: user.displayName })
  expect(pictures[0]).toHaveAttribute('src', '/api/users/actor/avatar?v=2026-10-23T10%3A00%3A00Z')
})

it('refuses files that are too big or not JPEG or PNG before sending anything', async () => {
  const fetcher = mockApi()
  renderApp(<App />, '/account')
  const section = await screen.findByRole('region', { name: 'Zdjęcie profilowe' })
  await userEvent.click(within(section).getByRole('button', { name: 'Dodaj zdjęcie' }))
  await userEvent.upload(within(section).getByLabelText(/Plik ze zdjęciem/), new File([new Uint8Array(2 * 1024 * 1024 + 1)], 'big.jpg', { type: 'image/jpeg' }))
  expect(await within(section).findByRole('alert')).toHaveTextContent('większy niż 2 MB')
  expect(fetcher.mock.calls.some(([url]) => String(url).includes('/avatar'))).toBe(false)
})

it('lets a team owner add a logo that replaces the colour dot', async () => {
  let logo: string | null = null
  mockApi({ 'GET /api/teams': () => json([{ ...team, logoUpdatedAt: logo }]),
    [`PUT /api/teams/${team.id}/logo`]: () => { logo = '2026-10-23T10:00:00Z'; return json(null, 204) } })
  renderApp(<App />, `/teams?team=${team.id}`)
  await userEvent.click(await screen.findByRole('button', { name: 'Ustawienia zespołu' }))
  const dialog = screen.getByRole('dialog')
  await userEvent.click(within(dialog).getByRole('button', { name: 'Dodaj logo' }))
  await choosePicture(300, 300)
  await userEvent.click(within(dialog).getByRole('button', { name: 'Zapisz zdjęcie' }))
  expect(await within(dialog).findByRole('button', { name: 'Usuń logo' })).toBeVisible()
  await waitFor(() => expect(document.querySelector(`img.team-logo[src^="/api/teams/${team.id}/logo"]`)).not.toBeNull())
})
