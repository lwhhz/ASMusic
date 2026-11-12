/**
 * Local Netease Cloud Music API proxy.
 *
 * The Android client talks to this service either through
 *   adb reverse tcp:3000 tcp:3000   -> http://127.0.0.1:3000
 * or by configuring a LAN / remote host inside the app settings.
 *
 * Cookies are NOT stored here: the client sends its own `Cookie` header on
 * every request after QR login, and the proxy forwards it upstream.
 */
const port = Number(process.env.PORT || 3000)
process.env.PORT = String(port)

const ncm = require('NeteaseCloudMusicApi')

if (typeof ncm.serveNcmApi === 'function') {
  ncm.serveNcmApi({ port, host: '0.0.0.0' })
  console.log(`[ncm] API proxy listening on http://0.0.0.0:${port}`)
} else {
  // Older builds expose app.js directly.
  require('NeteaseCloudMusicApi/app.js')
  console.log(`[ncm] API proxy (app.js) listening on port ${port}`)
}
