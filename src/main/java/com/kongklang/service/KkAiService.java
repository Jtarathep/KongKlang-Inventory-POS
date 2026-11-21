package com.kongklang.service;

import com.kongklang.entity.Product;
import com.kongklang.repository.ProductRepository;
import com.kongklang.repository.PurchaseOrderRepository;
import com.kongklang.repository.StockBatchRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class KkAiService {
    private final StoreAnalysisService analysis;
    private final OverviewService overview;
    private final ProductRepository products;
    private final StockBatchRepository batches;
    private final PurchaseOrderRepository orders;

    public KkAiService(StoreAnalysisService analysis, OverviewService overview,
                       ProductRepository products, StockBatchRepository batches, PurchaseOrderRepository orders) {
        this.analysis = analysis;
        this.overview = overview;
        this.products = products;
        this.batches = batches;
        this.orders = orders;
    }

    public record Answer(String title, String text, List<String> facts, String sourcePath) {}

    public Answer answer(Long storeId, String question, String code) {
        String q = question.toLowerCase(Locale.ROOT);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        if (q.contains("ราคา") || q.contains("ต้นทุน") || q.contains("เหลือกี่")) {
            return productAnswer(storeId, q, code);
        }
        if (q.contains("งบ")) {
            return new Answer("จัดซื้อตามงบ", "ตอนนี้ช่วยคัดรายการที่ควรเช็กก่อนสั่งเพิ่มได้ ส่วนการจัดจำนวนตามงบยังไม่รองรับ ลองถามว่า ‘ควรซื้ออะไรเพิ่ม’", List.of(), "/purchasing");
        }
        if (q.contains("หมดอายุ")) {
            var lots = batches.findAllByStoreId(storeId).stream()
                    .filter(b -> b.getRemainingQuantity() != null && b.getRemainingQuantity() > 0 && b.getExpiryDate() != null
                            && !b.getExpiryDate().isBefore(today) && !b.getExpiryDate().isAfter(today.plusDays(30)))
                    .sorted(java.util.Comparator.comparing(com.kongklang.entity.StockBatch::getExpiryDate)).limit(20)
                    .map(b -> b.getProductName() + " · ล็อต #" + b.getId() + " · เหลือ " + b.getRemainingQuantity() + " หน่วย · หมดอายุ " + b.getExpiryDate()).toList();
            return new Answer("ล็อตใกล้หมดอายุ", lots.isEmpty() ? "ไม่พบล็อตคงเหลือที่จะหมดอายุภายใน 30 วัน" : "ตรวจล็อตที่จะหมดอายุภายใน 30 วัน เรียงตามวันที่หมดอายุ แสดงสูงสุด 20 ล็อต", lots, "/stock");
        }
        if (q.contains("สั่ง") || q.contains("ซื้อเพิ่ม") || q.contains("ซื้ออะไร")) return reorder(storeId, today);
        if (q.contains("ยอดขาย") && (q.contains("7 วัน") || q.contains("ผิดปกติ"))) {
            var current = overview.report(storeId, today.minusDays(6), today);
            var prior = overview.report(storeId, today.minusDays(13), today.minusDays(7));
            return new Answer("ยอดขายสุทธิ 7 วัน", "เทียบกับ 7 วันก่อน ยอดขายสุทธิเปลี่ยนไป " + current.revenue().subtract(prior.revenue()).toPlainString() + " บาท",
                    List.of(current.start() + " ถึง " + today + " · " + current.revenue().toPlainString() + " บาท", prior.start() + " ถึง " + prior.end() + " · " + prior.revenue().toPlainString() + " บาท"), "/reports/overview");
        }
        String topic;
        if (q.contains("ขายช้า")) return new Answer("เลือกหัวข้อที่รองรับ", "การจัดอันดับสินค้าขายช้ายังอยู่ในแผน ลองดูยอดขายย้อนหลัง 7 วันหรือสินค้าขายดีได้ครับ", List.of(), "/reports/overview");
        else if (q.contains("ใกล้หมด") || q.contains("สต๊อกต่ำ")) topic = "low-stock";
        else if (q.contains("ขายดี")) topic = "best";
        else if (q.contains("ยอดขาย") || q.contains("แนวโน้ม")) topic = "sales";
        else return new Answer("ถามเรื่องร้านของคุณ", "ลองถาม ‘ควรซื้ออะไรเพิ่ม’, ‘สินค้าใกล้หมดอายุ’ หรือ ‘ยอดขายสุทธิ 7 วัน’ หากถามเรื่องราคา ให้ระบุรหัสสินค้าในช่องรหัสสินค้าด้วย", List.of(), null);

        var result = analysis.analyze(storeId, topic);
        var facts = result.items().stream().map(item -> item.name() + " · " + item.code() + " · " + item.quantity() + " หน่วย").toList();
        if (facts.isEmpty() && !topic.equals("sales")) facts = List.of("ไม่มีสินค้าที่เข้าเงื่อนไขในข้อมูลที่ตรวจ");
        return new Answer(result.topic(), result.summary(), facts, "/reports/overview");
    }

    private Answer reorder(Long storeId, LocalDate today) {
        var report = overview.report(storeId, today.minusDays(29), today);
        var incoming = new java.util.HashMap<String, Long>();
        for (var order : orders.findByStoreIdOrderByCreatedAtDesc(storeId)) {
            if (!java.util.Set.of("ORDERED", "PARTIALLY_RECEIVED").contains(order.getStatus())) continue;
            for (var item : order.getItems()) {
                incoming.merge(item.getProductCode(), (long) Math.max(0, item.getQuantity() - item.getReceivedQuantity()), Long::sum);
            }
        }
        var stock = new java.util.HashMap<String, OverviewService.StockRow>();
        report.stock().forEach(row -> stock.put(row.code(), row));
        var candidates = new java.util.ArrayList<String>();
        for (var sold : report.bestSellers()) {
            var row = stock.get(sold.code());
            if (row == null || !row.consistent() || sold.quantity() <= 0) continue;
            long ready = Math.max(0, row.quantity() - row.expired());
            long ordered = incoming.getOrDefault(sold.code(), 0L);
            long target = (long) Math.ceil(sold.quantity() * 7.0 / 30);
            if (target > ready + ordered) candidates.add(row.name() + " · พิจารณาสั่ง " + (target - ready - ordered)
                    + " หน่วย · พร้อมขาย " + ready + " · ค้างรับ " + ordered + " · ขายสุทธิ 30 วัน " + sold.quantity());
        }
        String text = "ประเมินสต๊อกให้พอขาย 7 วันจากสินค้าขายดีสูงสุด 20 รายการ หักล็อตหมดอายุและของที่สั่งแล้ว ควรเช็กวันส่งของก่อนสั่ง";
        if (candidates.isEmpty()) text += " ข้อมูลที่ตรวจไม่พบรายการที่ต้องเพิ่มตามเกณฑ์นี้";
        return new Answer("รายการที่ควรพิจารณาสั่งเพิ่ม", text, candidates, "/purchasing");
    }

    private Answer productAnswer(Long storeId, String question, String code) {
        if (code == null || code.isBlank()) return new Answer("ระบุสินค้า", "กรอกรหัสสินค้าในช่องรหัสสินค้า แล้วส่งคำถามอีกครั้ง", List.of(), "/products");
        Product product = products.findByStoreIdAndCode(storeId, code.trim()).orElse(null);
        if (product == null) return new Answer("ค้นหาสินค้า", "ไม่พบรหัสสินค้านี้ในร้านปัจจุบัน", List.of(), "/products");
        if (question.contains("เหลือกี่")) return new Answer(product.getName(), "คงเหลือตามทะเบียนสินค้า " + product.getQuantity() + " หน่วย รวมล็อตที่อาจหมดอายุ", List.of(), "/stock");
        var costs = batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId, product.getCode()).reversed().stream()
                .filter(batch -> batch.getPurchaseCostValue() != null && batch.getReceivedAt() != null).limit(2)
                .map(batch -> "ล็อตวันที่ " + batch.getReceivedAt().toLocalDate() + " · ต้นทุน " + batch.getPurchaseCostValue().toPlainString() + " บาทต่อหน่วย").toList();
        return new Answer(product.getName(), "ราคาขายปัจจุบัน " + (product.getPriceValue() == null ? "ยังไม่ได้ระบุ" : product.getPriceValue().toPlainString() + " บาทต่อหน่วย")
                + " ข้อมูลราคาตลาดยังไม่พอแนะนำให้ปรับราคา ให้ใช้ต้นทุนล็อตด้านล่างประกอบการตรวจสอบ", costs, "/stock");
    }
}
