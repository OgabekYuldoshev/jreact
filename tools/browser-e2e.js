// The app of a `jreact new` project in a real browser (Chromium via Playwright): node tools/browser-e2e.js http://localhost:8099
// Checks what jsdom cannot: real parsing of the server HTML, hydration that keeps the DOM nodes, focus and typing, CSS.
const path = require('path');
const { chromium } = require(path.join(__dirname, '..', 'examples', 'node_modules', 'playwright'));
const base = process.argv[2];
(async () => {
  let fails = 0;
  const check = (name, ok, extra) => { console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : '  -> ' + extra)); if (!ok) fails++; };
  const browser = await chromium.launch();
  const page = await browser.newPage();
  const logs = [], requests = [];
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(m.text()); });
  page.on('pageerror', e => logs.push(String(e)));
  page.on('request', r => requests.push(r.url()));

  // 1. the server's HTML is a complete page before any script runs
  const html = await (await page.request.get(base + '/', { headers: { Accept: 'text/html' } })).text();
  check('server HTML already contains the list', html.includes('Read the README'), html.slice(0, 200));

  // 2. mark a server rendered node, then let the scripts run: hydration must keep it
  await page.route('**/assets/app.*.js', async route => {
    await page.evaluate(() => { const li = document.querySelector('#todos li'); if (li) li.dataset.fromServer = '1'; });
    await new Promise(r => setTimeout(r, 100));
    route.continue();
  });
  await page.goto(base + '/', { waitUntil: 'networkidle' });
  check('hydration kept the server rendered nodes', await page.evaluate(() => !!document.querySelector('#todos li[data-from-server="1"]')), 'node was replaced');
  check('no console errors or hydration warnings', logs.length === 0, logs);
  check('no list call over the network for the first render', !requests.some(u => u.includes('/_rpc/TodoApi/list')), requests);
  check('Tailwind styles are applied', await page.evaluate(() => getComputedStyle(document.body).backgroundColor !== 'rgba(0, 0, 0, 0)'), '');

  // 3. typing and submitting in a real browser
  await page.fill('#title', 'from chromium');
  await page.press('#title', 'Enter');
  await page.waitForSelector('#todos li:has-text("from chromium")');
  check('adding through the form works and the input is cleared', (await page.inputValue('#title')) === '', await page.inputValue('#title'));
  const li = page.locator('#todos li', { hasText: 'from chromium' });
  await li.locator('form').first().evaluate(f => f.requestSubmit());
  await page.waitForSelector('#todos li:has-text("from chromium") span.line-through');
  check('toggling shows line-through', true, '');
  await li.locator('form').last().evaluate(f => f.requestSubmit());
  await page.waitForFunction(() => ![...document.querySelectorAll('#todos li')].some(l => l.textContent.includes('from chromium')));
  check('deleting removes it', true, '');

  // 4. unknown route: 404 page, real status
  const r = await page.goto(base + '/no/such/page');
  check('unknown route answers 404 and shows the error page', r.status() === 404 && (await page.content()).includes('404'), r.status());
  await browser.close();
  console.log(fails ? fails + ' FAILED' : 'all passed');
  process.exit(fails ? 1 : 0);
})();
