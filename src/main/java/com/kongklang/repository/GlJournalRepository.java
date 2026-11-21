package com.kongklang.repository;
import com.kongklang.entity.GlJournal;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GlJournalRepository extends JpaRepository<GlJournal,Long> {
    Optional<GlJournal> findByStoreIdAndSourceTypeAndSourceId(Long storeId,String sourceType,Long sourceId);
    List<GlJournal> findByStoreIdOrderByPostedAtDesc(Long storeId);
}
