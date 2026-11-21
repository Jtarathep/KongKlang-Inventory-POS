package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class JournalService {
    private AccountingPeriodGuard periods;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setPeriods(AccountingPeriodGuard value){periods=value;}
    public void requireOpen(Long storeId,LocalDate date){if(periods!=null)periods.assertOpen(storeId,date);}
    private final StoreRepository stores;
    private final GlAccountRepository accounts;
    private final GlJournalRepository journals;
    private static final Map<String,String[]> CHART=Map.ofEntries(
        Map.entry("1100",new String[]{"เงินสดและบัญชีชำระเงิน","ASSET"}),
        Map.entry("1110",new String[]{"เงินฝากธนาคารรับโอน","ASSET"}),
        Map.entry("1120",new String[]{"ลูกหนี้บัตรรอรับเงิน","ASSET"}),
        Map.entry("1200",new String[]{"สินค้าคงเหลือ","ASSET"}),
        Map.entry("2100",new String[]{"เจ้าหนี้การค้า","LIABILITY"}),
        Map.entry("2110",new String[]{"สินค้ารับแล้วรอใบแจ้งหนี้","LIABILITY"}),
        Map.entry("2200",new String[]{"ภาษีขาย","LIABILITY"}),
        Map.entry("3100",new String[]{"ทุนสินค้าคงเหลือตั้งต้น","EQUITY"}),
        Map.entry("3200",new String[]{"กำไรสะสม","EQUITY"}),
        Map.entry("4000",new String[]{"รายได้จากการขาย","REVENUE"}),
        Map.entry("4010",new String[]{"รับคืนสินค้า","REVENUE"}),
        Map.entry("4100",new String[]{"ผลต่างสินค้าจากการตรวจนับเพิ่ม","REVENUE"}),
        Map.entry("5000",new String[]{"ต้นทุนขาย","EXPENSE"}),
        Map.entry("5100",new String[]{"ผลต่างสินค้าลดและตัดจำหน่าย","EXPENSE"}));

    public JournalService(StoreRepository stores,GlAccountRepository accounts,GlJournalRepository journals) {
        this.stores=stores; this.accounts=accounts; this.journals=journals;
    }

    public void post(Long storeId,String sourceType,Long sourceId,String reference,
                     String debitAccount,String creditAccount,BigDecimal amount) {
        requireOpen(storeId,LocalDate.now(ZoneId.of("Asia/Bangkok")));
        if(amount==null || amount.signum()<0) throw new IllegalArgumentException("ยอดบัญชีไม่ถูกต้อง");
        if(amount.signum()==0) return;
        BusinessMoney.amount(amount);
        if(!CHART.containsKey(debitAccount)||!CHART.containsKey(creditAccount)||debitAccount.equals(creditAccount))
            throw new IllegalArgumentException("บัญชีสำหรับลงรายการไม่ถูกต้อง");
        Store store=stores.lockById(storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        Optional<GlJournal> prior=journals.findByStoreIdAndSourceTypeAndSourceId(storeId,sourceType,sourceId);
        if(prior.isPresent()) {
            List<GlJournalLine> lines=prior.get().getItems();
            boolean matches=lines.size()==2
                && lines.stream().anyMatch(l->l.getAccount().getCode().equals(debitAccount)&&l.getDebit().compareTo(amount)==0)
                && lines.stream().anyMatch(l->l.getAccount().getCode().equals(creditAccount)&&l.getCredit().compareTo(amount)==0);
            if(!matches) throw new IllegalStateException("เอกสารนี้ลงบัญชีแล้วด้วยยอดที่ต่างกัน");
            return;
        }
        GlJournal journal=new GlJournal(); journal.setStore(store); journal.setSourceType(sourceType);
        journal.setSourceId(sourceId); journal.setReference(reference);
        journal.setPostedAt(LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
        addLine(journal,account(store,debitAccount),amount,BigDecimal.ZERO);
        addLine(journal,account(store,creditAccount),BigDecimal.ZERO,amount);
        BigDecimal debits=journal.getItems().stream().map(GlJournalLine::getDebit).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal credits=journal.getItems().stream().map(GlJournalLine::getCredit).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(debits.compareTo(credits)!=0) throw new IllegalStateException("รายการบัญชีไม่สมดุล");
        journals.save(journal);
    }

    public record Posting(String account,BigDecimal debit,BigDecimal credit){}
    /** Internal period-close/reopen path. No external endpoint accepts arbitrary postings. */
    public Long postClosingLines(Long storeId,String sourceType,Long sourceId,String reference,List<Posting> postings,LocalDateTime at){
        if(!Set.of("PERIOD_CLOSE","PERIOD_REOPEN").contains(sourceType)||postings==null||postings.isEmpty()||postings.size()>1000)throw new IllegalArgumentException("รายการปิดบัญชีไม่ถูกต้อง");
        Store store=stores.lockById(storeId).orElseThrow();
        GlJournal journal=new GlJournal();journal.setStore(store);journal.setSourceType(sourceType);journal.setSourceId(sourceId);journal.setReference(reference);journal.setPostedAt(at);
        BigDecimal debit=BigDecimal.ZERO,credit=BigDecimal.ZERO;Set<String> seen=new HashSet<>();
        for(Posting p:postings){
            if(p==null||!CHART.containsKey(p.account())||!seen.add(p.account())||p.debit()==null||p.credit()==null||p.debit().signum()<0||p.credit().signum()<0||p.debit().signum()>0&&p.credit().signum()>0||p.debit().signum()==0&&p.credit().signum()==0)throw new IllegalArgumentException("บรรทัดบัญชีไม่ถูกต้อง");
            BigDecimal d=p.debit().setScale(2,java.math.RoundingMode.UNNECESSARY),c=p.credit().setScale(2,java.math.RoundingMode.UNNECESSARY);
            if(d.signum()>0)BusinessMoney.amount(d);if(c.signum()>0)BusinessMoney.amount(c);
            debit=debit.add(d);credit=credit.add(c);addLine(journal,account(store,p.account()),d,c);
        }
        if(debit.signum()==0||debit.compareTo(credit)!=0)throw new IllegalArgumentException("รายการปิดบัญชีไม่สมดุล");
        var prior=journals.findByStoreIdAndSourceTypeAndSourceId(storeId,sourceType,sourceId);
        if(prior.isPresent())throw new IllegalStateException("เหตุการณ์นี้ลงบัญชีแล้ว");
        return journals.saveAndFlush(journal).getId();
    }

    private GlAccount account(Store store,String code) {
        return accounts.findByStoreIdAndCode(store.getId(),code).orElseGet(()->{
            GlAccount a=new GlAccount(); a.setStore(store); a.setCode(code);
            a.setName(CHART.get(code)[0]); a.setType(CHART.get(code)[1]); return accounts.save(a);
        });
    }
    private void addLine(GlJournal journal,GlAccount account,BigDecimal debit,BigDecimal credit) {
        GlJournalLine line=new GlJournalLine(); line.setJournal(journal); line.setAccount(account);
        line.setDebit(debit); line.setCredit(credit); journal.getItems().add(line);
    }
    @Transactional(readOnly=true)
    public List<JournalView> list(Long storeId) {
        return journals.findByStoreIdOrderByPostedAtDesc(storeId).stream().map(j->new JournalView(
            j.getId(),j.getSourceType(),j.getReference(),j.getPostedAt(),j.getItems().stream().map(l->
                new LineView(l.getAccount().getCode(),l.getAccount().getName(),l.getDebit(),l.getCredit())).toList())).toList();
    }
    @Transactional(readOnly=true)
    public List<BalanceView> trialBalance(Long storeId) {
        Map<String,BalanceView> balances=new TreeMap<>();
        for(JournalView j:list(storeId)) for(LineView l:j.lines()) {
            BalanceView prior=balances.getOrDefault(l.accountCode(),new BalanceView(l.accountCode(),l.accountName(),BigDecimal.ZERO,BigDecimal.ZERO));
            balances.put(l.accountCode(),new BalanceView(l.accountCode(),l.accountName(),prior.debit().add(l.debit()),prior.credit().add(l.credit())));
        }
        return balances.values().stream().map(b->{
            BigDecimal net=b.debit().subtract(b.credit());
            return new BalanceView(b.accountCode(),b.accountName(),net.max(BigDecimal.ZERO),net.negate().max(BigDecimal.ZERO));
        }).toList();
    }
    public record LineView(String accountCode,String accountName,BigDecimal debit,BigDecimal credit) {}
    public record JournalView(Long id,String sourceType,String reference,LocalDateTime postedAt,List<LineView> lines) {}
    public record BalanceView(String accountCode,String accountName,BigDecimal debit,BigDecimal credit) {}
}
