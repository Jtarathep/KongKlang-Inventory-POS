package com.kongklang.service;
import com.kongklang.repository.StoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

@Service
@Transactional
public class AccountingPeriodGuard {
    private final StoreRepository stores;private final JdbcTemplate jdbc;
    public AccountingPeriodGuard(StoreRepository stores,JdbcTemplate jdbc){this.stores=stores;this.jdbc=jdbc;}
    public void assertOpen(Long storeId,LocalDate date){
        if(storeId==null||date==null)throw new IllegalArgumentException("กรุณาระบุร้านและวันที่บัญชี");
        stores.lockById(storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        java.sql.Date last=jdbc.queryForObject("select max(last_day(month_start)) from accounting_periods where store_id=? and status='CLOSED'",java.sql.Date.class,storeId);
        if(last!=null&&!date.isAfter(last.toLocalDate()))throw new IllegalArgumentException("วันที่รายการอยู่ในงวดที่ปิดแล้ว ต้องเปิดงวดใหม่ก่อนทำรายการ");
    }
    public void assertToday(Long storeId){assertOpen(storeId,LocalDate.now(ZoneId.of("Asia/Bangkok")));}
}
