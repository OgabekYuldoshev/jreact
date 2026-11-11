// Smoke test of the browser build: loads target/web in jsdom and uses the Todo app like a user would.
// Usage: node smoke.js <dir with index.html and app.js>
const { JSDOM } = require('jsdom');
const fs = require('fs');
const web = process.argv[2];
if (!web) { console.error('usage: node smoke.js <web dir>'); process.exit(2); }
const html = fs.readFileSync(web + '/index.html', 'utf8').replace(/<script[\s\S]*?<\/script>/g, '');
const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true });
const w = dom.window, d = w.document;
const logs = [];
for (const m of ['log','info','warn','error','debug']) w.console[m] = (...a) => logs.push(a.join(' '));
w.eval(fs.readFileSync(web + '/app.js', 'utf8'));
const sleep = ms => new Promise(r => setTimeout(r, ms));
const root = () => d.getElementById('root');
let failed = 0;
const check = (name, ok, extra) => { console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : '  -> ' + extra)); if (!ok) failed++; };

(async () => {
  w.eval('main()');
  await sleep(100);
  check('renders title', root().querySelector('h1')?.textContent === 'JReact Todo', root().innerHTML);
  check('renders 3 todos', root().querySelectorAll('li').length === 3, root().querySelectorAll('li').length);
  check('effect ran', logs.some(l => l.includes('[effect]')), JSON.stringify(logs));

  const input = root().querySelector('input');
  const submitBtn = root().querySelector('button[type=submit]');
  check('submit disabled while draft empty', submitBtn.disabled === true, submitBtn.outerHTML);

  input.value = 'Yangi vazifa';
  input.dispatchEvent(new w.Event('input', { bubbles: true }));
  await sleep(100);
  check('submit enabled after typing', root().querySelector('button[type=submit]').disabled === false, root().innerHTML);
  check('input keeps its value', root().querySelector('input').value === 'Yangi vazifa', root().querySelector('input').value);

  const form = root().querySelector('form');
  const sub = new w.Event('submit', { bubbles: true, cancelable: true });
  form.dispatchEvent(sub);
  await sleep(100);
  check('submit was prevented', sub.defaultPrevented === true, 'not prevented');
  check('todo added', root().querySelectorAll('li').length === 4, root().querySelectorAll('li').length);
  check('draft cleared', root().querySelector('input').value === '', root().querySelector('input').value);

  const firstLi = root().querySelector('li');
  const before = firstLi.className;
  const btns = [...firstLi.querySelectorAll('button, input')];
  const toggle = firstLi.querySelector('input[type=checkbox]') || btns[0];
  toggle.click();   // real activation: click, input and change like a user
  await sleep(100);
  check('toggle changed first item', root().querySelector('li').className !== before, before + ' -> ' + root().querySelector('li').className);

  const del = [...root().querySelectorAll('li')].pop().querySelector('button');
  del.click();
  await sleep(100);
  check('delete removed an item', root().querySelectorAll('li').length === 3, root().querySelectorAll('li').length);

  // error boundary: a render error replaces only the boundary's subtree
  const countBefore = root().querySelectorAll('li').length;
  d.getElementById('crash').click();
  await sleep(100);
  const errBox = root().querySelector('.error');
  check('boundary shows the fallback', errBox && errBox.textContent.includes('Crashy render xatosi'), root().innerHTML);
  check('rest of the app survives the error', root().querySelectorAll('li').length === countBefore && !d.getElementById('crash'), root().innerHTML);
  check('error was logged', logs.some(l => l.includes('Error caught by an error boundary')), JSON.stringify(logs));
  d.getElementById('reset').click();
  await sleep(100);
  check('reset renders the children again', !!d.getElementById('crash') && !root().querySelector('.error'), root().innerHTML);

  check('no dev-mode warnings', !logs.some(l => l.includes('Warning:')), JSON.stringify(logs.filter(l => l.includes('Warning:'))));

  console.log(failed ? `\n${failed} FAILED` : '\nall browser checks passed');
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('ERROR', e); process.exit(2); });
