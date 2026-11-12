'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const crypto = require('crypto');

const port = Number(process.argv[2] || 32123);
const sourcesDir = process.argv[3] || path.join(__dirname, 'sources');
const QUALITY = {
  standard: '128k',
  higher: '320k',
  exhigh: '320k',
  lossless: 'flac',
  hires: 'flac24bit',
  jyeffect: 'atmos',
  sky: 'atmos',
  jymaster: 'master'
};

const cache = new Map();
const queues = new Map();

function log() {
  try { console.log.apply(console, arguments); } catch (_) {}
}

function createLx() {
  let handler = null;
  let waiter = null;
  let sentUrl = null;
  let sentError = null;

  function notify() {
    if (waiter) {
      const w = waiter;
      waiter = null;
      w(sentUrl);
    }
  }

  function parseData(data) {
    if (typeof data === 'string') {
      try { return JSON.parse(data); } catch (_) { return null; }
    }
    return data;
  }

  const lx = {
    EVENT_NAMES: {
      request: 'request', inited: 'inited', updateAlert: 'updateAlert',
      error: 'error', download: 'download', log: 'log', progress: 'progress'
    },
    on(name, fn) {
      if (String(name) === 'request') handler = fn;
    },
    send(name, data) {
      if (String(name) !== 'request') return;
      const d = parseData(data);
      if (!d || d.action !== 'musicUrl') return;
      const p = d.data;
      const url = typeof p === 'string' ? p : (p && (p.url || p.musicUrl));
      if (url) sentUrl = url;
      notify();
    },
    request(url, options, callback) {
      const opts = options || {};
      const target = String(url || '');
      const headers = opts.headers || {};
      const method = opts.method || 'GET';
      let body = opts.body;
      if (body && typeof body !== 'string' && !(body instanceof Uint8Array)) {
        body = JSON.stringify(body);
        headers['Content-Type'] = headers['Content-Type'] || 'application/json';
      }
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 20000);
      const promise = fetch(target, { method, headers, body, signal: controller.signal })
        .then(async (resp) => {
          const text = await resp.text();
          return {
            statusCode: resp.status,
            statusMessage: resp.statusText,
            headers: Object.fromEntries(resp.headers.entries()),
            body: text
          };
        })
        .finally(() => clearTimeout(timer));
      if (typeof callback === 'function') {
        promise.then((resp) => callback(null, resp)).catch((err) => callback({ message: err.message }, null));
        return;
      }
      return promise;
    },
    utils: {},
    env: { os: 'android', appVersion: '2.9.0', version: '2.9.0', isMobile: true },
    version: '2.9.0',
    currentScriptInfo: {},
    handler: null,
    reset() {
      sentUrl = null;
      sentError = null;
      waiter = null;
    },
    nextSend() {
      if (sentUrl) return Promise.resolve(sentUrl);
      return new Promise((resolve) => { waiter = resolve; });
    }
  };
  Object.defineProperty(lx, 'handler', { get: () => handler });
  return lx;
}

function loadSource(entry) {
  const file = entry.path;
  if (!file || !fs.existsSync(file)) throw new Error('source file missing: ' + file);
  const stat = fs.statSync(file);
  const key = entry.id + ':' + file;
  const cached = cache.get(key);
  if (cached && cached.mtime === stat.mtimeMs && cached.size === stat.size) return cached.loaded;

  const source = fs.readFileSync(file, 'utf8');
  const lx = createLx();
  const sandboxProcess = Object.create(process);
  sandboxProcess.exit = () => { throw new Error('process.exit blocked in source'); };
  sandboxProcess.abort = () => { throw new Error('process.abort blocked in source'); };
  const sandbox = {
    console,
    process: sandboxProcess,
    Buffer,
    setTimeout,
    clearTimeout,
    setInterval,
    clearInterval,
    setImmediate,
    URL,
    URLSearchParams,
    TextEncoder,
    TextDecoder,
    AbortController,
    AbortSignal,
    fetch,
    Headers,
    Request,
    Response,
    crypto: crypto.webcrypto,
    require,
    module: { exports: {} },
    exports: {},
    lx
  };
  sandbox.global = sandbox;
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  vm.runInContext(source, sandbox, { filename: file, timeout: 15000 });
  const loaded = {
    lx,
    sandbox,
    handler: lx.handler,
    simple: (typeof sandbox.sourceInfo === 'function' && typeof sandbox.resolve === 'function' && !lx.handler) ? sandbox.resolve : null,
    queue: Promise.resolve()
  };
  cache.set(key, { mtime: stat.mtimeMs, size: stat.size, loaded });
  log('loaded source', entry.id, entry.path, 'handler=' + !!loaded.handler, 'simple=' + !!loaded.simple);
  return loaded;
}

