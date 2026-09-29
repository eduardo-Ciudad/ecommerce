package com.eduardo.ecomerce.domain.productspecification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, UUID> {

    List<ProductSpecification> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    void deleteByProductId(UUID productId);

    interface FeedBrandView {
        UUID getProductId();
        String getValue();
    }

    @Query("""
            SELECT s.product.id AS productId, s.value AS value
            FROM ProductSpecification s
            WHERE s.name = 'Marca' AND trim(s.value) <> '' AND s.product.active = true
            ORDER BY s.displayOrder ASC
            """)
    List<FeedBrandView> findBrandsForFeed();
}
