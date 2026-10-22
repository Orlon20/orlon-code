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
        document.title = document.title.replace(re, 'Mobilecode');
        var w = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        var n; while ((n = w.nextNode())) {
          var p = n.parentNode && n.parentNode.nodeName;
          if (p === 'SCRIPT' || p === 'STYLE' || p === 'TEXTAREA') continue;
          if (re.test(n.nodeValue)) n.nodeValue = n.nodeValue.replace(re, 'Mobilecode');
          re.lastIndex = 0;
        }
        root.querySelectorAll && root.querySelectorAll('[aria-label],[alt],[title]').forEach(function(e){
          ['aria-label','alt','title'].forEach(function(a){
            var v = e.getAttribute(a); if (v && re.test(v)) e.setAttribute(a, v.replace(re,'Mobilecode')); re.lastIndex = 0;
          });
        });
      } catch(e) {}
    }
    swap(document.body || document.documentElement);
    new MutationObserver(function(){ swap(document.body); }).observe(document.documentElement, {childList:true, subtree:true, characterData:true});
  } catch(e) {}
})();
"""
}
