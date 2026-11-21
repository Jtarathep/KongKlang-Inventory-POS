package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import com.kongklang.support.DecimalMoney;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/** All inventory writers must lock the product before reading its batches. */
@Service
@Transactional
public class StockLedgerService {
    private AccountingPeriodGuard periods;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setPeriods(AccountingPeriodGuard value){periods=value;}
    private void requireOpen(Long storeId){if(periods!=null)periods.assertToday(storeId);}
    private com.kongklang.repository.StoreRepository stores;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setStoreLocks(com.kongklang.repository.StoreRepository value){stores=value;}
    private final ProductRepository products;
    private final StockBatchRepository batches;
    private final StockTransactionRepository transactions;

    public StockLedgerService(ProductRepository products, StockBatchRepository batches,
                              StockTransactionRepository transactions) {
        this.products = products;
        this.batches = batches;
        this.transactions = transactions;
    }

    public Product lockProduct(String code, Long storeId) {
        if (storeId == null || code == null || code.isBlank()) {
            throw new IllegalArgumentException("กรุณาระบุร้านและรหัสสินค้า");
        }
        if(stores!=null)stores.lockById(storeId).orElseThrow(()->new IllegalArgumentException("ไม่พบร้าน"));
        return products.findForUpdateByStoreIdAndCode(storeId, code.trim())
                .orElseThrow(() -> new IllegalArgumentException("ไม่พบสินค้าในร้านปัจจุบัน"));
    }

    public Product receive(String code, int quantity, Double cost, Double sellingPrice,
                           LocalDate expiry, String note, Long storeId) {
        receiveBatch(code, quantity, cost, sellingPrice, expiry, note, storeId);
        return lockProduct(code, storeId);
    }

    public StockBatch receiveBatch(String code, int quantity, Double cost, Double sellingPrice,
                           LocalDate expiry, String note, Long storeId) {
        return receiveBatchValue(code, quantity, DecimalMoney.legacy(cost,4),
                DecimalMoney.legacy(sellingPrice,2), expiry, note, storeId);
    }

    public StockBatch receiveBatchValue(String code,int quantity,BigDecimal cost,BigDecimal sellingPrice,
                           LocalDate expiry,String note,Long storeId) {
        return receiveBatchValue(code,quantity,cost,sellingPrice,expiry,note,storeId,null);
    }

    public StockBatch receiveBatchValue(String code,int quantity,BigDecimal cost,BigDecimal sellingPrice,
                           LocalDate expiry,String note,Long storeId,BigDecimal receiptValue) {
        requireOpen(storeId);
        if (quantity <= 0) throw new IllegalArgumentException("จำนวนรับเข้าต้องมากกว่า 0");
        Product product = lockProduct(code, storeId);
        readConsistentBatches(product);
        DecimalMoney.required(DecimalMoney.normalize(cost,4), "ต้นทุนที่ถูกต้อง");
        if (sellingPrice != null) DecimalMoney.normalize(sellingPrice,2);
        if (expiry != null && expiry.isBefore(today())) {
            throw new IllegalArgumentException("ไม่สามารถรับสินค้าที่หมดอายุแล้ว");
        }
        int balance = Math.addExact(product.getQuantity(), quantity);
        // A receipt always creates a separate batch, even when cost and expiry match.
        StockBatch receivedBatch = addBatch(product, quantity, cost, expiry, note, receiptValue);
        product.setQuantity(balance);
        product.setCostPriceValue(cost);
        if (sellingPrice != null) product.setPriceValue(sellingPrice);
        products.save(product);
        record(product, "IN", quantity, note);
        return receivedBatch;
    }

