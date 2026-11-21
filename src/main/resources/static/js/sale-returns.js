(() => {
  'use strict';
  const el = id => document.getElementById(id), root = el('return-page'), form = el('return-form');
  if (!root || !form) return;
  const storage = `kk.refund.${root.dataset.storeId}.${root.dataset.userId}.${root.dataset.saleId}`, url = `/api/sales/${root.dataset.saleId}/returns`;
  let busy = false, reviewed = null, pending = null;
  const show = text => { el('return-error').textContent = text; el('return-error').hidden = false; el('return-error').focus(); };
  try { pending = JSON.parse(sessionStorage.getItem(storage) || 'null'); } catch { show('ข้อมูลรายการค้างไม่ถูกต้อง กรุณาตรวจประวัติการคืนก่อนเริ่มใหม่'); busy = true; }
  el('return-retry').hidden = !pending;
  function lock(value) { busy = value; form.querySelectorAll('input,select,textarea,button').forEach(e => { e.disabled = value || e.matches('.return-quantity[max="0"]'); }); el('return-retry').disabled = value; }
  async function post(path, data) {
    const response = await fetch(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': document.querySelector('meta[name=csrf-token]').content }, body: JSON.stringify(data) });
    let result; try { result = await response.json(); } catch { throw new Error('ยังยืนยันผลไม่ได้ กรุณาตรวจสอบรายการเดิม'); }
    if (!response.ok) { const error = new Error(result.message || result.detail || 'บันทึกรายการไม่ได้ กรุณาตรวจสอบข้อมูลและสิทธิ์'); error.status = response.status; throw error; }
    return result;
  }
  function lines() { return [...form.querySelectorAll('tr[data-item-id]')].map(row => ({ itemId: Number(row.dataset.itemId), quantity: Number(row.querySelector('.return-quantity').value), restock: row.querySelector('.return-restock').value === 'true' })).filter(row => row.quantity > 0); }
  const money = value => Number(value).toLocaleString('th-TH', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' บาท';
  function amount(id) { const text = el(id).value; if (!/^\d+(\.\d{1,2})?$/.test(text)) throw new Error('จำนวนเงินต้องมีไม่เกินสองตำแหน่งทศนิยม'); const value = Math.round(Number(text) * 100); if (!Number.isSafeInteger(value) || value > 99999999999999) throw new Error('จำนวนเงินเกินขอบเขต'); return value; }
  async function send(data) {
    lock(true);
    try { await post(url, data); sessionStorage.removeItem(storage); pending = null; location.reload(); }
    catch (error) {
      if ([400, 422].includes(error.status)) { sessionStorage.removeItem(storage); pending = null; reviewed = null; el('refund-review').hidden = true; el('return-submit').textContent = 'ตรวจสอบยอดคืน'; }
      el('return-retry').hidden = !pending; show(error.message + (pending ? ' เก็บรหัสเดิมไว้แล้ว ใช้ปุ่มตรวจสอบรายการเดิมก่อนสร้างรายการใหม่' : ''));
    } finally { lock(false); }
  }
  el('refund-method').addEventListener('change', () => { el('refund-mixed').hidden = el('refund-method').value !== 'MIXED'; el('refund-confirmed').checked = false; });
  el('return-retry').addEventListener('click', () => { if (!busy && pending) send(pending); });
  form.addEventListener('submit', async event => {
    event.preventDefault(); if (busy) return; if (pending) { show('กรุณาตรวจสอบรายการเดิมที่ยังไม่ได้รับผลยืนยันก่อน'); return; }
    try {
      const input = lines(); if (!input.length || input.some(l => !Number.isSafeInteger(l.quantity))) throw new Error('กรุณาเลือกจำนวนคืนอย่างน้อยหนึ่งรายการ');
      if (!reviewed || reviewed.key !== JSON.stringify(input)) {
        lock(true); const quote = await post(url + '/quote', input); reviewed = { key: JSON.stringify(input), quote };
        for (const field of ['net', 'tax', 'total']) el('refund-' + field).textContent = money(quote[field]);
        el('refund-review').hidden = false; el('refund-confirmed').checked = false; el('return-submit').textContent = 'บันทึกคืนสินค้าและคืนเงิน'; el('return-error').hidden = true; return;
      }
      if (!el('refund-confirmed').checked) throw new Error('กรุณายืนยันว่าได้ตรวจสินค้าและคืนเงินจริงครบแล้ว');
      const total = Math.round(Number(reviewed.quote.total) * 100), method = el('refund-method').value;
      const split = method === 'MIXED' ? [['CASH', amount('refund-cash')], ['TRANSFER', amount('refund-transfer')], ['CARD', amount('refund-card')]] : [[method, total]];
      if (split.reduce((sum, row) => sum + row[1], 0) !== total) throw new Error('ยอดคืนเงินทุกวิธีรวมกันต้องเท่ากับยอดคืนสุทธิ');
      const data = { requestKey: crypto.randomUUID(), lines: input, revision: reviewed.quote.revision, expectedTotal: (total / 100).toFixed(2), confirmed: true, reason: el('return-reason').value.trim(), payments: split.filter(([,value]) => value > 0).map(([name,value]) => ({ method: name, amount: (value / 100).toFixed(2), reference: el('refund-reference').value.trim() })) };
      if (!data.reason) throw new Error('กรุณาระบุเหตุผลคืนสินค้า');
      sessionStorage.setItem(storage, JSON.stringify(data)); pending = data; el('return-retry').hidden = false; await send(data);
    } catch (error) { show(error.message || 'ทำรายการไม่สำเร็จ กรุณาลองอีกครั้ง'); }
    finally { if (!pending) lock(false); }
  });
})();
