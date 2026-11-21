package com.kongklang.service;
import com.kongklang.entity.SaleItem;
import com.kongklang.repository.SaleItemRepository;
import com.kongklang.support.DecimalMoney;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ProfitReportService {
    private final SaleItemRepository items;
    public ProfitReportService(SaleItemRepository items){this.items=items;}
    public ReportResult generateReport(LocalDate start,LocalDate end){throw new IllegalStateException("ต้องระบุร้านในการดูรายงาน");}
    public ReportResult generateReport(LocalDate start,LocalDate end,Long storeId){
        if(storeId==null||start==null||end==null||end.isBefore(start))throw new IllegalArgumentException("กรุณาระบุร้านและช่วงวันที่ที่ถูกต้อง");
        LocalDateTime from=start.atStartOfDay(),to=end.plusDays(1).atStartOfDay();
        BigDecimal revenue=BigDecimal.ZERO,cost=BigDecimal.ZERO;int quantity=0;Set<Long> bills=new HashSet<>();Map<String,ProductReportRow> rows=new LinkedHashMap<>();
        for(SaleItem item:items.findAllBySaleStoreId(storeId)){
            if(item.getSale()==null||item.getSale().getCreatedAt()==null)continue;
            LocalDateTime date=item.getSale().getCreatedAt();if(date.isBefore(from)||!date.isBefore(to))continue;
            BigDecimal subtotal=DecimalMoney.zero(item.getSubtotalValue()),itemCost=DecimalMoney.zero(item.getCostValue());int qty=item.getQuantity()==null?0:item.getQuantity();
            revenue=revenue.add(subtotal);cost=cost.add(itemCost);quantity=Math.addExact(quantity,qty);
            if(item.getSale().getId()!=null)bills.add(item.getSale().getId());
            rows.computeIfAbsent(item.getProductCode(),code->new ProductReportRow(code,item.getProductName())).add(qty,subtotal,itemCost);
        }
        return new ReportResult(start,end,revenue,cost,quantity,bills.size(),new ArrayList<>(rows.values()));
    }
    public static class ReportResult {
        private final LocalDate startDate,endDate;private final BigDecimal totalSales,totalCost;
        private final int totalQuantity,totalBills;private final List<ProductReportRow> products;
        public ReportResult(LocalDate start,LocalDate end,BigDecimal sales,BigDecimal cost,int quantity,int bills,List<ProductReportRow> products){
            startDate=start;endDate=end;totalSales=sales;totalCost=cost;totalQuantity=quantity;totalBills=bills;this.products=List.copyOf(products);
        }
        public LocalDate getStartDate(){return startDate;} public LocalDate getEndDate(){return endDate;}
        public double getTotalSales(){return totalSales.doubleValue();} public double getTotalCost(){return totalCost.doubleValue();} public double getTotalProfit(){return totalSales.subtract(totalCost).doubleValue();}
        public BigDecimal getTotalSalesValue(){return totalSales;} public BigDecimal getTotalCostValue(){return totalCost;} public BigDecimal getTotalProfitValue(){return totalSales.subtract(totalCost);}
        public int getTotalQuantity(){return totalQuantity;} public int getTotalBills(){return totalBills;} public List<ProductReportRow> getProducts(){return products;}
    }
    public static class ProductReportRow {
        private final String productCode,productName;private int quantity;private BigDecimal sales=BigDecimal.ZERO,cost=BigDecimal.ZERO;
        public ProductReportRow(String code,String name){productCode=code;productName=name;}
        public void add(int qty,BigDecimal subtotal,BigDecimal value){quantity=Math.addExact(quantity,qty);sales=sales.add(subtotal);cost=cost.add(value);}
        public String getProductCode(){return productCode;} public String getProductName(){return productName;} public int getQuantity(){return quantity;}
        public double getSales(){return sales.doubleValue();} public double getCost(){return cost.doubleValue();} public double getProfit(){return sales.subtract(cost).doubleValue();}
        public BigDecimal getSalesValue(){return sales;}public BigDecimal getCostValue(){return cost;}
    }
}
