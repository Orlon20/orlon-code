package com.mobilecode.app

/**
 * Cosmetic only: swaps the standalone OpenCode wordmark and page title for Orlon Code.
 * Provider/model names ("OpenCode Go", "provided by OpenCode") are real services and are left alone.
 * Never throws.
 */
object Branding {
    const val SWAP_JS = """
(function(){
  try {
    if (window.__mcBrand) return; window.__mcBrand = true;
    var exact = /^\s*open\s*code\s*${'$'}/i;
    function inDialog(n){ return n.closest && n.closest('[role="dialog"],[data-component="dialog"]'); }
    function swap(root){
      try {
        var t = document.title; if (/open\s*code/i.test(t)) document.title = t.replace(/open\s*code/gi, 'Orlon Code');
        var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var n; while ((n = w.nextNode())) {
          var el = n.parentElement; if (!el || el.nodeName === 'SCRIPT' || el.nodeName === 'STYLE' || el.nodeName === 'TEXTAREA') continue;
          if (exact.test(n.nodeValue) && !inDialog(el)) n.nodeValue = n.nodeValue.replace(/open\s*code/i, 'Orlon Code');
        }
        root.querySelectorAll && root.querySelectorAll('[aria-label],[alt]').forEach(function(e){
          ['aria-label','alt'].forEach(function(a){ var v = e.getAttribute(a); if (v && exact.test(v)) e.setAttribute(a, 'Orlon Code'); });
        });
      } catch(e) {}
    }
    swap(document.body || document.documentElement);
    var pending = false;
    new MutationObserver(function(){ if (pending) return; pending = true; setTimeout(function(){ pending = false; swap(document.body); }, 400); })
      .observe(document.documentElement, {childList:true, subtree:true, characterData:true});
  } catch(e) {}
})();
"""

    /**
     * On-device mode only: on the new-session screen hide the "connect providers" banner and the
     * project/branch row so the prompt box (with its model picker) is all that is left. Never throws.
     */
    const val CLEAN_JS = """
(function(){
  try {
    if (window.__mcClean) return; window.__mcClean = true;
    var st = document.createElement('style'); st.id = 'mc-clean';
    st.textContent = '[class*="provider-tip"], main div[class*="min-h-7"][class*="min-w-0"][class*="flex-col"], main div[class*="mt-8"][class*="flex-col"][class*="gap-8"] > :nth-child(n+2) { display: none !important; }';
    function sync(){
      var home = location.pathname.indexOf('/new-session') === 0;
      if (home) { if (!st.parentNode) document.head.appendChild(st); } else if (st.parentNode) st.remove();
    }
    sync();
    new MutationObserver(sync).observe(document.documentElement, {childList:true, subtree:true});
    var push = history.pushState; history.pushState = function(){ var r = push.apply(this, arguments); sync(); return r; };
    window.addEventListener('popstate', sync);
  } catch(e) {}
})();
"""

    /**
     * Run once per launch on a same-origin page before the home screen loads: drops leftover empty-draft tabs and, in
     * on-device mode, registers the default project in OpenCode's own state so the Projects tab lists it with its sessions.
     * Never throws.
     */
    fun prepStateJs(project: String?): String = """
(function(){
  try {
    var k = 'opencode.window.browser.dat:tabs';
    var tabs = JSON.parse(localStorage.getItem(k) || '[]');
    localStorage.setItem(k, JSON.stringify(tabs.filter(function(t){ return t && t.type !== 'draft'; })));
    localStorage.removeItem(k + '.recent');
  } catch(e) {}
  try {
    var DIR = ${if (project == null) "null" else "'" + project + "'"};
    if (DIR) {
      var sk = 'opencode.global.dat:server', st = {};
      try { st = JSON.parse(localStorage.getItem(sk) || '{}') || {}; } catch(e) { st = {}; }
      st.list = st.list || []; st.projects = st.projects || {}; st.recentlyClosed = st.recentlyClosed || {};
      var arr = st.projects.local || [];
      if (!arr.some(function(p){ return p && p.worktree === DIR; })) arr.push({worktree: DIR, expanded: true});
      st.projects.local = arr; st.lastProject = st.lastProject || {}; st.lastProject.local = DIR;
      localStorage.setItem(sk, JSON.stringify(st));
    }
  } catch(e) {}
  return 'ok';
})()
"""

