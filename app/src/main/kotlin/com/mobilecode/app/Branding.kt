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
}
