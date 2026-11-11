#!/usr/bin/env node
// Dev server for all example apps: one port, live reload, rebuilds only the app that changed.
//   node tools/dev-server.js [port] [app...]      (normally: ./build.sh dev [port] [app...])
//
//   /           gallery        /todo/  examples/todo-web (plain CSS)
//   /kanban/    /shop/    /dashboard/    /landing/      (Tailwind CSS v4)
//
// Source change in an app            -> Maven for that app + Tailwind CLI, then reload that app's tabs
// Change in jreact-* (framework)     -> every app is rebuilt
// No dependencies. Live reload = Server-Sent Events, injected into index.html on the fly.
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const root = path.resolve(__dirname, '..');
const examples = path.join(root, 'examples');

// name -> module dir (under examples/) and whether it uses Tailwind
const ALL = {
  todo: { dir: 'todo-web', extra: ['examples/todo/src'], tailwind: false },
  kanban: { dir: 'kanban', tailwind: true },
  shop: { dir: 'shop', tailwind: true },
  dashboard: { dir: 'dashboard', tailwind: true },
  landing: { dir: 'landing', tailwind: true },
};
const args = process.argv.slice(2);
const port = Number(args.find(a => /^\d+$/.test(a)) || process.env.PORT || 5173);
const wanted = args.filter(a => !/^\d+$/.test(a));
for (const w of wanted) if (!ALL[w]) { console.error(`unknown app "${w}". apps: ${Object.keys(ALL).join(', ')}`); process.exit(2); }
const names = wanted.length ? wanted : Object.keys(ALL);
const apps = Object.fromEntries(names.map(n => [n, {
  ...ALL[n], name: n, module: 'examples/' + ALL[n].dir,
  web: path.join(examples, ALL[n].dir, 'target/web'),
  watch: [path.join(examples, ALL[n].dir, 'src'), ...(ALL[n].extra || []).map(p => path.join(root, p))],
  error: null, building: false, again: false,
}]));

const frameworkDirs = ['jreact-core/src', 'jreact-compiler/src/main', 'jreact-scheduler/src/main',
  'jreact-reconciler/src/main', 'jreact-dom/src/main', 'jreact-web/src/main'].map(p => path.join(root, p));
const galleryDir = path.join(examples, 'gallery');
const galleryCss = path.join(galleryDir, 'target/gallery.css');

const types = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css', '.map': 'application/json', '.json': 'application/json' };
const clients = new Set();

const reloadSnippet = app => `<script>
new EventSource('/__reload').onmessage = e => {
  const [what, who] = e.data.split(' ');
  const mine = who === '*' || who === ${JSON.stringify(app)};
  if (what === 'reload' && mine) location.reload();
  if (what === 'building' && mine) document.title = '⏳ ' + document.title.replace(/^⏳ /, '');
};
</script>`;
const errorPage = (app, msg) => `<!doctype html><meta charset="utf-8"><title>Build failed</title>
<body style="font:14px ui-monospace,monospace;padding:1rem;background:#1e1e1e;color:#f88">
<h3>Build failed: ${app}</h3><pre style="white-space:pre-wrap">${msg.replace(/[&<]/g, c => c === '&' ? '&amp;' : '&lt;')}</pre>${reloadSnippet(app)}`;

function send(what, who) { for (const c of clients) c.write(`data: ${what} ${who}\n\n`); }

function run(cmd, cmdArgs, cwd) {
  return new Promise(resolve => {
    const out = [];
    const p = spawn(cmd, cmdArgs, { cwd });
    p.stdout.on('data', d => out.push(d)); p.stderr.on('data', d => out.push(d));
    p.on('error', e => resolve({ code: 1, out: String(e) }));
    p.on('close', code => resolve({ code, out: Buffer.concat(out).toString() }));
  });
}

const tailwind = (input, output) =>
  run('npx', ['@tailwindcss/cli', '-i', input, '-o', output, '--minify'], examples);

