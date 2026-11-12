/**
 * Music source plugin — protocol v1
 * ------------------------------------------------------------------
 * The app executes this file with Rhino (JS 1.7) inside the Android process.
 * Only these two globals are part of the contract:
 *
 *   sourceInfo()            -> { name, version, description }
 *   resolve(songJson)       -> playable URL string, or "" to fall back
 *                              to Netease /song/url/v1
 *
 * songJson is a JSON string with: { id, name, artist, album, duration }
 *
 * SECURITY NOTE: imported scripts run arbitrary JS. Only import scripts you
 * trust. The engine has no Java package access (Rhino classpath is sealed).
 */

function sourceInfo() {
  return {
    name: "Sample / 公网测试音源",
    version: "1.0.0",
    description: "Returns a public test MP3 so you can verify network playback without Netease login."
  };
}

function resolve(songJson) {
  var song = JSON.parse(songJson);
  // Public domain-free test stream (SoundHelix). Replace with your own source.
  // Returning "" makes the app fall back to the Netease /song/url/v1 result.
  if (!song || !song.id) return "";
  return "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3";
}
