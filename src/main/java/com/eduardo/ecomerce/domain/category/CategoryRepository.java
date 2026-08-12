package com.eduardo.ecomerce.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByName(String name);
    Optional<Category> findByBlingCategoryId(Long blingCategoryId);
}