    /** Default theme only: pushes OpenCode's near-black backgrounds to pure black (AMOLED). Other themes are left alone. */
    const val AMOLED_JS = """
(function(){
  try {
    var id = localStorage.getItem('opencode-theme-id');
    if (id && id !== 'oc-2') return;
    var root = document.documentElement, names = {};
    function walk(rules){
      for (var j = 0; j < rules.length; j++) {
        var r = rules[j];
        if (r.cssRules) walk(r.cssRules);
        var st = r.style; if (!st) continue;
        for (var k = 0; k < st.length; k++) if (st[k].indexOf('--v2-background-') === 0) names[st[k]] = 1;
      }
    }
    for (var i = 0; i < document.styleSheets.length; i++) { try { walk(document.styleSheets[i].cssRules); } catch(e) {} }
    var probe = document.createElement('span'); document.body.appendChild(probe);
    Object.keys(names).forEach(function(n){
      probe.style.color = 'var(' + n + ')';
      var m = /^rgba?\(\s*(\d+)[,\s]+(\d+)[,\s]+(\d+)\s*(?:[,\/]\s*([\d.]+))?\s*\)$/.exec(getComputedStyle(probe).color);
      if (!m || (m[4] !== undefined && +m[4] < 1)) return;
      var mx = Math.max(+m[1], +m[2], +m[3]), v = mx <= 18 ? 0 : mx <= 30 ? 13 : mx <= 45 ? 26 : -1;
      if (v >= 0) root.style.setProperty(n, 'rgb(' + v + ',' + v + ',' + v + ')');
    });
    probe.remove();
    var cssEl = document.createElement('style');
    cssEl.textContent = '[data-slot="dialog-content"],[data-component="dialog"] [data-slot="dialog-content"],[data-slot="dialog-body"]{background:#000!important}' +
      '[data-slot="dialog-content"]{border:1px solid rgba(255,255,255,.14)!important}';
    document.head.appendChild(cssEl);
    function hideHeading(){
      document.querySelectorAll('[data-slot="dialog-body"] *').forEach(function(e){
        if (e.children.length === 0 && /^Free models provided by OpenCode\.?$/.test(e.textContent.trim())) {
          var h = e, body = e.closest('[data-slot="dialog-body"]');
          while (h.parentElement && h.parentElement !== body && h.parentElement.children.length === 1) h = h.parentElement;
          h.style.display = 'none'; if (h.parentElement) h.parentElement.style.setProperty('gap', '0', 'important');
        }
      });
    }
    hideHeading();
    new MutationObserver(hideHeading).observe(document.documentElement, {childList:true, subtree:true});
  } catch(e) {}
})();
"""

