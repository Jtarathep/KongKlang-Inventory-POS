package com.kongklang.service;

import com.kongklang.entity.*;
import com.kongklang.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true)
public class OverviewService {
    private SalesAdjustmentReader adjustments;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAdjustments(SalesAdjustmentReader value){adjustments=value;}
    private final ProductRepository products;private final StockBatchRepository batches;
    private final SaleRepository sales;private final SaleItemRepository items;
    public OverviewService(ProductRepository products,StockBatchRepository batches,SaleRepository sales,SaleItemRepository items){
        this.products=products;this.batches=batches;this.sales=sales;this.items=items;
    }
    public Overview report(Long storeId,LocalDate start,LocalDate end) {
        if(storeId==null||start==null||end==null||end.isBefore(start)||start.plusYears(5).isBefore(end))
            throw new IllegalArgumentException("เลือกช่วงวันที่ให้ถูกต้องและไม่เกิน 5 ปี");
        List<Sale> sold=sales.findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(storeId,start.atStartOfDay(),end.plusDays(1).atStartOfDay());
        Set<Long> ids=new HashSet<>();sold.forEach(s->ids.add(s.getId()));
        List<SaleItem> lines=items.findAllBySaleStoreId(storeId).stream().filter(i->ids.contains(i.getSale().getId())).toList();
        BigDecimal revenue=sold.stream().map(Sale::getNetAmount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cost=lines.stream().map(SaleItem::getCostValue).reduce(BigDecimal.ZERO,BigDecimal::add);
        long unverified=sold.stream().filter(s->s.getTotalCost()==null).count();
        Map<String,BestSeller> best=new HashMap<>();
        for(var i:lines) {
            BestSeller prev=best.getOrDefault(i.getProductCode(),new BestSeller(i.getProductCode(),i.getProductName(),0,BigDecimal.ZERO));
            best.put(i.getProductCode(),new BestSeller(i.getProductCode(),i.getProductName(),Math.addExact(prev.quantity(),i.getQuantity()),prev.revenue().add(i.getNetAmount())));
        }
        if(adjustments!=null){
            var a=adjustments.totals(storeId,start.atStartOfDay(),end.plusDays(1).atStartOfDay());revenue=revenue.subtract(a.revenue());cost=cost.subtract(a.cost());
            for(var returned:adjustments.products(storeId,start.atStartOfDay(),end.plusDays(1).atStartOfDay())){
                var prev=best.getOrDefault(returned.code(),new BestSeller(returned.code(),returned.name(),0,BigDecimal.ZERO));
                best.put(returned.code(),new BestSeller(returned.code(),returned.name(),Math.subtractExact(prev.quantity(),returned.quantity()),prev.revenue().subtract(returned.revenue())));
            }
        }
        LocalDate today=LocalDate.now(ZoneId.of("Asia/Bangkok"));
        Map<String,List<StockBatch>> lots=new HashMap<>();
        for(var b:batches.findAllByStoreId(storeId))lots.computeIfAbsent(b.getProductCode(),k->new ArrayList<>()).add(b);
        List<StockRow> stock=new ArrayList<>();
        for(var p:products.findAllByStoreId(storeId)) {
            List<StockBatch> group=lots.getOrDefault(p.getCode(),List.of());
            long lotQty=group.stream().mapToLong(b->b.getRemainingQuantity()==null?0:b.getRemainingQuantity()).sum();
            long expired=group.stream().filter(b->b.getExpiryDate()!=null&&b.getExpiryDate().isBefore(today)).mapToLong(b->b.getRemainingQuantity()==null?0:b.getRemainingQuantity()).sum();
            long near=group.stream().filter(b->b.getExpiryDate()!=null&&!b.getExpiryDate().isBefore(today)&&!b.getExpiryDate().isAfter(today.plusDays(30))).mapToLong(b->b.getRemainingQuantity()==null?0:b.getRemainingQuantity()).sum();
            BigDecimal value=group.stream().map(StockBatch::getRemainingValue).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
            long qty=p.getQuantity()==null?0:p.getQuantity();
            stock.add(new StockRow(p.getCode(),p.getName(),p.getCategory(),qty,lotQty,expired,near,value,qty==lotQty));
        }
        stock.sort(Comparator.comparing(StockRow::code));
        return new Overview(start,end,sold.size(),revenue,cost,revenue.subtract(cost),unverified,stock,
            best.values().stream().sorted(Comparator.comparingLong(BestSeller::quantity).reversed().thenComparing(BestSeller::code)).limit(20).toList());
    }
    public record StockRow(String code,String name,String category,long quantity,long lotQuantity,long expired,long expiring,BigDecimal value,boolean consistent){}
    public record BestSeller(String code,String name,long quantity,BigDecimal revenue){}
    public record Overview(LocalDate start,LocalDate end,long saleCount,BigDecimal revenue,BigDecimal cost,BigDecimal grossProfit,long unverifiedLegacySales,List<StockRow> stock,List<BestSeller> bestSellers) {
        public long getMismatchCount(){return stock.stream().filter(s->!s.consistent()).count();}
        public BigDecimal getInventoryValue(){return stock.stream().map(StockRow::value).reduce(BigDecimal.ZERO,BigDecimal::add);}
    }
}
