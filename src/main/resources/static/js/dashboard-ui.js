(() => {
  'use strict';
  const draw=()=>{
    const canvas=document.getElementById('salesChart');
    if(canvas&&typeof Chart!=='undefined'){
      const rows=Array.isArray(window.kkDashboardRows)?window.kkDashboardRows:[];
      if(matchMedia('(prefers-reduced-motion: reduce)').matches)Chart.defaults.animation=false;
      Chart.defaults.font.family='Noto Sans Thai, Segoe UI, sans-serif';Chart.defaults.font.size=10;Chart.defaults.color='#687990';
      const label=value=>{const text=String(value||'');const parts=text.split('-');return parts.length===3?`${Number(parts[2])}/${Number(parts[1])}`:text;};
      window.kongklangSalesChart=new Chart(canvas,{
        type:'bar',data:{labels:rows.map(r=>label(r[0])),datasets:[
          {label:'ยอดขาย',data:rows.map(r=>Number(r[1])||0),backgroundColor:'#2563eb',borderRadius:4,maxBarThickness:22},
          {label:'ต้นทุนขาย',data:rows.map(r=>Number(r[2])||0),backgroundColor:'#c8d3e4',borderRadius:4,maxBarThickness:22}
        ]},options:{responsive:true,maintainAspectRatio:false,animation:false,interaction:{intersect:false,mode:'index'},plugins:{legend:{display:false},tooltip:{backgroundColor:'#17243b',padding:12,cornerRadius:7,callbacks:{label:context=>`${context.dataset.label}: ${Number(context.parsed.y).toLocaleString('th-TH',{minimumFractionDigits:2,maximumFractionDigits:2})} บาท`}}},scales:{x:{grid:{display:false},border:{display:false}},y:{beginAtZero:true,border:{display:false},grid:{color:'#edf1f7'},ticks:{padding:8,maxTicksLimit:5}}}}
      });
    }
    
  };
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',draw);else draw();
})();

// Refresh all store-scoped dashboard regions, preserving scroll and the live chart instance.
(() => {
 const sheet=document.getElementById('dashboard-content'),status=document.getElementById('kkLiveStatus');if(!sheet)return;
 let timer,busy=false,controller,stopped=false;
 const ids=['live-ai','live-stock-metrics','live-sales-metrics','live-stock-alerts','live-transactions'];
 const chartData=doc=>JSON.parse(doc.getElementById('kk-chart-data').textContent.trim().replace(/;$/,''));
 let lastChart=JSON.stringify(chartData(document));
 async function refresh(){
  if(busy||document.hidden||stopped)return;busy=true;controller=new AbortController();const timeout=setTimeout(()=>controller.abort(),10000);
  try {
   const response=await fetch('/?dashboardLive=1',{credentials:'same-origin',cache:'no-store',signal:controller.signal,headers:{'X-Requested-With':'XMLHttpRequest'}});
   if(response.redirected||[401,403].includes(response.status)){stopped=true;status.textContent='เซสชันเปลี่ยน กรุณาเข้าสู่ระบบใหม่';return;}
   if(!response.ok)throw Error('connection');
   const doc=new DOMParser().parseFromString(await response.text(),'text/html'),incoming=doc.getElementById('dashboard-content');
   if(!incoming||incoming.dataset.store!==sheet.dataset.store||incoming.dataset.user!==sheet.dataset.user){stopped=true;status.textContent='บัญชีหรือร้านเปลี่ยน กรุณาเปิดหน้านี้ใหม่';return;}
   for(const id of ids){const current=document.getElementById(id),next=doc.getElementById(id);if(current&&next&&current.innerHTML!==next.innerHTML){const focused=current.contains(document.activeElement);if(!focused)current.replaceChildren(...Array.from(next.childNodes));}}
   const rows=chartData(doc),encoded=JSON.stringify(rows),chart=window.kongklangSalesChart;
   if(chart&&encoded!==lastChart){chart.data.labels=rows.map(r=>{const d=String(r[0]).split('-');return `${Number(d[2])}/${Number(d[1])}`});chart.data.datasets[0].data=rows.map(r=>Number(r[1])||0);chart.data.datasets[1].data=rows.map(r=>Number(r[2])||0);chart.update('none');lastChart=encoded;}
   status.textContent='อัปเดตล่าสุด '+new Date().toLocaleTimeString('th-TH',{hour:'2-digit',minute:'2-digit',second:'2-digit'});status.dataset.state='online';
  }catch(e){if(!document.hidden){status.textContent='เชื่อมต่อไม่ได้ · แสดงข้อมูลล่าสุดที่ได้รับ';status.dataset.state='offline';}}
  finally{clearTimeout(timeout);busy=false;clearTimeout(timer);if(!document.hidden&&!stopped)timer=setTimeout(refresh,4000);}
 }
 document.addEventListener('visibilitychange',()=>{clearTimeout(timer);if(document.hidden)controller?.abort();else refresh();});
 window.addEventListener('online',refresh);window.addEventListener('pagehide',()=>{clearTimeout(timer);controller?.abort();});window.addEventListener('pageshow',()=>{clearTimeout(timer);timer=setTimeout(refresh,1000);});
 timer=setTimeout(refresh,1000);
})();
