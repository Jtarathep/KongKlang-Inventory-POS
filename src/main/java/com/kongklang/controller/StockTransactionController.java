package com.kongklang.controller;

import com.kongklang.service.ProductService;
import com.kongklang.service.StockBatchService;
import com.kongklang.service.StockTransactionService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.math.BigDecimal;
import com.kongklang.service.AccessService;
import com.kongklang.service.InventoryOperationService;

@Controller
public class StockTransactionController {

    private final AccessService access;
    private final InventoryOperationService operations;
    private final StockTransactionService stockTransactionService;
    private final ProductService productService;
    private final StockBatchService stockBatchService;
    private final StoreContextService storeContextService;

    public StockTransactionController(
            StockTransactionService stockTransactionService,
            ProductService productService,
            StockBatchService stockBatchService,
            StoreContextService storeContextService, AccessService access, InventoryOperationService operations) {
        this.access=access; this.operations=operations;

        this.stockTransactionService = stockTransactionService;
        this.productService = productService;
        this.stockBatchService = stockBatchService;
        this.storeContextService = storeContextService;
    }

    // ==========================================
    // หน้าสต๊อก
    // ==========================================

    @GetMapping("/stock")
    public String stockPage(Model model, HttpSession session) {

        model.addAttribute(
                "products",
                productService.getAllProducts(session)
        );

        return "stock";
    }


    // ==========================================
    // หน้ารับสินค้าเข้า
    // ==========================================

    @GetMapping("/stock/in")
    public String stockInPage(Model model, HttpSession session) {
        model.addAttribute("inventoryRequestKey",java.util.UUID.randomUUID().toString());

        model.addAttribute(
                "products",
                productService.getAllProducts(session)
        );

        return "stock-in";
    }


    // ==========================================
    // รับสินค้าเข้า
    // ==========================================

    @PostMapping("/stock/in")
    public String stockIn(@RequestParam String requestKey, @RequestParam String code, @RequestParam Integer quantity, @RequestParam String note, @RequestParam BigDecimal purchaseCost, @RequestParam(required=false) BigDecimal sellingPrice, @RequestParam(required=false) String expiryDate, HttpSession session) {
        operations.execute(access.require(session,"OWNER","ADMIN","WAREHOUSE"),
            new InventoryOperationService.Input(requestKey,"RECEIPT",code,quantity,null,null,purchaseCost,sellingPrice,expiryDate==null||expiryDate.isBlank()?null:LocalDate.parse(expiryDate),note));
        return "redirect:/stock/history";
    }

    @GetMapping("/stock/out")
    public String stockOutPage(Model model, HttpSession session) {
        model.addAttribute("inventoryRequestKey",java.util.UUID.randomUUID().toString());

        model.addAttribute(
                "products",
                productService.getAllProducts(session)
        );

        return "stock-out";
    }


    // ==========================================
    // สินค้าออก
    // ==========================================

    @PostMapping("/stock/out")
    public String stockOut(@RequestParam String requestKey, @RequestParam String code, @RequestParam Integer quantity, @RequestParam String note,  HttpSession session) {
        operations.execute(access.require(session,"OWNER","ADMIN","WAREHOUSE"),
            new InventoryOperationService.Input(requestKey,"OUT",code,quantity,null,null,null,null,null,note));
        return "redirect:/stock/history";
    }

    @GetMapping("/stock/adjust")
    public String adjustStockPage(Model model, HttpSession session) {
        model.addAttribute("inventoryRequestKey",java.util.UUID.randomUUID().toString());

        model.addAttribute(
                "products",
                productService.getAllProducts(session)
        );

        return "stock-adjust";
    }


    // ==========================================
    // ปรับยอดสินค้า
    // ==========================================

    @PostMapping("/stock/adjust")
    public String adjustStock(@RequestParam String requestKey, @RequestParam String code, @RequestParam Integer quantity, @RequestParam String note, @RequestParam Integer expectedQuantity, @RequestParam Long expectedVersion, HttpSession session) {
        operations.execute(access.require(session,"OWNER","ADMIN","WAREHOUSE"),
            new InventoryOperationService.Input(requestKey,"ADJUST",code,quantity,expectedQuantity,expectedVersion,null,null,null,note));
        return "redirect:/stock/history";
    }

    @GetMapping("/stock/batches")
    public String stockBatches(Model model, HttpSession session) {

        model.addAttribute(
                "batches",
                stockBatchService.getAllBatches(storeContextService.getCurrentStoreId(session))
        );

        return "stock-batches";
    }


    // ==========================================
    // ประวัติสต๊อก
    // ==========================================

    @GetMapping("/stock/history")
    public String history(Model model, HttpSession session) {

        model.addAttribute(
                "transactions",
                stockTransactionService.getAllTransactions(storeContextService.getCurrentStoreId(session))
        );

        return "stock-history";
    }
}