(() => {
'use strict';
const box=document.createElement('div');box.className='kk-preloader';box.hidden=true;
box.innerHTML='<img src="/images/kongklang-logo.png" alt="KONGKLANG"><span class="kk-loading-ring" aria-hidden="true"></span><p role="status" aria-live="polite">กำลังโหลดข้อมูลร้าน…</p><small>กรุณารอสักครู่</small><div class="kk-load-actions" hidden><button type="button" data-load-cancel>กลับไปหน้านี้</button><button type="button" data-load-retry>ลองใหม่</button></div>';
document.body.append(box);
let timer, destination;const message=box.querySelector('p'), actions=box.querySelector('.kk-load-actions');
function hide(){clearTimeout(timer);box.hidden=true;actions.hidden=true;}
function show(full=false,url=null){destination=url;box.classList.toggle('kk-route-load',!full);box.hidden=false;actions.hidden=true;message.textContent=full?'กำลังเข้าสู่พื้นที่ทำงาน…':'กำลังเปิดหน้า…';clearTimeout(timer);timer=setTimeout(()=>{message.textContent='ใช้เวลาเชื่อมต่อนานกว่าปกติ';actions.hidden=false;},12000);}
box.querySelector('[data-load-cancel]').onclick=hide;
box.querySelector('[data-load-retry]').onclick=()=>{if(destination)location.assign(destination);else hide();};
window.addEventListener('pageshow',hide);window.addEventListener('pagehide',hide);
document.addEventListener('click',e=>{const a=e.target.closest('a');if(!a||e.defaultPrevented||e.button!==0||e.metaKey||e.ctrlKey||e.shiftKey||e.altKey||a.hasAttribute('download')||a.target&&a.target!=='_self')return;const u=new URL(a.href,location.href);if(u.origin!==location.origin||u.pathname===location.pathname&&u.search===location.search||!a.closest('.kk-menu,.kk-topbar'))return;show(false,u.href);});
document.querySelectorAll('.kk-summary-number').forEach(el=>{new MutationObserver(()=>{if(!matchMedia('(prefers-reduced-motion: reduce)').matches)el.animate([{color:'#2563eb',transform:'translateY(-2px)'},{color:getComputedStyle(el).color,transform:'none'}],{duration:300,easing:'ease-out'});}).observe(el,{childList:true,characterData:true,subtree:true});});
const login=document.querySelector('.kk-login-form');if(login)login.addEventListener('submit',()=>{if(login.checkValidity())show(true);});
const photo=document.querySelector('[name=photo]'),preview=document.querySelector('[data-photo-preview]');let objectUrl;
photo?.addEventListener('change',()=>{photo.setCustomValidity('');const file=photo.files[0];if(!file)return;if(file.size>5*1024*1024||!['image/jpeg','image/png'].includes(file.type)){photo.setCustomValidity('เลือกรูป JPG หรือ PNG ขนาดไม่เกิน 5 MB');photo.reportValidity();return;}if(objectUrl)URL.revokeObjectURL(objectUrl);objectUrl=URL.createObjectURL(file);if(preview)preview.src=objectUrl;});
if(['/purchasing','/stock/operations','/stock/in'].includes(location.pathname)) {
 document.addEventListener('change',async e=>{
  const input=e.target;if(!input.matches('input[name="productCode"],input[name="code"],select[name="code"]'))return;
  const code=input.value.trim();let thumb=input.parentElement.querySelector('[data-receive-photo]');
  if(!thumb){thumb=document.createElement('img');thumb.className='kk-photo';thumb.dataset.receivePhoto='';thumb.alt='รูปสินค้าที่เลือก';input.insertAdjacentElement('afterend',thumb);}thumb.hidden=true;
  if(!code)return;try{const response=await fetch('/products/by-code?code='+encodeURIComponent(code));if(!response.ok)return;const product=await response.json();if(input.value.trim()!==code||!product?.id)return;thumb.src='/products/'+product.id+'/image';thumb.hidden=false;}catch(_){}
 });
}
const hero=document.querySelector('.kk-hero');
if(hero){let frame=false;const check=()=>{frame=false;const sheet=document.getElementById('dashboard-content');hero.classList.toggle('kk-motion-paused',document.hidden||!!sheet&&sheet.getBoundingClientRect().top<=68);};window.addEventListener('scroll',()=>{if(!frame){frame=true;requestAnimationFrame(check);}},{passive:true});check();}
const motion=document.createElement('span');motion.className='kk-page-motion';motion.setAttribute('aria-hidden','true');motion.innerHTML='<i></i><i></i><i></i>';document.querySelector('.kk-topbar-left')?.append(motion);
const toggle=document.getElementById('kkMenuToggle');if(toggle){const label=()=>{const text=toggle.getAttribute('aria-expanded')==='true'?'ย่อเมนูด้านข้าง':'ขยายเมนูด้านข้าง';toggle.title=text;toggle.setAttribute('aria-label',text);};new MutationObserver(label).observe(toggle,{attributes:true,attributeFilter:['aria-expanded']});label();}
document.addEventListener('visibilitychange',()=>document.body.classList.toggle('kk-motion-paused',document.hidden));
})();
