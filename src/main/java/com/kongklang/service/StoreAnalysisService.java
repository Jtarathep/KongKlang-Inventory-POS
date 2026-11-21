package com.kongklang.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class StoreAnalysisService {
    private final OverviewService overview;
    public StoreAnalysisService(OverviewService overview){this.overview=overview;}
    public record Item(String code,String name,long quantity,BigDecimal revenue){}
    public record Result(String source,String topic,String summary,List<Item> items){}
    public Result analyze(Long storeId,String topic){
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));var now=overview.report(storeId,today.minusDays(29),today);
        return switch(topic){
            case "low-stock" -> new Result("RULES","สินค้าใกล้หมด","แสดงสินค้าที่มียอดพร้อมขายไม่เกิน 5 หน่วย โดยหักล็อตหมดอายุออก",now.stock().stream().filter(x->x.consistent()&&x.quantity()-x.expired()<=5).sorted(Comparator.comparingLong(x->x.quantity()-x.expired())).limit(20).map(x->new Item(x.code(),x.name(),x.quantity()-x.expired(),null)).toList());
            case "expiry" -> new Result("RULES","สินค้าใกล้หมดอายุ","จำนวนหน่วยในล็อตที่ยังไม่หมดอายุและจะหมดอายุภายใน 30 วัน",now.stock().stream().filter(x->x.expiring()>0).sorted(Comparator.comparingLong(OverviewService.StockRow::expiring).reversed()).limit(20).map(x->new Item(x.code(),x.name(),x.expiring(),null)).toList());
            case "best" -> new Result("RULES","สินค้าขายดี","จัดตามจำนวนขายสุทธิหลังคืนสินค้าใน 30 วันล่าสุด",now.bestSellers().stream().filter(x->x.quantity()>0).map(x->new Item(x.code(),x.name(),x.quantity(),x.revenue())).toList());
            case "reorder" -> {var byCode=new HashMap<String,OverviewService.StockRow>();now.stock().forEach(x->byCode.put(x.code(),x));yield new Result("RULES","สินค้าที่ควรตรวจเพื่อสั่งเพิ่ม","สินค้าขายดีที่ยอดพร้อมขายต่ำกว่าประมาณการขาย 7 วันจากยอดสุทธิ 30 วัน ใช้ประกอบการตัดสินใจ ไม่สร้างใบสั่งซื้อ",now.bestSellers().stream().filter(x->x.quantity()>0&&byCode.containsKey(x.code())&&byCode.get(x.code()).consistent()&&byCode.get(x.code()).quantity()-byCode.get(x.code()).expired()<Math.ceil(x.quantity()*7.0/30)).map(x->new Item(x.code(),x.name(),byCode.get(x.code()).quantity()-byCode.get(x.code()).expired(),x.revenue())).toList());}
            case "sales" -> {var prior=overview.report(storeId,today.minusDays(59),today.minusDays(30));var change=now.revenue().subtract(prior.revenue());yield new Result("RULES","สรุปยอดขายและแนวโน้ม","ยอดขายสุทธิไม่รวม VAT 30 วันล่าสุด "+now.revenue().setScale(2).toPlainString()+" บาท เปลี่ยนจาก 30 วันก่อน "+change.setScale(2).toPlainString()+" บาท กำไรขั้นต้น "+now.grossProfit().setScale(2).toPlainString()+" บาท",List.of());}
            default -> throw new IllegalArgumentException("กรุณาเลือกหัวข้อการวิเคราะห์ที่รองรับ");
        };
    }
}
