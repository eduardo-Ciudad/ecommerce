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

    // ---------- Dashboard do admin ----------

    long countByActive(boolean active);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.active = true AND (p.imageUrl IS NULL OR p.imageUrl = '')")
    long countActiveWithoutImage();

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
      AND (:codeQuery IS NULL
           OR ((:t1 IS NULL OR p.searchText LIKE :t1)
               AND (:t2 IS NULL OR p.searchText LIKE :t2)
               AND (:t3 IS NULL OR p.searchText LIKE :t3)
               AND (:t4 IS NULL OR p.searchText LIKE :t4)
               AND (:t5 IS NULL OR p.searchText LIKE :t5))
           OR EXISTS (
               SELECT 1 FROM ProductVariant pv2
               WHERE pv2.product = p
                 AND (LOWER(pv2.sku) = :codeQuery OR pv2.gtin = :codeQuery)
           ))
      AND (:includeOutOfStock = true OR EXISTS (
          SELECT 1 FROM ProductVariant pv3
          WHERE pv3.product = p AND pv3.stock > 0
      ))
    """)
    Page<Product> searchByTokens(
            @Param("categoryIds") Collection<UUID> categoryIds,
            @Param("includeWithoutImage") boolean includeWithoutImage,
            @Param("brandValues") Collection<String> brandValues,
            @Param("sizeValues") Collection<String> sizeValues,
            @Param("t1") String t1,
            @Param("t2") String t2,
            @Param("t3") String t3,
            @Param("t4") String t4,
            @Param("t5") String t5,
            @Param("codeQuery") String codeQuery,
            @Param("includeOutOfStock") boolean includeOutOfStock,
            Pageable pageable
    );

    /**
     * namePatterns: até 5 padrões "%palavra%" (já normalizados); cada um precisa bater no search_text.
     * codeQuery: termo inteiro em minúsculas, para SKU/GTIN exato. Ambos nulos = sem busca por texto.
     */
    default Page<Product> search(
            Collection<UUID> categoryIds,
            boolean includeWithoutImage,
            Collection<String> brandValues,
            Collection<String> sizeValues,
            List<String> namePatterns,
            String codeQuery,
            boolean includeOutOfStock,
            Pageable pageable
    ) {
        List<String> patterns = namePatterns == null ? List.of() : namePatterns;
        return searchByTokens(categoryIds, includeWithoutImage, brandValues, sizeValues,
                patternAt(patterns, 0), patternAt(patterns, 1), patternAt(patterns, 2),
                patternAt(patterns, 3), patternAt(patterns, 4),
                codeQuery, includeOutOfStock, pageable);
    }

    private static String patternAt(List<String> patterns, int index) {
        return index < patterns.size() ? patterns.get(index) : null;
    }

}
