package com.kongklang.repository;
import com.kongklang.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder,Long> {
    Optional<PurchaseOrder> findByStoreIdAndRequestKey(Long storeId,String requestKey);
    List<PurchaseOrder> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    Optional<PurchaseOrder> findByIdAndStoreId(Long id,Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id=:id and p.store.id=:storeId")
    Optional<PurchaseOrder> lockByIdAndStoreId(@Param("id") Long id,@Param("storeId") Long storeId);
}
