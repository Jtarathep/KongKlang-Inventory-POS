(() => {
  'use strict';
  const form = document.getElementById('saleForm');
  if (!form) return;
  const el = id => document.getElementById(id), message = el('checkout-message'), panel = el('checkout-pending');
  const keyInput = el('checkoutRequestKey'), retry = el('checkout-retry'), submit = form.querySelector('button[onclick="confirmSale()"]');
  const storageKey = `kk.checkout.${form.dataset.storeId}.${form.dataset.userId}`;
  let pending = null, busy = false, navigating = false, reviewed = null;
  try { pending = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { show('ข้อมูลรายการค้างไม่ถูกต้อง กรุณาตรวจประวัติการขายก่อนเริ่มบิลใหม่'); busy = true; }
  function show(text) { message.textContent = text; message.hidden = false; message.focus(); }
  function remember(value) {
    if (value) sessionStorage.setItem(storageKey, JSON.stringify(value)); else sessionStorage.removeItem(storageKey);
    pending = value; panel.hidden = !value;
    if (value) el('checkout-pending-description').textContent = value.productCodes.map((code, i) => `${code} × ${value.quantities[i]}`).join(', ');
  }
  function setBusy(value) {
    busy = value;
    form.querySelectorAll('button,input,select,textarea').forEach(control => { control.disabled = value; });
    retry.disabled = value; retry.textContent = value ? 'กำลังตรวจสอบรายการเดิม…' : 'ตรวจสอบและส่งรายการเดิมอีกครั้ง';
  }
  function cents(value) {
    const text = String(value).trim();
    if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('จำนวนเงินต้องเป็นศูนย์หรือมากกว่า และไม่เกินสองตำแหน่งทศนิยม');
    const [whole, fraction = ''] = text.split('.'), result = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
    if (!Number.isSafeInteger(result) || result > 99999999999999) throw new Error('จำนวนเงินเกินขอบเขต');
    return result;
  }
  const decimal = value => (value / 100).toFixed(2);
  const baht = value => Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' บาท';
  function basket() {
    const fields = new FormData(form), productCodes = fields.getAll('productCodes').map(String), quantities = fields.getAll('quantities').map(Number);
    if (!productCodes.length || quantities.some(q => !Number.isSafeInteger(q) || q <= 0)) throw new Error('กรุณาระบุสินค้าและจำนวนที่ถูกต้อง');
    const type = el('discountType').value, value = type === 'NONE' ? '0.00' : decimal(cents(el('discountValue').value));
    return { productCodes, quantities, discount: { type, value } };
  }
  async function post(url, body) {
    const response = await fetch(url, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-Token': form.elements.namedItem('_csrf').value }, body: JSON.stringify(body) });
    let result;
    try { result = await response.json(); } catch { throw new Error('ยังยืนยันผลรายการไม่ได้ กรุณาตรวจสอบรายการเดิมก่อนเริ่มใหม่'); }
    return { response, result };
  }
  function paymentMode() {
    const method = el('paymentMethod').value;
    el('mixed-payments').hidden = method !== 'MIXED'; el('cash-received').hidden = !['CASH', 'MIXED'].includes(method);
    updateChange();
  }
  function updateChange() {
    if(!reviewed)return;
    try {
      const applied=el('paymentMethod').value==='MIXED'?cents(el('payCash').value):cents(Number(reviewed.quote.pricing.payable).toFixed(2));
      const received=cents(el('cashReceived').value);
      el('cash-change').textContent=received>=applied?`เงินทอน ${baht((received-applied)/100)}`:`เงินสดยังขาด ${baht((applied-received)/100)}`;
    }catch{el('cash-change').textContent='กรอกยอดรับเงินจริงเพื่อดูเงินทอน';}
  }
  function changed() { reviewed = null; el('payment-review').hidden = true; el('paymentConfirmed').checked = false; submit.textContent = 'ตรวจสอบยอดชำระ'; }
  el('discountType').addEventListener('change', changed); el('discountValue').addEventListener('input', changed);
  el('paymentMethod').addEventListener('change', () => { paymentMode(); el('paymentConfirmed').checked = false; });
  for(const id of ['cashReceived','payCash','payTransfer','payCard'])el(id).addEventListener('input',()=>{el('paymentConfirmed').checked=false;updateChange();});
  el('documentType').addEventListener('change', () => { el('tax-buyer').hidden = el('documentType').value !== 'TAX_INVOICE'; });
  async function review(current) {
    setBusy(true);
    try {
      const { response, result } = await post('/api/sales/quote', current);
      if (!response.ok) throw new Error(result.message || result.detail || 'ตรวจสอบยอดไม่สำเร็จ กรุณาลองอีกครั้ง');
      const q = result.pricing;
      if (!q || !Number.isSafeInteger(result.taxRevision)) throw new Error('ข้อมูลยอดชำระไม่ครบถ้วน');
      reviewed = { basket: JSON.stringify(current), quote: result };
      for (const [name, field] of [['listed','listedAmount'],['discount','discount'],['net','net'],['tax','tax'],['total','payable']]) el(`quote-${name}`).textContent = baht(q[field]);
      el('quote-net-label').textContent = q.rule.mode === 'NONE' ? 'ยอดหลังส่วนลด' : 'ยอดก่อนภาษี';
      el('quote-tax-label').textContent = q.rule.mode === 'NONE' ? 'ไม่ได้เรียกเก็บ VAT' : `VAT ${q.rule.rate}%`;
      el('tax-invoice-option').disabled = !result.taxInvoiceAvailable;
      if (!result.taxInvoiceAvailable) { el('documentType').value = 'RECEIPT'; el('tax-buyer').hidden = true; }
      el('payment-review').hidden = false; el('paymentConfirmed').checked = false;
      el('cashReceived').value = Number(q.payable).toFixed(2); el('payCash').value = Number(q.payable).toFixed(2); el('payTransfer').value = '0'; el('payCard').value = '0';
      paymentMode(); submit.textContent = 'บันทึกขายและรับชำระ'; message.hidden = true;
    } finally { setBusy(false); }
  }
  function terms() {
    const q = reviewed.quote, total = cents(Number(q.pricing.payable).toFixed(2)), method = el('paymentMethod').value;
    if (!el('paymentConfirmed').checked) throw new Error('กรุณาตรวจรับเงินจริงและเลือกช่องยืนยันก่อนบันทึก');
    const reference = el('paymentReference').value.trim();
    const amounts = method === 'MIXED' ? [['CASH', cents(el('payCash').value)], ['TRANSFER', cents(el('payTransfer').value)], ['CARD', cents(el('payCard').value)]] : [[method, total]];
    if (amounts.reduce((sum, row) => sum + row[1], 0) !== total) throw new Error('ยอดชำระเงินสด โอน และบัตรรวมกันต้องเท่ากับยอดสุทธิ');
    const payments = amounts.filter(([, amount]) => amount > 0).map(([name, amount]) => {
      const received = name === 'CASH' ? cents(el('cashReceived').value) : amount;
      if (received < amount) throw new Error('เงินสดที่รับจริงน้อยกว่ายอดเงินสดที่ใช้ชำระ');
      return { method: name, amount: decimal(amount), received: decimal(received), reference: name === 'CASH' ? '' : reference };
    });
    const documentType = el('documentType').value;
    const buyer = documentType === 'TAX_INVOICE' ? { name: el('buyerName').value.trim(), address: el('buyerAddress').value.trim(), taxId: el('buyerTaxId').value.trim(), branchCode: el('buyerBranch').value.trim() } : null;
    if (buyer && (!buyer.name || !buyer.address || !/^\d{13}$/.test(buyer.taxId) || !/^\d{5}$/.test(buyer.branchCode))) throw new Error('กรุณากรอกข้อมูลผู้ซื้อใบกำกับภาษีให้ครบถ้วน');
    return { discount: q.pricing.discountRule, payments, expectedTotal: decimal(total), confirmed: true, taxRevision: q.taxRevision, documentType, buyer };
  }
  async function send(original) {
    if (busy) return;
    setBusy(true);
    try {
      const { response, result } = await post('/api/sales', original);
      if (!response.ok) {
        if ([400, 422].includes(response.status)) { remember(null); keyInput.value = crypto.randomUUID(); changed(); }
        throw new Error(result.message || result.detail || 'ยังยืนยันผลการขายไม่ได้ กรุณาตรวจสอบสิทธิ์และลองรายการเดิมอีกครั้ง');
      }
      if (!Number.isSafeInteger(result.id) || result.id <= 0) throw new Error('ผลตอบกลับไม่มีเลขอ้างอิงบิล กรุณาตรวจสอบรายการเดิม');
      remember(null); navigating = true; window.location.assign(`/sales/${result.id}/invoice`);
    } catch (error) {
      const detail = error instanceof TypeError ? 'การเชื่อมต่อขัดข้อง ยังยืนยันผลการขายไม่ได้' : error.message;
      show(pending ? `${detail} ข้อมูลและรหัสเดิมถูกเก็บไว้สำหรับตรวจสอบซ้ำ` : detail);
    } finally { if (!navigating) setBusy(false); }
  }
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return;
    if (pending) { show('มีบิลที่ยังไม่ได้รับผลยืนยัน กรุณาตรวจสอบรายการเดิมก่อนเริ่มบิลใหม่'); return; }
    try {
      const current = basket();
      if (!reviewed || reviewed.basket !== JSON.stringify(current)) { await review(current); return; }
      const original = { requestKey: keyInput.value || crypto.randomUUID(), productCodes: current.productCodes, quantities: current.quantities, terms: terms() };
      try { remember(original); } catch { throw new Error('ไม่สามารถเก็บรหัสรายการในเบราว์เซอร์ได้ กรุณาเปิดการจัดเก็บข้อมูลก่อนขาย'); }
      await send(original);
    } catch (error) { show(error.message || 'ตรวจสอบรายการไม่สำเร็จ กรุณาลองอีกครั้ง'); }
  });
  retry.addEventListener('click', () => { if (pending) send(pending); });
  if (pending) {
    if (!Array.isArray(pending.productCodes) || !Array.isArray(pending.quantities) || pending.productCodes.length !== pending.quantities.length || !pending.requestKey) { show('ข้อมูลรายการค้างไม่ถูกต้อง กรุณาตรวจประวัติการขายก่อนเริ่มบิลใหม่'); setBusy(true); }
    else { remember(pending); show('มีบิลที่ยังไม่ได้รับผลยืนยันจากครั้งก่อน กรุณาตรวจสอบรายการเดิม'); }
  }
})();
