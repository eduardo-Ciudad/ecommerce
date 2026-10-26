package com.eduardo.ecomerce.domain.productvariant;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
class ProductVariantRepositoryTest {

    @Autowired private ProductVariantRepository variantRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Stats");
        category = categoryRepository.saveAndFlush(category);
    }

    @Test
    void contagens_ignoramVariantesDeProdutoInativo() {
        Product active = product(true);
        Product inactive = product(false);
        variant(active, 5, "7890000000001");
        variant(active, 0, null);
        variant(inactive, 0, null);
        variant(inactive, 3, null);
        variantRepository.flush();

        assertThat(variantRepository.countOfActiveProducts()).isEqualTo(2);
        assertThat(variantRepository.countOutOfStockOfActiveProducts()).isEqualTo(1);
        assertThat(variantRepository.countWithoutGtinOfActiveProducts()).isEqualTo(1);
    }

    @Test
    void countWithoutGtin_consideraNuloEVazio() {
        Product active = product(true);
        variant(active, 1, null);
        variant(active, 1, "");
        variant(active, 1, "7890000000002");
        variantRepository.flush();

        assertThat(variantRepository.countWithoutGtinOfActiveProducts()).isEqualTo(2);
    }

    @Test
    void countActiveWithoutImage_consideraNuloEVazioSoEmProdutoAtivo() {
        product(true, null);
        product(true, "");
        product(true, "https://img/x.jpg");
        product(false, null);
        productRepository.flush();

        assertThat(productRepository.countActiveWithoutImage()).isEqualTo(2);
        assertThat(productRepository.countByActive(true)).isEqualTo(3);
        assertThat(productRepository.countByActive(false)).isEqualTo(1);
    }

    private Product product(boolean active) {
        return product(active, "https://img/p.jpg");
    }

    private Product product(boolean active, String imageUrl) {
        Product product = new Product();
        product.setName("Produto");
        product.setCategory(category);
        product.setActive(active);
        product.setImageUrl(imageUrl);
        return productRepository.save(product);
    }

    private void variant(Product product, int stock, String gtin) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setPrice(new BigDecimal("49.90"));
        variant.setStock(stock);
        variant.setGtin(gtin);
        variantRepository.save(variant);
    }
}
