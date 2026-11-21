(() => {
  'use strict';
  const form=document.getElementById('manual-invoice-form'); if(!form)return;
  const message=document.getElementById('manual-message');
  const field=name=>form.elements.namedItem(name);
  const today=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Bangkok',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
  field('invoiceDate').value=today; field('dueDate').value=today;
  const notify=text=>{message.textContent=text;message.hidden=false;};
  const option=(select,value,label)=>{const node=document.createElement('option');node.value=value;node.textContent=label;select.append(node);};
  async function load() {
    try {
      const responses=await Promise.all(['/api/stock/operations','/api/purchasing/suppliers'].map(url=>fetch(url,{headers:{Accept:'application/json'}})));
      if(responses.some(r=>!r.ok))throw new Error('โหลดข้อมูลไม่ได้ กรุณาเปิดหน้านี้ใหม่');
      const [operations,suppliers]=await Promise.all(responses.map(r=>r.json()));
      option(field('operationId'),'','เลือกเอกสารรับเข้า');option(field('supplierId'),'','เลือกผู้จำหน่าย');
      operations.filter(o=>o.kind==='RECEIPT').forEach(o=>option(field('operationId'),o.id,`${o.number} · ${o.productCode} · ${(Number(o.valueAfter)-Number(o.valueBefore)).toFixed(2)} บาท`));
      suppliers.filter(s=>s.active).forEach(s=>option(field('supplierId'),s.id,`${s.code} · ${s.name}`));
      document.getElementById('manual-load').textContent='เลือกเอกสารจาก 100 รายการล่าสุด หากตั้งเจ้าหนี้แล้ว การส่งข้อมูลเดิมจะคืนเอกสารเดิม';
    } catch(error){notify(error.message);document.getElementById('manual-load').textContent='โหลดข้อมูลไม่สำเร็จ';}
  }
  form.addEventListener('submit',async event=>{
    event.preventDefault();const button=form.querySelector('button');if(button.disabled)return;button.disabled=true;
    try {
      const input=Object.fromEntries(new FormData(form));input.operationId=Number(input.operationId);input.supplierId=Number(input.supplierId);
      const response=await fetch('/api/accounting/manual-invoices',{method:'POST',headers:{'Content-Type':'application/json','X-CSRF-Token':document.body.dataset.csrf},body:JSON.stringify(input)});
      const result=await response.json();if(!response.ok)throw new Error(result.message||'ยังยืนยันผลไม่ได้ โปรดส่งข้อมูลเดิมอีกครั้ง');
      notify(`ตั้งเจ้าหนี้ ${result.vendorNumber} สำเร็จ ยอด ${Number(result.amount).toFixed(2)} บาท เปิดหน้านี้ใหม่เพื่อดูรายการเจ้าหนี้ล่าสุด`);
    } catch(error){notify(error.message||'ยังยืนยันผลไม่ได้ โปรดเก็บข้อมูลเดิมและลองอีกครั้ง');}
    finally{button.disabled=false;}
  });
  load();
})();
