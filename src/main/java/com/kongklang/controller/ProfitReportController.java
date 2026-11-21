package com.kongklang.controller;

import com.kongklang.service.ProfitReportService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class ProfitReportController {

    private final ProfitReportService profitReportService;
    private final StoreContextService storeContextService;

    public ProfitReportController(
            ProfitReportService profitReportService,
            StoreContextService storeContextService) {

        this.profitReportService = profitReportService;
        this.storeContextService = storeContextService;
    }

    // ==========================================================
    // หน้ารายงานกำไร
    // ==========================================================

    @GetMapping("/reports/profit")
    public String profitReport(

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            Model model, HttpSession session) {

        // ถ้าไม่ได้เลือกวันที่
        // ให้ใช้เดือนปัจจุบัน

        LocalDate today =
                LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));

        if (startDate == null) {
            startDate =
                    today.withDayOfMonth(1);
        }

        if (endDate == null) {
            endDate = today;
        }

        // ป้องกันวันที่สิ้นสุดน้อยกว่าวันเริ่มต้น
        if (endDate.isBefore(startDate)) {
            endDate = startDate;
        }

        ProfitReportService.ReportResult report =
                profitReportService.generateReport(
                        startDate,
                        endDate,
                        storeContextService.getCurrentStoreId(session)
                );

        model.addAttribute(
                "report",
                report
        );

        return "profit-report";
    }
}