function musicInfo(song, quality) {
  return {
    songmid: String(song.id),
    songId: String(song.id),
    musicId: String(song.id),
    rid: String(song.id),
    id: song.id,
    name: song.name || '',
    singer: song.artist || '',
    albumName: song.album || '',
    albumId: '',
    interval: String(Math.floor((song.duration || 0) / 1000)),
    img: song.cover || '',
    hash: String(song.id),
    copyrightId: '',
    source: song.source || 'wy',
    type: song.source || 'wy',
    types: [{ type: song.source || 'wy', quality: QUALITY[quality] || 'flac' }]
  };
}

async function resolveOne(entry, song, quality) {
  const loaded = loadSource(entry);
  const run = async () => {
    const started = Date.now();
    const lxQuality = QUALITY[quality] || 'flac';
    if (loaded.simple) {
      const result = await Promise.resolve(loaded.simple(JSON.stringify(song)));
      const url = typeof result === 'string' ? result : (result && (result.url || result.musicUrl));
      if (url) return { id: entry.id, url, latencyMs: Date.now() - started };
      return { id: entry.id, error: 'simple resolve returned empty', latencyMs: Date.now() - started };
    }
    if (!loaded.handler) {
      return { id: entry.id, error: 'no request handler', latencyMs: Date.now() - started };
    }
    loaded.lx.reset();
    const info = musicInfo(song, quality);
    const wait = loaded.lx.nextSend();
    const timeout = new Promise((resolve) => setTimeout(() => resolve(null), 20000));
    try {
      const requestInfo = {
        source: song.source || 'wy',
        action: 'musicUrl',
        info: {
          // LX protocol: info.type is the requested quality (128k/320k/flac...).
          type: lxQuality,
          quality: lxQuality,
          musicInfo: info
        }
      };
      const direct = await Promise.resolve(loaded.handler(requestInfo));
      const directUrl = typeof direct === 'string' ? direct : (direct && (direct.url || direct.musicUrl));
      if (directUrl) return { id: entry.id, url: directUrl, latencyMs: Date.now() - started };
      const sent = await Promise.race([wait, timeout]);
      if (sent) return { id: entry.id, url: sent, latencyMs: Date.now() - started };
      return { id: entry.id, error: 'timeout waiting for lx.send', latencyMs: Date.now() - started };
    } catch (err) {
      return { id: entry.id, error: String(err && err.message ? err.message : err), latencyMs: Date.now() - started };
    }
  };

  const previous = queues.get(entry.id) || Promise.resolve();
  const current = previous.then(run, run);
  queues.set(entry.id, current.catch(() => {}));
  return current;
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => { body += chunk; });
    req.on('end', () => resolve(body));
    req.on('error', reject);
  });
}

process.on('uncaughtException', (err) => {
  log('uncaughtException: ' + String(err && err.stack ? err.stack : err));
});
process.on('unhandledRejection', (err) => {
  log('unhandledRejection: ' + String(err && err.stack ? err.stack : err));
});

const server = http.createServer(async (req, res) => {
  res.setHeader('Content-Type', 'application/json; charset=utf-8');
  if (req.method === 'GET' && req.url === '/health') {
    res.end(JSON.stringify({ ok: true }));
    return;
  }
  if (req.method !== 'POST' || req.url !== '/resolve') {
    res.statusCode = 404;
    res.end(JSON.stringify({ error: 'not found' }));
    return;
  }
  try {
    const body = JSON.parse(await readBody(req));
    const song = body.song || {};
    const quality = body.quality || 'lossless';
    const entries = Array.isArray(body.sources) ? body.sources : [];
    const started = Date.now();
    const results = await Promise.all(entries.map((entry) => resolveOne(entry, song, quality)));
    log('resolve', song.id, quality, 'sources=' + entries.length, 'ms=' + (Date.now() - started));
    res.end(JSON.stringify({ results }));
  } catch (err) {
    res.statusCode = 500;
    res.end(JSON.stringify({ error: String(err && err.message ? err.message : err) }));
  }
});

server.on('error', (err) => {
  log('server error: ' + String(err && err.stack ? err.stack : err));
});
server.listen(port, '127.0.0.1', () => {
  log('node-source-server ready on ' + port + ' dir=' + sourcesDir);
});
