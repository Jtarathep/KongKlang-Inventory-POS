package com.kongklang.repository;
import com.kongklang.entity.GoodsReceipt;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt,Long> {
    List<GoodsReceipt> findByStoreIdOrderByReceivedAtDesc(Long storeId);
    Optional<GoodsReceipt> findByStoreIdAndRequestKey(Long storeId,String requestKey);
    Optional<GoodsReceipt> findByIdAndStoreId(Long id,Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GoodsReceipt r where r.id=:id and r.store.id=:storeId")
    Optional<GoodsReceipt> lockByIdAndStoreId(@Param("id") Long id,@Param("storeId") Long storeId);
}
