window.kongklangScanBarcode = async function () {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    return { error: 'เปิดกล้องผ่าน HTTPS หรือ localhost บนอุปกรณ์ที่กำลังใช้งาน หรือกรอกรหัสสินค้าด้วยตัวเอง' };
  }
  if (!window.ZXingBrowser) return { error: 'โหลดตัวอ่านบาร์โค้ดไม่ได้ กรุณาโหลดหน้าใหม่' };

  const dialog = document.createElement('dialog');
  dialog.className = 'barcode-camera';
  dialog.setAttribute('aria-labelledby', 'barcode-title');
  dialog.innerHTML = `<h2 id="barcode-title">สแกนบาร์โค้ดสินค้า</h2>
    <div class="barcode-preview"><video autoplay muted playsinline></video><span aria-hidden="true"></span></div>
    <p role="status">กำลังเปิดกล้อง…</p>
    <form method="dialog"><button autofocus>ปิดกล้อง</button></form>`;
  const video = dialog.querySelector('video');
  const status = dialog.querySelector('[role=status]');
  document.body.append(dialog);
  dialog.showModal();

  return new Promise(resolve => {
    let stream, controls, result, feedbackTimer;
    const stop = () => {
      controls?.stop();
      stream?.getTracks().forEach(track => track.stop());
    };
    const hide = () => { if (document.hidden) dialog.close(); };
    dialog.addEventListener('close', () => {
      clearTimeout(feedbackTimer);
      stop();
      document.removeEventListener('visibilitychange', hide);
      dialog.remove();
      resolve(result ?? {});
    }, { once: true });
    document.addEventListener('visibilitychange', hide);

    (async () => {
      try {
        stream = await navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 } }
        });
        if (!dialog.open) { stop(); return; }
        const reader = new ZXingBrowser.BrowserMultiFormatReader();
        controls = await reader.decodeFromStream(stream, video, (scan, _, active) => {
          if (!scan || !dialog.open || result) return;
          result = { value: scan.getText() };
          active.stop();
          stream?.getTracks().forEach(track => track.stop());
          dialog.classList.add('barcode-found');
          status.textContent = 'อ่านบาร์โค้ดแล้ว กำลังค้นหาสินค้า';
          if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) dialog.close();
          else feedbackTimer = setTimeout(() => dialog.close(), 180);
        });
        if (!dialog.open) { stop(); return; }
        if (!result) status.textContent = 'ถือบาร์โค้ดให้ชัดและอยู่กลางกรอบ';
      } catch (error) {
        stop();
        if (!dialog.open) return;
        status.textContent = error.name === 'NotAllowedError'
          ? 'เบราว์เซอร์ยังไม่ได้รับอนุญาตให้ใช้กล้อง เปิดสิทธิ์กล้องหรือกรอกรหัสสินค้าด้วยตัวเอง'
          : 'เปิดกล้องไม่ได้ ลองปิดแอปที่ใช้กล้องอยู่ หรือกรอกรหัสสินค้าด้วยตัวเอง';
      }
    })();
  });
};