/** Rebuild the given apps (one Maven call), then Tailwind for each, then reload. */
const queue = new Set();
let busy = false;
async function build(list) {
  list.forEach(a => queue.add(a));
  if (busy) return;
  busy = true;
  while (queue.size) {
    const batch = [...queue]; queue.clear();
    batch.forEach(a => { a.building = true; send('building', a.name); });
    const t0 = Date.now();
    console.log(`[dev] building ${batch.map(a => a.name).join(', ')}...`);
    const mvn = await run('mvn', ['-B', '-q', '-pl', batch.map(a => a.module).join(','), '-am', 'package'], root);
    for (const a of batch) {
      a.building = false;
      if (mvn.code !== 0) { a.error = mvn.out; continue; }
      a.error = null;
      if (a.tailwind) {
        const css = await tailwind(`${a.dir}/src/main/styles/app.css`, `${a.dir}/target/web/app.css`);
        if (css.code !== 0) a.error = 'Tailwind failed:\n' + css.out;
      }
    }
    const failed = batch.filter(a => a.error);
    if (failed.length) failed.forEach(a => console.error(`[dev] FAILED ${a.name}\n${a.error}`));
    else console.log(`[dev] ok, ${Date.now() - t0} ms`);
    batch.forEach(a => send('reload', a.name));
  }
  busy = false;
}

async function buildGallery() {
  const r = await tailwind('gallery/gallery.css', 'gallery/target/gallery.css');
  if (r.code !== 0) console.error('[dev] gallery css failed\n' + r.out);
  send('reload', '*');
}

// mtime polling: portable, no fs.watch quirks.
function snapshot(dirs) {
  let sum = 0, n = 0;
  const walk = d => {
    let es; try { es = fs.readdirSync(d, { withFileTypes: true }); } catch { return; }
    for (const e of es) {
      if (e.name === 'target' || e.name === 'node_modules') continue;
      const f = path.join(d, e.name);
      if (e.isDirectory()) walk(f);
      else { n++; sum = (sum + fs.statSync(f).mtimeMs) % 1e15; }
    }
  };
  dirs.forEach(walk);
  return n + ':' + sum;
}
const watchers = [
  { dirs: frameworkDirs, last: snapshot(frameworkDirs), fire: () => build(Object.values(apps)) },
  { dirs: [galleryDir], last: snapshot([galleryDir]), fire: buildGallery },
  ...Object.values(apps).map(a => ({ dirs: a.watch, last: snapshot(a.watch), fire: () => build([a]) })),
];
setInterval(() => {
  for (const w of watchers) {
    const s = snapshot(w.dirs);
    if (s !== w.last) { w.last = s; w.fire(); }
  }
}, 500);

http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/__reload') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache', Connection: 'keep-alive' });
    res.write('\n'); clients.add(res); req.on('close', () => clients.delete(res)); return;
  }
  const notFound = () => { res.writeHead(404); res.end('not found'); };

  // gallery
  const m = url.match(/^\/([^/]+)(\/.*)?$/);
  const app = m && apps[m[1]];
  if (!app) {
    let file;
    if (url === '/' || url === '/index.html') file = path.join(galleryDir, 'index.html');
    else if (url === '/gallery.css') file = galleryCss;
    else return notFound();
    if (!fs.existsSync(file)) return notFound();
    res.writeHead(200, { 'Content-Type': types[path.extname(file)], 'Cache-Control': 'no-store' });
    res.end(path.extname(file) === '.html' ? fs.readFileSync(file, 'utf8').replace('</body>', reloadSnippet('*') + '</body>') : fs.readFileSync(file));
    return;
  }

  // app: /name -> /name/
  if (!m[2]) { res.writeHead(302, { Location: `/${app.name}/` }); res.end(); return; }
  const rel = m[2] === '/' ? 'index.html' : m[2].slice(1);
  if (app.error && rel === 'index.html') {
    res.writeHead(200, { 'Content-Type': types['.html'] }); res.end(errorPage(app.name, app.error)); return;
  }
  const file = path.join(app.web, rel);
  if (!file.startsWith(app.web) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) return notFound();
  res.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
  if (path.extname(file) === '.html') res.end(fs.readFileSync(file, 'utf8').replace('</body>', reloadSnippet(app.name) + '</body>'));
  else fs.createReadStream(file).pipe(res);
}).listen(port, () => {
  console.log(`[dev] http://localhost:${port}   apps: ${names.join(', ')}   (Ctrl+C to stop)`);
  for (const n of names) console.log(`[dev]   http://localhost:${port}/${n}/`);
});

(async () => {
  if (!fs.existsSync(path.join(examples, 'node_modules'))) {
    console.log('[dev] npm install (Tailwind CLI)...');
    await run('npm', ['install', '--silent', '--no-audit', '--no-fund'], examples);
  }
  await buildGallery();
  await build(Object.values(apps));
})();
