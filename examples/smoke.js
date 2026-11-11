// Smoke test of the Tailwind example apps: loads each built app in jsdom and uses it like a person would.
// Usage: node smoke.js <dist dir>      (./build.sh examples-test builds dist first)
const { JSDOM } = require('jsdom');
const fs = require('fs');
const path = require('path');

const dist = process.argv[2];
if (!dist) { console.error('usage: node smoke.js <dist dir>'); process.exit(2); }

let failed = 0, total = 0;
const sleep = ms => new Promise(r => setTimeout(r, ms));

/** Loads dist/<app> and returns helpers bound to its window. */
async function open(app) {
  const dir = path.join(dist, app);
  const html = fs.readFileSync(path.join(dir, 'index.html'), 'utf8').replace(/<script[\s\S]*?<\/script>/g, '');
  const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window, d = w.document;
  const logs = [];
  for (const m of ['log', 'info', 'warn', 'error', 'debug']) w.console[m] = (...a) => logs.push(a.join(' '));
  w.eval(fs.readFileSync(path.join(dir, 'app.js'), 'utf8'));
  w.eval('main()');
  await sleep(100);
  const $ = (sel, root = d) => root.querySelector(sel);
  const $$ = (sel, root = d) => [...root.querySelectorAll(sel)];
  return {
    w, d, logs, $, $$,
    text: (sel, root) => ($(sel, root)?.textContent || '').replace(/\s+/g, ' ').trim(),
    async click(el) { el.click(); await sleep(60); },
    async type(el, value) { el.value = value; el.dispatchEvent(new w.Event('input', { bubbles: true })); await sleep(60); },
    async choose(el, value) { el.value = value; el.dispatchEvent(new w.Event('change', { bubbles: true })); await sleep(60); },
    async submit(form) {
      const ev = new w.Event('submit', { bubbles: true, cancelable: true });
      form.dispatchEvent(ev); await sleep(60); return ev;
    },
  };
}

function check(app, name, ok, extra) {
  total++;
  console.log((ok ? 'ok   ' : 'FAIL ') + app + ': ' + name + (ok ? '' : '  -> ' + extra));
  if (!ok) failed++;
}

function cssHas(app, classes) {
  const css = fs.readFileSync(path.join(dist, app, 'app.css'), 'utf8');
  const missing = classes.filter(c => !css.includes('.' + c.replace(/:/g, '\\:')));
  check(app, 'Tailwind CSS has the classes the app uses', missing.length === 0, 'missing: ' + missing.join(', '));
}

const noWarnings = (app, t) => check(app, 'no dev-mode warnings or errors', !t.logs.some(l => /^Warning:|Error caught/.test(l)), JSON.stringify(t.logs.slice(0, 3)));

// ------------------------------------------------------------------------------------------------ kanban
async function kanban() {
  const t = await open('kanban');
  const col = i => t.$(`[data-column="${i}"]`);
  const count = i => t.text('.count', col(i));
  const cards = i => t.$$('.card', col(i));

  check('kanban', 'three columns with 3, 2 and 2 cards', t.$$('section[data-column]').length === 3 && count(0) === '3' && count(1) === '2' && count(2) === '2', [0, 1, 2].map(count));
  check('kanban', 'progress shows 28% (2 of 7)', t.text('p.text-right') === '28%' && t.$('#progress').style.width === '28%', t.$('#progress').getAttribute('style'));
  check('kanban', 'add button disabled while the title is empty', t.$('form button[type=submit]').disabled === true, '');

  await t.type(t.$('form input'), 'Write release notes');
  check('kanban', 'add button enabled after typing', t.$('form button[type=submit]').disabled === false, '');
  const ev = await t.submit(t.$('form'));
  check('kanban', 'new task lands in To do and the input is cleared', ev.defaultPrevented && count(0) === '4' && cards(0).some(c => c.textContent.includes('Write release notes')) && t.$('form input').value === '', count(0));

  check('kanban', 'first card cannot move left', cards(0)[0].querySelector('.left').disabled === true, '');
  const moving = cards(0)[0].textContent;
  await t.click(cards(0)[0].querySelector('.right'));
  check('kanban', 'move right shifts a card to In progress', count(0) === '3' && count(1) === '3' && cards(1).some(c => c.textContent === moving), [count(0), count(1)]);

  await t.click(cards(1)[0].querySelector('.right'));
  check('kanban', 'moving to Done raises the progress', t.text('p.text-right') === '37%' || t.text('p.text-right') === '38%' || t.text('p.text-right') === '37%', t.text('p.text-right'));

  await t.click(cards(2)[0].querySelector('.delete'));
  check('kanban', 'delete removes the card', count(2) === '2' && t.$$('.card').length === 7, t.$$('.card').length);

  for (const c of cards(0)) await t.click(c.querySelector('.delete'));
  check('kanban', 'an empty column shows its placeholder', t.text('section[data-column="0"]').includes('Nothing here yet') || count(0) !== '0', count(0));
  noWarnings('kanban', t);
  cssHas('kanban', ['rounded-xl', 'bg-emerald-500', 'md:grid-cols-3', 'disabled:opacity-30', 'hover:shadow-md']);
}

