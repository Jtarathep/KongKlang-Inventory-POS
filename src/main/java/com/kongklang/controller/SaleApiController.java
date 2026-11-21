package com.kongklang.controller;

import com.kongklang.entity.Product;
import com.kongklang.entity.Sale;
import com.kongklang.entity.SaleItem;
import com.kongklang.service.SaleService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
public class SaleApiController {

    private final SaleService saleService;
    private final com.kongklang.service.AccessService access;
    private final StoreContextService storeContextService;

    public SaleApiController(SaleService saleService, StoreContextService storeContextService, com.kongklang.service.AccessService access) {
        this.access = access;
        this.saleService = saleService;
        this.storeContextService = storeContextService;
    }

    // ==============================
    // ดูรายการขายทั้งหมด
    // GET /api/sales
    // ==============================

    @GetMapping
    public ResponseEntity<List<Sale>> getAllSales(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getAllSales(storeContextService.getCurrentStoreId(session))
        );
    }


    // ==============================
    // ดูข้อมูลบิลตาม ID
    // GET /api/sales/{id}
    // ==============================

    @GetMapping("/{id}")
    public ResponseEntity<Sale> getSaleById(
            @PathVariable Long id, HttpSession session) {

        Sale sale =
                saleService.getSaleById(id, storeContextService.getCurrentStoreId(session));

        if (sale == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(sale);
    }


    // ==============================
    // ดูสินค้าในบิล
    // GET /api/sales/{id}/items
    // ==============================

    @GetMapping("/{id}/items")
    public ResponseEntity<List<SaleItem>> getSaleItems(
            @PathVariable Long id, HttpSession session) {

        Sale sale =
                saleService.getSaleById(id, storeContextService.getCurrentStoreId(session));

        if (sale == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                saleService.getSaleItems(id, storeContextService.getCurrentStoreId(session))
        );
    }


    // ==============================
    // ค้นหาสินค้าสำหรับขาย
    // GET /api/sales/product/{code}
    // ==============================

    @GetMapping("/product/{code}")
    public ResponseEntity<Product> getProduct(
            @PathVariable String code, HttpSession session) {

        Product product =
                saleService.getProductByCode(code, storeContextService.getCurrentStoreId(session));

        if (product == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(product);
    }


    // ==============================
    // สร้างรายการขาย
    // POST /api/sales
    // ==============================

    @PostMapping
    public ResponseEntity<?> createSale(
            @RequestBody SaleRequest request, HttpSession session) {

        try {

            var actor=access.require(session,"OWNER","ADMIN","SALES","STAFF");
            Sale sale=request.getTerms()==null
                ?saleService.checkout(request.getRequestKey(),request.getProductCodes(),request.getQuantities(),actor)
                :saleService.checkout(request.getRequestKey(),request.getProductCodes(),request.getQuantities(),actor,request.getTerms());
            return ResponseEntity.ok(new SaleResponse(sale.getId(),sale.getSaleNumber(),sale.getTotalAmountValue(),sale.getCreatedAt(),sale.getPaymentMethod(),sale.getPaymentStatus()));

        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(java.util.Map.of("message","มีรายการรหัสนี้กำลังบันทึกหรือมีข้อมูลอ้างอิงไม่ถูกต้อง กรุณาตรวจสอบและลองส่งรายการเดิมอีกครั้ง"));
        } catch (IllegalArgumentException | ArithmeticException e) {

            return ResponseEntity
                    .badRequest()
                    .body(java.util.Map.of("message",e.getMessage()==null?"ไม่สามารถบันทึกการขายได้":e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(500).body(java.util.Map.of("message","ยังยืนยันผลการขายไม่ได้ กรุณาตรวจสอบและส่งข้อมูลพร้อมรหัสเดิมอีกครั้ง"));
        }
    }


    // ==============================
    // Request สำหรับสร้างบิล
    // ==============================

    public record SaleResponse(Long id,String saleNumber,java.math.BigDecimal totalAmount,java.time.LocalDateTime createdAt,String paymentMethod,String paymentStatus) {}

    @PostMapping("/quote")
    public ResponseEntity<?> quote(@RequestBody QuoteRequest request,HttpSession session){
        try{return ResponseEntity.ok(saleService.quote(request.productCodes(),request.quantities(),request.discount(),access.require(session,"OWNER","ADMIN","SALES","STAFF")));}
        catch(IllegalArgumentException|ArithmeticException e){return ResponseEntity.badRequest().body(java.util.Map.of("message",e.getMessage()));}
    }
    public record QuoteRequest(List<String> productCodes,List<Integer> quantities,com.kongklang.service.SalePricing.Discount discount){}

    @GetMapping("/{id}/allocations")
    public ResponseEntity<?> allocations(@PathVariable Long id,HttpSession session) {
        var actor=access.require(session,"OWNER","ADMIN","ACCOUNTING");
        if(saleService.getSaleById(id,actor.storeId())==null)return ResponseEntity.notFound().build();
        return ResponseEntity.ok(saleService.getAllocations(id,actor.storeId()));
    }

    public static class SaleRequest {
        private com.kongklang.service.CheckoutTerms terms;
        public com.kongklang.service.CheckoutTerms getTerms(){return terms;}
        public void setTerms(com.kongklang.service.CheckoutTerms value){terms=value;}
        private String requestKey;
        public String getRequestKey(){return requestKey;}
        public void setRequestKey(String value){requestKey=value;}


        private List<String> productCodes;

        private List<Integer> quantities;


        public SaleRequest() {
        }


        public List<String> getProductCodes() {
            return productCodes;
        }


        public void setProductCodes(
                List<String> productCodes) {

            this.productCodes = productCodes;
        }


        public List<Integer> getQuantities() {
            return quantities;
        }


        public void setQuantities(
                List<Integer> quantities) {

            this.quantities = quantities;
        }
    }
}
