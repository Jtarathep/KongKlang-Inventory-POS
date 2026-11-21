package com.kongklang.repository;
import com.kongklang.entity.SaleAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SaleAllocationRepository extends JpaRepository<SaleAllocation,Long> {
    List<SaleAllocation> findBySaleItemSaleIdAndStoreIdOrderByIdAsc(Long saleId,Long storeId);
}
