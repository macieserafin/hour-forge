import qrcode from 'qrcode-generator'

/** A QR code drawn as SVG in the browser, so the secret it carries never leaves the page. */
export function QrCode({ value, label, size = 184 }: { value: string; label: string; size?: number }) {
  const qr = qrcode(0, 'M')
  qr.addData(value)
  qr.make()
  const count = qr.getModuleCount()
  const quiet = 4
  const cells: string[] = []
  for (let row = 0; row < count; row++) {
    for (let col = 0; col < count; col++) if (qr.isDark(row, col)) cells.push(`M${col + quiet} ${row + quiet}h1v1h-1z`)
  }
  return <svg className="qr-code" role="img" aria-label={label} width={size} height={size} viewBox={`0 0 ${count + quiet * 2} ${count + quiet * 2}`}
    shapeRendering="crispEdges">
    <rect width="100%" height="100%" fill="#fff" />
    <path d={cells.join('')} fill="#000" />
  </svg>
}
