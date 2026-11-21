package com.kongklang.repository;
import com.kongklang.entity.InventoryOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InventoryOperationRepository extends JpaRepository<InventoryOperation,Long> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select o from InventoryOperation o where o.id=:id and o.store.id=:storeId")
 Optional<InventoryOperation> lockByIdAndStoreId(@org.springframework.data.repository.query.Param("id") Long id,@org.springframework.data.repository.query.Param("storeId") Long storeId);
 org.springframework.data.domain.Page<InventoryOperation> findByStoreIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndProductCodeContainingIgnoreCaseOrderByIdDesc(Long storeId,java.time.LocalDateTime start,java.time.LocalDateTime end,String code,org.springframework.data.domain.Pageable page);
 Optional<InventoryOperation> findByStoreIdAndRequestKey(Long storeId,String key);
 Optional<InventoryOperation> findByIdAndStoreId(Long id,Long storeId);
 List<InventoryOperation> findTop100ByStoreIdOrderByIdDesc(Long storeId);
}
