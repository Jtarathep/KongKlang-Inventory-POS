
package com.kongklang.repository;

import com.kongklang.entity.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransactionRepository
        extends JpaRepository<StockTransaction, Long> {
    boolean existsByStoreIdAndProductCode(Long storeId, String productCode);


    // ประวัติของร้าน เรียงใหม่ไปเก่า
    List<StockTransaction> findAllByStoreIdOrderByCreatedAtDesc(
            Long storeId
    );
    List<StockTransaction> findByStoreIdOrderByCreatedAtDesc(Long storeId, org.springframework.data.domain.Pageable pageable);
}