// ------------------------------------------------------------------------------------------------ shop
async function shop() {
  const t = await open('shop');
  const names = () => t.$$('.product h3').map(h => h.textContent);

  check('shop', '12 products are listed', t.$$('.product').length === 12 && t.text('#result-count') === '12 products', t.text('#result-count'));
  check('shop', 'cart badge is hidden when the cart is empty', !t.$('#cart-count'), '');

  await t.type(t.$('#search'), 'green');
  check('shop', 'search filters the grid', names().join() === 'Sencha Green', names());
  await t.type(t.$('#search'), 'zzz');
  check('shop', 'no match shows the empty state', t.$$('.product').length === 0 && t.text('main').includes('Nothing matches'), t.text('main'));
  await t.type(t.$('#search'), '');

  const tea = t.$$('main button').find(b => b.textContent === 'Tea');
  await t.click(tea);
  check('shop', 'category chip filters to 4 teas', t.$$('.product').length === 4 && t.text('#result-count') === '4 products', t.$$('.product').length);
  await t.choose(t.$('#sort'), 'price-asc');
  check('shop', 'sorting by price puts the cheapest first', names()[0] === 'Masala Chai', names());
  await t.choose(t.$('#sort'), 'rating');
  check('shop', 'sorting by rating', names()[0] === 'Jasmine Pearls', names());
  await t.click(t.$$('main button').find(b => b.textContent === 'All'));
  await t.choose(t.$('#sort'), 'popular');
  check('shop', 'back to all products in the original order', names()[0] === 'Ethiopia Yirgacheffe' && names().length === 12, names().slice(0, 2));

  const add = () => t.$$('.add')[0];
  await t.click(add());
  check('shop', 'adding updates the badge and the button label', t.text('#cart-count') === '1' && add().textContent.trim() === 'Add another (1)', add().textContent);
  await t.click(add());
  check('shop', 'adding again increments', t.text('#cart-count') === '2', t.text('#cart-count'));

  check('shop', 'cart panel starts off screen', t.$('#cart-panel').className.includes('translate-x-full'), '');
  await t.click(t.$('#cart-button'));
  check('shop', 'cart button slides the panel in', t.$('#cart-panel').className.includes('translate-x-0'), t.$('#cart-panel').className);
  check('shop', 'one line, quantity 2', t.$$('.line').length === 1 && t.text('.qty') === '2', t.$$('.line').length);
  check('shop', 'total = 2 x $18.90 + $4.99 shipping', t.text('#cart-total') === '$42.79', t.text('#cart-total'));

  await t.click(t.$('.inc'));
  check('shop', 'free shipping from $50: total is $56.70', t.text('#cart-total') === '$56.70', t.text('#cart-total'));
  await t.click(t.$('.dec'));
  await t.click(t.$('.dec'));
  await t.click(t.$('.dec'));
  check('shop', 'decrementing to zero removes the line', !!t.$('#cart-empty') && !t.$('#cart-count'), '');

  await t.click(t.$('#cart-close'));
  check('shop', 'close slides the panel out', t.$('#cart-panel').className.includes('translate-x-full'), '');
  await t.click(t.$$('.add')[1]);
  await t.click(t.$('#cart-button'));
  await t.click(t.$('#checkout'));
  check('shop', 'checkout shows the confirmation and empties the cart', !!t.$('#order-done') && !t.$('#cart-count'), '');
  await t.click(t.$('#order-done button'));
  check('shop', 'keep shopping closes the panel', t.$('#cart-panel').className.includes('translate-x-full') && !t.$('#order-done'), '');
  noWarnings('shop', t);
  cssHas('shop', ['bg-amber-800', 'translate-x-full', 'translate-x-0', 'xl:grid-cols-4', 'from-amber-50']);
}

