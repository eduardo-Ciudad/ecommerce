package com.eduardo.ecomerce.domain.product;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
class ProductRepositoryReconciliationTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void deactivatesOnlyActiveBlingProductsMissingFromSeenIds() {
        Category category = new Category();
        category.setName("Reconciliação");
        category = categoryRepository.saveAndFlush(category);

        Product missingFromBling = product("Removido", 10L, category);
        Product seenOnBling = product("Presente", 20L, category);
        Product manuallyCreated = product("Manual", null, category);
        productRepository.save(missingFromBling);
        productRepository.save(seenOnBling);
        productRepository.save(manuallyCreated);
        productRepository.flush();

        int updated = productRepository.deactivateMissingFromBling(Set.of(20L));
        productRepository.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(1);
        assertThat(productRepository.findById(missingFromBling.getId()).orElseThrow().getActive()).isFalse();
        assertThat(productRepository.findById(seenOnBling.getId()).orElseThrow().getActive()).isTrue();
        assertThat(productRepository.findById(manuallyCreated.getId()).orElseThrow().getActive()).isTrue();
    }

    private Product product(String name, Long blingProductId, Category category) {
        Product product = new Product();
        product.setName(name);
        product.setBlingProductId(blingProductId);
        product.setCategory(category);
        product.setActive(true);
        return product;
    }
}
