
package com.kongklang.repository;

import com.kongklang.entity.StockBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockBatchRepository
        extends JpaRepository<StockBatch, Long> {

    // ล็อตที่พร้อมขายของร้าน เรียง FEFO
    @Query("""
        SELECT b
        FROM StockBatch b
        WHERE b.store.id = :storeId
          AND b.productCode = :productCode
          AND b.remainingQuantity > 0
        ORDER BY
          CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
          b.expiryDate ASC,
          b.receivedAt ASC
        """)
    List<StockBatch> findAvailableBatchesForSale(
            @Param("storeId") Long storeId,
            @Param("productCode") String productCode
    );

    // ล็อตทั้งหมดของสินค้าในร้าน
    List<StockBatch> findByStoreIdAndProductCodeOrderByReceivedAtAsc(
            Long storeId,
            String productCode
    );

    // ล็อตทั้งหมดของร้าน
    List<StockBatch> findAllByStoreId(Long storeId);

    List<StockBatch> findByStoreIdOrderByReceivedAtDesc(Long storeId, org.springframework.data.domain.Pageable pageable);
}
