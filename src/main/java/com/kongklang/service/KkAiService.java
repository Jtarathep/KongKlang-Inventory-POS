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
        String q = KkAiLanguage.normalize(question);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Bangkok"));
        if (KkAiLanguage.has(q, "ลบ", "แก้ไข", "ปรับยอด", "บันทึก", "เพิ่มสินค้า"))
            return new Answer("ตรวจข้อมูลได้อย่างเดียว", "ผมช่วยค้นและอธิบายข้อมูลร้านได้ การเปลี่ยนข้อมูลให้ทำผ่านหน้ารายการและยืนยันด้วยตัวเองครับ", List.of(), "/products");
        if (q.contains("ราคา") || q.contains("ต้นทุน") || q.contains("เหลือกี่")) {
            return productAnswer(storeId, question, code);
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
        if (KkAiLanguage.has(q, "ยอดขาย", "กำไร", "ขายได้") && KkAiLanguage.has(q, "วันนี้", "เมื่อวาน", "เดือนนี้", "สัปดาห์นี้")) {
            LocalDate end = q.contains("เมื่อวาน") ? today.minusDays(1) : today;
            LocalDate start = q.contains("เดือนนี้") ? today.withDayOfMonth(1)
                    : q.contains("สัปดาห์นี้") ? today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) : end;
            var report = overview.report(storeId, start, end);
            return new Answer("ยอดขายของร้าน", "ช่วง " + start + " ถึง " + end + " (เวลาไทย)",
                    List.of("ยอดขายสุทธิไม่รวม VAT " + report.revenue().toPlainString() + " บาท",
                            "กำไรขั้นต้น " + report.grossProfit().toPlainString() + " บาท"), "/reports/overview");
        }
        String topic;
        if (q.contains("ขายช้า")) return new Answer("เลือกหัวข้อที่รองรับ", "การจัดอันดับสินค้าขายช้ายังอยู่ในแผน ลองดูยอดขายย้อนหลัง 7 วันหรือสินค้าขายดีได้ครับ", List.of(), "/reports/overview");
        else if (q.contains("ใกล้หมด") || q.contains("สต็อกต่ำ")) topic = "low-stock";
        else if (q.contains("ขายดี")) topic = "best";
        else if (q.contains("ยอดขาย") || q.contains("แนวโน้ม")) topic = "sales";
        else if (KkAiLanguage.has(q, "สินค้า", "สต็อก", "ในคลัง", "ของเหลือ", "มีอะไร", "คงเหลือ")) return inventory(storeId, q);
        else if (KkAiLanguage.has(q, "สวัสดี", "หวัดดี", "ทำอะไรได้", "ช่วยอะไร"))
            return new Answer("สวัสดีครับ", "ถามข้อมูลร้านได้เลย เช่น ในคลังมีอะไรบ้าง น้ำดื่มเหลือเท่าไหร่ หรือยอดขายวันนี้ ผมรองรับคำสะกดคลาดเคลื่อนบางแบบ หากไม่ชัดเจนจะถามเพิ่มครับ", List.of(), null);
        else {
            var matches = matchingProducts(storeId, q);
            if (!matches.isEmpty()) return productAnswer(storeId, q, code);
            return new Answer("ขอรายละเอียดเพิ่มนิดหนึ่ง", "หมายถึงสินค้า สต็อก ยอดขาย หรือการจัดซื้อครับ? ลองระบุชื่อสินค้าหรือช่วงเวลาเพิ่มเติม รุ่นไม่มีค่า API นี้ยังไม่ได้ใช้โมเดลสนทนาทั่วไป จึงอาจไม่เข้าใจทุกประโยค", List.of(), null);
        }

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
        if (code == null || code.isBlank()) {
            var matches = matchingProducts(storeId, question);
            if (matches.isEmpty()) return new Answer("ระบุสินค้า", "ยังระบุสินค้าไม่ได้ พิมพ์ชื่อหรือรหัสสินค้าในคำถามได้เลยครับ", List.of(), "/products");
            if (matches.size() > 1) return new Answer("หมายถึงสินค้าตัวไหนครับ?", "พบหลายรายการที่ใกล้เคียง กรุณาระบุรหัสของสินค้าที่ต้องการ", matches.stream().limit(20).map(p -> p.getName() + " · " + p.getCode()).toList(), "/products");
            code = matches.getFirst().getCode();
        }
        Product product = products.findByStoreIdAndCode(storeId, code.trim()).orElse(null);
        if (product == null) return new Answer("ค้นหาสินค้า", "ไม่พบรหัสสินค้านี้ในร้านปัจจุบัน", List.of(), "/products");
        if (!KkAiLanguage.has(KkAiLanguage.normalize(question), "ราคา", "ต้นทุน")) return new Answer(product.getName(), "รหัส " + product.getCode() + " · คงเหลือตามทะเบียนสินค้า " + product.getQuantity() + " หน่วย รวมล็อตที่อาจหมดอายุ", List.of(), "/stock");
        var costs = batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId, product.getCode()).reversed().stream()
                .filter(batch -> batch.getPurchaseCostValue() != null && batch.getReceivedAt() != null).limit(2)
                .map(batch -> "ล็อตวันที่ " + batch.getReceivedAt().toLocalDate() + " · ต้นทุน " + batch.getPurchaseCostValue().toPlainString() + " บาทต่อหน่วย").toList();
        return new Answer(product.getName(), "ราคาขายปัจจุบัน " + (product.getPriceValue() == null ? "ยังไม่ได้ระบุ" : product.getPriceValue().toPlainString() + " บาทต่อหน่วย")
                + " ข้อมูลราคาตลาดยังไม่พอแนะนำให้ปรับราคา ให้ใช้ต้นทุนล็อตด้านล่างประกอบการตรวจสอบ", costs, "/stock");
    }

    public Answer answer(Long storeId, String question, String code, String previousQuestion) {
        String q = KkAiLanguage.normalize(question);
        if (previousQuestion != null && KkAiLanguage.has(q, "แล้ว", "ล่ะ")
                && KkAiLanguage.has(q, "วันนี้", "เมื่อวาน", "เดือนนี้", "สัปดาห์นี้")
                && !KkAiLanguage.has(q, "สินค้า", "ราคา", "ต้นทุน", "สต็อก", "ยอดขาย")
                && KkAiLanguage.normalize(previousQuestion).contains("ยอดขาย"))
            return answer(storeId, "ยอดขาย " + question, code);
        // Re-resolve context against the CURRENT store. Never reuse facts or client-supplied answers.
        if ((code == null || code.isBlank()) && previousQuestion != null
                && KkAiLanguage.has(q, "แล้ว", "ตัวนี้", "อันนี้", "ตัวเดิม")
                && KkAiLanguage.has(q, "ราคา", "ต้นทุน", "เหลือกี่")) {
            var current = matchingProducts(storeId, q);
            var prior = matchingProducts(storeId, previousQuestion);
            if (current.isEmpty() && prior.size() == 1) code = prior.getFirst().getCode();
        }
        return answer(storeId, question, code);
    }

    private List<Product> matchingProducts(Long storeId, String question) {
        String q = KkAiLanguage.normalize(question);
        var catalog = products.findAllByStoreId(storeId);
        var exact = catalog.stream().filter(p -> q.equals(KkAiLanguage.normalize(p.getCode()))
                || java.util.regex.Pattern.compile("(?<![a-z0-9])" + java.util.regex.Pattern.quote(p.getCode().toLowerCase(Locale.ROOT)) + "(?![a-z0-9])")
                    .matcher(question.toLowerCase(Locale.ROOT)).find()
                || p.getBarcode() != null && !p.getBarcode().isBlank() && q.equals(p.getBarcode())).toList();
        if (!exact.isEmpty()) return exact;
        var names = catalog.stream().filter(p -> p.getName() != null && !p.getName().isBlank()
                && q.contains(KkAiLanguage.normalize(p.getName()))).toList();
        if (!names.isEmpty()) return names;
        String term = KkAiLanguage.productTerm(question);
        if (term.length() < 3 || term.length() > 100) return List.of();
        return catalog.stream().filter(p -> {
            String name = KkAiLanguage.normalize(p.getName());
            return name.contains(term) || KkAiLanguage.distance(term, name) <= (term.length() >= 8 ? 2 : 1);
        }).toList();
    }

    private Answer inventory(Long storeId, String question) {
        if (!matchingProducts(storeId, question).isEmpty()) return productAnswer(storeId, question, null);
        if (!KkAiLanguage.has(question, "มีอะไร", "ทั้งหมด", "รายการ", "สต็อก", "ในคลัง", "สินค้าหมด", "ของเหลือ", "คงเหลือ"))
            return new Answer("ยังไม่พบสินค้าที่ตรงคำถาม", "ลองระบุชื่อหรือรหัสสินค้าอีกครั้งครับ", List.of(), "/products");
        var catalog = products.findAllByStoreId(storeId).stream()
                .filter(p -> !Boolean.FALSE.equals(p.getActive()))
                .filter(p -> !question.contains("สินค้าหมด") || p.getQuantity() != null && p.getQuantity() <= 0)
                .sorted(java.util.Comparator.comparing(Product::getName)).toList();
        return new Answer("สินค้าในร้าน", catalog.isEmpty() ? "ไม่พบสินค้าในร้านปัจจุบันตามเงื่อนไขนี้"
                : "พบ " + catalog.size() + " รายการ แสดงสูงสุด 20 รายการ ยอดตามทะเบียนรวมล็อตที่อาจหมดอายุ ดูรายการทั้งหมดได้จากลิงก์ด้านล่าง",
                catalog.stream().limit(20).map(p -> p.getName() + " · " + p.getCode() + " · คงเหลือ " + p.getQuantity() + " หน่วย").toList(), "/stock");
    }
}
