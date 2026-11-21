package com.kongklang.repository;

import com.kongklang.entity.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    java.util.Optional<Sale> findByStoreIdAndRequestKey(Long storeId,String requestKey);

    Sale findBySaleNumber(String saleNumber);

    List<Sale> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);
    List<Sale> findByStoreIdOrderByCreatedAtDescIdDesc(Long storeId,org.springframework.data.domain.Pageable page);

    java.util.Optional<Sale> findByIdAndStoreId(Long id, Long storeId);

    List<Sale> findByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<Sale> findAllByOrderByCreatedAtDesc();

    // ใช้กับรายงาน/หน้าที่เรียกดูตามช่วงวันที่โดยไม่ระบุร้าน (legacy)
    List<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            LocalDateTime start,
            LocalDateTime end
    );

    List<Sale> findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            Long storeId,
            LocalDateTime start,
            LocalDateTime end
    );
}
