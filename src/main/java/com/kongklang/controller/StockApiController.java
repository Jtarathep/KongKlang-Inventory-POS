package com.kongklang.controller;
import com.kongklang.entity.*;
import com.kongklang.repository.StockTransactionRepository;
import com.kongklang.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/stock")
public class StockApiController {
    private final ProductService products;
    private final StockTransactionRepository transactions;
    private final AccessService access;
    private final InventoryOperationService operations;
    public StockApiController(ProductService products,StockTransactionRepository transactions,AccessService access,InventoryOperationService operations) {
        this.products=products;this.transactions=transactions;this.access=access;this.operations=operations;
    }
    @GetMapping public List<StockProductResponse> stock(HttpSession session) {
        access.require(session);return products.getAllProducts(session).stream().map(StockProductResponse::from).toList();
    }
    @GetMapping("/{code}") public ResponseEntity<StockProductResponse> product(@PathVariable String code,HttpSession session) {
        access.require(session);Product p=products.getProductByCode(code,session);
        return p==null?ResponseEntity.notFound().build():ResponseEntity.ok(StockProductResponse.from(p));
    }
    @GetMapping("/history") public List<StockHistoryResponse> history(HttpSession session) {
        var actor=access.require(session);return transactions.findAllByStoreIdOrderByCreatedAtDesc(actor.storeId()).stream().map(StockHistoryResponse::from).toList();
    }
    @GetMapping("/operations") public List<InventoryOperationService.OperationView> operations(HttpSession session) {
        return operations.list(access.require(session,"OWNER","ADMIN","WAREHOUSE","ACCOUNTING"));
    }
    @PostMapping("/operations") public InventoryOperationService.OperationView operation(@RequestBody InventoryOperationService.Input input,HttpSession session) {
        return operations.execute(access.require(session,"OWNER","ADMIN","WAREHOUSE"),input);
    }
    @PostMapping("/in") public InventoryOperationService.OperationView receive(@RequestBody StockRequest r,HttpSession session) {return apply("RECEIPT",r,session);}
    @PostMapping("/out") public InventoryOperationService.OperationView out(@RequestBody StockRequest r,HttpSession session) {return apply("OUT",r,session);}
    @PostMapping("/adjust") public InventoryOperationService.OperationView adjust(@RequestBody StockRequest r,HttpSession session) {return apply("ADJUST",r,session);}
    private InventoryOperationService.OperationView apply(String kind,StockRequest r,HttpSession session) {
        return operations.execute(access.require(session,"OWNER","ADMIN","WAREHOUSE"),new InventoryOperationService.Input(
            r.requestKey(),kind,r.code(),r.quantity(),r.expectedQuantity(),r.expectedVersion(),r.purchaseCost(),r.sellingPrice(),r.expiryDate(),r.note()));
    }
    @PostMapping("/scan-in") public InventoryOperationService.OperationView scanIn(@RequestParam String requestKey,@RequestParam String barcode,
            @RequestParam(required=false) LocalDate expiryDate,HttpSession session) {
        return scan("RECEIPT",requestKey,barcode,1,expiryDate,"รับเข้ารอตรวจสอบบัญชีจากบาร์โค้ด",session);
    }
    @PostMapping("/scan-out") public InventoryOperationService.OperationView scanOut(@RequestParam String requestKey,@RequestParam String barcode,
            @RequestParam(defaultValue="1") Integer quantity,@RequestParam String note,HttpSession session) {
        return scan("OUT",requestKey,barcode,quantity,null,note,session);
    }
    private InventoryOperationService.OperationView scan(String kind,String key,String barcode,Integer quantity,LocalDate expiry,String note,HttpSession session) {
        var actor=access.require(session,"OWNER","ADMIN","WAREHOUSE");
        if(barcode==null||barcode.isBlank())throw new IllegalArgumentException("กรุณาระบุบาร์โค้ด");
        Product p=products.getProductByBarcode(barcode.trim(),session);
        if(p==null)throw new IllegalArgumentException("ไม่พบสินค้าในร้านปัจจุบัน");
        return operations.execute(actor,new InventoryOperationService.Input(key,kind,p.getCode(),quantity,null,null,null,null,expiry,note));
    }
    public record StockRequest(String requestKey,String code,Integer quantity,Integer expectedQuantity,Long expectedVersion,BigDecimal purchaseCost,BigDecimal sellingPrice,LocalDate expiryDate,String note){}
    public record StockProductResponse(Long id,String code,String barcode,String name,Integer quantity,Double price,String category,String unit,Double costPrice) {
        static StockProductResponse from(Product p){return new StockProductResponse(p.getId(),p.getCode(),p.getBarcode(),p.getName(),p.getQuantity(),p.getPrice(),p.getCategory(),p.getUnit(),p.getCostPrice());}
    }
    public record StockHistoryResponse(Long id,String productCode,String productName,String type,Integer quantity,Integer balance,String note,LocalDateTime createdAt) {
        static StockHistoryResponse from(StockTransaction t){return new StockHistoryResponse(t.getId(),t.getProductCode(),t.getProductName(),t.getType(),t.getQuantity(),t.getBalance(),t.getNote(),t.getCreatedAt());}
    }
}
