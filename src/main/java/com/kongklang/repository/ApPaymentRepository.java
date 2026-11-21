package com.kongklang.repository;
import com.kongklang.entity.ApPayment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ApPaymentRepository extends JpaRepository<ApPayment,Long> {
    Optional<ApPayment> findByStoreIdAndRequestKey(Long storeId,String requestKey);
    List<ApPayment> findByInvoiceIdAndStoreIdOrderByCreatedAtDesc(Long invoiceId,Long storeId);
}
