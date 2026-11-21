package com.kongklang.repository;

import com.kongklang.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleItemRepository
        extends JpaRepository<SaleItem, Long> {

    // ==========================================================
    // รายการสินค้าในบิล
    // ==========================================================

    List<SaleItem> findBySaleId(Long saleId);

    List<SaleItem> findBySaleIdAndSaleStoreId(Long saleId, Long storeId);

    List<SaleItem> findAllBySaleStoreId(Long storeId);


    // ==========================================================
    // สินค้าขายดี
    //
    // ผลลัพธ์:
    // [0] productCode
    // [1] productName
    // [2] quantity
    // [3] sales
    // [4] cost
    // [5] profit
    // ==========================================================

    @Query("""
        SELECT
            s.productCode,
            s.productName,
            SUM(s.quantity),
            SUM(s.subtotal),
            COALESCE(SUM(s.cost), 0),
            SUM(s.subtotal) - COALESCE(SUM(s.cost), 0)
        FROM SaleItem s
        GROUP BY
            s.productCode,
            s.productName
        ORDER BY
            SUM(s.quantity) DESC
    """)
    List<Object[]> findBestSellingProducts();


    // ==========================================================
    // รวมต้นทุนตามช่วงเวลา
    // ==========================================================

    @Query("""
        SELECT COALESCE(SUM(s.cost), 0)
        FROM SaleItem s
        WHERE s.sale.createdAt >= :start
          AND s.sale.createdAt < :end
    """)
    Double sumCostBetween(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}