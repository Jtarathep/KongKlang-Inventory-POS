(() => {
  'use strict';
  const input = document.getElementById('kkGlobalSearch');
  if (!input) return;
  const form = input.closest('form');
  const panel = document.createElement('div');
  panel.id = 'kkSearchSuggestions';
  panel.className = 'kk-search-suggestions';
  panel.hidden = true;
  panel.setAttribute('role', 'listbox');
  panel.setAttribute('aria-label', 'สินค้าของร้าน');
  form.append(panel);
  input.setAttribute('role', 'combobox');
  input.setAttribute('aria-autocomplete', 'list');
  input.setAttribute('aria-controls', panel.id);
  input.setAttribute('aria-expanded', 'false');
  input.maxLength = 120;
  let timer;
  let request;
  let options = [];
  let active = -1;

  function close() {
    clearTimeout(timer);
    request?.abort();
    panel.hidden = true;
    input.setAttribute('aria-expanded', 'false');
    input.removeAttribute('aria-activedescendant');
    active = -1;
  }

  function status(text) {
    panel.replaceChildren();
    options = [];
    active = -1;
    input.removeAttribute('aria-activedescendant');
    const message = document.createElement('p');
    message.className = 'kk-suggestion-status';
    message.setAttribute('role', 'status');
    message.textContent = text;
    panel.append(message);
  }

  async function suggest() {
    request?.abort();
    const current = new AbortController();
    request = current;
    panel.hidden = false;
    input.setAttribute('aria-expanded', 'true');
    status('กำลังค้นหาสินค้าของร้าน…');
    try {
      const query = new URLSearchParams({query: input.value.trim(), stock: 'all'});
      const response = await fetch('/api/mobile/products?' + query, {
        signal: current.signal, credentials: 'same-origin', cache: 'no-store',
      });
      if (!response.ok) throw new Error('Search unavailable');
      const rows = await response.json();
      if (request !== current || current.signal.aborted) return;
      if (!Array.isArray(rows)) throw new Error('Invalid search response');
      status(rows.length ? (input.value.trim() ? 'สินค้าที่ตรงกับคำค้น' : 'สินค้าของร้าน · เลือกหรือพิมพ์เพื่อค้นหา') : 'ไม่พบสินค้า ลองค้นด้วยชื่อ รหัส หรือบาร์โค้ด');
      options = rows.slice(0, 8).map((row, index) => {
        const option = document.createElement('a');
        option.id = 'kkSuggestion' + index;
        option.className = 'kk-suggestion';
        option.href = '/products?q=' + encodeURIComponent(row.code);
        option.setAttribute('role', 'option');
        option.setAttribute('aria-selected', 'false');
        option.tabIndex = -1;
        const title = document.createElement('strong');
        title.textContent = row.name;
        const detail = document.createElement('small');
        detail.textContent = [row.code, row.category, 'คงเหลือ ' + row.quantity].filter(Boolean).join(' · ');
        option.append(title, detail);
        panel.append(option);
        return option;
      });
    } catch (error) {
      if (error.name !== 'AbortError' && request === current) status('โหลดรายการไม่ได้ กด Enter เพื่อไปหน้าค้นหา');
    }
  }

  input.addEventListener('focus', suggest);
  input.addEventListener('click', () => { if (panel.hidden) suggest(); });
  input.addEventListener('input', () => {
    clearTimeout(timer);
    request?.abort();
    status('กำลังค้นหา…');
    timer = setTimeout(suggest, 180);
  });
  input.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); close(); return; }
    if (event.key === 'Enter' && !panel.hidden && active >= 0) {
      event.preventDefault();
      options[active].click();
      return;
    }
    if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return;
    event.preventDefault();
    if (panel.hidden) { suggest(); return; }
    if (!options.length) return;
    active = (active + (event.key === 'ArrowDown' ? 1 : -1) + options.length) % options.length;
    options.forEach((option, index) => option.setAttribute('aria-selected', String(index === active)));
    input.setAttribute('aria-activedescendant', options[active].id);
    options[active].scrollIntoView({block: 'nearest'});
  });
  document.addEventListener('pointerdown', event => { if (!form.contains(event.target)) close(); });
  form.addEventListener('focusout', () => setTimeout(() => { if (!form.contains(document.activeElement)) close(); }, 0));
  form.addEventListener('submit', close);
})();
