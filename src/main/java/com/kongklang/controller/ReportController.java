package com.kongklang.controller;

import com.kongklang.entity.Product;
import com.kongklang.entity.Sale;
import com.kongklang.entity.StockTransaction;
import com.kongklang.repository.SaleItemRepository;
import com.kongklang.service.ProductService;
import com.kongklang.service.SaleService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import com.kongklang.service.StockTransactionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
public class ReportController {

    private final ProductService productService;
    private final StockTransactionService stockTransactionService;
    private final SaleService saleService;
    private final SaleItemRepository saleItemRepository;
    private final StoreContextService storeContextService;

    public ReportController(
            ProductService productService,
            StockTransactionService stockTransactionService,
            SaleService saleService,
            SaleItemRepository saleItemRepository,
            StoreContextService storeContextService) {

        this.productService = productService;
        this.stockTransactionService = stockTransactionService;
        this.saleService = saleService;
        this.saleItemRepository = saleItemRepository;
        this.storeContextService = storeContextService;
    }


    // ==========================================================
    // หน้ารายงานหลัก
    // ==========================================================

    @GetMapping("/reports")
    public String reports(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            Model model, HttpSession session) {
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            throw new IllegalStateException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }

        // ======================================================
        // ข้อมูลสินค้า
        // ======================================================

        List<Product> products =
                productService.getAllProducts(session);

        model.addAttribute(
                "products",
                products
        );


        // ======================================================
        // สถิติสินค้า
        // ======================================================

        model.addAttribute(
                "countProducts",
                productService.countProducts(session)
        );

        model.addAttribute(
                "countLowStock",
                productService.countLowStock(session)
        );

        model.addAttribute(
                "inventoryValue",
                productService.getInventoryValue(session)
        );


        // ======================================================
        // รายรับ
        // ======================================================

        model.addAttribute(
                "todayRevenue",
                saleService.getTodayRevenue(storeId)
        );

        model.addAttribute(
                "weekRevenue",
                saleService.getWeekRevenue(storeId)
        );

        model.addAttribute(
                "monthRevenue",
                saleService.getMonthRevenue(storeId)
        );

        model.addAttribute(
                "yearRevenue",
                saleService.getYearRevenue(storeId)
        );


        // ======================================================
        // ต้นทุน
        // ======================================================

        model.addAttribute(
                "todayCost",
                saleService.getTodayCost(storeId)
        );

        model.addAttribute(
                "weekCost",
                saleService.getWeekCost(storeId)
        );

        model.addAttribute(
                "monthCost",
                saleService.getMonthCost(storeId)
        );

        model.addAttribute(
                "yearCost",
                saleService.getYearCost(storeId)
        );


        // ======================================================
        // กำไร
        // ======================================================

        model.addAttribute(
                "todayProfit",
                saleService.getTodayProfit(storeId)
        );

        model.addAttribute(
                "weekProfit",
                saleService.getWeekProfit(storeId)
        );

        model.addAttribute(
                "monthProfit",
                saleService.getMonthProfit(storeId)
        );

        model.addAttribute(
                "yearProfit",
                saleService.getYearProfit(storeId)
        );


        // ======================================================
        // รายการขายทั้งหมด
        // ======================================================

