package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.dto.input.productvariant.ProductVariantInput;
import com.eduardo.ecomerce.dto.output.productvariant.ProductVariantOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductVariantServiceTest {

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductVariantService productVariantService;

    @Test
    void shouldCreateVariantSuccessfully() {
        UUID productId = UUID.randomUUID();
        ProductVariantInput input = new ProductVariantInput("P", new BigDecimal("29.90"), 10);

        Product product = new Product();
        product.setId(productId);
        product.setName("Vestido Rosa");

        ProductVariant saved = new ProductVariant();
        saved.setId(UUID.randomUUID());
        saved.setProduct(product);
        saved.setSize("P");
        saved.setPrice(new BigDecimal("29.90"));
        saved.setStock(10);
        saved.setCreatedAt(LocalDateTime.now());

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(saved);

        ProductVariantOutput output = productVariantService.create(productId, input);

        assertThat(output.size()).isEqualTo("P");
        assertThat(output.price()).isEqualByComparingTo(new BigDecimal("29.90"));
        assertThat(output.stock()).isEqualTo(10);
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        UUID productId = UUID.randomUUID();
        ProductVariantInput input = new ProductVariantInput("M", new BigDecimal("35.00"), 5);

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productVariantService.create(productId, input));
    }
}
