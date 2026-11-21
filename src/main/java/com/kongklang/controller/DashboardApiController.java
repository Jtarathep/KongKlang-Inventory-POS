package com.kongklang.controller;

import com.kongklang.service.ProductService;
import com.kongklang.service.SaleService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** AJAX data endpoint for refreshing the dashboard without reloading the page. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {
    private final ProductService productService;
    private final SaleService saleService;
    private final StoreContextService storeContextService;

    public DashboardApiController(ProductService productService, SaleService saleService,
                                  StoreContextService storeContextService) {
        this.productService = productService;
        this.saleService = saleService;
        this.storeContextService = storeContextService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(HttpSession session) {
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("countProducts", productService.countProducts(session));
        response.put("countLowStock", productService.countLowStock(session));
        response.put("countOutOfStock", productService.countOutOfStock(session));
        response.put("inventoryValue", productService.getInventoryValue(session));
        response.put("todayRevenue", saleService.getTodayRevenue(storeId));
        response.put("weekRevenue", saleService.getWeekRevenue(storeId));
        response.put("monthRevenue", saleService.getMonthRevenue(storeId));
        response.put("yearRevenue", saleService.getYearRevenue(storeId));

        List<Map<String, Object>> chartRows = new ArrayList<>();
        for (Object[] row : saleService.getLast7DaysSummary(storeId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", String.valueOf(row[0]));
            item.put("revenue", row[1]);
            item.put("cost", row[2]);
            item.put("profit", row[3]);
            chartRows.add(item);
        }
        response.put("last7DaysSummary", chartRows);
        return ResponseEntity.ok(response);
    }
}
