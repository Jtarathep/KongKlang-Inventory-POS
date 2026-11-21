(() => {
  const launch = document.querySelector('#ai-launch');
  const mini = document.querySelector('#ai-mini');
  const full = document.querySelector('.ai-page .ai-chat');
  const chat = full || mini?.querySelector('.ai-chat');
  if (!chat || !launch) return;

  const log = chat.querySelector('.ai-log');
  const welcome = chat.querySelector('.ai-welcome');
  const form = chat.querySelector('form');
  const question = form.elements.question;
  const product = form.elements.productCode;
  const submit = form.querySelector('[type=submit]');
  const key = `kk.assistant.${chat.dataset.store}.${chat.dataset.user}.${chat.dataset.csrf}`;
  let history = [];
  let busy = false;
  try { history = JSON.parse(sessionStorage.getItem(key) || '[]'); } catch { history = []; }

  function append(kind, answer) {
    welcome.hidden = true;
    const message = document.createElement('article');
    message.className = 'ai-message';
    message.dataset.kind = kind;
    const title = document.createElement('strong');
    title.textContent = answer.title;
    if (kind === 'assistant') {
      const mark = document.createElement('img');
      mark.src = '/images/kk-ai-mark.svg';
      mark.alt = '';
      mark.className = 'ai-assistant-mark';
      message.append(mark);
    }
    message.append(title);
    const body = document.createElement('p');
    body.textContent = answer.text;
    message.append(body);
    if (answer.facts?.length) {
      const list = document.createElement('ul');
      for (const fact of answer.facts) {
        const item = document.createElement('li');
        item.textContent = fact;
        list.append(item);
      }
      message.append(list);
    }
    if (answer.suggestion) {
      const label = document.createElement('strong');
      label.textContent = 'คำแนะนำจาก AI';
      const suggestion = document.createElement('p');
      suggestion.textContent = answer.suggestion;
      message.append(label, suggestion);
    }
    if (['/reports/overview', '/stock', '/products', '/purchasing'].includes(answer.sourcePath)) {
      const source = document.createElement('a');
      source.href = answer.sourcePath;
      source.textContent = 'ดูข้อมูลในคงคลัง';
      message.append(source);
    }
    log.append(message);
    log.scrollTop = log.scrollHeight;
    return message;
  }

  function remember(kind, answer) {
    append(kind, answer);
    history = [...history, { kind, answer }].slice(-24);
    try { sessionStorage.setItem(key, JSON.stringify(history)); } catch { /* Chat works without storage. */ }
  }
  for (const entry of history) append(entry.kind, entry.answer);

  async function ask(text) {
    if (busy || !text.trim()) return;
    busy = true;
    submit.disabled = true;
    chat.querySelectorAll('[data-ask]').forEach(button => { button.disabled = true; });
    remember('user', { title: 'คุณ', text });
    const waiting = append('assistant', { title: 'KK AI', text: 'กำลังอ่านข้อมูลร้าน…' });
    try {
      const response = await fetch('/api/kk-ai/ask', {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-Token': chat.dataset.csrf },
        body: JSON.stringify({ question: text, productCode: product.value.trim() }),
        signal: AbortSignal.timeout(80000)
      });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const answer = await response.json();
      remember('assistant', answer);
      if (question.value.trim() === text) question.value = '';
    } catch {
      append('assistant', { title: 'ส่งคำถามไม่สำเร็จ', text: 'ลองส่งอีกครั้ง หากเซสชันหมดอายุให้เข้าสู่ระบบใหม่' });
    } finally {
      waiting.remove();
      busy = false;
      submit.disabled = false;
      chat.querySelectorAll('[data-ask]').forEach(button => { button.disabled = false; });
    }
  }

  function toggle(open) {
    mini.hidden = !open;
    launch.setAttribute('aria-expanded', String(open));
    if (open) { question.focus(); log.scrollTop = log.scrollHeight; }
    else launch.focus();
  }
  launch.addEventListener('click', () => toggle(mini.hidden));
  document.querySelector('#ai-close').addEventListener('click', () => toggle(false));
  mini.addEventListener('keydown', event => { if (event.key === 'Escape') toggle(false); });
  question.addEventListener('keydown', event => {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) return;
    event.preventDefault();
    if (!busy) form.requestSubmit();
  });
  form.addEventListener('submit', event => { event.preventDefault(); ask(question.value.trim()); });
  chat.querySelectorAll('[data-ask]').forEach(button => button.addEventListener('click', () => ask(button.dataset.ask)));
  if (full) {
    launch.hidden = true;
    const topic = new URLSearchParams(location.search).get('topic');
    const presets = { reorder: 'ควรซื้ออะไรเพิ่ม', low: 'สินค้าใกล้หมด', expiry: 'สินค้าใกล้หมดอายุ' };
    if (Object.hasOwn(presets, topic)) ask(presets[topic]);
  }
})();
