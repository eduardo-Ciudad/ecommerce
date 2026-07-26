package com.eduardo.ecomerce.service;
import org.springframework.web.multipart.MultipartFile;
import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.dto.input.category.CategoryInput;
import com.eduardo.ecomerce.dto.output.category.CategoryOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    public CategoryOutput create(CategoryInput input) {
        if (categoryRepository.existsByName(input.name())) {
            throw new BusinessException("Categoria já cadastrada");
        }

        Category category = new Category();
        category.setName(input.name());

        categoryRepository.save(category);
        log.info("Categoria criada: {}", input.name());
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

        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException("Categoria possui produtos vinculados e não pode ser removida");
        }

        categoryRepository.deleteById(id);
        log.info("Categoria removida — id: {}", id);
    }

    public String uploadImage(UUID id, MultipartFile file) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));

        String imageUrl = storageService.upload(file, "categories");
        category.setImageUrl(imageUrl);
        categoryRepository.save(category);

        log.info("Imagem atualizada para a categoria {}: {}", id, imageUrl);

        return imageUrl;
    }

    private CategoryOutput toOutput(Category category) {
        return new CategoryOutput(
                category.getId(),
                category.getName(),
                category.getImageUrl(),
                category.getCreatedAt()
        );
    }
}