    public Consumption consume(String code, int quantity, String note, Long storeId,
                               boolean allowExpired) {
        requireOpen(storeId);
        if (quantity <= 0) throw new IllegalArgumentException("จำนวนจ่ายออกต้องมากกว่า 0");
        Product product = lockProduct(code, storeId);
        List<StockBatch> available = readConsistentBatches(product).stream()
                .filter(b -> allowExpired || b.getExpiryDate() == null || !b.getExpiryDate().isBefore(today()))
                .filter(b -> b.getRemainingQuantity() > 0).sorted(fefo()).toList();
        long sellable = available.stream().mapToLong(StockBatch::getRemainingQuantity).sum();
        if (sellable < quantity) throw new IllegalArgumentException("สินค้าที่จ่ายออกได้ไม่เพียงพอ กรุณาตรวจสอบล็อตและวันหมดอายุ");
        int remaining = quantity;
        BigDecimal cost = BigDecimal.ZERO;
        BigDecimal bookCost = BigDecimal.ZERO;
        List<Allocation> allocations = new ArrayList<>();
        // Validate all costs before changing any batch.
        for (StockBatch batch : available) DecimalMoney.required(DecimalMoney.normalize(batch.getPurchaseCostValue(),4), "ต้นทุนล็อตที่ถูกต้อง");
        for (StockBatch batch : available) {
            if (remaining == 0) break;
            int take = Math.min(remaining, batch.getRemainingQuantity());
            BigDecimal actual = batch.getPurchaseCostValue().multiply(BigDecimal.valueOf(take));
            cost = cost.add(actual);
            BigDecimal newValue=remainingBookValue(batch,batch.getRemainingQuantity()-take);
            BigDecimal booked=batch.getRemainingValue().subtract(newValue);
            if(booked.signum()<0)throw new IllegalStateException("มูลค่าล็อตไม่สอดคล้องกับจำนวนคงเหลือ");
            bookCost=bookCost.add(booked);
            allocations.add(new Allocation(batch.getId(), take, batch.getPurchaseCostValue(), actual,booked));
            batch.setRemainingValue(newValue);
            batch.setRemainingQuantity(batch.getRemainingQuantity() - take);
            batches.save(batch);
            remaining -= take;
        }
        product.setQuantity(product.getQuantity() - quantity);
        products.save(product);
        record(product, "OUT", quantity, note);
        return new Consumption(product.getQuantity(), cost, List.copyOf(allocations),bookCost);
    }

    public void adjust(String code, int newQuantity, String note, Long storeId) {
        adjust(code,newQuantity,note,storeId,null,null);
    }

    /** A return creates its own traceable lot, retaining original expiry and exact book value. */
    public StockBatch receiveReturn(String code,int quantity,BigDecimal unitCost,BigDecimal bookValue,LocalDate expiry,String note,Long storeId){
        requireOpen(storeId);
        if(quantity<=0||bookValue==null||bookValue.signum()<0)throw new IllegalArgumentException("จำนวนหรือมูลค่าคืนสินค้าไม่ถูกต้อง");
        BusinessMoney.cost(unitCost);
        if(expiry!=null&&expiry.isBefore(today()))throw new IllegalArgumentException("สินค้าที่หมดอายุแล้วรับคืนได้เฉพาะแบบไม่เข้าสต๊อก");
        Product product=lockProduct(code,storeId);readConsistentBatches(product);
        StockBatch batch=new StockBatch();batch.setStore(product.getStore());batch.setProductCode(product.getCode());batch.setProductName(product.getName());
        batch.setQuantity(quantity);batch.setRemainingQuantity(quantity);batch.setPurchaseCostValue(unitCost);
        batch.setReceivedValue(bookValue);batch.setRemainingValue(bookValue);batch.setExpiryDate(expiry);batch.setReceivedAt(LocalDateTime.now(ZoneId.of("Asia/Bangkok")));batch.setNote(note);
        batches.saveAndFlush(batch);product.setQuantity(Math.addExact(product.getQuantity(),quantity));products.save(product);record(product,"IN",quantity,note);return batch;
    }

    public void adjust(String code,int newQuantity,String note,Long storeId,BigDecimal countedCost,LocalDate countedExpiry) {
        requireOpen(storeId);
        if (newQuantity < 0) throw new IllegalArgumentException("จำนวนสินค้าต้องไม่ติดลบ");
        if (note == null || note.isBlank()) throw new IllegalArgumentException("กรุณาระบุเหตุผลในการปรับสต็อก");
        Product product = lockProduct(code, storeId);
        List<StockBatch> current = readConsistentBatches(product);
        int difference = Math.subtractExact(newQuantity, product.getQuantity());
        if (difference == 0) return;
        if (difference > 0) {
            BigDecimal cost=DecimalMoney.required(countedCost==null?product.getCostPriceValue():DecimalMoney.normalize(countedCost,4), "ต้นทุนสินค้าก่อนปรับเพิ่มสต็อก");
            addBatch(product, difference, cost, countedExpiry, "ปรับเพิ่ม: " + note,null);
        } else {
            int remaining = -difference;
            // Stock counts may remove expired/damaged inventory as well.
            for (StockBatch batch : current.stream().sorted(fefo()).toList()) {
                int take = Math.min(remaining, batch.getRemainingQuantity());
                if (take == 0) continue;
                batch.setRemainingValue(remainingBookValue(batch,batch.getRemainingQuantity()-take));
                batch.setRemainingQuantity(batch.getRemainingQuantity() - take);
                batches.save(batch);
                remaining -= take;
                if (remaining == 0) break;
            }
        }
        product.setQuantity(newQuantity);
        products.save(product);
        record(product, "ADJUST", difference, note.trim());
    }

