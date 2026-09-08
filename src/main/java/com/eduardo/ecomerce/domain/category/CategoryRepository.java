package com.eduardo.ecomerce.domain.category;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByName(String name);
    boolean existsByParentId(UUID parentId);
    Optional<Category> findByBlingCategoryId(Long blingCategoryId);
    Optional<Category> findByName(String name);
    List<Category> findByParentId(UUID parentId);
}