        // ======================================================
        // รายการขาย แยกตามเดือนและปี
        // ค่าเริ่มต้นเป็นเดือนและปีปัจจุบัน
        // ======================================================

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));
        int selectedMonth = (month == null || month < 1 || month > 12)
                ? today.getMonthValue()
                : month;
        int selectedYear = (year == null || year < 1)
                ? today.getYear()
                : year;

        List<Sale> allSales = saleService.getAllSales(storeId);

        TreeSet<Integer> reportYears = new TreeSet<>();
        reportYears.add(today.getYear());
        reportYears.add(selectedYear);
        for (Sale sale : allSales) {
            if (sale.getCreatedAt() != null) {
                reportYears.add(sale.getCreatedAt().getYear());
            }
        }

        // ให้เลือกปีที่มีข้อมูลจริงได้ แม้จะเป็นปีเก่า
        model.addAttribute("reportYears", reportYears);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute(
                "sales",
                saleService.getSalesByMonth(selectedYear, selectedMonth, storeId)
        );


        // ======================================================
        // ประวัติ Stock
        // ======================================================

        List<StockTransaction> transactions =
                stockTransactionService.getAllTransactions(storeId);

        model.addAttribute(
                "transactions",
                transactions
        );


        // ======================================================
        // สินค้าขายดี
        // ======================================================

        model.addAttribute(
                "bestSellingProducts",
                saleService.getBestSellingProducts(storeId)
        );


        // ======================================================
        // ยอดขายย้อนหลัง 7 วัน
        // ======================================================

        model.addAttribute(
                "last7DaysRevenue",
                saleService.getLast7DaysRevenue(storeId)
        );


        // ======================================================
        // สรุปย้อนหลัง 7 วัน
        // ======================================================

        model.addAttribute(
                "last7DaysSummary",
                saleService.getLast7DaysSummary(storeId)
        );


        return "reports";
    }



    // ==========================================================
    // AJAX: กรองรายการขายตามเดือนและปี โดยไม่โหลดหน้า reports ใหม่
    // GET /reports/sales?month=10&year=2026
    // ==========================================================

    @GetMapping("/reports/sales")
    @org.springframework.web.bind.annotation.ResponseBody
    public List<Map<String, Object>> getSalesByMonthAjax(
            @RequestParam int month,
            @RequestParam int year,
            HttpSession session) {
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            throw new IllegalStateException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }

        if (month < 1 || month > 12 || year < 1) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "เดือนหรือปีไม่ถูกต้อง"
            );
        }

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<Map<String, Object>> result = new ArrayList<>();

        for (Sale sale : saleService.getSalesByMonth(year, month, storeId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", sale.getId());
            row.put("saleNumber", sale.getSaleNumber());
            row.put("createdAt", sale.getCreatedAt() == null
                    ? "-"
                    : sale.getCreatedAt().format(formatter));
            row.put("totalAmount", sale.getTotalAmount());
            result.add(row);
        }

        return result;
    }

    // ==========================================================
    // รายงาน A4
    //
    // daily   = วันที่ -> วันที่
    // monthly = เดือน -> เดือน
    // yearly  = ปี
    // ==========================================================

    @GetMapping("/reports/print")
    public String printReport(
            @RequestParam String type,

            @RequestParam(required = false)
            String startDate,

            @RequestParam(required = false)
            String endDate,

            @RequestParam(required = false)
            String startMonth,

            @RequestParam(required = false)
            String endMonth,

            @RequestParam(required = false)
            Integer year,

            Model model, HttpSession session) {
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            throw new IllegalStateException("ไม่พบร้านค้าที่กำลังใช้งาน");
        }


        // ======================================================
        // รายงานรายวัน
        // วันที่เริ่มต้น -> วันที่สิ้นสุด
        // ======================================================

        if ("daily".equalsIgnoreCase(type)) {

            LocalDate today =
                    LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));

            LocalDate reportStart =
                    (startDate == null || startDate.isBlank())
                            ? today
                            : LocalDate.parse(startDate);

            LocalDate reportEnd =
                    (endDate == null || endDate.isBlank())
                            ? reportStart
                            : LocalDate.parse(endDate);


            // ถ้าเลือกสลับวัน
            if (reportEnd.isBefore(reportStart)) {

                LocalDate temp =
                        reportStart;

                reportStart =
                        reportEnd;

                reportEnd =
                        temp;
            }


            java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalCost = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalProfit = java.math.BigDecimal.ZERO;

            List<Object[]> rows =
                    new ArrayList<>();


            LocalDate currentDate =
                    reportStart;


            while (!currentDate.isAfter(reportEnd)) {

                SaleService.ReportSummary daily =
                        saleService.getDailyReport(
                                currentDate, storeId
                        );


                java.math.BigDecimal revenue = daily.getRevenueValue();

                java.math.BigDecimal cost = daily.getCostValue();

                java.math.BigDecimal profit = daily.getProfitValue();


                totalRevenue = totalRevenue.add(revenue);
                totalCost = totalCost.add(cost);
                totalProfit = totalProfit.add(profit);


                rows.add(
                        new Object[]{
                                currentDate,
                                revenue,
                                cost,
                                profit
                        }
                );


                currentDate =
                        currentDate.plusDays(1);
            }


            SaleService.ReportSummary summary =
                    new SaleService.ReportSummary(totalRevenue,totalCost);


            model.addAttribute(
                    "reportType",
                    "daily"
            );

            model.addAttribute(
                    "reportTitle",
                    "รายงานสรุปยอดขายรายวัน"
            );

            model.addAttribute(
                    "reportStartDate",
                    reportStart
            );

            model.addAttribute(
                    "reportEndDate",
                    reportEnd
            );

            model.addAttribute(
                    "reportPeriod",
                    formatDateRange(
                            reportStart,
                            reportEnd
                    )
            );

            model.addAttribute(
                    "summary",
                    summary
            );

            model.addAttribute(
                    "reportRows",
                    rows
            );

            return "report-print";
        }


        // ======================================================
        // รายงานรายเดือน
        // เดือนเริ่มต้น -> เดือนสิ้นสุด
        // ======================================================

        if ("monthly".equalsIgnoreCase(type)) {

            YearMonth currentMonth =
                    YearMonth.now();


            YearMonth reportStartMonth =
                    (startMonth == null || startMonth.isBlank())
                            ? currentMonth
                            : YearMonth.parse(startMonth);


            YearMonth reportEndMonth =
                    (endMonth == null || endMonth.isBlank())
                            ? reportStartMonth
                            : YearMonth.parse(endMonth);


            // ถ้าเลือกสลับเดือน
            if (reportEndMonth.isBefore(reportStartMonth)) {

                YearMonth temp =
                        reportStartMonth;

                reportStartMonth =
                        reportEndMonth;

                reportEndMonth =
                        temp;
            }


            java.math.BigDecimal totalRevenue = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalCost = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalProfit = java.math.BigDecimal.ZERO;


            List<Object[]> rows =
                    new ArrayList<>();


            YearMonth current =
                    reportStartMonth;


            while (!current.isAfter(reportEndMonth)) {

                SaleService.ReportSummary monthly =
                        saleService.getMonthlyReport(
                                current, storeId
                        );


                java.math.BigDecimal revenue = monthly.getRevenueValue();

                java.math.BigDecimal cost = monthly.getCostValue();

                java.math.BigDecimal profit = monthly.getProfitValue();


                totalRevenue = totalRevenue.add(revenue);
                totalCost = totalCost.add(cost);
                totalProfit = totalProfit.add(profit);


                rows.add(
                        new Object[]{
                                current,
                                revenue,
                                cost,
                                profit
                        }
                );


                current =
                        current.plusMonths(1);
            }


            SaleService.ReportSummary summary =
                    new SaleService.ReportSummary(totalRevenue,totalCost);


            model.addAttribute(
                    "reportType",
                    "monthly"
            );

            model.addAttribute(
                    "reportTitle",
                    "รายงานสรุปยอดขายรายเดือน"
            );

            model.addAttribute(
                    "reportStartMonth",
                    reportStartMonth
            );

            model.addAttribute(
                    "reportEndMonth",
                    reportEndMonth
            );

            model.addAttribute(
                    "reportPeriod",
                    formatMonthRange(
                            reportStartMonth,
                            reportEndMonth
                    )
            );

            model.addAttribute(
                    "summary",
                    summary
            );

            model.addAttribute(
                    "reportRows",
                    rows
            );

            return "report-print";
        }


        // ======================================================
        // รายงานรายปี
        // ======================================================

        if ("yearly".equalsIgnoreCase(type)) {

            int reportYear =
                    year == null
                            ? LocalDate.now(java.time.ZoneId.of("Asia/Bangkok")).getYear()
                            : year;


            SaleService.ReportSummary summary =
                    saleService.getYearlyReport(
                            reportYear, storeId
                    );


            model.addAttribute(
                    "reportType",
                    "yearly"
            );

            model.addAttribute(
                    "reportTitle",
                    "รายงานสรุปยอดขายรายปี"
            );

            model.addAttribute(
                    "reportYear",
                    reportYear
            );

            model.addAttribute(
                    "reportPeriod",
                    "ปี " + reportYear
            );

            model.addAttribute(
                    "summary",
                    summary
            );

            model.addAttribute(
                    "reportRows",
                    saleService.getYearlyReportRows(
                            reportYear, storeId
                    )
            );

            return "report-print";
        }


        // ======================================================
        // type ไม่ถูกต้อง
        // ======================================================

        return "redirect:/reports";
    }


    // ==========================================================
    // Format วันที่
    // ==========================================================

    private String formatDateRange(
            LocalDate start,
            LocalDate end) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "dd/MM/yyyy"
                );

        if (start.equals(end)) {

            return start.format(formatter);
        }

        return start.format(formatter)
                + " - "
                + end.format(formatter);
    }


    // ==========================================================
    // Format เดือน
    // ==========================================================

    private String formatMonthRange(
            YearMonth start,
            YearMonth end) {

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "MM/yyyy"
                );

        if (start.equals(end)) {

            return start.format(formatter);
        }

        return start.format(formatter)
                + " - "
                + end.format(formatter);
    }
}