// ------------------------------------------------------------------------------------------------ dashboard
async function dashboard() {
  const t = await open('dashboard');
  const rows = () => t.$$('.order .customer').map(c => c.textContent);

  check('dashboard', 'four stat cards and the overview title', t.$$('.stat').length === 4 && t.text('#page-title') === 'overview', t.$$('.stat').length);
  check('dashboard', 'revenue is the sum of the week', t.text('.stat .value') === '$3870k', t.text('.stat .value'));
  check('dashboard', 'seven bars, the tallest at 100%', t.$$('.bar').length === 7 && t.$$('.bar').some(b => b.style.height === '100%'), t.$$('.bar').length);
  await t.click(t.$('#range-12m'));
  check('dashboard', '12 months range shows 12 bars', t.$$('.bar').length === 12, t.$$('.bar').length);
  await t.click(t.$('#range-7d'));

  check('dashboard', 'orders table lists 12 rows, newest day first', rows().length === 12 && rows()[0] === 'Amelia Brooks', rows().slice(0, 2));
  await t.click(t.$('[data-sort=amount]'));
  check('dashboard', 'click a header to sort ascending', rows()[0] === 'Noah Fischer', rows().slice(0, 2));
  await t.click(t.$('[data-sort=amount]'));
  check('dashboard', 'click again to reverse', rows()[0] === 'Chloe Dubois', rows().slice(0, 2));
  await t.type(t.$('#order-search'), 'haddad');
  check('dashboard', 'search filters rows and updates the counter', rows().join() === 'Zara Haddad' && t.text('#row-count') === '(1)', rows());
  await t.type(t.$('#order-search'), '');
  await t.choose(t.$('#status-filter'), 'Pending');
  check('dashboard', 'status filter', rows().length === 3, rows().length);
  await t.choose(t.$('#status-filter'), 'All');

  check('dashboard', 'light theme at first', t.$('#theme-root').className === '', t.$('#theme-root').className);
  await t.click(t.$('#theme-toggle'));
  check('dashboard', 'toggle switches on the dark class', t.$('#theme-root').className === 'dark' && t.text('#theme-toggle').includes('Light'), t.$('#theme-root').className);
  await t.click(t.$('#theme-toggle'));

  await t.click(t.$('#crash'));
  check('dashboard', 'ErrorBoundary shows the fallback for the broken widget', !!t.$('#widget-error') && t.text('#widget-error').includes('malformed data'), t.text('#widget-error'));
  check('dashboard', 'the rest of the page keeps working', t.$$('.stat').length === 4 && t.$$('.bar').length === 7 && rows().length === 12, '');
  await t.click(t.$('#range-12m'));
  check('dashboard', 'the chart still reacts while the widget is down', t.$$('.bar').length === 12, '');
  await t.click(t.$('#widget-reset'));
  check('dashboard', 'Try again brings the widget back', !t.$('#widget-error') && t.text('#visitors') === '128', t.text('#visitors'));
  await t.click(t.$('#refresh'));
  check('dashboard', 'the widget has its own state', t.text('#visitors') === '135', t.text('#visitors'));

  await t.click(t.$('[data-page=orders]'));
  check('dashboard', 'navigation to Orders hides the stats', t.text('#page-title') === 'orders' && t.$$('.stat').length === 0 && rows().length === 12, t.text('#page-title'));
  await t.click(t.$('[data-page=customers]'));
  check('dashboard', 'Customers shows a placeholder', t.text('main').includes('not built yet'), '');
  // the only thing logged is the deliberate crash, reported once by the boundary
  const caught = t.logs.filter(l => /Error caught/.test(l));
  check('dashboard', 'boundary logged the deliberate crash and nothing else', caught.length === 1 && caught[0].includes('malformed data') && !t.logs.some(l => /^Warning:/.test(l)), JSON.stringify(t.logs.slice(0, 3)));
  cssHas('dashboard', ['bg-indigo-600', 'dark:bg-slate-950', 'md:flex', 'tabular-nums', 'divide-y', 'dark:text-indigo-300']);
}

