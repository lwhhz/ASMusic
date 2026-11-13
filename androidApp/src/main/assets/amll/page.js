/* AMLL page bridge: the Kotlin side pushes lyric / time / playing / cover state
 * through the amll* globals below. */
window.onerror = function (m, src, line, col) {
  window.__err = String(m) + " @" + line;
};

(function () {
  "use strict";

  var lyricHost = document.getElementById("lyrics");
  var bgHost = document.getElementById("bg");
  var hint = document.getElementById("hint");

  var player = null;
  var background = null;
  var timeMs = 0;
  var playing = false;
  var lastCover = "";
  var lines = null;
  var touching = false;
  var touchEndedAt = 0;
  var lastTouchAt = 0;
  var resumeDelayMs = 1500;
  var forceSnap = false;
  var pendingReturn = false;
  var lastLyricKey = "";
  var styleEl = null;

  function ensurePlayer() {
    if (player) return player;
    try {
      player = new AMLL.LyricPlayer();
      var el = player.getElement();
      el.style.width = "100%";
      el.style.height = "100%";
      lyricHost.appendChild(el);
      player.setEnableBlur(true);
      player.setEnableScale(true);
      // Keep the focused line slightly above the vertical centre, like Apple Music.
      player.setAlignAnchor("center");
      player.setAlignPosition(0.42);
      player.pause();
      window.__player = player;
    } catch (e) {
      player = null;
      console.error("AMLL LyricPlayer init failed", e);
    }
    return player;
  }

  function ensureBackground() {
    if (background) return background;
    try {
      background = AMLL.BackgroundRender.new(AMLL.MeshGradientRenderer);
      var el = background.getElement();
      el.style.cssText =
        "position:absolute;inset:0;width:100%;height:100%;display:block;";
      bgHost.appendChild(el);
      background.setFlowSpeed(1);
      background.setFPS(30);
      background.resume();
    } catch (e) {
      background = null;
      console.error("AMLL BackgroundRender init failed", e);
    }
    return background;
  }

  /* [{t, text, tr?, w?: [{t,d,s}]}] -> AMLL lyric lines. */
  function linesFromSimple(items) {
    var out = [];
    if (!items || !items.length) return out;
    for (var i = 0; i < items.length; i++) {
      var item = items[i];
      var t = item.t || 0;
      var text = item.text || "";
      if (!text) continue;
      var next = i + 1 < items.length ? items[i + 1].t : 0;
      if (!next || next <= t) next = t + 3000;
      var words;
      if (item.w && item.w.length) {
        words = item.w.map(function (w) {
          return {
            startTime: w.t || 0,
            endTime: (w.t || 0) + Math.max(1, w.d || 1),
            word: w.s || "",
          };
        });
      } else {
        words = [{ startTime: t, endTime: next, word: text }];
      }
      out.push({
        words: words,
        translatedLyric: item.tr || "",
        romanLyric: "",
        isBG: false,
        isDuet: false,
        startTime: t,
        endTime: next,
      });
    }
    return out;
  }

  function applyLines(linesIn) {
    lines = linesIn;
    window.__lineCount = linesIn.length;
    var p = ensurePlayer();
    if (p) {
      try {
        p.setLyricLines(linesIn, timeMs);
      } catch (e) {
        window.__err = "setLyricLines: " + (e && e.message ? e.message : e);
      }
      if (playing) p.resume();
      else p.pause();
    }
    if (background) background.setHasLyric(!!linesIn.length);
    if (hint) hint.style.display = linesIn.length ? "none" : "flex";
  }

  /* TTML files usually carry no translation and sometimes only one span per line;
   * fill both from the Netease copy (which is what the lyric cards show) so nothing
   * "disappears" when TTML wins. Index alignment first, time matching as fallback. */
  function mergeSimpleInto(lines, simple) {
    if (!simple || !simple.length || !lines || !lines.length) return lines;
    var byIndex = Math.abs(simple.length - lines.length) <= 2;
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      if (!line) continue;
      var startTime = typeof line.startTime === "number" ? line.startTime : 0;
      var tr = "";
      var words = null;
      var aligned = byIndex ? simple[i] : null;
      if (aligned) {
        if (aligned.tr) tr = aligned.tr;
        if (aligned.w && aligned.w.length > 1) words = aligned.w;
      }
      if (!tr) {
        var bestDelta = 3000;
        for (var j = 0; j < simple.length; j++) {
          if (!simple[j].tr) continue;
          var d = Math.abs((simple[j].t || 0) - startTime);
          if (d <= bestDelta) {
            bestDelta = d;
            tr = simple[j].tr;
          }
        }
      }
      if (!(line.translatedLyric && line.translatedLyric.trim().length) && tr) {
        line.translatedLyric = tr;
      }
      if (!words) {
        var bestWordDelta = 1500;
        for (var k = 0; k < simple.length; k++) {
          var item = simple[k];
          if (!item.w || item.w.length < 2) continue;
          var delta = Math.abs((item.t || 0) - startTime);
          if (delta <= bestWordDelta) {
            bestWordDelta = delta;
            words = item.w;
          }
        }
      }
      if (words && (!line.words || line.words.length <= 1)) {
        line.words = words.map(function (w) {
          return {
            startTime: w.t || 0,
            endTime: (w.t || 0) + Math.max(1, w.d || 1),
            word: w.s || "",
          };
        });
      }
    }
    return lines;
  }

  window.amllSetLyric = function (ttml, simpleJson) {
    var key = (ttml ? ttml.length : 0) + "|" + (simpleJson ? simpleJson.length : 0) + "|" + (ttml || "").slice(0, 64);
    if (key === lastLyricKey) return;
    lastLyricKey = key;

    var simple = [];
    try {
      simple = JSON.parse(simpleJson || "[]");
    } catch (e) {
      simple = [];
    }
    var lines = null;
    if (ttml && ttml.length > 0) {
      try {
        var parsed = AMLL.parseTTML(ttml);
        if (parsed && parsed.lines && parsed.lines.length) lines = parsed.lines;
      } catch (e) {
        console.warn("TTML parse failed, falling back to Netease lines", e);
      }
    }
    if (!lines) {
      lines = linesFromSimple(simple);
    } else {
      lines = mergeSimpleInto(lines, simple);
    }
    try {
      window.__l0 = lines && lines[0] ? JSON.stringify(lines[0]).slice(0, 150) : "none";
      window.__ttml = ttml ? ttml.length : 0;
    } catch (e) {}
    applyLines(lines);
  };

  window.amllSetTime = function (ms) {
    timeMs = ms || 0;
    if (!player) return;
    if (forceSnap) {
      forceSnap = false;
      pendingReturn = false;
      player.setCurrentTime(timeMs, true);
      if (player.resetScroll) player.resetScroll();
      return;
    }
    // A lost touchend used to freeze the lyric forever: recover after 1.5 s idle.
    if (touching && performance.now() - lastTouchAt > 1500) {
      touching = false;
      touchEndedAt = performance.now();
    }
    if (touching) return;
    if (resumeDelayMs > 0 && performance.now() - touchEndedAt < resumeDelayMs) {
      // Browsing: hold the view, remember that we owe a re-centre.
      pendingReturn = true;
      return;
    }
    // After browsing the current line may not have changed at all (long line or
    // instrumental), so a plain update would not scroll back. Force both a seek and
    // an explicit scroll reset, which re-centres on the playing line.
    var returning = pendingReturn;
    pendingReturn = false;
    if (returning) {
      try {
        player.setCurrentTime(timeMs, true);
        if (player.resetScroll) player.resetScroll();
      } catch (e) {
        player.setCurrentTime(timeMs, false);
      }
      return;
    }
    player.setCurrentTime(timeMs, false);
  };

  window.amllSetStyle = function (json) {
    var style;
    try {
      style = JSON.parse(json);
    } catch (e) {
      return;
    }
    resumeDelayMs = Math.max(0, style.resume || 0) * 1000;
    // Index order must match SystemFontLabels in AccountExtras.kt.
    var families = [
      "sans-serif",
      "sans-serif-light",
      "sans-serif-condensed",
      "sans-serif-medium",
      "serif",
      "monospace",
      "casual",
      "cursive",
    ];
    var family = families[style.family] || families[0];
    // Chinese glyphs only fall back through these; many Android "sans-serif-*" families
    // are Latin-only, so list CJK families explicitly after the chosen one.
    var cjk = [
      "'Noto Sans CJK SC'", "'Source Han Sans SC'", "'Noto Sans SC'", "'PingFang SC'",
      "'Microsoft YaHei'", "'Droid Sans Fallback'",
    ];
    var cjkSerif = [
      "'Noto Serif CJK SC'", "'Source Han Serif SC'", "'Noto Serif SC'", "'Songti SC'",
    ];
    var stack = family.split(",");
    stack = stack.concat(style.family === 4 ? cjkSerif : cjk);
    if (!styleEl) {
      styleEl = document.createElement("style");
      document.head.appendChild(styleEl);
    }
    var face = "";
    if (style.fontUrl) {
      stack = ["'AmllImported'"].concat(stack);
      face =
        "@font-face{font-family:'AmllImported';src:url('" + style.fontUrl + "') format('opentype');" +
        "font-display:block;}";
    }
    styleEl.textContent =
      face +
      "#lyrics .amll-lyric-player{--amll-lp-font-size:" + style.size + "px;" +
      "font-family:" + stack.join(",") + " !important;font-weight:" + style.weight +
      " !important;} #lyrics .amll-lyric-player *{font-family:inherit !important;" +
      "font-weight:inherit !important;}";
  };

  /* Fonts are pure CSS; rebuilding the lyric view here used to make the lyrics
   * flash mid-playback (and could drop word-level rendering), so just re-apply the
   * style instead. */
  window.amllFontsReady = function () {
    if (!document.fonts) return;
    document.fonts.ready.then(function () {
      if (styleEl && styleEl.textContent) {
        var css = styleEl.textContent;
        styleEl.textContent = "";
        styleEl.textContent = css;
      }
    });
  };

  window.amllSetMask = function (on) {
    if (!player || !player.setLyricProcessConfig) return;
    try {
      player.setLyricProcessConfig({ maskMode: on ? "partial-mask" : "" });
      player.rebuildLyricView(timeMs);
    } catch (e) {
      console.warn("mask config failed", e);
    }
  };

  window.amllSetPlaying = function (value) {
    playing = !!value;
    if (!player) return;
    if (playing) player.resume();
    else player.pause();
  };

  // ---- 静态背景：封面居中裁切（cover，不拉伸）+ 模糊，替换掉 Pixi 流体背景 ----
  var staticBgOn = false, staticCover = null, staticLayer = null, staticShade = null;

  function ensureStaticLayer() {
    if (staticLayer) return;
    staticShade = document.createElement("div");
    staticShade.style.cssText =
      "position:fixed;inset:0;background:#000;opacity:0;transition:opacity .35s ease;" +
      "z-index:0;pointer-events:none;";
    staticLayer = document.createElement("div");
    staticLayer.style.cssText =
      "position:fixed;inset:-8%;background-size:cover;background-position:center;" +
      "background-repeat:no-repeat;filter:blur(46px);transform:scale(1.06);opacity:0;" +
      "transition:opacity .35s ease;z-index:0;pointer-events:none;";
    document.body.insertBefore(staticShade, document.body.firstChild);
    document.body.insertBefore(staticLayer, document.body.firstChild);
  }

  function applyStaticBg() {
    ensureStaticLayer();
    if (staticCover) staticLayer.style.backgroundImage = "url(" + staticCover + ")";
    staticLayer.style.opacity = staticBgOn ? "1" : "0";
    staticShade.style.opacity = staticBgOn ? "0.32" : "0";
    var canvases = document.querySelectorAll("canvas");
    for (var i = 0; i < canvases.length; i++) canvases[i].style.opacity = staticBgOn ? "0" : "";
  }

  window.amllSetStaticBg = function (on) {
    staticBgOn = !!on;
    applyStaticBg();
  };

  window.amllSetCover = function (dataUrl) {
    staticCover = dataUrl || null;
    applyStaticBg();
    if (!dataUrl || dataUrl === lastCover) return;
    var bg = ensureBackground();
    if (!bg) return;
    var img = new Image();
    img.onload = function () {
      lastCover = dataUrl;
      try {
        bg.setAlbum(img, false);
      } catch (e) {
        console.error("setAlbum failed", e);
      }
    };
    img.src = dataUrl;
  };

  // Tap a lyric line -> ask the host to seek there.
  function bindLineClicks() {
    if (!player || player.__bound) return;
    player.__bound = true;
    player.addEventListener("line-click", function (evt) {
      var t = 0;
      try {
        if (evt && evt.line && typeof evt.line.startTime === "number") t = evt.line.startTime;
        else if (evt && typeof evt.lineIndex === "number" && lines && lines[evt.lineIndex]) {
          t = lines[evt.lineIndex].startTime || 0;
        }
      } catch (e) {}
      if (t > 0 && window.AmllHost && window.AmllHost.seekTo) {
        // Jump right away instead of waiting out the browsing delay.
        touching = false;
        touchEndedAt = 0;
        forceSnap = true;
        window.AmllHost.seekTo(t);
      }
    });
  }

  lyricHost.addEventListener("touchstart", function () {
    touching = true;
    lastTouchAt = performance.now();
  }, { passive: true });
  lyricHost.addEventListener("touchmove", function () {
    lastTouchAt = performance.now();
  }, { passive: true });
  lyricHost.addEventListener("touchend", function () {
    touching = false;
    lastTouchAt = performance.now();
    touchEndedAt = performance.now();
  }, { passive: true });
  lyricHost.addEventListener("touchcancel", function () {
    touching = false;
    lastTouchAt = performance.now();
    touchEndedAt = performance.now();
  }, { passive: true });

  // Pause rendering while the WebView is not visible.
  document.addEventListener("visibilitychange", function () {
    if (!player) return;
    if (document.hidden) player.pause();
    else if (playing) player.resume();
  });

  ensurePlayer();
  ensureBackground();

  // AMLL does not drive its own frame loop: the host is expected to call update(dt)
  // every frame (see the setCurrentTime docs in @applemusic-like-lyrics/core).
  var lastFrame = 0;
  function frame(ts) {
    var dt = lastFrame ? Math.min(ts - lastFrame, 100) : 16;
    lastFrame = ts;
    // The surface sits at 1 dp while the immersive page is closed: keep the Pixi
    // context alive but stop burning frames.
    var onScreen = document.documentElement.clientWidth > 60;
    if (background) {
      // Static cover background: pause the fluid render loop entirely instead of
      // merely hiding the canvas, otherwise Pixi keeps drawing every frame.
      if (onScreen && !staticBgOn) background.resume();
      else background.pause();
    }
    if (player && !document.hidden && onScreen) {
      try {
        bindLineClicks();
        player.update(dt);
      } catch (e) {
        window.__err = "update: " + (e && e.message ? e.message : e);
      }
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);
})();
