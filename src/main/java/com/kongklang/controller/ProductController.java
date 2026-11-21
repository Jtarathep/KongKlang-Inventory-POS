package com.kongklang.controller;

import com.kongklang.entity.Product;
import com.kongklang.entity.StockBatch;
import com.kongklang.service.CategoryService;
import com.kongklang.service.ProductService;
import com.kongklang.service.SaleService;
import com.kongklang.service.StockTransactionService;
import com.kongklang.service.StockBatchService;
import com.kongklang.service.StoreContextService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ProductController {

    private final ProductService productService;
    private final com.kongklang.service.ProductPhotoService photos;
    private final CategoryService categoryService;
    private final SaleService saleService;
    private final StockTransactionService stockTransactionService;
    private final StockBatchService stockBatchService;
    private final StoreContextService storeContextService;

    public ProductController(
            ProductService productService,
            CategoryService categoryService,
            SaleService saleService,
            StockTransactionService stockTransactionService,
            StockBatchService stockBatchService,
            StoreContextService storeContextService, com.kongklang.service.ProductPhotoService photos) {
        this.photos=photos;

        this.productService = productService;
        this.categoryService = categoryService;
        this.saleService = saleService;
        this.stockTransactionService = stockTransactionService;
        this.stockBatchService = stockBatchService;
        this.storeContextService = storeContextService;
    }


    // ==================================================
    // Dashboard
    // ==================================================

    @GetMapping("/")
    public String home(
            Model model,
            HttpSession session) {

        // ==================================================
        // จำนวนสินค้าทั้งหมด
        // ==================================================

        model.addAttribute(
                "countProducts",
                productService.countProducts(session)
        );


        // ==================================================
        // จำนวนสินค้าใกล้หมด
        // ==================================================

        model.addAttribute(
                "countLowStock",
                productService.countLowStock(session)
        );


        // ==================================================
        // รายการสินค้าใกล้หมด
        // ==================================================

        model.addAttribute(
                "lowStockProducts",
                productService.getAllProducts(session)
                        .stream()
                        .filter(product ->
                                product.getQuantity() != null
                                        && product.getQuantity() > 0
                                        && product.getQuantity() < 10)
                        .toList()
        );


        // ==================================================
        // จำนวนสินค้าหมด
        // ==================================================

        model.addAttribute(
                "countOutOfStock",
                productService.countOutOfStock(session)
        );

        // ==================================================
        // แจ้งเตือนสินค้าใกล้หมดอายุ / หมดอายุแล้ว
        // แสดงเฉพาะล็อตที่ยังมีสินค้าคงเหลือของร้านปัจจุบัน
        // ==================================================
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Bangkok"));
        LocalDate expiryThreshold = today.plusDays(30);
        Long currentStoreId = storeContextService.getCurrentStoreId(session);
        List<StockBatch> batches = currentStoreId == null
                ? List.of()
                : stockBatchService.getAllBatches(currentStoreId);

        List<StockBatch> expiredBatches = batches.stream()
                .filter(batch -> batch.getRemainingQuantity() != null
                        && batch.getRemainingQuantity() > 0)
                .filter(batch -> batch.getExpiryDate() != null
                        && batch.getExpiryDate().isBefore(today))
                .toList();

        List<StockBatch> expiringBatches = batches.stream()
                .filter(batch -> batch.getRemainingQuantity() != null
                        && batch.getRemainingQuantity() > 0)
                .filter(batch -> batch.getExpiryDate() != null
                        && !batch.getExpiryDate().isBefore(today)
                        && !batch.getExpiryDate().isAfter(expiryThreshold))
                .toList();

        model.addAttribute("expiredBatches", expiredBatches);
        model.addAttribute("expiringBatches", expiringBatches);
        model.addAttribute("countExpiredBatches", expiredBatches.size());
        model.addAttribute("countExpiringBatches", expiringBatches.size());


        // ==================================================
        // มูลค่าสินค้าคงคลัง
        // ==================================================

        model.addAttribute(
                "inventoryValue",
                productService.getInventoryValue(session)
        );


        // ==================================================
        // Dashboard รายรับ
        // ==================================================

        model.addAttribute(
                "todayRevenue",
                saleService.getTodayRevenue(currentStoreId)
        );

        model.addAttribute(
                "weekRevenue",
                saleService.getWeekRevenue(currentStoreId)
        );

        model.addAttribute(
                "monthRevenue",
                saleService.getMonthRevenue(currentStoreId)
        );

        model.addAttribute(
                "yearRevenue",
                saleService.getYearRevenue(currentStoreId)
        );


        // ==================================================
        // Dashboard กราฟย้อนหลัง 7 วัน
        // ==================================================

        model.addAttribute(
                "last7DaysSummary",
                saleService.getLast7DaysSummary(currentStoreId)
        );


        // ==================================================
        // ประวัติ Stock ล่าสุด
        // ==================================================

        model.addAttribute(
                "recentTransactions",
                stockTransactionService
                        .getAllTransactions(currentStoreId)
                        .stream()
                        .limit(5)
                        .toList()
        );


        return "dashboard";
    }


    // ==================================================
    // รายการสินค้า
    // ==================================================

    @GetMapping("/products")
    public String products(
            Model model,
            HttpSession session) {

        // ดึงสินค้าของ Store ปัจจุบัน
        List<Product> products =
                productService.getAllProducts(session);


        // ส่งรายการสินค้าทั้งหมด
        model.addAttribute(
                "products",
                products
        );


        // ==================================================
        // จัดกลุ่มสินค้าตามหมวดหมู่
        // ==================================================

        Map<String, List<Product>> groupedProducts =
                products.stream()
                        .collect(Collectors.groupingBy(
                                product -> {

                                    if (product.getCategory() == null
                                            || product.getCategory().isBlank()) {

                                        return "ไม่ระบุ";
                                    }

                                    return product.getCategory().trim();
                                },
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));


        // ==================================================
        // กำหนดลำดับหมวดหมู่
        // ==================================================

        List<String> categoryOrder = List.of(
                "เครื่องดื่ม",
                "อาหารแห้ง",
                "เครื่องปรุง",
                "ขนม",
                "ของสด",
                "แช่แข็ง",
                "ของใช้ในบ้าน",
                "อื่นๆ",
                "ไม่ระบุ"
        );


        Map<String, List<Product>> orderedProducts =
                new LinkedHashMap<>();


        // ==================================================
        // ใส่หมวดหมู่ตามลำดับที่กำหนด
        // ==================================================

        for (String category : categoryOrder) {

            if (groupedProducts.containsKey(category)) {

                orderedProducts.put(
                        category,
                        groupedProducts.get(category)
                );
            }
        }


        // ==================================================
        // หมวดหมู่อื่น ๆ ที่อาจมีในฐานข้อมูล
        // ==================================================

        for (Map.Entry<String, List<Product>> entry :
                groupedProducts.entrySet()) {

            if (!orderedProducts.containsKey(entry.getKey())) {

                orderedProducts.put(
                        entry.getKey(),
                        entry.getValue()
                );
            }
        }


        // ==================================================
        // ส่งข้อมูลที่จัดกลุ่มแล้วไปหน้า products.html
        // ==================================================

        model.addAttribute(
                "productsByCategory",
                orderedProducts
        );


        return "products";
    }


    // ==================================================
    // รายละเอียดสินค้าและล็อตสินค้า
    // ==================================================

    @GetMapping("/products/{id}/details")
    public String productDetails(
            @PathVariable Long id,
            Model model,
            HttpSession session) {

        Product product = productService.getProductById(id, session);
        if (product == null) {
            return "redirect:/products";
        }

        Long storeId = storeContextService.getCurrentStoreId(session);
        List<StockBatch> batches = storeId == null
                ? List.of()
                : stockBatchService.getAllBatches(storeId).stream()
                    .filter(batch -> product.getCode().equals(batch.getProductCode()))
                    .sorted((a, b) -> {
                        if (a.getExpiryDate() == null && b.getExpiryDate() != null) return 1;
                        if (a.getExpiryDate() != null && b.getExpiryDate() == null) return -1;
                        if (a.getExpiryDate() != null) {
                            int byExpiry = a.getExpiryDate().compareTo(b.getExpiryDate());
                            if (byExpiry != 0) return byExpiry;
                        }
                        if (a.getReceivedAt() == null) return 1;
                        if (b.getReceivedAt() == null) return -1;
                        return a.getReceivedAt().compareTo(b.getReceivedAt());
                    })
                    .toList();

        model.addAttribute("product", product);
        model.addAttribute("batches", batches);
        model.addAttribute("batchCount", batches.size());
        model.addAttribute("availableBatchCount", batches.stream()
                .filter(batch -> batch.getRemainingQuantity() != null && batch.getRemainingQuantity() > 0)
                .count());
        model.addAttribute("totalBatchQuantity", batches.stream()
                .map(StockBatch::getRemainingQuantity)
                .filter(qty -> qty != null)
                .mapToInt(Integer::intValue)
                .sum());
        return "product-details";
    }

    // ==================================================
    // หน้าเพิ่มสินค้า
    // ==================================================

    @GetMapping("/products/new")
    public String showAddForm(
            Model model,
            HttpSession session) {

        model.addAttribute(
                "product",
                new Product()
        );


        // ==================================================
        // หมวดหมู่
        // ==================================================

        model.addAttribute(
                "categories",
                categoryService.getAllCategories(session)
        );


        // ==================================================
        // สินค้าที่มีอยู่แล้ว
        // ==================================================

        model.addAttribute(
                "products",
                productService.getAllProducts(session)
        );


        return "product-form";
    }


    // ==================================================
    // ค้นหาสินค้าตามรหัส
    // ==================================================

    @GetMapping("/products/by-code")
    @ResponseBody
    public Product getProductByCode(
            @RequestParam String code,
            HttpSession session) {

        return productService.getProductByCode(
                code,
                session
        );
    }


    // ==================================================
    // ค้นหาสินค้าตาม Barcode
    // ==================================================

    @GetMapping("/products/by-barcode")
    @ResponseBody
    public Product getProductByBarcode(
            @RequestParam String barcode,
            HttpSession session) {

        return productService.getProductByBarcode(
                barcode,
                session
        );
    }


    // ==================================================
    // บันทึกสินค้า
    // ==================================================

    @PostMapping("/products/save")
    public String saveProduct(
            @ModelAttribute Product product,
            @RequestParam(required=false) org.springframework.web.multipart.MultipartFile photo,
            @RequestParam(defaultValue="false") boolean removePhoto,
            HttpSession session, Model model) {
        try {
            photos.save(product, photo, removePhoto, session);
            return "redirect:/products";
        } catch(IllegalArgumentException | org.springframework.dao.DataIntegrityViolationException error) {
            model.addAttribute("error",error instanceof IllegalArgumentException ? error.getMessage() : "รหัสสินค้าหรือบาร์โค้ดซ้ำ กรุณาตรวจสอบ");
            model.addAttribute("product",product);
            model.addAttribute("categories",categoryService.getAllCategories(session));
            model.addAttribute("products",productService.getAllProducts(session));
            return "product-form";
        }
    }


    // ==================================================
    // หน้าแก้ไขสินค้า
    // ==================================================

    @GetMapping("/products/edit/{id}")
    public String editProduct(
            @PathVariable Long id,
            Model model,
            HttpSession session) {

        Product product =
                productService.getProductById(
                        id,
                        session
                );


        if (product == null) {
            return "redirect:/products";
        }


        model.addAttribute(
                "product",
                product
        );


        model.addAttribute(
                "categories",
                categoryService.getAllCategories(session)
        );


        return "product-form";
    }


    // ==================================================
    // ลบสินค้า
    // ==================================================

    @PostMapping("/products/delete/{id}")
    public String deleteProduct(
            @PathVariable Long id,
            HttpSession session) {

        productService.deleteProduct(
                id,
                session
        );


        return "redirect:/products";
    }

}
