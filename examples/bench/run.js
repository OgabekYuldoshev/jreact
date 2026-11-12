#!/usr/bin/env node
// Runs the benchmark pages (JReact, React 18, vanilla DOM) in headless Brave/Chrome and prints a table.
//   node run.js [rounds]         (normally: ./build.sh bench [rounds])
// Needs: examples/bench built (mvn), npm install in examples/, Brave or Chrome (BROWSER=/path to override).
'use strict';
const fs = require('fs'), os = require('os'), path = require('path'), http = require('http'), zlib = require('zlib');
const { spawn } = require('child_process');

const here = __dirname;
const nm = path.join(here, '../node_modules');
const rounds = Number(process.argv[2] || process.env.ROUNDS || 3);
const dist = path.join(here, 'target/bench-dist');

const browser = process.env.BROWSER || [
  '/Applications/Brave Browser.app/Contents/MacOS/Brave Browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/brave-browser', '/usr/bin/google-chrome', '/usr/bin/chromium',
].find(p => fs.existsSync(p));
if (!browser) { console.error('no Brave/Chrome found: set BROWSER=/path/to/browser'); process.exit(2); }
for (const f of [path.join(here, 'target/web/app.js'), path.join(nm, 'react/umd/react.production.min.js')]) {
  if (!fs.existsSync(f)) { console.error('missing ' + f + ' (build first: ./build.sh bench)'); process.exit(2); }
}

// ---- assemble pages
fs.rmSync(dist, { recursive: true, force: true });
for (const d of ['jreact', 'react', 'vanilla']) fs.mkdirSync(path.join(dist, d), { recursive: true });
fs.cpSync(path.join(here, 'target/web'), path.join(dist, 'jreact'), { recursive: true });
for (const [dir, page] of [['react', 'react.html'], ['vanilla', 'vanilla.html']]) {
  fs.copyFileSync(path.join(here, 'contenders', page), path.join(dist, dir, 'index.html'));
  fs.copyFileSync(path.join(here, 'contenders/harness.js'), path.join(dist, dir, 'harness.js'));
}
fs.copyFileSync(path.join(nm, 'react/umd/react.production.min.js'), path.join(dist, 'react/react.production.min.js'));
fs.copyFileSync(path.join(nm, 'react-dom/umd/react-dom.production.min.js'), path.join(dist, 'react/react-dom.production.min.js'));

const server = http.createServer((req, res) => {
  let f = path.join(dist, decodeURIComponent(req.url.split('?')[0]));
  if (f.endsWith(path.sep) || (fs.existsSync(f) && fs.statSync(f).isDirectory())) f = path.join(f, 'index.html');
  if (!f.startsWith(dist) || !fs.existsSync(f)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'Content-Type': f.endsWith('.html') ? 'text/html' : 'text/javascript', 'Cache-Control': 'no-store' });
  fs.createReadStream(f).pipe(res);
});

const sleep = ms => new Promise(r => setTimeout(r, ms));
const median = a => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1]; };
const gz = f => zlib.gzipSync(fs.readFileSync(f), { level: 9 }).length;
const kb = n => (n / 1024).toFixed(1) + ' KB';

async function cdp(port, url) {            // open a tab, wait for window.__result, close the tab
  const tab = await (await fetch(`http://127.0.0.1:${port}/json/new?${encodeURI(url)}`, { method: 'PUT' })).json();
  const ws = new WebSocket(tab.webSocketDebuggerUrl);
  await new Promise((ok, fail) => { ws.onopen = ok; ws.onerror = fail; });
  let id = 0; const pending = new Map();
  ws.onmessage = m => { const d = JSON.parse(m.data); if (pending.has(d.id)) { pending.get(d.id)(d); pending.delete(d.id); } };
  const call = (method, params) => new Promise(r => { pending.set(++id, r); ws.send(JSON.stringify({ id, method, params })); });
  try {
    for (let i = 0; i < 600; i++) {            // up to 5 min
      await sleep(500);
      const r = await call('Runtime.evaluate', { expression: 'window.__result || null', returnByValue: true });
      const v = r.result && r.result.result && r.result.result.value;
      if (v) return JSON.parse(v);
    }
    throw new Error('timeout waiting for ' + url);
  } finally { ws.close(); await fetch(`http://127.0.0.1:${port}/json/close/${tab.id}`); }
}

(async () => {
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'jreact-bench-'));
  const port = 9300 + Math.floor(Math.random() * 500);
  const proc = spawn(browser, ['--headless=new', `--remote-debugging-port=${port}`, `--user-data-dir=${profile}`,
    '--no-first-run', '--disable-extensions', '--disable-background-timer-throttling', '--disable-renderer-backgrounding', 'about:blank'], { stdio: 'ignore' });
  try {
    for (let i = 0; ; i++) {
      try { await fetch(`http://127.0.0.1:${port}/json/version`); break; } catch { if (i > 60) throw new Error('browser did not start'); await sleep(250); }
    }
    const version = (await (await fetch(`http://127.0.0.1:${port}/json/version`)).json()).Browser;
    const names = ['vanilla', 'react', 'jreact', 'jreact?dev'];
    const runs = Object.fromEntries(names.map(n => [n, []]));
    for (let r = 0; r < rounds; r++) {
      for (const n of names) { process.stderr.write(`round ${r + 1}/${rounds}: ${n}...\n`); runs[n].push(await cdp(port, `${base}/${n.includes('?') ? n.replace('?', '/?') : n + '/'}`)); }
    }
    const scenarios = Object.keys(runs.vanilla[0]);
    const m = (n, s, k = 'median') => median(runs[n].map(x => x[s][k]));
    const out = [];
    out.push(`Browser: ${/brave/i.test(browser) ? 'Brave (' + version + ')' : version}, ${os.cpus()[0].model}, ${os.platform()} ${os.arch()}; median of ${rounds} page loads x 10 runs (ms, lower is better; includes forced layout)\n`);
    out.push('| scenario | vanilla DOM | React 18 | JReact | JReact / React | JReact dev mode |');
    out.push('|---|---:|---:|---:|---:|---:|');
    for (const s of scenarios) {
      const v = m('vanilla', s), re = m('react', s), j = m('jreact', s);
      out.push(`| ${s} | ${v} | ${re} | ${j} | ${(j / re).toFixed(2)}x | ${m('jreact?dev', s)} |`);
    }
    const reactSize = ['react.production.min.js', 'react-dom.production.min.js'].map(f => path.join(dist, 'react', f));
    const appJs = path.join(dist, 'jreact/app.js');
    out.push('');
    out.push(`Bundle: JReact app.js (this table app) ${kb(fs.statSync(appJs).size)}, gzip ${kb(gz(appJs))}; React 18 react + react-dom ${kb(reactSize.reduce((a, f) => a + fs.statSync(f).size, 0))}, gzip ${kb(reactSize.reduce((a, f) => a + gz(f), 0))} (without the app).`);
    const text = out.join('\n');
    console.log(text);
    fs.writeFileSync(path.join(here, 'target/results.md'), text + '\n');
  } finally {
    await new Promise(r => { proc.once('exit', r); proc.kill(); setTimeout(r, 3000); });
    server.close();
    try { fs.rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }); } catch {}
  }
})().catch(e => { console.error(e); process.exit(1); });
