package com.eduardo.ecomerce.service.feed;

import com.eduardo.ecomerce.domain.category.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleAttributeResolverTest {

    private static Category category(String rootName, String leafName) {
        Category root = new Category();
        root.setName(rootName);
        Category leaf = new Category();
        leaf.setName(leafName);
        leaf.setParent(root);
        return leaf;
    }

    @Test
    void genderComesFromRootCategory() {
        assertThat(GoogleAttributeResolver.gender(category("Meninas", "Vestidos"))).isEqualTo("female");
        assertThat(GoogleAttributeResolver.gender(category("Bebê menina", "Bodies"))).isEqualTo("female");
        assertThat(GoogleAttributeResolver.gender(category("Meninos", "Bermudas"))).isEqualTo("male");
        assertThat(GoogleAttributeResolver.gender(category("Bebê menino", "Macacões"))).isEqualTo("male");
        assertThat(GoogleAttributeResolver.gender(category("Bebê unissex", "Mantas"))).isEqualTo("unisex");
        assertThat(GoogleAttributeResolver.gender(null)).isEqualTo("unisex");
    }

    @Test
    void ageGroupComesFromSize() {
        Category meninas = category("Meninas", "Vestidos");
        assertThat(GoogleAttributeResolver.ageGroup("RN", meninas)).isEqualTo("newborn");
        assertThat(GoogleAttributeResolver.ageGroup("P bebê", meninas)).isEqualTo("infant");
        assertThat(GoogleAttributeResolver.ageGroup("9 meses", meninas)).isEqualTo("infant");
        assertThat(GoogleAttributeResolver.ageGroup("1", meninas)).isEqualTo("toddler");
        assertThat(GoogleAttributeResolver.ageGroup("4", meninas)).isEqualTo("toddler");
        assertThat(GoogleAttributeResolver.ageGroup("6", meninas)).isEqualTo("kids");
        assertThat(GoogleAttributeResolver.ageGroup("16", meninas)).isEqualTo("kids");
        assertThat(GoogleAttributeResolver.ageGroup("20", meninas)).isEqualTo("kids");
        assertThat(GoogleAttributeResolver.ageGroup("2 - cod 276784", meninas)).isEqualTo("toddler");
    }

    @Test
    void cleanSizeRemovesCodeSuffix() {
        assertThat(GoogleAttributeResolver.cleanSize("P - cod 276816")).isEqualTo("P");
        assertThat(GoogleAttributeResolver.cleanSize("2 - cod 276784")).isEqualTo("2");
        assertThat(GoogleAttributeResolver.cleanSize("M bebê")).isEqualTo("M bebê");
        assertThat(GoogleAttributeResolver.cleanSize(null)).isNull();
    }

    @Test
    void ageGroupFallsBackToRootCategoryWhenSizeIsUnknown() {
        assertThat(GoogleAttributeResolver.ageGroup("G", category("Bebê menino", "Bodies"))).isEqualTo("infant");
        assertThat(GoogleAttributeResolver.ageGroup(null, category("Meninos", "Camisetas"))).isEqualTo("kids");
        assertThat(GoogleAttributeResolver.ageGroup("Único", null)).isEqualTo("kids");
    }

    @Test
    void brandPrefersSpecificationAndFallsBackToProductName() {
        assertThat(GoogleAttributeResolver.brand(" Brandili ", "Qualquer nome")).isEqualTo("Brandili");
        assertThat(GoogleAttributeResolver.brand(null, "Conjunto Infantil Menino Fakini Listrado")).isEqualTo("Fakini");
        assertThat(GoogleAttributeResolver.brand("", "Blusa Colorittà Floral")).isEqualTo("Colorittá");
        assertThat(GoogleAttributeResolver.brand(null, "Camiseta Listrada Off-White")).isNull();
        assertThat(GoogleAttributeResolver.brand(null, "Vestido Elianinha")).isNull();
        assertThat(GoogleAttributeResolver.brand(null, "Blusa Infantil Menina Trick Nick Branca Cachorrinhos")).isEqualTo("Trick Nick");
        assertThat(GoogleAttributeResolver.brand(null, "Blusa Juvenil H!ts Azul “You Decide” – Tam. 12")).isEqualTo("H!ts");
        assertThat(GoogleAttributeResolver.brand(null, "Blusa Infantil Menina Rovi Kids Branca Believe")).isEqualTo("Rovi Kids");
        assertThat(GoogleAttributeResolver.brand(null, "Blusa Infantil Brandili Active Rosa Pink Esportiva")).isEqualTo("Brandili");
        assertThat(GoogleAttributeResolver.brand(null, "Blusa Infantil Forfun Preta Básica")).isEqualTo("Fakini");
    }
}