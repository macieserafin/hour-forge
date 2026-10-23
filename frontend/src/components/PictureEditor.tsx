import { useEffect, useRef, useState } from 'react'
import { LoaderCircle } from 'lucide-react'

const OUTPUT = 256
const MAX_BYTES = 2 * 1024 * 1024

/**
 * Chooses and crops a square picture in the browser: zoom and position with sliders (usable from the keyboard),
 * a live preview, and a JPEG of the crop for upload. The server re-encodes it again, so this is about framing only.
 */
export function PictureEditor({ label, saving, error, onSave, onCancel }: {
  label: string; saving: boolean; error?: string; onSave: (picture: Blob) => void; onCancel: () => void
}) {
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
    const left = (size.width - side) * x / 100
    const top = (size.height - side) * y / 100
    context.fillStyle = '#fff'
    context.fillRect(0, 0, OUTPUT, OUTPUT)
    context.drawImage(image.current, left, top, side, side, 0, 0, OUTPUT, OUTPUT)
  }, [size, zoom, x, y])

  function choose(file: File | undefined) {
    setProblem(''); setSize(null)
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) { setProblem('Wybierz plik JPEG lub PNG.'); return }
    if (file.size > MAX_BYTES) { setProblem('Plik jest większy niż 2 MB.'); return }
    setZoom(1); setX(50); setY(50)
    setSource(URL.createObjectURL(file))
  }

  function save() {
    canvas.current?.toBlob(blob => { if (blob) onSave(blob); else setProblem('Nie udało się przygotować zdjęcia.') }, 'image/jpeg', 0.9)
  }

  return <div className="picture-editor" role="group" aria-label={label}>
    <div className="field-group"><label htmlFor="picture-file">Plik ze zdjęciem (JPEG lub PNG, do 2 MB)</label>
      <input id="picture-file" type="file" accept="image/jpeg,image/png" disabled={saving} onChange={event => choose(event.target.files?.[0])} /></div>
    {source && <img ref={image} src={source} alt="" hidden onLoad={event => setSize({ width: event.currentTarget.naturalWidth, height: event.currentTarget.naturalHeight })}
      onError={() => setProblem('Tego pliku nie da się odczytać jako zdjęcia.')} />}
    {size && <div className="picture-crop">
      <canvas ref={canvas} width={OUTPUT} height={OUTPUT} role="img" aria-label="Podgląd wykadrowanego zdjęcia" />
      <div>
        <div className="field-group"><label htmlFor="picture-zoom">Powiększenie</label>
          <input id="picture-zoom" type="range" min={1} max={4} step={0.1} value={zoom} onChange={event => setZoom(Number(event.target.value))} /></div>
        <div className="field-group"><label htmlFor="picture-x">Położenie w poziomie</label>
          <input id="picture-x" type="range" min={0} max={100} value={x} onChange={event => setX(Number(event.target.value))} /></div>
        <div className="field-group"><label htmlFor="picture-y">Położenie w pionie</label>
          <input id="picture-y" type="range" min={0} max={100} value={y} onChange={event => setY(Number(event.target.value))} /></div>
      </div>
    </div>}
    {(problem || error) && <p className="form-error" role="alert">{problem || error}</p>}
    <div className="dialog-actions">
      <button type="button" className="button-secondary" disabled={saving} onClick={onCancel}>Anuluj</button>
      <button type="button" className="button-primary" disabled={saving || !size} onClick={save}>
        {saving && <LoaderCircle size={16} className="spin" />}{saving ? 'Zapisywanie…' : 'Zapisz zdjęcie'}</button>
    </div>
  </div>
}