    /** Replaces the model icons with the real logo of the lab that made the model (fetched from models.dev, cached). Never throws. */
    const val MODEL_ICONS_JS = """
(function(){
  try {
    if (window.__mcLogos) return; window.__mcLogos = true;
    var RULES = [[/deepseek/, 'deepseek'], [/glm|z\.ai|zhipu/, 'zai'], [/nemotron|nvidia/, 'nvidia'], [/mimo|xiaomi/, 'xiaomi'],
      [/kimi|moonshot/, 'moonshotai'], [/qwen|alibaba/, 'alibaba'], [/minimax/, 'minimax'], [/\bgpt|codex|\bo[134]\b|openai/, 'openai'],
      [/claude|anthropic/, 'anthropic'], [/gemini|gemma|google/, 'google'], [/llama|meta\b/, 'meta'], [/mistral|codestral|devstral/, 'mistral'],
      [/grok|xai/, 'xai']];
    var cache = {}, pending = {};
    function lab(text){ text = text.toLowerCase(); for (var i = 0; i < RULES.length; i++) if (RULES[i][0].test(text)) return RULES[i][1]; return null; }
    function get(id, cb){
      if (cache[id]) return cb(cache[id]);
      try { var s = localStorage.getItem('mc-logo-' + id); if (s) { cache[id] = JSON.parse(s); return cb(cache[id]); } } catch(e) {}
      if (pending[id]) { pending[id].push(cb); return; }
      pending[id] = [cb];
      fetch('https://models.dev/logos/' + id + '.svg').then(function(r){ return r.ok ? r.text() : Promise.reject(); }).then(function(t){
        var d = new DOMParser().parseFromString(t, 'image/svg+xml').documentElement;
        if (!d || d.nodeName !== 'svg') return;
        d.querySelectorAll('script,foreignObject').forEach(function(x){ x.remove(); });
        var v = { vb: d.getAttribute('viewBox') || '0 0 24 24', html: d.innerHTML };
        cache[id] = v; try { localStorage.setItem('mc-logo-' + id, JSON.stringify(v)); } catch(e) {}
        (pending[id] || []).forEach(function(f){ f(v); }); delete pending[id];
      }).catch(function(){ delete pending[id]; });
    }
    function run(){
      document.querySelectorAll('svg[data-component="provider-icon"]').forEach(function(s){
        var row = s.closest('button, [role="option"], [role="menuitem"], li, [data-slot*="item"]') || s.parentElement;
        var id = row ? lab(row.textContent || '') : null;
        if (!id || s.getAttribute('data-mc-lab') === id) return;
        s.setAttribute('data-mc-lab', id);
        get(id, function(v){
          if (s.getAttribute('data-mc-lab') !== id) return;
          s.setAttribute('viewBox', v.vb); s.setAttribute('fill', 'currentColor'); s.innerHTML = v.html;
        });
      });
    }
    var css = document.createElement('style');
    css.textContent = 'svg[data-component="provider-icon"]{opacity:1!important}';
    document.head.appendChild(css);
    function rows(){
      document.querySelectorAll('[data-slot="dialog-body"] button').forEach(function(btn){
        var name = btn.querySelector('span.truncate'); if (!name || btn.getAttribute('data-mc-row') === name.textContent) return;
        var id = lab(name.textContent || '');
        btn.setAttribute('data-mc-row', name.textContent);
        var old = btn.querySelector('[data-mc-ico]'); if (old) old.remove();
        var box = document.createElement('span'); box.setAttribute('data-mc-ico', '1');
        box.style.cssText = 'display:inline-flex;width:16px;height:16px;flex:none;align-items:center;justify-content:center';
        btn.insertBefore(box, btn.firstChild);
        if (!id) return;
        get(id, function(v){ box.innerHTML = '<svg width="16" height="16" viewBox="' + v.vb + '" fill="currentColor">' + v.html + '</svg>'; });
      });
    }
    run(); rows();
    var pend = false;
    new MutationObserver(function(){ if (pend) return; pend = true; requestAnimationFrame(function(){ pend = false; run(); rows(); }); })
      .observe(document.documentElement, {childList:true, subtree:true});
  } catch(e) {}
})();
"""

