package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import com.kongklang.support.DecimalMoney;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class SaleService {
    private SalesAdjustmentReader adjustments;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAdjustments(SalesAdjustmentReader value){adjustments=value;}
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}

    private final SaleRepository sales;
    private final SaleItemRepository items;
    private final ProductRepository products;
    private final StoreRepository stores;
    private final StockLedgerService ledger;
    private final SaleAllocationRepository allocations;
    private final StockBatchRepository batches;
    private final JournalService journals;

    public SaleService(SaleRepository sales,SaleItemRepository items,ProductRepository products,
            StoreRepository stores,StockLedgerService ledger,SaleAllocationRepository allocations,
            StockBatchRepository batches,JournalService journals) {
        this.sales=sales;this.items=items;this.products=products;this.stores=stores;
        this.ledger=ledger;this.allocations=allocations;this.batches=batches;this.journals=journals;
    }
    private void requireStore(Long storeId) {if(storeId==null)throw new IllegalArgumentException("กรุณาระบุร้านจากบัญชีผู้ใช้");}
    private IllegalStateException scoped() {return new IllegalStateException("ต้องระบุร้านและผู้ทำรายการอย่างชัดเจน");}
    private LocalDate today(){return LocalDate.now(ZoneId.of("Asia/Bangkok"));}

    @Transactional
    public Sale checkout(String requestKey,List<String> codes,List<Integer> quantities,AccessService.Actor actor) {
        return checkoutInternal(requestKey,codes,quantities,actor,null);
    }
    @Transactional
    public Sale checkout(String requestKey,List<String> codes,List<Integer> quantities,AccessService.Actor actor,CheckoutTerms terms) {
        if(terms==null||terms.taxRevision()==null)throw new IllegalArgumentException("กรุณาตรวจสอบยอดและการตั้งค่าภาษีก่อนยืนยัน");
        return checkoutInternal(requestKey,codes,quantities,actor,terms);
    }
    private Sale checkoutInternal(String requestKey,List<String> codes,List<Integer> quantities,AccessService.Actor actor,CheckoutTerms terms) {
        if(actor==null||actor.userId()==null||actor.storeId()==null||actor.role()==null||!Set.of("OWNER","ADMIN","SALES","STAFF").contains(actor.role()))
            throw new IllegalArgumentException("ไม่มีสิทธิ์ขายสินค้า");
        String key=PurchasingService.text(requestKey,100,"รหัสป้องกันขายซ้ำ");
        if(codes==null||quantities==null||codes.isEmpty()||codes.size()!=quantities.size()||codes.size()>200)
            throw new IllegalArgumentException("กรุณาระบุสินค้าที่ถูกต้อง 1 ถึง 200 รายการ");
        Map<String,Integer> merged=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(int i=0;i<codes.size();i++) {
            String code=PurchasingService.text(codes.get(i),255,"รหัสสินค้า");Integer quantity=quantities.get(i);
            if(quantity==null||quantity<=0)throw new IllegalArgumentException("จำนวนขายต้องมากกว่า 0");
            merged.merge(code,quantity,Math::addExact);
        }
        String fingerprint=terms==null?fingerprint(actor.userId(),merged):fingerprint(actor.userId(),merged,terms);
        Optional<Sale> prior=sales.findByStoreIdAndRequestKey(actor.storeId(),key);
        if(prior.isPresent()) {
            if(!Objects.equals(prior.get().getRequestFingerprint(),fingerprint))throw new IllegalArgumentException("รหัสขายซ้ำถูกใช้กับรายการอื่นแล้ว");
            return prior.get();
        }
        Store store=stores.lockById(actor.storeId()).filter(s->Boolean.TRUE.equals(s.getActive()))
            .orElseThrow(()->new IllegalArgumentException("ไม่พบร้านที่เปิดใช้งาน"));
        if(terms!=null&&terms.taxRevision()!=store.getTaxRevision())throw new IllegalArgumentException("การตั้งค่าภาษีเปลี่ยนแล้ว กรุณาตรวจสอบยอดใหม่");
        if(terms!=null&&terms.documentType()==CheckoutTerms.DocumentType.TAX_INVOICE && (!store.isVatRegistered()||store.getTaxMode().equals("NONE")))
            throw new IllegalArgumentException("ร้านยังไม่พร้อมออกใบกำกับภาษี");
        Sale sale=new Sale();sale.setStore(store);sale.setSoldBy(PurchasingService.user(actor.userId()));
        sale.setRequestKey(key);sale.setRequestFingerprint(fingerprint);sale.setSaleNumber(PurchasingService.number("S"));
        sale.setPaymentMethod("CASH");sale.setPaymentStatus("PAID");sale.setTotalAmountValue(BigDecimal.ZERO);sale.setTotalCost(BigDecimal.ZERO);
        // Flush the unique request key before touching stock. A concurrent duplicate rolls back before consumption.
        sale=sales.saveAndFlush(sale);
        BigDecimal financialCost=BigDecimal.ZERO;
        List<Product> lockedProducts=new ArrayList<>();List<SalePricing.Input> pricingInput=new ArrayList<>();
        for(var entry:merged.entrySet()) {
            Product product=ledger.lockProduct(entry.getKey(),actor.storeId());
            if(!Boolean.TRUE.equals(product.getActive()))throw new IllegalArgumentException("สินค้านี้ปิดการขายแล้ว");
            BigDecimal price=DecimalMoney.required(product.getPriceValue(),"ราคาขายสินค้า");
            lockedProducts.add(product);pricingInput.add(new SalePricing.Input(product.getCode(),entry.getValue(),price));
        }
        SalePricing.Quote pricing=SalePricing.quote(pricingInput,StoreTaxService.rule(store),terms==null?SalePricing.Discount.none():terms.discount());
        if(terms!=null&&terms.expectedTotal().compareTo(pricing.payable())!=0)throw new IllegalArgumentException("ราคาหรือยอดชำระเปลี่ยนแล้ว กรุณาตรวจสอบยอดใหม่");
        for(int i=0;i<lockedProducts.size();i++) {
            Product product=lockedProducts.get(i);SalePricing.Line line=pricing.lines().get(i);
            StockLedgerService.Consumption used=ledger.consume(product.getCode(),line.quantity(),"ขายสินค้า "+sale.getSaleNumber(),actor.storeId(),false);
            BigDecimal cost=used.bookCost();
            SaleItem item=new SaleItem();item.setSale(sale);item.setProductCode(product.getCode());item.setProductName(product.getName());
            item.setQuantity(line.quantity());item.setPriceValue(line.unitPrice());item.setSubtotalValue(line.payable());
            item.setListedAmount(line.listedAmount());item.setDiscountAmount(line.discount());item.setNetAmount(line.net());item.setTaxAmount(line.tax());
            item.setCostValue(cost);item.setActualCost(used.cost());items.saveAndFlush(item);
            int allocated=0;BigDecimal actual=BigDecimal.ZERO,booked=BigDecimal.ZERO;
            for(var usedBatch:used.allocations()) {
                if(usedBatch.batchId()==null)throw new IllegalStateException("ไม่พบรหัสล็อตที่ขาย");
                if(usedBatch.quantity()<=0||usedBatch.unitCost()==null||usedBatch.actualCost()==null||usedBatch.unitCost().signum()<0
                        ||usedBatch.unitCost().multiply(BigDecimal.valueOf(usedBatch.quantity())).compareTo(usedBatch.actualCost())!=0)
                    throw new IllegalStateException("ต้นทุนหรือจำนวนที่ตัดจากล็อตไม่ถูกต้อง");
                SaleAllocation allocation=new SaleAllocation();allocation.setStore(store);allocation.setSaleItem(item);
                allocation.setBatch(batches.getReferenceById(usedBatch.batchId()));allocation.setQuantity(usedBatch.quantity());
                allocation.setUnitCost(usedBatch.unitCost());allocation.setActualCost(usedBatch.actualCost());allocation.setFinancialCost(usedBatch.bookCost());allocations.save(allocation);
                allocated=Math.addExact(allocated,usedBatch.quantity());actual=actual.add(usedBatch.actualCost());
                booked=booked.add(usedBatch.bookCost());
            }
            if(allocated!=line.quantity()||actual.compareTo(used.cost())!=0||booked.compareTo(cost)!=0)
                throw new IllegalStateException("จำนวนหรือต้นทุนล็อตที่ขายไม่ตรงกับรายการขาย");
            financialCost=financialCost.add(cost);
        }
        sale.setListedAmount(pricing.listedAmount());sale.setDiscountAmount(pricing.discount());sale.setNetAmount(pricing.net());
        sale.setTaxAmount(pricing.tax());sale.setTaxRate(pricing.rule().rate());sale.setTaxMode(pricing.rule().mode().name());
        sale.setDiscountType(pricing.discountRule().type().name());sale.setDiscountValue(pricing.discountRule().value());
        sale.setDocumentType(terms==null?"RECEIPT":terms.documentType().name());
        sale.setSellerName(store.getLegalName()==null||store.getLegalName().isBlank()?store.getStoreName():store.getLegalName());
        sale.setSellerAddress(store.getLegalAddress()==null||store.getLegalAddress().isBlank()?store.getAddress():store.getLegalAddress());
        sale.setSellerTaxId(store.getTaxId());sale.setSellerBranch(store.getBranchCode());
        if(terms!=null&&terms.buyer()!=null){var buyer=terms.buyer();sale.setBuyerName(buyer.name());sale.setBuyerAddress(buyer.address());sale.setBuyerTaxId(buyer.taxId());sale.setBuyerBranch(buyer.branchCode());}
        List<CheckoutTerms.Payment> payments=terms==null?(pricing.payable().signum()==0?List.of():List.of(new CheckoutTerms.Payment(CheckoutTerms.Method.CASH,pricing.payable(),null,""))):terms.payments();
        sale.setPaymentMethod(terms==null?"CASH":terms.method());
        for(var payment:payments){SaleTender tender=new SaleTender();tender.setSale(sale);tender.setMethod(payment.method().name());tender.setAmount(payment.amount());tender.setReceived(payment.received());tender.setChangeAmount(payment.change());tender.setReference(payment.reference());sale.getTenders().add(tender);}
        sale.setTotalAmountValue(pricing.payable());sale.setTotalCost(financialCost);sales.saveAndFlush(sale);
        if(terms==null)journals.post(actor.storeId(),"SALE_REVENUE",sale.getId(),sale.getSaleNumber(),"1100","4000",pricing.payable());
        else for(var payment:payments)journals.post(actor.storeId(),"SALE_"+payment.method().name(),sale.getId(),sale.getSaleNumber(),payment.method().account(),"4000",payment.amount());
        journals.post(actor.storeId(),"SALE_VAT",sale.getId(),sale.getSaleNumber(),"4000","2200",pricing.tax());
        journals.post(actor.storeId(),"SALE_COGS",sale.getId(),sale.getSaleNumber(),"5000","1200",financialCost);
        if(audit!=null)audit.record(actor,"SALE",sale.getSaleNumber());
        return sale;
    }
    private String fingerprint(Long userId,Map<String,Integer> items) {
        try {
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            try(java.io.DataOutputStream data=new java.io.DataOutputStream(bytes)) {
                data.writeLong(userId);data.writeUTF("CASH");data.writeInt(items.size());
                for(var entry:items.entrySet()){data.writeUTF(entry.getKey().toUpperCase(Locale.ROOT));data.writeInt(entry.getValue());}
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }

    private String fingerprint(Long userId,Map<String,Integer> items,CheckoutTerms terms) {
        try {
            var bytes=new java.io.ByteArrayOutputStream();
            try(var data=new java.io.DataOutputStream(bytes)) {
                data.writeUTF(fingerprint(userId,items));data.writeUTF(terms.discount().type().name());data.writeUTF(terms.discount().value().toPlainString());
                data.writeUTF(terms.expectedTotal().toPlainString());data.writeLong(terms.taxRevision());data.writeUTF(terms.documentType().name());
                if(terms.buyer()!=null){data.writeUTF(terms.buyer().name());data.writeUTF(terms.buyer().address());data.writeUTF(terms.buyer().taxId());data.writeUTF(terms.buyer().branchCode());}
                for(var payment:terms.payments().stream().sorted(Comparator.comparing(p->p.method().name())).toList()) {
                    data.writeUTF(payment.method().name());data.writeUTF(payment.amount().toPlainString());data.writeUTF(payment.received().toPlainString());data.writeUTF(payment.reference());
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(java.io.IOException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    public record Quotation(SalePricing.Quote pricing,long taxRevision,boolean taxInvoiceAvailable) {}
    @Transactional(readOnly=true)
    public Quotation quote(List<String> codes,List<Integer> quantities,SalePricing.Discount discount,AccessService.Actor actor) {
        if(actor==null||!Set.of("OWNER","ADMIN","SALES","STAFF").contains(actor.role()))throw new IllegalArgumentException("ไม่มีสิทธิ์ขายสินค้า");
        if(codes==null||quantities==null||codes.isEmpty()||codes.size()!=quantities.size()||codes.size()>200)throw new IllegalArgumentException("กรุณาระบุสินค้า 1 ถึง 200 รายการ");
        Map<String,Integer> merged=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for(int i=0;i<codes.size();i++){String code=PurchasingService.text(codes.get(i),255,"รหัสสินค้า");Integer q=quantities.get(i);if(q==null||q<=0)throw new IllegalArgumentException("จำนวนขายไม่ถูกต้อง");merged.merge(code,q,Math::addExact);}
        Store store=stores.findById(actor.storeId()).filter(s->Boolean.TRUE.equals(s.getActive())).orElseThrow(()->new IllegalArgumentException("ไม่พบร้านที่เปิดใช้งาน"));
        List<SalePricing.Input> input=new ArrayList<>();
        for(var entry:merged.entrySet()){Product product=products.findByStoreIdAndCode(actor.storeId(),entry.getKey()).orElseThrow(()->new IllegalArgumentException("ไม่พบสินค้าในร้าน"));if(!Boolean.TRUE.equals(product.getActive()))throw new IllegalArgumentException("สินค้าปิดการขายแล้ว");input.add(new SalePricing.Input(product.getCode(),entry.getValue(),product.getPriceValue()));}
        return new Quotation(SalePricing.quote(input,StoreTaxService.rule(store),discount==null?SalePricing.Discount.none():discount),store.getTaxRevision(),store.isVatRegistered()&&!store.getTaxMode().equals("NONE"));
    }

    public List<Sale> getAllSales(Long storeId){requireStore(storeId);return sales.findAllByStoreIdOrderByCreatedAtDesc(storeId);}
    public List<Sale> getSalesByMonth(int year,int month,Long storeId){requireStore(storeId);YearMonth m=YearMonth.of(year,month);return sales.findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(storeId,m.atDay(1).atStartOfDay(),m.plusMonths(1).atDay(1).atStartOfDay());}
    public Product getProductByCode(String code,Long storeId){requireStore(storeId);return products.findByStoreIdAndCode(storeId,code).orElse(null);}
    public Sale getSaleById(Long id,Long storeId){requireStore(storeId);return sales.findByIdAndStoreId(id,storeId).orElse(null);}
    public List<SaleItem> getSaleItems(Long id,Long storeId){requireStore(storeId);return items.findBySaleIdAndSaleStoreId(id,storeId);}
    @Transactional(readOnly=true)
    public List<AllocationView> getAllocations(Long id,Long storeId){
        requireStore(storeId);if(getSaleById(id,storeId)==null)throw new IllegalArgumentException("ไม่พบบิลในร้าน");
        return allocations.findBySaleItemSaleIdAndStoreIdOrderByIdAsc(id,storeId).stream().map(a->new AllocationView(
            a.getSaleItem().getProductCode(),a.getBatch().getId(),a.getBatch().getExpiryDate(),a.getQuantity(),a.getUnitCost(),a.getActualCost(),a.getFinancialCost())).toList();
    }
    public record AllocationView(String productCode,Long batchId,LocalDate expiryDate,Integer quantity,BigDecimal unitCost,BigDecimal actualCost,BigDecimal financialCost){}

    private Summary totals(Long storeId,LocalDateTime start,LocalDateTime end) {
        requireStore(storeId);
        BigDecimal revenue=sales.findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(storeId,start,end)
            .stream().map(s->DecimalMoney.zero(s.getNetAmount())).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cost=items.findAllBySaleStoreId(storeId).stream().filter(i->i.getSale()!=null&&i.getSale().getCreatedAt()!=null)
            .filter(i->!i.getSale().getCreatedAt().isBefore(start)&&i.getSale().getCreatedAt().isBefore(end))
            .map(i->DecimalMoney.zero(i.getCostValue())).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(adjustments!=null){var a=adjustments.totals(storeId,start,end);revenue=revenue.subtract(a.revenue());cost=cost.subtract(a.cost());}
        return new Summary(revenue,cost);
    }
    private record Summary(BigDecimal revenue,BigDecimal cost){BigDecimal profit(){return revenue.subtract(cost);}}
    private Summary day(LocalDate date,Long storeId){return totals(storeId,date.atStartOfDay(),date.plusDays(1).atStartOfDay());}
    private Summary week(Long storeId){LocalDate start=today().with(DayOfWeek.MONDAY);return totals(storeId,start.atStartOfDay(),start.plusWeeks(1).atStartOfDay());}
    private Summary month(YearMonth m,Long storeId){return totals(storeId,m.atDay(1).atStartOfDay(),m.plusMonths(1).atDay(1).atStartOfDay());}
    private Summary year(int year,Long storeId){LocalDate start=LocalDate.of(year,1,1);return totals(storeId,start.atStartOfDay(),start.plusYears(1).atStartOfDay());}
    public double getTodayRevenue(Long storeId){return day(today(),storeId).revenue().doubleValue();}
    public double getTodayCost(Long storeId){return day(today(),storeId).cost().doubleValue();}
    public double getTodayProfit(Long storeId){return day(today(),storeId).profit().doubleValue();}
    public double getWeekRevenue(Long storeId){return week(storeId).revenue().doubleValue();}
    public double getWeekCost(Long storeId){return week(storeId).cost().doubleValue();}
    public double getWeekProfit(Long storeId){return week(storeId).profit().doubleValue();}
    public double getMonthRevenue(Long storeId){return month(YearMonth.from(today()),storeId).revenue().doubleValue();}
    public double getMonthCost(Long storeId){return month(YearMonth.from(today()),storeId).cost().doubleValue();}
    public double getMonthProfit(Long storeId){return month(YearMonth.from(today()),storeId).profit().doubleValue();}
    public double getYearRevenue(Long storeId){return year(today().getYear(),storeId).revenue().doubleValue();}
    public double getYearCost(Long storeId){return year(today().getYear(),storeId).cost().doubleValue();}
    public double getYearProfit(Long storeId){return year(today().getYear(),storeId).profit().doubleValue();}

    public Map<String,Double> getLast7DaysRevenue(Long storeId){Map<String,Double> result=new LinkedHashMap<>();for(int i=6;i>=0;i--){LocalDate d=today().minusDays(i);result.put(d.toString(),day(d,storeId).revenue().doubleValue());}return result;}
    public List<Object[]> getLast7DaysSummary(Long storeId){List<Object[]> result=new ArrayList<>();for(int i=6;i>=0;i--){LocalDate d=today().minusDays(i);Summary s=day(d,storeId);result.add(new Object[]{d,s.revenue(),s.cost(),s.profit()});}return result;}
    public List<Object[]> getBestSellingProducts(Long storeId){
        requireStore(storeId);Map<String,Object[]> result=new LinkedHashMap<>();
        for(SaleItem i:items.findAllBySaleStoreId(storeId)){
            Object[] r=result.computeIfAbsent(i.getProductCode(),k->new Object[]{k,i.getProductName(),0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});
            r[2]=Math.addExact((Integer)r[2],i.getQuantity()==null?0:i.getQuantity());
            r[3]=((BigDecimal)r[3]).add(DecimalMoney.zero(i.getNetAmount()));r[4]=((BigDecimal)r[4]).add(DecimalMoney.zero(i.getCostValue()));r[5]=((BigDecimal)r[3]).subtract((BigDecimal)r[4]);
        }
        if(adjustments!=null)for(var a:adjustments.products(storeId,LocalDate.of(1970,1,1).atStartOfDay(),LocalDate.of(9999,12,31).atStartOfDay())){
            Object[] r=result.computeIfAbsent(a.code(),k->new Object[]{k,a.name(),0,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO});r[2]=Math.subtractExact((Integer)r[2],a.quantity());r[3]=((BigDecimal)r[3]).subtract(a.revenue());r[4]=((BigDecimal)r[4]).subtract(a.cost());r[5]=((BigDecimal)r[3]).subtract((BigDecimal)r[4]);
        }
        return result.values().stream().sorted((a,b)->Integer.compare((Integer)b[2],(Integer)a[2])).toList();
    }
    public static class ReportSummary {
        private final BigDecimal revenue,cost;
        public ReportSummary(BigDecimal revenue,BigDecimal cost){this.revenue=revenue;this.cost=cost;}
        public BigDecimal getRevenueValue(){return revenue;} public BigDecimal getCostValue(){return cost;} public BigDecimal getProfitValue(){return revenue.subtract(cost);}
        public double getRevenue(){return revenue.doubleValue();} public double getCost(){return cost.doubleValue();} public double getProfit(){return revenue.subtract(cost).doubleValue();}
    }
    private ReportSummary report(Summary s){return new ReportSummary(s.revenue(),s.cost());}
    public ReportSummary getDailyReport(LocalDate d,Long storeId){return report(day(d,storeId));}
    public ReportSummary getMonthlyReport(YearMonth m,Long storeId){return report(month(m,storeId));}
    public ReportSummary getYearlyReport(int y,Long storeId){return report(year(y,storeId));}
    public List<Object[]> getDailyReportRows(LocalDate d,Long storeId){
        requireStore(storeId);List<Object[]> result=new ArrayList<>();
        for(Sale sale:sales.findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(storeId,d.atStartOfDay(),d.plusDays(1).atStartOfDay())){
            BigDecimal revenue=DecimalMoney.zero(sale.getNetAmount());
            BigDecimal cost=getSaleItems(sale.getId(),storeId).stream().map(i->DecimalMoney.zero(i.getCostValue())).reduce(BigDecimal.ZERO,BigDecimal::add);
            result.add(new Object[]{sale.getSaleNumber(),sale.getCreatedAt(),revenue,cost,revenue.subtract(cost)});
        }
        if(adjustments!=null)result.addAll(adjustments.rows(storeId,d.atStartOfDay(),d.plusDays(1).atStartOfDay()));
        return result;
    }
    public List<Object[]> getMonthlyReportRows(YearMonth m,Long storeId){List<Object[]> result=new ArrayList<>();for(int i=1;i<=m.lengthOfMonth();i++){LocalDate d=m.atDay(i);Summary s=day(d,storeId);result.add(new Object[]{d,s.revenue(),s.cost(),s.profit()});}return result;}
    public List<Object[]> getYearlyReportRows(int y,Long storeId){List<Object[]> result=new ArrayList<>();for(int i=1;i<=12;i++){YearMonth m=YearMonth.of(y,i);Summary s=month(m,storeId);result.add(new Object[]{m,s.revenue(),s.cost(),s.profit()});}return result;}
    public List<Sale> getAllSales() {throw scoped();}
    public List<Sale> getSalesByMonth(int year, int month) {throw scoped();}
    public Product getProductByCode(String code) {throw scoped();}
    public Sale getSaleById(Long id) {throw scoped();}
    public Sale createSale(String saleNumber, String productCode, Integer quantity) {throw scoped();}
    public Sale createMultiSale(String saleNumber, List<String> productCodes, List<Integer> quantities) {throw scoped();}
    public Sale createMultiSale(String saleNumber,
                                List<String> productCodes,
                                List<Integer> quantities,
                                Long storeId) {throw scoped();}
    public List<Object[]> getBestSellingProducts() {throw scoped();}
    public List<SaleItem> getSaleItems(Long saleId) {throw scoped();}
    public double getTodayRevenue() {throw scoped();}
    public double getTodayCost() {throw scoped();}
    public double getTodayProfit() {throw scoped();}
    public double getWeekRevenue() {throw scoped();}
    public double getWeekCost() {throw scoped();}
    public double getWeekProfit() {throw scoped();}
    public double getMonthRevenue() {throw scoped();}
    public double getMonthCost() {throw scoped();}
    public double getMonthProfit() {throw scoped();}
    public double getYearRevenue() {throw scoped();}
    public double getYearCost() {throw scoped();}
    public double getYearProfit() {throw scoped();}
    public Map<String, Double> getLast7DaysRevenue() {throw scoped();}
    public List<Object[]> getLast7DaysSummary() {throw scoped();}
    public ReportSummary getDailyReport(LocalDate date) {throw scoped();}
    public List<Object[]> getDailyReportRows(LocalDate date) {throw scoped();}
    public ReportSummary getMonthlyReport(YearMonth month) {throw scoped();}
    public List<Object[]> getMonthlyReportRows(YearMonth month) {throw scoped();}
    public ReportSummary getYearlyReport(int year) {throw scoped();}
    public List<Object[]> getYearlyReportRows(int year) {throw scoped();}
}
