package com.kongklang.service;

import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class AccountingPeriodService {
    private final StoreRepository stores;private final GlJournalRepository journalRepository;private final JournalService journals;private final JdbcTemplate jdbc;private final AuditTrail audit;
    public AccountingPeriodService(StoreRepository stores,GlJournalRepository journalRepository,JournalService journals,JdbcTemplate jdbc,AuditTrail audit){this.stores=stores;this.journalRepository=journalRepository;this.journals=journals;this.jdbc=jdbc;this.audit=audit;}
    public record Input(String month,long revision,String reason,boolean confirmed){}
    public record View(Long id,LocalDate month,String status,long revision){}
    @Transactional(readOnly=true) public List<View> list(Long storeId){return jdbc.query("select * from accounting_periods where store_id=? order by month_start desc",(r,n)->new View(r.getLong("id"),r.getDate("month_start").toLocalDate(),r.getString("status"),r.getLong("revision")),storeId);}
    private long insert(String sql,Object... args){var key=new GeneratedKeyHolder();jdbc.update(c->{var s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},key);return Objects.requireNonNull(key.getKey()).longValue();}
    private record Balance(long id,String code,String type,BigDecimal debit,BigDecimal credit){}
    private List<Balance> balances(Long store,LocalDateTime until){return jdbc.query("select a.id,a.code,a.type,coalesce(sum(l.debit),0) d,coalesce(sum(l.credit),0) c from gl_accounts a left join gl_journal_lines l on l.account_id=a.id left join gl_journals j on j.id=l.journal_id where a.store_id=? and (j.id is null or j.posted_at<?) group by a.id,a.code,a.type",(r,n)->new Balance(r.getLong(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5)),store,until);}
    private YearMonth validate(AccessService.Actor actor,Input input,boolean reopen){
        if(actor==null||!(reopen?Set.of("OWNER"):Set.of("OWNER","ADMIN")).contains(actor.role()))throw new IllegalArgumentException(reopen?"เปิดงวดใหม่ได้เฉพาะเจ้าของร้าน":"ไม่มีสิทธิ์ปิดงวด");
        if(input==null||!input.confirmed())throw new IllegalArgumentException("กรุณาตรวจสอบงบทดลองและยืนยันก่อนดำเนินการ");
        YearMonth month;try{month=YearMonth.parse(input.month());}catch(Exception e){throw new IllegalArgumentException("เดือนต้องอยู่ในรูป YYYY-MM");}
        if(month.isAfter(YearMonth.now(ZoneId.of("Asia/Bangkok")) )||month.getYear()<2000)throw new IllegalArgumentException("เลือกงวดตั้งแต่ปี 2000 ถึงเดือนปัจจุบัน");
        PurchasingService.text(input.reason(),1000,"เหตุผลการปิดหรือเปิดงวด");return month;
    }
    public View close(AccessService.Actor actor,Input input){
        YearMonth month=validate(actor,input,false);stores.lockById(actor.storeId()).orElseThrow();
        Optional<View> existing=list(actor.storeId()).stream().filter(p->p.month().equals(month.atDay(1))).findFirst();
        if(existing.isPresent()&&existing.get().status().equals("CLOSED"))return existing.get();
        if(existing.map(View::revision).orElse(0L)!=input.revision())throw new IllegalArgumentException("สถานะงวดเปลี่ยนแล้ว กรุณาโหลดข้อมูลใหม่");
        if(list(actor.storeId()).stream().anyMatch(p->p.status().equals("CLOSED")&&p.month().isAfter(month.atDay(1))))throw new IllegalArgumentException("มีงวดภายหลังปิดแล้ว");
        LocalDateTime cutoff=month.plusMonths(1).atDay(1).atStartOfDay();List<Balance> balances=balances(actor.storeId(),cutoff);
        BigDecimal debits=balances.stream().map(Balance::debit).reduce(BigDecimal.ZERO,BigDecimal::add),credits=balances.stream().map(Balance::credit).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(debits.compareTo(credits)!=0)throw new IllegalStateException("งบทดลองไม่สมดุล จึงปิดงวดไม่ได้");
        long id=existing.map(View::id).orElseGet(()->insert("insert into accounting_periods(store_id,month_start,status,revision) values(?,?,'CLOSED',0)",actor.storeId(),java.sql.Date.valueOf(month.atDay(1))));
        long event=insert("insert into accounting_period_events(period_id,performed_by_id,kind,reason,created_at) values(?,?,'CLOSE',?,?)",id,actor.userId(),input.reason().trim(),Timestamp.valueOf(PurchasingService.now()));
        List<JournalService.Posting> lines=new ArrayList<>();BigDecimal difference=BigDecimal.ZERO;
        for(Balance b:balances){
            jdbc.update("insert into accounting_period_balances(event_id,account_id,debit,credit) values(?,?,?,?)",event,b.id(),b.debit(),b.credit());
            if(Set.of("REVENUE","EXPENSE").contains(b.type())){
                BigDecimal net=b.debit().subtract(b.credit());if(net.signum()==0)continue;
                lines.add(new JournalService.Posting(b.code(),net.negate().max(BigDecimal.ZERO),net.max(BigDecimal.ZERO)));difference=difference.subtract(net);
            }
        }
        if(difference.signum()!=0)lines.add(new JournalService.Posting("3200",difference.negate().max(BigDecimal.ZERO),difference.max(BigDecimal.ZERO)));
        if(!lines.isEmpty()){Long journal=journals.postClosingLines(actor.storeId(),"PERIOD_CLOSE",event,"ปิดงวด "+month,lines,cutoff.minusNanos(1000));jdbc.update("update accounting_period_events set journal_id=? where id=?",journal,event);}
        jdbc.update("update accounting_periods set status='CLOSED',revision=revision+1 where id=?",id);audit.record(actor,"PERIOD_CLOSE",month.toString());return list(actor.storeId()).stream().filter(p->p.id()==id).findFirst().orElseThrow();
    }
    public View reopen(AccessService.Actor actor,Input input){
        YearMonth month=validate(actor,input,true);stores.lockById(actor.storeId()).orElseThrow();
        View period=list(actor.storeId()).stream().filter(p->p.month().equals(month.atDay(1))).findFirst().orElseThrow(()->new IllegalArgumentException("ไม่พบงวดที่ปิดแล้ว"));
        if(period.status().equals("REOPENED"))return period;
        if(period.revision()!=input.revision())throw new IllegalArgumentException("สถานะงวดเปลี่ยนแล้ว กรุณาโหลดข้อมูลใหม่");
        if(list(actor.storeId()).stream().anyMatch(p->p.status().equals("CLOSED")&&p.month().isAfter(month.atDay(1))))throw new IllegalArgumentException("ต้องเปิดงวดที่ปิดล่าสุดก่อน");
        Long previous=jdbc.queryForObject("select journal_id from accounting_period_events where period_id=? and kind='CLOSE' order by id desc limit 1",Long.class,period.id());
        long event=insert("insert into accounting_period_events(period_id,performed_by_id,kind,reason,created_at) values(?,?,'REOPEN',?,?)",period.id(),actor.userId(),input.reason().trim(),Timestamp.valueOf(PurchasingService.now()));
        if(previous!=null){var original=journalRepository.findById(previous).orElseThrow();List<JournalService.Posting> inverse=original.getItems().stream().map(l->new JournalService.Posting(l.getAccount().getCode(),l.getCredit(),l.getDebit())).toList();Long journal=journals.postClosingLines(actor.storeId(),"PERIOD_REOPEN",event,"เปิดงวด "+month,inverse,month.plusMonths(1).atDay(1).atStartOfDay().minusNanos(1000));jdbc.update("update accounting_period_events set journal_id=? where id=?",journal,event);}
        jdbc.update("update accounting_periods set status='REOPENED',revision=revision+1 where id=?",period.id());audit.record(actor,"PERIOD_REOPEN",month.toString());return list(actor.storeId()).stream().filter(p->p.id().equals(period.id())).findFirst().orElseThrow();
    }
}
