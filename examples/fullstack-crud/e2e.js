// Clicks through the running app in jsdom (the same JavaScript a browser gets): node e2e.js http://localhost:8080
// jsdom comes from the JReact repository's examples/node_modules (npm install in examples/), or set NODE_PATH to your own.
const path = require('path');
let JSDOM;
try { ({ JSDOM } = require('jsdom')); } catch (e) { ({ JSDOM } = require(path.join(__dirname, '..', 'node_modules', 'jsdom'))); }

const base = process.argv[2] || 'http://localhost:8080';
const sleep = ms => new Promise(r => setTimeout(r, ms));
let fails = 0;
const check = (name, ok, extra) => { console.log((ok ? 'ok   ' : 'FAIL ') + name + (ok ? '' : '  -> ' + JSON.stringify(extra))); if (!ok) fails++; };

(async () => {
  const errors = [];
  const dom = await JSDOM.fromURL(base + '/', {
    runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true,
    beforeParse(w) {
      w.fetch = (u, o) => fetch(new URL(u, w.location.href), o);
      w.addEventListener('error', e => errors.push(String(e.message)));
    },
  });
  const w = dom.window, d = w.document;
  const $ = s => d.querySelector(s), $$ = s => [...d.querySelectorAll(s)];
  const titles = () => $$('#books li a').map(a => a.textContent);
  const waitFor = async (what, cond, ms = 4000) => { for (let t = 0; t < ms; t += 50) { if (cond()) return true; await sleep(50); } return false; };
  const type = (sel, value) => { const e = $(sel); e.value = value; e.dispatchEvent(new w.Event('input', { bubbles: true })); };
  const submit = form => form.dispatchEvent(new w.Event('submit', { bubbles: true, cancelable: true }));
  const click = el => el.dispatchEvent(new w.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  const path0 = () => w.location.pathname + w.location.search;

  await waitFor('list', () => titles().length > 0);
  // the server's HTML is there before the script is: wait until the page is loaded and hydrated, then click
  await waitFor('loaded', () => d.readyState === 'complete');
  await sleep(500);
  check('the list shows the first page, sorted by title', titles().length === 8 && titles()[0] === 'A Philosophy of Software Design', titles());
  check('the pager and the status tabs come from the server', $('#pager').textContent.includes('23 books, page 1 of 3') && $('#tabs').textContent.includes('Lost (3)'), $('#pager').textContent);

  // paging
  click($('#next'));
  await waitFor('page 2', () => path0() === '/?page=2' && $('#pager').textContent.includes('page 2 of 3'));
  check('Next goes to page 2 without a reload', path0() === '/?page=2' && titles().length === 8, path0());

  // filter by status
  click($$('#tabs a').find(a => a.textContent.startsWith('Lost')));
  await waitFor('lost', () => titles().length === 3);
  check('the Lost tab lists the 3 lost books', titles().length === 3 && titles().includes('The Phoenix Project'), titles());

  // search
  click($$('#tabs a').find(a => a.textContent.startsWith('All')));
  await waitFor('all', () => titles().length === 8);
  type('#q', 'fowler');
  submit($('#search-form'));
  await waitFor('search', () => titles().length === 2);
  check('search finds the books of an author (case does not matter)', titles().length === 2 && path0() === '/?q=fowler', [titles(), path0()]);

  // create: a refused form stays, shows the server's message and keeps what was typed
  click($('#new-book'));
  await waitFor('form', () => $('#book-form'));
  type('#title', 'JReact in Action'); type('#author', 'Someone'); type('#year', '12');
  submit($('#book-form'));
  await waitFor('error', () => $('#form-error'));
  check('the server refuses a bad year, the message is shown on the form', $('#form-error') && $('#form-error').textContent.startsWith('The year must be between 1000'), $('#form-error') && $('#form-error').textContent);
  check('the message is also shown under the field', $('#year-error') && $('#year-error').textContent.startsWith('The year must be between 1000'), $('#year-error') && $('#year-error').textContent);
  check('what was typed is still there', $('#title').value === 'JReact in Action', $('#title').value);

  // create: the fixed form is saved and the page goes to the new book
  type('#year', '2025'); $('#status').value = 'BORROWED'; type('#isbn', '978-1-23456-789-7');
  submit($('#book-form'));
  await waitFor('detail', () => $('#book h1') && $('#book h1').textContent === 'JReact in Action');
  check('a valid form creates the book and shows it', $('#book h1').textContent === 'JReact in Action' && $('#book').textContent.includes('Borrowed') && $('#book').textContent.includes('978-1-23456-789-7'), $('#book') && $('#book').textContent);
  const url = w.location.pathname;
  check('the URL is /books/<id>', /^\/books\/\d+$/.test(url), url);

  // edit
  click($('#edit'));
  await waitFor('edit form', () => $('#book-form') && $('#title').value === 'JReact in Action');
  check('the edit form is filled in from the loader', $('#title').value === 'JReact in Action' && $('#year').value === '2025' && $('#status').value === 'BORROWED', [$('#title').value, $('#year').value, $('#status').value]);
  type('#title', 'JReact in Action, 2nd edition'); $('#status').value = 'AVAILABLE'; type('#isbn', '');
  submit($('#book-form'));
  await waitFor('updated', () => $('#book h1') && $('#book h1').textContent.endsWith('2nd edition'));
  check('saving the edit shows the new values (and an empty ISBN is "not known")', $('#book h1').textContent === 'JReact in Action, 2nd edition' && $('#book').textContent.includes('not known') && $('#book').textContent.includes('Available'), $('#book').textContent);

  // the list counts it
  click($('header a'));
  await waitFor('home', () => $('#pager') && $('#pager').textContent.includes('24 books'));
  check('the list now has 24 books', $('#pager').textContent.includes('24 books'), $('#pager').textContent);

  // delete (two steps)
  click($$('#books li a').find(a => a.textContent.startsWith('Accelerate')) || $('#books li a'));
  await waitFor('detail 2', () => $('#delete'));
  click($('#delete'));
  await waitFor('sure', () => $('#confirm-delete'));
  click($('#cancel-delete'));
  await waitFor('back', () => $('#delete'));
  check('"No" cancels the delete', !!$('#delete') && !$('#confirm-delete'), '');
  const doomed = $('#book h1').textContent;
  click($('#delete')); await waitFor('sure 2', () => $('#confirm-delete'));
  submit($('#delete-form'));
  await waitFor('deleted', () => path0() === '/' && $('#pager') && $('#pager').textContent.includes('23 books'));
  check('deleting goes back to the list, which has 23 books and not "' + doomed + '"', path0() === '/' && $('#pager').textContent.includes('23 books') && !titles().includes(doomed), [path0(), $('#pager') && $('#pager').textContent]);

  // a book that does not exist: the error page inside the layout
  // fromURL refuses a 404 response, and the server answers 404 (with the page) for an unknown book: fetch the HTML ourselves
  const html = await (await fetch(base + '/books/999999', { headers: { accept: 'text/html' } })).text();
  const missing = new JSDOM(html, { url: base + '/books/999999', runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true,
    beforeParse(w2) { w2.fetch = (u, o) => fetch(new URL(u, w2.location.href), o); } });
  await sleep(1500);
  const md = missing.window.document;
  check('an unknown book shows the error page with the header around it', md.querySelector('#page-error') && md.body.textContent.includes('There is no book 999999') && md.body.textContent.includes('Library'), md.body.textContent.slice(0, 200));
  missing.window.close();

  check('no script errors', errors.length === 0, errors);
  w.close();
  console.log(fails ? fails + ' FAILED' : 'all passed');
  process.exit(fails ? 1 : 0);
})();
