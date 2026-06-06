package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.dto.input.product.ProductInput;
import com.eduardo.ecomerce.dto.output.product.ProductOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldCreateProductSuccessfully() {
        UUID categoryId = UUID.randomUUID();
        ProductInput input = new ProductInput(categoryId, "Vestido Rosa", "Vestido infantil");

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Vestidos");

        Product saved = new Product();
        saved.setId(UUID.randomUUID());
        saved.setCategory(category);
        saved.setName(input.name());
        saved.setDescription(input.description());
        saved.setActive(true);
        saved.setCreatedAt(LocalDateTime.now());

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(saved);

        ProductOutput output = productService.create(input);

        assertThat(output.name()).isEqualTo("Vestido Rosa");
        assertThat(output.categoryId()).isEqualTo(categoryId);
        assertThat(output.active()).isTrue();
    }

    @Test
    void shouldThrowWhenCategoryNotFound() {
        UUID categoryId = UUID.randomUUID();
        ProductInput input = new ProductInput(categoryId, "Vestido Rosa", null);

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productService.create(input));
    }

    @Test
    void shouldSoftDeleteProduct() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setActive(true);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        productService.delete(productId);

        assertThat(product.getActive()).isFalse();
        verify(productRepository).save(product);
    }
}
