(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const mode = document.body.dataset.business;
  if (!mode) return;
  const role = document.body.dataset.role;
  const canBuy = ['OWNER', 'ADMIN', 'PURCHASING'].includes(role);
  const canReceive = ['OWNER', 'ADMIN', 'WAREHOUSE'].includes(role);
  document.querySelectorAll('[data-capability]').forEach(el => {
    if ((el.dataset.capability === 'buy' && !canBuy) || (el.dataset.capability === 'receive' && !canReceive)) el.hidden = true;
  });
  const currency = new Intl.NumberFormat('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  const money = value => currency.format(Number(value));
  const date = value => value ? new Intl.DateTimeFormat('th-TH', { dateStyle: 'medium' }).format(new Date(value)) : '—';
  const todayParts = new Intl.DateTimeFormat('en', { timeZone: 'Asia/Bangkok', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const part = type => todayParts.find(p => p.type === type).value;
  const today = `${part('year')}-${part('month')}-${part('day')}`;
  const statuses = { ORDERED: 'รอรับสินค้า', PARTIALLY_RECEIVED: 'รับบางส่วน', RECEIVED: 'รับครบแล้ว', CANCELLED: 'ยกเลิก', OPEN: 'ยังไม่ชำระ', PARTIALLY_PAID: 'ชำระบางส่วน', PAID: 'ชำระครบแล้ว' };
  const productPhotoIds=new Map();
  let suppliers = [], orders = [], receipts = [], selectedOrder = null, receiptKey = null, selectedInvoice = null, paymentKey = null;
  let busy = false;
  let stale = true;
  const storageKey = `kk.business.pending.${document.body.dataset.userId}.${document.body.dataset.storeId}`;
  let pendingMutation = null;
  try { pendingMutation = JSON.parse(sessionStorage.getItem(storageKey) || 'null'); } catch { /* No valid pending operation. */ }
  function savePending(value) {
    if (value) sessionStorage.setItem(storageKey, JSON.stringify(value)); else sessionStorage.removeItem(storageKey);
    pendingMutation = value; $('retry-pending').hidden = !value;
  }

  function notify(message, success = false) {
    const target = $('message'); target.textContent = message; target.classList.toggle('success', success);
    target.hidden = false; target.focus();
    $('retry-pending').hidden = !pendingMutation;
  }
  async function api(path, data, method = 'POST') {
    const options = { credentials: 'same-origin', headers: { Accept: 'application/json' } };
    if (data !== undefined) {
      options.method = method; options.headers['Content-Type'] = 'application/json';
      options.headers['X-CSRF-Token'] = document.body.dataset.csrf; options.body = JSON.stringify(data);
    }
    const protectedWrite = data !== undefined && method === 'POST' && /\/(orders|receipts|payments|invoices)$/.test(path);
    if (protectedWrite) {
      if (pendingMutation && (pendingMutation.path !== path || JSON.stringify(pendingMutation.data) !== JSON.stringify(data)))
        throw new Error('มีรายการที่ยังไม่ได้รับผลยืนยัน กรุณากดตรวจสอบรายการเดิมก่อนสร้างรายการใหม่');
      savePending({ path, data, method });
    }
    let response;
    try { response = await fetch(path, options); }
    catch { throw new Error('ยังยืนยันผลรายการไม่ได้ กรุณาใช้ปุ่มตรวจสอบรายการเดิมเพื่อส่งข้อมูลและรหัสเดิมอีกครั้ง'); }
    if (response.status === 401) { window.location.assign('/login'); throw new Error('กรุณาเข้าสู่ระบบใหม่'); }
    let body;
    try { body = await response.json(); } catch { throw new Error('อ่านผลลัพธ์ไม่ได้ กรุณาโหลดข้อมูลใหม่เพื่อตรวจสอบรายการก่อนส่งซ้ำ'); }
    if (!response.ok) {
      if (protectedWrite && [400, 422].includes(response.status)) savePending(null);
      throw new Error(body.message || body.detail || 'ทำรายการไม่สำเร็จ กรุณาตรวจสอบสิทธิ์และข้อมูล');
    }
    if (protectedWrite) savePending(null);
    return body;
  }
  function cell(row, value, numeric = false) {
    const td = document.createElement('td'); td.textContent = value == null ? '—' : value;
    if (numeric) td.className = 'numeric'; row.append(td); return td;
  }
  function row(target) { const tr = document.createElement('tr'); target.append(tr); return tr; }
  function empty(target, columns, text) {
    const td = cell(row(target), text); td.colSpan = columns; td.className = 'empty';
  }
  function action(target, label, callback) {
    const button = document.createElement('button'); button.type = 'button'; button.className = 'secondary';
    button.textContent = label; button.addEventListener('click', event => {
      if (busy) return;
      if (pendingMutation) { notify('กรุณาตรวจสอบรายการเดิมที่ยังไม่ได้รับผลยืนยันก่อน'); return; }
      if (stale) { notify('ข้อมูลอาจไม่เป็นปัจจุบัน กรุณาโหลดข้อมูลใหม่ก่อนทำรายการ'); return; }
      callback(event);
    }); target.append(button); return button;
  }
  function option(select, value, label) {
    const el = document.createElement('option'); el.value = value; el.textContent = label; select.append(el);
  }
  function bind(formId, task) {
    $(formId).addEventListener('submit', async event => {
      event.preventDefault(); if (busy) return;
      if (pendingMutation) { notify('กรุณาใช้ปุ่มตรวจสอบรายการเดิมก่อนทำรายการใหม่'); return; }
      if (stale) { notify('กรุณาโหลดข้อมูลใหม่ก่อนทำรายการ'); return; }
      const form = event.currentTarget; if (!form.reportValidity()) return;
      busy = true; const submit = form.querySelector('[type=submit]'); const label = submit.textContent;
      submit.disabled = true; submit.textContent = 'กำลังบันทึก…';
      try { await task(form); } catch (error) { notify(error.message); }
      finally {
        busy = false; submit.textContent = label;
        submit.disabled = formId === 'receive-form' && (!canReceive || !selectedOrder || !['ORDERED', 'PARTIALLY_RECEIVED'].includes(selectedOrder.status));
      }
    });
  }
  function field(form, name) { return form.elements.namedItem(name); }
  async function refresh() {
    $('loading').hidden = false; $('refresh').disabled = true;
    try {
      if (mode === 'purchasing') await loadPurchasing(); else await loadAccounting();
      stale = false; return true;
    } catch (error) { stale = true; notify(error.message); return false; }
    finally { $('loading').hidden = true; $('refresh').disabled = false; }
  }
  async function saved(message) {
    const loaded = await refresh();
    notify(loaded ? message : `${message} แต่โหลดข้อมูลล่าสุดไม่สำเร็จ กรุณากดโหลดข้อมูลใหม่ก่อนทำรายการต่อ`, loaded);
  }

  async function loadPurchasing() {
    const [s, o, r, products] = await Promise.all([
      api('/api/purchasing/suppliers'), api('/api/purchasing/orders'), api('/api/purchasing/receipts'), api('/api/products')
    ]);
    suppliers = s; orders = o; receipts = r;
    const supplierRows = $('supplier-rows'); supplierRows.replaceChildren();
    const select = $('order-supplier'); select.replaceChildren(); option(select, '', 'เลือกผู้จำหน่าย');
    for (const supplier of suppliers) {
      const tr = row(supplierRows); cell(tr, supplier.code); cell(tr, supplier.name);
      cell(tr, [supplier.contactName, supplier.phone].filter(Boolean).join(' / ') || '—');
      cell(tr, supplier.active ? 'เปิดใช้งาน' : 'ปิดใช้งาน'); const actions = cell(tr, '');
      if (canBuy) action(actions, 'แก้ไข', () => {
        const form = $('supplier-form'); form.closest('details').open = true;
        for (const name of ['id', 'code', 'name', 'contactName', 'phone', 'email', 'address', 'active']) field(form, name).value = supplier[name] ?? '';
        field(form, 'code').readOnly = true; field(form, 'name').focus(); form.scrollIntoView({ block: 'center' });
      });
      if (supplier.active) option(select, supplier.id, `${supplier.code} · ${supplier.name}`);
    }
    if (!suppliers.length) empty(supplierRows, 5, 'ยังไม่มีผู้จำหน่าย เพิ่มผู้จำหน่ายก่อนสร้างใบสั่งซื้อ');
    productPhotoIds.clear();for(const p of products)productPhotoIds.set(p.code,p.id);
    $('product-options').replaceChildren();
    for (const product of products) option($('product-options'), product.code, `${product.code} · ${product.name}`);
    const orderRows = $('order-rows'); orderRows.replaceChildren();
    for (const order of orders) {
      const tr = row(orderRows); cell(tr, order.number).className = 'document-number'; cell(tr, order.supplierName);
      cell(tr, statuses[order.status] || order.status); cell(tr, money(order.total), true); const actions = cell(tr, '');
      action(actions, canReceive && ['ORDERED', 'PARTIALLY_RECEIVED'].includes(order.status) ? 'รับสินค้า' : 'ดูรายละเอียด', () => openOrder(order));
      if (canBuy && order.status === 'ORDERED') action(actions, 'ยกเลิก', async () => {
        if (busy || !window.confirm('ยกเลิกใบสั่งซื้อนี้? ทำได้เฉพาะรายการที่ยังไม่รับสินค้า')) return;
        busy = true;
        try { await api(`/api/purchasing/orders/${order.id}/cancel`, {}); await saved('ยกเลิกใบสั่งซื้อแล้ว'); }
        catch (error) { notify(error.message); } finally { busy = false; }
      });
    }
    if (!orders.length) empty(orderRows, 5, 'ยังไม่มีใบสั่งซื้อ');
    const receiptRows = $('receipt-rows'); receiptRows.replaceChildren();
    for (const receipt of receipts) {
      const tr = row(receiptRows); cell(tr, receipt.number).className = 'document-number'; cell(tr, receipt.orderNumber).className = 'document-number';
      cell(tr, date(receipt.receivedAt)); cell(tr, money(receipt.total), true);
    }
    if (!receipts.length) empty(receiptRows, 4, 'ยังไม่มีการรับสินค้าจากใบสั่งซื้อ');
    if (selectedOrder) { const fresh = orders.find(o => o.id === selectedOrder.id); if (fresh) openOrder(fresh, false); }
  }

  function addOrderLine() {
    const tr = row($('order-lines'));
    for (const [name, type, attributes] of [
      ['productCode', 'text', { list: 'product-options', maxlength: '255' }],
      ['quantity', 'number', { min: '1', max: '2147483647', step: '1', value: '1' }],
      ['unitCost', 'number', { min: '0', max: '999999999.9999', step: '0.0001' }]
    ]) {
      const td = cell(tr, ''); const input = document.createElement('input'); input.name = name; input.type = type; input.required = true;
      input.setAttribute('aria-label', { productCode: 'รหัสสินค้า', quantity: 'จำนวนสั่ง', unitCost: 'ต้นทุนต่อหน่วย' }[name]);
      for (const [key, value] of Object.entries(attributes)) input.setAttribute(key, value); td.append(input);
    }
    action(cell(tr, ''), 'ลบรายการ', () => { tr.remove(); if (!$('order-lines').children.length) addOrderLine(); });
  }

  function openOrder(order, focus = true) {
    selectedOrder = order; receiptKey = crypto.randomUUID(); $('receive-section').hidden = false;
    $('receive-reference').textContent = `${order.number} · ${order.supplierName} · ${statuses[order.status] || order.status}`;
    const target = $('receive-lines'); target.replaceChildren();
    const canSubmit = canReceive && ['ORDERED', 'PARTIALLY_RECEIVED'].includes(order.status);
    for (const item of order.items) {
      const tr = row(target); tr.dataset.itemId = item.id;
      const productCell=cell(tr, `${item.productCode} · ${item.productName}`);const photoId=productPhotoIds.get(item.productCode);if(photoId){const photo=document.createElement('img');photo.className='kk-photo';photo.alt='';photo.loading='lazy';photo.src='/products/'+photoId+'/image';productCell.prepend(photo);} cell(tr, item.quantity, true); cell(tr, item.receivedQuantity, true);
      const remaining = item.quantity - item.receivedQuantity; cell(tr, remaining, true);
      const amount = document.createElement('input'); amount.type = 'number'; amount.name = 'quantity'; amount.min = '0'; amount.max = remaining;
      amount.step = '1'; amount.value = '0'; amount.disabled = !canSubmit || remaining === 0; amount.setAttribute('aria-label', `รับ ${item.productName} ครั้งนี้`); cell(tr, '').append(amount);
      const expiry = document.createElement('input'); expiry.type = 'date'; expiry.name = 'expiryDate'; expiry.min = today;
      expiry.disabled = !canSubmit || remaining === 0; expiry.setAttribute('aria-label', `วันหมดอายุ ${item.productName}`); cell(tr, '').append(expiry);
    }
    $('receive-submit').disabled = !canSubmit;
    if (focus) { $('receive-section').scrollIntoView({ block: 'start' }); $('receive-title').focus(); }
  }

  async function loadAccounting() {
    const [invoices, received, balances, journals] = await Promise.all([
      api('/api/accounting/invoices'), api('/api/purchasing/receipts'), api('/api/accounting/trial-balance'), api('/api/accounting/journals')
    ]);
    const select = $('invoice-receipt'); select.replaceChildren(); option(select, '', 'เลือกเอกสารรับสินค้า');
    const invoiced = new Set(invoices.map(i => i.receiptNumber));
    for (const receipt of received) if (!invoiced.has(receipt.number)) option(select, receipt.id, `${receipt.number} · ${money(receipt.total)} บาท`);
    const invoiceRows = $('invoice-rows'); invoiceRows.replaceChildren();
    for (const invoice of invoices) {
      const tr = row(invoiceRows); cell(tr, `${invoice.vendorNumber} · ${invoice.supplierName}`); cell(tr, date(invoice.dueDate));
      cell(tr, money(invoice.amount), true); cell(tr, money(invoice.paid), true); cell(tr, money(invoice.balance), true); cell(tr, statuses[invoice.status] || invoice.status);
      const target = cell(tr, ''); if (Number(invoice.balance) > 0) action(target, 'บันทึกชำระ', () => {
        selectedInvoice = invoice; paymentKey = crypto.randomUUID(); const form = $('payment-form');
        field(form, 'amount').value = invoice.balance; field(form, 'amount').max = invoice.balance;
        field(form, 'paymentDate').value = today; field(form, 'paymentDate').max = today;
        $('payment-reference').textContent = `${invoice.vendorNumber} · ${invoice.supplierName} · ค้างชำระ ${money(invoice.balance)} บาท`;
        $('payment-section').hidden = false; $('payment-section').scrollIntoView({ block: 'center' }); $('payment-title').focus();
      });
    }
    if (!invoices.length) empty(invoiceRows, 7, 'ยังไม่มีใบแจ้งหนี้ผู้จำหน่าย');
    const balanceRows = $('balance-rows'); balanceRows.replaceChildren(); let debit = 0, credit = 0;
    for (const balance of balances) {
      const tr = row(balanceRows); cell(tr, balance.accountCode); cell(tr, balance.accountName);
      cell(tr, money(balance.debit), true); cell(tr, money(balance.credit), true); debit += Number(balance.debit); credit += Number(balance.credit);
    }
    if (!balances.length) empty(balanceRows, 4, 'ยังไม่มีรายการลงบัญชี');
    $('total-debit').textContent = money(debit); $('total-credit').textContent = money(credit);
    const journalRows = $('journal-rows'); journalRows.replaceChildren();
    for (const journal of journals) for (let index = 0; index < journal.lines.length; index++) {
      const line = journal.lines[index], tr = row(journalRows);
      cell(tr, index === 0 ? `${journal.reference} · ${date(journal.postedAt)}` : '').className = 'document-number';
      cell(tr, `${line.accountCode} · ${line.accountName}`); cell(tr, money(line.debit), true); cell(tr, money(line.credit), true);
    }
    if (!journals.length) empty(journalRows, 4, 'ยังไม่มีสมุดรายวันจากธุรกรรมใหม่');
  }

  if (mode === 'purchasing') {
    addOrderLine(); $('add-order-line').addEventListener('click', () => { if (!busy && !pendingMutation) addOrderLine(); });
    $('supplier-form').addEventListener('reset', () => { field($('supplier-form'), 'id').value = ''; field($('supplier-form'), 'code').readOnly = false; });
    bind('supplier-form', async form => {
      const data = Object.fromEntries(new FormData(form)); const id = data.id; delete data.id; data.active = data.active === 'true';
      await api(`/api/purchasing/suppliers${id ? '/' + id : ''}`, data, id ? 'PUT' : 'POST'); form.reset();
      await saved('บันทึกผู้จำหน่ายแล้ว');
    });
    bind('order-form', async form => {
      const items = [...$('order-lines').rows].map(tr => ({ productCode: tr.querySelector('[name=productCode]').value, quantity: Number(tr.querySelector('[name=quantity]').value), unitCost: tr.querySelector('[name=unitCost]').value }));
      const created = await api('/api/purchasing/orders', { requestKey: crypto.randomUUID(), supplierId: Number(field(form, 'supplierId').value), note: field(form, 'note').value, items });
      form.reset(); $('order-lines').replaceChildren(); addOrderLine();
      await saved('สร้างใบสั่งซื้อแล้ว สต็อกจะเพิ่มเมื่อยืนยันรับสินค้า'); if (!stale) openOrder(created);
    });
    bind('receive-form', async () => {
      if (!selectedOrder || !canReceive) throw new Error('กรุณาเลือกใบสั่งซื้อที่รับสินค้าได้');
      const items = [...$('receive-lines').rows].map(tr => ({ orderItemId: Number(tr.dataset.itemId), quantity: Number(tr.querySelector('[name=quantity]').value), expiryDate: tr.querySelector('[name=expiryDate]').value || null })).filter(i => i.quantity > 0);
      if (!items.length) throw new Error('กรุณาระบุจำนวนรับสินค้าอย่างน้อยหนึ่งรายการ');
      const receipt = await api(`/api/purchasing/orders/${selectedOrder.id}/receipts`, { requestKey: receiptKey, items });
      await saved(`รับสินค้าเข้าคลังแล้ว เอกสาร ${receipt.number}`);
    });
  } else {
    field($('invoice-form'), 'invoiceDate').value = today; field($('invoice-form'), 'dueDate').value = today;
    bind('invoice-form', async form => {
      const data = Object.fromEntries(new FormData(form)); data.receiptId = Number(data.receiptId);
      await api('/api/accounting/invoices', data); form.reset(); field(form, 'invoiceDate').value = today; field(form, 'dueDate').value = today;
      await saved('บันทึกใบแจ้งหนี้และรายการบัญชีแล้ว');
    });
    bind('payment-form', async form => {
      if (!selectedInvoice) throw new Error('กรุณาเลือกใบแจ้งหนี้');
      await api(`/api/accounting/invoices/${selectedInvoice.id}/payments`, { requestKey: paymentKey, amount: field(form, 'amount').value, paymentDate: field(form, 'paymentDate').value, note: field(form, 'note').value });
      $('payment-section').hidden = true; selectedInvoice = null; form.reset(); await saved('บันทึกการชำระเงินและรายการบัญชีแล้ว');
    });
  }
  $('refresh').addEventListener('click', () => { if (!busy) refresh(); });
  $('business-mobile-menu').addEventListener('click', event => {
    event.stopPropagation(); const open = document.body.classList.toggle('kk-sidebar-open');
    $('business-mobile-menu').setAttribute('aria-expanded', String(open));
  });
  $('retry-pending').hidden = !pendingMutation;
  $('retry-pending').addEventListener('click', async () => {
    if (busy || !pendingMutation) return;
    busy = true; $('retry-pending').disabled = true;
    const original = pendingMutation;
    try {
      await api(original.path, original.data, original.method);
      selectedOrder = null; selectedInvoice = null;
      document.querySelectorAll('.business-main form').forEach(form => form.reset());
      if (mode === 'purchasing') { $('receive-section').hidden = true; $('order-lines').replaceChildren(); addOrderLine(); }
      else { $('payment-section').hidden = true; field($('invoice-form'), 'invoiceDate').value = today; field($('invoice-form'), 'dueDate').value = today; }
      await saved('ตรวจสอบรายการเดิมสำเร็จ ระบบใช้รหัสเดิมเพื่อป้องกันการบันทึกซ้ำ');
    } catch (error) { notify(error.message); }
    finally { busy = false; $('retry-pending').disabled = false; }
  });
  if (pendingMutation) notify('มีรายการที่ยังไม่ได้รับผลยืนยัน กรุณาตรวจสอบรายการเดิมก่อนสร้างรายการใหม่');
  refresh();
})();
