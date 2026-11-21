package com.kongklang.repository;
import com.kongklang.entity.GlAccount;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GlAccountRepository extends JpaRepository<GlAccount,Long> {
    Optional<GlAccount> findByStoreIdAndCode(Long storeId,String code);
    List<GlAccount> findByStoreIdOrderByCodeAsc(Long storeId);
}