    /** Phone-width fixes for OpenCode's UI (all modes): keep the prompt toolbar and long lines inside the screen. */
    const val FIT_JS = """
(function(){
  try {
    if (window.__mcFit) return; window.__mcFit = true;
    var st = document.createElement('style'); st.id = 'mc-fit';
    var bar = 'form[class*="prompt-input"] div[class*="min-w-0"][class*="flex-1"][class*="items-center"]';
    st.textContent =
      bar + '{flex-wrap:wrap!important;overflow:visible!important;row-gap:4px}' + bar + ' > *{min-width:0;flex-shrink:0;max-width:100%}' + bar + ' button{min-width:0;max-width:100%}' +
      'html,body{overflow-x:hidden;max-width:100vw}' +
      '[data-slot=basic-tool-tool-subtitle]{display:-webkit-box!important;-webkit-box-orient:vertical!important;-webkit-line-clamp:3!important;overflow:hidden!important;text-overflow:ellipsis!important;white-space:normal!important;overflow-wrap:anywhere!important;max-width:100%!important;height:auto!important}' +
      '[data-slot=shell-submessage-width],[data-component=shell-submessage],[data-slot=shell-submessage-value]{white-space:normal!important;overflow:hidden!important;text-overflow:ellipsis!important;overflow-wrap:anywhere!important;max-width:100%!important;height:auto!important}' +
      '[data-slot=shell-submessage-value]{display:inline!important}' +
      '[data-slot=basic-tool-tool-info-main],[data-slot=basic-tool-tool-info-structured]{overflow:visible!important;height:auto!important;min-width:0}' +
      '[data-slot=basic-tool-tool-subtitle]{text-align:left!important}' +
      '[data-slot=basic-tool-tool-trigger-content],[data-component=tool-trigger],[data-slot=collapsible-trigger],[data-component=collapsible-trigger],[data-slot=basic-tool-tool-info],[data-component=basic-tool-tool-info]{height:auto!important;min-height:32px;max-height:none!important}' +
      'pre.shiki{white-space:pre-wrap!important;overflow-wrap:anywhere!important;overflow-x:hidden!important}';
    document.head.appendChild(st);
    function grow(){
      document.querySelectorAll(bar).forEach(function(x){
        var f = x.closest('form'), n = x.parentElement;
        while (n && n !== f) { n.style.setProperty('height', 'auto', 'important'); n.style.setProperty('max-height', 'none', 'important'); n.style.setProperty('overflow', 'visible', 'important'); n = n.parentElement; }
        if (f) { f.style.setProperty('height', 'auto', 'important'); f.style.setProperty('max-height', 'none', 'important'); }
      });
    }
    function iconOnly(){
      document.querySelectorAll(bar + ' button').forEach(function(btn){
        if (btn.querySelectorAll('svg, img').length < 2) return;
        btn.querySelectorAll('*').forEach(function(e){
          if (e.children.length === 0 && e.tagName !== 'svg' && e.tagName !== 'path' && e.tagName !== 'IMG' && e.textContent.trim()) e.style.setProperty('display', 'none', 'important');
        });
      });
    }
    function both(){ grow(); iconOnly(); }
    both();
    new MutationObserver(both).observe(document.documentElement, {childList:true, subtree:true});
  } catch(e) {}
})();
"""

    /** Hides the "Add more models from popular providers" block in the model picker. Never throws. */
    const val NO_PROVIDER_BLOCK_JS = """
(function(){
  try {
    if (window.__mcNoProv) return; window.__mcNoProv = true;
    function run(){
      try {
        document.querySelectorAll('[data-slot="dialog-body"] div, [data-component="dialog-body"] div').forEach(function(e){
          if (e.children.length === 0 && e.textContent.trim() === 'Add more models from popular providers') {
            var box = e.parentElement && e.parentElement.parentElement;
            if (box && String(box.className).indexOf('rounded-lg') >= 0 && box.style.display !== 'none') box.style.display = 'none';
          }
        });
      } catch(e) {}
    }
    run();
    new MutationObserver(run).observe(document.documentElement, {childList:true, subtree:true});
  } catch(e) {}
})();
"""

