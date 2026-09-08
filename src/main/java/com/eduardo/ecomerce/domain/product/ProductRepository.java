package com.eduardo.ecomerce.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("SELECT p FROM Product p WHERE p.active = true AND p.imageUrl IS NOT NULL AND p.imageUrl <> ''")
    Page<Product> findByActiveTrueAndImageUrlIsNotNull(Pageable pageable);

    boolean existsByCategoryId(UUID categoryId);
    Optional<Product> findByBlingProductId(Long blingProductId);

    @Modifying
    @Query("UPDATE Product p SET p.active = false WHERE p.blingProductId IS NOT NULL AND p.active = true AND p.blingProductId NOT IN :seenIds")
    int deactivateMissingFromBling(@Param("seenIds") Collection<Long> seenIds);


}
