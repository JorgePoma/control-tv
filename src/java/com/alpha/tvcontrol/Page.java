package com.alpha.tvcontrol;

/** The remote page served at "/". Buttons send key down on press and key up on release. */
final class Page {
    private Page() { }

    static final String MANIFEST = """
{
  "name": "Control TV",
  "short_name": "Control TV",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#111214",
  "theme_color": "#111214",
  "icons": [
    { "src": "/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any maskable" },
    { "src": "/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable" }
  ]
}
""";

    static final String HTML = """
<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="theme-color" content="#111214">
<meta name="mobile-web-app-capable" content="yes">
<meta name="apple-mobile-web-app-capable" content="yes">
<title>Control TV</title>
<link rel="manifest" href="/manifest.webmanifest">
<link rel="icon" type="image/png" sizes="192x192" href="/icon-192.png">
<link rel="apple-touch-icon" href="/apple-touch-icon.png">
<style>
  :root { --bg:#111214; --btn:#24262b; --btn-on:#3a3d44; --fg:#f2f2f2; --dim:#9a9ca3; --accent:#4f8cff; }
  * { box-sizing:border-box; -webkit-tap-highlight-color:transparent; user-select:none; -webkit-user-select:none; }
  html,body { margin:0; height:100%; background:var(--bg); color:var(--fg);
    font-family:system-ui,-apple-system,Roboto,sans-serif; touch-action:manipulation; overscroll-behavior:none; }
  main { max-width:420px; margin:0 auto; padding:20px 16px 28px; display:flex; flex-direction:column; gap:22px; min-height:100%; }
  header { display:flex; justify-content:space-between; align-items:center; }
  h1 { font-size:17px; font-weight:600; margin:0; }
  #st { font-size:13px; color:var(--dim); display:flex; align-items:center; gap:7px; }
  #st::before { content:""; width:9px; height:9px; border-radius:50%; background:#3ddc84;
    box-shadow:0 0 0 3px rgba(61,220,132,.18); }
  #st.err { color:#ff6b6b; }
  #st.err::before { background:#ff5252; box-shadow:0 0 0 3px rgba(255,82,82,.2); }
  button { border:0; background:var(--btn); color:var(--fg); font:inherit; font-size:15px; border-radius:16px;
    display:flex; align-items:center; justify-content:center; gap:6px; cursor:pointer; touch-action:none; }
  button.on { background:var(--btn-on); transform:scale(.96); }
  svg { width:26px; height:26px; fill:none; stroke:currentColor; stroke-width:2.2; stroke-linecap:round; stroke-linejoin:round; }
  .row { display:grid; grid-template-columns:repeat(3,1fr); gap:12px; }
  .row button { height:58px; }
  .pad { position:relative; width:min(84vw,320px); aspect-ratio:1; margin:0 auto; border-radius:50%; background:var(--btn); }
  .pad button { position:absolute; background:transparent; border-radius:0; }
  .pad button.on { background:rgba(255,255,255,.08); transform:none; }
  .pad .up    { top:0; left:30%; width:40%; height:30%; border-radius:50% 50% 0 0 / 100% 100% 0 0; }
  .pad .down  { bottom:0; left:30%; width:40%; height:30%; border-radius:0 0 50% 50% / 0 0 100% 100%; }
  .pad .left  { left:0; top:30%; width:30%; height:40%; border-radius:50% 0 0 50% / 50% 0 0 50%; }
  .pad .right { right:0; top:30%; width:30%; height:40%; border-radius:0 50% 50% 0 / 0 50% 50% 0; }
  .pad .ok { top:31%; left:31%; width:38%; height:38%; border-radius:50%; background:var(--accent);
    font-size:20px; font-weight:700; letter-spacing:.5px; }
  .pad .ok.on { background:#3d74dc; transform:scale(.96); }
  .label { font-size:12px; color:var(--dim); text-align:center; margin-top:-12px; }
  details { color:var(--dim); font-size:14px; }
  summary { cursor:pointer; padding:6px 0; }
  .nums { display:grid; grid-template-columns:repeat(3,1fr); gap:10px; margin-top:10px; }
  .nums button { height:50px; font-size:18px; }
  button.wide { height:52px; width:100%; }
  form { display:flex; flex-direction:column; gap:10px; margin-top:-8px; }
  #kb.open, #cur.open { background:var(--accent); }
  .pad.cursor { box-shadow:0 0 0 3px var(--accent) inset; }
  form[hidden] { display:none; }
  #kbi { width:100%; height:52px; border-radius:14px; border:2px solid var(--accent); background:#1b1d21;
    color:var(--fg); font:inherit; font-size:18px; padding:0 14px; outline:none; user-select:text; -webkit-user-select:text; }
  .kbrow { display:grid; grid-template-columns:1fr 2fr; gap:10px; }
  .kbrow button { height:48px; }
  #kbok { background:var(--accent); font-weight:600; }
  .hint { font-size:13px; color:var(--dim); text-align:center; }
  .hint.ready { color:#7ee2a8; }
</style>
</head>
<body>
<main>
  <header><h1>Control TV</h1><span id="st">Conectado</span></header>


  <div class="row">
    <button data-k="158" aria-label="Atrás"><svg viewBox="0 0 24 24"><path d="M9 14 4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/></svg></button>
    <button data-k="102" aria-label="Inicio"><svg viewBox="0 0 24 24"><path d="M3 11 12 3l9 8"/><path d="M5 10v10h14V10"/></svg></button>
    <button data-k="139" aria-label="Menú"><svg viewBox="0 0 24 24"><path d="M4 6h16M4 12h16M4 18h16"/></svg></button>
  </div>
  <div class="label" style="display:grid;grid-template-columns:repeat(3,1fr)"><span>Atrás</span><span>Inicio</span><span>Menú</span></div>

  <div class="pad">
    <button class="up" data-k="103" aria-label="Arriba"><svg viewBox="0 0 24 24"><path d="m6 15 6-6 6 6"/></svg></button>
    <button class="left" data-k="105" aria-label="Izquierda"><svg viewBox="0 0 24 24"><path d="m15 6-6 6 6 6"/></svg></button>
    <button class="ok" data-k="232" aria-label="OK">OK</button>
    <button class="right" data-k="106" aria-label="Derecha"><svg viewBox="0 0 24 24"><path d="m9 6 6 6-6 6"/></svg></button>
    <button class="down" data-k="108" aria-label="Abajo"><svg viewBox="0 0 24 24"><path d="m6 9 6 6 6-6"/></svg></button>
  </div>

  <div class="row">
    <button data-k="114" aria-label="Bajar volumen"><svg viewBox="0 0 24 24"><path d="M11 5 6 9H3v6h3l5 4z"/><path d="M16 12h5"/></svg></button>
    <button data-k="164" aria-label="Reproducir o pausar"><svg viewBox="0 0 24 24"><path d="M5 5v14l7-7z"/><path d="M15 5v14M19 5v14"/></svg></button>
    <button data-k="115" aria-label="Subir volumen"><svg viewBox="0 0 24 24"><path d="M11 5 6 9H3v6h3l5 4z"/><path d="M16 12h5M18.5 9.5v5"/></svg></button>
  </div>
  <div class="row">
    <button data-k="113" aria-label="Silencio"><svg viewBox="0 0 24 24"><path d="M11 5 6 9H3v6h3l5 4z"/><path d="m16 9 5 6M21 9l-5 6"/></svg></button>
    <button id="kb" aria-label="Teclado"><svg viewBox="0 0 24 24"><rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10"/></svg></button>
    <button id="cur" aria-label="Cursor"><svg viewBox="0 0 24 24"><path d="M5 3v16l4.5-4.5 3 6.5 3-1.4-3-6.4H18z"/></svg></button>
  </div>
  <form id="kbp" hidden>
    <input id="kbi" type="text" autocomplete="off" autocorrect="on" autocapitalize="off" enterkeyhint="search" placeholder="Escribe aquí…">
    <div class="kbrow">
      <button type="button" id="kbdel">Borrar</button>
      <button type="submit" id="kbok">Enviar ⏎</button>
    </div>
    <div id="kbh" class="hint">Selecciona un cuadro de texto en la tele</div>
  </form>

  <details>
    <summary>Números</summary>
    <div class="nums">
      <button data-k="2">1</button><button data-k="3">2</button><button data-k="4">3</button>
      <button data-k="5">4</button><button data-k="6">5</button><button data-k="7">6</button>
      <button data-k="8">7</button><button data-k="9">8</button><button data-k="10">9</button>
      <span></span><button data-k="11">0</button><span></span>
    </div>
  </details>
</main>
<script>
  const st = document.getElementById('st');
  let chain = Promise.resolve();
  function setStatus(ok, text) {
    st.textContent = text || (ok ? 'Conectado' : 'Desconectado');
    st.className = ok ? '' : 'err';
  }
  function offline() { setStatus(false); }
  function send(k, v) {
    chain = chain.then(() => fetch('/k?c=' + k + '&v=' + v, { cache: 'no-store' }))
      .then(r => { if (!r.ok) throw 0; setStatus(true); })
      .catch(offline);
  }
  // Heartbeat so the dot turns red when the box or Wi-Fi drops, even without pressing anything.
  function ping() {
    if (document.hidden) return;
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 3000);
    fetch('/cur', { cache: 'no-store', signal: ctl.signal })
      .then(r => { if (!r.ok) throw 0; if (st.className === 'err') setStatus(true); })
      .catch(offline)
      .finally(() => clearTimeout(t));
  }
  setInterval(ping, 4000);
  document.addEventListener('visibilitychange', ping);
  window.addEventListener('online', ping);
  window.addEventListener('offline', offline);
  // Cursor mode: arrows glide a pointer on the TV, OK taps where it is.
  const DIRS = { 103: [0, -1], 108: [0, 1], 105: [-1, 0], 106: [1, 0] };
  const curBtn = document.getElementById('cur'), pad = document.querySelector('.pad');
  let cursorOn = false;
  function req(url) {
    chain = chain.then(() => fetch(url, { cache: 'no-store' }))
      .then(r => { if (r.status === 409) { setCursor(false); setStatus(false, 'Cursor no disponible'); } else setStatus(true); })
      .catch(offline);
  }
  function setCursor(on) {
    cursorOn = on;
    curBtn.classList.toggle('open', on);
    pad.classList.toggle('cursor', on);
  }
  curBtn.addEventListener('click', () => { setCursor(!cursorOn); req('/cur?on=' + (cursorOn ? 1 : 0)); });
  fetch('/cur', { cache: 'no-store' }).then(r => r.json()).then(j => setCursor(j.on)).catch(() => {});

  document.querySelectorAll('button[data-k]').forEach(b => {
    const k = b.dataset.k;
    let down = false, viaCursor = false;
    const press = e => {
      e.preventDefault();
      if (down) return;
      down = true; b.classList.add('on');
      if (navigator.vibrate) navigator.vibrate(12);
      viaCursor = cursorOn && (k in DIRS || k === '232');
      if (!viaCursor) send(k, 1);
      else if (k === '232') req('/cur?click=1');
      else req('/cur?dx=' + DIRS[k][0] + '&dy=' + DIRS[k][1]);
    };
    const release = () => {
      if (!down) return;
      down = false; b.classList.remove('on');
      if (!viaCursor) send(k, 0);
      else if (k !== '232') req('/cur?dx=0&dy=0');
    };
    b.addEventListener('pointerdown', press);
    b.addEventListener('pointerup', release);
    b.addEventListener('pointercancel', release);
    b.addEventListener('pointerleave', release);
    b.addEventListener('contextmenu', e => e.preventDefault());
  });

  // Keyboard: the phone box is mirrored live into the focused TV field.
  const kb = document.getElementById('kb'), kbp = document.getElementById('kbp');
  const kbi = document.getElementById('kbi'), kbh = document.getElementById('kbh');
  let poll = null, timer = null;
  function txt(q) {
    chain = chain.then(() => fetch('/txt?' + q, { cache: 'no-store' }))
      .then(r => { setStatus(true); if (r.status === 409) setHint(false); })
      .catch(offline);
  }
  function setHint(ready) {
    kbh.textContent = ready ? 'Escribiendo en la tele' : 'Selecciona un cuadro de texto en la tele';
    kbh.className = ready ? 'hint ready' : 'hint';
  }
  function checkIme() {
    fetch('/ime', { cache: 'no-store' }).then(r => r.json()).then(j => setHint(j.active)).catch(() => {});
  }
  kb.addEventListener('click', () => {
    kbp.hidden = !kbp.hidden;
    kb.classList.toggle('open', !kbp.hidden);
    if (!kbp.hidden) {
      kbi.focus(); checkIme(); poll = setInterval(checkIme, 1500);
    } else {
      clearInterval(poll); kbi.blur();
    }
  });
  kbi.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => txt('v=' + encodeURIComponent(kbi.value)), 80);
  });
  kbi.addEventListener('keydown', e => {
    if (e.key === 'Backspace' && kbi.value === '') txt('del=1');
  });
  document.getElementById('kbdel').addEventListener('click', () => {
    if (kbi.value) { kbi.value = kbi.value.slice(0, -1); txt('v=' + encodeURIComponent(kbi.value)); }
    else txt('del=1');
    kbi.focus();
  });
  kbp.addEventListener('submit', e => {
    e.preventDefault();
    clearTimeout(timer);
    txt('v=' + encodeURIComponent(kbi.value));
    txt('done=1');
    kbi.value = '';
  });
</script>
</body>
</html>
""";
}
