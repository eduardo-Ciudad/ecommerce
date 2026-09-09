package com.eduardo.ecomerce.domain.productimage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {

    List<ProductImage> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    void deleteByProductIdAndSource(UUID productId, ImageSource source);
}
