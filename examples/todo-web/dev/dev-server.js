#!/usr/bin/env node
// Dev server: serves target/web, rebuilds with Maven when a source file changes, reloads the page.
//   node dev-server.js [port]      (normally: ./build.sh dev)
// No dependencies. Live reload = Server-Sent Events, injected into index.html on the fly.
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const root = path.resolve(__dirname, '../../..');
const webDir = path.join(root, 'examples/todo-web/target/web');
const port = Number(process.argv[2] || process.env.PORT || 5173);
const watched = [
  'jreact-core/src', 'jreact-compiler/src/main', 'jreact-scheduler/src/main', 'jreact-reconciler/src/main',
  'jreact-dom/src/main', 'jreact-web/src/main', 'examples/todo/src', 'examples/todo-web/src',
].map(p => path.join(root, p));

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css', '.map': 'application/json', '.json': 'application/json' };
const clients = new Set();
let building = false, again = false, lastError = null;

const reloadSnippet = `<script>
new EventSource('/__reload').onmessage = e => {
  if (e.data === 'reload') location.reload();
  if (e.data === 'building') document.title = '⏳ ' + document.title.replace(/^⏳ /, '');
};
</script>`;
const errorPage = msg => `<!doctype html><meta charset="utf-8"><title>Build failed</title>
<body style="font:14px ui-monospace,monospace;padding:1rem;background:#1e1e1e;color:#f88">
<h3>Build failed</h3><pre style="white-space:pre-wrap">${msg.replace(/[&<]/g, c => c === '&' ? '&amp;' : '&lt;')}</pre>${reloadSnippet}`;

function send(data) { for (const c of clients) c.write(`data: ${data}\n\n`); }

function build() {
  if (building) { again = true; return; }
  building = true; send('building');
  const t0 = Date.now(), out = [];
  console.log('[dev] building...');
  const p = spawn('mvn', ['-B', '-q', '-pl', 'examples/todo-web', '-am', 'package'], { cwd: root });
  p.stdout.on('data', d => out.push(d)); p.stderr.on('data', d => out.push(d));
  p.on('close', code => {
    building = false;
    if (code === 0) { lastError = null; console.log(`[dev] ok, ${Date.now() - t0} ms`); }
    else { lastError = Buffer.concat(out).toString(); console.error('[dev] FAILED\n' + lastError); }
    send('reload');
    if (again) { again = false; build(); }
  });
}

// mtime polling: portable, no fs.watch quirks. Debounced by the "building/again" flags.
function snapshot() {
  let sum = 0, n = 0;
  const walk = d => {
    let es; try { es = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of es) {
      const f = path.join(d, e.name);
      if (e.isDirectory()) walk(f);
      else { n++; sum = (sum + fs.statSync(f).mtimeMs) % 1e15; }
    }
  };
  watched.forEach(walk);
  return n + ':' + sum;
}
let last = snapshot();
setInterval(() => { const s = snapshot(); if (s !== last) { last = s; build(); } }, 500);

http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/__reload') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    res.write('\n'); clients.add(res); req.on('close', () => clients.delete(res)); return;
  }
  if (lastError && (url === '/' || url === '/index.html')) {
    res.writeHead(200, { 'Content-Type': types['.html'] }); res.end(errorPage(lastError)); return;
  }
  const file = path.join(webDir, url === '/' ? 'index.html' : url);
  if (!file.startsWith(webDir) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    res.writeHead(404); res.end('not found'); return;
  }
  const type = types[path.extname(file)] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': type, 'Cache-Control': 'no-store' });
  if (path.extname(file) === '.html') res.end(fs.readFileSync(file, 'utf8').replace('</body>', reloadSnippet + '</body>'));
  else fs.createReadStream(file).pipe(res);
}).listen(port, () => console.log(`[dev] http://localhost:${port}  (watching sources, Ctrl+C to stop)`));

build();
