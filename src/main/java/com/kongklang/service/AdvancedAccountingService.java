package com.kongklang.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class AdvancedAccountingService {
    private final JdbcTemplate jdbc;
    public AdvancedAccountingService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record Account(String code,String name,String type,BigDecimal debit,BigDecimal credit){
        public BigDecimal value(){return Set.of("LIABILITY","EQUITY","REVENUE").contains(type)?credit.subtract(debit):debit.subtract(credit);}
    }
    public record Income(LocalDate start,LocalDate end,List<Account> accounts,BigDecimal revenue,BigDecimal expense,BigDecimal profit){}
    public record BalanceSheet(LocalDate asOf,List<Account> accounts,BigDecimal assets,BigDecimal liabilities,BigDecimal equity,BigDecimal unclosedProfit,BigDecimal difference){}
    public record Aging(Long id,String vendorNumber,String supplier,LocalDate dueDate,long overdueDays,BigDecimal balance,String bucket){}
    public record LedgerLine(Long id,String source,String reference,LocalDateTime at,BigDecimal debit,BigDecimal credit,BigDecimal balance){}
    public record Ledger(String account,BigDecimal opening,List<LedgerLine> lines,BigDecimal closing){}
    private void range(LocalDate start,LocalDate end){if(start==null||end==null||end.isBefore(start)||start.getYear()<2000||start.plusYears(5).isBefore(end))throw new IllegalArgumentException("เลือกช่วงวันที่ตั้งแต่ปี 2000 และไม่เกิน 5 ปี");}
    private List<Account> accounts(Long store,LocalDateTime start,LocalDateTime end,boolean operating){
        return jdbc.query("select a.code,a.name,a.type,sum(l.debit),sum(l.credit) from gl_journal_lines l join gl_journals j on j.id=l.journal_id join gl_accounts a on a.id=l.account_id where j.store_id=? and j.posted_at>=? and j.posted_at<?"+(operating?" and j.source_type not in ('PERIOD_CLOSE','PERIOD_REOPEN')":"")+" group by a.id,a.code,a.name,a.type order by a.code",(r,n)->new Account(r.getString(1),r.getString(2),r.getString(3),r.getBigDecimal(4),r.getBigDecimal(5)),store,start,end);
    }
    private BigDecimal sum(List<Account> accounts,String type){return accounts.stream().filter(a->a.type().equals(type)).map(Account::value).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public Income income(Long store,LocalDate start,LocalDate end){range(start,end);List<Account> all=accounts(store,start.atStartOfDay(),end.plusDays(1).atStartOfDay(),true).stream().filter(a->Set.of("REVENUE","EXPENSE").contains(a.type())).toList();BigDecimal revenue=sum(all,"REVENUE"),expense=sum(all,"EXPENSE");return new Income(start,end,all,revenue,expense,revenue.subtract(expense));}
    public BalanceSheet balanceSheet(Long store,LocalDate asOf){range(asOf,asOf);List<Account> all=accounts(store,LocalDate.of(1900,1,1).atStartOfDay(),asOf.plusDays(1).atStartOfDay(),false);BigDecimal assets=sum(all,"ASSET"),liabilities=sum(all,"LIABILITY"),equity=sum(all,"EQUITY"),profit=sum(all,"REVENUE").subtract(sum(all,"EXPENSE"));return new BalanceSheet(asOf,all,assets,liabilities,equity,profit,assets.subtract(liabilities).subtract(equity).subtract(profit));}
    /** As-of recognition follows recorded timestamps, matching the existing posting-date ledger. */
    public List<Aging> aging(Long store,LocalDate asOf){range(asOf,asOf);return jdbc.query("select i.id,i.vendor_number,s.name,i.due_date,i.amount-coalesce((select sum(p.amount) from ap_payments p where p.invoice_id=i.id and p.created_at<?),0) balance from ap_invoices i join suppliers s on s.id=i.supplier_id where i.store_id=? and i.created_at<? order by i.due_date,i.id",(r,n)->{LocalDate due=r.getDate(4).toLocalDate();long days=java.time.temporal.ChronoUnit.DAYS.between(due,asOf);String bucket=days<=0?"ยังไม่เกินกำหนด":days<=30?"1–30 วัน":days<=60?"31–60 วัน":days<=90?"61–90 วัน":"เกิน 90 วัน";return new Aging(r.getLong(1),r.getString(2),r.getString(3),due,Math.max(0,days),r.getBigDecimal(5),bucket);},asOf.plusDays(1).atStartOfDay(),store,asOf.plusDays(1).atStartOfDay()).stream().filter(a->a.balance().signum()>0).toList();}
    public Ledger ledger(Long store,String code,LocalDate start,LocalDate end){
        range(start,end);if(code==null||!code.matches("[0-9]{4}"))throw new IllegalArgumentException("รหัสบัญชีต้องเป็นตัวเลข 4 หลัก");
        BigDecimal opening=jdbc.queryForObject("select coalesce(sum(l.debit-l.credit),0) from gl_journal_lines l join gl_journals j on j.id=l.journal_id join gl_accounts a on a.id=l.account_id where j.store_id=? and a.code=? and j.posted_at<?",BigDecimal.class,store,code,start.atStartOfDay());
        var rows=jdbc.query("select j.id,j.source_type,j.reference,j.posted_at,l.debit,l.credit from gl_journal_lines l join gl_journals j on j.id=l.journal_id join gl_accounts a on a.id=l.account_id where j.store_id=? and a.code=? and j.posted_at>=? and j.posted_at<? order by j.posted_at,j.id,l.id",(r,n)->new LedgerLine(r.getLong(1),r.getString(2),r.getString(3),r.getTimestamp(4).toLocalDateTime(),r.getBigDecimal(5),r.getBigDecimal(6),BigDecimal.ZERO),store,code,start.atStartOfDay(),end.plusDays(1).atStartOfDay());
        List<LedgerLine> result=new ArrayList<>();BigDecimal balance=opening;for(var r:rows){balance=balance.add(r.debit()).subtract(r.credit());result.add(new LedgerLine(r.id(),r.source(),r.reference(),r.at(),r.debit(),r.credit(),balance));}return new Ledger(code,opening,List.copyOf(result),balance);
    }
}
