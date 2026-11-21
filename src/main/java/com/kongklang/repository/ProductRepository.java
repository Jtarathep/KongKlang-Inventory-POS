package com.kongklang.repository;

import com.kongklang.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.store.id = :storeId and p.code = :code")
    Optional<Product> findForUpdateByStoreIdAndCode(@Param("storeId") Long storeId, @Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.store.id = :storeId and p.id = :id")
    Optional<Product> findForUpdateByIdAndStoreId(@Param("id") Long id, @Param("storeId") Long storeId);

    // =========================
    // Multi Store
    // =========================

    List<Product> findAllByStoreId(Long storeId);

    Optional<Product> findByIdAndStoreId(
            Long id,
            Long storeId
    );

    Optional<Product> findByStoreIdAndCode(
            Long storeId,
            String code
    );

    Optional<Product> findByStoreIdAndBarcode(
            Long storeId,
            String barcode
    );

    boolean existsByStoreIdAndCode(
            Long storeId,
            String code
    );

    boolean existsByStoreIdAndBarcode(
            Long storeId,
            String barcode
    );

    long countByStoreId(Long storeId);
    long countByStoreIdAndQuantityLessThanEqual(Long storeId,Integer quantity);
    List<Product> findTop4ByStoreIdAndQuantityLessThanEqualOrderByQuantityAscIdAsc(Long storeId, Integer quantity);
    @Query("""
            select p from Product p
            where p.store.id = :storeId
              and (:term = '' or locate(lower(:term), lower(p.name)) > 0
                or locate(lower(:term), lower(p.code)) > 0 or p.barcode = :term)
              and (:stock = 'all'
                or (:stock = 'low' and p.quantity between 1 and 5)
                or (:stock = 'out' and p.quantity <= 0)
                or (:stock = 'replenish' and p.quantity <= 5))
            order by p.name, p.id
            """)
    List<Product> mobileSearch(@Param("storeId") Long storeId, @Param("term") String term,
                               @Param("stock") String stock, org.springframework.data.domain.Pageable page);
    boolean existsByStoreIdAndCategoryIgnoreCase(Long storeId,String category);
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Product p set p.category=:newName, p.version=p.version+1 where p.store.id=:storeId and lower(p.category)=lower(:oldName)")
    int renameCategory(@Param("storeId") Long storeId,@Param("oldName") String oldName,@Param("newName") String newName);

}
