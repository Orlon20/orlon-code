package com.mobilecode.app

/**
 * Cosmetic only: swaps the standalone OpenCode wordmark and page title for Mobilecode.
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
        var t = document.title; if (/open\s*code/i.test(t)) document.title = t.replace(/open\s*code/gi, 'Mobilecode');
        var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var n; while ((n = w.nextNode())) {
          var el = n.parentElement; if (!el || el.nodeName === 'SCRIPT' || el.nodeName === 'STYLE' || el.nodeName === 'TEXTAREA') continue;
          if (exact.test(n.nodeValue) && !inDialog(el)) n.nodeValue = n.nodeValue.replace(/open\s*code/i, 'Mobilecode');
        }
        root.querySelectorAll && root.querySelectorAll('[aria-label],[alt]').forEach(function(e){
          ['aria-label','alt'].forEach(function(a){ var v = e.getAttribute(a); if (v && exact.test(v)) e.setAttribute(a, 'Mobilecode'); });
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
    st.textContent = '[class*="provider-tip"], main div[class*="min-h-7"][class*="min-w-0"][class*="flex-col"] { display: none !important; }';
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

    /** Drops leftover empty-draft tabs so each launch opens exactly one fresh prompt screen. Never throws. */
    const val PRUNE_DRAFTS_JS = """
(function(){
  try {
    var k = 'opencode.window.browser.dat:tabs';
    var tabs = JSON.parse(localStorage.getItem(k) || '[]');
    localStorage.setItem(k, JSON.stringify(tabs.filter(function(t){ return t && t.type !== 'draft'; })));
    localStorage.removeItem(k + '.recent');
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
      '[data-slot=basic-tool-tool-subtitle],[data-slot=shell-submessage-width],[data-component=shell-submessage],[data-slot=shell-submessage-value]' +
        '{white-space:normal!important;overflow:visible!important;text-overflow:clip!important;overflow-wrap:anywhere!important;max-width:100%!important;height:auto!important}' +
      '[data-slot=shell-submessage-value]{display:inline!important}' +
      '[data-slot=basic-tool-tool-info-main],[data-slot=basic-tool-tool-info-structured]{overflow:visible!important;height:auto!important;min-width:0}' +
      '[data-slot=basic-tool-tool-subtitle]{text-align:left!important}' +
      '[data-slot=basic-tool-tool-trigger-content],[data-component=tool-trigger],[data-slot=collapsible-trigger],[data-component=collapsible-trigger],[data-slot=basic-tool-tool-info],[data-component=basic-tool-tool-info]{height:auto!important;min-height:32px;max-height:none!important}' +
      'pre.shiki{white-space:pre-wrap!important;overflow-wrap:anywhere!important;overflow-x:hidden!important}';
    document.head.appendChild(st);
  } catch(e) {}
})();
"""
}
