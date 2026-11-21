(() => {
 'use strict';
 const user=document.body.dataset.inventoryUser,store=document.body.dataset.inventoryStore;
 const storageKey=`kongklang.inventory.v1.${user}.${store}`;
 const message=document.getElementById('inventory-message'),panel=document.getElementById('inventory-pending');
 const description=document.getElementById('inventory-description'),retry=document.getElementById('inventory-retry');
 const details=document.getElementById('inventory-details');
 let pending=null,busy=false,storageOK=true;
 const disabledBefore=new Map();
 function error(text){message.textContent=text;message.hidden=false;message.focus({preventScroll:true});message.scrollIntoView({block:'center'});}
 function show(){
   panel.hidden=!pending;retry.disabled=busy;description.textContent=pending?pending.label:'';
   document.querySelectorAll('form').forEach(form=>{
     form.setAttribute('aria-busy',String(busy));
     form.querySelectorAll('button[type="submit"],input[type="submit"]').forEach(button=>{
       if(busy||pending||!storageOK){if(!disabledBefore.has(button))disabledBefore.set(button,button.disabled);button.disabled=true;}
       else if(disabledBefore.has(button)){button.disabled=disabledBefore.get(button);disabledBefore.delete(button);}
     });
   });
   if(!pending){details.textContent='';return;}
   const p=pending.payload||{};
   details.textContent=pending.mode==='scan'?'รับเข้าจากบาร์โค้ด โดยใช้ต้นทุนล่าสุดเมื่อบันทึกรายการครั้งแรก · วันหมดอายุ '+(new URL(pending.url,location.origin).searchParams.get('expiryDate')||'ไม่ระบุ'):
     [p.note,p.unitCost!=null?'ต้นทุนต่อหน่วย '+p.unitCost+' บาท':'',p.sellingPrice!=null?'ราคาขายใหม่ '+p.sellingPrice+' บาท':'',p.kind==='RECEIPT'?'วันหมดอายุ '+(p.expiryDate||'ไม่ระบุ'):'',p.kind==='ADJUST'?'ยอดระบบก่อนนับ '+p.expectedQuantity:''].filter(Boolean).join(' · ');
 }
 function clear(){sessionStorage.removeItem(storageKey);pending=null;show();}
 function persist(value){sessionStorage.setItem(storageKey,JSON.stringify(value));pending=value;show();}
 function valid(value){return value&&value.version===1&&typeof value.label==='string'&&
   typeof value.url==='string'&&['/api/stock/operations','/api/stock/scan-in'].includes(value.url.split('?')[0])&&
   (value.mode==='scan'||value.mode==='form')&&typeof value.requestKey==='string'&&value.payload&&typeof value.payload==='object';}
 try {
   if(!user||!store)throw new Error();
   const raw=sessionStorage.getItem(storageKey);
   if(raw){pending=JSON.parse(raw);if(!valid(pending))throw new Error();}
   const probe=storageKey+'.probe';sessionStorage.setItem(probe,'1');sessionStorage.removeItem(probe);
 }catch{storageOK=false;error('ไม่สามารถเก็บหรือตรวจสอบคำขอเดิมในเบราว์เซอร์ได้ กรุณาตรวจสอบประวัติสต็อกก่อนทำรายการต่อ');}
 show();
 const hasPending=()=>Boolean(pending)||!storageOK;
 async function send(value){
   if(busy)throw new Error('กำลังตรวจสอบรายการ กรุณารอสักครู่');
   busy=true;show();message.hidden=true;
   try {
     const headers={'Accept':'application/json','X-CSRF-Token':document.querySelector('meta[name="csrf-token"]')?.content||''};
     const options={method:'POST',credentials:'same-origin',headers};
     if(value.mode==='form'){headers['Content-Type']='application/json';options.body=JSON.stringify(value.payload);}
     const response=await fetch(value.url,options);
     let data=null;try{if((response.headers.get('content-type')||'').includes('application/json'))data=await response.json();}catch{}
     if(!response.ok){
       if(response.status===400||response.status===422)clear();
       throw new Error(data?.message||`ยังยืนยันผลไม่ได้ (HTTP ${response.status}) กรุณาตรวจสอบรายการเดิม`);
     }
     if(!data||!Number.isSafeInteger(data.id)||data.id<=0||typeof data.number!=='string'||typeof data.quantityAfter!=='number')
       throw new Error('ผลตอบกลับไม่ครบถ้วน กรุณาตรวจสอบและส่งรายการเดิมอีกครั้ง');
     clear();return data;
   }catch(e){const text=e instanceof TypeError?'การเชื่อมต่อขัดข้อง ยังยืนยันผลไม่ได้ กรุณาตรวจสอบรายการเดิม':e.message;error(text);throw new Error(text);}
   finally{busy=false;show();}
 }
 async function start(value){
   if(!storageOK)throw new Error('ไม่สามารถเก็บคำขอในเบราว์เซอร์ได้ กรุณาตรวจสอบประวัติก่อน');
   if(hasPending()||busy)throw new Error('มีรายการรอตรวจสอบ กรุณาใช้ปุ่มส่งรายการเดิมก่อน');
   try{persist(value);}catch{storageOK=false;throw new Error('เก็บคำขอไม่สำเร็จ จึงยังไม่ได้ส่งรายการ');}
   return send(value);
 }
 retry.addEventListener('click',async()=>{if(!pending||busy||!storageOK)return;try{await send(pending);location.assign('/stock/history');}catch{}});
 document.addEventListener('submit',async event=>{
   const form=event.target;
   if(!(form instanceof HTMLFormElement)||event.defaultPrevented)return;
   const action=new URL(form.action,location.href).pathname;
   const kind={'/stock/in':'RECEIPT','/stock/out':'OUT','/stock/adjust':'ADJUST'}[action];
   if(!kind)return;event.preventDefault();
   try {
     if(!form.reportValidity())return;
     const f=new FormData(form),requestKey=crypto.randomUUID();
     const integer=name=>{const raw=f.get(name);if(raw===null||raw==='')throw new Error('กรุณาเลือกสินค้าและรอข้อมูลล่าสุดก่อนบันทึก');const n=Number(raw);if(!Number.isSafeInteger(n)||n<0)throw new Error('จำนวนหรือเวอร์ชันข้อมูลไม่ถูกต้อง');return n;};
     const payload={requestKey,kind,code:String(f.get('code')||''),quantity:integer('quantity'),note:String(f.get('note')||'').trim()};
     if(kind==='RECEIPT'){payload.unitCost=String(f.get('purchaseCost')||'');payload.sellingPrice=f.get('sellingPrice')||null;payload.expiryDate=f.get('expiryDate')||null;}
     if(kind==='ADJUST'){
       payload.expectedQuantity=integer('expectedQuantity');payload.expectedVersion=integer('expectedVersion');
       if(payload.quantity>payload.expectedQuantity){payload.unitCost=f.get('countedCost')||null;payload.expiryDate=f.get('countedExpiry')||null;}
     }
     await start({version:1,mode:'form',url:'/api/stock/operations',requestKey,payload,label:`${payload.code} · ${kind==='ADJUST'?'ยอดหลังนับ':'จำนวน'} ${payload.quantity}`});
     location.assign('/stock/history');
   }catch(e){error(e.message);}
 });
 window.KongklangStock={hasPending,canQueueScan:()=>storageOK&&(!pending||(busy&&pending.mode==='scan')),sendScan:async(url,barcode)=>{
   const target=new URL(url,location.origin),requestKey=crypto.randomUUID();
   if(target.origin!==location.origin||target.pathname!=='/api/stock/scan-in')throw new Error('เส้นทางรับสินค้าไม่ถูกต้อง');
   target.searchParams.set('requestKey',requestKey);
   return start({version:1,mode:'scan',url:target.pathname+target.search,requestKey,payload:{},label:`บาร์โค้ด ${barcode} · รับเข้า 1 ชิ้น`});
 }};
})();
