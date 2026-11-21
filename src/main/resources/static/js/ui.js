(() => {
  'use strict';
  function ready() {
    const body=document.body,sidebar=document.getElementById('kkSidebar');
    const mobile=document.getElementById('kkMobileOpen'),collapse=document.getElementById('kkMenuToggle');
    const backdrop=document.getElementById('kkSidebarBackdrop');
    const isMobile=()=>matchMedia('(max-width: 991px)').matches;
    function setOpen(open){body.classList.toggle('kk-sidebar-open',open);mobile?.setAttribute('aria-expanded',String(open));if(backdrop)backdrop.hidden=!open;}
    if(sidebar){
      const menu=sidebar.querySelector('.kk-menu');
      if(menu){
        const key='kk.menu.scroll.'+(sidebar.dataset.store||'')+'.'+(sidebar.dataset.user||'');
        let saved=0,touched=false;
        try{saved=Number(sessionStorage.getItem(key))||0;}catch{}
        const restore=()=>{if(!touched)menu.scrollTop=saved;};
        restore();document.fonts?.ready.then(restore);
        const save=()=>{try{sessionStorage.setItem(key,String(menu.scrollTop));}catch{}};
        menu.addEventListener('wheel',()=>{touched=true;},{passive:true});menu.addEventListener('touchstart',()=>{touched=true;},{passive:true});
        menu.addEventListener('keydown',()=>{touched=true;});
        menu.addEventListener('scroll',save,{passive:true});menu.addEventListener('click',save,{capture:true});window.addEventListener('pagehide',save);
      }

      try{if(localStorage.getItem('kk.sidebarCollapsed')==='true'&&!isMobile())body.classList.add('kk-sidebar-collapsed');}catch{}
      collapse?.setAttribute('aria-expanded',String(!body.classList.contains('kk-sidebar-collapsed')));
      collapse?.addEventListener('click',()=>{
        if(isMobile()){setOpen(false);mobile?.focus();return;}
        const collapsed=body.classList.toggle('kk-sidebar-collapsed');collapse.setAttribute('aria-expanded',String(!collapsed));
        try{localStorage.setItem('kk.sidebarCollapsed',String(collapsed));}catch{}
      });
      mobile?.addEventListener('click',()=>setOpen(!body.classList.contains('kk-sidebar-open')));
      backdrop?.addEventListener('click',()=>setOpen(false));
      document.addEventListener('keydown',event=>{if(event.key==='Escape'&&body.classList.contains('kk-sidebar-open')){setOpen(false);mobile?.focus();}});
      matchMedia('(max-width: 991px)').addEventListener('change',()=>setOpen(false));
      const current=location.pathname;
      const links=[...sidebar.querySelectorAll('[data-path]')];
      const best=links.filter(a=>a.dataset.path==='/'?current==='/':current===a.dataset.path||current.startsWith(a.dataset.path+'/')).sort((a,b)=>b.dataset.path.length-a.dataset.path.length)[0];
      links.forEach(a=>{a.classList.toggle('active',a===best);if(a===best)a.setAttribute('aria-current','page');});
      const title=document.querySelector('.kk-topbar-title');if(title)title.textContent=best?.querySelector('.kk-menu-text')?.textContent||document.querySelector('main h1,h2')?.textContent.trim()||'คงคลัง';
    }
    const roles={OWNER:'เจ้าของร้าน',ADMIN:'ผู้ดูแลร้าน',SALES:'พนักงานขาย',STAFF:'พนักงานขาย',WAREHOUSE:'พนักงานคลัง',PURCHASING:'พนักงานจัดซื้อ',ACCOUNTING:'พนักงานบัญชี',SYSTEM_ADMIN:'ผู้ดูแลระบบกลาง'};
    document.querySelectorAll('.kk-role-label').forEach(node=>{node.textContent=roles[node.dataset.role]||node.textContent;});
    document.addEventListener('keydown',event=>{if((event.ctrlKey||event.metaKey)&&event.key.toLowerCase()==='k'){const search=document.getElementById('kkGlobalSearch');if(search){event.preventDefault();search.focus();}}});
    const q=new URLSearchParams(location.search).get('q'),search=document.getElementById('productLiveSearch');
    if(q&&search){search.value=q;search.dispatchEvent(new Event('input',{bubbles:true}));search.focus();}
    document.querySelectorAll('table').forEach(table=>{
      // Keep native table semantics and the existing search/filter code.
      table.querySelectorAll('thead th').forEach(th=>{if(!th.hasAttribute('scope'))th.setAttribute('scope','col');});
    });
    const sprite='http://www.w3.org/2000/svg';
    const symbols={'🛒':'cart','📷':'box','✏':'edit','🗑':'trash','📦':'box','📊':'chart','🔍':'search','➕':'plus','⚠':'warning','✅':'check','🟢':'check','🥤':'tag','🍜':'tag','🧂':'tag','🍪':'tag','🥬':'tag','🧊':'tag','🏠':'tag','❓':'tag'};
    const pattern=/(🛒|📷|✏️?|🗑️?|📦|📊|🔍|➕|⚠️?|✅|🟢|🥤|🍜|🧂|🍪|🥬|🧊|🏠|❓)/u;
    function normalizeIcons(root){
      const targets=root.querySelectorAll('.category-header span[style],.action-btn,button,.btn,.form-label,.mode-button,.auto-scan-title,.scan-ready-title,.fs-2');
      targets.forEach(node=>{
        if(node.matches('.action-btn')&&node.title&&!node.hasAttribute('aria-label'))node.setAttribute('aria-label',node.title);
        for(const child of [...node.childNodes]){
          if(child.nodeType!==Node.TEXT_NODE||!pattern.test(child.textContent))continue;
          const fragment=document.createDocumentFragment();
          for(const piece of child.textContent.split(pattern)){
            const name=symbols[piece.replace(/\uFE0F/g,'')];
            if(!name){fragment.append(document.createTextNode(piece));continue;}
            const svg=document.createElementNS(sprite,'svg'),use=document.createElementNS(sprite,'use');svg.setAttribute('class','kk-icon kk-inline-icon');svg.setAttribute('aria-hidden','true');use.setAttribute('href','/images/kk-icons.svg#'+name);svg.append(use);fragment.append(svg);
          }
          child.replaceWith(fragment);
        }
      });
    }
    normalizeIcons(document);
    function keepEmptyStatesVisible(){
      document.querySelectorAll('tbody td[colspan]').forEach(cell=>{
        if(cell.parentElement.children.length!==1)return;
        const scroll=cell.closest('.table-responsive,.table-scroll');if(!scroll)return;
        let content=cell.querySelector(':scope > .kk-table-empty');
        if(!content){content=document.createElement('div');content.className='kk-table-empty';while(cell.firstChild)content.append(cell.firstChild);cell.append(content);normalizeIcons(content);}
        const style=getComputedStyle(cell);const width=Math.max(160,scroll.clientWidth-parseFloat(style.paddingLeft)-parseFloat(style.paddingRight));
        content.style.width=width+'px';
      });
    }
    keepEmptyStatesVisible();
    const tables=[...document.querySelectorAll('.table-scroll,.table-responsive')];
    const observer=new MutationObserver(keepEmptyStatesVisible);tables.forEach(node=>observer.observe(node,{childList:true,subtree:true}));
    window.addEventListener('resize',keepEmptyStatesVisible,{passive:true});
    const note=document.querySelector('.inventory-accounting-note'),heading=document.querySelector('.page-header');
    if(note&&heading)heading.insertAdjacentElement('afterend',note);
    // Decorative emoji in old headings are replaced by the shared icon vocabulary.
    document.querySelectorAll('h1,h2,h3,h4,h5:not(.category-title),.card-header,.form-note .fw-semibold,.selector-help').forEach(node=>{
      for(const child of node.childNodes){if(child.nodeType===Node.TEXT_NODE)child.textContent=child.textContent.replace(/[\p{Extended_Pictographic}\uFE0F\u200D]/gu,'');}
    });
  }
  if(document.readyState!=='complete')document.addEventListener('DOMContentLoaded',ready,{once:true});else ready();
})();
