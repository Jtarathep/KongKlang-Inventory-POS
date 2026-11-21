package com.kongklang.controller;

import com.kongklang.entity.Product;
import com.kongklang.entity.Sale;
import com.kongklang.service.SaleService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class SaleController {

    private final SaleService saleService;
    private final com.kongklang.service.AccessService access;
    private final StoreContextService storeContextService;

    public SaleController(SaleService saleService, StoreContextService storeContextService, com.kongklang.service.AccessService access) {
        this.access=access;
        this.saleService = saleService;
        this.storeContextService = storeContextService;
    }

    @GetMapping("/sales")
    public String salesPage(Model model) {
        model.addAttribute("checkoutRequestKey",java.util.UUID.randomUUID().toString());
        return "sales";
    }

    @GetMapping("/sales/product")
    @ResponseBody
    public Product getProduct(@RequestParam String code, HttpSession session) {
        return saleService.getProductByCode(code, storeContextService.getCurrentStoreId(session));
    }

    @PostMapping("/sales/create")
    public String createSale(
            @RequestParam String requestKey,
            @RequestParam List<String> productCodes,
            @RequestParam List<Integer> quantities,
            HttpSession session) {

        Sale sale=saleService.checkout(requestKey,productCodes,quantities,access.require(session,"OWNER","ADMIN","SALES","STAFF"));
        return "redirect:/sales/"+sale.getId()+"/invoice";
    }

    @GetMapping("/sales/history")
    public String salesHistory(Model model, HttpSession session) {

        List<Sale> sales =
                saleService.getAllSales(storeContextService.getCurrentStoreId(session));

        System.out.println(
                "========== SALES HISTORY =========="
        );

        System.out.println(
                "จำนวนบิลทั้งหมด = "
                        + sales.size()
        );

        for (Sale sale : sales) {

            System.out.println(
                    "ID = " + sale.getId()
                            + " | Bill = "
                            + sale.getSaleNumber()
                            + " | Total = "
                            + sale.getTotalAmount()
            );
        }

        System.out.println(
                "==================================="
        );

        model.addAttribute(
                "sales",
                sales
        );

        return "sales-history";
    }

    @GetMapping("/sales/{id}")
    public String saleDetail(
            @PathVariable Long id,
            Model model, HttpSession session) {

        Sale sale =
                saleService.getSaleById(id, storeContextService.getCurrentStoreId(session));

        if (sale == null) {
            return "redirect:/sales/history";
        }

        model.addAttribute(
                "sale",
                sale
        );

        model.addAttribute(
                "items",
                saleService.getSaleItems(id, storeContextService.getCurrentStoreId(session))
        );

        return "sale-detail";
    }

    // ==========================================
    // Invoice สำหรับพิมพ์เป็นเอกสาร A4
    // ==========================================

    @GetMapping("/sales/{id}/invoice")
    public String saleInvoice(
            @PathVariable Long id,
            Model model, HttpSession session) {

        Sale sale =
                saleService.getSaleById(id, storeContextService.getCurrentStoreId(session));

        if (sale == null) {
            return "redirect:/sales/history";
        }

        model.addAttribute(
                "sale",
                sale
        );

        model.addAttribute(
                "items",
                saleService.getSaleItems(id, storeContextService.getCurrentStoreId(session))
        );

        return "sale-invoice";
    }
}