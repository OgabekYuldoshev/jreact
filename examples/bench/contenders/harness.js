// Same scenarios, row data and timing method as src/main/java/app/Bench.java. An adapter renders the rows:
//   adapter.set(rows, kind, info)   kind/info only help the hand-written vanilla DOM version; frameworks just re-render rows
(function () {
  var ADJ = ['pretty', 'large', 'big', 'small', 'tall', 'short', 'long', 'handsome', 'plain', 'quaint', 'clean', 'elegant', 'easy', 'angry', 'crazy', 'helpful', 'mushy', 'odd', 'unsightly', 'adorable', 'important', 'inexpensive', 'cheap', 'expensive', 'fancy'];
  var COL = ['red', 'yellow', 'blue', 'green', 'pink', 'brown', 'purple', 'brown', 'white', 'black', 'orange'];
  var NOUN = ['table', 'chair', 'house', 'bbq', 'desk', 'car', 'pony', 'cookie', 'sandwich', 'burger', 'pizza', 'mouse', 'keyboard'];
  var nextId = 1, seed = 12345;
  function random(bound) { seed = (Math.imul(seed, 1103515245) + 12345) | 0; return ((seed >>> 16) & 0x7fff) % bound; }
  function build(n) {
    var out = [];
    for (var i = 0; i < n; i++) out.push({ id: nextId++, label: ADJ[random(ADJ.length)] + ' ' + COL[random(COL.length)] + ' ' + NOUN[random(NOUN.length)] });
    return out;
  }
  var scenarios = [
    ['create1k', 0, 'replace', function () { return build(1000); }],
    ['replace1k', 1000, 'replace', function () { return build(1000); }],
    ['update10th', 1000, 'update', function (r) { return r.map(function (x, i) { return i % 10 === 0 ? { id: x.id, label: x.label + ' !!!' } : x; }); }],
    ['swap', 1000, 'swap', function (r) { var n = r.slice(); var t = n[1]; n[1] = n[998]; n[998] = t; return n; }],
    ['remove', 1000, 'remove', function (r) { var n = r.slice(); n.splice(500, 1); return n; }],
    ['append1k', 1000, 'append', function (r) { return r.concat(build(1000)); }],
    ['clear', 1000, 'replace', function () { return []; }],
    ['create10k', 0, 'replace', function () { return build(10000); }]
  ];
  var WARMUP = 3, RUNS = 10;

  window.runBench = function (adapter) {
    var rows = [], result = {};
    function set(next, kind) { var prev = rows; rows = next; adapter.set(next, kind || 'replace', prev); }
    scenarios.forEach(function (s) {
      var times = [];
      for (var i = 0; i < WARMUP + RUNS; i++) {
        set([]);
        if (s[1] > 0) set(build(s[1]));
        void document.body.offsetHeight;
        var t0 = performance.now();
        set(s[3](rows), s[2]);
        void document.body.offsetHeight;
        var dt = performance.now() - t0;
        if (i >= WARMUP) times.push(dt);
      }
      times.sort(function (a, b) { return a - b; });
      result[s[0]] = { median: Math.round(times[RUNS >> 1] * 10) / 10, min: Math.round(times[0] * 10) / 10 };
    });
    set([]);
    var json = JSON.stringify(result);
    document.getElementById('result').textContent = json;
    window.__result = json;
  };
})();
