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
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Busca do ?q=: nome (contém) ou SKU/GTIN exato de alguma variante. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        // product_specifications tem a coluna "value", palavra reservada no H2: sem aspas a tabela não é criada
        "spring.jpa.properties.hibernate.auto_quote_keyword=true",
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

    @Test
    void semAcento_achaNomeComAcento() {
        product("Salopete Bebê Milon Rosa Poá", true);
        flushAndClear();

        assertThat(search("salopete bebe")).extracting(Product::getName).containsExactly("Salopete Bebê Milon Rosa Poá");
    }

    @Test
    void palavrasForaDeOrdem_acha() {
        product("Salopete Bebê Milon Rosa Poá", true);
        flushAndClear();

        assertThat(search("milon salopete")).extracting(Product::getName).containsExactly("Salopete Bebê Milon Rosa Poá");
    }

    @Test
    void plural_achaNomeNoSingular() {
        product("Vestido Floral", true);
        flushAndClear();

        assertThat(search("vestidos")).extracting(Product::getName).containsExactly("Vestido Floral");
    }

    @Test
    void palavraQueNaoExiste_impedeResultado() {
        product("Salopete Bebê Milon Rosa Poá", true);
        flushAndClear();

        assertThat(search("salopete azul")).isEmpty();
    }

    @Test
    void palavraPodeVirDaCategoria() {
        Category bebeMenina = new Category();
        bebeMenina.setName("Bebê menina");
        bebeMenina = categoryRepository.saveAndFlush(bebeMenina);
        product("Body Manga Curta Floral", true, bebeMenina);
        product("Body Manga Curta Liso", true);
        flushAndClear();

        assertThat(search("body bebe menina")).extracting(Product::getName).containsExactly("Body Manga Curta Floral");
    }

    @Test
    void renomearProduto_atualizaTextoDeBusca() {
        Product product = product("Short Liso", true);
        product.setName("Short Moletinho");
        productRepository.saveAndFlush(product);
        flushAndClear();

        assertThat(search("moletinho")).extracting(Product::getName).containsExactly("Short Moletinho");
        assertThat(search("liso")).isEmpty();
    }

    @Test
    void todasVariantesZeradas_naoApareceNaLoja() {
        Product esgotado = product("Conjunto Esgotado", true);
        variant(esgotado, "ESG-M", null, 0);
        variant(esgotado, "ESG-G", null, 0);
        Product disponivel = product("Vestido Disponivel", true);
        variant(disponivel, "DISP-4", null, 2);
        flushAndClear();

        assertThat(storeListing(false)).extracting(Product::getName).containsExactly("Vestido Disponivel");
    }

    @Test
    void umaVarianteComEstoque_produtoAparece() {
        Product shorts = product("Short Moletinho", true);
        variant(shorts, "SHO-2", null, 0);
        variant(shorts, "SHO-4", null, 1);
        flushAndClear();

        assertThat(storeListing(false)).extracting(Product::getName).containsExactly("Short Moletinho");
    }

    @Test
    void includeOutOfStockTrue_mostraEsgotado() {
        Product esgotado = product("Conjunto Esgotado", true);
        variant(esgotado, "ESG-M", null, 0);
        flushAndClear();

        assertThat(storeListing(true)).extracting(Product::getName).containsExactly("Conjunto Esgotado");
    }

    @Test
    void paginacaoComDesempatePorId_naoRepeteNemPulaProduto() {
        for (int i = 0; i < 25; i++) {
            Product product = product("Produto " + i, true);
            variant(product, "PAG-" + i, null, 1);
        }
        entityManager.createQuery("UPDATE Product p SET p.createdAt = :mesmoInstante")
                .setParameter("mesmoInstante", java.time.LocalDateTime.of(2026, 11, 16, 12, 0))
                .executeUpdate();
        flushAndClear();

        Sort stable = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        java.util.Set<java.util.UUID> ids = new java.util.HashSet<>();
        int total = 0;
        for (int page = 0; page < 3; page++) {
            List<Product> content = productRepository
                    .search(null, true, null, null, null, null, false, PageRequest.of(page, 10, stable))
                    .getContent();
            total += content.size();
            content.forEach(p -> ids.add(p.getId()));
        }

        assertThat(total).isEqualTo(25);
        assertThat(ids).hasSize(25);
    }

    private List<Product> search(String q) {
        String code = q.trim().toLowerCase();
        List<String> patterns = SearchTextNormalizer.tokens(q).stream().map(t -> "%" + t + "%").toList();
        return productRepository.search(null, true, null, null, patterns, code, true, PageRequest.of(0, 20))
                .getContent();
    }

    private List<Product> storeListing(boolean includeOutOfStock) {
        return productRepository.search(null, true, null, null, null, null, includeOutOfStock, PageRequest.of(0, 20))
                .getContent();
    }

    private Product product(String name, boolean active) {
        return product(name, active, category);
    }

    private Product product(String name, boolean active, Category productCategory) {
        Product product = new Product();
        product.setName(name);
        product.setCategory(productCategory);
        product.setActive(active);
        return productRepository.saveAndFlush(product);
    }

    private void variant(Product product, String sku, String gtin) {
        variant(product, sku, gtin, 1);
    }

    private void variant(Product product, String sku, String gtin, int stock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setGtin(gtin);
        variant.setPrice(new BigDecimal("49.90"));
        variant.setStock(stock);
        entityManager.persist(variant);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