    /** Typing "/themes" in the prompt and sending opens Orlon Code's theme picker instead of sending it to the agent. */
    const val SLASH_THEMES_JS = """
(function(){
  try {
    if (window.__mcSlash) return; window.__mcSlash = true;
    var FORM = 'form[class*="prompt-input"]';
    function editor(){ return document.querySelector(FORM + ' [contenteditable="true"], ' + FORM + ' textarea'); }
    function isCmd(){
      var ed = editor(); if (!ed) return false;
      var t = (ed.value !== undefined ? ed.value : ed.innerText || '').trim().toLowerCase();
      return t === '/themes' || t === '/theme';
    }
    function clear(){
      var ed = editor(); if (!ed) return; ed.focus();
      if (ed.value !== undefined) {
        var set = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(ed), 'value').set; set.call(ed, ''); ed.dispatchEvent(new Event('input', {bubbles:true}));
      } else { document.execCommand('selectAll'); document.execCommand('delete'); }
    }
    function fire(e){ e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); clear(); if (window.MobilecodeBridge) MobilecodeBridge.openThemes(); }
    document.addEventListener('keydown', function(e){
      if ((e.key === 'Enter' || e.keyCode === 13) && !e.shiftKey && e.target.closest && e.target.closest(FORM) && isCmd()) fire(e);
    }, true);
    document.addEventListener('beforeinput', function(e){
      if (e.inputType === 'insertParagraph' && e.target.closest && e.target.closest(FORM) && isCmd()) fire(e);
    }, true);
    document.addEventListener('click', function(e){
      var b = e.target.closest && e.target.closest(FORM + ' button[type="submit"]');
      if (b && isCmd()) fire(e);
    }, true);
    document.addEventListener('submit', function(e){ if (isCmd()) fire(e); }, true);
  } catch(e) {}
})();
"""

    /** Scrapes OpenCode's theme catalog (ids, names, light/dark palettes) and reports it to MobilecodeBridge.themesData. */
    const val THEMES_SCAN_JS = """
(async function(){
  try {
    var src = Array.prototype.map.call(document.scripts, function(s){ return s.src; }).filter(function(s){ return /\/assets\/index-[^/]*\.js/.test(s); })[0];
    var out = [{id:'oc-2', name:'OC-2', light:{neutral:'#f7f7f7', ink:'#171311', primary:'#dcde8d', accent:'#034cff'}, dark:{neutral:'#080808', ink:'#fafafa', primary:'#a2bcff', accent:'#3b5cf6'}}];
    if (src) {
      var txt = await (await fetch(src)).text();
      var base = new URL(src).origin + '/assets/';
      var re = /"\.\/themes\/([a-z0-9-]+)\.json":\(\)=>[A-Za-z_$]+\(\(\)=>import\("\.\/([^"]+)"/g, m, files = [];
      while ((m = re.exec(txt))) files.push([m[1], m[2]]);
      var mods = await Promise.all(files.map(function(f){ return import(base + f[1]).then(function(mod){
        return {id:f[0], name:mod.name || f[0], light:mod.light && mod.light.palette, dark:mod.dark && mod.dark.palette};
      }).catch(function(){ return null; }); }));
      mods.forEach(function(x){ if (x && x.light && x.dark) out.push(x); });
    }
    if (window.MobilecodeBridge) MobilecodeBridge.themesData(JSON.stringify(out));
  } catch(e) { if (window.MobilecodeBridge) MobilecodeBridge.themesData('[]'); }
})();
"""

    /** Reads the active theme's colors (resolved to rgb) from the page so native screens can match. Returns a JSON string. */
    const val THEME_COLORS_JS = """
(function(){
  try {
    function c(n){ var el = document.createElement('span'); el.style.color = 'var(' + n + ')'; document.body.appendChild(el); var v = getComputedStyle(el).color; el.remove(); return v; }
    return JSON.stringify({bg:c('--v2-background-bg-deep'), card:c('--v2-background-bg-base'), layer:c('--v2-background-bg-layer-01'),
      button:c('--v2-background-bg-button-neutral'), border:c('--v2-border-border-base'), fg:c('--v2-text-text-base'),
      muted:c('--v2-text-text-muted'), faint:c('--v2-text-text-faint'), accent:c('--v2-text-text-accent'),
      invBg:c('--v2-background-bg-inverse'), invFg:c('--v2-text-text-inverse')});
  } catch(e) { return '{}'; }
})()
"""

