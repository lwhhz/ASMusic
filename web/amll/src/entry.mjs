import { BackgroundRender, LyricPlayer, MeshGradientRenderer } from "@applemusic-like-lyrics/core";
import "@applemusic-like-lyrics/core/style.css";
import { parseTTML, parseYrc, parseLrc, parseLrcLike } from "@applemusic-like-lyrics/lyric";

// Exposed for the WebView page (assets/amll/page.js).
window.AMLL = {
  BackgroundRender,
  LyricPlayer,
  MeshGradientRenderer,
  parseTTML,
  parseYrc,
  parseLrc,
  parseLrcLike,
};
