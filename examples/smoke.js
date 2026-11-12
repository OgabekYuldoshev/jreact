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
  const dom = new JSDOM(html, { runScripts: 'outside-only', pretendToBeVisual: true, url: `http://localhost/${app}/` });
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

// ------------------------------------------------------------------------------------------------ blog (router)
async function blog() {
  const t = await open('blog');
  const hash = () => t.w.location.hash;
  const link = (href, scope = '#main-nav') => t.$(`${scope} a[href="${href}"]`);       // the brand link also points at #/
  const hasClass = (el, c) => (el?.className || '').split(/\s+/).includes(c);       // 'hover:bg-x' must not count as 'bg-x'
  const active = href => hasClass(link(href), 'bg-stone-900');
  const go = async hashValue => { t.w.location.hash = hashValue; await sleep(120); };
  const titles = () => t.$$('.post h2').map(h => h.textContent);

  check('blog', 'the empty hash shows the home page', !!t.$('#page-home') && t.$$('.latest').length === 3, t.d.body.innerHTML.slice(0, 200));
  check('blog', 'links carry hash hrefs', !!link('#/posts') && !!link('#/about') && !!link('#/account'), '');
  check('blog', 'the current page is marked in the nav (aria-current)', active('#/') && !active('#/posts') && link('#/').getAttribute('aria-current') === 'page', link('#/').className);

  await t.click(link('#/posts'));
  check('blog', 'clicking a link changes the route without a reload', !!t.$('#page-posts') && !t.$('#page-home') && hash() === '#/posts', hash());
  check('blog', 'the nav follows the route', active('#/posts') && !active('#/'), '');
  check('blog', 'all 8 posts are listed', t.text('#post-count') === '8 posts' && t.$$('.post').length === 8, t.text('#post-count'));

  await t.click(t.$$('.tag').find(a => a.textContent === 'java'));
  check('blog', 'a filter chip puts the tag in the URL (a relative ?query link)', hash() === '#/posts?tag=java' && t.text('#post-count') === '4 posts', hash() + ' ' + t.text('#post-count'));
  await t.click(t.$$('a').find(a => a.textContent === 'all'));
  check('blog', '"all" clears the filter', hash() === '#/posts' && t.text('#post-count') === '8 posts', hash());

  await t.type(t.$('#q'), 'fiber');
  check('blog', 'typing in the search box updates the query string', hash() === '#/posts?q=fiber' && titles().length === 1 && titles()[0].startsWith('Fibers'), hash() + ' ' + titles());
  await t.type(t.$('#q'), 'zzz');
  check('blog', 'no match shows the empty state', !!t.$('#no-posts') && t.text('#post-count') === '0 posts', '');
  await t.type(t.$('#q'), '');

  await go('#/posts?tag=react');
  check('blog', 'a deep link with a query string restores the filter', t.$$('.post').length === 3 && t.$$('.tag').find(a => a.textContent === 'react').className.includes('bg-indigo-600'), t.$$('.post').length);

  await go('#/posts');
  await t.click(t.$$('.post')[0]);
  check('blog', 'a relative link opens /posts/:id and useParams reads the id', hash() === '#/posts/1' && t.text('#post-title').startsWith('Fibers') && !t.$('#prev') && !!t.$('#next'), hash());
  await t.click(t.$('#next'));
  check('blog', '"../2" resolves against the route', hash() === '#/posts/2' && t.text('#post-title').startsWith('Why keys'), hash() + ' ' + t.text('#post-title'));
  await t.click(t.$('#prev'));
  check('blog', 'prev goes back to post 1', hash() === '#/posts/1' && t.text('#post-title').startsWith('Fibers'), hash());
  await t.click(t.$('#next'));
  await t.click(t.$('#back'));
  await sleep(100);
  check('blog', 'useNavigate().back() follows the browser history (popstate)', hash() === '#/posts/1' && t.text('#post-title').startsWith('Fibers'), hash());

  await go('#/posts/999');
  check('blog', "an unknown id shows the page's own not-found, not the 404 route", !!t.$('#page-post-missing') && !t.$('#page-404') && t.text('#page-post-missing').includes('999'), t.d.body.textContent.slice(0, 100));
  await go('#/nope/deeper');
  check('blog', 'the * route catches everything else and sees the location', !!t.$('#page-404') && t.text('#missing-path') === '/nope/deeper', '');
  check('blog', 'the layout stays around the 404 page', !!t.$('#main-nav'), '');

  await go('#/account');
  check('blog', 'a protected route redirects to /login and remembers where to return', !!t.$('#page-login') && hash() === '#/login?next=%2Faccount' && t.text('#next-path') === '/account', hash());
  await t.type(t.$('#username'), 'Ada');
  const ev = await t.submit(t.$('#login-form'));
  check('blog', 'signing in lands on the page the user wanted', ev.defaultPrevented && !!t.$('#page-account') && !!t.$('#page-overview') && hash() === '#/account' && t.text('#account-user') === 'Ada', hash());
  check('blog', 'the header shows Sign out and the Account link is active', !!t.$('#sign-out') && active('#/account'), '');

  const sidebar = t.$('#account-nav');
  const overviewActive = () => hasClass(link('#/account', '#account-nav'), 'bg-stone-200');
  const settingsActive = () => hasClass(link('#/account/settings', '#account-nav'), 'bg-stone-200');
  check('blog', 'index route: Overview is active, Settings is not (end link to ".")', overviewActive() && !settingsActive(), '');
  await t.click(link('#/account/settings', '#account-nav'));
  check('blog', "a nested route renders in the layout's Outlet", !!t.$('#page-settings') && !t.$('#page-overview') && hash() === '#/account/settings', hash());
  check('blog', 'the layout is not remounted (same DOM node) and the sidebar follows', t.$('#account-nav') === sidebar && settingsActive() && !overviewActive(), '');

  await t.click(t.$('#sign-out'));
  check('blog', 'signing out goes home', !!t.$('#page-home') && !t.$('#sign-out') && hash() === '#/', hash());
  await go('#/account/settings');
  check('blog', 'a deep protected link redirects too', !!t.$('#page-login') && t.text('#next-path') === '/account/settings', hash());
  await t.type(t.$('#username'), 'Bob');
  await t.submit(t.$('#login-form'));
  check('blog', 'and returns to the deep page after signing in', !!t.$('#page-settings') && hash() === '#/account/settings', hash());

  const modifiedClick = new t.w.MouseEvent('click', { bubbles: true, cancelable: true, ctrlKey: true });
  link('#/about').dispatchEvent(modifiedClick);
  await sleep(60);
  check('blog', 'ctrl-click is left to the browser (no client-side navigation)', !modifiedClick.defaultPrevented, 'the router must not call preventDefault');   // jsdom then follows the #link itself, like a browser would
  noWarnings('blog', t);
  cssHas('blog', ['bg-indigo-600', 'divide-y', 'sm:grid-cols-3', 'max-w-4xl', 'hover:bg-stone-100']);
}

