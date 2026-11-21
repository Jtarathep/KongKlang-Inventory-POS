package com.kongklang.repository;

import com.kongklang.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from UserAccount u where u.id=:id")
    Optional<UserAccount> lockById(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByUsernameAndActiveTrue(String username);

    boolean existsByUsername(String username);

    List<UserAccount> findAllByOrderByCreatedAtDesc();
    List<UserAccount> findAllByStoreIdOrderByCreatedAtDesc(Long storeId);
    Optional<UserAccount> findByIdAndStoreId(Long id, Long storeId);
}
