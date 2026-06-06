package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.dto.input.category.CategoryInput;
import com.eduardo.ecomerce.dto.output.category.CategoryOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryOutput create(CategoryInput input) {
        if (categoryRepository.existsByName(input.name())) {
            throw new BusinessException("Categoria já cadastrada");
        }

        Category category = new Category();
        category.setName(input.name());

        categoryRepository.save(category);
        return toOutput(category);
    }

    public List<CategoryOutput> findAll() {
        return categoryRepository.findAll()
                .stream()
                .map(this::toOutput)
                .toList();
    }

    public CategoryOutput findById(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        return toOutput(category);
    }

    public void delete(UUID id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Categoria não encontrada");
        }
        categoryRepository.deleteById(id);
    }

    private CategoryOutput toOutput(Category category) {
        return new CategoryOutput(
                category.getId(),
                category.getName(),
                category.getCreatedAt()
        );
    }
}