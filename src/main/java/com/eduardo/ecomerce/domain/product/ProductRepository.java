package com.eduardo.ecomerce.domain.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    Page<Product> findByActiveTrue(Pageable pageable);
    boolean existsByCategoryId(UUID categoryId);
    Optional<Product> findByBlingProductId(Long blingProductId);


}
