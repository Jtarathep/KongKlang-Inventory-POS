package com.kongklang.service;

import com.kongklang.entity.Category;
import com.kongklang.entity.Store;
import com.kongklang.repository.CategoryRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * จัดการหมวดหมู่แยกตามกิจการที่ Login อยู่.
 * ทุก operation ต้องใช้ storeId จาก session ฝั่ง server เท่านั้น.
 */
@Service
@Transactional
public class CategoryService {
    private AuditTrail audit;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setAuditTrail(AuditTrail value){this.audit=value;}


    private final CategoryRepository categoryRepository;
    private final StoreContextService storeContextService;
    private final com.kongklang.repository.ProductRepository products;

    public CategoryService(CategoryRepository categoryRepository,
                           StoreContextService storeContextService, com.kongklang.repository.ProductRepository products) {
        this.categoryRepository = categoryRepository;
        this.storeContextService = storeContextService;
        this.products=products;
    }

    private Long requireStoreId(HttpSession session) {
        if (session == null) {
            throw new IllegalStateException("ไม่พบ Session ของผู้ใช้งาน");
        }
        Long storeId = storeContextService.getCurrentStoreId(session);
        if (storeId == null) {
            throw new IllegalStateException("ไม่พบกิจการที่กำลังใช้งาน กรุณาเข้าสู่ระบบใหม่");
        }
        return storeId;
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories(HttpSession session) {
        Long storeId = requireStoreId(session);
        return categoryRepository.findAllByStoreIdOrderByNameAsc(storeId);
    }

    @Transactional(readOnly = true)
    public Category getCategoryById(Long id, HttpSession session) {
        if (id == null) return null;
        Long storeId = requireStoreId(session);
        return categoryRepository.findByIdAndStoreId(id, storeId).orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean existsByNameForStore(String name, HttpSession session) {
        if (name == null || name.trim().isEmpty()) return false;
        Long storeId = requireStoreId(session);
        return categoryRepository.existsByStoreIdAndNameIgnoreCase(storeId, name.trim());
    }

    public Category saveCategory(Category category, HttpSession session) {
        if (category == null) {
            throw new IllegalArgumentException("ไม่พบข้อมูลหมวดหมู่");
        }

        Long storeId = requireStoreId(session);
        if(audit!=null)audit.record(new AccessService.Actor(((Number)session.getAttribute("userId")).longValue(),storeId,String.valueOf(session.getAttribute("role"))),"CATEGORY_SAVE",String.valueOf(category.getName()));
        String name = category.getName() == null ? "" : category.getName().trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("กรุณาระบุชื่อหมวดหมู่");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("ชื่อหมวดหมู่ต้องไม่เกิน 100 ตัวอักษร");
        }

        Category duplicate = categoryRepository
                .findByStoreIdAndNameIgnoreCase(storeId, name)
                .orElse(null);

        if (duplicate != null && !duplicate.getId().equals(category.getId())) {
            throw new IllegalArgumentException("ร้านของคุณมีหมวดหมู่ '" + name + "' อยู่แล้ว");
        }

        if (category.getId() != null) {
            Category current = categoryRepository
                    .findByIdAndStoreId(category.getId(), storeId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ไม่พบหมวดหมู่ในร้านปัจจุบัน หรือคุณไม่มีสิทธิ์แก้ไข"
                    ));
            String oldName=current.getName();
            current.setName(name);
            products.renameCategory(storeId,oldName,name);
            return categoryRepository.save(current);
        }

        Category newCategory = new Category();
        newCategory.setName(name);
        Store store = new Store();
        store.setId(storeId);
        newCategory.setStore(store);
        return categoryRepository.save(newCategory);
    }

    public void deleteCategory(Long id, HttpSession session) {
        Long storeId = requireStoreId(session);
        Category category = categoryRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "ไม่พบหมวดหมู่ในร้านปัจจุบัน หรือคุณไม่มีสิทธิ์ลบ"
                ));
        if(products.existsByStoreIdAndCategoryIgnoreCase(storeId,category.getName()))
            throw new IllegalArgumentException("หมวดหมู่นี้ยังมีสินค้า กรุณาย้ายหมวดหมู่สินค้าก่อนลบ");
        if(audit!=null)audit.record(new AccessService.Actor(((Number)session.getAttribute("userId")).longValue(),storeId,String.valueOf(session.getAttribute("role"))),"CATEGORY_DELETE",category.getName());
        categoryRepository.delete(category);
    }

    @Transactional(readOnly = true)
    public long countCategories(HttpSession session) {
        return categoryRepository.countByStoreId(requireStoreId(session));
    }
}
