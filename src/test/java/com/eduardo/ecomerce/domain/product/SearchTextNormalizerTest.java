package com.eduardo.ecomerce.domain.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextNormalizerTest {

    @Test
    void normalize_tiraAcentoEDeixaMinusculo() {
        assertThat(SearchTextNormalizer.normalize("Salopete Bebê Milon Rosa Poá")).isEqualTo("salopete bebe milon rosa poa");
    }

    @Test
    void normalize_hifenETravessaoViramEspaco() {
        assertThat(SearchTextNormalizer.normalize("Conjunto Bebê Brandili – Love Floral - Tam. M"))
                .isEqualTo("conjunto bebe brandili love floral tam. m");
    }

    @Test
    void normalize_nuloViraVazio() {
        assertThat(SearchTextNormalizer.normalize(null)).isEmpty();
    }

    @Test
    void tokens_descartaPalavrasDeLigacao() {
        assertThat(SearchTextNormalizer.tokens("Vestido de flor com laço")).containsExactly("vestido", "flor", "laco");
    }

    @Test
    void tokens_tiraPluralDePalavraLonga() {
        assertThat(SearchTextNormalizer.tokens("vestidos bermudas")).containsExactly("vestido", "bermuda");
    }

    @Test
    void tokens_naoMexeEmPalavraCurta() {
        assertThat(SearchTextNormalizer.tokens("gás")).containsExactly("gas");
    }

    @Test
    void tokens_tiraPontuacaoDasPontas() {
        assertThat(SearchTextNormalizer.tokens("Tam. M,")).containsExactly("tam", "m");
    }

    @Test
    void tokens_limitaACincoPalavras() {
        assertThat(SearchTextNormalizer.tokens("um dois tres quatro cinco seis sete")).hasSize(5);
    }

    @Test
    void tokens_vazioOuNuloDevolveListaVazia() {
        assertThat(SearchTextNormalizer.tokens("   ")).isEmpty();
        assertThat(SearchTextNormalizer.tokens(null)).isEmpty();
    }
}
