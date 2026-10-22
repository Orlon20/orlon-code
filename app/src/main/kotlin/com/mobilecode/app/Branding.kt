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

    /** Phone-width fixes for OpenCode's UI (all modes): keep the prompt toolbar and long lines inside the screen. */
    const val FIT_JS = """
(function(){
  try {
    if (window.__mcFit) return; window.__mcFit = true;
    var st = document.createElement('style'); st.id = 'mc-fit';
    var bar = 'form[class*="prompt-input"] div[class*="min-w-0"][class*="flex-1"][class*="items-center"]';
    st.textContent =
      bar + '{overflow:hidden}' + bar + ' > *{min-width:0;flex-shrink:1}' + bar + ' button{min-width:0;max-width:100%}' +
      'html,body{overflow-x:hidden;max-width:100vw}' +
      '[data-slot=basic-tool-tool-subtitle]{display:-webkit-box!important;-webkit-box-orient:vertical!important;-webkit-line-clamp:3!important;overflow:hidden!important;text-overflow:ellipsis!important;white-space:normal!important;overflow-wrap:anywhere!important;max-width:100%!important;height:auto!important}' +
      '[data-slot=shell-submessage-width],[data-component=shell-submessage],[data-slot=shell-submessage-value]{white-space:normal!important;overflow:hidden!important;text-overflow:ellipsis!important;overflow-wrap:anywhere!important;max-width:100%!important;height:auto!important}' +
      '[data-slot=shell-submessage-value]{display:inline!important}' +
      '[data-slot=basic-tool-tool-info-main],[data-slot=basic-tool-tool-info-structured]{overflow:visible!important;height:auto!important;min-width:0}' +
      '[data-slot=basic-tool-tool-subtitle]{text-align:left!important}' +
      '[data-slot=basic-tool-tool-trigger-content],[data-component=tool-trigger],[data-slot=collapsible-trigger],[data-component=collapsible-trigger],[data-slot=basic-tool-tool-info],[data-component=basic-tool-tool-info]{height:auto!important;min-height:32px;max-height:none!important}' +
      'pre.shiki{white-space:pre-wrap!important;overflow-wrap:anywhere!important;overflow-x:hidden!important}';
    document.head.appendChild(st);
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
    var pending = false;
    new MutationObserver(function(){ if (pending) return; pending = true; setTimeout(function(){ pending = false; run(); }, 100); })
      .observe(document.documentElement, {childList:true, subtree:true});
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
}
