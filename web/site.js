/* Generador de contraseñas (crypto.getRandomValues; nada sale del navegador) y selector de variante de las capturas. */
(function () {
  'use strict';
  var $ = function (s) { return document.querySelector(s); };
  var SETS = { up: 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', lo: 'abcdefghijklmnopqrstuvwxyz', nu: '0123456789', sy: '!@#$%^&*()-_=+[]{};:,.<>?/|~' };
  var AMBIG = /[Il1O0o|]/g;
  var out = $('#gen-out'), len = $('#gen-len'), lenV = $('#gen-len-v'), mt = $('#meter-text'), mb = $('#meter-bits'), toast = $('#gen-toast');
  var segs = document.querySelectorAll('.meter i');
  var boxes = { up: $('#o-up'), lo: $('#o-lo'), nu: $('#o-nu'), sy: $('#o-sy'), am: $('#o-am') };

  function rnd(n) { // sin sesgo de módulo
    var lim = Math.floor(0x100000000 / n) * n, a = new Uint32Array(1);
    do { crypto.getRandomValues(a); } while (a[0] >= lim);
    return a[0] % n;
  }

  function generate() {
    var L = +len.value;
    var groups = ['up', 'lo', 'nu', 'sy'].filter(function (k) { return boxes[k].checked; })
      .map(function (k) { return boxes.am.checked ? SETS[k].replace(AMBIG, '') : SETS[k]; });
    if (!groups.length) { boxes.lo.checked = true; groups = [boxes.am.checked ? SETS.lo.replace(AMBIG, '') : SETS.lo]; }
    var pool = groups.join('');
    var chars = groups.map(function (g) { return g[rnd(g.length)]; }); // al menos un carácter de cada grupo
    while (chars.length < L) chars.push(pool[rnd(pool.length)]);
    for (var i = chars.length - 1; i > 0; i--) { var j = rnd(i + 1), t = chars[i]; chars[i] = chars[j]; chars[j] = t; }
    var bits = Math.round(L * Math.log2(pool.length));
    out.textContent = chars.slice(0, L).join('');
    lenV.textContent = L;
    // el nivel va en texto con glifo (✕ débil, ▲ media, ● fuerte) y en segmentos llenos; nunca solo en color
    var lvl = bits < 40 ? ['✕ Muy débil', 1] : bits < 60 ? ['✕ Débil', 2] : bits < 80 ? ['▲ Aceptable', 3] : bits < 110 ? ['● Fuerte', 4] : ['● Muy fuerte', 5];
    segs.forEach(function (s, i) { s.classList.toggle('on', i < lvl[1]); });
    mt.textContent = lvl[0];
    mb.textContent = '~' + bits + ' bits';
  }
  Object.keys(boxes).forEach(function (k) { boxes[k].addEventListener('change', generate); });
  len.addEventListener('input', generate);
  $('#gen-reroll').addEventListener('click', generate);
  $('#gen-copy').addEventListener('click', function () {
    var done = function (m) { toast.textContent = m; setTimeout(function () { toast.textContent = ''; }, 2600); };
    if (navigator.clipboard) navigator.clipboard.writeText(out.textContent).then(function () { done('✓ Copiada. (En la app, el portapapeles se vacía solo.)'); }, function () { done('Selecciona y copia el texto a mano.'); });
    else done('Selecciona y copia el texto a mano.');
  });
  generate();

  var btns = document.querySelectorAll('[data-tema]');
  btns.forEach(function (b) {
    b.addEventListener('click', function () {
      var t = b.dataset.tema;
      btns.forEach(function (x) { x.classList.toggle('on', x === b); });
      ['principal', 'salud', 'acceso'].forEach(function (n) { $('#cap-' + n).src = 'img/' + n + '-' + t + '.webp'; });
    });
  });
})();
