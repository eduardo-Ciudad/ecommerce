package com.eduardo.ecomerce.domain.productspecification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductSpecificationRepository extends JpaRepository<ProductSpecification, UUID> {

    List<ProductSpecification> findByProductIdOrderByDisplayOrderAsc(UUID productId);

    void deleteByProductId(UUID productId);
}