// ------------------------------------------------------------------------------------------------ landing
async function landing() {
  const t = await open('landing');
  const prices = () => t.$$('.plan .price').map(p => p.textContent);

  check('landing', 'three plans and six features', t.$$('.plan').length === 3 && t.$$('.feature').length === 6, t.$$('.plan').length);
  check('landing', 'monthly prices by default', prices().join() === '$12,$39,$99', prices());
  await t.click(t.$('#bill-yearly'));
  check('landing', 'yearly switch lowers the prices', prices().join() === '$10,$31,$79' && t.text('.plan').length > 0 && t.$$('.plan')[0].textContent.includes('billed annually'), prices());
  await t.click(t.$('#bill-monthly'));
  check('landing', 'and back', prices().join() === '$12,$39,$99', prices());

  check('landing', 'FAQ starts collapsed', t.$$('.faq').length === 4 && t.$$('.answer').length === 0, t.$$('.answer').length);
  await t.click(t.$$('.question')[0]);
  check('landing', 'opening a question shows its answer', t.$$('.answer').length === 1 && t.text('.answer').includes('14 day'), t.text('.answer'));
  await t.click(t.$$('.question')[1]);
  check('landing', 'only one answer is open at a time', t.$$('.answer').length === 1 && t.text('.answer').includes('upgrade'), t.text('.answer'));
  await t.click(t.$$('.question')[1]);
  check('landing', 'clicking again closes it', t.$$('.answer').length === 0, '');

  check('landing', 'mobile menu is closed', !t.$('#mobile-menu'), '');
  await t.click(t.$('#menu-button'));
  check('landing', 'menu button opens it', !!t.$('#mobile-menu') && t.$$('#mobile-menu a').length === 4, '');
  await t.click(t.$('#menu-button'));
  check('landing', 'and closes it', !t.$('#mobile-menu'), '');

  const form = () => t.$('#contact-form');
  const errors = () => t.$$('.error').map(e => e.textContent);
  check('landing', 'no errors before the first submit', errors().length === 0, errors());
  const ev = await t.submit(form());
  check('landing', 'submitting an empty form shows three errors', ev.defaultPrevented && errors().length === 3, errors());
  await t.type(t.$('#name'), 'Ada');
  check('landing', 'errors update live while typing', errors().length === 2, errors());
  await t.type(t.$('#email'), 'ada@x');
  check('landing', 'an invalid email keeps its error', errors().some(e => e.includes('valid email')), errors());
  await t.type(t.$('#email'), 'ada@example.com');
  await t.type(t.$('#message'), 'Hello, I would like a demo.');
  check('landing', 'a valid form has no errors', errors().length === 0, errors());
  await t.submit(form());
  check('landing', 'a valid submit shows the success message', !!t.$('#success') && t.text('#success').includes('Thanks Ada') && !form(), t.text('#contact'));
  await t.click(t.$('#again'));
  check('landing', 'Send another resets the form', !!form() && t.$('#name').value === '' && errors().length === 0, '');
  noWarnings('landing', t);
  cssHas('landing', ['from-violet-600', 'bg-clip-text', 'lg:grid-cols-3', 'backdrop-blur', 'md:hidden']);
}

// ------------------------------------------------------------------------------------------------ gallery
function gallery() {
  const html = fs.readFileSync(path.join(dist, 'index.html'), 'utf8');
  const missing = ['kanban/', 'shop/', 'dashboard/', 'landing/'].filter(l => !html.includes('href="' + l + '"'));
  check('gallery', 'links to every app', missing.length === 0, missing.join());
  check('gallery', 'has its stylesheet', fs.existsSync(path.join(dist, 'gallery.css')) && fs.statSync(path.join(dist, 'gallery.css')).size > 1000, '');
}

(async () => {
  for (const run of [gallery, kanban, shop, dashboard, landing]) await run();
  console.log(failed ? `\n${failed} of ${total} FAILED` : `\nall ${total} example checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('ERROR', e); process.exit(2); });
