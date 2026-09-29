package com.eduardo.ecomerce.domain.productimage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(UUID productId);


    void deleteByProductIdAndSource(UUID productId, ImageSource source);

    interface FeedImageView {
        UUID getProductId();
        String getUrl();
    }

    @Query("""
            SELECT i.product.id AS productId, i.url AS url
            FROM ProductImage i
            WHERE i.product.active = true
            ORDER BY i.product.id, i.displayOrder ASC
            """)
    List<FeedImageView> findAllForFeed();
}
