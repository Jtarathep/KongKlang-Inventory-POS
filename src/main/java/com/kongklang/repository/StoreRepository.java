package com.kongklang.repository;

import com.kongklang.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Store s where s.id=:id")
    Optional<Store> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<Store> findByStoreCode(String storeCode);

    Optional<Store> findByStoreCodeAndActiveTrue(String storeCode);

    List<Store> findAllByOrderByCreatedAtDesc();

    boolean existsByStoreCode(String storeCode);
}
