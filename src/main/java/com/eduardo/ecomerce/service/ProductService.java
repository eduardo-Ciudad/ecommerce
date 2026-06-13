package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.dto.input.product.ProductInput;
import com.eduardo.ecomerce.dto.output.product.ProductOutput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductOutput create(ProductInput input) {
        Category category = categoryRepository.findById(input.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        Product product = new Product();
        product.setCategory(category);
        product.setName(input.name());
        product.setDescription(input.description());
        productRepository.save(product);
        log.info("Produto criado — id: {}, nome: {}", product.getId(), product.getName());
        return toOutput(product);
    }

    @Transactional(readOnly = true)
    public List<ProductOutput> findAllActive() {
        return productRepository.findByActiveTrue().stream().map(this::toOutput).toList();
    }

    @Transactional(readOnly = true)
    public ProductOutput findById(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        return toOutput(product);
    }

    @Transactional
    public ProductOutput update(UUID id, ProductInput input) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        Category category = categoryRepository.findById(input.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada"));
        product.setCategory(category);
        product.setName(input.name());
        product.setDescription(input.description());
        productRepository.save(product);
        log.info("Produto atualizado — id: {}", id);
        return toOutput(product);
    }

    @Transactional
    public void delete(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
        product.setActive(false);
        productRepository.save(product);
        log.info("Produto desativado (soft delete) — id: {}", id);
    }

    private ProductOutput toOutput(Product product) {
        List<ProductVariantOutput> variants = product.getVariants().stream()
                .map(v -> new ProductVariantOutput(v.getId(), v.getSize(), v.getPrice(), v.getStock(), v.getCreatedAt()))
                .toList();
        return new ProductOutput(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getActive(),
                variants,
                product.getCreatedAt()
        );
    }
}
