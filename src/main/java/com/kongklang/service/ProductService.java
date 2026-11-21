package com.kongklang.service;

import com.kongklang.entity.Product;
import com.kongklang.entity.Store;
import com.kongklang.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}


    private final InventoryOperationService inventoryOperations;
    private final AccessService access;
    private final com.kongklang.repository.StockBatchRepository stockBatchRepository;
    private final com.kongklang.repository.StockTransactionRepository stockTransactionRepository;
    private final ProductRepository productRepository;
    private final StoreContextService storeContextService;
    private final CategoryService categoryService;

    public ProductService(
            ProductRepository productRepository,
            StoreContextService storeContextService,
            CategoryService categoryService, InventoryOperationService inventoryOperations, AccessService access,
            com.kongklang.repository.StockBatchRepository stockBatchRepository,
            com.kongklang.repository.StockTransactionRepository stockTransactionRepository) {
        this.inventoryOperations = inventoryOperations;
        this.access = access;
        this.stockBatchRepository = stockBatchRepository;
        this.stockTransactionRepository = stockTransactionRepository;

        this.productRepository = productRepository;
        this.storeContextService = storeContextService;
        this.categoryService = categoryService;
    }

    private void validateProduct(Product p) {
        if(p==null)throw new IllegalArgumentException("ไม่พบข้อมูลสินค้า");
        p.setName(com.kongklang.service.PurchasingService.text(p.getName(),255,"ชื่อสินค้า"));
        p.setCode(com.kongklang.service.PurchasingService.text(p.getCode(),255,"รหัสสินค้า"));
        p.setBarcode(p.getBarcode()==null||p.getBarcode().isBlank()?null:com.kongklang.service.PurchasingService.text(p.getBarcode(),255,"บาร์โค้ด"));
        if(p.getUnit()!=null&&p.getUnit().length()>255)throw new IllegalArgumentException("หน่วยนับยาวเกินไป");
        if(p.getDescription()!=null&&p.getDescription().length()>2000)throw new IllegalArgumentException("รายละเอียดต้องไม่เกิน 2000 ตัวอักษร");
        if(p.getActive()==null)p.setActive(true);
        com.kongklang.support.DecimalMoney.required(p.getPriceValue(),"ราคาขาย");
    }

    // ==================================================
    // ดึง Store ปัจจุบัน
    // ==================================================

    private Long getCurrentStoreId(HttpSession session) {

        if (session == null) {
            throw new IllegalStateException(
                    "ไม่พบ Session ของผู้ใช้งาน"
            );
        }

        Long storeId =
                storeContextService.getCurrentStoreId(session);

        if (storeId == null) {
            throw new IllegalStateException(
                    "ไม่พบ Store ที่กำลังใช้งาน"
            );
        }

        return storeId;
    }

    // ==================================================
    // ดึง HTTP Session ปัจจุบัน
    // สำหรับโค้ดเดิมที่ยังไม่ได้ส่ง Session
    // ==================================================

    private HttpSession getCurrentSession() {

        ServletRequestAttributes attributes =
                (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();

        if (attributes == null) {
            throw new IllegalStateException(
                    "ไม่พบ HTTP Request ปัจจุบัน"
            );
        }

        HttpSession session =
                attributes.getRequest().getSession(false);

        if (session == null) {
            throw new IllegalStateException(
                    "ไม่พบ Session ของผู้ใช้งาน"
            );
        }

        return session;
    }

    // ==================================================
    // ตรวจสอบว่าหมวดหมู่เป็นของร้านปัจจุบัน
    // ==================================================

    private void validateCategoryForStore(String categoryName, HttpSession session) {
        if (categoryName == null || categoryName.trim().isEmpty()) {
            throw new IllegalArgumentException("กรุณาเลือกหมวดหมู่สินค้าของร้านคุณ");
        }
        if (!categoryService.existsByNameForStore(categoryName.trim(), session)) {
            throw new IllegalArgumentException("ไม่พบหมวดหมู่นี้ในร้านปัจจุบัน กรุณาเลือกหมวดหมู่ของร้านคุณ");
        }
    }

    // ==================================================
    // ดึงสินค้าทั้งหมดของ Store ปัจจุบัน
    // ==================================================

    public List<Product> getAllProducts(
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        return productRepository.findAllByStoreId(storeId);
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public List<Product> getAllProducts() {

        return getAllProducts(
                getCurrentSession()
        );
    }

    // ==================================================
    // เพิ่มสินค้าใหม่
    // หรือเพิ่มจำนวนสินค้าที่มีรหัสเดิม
    // ==================================================

    @Transactional
    public void saveProduct(Product product, HttpSession session) {
        Long storeId = getCurrentStoreId(session);
        validateProduct(product);
        validateCategoryForStore(product.getCategory(), session);
        if (product.getId() != null) throw new IllegalArgumentException("การเพิ่มสินค้าไม่ต้องระบุ ID");
        if (product.getCode() == null || product.getCode().isBlank()) throw new IllegalArgumentException("กรุณาระบุรหัสสินค้า");
        product.setCode(product.getCode().trim());
        if (productRepository.existsByStoreIdAndCode(storeId, product.getCode()))
            throw new IllegalArgumentException("รหัสสินค้านี้มีอยู่แล้วในร้าน กรุณาใช้เมนูรับสินค้าเข้า");
        int opening = product.getQuantity() == null ? 0 : product.getQuantity();
        if (opening < 0) throw new IllegalArgumentException("จำนวนสินค้าต้องไม่ติดลบ");
        Store store = new Store();
        store.setId(storeId);
        product.setStore(store);
        product.setQuantity(0);
        if (product.getBarcode() != null && product.getBarcode().isBlank()) product.setBarcode(null);
        product.setVersion(null);
        productRepository.saveAndFlush(product);
        if(audit!=null)audit.record(access.require(session,"OWNER","ADMIN"),"PRODUCT_CREATE",product.getCode());
        if (opening > 0) inventoryOperations.opening(access.require(session,"OWNER","ADMIN"),product.getCode(),opening,
                product.getCostPriceValue(),product.getId());
    }

    public Product getProductById(
            Long id,
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        return productRepository
                .findByIdAndStoreId(
                        id,
                        storeId
                )
                .orElse(null);
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public Product getProductById(Long id) {

        return getProductById(
                id,
                getCurrentSession()
        );
    }

    // ==================================================
    // ค้นหาตามรหัสสินค้า
    // ==================================================

    public Product getProductByCode(
            String code,
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        return productRepository
                .findByStoreIdAndCode(
                        storeId,
                        code
                )
                .orElse(null);
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public Product getProductByCode(String code) {

        return getProductByCode(
                code,
                getCurrentSession()
        );
    }

    // ==================================================
    // ค้นหาตาม Barcode
    // ==================================================

    public Product getProductByBarcode(
            String barcode,
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        return productRepository
                .findByStoreIdAndBarcode(
                        storeId,
                        barcode
                )
                .orElse(null);
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public Product getProductByBarcode(String barcode) {

        return getProductByBarcode(
                barcode,
                getCurrentSession()
        );
    }

    // ==================================================
    // ลบสินค้า
    // ==================================================

    @Transactional
    public void deleteProduct(
            Long id,
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        Product product =
                productRepository
                        .findForUpdateByIdAndStoreId(
                                id,
                                storeId
                        )
                        .orElse(null);

        if (product != null) {
            if ((product.getQuantity() != null && product.getQuantity() != 0)
                    || !stockBatchRepository.findByStoreIdAndProductCodeOrderByReceivedAtAsc(storeId, product.getCode()).isEmpty()
                    || stockTransactionRepository.existsByStoreIdAndProductCode(storeId, product.getCode()))
                throw new IllegalArgumentException("ไม่สามารถลบสินค้าที่มีสต็อกหรือประวัติการเคลื่อนไหว");
            if(audit!=null)audit.record(access.require(session,"OWNER","ADMIN"),"PRODUCT_DELETE",product.getCode());
            productRepository.delete(product);
        }
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public void deleteProduct(Long id) {

        deleteProduct(
                id,
                getCurrentSession()
        );
    }

    // ==================================================
    // แก้ไขสินค้า
    // ==================================================

    @Transactional
    public void updateProduct(
            Long id,
            Product product,
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        validateProduct(product);
        validateCategoryForStore(product.getCategory(), session);

        Product existingProduct =
                productRepository
                        .findForUpdateByIdAndStoreId(
                                id,
                                storeId
                        )
                        .orElse(null);

        if (existingProduct == null) {

            throw new IllegalArgumentException(
                    "ไม่พบสินค้าใน Store ปัจจุบัน"
            );
        }

        if (!java.util.Objects.equals(existingProduct.getCode(), product.getCode()))
            throw new IllegalArgumentException("ไม่สามารถเปลี่ยนรหัสสินค้าที่สร้างแล้ว");
        if (product.getQuantity() != null && !java.util.Objects.equals(existingProduct.getQuantity(), product.getQuantity()))
            throw new IllegalArgumentException("กรุณาเปลี่ยนจำนวนผ่านเมนูปรับสต็อกเพื่อบันทึกล็อตและประวัติ");

        existingProduct.setDescription(product.getDescription());
        existingProduct.setActive(product.getActive());
        existingProduct.setBarcode(
                product.getBarcode()
        );

        existingProduct.setName(
                product.getName()
        );



        existingProduct.setPriceValue(
                product.getPriceValue()
        );

        existingProduct.setCategory(
                product.getCategory()
        );

        existingProduct.setUnit(
                product.getUnit()
        );

        existingProduct.setCostPriceValue(
                product.getCostPriceValue()
        );

        if(audit!=null)audit.record(access.require(session,"OWNER","ADMIN"),"PRODUCT_UPDATE",existingProduct.getCode());
        // Store เดิมต้องไม่ถูกเปลี่ยน
        productRepository.save(
                existingProduct
        );
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public void updateProduct(
            Product product) {

        if (product == null
                || product.getId() == null) {

            throw new IllegalArgumentException(
                    "ไม่พบ ID ของสินค้า"
            );
        }

        updateProduct(
                product.getId(),
                product,
                getCurrentSession()
        );
    }

    // ==================================================
    // จำนวนสินค้าทั้งหมด
    // ==================================================

    public long countProducts(
            HttpSession session) {

        Long storeId =
                getCurrentStoreId(session);

        return productRepository
                .countByStoreId(storeId);
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public long countProducts() {

        return countProducts(
                getCurrentSession()
        );
    }

    // ==================================================
    // จำนวนสินค้าใกล้หมด
    // 1 - 9 ชิ้น
    // ==================================================

    public long countLowStock(
            HttpSession session) {

        return getAllProducts(session)
                .stream()
                .filter(product ->
                        product.getQuantity() != null
                                && product.getQuantity() > 0
                                && product.getQuantity() < 10)
                .count();
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public long countLowStock() {

        return countLowStock(
                getCurrentSession()
        );
    }

    // ==================================================
    // จำนวนสินค้าหมด
    // ==================================================

    public long countOutOfStock(
            HttpSession session) {

        return getAllProducts(session)
                .stream()
                .filter(product ->
                        product.getQuantity() != null
                                && product.getQuantity() == 0)
                .count();
    }

    // ==================================================
    // Compatibility Method
    // ==================================================

    public long countOutOfStock() {

        return countOutOfStock(
                getCurrentSession()
        );
    }

    // ==================================================
    // มูลค่าสินค้าคงคลัง
    // ==================================================

    public double getInventoryValue(HttpSession session) {
        Long storeId=getCurrentStoreId(session);
        return stockBatchRepository.findAllByStoreId(storeId).stream()
            .map(b->com.kongklang.support.DecimalMoney.required(b.getRemainingValue(),"มูลค่าล็อตคงเหลือ"))
            .reduce(java.math.BigDecimal.ZERO,java.math.BigDecimal::add).doubleValue();
    }

    public double getInventoryValue() {

        return getInventoryValue(
                getCurrentSession()
        );
    }
}
