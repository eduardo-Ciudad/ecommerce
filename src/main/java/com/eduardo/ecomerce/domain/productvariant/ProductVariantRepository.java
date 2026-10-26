package com.eduardo.ecomerce.domain.productvariant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByBlingVariationId(Long blingVariationId);
    Optional<ProductVariant> findBySku(String sku);

    @Query("""
            SELECT v FROM ProductVariant v
            JOIN FETCH v.product p
            JOIN FETCH p.category c
            LEFT JOIN FETCH c.parent
            WHERE p.active = true
            ORDER BY p.id, v.id
            """)
    List<ProductVariant> findAllForFeed();

    // ---------- Dashboard do admin (só variantes de produto ativo, as que aparecem na loja) ----------

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.product.active = true")
    long countOfActiveProducts();

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.product.active = true AND v.stock <= 0")
    long countOutOfStockOfActiveProducts();

    @Query("SELECT COUNT(v) FROM ProductVariant v WHERE v.product.active = true AND (v.gtin IS NULL OR v.gtin = '')")
    long countWithoutGtinOfActiveProducts();

}
