// Smoke test of the router example apps (blog, contacts): loads each built app in jsdom and uses it like a person would.
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

(async () => {
  for (const run of [blog, contacts]) await run();
  console.log(failed ? `\n${failed} of ${total} FAILED` : `\nall ${total} example checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error('ERROR', e); process.exit(2); });