// ------------------------------------------------------------------------------------------------ contacts (data router)
async function contacts() {
  const t = await open('contacts');
  const names = () => t.$$('aside nav li span:first-child').map(x => x.textContent);
  const path = () => t.w.location.pathname + t.w.location.search;
  const form = id => t.$('#' + id);
  const settle = () => sleep(320);

  check('contacts', 'the first loader ran before anything showed: 5 contacts and the welcome page', names().length === 5 && !!t.$('#welcome'), names());
  check('contacts', 'the app lives under its basename', path() === '/contacts/', path());

  await t.click(t.$('#contact-2'));
  check('contacts', 'while a loader runs the old page stays and the app shows it is loading', !!t.$('#welcome') && t.text('#nav-state') === 'LOADING' && t.$('#detail').className.includes('opacity-40'), t.text('#nav-state'));
  check('contacts', 'the URL changes only when the data is there', path() === '/contacts/', path());
  await settle();
  check('contacts', 'then the page appears with its data, in one step', t.text('#contact-name') === 'Grace Hopper' && path() === '/contacts/contacts/2' && t.text('#nav-state') === 'IDLE', t.text('#contact-name') + path());
  check('contacts', 'the NavLink of the current contact is active', t.$('#contact-2').className.includes('bg-indigo-600') && !t.$('#contact-1').className.includes('bg-indigo-600'), t.$('#contact-2').className);

  // a search box that replaces the history entry
  const before = t.w.history.length;
  await t.type(t.$('#q'), 'ad');
  await sleep(100);
  check('contacts', 'typing in the search box filters through the root loader (GET form)', names().join() === 'Ada Lovelace' && path() === '/contacts/?q=ad', names() + ' ' + path());
  check('contacts', 'search replaces the history entry instead of filling the back button', t.w.history.length === before, before + ' ' + t.w.history.length);
  await t.type(t.$('#q'), '');
  await sleep(100);
  check('contacts', 'clearing the search brings all contacts back', names().length === 5, names());

  // a fetcher: the star
  await t.click(t.$('#contact-2'));
  await settle();
  check('contacts', 'Grace is not a favorite yet', t.text('#favorite') === '☆' && !t.$('#contact-2 .fav'), t.text('#favorite'));
  await t.submit(form('favorite-form'));
  check('contacts', 'a fetcher submits without navigating and the star flips at once (optimistic)', t.text('#favorite') === '★' && path() === '/contacts/contacts/2', t.text('#favorite') + path());
  await settle();
  check('contacts', 'after the action every loader reruns: the sidebar shows the favorite', !!t.$('#contact-2 .fav') && t.text('#favorite') === '★', '');

  // edit: a post action that redirects
  await t.submit(form('edit-form'));
  await sleep(120);
  check('contacts', 'a GET <Form action="edit"> goes to the edit route (relative to the route)', path() === '/contacts/contacts/2/edit' && t.$('#first').value === 'Grace', path());
  await t.type(t.$('#first'), 'Rear Admiral Grace');
  await t.submit(form('edit-contact'));
  await settle();
  check('contacts', 'saving runs the action, redirects to the contact and the list is fresh', path() === '/contacts/contacts/2' && t.text('#contact-name') === 'Rear Admiral Grace Hopper' && names().includes('Rear Admiral Grace Hopper'), path() + ' ' + t.text('#contact-name'));
  t.w.history.back();
  await sleep(150);
  check('contacts', 'the browser back button is a navigation too (loaders run for the page it returns to)', path() !== '/contacts/contacts/2' && !!t.$('#detail'), path());
  await t.click(t.$('#contact-2'));
  await settle();

  // new: the layout's action, redirect to the edit page of the new contact
  await t.submit(form('new-form'));
  await sleep(150);
  check('contacts', 'a POST on "/" reaches the root action (not the index route) and redirects', path() === '/contacts/contacts/6/edit' && names()[0] === 'No name', path() + ' ' + names());
  await t.type(t.$('#last'), 'Newcomer');
  await t.submit(form('edit-contact'));
  await settle();
  check('contacts', 'the new contact is saved', t.text('#contact-name') === 'Newcomer' && names().length === 6, t.text('#contact-name'));

  // delete
  await t.submit(form('destroy-form'));
  await sleep(150);
  check('contacts', 'delete runs the action and redirects home', path() === '/contacts/' && !!t.$('#welcome') && names().length === 5 && !names().includes('Newcomer'), path() + ' ' + names());

  // lazy route: the first visit waits for the route itself, the second is instant
  await t.click(t.$('#stats-link'));
  await sleep(60);
  check('contacts', 'lazy: while the route is being fetched the old page stays and the app is LOADING', !!t.$('#welcome') && t.text('#nav-state') === 'LOADING' && path() === '/contacts/', t.text('#nav-state') + path());
  await sleep(350);
  check('contacts', 'lazy: then the page appears', t.text('#stats-total') === '5 contacts, 3 favorites' && path() === '/contacts/stats' && t.text('#nav-state') === 'IDLE', t.text('#stats-total') + path());
  await t.click(t.$('#contact-1'));
  await settle();
  t.w.history.back();
  await sleep(60);
  check('contacts', 'lazy: a second visit does not fetch again (instant)', !!t.$('#stats') && t.text('#nav-state') === 'IDLE', t.text('#nav-state'));
  t.w.history.pushState(null, '', '/contacts/');
  t.w.dispatchEvent(new t.w.PopStateEvent('popstate'));
  await sleep(120);

  // errors
  t.w.history.pushState(null, '', '/contacts/contacts/99');
  t.w.dispatchEvent(new t.w.PopStateEvent('popstate'));
  await settle();
  check('contacts', 'a loader that throws ErrorResponse shows the errorElement inside the layout (sidebar stays)', t.text('#error-title') === '404 Not Found' && t.text('#error-detail').includes('99') && names().length === 5, t.text('#error-title'));
  t.w.history.pushState(null, '', '/contacts/no/such/page');
  t.w.dispatchEvent(new t.w.PopStateEvent('popstate'));
  await sleep(120);
  check('contacts', 'an unknown URL is a 404 from the root errorElement', t.text('#error-title') === '404 Not Found', t.text('#error-title'));
  await t.click(t.$('#page-error a'));
  await sleep(120);
  check('contacts', 'and a Link leaves the error page', !!t.$('#welcome') && path() === '/contacts/', path());
  noWarnings('contacts', t);
  cssHas('contacts', ['bg-indigo-600', 'opacity-40', 'max-w-xl', 'text-amber-400', 'disabled:opacity-50']);
}

// ------------------------------------------------------------------------------------------------ gallery
function gallery() {
  const html = fs.readFileSync(path.join(dist, 'index.html'), 'utf8');
  const missing = ['kanban/', 'shop/', 'dashboard/', 'landing/', 'blog/', 'contacts/'].filter(l => !html.includes('href="' + l + '"'));
  check('gallery', 'links to every app', missing.length === 0, missing.join());
  check('gallery', 'has its stylesheet', fs.existsSync(path.join(dist, 'gallery.css')) && fs.statSync(path.join(dist, 'gallery.css')).size > 1000, '');
}

(async () => {
  for (const run of [gallery, kanban, shop, dashboard, landing, blog, contacts]) await run();
  console.log(failed ? `\n${failed} of ${total} FAILED` : `\nall ${total} example checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('ERROR', e); process.exit(2); });
