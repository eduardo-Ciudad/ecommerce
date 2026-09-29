package com.eduardo.ecomerce.service.feed;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.product.Product;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleFeedMapperTest {

    private static final String SITE = "https://www.gabikidstore.com";

    private static ProductVariant variant(String size, String color, String gtin, int stock) {
        Category root = new Category();
        root.setName("Meninas");
        Category leaf = new Category();
        leaf.setName("Calças e Leggings menina");
        leaf.setParent(root);

        Product product = new Product();
        product.setId(UUID.fromString("d1ad0236-81d8-486d-8617-5c3c61158321"));
        product.setName("Legging Infantil Menina Brandili Glitter Preta");
        product.setDescription("<p>Legging em <strong>cotton</strong>\n confortável</p>");
        product.setCategory(leaf);

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.fromString("6e7cdf02-46af-48ab-b65a-3879742ff79d"));
        variant.setProduct(product);
        variant.setSize(size);
        variant.setColor(color);
        variant.setGtin(gtin);
        variant.setPrice(new BigDecimal("49.9"));
        variant.setStock(stock);
        return variant;
    }

    @Test
    void mapsVariantWithGtinToCompleteItem() {
        GoogleFeedItem item = GoogleFeedMapper.toItem(
                variant("4", null, "7900257197337", 2),
                List.of("https://img/1.jpg", "https://img/2.jpg"),
                "Brandili",
                SITE
        );

        assertThat(item.id()).isEqualTo("6e7cdf02-46af-48ab-b65a-3879742ff79d");
        assertThat(item.itemGroupId()).isEqualTo("d1ad0236-81d8-486d-8617-5c3c61158321");
        assertThat(item.title()).isEqualTo("Legging Infantil Menina Brandili Glitter Preta – Tam. 4");
        assertThat(item.description()).isEqualTo("Legging em cotton confortável");
        assertThat(item.link()).isEqualTo(SITE + "/product.html?id=d1ad0236-81d8-486d-8617-5c3c61158321"
                + "&variant=6e7cdf02-46af-48ab-b65a-3879742ff79d");
        assertThat(item.imageLink()).isEqualTo("https://img/1.jpg");
        assertThat(item.additionalImageLinks()).isEqualTo(List.of("https://img/2.jpg"));
        assertThat(item.price()).isEqualTo("49.90 BRL");
        assertThat(item.availability()).isEqualTo("in_stock");
        assertThat(item.brand()).isEqualTo("Brandili");
        assertThat(item.gtin()).isEqualTo("7900257197337");
        assertThat(item.identifierExists()).isNull();
        assertThat(item.productType()).isEqualTo("Meninas > Calças e Leggings menina");
        assertThat(item.gender()).isEqualTo("female");
        assertThat(item.ageGroup()).isEqualTo("toddler");
        assertThat(item.size()).isEqualTo("4");
    }

    @Test
    void withoutGtinSendsIdentifierExistsNoAndCleansSize() {
        GoogleFeedItem item = GoogleFeedMapper.toItem(
                variant("2 - cod 276784", "Rosa", " ", 0), List.of("https://img/1.jpg"), null, SITE);

        assertThat(item.gtin()).isNull();
        assertThat(item.identifierExists()).isEqualTo("no");
        assertThat(item.size()).isEqualTo("2");
        assertThat(item.color()).isEqualTo("Rosa");
        assertThat(item.title()).endsWith("– Tam. 2 – Rosa");
        assertThat(item.availability()).isEqualTo("out_of_stock");
        assertThat(item.brand()).isEqualTo("Brandili"); // fallback pelo nome do produto
    }

    @Test
    void fallsBackToProductImageAndSkipsWhenThereIsNoImageAtAll() {
        ProductVariant withCover = variant("6", null, null, 1);
        withCover.getProduct().setImageUrl("https://img/capa.jpg");
        assertThat(GoogleFeedMapper.toItem(withCover, List.of(), null, SITE).imageLink())
                .isEqualTo("https://img/capa.jpg");

        ProductVariant withoutImage = variant("6", null, null, 1);
        assertThat(GoogleFeedMapper.toItem(withoutImage, List.of(), null, SITE)).isNull();
    }
}