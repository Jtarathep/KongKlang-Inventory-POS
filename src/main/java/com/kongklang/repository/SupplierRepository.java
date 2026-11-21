package com.kongklang.repository;
import com.kongklang.entity.Supplier;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface SupplierRepository extends JpaRepository<Supplier,Long> {
    List<Supplier> findByStoreIdOrderByNameAsc(Long storeId);
    Optional<Supplier> findByIdAndStoreId(Long id, Long storeId);
    boolean existsByStoreIdAndCode(Long storeId,String code);
}
