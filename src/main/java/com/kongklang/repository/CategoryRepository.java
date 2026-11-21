package com.kongklang.repository;

import com.kongklang.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * หมวดหมู่เป็นข้อมูลแยกตามกิจการ (store).
 * ชื่อเดียวกันสามารถมีได้ในคนละร้าน แต่ห้ามซ้ำภายในร้านเดียวกัน.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByStoreIdOrderByNameAsc(Long storeId);

    Optional<Category> findByIdAndStoreId(Long id, Long storeId);

    Optional<Category> findByStoreIdAndNameIgnoreCase(Long storeId, String name);

    boolean existsByStoreIdAndNameIgnoreCase(Long storeId, String name);

    long countByStoreId(Long storeId);
}
