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


    boolean existsByCategoryId(UUID categoryId);
    Optional<Product> findByBlingProductId(Long blingProductId);

    @Modifying
    @Query("UPDATE Product p SET p.active = false WHERE p.blingProductId IS NOT NULL AND p.active = true AND p.blingProductId NOT IN :seenIds")
    int deactivateMissingFromBling(@Param("seenIds") Collection<Long> seenIds);

    @Query("""
        SELECT DISTINCT p FROM Product p
        WHERE p.active = true
          AND (:categoryIds IS NULL OR p.category.id IN :categoryIds)
          AND (:includeWithoutImage = true OR (p.imageUrl IS NOT NULL AND p.imageUrl <> ''))
          AND (:brandValues IS NULL OR EXISTS (
              SELECT 1 FROM ProductSpecification ps
              WHERE ps.product = p AND ps.name = 'Marca' AND ps.value IN :brandValues
          ))
          AND (:sizeValues IS NULL OR EXISTS (
              SELECT 1 FROM ProductVariant pv
              WHERE pv.product = p AND pv.size IN :sizeValues
          ))
        """)
    Page<Product> search(
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("includeWithoutImage") boolean includeWithoutImage,
            @Param("brandValues") Collection<String> brandValues,
            @Param("sizeValues") Collection<String> sizeValues,
            Pageable pageable
    );

}
