package com.kongklang.controller;

import com.kongklang.service.*;
import com.kongklang.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Controller
public class MobileWorkspaceController {
    private static final Set<String> SALES_ROLES = Set.of("OWNER", "ADMIN", "SALES", "STAFF", "ACCOUNTING");
    private final StockBatchRepository batches;
    private final StockTransactionRepository movements;
    private final SaleService saleService;
    private final AccessService access;private final ProductRepository products;private final SaleRepository sales;private final StoreAnalysisService analysis;private final InventoryOperationService inventory;
    public MobileWorkspaceController(AccessService access,ProductRepository products,SaleRepository sales,StoreAnalysisService analysis,InventoryOperationService inventory,SaleService saleService, StockBatchRepository batches, StockTransactionRepository movements){this.batches=batches;this.movements=movements;this.saleService=saleService;this.access=access;this.products=products;this.sales=sales;this.analysis=analysis;this.inventory=inventory;}
    @GetMapping({"/mobile", "/mobile/"}) public String open(){return "redirect:/mobile/index.html";}
    @GetMapping("/api/mobile/session") @ResponseBody public Object session(HttpSession session){
        var a=access.require(session);return Map.of("store",Objects.toString(session.getAttribute("storeName"),""),"role",a.role(),"displayName",Objects.toString(session.getAttribute("displayName"),""),"csrf",session.getAttribute("csrfToken"),"canReceive",Set.of("OWNER","ADMIN","WAREHOUSE").contains(a.role()),"canSeeSales",SALES_ROLES.contains(a.role()),"canAnalyze",Set.of("OWNER","ADMIN","ACCOUNTING").contains(a.role()));
    }
    // Additive id field lets the mobile UI use the existing store-scoped photo route.
    public record ProductRow(Long id,String code,String name,String barcode,String category,Integer quantity,BigDecimal price){}
    @GetMapping("/api/mobile/barcode") @ResponseBody
    public ResponseEntity<ProductRow> barcode(@RequestParam String value, HttpSession session) {
        var actor = access.require(session);
        String barcode = value.trim();
        if (barcode.isEmpty() || barcode.length() > 120) return ResponseEntity.badRequest().build();
        return ResponseEntity.of(products.findByStoreIdAndBarcode(actor.storeId(), barcode)
            .map(p -> new ProductRow(p.getId(), p.getCode(), p.getName(), p.getBarcode(), p.getCategory(), p.getQuantity(), p.getPriceValue())));
    }
    @GetMapping("/api/mobile/products")
    @ResponseBody
    public Object products(@RequestParam(defaultValue = "") String query,
                           @RequestParam(defaultValue = "all") String stock, HttpSession session) {
        var actor = access.require(session);
        String term = query.trim();
        if (term.length() > 120 || !Set.of("all", "low", "out", "replenish").contains(stock)) {
            return ResponseEntity.badRequest().body(Map.of("message", "คำค้นหาหรือตัวกรองไม่ถูกต้อง"));
        }
        return products.mobileSearch(actor.storeId(), term, stock, PageRequest.of(0, 100)).stream()
                .map(product -> new ProductRow(product.getId(), product.getCode(), product.getName(), product.getBarcode(),
                        product.getCategory(), product.getQuantity(), product.getPriceValue()))
                .toList();
    }
    public record BatchRow(String product, Integer remaining, LocalDate expiry, LocalDateTime received) {}
    public record MovementRow(String product, String type, Integer quantity, Integer balance, LocalDateTime at) {}

    @GetMapping("/api/mobile/batches") @ResponseBody
    public Object batches(HttpSession session) {
        var actor = access.require(session);
        return batches.findByStoreIdOrderByReceivedAtDesc(actor.storeId(), PageRequest.of(0, 100)).stream()
            .map(b -> new BatchRow(b.getProductName(), b.getRemainingQuantity(), b.getExpiryDate(), b.getReceivedAt())).toList();
    }

    @GetMapping("/api/mobile/movements") @ResponseBody
    public Object movements(HttpSession session) {
        var actor = access.require(session);
        return movements.findByStoreIdOrderByCreatedAtDesc(actor.storeId(), PageRequest.of(0, 100)).stream()
            .map(m -> new MovementRow(m.getProductName(), m.getType(), m.getQuantity(), m.getBalance(), m.getCreatedAt())).toList();
    }

    public record SaleRow(Long id,String number,LocalDateTime at,BigDecimal total,BigDecimal vat,String method){}
    @GetMapping("/api/mobile/sales") @ResponseBody public Object sales(HttpSession s){var a=access.require(s,"OWNER","ADMIN","SALES","STAFF","ACCOUNTING");return sales.findByStoreIdOrderByCreatedAtDescIdDesc(a.storeId(),PageRequest.of(0,50)).stream().map(x->new SaleRow(x.getId(),x.getSaleNumber(),x.getCreatedAt(),x.getTotalAmountValue(),x.getTaxAmount(),x.getPaymentMethod())).toList();}
    @GetMapping("/api/mobile/analysis") @ResponseBody public Object analysis(@RequestParam(defaultValue="sales") String topic,HttpSession s){return analysis.analyze(access.require(s,"OWNER","ADMIN","ACCOUNTING").storeId(),topic);}
    public record SalesDay(LocalDate date, BigDecimal amount, BigDecimal cost) {}
    public record LowStockItem(String name, Integer quantity) {}

    @GetMapping("/api/mobile/dashboard")
    @ResponseBody
    public Object dashboard(HttpSession session) {
        var actor = access.require(session);
        var storeId = actor.storeId();
        boolean canSeeCost = Set.of("OWNER", "ADMIN", "ACCOUNTING").contains(actor.role());
        var salesDays = SALES_ROLES.contains(actor.role())
                ? saleService.getLast7DaysSummary(storeId).stream()
                        .map(day -> new SalesDay((LocalDate) day[0], (BigDecimal) day[1],
                                canSeeCost ? (BigDecimal) day[2] : null))
                        .toList()
                : List.of();
        return Map.of("products", products.countByStoreId(storeId),
                "lowStock", products.countByStoreIdAndQuantityLessThanEqual(storeId, 5),
                "lowStockItems", products.findTop4ByStoreIdAndQuantityLessThanEqualOrderByQuantityAscIdAsc(storeId, 5)
                        .stream().map(product -> new LowStockItem(product.getName(), product.getQuantity())).toList(),
                "salesDays", salesDays);
    }
    @PostMapping("/api/mobile/receive") @ResponseBody public ResponseEntity<?> receive(@RequestBody InventoryOperationService.Input input,HttpSession s){
        try{var actor=access.require(s,"OWNER","ADMIN","WAREHOUSE");if(input==null||!"RECEIPT".equals(input.kind()))throw new IllegalArgumentException("หน้านี้บันทึกได้เฉพาะรับสินค้าเข้า");return ResponseEntity.ok(inventory.execute(actor,input));}catch(IllegalArgumentException|ArithmeticException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
    }
}
