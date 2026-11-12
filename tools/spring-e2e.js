// Opens the running app of a `jreact new` project in jsdom and clicks through it: node tools/spring-e2e.js http://localhost:8099
const path = require('path');
const { JSDOM } = require(path.join(__dirname, '..', 'examples', 'node_modules', 'jsdom'));
const base = process.argv[2];
const rpc = async (method, args) => (await (await fetch(base + '/_rpc/TodoApi/' + method, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(args) })).json());
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  let fails = 0;
  const check = (name, ok, extra) => { console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : '  -> ' + extra)); if (!ok) fails++; };
  const errors = [];
  const dom = await JSDOM.fromURL(base + '/', {
    runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true,
    beforeParse(w) {
      w.fetch = (u, o) => fetch(new URL(u, w.location.href), o);
      w.addEventListener('error', e => errors.push(String(e.message)));
    },
  });
  const w = dom.window, d = w.document;
  await sleep(1200);
  const items = () => [...d.querySelectorAll('#todos li span')].map(s => s.textContent);
  check('the loader called TodoApiClient.list() and the page rendered the list', items().length >= 3 && items().includes('Read the README'), items());
  check('the page is styled by Tailwind (a stylesheet link with a hashed name)', !!d.querySelector('link[rel=stylesheet][href^="/assets/app."]'), '');

  // add a todo through the form (an action: POST /api/todos, then the loader runs again)
  const before = items().length;
  d.querySelector('#title').value = 'from jsdom';
  d.querySelector('#add-form').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await sleep(800);
  check('the add form posted to the server and the list shows the new todo', items().length === before + 1 && items().includes('from jsdom'), items());

  // toggle it
  const li = [...d.querySelectorAll('#todos li')].find(l => l.textContent.includes('from jsdom'));
  li.querySelector('form').dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await sleep(800);
  const li2 = [...d.querySelectorAll('#todos li')].find(l => l.textContent.includes('from jsdom'));
  check('toggling is saved on the server and shown (line-through)', li2.querySelector('span').className.includes('line-through'), li2.innerHTML);
  const server = await rpc('list', []);
  check('the server really has it done', server.find(t => t.title === 'from jsdom')?.done === true, JSON.stringify(server));

  // delete it
  [...li2.querySelectorAll('form')].pop().dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  await sleep(800);
  check('deleting removes it from the page and the server', !items().includes('from jsdom') && !(await rpc('list', [])).some(t => t.title === 'from jsdom'), items());
  check('no script errors', errors.length === 0, errors);
  w.close();
  console.log(fails ? fails + ' FAILED' : 'all passed');
  process.exit(fails ? 1 : 0);
})();
