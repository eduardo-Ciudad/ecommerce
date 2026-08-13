package com.eduardo.ecomerce.domain.productvariant;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    Optional<ProductVariant> findByBlingVariationId(Long blingVariationId);
    Optional<ProductVariant> findBySku(String sku);

}
