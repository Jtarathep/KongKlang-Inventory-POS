package com.kongklang.controller;

import com.kongklang.service.SaleService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportApiController {

    private final SaleService saleService;
    private final StoreContextService storeContextService;

    public ReportApiController(SaleService saleService, StoreContextService storeContextService) {
        this.saleService = saleService;
        this.storeContextService = storeContextService;
    }


    private Long activeStoreId(HttpSession session) {
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "ไม่พบร้านค้าที่กำลังใช้งาน");
        }
        return storeId;
    }

    // ==============================
    // รายรับวันนี้
    // GET /api/reports/today
    // ==============================

    @GetMapping("/today")
    public ResponseEntity<Double> getTodayRevenue(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getTodayRevenue(activeStoreId(session))
        );
    }


    // ==============================
    // รายรับสัปดาห์นี้
    // GET /api/reports/week
    // ==============================

    @GetMapping("/week")
    public ResponseEntity<Double> getWeekRevenue(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getWeekRevenue(activeStoreId(session))
        );
    }


    // ==============================
    // รายรับเดือนนี้
    // GET /api/reports/month
    // ==============================

    @GetMapping("/month")
    public ResponseEntity<Double> getMonthRevenue(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getMonthRevenue(activeStoreId(session))
        );
    }


    // ==============================
    // รายรับปีนี้
    // GET /api/reports/year
    // ==============================

    @GetMapping("/year")
    public ResponseEntity<Double> getYearRevenue(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getYearRevenue(activeStoreId(session))
        );
    }


    // ==============================
    // ยอดขายย้อนหลัง 7 วัน
    // GET /api/reports/last-7-days
    // ==============================

    @GetMapping("/last-7-days")
    public ResponseEntity<Map<String, Double>> getLast7DaysRevenue(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getLast7DaysRevenue(activeStoreId(session))
        );
    }


    // ==============================
    // สินค้าขายดี
    // GET /api/reports/best-selling
    // ==============================

    @GetMapping("/best-selling")
    public ResponseEntity<List<Object[]>> getBestSellingProducts(HttpSession session) {

        return ResponseEntity.ok(
                saleService.getBestSellingProducts(activeStoreId(session))
        );
    }
}