    public Snapshot snapshot(String code,Long storeId) {
        Product p=lockProduct(code,storeId);
        List<BatchSnapshot> lots=readConsistentBatches(p).stream().map(b->new BatchSnapshot(
            b.getId(),b.getRemainingQuantity(),b.getRemainingValue(),b.getPurchaseCostValue())).toList();
        return new Snapshot(p.getQuantity(),lots.stream().map(BatchSnapshot::value).reduce(BigDecimal.ZERO,BigDecimal::add),lots);
    }
    public record BatchSnapshot(Long id,int quantity,BigDecimal value,BigDecimal unitCost) {}
    public record Snapshot(int quantity,BigDecimal value,List<BatchSnapshot> batches) {}

    private List<StockBatch> readConsistentBatches(Product product) {
        List<StockBatch> result = batches.findByStoreIdAndProductCodeOrderByReceivedAtAsc(
                product.getStore().getId(), product.getCode());
        long total = 0;
        for (StockBatch batch : result) {
            if (batch.getRemainingQuantity() == null || batch.getRemainingQuantity() < 0
                    || batch.getQuantity() == null || batch.getRemainingQuantity() > batch.getQuantity()) {
                throw new IllegalStateException("ข้อมูลล็อตไม่ถูกต้อง กรุณาตรวจสอบก่อนทำรายการ");
            }
            total += batch.getRemainingQuantity();
            if(batch.getReceivedValue()==null||batch.getRemainingValue()==null||batch.getReceivedValue().signum()<0
                    ||batch.getQuantity()<=0||batch.getRemainingValue().compareTo(remainingBookValue(batch,batch.getRemainingQuantity()))!=0)
                throw new IllegalStateException("มูลค่าล็อตไม่ตรงกับยอดคงเหลือ กรุณาตรวจสอบก่อนทำรายการ");
        }
        if (product.getQuantity() == null || product.getQuantity() < 0 || total != product.getQuantity()) {
            throw new IllegalStateException("ยอดสินค้า " + product.getCode()
                    + " ไม่ตรงกับล็อต กรุณาตรวจสอบยอดตั้งต้นก่อนทำรายการ");
        }
        return result;
    }

    private StockBatch addBatch(Product p, int quantity, BigDecimal cost, LocalDate expiry, String note,BigDecimal receiptValue) {
        StockBatch batch = new StockBatch();
        batch.setStore(p.getStore());
        batch.setProductCode(p.getCode());
        batch.setProductName(p.getName());
        batch.setQuantity(quantity);
        batch.setRemainingQuantity(quantity);
        batch.setPurchaseCostValue(cost);
        BigDecimal rounded=DecimalMoney.cents(cost.multiply(BigDecimal.valueOf(quantity)));
        BigDecimal value=receiptValue==null?rounded:DecimalMoney.normalize(receiptValue,2);
        if(value.subtract(rounded).abs().compareTo(new BigDecimal("0.01"))>0)
            throw new IllegalArgumentException("มูลค่ารับสินค้าไม่สัมพันธ์กับต้นทุนล็อต");
        batch.setReceivedValue(value);batch.setRemainingValue(value);
        batch.setExpiryDate(expiry);
        batch.setReceivedAt(LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
        batch.setNote(note);
        return batches.save(batch);
    }

    private void record(Product p, String type, int quantity, String note) {
        StockTransaction tx = new StockTransaction();
        tx.setStore(p.getStore());
        tx.setProductCode(p.getCode());
        tx.setProductName(p.getName());
        tx.setType(type);
        tx.setQuantity(quantity);
        tx.setBalance(p.getQuantity());
        tx.setNote(note);
        tx.setCreatedAt(LocalDateTime.now(ZoneId.of("Asia/Bangkok")));
        transactions.save(tx);
    }

    private static Comparator<StockBatch> fefo() {
        return Comparator.comparing(StockBatch::getExpiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockBatch::getReceivedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StockBatch::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Bangkok")); }

    private BigDecimal remainingBookValue(StockBatch batch,int remaining) {
        BigDecimal consumed=batch.getReceivedValue().multiply(BigDecimal.valueOf(batch.getQuantity()-remaining))
            .divide(BigDecimal.valueOf(batch.getQuantity()),2,java.math.RoundingMode.HALF_UP);
        return batch.getReceivedValue().subtract(consumed);
    }
    public record Allocation(Long batchId,int quantity,BigDecimal unitCost,BigDecimal actualCost,BigDecimal bookCost) {
        public Allocation(Long id,int quantity,BigDecimal cost,BigDecimal actual){this(id,quantity,cost,actual,DecimalMoney.cents(actual));}
    }
    public record Consumption(int balance, BigDecimal cost, List<Allocation> allocations,BigDecimal bookCost) {
        public Consumption(int balance,BigDecimal cost,List<Allocation> allocations){this(balance,cost,allocations,allocations.stream().map(Allocation::bookCost).reduce(BigDecimal.ZERO,BigDecimal::add));}
    }
}
