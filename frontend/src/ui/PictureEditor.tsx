import { useEffect, useId, useRef, useState } from 'react'
import { useI18n, useMessages } from '../content/i18n'
import { Button } from '.'

const OUTPUT = 256
const MAX_BYTES = 2 * 1024 * 1024

const messages = {
  pl: { file: 'Plik ze zdjęciem (JPEG lub PNG, do 2 MB)', zoom: 'Powiększenie', x: 'Położenie w poziomie', y: 'Położenie w pionie', preview: 'Podgląd wykadrowanego zdjęcia',
    type: 'Wybierz plik JPEG lub PNG.', size: 'Plik jest większy niż 2 MB.', read: 'Tego pliku nie da się odczytać jako zdjęcia.', prepare: 'Nie udało się przygotować zdjęcia.', save: 'Zapisz zdjęcie', saving: 'Zapisywanie…' },
  en: { file: 'Picture file (JPEG or PNG, up to 2 MB)', zoom: 'Zoom', x: 'Horizontal position', y: 'Vertical position', preview: 'Preview of the cropped picture',
    type: 'Choose a JPEG or PNG file.', size: 'The file is larger than 2 MB.', read: 'This file cannot be read as a picture.', prepare: 'The picture could not be prepared.', save: 'Save picture', saving: 'Saving…' },
}

/** Square crop in the browser with keyboard-usable sliders and a live preview; the server re-encodes the JPEG again. */
export function PictureEditor({ label, saving, error, onSave, onCancel }: { label: string; saving: boolean; error?: string; onSave: (picture: Blob) => void; onCancel: () => void }) {
  const m = useMessages(messages)
  const { t } = useI18n()
  const id = useId()
  const [source, setSource] = useState<string | null>(null)
  const [size, setSize] = useState<{ width: number; height: number } | null>(null)
  const [zoom, setZoom] = useState(1)
  const [x, setX] = useState(50)
  const [y, setY] = useState(50)
  const [problem, setProblem] = useState('')
  const image = useRef<HTMLImageElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  useEffect(() => () => { if (source) URL.revokeObjectURL(source) }, [source])
  useEffect(() => {
    const context = canvas.current?.getContext('2d')
    if (!size || !image.current || !context) return
    const side = Math.min(size.width, size.height) / zoom
    context.fillStyle = '#fff'
    context.fillRect(0, 0, OUTPUT, OUTPUT)
    context.drawImage(image.current, (size.width - side) * x / 100, (size.height - side) * y / 100, side, side, 0, 0, OUTPUT, OUTPUT)
  }, [size, zoom, x, y])
  const choose = (file: File | undefined) => {
    setProblem(''); setSize(null)
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) { setProblem(m('type')); return }
    if (file.size > MAX_BYTES) { setProblem(m('size')); return }
    setZoom(1); setX(50); setY(50); setSource(URL.createObjectURL(file))
  }
  const slider = (key: 'zoom' | 'x' | 'y', value: number, set: (value: number) => void, min: number, max: number, step = 1) =>
    <div className="field"><label className="field-label" htmlFor={`${id}-${key}`}>{m(key)}</label>
      <input id={`${id}-${key}`} type="range" min={min} max={max} step={step} value={value} onChange={event => set(Number(event.target.value))} /></div>
  return <div className="stack" role="group" aria-label={label}>
    <div className="field"><label className="field-label" htmlFor={`${id}-file`}>{m('file')}</label>
      <input id={`${id}-file`} type="file" accept="image/jpeg,image/png" disabled={saving} onChange={event => choose(event.target.files?.[0])} /></div>
    {source && <img ref={image} src={source} alt="" hidden onLoad={event => setSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })} onError={() => setProblem(m('read'))} />}
    {size && <div className="picture-crop">
      <canvas ref={canvas} width={OUTPUT} height={OUTPUT} role="img" aria-label={m('preview')} />
      <div className="stack">{slider('zoom', zoom, setZoom, 1, 4, 0.1)}{slider('x', x, setX, 0, 100)}{slider('y', y, setY, 0, 100)}</div>
    </div>}
    {(problem || error) && <p className="field-error" role="alert">{problem || error}</p>}
    <div className="dialog-actions"><Button onClick={onCancel} disabled={saving}>{t('common.cancel')}</Button>
      <Button variant="primary" pending={saving} disabled={!size} onClick={() => canvas.current?.toBlob(blob => { if (blob) onSave(blob); else setProblem(m('prepare')) }, 'image/jpeg', 0.9)}>{m(saving ? 'saving' : 'save')}</Button></div>
  </div>
}