    /** Replaces OpenCode's large home-screen logo with the Orlon Code wordmark (same grid, same fade). Never throws. */
    fun logoSwapJs(): String {
        val cell = Wordmark.OPENCODE_CELL
        val vb = Wordmark.viewBox(cell)
        val w = Wordmark.widthCells * cell
        val h = Wordmark.HEIGHT_CELLS * cell
        return """
(function(){
  try {
    var D = '${Wordmark.pathData(cell)}', VB = '$vb', W = $w, H = $h;
    function swap(){
      document.querySelectorAll('svg.h-auto.w-full.text-v2-background-bg-inverse').forEach(function(s){
        if (s.getAttribute('data-oc') === '1') return;
        var paths = s.querySelectorAll('path'); if (!paths.length) return;
        s.setAttribute('viewBox', VB);
        paths.forEach(function(p, i){ if (i > 0) p.remove(); });
        paths[0].setAttribute('d', D);
        s.querySelectorAll('mask, mask rect').forEach(function(m){ m.setAttribute('width', W); m.setAttribute('height', H); });
        s.querySelectorAll('linearGradient').forEach(function(g){ g.setAttribute('x1', W/2); g.setAttribute('x2', W/2); g.setAttribute('y1', H*0.53); g.setAttribute('y2', H); });
        s.querySelectorAll('[mask]').forEach(function(m){ m.removeAttribute('mask'); });
        s.style.cssText += ';color:#fff!important;opacity:1!important'; s.querySelectorAll('*').forEach(function(n){ n.style.setProperty('fill', '#fff', 'important'); n.style.setProperty('opacity', '1', 'important'); }); paths[0].setAttribute('fill', 'currentColor'); paths[0].style.fill = 'currentColor';
        s.setAttribute('data-oc', '1');
      });
    }
    swap();
    if (window.__ocLogo) return; window.__ocLogo = true;
    var pending = false;
    new MutationObserver(function(){ if (pending) return; pending = true; setTimeout(function(){ pending = false; swap(); }, 150); })
      .observe(document.documentElement, {childList:true, subtree:true});
  } catch(e) {}
})();
"""
    }

    /**
     * Makes APK paths in the chat tappable (on-device mode). It never edits the page's text: taps are caught and the path under
     * the finger is read from the text node, and whole-path code spans just get a link look. The app then validates the path.
     */
    const val APK_LINKS_JS = """
(function(){
  try {
    if (window.__mcApkLinks) return; window.__mcApkLinks = true;
    var RE = /\/root\/projects\/[^\s`'"<>()]+?\/\.orlon\/out\/[^\s`'"<>()]+?\.apk/g;
    var WHOLE = /^\/root\/projects\/[^\s`'"<>()]+?\/\.orlon\/out\/[^\s`'"<>()]+?\.apk$/;
    var st = document.createElement('style');
    st.textContent = '[data-mc-apk]{cursor:pointer;text-decoration:underline;text-underline-offset:3px;color:var(--v2-text-text-accent,#a2bcff)!important} [data-mc-apk]::before{content:"\\2B07  ";text-decoration:none;display:inline-block}';
    document.head.appendChild(st);
    function mark(){
      try { document.querySelectorAll('code').forEach(function(c){ if (!c.hasAttribute('data-mc-apk') && WHOLE.test((c.textContent || '').trim())) c.setAttribute('data-mc-apk', c.textContent.trim()); }); } catch(e) {}
    }
    mark(); var pending = false;
    new MutationObserver(function(){ if (pending) return; pending = true; setTimeout(function(){ pending = false; mark(); }, 300); })
      .observe(document.documentElement, {childList:true, subtree:true, characterData:true});
    document.addEventListener('click', function(e){
      try {
        var path = null, el = e.target && e.target.closest ? e.target.closest('[data-mc-apk]') : null;
        if (el) path = el.getAttribute('data-mc-apk');
        if (!path && document.caretRangeFromPoint) {
          var r = document.caretRangeFromPoint(e.clientX, e.clientY);
          if (r && r.startContainer.nodeType === 3) {
            var txt = r.startContainer.nodeValue, off = r.startOffset, m; RE.lastIndex = 0;
            while ((m = RE.exec(txt))) { if (off >= m.index && off <= m.index + m[0].length) { path = m[0]; break; } }
          }
        }
        if (path && window.MobilecodeBridge && MobilecodeBridge.installApk) { e.preventDefault(); e.stopPropagation(); MobilecodeBridge.installApk(path); }
      } catch(err) {}
    }, true);
  } catch(e) {}
})();
"""
}
