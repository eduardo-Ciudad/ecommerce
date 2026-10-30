package com.eduardo.ecomerce.domain.product;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Busca do ?q=: nome (contém) ou SKU/GTIN exato de alguma variante. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
class ProductRepositorySearchTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Busca");
        category = categoryRepository.saveAndFlush(category);
    }

    @Test
    void codigoExato_achaPeloSkuSemDiferenciarMaiuscula() {
        Product shorts = product("Short Moletinho", true);
        variant(shorts, "SKU-ABC-4", "7890000000017");
        product("Vestido Floral", true);
        flushAndClear();

        assertThat(search("sku-abc-4")).extracting(Product::getName).containsExactly("Short Moletinho");
    }

    @Test
    void codigoExato_achaPeloGtin() {
        Product body = product("Body Manga Curta", true);
        variant(body, "BODY-M", "7891234567895");
        flushAndClear();

        assertThat(search("7891234567895")).extracting(Product::getName).containsExactly("Body Manga Curta");
    }

    @Test
    void skuParcial_naoAchaPeloCodigo() {
        Product shorts = product("Short Moletinho", true);
        variant(shorts, "SKU-ABC-4", null);
        flushAndClear();

        assertThat(search("sku-abc")).isEmpty();
    }

    @Test
    void nome_continuaAchandoPorParte() {
        Product shorts = product("Short Moletinho Brandili", true);
        variant(shorts, "SKU-1", null);
        flushAndClear();

        assertThat(search("moletinho")).extracting(Product::getName).containsExactly("Short Moletinho Brandili");
    }

    @Test
    void produtoInativo_naoApareceMesmoComSkuCerto() {
        Product inactive = product("Pijama Antigo", false);
        variant(inactive, "PIJ-1", "7890000000024");
        flushAndClear();

        assertThat(search("pij-1")).isEmpty();
        assertThat(search("7890000000024")).isEmpty();
    }

    @Test
    void duasVariantesBatendo_produtoApareceUmaVez() {
        Product shorts = product("Short Moletinho", true);
        variant(shorts, "7890000000031", null);
        variant(shorts, "OUTRO-SKU", "7890000000031");
        flushAndClear();

        assertThat(search("7890000000031")).hasSize(1);
    }

    private List<Product> search(String q) {
        String code = q.trim().toLowerCase();
        return productRepository.search(null, true, null, null, "%" + code + "%", code, PageRequest.of(0, 20))
                .getContent();
    }

    private Product product(String name, boolean active) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(category);
        product.setActive(active);
        return productRepository.saveAndFlush(product);
    }

    private void variant(Product product, String sku, String gtin) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setGtin(gtin);
        variant.setPrice(new BigDecimal("49.90"));
        variant.setStock(1);
        entityManager.persist(variant);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
