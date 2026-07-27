package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.dto.input.product.ProductInput;
import com.eduardo.ecomerce.dto.output.product.ProductOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductService productService;

    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

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

    @Test
    @DisplayName("Deve fazer upload de imagem e atualizar o produto")
    void uploadImageSuccess() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setName("Camiseta Infantil");

        var file = new MockMultipartFile(
                "file", "camiseta.jpg", "image/jpeg", JPEG_BYTES
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(storageService.upload(file, "products")).thenReturn("https://pub-test.r2.dev/products/abc.jpg");
        when(productRepository.save(any(Product.class))).thenReturn(product);

        String imageUrl = productService.uploadImage(productId, file);

        assertThat(imageUrl).isEqualTo("https://pub-test.r2.dev/products/abc.jpg");
        assertThat(product.getImageUrl()).isEqualTo("https://pub-test.r2.dev/products/abc.jpg");
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("Deve lançar exceção ao fazer upload para produto inexistente")
    void uploadImageProductNotFound() {
        UUID productId = UUID.randomUUID();
        var file = new MockMultipartFile(
                "file", "foto.jpg", "image/jpeg", JPEG_BYTES
        );

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.uploadImage(productId, file))
                .isInstanceOf(EntityNotFoundException.class);

        verify(storageService, never()).upload(any(), anyString());
        verify(productRepository, never()).save(any());
    }
}
