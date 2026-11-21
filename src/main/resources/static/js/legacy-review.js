(() => {
  'use strict';
  const form=document.getElementById('baseline-form'),rows=document.getElementById('baseline-lots'),message=document.getElementById('baseline-message');
  let product=null,busy=false;const key=`kongklang-baseline-${document.body.dataset.storeId}-${document.body.dataset.userId}`;
  let pending=null;try{pending=JSON.parse(sessionStorage.getItem(key)||'null');}catch{}
  function notify(text){message.textContent=text;message.hidden=false;}
  function add(){
    const tr=document.createElement('tr');
    for(const [name,type,label] of [['quantity','number','จำนวนจริง'],['unitCost','number','ต้นทุนต่อหน่วย'],['expiryDate','date','วันหมดอายุ']]){
      const td=document.createElement('td'),input=document.createElement('input');input.name=name;input.type=type;input.setAttribute('aria-label',label);
      if(name==='quantity'){input.min='1';input.step='1';input.required=true;}if(name==='unitCost'){input.min='0';input.step='0.0001';input.required=true;}
      td.append(input);tr.append(td);
    }
    const td=document.createElement('td'),button=document.createElement('button');button.type='button';button.className='secondary';button.textContent='ลบล็อต';button.onclick=()=>{if(!busy)tr.remove();};td.append(button);tr.append(td);rows.append(tr);
  }
  document.getElementById('baseline-add').onclick=()=>{if(!busy)add();};add();
  form.elements.namedItem('productId').addEventListener('change',async event=>{
    product=null;const id=event.target.value;if(!id)return;
    try{const response=await fetch(`/api/products/${encodeURIComponent(id)}`);if(!response.ok)throw new Error('อ่านสินค้าไม่ได้');const data=await response.json();if(event.target.value!==String(data.id))return;product=data;document.getElementById('baseline-current').textContent=`${data.code} · ยอดระบบก่อนตรวจนับ ${data.quantity} · เวอร์ชัน ${data.version}`;}
    catch(error){notify(error.message);}
  });
  if(pending){notify(`มีรายการ ${pending.code} ที่ยังไม่ได้รับผลยืนยัน กดปุ่มด้านล่างเพื่อตรวจสอบคำขอเดิม ห้ามสร้างคำขอใหม่จนทราบผล`);form.noValidate=true;}
  form.addEventListener('submit',async event=>{
    event.preventDefault();if(busy)return;
    if(!pending){
      if(!product){notify('เลือกสินค้าและรออ่านยอดก่อน');return;}
      pending={requestKey:crypto.randomUUID(),code:product.code,expectedQuantity:product.quantity,expectedVersion:product.version,confirmed:form.elements.namedItem('confirmed').checked,note:form.elements.namedItem('note').value,
        lots:[...rows.rows].map(tr=>({quantity:Number(tr.querySelector('[name=quantity]').value),unitCost:tr.querySelector('[name=unitCost]').value,expiryDate:tr.querySelector('[name=expiryDate]').value||null}))};
      try{sessionStorage.setItem(key,JSON.stringify(pending));}catch{pending=null;notify('เก็บคำขอป้องกันซ้ำไม่ได้ กรุณาอนุญาตพื้นที่จัดเก็บในเบราว์เซอร์');return;}
    }
    busy=true;const button=form.querySelector('[type=submit]');button.disabled=true;
    try{
      const response=await fetch('/api/stock/legacy-review',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.querySelector('meta[name=csrf-token]').content},body:JSON.stringify(pending)});
      const data=await response.json();if(!response.ok){if([400,401,403,404].includes(response.status)){pending=null;sessionStorage.removeItem(key);form.noValidate=false;}throw new Error(data.message||'ยังยืนยันผลไม่ได้ ส่งคำขอเดิมอีกครั้ง');}
      sessionStorage.removeItem(key);pending=null;notify(`บันทึก ${data.number} สำเร็จ ยอดหลังยืนยัน ${data.quantityAfter}`);const link=document.getElementById('baseline-document');link.href=`/stock/operations/${data.id}`;link.hidden=false;form.hidden=true;
    }catch(error){notify(error.message||'ยังยืนยันผลไม่ได้ กดปุ่มอีกครั้งเพื่อตรวจสอบคำขอเดิม');}
    finally{busy=false;button.disabled=false;}
  });
})();
