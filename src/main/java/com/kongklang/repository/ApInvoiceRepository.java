package com.kongklang.repository;
import com.kongklang.entity.ApInvoice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ApInvoiceRepository extends JpaRepository<ApInvoice,Long> {
    java.util.Optional<ApInvoice> findByInventoryOperationIdAndStoreId(Long operationId,Long storeId);
    boolean existsByStoreIdAndSupplierIdAndVendorNumber(Long storeId,Long supplierId,String vendorNumber);
    List<ApInvoice> findByStoreIdOrderByCreatedAtDesc(Long storeId);
    Optional<ApInvoice> findByReceiptIdAndStoreId(Long receiptId,Long storeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ApInvoice i where i.id=:id and i.store.id=:storeId")
    Optional<ApInvoice> lockByIdAndStoreId(@Param("id") Long id,@Param("storeId") Long storeId);
}
