package com.mobilecode.app

/** Cosmetic only: swaps visible OpenCode branding for Mobilecode. Never throws. */
object Branding {
    const val SWAP_JS = """
(function(){
  try {
    if (window.__mcBrand) return; window.__mcBrand = true;
    var re = /open\s*code/gi;
    function swap(root){
      try {
        var t = document.title; if (/open\s*code/i.test(t)) document.title = t.replace(/open\s*code/gi, 'Mobilecode');
        var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var n; while ((n = w.nextNode())) {
          var p = n.parentNode && n.parentNode.nodeName;
          if (p === 'SCRIPT' || p === 'STYLE' || p === 'TEXTAREA') continue;
          if (/open\s*code/i.test(n.nodeValue)) n.nodeValue = n.nodeValue.replace(re, 'Mobilecode');
        }
        root.querySelectorAll && root.querySelectorAll('[aria-label],[alt],[title]').forEach(function(e){
          ['aria-label','alt','title'].forEach(function(a){
            var v = e.getAttribute(a); if (v && /open\s*code/i.test(v)) e.setAttribute(a, v.replace(re,'Mobilecode'));
          });
        });
      } catch(e) {}
    }
    swap(document.body || document.documentElement);
    var pending = false;
    new MutationObserver(function(){ if (pending) return; pending = true; setTimeout(function(){ pending = false; swap(document.body); }, 400); }).observe(document.documentElement, {childList:true, subtree:true, characterData:true});
  } catch(e) {}
})();
"""
}
